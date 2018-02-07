package lia.net.topology.agents.admin;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Agents.OpticalPath.Admin.OSAdminInterface;
import lia.Monitor.JiniSerFarmMon.RegFarmMonitor;
import lia.Monitor.monitor.AppConfig;
import lia.net.topology.agents.AFOXAgent;
import lia.util.net.rmi.RangePortUnicastRemoteObject;
import lia.util.security.OSRSSF;
import lia.util.security.RCSF;

public class AFOXAdminImpl extends RangePortUnicastRemoteObject implements OSAdminInterface {

    /**
     * 
     */
    private static final long serialVersionUID = -3921673507268556255L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(AFOXAdminImpl.class.getName());

    final AFOXAgent parent;

    public AFOXAdminImpl(AFOXAgent parent) throws RemoteException {
        super(new RCSF(), new OSRSSF());

        this.parent = parent;

        String forceIP = AppConfig.getProperty("lia.Monitor.useIPaddress");
        try {
            if (forceIP == null) {
                Naming.rebind("rmi://localhost:" + RegFarmMonitor.REGISTRY_PORT + "/OS_Admin", this);
            } else {
                Naming.rebind("rmi://" + forceIP + ":" + RegFarmMonitor.REGISTRY_PORT + "/OS_Admin", this);
            }
            logger.log(Level.INFO, "RMI exported AFOXAdminImpl");
        } catch (Throwable t) {
            logger.log(Level.SEVERE, " Failed to export AFOXAdminImpl" + t);
        }

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
        return parent.connectPorts(sPort, dPort, connParams, fullDuplex);
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
        return parent.disconnectPorts(sPort, dPort, connParams, fullDuplex);
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

}
