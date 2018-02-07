/*
 * Created on 12.06.2004 21:22:04
 * Filename: FarmNodesRenderer.java
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;

import lia.Monitor.Agents.OpticalPath.OSPort;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwPort;
import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.pie;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Jogl.util.vcf;
import lia.Monitor.JiniClient.Farms.FarmsSerMonitor;
import lia.Monitor.monitor.Gresult;
import lia.Monitor.monitor.ILink;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.NFLink;
import lia.Monitor.monitor.OSLink;

/**
 * @author Luc<br>
 * <br>
 *         FarmNodesRenderer<br>
 *         implementation of NodesRendererInterface that proposes a visualisation model for
 *         the nodes and asociated links as cups
 */
public class FarmsNodesRenderer extends AbstractNodesRenderer implements GlobeListener {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(FarmsNodesRenderer.class.getName());

    // public int nodesShow = 1; // what do the bubbles are = one of NODE_xxx constants
    // public int peersShow = 11; // what shows the color of links = one of the LINK_xxx constants

    private int routerID = 0;// compiled openGL object to be used to draw each router

    private int nfDeviceID = 0;// compiled openGL object to be used to draw each netflow device

    private int nfRouterID = 0;// compiled openGL object to be used to draw each netflow device

    private int optical_switchID = 0;// compiled openGL object to be used to draw each OS

    private int conID = 0; // compiled openGL object to be used to draw each arrow for a link

    private int packageID = 0;// compiled openGL cube with size 1 used to draw WAN packages for a link

    private int packageNFID = 0;// compiled openGL cube with size 1 used to draw WAN packages for a link

    // private int nLevels;//levels of detail for drawing a cap as several spherical segments drawn one on top of
    // another
    // private int nMaxPoints = 32;//total number of points to be drawn, is another mesurement of level of detail

    /**
     * switches between the two existing views:<br>
     * - normal, where farms can overlap, and<br>
     * - on top, where overlapping farms are stacked one above the other
     */
    private boolean bOnTopView = false;

    // vcf -upb specific variables, to be removed or changed!!!
    // TODO: to be put into vcf class
    int nCurrentVcf = -1;// index in list of vcfs for current vcf to be shown

    // int nCurrentVcfImgID=0;//texture id for image for current vcf, if one exists
    long lLastVcfChange = -1;// time of last vcf change from current to next

    int nVCF_SHOW_TIME = 10000;// time in miliseconds to show a vcf, or minimum time after which the vcf can be changed

    // netflow related stuff
    Hashtable hNetFlowDevices; // table NetFlow device -> TexturedPie

    public String nfDeviceTextureFile = "lia/images/ml_netflow_device.png";

    public String nfRouterTextureFile = "lia/images/ml_router_netflow.png";

    public float[] nfDeviceColor = new float[] { FarmsJoglPanel.maxNetFlowColor.getRed() / 255f,
            FarmsJoglPanel.maxNetFlowColor.getGreen() / 255f, FarmsJoglPanel.maxNetFlowColor.getBlue() / 255f };

    /**
     * This class SHOULD NOT BE USED!!!!<br>
     * Because it is very similar with WANRouter.
     * It will be used because some modifications/differences may appear during the development.
     * Sep 13, 2005 - 4:52:33 PM
     */
    public class NetFlowDevice {

        public HashSet fromLinks = new HashSet();// list of links that leave this router

        public HashSet toLinks = new HashSet();// list of links that go into this router

        public String sLocation;

        public boolean bIsRouter = false;

        public float posLONG, posLAT;

        private double total_IN = 0;

        private double total_OUT = 0;

        public NetFlowDevice(float posLONG, float posLAT, String sLocation) {
            if (sLocation.startsWith("[R]")) {
                bIsRouter = true;
            }
            this.sLocation = sLocation;
            this.posLONG = posLONG;
            this.posLAT = posLAT;
        }

        /**
         * add a link to list of link, in consideration of direction:<br>
         * 1 - link leaves the router<br>
         * -1 - link goes into the router<br>
         * <b>NOT recomended!</b>
         *
         * @param direction
         * @param link
         */
        public void addLink(int direction, NFLink link) {
            if (direction == 1) {
                if (fromLinks.add(link)) {// if new link added, recompute total traffic
                    double data = 0;
                    if ((link.data != null) && (link.data instanceof Double)) {
                        data = ((Double) (link.data)).doubleValue();
                    }
                    total_OUT += data;
                }
                ;
            } else if (direction == -1) {
                if (toLinks.add(link)) {// if new link added, recompute total traffic
                    double data = 0;
                    if ((link.data != null) && (link.data instanceof Double)) {
                        data = ((Double) (link.data)).doubleValue();
                    }
                    total_IN += data;
                }
                ;
            }
        }

        /**
         * removes a link from routers and updates total traffic for them<br>
         * <b>NOT recomended!</b>
         *
         * @param link
         */
        public void removeLink(NFLink link) {
            if (fromLinks.remove(link)) {// if link removed, recompute total traffic
                double data = 0;
                if ((link.data != null) && (link.data instanceof Double)) {
                    data = ((Double) (link.data)).doubleValue();
                }
                total_OUT -= data;
            }
            ;
            if (toLinks.remove(link)) {// if link removed, recompute total traffic
                double data = 0;
                if ((link.data != null) && (link.data instanceof Double)) {
                    data = ((Double) (link.data)).doubleValue();
                }
                total_IN -= data;
            }
            ;
            // check to see if this is valid
        }

        /**
         * recomputes traffic caused by modification in link lists<br>
         * although add and remove are available, this is recomanded because
         * it uses latest values from links
         */
        public void recomputeTraffic() {
            double sum = 0;
            double data = 0;
            NFLink link;
            for (Iterator it = fromLinks.iterator(); it.hasNext();) {
                data = 0;
                link = (NFLink) it.next();
                if ((link.data != null) && (link.data instanceof Double)) {
                    data = ((Double) (link.data)).doubleValue();
                }
                sum += data;
            }
            ;
            total_OUT = sum;
            sum = 0;
            for (Iterator it = toLinks.iterator(); it.hasNext();) {
                data = 0;
                link = (NFLink) it.next();
                if ((link.data != null) && (link.data instanceof Double)) {
                    data = ((Double) (link.data)).doubleValue();
                }
                sum += data;
            }
            ;
            total_IN = sum;
        }
    }

    // routers related stuff
    Hashtable hRouters; // table CITY -> router TexturedPie

    public String routerTextureFile = "lia/images/ml_router.png";

    public float[] routerColor = new float[] { 0f, 170 / 255f, 249 / 255f };

    // int router_texture_id = 0;
    public class WANRouter {

        public HashSet fromLinks = new HashSet();// list of links that leave this router

        public HashSet toLinks = new HashSet();// list of links that go into this router

        public String sLocation;

        public float posLONG, posLAT;

        private double total_IN = 0;

        private double total_OUT = 0;

        public WANRouter(float posLONG, float posLAT, String sLocation) {
            this.sLocation = sLocation;
            this.posLONG = posLONG;
            this.posLAT = posLAT;
        }

        /**
         * add a link to list of link, in consideration of direction:<br>
         * 1 - link leaves the router<br>
         * -1 - link goes into the router<br>
         * <b>NOT recomended!</b>
         *
         * @param direction
         * @param link
         */
        public void addLink(int direction, ILink link) {
            if (direction == 1) {
                if (fromLinks.add(link)) {// if new link added, recompute total traffic
                    double data = 0;
                    if ((link.data != null) && (link.data instanceof Double)) {
                        data = ((Double) (link.data)).doubleValue();
                    }
                    total_OUT += data;
                }
                ;
            } else if (direction == -1) {
                if (toLinks.add(link)) {// if new link added, recompute total traffic
                    double data = 0;
                    if ((link.data != null) && (link.data instanceof Double)) {
                        data = ((Double) (link.data)).doubleValue();
                    }
                    total_IN += data;
                }
                ;
            }
        }

        /**
         * removes a link from routers and updates total traffic for them<br>
         * <b>NOT recomended!</b>
         *
         * @param link
         */
        public void removeLink(ILink link) {
            if (fromLinks.remove(link)) {// if link removed, recompute total traffic
                double data = 0;
                if ((link.data != null) && (link.data instanceof Double)) {
                    data = ((Double) (link.data)).doubleValue();
                }
                total_OUT -= data;
            }
            ;
            if (toLinks.remove(link)) {// if link removed, recompute total traffic
                double data = 0;
                if ((link.data != null) && (link.data instanceof Double)) {
                    data = ((Double) (link.data)).doubleValue();
                }
                total_IN -= data;
            }
            ;
            // check to see if this is valid
        }

        /**
         * recomputes traffic caused by modification in link lists<br>
         * although add and remove are available, this is recomanded because
         * it uses latest values from links
         */
        public void recomputeTraffic() {
            double sum = 0;
            double data = 0;
            ILink link;
            for (Iterator it = fromLinks.iterator(); it.hasNext();) {
                data = 0;
                link = (ILink) it.next();
                if ((link.data != null) && (link.data instanceof Double)) {
                    data = ((Double) (link.data)).doubleValue();
                }
                sum += data;
            }
            ;
            total_OUT = sum;
            sum = 0;
            for (Iterator it = toLinks.iterator(); it.hasNext();) {
                data = 0;
                link = (ILink) it.next();
                if ((link.data != null) && (link.data instanceof Double)) {
                    data = ((Double) (link.data)).doubleValue();
                }
                sum += data;
            }
            ;
            total_IN = sum;
        }

        @Override
        public String toString() {
            return "router " + sLocation + " " + posLONG + " long " + posLAT + " lat with " + fromLinks.size()
                    + " from links and " + toLinks.size() + " to links ";
        }

        public boolean equals(WANRouter b) {
            if ((((int) (posLONG * 100)) == ((int) (b.posLONG * 100)))
                    && (((int) (posLAT * 100)) == ((int) (b.posLAT * 100)))) {
                return true;
            }
            return false;
        }
    }

    /**
     * iterats through hRouters set to remove from each router
     * the link, that can be from or to.<br>
     * If a routers remains without from and to links, it is removed also.
     *
     * @param link
     * @return number of routers that have been removed
     */
    private int removeLinkFromRouters(ILink link) {
        int nRemovedRoutersCount = 0;
        try {
            // try to remove from each router the specified link
            for (Iterator it = hRouters.values().iterator(); it.hasNext();) {
                WANRouter router = (WANRouter) it.next();
                router.fromLinks.remove(link);
                router.toLinks.remove(link);
                if ((router.fromLinks.size() == 0) && (router.toLinks.size() == 0)) {
                    // this router is not generated by any link, so remove it
                    it.remove();
                    nRemovedRoutersCount++;
                }
                ;
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception while removing wan router with wan link removal.");
            ex.printStackTrace();
        }
        return nRemovedRoutersCount;
    }

    /**
     * recomputes all routers traffic in and out
     */
    private void recomputeTrafficForRouters() {
        try {
            for (Iterator it = hRouters.values().iterator(); it.hasNext();) {
                WANRouter router = (WANRouter) it.next();
                router.recomputeTraffic();
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception while recomputing router traffic. ");
            ex.printStackTrace();
        }
    }

    /**
     * iterats through hNetFlowDevices set to remove from each router
     * the link, that can be from or to.<br>
     * If a routers remains without from and to links, it is removed also.
     *
     * @param link
     * @return number of routers that have been removed
     */
    private int removeLinkFromNetFlowDevices(NFLink link) {
        int nRemovedRoutersCount = 0;
        try {
            // try to remove from each router the specified link
            for (Iterator it = hNetFlowDevices.values().iterator(); it.hasNext();) {
                NetFlowDevice router = (NetFlowDevice) it.next();
                router.fromLinks.remove(link);
                router.toLinks.remove(link);
                if ((router.fromLinks.size() == 0) && (router.toLinks.size() == 0)) {
                    // this router is not generated by any link, so remove it
                    it.remove();
                    nRemovedRoutersCount++;
                }
                ;
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception while removing netflow device with netflow link removal.");
            ex.printStackTrace();
        }
        return nRemovedRoutersCount;
    }

    /**
     * recomputes all netflow devices traffic in and out
     */
    // private void recomputeTrafficForNetFlowDevices()
    // {
    // try {
    // for ( Iterator it=hNetFlowDevices.values().iterator(); it.hasNext(); ) {
    // NetFlowDevice router = (NetFlowDevice)it.next();
    // router.recomputeTraffic();
    // }
    // } catch (Exception ex) {
    // logger.log(Level.INFO, "Exception while recomputing router traffic. ");
    // ex.printStackTrace();
    // }
    // }

    HashMap wanAttrs = new HashMap();// contains wan links

    HashMap nfAttrs = new HashMap();// contains net flow links

    HashMap osAttrs = new HashMap();// contains os links, key is OSLink, value is a hashtable

    /**
     * levels represents the number of fragments to be drawn to top
     */
    public FarmsNodesRenderer(int levels, int max_points) {
        // nLevels = levels;
        // nMaxPoints = max_points;

        subViewCapabilities = new String[] { "Normal view", "OnTop view" };
        /**
         * all these variables have to be reinitialized when we have a new gl
         */
        hRouters = new Hashtable();
        hNetFlowDevices = new Hashtable();

        OptionsPanelValues = new Hashtable();

        // put default values for wan links
        Hashtable hWANOptions = new Hashtable();
        hWANOptions.put("LimitMinValue", Double.valueOf(0));
        hWANOptions.put("LimitMaxValue", Double.valueOf(0));
        hWANOptions.put("MinValue", Double.valueOf(0));
        hWANOptions.put("MaxValue", Double.valueOf(0));
        hWANOptions.put("MinColor", Color.CYAN);
        hWANOptions.put("MaxColor", Color.CYAN);
        OptionsPanelValues.put("wan", hWANOptions);

        // put default values for netflow links
        Hashtable hNFOptions = new Hashtable();
        hNFOptions.put("LimitMinValue", Double.valueOf(0));
        hNFOptions.put("LimitMaxValue", Double.valueOf(0));
        hNFOptions.put("MinValue", Double.valueOf(0));
        hNFOptions.put("MaxValue", Double.valueOf(0));
        hNFOptions.put("MinColor", FarmsJoglPanel.minNetFlowColor);
        hNFOptions.put("MaxColor", FarmsJoglPanel.maxNetFlowColor);
        OptionsPanelValues.put("netflow", hNFOptions);

        // put default values for ping links
        Hashtable hPingOptions = new Hashtable();
        hPingOptions.put("MinValue", Double.valueOf(0));
        hPingOptions.put("MaxValue", Double.valueOf(0));
        hPingOptions.put("MinColor", Color.GREEN);
        hPingOptions.put("MaxColor", Color.GREEN);
        OptionsPanelValues.put("ping", hPingOptions);

        // inititial values to be used for computation of node's value
        // NodeMinValue = 0.0;
        // NodeMaxValue = 100.0;
        // NodeMinColor = Color.CYAN;
        // NodeMaxColor = Color.BLUE;

    }

    /**
     * draws the farms nodes as cilinders
     */
    @Override
    public void drawNodes(GL2 gl, Object[] graphicalAttrs) {
        // get radius
        Object obj;
        obj = ((Hashtable) graphicalAttrs[0]).get("NodeRadius");
        if (obj == null) {
            return;
        }
        float radius = ((Float) obj).floatValue();

        HashMap htComputedNodes = new HashMap();// used for ontop view
        HashMap hAttrs;
        rcNode node;
        VectorO vNewPos;// used for on top view
        // node's vectors
        VectorO[] vectors;
        // Color color;
        int nodeID;
        for (Iterator it = ((HashMap) graphicalAttrs[1]).keySet().iterator(); it.hasNext();) {
            node = (rcNode) it.next();
            // save the matrix
            gl.glPushMatrix();
            try {
                obj = ((HashMap) graphicalAttrs[1]).get(node);
                if (obj != null) {// this node has its attributes computed, so draw it
                    hAttrs = (HashMap) obj;
                    vectors = (VectorO[]) hAttrs.get("PositioningVectors");
                    if (vectors == null) {
                        continue;
                    }
                    if (node.szOpticalSwitch_Name == null) {
                        obj = hAttrs.get("NodeID");
                        if (obj == null) {
                            continue;// skip to next one
                        }
                        nodeID = ((Integer) obj).intValue();
                    } else {
                        nodeID = optical_switchID;
                    }
                    // draw node
                    if (nodeID > 0 /* && vectors!=null */) {// vectors is already checked for nullity
                        if (bOnTopView) {
                            vNewPos = recomputeVectors(((HashMap) graphicalAttrs[1]), htComputedNodes, node, vectors,
                                    radius);
                        } else {
                            vNewPos = vectors[1];
                        }
                        drawNode(gl, vectors[0], vNewPos, nodeID, radius);
                    }
                    // draw node links to other nodes
                }
            } catch (Exception ex) {
                logger.log(Level.INFO, "Could not draw node " + (node == null ? "null" : node.UnitName) + " exception:"
                        + ex.getMessage());
                ex.printStackTrace();
            }
            // pop the matrix
            gl.glPopMatrix();
        }
    }

    /**
     * computes some node related info like its value to be represented<br>
     * this computation is called at RECOMPUTE_NODES_TIME to show data received<br>
     * after each recomputation, draw node
     */
    @Override
    public void computeNodes(GL2 gl, Object[] graphicalAttrs) {
        // get radius
        Object obj;
        obj = ((Hashtable) graphicalAttrs[0]).get("NodeRadius");
        if (obj == null) {
            return;
        }
        float radius = ((Float) obj).floatValue();

        // compute maximal values
        // computeNodesColors( ((HashMap)graphicalAttrs[1]));

        HashMap htComputedNodes = new HashMap();// used for ontop view

        rcNode node;
        HashMap hAttrs;
        float[] fractions;
        Color[] colors;
        int components;
        int nodeID;
        VectorO vNewPos;
        VectorO[] vectors;
        for (Iterator it = ((HashMap) graphicalAttrs[1]).keySet().iterator(); it.hasNext();) {
            node = (rcNode) it.next();
            // save the matrix
            gl.glPushMatrix();
            try {
                obj = ((HashMap) graphicalAttrs[1]).get(node);
                if (obj != null) {// this node has attributes , so consider it
                    hAttrs = (HashMap) obj;
                    if (node.szOpticalSwitch_Name == null) {
                        // determin components
                        fractions = (float[]) hAttrs.get("NodeFractions");
                        if (fractions == null) {
                            fractions = new float[10];
                            hAttrs.put("NodeFractions", fractions);
                        }
                        ;
                        colors = (Color[]) hAttrs.get("NodeColors");
                        if (colors == null) {
                            colors = new Color[10];
                            hAttrs.put("NodeColors", colors);
                        }
                        ;
                        // compute fractions and colors by updating vectors already allocated
                        if (node.errorCount > 0) {
                            components = 1;
                            fractions[0] = 1f;
                            colors[0] = Color.RED;
                        } else {
                            pie px = (pie) node.haux.get(((FarmsJoglPanel) JoglPanel.globals.mainPanel).pieKey);
                            if ((px == null) || (px.len == 0)) {
                                components = 1;
                                fractions[0] = 1f;
                                colors[0] = Color.YELLOW;
                            } else {
                                components = px.len;
                                for (int i = 0; i < components; i++) {
                                    fractions[i] = (float) px.rpie[i];
                                    colors[i] = px.cpie[i];
                                    // if(node.UnitName.equals("upb"))
                                    // System.out.println("upb"+i+": "+fractions[i]+" "+colors[i]);
                                }
                            }
                        }
                        // set number of components
                        hAttrs.put("NodeComponents", Integer.valueOf(components));
                        // create display list for node
                        // if neccessary, recompute display list
                        obj = hAttrs.get("NodeID");
                        if (obj != null) {// for that, delete old id
                            gl.glDeleteLists(((Integer) obj).intValue(), 1);
                            obj = null;
                        }
                        ;
                        if (obj == null) {// each time reconstruct the node...?
                            nodeID = gl.glGenLists(1);
                            // System.out.println("linkID generated: "+linkID);
                            gl.glNewList(nodeID, GL2.GL_COMPILE);

                            // construct node
                            constructNode(gl, components, fractions, colors);

                            gl.glEndList();
                            hAttrs.put("NodeID", Integer.valueOf(nodeID));
                        } else {
                            nodeID = ((Integer) obj).intValue();
                        }
                    } else {
                        nodeID = optical_switchID;
                        // hAttrs.put("NodeID", Integer.valueOf(nodeID));
                    }
                    vectors = (VectorO[]) hAttrs.get("PositioningVectors");
                    // draw node
                    if (bOnTopView) {
                        vNewPos = recomputeVectors(((HashMap) graphicalAttrs[1]), htComputedNodes, node, vectors,
                                radius);
                    } else {
                        vNewPos = vectors[1];
                    }
                    drawNode(gl, vectors[0], vNewPos, nodeID, radius);
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Could not compute and draw node " + (node == null ? "null" : node.UnitName)
                        + " ex:" + ex.getMessage());
                ex.printStackTrace();
            }
            // pop the matrix
            gl.glPopMatrix();
        }
    }

    /**
     * creates the cap to be used by each node
     */
    @Override
    public void initNodes(GL2 gl, Object[] graphicalAttrs) {

        // remove all aping, wan and os links, and also netflow links, they are invalid now
        Object link;
        HashMap hLinkAttrs;
        Iterator it;
        // Object obj;
        try {
            for (it = ((HashMap) graphicalAttrs[2]).keySet().iterator(); it.hasNext();) {
                link = it.next();
                hLinkAttrs = (HashMap) ((HashMap) graphicalAttrs[2]).get(link);
                /* obj = */hLinkAttrs.remove("LinkID");
                // System.out.println("remove link with id="+obj);
            }
            ;
            for (it = wanAttrs.keySet().iterator(); it.hasNext();) {
                link = it.next();
                hLinkAttrs = (HashMap) wanAttrs.get(link);
                // remove display list, as it became invalid
                /* obj = */hLinkAttrs.remove("LinkID");
                /*
                 * //replaces it with a valid one
                 * ILink ilink = (ILink)link;
                 * //generate new id
                 * float globeRad = JoglPanel.globals.globeRadius;
                 * float globeVirtRad = JoglPanel.globals.globeVirtualRadius;
                 * int linkID = gl.glGenLists(1);
                 * //System.out.println("linkID generated: "+linkID);
                 * gl.glNewList( linkID, GL.GL_COMPILE);
                 * JoGLDirectedArc3D Darc3D;
                 * Darc3D = new JoGLDirectedArc3D(ilink.fromLAT, ilink.fromLONG, ilink.toLAT, ilink.toLONG, globeRad,
                 * 10, 0.05, -globeRad+globeVirtRad, 0.2);
                 * ArrayList alArcPoints = new ArrayList();
                 * Darc3D.setPoints( alArcPoints);
                 * Darc3D.drawArc(gl);
                 * // if ( bFirstLink ) {
                 * // for( int i=0; i<alArcPoints.size(); i++)
                 * // System.out.println("point "+i+": "+alArcPoints.get(i));
                 * // bFirstLink=false;
                 * // };
                 * gl.glEndList();
                 * hLinkAttrs.put("LinkID", Integer.valueOf(linkID));
                 * hLinkAttrs.put("LinkPoints", alArcPoints);
                 * hLinkAttrs.put("LinkArrowBase", Darc3D.vPointFrom);
                 * //System.out.println("point from: "+Darc3D.vPointFrom);
                 * //System.out.println("point to: "+Darc3D.vPointTo);
                 * VectorO vPointDir = VectorO.SubstractVector( Darc3D.vPointTo, Darc3D.vPointFrom);
                 * vPointDir.Normalize();
                 * VectorO VzAxis = new VectorO(0,0,1);
                 * VectorO vRotAxis = VzAxis.CrossProduct(vPointDir);
                 * // rotate z to Vdir around vectorial product with dot product
                 * float angleRotation = (float)(Math.acos(VzAxis.DotProduct(vPointDir))* 180 / Math.PI);
                 * hLinkAttrs.put( "LinkArrowRotationAngle", new Float(angleRotation));
                 * hLinkAttrs.put("LinkArrowRotationAxis", vRotAxis);
                 */// System.out.println("replace wan link old_id="+obj+" with new_id="+linkID);
            }
            ;
            for (it = nfAttrs.keySet().iterator(); it.hasNext();) {
                link = it.next();
                hLinkAttrs = (HashMap) nfAttrs.get(link);
                // remove display list, as it became invalid
                /* obj = */hLinkAttrs.get("LinkID");
                // replaces it with a valid one
                NFLink nflink = (NFLink) link;
                // generate new id
                float globeRad = JoglPanel.globals.globeRadius;
                float globeVirtRad = JoglPanel.globals.globeVirtualRadius;
                int linkID = gl.glGenLists(1);
                // System.out.println("linkID generated: "+linkID);
                gl.glNewList(linkID, GL2.GL_COMPILE);
                JoGLDirectedArc3D Darc3D;
                Darc3D = new JoGLDirectedArc3D(nflink.fromLAT, nflink.fromLONG, nflink.toLAT, nflink.toLONG, globeRad,
                        10, 0.05, -globeRad + globeVirtRad, 0.2);
                ArrayList alArcPoints = new ArrayList();
                Darc3D.setPoints(alArcPoints);
                Darc3D.drawArc(gl);
                gl.glEndList();
                hLinkAttrs.put("LinkID", Integer.valueOf(linkID));
                hLinkAttrs.put("LinkPoints", alArcPoints);
                hLinkAttrs.put("LinkArrowBase", Darc3D.vPointFrom);
                VectorO vPointDir = VectorO.SubstractVector(Darc3D.vPointTo, Darc3D.vPointFrom);
                vPointDir.Normalize();
                VectorO VzAxis = new VectorO(0, 0, 1);
                VectorO vRotAxis = VzAxis.CrossProduct(vPointDir);
                // rotate z to Vdir around vectorial product with dot product
                float angleRotation = (float) ((Math.acos(VzAxis.DotProduct(vPointDir)) * 180) / Math.PI);
                hLinkAttrs.put("LinkArrowRotationAngle", new Float(angleRotation));
                hLinkAttrs.put("LinkArrowRotationAxis", vRotAxis);
                // System.out.println("replace netflow link old_id="+obj+" with new_id="+linkID);
            }
            ;
            for (it = osAttrs.keySet().iterator(); it.hasNext();) {
                link = it.next();
                hLinkAttrs = (HashMap) osAttrs.get(link);
                /* obj = */hLinkAttrs.remove("LinkID");
                // System.out.println("remove optical link with id="+obj);
            }
            ;
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception removing invalid links");
            ex.printStackTrace();
        }
        /*
         * rcNode node;
         * ILink link;
         * OSLink oslink;
         * try {
         * for ( Iterator it = ((HashMap)graphicalAttrs[1]).keySet().iterator(); it.hasNext(); ) {
         * node = (rcNode)it.next();
         * //for each wan link, check to see if already put
         * for ( Iterator it2 = node.wconn.values().iterator(); it2.hasNext(); ) {
         * Object objLink = it2.next();
         * // System.out.println("obj link instance of "+objLink.getClass().getName());
         * if ( objLink instanceof ILink ) {
         * link = (ILink)objLink;
         * wanAttrs.remove(link);
         * } else if ( objLink instanceof OSLink ) {
         * oslink = (OSLink)objLink;
         * if ( oslink.checkFlag(OSLink.OSLINK_FLAG_FAKE_NODE) )
         * continue;
         * osAttrs.remove(oslink);
         * };
         * }
         * };
         * } catch(Exception ex) {
         * System.out.println("Exception removing invalid wan links");
         * ex.printStackTrace();
         * }
         */
        routerID = DataGlobals.getBubbleID(gl, routerTextureFile, routerColor);
        nfDeviceID = DataGlobals.getBubbleID(gl, nfDeviceTextureFile, nfDeviceColor);
        nfRouterID = DataGlobals.getBubbleID(gl, nfRouterTextureFile, nfDeviceColor);
        optical_switchID = DataGlobals.getBubbleID(gl, "lia/images/ml_optical_switch.png", routerColor);
        packageID = DataGlobals.getCubeID(gl, "lia/images/ml_package.jpg", "Package Cube");
        packageNFID = gl.glGenLists(1);
        // System.out.println("routerID="+ routerID);
        gl.glNewList(packageNFID, GL2.GL_COMPILE);
        {
            ZoomMapRenderer.drawSphere(gl, 8, 4);
        }
        gl.glEndList();
        conID = 0;
        if (conID == 0) {
            int nMaxPoints = 8;
            conID = gl.glGenLists(1);
            // System.out.println("conID="+conID);
            gl.glNewList(conID, GL2.GL_COMPILE);
            {
                float faDelta = (2 * (float) Math.PI) / nMaxPoints, faAlfa;
                float fX, fY;
                // draw upper cone
                gl.glBegin(GL.GL_TRIANGLE_FAN);
                // top
                gl.glVertex3f(0, 0, 2);
                // side
                faAlfa = 0.0f;
                for (int slice = 0; slice <= nMaxPoints; slice++) {
                    fX = (float) Math.cos(faAlfa);
                    fY = (float) Math.sin(faAlfa);
                    gl.glVertex3f(fX, fY, 0);
                    faAlfa += faDelta;
                }
                gl.glEnd();

                // draw base circle
                gl.glBegin(GL.GL_TRIANGLE_FAN);
                // top
                gl.glVertex3f(0, 0, 0);
                // side
                faAlfa = (float) (2 * Math.PI);
                for (int slice = 0; slice <= nMaxPoints; slice++) {
                    fX = (float) Math.cos(faAlfa);
                    fY = (float) Math.sin(faAlfa);
                    gl.glVertex3f(fX, fY, 0);
                    faAlfa -= faDelta;
                }
                gl.glEnd();
            }
            gl.glEndList();
        }
    }

    private final float[] topCenter = new float[] { 0, 0, .5f };

    private final float[] pa1 = new float[3];

    private final float[] pa2 = new float[3];

    private final float[] p1 = new float[3];

    private final float[] p2 = new float[3];

    private final float[] aux = new float[3];

    private final float fraction = .2f;

    /**
     * draws a node centered in (0,0,0) pointed in z direction, and having unitar dimensions,
     * with all the colors on it
     *
     * @param gl
     * @param Vdir
     * @param Vpos
     * @param components
     * @param fractions
     * @param colors
     * @param radius
     */
    private void constructNode(GL2 gl, int components, float[] fractions, Color[] colors) {
        VectorO Vdir = new VectorO(0, 0, 1);
        VectorO Vp = new VectorO(1, 0, 0);
        // VectorO Vpos = new VectorO(0,0,0);
        // int max_points = 32;//total number of points to be drawn
        float curr_Tangle = 0f;// current total angle of drawn cilinder
        int points;// number of points to be drawn for a fraction 0-32
        float angle;// distance in grades between two consecutive points - depends on current component to be drawn
        // compute start points
        p1[0] = Vp.getX();
        p2[0] = Vp.getX();
        p1[1] = Vp.getY();
        p2[1] = Vp.getY();
        p1[2] = Vp.getZ();
        p2[2] = Vp.getZ() + .5f;

        for (int i = 0; i < components; i++) {
            points = 1 + (int) (fractions[i] * 31);
            angle = (360f * fractions[i]) / points;
            // draw first point
            for (int j = 0; j < points; j++) {
                // draw next points
                // set previous points, before computing the new ones, after rotation
                pa1[0] = p1[0];
                pa1[1] = p1[1];
                pa1[2] = p1[2];
                pa2[0] = p2[0];
                pa2[1] = p2[1];
                pa2[2] = p2[2];
                if ((curr_Tangle + angle) > 360f) {
                    angle = 360f - curr_Tangle;
                }
                Vp.Rotate(Vdir, angle);
                curr_Tangle += angle;
                // compute current points
                p1[0] = Vp.getX();
                p2[0] = Vp.getX();
                p1[1] = Vp.getY();
                p2[1] = Vp.getY();
                p1[2] = Vp.getZ();
                p2[2] = Vp.getZ() + .5f;
                gl.glColor3f(colors[i].getRed() / 255f, colors[i].getGreen() / 255f, colors[i].getBlue() / 255f);
                gl.glBegin(GL.GL_TRIANGLE_STRIP);
                // top triangle
                gl.glVertex3fv(topCenter, 0);
                // ---code insertion---//
                aux[0] = pa2[0] * (1f - fraction);
                aux[1] = pa2[1] * (1f - fraction);
                aux[2] = (pa2[2] * (1f - fraction)) + (.5f * fraction);
                gl.glVertex3fv(aux, 0);
                aux[0] = p2[0] * (1f - fraction);
                aux[1] = p2[1] * (1f - fraction);
                aux[2] = (p2[2] * (1f - fraction)) + (0.5f * fraction);
                gl.glVertex3fv(aux, 0);
                gl.glColor3f(colors[i].getRed() / 2f / 255f, colors[i].getGreen() / 2f / 255f,
                        colors[i].getBlue() / 2f / 255f);
                // ---end code insertion---//
                gl.glVertex3fv(pa2, 0);
                gl.glVertex3fv(p2, 0);
                // side triangles
                // gl.glColor3f(colors[i].getRed()/2f/255f, colors[i].getGreen()/2f/255f, colors[i].getBlue()/2f/255f);
                gl.glVertex3fv(pa1, 0);
                gl.glVertex3fv(p1, 0);
                // base triangle
                gl.glEnd();
            }// end for points
        }// end for components
    }

    /**
     * draws a node by creating a diplay list for it
     *
     * @param gl
     * @param Vdir
     * @param Vpos
     * @param components
     * @param fractions
     * @param colors
     * @param radius
     */
    private void drawNode(GL2 gl, VectorO VdirInit, VectorO VposInit, int nodeID, float radius) {
        // operations are put in inverse order because last is first executed in opengl
        // 3. position object cu correct coordinates
        gl.glTranslatef(VposInit.getX(), VposInit.getY(), VposInit.getZ());
        // 2. operation: rotate from (0,0,1) to Vdir
        // for that, first compute rotation axis as cross product between z and Vdir
        VectorO VzAxis = new VectorO(0, 0, 1);
        VectorO VRotAxis = VzAxis.CrossProduct(VdirInit);
        // rotate z to Vdir around vectorial product with dot product
        gl.glRotatef((float) ((Math.acos(VzAxis.DotProduct(VdirInit)) * 180) / Math.PI), VRotAxis.getX(),
                VRotAxis.getY(), VRotAxis.getZ());
        // 1. operation: scale to radius dimmensions:
        gl.glScalef(radius, radius, radius);
        // use already constructed cap as display list
        // draw it at (0,0,0) in self reference system
        gl.glCallList(nodeID);
    }

    /**
     * draws links, if their attributes are computed, and invalidate links is not set
     */
    @Override
    public void drawLinks(GL2 gl, Object[] graphicalAttrs) {
        // get radius
        Object obj;
        obj = ((Hashtable) graphicalAttrs[0]).get("NodeRadius");
        if (obj == null) {
            return;
        }
        float radius = ((Float) obj).floatValue();

        // check options in panel to recompute colors
        if (((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowWAN.isSelected()) {
            drawWAN(gl, radius);
        }
        if (((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowNF.isSelected()) {
            drawNF(gl, radius);
        }
        if (((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowOS.isSelected()) {
            drawOS(gl, radius);
        }

        if (!((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowPing.isSelected()) {
            return;
        }

        ILink link;
        HashMap hLinkAttrs;
        float[] colorLink;
        int linkID;
        try {
            for (Iterator it = ((HashMap) graphicalAttrs[2]).keySet().iterator(); it.hasNext();) {
                link = (ILink) it.next();
                hLinkAttrs = (HashMap) ((HashMap) graphicalAttrs[2]).get(link);
                // draw the link
                obj = hLinkAttrs.get("LinkColor");
                if (obj == null) {
                    continue;// skip to next one
                }
                colorLink = (float[]) obj;
                obj = hLinkAttrs.get("LinkID");
                if (obj == null) {
                    continue;// skip to next one
                }
                linkID = ((Integer) obj).intValue();

                if ((link.name != null) && (link.name.indexOf("NetLink") >= 0)) {
                    gl.glColor3fv(Color.GREEN.getColorComponents(null), 0);
                } else {
                    gl.glColor3fv(colorLink, 0);
                }
                gl.glCallList(linkID);

                // draw arrow on link
                if (conID > 0) {// if there is an object to be drawn
                    gl.glPushMatrix();
                    try {
                        VectorO vLinkArrowBase = (VectorO) hLinkAttrs.get("LinkArrowBase");
                        float fLinkArrowRotationAngle = ((Float) hLinkAttrs.get("LinkArrowRotationAngle")).floatValue();
                        VectorO vLinkArrowRotationAxis = (VectorO) hLinkAttrs.get("LinkArrowRotationAxis");

                        // operations are put in inverse order because last is first executed in opengl
                        // 3. position object cu correct coordinates
                        gl.glTranslatef(vLinkArrowBase.getX(), vLinkArrowBase.getY(), vLinkArrowBase.getZ());
                        // 2. operation: rotate from (0,0,1) to Vdir
                        gl.glRotatef(fLinkArrowRotationAngle, vLinkArrowRotationAxis.getX(),
                                vLinkArrowRotationAxis.getY(), vLinkArrowRotationAxis.getZ());
                        // 1. operation: scale to radius dimmensions:
                        gl.glScalef(radius / 2, radius / 2, radius / 2);
                        // use already constructed cap as display list
                        // draw it at (0,0,0) in self reference system
                        gl.glCallList(conID);
                    } catch (Exception ex) {
                        logger.log(Level.INFO, "Exception drawing arrow for link: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                    gl.glPopMatrix();
                }
                ;
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception drawing links: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * computes and draws links; recomputes color and, if links invalidated or link without diplay list,
     * recomputes diplay list for link
     */
    @Override
    public void computeLinks(GL2 gl, Object[] graphicalAttrs) {
        // check to see if all links are invalidated, so to recompute them
        boolean bInvalidateLinks = (((Hashtable) graphicalAttrs[0]).remove("InvalidateLinks") != null);
        Object obj;

        // get radius
        obj = ((Hashtable) graphicalAttrs[0]).get("NodeRadius");
        if (obj == null) {
            return;
        }
        float radius = ((Float) obj).floatValue();

        // check options in panel to recompute wan colors
        if (((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowWAN.isSelected()) {
            // System.out.println("<mluc>  compute wan links");
            computeWANTrafficColors(wanAttrs);
            computeWAN(gl, graphicalAttrs, bInvalidateLinks, radius);// does the same job as DataRenderer.updateLinks
            // and this.computeLinks
        }
        ;

        // check options in panel to recompute net flow colors
        if (((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowNF.isSelected()) {
            computeNFTrafficColors(nfAttrs);
            computeNF(gl, graphicalAttrs, bInvalidateLinks, radius);// does the same job as DataRenderer.updateLinks and
            // this.computeLinks
        }
        ;

        // check options in panel to recompute os colors
        if (((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowOS.isSelected()) {
            computeOS(gl, graphicalAttrs, bInvalidateLinks, radius);// does the same job as DataRenderer.updateLinks and
            // this.computeLinks
        }
        ;

        if (!((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowPing.isSelected()) {
            return;
        }

        // check options in panel to recompute colors
        computePingTrafficColors(((HashMap) graphicalAttrs[2]), ((HashMap) graphicalAttrs[1]));

        // global variables needed for drawing nodes, get them now to avoid concurency on them later
        float globeRad = JoglPanel.globals.globeRadius;
        float globeVirtRad = JoglPanel.globals.globeVirtualRadius;

        ILink link;
        HashMap hLinkAttrs;
        float[] colorLink;
        int linkID;
        rcNode ns, nw;
        try {
            for (Iterator it = ((HashMap) graphicalAttrs[2]).keySet().iterator(); it.hasNext();) {
                link = (ILink) it.next();
                hLinkAttrs = (HashMap) ((HashMap) graphicalAttrs[2]).get(link);

                // recompute color
                obj = hLinkAttrs.get("LinkColor");
                if (obj == null) {
                    colorLink = new float[3];
                } else {
                    colorLink = (float[]) obj;
                }
                ns = (rcNode) hLinkAttrs.get("fromNode");
                if (ns == null) {
                    continue;
                }
                nw = (rcNode) hLinkAttrs.get("toNode");
                if (nw == null) {
                    continue;
                }
                link.speed = ns.connPerformance(nw);
                link.data = Double.valueOf(ns.connLP(nw));

                Color c;
                if (((Double) link.data).doubleValue() == 1.0) {
                    c = Color.RED;
                } else {
                    c = ((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.csPing.getColor(link.speed);
                    if (c == null) {
                        continue;
                    }
                }
                colorLink[0] = c.getRed() / 255f;
                colorLink[1] = c.getGreen() / 255f;
                colorLink[2] = c.getBlue() / 255f;

                // colorLink = getLinkQualityColor(link, colorLink);
                if (obj == null) {
                    hLinkAttrs.put("LinkColor", colorLink);
                }

                // check coordinates for source and destination nodes to see if any changes, and, if so
                // update values for link and recompute it
                float srcLat, srcLong, dstLat, dstLong;
                boolean bInvalidateThisLink = false;
                srcLat = DataGlobals.failsafeParseFloat(ns.LAT, -21.22f);
                srcLong = DataGlobals.failsafeParseFloat(ns.LONG, -111.15f);
                dstLat = DataGlobals.failsafeParseFloat(nw.LAT, -21.22f);
                dstLong = DataGlobals.failsafeParseFloat(nw.LONG, -111.15f);
                if ((srcLat != link.fromLAT) || (srcLong != link.fromLONG) || (dstLat != link.toLAT)
                        || (dstLong != link.toLONG)) {
                    // update link's start and end coordinates
                    link.fromLAT = srcLat;
                    link.fromLONG = srcLong;
                    link.toLAT = dstLat;
                    link.toLONG = dstLong;
                    // invalidate this link
                    bInvalidateThisLink = true;
                }

                // if neccessary, recompute display list
                obj = hLinkAttrs.get("LinkID");
                if ((obj != null) && (bInvalidateLinks || bInvalidateThisLink)) {// for that, delete old id
                    gl.glDeleteLists(((Integer) obj).intValue(), 1);
                    obj = null;
                }
                ;
                if (obj == null) {
                    linkID = gl.glGenLists(1);
                    // System.out.println("linkID generated: "+linkID);
                    gl.glNewList(linkID, GL2.GL_COMPILE);

                    // JoGLArc3D arc3D = new JoGLArc3D(link.fromLAT, link.fromLONG, link.toLAT, link.toLONG, globeRad,
                    // 10, 0.02, -globeRad+globeVirtRad);
                    // arc3D.drawArc(gl);
                    JoGLDirectedArc3D Darc3D;
                    Darc3D = new JoGLDirectedArc3D(link.fromLAT, link.fromLONG, link.toLAT, link.toLONG, globeRad, 10,
                            0.05, -globeRad + globeVirtRad, 0.2);
                    Darc3D.drawArc(gl);
                    gl.glEndList();
                    hLinkAttrs.put("LinkID", Integer.valueOf(linkID));
                    hLinkAttrs.put("LinkArrowBase", Darc3D.vPointFrom);
                    // System.out.println("point from: "+Darc3D.vPointFrom);
                    // System.out.println("point to: "+Darc3D.vPointTo);
                    VectorO vPointDir = VectorO.SubstractVector(Darc3D.vPointTo, Darc3D.vPointFrom);
                    vPointDir.Normalize();
                    VectorO VzAxis = new VectorO(0, 0, 1);
                    VectorO vRotAxis = VzAxis.CrossProduct(vPointDir);
                    // rotate z to Vdir around vectorial product with dot product
                    float angleRotation = (float) ((Math.acos(VzAxis.DotProduct(vPointDir)) * 180) / Math.PI);
                    hLinkAttrs.put("LinkArrowRotationAngle", new Float(angleRotation));
                    hLinkAttrs.put("LinkArrowRotationAxis", vRotAxis);
                } else {
                    linkID = ((Integer) obj).intValue();
                }

                // draw the link
                // colorLink = (float[])hLinkAttrs.get("LinkColor");
                if ((link.name != null) && (link.name.indexOf("NetLink") >= 0)) {
                    gl.glColor3fv(Color.GREEN.getColorComponents(null), 0);
                } else {
                    gl.glColor3fv(colorLink, 0);
                }

                gl.glCallList(linkID);

                // draw arrow on link
                if (conID > 0) {// if there is an object to be drawn
                    gl.glPushMatrix();
                    try {
                        VectorO vLinkArrowBase = (VectorO) hLinkAttrs.get("LinkArrowBase");
                        float fLinkArrowRotationAngle = ((Float) hLinkAttrs.get("LinkArrowRotationAngle")).floatValue();
                        VectorO vLinkArrowRotationAxis = (VectorO) hLinkAttrs.get("LinkArrowRotationAxis");

                        // operations are put in inverse order because last is first executed in opengl
                        // 3. position object cu correct coordinates
                        // System.out.println("link arrow base: "+vLinkArrowBase);
                        gl.glTranslatef(vLinkArrowBase.getX(), vLinkArrowBase.getY(), vLinkArrowBase.getZ());
                        // 2. operation: rotate from (0,0,1) to Vdir
                        // System.out.println("link arrow rotation axis: "+vLinkArrowRotationAxis);
                        gl.glRotatef(fLinkArrowRotationAngle, vLinkArrowRotationAxis.getX(),
                                vLinkArrowRotationAxis.getY(), vLinkArrowRotationAxis.getZ());
                        // 1. operation: scale to radius dimmensions:
                        gl.glScalef(radius / 2, radius / 2, radius / 2);
                        // use already constructed cap as display list
                        // draw it at (0,0,0) in self reference system
                        gl.glCallList(conID);
                    } catch (Exception ex) {
                        logger.log(Level.INFO, "Exception drawing arrow for link: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                    gl.glPopMatrix();
                }
                ;
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception computing links: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * computes wan links
     *
     * @param gl
     * @param graphicalAttrs
     * @param bInvalidateLinks
     */
    private void computeWAN(GL2 gl, Object[] graphicalAttrs, boolean bInvalidateLinks, float radius) {
        Object obj;
        ILink link;
        HashMap hLinkAttrs;
        rcNode nFrom, node;
        HashMap hOrderNodes = new HashMap();
        try {
            int nOrder;
            // System.out.println("compute wan links");
            for (Iterator it = ((HashMap) graphicalAttrs[1]).keySet().iterator(); it.hasNext();) {
                node = (rcNode) it.next();
                // System.out.println("node "+node.shortName+" is "+(node.bHiddenOnMap?"":"not")+" hidden");
                if (!node.bHiddenOnMap) {
                    // for each wan link, check to see if already put
                    // because a router doesn't realy rely on one node,
                    // the hashmap is no more cleared for each node
                    // hOrderNodes.clear();
                    for (Iterator it2 = node.wconn.values().iterator(); it2.hasNext();) {
                        Object objLink = it2.next();
                        // System.out.println("obj link instance of "+objLink.getClass().getName());
                        if (!(objLink instanceof ILink)) {
                            continue;
                        }
                        link = (ILink) objLink;
                        // System.out.println("wan link "+link.name);
                        obj = wanAttrs.get(link);
                        WANRouter routerFrom = null;
                        WANRouter routerTo = null;
                        if (obj == null) {
                            hLinkAttrs = new HashMap();
                            // remember parent node
                            hLinkAttrs.put("fromNode", node);
                            wanAttrs.put(link, hLinkAttrs);

                            // put routers at each end of the wan link
                            Object objRouter;
                            // String cityFrom = (String) link.from.get("CITY");
                            if ((link.from != null) && (link.from.get("LONG") != null)
                                    && (link.from.get("LAT") != null) && (link.to != null)
                                    && (link.to.get("LONG") != null) && (link.to.get("LAT") != null)) {
                                String locationFrom = link.from.get("LONG") + "/" + link.from.get("LAT");
                                // System.out.println("location from: "+locationFrom);
                                if ((objRouter = hRouters.get(locationFrom)) == null) {
                                    // float []vectorPos = new float[2];
                                    float posLONG, posLAT;
                                    /* vectorPos[0] = */posLONG = DataGlobals.failsafeParseFloat(
                                            link.from.get("LONG"), -111.15f);
                                    /* vectorPos[1] = */posLAT = DataGlobals.failsafeParseFloat(link.from.get("LAT"),
                                            -21.22f);
                                    // hRouters.put( locationFrom, vectorPos);
                                    // parse router location
                                    String sLocation = null;
                                    // int nMinusSeparator=link.name.indexOf('-');
                                    // //from link name that looks like: town1-town2_(IN|OUT)
                                    // if ( nMinusSeparator!=-1 ) {
                                    // if ( link.name.endsWith("_IN") ) {
                                    // //from is town2
                                    // sLocation = link.name.substring( nMinusSeparator+1, link.name.length()-3);
                                    // } else if ( link.name.endsWith("_OUT") ) {
                                    // //from is town1
                                    // sLocation = link.name.substring(0, nMinusSeparator);
                                    // }
                                    // };
                                    sLocation = link.from.get("CITY");
                                    routerFrom = new WANRouter(posLONG, posLAT, sLocation);
                                    hRouters.put(locationFrom, routerFrom);
                                } else if (objRouter instanceof WANRouter) {
                                    routerFrom = (WANRouter) objRouter;
                                    // if link already there, will be ignored
                                }
                                hLinkAttrs.put("routerFrom", routerFrom);
                                routerFrom.fromLinks.add(link);
                                // String cityTo = (String) link.to.get("CITY");
                                String locationTo = link.to.get("LONG") + "/" + link.to.get("LAT");
                                // System.out.println("location to: "+locationTo);
                                if ((objRouter = hRouters.get(locationTo)) == null) {
                                    float posLONG, posLAT;
                                    posLONG = DataGlobals.failsafeParseFloat(link.to.get("LONG"), -111.15f);
                                    posLAT = DataGlobals.failsafeParseFloat(link.to.get("LAT"), -21.22f);
                                    // hRouters.put( locationTo, vectorPos);
                                    // parse router location
                                    String sLocation = null;
                                    // int nMinusSeparator=link.name.indexOf('-');
                                    // //from link name that looks like: town1-town2_(IN|OUT)
                                    // if ( nMinusSeparator!=-1 ) {
                                    // if ( link.name.endsWith("_OUT") ) {
                                    // //to is town2
                                    // sLocation = link.name.substring( nMinusSeparator+1, link.name.length()-4);
                                    // } else if ( link.name.endsWith("_IN") ) {
                                    // //to is town1
                                    // sLocation = link.name.substring(0, nMinusSeparator);
                                    // }
                                    // }
                                    sLocation = link.to.get("CITY");
                                    routerTo = new WANRouter(posLONG, posLAT, sLocation);
                                    hRouters.put(locationTo, routerTo);
                                } else if (objRouter instanceof WANRouter) {
                                    routerTo = (WANRouter) objRouter;
                                    // if link already there, will be ignored
                                }
                                ;
                                hLinkAttrs.put("routerTo", routerTo);
                                routerTo.toLinks.add(link);
                            }
                        } else {
                            hLinkAttrs = (HashMap) obj;
                            routerFrom = (WANRouter) hLinkAttrs.get("routerFrom");
                            routerTo = (WANRouter) hLinkAttrs.get("routerTo");
                            // check to see if the coordinates for this wan link have changed
                        }
                        ;
                        if ((routerFrom != null) && (routerTo != null)) {
                            // here i have the creator node, the source and
                            // destination routers, so sort links
                            // check the order hashmap
                            nOrder = 1;// if not found, it will be one
                            WANRouter[] keySel = null;
                            for (Iterator it3 = hOrderNodes.keySet().iterator(); it3.hasNext();) {
                                WANRouter[] key = (WANRouter[]) it3.next();
                                if (routerFrom.equals(key[0]) && routerTo.equals(key[1]) /*
                                                                                         * ||
                                                                                         * routerFrom.equals(key[1]) &&
                                                                                         * routerTo.equals(key[0])
                                                                                         */) {
                                    // another link(s) already exist between these two routers
                                    // increment order and break cycle
                                    nOrder = ((Integer) hOrderNodes.get(key)).intValue() + 1;
                                    keySel = key;
                                    break;
                                }
                            }
                            // check the previous order number, so that is different, then
                            // a new link to be generated
                            obj = hLinkAttrs.get("Order");
                            if (obj != null) {
                                int nOldOrder = ((Integer) obj).intValue();
                                if (nOldOrder != nOrder) {
                                    hLinkAttrs.remove("LinkID");
                                }
                            }
                            hLinkAttrs.put("Order", Integer.valueOf(nOrder));
                            if (keySel == null) {
                                keySel = new WANRouter[] { routerFrom, routerTo };
                            }
                            // System.out.println("from "+routerFrom.toString()+" to "+routerTo+" link number="+nOrder);
                            hOrderNodes.put(keySel, Integer.valueOf(nOrder));
                        }
                    }
                    ;
                }
            }
            ;
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception checking new wan links. Exception: " + ex.getMessage());
            ex.printStackTrace();
        }

        // ServiceID sidTo, sidFrom;
        // remove from renderer's wan map the ones that are no longer into mst vector
        try {
            // check if mst link has start and end node
            for (Iterator it = wanAttrs.keySet().iterator(); it.hasNext();) {
                link = (ILink) it.next();
                hLinkAttrs = (HashMap) wanAttrs.get(link);
                nFrom = (rcNode) hLinkAttrs.get("fromNode");
                if (nFrom == null) {
                    it.remove();
                    // remove from routers list also
                    removeLinkFromRouters(link);
                }
                ;
                boolean contains = false;
                for (Enumeration en = nFrom.wconn.elements(); en.hasMoreElements();) {
                    if (link.equals(en.nextElement())) {
                        contains = true;
                        break;
                    }
                }
                if (nFrom.bHiddenOnMap || !contains || !((HashMap) graphicalAttrs[1]).keySet().contains(nFrom)) {
                    it.remove();
                    // remove from routers list also
                    removeLinkFromRouters(link);
                }
                ;
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception checking wan links removal: " + ex.getMessage());
            ex.printStackTrace();
        }

        // remove routers
        // not any more neccessary
        // but recompute traffic
        recomputeTrafficForRouters();
        /*
         * try {
         * //check if mst link has start and end node
         * for( Iterator it = hRouters.keySet().iterator(); it.hasNext(); ) {
         * String location = (String)it.next();
         * boolean removeIt = true;
         * for(Iterator lit=wanAttrs.keySet().iterator(); lit.hasNext(); ){
         * link = (ILink) lit.next();
         * if ( location.equals( (String)link.from.get("LONG")+"/"+(String)link.from.get("LAT"))
         * || location.equals(link.to.get("LONG")+"/"+link.to.get("LAT")) ) {
         * removeIt = false;
         * break;
         * }
         * }
         * if(removeIt)
         * it.remove();
         * }
         * } catch (Exception ex) {
         * logger.log(Level.INFO, "Exception checking routers removal: "+ex.getMessage());
         * ex.printStackTrace();
         * }
         */

        // compute graphical information for each mst link taking in account the invalidate links flag
        // global variables needed for drawing nodes, get them now to avoid concurency on them later
        float globeRad = JoglPanel.globals.globeRadius;
        float globeVirtRad = JoglPanel.globals.globeVirtualRadius;

        float[] colorLink;
        int linkID;
        try {
            // boolean bFirstLink = true;
            for (Iterator it = wanAttrs.keySet().iterator(); it.hasNext();) {
                link = (ILink) it.next();
                hLinkAttrs = (HashMap) wanAttrs.get(link);

                // get link order
                obj = hLinkAttrs.get("Order");
                if ((obj == null) || !(obj instanceof Integer)) {
                    continue;
                }
                int nOrder = ((Integer) obj).intValue();
                // recompute color
                Color c = ((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.csWAN.getColor(((Double) (link.data))
                        .doubleValue());
                if (c == null) {
                    continue;
                }
                obj = hLinkAttrs.get("LinkColor");
                if (obj == null) {
                    colorLink = new float[3];
                } else {
                    colorLink = (float[]) obj;
                }
                colorLink[0] = c.getRed() / 255f;
                colorLink[1] = c.getGreen() / 255f;
                colorLink[2] = c.getBlue() / 255f;
                if (obj == null) {
                    hLinkAttrs.put("LinkColor", colorLink);
                }

                // recompute value
                Double value = ((Double) (link.data));
                if (value == null) {
                    continue;
                }
                hLinkAttrs.put("LinkValue", value);
                double val = value.doubleValue();
                int line_width = 1;
                /**
                 * compute line width
                 * max line widht is 3, minimum is 1
                 */
                Hashtable hWANOptions = (Hashtable) OptionsPanelValues.get("wan");
                double Max = ((Double) hWANOptions.get("MaxValue")).doubleValue();
                double Min = ((Double) hWANOptions.get("MinValue")).doubleValue();
                if ((Max >= val) && (Max > Min) && (Max > 0) && (val >= Min)) {
                    line_width = (int) (3 - ((2 * (Max - val)) / (Max - Min)));
                    if (line_width < 1) {
                        line_width = 1;
                    }
                } else {
                    line_width = 1;
                }
                // System.out.println("compute wan link width: "+line_width);
                hLinkAttrs.put("LinkWidth", Integer.valueOf(line_width));

                // double MaxLimit = ((Double)hWANOptions.get( "LimitMaxValue")).doubleValue();
                // double MinLimit = ((Double)hWANOptions.get( "LimitMinValue")).doubleValue();
                // compute number of steps
                // to Max corresponds MAX_SPEED_WAN_SEGMENT_STEPS and
                // to Min corresponds MIN_SPEED_WAN_SEGMENT_STEPS
                int total_steps = 0;
                // if ( MaxLimit>MinLimit )
                // total_steps = (int)(MIN_SPEED_WAN_SEGMENT_STEPS + (link.speed-MinLimit)
                // *(MAX_SPEED_WAN_SEGMENT_STEPS-MIN_SPEED_WAN_SEGMENT_STEPS)
                // /(MaxLimit-MinLimit));
                total_steps = MIN_SPEED_WAN_SEGMENT_STEPS
                        + (Max > 0 ? (int) (((MAX_SPEED_WAN_SEGMENT_STEPS - MIN_SPEED_WAN_SEGMENT_STEPS) * val) / Max)
                                : 0);
                if (total_steps > MIN_SPEED_WAN_SEGMENT_STEPS) {
                    total_steps = MIN_SPEED_WAN_SEGMENT_STEPS;
                }
                if (total_steps < MAX_SPEED_WAN_SEGMENT_STEPS) {
                    total_steps = MAX_SPEED_WAN_SEGMENT_STEPS;
                }
                // System.out.println("total_steps="+total_steps);
                hLinkAttrs.put("WANSegmentSteps", Integer.valueOf(total_steps));

                // if neccessary, recompute display list
                obj = hLinkAttrs.get("LinkID");
                if ((obj != null) && bInvalidateLinks) {// for that, delete old id
                    gl.glDeleteLists(((Integer) obj).intValue(), 1);
                    obj = null;
                }
                ;
                if (obj == null) {
                    linkID = gl.glGenLists(1);
                    // System.out.println("linkID generated: "+linkID);
                    gl.glNewList(linkID, GL2.GL_COMPILE);
                    JoGLDirectedArc3D Darc3D;
                    Darc3D = new JoGLDirectedArc3D(link.fromLAT, link.fromLONG, link.toLAT, link.toLONG, globeRad, 10,
                            0.05/*
                                * +
                                * 0.01
                                * *
                                * nOrder
                                */, -globeRad + globeVirtRad, 0.1 + (0.1 * nOrder));
                    ArrayList alArcPoints = new ArrayList();
                    Darc3D.setPoints(alArcPoints);
                    Darc3D.drawArc(gl);
                    // if ( bFirstLink ) {
                    // for( int i=0; i<alArcPoints.size(); i++)
                    // System.out.println("point "+i+": "+alArcPoints.get(i));
                    // bFirstLink=false;
                    // };
                    gl.glEndList();
                    hLinkAttrs.put("LinkID", Integer.valueOf(linkID));
                    hLinkAttrs.put("LinkPoints", alArcPoints);
                    hLinkAttrs.put("LinkArrowBase", Darc3D.vPointFrom);
                    // System.out.println("point from: "+Darc3D.vPointFrom);
                    // System.out.println("point to: "+Darc3D.vPointTo);
                    VectorO vPointDir = VectorO.SubstractVector(Darc3D.vPointTo, Darc3D.vPointFrom);
                    vPointDir.Normalize();
                    VectorO VzAxis = new VectorO(0, 0, 1);
                    VectorO vRotAxis = VzAxis.CrossProduct(vPointDir);
                    // rotate z to Vdir around vectorial product with dot product
                    float angleRotation = (float) ((Math.acos(VzAxis.DotProduct(vPointDir)) * 180) / Math.PI);
                    hLinkAttrs.put("LinkArrowRotationAngle", new Float(angleRotation));
                    hLinkAttrs.put("LinkArrowRotationAxis", vRotAxis);
                } else {
                    linkID = ((Integer) obj).intValue();
                }
                ;

                // draw the link
                // colorLink = (float[])hLinkAttrs.get("LinkColor");
                gl.glLineWidth(line_width);
                if ((link.name != null) && (link.name.indexOf("NetLink") >= 0)) {
                    gl.glColor3fv(Color.GREEN.getColorComponents(null), 0);
                } else {
                    gl.glColor3fv(colorLink, 0);
                }
                gl.glCallList(linkID);
                gl.glLineWidth(1f);
                if ((link.name != null) && (link.name.indexOf("NetLink") >= 0)) {
                    gl.glColor3fv(Color.GREEN.getColorComponents(null), 0);
                } else {
                    gl.glColor3fv(colorLink, 0);
                }

                // draw arrow on link
                if (conID > 0) {// if there is an object to be drawn
                    gl.glPushMatrix();
                    try {
                        VectorO vLinkArrowBase = (VectorO) hLinkAttrs.get("LinkArrowBase");
                        float fLinkArrowRotationAngle = ((Float) hLinkAttrs.get("LinkArrowRotationAngle")).floatValue();
                        VectorO vLinkArrowRotationAxis = (VectorO) hLinkAttrs.get("LinkArrowRotationAxis");

                        // operations are put in inverse order because last is first executed in opengl
                        // 3. position object cu correct coordinates
                        // System.out.println("link arrow base: "+vLinkArrowBase);
                        gl.glTranslatef(vLinkArrowBase.getX(), vLinkArrowBase.getY(), vLinkArrowBase.getZ());
                        // 2. operation: rotate from (0,0,1) to Vdir
                        // System.out.println("link arrow rotation axis: "+vLinkArrowRotationAxis);
                        gl.glRotatef(fLinkArrowRotationAngle, vLinkArrowRotationAxis.getX(),
                                vLinkArrowRotationAxis.getY(), vLinkArrowRotationAxis.getZ());
                        // 1. operation: scale to radius dimmensions:
                        gl.glScalef(radius / 2, radius / 2, radius / 2);
                        // use already constructed cap as display list
                        // draw it at (0,0,0) in self reference system
                        gl.glCallList(conID);
                    } catch (Exception ex) {
                        logger.log(Level.INFO, "Exception drawing arrow for link: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                    gl.glPopMatrix();
                }
                ;
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception computing graphical attributes for wan links: " + ex.getMessage());
            ex.printStackTrace();
        }

        // listRouters();
        drawRouters(gl, radius * .9f);

        if (((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbAnimateWAN.isSelected()) {
            drawPackages(gl, radius);
        }
    }

    private void computeNF(GL2 gl, Object[] graphicalAttrs, boolean bInvalidateLinks, float radius) {
        Object obj;
        NFLink link;
        HashMap hLinkAttrs;
        rcNode nCreator, node;
        HashMap hOrderNodes = new HashMap();
        try {
            int nOrder;
            for (Iterator it = ((HashMap) graphicalAttrs[1]).keySet().iterator(); it.hasNext();) {
                node = (rcNode) it.next();
                if (!node.bHiddenOnMap) {
                    // for each net flow link, check to see if already put
                    for (Iterator it2 = node.wconn.values().iterator(); it2.hasNext();) {
                        Object objLink = it2.next();
                        if (!(objLink instanceof NFLink)) {
                            continue;
                        }
                        link = (NFLink) objLink;
                        obj = nfAttrs.get(link);
                        NetFlowDevice routerFrom = null;
                        NetFlowDevice routerTo = null;
                        if (obj == null) {
                            hLinkAttrs = new HashMap();
                            // remember parent node
                            hLinkAttrs.put("creatorNode", node);
                            nfAttrs.put(link, hLinkAttrs);

                            // put netflow router at each end of the net flow link
                            Object objRouter;
                            if ((link.from != null) && (link.from.get("LONG") != null)
                                    && (link.from.get("LAT") != null) && (link.to != null)
                                    && (link.to.get("LONG") != null) && (link.to.get("LAT") != null)) {
                                String locationFrom = link.from.get("LONG") + "/" + link.from.get("LAT");
                                if ((objRouter = hNetFlowDevices.get(locationFrom)) == null) {
                                    float posLONG, posLAT;
                                    posLONG = DataGlobals.failsafeParseFloat(link.from.get("LONG"), -111.15f);
                                    posLAT = DataGlobals.failsafeParseFloat(link.from.get("LAT"), -21.22f);
                                    // parse router location
                                    String sLocation = null;
                                    sLocation = link.from.get("CITY");
                                    routerFrom = new NetFlowDevice(posLONG, posLAT, sLocation);
                                    hNetFlowDevices.put(locationFrom, routerFrom);
                                } else if (objRouter instanceof NetFlowDevice) {
                                    routerFrom = (NetFlowDevice) objRouter;
                                }
                                hLinkAttrs.put("routerFrom", routerFrom);
                                routerFrom.fromLinks.add(link);
                                String locationTo = link.to.get("LONG") + "/" + link.to.get("LAT");
                                if ((objRouter = hNetFlowDevices.get(locationTo)) == null) {
                                    float posLONG, posLAT;
                                    posLONG = DataGlobals.failsafeParseFloat(link.to.get("LONG"), -111.15f);
                                    posLAT = DataGlobals.failsafeParseFloat(link.to.get("LAT"), -21.22f);
                                    // parse router location
                                    String sLocation = null;
                                    sLocation = link.to.get("CITY");
                                    routerTo = new NetFlowDevice(posLONG, posLAT, sLocation);
                                    hNetFlowDevices.put(locationTo, routerTo);
                                } else if (objRouter instanceof NetFlowDevice) {
                                    routerTo = (NetFlowDevice) objRouter;
                                }
                                ;
                                routerTo.toLinks.add(link);
                            }
                            ;
                        } else {
                            hLinkAttrs = (HashMap) obj;
                            routerFrom = (NetFlowDevice) hLinkAttrs.get("routerFrom");
                            routerTo = (NetFlowDevice) hLinkAttrs.get("routerTo");
                            // check to see if the coordinates for this netflow link have changed
                        }
                        ;
                        if ((routerFrom != null) && (routerTo != null)) {
                            // here i have the creator node, the source and
                            // destination routers, so sort links
                            // check the order hashmap
                            nOrder = 1;// if not found, it will be one
                            NetFlowDevice[] keySel = null;
                            for (Iterator it3 = hOrderNodes.keySet().iterator(); it3.hasNext();) {
                                NetFlowDevice[] key = (NetFlowDevice[]) it3.next();
                                if (routerFrom.equals(key[0]) && routerTo.equals(key[1])) {
                                    // another link(s) already exist between these two routers
                                    // increment order and break cycle
                                    nOrder = ((Integer) hOrderNodes.get(key)).intValue() + 1;
                                    keySel = key;
                                    break;
                                }
                            }
                            // check the previous order number, so that is different, then
                            // a new link to be generated
                            obj = hLinkAttrs.get("Order");
                            if (obj != null) {
                                int nOldOrder = ((Integer) obj).intValue();
                                if (nOldOrder != nOrder) {
                                    hLinkAttrs.remove("LinkID");
                                }
                            }
                            hLinkAttrs.put("Order", Integer.valueOf(nOrder));
                            if (keySel == null) {
                                keySel = new NetFlowDevice[] { routerFrom, routerTo };
                            }
                            // System.out.println("from "+routerFrom.toString()+" to "+routerTo+" link number="+nOrder);
                            hOrderNodes.put(keySel, Integer.valueOf(nOrder));
                        }
                    }
                    ;
                }
            }
            ;
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception checking new net flow links. Exception: " + ex.getMessage());
            ex.printStackTrace();
        }

        // ServiceID sidTo, sidFrom;
        // remove from renderer's wan map the ones that are no longer into mst vector
        try {
            // check if mst link has start and end node
            for (Iterator it = nfAttrs.keySet().iterator(); it.hasNext();) {
                link = (NFLink) it.next();
                hLinkAttrs = (HashMap) nfAttrs.get(link);
                nCreator = (rcNode) hLinkAttrs.get("creatorNode");
                if (nCreator == null) {
                    it.remove();
                    // remove from routers list also
                    removeLinkFromNetFlowDevices(link);
                }
                ;
                boolean contains = false;
                for (Enumeration en = nCreator.wconn.elements(); en.hasMoreElements();) {
                    if (link.equals(en.nextElement())) {
                        contains = true;
                        break;
                    }
                }
                if (nCreator.bHiddenOnMap || !contains || !((HashMap) graphicalAttrs[1]).keySet().contains(nCreator)) {
                    it.remove();
                    // remove from routers list also
                    removeLinkFromNetFlowDevices(link);
                }
                ;
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception checking net flow links removal: " + ex.getMessage());
            ex.printStackTrace();
        }

        // remove routers
        // not any more neccessary
        // but recompute traffic
        // recomputeTrafficForNetFlowDevices();

        // compute graphical information for each mst link taking in account the invalidate links flag
        // global variables needed for drawing nodes, get them now to avoid concurency on them later
        float globeRad = JoglPanel.globals.globeRadius;
        float globeVirtRad = JoglPanel.globals.globeVirtualRadius;

        float[] colorLink;
        int linkID;
        try {
            // boolean bFirstLink = true;
            for (Iterator it = nfAttrs.keySet().iterator(); it.hasNext();) {
                link = (NFLink) it.next();
                hLinkAttrs = (HashMap) nfAttrs.get(link);

                // get link order
                obj = hLinkAttrs.get("Order");
                if ((obj == null) || !(obj instanceof Integer)) {
                    continue;
                }
                int nOrder = ((Integer) obj).intValue();
                // recompute value
                Double value = ((Double) (link.data));
                if (value == null) {
                    continue;
                }
                hLinkAttrs.put("LinkValue", value);
                double val = value.doubleValue();

                // recompute color
                Color c = ((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.csNetFlow.getColor(val);
                if (c == null) {
                    continue;
                }
                obj = hLinkAttrs.get("LinkColor");
                if (obj == null) {
                    colorLink = new float[3];
                } else {
                    colorLink = (float[]) obj;
                }
                colorLink[0] = c.getRed() / 255f;
                colorLink[1] = c.getGreen() / 255f;
                colorLink[2] = c.getBlue() / 255f;
                if (obj == null) {
                    hLinkAttrs.put("LinkColor", colorLink);
                }

                int line_width = 1;
                /**
                 * compute line width
                 * max line widht is 3, minimum is 1
                 */
                Hashtable hNFOptions = (Hashtable) OptionsPanelValues.get("netflow");
                double Max = ((Double) hNFOptions.get("MaxValue")).doubleValue();
                double Min = ((Double) hNFOptions.get("MinValue")).doubleValue();
                if ((Max >= val) && (Max > Min) && (Max > 0) && (val >= Min)) {
                    line_width = (int) (3 - ((2 * (Max - val)) / (Max - Min)));
                    if (line_width < 1) {
                        line_width = 1;
                    }
                } else {
                    line_width = 1;
                }
                hLinkAttrs.put("LinkWidth", Integer.valueOf(line_width));

                /** ?????? */
                // compute number of steps
                // to Max corresponds MAX_SPEED_WAN_SEGMENT_STEPS and
                // to Min corresponds MIN_SPEED_WAN_SEGMENT_STEPS
                int total_steps = 0;
                total_steps = MIN_SPEED_WAN_SEGMENT_STEPS
                        + (Max > 0 ? (int) (((MAX_SPEED_WAN_SEGMENT_STEPS - MIN_SPEED_WAN_SEGMENT_STEPS) * val) / Max)
                                : 0);
                if (total_steps > MIN_SPEED_WAN_SEGMENT_STEPS) {
                    total_steps = MIN_SPEED_WAN_SEGMENT_STEPS;
                }
                if (total_steps < MAX_SPEED_WAN_SEGMENT_STEPS) {
                    total_steps = MAX_SPEED_WAN_SEGMENT_STEPS;
                }
                // System.out.println("total_steps="+total_steps);
                hLinkAttrs.put("WANSegmentSteps", Integer.valueOf(total_steps));

                // if neccessary, recompute display list
                obj = hLinkAttrs.get("LinkID");
                if ((obj != null) && bInvalidateLinks) {// for that, delete old id
                    gl.glDeleteLists(((Integer) obj).intValue(), 1);
                    obj = null;
                }
                ;
                if (obj == null) {
                    linkID = gl.glGenLists(1);
                    // System.out.println("linkID generated: "+linkID);
                    gl.glNewList(linkID, GL2.GL_COMPILE);
                    JoGLDirectedArc3D Darc3D;
                    Darc3D = new JoGLDirectedArc3D(link.fromLAT, link.fromLONG, link.toLAT, link.toLONG, globeRad, 10,
                            0.05, -globeRad + globeVirtRad, 0.1 + (0.1 * nOrder));
                    ArrayList alArcPoints = new ArrayList();
                    Darc3D.setPoints(alArcPoints);
                    Darc3D.drawArc(gl);
                    gl.glEndList();
                    hLinkAttrs.put("LinkID", Integer.valueOf(linkID));
                    hLinkAttrs.put("LinkPoints", alArcPoints);
                    hLinkAttrs.put("LinkArrowBase", Darc3D.vPointFrom);
                    VectorO vPointDir = VectorO.SubstractVector(Darc3D.vPointTo, Darc3D.vPointFrom);
                    vPointDir.Normalize();
                    VectorO VzAxis = new VectorO(0, 0, 1);
                    VectorO vRotAxis = VzAxis.CrossProduct(vPointDir);
                    // rotate z to Vdir around vectorial product with dot product
                    float angleRotation = (float) ((Math.acos(VzAxis.DotProduct(vPointDir)) * 180) / Math.PI);
                    hLinkAttrs.put("LinkArrowRotationAngle", new Float(angleRotation));
                    hLinkAttrs.put("LinkArrowRotationAxis", vRotAxis);
                } else {
                    linkID = ((Integer) obj).intValue();
                }

                // draw the link
                gl.glLineWidth(line_width);
                if ((link.name != null) && (link.name.indexOf("NetLink") >= 0)) {
                    gl.glColor3fv(Color.GREEN.getColorComponents(null), 0);
                } else {
                    gl.glColor3fv(colorLink, 0);
                }
                gl.glCallList(linkID);
                gl.glLineWidth(1f);

                if ((link.name != null) && (link.name.indexOf("NetLink") >= 0)) {
                    gl.glColor3fv(Color.GREEN.getColorComponents(null), 0);
                } else {
                    gl.glColor3fv(colorLink, 0);
                }
                /** ???? */

                // draw arrow on link
                if (conID > 0) {// if there is an object to be drawn
                    gl.glPushMatrix();
                    try {
                        VectorO vLinkArrowBase = (VectorO) hLinkAttrs.get("LinkArrowBase");
                        float fLinkArrowRotationAngle = ((Float) hLinkAttrs.get("LinkArrowRotationAngle")).floatValue();
                        VectorO vLinkArrowRotationAxis = (VectorO) hLinkAttrs.get("LinkArrowRotationAxis");

                        // operations are put in inverse order because last is first executed in opengl
                        // 3. position object cu correct coordinates
                        // System.out.println("link arrow base: "+vLinkArrowBase);
                        gl.glTranslatef(vLinkArrowBase.getX(), vLinkArrowBase.getY(), vLinkArrowBase.getZ());
                        // 2. operation: rotate from (0,0,1) to Vdir
                        // System.out.println("link arrow rotation axis: "+vLinkArrowRotationAxis);
                        gl.glRotatef(fLinkArrowRotationAngle, vLinkArrowRotationAxis.getX(),
                                vLinkArrowRotationAxis.getY(), vLinkArrowRotationAxis.getZ());
                        // 1. operation: scale to radius dimmensions:
                        gl.glScalef(radius / 2, radius / 2, radius / 2);
                        // use already constructed cap as display list
                        // draw it at (0,0,0) in self reference system
                        gl.glCallList(conID);
                    } catch (Exception ex) {
                        logger.log(Level.INFO, "Exception drawing arrow for link: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                    gl.glPopMatrix();
                }
                ;
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception computing graphical attributes for wan links: " + ex.getMessage());
            ex.printStackTrace();
        }

        drawNetFlowDevices(gl, radius * .9f);

        /** should it be??? */
        if (((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbAnimateWAN.isSelected()) {
            drawPackages4NF(gl, radius);
        }
    }

    /**
     * computes os links
     *
     * @param gl
     * @param graphicalAttrs
     * @param bInvalidateLinks
     */
    private void computeOS(GL2 gl, Object[] graphicalAttrs, boolean bInvalidateLinks, float radius) {
        // System.out.println("recompute os links: "+bInvalidateLinks+" radius="+radius);
        Object obj;
        OSLink link;
        HashMap hLinkAttrs;
        rcNode node;
        HashMap hOrderNodes = new HashMap();
        try {
            int nOrder;
            for (Iterator it = ((HashMap) graphicalAttrs[1]).keySet().iterator(); it.hasNext();) {
                node = (rcNode) it.next();
                if (!node.bHiddenOnMap) {
                    // for each os link, check to see if already put
                    hOrderNodes.clear();
                    for (Iterator it2 = node.wconn.values().iterator(); it2.hasNext();) {
                        Object objLink = it2.next();
                        if (!(objLink instanceof OSLink)) {
                            continue;
                        }
                        link = (OSLink) objLink;
                        if (link.checkFlag(OSLink.OSLINK_FLAG_FAKE_NODE)) {
                            continue;
                        }
                        rcNode source = null, dest = null;

                        if (link.szSourcePort != null) { // type one sw
                            if (link.szSourcePort.type.shortValue() == OSPort.OUTPUT_PORT) {
                                source = link.rcSource;
                                dest = link.rcDest;
                            } else {
                                source = link.rcDest;
                                dest = link.rcSource;
                            }
                        } else if (link.szSourceNewPort != null) { // type new sw
                            if (link.szSourceNewPort.type == OSwPort.OUTPUT_PORT) {
                                source = link.rcSource;
                                dest = link.rcDest;
                            } else {
                                source = link.rcDest;
                                dest = link.rcSource;
                            }
                        }

                        obj = osAttrs.get(link);
                        if (obj == null) {
                            hLinkAttrs = new HashMap();
                            hLinkAttrs.put("fromNode", source);
                            hLinkAttrs.put("toNode", dest);
                            hLinkAttrs.put("fromLAT", source.LAT);
                            hLinkAttrs.put("fromLONG", source.LONG);
                            hLinkAttrs.put("toLAT", dest.LAT);
                            hLinkAttrs.put("toLONG", dest.LONG);
                            osAttrs.put(link, hLinkAttrs);
                        }
                        ;
                        // check the order hashmap
                        Object objOrder;
                        objOrder = hOrderNodes.get(dest);
                        if (objOrder == null) {
                            nOrder = 1;
                        } else {
                            nOrder = ((Integer) objOrder).intValue() + 1;
                        }
                        ((HashMap) osAttrs.get(link)).put("Order", Integer.valueOf(nOrder));
                        hOrderNodes.put(dest, Integer.valueOf(nOrder));
                    }
                    ;
                }
            }
            ;
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception checking new os links. Exception: " + ex.getMessage());
            ex.printStackTrace();
        }

        // ServiceID sidTo, sidFrom;
        // remove from renderer's os map the ones that are no longer into mst vector
        try {
            // check if os link has start and end node
            for (Iterator it = osAttrs.keySet().iterator(); it.hasNext();) {
                link = (OSLink) it.next();
                hLinkAttrs = (HashMap) osAttrs.get(link);
                rcNode nFrom = (rcNode) hLinkAttrs.get("fromNode");// link.rcSource;
                if (nFrom == null) {
                    System.out.println("no source for " + link);
                    it.remove();
                    continue;
                }
                rcNode nTo = (rcNode) hLinkAttrs.get("toNode");
                if (nTo == null) {
                    System.out.println("no destination for " + link);
                    it.remove();
                    continue;
                }
                boolean contains = false;
                if (link.rcSource != null) {
                    for (Enumeration en = link.rcSource.wconn.elements(); en.hasMoreElements();) {
                        if (link.equals(en.nextElement())) {
                            contains = true;
                            break;
                        }
                    }
                }
                if (nFrom.bHiddenOnMap || !contains || nTo.bHiddenOnMap) {
                    System.out.println("source hidden or not contained or destination hidden for " + link);
                    it.remove();
                }
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception checking os links removal: " + ex.getMessage());
            ex.printStackTrace();
        }

        // compute graphical information for each mst link taking in account the invalidate links flag
        // global variables needed for drawing nodes, get them now to avoid concurency on them later
        float globeRad = JoglPanel.globals.globeRadius;
        float globeVirtRad = JoglPanel.globals.globeVirtualRadius;

        float[] colorLink;
        int linkID;
        try {
            // System.out.println("list of optical links: "+osAttrs.keySet());
            // check if a node's link from wconn was eliminated
            for (Iterator it = osAttrs.keySet().iterator(); it.hasNext();) {
                link = (OSLink) it.next();
                hLinkAttrs = (HashMap) osAttrs.get(link);

                // get link order
                obj = hLinkAttrs.get("Order");
                if ((obj == null) || !(obj instanceof Integer)) {
                    continue;
                }
                int nOrder = ((Integer) obj).intValue();
                // recompute color
                Color c = link.getStateColor();
                if (c == null) {
                    continue;
                }
                obj = hLinkAttrs.get("LinkColor");
                if (obj == null) {
                    colorLink = new float[3];
                } else {
                    colorLink = (float[]) obj;
                }
                colorLink[0] = c.getRed() / 255f;
                colorLink[1] = c.getGreen() / 255f;
                colorLink[2] = c.getBlue() / 255f;
                if (obj == null) {
                    hLinkAttrs.put("LinkColor", colorLink);
                }

                // check coordinates for source and destination nodes to see if any changes, and, if so
                // update values for link and recompute it
                float srcLat, srcLong, dstLat, dstLong;
                boolean bInvalidateThisLink = false;
                srcLat = DataGlobals.failsafeParseFloat((String) hLinkAttrs.get("fromLAT"), -21.22f);
                srcLong = DataGlobals.failsafeParseFloat((String) hLinkAttrs.get("fromLONG"), -111.15f);
                dstLat = DataGlobals.failsafeParseFloat((String) hLinkAttrs.get("toLAT"), -21.22f);
                dstLong = DataGlobals.failsafeParseFloat((String) hLinkAttrs.get("toLONG"), -111.15f);
                rcNode nFrom = (rcNode) hLinkAttrs.get("fromNode");// link.rcSource;
                rcNode nTo = (rcNode) hLinkAttrs.get("toNode");
                float nfrom_lat = DataGlobals.failsafeParseFloat(nFrom.LAT, -21.22f);
                float nfrom_long = DataGlobals.failsafeParseFloat(nFrom.LONG, -111.15f);
                float nto_lat = DataGlobals.failsafeParseFloat(nTo.LAT, -21.22f);
                float nto_long = DataGlobals.failsafeParseFloat(nTo.LONG, -111.15f);
                if ((srcLat != nfrom_lat) || (srcLong != nfrom_long) || (dstLat != nto_lat) || (dstLong != nto_long)) {// link.fromLAT
                    // ||
                    // srcLong!=link.fromLONG
                    // ||
                    // dstLat!=link.toLAT
                    // ||
                    // dstLong!=link.toLONG
                    // ) {
                    // update link's start and end coordinates
                    hLinkAttrs.put("fromLAT", nFrom.LAT);// link.rcSource.LAT);
                    hLinkAttrs.put("fromLONG", nFrom.LONG);// link.rcSource.LONG);
                    hLinkAttrs.put("toLAT", nTo.LAT);// link.rcDest.LAT);
                    hLinkAttrs.put("toLONG", nTo.LONG);// link.rcDest.LONG);
                    srcLat = nfrom_lat;
                    srcLong = nfrom_long;
                    dstLat = nto_lat;
                    dstLong = nto_long;
                    // invalidate this link
                    bInvalidateThisLink = true;
                }

                // if neccessary, recompute display list
                obj = hLinkAttrs.get("LinkID");
                if ((obj != null) && (bInvalidateLinks || bInvalidateThisLink)) {// for that, delete old id
                    gl.glDeleteLists(((Integer) obj).intValue(), 1);
                    obj = null;
                }
                ;
                if (obj == null) {
                    linkID = gl.glGenLists(1);
                    // System.out.println("linkID generated: "+linkID);
                    gl.glNewList(linkID, GL2.GL_COMPILE);
                    JoGLDirectedArc3D Darc3D;
                    Darc3D = new JoGLDirectedArc3D(srcLat, srcLong, dstLat, dstLong, globeRad, 10, 0.05, -globeRad
                            + globeVirtRad, 0.1 + (0.1 * nOrder/*
                                                               * 0.05
                                                               * *
                                                               * nOrder
                                                               * ,
                                                               * -
                                                               * globeRad
                                                               * +
                                                               * globeVirtRad
                                                               * ,
                                                               * 0.2
                                                               * *
                                                               * nOrder
                                                               */));
                    Darc3D.drawArc(gl);
                    gl.glEndList();
                    hLinkAttrs.put("LinkID", Integer.valueOf(linkID));
                    hLinkAttrs.put("LinkArrowBase", Darc3D.vPointFrom);
                    // System.out.println("point from: "+Darc3D.vPointFrom);
                    // System.out.println("point to: "+Darc3D.vPointTo);
                    VectorO vPointDir = VectorO.SubstractVector(Darc3D.vPointTo, Darc3D.vPointFrom);
                    vPointDir.Normalize();
                    VectorO VzAxis = new VectorO(0, 0, 1);
                    VectorO vRotAxis = VzAxis.CrossProduct(vPointDir);
                    // rotate z to Vdir around vectorial product with dot product
                    float angleRotation = (float) ((Math.acos(VzAxis.DotProduct(vPointDir)) * 180) / Math.PI);
                    hLinkAttrs.put("LinkArrowRotationAngle", new Float(angleRotation));
                    hLinkAttrs.put("LinkArrowRotationAxis", vRotAxis);
                } else {
                    linkID = ((Integer) obj).intValue();
                }

                // draw the link
                // colorLink = (float[])hLinkAttrs.get("LinkColor");
                gl.glColor3fv(colorLink, 0);
                gl.glCallList(linkID);

                // draw arrow on link
                if (conID > 0) {// if there is an object to be drawn
                    gl.glPushMatrix();
                    try {
                        VectorO vLinkArrowBase = (VectorO) hLinkAttrs.get("LinkArrowBase");
                        float fLinkArrowRotationAngle = ((Float) hLinkAttrs.get("LinkArrowRotationAngle")).floatValue();
                        VectorO vLinkArrowRotationAxis = (VectorO) hLinkAttrs.get("LinkArrowRotationAxis");

                        // operations are put in inverse order because last is first executed in opengl
                        // 3. position object cu correct coordinates
                        // System.out.println("link arrow base: "+vLinkArrowBase);
                        gl.glTranslatef(vLinkArrowBase.getX(), vLinkArrowBase.getY(), vLinkArrowBase.getZ());
                        // 2. operation: rotate from (0,0,1) to Vdir
                        // System.out.println("link arrow rotation axis: "+vLinkArrowRotationAxis);
                        gl.glRotatef(fLinkArrowRotationAngle, vLinkArrowRotationAxis.getX(),
                                vLinkArrowRotationAxis.getY(), vLinkArrowRotationAxis.getZ());
                        // 1. operation: scale to radius dimmensions:
                        gl.glScalef(radius / 2, radius / 2, radius / 2);
                        // use already constructed cap as display list
                        // draw it at (0,0,0) in self reference system
                        gl.glCallList(conID);
                    } catch (Exception ex) {
                        logger.log(Level.INFO, "Exception drawing arrow for link: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                    gl.glPopMatrix();
                }
                ;
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception computing graphical attributes for os links: " + ex.getMessage());
            ex.printStackTrace();
        }

    }

    private void drawWAN(GL2 gl, float radius) {
        Object obj;
        ILink link;
        HashMap hLinkAttrs;
        float[] colorLink;
        int linkID;
        try {
            // check if a node's link from wconn was eliminated
            for (Iterator it = wanAttrs.keySet().iterator(); it.hasNext();) {
                Object objLink = it.next();
                if (!(objLink instanceof ILink)) {
                    continue;
                }
                link = (ILink) objLink;
                hLinkAttrs = (HashMap) wanAttrs.get(link);

                // draw the link
                obj = hLinkAttrs.get("LinkValue");
                if (obj == null) {
                    continue;// skip to next one
                }
                // double val = ((Double)obj).doubleValue();
                // double line_width = 1;
                obj = hLinkAttrs.get("LinkWidth");
                // System.out.println("link width: "+obj);
                if (obj == null) {
                    continue;// skip to next one
                }
                double line_width = ((Integer) obj).intValue();
                /**
                 * max line widht is 3, minimum is 1
                 */
                // try {
                // Hashtable hWANOptions = (Hashtable)OptionsPanelValues.get("wan");
                // double Max = ((Double)hWANOptions.get( "MaxValue")).doubleValue();
                // double Min = ((Double)hWANOptions.get( "MinValue")).doubleValue();
                // if ( Max >= val && Max > Min && Max > 0 && val >= Min )
                // line_width = 3-2*( Max-val )/( Max-Min );
                // else
                // line_width = 1;
                // // System.out.println("line width="+line_width);
                // } catch(Exception ex) {
                // line_width=1;
                // }

                obj = hLinkAttrs.get("LinkColor");
                if (obj == null) {
                    continue;// skip to next one
                }
                colorLink = (float[]) obj;
                obj = hLinkAttrs.get("LinkID");
                if (obj == null) {
                    continue;// skip to next one
                }
                linkID = ((Integer) obj).intValue();

                // draw the link
                gl.glLineWidth((float) line_width);
                if ((link.name != null) && (link.name.indexOf("NetLink") >= 0)) {
                    gl.glColor3fv(Color.GREEN.getColorComponents(null), 0);
                } else {
                    gl.glColor3fv(colorLink, 0);
                }
                gl.glCallList(linkID);
                gl.glLineWidth(1f);

                // draw arrow on link
                if (conID > 0) {// if there is an object to be drawn
                    gl.glPushMatrix();
                    try {
                        VectorO vLinkArrowBase = (VectorO) hLinkAttrs.get("LinkArrowBase");
                        float fLinkArrowRotationAngle = ((Float) hLinkAttrs.get("LinkArrowRotationAngle")).floatValue();
                        VectorO vLinkArrowRotationAxis = (VectorO) hLinkAttrs.get("LinkArrowRotationAxis");

                        // operations are put in inverse order because last is first executed in opengl
                        // 3. position object cu correct coordinates
                        gl.glTranslatef(vLinkArrowBase.getX(), vLinkArrowBase.getY(), vLinkArrowBase.getZ());
                        // 2. operation: rotate from (0,0,1) to Vdir
                        gl.glRotatef(fLinkArrowRotationAngle, vLinkArrowRotationAxis.getX(),
                                vLinkArrowRotationAxis.getY(), vLinkArrowRotationAxis.getZ());
                        // 1. operation: scale to radius dimmensions:
                        gl.glScalef(radius / 2, radius / 2, radius / 2);
                        // use already constructed cap as display list
                        // draw it at (0,0,0) in self reference system
                        gl.glCallList(conID);
                    } catch (Exception ex) {
                        logger.log(Level.INFO, "Exception drawing arrow for link: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                    gl.glPopMatrix();
                }
                ;
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception drawing graphical attributes for wan links: " + ex.getMessage());
            ex.printStackTrace();
        }

        drawRouters(gl, radius * .9f);

        if (((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbAnimateWAN.isSelected()) {
            drawPackages(gl, radius);
        }
    }

    /**
     * draw netflow links
     * Sep 13, 2005 6:57:19 PM - mluc<br>
     *
     * @param gl
     * @param radius
     */
    private void drawNF(GL2 gl, float radius) {
        Object obj;
        NFLink link;
        HashMap hLinkAttrs;
        float[] colorLink;
        int linkID;
        try {
            // check if a node's link from wconn was eliminated
            for (Iterator it = nfAttrs.keySet().iterator(); it.hasNext();) {
                Object objLink = it.next();
                if (!(objLink instanceof NFLink)) {
                    continue;
                }
                link = (NFLink) objLink;
                hLinkAttrs = (HashMap) nfAttrs.get(link);

                // draw the link
                obj = hLinkAttrs.get("LinkValue");
                if (obj == null) {
                    continue;// skip to next one
                }
                obj = hLinkAttrs.get("LinkWidth");
                if (obj == null) {
                    continue;// skip to next one
                }
                double line_width = ((Integer) obj).intValue();
                /** max line widht is 3, minimum is 1 */

                obj = hLinkAttrs.get("LinkColor");
                if (obj == null) {
                    continue;// skip to next one
                }
                colorLink = (float[]) obj;
                obj = hLinkAttrs.get("LinkID");
                if (obj == null) {
                    continue;// skip to next one
                }
                linkID = ((Integer) obj).intValue();

                // draw the link
                gl.glLineWidth((float) line_width);
                if ((link.name != null) && (link.name.indexOf("NetLink") >= 0)) {
                    gl.glColor3fv(Color.GREEN.getColorComponents(null), 0);
                } else {
                    gl.glColor3fv(colorLink, 0);
                }
                gl.glCallList(linkID);
                gl.glLineWidth(1f);

                // draw arrow on link
                if (conID > 0) {// if there is an object to be drawn
                    gl.glPushMatrix();
                    try {
                        VectorO vLinkArrowBase = (VectorO) hLinkAttrs.get("LinkArrowBase");
                        float fLinkArrowRotationAngle = ((Float) hLinkAttrs.get("LinkArrowRotationAngle")).floatValue();
                        VectorO vLinkArrowRotationAxis = (VectorO) hLinkAttrs.get("LinkArrowRotationAxis");

                        // operations are put in inverse order because last is first executed in opengl
                        // 3. position object cu correct coordinates
                        gl.glTranslatef(vLinkArrowBase.getX(), vLinkArrowBase.getY(), vLinkArrowBase.getZ());
                        // 2. operation: rotate from (0,0,1) to Vdir
                        gl.glRotatef(fLinkArrowRotationAngle, vLinkArrowRotationAxis.getX(),
                                vLinkArrowRotationAxis.getY(), vLinkArrowRotationAxis.getZ());
                        // 1. operation: scale to radius dimmensions:
                        gl.glScalef(radius / 2, radius / 2, radius / 2);
                        // use already constructed cap as display list
                        // draw it at (0,0,0) in self reference system
                        gl.glCallList(conID);
                    } catch (Exception ex) {
                        logger.log(Level.INFO, "Exception drawing arrow for link: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                    gl.glPopMatrix();
                }
                ;
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception drawing graphical attributes for net flow links: " + ex.getMessage());
            ex.printStackTrace();
        }

        drawNetFlowDevices(gl, radius * .9f);

        if (((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbAnimateWAN.isSelected()) {
            drawPackages4NF(gl, radius);
        }
    }

    /**
     * draws packages for each link
     *
     * @param gl
     * @param radius
     */
    private void drawPackages(GL2 gl, float radius) {
        if (!bAnimationRecomputed) {
            return;
        }
        Object obj;
        ILink link;
        HashMap hLinkAttrs;
        if (packageID == 0) {
            return;
        }
        try {
            // for each link
            for (Iterator it = wanAttrs.keySet().iterator(); it.hasNext();) {
                Object objLink = it.next();
                if (!(objLink instanceof ILink)) {
                    continue;
                }
                link = (ILink) objLink;
                hLinkAttrs = (HashMap) wanAttrs.get(link);
                // get packages vector
                Vector vPackages = null;
                obj = hLinkAttrs.get("PackagesVector");
                if ((obj != null) && (obj instanceof Vector)) {
                    vPackages = (Vector) obj;
                } else {
                    continue;
                }
                // if one available, draw packages
                WANPackage aPackage;
                for (int i = 0; i < vPackages.size(); i++) {
                    aPackage = (WANPackage) vPackages.get(i);
                    gl.glPushMatrix();
                    try {
                        // VectorO vLinkArrowBase = (VectorO)hLinkAttrs.get("LinkArrowBase");
                        // float fLinkArrowRotationAngle = ((Float)hLinkAttrs.get(
                        // "LinkArrowRotationAngle")).floatValue();
                        // VectorO vLinkArrowRotationAxis = (VectorO)hLinkAttrs.get("LinkArrowRotationAxis");

                        // operations are put in inverse order because last is first executed in opengl
                        // 3. position object cu correct coordinates
                        gl.glTranslatef(aPackage.vPosition.getX(), aPackage.vPosition.getY(), aPackage.vPosition.getZ());
                        // 2. operation: rotate from (0,0,1) to Vdir
                        // gl.glRotatef( aPackage.fRotAngle, aPackage.vRotAxis.getX(), aPackage.vRotAxis.getY(),
                        // aPackage.vRotAxis.getZ());
                        // gl.glRotatef( aPackage.fRotAngle2, 0, 0, 1);
                        // 1. operation: scale to radius dimmensions:
                        gl.glScalef(radius * aPackage.fraction_radius, radius * aPackage.fraction_radius, radius
                                * aPackage.fraction_radius);
                        // use already constructed cap as display list
                        // draw it at (0,0,0) in self reference system
                        gl.glCallList(packageID);
                    } catch (Exception ex) {
                        logger.log(Level.INFO, "Exception drawing package for link: " + aPackage.sDescription
                                + ". Package may be during initialisation. Ignore it.");
                        ex.printStackTrace();
                    }
                    gl.glPopMatrix();
                }
                ;
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception drawing graphical attributes for wan links: " + ex.getMessage());
            ex.printStackTrace();
        }

    }

    /**
     * draws packages for each netflow link
     *
     * @param gl
     * @param radius
     */
    private void drawPackages4NF(GL2 gl, float radius) {
        if (!bAnimationRecomputed) {
            return;
        }
        Object obj;
        NFLink link;
        HashMap hLinkAttrs;
        if (packageNFID == 0) {
            return;
        }
        float[] colorLink;
        try {
            // for each link
            for (Iterator it = nfAttrs.keySet().iterator(); it.hasNext();) {
                Object objLink = it.next();
                if (!(objLink instanceof NFLink)) {
                    continue;
                }
                link = (NFLink) objLink;
                hLinkAttrs = (HashMap) nfAttrs.get(link);
                obj = hLinkAttrs.get("LinkColor");
                if (obj == null) {
                    continue;// skip to next one
                }
                colorLink = (float[]) obj;
                if ((link.name != null) && (link.name.indexOf("NetLink") >= 0)) {
                    gl.glColor3fv(Color.GREEN.getColorComponents(null), 0);

                    continue;
                } else {
                    gl.glColor3fv(colorLink, 0);
                }
                // get packages vector
                Vector vPackages = null;
                obj = hLinkAttrs.get("PackagesVector");
                if ((obj != null) && (obj instanceof Vector)) {
                    vPackages = (Vector) obj;
                } else {
                    continue;
                }
                // if one available, draw packages
                WANPackage aPackage;
                for (int i = 0; i < vPackages.size(); i++) {
                    aPackage = (WANPackage) vPackages.get(i);
                    gl.glPushMatrix();
                    try {
                        // VectorO vLinkArrowBase = (VectorO)hLinkAttrs.get("LinkArrowBase");
                        // float fLinkArrowRotationAngle = ((Float)hLinkAttrs.get(
                        // "LinkArrowRotationAngle")).floatValue();
                        // VectorO vLinkArrowRotationAxis = (VectorO)hLinkAttrs.get("LinkArrowRotationAxis");

                        // operations are put in inverse order because last is first executed in opengl
                        // 3. position object cu correct coordinates
                        gl.glTranslatef(aPackage.vPosition.getX(), aPackage.vPosition.getY(), aPackage.vPosition.getZ());
                        // 2. operation: rotate from (0,0,1) to Vdir
                        // gl.glRotatef( aPackage.fRotAngle, aPackage.vRotAxis.getX(), aPackage.vRotAxis.getY(),
                        // aPackage.vRotAxis.getZ());
                        // gl.glRotatef( aPackage.fRotAngle2, 0, 0, 1);
                        // 1. operation: scale to radius dimmensions:
                        gl.glScalef(radius * aPackage.fraction_radius, radius * aPackage.fraction_radius, radius
                                * aPackage.fraction_radius);
                        // use already constructed cap as display list
                        // draw it at (0,0,0) in self reference system
                        gl.glCallList(packageNFID);
                    } catch (Exception ex) {
                        logger.log(Level.INFO, "Exception drawing package for link: " + aPackage.sDescription
                                + ". Package may be during initialisation. Ignore it.");
                        ex.printStackTrace();
                    }
                    gl.glPopMatrix();
                }
                ;
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception drawing graphical attributes for nf links: " + ex.getMessage());
            ex.printStackTrace();
        }

    }

    private void drawOS(GL2 gl, float radius) {
        Object obj;
        OSLink link;
        HashMap hLinkAttrs;
        float[] colorLink;
        int linkID;
        try {
            for (Iterator it = osAttrs.keySet().iterator(); it.hasNext();) {
                Object objLink = it.next();
                if (!(objLink instanceof OSLink)) {
                    continue;
                }
                link = (OSLink) objLink;
                hLinkAttrs = (HashMap) osAttrs.get(link);

                // draw the link
                obj = hLinkAttrs.get("LinkColor");
                if (obj == null) {
                    continue;// skip to next one
                }
                colorLink = (float[]) obj;
                obj = hLinkAttrs.get("LinkID");
                if (obj == null) {
                    continue;// skip to next one
                }
                linkID = ((Integer) obj).intValue();

                // draw the link
                gl.glColor3fv(colorLink, 0);
                gl.glCallList(linkID);

                // draw arrow on link
                if (conID > 0) {// if there is an object to be drawn
                    gl.glPushMatrix();
                    try {
                        VectorO vLinkArrowBase = (VectorO) hLinkAttrs.get("LinkArrowBase");
                        float fLinkArrowRotationAngle = ((Float) hLinkAttrs.get("LinkArrowRotationAngle")).floatValue();
                        VectorO vLinkArrowRotationAxis = (VectorO) hLinkAttrs.get("LinkArrowRotationAxis");

                        // operations are put in inverse order because last is first executed in opengl
                        // 3. position object cu correct coordinates
                        gl.glTranslatef(vLinkArrowBase.getX(), vLinkArrowBase.getY(), vLinkArrowBase.getZ());
                        // 2. operation: rotate from (0,0,1) to Vdir
                        gl.glRotatef(fLinkArrowRotationAngle, vLinkArrowRotationAxis.getX(),
                                vLinkArrowRotationAxis.getY(), vLinkArrowRotationAxis.getZ());
                        // 1. operation: scale to radius dimmensions:
                        gl.glScalef(radius / 2, radius / 2, radius / 2);
                        // use already constructed cap as display list
                        // draw it at (0,0,0) in self reference system
                        gl.glCallList(conID);
                    } catch (Exception ex) {
                        logger.log(Level.INFO, "Exception drawing arrow for link: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                    gl.glPopMatrix();
                }
                ;
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception drawing graphical attributes for os links: " + ex.getMessage());
            ex.printStackTrace();
        }

    }

    private void drawRouters(GL2 gl, float radius) {
        // int rID = getRouterID( gl);
        if (routerID <= 0) {
            // System.out.println("Error on drawing router");
            return;
        }
        ;
        // float[] vectors;
        float[] coords = null;
        // System.out.println("number of routers: "+hRouters.size());
        // check if a node's link from wconn was eliminated
        for (Iterator it = hRouters.values().iterator(); it.hasNext();) {
            gl.glPushMatrix();
            try {
                WANRouter router = (WANRouter) it.next();
                coords = Globals.point2Dto3D(router.posLAT, router.posLONG, coords);
                if (coords == null) {
                    continue;
                }

                VectorO VdirInit;
                if (JoglPanel.globals.globeRadius != -1) {
                    float adaosZ = 0f;
                    adaosZ = JoglPanel.globals.globeRadius - JoglPanel.globals.globeVirtualRadius;
                    VdirInit = new VectorO(coords[0], coords[1], coords[2] + adaosZ);
                    VdirInit.Normalize();
                } else {
                    VdirInit = new VectorO(0, 0, 1);
                }
                // operations are put in inverse order because last is first executed in opengl
                // 3. position object cu correct coordinates
                gl.glTranslatef(coords[0], coords[1], coords[2]);
                // 2. operation: rotate from (0,0,1) to Vdir
                // for that, first compute rotation axis as cross product between z and Vdir
                VectorO VzAxis = new VectorO(0, 0, 1);
                VectorO VRotAxis = VzAxis.CrossProduct(VdirInit);
                // rotate z to Vdir around vectorial product with dot product
                gl.glRotatef((float) ((Math.acos(VzAxis.DotProduct(VdirInit)) * 180) / Math.PI), VRotAxis.getX(),
                        VRotAxis.getY(), VRotAxis.getZ());
                // 1. operation: scale to radius dimmensions:
                gl.glScalef(radius, radius, radius);
                // use already constructed cap as display list
                // draw it at (0,0,0) in self reference system
                gl.glCallList(routerID);
                // gl.glCallList( conID);
            } catch (Exception ex) {
                logger.log(Level.INFO, "Exception drawing routers for wan links: " + ex.getMessage());
                ex.printStackTrace();
            }
            gl.glPopMatrix();
        }
    }

    private void listRouters() {
        try {
            System.out.println("Dumping wan routers list:");
            for (Iterator it = hRouters.values().iterator(); it.hasNext();) {
                WANRouter router = (WANRouter) it.next();
                System.out.println(router.toString());
            }
            System.out.println("End wan routers list.");
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception dumping wan routers list " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void drawNetFlowDevices(GL2 gl, float radius) {
        if (nfDeviceID <= 0) {
            return;
        }
        ;
        float[] coords = null;
        // System.out.println("number of routers: "+hRouters.size());
        // check if a node's link from wconn was eliminated
        for (Iterator it = hNetFlowDevices.values().iterator(); it.hasNext();) {
            gl.glPushMatrix();
            try {
                NetFlowDevice router = (NetFlowDevice) it.next();
                coords = Globals.point2Dto3D(router.posLAT, router.posLONG, coords);
                if (coords == null) {
                    continue;
                }

                VectorO VdirInit;
                if (JoglPanel.globals.globeRadius != -1) {
                    float adaosZ = 0f;
                    adaosZ = JoglPanel.globals.globeRadius - JoglPanel.globals.globeVirtualRadius;
                    VdirInit = new VectorO(coords[0], coords[1], coords[2] + adaosZ);
                    VdirInit.Normalize();
                } else {
                    VdirInit = new VectorO(0, 0, 1);
                }
                // operations are put in inverse order because last is first executed in opengl
                // 3. position object cu correct coordinates
                gl.glTranslatef(coords[0], coords[1], coords[2]);
                // 2. operation: rotate from (0,0,1) to Vdir
                // for that, first compute rotation axis as cross product between z and Vdir
                VectorO VzAxis = new VectorO(0, 0, 1);
                VectorO VRotAxis = VzAxis.CrossProduct(VdirInit);
                // rotate z to Vdir around vectorial product with dot product
                gl.glRotatef((float) ((Math.acos(VzAxis.DotProduct(VdirInit)) * 180) / Math.PI), VRotAxis.getX(),
                        VRotAxis.getY(), VRotAxis.getZ());
                // 1. operation: scale to radius dimmensions:
                gl.glScalef(radius, radius, radius);
                // use already constructed cap as display list
                // draw it at (0,0,0) in self reference system
                if (router.bIsRouter) {
                    gl.glCallList(nfRouterID);
                } else {
                    gl.glCallList(nfDeviceID);
                    // gl.glCallList( conID);
                }
            } catch (Exception ex) {
                logger.log(Level.INFO, "Exception drawing devices for netflow links: " + ex.getMessage());
                ex.printStackTrace();
            }
            gl.glPopMatrix();
        }
    }

    @Override
    public Hashtable getLinks(rcNode node) {
        if (node != null) {
            return node.conn;
        }
        return null;
    }

    @Override
    public boolean isValidLink(Object link) {
        if (link == null) {
            return false;
        }
        if (((link instanceof ILink) && ((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowPing.isSelected())
                || ((link instanceof OSLink) && ((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowOS
                        .isSelected())) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isDeadLink(Object link, HashMap hLinkAttrs, HashMap localNodes) {
        rcNode nodeFrom, nodeTo;
        nodeFrom = (rcNode) hLinkAttrs.get("fromNode");
        nodeTo = (rcNode) hLinkAttrs.get("toNode");
        if ((nodeFrom == null) || (nodeTo == null) || !localNodes.containsKey(nodeFrom)
                || !localNodes.containsKey(nodeTo) || nodeFrom.bHiddenOnMap || nodeTo.bHiddenOnMap
                || !getLinks(nodeFrom).containsKey(nodeTo) || !isValidLink(link)) {
            return true;
        }
        return false;
    }

    /**
     * NODES FUNCTIONS
     */

    /**
     * specific function for on top renderer
     */
    private VectorO recomputeVectors(HashMap gaNodes, HashMap htComputedNodes, rcNode node, VectorO[] vectors,
            float radius) {
        rcNode checked_node;
        boolean bNodeTreated = false;
        Map.Entry entry;
        Object obj;
        VectorO[] cnVectors = null;// checked node vectors
        VectorO vNewPos = vectors[1];
        // for each node, see if falls on top of another one, already cheched
        // and the draw it accordingly
        // for that I'll use an Hashtable to remember the checked nodes and
        // their properties: the postion and direction, the next level to put
        // a node that falls on current one, etc
        // so, hastable contains an array of two objects: the rcNode and its properties
        // one is the key, and one the value
        // there is only one property that must be memorized: the number of nodes already drawn on top of first one
        // including this one, because this can be multiplied with the radius to obtain an height
        bNodeTreated = false;
        // check with each already treated nodes
        for (Iterator it = htComputedNodes.entrySet().iterator(); it.hasNext();) {
            entry = (Map.Entry) it.next();
            checked_node = (rcNode) entry.getKey();
            int levels = ((Integer) entry.getValue()).intValue();
            obj = gaNodes.get(checked_node);
            if (obj != null) {// this node has its attributes computed, so draw it
                cnVectors = (VectorO[]) ((HashMap) obj).get("PositioningVectors");
                if (cnVectors == null) {
                    continue;
                }
            }
            ;
            if ((float) vectors[1].distanceTo(cnVectors[1]) < (2 * radius)) {
                // distance from one node to another one is smaller than radius, so draw one on top of other
                vNewPos = new VectorO(cnVectors[1]);
                VectorO vLevel = new VectorO(cnVectors[0]);
                vLevel.MultiplyScalar(2 * levels * radius);
                vNewPos.AddVector(vLevel);
                // compute some view values
                // and draw the node
                // vectors[1].duplicate( vNewPos);

                // update first's node infos
                entry.setValue(Integer.valueOf(levels + 1));
                bNodeTreated = true;
                break;
            }
        }
        if (!bNodeTreated) {// that means that this node falls over no other node
            // so draw it apart, but remember it on the hashtable

            // add to htComputedNodes
            htComputedNodes.put(node, Integer.valueOf(1));
        }
        return vNewPos;
    }

    /**
     * LINKS FUNCTIONS
     */

    private void computePingTrafficColors(HashMap links, HashMap nodes) {
        if (links == null) {
            return;
        }
        double minPerformance = Double.MAX_VALUE;
        double maxPerformance = Double.MIN_VALUE;
        double perf, lp;
        rcNode n, ns;
        // ServiceID sidFrom, sidTo;
        ILink link;
        // Object obj;
        try {
            for (Iterator it = links.keySet().iterator(); it.hasNext();) {
                link = (ILink) it.next();
                ns = (rcNode) ((HashMap) links.get(link)).get("fromNode");
                if (ns == null) {
                    continue;
                }
                n = (rcNode) ((HashMap) links.get(link)).get("toNode");
                if (n == null) {
                    continue;
                }
                perf = ns.connPerformance(n);
                lp = ns.connLP(n);
                if (lp == 1.0) {
                    continue;
                }
                if (perf < minPerformance) {
                    minPerformance = perf;
                }
                if (perf > maxPerformance) {
                    maxPerformance = perf;
                }
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "exception computing Ping links color: " + ex.getMessage());
            ex.printStackTrace();
        }
        try {
            Hashtable hPingOptions = (Hashtable) OptionsPanelValues.get("ping");
            if (minPerformance >= maxPerformance) {
                hPingOptions.put("MinColor", Color.GREEN);
                hPingOptions.put("MaxColor", Color.GREEN);
                if (minPerformance == Double.MAX_VALUE) {
                    hPingOptions.put("MinValue", Double.valueOf(0));
                    hPingOptions.put("MaxValue", Double.valueOf(0));
                    return;
                }
            } else {
                hPingOptions.put("MinColor", Color.GREEN);
                hPingOptions.put("MaxColor", Color.YELLOW);
            }
            hPingOptions.put("MinValue", Double.valueOf(minPerformance));
            hPingOptions.put("MaxValue", Double.valueOf(maxPerformance));
        } catch (Exception ex) {
            logger.log(Level.WARNING, "exception computing Ping links color: no options values hastable????");
            ex.printStackTrace();
        }
    }

    private void computeWANTrafficColors(HashMap links) {
        if (links == null) {
            return;
            // first, check all links and see which is the best and which is
            // the worst for the current quality selected in the link menu
        }

        double minPerformance = Double.MAX_VALUE;
        double maxPerformance = 0;
        double minLimit = Double.MAX_VALUE;
        double maxLimit = 0;
        double v;
        ILink link;
        try {
            for (Iterator it = links.keySet().iterator(); it.hasNext();) {
                link = (ILink) it.next();
                v = ((Double) (link.data)).doubleValue();
                if (v < minPerformance) {
                    minPerformance = v;
                }
                if (v > maxPerformance) {
                    maxPerformance = v;
                }
                v = link.speed;
                if (v < minLimit) {
                    minLimit = v;
                }
                if (v > maxLimit) {
                    maxLimit = v;
                }
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "exception computing WAN links color: " + ex.getMessage());
            ex.printStackTrace();
        }
        try {
            Hashtable hWANOptions = (Hashtable) OptionsPanelValues.get("wan");
            if (minLimit > maxLimit) {
                hWANOptions.put("LimitMinValue", Double.valueOf(0));
                hWANOptions.put("LimitMaxValue", Double.valueOf(0));
            } else {
                hWANOptions.put("LimitMinValue", Double.valueOf(minLimit));
                hWANOptions.put("LimitMaxValue", Double.valueOf(maxLimit));
            }
            if (minPerformance >= maxPerformance) {
                hWANOptions.put("MinColor", Color.CYAN);
                hWANOptions.put("MaxColor", Color.CYAN);
                if (minPerformance == Double.MAX_VALUE) {
                    hWANOptions.put("MinValue", Double.valueOf(0));
                    hWANOptions.put("MaxValue", Double.valueOf(0));
                    return;
                }
            } else {
                hWANOptions.put("MinColor", Color.CYAN);
                hWANOptions.put("MaxColor", Color.BLUE);
                hWANOptions.put("MinValue", Double.valueOf(minPerformance));
                hWANOptions.put("MaxValue", Double.valueOf(maxPerformance));
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "exception computing WAN links color: no options values hastable????");
            ex.printStackTrace();
        }
    }

    /**
     * computes netflow traffic color based on colors of each link
     * Sep 13, 2005 4:27:04 PM - mluc<br>
     *
     * @param links
     *            hashtable of NFLinks
     */
    private void computeNFTrafficColors(HashMap links) {
        if (links == null) {
            return;
            // first, check all links and see which is the best and which is
            // the worst for the current quality selected in the link menu
        }

        double minPerformance = Double.MAX_VALUE;
        double maxPerformance = 0;
        double minLimit = Double.MAX_VALUE;
        double maxLimit = 0;
        double v;
        NFLink link;
        try {
            for (Iterator it = links.keySet().iterator(); it.hasNext();) {
                link = (NFLink) it.next();
                v = ((Double) (link.data)).doubleValue();
                if (v < minPerformance) {
                    minPerformance = v;
                }
                if (v > maxPerformance) {
                    maxPerformance = v;
                }
                v = link.speed;
                if (v < minLimit) {
                    minLimit = v;
                }
                if (v > maxLimit) {
                    maxLimit = v;
                }
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "exception computing NetFlow links color: " + ex.getMessage());
            ex.printStackTrace();
        }
        try {
            Hashtable hNFOptions = (Hashtable) OptionsPanelValues.get("netflow");
            if (minLimit > maxLimit) {
                hNFOptions.put("LimitMinValue", Double.valueOf(0));
                hNFOptions.put("LimitMaxValue", Double.valueOf(0));
            } else {
                hNFOptions.put("LimitMinValue", Double.valueOf(minLimit));
                hNFOptions.put("LimitMaxValue", Double.valueOf(maxLimit));
            }
            if (minPerformance >= maxPerformance) {
                hNFOptions.put("MinColor", FarmsJoglPanel.minNetFlowColor);
                hNFOptions.put("MaxColor", FarmsJoglPanel.minNetFlowColor);
                if (minPerformance == Double.MAX_VALUE) {
                    hNFOptions.put("MinValue", Double.valueOf(0));
                    hNFOptions.put("MaxValue", Double.valueOf(0));
                    return;
                }
            } else {
                hNFOptions.put("MinColor", FarmsJoglPanel.minNetFlowColor);
                hNFOptions.put("MaxColor", FarmsJoglPanel.maxNetFlowColor);
                hNFOptions.put("MinValue", Double.valueOf(minPerformance));
                hNFOptions.put("MaxValue", Double.valueOf(maxPerformance));
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "exception computing NetFlow links color: no options values hastable????");
            ex.printStackTrace();
        }
    }

    @Override
    public ArrayList getOtherSelectedObjects(VectorO vEyePosition, VectorO vDirection, float radius,
            ArrayList alSelectedObjects) {
        HashMap hLinkAttrs;
        if (((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowWAN.isSelected()) {
            // select WAN objects
            // check first WAN links
            ILink link;
            float fGlobeRadius = JoglPanel.globals.globeRadius;
            try {
                // check wan links
                for (Iterator it = wanAttrs.keySet().iterator(); it.hasNext();) {
                    Object objLink = it.next();
                    if (!(objLink instanceof ILink)) {
                        continue;
                    }
                    link = (ILink) objLink;
                    hLinkAttrs = (HashMap) wanAttrs.get(link);

                    // check if arrow is selected
                    VectorO vLinkArrowBase = (VectorO) hLinkAttrs.get("LinkArrowBase");

                    if ((vLinkArrowBase != null)
                            && Globals.sphereIntersection(vEyePosition, vDirection, vLinkArrowBase, radius / 2)
                            && Globals.isVisible(vEyePosition, vLinkArrowBase, fGlobeRadius)) {
                        HashMap hObjAttrs = new HashMap();// create hashmap to put this link's attributes in it
                        double data = 0;
                        if ((link.data != null) && (link.data instanceof Double)) {
                            data = ((Double) (link.data)).doubleValue();
                        }
                        // String sName = "";//hLinkAttrs.get("LinkArrowBase");
                        String sDescription = "Traffic: " + DataGlobals.formatDoubleByteMul(data, 2) + "\nCapacity: "
                                + DataGlobals.formatDoubleByteMul(link.speed, 2) + "\nUtilisation: "
                                + (link.speed > 0 ? "" + (int) ((data * 100) / link.speed) : "???") + "%";
                        hObjAttrs.put("Name", "wan-link " + link.name);
                        hObjAttrs.put("Description", sDescription);
                        hObjAttrs.put("Position", vLinkArrowBase);
                        hObjAttrs.put("Type", "wan-link");
                        if (alSelectedObjects == null) {
                            alSelectedObjects = new ArrayList();
                        }
                        alSelectedObjects.add(hObjAttrs);
                    }
                    ;

                    // check selected packages
                    // get packages vector
                    /*
                     * Vector vPackages = (Vector)hLinkAttrs.get("PackagesVector");
                     * if ( vPackages != null ) {
                     * //if one available, draw packages
                     * WANPackage aPackage;
                     * for ( int i=0; i<vPackages.size(); i++) {
                     * aPackage = (WANPackage)vPackages.get(i);
                     * if ( Globals.sphereIntersection( vEyePosition, vDirection, aPackage.vPosition, radius/2)
                     * && Globals.isVisible( vEyePosition, aPackage.vPosition, fGlobeRadius) ) {
                     * //add package to selected objects
                     * HashMap hObjAttrs = new HashMap();//create hashmap to put this link's attributes in it
                     * hObjAttrs.put("Name", aPackage.sDescription);
                     * hObjAttrs.put("Position", aPackage.vPosition);
                     * hObjAttrs.put("Type", "wan-link-package");
                     * if ( alSelectedObjects==null )
                     * alSelectedObjects = new ArrayList();
                     * alSelectedObjects.add(hObjAttrs);
                     * }
                     * };
                     * };
                     */
                }
                // check routers
                float[] coords = null;
                // System.out.println("number of routers: "+hRouters.size());
                for (Iterator it = hRouters.values().iterator(); it.hasNext();) {
                    WANRouter router = (WANRouter) it.next();
                    coords = Globals.point2Dto3D(router.posLAT, router.posLONG, coords);
                    if (coords == null) {
                        continue;
                    }
                    VectorO vPos = new VectorO(coords);
                    if (Globals.sphereIntersection(vEyePosition, vDirection, vPos, radius)
                            && Globals.isVisible(vEyePosition, vPos, fGlobeRadius)) {
                        // add package to selected objects
                        HashMap hObjAttrs = new HashMap();// create hashmap to put this link's attributes in it
                        hObjAttrs.put("Name", "wan-router"
                                + (router.sLocation == null ? "" : " (" + router.sLocation + ")"));
                        hObjAttrs.put(
                                "Description",
                                "Total traffic IN: " + DataGlobals.formatDoubleByteMul(router.total_IN, 2)
                                        + "\nTotal traffic OUT: "
                                        + DataGlobals.formatDoubleByteMul(router.total_OUT, 2)
                        // +"\nLinks IN: "+router.toLinks.size()
                        // +"\nLinks OUT: "+router.fromLinks.size()
                                );
                        hObjAttrs.put("Position", vPos);
                        hObjAttrs.put("Type", "wan-link-router");
                        if (alSelectedObjects == null) {
                            alSelectedObjects = new ArrayList();
                        }
                        alSelectedObjects.add(hObjAttrs);
                    }
                }
            } catch (Exception ex) {
                logger.log(Level.INFO, "Exception checking new selected wan links: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
        if (((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowOS.isSelected()) {
            OSLink oslink;
            try {
                for (Iterator it = osAttrs.keySet().iterator(); it.hasNext();) {
                    Object objLink = it.next();
                    if (!(objLink instanceof OSLink)) {
                        continue;
                    }
                    oslink = (OSLink) objLink;
                    hLinkAttrs = (HashMap) osAttrs.get(oslink);

                    VectorO vLinkArrowBase = (VectorO) hLinkAttrs.get("LinkArrowBase");

                    if ((vLinkArrowBase != null)
                            && Globals.sphereIntersection(vEyePosition, vDirection, vLinkArrowBase, radius / 2)) {
                        HashMap hObjAttrs = new HashMap();// create hashmap to put this link's attributes in it
                        String sName = "";// hLinkAttrs.get("LinkArrowBase");
                        sName = oslink.toString();
                        hObjAttrs.put("Name", sName);
                        hObjAttrs.put("Position", vLinkArrowBase);
                        hObjAttrs.put("Type", "os-link");
                        if (alSelectedObjects == null) {
                            alSelectedObjects = new ArrayList();
                        }
                        alSelectedObjects.add(hObjAttrs);
                    }
                    ;
                }
            } catch (Exception ex) {
                logger.log(Level.INFO, "Exception checking new selected os links: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
        if (((FarmsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowNF.isSelected()) {
            // select NetFlow objects
            // check first NetFlow links
            NFLink link;
            float fGlobeRadius = JoglPanel.globals.globeRadius;

            final FarmsSerMonitor.GlobeLinksType glt = FarmsSerMonitor.getGlobeLinksTypeFromEnv();
            String attrName = "";
            String routerName = "";
            switch (glt) {
            case FDT: {
                attrName = "FDT link ";
                routerName = "FDT node";
                break;
            }
            case NETFLOW: {
                attrName = "NetFlow ";
                routerName = "NetFlow device";
                break;
            }
            case OPENFLOW: {
                attrName = "OpenFlow ";
                routerName = "OpenFlow device";
                break;
            }
            case UNDEFINED:
                break;
            default:
                break;
            }

            try {
                // check net flow links
                for (Iterator it = nfAttrs.keySet().iterator(); it.hasNext();) {
                    Object objLink = it.next();
                    if (!(objLink instanceof NFLink)) {
                        continue;
                    }
                    link = (NFLink) objLink;
                    hLinkAttrs = (HashMap) nfAttrs.get(link);

                    // check if arrow is selected
                    VectorO vLinkArrowBase = (VectorO) hLinkAttrs.get("LinkArrowBase");

                    if ((vLinkArrowBase != null)
                            && Globals.sphereIntersection(vEyePosition, vDirection, vLinkArrowBase, radius / 2)
                            && Globals.isVisible(vEyePosition, vLinkArrowBase, fGlobeRadius)) {
                        HashMap hObjAttrs = new HashMap();// create hashmap to put this link's attributes in it
                        double data = 0;
                        if ((link.data != null) && (link.data instanceof Double)) {
                            data = ((Double) (link.data)).doubleValue();
                        }
                        // String sName = "";//hLinkAttrs.get("LinkArrowBase");
                        final boolean isNetLink = ((link.name != null) && (link.name.indexOf("NetLink") >= 0));

                        String sDescription = ((isNetLink) ? "Capacity: " : "Traffic: ")
                                + DataGlobals.formatDoubleByteMul(data, 2)
                        /*
                         * + "\nCapacity: " + DataGlobals.formatDoubleByteMul(link.speed/8, 2, false)
                         * + "\nUtilisation: " + (link.speed>0?""+((int)(data*100*8/link.speed)):"???")+"%"
                         */;

                        hObjAttrs.put("Name", ((isNetLink) ? "" : attrName) + link.name);
                        hObjAttrs.put("Description", sDescription);
                        hObjAttrs.put("Position", vLinkArrowBase);
                        hObjAttrs.put("Type", "netflow-link");
                        if (alSelectedObjects == null) {
                            alSelectedObjects = new ArrayList();
                        }
                        alSelectedObjects.add(hObjAttrs);
                    }
                }
                // check netflow devices
                float[] coords = null;
                // System.out.println("number of routers: "+hRouters.size());
                for (Iterator it = hNetFlowDevices.values().iterator(); it.hasNext();) {
                    NetFlowDevice router = (NetFlowDevice) it.next();
                    coords = Globals.point2Dto3D(router.posLAT, router.posLONG, coords);
                    if (coords == null) {
                        continue;
                    }
                    VectorO vPos = new VectorO(coords);
                    if (Globals.sphereIntersection(vEyePosition, vDirection, vPos, radius)
                            && Globals.isVisible(vEyePosition, vPos, fGlobeRadius)) {
                        // add package to selected objects
                        HashMap hObjAttrs = new HashMap();// create hashmap to put this link's attributes in it
                        // TODO: this boolean could be used to indicate a source, an intermediary or a destination node
                        hObjAttrs.put("Name", routerName);// (router.bIsRouter?"FDT router":"FDT node")+(router.sLocation==null?"":" ("+(router.sLocation.startsWith("[R]")?router.sLocation.substring(3):router.sLocation)+")"));
                        hObjAttrs.put("Description", "Name: " + router.sLocation);// "Geographical position:\n "+router.posLONG+" degree LONG\n "+router.posLAT+" degree LAT");
                        // "Total traffic IN: "+DataGlobals.formatDoubleByteMul(router.total_IN, 2)
                        // +"\nTotal traffic OUT: "+DataGlobals.formatDoubleByteMul(router.total_OUT, 2)
                        // );
                        hObjAttrs.put("Position", vPos);
                        hObjAttrs.put("Type", "netflow-device");
                        if (alSelectedObjects == null) {
                            alSelectedObjects = new ArrayList();
                        }
                        alSelectedObjects.add(hObjAttrs);
                    }
                }
            } catch (Exception ex) {
                logger.log(Level.INFO, "Exception checking new selected netflow links: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
        return alSelectedObjects;
    }

    /** indicates that a globe transformation is on, so that packages at least should not be drawn */
    private volatile boolean bGlobeTransformIsOn = false;

    // first boolean shows that animation requested to start
    // second shows that animation is under way
    private boolean bAnimateWAN = false;

    /** this indicates that the (re)computation for links animation was done and the drawing can procede */
    private volatile boolean bAnimationRecomputed = false;

    private boolean bAnimateWANTimer = false;

    // number of steps for a package to make to slide along the segment
    // MIN is ALWAYS greater than MAX
    public static final int MIN_SPEED_WAN_SEGMENT_STEPS = 50;

    public static final int MAX_SPEED_WAN_SEGMENT_STEPS = 10;

    protected class WANPackage {

        public float fraction_radius;// determined by data field of link and maximal capacity of the link

        public VectorO vPosition;// center of cube that represents the package where it should be drawn

        // these two should be computed somehow
        VectorO vRotAxis;// rotation axis

        float fRotAngle;// rotation angle

        VectorO vRotAxis2;// perpendicular rotation axis ( is z )

        float fRotAngle2;// rotation angle in plane (xy)

        public String sDescription;// informations about the package

        public WANPackage() {
        }// empty constructor to have something...
    }

    public void changeLinkAnimationStatus(boolean bAnimate) {
        bAnimateWAN = bAnimate;
        if (bAnimateWAN && !bAnimateWANTimer) {
            // if animation is on and there is no animation timer started, start it
            BackgroundWorker.schedule(new TimerTask() {

                @Override
                public void run() {
                    Thread.currentThread().setName(" ( ML ) - JOGL - FarmsNodesRenderer - WAN animation Timer Thread");
                    bAnimateWANTimer = true;
                    /**
                     * action to be completed here:<br>
                     * update each WAN link's data packets position based on link's
                     * segments, so that each segment has one packet, with different sizes.<br>
                     * A package is defined by a position vector, a dimension/radius, determined by
                     * the size of data it carries, and a description including source node, destination node,
                     * link's details and current data size.<br>
                     * A WAN link must have among its attributes a list of vectors that determine the segments.
                     */
                    if (!bGlobeTransformIsOn) {
                        try {
                            ILink link;
                            HashMap hLinkAttrs;
                            // for each WAN link
                            for (Iterator it = wanAttrs.keySet().iterator(); it.hasNext();) {
                                Object objLink = it.next();
                                if (!(objLink instanceof ILink)) {
                                    continue;
                                }
                                link = (ILink) objLink;
                                hLinkAttrs = (HashMap) wanAttrs.get(link);
                                updatePackages(hLinkAttrs, link.data, link.speed, link.name);
                            }
                            ;
                            // for each NF link
                            for (Iterator it = nfAttrs.keySet().iterator(); it.hasNext();) {
                                Object objLink = it.next();
                                if (!(objLink instanceof NFLink)) {
                                    continue;
                                }
                                hLinkAttrs = (HashMap) nfAttrs.get(objLink);
                                updatePackages(hLinkAttrs, ((NFLink) objLink).data, ((NFLink) objLink).speed,
                                        ((NFLink) objLink).name);
                            }
                            ;

                            bAnimationRecomputed = true;
                            JoglPanel.globals.canvas.repaint();
                        } catch (Exception ex) {
                            logger.log(Level.WARNING,
                                    "Exception during animating WAN packages movement: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }
                    // this is not synchronized, so it may happen that animation stayes stopped although the
                    // animation checkbox is checked, or stay running although the checkbox is unselected...
                    if (!bAnimateWAN) {
                        bAnimateWANTimer = false;
                        cancel();
                    }
                    ;
                }

                /**
                 * updates coordinates for the packages for this current link
                 *
                 * @author mluc
                 * @since Nov 15, 2006
                 * @param hLinkAttrs
                 * @param objData
                 * @param speed
                 * @param name
                 */
                public void updatePackages(HashMap hLinkAttrs, Object objData, double speed, String name) {
                    Object obj;
                    // get max steps per segment
                    obj = hLinkAttrs.get("WANSegmentSteps");
                    if ((obj == null) || !(obj instanceof Integer)) {
                        return;// no number of steps, so ignore it, it hasn't been computed
                    }
                    int total_steps = ((Integer) obj).intValue();
                    if (total_steps == 0) {
                        return;// no number of steps, so ignore it, it hasn't been computed
                    }
                    // System.out.println("total steps: "+total_steps);
                    // get points that form the link
                    obj = hLinkAttrs.get("LinkPoints");
                    if ((obj == null) || !(obj instanceof ArrayList)) {
                        return;// no points, so ignore it, it hasn't been computed
                    }
                    ArrayList alPoints = (ArrayList) obj;
                    // System.out.println("number of points: "+alPoints.size());
                    // get old current step
                    int current_step = 0;
                    obj = hLinkAttrs.get("WANSegmentCurrentStep");
                    if ((obj != null) && (obj instanceof Integer)) {
                        current_step = ((Integer) obj).intValue() + 1;
                    }
                    if (current_step < 0) {
                        current_step = 0;
                    }
                    if (current_step >= total_steps) {
                        current_step = 0;
                    }
                    // save new current step
                    hLinkAttrs.put("WANSegmentCurrentStep", Integer.valueOf(current_step));
                    // System.out.println("current step: "+current_step);
                    // get list of packages
                    // if vector doesn't exist, create it
                    Vector vPackages = null;
                    obj = hLinkAttrs.get("PackagesVector");
                    if ((obj != null) && (obj instanceof Vector)) {
                        vPackages = (Vector) obj;
                    } else {
                        vPackages = new Vector();
                        hLinkAttrs.put("PackagesVector", vPackages);
                    }
                    ;
                    // System.out.println("number of packages: "+vPackages.size());
                    // first, take in consideration the fact that the points may have changed,
                    // so try to remove excedentary packages
                    // so that, vPackages.size = alPoints.size-1
                    while (vPackages.size() >= alPoints.size()) {
                        vPackages.remove(vPackages.size() - 1);
                    }
                    // if current step is 0, a new package is to be create
                    if (current_step == 0) {
                        // if number of packages is equal with number of points-1 in link, remove last package and
                        // use it as first
                        // else, add a new package
                        // removing last excedentary to be package
                        obj = null;
                        WANPackage newPackage = null;
                        if (vPackages.size() == (alPoints.size() - 1)) {
                            obj = vPackages.remove(vPackages.size() - 1);
                        }
                        // obtain a new package
                        if ((obj != null) && (obj instanceof WANPackage)) {
                            newPackage = (WANPackage) obj;
                        } else {
                            newPackage = new WANPackage();
                        }
                        // set some neccessary attributes
                        double data = 0;
                        newPackage.fraction_radius = 0f;
                        double Max = 0;
                        try {
                            Hashtable hWANOptions = (Hashtable) OptionsPanelValues.get("wan");
                            Max = ((Double) hWANOptions.get("MaxValue")).doubleValue();
                        } catch (Exception ex) {
                            Max = 0;
                        }
                        if ((objData != null) && (objData instanceof Double)
                                && ((data = ((Double) (objData)).doubleValue()) != 0)
                                && /* link.speed */((data < Max) && (data < speed))) {
                            newPackage.fraction_radius = (float) (data / Max/* link.speed */);
                        }
                        newPackage.fraction_radius = .5f + (.4f * newPackage.fraction_radius);// if no data available, use
                        // a standard size
                        newPackage.sDescription = "WAN Package: " + name + " = " + (int) data + "/" + (int) speed
                                + " Mbps -> " + (speed > 0 ? "" + (int) ((data * 100) / speed) : "???") + "%";
                        // add the new package
                        vPackages.add(0, newPackage);
                    }
                    ;
                    // System.out.println("vPackages.size="+vPackages.size()+" and alPoints.size="+alPoints.size());
                    // traverse the vector of packages and position packages on the map
                    WANPackage aPackage;
                    // int nPoints = alPoints.size();
                    VectorO vStart, vEnd, vDir;
                    for (int i = 0; i < vPackages.size(); i++) {
                        // get start and end points that compose the segment
                        vStart = (VectorO) alPoints.get(i);
                        vEnd = (VectorO) alPoints.get(i + 1);// nPoints-i-1);//(i+2)%nPoints);
                        aPackage = (WANPackage) vPackages.get(i);
                        // compute vector direction from start to end:
                        vDir = VectorO.SubstractVector(vEnd, vStart);
                        // multiply vDir to current step from total steps to go to correct position
                        vDir.MultiplyScalar(current_step / (double) total_steps);
                        // get absolute position for point by adding start point
                        vDir.AddVector(vStart);
                        aPackage.vPosition = vDir;
                    }
                }
            }, 100, 200);// check animation at each 100 miliseconds
        }
    }

    /**
     * changes the subview mode, meaning that the position of same objects
     * is a little bit changed, but the data scene remains the same...
     *
     * @param subView
     */
    @Override
    public void changeSubView(int subView) {
        if (subView == 0) {
            bOnTopView = false;
        } else if (subView == 1) {
            bOnTopView = true;
        }
    }

    /**
     * fills farm node tooltip with specific info
     */
    @Override
    public void fillSelectedNodeInfo(rcNode n, HashMap hObjAttrs) {
        if (n.UnitName.equals("upb") && (vcf.vcfList.size() > 0)) {
            // do the trick to show monalisa developers from upb ;)
            long currentTime = System.currentTimeMillis();
            if ((lLastVcfChange == -1) || ((lLastVcfChange + nVCF_SHOW_TIME) < currentTime)) {
                // change vcf
                lLastVcfChange = currentTime;
                nCurrentVcf++;
                if (nCurrentVcf >= vcf.vcfList.size()) {
                    nCurrentVcf = 0;
                }
            }
            // read vcf info and put in hash
            vcf v = (vcf) vcf.vcfList.get(nCurrentVcf);
            if (v != null) {
                String fn = (String) v.hmData.get("FN");
                if (fn != null) {
                    hObjAttrs.put("Name", fn);
                } else {
                    hObjAttrs.put("Name", n.UnitName);
                }
                String email = (String) v.hmData.get("EMAIL");
                if (email != null) {
                    hObjAttrs.put("Description", email);
                } else {
                    hObjAttrs.put("Description", "Site info:\nIp Address: " + n.IPaddress);
                }
                if (v.hmData.get("ImageID") != null) {
                    hObjAttrs.put("ImageID", v.hmData.get("ImageID"));
                    hObjAttrs.put("ImageWidth", v.hmData.get("ImageWidth"));
                    hObjAttrs.put("ImageHeight", v.hmData.get("ImageHeight"));
                } else {
                    BufferedImage bi = (BufferedImage) v.hmData.get("PHOTO");
                    if (bi != null) {
                        hObjAttrs.put("BufferedImage", bi);
                        hObjAttrs.put("whRaport", v.hmData.get("whRaport"));
                        hObjAttrs.put("vcf", v);
                    }
                }
                ;
            }
            ;
        } else {
            String nodeName = n.szOpticalSwitch_Name == null ? n.shortName : n.szOpticalSwitch_Name;
            hObjAttrs.put("Name", nodeName + " (" + n.IPaddress + ")");
            String sDescription = "Group: " + n.mlentry.Group;
            // compute total number of parameters
            // int nrClusters = n.client.farm.getClusters().size();
            // int nrNodes = n.client.farm.getNodes().size();
            // int nrParams = n.client.farm.getParameterList().size();
            Vector v = n.client.farm.getClusters(), v1;
            int tparams = 0;
            for (int i = 0; i < v.size(); i++) {
                v1 = ((MCluster) v.get(i)).getNodes();
                for (int j = 0; j < v1.size(); j++) {
                    tparams += ((MNode) v1.get(j)).getParameterList().size();
                }
            }
            ;
            // "<html>"+nrClusters+" cluster"+(nrClusters != 1 ? "s" : "")+"<br>"
            // +nrNodes+" node"+(nrNodes != 1 ? "s" : "")+"<br>"
            // +nrParams+" unique param"+(nrParams != 1 ? "s" : "")+"<br>"
            // +tparams+" total param"+(tparams != 1 ? "s" : "")+"</html>";
            // n.client.

            Gresult grLoad = ((n == null) || (n.global_param == null) ? null : (Gresult) n.global_param.get("Load5"));
            /*
             * if (grLoad == null ) {
             * grLoad = (n==null || n.global_param == null?null:(Gresult) n.global_param.get("Load1" ));
             * if(grLoad!=null && grLoad.ClusterName.indexOf("PBS")==-1 && grLoad.ClusterName.indexOf("Condor")==-1 )
             * grLoad=null;
             * }
             */
            if (grLoad != null) {
                sDescription += "\nNo of nodes: " + grLoad.Nodes;
                if (grLoad.hist != null) {
                    sDescription += "\nNo of free nodes: " + (grLoad.hist[0] + grLoad.hist[1]);
                }
            }
            ;
            sDescription += "\nTotal number of params: " + tparams;
            hObjAttrs.put("Description", sDescription);
        }
        ;
    }

    public boolean getLinkAnimationStatus() {
        return bAnimateWAN;
    }

    @Override
    public void mouseClick(float LONG, float LAT) {
    }

    @Override
    public void mouseDblClick(float LONG, float LAT) {
    }

    @Override
    public void mouseMove(int mouse_x, int mouse_y) {
    }

    @Override
    public void optionPanelChanged(int event) {
    }

    @Override
    public void radiusChangeFinish() {
        bGlobeTransformIsOn = false;
    }

    @Override
    public void radiusChangeStart() {
        bGlobeTransformIsOn = true;
        bAnimationRecomputed = false;
    }

    @Override
    public void radiusChanged() {
    }

}
