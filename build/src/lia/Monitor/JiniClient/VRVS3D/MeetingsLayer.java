/*
 * Created on May 2, 2005 9:57:59 PM
 * Filename: MeetingsLayer.java
 */
package lia.Monitor.JiniClient.VRVS3D;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.MLSerClient;
import net.jini.core.lookup.ServiceID;

/**
 * @author Luc
 *         MeetingsLayer<br>
 *         consists of an set of functions and variables that
 *         facilitates the construction of a menu from a list of meetings
 */
public class MeetingsLayer implements ActionListener, LocalDataFarmClient {

    private final static class MeetingComp implements Comparator<String[]> {

        @Override
        public int compare(String[] s1, String[] s2) {
            return s1[1].trim().compareToIgnoreCase(s2[1].trim());
        }
    }

    private final static Pattern p = Pattern.compile("^[^#]+#[^#]+#([^#]+)+#.");

    private final static MeetingComp comparator = new MeetingComp();

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(MeetingsLayer.class.getName());

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

    private final Color[] pColors = new Color[] { LIGHT_BLUE, LIGHT_GREEN, LIGHT_YELLOW, LIGHT_MAGENTA, LIGHT_CYAN,
            LIGHT_RED, Color.lightGray, Color.blue, Color.green, Color.yellow, Color.orange, Color.magenta, Color.cyan,
            Color.pink, Color.red, Color.gray, DARK_BLUE, DARK_GREEN, DARK_YELLOW, DARK_MAGENTA, DARK_CYAN, DARK_RED,
            Color.darkGray, VERY_DARK_BLUE, VERY_DARK_GREEN, VERY_DARK_YELLOW, VERY_DARK_MAGENTA, VERY_DARK_CYAN,
            VERY_DARK_RED, VERY_LIGHT_BLUE, VERY_LIGHT_GREEN, VERY_LIGHT_YELLOW, VERY_LIGHT_MAGENTA, VERY_LIGHT_CYAN,
            VERY_LIGHT_RED };

    public ArrayList<Color> lAvailableColors = new ArrayList<Color>();

    private final ConcurrentMap<String, Set<String>> communitiesMeetingsMap = new ConcurrentHashMap<String, Set<String>>();

    private final ConcurrentMap<String, Boolean> communityState = new ConcurrentHashMap<String, Boolean>();

    private volatile boolean communityUpdated = true;

    private volatile boolean selectAllCommunities = true;

    private volatile boolean selectAllMeetings = true;

    // HashMap hmMeetings2Colors = new HashMap();
    ActionListener parent;// parent panel that should be notified of menu items actions

    public MeetingsLayer(ActionListener parent) {
        this.parent = parent;
        for (Color pColor : pColors) {
            lAvailableColors.add(pColor);
        }
        communitiesMeetingsMap.put("other", new HashSet<String>());
        communityState.put("other", Boolean.TRUE);
    }

    private final Object syncMeetingObject = new Object();

    private final HashMap<String, List<Object>> miNodes = new HashMap<String, List<Object>>();

    public static final char MNode_PoundSign = '#';

    private final String meetingIDFromName(final String fullMeetingName) {
        final int idx = fullMeetingName.indexOf(MNode_PoundSign);
        if ((idx <= 0) || (idx >= fullMeetingName.length())) {
            return null;
        }
        return fullMeetingName.substring(idx + 1);
    }

    private final String meetingNameFromName(final String fullMeetingName) {
        final int idx = fullMeetingName.indexOf(MNode_PoundSign);
        if ((idx <= 0) || (idx >= fullMeetingName.length())) {
            return fullMeetingName;
        }
        return fullMeetingName.substring(0, idx);
    }

    /**
     * from list of nodes received creates a list of objects, as arrays of two strings,
     * each element containing meeting id and meeting name
     * 
     * @param nodes
     *            list of nodes
     */
    private ArrayList<String[]> computeMeetingsList(Map<ServiceID, rcNode> nodes) {

        // System.out.println("computeMeetingsList");

        ArrayList<String> me = new ArrayList<String>();
        ArrayList<String[]> lMeetings = new ArrayList<String[]>();
        try {
            rcNode node;
            for (Iterator<rcNode> it = nodes.values().iterator(); it.hasNext();) {
                node = it.next();
                if ((node.client != null) && (node.client.farm != null)) {
                    // for each node, check its meetings cluster
                    MCluster clusterMeetings = node.client.farm.getCluster("Meetings");
                    if (clusterMeetings != null) {
                        Vector<MNode> clMList = clusterMeetings.getNodes();
                        if (clMList.size() > 0) {
                            for (int i = 0; i < clMList.size(); i++) {
                                // find meeting id
                                String meetingFullName = clMList.get(i).getName();
                                int nSep = meetingFullName.indexOf(MNode_PoundSign);
                                String meetingID = "";
                                if (nSep != -1) {
                                    meetingID = meetingFullName.substring(nSep + 1);
                                }
                                if (meetingID.length() > 0) {
                                    int j;
                                    // check first to see if meeting already registered
                                    for (j = 0; j < lMeetings.size(); j++) {
                                        if (lMeetings.get(j)[0].equals(meetingID)) {
                                            break;
                                        }
                                    }
                                    if (j >= lMeetings.size()) {
                                        lMeetings.add(new String[] { meetingID, clMList.get(i).toString() });
                                    }

                                    String key = meetingFullName;
                                    me.add(key);
                                    if (!miNodes.containsKey(key)) {
                                        monPredicate pred = new monPredicate(node.UnitName, "Meetings",
                                                meetingFullName, TimeUnit.MINUTES.toMillis(45), -1,
                                                new String[] { "MeetingInfo" }, null);

                                        // System.out.println("sending pred "+pred.toString());

                                        node.client.addLocalClient(this, pred);
                                        ArrayList<Object> l = new ArrayList<Object>();
                                        l.add(node);
                                        l.add(pred);
                                        miNodes.put(key, l);
                                    }

                                }
                            }// end for each meeting
                        }// end meetings list has at least one value
                    }
                }
            }

            ArrayList<String> toRemove = new ArrayList<String>();
            for (String key : miNodes.keySet()) {
                if (!me.contains(key)) {
                    node = (rcNode) miNodes.get(key).get(0);
                    monPredicate pred = (monPredicate) miNodes.get(key).get(1);
                    node.client.deleteLocalClient(this, pred);
                    toRemove.add(key);
                }
            }
            for (final String sKey : toRemove) {
                miNodes.remove(sKey);
                final String id = meetingIDFromName(sKey);
                if (id != null) {
                    meetingCommunityEarlyMap.remove(id);
                }
            }

        } catch (Throwable ex) {
            logger.log(Level.WARNING,
                    "Error on updating new list of meetings. Go back to last available: " + ex.getCause());
        }
        return lMeetings;
    }

    /**
     * list that contain meetings details:<br>
     * ID<br>
     * Name<br>
     * Color<br>
     * Status<br>
     */
    private final List<MeetingDetails> lMeetingDetails = new CopyOnWriteArrayList<MeetingDetails>();

    private final ConcurrentMap<String, String> meetingCommunityEarlyMap = new ConcurrentHashMap<String, String>();

    // current JPopupMenu with known communities..

    /**
     * maps the last used colors with the id of an meeting, so that, if that meeting comes back
     * to get the same color, so, the key is the color
     */
    private final HashMap<Color, String> hLastUsedColors = new HashMap<Color, String>();

    private final boolean bUseRecentColorsList = true;

    private volatile boolean bRegenerateMenu = false;// boolean variable that is set if an exception occured in awt menu

    private void addDefaultItems(JScrollMenu meetingsMenuList) {
        // if there are items in the menu, that means that select/unselect all option are already added
        if (meetingsMenuList.getComponentList().size() > 0) {
            return;
        }
        JMenuItem miSelect;
        miSelect = new JMenuItem("Select All", doRectangleIcon(Color.black, true));
        miSelect.setActionCommand("MeetingsMenuSelectAll");
        miSelect.addActionListener(this);
        if (parent != null) {
            miSelect.addActionListener(parent);// the parent panel should be notified
        }
        JMenuItem miUnSelect;
        miUnSelect = new JMenuItem("UnSelect All", doRectangleIcon(Color.black, false));
        miUnSelect.setActionCommand("MeetingsMenuUnSelectAll");
        miUnSelect.addActionListener(this);
        if (parent != null) {
            miUnSelect.addActionListener(parent);// the parent panel should be notified
        }
        synchronized (syncMeetingObject) {
            // add select all and unselect all menu options
            meetingsMenuList.add(miSelect);
            meetingsMenuList.add(miUnSelect);
            meetingsMenuList.addSeparator();

            meetingsMenuList.pack();

            // for (int i=0; i<100; i++) {
            // JCheckBoxMenuItem t = new JCheckBoxMenuItem( "i"+i);
            // meetingsMenuList.add(t);
            // }
        }

    }

    private void regenerateMenu(JScrollMenu meetingsMenuList) {

        // System.out.println("regenerate menu: "+bRegenerateMenu);
        if (bRegenerateMenu) {
            bRegenerateMenu = false;
            try {
                synchronized (syncMeetingObject) {
                    meetingsMenuList.removeAll();
                }
                // add default: select all, unselect all, separator
                addDefaultItems(meetingsMenuList);
                // add new elements
                for (final MeetingDetails meetingDetail : lMeetingDetails) {
                    // final String comm = getCommunityByID(meetingDetail.idColor.ID);
                    final String comm = meetingDetail.getCommunity();
                    if (communityState.containsKey(comm)) {
                        if (!communityState.get(comm).booleanValue()) {
                            continue;
                        }
                    } else {
                        if (!communityState.get("other").booleanValue()) {
                            continue;
                        }
                    }
                    JCheckBoxMenuItem cbmi = meetingDetail.menuItem;
                    cbmi.setActionCommand("MeetingsMenuItemChanged");
                    cbmi.addActionListener(this);
                    if (parent != null) {
                        cbmi.addActionListener(parent);// the parent panel should be notified
                    }
                    synchronized (syncMeetingObject) {
                        meetingsMenuList.add(cbmi);
                    }
                    cbmi.setState(meetingDetail.status);
                }
                logger.log(Level.INFO, "Meetings menu regenerated.");
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " [ MeetingsLayer ] Unknown state for meetings menu. Cause: ", t.getCause());
                } else {
                    logger.log(Level.INFO, " [ MeetingsLayer ] Unknown state for meetings menu. Cause: " + t.getCause()
                            + " will regenerate menu.");
                }
                bRegenerateMenu = true;
            }
        }
    }

    private static final int iconSize = 10;// checkbox item in meetings menu, colored spot's size

    /**
     * creates an icon of an rectangle, filled with the specified color
     * 
     * @author mluc
     * @since Mar 27, 2007
     * @param c
     *            color to draw the rectangle
     */
    private static ImageIcon doRectangleIcon(Color c, boolean bFill) {
        BufferedImage biColor;
        Graphics g;
        biColor = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);
        g = biColor.getGraphics();
        // add first menu item
        g.setColor(c);
        if (bFill) {
            g.fillRoundRect(0, 0, iconSize - 1, iconSize - 1, iconSize / 3, iconSize / 3);
        } else {
            g.drawRoundRect(0, 0, iconSize - 1, iconSize - 1, iconSize / 3, iconSize / 3);
        }
        return new ImageIcon(biColor);
    }

    private void regenerateCommunityMenu(JScrollMenu communityMenuList) {

        if (communityUpdated) {
            communityUpdated = false;
            try {
                synchronized (syncMeetingObject) {
                    communityMenuList.removeAll();
                }

                // add default: select all, unselect all, separator

                JMenuItem miSelect;
                miSelect = new JMenuItem("Select All", doRectangleIcon(Color.black, true));
                miSelect.setActionCommand("CommunityMenuSelectAll");
                miSelect.addActionListener(this);
                if (parent != null) {
                    miSelect.addActionListener(parent);// the parent panel should be notified
                }
                JMenuItem miUnSelect;
                miUnSelect = new JMenuItem("UnSelect All", doRectangleIcon(Color.black, false));
                miUnSelect.setActionCommand("CommunityMenuUnSelectAll");
                miUnSelect.addActionListener(this);
                if (parent != null) {
                    miUnSelect.addActionListener(parent);// the parent panel should be notified
                }

                synchronized (syncMeetingObject) {
                    // add select all and unselect all menu options
                    communityMenuList.add(miSelect);
                    communityMenuList.add(miUnSelect);
                    communityMenuList.addSeparator();
                }

                // add new elements
                List<String> l = null;
                synchronized (syncMeetingObject) {
                    l = new ArrayList<String>(communitiesMeetingsMap.keySet());
                }
                Collections.sort(l);
                for (final String comm : l) {
                    if (comm.equals("other")) {
                        continue;
                    }
                    JCheckBoxMenuItem cbmi;
                    cbmi = new JCheckBoxMenuItem(comm, true);
                    cbmi.setActionCommand("CommunityMenuItemChanged");
                    cbmi.addActionListener(this);
                    if (parent != null) {
                        cbmi.addActionListener(parent);// the parent panel should be notified
                    }
                    synchronized (syncMeetingObject) {
                        communityMenuList.add(cbmi);
                    }
                    cbmi.setState(communityState.get(comm).booleanValue());
                }

                if (communitiesMeetingsMap.containsKey("other")) {
                    JCheckBoxMenuItem cbmi;
                    cbmi = new JCheckBoxMenuItem("other", true);
                    cbmi.setActionCommand("CommunityMenuItemChanged");
                    cbmi.addActionListener(this);
                    if (parent != null) {
                        cbmi.addActionListener(parent);// the parent panel should be notified
                    }
                    synchronized (syncMeetingObject) {
                        communityMenuList.add(cbmi);
                    }

                    cbmi.setState(communityState.get("other").booleanValue());
                }

                bRegenerateMenu = true;

                logger.log(Level.INFO, "Meetings menu regenerated.");
            } catch (Throwable ex) {
                logger.log(Level.WARNING,
                        "Exception while regenerating meetings menu. Unknown state for meetings menu.");
                bRegenerateMenu = true;
                ex.printStackTrace();
            }
        }
    }

    volatile JScrollMenu communityMenuList = null;

    volatile JScrollMenu meetingsMenuList = null;

    public void updateMeetingsMenu(Map<ServiceID, rcNode> nodes, JScrollMenu communityMenuList,
            JScrollMenu meetingsMenuList) {

        // check communitiesMeetingsMap menu....
        regenerateCommunityMenu(communityMenuList);

        regenerateMenu(meetingsMenuList);

        this.communityMenuList = communityMenuList;
        this.meetingsMenuList = meetingsMenuList;

        try {
            ArrayList<String[]> lMeetings = computeMeetingsList(nodes);
            Collections.sort(lMeetings, comparator);
            // if the list computed is empty, then no new meeting to add, but alot to remove
            // if ( lMeetings == null )
            // return;//do nothing, change nothing
            int i, j;
            // for each current meeting
            for (i = 0; i < lMeetings.size(); i++) {
                String[] metDet = lMeetings.get(i);
                // search its id in list of meetings in menu's associated list
                for (j = 0; j < lMeetingDetails.size(); j++) {
                    if (lMeetingDetails.get(j).idColor.ID.equals(metDet[0])) {
                        break;
                    }
                }
                if (j < lMeetingDetails.size()) {
                    continue;
                }
                // else meeting will be added
                // add default: select all, unselect all, separator if they aren't already there
                addDefaultItems(meetingsMenuList);
                // first, look for a color in laset_used_colors
                // by providing the id of the meeting
                Color c = null;
                if (bUseRecentColorsList) {
                    if (hLastUsedColors.containsValue(metDet[0])) {
                        // find color
                        for (Color c1 : hLastUsedColors.keySet()) {
                            String mid = hLastUsedColors.get(c);
                            if (mid.equals(metDet[0])) {
                                c = c1;
                                break;
                            }
                        }
                    }
                }

                // color is null or not null
                // anyway, check for color availability
                int colorPos = 0;
                if (c != null) {
                    for (; colorPos < lAvailableColors.size(); colorPos++) {
                        // Color c2 = (Color)lAvailableColors.get(colorPos);
                        // System.out.println("color available: "+c2);
                        // if ( c2.getRGB()==c.getRGB() )
                        if (lAvailableColors.get(colorPos).equals(c)) {
                            break;
                        }
                    }
                    if (colorPos >= lAvailableColors.size()) {
                        colorPos = 0;// color not available, so choose a new one
                    }
                }

                if (lAvailableColors.size() > 0) {
                    c = lAvailableColors.get(colorPos);
                } else {// no available color left, so choose a new one
                    int red = (int) (Math.random() * 254) + 1;
                    int green = (int) (Math.random() * 254) + 1;
                    int blue = (int) (Math.random() * 254) + 1;
                    c = new Color(red, green, blue);
                    colorPos = -1;
                }
                // colorPos is set to next color to be used for this new meeting
                // add the new meeting to list and menu
                final JCheckBoxMenuItem cbmi = new JCheckBoxMenuItem(meetingNameFromName(metDet[1]), doRectangleIcon(c,
                        true));
                cbmi.setActionCommand("MeetingsMenuItemChanged");
                cbmi.addActionListener(this);
                if (parent != null) {
                    cbmi.addActionListener(parent);// the parent panel should be notified
                }

                final MeetingDetails newElem = new MeetingDetails(metDet[0], meetingNameFromName(metDet[1]), c,
                        selectAllMeetings, cbmi);
                lMeetingDetails.add(newElem);
                final String comm = meetingCommunityEarlyMap.get(metDet[0]);
                if (comm != null) {
                    newElem.setCommunity(comm);
                }

                // remove all entries with value equal to current meeting id
                if (bUseRecentColorsList) {
                    for (Iterator<String> it = hLastUsedColors.values().iterator(); it.hasNext();) {
                        // c = (Color)it.next();
                        String mid = it.next();// hLastUsedColors.get(c);
                        if (mid.equals(metDet[0])) {
                            it.remove();
                        }
                    }
                }
                synchronized (syncMeetingObject) {
                    meetingsMenuList.add(cbmi);

                    // add default to the "other" communitiesMeetingsMap...
                    Set<String> l = communitiesMeetingsMap.get("other");
                    if (l != null) {
                        l.add(meetingIDFromName(metDet[1]));
                    }

                    // remove color from available queue after meeting safely added
                    if ((colorPos >= 0) && (lAvailableColors.size() > colorPos)) {
                        lAvailableColors.remove(colorPos);
                    }
                    // set as last used color
                    if (bUseRecentColorsList) {
                        hLastUsedColors.put(c, metDet[0]);
                    }
                }
            }
            // after addition of new meetings, remove old ones
            for (i = 0; i < lMeetingDetails.size(); i++) {
                final MeetingDetails oldElem = lMeetingDetails.get(i);
                // search its id in list of meetings
                for (j = 0; j < lMeetings.size(); j++) {
                    if (lMeetings.get(j)[0].equals(oldElem.idColor.ID)) {
                        break;
                    }
                }
                if (j < lMeetings.size()) {
                    continue;
                }
                // old meeting not found, so it has to be removed
                synchronized (syncMeetingObject) {
                    // meetingsMenuList.remove(i+3);//the first 3 elements are select all, unselect all and separator,
                    // so ignore them
                    meetingsMenuList.remove(oldElem.menuItem);
                    lMeetingDetails.remove(i);
                    i--;// size of list modified, so decrease conuter also
                    // put the removed color back in available color list
                    lAvailableColors.add(oldElem.idColor.color);
                }
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Exception while checking for new meetings. Unknown state for meetings menu.");
            bRegenerateMenu = true;
            ex.printStackTrace();
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            // if selection changed, update menu list
            // and refresh panel

            if (e.getActionCommand().equals("CommunityMenuItemChanged")
                    || e.getActionCommand().equals("OtherCommunity")) {
                JCheckBoxMenuItem ck = (JCheckBoxMenuItem) e.getSource();
                // System.out.println("found checkbox "+ck.getText());
                if (ck != null) {
                    selectAllCommunities = false;
                    boolean bState = ck.getState();
                    String comm = ck.getText();
                    // System.out.println(comm);
                    communityState.put(comm, Boolean.valueOf(bState));
                    bRegenerateMenu = true;
                    if (meetingsMenuList != null) {
                        regenerateMenu(meetingsMenuList);
                        meetingsMenuList.pack();
                    }
                }
                return;
            }

            if (e.getActionCommand().equals("MeetingsMenuItemChanged")) {
                selectAllMeetings = false;
                // get checkbox's name
                JCheckBoxMenuItem ck = (JCheckBoxMenuItem) e.getSource();
                // System.out.println("found checkbox "+ck.getText());
                if (ck != null) {
                    // identify element based on color :D
                    BufferedImage bi = ((BufferedImage) ((ImageIcon) ck.getIcon()).getImage());
                    boolean bState = ck.getState();
                    Color c = new Color(bi.getRGB(iconSize / 2, iconSize / 2));
                    // System.out.println("found color "+c);
                    for (final MeetingDetails elem : lMeetingDetails) {
                        // System.out.println("comparing with color "+elem[2]);
                        if (c.equals(elem.idColor.color)) {
                            // System.out.println("Set checked state ("+bState+") for "+elem[1]);
                            // element found, so update state
                            synchronized (syncMeetingObject) {
                                elem.status = bState;
                            }
                            return;
                        }
                    }
                }
            } else if (e.getActionCommand().equals("MeetingsMenuSelectAll")
                    || e.getActionCommand().equals("CommunityMenuSelectAll")) {
                // select all items
                if (e.getActionCommand().equals("MeetingsMenuSelectAll")) {
                    selectAllMeetings = true;
                    doActionOnAll(e, true);
                    for (final MeetingDetails elemt : lMeetingDetails) {
                        elemt.status = true;
                    }
                } else {
                    selectAllCommunities = true;
                    doActionOnAllCommunity(e, true);
                }
            } else if (e.getActionCommand().equals("MeetingsMenuUnSelectAll")
                    || e.getActionCommand().equals("CommunityMenuUnSelectAll")) {
                // unselect all items
                if (e.getActionCommand().equals("MeetingsMenuUnSelectAll")) {
                    selectAllMeetings = false;
                    doActionOnAll(e, false);
                    for (final MeetingDetails elemf : lMeetingDetails) {
                        elemf.status = false;
                    }
                } else {
                    selectAllCommunities = false;
                    doActionOnAllCommunity(e, false);
                }
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING,
                    "Exception while updating meetings menu checkbox status. Unknown state for meetings menu.");
            bRegenerateMenu = true;
            ex.printStackTrace();
        }
    }

    /**
     * selects or unselects all checkboxes by sending doClick events -> freezes for a very short while the interface
     * 
     * @author mluc
     * @since Mar 27, 2007
     * @param bSelect
     *            to select or to unselect, that is the question
     */
    private void doActionOnAllCommunity(final ActionEvent e, final boolean bSelect) {
        // start a timer so that it does not freeze the interface
        new Timer().schedule(new TimerTask() {

            @Override
            public void run() {
                // select/unselect all items
                if (e.getSource() instanceof JMenuItem) {
                    JMenuItem mi = (JMenuItem) e.getSource();
                    JScrollMenu pm = ((JScrollMenu) mi.getParent().getParent().getParent().getParent());
                    Object obj;
                    if (pm != null) {
                        List<Component> l = pm.getComponentList();
                        for (int i = 0; i < l.size(); i++) {
                            obj = l.get(i);
                            if ((obj instanceof JCheckBoxMenuItem)
                                    && (bSelect == !((JCheckBoxMenuItem) obj).isSelected())) {
                                JCheckBoxMenuItem ck = (JCheckBoxMenuItem) obj;
                                ck.setSelected(bSelect);
                                // System.out.println("found checkbox "+ck.getText());
                                String comm = ck.getText();
                                // System.out.println(comm);
                                communityState.put(comm, new Boolean(bSelect));
                                bRegenerateMenu = true;
                            }
                        }
                        if (bRegenerateMenu && (meetingsMenuList != null)) {
                            regenerateMenu(meetingsMenuList);
                        }
                    }
                }
            }
        }, 50);
    }

    /**
     * selects or unselects all checkboxes by sending doClick events -> freezes for a very short while the interface
     * 
     * @author mluc
     * @since Mar 27, 2007
     * @param bSelect
     *            to select or to unselect, that is the question
     */
    private void doActionOnAll(final ActionEvent e, final boolean bSelect) {
        // start a timer so that it does not freeze the interface
        new Timer().schedule(new TimerTask() {

            @Override
            public void run() {
                // select/unselect all items
                if (e.getSource() instanceof JMenuItem) {
                    JMenuItem mi = (JMenuItem) e.getSource();
                    JScrollMenu pm = ((JScrollMenu) mi.getParent().getParent().getParent().getParent());
                    Object obj;
                    if (pm != null) {
                        List<Component> l = pm.getComponentList();
                        for (int i = 0; i < l.size(); i++) {
                            obj = l.get(i);
                            if ((obj instanceof JCheckBoxMenuItem)
                                    && (bSelect == !((JCheckBoxMenuItem) obj).isSelected())) {
                                JCheckBoxMenuItem ck = (JCheckBoxMenuItem) obj;
                                ck.setSelected(bSelect);
                                // System.out.println("found checkbox "+ck.getText());
                                // identify element based on color :D
                                ImageIcon ic = (ImageIcon) ck.getIcon();
                                if (ic == null) {
                                    continue;
                                }
                                BufferedImage bi = ((BufferedImage) (ic).getImage());
                                Color c = new Color(bi.getRGB(iconSize / 2, iconSize / 2));
                                // System.out.println("found color "+c);
                                for (final MeetingDetails elem : lMeetingDetails) {
                                    // System.out.println("comparing with color "+elem[2]);
                                    if (c.equals(elem.idColor.color)) {
                                        // System.out.println("Set checked state ("+bState+") for "+elem[1]);
                                        // element found, so update state
                                        synchronized (syncMeetingObject) {
                                            elem.status = bSelect;
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                        pm.pack();
                    }
                }
            }
        }, 50);
    }

    /**
     * gets a snapshot of only selected meetings, with their associated color
     * 
     * @return a list that contains {id,Color} objects
     */
    public ArrayList<MeetingDetails.IDColor> getSnapshot() {
        ArrayList<MeetingDetails.IDColor> lM = new ArrayList<MeetingDetails.IDColor>();
        try {
            for (final MeetingDetails elem : lMeetingDetails) {
                if (elem.status) {

                    // final String comm = getCommunityByName(elem.idColor.ID);
                    final String comm = elem.getCommunity();
                    if (communityState.containsKey(comm) && communityState.get(comm).booleanValue()) {
                        lM.add(new MeetingDetails.IDColor(elem.idColor.ID, elem.idColor.color));
                    }
                }
            }

        } catch (Exception ex) {
            logger.log(Level.WARNING, "exception while creating snapshot of meetings.");
            ex.printStackTrace();
        }
        return lM;
    }

    public MeetingDetails getMeetingDetails(String meetingID) {
        for (final MeetingDetails elem : lMeetingDetails) {
            if (elem.idColor.ID.equals(meetingID)) {
                return elem;
            }
        }
        return null;
    }

    public String getMeetingName(String meetingID) {
        try {
            for (final MeetingDetails elem : lMeetingDetails) {
                if (elem.idColor.ID.equals(meetingID)) {
                    return elem.name;
                }
            }

        } catch (Exception ex) {
            logger.log(Level.WARNING, "exception while getting name of meeting " + meetingID + ".");
            ex.printStackTrace();
        }
        return null;
    }

    public boolean isOneSelected() {
        try {
            for (final MeetingDetails elem : lMeetingDetails) {
                if (elem.status) {
                    return true;
                }
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "exception while checking for at least one meeting selected.");
            ex.printStackTrace();
        }
        return false;
    }

    // @Override
    @Override
    public void newFarmResult(MLSerClient client, Object ro) {

        if (ro == null) {
            return;
        }
        // System.out.println("vrvs result: "+ro.getClass());
        if (ro instanceof Result) {
            // logger.log(Level.INFO, "VOJobs Result from "+client.farm.name+" = "+r);
        } else if (ro instanceof eResult) {
            eResult r = (eResult) ro;
            // System.out.println(" Got eResult " + ro);
            setResult(client, r);
        } else if (ro instanceof Vector) {
            @SuppressWarnings("unchecked")
            Vector<Object> vr = (Vector<Object>) ro;
            // System.out.println(new Date()+" V["+vr.size()+"] from "+client.farm.name);
            for (int i = 0; i < vr.size(); i++) {
                newFarmResult(client, vr.elementAt(i));
            }
        } else {
            // logger.log(Level.WARNING, "Wrong Result type in VoJob from "+client.farm.name+": " + ro);
            return;
        }
    }

    public void setResult(MLSerClient client, eResult r) {
        final String meeting = r.NodeName;
        final String meetingID = meetingIDFromName(meeting);

        // we got user id from result and found the fake node corresponding to this id
        for (int i = 0; i < r.param_name.length; i++) {
            // parse the result to find the meeting id
            if ("MeetingInfo".equals(r.param_name[i])) {
                String v = r.param[i].toString();
                Matcher m = p.matcher(v);
                if (m.find()) {
                    String comm = m.group(1);
                    Set<String> l = communitiesMeetingsMap.get(comm);
                    if (l == null) {
                        l = new HashSet<String>();
                        communitiesMeetingsMap.put(comm, l);
                        communityState.put(comm, Boolean.valueOf(selectAllCommunities));
                        logger.log(Level.INFO, "Community " + comm + " added. selected = " + selectAllCommunities);
                        communityUpdated = true;
                    }
                    final MeetingDetails md = getMeetingDetails(meetingID);
                    if (md != null) {
                        bRegenerateMenu = md.setCommunity(comm);
                    } else {
                        meetingCommunityEarlyMap.put(meetingID, comm);
                    }

                    if (!l.contains(meetingID)) {
                        l.add(meetingID);
                        Set<String> ll = communitiesMeetingsMap.get("other");
                        if (ll != null) {
                            ll.remove(meetingID);
                            if (ll.size() == 0) {
                                communitiesMeetingsMap.remove("other");
                                communityState.remove("other");
                            }
                        }
                        communityUpdated = true;
                    }
                }
            }
        }
    }
}
