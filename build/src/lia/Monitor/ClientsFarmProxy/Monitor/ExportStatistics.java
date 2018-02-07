/*
 * $Id: ExportStatistics.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.ClientsFarmProxy.Monitor;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetAddress;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import apmon.ApMon;

/**
 * 
 * @author mickyt
 */
public class ExportStatistics extends Thread {

    private static final Logger logger = Logger.getLogger(ExportStatistics.class.getName());

    final ApMon apm;

    final String clusterName;

    final String nodeName;

    private static final int MAX_QUEUE_SIZE;
    private final BlockingQueue<ApMonParamEntry> sendQueue;

    int MAX_MED_LOAD = 10;

    private static final ExportStatistics _thisInstance;

    static {
        MAX_QUEUE_SIZE = AppConfig.geti("lia.Monitor.ClientsFarmProxy.Monitor.ExportStatistics.MAX_QUEUE_SIZE", 2000);
        ExportStatistics tmpInstance = null;
        try {
            tmpInstance = new ExportStatistics();
            tmpInstance.start();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ ExportStatistics ] got exception while trying to initialize", t);
            tmpInstance = null;
        }
        _thisInstance = tmpInstance;
    }

    public static final ExportStatistics getInstance() {
        return _thisInstance;
    }

    private static final class ApMonParamEntry {

        final String paramName;

        final Object paramValue;

        final Integer paramType;

        ApMonParamEntry(String paramName, Object paramValue, Integer paramType) {
            this.paramName = paramName;
            this.paramValue = paramValue;
            this.paramType = paramType;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(" ApMonParamEntry - param=").append(paramName).append(", value=").append(paramValue)
                    .append(", type=").append(paramType);
            return sb.toString();
        }
    }

    private ExportStatistics() throws Exception {

        ApMon tmpApMon = null;
        final String sConfig = AppConfig.getProperty("lia.Monitor.ClientsFarmProxy.apMon",
                "http://mickyt.rogrid.pub.ro/ProxyMonitorUrl").trim();
        logger.log(Level.INFO, "ApMon Configuration file : " + sConfig);
        if (sConfig.toLowerCase().startsWith("http://")) {
            Vector<String> v = new Vector<String>();
            v.add(sConfig);
            tmpApMon = new ApMon(v);
        } else {
            tmpApMon = new ApMon(sConfig);

        } // if - else

        apm = tmpApMon;
        String sHost = "";
        String sShortHost = "";
        try {
            sShortHost = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
        }
        try {
            sHost = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (Exception ex) {
        }

        clusterName = AppConfig.getProperty("lia.Monitor.ClientsFarmProxy.ProxyGroup", "farm_proxy");
        nodeName = sHost.length() > sShortHost.length() ? sHost : sShortHost;

        if (apm != null) {
            apm.setMonitorClusterNode(clusterName, nodeName);
        } // if
        sendQueue = new LinkedBlockingQueue<ApMonParamEntry>(MAX_QUEUE_SIZE);
    }

    @Override
    public void run() {
        Thread.currentThread().setName(" ( ML ) - ClientsFarmProxyMonitor - ExportStatistics - Thread");

        for (;;) {
            try {
                final ApMonParamEntry apmonParamEntry = sendQueue.take();
                if (apmonParamEntry == null) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, " [ ExportStatistics ] Got null apmonParamEntry from queue ??");
                    }
                    continue;
                }

                apm.sendParameter(clusterName, nodeName, apmonParamEntry.paramName, apmonParamEntry.paramType,
                        apmonParamEntry.paramValue);
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, " [ ExportStatistics ] Sent param " + apmonParamEntry);
                }

                //do not loop....
                try {
                    Thread.sleep(100 + ((long) Math.random() * 4));
                } catch (Throwable t) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, " [ ExportStatistics ] got exception sendig param: " + apmonParamEntry,
                                t);
                    }
                }

            } catch (InterruptedException ie) {
                logger.log(Level.WARNING, "[ ExportStatistics ] Interrupted exception main loop", ie);
                Thread.interrupted();
            } catch (Throwable e) {
                logger.log(Level.WARNING, "[ ExportStatistics ] General exception main loop ", e);
            } finally {
            }
        }//main loop
    }

    public void addParam(String param, Integer type, Object value) {
        if (_thisInstance == null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ ExportStatistics ] ignoring param " + param
                        + " because the thread was not started");
            }
            return;
        }

        if ((param == null) || (type == null) || (value == null)) {
            return;
        }
        final ApMonParamEntry entry = new ApMonParamEntry(param, value, type);
        if (!sendQueue.offer(entry)) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ ExportStatistics ] ignoring param " + param
                        + " because the queue is full....");
            }
        } else {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINE, " [ ExportStatistics ] Adding param " + param + " to the queue ...");
            }
        }
    } // addParam

    public double getMediatedLoad() {
        double retv = -1;

        FileReader fr = null;
        BufferedReader br = null;
        try {
            fr = new FileReader("/proc/loadavg");
            br = new BufferedReader(fr);
            final String line = br.readLine();
            retv = Double.valueOf(line.split("\\s")[1]);
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ ExportStatistics ] exception in getMediated load", t);
            }
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignore) {
                }
            }
            if (fr != null) {
                try {
                    fr.close();
                } catch (Throwable ignore) {
                }
            }
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, " [ ExportStatistics ] getMediatedLoad returns " + retv);
        }
        return retv;
    }

}
