package lia.util.telnet;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;

class Sys300Telnet extends OSTelnet {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(Sys300Telnet.class.getName());
    private static Sys300Telnet _controlInstance = null;
    private static Sys300Telnet _monitorInstance = null;
    private static volatile boolean controlInited;
    private static volatile boolean monitorInited;

    private Sys300Telnet(String username, String passwd, String hostName, int port) throws Exception {
        super(username, passwd, hostName, port);
    }

    public static Sys300Telnet getMonitorInstance() throws Exception {

        //TODO - Still slow ? ( works only wit 1.5 ? )
        if (!monitorInited) {
            synchronized (Sys300Telnet.class) {
                if (!monitorInited) {
                    String username = AppConfig.getProperty("lia.util.telnet.Sys300MonitorUsername");
                    String passwd = AppConfig.getProperty("lia.util.telnet.Sys300MonitorPasswd");
                    String hostName = AppConfig.getProperty("lia.util.telnet.Sys300MonitorHostname");

                    if ((username == null) || (passwd == null) || (hostName == null)) {
                        throw new Exception(
                                "[ Sys300Telnet ] Sys300MonitorUsername, Sys300MonitorPasswd and Sys300MonitorHostname MUST BE != null");
                    }

                    int port = 10033;

                    try {
                        port = AppConfig.geti("lia.util.telnet.Sys300MonitorPort", 10033);
                    } catch (Throwable t) {
                        port = 10033;
                    }

                    _monitorInstance = new Sys300Telnet(username, passwd, hostName, port);
                    monitorInited = true;
                }//if - sync
            }//end sync
        }//if - not sync

        return _monitorInstance;
    }

    public static Sys300Telnet getControlInstance() throws Exception {

        if (!controlInited) {
            synchronized (Sys300Telnet.class) {
                if (!controlInited) {
                    try {
                        String username = AppConfig.getProperty("lia.util.telnet.Sys300ControlUsername", null).trim();
                        String passwd = AppConfig.getProperty("lia.util.telnet.Sys300ControlPasswd", null).trim();
                        String hostName = AppConfig.getProperty("lia.util.telnet.Sys300ControlHostname", null).trim();
                        if ((username != null) && (passwd != null) && (hostName != null)) {
                            int port = 10033;
                            try {
                                port = AppConfig.geti("lia.util.telnet.Sys300ControlPort", 10033);
                            } catch (Throwable t) {
                                port = 10033;
                            }
                            _controlInstance = new Sys300Telnet(username, passwd, hostName, port);
                        }
                    } catch (Throwable t) {
                        logger.log(Level.WARNING,
                                "Cannot instantiate ControlSys300Telnet ... will try to use MonitorSys300Telnet", t);
                        _controlInstance = null;
                    }

                    if (_controlInstance == null) {
                        _controlInstance = getMonitorInstance();
                    }

                    controlInited = true;
                }//if - sync
            }//sync
        }//if - not sync

        return _controlInstance;
    }

    @Override
    public void makeFDXConn(String cnx) throws Exception {
        String[] csplit = decodeConn(cnx);
        makeFDXConn(csplit[0], csplit[1]);
    }

    public void makeFDXConn(String iPort, String oPort) throws Exception {
        String sPort = "100" + iPort;
        String dPort = "200" + oPort;

        String pSPort = "100" + oPort;
        String pDPort = "200" + iPort;

        execCmd("ent-crs-fiber::" + sPort + "&" + pSPort + "," + dPort + "&" + pDPort + ":" + MCONN_CTAG + ";",
                MCONN_CTAG);
    }

    /**
     * @return true if succes, false otherwise 
     */
    @Override
    public void makeConn(String cnx) throws Exception {
        String[] csplit = decodeConn(cnx);
        makeConn(csplit[0], csplit[1]);
    }

    /**
     * @return true if succes, false otherwise 
     */
    public void makeConn(String iPort, String oPort) throws Exception {
        execCmd("ent-crs-fiber::100" + iPort + ",200" + oPort + ":" + MCONN_CTAG + ";", MCONN_CTAG);
    }

    //cnx should be iPort&oPort
    @Override
    public void deleteFDXConn(String cnx) throws Exception {
        String[] csplit = decodeConn(cnx);
        deleteConn(csplit[0], csplit[1]);
        deleteConn(csplit[1], csplit[0]);
    }

    //cnx should be iPort&oPort
    @Override
    public void deleteConn(String cnx) throws Exception {
        String[] csplit = decodeConn(cnx);
        deleteConn(csplit[0], csplit[1]);
    }

    public void deleteConn(String iPort, String oPort) throws Exception {
        execCmd("dlt-crs-fiber::100" + iPort + ",200" + oPort + ":" + DCONN_CTAG + ";", DCONN_CTAG);
    }

    //cnx should be iPort&oPort
    public boolean isConn(String cnx) throws Exception {
        String[] csplit = decodeConn(cnx);
        return isConn(csplit[0], csplit[1]);
    }

    //TODO - MUST be redone !! use execCmdAndGet
    public boolean isConn(String iPort, String oPort) throws Exception {
        String sPort = "100" + iPort;
        String dPort = "200" + oPort;

        Sys300Telnet st = Sys300Telnet.getControlInstance();

        BufferedReader br = new BufferedReader(new StringReader(st.doCmd(
                "rtrv-crs-fiber::" + sPort + "&" + dPort + ":" + ISCONN_CTAG + ";", ISCONN_CTAG).toString()));

        boolean isConn = false;
        try {
            String line = null;
            while ((line = br.readLine()) != null) {
                if ((line.indexOf("IPORTID=" + sPort) != -1) && (line.indexOf("OPORTID=" + dPort) != -1)) {
                    line = line.substring(line.indexOf("\""), line.lastIndexOf("\""));
                    String[] tokens = line.split("(\\s)*,(\\s)*");
                    //GGN:IPORTID=10046,IPORTNAME=,OPORTID=20047,OPORTNAME=,CONNID=0,CONNSTATE=steady,CONNCAUSE=none,INPWR=-5.873,OUTPWR=-6.762,PWRLOSS=0.889
                    String connState = tokens[5];
                    if ((connState.indexOf("steady") != -1) || (connState.indexOf("fault") != -1)) {
                        isConn = true;
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got exc in deleteConn", t);
            isConn = false;
        } finally {
            try {
                br.close();
                br = null;
            } catch (Throwable t1) {
            }
        }
        return isConn;
    }

    @Override
    public boolean changeOSPF(String routerID, String areaID) throws Exception {
        throw new Exception(" [ Sys300Telnet ] This is not implemented yet on Glimmerglass switches");
    }

    @Override
    public boolean changeRSVP(String msgRetryInvl, String ntfRetryInvl, String grInvl, String grcInvl) throws Exception {
        throw new Exception(" [ Sys300Telnet ] This is not implemented yet on Glimmerglass switches");
    }

    @Override
    public boolean changeNPPort(String eqptID, String ip, String mask, String gw) throws Exception {
        throw new Exception(" [ Sys300Telnet ] This is not implemented yet on Glimmerglass switches");
    }

    @Override
    public boolean addCtrlCh(String name, String remoteIP, String remoteRid, String port, String adj, String helloInvl,
            String helloInvlMin, String helloInvlMax, String deadInvl, String deadInvlMin, String deadInvlMax)
            throws Exception {
        throw new Exception(" [ Sys300Telnet ] This is not implemented yet on Glimmerglass switches");
    }

    @Override
    public boolean delCtrlCh(String name) throws Exception {
        throw new Exception(" [ Sys300Telnet ] This is not implemented yet on Glimmerglass switches");
    }

    @Override
    public boolean changeCtrlCh(String name, String remoteIP, String remoteRid, String port, String adj,
            String helloInvl, String helloInvlMin, String helloInvlMax, String deadInvl, String deadInvlMin,
            String deadInvlMax) throws Exception {
        throw new Exception(" [ Sys300Telnet ] This is not implemented yet on Glimmerglass switches");
    }

    @Override
    public boolean addAdj(String name, String ctrlCh, String remoteRid, String ospfArea, String metric, String ospfAdj,
            String adjType, String rsvpRRFlag, String rsvpGRFlag, String ntfProc) throws Exception {
        throw new Exception(" [ Sys300Telnet ] This is not implemented yet on Glimmerglass switches");
    }

    @Override
    public boolean deleteAdj(String name) throws Exception {
        throw new Exception(" [ Sys300Telnet ] This is not implemented yet on Glimmerglass switches");
    }

    @Override
    public boolean changeAdj(String name, String ctrlCh, String remoteRid, String ospfArea, String metric,
            String ospfAdj, String adjType, String rsvpRRFlag, String rsvpGRFlag, String ntfProc) throws Exception {
        throw new Exception(" [ Sys300Telnet ] This is not implemented yet on Glimmerglass switches");
    }

    @Override
    public boolean addLink(String name, String localIP, String remoteIP, String adj) throws Exception {
        throw new Exception(" [ Sys300Telnet ] This is not implemented yet on Glimmerglass switches");
    }

    @Override
    public boolean delLink(String name) throws Exception {
        throw new Exception(" [ Sys300Telnet ] This is not implemented yet on Glimmerglass switches");
    }

    @Override
    public boolean changeLink(String name, String localIP, String remoteIP, String linkType, String adj, String wdmAdj,
            String remoteIf, String wdmRemoteIf, String lmpVerify, String fltDetect, String metric, String port)
            throws Exception {
        throw new Exception(" [ Sys300Telnet ] This is not implemented yet on Glimmerglass switches");
    }

    @Override
    public void connected() {
        logger.log(Level.INFO, " [ Sys300Telnet ] connected");
    }

}
