package lia.Monitor.GUIs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.List;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ListModel;
import javax.swing.ToolTipManager;
import javax.swing.border.BevelBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.Plot.BarClusterSummaryPlot;
import lia.Monitor.Plot.BarMonDataPlotClus;
import lia.Monitor.Plot.BarNodeSummaryPlot;
import lia.Monitor.Plot.DataPlotter;
import lia.Monitor.Plot.MonDataPlot;
import lia.Monitor.Plot.MonDataPlotClus;
import lia.Monitor.monitor.LocalDataFarmProvider;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.tRCFrame;

public class RCMonPanel extends JPanel implements DataPlotterParent {
    /**
     * 
     */
    private static final long serialVersionUID = 8630650319188366226L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(RCMonPanel.class.getName());

    public String name;
    private final LocalDataFarmProvider dataprovider;
    public Hashtable config;
    public ModulesPanel modPanel;
    public ValuesPanel valPanel;
    public HashMap currentUnits = null;

    public DefaultMutableTreeNode root;
    //	public DefaultMutableTreeNode groupRoot;
    public DefaultMutableTreeNode locatorRoot;
    public DefaultTreeModel model;
    public JTree configTree;
    public DefaultTreeCellRenderer rend;
    public List funcList;
    public String localTime;
    private final tRCFrame parentFrame;
    private final DataPlotterParent thisReference;
    //	private ConfigListener listener;
    Color c;
    private final Vector winList = new Vector();

    private String farmName;
    private String countryCode;

    public RCMonPanel(LocalDataFarmProvider dataprovider, String name, Hashtable config, tRCFrame parentFrame) {
        super();
        this.dataprovider = dataprovider;
        this.parentFrame = parentFrame;
        c = new Color(205, 226, 247);
        setBackground(c);
        setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED, Color.white, Color.white, new Color(0, 98, 137),
                new Color(0, 59, 95)));
        this.name = name;
        this.config = config;
        thisReference = this;

        setLayout(new BorderLayout());

        modPanel = new ModulesPanel();
        valPanel = new ValuesPanel(modPanel);
        if ((parentFrame != null) && (parentFrame.serMonitorBase != null)) {
            valPanel.setRegistry(parentFrame.serMonitorBase.getRegistry());
        }

        JPanel leftp = new JPanel();
        leftp.setBackground(c);
        leftp.setLayout(new BoxLayout(leftp, BoxLayout.Y_AXIS));
        leftp.add("Center", valPanel);
        leftp.add("South", modPanel);
        add("East", leftp);

        PlotListener pl = new PlotListener();

        valPanel.show.addActionListener(pl);
        valPanel.barshow.addActionListener(pl);
        valPanel.mnBarAverage.addActionListener(pl);
        valPanel.mnBarSum.addActionListener(pl);
        valPanel.mnBarIntegral.addActionListener(pl);
        valPanel.mnBarMinMax.addActionListener(pl);
        valPanel.mnPieAverage.addActionListener(pl);
        valPanel.mnPieSum.addActionListener(pl);
        valPanel.mnPieIntegral.addActionListener(pl);
        valPanel.mnPieMinMax.addActionListener(pl);
    }

    public void setSerMonitorBase(SerMonitorBase serMonitorBase) {
        if ((serMonitorBase != null) && (valPanel != null)) {
            valPanel.setRegistry(serMonitorBase.getRegistry());
        }
    }

    /** save local time and also send it to opened plotter windows */
    public void setLocalTime(String dd) {
        localTime = dd;
        for (Enumeration ew = winList.elements(); ew.hasMoreElements();) {
            DataPlotter w = (DataPlotter) ew.nextElement();
            w.setLocalTime(dd);
        }
    }

    /** called to set the country code */
    public void setCountryCode(String cc) {
        this.countryCode = cc;
    }

    /** called to set the farm name */
    public void setFarmName(String farmName) {
        this.farmName = farmName;
    }

    /**
     * update the params and modules panels, with data from the current
     * selected object in tree. It can be an instance of MFarm, MCluster
     * or MNode
     * @param obj - the currently selected object
     */
    void updateParamAndModPanels(Object[] obj) {
        if ((obj == null) || (obj.length == 0)) {
            return;
        }
        Vector func = new Vector(getParamList(obj[0]));
        Vector modules = new Vector(getModuleList(obj[0]));
        for (int i = 1; i < obj.length; i++) {
            reunionStringVectors(func, getParamList(obj[i]));
            reunionStringVectors(modules, getModuleList(obj[i]));
        }
        valPanel.updateValues(func);
        modPanel.updateList(modules);
    }

    /** get the parameter list from a MNode, MCluster or MFarm */
    private Vector getParamList(Object userObject) {
        if (userObject instanceof MNode) {
            return ((MNode) userObject).getParameterList();
        } else if (userObject instanceof MCluster) {
            return ((MCluster) userObject).getParameterList();
        } else if (userObject instanceof MFarm) {
            return ((MFarm) userObject).getParameterList();
        } else {
            System.out.println("unknown instance!");
        }
        return new Vector();
    }

    /** get the module list from a MNode, MCluster or MFarm */
    private Vector getModuleList(Object userObject) {
        if (userObject instanceof MNode) {
            return ((MNode) userObject).getModuleList();
        } else if (userObject instanceof MCluster) {
            return ((MCluster) userObject).getModuleList();
        } else if (userObject instanceof MFarm) {
            return ((MFarm) userObject).getModuleList();
        }
        return new Vector();
    }

    /** 
     * intersect the contents in base and probe vectors, 
     * keeping in base only the common values
     * @deprecated
     */
    @Deprecated
    private void intersectStringVectors(Vector base, Vector probe) {
        for (Iterator bit = base.iterator(); bit.hasNext();) {
            String el = (String) bit.next();
            if (!probe.contains(el)) {
                bit.remove();
            }
        }
    }

    private void reunionStringVectors(Vector base, Vector probe) {
        for (Iterator bit = probe.iterator(); bit.hasNext();) {
            String el = (String) bit.next();
            if (!base.contains(el)) {
                base.add(el);
            }
        }
    }

    void makeBasicTree(MFarm farm) {
        root = new DefaultMutableTreeNode(farm);
        model = new DefaultTreeModel(root);
        configTree = new JTree(model) {
            /**
             * 
             */
            private static final long serialVersionUID = 3462034265213552063L;

            @Override
            public String getToolTipText(MouseEvent evt) {
                try {
                    if (getRowForLocation(evt.getX(), evt.getY()) == -1) {
                        return null;
                    }
                    TreePath treepath = getPathForLocation(evt.getX(), evt.getY());
                    if (treepath == null) {
                        return null;
                    }
                    DefaultMutableTreeNode defaultmutabletreenode = (DefaultMutableTreeNode) treepath
                            .getLastPathComponent();
                    Object obj = defaultmutabletreenode.getUserObject();
                    if (obj instanceof MNode) {
                        int nrParams = ((MNode) obj).getParameterList().size();
                        return nrParams + " parameter" + (nrParams != 1 ? "s" : "");
                        //modules = ((MNode) obj).getModuleList();
                    } else if (obj instanceof MCluster) {
                        int nrNodes = ((MCluster) obj).getNodes().size();
                        int nrParams = ((MCluster) obj).getParameterList().size();
                        Vector<MNode> v = ((MCluster) obj).getNodes();
                        int tparams = 0;
                        for (int i = 0; i < v.size(); i++) {
                            tparams += v.get(i).getParameterList().size();
                        }
                        return "<html>" + nrNodes + " node" + (nrNodes != 1 ? "s" : "") + "<br>" + nrParams
                                + " unique param" + (nrParams != 1 ? "s" : "") + "<br>" + tparams + " total param"
                                + (tparams != 1 ? "s" : "") + "</html>";
                        //modules = ((MCluster) obj).getModuleList();
                    } else if (obj instanceof MFarm) {
                        int nrClusters = ((MFarm) obj).getClusters().size();
                        int nrNodes = ((MFarm) obj).getNodes().size();
                        int nrParams = ((MFarm) obj).getParameterList().size();
                        Vector<MCluster> v = ((MFarm) obj).getClusters();
                        Vector<MNode> v1;
                        int tparams = 0;
                        for (int i = 0; i < v.size(); i++) {
                            v1 = v.get(i).getNodes();
                            for (int j = 0; j < v1.size(); j++) {
                                tparams += v1.get(j).getParameterList().size();
                            }
                        }
                        return "<html>" + nrClusters + " cluster" + (nrClusters != 1 ? "s" : "") + "<br>" + nrNodes
                                + " node" + (nrNodes != 1 ? "s" : "") + "<br>" + nrParams + " unique param"
                                + (nrParams != 1 ? "s" : "") + "<br>" + tparams + " total param"
                                + (tparams != 1 ? "s" : "") + "</html>";
                        //modules = ((MFarm) obj).getModuleList();
                    }
                    return obj.getClass().toString();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Couldn't create tooltip", t);
                    return null;
                }
            }
        };
        ToolTipManager.sharedInstance().registerComponent(configTree);

        rend = new DefaultTreeCellRenderer();
        rend.setLeafIcon(new ImageIcon(DefaultImages.createLeafImage()));
        rend.setOpenIcon(new ImageIcon(DefaultImages.createOpenFolderImage()));
        rend.setClosedIcon(new ImageIcon(DefaultImages.createClosedFolderImage()));
        rend.setBackgroundSelectionColor(new Color(228, 219, 165));
        rend.setBackgroundNonSelectionColor(c);
        rend.setTextSelectionColor(Color.blue);
        rend.setTextNonSelectionColor(new Color(14, 18, 43));
        rend.setFont(new Font("Tahoma", Font.BOLD, 11));
        rend.setMinimumSize(null);
        rend.setMaximumSize(null);
        rend.setPreferredSize(null);
        //rend.setPreferredSize(new Dimension(100, 16));

        configTree.setCellRenderer(rend);
        //		configTree.addTreeSelectionListener(listener);
        configTree.setBackground(c);
        configTree.setRowHeight(17);
        configTree.setAutoscrolls(true);
        configTree.putClientProperty("JTree.lineStyle", "Angled");
        //		configTree.setRootVisible(false);
        configTree.setShowsRootHandles(true);

        MouseAdapter mouseadapter = new MouseAdapter() {
            @Override
            public void mousePressed(final MouseEvent mouseevent) {
                // check if mouse hit the farm, a cluster or a node
                int i = configTree.getRowForLocation(mouseevent.getX(), mouseevent.getY());
                if ((i != -1) && (mouseevent.getClickCount() == 1)) {
                    updateTreeSelection();
                }
            }
        };
        configTree.addMouseListener(mouseadapter);
        KeyAdapter keyAdapter = new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                TreePath path = configTree.getSelectionPath();
                if (path == null) {
                    return;
                }
                updateTreeSelection();
            }
        };
        configTree.addKeyListener(keyAdapter);

        // configTree.setCellRenderer(new RegistrarCellRenderer());
        JScrollPane jscrollpane = new JScrollPane(configTree);
        jscrollpane.setBackground(Color.black);
        jscrollpane.setForeground(Color.green);
        //jscrollpane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        jscrollpane.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED, Color.white, Color.white, new Color(
                0, 98, 137), new Color(0, 59, 95)));
        //        jscrollpane.setPreferredSize(new Dimension(220, 400));
        add("Center", jscrollpane);
        //		configTree.validate();
        //		jscrollpane.validate();
        //		configTree.expandPath(new TreePath(root.getPath()));
        setPreferredSize(new Dimension(500, 350));
    }

    /** get current selections and update other views */
    private void updateTreeSelection() {
        TreePath[] selections = configTree.getSelectionPaths();
        if ((selections == null) || (selections.length == 0)) {
            return;
        }
        Object[] userSel = new Object[selections.length];
        for (int j = 0; j < selections.length; j++) {
            userSel[j] = ((DefaultMutableTreeNode) selections[j].getLastPathComponent()).getUserObject();
        }
        // update the parameters window to show only common parameters
        updateParamAndModPanels(userSel);
    }

    /**
     * This is called whenever the farm's configuration changes. New 
     * clusters&nodes are added; old ones are deleted.
     * @param farm - the new farm configuration
     * @param remove - obsolete; unused
     */
    public void updateFarm(final MFarm farm, final boolean remove) {

        synchronized (getTreeLock()) {
            String watchedFarm = null;
            //            String watchedFarm = "devel_lnx_64";
            if ((watchedFarm != null) && farm.name.equals(watchedFarm)) {
                System.out.println("updating config for: " + farm.name);
            }
            // whole class parameters may have changed
            root.setUserObject(farm);
            model.nodeChanged(root);

            final TreeMap set = new TreeMap();
            for (int tci = 0; tci < root.getChildCount(); tci++) {
                DefaultMutableTreeNode tcn = (DefaultMutableTreeNode) root.getChildAt(tci);
                MCluster tc = (MCluster) tcn.getUserObject();
                set.put(tc.name, tcn);
            }

            // add new clusters & nodes
            for (Object element : farm.getClusters()) {
                MCluster cc = (MCluster) element;
                if ((watchedFarm != null) && farm.name.equals(watchedFarm)) {
                    System.out.println("cluster: " + cc.name);
                }
                if (!set.containsKey(cc.name)) { // this cluster is new, add it and all its nodes
                    DefaultMutableTreeNode ccn = new DefaultMutableTreeNode(cc);
                    model.insertNodeInto(ccn, root, root.getChildCount());
                    if ((watchedFarm != null) && farm.name.equals(watchedFarm)) {
                        System.out.println("ADD_CLUSTER " + cc.name);
                    }
                    Enumeration nodes = cc.getNodes().elements();
                    while (nodes.hasMoreElements()) {
                        MNode bn = (MNode) nodes.nextElement();
                        if ((watchedFarm != null) && farm.name.equals(watchedFarm)) {
                            System.out.println("  node: " + bn.name + " params: " + bn.getParameterList());
                        }
                        DefaultMutableTreeNode non = new DefaultMutableTreeNode(bn);
                        model.insertNodeInto(non, ccn, ccn.getChildCount());
                        if ((watchedFarm != null) && farm.name.equals(watchedFarm)) {
                            System.out.println("  ADD_NODE " + bn.name);
                        }
                    }
                    set.put(cc.name, ccn);
                } else { // we should check if there are any new nodes
                    final DefaultMutableTreeNode tcn = (DefaultMutableTreeNode) set.get(cc.name);
                    tcn.setUserObject(cc); // maybe some parameters have changed
                    model.nodeChanged(tcn);
                    Enumeration nodes = cc.getNodes().elements();

                    final TreeMap nset = new TreeMap();
                    for (int tni = 0; tni < tcn.getChildCount(); tni++) {
                        DefaultMutableTreeNode tnn = (DefaultMutableTreeNode) tcn.getChildAt(tni);
                        MNode tn = (MNode) tnn.getUserObject();
                        nset.put(tn.name, tnn);
                    }

                    while (nodes.hasMoreElements()) {
                        MNode bn = (MNode) nodes.nextElement();
                        if ((watchedFarm != null) && farm.name.equals(watchedFarm)) {
                            System.out.println("  node: " + bn.name + " params: " + bn.getParameterList());
                        }
                        if (!nset.containsKey(bn.name)) { // this node is new; we should add it
                            DefaultMutableTreeNode non = new DefaultMutableTreeNode(bn);
                            model.insertNodeInto(non, tcn, tcn.getChildCount());
                            nset.put(bn.name, non);
                            if ((watchedFarm != null) && farm.name.equals(watchedFarm)) {
                                System.out.println("  ADD_NODE " + bn.name + " to cluster " + cc.name);
                            }
                        } else { // maybe these node's parameters have changed
                            final DefaultMutableTreeNode tnn = (DefaultMutableTreeNode) nset.get(bn.name);
                            tnn.setUserObject(bn);
                            model.nodeChanged(tnn);
                        }
                    }
                }
            }

            set.clear();
            for (Object element : farm.getClusters()) {
                MCluster cc = (MCluster) element;
                set.put(cc.name, cc);
            }

            // delete old clusters & nodes
            for (int tci = 0; tci < root.getChildCount(); tci++) {
                DefaultMutableTreeNode tcn = (DefaultMutableTreeNode) root.getChildAt(tci);
                MCluster tc = (MCluster) tcn.getUserObject();
                if (!set.containsKey(tc.name)) { // this cluster is no longer in config; delete it
                    tcn.removeAllChildren(); // remove all nodes in this cluster
                    model.removeNodeFromParent(tcn);
                    if ((watchedFarm != null) && farm.name.equals(watchedFarm)) {
                        System.out.println("RMV_CLUSTER " + tc.name);
                    }
                    tci--; // avoid skipping next cluster
                } else { // we must also check if any node was deleted
                    final MCluster cc = (MCluster) set.get(tc.name);
                    final TreeSet nset = new TreeSet();
                    for (Object element : cc.getNodes()) {
                        MNode cn = (MNode) element;
                        nset.add(cn.name);
                    }
                    for (int tni = 0; tni < tcn.getChildCount(); tni++) {
                        DefaultMutableTreeNode tnn = (DefaultMutableTreeNode) tcn.getChildAt(tni);
                        MNode tn = (MNode) tnn.getUserObject();
                        if (!nset.contains(tn.name)) { // this node must be deleted
                            model.removeNodeFromParent(tnn);
                            if ((watchedFarm != null) && farm.name.equals(watchedFarm)) {
                                System.out.println("  RMV_NODE " + tn.name + " from cluster " + cc.name);
                            }
                            tni--;
                        }
                    }
                }
            }
        }
        // something must have changed; update the other panels if necessary
        updateTreeSelection();
        configTree.repaint();
    }

    /**
     * the farm has just been discovered and the tree with its config must be
     * created
     * @param fa - the MFarm
     */
    public void addFarm(MFarm fa) {
        makeBasicTree(fa);
        for (Object element : fa.getClusters()) {
            MCluster cc = (MCluster) element;
            //Do not display Traceroute Cluster
            if (cc.name.equals("Traceroute")) {
                continue;
            }
            DefaultMutableTreeNode ccn = new DefaultMutableTreeNode(cc);
            root.add(ccn);
            Enumeration nodes = cc.getNodes().elements();
            while (nodes.hasMoreElements()) {
                MNode bn = (MNode) nodes.nextElement();
                DefaultMutableTreeNode non = new DefaultMutableTreeNode(bn);
                ccn.add(non);
            }
        }
        configTree.expandPath(new TreePath(root.getPath()));
    }

    private class PlotListener implements ActionListener {

        final static long DEFAULT_HISTORY = -2 * 60 * 60 * 1000; // history plots for two hours by default
        final static long DEFAULT_REALTIME = -1 * 60 * 1000; // realtime plots with data over last 1 minutes;
        final static long DEFAULT_SUMMARY = -30 * 60 * 1000; // summary plots with data over last 30 minutes;
        final static long DEFAULT_HIST_IPERF = -24 * 60 * 60 * 1000; // very rare measurements
        final static long DEFAULT_HIST_VOMOD = -2 * 60 * 60 * 1000; // vo modules - history for last 2 hours
        final static long DEFAULT_RT_IPERF = -24 * 60 * 60 * 1000; // very rare measurements
        final static long DEFAULT_RT_VOMOD = -7 * 60 * 1000; // vo modules - realtime data from last 7 minutes

        final static int TYPE_HISTORY = 1; // types of charts
        final static int TYPE_REALTIME = 2;
        final static int TYPE_SUMMARY = 3;

        boolean clusterSelected = false;
        Vector clusters = null;
        Vector predicates = null;

        private void helpSelection() {
            JOptionPane.showMessageDialog(parentFrame, "Please select at least a Cluster or at least a Node \n"
                    + "and one or more parameters to plot!", "Information", JOptionPane.INFORMATION_MESSAGE);
        }

        private void helpClusterSelection() {
            JOptionPane.showMessageDialog(parentFrame,
                    "Please select at least a Cluster or a few Nodes and one or more parameters to plot!",
                    "Information", JOptionPane.INFORMATION_MESSAGE);
        }

        /** 
         * build a list with predicates generated from the current tree selection,
         * create a list with all selected clusters
         * set clusterSelected to true if more than one node was selected*/
        private void buildPredicates(Object source) {
            clusterSelected = false;
            clusters = new Vector();
            predicates = new Vector();
            //TODO: check to see if below exeption still appears
            /*
             * cand am dat history nu mai prindea evenimentul de la butoanele de
             acolo (history, real time, etc).

             Exception in thread "AWT-EventQueue-0"
             java.lang.ArrayIndexOutOfBoundsException: 4 >= 4
             at java.util.Vector.elementAt(Vector.java:432)
             at javax.swing.DefaultListModel.getElementAt(DefaultListModel.java:70)
             at javax.swing.JList.getSelectedValues(JList.java:1781)
             at lia.Monitor.GUIs.RCMonPanel$PlotListener.buildPredicates(RCMonPanel.java:481)
             at lia.Monitor.GUIs.RCMonPanel$PlotListener.actionPerformed(RCMonPanel.java:594)
             at javax.swing.AbstractButton.fireActionPerformed(AbstractButton.java:1849)
             at javax.swing.AbstractButton$Handler.actionPerformed(AbstractButton.java:2169)
             at javax.swing.DefaultButtonModel.fireActionPerformed(DefaultButtonModel.java:420)
             at javax.swing.DefaultButtonModel.setPressed(DefaultButtonModel.java:258)
             at javax.swing.plaf.basic.BasicButtonListener.mouseReleased(BasicButtonListener.java:234)
             at java.awt.Component.processMouseEvent(Component.java:5488)
             at javax.swing.JComponent.processMouseEvent(JComponent.java:3093)
             at java.awt.Component.processEvent(Component.java:5253)
             at java.awt.Container.processEvent(Container.java:1966)
             at java.awt.Component.dispatchEventImpl(Component.java:3955)
             at java.awt.Container.dispatchEventImpl(Container.java:2024)
             at java.awt.Component.dispatchEvent(Component.java:3803)
             at java.awt.LightweightDispatcher.retargetMouseEvent(Container.java:4212)
             at java.awt.LightweightDispatcher.processMouseEvent(Container.java:3892)
             at java.awt.LightweightDispatcher.dispatchEvent(Container.java:3822)
             at java.awt.Container.dispatchEventImpl(Container.java:2010)
             at java.awt.Window.dispatchEventImpl(Window.java:1766)
             at java.awt.Component.dispatchEvent(Component.java:3803)
             at java.awt.EventQueue.dispatchEvent(EventQueue.java:463)
             at java.awt.EventDispatchThread.pumpOneEventForHierarchy(EventDispatchThread.java:234)
             at java.awt.EventDispatchThread.pumpEventsForHierarchy(EventDispatchThread.java:163)
             at java.awt.EventDispatchThread.pumpEvents(EventDispatchThread.java:157)
             at java.awt.EventDispatchThread.pumpEvents(EventDispatchThread.java:149)
             at java.awt.EventDispatchThread.run(EventDispatchThread.java:110)
             */
            //change to a synchronized call for modList
            //			Object[] paramObjs = valPanel.modList.getSelectedValues();
            Object[] paramObjs = valPanel.getSelectedValues();
            currentUnits = valPanel.getCurrentUnits();

            String[] params = new String[paramObjs.length];
            System.arraycopy(paramObjs, 0, params, 0, paramObjs.length);
            if ((params == null) || (params.length == 0)) {
                return;
            }
            TreePath[] selPaths = configTree.getSelectionPaths();
            if (selPaths != null) {
                for (TreePath path : selPaths) {
                    int len = path.getPathCount();
                    // don't allow selecting the farm or ?!?
                    if ((len < 2) || (len > 3)) {
                        continue;
                    }
                    String farm = ((MFarm) ((DefaultMutableTreeNode) path.getPathComponent(0)).getUserObject()).name;
                    String cluster = ((MCluster) ((DefaultMutableTreeNode) path.getPathComponent(1)).getUserObject()).name;
                    String node = "*";
                    if (len == 3) {
                        node = ((MNode) ((DefaultMutableTreeNode) path.getPathComponent(2)).getUserObject()).name;
                    }
                    // if haven't clicked on a node, it must be a cluster plot
                    if (node.equals("*")) {
                        clusterSelected = true;
                    }
                    // ensure that in clusters is a list of unique cluster names
                    if (!clusters.contains(cluster)) {
                        clusters.add(cluster);
                    }
                    monPredicate pred = new monPredicate(farm, cluster, node, 0, -1, params, null);
                    predicates.add(pred);
                }
            }
            // if we have more than one node, it's a cluster plot
            if (predicates.size() > 1) {
                clusterSelected = true;
            }
            // if we have some valid predicates, set the start time for each of them
            // and if we have a Cluster History data plot, restrict the number of parameters to one
            if (predicates.size() > 0) {
                long startTime = getHistoryTime(source);
                String[] newParams = null;
                if (clusterSelected && (source == valPanel.show)) {
                    newParams = new String[] { params[0] };
                }
                for (Iterator pit = predicates.iterator(); pit.hasNext();) {
                    monPredicate pred = (monPredicate) pit.next();
                    pred.tmin = startTime;
                    //System.out.println("startTime="+(startTime/1000/60)+" min");
                    if (newParams != null) {
                        pred.parameters = newParams;
                    }
                }
            }
        }

        /** based on current selection and the desired plot, suggest a default history time 
         * if there are different types of clusters, choose the smallest time interval 
         */
        private long getHistoryTime(Object source) {
            long rezTime = Long.MIN_VALUE;
            long defaultTime = 0;
            int type = 0;
            if (source == valPanel.show) {
                type = TYPE_HISTORY;
                defaultTime = DEFAULT_HISTORY;
            } else if (source == valPanel.barshow) {
                type = TYPE_REALTIME;
                defaultTime = DEFAULT_REALTIME;
            } else if ((source == valPanel.mnBarAverage) || (source == valPanel.mnPieAverage)
                    || (source == valPanel.mnBarSum) || (source == valPanel.mnPieSum)
                    || (source == valPanel.mnBarIntegral) || (source == valPanel.mnPieIntegral)
                    || (source == valPanel.mnBarMinMax) || (source == valPanel.mnPieMinMax)) {
                type = TYPE_SUMMARY;
                defaultTime = DEFAULT_SUMMARY;
            } else {
                logger.log(Level.WARNING, "Unknown plot source");
            }

            // hack for McastGanglia source module for the data
            ListModel lm = modPanel.modList.getModel();
            for (int i = 0; i < lm.getSize(); i++) {
                if ((lm.getElementAt(i) != null) && (lm.getElementAt(i).toString().indexOf("McastGanglia") != -1)) {
                    rezTime = -2 * 60 * 60 * 1000;
                    break;
                }
            }
            // hack for different cluster names
            for (Iterator cit = clusters.iterator(); cit.hasNext();) {
                String cluster = (String) cit.next();
                if ((cluster.indexOf("IEPM-BW") != -1) || (cluster.indexOf("_WS") != -1)
                        || (cluster.indexOf("Abing") != -1)) {
                    switch (type) {
                    case TYPE_HISTORY:
                        if (rezTime < DEFAULT_HIST_IPERF) {
                            rezTime = DEFAULT_HIST_IPERF;
                        }
                        break;
                    case TYPE_REALTIME:
                    case TYPE_SUMMARY:
                        if (rezTime < DEFAULT_RT_IPERF) {
                            rezTime = DEFAULT_RT_IPERF;
                        }
                        break;
                    }
                } else if (cluster.indexOf("VO_") != -1) {
                    switch (type) {
                    case TYPE_HISTORY:
                        if (rezTime < DEFAULT_HIST_VOMOD) {
                            rezTime = DEFAULT_HIST_VOMOD;
                        }
                        break;
                    case TYPE_REALTIME:
                    case TYPE_SUMMARY:
                        if (rezTime < DEFAULT_RT_IPERF) {
                            rezTime = DEFAULT_RT_IPERF;
                        }
                        break;
                    }
                }
            }
            // if no hack was applied, use default value for this kind of plot (history, realtime, summary)
            if (rezTime == Long.MIN_VALUE) {
                rezTime = defaultTime;
            }
            logger.log(Level.INFO, "Registering for data on last " + (rezTime / 1000 / 60) + " minutes");
            return rezTime;
        }

        /** called when one of the plot buttons was pushed */
        @Override
        public void actionPerformed(ActionEvent e) {
            DataPlotter crtPlot = null;
            Object source = e.getSource();
            buildPredicates(source);
            if (predicates.size() == 0) {
                helpSelection();
                return;
            }

            //			System.out.println("Sending predicates");
            //			for (int i=0; i<predicates.size(); i++) {
            //			monPredicate p = (monPredicate)predicates.get(i);
            //			System.out.print(p.Farm+"/"+p.Cluster+"/"+p.Node+"/"+p.tmin+"/"+p.tmax+"/");
            //			for (int k=0; k<p.parameters.length; k++)
            //			System.out.println("/"+p.parameters[k]);
            //			System.out.println("");
            //			}

            if (source == valPanel.show) {
                // it's a history plot
                if (clusterSelected) {
                    crtPlot = new MonDataPlotClus(thisReference, dataprovider, predicates, clusters);
                    ((MonDataPlotClus) crtPlot).setCurrentUnit(currentUnits);
                } else {
                    crtPlot = new MonDataPlot(thisReference, dataprovider, predicates);
                    ((MonDataPlot) crtPlot).setCurrentUnit(currentUnits);
                }
            } else if (source == valPanel.barshow) {
                for (Iterator pit = predicates.iterator(); pit.hasNext();) {
                    monPredicate pred = (monPredicate) pit.next();
                    pred.bLastVals = true;
                }
                // no, it's a realtime plot
                crtPlot = new BarMonDataPlotClus(thisReference, dataprovider, predicates, clusters, currentUnits);
            } else if ((source == valPanel.mnBarAverage) || (source == valPanel.mnPieAverage)
                    || (source == valPanel.mnBarSum) || (source == valPanel.mnPieSum)
                    || (source == valPanel.mnBarIntegral) || (source == valPanel.mnPieIntegral)
                    || (source == valPanel.mnBarMinMax) || (source == valPanel.mnPieMinMax)) {
                // no, in fact it's a summary plot. Let's see what type...
                if (clusterSelected) {
                    boolean pieOrBar = !((source == valPanel.mnPieAverage) || (source == valPanel.mnPieSum)
                            || (source == valPanel.mnPieIntegral) || (source == valPanel.mnPieMinMax));
                    int summaryType = -1;
                    if ((source == valPanel.mnBarAverage) || (source == valPanel.mnPieAverage)) {
                        summaryType = BarClusterSummaryPlot.BCSP_AVERAGE;
                    } else if ((source == valPanel.mnBarSum) || (source == valPanel.mnPieSum)) {
                        summaryType = BarClusterSummaryPlot.BCSP_SUM;
                    } else if ((source == valPanel.mnBarIntegral) || (source == valPanel.mnPieIntegral)) {
                        summaryType = BarClusterSummaryPlot.BCSP_INTEGRAL;
                    } else if ((source == valPanel.mnBarMinMax) || (source == valPanel.mnPieMinMax)) {
                        summaryType = BarClusterSummaryPlot.BCSP_MINMAX;
                    }

                    if (valPanel.lastSummaryBtn == valPanel.clusterSummary) {
                        crtPlot = new BarClusterSummaryPlot(thisReference, dataprovider, predicates, clusters,
                                summaryType, pieOrBar, currentUnits);
                    } else if (valPanel.lastSummaryBtn == valPanel.nodeSummary) {
                        crtPlot = new BarNodeSummaryPlot(thisReference, dataprovider, predicates, clusters,
                                summaryType, pieOrBar, currentUnits);
                    } else {
                        logger.log(Level.WARNING, "event from unknown source " + source);
                    }
                } else {
                    helpClusterSelection();
                }
            }
            if (crtPlot != null) {
                crtPlot.setFarmName(farmName);
                crtPlot.setCountryCode(countryCode);
                crtPlot.setLocalTime(localTime);
                winList.add(crtPlot);
            }
        }
    }

    /** the parent farm is dead, so stop all plotting windows */
    public void stopIt() {
        if (winList != null) {
            for (int i = 0; i < winList.size(); i++) {
                DataPlotter win = (DataPlotter) winList.get(i);
                win.stopIt();
                winList.remove(i);
                i--;
            }
        }
    }

    /** called from a plotter window when it is closed */
    @Override
    public void stopPlot(DataPlotter win) {
        if (winList != null) {
            winList.remove(win);
            win.stopIt();
        }
    }

    /*
     public static void main ( String args[] )
     {
     Vector config = new Vector();
     MFarm  f1 = new MFarm ("farm1");
     //f1.addNode( new BNode("node1", "f1" )); f1.addNode( new BNode("node2", "f1" ));
      Farm  f2 = new Farm ("farm2");
      //f2.addNode( new BNode("node1", "f2" )); f2.addNode( new BNode("node2", "f2" ));
       config.add(f1); config.add(f2);

       Cache cache = new Cache ( "test" );
       RCMonPanel rcm = new RCMonPanel( cache, "RC", config ) ;

       JFrame f = new JFrame();
       f.getContentPane().add(new JScrollPane( rcm));
       f.pack();
       f.setVisible( true);



       }
     */
}
