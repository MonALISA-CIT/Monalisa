package lia.util.telnet;

import java.util.regex.Pattern;

public abstract class OSTelnet extends TL1Telnet {

    protected static final Pattern PORT_SEPARATOR = Pattern.compile("(\\s)*-(\\s)*");

    public static final short NOSUCHTYPE = 0;
    public static final short CALIENT = 1;
    public static final short GLIMMERGLASS = 2;

    protected static long MIN_CONNECT_DELAY;

    public static final String MCONN_CTAG = "mcon";
    public static final String DCONN_CTAG = "dcon";
    public static final String ISCONN_CTAG = "icon";
    public static final String PPOWER_CTAG = "pmon";
    public static final String CCONN_CTAG = "cmon";

    OSTelnet(String username, String passwd, String hostName, int port) throws Exception {
        super(username, passwd, hostName, port, false);
    }

    // for a cnx like iPort&oPort should return {iPort, oPort}
    protected String[] decodeConn(String cnx) {
        return PORT_SEPARATOR.split(cnx);
    }

    public static final String getType(final short type) {
        switch (type) {
        case GLIMMERGLASS: {
            return "Glimmerglass";
        }
        case CALIENT: {
            return "Calient";
        }
        }
        return "NoSuchType";
    }

    public abstract void makeConn(String cnx) throws Exception;

    public abstract void makeFDXConn(String cnx) throws Exception;

    public abstract void deleteConn(String cnx) throws Exception;

    public abstract void deleteFDXConn(String cnx) throws Exception;

    /** added methods for gmpls special */
    public abstract boolean changeNPPort(String eqptID, String ip, String mask, String gw) throws Exception;

    public abstract boolean changeOSPF(String routerID, String areaID) throws Exception;

    public abstract boolean changeRSVP(String msgRetryInvl, String ntfRetryInvl, String grInvl, String grcInvl)
            throws Exception;

    public abstract boolean addCtrlCh(String name, String remoteIP, String remoteRid, String port, String adj,
            String helloInvl, String helloInvlMin, String helloInvlMax, String deadInvl, String deadInvlMin,
            String deadInvlMax) throws Exception;

    public abstract boolean delCtrlCh(String name) throws Exception;

    public abstract boolean changeCtrlCh(String name, String remoteIP, String remoteRid, String port, String adj,
            String helloInvl, String helloInvlMin, String helloInvlMax, String deadInvl, String deadInvlMin,
            String deadInvlMax) throws Exception;

    public abstract boolean addAdj(String name, String ctrlCh, String remoteRid, String ospfArea, String metric,
            String ospfAdj, String adjType, String rsvpRRFlag, String rsvpGRFlag, String ntfProc) throws Exception;

    public abstract boolean deleteAdj(String name) throws Exception;

    public abstract boolean changeAdj(String name, String ctrlCh, String remoteRid, String ospfArea, String metric,
            String ospfAdj, String adjType, String rsvpRRFlag, String rsvpGRFlag, String ntfProc) throws Exception;

    public abstract boolean addLink(String name, String localIP, String remoteIP, String adj) throws Exception;

    public abstract boolean delLink(String name) throws Exception;

    public abstract boolean changeLink(String name, String localIP, String remoteIP, String linkType, String adj,
            String wdmAdj, String remoteIf, String wdmRemoteIf, String lmpVerify, String fltDetect, String metric,
            String port) throws Exception;

    public static final short getType(String type) {
        if ((type == null) || (type.length() == 0)) {
            return NOSUCHTYPE;
        }
        if (type.toLowerCase().equals("glimmerglass")) {
            return GLIMMERGLASS;
        }
        if (type.toLowerCase().equals("calient")) {
            return CALIENT;
        }
        return NOSUCHTYPE;
    }

}
