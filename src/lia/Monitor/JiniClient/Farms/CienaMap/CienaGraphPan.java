package lia.Monitor.JiniClient.Farms.CienaMap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MediaTracker;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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
import java.util.LinkedList;
import java.util.Set;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JPanel;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.LayoutChangedListener;
import lia.Monitor.JiniClient.CommonGUI.Gmap.LayoutTransformer;
import lia.Monitor.JiniClient.Farms.CienaMap.layout.GraphNode;
import lia.Monitor.JiniClient.Farms.CienaMap.layout.GraphTopology;
import lia.Monitor.JiniClient.Farms.CienaMap.layout.GridLayoutAlgorithm;
import lia.Monitor.JiniClient.Farms.CienaMap.layout.LayeredTreeLayoutAlgorithm;
import lia.Monitor.JiniClient.Farms.CienaMap.layout.NoLayoutLayoutAlgorithm;
import lia.Monitor.JiniClient.Farms.CienaMap.layout.RadialLayoutAlgorithm;
import lia.Monitor.JiniClient.Farms.CienaMap.layout.RandomLayoutAlgorithm;
import lia.Monitor.JiniClient.Farms.CienaMap.layout.SpringLayoutAlgorithm;
import lia.Monitor.ciena.circuits.topo.CDCICircuitsHolder;
import lia.Monitor.ciena.circuits.topo.CircuitsHolder;
import lia.Monitor.ciena.circuits.topo.SNC;
import lia.Monitor.ciena.osrp.topo.OsrpLtp;
import lia.Monitor.ciena.osrp.topo.OsrpNode;
import lia.Monitor.ciena.osrp.topo.OsrpTopoHolder;
import lia.Monitor.ciena.osrp.topo.OsrpTopology;

public class CienaGraphPan extends JPanel implements MouseListener, MouseMotionListener, LayoutChangedListener,
        ActionListener {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(CienaGraphPan.class.getName());

    Hashtable nodes = new Hashtable();// local nodes

    static BufferedImage nodeShadow;

    static BufferedImage fakeShadow;

    static ConvolveOp blurOp;

    final static Color shadowColor = new Color(0.0f, 0.0f, 0.0f, 0.3f);

    private static CienaGraphPan _instance = null;

    private final CienaMapPan gmapPan;

    Color bgColor = Color.WHITE; // new Color(230, 230, 250);

    static Font osFont = new Font("Arial", Font.BOLD, 12);

    private final BufferedImage osImage;// optical switch image

    private int osImgWidth = 75;// set at loading iamge moment

    private int osImgHeight = 100;// set at loading iamge moment

    // layout & stuff
    GraphLayoutAlgorithm layout = null;

    GraphTopology vGraph = null;

    Object syncGraphObj = new Object();

    LayoutTransformer layoutTransformer;

    String currentLayout = "None";

    boolean currentTransformCancelled = false;

    volatile boolean showOnlyConnectedNodes;

    volatile boolean onlyDCN = false;

    SerMonitorBase monitor;

    private String linkToolTip = null;

    private String nodeToolTip = null;

    CienaNode pick;

    CienaNode pickX;

    private CienaLTP currentSelectedLink = null;

    private final Vector currentSelectedPath = new Vector();

    private CienaNode currentSelectedNode = null;

    private final Hashtable paths = new Hashtable();

    private final Vector links = new Vector();

    private boolean drawOSRP = true;

    private long startTimeOut = -1;// first time when panel has no nodes

    private static final long TIME_OUT = 14000;// time in miliseconds before removing this panel in inactive state

    public static final float fLayoutMargin = 0.02f;

    int wunit = 0;

    int hunit = 0;

    Rectangle range = new Rectangle();

    Rectangle actualRange = new Rectangle();

    public static synchronized final CienaGraphPan getInstance(CienaMapPan gmapPan) {
        if (_instance == null) {
            _instance = new CienaGraphPan(gmapPan);
        }
        return _instance;
    }

    private CienaGraphPan(CienaMapPan gmapPan) {
        this.gmapPan = gmapPan;
        // load image to represent optical switches and get its dimensions
        osImage = loadBuffImage("lia/images/ciena2.png");
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
    }

    public void setDrawOSRP(boolean drawOSRP) {
        this.drawOSRP = drawOSRP;
        repaint();
    }

    private final boolean first = true;

    /**
     * synchronizes local view of nodes set with global one by adding and removing nodes
     */
    public void syncNodes() {
        synchronized (getTreeLock()) {
            Set osrpNodes = OsrpTopoHolder.getAllOsrpNodeIDs();
            for (Iterator it = osrpNodes.iterator(); it.hasNext();) {
                try {
                    String osrpNodeID = it.next().toString();
                    OsrpTopology topo = OsrpTopoHolder.getOsrptopology(osrpNodeID);
                    if ((topo == null) || (topo.getAllNodesNames() == null) || (topo.getAllNodesNames().size() == 0)) {
                        continue;
                    }
                    Set nodesNames = topo.getAllNodesNames();
                    for (Iterator it1 = nodesNames.iterator(); it1.hasNext();) {
                        OsrpNode on = topo.getNodeWitName(it1.next().toString());
                        if ((on == null) || (on.locality != OsrpNode.LOCAL)) {
                            continue;
                        }
                        CienaNode n = null;
                        if (nodes.containsKey(on.name)) {
                            n = (CienaNode) nodes.get(on.name);
                        } else {
                            n = new CienaNode(on.name);
                            n.fixed = false;
                            n.selected = false;
                            n.x = (int) (getWidth() * Math.random());
                            n.y = (int) (getHeight() * Math.random());
                            n.osrpID = on.id;
                            n.ipAddress = on.ipAddress.getHostAddress();
                            nodes.put(on.name, n);
                        }
                        n.dirty = true;
                        if ((on.osrpLtpsMap == null) || (on.osrpLtpsMap.size() == 0)) {
                            continue;
                        }
                        for (Iterator it2 = on.osrpLtpsMap.keySet().iterator(); it2.hasNext();) {
                            Object id = it2.next();
                            OsrpLtp ol = (OsrpLtp) on.osrpLtpsMap.get(id);
                            String linkID = on.name + "-" + ol.rmtName;
                            CienaLTP ltp = null;
                            if (n.osrpLtpsMap.containsKey(linkID)) {
                                ltp = (CienaLTP) n.osrpLtpsMap.get(linkID);
                            } else {
                                ltp = new CienaLTP(id.toString());
                                n.osrpLtpsMap.put(linkID, ltp);
                            }
                            ltp.dirty = true;
                            ltp.osrpNode = on.name;
                            ltp.rmtName = ol.rmtName;
                            ltp.maxBW = ol.maxBW;
                            ltp.avlBW = ol.avlBW;
                            ltp.prio = ol.prio;
                            ltp.hState = ol.hState;
                            ltp.osrpCtps = ol.osrpCtps;
                        }
                    }
                } catch (Exception e) {
                }
            }
            Vector toRemove = new Vector();
            for (Enumeration en = nodes.keys(); en.hasMoreElements();) {
                Object key = en.nextElement();
                CienaNode n = (CienaNode) nodes.get(key);
                if (!n.dirty) {
                    toRemove.add(key);
                }
            }
            for (int i = 0; i < toRemove.size(); i++) {
                nodes.remove(toRemove.get(i));
            }
            toRemove.clear();
            for (Enumeration en = nodes.keys(); en.hasMoreElements();) {
                Object key = en.nextElement();
                CienaNode n = (CienaNode) nodes.get(key);
                n.dirty = false;
                for (Iterator it = n.osrpLtpsMap.keySet().iterator(); it.hasNext();) {
                    Object k = it.next();
                    CienaLTP l = (CienaLTP) n.osrpLtpsMap.get(k);
                    if (!l.dirty) {
                        toRemove.add(k);
                    }
                }
                for (int i = 0; i < toRemove.size(); i++) {
                    n.osrpLtpsMap.remove(toRemove.get(i));
                }
                toRemove.clear();
                for (Iterator it = n.osrpLtpsMap.keySet().iterator(); it.hasNext();) {
                    Object k = it.next();
                    CienaLTP l = (CienaLTP) n.osrpLtpsMap.get(k);
                    l.dirty = false;
                }
            }
            CircuitsHolder h = CircuitsHolder.getInstance();
            if (h.cdciMap == null) {
                gmapPan.swDropDown.setContent(new String[0]);
                gmapPan.sncDropDown.setContent(new String[0]);
            } else {
                String[] n = new String[h.cdciMap.size()];
                int i = 0;
                for (Iterator en = h.cdciMap.keySet().iterator(); en.hasNext() && (i < n.length);) {
                    String swName = (String) en.next();
                    n[i] = swName;
                    i++;
                }
                gmapPan.swDropDown.setContent(n);
                String names[] = h.getAllNodeNames();
                if (names != null) {
                    for (String swName : names) {
                        Hashtable pHash = null;
                        if (paths.containsKey(swName)) {
                            pHash = (Hashtable) paths.get(swName);
                        } else {
                            pHash = new Hashtable();
                            paths.put(swName, pHash);
                        }
                        CDCICircuitsHolder hh = getCDCICircuitHolder(swName);
                        if ((hh.sncMap == null) || (hh.sncMap.size() == 0)) {
                            continue;
                        }
                        for (Object element : hh.sncMap.keySet()) {
                            String pName = (String) element;
                            CienaPath p = null;
                            SNC s = hh.sncMap.get(pName);
                            if (pHash.containsKey(pName)) {
                                p = (CienaPath) pHash.get(pName);
                            } else {
                                p = new CienaPath(pName, swName, s.remoteNode);
                                pHash.put(pName, p);
                            }
                            p.setRate(s.rate.intValue());
                            p.redoPath(nodes);
                            // System.out.println(p);
                        }
                    }
                }
                redoSNCs();
            }
        }
    }

    private final CDCICircuitsHolder getCDCICircuitHolder(String name) {
        CircuitsHolder h = CircuitsHolder.getInstance();
        CDCICircuitsHolder hh[] = h.getAllCDCICircuits();
        if (hh == null) {
            return null;
        }
        for (CDCICircuitsHolder element : hh) {
            if (element.swName.equals(name)) {
                return element;
            }
        }
        return null;
    }

    public void redoSNCs() {
        synchronized (getTreeLock()) {
            // first get the list of sw selected...
            LinkedList l = new LinkedList();
            for (Iterator en = gmapPan.swDropDown.stores.keySet().iterator(); en.hasNext();) {
                String k = (String) en.next();
                if (k.equals("All") || k.equals("None")) {
                    continue;
                }
                if (gmapPan.swDropDown.getValueFor(k)) { // is selected...
                    CDCICircuitsHolder hh = getCDCICircuitHolder(k);
                    if ((hh.sncMap == null) || (hh.sncMap.size() == 0)) {
                        continue;
                    }
                    for (Object element : hh.sncMap.keySet()) {
                        String name = (String) element;
                        if (paths.containsKey(k)) {
                            Hashtable hPath = (Hashtable) paths.get(k);
                            if (hPath.containsKey(name)) {
                                CienaPath path = (CienaPath) hPath.get(name);
                                if (!path.pathCompleted()) {
                                    continue;
                                }
                            }
                        }
                        if (!l.contains(name)) {
                            if (onlyDCN) {
                                if (name.indexOf("dcs") >= 0) {
                                    l.addLast(name);
                                }
                            } else {
                                l.addLast(name);
                            }

                        }
                    }
                }
            }
            String s[] = new String[l.size()];
            int i = 0;
            for (Iterator it = l.iterator(); it.hasNext() && (i < s.length);) {
                s[i] = (String) it.next();
                i++;
            }
            gmapPan.sncDropDown.setContent(s);
        }
    }

    private final int howManyPaths(String sw1, String sw2) {
        int nr = 0;
        for (Enumeration en = paths.keys(); en.hasMoreElements();) {
            String swName = (String) en.nextElement();
            if (!gmapPan.swDropDown.getValueFor(swName)) {
                continue;
            }
            Hashtable pHash = (Hashtable) paths.get(swName);
            for (Enumeration en1 = pHash.keys(); en1.hasMoreElements();) {
                String pName = (String) en1.nextElement();
                if (gmapPan.sncDropDown.getValueFor(pName)) {
                    CienaPath p = (CienaPath) pHash.get(pName);
                    if (p.traverses(sw1, sw2)) {
                        nr++;
                    }
                }
            }
        }
        return nr;
    }

    public void setSerMonitor(SerMonitorBase monitor) {
        this.monitor = monitor;
    }

    /**
     * decides if this panel should stop: be removed from menu
     * 
     * @return
     */
    boolean shouldStop() {
        // System.out.println("Should stop function call.");
        // first sync the nodes list with global one
        syncNodes();
        // then check to see if this panel should be removed
        if (nodes.size() == 0) {
            // System.out.println("nodes size is 0");
            // if no nodes any more, remove the panel from left menu
            long currentTime = System.currentTimeMillis();
            if (startTimeOut == -1) {
                startTimeOut = currentTime;
            } else {
                if ((currentTime - startTimeOut) >= TIME_OUT) {
                    // the maximum waiting time has passed, so, remove the panel
                    monitor.main.removeGraphical("OS GMap");
                    // monitor.main.jpMenu.pack();
                    return true;
                }
            }
        } else {
            startTimeOut = -1;
        }
        return false;
    }

    class LayoutUpdateTimerTask extends TimerTask {

        Component parent;

        public LayoutUpdateTimerTask(Component parent) {
            this.parent = parent;
        }

        @Override
        public void run() {
            Thread.currentThread()
                    .setName(
                            " ( ML ) - CienaMap - CienaGraphPan Layout Update Timer Thread: checks stop condition and sets layout.");
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
                t.printStackTrace();
                logger.log(Level.WARNING, "Error executing", t);
            }
        }
    };

    void setLayoutType(String type) {
        // System.out.println("setLayoutType:"+type);
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
                vGraph = GraphTopology.constructCienaTree(nodes);
                if (type.equals("Random")) {
                    layout = new RandomLayoutAlgorithm(vGraph);
                } else if (type.equals("Grid")) {
                    layout = new GridLayoutAlgorithm(vGraph);
                } else {
                    layout = new NoLayoutLayoutAlgorithm(vGraph);
                }
                layoutTransformer.layoutChanged();
            }
        } else if (type.equals("Radial") || type.equals("Layered")) {
            synchronized (syncGraphObj) {
                unfixNodes();
                vGraph = GraphTopology.constructCienaTree(nodes);
                if ((pickX == null) || (!nodes.containsValue(pickX))) {
                    pickX = vGraph.findCienaRoot();
                }
                // System.out.println("layout "+type+" from "+pickX.UnitName);
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
                    vGraph = GraphTopology.constructCienaTree(nodes);
                    ((SpringLayoutAlgorithm) layout).updateGT(vGraph);
                }
            } else {
                synchronized (syncGraphObj) {
                    unfixNodes();
                    currentLayout = "Elastic";
                    vGraph = GraphTopology.constructCienaTree(nodes);
                    layout = new SpringLayoutAlgorithm(vGraph, this);
                    ((SpringLayoutAlgorithm) layout).setStiffness(gmapPan.sldStiffness.getValue());
                    ((SpringLayoutAlgorithm) layout).setRespRange(gmapPan.sldRepulsion.getValue());
                    layout.layOut();
                }
            }
        }
        // currentLayout = type;
    }

    void unfixNodes() {
        CienaNode n;
        for (Enumeration en = nodes.elements(); en.hasMoreElements();) {
            n = (CienaNode) en.nextElement();
            n.fixed = false;
        }
        ;
    }

    /**
     * loads a image and returns a reference to the loaded object
     * 
     * @param imageFileName
     * @return loaded image or null
     */
    public static BufferedImage loadBuffImage(String imageFileName) {
        BufferedImage res = null;
        try {
            if (imageFileName == null) {
                return null;
            }
            ClassLoader myClassLoader = CienaGraphPan.class.getClassLoader();
            URL imageURL;
            if (imageFileName.indexOf("://") >= 0) {
                imageURL = new URL(imageFileName);
            } else {
                imageURL = myClassLoader.getResource(imageFileName);
            }
            ImageIcon icon = new ImageIcon(imageURL);// imageFileName);//imageURL);
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
            // System.out.println("\nFailed loading image from "+imageFileName);
            // e.printStackTrace();
        }
        return res;
    }

    /**
     * loads a image and returns a reference to the loaded object
     * 
     * @param imageFileName
     * @return loaded image or null
     */
    public static BufferedImage loadGlowBuffImage(String imageFileName) {
        BufferedImage res = null;
        try {
            if (imageFileName == null) {
                return null;
            }
            ClassLoader myClassLoader = CienaGraphPan.class.getClassLoader();
            URL imageURL;
            if (imageFileName.indexOf("://") >= 0) {
                imageURL = new URL(imageFileName);
            } else {
                imageURL = myClassLoader.getResource(imageFileName);
            }
            ImageIcon icon = new ImageIcon(imageURL);// imageFileName);//imageURL);
            if (icon.getImageLoadStatus() != MediaTracker.COMPLETE) {
                throw new Exception("failed");
            }
            int width = icon.getIconWidth();
            int height = icon.getIconHeight();
            res = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D big = res.createGraphics();
            icon.paintIcon(new Component() {
            }, big, 0, 0);
            big.dispose();
        } catch (Exception e) {
            res = null;
            // System.out.println("\nFailed loading image from "+imageFileName);
            // e.printStackTrace();
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

    private final ConvolveOp getBlurOp(int size) {
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

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
        int x = e.getX();
        int y = e.getY();
        pick = null;
        for (Enumeration en = nodes.elements(); en.hasMoreElements();) {
            CienaNode n = (CienaNode) en.nextElement();
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
            if (currentLayout.equals("Elastic") || gmapPan.cbLayout.getSelectedItem().equals("Layered")
                    || gmapPan.cbLayout.getSelectedItem().equals("Radial")) {
                currentTransformCancelled = true;
                setLayoutType((String) gmapPan.cbLayout.getSelectedItem());
            }
        }
        e.consume();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if ((pick != null) /* && (e.getButton() == MouseEvent.BUTTON2) */) {
            pick.fixed = false;
            // pick = null;
        }
        // notify the thread that the position has changed
        if (layout instanceof SpringLayoutAlgorithm) {
            ((SpringLayoutAlgorithm) layout).notifyRunnerThread();
        }
        repaint();
        e.consume();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
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

    @Override
    public void mouseMoved(MouseEvent e) {
        final int x = e.getX();
        final int y = e.getY();
        CienaNode nodeOver = null;
        for (Enumeration en = nodes.elements(); en.hasMoreElements();) {
            CienaNode n = (CienaNode) en.nextElement();
            if ((n == null) || (n.limits == null) || (showOnlyConnectedNodes && !n.isLayoutHandled)) {
                continue;
            }
            if (n.limits.contains(x, y)) {
                nodeOver = n;
                break;
            }
        }
        if (nodeOver != null) {
            if ((currentSelectedNode == null) || !currentSelectedNode.equals(nodeOver)) {
                currentSelectedNode = nodeOver;
                currentSelectedLink = null;
                linkToolTip = null;
                nodeToolTip = formNodeToolTip();
            }
            return;
        }
        currentSelectedNode = null;
        nodeToolTip = null;
        synchronized (links) {
            String linkID = null;
            boolean linkSelected = false;
            currentSelectedPath.clear();
            for (int i = 0; i < links.size(); i++) {
                Object o = links.get(i);
                if (drawOSRP && (o instanceof Link)) {
                    Link l = (Link) o;
                    if (l.intersect(x, y)) {
                        linkID = l.linkID;
                        boolean shouldRedraw = (currentSelectedLink == null)
                                || (!currentSelectedLink.equals(l.realLink));
                        currentSelectedLink = l.realLink;
                        linkSelected = true;
                        if (shouldRedraw) {
                            repaint();
                        }
                        break;
                    }
                }
                if (!drawOSRP && (o instanceof PathLink)) {
                    PathLink l = (PathLink) o;
                    if (l.intersect(x, y)) {
                        currentSelectedPath.add(l.realLink);
                        linkSelected = true;
                        repaint();
                    }
                }
            }
            if (!linkSelected && (currentSelectedLink != null)) {
                currentSelectedLink = null;
                repaint();
            }
            if (linkSelected && (linkID == null)) {
                currentSelectedLink = null;
            }
            if ((linkID != null) || (currentSelectedPath.size() != 0)) {
                linkToolTip = formLinkToolTip();
                return;
            }
            linkToolTip = null;
        }
    }

    public String formLinkToolTip() {
        if ((currentSelectedLink == null) && (currentSelectedPath.size() == 0)) {
            return null;
        }
        StringBuilder buf = new StringBuilder();
        if (drawOSRP) {
            buf.append("<html><table BORDER CELLSPACING=0>");
            buf.append("<tr><td>Param</td><td>Value</td></tr>");
            buf.append("<tr><td>ID</td><td>").append(currentSelectedLink.id).append("</td></tr>");
            buf.append("<tr><td>fromNode</td><td>").append(currentSelectedLink.osrpNode).append("</td></tr>");
            buf.append("<tr><td>toNode</td><td>").append(currentSelectedLink.rmtName).append("</td></tr>");
            buf.append("<tr><td>maxBW</td><td>").append(currentSelectedLink.maxBW).append("</td></tr>");
            buf.append("<tr><td>avlBW</td><td>").append(currentSelectedLink.avlBW).append("</td></tr>");
            buf.append("<tr><td>prio</td><td>").append(currentSelectedLink.prio).append("</td></tr>");
            buf.append("<tr><td>helloState</td><td>").append(currentSelectedLink.hState).append("</td></tr>");
            StringBuilder b1 = new StringBuilder();
            if (currentSelectedLink.osrpCtps != null) {
                for (int i = 0; i < currentSelectedLink.osrpCtps.length; i++) {
                    if (i != 0) {
                        b1.append("&");
                    }
                    b1.append(currentSelectedLink.osrpCtps[i]);
                }
            }
            buf.append("<tr><td>CTP(s)</td><td>").append(b1.toString()).append("</td></tr>");
            buf.append("</table></html>");
        } else {
            if (currentSelectedPath.size() == 0) {
                return null;
            }
            buf.append("<html><table BORDER CELLSPACING=0>");
            for (int i = 0; i < currentSelectedPath.size(); i++) {
                CienaPath p = (CienaPath) currentSelectedPath.get(i);
                buf.append("<tr><td>").append(p.toString()).append("</td></tr>");
            }
            buf.append("</table></html>");
        }
        return buf.toString();
    }

    public String formNodeToolTip() {
        if (currentSelectedNode == null) {
            return null;
        }
        StringBuilder buf = new StringBuilder();
        buf.append("<html><table BORDER CELLSPACING=0>");
        buf.append("<tr><td>Param</td><td>Value</td></tr>");
        buf.append("<tr><td>SwName</td><td>").append(currentSelectedNode.UnitName).append("</td></tr>");
        buf.append("<tr><td>osrpID</td><td>").append(currentSelectedNode.osrpID).append("</td></tr>");
        buf.append("<tr><td>ipAddress</td><td>").append(currentSelectedNode.ipAddress).append("</td></tr>");
        buf.append("</table></html>");
        return buf.toString();
    }

    /**
     * when user over a node, return node configuration for real node ignore tooltip text
     */
    @Override
    public String getToolTipText(MouseEvent event) {
        if (nodeToolTip != null) {
            return nodeToolTip;
        }
        if (linkToolTip != null) {
            return linkToolTip;
        }
        return null;
    }

    @Override
    public int setElasticLayout() {
        // synchronized(syncRescale){
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        // range.setBounds(0, 0, getWidth(), getHeight());
        range.setBounds((int) (panelWidth * fLayoutMargin), (int) (panelHeight * fLayoutMargin),
                (int) (panelWidth * (1 - (2 * fLayoutMargin))), (int) (panelHeight * (1 - (2 * fLayoutMargin))));
        range.grow(-wunit - 2, -hunit - 2);
        // System.out.println("setting elastic layout");
        // set positions for the unhandled nodes
        // if(vGraph == layout.gt)
        int totalMovement = 0;
        for (Iterator it = vGraph.gnodes.iterator(); it.hasNext();) {
            GraphNode gn = (GraphNode) it.next();
            int nx = gn.rcnode.x;
            int ny = gn.rcnode.y;
            gn.rcnode.x = (int) Math.round(gn.pos.x);
            gn.rcnode.y = (int) Math.round(gn.pos.y);
            // if(layout.handled.contains(gn)){
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
            // }
        }
        repaint();
        return totalMovement;
    }

    @Override
    public void computeNewLayout() {
        synchronized (syncGraphObj) {
            if (!(layout instanceof NoLayoutLayoutAlgorithm)) {
                currentLayout = "SomeLayout";
            }
            // System.out.println("enter "+currentLayout+" class "+layout.getClass().getName());
            int panelWidth = getWidth();
            int panelHeight = getHeight();
            // range.setBounds(0, 0, getWidth(), getHeight());
            range.setBounds((int) (panelWidth * fLayoutMargin), (int) (panelHeight * fLayoutMargin),
                    (int) (panelWidth * (1 - (2 * fLayoutMargin))), (int) (panelHeight * (1 - (2 * fLayoutMargin))));
            range.grow(-wunit, -hunit);
            layout.layOut();

            if (layout instanceof NoLayoutLayoutAlgorithm) { // currentLayout.equals("None")){
                // System.out.println("exit1 "+currentLayout+" class "+layout.getClass().getName());
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
                for (Iterator it = vGraph.gnodes.iterator(); it.hasNext();) {
                    GraphNode gn = (GraphNode) it.next();
                    if (!gn.rcnode.fixed) {
                        int dx = (int) ((gn.pos.x - gn.rcnode.x) / (nSteps - i));
                        int dy = (int) ((gn.pos.y - gn.rcnode.y) / (nSteps - i));
                        // if(gn.rcnode.UnitName.equals("CALTECH"))
                        // System.out.println("moving C["+gn.rcnode.x+", "+gn.rcnode.y+"] -> ["+gn.pos.x+", "+gn.pos.y+"]");
                        // // by "+dx+", "+dy);
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
    public void actionPerformed(ActionEvent e) {
    }

    void rescaleIfNeeded() {
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        range.setBounds((int) (panelWidth * fLayoutMargin), (int) (panelHeight * fLayoutMargin),
                (int) (panelWidth * (1 - (2 * fLayoutMargin))), (int) (panelHeight * (1 - (2 * fLayoutMargin))));
        range.grow(-wunit, -hunit);
        boolean firstNode = true;
        for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
            CienaNode n = (CienaNode) e.nextElement();
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
            CienaNode n = (CienaNode) e.nextElement();
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
    public void paintComponent(Graphics g) {

        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                gmapPan.kbMakeNice.isSelected() ? RenderingHints.VALUE_ANTIALIAS_ON
                        : RenderingHints.VALUE_ANTIALIAS_OFF);

        synchronized (links) {
            links.clear();
        }
        Dimension d = getSize();
        g.setColor(bgColor);
        g.setFont(osFont);
        FontMetrics fm = g.getFontMetrics();
        g.fillRect(0, 0, d.width, d.height);

        // this also calls syncNodes so no need to have it called from paint
        shouldStop();
        if (nodes.size() == 0) {
            // if no nodes to be drawn, show a small message
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
        CienaLTP link;
        int nOrder;
        int i = 0;
        if (gmapPan.kbShowOS.isSelected()) {
            // more simple solution
            ArrayList alNodes = new ArrayList();
            // first get all nodes
            for (Enumeration e = nodes.elements(); e.hasMoreElements(); i++) {
                CienaNode n = (CienaNode) e.nextElement();
                alNodes.add(n);
            }
            ;
            int nNodes = alNodes.size();
            // create a matrix
            // int [][]matrixValues = new int[nNodes][nNodes];
            int j;
            // for (i=0; i<nNodes; i++) for (j=0; j<nNodes; j++) matrixValues[i][j] = 0;
            // for ( i=0; i<nNodes; i++) {
            // CienaNode n = (CienaNode)alNodes.get(i);
            // for (Iterator it = n.osrpLtpsMap.keySet().iterator(); it.hasNext(); ) {
            // link = (CienaLTP)n.osrpLtpsMap.get(it.next());
            // if (link.rmtName != null && nodes.containsKey(link.rmtName)) {
            // for ( j=0; j<nNodes; j++) {
            // Object o = alNodes.get(j);
            // if (o == null) continue;
            // if ( nodes.get(link.rmtName).equals(o))
            // break;
            // }
            // if ( j<nNodes ) { //destination node found
            // matrixValues[i][j]++;//increment pozition at [i][j]
            // }
            //						
            // }
            // }
            // }

            synchronized (links) {
                boolean selected = gmapPan.ckShowOSRP.isSelected();
                if (selected) { // draw connected OSRP links
                    // for each os link, check to see if already put
                    hOrderNodes.clear();
                    Vector linksDrawn = new Vector();
                    for (i = 0; i < nNodes; i++) {
                        CienaNode n = (CienaNode) alNodes.get(i);
                        CienaNode rcDest;
                        try {
                            for (Iterator it = n.osrpLtpsMap.keySet().iterator(); it.hasNext();) {
                                Object objkey = it.next();
                                link = (CienaLTP) n.osrpLtpsMap.get(objkey);
                                if (linksDrawn.contains(objkey)) {
                                    continue; // do not draw again
                                }
                                linksDrawn.add(objkey);
                                rcDest = (CienaNode) nodes.get(link.rmtName);
                                if (rcDest != null) {// check first to see if this is a valid link
                                    if (showOnlyConnectedNodes && (!n.isLayoutHandled || !rcDest.isLayoutHandled)) {
                                        continue;
                                    }
                                    // set new state if the case
                                    Color stateColor = null;
                                    // if transfering
                                    stateColor = link.getStateColor();
                                    // check for equivalent connection from the other node
                                    // -> that means to check if number of transfering links is greater than 0
                                    // get position of other node
                                    // for ( j=0; j<nNodes; j++)
                                    // if ( rcDest.equals(alNodes.get(j)))
                                    // break;
                                    // if (j < nNodes) {
                                    // if (matrixValues[i][j] > 0)
                                    // matrixValues[i][j]--;
                                    // }
                                    String key = link.id;
                                    Object objOrder = hOrderNodes.get(key);
                                    if (objOrder == null) {
                                        nOrder = 1;
                                    } else {
                                        nOrder = ((Integer) objOrder).intValue() + 1;
                                    }
                                    hOrderNodes.put(key, Integer.valueOf(nOrder));
                                    if (drawOSRP) {
                                        drawConn(g, link, n, rcDest, stateColor, nOrder);
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            // ignore exception
                            // ex.printStackTrace();
                        }
                    }
                } else { // draw connected data channels
                    // for each os link, check to see if already put
                    hOrderNodes.clear();
                    Vector linksDrawn = new Vector();
                    for (i = 0; i < nNodes; i++) {
                        CienaNode source = (CienaNode) alNodes.get(i);
                        if (!gmapPan.swDropDown.getValueFor(source.UnitName)) {
                            continue;
                        }
                        for (int l = 0; l < nNodes; l++) {
                            if (l == i) {
                                continue; // do not repeat...
                            }
                            CienaNode dest = (CienaNode) alNodes.get(l);
                            // int nr = howManyPaths(source.UnitName, dest.UnitName);
                            // if (nr == 0) continue;
                            for (Enumeration en = paths.keys(); en.hasMoreElements();) {
                                String swName = (String) en.nextElement();
                                final String key = source.UnitName + "_" + dest.UnitName;
                                Hashtable pHash = (Hashtable) paths.get(swName);
                                for (Enumeration en1 = pHash.keys(); en1.hasMoreElements();) {
                                    String pName = (String) en1.nextElement();
                                    if (gmapPan.sncDropDown.getValueFor(pName)) {
                                        CienaPath p = (CienaPath) pHash.get(pName);
                                        if (p.traverses(source.UnitName, dest.UnitName)) { // we found a path leading
                                            // from source to dest
                                            Object objOrder = hOrderNodes.get(key);
                                            if (objOrder == null) {
                                                nOrder = 1;
                                            } else {
                                                nOrder = ((((Integer) objOrder).intValue() + 1) % 20) + 1;
                                            }
                                            hOrderNodes.put(key, Integer.valueOf(nOrder));
                                            drawConn(g, p, source, dest, Color.red, nOrder);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }// end if show links
        for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
            CienaNode n = (CienaNode) e.nextElement();
            if (showOnlyConnectedNodes && !n.isLayoutHandled) {
                continue;
            }
            paintNode(g, n, fm);
        }
    }

    class Link {

        int x1, y1, x2, y2;

        String linkID;

        CienaLTP realLink;

        public Link(int x1, int y1, int x2, int y2, String linkID, CienaLTP realLink) {

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

    class PathLink {

        int x1, y1, x2, y2;

        String linkID;

        CienaPath realLink;

        public PathLink(int x1, int y1, int x2, int y2, String linkID, CienaPath realLink) {

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

    protected void drawConn(Graphics gg, CienaLTP link, CienaNode source, CienaNode dest, Color col, int nOrder) {

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

        Link ll = new Link(x1p, y1p, x2p, y2p, link.id, link);
        links.add(ll);

        Graphics2D g2 = (Graphics2D) gg;
        BasicStroke b = (BasicStroke) g2.getStroke();

        // compute the new stroke...
        if (drawOSRP) {
            BasicStroke stroke = null;
            if (link.avlBW < (link.maxBW * 0.05)) {
                stroke = new BasicStroke(b.getLineWidth() + 1.0f);
            } else {
                float val = (4.0f * link.avlBW) / link.maxBW;
                stroke = new BasicStroke(2.0f, b.getEndCap(), b.getLineJoin(), b.getMiterLimit(), new float[] { 16.0f,
                        val }, b.getDashPhase());
            }
            g2.setStroke(stroke);
            gg.drawLine(x1p, y1p, x2p, y2p);
            g2.setStroke(b);
        }

        // // compute the new stroke...
        // if (nrConn != 0) {
        // int r = (25 * nrConn);
        // if (r > 255) r = 255;
        // Color col2 = new Color(0, 0, r);
        // gg.setColor(col2);
        // gg.drawLine(x1p, y1p, x2p, y2p);
        // }

        int xv = (int) ((x1p + x2p + (((x1p - x2p) * (float) nOrder) / 20.0f)) / 2.0f);
        int yv = (int) ((y1p + y2p + (((y1p - y2p) * (float) nOrder) / 20.0f)) / 2.0f);

        float aa;// (float) (dd) / (float) 2.0;
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
        gg.setColor(Color.black);
    }

    public void drawConn(Graphics gg, CienaPath path, CienaNode source, CienaNode dest, Color col, int nOrder) {

        final int xSrc = source.x;
        final int ySrc = source.y;
        final int xDst = dest.x;
        final int yDst = dest.y;

        final int DD = 6;
        int dd;
        if (nOrder == 0) {
            dd = DD;
        } else {
            dd = 3 * nOrder;
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

        PathLink ll = new PathLink(x1p, y1p, x2p, y2p, path.sncName, path);
        links.add(ll);

        int rate = 1;
        if (path.rate > 20) {
            col = new Color(255, 0, 0);
            rate = path.rate - 20;
        } else if (path.rate > 14) {
            col = new Color(255, 48, 48);
            rate = path.rate - 14;
        } else if (path.rate > 10) {
            col = new Color(255, 110, 110);
            rate = path.rate - 10;
        } else {
            col = new Color(255, 175, 175);
        }

        int r = col.getRed();
        int g = col.getGreen();
        int b = col.getBlue();

        r = (int) ((r * (double) path.rate) / 5.0);
        if (r < 0) {
            r = 0;
        }
        if (r > 255) {
            r = 255;
        }
        g = (int) ((g * (double) path.rate) / 5.0);
        if (g < 0) {
            g = 0;
        }
        if (g > 255) {
            g = 255;
        }
        b = (int) ((b * (double) path.rate) / 5.0);
        if (b < 0) {
            b = 0;
        }
        if (b > 255) {
            b = 255;
        }
        int alpha = (int) (10 + ((255 * (double) path.rate) / 5.0));
        if (alpha < 10) {
            alpha = 10;
        }
        if (alpha > 255) {
            alpha = 255;
        }
        Color col2 = new Color(r, g, b, alpha);
        gg.setColor(col2);
        // gg.setColor(col);

        Graphics2D g2 = (Graphics2D) gg;
        BasicStroke bb = (BasicStroke) g2.getStroke();

        // compute the new stroke...
        BasicStroke stroke = new BasicStroke(bb.getLineWidth() + 1.0f);
        g2.setStroke(stroke);
        gg.drawLine(x1p, y1p, x2p, y2p);
        g2.setStroke(bb);

        int xv = (int) ((x1p + x2p + (((x1p - x2p) * (float) nOrder) / 35.0f)) / 2.0f);
        int yv = (int) ((y1p + y2p + (((y1p - y2p) * (float) nOrder) / 35.0f)) / 2.0f);

        // int xv = (int)((x1p + x2p) / 2.0f);
        // int yv = (int)((y1p + y2p) / 2.0f);

        float aa;// (float) (dd) / (float) 2.0;
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
        gg.setColor(Color.black);
    }

    public void paintNode(Graphics g, CienaNode n, FontMetrics fm) {

        int x = n.x;
        int y = n.y;
        String nodeName = n.UnitName;

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
        // draw new OS
        if (gmapPan.kbShowShadow.isSelected()) {
            ((Graphics2D) g).drawImage(nodeShadow, blurOp, x - (osImgWidth / 2), y - (osImgHeight / 2));
        }
        g.drawImage(osImage, x - (osImgWidth / 2), y - (osImgHeight / 2), null);

        // g.setColor(Color.white);
        // g.drawString(nodeName, n.limits.x + 7 + wunit - w2 -1,
        // n.limits.y +h2 - fm.getAscent() + 4 /*+ hunit - h2 + fm.getAscent()*/);
        // g.drawString(nodeName, n.limits.x + 7 + wunit - w2 +1,
        // n.limits.y - fm.getAscent() - 28/*+ hunit - h2 + fm.getAscent()*/);
        g.setColor(new Color(0, 0, 77));
        if (n.equals(pick)) {
            g.setColor(new Color(30, 30, 150));
        }
        g.drawString(nodeName, (n.limits.x + 7 + wunit) - w2, ((n.limits.y + h2) - (fm.getAscent() / 2)) + 5 /*
                                                                                                             * + hunit - h2 +
                                                                                                             * fm.getAscent()
                                                                                                             */);
    }
}
