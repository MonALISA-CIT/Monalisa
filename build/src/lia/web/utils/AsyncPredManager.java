/**
 * 
 */
package lia.web.utils;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniClient.Store.JtClient;
import lia.Monitor.JiniClient.Store.Main;
import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.TransparentStoreFast;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.TimestampedResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.ntp.NTPDate;

/**
 * @author costing
 * @since 2007-03-12
 */
public final class AsyncPredManager {
    /**
     * Logger for this class
     */
    private static final Logger logger = Logger.getLogger(AsyncPredManager.class.getName());

    private static final class Entry {
        /**
         * Predicate
         */
        final monPredicate pred;

        /**
         * Last access time
         */
        long lLastAccessTime;

        /**
         * @param _pred
         */
        public Entry(final monPredicate _pred) {
            pred = _pred;
            lLastAccessTime = NTPDate.currentTimeMillis();
        }
    }

    private static final MonitorThread monitor;

    static {
        monitor = new MonitorThread();
        monitor.start();
    }

    /**
     * Configurable expiration time
     */
    static volatile long lExpirationTime = AppConfig.getl("lia.web.utils.AsyncPredManager.expire_time", 60) * 1000;

    /**
     * 
     */
    static void updateExpirationTime() {
        lExpirationTime = AppConfig.getl("lia.web.utils.AsyncPredManager.expire_time", lExpirationTime / 1000) * 1000;
    }

    static {
        final AppConfigChangeListener accl = new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                updateExpirationTime();
            }

        };

        AppConfig.addNotifier(accl);
    }

    private static final class MonitorThread extends Thread {
        /**
         * Simple constructor
         */
        public MonitorThread() {
            super("(ML) AsyncPredManager");
        }

        @Override
        public void run() {
            try {
                sleep(lExpirationTime);
            } catch (Exception e) {
                // ignore
            }

            while (true) {
                logger.log(Level.FINEST, "MonitorThread: start cleanup");

                final long lLimit = NTPDate.currentTimeMillis() - lExpirationTime;

                synchronized (lEntries) {
                    final Iterator<Entry> it = lEntries.iterator();

                    while (it.hasNext()) {
                        final Entry e = it.next();

                        if (e.lLastAccessTime < lLimit) {
                            it.remove();

                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, "MonitorThread: removing predicate: " + e.pred);
                            }

                            try {
                                Main.getInstance().unregisterPredicate(e.pred);
                            } catch (Throwable t) {
                                logger.log(Level.FINE, "MonitorThread: exception removing predicate", t);
                            }
                        }
                    }

                    if (lEntries.size() == 0) {
                        logger.log(Level.FINEST, "Nothing to monitor, entering hibernation");

                        try {
                            lEntries.wait();
                        } catch (InterruptedException ie) {
                            // ignore
                        }
                    }
                }

                try {
                    sleep(Math.max(lExpirationTime / 10, 1000));
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Unregister all currently registered predicates and clear all the structures. Good for debugging.
     */
    public static void clear() {
        synchronized (lEntries) {
            Iterator<Entry> it = lEntries.iterator();

            while (it.hasNext()) {
                Entry e = it.next();

                try {
                    Main.getInstance().unregisterPredicate(e.pred);
                } catch (Throwable t) {
                    logger.log(Level.FINE, "clear: exception removing predicate", t);
                }
            }
        }
    }

    /**
     * This function gets the last values for the given predicate, since the last query time until now.
     * If the predicate was not registered for continuous data receiving, it will be registered after this call. 
     * 
     * @param pred predicate for data
     * @param lLastQueryTime
     * @return a Vector of objects already received that match the query
     */
    public static Vector<TimestampedResult> getValues(final monPredicate pred, final long lLastQueryTime) {
        verifyPred(pred);

        final TransparentStoreFast store = (TransparentStoreFast) TransparentStoreFactory.getStore();

        pred.tmin = lLastQueryTime;
        pred.tmax = -1;

        final Vector<TimestampedResult> v = store.select(pred);

        return v;
    }

    /**
     * Currently registered entries
     */
    static final LinkedList<Entry> lEntries = new LinkedList<Entry>();

    private static void verifyPred(final monPredicate pred) {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Verifying predicate: " + pred);
        }

        boolean bUnpause = false;

        synchronized (lEntries) {
            final Iterator<Entry> it = lEntries.iterator();

            while (it.hasNext()) {
                final Entry e = it.next();

                if (JtClient.predicatesComparator.compare(e.pred, pred) == 0) {
                    e.lLastAccessTime = NTPDate.currentTimeMillis();

                    logger.log(Level.FINER, "Predicate already registered for monitoring");

                    return;
                }
            }

            logger.log(Level.FINE, "Should start monitoring this predicate");

            // if the registration is successful (the same pred was not already registered) then add it to the monitoring
            if (Main.getInstance().registerPredicate(pred)) {
                logger.log(Level.FINER, "Predicate registered, starting monitoring it");

                bUnpause = lEntries.size() == 0;

                lEntries.add(new Entry(pred));
            } else {
                logger.log(Level.FINE, "Hmm, the store client didn't accept this predicate");
            }

            if (bUnpause) {
                logger.log(Level.FINE, "Activity has resumed, waking up monitoring thread");

                lEntries.notifyAll();
            }
        }
    }
}
