package lia.Monitor.JiniClient.ReflRouter;

import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniClient.CommonGUI.DLink;
import lia.Monitor.monitor.AppConfig;
import lia.util.ntp.NTPDate;

/**
 * IPTunnel is a link between two reflector nodes. 
 */
public class IPTunnel {
    private static final Logger logger = Logger.getLogger(IPTunnel.class.getName());
    // Internet/ip/params data comes each 30 sec
    public static long inetTimeout = AppConfig.getl("lia.Monitor.JiniClient.InetTimeout", 60) * 1000; // seconds
    // Number of samples to keep for inet parm (RTime and PktLoss)
    public static int inetSamples = AppConfig.geti("lia.Monitor.JiniClient.InetSamples", 11);
    // Peers/ip/params data comes each 3 sec
    public static long peersTimeout = AppConfig.getl("lia.Monitor.JiniClient.PeersTimeout", 30) * 1000; // seconds
    // Number of samples to keep for peers parm (RTime and PktLoss)
    public static int peersSamples = AppConfig.geti("lia.Monitor.JiniClient.PeersSamples", 11);
    // if packet loss is higher, this will be excluded from MST
    public final static double maxPktLoss = AppConfig.getd("lia.Monitor.JiniClient.MaxPktLoss", 0.9);
    // Number of samples to be considered when deciding it's a high packet loss link
    public static int pktLossSamples = AppConfig.geti("lia.Monitor.JiniClient.PktLossSamples", 5);

    public final static int ACTIVE = 1; // current state
    public final static int INACTIVE = 2;
    public final static int MUST_DEACTIVATE = 3; // next state
    public final static int MUST_ACTIVATE = 4;

    public ReflNode from;
    public ReflNode to;

    private final HistoryParam peerQual; // link quality, as reported by reflector
    private final HistoryParam inetRTTime; // link quality, as reported by ABPing
    private final HistoryParam inetPktLoss; // packet loss observed on this tunnel
    private String lastCommand; // last command sent for this tunnel
    private long lastCommandTime; // when was the last command sent
    private String reasonNextState; // reason for being in next status

    private int crtState; // current tunnel status
    private int nextState; // computed tunnel status

    /**
     * Constructor for a link (tunnel) between two reflectors
     * @param srcNode source reflector
     * @param dstNode destination reflector
     */
    public IPTunnel(ReflNode srcNode, ReflNode dstNode) {
        this.from = srcNode;
        this.to = dstNode;
        peerQual = new HistoryParam(peersSamples, peersTimeout);
        inetRTTime = new HistoryParam(inetSamples, inetTimeout);
        inetPktLoss = new HistoryParam(pktLossSamples, inetTimeout);
        setCrtStatus(INACTIVE);
        setNextStatus(INACTIVE, "init");
    }

    /**
     * Set the new current status; if different from the last, clear last command.
     * This is set only internally, based on the received monitoring results.
     */
    private void setCrtStatus(int newStatus) {
        if (newStatus != crtState) {
            setLastCommand("none");
        }
        crtState = newStatus;
    }

    /** Get tunnel's current status */
    public int getCrtStatus() {
        return crtState;
    }

    /** Set new next status */
    public void setNextStatus(int newStatus, String reason) {
        nextState = newStatus;
        reasonNextState = reason;
    }

    /** Get tunnel's next status */
    public int getNextStatus() {
        return nextState;
    }

    /** Set the last command issued for this tunnel */
    public void setLastCommand(String cmd) {
        if (!cmd.equals(lastCommand)) {
            lastCommand = cmd;
            lastCommandTime = NTPDate.currentTimeMillis();
        }
    }

    /**
     * Set peer quality
     * @param pq
     */
    public void setPeerQuality(double pq) {
        peerQual.addValue(pq);
        setCrtStatus(ACTIVE);
    }

    /**
     * Get peer quality as an average on all known history.
     * @return peer quality
     */
    public double getPeerQuality() {
        return getPeerQuality(peersSamples);
    }

    /** Get the peer quality for as an average of the given number of samples */
    public double getPeerQuality(int nSamples) {
        return peerQual.getAvgValue(nSamples);
    }

    /** Remove the peer quality information for this tunnel. */
    public void removePeerQuality() {
        peerQual.invalidate();
        setCrtStatus(INACTIVE);
    }

    /**
     * Check if peer quality exists and is not expired.
     * @return true/false
     */
    public boolean hasPeerQuality() {
        return peerQual.isValid(1);
    }

    /**
     * Set the inet quality on this tunnel.
     * @param irt the value to be set
     */
    public void setInetQuality(double irt) {
        inetRTTime.addValue(irt);
    }

    /**
     * Get inet link quality as an average over all last stored values 
     * @return inetRTTime quality
     */
    public double getInetQuality() {
        return getInetQuality(inetSamples);
    }

    /** Get the inet link quality as an average over the last n samples */
    public double getInetQuality(int nSamples) {
        double qual = inetRTTime.getSmoothAvg(nSamples);
        if (from.isPenalized()) {
            qual += ReflNode.nodePenaltyCost;
        }
        if (to.isPenalized()) {
            qual += ReflNode.nodePenaltyCost;
        }
        return qual;
    }

    /**
     * Check if link has inet quality and is not expired
     * @return true/false
     */
    public boolean hasInetQuality() {
        return inetRTTime.isValid(1);
    }

    /**
     * Set the inet packet loss on this tunnel.
     * @param pktLoss the value to be set
     */
    public void setInetPktLoss(double pktLoss) {
        inetPktLoss.addValue(pktLoss);
    }

    /** Check if link has information about packet loss */
    public boolean hasPktLossInfo() {
        return inetPktLoss.isValid(1);
    }

    /** Check if there's high packet loss rate on this tunnel */
    public boolean hasHighPktLoss() {
        if (!hasPktLossInfo()) {
            return false;
        }
        return inetPktLoss.getAvgValue(pktLossSamples) > maxPktLoss;
    }

    /**
     * Check if this link is still alive; if not, remove it from "from" tunnels list 
     */
    public boolean checkAlive() {
        if ((peerQual.size() > 0) && peerQual.isExpired()) {
            if (logger.isLoggable(Level.FINE)) {
                logger.info("PeerQual expired for " + toString());
            }
            removePeerQuality();
        }
        if ((inetRTTime.size() > 0) && inetRTTime.isExpired()) {
            if (logger.isLoggable(Level.FINE)) {
                logger.info("InetRTT expired for " + toString());
            }
            inetRTTime.invalidate();
        }
        if ((inetPktLoss.size() > 0) && inetPktLoss.isExpired()) {
            if (logger.isLoggable(Level.FINE)) {
                logger.info("InePktLoss expired for " + toString());
            }
            inetPktLoss.invalidate();
        }
        return (peerQual.size() > 0) || (inetRTTime.size() > 0) || (inetPktLoss.size() > 0);
    }

    /**
     * string representation of this tunnel
     */
    @Override
    public String toString() {
        //checkAlive();
        long now = NTPDate.currentTimeMillis();
        StringBuilder sb = new StringBuilder("IPTun:");
        sb.append(from.UnitName).append("->").append(to.UnitName);
        sb.append(" pQ=").append(hasPeerQuality() ? ReflNode.numberFormat.format(getPeerQuality()) : "?");
        sb.append("/").append(peerQual.size());
        sb.append(" ");
        ReflNode.appendDelay(sb, peerQual.getUpdateDelay());
        sb.append(" iRT=").append(hasInetQuality() ? ReflNode.numberFormat.format(getInetQuality()) : "?");
        sb.append("/").append(inetRTTime.size());
        sb.append(" ");
        ReflNode.appendDelay(sb, inetRTTime.getUpdateDelay());
        sb.append(" loss=")
                .append(hasPktLossInfo() ? ReflNode.numberFormat.format(inetPktLoss.getAvgValue(pktLossSamples) * 100)
                        : "?").append("%");
        sb.append("/").append(inetPktLoss.size());
        sb.append(" crt=").append(stateStr(crtState)).append(" nxt=").append(stateStr(nextState));
        sb.append(" cmd=").append(lastCommand);
        sb.append(" ");
        ReflNode.appendDelay(sb, now - lastCommandTime);
        sb.append(" nsReason=").append(reasonNextState);
        return sb.toString();
    }

    /**
     * string for the state
     * @param state the state
     * @return the corresponding string
     */
    private String stateStr(int state) {
        switch (state) {
        case ACTIVE:
            return "active";
        case INACTIVE:
            return "INACTIVE";
        case MUST_DEACTIVATE:
            return "MUST_DEactivate";
        case MUST_ACTIVATE:
            return "MUST_activate";
        default:
            return "UNKNOWN";
        }
    }

    /**
     * Build a DLink from the current tunnel (used by the GUI client)
     * @return the DLink corresponding to this tunnel
     */
    public DLink toDLink() {
        DLink dl = new DLink(from.sid, to.sid, getInetQuality());
        return dl;
    }
}
