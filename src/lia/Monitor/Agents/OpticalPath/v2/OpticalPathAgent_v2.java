package lia.Monitor.Agents.OpticalPath.v2;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Agents.AbstractAgent;
import lia.Monitor.Agents.OpticalPath.OSPort;
import lia.Monitor.Agents.OpticalPath.OpticalCrossConnectLink;
import lia.Monitor.Agents.OpticalPath.OpticalLink;
import lia.Monitor.Agents.OpticalPath.Admin.OSAdminInterface;
import lia.Monitor.Agents.OpticalPath.Lease.Lease;
import lia.Monitor.Agents.OpticalPath.Lease.LeaseRenewal;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwConfig;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwCrossConn;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwFSM;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwLink;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwPort;
import lia.Monitor.ClientsFarmProxy.AgentsPlatform.AgentMessage;
import lia.Monitor.JiniSerFarmMon.GMLEPublisher;
import lia.Monitor.modules.monOSPortsPower;
import lia.Monitor.monitor.AgentInfo;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Gresult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.Utils;
import lia.util.exporters.RMIRangePortExporter;
import lia.util.ntp.NTPDate;
import lia.util.telnet.OSTelnet;
import lia.util.telnet.OSTelnetFactory;

public class OpticalPathAgent_v2 extends AbstractAgent implements Observer, LeaseRenewal {

    private static final long serialVersionUID = 3257284751210459449L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(OpticalPathAgent_v2.class.getName());

    static final String CMD_TOKEN = ":-";

    static final double POWER_THRESHOLD = -20;

    static long IGNORE_MONITORING_CMAP_DELAY;

    private static boolean shouldCheckPortNames = false;
    AgentInfo agentInfo;

    Vector addresses;

    int rec; // for debugging

    /**
     * Message ID Sequencer
     */
    private static AtomicInteger messIDs;

    /**
     * Optical Contor Sequencer
     */
    static AtomicLong opticalContor;

    /**
     * Local Configuration
     */
    LocalConfigurationManager lcm;

    /**
     * Other Agents Confs 
     */
    ConcurrentHashMap otherConfs;

    public ConcurrentHashMap swNameAddrHash;

    private static boolean shouldStartAdminInterface = false;

    /**
     * OpticalPath-s made by ml_path command 
     */
    //    ConcurrentHashMap workers;

    static boolean ping_pong_mode = false;// only for testing

    ConcurrentHashMap receivedCMDs = new ConcurrentHashMap();

    private static final long SLEEP_WAIT = 4000;//ms

    private static final long dt_eResultSent = 20 * 1000;

    public static long cmdsID;

    Hashtable sentCMDsConfs = new Hashtable();

    private long lasteResult_SENT;

    private OSAdminInterface localAdminInterface;

    private boolean attrPublished;

    private long lastBConfSent;

    private static long BCONF_DELAY;

    long ignoreUntil;
    static ExecutorService executor;

    ConcurrentHashMap currentSessions;
    private final ReadWriteLock monitoringResultsLock;
    private final Lock monitoringResultsReadLock;
    private final Lock monitoringResultsWriteLock;
    private static final String OS_CONFIG_CLUSTER;

    private static boolean debugOSStates = false;

    private static XDRMessagesHandler xdrMsgHandler;

    private long lastAgentListMsg;
    private static long AGENT_LIST_MSG_DELAY = 15 * 1000;

    OSPathFinder osPathFinder;

    private class CConnPorts {
        String sPort;
        String dPort;

        private CConnPorts(String sPort, String dPort) {
            this.sPort = sPort;
            this.dPort = dPort;
        }
    }

    static {

        String hiddenOSWClusterName = AppConfig.getProperty("lia.Monitor.Farm.OSwConfigHiddenCluster",
                "OSwConfigHCluster");

        if (hiddenOSWClusterName == null) {
            hiddenOSWClusterName = "OSwConfigHCluster";
        }
        OS_CONFIG_CLUSTER = hiddenOSWClusterName;

        logger.log(Level.INFO, " [ OSConfigCluster ] " + OS_CONFIG_CLUSTER);

        executor = new ThreadPoolExecutor(5, 256, 5, TimeUnit.MINUTES, new SynchronousQueue(), new ThreadFactory() {
            AtomicLong l = new AtomicLong(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "( ML ) MLCopyAgentWorker " + l.getAndIncrement());
            }
        });
        ((ThreadPoolExecutor) executor).allowCoreThreadTimeOut(false);
        ((ThreadPoolExecutor) executor).prestartAllCoreThreads();

        cmdsID = 0;
        try {
            shouldCheckPortNames = Boolean.valueOf(
                    AppConfig.getProperty("lia.Monitor.Agents.OpticalPath.MLCopyAgent.shouldCheckPortNames", "false"))
                    .booleanValue();
        } catch (Throwable t) {
            shouldCheckPortNames = false;
        }

        try {
            ping_pong_mode = Boolean.valueOf(
                    AppConfig.getProperty("lia.Monitor.Agents.OpticalPath.MLCopyAgent.ping_pong_mode", "false"))
                    .booleanValue();
        } catch (Throwable t) {
            ping_pong_mode = false;
        }

        try {
            BCONF_DELAY = Long.valueOf(
                    AppConfig.getProperty("lia.Monitor.Agents.OpticalPath.MLCopyAgent.BCONF_DELAY", "10")).longValue() * 1000;
        } catch (Throwable t) {
            BCONF_DELAY = 10 * 1000;
        }

        try {
            shouldStartAdminInterface = Boolean.valueOf(
                    AppConfig.getProperty("lia.Monitor.Agents.OpticalPath.MLCopyAgent.startAdminInterface", "false"))
                    .booleanValue();
        } catch (Throwable t) {
            shouldStartAdminInterface = false;
        }

        try {
            IGNORE_MONITORING_CMAP_DELAY = Long.valueOf(
                    AppConfig.getProperty("lia.Monitor.Agents.OpticalPath.MLCopyAgent.IGNORE_MONITORING_CMAP_DELAY",
                            "10")).longValue() * 1000;
        } catch (Throwable t) {
            IGNORE_MONITORING_CMAP_DELAY = 10 * 1000;
        }
        debugOSStates = AppConfig.getb("lia.Monitor.Agents.OpticalPath.MLCopyAgent.debugOSStates", false);

        opticalContor = new AtomicLong(0);
        messIDs = new AtomicInteger(0);
    }

    static int getAndIncrementMSGID() {
        return messIDs.getAndIncrement();
    }

    @Override
    public void sendAgentMessage(AgentMessage am) {
        super.sendAgentMessage(am);
    }

    private class MLCopyAgentTask implements Runnable {
        Object msg;
        long startTime;

        MLCopyAgentTask(Object o, long startTime) {
            this.msg = o;
            this.startTime = startTime;
        }

        @Override
        public void run() {
            String cName = null;
            try {
                Thread cThread = Thread.currentThread();
                cThread.getName();
                cThread.setName(cName + " MLCopyAgentTask StartTime: " + new Date(startTime));
            } catch (Throwable t) {
            }

            try {

                AgentMessage am = (AgentMessage) msg;

                String agentS = am.agentAddrS;

                //internal stuff - this should have been included in the agents framework
                if (agentS.equals("proxy")) {

                    String[] proxyAdresses = ((String) (((AgentMessage) msg).message)).split(":");
                    if ((proxyAdresses != null) && (proxyAdresses.length > 0)) {
                        for (String proxyAdresse : proxyAdresses) {
                            String dest = proxyAdresse;
                            if (!addresses.contains(dest)) {
                                addresses.add(dest);
                                sendConf(dest);
                            }
                        } // while

                        //check for dead agents
                        for (Enumeration en = addresses.elements(); en.hasMoreElements();) {
                            String dAddr = (String) en.nextElement();
                            boolean found = false;
                            for (String proxyAdresse : proxyAdresses) {
                                if (proxyAdresse.equals(dAddr)) {
                                    found = true;
                                    break;
                                }
                            }//for
                            if (!found) {
                                logger.log(Level.WARNING, " Removing Agent Address" + dAddr);
                                addresses.remove(dAddr);
                            }
                        }
                    }
                    return;
                }

                if (am.message == null) {
                    logger.log(Level.WARNING, "Got a null message ... returning");
                    return;
                }

                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "Got message:\n" + am + "\nMSG:"
                            + ((am.message == null) ? "null" : am.message.toString()));
                }

                if (am.message instanceof OSwConfig) {//remote conf
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "\n\n [ OpticalPathAgent_v2 ] Received a remote OSwConfig: \n\n"
                                + am.message);
                    }
                    if (am.agentAddrS.equals(agentInfo.agentAddr)) {
                        return;
                    }
                    OSwConfig remoteOSwConfig = (OSwConfig) am.message;
                    otherConfs.put(am.agentAddrS, remoteOSwConfig);
                    swNameAddrHash.put(remoteOSwConfig.name, am.agentAddrS);
                } else if (am.message instanceof String) {//remote CMD
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "\n\n [ OPA_v2 ] Received sMSG: " + am.toString() + "\n\n");
                    }
                    final String smsg = (String) am.message;

                    //TODO - maybe this could speed up things ... though does not seem to work as expected ... check IT
                    if (smsg.equals("reg")) {
                        return;
                    }

                    long sTime = System.currentTimeMillis();
                    RemoteAgentMsgWrapper raMsg = null;
                    try {
                        raMsg = RemoteAgentMsgWrapper.fromString(smsg);
                    } catch (Throwable t) {
                        logger.log(Level.WARNING,
                                " [ ProtocolException ] [ HANDLED ] [ OPA_v2 ] Cannot deserialize REQ from "
                                        + getSwName(am.agentAddrS) + " [ sMSG: " + smsg + " ]", t);
                        return;
                    }

                    MLOpticalPathSession mlPathSession = (MLOpticalPathSession) currentSessions.get(raMsg.session);

                    /////////////////////////////
                    //(N)ACK messages
                    /////////////////////////////

                    if (raMsg.remoteOpStat != RemoteAgentMsgWrapper.REQ) {
                        if (mlPathSession != null) {
                            mlPathSession.opr.sentCMDs.put(raMsg.remoteCMD_ID,
                                    RemoteAgentMsgWrapper.getDecodedRemoteOPStatus(raMsg.remoteOpStat));
                            mlPathSession.opr.getCountDownLatch().countDown();
                        } else {
                            logger.log(Level.WARNING, "\n\n [ProtocolException] Got an (N)ACK from "
                                    + getSwName(am.agentAddrS) + " for session " + raMsg.session + " RAWStrinMSG: "
                                    + smsg);
                        }
                        return;
                    }// END (N)ACK

                    //////////////
                    //REQ message
                    //////////////

                    if (raMsg.remoteCMD == RemoteAgentMsgWrapper.ADMINDEL) {
                        String status = deleteMLPathConn(raMsg.session);
                        logger.log(Level.INFO, " Got remote ADMIN DEL ... [ " + (System.currentTimeMillis() - sTime)
                                + " ]\n" + status);
                        return;
                    }

                    if (mlPathSession == null) {
                        mlPathSession = new MLOpticalPathSession(raMsg.session, OpticalPathAgent_v2.this, null,
                                startTime);
                        currentSessions.put(raMsg.session, mlPathSession);
                    }

                    //it will be faster if put also ops[] ... does not make sens to split()-it again 
                    // we need to be fast&furious :)  
                    mlPathSession.notifyAgentMessage(am, raMsg, startTime);

                } else if (am.message instanceof String[]) {
                    //more remote commands in the same message. Used for especially rerouting

                    //The commands will be processed in the same order as received. 
                    String[] sRemoteMessages = (String[]) am.message;
                    MLOpticalPathSession mlPathSession = null;

                    if (sRemoteMessages.length > 0) {

                        RemoteAgentMsgWrapper[] ramws = new RemoteAgentMsgWrapper[sRemoteMessages.length];
                        for (int iterSR = 0; iterSR < sRemoteMessages.length; iterSR++) {
                            try {
                                ramws[iterSR] = RemoteAgentMsgWrapper.fromString(sRemoteMessages[iterSR]);

                                //TODO - Only for extra checks in ProtocolExceptions 
                                if (iterSR == 0) {//GET The default session
                                    mlPathSession = (MLOpticalPathSession) currentSessions.get(ramws[iterSR].session);
                                    if (mlPathSession == null) {
                                        logger.log(Level.WARNING,
                                                " [ ProtocolException ] or Session already finished [ "
                                                        + ramws[iterSR].session
                                                        + " ] . First item in the command string");
                                        //TODO - send back NACK
                                        return;
                                    }
                                } else {
                                    //TODO - Extra check for ProtcolExceptions
                                    if (mlPathSession != (MLOpticalPathSession) currentSessions
                                            .get(ramws[iterSR].session)) {
                                        logger.log(Level.WARNING, " [ ProtocolException ] Session [ "
                                                + ramws[iterSR].session + " ] != DEFAULT Session "
                                                + mlPathSession.opr.id);
                                        //TODO - send back NACK
                                        return;
                                    }
                                }
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, " Cannot deserialize REQ from " + getSwName(am.agentAddrS)
                                        + " on POS[" + iterSR + "] --> [ MSG: " + sRemoteMessages[iterSR] + " ]", t);
                                return;
                            }
                        }//for

                        mlPathSession.notifyAgentMessage(am, ramws, startTime);
                    } else {
                        logger.log(Level.WARNING, " [ ProtocolException ] Got a zero length String[]");
                    }

                } else {//No such object Exception !
                    logger.log(Level.WARNING, "Got an unknown message [ " + am.message + " ] from "
                            + getSwName(am.agentAddrS));
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Got exception in processMsg", t);
            } finally {
                if (cName != null) {//should not be null ... but
                    Thread.currentThread().setName(cName);
                }
            }
        }
    }

    public OpticalPathAgent_v2(String agentName, String agentGroup, String farmID) {
        super(agentName, agentGroup, farmID);

        monitoringResultsLock = new ReentrantReadWriteLock();
        monitoringResultsReadLock = monitoringResultsLock.readLock();
        monitoringResultsWriteLock = monitoringResultsLock.writeLock();

        //        workers = new ConcurrentHashMap();
        currentSessions = new ConcurrentHashMap();

        attrPublished = false;
        clients = new Vector();
        lasteResult_SENT = 0;

        lastBConfSent = 0;

        hasToRun = true;
        agentInfo = new AgentInfo(agentName, agentGroup, farmID);
        addresses = new Vector();

        otherConfs = new ConcurrentHashMap();

        swNameAddrHash = new ConcurrentHashMap();

        if (shouldStartAdminInterface) {
            try {
                localAdminInterface = new lia.Monitor.Agents.OpticalPath.v2.Admin.OSAdminImpl(this);
                logger.log(
                        Level.INFO,
                        "Optical Switch Admin Interface started on [ "
                                + RMIRangePortExporter.getPort(localAdminInterface) + " ]");
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Cannot start the admin interface", t);
            }
        }

        try {
            String cf = AppConfig.getProperty("lia.Monitor.Agents.OpticalPath.MLCopyAgent.ConfFile", null);
            if (cf != null) {
                lcm = new LocalConfigurationManager(new File(cf.trim()), this);
            } else {
                lcm = null;
                hasToRun = false;
            }
        } catch (Throwable t) {
            logger.log(
                    Level.WARNING,
                    " [ OpticalPathAgent_v2 ] Cannot read or instantiate local configuration. The OpticalPathAgent_v2 will not start ",
                    t);
            hasToRun = false;
        }

        try {
            xdrMsgHandler = new XDRMessagesHandler(this);
        } catch (Throwable t) {
            xdrMsgHandler = null;
            logger.log(
                    Level.WARNING,
                    " [ OpticalPathAgent_v2 ] Cannot instantiate XDR Message Handler. The agent will not accept commands from externat Optical Switch Daemons ",
                    t);
        }

        this.osPathFinder = new OSPathFinder(this);
    } // TestAgent

    public String getPeers() {

        //        AgentMessage amB = createCtrlMsg(getAndIncrementMSGID(), 1, 1, 5, null, null, "MLCopyGroup");
        AgentMessage amB = createCtrlMsg(getAndIncrementMSGID(), 1, 1, 5, null, null, agentInfo.agentGroup);
        agentComm.sendCtrlMsg(amB, "list");

        if ((addresses == null) || (addresses.size() == 0)) {
            return "N/A";
        }

        String retV = "";
        for (int i = 0; i < addresses.size(); i++) {
            retV += (String) addresses.elementAt(i) + ":";
        }

        return retV;
    }

    String modifyConfigurations(String data) {
        // Decode the configurations in the message
        // Send cfg messages to the other agents ... if more then one
        // configuration specified
        //        sentCMDsConfs.clear();
        //        String sReturnValue = "";
        //        HashMap splittedConfs = null;
        //        ArrayList idsToWait = new ArrayList();
        //        try {
        //            splittedConfs = Util.splitConfigurations(new StringReader(data));
        //            if (splittedConfs == null) {
        //                sReturnValue = "Cannot decode any conf(s)";
        //            }
        //        } catch (Throwable t) {
        //            logger.log(Level.WARNING, "Got exc while decoding conf", t);
        //            splittedConfs = null;
        //            StringWriter sw = new StringWriter();
        //            t.printStackTrace(new PrintWriter(new BufferedWriter(sw), true));
        //            sReturnValue = sw.getBuffer().toString();
        //        }
        //        if (splittedConfs == null) { return sReturnValue; }
        //
        //        StringBuilder sb = new StringBuilder();
        //        if (logger.isLoggable(Level.FINER)) {
        //            sb.append("Got ").append(splittedConfs.size()).append(" confs");
        //            for (Iterator it = splittedConfs.keySet().iterator(); it.hasNext();) {
        //                String swName = (String) it.next();
        //                sb.append("\n*********\nSTART Conf :- ").append(swName).append("\n***********\n");
        //                sb.append(splittedConfs.get(swName));
        //                sb.append("\n*********\nEND Conf :- ").append(swName).append("\n***********\n");
        //            }
        //            sb.append("END with all ").append(splittedConfs.size()).append(" confs");
        //            logger.log(Level.FINER, sb.toString());
        //        }
        //
        //        StringBuilder status = new StringBuilder(1024);
        //        for (Iterator it = splittedConfs.keySet().iterator(); it.hasNext();) {
        //            cmdsID++;
        //            Long idl = Long.valueOf(cmdsID);
        //            idsToWait.add(idl);
        //            String swName = (String) it.next();
        //            status.append("\n Status for: ").append(swName);
        //            if (swName.equals(OSwFSM.getInstance().oswConfig.name)) {// I should modify my own
        //                // conf...
        //                status.append(" ... my own address ... OK\n");
        //                try {
        //                    // cfg.setNewConfiguration(Util.getOpticalSwitchConfig(new StringReader((String) splittedConfs.get(swName))), -1);
        //                } catch (ConfigurationParsingException cpe) {
        //                    logger.log(Level.INFO, "Got Parsing Exception setting new config", cpe);
        //                } catch (Throwable t) {
        //                    logger.log(Level.WARNING, "Got generic Exception setting new config", t);
        //                }
        //                sentCMDsConfs.put(idl, "ACK");
        //            } else {
        //                String agentAddr = (String) swNameAddrHash.get(swName);
        //                if (agentAddr == null) {
        //                    status.append("\n Error...No dstination agent address for this switch \n");
        //                    continue;
        //                }
        //
        //                AgentMessage amB = createMsg(getAndIncrementMSGID(), 1, 1, 8, agentAddr, agentInfo.agentGroup, "NCONF:-REQ:-" + splittedConfs.get(swName) + ":-" + idl);
        //                sentCMDsConfs.put(idl, "SENT");
        //                sendAgentMessage(amB);
        //            }
        //        }
        //
        //        //TODO !!!
        ////        waitForSentCMDtoFinish(idsToWait.size(), "xxxx");
        //
        //        if (getSentCMDsStatusNo("NACK", idsToWait, sentCMDsConfs) == 0) {
        //            status.append(" ALL OK !");
        //        } else {
        //            status.append("There are " + getSentCMDsStatusNo("NACK", idsToWait, sentCMDsConfs) + " NACKed!");
        //        }
        //
        //        sReturnValue = status.toString();
        //        return sReturnValue;
        return null;
    }

    static boolean checkPort(final OSwPort oswPort, final String allowedMLID, final OSwConfig oswConfig) {
        if ((oswPort.powerState != OSwPort.NOLIGHT) && (oswPort.powerState != OSwPort.UNKLIGHT)
                && (oswPort.powerState != OSwPort.LIGHTOK)) {
            return false;
        }
        //check if this port is in any cross connect ... if yes, check if the cross connect has allowedMLIDCrossConn ID
        boolean cconnCheck = true;
        if (oswConfig.crossConnects != null) {
            int cconnLen = oswConfig.crossConnects.length;
            for (int j = 0; j < cconnLen; j++) {
                OSwCrossConn oswCConn = oswConfig.crossConnects[j];
                if (oswCConn.sPort.equals(oswPort) || oswCConn.dPort.equals(oswPort)) {
                    if ((oswCConn.mlID == null) || (allowedMLID == null) || !oswCConn.equals(allowedMLID)) {
                        cconnCheck = false;
                        break;
                    }
                }
            }//end for
        }

        return cconnCheck;
    }

    static OSwPort getPearPort(final OSwPort oswPort, final OSwConfig oswConfig) {
        for (OSwPort port : oswConfig.osPorts) {
            if ((oswPort.type != port.type) && oswPort.name.equals(port.name)) {
                return port;
            }
        }
        return null;
    }

    public boolean makeConnection(OpticalPathRequest opr, boolean ignoreIdle) throws Exception {

        if (opr.destination.equals(opr.source)) {
            opr.status.append("Cannot EST cnx \"between\" the same host [ " + opr.source + " - " + opr.destination
                    + " ]");
            return false;
        }

        EndPointEntry[] srcEndPoints = getOpticalSwitchForEndPoint(opr.source, true, opr.isFDX);
        if ((srcEndPoints == null) || (srcEndPoints.length == 0)) {
            opr.status.append("Cannot find source [ ").append(opr.source).append(" ] in local conf");
            return false;
        }

        EndPointEntry srcEndPoint = null;
        for (int i = 0; i < srcEndPoints.length; i++) {
            if (checkPort(srcEndPoints[i].inputPort, opr.id, srcEndPoints[i].oswConfig)) {

                if (opr.isFDX && !checkPort(srcEndPoints[i].outputPort, opr.id, srcEndPoints[i].oswConfig)) {
                    continue;
                }

                srcEndPoint = srcEndPoints[i];
                break;
            }
        }

        if (srcEndPoint == null) {
            opr.status.append("The source port for endpoint ").append(opr.source)
                    .append(" cannot be used in a new cross-connect");
            return false;
        }

        EndPointEntry[] dstEndPoints = getOpticalSwitchForEndPoint(opr.destination, false, opr.isFDX);
        if ((dstEndPoints == null) || (dstEndPoints.length == 0)) {
            opr.status.append("Cannot find destination [ ").append(opr.destination).append(" ] in local conf");
            return false;
        }

        EndPointEntry dstEndPoint = null;
        for (int i = 0; i < dstEndPoints.length; i++) {
            if (checkPort(dstEndPoints[i].outputPort, opr.id, dstEndPoints[i].oswConfig)) {

                if (opr.isFDX && !checkPort(dstEndPoints[i].inputPort, opr.id, dstEndPoints[i].oswConfig)) {
                    continue;
                }

                dstEndPoint = dstEndPoints[i];
                break;
            }
        }

        if (dstEndPoint == null) {
            opr.status.append("The destination port for DST [").append(opr.destination)
                    .append("] cannot be used in a new cross-connect");
            return false;
        }

        OSwConfig srcOSI = srcEndPoint.oswConfig;
        OSwConfig dstOSI = dstEndPoint.oswConfig;

        DNode dstNode = null;
        dstNode = osPathFinder.getShortestPath(srcOSI, dstOSI, opr.isFDX, opr.id);

        if (dstNode == null) {
            return false;
        }

        String dPort = srcEndPoint.outputPort.name;
        while (dstNode.predecessor != null) {
            opr.links.put(dstNode.oswConfig.name, new CConnPorts(dstNode.localPort, dPort));
            dPort = dstNode.predecessorPort;
            dstNode = dstNode.predecessor;
        }

        opr.links.put(srcEndPoint.oswConfig.name, new CConnPorts(srcEndPoint.inputPort.name, dPort));

        StringBuilder sb = new StringBuilder();
        String[] sOpticalPath = null;
        HashMap theLinks = new HashMap();

        sb.append("Optical Path [ ").append(opr.source);
        //        sOpticalPath = new String[path.size() + 2];
        //
        //        int opIndex = 0;
        //        sOpticalPath[opIndex++] = opr.source;
        //        if (sOpticalPath.length == 3) {
        //            sOpticalPath[opIndex++] = sourceOS;
        //            sb.append(" - ").append(sourceOS);
        //        } else {
        //            for (int i = 0; i <= path.size(); i++) {
        //                String sname = (String) path.get(i);
        //                sb.append(" - ").append(sname);
        //                sOpticalPath[opIndex++] = sname;
        //            }
        //       }

        //        sOpticalPath[opIndex] = opr.destination;

        sb.append(" - ").append(opr.destination).append(" ] ");

        getOpticalLinks(theLinks, sOpticalPath, opr, ignoreIdle);

        opr.readableOpticalPath = sb.toString();

        if (opr.links != null) {
            logger.log(Level.INFO, "\n\n--|--\n\n" + opr.links.toString() + "\n\n--|--\n\n");
            return true;
        }

        logger.log(Level.INFO, "\n\n--|--\n\n Cannot EST CNX ... opr.links == null\n\n--|--\n\n");
        return false;
    }

    /**
     * make a connection from the GUI Admin interface
     */
    public String makeMLPathConn(String src, String dst, boolean isFDX) throws Exception {
        String olID = getAddress() + "//::" + opticalContor.incrementAndGet();
        OpticalPathRequest opr = new OpticalPathRequest(olID, isFDX, false);
        logger.log(Level.INFO, "\n\n Got GUI makeMLPathConn " + olID);
        opr.source = src;
        opr.destination = dst;

        String status = "OK";
        currentSessions.put(olID, new MLOpticalPathSession(opr, this, System.currentTimeMillis()));
        if (!makeConnection(opr)) {
            status = "Cannot establish a conn [ " + opr.status.toString() + " ]";
            logger.log(Level.INFO, "\n\n GUI makeMLPathConn " + olID + " cannot be established");
            MLOpticalPathSession mlps = (MLOpticalPathSession) currentSessions.remove(opr.id);
            mlps.stopIt();
        } else {
            ((MLOpticalPathSession) currentSessions.get(olID)).registerMasterLeases();
        }
        return status;
    }

    public String deleteMLPathConn(String olID) throws Exception {
        if (olID == null) {
            return " No such OLID == null";
        }
        String dAgent = olID.split("//::")[0];

        if (dAgent.equals(getAddress())) {//it's me
            boolean status = false;

            MLOpticalPathSession mlPathSession = (MLOpticalPathSession) currentSessions.get(olID);
            if (mlPathSession != null) {
                logger.log(Level.INFO, "deleteMLPathConn from local workers");
                status = deleteOLConns(mlPathSession.opr);
                mlPathSession.stopIt();
                currentSessions.remove(olID);
                if (status) {
                    return "OK";
                }
                return "Not OK";
            }

            return "Cannot find such olID";
        }

        //I should notify the agent who made the path
        logger.log(Level.INFO, "deleteMLPathConn from a remote agent" + getSwName(dAgent));
        AgentMessage amB = createMsg(getAndIncrementMSGID(), 1, 1, 5, dAgent, agentInfo.agentGroup, "ADMINDEL:-REQ:-"
                + olID);
        sendAgentMessage(amB);

        return "OK";
    }

    public boolean makeConnection(OpticalPathRequest opr) throws Exception {
        long STIME = System.currentTimeMillis();
        boolean status = makeConnection(opr, false);
        if (!status || (opr.links == null) || (opr.links.size() == 0)) {
            opr.status.append("Cannot establish end-to-end connection ... " + opr.readableOpticalPath + "\n");
            return false;
        }
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "\n\n -----> ALGO [ " + (System.currentTimeMillis() - STIME) + " ] <--------\n\n");
        }
        status = makeOLConns(opr, null, null);
        return status;
    }

    //TODO - MUST be redone !! use execCmdAndGet
    private void checkPortName() {
        try {
            if (OSwFSM.getInstance().oswConfig.type == OSwConfig.GLIMMERGLASS) {
                OSTelnet ost = OSTelnetFactory.getControlInstance(OSwFSM.getInstance().oswConfig.type);
                BufferedReader br = new BufferedReader(new StringReader(ost.doCmd(
                        monOSPortsPower.GLIMMER_TL1_CMD_PORT_POWER, OSTelnet.PPOWER_CTAG).toString()));
                for (String line = br.readLine(); line != null; line = br.readLine()) {
                    try {
                        String trimmedLine = line.trim();
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, " Parsing (trimmed)line " + trimmedLine);
                        }
                        if (trimmedLine.indexOf("PORTNAME=") != -1) {
                            String[] tk = trimmedLine.substring(1, trimmedLine.length() - 1).split("PORTNAME="); // also remove "" from the beginning and the end of line
                            if (tk.length > 0) {
                                String pLabel = tk[1].split(",")[0];
                                if (pLabel != null) {
                                    pLabel = pLabel.trim();
                                }
                                if (pLabel.length() > 0) {
                                    String[] tk12 = trimmedLine.substring(1, trimmedLine.length() - 1).split(
                                            "PORTPOWER="); // also remove "" from the beginning and the end of line
                                    String sPname = tk12[0].split("PORTID=")[1].split(",")[0];
                                    if (sPname.startsWith("100") || sPname.startsWith("200")) {
                                        String mlPName = sPname.substring(3);
                                        OSPort searchPort = null;
                                        if (sPname.startsWith("100")) {
                                            searchPort = new OSPort(mlPName, OSPort.INPUT_PORT);
                                        } else {
                                            searchPort = new OSPort(mlPName, OSPort.OUTPUT_PORT);
                                        }

                                        //                                      for(Iterator it = cfg.oswConfig.map.keySet().iterator(); it.hasNext();) {
                                        //                                      OSPort ospKey = (OSPort)it.next();
                                        //                                      if(ospKey != null && ospKey.equals(searchPort)) {
                                        //                                      ospKey.label = pLabel;
                                        //                                      if(logger.isLoggable(Level.FINEST)) {
                                        //                                      logger.log(Level.FINEST, " Setting PortName = " + pLabel + " for " + sPname);
                                        //                                      }
                                        //                                      }
                                        //                                      }
                                    }
                                }
                            }
                        }
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Got exception parsing line [" + line + "]", t);
                    }
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception reading from command's buffered stream", t);
        }
    }

    boolean makeOLConns(OpticalPathRequest opr, HashMap removeLinks, HashMap alreadyCreatedLinks) throws Exception {
        String status = "";
        opr.sentCMDs.clear();
        ArrayList idsToWait = new ArrayList();

        String localPorts = null;
        String localRemovePorts = null;
        Long localIDL = null;

        if (opr.links == null) {
            return false;
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " Trying to make the following links: " + opr.links.toString());
        }

        HashMap totalRemoveLinks = new HashMap();
        int expectedSize = opr.links.size() - ((alreadyCreatedLinks == null) ? 0 : alreadyCreatedLinks.size());
        if (removeLinks != null) {
            for (Iterator itOld = removeLinks.entrySet().iterator(); itOld.hasNext();) {
                Map.Entry entry = (Map.Entry) itOld.next();

                String dAgent = (String) entry.getKey();
                String oldPorts = (String) entry.getValue();

                if (opr.links.containsKey(dAgent)) {
                    continue;
                }

                totalRemoveLinks.put(dAgent, oldPorts);
            }
        }

        CountDownLatch cdl = new CountDownLatch(expectedSize + totalRemoveLinks.size());
        opr.setCountDownLatch(cdl);

        for (Enumeration en = opr.links.keys(); en.hasMoreElements();) {
            String dAgent = (String) en.nextElement();
            if ((alreadyCreatedLinks != null) && alreadyCreatedLinks.containsKey(dAgent)) {
                continue;
            }
            String ports = (String) opr.links.get(dAgent);

            String oldPorts = null;
            if (removeLinks != null) {
                oldPorts = (String) removeLinks.get(dAgent);
            }

            cmdsID++;
            Long idl = Long.valueOf(cmdsID);
            idsToWait.add(idl);
            if (dAgent.equals(agentInfo.agentAddr)) {
                localPorts = ports;
                localRemovePorts = oldPorts;
                localIDL = idl;
            } else {
                AgentMessage amB = null;
                if (oldPorts == null) {
                    amB = createMsg(getAndIncrementMSGID(), 1, 1, 8, dAgent, agentInfo.agentGroup, "MCONN:-REQ:-"
                            + ports + ":-" + idl + ":-" + opr.id + ":-" + ((opr.isFDX) ? "1" : "0"));
                } else {
                    amB = createMsg(getAndIncrementMSGID(), 1, 1, 8, dAgent, agentInfo.agentGroup, new String[] {
                            "PDCONN:-REQ:-" + oldPorts + ":-" + idl + ":-" + opr.id + ":-" + ((opr.isFDX) ? "1" : "0"),
                            "MCONN:-REQ:-" + ports + ":-" + idl + ":-" + opr.id + ":-" + ((opr.isFDX) ? "1" : "0") });
                }
                opr.sentCMDs.put(idl, "SENT");
                sendAgentMessage(amB);
            }
        }

        //send Remote DCONN-s
        for (Iterator itRemDel = totalRemoveLinks.entrySet().iterator(); itRemDel.hasNext();) {
            Map.Entry entry = (Map.Entry) itRemDel.next();
            String dAgent = (String) entry.getKey();
            String ports = (String) entry.getValue();
            if (dAgent.equals(agentInfo.agentAddr)) {
                continue;
            }

            cmdsID++;
            Long idl = Long.valueOf(cmdsID);
            idsToWait.add(idl);

            AgentMessage amB = createMsg(getAndIncrementMSGID(), 1, 1, 8, dAgent, agentInfo.agentGroup, "DCONN:-REQ:-"
                    + ports + ":-" + idl + ":-" + opr.id + ":-" + ((opr.isFDX) ? "1" : "0"));
            opr.sentCMDs.put(idl, "SENT");
            sendAgentMessage(amB);
        }

        //        if (localPorts != null) {
        //            if (localIDL != null) {
        //                setDelayMonitoringResult();
        //                
        //                expectedSize--;
        //                if(logger.isLoggable(Level.FINER)) {
        //                    logger.log(Level.FINER, "\n[ REMOVE ME!!] expectedSize = " + expectedSize);
        //                }
        //                cdl.countDown();
        //
        //                String sdports[] = localPorts.split(" - ");
        //                
        //                OSPort sOSPort = new OSPort(sdports[0], OSPort.INPUT_PORT);
        //                OSPort dOSPort = new OSPort(sdports[1], OSPort.OUTPUT_PORT);
        //
        //                boolean bstatus = false;
        //
        //
        //                Long transactionID = null;
        //                if(opr.isFDX) {
        //                    transactionID =  cfg.osi.beginTransaction(
        //                            new OSPort[]{
        //                                    sOSPort, 
        //                                    dOSPort, 
        //                                    sOSPort.getPear(), 
        //                                    dOSPort.getPear()
        //                                    },
        //                            new Integer[][] {
        //                                    new Integer[]{(localRemovePorts == null || localRemovePorts.indexOf(sOSPort.name) < 0)?OpticalLink.CONNECTED_FREE:OpticalLink.CONNECTED_ML_CONN},
        //                                    new Integer[]{(localRemovePorts == null || localRemovePorts.indexOf(dOSPort.name) < 0)?OpticalLink.CONNECTED_FREE:OpticalLink.CONNECTED_ML_CONN},
        //                                    new Integer[]{(localRemovePorts == null || localRemovePorts.indexOf(sOSPort.getPear().name) < 0)?OpticalLink.CONNECTED_FREE:OpticalLink.CONNECTED_ML_CONN},
        //                                    new Integer[]{(localRemovePorts == null || localRemovePorts.indexOf(dOSPort.getPear().name) < 0)?OpticalLink.CONNECTED_FREE:OpticalLink.CONNECTED_ML_CONN}
        //                            },
        //                            new Integer[]{
        //                                    OpticalLink.CONNECTED_ML_CONN, 
        //                                    OpticalLink.CONNECTED_ML_CONN, 
        //                                    OpticalLink.CONNECTED_ML_CONN,
        //                                    OpticalLink.CONNECTED_ML_CONN,
        //                            }
        //                            );
        //                } else {
        //                    transactionID = cfg.osi.beginTransaction(
        //                            new OSPort[]{
        //                                    sOSPort, 
        //                                    dOSPort, 
        //                                    },
        //                            new Integer[][] {
        //                                    new Integer[]{(localRemovePorts == null || localRemovePorts.indexOf(sOSPort.name) < 0)?OpticalLink.CONNECTED_FREE:OpticalLink.CONNECTED_ML_CONN},
        //                                    new Integer[]{(localRemovePorts == null || localRemovePorts.indexOf(dOSPort.name) < 0)?OpticalLink.CONNECTED_FREE:OpticalLink.CONNECTED_ML_CONN},
        //                            },
        //                            new Integer[]{
        //                                    OpticalLink.CONNECTED_ML_CONN, 
        //                                    OpticalLink.CONNECTED_ML_CONN, 
        //                            }
        //                            );
        //                }//else
        //                
        //                bstatus = (transactionID != null);
        //
        //                try {
        //                    if (bstatus) {
        //                        OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
        //
        //                        if (ost != null) {
        //                            if(opr.isFDX) {
        //                                if (localRemovePorts != null) {
        //                                    try {
        //                                        ost.deleteFDXConn(localRemovePorts);
        //                                    } catch(Throwable t) {
        //                                        bstatus = false;
        //                                        logger.log(Level.WARNING, " [ OpticalPathAgent_v2 ] Got exception deleting conn: [ " + localRemovePorts + "]", t );
        //                                    }
        //                                    
        //                                    if(bstatus) {
        //                                        String sDelPorts[] = localRemovePorts.split(" - ");
        //
        //                                        OSPort sDelOSPort = new OSPort(sDelPorts[0], OSPort.INPUT_PORT);
        //                                        OSPort dDelOSPort = new OSPort(sDelPorts[1], OSPort.OUTPUT_PORT);
        //
        //                                        changePortState(sDelOSPort, OpticalLink.CONNECTED_FREE);
        //                                        changePortState(dDelOSPort, OpticalLink.CONNECTED_FREE);
        //                                        ((OpticalLink)cfg.osi.map.get(sDelOSPort)).opticalLinkID = null;
        //                                        ((OpticalLink)cfg.osi.map.get(dDelOSPort)).opticalLinkID = null;
        //                                        cfg.osi.crossConnects.remove(sDelOSPort);
        //
        //                                        changePortState(sDelOSPort.getPear(), OpticalLink.CONNECTED_FREE);
        //                                        changePortState(dDelOSPort.getPear(), OpticalLink.CONNECTED_FREE);
        //                                        ((OpticalLink)cfg.osi.map.get(sDelOSPort.getPear())).opticalLinkID = null;
        //                                        ((OpticalLink)cfg.osi.map.get(dDelOSPort.getPear())).opticalLinkID = null;
        //                                        cfg.osi.crossConnects.remove(dDelOSPort.getPear());
        //
        //                                        try {
        //                                            ost.makeFDXConn(localPorts);
        //                                        } catch(Throwable t) {
        //                                            bstatus = false;
        //                                            logger.log(Level.WARNING, " [ OpticalPathAgent_v2 ] Got exception making conn: [ " + localPorts + "]", t );
        //                                        }
        //                                        
        //                                        if(bstatus) {
        //                                            changePortState(sOSPort, OpticalLink.CONNECTED_ML_CONN);
        //                                            changePortState(dOSPort, OpticalLink.CONNECTED_ML_CONN);
        //                                            changePortState(sOSPort.getPear(), OpticalLink.CONNECTED_ML_CONN);
        //                                            changePortState(dOSPort.getPear(), OpticalLink.CONNECTED_ML_CONN);
        //                                        }
        //                                    }
        //                                } else {
        //                                    try {
        //                                        ost.makeFDXConn(localPorts);
        //                                    } catch(Throwable t) {
        //                                        bstatus = false;
        //                                        logger.log(Level.WARNING, " [ OpticalPathAgent_v2 ] Got exception making conn: [ " + localPorts + "]", t );
        //                                    }
        //
        //                                    if(bstatus) {
        //                                        changePortState(sOSPort, OpticalLink.CONNECTED_ML_CONN);
        //                                        changePortState(dOSPort, OpticalLink.CONNECTED_ML_CONN);
        //                                        changePortState(sOSPort.getPear(), OpticalLink.CONNECTED_ML_CONN);
        //                                        changePortState(dOSPort.getPear(), OpticalLink.CONNECTED_ML_CONN);
        //                                    }
        //
        //                                }
        //                            } else {
        //                                if (localRemovePorts != null) {
        //                                    try {
        //                                        ost.deleteConn(localRemovePorts);
        //                                    } catch(Throwable t) {
        //                                        bstatus = false;
        //                                        logger.log(Level.WARNING, " [ OpticalPathAgent_v2 ] Got exception deleting conn: [ " + localRemovePorts + "]", t );
        //                                    }
        //                                    
        //                                    if(bstatus) {
        //                                        String sDelPorts[] = localRemovePorts.split(" - ");
        //
        //                                        OSPort sDelOSPort = new OSPort(sDelPorts[0], OSPort.INPUT_PORT);
        //                                        OSPort dDelOSPort = new OSPort(sDelPorts[1], OSPort.OUTPUT_PORT);
        //
        //                                        changePortState(sDelOSPort, OpticalLink.CONNECTED_FREE);
        //                                        changePortState(dDelOSPort, OpticalLink.CONNECTED_FREE);
        //                                        ((OpticalLink)cfg.osi.map.get(sDelOSPort)).opticalLinkID = null;
        //                                        ((OpticalLink)cfg.osi.map.get(dDelOSPort)).opticalLinkID = null;
        //                                        cfg.osi.crossConnects.remove(sDelOSPort);
        //                                        
        //                                        try {
        //                                            ost.makeConn(localPorts);
        //                                        } catch(Throwable t) {
        //                                            bstatus = false;
        //                                            logger.log(Level.WARNING, " [ OpticalPathAgent_v2 ] Got making making conn: [ " + localPorts + "]", t );
        //                                        }
        //                                        
        //                                        if(bstatus) {
        //                                            changePortState(sOSPort, OpticalLink.CONNECTED_ML_CONN);
        //                                            changePortState(dOSPort, OpticalLink.CONNECTED_ML_CONN);
        //                                        }
        //                                    }
        //                                } else {
        //                                    try {
        //                                        ost.makeConn(localPorts);
        //                                    } catch(Throwable t) {
        //                                        bstatus = false;
        //                                        logger.log(Level.WARNING, " [ OpticalPathAgent_v2 ] Got making deleting conn: [ " + localPorts + "]", t );
        //                                    }
        //
        //                                    if(bstatus) {
        //                                        changePortState(sOSPort, OpticalLink.CONNECTED_ML_CONN);
        //                                        changePortState(dOSPort, OpticalLink.CONNECTED_ML_CONN);
        //                                    }
        //                                }
        //                            }
        //                        }
        //
        //                        if (logger.isLoggable(Level.FINER)) {
        //                            logger.log(Level.FINER, "Making local conn [ " + localPorts + " ] status = " + status);
        //                        }
        //                    }
        //
        //                    if (bstatus) {
        //                        cfg.osi.commit(transactionID);
        //                        ((OpticalLink)cfg.osi.map.get(sOSPort)).opticalLinkID = opr.id;
        //                        ((OpticalLink)cfg.osi.map.get(dOSPort)).opticalLinkID = opr.id;
        //                        
        //                        cfg.osi.crossConnects.put(sOSPort, new OpticalCrossConnectLink(sOSPort, dOSPort, Integer.valueOf(OpticalCrossConnectLink.OK)));
        //                        if(opr.isFDX) {
        //                            OpticalCrossConnectLink occld = new OpticalCrossConnectLink(dOSPort.getPear(), sOSPort.getPear(), Integer.valueOf(OpticalCrossConnectLink.OK));
        //                            cfg.osi.crossConnects.put(dOSPort.getPear(), occld);
        //                            ((OpticalLink)cfg.osi.map.get(sOSPort.getPear())).opticalLinkID = opr.id;
        //                            ((OpticalLink)cfg.osi.map.get(dOSPort.getPear())).opticalLinkID = opr.id;
        //                       }
        //                        
        //                       opr.sentCMDs.put(localIDL, "ACK");
        //                        
        //                    } else {
        //                        cfg.osi.rollback(transactionID);
        //                        opr.sentCMDs.put(localIDL, "NACK");
        //                    }
        //                } catch (Throwable t) {
        //                    logger.log(Level.WARNING, " Got exception ", t);
        //                    cfg.osi.rollback(transactionID);  
        //                }
        //                
        ////                clearDelayMonitoringResult();
        //            } else {
        //                logger.log(Level.WARNING, "\n\n\nLocalPorts [ " + localPorts + " ] but localIDL == null!!! \n\n");
        //            }
        //        } else {
        //            if(localRemovePorts != null) {
        //                expectedSize--;
        //                cdl.countDown();
        //                OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
        //                if (ost != null) {
        //                    
        //                    String sDelPorts[] = localRemovePorts.split(" - ");
        //                    OSPort sDelOSPort = new OSPort(sDelPorts[0], OSPort.INPUT_PORT);
        //                    OSPort dDelOSPort = new OSPort(sDelPorts[1], OSPort.OUTPUT_PORT);
        //                    
        //                    if(opr.isFDX) {
        //                        boolean bstatus = true;
        //                        try {
        //                            ost.deleteFDXConn(localRemovePorts);
        //                        } catch(Throwable t) {
        //                            bstatus = false;
        //                            logger.log(Level.WARNING, " [ OpticalPathAgent_v2 ] Got making deleting conn: [ " + localRemovePorts + "]", t );
        //                        }
        //
        //                        if(bstatus) {
        //    
        //    
        //                            changePortState(sDelOSPort, OpticalLink.CONNECTED_FREE);
        //                            changePortState(dDelOSPort, OpticalLink.CONNECTED_FREE);
        //                            ((OpticalLink)cfg.osi.map.get(sDelOSPort)).opticalLinkID = null;
        //                            ((OpticalLink)cfg.osi.map.get(dDelOSPort)).opticalLinkID = null;
        //                            cfg.osi.crossConnects.remove(sDelOSPort);
        //    
        //                            changePortState(sDelOSPort.getPear(), OpticalLink.CONNECTED_FREE);
        //                            changePortState(dDelOSPort.getPear(), OpticalLink.CONNECTED_FREE);
        //                            ((OpticalLink)cfg.osi.map.get(sDelOSPort.getPear())).opticalLinkID = null;
        //                            ((OpticalLink)cfg.osi.map.get(dDelOSPort.getPear())).opticalLinkID = null;
        //                            cfg.osi.crossConnects.remove(dDelOSPort.getPear());
        //
        //                            opr.sentCMDs.put(localIDL, "ACK");
        //                        } else {
        //                            opr.sentCMDs.put(localIDL, "NACK");
        //                        }
        //                    } else {
        //                        boolean bstatus = true;
        //                        try {
        //                            ost.deleteFDXConn(localRemovePorts);
        //                        } catch(Throwable t) {
        //                            bstatus = false;
        //                            logger.log(Level.WARNING, " [ OpticalPathAgent_v2 ] Got making deleting conn: [ " + localRemovePorts + "]", t );
        //                        }
        //                        if(bstatus) {
        //    
        //                            changePortState(sDelOSPort, OpticalLink.CONNECTED_FREE);
        //                            changePortState(dDelOSPort, OpticalLink.CONNECTED_FREE);
        //                            ((OpticalLink)cfg.osi.map.get(sDelOSPort)).opticalLinkID = null;
        //                            ((OpticalLink)cfg.osi.map.get(dDelOSPort)).opticalLinkID = null;
        //                            cfg.osi.crossConnects.remove(sDelOSPort);
        //                            opr.sentCMDs.put(localIDL, "ACK");
        //                        } else {
        //                            opr.sentCMDs.put(localIDL, "NACK");
        //                        }
        //                    }
        //                }//if ost != null
        //            }
        //        }

        long wTime = System.currentTimeMillis();
        if (expectedSize > 0) {
            try {
                cdl.await(SLEEP_WAIT, TimeUnit.MILLISECONDS);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "\n\n Got Exc countDownLatch - waitForSentCMDtoFinish", t);
            }
        }
        logger.log(Level.INFO, "\n\n ----> Waiting took " + (System.currentTimeMillis() - wTime) + " ms\n\n");

        System.out.println(" sent = " + opr.sentCMDs.toString());

        int NACKCmdsNo = getSentCMDsStatusNo("NACK", idsToWait, opr.sentCMDs);
        int SENTCmdsNo = getSentCMDsStatusNo("SENT", idsToWait, opr.sentCMDs);

        if ((NACKCmdsNo == 0) && (SENTCmdsNo == 0)) {
            logger.log(Level.INFO, "\n\nCNX EST:\n\n" + opr.links.toString());
            //            cfg.setNewConfiguration(cfg.oswConfig, -1);
            status = "Optical path ESTABLISHED!";
        } else {
            if (NACKCmdsNo != 0) {
                status = "There are " + NACKCmdsNo + " NACKed!\n";
            }

            if (SENTCmdsNo != 0) {
                if (status == null) {
                    status = "";
                }
                status += "There are " + SENTCmdsNo + " SENT!\n";
            }

            for (Enumeration en = opr.links.keys(); en.hasMoreElements();) {
                String dAgent = (String) en.nextElement();
                String ports = (String) opr.links.get(dAgent);
                if (dAgent.equals(agentInfo.agentAddr)) {
                    String lports[] = ports.split(" - ");
                    delConn(lports[0], lports[1], true);
                } else {
                    AgentMessage amB = createMsg(getAndIncrementMSGID(), 1, 1, 8, dAgent, agentInfo.agentGroup,
                            "DCONN:-REQ:-" + ports + ":-" + (cmdsID++) + ":-" + opr.id + ":-"
                                    + ((opr.isFDX) ? "1" : "0"));
                    sendAgentMessage(amB);
                }
            }
            return false;
        }

        return true;
    }

    boolean deleteOLConns(OpticalPathRequest opr) {
        return deleteOLConns(opr, false);
    }

    boolean deleteOLConns(OpticalPathRequest opr, boolean partial) {
        String status = "";
        try {
            if ((opr == null) || (opr.links == null)) {
                logger.log(Level.WARNING, "OPR == null || OPR.LINKS == null ... could not determine current links");
                opr.status
                        .append("No such opticalPath ... Possible reasons: it expired or was deleted by an administrator");
                return false;
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "deleteOLConns : " + opr.links.toString());
            }

            ArrayList idsToWait = new ArrayList();
            opr.sentCMDs.clear();
            String localPorts = null;
            Long localIDL = null;

            int expectedSize = opr.links.size();
            CountDownLatch cdl = new CountDownLatch(expectedSize);
            opr.setCountDownLatch(cdl);

            for (Enumeration en = opr.links.keys(); en.hasMoreElements();) {
                String dAgent = (String) en.nextElement();
                String ports = (String) opr.links.get(dAgent);
                cmdsID++;
                Long idl = Long.valueOf(cmdsID);
                idsToWait.add(idl);
                if (dAgent.equals(agentInfo.agentAddr)) { // try to be quickest ... send remote CMD-s first
                    localPorts = ports;
                    localIDL = idl;
                } else {
                    AgentMessage amB = null;

                    if (partial) {
                        amB = createMsg(getAndIncrementMSGID(), 1, 1, 5, dAgent, agentInfo.agentGroup, "PDCONN:-REQ:-"
                                + ports + ":-" + idl + ":-" + opr.id + ":-" + ((opr.isFDX) ? "1" : "0"));
                    } else {
                        amB = createMsg(getAndIncrementMSGID(), 1, 1, 5, dAgent, agentInfo.agentGroup, "DCONN:-REQ:-"
                                + ports + ":-" + idl + ":-" + opr.id + ":-" + ((opr.isFDX) ? "1" : "0"));
                    }
                    opr.sentCMDs.put(idl, "SENT");
                    sendAgentMessage(amB);
                }
            }

            // Remote CMD-s sent ... so try to do the local work (if any)
            if (localPorts != null) {
                if (localIDL != null) {
                    expectedSize--;
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "\n[ REMOVE ME!!] expectedSize = " + expectedSize);
                    }
                    cdl.countDown();
                    boolean bstatus = true;
                    // TODO - extra checks !!
                    OSTelnet ost = OSTelnetFactory.getControlInstance(OSwFSM.getInstance().oswConfig.type);
                    String sdports[] = localPorts.split(" - ");
                    if (partial) {
                        if (opr.pDconn == null) {
                            opr.pDconn = localPorts;
                        } else {
                            opr.pDconn += " - " + localPorts;
                        }
                    }

                    OSPort sOSPort = new OSPort(sdports[0], OSPort.INPUT_PORT);
                    OSPort dOSPort = new OSPort(sdports[1], OSPort.OUTPUT_PORT);
                    setDelayMonitoringResult();

                    try {
                        if (opr.isFDX) {
                            ost.deleteFDXConn(localPorts);
                        } else {
                            ost.deleteConn(localPorts);
                        }
                    } catch (Throwable t) {
                        bstatus = false;
                        logger.log(Level.WARNING, " [ OpticalPathAgent_v2 ] Got making deleting conn: [ " + localPorts
                                + "]", t);
                    }

                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "Del conn [ " + localPorts + " ] status = " + status);
                    }

                    //                    if (bstatus) {
                    //                        
                    //                        changePortState(sOSPort, OpticalLink.CONNECTED_FREE);
                    //                        changePortState(dOSPort, OpticalLink.CONNECTED_FREE);
                    //                        ((OpticalLink)cfg.osi.map.get(sOSPort)).opticalLinkID = null;
                    //                        ((OpticalLink)cfg.osi.map.get(dOSPort)).opticalLinkID = null;
                    //                        cfg.osi.crossConnects.remove(sOSPort);
                    //                        
                    //                        if(opr.isFDX) {
                    //                            changePortState(sOSPort.getPear(), OpticalLink.CONNECTED_FREE);
                    //                            changePortState(dOSPort.getPear(), OpticalLink.CONNECTED_FREE);
                    //                            ((OpticalLink)cfg.osi.map.get(sOSPort.getPear())).opticalLinkID = null;
                    //                            ((OpticalLink)cfg.osi.map.get(dOSPort.getPear())).opticalLinkID = null;
                    //                            cfg.osi.crossConnects.remove(dOSPort.getPear());
                    //                        }
                    //                        
                    //                        opr.sentCMDs.put(localIDL, "ACK");
                    //                        
                    //                    } else {
                    //                        opr.sentCMDs.put(localIDL, "NACK");
                    //                    }

                } else {
                    logger.log(Level.WARNING, "\n\n\nLocalPorts [ " + localPorts + " ] but localIDL == null!!! \n\n");
                }
            }

            long wTime = System.currentTimeMillis();
            if (expectedSize > 0) {
                try {
                    cdl.await(SLEEP_WAIT, TimeUnit.MILLISECONDS);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "\n\n Got Exc countDownLatch - waitForSentCMDtoFinish", t);
                }
            }
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "\n\n ----> Waiting took " + (System.currentTimeMillis() - wTime) + " ms\n\n");
            }

            if (getSentCMDsStatusNo("NACK", idsToWait, opr.sentCMDs) == 0) {
                //                cfg.setNewConfiguration(cfg.oswConfig, -1);
                status = "Optical Path released ...";
            } else {
                status = "There are " + getSentCMDsStatusNo("NACK", idsToWait, opr.sentCMDs) + " NACKed!";
                for (Enumeration en = opr.links.keys(); en.hasMoreElements();) {
                    String dAgent = (String) en.nextElement();
                    String ports = (String) opr.links.get(dAgent);
                    AgentMessage amB = createMsg(getAndIncrementMSGID(), 1, 1, 8, dAgent, agentInfo.agentGroup,
                            "DCONN:-REQ:-" + ports + ":-" + (cmdsID++) + ":-" + opr.id);
                    sendAgentMessage(amB);
                }
            }

            //          clearDelayMonitoringResult();
            return true;
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception ", t);
        }
        return false;
    }

    void setDelayMonitoringResult() {
        try {
            monitoringResultsWriteLock.lock();
            ignoreUntil = System.currentTimeMillis() + OpticalPathAgent_v2.IGNORE_MONITORING_CMAP_DELAY;
        } catch (Throwable t) {
        } finally {
            monitoringResultsWriteLock.unlock();
        }
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " setDelayMonitoringResult unlock ");
        }
    }

    void clearDelayMonitoringResult() {
        try {
            monitoringResultsWriteLock.lock();
            ignoreUntil = System.currentTimeMillis() - 1;
        } catch (Throwable t) {
        } finally {
            monitoringResultsWriteLock.unlock();
        }
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " clearDelayMonitoringResult unlock ");
        }
    }

    /**
     * DO NOT USE THIS YET ...  
     */
    boolean ignoreMonitoringResult() {
        boolean ignore = false;
        try {
            monitoringResultsReadLock.lock();
            ignore = (System.currentTimeMillis() < ignoreUntil);
        } catch (Throwable t) {
        } finally {
            monitoringResultsReadLock.unlock();
        }
        return ignore;
    }

    private int getSentCMDsStatusNo(String status, ArrayList ids, Hashtable sentCMDs) {
        int counter = 0;
        for (Iterator it = sentCMDs.keySet().iterator(); it.hasNext();) {
            Object key = it.next();
            if (ids.contains(key)) {
                if (((String) sentCMDs.get(key)).equals(status)) {
                    counter++;
                }
            }
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "<<<<<<<<<==" + counter + "==>>>>>>" + sentCMDs);
        }
        return counter;
    }

    private String getAgentAddress(String name) throws Exception {
        if (OSwFSM.getInstance().oswConfig.name.equals(name)) {
            return getAddress();
        }
        for (Enumeration en = otherConfs.keys(); en.hasMoreElements();) {
            String agentAddress = (String) en.nextElement();
            OSwConfig osi = (OSwConfig) otherConfs.get(agentAddress);
            if ((osi != null) && osi.name.equals(name)) {
                return agentAddress;
            }
        }

        return null;
    }

    private Hashtable getOpticalLinks(HashMap theLinks, String[] sOpticalPath, OpticalPathRequest opr,
            boolean ignoreIdle) {

        if ((sOpticalPath == null) || (sOpticalPath.length < 2)) {
            return null;
        }

        for (int i = 1; i < (sOpticalPath.length - 1); i++) {
            String source = sOpticalPath[i - 1];
            String dest = sOpticalPath[i + 1];
            //            if(i - 1 == 0) {
            //                OpticalLink srcOL = null;
            //                OpticalLink dstOL = null;
            //                
            //                OSwConfig oss = getOpticalSwitchForEndPoint(source);
            //                if(i+1 == sOpticalPath.length - 1) {
            //                    for(Iterator it = oss.map.keySet().iterator(); it.hasNext();) {
            //                        OSPort osp = (OSPort)it.next();
            //                        OpticalLink olink = (OpticalLink)oss.map.get(osp);
            //                        if(srcOL == null && olink.destination.equals(source)) {
            //                            srcOL = olink;
            //                            if(dstOL != null) break;
            //                        } else if(dstOL == null && olink.destination.equals(dest)){
            //                            dstOL = olink;
            //                            if(srcOL != null) break;
            //                        }
            //                    }
            //                    
            //                    if(srcOL == null && dstOL == null) {
            //                        logger.log(Level.WARNING, "[ProtocolException] Got null srcOL OR dstOL [ " + source + " -> " + sOpticalPath[i] + " -> " + dest + " ] ");
            //                        return null;
            //                    }
            //                    opr.links.put(getAgentAddress(sOpticalPath[i]), srcOL.port.name + " - " + dstOL.port.name );
            //                } else {
            //                    dstOL = (OpticalLink)theLinks.get(sOpticalPath[i] + " - " + dest);
            //                    for(Iterator it = oss.map.keySet().iterator(); it.hasNext();) {
            //                        OSPort osp = (OSPort)it.next();
            //                        OpticalLink olink = (OpticalLink)oss.map.get(osp);
            //                        if(srcOL == null && olink.destination.equals(source)) {
            //                            srcOL = olink;
            //                            break;
            //                        }
            //                    }
            //                    if(srcOL == null && dstOL == null) {
            //                        logger.log(Level.WARNING, "[ProtocolException] Got null srcOL OR dstOL [ " + source + " -> " + sOpticalPath[i] + " -> " + dest + " ] ");
            //                        return null;
            //                    }
            //                    opr.links.put(getAgentAddress(sOpticalPath[i]), srcOL.port.name + " - " + dstOL.port.name );
            //                }
            //            } else if (i+1 == sOpticalPath.length - 1){
            //                OpticalLink srcOL = (OpticalLink)theLinks.get(source + " - " + sOpticalPath[i]);
            //                OpticalLink dstOL = null;
            //                SyncOpticalSwitchInfo oss = getOpticalSwitchForEndPoint(dest);
            //
            //                for(Iterator it = oss.map.keySet().iterator(); it.hasNext();) {
            //                    OSPort osp = (OSPort)it.next();
            //                    OpticalLink olink = (OpticalLink)oss.map.get(osp);
            //                    if(dstOL == null && olink.destination.equals(dest)) {
            //                        dstOL = olink;
            //                        break;
            //                    }
            //                }
            //                if(srcOL == null && dstOL == null) {
            //                    logger.log(Level.WARNING, "[ProtocolException] Got null srcOL OR dstOL [ " + source + " -> " + sOpticalPath[i] + " -> " + dest + " ] ");
            //                    return null;
            //                }
            //                opr.links.put(getAgentAddress(sOpticalPath[i]), srcOL.destinationPortName + " - " + dstOL.port.name );
            //            } else {
            //                addOCC(theLinks, sOpticalPath[i], source, dest, opr, ignoreIdle);
            //            }
        }
        return opr.links;
    }

    private class EndPointEntry {
        OSwConfig oswConfig;
        OSwPort inputPort;
        OSwPort outputPort;

        private EndPointEntry(OSwConfig oswConfig, OSwPort inputPort, OSwPort outputPort) {
            this.oswConfig = oswConfig;
            this.inputPort = inputPort;
            this.outputPort = outputPort;
        }
    }

    private EndPointEntry[] getEndPointEntriesForEndPoint(OSwConfig oswConfig, String endPoint, boolean isSource,
            boolean isFDX) throws Exception {

        OSwLink[] inputLinks = null;
        OSwPort[] outputPorts = null;

        if (isSource) {
            inputLinks = (OSwLink[]) oswConfig.localHosts.get(endPoint);
            //
            //if the endpoind is a "source" if looking for full-duplex links 
            //the OSwConfig must contain the "endPoint" 
            //as a key in the "localHosts" hash
            //
            if ((inputLinks == null) || (inputLinks.length == 0)) {
                return null;
            }
            if (isFDX) {
                outputPorts = oswConfig.getOutputPortsForEndPoint(endPoint);
            }
        } else {
            outputPorts = oswConfig.getOutputPortsForEndPoint(endPoint);
            if ((outputPorts == null) || (outputPorts.length == 0)) {
                return null;
            }
            if (isFDX) {
                inputLinks = (OSwLink[]) oswConfig.localHosts.get(endPoint);
            }
        }

        if (isFDX
                && ((inputLinks == null) || (inputLinks.length == 0) || (outputPorts == null) || (outputPorts.length == 0))) {
            return null;
        }

        ArrayList retArray = new ArrayList();

        if (isSource) {
            for (OSwLink oswLink : inputLinks) {
                OSwPort inputPort = oswConfig.getPort(oswLink.destinationPortName, OSwPort.INPUT_PORT);
                if (inputPort == null) {//this should be logged !!
                    logger.log(Level.WARNING, "\n\n [ ProtocolException ] Wrong Configuration for " + oswConfig
                            + "\n Searching input port for endPoint: " + endPoint + "\n\n");
                    continue;
                }
                OSwPort outputPort = null;
                if (isFDX) {
                    outputPort = oswConfig.getPort(inputPort.name, OSwPort.OUTPUT_PORT);
                    if (outputPort == null) {
                        logger.log(Level.WARNING, "\n\n [ ProtocolException ] Asymetric Config for " + oswConfig
                                + "\n Searching output port for endPoint: " + endPoint
                                + " ... input port already defined\n\n");
                        continue;
                    }

                    if (outputPort.oswLink == null) {
                        logger.log(Level.WARNING, "\n\n [ ProtocolException ] Asymetric Config for " + oswConfig
                                + "\n Searching output port for endPoint: " + endPoint
                                + " ... input port already defined, but oswLink  == null for output port\n\n");
                        continue;
                    }

                    if (outputPort.oswLink.destination == null) {
                        logger.log(
                                Level.WARNING,
                                "\n\n [ ProtocolException ] Asymetric Config for "
                                        + oswConfig
                                        + "\n Searching output port for endPoint: "
                                        + endPoint
                                        + " ... input port already defined, but oswLink.destination == null for output port\n\n");
                        continue;
                    }

                    if (!outputPort.oswLink.destination.equals(endPoint)) {
                        logger.log(Level.WARNING, "\n\n [ ProtocolException ] Asymetric Config for " + oswConfig
                                + "\n Searching output port for endPoint: " + endPoint
                                + " ... input port already defined, but oswLink.destination ("
                                + outputPort.oswLink.destination + ") != endPoint\n\n");
                        continue;
                    }
                }

                retArray.add(new EndPointEntry(oswConfig, inputPort, outputPort));
            }
        } else {
            for (OSwPort outputPort : outputPorts) {
                OSwPort inputPort = null;
                if (isFDX) {
                    inputPort = oswConfig.getPort(outputPort.name, OSwPort.INPUT_PORT);
                    if (inputPort == null) {
                        logger.log(Level.WARNING, "\n\n [ ProtocolException ] Asymetric Config for " + oswConfig
                                + "\n Searching input port for endPoint: " + endPoint
                                + " ... output port already defined\n\n");
                        continue;
                    }

                    boolean found = false;
                    for (OSwLink inputLink : inputLinks) {
                        if (inputLink.destinationPortName.equals(inputPort.name)) {
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        logger.log(Level.WARNING, "\n\n [ ProtocolException ] Asymetric Config for " + oswConfig
                                + "\n Searching input port for endPoint: " + endPoint
                                + " ... output port already defined\n\n");
                        continue;
                    }
                }

                retArray.add(new EndPointEntry(oswConfig, inputPort, outputPort));
            }//for
        }

        if (retArray.size() == 0) {
            return null;
        }

        return (EndPointEntry[]) retArray.toArray(new EndPointEntry[retArray.size()]);
    }

    /**
     * 
     * @param endPoint
     * @return 
     * @throws Exception
     */
    private EndPointEntry[] getOpticalSwitchForEndPoint(String endPoint, boolean isSource, boolean isFDX)
            throws Exception {

        // my conf
        OSwConfig oswConfig = OSwFSM.getInstance().oswConfig;
        EndPointEntry[] epEntries = getEndPointEntriesForEndPoint(oswConfig, endPoint, isSource, isFDX);

        if (epEntries != null) {
            return epEntries;
        }

        for (Enumeration enums = otherConfs.elements(); enums.hasMoreElements();) {
            oswConfig = (OSwConfig) enums.nextElement();
            epEntries = getEndPointEntriesForEndPoint(oswConfig, endPoint, isSource, isFDX);

            if (epEntries != null) {
                return epEntries;
            }
        }//for

        return null;
    }

    private void deliverResults2ML(Object o) {
        if (o != null) {
            Vector notifResults = new Vector();
            Vector storeResults = new Vector();

            if ((o instanceof Vector) && (((Vector) o).size() > 0)) {
                Vector allResults = (Vector) o;

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
    }

    private void checkIfIsUP() throws Exception {
        boolean oldStatus = OSwFSM.getInstance().oswConfig.isAlive;
        try {
            OSTelnet ost = OSTelnetFactory.getControlInstance(OSwFSM.getInstance().oswConfig.type);
            OSwFSM.getInstance().oswConfig.isAlive = ost.isConnected();
        } catch (Throwable t) {
            OSwFSM.getInstance().oswConfig.isAlive = false;
        }

        if (oldStatus != OSwFSM.getInstance().oswConfig.isAlive) {
            logger.log(Level.INFO, "\n\nSwitch changed it's isAlive flag !!!" + OSwFSM.getInstance().oswConfig.isAlive);
            //            cfg.setNewConfiguration(cfg.oswConfig, -1);
        }
    }

    @Override
    public void doWork() {
        int del = 4;
        int counter = 0;
        int counterCheckPname = 0;

        long now = NTPDate.currentTimeMillis();

        while (hasToRun) {
            try {

                if ((lastAgentListMsg + AGENT_LIST_MSG_DELAY) < now) {
                    try {
                        if (agentComm == null) {
                            logger.log(Level.WARNING, "AgentsComm e null :((");
                        } else {
                            //                            AgentMessage amB = createCtrlMsg(getAndIncrementMSGID(), 1, 1, 5, null, null, "MLCopyGroup");
                            AgentMessage amB = createCtrlMsg(getAndIncrementMSGID(), 1, 1, 5, null, null,
                                    agentInfo.agentGroup);
                            agentComm.sendCtrlMsg(amB, "list");
                            lastAgentListMsg = now;
                        } // if - else
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Got exc sending message to proxy", t);
                    }
                }

                if (shouldCheckPortNames) {
                    if ((counterCheckPname % 10) == 0) {
                        counterCheckPname = 0;
                        checkPortName();
                    }
                    counterCheckPname++;
                }

                if ((counter % 2) == 0) {
                    counter = 0;
                    checkIfIsUP();
                }

                counter++;

                if (!attrPublished) {
                    publishAttrs();
                }
                try {
                    Thread.sleep(5 * 1000);
                } catch (Exception e) {
                }

                if (del == 5) {
                    deliverResults2ML(expressResults());
                    del = 0;
                }
                del++;

                if ((lastBConfSent + BCONF_DELAY) < System.currentTimeMillis()) {
                    sendBConfToAll();
                }

            } catch (Throwable t) {
                logger.log(Level.WARNING, " OpticalPathAgent_v2 - Got Exception main loop", t);
            }
        }

        logger.log(Level.WARNING, " OpticalPathAgent_v2 hasToRun == " + hasToRun);
    } // doWork

    void sendBConfToAll() {

        lastBConfSent = System.currentTimeMillis();
        if (agentComm == null) {
            logger.log(Level.WARNING, "\n\n -----> AgentsComm e null :(( <------ \n\n");
        } else {
            // if(opticalPaths.size() == 0 && receivedCMDs.size() == 0) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "\n ---> sendBConfToAll() - OtherConf-s size: " + otherConfs.size()
                        + " <---\n");
            }
            AgentMessage amB = null;
            try {
                amB = createMsg(getAndIncrementMSGID(), 1, 1, 3, "bcast:" + agentInfo.agentGroup, agentInfo.agentGroup,
                        OSwFSM.getInstance().oswConfig);
            } catch (Exception exc) {
                logger.log(Level.WARNING, " Got exception creating config msg", exc);
                amB = null;
            }
            if (amB != null) {
                agentComm.sendMsg(amB);
            }
        }
    }

    /**
     * Renew for a specific Lease
     */
    @Override
    public boolean renew(Lease lease) {
        if (agentComm == null) {
            logger.log(Level.WARNING, "\n\n -----> AgentsComm e null :(( <------ \n\n");
            //TODO
            //maybe we should return false here ...
        } else {
            if (lease.getRemoteAgentAddress().equals(agentInfo.agentAddr)) {
                logger.log(Level.WARNING, " [ProtocolException]  Send renew to myself ??!!!");
                //renewAll(lease.getSessionID());
                return true;
            }
            if (logger.isLoggable(Level.FINER)) {
                try {
                    logger.log(Level.FINER,
                            "\n\n {LEASE_SYSTEM} Renewing for " + getSwName(lease.getRemoteAgentAddress())
                                    + "\nLease: " + lease + "\n");
                } catch (Throwable t) {
                }
            }
            AgentMessage amB = createMsg(getAndIncrementMSGID(), 1, 1, 3, lease.getRemoteAgentAddress(),
                    agentInfo.agentGroup, "LRENEW:-REQ:-" + lease.getSessionID());
            // agentComm.sendCtrlMsg(amB, "bcast");
            agentComm.sendMsg(amB);
        }
        return true;
    }

    /**
     * Renew for a specific sessionID ... with broadCast
     */
    @Override
    public boolean renewAll(String sessionID) {
        if (agentComm == null) {
            logger.log(Level.WARNING, "\n\n -----> AgentsComm e null :(( <------ \n\n");
            //TODO
            //maybe we should return false here ...
        } else {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "\n\n {LEASE_SYSTEM} Renewing BCAST ... for SESSIONID " + sessionID);
            }
            AgentMessage amB = createMsg(getAndIncrementMSGID(), 1, 1, 3, "bcast:" + agentInfo.agentGroup,
                    agentInfo.agentGroup, "LRENEW:-REQ:-" + sessionID);
            // agentComm.sendCtrlMsg(amB, "bcast");
            agentComm.sendMsg(amB);
        }
        return true;
    }

    public void cleanupSession(String sessionID) {
        try {
            MLOpticalPathSession mlps = (MLOpticalPathSession) currentSessions.get(sessionID);
            if (mlps != null) {
                mlps.stopIt(); //can be called multiple times
                currentSessions.remove(sessionID);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exc removing session " + sessionID, t);
        }
    }

    private void sendConf(String dAddr) throws Exception {
        AgentMessage amB = createMsg(getAndIncrementMSGID(), 1, 1, 5, dAddr, agentInfo.agentGroup,
                OSwFSM.getInstance().oswConfig);
        sendAgentMessage(amB);
    }

    public Object expressResults() {
        Vector retV = new Vector();
        String ClusterName = "OSAgent_PortStates";

        //TO be the same in all the Result-s returned from ML 
        long now = NTPDate.currentTimeMillis();
        try {
            OSwConfig oswConfig = OSwFSM.getInstance().oswConfig;
            if ((oswConfig.osPorts != null) && (oswConfig.osPorts.length > 0)) {
                for (OSwPort oswPort : oswConfig.osPorts) {
                    Result r = new Result();
                    r.ClusterName = ClusterName;
                    r.time = now;
                    r.NodeName = oswPort.name + "_" + oswPort.getDecodedPortType();
                    r.addSet("State", oswPort.powerState);
                    retV.add(r);

                    if (debugOSStates) {
                        eResult er = new eResult();
                        er.ClusterName = ClusterName;
                        er.time = now;
                        er.NodeName = oswPort.name + "_" + oswPort.getDecodedPortType();
                        er.addSet("State_Debug", oswPort.getDecodedPortPowerState());
                        retV.add(er);
                    }
                }//for
            }

            if ((lasteResult_SENT + dt_eResultSent) < now) {
                eResult er = new eResult();
                er.time = now;
                er.ClusterName = OS_CONFIG_CLUSTER;
                er.NodeName = "localhost";
                er.FarmName = farm.name;
                try {
                    byte[] buff = Utils.writeObject(OSwFSM.getInstance().oswConfig);
                    er.addSet("OSIConfig", buff);
                    logger.log(Level.INFO, "Sending eResult  [ " + buff.length + " ]: \n" + er.toString()
                            + "\n\n with OSconfig " + OSwFSM.getInstance().oswConfig.getExtendedStatus() + "\n\n");
                    retV.add(er);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Cannot serialize cfg.osi", t);
                }
                lasteResult_SENT = now;
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " This should not happen! Got exception notifying clients", t);
        }

        return retV;
    }

    private void notifyCache(Vector storeResults) {
        cache.notifyInternalResults(storeResults);
    }

    void changePortState(OSPort port, Integer newState) {
        //        cfg.osi.changePortState(port, newState);
    }

    @Override
    public void processMsg(Object msg) {
        if (msg == null) {
            return;
        }
        executor.execute(new MLCopyAgentTask(msg, System.currentTimeMillis()));
    } // processMsg

    public String makeConn(String sPort, String dPort, boolean isFDX) {

        if ((sPort != null) && (dPort == null)) {
            monOSPortsPower.simulatedPortsHash.remove(sPort + "_In");
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "\n\n Remote 'Disable Simulate' received for port: " + sPort);
            }
            return "OK";
        }

        //        logger.log(Level.INFO, "\n\n Remote MAKE CMD " + sPort + ":" + dPort + " isFDX" + isFDX);
        //        if(sPort == null || dPort == null || cfg == null || cfg.osi == null) {
        //            return "Not OK";
        //        }
        //        
        //        if(sPort.length() == 0 || dPort.length() == 0) {
        //            return "Not OK";
        //        }
        //        
        //        Long transactionID = null;
        //        try {
        //            OSPort sOSPort = new OSPort(sPort, OSPort.INPUT_PORT);
        //            OSPort dOSPort = new OSPort(dPort, OSPort.OUTPUT_PORT);
        //            
        //            if(isFDX) {
        //                transactionID =  cfg.osi.beginTransaction(
        //                        new OSPort[]{
        //                                sOSPort, 
        //                                dOSPort, 
        //                                sOSPort.getPear(), 
        //                                dOSPort.getPear()
        //                                },
        //                        new Integer[][] {
        //                                new Integer[]{OpticalLink.CONNECTED_FREE},
        //                                new Integer[]{OpticalLink.CONNECTED_FREE},
        //                                new Integer[]{OpticalLink.CONNECTED_FREE},
        //                                new Integer[]{OpticalLink.CONNECTED_FREE}
        //                        },
        //                        new Integer[]{
        //                                OpticalLink.CONNECTED_OTHER_CONN, 
        //                                OpticalLink.CONNECTED_OTHER_CONN, 
        //                                OpticalLink.CONNECTED_OTHER_CONN,
        //                                OpticalLink.CONNECTED_OTHER_CONN,
        //                        }
        //                        );
        //            } else {
        //                transactionID = cfg.osi.beginTransaction(
        //                        new OSPort[]{
        //                                sOSPort, 
        //                                dOSPort, 
        //                                },
        //                        new Integer[][] {
        //                                new Integer[]{OpticalLink.CONNECTED_FREE},
        //                                new Integer[]{OpticalLink.CONNECTED_FREE},
        //                        },
        //                        new Integer[]{
        //                                OpticalLink.CONNECTED_OTHER_CONN, 
        //                                OpticalLink.CONNECTED_OTHER_CONN, 
        //                        }
        //                        );
        //            }//else
        //            
        //            boolean status = (transactionID != null);
        //            
        //            if(status) {
        //                OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
        //                try {
        //                    if(isFDX) {
        //                        ost.makeFDXConn(sPort + " - " + dPort);
        //                    } else {
        //                        ost.makeConn(sPort + " - " + dPort);
        //                    }
        //                }catch(Throwable t) {
        //                    status = false;
        //                    logger.log(Level.WARNING, " [ OpticalPathAgent_v2 ] Got exception deleting conn: [ " + sPort + "-" + dPort + "]", t );
        //                }
        //
        //            }
        //            
        //            if(status) {
        //                cfg.osi.commit(transactionID);
        //                OpticalCrossConnectLink occls = new OpticalCrossConnectLink(sOSPort, dOSPort, Integer.valueOf(OpticalCrossConnectLink.OK));
        //                cfg.osi.crossConnects.put(sOSPort, occls);
        //                
        //                if(isFDX) {
        //                     OpticalCrossConnectLink occld = new OpticalCrossConnectLink(dOSPort.getPear(), sOSPort.getPear(), Integer.valueOf(OpticalCrossConnectLink.OK));
        //                     cfg.osi.crossConnects.put(dOSPort.getPear(), occld);
        //                }
        //
        //                if(logger.isLoggable(Level.FINER)) {
        //                    logger.log(Level.FINER, " Adding OpticalCrossConnect [" + sPort + " -> " + dPort + "] cfg.osi.crossConnects = " + cfg.osi.crossConnects.toString());
        //                }
        //                
        //                cfg.setNewConfiguration(cfg.osi, -1);
        //            }
        //            
        //        } catch(Throwable t) {
        //            cfg.osi.rollback(transactionID);
        //            return "Not OK";
        //        }
        return "OK";
    }

    public String delConn(String sPort, String dPort, boolean isFDX) {

        if ((sPort != null) && (dPort == null)) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "\n\n Remote 'Enable Simulate' received for port: " + sPort);
            }
            monOSPortsPower.simulatedPortsHash.put(sPort + "_In", Double.valueOf(POWER_THRESHOLD - 0.5));
            return "OK";
        }

        logger.log(Level.INFO, " Remote DELETE CMD " + sPort + ":" + dPort + " isFDX" + isFDX);
        //        if(sPort == null || dPort == null || cfg == null || cfg.osi == null) {
        //            return "Not OK";
        //        }
        //        
        //        if(sPort.length() == 0 || dPort.length() == 0) {
        //            return "Not OK";
        //        }
        //        try {
        //            OSPort sOSPort = new OSPort(sPort, OSPort.INPUT_PORT);
        //            OSPort dOSPort = new OSPort(dPort, OSPort.OUTPUT_PORT);
        //
        //            OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
        //            if(ost == null) return "Not OK";
        //            
        //            setDelayMonitoringResult();
        //            
        //            boolean status = true;
        //            try {
        //                if(isFDX) {
        //                   ost.deleteFDXConn(sPort + " - " + dPort);
        //                } else {
        //                   ost.deleteConn(sPort + " - " + dPort);
        //                }
        //            }catch(Throwable t) {
        //                status = false;
        //                logger.log(Level.WARNING, " [ OpticalPathAgent_v2 ] Got exception deleting conn: [ " + sPort + "-" + dPort + "]", t );
        //            }
        //            
        //            if(status) {
        //
        //                changePortState(sOSPort, OpticalLink.CONNECTED_FREE);
        //                changePortState(dOSPort, OpticalLink.CONNECTED_FREE);
        //
        //                ((OpticalLink)cfg.osi.map.get(sOSPort)).opticalLinkID = null;
        //                ((OpticalLink)cfg.osi.map.get(dOSPort)).opticalLinkID = null;
        //
        //                cfg.osi.crossConnects.remove(sOSPort);
        //                
        //                if(isFDX) {
        //                    changePortState(sOSPort.getPear(), OpticalLink.CONNECTED_FREE);
        //                    changePortState(dOSPort.getPear(), OpticalLink.CONNECTED_FREE);
        //
        //                    ((OpticalLink)cfg.osi.map.get(sOSPort.getPear())).opticalLinkID = null;
        //                    ((OpticalLink)cfg.osi.map.get(dOSPort.getPear())).opticalLinkID = null;
        //                    
        //                    cfg.osi.crossConnects.remove(dOSPort.getPear());
        //                }
        //                
        //                if(logger.isLoggable(Level.FINER)) {
        //                    logger.log(Level.FINER, " Removing OpticalCrossConnect [" + sPort + " -> " + dPort + "] cfg.osi.crossConnects = " + cfg.osi.crossConnects.toString());
        //                } 
        //
        //                cfg.setNewConfiguration(cfg.osi, -1);
        //            }
        //
        ////            clearDelayMonitoringResult();
        //
        //        } catch(Throwable t) {
        //            return "Not OK";
        //        }
        return "OK";
    }

    private void checkCrossConns(Result r) {
        try {

            //            if(ignoreMonitoringResult()) {
            //                if(logger.isLoggable(Level.FINEST)) {
            //                    logger.log(Level.FINEST, " [ HANDLED ] checkCrossConns ignoring delayed Result " + r);
            //                }
            //                return;
            //            }
            //            
            //            boolean changedConf = false;
            //                
            //                if(logger.isLoggable(Level.FINEST)) {
            //                    logger.log(Level.FINEST, " checkCrossConns processing " + r.toString());
            //                    logger.log(Level.FINEST, "cfg.osi.crossConnects = " + cfg.osi.crossConnects.toString());
            //                }
            //                boolean found = false;
            //                
            //                String ports[] = r.NodeName.split(" - ");
            //                
            //                OSPort sOSPort = new OSPort(ports[0], OSPort.INPUT_PORT);
            //                OSPort dOSPort = new OSPort(ports[1], OSPort.OUTPUT_PORT);
            //                
            //                //it must be write !!
            //                monitoringResultsWriteLock.lock();
            //                try {//keep as small as possible the WR lock
            //                    for(Iterator it = cfg.osi.crossConnects.entrySet().iterator(); it.hasNext(); ) {
            //                        Map.Entry entry = (Map.Entry)it.next();
            //                        OpticalCrossConnectLink occl = (OpticalCrossConnectLink)entry.getValue();
            //                        
            //                        
            //                        if(logger.isLoggable(Level.FINEST)) {
            //                            logger.log(Level.FINEST, " checkCrossConns - Processing ...  " + occl.toString());
            //                        }
            //                        if( occl.sPort.equals(sOSPort) && occl.dPort.equals(dOSPort) ) {
            //                            found = true;
            //                            if(r.param[0] == OpticalCrossConnectLink.REMOVED ) {
            //                                changePortState(occl.sPort, OpticalLink.CONNECTED_FREE);
            //                                changePortState(occl.dPort, OpticalLink.CONNECTED_FREE);
            //                                
            //                                ((OpticalLink)cfg.osi.map.get(sOSPort)).opticalLinkID = null;
            //                                ((OpticalLink)cfg.osi.map.get(dOSPort)).opticalLinkID = null;
            //
            //                                cfg.osi.crossConnects.remove(sOSPort);
            //                                
            //                                if(logger.isLoggable(Level.FINER)) {
            //                                    logger.log(Level.FINER, " Removing OpticalCrossConnect " + occl.toString() + " cfg.osi.crossConnects = " + cfg.osi.crossConnects.toString());
            //                                } 
            //                                changedConf = true;
            //                            } else {
            //                                if(occl.status.intValue() != (int)r.param[0]) {
            //                                    if(logger.isLoggable(Level.FINEST)) {
            //                                        logger.log(Level.FINEST, " Changing state for OpticalCrossConnect " + occl.toString());
            //                                    }
            //                                    occl.status = Integer.valueOf((int)r.param[0]);
            //                                    changedConf = true;
            //                                }
            //                            }
            //                        }
            //                    }//for
            //                } catch (Throwable t) {
            //                    logger.log(Level.WARNING, " check cross conns exc", t);
            //                } finally {
            //                    monitoringResultsWriteLock.unlock();
            //                }
            //                    
            //                if(!found && (int)r.param[0] != OpticalCrossConnectLink.REMOVED ) {
            //
            //                        changePortState(sOSPort, OpticalLink.CONNECTED_OTHER_CONN);
            //                        changePortState(dOSPort, OpticalLink.CONNECTED_OTHER_CONN);
            //                        
            //                        OpticalCrossConnectLink occl1 = new OpticalCrossConnectLink(sOSPort, dOSPort, Integer.valueOf(OpticalCrossConnectLink.OK));
            //                        cfg.osi.crossConnects.put(sOSPort, occl1);
            //                        changedConf = true;
            //                }
            //                    
            //            if(changedConf) {
            //                cfg.setNewConfiguration(cfg.osi, 700);
            //            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exc checkCrossConns", t);
        }
    }

    private void checkPortPower(Result r) {

        //        if(ignoreMonitoringResult()) {
        //            if(logger.isLoggable(Level.FINEST)) {
        //                logger.log(Level.FINEST, " [ HANDLED ] checkPortPower ignoring delayed Result " + r);
        //            }
        //            return;
        //        }
        //        
        //        HashMap opticalLinksDown = new HashMap();
        //        try {
        //            monitoringResultsWriteLock.lock();
        //            if(r.ClusterName != null && r.ClusterName.indexOf("Ports") != -1 
        //                    &&  r.param != null && r.param_name != null
        //            ) {
        //                for(int i=0; i<r.param_name.length; i++) {
        //                    if(r.NodeName.indexOf("_") != -1) {
        //                        String splitedNames[] = r.NodeName.split("_");
        //                        String pName = splitedNames[0];
        //                        short type = (splitedNames[1].equals("In"))?OSPort.INPUT_PORT:OSPort.OUTPUT_PORT;
        //                        OSPort osp = new OSPort(pName, type);
        //                        OpticalLink ol = (OpticalLink)cfg.osi.map.get(osp);
        //                        if(ol != null){
        //                            ol.port.power = Double.valueOf(r.param[i]);
        //                            if(r.param[i] > POWER_THRESHOLD) {//Light
        //                                if(isSet(ol, OpticalLink.FREE | OpticalLink.CONNECTED) ) {
        //                                    ol.state = Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.IDLE_LIGHT); 
        //                                }
        //                            } else if(r.param[i] < POWER_THRESHOLD) {//NO Light
        //                                if(isSet(ol, OpticalLink.IDLE_LIGHT | OpticalLink.CONNECTED)) {
        //                                    ol.state = Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.FREE);
        //                                }
        //                                if( isSet(ol, OpticalLink.OTHER_CONN) ) {
        //                                    // changePortState(splitedNames[0], OpticalLink.OTHER_CONN | OpticalLink.CONN_FAIL);
        //                                    logger.log(Level.WARNING, "\n\n Changing to fail !!!! for port ["+splitedNames[0]+"]\n\n");
        //                                }
        //                                
        //                                if(type == OSPort.INPUT_PORT && ol.opticalLinkID != null) {
        //                                    if(!opticalLinksDown.containsKey(ol.opticalLinkID)) {
        //                                        ol.state = Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.CONN_FAIL);
        //                                        opticalLinksDown.put(ol.opticalLinkID, ol);
        //                                    } else {
        //                                        //This should not happen
        //                                        logger.log(Level.SEVERE, " [ ProtocolException ] !!! There are more OpticalLinks in different ports associated with the same opticalLinkID [ " + ol.opticalLinkID + " ]" );
        //                                    }
        //                                }//if
        //                            }//else id - NO Light
        //                        }//if (ol != null)
        //                    }//if
        //                }//for
        //            }//if
        //            
        //            if(opticalLinksDown.size() > 0) {//ML_LIKS down! Reroute!
        //                setDelayMonitoringResult();
        //                handleOpticalLinksDown(opticalLinksDown);
        //            }
        //        }catch(Throwable t){
        //            logger.log(Level.WARNING, " Got exc checkPortPower ["+r.toString()+"]", t);
        //        } finally {
        //            monitoringResultsWriteLock.unlock();
        //        }
    }

    private void handleOpticalLinksDown(final HashMap opticalLinksDown) throws Exception {
        for (Iterator it = opticalLinksDown.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();

            String olID = (String) entry.getKey();
            OpticalLink ol = (OpticalLink) entry.getValue();

            String dAgent = olID.split("//::")[0];
            AgentMessage amB = createMsg(getAndIncrementMSGID(), 1, 1, 8, dAgent, agentInfo.agentGroup, "PDOWN:-REQ:-"
                    + ol.destinationPortName + " - " + ol.port.name + ":-" + (cmdsID++) + ":-" + olID + ":-2");
            logger.log(Level.INFO, "Port DOWN [ " + ol.port.name + " ] Notify dAgent " + dAgent + " @ "
                    + getSwName(dAgent));
            sendAgentMessage(amB);
        }
    }

    /**
     * Monitoring info will came here .... we'll be interested when the Real OS
     * will be monitored...until then just ignore it
     * 
     * @see lia.Monitor.monitor.MonitorFilter#addNewResult(java.lang.Object)
     */
    @Override
    public void addNewResult(Object o) {
        // System.out.println("Got Result: " + r.toString());
        if (o == null) {
            return;
        }
        if (o instanceof Result) {
            Result r = (Result) o;
            if (r.Module != null) {
                if (r.Module.indexOf("PortsPower") != -1) {
                    checkPortPower(r);
                } else if (r.Module.indexOf("CrossConns") != -1) {
                    checkCrossConns(r);
                }
            }
        }

    }

    private void makeCConn(OpticalCrossConnectLink occl) throws Exception {
        OSTelnet ost = OSTelnetFactory.getControlInstance(OSwFSM.getInstance().oswConfig.type);
        boolean status = false;
        try {
            ost.makeFDXConn(occl.sPort + " - " + occl.dPort);
        } catch (Throwable t) {
            status = false;
            logger.log(Level.WARNING, " [ OpticalPathAgent_v2 ] Got exception making conn: [ " + occl.sPort + "-"
                    + occl.dPort + "]", t);
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Making conn [ " + occl.sPort + "," + occl.dPort + " ] status = " + status);
        }

        if (status) {
            //            changePortState(occl.sPort, OpticalLink.CONNECTED | OpticalLink.ML_CONN);
            //            changePortState(occl.dPort, OpticalLink.CONNECTED | OpticalLink.ML_CONN);
            // sendBConfToAll();
        }

    }

    private void deleteCConn(OpticalCrossConnectLink occl) throws Exception {
        OSTelnet ost = OSTelnetFactory.getControlInstance(OSwFSM.getInstance().oswConfig.type);
        boolean status = true;
        try {
            ost.deleteFDXConn(occl.sPort + " - " + occl.dPort);
        } catch (Throwable t) {
            status = false;
            logger.log(Level.WARNING, " [ OpticalPathAgent_v2 ] Got exception deleting conn: [ " + occl.sPort + "-"
                    + occl.dPort + "]", t);
        }
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Deleting conn [ " + occl.sPort + "," + occl.dPort + " ] status = " + status);
        }
        // if (status) {
        //        changePortState(occl.sPort, OpticalLink.CONNECTED | OpticalLink.FREE);
        //        changePortState(occl.dPort, OpticalLink.CONNECTED | OpticalLink.FREE);
        // sendBConfToAll();
        // }
    }

    /**
     * Will detect the OpticalLinks that were disconnected
     * 
     * @param oldOSI
     * @return A Vector of Result-s that must be notfied to Clients
     */
    private Vector computeConfDiff(OSwConfig oldOSI) throws Exception {
        Vector retV = new Vector();
        String ClusterName = "OS_" + OSwFSM.getInstance().oswConfig.name;

        //        try {
        //            if (oldOSI != null) {
        //                ConcurrentHashMap olh = oldOSI.map;
        //                ConcurrentHashMap new_olh = cfg.osi.map;
        //
        //                // looking for OpticalLinks
        //                for (Iterator it = olh.keySet().iterator(); it.hasNext();) {
        //                    try {
        //                        boolean shouldSend = false;
        //                        OSPort port = (OSPort) it.next();
        //                        OpticalLink ol = (OpticalLink) olh.get(port);
        //                        OpticalLink new_ol = (OpticalLink) new_olh.get(port);
        //                        if (logger.isLoggable(Level.FINEST)) {
        //                            logger.log(Level.FINEST, "Comparing new:\n " + new_ol + "\n with old:\n" + ol);
        //                        }
        //                        if (new_ol == null) {
        //                            shouldSend = true;
        //                        } else if (!ol.equals(new_ol)) {
        //                            shouldSend = true;
        //                        } else if ((new_ol.state.intValue() & OpticalLink.DISCONNECTED) == OpticalLink.DISCONNECTED) {
        //                            shouldSend = true;
        //                        }
        //
        //                        if (shouldSend) {
        //                            Result r = new Result();
        //                            r.ClusterName = ClusterName;
        //                            r.time = NTPDate.currentTimeMillis();
        //                            ;
        //                            r.NodeName = port.name + ":-" + ol.destination;
        //                            if (ol.type.shortValue() == OpticalLink.TYPE_SWITCH) {
        //                                r.NodeName += ":" + ol.destinationPortName;
        //                            }
        //                            r.addSet("State", OpticalLink.DISCONNECTED);
        //                            retV.add(r);
        //                        }
        //
        //                    } catch (Throwable t1) {
        //                        t1.printStackTrace();
        //                    }
        //                }
        //
        //                // looking for OpticalCrossConnectLinks
        //                ConcurrentHashMap oldCC = oldOSI.crossConnects;
        //                ConcurrentHashMap newCC = cfg.osi.crossConnects;
        //
        //                // DELETE the old ones that are no longer in the new conf
        //                for (Iterator it = oldCC.keySet().iterator(); it.hasNext();) {
        //                    OSPort osp = (OSPort) it.next();
        //                    OpticalCrossConnectLink occl = (OpticalCrossConnectLink) oldCC.get(osp);
        //                    if (newCC.containsValue(occl)) continue;
        //                    // must be deleted
        //                    deleteCConn(occl);
        //                }
        //
        //            }
        //            
        //            ConcurrentHashMap newCC = cfg.osi.crossConnects;
        //            ConcurrentHashMap oldCC = (oldOSI == null) ? null : oldOSI.crossConnects;
        //            
        //            // ADD the new ones
        //            for (Iterator it = newCC.keySet().iterator(); it.hasNext();) {
        //                OSPort osp = (OSPort)it.next();
        //                OpticalCrossConnectLink occl = (OpticalCrossConnectLink) newCC.get(osp);
        //                if (oldCC != null && oldCC.containsValue(occl)) continue;
        //                // must be added
        //                makeCConn(occl);
        //            }
        //        } catch (Throwable t) {
        //            logger.log(Level.WARNING, " Got Exception ex", t);
        //        }

        return retV;
    }

    private void publishAttrs() {
        try {
            HashMap hm = new HashMap();

            if (xdrMsgHandler != null) {
                hm.put("xdrTCPPort", Integer.valueOf(xdrMsgHandler.getXDRTCPPort()));
            }
            hm.put("OSProtocolVersion", "v2");
            hm.put("OS_CONFIG_CLUSTER", OS_CONFIG_CLUSTER);

            GMLEPublisher.getInstance().publishNow(hm);
            attrPublished = true;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got exception publishing attrs", t);
        }

    }

    /**
     * @see java.util.Observer#update(java.util.Observable, java.lang.Object)
     */
    @Override
    public void update(Observable o, Object arg) {
        try {
            StringBuilder sb = new StringBuilder();
            if (logger.isLoggable(Level.FINE)) {
                sb.append("OpticalPathAgent_v2 update conf...\n");
            }

            if ((OSwFSM.getInstance().oswConfig != null) && (o != null) && o.equals(OSwFSM.getInstance().oswConfig)) {// conf was changed
                if ((arg != null) && (arg instanceof OSwConfig)) {
                    if (logger.isLoggable(Level.FINE)) {
                        sb.append("OpticalPathAgent_v2 update conf...entering computeConfDiff()\n");
                    }

                    Vector v = computeConfDiff((OSwConfig) arg);
                    eResult er = new eResult();
                    er.time = NTPDate.currentTimeMillis();
                    er.ClusterName = OS_CONFIG_CLUSTER;
                    er.NodeName = "localhost";
                    er.FarmName = farm.name;

                    //keep the isAlive flag from the previous state
                    OSwFSM.getInstance().oswConfig.isAlive = ((OSwConfig) arg).isAlive;

                    try {
                        byte[] buffConfig = Utils.writeObject(OSwFSM.getInstance().oswConfig);
                        er.addSet("OSIConfig", buffConfig);
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, "Sending eResult attr ... cfg.osi.size == " + buffConfig.length);
                        }
                        v.add(er);
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Cannot serialize cfg.osi", t);
                    }
                    if ((v != null) && (v.size() > 0)) {
                        if (logger.isLoggable(Level.FINE)) {
                            sb.append("OpticalPathAgent_v2 update conf...entering delivering ").append(v.size())
                                    .append(" results ...\n");
                            for (int i = 0; i < v.size(); i++) {
                                sb.append(v.elementAt(i)).append("\n");
                            }
                        }
                        deliverResults2ML(v);
                        deliverResults2ML(expressResults());
                    }
                }
            }
            sendBConfToAll();
            publishAttrs();
            if (logger.isLoggable(Level.FINE)) {
                sb.append("OpticalPathAgent_v2 End update Conf\n\n");
                logger.log(Level.FINE, sb.toString());
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got Exception ", t);
        }
    }

    String getSwName(String agentAddress) throws Exception {
        if (agentAddress == null) {
            return "[AgentAddress is null] -> No Such Address";
        }

        if (agentAddress.equals(agentInfo.agentAddr)) {
            return agentAddress + " @ " + OSwFSM.getInstance().oswConfig.name;
        }

        OSwConfig osi = (OSwConfig) otherConfs.get(agentAddress);
        if (osi == null) {
            return agentAddress + " @ No such address";
        }

        return agentAddress + " @ " + osi.name;
    }

    public String changeNPPort(String eqptID, String ip, String mask, String gw) {
        try {
            OSTelnet ost = OSTelnetFactory.getControlInstance(OSwFSM.getInstance().oswConfig.type);
            boolean status = ost.changeNPPort(eqptID, ip, mask, gw);
            if (status) {
                return "OK";
            }
            return "Not OK";
        } catch (Throwable t) {
            return "Error " + t.getLocalizedMessage();
        }
    }

    public String changeOSPF(String routerID, String areaID) {
        try {
            OSTelnet ost = OSTelnetFactory.getControlInstance(OSwFSM.getInstance().oswConfig.type);
            boolean status = ost.changeOSPF(routerID, areaID);
            if (status) {
                return "OK";
            }
            return "Not OK";
        } catch (Throwable t) {
            return "Error " + t.getLocalizedMessage();
        }
    }

    public String changeRSVP(String msgRetryInvl, String ntfRetryInvl, String grInvl, String grcvInvl) {
        try {
            OSTelnet ost = OSTelnetFactory.getControlInstance(OSwFSM.getInstance().oswConfig.type);
            boolean status = ost.changeRSVP(msgRetryInvl, ntfRetryInvl, grInvl, grcvInvl);
            if (status) {
                return "OK";
            }
            return "Not OK";
        } catch (Throwable t) {
            return "Error " + t.getLocalizedMessage();
        }
    }

    public String addCtrlCh(String name, String remoteIP, String remoteRid, String port, String adj, String helloInvl,
            String helloInvlMin, String helloInvlMax, String deadInvl, String deadInvlMin, String deadInvlMax) {
        try {
            OSTelnet ost = OSTelnetFactory.getControlInstance(OSwFSM.getInstance().oswConfig.type);
            boolean status = ost.addCtrlCh(name, remoteIP, remoteRid, port, adj, helloInvl, helloInvlMin, helloInvlMax,
                    deadInvl, deadInvlMin, deadInvlMax);
            if (status) {
                return "OK";
            }
            return "Not OK";
        } catch (Throwable t) {
            return "Error " + t.getLocalizedMessage();
        }
    }

    public String delCtrlCh(String name) {
        try {
            OSTelnet ost = OSTelnetFactory.getControlInstance(OSwFSM.getInstance().oswConfig.type);
            boolean status = ost.delCtrlCh(name);
            if (status) {
                return "OK";
            }
            return "Not OK";
        } catch (Throwable t) {
            return "Error " + t.getLocalizedMessage();
        }
    }

    public String changeCtrlCh(String name, String remoteIP, String remoteRid, String port, String adj,
            String helloInvl, String helloInvlMin, String helloInvlMax, String deadInvl, String deadInvlMin,
            String deadInvlMax) {
        try {
            OSTelnet ost = OSTelnetFactory.getControlInstance(OSwFSM.getInstance().oswConfig.type);
            boolean status = ost.changeCtrlCh(name, remoteIP, remoteRid, port, adj, helloInvl, helloInvlMin,
                    helloInvlMax, deadInvl, deadInvlMin, deadInvlMax);
            if (status) {
                return "OK";
            }
            return "Not OK";
        } catch (Throwable t) {
            return "Error " + t.getLocalizedMessage();
        }
    }

    public String addAdj(String name, String ctrlCh, String remoteRid, String ospfArea, String metric, String ospfAdj,
            String adjType, String rsvpRRFlag, String rsvpGRFlag, String ntfProc) {
        try {
            OSTelnet ost = OSTelnetFactory.getControlInstance(OSwFSM.getInstance().oswConfig.type);
            boolean status = ost.addAdj(name, ctrlCh, remoteRid, ospfArea, metric, ospfAdj, adjType, rsvpRRFlag,
                    rsvpGRFlag, ntfProc);
            if (status) {
                return "OK";
            }
            return "Not OK";
        } catch (Throwable t) {
            return "Error " + t.getLocalizedMessage();
        }
    }

    public String deleteAdj(String name) {
        try {
            OSTelnet ost = OSTelnetFactory.getControlInstance(OSwFSM.getInstance().oswConfig.type);
            boolean status = ost.deleteAdj(name);
            if (status) {
                return "OK";
            }
            return "Not OK";
        } catch (Throwable t) {
            return "Error " + t.getLocalizedMessage();
        }
    }

    public String changeAdj(String name, String ctrlCh, String remoteRid, String ospfArea, String metric,
            String ospfAdj, String adjType, String rsvpRRFlag, String rsvpGRFlag, String ntfProc) {
        try {
            OSTelnet ost = OSTelnetFactory.getControlInstance(OSwFSM.getInstance().oswConfig.type);
            boolean status = ost.changeAdj(name, ctrlCh, remoteRid, ospfArea, metric, ospfAdj, adjType, rsvpRRFlag,
                    rsvpGRFlag, ntfProc);
            if (status) {
                return "OK";
            }
            return "Not OK";
        } catch (Throwable t) {
            return "Error " + t.getLocalizedMessage();
        }
    }

    public String addLink(String name, String localIP, String remoteIP, String adj) {

        try {
            OSTelnet ost = OSTelnetFactory.getControlInstance(OSwFSM.getInstance().oswConfig.type);
            boolean status = ost.addLink(name, localIP, remoteIP, adj);
            if (status) {
                return "OK";
            }
            return "Not OK";
        } catch (Throwable t) {
            return "Error " + t.getLocalizedMessage();
        }
    }

    public String delLink(String name) {

        try {
            OSTelnet ost = OSTelnetFactory.getControlInstance(OSwFSM.getInstance().oswConfig.type);
            boolean status = ost.delLink(name);
            if (status) {
                return "OK";
            }
            return "Not OK";
        } catch (Throwable t) {
            return "Error " + t.getLocalizedMessage();
        }
    }

    public static final Executor getExecutor() {
        return executor;
    }

    public String changeLink(String name, String localIP, String remoteIP, String linkType, String adj, String wdmAdj,
            String remoteIf, String wdmRemoteIf, String lmpVerify, String fltDetect, String metric, String port) {

        try {
            OSTelnet ost = OSTelnetFactory.getControlInstance(OSwFSM.getInstance().oswConfig.type);
            boolean status = ost.changeLink(name, localIP, remoteIP, linkType, adj, wdmAdj, remoteIf, wdmRemoteIf,
                    lmpVerify, fltDetect, metric, port);
            if (status) {
                return "OK";
            }
            return "Not OK";
        } catch (Throwable t) {
            return "Error " + t.getLocalizedMessage();
        }
    }

} // class OpticalPathAgent_v2
