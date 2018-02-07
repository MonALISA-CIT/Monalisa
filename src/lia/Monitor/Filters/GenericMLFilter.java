/*
 * $Id: GenericMLFilter.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.Filters;

import java.util.Date;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.DataCache.Cache;
import lia.Monitor.DataCache.DataSelect;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.Gresult;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MonitorClient;
import lia.Monitor.monitor.MonitorFilter;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.dbStore;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.threads.MonALISAExecutors;

/**
 * Base class for all filters inside ML
 * 
 * @author ramiro
 */
public abstract class GenericMLFilter implements MonitorFilter, Runnable {

    /**
     * 
     */
    private static final long serialVersionUID = 7734400449888103027L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(GenericMLFilter.class.getName());

    /**
     * flag that shows if the filter is active or was destroyed
     */
    protected volatile boolean active = true;

    /**
     * a link to the storage infrastructure
     */
    protected dbStore store;

    /**
     * service under which we are running
     */
    protected MFarm farm;

    /**
     * service worker
     */
    protected volatile Cache cache;

    private static long MIN_THREAD_SLEEP;

    private final CopyOnWriteArrayList<MonitorClient> clients = new CopyOnWriteArrayList<MonitorClient>();

    /**
     * service name
     */
    protected String farmName = null;

    private boolean shouldLimitSleepTime;

    static {
        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                final String sLevel = AppConfig.getProperty("lia.Monitor.Filters.GenericMLFilter.level");
                Level loggingLevel = null;
                if (sLevel != null) {
                    try {
                        loggingLevel = Level.parse(sLevel);
                    } catch (Throwable t) {
                        loggingLevel = null;
                    }

                    logger.setLevel(loggingLevel);
                }

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " [ GenericMLFilter ] reloadedConf. Logging level: " + loggingLevel);
                }
            }

        });
    }

    /**
     * Initialize the filter with the name of the service under which it is
     * running
     * 
     * @param farmName
     *            current service name
     */
    protected GenericMLFilter(final String farmName) {
        this.farmName = farmName;
    }

    @Override
    public void addClient(MonitorClient client) {
        clients.add(client);
    }

    @Override
    public void removeClient(MonitorClient client) {
        if (!clients.remove(client)) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "[ GenericMLFilter ] removedClient (" + client
                        + " ) == Not OK! Client not found!");
            }
        } else {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINE, "[ GenericMLFilter ] removedClient (" + client + " ) == OK");
            }
        }
    }

    @Override
    public void initCache(Cache cache) {
        this.cache = cache;
        rescheduleFilter();
    }

    @Override
    public void initdb(dbStore store, MFarm farm) {
        this.store = store;
        this.farm = farm;
    }

    @Override
    public void run() {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "The filter: " + getClass().getName() + "  started main loop");
        }

        try {
            Object o = expressResults();
            if (o != null) {
                Vector<Object> notifResults = new Vector<Object>();
                Vector<Object> storeResults = new Vector<Object>();

                if (o instanceof Vector) {
                    @SuppressWarnings("unchecked")
                    Vector<Object> allResults = (Vector<Object>) o;

                    if (allResults.size() > 0) {
                        for (int i = 0; i < allResults.size(); i++) {
                            Object r = allResults.elementAt(i);
                            if (r != null) {
                                if (r instanceof Gresult) {
                                    notifResults.add(r);
                                } else {
                                    storeResults.add(r);
                                }
                            }
                        }
                    }
                } else if (o instanceof Result[]) {// notify an Array of
                                                   // ResultS...but not a Vector
                    Result[] rez = (Result[]) o;
                    for (Result element : rez) {
                        notifResults.add(element);
                    }
                } else {// notify anything else
                    notifResults.add(o);
                }
                if (notifResults.size() > 0) {
                    informClients(notifResults);
                }
                if (storeResults.size() > 0) {
                    notifyCache(storeResults);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "GenericMLFilter General Exception in main loop", t);
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "The filter: " + getClass().getName() + "  finished main loop");
        }

        rescheduleFilter();
    }

    private void rescheduleFilter() {
        try {
            shouldLimitSleepTime = AppConfig.getb("lia.Monitor.Filters.ShouldLimitSleepTime", true);
        } catch (Throwable t) {
            shouldLimitSleepTime = true;
        }

        MIN_THREAD_SLEEP = AppConfig.getl("lia.Monitor.Filters.ShouldLimitSleepTime", 1000);

        long sleepTime = MIN_THREAD_SLEEP;
        try {

            sleepTime = getSleepTime();

            // try to not let the user run the filter for at least
            // MIN_THREAD_SLEEP millis
            if ((sleepTime < MIN_THREAD_SLEEP) && shouldLimitSleepTime) {
                sleepTime = MIN_THREAD_SLEEP;
            }

        } catch (Throwable e) {
            logger.log(Level.WARNING, " [ GenericMLFilter ] got exception trying to determine next sched date ", e);
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "The filter: " + getClass().getName() + "  will be rescheduled for " + sleepTime
                    + " ms = " + new Date(System.currentTimeMillis() + sleepTime));
        }

        try {
            MonALISAExecutors.getMLHelperExecutor().schedule(this, sleepTime, TimeUnit.MILLISECONDS);
        } catch (Throwable e) {
            logger.log(Level.SEVERE, "\n\n\n [ GenericMLFilter ] got exception trying sched date \n\n\n", e);
        }
    }

    @Override
    public void finishIt() {
        active = false;
        clients.clear();
        this.store = null;

        Thread.currentThread().interrupt();

    }

    @Override
    public boolean isAlive() {
        return active;
    }

    /**
     * Got new result ... see if it can match any of the predicates (if any)
     */
    @Override
    public void addNewResult(Object o) {
        if (o == null) {
            return;
        }

        if (o instanceof Vector) {
            Vector<?> v = (Vector<?>) o;
            if (v.size() == 0) {
                return;
            }
            for (int i = 0; i < v.size(); i++) {
                addNewResult(v.elementAt(i));
            }
        } else if (o instanceof Result) {
            Result r = (Result) o;
            monPredicate[] fPreds = getFilterPred();
            if ((fPreds == null) || (fPreds.length == 0)) {
                notifyResult(o);
                return;
            }

            for (monPredicate p : fPreds) {
                Result rr = DataSelect.matchResult(r, p);
                if (rr != null) {
                    notifyResult(rr);
                }
            }
        } else if (o instanceof eResult) {
            eResult r = (eResult) o;
            monPredicate[] fPreds = getFilterPred();
            if ((fPreds == null) || (fPreds.length == 0)) {
                notifyResult(o);
                return;
            }

            for (monPredicate p : fPreds) {
                eResult rr = DataSelect.matchResult(r, p);
                if (rr != null) {
                    notifyResult(rr);
                }
            }
        }
    }

    /**
     * Notify clients when we have some new values
     * 
     * @param v
     */
    void informClients(Vector<?> v) {
        if ((v == null) || (v.size() == 0)) {
            return;
        }
        String fName = getName();
        if ((fName == null) || (fName.length() == 0)) {
            return;
        }
        for (MonitorClient mc : clients) {
            try {
                mc.notifyResult(v, fName);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Got exception notifying client", t);
            }
        }

    }

    private void notifyCache(Vector<?> storeResults) {
        cache.notifyInternalResults(storeResults);
    }

    @Override
    public void confChanged() {
        // you can override this to be notified when the configuration is
        // changed
    }

    @Override
    public abstract String getName();

    /**
     * How often should this filter be run ? At this interval the {@link #expressResults()} method will be called on the
     * filter.
     * 
     * @return interval in milliseconds
     */
    public abstract long getSleepTime();

    /**
     * Get the list of predicates that match the data you are interested in
     * 
     * @return an array of predicates. Can be <code>null</code> or empty if you
     *         want to receive everything.
     */
    public abstract monPredicate[] getFilterPred();

    /**
     * Callback function through which the filter is notified about some new
     * value that matches one of the predicates from {@link #getFilterPred()}
     * 
     * @param o
     */
    public abstract void notifyResult(Object o);

    /**
     * This function is periodically called to give the filter a chance to
     * summarize values
     * 
     * @return any ML-style object: Vector, Result, eResult etc
     * @see #getSleepTime()
     */
    public abstract Object expressResults();

}
