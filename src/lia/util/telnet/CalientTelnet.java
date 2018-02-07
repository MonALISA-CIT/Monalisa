package lia.util.telnet;

import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;

class CalientTelnet extends OSTelnet {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(CalientTelnet.class.getName());
    private static CalientTelnet _monitorInstance = null;
    private static CalientTelnet _controlInstance = null;
    private static volatile boolean controlInited;
    private static volatile boolean monitorInited;

    private CalientTelnet(String username, String passwd, String hostName, int port) throws Exception {
        super(username, passwd, hostName, port);
    }

    public static final CalientTelnet getControlInstance() throws Exception {
        if (!controlInited) {//should speed-up a little bit ... 
            synchronized (CalientTelnet.class) {
                if (!controlInited) {
                    try {
                        String username = AppConfig.getProperty("lia.util.telnet.CalientControlUsername");
                        String passwd = AppConfig.getProperty("lia.util.telnet.CalientControlPasswd");
                        String hostName = AppConfig.getProperty("lia.util.telnet.CalientControlHostname");
                        if ((username != null) && (passwd != null) && (hostName != null)) {
                            int port = 3083;
                            try {
                                port = AppConfig.geti("lia.util.telnet.CalientControlPort", 3083);
                            } catch (Throwable t) {
                                port = 3083;
                            }
                            _controlInstance = new CalientTelnet(username, passwd, hostName, port);
                        }
                    } catch (Throwable t) {
                        logger.log(
                                Level.WARNING,
                                " [ CalientTelnet ] Cannot instantiate ControlCalientTelnet ... will try to use MonitorCalientTelnet",
                                t);
                        _controlInstance = null;
                    }
                    if (_controlInstance == null) {
                        _controlInstance = getMonitorInstance();
                    }

                    controlInited = true;
                }
            }//end sync
        }

        return _controlInstance;
    }

    public static synchronized final CalientTelnet getMonitorInstance() throws Exception {
        if (!monitorInited) {
            synchronized (CalientTelnet.class) {
                if (!monitorInited) {
                    String username = AppConfig.getProperty("lia.util.telnet.CalientMonitorUsername", null).trim();
                    String passwd = AppConfig.getProperty("lia.util.telnet.CalientMonitorPasswd", null).trim();
                    String hostName = AppConfig.getProperty("lia.util.telnet.CalientMonitorHostname", null).trim();
                    int port = 3083;
                    try {
                        port = AppConfig.geti("lia.util.telnet.CalienMonitortPort", 3083);
                    } catch (Throwable t) {
                        port = 3083;
                    }
                    _monitorInstance = new CalientTelnet(username, passwd, hostName, port);
                    monitorInited = true;
                }//if - sync
            }//sync
        }//if - not sync

        return _monitorInstance;
    }

    /**
     * @return true if succes, false otherwise 
     */
    @Override
    public void makeFDXConn(String cnx) throws Exception {
        String[] csplit = decodeConn(cnx);
        makeConn(csplit[0], csplit[1], true);
    }

    /**
     * @return true if succes, false otherwise 
     */
    @Override
    public void makeConn(String cnx) throws Exception {
        String[] csplit = decodeConn(cnx);
        makeConn(csplit[0], csplit[1], false);
    }

    /**
     * @return true if succes, false otherwise 
     */
    public void makeConn(String iPort, String oPort, boolean fdx) throws Exception {

        String sPort = iPort;
        String dPort = oPort;

        execCmd("ent-crs::" + sPort + "," + dPort + ":" + MCONN_CTAG + "::," + (fdx ? "2way" : "1way") + ";",
                MCONN_CTAG);
    }

    //cnx should be iPort&oPort
    @Override
    public void deleteFDXConn(String cnx) throws Exception {
        deleteConn(cnx, true);
    }

    @Override
    public void deleteConn(String cnx) throws Exception {
        deleteConn(cnx, false);
    }

    //cnx should be iPort&oPort
    public void deleteConn(String cnx, boolean isFDX) throws Exception {
        String[] csplit = decodeConn(cnx);
        deleteConn(csplit[0], csplit[1], isFDX);
    }

    public void deleteConn(String iPort, String oPort, boolean isFDX) throws Exception {
        String sPort = iPort;
        String dPort = oPort;

        if (isFDX) {
            execCmd("dlt-crs:::" + DCONN_CTAG + "::" + sPort + "-" + dPort + ";", DCONN_CTAG);
        } else {
            execCmd("dlt-crs:::" + DCONN_CTAG + "::" + sPort + ">" + dPort + ";", DCONN_CTAG);
        }
    }

    //cnx should be iPort&oPort
    public boolean isConn(String cnx) throws Exception {
        String[] csplit = decodeConn(cnx);
        return isConn(csplit[0], csplit[1]);
    }

    //TODO - MUST be redone !! use execCmdAndGet
    public boolean isConn(String iPort, String oPort) throws Exception {

        CalientTelnet st = CalientTelnet.getControlInstance();
        StringBuilder sb = st.doCmd("rtrv-crs:::" + ISCONN_CTAG + "::," + iPort + "-" + oPort + ";", ISCONN_CTAG);

        return (sb.indexOf(TL1_COMPLD_TAG) != -1);
    }

    @Override
    public boolean changeNPPort(String eqptID, String ip, String mask, String gw) throws Exception {
        if (eqptID == null) {
            return false;
        }
        CalientTelnet st = CalientTelnet.getControlInstance();

        StringBuilder buf = new StringBuilder();
        buf.append("set-ip::").append(eqptID).append(":::");
        if ((ip != null) && (ip.length() != 0)) {
            buf.append(ip);
        }
        if ((mask != null) && (mask.length() != 0)) {
            buf.append(",").append(mask);
        }
        if ((gw != null) && (gw.length() != 0)) {
            buf.append(",").append(gw);
        }
        buf.append(";");

        StringBuilder sb = st.doCmd(buf.toString(), null);
        return (sb.indexOf(TL1_COMPLD_TAG) != -1);
    }

    @Override
    public boolean changeOSPF(String routerID, String areaID) throws Exception {
        CalientTelnet st = CalientTelnet.getControlInstance();

        StringBuilder buf = new StringBuilder();
        buf.append("ed-cfg-ospf:::::");
        if ((routerID != null) && (routerID.length() != 0)) {
            buf.append(routerID);
        }
        if ((areaID != null) && (areaID.length() != 0)) {
            buf.append(",").append(areaID);
        }
        buf.append(";");

        StringBuilder sb = st.doCmd(buf.toString(), null);
        return (sb.indexOf(TL1_COMPLD_TAG) != -1);
    }

    @Override
    public boolean changeRSVP(String msgRetryInvl, String ntfRetryInvl, String grInvl, String grcvInvl)
            throws Exception {
        CalientTelnet st = CalientTelnet.getControlInstance();

        StringBuilder buf = new StringBuilder();
        buf.append("ed-cfg-rsvp:::::");
        if ((msgRetryInvl != null) && (msgRetryInvl.length() != 0)) {
            buf.append("msgretryinvl=").append(msgRetryInvl);
        }
        if ((ntfRetryInvl != null) && (ntfRetryInvl.length() != 0)) {
            buf.append(",ntfretryinvl=").append(ntfRetryInvl);
        }
        if ((grInvl != null) && (grInvl.length() != 0)) {
            buf.append(",grinvl=").append(grInvl);
        }
        if ((grcvInvl != null) && (grcvInvl.length() != 0)) {
            buf.append(",grcvinvl=").append(grcvInvl);
        }
        buf.append(";");

        StringBuilder sb = st.doCmd(buf.toString(), null);
        return (sb.indexOf(TL1_COMPLD_TAG) != -1);
    }

    @Override
    public boolean addCtrlCh(String name, String remoteIP, String remoteRid, String port, String adj, String helloInvl,
            String helloInvlMin, String helloInvlMax, String deadInvl, String deadInvlMin, String deadInvlMax)
            throws Exception {

        if ((name == null) || (name.length() == 0) || (remoteIP == null) || (remoteIP.length() == 0)
                || (remoteRid == null) || (remoteRid.length() == 0) || (port == null) || (port.length() == 0)) {
            return false;
        }
        CalientTelnet st = CalientTelnet.getControlInstance();

        StringBuilder buf = new StringBuilder();
        buf.append("ent-ctrlch::").append(name).append(":::");
        buf.append(remoteIP).append(",").append(remoteRid).append(",").append(port).append(":");
        if ((adj != null) && (adj.length() != 0)) {
            buf.append(",ADJ=").append(adj);
        }
        if ((helloInvl != null) && (helloInvl.length() != 0)) {
            buf.append(",HELLOINTRVL=").append(helloInvl);
        }
        if ((helloInvlMin != null) && (helloInvlMin.length() != 0)) {
            buf.append(",HELLOINTRVLMIN=").append(helloInvlMin);
        }
        if ((helloInvlMax != null) && (helloInvlMax.length() != 0)) {
            buf.append(",HELLOINTRVLMAX=").append(helloInvlMax);
        }
        if ((deadInvl != null) && (deadInvl.length() != 0)) {
            buf.append(",DEADINTRVL=").append(deadInvl);
        }
        if ((deadInvlMin != null) && (deadInvlMin.length() != 0)) {
            buf.append(",DEADINTRVLMIN=").append(deadInvlMin);
        }
        if ((deadInvlMax != null) && (deadInvlMax.length() != 0)) {
            buf.append(",DEADINTRVLMAX=").append(deadInvlMax);
        }
        buf.append(";");

        StringBuilder sb = st.doCmd(buf.toString(), null);
        return (sb.indexOf(TL1_COMPLD_TAG) != -1);
    }

    @Override
    public boolean delCtrlCh(String name) throws Exception {
        if ((name == null) || (name.length() == 0)) {
            return false;
        }
        CalientTelnet st = CalientTelnet.getControlInstance();

        StringBuilder buf = new StringBuilder();
        buf.append("dlt-ctrlch::").append(name).append(";");

        StringBuilder sb = st.doCmd(buf.toString(), null);
        return (sb.indexOf(TL1_COMPLD_TAG) != -1);
    }

    @Override
    public boolean changeCtrlCh(String name, String remoteIP, String remoteRid, String port, String adj,
            String helloInvl, String helloInvlMin, String helloInvlMax, String deadInvl, String deadInvlMin,
            String deadInvlMax) throws Exception {

        if ((name == null) || (name.length() == 0) || (remoteIP == null) || (remoteIP.length() == 0)
                || (remoteRid == null) || (remoteRid.length() == 0) || (port == null) || (port.length() == 0)) {
            return false;
        }
        CalientTelnet st = CalientTelnet.getControlInstance();

        if (!delCtrlCh(name)) {
            return false;
        }

        StringBuilder buf = new StringBuilder();
        buf.append("ent-ctrlch::").append(name).append(":::");
        buf.append(remoteIP).append(",").append(remoteRid).append(",").append(port).append(":");
        if ((adj != null) && (adj.length() != 0)) {
            buf.append(",ADJ=").append(adj);
        }
        if ((helloInvl != null) && (helloInvl.length() != 0)) {
            buf.append(",HELLOINTRVL=").append(helloInvl);
        }
        if ((helloInvlMin != null) && (helloInvlMin.length() != 0)) {
            buf.append(",HELLOINTRVLMIN=").append(helloInvlMin);
        }
        if ((helloInvlMax != null) && (helloInvlMax.length() != 0)) {
            buf.append(",HELLOINTRVLMAX=").append(helloInvlMax);
        }
        if ((deadInvl != null) && (deadInvl.length() != 0)) {
            buf.append(",DEADINTRVL=").append(deadInvl);
        }
        if ((deadInvlMin != null) && (deadInvlMin.length() != 0)) {
            buf.append(",DEADINTRVLMIN=").append(deadInvlMin);
        }
        if ((deadInvlMax != null) && (deadInvlMax.length() != 0)) {
            buf.append(",DEADINTRVLMAX=").append(deadInvlMax);
        }
        buf.append(";");

        StringBuilder sb = st.doCmd(buf.toString(), null);
        return (sb.indexOf(TL1_COMPLD_TAG) != -1);
    }

    @Override
    public boolean addAdj(String name, String ctrlCh, String remoteRid, String ospfArea, String metric, String ospfAdj,
            String adjType, String rsvpRRFlag, String rsvpGRFlag, String ntfProc) throws Exception {

        if ((name == null) || (name.length() == 0) || (ctrlCh == null) || (ctrlCh.length() == 0) || (remoteRid == null)
                || (remoteRid.length() == 0)) {
            return false;
        }
        CalientTelnet st = CalientTelnet.getControlInstance();

        StringBuilder buf = new StringBuilder();
        buf.append("ent-adj::").append(name).append(":::");
        buf.append(ctrlCh).append(",").append(remoteRid).append(":");
        if ((ospfArea != null) && (ospfArea.length() != 0)) {
            buf.append("OSPFAREA=").append(ospfArea);
        }
        if ((metric != null) && (metric.length() != 0)) {
            buf.append(",METRIC=").append(metric);
        }
        if ((ospfAdj != null) && (ospfAdj.length() != 0)) {
            buf.append(",OSPFADJ=").append(ospfAdj);
        }
        if ((adjType != null) && (adjType.length() != 0)) {
            buf.append(",ADJTYPE=").append(adjType);
        }
        if ((rsvpRRFlag != null) && (rsvpRRFlag.length() != 0)) {
            buf.append(",RSVPRRFLAG=").append(rsvpRRFlag);
        }
        if ((rsvpGRFlag != null) && (rsvpGRFlag.length() != 0)) {
            buf.append(",RSVPGRFLAG=").append(rsvpGRFlag);
        }
        if ((ntfProc != null) && (ntfProc.length() != 0)) {
            buf.append(",NTFPROC=").append(ntfProc);
        }
        buf.append(";");

        StringBuilder sb = st.doCmd(buf.toString(), null);
        return (sb.indexOf(TL1_COMPLD_TAG) != -1);
    }

    @Override
    public boolean deleteAdj(String name) throws Exception {
        if ((name == null) || (name.length() == 0)) {
            return false;
        }
        CalientTelnet st = CalientTelnet.getControlInstance();
        StringBuilder buf = new StringBuilder();
        buf.append("dlt-adj::").append(name).append(";");

        StringBuilder sb = st.doCmd(buf.toString(), null);
        return (sb.indexOf(TL1_COMPLD_TAG) != -1);
    }

    @Override
    public boolean changeAdj(String name, String ctrlCh, String remoteRid, String ospfArea, String metric,
            String ospfAdj, String adjType, String rsvpRRFlag, String rsvpGRFlag, String ntfProc) throws Exception {

        if ((name == null) || (name.length() == 0) || (ctrlCh == null) || (ctrlCh.length() == 0) || (remoteRid == null)
                || (remoteRid.length() == 0)) {
            return false;
        }
        CalientTelnet st = CalientTelnet.getControlInstance();

        if (!deleteAdj(name)) {
            return false;
        }

        StringBuilder buf = new StringBuilder();
        buf.append("ent-adj::").append(name).append(":::");
        buf.append(ctrlCh).append(",").append(remoteRid).append(":");
        if ((ospfArea != null) && (ospfArea.length() != 0)) {
            buf.append("OSPFAREA=").append(ospfArea);
        }
        if ((metric != null) && (metric.length() != 0)) {
            buf.append(",METRIC=").append(metric);
        }
        if ((ospfAdj != null) && (ospfAdj.length() != 0)) {
            buf.append(",OSPFADJ=").append(ospfAdj);
        }
        if ((adjType != null) && (adjType.length() != 0)) {
            buf.append(",ADJTYPE=").append(adjType);
        }
        if ((rsvpRRFlag != null) && (rsvpRRFlag.length() != 0)) {
            buf.append(",RSVPRRFLAG=").append(rsvpRRFlag);
        }
        if ((rsvpGRFlag != null) && (rsvpGRFlag.length() != 0)) {
            buf.append(",RSVPGRFLAG=").append(rsvpGRFlag);
        }
        if ((ntfProc != null) && (ntfProc.length() != 0)) {
            buf.append(",NTFPROC=").append(ntfProc);
        }
        buf.append(";");

        StringBuilder sb = st.doCmd(buf.toString(), null);
        return (sb.indexOf(TL1_COMPLD_TAG) != -1);
    }

    @Override
    public boolean addLink(String name, String localIP, String remoteIP, String adj) throws Exception {

        if ((name == null) || (name.length() == 0) || (localIP == null) || (localIP.length() == 0)
                || (remoteIP == null) || (remoteIP.length() == 0)) {
            return false;
        }
        CalientTelnet st = CalientTelnet.getControlInstance();

        StringBuilder buf = new StringBuilder();
        buf.append("ent-link::").append(name).append(":::Numbered,");
        if ((adj != null) && (adj.length() != 0)) {
            buf.append(adj);
        }
        buf.append(",:LOCALIP=").append(localIP).append(",REMOTEIP=").append(remoteIP).append(";");

        StringBuilder sb = st.doCmd(buf.toString(), null);
        return (sb.indexOf(TL1_COMPLD_TAG) != -1);
    }

    @Override
    public boolean delLink(String name) throws Exception {

        if ((name == null) || (name.length() == 0)) {
            return false;
        }
        CalientTelnet st = CalientTelnet.getControlInstance();
        StringBuilder buf = new StringBuilder();
        buf.append("rmv-link::").append(name).append(";");

        StringBuilder sb = st.doCmd(buf.toString(), null);
        return (sb.indexOf(TL1_COMPLD_TAG) != -1);
    }

    @Override
    public boolean changeLink(String name, String localIP, String remoteIP, String linkType, String adj, String wdmAdj,
            String remoteIf, String wdmRemoteIf, String lmpVerify, String fltDetect, String metric, String port)
            throws Exception {

        if ((name == null) || (name.length() == 0) || (linkType == null) || (linkType.length() == 0)) {
            return false;
        }
        if (!linkType.equals("Numbered") && !linkType.equals("Unnumbered")) {
            linkType = "Numbered";
        } // by default
        if (linkType.equals("Numbered")) {
            if ((localIP == null) || (localIP.length() == 0) || (remoteIP == null) || (remoteIP.length() == 0)) {
                return false;
            }
        } else {
            if ((remoteIf == null) || (remoteIf.length() == 0)) {
                return false;
            }
        }
        CalientTelnet st = CalientTelnet.getControlInstance();

        if (!delLink(name)) {
            return false;
        }

        boolean success = false;
        StringBuilder buf = new StringBuilder();
        buf.append("ent-link::").append(name).append(":::").append(linkType).append(",");
        if ((adj != null) && (adj.length() != 0)) {
            buf.append(adj);
        }
        buf.append(",");
        if ((wdmAdj != null) && (wdmAdj.length() != 0)) {
            buf.append(wdmAdj);
        }
        buf.append(":");
        if ((localIP != null) && (localIP.length() != 0)) {
            buf.append("LOCALIP=").append(localIP);
        }
        buf.append(",");
        if ((remoteIP != null) && (remoteIP.length() != 0)) {
            buf.append("REMOTEIP=").append(remoteIP);
        }
        buf.append(",");
        if ((wdmRemoteIf != null) && (wdmRemoteIf.length() != 0)) {
            buf.append("WDMREMOTETEIF=").append(wdmRemoteIf);
        }
        buf.append(",");
        if ((remoteIf != null) && (remoteIf.length() != 0)) {
            buf.append("REMOTETEIF=").append(remoteIf);
        }
        buf.append(",");
        if ((lmpVerify != null) && (lmpVerify.length() != 0)) {
            buf.append("LMPVERIFY=").append(lmpVerify);
        }
        buf.append(",");
        if ((fltDetect != null) && (fltDetect.length() != 0)) {
            buf.append("FLTDETECT=").append(fltDetect);
        }
        buf.append(",");
        if ((metric != null) && (metric.length() != 0)) {
            buf.append("METRIC=").append(metric);
        }
        buf.append(";");
        StringBuilder sb = st.doCmd(buf.toString(), null);
        success = (sb.indexOf(TL1_COMPLD_TAG) != -1);

        if (!success || (port == null) || (port.length() == 0)) {
            return success;
        }

        buf = new StringBuilder();
        buf.append("ed-port::").append(port).append(":::TELINK:").append("LINK=").append(name).append(";");

        sb = st.doCmd(buf.toString(), null);
        return (sb.indexOf(TL1_COMPLD_TAG) != -1);
    }

    @Override
    public void connected() {
        logger.log(Level.INFO, " [ CalientTelnet ] connected");
    }

    public static final void main(String args[]) throws Exception {

        CalientTelnet st = null;
        try {
            st = CalientTelnet.getControlInstance();
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }

        //        for (int i=1; i<=2; i++) {
        //            try {
        //                Thread.sleep(5000);
        //            }catch(Throwable t){
        //                
        //            }
        //            
        //            st.doCmd("rtrv-port::10.10a." + i + ":ctag;");
        //        }
        st.makeConn("10.12a.4-10.14a.5");
        System.out.println("\n\nVerifying ping connection ... should be get version info\n\n");
        //verify The Ping here
        try {
            Thread.sleep(20 * 1000);
        } catch (Throwable t) {
        }

        st.deleteConn("10.12a.4-10.14a.5");

        st.stopIt();

        //do the cleanup()
        try {
            Thread.sleep(5 * 1000);
        } catch (Throwable t) {
        }
    }
}
