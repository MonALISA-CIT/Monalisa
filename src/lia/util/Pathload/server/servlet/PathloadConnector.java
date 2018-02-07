/**
 * 
 */
package lia.util.Pathload.server.servlet;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.Pathload.server.PathloadResponse;
import lia.util.Pathload.server.PeerInfo;
import lia.util.Pathload.server.Token;
import lia.util.Pathload.server.manager.ConfigurationManager;
import lia.util.Pathload.util.PathloadClient;
import lia.web.utils.MailDate;
import lia.web.utils.ThreadedPage;

/**
 * The Pathload Connector binds the Pathload Configuration 
 * Application on one side and monPathload on the other.
 * [CODE REVIEW]  
 *  
 * @author heri
 *
 */
public class PathloadConnector extends ThreadedPage {

    /**
     * Required SerialVersion
     */
    private static final long serialVersionUID = -5589039190156159691L;

    /**
     * Logging component
     */
    private static final Logger logger = Logger.getLogger(PathloadConnector.class.getName());

    /** 
     * @see lia.web.utils.ThreadedPage#doInit()
     */
    @Override
    public void doInit() {
        response.setHeader("Expires", "0");
        response.setHeader("Last-Modified", (new MailDate(new Date())).toMailString());
        response.setHeader("Cache-Control", "no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setContentType("text/html");
    }

    /** 
     * @see lia.web.utils.ThreadedPage#execGet()
     */
    @Override
    public void execGet() {
        String ver = "";
        String hostname = "", ipAddress = "";
        String testIpAddress;
        String farmName = "", action = "";
        String[] farmGroups = null;
        long startRequest = System.currentTimeMillis();

        PathloadResponse pr = new PathloadResponse();

        try {
            if (!haveParametersToProcess()) {
                throw new IllegalArgumentException("HTML Request contains no parameters.");
            }

            ver = request.getParameter("ver");
            hostname = request.getParameter("hostname");
            ipAddress = request.getParameter("ipAddress");
            testIpAddress = InetAddress.getByName(ipAddress).getHostAddress();
            if ((testIpAddress == null) || (!testIpAddress.equalsIgnoreCase(ipAddress))) {
                throw new IllegalArgumentException("Could not parse your ip (" + ipAddress + ") correctly.");
            }
            if (!isPublicAddress(testIpAddress)) {
                throw new IllegalArgumentException("I will not accept your IP Address " + "(" + ipAddress
                        + ") because it is private.");
            }
            farmName = request.getParameter("farmName");

            farmGroups = request.getParameterValues("farmGroups");
            if (farmGroups == null) {
                farmGroups = new String[1];
                farmGroups[0] = "test";
            }
            action = request.getParameter("action");

            if ((ver == null) || (hostname == null) || (ipAddress == null) || (farmName == null)
                    || (farmGroups == null)) {
                throw new IllegalArgumentException("Could not parse all POST Data parameters correctly: " + "[ver: "
                        + ver + "]" + "[hostname: " + hostname + "]" + "[ipAddress: " + ipAddress + "]" + "[farmName: "
                        + farmName + "]");
            }
            PeerInfo p = new PeerInfo(hostname, ipAddress, farmName, farmGroups);

            int iAction = getActionFromString(action);
            ConfigurationManager cm = ConfigurationManager.getInstance();

            boolean statusMessage = false;
            switch (iAction) {
            case PathloadClient.ACTION_GET_TOKEN:
                statusMessage = pr.addToken(cm.getToken(p));
                pr.addGroup(cm.getGroupOfPeer(p));
                break;
            case PathloadClient.ACTION_RELEASE_TOKEN:
                statusMessage = cm.releaseToken(new Token(request.getParameter("ID")));
                pr.addGroup(cm.getGroupOfPeer(p));
                break;
            case PathloadClient.ACTION_REFRESH:
                statusMessage = cm.refresh(p);
                pr.addGroup(cm.getGroupOfPeer(p));
                if (!statusMessage) {
                    pr.addError("Peer is not part of cache or token is not his.");
                }
                break;
            case PathloadClient.ACTION_SHUTDOWN:
                statusMessage = cm.shutdown(p);
                break;
            default:
                throw new IllegalArgumentException("Unknown action id: " + iAction);
            }
            pr.addStatusMessage(statusMessage);
        } catch (IllegalArgumentException e) {
            pr.addError(e.getMessage());
        } catch (UnknownHostException e) {
            pr.addError(e.getMessage());
        } catch (NullPointerException e) {
            pr.addError(e.getMessage());
        }

        if (logger.isLoggable(Level.FINE)) {
            long now = System.currentTimeMillis();
            StringBuilder sb = new StringBuilder();
            sb.append("RequestInfo: ");
            sb.append(" action = " + action);
            sb.append(" duration = " + ((now - startRequest) / 1000) + " sec");
            sb.append(" requestIp = " + hostname + "/" + ipAddress);
            sb.append(" requestFarm = " + farmName);
            sb.append(" requestVersion = " + ver);
            sb.append(" tokenId = " + ((request.getParameter("ID") != null) ? request.getParameter("ID") : "No Token"));
            sb.append("\n");
            if (logger.isLoggable(Level.FINEST)) {
                sb.append("ResponseInfo: ");
                sb.append(pr.toString());
                sb.append("\n");
            }
            logger.log(Level.FINE, sb.toString());
        }

        pwOut.println(pr.toString());

        bAuthOK = true;
    }

    /**
     * Check if request has parameters to commit. (IsPostBack)
     * 
     * @return	True if parameters are available
     */
    private boolean haveParametersToProcess() {
        return !request.getParameterMap().isEmpty();
    }

    /**
     * Transform an action name to a int value. Action names and values
     * are defined in PathloadResponse. 
     * 
     * @param action	Action name
     * @return			Int value
     */
    private int getActionFromString(String action) {
        int result = PathloadClient.ACTION_UNKNOWN;
        if (action == null) {
            return PathloadClient.ACTION_UNKNOWN;
        }

        if (action.equals(PathloadClient.ACTION_NAME_GET_TOKEN)) {
            result = PathloadClient.ACTION_GET_TOKEN;
        } else if (action.equals(PathloadClient.ACTION_NAME_REFRESH)) {
            result = PathloadClient.ACTION_REFRESH;
        } else if (action.equals(PathloadClient.ACTION_NAME_RELEASE_TOKEN)) {
            result = PathloadClient.ACTION_RELEASE_TOKEN;
        } else if (action.equals(PathloadClient.ACTION_NAME_SHUTDOWN)) {
            result = PathloadClient.ACTION_SHUTDOWN;
        }

        return result;
    }

    /**
     * Test and see if the given ipAddress is in the private Address
     * Space.
     * 
     * @param ipAddress	IP Address in String form.
     * @return	True if it's in public range, false otherwise.
     */
    private static boolean isPublicAddress(String ipAddress) {
        int octet1, octet2, octet3;
        boolean bResult = true;

        if (ipAddress == null) {
            return false;
        }
        try {
            String[] octets = ipAddress.split("\\.");
            if ((octets == null) || (octets.length != 4)) {
                return false;
            }

            octet1 = Integer.parseInt(octets[0]);
            octet2 = Integer.parseInt(octets[1]);
            octet3 = Integer.parseInt(octets[2]);

            if ((octet1 == 10) || (octet1 == 127)) {
                bResult = false;
            }
            if ((octet1 == 172) && ((octet2 > 15) && (octet3 < 32))) {
                bResult = false;
            }
            if ((octet1 == 192) && (octet2 > 167)) {
                bResult = false;
            }
        } catch (NoSuchElementException e) {
            bResult = false;
        } catch (NullPointerException e) {
            bResult = false;
        } catch (NumberFormatException e) {
            bResult = false;
        }

        return bResult;
    }
}
