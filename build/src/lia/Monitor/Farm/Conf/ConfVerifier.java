/*
 * $Id: ConfVerifier.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.Farm.Conf;

import java.util.Vector;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.util.MFarmConfigUtils;
import lia.util.Utils;

/**
 * Used to monitor the configuration
 * 
 * @author ramiro
 */
public class ConfVerifier extends Thread implements AppConfigChangeListener {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(ConfVerifier.class.getName());

    private final AtomicBoolean hasToRun = new AtomicBoolean(true);

    private static volatile ConfVerifier _thisInstance = null;

    private final MFarm farm;
    private final ConcurrentMap<String, CCluster> clustersTimeoutMap;

    private final DelayQueue<AbstractConfigTimeoutItem> timeoutQueue = new DelayQueue<AbstractConfigTimeoutItem>();

    private final AtomicBoolean internalMonitoringThreadRunning = new AtomicBoolean(false);

    private final AtomicLong takeCount = new AtomicLong(0);

    private final class ConfVerifierInternalMonitoring extends Thread {

        ConfVerifierInternalMonitoring() {
            setName("(ML) ConfVerifierInternalMonitoring");
        }

        @Override
        public void run() {
            while (internalMonitoringThreadRunning.get()) {
                try {
                    Thread.sleep(60 * 1000);
                } catch (InterruptedException ie) {
                    Thread.interrupted();
                } catch (Throwable ignore) {
                }

                StringBuilder sb = new StringBuilder();
                if (logger.isLoggable(Level.FINER)) {
                    sb.append("[ ConfVerifier ] [ WAITQUEUE ] { Head: ").append(timeoutQueue.peek())
                            .append("\n Queue Size: ").append(timeoutQueue.size());
                    if (logger.isLoggable(Level.FINEST)) {
                        sb.append("\n The Queue: \n");
                        for (AbstractConfigTimeoutItem abstractConfigTimeoutItem : timeoutQueue) {
                            sb.append("\n").append(abstractConfigTimeoutItem);
                        }
                    }
                    sb.append("\n The current config : \n").append(MFarmConfigUtils.getMFarmDump(farm));
                }
                logger.log(Level.FINER, sb.toString());
            }
        }
    }

    public static final ConfVerifier initInstance(final MFarm farm,
            final ConcurrentMap<String, CCluster> clustersTimeoutMap) {
        synchronized (ConfVerifier.class) {
            if (_thisInstance == null) {
                _thisInstance = new ConfVerifier(farm, clustersTimeoutMap);
                _thisInstance.start();
            }
        }

        return _thisInstance;
    }

    private ConfVerifier(MFarm farm, final ConcurrentMap<String, CCluster> clustersTimeoutMap) {
        super("(ML) Config Timeout Verifier Thread [N/A]");
        this.setDaemon(true);
        this.hasToRun.set(true);
        this.farm = farm;
        this.clustersTimeoutMap = clustersTimeoutMap;
        AppConfig.addNotifier(this);
        notifyAppConfigChanged();
    }

    public static ConfVerifier getInstance() {
        return _thisInstance;
    }

    public int getWaitQueueSize() {
        return this.timeoutQueue.size();
    }

    @Override
    public void run() {
        logger.log(Level.INFO, " [ ConfVerifier ] Config verifier thread started ");
        while (hasToRun.get()) {
            try {

                AbstractConfigTimeoutItem item = null;
                try {
                    item = timeoutQueue.take();
                } catch (InterruptedException ie) {
                    logger.log(Level.WARNING, " [ ConfVerifier ] [ HANDLED ] Conf verifier thread was interrupted", ie);
                    Thread.interrupted();
                } catch (Throwable t) {
                    logger.log(Level.WARNING,
                            " [ ConfVerifier ] [ HANDLED ] Conf verifier thread got general exception while sleeping",
                            t);
                }

                takeCount.incrementAndGet();
                if (item == null) {
                    continue;
                }

                // it's too much ... but not safe otherwise
                synchronized (farm) {
                    final long nanoReference = Utils.nanoNow();
                    if (nanoReference > item.nextTimeoutNano.get()) {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "[ ConfVerifier ] [ TIMEOUT ] Item [ " + item + " ]");
                        }
                        try {
                            item.timedOut();
                        } catch (Throwable t) {
                            logger.log(
                                    Level.WARNING,
                                    " [ ConfVerifier ] [ HANDLED ] got exception notifying timeout to the item " + item,
                                    t);
                        }

                    } else {
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, "[ ConfVerifier ] [ RESCHEDULE ] Item [ " + item + " ]");
                        }
                        reschedule(item);
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ ConfVerifier ] Got Exception in main loop:", t);
            }
        }// while

        logger.log(Level.INFO, " [ ConfVerifier ] finished. hasToRun = " + hasToRun.get());
    }// run()

    void removeCluster(CCluster cc) {

        final String clusterName = cc.name;

        try {
            if (logger.isLoggable(Level.FINER)) {
                StringBuilder sb = new StringBuilder();
                sb.append("[ ConfVerifier ] [ removeCluster ] Cluster : ").append(clusterName)
                        .append(" expired. Clusters list before remove: ").append(farm.getClusters());
                if (logger.isLoggable(Level.FINEST)) {
                    sb.append("\n Farm dump before remove: ").append(MFarmConfigUtils.getMFarmDump(farm)).append("\n");
                }
                logger.log(Level.FINER, sb.toString());
            }

            final boolean bRemoveCluster = farm.removeCluster(clusterName);

            logger.log(Level.INFO, "[ ConfVerifier ] [ removeCluster ] Cluster : " + clusterName
                    + " expired. remove code: " + bRemoveCluster);

            if (logger.isLoggable(Level.FINE)) {
                // eResult with null was sent before
                StringBuilder sb = new StringBuilder();
                if (!bRemoveCluster) {
                    sb.append("[ ConfVerifier ] [ removeCluster ] [ HANDLED ]: No such cluster [ ").append(clusterName)
                            .append(" ] in real MFarm conf...Just in cache");
                    if (logger.isLoggable(Level.FINER)) {
                        sb.append("\nReal Config after not removing Cluster: ").append(clusterName).append(":\n\n")
                                .append(MFarmConfigUtils.getMFarmDump(farm)).append("\n\n");
                    }
                } else {
                    sb.append("[ ConfVerifier ] [ removeCluster ] Removed cluster [ ").append(clusterName)
                            .append(" ] from real MFarm conf");
                    if (logger.isLoggable(Level.FINER)) {
                        sb.append("\nReal Config after removing Cluster: ").append(clusterName).append(":\n\n")
                                .append(MFarmConfigUtils.getMFarmDump(farm)).append("\n\n");
                    }
                }
                logger.log(Level.FINE, sb.toString());
            }
        } finally {
            clustersTimeoutMap.remove(clusterName);
        }
    }

    void removeNode(CNode cn) {
        final String nodeName = cn.name;
        final CCluster cc = cn.cCluster;
        final String clusterName = cc.name;

        try {

            final MCluster mc = farm.getCluster(clusterName);
            if (mc == null) {
                logger.log(Level.INFO, " [ ConfVerifier ] [ removeNode ] The cluster: " + clusterName
                        + " is already removed. Had to remove Cluster/Node: " + clusterName + "/" + nodeName);
                return;
            }

            if (logger.isLoggable(Level.FINER)) {
                StringBuilder sb = new StringBuilder();
                sb.append("[ ConfVerifier ] [ removeNode ] Node: ").append(nodeName).append(" | Cluster : ")
                        .append(clusterName).append(" expired. Nodes list before remove: ").append(mc.getNodes());
                if (logger.isLoggable(Level.FINEST)) {
                    sb.append("\n Farm dump before remove: ").append(MFarmConfigUtils.getMFarmDump(farm)).append("\n");
                }
                logger.log(Level.FINER, sb.toString());
            }

            boolean bRemoveNode = mc.removeNode(nodeName);
            logger.log(Level.INFO, "[ ConfVerifier ] [ removeNode ] Node: " + nodeName + " | Cluster : " + clusterName
                    + " expired. remove code: " + bRemoveNode);

            if (logger.isLoggable(Level.FINE)) {
                // eResult with null was sent before
                StringBuilder sb = new StringBuilder();
                if (!bRemoveNode) {
                    sb.append(" [ ConfVerifier ] [ removeNode ] [ Handled ]: No such Node [ ").append(nodeName)
                            .append(" ] Cluster [ ").append(clusterName)
                            .append(" ] in real MFarm conf...Just in cache");
                    if (logger.isLoggable(Level.FINER)) {
                        sb.append("\nReal Config after not removing Node [ ").append(nodeName).append(" ] Cluster [ ")
                                .append(clusterName).append("]:\n\n").append(MFarmConfigUtils.getMFarmDump(farm))
                                .append("\n\n");
                    }
                } else {
                    sb.append(" [ ConfVerifier ] [ removeNode ] Removed Node [ ").append(nodeName)
                            .append(" ] Cluster [ ").append(clusterName).append(" ] removed from real MFarm conf");
                    if (logger.isLoggable(Level.FINER)) {
                        sb.append("\nReal Config after removing Node [ ").append(nodeName).append(" ] Cluster [ ")
                                .append(clusterName).append("]:\n\n").append(MFarmConfigUtils.getMFarmDump(farm))
                                .append("\n\n");
                    }
                }
                logger.log(Level.FINE, sb.toString());
            }
        } finally {
            cc.cNodes.remove(nodeName);
        }
    }

    public long getTakeCount() {
        return takeCount.get();
    }

    void removeParam(CParam cp) {

        final CNode cn = cp.cNode;
        final CCluster cc = cn.cCluster;
        final String clusterName = cc.name;
        final String nodeName = cn.name;
        final String paramName = cp.name;

        try {
            final MCluster mc = farm.getCluster(clusterName);
            if (mc == null) {
                logger.log(Level.INFO, " [ ConfVerifier ] [ removeParam ] The cluster: " + clusterName
                        + " is already removed. Had to remove Cluster/Node/Param: " + clusterName + "/" + nodeName
                        + "/" + paramName);
                return;
            }

            final MNode mn = mc.getNode(nodeName);
            if (mn == null) {
                logger.log(Level.INFO, " [ ConfVerifier ] [ removeParam ] The node: " + nodeName
                        + " is already removed. Had to remove Cluster/Node/Param: " + clusterName + "/" + nodeName
                        + "/" + paramName);
                return;
            }

            final Vector<String> plist = mn.getParameterList();
            if (logger.isLoggable(Level.FINER)) {
                StringBuilder sb = new StringBuilder();
                sb.append("[ ConfVerifier ] [ removeParam ] Param: ").append(paramName).append("  Node: ")
                        .append(nodeName).append(" | Cluster : ").append(clusterName)
                        .append(" expired. Params list before remove: ").append(plist);
                if (logger.isLoggable(Level.FINEST)) {
                    sb.append("\n Farm dump before remove: ").append(MFarmConfigUtils.getMFarmDump(farm)).append("\n");
                }
                logger.log(Level.FINER, sb.toString());
            }
            final boolean bRemoveParam = plist.remove(paramName);
            logger.log(Level.INFO, "[ ConfVerifier ] [ removeParam ] Param: " + paramName + "  Node: " + nodeName
                    + " | Cluster : " + clusterName + " expired. remove code: " + bRemoveParam);

            if (logger.isLoggable(Level.FINE)) {
                StringBuilder sb = new StringBuilder();
                if (!bRemoveParam) {
                    sb.append(" [ ConfVerifier ] [ removeParam ] [ Handled ]: No such Param [ ").append(paramName)
                            .append(" ] Node [ ").append(nodeName).append(" ] Cluster [ ").append(clusterName)
                            .append(" ] in real MFarm conf...Just in cache");
                    if (logger.isLoggable(Level.FINER)) {
                        sb.append("\nReal Config after not removing Param [ ").append(paramName).append(" ] Node [ ")
                                .append(nodeName).append(" ] Cluster [ ").append(clusterName).append("]:\n\n")
                                .append(MFarmConfigUtils.getMFarmDump(farm)).append("\n\n");
                    }
                } else {
                    sb.append(" [ ConfVerifier ] [ removeParam ] [ Handled ] Removed Param [ ").append(paramName)
                            .append(" ] Node [ ").append(nodeName).append(" ] Cluster [ ").append(clusterName)
                            .append(" ] from real MFarm conf");
                    if (logger.isLoggable(Level.FINER)) {
                        sb.append("\nReal Config after not removing Param [ ").append(paramName).append(" ] Node [ ")
                                .append(nodeName).append(" ] Cluster [ ").append(clusterName).append("]:\n\n")
                                .append(MFarmConfigUtils.getMFarmDump(farm)).append("\n\n");
                    }
                }
                logger.log(Level.FINE, sb.toString());
            }
        } finally {
            cn.cParams.remove(paramName);
        }

    }

    public final void reschedule(final AbstractConfigTimeoutItem item) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [ ConfVerifier ] reschedule for " + item);
        }
        item.reschedule();
        timeoutQueue.offer(item);
    }

    @Override
    public void notifyAppConfigChanged() {
        final String sLevel = AppConfig.getProperty("lia.Monitor.Farm.Conf.level");
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
            if (internalMonitoringThreadRunning.compareAndSet(false, true)) {
                new ConfVerifierInternalMonitoring().start();
            }
        } else {
            internalMonitoringThreadRunning.set(false);
        }
    }
}
