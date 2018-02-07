package lia.Monitor.Farm.Transfer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Support for handling the Link Protocol. This is not effectively transferring files!
 * It keeps track and manages the status of the existing links between network nodes and the 
 * other transfer requests which are using them.
 * 
 * This works in the following way:
 *  - Before issuing a new transfer request (either FDT, RSV or something else), a link request
 *    should be issued to this protocol to indicate which link will be used. If the link is not
 *    active, the configured command will be run to initialize it at the first usage.
 *  - After a request is finished, a link request should be issued to this as well, to indicate
 *    this fact. If there is no other active transfer a given command can be run to deactivate the link.
 *  - This protocol will continuously report the existing links between nodes, their status
 *    and the list of transfer requests using each of them.
 *  - In order to be consistent, link requests must be issued to all network segments that are
 *    used in a end-to-end path.
 *    
 * Reporting the available links' status this way should allow having a distributed transfer
 * scheduler.
 * 
 * Config parameters:
 * link.<<baseName>>.srcNode=sourceNodeName	// Mandatory. Current ML service name or virtual node name
 * link.<<baseName>>.dstNode=destNodeName	// Mandatory. Other ML service name or virtual node name
 * link.<<baseName>>.enabled=true|false		// Optional. Administrative status of the link. Default: true
 * link.<<baseName>>.active=true|false		// Optional. Link is ready to be used. Default: true
 * link.<<baseName>>.bw=xxxx[B/K/M/G/T]		// Optional. Link capacity in [B/K/M/G/T]bits/s. Default is 1tbps.
 * link.<<baseName>>.phys=interfaceName		// Optional. Name of the physical interface used for this (logical) link.
 * link.<<baseName>>.delay=xxxx				// Optional. Link delay in millis. Default is 0.
 * link.<<baseName>>.cost=xxxx				// Optional. Link cost. Default is 0.
 * link.<<baseName>>.id={x|x,y,z|x-y]		// Optional. Circuit ID; can be for example the vlanid or smth else.
 * link.<<baseName>>.srcIP=x.y.z.t			// Optional. IP address of the source node interface
 * link.<<baseName>>.srcCMD=/path/to/script	// Optional. If link not active, it will be activated running this script. 
 * 
 * TODO: to decide if the links usage should be refreshed periodically.
 * 
 * @author catac
 */
public class LinkProtocol extends TransferProtocol {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(LinkProtocol.class.getName());

    /**
     * Initialize the Reservation Transfer Protocol
     * @param appTransfer the AppTransfer that created this protocol.
     */
    public LinkProtocol() {
        super("link");
    }

    @Override
    public String startInstance(Properties props) {
        StringBuilder sbRes = new StringBuilder("-ERR Failed to start link request. ");
        String requestID = props.getProperty("requestID");
        String linkName = props.getProperty("link");
        String srcNode = props.getProperty("srcNode");
        String dstNode = props.getProperty("dstNode");

        if (requestID == null) {
            sbRes.append("requestID missing");
        } else if (linkName == null) {
            sbRes.append("link missing");
        } else if (srcNode == null) {
            sbRes.append("srcNode missing");
        } else if (dstNode == null) {
            sbRes.append("dstNode missing");
        } else {
            LinkInstance li = (LinkInstance) htInstances.get(linkName);
            if (li == null) {
                sbRes.append("link '").append(linkName).append("' not existing in config.");
            } else {
                int direction = li.getDirection(srcNode, dstNode);
                if (direction == 0) {
                    sbRes.append("Given srcNode and dstNode don't match link's endNodes.");
                } else {
                    StringBuilder log = new StringBuilder();
                    if (li.startRequest(requestID, direction, props, log)) {
                        sbRes.setLength(0);
                    } else {
                        sbRes.append("\n");
                    }
                    sbRes.append(log);
                }
            }
        }
        return sbRes.toString();
    }

    @Override
    public String stopInstance(Properties props) {
        StringBuilder sbRes = new StringBuilder("-ERR Failed to stop link request. ");
        String requestID = props.getProperty("requestID");
        String linkName = props.getProperty("link");
        if (requestID == null) {
            sbRes.append("requestID missing");
        } else if (linkName == null) {
            sbRes.append("link missing");
        } else {
            LinkInstance li = (LinkInstance) htInstances.get(linkName);
            if (li == null) {
                sbRes.append("link '").append(linkName).append("' not existing in config.");
            } else {
                StringBuilder log = new StringBuilder();
                if (li.stopRequest(requestID, log)) {
                    sbRes.setLength(0); // reset the failed message
                } else {
                    sbRes.append("\n");
                }
                sbRes.append(log);
            }
        }
        return sbRes.toString();
    }

    @Override
    public void updateConfig() {
        HashMap hmLinkParams = new HashMap();
        HashMap hmLinkIDs = new HashMap();
        // get the properties for each of the defined links and the linkIDs for each link base name 
        for (Object element : config.entrySet()) {
            Map.Entry me = (Map.Entry) element;
            String key = (String) me.getKey();
            String propValue = (String) me.getValue();
            int idx = key.indexOf('.');
            if (idx <= 0) {
                logger.warning("Invalid link definition. Ignoring property: " + key + "=" + propValue);
                continue;
            }
            String linkBaseName = key.substring(0, idx);
            String linkProp = key.substring(idx + 1);
            Properties props = (Properties) hmLinkParams.get(linkBaseName);
            if (props == null) {
                props = new Properties();
                hmLinkParams.put(linkBaseName, props);
            }
            if (linkProp.equals("id")) {
                hmLinkIDs.put(linkBaseName, TransferUtils.parseSequence(propValue));
            } else {
                props.setProperty(linkProp, propValue);
            }
        }
        // create the non-existing links and update existing ones
        for (Iterator meit = hmLinkParams.entrySet().iterator(); meit.hasNext();) {
            Map.Entry me = (Map.Entry) meit.next();
            String linkBaseName = (String) me.getKey();
            Properties linkParams = (Properties) me.getValue();
            String srcNode = linkParams.getProperty("srcNode");
            String dstNode = linkParams.getProperty("dstNode");
            if ((srcNode == null) || (dstNode == null)) {
                logger.warning("Cannot create base link " + linkBaseName + " srcNode=" + srcNode + " and dstNode="
                        + dstNode);
                continue;
            }
            List linkIDs = (List) hmLinkIDs.get(linkBaseName);
            if (linkIDs == null) {
                linkIDs = new ArrayList();
                linkIDs.add(""); // link doesn't have an id property. Create a dummy element to identify the link
            }
            for (Iterator idit = linkIDs.iterator(); idit.hasNext();) {
                Object id = idit.next();
                String linkName = linkBaseName;
                if (id instanceof Integer) {
                    linkName += "-" + id;
                } else {
                    id = null;
                }
                LinkInstance li = (LinkInstance) htInstances.get(linkName);
                if (li == null) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("Creating link " + linkName + " from " + srcNode + " to " + dstNode);
                    }
                    li = new LinkInstance(linkBaseName, srcNode, dstNode, (Integer) id);
                    htInstances.put(linkName, li);
                }
                li.setParams(linkParams);
            }
        }
        // remove links that no longer exist
        for (Iterator lit = htInstances.values().iterator(); lit.hasNext();) {
            LinkInstance li = (LinkInstance) lit.next();
            String linkBaseName = li.getLinkBaseName();
            List linkIDs = (List) hmLinkIDs.get(linkBaseName);
            if ((hmLinkParams.get(linkBaseName) == null // link removed completely,
                    )
                    || ((linkIDs == null) && (li.getCircuitID() != null)) // all its IDs were removed, and it was a ID-based link
                    || ((linkIDs != null) && (!linkIDs.contains(li.getCircuitID())))) { // OR this ID was removed
                // link has to be disabled and removed
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Disabling link " + li.getLinkName() + " from " + li.getSrcNode() + " to "
                            + li.getDstNode());
                }
                li.disableLink();
                li.checkStatus(bvMonitorResults);
                lit.remove();
            }
        }
    }

    /** Add support for the keepAlive command in this protocol */
    @Override
    public String execCommand(String sCmd, Properties props) {
        if (sCmd.equals("keepAlive")) {
            StringBuilder sbRes = new StringBuilder("-ERR Failed to keepAlive link request. ");
            String requestID = props.getProperty("requestID");
            String linkName = props.getProperty("link");

            if (requestID == null) {
                sbRes.append("requestID missing");
            } else if (linkName == null) {
                sbRes.append("link missing");
            } else {
                LinkInstance li = (LinkInstance) htInstances.get(linkName);
                if (li == null) {
                    sbRes.append("link '").append(linkName).append("' not existing in config.");
                } else {
                    StringBuilder log = new StringBuilder();
                    if (li.keepAliveRequest(requestID, log)) {
                        sbRes.setLength(0);
                    } else {
                        sbRes.append("\n");
                    }
                    sbRes.append(log);
                }
            }
            return sbRes.toString();
        }
        return super.execCommand(sCmd, props);
    }

    @Override
    public String getProtocolUsage() {
        StringBuilder sb = new StringBuilder("LinkProtocol:\n");
        sb.append("link start&requestID=string&link=linkName&srcNode=nodeName&dstNode=nodeName[&param=value&...]\n");
        sb.append("\tregister the new request, on the given link, in the given direction (src->dst), and pass the following parameters\n");
        sb.append("link stop&requestID=string&link=linkName[&param=value&...]\n");
        sb.append("\tunregister the request, from the given link, and pass the following parameters\n");
        sb.append("link keepAlive&requestID=string&link=linkName\n");
        sb.append("\tkeep alive the request on the given link\n");
        sb.append("link help\n");
        sb.append("\treturn this help");
        sb.append("Parameters:\n");
        sb.append("\trequestID\t-a string representing the request ID\n");
        sb.append("\tinkName\t-a string, the name of the link, must be one from the config\n");
        sb.append("\tparam\t-other parameters, passed as they are, to the command to setup/destroy the link");
        return sb.toString();
    }
}
