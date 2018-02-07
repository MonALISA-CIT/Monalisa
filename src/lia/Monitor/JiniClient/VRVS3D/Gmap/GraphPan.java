package lia.Monitor.JiniClient.VRVS3D.Gmap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.ToolTipManager;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.DLink;
import lia.Monitor.JiniClient.CommonGUI.DoubleContainer;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphTopology;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GridLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.LayeredTreeLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.LayoutChangedListener;
import lia.Monitor.JiniClient.CommonGUI.Gmap.LayoutTransformer;
import lia.Monitor.JiniClient.CommonGUI.Gmap.RadialLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.RandomLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.SpringLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Jogl.DataGlobals;
import lia.Monitor.JiniClient.CommonGUI.Jogl.VrvsNodesRenderer;
import lia.Monitor.JiniClient.CommonGUI.Mmap.JColorScale;
import lia.Monitor.JiniClient.VRVS3D.JoptPan;
import lia.Monitor.JiniClient.VRVS3D.MeetingDetails;
import lia.Monitor.JiniClient.VRVS3D.SearchUserLayer;
import lia.Monitor.JiniClient.VRVS3D.VrvsSerMonitor;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ILink;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.MLSerClient;
import net.jini.core.lookup.ServiceID;

public class GraphPan extends JPanel implements MouseListener, MouseMotionListener, LayoutChangedListener,
        LocalDataFarmClient {

    /**
     * 
     */
    private static final long serialVersionUID = -7176973961087574652L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(GraphPan.class.getName());

    volatile Map<ServiceID, rcNode> GlobalNodes = new ConcurrentHashMap<ServiceID, rcNode>();
    Hashtable nodes = new Hashtable();//local nodes
    ArrayList lLinkToFakeNode = new ArrayList();//reference from link to fake node, contains Object[2], fake node is on first position, link on second

    HashMap users = new HashMap();

    public JPopupMenu userSearchMenuList;
    private final SearchUserLayer sl = new SearchUserLayer();

    public String userSearchHighlight = null;

    //Vector vnodes;
    public Vector mstData;

    private final BasicStroke doubleStroke = new BasicStroke(2);

    public static final float fLayoutMargin = 0.02f;

    /**
     * checks to see if object is containd into lLinkToFakeNode array list,
     * using the type parameter to know whom to compare with
     * @param type
     * @param obj
     */
    public synchronized Object[] LTFNcontains(int type, Object obj) {
        if (obj == null) {
            return null;
        }
        Object con[];
        for (int i = 0; i < lLinkToFakeNode.size(); i++) {
            con = (Object[]) lLinkToFakeNode.get(i);
            if ((con.length > type) && con[type].equals(obj)) {
                return con;
            }
        }
        return null;
    }

    rcNode pick;
    rcNode pickX;

    SerMonitorBase monitor;
    GmapPan gmapPan;
    Color bgColor = Color.WHITE; //new Color(230, 230, 250);
    Color clHiPing = new Color(255, 100, 100);
    int wunit = 0;
    int hunit = 0;
    boolean showOnlyHandledNodes;
    boolean showOnlyReflectorNodes;
    boolean showFullUserInfo = true;
    static Color darkGreen = new Color(0, 32, 0);
    static Font nodesFont = new Font("Arial", Font.PLAIN, 10);

    private final DecimalFormat lblFormat = new DecimalFormat("###,###.#");

    // layout & stuff
    GraphLayoutAlgorithm layout = null;
    GraphTopology vGraph = null;
    private final Object syncGraphObj = new Object();
    public String currentLayout = "none";
    LayoutTransformer layoutTransformer;
    // rectangles used for rescaling purposes
    Rectangle range = new Rectangle();
    Rectangle actualRange = new Rectangle();
    //  Object syncRescale = new Object();

    public final String PRESENCE_STATUS_AVAILABLE = "AVAILABLE";
    public final String PRESENCE_STATUS_AWAY = "AWAY";

    public GraphPan(GmapPan gmapPan) {
        this.gmapPan = gmapPan;
        addMouseListener(this);
        userSearchMenuList = new JPopupMenu();
        userSearchMenuList.addFocusListener(sl);
        ToolTipManager.sharedInstance().registerComponent(this);
        setBackground(bgColor);
        TimerTask ttask = new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName(" ( ML ) - VRVS3D Gmap - GraphPan update things Timer Thread");
                syncNodes();
                try {
                    computeNewColors();
                    updateMeetings();
                    repaint();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Error executing", t);
                }
            }
        };
        BackgroundWorker.schedule(ttask, 2000, 2000);
        layoutTransformer = new LayoutTransformer(this);
        //        System.out.println("AppConfig.getProperty(\"rcFrame.MNode.overridePoundSign\")="+AppConfig.getProperty("rcFrame.MNode.overridePoundSign"));
    }

    public void showSearchPopup() {

        ArrayList l = new ArrayList();
        if ((users != null) && showFullUserInfo) {
            for (Iterator it = users.keySet().iterator(); it.hasNext();) {

                Object key = it.next();
                Object o[] = (Object[]) users.get(key);
                rcNode n = null;
                if (o != null) {
                    n = (rcNode) o[0];
                }

                if (showOnlyHandledNodes && (n != null) && !n.isLayoutHandled) {
                    continue;
                }

                if ((n != null) && !nodes.contains(n)) {
                    continue;
                }

                if (!showOnlyReflectorNodes) {
                    l.add(key);
                }
            }
        }
        sl.setUserList(l, this);
        userSearchMenuList.add(sl);
        userSearchMenuList.show(this, 0, 0);
        //    	userSearchMenuList.requestFocus();
        userSearchMenuList.requestFocusInWindow();
    }

    private void updateMeetings() {
        gmapPan.optPan.meetingsUpdate.updateMeetingsMenu(GlobalNodes, gmapPan.optPan.communityMenuList,
                gmapPan.optPan.meetingsMenuList);
    }

    /*	public void setNodes(Hashtable nodes, Vector vnodes) {
     this.nodes = nodes;
     this.vnodes = vnodes;
     }
     */

    public void setNodesTab(Map<ServiceID, rcNode> snodes) {
        this.GlobalNodes = snodes;
        syncNodes();
    }

    /**
     * for a given node, checks to see if it has fake links,
     * and, if the case, checkes to see if that type of link is already treated in local cache (lLinkToFakeNode),
     * or, adds it setting its sid to the parent sid and its position (parent) and name (based on link's destination). 
     * @param node
     */
    private synchronized void addFakes(rcNode node) {
        //fake nodes are users connected to this reflector
        try {
            String val = AppConfig.getProperty("rcFrame.MNode.overridePoundSign");
            boolean bCutAfterPoundSign = false;
            if ((val != null) && (val.equals("1") || val.toLowerCase().equals("true"))) {
                bCutAfterPoundSign = true;
            }
            //get meetings list
            if ((node.client != null) && (node.client.farm != null)) {
                MCluster clusterUsers = node.client.farm.getCluster("Users");
                if ((clusterUsers != null) && (clusterUsers.getNodes().size() > 0)) {
                    Vector clUList = clusterUsers.getNodes();
                    for (int i = 0; i < clUList.size(); i++) {
                        //parse user
                        String userFullName = ((MNode) clUList.get(i)).getName();
                        int nFirstSep = userFullName.indexOf(VrvsNodesRenderer.MNode_PoundSign);
                        int nSecondSep = userFullName.indexOf(VrvsNodesRenderer.MNode_PoundSign, nFirstSep + 1);
                        if (nSecondSep == -1) {
                            nSecondSep = userFullName.length();
                        }
                        String userID = "";
                        if ((nFirstSep != -1) && (nSecondSep != -1)) {
                            userID = userFullName.substring(nFirstSep + 1, nSecondSep);
                        }
                        String meetingID = "";
                        //not the case anymore, now a predicate has to be used to find the meeting id
                        //                        if ( nSecondSep!=-1 && nSecondSep+1<userFullName.length() )
                        //                            meetingID = userFullName.substring( nSecondSep+1);
                        //use as link the userID
                        String userName = (bCutAfterPoundSign ? clUList.get(i).toString() : ((MNode) clUList.get(i))
                                .getName());
                        //System.out.println("checking "+userName+" with id "+userID+" as fake node for "+node.UnitName);
                        Object[] userInfo = null;
                        if ((userID.length() > 0) && ((userInfo = LTFNcontains(1, userID)) == null)) {
                            //additional checking for when the Meeting option is selected in combobox
                            //so that only users in the selected meetings are shown
                            /**
                             * create a fake node, put it in local nodes and save its connection to fake link 
                             */
                            rcNode fakeNode = new rcNode();
                            fakeNode.wconn = new Hashtable();
                            fakeNode.conn = new Hashtable();
                            fakeNode.UnitName = userName;
                            //fakeNode.setShortName();//not usefull because it cuts last digits from ip adress
                            fakeNode.shortName = userName;
                            fakeNode.x = node.x;
                            fakeNode.y = node.y;
                            //because the meeting id is no longer available in user name, register with a real time
                            //predicate for this fake node to find the meeting id
                            monPredicate pred = new monPredicate(node.UnitName, "Users", userFullName, -1, -1,
                                    new String[] { "ProxyStatus", "UserStatus" }, null);
                            node.client.addLocalClient(this, pred);
                            monPredicate pred2 = new monPredicate(node.UnitName, "Users", userFullName,
                                    -30 * 60 * 1000, -1, new String[] { "UserInfo" }, null);
                            node.client.addLocalClient(this, pred2);
                            //                            fakeNode.sid = node.sid;
                            //                            rcNode n = new rcNode();
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
                            coord += DataGlobals.failsafeParseFloat(node.LONG, -111.15f);
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
                            coord += DataGlobals.failsafeParseFloat(node.LAT, -21.22f);
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
                            //fakeNode.IPaddress = hlink.szDestName;
                            fakeNode.isLayoutHandled = true;

                            String niceUserName = userFullName;
                            if (nFirstSep != -1) {
                                niceUserName = userFullName.substring(0, nFirstSep);
                            }
                            //System.out.println("reformated name: "+userName.replaceAll("([a-z]+)([A-Z][a-z]+)", "$1 $2"));
                            niceUserName = niceUserName.replaceAll("([a-z]+)([A-Z][a-z]+)", "$1 $2");
                            String userinfo5 = "<html><table border=0 cellspacing=0 cellpadding=4><tr><td><table border=0 cellspacing=0 cellpadding=1>"
                                    + "userinfo_start"
                                    + "<tr><td>Name:</td><td>"
                                    + niceUserName
                                    + "</td></tr>"
                                    + "userinfo_end"
                                    + "userstatus_startuserstatus_end"
                                    + "usermeeting_startusermeeting_end" + "</table></td></tr></table></html>";
                            //after creation of this node, check to see if such a node already exists in list
                            //although the link does not (probably bad removal), and if it is, update only the link
                            //and don't add the new node to nodes, so its creation is useless
                            //parameters for userInfo are: rcNode reference, user id, real node, meeting id, predicate, tooltip text, presence status string (AVAILABLE or AWAY), second predicate for UserInfo
                            userInfo = new Object[] { fakeNode, userID, node, meetingID, pred, userinfo5, null, pred2 };
                            lLinkToFakeNode.add(userInfo);
                            users.put(niceUserName, userInfo);
                            //                            System.out.println("<mluc> oddly, we are adding user "+fakeNode.UnitName+" as fake node for "+node.UnitName);
                        }
                        ;
                        rcNode fakeNode = ((rcNode) userInfo[0]);
                        boolean bAddNode = true;
                        //not add a node to nodes only if the following things are fulfilled
                        //Meetings option in combo is selected and at least one meeting is checked
                        //and node is not in that meeting
                        boolean bMeetings = (gmapPan.optPan.nodesShow == JoptPan.NODE_MEETINGS);
                        if (bMeetings) {
                            ArrayList<MeetingDetails.IDColor> lMeetingsSelected = gmapPan.optPan.meetingsUpdate
                                    .getSnapshot();
                            boolean bAtLeastOneMeetingSel = lMeetingsSelected.size() > 0;
                            if (bAtLeastOneMeetingSel) {
                                bAddNode = false;
                                for (final MeetingDetails.IDColor mElem : lMeetingsSelected) {
                                    if (mElem.ID.equals(userInfo[3])) {//equal meeting ids
                                        bAddNode = true;
                                        //System.out.println("Should add node "+fakeNode.shortName);
                                        break;
                                    }
                                }
                                //System.out.println("Should remove node "+fakeNode.shortName);
                            }
                        }
                        if (bAddNode && (fakeNode != null) && !nodes.containsValue(fakeNode)) {
                            //System.out.println("Adding node "+fakeNode.shortName);
                            nodes.put(new Object(), fakeNode);
                        }
                        //this should never get reached because there is no more meetingID sent
                        //                        if ( meetingID.length()>0 && userInfo!=null && !((String)userInfo[3]).equals(meetingID) ) {
                        //                            userInfo[3] = meetingID;
                        ////                            System.out.println("<mluc> oddly, userID="+userID+" is in meeting "+meetingID);
                        //                        }
                    }
                }
            }
        } catch (Exception ex) {
            //ignore exception
            //ex.printStackTrace();
        }
    }

    /**
     * removes fake nodes based on hLinkToNode list
     * by checking the validity of node or link (node could still be ok, but not link..)
     *
     */
    private synchronized void removeFakes() {
        try {
            Object[] con;
            Object key;
            rcNode node, fakeNode;
            String fakeNodeUserID;
            monPredicate pred = null, pred2 = null;
            //          ArrayList sidToCheck = new ArrayList();
            ArrayList nodeToCheck = new ArrayList();

            //remove fake nodes if real nodes with same sid not in GlobalNodes any more or link from node does not exist
            //from lLinkToFakeNode list
            //also remove fake link
            for (Iterator it = lLinkToFakeNode.iterator(); it.hasNext();) {
                con = (Object[]) it.next();
                fakeNode = (rcNode) con[0];
                fakeNodeUserID = (String) con[1];
                node = (rcNode) con[2];
                pred = (monPredicate) con[4];
                pred2 = (monPredicate) con[7];
                //if real link has disapeared, remove this one too (real or fake)
                //              if ( !nodes.containsKey(node.sid) )
                //              sidToCheck.add( node.sid);
                boolean bUserStill = false;
                //get meetings list
                if ((node.client != null) && (node.client.farm != null)) {
                    MCluster clusterUsers = node.client.farm.getCluster("Users");
                    if ((clusterUsers != null) && (clusterUsers.getNodes().size() > 0)) {
                        Vector clUList = clusterUsers.getNodes();
                        for (int i = 0; i < clUList.size(); i++) {
                            //parse user
                            String userFullName = ((MNode) clUList.get(i)).getName();
                            int nFirstSep = userFullName.indexOf(VrvsNodesRenderer.MNode_PoundSign);
                            int nSecondSep = userFullName.indexOf(VrvsNodesRenderer.MNode_PoundSign, nFirstSep + 1);
                            if (nSecondSep == -1) {
                                nSecondSep = userFullName.length();
                            }
                            String userID = "";
                            if ((nFirstSep != -1) && (nSecondSep != -1)) {
                                userID = userFullName.substring(nFirstSep + 1, nSecondSep);
                            }
                            if ((userID.length() > 0) && userID.equals(fakeNodeUserID)) {
                                bUserStill = true;
                            }
                        }
                    }
                    ;
                }
                ;
                boolean contains = false;
                if (!nodes.containsKey(node.sid)) {
                    contains = false;
                } else { //equivalent to nodes.containsValue(node) 
                    rcNode n1 = (rcNode) nodes.get(node.sid);
                    contains = n1.equals(node);
                }
                //if node no more in nodes or its key not any more in nodes (creator node is gone), or its link is invalid, remove it
                if (!contains || !bUserStill) {
                    it.remove();
                    nodeToCheck.add(fakeNode);
                    //unregister fake node
                    //                    System.out.println("removing user "+fakeNode.UnitName+" and predicate "+pred);
                    node.client.deleteLocalClient(this, pred);
                    node.client.deleteLocalClient(this, pred2);
                    //                  nodes.remove(node.sid);
                    for (Iterator i = users.keySet().iterator(); i.hasNext();) {
                        String n = (String) i.next();
                        Object[] o = (Object[]) users.get(n);
                        if (o[0].equals(fakeNode)) {
                            users.remove(n);
                            break;
                        }
                    }
                }
            }
            //removes fake nodes
            //from nodes hash
            //needed because they are not removed from nodes in previous "for"
            for (Iterator it = nodes.keySet().iterator(); it.hasNext();) {
                key = it.next();
                fakeNode = (rcNode) nodes.get(key);
                //                System.out.println("checking for removal of fake nodes: "+fakeNode.UnitName);
                //              if ( sidToCheck.contains(node.sid) ) {
                //              it.remove();
                //              }
                if (nodeToCheck.contains(fakeNode)) {
                    //                    System.out.println("removing node "+fakeNode.UnitName);
                    it.remove();
                }
            }
            ;
            //remove all fake nodes that shouldn't be visible:
            boolean bMeetings = (gmapPan.optPan.nodesShow == JoptPan.NODE_MEETINGS);
            if (bMeetings) {
                ArrayList<MeetingDetails.IDColor> lMeetingsSelected = gmapPan.optPan.meetingsUpdate.getSnapshot();
                boolean bAtLeastOneMeetingSel = lMeetingsSelected.size() > 0;
                if (bAtLeastOneMeetingSel) {
                    Hashtable htRemoveInvisibleFakes = new Hashtable();
                    //for each element in current nodes list
                    for (Enumeration en = nodes.keys(); en.hasMoreElements();) {
                        key = en.nextElement();
                        node = (rcNode) nodes.get(key);
                        //check if it is a fake node
                        Object[] fakeAttrs = LTFNcontains(0, node);
                        MeetingDetails.IDColor selectedElem = null;
                        if (fakeAttrs != null) { //fake node, so check if in a meeting or not
                            for (final MeetingDetails.IDColor mElem : lMeetingsSelected) {
                                if (mElem.ID.equals(fakeAttrs[3])) {//equal meeting ids
                                    selectedElem = mElem;
                                    break;
                                }
                            }
                            if (selectedElem == null) {
                                //remove node
                                htRemoveInvisibleFakes.put(key, node);
                            }
                        }
                    }
                    for (Enumeration en = htRemoveInvisibleFakes.keys(); en.hasMoreElements();) {
                        key = en.nextElement();
                        node = (rcNode) nodes.remove(key);
                        //System.out.println("removing invisible node "+node.shortName);
                    }
                }
            }
        } catch (Exception ex) {
            //ignore exception
            ex.printStackTrace();
        }
    }

    /**
     * synchronizes local view of nodes set with global one by adding and removing nodes
     *
     */
    public void syncNodes() {
        //add new nodes from global to local, 
        //creating, if neccessary, new fakes nodes for a newly added real one
        for (rcNode n : GlobalNodes.values()) {
            boolean contains = false;
            if (!nodes.containsKey(n.sid)) {
                contains = false;
            } else {
                rcNode n1 = (rcNode) nodes.get(n.sid);
                contains = n1.equals(n);
            }
            if (!contains) {
                nodes.put(n.sid, n);
            }
            addFakes(n);
        }
        ;
        //remove old nodes from local if not in global
        try {
            Object key;
            rcNode node;
            //remove old real nodes from local if not in global
            for (Iterator it = nodes.keySet().iterator(); it.hasNext();) {
                key = it.next();
                node = (rcNode) nodes.get(key);
                if (key instanceof ServiceID) { //identify a real node
                    boolean contains = false;
                    if (!GlobalNodes.containsKey(node.sid)) {
                        contains = false;
                    } else {
                        rcNode n1 = GlobalNodes.get(node.sid);
                        contains = n1.equals(node);
                    }
                    if (!contains) {
                        //System.out.println("removing node "+node.UnitName);
                        it.remove();
                    }
                }
            }
            //remove old fake nodes from local if real node not real any more
            removeFakes();
        } catch (Exception ex) {
            //ignore exception
            //ex.printStackTrace();
        }
    }

    public void setSerMonitor(SerMonitorBase monitor) {
        this.monitor = monitor;
    }

    //  public void setPie(String k) {
    //  pieKey = k;
    //  }

    public boolean isHotLink(rcNode n1, rcNode n2) {
        if (mstData == null) {
            return false;
        }
        for (int i = 0; i < mstData.size(); i++) {
            DLink dl = (DLink) mstData.elementAt(i);
            if (dl.cequals(n1.sid, n2.sid)) {
                return true;
            }
        }
        return false;
    }

    public double getNodeValue(rcNode node) {
        try {
            switch (gmapPan.optPan.nodesShow) {
            case JoptPan.NODE_AUDIO:
                return DoubleContainer.getHashValue(node.haux, "Audio");
            case JoptPan.NODE_VIDEO:
                return DoubleContainer.getHashValue(node.haux, "Video");
            case JoptPan.NODE_TRAFFIC:
                double in = -1.0,
                out = -1.0;
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

    public Color getNodeColor(rcNode n) {
        double val = getNodeValue(n);
        if (val == -1) {
            return null;
        }
        double delta = gmapPan.optPan.csNodes.maxValue - gmapPan.optPan.csNodes.minValue;
        if (Math.abs(delta) < 1E-5) {
            return gmapPan.optPan.csNodes.minColor;
        }
        int R = (int) ((val * (gmapPan.optPan.csNodes.maxColor.getRed() - gmapPan.optPan.csNodes.minColor.getRed())) / delta)
                + gmapPan.optPan.csNodes.minColor.getRed();
        int G = (int) ((val * (gmapPan.optPan.csNodes.maxColor.getGreen() - gmapPan.optPan.csNodes.minColor.getGreen())) / delta)
                + gmapPan.optPan.csNodes.minColor.getGreen();
        int B = (int) ((val * (gmapPan.optPan.csNodes.maxColor.getBlue() - gmapPan.optPan.csNodes.minColor.getBlue())) / delta)
                + gmapPan.optPan.csNodes.minColor.getBlue();
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

    public void paintFakeNode(Graphics g, rcNode n, ArrayList<MeetingDetails.IDColor> lMeetingsSelected,
            boolean fullInfo) {

        if (!fullInfo) {
            Object[] fakeAttrs = LTFNcontains(0, n);
            MeetingDetails.IDColor selectedElem = null;
            int w_use = 7;
            boolean bMeetings = (gmapPan.optPan.nodesShow == JoptPan.NODE_MEETINGS);
            if (bMeetings && (fakeAttrs != null)) {
                for (final MeetingDetails.IDColor mElem : lMeetingsSelected) {
                    if (mElem.ID.equals(fakeAttrs[3])) {//equal meeting ids
                        selectedElem = mElem;
                        break;
                    }
                }
            }
            if (n.limits == null) {
                n.limits = new Rectangle(n.x - w_use, n.y - w_use, w_use * 2, w_use * 2);
            } else {
                n.limits.setBounds(n.x - w_use, n.y - w_use, w_use * 2, w_use * 2);
            }
            Color fillColor = Color.LIGHT_GRAY;
            if ((fakeAttrs != null) && (fakeAttrs.length > 6)) {
                if (PRESENCE_STATUS_AVAILABLE.equals(fakeAttrs[6])) {
                    fillColor = new Color(0xFFFFAA); //new Color(215,215,215);
                } else if (PRESENCE_STATUS_AWAY.equals(fakeAttrs[6])) {
                    fillColor = Color.LIGHT_GRAY;
                }
            }
            g.setColor(fillColor);
            g.fillOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
            if (selectedElem != null) {
                Stroke oldStroke = ((Graphics2D) g).getStroke();
                ((Graphics2D) g).setStroke(doubleStroke);
                //draw a darker margin - outside
                g.setColor(Color.DARK_GRAY);
                g.drawOval(n.limits.x + 3, n.limits.y + 3, n.limits.width - 6, n.limits.height - 6);
                //draw a darker margin - inside
                g.setColor(Color.DARK_GRAY);
                g.drawOval(n.limits.x + 5, n.limits.y + 5, n.limits.width - 10, n.limits.height - 10);
                //draw the margin for meeting
                g.setColor(selectedElem.color);//mElem[1]);
                g.drawOval(n.limits.x + 4, n.limits.y + 4, n.limits.width - 8, n.limits.height - 8);
                ((Graphics2D) g).setStroke(oldStroke);
                int new_x, new_y, new_width, new_height;
                new_x = n.limits.x + 4 + 6;
                new_y = n.limits.y;
                new_width = n.limits.width - 8 - 6 - 6;
                new_height = n.limits.height;
                g.setColor(fillColor);
                g.fillRect(new_x, new_y, new_width, new_height);
            }
            g.setColor(Color.black);
            if (n.equals(pickX)) {
                g.setColor(Color.red);
            }
            g.drawOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
            return;
        }

        //draw only users for selected meetings
        Object[] fakeAttrs = LTFNcontains(0, n);
        MeetingDetails.IDColor selectedElem = null;
        boolean bMeetings = (gmapPan.optPan.nodesShow == JoptPan.NODE_MEETINGS);
        if (bMeetings && (fakeAttrs != null)) {
            //System.out.println("meeting id for user "+fakeAttrs[1]+" is "+fakeAttrs[3]);
            for (final MeetingDetails.IDColor mElem : lMeetingsSelected) {
                //System.out.println("a selected meeting with id: "+mElem[0]);
                if (mElem.ID.equals(fakeAttrs[3])
                        && ((userSearchHighlight == null) || (userSearchHighlight.length() == 0))) {//equal meeting ids
                    selectedElem = mElem;
                    break;
                }
            }
            //			if ( selectedElem==null )
            //				return;//don't draw users whos' meetings aren't selected
        }
        FontMetrics fm = g.getFontMetrics();
        int w2 = (fm.stringWidth(n.shortName) + 15) / 2;

        int w_use = (w2 > wunit ? w2 : wunit);

        //        int w2 = (fm.stringWidth( n.shortName) + 15) / 2;
        //        int h2 = (fm.getHeight() + 10) / 2;
        //        if ( w2 > wunit ) 
        //            wunit = w2;
        //        if ( h2 > hunit ) 
        //            hunit = h2;
        if (n.limits == null) {
            n.limits = new Rectangle(n.x - w_use, n.y - hunit, w_use * 2, hunit * 2);
        } else {
            n.limits.setBounds(n.x - w_use, n.y - hunit, w_use * 2, hunit * 2);
        }

        //	    Object[] fakeAttrs = LTFNcontains(0,n);
        Color fillColor = Color.LIGHT_GRAY;
        if ((fakeAttrs != null) && (fakeAttrs.length > 6)) {
            if (PRESENCE_STATUS_AVAILABLE.equals(fakeAttrs[6])) {
                fillColor = new Color(0xFFFFAA); //new Color(215,215,215);
            } else if (PRESENCE_STATUS_AWAY.equals(fakeAttrs[6])) {
                fillColor = Color.LIGHT_GRAY;
            }
        }
        g.setColor(fillColor);
        //g.fillOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);

        g.fillRoundRect(n.limits.x, n.limits.y, n.limits.width, n.limits.height, 10, 10);
        //		boolean bMeetings = (gmapPan.optPan.nodesShow==JoptPan.NODE_MEETINGS);
        //		if ( bMeetings && fakeAttrs!=null ) {
        //		    //System.out.println("meeting id for user "+fakeAttrs[1]+" is "+fakeAttrs[3]);
        //		    for ( int i=0; i<lMeetingsSelected.size(); i++) {
        //		        Object[] mElem = (Object[])lMeetingsSelected.get(i);
        //		        //System.out.println("a selected meeting with id: "+mElem[0]);
        //		        if ( mElem[0].equals(fakeAttrs[3]) ) {//equal meeting ids
        //draw a round rectangle with user's meeting color
        //System.out.println("stroke meeting for user");

        boolean select = false;
        if ((userSearchHighlight != null) && (userSearchHighlight.length() != 0)) {
            Object o[] = (Object[]) users.get(userSearchHighlight);
            if (o != null) {
                rcNode rn = (rcNode) o[0];
                if (rn.equals(n)) {
                    select = true;
                }
            }
        }

        if (select) {
            Stroke oldStroke = ((Graphics2D) g).getStroke();
            ((Graphics2D) g).setStroke(doubleStroke);
            //draw a darker margin - outside
            g.setColor(Color.RED);
            g.drawRoundRect(n.limits.x + 3, n.limits.y + 3, n.limits.width - 6, n.limits.height - 6, 2, 2);
            //draw a darker margin - inside
            g.setColor(Color.RED);
            g.drawRoundRect(n.limits.x + 5, n.limits.y + 5, n.limits.width - 10, n.limits.height - 10, 2, 2);
            //draw the margin for meeting
            g.setColor(Color.red);//mElem[1]);
            g.drawRoundRect(n.limits.x + 4, n.limits.y + 4, n.limits.width - 8, n.limits.height - 8, 2, 2);
            ((Graphics2D) g).setStroke(oldStroke);
            int new_x, new_y, new_width, new_height;
            new_x = n.limits.x + 4 + 6;
            new_y = n.limits.y;
            new_width = n.limits.width - 8 - 6 - 6;
            new_height = n.limits.height;
            g.setColor(Color.red);
            g.drawRect(new_x, new_y, new_width, new_height);
        }

        if (selectedElem != null) {
            Stroke oldStroke = ((Graphics2D) g).getStroke();
            ((Graphics2D) g).setStroke(doubleStroke);
            //draw a darker margin - outside
            g.setColor(Color.DARK_GRAY);
            g.drawRoundRect(n.limits.x + 3, n.limits.y + 3, n.limits.width - 6, n.limits.height - 6, 2, 2);
            //draw a darker margin - inside
            g.setColor(Color.DARK_GRAY);
            g.drawRoundRect(n.limits.x + 5, n.limits.y + 5, n.limits.width - 10, n.limits.height - 10, 2, 2);
            //draw the margin for meeting
            g.setColor(selectedElem.color);//mElem[1]);
            g.drawRoundRect(n.limits.x + 4, n.limits.y + 4, n.limits.width - 8, n.limits.height - 8, 2, 2);
            ((Graphics2D) g).setStroke(oldStroke);
            int new_x, new_y, new_width, new_height;
            new_x = n.limits.x + 4 + 6;
            new_y = n.limits.y;
            new_width = n.limits.width - 8 - 6 - 6;
            new_height = n.limits.height;
            g.setColor(fillColor);
            g.fillRect(new_x, new_y, new_width, new_height);
        }

        g.setColor(Color.black);
        if (n.equals(pickX) || select) {
            g.setColor(Color.red);
        }
        //g.drawOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
        g.drawRoundRect(n.limits.x, n.limits.y, n.limits.width, n.limits.height, 10, 10);
        Font fOldFont = null;
        if ((fakeAttrs != null) && (fakeAttrs.length > 6)) {
            fOldFont = g.getFont();
            if (PRESENCE_STATUS_AVAILABLE.equals(fakeAttrs[6])) {
                g.setColor(Color.BLACK);
                Font fbold = fOldFont.deriveFont(Font.BOLD);
                g.setFont(fbold);
            } else if (PRESENCE_STATUS_AWAY.equals(fakeAttrs[6])) {
                g.setColor(Color.GRAY);
                Font fit = fOldFont.deriveFont(Font.ITALIC);
                g.setFont(fit);
            }
        } else {
            g.setColor(Color.BLACK);
        }
        //        if (n.equals(pickX))
        //            g.setColor( Color.red);
        g.drawString(n.shortName, (n.limits.x + 7 + w_use) - w2, n.limits.y + 5 + fm.getAscent());
        if (fOldFont != null) {
            g.setFont(fOldFont);
        }
    }

    public static final char MNode_PoundSign = '#';
    /** maximum letter a name has to be considered as a standard for the others */
    private static final int MAX_NAME_LENGTH = 12;

    public void computeMaxDims(Graphics g) {
        FontMetrics fm = g.getFontMetrics();
        hunit = (fm.getHeight() + 10) / 2;
        for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
            rcNode n = (rcNode) e.nextElement();
            if (showOnlyHandledNodes && !n.isLayoutHandled) {
                continue;
            } else {
                String text = n.shortName;
                if (text.length() > MAX_NAME_LENGTH) {
                    text = text.substring(0, MAX_NAME_LENGTH);
                }
                int w2 = (fm.stringWidth(text) + 15) / 2;
                if (w2 > wunit) {
                    wunit = w2;
                }
            }
        }
    }

    public void paintNode(Graphics g, rcNode node, ArrayList<MeetingDetails.IDColor> lMeetingsSelected) {
        boolean bMeetings = (gmapPan.optPan.nodesShow == JoptPan.NODE_MEETINGS);
        ArrayList al = new ArrayList();
        int x = node.x;
        int y = node.y;
        String nodeName = node.shortName;

        FontMetrics fm = g.getFontMetrics();
        int w2 = (fm.stringWidth(nodeName) + 15) / 2;

        int w_use = (w2 > wunit ? w2 : wunit);
        //        int h2 = (fm.getHeight() + 10) / 2;
        //        if ( h2 > hunit ) 
        //            hunit = h2;

        if (node.limits == null) {
            node.limits = new Rectangle(x - w_use, y - hunit, w_use * 2, hunit * 2);
        } else {
            node.limits.setBounds(x - w_use, y - hunit, w_use * 2, hunit * 2);
        }

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
                                for (final MeetingDetails.IDColor elem : lMeetingsSelected) {
                                    if (meetingID.equals(elem.ID)) {
                                        //node's meeting is found in list of selected meetings, so show it
                                        al.add(elem.color);//add color
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
            Color vrvsColor;
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

        int nSize = al.size();

        //draw components slices 
        //g.setColor(c);
        //g.fillOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
        int startAngle = 0;
        int angle = 360 / nSize;
        for (int i = 0; i < nSize; i++) {
            g.setColor((Color) al.get(i));
            if (i == (nSize - 1)) {
                angle = 360 - startAngle;
            }
            g.fillArc(node.limits.x, node.limits.y, node.limits.width, node.limits.height, startAngle, angle);
            startAngle += angle;
        }

        if (bMeetings) {
            //draw shadow for when there are the meetings
            g.setColor(Color.WHITE);
            g.drawString(nodeName, (node.limits.x + 7 + w_use) - w2 - 1, (node.limits.y + 5 + fm.getAscent()) - 1);
            g.drawString(nodeName, (node.limits.x + 7 + w_use) - w2 - 1, node.limits.y + 5 + fm.getAscent() + 1);
            g.drawString(nodeName, ((node.limits.x + 7 + w_use) - w2) + 1, (node.limits.y + 5 + fm.getAscent()) - 1);
            g.drawString(nodeName, ((node.limits.x + 7 + w_use) - w2) + 1, node.limits.y + 5 + fm.getAscent() + 1);
        }
        Color invC = Color.WHITE;
        if (nSize == 1) {
            Color c = (Color) al.get(0);
            if (node.fixed || (node == pickX)) {
                if (c.equals(Color.WHITE)) {
                    invC = new Color(240, 25, 25);
                } else {
                    invC = new Color(255 - c.getRed(), 255 - c.getGreen(), 255 - c.getBlue());
                }
            }
        } else {
            invC = new Color(240, 25, 25);
        }
        if (node.fixed) {
            g.setColor(invC);
        } else {
            g.setColor(Color.BLACK);
        }
        g.drawString(nodeName, (node.limits.x + 7 + w_use) - w2, node.limits.y + 5 + fm.getAscent());
        if (node.equals(pickX)) {
            g.setColor(invC);
        }
        g.drawOval(node.limits.x, node.limits.y, node.limits.width, node.limits.height);
    }

    // return the link's quality, if exists, according to the
    // current menu item selected int the link combo box
    public double getLinkQuality(ILink link) {
        double rez = -1;
        if (link == null) {
            return rez;
        }
        if (gmapPan.optPan.kbShowPing.isSelected()) {// || (!parent.optPan.kbShowPing.isSelected() &&
            if (link.inetQuality != null) {
                rez = link.inetQuality[0];
            }
        }
        if (gmapPan.optPan.kbShowPeers.isSelected()) {
            if (link.peersQuality != null) {
                rez = link.peersQuality[gmapPan.optPan.peersShow - 11];
            }
        }
        return rez;
    }

    public Color getLinkQualityColor(ILink lnk) {
        double val = getLinkQuality(lnk);
        if (val == -1) {
            return null;
        }

        JColorScale crtCs = null;
        if (gmapPan.optPan.kbShowPing.isSelected() && (lnk.inetQuality != null)) {
            crtCs = gmapPan.optPan.csPing;
        }
        if (gmapPan.optPan.kbShowPeers.isSelected() && (lnk.peersQuality != null)) {
            crtCs = gmapPan.optPan.csPeers;
        }
        if (crtCs == null) {
            return null;
        }

        double delta = Math.abs(crtCs.maxValue - crtCs.minValue);
        if (Math.abs(delta) < 1E-5) {
            return crtCs.minColor;
        }
        int R, G, B;
        if (crtCs.maxValue > crtCs.minValue) {
            R = (int) ((val * (crtCs.maxColor.getRed() - crtCs.minColor.getRed())) / delta) + crtCs.minColor.getRed();
            G = (int) ((val * (crtCs.maxColor.getGreen() - crtCs.minColor.getGreen())) / delta)
                    + crtCs.minColor.getGreen();
            B = (int) ((val * (crtCs.maxColor.getBlue() - crtCs.minColor.getBlue())) / delta)
                    + crtCs.minColor.getBlue();
        } else {
            R = (int) ((val * (crtCs.minColor.getRed() - crtCs.maxColor.getRed())) / delta) + crtCs.maxColor.getRed();
            G = (int) ((val * (crtCs.minColor.getGreen() - crtCs.maxColor.getGreen())) / delta)
                    + crtCs.maxColor.getGreen();
            B = (int) ((val * (crtCs.minColor.getBlue() - crtCs.maxColor.getBlue())) / delta)
                    + crtCs.maxColor.getBlue();
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
        return new Color(R, G, B);
    }

    public void drawConn(Graphics gg, rcNode n, rcNode n1, ILink link) {

        if (gmapPan.optPan.kbShowMST.isSelected() && isHotLink(n, n1)) {
            gg.setColor(gmapPan.optPan.csMST.minColor);
            gg.drawLine(n.x, n.y, n1.x, n1.y);
            //n.isConnected = n1.isConnected = true;
        }

        double val = getLinkQuality(link);
        if (val == -1) {
            return;
        }
        //n.isConnected = n1.isConnected = true;
        Color col = getLinkQualityColor(link);
        gg.setColor(col);

        int dd = 6;

        int dx = (n1.x - n.x);
        int dy = n1.y - n.y;
        float l = (float) Math.sqrt((dx * dx) + (dy * dy));
        float dir_x = dx / l;
        float dir_y = dy / l;
        int dd1 = dd;

        int x1p = n.x - (int) (dd1 * dir_y);
        int x2p = n1.x - (int) (dd1 * dir_y);
        int y1p = n.y + (int) (dd1 * dir_x);
        int y2p = n1.y + (int) (dd1 * dir_x);

        gg.drawLine(x1p, y1p, x2p, y2p);

        int xv = (x1p + x2p) / 2;
        int yv = (y1p + y2p) / 2;

        float aa = (dd) / (float) 2.0;

        int[] axv = { (int) ((xv - (aa * dir_x)) + (2 * aa * dir_y)), (int) (xv - (aa * dir_x) - (2 * aa * dir_y)),
                (int) (xv + (2 * aa * dir_x)), (int) ((xv - (aa * dir_x)) + (2 * aa * dir_y)) };

        int[] ayv = { (int) (yv - (aa * dir_y) - (2 * aa * dir_x)), (int) ((yv - (aa * dir_y)) + (2 * aa * dir_x)),
                (int) (yv + (2 * aa * dir_y)), (int) (yv - (aa * dir_y) - (2 * aa * dir_x)) };

        gg.fillPolygon(axv, ayv, 4);

        //int ddl = 6;
        // String lbl = "" + (int) perf ;
        String lbl = gmapPan.optPan.csNodes.formatter.format(val);
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
        gg.setColor(Color.black);
    }

    public void drawFakeConn(Graphics gg, rcNode n, rcNode n1, Color col) {

        gg.setColor(col);
        gg.drawLine(n.x, n.y, n1.x, n1.y);
    }

    public void computeNewColors() {
        mstData = ((VrvsSerMonitor) monitor).getMST();
        computeNodesColorRange();
        computeTrafficColorRange();
    }

    public void computeTrafficColorRange() {
        if (nodes == null) {
            return;
        }
        if (nodes.size() == 0) {
            return;
            // first, check all links and see which is the best and which is
            // the worst for the current quality selected in the link menu
        }

        double min_i = Double.MAX_VALUE;
        double max_i = Double.MIN_VALUE;
        double min_p = Double.MAX_VALUE;
        double max_p = Double.MIN_VALUE;
        double crt = 0.0;
        for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
            rcNode n = (rcNode) e.nextElement();
            for (Enumeration e1 = n.wconn.elements(); e1.hasMoreElements();) {
                ILink link = (ILink) e1.nextElement();
                if (link == null) {
                    continue;
                }
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
        }

        if ((min_i == Double.MAX_VALUE) || (max_i == Double.MIN_VALUE)) {
            min_i = max_i = 0;
        }
        if ((min_p == Double.MAX_VALUE) || (max_p == Double.MIN_VALUE)) {
            min_p = max_p = 0;
        }

        gmapPan.optPan.csPing.setValues(max_i, min_i); // lower value is better
        //optPan.csPeers.setValues(min_p, max_p);

        if (Math.round(min_i) == Math.round(max_i)) {
            gmapPan.optPan.csPing.setColors(Color.RED, Color.RED);
        } else {
            gmapPan.optPan.csPing.setColors(Color.RED, Color.YELLOW);
        }
    }

    public void plotTraffic(Graphics g) {

        //      for(Enumeration e = nodes.elements(); e.hasMoreElements(); ){
        //      rcNode n = ( rcNode ) e.nextElement();
        //      n.isConnected = false;
        //      }

        Object[] fnProps;
        for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
            rcNode n = (rcNode) e.nextElement();
            if ((fnProps = LTFNcontains(0, n)) != null) {//fake node
                if (!showOnlyReflectorNodes) {
                    rcNode nw = (rcNode) fnProps[2];
                    drawFakeConn(g, n, nw, Color.GRAY);
                    drawFakeConn(g, nw, n, Color.GRAY);
                }
            } else {
                for (Enumeration e1 = n.wconn.keys(); e1.hasMoreElements();) {
                    ServiceID nwsid = (ServiceID) e1.nextElement();
                    rcNode nw = (rcNode) nodes.get(nwsid);

                    ILink link = (ILink) n.wconn.get(nwsid);
                    if ((nw != null) && (!showOnlyHandledNodes || (n.isLayoutHandled && nw.isLayoutHandled))) {
                        drawConn(g, n, nw, link);
                    }
                }
            }
            ;
        }
    }

    public void computeNodesColorRange() {
        if (nodes == null) {
            return;
        }
        // check all nodes and make a statistic
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        double crt = 0.0;
        for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
            rcNode n = (rcNode) e.nextElement();
            crt = getNodeValue(n);
            if (crt == -1) {
                continue;
            }
            min = (min < crt) ? min : crt;
            max = (max > crt) ? max : crt;
        }
        if (min > max) {
            min = max = 0.0;
        }
        if (min <= max) {
            gmapPan.optPan.csNodes.setValues(min, max);
            if ((max - min) < 1E-2) {
                gmapPan.optPan.csNodes.setColors(Color.CYAN, Color.CYAN);
            } else {
                gmapPan.optPan.csNodes.setColors(Color.CYAN, Color.BLUE);
            }
        }
    }

    public void plotNodes(Graphics g) {
        boolean bMeetings = (gmapPan.optPan.nodesShow == JoptPan.NODE_MEETINGS);
        ArrayList<MeetingDetails.IDColor> lMeetingsSelected = new ArrayList<MeetingDetails.IDColor>();
        //if option selected from combo is NODE_MEETINGS, then get snapshot of meetings
        if (bMeetings) {
            lMeetingsSelected = gmapPan.optPan.meetingsUpdate.getSnapshot();
        }
        computeMaxDims(g);
        rcNode selected = null;

        rcNode toCompare = null;
        if ((userSearchHighlight != null) && (userSearchHighlight.length() > 0)) {
            Object o[] = (Object[]) users.get(userSearchHighlight);
            if (o != null) {
                toCompare = (rcNode) o[0];
            }
        }

        for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
            rcNode n = (rcNode) e.nextElement();
            //            System.out.println(n.shortName);
            if (showOnlyHandledNodes && !n.isLayoutHandled) {
                continue;
            } else if (LTFNcontains(0, n) == null) {
                paintNode(g, n, lMeetingsSelected);
            } else if (!showOnlyReflectorNodes) {
                //check to see if node should be drawn, based on the meeting he is in: if it is selected or not
                //                    System.out.println("paint fake node "+n.shortName);
                if (toCompare != null) {
                    if (toCompare.equals(n)) {
                        selected = n;
                    }
                }

                if ((selected == null) || !selected.equals(n)) {
                    paintFakeNode(g, n, lMeetingsSelected, showFullUserInfo);
                }
            }
        }
        if (selected != null) {
            paintFakeNode(g, selected, lMeetingsSelected, showFullUserInfo);
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        if (gmapPan.optPan.gmapNotif) {
            gmapPan.optPan.gmapNotif = false;
            computeNewColors();
        }
        Dimension d = getSize();
        g.setFont(nodesFont);
        g.setColor(bgColor);
        g.fillRect(0, 0, d.width, d.height);
        FontMetrics fm = g.getFontMetrics();

        //first sync the nodes list with global one
        syncNodes();
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
        plotTraffic(g);
        plotNodes(g);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
        addMouseMotionListener(this);
        double bestdist = Double.MAX_VALUE;
        int x = e.getX();
        int y = e.getY();
        pick = null;
        for (Enumeration en = nodes.elements(); en.hasMoreElements();) {
            rcNode n = (rcNode) en.nextElement();
            n.selected = false;

            if ((n == null) || (n.limits == null) || (showOnlyHandledNodes && !n.isLayoutHandled)) {
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
            pickX = pick;
            userSearchHighlight = null;
            //System.out.println("Selected lay = "+gmapPan.cbLayout.getSelectedItem());
            if (currentLayout.equals("Elastic") || gmapPan.cbLayout.getSelectedItem().equals("Layered")
                    || gmapPan.cbLayout.getSelectedItem().equals("Radial")) {
                setLayoutType((String) gmapPan.cbLayout.getSelectedItem());
            }
            repaint();
        }

        if (but == MouseEvent.BUTTON1) {
            if (pick == null) {
                //setMaxFlow ( null ) ;  
            } else {
                // setMaxFlow ( pick );
                pick.selected = true;
                if (pick.client != null) {
                    pick.client.setVisible(!pick.client.isVisible());
                }
                pick.fixed = false;
                pick = null;
                repaint();
            }
        }
        e.consume();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        removeMouseMotionListener(this);

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
    }

    @Override
    public void mouseExited(MouseEvent e) {
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
    }

    /* (non-Javadoc)
     * @see javax.swing.JComponent#getToolTipText(java.awt.event.MouseEvent)
     */
    @Override
    public String getToolTipText(MouseEvent event) {
        //System.out.println("getToolTipText called");
        int x = event.getX();
        int y = event.getY();
        rcNode tooltipNode = null;
        for (Enumeration en = nodes.elements(); en.hasMoreElements();) {
            rcNode n = (rcNode) en.nextElement();
            if ((n == null) || (n.limits == null) || (showOnlyHandledNodes && !n.isLayoutHandled)) {
                continue;
                //TODO: ingnore also invisible users due to unchecking of a meeting
                //            if ( isUnselectedMeetingNode(n) )
                //            	continue;
            }

            if (n.limits.contains(x, y)) {
                tooltipNode = n;
                break;
            }
        }
        Object[] info = LTFNcontains(0, tooltipNode);
        String tooltiptext = ((info != null) && (info.length > 5)) ? info[5].toString() : null;
        if (tooltiptext != null) {
            tooltiptext = tooltiptext.replaceAll("user((info)|(status)|(meeting))_((start)|(end))", "");
        }
        return tooltiptext;
        //return super.getToolTipText(event);
    }

    void rescaleIfNeeded() {
        //      synchronized(syncRescale){
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        //      range.setBounds(0, 0, getWidth(), getHeight());
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
        //      }
    }

    // this must be called by the LayoutTransformer thread
    @Override
    public void computeNewLayout() {
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        //      range.setBounds(0, 0, getWidth(), getHeight());
        range.setBounds((int) (panelWidth * fLayoutMargin), (int) (panelHeight * fLayoutMargin),
                (int) (panelWidth * (1 - (2 * fLayoutMargin))), (int) (panelHeight * (1 - (2 * fLayoutMargin))));
        range.grow(-wunit, -hunit);
        layout.layOut();

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

        // perform transitions
        for (int i = 0; i < nSteps; i++) {
            //          System.out.println("transition "+i);
            for (Iterator it = vGraph.gnodes.iterator(); it.hasNext();) {
                GraphNode gn = (GraphNode) it.next();
                if (!gn.rcnode.fixed) {
                    int dx = (int) ((gn.pos.x - gn.rcnode.x) / (nSteps - i));
                    int dy = (int) ((gn.pos.y - gn.rcnode.y) / (nSteps - i));
                    //                  if(gn.rcnode.UnitName.equals("CALTECH"))
                    //                  System.out.println("moving C["+gn.rcnode.x+", "+gn.rcnode.y+"] -> ["+gn.pos.x+", "+gn.pos.y+"]"); // by "+dx+", "+dy);
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
        vGraph = null;
        layout = null;
        currentLayout = "None";
        repaint();
    }

    @Override
    public int setElasticLayout() {
        //synchronized(syncRescale){
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        //      range.setBounds(0, 0, getWidth(), getHeight());
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
            //          if(layout.handled.contains(gn)){
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
            //          }
        }
        //rescaleIfNeeded();
        //}
        repaint();
        return totalMovement;
    }

    void unfixNodes() {
        for (Enumeration en = nodes.elements(); en.hasMoreElements();) {
            ((rcNode) en.nextElement()).fixed = false;
        }
    }

    void setLayoutType(String type) {
        //System.out.println("setLayoutType:"+type);
        if ((layout != null) && !type.equals(currentLayout)) {
            layout.finish();
        }
        syncNodes();
        Hashtable nodesOK = nodes;
        //here would be a smart place for removing nodes that we don't what to be considered by layout
        // the conditions for showing only some of the nodes are:
        // Meetings option in combo is selected and at least one meeting is checked
        // NOT WORKING WELL!!!
        /*		boolean bMeetings = (gmapPan.optPan.nodesShow==JoptPan.NODE_MEETINGS);
                if ( bMeetings ) {
                	ArrayList lMeetingsSelected = gmapPan.optPan.meetingsUpdate.getSnapshot();
            		boolean bAtLeastOneMeetingSel = lMeetingsSelected.size()>0;
            		if ( bAtLeastOneMeetingSel ) {
        		        nodesOK = new Hashtable();
        		        //for each element in current nodes list
        		        for ( Enumeration en=nodes.keys(); en.hasMoreElements(); ) {
        		        	Object key = en.nextElement();
        		        	rcNode node = (rcNode) nodes.get(key);
        		        	//check if it is a fake node
        		    	    Object[] fakeAttrs = LTFNcontains(0,node);
        		    	    Object [] selectedElem = null;
        		    		if ( fakeAttrs==null )
        	                	nodesOK.put(key, node);
        	                else { //fake node, so check if in a meeting or not
        		    		    for ( int i=0; i<lMeetingsSelected.size(); i++) {
        		    		        Object[] mElem = (Object[])lMeetingsSelected.get(i);
        		    		        if ( mElem[0].equals(fakeAttrs[3]) ) {//equal meeting ids
        		    		        	selectedElem = mElem;
        		    		        	break;
        		    		        }
        		    		    }
        		    		    if ( selectedElem != null )
        		    		    	nodesOK.put(key, node);
        		    		}
        		        }
            		}
                }
        */
        if (type.equals("Random") || type.equals("Grid")) {
            synchronized (syncGraphObj) {
                unfixNodes();
                vGraph = GraphTopology.constructGraphFromPeersPingAndMSTAndUsers(nodesOK, mstData,
                        gmapPan.optPan.kbShowPeers.isSelected(), gmapPan.optPan.kbShowPing.isSelected(),
                        gmapPan.optPan.kbShowMST.isSelected(), this);
                if (type.equals("Random")) {
                    layout = new RandomLayoutAlgorithm(vGraph);
                } else {
                    layout = new GridLayoutAlgorithm(vGraph);
                }
                layoutTransformer.layoutChanged();
            }
        } else if (type.equals("Radial") || type.equals("Layered")) {
            synchronized (syncGraphObj) {
                unfixNodes();
                vGraph = GraphTopology.constructGraphFromPeersPingAndMSTAndUsers(nodesOK, mstData,
                        gmapPan.optPan.kbShowPeers.isSelected(), gmapPan.optPan.kbShowPing.isSelected(),
                        gmapPan.optPan.kbShowMST.isSelected(), this);
                if ((pickX == null) || (!nodesOK.containsValue(pickX))) {
                    pickX = vGraph.findARoot();
                }
                vGraph.pruneToTree(pickX); // convert the graph to a tree
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
                    vGraph = GraphTopology.constructGraphFromPeersPingAndMSTAndUsers(nodesOK, mstData,
                            gmapPan.optPan.kbShowPeers.isSelected(), gmapPan.optPan.kbShowPing.isSelected(),
                            gmapPan.optPan.kbShowMST.isSelected(), this);
                    ((SpringLayoutAlgorithm) layout).updateGT(vGraph);
                }
            } else {
                synchronized (syncGraphObj) {
                    unfixNodes();
                    vGraph = GraphTopology.constructGraphFromPeersPingAndMSTAndUsers(nodesOK, mstData,
                            gmapPan.optPan.kbShowPeers.isSelected(), gmapPan.optPan.kbShowPing.isSelected(),
                            gmapPan.optPan.kbShowMST.isSelected(), this);
                    layout = new SpringLayoutAlgorithm(vGraph, this);
                    ((SpringLayoutAlgorithm) layout).setStiffness(gmapPan.sldStiffness.getValue());
                    ((SpringLayoutAlgorithm) layout).setRespRange(gmapPan.sldRepulsion.getValue());
                    layout.layOut();
                }
            }
        }
        currentLayout = type;
    }

    /**
     * checks new values containing meeting id for a user id
     */
    @Override
    public void newFarmResult(MLSerClient client, Object ro) {
        // 
        //		DE DEINREGISTRAT PREDICATELE CARE SE INREGISTREAZA
        //		DE PASTRAT PREDICATELE PENTRU FIECARE FAKE NODE PENTRU A PUTEA DEINREGISTRA
        if (ro == null) {
            return;
        }

        //      System.out.println("vrvs result: "+ro.getClass()); 
        if (ro instanceof Result) {
            //logger.log(Level.INFO, "VOJobs Result from "+client.farm.name+" = "+r);
        } else if (ro instanceof eResult) {
            eResult r = (eResult) ro;
            //  		System.out.println("<mluc> client "+client.getFarmName()+" received result "+r);
            //  System.out.println(" Got eResult " + ro);
            setResult(client, r);
        } else if (ro instanceof Vector) {
            Vector vr = (Vector) ro;
            //System.out.println(new Date()+" V["+vr.size()+"] from "+client.farm.name);
            for (int i = 0; i < vr.size(); i++) {
                newFarmResult(client, vr.elementAt(i));
            }
        } else {
            //logger.log(Level.WARNING, "Wrong Result type in VoJob from "+client.farm.name+": " + ro);
            return;
        }

    }

    /**
     * finds the fake node that corresponds to the touple (client,result)
     * and sets the meeting id
     * @author mluc
     * @since Jul 28, 2006
     * @param client
     * @param r
     */
    public void setResult(MLSerClient client, eResult r) {
        String userFullName = r.NodeName;
        int nFirstSep = userFullName.indexOf(VrvsNodesRenderer.MNode_PoundSign);
        int nSecondSep = userFullName.indexOf(VrvsNodesRenderer.MNode_PoundSign, nFirstSep + 1);
        if (nSecondSep == -1) {
            nSecondSep = userFullName.length();
        }
        String userID = "";
        if ((nFirstSep != -1) && (nSecondSep != -1)) {
            userID = userFullName.substring(nFirstSep + 1, nSecondSep);
        }

        //        String meetingID = "";
        //        //not the case anymore, now a predicate has to be used to find the meeting id
        //        if ( nSecondSep!=-1 && nSecondSep+1<userFullName.length() )
        //            meetingID = userFullName.substring( nSecondSep+1);
        //System.out.println("checking "+userName+" with id "+userID+" as fake node for "+node.UnitName);
        Object[] userInfo = null;
        if ((userID.length() > 0) && ((userInfo = LTFNcontains(1, userID)) != null)) {
            //we got user id from result and found the fake node corresponding to this id
            for (int i = 0; i < r.param_name.length; i++) {

                //        		System.out.println(r.param_name[i]);

                //parse the result to find the meeting id
                if ("ProxyStatus".equals(r.param_name[i])) {
                    String status = r.param[i].toString();
                    int nFirstResSep = status.indexOf(VrvsNodesRenderer.MNode_PoundSign);
                    if (nFirstResSep != -1) {
                        String meetingID = status.substring(0, nFirstResSep);
                        if ((userInfo != null) && (userInfo.length > 3) && !((String) userInfo[3]).equals(meetingID)) {
                            userInfo[3] = meetingID;
                            String meetingName = gmapPan.optPan.meetingsUpdate.getMeetingName(meetingID);
                            if (meetingName != null) {
                                String userstatus_text = "<tr><td>Meeting:</td><td>" + meetingName + "</td></tr>";
                                userInfo[5] = ((String) userInfo[5]).replaceFirst("usermeeting_start.*usermeeting_end",
                                        "usermeeting_start" + userstatus_text + "usermeeting_end");
                            } else {
                                userInfo[5] = ((String) userInfo[5]).replaceFirst("usermeeting_start.*usermeeting_end",
                                        "usermeeting_start" + "usermeeting_end");
                            }
                        }
                    }
                    ;
                }
                if ("UserInfo".equals(r.param_name[i]) && (userInfo.length > 5)) {
                    String userName = userFullName;
                    if (nFirstSep != -1) {
                        userName = userFullName.substring(0, nFirstSep);
                    }
                    //System.out.println("reformated name: "+userName.replaceAll("([a-z]+)([A-Z][a-z]+)", "$1 $2"));
                    userName = userName.replaceAll("([a-z]+)([A-Z][a-z]+)", "$1 $2");
                    String userinfo_text = "";
                    String separator = "#";
                    String[] values = ((String) r.param[i]).split(separator);
                    if (values.length > 3) {
                        userinfo_text = //"<html><table border=0 cellspacing=1 cellpadding=0>"
                        "<tr><td>UserID:</td><td>" + values[0] + "</td></tr>" + "<tr><td>Name:</td><td>" + userName
                                + "</td></tr>" + "<tr><td>Email:</td><td>" + values[2] + "</td></tr>"
                                + "<tr><td>Location:</td><td>" + values[3] + "</td></tr>";
                        userInfo[5] = ((String) userInfo[5]).replaceFirst("userinfo_start.*userinfo_end",
                                "userinfo_start" + userinfo_text + "userinfo_end");
                    }
                }
                if ("UserStatus".equals(r.param_name[i]) && (userInfo.length > 5)) {
                    String userstatus_text = "";
                    String separator = "#";
                    String[] values = ((String) r.param[i]).split(separator);
                    if (values.length > 3) {
                        userstatus_text = //"<html><table border=0 cellspacing=1 cellpadding=0>"
                        "<tr><td>Communities:</td><td>" + values[1] + "</td></tr>"
                                + "<tr><td colspan=2>------------</td></tr>" + "<tr><td>Status:</td><td>" + values[0]
                                + "</td></tr>" + "<tr><td>Idle time:</td><td>" + values[3] + "</td></tr>";
                        userInfo[5] = ((String) userInfo[5]).replaceFirst("userstatus_start.*userstatus_end",
                                "userstatus_start" + userstatus_text + "userstatus_end");
                        if (userInfo.length > 6) {
                            userInfo[6] = values[0];
                        }
                    }
                }
            }
            //parse the result to find the meeting id
            //        	if ( "ProxyStatus".equals(r.param_name[0]) ) {
            //            	String status = r.param[0].toString();
            //                int nFirstResSep = status.indexOf(VrvsNodesRenderer.MNode_PoundSign);
            //                if ( nFirstResSep!=-1 ) {
            //                	String meetingID = status.substring(0, nFirstResSep);
            //                    if ( userInfo!=null && !((String)userInfo[3]).equals(meetingID) ) {
            //                        userInfo[3] = meetingID;
            ////                        System.out.println("<mluc> new result: userID="+userID+" is in meeting "+meetingID);
            //                    }
            //                };
            //        	}
        }
    }

}
