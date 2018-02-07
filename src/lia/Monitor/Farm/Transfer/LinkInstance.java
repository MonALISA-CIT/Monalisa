package lia.Monitor.Farm.Transfer;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.MLProcess;
import lia.util.ntp.NTPDate;

/**
 * Hold and control an existing physical or logical link between two nodes. Each link keeps 
 * two hashes of ongoing requests (outgoing srcNode==>dstNode and incoming dstNode==>srcNode).
 * 
 * @author catac
 */
class LinkInstance implements ProtocolInstance {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(LinkInstance.class.getName());

    /** Timeout for running a link control command */
    private static final int LINK_CTRL_CMD_TIMEOUT = 10 * 1000;

    private final String linkName; // full name of the link, including id
    private final String linkBaseName;// base name of this link, without the id
    private final String srcNode; // source node (ML name or virtual node name)
    private final String dstNode; // destination node (ML name or virtual node name)
    private final String clusterName; // the name of the cluster used to report monitoring information about this link
    private final Integer id; // circuit id of this link; can be null if link has no ID, i.e. suitable for all circuits
    private boolean enabled; // if not enabled the link cannot be used for transfers (administrative status)
    private boolean active; // if true, this link can be used for transfers; otherwise not
    private String phys; // physical interface used by this (logical) link - can be used 
    private long bandwidth; // link's bandwidth, in bps
    private long delay; // link's delay, in milliseconds
    private long cost; // link's cost
    private String srcIP; // IP of the srcNode; when not null, the link is active and can be used for transfers
    private boolean guessedIP; // IP was guessed based on the value of phys.
    private String srcCMD; // command to activate/deactivate the link (start/stop) - this is executed at srcNode (current ML)
    private final Properties cmdParams; // the parameters passed when running the command to activate the link
    private final Properties cmdResParams; // the parameters produced by running the command. These are kept because are returned to subsequent link activation requests
    private final Hashtable htInRequests; // the requests using this link in the DST-->SRC direction
    private final Hashtable htOutRequests; // the requests using this link in the SRC-->DST direction
    private final Object syncRequests = new Object(); // sync object used for sync-ed access to the requests

    /** Create a new link */
    public LinkInstance(String baseName, String fromNode, String toNode, Integer circuitID) {
        this.linkBaseName = baseName;
        this.srcNode = fromNode;
        this.dstNode = toNode;
        this.id = circuitID;
        this.linkName = (id == null ? baseName : baseName + "-" + id);
        clusterName = "Link_" + srcNode + "->" + dstNode;
        bandwidth = TransferUtils.MAX_BANDWIDTH;
        enabled = true;
        active = true;
        guessedIP = true;
        cmdParams = new Properties();
        cmdResParams = new Properties();
        htInRequests = new Hashtable();
        htOutRequests = new Hashtable();
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Creating link instance " + linkName + " from " + fromNode + " to " + toNode);
        }
    }

    /**
     * Get the link's name.
     * @return the link's name, including the circuit id
     */
    public String getLinkName() {
        return linkName;
    }

    /**
     * Get the link's base name.
     * @return the link's base name, without the circuit id
     */
    public String getLinkBaseName() {
        return linkBaseName;
    }

    /**
     * Get the link's circuit id.
     * @return the link's circuit id; can be null, if the link is suitable for all circuits
     */
    public Integer getCircuitID() {
        return id;
    }

    /** Get the link's source node */
    public String getSrcNode() {
        return srcNode;
    }

    /** Get the link's destination node */
    public String getDstNode() {
        return dstNode;
    }

    /**
     * Based on the given source and destination nodes, determine if this direction is
     * outgoing, incoming or it doesn't match at all with the current link
     * @param sourceNode a source node
     * @param destinationNode a destination node
     * @return 1 for outgoing, -1 for incoming or 0 if the given pair doesn't match the link
     */
    public int getDirection(String sourceNode, String destinationNode) {
        if (srcNode.equals(sourceNode) && dstNode.equals(destinationNode)) {
            return 1;
        }
        if (srcNode.equals(destinationNode) && dstNode.equals(sourceNode)) {
            return -1;
        }
        return 0;
    }

    /**
     * Set link parameters
     * @param prop the properties to set
     */
    public void setParams(Properties prop) {
        synchronized (syncRequests) {
            for (Object element : prop.keySet()) {
                String propName = (String) element;
                String propValue = prop.getProperty(propName);
                if (propName.equalsIgnoreCase("srcIP")) {
                    try {
                        if (propValue.length() > 0) {
                            InetAddress ina = InetAddress.getByName(propValue);
                            srcIP = ina.getHostAddress();
                            guessedIP = false;
                        } else {
                            // if IP field is empty then, disable IP 
                            srcIP = null;
                            guessedIP = true;
                        }
                    } catch (UnknownHostException uhe) {
                        logger.warning("Invalid Hostname or IP address for " + linkName + ".srcIP: '" + propValue
                                + "'.");
                        srcIP = null;
                        guessedIP = true;
                    }
                } else if (propName.equalsIgnoreCase("enabled")) {
                    boolean value = Boolean.getBoolean(propValue);
                    if (!value) {
                        logger.info("Link " + linkName + " administratively shut down");
                        disableLink();
                    }
                    enabled = value; // set this status after disabling the link
                } else if (propName.equalsIgnoreCase("active")) {
                    active = Boolean.valueOf(propValue).booleanValue();
                } else if (propName.equalsIgnoreCase("bw") || propName.equalsIgnoreCase("bandwidth")) {
                    bandwidth = TransferUtils.parseBKMGps(propValue);
                } else if (propName.equalsIgnoreCase("delay")) {
                    delay = TransferUtils.parseLongValueProperty(propValue, linkName + ".delay", 0);
                } else if (propName.equalsIgnoreCase("cost")) {
                    cost = TransferUtils.parseLongValueProperty(propValue, linkName + ".cost", 0);
                } else if (propName.equalsIgnoreCase("srcCMD")) {
                    if (propValue.length() > 0) {
                        srcCMD = propValue;
                    } else {
                        srcCMD = null;
                    }
                } else if (propName.equalsIgnoreCase("phys")) {
                    if (propValue.length() > 0) {
                        if (!propValue.equals(phys)) {
                            phys = propValue;
                            if (guessedIP && (!prop.containsKey("srcIP"))) {
                                try {
                                    NetworkInterface ni = NetworkInterface.getByName(phys);
                                    String guessedAddr = null;
                                    for (Enumeration nien = ni.getInetAddresses(); nien.hasMoreElements();) {
                                        InetAddress ina = (InetAddress) nien.nextElement();
                                        if (ina instanceof Inet4Address) {
                                            // take it, if it's IPv4
                                            guessedAddr = ina.getHostAddress();
                                        } else if ((guessedAddr == null) && (ina instanceof Inet6Address)) {
                                            // if IPv6, take it only if there's no other address
                                            guessedAddr = ina.getHostAddress();
                                        }
                                    }
                                    srcIP = guessedAddr;
                                    logger.finer("Guessing srcIP=" + srcIP + " for phys=" + phys + " on " + linkName);
                                } catch (Throwable t) {
                                    logger.finer("On " + linkName + " cannot access local '" + phys
                                            + "' network interface: " + t.getMessage());
                                    srcIP = null;
                                }
                            }
                        }
                    } else {
                        phys = null;
                    }
                } else if (propName.equalsIgnoreCase("srcNode") || propName.equalsIgnoreCase("dstNode")) {
                    // just ignore it; these cannot change and are set while loading links configuration in LinkProtocol
                } else {
                    logger.warning("Unknown property for " + linkName + ": " + propName + ". Ignoring its value: "
                            + propValue);
                }
            }
            if (logger.isLoggable(Level.FINE)) {
                logger.info("After setting params: " + toString());
            }
        }
    }

    /**
     * Run the link control with a given parameter. Then it loads the params generated by the command.
     * @param op parameter to add to the command. For example 'start' or 'stop'.
     * @param log appends user logging info to this 
     */
    private void runLinkControlCommand(String op, StringBuilder log) {
        synchronized (syncRequests) {
            StringBuilder sbCmd = new StringBuilder();
            int idxSpc = srcCMD.indexOf(" ");
            if (idxSpc != -1) {
                // if the command has parameters, insert the operation (start/stop) just after the executable
                sbCmd.append(srcCMD.substring(0, idxSpc));
                sbCmd.append(" ").append(op).append(" ");
                sbCmd.append(srcCMD.substring(idxSpc + 1)).append(" ");
            } else {
                sbCmd.append(srcCMD).append(" ").append(op).append(" ");
            }
            sbCmd.append(TransferUtils.joinProperties(cmdParams, ' ', ' '));
            sbCmd.append(" 2>&1");
            String fullCmd = sbCmd.toString();
            fullCmd = fullCmd.replaceAll("%ip%", srcIP != null ? srcIP : "NONE");
            fullCmd = fullCmd.replaceAll("%id%", id != null ? id.toString() : "NONE");
            fullCmd = fullCmd.replaceAll("%phys%", phys != null ? phys : "NONE");
            fullCmd = fullCmd.replaceAll("%name%", linkBaseName);
            Process proc = null;
            cmdResParams.clear();
            try {
                proc = MLProcess.exec(fullCmd, LINK_CTRL_CMD_TIMEOUT);
                cmdResParams.load(proc.getInputStream());
            } catch (Exception ex) {
                log.append("-ERR While running link control command: ").append(fullCmd);
                log.append(" Got: ").append(ex.getMessage()).append("\n");
                logger.log(Level.WARNING, "Failed running link control command: " + fullCmd, ex);
            } finally {
                if (proc != null) {
                    proc.destroy();
                }
            }
            // the overall result of the command is set by setting link properties
            // in (de)activateLink there will be checks for the new status of parameters 
            // which are supposed to be set/unset by applying these properties
            setParams(cmdResParams);
        }
    }

    /**
     * Make sure that the link is active (it is enabled and has an IP). If it's not, then
     * try to activate it by running the control command.
     * @param log appends user logging info to this
     * @return whether the link is now active
     */
    private boolean activateLink(Properties params, StringBuilder log) {
        synchronized (syncRequests) {
            if (!enabled) {
                log.append("-ERR Link is not enabled (administratively down).\n");
                return false;
            }
            if (!active) {
                if (srcCMD == null) {
                    log.append("-ERR Link is not active AND doesn't have a command to activate it!\n");
                    return false;
                }
                cmdParams.clear();
                cmdParams.putAll(params);
                runLinkControlCommand("start", log);
                if (!active) {
                    log.append("-ERR Failed to activate the link.\n");
                    return false;
                }
            }
            log.append("+OK link active.\n");
            log.append("+OK link params: ").append(TransferUtils.joinProperties(cmdResParams, '=', '&')).append("\n");
            return true;
        }
    }

    /**
     * If the link can be deactivated (has a control command to manage the IP of srcNode), 
     * this command will be run. 
     * @param log appends user logging info to this 
     * @return whether the link is now inactive
     */
    private boolean deactivateLink(StringBuilder log) {
        synchronized (syncRequests) {
            if (enabled) { // link is administratively enabled
                if (active) { // and is active
                    if (srcCMD == null) {
                        log.append("-ERR Link is active but has no command to deactivate it!\n");
                        return false;
                    } // and has a command to deactivate it
                    runLinkControlCommand("stop", log);
                    if (active) {
                        log.append("-ERR Failed to deactivate the link.\n");
                        return false;
                    }
                }
            }
            log.append("+OK link not active.\n");
            log.append("+OK link params: ").append(TransferUtils.joinProperties(cmdResParams, '=', '&')).append("\n");
            return true;
        }
    }

    /**
     * Register a new request on this link.
     * @param requestID the request to be started
     * @param log appends user logging info to this
     * @return whether the request was started successfully  
     */
    public boolean startRequest(String requestID, int direction, Properties linkParams, StringBuilder log) {
        synchronized (syncRequests) {
            String logMsg;
            if (activateLink(linkParams, log)) {
                Hashtable htRequests = (direction == 1 ? htOutRequests : htInRequests);
                LinkRequestInstance lri = (LinkRequestInstance) htRequests.get(requestID);
                if (lri == null) {
                    lri = new LinkRequestInstance(requestID);
                    lri.start();
                    htRequests.put(requestID, lri);
                    logMsg = "Registering " + (direction == 1 ? "outgoing" : "incoming") + " request " + requestID;
                } else {
                    lri.keepAlive();
                    logMsg = "Request " + requestID + " already started. Keeping it alive.";
                }
                logger.fine(logMsg);
                log.append("+OK ").append(logMsg).append("\n");
                return true;
            }
            logMsg = "NOT registering request " + requestID + " since I cannot activate link: " + toString();
            logger.warning(logMsg);
            log.append("-ERR ").append(logMsg).append("\n");
            return false;
        }
    }

    /**
     * Stop an active request that is using this link. If there are no more requests, it 
     * @param requestID the request to stop.
     * @return whether the request was stopped successfully (and the link deactivated ok if it was the last) 
     */
    public boolean stopRequest(String requestID, StringBuilder log) {
        synchronized (syncRequests) {
            Hashtable htRequests = htInRequests.containsKey(requestID) ? htInRequests : htOutRequests;
            LinkRequestInstance lri = (LinkRequestInstance) htRequests.remove(requestID);
            if (lri == null) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Request " + requestID + " is already unregistered.");
                }
                log.append("-ERR Request ").append(requestID).append(" unknown/already unregistered!.\n");
                return false;
            }
            lri.stop();
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Unregistering request " + requestID);
            }
            log.append("+OK Request ").append(requestID).append(" unregistered.\n");
            if ((htInRequests.size() == 0) && (htOutRequests.size() == 0) && (srcCMD != null)) {
                return deactivateLink(log);
            }
            return true;
        }
    }

    public boolean keepAliveRequest(String requestID, StringBuilder log) {
        synchronized (syncRequests) {
            Hashtable htRequests = htInRequests.containsKey(requestID) ? htInRequests : htOutRequests;
            LinkRequestInstance lri = (LinkRequestInstance) htRequests.get(requestID);
            if (lri == null) {
                String msg = "Request " + requestID + " unknown or already unregistered!";
                log.append("-ERR ").append(msg).append("\n");
                logger.warning(msg);
                return false;
            }
            lri.keepAlive();
            log.append("+OK Keeping alive request " + requestID);
            return true;
        }
    }

    /**
     * Stop all requests in the given list
     * @param htRequests the requests list, one of htInRequests or htOutRequests
     */
    private void stopRequestsGroup(Hashtable htRequests) {
        for (Iterator rit = htRequests.values().iterator(); rit.hasNext();) {
            LinkRequestInstance lri = (LinkRequestInstance) rit.next();
            lri.stop();
        }
        htRequests.clear();
    }

    /**
     * The link is going to shutdown. Stop all the current requests and deactivate the link.
     */
    public void disableLink() {
        StringBuilder sbLogs = new StringBuilder();
        synchronized (syncRequests) {
            stopRequestsGroup(htInRequests);
            stopRequestsGroup(htOutRequests);
            if (srcCMD != null) {
                deactivateLink(sbLogs);
            }
            active = false;
            enabled = false;
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Link " + linkName + " disabled.\n" + sbLogs.toString());
        }
    }

    /**
     * Check the status for a group of requests (in/out)Requests 
     * @param htRequests
     * @param sbActiveList
     * @return true if removed anything
     */
    private boolean checkRequestsGroup(Hashtable htRequests, StringBuilder sbActiveList) {
        boolean removed = false;
        for (Iterator rit = htRequests.values().iterator(); rit.hasNext();) {
            LinkRequestInstance lri = (LinkRequestInstance) rit.next();
            if (!lri.checkStatus(sbActiveList)) {
                rit.remove(); // not active anymore; remove the request from the list
                removed = true;
            }
        }
        return removed;
    }

    /**
     * Check the link status and report its parameters with ApMon
     * @param apMon the apMon instance used to report this 
     */
    @Override
    public boolean checkStatus(List lResults) {
        StringBuilder sbInList = new StringBuilder();
        StringBuilder sbOutList = new StringBuilder();
        Result r;
        eResult er;
        synchronized (syncRequests) {
            boolean removedRequests = false;
            removedRequests |= checkRequestsGroup(htInRequests, sbInList);
            removedRequests |= checkRequestsGroup(htOutRequests, sbOutList);
            if ((htInRequests.size() == 0) && (htOutRequests.size() == 0) && active
                    && ((srcCMD != null) & removedRequests)) {
                StringBuilder sbLog = new StringBuilder();
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Link " + linkName + " not used. Trying to deactivate it.");
                }
                if (!deactivateLink(sbLog)) {
                    logger.warning("Failed deactivating link " + linkName + ":\n" + sbLog.toString());
                } else if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Link " + linkName + " deactivated successfully.");
                }
            }
            r = new Result(
                    TransferUtils.farmName,
                    clusterName,
                    linkName,
                    TransferUtils.resultsModuleName,
                    new String[] { "inReqCount", "outReqCount", "enabled", "active", "id", "bandwidth", "delay", "cost" },
                    new double[] { htInRequests.size(), htOutRequests.size(), enabled ? 1 : 0, active ? 1 : 0,
                            id != null ? id.intValue() : -1, bandwidth, delay, cost });
            er = new eResult(TransferUtils.farmName, clusterName, linkName, TransferUtils.resultsModuleName,
                    new String[] { "inRequests", "outRequests", "srcIP", "phys" });
            er.param = new Object[] { sbInList.toString(), sbOutList.toString(), srcIP != null ? srcIP : "",
                    phys != null ? phys : "" };
            r.time = er.time = NTPDate.currentTimeMillis();
        }
        lResults.add(r);
        lResults.add(er);
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("Status for: " + toString() + " inRequests: " + sbInList.toString() + " outRequests: "
                    + sbOutList.toString());
        }
        return true; // always success - links can be removed only by changing configuration
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("LinkInstance: ");
        sb.append("name=").append(linkName);
        sb.append(" srcNode=").append(srcNode);
        sb.append(" dstNode=").append(dstNode);
        sb.append(" enabled=").append(enabled);
        sb.append(" active=").append(active);
        sb.append(" srcIP=").append(srcIP);
        sb.append(" srcCMD=").append(srcCMD);
        sb.append(" phys=").append(phys);
        sb.append(" delay=").append(delay);
        sb.append(" bw=").append(TransferUtils.prettyBitsSpeed(bandwidth));
        sb.append(" cost=").append(cost);
        sb.append(" inReqCount=").append(htInRequests.size());
        sb.append(" outReqCount=").append(htOutRequests.size());
        return sb.toString();
    }

    @Override
    public boolean start() {
        // the links are started when (re-)loading configuration 
        return true;
    }

    @Override
    public boolean stop() {
        // called during the shutdown
        disableLink();
        return true;
    }
}
