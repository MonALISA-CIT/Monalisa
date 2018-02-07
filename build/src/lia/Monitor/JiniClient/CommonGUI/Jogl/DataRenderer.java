/*
 * Created on 30.05.2004 20:43:37 Filename: DataRenderer.java
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Jogl.Grids.BBBrowserLaunch;
import lia.Monitor.JiniClient.CommonGUI.Jogl.util.vcf;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ILink;
import lia.util.ntp.NTPDate;
import net.jini.core.lookup.ServiceID;

/**
 * @author Luc<br>
 * <br>
 *         DataRenderer - extends the renderer to display data/informations<br>
 *         listens for globe radius change and correct nodes positions accordinglly<br>
 */
public class DataRenderer extends ZoomMapRenderer implements GlobeListener {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(DataRenderer.class.getName());

    /**
     * hashmap that contains for each node some relevant graphical attributes, some are put by DataRenderer, some by
     * each NodeRenderer<br>
     * or, a pair "name" -> value object <br>
     * To check for a node, a instanceof check must be done.<br>
     * ATTENTION: this structure is NOT synchronized so it MUST be used only in one thread.<br>
     */
    /**
     * vector of 3 hashmaps: - first contains general data, for any node and any link and any renderer, grouped into a
     * hashmap - second contains a list of renderable nodes, each one with its properties - third contains a list of
     * renderable links, each one with its own list of +properties
     */
    private final Object[] graphicalAttributes = new Object[3];

    // list of available nodes renderer objects that implement NodesRendererInterface
    private final ArrayList NodesRendererList = new ArrayList();

    private final ArrayList NRNameList = new ArrayList();

    // TODO: remove the two arrays, make a hashtable
    // current active nodesrenderer
    private int activeNodesRenderer = -1;

    // private long lLastDrawNodesTime = -1; //last time nodes have beed drawn
    // private long lLastDrawNodesDuration = -1; //last nodes draw duration

    SphereRotator sr;// sphere rotator

    public ChangeProjectionStatus projectionStatus = new ChangeProjectionStatus();

    // list of listeners for globe events
    private static final ArrayList globeListenersList = new ArrayList();

    public static final int GLOBE_RADIUS_CHANGED = 1;

    public static final int GLOBE_RADIUS_CHANGE_START = 2;

    public static final int GLOBE_RADIUS_CHANGE_FINISH = 3;

    public static final int GLOBE_MOUSE_CLICK = 4;

    public static final int GLOBE_MOUSE_DBLCLICK = 5;

    public static final int GLOBE_OPTION_PANEL_CHANGE = 6;

    public static final int GLOBE_MOUSE_MOVE = 7;

    public static final int GLOBE_EVENT_PARAM_NODES_CHANGED = 1;

    public static final int GLOBE_EVENT_PARAM_LINKS_CHANGED = 2;

    public static final int GLOBE_EVENT_PARAM_RENDERER_CHANGED = 3;

    public DataRenderer() {
        graphicalAttributes[0] = new Hashtable();
        graphicalAttributes[1] = new HashMap();
        graphicalAttributes[2] = new HashMap();
        ((Hashtable) (graphicalAttributes[0])).put("DetailLevel_ShowLinksOnChangeProjection", new Object());
        ((Hashtable) (graphicalAttributes[0])).put("MouseOverObjects", new ArrayList());

        // very important the order: first the node renderer and then the data renderer
        // DataRenderer.addGlobeListener(((GridsNodesRenderer)getActiveNodesRenderer()));
        DataRenderer.addGlobeListener(this);

        // set the monitoring object for projection changing status
        DataRenderer.addGlobeListener(projectionStatus);

        // initialize the rotator thread. This thread can also be started in ZoomRenderer
        sr = new SphereRotator("JoGL - SphereRotator - Earth rotation thread");
        sr.setFrozen(JoglPanel.globals.bMapTransition2Sphere);
        DataRenderer.addGlobeListener(sr);
        sr.start();

        // start the update shadow timer
        Globals.doUpdateShadowTimer();

        if (JoglPanel.globals.mainPanel instanceof FarmsJoglPanel /*
                                                                  * &&
                                                                  * JoglPanel.globals.mainPanel.monitor.main.bGridsClient
                                                                  */) {
            // init list of vcf's
            vcf.readVcfList("lia/images/joglpanel/monalisa_team.vcf");
            logger.log(Level.INFO, "Reading vcf file monalisa_team.vcf, " + vcf.vcfList.size() + " entries found.");
        }
    }

    // sets the active nodes renderer
    public synchronized boolean setActiveNodesRenderer(int index) {
        if ((index < 0) || (index >= NodesRendererList.size())) {
            return false;
        }
        if (activeNodesRenderer == index) {
            return false;
        }
        activeNodesRenderer = index;
        // Globals.sendGlobeEvent( Globals.GLOBE_OPTION_PANEL_CHANGE, new
        // Integer(Globals.GLOBE_EVENT_PARAM_NODES_CHANGED));
        // JoglPanel.globals.mainPanel.comboNodesRenderer.setSelectedIndex(activeNodesRenderer);
        return true;
    }

    public synchronized boolean setActiveNodesRenderer(String name) {
        for (int i = 0; i < NRNameList.size(); i++) {
            Object nr = NodesRendererList.get(i);
            if (nr instanceof AbstractNodesRenderer) {
                AbstractNodesRenderer fnr = (AbstractNodesRenderer) nr;
                for (int j = 0; j < fnr.subViewCapabilities.length; j++) {
                    if (fnr.subViewCapabilities[j].equals(name)) {
                        fnr.changeSubView(j);
                        return true;
                    }
                }
            } else if (((String) NRNameList.get(i)).compareTo(name) == 0) {
                activeNodesRenderer = i;
                // Globals.sendGlobeEvent( Globals.GLOBE_OPTION_PANEL_CHANGE, new
                // Integer(Globals.GLOBE_EVENT_PARAM_NODES_CHANGED));
                return true;
            }
            ;
        }
        return false;
    }

    // gets the active nodes renderer
    public synchronized NodesRendererInterface getActiveNodesRenderer() {
        if (activeNodesRenderer != -1) {
            return (NodesRendererInterface) NodesRendererList.get(activeNodesRenderer);
        }
        return null;
    }

    // adds a nodes renderer object to the list
    public synchronized void addNodesRenderer( /* NodesRendererInterface */Object nr, String name) {
        for (int i = 0; i < NodesRendererList.size(); i++) {
            if (NodesRendererList.get(i) == nr) {
                return;// already added
            }
        }
        // else add this new nodes renderer
        NodesRendererList.add(nr);
        NRNameList.add(name);
        // also set active nodes renderer
        activeNodesRenderer = NodesRendererList.size() - 1;
        // JoglPanel.globals.mainPanel.comboNodesRenderer.addItem(name);
        // JoglPanel.globals.mainPanel.comboNodesRenderer.setSelectedIndex(activeNodesRenderer);
    }

    // removes a renderer from the list
    public synchronized boolean removeNodesRenderer( /* NodesRendererInterface */Object nr, String name) {
        // change the active nodes renderer
        NRNameList.remove(name);
        return NodesRendererList.remove(nr);
    }

    public synchronized int getNodesRendererByName(String name) {
        for (int i = 0; i < NRNameList.size(); i++) {
            if (((String) NRNameList.get(i)).compareTo(name) == 0) {
                return i;
            }
        }
        return -1;
    }

    public synchronized String getNodesRendererById(int id) {
        return (String) NRNameList.get(id);
    }

    /**
     * inits all nodes renderers from list it must be called after all renderers have been added to list
     *
     * @param gl
     */
    public synchronized void initNodesRenderers(GL2 gl) {
        // System.out.println("init nodes renderers");
        for (int i = 0; i < NodesRendererList.size(); i++) {
            Object nri = NodesRendererList.get(i);
            if (nri instanceof NodesRendererInterface) {
                ((NodesRendererInterface) nri).initNodes(gl, graphicalAttributes);
            }
        }
        ;
    }

    public synchronized int getNodesRenderersSize() {
        return NodesRendererList.size();
    }

    public synchronized void addNodesRendererAction(int id, ButtonGroup bg, JPanel jp, Dimension dim, Font font) {
        Object nr = NodesRendererList.get(id);
        if (nr instanceof AbstractNodesRenderer) {
            AbstractNodesRenderer fnr = (AbstractNodesRenderer) nr;
            for (int i = 0; i < fnr.subViewCapabilities.length; i++) {
                JRadioButton radioBut;
                radioBut = new JRadioButton(fnr.subViewCapabilities[i]);
                radioBut.setFont(font);
                radioBut.setPreferredSize(dim);
                radioBut.setMaximumSize(dim);
                radioBut.setMinimumSize(dim);
                radioBut.setActionCommand("changeNodesRenderer");
                radioBut.addActionListener(uil);
                if ((id == 0) && (i == 0)) {
                    radioBut.setSelected(true);
                }
                bg.add(radioBut);
                jp.add(radioBut);
            }
        } else {
            JRadioButton radioBut;
            radioBut = new JRadioButton((String) NRNameList.get(id));
            radioBut.setFont(font);
            radioBut.setPreferredSize(dim);
            radioBut.setMaximumSize(dim);
            radioBut.setMinimumSize(dim);
            radioBut.setActionCommand("changeNodesRenderer");
            radioBut.addActionListener(uil);
            if (id == 0) {
                radioBut.setSelected(true);
            }
            bg.add(radioBut);
            jp.add(radioBut);
        }
        ;
    }

    /**
     * synchronizes local snapshot of node array with global one by adding new graphical properties to new nodes, or
     * remving those that doesn't appear in global hash, but only in local<br>
     * It should be called now and then, not at every repaint...
     */
    public void organizeNodes(Map<ServiceID, rcNode> hNodes) {
        // long startTime = System.currentTimeMillis();
        Object obj;
        try {
            // for each node in global hash
            for (final rcNode node : hNodes.values()) {
                // test to see if nodes in local hash
                obj = ((HashMap) graphicalAttributes[1]).get(node);
                if ((obj == null) && !node.bHiddenOnMap) {// if not, add it
                    HashMap hMap = new HashMap();
                    ((HashMap) graphicalAttributes[1]).put(node, hMap);
                    hMap.put("LAT", node.LAT);
                    hMap.put("LONG", node.LONG);
                    computeVector(node);
                } else {// else check to see only if its position changed
                    HashMap hAttrs = (HashMap) obj;
                    if (hAttrs != null) {
                        if (!hAttrs.get("LAT").equals(node.LAT) || !hAttrs.get("LONG").equals(node.LONG)) {
                            hAttrs.put("LAT", node.LAT);
                            hAttrs.put("LONG", node.LONG);
                            computeVector(node);
                        }
                    }
                }
            }
            ;
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Exception updating snapshot of snodes at append opperation: " + ex.getMessage());
            ex.printStackTrace();
        }
        try {
            // check if a node from snodes was eliminated
            for (Iterator it = ((HashMap) graphicalAttributes[1]).keySet().iterator(); it.hasNext();) {
                obj = it.next();
                rcNode node = (rcNode) obj;
                boolean exists = true;
                if (!hNodes.containsKey(node.sid)) {
                    exists = false;
                } else {
                    rcNode n1 = hNodes.get(node.sid);
                    exists = n1.equals(node);
                }
                if ( /* JoglPanel.dglobals.snodes.contains(node) */(exists == false) || node.bHiddenOnMap) {
                    it.remove();// graphicalAttributes[1].remove(node);
                }
            }
            // if ( bNewNode )
            // computeVectors( false);//new nodes, so compute for them their vectors
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Exception updating snapshot of snodes at remove opperation: " + ex.getMessage());
            ex.printStackTrace();
        }
        // long endTime = System.currentTimeMillis();
        // logger.log( Level.INFO, "duration: "+(endTime-startTime)+" ms");
    }

    /**
     * synchronizes local snapshot of node array with global one by adding new graphical properties to new nodes, or
     * remving those that doesn't appear in global hash, but only in local
     */
    public void organizeLinks() {
        // long startTime = NTPDate.currentTimeMillis();
        Object objKey, objVal;
        rcNode node, node2;
        ServiceID sidTo;
        ILink link;
        HashMap hLinkAttrs;
        Hashtable hLinks;
        // for each node in local hash, because there is no point of drawing a link to or from an non existing node...
        for (Iterator it = ((HashMap) graphicalAttributes[1]).keySet().iterator(); it.hasNext();) {
            node = (rcNode) it.next();
            hLinks = getActiveNodesRenderer().getLinks(node);
            try {
                if (hLinks != null) {
                    // for each link, check to see if already put
                    for (Iterator it2 = hLinks.keySet().iterator(); it2.hasNext();) {
                        objKey = it2.next();
                        objVal = hLinks.get(objKey);
                        link = null;
                        if ((objVal != null) && (objVal instanceof ILink)) {
                            link = (ILink) objVal;
                        }
                        node2 = null;
                        sidTo = null;
                        if (objKey instanceof ServiceID) {
                            // based on sid, get destination node
                            sidTo = (ServiceID) objKey;
                            node2 = getNodeBasedOnSID(sidTo);
                        } else if (objKey instanceof rcNode) {
                            node2 = (rcNode) objKey;
                        } else if (link != null) {// should never do such a checking
                            node2 = getNodeBasedOnIP(link.toIP);
                        }
                        ;
                        if (node2 == null) {
                            // System.out.println("Ingnore link "+link.name+" with no computable destination");
                            continue;// ignore link for which a destination node could not be computed
                        }
                        ;
                        sidTo = node2.sid;
                        // create new links
                        if (link == null) {
                            link = new ILink(node.sid + "->" + node2.sid);
                            link.fromLAT = DataGlobals.failsafeParseFloat(node.LAT, -21.22f);
                            link.fromLONG = DataGlobals.failsafeParseFloat(node.LONG, -111.15f);
                            link.toLAT = DataGlobals.failsafeParseFloat(node2.LAT, -21.22f);
                            link.toLONG = DataGlobals.failsafeParseFloat(node2.LONG, -111.15f);
                            link.speed = node.connPerformance(node2);
                            link.data = Double.valueOf(node.connLP(node2));
                            // System.out.println("New link created: "+link.name);
                        }
                        if (getActiveNodesRenderer().isValidLink(link)) {
                            objVal = ((HashMap) graphicalAttributes[2]).get(link);
                            if (objVal == null) {
                                hLinkAttrs = new HashMap();
                                // remember parent nodes
                                hLinkAttrs.put("fromSID", node.sid);
                                hLinkAttrs.put("toSID", sidTo);
                                hLinkAttrs.put("fromNode", node);
                                hLinkAttrs.put("toNode", node2);
                                ((HashMap) graphicalAttributes[2]).put(link, hLinkAttrs);
                            }
                            ;
                            hLinkAttrs = (HashMap) ((HashMap) graphicalAttributes[2]).get(link);
                            // if link ok, check source and destination coordinates, and update values if neccessary
                            float srcLat, srcLong, dstLat, dstLong;
                            srcLat = DataGlobals.failsafeParseFloat(node.LAT, -21.22f);
                            srcLong = DataGlobals.failsafeParseFloat(node.LONG, -111.15f);
                            dstLat = DataGlobals.failsafeParseFloat(node2.LAT, -21.22f);
                            dstLong = DataGlobals.failsafeParseFloat(node2.LONG, -111.15f);
                            if ((srcLat != link.fromLAT) || (srcLong != link.fromLONG) || (dstLat != link.toLAT)
                                    || (dstLong != link.toLONG)) {
                                // update link's start and end coordinates
                                link.fromLAT = srcLat;
                                link.fromLONG = srcLong;
                                link.toLAT = dstLat;
                                link.toLONG = dstLong;
                                // I don't think that's neccessary because on next invalidate the new coordinates will
                                // be used
                                // hLinkAttrs.put("invalidateThisLink", new Object());
                            }
                        } else {
                            // invalid link
                            // System.out.println("Link "+link.name+" is invalid");
                        }
                    }
                }
                ;
            } catch (Exception ex) {
                logger.log(Level.INFO, "Exception checking link for node " + (node != null ? node.UnitName : "unknown")
                        + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        }
        ;
        try {
            // check if a node's link from wconn was eliminated
            for (Iterator it = ((HashMap) graphicalAttributes[2]).keySet().iterator(); it.hasNext();) {
                Object objLink = it.next();
                hLinkAttrs = (HashMap) ((HashMap) graphicalAttributes[2]).get(objLink);
                if (getActiveNodesRenderer().isDeadLink(objLink, hLinkAttrs, ((HashMap) graphicalAttributes[1]))) {
                    it.remove();
                }
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception checking links removal: " + ex.getMessage());
            ex.printStackTrace();
        }
        // long endTime = NTPDate.currentTimeMillis();
        // logger.log( Level.INFO, "duration: "+(endTime-startTime)+" ms");
    }

    /**
     * computes for one node its graphical properties like: position and direction vectors
     *
     * @param node
     *            the node for which to recompute
     */
    public void computeVector(rcNode node) {
        float[] coords = new float[3];
        VectorO[] vDandP;// array of two vectors: direction and position; Vdir, Vpos;
        Object obj;// testing object
        HashMap hAttrs = (HashMap) ((HashMap) graphicalAttributes[1]).get(node);
        obj = hAttrs.get("PositioningVectors");
        if (obj == null) {
            vDandP = new VectorO[2];
        } else {
            vDandP = (VectorO[]) obj;
        }

        // System.out.println("node "+node.UnitName+" LAT="+node.LAT+" LONG="+node.LONG);
        Globals.point2Dto3D(DataGlobals.failsafeParseLAT(node.LAT, -21.22f),
                DataGlobals.failsafeParseLONG(node.LONG, -111.15f), coords);
        // System.out.println("node "+node.UnitName+" parsed LAT="+DataGlobals.failsafeParseFloat(node.LAT,
        // -21.22f)+" parse LONG="+DataGlobals.failsafeParseFloat(node.LONG, -111.15f));
        // position vector initialised with transformed coordinates from lat and long to world x,y,z, taking in account
        // the globe radius
        vDandP[1] = new VectorO(coords[0], coords[1], coords[2]);
        // init direction vector, 2 cases, for plane projection and for globe projection
        if (JoglPanel.globals.globeRadius != -1) {// sphere
            vDandP[0] = new VectorO(coords[0], coords[1], (coords[2] - JoglPanel.globals.globeVirtualRadius)
                    + JoglPanel.globals.globeRadius);
            vDandP[0].Normalize();
        } else {// plane
            vDandP[0] = new VectorO(0, 0, 1);
        }
        ;
        // set vectors for node
        if (obj == null) {
            hAttrs.put("PositioningVectors", vDandP);
        }
    }

    /**
     * computes for each node its graphical properties like: position and direction vectors
     */
    public void computeVectors() {
        rcNode node;
        // float [] coords = new float[3];
        // VectorO []vDandP;//array of two vectors: direction and position; Vdir, Vpos;
        // Object obj;//testing object
        for (Iterator it = ((HashMap) graphicalAttributes[1]).keySet().iterator(); it.hasNext();) {
            node = (rcNode) it.next();
            computeVector(node);
        }
        ;
    }

    /**
     * computes node radius so that it will always have nRadiusPixels, for any Z depth and window size.<br>
     * stores the computed radius into graphicalAttributes<br>
     * <br>
     * Algorithm:<br>
     * <br>
     * 1. first consider the distance from eye to map, this is the Z depth as the distance from eye to closest point on
     * the map<br>
     * That means for sphere projection, eye_pos-virtual_radius,<br>
     * and for plane projection, eye_pos projected on (0,0,1) => z_eye_pos<br>
     * <br>
     * 2. compute the viewable width (fx) for current chosen depth, that means how much of the world can be seen on x
     * axis at the given depth<br>
     * The formula:<br>
     * fx = zDepth*tg(FOV_ANGLE)<br>
     * fx is half of the viewable width<br>
     * <br>
     * 3. Using "regula de 3 simpla", the radius is:<br>
     * radius = nRadiusPixels*fx/width,<br>
     * where width is the width of the window.<br>
     */
    public void computeRadius() {
        // number of pixels that the radius will have when eye is normal to plane map,
        // at the intersection of eye direction with map
        double multiplicationFactor = 15;
        if ((JoglPanel.globals.mainPanel.monitor != null) && JoglPanel.globals.mainPanel.monitor.main.bGridsClient) {
            multiplicationFactor = 10;
        }
        final int nRadiusPixels = (int) (multiplicationFactor * Math.pow(2,
                (JoglPanel.globals.nScaleFactor - 50) / 15.0));
        // System.out.println("scale factor: "+JoglPanel.globals.nScaleFactor+" nRadiusPixels: "+nRadiusPixels);
        float radius;
        // 1. compute depth
        float zDepth;
        if (JoglPanel.globals.globeRadius != -1) {// sphere
            zDepth = (float) JoglPanel.globals.EyePosition.getRadius() - JoglPanel.globals.globeVirtualRadius;
        } else {// plane
            zDepth = JoglPanel.globals.EyePosition.getZ();
        }
        ;
        // if depth greater than a certain value, don't modify radius to correspond to a number of pixels
        // so, let it be the same, so that the units will appear smaller
        // and already a radius is computed
        if ((zDepth > 30f) && (((Hashtable) graphicalAttributes[0]).get("NodeRadius") != null)) {
            return;
        }
        // 2. compute fx
        float fx;
        fx = zDepth * (float) Math.tan(((Globals.FOV_ANGLE / 2f) * Math.PI) / 180.0f);
        // 3. radius
        radius = (nRadiusPixels * fx) / JoglPanel.globals.width;
        // update the radius in graphicalAttributes
        ((Hashtable) graphicalAttributes[0]).put("NodeRadius", new Float(radius));
    }

    // private Object syncObject = new Object();
    /**
     * globe radius changed event
     */
    @Override
    public void radiusChanged() {
        // boolean bInvalidateLinks = false;
        // synchronized(syncObject) {
        // do not call organize because we don't search for new nodes at this stage
        computeVectors();
        // };
        Texture.checkVisibility();
        // if ( bInvalidateLinks )
        JoglPanel.globals.canvas.repaint();
        // System.out.println("radius changed");
    }

    /**
     * useful variable to store the status of the links animation during globe transformation
     */
    // private boolean linkAnimationStatus=false;

    @Override
    public void radiusChangeStart() {
        // synchronized(syncObject) {
        ((Hashtable) graphicalAttributes[0]).put("InvalidateLinksStart", new Object());
        // };
        // linkAnimationStatus = getActiveNodesRenderer().getLinkAnimationStatus();
        // getActiveNodesRenderer().changeLinkAnimationStatus(false);
        Texture.checkVisibility();
        JoglPanel.globals.canvas.repaint();
    }

    @Override
    public void radiusChangeFinish() {
        // synchronized(syncObject) {
        ((Hashtable) graphicalAttributes[0]).put("InvalidateLinksFinish", new Object());
        // ((Hashtable)graphicalAttributes[0]).remove("InvalidateLinks");
        ((Hashtable) graphicalAttributes[0]).remove("InvalidateLinksStart");
        // };
        // getActiveNodesRenderer().changeLinkAnimationStatus(linkAnimationStatus);
        Texture.checkVisibility();
        JoglPanel.globals.canvas.repaint();
    }

    @Override
    public void optionPanelChanged(int event) {
        // synchronized(syncObject) {
        if (event == DataRenderer.GLOBE_EVENT_PARAM_NODES_CHANGED) {
            ((Hashtable) graphicalAttributes[0]).put("RecomputeNodes", new Object());
        } else if (event == DataRenderer.GLOBE_EVENT_PARAM_LINKS_CHANGED) {
            ((Hashtable) graphicalAttributes[0]).put("InvalidateLinks", new Object());
        } else if (event == DataRenderer.GLOBE_EVENT_PARAM_RENDERER_CHANGED) {
            ((Hashtable) graphicalAttributes[0]).put("RecomputeNodes", new Object());
            ((Hashtable) graphicalAttributes[0]).put("InvalidateLinks", new Object());
        }
        // it is neccessary to have invalidate because recompute only recalculates for new links,
        // while invalidate recomputes for all links
        // ((Hashtable)graphicalAttributes[0]).put("RecomputeLinks", new Object());
        // };
        JoglPanel.globals.canvas.repaint();
    }

    /**
     * selects nodes that are under the mouse cursor
     *
     * @param LONG
     * @param LAT
     * @return Returns list of nodes
     */
    private ArrayList getSelectedNode(float LONG, float LAT) {
        ArrayList al = new ArrayList();
        // rcNode nSelNode = null;
        try {
            // synchronized(syncObject) {
            // get radius
            Object obj;
            obj = ((Hashtable) graphicalAttributes[0]).get("NodeRadius");
            float radius = 0.01f;
            if (obj != null) {
                radius = ((Float) obj).floatValue();
            }

            float d_lat = (180 * radius) / Globals.MAP_HEIGHT;
            float d_long = (360 * radius) / Globals.MAP_WIDTH;
            // System.out.println("mouse click coordinates: lat="+LAT+" long="+LONG+" radius:"+radius+" MAP_WIDTH="+Globals.MAP_WIDTH+" dx="+d_long+
            // " dy="+d_lat);
            for (Iterator it = ((HashMap) graphicalAttributes[1]).keySet().iterator(); it.hasNext();) {
                rcNode n = (rcNode) it.next();
                if (!n.bHiddenOnMap) {// if node should not be visible on map, don't check it
                    float n_lat, n_long;
                    try {
                        n_lat = DataGlobals.failsafeParseLAT(n.LAT, -21.22f);
                        n_long = DataGlobals.failsafeParseLONG(n.LONG, -111.15f);
                    } catch (NumberFormatException nfex) {
                        n_lat = 0;
                        n_long = 0;
                    }
                    // System.out.println("node "+n.UnitName+" LAT="+n.LAT+" LONG="+n.LONG);
                    // System.out.println("Checking node with lat="+n_lat+" long="+n_long);
                    if ((Math.abs(LAT - n_lat) < d_lat) && (Math.abs(LONG - n_long) < d_long)) {
                        al.add(n);
                        // changed to standardize
                    }
                }
            }
            // };//end synchronize
        } catch (Exception ex) {
            // exeption during checking of nodes
        }
        return al;
    }

    /**
     * functions much like getSelectedNode<br>
     * only that returns a more standardized list, see @see getSelectedLinks
     *
     * @param LONG
     * @param LAT
     * @return
     */
    private ArrayList getSelectedNodes(int mouse_x, int mouse_y, ArrayList alSelectedObjects) {
        float[] map_coordinates = UserInputListener.getPointOnMap(mouse_x, mouse_y, null);
        if (map_coordinates == null) {
            return alSelectedObjects;
        }
        float LONG = map_coordinates[0], LAT = map_coordinates[1];
        // rcNode nSelNode = null;
        try {
            // synchronized(syncObject) {
            // get radius
            Object obj;
            obj = ((Hashtable) graphicalAttributes[0]).get("NodeRadius");
            float radius = 0.01f;
            if (obj != null) {
                radius = ((Float) obj).floatValue();
            }

            float d_lat = (180 * radius) / Globals.MAP_HEIGHT;
            float d_long = (360 * radius) / Globals.MAP_WIDTH;
            // System.out.println("mouse click coordinates: lat="+LAT+" long="+LONG+" radius:"+radius+" MAP_WIDTH="+Globals.MAP_WIDTH+" dx="+d_long+
            // " dy="+d_lat);
            for (Iterator it = ((HashMap) graphicalAttributes[1]).keySet().iterator(); it.hasNext();) {
                rcNode n = (rcNode) it.next();
                if (!n.bHiddenOnMap) {// if node should not be visible on map, don't check it
                    float n_lat, n_long;
                    try {
                        n_lat = DataGlobals.failsafeParseLAT(n.LAT, -21.22f);
                        n_long = DataGlobals.failsafeParseLONG(n.LONG, -111.15f);
                    } catch (NumberFormatException nfex) {
                        n_lat = 0;
                        n_long = 0;
                    }
                    // System.out.println("node "+n.UnitName+" LAT="+n.LAT+" LONG="+n.LONG);
                    // System.out.println("Checking node with lat="+n_lat+" long="+n_long);
                    if ((Math.abs(LAT - n_lat) < d_lat) && (Math.abs(LONG - n_long) < d_long)) {
                        // changed compared to getSelectedNode in order to standardize
                        if (alSelectedObjects == null) {
                            alSelectedObjects = new ArrayList();
                        }
                        HashMap hObjAttrs = new HashMap();
                        hObjAttrs.put("Position", new float[] { n_long, n_lat });
                        hObjAttrs.put("Type", "node");
                        NodesRendererInterface nri = getActiveNodesRenderer();
                        nri.fillSelectedNodeInfo(n, hObjAttrs);
                        // TODO: put other attrs like ones available in vcf
                        alSelectedObjects.add(hObjAttrs);
                    }
                }
            }
            // };//end synchronize
        } catch (Exception ex) {
            // exeption during checking of nodes
        }
        return alSelectedObjects;
    }

    /**
     * returns links that have their arrow under mouse cursor as a list of maps, each map containing information like:<br>
     * Name -> text<br>
     * Description -> text<br>
     * Position -> VectorO<br>
     * Type -> text<br>
     * Name and Position and Type should not miss from attributes as they are the most relevant attributes.<br>
     *
     * @param mouse_x
     * @param mouse_y
     * @param alSelectedObjects
     *            list of maps or null if not initialized yet
     */
    // private ArrayList getSelectedLinks(int mouse_x, int mouse_y, ArrayList alSelectedObjects) {
    // VectorO vDirection = UserInputListener.getVectorToPointOnMap( mouse_x, mouse_y);
    // VectorO vEye = new VectorO(JoglPanel.globals.EyePosition);
    // return getSelectedLinks( vEye, vDirection, alSelectedObjects);
    // }

    /**
     * @see getSelectedLinks(int, int, ArrayList)
     * @param vEye
     * @param vDirection
     * @param alSelectedObjects
     * @return alSelectedObjects or new list
     */
    private ArrayList getSelectedLinks(VectorO vEye, VectorO vDirection, ArrayList alSelectedObjects) {
        try {
            HashMap hLinkAttrs;
            // synchronized(syncObject) {//synchronized because probably of graphicalAttributes
            // get radius
            Object obj;
            obj = ((Hashtable) graphicalAttributes[0]).get("NodeRadius");
            float radius = 0.01f;
            if (obj != null) {
                radius = ((Float) obj).floatValue();
            }
            // check if a node's link from wconn was eliminated
            for (Iterator it = ((HashMap) graphicalAttributes[2]).keySet().iterator(); it.hasNext();) {
                Object objLink = it.next();
                hLinkAttrs = (HashMap) ((HashMap) graphicalAttributes[2]).get(objLink);
                VectorO vPoint = (VectorO) hLinkAttrs.get("LinkArrowBase");
                // System.out.println("show link at"+vPoint);
                if ((vPoint != null) && Globals.sphereIntersection(vEye, vDirection, vPoint, radius / 2)) {
                    HashMap hObjAttrs = new HashMap();// create hashmap to put this link's attributes in it
                    // also set position and type by default
                    hObjAttrs.put("Position", vPoint);
                    hObjAttrs.put("Type", "link");
                    // give the possibility to change them
                    NodesRendererInterface nri = getActiveNodesRenderer();
                    if (!nri.fillSelectedLinkInfo(objLink, hLinkAttrs, hObjAttrs)) {// renderer is not filling
                        // neccessary infos
                        String sName = "Inet link ";// hLinkAttrs.get("LinkArrowBase");
                        rcNode nFrom = (rcNode) hLinkAttrs.get("fromNode");
                        if (nFrom != null) {
                            sName += nFrom.UnitName;
                        }
                        rcNode nTo = (rcNode) hLinkAttrs.get("toNode");
                        if (nTo != null) {
                            sName += "->" + nTo.UnitName;
                        }

                        hObjAttrs.put("Name", sName);
                        StringBuilder sDesc = new StringBuilder();
                        if ((nFrom != null) && (nTo != null)) {
                            sDesc.append("RTT: ");
                            if (nFrom.connRTT(nTo) != -1) {
                                sDesc.append(nFrom.connRTT(nTo));
                            } else {
                                sDesc.append("??");
                            }
                            sDesc.append(" ms\nLost Pkgs: ");
                            if (nFrom.connLP(nTo) != -1) {
                                sDesc.append((nFrom.connLP(nTo) * 100));
                            } else {
                                sDesc.append("??");
                            }
                            sDesc.append(" %\nRTTime: ");
                            if (nFrom.connPerformance(nTo) != -1) {
                                sDesc.append((int) nFrom.connPerformance(nTo));
                            } else {
                                sDesc.append("??");
                            }
                        } else {
                            sDesc.append(objLink.toString());
                        }
                        hObjAttrs.put("Description", sDesc.toString());
                    }
                    ;
                    if (alSelectedObjects == null) {
                        alSelectedObjects = new ArrayList();
                    }
                    alSelectedObjects.add(hObjAttrs);
                }
                ;
            }
            // }
        } catch (Exception ex) {
            logger.log(Level.INFO,
                    "Exception checkin links that are selected given mouse_x and mouse_y: " + ex.getMessage());
            ex.printStackTrace();
        }
        return alSelectedObjects;
    }

    private ArrayList getOtherSelectedObjects(VectorO vEye, VectorO vDirection, ArrayList alSelectedObjects) {
        // get radius
        Object obj;
        obj = ((Hashtable) graphicalAttributes[0]).get("NodeRadius");
        float radius = 0.01f;
        if (obj != null) {
            radius = ((Float) obj).floatValue();
        }
        alSelectedObjects = getActiveNodesRenderer().getOtherSelectedObjects(vEye, vDirection, radius,
                alSelectedObjects);
        if (alSelectedObjects == null) {
            // try to select cities if nothing else selected
            alSelectedObjects = getCitiesSelected(vEye, vDirection, alSelectedObjects);
        }
        return alSelectedObjects;
    }

    private ArrayList getCitiesSelected(VectorO vEye, VectorO vDirection, ArrayList alSelectedObjects) {
        if ((JoglPanel.globals.mapAngle != 90) && (JoglPanel.globals.mapAngle != 0)) {
            return alSelectedObjects;
        }
        float[] coords = null;
        VectorO vPoint;
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(2);
        float full_radius = (Globals.MAP_WIDTH * .5f) / (float) Math.PI;
        float radius = (full_radius / 360f) * .5f;
        if (JoglPanel.globals.wcityGIS != null) {
            for (int i = 0; i < JoglPanel.globals.wcityGIS.getTotalNumber(); i++) {
                coords = Globals.point2Dto3D(JoglPanel.globals.wcityGIS.fLat[i], JoglPanel.globals.wcityGIS.fLong[i],
                        coords, 0.001f);
                vPoint = new VectorO(coords);
                // System.out.println("show link at"+vPoint);
                if ((vPoint != null) && Globals.sphereIntersection(vEye, vDirection, vPoint, radius)) {
                    HashMap hObjAttrs = new HashMap();// create hashmap to put this link's attributes in it
                    // also set position and type by default
                    hObjAttrs.put("Position", vPoint);
                    hObjAttrs.put("Type", "city");
                    hObjAttrs.put("Name", JoglPanel.globals.wcityGIS.sNames[i]);
                    hObjAttrs.put(
                            "Description",
                            "Country: " + JoglPanel.globals.wcityGIS.sCountries[i] + "\nCity: "
                                    + JoglPanel.globals.wcityGIS.sNames[i] + "\nLatitude: "
                                    + nf.format(JoglPanel.globals.wcityGIS.fLat[i]) + "\nLongitude: "
                                    + nf.format(JoglPanel.globals.wcityGIS.fLong[i]) + "\nLocal time: "
                                    + Globals.getUTCHour(JoglPanel.globals.wcityGIS.sHour[i]));
                    if (alSelectedObjects == null) {
                        alSelectedObjects = new ArrayList();
                    }
                    alSelectedObjects.add(hObjAttrs);
                }
            }
            ;
        }
        ;
        if (JoglPanel.globals.uscityGIS != null) {
            for (int i = 0; i < JoglPanel.globals.uscityGIS.getTotalNumber(); i++) {
                coords = Globals.point2Dto3D(JoglPanel.globals.uscityGIS.fLat[i], JoglPanel.globals.uscityGIS.fLong[i],
                        coords, 0.001f);
                vPoint = new VectorO(coords);
                // System.out.println("show link at"+vPoint);
                String sUS = "US", sCanada = "Canada";
                if ((vPoint != null) && Globals.sphereIntersection(vEye, vDirection, vPoint, radius)) {
                    HashMap hObjAttrs = new HashMap();// create hashmap to put this link's attributes in it
                    // also set position and type by default
                    hObjAttrs.put("Position", vPoint);
                    hObjAttrs.put("Type", "city");
                    hObjAttrs.put("Name", JoglPanel.globals.uscityGIS.sNames[i]);
                    String sCountry, sState;
                    if (JoglPanel.globals.uscityGIS.sCountries[i].endsWith("; Canada")) {
                        sCountry = sCanada;
                        sState = JoglPanel.globals.uscityGIS.sCountries[i].substring(0,
                                JoglPanel.globals.uscityGIS.sCountries[i].length() - 8);
                    } else {
                        sCountry = sUS;
                        sState = JoglPanel.globals.uscityGIS.sCountries[i];
                    }
                    ;
                    hObjAttrs.put(
                            "Description",
                            "Country: " + sCountry + "\nState: " + sState + "\nCity: "
                                    + JoglPanel.globals.uscityGIS.sNames[i] + "\nLatitude: "
                                    + nf.format(JoglPanel.globals.uscityGIS.fLat[i]) + "\nLongitude: "
                                    + nf.format(JoglPanel.globals.uscityGIS.fLong[i]) + "\nLocal time: "
                                    + Globals.getUTCHour(JoglPanel.globals.uscityGIS.sHour[i]));
                    if (alSelectedObjects == null) {
                        alSelectedObjects = new ArrayList();
                    }
                    alSelectedObjects.add(hObjAttrs);
                }
            }
            ;
        }
        ;
        return alSelectedObjects;
    }

    /**
     * user clicked on map so check if any node influenced
     */
    @Override
    public void mouseClick(float LONG, float LAT) {
        String sNodesSelected = "";

        rcNode node = null;
        ArrayList alSelectedNodes = getSelectedNode(LONG, LAT);
        for (int i = 0; i < alSelectedNodes.size(); i++) {
            node = (rcNode) alSelectedNodes.get(i);
            if ((JoglPanel.globals.mainPanel.monitor != null) && JoglPanel.globals.mainPanel.monitor.main.bGridsClient) {
                // if ( !JoglPanel.globals.mainPanel.monitor.main.bGridsClient )
                // open web address
                if (node.szOpticalSwitch_Name != null) {
                    BBBrowserLaunch.openURL(node.szOpticalSwitch_Name);
                }
            } else {
                node.client.setVisible(!node.client.isVisible());
            }
            // System.out.println("node selected: "+n.UnitName);
            if (sNodesSelected.compareTo("") != 0) {
                sNodesSelected += ",";
            }
            sNodesSelected += node.UnitName;
        }
        JoglPanel.globals.mainPanel.status.setText("node(s) selected: " + sNodesSelected);
    }

    /**
     * user duble clicked on map so zoom to it
     */
    @Override
    public void mouseDblClick(float LONG, float LAT) {
        rcNode node = null;
        ArrayList alSelectedNodes = getSelectedNode(LONG, LAT);
        // for ( int i=0; i<alSelectedNodes.size(); i++) {
        // node = (rcNode)alSelectedNodes.get(i);
        // break;
        // }
        // substituted with below
        if (alSelectedNodes.size() > 0) {
            node = (rcNode) alSelectedNodes.get(0);
        }
        JoglPanel.globals.mainPanel.status.setText("node(s) selected: " + (node != null ? node.UnitName : ""));
        // set that eye come over the node
        if (node != null) {
            BackgroundWorker.schedule(new GoToNodeTimerTask(node, this), 0, JoglPanel.globals.GO_TO_NODE_TIME);
        }
        ;

    }

    class GoToNodeTimerTask extends TimerTask {

        private int nStep = 1;

        private final int nMaxSteps = 10;

        private final rcNode nSelNode;

        private final DataRenderer drThis;

        public GoToNodeTimerTask(rcNode n, DataRenderer drThis) {
            nSelNode = n;
            this.drThis = drThis;
        }

        @Override
        public void run() {
            Thread.currentThread().setName(" ( ML ) - JOGL - DataRenderer Go To Node Timer Thread");
            if (nSelNode != null) {
                // VectorO vEnd, vEndDir;
                VectorO[] vectors;
                VectorO[] vEnds = new VectorO[3];
                // synchronized(drThis.syncObject) {
                Object obj;
                obj = ((HashMap) drThis.graphicalAttributes[1]).get(nSelNode);
                if (obj == null) {
                    return;
                }
                obj = ((HashMap) obj).get("PositioningVectors");
                if (obj == null) {
                    return;
                }
                if (!(obj instanceof VectorO[])) {
                    return;
                }
                vectors = (VectorO[]) obj;
                // };
                // 0 -> pos
                // 1 -> dir
                // 2 -> normal
                // set direction, first part
                vEnds[1] = new VectorO(vectors[0]);
                vEnds[1].Normalize();
                // set position away from center of sphere
                vEnds[0] = new VectorO(vEnds[1]);
                vEnds[0].AddVector(vectors[1]);
                // change direction to point to node
                vEnds[1].MultiplyScalar(-1);
                VectorO vAux = new VectorO(0, 1, 0);
                vEnds[2] = vEnds[1].CrossProduct(vAux);
                vAux.duplicate(vEnds[2]);
                vEnds[2] = vAux.CrossProduct(vEnds[1]);
                JoglPanel.globals.rotate(vEnds, nStep / (double) nMaxSteps);

                // at the end, increment step
                nStep++;
                if (nStep > nMaxSteps) {
                    this.cancel();
                    // check textures
                    JoglPanel.globals.resetIdleTime();
                }
                ;
                JoglPanel.globals.canvas.repaint();
            } else {
                this.cancel();
            }
        }
    }

    /**
     * mouse moved so check to see if any node in range
     *
     * @see lia.Monitor.JiniClient.CommonGUI.Jogl.GlobeListener#mouseMove(float, float)
     */
    @Override
    public void mouseMove(int mouse_x, int mouse_y) {// float LONG, float LAT) {
        if (JoglPanel.globals.mainPanel.renderer.sr.IsInRotation()) {
            return;
        }
        // check to see if mouse over nodes and links
        ArrayList alSelectedObjects = null;
        // long lStart = NTPDate.currentTimeMillis();
        alSelectedObjects = getSelectedNodes(mouse_x, mouse_y, alSelectedObjects);
        // long lEnd = NTPDate.currentTimeMillis();
        // if ( lEnd-lStart>3 ) System.out.println("getSelectedNodes took "+(lEnd-lStart)+"ms");
        VectorO vDirection = UserInputListener.getVectorToPointOnMap(mouse_x, mouse_y);
        VectorO vEye = new VectorO(JoglPanel.globals.EyePosition);
        alSelectedObjects = getSelectedLinks(vEye, vDirection, alSelectedObjects);
        alSelectedObjects = getOtherSelectedObjects(vEye, vDirection, alSelectedObjects);
        // long lStart1 = NTPDate.currentTimeMillis();
        if ((alSelectedObjects != null) && (alSelectedObjects.size() > 0)) {
            String sNodesSelected = "";
            // synchronized(syncObject) {
            ((Hashtable) graphicalAttributes[0]).put("MousePosition_X", Integer.valueOf(mouse_x));
            ((Hashtable) graphicalAttributes[0]).put("MousePosition_Y", Integer.valueOf(mouse_y));
            ArrayList alMOobjs = (ArrayList) ((Hashtable) graphicalAttributes[0]).get("MouseOverObjects");
            // clear list of objects that mouse is over, simpler solution
            alMOobjs.clear();
            HashMap objAttrs;
            String sName, sType;
            Object objPos;
            // check for new objects under mouse
            for (int i = 0; i < alSelectedObjects.size(); i++) {
                objAttrs = (HashMap) alSelectedObjects.get(i);
                // substitute mouse positions with farm positions
                sType = (String) objAttrs.get("Type");
                // check to see if we hava correct name and position
                boolean bObjOK = true;
                sName = (String) objAttrs.get("Name");
                if ((sName == null) || (sName.length() == 0)) {
                    bObjOK = false;
                } else if ((sType != null) && sType.equals("node")) {
                    if (sNodesSelected.compareTo("") != 0) {
                        sNodesSelected += ",";
                    }
                    sNodesSelected += sName;
                }
                ;
                objPos = objAttrs.get("Position");
                if (objPos instanceof VectorO) {
                    VectorO vPos = (VectorO) objPos;
                    objAttrs.put("Position", new float[] { vPos.getX(), vPos.getY(), vPos.getZ() });
                } else if ((objPos instanceof float[]) && (((float[]) objPos).length == 2)) {
                    float[] geo_coords = (float[]) objPos;
                    float[] pos3d = Globals.point2Dto3D(geo_coords[1], geo_coords[0], null);
                    objAttrs.put("Position", pos3d);
                } else if (!((objPos instanceof float[]) && (((float[]) objPos).length == 3))) {
                    bObjOK = false;
                }
                if (bObjOK) {
                    alMOobjs.add(objAttrs);
                }
            }
            ;
            if (alMOobjs.size() > 0) {
                JoglPanel.globals.canvas.repaint();
                JoglPanel.globals.mainPanel.status.setText("node(s) under cursor: " + sNodesSelected);
            }
            ;
            // };
        } else {
            // System.out.println("remove mouse");
            // synchronized(syncObject) {
            ((Hashtable) graphicalAttributes[0]).remove("MousePosition_X");
            ((Hashtable) graphicalAttributes[0]).remove("MousePosition_Y");
            Object obj = ((Hashtable) graphicalAttributes[0]).get("MouseOverObjects");
            if ((obj != null) && (((ArrayList) obj).size() > 0)) {
                ((ArrayList) obj).clear();
                JoglPanel.globals.canvas.repaint();
            }
            ;
            // };
        }
        // long lEnd1 = NTPDate.currentTimeMillis();
        // if ( lEnd1-lStart1>3 ) System.out.println("gathering of info about selections took "+(lEnd1-lStart1)+"ms");
        float[] map_coordinates = UserInputListener.getPointOnMap(mouse_x, mouse_y, null);
        if ((JoglPanel.globals.charPressed == 'L') && (map_coordinates != null)) {
            // NumberFormat
            JoglPanel.globals.mainPanel.status.setText("Mouse at [" + ((((int) (map_coordinates[0] * 10000))) / 10000f)
                    + " LONG," + ((((int) (map_coordinates[1] * 10000f))) / 10000f) + " LAT]");
        }
        if ((JoglPanel.globals.charPressed == 'J') && (map_coordinates != null)) {
            // get
            String text_name = Texture.findAtCoords(map_coordinates[0], map_coordinates[1]);
            if (text_name != null) {
                JoglPanel.globals.mainPanel.status.setText("texture at mouse position is " + text_name);
            }
        }
    }

    /*
     * public void mouseMove( int mouse_x, int mouse_y) {//float LONG, float LAT) { rcNode node=null; float
     * []map_coordinates = UserInputListener.getPointOnMap( mouse_x, mouse_y, null); if ( JoglPanel.globals.charPressed
     * == 'L' ) {
     * JoglPanel.globals.mainPanel.status.setText("Mouse at ["+((float)((int)(map_coordinates[0]*100)))/100f+" LONG,"
     * +((float)((int)(map_coordinates[1]*100)))/100f+" LAT]"); } //check to see if mouse over nodes ArrayList
     * alSelectedNodes=null; if ( map_coordinates == null ) alSelectedNodes = new ArrayList(); else alSelectedNodes =
     * getSelectedNode( map_coordinates[0], map_coordinates[1]);//LONG, LAT); ArrayList alSelectedObjects = null;
     * alSelectedObjects = getSelectedLinks(mouse_x, mouse_y, alSelectedObjects); if ( alSelectedNodes.size()>0 ) {
     * String sNodesSelected = ""; boolean bChange = false; synchronized(syncObject) {
     * ((Hashtable)graphicalAttributes[0]).put("MousePosition_X", Integer.valueOf(mouse_x));
     * ((Hashtable)graphicalAttributes[0]).put("MousePosition_Y", Integer.valueOf(mouse_y)); HashMap hm =
     * (HashMap)((Hashtable)graphicalAttributes[0]).get("MouseOverNodes"); ArrayList nodeNames = new ArrayList();
     * //check for new nodes under mouse for( int i=0; i<alSelectedNodes.size(); i++) { node =
     * (rcNode)alSelectedNodes.get(i); //this name is selected, so add it to nodes array nodeNames.add( node.UnitName);
     * //hm.put( node.UnitName, new float[]{LONG, LAT}); //substitute mouse positions with farm positions if (
     * hm.get(node.UnitName)==null ) { float n_lat, n_long; try { n_lat = DataGlobals.failsafeParseLAT(node.LAT,
     * -21.22f); n_long = DataGlobals.failsafeParseLONG(node.LONG, -111.15f); } catch (NumberFormatException nfex) {
     * n_lat = 0; n_long = 0; } hm.put( node.UnitName, new float[]{n_long, n_lat}); bChange = true; } if (
     * sNodesSelected.compareTo("")!=0 ) sNodesSelected+=","; sNodesSelected += node.UnitName; }; //remove old nodes
     * that are no longer under mouse for ( Iterator it=hm.keySet().iterator(); it.hasNext(); ) { if (
     * !nodeNames.contains(it.next()) ) { it.remove(); bChange = true; }; }; }; if ( bChange ) {
     * JoglPanel.globals.canvas.repaint();
     * JoglPanel.globals.mainPanel.status.setText("node(s) under cursor: "+sNodesSelected); }; } else {
     * //System.out.println("remove mouse"); synchronized(syncObject) {
     * ((Hashtable)graphicalAttributes[0]).remove("MousePosition_X");
     * ((Hashtable)graphicalAttributes[0]).remove("MousePosition_Y"); Object obj =
     * ((Hashtable)graphicalAttributes[0]).get("MouseOverNodes"); if ( obj != null && ((HashMap)obj).size()>0 ) {
     * ((HashMap)obj).clear(); JoglPanel.globals.canvas.repaint(); }; }; } }
     */

    @Override
    public void display(GLAutoDrawable gLDrawable) {

        // TODO: check it out!
        long lCurrentTime = NTPDate.currentTimeMillis();
        JoglPanel.globals.lStartCanvasRepaint = lCurrentTime;
        if ((JoglPanel.globals.lLastRefreshTime != -1)
                && (lCurrentTime < (JoglPanel.globals.lLastRefreshTime + Globals.REFRESH_TIME))) {
            return;
        }

        super.displayBefore(gLDrawable);
        final GL2 gl = gLDrawable.getGL().getGL2();
        // correct radius if zoom level changed
        computeRadius();

        // synchronized (Globals.syncPrintObject) {
        // System.out.println("drawn everything else");
        // }
        gl.glEnable(GL.GL_TEXTURE_2D);
        Texture.drawTree(gl);
        gl.glDisable(GL.GL_TEXTURE_2D);
        // synchronized (Globals.syncPrintObject) {
        // System.out.println("drawn tree");
        // }

        // System.out.println("intermediary time: "+(System.currentTimeMillis()-lCurrentDrawNodesTime)+" ms");
        int nShowLinks = 1;// 0 - no draw, 1 - draw, 2- recompute and draw
        boolean bRecomputeTime = false;

        // synchronized(syncObject) {
        if ((JoglPanel.globals.lLastRefreshTime == -1)
                || (lCurrentTime > (JoglPanel.globals.lLastRefreshTime + Globals.RECOMPUTE_NODES_TIME))) {
            bRecomputeTime = true;
        }
        if ((JoglPanel.globals.mainPanel.monitor != null) && JoglPanel.globals.mainPanel.monitor.main.bGridsClient) {
            bRecomputeTime = false;
        }
        if (((Hashtable) graphicalAttributes[0]).remove("RecomputeNodes") != null) {
            bRecomputeTime = true;
        }
        if (((Hashtable) graphicalAttributes[0]).get("InvalidateLinksStart") != null) {
            if (((Hashtable) graphicalAttributes[0]).get("DetailLevel_ShowLinksOnChangeProjection") != null) {
                ((Hashtable) graphicalAttributes[0]).put("InvalidateLinks", new Object());
            } else {
                nShowLinks = 0;
            }
        } else if (((Hashtable) graphicalAttributes[0]).remove("InvalidateLinksFinish") != null) {
            ((Hashtable) graphicalAttributes[0]).put("InvalidateLinks", new Object());
        } else if (bRecomputeTime) {
            nShowLinks = 2;
        }
        if (((Hashtable) graphicalAttributes[0]).get("InvalidateLinks") != null) {
            nShowLinks = 2;
        }
        if (((Hashtable) graphicalAttributes[0]).remove("RecomputeLinks") != null) {
            nShowLinks = 2;
            // };
        }

        // draw nodes with the current active nodes renderer
        NodesRendererInterface nri = null;
        nri = getActiveNodesRenderer();
        if (nri != null) {
            // gl.glDisable(GL.GL_COLOR_MATERIAL);
            // gl.glDisable(GL.GL_TEXTURE_2D);
            // first organize nodes, if the case
            if (bRecomputeTime) {
                // first check to see what new nodes have appeared
                organizeNodes(JoglPanel.dglobals.snodes);
            }
            ;
            if (nShowLinks == 2) {// should recompute, includes bRecomputeTime
                // correct nodes positions, if it is the case, called as new nodes may have appeared...
                organizeLinks();// adds/removes entries about links for each node
                // the entries are empty hashmaps where position and dimension and color info will be put
                // };
                // //show links
                // //if links should be updated because of timeout or change radius event
                // if ( nShowLinks == 2 ) {
                // for ( int i=0; i<getNodesRenderersSize(); i++) {
                // ((NodesRendererInterface)NodesRendererList.get(i)).computeLinks( gl, graphicalAttributes);
                // }
                // System.out.println("gl="+gl);
                boolean bNL = Globals.bNiceLinks;
                if (bNL) {
                    gl.glEnable(GL.GL_LINE_SMOOTH);
                    gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_DONT_CARE);
                    gl.glEnable(GL.GL_BLEND);
                    gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
                }
                ;
                nri.computeLinks(gl, graphicalAttributes);
                if (bNL) {
                    gl.glDisable(GL.GL_LINE_SMOOTH);
                    gl.glDisable(GL.GL_BLEND);
                }
                ;
            } else if (nShowLinks == 1) {// if only to draw them
                boolean bNL = Globals.bNiceLinks;
                if (bNL) {
                    gl.glEnable(GL.GL_LINE_SMOOTH);
                    gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_DONT_CARE);
                    gl.glEnable(GL.GL_BLEND);
                    gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
                }
                ;
                nri.drawLinks(gl, graphicalAttributes);
                if (bNL) {
                    gl.glDisable(GL.GL_LINE_SMOOTH);
                    gl.glDisable(GL.GL_BLEND);
                }
                ;
            }
            // then nodes
            if (bRecomputeTime) {
                // System.out.println("recompute nodes");
                nri.computeNodes(gl, graphicalAttributes);
                JoglPanel.globals.lLastRefreshTime = NTPDate.currentTimeMillis();
            } else {
                nri.drawNodes(gl, graphicalAttributes);
            }
            gl.glColor3f(1.0f, 1.0f, 1.0f);
            // gl.glEnable(GL.GL_TEXTURE_2D);
            // gl.glEnable(GL.GL_COLOR_MATERIAL);
            // } else
            // logger.log( Level.INFO, "no active nodes renderer");
        }
        // draw tooltip
        // long lStart = NTPDate.currentTimeMillis();
        ArrayList alObjects = (ArrayList) ((Hashtable) graphicalAttributes[0]).get("MouseOverObjects");
        if ((alObjects != null) && (alObjects.size() > 0)) {
            int mpx = 0, mpy = 0;
            try {
                mpx = ((Integer) ((Hashtable) graphicalAttributes[0]).get("MousePosition_X")).intValue();
                mpy = ((Integer) ((Hashtable) graphicalAttributes[0]).get("MousePosition_Y")).intValue();
            } catch (Exception ex) {
                mpx = 0;
                mpy = 0;
            }
            ;
            boolean bLeft2Right;
            boolean bTop2Bottom;
            if (mpx < (JoglPanel.globals.width / 2)) {
                bLeft2Right = true;
            } else {
                bLeft2Right = false;
            }
            if (mpy < (JoglPanel.globals.height / 2)) {
                bTop2Bottom = true;
            } else {
                bTop2Bottom = false;
            }
            float[] coords;

            boolean bTransparentTooltip = (AppConfig.getProperty("ml_client.jogl.transparent_tooltip", "true")
                    .equals("true"));
            if (bTransparentTooltip) {
                gl.glEnable(GL.GL_BLEND);
                // source is what is in front, destination is what already is there
                gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
            }

            InfoPlane ip = new InfoPlane(gl, bLeft2Right, bTop2Bottom, 0.6f);// initialize drawing plane
            ip.setBgAlphaOnly(true);
            // ip.setBDebug(true);
            // ip.doInfoBox( 160, 80, true, true, "test", "test", 0, 0, 0, new float[] {0,0,0});
            // initText( bLeft2Right, bTop2Bottom);
            HashMap objAttrs;
            String sName, sType, sDesc;
            // Object objPos;
            int nTTID = 0;// tool tip image texture id
            int nTTw = 0, nTTh = 0;
            // check for new objects under mouse
            for (int i = 0; i < alObjects.size(); i++) {
                objAttrs = (HashMap) alObjects.get(i);
                // substitute mouse positions with farm positions
                sType = (String) objAttrs.get("Type");
                if (sType.equals("city")) {
                    ip.setActiveColorSet(2);
                } else {
                    ip.setActiveColorSet(1);
                }
                coords = (float[]) objAttrs.get("Position");
                sName = (String) objAttrs.get("Name");
                sDesc = (String) objAttrs.get("Description");
                if (objAttrs.get("ImageID") != null) {
                    nTTID = ((Integer) objAttrs.get("ImageID")).intValue();
                    nTTw = ((Integer) objAttrs.get("ImageWidth")).intValue();
                    nTTh = ((Integer) objAttrs.get("ImageHeight")).intValue();
                } else if (objAttrs.get("BufferedImage") != null) {
                    float whRaport = ((Float) objAttrs.get("whRaport")).floatValue();
                    BufferedImage bi = (BufferedImage) objAttrs.get("BufferedImage");
                    // we have the picture, so create a texture from it
                    int biW = bi.getWidth();
                    int biH = bi.getHeight();
                    // System.out.println("bi="+bi+" width="+biW+" height="+biH);
                    int texture_id = 0;
                    texture_id = DataGlobals.makeTexture(gl, bi);
                    objAttrs.put("ImageID", Integer.valueOf(texture_id));
                    objAttrs.put("ImageWidth", Integer.valueOf((int) (biH * whRaport)));
                    objAttrs.put("ImageHeight", Integer.valueOf(biH));
                    nTTID = texture_id;
                    nTTw = (int) (biH * whRaport);
                    nTTh = biH;
                    vcf vcfObj = ((vcf) objAttrs.get("vcf"));
                    vcfObj.hmData.put("ImageID", Integer.valueOf(texture_id));
                    vcfObj.hmData.put("ImageWidth", Integer.valueOf((int) (biH * whRaport)));
                    vcfObj.hmData.put("ImageHeight", Integer.valueOf(biH));
                } else {
                    nTTID = 0;
                    nTTw = 0;
                    nTTh = 0;
                }
                ;
                // TODO: create a display list with text to speed up drawing for same text
                // drawTextFrom( gl, sName, coords);
                if (sDesc != null) {
                    ip.doInfoBox(600, 300, true, true, sName, sDesc, nTTID, nTTw, nTTh, coords);
                } else {
                    ip.doInfoBox(600, 300, true, true, sType, sName, nTTID, nTTw, nTTh, coords);
                }
            }

            if (bTransparentTooltip) {
                gl.glDisable(GL.GL_BLEND);
            }
        }
        // long lEnd = NTPDate.currentTimeMillis();
        // if ( lEnd-lStart>3 ) System.out.println("draw selected objects took "+(lEnd-lStart)+"ms");

        super.displayAfter(gLDrawable);

        sr.rotationDrawn();
        // if ( Globals.bShowCities ) {
        // if ( JoglPanel.globals.mapAngle==90 ) {
        // gl.glCallList( JoglPanel.globals.wcityGisID90);
        // gl.glCallList( JoglPanel.globals.uscityGisID90);
        // } else if ( JoglPanel.globals.mapAngle==0 ) {
        // //because of some randation problems due to very small distance between borders and map
        // //do a translation in z direction away from map, depending on zoom level
        // gl.glPushMatrix();
        // float z_translate = (float)JoglPanel.globals.EyePosition.getRadius()*.0001f;
        // gl.glTranslatef( 0f, 0f, z_translate);
        // gl.glCallList( JoglPanel.globals.wcityGisID0);
        // gl.glCallList( JoglPanel.globals.uscityGisID0);
        // gl.glPopMatrix();
        // }
        // };
        // if ( Globals.bShowCountriesBorders ) {
        // if ( JoglPanel.globals.mapAngle==90 && JoglPanel.globals.countriesBordersID90!=-1 ) {//sphere projection
        // gl.glCallList( JoglPanel.globals.countriesBordersID90);
        // gl.glCallList( JoglPanel.globals.usStatesBordersID90);
        // } else if ( JoglPanel.globals.globeRadius<0 && JoglPanel.globals.countriesBordersID0!=-1 ) {//map projection
        // //compute minimal distance to map from view point
        // float dist = (float)JoglPanel.globals.EyePosition.DotProduct(JoglPanel.globals.EyeDirection);
        // if ( dist < 0 )
        // dist = -dist;
        // if ( dist < 70f ) {
        // //only if distance is smaller than a certain value
        // //because of some randation problems due to very small distance between borders and map
        // //do a translation in z direction away from map, depending on zoom level
        // gl.glPushMatrix();
        // float z_translate = dist*.001f;//(float)JoglPanel.globals.EyePosition.getRadius()*.0001f;
        // System.out.println("z translation: "+z_translate);
        // gl.glTranslatef( 0f, 0f, z_translate);
        // gl.glCallList( JoglPanel.globals.countriesBordersID0);
        // gl.glCallList( JoglPanel.globals.usStatesBordersID0);
        // gl.glPopMatrix();
        // }
        // }
        // };

        JoglPanel.globals.lEndCanvasRepaint = NTPDate.currentTimeMillis();
    }

    /**
     * init data related variables for each node renderer
     */
    @Override
    public void init(GLAutoDrawable gLDrawable) {
        super.init(gLDrawable);
        // should these be here??
        // JoglPanel.globals.lLastRefreshTime = -1;
        ((Hashtable) graphicalAttributes[0]).put("RecomputeNodes", new Object());
        // ((Hashtable)graphicalAttributes[0]).put("InvalidateLinks", new Object());
        final GL2 gl = gLDrawable.getGL().getGL2();
        // System.out.println("gl_init="+gl);
        initNodesRenderers(gl);
    }

    private rcNode getNodeBasedOnIP(String ip) {
        try {
            // for each node in global hash
            for (final rcNode node : JoglPanel.dglobals.snodes.values()) {
                if ((node != null) && (node.IPaddress != null) && (node.IPaddress.compareTo(ip) == 0)) {
                    return node;
                }
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Exception identifying a to link's node: " + ex.getMessage());
            ex.printStackTrace();
        }
        return null;
    }

    private rcNode getNodeBasedOnSID(ServiceID sid) {
        return JoglPanel.dglobals.snodes.get(sid);
        // rcNode node;
        // try {
        // //for each node in global hash
        // for ( Enumeration en = JoglPanel.dglobals.snodes.elements(); en.hasMoreElements(); ) {
        // node = (rcNode)en.nextElement();
        // if ( node.sid.equals(sid) )
        // return node;
        // };
        // } catch (Exception ex) {
        // logger.log(Level.WARNING, "Exception identifying a to link's node: "+ex.getMessage());
        // ex.printStackTrace();
        // }
        // return null;
    }

    // TODO: this 3 methods should be synchronized
    public static void addGlobeListener(GlobeListener gl) {
        for (int i = 0; i < globeListenersList.size(); i++) {
            if ((GlobeListener) globeListenersList.get(i) == gl) {
                return;// already added
            }
        }
        // else add this new nodes renderer
        globeListenersList.add(gl);
    }

    public static void addGlobeListener(int position, GlobeListener gl) {
        for (int i = 0; i < globeListenersList.size(); i++) {
            if ((GlobeListener) globeListenersList.get(i) == gl) {
                return;// already added
            }
        }
        // else add this new nodes renderer
        globeListenersList.add(position, gl);
    }

    public static boolean removeGlobeListener(GlobeListener gl) {
        // change the active nodes renderer
        return globeListenersList.remove(gl);
    }

    /** last time when mouse move event was called */
    // private static long lastMouseMoveTime=-1;
    /** miliseconds till next mouse move event */
    // private static long delayMouseMoveTime=1000;
    // informes listeners of an globe event
    public static void sendGlobeEvent(int event, Object params) {
        for (int i = 0; i < globeListenersList.size(); i++) {
            if (event == GLOBE_RADIUS_CHANGED) {
                ((GlobeListener) globeListenersList.get(i)).radiusChanged();
            } else if (event == GLOBE_RADIUS_CHANGE_START) {
                ((GlobeListener) globeListenersList.get(i)).radiusChangeStart();
            } else if (event == GLOBE_RADIUS_CHANGE_FINISH) {
                ((GlobeListener) globeListenersList.get(i)).radiusChangeFinish();
            } else if ((event == GLOBE_MOUSE_CLICK) || (event == GLOBE_MOUSE_DBLCLICK)) {
                if (params instanceof float[]) {
                    float[] coords = (float[]) params;
                    if ((coords != null) && (coords.length == 2)) {
                        if (event == GLOBE_MOUSE_CLICK) {
                            ((GlobeListener) globeListenersList.get(i)).mouseClick(coords[0], coords[1]);
                        } else if (event == GLOBE_MOUSE_DBLCLICK) {
                            ((GlobeListener) globeListenersList.get(i)).mouseDblClick(coords[0], coords[1]);
                        }
                    }
                }
                ;
            } else if (event == GLOBE_OPTION_PANEL_CHANGE) {
                if ((params instanceof Integer) && (params != null)) {
                    ((GlobeListener) globeListenersList.get(i)).optionPanelChanged(((Integer) params).intValue());
                }
            } else if (event == GLOBE_MOUSE_MOVE) {
                // if ( params instanceof float[] ) {
                // long curTime = NTPDate.currentTimeMillis();
                // send mouse event only after a period of time, for two very quick consecutive events, some get ignored
                // if ( lastMouseMoveTime==-1 || curTime-lastMouseMoveTime>delayMouseMoveTime ) {
                // System.out.println("curTime="+curTime);
                // lastMouseMoveTime=curTime;
                int[] coords = (int[]) params;
                if ((coords != null) && (coords.length == 2)) {
                    ((GlobeListener) globeListenersList.get(i)).mouseMove(coords[0], coords[1]);
                } else {
                    ((GlobeListener) globeListenersList.get(i)).mouseMove(-1, -1);// 400, 200);//this dimensions expand
                    // beyond the limits of longitude and
                    // latitude => removes the tooltip
                    // };
                    // };
                }
            }
        }
        ;
    }

    public HashMap getLinks() {
        return ((HashMap) graphicalAttributes[2]);
    }
}
