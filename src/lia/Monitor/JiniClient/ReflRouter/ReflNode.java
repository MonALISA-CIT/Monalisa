package lia.Monitor.JiniClient.ReflRouter;

import java.text.NumberFormat;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.tcpClient.MLSerClient;
import lia.util.ntp.NTPDate;
import net.jini.core.lookup.ServiceID;

/**
 * ReflNode is the rcNode for the reflectors. It holds Reflector-specific data
 */
public class ReflNode {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(ReflNode.class.getName());

    // Reflector/*/Status data comes each 3 seconds. If it doesn't receive anything,
    // after this interval it will be considered INACTIVE. 
    public static long reflDataTimeout = AppConfig.getl("lia.Monitor.JiniClient.ReflDataTimeout", 30) * 1000; // seconds

    // In case node has problems, it will be penalized with this cost. This should
    // force the node to become a leaf and not to be selected in the core of the MST
    public static double nodePenaltyCost = AppConfig.getd("lia.Monitor.JiniClient.NodePenaltyCost", 5000);

    // How long after becoming ACTIVE it can be again part of the MST core. Default 20 minutes.
    public static long penaltyTimeout = AppConfig.getl("lia.Monitor.JiniClient.StatusChangeReadyDelay", 20 * 60) * 1000;

    public final static int ACTIVE = 1;
    public final static int INACTIVE = 2;

    public ServiceID sid;
    public MLSerClient client;

    public String UnitName;
    public String ipad;

    public Hashtable tunnels; // the key is the name of peer node

    private int status; // current state = active/inactive
    private long lastResultTime; // time of last result concerning this reflector
    private long statusChangeTime; // when was the last status change

    public static NumberFormat numberFormat = NumberFormat.getNumberInstance();
    private static String[] timeUnits = new String[] { "ms", "sec", "min", "hr", "day" };
    private static double[] timeDivisions = new double[] { 1, 1000, 60, 60, 24 };
    static {
        numberFormat.setMaximumFractionDigits(2);
    }

    /**
     * Constructor for the Reflector Node
     * @param UnitName The name of the unit, as returned from farm
     * @param sid unique identifier of the ML service
     * @param ipad the IP address of the reflector 
     * @param router pointer to the ReflRouter class
     */
    public ReflNode(String UnitName, ServiceID sid, String ipad) {
        this.UnitName = UnitName;
        this.sid = sid;
        this.ipad = ipad;
        tunnels = new Hashtable();
        setStatus(INACTIVE);
        markUpdated();
    }

    /** Set the Reflector's status */
    private void setStatus(int newStatus) {
        if (newStatus != status) {
            // changed status
            logger.info("Reflector " + UnitName + " is now " + getStatusStr(newStatus));
            if (!ReflRouter.hasJustStarted()) {
                statusChangeTime = NTPDate.currentTimeMillis();
            }
            status = newStatus;
        }
    }

    /**
     * Same as addPeer but for the internet links monitored through ABPing. The MST
     * is computed based on these results. The RTTime is minimized.
     * @param nn the peer node
     * @param r the result containing RTTime information about this link.
     */
    public void processInetResult(ReflNode dstNode, Result r) {
        markUpdated();
        IPTunnel tun = (IPTunnel) tunnels.get(dstNode.UnitName);
        if (tun == null) {
            tun = new IPTunnel(this, dstNode);
            tunnels.put(dstNode.UnitName, tun);
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "adding tunnel for inet info: " + UnitName + " -> " + dstNode.UnitName
                        + " for: " + r);
            }
        }
        for (int i = 0; i < r.param_name.length; i++) {
            String paramName = r.param_name[i];
            double paramValue = r.param[i];
            if (paramName.equals("RTime")) {
                tun.setInetQuality(paramValue);
            } else if (paramName.equals("PacketLoss")) {
                tun.setInetPktLoss(paramValue);
            }
        }
    }

    /** remove all tunnels from this node and return the vector with them */
    public Vector removeAllTunnels() {
        Vector rmTun = new Vector();
        for (Enumeration en = tunnels.keys(); en.hasMoreElements();) {
            String peerName = (String) en.nextElement();
            IPTunnel tun = (IPTunnel) tunnels.remove(peerName); // remove *-->
            if (tun != null) {
                tun.setNextStatus(IPTunnel.MUST_DEACTIVATE, "Remove tunnels from " + UnitName);
                rmTun.add(tun);
                tun = (IPTunnel) tun.to.tunnels.remove(this.UnitName); // remove <--*
                if (tun != null) {
                    tun.setNextStatus(IPTunnel.MUST_DEACTIVATE, "Remove tunnels to " + UnitName);
                    rmTun.add(tun);
                }
            }
        }
        return rmTun;
    }

    /** remove Peer info from a tunnel (if exists) about the given peer node */
    private void removePeerInfo(ReflNode dstNode) {
        IPTunnel tun = (IPTunnel) tunnels.get(dstNode.UnitName);
        if (tun != null) {
            tun.removePeerQuality();
        }
    }

    /** remove Peer info from all tunnels to adjacent nodes */
    private void removeAllPeersInfo() {
        for (Enumeration en = tunnels.elements(); en.hasMoreElements();) {
            IPTunnel tun = (IPTunnel) en.nextElement();
            tun.removePeerQuality();
        }
    }

    /**
     * Process an eResult like  ${this}/Peers/dstNodeIP/param.
     * This way we can quickly remove dead links, before expire.
     */
    public void processPeerResult(ReflNode dstNode, eResult er) {
        markUpdated();
        if (er.param_name == null) {
            if (er.NodeName == null) {
                // Removed all Peers from source node
                removeAllPeersInfo();
                logger.info("Removed peer info for Tunnels from " + UnitName);
            } else {
                if (dstNode != null) {
                    removePeerInfo(dstNode);
                    logger.info("Removed peer info for tunnel " + UnitName + "->" + dstNode.UnitName);
                }
            }
        } else {
            if (logger.isLoggable(Level.FINE)) {
                logger.finer("Ignoring result " + er);
            }
        }
    }

    /**
     * For the current reflector adds a peer link, i.e. a link currently used
     * by the reflector. If this link already exists, it refreshes the link's 
     * quality information.
     * @param nn the peer node
     * @param r the result that contains information about link's quality.
     */
    public void processPeerResult(ReflNode dstNode, Result r) {
        markUpdated();
        IPTunnel tun = (IPTunnel) tunnels.get(dstNode.UnitName);
        if (tun == null) {
            tun = new IPTunnel(this, dstNode);
            tunnels.put(dstNode.UnitName, tun);
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "adding tunnel for peer info: " + UnitName + " -> " + dstNode.UnitName);
            }
        }
        int i = r.getIndex("Quality");
        if (i >= 0) {
            // If we have peer results, we have to make sure that the reflector
            // is ready for participating to MST. Otherwise this link will be
            // immediately disconnected and we don't want that - for example,
            // ReflRouter starts, or a reflector appears with its set of peers
            // already set, we should start from that configuration and not by
            // trying to disable them.
            setStatus(ACTIVE);
            tun.setPeerQuality(r.param[i]);
        }
    }

    /** 
     * Process a Result like ${this}/Reflector/localhost/Status.
     * Based on this info we know if Panda is active or not - if we can use it
     * in the MST or not.
     */
    public void processReflectorResult(Result r) {
        markUpdated();
        int i = r.getIndex("Status");
        if (i >= 0) {
            setStatus(r.param[i] < -0.1 ? INACTIVE : ACTIVE);
        }
    }

    /**
     * Check if this reflector should be penalized for its behavior. That
     * happens if it becomes inactive and then active again. It will be
     * forced to be a leaf in MST for a period equal to penaltyTimeout.
     * @return reflector active or not
     */
    public boolean isPenalized() {
        if (ReflRouter.hasJustStarted()) {
            return false;
        }
        return (NTPDate.currentTimeMillis() - statusChangeTime) < penaltyTimeout;
    }

    /**
     * Check if reflector is still active
     * @return reflector active or not
     */
    public boolean checkReflActive() {
        if ((NTPDate.currentTimeMillis() - lastResultTime) > reflDataTimeout) {
            setStatus(INACTIVE);
        }
        return status == ACTIVE;
    }

    /** Mark the node as updated */
    private void markUpdated() {
        lastResultTime = NTPDate.currentTimeMillis();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ReflNode:");
        sb.append(UnitName).append("@").append(ipad);
        sb.append(" status=").append(getStatusStr(status));
        sb.append(" for ");
        appendDelay(sb, NTPDate.currentTimeMillis() - statusChangeTime);
        sb.append(" penalized=").append(isPenalized());
        sb.append(" last result ");
        appendDelay(sb, NTPDate.currentTimeMillis() - lastResultTime);
        sb.append(client == null ? "" : " client.active=" + client.active);
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        return ((o != null) && (o instanceof ReflNode) && ((ReflNode) o).UnitName.equals(UnitName));
    }

    @Override
    public int hashCode() {
        return sid.hashCode();
    }

    private String getStatusStr(int state) {
        switch (state) {
        case ACTIVE:
            return "active";
        case INACTIVE:
            return "INactive";
        default:
            return "UNKNOWN";
        }
    }

    /** append a pretty-printed delay to the given string buffer */
    public static void appendDelay(StringBuilder sb, long millis) {
        double time = millis;
        int i = 0;
        while ((i < timeUnits.length) && (time > (5 * timeDivisions[i]))) {
            time /= timeDivisions[i++];
        }
        if (i > 0) {
            i--;
        }
        if ((time > 1000) && (i > 0)) {
            sb.append("never");
        } else {
            sb.append(numberFormat.format(time)).append(" ").append(timeUnits[i]);
        }
    }
}
