package lia.Monitor.Agents.OpticalPath.v2.Admin;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Agents.OpticalPath.Admin.OSAdminInterface;
import lia.Monitor.Agents.OpticalPath.v2.OpticalPathAgent_v2;
import lia.Monitor.JiniSerFarmMon.RegFarmMonitor;
import lia.Monitor.monitor.AppConfig;
import lia.util.net.rmi.RangePortUnicastRemoteObject;
import lia.util.security.OSRSSF;
import lia.util.security.RCSF;

public class OSAdminImpl extends RangePortUnicastRemoteObject implements OSAdminInterface {

    private static final long serialVersionUID = 8840343890798584822L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(OSAdminImpl.class.getName());
    OpticalPathAgent_v2 parent;

    public OSAdminImpl(OpticalPathAgent_v2 parent) throws RemoteException {
        super(new RCSF(), new OSRSSF());

        this.parent = parent;

        String forceIP = AppConfig.getProperty("lia.Monitor.useIPaddress");
        try {
            if (forceIP == null) {
                Naming.rebind("rmi://localhost:" + RegFarmMonitor.REGISTRY_PORT + "/OS_Admin", this);
            } else {
                Naming.rebind("rmi://" + forceIP + ":" + RegFarmMonitor.REGISTRY_PORT + "/OS_Admin", this);
            }
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "rmi export OSAdminImpl");
            }
        } catch (Throwable t) {
            logger.log(Level.SEVERE, " Failed to export OSAdminImpl" + t);
        }

    }

    @Override
    public String changePortState(String portName, String newSignalType) throws RemoteException {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " changePortState called with portName [" + portName + "] and newSignalType ["
                    + newSignalType + "]");
        }
        return "Not Implemented Yet!";
    }

    @Override
    public String connectPorts(String sPort, String dPort, String connParams, boolean fullDuplex)
            throws RemoteException {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " changePortState called with sPort [" + sPort + "] :- dPort [" + dPort
                    + "] :- connParams [" + connParams + "] fullDuplex [" + fullDuplex + "]");
        }
        if (parent != null) {
            try {
                return parent.makeConn(sPort, dPort, fullDuplex);//ignore fullDuplex for the moment
            } catch (Throwable t) {
                return t.getLocalizedMessage();
            }
        }
        return "Not Implemented Yet!";
    }

    @Override
    public String disconnectPorts(String sPort, String dPort, String connParams, boolean fullDuplex)
            throws RemoteException {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " changePortState called with sPort [" + sPort + "] :- dPort [" + dPort
                    + "] :- connParams [" + connParams + "] fullDuplex [" + fullDuplex + "]");
        }
        if (parent != null) {
            try {
                return parent.delConn(sPort, dPort, fullDuplex);//ignore fullDuplex for the moment
            } catch (Throwable t) {
                return t.getLocalizedMessage();
            }
        }
        return "Not Implemented Yet!";
    }

    @Override
    public String changeOSPF(String routerID, String areaID) throws RemoteException {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "changeOSPF called with routerID [" + routerID + "], areaID [" + areaID + "]");
        }
        if (parent != null) {
            try {
                return parent.changeOSPF(routerID, areaID);
            } catch (Throwable t) {
                return t.getLocalizedMessage();
            }
        }
        return "Not Implemented Yet!";
    }

    @Override
    public String changeRSVP(String msgRetryInvl, String ntfRetryInvl, String grInvl, String grcvInvl)
            throws RemoteException {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "changeRSVP called with msgRetryInvl [" + msgRetryInvl + "], ntfRetryInvl ["
                    + ntfRetryInvl + "], grInvl [" + grInvl + "], grcvInvl [" + grcvInvl + "]");
        }
        if (parent != null) {
            try {
                return parent.changeRSVP(msgRetryInvl, ntfRetryInvl, grInvl, grcvInvl);
            } catch (Throwable t) {
                return t.getLocalizedMessage();
            }
        }
        return "Not Implemented Yet!";
    }

    @Override
    public String changeNPPort(String eqptID, String ip, String mask, String gw) throws RemoteException {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "changeNPPort called with eqptID[ " + eqptID + "], ip [" + ip + "], mask [" + mask
                    + "], gw [" + gw + "]");
        }
        if (parent != null) {
            try {
                return parent.changeNPPort(eqptID, ip, mask, gw);
            } catch (Throwable t) {
                return t.getLocalizedMessage();
            }
        }
        return "Not Implemented Yet!";
    }

    @Override
    public String addCtrlCh(String name, String remoteIP, String remoteRid, String port, String adj, String helloInvl,
            String helloInvlMin, String helloInvlMax, String deadInvl, String deadInvlMin, String deadInvlMax)
            throws RemoteException {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "addCtrlCh called with name[ " + name + "], remoteIP [" + remoteIP
                    + "], remoteRid [" + remoteRid + "], port [" + port + "], adj [" + adj + "], helloInvl["
                    + helloInvl + "], helloInvlMin[" + helloInvlMin + "], helloInvlMax[" + helloInvlMax
                    + "], deadInvl [" + deadInvl + "], deadInvlMin [" + deadInvlMin + "], deadInvlMax [" + deadInvlMax
                    + "]");
        }
        if (parent != null) {
            try {
                return parent.addCtrlCh(name, remoteIP, remoteRid, port, adj, helloInvl, helloInvlMin, helloInvlMax,
                        deadInvl, deadInvlMin, deadInvlMax);
            } catch (Throwable t) {
                return t.getLocalizedMessage();
            }
        }
        return "Not Implemented Yet!";
    }

    @Override
    public String delCtrlCh(String name) throws RemoteException {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "delCtrlCh called with name[ " + name + "]");
        }
        if (parent != null) {
            try {
                return parent.delCtrlCh(name);
            } catch (Throwable t) {
                return t.getLocalizedMessage();
            }
        }
        return "Not Implemented Yet!";
    }

    @Override
    public String changeCtrlCh(String name, String remoteIP, String remoteRid, String port, String adj,
            String helloInvl, String helloInvlMin, String helloInvlMax, String deadInvl, String deadInvlMin,
            String deadInvlMax) throws RemoteException {

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "changeCtrlCh called with name[ " + name + "], remoteIP [" + remoteIP
                    + "], remoteRid [" + remoteRid + "], port [" + port + "], adj [" + adj + "], helloInvl["
                    + helloInvl + "], helloInvlMin[" + helloInvlMin + "], helloInvlMax[" + helloInvlMax
                    + "], deadInvl [" + deadInvl + "], deadInvlMin [" + deadInvlMin + "], deadInvlMax [" + deadInvlMax
                    + "]");
        }
        if (parent != null) {
            try {
                return parent.changeCtrlCh(name, remoteIP, remoteRid, port, adj, helloInvl, helloInvlMin, helloInvlMax,
                        deadInvl, deadInvlMin, deadInvlMax);
            } catch (Throwable t) {
                return t.getLocalizedMessage();
            }
        }
        return "Not Implemented Yet!";
    }

    @Override
    public String addAdj(String name, String ctrlCh, String remoteRid, String ospfArea, String metric, String ospfAdj,
            String adjType, String rsvpRRFlag, String rsvpGRFlag, String ntfProc) throws RemoteException {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "addAdj called with name[" + name + "] ctrlCh [" + ctrlCh + "] remoteRid ["
                    + remoteRid + "] ospfArea [" + ospfArea + "] metric [" + metric + "] ospfAdj [" + ospfAdj
                    + "] adjType [" + adjType + "] rsvpRRFlag [" + rsvpRRFlag + "] rsvpGRFlag [" + rsvpGRFlag
                    + "] ntfProc [" + ntfProc + "]");
        }
        if (parent != null) {
            try {
                return parent.addAdj(name, ctrlCh, remoteRid, ospfArea, metric, ospfAdj, adjType, rsvpRRFlag,
                        rsvpGRFlag, ntfProc);
            } catch (Throwable t) {
                return t.getLocalizedMessage();
            }
        }
        return "Not Implemented Yet!";
    }

    @Override
    public String deleteAdj(String name) throws RemoteException {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "deleteAdj called with name[" + name + "]");
        }
        if (parent != null) {
            try {
                return parent.deleteAdj(name);
            } catch (Throwable t) {
                return t.getLocalizedMessage();
            }
        }
        return "Not Implemented Yet!";
    }

    @Override
    public String changeAdj(String name, String ctrlCh, String remoteRid, String ospfArea, String metric,
            String ospfAdj, String adjType, String rsvpRRFlag, String rsvpGRFlag, String ntfProc)
            throws RemoteException {

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "changeAdj called with name[" + name + "] ctrlCh [" + ctrlCh + "] remoteRid ["
                    + remoteRid + "] ospfArea [" + ospfArea + "] metric [" + metric + "] ospfAdj [" + ospfAdj
                    + "] adjType [" + adjType + "] rsvpRRFlag [" + rsvpRRFlag + "] rsvpGRFlag [" + rsvpGRFlag
                    + "] ntfProc [" + ntfProc + "]");
        }
        if (parent != null) {
            try {
                return parent.changeAdj(name, ctrlCh, remoteRid, ospfArea, metric, ospfAdj, adjType, rsvpRRFlag,
                        rsvpGRFlag, ntfProc);
            } catch (Throwable t) {
                return t.getLocalizedMessage();
            }
        }
        return "Not Implemented Yet!";
    }

    @Override
    public String addLink(String name, String localIP, String remoteIP, String adj) throws RemoteException {

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "addLink called with name[" + name + "] localIP [" + localIP + "] remoteIP ["
                    + remoteIP + "] adj [" + adj);
        }
        if (parent != null) {
            try {
                return parent.addLink(name, localIP, remoteIP, adj);
            } catch (Throwable t) {
                return t.getLocalizedMessage();
            }
        }
        return "Not Implemented Yet!";
    }

    @Override
    public String delLink(String name) throws RemoteException {

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "delLink called with name[" + name + "]");
        }
        if (parent != null) {
            try {
                return parent.delLink(name);
            } catch (Throwable t) {
                return t.getLocalizedMessage();
            }
        }
        return "Not Implemented Yet!";
    }

    @Override
    public String changeLink(String name, String localIP, String remoteIP, String linkType, String adj, String wdmAdj,
            String remoteIf, String wdmRemoteIf, String lmpVerify, String fltDetect, String metric, String port)
            throws RemoteException {

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "changeLink called with name[" + name + "] localIP [" + localIP + "] remoteIP ["
                    + remoteIP + "] linkType [" + linkType + "] adj [" + adj + "] wdmAdj [" + wdmAdj + "] remoteIf ["
                    + remoteIf + "] wdmRemoteIf [" + wdmRemoteIf + "] lmpVerify [" + lmpVerify + "] fltDetect ["
                    + fltDetect + "] metric [" + metric + "] port [" + port + "]");
        }
        if (parent != null) {
            try {
                return parent.changeLink(name, localIP, remoteIP, linkType, adj, wdmAdj, remoteIf, wdmRemoteIf,
                        lmpVerify, fltDetect, metric, port);
            } catch (Throwable t) {
                return t.getLocalizedMessage();
            }
        }
        return "Not Implemented Yet!";
    }

    /**
     * 
     */
    @Override
    public String makeMLPathConn(String src, String dest, boolean isFDX) throws RemoteException {
        logger.log(Level.INFO, " Got makeMLPathConn from AdminInterface [ " + src + "," + dest + " ] isFDX = " + isFDX);
        try {
            return parent.makeMLPathConn(src, dest, isFDX);
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Cannot make conn because", t);
        }
        return "NOT_OK";
    }

    /**
     * 
     */
    @Override
    public String deleteMLPathConn(String olID) throws RemoteException {
        logger.log(Level.INFO, " Got deleteMLPathConn from AdminInterface [ " + olID + " ]");
        try {
            return parent.deleteMLPathConn(olID);
        } catch (Exception ex) {
            throw new RemoteException("Got exception trying to delete MLPath", ex);
        }
    }

}
