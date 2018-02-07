package lia.Monitor.modules;

import java.io.BufferedReader;
import java.net.InetAddress;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.util.ntp.NTPDate;

public class SyncVrvsConnT extends SyncVrvsTcpCmd implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = -6373417501074691414L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(SyncVrvsConnT.class.getName());

    static String[] tmetric = { "Quality", "LostPackages" };

    String cmd = "vrvs/check_peer_status/30";
    Hashtable peers;
    int port = -1;

    public SyncVrvsConnT() {
        super("SyncVrvsConnT");
        port = Integer.valueOf(AppConfig.getProperty("lia.Monitor.VRVS_port", "46011").trim()).intValue();
        info.ResTypes = tmetric;
        peers = new Hashtable();
        isRepetitive = true;
        canSuspend = false;
    }

    @Override
    public MonModuleInfo init(MNode Node, String arg) {
        super.init(Node, arg);
        this.Node = Node;
        info.ResTypes = tmetric;

        port = Integer.valueOf(AppConfig.getProperty("lia.Monitor.VRVS_port", "46011")).intValue();

        try {
            BufferedReader buff2 = procOutput(port, cmd);
            buff2.close();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, " Failed  to get perform the init cmd", t);
        }

        return info;
    }

    @Override
    public Object doProcess() throws Exception {
        //    BufferedReader buff1 = procOutput ( cmd );
        BufferedReader buff1 = procOutput(port, cmd);

        if (buff1 == null) {
            throw new Exception(" SyncVrvsConnT output  is null for " + Node.name);
        }

        Vector vec = Parse(buff1);

        return vec;
    }

    public Vector Parse(BufferedReader buff) throws Exception {

        String lin = null;

        // Read until buffer emty or the PEER keyword found
        for (lin = buff.readLine(); (lin != null) && (lin.indexOf("PEER") == -1); lin = buff.readLine()) {
            ;
        }

        if (lin == null) {
            return null;
        }

        Result rr = null;
        Vector results = new Vector();

        for (lin = buff.readLine(); lin != null; lin = buff.readLine()) {

            if (lin.length() > 10) {
                StringTokenizer tz = new StringTokenizer(lin);
                String peerNameIP = tz.nextToken();

                String peerName = null;

                if (!peers.containsKey(peerNameIP)) {
                    try {
                        peerName = InetAddress.getByName(peerNameIP).getHostName();
                    } catch (Exception e) {
                    }
                    if (peerName == null) {
                        logger.log(Level.WARNING, "Failed to get Addrees for " + peerNameIP + "Using the IP Address: "
                                + peerNameIP);
                        peers.put(peerNameIP, peerNameIP);

                    } else {
                        peers.put(peerNameIP, peerName);
                    }
                }

                peerName = (String) peers.get(peerNameIP);

                if (tz.hasMoreTokens()) {
                    rr = new Result(Node.getFarmName(), Node.getClusterName(), peerName, "vrvsConn", tmetric);

                    String v1 = tz.nextToken().trim();
                    String vv1 = v1.substring(0, v1.length() - 1);
                    double lo = (Double.valueOf(vv1)).doubleValue();
                    rr.param[0] = 100 - lo;
                    rr.param[1] = lo;
                    rr.time = NTPDate.currentTimeMillis();

                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "R: " + rr);
                    }
                    results.add(rr);
                }
            }
        }

        buff.close();
        return results;
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    @Override
    public String[] ResTypes() {
        return tmetric;
    }

    @Override
    public String getOsName() {
        return "linux";
    }

    static public void main(String[] args) {

        vrvsConn aa = new vrvsConn();
        String ad = null;
        String host = "localhost";
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        MonModuleInfo info = aa.init(new MNode(host, ad, null, null), null, null);

        try {

            Object bb = aa.doProcess();

        } catch (Exception e) {
            System.out.println(" failed to process !!!");
        }

    }

}
