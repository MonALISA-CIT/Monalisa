/**
 * Aug 14, 2006 - 10:14:11 AM
 * mluc - MonALISA1
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl.Grids;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import lia.Monitor.JiniClient.CommonGUI.MainBase;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Jogl.DataRenderer;
import lia.Monitor.JiniClient.CommonGUI.Jogl.JoglPanel;
import lia.Monitor.monitor.GridSiteInfo;
import lia.Monitor.monitor.ILink;
import lia.Monitor.monitor.LcgLdapInfo;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.MLSerClient;
import net.jini.core.lookup.ServiceID;

/**
 * @author mluc
 *
 */
public class GridsJoglPanel extends JoglPanel implements LocalDataFarmClient, ActionListener {
    private static final long serialVersionUID = -3246699653643938276L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(GridsJoglPanel.class.getName());

    /** integer value to create a serviceID object needed for fake nodes */
    private static int nextServiceID = 1;
    //	private long lLastUpdateNodeTime = -1;
    //	private long lMinUpdateNodeTime = 30000;
    /**
     * contains a reference from node to the predicate with whom is registered
     */
    private final HashMap hNodesPreds = new HashMap();

    /** contains a list of grid sites, of type GridSite */
    public ArrayList sites = new ArrayList();

    /** global updated snodes */
    public Hashtable snodesGlobal;
    /** global updated vnodes */
    public Vector vnodesGlobal;

    private final String gridmap_suffix = "_GridMap";
    public static final String OSG_GRIDMAP_PREFIX = "OSG";
    public static final String NDGF_GRIDMAP_PREFIX = "NDGF";
    public static final String EGEE_GRIDMAP_PREFIX = "EGEE";

    /** grids select buttons */
    JToggleButton jtbEGEE, jtbNDG, jtbOSG, jtbLCG;

    /** dimensions for egee button */
    Dimension egeeSize = new Dimension(81 + 110, 34);
    Dimension egeeMaxSize = new Dimension(81 + 140, 42);

    public GridsJoglPanel() {
        super();
    }

    @Override
    public void init() {
        super.init();
        //set renderer for nodes
        GridsNodesRenderer gnr = new GridsNodesRenderer();
        renderer.addNodesRenderer(gnr, "Normal view");
        //renderer.addNodesRenderer( new FarmsOnTopNodesRenderer(3, 32), "OnTop view");
        renderer.setActiveNodesRenderer(0);
        DataRenderer.addGlobeListener(gnr);

        checkToolbar();

        ((GridsNodesRenderer) renderer.getActiveNodesRenderer()).setLinks(renderer.getLinks());
        ((GridsNodesRenderer) renderer.getActiveNodesRenderer()).changeLinkAnimationStatus(jtbLCG.isSelected());

        //TODO: create a background worker to check for dead nodes
    }

    @Override
    public void setSerMonitor(SerMonitorBase ms) {
        super.setSerMonitor(ms);
        //		System.out.println("set ser monitor");
        //		System.out.println("[2] options panel: "+optionsPanelGrids);
        if (optionsPanelGrids != null) {
            //			System.out.println("add logo");
            optionsPanelGrids.add(monitor.main.createCaltechLogoPanel());
            optionsPanelGrids.add(Box.createHorizontalStrut(2));
        }
    }

    public JPanel optionsPanelGrids;

    @Override
    protected void buildOptPan() {
        Insets mBut = new Insets(2, 2, 2, 2);
        //		Border raisedetched = BorderFactory.createEtchedBorder(EtchedBorder.RAISED);
        optionsPanelGrids = new JPanel();
        optionsPanelGrids.setLayout(new BoxLayout(optionsPanelGrids, BoxLayout.X_AXIS));
        jtbLCG = new JToggleButton("<html><center>LCG<br>Tier-0 to Tier-1",
                MainBase.loadIcon("lia/images/grids/lcg_logo.png"), true);//<html><center>LCG<br>(Tier-0/Tier-1)
        if (jtbLCG.getBorder() instanceof javax.swing.plaf.BorderUIResource.CompoundBorderUIResource) {
            jtbLCG.setMargin(mBut);
        }
        //		jtbLCG.setBorder(raisedetched);
        jtbLCG.setFont(fontDefault);
        jtbLCG.addActionListener(this);
        optionsPanelGrids.add(jtbLCG);
        optionsPanelGrids.add(Box.createHorizontalStrut(10));
        jtbEGEE = new JToggleButton("<html>Enabling Grids<br>for E-sciencE",
                MainBase.loadIcon("lia/images/grids/egee_logo.png"), true);
        if (jtbEGEE.getBorder() instanceof javax.swing.plaf.BorderUIResource.CompoundBorderUIResource) {
            jtbEGEE.setMargin(mBut);
        }
        //		jtbEGEE.setBorder(raisedetched);
        jtbEGEE.setFont(fontDefault);
        jtbEGEE.setPreferredSize(egeeSize);
        //		jtbEGEE.setSize(egeeSize);
        jtbEGEE.setMaximumSize(egeeMaxSize);
        jtbEGEE.addActionListener(this);
        optionsPanelGrids.add(jtbEGEE);
        optionsPanelGrids.add(Box.createHorizontalStrut(10));
        jtbOSG = new JToggleButton("Open Science Grid", MainBase.loadIcon("lia/images/grids/osg_logo.png"), true);
        if (jtbOSG.getBorder() instanceof javax.swing.plaf.BorderUIResource.CompoundBorderUIResource) {
            jtbOSG.setMargin(mBut);
        }
        //		jtbOSG.setBorder(raisedetched);
        jtbOSG.setFont(fontDefault);
        jtbOSG.addActionListener(this);
        optionsPanelGrids.add(jtbOSG);
        optionsPanelGrids.add(Box.createHorizontalStrut(10));
        jtbNDG = new JToggleButton("NorduGrid", MainBase.loadIcon("lia/images/grids/nordugrid_logo.png"), true);
        if (jtbNDG.getBorder() instanceof javax.swing.plaf.BorderUIResource.CompoundBorderUIResource) {
            jtbNDG.setMargin(mBut);
        }
        //		jtbNDG.setBorder(raisedetched);
        jtbNDG.setFont(fontDefault);
        jtbNDG.addActionListener(this);
        optionsPanelGrids.add(jtbNDG);
        //JLabel jlbLegend = new JLabel(MainBase.loadIcon("lia/images/grids/lcg_legend2.png"));
        //optionsPanelGrids.add(jlbLegend);
        optionsPanelGrids.add(Box.createHorizontalGlue());
        //		System.out.println("[1] options panel: "+optionsPanelGrids);
        optionsPanelGrids.add(Box.createHorizontalStrut(2));
        toolbarsPanel.add(optionsPanelGrids);
        //		System.out.println("2>size: "+jtbNDG.getSize());
    }

    boolean bFirstTimeDraw = true;

    /* (non-Javadoc)
     * @see javax.swing.JComponent#paint(java.awt.Graphics)
     */
    @Override
    public void paint(Graphics g) {
        // TODO Auto-generated method stub
        super.paint(g);
        if (bFirstTimeDraw) {
            bFirstTimeDraw = false;
            //			System.out.println("3>size: "+jtbNDG.getSize());
            int height = jtbNDG.getSize().height;
            egeeSize.height = height;
            egeeMaxSize.height = height;
            jtbEGEE.setPreferredSize(egeeSize);
            jtbEGEE.setSize(egeeSize);
            jtbEGEE.setMinimumSize(egeeSize);
            jtbEGEE.setMaximumSize(egeeMaxSize);
            //			System.out.println("<debug> font: "+jtbNDG.getFont());
            //			System.out.println("<debug> margins: "+jtbNDG.getMargin());
            //			System.out.println("<debug> icon-text gap: "+jtbNDG.getIconTextGap());
            //			System.out.println("<debug> border: "+jtbNDG.getBorder());
            //	        System.out.println("<debug> toolbarspanel2 margins: "+toolbarsPanel2.getInsets());
            //	        System.out.println("<debug> toolbarspanel2 size: "+toolbarsPanel2.getSize());
        }
    }

    public boolean bFirstNode = true;

    @Override
    public synchronized void gupdate() {
        //TODO: create a timer to check for new nodes as gupdate is randomly run
        //a new node has appeared, hopefully, register for data of interest
        //		 System.out.println("a new node has appeared");
        //		long lCurrentTime = NTPDate.currentTimeMillis();
        //		if ( lLastUpdateNodeTime==-1 || lCurrentTime-lLastUpdateNodeTime>lMinUpdateNodeTime ) {
        //			lLastUpdateNodeTime = lCurrentTime;
        for (int i = 0; i < vnodesGlobal.size(); i++) {
            rcNode node = (rcNode) vnodesGlobal.get(i);
            //				System.out.println("<mluc> global Node "+node);
            if ((node != null) && (node.client != null)) {
                Integer ikey = (Integer) hNodesPreds.get(node.sid);
                //					System.out.println("<mluc> Node "+node+" has sid "+node.sid+" and key "+ikey);
                if ((ikey == null) || !node.client.containsPredicate(ikey)) {
                    //register for grids topology information
                    monPredicate predGrids;
                    predGrids = new monPredicate("*", "%" + gridmap_suffix, "*", -120000, -1, new String[] { "Info" },
                            null);
                    //						 predGrids = new monPredicate ("*","%_Sites", "*", -60000, -1 , null , null);
                    //						 System.out.println("registered for lcg info for node "+node);
                    node.client.addLocalClient(this, predGrids);
                    if (bFirstNode) {
                        bFirstNode = false;
                        logger.info("Registration of first node.");
                        //							 System.out.println("<mluc> Registration of first node.");
                    }
                    logger.info("MonALISA service that provides GridMap info is " + node.shortName + " ["
                            + node.IPaddress + "]");
                    //						 System.out.println("<mluc> MonALISA service that provides GridMap info is "+node.shortName+" ["+node.IPaddress+"]");
                    hNodesPreds.put(node.sid, Integer.valueOf(predGrids.id));
                    //						 System.out.println("<mluc> "+node.shortName+" ["+node.IPaddress+"] has predicate "+predGrids);
                }
            } //else
              //					System.out.println("<mluc> "+node.shortName+" ["+node.IPaddress+"] doesn't have client");
        }
        //remove all nodes not present in vnodes

        //		}
        super.gupdate();
    }

    public boolean bFirstResultReceived = true;

    @Override
    public void newFarmResult(MLSerClient client, Object res) {
        boolean bNewNode = false;
        if (res instanceof eResult) {
            bNewNode = newFarmResultFromVector(client, res);
        } else if (res instanceof Vector) {
            Vector vr = (Vector) res;
            //System.out.println(new Date()+" V["+vr.size()+"] from "+client.farm.name);
            for (int i = 0; i < vr.size(); i++) {
                bNewNode |= newFarmResultFromVector(client, vr.elementAt(i));
            }
        }
        if (bNewNode) {
            DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_OPTION_PANEL_CHANGE,
                    Integer.valueOf(DataRenderer.GLOBE_EVENT_PARAM_NODES_CHANGED));
        }
    }

    private boolean newFarmResultFromVector(MLSerClient client, Object res) {
        boolean bNewNode = false;
        if (res instanceof eResult) {
            //the result we're interested on
            //should get parsed
            eResult eRes = (eResult) res;
            //get grid name
            String gridName = eRes.ClusterName;
            if (gridName.endsWith(gridmap_suffix)) {
                gridName = gridName.substring(0, gridName.length() - gridmap_suffix.length());
            }
            //			System.out.println("got result for grid "+gridName+" res="+res);
            for (int i = 0; i < eRes.param_name.length; i++) {
                if (eRes.param_name[i].equals("Info")) {
                    //deserialize object
                    byte[] array = (byte[]) eRes.param[i];
                    if (array != null) {
                        try {
                            ByteArrayInputStream bais = new ByteArrayInputStream(array);
                            ObjectInputStream ois;
                            ois = new ObjectInputStream(bais);
                            Object info = ois.readObject();
                            if ((info instanceof LcgLdapInfo) || (info instanceof GridSiteInfo)) {
                                //								System.out.println("notify result "+eRes.NodeName);
                                try {
                                    //									long lStart = System.currentTimeMillis();
                                    bNewNode |= newResult(gridName, eRes.NodeName, info);
                                    //									long lEnd = System.currentTimeMillis();
                                    //									if ( lEnd - lStart > 3 )
                                    //										System.out.println("result for "+eRes.NodeName+" parsed in "+(lEnd-lStart)+" milis");
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                }
                            }
                        } catch (IOException ex) {
                            // TODO Auto-generated catch block
                            ex.printStackTrace();
                        } catch (ClassNotFoundException ex) {
                            // TODO Auto-generated catch block
                            ex.printStackTrace();
                        }
                    }
                }
            }
        }
        return bNewNode;
    }

    public void setNodes(Hashtable<ServiceID, rcNode> snodes, Vector<rcNode> vnodes) {
        vnodesGlobal = vnodes;
        snodesGlobal = snodes;
        dglobals.vnodes = new Vector();
        dglobals.snodes = new Hashtable();
    }

    public synchronized ServiceID getNextSID() {
        ServiceID sid = new ServiceID(nextServiceID, nextServiceID);
        nextServiceID++;
        return sid;
    }

    /**
     * checks if a node is visible based on it's grid name
     * and on the currently selected grids
     * @author mluc
     * @since Oct 19, 2006
     * @param nodeGrid the name of the grid that contains the node
     * @return boolean value indicating the visibility
     */
    public boolean checkVisibleGrid(String nodeGrid) {
        if (jtbEGEE.isSelected() && nodeGrid.equals(EGEE_GRIDMAP_PREFIX)) {
            return true;
        }
        if (jtbNDG.isSelected() && nodeGrid.equals(NDGF_GRIDMAP_PREFIX)) {
            return true;
        }
        if (jtbOSG.isSelected() && nodeGrid.equals(OSG_GRIDMAP_PREFIX)) {
            return true;
        }
        return false;
    }

    /**
     * @author mluc
     * @since Aug 14, 2006
     * @param client
     * @param info
     * @return if a new node was created for the received data
     */
    private boolean newResult(String gridName, String siteAddress, Object info) {
        //		String sID = gridName+"#"+siteAddress;
        //System.out.println("New result for "+gridName+"#"+siteAddress);
        //		if ( siteAddress.equals("IEPSAS-Kosice")
        //				|| siteAddress.equals("TW-FTT")
        //				|| siteAddress.equals("Taiwan-NCUCC-LCG2")
        //				|| siteAddress.equals("Taiwan-LCG2")
        //				)
        //			System.out.println("node "+siteAddress+"\nconnected to: "+((LcgLdapInfo)info).connectedTo);
        //if site already received, update information
        //TODO: a list of events should be used

        rcNode node = null;
        //find the node with the characteristics: gridName, siteAddress
        for (int i = 0; i < dglobals.vnodes.size(); i++) {
            rcNode aNode = dglobals.vnodes.get(i);
            if (aNode.UnitName.equals(siteAddress) && aNode.CITY.equals(gridName)) {
                node = aNode;
                break;
            }
            ;
        }

        boolean bNewNode = false;
        if (info instanceof LcgLdapInfo) {
            LcgLdapInfo lcgSite = (LcgLdapInfo) info;
            //because the provided lcgSite.name is invalid, update it with siteAddress
            lcgSite.name = siteAddress;
            if (lcgSite.name.equals("CERN-PROD")) {
                lcgSite.webURL = "http://rosy.web.cern.ch/rosy/GridCast/pageCERN.html";
            }
            bNewNode = newLCGLdapResult(gridName, node, lcgSite);
        } else if (info instanceof GridSiteInfo) {
            GridSiteInfo gridSite = (GridSiteInfo) info;
            bNewNode = newGridSiteResult(gridName, node, gridSite);
        }

        //		
        //		GridSite newSiteInfo = new GridSite(gridName, siteAddress, siteType, info);
        //		int index = -1;
        //		if ( (index=sites.indexOf(newSiteInfo))>=0 ) {
        //			newSiteInfo = (GridSite)sites.get(index);
        //			//should i have a timeout for info? how to update site address...?
        //			newSiteInfo.objInfo = info;
        //			for ( int i=0; i<dglobals.vnodes.size(); i++) {
        //				if ( )
        //			}
        //		}

        return bNewNode;
    }

    /**
     * processes the lcg ldap info by creating/updating a node and its' links
     * @author mluc
     * @since Oct 22, 2006
     * @param gridName lcg grid name as reported by farm 
     * @param node representation on map
     * @param lcgSite lcg information
     * @return if the information is the first one for the node
     */
    private boolean newLCGLdapResult(String gridName, rcNode node, LcgLdapInfo lcgSite) {
        if (node != null) {
            //try to update the information for this node
            //			if ( !lcgSite.name.equals(node.UnitName) ) {
            //notify site name changed
            //				node.UnitName = lcgSite.name;
            //			}
            node.LONG = "" + lcgSite.longitude;
            node.LAT = "" + lcgSite.latitude;
            node.szOpticalSwitch_Name = lcgSite.webURL;
            if (node.szOpticalSwitch_Name != null) {
                if (!node.szOpticalSwitch_Name.startsWith("http://")) {
                    node.szOpticalSwitch_Name = "http://" + node.szOpticalSwitch_Name;
                }
            }
            StringBuilder buf = new StringBuilder();
            if (lcgSite.siteLocation != null) {
                buf.append("Location: ").append(lcgSite.siteLocation);
            } else {
                buf.append("Location: N/A");
            }
            if (lcgSite.webURL != null) {
                buf.append("\nWeb: ").append(lcgSite.webURL);
            } else {
                buf.append("\nWeb: N/A");
            }
            if (lcgSite.siteSupportContact != null) {
                buf.append("\nContact: ").append(
                        (lcgSite.siteSupportContact.startsWith("mailto: ") ? lcgSite.siteSupportContact.substring(8)
                                : lcgSite.siteSupportContact));
            }
            buf.append("\nSite type: ").append((lcgSite.tierType >= 0 ? "Tier " + lcgSite.tierType : "N/A"));
            //			node.shortName += "\nAvg RAM size: "+lcgSite.mainMemoryRAMSize;
            if ((lcgSite.operatingSystemName != null)
                    && ((lcgSite.operatingSystemRelease != null) & (lcgSite.operatingSystemVersion != null))) {
                buf.append("\nOperating system: ").append(lcgSite.operatingSystemName).append(" ")
                        .append(lcgSite.operatingSystemVersion).append(" ").append(lcgSite.operatingSystemRelease);
            }
            if (lcgSite.processorModel != null) {
                buf.append("\nProcessor model: ").append(lcgSite.processorModel);
            }
            node.shortName = buf.toString();
            node.myID = lcgSite.tierType;
            if (node.myID == 0) {
                node.errorCount = 0;
            }
            //if the link to the other end has changed, then remove the current link
            //and add a new one
            if ((node.IPaddress != null)
                    && ((lcgSite.connectedTo == null) || !node.IPaddress.equals(lcgSite.connectedTo))) {
                node.IPaddress = lcgSite.connectedTo;
                //remove the link
                node.conn.clear();
                //add a new link, if the case
                checkLinks();
            }
            ;
        } else if (!lcgSite.name.equals("N/A") && (lcgSite.longitude != 0) && (lcgSite.latitude != 0)) {
            if (bFirstResultReceived) {
                bFirstResultReceived = false;
                logger.info("First result received");
            }
            //System.out.println("Add node $"+lcgSite.name+"$");
            node = new rcNode();
            node.conn = new Hashtable();
            node.sid = getNextSID();
            //			node.IPaddress = siteAddress;
            node.CITY = gridName;
            node.UnitName = lcgSite.name;
            node.LONG = "" + lcgSite.longitude;
            node.LAT = "" + lcgSite.latitude;
            node.szOpticalSwitch_Name = lcgSite.webURL;
            if (node.szOpticalSwitch_Name != null) {
                if (!node.szOpticalSwitch_Name.startsWith("http://")) {
                    node.szOpticalSwitch_Name = "http://" + node.szOpticalSwitch_Name;
                }
            }
            StringBuilder buf = new StringBuilder();
            if (lcgSite.siteLocation != null) {
                buf.append("Location: ").append(lcgSite.siteLocation);
            } else {
                buf.append("Location: N/A");
            }
            if (lcgSite.webURL != null) {
                buf.append("\nWeb: ").append(lcgSite.webURL);
            } else {
                buf.append("\nWeb: N/A");
            }
            if (lcgSite.siteSupportContact != null) {
                buf.append("\nContact: ").append(
                        (lcgSite.siteSupportContact.startsWith("mailto: ") ? lcgSite.siteSupportContact.substring(8)
                                : lcgSite.siteSupportContact));
            }
            buf.append("\nSite type: ").append((lcgSite.tierType >= 0 ? "Tier " + lcgSite.tierType : "N/A"));
            //			node.shortName += "\nAvg RAM size: "+lcgSite.mainMemoryRAMSize;
            if ((lcgSite.operatingSystemName != null)
                    && ((lcgSite.operatingSystemRelease != null) & (lcgSite.operatingSystemVersion != null))) {
                buf.append("\nOperating system: ").append(lcgSite.operatingSystemName).append(" ")
                        .append(lcgSite.operatingSystemVersion).append(" ").append(lcgSite.operatingSystemRelease);
            }
            if (lcgSite.processorModel != null) {
                buf.append("\nProcessor model: ").append(lcgSite.processorModel);
            }
            node.shortName = buf.toString();

            node.bHiddenOnMap = !checkVisibleGrid(node.CITY);
            //			System.out.println("node "+node.UnitName+" is hidden? "+node.bHiddenOnMap);
            node.myID = lcgSite.tierType;
            node.IPaddress = lcgSite.connectedTo;
            if (node.myID == 0) {
                node.errorCount = 0;
                //				System.out.println("node "+lcgSite);
            } else {
                node.errorCount = lcgSite.tierType;
            }
            dglobals.snodes.put(node.sid, node);
            dglobals.vnodes.add(node);
            //			System.out.println("adding grid site "+siteAddress+" with info:\n"+lcgSite.toString());
            //when a new node is inserted, all links have to be checked
            checkLinks();
            return true;
        }
        return false;
    }

    /**
     * processes the grid ldap info by creating/updating a node and its' links
     * @author mluc
     * @since Oct 22, 2006
     * @param gridName grid name as reported by farm 
     * @param node representation on map
     * @param gridSite information about the grid site
     * @return if the information is the first one for the node
     */
    private boolean newGridSiteResult(String gridName, rcNode node, GridSiteInfo gridSite) {
        if (node != null) {
            //try to update the information for this node
            //			if ( !lcgSite.name.equals(node.UnitName) ) {
            //notify site name changed
            //				node.UnitName = lcgSite.name;
            //			}
            node.LONG = "" + gridSite.longitude;
            node.LAT = "" + gridSite.latitude;
            node.szOpticalSwitch_Name = gridSite.webURL;
            if (node.szOpticalSwitch_Name != null) {
                if (!node.szOpticalSwitch_Name.startsWith("http://")) {
                    node.szOpticalSwitch_Name = "http://" + node.szOpticalSwitch_Name;
                }
            }
            StringBuilder buf = new StringBuilder();
            if (gridSite.siteLocation != null) {
                buf.append("Location: ").append(gridSite.siteLocation);
            } else {
                buf.append("Location: N/A");
            }
            if (gridSite.niceName != null) {
                buf.append("\nName: ").append(gridSite.niceName);
            }
            if (gridSite.webURL != null) {
                buf.append("\nWeb: ").append(gridSite.webURL);
            } else {
                buf.append("\nWeb: N/A");
            }
            if (gridSite.siteSupportContact != null) {
                buf.append("\nContact: ").append(
                        (gridSite.siteSupportContact.startsWith("mailto: ") ? gridSite.siteSupportContact.substring(8)
                                : gridSite.siteSupportContact));
            }
            //			buf.append("\nSite type: ").append((gridSite.tierType>=0?"Tier "+gridSite.tierType:"N/A"));
            node.shortName = buf.toString();
            //if the link to the other end has changed, then remove the current link
            //and add a new one
            if ((node.IPaddress != null)
                    && ((gridSite.connectedTo == null) || !node.IPaddress.equals(gridSite.connectedTo))) {
                node.IPaddress = gridSite.connectedTo;
                //remove the link
                node.conn.clear();
                //add a new link, if the case
                checkLinks();
            }
            ;
            return true;
        } else if (!gridSite.name.equals("N/A") && (gridSite.longitude != 0) && (gridSite.latitude != 0)) {
            if (bFirstResultReceived) {
                bFirstResultReceived = false;
                logger.info("First result received");
            }
            //System.out.println("Add node $"+lcgSite.name+"$");
            node = new rcNode();
            node.conn = new Hashtable();
            node.sid = getNextSID();
            //			node.IPaddress = siteAddress;
            node.CITY = gridName;
            node.UnitName = gridSite.name;
            node.LONG = "" + gridSite.longitude;
            node.LAT = "" + gridSite.latitude;
            node.szOpticalSwitch_Name = gridSite.webURL;
            if (node.szOpticalSwitch_Name != null) {
                if (!node.szOpticalSwitch_Name.startsWith("http://")) {
                    node.szOpticalSwitch_Name = "http://" + node.szOpticalSwitch_Name;
                }
            }
            StringBuilder buf = new StringBuilder();
            if (gridSite.siteLocation != null) {
                buf.append("Location: ").append(gridSite.siteLocation);
            } else {
                buf.append("Location: N/A");
            }
            if (gridSite.niceName != null) {
                buf.append("\nName: ").append(gridSite.niceName);
            }
            if (gridSite.webURL != null) {
                buf.append("\nWeb: ").append(gridSite.webURL);
            } else {
                buf.append("\nWeb: N/A");
            }
            if (gridSite.siteSupportContact != null) {
                buf.append("\nContact: ").append(
                        (gridSite.siteSupportContact.startsWith("mailto: ") ? gridSite.siteSupportContact.substring(8)
                                : gridSite.siteSupportContact));
            }
            buf.append("\nSite type: ").append((gridSite.tierType >= 0 ? "Tier " + gridSite.tierType : "N/A"));
            node.shortName = buf.toString();

            node.bHiddenOnMap = !checkVisibleGrid(node.CITY);
            node.myID = 2;//1;
            if (gridName.equals(NDGF_GRIDMAP_PREFIX)) {
                node.errorCount = -2;
            } else if (gridName.equals(OSG_GRIDMAP_PREFIX)) {
                node.errorCount = -3;
            }
            node.IPaddress = gridSite.connectedTo;
            //			if ( !node.UnitName.equals("-1") ) {
            //				node.IPaddress = "-1";//gridSite.connectedTo;
            //				System.out.println("try to connect "+node.UnitName+" to root (-1)");
            //			}
            dglobals.snodes.put(node.sid, node);
            dglobals.vnodes.add(node);
            //			System.out.println("adding grid site "+siteAddress+" with info:\n"+lcgSite.toString());
            //when a new node is inserted, all links have to be checked
            checkLinks();
            //			System.out.println("Node "+node.UnitName+" has "+node.conn.size()+" links");
            //			if ( node.conn.size()>0 )
            //				for( Enumeration en = node.conn.elements(); en.hasMoreElements(); )
            //					System.out.println("link: "+en.nextElement());
        }
        return false;
    }

    /**
     * removes invalid links by checking if destination node still exists<br>
     * checks the connectedTo attribute for each node to add new links
     * @author mluc
     * @since Aug 16, 2006
     */
    private void checkLinks() {
        //also find the root, the node with type = 0
        //		int tier1Count = 0;
        //check first for dead links
        for (Object element : dglobals.vnodes) {
            rcNode aNode = (rcNode) element;
            try {
                //for each link, check if destination still available
                for (Iterator itLink = aNode.conn.keySet().iterator(); itLink.hasNext();) {
                    if (!dglobals.vnodes.contains(itLink.next())) {
                        itLink.remove();
                    }
                }
            } catch (Exception ex) {
                //probably concurent exception, ignore it
            }
        }

        //add any new link
        int colorID = 1;
        for (Object element : dglobals.vnodes) {
            rcNode node1 = (rcNode) element;
            for (Object element2 : dglobals.vnodes) {
                rcNode node2 = (rcNode) element2;
                if (node1.CITY.equals(node2.CITY)) {
                    //same grid
                    //CHECK FIRST IF LINK ALREADY ADDED
                    if ((node1.IPaddress != null) && (node2.UnitName != null) && node1.IPaddress.equals(node2.UnitName)
                            && !node1.conn.containsKey(node2)) {
                        //we have a link
                        ILink ilink = new ILink(node2.UnitName + "->" + node1.UnitName);
                        node1.conn.put(node2, ilink);
                        //						System.out.println("added link "+ilink);
                        if ((node1.myID == 1) && (node2.myID == 0)) {
                            node1.errorCount = colorID++;
                            //							tier1Count ++;
                        }
                        ;
                    }
                }
            }
        }
        //		System.out.println("number of tier 1 sites is "+tier1Count);
        //set for each node and its links an id that establishes the color that will be used for it
        //tier 0 node has the first and default color
        boolean bColorSet = true;
        while (bColorSet) {
            bColorSet = false;
            for (Object element : dglobals.vnodes) {
                rcNode node1 = (rcNode) element;
                if ((node1.errorCount == -1) && (node1.myID != 0) && (node1.conn.size() > 0)) {
                    rcNode node2 = (rcNode) node1.conn.keys().nextElement();
                    if (node2.errorCount > 0) {
                        bColorSet = true;
                        node1.errorCount = node2.errorCount;
                    }
                }
            }

        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        //when lcg grid visibility is changed, modify all nodes
        if (e.getSource() instanceof JToggleButton) {
            JToggleButton togbut = (JToggleButton) e.getSource();
            boolean bHidden = !togbut.isSelected();
            String selGrid = "";
            boolean bLinksChanged = false;
            if (e.getSource() == jtbEGEE) {
                selGrid = EGEE_GRIDMAP_PREFIX;
            } else if (e.getSource() == jtbNDG) {
                selGrid = NDGF_GRIDMAP_PREFIX;
            } else if (e.getSource() == jtbOSG) {
                selGrid = OSG_GRIDMAP_PREFIX;
            } else if (e.getSource() == jtbLCG) {
                bLinksChanged = true;
                ((GridsNodesRenderer) renderer.getActiveNodesRenderer()).changeLinkAnimationStatus(togbut.isSelected());
            }
            boolean bAtLeastOneNode = false;
            if (selGrid.length() > 0) {
                for (Object element : dglobals.vnodes) {
                    rcNode node = (rcNode) element;
                    if (node.CITY.equals(selGrid)) {
                        node.bHiddenOnMap = bHidden;
                        bAtLeastOneNode = true;
                    }
                    ;
                }
            }
            if (bAtLeastOneNode || bLinksChanged) {
                DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_OPTION_PANEL_CHANGE,
                        Integer.valueOf(DataRenderer.GLOBE_EVENT_PARAM_LINKS_CHANGED));
            }
            if (bAtLeastOneNode) {
                DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_OPTION_PANEL_CHANGE,
                        Integer.valueOf(DataRenderer.GLOBE_EVENT_PARAM_NODES_CHANGED));
            }
        }
    }

}
