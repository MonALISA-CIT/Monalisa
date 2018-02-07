package lia.Monitor.modules;

import java.io.FileWriter;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ExtResult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.ntp.NTPDate;

/**
 * 
 * Used only from Ganglia for the moment
 * 
 **/
public final class ResultTimeWatcher extends Thread {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(ResultTimeWatcher.class.getName());

    private final String fileName;
    private final long logDTTime;
    private final Hashtable ht;
    private final long acceptedDTError;
    private boolean shouldWrite;
    private final boolean hasToRun;
    private long lastWrite;
    private static long CLEANUP_DELAY;

    static {
        try {
            //in miutes
            CLEANUP_DELAY = Long.valueOf(
                    AppConfig.getProperty("lia.Monitor.modules.ResultTimeWatcher.CLEANUP_DELAY", "60")).longValue() * 60 * 1000;
        } catch (Throwable t) {
            CLEANUP_DELAY = 60 * 60 * 1000;//1h
        }
    }

    private static final class RInfo {
        //update time
        private final long uTime;

        //result time
        private final long rTime;

        private final String NodeName;
        private final String ClusterName;

        public RInfo(String ClusterName, String NodeName, long rTime, long uTime) {
            this.NodeName = NodeName;
            this.ClusterName = ClusterName;
            this.uTime = uTime;
            this.rTime = rTime;
        }
    }

    public ResultTimeWatcher(String fileName, long acceptedDTError, long logTimeDelay) {
        super("( ML ) ResultTimeWatcher Thread");
        ht = new Hashtable();
        this.fileName = fileName;
        this.acceptedDTError = acceptedDTError;
        this.logDTTime = logTimeDelay;
        shouldWrite = false;
        hasToRun = true;
    }

    public void add(Object o) {

        long cTime = NTPDate.currentTimeMillis();

        if (o == null) {
            return;
        }
        if (o instanceof Result) {
            Result r = (Result) o;
            add(r.ClusterName, r.NodeName, r.time, cTime);
        } else if (o instanceof eResult) {
            eResult r = (eResult) o;
            add(r.ClusterName, r.NodeName, r.time, cTime);
        } else if (o instanceof ExtResult) {
            ExtResult r = (ExtResult) o;
            add(r.ClusterName, r.NodeName, r.time, cTime);
        }
    }

    private void add(String ClusterName, String NodeName, long rTime, long cTime) {
        if ((NodeName == null) || (ClusterName == null)) {
            return;
        }

        String key = ClusterName + "/" + NodeName;
        long diff = Math.abs(cTime - rTime);
        if (diff > acceptedDTError) {
            synchronized (ht) {
                if (!ht.containsKey(key)) {
                    shouldWrite = true;
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "ResultTimeWatcher adding key " + key + " cTime=" + new Date(cTime)
                                + " rTime=" + new Date(rTime));
                    }
                }
                ht.put(key, new RInfo(ClusterName, NodeName, rTime, cTime));
            }//end sync
        } else {//should delete if the node has now the correct time
            synchronized (ht) {
                if (ht.containsKey(key)) {
                    ht.remove(key);
                    shouldWrite = true;
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "ResultTimeWatcher REMOVING key " + key + " cTime=" + new Date(cTime)
                                + " rTime=" + new Date(rTime));
                    }
                }
            }
        }
    }

    private String fancyPrint() {
        StringBuilder sb = new StringBuilder();
        sb.setLength(0);
        sb.append("#Last Update  @ ").append(new Date(NTPDate.currentTimeMillis()).toString()).append("\n");
        sb.append("Node\t\t\tNodeTime\t\t\tMLTime\n");
        synchronized (ht) {
            for (Enumeration en = ht.elements(); en.hasMoreElements();) {
                RInfo ri = (RInfo) en.nextElement();
                sb.append(ri.NodeName).append("\t\t\t");
                sb.append(new Date(ri.rTime)).append("\t\t\t");
                sb.append(new Date(ri.uTime)).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    private void log() {

        lastWrite = NTPDate.currentTimeMillis();
        String toPrint = null;
        try {
            //could take a while ... make a copy first
            toPrint = fancyPrint();
            FileWriter fw = new FileWriter(fileName);
            fw.write(toPrint);
            fw.flush();
            fw.close();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "ResultTimeWatcher cannot write to file", t);
            if (toPrint != null) {
                logger.log(Level.WARNING, "Should have logged ... \n" + toPrint);
            }
        }
    }

    private void cleanup() {
        long cTime = NTPDate.currentTimeMillis();

        synchronized (ht) {
            for (Enumeration en = ht.keys(); en.hasMoreElements();) {
                Object key = en.nextElement();
                RInfo ri = (RInfo) ht.get(key);
                if ((ri.uTime + CLEANUP_DELAY) < cTime) {
                    ht.remove(key);
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "ResultTimeWatcher removing key " + key + " cTime="
                                + new Date(ri.uTime) + " rTime=" + new Date(ri.rTime));
                    }
                }
            }
        }
    }

    @Override
    public void run() {
        logger.log(Level.INFO, "ResultTimeWatcher started ... log file: " + fileName);
        while (hasToRun) {
            try {
                try {
                    Thread.sleep(60 * 1000);
                } catch (Throwable t1) {

                }

                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, " ResultTimeWatcher ht size " + ht.size());
                    logger.log(Level.FINEST, " ResultTimeWatcher ht size " + fancyPrint());
                }

                if (ht.size() > 0) {
                    synchronized (ht) {
                        if ((lastWrite + logDTTime) < NTPDate.currentTimeMillis()) {
                            shouldWrite = true;
                        }
                    }
                    if (shouldWrite) {
                        try {
                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, " log() ht.size()" + ht.size());
                            }
                            log();
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, " ResultTimeWatcher got exc log()", t);
                        }
                        shouldWrite = false;
                    }
                    cleanup();
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "ResultTimeWatcher got exc main loop", t);
            }
        }
    }
}
