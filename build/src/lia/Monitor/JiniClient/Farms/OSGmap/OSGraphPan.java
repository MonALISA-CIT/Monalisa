package lia.Monitor.JiniClient.Farms.OSGmap;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import lia.Monitor.Agents.OpticalPath.OSPort;
import lia.Monitor.Agents.OpticalPath.OpticalCrossConnectLink;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwCrossConn;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwPort;
import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.DLink;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GeographicLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphTopology;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GridLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.LayeredTreeLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.LayoutChangedListener;
import lia.Monitor.JiniClient.CommonGUI.Gmap.LayoutTransformer;
import lia.Monitor.JiniClient.CommonGUI.Gmap.NoLayoutLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.RadialLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.RandomLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.SpringLayoutAlgorithm;
import lia.Monitor.JiniClient.Farms.FarmsSerMonitor;
import lia.Monitor.JiniClient.Farms.OSGmap.Config.OSMonitorControl;
import lia.Monitor.JiniClient.Farms.OSGmap.Ortho.OSFrontImage;
import lia.Monitor.JiniClient.Farms.OSGmap.Ortho.OpticalConnectivityToolTip;
import lia.Monitor.monitor.GenericMLEntry;
import lia.Monitor.monitor.OSLink;
import lia.Monitor.tcpClient.tClient;
import net.jini.core.lookup.ServiceID;

public class OSGraphPan extends JPanel implements MouseListener, MouseMotionListener, LayoutChangedListener,
        ActionListener {

    /**
     * 
     */
    private static final long serialVersionUID = -8535492530508675114L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(OSGraphPan.class.getName());

    volatile Map<ServiceID, rcNode> GlobalNodes = new Hashtable();//snodes
    Hashtable nodes = new Hashtable();//local nodes
    public Vector lLinkToFakeNode = new Vector();//reference from link to fake node, contains Object[2], fake node is on first position, link on second 
    public Vector maxFlow;

    public static final float fLayoutMargin = 0.02f;

    public boolean selectionMode = false; // flag active when the user is in SelectionMode (can select OSs for creating an optical path)
    public boolean allPathMode = false, singleEndPointsMode = true, multipleEndPointsMode = false;
    public boolean fdxPath = true;
    public Vector selectedNodes = new Vector();
    public boolean linkSelectionMode = false;
    public String linkSelected = null;
    public String linkSelectedDetails = null;
    final Cursor selectableCursor = createSelectableCursor();
    final Cursor unselectableCursor = createUnselectableCursor();
    final Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);

    private OSLink currentSelectedLink = null;
    private final Hashtable currentSelectedLinkPoz = new Hashtable();

    final Color linkSelectionColor = new Color(15, 191, 126);
    boolean haveLinkWithID = false;

    static BufferedImage nodeShadow;
    static BufferedImage fakeShadow;
    static ConvolveOp blurOp;
    final static Color shadowColor = new Color(0.0f, 0.0f, 0.0f, 0.3f);

    /**
     * checks to see if object is containd into lLinkToFakeNode array list,
     * using the type parameter to know whom to compare with
     * @param type
     * @param obj
     */
    public Object[] LTFNcontains(int type, Object obj) {

        synchronized (getTreeLock()) {
            Object con[];
            for (int i = 0; i < lLinkToFakeNode.size(); i++) {
                con = (Object[]) lLinkToFakeNode.get(i);
                if (con[type].equals(obj)) {
                    return con;
                }
            }
            return null;
        }
    }

    rcNode pick;
    rcNode pickX;

    OpticalConnectivityToolTip OSConToolTip = new OpticalConnectivityToolTip();
    private final BufferedImage osImage;//optical switch image
    private final BufferedImage glowOSImage;
    private final BufferedImage grayosImage;
    private int osImgWidth = 106;//set at loading iamge moment
    private int osImgHeight = 32;//set at loading iamge moment

    SerMonitorBase monitor;
    OSGmapPan gmapPan;
    Color bgColor = Color.WHITE; //new Color(230, 230, 250);
    static Font osFont = new Font("Arial", Font.BOLD, 12);
    static Font nodesFont = new Font("Arial", Font.PLAIN, 10);
    Color clHiPing = new Color(255, 100, 100);
    int wunit = 0;
    int hunit = 0;
    int wunit_fake = 0;
    int hunit_fake = 0;
    String pieKey = "noLoadPie";
    Rectangle range = new Rectangle();
    Rectangle actualRange = new Rectangle();

    //	private DecimalFormat lblFormat = new DecimalFormat("###,###.#");

    // layout & stuff
    GraphLayoutAlgorithm layout = null;
    GraphTopology vGraph = null;
    Object syncGraphObj = new Object();
    LayoutTransformer layoutTransformer;
    String currentLayout = "None";
    boolean currentTransformCancelled = false;
    boolean showOnlyConnectedNodes;

    private final Vector links = new Vector();
    private final Vector oslinks = new Vector();
    private String linkToolTip = null;
    Vector participatingRCNodes = new Vector();

    private static OSGraphPan _instance = null;

    public static synchronized final OSGraphPan getInstance(OSGmapPan gmapPan) {
        if (_instance == null) {
            _instance = new OSGraphPan(gmapPan);
        }
        return _instance;
    }

    public static synchronized final OSGraphPan getInstance() {
        return _instance;
    }

    private OSGraphPan(OSGmapPan gmapPan) {
        this.gmapPan = gmapPan;
        //load image to represent optical switches and get its dimensions
        osImage = loadBuffImage("lia/images/so.gif");
        grayosImage = loadBuffImage("lia/images/so_gray.gif");
        glowOSImage = loadGlowBuffImage("lia/images/so.gif");
        osImgWidth = osImage.getWidth();
        osImgHeight = osImage.getHeight();
        if (osImage != null) {
            nodeShadow = new BufferedImage(osImgWidth + 14, osImgHeight + 14, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = nodeShadow.createGraphics();
            g.drawImage(osImage, null, 6, 6);
            g.dispose();
            blurOp = getBlurOp(7);
            blurOp.filter(createShadowMask(osImage), nodeShadow);
        }

        addMouseListener(this);
        addMouseMotionListener(this);
        setBackground(bgColor);
        TimerTask ttask = new LayoutUpdateTimerTask(gmapPan);
        BackgroundWorker.schedule(ttask, 4000, 4000);
        layoutTransformer = new LayoutTransformer(this);

        setToolTipText("");

        //creates a reference to this panel to be used by leds panel in tooltip
        OSFrontImage.setComponent(this);
    }

    private ConvolveOp getBlurOp(int size) {
        float[] data = new float[size * size];
        float value = 1 / (float) (size * size);
        for (int i = 0; i < data.length; i++) {
            data[i] = value;
        }
        return new ConvolveOp(new Kernel(size, size, data));
    }

    private BufferedImage createShadowMask(BufferedImage image) {
        BufferedImage mask = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int argb = image.getRGB(x, y);
                argb = ((int) (((argb >> 24) & 0xFF) * 0.5) << 24) | (shadowColor.getRGB() & 0x00FFFFFF);
                mask.setRGB(x, y, argb);
            }
        }
        return mask;
    }

    private void createFakeShadow(int w, int h) {

        fakeShadow = new BufferedImage(w + 14, h + 14, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = fakeShadow.createGraphics();
        g.setColor(Color.cyan);
        g.fillOval(6, 6, w, h);
        blurOp.filter(createShadowMask(fakeShadow), fakeShadow);
    }

    class LayoutUpdateTimerTask extends TimerTask {
        Component parent;

        public LayoutUpdateTimerTask(Component parent) {
            this.parent = parent;
        }

        @Override
        public void run() {
            Thread.currentThread().setName(
                    " ( ML ) - OSGMap - OSGraphPan Layout Update Timer Thread: checks stop condition and sets layout.");
            if (shouldStop()) {
                this.cancel();
                return;
            }
            ;
            try {
                if ((parent != null) && parent.isVisible()) {
                    if (!currentLayout.equals("SomeLayout")) {
                        setLayoutType(currentLayout);
                    }
                    repaint();
                }
                ;
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error executing", t);
            }
        }
    };

    /**
     * loads a image and returns a reference to the loaded object
     * @param imageFileName
     * @return loaded image or null
     */
    public static BufferedImage loadBuffImage(String imageFileName) {
        BufferedImage res = null;
        try {
            if (imageFileName == null) {
                return null;
            }
            ClassLoader myClassLoader = OSGraphPan.class.getClassLoader();
            URL imageURL;
            if (imageFileName.indexOf("://") >= 0) {
                imageURL = new URL(imageFileName);
            } else {
                imageURL = myClassLoader.getResource(imageFileName);
            }
            ImageIcon icon = new ImageIcon(imageURL);//imageFileName);//imageURL);
            if (icon.getImageLoadStatus() != MediaTracker.COMPLETE) {
                throw new Exception("failed");
            }
            Component obs = new Component() {
            };
            int width = icon.getIconWidth();
            int height = icon.getIconHeight();
            res = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D big = res.createGraphics();
            icon.paintIcon(obs, big, 0, 0);
            big.dispose();
        } catch (Exception e) {
            res = null;
            //System.out.println("\nFailed loading image from "+imageFileName);
            //e.printStackTrace();
        }
        return res;
    }

    /**
     * loads a image and returns a reference to the loaded object
     * @param imageFileName
     * @return loaded image or null
     */
    public static BufferedImage loadGlowBuffImage(String imageFileName) {
        BufferedImage res = null;
        try {
            if (imageFileName == null) {
                return null;
            }
            ClassLoader myClassLoader = OSGraphPan.class.getClassLoader();
            URL imageURL;
            if (imageFileName.indexOf("://") >= 0) {
                imageURL = new URL(imageFileName);
            } else {
                imageURL = myClassLoader.getResource(imageFileName);
            }
            ImageIcon icon = new ImageIcon(imageURL);//imageFileName);//imageURL);
            if (icon.getImageLoadStatus() != MediaTracker.COMPLETE) {
                throw new Exception("failed");
            }
            Component obs = new Component() {
            };
            int width = icon.getIconWidth();
            int height = icon.getIconHeight();
            res = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D big = res.createGraphics();
            icon.paintIcon(obs, big, 0, 0);
            big.dispose();
        } catch (Exception e) {
            res = null;
            //System.out.println("\nFailed loading image from "+imageFileName);
            //e.printStackTrace();
        }
        int midX = res.getWidth(null) / 2;
        int midY = res.getHeight(null) / 2;
        int max = midX;
        if (max > midY) {
            max = midY;
        }
        if (res != null) {
            for (int i = 0; i < res.getWidth(null); i++) {
                for (int j = 0; j < res.getHeight(null); j++) {
                    int argb = res.getRGB(i, j);
                    int red = (argb >> 16) & 0xff;
                    int green = (argb >> 8) & 0xff;
                    int blue = argb & 0xff;
                    int alpha = (argb >> 24) & 0xff;
                    double dist = Math.sqrt(((i - midX) * (i - midX)) + ((j - midY) * (j - midY))) / max;
                    red = (int) (red * (1 + (0.2 * dist)));
                    green = (int) (green * (1 + (0.2 * dist)));
                    blue = (int) (blue * (1 + (0.2 * dist)));
                    if (red < 0) {
                        red = 0;
                    }
                    if (red > 255) {
                        red = 255;
                    }
                    if (green < 0) {
                        green = 0;
                    }
                    if (green > 255) {
                        green = 255;
                    }
                    if (blue < 0) {
                        blue = 0;
                    }
                    if (blue > 255) {
                        blue = 255;
                    }
                    argb = (alpha << 24) | (red << 16) | (green << 8) | blue;
                    res.setRGB(i, j, argb);
                }
            }
        }
        return res;
    }

    public void setNodesTab(Map<ServiceID, rcNode> snodes) {
        this.GlobalNodes = snodes;
    }

    /**
     * convert a string to a double value. If there would be an exception
     * return the failsafe value
     * @param value initial value
     * @param failsafe failsafe value
     * @return final value
     */
    public static float failsafeParseFloat(String value, float failsafe) {
        try {
            return Float.parseFloat(value);
        } catch (Throwable t) {
            return failsafe;
        }
    }

    private rcNode createFakeNode(rcNode node, OSLink hlink) {

        /**
         * create a fake node, put it in local nodes and save its connection to fake link 
         */
        rcNode fakeNode = new rcNode();
        fakeNode.wconn = new Hashtable();
        fakeNode.conn = new Hashtable();
        fakeNode.UnitName = hlink.szDestName;
        //fakeNode.setShortName();//not usefull because it cuts last digits from ip adress
        fakeNode.shortName = hlink.szDestName;
        fakeNode.x = node.x;
        fakeNode.y = node.y;
        fakeNode.fakeSid = node.sid;
        fakeNode.fixed = false;
        fakeNode.selected = false;
        float coord;
        float MAX_LONG_DEPL = 10;
        float MIN_LONG_DEPL = 4;
        float MAX_LAT_DEPL = 6;
        float MIN_LAT_DEPL = 2;
        coord = (MAX_LONG_DEPL - MIN_LONG_DEPL) * (float) (Math.random() - .5);
        if (coord < 0) {
            coord -= MIN_LONG_DEPL;
        } else {
            coord += MIN_LONG_DEPL;
        }
        coord += failsafeParseFloat(node.LONG, -111.15f);
        if (coord < -180) {
            coord = -180;
        }
        if (coord > 180) {
            coord = 180;
        }
        fakeNode.LONG = "" + coord;
        coord = (MAX_LAT_DEPL - MIN_LAT_DEPL) * (float) (Math.random() - .5);
        if (coord < 0) {
            coord -= MIN_LAT_DEPL;
        } else {
            coord += MIN_LAT_DEPL;
        }
        coord += failsafeParseFloat(node.LAT, -21.22f);
        if (coord < -90) {
            coord = -90;
        }
        if (coord > 90) {
            coord = 90;
        }
        fakeNode.LAT = "" + coord;

        int deplX = (int) (40 * Math.random());
        if (deplX > 0) {
            fakeNode.x += deplX + 20;
        } else {
            fakeNode.x += deplX - 20;
        }
        int deplY = (int) (30 * Math.random());
        if (deplY > 0) {
            fakeNode.y += deplY + 20;
        } else {
            fakeNode.y += deplY - 20;
        }
        fakeNode.IPaddress = hlink.szDestName;
        fakeNode.isLayoutHandled = true;
        return fakeNode;
    }

    /**
     * for a given node, checks to see if it has fake links,
     * and, if the case, checkes to see if that type of link is already treated in local cache (lLinkToFakeNode),
     * or, adds it setting its sid to the parent sid and its position (parent) and name (based on link's destination). 
     * @param node
     */
    private void addFakes(rcNode node) {

        synchronized (getTreeLock()) {
            OSLink hlink;
            for (Enumeration it2 = node.wconn.keys(); it2.hasMoreElements();) {
                Object key = it2.nextElement();
                if (key == null) {
                    continue;
                }
                Object objLink = node.wconn.get(key);
                if (objLink == null) {
                    continue;
                }
                if (objLink instanceof OSLink) {
                    hlink = (OSLink) objLink;
                    if (!hlink.checkFlag(OSLink.OSLINK_FLAG_FAKE_NODE)) {
                        continue; // not interested if  not fake
                    }
                    boolean added = false;
                    for (int i = 0; i < lLinkToFakeNode.size(); i++) {
                        Object[] con = (Object[]) lLinkToFakeNode.get(i);
                        if (con[3].equals(node)) {
                            rcNode fake = (rcNode) con[0];
                            if ((fake != null) && (fake.UnitName != null) && (hlink != null)
                                    && (hlink.szDestName != null) && fake.UnitName.equals(hlink.szDestName)) {
                                added = true;
                                lLinkToFakeNode.remove(i);
                                hlink.rcSource = node;
                                hlink.rcDest = fake;
                                if (hlink.szSourcePort != null) {
                                    if (hlink.szSourcePort.type.shortValue() == OSPort.OUTPUT_PORT) {
                                        lLinkToFakeNode.add(i, new Object[] { con[0], hlink, con[2], con[3] });
                                    } else {
                                        lLinkToFakeNode.add(i, new Object[] { con[0], con[1], hlink, con[3] });
                                    }
                                } else if (hlink.szSourceNewPort != null) {
                                    if (hlink.szSourceNewPort.type == OSwPort.OUTPUT_PORT) {
                                        lLinkToFakeNode.add(i, new Object[] { con[0], hlink, con[2], con[3] });
                                    } else {
                                        lLinkToFakeNode.add(i, new Object[] { con[0], con[1], hlink, con[3] });
                                    }
                                }
                                break;
                            }
                        }
                    }
                    if (!added) {
                        rcNode fake = createFakeNode(node, hlink);
                        hlink.rcDest = fake;
                        if (hlink.szSourcePort != null) {
                            if (hlink.szSourcePort.type.shortValue() == OSPort.OUTPUT_PORT) {
                                lLinkToFakeNode.add(new Object[] { fake, hlink, null, node });
                            } else {
                                lLinkToFakeNode.add(new Object[] { fake, null, hlink, node });
                            }
                        } else if (hlink.szSourceNewPort != null) {
                            if (hlink.szSourceNewPort.type == OSwPort.OUTPUT_PORT) {
                                lLinkToFakeNode.add(new Object[] { fake, hlink, null, node });
                            } else {
                                lLinkToFakeNode.add(new Object[] { fake, null, hlink, node });
                            }
                        }
                        nodes.put(new Object(), fake);
                    }
                }
            }
        }
    }

    /**
     * removes fake nodes based on hLinkToNode list
     * by checking the validity of node or link (node could still be ok, but not link..)
     *
     */
    private void removeFakes(rcNode rcnode) {

        synchronized (getTreeLock()) {
            Object[] con;
            rcNode node, rnode;
            OSLink ilink, olink;

            for (int i = 0; i < lLinkToFakeNode.size(); i++) {
                con = (Object[]) lLinkToFakeNode.get(i);
                rcNode n = (rcNode) con[3];
                if (n.equals(rcnode)) {
                    lLinkToFakeNode.remove(i);
                    i--;
                    Vector v = new Vector();
                    for (Enumeration en = nodes.keys(); en.hasMoreElements();) {
                        Object o = en.nextElement();
                        if (o instanceof ServiceID) {
                            continue;
                        }
                        rcNode n1 = (rcNode) nodes.get(o);
                        if ((n1.fakeSid != null) && n1.fakeSid.equals(n.sid)) {
                            v.add(o);
                        }
                    }
                    for (Iterator it = v.iterator(); it.hasNext();) {
                        nodes.remove(it.next());
                    }
                }
            }

            //remove fake nodes if real nodes with same sid not in GlobalNodes any more or link from node does not exist
            //from lLinkToFakeNode list
            //also remove fake link
            for (int k = 0; k < lLinkToFakeNode.size(); k++) {
                con = (Object[]) lLinkToFakeNode.get(k);
                node = (rcNode) con[0];
                ilink = (OSLink) con[1];
                olink = (OSLink) con[2];
                rnode = (rcNode) con[3];
                if (ilink != null) {
                    boolean contains = false;
                    for (Enumeration en = rnode.wconn.keys(); en.hasMoreElements();) {
                        Object key = en.nextElement();
                        if (key == null) {
                            continue;
                        }
                        Object val = rnode.wconn.get(key);
                        if (val == null) {
                            continue;
                        }
                        if (ilink.equals(val)) {
                            contains = true;
                            break;
                        }
                    }
                    if (!contains) {
                        for (int i = 0; i < lLinkToFakeNode.size(); i++) {
                            Object[] obj = (Object[]) lLinkToFakeNode.get(i);
                            if ((obj[3] != null) && obj[3].equals(rnode) && (obj[0] != null)
                                    && (((rcNode) obj[0]).UnitName != null)
                                    && ((rcNode) obj[0]).UnitName.equals(node.UnitName)) {
                                lLinkToFakeNode.remove(i);
                                lLinkToFakeNode.add(i, new Object[] { obj[0], null, obj[2], obj[3] });
                                break;
                            }
                        }
                    }
                }
                if (olink != null) {
                    boolean contains = false;
                    for (Enumeration en = rnode.wconn.keys(); en.hasMoreElements();) {
                        Object key = en.nextElement();
                        if (key == null) {
                            continue;
                        }
                        Object obj = rnode.wconn.get(key);
                        if (obj == null) {
                            continue;
                        }
                        if (olink.equals(obj)) {
                            contains = true;
                            break;
                        }
                    }
                    if (!contains) {
                        for (int i = 0; i < lLinkToFakeNode.size(); i++) {
                            Object[] obj = (Object[]) lLinkToFakeNode.get(i);
                            if ((obj[3] != null) && obj[3].equals(rnode) && (obj[0] != null)
                                    && (((rcNode) obj[0]).UnitName != null)
                                    && ((rcNode) obj[0]).UnitName.equals(node.UnitName)) {
                                lLinkToFakeNode.remove(i);
                                lLinkToFakeNode.add(i, new Object[] { obj[0], obj[1], null, obj[3] });
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * synchronizes local view of nodes set with global one by adding and removing nodes
     *
     */
    public void syncNodes() {

        synchronized (getTreeLock()) {
            if (GlobalNodes == null) {
                Object key;
                rcNode node;
                Vector v = new Vector();
                //remove old real nodes from local if not in global
                for (Enumeration en = nodes.keys(); en.hasMoreElements();) {
                    key = en.nextElement();
                    node = (rcNode) nodes.get(key);
                    if (key instanceof ServiceID) { //identify a real node
                        v.add(key);
                        if (OSConToolTip != null) {
                            OSConToolTip.removeCheck(node);
                        }
                    }
                }
                for (int i = 0; i < v.size(); i++) {
                    rcNode n = (rcNode) nodes.remove(v.get(i));
                    removeFakes(n);
                }
                return;
            }
            // remove old nodes
            Vector v = new Vector();
            for (Enumeration en = nodes.keys(); en.hasMoreElements();) {
                Object key = en.nextElement();
                if (!(key instanceof ServiceID)) {
                    continue; // only real nodes
                }
                rcNode n = (rcNode) nodes.get(key);

                if (!GlobalNodes.containsKey(key)) {
                    v.add(key);
                    if (OSConToolTip != null) {
                        OSConToolTip.removeCheck(n);
                    }
                } else {
                    rcNode n1 = GlobalNodes.get(key);
                    if (!n1.equals(n)) {
                        v.add(key);
                        if (OSConToolTip != null) {
                            OSConToolTip.removeCheck(n);
                        }
                    }
                }
            }
            for (int i = 0; i < v.size(); i++) {
                rcNode nn = (rcNode) nodes.remove(v.get(i));
                removeFakes(nn);
            }
            recheckFakes();
            //add new nodes from global to local, 
            //creating, if neccessary, new fakes nodes for a newly added real one
            for (final rcNode n : GlobalNodes.values()) {
                if ((n != null) && (n.mlentry != null) && (n.mlentry.Group != null)
                        && tClient.isOSgroup(n.mlentry.Group)) {
                    if (n.client != null) {
                        GenericMLEntry gmle = n.client.getGMLEntry();
                        if ((gmle.hash != null) && !gmle.hash.containsKey("OS_PortMap")) {
                            continue;
                        }
                    }

                    if (!nodes.containsKey(n.sid)) {
                        nodes.put(n.sid, n);
                        if (OSConToolTip != null) {
                            OSConToolTip.checkNode(n);
                        }
                    } else {
                        rcNode n1 = (rcNode) nodes.get(n.sid);
                        if (!n1.equals(n)) {
                            nodes.put(n.sid, n);
                            if (OSConToolTip != null) {
                                OSConToolTip.checkNode(n);
                            }
                        }
                    }
                    addFakes(n);
                }
            }
        }
    }

    private void recheckFakes() {

        for (int i = 0; i < lLinkToFakeNode.size(); i++) {
            Object[] con = (Object[]) lLinkToFakeNode.get(i);
            rcNode realNode = (rcNode) con[3];
            if ((realNode == null) || !nodes.containsKey(realNode.sid)) {
                lLinkToFakeNode.remove(i);
                i--;
                continue;
            }
            rcNode n = (rcNode) nodes.get(realNode.sid);
            if (!n.equals(realNode)) {
                lLinkToFakeNode.remove(i);
                i--;
                continue;
            }
        }
    }

    public void setSerMonitor(SerMonitorBase monitor) {
        this.monitor = monitor;
    }

    public void setMaxFlow(rcNode n) {
        // even if n == null, call it
        setMaxFlowData(n, ((FarmsSerMonitor) monitor).getMaxFlow(n));
    }

    public void setMaxFlowData(rcNode n, Vector v) {
        maxFlow = v;
    }

    public boolean isHotLink(rcNode n1, rcNode n2) {
        if (maxFlow == null) {
            return false;
        }
        for (int i = 0; i < maxFlow.size(); i++) {
            DLink dl = (DLink) maxFlow.elementAt(i);
            if (dl.cequals(n1.sid, n2.sid)) {
                return true;
            }
        }
        return false;
    }

    public void paintFakeNode(Graphics g, rcNode n, FontMetrics fm) {

        if (n.shortName == null) {
            return;
        }
        int w2 = (fm.stringWidth(n.shortName) + 15) / 2;
        int h2 = (fm.getHeight() + 10) / 2;
        boolean changed = false;
        if (w2 > wunit_fake) {
            wunit_fake = w2;
            changed = true;
        }
        if (h2 > hunit_fake) {
            hunit_fake = h2;
            changed = true;
        }
        if (w2 > wunit) {
            wunit = w2;
        }
        if (h2 > hunit) {
            hunit = h2;
        }
        if (n.limits == null) {
            n.limits = new Rectangle(n.x - wunit_fake, n.y - hunit_fake, wunit_fake * 2, hunit_fake * 2);
        } else {
            n.limits.setBounds(n.x - wunit_fake, n.y - hunit_fake, wunit_fake * 2, hunit_fake * 2);
        }

        if (changed) {
            createFakeShadow(wunit_fake * 2, hunit_fake * 2);
        }
        if (gmapPan.kbShowShadow.isSelected()) {
            ((Graphics2D) g).drawImage(fakeShadow, blurOp, n.limits.x, n.limits.y);
        }

        if (participatingRCNodes.contains(n)) {
            g.setColor(OSLink.OSLINK_COLOR_TRANSFERING_ML);
        } else {
            g.setColor(Color.cyan);
        }
        g.fillOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
        g.setColor(Color.black);
        if (n.equals(pickX)) {
            g.setColor(Color.red);
        }
        g.drawOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
        if (selectedNodes.contains(n)) {
            g.setColor(Color.red);
            Graphics2D g2 = (Graphics2D) g;
            BasicStroke b = (BasicStroke) g2.getStroke();
            BasicStroke stroke = new BasicStroke(2.0f, b.getEndCap(), b.getLineJoin(), b.getMiterLimit(), new float[] {
                    16.0f, 4.0f }, // Dash pattern
                    b.getDashPhase());
            g2.setStroke(stroke);
            g.drawRect(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
            g2.setStroke(b);
        }
        g.setColor(Color.BLACK);
        if (n.equals(pickX)) {
            g.setColor(Color.red);
        }
        g.drawString(n.shortName, (n.limits.x + 7 + wunit_fake) - w2,
                ((n.limits.y + 5 + hunit_fake) - h2) + fm.getAscent());

        // also possible draw the link ports...
        if (currentSelectedLinkPoz.containsKey(n)) {
            final Object o[] = (Object[]) currentSelectedLinkPoz.get(n);
            paintPort((Graphics2D) g, ((Integer) o[0]).intValue(), ((Integer) o[1]).intValue(), (String) o[2], fm,
                    (Color) o[3], (Color) o[4]);
        }
    }

    public void paintNode(Graphics g, rcNode n, FontMetrics fm) {

        int x = n.x;
        int y = n.y;
        String nodeName = n.szOpticalSwitch_Name == null ? n.shortName : n.szOpticalSwitch_Name;
        //System.out.println("painting "+n.UnitName+" "+n.x+" "+n.y);

        int w2, h2;
        w2 = osImgWidth / 2;
        if (w2 > wunit) {
            wunit = w2;
        }
        h2 = osImgHeight / 2;
        if (h2 > hunit) {
            hunit = h2;
        }
        w2 = (fm.stringWidth(nodeName) + 15) / 2;
        h2 = (fm.getHeight() + 10) / 2;
        if (w2 > wunit) {
            wunit = w2;
        }
        if (h2 > hunit) {
            hunit = h2;
        }
        if (n.limits == null) {
            n.limits = new Rectangle(x - wunit, y - hunit, wunit * 2, hunit * 2);
        } else {
            n.limits.setBounds(x - wunit, y - hunit, wunit * 2, hunit * 2);
        }
        //draw new OS
        if (gmapPan.kbShowShadow.isSelected()) {
            ((Graphics2D) g).drawImage(nodeShadow, blurOp, x - (osImgWidth / 2), y - (osImgHeight / 2));
        }
        if (participatingRCNodes.contains(n)) {
            g.drawImage(glowOSImage, x - (osImgWidth / 2), y - (osImgHeight / 2), null);
        } else {
            g.drawImage(osImage, x - (osImgWidth / 2), y - (osImgHeight / 2), null);
        }
        if (selectedNodes.contains(n)) {
            g.setColor(Color.red);
            Graphics2D g2 = (Graphics2D) g;
            BasicStroke b = (BasicStroke) g2.getStroke();
            BasicStroke stroke = new BasicStroke(2.0f, b.getEndCap(), b.getLineJoin(), b.getMiterLimit(), new float[] {
                    16.0f, 4.0f }, // Dash pattern
                    b.getDashPhase());
            g2.setStroke(stroke);
            g.drawRect(x - (osImgWidth / 2), y - (osImgHeight / 2), osImgWidth, osImgHeight);
            g2.setStroke(b);
        }
        OSFrontImage.drawOSLeds(n, (Graphics2D) g, n.limits.x + 12, n.limits.y + 19, 70, 10);

        g.setColor(Color.white);
        g.drawString(nodeName, (n.limits.x + 7 + wunit) - w2 - 1, (n.limits.y + fm.getAscent()) - 1/*+ hunit - h2 + fm.getAscent()*/);
        g.drawString(nodeName, ((n.limits.x + 7 + wunit) - w2) + 1, n.limits.y + fm.getAscent() + 1/*+ hunit - h2 + fm.getAscent()*/);
        g.setColor(new Color(0, 0, 77));
        if (n.equals(pickX)) {
            g.setColor(Color.red);
        }
        g.drawString(nodeName, (n.limits.x + 7 + wunit) - w2, n.limits.y + fm.getAscent() /*+ hunit - h2 + fm.getAscent()*/);

        // also possible draw the link ports...
        if (currentSelectedLinkPoz.containsKey(n)) {
            final Object o[] = (Object[]) currentSelectedLinkPoz.get(n);
            paintPort((Graphics2D) g, ((Integer) o[0]).intValue(), ((Integer) o[1]).intValue(), (String) o[2], fm,
                    (Color) o[3], (Color) o[4]);
        }
    }

    private void paintPort(Graphics2D g2, int x, int y, String portName, FontMetrics fm, Color fiberColor,
            Color stateColor) {

        if (portName == null) {
            return;
        }

        int w = fm.stringWidth(portName);
        int h = fm.getHeight() + 4;

        x = x - (w / 2);
        y = y - (h / 2);

        g2.setColor(stateColor);
        g2.fillRect(x + 1, y + 1, w + 8, h - 2);
        g2.setColor(fiberColor.brighter());
        g2.drawLine(x, y, x + w + 10, y);
        g2.drawLine(x, y + 1, x + w + 9, y + 1);
        g2.drawLine(x, y + 2, x + w + 8, y + 2);
        g2.drawLine(x, y, x, y + h);
        g2.drawLine(x + 1, y, x + 1, (y + h) - 1);
        g2.drawLine(x + 2, y, x + 2, (y + h) - 2);
        g2.setColor(fiberColor.darker());
        g2.drawLine(x + w + 8, y + 2, x + w + 8, y + h);
        g2.drawLine(x + w + 9, y + 1, x + w + 9, y + h);
        g2.drawLine(x + w + 10, y, x + w + 10, y + h);
        g2.drawLine(x + 2, (y + h) - 2, x + w + 10, (y + h) - 2);
        g2.drawLine(x + 1, (y + h) - 1, x + w + 10, (y + h) - 1);
        g2.drawLine(x, y + h, x + w + 10, y + h);
        g2.setColor(Color.black);
        g2.drawString(portName, ((x + ((w + 10) / 2)) - (w / 2)) + 1,
                (y + (h / 2) + (g2.getFontMetrics().getHeight() / 2)) - 1);
    }

    public void paintNoNode(Graphics g, rcNode n, FontMetrics fm) {

        int x = n.x;
        int y = n.y;
        String nodeName = n.szOpticalSwitch_Name == null ? n.shortName : n.szOpticalSwitch_Name;
        //System.out.println("painting "+n.UnitName+" "+n.x+" "+n.y);

        int w2, h2;
        w2 = osImgWidth / 2;
        if (w2 > wunit) {
            wunit = w2;
        }
        h2 = osImgHeight / 2;
        if (h2 > hunit) {
            hunit = h2;
        }
        w2 = (fm.stringWidth(nodeName) + 15) / 2;
        h2 = (fm.getHeight() + 10) / 2;
        if (w2 > wunit) {
            wunit = w2;
        }
        if (h2 > hunit) {
            hunit = h2;
        }
        if (n.limits == null) {
            n.limits = new Rectangle(x - wunit, y - hunit, wunit * 2, hunit * 2);
        } else {
            n.limits.setBounds(x - wunit, y - hunit, wunit * 2, hunit * 2);
        }

        //draw new OS
        if (gmapPan.kbShowShadow.isSelected()) {
            ((Graphics2D) g).drawImage(nodeShadow, blurOp, x - (osImgWidth / 2), y - (osImgHeight / 2));
        }
        g.drawImage(grayosImage, x - (osImgWidth / 2), y - (osImgHeight / 2), null);

        g.setColor(Color.white);
        g.drawString(nodeName, (n.limits.x + 7 + wunit) - w2 - 1, (n.limits.y + fm.getAscent()) - 1/*+ hunit - h2 + fm.getAscent()*/);
        g.drawString(nodeName, ((n.limits.x + 7 + wunit) - w2) + 1, n.limits.y + fm.getAscent() + 1/*+ hunit - h2 + fm.getAscent()*/);
        g.setColor(new Color(0, 0, 77));
        if (n.equals(pickX)) {
            g.setColor(Color.red);
        }
        g.drawString(nodeName, (n.limits.x + 7 + wunit) - w2, n.limits.y + fm.getAscent() /*+ hunit - h2 + fm.getAscent()*/);

        // also possible draw the link ports...
        if (currentSelectedLinkPoz.containsKey(n)) {
            final Object o[] = (Object[]) currentSelectedLinkPoz.get(n);
            paintPort((Graphics2D) g, ((Integer) o[0]).intValue(), ((Integer) o[1]).intValue(), (String) o[2], fm,
                    (Color) o[3], (Color) o[4]);
        }
    }

    protected void drawConn(Graphics gg, OSLink link, String lbl, Color col, int nOrder) {

        rcNode source = null, dest = null;
        String portSource = null, portDest = null;
        Color sourceCol = col, destCol = col;

        if (link.szSourcePort != null) { // type one sw
            if (link.szSourcePort.type.shortValue() == OSPort.OUTPUT_PORT) {
                source = link.rcSource;
                dest = link.rcDest;
                portSource = link.szSourcePort.name;
                portDest = link.szDestPort;
            } else {
                source = link.rcDest;
                dest = link.rcSource;
                portSource = link.szDestPort;
                portDest = link.szSourcePort.name;
            }
        } else if (link.szSourceNewPort != null) { // type new sw
            if (link.szSourceNewPort.type == OSwPort.OUTPUT_PORT) {
                source = link.rcSource;
                dest = link.rcDest;
                portSource = link.szSourceNewPort.name;
                portDest = link.szDestPort;
                // now find the dest port...
                try {
                    if ((dest.getOSwConfig() != null) && (dest.getOSwConfig().osPorts != null)) {
                        for (OSwPort p : dest.getOSwConfig().osPorts) {
                            if ((p.type == OSwPort.INPUT_PORT) && p.name.equals(portDest)) {
                                destCol = link.getPortColor(p.fiberState, p.powerState, link.linkID);
                                break;
                            }
                        }
                    }
                } catch (Throwable t) {
                }
            } else {
                source = link.rcDest;
                dest = link.rcSource;
                portSource = link.szDestPort;
                portDest = link.szSourceNewPort.name;
                // now find the source port....
                try {
                    if ((source.getOSwConfig() != null) && (source.getOSwConfig().osPorts != null)) {
                        for (OSwPort p : source.getOSwConfig().osPorts) {
                            if ((p.type == OSwPort.OUTPUT_PORT) && p.name.equals(portSource)) {
                                sourceCol = link.getPortColor(p.fiberState, p.powerState, link.linkID);
                                break;
                            }
                        }
                    }
                } catch (Throwable t) {
                }
            }
        }

        boolean showPorts = ((currentSelectedLink != null) && link.equals(currentSelectedLink));

        final int xSrc = source.x;
        final int ySrc = source.y;
        final int xDst = dest.x;
        final int yDst = dest.y;

        final int DD = 6;
        int dd;
        if (nOrder == 0) {
            dd = DD;
        } else {
            dd = 4 * nOrder;
        }

        gg.setColor(col);

        int dx = xDst - xSrc;
        int dy = yDst - ySrc;
        float l = (float) Math.sqrt((dx * dx) + (dy * dy));
        float dir_x = dx / l;
        float dir_y = dy / l;
        int dd1 = dd;

        int x1p = xSrc - (int) (dd1 * dir_y);
        int x2p = xDst - (int) (dd1 * dir_y);
        int y1p = ySrc + (int) (dd1 * dir_x);
        int y2p = yDst + (int) (dd1 * dir_x);

        if (showPorts) {
            final float difx = x1p - x2p;
            final float dify = y1p - y2p;
            double angle = (difx == 0 ? Math.PI / 2 : Math.atan(Math.abs(dify) / Math.abs(difx)));
            if ((difx * dify) < 0) {
                angle *= -1.0;
            }
            if (dify < 0) {
                angle += Math.PI;
            }
            if ((difx * dify) < 0) {
                angle -= Math.PI;
            }
            angle += Math.PI;
            final float x1o = (float) (x1p + (15 * Math.cos(angle)));
            final float y1o = (float) (y1p + (15 * Math.sin(angle)));
            angle += Math.PI; // just reverse the angle
            final float x2o = (float) (x2p + (15 * Math.cos(angle)));
            final float y2o = (float) (y2p + (15 * Math.sin(angle)));
            currentSelectedLinkPoz.put(source, new Object[] { Integer.valueOf((int) x1o), Integer.valueOf((int) y1o),
                    portSource, (link.checkState(OSLink.STATE_DISCONNECTED) ? Color.BLACK : Color.GRAY), sourceCol });
            currentSelectedLinkPoz.put(dest, new Object[] { Integer.valueOf((int) x2o), Integer.valueOf((int) y2o),
                    portDest, (link.checkState(OSLink.STATE_DISCONNECTED) ? Color.BLACK : Color.GRAY), destCol });
        }

        Link ll = new Link(x1p, y1p, x2p, y2p, link.linkID, link);
        links.add(ll);
        if (link.linkID != null) {
            haveLinkWithID = true;
        }

        if ((linkSelected != null) && (link.linkID != null) && linkSelected.equals(link.linkID)) {
            col = linkSelectionColor;
        }

        if (col.equals(OSLink.OSLINK_COLOR_DISCONNECTED) || col.equals(OSLink.OSLINK_COLOR_FREE_2)
                || col.equals(OSLink.OSLINK_COLOR_FAIL)) {
            Graphics2D g2 = (Graphics2D) gg;
            BasicStroke b = (BasicStroke) g2.getStroke();
            BasicStroke stroke = new BasicStroke((showPorts ? b.getLineWidth() + 1.0f : b.getLineWidth()),
                    b.getEndCap(), b.getLineJoin(), b.getMiterLimit(), new float[] { 16.0f, 4.0f }, // Dash pattern
                    b.getDashPhase());
            g2.setStroke(stroke);
            gg.drawLine(x1p, y1p, x2p, y2p);
            g2.setStroke(b);
        } else if ((linkSelected != null) && (link.linkID != null) && linkSelected.equals(link.linkID)) {
            Graphics2D g2 = (Graphics2D) gg;
            BasicStroke b = (BasicStroke) g2.getStroke();
            BasicStroke stroke = new BasicStroke((showPorts ? 3.0f : 2.0f), b.getEndCap(), b.getLineJoin(),
                    b.getMiterLimit(), new float[] { 16.0f, 4.0f }, // Dash pattern
                    b.getDashPhase());
            g2.setStroke(stroke);
            gg.drawLine(x1p, y1p, x2p, y2p);
            g2.setStroke(b);
        } else {
            if (showPorts) {
                Graphics2D g2 = (Graphics2D) gg;
                BasicStroke b = (BasicStroke) g2.getStroke();
                BasicStroke stroke = new BasicStroke(b.getLineWidth() + 1.0f);
                g2.setStroke(stroke);
                gg.drawLine(x1p, y1p, x2p, y2p);
                g2.setStroke(b);
            } else {
                gg.drawLine(x1p, y1p, x2p, y2p);
            }
        }

        int xv = (int) ((x1p + x2p + (((x1p - x2p) * (float) nOrder) / 20.0f)) / 2.0f);
        int yv = (int) ((y1p + y2p + (((y1p - y2p) * (float) nOrder) / 20.0f)) / 2.0f);

        float aa;//(float) (dd) / (float) 2.0;
        if (nOrder == 0) {
            aa = dd / 2.0f;
        } else {
            aa = 2.0f;
        }

        int[] axv = { (int) ((xv - (aa * dir_x)) + (2 * aa * dir_y)), (int) (xv - (aa * dir_x) - (2 * aa * dir_y)),
                (int) (xv + (2 * aa * dir_x)), (int) ((xv - (aa * dir_x)) + (2 * aa * dir_y)) };

        int[] ayv = { (int) (yv - (aa * dir_y) - (2 * aa * dir_x)), (int) ((yv - (aa * dir_y)) + (2 * aa * dir_x)),
                (int) (yv + (2 * aa * dir_y)), (int) (yv - (aa * dir_y) - (2 * aa * dir_x)) };

        gg.fillPolygon(axv, ayv, 4);

        if ((lbl != null) && (lbl.length() != 0)) {
            //int ddl = 6;
            // String lbl = "" + (int) perf ;
            FontMetrics fm = gg.getFontMetrics();
            int wl = fm.stringWidth(lbl) + 1;
            int hl = fm.getHeight() + 1;

            int off = 2;

            int xl = xv;
            int yl = yv;

            if ((dir_x >= 0) && (dir_y < 0)) {
                xl = xl + off;
                yl = yl + hl;
            }
            if ((dir_x <= 0) && (dir_y > 0)) {
                xl = xl - wl - off;
                yl = yl - off;
            }

            if ((dir_x > 0) && (dir_y >= 0)) {
                xl = xl - wl - off;
                yl = yl + hl;
            }
            if ((dir_x < 0) && (dir_y < 0)) {
                xl = xl + off;
                yl = yl - off;
            }

            gg.drawString(lbl, xl, yl);
        }
        gg.setColor(Color.black);
    }

    /**
     * decides if this panel should stop: be removed from menu
     * @return
     */
    boolean shouldStop() {
        //System.out.println("Should stop function call.");
        //first sync the nodes list with global one
        syncNodes();
        //then check to see if this panel should be removed
        if (nodes.size() == 0) {
            //System.out.println("nodes size is 0");
            //if no nodes any more, remove the panel from left menu
            long currentTime = System.currentTimeMillis();
            if (startTimeOut == -1) {
                startTimeOut = currentTime;
            } else {
                if ((currentTime - startTimeOut) >= TIME_OUT) {
                    //the maximum waiting time has passed, so, remove the panel
                    monitor.main.removeGraphical("OS GMap");
                    //monitor.main.jpMenu.pack();
                    return true;
                }
            }
        } else {
            startTimeOut = -1;
        }
        return false;
    }

    private long startTimeOut = -1;//first time when panel has no nodes
    private static final long TIME_OUT = 4000;//time in miliseconds before removing this panel in inactive state

    @Override
    public void paintComponent(Graphics g) {

        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                gmapPan.kbMakeNice.isSelected() ? RenderingHints.VALUE_ANTIALIAS_ON
                        : RenderingHints.VALUE_ANTIALIAS_OFF);

        synchronized (links) {
            links.clear();
            oslinks.clear();
            haveLinkWithID = false;
        }
        Dimension d = getSize();
        g.setColor(bgColor);
        g.setFont(osFont);
        FontMetrics fmOS = g.getFontMetrics();
        g.setFont(nodesFont);
        g.fillRect(0, 0, d.width, d.height);
        FontMetrics fm = g.getFontMetrics();

        //this also calls syncNodes so no need to have it called from paint
        shouldStop();
        if (nodes.size() == 0) {
            //if no nodes to be drawn, show a small message
            g.setColor(Color.red);
            String szNoData = "no data available yet...";
            g.drawString(szNoData, (getWidth() - fm.stringWidth(szNoData)) / 2, (getHeight() + fm.getAscent()) / 2);
            return;
        }
        ;

        if (currentLayout.equals("None")) {
            rescaleIfNeeded();
        }
        HashMap hOrderNodes = new HashMap();
        OSLink link;
        int nOrder;
        int i = 0;
        if (gmapPan.kbShowOS.isSelected()) {
            //more simple solution
            ArrayList alNodes = new ArrayList();
            //first get all nodes
            for (Enumeration e = nodes.elements(); e.hasMoreElements(); i++) {
                rcNode n = (rcNode) e.nextElement();
                alNodes.add(n);
            }
            ;
            int nNodes = alNodes.size();
            //create a matrix 
            int[][] matrixValues = new int[nNodes][nNodes];
            int j;
            for (i = 0; i < nNodes; i++) {
                for (j = 0; j < nNodes; j++) {
                    matrixValues[i][j] = 0;
                }
            }
            for (i = 0; i < nNodes; i++) {
                rcNode n = (rcNode) alNodes.get(i);
                for (Enumeration it2 = n.wconn.keys(); it2.hasMoreElements();) {
                    Object key = it2.nextElement();
                    if (key == null) {
                        continue;
                    }
                    Object objLink = n.wconn.get(key);
                    if (objLink == null) {
                        continue;
                    }
                    if (objLink instanceof OSLink) {
                        link = (OSLink) objLink;
                        if (link.checkState(OSLink.STATE_TRANSFERING)) {//if this link transfers, add it to value
                            //get destination node
                            for (j = 0; j < nNodes; j++) {
                                Object o = alNodes.get(j);
                                if (o == null) {
                                    continue;
                                }
                                if (link.rcDest.equals(o)) {
                                    break;
                                }
                            }
                            if (j < nNodes) { //destination node found
                                matrixValues[i][j]++;//increment pozition at [i][j]
                            }
                        }
                    }
                }
            }

            synchronized (links) {
                //for each os link, check to see if already put
                hOrderNodes.clear();
                Vector linksDrawn = new Vector();
                for (i = 0; i < nNodes; i++) {
                    rcNode n = (rcNode) alNodes.get(i);
                    rcNode rcDest;
                    Object[] con;
                    try {
                        for (Enumeration it2 = n.wconn.keys(); it2.hasMoreElements();) {
                            Object objkey = it2.nextElement();
                            Object objLink = n.wconn.get(objkey);
                            if (objLink instanceof OSLink) {
                                if (linksDrawn.contains(objkey)) {
                                    continue; // do not draw again
                                }
                                linksDrawn.add(objkey);
                                link = (OSLink) objLink;
                                rcDest = link.rcDest;
                                if (rcDest == null) { //if no destination, check to see if fake link, so check the hash for fake links/nodes and that the destination node is stii in nodes structure
                                    for (int k = 0; k < lLinkToFakeNode.size(); k++) {
                                        con = (Object[]) lLinkToFakeNode.get(k);
                                        if (con[3].equals(n) && ((rcNode) con[0]).UnitName.equals(link.szDestName)) {
                                            rcDest = (rcNode) con[0];
                                            break;
                                        }
                                    }
                                }
                                if (rcDest == null) {
                                    continue;
                                }
                                // check to see if rcDest is acceptable
                                boolean found = false;
                                for (Enumeration en = nodes.elements(); en.hasMoreElements();) {
                                    rcNode nnn = (rcNode) en.nextElement();
                                    if (nnn.equals(rcDest)) {
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    continue; // don't draw
                                }
                                if (rcDest.wconn.containsKey(objkey)) {
                                    OSLink revLink = (OSLink) rcDest.wconn.get(objkey);
                                    if (((revLink.nState & OSLink.STATE_CONNECTED) != OSLink.STATE_CONNECTED)
                                            || ((revLink.nState & OSLink.STATE_FREE) != OSLink.STATE_FREE)) {
                                        link = revLink;
                                    }
                                }
                                if (rcDest != null) {//check first to see if this is a valid link
                                    if (showOnlyConnectedNodes && (!n.isLayoutHandled || !rcDest.isLayoutHandled)) {
                                        continue;
                                    }

                                    oslinks.add(link);
                                    //set new state if the case
                                    Color stateColor = null;

                                    //if transfering
                                    stateColor = link.getStateColor2();
                                    if (link.checkState(OSLink.STATE_TRANSFERING)) {
                                        //check for equivalent connection from the other node
                                        //-> that means to check if number of transfering links is greater than 0
                                        //get position of other node
                                        for (j = 0; j < nNodes; j++) {
                                            if (rcDest.equals(alNodes.get(j))) {
                                                break;
                                            }
                                        }
                                        if (j < nNodes) {
                                            if (matrixValues[i][j] > 0) {
                                                matrixValues[i][j]--;
                                            } else {
                                                stateColor = OSLink.OSLINK_COLOR_FREE_2;
                                            }
                                        }
                                    }
                                    //draw link with respect to nOrder
                                    //check the order hashmap
                                    Object objOrder;

                                    String key = null;
                                    if (link.szSourcePort != null) {
                                        if (link.szSourcePort.type.shortValue() == OSPort.OUTPUT_PORT) {
                                            key = link.rcSource.UnitName + "->" + link.rcDest.UnitName;
                                        } else {
                                            key = link.rcDest.UnitName + "->" + link.rcSource.UnitName;
                                        }
                                    } else if (link.szSourceNewPort != null) {
                                        if (link.szSourceNewPort.type == OSwPort.OUTPUT_PORT) {
                                            key = link.rcSource.UnitName + "->" + link.rcDest.UnitName;
                                        } else {
                                            key = link.rcDest.UnitName + "->" + link.rcSource.UnitName;
                                        }
                                    } else {
                                        continue;
                                    }
                                    objOrder = hOrderNodes.get(key);
                                    if (objOrder == null) {
                                        nOrder = 1;
                                    } else {
                                        nOrder = ((Integer) objOrder).intValue() + 1;
                                    }
                                    hOrderNodes.put(key, Integer.valueOf(nOrder));
                                    drawConn(g, link, null, stateColor, nOrder);
                                }
                            }
                        }
                    } catch (Exception ex) {
                        //ignore exception
                        //						ex.printStackTrace();
                    }
                }
            }
        }//end if show links
        boolean haveControl = false;
        for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
            rcNode n = (rcNode) e.nextElement();
            if (showOnlyConnectedNodes && !n.isLayoutHandled) {
                continue;
            } else if (LTFNcontains(0, n) == null) {
                g.setFont(osFont);
                if (n.getOpticalSwitchInfo() != null) {
                    if (!n.getOpticalSwitchInfo().isAlive) {
                        paintNoNode(g, n, fmOS);
                    } else {
                        paintNode(g, n, fmOS);
                        if ((n != null) && (n.client != null) && (n.client.osControl != null)) {
                            if (!haveControl) {
                                gmapPan.currentControl = n.client.osControl;
                            }
                            haveControl = true;
                        }
                    }
                } else if (n.getOSwConfig() != null) {
                    if (!n.getOSwConfig().isAlive) {
                        paintNoNode(g, n, fmOS);
                    } else {
                        paintNode(g, n, fmOS);
                        if ((n != null) && (n.client != null) && (n.client.osControl != null)) {
                            if (!haveControl) {
                                gmapPan.currentControl = n.client.osControl;
                            }
                            haveControl = true;
                        }
                    }
                } else {
                    paintNode(g, n, fmOS);
                    if ((n != null) && (n.client != null) && (n.client.osControl != null)) {
                        if (!haveControl) {
                            gmapPan.currentControl = n.client.osControl;
                        }
                        haveControl = true;
                    }
                }
                g.setFont(nodesFont);
            } else {
                paintFakeNode(g, n, fm);
            }
        }
        gmapPan.createPathButton.setEnabled(haveControl);
        gmapPan.createPathButton.revalidate();
        gmapPan.createPathButton.repaint();

        //		System.out.println(System.currentTimeMillis() + ": " + haveControl);

        if (!haveLinkWithID && linkSelectionMode) {
            gmapPan.enterCmd();
        }
        if ((haveLinkWithID != oldHaveLinkWithID) && haveControl) {
            setRemoveLinkButtons(haveLinkWithID);
        } else {
            if (!haveControl) {
                gmapPan.destroyPathButton.setEnabled(false);
                gmapPan.destroyPathButton.revalidate();
                gmapPan.destroyPathButton.repaint();
                oldHaveLinkWithID = !haveLinkWithID;
            }
        }
    }

    public boolean checkPortConnectsFake(String switchName, String port) {
        for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
            rcNode n = (rcNode) e.nextElement();
            final String nodeName = n.szOpticalSwitch_Name == null ? n.shortName : n.szOpticalSwitch_Name;
            if (nodeName.equals(switchName)) {
                for (Enumeration it2 = n.wconn.keys(); it2.hasMoreElements();) {
                    try {
                        Object objkey = it2.nextElement();
                        Object objLink = n.wconn.get(objkey);
                        if (objLink instanceof OSLink) {
                            OSLink link = (OSLink) objLink;
                            if (link.szSourcePort != null) { // type one sw
                                if ((link.rcSource != null) && link.rcSource.equals(n)
                                        && link.szSourcePort.name.equals(port)) {
                                    return ((link.rcDest != null) && (LTFNcontains(0, link.rcDest) != null));
                                }
                                if ((link.rcDest != null) && link.rcDest.equals(n) && link.szDestPort.equals(port)) {
                                    return ((link.rcSource != null) && (LTFNcontains(0, link.rcSource) != null));
                                }
                            } else if (link.szSourceNewPort != null) { // type new sw
                                if ((link.rcSource != null) && link.rcSource.equals(n)
                                        && link.szSourceNewPort.name.equals(port)) {
                                    return ((link.rcDest != null) && (LTFNcontains(0, link.rcDest) != null));
                                }
                                if ((link.rcDest != null) && link.rcDest.equals(n) && link.szDestPort.equals(port)) {
                                    return ((link.rcSource != null) && (LTFNcontains(0, link.rcSource) != null));
                                }
                            }
                        }
                    } catch (Exception ex) {
                    }
                }
            }
        }
        return false;
    }

    private boolean oldHaveLinkWithID = false;

    public void setRemoveLinkButtons(boolean enabled) {

        gmapPan.destroyPathButton.setEnabled(enabled);
        gmapPan.destroyPathButton.revalidate();
        gmapPan.destroyPathButton.repaint();
        oldHaveLinkWithID = haveLinkWithID;
    }

    public final Hashtable getLinks() {

        final Hashtable h = new Hashtable();
        for (Enumeration en = nodes.keys(); en.hasMoreElements();) {
            Object key = en.nextElement();
            if (key == null) {
                continue;
            }
            rcNode node = (rcNode) nodes.get(key);
            if (node == null) {
                continue;
            }
            for (Enumeration en1 = node.wconn.keys(); en1.hasMoreElements();) {
                key = en1.nextElement();
                if (key == null) {
                    continue;
                }
                Object obj = node.wconn.get(key);
                if (obj == null) {
                    continue;
                }
                if (obj instanceof OSLink) {
                    OSLink link = (OSLink) obj;
                    if (link.linkID == null) {
                        continue;
                    }
                    if (!h.containsKey(link.linkID)) {
                        String[] details = getLink(link.linkID);
                        h.put(link.linkID, details);
                    }
                }
            }
        }
        return h;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
        //		addMouseMotionListener(this);

        //		System.out.println("mousePressed called");

        if (OSConToolTip.isShowing()) {
            //System.out.println("mouse dragged");
            OSConToolTip.hidePopup();
            OSConToolTip.setNode(null);
            requestFocus();
            requestFocusInWindow();
            repaint();
        }//	    nodeCandidate = null;

        int x = e.getX();
        int y = e.getY();
        pick = null;
        for (Enumeration en = nodes.elements(); en.hasMoreElements();) {
            rcNode n = (rcNode) en.nextElement();
            n.selected = false;
            if ((n == null) || (n.limits == null) || (showOnlyConnectedNodes && !n.isLayoutHandled)) {
                continue;
            }
            if (n.limits.contains(x, y)) {
                pick = n;
                pick.fixed = true;
                break;
            }
        }
        int but = e.getButton();

        if (but == MouseEvent.BUTTON3) {
            synchronized (syncGraphObj) {
                pickX = pick;
            }
            setMaxFlow(pickX);
            if (currentLayout.equals("Elastic") || gmapPan.cbLayout.getSelectedItem().equals("Layered")
                    || gmapPan.cbLayout.getSelectedItem().equals("Radial")) {
                currentTransformCancelled = true;
                setLayoutType((String) gmapPan.cbLayout.getSelectedItem());
            }
        }

        if (but == MouseEvent.BUTTON1) {
            if (pick == null) {
                if (linkSelectionMode) {
                    synchronized (links) {
                        for (int i = 0; i < links.size(); i++) {
                            Link l = (Link) links.get(i);
                            if (l.intersect(x, y)) {
                                if (l.linkID != null) {
                                    gmapPan.removeLink.setEnabled(true);
                                    gmapPan.detailsLink.setEnabled(true);
                                    linkSelected = l.linkID;
                                    linkSelectedDetails = formLinkToolTip(linkSelected);
                                    repaint();
                                    return;
                                }
                            }
                        }
                    }
                    gmapPan.removeLink.setEnabled(false);
                    gmapPan.detailsLink.setEnabled(false);
                    linkSelected = null;
                    participatingRCNodes.clear();
                    linkSelectedDetails = null;
                    repaint();
                    return;
                }
            } else {
                if (selectionMode) {
                    if (LTFNcontains(0, pick) != null) {
                        if (!selectedNodes.contains(pick)) {
                            if (checkPath(pick)) {
                                addToPath(pick);
                            } else {
                                logger.warning("Can not add " + pick.UnitName + ".");
                            }
                        } else {
                            removeFromPath(pick);
                        }
                    } else {
                        if ((pick.getOpticalSwitchInfo() != null) && pick.getOpticalSwitchInfo().isAlive) {
                            if (!selectedNodes.contains(pick)) {
                                if (checkPath(pick)) {
                                    addToPath(pick);
                                } else {
                                    logger.warning("Can not add " + pick.UnitName + ".");
                                }
                            } else {
                                removeFromPath(pick);
                            }
                        }
                    }
                    redoSelectedPath();
                    repaint();
                    pick.fixed = false;
                    pick = null;
                    return;
                }
                // setMaxFlow ( pick );
                pick.selected = true;
                if (pick.client != null) {
                    //					&& (pick.getOpticalSwitchInfo() == null || pick.getOpticalSwitchInfo().isAlive)) {
                    pick.client.setVisible(!pick.client.isVisible());
                }
                pick.fixed = false;
                pick = null;
                repaint();
            }
        }
        e.consume();
    }

    protected void addToPath(rcNode pick) {

        if (singleEndPointsMode) {
            while (selectedNodes.size() > 1) {
                selectedNodes.remove(1);
            }
            selectedNodes.add(pick);
            return;
        }
        selectedNodes.add(pick);
    }

    protected void removeFromPath(rcNode pick) {

        if (allPathMode) {
            int poz = 0;
            while (poz < selectedNodes.size()) {
                if (pick.equals(selectedNodes.get(poz))) {
                    break;
                }
                poz++;
            }
            if ((poz == (selectedNodes.size() - 1)) || (poz == 0)) {
                selectedNodes.remove(poz);
                return;
            }
            rcNode p1 = (rcNode) selectedNodes.get(poz - 1);
            rcNode p2 = (rcNode) selectedNodes.get(poz - 2);
            Vector path = new Vector();
            path.add(p1);
            Vector v = pathBetweenEndPoints(p1, p2, path);
            if (v == null) {
                logger.warning("Can not remove " + pick.UnitName + " since there is no path between " + p1.UnitName
                        + " and " + p2.UnitName);
                return;
            }
            if (fdxPath) {
                path.clear();
                path.add(p2);
                v = pathBetweenEndPoints(p2, p1, path);
                if (v == null) {
                    logger.warning("Can not remove " + pick.UnitName + " since there is no path between " + p2.UnitName
                            + " and " + p1.UnitName);
                    return;
                }
            }
            selectedNodes.remove(poz);
            return;
        }
        int poz = 0;
        while (poz < selectedNodes.size()) {
            if (pick.equals(selectedNodes.get(poz))) {
                break;
            }
            poz++;
        }
        if (poz == 0) {
            logger.warning("Removing all selected nodes since " + pick + " was the root of the path.");
            selectedNodes.clear();
            return;
        }
        selectedNodes.remove(poz);
    }

    public void redoSelectedPath() {
        if (selectedNodes.size() != 0) {
            StringBuilder buf = new StringBuilder();
            buf.append("Current path: ");
            for (int i = 0; i < selectedNodes.size(); i++) {
                if (i != 0) {
                    if (multipleEndPointsMode && (i != 1)) {
                        buf.append(" + ");
                    } else {
                        buf.append(" -> ");
                    }
                }
                rcNode sn = (rcNode) selectedNodes.get(i);
                String nodeName = sn.szOpticalSwitch_Name == null ? sn.shortName : sn.szOpticalSwitch_Name;
                buf.append(nodeName);
            }
            gmapPan.currentPath.setText(buf.toString());
        } else {
            gmapPan.currentPath.setText("");
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if ((pick != null) /*&& (e.getButton() == MouseEvent.BUTTON2)*/) {
            pick.fixed = false;
            //    pick = null;
        }
        // notify the thread that the position has changed
        if (layout instanceof SpringLayoutAlgorithm) {
            ((SpringLayoutAlgorithm) layout).notifyRunnerThread();
        }
        repaint();
        e.consume();
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        linkToolTip = null;
        if (!linkSelectionMode) {
            participatingRCNodes.clear();
        }
        setCursor(defaultCursor);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        linkToolTip = null;
        if (!linkSelectionMode) {
            participatingRCNodes.clear();
        }
        setCursor(defaultCursor);
    }

    @Override
    public void mouseDragged(MouseEvent e) {

        //		System.out.println("mouseDragged "+e);

        if (OSConToolTip.isShowing()) {
            //System.out.println("mouse dragged");
            OSConToolTip.hidePopup();
            OSConToolTip.setNode(null);
            repaint();
        }//	    nodeCandidate = null;

        if (pick != null) {
            int margin = (int) (fLayoutMargin * getWidth());
            int minx = wunit + 2 + margin;
            int miny = hunit + 2 + margin;
            int maxx = getWidth() - minx;
            int maxy = getHeight() - miny;
            int x = e.getX();
            int y = e.getY();

            if (x < minx) {
                x = minx;
            }
            if (y < miny) {
                y = miny;
            }
            if (x > maxx) {
                x = maxx;
            }
            if (y > maxy) {
                y = maxy;
            }

            pick.x = x;
            pick.y = y;
            if (vGraph != null) {
                GraphNode gn = (GraphNode) vGraph.nodesMap.get(pick);
                if (gn != null) {
                    gn.pos.setLocation(pick.x, pick.y);
                    // notify the thread that the position has changed
                    if (layout instanceof SpringLayoutAlgorithm) {
                        ((SpringLayoutAlgorithm) layout).notifyRunnerThread();
                    }
                }
            }
            repaint();
        }
        e.consume();
    }

    protected boolean checkPath(rcNode n) {

        Object[] obj = LTFNcontains(0, n);
        if (obj != null) {
            rcNode n1 = (rcNode) obj[3];
            if ((n1.client != null) && (n1.client.osControl == null)) {
                return false;
            }
            if (selectedNodes.size() == 0) { // first node
                if (obj[2] == null) {
                    return false; // must have connection fake -> node
                }
            }
        } else if ((n.client != null) && (n.client.osControl == null)) {
            return false;
        }
        if (!selectionMode) {
            return false;
        }
        if (allPathMode) {
            if (selectedNodes.size() > 0) {
                rcNode lastNode = (rcNode) selectedNodes.get(selectedNodes.size() - 1);
                Vector path = new Vector();
                path.add(lastNode);
                Vector v = pathBetweenEndPoints(lastNode, n, path);
                if (v == null) {
                    return false;
                }
                if (fdxPath) {
                    path.clear();
                    path.add(n);
                    v = pathBetweenEndPoints(n, lastNode, path);
                    if (v == null) {
                        return false;
                    }
                }
            }
            return true;
        }
        if (singleEndPointsMode) {
            if (LTFNcontains(0, n) == null) {
                return false;
            }
            if (selectedNodes.size() > 0) {
                rcNode start = (rcNode) selectedNodes.get(0);
                Vector path = new Vector();
                path.add(start);
                Vector v = pathBetweenEndPoints(start, n, path);
                if (v == null) {
                    return false;
                }
                if (fdxPath) {
                    path.clear();
                    path.add(n);
                    v = pathBetweenEndPoints(n, start, path);
                    if (v == null) {
                        return false;
                    }
                }
            }
            return true;
        }
        if (multipleEndPointsMode) {
            if (LTFNcontains(0, n) == null) {
                return false;
            }
            if (selectedNodes.size() > 0) {
                rcNode start = (rcNode) selectedNodes.get(0);
                Vector path = new Vector();
                path.add(start);
                Vector v = pathBetweenEndPoints(start, n, path);
                if (v == null) {
                    return false;
                }
                if (fdxPath) {
                    path.clear();
                    path.add(n);
                    v = pathBetweenEndPoints(n, start, path);
                    if (v == null) {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    public OSMonitorControl getAddFirstMonitor() {

        if ((selectedNodes == null) || (selectedNodes.size() == 0)) {
            return null;
        }
        for (int i = 0; i < selectedNodes.size(); i++) {
            rcNode n = (rcNode) selectedNodes.get(i);
            Object obj[];
            if ((obj = LTFNcontains(0, n)) != null) {
                rcNode n1 = (rcNode) obj[3];
                if ((n1 != null) && (n1.client.osControl != null)) {
                    return n1.client.osControl;
                }
                continue;
            }
            if (n.client.osControl != null) {
                return n.client.osControl;
            }
        }
        return null;
    }

    public OSMonitorControl getDelFirstMonitor() {

        if ((participatingRCNodes == null) || (participatingRCNodes.size() == 0)) {
            return null;
        }
        for (int i = 0; i < participatingRCNodes.size(); i++) {
            rcNode n = (rcNode) participatingRCNodes.get(i);
            Object obj[];
            if ((obj = LTFNcontains(0, n)) != null) {
                rcNode n1 = (rcNode) obj[3];
                if ((n1 != null) && (n1.client.osControl != null)) {
                    return n1.client.osControl;
                }
                continue;
            }
            if (n.client.osControl != null) {
                return n.client.osControl;
            }
        }
        return null;
    }

    protected Vector pathBetweenEndPoints(rcNode n1, rcNode n2, Vector path) {

        if (n1.equals(n2)) {
            Vector v = new Vector();
            v.add(n2);
            return v;
        }

        Object[] objs;
        if ((objs = LTFNcontains(0, n1)) != null) {
            rcNode dest = (rcNode) objs[3];
            if (path.contains(dest)) {
                return null;
            }
            path.add(dest);
            Vector v = pathBetweenEndPoints(dest, n2, path);
            if (v != null) {
                Vector v1 = new Vector();
                v1.add(n1);
                for (int i = 0; i < v.size(); i++) {
                    v1.add(v.get(i));
                }
                path.remove(dest);
                return v1;
            }
            path.remove(dest);
        } else {
            if ((n1.client == null) || (n1.client.osControl == null)) {
                return null;
            }
        }

        if (n1.wconn != null) {
            for (Enumeration en = n1.wconn.keys(); en.hasMoreElements();) {
                Object key = en.nextElement();
                if (key == null) {
                    continue;
                }
                Object obj = n1.wconn.get(key);
                if (obj == null) {
                    continue;
                }
                if (obj instanceof OSLink) {
                    OSLink link = (OSLink) obj;
                    if ((link.szSourcePort != null)
                            && ((link.szSourcePort.type.shortValue() == OSPort.INPUT_PORT) || (link.linkID != null))) {
                        continue;
                    }
                    if ((link.szSourceNewPort != null)
                            && ((link.szSourceNewPort.type == OSwPort.INPUT_PORT) || (link.linkID != null))) {
                        continue;
                    }
                    rcNode dest = link.rcDest;
                    if ((dest == null) || dest.equals(n1)) {
                        dest = link.rcSource;
                    }
                    if (dest == null) {
                        continue;
                    }
                    if (path.contains(dest)) {
                        continue;
                    }
                    path.add(dest);
                    Vector v = pathBetweenEndPoints(dest, n2, path);
                    if (v != null) {
                        Vector v1 = new Vector();
                        v1.add(n1);
                        for (int i = 0; i < v.size(); i++) {
                            v1.add(v.get(i));
                        }
                        path.remove(dest);
                        return v1;
                    }
                    path.remove(dest);
                }
            }
        }
        return null;
    }

    @Override
    public void mouseMoved(MouseEvent e) {

        final int x = e.getX();
        final int y = e.getY();
        if (selectionMode) {
            rcNode nodeOver = null;
            for (Enumeration en = nodes.elements(); en.hasMoreElements();) {
                rcNode n = (rcNode) en.nextElement();
                if ((n == null) || (n.limits == null) || (showOnlyConnectedNodes && !n.isLayoutHandled)) {
                    continue;
                }
                if (LTFNcontains(0, n) != null) {
                    if (n.limits.contains(x, y)) {
                        if (!checkPath(n)) {
                            setCursor(unselectableCursor);
                        } else {
                            setCursor(selectableCursor);
                        }
                        return;
                    }
                }
                if (n.limits.contains(x, y) || OSConToolTip.hasMouse()/*&& n.szOpticalSwitch_Name!=null */) {//mouse over node and node si optical switch
                    //System.out.println("(x,y)=("+x+","+y+") for node "+n.UnitName);
                    nodeOver = n;
                    break;
                }
            }
            if ((nodeOver != null)
                    && ((nodeOver.getOpticalSwitchInfo() == null) || !nodeOver.getOpticalSwitchInfo().isAlive)) {
                setCursor(unselectableCursor);
                return;
            } else if (nodeOver == null) {
                setCursor(defaultCursor);
                return;
            }
            if (checkPath(nodeOver)) {
                setCursor(selectableCursor);
            } else {
                setCursor(unselectableCursor);
            }
            return;
        }
        if (linkSelectionMode) {
            // now check the links also
            synchronized (links) {
                for (int i = 0; i < links.size(); i++) {
                    Link l = (Link) links.get(i);
                    if (l.intersect(x, y)) {
                        if (l.linkID != null) {
                            setCursor(selectableCursor);
                            String ret[] = getLink(l.linkID);
                            if ((ret != null) && (ret.length != 0)) {
                                linkToolTip = ret[0];
                            }
                        } else {
                            setCursor(unselectableCursor);
                            linkToolTip = null;
                        }
                        return;
                    }
                }
            }
            linkToolTip = null;
            setCursor(defaultCursor);
            return;
        }
        if (OSConToolTip.isShowing()) {
            rcNode nodeOver = null;
            for (Enumeration en = nodes.elements(); en.hasMoreElements();) {
                rcNode n = (rcNode) en.nextElement();

                if ((n == null) || (n.limits == null) || (showOnlyConnectedNodes && !n.isLayoutHandled)) {
                    continue;
                }
                if (LTFNcontains(0, n) != null) {
                    continue;
                }
                if (n.limits.contains(x, y) || OSConToolTip.hasMouse()) {//mouse over node and node si optical switch
                    nodeOver = n;
                    break;
                }
            }
            if ((nodeOver != null)
                    && ((nodeOver.getOpticalSwitchInfo() == null) || nodeOver.getOpticalSwitchInfo().isAlive)) {
                return;
            }
            OSConToolTip.hidePopup();
            OSConToolTip.setNode(null);
            repaint();
        }
        // now check the links also
        synchronized (links) {
            String linkID = null;
            boolean linkSelected = false;
            for (int i = 0; i < links.size(); i++) {
                Link l = (Link) links.get(i);
                if (l.intersect(x, y)) {
                    linkID = l.linkID;
                    boolean shouldRedraw = (currentSelectedLink == null) || (!currentSelectedLink.equals(l.realLink));
                    currentSelectedLink = l.realLink;
                    currentSelectedLinkPoz.clear();
                    linkSelected = true;
                    if (shouldRedraw) {
                        repaint();
                    }
                    break;
                }
            }
            if (!linkSelected && (currentSelectedLink != null)) {
                currentSelectedLink = null;
                currentSelectedLinkPoz.clear();
                repaint();
            }
            if (linkID != null) {
                linkToolTip = formLinkToolTip(linkID);
                return;
            }
            linkToolTip = null;
            participatingRCNodes.clear();
        }
    }

    /**
     * when user over a node, return node configuration for real node
     * ignore tooltip text
     */
    @Override
    public String getToolTipText(MouseEvent event) {

        if (selectionMode) {
            return null;
        }
        if (linkSelectionMode) {
            return linkToolTip;
        }
        final int x = event.getX();
        final int y = event.getY();
        rcNode nodeOver = null;
        for (Enumeration en = nodes.elements(); en.hasMoreElements();) {
            rcNode n = (rcNode) en.nextElement();

            if ((n == null) || (n.limits == null) || (showOnlyConnectedNodes && !n.isLayoutHandled)) {
                continue;
            }
            if (LTFNcontains(0, n) != null/*n.mlentry!=null && n.mlentry.Group.compareTo("OSwitch")==0*/) {
                continue;
            }
            if (n.limits.contains(x, y) || OSConToolTip.hasMouse()/*&& n.szOpticalSwitch_Name!=null */) {//mouse over node and node si optical switch
                nodeOver = n;
                break;
            }
        }
        if (nodeOver != null) {
            if ((nodeOver.getOpticalSwitchInfo() != null) && !nodeOver.getOpticalSwitchInfo().isAlive) {
                return linkToolTip;
            }
            if ((nodeOver.getOSwConfig() != null) && !nodeOver.getOSwConfig().isAlive) {
                return linkToolTip;
            }
            OSConToolTip.setNode(nodeOver);
            Point pParentPos = getLocationOnScreen();
            OSConToolTip.showPopup(this, pParentPos.x + nodeOver.x, pParentPos.y + nodeOver.y);
        } else {
            return linkToolTip;
        }
        return null;
    }

    void rescaleIfNeeded() {
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        range.setBounds((int) (panelWidth * fLayoutMargin), (int) (panelHeight * fLayoutMargin),
                (int) (panelWidth * (1 - (2 * fLayoutMargin))), (int) (panelHeight * (1 - (2 * fLayoutMargin))));
        range.grow(-wunit, -hunit);
        boolean firstNode = true;
        for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
            rcNode n = (rcNode) e.nextElement();
            if (firstNode) {
                firstNode = false;
                actualRange.setBounds(n.x, n.y, 1, 1);
            } else {
                actualRange.add(n.x, n.y);
            }
        }
        if (firstNode) {
            return;
        }
        double zx = actualRange.getWidth() / range.getWidth();
        double zy = actualRange.getHeight() / range.getHeight();

        if (zx > 1) {
            actualRange.width = (int) (actualRange.width / zx);
        }
        if (zy > 1) {
            actualRange.height /= (int) (actualRange.height / zy);
        }

        double dx = 0;
        double dy = 0;

        if (actualRange.x < range.x) {
            dx = range.x - actualRange.x;
        }
        if (actualRange.getMaxX() > range.getMaxX()) {
            dx = range.getMaxX() - actualRange.getMaxX();
        }
        if (actualRange.y < range.y) {
            dy = range.y - actualRange.y;
        }
        if (actualRange.getMaxY() > range.getMaxY()) {
            dy = range.getMaxY() - actualRange.getMaxY();
        }

        for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
            rcNode n = (rcNode) e.nextElement();
            if (zx > 1) {
                n.x = (int) (n.x / zx);
            }
            if (zy > 1) {
                n.y = (int) (n.y / zy);
            }
            if (n.fixed) {
                continue;
            }
            n.x = (int) (n.x + dx);
            n.y = (int) (n.y + dy);
            if (vGraph != null) {
                GraphNode gn = (GraphNode) vGraph.nodesMap.get(n);
                if (gn != null) {
                    gn.pos.setLocation(n.x, n.y);
                }
            }
        }
    }

    @Override
    public void computeNewLayout() {
        synchronized (syncGraphObj) {
            if (!(layout instanceof NoLayoutLayoutAlgorithm)) {
                currentLayout = "SomeLayout";
            }
            //System.out.println("enter "+currentLayout+" class "+layout.getClass().getName());
            int panelWidth = getWidth();
            int panelHeight = getHeight();
            //			range.setBounds(0, 0, getWidth(), getHeight());
            range.setBounds((int) (panelWidth * fLayoutMargin), (int) (panelHeight * fLayoutMargin),
                    (int) (panelWidth * (1 - (2 * fLayoutMargin))), (int) (panelHeight * (1 - (2 * fLayoutMargin))));
            range.grow(-wunit, -hunit);
            layout.layOut();

            if (layout instanceof NoLayoutLayoutAlgorithm) { //currentLayout.equals("None")){
                //System.out.println("exit1 "+currentLayout+" class "+layout.getClass().getName());
                vGraph = null;
                layout = null;
                repaint();
                return;
            }
            // transform smoothly from the current positions to the destination
            long TRANSF_TOTAL_TIME = 2000; // 3 seconds
            long STEP_DELAY = 30; // 30 millis
            long nSteps = TRANSF_TOTAL_TIME / STEP_DELAY;

            // convert positions from relative [-1, 1] to [range]
            for (Iterator it = vGraph.gnodes.iterator(); it.hasNext();) {
                GraphNode gn = (GraphNode) it.next();
                if (!gn.rcnode.fixed) {
                    gn.pos.x = range.x + (int) ((range.width * (1.0 + gn.pos.x)) / 2.0);
                    gn.pos.y = range.y + (int) ((range.height * (1.0 + gn.pos.y)) / 2.0);
                }
            }
            currentTransformCancelled = false;
            // perform transitions
            for (int i = 0; (i < nSteps) && !currentTransformCancelled; i++) {
                //		    System.out.println("transition "+i);
                for (Iterator it = vGraph.gnodes.iterator(); it.hasNext();) {
                    GraphNode gn = (GraphNode) it.next();
                    if (!gn.rcnode.fixed) {
                        int dx = (int) ((gn.pos.x - gn.rcnode.x) / (nSteps - i));
                        int dy = (int) ((gn.pos.y - gn.rcnode.y) / (nSteps - i));
                        //				    if(gn.rcnode.UnitName.equals("CALTECH"))
                        //				        System.out.println("moving C["+gn.rcnode.x+", "+gn.rcnode.y+"] -> ["+gn.pos.x+", "+gn.pos.y+"]"); // by "+dx+", "+dy);
                        gn.rcnode.x += dx;
                        gn.rcnode.y += dy;
                    }
                }
                repaint();
                try {
                    Thread.sleep(STEP_DELAY);
                } catch (InterruptedException ex) {
                }
            }

            // final positions
            for (Iterator it = vGraph.gnodes.iterator(); it.hasNext();) {
                GraphNode gn = (GraphNode) it.next();
                if (!gn.rcnode.fixed) {
                    gn.rcnode.x = (int) gn.pos.x;
                    gn.rcnode.y = (int) gn.pos.y;
                }
            }
            //System.out.println("exit2 "+currentLayout+" class "+layout.getClass().getName());
            // invoke the NoLayout algorithm to recompute visibility for the other nodes
            layout = new NoLayoutLayoutAlgorithm(vGraph);
            layout.layOut();
            vGraph = null;
            layout = null;
            repaint();
            currentLayout = "None";
        }
    }

    @Override
    public int setElasticLayout() {
        //synchronized(syncRescale){
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        //		range.setBounds(0, 0, getWidth(), getHeight());
        range.setBounds((int) (panelWidth * fLayoutMargin), (int) (panelHeight * fLayoutMargin),
                (int) (panelWidth * (1 - (2 * fLayoutMargin))), (int) (panelHeight * (1 - (2 * fLayoutMargin))));
        range.grow(-wunit - 2, -hunit - 2);
        //System.out.println("setting elastic layout");
        // set positions for the unhandled nodes
        //if(vGraph == layout.gt)
        int totalMovement = 0;
        for (Iterator it = vGraph.gnodes.iterator(); it.hasNext();) {
            GraphNode gn = (GraphNode) it.next();
            int nx = gn.rcnode.x;
            int ny = gn.rcnode.y;
            gn.rcnode.x = (int) Math.round(gn.pos.x);
            gn.rcnode.y = (int) Math.round(gn.pos.y);
            //				if(layout.handled.contains(gn)){
            if (gn.rcnode.x < range.x) {
                gn.rcnode.x = range.x;
            }
            if (gn.rcnode.y < range.y) {
                gn.rcnode.y = range.y;
            }
            if (gn.rcnode.x > range.getMaxX()) {
                gn.rcnode.x = (int) range.getMaxX();
            }
            if (gn.rcnode.y > range.getMaxY()) {
                gn.rcnode.y = (int) range.getMaxY();
            }
            gn.pos.setLocation(gn.rcnode.x, gn.rcnode.y);
            totalMovement += Math.abs(gn.rcnode.x - nx) + Math.abs(gn.rcnode.y - ny);
            //				}
        }
        repaint();
        return totalMovement;
    }

    void unfixNodes() {
        rcNode n;
        for (Enumeration en = nodes.elements(); en.hasMoreElements();) {
            n = (rcNode) en.nextElement();
            n.fixed = false;
        }
        ;
    }

    void setLayoutType(String type) {
        //System.out.println("setLayoutType:"+type);
        if ((layout == null) && type.equals("Elastic")) {
            currentLayout = "None";
        }
        if ((layout != null) && !type.equals(currentLayout)) {
            currentLayout = "None";
            layout.finish();
        }
        boolean bUseInet = false;
        boolean bUseOS;
        bUseOS = gmapPan.kbShowOS.isSelected();
        syncNodes();
        if (type.equals("Random") || type.equals("Grid") || type.equals("Map") || type.equals("None")) {
            synchronized (syncGraphObj) {
                unfixNodes();
                vGraph = GraphTopology.constructGraphFromInetAndMaxFlowAndOS(nodes, maxFlow, bUseInet, bUseOS, this);
                if (type.equals("Random")) {
                    layout = new RandomLayoutAlgorithm(vGraph);
                } else if (type.equals("Grid")) {
                    layout = new GridLayoutAlgorithm(vGraph);
                } else if (type.equals("Map")) {
                    layout = new GeographicLayoutAlgorithm(vGraph);
                } else {
                    layout = new NoLayoutLayoutAlgorithm(vGraph);
                }
                layoutTransformer.layoutChanged();
            }
        } else if (type.equals("Radial") || type.equals("Layered")) {
            synchronized (syncGraphObj) {
                unfixNodes();
                vGraph = GraphTopology.constructGraphFromInetAndMaxFlowAndOS(nodes, maxFlow, bUseInet, bUseOS, this);
                if ((pickX == null) || (!nodes.containsValue(pickX))) {
                    pickX = vGraph.findARoot();
                }
                //System.out.println("layout "+type+" from "+pickX.UnitName);
                if (pickX != null) {
                    vGraph.pruneToTree(pickX); // convert the graph to a tree
                }
                if (type.equals("Radial")) {
                    layout = new RadialLayoutAlgorithm(vGraph, pickX);
                } else {
                    layout = new LayeredTreeLayoutAlgorithm(vGraph, pickX);
                }
                layoutTransformer.layoutChanged();
            }
        } else if (type.equals("Elastic")) {
            if (currentLayout.equals("Elastic")) {
                synchronized (syncGraphObj) {
                    vGraph = GraphTopology
                            .constructGraphFromInetAndMaxFlowAndOS(nodes, maxFlow, bUseInet, bUseOS, this);
                    ((SpringLayoutAlgorithm) layout).updateGT(vGraph);
                }
            } else {
                synchronized (syncGraphObj) {
                    unfixNodes();
                    currentLayout = "Elastic";
                    vGraph = GraphTopology
                            .constructGraphFromInetAndMaxFlowAndOS(nodes, maxFlow, bUseInet, bUseOS, this);
                    layout = new SpringLayoutAlgorithm(vGraph, this);
                    ((SpringLayoutAlgorithm) layout).setStiffness(gmapPan.sldStiffness.getValue());
                    ((SpringLayoutAlgorithm) layout).setRespRange(gmapPan.sldRepulsion.getValue());
                    layout.layOut();
                }
            }
        }
        //currentLayout = type;
    }

    /**
     * changes tooltip mode from wired connections to port 2 port connections
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().equals("changeTooltipMode2Wired")) {
            OSConToolTip.switchPanel(true);
        } else if (e.getActionCommand().equals("changeTooltipMode2Port")) {
            OSConToolTip.switchPanel(false);
        }
    }

    class Link {

        int x1, y1, x2, y2;
        String linkID;
        OSLink realLink;

        public Link(int x1, int y1, int x2, int y2, String linkID, OSLink realLink) {

            this.x1 = x1;
            this.x2 = x2;
            this.y1 = y1;
            this.y2 = y2;
            this.linkID = linkID;
            this.realLink = realLink;
        }

        public boolean intersect(int x, int y) {

            int minX = (x1 < x2) ? x1 : x2;
            if (x < minX) {
                return false;
            }
            int maxX = (x1 < x2) ? x2 : x1;
            if (x > maxX) {
                return false;
            }
            int minY = (y1 < y2) ? y1 : y2;
            if (y < minY) {
                return false;
            }
            int maxY = (y1 < y2) ? y2 : y1;
            if (y > maxY) {
                return false;
            }
            int dist = (int) (Math.abs(((x2 - x1) * (y1 - y)) - ((x1 - x) * (y2 - y1))) / Math
                    .sqrt(((x2 - x1) * (x2 - x1)) + ((y2 - y1) * (y2 - y1))));
            return (dist < 2);
        }

        @Override
        public String toString() {

            return "Link (" + x1 + ":" + y1 + ") - (" + x2 + ":" + y2 + ")" + " -> " + linkID;
        }
    }

    public void printPath(Vector path) {

        for (int i = 0; i < path.size(); i++) {
            String[] str = (String[]) path.get(i);
            System.out.print(str[0] + ":" + str[1] + "(" + str[2] + ") ");
        }
        System.out.println("");
    }

    public String[] getLink(String linkID) {

        Vector v = new Vector();
        for (int i = 0; i < oslinks.size(); i++) {
            OSLink l = (OSLink) oslinks.get(i);
            if ((l.linkID == null) || !l.linkID.equals(linkID)) {
                continue;
            }
            v.add(l);
        }

        if (v.size() == 0) {
            return null;
        }
        StringBuilder buf = new StringBuilder();
        StringBuilder buf2 = new StringBuilder();
        buf.append("<html><table BORDER CELLSPACING=0>");
        buf.append("<tr><td>OS Name</td><td>OS Port</td><td>Port Type</td></tr>");
        Vector path = new Vector();

        Hashtable p = new Hashtable();
        for (int i = 0; i < v.size(); i++) {
            OSLink link = (OSLink) v.get(i);
            if (link.szSourcePort != null) {
                if (link.szSourcePort.type.shortValue() == OSPort.INPUT_PORT) {
                    String key = link.szDestName + ":" + link.szDestPort;
                    p.put(key, new Object[] { link.szSourceName, link.szSourcePort.name, link.rcDest, link.rcSource,
                            link.szDestName, link.szDestPort });
                } else {
                    String key = link.szSourceName + ":" + link.szSourcePort.name;
                    p.put(key, new Object[] { link.szDestName, link.szDestPort, link.rcSource, link.rcDest,
                            link.szSourceName, link.szSourcePort.name });
                }
            } else if (link.szSourceNewPort != null) {
                if (link.szSourceNewPort.type == OSwPort.INPUT_PORT) {
                    String key = link.szDestName + ":" + link.szDestPort;
                    p.put(key, new Object[] { link.szSourceName, link.szSourceNewPort.name, link.rcDest, link.rcSource,
                            link.szDestName, link.szDestPort });
                } else {
                    String key = link.szSourceName + ":" + link.szSourceNewPort.name;
                    p.put(key, new Object[] { link.szDestName, link.szDestPort, link.rcSource, link.rcDest,
                            link.szSourceName, link.szSourceNewPort.name });
                }
            }
        }

        while (p.size() != 0) {
            String key = (String) p.keys().nextElement();
            Object[] obj = (Object[]) p.remove(key);
            String sourceName = (String) obj[4];
            String sourcePort = (String) obj[5];
            rcNode sourceNode = (rcNode) obj[2];
            while (obj != null) {
                String destName = (String) obj[0];
                String destPort = (String) obj[1];
                path.add(new String[] { (String) obj[4], (String) obj[5], "Out" });
                path.add(new String[] { destName, destPort, "In" });
                rcNode next = (rcNode) obj[3];
                String nextPort = null;
                if (LTFNcontains(0, next) != null) {
                    nextPort = null;
                } else {
                    if (next.getOpticalSwitchInfo() != null) {
                        if (next.getOpticalSwitchInfo().crossConnects == null) {
                            break;
                        }
                        for (Object element : next.getOpticalSwitchInfo().crossConnects.keySet()) {
                            OpticalCrossConnectLink ccl = next.getOpticalSwitchInfo().crossConnects.get(element);
                            if (ccl.sPort.name.equals(destPort)) {
                                nextPort = ccl.dPort.name;
                                break;
                            }
                        }
                    } else if (next.getOSwConfig() != null) {
                        if (next.getOSwConfig().crossConnects == null) {
                            break;
                        }
                        for (OSwCrossConn ccl : next.getOSwConfig().crossConnects) {
                            if (ccl.sPort.name.equals(destPort)) {
                                nextPort = ccl.dPort.name;
                                break;
                            }
                        }
                    }
                    if (nextPort == null) {
                        break;
                    }
                }
                key = destName + ":" + nextPort;
                obj = (Object[]) p.remove(key);
            }
            while (true) {
                String previousPort = null;
                if (LTFNcontains(0, sourceNode) != null) {
                    previousPort = null;
                } else {
                    if (sourceNode.getOpticalSwitchInfo() != null) {
                        if (sourceNode.getOpticalSwitchInfo().crossConnects == null) {
                            break;
                        }
                        for (Object element : sourceNode.getOpticalSwitchInfo().crossConnects.keySet()) {
                            OpticalCrossConnectLink ccl = sourceNode.getOpticalSwitchInfo().crossConnects.get(element);
                            if (ccl.dPort.name.equals(sourcePort)) {
                                previousPort = ccl.sPort.name;
                                break;
                            }
                        }
                    } else if (sourceNode.getOSwConfig() != null) {
                        if (sourceNode.getOSwConfig().crossConnects == null) {
                            break;
                        }
                        for (OSwCrossConn ccl : sourceNode.getOSwConfig().crossConnects) {
                            if (ccl.dPort.name.equals(sourcePort)) {
                                previousPort = ccl.dPort.name;
                                break;
                            }
                        }
                    }
                    if (previousPort == null) {
                        break;
                    }
                }
                boolean found = false;
                for (Enumeration en = p.keys(); en.hasMoreElements();) {
                    key = (String) en.nextElement();
                    obj = (Object[]) p.get(key);
                    if (obj[0].equals(sourceName)
                            && (((previousPort == null) && (obj[1] == null)) || obj[1].equals(previousPort))) {
                        found = true;
                        p.remove(key);
                        sourceName = (String) obj[4];
                        sourcePort = (String) obj[5];
                        sourceNode = (rcNode) obj[2];
                        path.add(0, new String[] { (String) obj[0], (String) obj[1], "In" });
                        path.add(0, new String[] { sourceName, sourcePort, "Out" });
                        break;
                    }
                }
                if (!found) {
                    break;
                }
            }
            for (int i = 0; i < path.size(); i++) {
                String[] str = (String[]) path.get(i);
                buf.append("<tr><td>");
                if (str[0] != null) {
                    buf.append(str[0]);
                }
                buf.append("</td><td>");
                if (str[1] != null) {
                    buf.append(str[1]);
                }
                buf.append("</td><td>");
                if (str[2] != null) {
                    buf.append(str[2]);
                }
                buf.append("</td></tr>");
            }
            String start[] = (String[]) path.get(0);
            String end[] = (String[]) path.get(path.size() - 1);
            if (start[0].equals(end[0])) {
                end = (String[]) path.get(path.size() / 2);
            }
            buf2.append(start[0]).append("-").append(end[0]).append(" ");
            path.clear();
        }

        buf.append("</table></html>");
        String ret[] = new String[2];
        ret[0] = buf.toString();
        ret[1] = buf2.toString();
        return ret;
    }

    public String formLinkToolTip(String linkID) {

        participatingRCNodes.clear();
        Vector v = new Vector();
        for (int i = 0; i < oslinks.size(); i++) {
            OSLink l = (OSLink) oslinks.get(i);
            if ((l.linkID == null) || !l.linkID.equals(linkID)) {
                continue;
            }
            if ((l.rcDest != null) && !participatingRCNodes.contains(l.rcDest)) {
                participatingRCNodes.add(l.rcDest);
            }
            if ((l.rcSource != null) && !participatingRCNodes.contains(l.rcSource)) {
                participatingRCNodes.add(l.rcSource);
            }
            v.add(l);
        }

        if (v.size() == 0) {
            return null;
        }
        StringBuilder buf = new StringBuilder();
        buf.append("<html><table BORDER CELLSPACING=0>");
        buf.append("<tr><td>OS Name</td><td>OS Port</td><td>Port Type</td></tr>");
        Vector path = new Vector();

        Hashtable p = new Hashtable();
        for (int i = 0; i < v.size(); i++) {
            OSLink link = (OSLink) v.get(i);
            if (link.szSourcePort != null) {
                if (link.szSourcePort.type.shortValue() == OSPort.INPUT_PORT) {
                    String key = link.szDestName + ":" + link.szDestPort;
                    p.put(key, new Object[] { link.szSourceName, link.szSourcePort.name, link.rcDest, link.rcSource,
                            link.szDestName, link.szDestPort });
                } else {
                    String key = link.szSourceName + ":" + link.szSourcePort.name;
                    p.put(key, new Object[] { link.szDestName, link.szDestPort, link.rcSource, link.rcDest,
                            link.szSourceName, link.szSourcePort.name });
                }
            } else if (link.szSourceNewPort != null) {
                if (link.szSourceNewPort.type == OSwPort.INPUT_PORT) {
                    String key = link.szDestName + ":" + link.szDestPort;
                    p.put(key, new Object[] { link.szSourceName, link.szSourceNewPort.name, link.rcDest, link.rcSource,
                            link.szDestName, link.szDestPort });
                } else {
                    String key = link.szSourceName + ":" + link.szSourceNewPort.name;
                    p.put(key, new Object[] { link.szDestName, link.szDestPort, link.rcSource, link.rcDest,
                            link.szSourceName, link.szSourceNewPort.name });
                }
            }
        }

        while (p.size() != 0) {
            String key = (String) p.keys().nextElement();
            Object[] obj = (Object[]) p.remove(key);
            String sourceName = (String) obj[4];
            String sourcePort = (String) obj[5];
            rcNode sourceNode = (rcNode) obj[2];
            while (obj != null) {
                String destName = (String) obj[0];
                String destPort = (String) obj[1];
                path.add(new String[] { (String) obj[4], (String) obj[5], "Out" });
                path.add(new String[] { destName, destPort, "In" });
                rcNode next = (rcNode) obj[3];
                String nextPort = null;
                if (LTFNcontains(0, next) != null) {
                    nextPort = null;
                } else {
                    if (next.getOpticalSwitchInfo() != null) {
                        if (next.getOpticalSwitchInfo().crossConnects == null) {
                            break;
                        }
                        for (Object element : next.getOpticalSwitchInfo().crossConnects.keySet()) {
                            OpticalCrossConnectLink ccl = next.getOpticalSwitchInfo().crossConnects.get(element);
                            if (ccl.sPort.name.equals(destPort)) {
                                nextPort = ccl.dPort.name;
                                break;
                            }
                        }
                    } else if (next.getOSwConfig() != null) {
                        if (next.getOSwConfig().crossConnects == null) {
                            break;
                        }
                        for (OSwCrossConn ccl : next.getOSwConfig().crossConnects) {
                            if (ccl.sPort.name.equals(destPort)) {
                                nextPort = ccl.dPort.name;
                                break;
                            }
                        }
                    }
                    if (nextPort == null) {
                        break;
                    }
                }
                key = destName + ":" + nextPort;
                obj = (Object[]) p.remove(key);
            }
            while (true) {
                String previousPort = null;
                if (LTFNcontains(0, sourceNode) != null) {
                    previousPort = null;
                } else {
                    if (sourceNode.getOpticalSwitchInfo() != null) {
                        if (sourceNode.getOpticalSwitchInfo().crossConnects == null) {
                            break;
                        }
                        for (Object element : sourceNode.getOpticalSwitchInfo().crossConnects.keySet()) {
                            OpticalCrossConnectLink ccl = sourceNode.getOpticalSwitchInfo().crossConnects.get(element);
                            if (ccl.dPort.name.equals(sourcePort)) {
                                previousPort = ccl.sPort.name;
                                break;
                            }
                        }
                    } else if (sourceNode.getOSwConfig() != null) {
                        if (sourceNode.getOSwConfig().crossConnects == null) {
                            break;
                        }
                        for (OSwCrossConn ccl : sourceNode.getOSwConfig().crossConnects) {
                            if (ccl.dPort.name.equals(sourcePort)) {
                                previousPort = ccl.sPort.name;
                                break;
                            }
                        }
                    }
                    if (previousPort == null) {
                        break;
                    }
                }
                boolean found = false;
                for (Enumeration en = p.keys(); en.hasMoreElements();) {
                    key = (String) en.nextElement();
                    obj = (Object[]) p.get(key);
                    if (obj[0].equals(sourceName)
                            && (((previousPort == null) && (obj[1] == null)) || obj[1].equals(previousPort))) {
                        found = true;
                        p.remove(key);
                        sourceName = (String) obj[4];
                        sourcePort = (String) obj[5];
                        sourceNode = (rcNode) obj[2];
                        path.add(0, new String[] { (String) obj[0], (String) obj[1], "In" });
                        path.add(0, new String[] { sourceName, sourcePort, "Out" });
                        break;
                    }
                }
                if (!found) {
                    break;
                }
            }
            for (int i = 0; i < path.size(); i++) {
                String[] str = (String[]) path.get(i);
                buf.append("<tr><td>");
                if (str[0] != null) {
                    buf.append(str[0]);
                }
                buf.append("</td><td>");
                if (str[1] != null) {
                    buf.append(str[1]);
                }
                buf.append("</td><td>");
                if (str[2] != null) {
                    buf.append(str[2]);
                }
                buf.append("</td></tr>");
            }
            path.clear();
        }

        buf.append("</table></html>");
        return buf.toString();
    }

    protected Icon selectableIcon = null;
    protected Icon unselectableIcon = null;

    public Icon getSelectableIcon() {

        if (selectableIcon != null) {
            return selectableIcon;
        }
        URL iconURL = this.getClass().getResource("/lia/images/osgmap/Up.png");
        selectableIcon = new ImageIcon(iconURL);
        return selectableIcon;
    }

    public Icon getUnselectableIcon() {

        if (unselectableIcon != null) {
            return unselectableIcon;
        }
        URL iconURL = this.getClass().getResource("/lia/images/osgmap/Down.gif");
        unselectableIcon = new ImageIcon(iconURL);
        return unselectableIcon;
    }

    private Cursor createSelectableCursor() {

        ImageIcon icon = (ImageIcon) getSelectableIcon();
        Image image = icon.getImage();
        return Toolkit.getDefaultToolkit().createCustomCursor(image,
                new Point(image.getWidth(null) / 2, image.getHeight(null) / 2), "Selectable");
    }

    private Cursor createUnselectableCursor() {

        return defaultCursor;
        //		ImageIcon icon = (ImageIcon)getUnselectableIcon(); 
        //		Image image = icon.getImage();
        //		return Toolkit.getDefaultToolkit().createCustomCursor(image, new Point(image.getWidth(null)/2, image.getHeight(null)/2), "Unselectable");
    }

    public void setAllPathMode() {
        allPathMode = true;
        singleEndPointsMode = false;
        multipleEndPointsMode = false;
        selectedNodes.clear();
        redoSelectedPath();
        repaint();
    }

    public void setSingleEndPointsMode() {
        allPathMode = false;
        singleEndPointsMode = true;
        multipleEndPointsMode = false;
        selectedNodes.clear();
        redoSelectedPath();
        repaint();
    }

    public void setMultipleEndPointsMode() {
        allPathMode = false;
        singleEndPointsMode = false;
        multipleEndPointsMode = true;
        selectedNodes.clear();
        redoSelectedPath();
        repaint();
    }

    public static void main(String args[]) {

        JFrame f = new JFrame();
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.setSize(400, 400);
        f.getContentPane().setLayout(new BorderLayout());
        JPanel p = new JPanel();
        p.setLayout(new GridLayout(0, 2));
        JLabel l = new JLabel("");
        l.setIcon(new ImageIcon(loadBuffImage("lia/images/so.gif")));
        p.add(l);
        l = new JLabel("");
        l.setIcon(new ImageIcon(loadGlowBuffImage("lia/images/so.gif")));
        p.add(l);
        f.getContentPane().add(p, BorderLayout.CENTER);
        f.setVisible(true);
    }
}
