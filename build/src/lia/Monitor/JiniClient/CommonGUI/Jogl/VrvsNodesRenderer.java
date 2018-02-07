/*
 * Created on 12.06.2004 21:22:04
 * Filename: FarmNodesRenderer.java
 *
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;

import lia.Monitor.JiniClient.CommonGUI.DLink;
import lia.Monitor.JiniClient.CommonGUI.DoubleContainer;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.VRVS3D.JoptPan;
import lia.Monitor.JiniClient.VRVS3D.MeetingDetails;
import lia.Monitor.JiniClient.VRVS3D.VrvsSerMonitor;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ILink;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MNode;
import net.jini.core.lookup.ServiceID;

/**
 * @author Luc<br>
 * <br>
 * FarmNodesRenderer<br>
 * implementation of NodesRendererInterface that proposes a visualisation model for
 * the nodes and asociated links as cups
 */
public class VrvsNodesRenderer extends AbstractNodesRenderer {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(VrvsNodesRenderer.class.getName());

    //	public int nodesShow = 1;         // what do the bubbles are = one of NODE_xxx constants
    //public int peersShow = 11;         // what shows the color of links = one of the LINK_xxx constants

    private int capID = 0;//compiled openGL object to be used to draw each node
    private int circleID = 0; //compiled openGL object that represents a circle centered in (0,0,0), with unit radius and positioned in (x,y) plane
    private int conID = 0; //compiled openGL object to be used to draw each arrow for a link

    private final int nLevels;//levels of detail for drawing a cap as several spherical segments drawn one on top of another
    private int nMaxPoints = 32;//total number of points to be drawn, is another mesurement of level of detail

    /**
     * switches between the two existing views:<br>
     *  - normal, where farms can overlap, and<br>
     *  - on top, where overlapping farms are stacked one above the other
     */
    private boolean bOnTopView = false;

    private final HashMap mstAttrs = new HashMap();

    /**
     * levels represents the number of fragments to be drawn to top
     */
    public VrvsNodesRenderer(int levels, int max_points) {
        nLevels = levels;
        nMaxPoints = max_points;
        pan = new float[nLevels][3];
        pn = new float[nLevels][3];

        //TODO: change to use options hashtable
        //inititial values to be used for computation of node's value
        NodeMinValue = 0.0;
        NodeMaxValue = 100.0;
        NodeMinColor = Color.CYAN;
        NodeMaxColor = Color.BLUE;

        OptionsPanelValues = new Hashtable();

        subViewCapabilities = new String[] { "Normal view", "OnTop view" };

        //put default values for wan links
        Hashtable hPeersOptions = new Hashtable();
        hPeersOptions.put("MinValue", Double.valueOf(0));
        hPeersOptions.put("MaxValue", Double.valueOf(100));
        hPeersOptions.put("MinColor", Color.RED);
        hPeersOptions.put("MaxColor", Color.GREEN);
        OptionsPanelValues.put("peers", hPeersOptions);

        //put default values for ping links
        Hashtable hPingOptions = new Hashtable();
        hPingOptions.put("MinValue", Double.valueOf(0));
        hPingOptions.put("MaxValue", Double.valueOf(0));
        hPingOptions.put("MinColor", Color.RED);
        hPingOptions.put("MaxColor", Color.RED);
        OptionsPanelValues.put("ping", hPingOptions);
    }

    /**
     * draws the farms nodes as cups
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
        Color[] colors;
        int components;
        int nodeID;
        for (Iterator it = ((HashMap) graphicalAttrs[1]).keySet().iterator(); it.hasNext();) {
            node = (rcNode) it.next();
            //save the matrix
            gl.glPushMatrix();
            try {
                obj = ((HashMap) graphicalAttrs[1]).get(node);
                if (obj != null) {//this node has its attributes computed, so draw it
                    hAttrs = (HashMap) obj;
                    vectors = (VectorO[]) hAttrs.get("PositioningVectors");
                    obj = hAttrs.get("NodeID");
                    if (obj == null) {
                        continue;//skip to next one
                    }
                    nodeID = ((Integer) obj).intValue();
                    colors = (Color[]) hAttrs.get("NodeColors");
                    components = colors.length;
                    //					color = (Color)hAttrs.get("NodeColor");
                    //draw components
                    //draw node
                    if ((nodeID > 0) && (vectors != null)) {
                        if (bOnTopView) {
                            vNewPos = recomputeVectors((((HashMap) graphicalAttrs[1])), htComputedNodes, node, vectors,
                                    radius);
                        } else {
                            vNewPos = vectors[1];
                        }
                        if (components == 1) {
                            gl.glColor3f(colors[0].getRed() / 255f, colors[0].getGreen() / 255f,
                                    colors[0].getBlue() / 255f);
                            //                            System.out.println("colors[0]="+colors[0]);
                        }
                        ;
                        drawNode(gl, vectors[0], vNewPos, nodeID, radius);
                    }
                    ;
                    //					if ( color!=null && vectors!=null )
                    //					    drawNode( gl, vectors[0], vectors[1], color, radius);
                    //draw node links to other nodes
                }
            } catch (Exception ex) {
                logger.log(Level.INFO, "Could not draw node " + (node == null ? "null" : node.UnitName) + " exception:"
                        + ex.getMessage());
                ex.printStackTrace();
            }
            //pop the matrix
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
        //get radius
        Object obj;
        obj = ((Hashtable) graphicalAttrs[0]).get("NodeRadius");
        if (obj == null) {
            return;
        }
        float radius = ((Float) obj).floatValue();

        boolean bMeetings = (((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.nodesShow == JoptPan.NODE_MEETINGS);
        ArrayList<MeetingDetails.IDColor> lMeetingsSelected = new ArrayList<MeetingDetails.IDColor>();
        //if option selected from combo is NODE_MEETINGS, then get snapshot of meetings
        if (bMeetings) {
            lMeetingsSelected = ((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.meetingsUpdate.getSnapshot();
            //for ( int i=0; i<lMeetingsSelected.size(); i++)
            //System.out.println("lMeetingsSelected["+i+"]={"+((Object[])lMeetingsSelected.get(i))[0]+","+((Object[])lMeetingsSelected.get(i))[1]+"}");
        } else {
            //otherwise compute colors
            //compute maximal values
            computeNodesColors(((HashMap) graphicalAttrs[1]));
        }

        HashMap htComputedNodes = new HashMap();//used for ontop view

        rcNode node;
        Color vrvsColor;
        int nodeID;
        HashMap hAttrs;
        float[] fractions;
        Color[] colors;
        VectorO vNewPos;
        ArrayList al = new ArrayList();
        for (Iterator it = ((HashMap) graphicalAttrs[1]).keySet().iterator(); it.hasNext();) {
            node = (rcNode) it.next();
            al.clear();
            //save the matrix
            gl.glPushMatrix();
            try {
                if (bMeetings) {
                    //compute for current node list of meetings to show
                    if ((node.client != null) && (node.client.farm != null)) {
                        //for each node, check its meetings cluster
                        MCluster clusterMeetings = node.client.farm.getCluster("Meetings");
                        if (clusterMeetings != null) {
                            Vector clMList = clusterMeetings.getNodes();
                            if (clMList.size() > 0) {
                                for (int i = 0; i < clMList.size(); i++) {
                                    //find meeting id
                                    String meetingFullName = ((MNode) clMList.get(i)).getName();
                                    int nSep = meetingFullName.indexOf(MNode_PoundSign);
                                    String meetingID = "";
                                    if (nSep != -1) {
                                        meetingID = meetingFullName.substring(nSep + 1);
                                    }
                                    if (meetingID.length() > 0) {
                                        //System.out.println("checking meetingid="+meetingID+" for "+node.UnitName);
                                        for (final MeetingDetails.IDColor elem : lMeetingsSelected) {
                                            if (meetingID.equals(elem.ID)) {
                                                //node's meeting is found in list of selected meetings, so show it
                                                al.add(elem.color);//add color
                                                //al.add(new Color( 255-((Color)elem[1]).getRed(), 255-((Color)elem[1]).getGreen(), 255-((Color)elem[1]).getBlue() ));//add color
                                                //al.add(new Color( 255-((Color)elem[1]).getGreen(), 255-((Color)elem[1]).getRed(), 255-((Color)elem[1]).getBlue() ));//add color
                                            }
                                        }
                                    }
                                }//end for each entry in meetings cluster list
                            }//end if cluster list length greater than zero
                        }
                        ;//end if cluster meetings exists
                    }
                    ;//end if node has clusters
                    if (al.size() == 0) {
                        al.add(Color.WHITE);
                    }
                } else {
                    if (node.errorCount > 0) {
                        vrvsColor = Color.RED;
                    } else {
                        vrvsColor = Color.PINK;
                        //Color c = null;
                        if ((node.haux.get("ErrorKey") != null) && node.haux.get("ErrorKey").equals("1")) {
                            vrvsColor = Color.PINK;
                        }
                        if ((node.haux.get("lostConn") != null) && node.haux.get("lostConn").equals("1")) {
                            vrvsColor = Color.RED;
                        }
                        Color cc = getNodeColor(node);
                        if (cc != null) {
                            vrvsColor = cc;
                        }
                    }
                    al.add(vrvsColor);
                }
                //end if meetings option is selected
                obj = ((HashMap) graphicalAttrs[1]).get(node);
                if (obj != null) {//this node has attributes , so consider it
                    hAttrs = (HashMap) obj;
                    //determin components
                    int nSize = al.size();
                    //determin components
                    fractions = (float[]) hAttrs.get("NodeFractions");
                    if ((fractions == null) || (fractions.length != nSize)) {
                        fractions = new float[nSize];
                        hAttrs.put("NodeFractions", fractions);
                    }
                    ;
                    colors = (Color[]) hAttrs.get("NodeColors");
                    if ((colors == null) || (colors.length != nSize)) {
                        colors = new Color[nSize];
                        hAttrs.put("NodeColors", colors);
                    }
                    ;
                    float fStep = 1.0f / nSize;
                    //fill vectors
                    for (int i = 0; i < nSize; i++) {
                        //ignore the fact that sum of fractions may be smaller than 1.0f
                        fractions[i] = fStep;
                        colors[i] = (Color) al.get(i);
                    }
                    //create display list for node
                    //if neccessary, recompute display list
                    int components = fractions.length;
                    obj = hAttrs.get("NodeID");
                    if (obj != null) {//for that, delete old id
                        nodeID = ((Integer) obj).intValue();
                        if (nodeID != capID) {
                            gl.glDeleteLists(nodeID, 1);
                        }
                        ;
                        //if ( components!=1 )
                        obj = null;
                    }
                    ;
                    if (obj == null) {//each time reconstruct the node...?
                        if (components == 1) {
                            nodeID = capID;
                        } else {
                            nodeID = gl.glGenLists(1);
                            //System.out.println("linkID generated: "+linkID);
                            gl.glNewList(nodeID, GL2.GL_COMPILE);

                            //construct node
                            constructNode(gl, components, fractions, colors);

                            gl.glEndList();
                        }
                        ;
                        hAttrs.put("NodeID", Integer.valueOf(nodeID));
                    } else {
                        nodeID = ((Integer) obj).intValue();
                    }
                    VectorO[] vectors;
                    vectors = (VectorO[]) hAttrs.get("PositioningVectors");
                    if (bOnTopView) {
                        vNewPos = recomputeVectors(((HashMap) graphicalAttrs[1]), htComputedNodes, node, vectors,
                                radius);
                    } else {
                        vNewPos = vectors[1];
                    }
                    if (components == 1) {
                        gl.glColor3f(colors[0].getRed() / 255f, colors[0].getGreen() / 255f, colors[0].getBlue() / 255f);
                    }
                    drawNode(gl, vectors[0], vNewPos, nodeID, radius);
                    //draw node
                    //drawNode( gl, vectors[0], vectors[1], fractions, colors, radius);
                    //compute links and then draw them
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Could not compute and draw node " + (node == null ? "null" : node.UnitName)
                        + " ex:" + ex.getMessage());
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

        /*        Object link;
                HashMap hLinkAttrs;
                Iterator it;
                try {
                    for( it = ((HashMap)graphicalAttrs[2]).keySet().iterator(); it.hasNext(); ) {
                        link = it.next();
                        hLinkAttrs = (HashMap)((HashMap)graphicalAttrs[2]).get( link);
                        hLinkAttrs.remove("LinkID");
                    };
                    for( it = wanAttrs.keySet().iterator(); it.hasNext(); ) {
                        link = it.next();
                        hLinkAttrs = (HashMap)wanAttrs.get( link);
                        //remove display list, as it became invalid
                        hLinkAttrs.remove("LinkID");
                    };
                    for( it = osAttrs.keySet().iterator(); it.hasNext(); ) {
                        link = it.next();
                        hLinkAttrs = (HashMap)osAttrs.get( link);
                        hLinkAttrs.remove("LinkID");
                    };
                } catch(Exception ex) {
                    logger.log( Level.INFO, "Exception removing invalid links");
                    ex.printStackTrace();
                }
         */
        capID = 0;
        circleID = 0;
        conID = 0;
        if (capID == 0) {
            capID = gl.glGenLists(1);
            gl.glNewList(capID, GL2.GL_COMPILE);
            {
                //direction of node (up), on z axis
                VectorO Vdir = new VectorO(0, 0, 1);
                //horizontal vector, that means in plane perpendicular on Vdir,
                //this vector rotates itself around Vdir
                VectorO Voriz = new VectorO(1, 0, 0);
                int points;//number of points to be drawn for a fraction 0-32
                float curr_Tangle = 0f;//current total angle of drawn cilinder
                float angleOriz;//distance in grades between two consecutive points - depends on current component to be drawn
                float angleVert;//vertical angle between two consecutive points
                //compute start points
                //vertical vector, that is the vector that rotates from horizontal position (Voriz)
                //to Vdir
                VectorO Vvert = new VectorO(Voriz);
                //compute angleVert
                angleVert = 90f / (nLevels + 1);
                //for rotation, I need the rotating axis, and that is CrossProduct between
                //Vdir and Voriz
                VectorO VvertRotAxis = new VectorO(0, -1, 0);//Voriz.CrossProduct(Vdir);
                //compute each point from horizontal to vertical, minus the first and the last
                for (int k = 0; k < nLevels; k++) {
                    pn[k][0] = Vvert.getX();
                    pn[k][1] = Vvert.getY();
                    pn[k][2] = Vvert.getZ();
                    Vvert.Rotate(VvertRotAxis, angleVert);
                }

                points = nMaxPoints;
                angleOriz = 360f / points;
                //for each point
                for (int j = 0; j < points; j++) {
                    //set previous points, before computing the new ones, after rotation
                    for (int k = 0; k < nLevels; k++) {
                        pan[k][0] = pn[k][0];
                        pan[k][1] = pn[k][1];
                        pan[k][2] = pn[k][2];
                    }
                    ;
                    if ((curr_Tangle + angleOriz) > 360f) {
                        angleOriz = 360f - curr_Tangle;
                    }
                    Voriz.Rotate(Vdir, angleOriz);
                    curr_Tangle += angleOriz;
                    //compute current points
                    VvertRotAxis.Rotate(Vdir, angleOriz);
                    Vvert.duplicate(Voriz);
                    //compute each point from horizontal to vertical, minus the first and the last
                    for (int k = 0; k < nLevels; k++) {
                        pn[k][0] = Vvert.getX();
                        pn[k][1] = Vvert.getY();
                        pn[k][2] = Vvert.getZ();
                        Vvert.Rotate(VvertRotAxis, angleVert);
                    }
                    gl.glBegin(GL.GL_TRIANGLE_STRIP);
                    //top triangle
                    gl.glVertex3f(0, 0, 1);
                    //side triangles
                    for (int k = nLevels - 1; k > 0; k--) {
                        gl.glVertex3fv(pan[k], 0);
                        gl.glVertex3fv(pn[k], 0);
                    }
                    gl.glVertex3fv(pan[0], 0);
                    gl.glVertex3fv(pn[0], 0);

                    gl.glEnd();
                }
                //gl.glEnable( GL.GL_TEXTURE_2D);
            }
            gl.glEndList();
        }
        if (circleID == 0) {
            circleID = gl.glGenLists(1);
            gl.glNewList(circleID, GL2.GL_COMPILE);
            {
                //gl.glDisable( GL.GL_TEXTURE_2D);
                Globals.drawCircle(gl, nMaxPoints, 1.01f);
                //gl.glEnable( GL.GL_TEXTURE_2D);
            }
            gl.glEndList();
        }
        if (conID == 0) {
            int nMaxPoints = 8;
            conID = gl.glGenLists(1);
            gl.glNewList(conID, GL2.GL_COMPILE);
            {
                float faDelta = (2 * (float) Math.PI) / nMaxPoints, faAlfa;
                float fX, fY;
                //draw upper cone
                gl.glBegin(GL.GL_TRIANGLE_FAN);
                //top
                gl.glVertex3f(0, 0, 2);
                //side
                faAlfa = 0.0f;
                for (int slice = 0; slice <= nMaxPoints; slice++) {
                    fX = (float) Math.cos(faAlfa);
                    fY = (float) Math.sin(faAlfa);
                    gl.glVertex3f(fX, fY, 0);
                    faAlfa += faDelta;
                }
                gl.glEnd();

                //draw base circle
                gl.glBegin(GL.GL_TRIANGLE_FAN);
                //top
                gl.glVertex3f(0, 0, 0);
                //side
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

    //    private float[] c1 = new float[3];
    //    private float[] c2 = new float[3];
    //    private float[] pa1 = new float[3];
    //    private float[] pa2 = new float[3];
    //    private float[] p1 = new float[3];
    //    private float[] p2 = new float[3];
    //    private float[] aux = new float[3];
    //    private float fraction = .2f;

    float[][] pan;
    float[][] pn;

    /**
     * draws a node centered in (0,0,0) pointed in z direction, and having unitar dimensions,
     * with all the colors on it
     * @param gl
     * @param components
     * @param fractions
     * @param colors
     * @param radius
     */
    private void constructNode(GL2 gl, int components, float[] fractions, Color[] colors) {
        //direction of node (up), on z axis
        VectorO Vdir = new VectorO(0, 0, 1);
        //horizontal vector, that means in plane perpendicular on Vdir,
        //this vector rotates itself around Vdir
        VectorO Voriz = new VectorO(1, 0, 0);//substitutes Vp
        int points;//number of points to be drawn for a fraction 0-32
        float curr_Tangle = 0f;//current total angle of drawn cap
        float angleOriz;//distance in grades between two consecutive points - depends on current component to be drawn
        float angleVert;//vertical angle between two consecutive points
        //compute start points
        //vertical vector, that is the vector that rotates from horizontal position (Voriz)
        //to Vdir
        VectorO Vvert = new VectorO(Voriz);
        //compute angleVert
        angleVert = 90f / (nLevels + 1);
        //for rotation, I need the rotating axis, and that is CrossProduct between
        //Vdir and Voriz
        VectorO VvertRotAxis = new VectorO(0, -1, 0);//Voriz.CrossProduct(Vdir);
        //compute each point from horizontal to vertical, minus the first and the last
        for (int k = 0; k < nLevels; k++) {
            pn[k][0] = Vvert.getX();
            pn[k][1] = Vvert.getY();
            pn[k][2] = Vvert.getZ();
            Vvert.Rotate(VvertRotAxis, angleVert);
        }
        //        VectorO Vp = new VectorO( 1,0,0);
        //        VectorO Vpos = new VectorO(0,0,0);
        int max_points = 32;//total number of points to be drawn
        //        float angle;//distance in grades between two consecutive points - depends on current component to be drawn
        //compute start points
        //        p1[0] = Vp.getX(); p2[0] = Vp.getX();
        //        p1[1] = Vp.getY(); p2[1] = Vp.getY();
        //        p1[2] = Vp.getZ(); p2[2] = Vp.getZ()+.5f;

        //System.out.println("number of components: "+components);
        for (int i = 0; i < components; i++) {
            points = 1 + (int) (fractions[i] * (max_points - 1));
            angleOriz = (360f * fractions[i]) / points;
            for (int j = 0; j < points; j++) {
                //draw next points
                //set previous points, before computing the new ones, after rotation
                for (int k = 0; k < nLevels; k++) {
                    pan[k][0] = pn[k][0];
                    pan[k][1] = pn[k][1];
                    pan[k][2] = pn[k][2];
                }
                ;
                if ((curr_Tangle + angleOriz) > 360f) {
                    angleOriz = 360f - curr_Tangle;
                }
                Voriz.Rotate(Vdir, angleOriz);
                curr_Tangle += angleOriz;
                //compute current points
                VvertRotAxis.Rotate(Vdir, angleOriz);
                Vvert.duplicate(Voriz);
                //compute each point from horizontal to vertical, minus the first and the last
                for (int k = 0; k < nLevels; k++) {
                    pn[k][0] = Vvert.getX();
                    pn[k][1] = Vvert.getY();
                    pn[k][2] = Vvert.getZ();
                    Vvert.Rotate(VvertRotAxis, angleVert);
                }
                //System.out.println("colors["+i+"="+colors[i]);
                gl.glColor3f(colors[i].getRed() / 255f, colors[i].getGreen() / 255f, colors[i].getBlue() / 255f);
                gl.glBegin(GL.GL_TRIANGLE_STRIP);
                //top triangle
                gl.glVertex3f(0, 0, 1);
                //side triangles
                for (int k = nLevels - 1; k > 0; k--) {
                    gl.glVertex3fv(pan[k], 0);
                    gl.glVertex3fv(pn[k], 0);
                }
                gl.glVertex3fv(pan[0], 0);
                gl.glVertex3fv(pn[0], 0);
                gl.glEnd();
            }//end for points
        }//end for components
    }

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
     * draws a node
     * @param gl
     * @param Vdir
     * @param Vpos
     * @param components
     * @param fractions
     * @param colors
     * @param radius
     */
    private void drawNode(GL2 gl, VectorO VdirInit, VectorO VposInit, int nodeID, float radius) {
        //if ( capID > 0 ) {//if there is an object to be drawn

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
        //gl.glColor3f( color.getRed()/255f, color.getGreen()/255f, color.getBlue()/255f);
        //use already constructed cap as display list
        //draw it at (0,0,0) in self reference system
        gl.glCallList(nodeID);
        //draw circle for cap base
        if (circleID > 0) {
            gl.glColor3f(0.1f, .1f, .1f);//color.getRed()/510f, color.getGreen()/510f, color.getBlue()/510f);
            //use already constructed cap as display list
            //draw it at (0,0,0) in self reference system
            gl.glCallList(circleID);
        }
        ;
        //};
    }

    /**
     *  draws links, if their attributes are computed, and invalidate links is not set
     */
    @Override
    public void drawLinks(GL2 gl, Object[] graphicalAttrs) {
        if (((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowMST.isSelected()) {
            drawMST(gl);
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
                if (!(((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowPeers.isSelected() /*&& link.peersQuality != null*/
                        || ((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowPing.isSelected() /*&& link.inetQuality != null && link.inetQuality.length >= 3*/
                        )) {
                    //skip it
                    //System.out.println( (link.peersQuality!=null?"peers":"ping")+" link "+link.name+" not shown");
                    continue;
                }
                ;
                hLinkAttrs = (HashMap) ((HashMap) graphicalAttrs[2]).get(link);
                //draw the link
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
                gl.glColor3fv(colorLink, 0);
                gl.glCallList(linkID);

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

    private void computeMST(GL2 gl, Object[] graphicalAttrs, boolean bInvalidateLinks) {
        //get mst information
        Vector mstData;
        mstData = ((VrvsSerMonitor) JoglPanel.globals.mainPanel.monitor).getMST();
        if (mstData == null) {
            mstData = new Vector();
        }
        Object obj;
        DLink dlink;
        HashMap hLinkAttrs;
        rcNode nFrom, nTo;
        //update local mst array with the new mst links that have appeared
        try {
            for (int i = 0; i < mstData.size(); i++) {
                dlink = (DLink) mstData.get(i);
                obj = mstAttrs.get(dlink);
                if (obj == null) {
                    nFrom = JoglPanel.dglobals.snodes.get(dlink.fromSid);
                    nTo = JoglPanel.dglobals.snodes.get(dlink.toSid);
                    if ((nFrom != null) && (nTo != null)) {//valid from-to link
                        hLinkAttrs = new HashMap();
                        //remember parent nodes
                        hLinkAttrs.put("fromSID", dlink.fromSid);
                        hLinkAttrs.put("toSID", dlink.toSid);
                        hLinkAttrs.put("fromLAT", new Float(DataGlobals.failsafeParseFloat(nFrom.LAT, -21.22f)));
                        hLinkAttrs.put("fromLONG", new Float(DataGlobals.failsafeParseFloat(nFrom.LONG, -111.15f)));
                        hLinkAttrs.put("toLAT", new Float(DataGlobals.failsafeParseFloat(nTo.LAT, -21.22f)));
                        hLinkAttrs.put("toLONG", new Float(DataGlobals.failsafeParseFloat(nTo.LONG, -111.15f)));
                        mstAttrs.put(dlink, hLinkAttrs);
                    }
                    ;
                }
                ;
            }
            ;
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception checking new mst links. Exception: " + ex.getMessage());
            ex.printStackTrace();
        }

        ServiceID sidTo, sidFrom;
        //remove from renderer's mst map the ones that are no longer into mst vector
        try {
            //check if mst link has start and end node
            for (Iterator it = mstAttrs.keySet().iterator(); it.hasNext();) {
                dlink = (DLink) it.next();
                hLinkAttrs = (HashMap) mstAttrs.get(dlink);
                sidFrom = (ServiceID) hLinkAttrs.get("fromSID");
                sidTo = (ServiceID) hLinkAttrs.get("toSID");
                nFrom = JoglPanel.dglobals.snodes.get(sidFrom);
                nTo = JoglPanel.dglobals.snodes.get(sidTo);
                if ((nFrom == null) || (nTo == null) || nFrom.bHiddenOnMap || nTo.bHiddenOnMap
                        || !mstData.contains(dlink)) {
                    it.remove();
                }
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception checking mst links removal: " + ex.getMessage());
            ex.printStackTrace();
        }

        //compute graphical information for each mst link taking in account the invalidate links flag
        //global variables needed for drawing nodes, get them now to avoid concurency on them later
        float globeRad = JoglPanel.globals.globeRadius;
        float globeVirtRad = JoglPanel.globals.globeVirtualRadius;

        float[] colorLink;
        colorLink = new float[3];
        colorLink[0] = ((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.csMST.minColor.getRed() / 255f;
        colorLink[1] = ((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.csMST.minColor.getGreen() / 255f;
        colorLink[2] = ((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.csMST.minColor.getBlue() / 255f;
        int linkID;
        try {
            //check if a node's link from wconn was eliminated
            for (Iterator it = mstAttrs.keySet().iterator(); it.hasNext();) {
                dlink = (DLink) it.next();
                hLinkAttrs = (HashMap) mstAttrs.get(dlink);

                //recompute color
                //not neccessary, only one color

                //if neccessary, recompute display list
                obj = hLinkAttrs.get("LinkID");
                if ((obj != null) && bInvalidateLinks) {//for that, delete old id
                    gl.glDeleteLists(((Integer) obj).intValue(), 1);
                    obj = null;
                }
                ;
                if (obj == null) {
                    linkID = gl.glGenLists(1);
                    //System.out.println("linkID generated: "+linkID);
                    gl.glNewList(linkID, GL2.GL_COMPILE);
                    gl.glLineWidth(2f);
                    JoGLArc3D arc3D = new JoGLArc3D(((Float) hLinkAttrs.get("fromLAT")).floatValue(),
                            ((Float) hLinkAttrs.get("fromLONG")).floatValue(),
                            ((Float) hLinkAttrs.get("toLAT")).floatValue(),
                            ((Float) hLinkAttrs.get("toLONG")).floatValue(), globeRad, 10, 0.02, -globeRad
                            + globeVirtRad);
                    arc3D.drawArc(gl);
                    gl.glLineWidth(1f);
                    gl.glEndList();
                    hLinkAttrs.put("LinkID", Integer.valueOf(linkID));
                } else {
                    linkID = ((Integer) obj).intValue();
                }

                //draw the link
                //colorLink = (float[])hLinkAttrs.get("LinkColor");
                gl.glColor3fv(colorLink, 0);
                gl.glCallList(linkID);
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception computing graphical attributes for mst links: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void drawMST(GL2 gl) {
        Object obj;
        DLink dlink;
        HashMap hLinkAttrs;
        float[] colorLink;
        colorLink = new float[3];
        colorLink[0] = ((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.csMST.minColor.getRed();
        colorLink[1] = ((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.csMST.minColor.getGreen();
        colorLink[2] = ((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.csMST.minColor.getBlue();
        int linkID;
        //        gl.glLineWidth(2f);
        try {
            //check if a node's link from wconn was eliminated
            for (Iterator it = mstAttrs.keySet().iterator(); it.hasNext();) {
                dlink = (DLink) it.next();
                hLinkAttrs = (HashMap) mstAttrs.get(dlink);

                obj = hLinkAttrs.get("LinkID");
                if (obj == null) {
                    continue;//skip to next one
                }
                linkID = ((Integer) obj).intValue();

                //draw the link
                gl.glColor3fv(colorLink, 0);
                gl.glCallList(linkID);
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception drawing graphical attributes for mst links: " + ex.getMessage());
            ex.printStackTrace();
        }
        //        gl.glLineWidth(1f);
    }

    /**
     * computes and draws links; recomputes color and, if links invalidated or link without diplay list,
     * recomputes diplay list for link
     */
    @Override
    public void computeLinks(GL2 gl, Object[] graphicalAttrs) {
        //check to see if all links are invalidated, so to recompute them
        boolean bInvalidateLinks = (((Hashtable) graphicalAttrs[0]).remove("InvalidateLinks") != null);
        Object obj;

        if (((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowMST.isSelected()) {
            computeMST(gl, graphicalAttrs, bInvalidateLinks);//does the same job as DataRenderer.updateLinks and this.computeLinks
        }

        //get radius
        obj = ((Hashtable) graphicalAttrs[0]).get("NodeRadius");
        if (obj == null) {
            return;
        }
        float radius = ((Float) obj).floatValue();

        //check options in panel to recompute colors
        computeTrafficColors(((HashMap) graphicalAttrs[2]));

        //global variables needed for drawing nodes, get them now to avoid concurency on them later
        float globeRad = JoglPanel.globals.globeRadius;
        float globeVirtRad = JoglPanel.globals.globeVirtualRadius;

        ILink link;
        HashMap hLinkAttrs;
        float[] colorLink;
        int linkID;
        try {
            for (Iterator it = ((HashMap) graphicalAttrs[2]).keySet().iterator(); it.hasNext();) {
                link = (ILink) it.next();
                if (!((((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowPeers.isSelected() && (link.peersQuality != null)) || (((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowPing
                        .isSelected() && (link.inetQuality != null) && (link.inetQuality.length >= 3)))) {
                    //skip it, not remove it...
                    continue;
                }
                ;
                hLinkAttrs = (HashMap) ((HashMap) graphicalAttrs[2]).get(link);

                //recompute color
                obj = hLinkAttrs.get("LinkColor");
                colorLink = getLinkQualityColor(link, (float[]) obj);
                if (colorLink == null) {
                    System.out.println("No color for link from " + link.name);
                    continue;//could not get color for this link, so ingnore it
                }
                ;
                if (obj == null) {
                    hLinkAttrs.put("LinkColor", colorLink);
                }

                //if neccessary, recompute display list
                obj = hLinkAttrs.get("LinkID");
                if ((obj != null) && bInvalidateLinks) {//for that, delete old id
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
                    JoGLDirectedArc3D Darc3D;
                    if (link.peersQuality != null) {
                        Darc3D = new JoGLDirectedArc3D(link.fromLAT, link.fromLONG, link.toLAT, link.toLONG, globeRad,
                                10, 0.05, -globeRad + globeVirtRad, 0.2);
                    } else {
                        Darc3D = new JoGLDirectedArc3D(link.fromLAT, link.fromLONG, link.toLAT, link.toLONG, globeRad,
                                10, 0.05, -globeRad + globeVirtRad, 0.4);
                    }
                    Darc3D.drawArc(gl);
                    gl.glEndList();
                    hLinkAttrs.put("LinkID", Integer.valueOf(linkID));
                    hLinkAttrs.put("LinkArrowBase", Darc3D.vPointFrom);
                    //System.out.println("point from: "+Darc3D.vPointFrom);
                    //System.out.println("point to: "+Darc3D.vPointTo);
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

                //draw the link
                //colorLink = (float[])hLinkAttrs.get("LinkColor");
                gl.glColor3fv(colorLink, 0);
                gl.glCallList(linkID);

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

    // what we show on the map
    /*    private final static int NODE_AUDIO = 1;    // bubbles with nr. of audio clients
        private final static int NODE_VIDEO = 2;    // bubbles with nr. of audio clients
        private final static int NODE_TRAFFIC = 3;   // bubbles with current traffic
        private final static int NODE_LOAD = 4;     // bubbles with current load (load5)
        private final static int NODE_VIRTROOMS = 5;// bubbles with nr. of virtual rooms
        private final static int NODE_CPUPIE = 6;   // bubbles with cpu pie (user/sys/idle)
        private final static int PEERS_QUAL2H = 11;  // peers link with quality last 2 h
        private final static int PEERS_QUAL12H = 12; // peers link with quality last 12 h
        private final static int PEERS_QUAL24H = 13; // peers link with quality last 24 h
        private final static int PEERS_TRAFFIC = 14; // peers link with current traffic
     */
    /**
     * 						NODES FUNCTIONS
     */

    private void computeNodesColors(HashMap nodes) {
        if (nodes == null) {
            return;
        }
        // check all nodes and make a statistic
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        double crt = 0.0;
        rcNode node;
        try {
            for (Iterator it = nodes.keySet().iterator(); it.hasNext();) {
                node = (rcNode) it.next();
                crt = getNodeValue(node);
                if (crt == -1) {
                    continue;
                }
                min = (min < crt) ? min : crt;
                max = (max > crt) ? max : crt;
            }
        } catch (Exception ex) {
            //there was an error, ignore
            logger.log(Level.INFO, "exception computing nodes color: " + ex.getMessage());
            ex.printStackTrace();
        }
        if (min > max) {
            min = max = 0.0;
        }
        if (min <= max) {
            NodeMinValue = min;
            NodeMaxValue = max;
            if ((max - min) < 1E-2) {
                NodeMinColor = Color.CYAN;
                NodeMaxColor = Color.CYAN;
            } else {
                NodeMinColor = Color.CYAN;
                NodeMaxColor = Color.BLUE;
            }
            ;
        }
    }

    /**
     * computes the color for a node, as the value between minimum
     * and maximum values
     * @param n the node
     * @return the corresponding color
     */
    private Color getNodeColor(rcNode n) {
        double val = getNodeValue(n);
        if (val == -1) {
            return null;
        }
        double delta = ((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.csNodes.maxValue
                - ((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.csNodes.minValue;
        if (Math.abs(delta) < 1E-5) {
            return NodeMinColor;
        }
        int R = (int) ((val * (NodeMaxColor.getRed() - NodeMinColor.getRed())) / delta) + NodeMinColor.getRed();
        int G = (int) ((val * (NodeMaxColor.getGreen() - NodeMinColor.getGreen())) / delta) + NodeMinColor.getGreen();
        int B = (int) ((val * (NodeMaxColor.getBlue() - NodeMinColor.getBlue())) / delta) + NodeMinColor.getBlue();
        if (R < 0) {
            R = 0;
        }
        if (R > 255) {
            R = 255;
        }
        if (G < 0) {
            G = 0;
        }
        if (G > 255) {
            G = 255;
        }
        if (B < 0) {
            B = 0;
        }
        if (B > 255) {
            B = 255;
        }
        return new Color(R, G, B);
    }

    /**
     * computes the value for the selected item in the JOptPan combobox
     * @param n the node
     * @return the corresponding value
     */
    private double getNodeValue(rcNode node) {
        try {
            switch (((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.nodesShow) {
            case JoptPan.NODE_AUDIO:
                return DoubleContainer.getHashValue(node.haux, "Audio");
            case JoptPan.NODE_VIDEO:
                return DoubleContainer.getHashValue(node.haux, "Video");
            case JoptPan.NODE_TRAFFIC:
                double in = -1.0,
                out = -1.0;
                synchronized (node.haux) {//do not modify the hash
                    for (Enumeration en = node.haux.keys(); en.hasMoreElements();) {
                        String key = (String) en.nextElement();
                        if (key.indexOf("_IN") != -1) {
                            double tin = DoubleContainer.getHashValue(node.haux, key);
                            if (tin >= 0) {
                                if (in < 0) {
                                    in = tin;
                                } else {
                                    in += tin;
                                }
                            }
                        } else if (key.indexOf("_OUT") != -1) {
                            double tout = DoubleContainer.getHashValue(node.haux, key);
                            if (tout >= 0) {
                                if (out < 0) {
                                    out = tout;
                                } else {
                                    out += tout;
                                }
                            }
                        }
                    }//end for
                }//end sync
                if ((in + out) < 0) {
                    return -1;
                }
                return in + out;
            case JoptPan.NODE_LOAD:
                return DoubleContainer.getHashValue(node.haux, "Load");
            case JoptPan.NODE_VIRTROOMS:
                return DoubleContainer.getHashValue(node.haux, "VirtualRooms");
            case JoptPan.NODE_USERS:
                if ((node.client != null) && (node.client.farm != null)) {
                    MCluster clusterUsers = node.client.farm.getCluster("Users");
                    if (clusterUsers != null) {
                        return clusterUsers.getNodes().size();
                    }
                }
                ;
                return 0;
            default:
                return -1;
            }
        } catch (NullPointerException e) {
            return -1;
        }
    }

    //	private double minQuality = 0;
    //	private double maxQuality = 100;
    //	private float[] minQualityColor = {0.0f, 1.0f, 1.0f};
    //	private float[] maxQualityColor = {0.0f, 0.0f, 1.0f};

    /**
     * 						LINKS FUNCTIONS
     */

    void computeTrafficColors(HashMap links) {
        if (links == null) {
            return;
            // first, check all links and see which is the best and which is
            // the worst for the current quality selected in the link menu
        }

        double min_i = Double.MAX_VALUE;
        double max_i = Double.MIN_VALUE;
        double min_p = Double.MAX_VALUE;
        double max_p = Double.MIN_VALUE;
        double crt = 0.0;
        ILink link;
        try {
            for (Iterator it = links.keySet().iterator(); it.hasNext();) {
                link = (ILink) it.next();
                if (link.inetQuality != null) {
                    crt = link.inetQuality[0];
                    min_i = (min_i < crt) ? min_i : crt;
                    max_i = (max_i > crt) ? max_i : crt;
                }
                if (link.peersQuality != null) {
                    crt = link.peersQuality[0];
                    min_p = (min_p < crt) ? min_p : crt;
                    max_p = (max_p > crt) ? max_p : crt;
                }
            }
        } catch (Exception ex) {
            //there was an error, ignore
            logger.log(Level.INFO, "exception computing links color: " + ex.getMessage());
            ex.printStackTrace();
        }

        if ((min_i == Double.MAX_VALUE) || (max_i == Double.MIN_VALUE)) {
            min_i = max_i = 0;
        }
        if ((min_p == Double.MAX_VALUE) || (max_p == Double.MIN_VALUE)) {
            min_p = max_p = 0;
        }

        try {
            Hashtable hPingOptions = (Hashtable) OptionsPanelValues.get("ping");
            hPingOptions.put("MinValue", Double.valueOf(max_i));
            hPingOptions.put("MaxValue", Double.valueOf(min_i));

            if (Math.round(min_i) == Math.round(max_i)) {
                hPingOptions.put("MinColor", Color.RED);
                hPingOptions.put("MaxColor", Color.RED);
            } else {
                hPingOptions.put("MinColor", Color.RED);
                hPingOptions.put("MaxColor", Color.YELLOW);
            }
            ;
        } catch (Exception ex) {
            logger.log(Level.WARNING, "exception computing Ping links color: no options values hastable????");
            ex.printStackTrace();
        }
    }

    /**
     * compute the color for the link, using @link getLinkQuality
     * @param lnk the link
     * @return the color
     */
    private float[] getLinkQualityColor(ILink lnk, float[] color) {
        double val = getLinkQuality(lnk);
        if (val == -1) {
            return null;
        }
        if (color == null) {
            color = new float[3];
        }

        double maxValue = -1, minValue = -1;
        Color maxColor = Color.WHITE, minColor = Color.BLACK;
        boolean bCrtCs = false;
        try {
            Hashtable hPingOptions = (Hashtable) OptionsPanelValues.get("ping");
            if (((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowPing.isSelected()
                    && (lnk.inetQuality != null)) {
                minValue = ((Double) hPingOptions.get("MinValue")).doubleValue();
                maxValue = ((Double) hPingOptions.get("MaxValue")).doubleValue();
                minColor = (Color) hPingOptions.get("MinColor");
                maxColor = (Color) hPingOptions.get("MaxColor");
                //crtCs = JoglPanel.globals.mainPanel.optPan.csPing;
                bCrtCs = true;
            }
            Hashtable hPeersOptions = (Hashtable) OptionsPanelValues.get("peers");
            if (((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowPeers.isSelected()
                    && (lnk.peersQuality != null)) {
                minValue = ((Double) hPeersOptions.get("MinValue")).doubleValue();
                maxValue = ((Double) hPeersOptions.get("MaxValue")).doubleValue();
                minColor = (Color) hPeersOptions.get("MinColor");
                maxColor = (Color) hPeersOptions.get("MaxColor");
                //crtCs = JoglPanel.globals.mainPanel.optPan.csPeers;
                bCrtCs = true;
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "exception computing links quality: no options values hastable????");
            ex.printStackTrace();
        }
        if (!bCrtCs) {
            return null;
            //if(crtCs == null) return null;
        }

        double delta = Math.abs(maxValue - minValue);
        if (Math.abs(delta) < 1E-5) {
            color[0] = minColor.getRed() / 255f;
            color[1] = minColor.getGreen() / 255f;
            color[2] = minColor.getBlue() / 255f;
            return color;
            //return crtCs.minColor;
        }
        ;
        int R, G, B;
        if (maxValue > minValue) {
            R = (int) ((val * (maxColor.getRed() - minColor.getRed())) / delta) + minColor.getRed();
            G = (int) ((val * (maxColor.getGreen() - minColor.getGreen())) / delta) + minColor.getGreen();
            B = (int) ((val * (maxColor.getBlue() - minColor.getBlue())) / delta) + minColor.getBlue();
        } else {
            R = (int) ((val * (minColor.getRed() - maxColor.getRed())) / delta) + maxColor.getRed();
            G = (int) ((val * (minColor.getGreen() - maxColor.getGreen())) / delta) + maxColor.getGreen();
            B = (int) ((val * (minColor.getBlue() - maxColor.getBlue())) / delta) + maxColor.getBlue();
        }

        if (R < 0) {
            R = 0;
        }
        if (R > 255) {
            R = 255;
        }
        if (G < 0) {
            G = 0;
        }
        if (G > 255) {
            G = 255;
        }
        if (B < 0) {
            B = 0;
        }
        if (B > 255) {
            B = 255;
        }
        color[0] = R / 255f;
        color[1] = G / 255f;
        color[2] = B / 255f;
        //return new Color(R, G, B);
        return color;
    }

    /**
     * return the link's quality, if exists, according to the
     * current menu item selected int the link combo box
     */
    public double getLinkQuality(ILink link) {
        double rez = -1;
        if (link == null) {
            return rez;
        }
        if (((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowPing.isSelected()) {
            if (link.inetQuality != null) {
                rez = link.inetQuality[0];
            }
        }
        if (((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowPeers.isSelected()) {
            if (link.peersQuality != null) {
                rez = link.peersQuality[((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.peersShow - 11];
            }
        }
        return rez;
    }

    @Override
    public Hashtable getLinks(rcNode node) {
        if (node != null) {
            return node.wconn;
        }
        return null;
    }

    @Override
    public boolean isValidLink(Object objLink) {
        ILink link;
        if (objLink == null) {
            return false;
        }
        if (!(objLink instanceof ILink)) {
            return false;
        }
        link = (ILink) objLink;
        if ((((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowPeers.isSelected() && (link.peersQuality != null))
                || (((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowPing.isSelected()
                        && (link.inetQuality != null) && (link.inetQuality.length >= 3))) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isDeadLink(Object link, HashMap hLinkAttrs, HashMap localNodes) {
        rcNode nodeFrom, nodeTo;
        nodeFrom = (rcNode) hLinkAttrs.get("fromNode");
        nodeTo = (rcNode) hLinkAttrs.get("toNode");
        Hashtable h = getLinks(nodeFrom);
        boolean contains = false;
        if (h != null) {
            for (Enumeration en = h.elements(); en.hasMoreElements();) {
                if (link.equals(en.nextElement())) {
                    contains = true;
                    break;
                }
            }
        }
        if ((nodeFrom == null) || (nodeTo == null) || !localNodes.containsKey(nodeTo)
                || !localNodes.containsKey(nodeFrom) || nodeFrom.bHiddenOnMap || nodeTo.bHiddenOnMap || !contains
                || !isValidLink(link)) {
            return true;
        }
        return false;
    }

    public static final char MNode_PoundSign = '#';

    /**
     * fills farm node tooltip with specific info
     */
    @Override
    public void fillSelectedNodeInfo(rcNode n, HashMap hObjAttrs) {
        hObjAttrs.put("Name", n.UnitName + " (" + n.IPaddress + ")");
        String sDescription = "Group: " + n.mlentry.Group;
        //get app property
        String val = AppConfig.getProperty("rcFrame.MNode.overridePoundSign", "false");
        boolean bCutAfterPoundSign = false;
        if ((val != null) && (val.equals("1") || val.toLowerCase().equals("true"))) {
            bCutAfterPoundSign = true;
        }
        //get meetings list
        if ((n.client != null) && (n.client.farm != null)) {
            MCluster clusterUsers = n.client.farm.getCluster("Users");
            ArrayList alUList = null;
            //if there is a list of users as cluster, then construct
            //an array of users, with name and meetings ids
            //if a user is found in a meeting, is removed from list
            //remaining users are shown independently
            if ((clusterUsers != null) && (clusterUsers.getNodes().size() > 0)) {
                alUList = new ArrayList();
                Vector clUList = clusterUsers.getNodes();
                for (int i = 0; i < clUList.size(); i++) {
                    //parse user
                    String userFullName = ((MNode) clUList.get(i)).getName();
                    //user has format: <user_name>#<user_id>#<meeting_id>
                    //the list will contain {<user_name>,<meeting_id>}
                    int nLastSep = userFullName.lastIndexOf(MNode_PoundSign);
                    if (nLastSep != -1) {
                        int nFirstSep = userFullName.indexOf(MNode_PoundSign);
                        //is always != -1 as it can only be <= nLastSep
                        if (nFirstSep < (nLastSep - 1)) {
                            alUList.add(new String[] {
                                    (bCutAfterPoundSign ? userFullName.substring(0, nFirstSep) : userFullName),
                                    userFullName.substring(nLastSep + 1) });
                        } else {
                            alUList.add(new String[] {
                                    (bCutAfterPoundSign ? userFullName.substring(0, nFirstSep) : userFullName), "" });
                        }
                    } else {
                        alUList.add(new String[] { userFullName, "" });
                    }
                    ;
                }
            }
            //for each meeting search for users based on meeting id
            MCluster clusterMeetings = n.client.farm.getCluster("Meetings");
            if (clusterMeetings != null) {
                Vector clMList = clusterMeetings.getNodes();
                if (clMList.size() > 0) {
                    sDescription += "\nMeetings (local users):";
                    for (int i = 0; i < clMList.size(); i++) {
                        sDescription += "\n  " + clMList.get(i);
                        //find meeting id, if users list available
                        if ((alUList != null) && (alUList.size() > 0)) {//if there are users in it
                            String meetingFullName = ((MNode) clMList.get(i)).getName();
                            int nSep = meetingFullName.indexOf(MNode_PoundSign);
                            String meetingID = "";
                            if (nSep != -1) {
                                meetingID = meetingFullName.substring(nSep + 1);
                            }
                            if (meetingID.length() > 0) {
                                boolean bFirstUser = true;
                                //search local users for meeting
                                for (int j = 0; j < alUList.size(); j++) {
                                    String aux[] = ((String[]) alUList.get(j));
                                    //if user in meeting, remove from list
                                    if (aux[1].equals(meetingID)) {
                                        sDescription += (bFirstUser ? " (" : ",") + aux[0];
                                        bFirstUser = false;
                                        alUList.remove(j);
                                        j--;
                                    }
                                }
                                ;
                                if (!bFirstUser) {
                                    sDescription += ")";
                                } else {
                                    sDescription += " (-)";
                                }
                            } else {
                                //no meeting id, so, no user
                                sDescription += " (-)";
                            }//end meeting id
                        }
                        ;//end users list is available
                    }//end for each meeting
                }//end meetings list has at least one value
            }
            ;//end meetings list is an object
            //show remaining users without meetings
            if ((alUList != null) && (alUList.size() > 0)) {//if there are users in it
                sDescription += "\n  Idle users: ";
                boolean bFirstUser = true;
                for (int j = 0; j < alUList.size(); j++) {
                    String aux[] = ((String[]) alUList.get(j));
                    sDescription += (bFirstUser ? "" : ",") + aux[0];
                    bFirstUser = false;
                }
                ;
            }
            ;
        }
        hObjAttrs.put("Description", sDescription);
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

    @Override
    public boolean fillSelectedLinkInfo(Object objLink, HashMap hLinkAttrs, HashMap objAttrs) {
        if (!(objLink instanceof ILink)) {
            return false;
        }
        String sName = "";
        String sDescription = "";
        rcNode n = (rcNode) hLinkAttrs.get("fromNode");
        if (n != null) {
            sDescription += "from " + n.UnitName;
        }
        n = (rcNode) hLinkAttrs.get("toNode");
        if (n != null) {
            sDescription += " to " + n.UnitName;
        }
        ILink link = (ILink) objLink;
        if ((link.peersQuality != null)
                && ((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowPeers.isSelected()) {
            sName = "Peer link ";
            sDescription += "\nQuality: "
                    + ((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.csPeers.formatter
                    .format(getLinkQuality(link)) + " %";
        }
        if ((link.inetQuality != null) && ((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.kbShowPing.isSelected()) {
            sName += "ABPing";
            sDescription += "\nRTime: "
                    + ((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.csPing.formatter.format(link.inetQuality[0])
                    + " ms";
            sDescription += "\nRTT = "
                    + ((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.csPing.formatter.format(link.inetQuality[1])
                    + " ms";
            sDescription += "\nLost packages: "
                    + ((VrvsJoglPanel) JoglPanel.globals.mainPanel).optPan.csPing.formatter
                    .format(link.inetQuality[3] * 100.0) + " %";
        }
        objAttrs.put("Name", sName);
        objAttrs.put("Description", sDescription);
        return true;
    }

    public void changeLinkAnimationStatus(boolean bAnimate) {
        //do nothing

    }

    public boolean getLinkAnimationStatus() {
        return false;
    }
}
