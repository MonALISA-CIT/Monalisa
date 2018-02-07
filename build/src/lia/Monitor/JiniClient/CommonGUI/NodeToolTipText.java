package lia.Monitor.JiniClient.CommonGUI;

import java.net.InetAddress;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.ntp.NTPDate;

public class NodeToolTipText {
    private static final Logger logger = Logger.getLogger(NodeToolTipText.class.getName());

    protected static Hashtable hostCache = new Hashtable();

    /**
     * check if a string value is valid to be included in the tooltip text
     * @param val the parameter value
     * @return if the value is not null, not n/a, not empty
     */
    protected static boolean isValid(String val) {
        if (val == null) {
            return false;
        }
        if (val.indexOf("N/A") >= 0) {
            return false;
        }
        if (val.equals(" ")) {
            return false;
        }
        return true;
    }

    /**
     * get a tool tip text for a node
     * @param node the rcNode
     * @return the tooltip text
     */
    public static String getToolTip(rcNode node) {
        if (node == null) {
            return "";
        }
        String text = node.UnitName + " ";
        if ((node.mlentry != null) && isValid(node.mlentry.Location)) {
            text += node.mlentry.Location;
        }
        if (isValid(node.LONG) && isValid(node.LAT)) {
            text += "[" + (node.LAT.startsWith("-") ? (node.LAT.substring(1) + "S") : (node.LAT + "N")) + ","
                    + (node.LONG.startsWith("-") ? (node.LONG.substring(1) + "W") : (node.LONG + "E")) + "]";
        }
        if ((node.mlentry != null) && isValid(node.mlentry.Country)) {
            text += "/" + node.mlentry.Country;
        }
        if (isValid(node.IPaddress)) {
            String hostname = IpAddrCache.getHostName(node.IPaddress, false);
            text += (hostname != null ? " " + hostname : "") + " [" + node.IPaddress + "]";
        }
        //		double val = nodesGroup.getNodeValue(node);
        //		if(val != -1)
        //		  text += "  [" + optPan.cbNodeOpts.getSelectedItem() + " = "
        //			   + optPan.csNodes.formatter.format(val) + " "
        //			   + optPan.csNodes.units + "]";
        return text;
    }

    /**
     * return a short version of the tool tip for smaller available space
     * @param node the rcNode
     * @return the sort tooltip text
     */
    public static String getShortToolTip(rcNode node) {
        if (node == null) {
            return "";
        }
        String text = node.UnitName + ": ";
        if ((node.mlentry != null) && isValid(node.mlentry.Location)) {
            text += node.mlentry.Location;
        }
        if ((node.mlentry != null) && isValid(node.mlentry.Country)) {
            text += ", " + node.mlentry.Country;
        }
        if ((node.mlentry != null) && (node.mlentry.Group != null) && isValid(node.mlentry.Group)) {
            text += " / " + node.mlentry.Group;
        }
        return text;
    }

    /**
     * maintains a cache with hostnames for ip addresses used in some
     * tool tip texts
     * @param IPaddr the ipaddress
     * @return the hostname string
     */
    protected static String getHostName(String IPaddr) {
        String name = (String) hostCache.get(IPaddr);
        long startTime, endTime;
        startTime = NTPDate.currentTimeMillis();
        if (name == null) {
            try {
                name = InetAddress.getByName(IPaddr).getHostName();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Cannot find hostname for IP addr: " + IPaddr);
                name = "";
            }
            hostCache.put(IPaddr, name);
        }
        endTime = NTPDate.currentTimeMillis();
        return name + " (found in " + (endTime - startTime) + "ms)";
    }
}
