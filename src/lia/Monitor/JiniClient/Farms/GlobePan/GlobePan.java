package lia.Monitor.JiniClient.Farms.GlobePan;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JComboBox;
import javax.swing.event.ChangeEvent;

import lia.Monitor.JiniClient.CommonGUI.MFilter2Constants;
import lia.Monitor.JiniClient.CommonGUI.NodeToolTipText;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.GlobePan.GlobePanBase;
import lia.Monitor.JiniClient.CommonGUI.GlobePan.TexturedPie;
import lia.Monitor.JiniClient.Farms.JoptPan;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ILink;
import lia.util.ntp.NTPDate;

public class GlobePan extends GlobePanBase {

    /**
     * 
     */
    private static final long serialVersionUID = 5179118934827483623L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(GlobePan.class.getName());

    public JoptPan optPan;
    public NodesGroup nodesGroup;
    public PingLinksGroup pingLinksGroup;
    public WANLinksGroup wanLinksGroup;
    public Hashtable pingLinksCache = new Hashtable(); // cache with Ping ILinks
    private final Object syncGparamCombobox = new Object();
    private final Object syncRefreshNodes = new Object();

    int nodesMode = 1;
    int peersMode = 11;
    int MAX_INV = 2;
    int invisibleUpdates = MAX_INV;

    public GlobePan() {
        super();
        TimerTask ttask = new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName(" ( ML ) - Farms - GlobePan Timer Thread");
                if (!isVisible()) {
                    invisibleUpdates--;
                    if (invisibleUpdates > 0) {
                        return;
                    }
                    invisibleUpdates = MAX_INV;
                }
                //System.out.println("Refreshing...");
                refresh();
            }
        };
        Timer timer = new Timer();
        ;
        timer.schedule(ttask, 10000, 4000);
        //System.out.println("GlobePan Finished");
    }

    @Override
    protected void buildOptPan() {
        optPan = new JoptPan(this);
        optPan.setMaximumSize(new Dimension(1000, 80));

        optPan.csWAN.setColors(Color.CYAN, Color.CYAN);
        optPan.csWAN.setLabelFormat("###.##", "Mbps");
        optPan.csWAN.setValues(0, 0);

        optPan.csPing.setColors(Color.GREEN, Color.GREEN);
        optPan.csPing.setValues(0, 0);
        optPan.csPing.setLabelFormat("###.##", "");

        optPan.kbShowWAN.setActionCommand("kbShowWAN");
        optPan.kbShowWAN.addActionListener(this);

        optPan.kbShowPing.setActionCommand("kbShowPing");
        optPan.kbShowPing.addActionListener(this);

        optPan.kbAnimateWAN.setActionCommand("kbAnimateWAN");
        optPan.kbAnimateWAN.addActionListener(this);

        optPan.gparam.setActionCommand("comboBox");
        optPan.gparam.addActionListener(this);

        toolbarsPanel.add(optPan);

    }

    @Override
    protected void buildGroups() {
        super.buildGroups();
        pingLinksGroup = new PingLinksGroup();
        nodesGroup = new NodesGroup();
        wanLinksGroup = new WANLinksGroup();
        //System.out.println("builtGroups");
        try {
            boolean animate = AppConfig.getProperty("lia.Monitor.j3d.animateWANLinks", "false").equals("true");
            optPan.kbAnimateWAN.setSelected(animate);
            wanLinksGroup.changeAnimationStatus(animate); // because checkbox.setSelected doesn't trigger an avent
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error setting farms j3d WAN Links animation", ex);
        }
    }

    @Override
    protected void buildSelector() {
        selector = new Selector(canvas, nodesGroup, pingLinksGroup, wanLinksGroup);
        canvas.addMouseListener(selector);
        canvas.addMouseMotionListener(selector);
        selector.addNodeSelectionListener(this);
        selector.addLinkHighlightedListener(this);
        //System.out.println("BuiltSelector");
    }

    @Override
    protected void addGroups() {
        spin.addChild(nodesGroup);
        if (optPan.kbShowPing.isSelected()) {
            spin.addChild(pingLinksGroup);
        }
        if (optPan.kbShowWAN.isSelected()) {
            spin.addChild(wanLinksGroup);
        }
        if (optPan.kbShowPing.isSelected()) {
            spin.addChild(pingLinksGroup);
        }

        ((Selector) selector).setNodesBranch(nodesGroup);
        ((Selector) selector).setPingBranch(pingLinksGroup);
        pingLinksGroup.setScale(1.01 + (0.01 * scaleSlider.getValue()));
        nodesGroup.setScale(1.01 + (0.01 * scaleSlider.getValue()));
        //System.out.println("addedGroups");
    }

    @Override
    protected void otherActionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (cmd.equals("kbShowWAN")) {
            synchronized (syncRefreshNodes) {
                toggleWANLinksGroup();
            }
        } else if (cmd.equals("kbShowPing")) {
            synchronized (syncRefreshNodes) {
                togglePingLinksGroup();
            }
        } else if (cmd.equals("kbAnimateWAN")) {
            synchronized (syncRefreshNodes) {
                wanLinksGroup.changeAnimationStatus(optPan.kbAnimateWAN.isSelected());
            }
        } else if (cmd.equals("comboBox")) {
            JComboBox cb = (JComboBox) e.getSource();
            String paramName = (String) cb.getSelectedItem();
            if (paramName == null) {
                return;
            }

            if (paramName.equals(MFilter2Constants.MenuAccept[0])) {
                setPie("CPUPie");
                refresh();
                return;
            }
            if (paramName.equals(MFilter2Constants.MenuAccept[1])) {
                setPie("IOPie");
                refresh();
                return;
            }
            if (paramName.equals(MFilter2Constants.MenuAccept[2])) {
                setPie("LoadPie");
                refresh();
                return;
            }
            if (paramName.equals(MFilter2Constants.MenuAccept[3])) {
                setPie("DiskPie");
                refresh();
                return;
            }
        }
    }

    void setPie(String key) {
        nodesGroup.pieKey = key;
    }

    @Override
    protected void otherStateChanged(ChangeEvent e) {
        Object src = e.getSource();
        if (src == scaleSlider) {
            int v = scaleSlider.getValue();
            nodesGroup.setScale(1.01 + (0.01 * v));
            wanLinksGroup.setScale(1.01 + (0.01 * v));
            pingLinksGroup.setScale(1.01 + (0.01 * v));
            zoomer.resetPosSlider();
        }
    }

    void toggleWANLinksGroup() {
        if (optPan.kbShowWAN.isSelected()) {
            if (!wanLinksGroup.isLive()) {
                wanLinksGroup.refresh();
                spin.addChild(wanLinksGroup);
            }
        } else {
            if (wanLinksGroup.isLive()) {
                wanLinksGroup.detach();
                spin.removeChild(wanLinksGroup);
            }
        }
    }

    void togglePingLinksGroup() {
        if (optPan.kbShowPing.isSelected()) {
            if (!pingLinksGroup.isLive()) {
                pingLinksGroup.refresh();
                spin.addChild(pingLinksGroup);
            }
        } else {
            if (pingLinksGroup.isLive()) {
                pingLinksGroup.detach();
                spin.removeChild(pingLinksGroup);
            }
        }
    }

    void updatePingLinks(rcNode from, rcNode to) {
        String key = from.UnitName + "->" + to.UnitName;
        ILink link = (ILink) pingLinksCache.get(key);
        if (link == null) {
            link = new ILink(key);
            link.fromIP = from.UnitName;
            link.fromLAT = failsafeParseDouble(from.LAT, -21.22D);
            link.fromLONG = failsafeParseDouble(from.LONG, -111.15D);
            link.toIP = to.UnitName;
            link.toLAT = failsafeParseDouble(to.LAT, -21.22D);
            link.toLONG = failsafeParseDouble(to.LONG, -111.15D);
            link.inetQuality = new double[3];
            link.inetQuality[0] = link.speed = from.connPerformance(to);
            link.inetQuality[1] = from.connRTT(to);
            link.inetQuality[2] = from.connLP(to);
            link.time = NTPDate.currentTimeMillis();
            pingLinksCache.put(key, link);
            pingLinksGroup.addPingLink(link);
        } else {
            double rttime = link.speed = link.inetQuality[0] = from.connPerformance(to);
            double rtt = link.inetQuality[1] = from.connRTT(to);
            double lp = link.inetQuality[2] = from.connLP(to);
            link.time = NTPDate.currentTimeMillis();
            //System.out.println("Setting for "+link.name+" rttime="+rttime+" rtt="+rtt+" lp="+lp);
        }
    }

    double failsafeParseDouble(String value, double failsafe) {
        try {
            return Double.parseDouble(value);
        } catch (Throwable t) {
            return failsafe;
        }
    }

    void check4DeadPingLinks() {
        boolean mustRebuild = false;
        long now = NTPDate.currentTimeMillis();

        for (Enumeration e = pingLinksCache.elements(); e.hasMoreElements();) {
            ILink link = (ILink) e.nextElement();
            if ((now - link.time) > (2 * 60 * 1000)) { // after 2 minutes, it expires
                //System.out.println("Going to remove expired ping link "+ link.name);
                pingLinksCache.remove(link.name);
                pingLinksGroup.removePingLink(link);
                continue;
            }
            // check if from & to nodes are still alive; if one isn't, this
            // link is also dead
            boolean foundFrom = false, foundTo = false;
            String from = link.name.substring(0, link.name.indexOf("->"));
            String to = link.name.substring(2 + link.name.indexOf("->"));
            for (Iterator in = nodesGroup.nodes.iterator(); in.hasNext();) {
                rcNode n = (rcNode) in.next();
                if (from.equals(n.UnitName)) {
                    foundFrom = true;
                }
                if (to.equals(n.UnitName)) {
                    foundTo = true;
                }
            }
            if (!(foundTo && foundFrom)) {
                //System.out.println("Going to remove half dead link "+link.name);
                pingLinksCache.remove(link.name);
                pingLinksGroup.removePingLink(link);
            }
        } // next link...
    }

    /**
     * this method needs to be synchronized because concurent calls may be made for it:
     * when a value in combo-box is changed, when the 'reset' button is pressed and when the TimerTask reaches
     * the execution moment
     * @see lia.Monitor.JiniClient.CommonGUI.GlobePan.GlobePanBase#refresh()
     */
    @Override
    public void refresh() {
        super.refresh();
        try {
            // There's no sense in refreshing if there's nothing to refresh.
            if ((hnodes == null) && (vnodes == null)) {
                return;
            }
            synchronized (syncRefreshNodes) {
                animateWANLinks = optPan.kbAnimateWAN.isSelected();
                HashSet newWANLinks = new HashSet();
                //HashSet newPingLinks = new HashSet();
                //HashSet newNodes = new HashSet();

                // Figure out whether any nodes or links were added or removed
                for (Object element : vnodes) {

                    rcNode node = (rcNode) element;
                    if (!node.bHiddenOnMap) {
                        // add a new node
                        if (!nodesGroup.nodes.contains(node)) {
                            nodesGroup.addNode(node);
                        }
                        // for wan links
                        for (Enumeration el = node.wconn.elements(); el.hasMoreElements();) {
                            Object objLink = el.nextElement();
                            if (!(objLink instanceof ILink)) {
                                continue;
                            }
                            ILink link = (ILink) objLink;

                            // new wan link
                            if (!wanLinksGroup.links.contains(link)) {
                                wanLinksGroup.addWANLink(link);
                            }
                            // save a copy of this link for later checking of dead links
                            newWANLinks.add(link);
                        }

                        // for ping links
                        for (Enumeration e1 = node.conn.keys(); e1.hasMoreElements();) {
                            rcNode nw = (rcNode) e1.nextElement();
                            updatePingLinks(node, nw);
                        }
                    }
                    ;
                }
                // check for deleted nodes
                for (Iterator nit = nodesGroup.nodes.iterator(); nit.hasNext();) {
                    rcNode n = (rcNode) nit.next();
                    if (!vnodes.contains(n) || n.bHiddenOnMap) {//if node set to be hidden, remove representation
                        nodesGroup.removeNode(n, nit);
                    }
                }
                // check for deleted wanLinks
                for (Iterator wit = wanLinksGroup.links.iterator(); wit.hasNext();) {
                    ILink link = (ILink) wit.next();
                    if (!newWANLinks.contains(link)) {
                        wanLinksGroup.removeWANLink(link, wit);
                    }
                }
                newWANLinks.clear();

                // refresh components
                nodesGroup.refresh();

                wanLinksGroup.refresh();

                optPan.csWAN.setValues(wanLinksGroup.minQuality, wanLinksGroup.maxQuality);
                if (wanLinksGroup.minQuality >= wanLinksGroup.maxQuality) {
                    optPan.csWAN.setColors(Color.CYAN, Color.CYAN);
                } else {
                    optPan.csWAN.setColors(Color.CYAN, Color.BLUE);
                }

                check4DeadPingLinks();
                pingLinksGroup.refresh();

                // Update the options panel
                optPan.csPing.setValues(pingLinksGroup.minRTT, pingLinksGroup.maxRTT);
                if (pingLinksGroup.minRTT >= pingLinksGroup.maxRTT) {
                    optPan.csPing.setColors(Color.GREEN, Color.GREEN); // red(min) --> yellow(max)  
                } else {
                    optPan.csPing.setColors(Color.GREEN, Color.YELLOW); // red(min) --> yellow(max)
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "jc: Exception caught in GlobePan.refresh()", e);
        }
    }

    @Override
    public void nodeHighlighted(rcNode node) {
        if (node == null) {
            if (!status.getText().equals(" ")) {
                status.setText(" ");
                nodesGroup.hideAllNodeTooltips();
                wanLinksGroup.hideAllLinkTooltips();
                pingLinksGroup.hideAllLinkTooltips();
            }
        } else {
            String text = NodeToolTipText.getToolTip(node);
            if (!status.getText().equals(text)) {
                nodesGroup.hideAllNodeTooltips();
                status.setIcon(node.icon);
                status.setText(text);
                nodesGroup.setNodeTooltip(node, NodeToolTipText.getShortToolTip(node));
                nodesGroup.showNodeTooltip(node);
            }
        }
    }

    @Override
    public void linkHighlighted(Object olink) {
        if (olink == null) {
            if (!status.getText().equals(" ")) {
                status.setText(" ");
                wanLinksGroup.hideAllLinkTooltips();
                pingLinksGroup.hideAllLinkTooltips();
            }
        } else if (olink instanceof ILink) {
            ILink link = (ILink) olink;
            String text = "";
            String shortText = "";
            if (link.name.indexOf("->") < 0) {
                text = "WAN: " + link.name + " = "
                        + optPan.csWAN.formatter.format(((Double) (link.data)).doubleValue()) + " / "
                        + optPan.csWAN.formatter.format(link.speed) + " Mbps -> "
                        + optPan.csWAN.formatter.format((((Double) (link.data)).doubleValue() * 100) / link.speed)
                        + " % ";
                shortText = "WAN: " + link.name + " "
                        + optPan.csWAN.formatter.format(((Double) (link.data)).doubleValue()) + "Mbps ("
                        + optPan.csWAN.formatter.format(((((Double) (link.data)).doubleValue()) * 100.0) / link.speed)
                        + "%)";
            } else {
                text = "ABPing: " + link.fromIP + "->" + link.toIP + ": RTTime= "
                        + optPan.csPing.formatter.format(link.inetQuality[0]) + ", RTT= "
                        + optPan.csPing.formatter.format(link.inetQuality[1]) + " ms, Lost Packages= "
                        + optPan.csPing.formatter.format(link.inetQuality[2] * 100) + " % ";
                shortText = "ABPing: " + link.fromIP + "->" + link.toIP + ": RTT="
                        + optPan.csPing.formatter.format(link.inetQuality[1]) + " (Lost="
                        + optPan.csPing.formatter.format(link.inetQuality[2] * 100) + " %)";
            }
            if (!status.getText().equals(text)) {
                wanLinksGroup.hideAllLinkTooltips();
                pingLinksGroup.hideAllLinkTooltips();
                wanLinksGroup.setLinkTooltip(link, shortText);
                pingLinksGroup.setLinkTooltip(link, shortText);
                wanLinksGroup.showLinkTooltip(link);
                pingLinksGroup.showLinkTooltip(link);
                status.setText(text);
            }
        } else if (olink instanceof TexturedPie) {
            TexturedPie r = (TexturedPie) olink;
            String text = "Router @ " + r.name;
            if (!status.getText().equals(text)) {
                wanLinksGroup.hideAllLinkTooltips();
                pingLinksGroup.hideAllLinkTooltips();
                wanLinksGroup.showRouterTooltip(r.LONG + "/" + r.LAT);
                status.setText(text);
            }
        }
    }

    public void setNodes(Hashtable hnodes, Vector vnodes) {
        super.setNodes(hnodes, vnodes);
    }

    @Override
    public void setSerMonitor(SerMonitorBase monitor) {
        super.setSerMonitor(monitor);
    }

    @Override
    public void new_global_param(String mod) {
        int k = -1;

        for (int i = 0; i < MFilter2Constants.acceptg.length; i++) {
            if (mod.equals(MFilter2Constants.acceptg[i])) {
                k = i;
                break;
            }
        }
        if (k < 0) {
            return;
        }

        synchronized (syncGparamCombobox) {
            int f = 0;
            for (int i = 0; i < optPan.gparam.getItemCount(); i++) {
                String pa2 = (String) optPan.gparam.getItemAt(i);
                if (pa2.equals(MFilter2Constants.MenuAccept[k])) {
                    f = 1;
                }
            }

            if (f == 0) {
                logger.log(Level.FINE, "Adding in GlobePan gparam combobox: " + MFilter2Constants.MenuAccept[k]);
                optPan.gparam.addItem(MFilter2Constants.MenuAccept[k]);
                optPan.gparam.repaint();
            }
        }
    }

}
