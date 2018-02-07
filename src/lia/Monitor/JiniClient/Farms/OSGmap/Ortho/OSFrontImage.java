package lia.Monitor.JiniClient.Farms.OSGmap.Ortho;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.Hashtable;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Agents.OpticalPath.OpticalCrossConnectLink;
import lia.Monitor.Agents.OpticalPath.OpticalSwitchInfo;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwConfig;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwCrossConn;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwPort;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.Farms.OSGmap.OSPortComparator;
import lia.Monitor.monitor.GenericMLEntry;

public class OSFrontImage {
    /** The Logger */
    private static final Logger logger = Logger.getLogger(OSFrontImage.class.getName());

    protected static Hashtable port2link = new Hashtable();
    private static Hashtable colors = new Hashtable();
    private static Hashtable antiColors = new Hashtable();
    private static Object lock = new Object();

    protected static Color unconnectedColor = new Color(255, 0, 0, 160);

    /** A very dark red color. */
    public static final Color VERY_DARK_RED = new Color(0x80, 0x00, 0x00);

    /** A dark red color. */
    public static final Color DARK_RED = new Color(0xc0, 0x00, 0x00);

    /** A light red color. */
    public static final Color LIGHT_RED = new Color(0xFF, 0x40, 0x40);

    /** A very light red color. */
    public static final Color VERY_LIGHT_RED = new Color(0xFF, 0x80, 0x80);

    /** A very dark yellow color. */
    public static final Color VERY_DARK_YELLOW = new Color(0x80, 0x80, 0x00);

    /** A dark yellow color. */
    public static final Color DARK_YELLOW = new Color(0xC0, 0xC0, 0x00);

    /** A light yellow color. */
    public static final Color LIGHT_YELLOW = new Color(0xFF, 0xFF, 0x40);

    /** A very light yellow color. */
    public static final Color VERY_LIGHT_YELLOW = new Color(0xFF, 0xFF, 0x80);

    /** A very dark green color. */
    public static final Color VERY_DARK_GREEN = new Color(0x00, 0x80, 0x00);

    /** A dark green color. */
    public static final Color DARK_GREEN = new Color(0x00, 0xC0, 0x00);

    /** A light green color. */
    public static final Color LIGHT_GREEN = new Color(0x40, 0xFF, 0x40);

    /** A very light green color. */
    public static final Color VERY_LIGHT_GREEN = new Color(0x80, 0xFF, 0x80);

    /** A very dark cyan color. */
    public static final Color VERY_DARK_CYAN = new Color(0x00, 0x80, 0x80);

    /** A dark cyan color. */
    public static final Color DARK_CYAN = new Color(0x00, 0xC0, 0xC0);

    /** A light cyan color. */
    public static final Color LIGHT_CYAN = new Color(0x40, 0xFF, 0xFF);

    /** Aa very light cyan color. */
    public static final Color VERY_LIGHT_CYAN = new Color(0x80, 0xFF, 0xFF);

    /** A very dark blue color. */
    public static final Color VERY_DARK_BLUE = new Color(0x00, 0x00, 0x80);

    /** A dark blue color. */
    public static final Color DARK_BLUE = new Color(0x00, 0x00, 0xC0);

    /** A light blue color. */
    public static final Color LIGHT_BLUE = new Color(0x40, 0x40, 0xFF);

    /** A very light blue color. */
    public static final Color VERY_LIGHT_BLUE = new Color(0x80, 0x80, 0xFF);

    /** A very dark magenta/purple color. */
    public static final Color VERY_DARK_MAGENTA = new Color(0x80, 0x00, 0x80);

    /** A dark magenta color. */
    public static final Color DARK_MAGENTA = new Color(0xC0, 0x00, 0xC0);

    /** A light magenta color. */
    public static final Color LIGHT_MAGENTA = new Color(0xFF, 0x40, 0xFF);

    /** A very light magenta color. */
    public static final Color VERY_LIGHT_MAGENTA = new Color(0xFF, 0x80, 0xFF);

    private static Paint colorSupply[] = new Paint[] { new Color(36, 255, 0), new Color(16, 16, 214),
            new Color(255, 144, 62), new Color(16, 16, 214), new Color(164, 176, 255), new Color(234, 159, 255),
            new Color(255, 154, 154), VERY_LIGHT_RED, VERY_LIGHT_BLUE, VERY_LIGHT_GREEN, VERY_LIGHT_YELLOW,
            VERY_LIGHT_MAGENTA, VERY_LIGHT_CYAN, LIGHT_RED, LIGHT_BLUE, LIGHT_GREEN, LIGHT_YELLOW, LIGHT_MAGENTA,
            LIGHT_CYAN };
    private static Paint antiColorSupply[] = new Paint[] { new Color(14, 118, 22), new Color(82, 158, 244),
            new Color(168, 115, 13), new Color(82, 158, 244), new Color(255 - 164, 255 - 176, 255),
            new Color(255 - 234, 255 - 159, 255), new Color(255, 255 - 154, 255 - 154), VERY_DARK_RED, VERY_DARK_BLUE,
            VERY_DARK_GREEN, VERY_DARK_YELLOW, VERY_DARK_MAGENTA, VERY_DARK_CYAN, DARK_RED, DARK_BLUE, DARK_GREEN,
            DARK_YELLOW, DARK_MAGENTA, DARK_CYAN };

    static int colorIndex = 0;
    static Hashtable blinkMapping = new Hashtable();

    static OSFrontImageTimer thread = null;

    public static void setComponent(Component comp) {

        thread = new OSFrontImageTimer(comp, lock, blinkMapping);
        Thread tTh;
        tTh = new Thread(thread);
        tTh.setName("OSFrontImageTimer");
        tTh.start();
    }

    private static void drawOSLedsForOSI(rcNode node, Graphics2D g2, int startX, int startY, int width, int height,
            OpticalSwitchInfo osi) {
        if (osi.crossConnects == null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.warning("Node " + node.shortName + " doesn't received yet any crossconnects");
            }
            return;
        }

        TreeMap portNames = new TreeMap(new OSPortComparator());
        port2link.clear();
        int x = 0;
        int y = 0;
        int nr = 0;

        int portsW = 8;

        synchronized (lock) {
            for (Object element : osi.crossConnects.keySet()) {
                OpticalCrossConnectLink link = osi.crossConnects.get(element);
                if ((link == null) || (link.sPort == null) || (link.dPort == null)) {
                    continue;
                }
                if (!portNames.containsKey(link.dPort)) {
                    portNames.put(link.dPort, new Point(x, y));
                    port2link.put(new Point(x, y), link);
                    String key = node + "#" + link;
                    if (!blinkMapping.containsKey(key)) {
                        blinkMapping.put(key, new Boolean(Math.random() <= 0.5));
                    }
                    if (!thread.getRunning()) {
                        thread.start();
                    }
                    x++;
                    if (x == portsW) {
                        x = 0;
                        y++;
                    }
                    nr++;
                }
                if (!portNames.containsKey(link.sPort)) {
                    portNames.put(link.sPort, new Point(x, y));
                    port2link.put(new Point(x, y), link);
                    String key = node + "#" + link;
                    if (!blinkMapping.containsKey(key)) {
                        blinkMapping.put(key, new Boolean(Math.random() <= 0.5));
                    }
                    if (!thread.getRunning()) {
                        thread.start();
                    }
                    x++;
                    if (x == portsW) {
                        x = 0;
                        y++;
                    }
                    nr++;
                }
            }
            drawLeds(node, 16, g2, startX, startY, width, height);
        }
    }

    private static void drawOSLedsForOSI(rcNode node, Graphics2D g2, int startX, int startY, int width, int height,
            OSwConfig osi) {
        if (osi.crossConnects == null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.warning("Node " + node.shortName + " doesn't received yet any crossconnects");
            }
            return;
        }

        TreeMap portNames = new TreeMap(new OSPortComparator());
        port2link.clear();
        int x = 0;
        int y = 0;
        int nr = 0;

        int portsW = 8;

        synchronized (lock) {
            for (OSwCrossConn crossConnect : osi.crossConnects) {
                final OSwCrossConn link = crossConnect;
                if ((link.sPort.type == OSwPort.MULTICAST_PORT) || (link.dPort.type == OSwPort.MULTICAST_PORT)) {
                    continue;
                }
                if ((link == null) || (link.sPort == null) || (link.dPort == null)) {
                    continue;
                }
                if (!portNames.containsKey(link.dPort)) {
                    portNames.put(link.dPort, new Point(x, y));
                    port2link.put(new Point(x, y), link);
                    String key = node + "#" + link;
                    if (!blinkMapping.containsKey(key)) {
                        blinkMapping.put(key, new Boolean(Math.random() <= 0.5));
                    }
                    if (!thread.getRunning()) {
                        thread.start();
                    }
                    x++;
                    if (x == portsW) {
                        x = 0;
                        y++;
                    }
                    nr++;
                }
                if (!portNames.containsKey(link.sPort)) {
                    portNames.put(link.sPort, new Point(x, y));
                    port2link.put(new Point(x, y), link);
                    String key = node + "#" + link;
                    if (!blinkMapping.containsKey(key)) {
                        blinkMapping.put(key, new Boolean(Math.random() <= 0.5));
                    }
                    if (!thread.getRunning()) {
                        thread.start();
                    }
                    x++;
                    if (x == portsW) {
                        x = 0;
                        y++;
                    }
                    nr++;
                }
            }
            drawLeds(node, 16, g2, startX, startY, width, height);
        }
    }

    public static void drawOSLeds(rcNode node, Graphics2D g2, int startX, int startY, int width, int height) {

        if ((node == null) || (g2 == null)) {
            return;
        }
        OpticalSwitchInfo osi = node.getOpticalSwitchInfo(); // get the latest eResult value
        if (osi != null) {
            drawOSLedsForOSI(node, g2, startX, startY, width, height, osi);
            return;
        }
        OSwConfig newOsi = node.getOSwConfig();
        if (newOsi != null) {
            drawOSLedsForOSI(node, g2, startX, startY, width, height, newOsi);
            return;
        }

        // no eResult yet, get the default info
        osi = getOpticalSwitchInfo(node);
        if (osi != null) {
            drawOSLedsForOSI(node, g2, startX, startY, width, height, osi);
            return;
        }
        newOsi = getNewOpticalSwitchInfo(node);
        if (newOsi != null) {
            drawOSLedsForOSI(node, g2, startX, startY, width, height, newOsi);
            return;
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.warning("Node " + node.shortName + " doesn't received yet an opticalswitchinfo result");
        }

        synchronized (lock) {
            drawLeds(node, 16, g2, startX, startY, width, height);
        }
    }

    protected static void drawLeds(rcNode node, int nrPorts, Graphics2D g2, int startX, int startY, int width,
            int height) {

        colors.clear();
        antiColors.clear();
        colorIndex = 0;

        int portsW = nrPorts / 2;

        int gapW = 2;
        int gapH = 2;
        int w = 2 * (portsW + 1);
        int h = 4;
        if (w > width) {
            gapW = 1;
            w = portsW + 1;
        }
        if (w > width) {
            gapW = w = 0;
        }
        if (h > height) {
            gapH = 1;
            h = 3;
        }
        if (h > height) {
            gapH = h = 0;
        }

        int pw = (width - w) / portsW;
        int ph = (height - h) / 2;

        int diff = (width - (gapW * (portsW - 1)) - (pw * portsW)) / 2;
        if (diff < 0) {
            diff = 0;
        }
        startX += diff;

        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < portsW; j++) {
                Point p = new Point(j, i);
                Color c = null;
                if (port2link.containsKey(p)) {
                    final Object o = port2link.get(p);
                    if (o instanceof OpticalCrossConnectLink) {
                        OpticalCrossConnectLink link = (OpticalCrossConnectLink) port2link.get(p);
                        boolean blink = Math.random() <= 0.5;
                        String key = node + "#" + link;
                        if (blinkMapping.containsKey(key)) {
                            blink = ((Boolean) blinkMapping.get(key)).booleanValue();
                        }
                        if (colors.containsKey(link)) {
                            if (!blink) {
                                c = (Color) colors.get(link);
                            } else {
                                c = (Color) antiColors.get(link);
                            }
                        } else {
                            c = generateUniqueColor();
                            colors.put(link, c);
                            antiColors.put(link, getAntiColor(c));
                            if (blink) {
                                c = (Color) antiColors.get(link);
                            }
                        }
                    } else if (o instanceof OSwCrossConn) {
                        OSwCrossConn link = (OSwCrossConn) port2link.get(p);
                        boolean blink = Math.random() <= 0.5;
                        String key = node + "#" + link;
                        if (blinkMapping.containsKey(key)) {
                            blink = ((Boolean) blinkMapping.get(key)).booleanValue();
                        }
                        if (colors.containsKey(link)) {
                            if (!blink) {
                                c = (Color) colors.get(link);
                            } else {
                                c = (Color) antiColors.get(link);
                            }
                        } else {
                            c = generateUniqueColor();
                            colors.put(link, c);
                            antiColors.put(link, getAntiColor(c));
                            if (blink) {
                                c = (Color) antiColors.get(link);
                            }
                        }
                    }
                } else {
                    c = unconnectedColor;
                }
                // compute pozition
                int x = startX + (gapW * j) + (pw * j);
                int y = startY + 1 + (gapH * i) + (ph * i);
                // draw Led
                g2.setColor(c);
                g2.fillRect(x, y, pw, ph);
            }
        }
    }

    /**
     * Method that unwrap the OpticalSwitchInfo object from a node's gmle
     * @param node
     * @return
     */
    public static OpticalSwitchInfo getOpticalSwitchInfo(rcNode node) {

        if ((node == null) || (node.client == null)) {
            return null;
        }
        GenericMLEntry gmle = node.client.getGMLEntry();
        if (gmle == null) {
            return null;
        }
        byte array[] = (byte[]) gmle.hash.get("OSIConfig");
        if (array == null) {
            return null;
        }
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(array);
            ObjectInputStream ois = new ObjectInputStream(bais);
            OpticalSwitchInfo osi = (OpticalSwitchInfo) ois.readObject();
            return osi;
        } catch (Exception e) {
            return null;
        }
    }

    public static OSwConfig getNewOpticalSwitchInfo(rcNode node) {

        if ((node == null) || (node.client == null)) {
            return null;
        }
        GenericMLEntry gmle = node.client.getGMLEntry();
        if (gmle == null) {
            return null;
        }
        byte array[] = (byte[]) gmle.hash.get("OSIConfig");
        if (array == null) {
            return null;
        }
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(array);
            ObjectInputStream ois = new ObjectInputStream(bais);
            OSwConfig conf = (OSwConfig) ois.readObject();
            return conf;
        } catch (Exception e) {
            return null;
        }
    }

    protected static Color generateUniqueColor() {

        if (colorIndex == colorSupply.length) {
            colorIndex = 0;
        }
        return (Color) colorSupply[colorIndex++];
    }

    protected static Color getAntiColor(Color c) {

        for (int i = 0; i < colorSupply.length; i++) {
            if (c.equals(colorSupply[i])) {
                return (Color) antiColorSupply[i];
            }
        }
        return (Color) antiColorSupply[0];
    }

} // end of class OpticalFactory

