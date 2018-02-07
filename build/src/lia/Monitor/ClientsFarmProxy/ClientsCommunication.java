/*
 * $Id: ClientsCommunication.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.ClientsFarmProxy;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonMessageClientsProxy;
import lia.Monitor.monitor.monPredicate;
import lia.util.Utils;
import lia.util.net.Util;
import net.jini.core.lookup.ServiceID;

/**
 * TODO mda ... cred ca o sa sara in aer clasa asta cu totu' in curand ( ramiro )
 * 
 * @author micky
 */
public final class ClientsCommunication {

    private static final Logger logger = Logger.getLogger(ClientsCommunication.class.getName());

    private static final ServiceI service = Service.getServiceI(); // the service that is registered in LUSs

    private static final ConcurrentSkipListMap<Integer, ClientWorker> clients = new ConcurrentSkipListMap<Integer, ClientWorker>(); // clients

    public static final MFarm[] NO_DIFF_CONF = new MFarm[0];

    // for generation unique ids for clients
    private static final AtomicInteger ID_SEQ = new AtomicInteger(0);

    private static final ConcurrentLinkedQueue<Integer> reusedIDs = new ConcurrentLinkedQueue<Integer>();

    private static final ConcurrentSkipListMap<Integer, ClientAccountingInfo> clientsAccInfo = new ConcurrentSkipListMap<Integer, ClientAccountingInfo>();

    private static final AtomicBoolean debuggerThreadStarted = new AtomicBoolean(false);

    private static volatile MFarmConfDebugger configDebugger = null;

    private static final Object configDebuggerLock = new Object();

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
    }

    static final class ClientAccountingInfo {

        final double totalRate;

        final double confRate;

        ClientAccountingInfo(double totalRate, double confRate) {
            this.totalRate = totalRate;
            this.confRate = confRate;
        }
    }

    private static final Integer getID() {
        Integer retV = reusedIDs.poll();
        if (retV == null) {
            retV = Integer.valueOf(ID_SEQ.getAndIncrement());
        }
        return retV;
    } // getID

    public static final ClientWorker addClientWorker(ProxyTCPWorker ptw) {
        if (ptw == null) {
            logger.log(Level.WARNING, "Cannot create Client WORKER!! ptw == null");
            return null;
        } // if

        // generate a new id for the comming client
        final Integer clientID = getID();
        ClientWorker cw = new ClientWorker(clientID, ptw); // a new worker for
        clients.put(clientID, cw);
        return cw;
    }

    public static final int getNumberOfClients() {
        return clients.size();
    }

    public static final void addClientRate(Integer clientID, double totalRate, double confRate) {
        if (clientID != null) {
            clientsAccInfo.put(clientID, new ClientAccountingInfo(totalRate, confRate));
        }
    } // addClientRate

    public static final Map<Integer, ClientAccountingInfo> getClienAccountingInfo() {
        return clientsAccInfo;
    } // getClientMsgRate

    // public static final void sendBcastMsg(final Object msg) {
    //
    // byte[] sMsg;
    //
    // final long cTime = Utils.nanoTimeReference();
    //
    // try {
    // sMsg = Utils.writeDirectObject(msg);
    // } catch (Throwable t) {
    // sMsg = null;
    // // OOM can be the one and only >>> ????
    // logger.log(Level.WARNING,
    // "[ ClientsCommunication ] [ BROADCAST MSG ] [ HANDLED ] Got exception serialiazing Objs", t);
    // }
    //
    // final long confDT = Utils.nanoTimeReference() - cTime;
    //
    // final Object oToWrite = ((sMsg == null) ? msg : (Object) sMsg);
    //
    // if (logger.isLoggable(Level.FINER)) {
    //
    // StringBuilder sb = new StringBuilder();
    // sb.append(" [ ClientsCommunication ] [ BROADCAST MSG ] sending ");
    //
    // if (sMsg == null) {
    // sb.append(" normal msg ");
    // } else {
    // sb.append(" serialized msg ( ").append(sMsg.length).append(" bytes ) DT compress: ").append(TimeUnit.NANOSECONDS.toMillis(confDT)).append(" ms ");
    // }
    //
    // sb.append("to all clients ( ").append(clients.size()).append(" ) ");
    //
    // logger.log(Level.FINER, sb.toString());
    // }
    //
    // for (final ClientWorker cw : clients.values()) {
    // try {
    // if (cw != null) {
    // cw.sendMsg(oToWrite);
    // }
    // } catch (Throwable t) {
    // logger.log(Level.WARNING, " [ ClientsCommuncation ] [ BROADCAST MSG ] Exception trying to send bcast msg: " +
    // oToWrite + " to: " + cw, t);
    // } // try - catch
    // } // for
    //
    // } // sendBcastMsg

    public static final void sendBcastMsg(final MonMessageClientsProxy msg) {

        for (final ClientWorker cw : clients.values()) {
            try {
                if (cw != null) {
                    cw.sendMsg(msg);
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING,
                        " [ ClientsCommuncation ] [ BROADCAST MSG ] Exception trying to send bcast msg: " + msg
                                + " to: " + cw, t);
            } // try - catch
        } // for

    } // sendBcastMsg

    // deallocates a client - it is call when the coneection with the client is
    // lost
    public static final void removeClient(Integer clientID) {
        clients.remove(clientID);
        service.unregisterMessages(clientID);
        reusedIDs.offer(clientID);
    } // removeClient

    public static final void addSendMessage(Integer clientID, Object message) {

        try {
            MonMessageClientsProxy mm = (MonMessageClientsProxy) message;
            if ((mm.ident != null) && (mm.ident instanceof Integer)) { // a predicate
                // verify times
                monPredicate pred = (monPredicate) mm.result;
                if (pred != null) {
                    if ((pred.tmax < 0) || ((pred.tmax > 0) && (pred.tmin <= 0))) {
                        monPredicate pred1 = new monPredicate(pred.Farm, pred.Cluster, pred.Node, 0, -1,
                                pred.parameters, pred.constraints);
                        pred1.bLastVals = pred.bLastVals;
                        MonMessageClientsProxy newmm = new MonMessageClientsProxy(mm.tag, mm.ident, pred1, mm.farmID);

                        pred.tmax = 0;

                        sendMsgToFarm((MonMessageClientsProxy) message, clientID);
                        sendMsgToFarm(newmm, clientID);
                        return;
                    } // if
                } // if
            } // if

            sendMsgToFarm((MonMessageClientsProxy) message, clientID);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got exception while sending message", t);
        }
    } // addReceivedMessage

    private static final void sendMsgToFarm(MonMessageClientsProxy message, Integer clientID) throws Exception {
        ServiceCommunication.sendMessage(message, clientID);
    }

    // public static final void addReceivedMessage(Integer clientID, Object message) {
    // final ClientWorker cw = clients.get(clientID);
    // if (cw != null) {
    // cw.sendMsg(message);
    // }
    // } // addReceivedMessage

    public static final Set<Integer> getClientsKeys() {
        return clients.keySet();
    } // getClientsKeys

    public static final Collection<ClientWorker> getClients() {
        return clients.values();
    } // getClients

    public static final ClientWorker getClient(Integer clientID) {
        return clients.get(clientID);
    }

    public static final String getClientName(final Integer clientID) {
        if (clientID == null) {
            return null;
        }
        final ClientWorker cw = clients.get(clientID);
        if (cw != null) {
            return cw.getName();
        }
        return null;
    }

    public static final MFarm[] addFarmConfiguration(final ServiceID farmID, final MonMessageClientsProxy configuration) {

        final long sTime = Utils.nanoNow();
        final boolean isFinest = logger.isLoggable(Level.FINEST);
        final boolean isFiner = (isFinest || logger.isLoggable(Level.FINER));
        final boolean isFine = (isFiner || logger.isLoggable(Level.FINE));

        if (farmID == null) {
            logger.log(Level.SEVERE, "\n\n~~~~~~ServiceID == null in ClientsCommunication~~~~dumping stack:\n",
                    new Exception("Debug Exception: ServiceID == null in ClientsCommunication"));
            return NO_DIFF_CONF;
        }

        if (configuration == null) {
            logger.log(Level.SEVERE, "\n\n~~~~~~configuration == null in ClientsCommunication~~~~dumping stack:\n",
                    new Exception("Debug Exception: configuration == null in ClientsCommunication"));
            return NO_DIFF_CONF;
        } // if

        if (configuration.result == null) {
            logger.log(Level.SEVERE,
                    "\n\n~~~~ configuration.result == null in ClientsCommunication~~~~dumping stack:\n", new Exception(
                            "Debug Exception: configuration == null in ClientsCommunication"));
            return NO_DIFF_CONF;
        } // if

        final FarmWorker fw = FarmCommunication.getFarmsHash().get(farmID);
        MFarmClientConfigInfo lastConfMon = fw.getClientConfigInfo();

        MFarm[] diff = NO_DIFF_CONF;
        MFarm newConf = (MFarm) configuration.result;

        try {
            // TODO - create the last difference
            final MFarm oldConf = (lastConfMon != null) ? (MFarm) lastConfMon.getConfigMessage().result : null;
            long dtComp = 0;

            if (oldConf != null) {
                final long sTimeComp = Utils.nanoNow();
                try {
                    diff = CompConfigV2.compareClusters(newConf, oldConf, lastConfMon);
                } catch (Throwable t) {
                    logger.log(Level.WARNING,
                            " [ ClientsCommunication ] comconfigV2 ignoring difff ... Got milk? ( exception ): ", t);
                    diff = null;
                }
                // diff = MFarmConfigUtils.compareClusters(newConf, oldConf, lastConfMon, debugInfo);
                dtComp = TimeUnit.NANOSECONDS.toMillis(Utils.nanoNow() - sTimeComp);
            } else {

                // THIS IS PARSED ONLY FIRST TIME!!
                lastConfMon = new MFarmClientConfigInfo(farmID, configuration, 0, 0);
                fw.getAndSetClientConfigInfo(lastConfMon);

            }// else - first time

            if (isFine) {
                StringBuilder sb = new StringBuilder();
                sb.append("\n\n[ ClientsCommunication ] addFarmConfiguration status for ").append(lastConfMon);
                sb.append("\n DtCompare: ").append(dtComp).append(" ms");
                sb.append("\n DtTotal: ").append(TimeUnit.NANOSECONDS.toMillis(Utils.nanoNow() - sTime)).append(" ms");
                logger.log(Level.FINE, sb.toString());
            }

            if (isFinest) {
                synchronized (configDebuggerLock) {
                    if (debuggerThreadStarted.compareAndSet(false, true)) {
                        configDebugger = new MFarmConfDebugger();
                        configDebugger.start();
                        configDebuggerLock.notifyAll();
                    } else {
                        while (configDebugger == null) {
                            try {
                                configDebuggerLock.wait();
                            } catch (InterruptedException ie) {
                                logger.log(
                                        Level.WARNING,
                                        " [ ClientsCommunication ] got interrupted exception while waiting for logging",
                                        ie);
                                Thread.interrupted();
                            } catch (Throwable t) {
                                logger.log(Level.WARNING,
                                        " [ ClientsCommunication ] got general exception while waiting for logging", t);
                            }
                        }// end sync
                    }
                }// end sync
                if (configDebugger != null) {
                    configDebugger.addDebugEntry(oldConf, newConf, diff);
                }
            } else {
                synchronized (configDebuggerLock) {
                    if (debuggerThreadStarted.compareAndSet(true, false)) {
                        configDebugger.stopIT();
                        configDebugger = null;
                    }
                }

            }
        } catch (Throwable t) {
            logger.log(
                    Level.WARNING,
                    " [ ClientsCommunication ] got exception checking for confs diffs. Will keep in sync with last received config",
                    t);
        } finally {
            final Vector vClusters = newConf.getClusters();
            final int clustersCount = vClusters.size();
            long nodesCount = 0;
            long paramCount = 0;
            for (int i = 0; i < clustersCount; i++) {
                final MCluster mc = (MCluster) vClusters.get(i);
                final Vector vNodes = mc.getNodes();
                final int nCount = vNodes.size();
                nodesCount += nCount;
                for (int j = 0; j < nCount; j++) {
                    final MNode mn = (MNode) vNodes.get(j);
                    paramCount += mn.getParameterList().size();
                }

            }
            lastConfMon.setParamsCount(paramCount);
            lastConfMon.setNodesCount(nodesCount);
            lastConfMon.getAndSetConfigMessage(configuration);
        }

        return diff;
    }

    public static final Map<ServiceID, MFarmClientConfigInfo> getFarmsConfByGrps(Set<String> groups) {

        if (groups == null) {
            return Collections.emptyMap();
        }

        final HashMap<ServiceID, MFarmClientConfigInfo> chmr = new HashMap<ServiceID, MFarmClientConfigInfo>();

        for (final Map.Entry<ServiceID, FarmWorker> entry : FarmCommunication.getFarmsHash().entrySet()) {

            final ServiceID sID = entry.getKey();
            final FarmWorker fw = entry.getValue();
            final MFarmClientConfigInfo mcfi = fw.getClientConfigInfo();

            try {
                Set<String> grsForFarm = fw.getGroups();

                if ((grsForFarm != null) && (grsForFarm.size() > 0)) {
                    for (final String group : groups) {
                        if (grsForFarm.contains(group)) {
                            chmr.put(sID, mcfi);
                            break;
                        } // if
                    } // for
                } // if

            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ ClientsCommunication ] [ getFarmsConfByGrps ] got exception ", t);
            } // try - catch

        } // for

        return chmr;

    } // getFarmsConfbyGrps

    private static final String getUptime(long l) {
        l /= 1000;
        // long s = l%60;
        l /= 60;
        long m = l % 60;
        l /= 60;
        long h = l % 24;
        l /= 24;
        long d = l;
        return (d > 0 ? d + " day" + (d != 1 ? "s" : "") + ", " : "") + ((d > 0) || (h > 0) ? h + "h, " : "") + m
                + "min";
    }

    /**
     * gets information about all existing clients
     */
    public static final String getInfoMailRaport(Date lastMailDate, Date thisMailDate) {
        final StringBuilder sb = new StringBuilder(16384);

        double confsTrafic = 0;
        double totalTrafic = 0;
        long uptime = 0;
        double raportTimeSec = 0;

        if ((lastMailDate != null) && (thisMailDate != null)) {
            raportTimeSec = (thisMailDate.getTime() - lastMailDate.getTime()) / 1000.0;
        }

        for (ClientWorker cw : clients.values()) {
            sb.append(cw.getName());
            sb.append("   ");
            sb.append("\n\tuptime: ");
            long up = (TimeUnit.NANOSECONDS.toMillis(Utils.nanoNow() - cw.getStartTime()));
            sb.append(getUptime(up));
            up = up / 1000;
            uptime = up;

            sb.append("\n\t Traffic since last report: ");
            double d = cw.getMailSentMega();
            if (d > 0) {
                totalTrafic = totalTrafic + d;
                sb.append(Util.valToString(d, Util.VALUE_2_STRING_UNIT) + "B ");
                if (raportTimeSec > 0) {
                    sb.append(" [");
                    sb.append(Util.valToString(d / raportTimeSec, Util.VALUE_2_STRING_UNIT) + "B/sec ");
                    sb.append("] ");
                } // if
            } else {
                sb.append("<unknown>");
            } // if - else

            sb.append(" (");
            double confs = cw.getMailConfBytes();
            if (confs > 0) {
                confsTrafic = confsTrafic + confs;
                sb.append(Util.valToString(confs, Util.VALUE_2_STRING_UNIT) + "B ");
            } else {
                sb.append("<unknown>");
            } // if - else
            if (raportTimeSec > 0) {
                sb.append(" [");
                sb.append(Util.valToString(confs / raportTimeSec, Util.VALUE_2_STRING_UNIT) + "B/sec ");
                sb.append("] ");
            }
            sb.append(" for conf)");

            final double rate = cw.getSentBytes() / (double) up;

            sb.append("\n\t Traffic since registered : ");
            sb.append(Util.valToString(cw.getSentBytes(), Util.VALUE_2_STRING_UNIT) + "B ");
            sb.append(" [");
            sb.append(Util.valToString(rate, Util.VALUE_2_STRING_UNIT) + "B/sec ");
            sb.append("]  (");
            sb.append(Util.valToString(cw.getConfSentBytes(), Util.VALUE_2_STRING_UNIT) + "B ");
            sb.append("[");
            final double confRate = cw.getConfSentBytes() / (double) up;
            sb.append(Util.valToString(confRate, Util.VALUE_2_STRING_UNIT) + "B/sec ");
            sb.append("] ");
            sb.append(" for conf)\n");

        } // for

        sb.append("\n----------------------------------------------------------\n");

        if (clients.size() > 0) {
            sb.append("Totals:\n");
            sb.append("-------\n");

            sb.append("\tTotal traffic since last report: ");
            sb.append(Util.valToString(totalTrafic, Util.VALUE_2_STRING_UNIT) + "B");
            if (uptime > 0) {
                sb.append("  [ ");
                sb.append(Util.valToString(totalTrafic / uptime, Util.VALUE_2_STRING_UNIT));
                sb.append("B/sec ]");
            }
            sb.append(" (");
            sb.append(Util.valToString(confsTrafic, Util.VALUE_2_STRING_UNIT) + "B");
            if (uptime > 0) {
                sb.append("  [ ");
                sb.append(Util.valToString(confsTrafic / uptime, Util.VALUE_2_STRING_UNIT));
                sb.append("B/sec ] ");
            }
            sb.append(" for conf)");
            sb.append("\n----------------------------------------------------------\n");
        } // if

        return sb.toString();

    } // getInfoMailRaport

}
