/*
 * $Id: InfoToFile.java 7419 2013-10-16 12:56:15Z ramiro $
 */

package lia.Monitor.ClientsFarmProxy.Monitor;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;

/**
 * @author mickyt, ramiro
 */
public class InfoToFile {

    private static final Logger logger = Logger.getLogger(InfoToFile.class.getName());

    private static final String POISON_PILL = "STOP_TOKEN_NOW";

    private static final InfoToFile _thisInstance = new InfoToFile();

    private final static AtomicBoolean enabled = new AtomicBoolean(false);

    private final BlockingQueue<String> theQueue = new LinkedBlockingQueue<String>(500);

    static {
        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                reloadConfig();
            }
        });
        reloadConfig();
    }

    private static final void reloadConfig() {
        checkStatusAndStart(AppConfig.getb("lia.Monitor.ClientsFarmProxy.Monitor.InfoToFile.enabled", false));
    }

    private static final boolean checkStatusAndStart(final boolean shouldStart) {

        boolean returnValue = false;
        try {
            if (shouldStart) {
                if (enabled.compareAndSet(false, shouldStart)) {
                    Thread worker = null;
                    try {
                        worker = new WriterThread();
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " [ InfoToFile ] checkStatusAndStart( " + shouldStart
                                + " ) failed to start. Cause: ", t);
                    }
                    if (worker != null) {
                        worker.start();
                        returnValue = true;
                    } else {
                        enabled.set(false);
                    }
                } else {
                    logger.log(Level.INFO, " [ InfoToFile ] checkStatusAndStart( " + shouldStart
                            + " ) WriterTask already started. enabled(): " + enabled.get());
                    returnValue = true;
                }
            } else {
                if (enabled.compareAndSet(true, shouldStart)) {
                    try {
                        _thisInstance.theQueue.put(POISON_PILL);
                    } catch (Throwable t) {
                        logger.log(Level.WARNING,
                                "\n\n\n [ InfoToFile ] Unable to notify STOP_TOKEN to the Queue! \n\n");
                    }
                }
            }
        } finally {
            logger.log(Level.INFO, " [ InfoToFile ] checkStatusAndStart( " + shouldStart + " ) returnVal: "
                    + returnValue + ", enabled=" + enabled.get());
        }

        return returnValue;
    }

    public static InfoToFile getInstance() {
        return _thisInstance;
    } // getInstance

    private InfoToFile() {
    } // InfotoFile

    private static final class WriterThread extends Thread {

        private final BufferedWriter writer;

        private final FileWriter fw;

        final BlockingQueue<String> theQueue = _thisInstance.theQueue;

        private WriterThread() throws IOException {
            // do a more log rotate like...or use a custom logger.
            fw = new FileWriter("connInfos");
            writer = new BufferedWriter(fw);
        }

        private final void drainQueue() throws IOException {
            logger.log(Level.INFO, " [ InfoToFile ] [ WriterThread ]  drainQueue started for " + theQueue.size()
                    + " elements");

            for (;;) {
                final String s = theQueue.poll();
                if (s == null) {
                    return;
                }

                //it's the same poisson pill; == is OK!
                if (s != POISON_PILL) {
                    writer.write(s);
                    writer.newLine();
                    writer.flush();
                }
            }
        }

        @Override
        public void run() {

            try {
                logger.log(Level.INFO, " [ InfoToFile ] [ WriterThread ] STARTED enabled(): " + enabled.get());

                Thread.currentThread().setName("(ML) InfoToFile Writer");

                while (enabled.get()) {
                    try {
                        final String s = theQueue.take();

                        // it's ok to go for identity
                        if (s == POISON_PILL) {
                            logger.log(Level.INFO, " [ InfoToFile ] [ WriterThread ] STOP_TOKEN received. enabled(): "
                                    + enabled.get());
                            break;
                        }

                        if (s == null) {
                            logger.log(Level.WARNING,
                                    " [ InfoToFile ] [ WriterThread ] got null String from Queue !!!?!???!!!! Ignoring it ");
                            continue;
                        }

                        writer.write(s);
                        writer.newLine();
                        writer.flush();

                    } catch (InterruptedException ie) {
                        logger.log(
                                Level.WARNING,
                                " [ InfoToFile ] Trying to start another WriterThread beacause this one got Interrupted Exception. ",
                                ie);
                        enabled.set(false);
                        checkStatusAndStart(true);
                        break;
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " [ InfoToFile ] Got general Exception ", t);
                    }
                }
            } finally {
                logger.log(Level.INFO,
                        " [ InfoToFile ] [ WriterThread ] will drainQueue() because finished main loop. enabled(): "
                                + enabled.get());

                if (!isInterrupted()) {
                    try {
                        drainQueue();
                    } catch (Throwable t) {
                        logger.log(Level.WARNING,
                                " [ InfoToFile ] [ WriterThread ] got exception on finally drainQueue. Cause: ", t);
                    }
                }

                if (fw != null) {
                    try {
                        fw.close();
                    } catch (Throwable ignore) {
                    }
                }

                if (writer != null) {
                    try {
                        writer.close();
                    } catch (Throwable ignore) {
                    }
                }
            }
        }
    }

    public boolean enabled() {
        return enabled.get();
    }

    public boolean writeToFile(final String s) {
        if (!enabled.get() || (s == null)) {
            return false;
        }

        try {
            if (!theQueue.offer(s)) {
                logger.log(Level.WARNING, " [ InfoToFile ] dropping logging info ( " + s + " ). Queue is full");
                return false;
            }

            return true;
        } catch (Throwable exp) {
            logger.log(Level.WARNING, " [ InfoToFile ] got Exception inserting in the queue", exp);
        } // try - catch

        return false;
    } // writeToFile

} // InfoToFile

