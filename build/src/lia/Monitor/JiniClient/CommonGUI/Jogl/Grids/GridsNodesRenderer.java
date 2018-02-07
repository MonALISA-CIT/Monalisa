/**
 * Aug 14, 2006 - 11:38:18 AM
 * mluc - MonALISA1
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl.Grids;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.TimerTask;
import java.util.Vector;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Jogl.AbstractNodesRenderer;
import lia.Monitor.JiniClient.CommonGUI.Jogl.DataGlobals;
import lia.Monitor.JiniClient.CommonGUI.Jogl.GlobeListener;
import lia.Monitor.JiniClient.CommonGUI.Jogl.JoGLArc3D;
import lia.Monitor.JiniClient.CommonGUI.Jogl.JoglPanel;
import lia.Monitor.JiniClient.CommonGUI.Jogl.VectorO;
import lia.Monitor.JiniClient.CommonGUI.Jogl.ZoomMapRenderer;
import lia.Monitor.monitor.ILink;

/**
 * @author mluc
 *
 */
public class GridsNodesRenderer extends AbstractNodesRenderer implements GlobeListener {
    public GridsNodesRenderer() {
        subViewCapabilities = new String[] { "Normal view", "OnTop view" };
    }

    /**
     * switches between the two existing views:<br>
     *  - normal, where farms can overlap, and<br>
     *  - on top, where overlapping farms are stacked one above the other
     */
    private boolean bOnTopView = false;

    /** indicates that a globe transformation is on, so that packages at least should not be drawn */
    private volatile boolean bGlobeTransformIsOn = false;

    /**
     * draws the farms nodes as cilinders
     */
    @Override
    public void drawNodes(GL2 gl, Object[] graphicalAttrs) {
        //get radius
        Object obj;
        obj = ((Hashtable) graphicalAttrs[0]).get("NodeRadius");
        if (obj == null) {
            return;
        }
        float radius = ((Float) obj).floatValue();

        HashMap htComputedNodes = new HashMap();//used for ontop view
        HashMap hAttrs;
        rcNode node;
        VectorO vNewPos;//used for on top view
        //node's vectors
        VectorO[] vectors;
        //		Color color;
        int nodeID;
        for (Iterator it = ((HashMap) graphicalAttrs[1]).keySet().iterator(); it.hasNext();) {
            node = (rcNode) it.next();

            //check if the grid the node belongs to is visible or not
            if (node.bHiddenOnMap) {
                continue;
            }
            //save the matrix
            gl.glPushMatrix();
            try {
                obj = ((HashMap) graphicalAttrs[1]).get(node);
                if (obj != null) {//this node has its attributes computed, so draw it
                    hAttrs = (HashMap) obj;
                    float nFactor = 1;
                    obj = hAttrs.get("NodeFactor");
                    if (obj != null) {
                        nFactor = ((Float) obj).floatValue();//skip to next one
                    }
                    vectors = (VectorO[]) hAttrs.get("PositioningVectors");
                    if (vectors == null) {
                        continue;
                    }
                    obj = hAttrs.get("NodeID");
                    if (obj == null) {
                        continue;//skip to next one
                    }
                    nodeID = ((Integer) obj).intValue();
                    //draw node
                    if (nodeID > 0 /*&& vectors!=null*/) {//vectors is already checked for nullity
                        if (bOnTopView) {
                            vNewPos = recomputeVectors(((HashMap) graphicalAttrs[1]), htComputedNodes, node, vectors,
                                    radius);
                        } else {
                            vNewPos = vectors[1];
                        }
                        drawNode(gl, vectors[0], vNewPos, nodeID, radius * nFactor);
                    }
                    //draw node links to other nodes
                }
            } catch (Exception ex) {
                //				logger.log(Level.INFO, "Could not draw node "+(node==null?"null":node.UnitName)+" exception:"+ex.getMessage());
                ex.printStackTrace();
            }
            //pop the matrix
            gl.glPopMatrix();
        }
    }

    public static Color[] tierColorsUp = new Color[] { new Color(0xfff436), new Color(0xfff227/*F4E934*/) };//, new Color(0xF4E934), new Color(0xF4E934)};//new Color(0x37eaff), new Color(0xbe93e6)};
    public static Color[] tierColorsDown = new Color[] { new Color(0xff2121), new Color(0xff8a00/*F45B3D*/) };//, new Color(0xF45B3D),new Color(0xF45B3D)};//7B5D)};//new Color(0x396924), new Color(0x1e4386)};
    public static Color errColorUp = new Color(0xF4E934);
    public static Color errColorDown = new Color(0xF45B3D);
    public static Color gridNColorUp = new Color(0x00ff30);
    public static Color gridNColorDown = new Color(0x234e2c);
    public static Color gridOSColorUp = new Color(0xfa9e94);
    public static Color gridOSColorDown = new Color(0xff0000);//5148);

    /**
     * computes some node related info like its value to be represented<br>
     * this computation is called at RECOMPUTE_NODES_TIME to show data received<br>
     * after each recomputation, draw node
     */
    @Override
    public void computeNodes(GL2 gl, Object[] graphicalAttrs) {
        //get radius
        Object obj;
        obj = ((Hashtable) graphicalAttrs[0]).get("NodeRadius");
        if (obj == null) {
            return;
        }
        float radius = ((Float) obj).floatValue();

        //compute maximal values
        //computeNodesColors( ((HashMap)graphicalAttrs[1]));

        HashMap htComputedNodes = new HashMap();//used for ontop view

        rcNode node;
        HashMap hAttrs;
        float[] fractions;
        Color[] colorsUp;
        Color[] colorsDown;
        int components;
        int nodeID;
        VectorO vNewPos;
        VectorO[] vectors;
        for (Iterator it = ((HashMap) graphicalAttrs[1]).keySet().iterator(); it.hasNext();) {
            node = (rcNode) it.next();
            //check if the grid the node belongs to is visible or not
            if (node.bHiddenOnMap) {
                continue;
            }
            //			System.out.println("node: "+node);
            //save the matrix
            gl.glPushMatrix();
            try {
                obj = ((HashMap) graphicalAttrs[1]).get(node);
                if (obj != null) {//this node has attributes , so consider it
                    hAttrs = (HashMap) obj;
                    //determin components
                    fractions = (float[]) hAttrs.get("NodeFractions");
                    if (fractions == null) {
                        fractions = new float[10];
                        hAttrs.put("NodeFractions", fractions);
                    }
                    ;
                    colorsUp = (Color[]) hAttrs.get("NodeColorsUp");
                    if (colorsUp == null) {
                        colorsUp = new Color[10];
                        hAttrs.put("NodeColorsUp", colorsUp);
                    }
                    ;
                    colorsDown = (Color[]) hAttrs.get("NodeColorsDown");
                    if (colorsDown == null) {
                        colorsDown = new Color[10];
                        hAttrs.put("NodeColorsDown", colorsDown);
                    }
                    ;
                    components = 1;
                    fractions[0] = 1f;
                    //compute fractions and colors by updating vectors already allocated
                    if (node.errorCount == -3) {
                        colorsUp[0] = gridOSColorUp;
                        colorsDown[0] = gridOSColorDown;
                    } else if (node.errorCount == -2) {
                        colorsUp[0] = gridNColorUp;
                        colorsDown[0] = gridNColorDown;
                    } else if (node.errorCount == -1) {
                        colorsUp[0] = errColorUp;
                        colorsDown[0] = errColorDown;
                        /** --------------- color links by tier type ------------ */
                    } else if (node.myID >= (tierColorsUp.length - 1)) {
                        colorsUp[0] = tierColorsUp[tierColorsUp.length - 1];
                        colorsDown[0] = tierColorsDown[tierColorsUp.length - 1];
                    } else {
                        colorsUp[0] = tierColorsUp[node.myID];
                        colorsDown[0] = tierColorsDown[node.myID];
                        /** --------------- color links by tier group ------------ */
                        //						} else if ( node.errorCount>=defColorsUp.length || node.errorCount == 0 ) {
                        //						colorsUp[0] = defColorsUp[0];
                        //						colorsDown[0] = defColorsDown[0];
                        //						} else {
                        //						colorsUp[0] = defColorsUp[node.errorCount];
                        //						colorsDown[0] = defColorsDown[node.errorCount];
                    }
                    //					if (node.errorCount == 0) {
                    //					components = 1;
                    //					fractions[0] = 1f;
                    //					colorsUp[0] = colorUp;//Color.RED;
                    //					colorsDown[0] = colorDown;
                    //					} else {
                    //					pie px = (pie) node.haux.get(((FarmsJoglPanel)JoglPanel.globals.mainPanel).pieKey);
                    //					if ((px == null) || (px.len == 0)) {
                    //					components = 1;
                    //					fractions[0] = 1f;
                    ////					colors[0] = Color.YELLOW;
                    //					colorsUp[0] = Color.YELLOW;//Color.RED;
                    //					colorsDown[0] = Color.RED;
                    //					} else {
                    //					components = px.len;
                    //					for (int i = 0; i < components; i++) {
                    //					fractions[i] = (float)px.rpie[i];
                    //					colorsUp[i] = px.cpie[i];
                    //					colorsDown[i] = new Color(px.cpie[i].getRed(),px.cpie[i].getGreen(),px.cpie[i].getBlue());
                    //					//								if(node.UnitName.equals("upb"))
                    //					//								System.out.println("upb"+i+": "+fractions[i]+" "+colors[i]);
                    //					}
                    //					}
                    //					}
                    //set size of node
                    float nFactor = 1;
                    switch (node.myID) {
                    case 0:
                        nFactor = 2;
                        break;
                    case 1:
                        nFactor = 1.5f;
                        break;
                    case 2:
                        nFactor = 1;
                        break;
                    default:
                    }
                    hAttrs.put("NodeFactor", new Float(nFactor));
                    //set number of components
                    hAttrs.put("NodeComponents", Integer.valueOf(components));
                    //create display list for node
                    //if neccessary, recompute display list
                    obj = hAttrs.get("NodeID");
                    if (obj != null) {//for that, delete old id
                        gl.glDeleteLists(((Integer) obj).intValue(), 1);
                        obj = null;
                    }
                    ;
                    if (obj == null) {//each time reconstruct the node...?
                        nodeID = gl.glGenLists(1);
                        //System.out.println("linkID generated: "+linkID);
                        gl.glNewList(nodeID, GL2.GL_COMPILE);

                        //construct node
                        constructNode(gl, components, fractions, colorsUp, colorsDown);

                        gl.glEndList();
                        hAttrs.put("NodeID", Integer.valueOf(nodeID));
                    } else {
                        nodeID = ((Integer) obj).intValue();
                    }
                    vectors = (VectorO[]) hAttrs.get("PositioningVectors");
                    //draw node
                    if (bOnTopView) {
                        vNewPos = recomputeVectors(((HashMap) graphicalAttrs[1]), htComputedNodes, node, vectors,
                                radius);
                    } else {
                        vNewPos = vectors[1];
                    }
                    drawNode(gl, vectors[0], vNewPos, nodeID, radius * nFactor);
                }
            } catch (Exception ex) {
                //				logger.log(Level.WARNING, "Could not compute and draw node "+(node==null?"null":node.UnitName)+ " ex:"+ex.getMessage());
                ex.printStackTrace();
            }
            //pop the matrix
            gl.glPopMatrix();
        }
    }

    /**
     * creates the cap to be used by each node
     */
    @Override
    public void initNodes(GL2 gl, Object[] graphicalAttrs) {
        //		packageID = DataGlobals.getCubeID( gl, "lia/images/ml_package.jpg", "Package Cube");
        packageID = gl.glGenLists(1);
        //System.out.println("routerID="+ routerID);
        gl.glNewList(packageID, GL2.GL_COMPILE);
        {
            ZoomMapRenderer.drawSphere(gl, 8, 4);
        }
        gl.glEndList();
    }

    private final float[] topCenter = new float[] { 0, 0, .5f };
    private final float[] pa1 = new float[3];
    private final float[] pa2 = new float[3];
    private final float[] p1 = new float[3];
    private final float[] p2 = new float[3];
    private final float[] aux = new float[3];
    private final float fraction = .5f;
    private final int conID = 0;
    private int packageID = 0;//compiled openGL cube with size 1 used to draw packages for a link

    /**
     * draws a node centered in (0,0,0) pointed in z direction, and having unitar dimensions,
     * with all the colors on it
     * @param gl
     * @param Vdir
     * @param Vpos
     * @param components
     * @param fractions
     * @param colors
     * @param radius
     */
    private void constructNode(GL2 gl, int components, float[] fractions, Color[] colorsUp, Color[] colorsDown) {
        VectorO Vdir = new VectorO(0, 0, 1);
        VectorO Vp = new VectorO(1, 0, 0);
        //		VectorO Vpos = new VectorO(0,0,0);
        //		int max_points = 32;//total number of points to be drawn
        float curr_Tangle = 0f;//current total angle of drawn cilinder
        int points;//number of points to be drawn for a fraction 0-32
        float angle;//distance in grades between two consecutive points - depends on current component to be drawn
        //compute start points
        p1[0] = Vp.getX();
        p2[0] = Vp.getX();
        p1[1] = Vp.getY();
        p2[1] = Vp.getY();
        p1[2] = Vp.getZ();
        p2[2] = Vp.getZ() + .5f;

        for (int i = 0; i < components; i++) {
            points = 1 + (int) (fractions[i] * 31);
            angle = (360f * fractions[i]) / points;
            //draw first point
            for (int j = 0; j < points; j++) {
                //draw next points
                //set previous points, before computing the new ones, after rotation
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
                //compute current points
                p1[0] = Vp.getX();
                p2[0] = Vp.getX();
                p1[1] = Vp.getY();
                p2[1] = Vp.getY();
                p1[2] = Vp.getZ();
                p2[2] = Vp.getZ() + .5f;
                gl.glColor3f(colorsUp[i].getRed() / 255f, colorsUp[i].getGreen() / 255f, colorsUp[i].getBlue() / 255f);
                gl.glBegin(GL.GL_TRIANGLE_STRIP);
                //top triangle
                gl.glVertex3fv(topCenter, 0);
                //---code insertion---//
                aux[0] = pa2[0] * (1f - fraction);
                aux[1] = pa2[1] * (1f - fraction);
                aux[2] = (pa2[2] * (1f - fraction)) + (.5f * fraction);
                gl.glVertex3fv(aux, 0);
                aux[0] = p2[0] * (1f - fraction);
                aux[1] = p2[1] * (1f - fraction);
                aux[2] = (p2[2] * (1f - fraction)) + (0.5f * fraction);
                gl.glVertex3fv(aux, 0);
                gl.glColor3f(colorsDown[i].getRed() / 255f, colorsDown[i].getGreen() / 255f,
                        colorsDown[i].getBlue() / 255f);
                //---end code insertion---//
                gl.glVertex3fv(pa2, 0);
                gl.glVertex3fv(p2, 0);
                //side triangles
                //gl.glColor3f(colors[i].getRed()/2f/255f, colors[i].getGreen()/2f/255f, colors[i].getBlue()/2f/255f);
                gl.glVertex3fv(pa1, 0);
                gl.glVertex3fv(p1, 0);
                //base triangle
                gl.glEnd();
            }//end for points
        }//end for components
    }

    /**
     * draws a node by creating a diplay list for it
     * @param gl
     * @param Vdir
     * @param Vpos
     * @param components
     * @param fractions
     * @param colors
     * @param radius
     */
    private void drawNode(GL2 gl, VectorO VdirInit, VectorO VposInit, int nodeID, float radius) {
        //operations are put in inverse order because last is first executed in opengl
        //3. position object cu correct coordinates
        gl.glTranslatef(VposInit.getX(), VposInit.getY(), VposInit.getZ());
        //2. operation: rotate from (0,0,1) to Vdir
        //for that, first compute rotation axis as cross product between z and Vdir
        VectorO VzAxis = new VectorO(0, 0, 1);
        VectorO VRotAxis = VzAxis.CrossProduct(VdirInit);
        // rotate z to Vdir around vectorial product with dot product
        gl.glRotatef((float) ((Math.acos(VzAxis.DotProduct(VdirInit)) * 180) / Math.PI), VRotAxis.getX(),
                VRotAxis.getY(), VRotAxis.getZ());
        //1. operation: scale to radius dimmensions:
        gl.glScalef(radius, radius, radius);
        //use already constructed cap as display list
        //draw it at (0,0,0) in self reference system
        gl.glCallList(nodeID);
    }

    /**
     * 						NODES FUNCTIONS
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
        VectorO[] cnVectors = null;//checked node vectors
        VectorO vNewPos = vectors[1];
        //for each node, see if falls on top of another one, already cheched
        //and the draw it accordingly
        //for that I'll use an Hashtable to remember the checked nodes and
        //their properties: the postion and direction, the next level to put
        //a node that falls on current one, etc
        //so, hastable contains an array of two objects: the rcNode and its properties
        //one is  the key, and one the value
        //there is only one property that must be memorized: the number of nodes already drawn on top of first one
        //including this one, because this can be multiplied with the radius to obtain an height
        bNodeTreated = false;
        //check with each already treated nodes
        for (Iterator it = htComputedNodes.entrySet().iterator(); it.hasNext();) {
            entry = (Map.Entry) it.next();
            checked_node = (rcNode) entry.getKey();
            int levels = ((Integer) entry.getValue()).intValue();
            obj = gaNodes.get(checked_node);
            if (obj != null) {//this node has its attributes computed, so draw it
                cnVectors = (VectorO[]) ((HashMap) obj).get("PositioningVectors");
                if (cnVectors == null) {
                    continue;
                }
            }
            ;
            if ((float) vectors[1].distanceTo(cnVectors[1]) < (2 * radius)) {
                //distance from one node to another one is smaller than radius, so draw one on top of other
                vNewPos = new VectorO(cnVectors[1]);
                VectorO vLevel = new VectorO(cnVectors[0]);
                vLevel.MultiplyScalar(2 * levels * radius);
                vNewPos.AddVector(vLevel);
                //compute some view values
                //and draw the node
                //vectors[1].duplicate( vNewPos);

                //update first's node infos
                entry.setValue(Integer.valueOf(levels + 1));
                bNodeTreated = true;
                break;
            }
        }
        if (!bNodeTreated) {//that means that this node falls over no other node
            //so draw it apart, but remember it on the hashtable

            //add to htComputedNodes
            htComputedNodes.put(node, Integer.valueOf(1));
        }
        return vNewPos;
    }

    /**
     * fills farm node tooltip with specific info
     */
    @Override
    public void fillSelectedNodeInfo(rcNode n, HashMap hObjAttrs) {
        hObjAttrs.put("Name", n.UnitName/*+" ("+n.IPaddress+")"*/);
        String sDescription = "Grid: " + n.CITY;
        sDescription += "\n" + n.shortName;
        hObjAttrs.put("Description", sDescription);
    }

    /**
     * links functions
     */

    /**
     * computes and draws links; recomputes color and, if links invalidated or link without diplay list,
     * recomputes diplay list for link
     */
    @Override
    public void computeLinks(GL2 gl, Object[] graphicalAttrs) {
        //FIXME: because only egee/lcg has links, if the lcg button is off, don't try to draw the links
        //TODO: remove this fix or correct it when other grids will have links
        if (!((GridsJoglPanel) JoglPanel.globals.mainPanel).jtbLCG.isSelected()) {
            return;
        }

        //check to see if all links are invalidated, so to recompute them
        boolean bInvalidateLinks = (((Hashtable) graphicalAttrs[0]).remove("InvalidateLinks") != null);
        Object obj;

        //get radius
        obj = ((Hashtable) graphicalAttrs[0]).get("NodeRadius");
        if (obj == null) {
            return;
        }
        float radius = ((Float) obj).floatValue();

        //global variables needed for drawing nodes, get them now to avoid concurency on them later
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

                //recompute color
                obj = hLinkAttrs.get("LinkColor");
                if (obj == null) {
                    colorLink = new float[3];
                } else {
                    colorLink = (float[]) obj;
                }
                //switch source with destination of node to correctly represent packages flow
                ns = (rcNode) hLinkAttrs.get("toNode");
                if (ns == null) {
                    continue;
                }
                nw = (rcNode) hLinkAttrs.get("fromNode");
                if (nw == null) {
                    continue;
                }

                int lw = 1;
                int col = 3;
                if ((ns.myID == 0) || (nw.myID == 0)) {
                    lw = 3;
                    col = 0;
                } else if ((ns.myID == 1) || (nw.myID == 1)) {
                    lw = 2;
                    col = 1;
                } else if ((ns.myID == 2) || (nw.myID == 2)) {
                    col = 2;
                }
                //System.out.println("connecting "+ns.UnitName+" with "+nw.UnitName);
                //if nordu grid, select corresponding color and link width
                if ((ns.myID == 1) && (nw.myID == 1) && (ns.errorCount == -2) && (nw.errorCount == -2)) {
                    lw = 2;
                    colorLink[0] = 120 / 255f;
                    colorLink[1] = 196 / 255f;
                    colorLink[2] = 136 / 255f;
                } else {

                    Color c;
                    //				if(((Double)link.data).doubleValue() == 1.0)
                    //compute color for link based on colors of end nodes
                    int r, g, b;
                    r = Color.CYAN.getRed() + (((Color.BLUE.getRed() - Color.CYAN.getRed()) * col) / 3);
                    g = Color.CYAN.getGreen() + (((Color.BLUE.getGreen() - Color.CYAN.getBlue()) * col) / 3);
                    b = Color.CYAN.getBlue() + (((Color.BLUE.getBlue() - Color.CYAN.getGreen()) * col) / 3);
                    c = new Color(r, g, b);//new Color(0xff4444);//Color.RED;
                    //				else {
                    //				c = ((FarmsJoglPanel)JoglPanel.globals.mainPanel).optPan.csPing.getColor(link.speed);
                    //				if ( c == null )
                    //				continue;
                    //				}
                    colorLink[0] = c.getRed() / 255f;
                    colorLink[1] = c.getGreen() / 255f;
                    colorLink[2] = c.getBlue() / 255f;
                }

                hLinkAttrs.put("LinkWidth", Integer.valueOf(lw));
                //colorLink = getLinkQualityColor(link, colorLink);
                if (obj == null) {
                    hLinkAttrs.put("LinkColor", colorLink);
                }
                //check coordinates for source and destination nodes to see if any changes, and, if so
                //update values for link and recompute it
                float srcLat, srcLong, dstLat, dstLong;
                boolean bInvalidateThisLink = false;
                srcLat = DataGlobals.failsafeParseFloat(ns.LAT, -21.22f);
                srcLong = DataGlobals.failsafeParseFloat(ns.LONG, -111.15f);
                dstLat = DataGlobals.failsafeParseFloat(nw.LAT, -21.22f);
                dstLong = DataGlobals.failsafeParseFloat(nw.LONG, -111.15f);
                if ((srcLat != link.fromLAT) || (srcLong != link.fromLONG) || (dstLat != link.toLAT)
                        || (dstLong != link.toLONG)) {
                    //update link's start and end coordinates
                    link.fromLAT = srcLat;
                    link.fromLONG = srcLong;
                    link.toLAT = dstLat;
                    link.toLONG = dstLong;
                    //invalidate this link
                    bInvalidateThisLink = true;
                }

                //compute number of steps
                int total_steps = 0;
                double Max = 10, val = 5;
                total_steps = MIN_SPEED_Link_SEGMENT_STEPS
                        + (Max > 0 ? (int) (((MAX_SPEED_Link_SEGMENT_STEPS - MIN_SPEED_Link_SEGMENT_STEPS) * val) / Max)
                                : 0);
                if (total_steps > MIN_SPEED_Link_SEGMENT_STEPS) {
                    total_steps = MIN_SPEED_Link_SEGMENT_STEPS;
                }
                if (total_steps < MAX_SPEED_Link_SEGMENT_STEPS) {
                    total_steps = MAX_SPEED_Link_SEGMENT_STEPS;
                }
                //System.out.println("total_steps="+total_steps);
                hLinkAttrs.put("LinkSegmentSteps", Integer.valueOf(total_steps));

                //if neccessary, recompute display list
                obj = hLinkAttrs.get("LinkID");
                if ((obj != null) && (bInvalidateLinks || bInvalidateThisLink)) {//for that, delete old id
                    gl.glDeleteLists(((Integer) obj).intValue(), 1);
                    obj = null;
                }
                ;
                if (obj == null) {
                    linkID = gl.glGenLists(1);
                    //System.out.println("linkID generated: "+linkID);
                    gl.glNewList(linkID, GL2.GL_COMPILE);

                    //					JoGLArc3D arc3D = new JoGLArc3D(link.fromLAT, link.fromLONG, link.toLAT, link.toLONG, globeRad, 10, 0.02, -globeRad+globeVirtRad);
                    //					arc3D.drawArc(gl);
                    JoGLArc3D Darc3D;
                    Darc3D = new JoGLArc3D(link.fromLAT, link.fromLONG, link.toLAT, link.toLONG, globeRad, 10, 0.05,
                            -globeRad + globeVirtRad/*, 0.2*/);
                    ArrayList alArcPoints = new ArrayList();
                    Darc3D.setPoints(alArcPoints);
                    Darc3D.drawArc(gl);
                    gl.glEndList();
                    hLinkAttrs.put("LinkID", Integer.valueOf(linkID));
                    hLinkAttrs.put("LinkPoints", alArcPoints);
                    //					hLinkAttrs.put("LinkArrowBase", Darc3D.vPointFrom);
                    //System.out.println("point from: "+Darc3D.vPointFrom);
                    //System.out.println("point to: "+Darc3D.vPointTo);
                    //					VectorO vPointDir = VectorO.SubstractVector( Darc3D.vPointTo, Darc3D.vPointFrom);
                    //					vPointDir.Normalize();
                    //					VectorO VzAxis = new VectorO(0,0,1);
                    //					VectorO vRotAxis = VzAxis.CrossProduct(vPointDir);
                    //					// rotate z to Vdir around vectorial product with dot product
                    //					float angleRotation = (float)(Math.acos(VzAxis.DotProduct(vPointDir))* 180 / Math.PI);
                    //					hLinkAttrs.put( "LinkArrowRotationAngle", new Float(angleRotation));
                    //					hLinkAttrs.put("LinkArrowRotationAxis", vRotAxis);
                } else {
                    linkID = ((Integer) obj).intValue();
                }

                //draw the link
                //colorLink = (float[])hLinkAttrs.get("LinkColor");
                //				gl.glColor3fv(colorLink, 0);
                //				gl.glCallList( linkID);
                gl.glLineWidth(lw);
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

                //draw arrow on link
                if (conID > 0) {//if there is an object to be drawn
                    gl.glPushMatrix();
                    try {
                        VectorO vLinkArrowBase = (VectorO) hLinkAttrs.get("LinkArrowBase");
                        float fLinkArrowRotationAngle = ((Float) hLinkAttrs.get("LinkArrowRotationAngle")).floatValue();
                        VectorO vLinkArrowRotationAxis = (VectorO) hLinkAttrs.get("LinkArrowRotationAxis");

                        //operations are put in inverse order because last is first executed in opengl
                        //3. position object cu correct coordinates
                        //System.out.println("link arrow base: "+vLinkArrowBase);
                        gl.glTranslatef(vLinkArrowBase.getX(), vLinkArrowBase.getY(), vLinkArrowBase.getZ());
                        //2. operation: rotate from (0,0,1) to Vdir
                        //System.out.println("link arrow rotation axis: "+vLinkArrowRotationAxis);
                        gl.glRotatef(fLinkArrowRotationAngle, vLinkArrowRotationAxis.getX(),
                                vLinkArrowRotationAxis.getY(), vLinkArrowRotationAxis.getZ());
                        //1. operation: scale to radius dimmensions:
                        gl.glScalef(radius / 2, radius / 2, radius / 2);
                        //use already constructed cap as display list
                        //draw it at (0,0,0) in self reference system
                        gl.glCallList(conID);
                    } catch (Exception ex) {
                        //						logger.log(Level.INFO, "Exception drawing arrow for link: "+ex.getMessage());
                        ex.printStackTrace();
                    }
                    gl.glPopMatrix();
                }
                ;
            }
        } catch (Exception ex) {
            //			logger.log(Level.INFO, "Exception computing links: "+ex.getMessage());
            ex.printStackTrace();
        }

        /** draw packages for links */
        drawPackages(gl, radius);

    }

    /**
     *  draws links, if their attributes are computed, and invalidate links is not set
     */
    @Override
    public void drawLinks(GL2 gl, Object[] graphicalAttrs) {
        //QUICKFIX: because only egee/lcg has links, if the lcg button is off, don't try to draw the links
        //TODO: remove this fix or correct it when other grids will have links
        if (!((GridsJoglPanel) JoglPanel.globals.mainPanel).jtbLCG.isSelected()) {
            return;
        }

        //get radius
        Object obj;
        obj = ((Hashtable) graphicalAttrs[0]).get("NodeRadius");
        if (obj == null) {
            return;
        }
        float radius = ((Float) obj).floatValue();

        ILink link;
        HashMap hLinkAttrs;
        float[] colorLink;
        int linkID;
        try {
            for (Iterator it = ((HashMap) graphicalAttrs[2]).keySet().iterator(); it.hasNext();) {
                link = (ILink) it.next();
                hLinkAttrs = (HashMap) ((HashMap) graphicalAttrs[2]).get(link);
                //draw the link
                obj = hLinkAttrs.get("LinkWidth");
                if (obj == null) {
                    continue;//skip to next one
                }
                double line_width = ((Integer) obj).intValue();
                obj = hLinkAttrs.get("LinkColor");
                if (obj == null) {
                    continue;//skip to next one
                }
                colorLink = (float[]) obj;
                obj = hLinkAttrs.get("LinkID");
                if (obj == null) {
                    continue;//skip to next one
                }
                linkID = ((Integer) obj).intValue();
                gl.glLineWidth((float) line_width);
                gl.glColor3fv(colorLink, 0);
                gl.glCallList(linkID);
                gl.glLineWidth(1f);

                //draw arrow on link
                if (conID > 0) {//if there is an object to be drawn
                    gl.glPushMatrix();
                    try {
                        VectorO vLinkArrowBase = (VectorO) hLinkAttrs.get("LinkArrowBase");
                        float fLinkArrowRotationAngle = ((Float) hLinkAttrs.get("LinkArrowRotationAngle")).floatValue();
                        VectorO vLinkArrowRotationAxis = (VectorO) hLinkAttrs.get("LinkArrowRotationAxis");

                        //operations are put in inverse order because last is first executed in opengl
                        //3. position object cu correct coordinates
                        gl.glTranslatef(vLinkArrowBase.getX(), vLinkArrowBase.getY(), vLinkArrowBase.getZ());
                        //2. operation: rotate from (0,0,1) to Vdir
                        gl.glRotatef(fLinkArrowRotationAngle, vLinkArrowRotationAxis.getX(),
                                vLinkArrowRotationAxis.getY(), vLinkArrowRotationAxis.getZ());
                        //1. operation: scale to radius dimmensions:
                        gl.glScalef(radius / 2, radius / 2, radius / 2);
                        //use already constructed cap as display list
                        //draw it at (0,0,0) in self reference system
                        gl.glCallList(conID);
                    } catch (Exception ex) {
                        //						logger.log(Level.INFO, "Exception drawing arrow for link: "+ex.getMessage());
                        ex.printStackTrace();
                    }
                    gl.glPopMatrix();
                }
                ;
            }
        } catch (Exception ex) {
            //			logger.log(Level.INFO, "Exception drawing links: "+ex.getMessage());
            ex.printStackTrace();
        }

        /** draw packages for links */
        drawPackages(gl, radius);
    }

    /**
     * draws packages for each link
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
        float[] colorLink;
        try {
            //for each link
            for (Iterator it = hLinks.keySet().iterator(); it.hasNext();) {
                Object objLink = it.next();
                if (!(objLink instanceof ILink)) {
                    continue;
                }
                link = (ILink) objLink;
                hLinkAttrs = (HashMap) hLinks.get(link);
                obj = hLinkAttrs.get("LinkColor");
                if (obj == null) {
                    continue;//skip to next one
                }
                colorLink = (float[]) obj;
                gl.glColor3fv(colorLink, 0);
                //				System.out.println("<mluc> draw packages for link "+link);
                //get packages vector
                Vector vPackages = null;
                obj = hLinkAttrs.get("PackagesVector");
                if ((obj != null) && (obj instanceof Vector)) {
                    vPackages = (Vector) obj;
                } else {
                    continue;
                }
                //if one available, draw packages
                LinkPackage aPackage;
                for (int i = 0; i < vPackages.size(); i++) {
                    aPackage = (LinkPackage) vPackages.get(i);
                    //					System.out.println("<mluc> draw package "+aPackage.sDescription+" for link "+link);
                    gl.glPushMatrix();
                    try {
                        //operations are put in inverse order because last is first executed in opengl
                        //3. position object cu correct coordinates
                        gl.glTranslatef(aPackage.vPosition.getX(), aPackage.vPosition.getY(), aPackage.vPosition.getZ());
                        //2. operation: rotate from (0,0,1) to Vdir
                        //						gl.glRotatef( aPackage.fRotAngle, aPackage.vRotAxis.getX(), aPackage.vRotAxis.getY(), aPackage.vRotAxis.getZ());
                        //						gl.glRotatef( aPackage.fRotAngle2, 0, 0, 1);
                        //1. operation: scale to radius dimmensions:
                        gl.glScalef(radius * aPackage.fraction_radius, radius * aPackage.fraction_radius, radius
                                * aPackage.fraction_radius);
                        //use already constructed cap as display list
                        //draw it at (0,0,0) in self reference system
                        gl.glCallList(packageID);
                    } catch (Exception ex) {
                        System.out.println("Exception drawing package for link: " + aPackage.sDescription
                                + ". Package may be during initialisation. Ignore it.");
                        ex.printStackTrace();
                    }
                    gl.glPopMatrix();
                }
                ;
            }
        } catch (Exception ex) {
            System.out.println("Exception drawing graphical attributes for wan links: " + ex.getMessage());
            ex.printStackTrace();
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
        if (link instanceof ILink) {
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

    /************************************************
     * Other functions
     ************************************************/

    @Override
    public ArrayList getOtherSelectedObjects(VectorO vEyePosition, VectorO vDirection, float radius,
            ArrayList alSelectedObjects) {
        return alSelectedObjects;
    }

    /**
     * changes the subview mode, meaning that the position of same objects
     * is a little bit changed, but the data scene remains the same...
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

    //first boolean shows that animation requested to start
    //second shows that animation is under way
    private volatile boolean bAnimateLink = false;
    /** this indicates that the (re)computation for links animation was done and the drawing can procede */
    private volatile boolean bAnimationRecomputed = false;
    private boolean bAnimateLinkTimer = false;
    //number of steps for a package to make to slide along the segment
    //MIN is ALWAYS greater than MAX
    public static final int MIN_SPEED_Link_SEGMENT_STEPS = 50;
    public static final int MAX_SPEED_Link_SEGMENT_STEPS = 10;

    /** reference to links from DataRenderer.graphicalAttributes[2]; value set right after creation */
    private HashMap hLinks;

    /**
     * a package to be drawn on a link
     * @author mluc
     *
     */
    protected class LinkPackage {
        public float fraction_radius;//determined by data field of link and maximal capacity of the link
        public VectorO vPosition;//center of cube that represents the package where it should be drawn
        //these two should be computed somehow
        VectorO vRotAxis;//rotation axis
        float fRotAngle;//rotation angle
        VectorO vRotAxis2;//perpendicular rotation axis ( is z )
        float fRotAngle2;//rotation angle in plane (xy)
        public String sDescription;//informations about the package

        public LinkPackage() {
        }//empty constructor to have something...
    }

    public boolean getLinkAnimationStatus() {
        return bAnimateLink;
    }

    public void changeLinkAnimationStatus(final boolean bAnimate) {
        bAnimateLink = bAnimate;
        if (bAnimateLink && !bAnimateLinkTimer) {
            //if animation is on and there is no animation timer started, start it
            BackgroundWorker.schedule(new TimerTask() {
                @Override
                public void run() {
                    Thread.currentThread().setName(" ( ML ) - JOGL - GridsNodesRenderer - Link animation Timer Thread");
                    bAnimateLinkTimer = true;
                    /**
                     * action to be completed here:<br>
                     * update each Link link's data packets position based on link's
                     * segments, so that each segment has one packet, with different sizes.<br>
                     * A package is defined by a position vector, a dimension/radius, determined by
                     * the size of data it carries, and a description including source node, destination node,
                     * link's details and current data size.<br>
                     * A Link link must have among its attributes a list of vectors that determine the segments.
                     */
                    if (!bGlobeTransformIsOn) {
                        try {
                            ILink link;
                            HashMap hLinkAttrs;
                            Object obj;
                            //	for each Link link
                            for (Iterator it = hLinks.keySet().iterator(); it.hasNext();) {
                                Object objLink = it.next();
                                if (!(objLink instanceof ILink)) {
                                    continue;
                                }
                                link = (ILink) objLink;
                                hLinkAttrs = (HashMap) hLinks.get(link);
                                //								System.out.println("animate link"+link);

                                //get max steps per segment
                                obj = hLinkAttrs.get("LinkSegmentSteps");
                                if ((obj == null) || !(obj instanceof Integer)) {
                                    continue;//no number of steps, so ignore it, it hasn't been computed
                                }
                                int total_steps = ((Integer) obj).intValue();
                                if (total_steps == 0) {
                                    continue;//no number of steps, so ignore it, it hasn't been computed
                                }
                                //								System.out.println("total steps: "+total_steps);
                                //get points that form the link
                                obj = hLinkAttrs.get("LinkPoints");
                                if ((obj == null) || !(obj instanceof ArrayList)) {
                                    continue;//no points, so ignore it, it hasn't been computed
                                }
                                ArrayList alPoints = (ArrayList) obj;
                                //								System.out.println("number of points: "+alPoints.size());
                                //get old current step
                                int current_step = 0;
                                obj = hLinkAttrs.get("LinkSegmentCurrentStep");
                                if ((obj != null) && (obj instanceof Integer)) {
                                    current_step = ((Integer) obj).intValue() + 1;
                                }
                                if (current_step < 0) {
                                    current_step = 0;
                                }
                                if (current_step >= total_steps) {
                                    current_step = 0;
                                }
                                //save new current step
                                hLinkAttrs.put("LinkSegmentCurrentStep", Integer.valueOf(current_step));
                                //								System.out.println("current step: "+current_step);
                                //get list of packages
                                //if vector doesn't exist, create it
                                Vector vPackages = null;
                                obj = hLinkAttrs.get("PackagesVector");
                                if ((obj != null) && (obj instanceof Vector)) {
                                    vPackages = (Vector) obj;
                                } else {
                                    vPackages = new Vector();
                                    hLinkAttrs.put("PackagesVector", vPackages);
                                }
                                ;
                                //								System.out.println("number of packages: "+vPackages.size());
                                //first, take in consideration the fact that the points may have changed,
                                //so try to remove excedentary packages
                                //so that, vPackages.size = alPoints.size-1
                                while (vPackages.size() >= alPoints.size()) {
                                    vPackages.remove(vPackages.size() - 1);
                                }
                                //if current step is 0, a new package is to be create
                                if (current_step == 0) {
                                    //if number of packages is equal with number of points-1 in link, remove last package and
                                    //use it as first
                                    //else, add a new package
                                    //removing last excedentary to be package
                                    obj = null;
                                    LinkPackage newPackage = null;
                                    if (vPackages.size() == (alPoints.size() - 1)) {
                                        obj = vPackages.remove(vPackages.size() - 1);
                                    }
                                    //obtain a new package
                                    if ((obj != null) && (obj instanceof LinkPackage)) {
                                        newPackage = (LinkPackage) obj;
                                    } else {
                                        newPackage = new LinkPackage();
                                    }
                                    //set some neccessary attributes
                                    double data = 0;
                                    newPackage.fraction_radius = 0f;
                                    double Max = 0;
                                    //									try {
                                    //	Hashtable hWANOptions = (Hashtable)OptionsPanelValues.get("wan");
                                    //									Max = ((Double)hWANOptions.get( "MaxValue")).doubleValue();
                                    //									} catch(Exception ex) {Max=0;}
                                    if ((link.data != null) && (link.data instanceof Double)
                                            && ((data = ((Double) (link.data)).doubleValue()) != 0)
                                            && ((data < Max) && (data < link.speed)) && (Max > 0)) {
                                        newPackage.fraction_radius = (float) (data / Max/*link.speed*/);
                                    }
                                    newPackage.fraction_radius = .8f + (.4f * newPackage.fraction_radius);//if no data available, use a standard size
                                    //									System.out.println("data="+data+"Max="+Max);
                                    newPackage.sDescription = "Link Package: " + link.name;
                                    //									+ " = " + (int)data
                                    //									+ "/" + (int)link.speed
                                    //									+ " Mbps -> " + (link.speed>0?""+(int)(data*100/link.speed):"???")
                                    //									+ "%";
                                    //add the new package
                                    vPackages.add(0, newPackage);
                                    //									System.out.println("<mluc> add package "+newPackage.sDescription+" for link "+link);
                                }
                                ;
                                //System.out.println("vPackages.size="+vPackages.size()+" and alPoints.size="+alPoints.size());
                                //traverse the vector of packages and position packages on the map
                                LinkPackage aPackage;
                                //int nPoints = alPoints.size();
                                VectorO vStart, vEnd, vDir;
                                for (int i = 0; i < vPackages.size(); i++) {
                                    //get start and end points that compose the segment
                                    vStart = (VectorO) alPoints.get(i);
                                    vEnd = (VectorO) alPoints.get(i + 1);//nPoints-i-1);//(i+2)%nPoints);
                                    aPackage = (LinkPackage) vPackages.get(i);
                                    //compute vector direction from start to end:
                                    vDir = VectorO.SubstractVector(vEnd, vStart);
                                    //multiply vDir to current step from total steps to go to correct position
                                    vDir.MultiplyScalar(current_step / (double) total_steps);
                                    //get absolute position for point by adding start point
                                    vDir.AddVector(vStart);
                                    aPackage.vPosition = vDir;
                                }
                            }
                            ;
                            bAnimationRecomputed = true;
                            JoglPanel.globals.canvas.repaint();
                        } catch (Exception ex) {
                            System.out.println("Exception during animating packages movement: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }
                    //this is not synchronized, so it may happen that animation stayes stopped although the
                    //animation checkbox is checked, or stay running although the checkbox is unselected...
                    if (!bAnimateLink) {
                        bAnimateLinkTimer = false;
                        cancel();
                    }
                    ;
                }
            }, 100, 200);//check animation at each 100 miliseconds
        }
    }

    /**
     * makes available the map of links from DataRenderer
     * for the packages list
     * @author mluc
     * @since Nov 12, 2006
     * @param links
     */
    public void setLinks(HashMap links) {
        hLinks = links;
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
