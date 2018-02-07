package lia.Monitor.Agents.OpticalPath;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
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
import lia.Monitor.Agents.OpticalPath.Admin.OSAdminImpl;
import lia.Monitor.Agents.OpticalPath.Admin.OSAdminInterface;
import lia.Monitor.Agents.OpticalPath.Lease.Lease;
import lia.Monitor.Agents.OpticalPath.Lease.LeaseRenewal;
import lia.Monitor.Agents.OpticalPath.comm.XDRAuthZSSLTcpServer;
import lia.Monitor.Agents.OpticalPath.comm.XDRGenericComm;
import lia.Monitor.Agents.OpticalPath.comm.XDRMessage;
import lia.Monitor.Agents.OpticalPath.comm.XDRMessageNotifier;
import lia.Monitor.Agents.OpticalPath.comm.XDRSSLTcpServer;
import lia.Monitor.Agents.OpticalPath.comm.XDRTcpServer;
import lia.Monitor.ClientsFarmProxy.AgentsPlatform.AgentMessage;
import lia.Monitor.JiniSerFarmMon.GMLEPublisher;
import lia.Monitor.modules.monOSPortsPower;
import lia.Monitor.monitor.AgentInfo;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Gresult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.algo.Dijkstra.AdjacencyMatrixGraph;
import lia.util.algo.Dijkstra.Dijkstra;
import lia.util.algo.Dijkstra.IGraph;
import lia.util.algo.Dijkstra.Path;
import lia.util.exporters.RMIRangePortExporter;
import lia.util.ntp.NTPDate;
import lia.util.telnet.OSTelnet;
import lia.util.telnet.OSTelnetFactory;

public class MLCopyAgent extends AbstractAgent implements XDRMessageNotifier, Observer, LeaseRenewal {

    private static final long serialVersionUID = 3257284751210459449L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(MLCopyAgent.class.getName());

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
    private static AtomicLong opticalContor;

    XDRTcpServer server;

    /**
     * Local Configuration
     */
    Configuration cfg;

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

    private static int xdrTCPPort;

    private static boolean ping_pong_mode = false;// only for testing

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

    static {

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
            xdrTCPPort = Integer.valueOf(
                    AppConfig.getProperty("lia.Monitor.Agents.OpticalPath.MLCopyAgent.xdrTCPPort", "25001")).intValue();
        } catch (Throwable t) {
            xdrTCPPort = 25001;
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
                            "12")).longValue() * 1000;
        } catch (Throwable t) {
            IGNORE_MONITORING_CMAP_DELAY = 12 * 1000;
        }

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

                //Is it possible to receive other objects?
                if (msg instanceof AgentMessage) {
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

                    if (am.message instanceof OpticalSwitchInfo) {//remote conf
                        if (am.agentAddrS.equals(agentInfo.agentAddr)) {
                            return;
                        }
                        SyncOpticalSwitchInfo remoteOSI = SyncOpticalSwitchInfo
                                .fromOpticalSwitchInfo((OpticalSwitchInfo) am.message);
                        otherConfs.put(am.agentAddrS, remoteOSI);
                        swNameAddrHash.put(remoteOSI.name, am.agentAddrS);
                    } else if (am.message instanceof String) {//remote CMD
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, "\n\nReceived MSG: " + am.toString() + "\n\n");
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
                            logger.log(Level.WARNING, " Cannot deserialize REQ from " + getSwName(am.agentAddrS)
                                    + " [ MSG: " + smsg + " ]", t);
                            return;
                        }

                        MLPathSession mlPathSession = (MLPathSession) currentSessions.get(raMsg.session);

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
                            logger.log(Level.INFO, " Got remote ADMIN DEL ... [ "
                                    + (System.currentTimeMillis() - sTime) + " ]\n" + status);
                            return;
                        }

                        if (mlPathSession == null) {
                            mlPathSession = new MLPathSession(raMsg.session, MLCopyAgent.this, null, startTime);
                            currentSessions.put(raMsg.session, mlPathSession);
                        }

                        //it will be faster if put also ops[] ... does not make sens to split()-it again 
                        // we need to be fast&furious :)  
                        mlPathSession.notifyAgentMessage(am, raMsg, startTime);

                    } else if (am.message instanceof String[]) {
                        //more remote commands in the same message. Used for especially rerouting

                        //The commands will be processed in the same order as received. 
                        String[] sRemoteMessages = (String[]) am.message;
                        MLPathSession mlPathSession = null;

                        if (sRemoteMessages.length > 0) {

                            RemoteAgentMsgWrapper[] ramws = new RemoteAgentMsgWrapper[sRemoteMessages.length];
                            for (int iterSR = 0; iterSR < sRemoteMessages.length; iterSR++) {
                                try {
                                    ramws[iterSR] = RemoteAgentMsgWrapper.fromString(sRemoteMessages[iterSR]);

                                    //TODO - Only for extra checks in ProtocolExceptions 
                                    if (iterSR == 0) {//GET The default session
                                        mlPathSession = (MLPathSession) currentSessions.get(ramws[iterSR].session);
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
                                        if (mlPathSession != (MLPathSession) currentSessions.get(ramws[iterSR].session)) {
                                            logger.log(Level.WARNING, " [ ProtocolException ] Session [ "
                                                    + ramws[iterSR].session + " ] != DEFAULT Session "
                                                    + mlPathSession.opr.id);
                                            //TODO - send back NACK
                                            return;
                                        }
                                    }
                                } catch (Throwable t) {
                                    logger.log(Level.WARNING, " Cannot deserialize REQ from "
                                            + getSwName(am.agentAddrS) + " on POS[" + iterSR + "] --> [ MSG: "
                                            + sRemoteMessages[iterSR] + " ]", t);
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

    public MLCopyAgent(String agentName, String agentGroup, String farmID) {
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
                localAdminInterface = new OSAdminImpl(this);
                logger.log(
                        Level.INFO,
                        "Optical Switch Admin Interface started on [ "
                                + RMIRangePortExporter.getPort(localAdminInterface) + " ]");
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Cannot start the admin interface", t);
            }
        }

        try {
            boolean shouldUseSSL = false;
            try {
                String cf = AppConfig.getProperty("lia.Monitor.Agents.OpticalPath.MLCopyAgent.useSSL", null);
                if (cf != null) {
                    shouldUseSSL = Boolean.valueOf(cf).booleanValue();
                }
            } catch (Throwable t) {
                shouldUseSSL = false;
            }
            if (shouldUseSSL) {
                boolean shouldUseAuthz = false;
                try {
                    String cf = AppConfig.getProperty("lia.Monitor.Agents.OpticalPath.MLCopyAgent.useAuthZ", null);
                    if (cf != null) {
                        shouldUseAuthz = Boolean.valueOf(cf).booleanValue();
                    }
                } catch (Throwable t) {
                    shouldUseAuthz = false;
                }
                if (shouldUseAuthz) {
                    server = new XDRAuthZSSLTcpServer(xdrTCPPort, this);
                } else {
                    server = new XDRSSLTcpServer(xdrTCPPort, this);
                }
            } else {
                server = new XDRTcpServer(xdrTCPPort, this);
            }
            server.start();
        } catch (Throwable t) {
            hasToRun = false;
            t.printStackTrace();
        }

        try {
            String cf = AppConfig.getProperty("lia.Monitor.Agents.OpticalPath.MLCopyAgent.ConfFile", null);
            if (cf != null) {
                cfg = new Configuration(new File(cf.trim()), this);
            } else {
                cfg = null;
                hasToRun = false;
            }
        } catch (Throwable t) {
            t.printStackTrace();
            hasToRun = false;
        }
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

    @Override
    public void notifyXDRCommClosed(XDRGenericComm comm) {

    }

    String modifyConfigurations(String data) {
        // Decode the configurations in the message
        // Send cfg messages to the other agents ... if more then one
        // configuration specified
        sentCMDsConfs.clear();
        String sReturnValue = "";
        HashMap splittedConfs = null;
        ArrayList idsToWait = new ArrayList();
        try {
            splittedConfs = Util.splitConfigurations(new StringReader(data));
            if (splittedConfs == null) {
                sReturnValue = "Cannot decode any conf(s)";
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got exc while decoding conf", t);
            splittedConfs = null;
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(new BufferedWriter(sw), true));
            sReturnValue = sw.getBuffer().toString();
        }
        if (splittedConfs == null) {
            return sReturnValue;
        }

        StringBuilder sb = new StringBuilder();
        if (logger.isLoggable(Level.FINER)) {
            sb.append("Got ").append(splittedConfs.size()).append(" confs");
            for (Iterator it = splittedConfs.keySet().iterator(); it.hasNext();) {
                String swName = (String) it.next();
                sb.append("\n*********\nSTART Conf :- ").append(swName).append("\n***********\n");
                sb.append(splittedConfs.get(swName));
                sb.append("\n*********\nEND Conf :- ").append(swName).append("\n***********\n");
            }
            sb.append("END with all ").append(splittedConfs.size()).append(" confs");
            logger.log(Level.FINER, sb.toString());
        }

        StringBuilder status = new StringBuilder(1024);
        for (Iterator it = splittedConfs.keySet().iterator(); it.hasNext();) {
            cmdsID++;
            Long idl = Long.valueOf(cmdsID);
            idsToWait.add(idl);
            String swName = (String) it.next();
            status.append("\n Status for: ").append(swName);
            if (swName.equals(cfg.osi.name)) {// I should modify my own
                // conf...
                status.append(" ... my own address ... OK\n");
                cfg.setNewConfiguration(SyncOpticalSwitchInfo.fromOpticalSwitchInfo(Util
                        .getOpticalSwitchInfo(new StringReader((String) splittedConfs.get(swName)))), -1);
                sentCMDsConfs.put(idl, "ACK");
            } else {
                String agentAddr = (String) swNameAddrHash.get(swName);
                if (agentAddr == null) {
                    status.append("\n Error...No dstination agent address for this switch \n");
                    continue;
                }

                AgentMessage amB = createMsg(getAndIncrementMSGID(), 1, 1, 8, agentAddr, agentInfo.agentGroup,
                        "NCONF:-REQ:-" + splittedConfs.get(swName) + ":-" + idl);
                sentCMDsConfs.put(idl, "SENT");
                sendAgentMessage(amB);
            }
        }

        //TODO !!!
        //        waitForSentCMDtoFinish(idsToWait.size(), "xxxx");

        if (getSentCMDsStatusNo("NACK", idsToWait, sentCMDsConfs) == 0) {
            status.append(" ALL OK !");
        } else {
            status.append("There are " + getSentCMDsStatusNo("NACK", idsToWait, sentCMDsConfs) + " NACKed!");
        }

        sReturnValue = status.toString();
        return sReturnValue;
    }

    /**
     * messages coming from OSDaemon-s
     */
    @Override
    public void notifyXDRMessage(XDRMessage xdrMsg, XDRGenericComm comm) {
        try {
            //The very START time
            long notifyStartTime = System.currentTimeMillis();

            if (!ping_pong_mode) {
                MLPathSession mlPathSession = null;
                if (xdrMsg.olID != null) {
                    if (xdrMsg.olID.equals("NOSENSE")) { // I should start a new Session !!
                        final String sessionID = getAddress() + "//::" + opticalContor.incrementAndGet();
                        mlPathSession = new MLPathSession(sessionID, this, comm, notifyStartTime);
                        currentSessions.put(sessionID, mlPathSession);
                    } else {
                        mlPathSession = (MLPathSession) currentSessions.get(xdrMsg.olID);

                        if (mlPathSession == null) { // it should be inactive ....
                            try {
                                logger.log(Level.WARNING, " Got a XDR MSG bu session alreay expired ... notify ");
                                comm.write(XDRMessage.getErrorMessage("The session already expired ...\n\n"));
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, " Error sending message ", t);
                            }
                            return;
                        }
                    }
                    mlPathSession.notifyXDRMessage(xdrMsg, notifyStartTime);
                } else {
                    logger.log(Level.WARNING, " Got an xdrMsg with NULL olID!");
                    try {
                        comm.write(XDRMessage.getErrorMessage("Got an xdrMsg with NULL olID!\n\n"));
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " Error sending message ", t);
                    }
                    return;
                }
            } else {// only for debug
                xdrMsg.olID = "ping_pong_mode";
                long dT = System.currentTimeMillis() - notifyStartTime;
                xdrMsg.data = " Operation took " + dT + " ms";
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got Exception ex ... sending back to the ml_path client", t);
            XDRMessage msg = XDRMessage.getErrorMessage(t);
            try {
                comm.write(msg);
            } catch (Throwable tsend) {
                logger.log(Level.WARNING, "Got exc senfing message", tsend);
            }
        }
    }

    public boolean makeConnection(OpticalPathRequest opr, boolean ignoreIdle) {

        if (opr.destination.equals(opr.source)) {
            opr.status.append("Cannot EST cnx \"between\" the same host [ " + opr.source + " - " + opr.destination
                    + " ]");
            return false;
        }

        SyncOpticalSwitchInfo srcOSI = getOpticalSwitchForEndPoint(opr.source);
        SyncOpticalSwitchInfo dstOSI = getOpticalSwitchForEndPoint(opr.destination);

        if (srcOSI == null) {
            opr.status.append("Cannot find source [ ").append(opr.source).append(" ] in local conf");
            return false;
        }

        if (dstOSI == null) {
            opr.status.append("Cannot find destination [ ").append(opr.destination).append(" ] in local conf");
            return false;
        }

        String sourceOS = srcOSI.name;
        String destOS = dstOSI.name;

        IGraph igraph = null;
        Dijkstra djAlgo = null;
        Path path = null;
        StringBuilder sb = new StringBuilder();
        String[] sOpticalPath = null;

        sb.append("Optical Path [ ").append(opr.source);
        HashMap theLinks = new HashMap();
        if (!destOS.equals(sourceOS)) {
            igraph = buildCurrentGraph(theLinks, opr.isFDX, ignoreIdle ? opr.id : null);
            djAlgo = new Dijkstra(igraph);
            path = djAlgo.getShortestPath(sourceOS, destOS);

            if ((path == null) || (path.getLength() <= 0)) {
                //                return new String[] { "NoSuchLink", "Cannot establish a connection between source [ " + source + " ] - destination [ " + destination + " ] "};
                return false;
            }

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " Seems that I can est a conn ... : " + path.toString() + " path.getLength() = "
                        + path.getLength());
            }

            sOpticalPath = new String[path.getLength() + 3];
        } else {
            sOpticalPath = new String[3];
        }

        int opIndex = 0;
        sOpticalPath[opIndex++] = opr.source;
        if (sOpticalPath.length == 3) {
            sOpticalPath[opIndex++] = sourceOS;
            sb.append(" - ").append(sourceOS);
        } else {
            for (int i = 0; i <= path.getLength(); i++) {
                String sname = (String) path.get(i);
                sb.append(" - ").append(sname);
                sOpticalPath[opIndex++] = sname;
            }
        }

        sOpticalPath[opIndex] = opr.destination;

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
        currentSessions.put(olID, new MLPathSession(opr, this, System.currentTimeMillis()));
        if (!makeConnection(opr)) {
            status = "Cannot establish a conn [ " + opr.status.toString() + " ]";
            logger.log(Level.INFO, "\n\n GUI makeMLPathConn " + olID + " cannot be established");
            MLPathSession mlps = (MLPathSession) currentSessions.remove(opr.id);
            mlps.stopIt();
        } else {
            ((MLPathSession) currentSessions.get(olID)).registerMasterLeases();
        }
        return status;
    }

    public String deleteMLPathConn(String olID) {
        if (olID == null) {
            return " No such OLID == null";
        }
        String dAgent = olID.split("//::")[0];

        if (dAgent.equals(getAddress())) {//it's me
            boolean status = false;

            MLPathSession mlPathSession = (MLPathSession) currentSessions.get(olID);
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
        if (cfg.osi.type.shortValue() == OpticalSwitchInfo.GLIMMERGLASS) {
            try {
                OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
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

                                        for (Object element : cfg.osi.map.keySet()) {
                                            OSPort ospKey = (OSPort) element;
                                            if ((ospKey != null) && ospKey.equals(searchPort)) {
                                                ospKey.label = pLabel;
                                                if (logger.isLoggable(Level.FINEST)) {
                                                    logger.log(Level.FINEST, " Setting PortName = " + pLabel + " for "
                                                            + sPname);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Got exception parsing line [" + line + "]", t);
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Got exception reading from command's buffered stream", t);
            }
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

        if (localPorts != null) {
            if (localIDL != null) {
                setDelayMonitoringResult();

                expectedSize--;
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "\n[ REMOVE ME!!] expectedSize = " + expectedSize);
                }
                cdl.countDown();

                String sdports[] = localPorts.split(" - ");

                OSPort sOSPort = new OSPort(sdports[0], OSPort.INPUT_PORT);
                OSPort dOSPort = new OSPort(sdports[1], OSPort.OUTPUT_PORT);

                boolean bstatus = false;

                Long transactionID = null;
                if (opr.isFDX) {
                    transactionID = cfg.osi.beginTransaction(
                            new OSPort[] { sOSPort, dOSPort, sOSPort.getPear(), dOSPort.getPear() },
                            new Integer[][] {
                                    new Integer[] { ((localRemovePorts == null) || (localRemovePorts
                                            .indexOf(sOSPort.name) < 0)) ? OpticalLink.CONNECTED_FREE
                                            : OpticalLink.CONNECTED_ML_CONN },
                                    new Integer[] { ((localRemovePorts == null) || (localRemovePorts
                                            .indexOf(dOSPort.name) < 0)) ? OpticalLink.CONNECTED_FREE
                                            : OpticalLink.CONNECTED_ML_CONN },
                                    new Integer[] { ((localRemovePorts == null) || (localRemovePorts.indexOf(sOSPort
                                            .getPear().name) < 0)) ? OpticalLink.CONNECTED_FREE
                                            : OpticalLink.CONNECTED_ML_CONN },
                                    new Integer[] { ((localRemovePorts == null) || (localRemovePorts.indexOf(dOSPort
                                            .getPear().name) < 0)) ? OpticalLink.CONNECTED_FREE
                                            : OpticalLink.CONNECTED_ML_CONN } }, new Integer[] {
                                    OpticalLink.CONNECTED_ML_CONN, OpticalLink.CONNECTED_ML_CONN,
                                    OpticalLink.CONNECTED_ML_CONN, OpticalLink.CONNECTED_ML_CONN, });
                } else {
                    transactionID = cfg.osi.beginTransaction(
                            new OSPort[] { sOSPort, dOSPort, },
                            new Integer[][] {
                                    new Integer[] { ((localRemovePorts == null) || (localRemovePorts
                                            .indexOf(sOSPort.name) < 0)) ? OpticalLink.CONNECTED_FREE
                                            : OpticalLink.CONNECTED_ML_CONN },
                                    new Integer[] { ((localRemovePorts == null) || (localRemovePorts
                                            .indexOf(dOSPort.name) < 0)) ? OpticalLink.CONNECTED_FREE
                                            : OpticalLink.CONNECTED_ML_CONN }, }, new Integer[] {
                                    OpticalLink.CONNECTED_ML_CONN, OpticalLink.CONNECTED_ML_CONN, });
                }//else

                bstatus = (transactionID != null);

                try {
                    if (bstatus) {
                        OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());

                        if (ost != null) {
                            if (opr.isFDX) {
                                if (localRemovePorts != null) {
                                    try {
                                        ost.deleteFDXConn(localRemovePorts);
                                    } catch (Throwable t) {
                                        bstatus = false;
                                        logger.log(Level.WARNING, " [ MLCopyAgent ] Got exception deleting conn: [ "
                                                + localRemovePorts + "]", t);
                                    }

                                    if (bstatus) {
                                        String sDelPorts[] = localRemovePorts.split(" - ");

                                        OSPort sDelOSPort = new OSPort(sDelPorts[0], OSPort.INPUT_PORT);
                                        OSPort dDelOSPort = new OSPort(sDelPorts[1], OSPort.OUTPUT_PORT);

                                        changePortState(sDelOSPort, OpticalLink.CONNECTED_FREE);
                                        changePortState(dDelOSPort, OpticalLink.CONNECTED_FREE);
                                        cfg.osi.map.get(sDelOSPort).opticalLinkID = null;
                                        cfg.osi.map.get(dDelOSPort).opticalLinkID = null;
                                        cfg.osi.crossConnects.remove(sDelOSPort);

                                        changePortState(sDelOSPort.getPear(), OpticalLink.CONNECTED_FREE);
                                        changePortState(dDelOSPort.getPear(), OpticalLink.CONNECTED_FREE);
                                        cfg.osi.map.get(sDelOSPort.getPear()).opticalLinkID = null;
                                        cfg.osi.map.get(dDelOSPort.getPear()).opticalLinkID = null;
                                        cfg.osi.crossConnects.remove(dDelOSPort.getPear());

                                        try {
                                            ost.makeFDXConn(localPorts);
                                        } catch (Throwable t) {
                                            bstatus = false;
                                            logger.log(Level.WARNING, " [ MLCopyAgent ] Got exception making conn: [ "
                                                    + localPorts + "]", t);
                                        }

                                        if (bstatus) {
                                            changePortState(sOSPort, OpticalLink.CONNECTED_ML_CONN);
                                            changePortState(dOSPort, OpticalLink.CONNECTED_ML_CONN);
                                            changePortState(sOSPort.getPear(), OpticalLink.CONNECTED_ML_CONN);
                                            changePortState(dOSPort.getPear(), OpticalLink.CONNECTED_ML_CONN);
                                        }
                                    }
                                } else {
                                    try {
                                        ost.makeFDXConn(localPorts);
                                    } catch (Throwable t) {
                                        bstatus = false;
                                        logger.log(Level.WARNING, " [ MLCopyAgent ] Got exception making conn: [ "
                                                + localPorts + "]", t);
                                    }

                                    if (bstatus) {
                                        changePortState(sOSPort, OpticalLink.CONNECTED_ML_CONN);
                                        changePortState(dOSPort, OpticalLink.CONNECTED_ML_CONN);
                                        changePortState(sOSPort.getPear(), OpticalLink.CONNECTED_ML_CONN);
                                        changePortState(dOSPort.getPear(), OpticalLink.CONNECTED_ML_CONN);
                                    }

                                }
                            } else {
                                if (localRemovePorts != null) {
                                    try {
                                        ost.deleteConn(localRemovePorts);
                                    } catch (Throwable t) {
                                        bstatus = false;
                                        logger.log(Level.WARNING, " [ MLCopyAgent ] Got exception deleting conn: [ "
                                                + localRemovePorts + "]", t);
                                    }

                                    if (bstatus) {
                                        String sDelPorts[] = localRemovePorts.split(" - ");

                                        OSPort sDelOSPort = new OSPort(sDelPorts[0], OSPort.INPUT_PORT);
                                        OSPort dDelOSPort = new OSPort(sDelPorts[1], OSPort.OUTPUT_PORT);

                                        changePortState(sDelOSPort, OpticalLink.CONNECTED_FREE);
                                        changePortState(dDelOSPort, OpticalLink.CONNECTED_FREE);
                                        cfg.osi.map.get(sDelOSPort).opticalLinkID = null;
                                        cfg.osi.map.get(dDelOSPort).opticalLinkID = null;
                                        cfg.osi.crossConnects.remove(sDelOSPort);

                                        try {
                                            ost.makeConn(localPorts);
                                        } catch (Throwable t) {
                                            bstatus = false;
                                            logger.log(Level.WARNING, " [ MLCopyAgent ] Got making making conn: [ "
                                                    + localPorts + "]", t);
                                        }

                                        if (bstatus) {
                                            changePortState(sOSPort, OpticalLink.CONNECTED_ML_CONN);
                                            changePortState(dOSPort, OpticalLink.CONNECTED_ML_CONN);
                                        }
                                    }
                                } else {
                                    try {
                                        ost.makeConn(localPorts);
                                    } catch (Throwable t) {
                                        bstatus = false;
                                        logger.log(Level.WARNING, " [ MLCopyAgent ] Got making deleting conn: [ "
                                                + localPorts + "]", t);
                                    }

                                    if (bstatus) {
                                        changePortState(sOSPort, OpticalLink.CONNECTED_ML_CONN);
                                        changePortState(dOSPort, OpticalLink.CONNECTED_ML_CONN);
                                    }
                                }
                            }
                        }

                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, "Making local conn [ " + localPorts + " ] status = " + status);
                        }
                    }

                    if (bstatus) {
                        cfg.osi.commit(transactionID);
                        cfg.osi.map.get(sOSPort).opticalLinkID = opr.id;
                        cfg.osi.map.get(dOSPort).opticalLinkID = opr.id;

                        cfg.osi.crossConnects.put(
                                sOSPort,
                                new OpticalCrossConnectLink(sOSPort, dOSPort, Integer
                                        .valueOf(OpticalCrossConnectLink.OK)));
                        if (opr.isFDX) {
                            OpticalCrossConnectLink occld = new OpticalCrossConnectLink(dOSPort.getPear(),
                                    sOSPort.getPear(), Integer.valueOf(OpticalCrossConnectLink.OK));
                            cfg.osi.crossConnects.put(dOSPort.getPear(), occld);
                            cfg.osi.map.get(sOSPort.getPear()).opticalLinkID = opr.id;
                            cfg.osi.map.get(dOSPort.getPear()).opticalLinkID = opr.id;
                        }

                        opr.sentCMDs.put(localIDL, "ACK");

                    } else {
                        cfg.osi.rollback(transactionID);
                        opr.sentCMDs.put(localIDL, "NACK");
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " Got exception ", t);
                    cfg.osi.rollback(transactionID);
                }

                //                clearDelayMonitoringResult();
            } else {
                logger.log(Level.WARNING, "\n\n\nLocalPorts [ " + localPorts + " ] but localIDL == null!!! \n\n");
            }
        } else {
            if (localRemovePorts != null) {
                expectedSize--;
                cdl.countDown();
                OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
                if (ost != null) {

                    String sDelPorts[] = localRemovePorts.split(" - ");
                    OSPort sDelOSPort = new OSPort(sDelPorts[0], OSPort.INPUT_PORT);
                    OSPort dDelOSPort = new OSPort(sDelPorts[1], OSPort.OUTPUT_PORT);

                    if (opr.isFDX) {
                        boolean bstatus = true;
                        try {
                            ost.deleteFDXConn(localRemovePorts);
                        } catch (Throwable t) {
                            bstatus = false;
                            logger.log(Level.WARNING, " [ MLCopyAgent ] Got making deleting conn: [ "
                                    + localRemovePorts + "]", t);
                        }

                        if (bstatus) {

                            changePortState(sDelOSPort, OpticalLink.CONNECTED_FREE);
                            changePortState(dDelOSPort, OpticalLink.CONNECTED_FREE);
                            cfg.osi.map.get(sDelOSPort).opticalLinkID = null;
                            cfg.osi.map.get(dDelOSPort).opticalLinkID = null;
                            cfg.osi.crossConnects.remove(sDelOSPort);

                            changePortState(sDelOSPort.getPear(), OpticalLink.CONNECTED_FREE);
                            changePortState(dDelOSPort.getPear(), OpticalLink.CONNECTED_FREE);
                            cfg.osi.map.get(sDelOSPort.getPear()).opticalLinkID = null;
                            cfg.osi.map.get(dDelOSPort.getPear()).opticalLinkID = null;
                            cfg.osi.crossConnects.remove(dDelOSPort.getPear());

                            opr.sentCMDs.put(localIDL, "ACK");
                        } else {
                            opr.sentCMDs.put(localIDL, "NACK");
                        }
                    } else {
                        boolean bstatus = true;
                        try {
                            ost.deleteFDXConn(localRemovePorts);
                        } catch (Throwable t) {
                            bstatus = false;
                            logger.log(Level.WARNING, " [ MLCopyAgent ] Got making deleting conn: [ "
                                    + localRemovePorts + "]", t);
                        }
                        if (bstatus) {

                            changePortState(sDelOSPort, OpticalLink.CONNECTED_FREE);
                            changePortState(dDelOSPort, OpticalLink.CONNECTED_FREE);
                            cfg.osi.map.get(sDelOSPort).opticalLinkID = null;
                            cfg.osi.map.get(dDelOSPort).opticalLinkID = null;
                            cfg.osi.crossConnects.remove(sDelOSPort);
                            opr.sentCMDs.put(localIDL, "ACK");
                        } else {
                            opr.sentCMDs.put(localIDL, "NACK");
                        }
                    }
                }//if ost != null
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
        logger.log(Level.INFO, "\n\n ----> Waiting took " + (System.currentTimeMillis() - wTime) + " ms\n\n");

        System.out.println(" sent = " + opr.sentCMDs.toString());

        int NACKCmdsNo = getSentCMDsStatusNo("NACK", idsToWait, opr.sentCMDs);
        int SENTCmdsNo = getSentCMDsStatusNo("SENT", idsToWait, opr.sentCMDs);

        if ((NACKCmdsNo == 0) && (SENTCmdsNo == 0)) {
            logger.log(Level.INFO, "\n\nCNX EST:\n\n" + opr.links.toString());
            cfg.setNewConfiguration(cfg.osi, -1);
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
                    OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
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
                        logger.log(Level.WARNING, " [ MLCopyAgent ] Got making deleting conn: [ " + localPorts + "]", t);
                    }

                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "Del conn [ " + localPorts + " ] status = " + status);
                    }

                    if (bstatus) {

                        changePortState(sOSPort, OpticalLink.CONNECTED_FREE);
                        changePortState(dOSPort, OpticalLink.CONNECTED_FREE);
                        cfg.osi.map.get(sOSPort).opticalLinkID = null;
                        cfg.osi.map.get(dOSPort).opticalLinkID = null;
                        cfg.osi.crossConnects.remove(sOSPort);

                        if (opr.isFDX) {
                            changePortState(sOSPort.getPear(), OpticalLink.CONNECTED_FREE);
                            changePortState(dOSPort.getPear(), OpticalLink.CONNECTED_FREE);
                            cfg.osi.map.get(sOSPort.getPear()).opticalLinkID = null;
                            cfg.osi.map.get(dOSPort.getPear()).opticalLinkID = null;
                            cfg.osi.crossConnects.remove(dOSPort.getPear());
                        }

                        opr.sentCMDs.put(localIDL, "ACK");

                    } else {
                        opr.sentCMDs.put(localIDL, "NACK");
                    }

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
                cfg.setNewConfiguration(cfg.osi, -1);
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
            ignoreUntil = System.currentTimeMillis() + MLCopyAgent.IGNORE_MONITORING_CMAP_DELAY;
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

    private boolean addOCC(HashMap theLinks, String swName, String source, String dest, OpticalPathRequest opr,
            boolean ignoreIdle) {
        OpticalLink srcOL = null;
        OpticalLink dstOL = null;

        if (theLinks != null) {
            srcOL = (OpticalLink) theLinks.get(source + " - " + swName);
            dstOL = (OpticalLink) theLinks.get(swName + " - " + dest);
            if ((srcOL != null) && (dstOL != null)) {
                String agentAddress = getAgentAddress(swName);
                if (agentAddress == null) {
                    logger.log(Level.WARNING, " No such agent in my local config");
                    return false;
                }
                opr.links.put(agentAddress, srcOL.destinationPortName + " - " + dstOL.port.name);
            } else {
                logger.log(Level.WARNING, " Got a null OpticalLink and a null newSport for [ " + source + " -> "
                        + swName + " -> " + dest + " ] ");
                return false;
            }
        }

        //should not get here!!
        logger.log(Level.WARNING, "[ProtocolException] SHOULD NOT GET HERE [ " + source + " -> " + swName + " -> "
                + dest + " ] ");
        return false;
    }

    private String getAgentAddress(String name) {
        if (cfg.osi.name.equals(name)) {
            return getAddress();
        }
        for (Enumeration en = otherConfs.keys(); en.hasMoreElements();) {
            String agentAddress = (String) en.nextElement();
            SyncOpticalSwitchInfo osi = (SyncOpticalSwitchInfo) otherConfs.get(agentAddress);
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
            if ((i - 1) == 0) {
                OpticalLink srcOL = null;
                OpticalLink dstOL = null;

                SyncOpticalSwitchInfo oss = getOpticalSwitchForEndPoint(source);
                if ((i + 1) == (sOpticalPath.length - 1)) {
                    for (Object element : oss.map.keySet()) {
                        OSPort osp = (OSPort) element;
                        OpticalLink olink = oss.map.get(osp);
                        if ((srcOL == null) && olink.destination.equals(source)) {
                            srcOL = olink;
                            if (dstOL != null) {
                                break;
                            }
                        } else if ((dstOL == null) && olink.destination.equals(dest)) {
                            dstOL = olink;
                            if (srcOL != null) {
                                break;
                            }
                        }
                    }

                    if ((srcOL == null) && (dstOL == null)) {
                        logger.log(Level.WARNING, "[ProtocolException] Got null srcOL OR dstOL [ " + source + " -> "
                                + sOpticalPath[i] + " -> " + dest + " ] ");
                        return null;
                    }
                    opr.links.put(getAgentAddress(sOpticalPath[i]), srcOL.port.name + " - " + dstOL.port.name);
                } else {
                    dstOL = (OpticalLink) theLinks.get(sOpticalPath[i] + " - " + dest);
                    for (Object element : oss.map.keySet()) {
                        OSPort osp = (OSPort) element;
                        OpticalLink olink = oss.map.get(osp);
                        if ((srcOL == null) && olink.destination.equals(source)) {
                            srcOL = olink;
                            break;
                        }
                    }
                    if ((srcOL == null) && (dstOL == null)) {
                        logger.log(Level.WARNING, "[ProtocolException] Got null srcOL OR dstOL [ " + source + " -> "
                                + sOpticalPath[i] + " -> " + dest + " ] ");
                        return null;
                    }
                    opr.links.put(getAgentAddress(sOpticalPath[i]), srcOL.port.name + " - " + dstOL.port.name);
                }
            } else if ((i + 1) == (sOpticalPath.length - 1)) {
                OpticalLink srcOL = (OpticalLink) theLinks.get(source + " - " + sOpticalPath[i]);
                OpticalLink dstOL = null;
                SyncOpticalSwitchInfo oss = getOpticalSwitchForEndPoint(dest);

                for (Object element : oss.map.keySet()) {
                    OSPort osp = (OSPort) element;
                    OpticalLink olink = oss.map.get(osp);
                    if ((dstOL == null) && olink.destination.equals(dest)) {
                        dstOL = olink;
                        break;
                    }
                }
                if ((srcOL == null) && (dstOL == null)) {
                    logger.log(Level.WARNING, "[ProtocolException] Got null srcOL OR dstOL [ " + source + " -> "
                            + sOpticalPath[i] + " -> " + dest + " ] ");
                    return null;
                }
                opr.links.put(getAgentAddress(sOpticalPath[i]), srcOL.destinationPortName + " - " + dstOL.port.name);
            } else {
                addOCC(theLinks, sOpticalPath[i], source, dest, opr, ignoreIdle);
            }
        }
        return opr.links;
    }

    private void addOSIToGraph(SyncOpticalSwitchInfo osi, AdjacencyMatrixGraph adj, HashMap theLinks, boolean isFDX,
            String opticalPathID) {

        for (Object element : osi.map.keySet()) {

            OSPort key = (OSPort) element;

            //select only output ports
            if (key.type.shortValue() == OSPort.INPUT_PORT) {
                continue;
            }

            OpticalLink ol = osi.map.get(key);

            //NOT YET in the config 
            if (getOpticalSwitchForEndPoint(ol.destination) == null) {
                continue;
            }

            if (isSet(ol, OpticalLink.OTHER_CONN)) {
                continue;
            }

            if ((ol.opticalLinkID != null) && !ol.opticalLinkID.equals(opticalPathID)) {
                continue;
            }

            OpticalLink pol = getPearOl(ol);
            if (pol == null) {
                continue;
            }
            if ((opticalPathID == null) && !(isSet(pol, OpticalLink.FREE) || isSet(pol, OpticalLink.IDLE_LIGHT))) {
                continue;
            }

            if ((pol.opticalLinkID != null) && !pol.opticalLinkID.equals(opticalPathID)) {
                continue;
            }
            if (isSet(ol, OpticalLink.CONN_FAIL) || isSet(pol, OpticalLink.CONN_FAIL)) {
                continue;
            }

            OpticalLink pearOL = null;
            String theLinksKey = osi.name + " - " + ol.destination;

            if (isFDX) {
                pearOL = osi.map.get(key.getPear());
                if (pearOL == null) {
                    continue;
                }

                if (isSet(pearOL, OpticalLink.OTHER_CONN)) {
                    continue;
                }
                if ((pearOL.opticalLinkID != null) && !pearOL.opticalLinkID.equals(opticalPathID)) {
                    continue;
                }

                if (!pearOL.destination.equals(ol.destination)) {
                    continue;
                }

                pol = getPearOl(pearOL);
                if (pol == null) {
                    continue;
                }
                if ((opticalPathID == null) && !(isSet(pol, OpticalLink.FREE) || isSet(pol, OpticalLink.IDLE_LIGHT))) {
                    continue;
                }

                if ((pol.opticalLinkID != null) && !pol.opticalLinkID.equals(opticalPathID)) {
                    continue;
                }
                if (isSet(pearOL, OpticalLink.CONN_FAIL) || isSet(pol, OpticalLink.CONN_FAIL)) {
                    continue;
                }

                //should be both IN ( and out free )
                if (!isOLEndPoint(pearOL, OpticalLink.TYPE_SWITCH)) {
                    continue;
                }
            }

            OpticalLink existingOL = (OpticalLink) theLinks.get(theLinksKey);

            if (isOLEndPoint(ol, OpticalLink.TYPE_SWITCH)) {
                if (((opticalPathID == null) && isSet(ol, OpticalLink.FREE | OpticalLink.CONNECTED))
                        || (opticalPathID != null)) {
                    if (existingOL == null) {
                        adj.addEdge(osi.name, ol.destination, ol.quality.intValue());
                        theLinks.put(theLinksKey, ol);
                    }
                }
            }
        }
    }

    private boolean isSet(OpticalLink ol, int state) {
        return (ol.state.intValue() & state) == state;
    }

    private OpticalLink getPearOl(OpticalLink ol) {
        if (cfg.osi.name.equals(ol.destination)) {
            return cfg.osi.map.get(new OSPort(ol.destinationPortName,
                    (ol.port.type.shortValue() == OSPort.INPUT_PORT) ? OSPort.OUTPUT_PORT : OSPort.INPUT_PORT));
        }
        for (Enumeration enums = otherConfs.elements(); enums.hasMoreElements();) {
            SyncOpticalSwitchInfo osi = (SyncOpticalSwitchInfo) enums.nextElement();
            if (osi.name.equals(ol.destination)) {
                return osi.map.get(new OSPort(ol.destinationPortName,
                        (ol.port.type.shortValue() == OSPort.INPUT_PORT) ? OSPort.OUTPUT_PORT : OSPort.INPUT_PORT));
            }
        }
        return null;
    }

    private boolean isOLEndPoint(OpticalLink ol, short type) {
        return (ol.type.shortValue() == type);
    }

    private IGraph buildCurrentGraph(HashMap theLinks, boolean isFDX, String opticalPathID) {
        AdjacencyMatrixGraph adj = null;
        if (otherConfs.size() == 0) {// only local switch
            adj = new AdjacencyMatrixGraph(1);
        } else {
            adj = new AdjacencyMatrixGraph(otherConfs.size() + 1);
        }

        addVertexes(adj);

        // Local config
        SyncOpticalSwitchInfo osi = cfg.osi;
        addOSIToGraph(osi, adj, theLinks, isFDX, opticalPathID);

        // Remote config-s
        for (Enumeration enums = otherConfs.elements(); enums.hasMoreElements();) {
            osi = (SyncOpticalSwitchInfo) enums.nextElement();
            addOSIToGraph(osi, adj, theLinks, isFDX, opticalPathID);
        }
        return adj;
    }

    private void addVertexes(AdjacencyMatrixGraph adj) {
        for (Enumeration enums = otherConfs.elements(); enums.hasMoreElements();) {
            SyncOpticalSwitchInfo osi = (SyncOpticalSwitchInfo) enums.nextElement();
            adj.addVertex(osi.name);
        }
        adj.addVertex(cfg.osi.name);
    }

    private SyncOpticalSwitchInfo getOpticalSwitchForEndPoint(String endPoint) {

        if (endPoint == null) {
            return null;
        }

        // my conf
        SyncOpticalSwitchInfo osi = cfg.osi;
        for (Object key : osi.map.keySet()) {
            OpticalLink ol = osi.map.get(key);
            if ((ol == null) || (ol.destination == null)) {
                continue;
            }
            if (ol.destination.equals(endPoint)) {
                return osi;
            }
        }

        // other confs
        for (Enumeration enums = otherConfs.elements(); enums.hasMoreElements();) {
            osi = (SyncOpticalSwitchInfo) enums.nextElement();
            for (Object key : osi.map.keySet()) {
                OpticalLink ol = osi.map.get(key);
                if ((ol == null) || (ol.destination == null)) {
                    continue;
                }
                if (ol.destination.equals(endPoint)) {
                    return osi;
                }
            }
        }

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

    private void checkIfIsUP() {
        boolean oldStatus = cfg.osi.isAlive.get();
        try {
            OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
            cfg.osi.isAlive.set(ost.isConnected());
        } catch (Throwable t) {
            cfg.osi.isAlive.set(false);
        }

        if (oldStatus != cfg.osi.isAlive.get()) {
            logger.log(Level.INFO, "\n\nSwitch changed it's isAlive flag !!!" + cfg.osi.isAlive);
            cfg.setNewConfiguration(cfg.osi, -1);
        }
    }

    @Override
    public void doWork() {
        int del = 4;
        int counter = 0;
        int counterCheckPname = 0;
        try {
            if (agentComm == null) {
                logger.log(Level.WARNING, "AgentsComm e null :((");
            } else {
                //                AgentMessage amB = createCtrlMsg(getAndIncrementMSGID(), 1, 1, 5, null, null, "MLCopyGroup");
                AgentMessage amB = createCtrlMsg(getAndIncrementMSGID(), 1, 1, 5, null, null, agentInfo.agentGroup);
                agentComm.sendCtrlMsg(amB, "list");
            } // if - else
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got exc sending message to proxy", t);
        }

        while (hasToRun) {
            try {

                if (shouldCheckPortNames) {
                    if ((counterCheckPname % 10) == 0) {
                        counterCheckPname = 0;
                        checkPortName();
                        if (agentComm == null) {
                            logger.log(Level.WARNING, "AgentsComm e null :((");
                        } else {
                            //                            AgentMessage amB = createCtrlMsg(getAndIncrementMSGID(), 1, 1, 5, null, null, "MLCopyGroup");
                            AgentMessage amB = createCtrlMsg(getAndIncrementMSGID(), 1, 1, 5, null, null,
                                    agentInfo.agentGroup);
                            agentComm.sendCtrlMsg(amB, "list");
                        } // if - else
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
                logger.log(Level.WARNING, " MLCopyAgent - Got Exception main loop", t);
            }
        }

        logger.log(Level.WARNING, " MLCopyAgent hasToRun == " + hasToRun);
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
            AgentMessage amB = createMsg(getAndIncrementMSGID(), 1, 1, 3, "bcast:" + agentInfo.agentGroup,
                    agentInfo.agentGroup, SyncOpticalSwitchInfo.toOpticalSwitchInfo(cfg.osi));
            // agentComm.sendCtrlMsg(amB, "bcast");
            agentComm.sendMsg(amB);
            // } else {
            // if(logger.isLoggable(Level.FINEST)) {
            // logger.log(Level.FINEST, "Should have send myu conf to others ...
            // but I'm in a transfer opticalPaths.size ["+opticalPaths.size()+"]
            // receivedCMDs.size() ["+receivedCMDs.size()+"]");
            // }
            // }
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
                logger.log(Level.FINER, "\n\n {LEASE_SYSTEM} Renewing for " + getSwName(lease.getRemoteAgentAddress())
                        + "\nLease: " + lease + "\n");
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
            MLPathSession mlps = (MLPathSession) currentSessions.get(sessionID);
            if (mlps != null) {
                mlps.stopIt(); //can be called multiple times
                currentSessions.remove(sessionID);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exc removing session " + sessionID, t);
        }
    }

    private void sendConf(String dAddr) {
        AgentMessage amB = createMsg(getAndIncrementMSGID(), 1, 1, 5, dAddr, agentInfo.agentGroup, cfg.osi);
        sendAgentMessage(amB);
    }

    public Object expressResults() {
        Vector retV = new Vector();
        String ClusterName = "OS_" + cfg.osi.name;

        //TO be the same in all the Result-s returned from ML 
        long now = NTPDate.currentTimeMillis();
        try {
            ConcurrentHashMap olh = cfg.osi.map;
            for (Iterator it = olh.entrySet().iterator(); it.hasNext();) {

                Map.Entry entry = (Map.Entry) it.next();
                OSPort port = (OSPort) entry.getKey();
                OpticalLink ol = (OpticalLink) entry.getValue();

                Result r = new Result();
                r.ClusterName = ClusterName;
                r.time = now;
                r.NodeName = port.name + ":-" + ol.destination;
                if (ol.type.shortValue() == OpticalLink.TYPE_SWITCH) {
                    r.NodeName += ":" + ol.destinationPortName;
                }
                r.addSet("State", ol.state.doubleValue());
                retV.add(r);
            }//for

            if ((lasteResult_SENT + dt_eResultSent) < now) {
                eResult er = new eResult();
                er.time = now;
                er.ClusterName = ClusterName;
                er.NodeName = "localhost";
                er.FarmName = farm.name;
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(baos);
                    oos.writeObject(SyncOpticalSwitchInfo.toOpticalSwitchInfo(cfg.osi));
                    oos.flush();
                    er.addSet("OSIConfig", baos.toByteArray());
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "Sending eResult attr ... cfg.osi.size == " + baos.size());
                    }
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
        cfg.osi.changePortState(port, newState);
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

        logger.log(Level.INFO, "\n\n Remote MAKE CMD " + sPort + ":" + dPort + " isFDX" + isFDX);
        if ((sPort == null) || (dPort == null) || (cfg == null) || (cfg.osi == null)) {
            return "Not OK";
        }

        if ((sPort.length() == 0) || (dPort.length() == 0)) {
            return "Not OK";
        }

        Long transactionID = null;
        try {
            OSPort sOSPort = new OSPort(sPort, OSPort.INPUT_PORT);
            OSPort dOSPort = new OSPort(dPort, OSPort.OUTPUT_PORT);

            if (isFDX) {
                transactionID = cfg.osi.beginTransaction(
                        new OSPort[] { sOSPort, dOSPort, sOSPort.getPear(), dOSPort.getPear() }, new Integer[][] {
                                new Integer[] { OpticalLink.CONNECTED_FREE },
                                new Integer[] { OpticalLink.CONNECTED_FREE },
                                new Integer[] { OpticalLink.CONNECTED_FREE },
                                new Integer[] { OpticalLink.CONNECTED_FREE } }, new Integer[] {
                                OpticalLink.CONNECTED_OTHER_CONN, OpticalLink.CONNECTED_OTHER_CONN,
                                OpticalLink.CONNECTED_OTHER_CONN, OpticalLink.CONNECTED_OTHER_CONN, });
            } else {
                transactionID = cfg.osi.beginTransaction(new OSPort[] { sOSPort, dOSPort, }, new Integer[][] {
                        new Integer[] { OpticalLink.CONNECTED_FREE }, new Integer[] { OpticalLink.CONNECTED_FREE }, },
                        new Integer[] { OpticalLink.CONNECTED_OTHER_CONN, OpticalLink.CONNECTED_OTHER_CONN, });
            }//else

            boolean status = (transactionID != null);

            if (status) {
                OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
                try {
                    if (isFDX) {
                        ost.makeFDXConn(sPort + " - " + dPort);
                    } else {
                        ost.makeConn(sPort + " - " + dPort);
                    }
                } catch (Throwable t) {
                    status = false;
                    logger.log(Level.WARNING, " [ MLCopyAgent ] Got exception deleting conn: [ " + sPort + "-" + dPort
                            + "]", t);
                }

            }

            if (status) {
                cfg.osi.commit(transactionID);
                OpticalCrossConnectLink occls = new OpticalCrossConnectLink(sOSPort, dOSPort,
                        Integer.valueOf(OpticalCrossConnectLink.OK));
                cfg.osi.crossConnects.put(sOSPort, occls);

                if (isFDX) {
                    OpticalCrossConnectLink occld = new OpticalCrossConnectLink(dOSPort.getPear(), sOSPort.getPear(),
                            Integer.valueOf(OpticalCrossConnectLink.OK));
                    cfg.osi.crossConnects.put(dOSPort.getPear(), occld);
                }

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " Adding OpticalCrossConnect [" + sPort + " -> " + dPort
                            + "] cfg.osi.crossConnects = " + cfg.osi.crossConnects.toString());
                }

                cfg.setNewConfiguration(cfg.osi, -1);
            }

        } catch (Throwable t) {
            cfg.osi.rollback(transactionID);
            return "Not OK";
        }
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
        if ((sPort == null) || (dPort == null) || (cfg == null) || (cfg.osi == null)) {
            return "Not OK";
        }

        if ((sPort.length() == 0) || (dPort.length() == 0)) {
            return "Not OK";
        }
        try {
            OSPort sOSPort = new OSPort(sPort, OSPort.INPUT_PORT);
            OSPort dOSPort = new OSPort(dPort, OSPort.OUTPUT_PORT);

            OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
            if (ost == null) {
                return "Not OK";
            }

            setDelayMonitoringResult();

            boolean status = true;
            try {
                if (isFDX) {
                    ost.deleteFDXConn(sPort + " - " + dPort);
                } else {
                    ost.deleteConn(sPort + " - " + dPort);
                }
            } catch (Throwable t) {
                status = false;
                logger.log(Level.WARNING, " [ MLCopyAgent ] Got exception deleting conn: [ " + sPort + "-" + dPort
                        + "]", t);
            }

            if (status) {

                changePortState(sOSPort, OpticalLink.CONNECTED_FREE);
                changePortState(dOSPort, OpticalLink.CONNECTED_FREE);

                cfg.osi.map.get(sOSPort).opticalLinkID = null;
                cfg.osi.map.get(dOSPort).opticalLinkID = null;

                cfg.osi.crossConnects.remove(sOSPort);

                if (isFDX) {
                    changePortState(sOSPort.getPear(), OpticalLink.CONNECTED_FREE);
                    changePortState(dOSPort.getPear(), OpticalLink.CONNECTED_FREE);

                    cfg.osi.map.get(sOSPort.getPear()).opticalLinkID = null;
                    cfg.osi.map.get(dOSPort.getPear()).opticalLinkID = null;

                    cfg.osi.crossConnects.remove(dOSPort.getPear());
                }

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " Removing OpticalCrossConnect [" + sPort + " -> " + dPort
                            + "] cfg.osi.crossConnects = " + cfg.osi.crossConnects.toString());
                }

                cfg.setNewConfiguration(cfg.osi, -1);
            }

            //            clearDelayMonitoringResult();

        } catch (Throwable t) {
            return "Not OK";
        }
        return "OK";
    }

    private void checkCrossConns(Result r) {
        try {

            if (ignoreMonitoringResult()) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, " [ HANDLED ] checkCrossConns ignoring delayed Result " + r);
                }
                return;
            }

            boolean changedConf = false;

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, " checkCrossConns processing " + r.toString());
                logger.log(Level.FINEST, "cfg.osi.crossConnects = " + cfg.osi.crossConnects.toString());
            }
            boolean found = false;

            String ports[] = r.NodeName.split(" - ");

            OSPort sOSPort = new OSPort(ports[0], OSPort.INPUT_PORT);
            OSPort dOSPort = new OSPort(ports[1], OSPort.OUTPUT_PORT);

            //it must be write !!
            monitoringResultsWriteLock.lock();
            try {//keep as small as possible the WR lock
                for (Object element : cfg.osi.crossConnects.entrySet()) {
                    Map.Entry entry = (Map.Entry) element;
                    OpticalCrossConnectLink occl = (OpticalCrossConnectLink) entry.getValue();

                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, " checkCrossConns - Processing ...  " + occl.toString());
                    }
                    if (occl.sPort.equals(sOSPort) && occl.dPort.equals(dOSPort)) {
                        found = true;
                        if (r.param[0] == OpticalCrossConnectLink.REMOVED) {
                            changePortState(occl.sPort, OpticalLink.CONNECTED_FREE);
                            changePortState(occl.dPort, OpticalLink.CONNECTED_FREE);

                            cfg.osi.map.get(sOSPort).opticalLinkID = null;
                            cfg.osi.map.get(dOSPort).opticalLinkID = null;

                            cfg.osi.crossConnects.remove(sOSPort);

                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, " Removing OpticalCrossConnect " + occl.toString()
                                        + " cfg.osi.crossConnects = " + cfg.osi.crossConnects.toString());
                            }
                            changedConf = true;
                        } else {
                            if (occl.status.intValue() != (int) r.param[0]) {
                                if (logger.isLoggable(Level.FINEST)) {
                                    logger.log(Level.FINEST,
                                            " Changing state for OpticalCrossConnect " + occl.toString());
                                }
                                occl.status = Integer.valueOf((int) r.param[0]);
                                changedConf = true;
                            }
                        }
                    }
                }//for
            } catch (Throwable t) {
                logger.log(Level.WARNING, " check cross conns exc", t);
            } finally {
                monitoringResultsWriteLock.unlock();
            }

            if (!found && ((int) r.param[0] != OpticalCrossConnectLink.REMOVED)) {

                changePortState(sOSPort, OpticalLink.CONNECTED_OTHER_CONN);
                changePortState(dOSPort, OpticalLink.CONNECTED_OTHER_CONN);

                OpticalCrossConnectLink occl1 = new OpticalCrossConnectLink(sOSPort, dOSPort,
                        Integer.valueOf(OpticalCrossConnectLink.OK));
                cfg.osi.crossConnects.put(sOSPort, occl1);
                changedConf = true;
            }

            if (changedConf) {
                cfg.setNewConfiguration(cfg.osi, 700);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exc checkCrossConns", t);
        }
    }

    private void checkPortPower(Result r) {

        if (ignoreMonitoringResult()) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, " [ HANDLED ] checkPortPower ignoring delayed Result " + r);
            }
            return;
        }

        HashMap opticalLinksDown = new HashMap();
        try {
            monitoringResultsWriteLock.lock();
            if ((r.ClusterName != null) && (r.ClusterName.indexOf("Ports") != -1) && (r.param != null)
                    && (r.param_name != null)) {
                for (int i = 0; i < r.param_name.length; i++) {
                    if (r.NodeName.indexOf("_") != -1) {
                        String splitedNames[] = r.NodeName.split("_");
                        String pName = splitedNames[0];
                        short type = (splitedNames[1].equals("In")) ? OSPort.INPUT_PORT : OSPort.OUTPUT_PORT;
                        OSPort osp = new OSPort(pName, type);
                        OpticalLink ol = cfg.osi.map.get(osp);
                        if (ol != null) {
                            ol.port.power = Double.valueOf(r.param[i]);
                            if (r.param[i] > POWER_THRESHOLD) {//Light
                                if (isSet(ol, OpticalLink.FREE | OpticalLink.CONNECTED)) {
                                    ol.state = Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.IDLE_LIGHT);
                                }
                            } else if (r.param[i] < POWER_THRESHOLD) {//NO Light
                                if (isSet(ol, OpticalLink.IDLE_LIGHT | OpticalLink.CONNECTED)) {
                                    ol.state = Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.FREE);
                                }
                                if (isSet(ol, OpticalLink.OTHER_CONN)) {
                                    // changePortState(splitedNames[0], OpticalLink.OTHER_CONN | OpticalLink.CONN_FAIL);
                                    logger.log(Level.WARNING, "\n\n Changing to fail !!!! for port [" + splitedNames[0]
                                            + "]\n\n");
                                }

                                if ((type == OSPort.INPUT_PORT) && (ol.opticalLinkID != null)) {
                                    if (!opticalLinksDown.containsKey(ol.opticalLinkID)) {
                                        ol.state = Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.CONN_FAIL);
                                        opticalLinksDown.put(ol.opticalLinkID, ol);
                                    } else {
                                        //This should not happen
                                        logger.log(Level.SEVERE,
                                                " [ ProtocolException ] !!! There are more OpticalLinks in different ports associated with the same opticalLinkID [ "
                                                        + ol.opticalLinkID + " ]");
                                    }
                                }//if
                            }//else id - NO Light
                        }//if (ol != null)
                    }//if
                }//for
            }//if

            if (opticalLinksDown.size() > 0) {//ML_LIKS down! Reroute!
                setDelayMonitoringResult();
                handleOpticalLinksDown(opticalLinksDown);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exc checkPortPower [" + r.toString() + "]", t);
        } finally {
            monitoringResultsWriteLock.unlock();
        }
    }

    private void handleOpticalLinksDown(final HashMap opticalLinksDown) {
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

                    //TODO
                    //when it will work I will take it out
                    if (cfg.osi.type.shortValue() != OpticalSwitchInfo.CALIENT) {
                        checkPortPower(r);
                    }
                } else if (r.Module.indexOf("CrossConns") != -1) {
                    checkCrossConns(r);
                }
            }
        }

    }

    private void makeCConn(OpticalCrossConnectLink occl) throws Exception {
        OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
        boolean status = false;
        try {
            ost.makeFDXConn(occl.sPort + " - " + occl.dPort);
        } catch (Throwable t) {
            status = false;
            logger.log(Level.WARNING, " [ MLCopyAgent ] Got exception making conn: [ " + occl.sPort + "-" + occl.dPort
                    + "]", t);
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
        OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
        boolean status = true;
        try {
            ost.deleteFDXConn(occl.sPort + " - " + occl.dPort);
        } catch (Throwable t) {
            status = false;
            logger.log(Level.WARNING, " [ MLCopyAgent ] Got exception deleting conn: [ " + occl.sPort + "-"
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
    private Vector computeConfDiff(SyncOpticalSwitchInfo oldOSI) {
        Vector retV = new Vector();
        String ClusterName = "OS_" + cfg.osi.name;

        try {
            if (oldOSI != null) {
                ConcurrentHashMap olh = oldOSI.map;
                ConcurrentHashMap new_olh = cfg.osi.map;

                // looking for OpticalLinks
                for (Iterator it = olh.keySet().iterator(); it.hasNext();) {
                    try {
                        boolean shouldSend = false;
                        OSPort port = (OSPort) it.next();
                        OpticalLink ol = (OpticalLink) olh.get(port);
                        OpticalLink new_ol = (OpticalLink) new_olh.get(port);
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, "Comparing new:\n " + new_ol + "\n with old:\n" + ol);
                        }
                        if (new_ol == null) {
                            shouldSend = true;
                        } else if (!ol.equals(new_ol)) {
                            shouldSend = true;
                        } else if ((new_ol.state.intValue() & OpticalLink.DISCONNECTED) == OpticalLink.DISCONNECTED) {
                            shouldSend = true;
                        }

                        if (shouldSend) {
                            Result r = new Result();
                            r.ClusterName = ClusterName;
                            r.time = NTPDate.currentTimeMillis();
                            ;
                            r.NodeName = port.name + ":-" + ol.destination;
                            if (ol.type.shortValue() == OpticalLink.TYPE_SWITCH) {
                                r.NodeName += ":" + ol.destinationPortName;
                            }
                            r.addSet("State", OpticalLink.DISCONNECTED);
                            retV.add(r);
                        }

                    } catch (Throwable t1) {
                        t1.printStackTrace();
                    }
                }

                // looking for OpticalCrossConnectLinks
                ConcurrentHashMap oldCC = oldOSI.crossConnects;
                ConcurrentHashMap newCC = cfg.osi.crossConnects;

                // DELETE the old ones that are no longer in the new conf
                for (Iterator it = oldCC.keySet().iterator(); it.hasNext();) {
                    OSPort osp = (OSPort) it.next();
                    OpticalCrossConnectLink occl = (OpticalCrossConnectLink) oldCC.get(osp);
                    if (newCC.containsValue(occl)) {
                        continue;
                    }
                    // must be deleted
                    deleteCConn(occl);
                }

            }

            ConcurrentHashMap newCC = cfg.osi.crossConnects;
            ConcurrentHashMap oldCC = (oldOSI == null) ? null : oldOSI.crossConnects;

            // ADD the new ones
            for (Iterator it = newCC.keySet().iterator(); it.hasNext();) {
                OSPort osp = (OSPort) it.next();
                OpticalCrossConnectLink occl = (OpticalCrossConnectLink) newCC.get(osp);
                if ((oldCC != null) && oldCC.containsValue(occl)) {
                    continue;
                }
                // must be added
                makeCConn(occl);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got Exception ex", t);
        }

        return retV;
    }

    private void publishAttrs() {
        try {
            Hashtable ht = new Hashtable();
            ht.put("xdrTCPPort", Integer.valueOf(xdrTCPPort));
            if ((cfg != null) && (cfg.osi != null)) {
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(baos);
                    oos.writeObject(SyncOpticalSwitchInfo.toOpticalSwitchInfo(cfg.osi));
                    oos.flush();
                    ht.put("OSIConfig", baos.toByteArray());
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "Publishing attr ... cfg.osi.size == " + baos.size());
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Cannot serialize cfg.osi", t);
                }
            }
            GMLEPublisher.getInstance().publishNow(agentInfo.agentName, ht);
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
        StringBuilder sb = new StringBuilder();
        if (logger.isLoggable(Level.FINE)) {
            sb.append("MLCopyAgent update conf...\n");
        }

        if ((cfg != null) && (o != null) && o.equals(cfg)) {// conf was changed
            if ((arg != null) && (arg instanceof SyncOpticalSwitchInfo)) {
                if (logger.isLoggable(Level.FINE)) {
                    sb.append("MLCopyAgent update conf...entering computeConfDiff()\n");
                }

                Vector v = computeConfDiff((SyncOpticalSwitchInfo) arg);
                eResult er = new eResult();
                er.time = NTPDate.currentTimeMillis();
                er.ClusterName = "OS_" + cfg.osi.name;
                er.NodeName = "localhost";
                er.FarmName = farm.name;

                //keep the isAlive flag from the previous state
                cfg.osi.isAlive.set(((SyncOpticalSwitchInfo) arg).isAlive.get());

                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(baos);
                    oos.writeObject(SyncOpticalSwitchInfo.toOpticalSwitchInfo(cfg.osi));
                    oos.flush();
                    er.addSet("OSIConfig", baos.toByteArray());
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "Sending eResult attr ... cfg.osi.size == " + baos.size());
                    }
                    v.add(er);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Cannot serialize cfg.osi", t);
                }
                if ((v != null) && (v.size() > 0)) {
                    if (logger.isLoggable(Level.FINE)) {
                        sb.append("MLCopyAgent update conf...entering delivering ").append(v.size())
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
            sb.append("MLCopyAgent End update Conf\n\n");
            logger.log(Level.FINE, sb.toString());
        }
    }

    String getSwName(String agentAddress) {
        if (agentAddress == null) {
            return "[AgentAddress is null] -> No Such Address";
        }

        if (agentAddress.equals(agentInfo.agentAddr)) {
            return agentAddress + " @ " + cfg.osi.name;
        }

        SyncOpticalSwitchInfo osi = (SyncOpticalSwitchInfo) otherConfs.get(agentAddress);
        if (osi == null) {
            return agentAddress + " @ No such address";
        }

        return agentAddress + " @ " + osi.name;
    }

    public String changeNPPort(String eqptID, String ip, String mask, String gw) {
        try {
            OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
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
            OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
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
            OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
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
            OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
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
            OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
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
            OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
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
            OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
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
            OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
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
            OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
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
            OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
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
            OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
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
            OSTelnet ost = OSTelnetFactory.getControlInstance(cfg.osi.type.shortValue());
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

} // class MLCopyAgent
