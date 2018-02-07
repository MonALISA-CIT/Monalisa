package lia.Monitor.ClientsFarmProxy;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MFarm;
import lia.util.MFarmConfigUtils;

/**
 * 
 * Helper class which prints the configurations in $PROXY_HOME/confs_dir/MFarm.name
 * @author ramiro
 * 
 */
class MFarmConfDebugger extends Thread {

    private static final Logger logger = Logger.getLogger(MFarmConfDebugger.class.getName());

    private final BlockingQueue<MFarmDebugEntry> logQueue = new LinkedBlockingQueue<MFarmDebugEntry>(100);

    private final static class MFarmDebugEntry {
        private final MFarm oldConf;
        private final MFarm newConf;
        private final MFarm[] diffConf;

        private MFarmDebugEntry(final MFarm oldConf, final MFarm newConf, final MFarm[] diffConf) {
            this.oldConf = oldConf;
            this.newConf = newConf;
            this.diffConf = diffConf;
        }

        private void write(Writer writer) throws IOException {
            writer.write("\n\n\n -------- START DUMP [ ");
            writer.write(new Date().toString());
            writer.write(" ] --------\n\n oldConf:\n");
            writer.write(MFarmConfigUtils.getMFarmDump(oldConf));
            writer.write("\n--------newConf:\n");
            writer.write(MFarmConfigUtils.getMFarmDump(newConf));
            if (diffConf == null) {
                writer.write("\n--------diffConf == null ---- \n");
            } else {
                writer.write("\n--------diff[0]:\n");
                writer.write(MFarmConfigUtils.getMFarmDump(diffConf[0]));
                writer.write("\n--------diff[1]:\n");
                writer.write(MFarmConfigUtils.getMFarmDump(diffConf[1]));
            }
            writer.write("\n\n -------- END DUMP [ ");
            writer.write(new Date().toString());
            writer.write(" ] --------\n");
        }
    }

    private final AtomicBoolean hasToRun = new AtomicBoolean(true);
    private final AtomicLong eventsReceived = new AtomicLong(0);
    private final AtomicLong eventsDropped = new AtomicLong(0);
    private long writeErrors = 0;
    private long eventsLogged = 0;

    @Override
    public void run() {
        setName("(ML) MFarm Cofiguration Debugger Thread");
        String logDir = null;
        try {
            final String proxyHome = AppConfig.getProperty("lia.Monitor.monitor.proxy_home", ".");
            logDir = proxyHome + File.separator + "confs_debug";
            new File(logDir).mkdirs();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ MFarmConfDebugger ] unable to initialize");
        }

        while (hasToRun.get()) {
            try {
                MFarmDebugEntry debugEntry = null;

                try {
                    debugEntry = logQueue.poll(20, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    logger.log(Level.WARNING, " [ MFarmConfDebugger ] got interrupted exception waiting for queue", ie);
                    Thread.interrupted();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " [ MFarmConfDebugger ] got general exception waiting for queue", t);
                }

                if (debugEntry == null) {
                    continue;
                }

                FileWriter fw = null;
                BufferedWriter bw = null;
                String fName = null;
                if (debugEntry.oldConf != null) {
                    fName = debugEntry.oldConf.name;
                } else {
                    if (debugEntry.newConf != null) {
                        fName = debugEntry.newConf.name;
                    }
                }

                if (fName == null) {
                    fName = "SERVICE_NAME_NOT_AVAILABLE";
                }

                String fileName = logDir + File.separator + fName;
                try {
                    fw = new FileWriter(fileName, true);
                    bw = new BufferedWriter(fw, 16384);
                    debugEntry.write(bw);
                    eventsLogged++;
                } catch (Throwable t) {
                    writeErrors++;
                    logger.log(Level.WARNING, " [ MFarmConfDebugger ] got exception trying to write to: " + fileName, t);
                } finally {
                    if (bw != null) {
                        try {
                            bw.close();
                        } catch (Throwable ignore) {
                        }
                    }
                    if (fw != null) {
                        try {
                            fw.close();
                        } catch (Throwable ignore) {
                        }
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ MFarmConfDebugger ] got exception main loop", t);
            }
        }

        logQueue.clear();
        logger.log(Level.INFO, " [ MFarmConfDebugger ] finished ... Events Stats: " + "eventsReceived="
                + eventsReceived.get() + ",eventsDropped=" + eventsDropped.get() + ",eventsLogged=" + eventsLogged
                + "eventsWriteError" + writeErrors);
    }

    void addDebugEntry(final MFarm oldConf, final MFarm newConf, final MFarm[] diffConf) {
        eventsReceived.incrementAndGet();
        if (logQueue.offer(new MFarmDebugEntry(oldConf, newConf, diffConf))) {
            eventsDropped.incrementAndGet();
        }
    }

    void stopIT() {
        hasToRun.set(false);
    }
}
