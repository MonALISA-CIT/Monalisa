/*
 * Created on Mar 24, 2010
 */
package lia.net.topology.agents;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Agents.OpticalPath.Admin.OSAdminInterface;
import lia.Monitor.JiniSerFarmMon.GMLEPublisher;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Gresult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.net.topology.DeviceType;
import lia.net.topology.GenericEntity;
import lia.net.topology.Link;
import lia.net.topology.MLLinksMsg;
import lia.net.topology.Port.PortType;
import lia.net.topology.TopoMsg;
import lia.net.topology.agents.admin.AFOXAdminImpl;
import lia.net.topology.agents.conf.AFOXRawPort;
import lia.net.topology.agents.conf.MLAFOXConfig;
import lia.net.topology.agents.conf.OutgoingLink;
import lia.net.topology.agents.conf.RawConfigInterface;
import lia.net.topology.opticalswitch.AFOXOSPort;
import lia.net.topology.opticalswitch.OSPort;
import lia.net.topology.opticalswitch.OpticalSwitch;
import lia.util.Utils;
import lia.util.exporters.RMIRangePortExporter;
import lia.util.ntp.NTPDate;

import com.telescent.afox.AFOXConnection;
import com.telescent.afox.global.SMConnectedTo;
import com.telescent.afox.global.SM_InOrOut_CurAndPending;
import com.telescent.afox.msg.AFOXFullUpdateRequestMsg;
import com.telescent.afox.msg.AFOXFullUpdateReturnMsg;
import com.telescent.afox.msg.AFOXGetInputRFIDMsg;
import com.telescent.afox.msg.AFOXGetInputRFIDRetMsg;

/**
 * 
 * @author ramiro 
 */
public class AFOXAgent extends TopoAgent<MLAFOXConfig, AFOXRawPort> implements OSAdminInterface {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(AFOXAgent.class.getName());

    /**
     * 
     */
    private static final long serialVersionUID = -6331036966100212467L;

    private static final AtomicReference<AFOXFullUpdateReturnMsg> fullState = new AtomicReference<AFOXFullUpdateReturnMsg>();

    private static final Map<AFOXRawPort, AFOXGetInputRFIDRetMsg> rfidState = new ConcurrentHashMap<AFOXRawPort, AFOXGetInputRFIDRetMsg>();

    private static final String AFOX_HOST = AppConfig.getProperty("AFOX_HOST");

    private static final int AFOX_PORT = AppConfig.geti("AFOX_PORT", -1);

    private static final boolean shouldStartAdminInterface = AppConfig.getb(
            "lia.net.topology.agents.AFOXAgent.startAdminInterface", true);

    private volatile OpticalSwitch os;

    private final transient OSAdminInterface localAdminInterface;

    private final AtomicReference<OpticalSwitch> osStateRef = new AtomicReference<OpticalSwitch>();

    private final AtomicBoolean shouldReloadConfig = new AtomicBoolean(false);

    private final class AFOXStateFetcher implements Runnable {

        @Override
        public void run() {
            try {
                updateState();
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ AFOXStateFetcher ] [ updateState ] get exception ", t);
            }

            try {
                publishAttrs();
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ AFOXStateFetcher ] get exception ", t);
            }

            try {
                if (!SIMULATED) {
                    updateRFID();
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ AFOXStateFetcher ] [ updateRFID ] get exception ", t);
            }
        }
    }

    private void invalidateOS() {
        GenericEntity.clearIDFromCache(os.id());
        for (OSPort cp : this.os.getPortSet()) {
            if (cp.outgoingLink() != null) {
                GenericEntity.clearIDFromCache(cp.outgoingLink().id());
            }
            GenericEntity.clearIDFromCache(cp.id());
        }
        for (Link link : this.os.getCrossConnects()) {
            GenericEntity.clearIDFromCache(link.id());
            if (link.sourcePort() != null) {
                GenericEntity.clearIDFromCache(link.sourcePort().id());
            }
            if (link.destinationPort() != null) {
                GenericEntity.clearIDFromCache(link.destinationPort().id());
            }
        }
    }

    private void updateState() {
        try {
            if (os != null) {
                invalidateOS();
                os = null;
            }
            final MLAFOXConfig mlAFOXConfig = this.config;
            if (SIMULATED) {
                System.out.println(mlAFOXConfig.toString());
            }
            os = (SIMULATED) ? MLTranslation.fromAfoxConfig(mlAFOXConfig) : MLTranslation.fromConfigAndMsg(
                    fullState.get(), rfidState, mlAFOXConfig);
            logger.log(Level.INFO, " AfoxAgent: " + os + " reloaded config: \n X-Conns: " + os.getCrossConnSet());
        } catch (Throwable t) {
            logger.log(Level.WARNING, " AfoxAgent got exception update state", t);
        }

    }

    private final void updateStateFromSwitch() throws UnknownHostException, IOException {
        if (SIMULATED) {
            return;
        }
        AFOXConnection afoxConn = new AFOXConnection(AFOX_HOST, AFOX_PORT);
        final AFOXFullUpdateRequestMsg reqMsg = new AFOXFullUpdateRequestMsg("", 0, 0);
        final byte[] respBMsg = afoxConn.sendAndReceive(reqMsg.ToFlatSer(), 5, TimeUnit.SECONDS);
        final AFOXFullUpdateReturnMsg respMsg = AFOXFullUpdateReturnMsg.DeSerialize(respBMsg);
        fullState.set(respMsg);
    }

    private final void updateRFID() throws UnknownHostException, IOException {
        final MLAFOXConfig mlAFOXConfig = this.config;
        final List<AFOXRawPort> configPorts = mlAFOXConfig.hostPorts();
        AFOXConnection afoxConn = new AFOXConnection(AFOX_HOST, AFOX_PORT);
        for (AFOXRawPort cp : configPorts) {
            final AFOXGetInputRFIDMsg reqMsg = new AFOXGetInputRFIDMsg("", cp.portRow, cp.portColumn);
            final byte[] respBMsg = afoxConn.sendAndReceive(reqMsg.ToFlatSer(), 5, TimeUnit.SECONDS);
            final AFOXGetInputRFIDRetMsg respMsg = AFOXGetInputRFIDRetMsg.DeSerialize(respBMsg);
            rfidState.put(cp, respMsg);
            // if (cp.portType == PortType.OUTPUT_PORT) {
            // logger.log(Level.INFO, " RFID for output: " + respMsg.CustomerCode);
            // }
        }
    }

    private final void publishAttrs() {
        try {
            HashMap<String, String> hm = new HashMap<String, String>();

            hm.put("TOPO_CONFIG_CLUSTER", TOPO_CONFIG_CLUSTER);

            GMLEPublisher.getInstance().publishNow(hm);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got exception publishing attrs", t);
        }

    }

    private final void populateCrossConnects(OpticalSwitch os) {
        try {
            final OpticalSwitch osState = osStateRef.get();
            if (osState == null) {
                logger.log(Level.WARNING, " OS internal state (still) null ");
                return;
            }
            GenericEntity.clearIDFromCache(os.getCrossConnects());
            os.clearCross();
            if (SIMULATED) {
                os.addCrossConnSet(osState.getCrossConnSet());
                return;
            }
            AFOXFullUpdateReturnMsg cAFOXState = fullState.get();
            final Set<OSPort> osPorts = os.getPortSet();
            for (OSPort port : osPorts) {
                AFOXOSPort mlPort = (AFOXOSPort) port;
                SM_InOrOut_CurAndPending afxPort = (mlPort.type() == PortType.INPUT_PORT) ? cAFOXState.SMCurrentIns[mlPort
                        .getRow() - 1][mlPort.getColumn() - 1] : cAFOXState.SMCurrentOuts[mlPort.getRow() - 1][mlPort
                        .getColumn() - 1];
                if (afxPort.Current != null) {
                    SMConnectedTo afxConnTo = afxPort.Current;
                    int sRow = mlPort.getRow();
                    int cRow = mlPort.getColumn();

                    int dRow = afxConnTo.ConnectedToRow;
                    int dCol = afxConnTo.ConnectedToCol;

                    PortType dPType = (mlPort.type() == PortType.INPUT_PORT) ? PortType.OUTPUT_PORT
                            : PortType.INPUT_PORT;

                    boolean bFound = false;
                    OSPort foundPort = null;
                    for (OSPort dMLPort : osPorts) {
                        AFOXOSPort dAfoxPort = (AFOXOSPort) dMLPort;
                        if ((dAfoxPort.type() == dPType) && (dAfoxPort.getColumn() == dCol)
                                && (dAfoxPort.getRow() == dRow)) {
                            bFound = true;
                            foundPort = dMLPort;
                            break;
                        }
                    }

                    if (!bFound) {
                        logger.log(Level.WARNING, " Unable to det port. Not in my map ?? for: " + sRow + "," + cRow
                                + " _ " + mlPort.type() + "  ----> " + dRow + ", " + dCol);
                    } else {
                        Link[] ccross = os.getCrossConnects();
                        boolean bFoundLink = false;
                        for (Link l : ccross) {
                            if (l.sourcePort().equals(port) && l.destinationPort().equals(foundPort)) {
                                bFoundLink = true;
                                break;
                            }
                        }
                        if (!bFoundLink) {
                            os.addCrossConn(new Link(port, foundPort));
                        }
                    }
                }
            }

        } catch (Throwable t) {
            logger.log(Level.WARNING, " populateCrossConnects AFOX agent exc in monitoring task ", t);
        }
        logger.log(Level.INFO, " \n\n AFTERRRR!!! " + Arrays.toString(os.getCrossConnects()));
    }

    private static final class ResultMonitorTask implements Runnable {

        @Override
        public void run() {
            // nothing for the moment
        }
    }

    private final class ConfigPublisherTask implements Runnable {

        @Override
        public void run() {
            try {
                if (shouldReloadConfig.compareAndSet(true, false)) {
                    updateStateFromSwitch();
                }
                deliverResults2ML(expressResults());
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Unable to deliver results", t);
            }
        }
    }

    private MLLinksMsg getLinks() {
        final MLAFOXConfig mlAFOXConfig = this.config;
        final Map<AFOXRawPort, OutgoingLink> confMap = mlAFOXConfig.outgoingLinks();
        if (confMap == null) {
            final Map<UUID, OutgoingLink> linksMap = Collections.emptyMap();
            return new MLLinksMsg(os.id(), DeviceType.AFOX, linksMap);
        }
        Map<UUID, OutgoingLink> pMap = new HashMap<UUID, OutgoingLink>(confMap.size());
        Set<OSPort> allPorts = os.getPortSet();
        for (final Map.Entry<AFOXRawPort, OutgoingLink> entry : confMap.entrySet()) {
            final AFOXRawPort rawPort = entry.getKey();
            for (final OSPort osPort : allPorts) {
                AFOXRawPort cRawPort = ((AFOXOSPort) osPort).rawPort();
                if ((cRawPort != null) && cRawPort.equals(rawPort)) {
                    pMap.put(osPort.id(), entry.getValue());
                    break;
                }
            }
        }

        logger.log(Level.INFO, " Sending cross links: " + pMap);
        return new MLLinksMsg(os.id(), DeviceType.AFOX, pMap);
    }

    private final Object expressResults() {
        final OpticalSwitch os = this.os;
        if (os != null) {

            populateCrossConnects(os);
            final Vector<Object> retV = new Vector<Object>();
            eResult er = new eResult();
            er.time = NTPDate.currentTimeMillis();
            er.ClusterName = TOPO_CONFIG_CLUSTER;
            er.NodeName = "localhost";
            er.FarmName = getFarmName();
            try {
                byte[] buff = Utils.writeObject(new TopoMsg(agentID, serviceID, TopoMsg.Type.AFOX_CONFIG, os));
                er.addSet("TOPO_CONFIG", buff);
                logger.log(Level.INFO, "Sending eResult  [ " + buff.length + " ]: \n" + er.toString() + "\n\n");
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Cannot serialize OS state", t);
            }

            try {
                final MLLinksMsg linksMsg = getLinks();
                byte[] buff = Utils.writeObject(new TopoMsg(agentID, serviceID, TopoMsg.Type.ML_LINKS, linksMsg));
                er.addSet("LINKS_CONFIG", buff);
                logger.log(Level.INFO, "Sending eResult [ " + buff.length + " ]: \n" + er.toString() + "\n\n");
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Cannot serialize OS state", t);
            }

            retV.add(er);
            logger.log(Level.INFO, "Sending Vector [ " + retV.size() + " ]: \n" + retV + "\n\n");
            return retV;
        }
        logger.log(Level.INFO, " [ ] OS still null.");
        return null;
    }

    public AFOXAgent(String agentName, String agentGroup, String farmID) {
        super(agentName, agentGroup, farmID, getLocalConfig());
        OSAdminInterface tmpAdmin = null;
        if (shouldStartAdminInterface) {
            try {
                tmpAdmin = new AFOXAdminImpl(this);
                logger.log(Level.INFO, "Afox Admin Interface started on [ " + RMIRangePortExporter.getPort(tmpAdmin)
                        + " ]");
            } catch (Throwable t) {
                tmpAdmin = null;
                logger.log(Level.WARNING, "Cannot start the admin interface", t);
            }
        }

        localAdminInterface = tmpAdmin;

        try {
            if (!SIMULATED) {
                updateStateFromSwitch();
                updateRFID();
            }
            updateState();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Unable to load AFOX agent. Cause:", t);
            throw new InstantiationError("Unable to load AFOX agent");
        }
    }

    private static final MLAFOXConfig getLocalConfig() {
        MLAFOXConfig mlAFOXConfig = null;
        try {
            mlAFOXConfig = new MLAFOXConfig(AppConfig.getProperty("AFOX_CONFIG_FILE"));
            return mlAFOXConfig;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Unable to load AFOX agent. Cause:", t);
            throw new InstantiationError("Unable to load AFOX agent");
        }
    }

    /**
     * @param r
     */
    @Override
    public void addNewResult(Object r) {
        // TODO Auto-generated method stub

    }

    private void deliverResults2ML(Object o) {

        if (STANDALONE) {
            return;
        }
        if (o != null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ AFOXAgent ] delivering results to ML! " + o);
            }
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
    }

    private void notifyCache(Vector<Object> storeResults) {
        cache.notifyInternalResults(storeResults);
    }

    @Override
    public void doWork() {
        logger.log(Level.INFO, " AFOXAgent STARTED sus pe gard !");
        // TODO Auto-generated method stub
        monitorExec.scheduleWithFixedDelay(new ResultMonitorTask(), 10, 10, TimeUnit.SECONDS);
        monitorExec.scheduleWithFixedDelay(new ConfigPublisherTask(), 15, 10, TimeUnit.SECONDS);
        monitorExec.scheduleWithFixedDelay(new AFOXStateFetcher(), 15, 40, TimeUnit.SECONDS);
        try {
            for (;;) {
                try {
                    Thread.sleep(10 * 1000);
                } catch (Throwable t1) {
                    logger.log(Level.WARNING, " Exception in loop ", t1);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Exception OUTSIDE loop. AGENT LOOP WILL FINISH NOW!! ", t);
        }
    }

    /**
     * @param msg
     */
    @Override
    public void processMsg(Object msg) {
        // TODO Auto-generated method stub

    }

    public static void main(String[] args) {
        new AFOXAgent("Test", "Test", UUID.randomUUID().toString()).doWork();
    }

    @Override
    public void notifyConfig(RawConfigInterface<AFOXRawPort> oldConfig, RawConfigInterface<AFOXRawPort> newConfig) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n old config \n\n ").append(oldConfig).append("\n\n new config \n\n").append(newConfig)
                .append("\n\n");
        shouldReloadConfig.compareAndSet(false, true);
        logger.log(Level.INFO, sb.toString());
    }

    /*
     * Just to avoid FindBugs complains...
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        // our "pseudo-constructor"
        in.defaultReadObject();
    }

    /**
     * @param portName 
     * @param newSignalType  
     * @throws RemoteException 
     */
    @Override
    public String changePortState(String portName, String newSignalType) throws RemoteException {
        return "Not implemented yet!";
    }

    /**
     * @param sPort 
     * @param dPort  
     * @param connParams  
     * @param fullDuplex  
     * @throws RemoteException 
     */
    @Override
    public String connectPorts(String sPort, String dPort, String connParams, boolean fullDuplex)
            throws RemoteException {
        logger.log(Level.INFO, "[ AFOXAgent ]  [ connectPorts ] sPort=" + sPort + ", dPort=" + dPort + ", connParams='"
                + connParams + "', fdx: " + fullDuplex);
        if ((sPort == null) || (dPort == null)) {
            logger.log(Level.WARNING, " sPort or dPort null");
            return "OK";
        }
        if ((sPort.trim().length() == 0) || (dPort.trim().length() == 0)) {
            logger.log(Level.WARNING, " sPort or dPort are zero length");
            return "OK";
        }

        final MLAFOXConfig mlAFOXConfig = this.config;
        final ConcurrentMap<AFOXRawPort, AFOXRawPort> sConnsMap = mlAFOXConfig.crossConnMap();

        final AFOXRawPort sRawPort = AFOXRawPort.valueOf(sPort + ":INPUT_PORT");
        final AFOXRawPort dRawPort = AFOXRawPort.valueOf(dPort + ":OUTPUT_PORT");
        final boolean bAdded = sConnsMap.putIfAbsent(sRawPort, dRawPort) == null;
        logger.log(Level.INFO, "[ AFOXAgent ] [ connectPorts ] connected first pair: " + sRawPort + " -> " + dRawPort);
        if (fullDuplex && bAdded) {
            final AFOXRawPort sRawFDXPort = AFOXRawPort.valueOf(dPort + ":INPUT_PORT");
            final AFOXRawPort dRawFDXPort = AFOXRawPort.valueOf(sPort + ":OUTPUT_PORT");
            final boolean bAddedFDX = sConnsMap.put(sRawFDXPort, dRawFDXPort) == null;
            if (!bAddedFDX) {
                logger.log(Level.WARNING, "[ AFOXAgent ] [ connectPorts ] Unable to add second FDX conn " + sRawFDXPort
                        + " -> " + dRawFDXPort + " ... removing first conn");
                final boolean bRemove = sConnsMap.remove(sRawPort, dRawPort);
                if (!bRemove) {
                    logger.log(Level.WARNING, "[ AFOXAgent ] [ connectPorts ] Unable to DELETE FIRST FDX conn "
                            + sRawPort + " -> " + sRawPort + " ... removing first conn");
                }
            } else {
                logger.log(Level.INFO, "[ AFOXAgent ] [ connectPorts ] connected second pair: " + sRawFDXPort + " -> "
                        + dRawFDXPort);
            }
        }
        updateState();
        shouldReloadConfig.set(true);
        //notify the change in a "forced" asynchronous manner
        monitorExec.submit(new ConfigPublisherTask());
        return "OK";
    }

    /**
     * @param sPort 
     * @param dPort  
     * @param connParams  
     * @param fullDuplex  
     * @throws RemoteException 
     */
    @Override
    public String disconnectPorts(String sPort, String dPort, String connParams, boolean fullDuplex)
            throws RemoteException {
        logger.log(Level.INFO, " [ AFOXAgent ]  disconnectPorts sPort=" + sPort + ", dPort=" + dPort + ", connParams='"
                + connParams + "', fdx: " + fullDuplex);

        logger.log(Level.INFO, " [ AFOXAgent ]  connectPorts sPort=" + sPort + ", dPort=" + dPort + ", connParams='"
                + connParams + "', fdx: " + fullDuplex);
        if ((sPort == null) || (dPort == null)) {
            logger.log(Level.WARNING, " sPort or dPort null");
            return "OK";
        }
        if ((sPort.trim().length() == 0) || (dPort.trim().length() == 0)) {
            logger.log(Level.WARNING, " sPort or dPort are zero length");
            return "OK";
        }

        final MLAFOXConfig mlAFOXConfig = this.config;
        final ConcurrentMap<AFOXRawPort, AFOXRawPort> sConnsMap = mlAFOXConfig.crossConnMap();

        final AFOXRawPort sRawPort = AFOXRawPort.valueOf(sPort + ":INPUT_PORT");
        final AFOXRawPort dRawPort = AFOXRawPort.valueOf(dPort + ":OUTPUT_PORT");
        final boolean bFirstRemove = sConnsMap.remove(sRawPort, dRawPort);
        if (bFirstRemove) {
            logger.log(Level.INFO, "[ AFOXAgent ] [ connectPorts ] disconnected first pair: " + sRawPort + " -> "
                    + dRawPort);
        } else {
            logger.log(Level.WARNING, "[ AFOXAgent ] [ connectPorts ] UNABLE to DISCONNECT first pair: " + sRawPort
                    + " -> " + dRawPort);
        }
        if (fullDuplex && bFirstRemove) {
            final AFOXRawPort sRawFDXPort = AFOXRawPort.valueOf(dPort + ":INPUT_PORT");
            final AFOXRawPort dRawFDXPort = AFOXRawPort.valueOf(sPort + ":OUTPUT_PORT");

            if (sConnsMap.remove(sRawFDXPort, dRawFDXPort)) {
                logger.log(Level.INFO, "[ AFOXAgent ] [ connectPorts ] disconnected second pair: " + sRawFDXPort
                        + " -> " + dRawFDXPort);
            } else {
                logger.log(Level.WARNING, "[ AFOXAgent ] [ connectPorts ] UNABLE to DISCONNECT second pair: "
                        + sRawFDXPort + " -> " + dRawFDXPort);
            }
        }
        updateState();
        shouldReloadConfig.set(true);
        //notify the change in a "forced" asynchronous manner
        monitorExec.submit(new ConfigPublisherTask());
        return "OK";
    }

    /**
     * @param eqptID 
     * @param ip  
     * @param mask  
     * @param gw  
     * @throws RemoteException 
     */
    @Override
    public String changeNPPort(String eqptID, String ip, String mask, String gw) throws RemoteException {
        return "Not implemented yet!";
    }

    /**
     * @param routerID 
     * @param areaID  
     * @throws RemoteException 
     */
    @Override
    public String changeOSPF(String routerID, String areaID) throws RemoteException {
        return "Not implemented yet!";
    }

    /**
     * @param msgRetryInvl 
     * @param ntfRetryInvl  
     * @param grInvl  
     * @param grcInvl  
     * @throws RemoteException 
     */
    @Override
    public String changeRSVP(String msgRetryInvl, String ntfRetryInvl, String grInvl, String grcInvl)
            throws RemoteException {
        return "Not implemented yet!";
    }

    /**
     * @param name 
     * @param remoteIP  
     * @param remoteRid  
     * @param port  
     * @param adj  
     * @param helloInvl  
     * @param helloInvlMin  
     * @param helloInvlMax 
     * @param deadInvl 
     * @param deadInvlMin 
     * @param deadInvlMax 
     * @throws RemoteException 
     */
    @Override
    public String addCtrlCh(String name, String remoteIP, String remoteRid, String port, String adj, String helloInvl,
            String helloInvlMin, String helloInvlMax, String deadInvl, String deadInvlMin, String deadInvlMax)
            throws RemoteException {
        return "Not implemented yet!";
    }

    /**
     * @param name  
     * @throws RemoteException 
     */
    @Override
    public String delCtrlCh(String name) throws RemoteException {
        return "Not implemented yet!";
    }

    /**
     * @param name 
     * @param remoteIP 
     * @param remoteRid 
     * @param port 
     * @param adj 
     * @param helloInvl 
     * @param helloInvlMin  
     * @param helloInvlMax 
     * @param deadInvl 
     * @param deadInvlMin 
     * @param deadInvlMax 
     * @throws RemoteException 
     */
    @Override
    public String changeCtrlCh(String name, String remoteIP, String remoteRid, String port, String adj,
            String helloInvl, String helloInvlMin, String helloInvlMax, String deadInvl, String deadInvlMin,
            String deadInvlMax) throws RemoteException {
        return "Not implemented yet!";
    }

    /**
     * @param name 
     * @param ctrlCh 
     * @param remoteRid 
     * @param ospfArea 
     * @param metric 
     * @param ospfAdj 
     * @param adjType 
     * @param rsvpRRFlag 
     * @param rsvpGRFlag 
     * @param ntfProc 
     * @throws RemoteException  
     */
    @Override
    public String addAdj(String name, String ctrlCh, String remoteRid, String ospfArea, String metric, String ospfAdj,
            String adjType, String rsvpRRFlag, String rsvpGRFlag, String ntfProc) throws RemoteException {
        return "Not implemented yet!";
    }

    /**
     * @param name 
     * @throws RemoteException  
     */
    @Override
    public String deleteAdj(String name) throws RemoteException {
        return "Not implemented yet!";
    }

    /**
     * @param name 
     * @param ctrlCh 
     * @param remoteRid 
     * @param ospfArea 
     * @param metric 
     * @param ospfAdj 
     * @param adjType 
     * @param rsvpRRFlag  
     * @param rsvpGRFlag 
     * @param ntfProc 
     * @throws RemoteException 
     */
    @Override
    public String changeAdj(String name, String ctrlCh, String remoteRid, String ospfArea, String metric,
            String ospfAdj, String adjType, String rsvpRRFlag, String rsvpGRFlag, String ntfProc)
            throws RemoteException {
        return "Not implemented yet!";
    }

    /**
     * @param name 
     * @param localIP 
     * @param remoteIP 
     * @param adj  
     * @throws RemoteException 
     */
    @Override
    public String addLink(String name, String localIP, String remoteIP, String adj) throws RemoteException {
        return "Not implemented yet!";
    }

    /**
     * @param name  
     * @throws RemoteException 
     */
    @Override
    public String delLink(String name) throws RemoteException {
        return "Not implemented yet!";
    }

    /**
     * @param name 
     * @param name 
     * @param localIP 
     * @param remoteIP 
     * @param linkType 
     * @param adj 
     * @param wdmAdj 
     * @param remoteIf 
     * @param wdmRemoteIf  
     * @param lmpVerify 
     * @param fltDetect 
     * @param metric 
     * @param port 
     * @throws RemoteException 
     */
    @Override
    public String changeLink(String name, String localIP, String remoteIP, String linkType, String adj, String wdmAdj,
            String remoteIf, String wdmRemoteIf, String lmpVerify, String fltDetect, String metric, String port)
            throws RemoteException {
        return "Not implemented yet!";
    }

    /**
     * @param src 
     * @param dest 
     * @param isFDX 
     * @throws RemoteException  
     */
    @Override
    public String makeMLPathConn(String src, String dest, boolean isFDX) throws RemoteException {
        return "Not implemented yet!";
    }

    /**
     * @param olID 
     * @throws RemoteException  
     */
    @Override
    public String deleteMLPathConn(String olID) throws RemoteException {
        return "Not implemented yet!";
    }

    /**
     * @return the localAdminInterface
     */
    public OSAdminInterface getLocalAdminInterface() {
        return localAdminInterface;
    }
}
