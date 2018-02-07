package lia.Monitor.JiniClient.CommonGUI.Groups;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.text.Collator;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.border.BevelBorder;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.control.MonitorControl;
import lia.Monitor.monitor.ExtendedSiteInfoEntry;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import net.jini.core.lookup.ServiceID;

public class FarmTreePanel extends JPanel implements ActionListener, MouseMotionListener {

    /**
     * 
     */
    private static final long serialVersionUID = -8614158283821665138L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(FarmTreePanel.class.getName());

    protected JTree tree = null;
    protected DefaultMutableTreeNode root = null;
    protected Vector nodes = null;
    //	protected Hashtable nameNodes = null;
    protected Hashtable groupFarms = null;
    protected DefaultTreeModel treeModel = null;
    protected DefaultTreeCellRenderer treeRenderer = null;
    protected Vector modifiedNodes = new Vector();
    protected GroupsPanel basicPanel = null;

    protected Vector selectedGroups = null;

    protected JRadioButton intersectButton = null;
    protected JRadioButton reunionButton = null;
    public boolean intersectSelected = true;

    protected JButton farmStatistics = null;

    protected JButton linkStatistics = null;

    protected JLabel farmInfo = null;

    protected JButton adminButton = null;

    public JPanel radioButtonsPanel = null;
    public JPanel farmStatisticsPanel = null;
    public JPanel linkStatisticsPanel = null;
    public JPanel infoPanel = null;
    public JPanel adminPanel = null;

    protected final Vector currentSelectedPaths = new Vector();

    protected HashSet farmsChecked = new HashSet();

    protected static KeyStoreThread ksThread = null;
    protected static final Object ksThreadLock = new Object();

    final Cursor plusCursor = createPlusCursor();
    final Cursor minusCursor = createMinusCursor();
    final Cursor defaultCursor = Cursor.getDefaultCursor();

    SeachDialog search = null;

    public FarmTreePanel(String name, GroupsPanel basicPanel) {

        super();
        this.basicPanel = basicPanel;
        this.search = new SeachDialog(this);
        nodes = new Vector();
        groupFarms = new Hashtable();
        selectedGroups = new Vector();
        root = new DefaultMutableTreeNode("");
        modifiedNodes.add(root);
        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel) {
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
                    if (obj instanceof rcNode) {
                        rcNode nn = (rcNode) obj;
                        if ((nn.client == null) || (nn.client.farm == null)) {
                            return "";
                        }
                        MFarm farm = ((rcNode) obj).client.farm;
                        int nrClusters = farm.getClusters().size();
                        int nrNodes = 0;
                        Vector base = new Vector();
                        TreeSet set = new TreeSet();
                        Vector v = farm.getClusters(), v1;
                        int tparams = 0;
                        for (int i = 0; i < v.size(); i++) {
                            MCluster mcluster = (MCluster) v.get(i);
                            v1 = mcluster.getNodes();
                            nrNodes += v1.size();
                            for (int j = 0; j < v1.size(); j++) {
                                MNode mnode = (MNode) v1.get(j);
                                reunionVectors(set, base, mnode.getParameterList());
                                tparams += mnode.getParameterList().size();
                            }
                        }
                        ;
                        int nrParams = base.size();
                        return "<html>" + nrClusters + " cluster" + (nrClusters != 1 ? "s" : "") + "<br>" + nrNodes
                                + " node" + (nrNodes != 1 ? "s" : "") + "<br>" + nrParams + " unique param"
                                + (nrParams != 1 ? "s" : "") + "<br>" + tparams + " total param"
                                + (tparams != 1 ? "s" : "") + "</html>";
                    } else if (obj instanceof String) {
                        if (groupFarms.containsKey(obj)) {
                            Vector vv = (Vector) groupFarms.get(obj);
                            int nrFarms = vv.size();
                            int nrClusters = 0, nrNodes = 0, nrParams = 0, tParams = 0;
                            Vector base = new Vector();
                            TreeSet set = new TreeSet();
                            for (Enumeration e = vv.elements(); e.hasMoreElements();) {
                                rcNode nn = (rcNode) e.nextElement();
                                if ((nn == null) || (nn.client == null) || (nn.client.farm == null)) {
                                    continue;
                                }
                                MFarm farm = nn.client.farm;
                                nrClusters += farm.getClusters().size();
                                Vector v = farm.getClusters(), v1;
                                for (int i = 0; i < v.size(); i++) {
                                    v1 = ((MCluster) v.get(i)).getNodes();
                                    nrNodes += v1.size();
                                    for (int j = 0; j < v1.size(); j++) {
                                        MNode mnode = (MNode) v1.get(j);
                                        reunionVectors(set, base, mnode.getParameterList());
                                        tParams += mnode.getParameterList().size();
                                    }
                                }
                                ;
                            }
                            nrParams = base.size();
                            return "<html>" + nrFarms + " service" + (nrFarms != 1 ? "s" : "") + "<br>" + nrClusters
                                    + " cluster" + (nrClusters != 1 ? "s" : "") + "<br>" + nrNodes + " node"
                                    + (nrNodes != 1 ? "s" : "") + "<br>" + nrParams + " unique param"
                                    + (nrParams != 1 ? "s" : "") + "<br>" + tParams + " total param"
                                    + (tParams != 1 ? "s" : "") + "</html>";
                        }
                    }
                    return obj.getClass().toString();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Couldn't create tooltip", t);
                    return null;
                }
            }
        };
        ToolTipManager.sharedInstance().registerComponent(tree);
        treeRenderer = new TreeRenderer();
        treeRenderer.setBackgroundSelectionColor(new Color(228, 219, 165));
        treeRenderer.setBackgroundNonSelectionColor(new Color(205, 226, 247));
        treeRenderer.setTextSelectionColor(Color.blue);
        treeRenderer.setTextNonSelectionColor(new Color(14, 18, 43));
        treeRenderer.setFont(new Font("Tahoma", Font.BOLD, 11));
        treeRenderer.setMinimumSize(null);
        treeRenderer.setMaximumSize(null);
        treeRenderer.setPreferredSize(null);

        MouseAdapter mouseadapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent mouseevent) {
                synchronized (getTreeLock()) {
                    // check if mouse hit the farm, or a group
                    TreePath path = tree.getPathForLocation(mouseevent.getX(), mouseevent.getY());
                    if (path == null) {
                        clearPath();
                        return;
                    }
                    Rectangle rect = ((BasicTreeUI) tree.getUI()).getPathBounds(tree, path);
                    if (!rect.contains(mouseevent.getX(), mouseevent.getY())) {
                        clearPath();
                        return;
                    }
                    if (!((TreeNode) path.getLastPathComponent()).isLeaf()) {
                        if (Math.abs(mouseevent.getX() - rect.getX()) < 20) {
                            ((TreeRenderer) treeRenderer).mouseEvent((DefaultMutableTreeNode) path
                                    .getLastPathComponent());
                            newKeyStoreThread(false);
                            return;
                        }
                    }
                    int i = tree.getRowForLocation(mouseevent.getX(), mouseevent.getY());
                    if ((i != -1) && (mouseevent.getClickCount() == 1)) {
                        updateTreeSelection();
                    } else if ((i != -1) && (mouseevent.getClickCount() == 2)) {
                        Object obj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
                        if (obj instanceof rcNode) { // double click on a MFarm - open rcmonpanel frame
                            rcNode nn = (rcNode) obj;
                            if (nn.client != null) {
                                nn.client.setVisible(true);
                            }
                        }
                    }
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                setCursor(defaultCursor);
                updateFarmInfo(null);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setCursor(defaultCursor);
                updateFarmInfo(null);
            }
        };
        tree.addMouseListener(mouseadapter);
        tree.addMouseMotionListener(this);

        KeyAdapter panelKeyAdapter = new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                tree.requestFocus();
                KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(tree, e);
            }
        };
        addKeyListener(panelKeyAdapter);

        tree.setRootVisible(false);
        tree.setEditable(false);
        tree.putClientProperty("JTree.lineStyle", "Angled");
        tree.setExpandsSelectedPaths(true);
        BasicTreeUI ui = (BasicTreeUI) tree.getUI();
        ui.setExpandedIcon(null);
        ui.setCollapsedIcon(null);
        tree.setCellRenderer(treeRenderer);
        tree.setBackground(new Color(205, 226, 247));
        tree.setRowHeight(17);
        tree.setAutoscrolls(true);
        ToolTipManager.sharedInstance().registerComponent(tree);
        final JScrollPane treeView = new JScrollPane(tree);
        treeView.setBackground(Color.black);
        treeView.setForeground(Color.green);
        treeView.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED, Color.white, Color.white, new Color(0,
                98, 137), new Color(0, 59, 95)));

        KeyAdapter scrollKeyAdapter = new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                tree.requestFocus();
                KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(tree, e);
            }
        };
        treeView.addKeyListener(scrollKeyAdapter);

        KeyAdapter keyAdapter = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) { // for key up there is a problem when crossing from one group up to the next one
                synchronized (getTreeLock()) {
                    if ((e.getKeyCode() == KeyEvent.VK_S) && e.isControlDown()) {
                        Point pParentPos = getLocationOnScreen();
                        search.showPopup(treeView, pParentPos.x + treeView.getX(), pParentPos.y + treeView.getY());
                        return;
                    }
                    if (e.getKeyCode() != KeyEvent.VK_UP) {
                        return;
                    }
                    TreePath[] selections = tree.getSelectionPaths();
                    for (TreePath selection : selections) {
                        Object sel = ((DefaultMutableTreeNode) selection.getLastPathComponent()).getUserObject();
                        if (sel instanceof String) { // group selected
                            tree.setSelectionPath(selection);
                        }
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                synchronized (getTreeLock()) {
                    TreePath path = tree.getSelectionPath();
                    if (path == null) {
                        clearPath();
                        return;
                    }
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        TreePath paths[] = tree.getSelectionPaths();
                        if ((paths == null) || (paths.length == 0)) {
                            return;
                        }
                        for (TreePath path2 : paths) {
                            Object obj = ((DefaultMutableTreeNode) path2.getLastPathComponent()).getUserObject();
                            if (obj instanceof rcNode) { // double click on a MFarm - open rcmonpanel frame
                                rcNode nn = (rcNode) obj;
                                if (nn.client != null) {
                                    nn.client.setVisible(true);
                                }
                            }
                        }
                        return;
                    }
                    updateTreeSelection();
                }
            }
        };
        tree.addKeyListener(keyAdapter);

        setLayout(new BorderLayout());
        add(treeView, BorderLayout.CENTER);

        intersectButton = new JRadioButton("AND");
        Font radioFont = new Font("Arial", Font.PLAIN, 10);
        intersectButton.setFont(radioFont);
        intersectButton.addActionListener(this);
        intersectButton.setToolTipText("Select only the common objects");
        intersectButton.setRolloverEnabled(true);
        reunionButton = new JRadioButton("OR");
        reunionButton.setFont(radioFont);
        reunionButton.addActionListener(this);
        reunionButton.setToolTipText("Select all the objects");
        reunionButton.setRolloverEnabled(true);
        ButtonGroup group = new ButtonGroup();
        group.add(intersectButton);
        group.add(reunionButton);
        group.setSelected(intersectButton.getModel(), true);
        JPanel p1 = new JPanel();
        BoxLayout layout = new BoxLayout(p1, BoxLayout.Y_AXIS);
        p1.setLayout(layout);
        p1.add(intersectButton);
        p1.add(reunionButton);
        radioButtonsPanel = new JPanel();
        layout = new BoxLayout(radioButtonsPanel, BoxLayout.X_AXIS);
        radioButtonsPanel.setLayout(layout);
        radioButtonsPanel.add(Box.createHorizontalStrut(4));
        radioButtonsPanel.add(p1);
        radioButtonsPanel.add(Box.createHorizontalStrut(4));
        radioButtonsPanel.setBorder(BorderFactory.createTitledBorder("Multiple selection"));
        ((TitledBorder) radioButtonsPanel.getBorder()).setTitleFont(radioFont);

        farmStatistics = new JButton("Farm statistics");
        farmStatistics.setIcon(getGaugeIcon());
        farmStatistics.setEnabled(false);
        farmStatistics.setFont(radioFont);
        farmStatistics.addActionListener(this);
        farmStatisticsPanel = new JPanel();
        farmStatisticsPanel.setLayout(new BorderLayout());
        farmStatisticsPanel.add(farmStatistics, BorderLayout.CENTER);

        linkStatistics = new JButton("Farm links statistics");
        linkStatistics.setIcon(getGearIcon());
        linkStatistics.setEnabled(false);
        linkStatistics.setFont(radioFont);
        linkStatistics.addActionListener(this);
        linkStatisticsPanel = new JPanel();
        linkStatisticsPanel.setLayout(new BorderLayout());
        linkStatisticsPanel.add(linkStatistics, BorderLayout.CENTER);

        farmInfo = new JLabel("");
        farmInfo.setFont(radioFont);
        updateFarmInfo(null);
        infoPanel = new JPanel();
        infoPanel.setLayout(new BorderLayout());
        infoPanel.add(farmInfo, BorderLayout.CENTER);
        infoPanel.setBorder(BorderFactory.createTitledBorder("Farm info"));
        ((TitledBorder) infoPanel.getBorder()).setTitleFont(radioFont);

        adminButton = new JButton("Administer");
        adminButton.setIcon(getLocksIcon());
        adminButton.setVisible(false);
        adminButton.setFont(radioFont);
        adminButton.addActionListener(this);
        JPanel p2 = new JPanel();
        layout = new BoxLayout(p2, BoxLayout.Y_AXIS);
        p2.setLayout(layout);
        p2.add(adminButton);
        adminPanel = new JPanel();
        layout = new BoxLayout(adminPanel, BoxLayout.X_AXIS);
        adminPanel.setLayout(layout);
        adminPanel.add(p2);

        setBorder(BorderFactory.createTitledBorder(name));
        treeModel = (DefaultTreeModel) tree.getModel();
    }

    public void regainFocus() {
        tree.requestFocus();
    }

    public void setSelectedFarm(rcNode farm) {
        if (farm == null) {
            return;
        }
        tree.requestFocus();
        for (int i = 0; i < treeModel.getChildCount(root); i++) {
            DefaultMutableTreeNode nn = (DefaultMutableTreeNode) treeModel.getChild(root, i);
            if (nn == null) {
                continue;
            }
            for (int j = 0; j < treeModel.getChildCount(nn); j++) {
                DefaultMutableTreeNode nn1 = (DefaultMutableTreeNode) treeModel.getChild(nn, j);
                if ((nn1.getUserObject() != null) && nn1.getUserObject().equals(farm)) {
                    TreePath path = new TreePath(treeModel.getPathToRoot(nn1));
                    tree.setSelectionPath(path);
                    tree.scrollPathToVisible(path);
                    updateTreeSelection();
                }
            }
        }
    }

    /** get current selections and update other views */
    void updateTreeSelection() {

        TreePath[] selections = tree.getSelectionPaths();
        currentSelectedPaths.clear();
        if (selections != null) {
            for (TreePath selection : selections) {
                currentSelectedPaths.add(selection);
            }
        }
        if ((selections == null) || (selections.length == 0)) {
            farmStatistics.setEnabled(false);
            linkStatistics.setEnabled(false);
            synchronized (basicPanel.getClusterPanel().getTreeLock()) {
                basicPanel.getClusterPanel().updateClusters(new Vector(), new Hashtable());
            }
            synchronized (basicPanel.getValPanel().getTreeLock()) {
                basicPanel.getValPanel().updateValues(new Vector());
            }
            synchronized (basicPanel.getModPanel().getTreeLock()) {
                basicPanel.getModPanel().updateList(new Vector());
            }
            return;
        }
        if ((selections.length == 1)
                && (((DefaultMutableTreeNode) selections[0].getLastPathComponent()).getUserObject() instanceof rcNode)) {
            farmStatistics.setEnabled(canShowFarmStatistics());
            linkStatistics.setEnabled(canShowLinkStatistics());
        } else {
            farmStatistics.setEnabled(false);
            linkStatistics.setEnabled(false);
        }
        Object[] userSel = new Object[selections.length];
        selectedGroups.clear();
        for (int j = 0; j < selections.length; j++) {
            userSel[j] = ((DefaultMutableTreeNode) selections[j].getLastPathComponent()).getUserObject();
            if (userSel[j] instanceof String) { // group selected
                selectedGroups.add(userSel[j]);
                DefaultMutableTreeNode groupNode = ((DefaultMutableTreeNode) selections[j].getLastPathComponent());
                for (int i = 0; i < treeModel.getChildCount(groupNode); i++) {
                    tree.addSelectionPath(selections[j].pathByAddingChild(treeModel.getChild(groupNode, i)));
                }
            }
        }

        // update the parameters window to show only common parameters
        updateParamAndModPanels(userSel);
    }

    /**
     * update the params and modules panels, with data from the current
     * selected object in tree. It can be an instance of MFarm, MCluster
     * or MNode
     * @param obj - the currently selected object
     */
    void updateParamAndModPanels(Object[] obj) {

        if ((obj == null) || (obj.length == 0)) {
            synchronized (basicPanel.getClusterPanel().getTreeLock()) {
                basicPanel.getClusterPanel().updateClusters(new Vector(), new Hashtable());
            }
            synchronized (basicPanel.getValPanel().getTreeLock()) {
                basicPanel.getValPanel().updateValues(new Vector());
            }
            synchronized (basicPanel.getModPanel().getTreeLock()) {
                basicPanel.getModPanel().updateList(new Vector());
            }
            return;
        }
        Hashtable nodes = new Hashtable();
        Hashtable nodeTreeSets = new Hashtable();
        Vector clusters = new Vector(getClusterList(obj[0], nodes, nodeTreeSets));
        Vector func = new Vector(getParamList(obj[0]));
        Vector modules = new Vector(getModuleList(obj[0]));
        TreeSet clusterSet = null;
        TreeSet funcSet = null;
        TreeSet moduleSet = null;
        if (!intersectSelected) {
            clusterSet = new TreeSet();
            auxiliarAdding(clusterSet, clusters);
            funcSet = new TreeSet();
            auxiliarAdding(funcSet, func);
            moduleSet = new TreeSet();
            auxiliarAdding(moduleSet, modules);
        }
        for (int i = 1; i < obj.length; i++) {
            if (intersectSelected) {
                intersectVectors(clusters, getClusterList(obj[i], nodes, nodeTreeSets));
                intersectVectors(func, getParamList(obj[i]));
                intersectVectors(modules, getModuleList(obj[i]));
            } else {
                reunionVectors(clusterSet, clusters, getClusterList(obj[i], nodes, nodeTreeSets));
                reunionVectors(funcSet, func, getParamList(obj[i]));
                reunionVectors(moduleSet, modules, getModuleList(obj[i]));
            }
        }
        func = order(func);
        modules = order(modules);
        synchronized (basicPanel.getClusterPanel().getTreeLock()) {
            basicPanel.getClusterPanel().updateClusters(clusters, nodes);
        }
        if (!basicPanel.getClusterPanel().areNodesSelected()) {
            synchronized (basicPanel.getValPanel().getTreeLock()) {
                basicPanel.getValPanel().updateValues(func);
            }
        }
        synchronized (basicPanel.getModPanel().getTreeLock()) {
            basicPanel.getModPanel().updateList(modules);
        }
    }

    protected void clearPath() {

        currentSelectedPaths.clear();
        tree.setSelectionPaths(null);
        basicPanel.getClusterPanel().updateClusters(null, null);
        basicPanel.getValPanel().updateValues(null);
        basicPanel.getModPanel().updateList(null);
    }

    private void intersectVectors(final Vector base, final Vector probe) {

        if (probe == null) {
            base.clear();
            return;
        }
        if (base == null) {
            return;
        }
        final TreeSet set = new TreeSet();
        for (int i = 0; i < probe.size(); i++) {
            final Object ep = probe.get(i);
            if (ep == null) {
                continue;
            }
            final String str = ep.toString();
            if (str == null) {
                continue;
            }
            set.add(str);
        }
        for (int i = 0; i < base.size(); i++) {
            final Object el = base.get(i);
            if (el == null) {
                continue;
            }
            final String str = el.toString();
            if (str == null) {
                continue;
            }
            if (!set.contains(str)) {
                base.remove(i);
                i--;
            }
        }
    }

    void reunionVectors(final TreeSet set, final Vector base, final Vector probe) {

        if ((set == null) || (base == null) || (probe == null)) {
            return;
        }
        for (int i = 0; i < probe.size(); i++) {
            final Object el = probe.get(i);
            if (el == null) {
                continue;
            }
            final String str = el.toString();
            if (str == null) {
                continue;
            }
            if (!set.contains(str)) {
                base.add(el);
                set.add(str);
            }
        }
    }

    private void auxiliarAdding(final TreeSet set, final Vector base) {

        if ((set == null) || (base == null)) {
            return;
        }
        for (int i = 0; i < base.size(); i++) {
            Object el = base.get(i);
            if ((el == null) || (el.toString() == null)) {
                continue;
            }
            set.add(el.toString());
        }
    }

    private Vector getClusterList(Object userObject, Hashtable nodes, Hashtable nodesTreeSets) {

        if (userObject instanceof rcNode) {
            rcNode nn = (rcNode) userObject;
            if ((nn.client == null) || (nn.client.farm == null)) {
                return new Vector();
            }
            MFarm farm = nn.client.farm;

            // for debugging
            //			StringBuilder buf = new StringBuilder();
            //			buf.append("Farm: ").append(farm.name);
            //			for (Iterator it = farm.clusterList.iterator(); it.hasNext(); ) {
            //				MCluster cluster = (MCluster)it.next();
            //				buf.append(" [Cluster: ").append(cluster.getName());
            //				for (Iterator it2 = cluster.getNodes().iterator(); it2.hasNext(); ) {
            //					MNode node = (MNode)it2.next();
            //					buf.append(" (").append(node.getName()).append(")");
            //				}
            //				buf.append("]");
            //			}
            //			logger.info(buf.toString());

            for (Object element : farm.getClusters()) {
                MCluster cluster = (MCluster) element;
                Vector nodeList = cluster.getNodes();
                if (!nodes.containsKey(cluster.toString())) {
                    nodes.put(cluster.toString(), new Vector(nodeList));
                    if (!intersectSelected) {
                        TreeSet s = new TreeSet();
                        auxiliarAdding(s, nodeList);
                        nodesTreeSets.put(cluster.toString(), s);
                    }
                } else {
                    Vector vv = (Vector) nodes.get(cluster.toString());
                    if (intersectSelected) {
                        intersectVectors(vv, nodeList);
                    } else {
                        TreeSet set = (TreeSet) nodesTreeSets.get(cluster.toString());
                        reunionVectors(set, vv, nodeList);
                    }
                    nodes.put(cluster.toString(), vv);
                }
            }
            return farm.getClusters();
        } else if (userObject instanceof String) {
            Vector farms = (Vector) groupFarms.get(userObject);
            if (farms == null) {
                return new Vector();
            }
            Vector v = null;
            TreeSet ss = null;
            for (Enumeration e = farms.elements(); e.hasMoreElements();) {
                rcNode nn = (rcNode) e.nextElement();
                if ((nn == null) || (nn.client == null) || (nn.client.farm == null)) {
                    continue;
                }
                MFarm farm = nn.client.farm;
                for (Object element : farm.getClusters()) {
                    MCluster cluster = (MCluster) element;
                    Vector nodeList = cluster.getNodes();
                    if (!nodes.containsKey(cluster.toString())) {
                        nodes.put(cluster.toString(), new Vector(nodeList));
                        if (!intersectSelected) {
                            TreeSet s = new TreeSet();
                            auxiliarAdding(s, nodeList);
                            nodesTreeSets.put(cluster.toString(), s);
                        }
                    } else {
                        Vector vv = (Vector) nodes.get(cluster.toString());
                        if (intersectSelected) {
                            intersectVectors(vv, nodeList);
                        } else {
                            TreeSet set = (TreeSet) nodesTreeSets.get(cluster.toString());
                            reunionVectors(set, vv, nodeList);
                        }
                        nodes.put(cluster.toString(), vv);
                    }
                }
                Vector clusterList = farm.getClusters();
                if (v == null) {
                    v = new Vector(clusterList);
                    if (!intersectSelected) {
                        ss = new TreeSet();
                        auxiliarAdding(ss, clusterList);
                    }
                } else {
                    if (intersectSelected) {
                        intersectVectors(v, clusterList);
                    } else {
                        reunionVectors(ss, v, clusterList);
                    }
                }
            }
            if (v == null) {
                v = new Vector();
            }
            return v;
        }
        return new Vector();
    }

    protected Vector order(Vector base) {

        TreeMap map = new TreeMap();
        for (Iterator it = base.iterator(); it.hasNext();) {
            Object obj = it.next();
            if (obj != null) {
                map.put(obj.toString(), obj);
            }
        }
        Vector v = new Vector();
        for (Iterator it = map.keySet().iterator(); it.hasNext();) {
            Object obj = map.get(it.next());
            if (obj != null) {
                v.add(obj);
            }
        }
        return v;
    }

    private Vector getParameters(MFarm farm) {

        Vector base = null;
        TreeSet s = null;

        if ((farm.getClusters() == null) || (farm.getClusters().size() == 0)) {
            return new Vector();
        }
        for (int i = 0; i < farm.getClusters().size(); i++) {
            MCluster cluster = farm.getClusters().get(i);
            if ((cluster.getNodes() == null) || (cluster.getNodes().size() == 0)) {
                if (intersectSelected) {
                    return new Vector(); // intersect vs empty set means an empty set also
                }
                continue;
            }
            for (int j = 0; j < cluster.getNodes().size(); j++) {
                MNode node = cluster.getNodes().get(j);
                if ((node.getParameterList() == null) || (node.getParameterList().size() == 0)) {
                    if (intersectSelected) {
                        return new Vector(); // intersect vs empty set means an empty set also
                    }
                    continue;
                }
                if (base == null) {
                    base = new Vector(node.getParameterList());
                    if (!intersectSelected) {
                        s = new TreeSet();
                        auxiliarAdding(s, node.getParameterList());
                    }
                } else {
                    if (intersectSelected) {
                        intersectVectors(base, node.getParameterList());
                    } else {
                        reunionVectors(s, base, node.getParameterList());
                    }
                }
            }
        }
        return base;
    }

    private Vector getModules(MFarm farm) {

        Vector base = null;
        TreeSet s = null;

        if ((farm.getClusters() == null) || (farm.getClusters().size() == 0)) {
            return new Vector();
        }
        for (int i = 0; i < farm.getClusters().size(); i++) {
            MCluster cluster = farm.getClusters().get(i);
            if ((cluster.getNodes() == null) || (cluster.getNodes().size() == 0)) {
                if (intersectSelected) {
                    return new Vector(); // intersect vs empty set means an empty set also
                }
                continue;
            }
            for (int j = 0; j < cluster.getNodes().size(); j++) {
                MNode node = cluster.getNodes().get(j);
                if ((node.moduleList == null) || (node.moduleList.size() == 0)) {
                    if (intersectSelected) {
                        return new Vector(); // intersect vs empty set means an empty set also
                    }
                    continue;
                }
                if (base == null) {
                    base = new Vector(node.moduleList);
                    if (!intersectSelected) {
                        s = new TreeSet();
                        auxiliarAdding(s, node.moduleList);
                    }
                } else {
                    if (intersectSelected) {
                        intersectVectors(base, node.moduleList);
                    } else {
                        reunionVectors(s, base, node.moduleList);
                    }
                }
            }
        }
        return base;
    }

    /** get the parameter list from a MNode, MCluster or MFarm */
    private Vector getParamList(Object userObject) {

        if (userObject instanceof rcNode) {
            rcNode nn = (rcNode) userObject;
            if ((nn.client == null) || (nn.client.farm == null)) {
                return new Vector();
            }
            return getParameters(nn.client.farm);
        } else if (userObject instanceof String) {
            Vector farms = (Vector) groupFarms.get(userObject);
            if (farms == null) {
                return new Vector();
            }
            Vector v = null;
            TreeSet ss = null;
            for (Enumeration e = farms.elements(); e.hasMoreElements();) {
                rcNode nn = (rcNode) e.nextElement();
                if ((nn == null) || (nn.client == null) || (nn.client.farm == null)) {
                    continue;
                }
                MFarm farm = nn.client.farm;
                Vector parametersList = getParameters(farm);
                if (v == null) {
                    v = new Vector(parametersList);
                    if (!intersectSelected) {
                        ss = new TreeSet();
                        auxiliarAdding(ss, parametersList);
                    }
                } else if (intersectSelected) {
                    intersectVectors(v, parametersList);
                } else {
                    reunionVectors(ss, v, parametersList);
                }
            }
            return v;
        }
        return new Vector();
    }

    /** get the module list from a MNode, MCluster or MFarm */
    private Vector getModuleList(Object userObject) {

        if (userObject instanceof rcNode) {
            rcNode nn = (rcNode) userObject;
            if ((nn == null) || (nn.client == null) || (nn.client.farm == null)) {
                return new Vector();
            }
            return getModules(nn.client.farm);
        } else if (userObject instanceof String) {
            Vector farms = (Vector) groupFarms.get(userObject);
            if (farms == null) {
                return new Vector();
            }
            Vector v = null;
            TreeSet ss = null;
            for (Enumeration e = farms.elements(); e.hasMoreElements();) {
                rcNode nn = (rcNode) e.nextElement();
                if ((nn == null) || (nn.client == null) || (nn.client.farm == null)) {
                    continue;
                }
                MFarm farm = nn.client.farm;
                Vector moduleList = getModules(farm);
                if (v == null) {
                    v = new Vector(moduleList);
                    if (!intersectSelected) {
                        ss = new TreeSet();
                        auxiliarAdding(ss, moduleList);
                    }
                } else if (intersectSelected) {
                    intersectVectors(v, moduleList);
                } else {
                    reunionVectors(ss, v, moduleList);
                }
            }
            return v;
        }
        return new Vector();
    }

    /** Method to add a new node to the second level of the tree */
    public void add(String nodeName, rcNode farm) {

        for (int i = 0; i < treeModel.getChildCount(root); i++) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) treeModel.getChild(root, i);
            if (nodeName.equals(n.getUserObject().toString())) {
                boolean found = false;
                for (int j = 0; j < treeModel.getChildCount(n); j++) {
                    DefaultMutableTreeNode nn = (DefaultMutableTreeNode) treeModel.getChild(n, j);
                    if (farm.equals(nn.getUserObject())) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    return;
                }
                DefaultMutableTreeNode node = addFarm(n, farm);
                if (!groupFarms.containsKey(nodeName)) {
                    Vector vv = new Vector();
                    vv.add(farm);
                    groupFarms.put(nodeName, vv);
                } else {
                    Vector vv = (Vector) groupFarms.get(nodeName);
                    for (int k = 0; k < vv.size(); k++) {
                        if (farm.equals(vv.get(k))) {
                            vv.remove(k);
                        }
                    }
                    vv.add(farm);
                }
                modifiedNodes.add(n);
                modifiedNodes.add(node);
                if (selectedGroups.contains(n.getUserObject())) {
                    TreePath path = new TreePath(node.getPath());
                    tree.addSelectionPath(path);
                }
                return;
            }
        }
        DefaultMutableTreeNode n = addGroup(nodeName);
        modifiedNodes.add(root);
        modifiedNodes.add(n);
        DefaultMutableTreeNode node = addFarm(n, farm);
        Vector vv = new Vector();
        vv.add(farm);
        groupFarms.put(nodeName, vv);
        modifiedNodes.add(node);
    }

    private DefaultMutableTreeNode addGroup(String groupName) {

        DefaultMutableTreeNode n = new DefaultMutableTreeNode(groupName);
        try {
            for (int i = 0; i < treeModel.getChildCount(root); i++) {
                DefaultMutableTreeNode nn = (DefaultMutableTreeNode) treeModel.getChild(root, i);
                String str = nn.getUserObject().toString();
                if (str.compareToIgnoreCase(groupName) > 0) {
                    try {
                        treeModel.insertNodeInto(n, root, i);
                    } catch (Exception ex) {
                        treeModel.insertNodeInto(n, root, treeModel.getChildCount(root));
                    }
                    return n;
                }
            }
        } catch (Exception ex) {
            logger.warning("Got exception " + ex.toString());
        }
        treeModel.insertNodeInto(n, root, treeModel.getChildCount(root));
        return n;
    }

    private DefaultMutableTreeNode addFarm(DefaultMutableTreeNode parent, rcNode farm) {

        DefaultMutableTreeNode node = new DefaultMutableTreeNode(farm);
        try {
            search.addNode(farm);
            for (int i = 0; i < treeModel.getChildCount(parent); i++) {
                DefaultMutableTreeNode nn = (DefaultMutableTreeNode) treeModel.getChild(parent, i);
                String str = nn.getUserObject().toString();
                if (str.compareToIgnoreCase(farm.toString()) > 0) {
                    try {
                        treeModel.insertNodeInto(node, parent, i);
                    } catch (Exception ex) {
                        treeModel.insertNodeInto(node, parent, treeModel.getChildCount(parent));
                    }
                    return node;
                }
            }
        } catch (Exception ex) {
            logger.warning("Got exception " + ex.toString());
        }
        treeModel.insertNodeInto(node, parent, treeModel.getChildCount(parent));
        return node;
    }

    protected boolean updateNode(String nodeName, rcNode farm) {

        for (int i = 0; i < treeModel.getChildCount(root); i++) {
            DefaultMutableTreeNode n = null;
            try {
                n = (DefaultMutableTreeNode) treeModel.getChild(root, i);
            } catch (Exception ex) {
                logger.warning("Got exception " + ex.toString());
                continue;
            }
            if (nodeName.equals(n.getUserObject())) {
                for (int j = 0; j < treeModel.getChildCount(n); j++) {
                    DefaultMutableTreeNode nn = null;
                    try {
                        nn = (DefaultMutableTreeNode) treeModel.getChild(n, j);
                    } catch (Exception ex) {
                        logger.warning("Got exception " + ex.toString());
                        continue;
                    }
                    if (nn.getUserObject().equals(farm)) {
                        nn.setUserObject(farm);
                        return false;
                    }
                }
            }
        }
        add(nodeName, farm);
        return true;
    }

    //	private void printStatistics() {
    //
    //		System.out.println("nodes "+nodes.size()+" "+nodes);
    //		System.out.println("nameNodes "+nameNodes.size()+" "+nameNodes);
    //		System.out.println("groupFarms "+groupFarms.size()+" "+groupFarms);
    //		System.out.println("modifiedNodes "+modifiedNodes.size()+" "+modifiedNodes);
    //		System.out.println("selectedGroups "+selectedGroups.size()+" "+selectedGroups);
    //		System.out.println("root size: "+getObjectSize(root));
    //		System.out.println("nodes size: "+getObjectSize(nodes));
    //		System.out.println("nameNodes size: "+getObjectSize(nameNodes));
    //		System.out.println("groupFarms size: "+getObjectSize(groupFarms));
    //		System.out.println("treeModel size: "+getObjectSize(treeModel));
    //		System.out.println("modifiedNodes size: "+getObjectSize(modifiedNodes));
    //	}

    //	public static int getObjectSize(Object object){
    //
    //		if(object==null || !(object instanceof Serializable)){
    //	      return -1;
    //	    } 
    //	    try{
    //	      ByteArrayOutputStream baos = new ByteArrayOutputStream();
    //	      ObjectOutputStream oos = new ObjectOutputStream(baos);
    //	      oos.writeObject(object);
    //	      byte[] bytes = baos.toByteArray();
    //	      oos.close();
    //	      baos.close();
    //	      return bytes.length;
    //	    }
    //	    catch(Exception e){
    //	    }
    //	    return -1;
    //	  }  

    /** Method to remove a node from the second level of the tree */
    public void remove(String groupName, rcNode farm) {

        if (farm == null) {
            return;
        }

        try {
            search.removeNode(farm);
            for (int i = 0; i < treeModel.getChildCount(root); i++) {
                DefaultMutableTreeNode n = (DefaultMutableTreeNode) treeModel.getChild(root, i);
                if (groupName.equals(n.getUserObject())) {
                    for (int j = 0; j < treeModel.getChildCount(n); j++) {
                        DefaultMutableTreeNode n1 = (DefaultMutableTreeNode) treeModel.getChild(n, j);
                        if ((n1 == null) || (n1.getUserObject() == null)) {
                            logger.warning("FarmTreePanel - suspect no farm in tree at pos " + j);
                            continue;
                        }
                        if (farm.equals(n1.getUserObject())) {
                            treeModel.removeNodeFromParent(n1);
                            modifiedNodes.add(n);
                            boolean noMoreChildren = false;
                            if (groupFarms.containsKey(groupName)) {
                                Vector vv = (Vector) groupFarms.get(groupName);
                                for (int k = 0; k < vv.size(); k++) {
                                    rcNode f = (rcNode) vv.get(k);
                                    if (f.equals(farm)) {
                                        vv.remove(k);
                                        k--;
                                    }
                                }
                                noMoreChildren = (vv.size() == 0);
                            }
                            if (noMoreChildren) {
                                treeModel.removeNodeFromParent(n);
                                modifiedNodes.add(root);
                                if (groupFarms.containsKey(groupName)) {
                                    groupFarms.remove(groupName);
                                }
                                ((TreeRenderer) treeRenderer).remove(groupName);
                            }
                        }
                    }
                    return;
                }
            }
        } catch (Exception ex) {
            logger.warning("Got exception " + ex.toString());
        }
    }

    public void expandAll() {

        for (int i = 0; i < modifiedNodes.size(); i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) modifiedNodes.get(i);
            if (node.getLevel() == 0) { // root
                modifiedNodes.remove(i);
                i--;
                treeModel.reload(root);
                treeModel.nodeStructureChanged(root);
            }
        }

        for (int i = 0; i < modifiedNodes.size(); i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) modifiedNodes.get(i);
            if (node.getLevel() == 1) { // group
                modifiedNodes.remove(i);
                i--;
                boolean contains = false;
                for (int j = 0; j < root.getChildCount(); j++) {
                    DefaultMutableTreeNode nn = (DefaultMutableTreeNode) root.getChildAt(j);
                    if (nn.getUserObject().equals(node.getUserObject())) {
                        contains = true;
                        break;
                    }
                }
                if (contains) {
                    treeModel.reload(node);
                    treeModel.nodeStructureChanged(node);
                    if (checkGroupSelected(node)) {
                        TreePath path = new TreePath(node.getPath());
                        tree.addSelectionPath(path);
                    }
                }
            }
        }

        while (modifiedNodes.size() != 0) {
            try {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) modifiedNodes.remove(0);
                String group = (String) ((DefaultMutableTreeNode) node.getParent()).getUserObject();
                boolean contains = false;
                for (int j = 0; j < root.getChildCount(); j++) {
                    DefaultMutableTreeNode nn = (DefaultMutableTreeNode) root.getChildAt(j);
                    if (nn.getUserObject().equals(group)) {
                        contains = true;
                        break;
                    }
                }
                if (!contains) {
                    continue;
                }
                treeModel.reload(node);
                treeModel.nodeStructureChanged(node);
                if (checkGroupSelected(node)) {
                    TreePath path = new TreePath(node.getPath());
                    tree.addSelectionPath(path);
                }
            } catch (Exception e) {
                //				e.printStackTrace();
            }
        }

    }

    protected boolean checkGroupSelected(DefaultMutableTreeNode node) {

        if (node.getUserObject() instanceof String) {
            return selectedGroups.contains(node.getUserObject());
        }

        if (!(node.getUserObject() instanceof rcNode)) {
            return false;
        }
        if (node.getParent() == null) {
            return false;
        }
        String group = (String) (((DefaultMutableTreeNode) node.getParent()).getUserObject());
        return selectedGroups.contains(group);
    }

    protected Vector extrageGroups(String groups) {

        String str = groups;
        Vector v = new Vector();
        while (str.indexOf(",") >= 0) {
            String tmp = str.substring(0, str.indexOf(","));
            str = str.substring(str.indexOf(",") + 1);
            if (tmp.length() > 0) {
                v.add(tmp);
            }
        }
        if (str.length() > 0) {
            v.add(str);
        }
        return v;
    }

    protected boolean groupSelected(String group) {

        if ((basicPanel == null) || (basicPanel.getSerMonitorBase() == null)) {
            return false;
        }
        SerMonitorBase smb = basicPanel.getSerMonitorBase();
        if (!smb.SGroups.containsKey(group)) {
            return false;
        }
        return smb.SGroups.get(group).intValue() != 0;
    }

    protected void orderGroups() {
        TreeSet sorted = new TreeSet(new MyComparator());
        for (Enumeration e = root.children(); e.hasMoreElements();) {
            final Object ee = e.nextElement();
            if (ee != null) {
                sorted.add(ee);
            }
        }

        int i = 0;
        for (Iterator it = sorted.iterator(); it.hasNext(); i++) {
            DefaultMutableTreeNode element = (DefaultMutableTreeNode) it.next();
            treeModel.removeNodeFromParent(element);
            treeModel.insertNodeInto(element, root, i);
        }
    }

    static class MyComparator implements Comparator {

        @Override
        public int compare(Object o1, Object o2) {
            DefaultMutableTreeNode node1 = (DefaultMutableTreeNode) o1;
            DefaultMutableTreeNode node2 = (DefaultMutableTreeNode) o2;
            return Collator.getInstance().compare(node1.getUserObject(), node2.getUserObject());
        }
    }

    public void add(Map<ServiceID, rcNode> v) {

        if (tree == null) {
            return;
        }

        synchronized (getTreeLock()) {

            TreePath[] selPaths = tree.getSelectionPaths();
            currentSelectedPaths.clear();
            if (selPaths != null) {
                for (TreePath selPath : selPaths) {
                    currentSelectedPaths.add(selPath);
                }
            }

            boolean dirtyFlag = false;
            for (final rcNode node : v.values()) {
                if ((node != null) && nodes.contains(node)) {
                    Vector vv = extrageGroups(node.mlentry.Group);
                    boolean nodeIn = false;
                    for (int k = 0; k < vv.size(); k++) {
                        String str = (String) vv.get(k);
                        boolean needUpdating = false;
                        if (!groupSelected(str)) {
                            if ((currentSelectedPaths != null) && (currentSelectedPaths.size() != 0)) {
                                for (int i = 0; i < currentSelectedPaths.size(); i++) {
                                    TreePath p = (TreePath) currentSelectedPaths.get(i);
                                    Object obj = ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
                                    if (obj instanceof rcNode) {
                                        rcNode nn = (rcNode) obj;
                                        String groupSelected = (String) ((DefaultMutableTreeNode) (((DefaultMutableTreeNode) p
                                                .getLastPathComponent()).getParent())).getUserObject();
                                        if ((nn != null) && (groupSelected != null) && nn.equals(node)
                                                && groupSelected.equals(str)) {
                                            tree.removeSelectionPath((TreePath) currentSelectedPaths.remove(i));
                                            i--;
                                            needUpdating = true;
                                        }
                                    }
                                }
                            }
                            remove(str, node);
                            if (needUpdating) {
                                updateTreeSelection();
                            }
                            dirtyFlag = true;
                        } else {
                            dirtyFlag = updateNode(str, node) || dirtyFlag;
                            nodeIn = true;
                        }
                    }
                    if (!nodeIn) {
                        ((TreeRenderer) treeRenderer).remove(node);
                        farmsChecked.remove(node);
                        nodes.remove(node);
                    }
                } else {
                    dirtyFlag = true;
                    Vector vv = extrageGroups(node.mlentry.Group);
                    for (Iterator it = vv.iterator(); it.hasNext();) {
                        String str = (String) it.next();
                        if (groupSelected(str)) {
                            if ((node == null) || (node.client == null) || (node.client.farm == null)) {
                                return;
                            }
                            add(str, node);
                        }
                    }
                    nodes.add(node);
                }
            }
            for (int k = 0; k < nodes.size(); k++) {
                rcNode node = (rcNode) nodes.get(k);
                boolean test = false;
                if ((node.sid != null) && !v.containsKey(node.sid)) {
                    test = true;
                } else {
                    rcNode nn1 = v.get(node.sid);
                    if (!nn1.equals(node)) {
                        test = true;
                    }
                }
                if (test) {
                    dirtyFlag = true;
                    Vector vv = extrageGroups(node.mlentry.Group);
                    for (Iterator it = vv.iterator(); it.hasNext();) {
                        String str = (String) it.next();
                        boolean needUpdating = false;
                        if ((currentSelectedPaths != null) && (currentSelectedPaths.size() != 0)) {
                            for (int i = 0; i < currentSelectedPaths.size(); i++) {
                                TreePath p = (TreePath) currentSelectedPaths.get(i);
                                Object obj = ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
                                if (obj instanceof rcNode) {
                                    rcNode nn = (rcNode) obj;
                                    String groupSelected = (String) ((DefaultMutableTreeNode) (((DefaultMutableTreeNode) p
                                            .getLastPathComponent()).getParent())).getUserObject();
                                    if ((nn != null) && (groupSelected != null) && nn.equals(node)
                                            && groupSelected.equals(str)) {
                                        tree.removeSelectionPath((TreePath) currentSelectedPaths.remove(i));
                                        i--;
                                        needUpdating = true;
                                    }
                                }
                            }
                        }
                        remove(str, node);
                        if (needUpdating) {
                            updateTreeSelection();
                        }
                    }
                    ((TreeRenderer) treeRenderer).remove(node);
                    farmsChecked.remove(node);
                    nodes.remove(k);
                    k--;
                }
            }
            if (dirtyFlag) {
                expandAll();
                newKeyStoreThread(false);
                if (currentSelectedPaths.size() != 0) {
                    selPaths = new TreePath[currentSelectedPaths.size()];
                    for (int i = 0; i < selPaths.length; i++) {
                        selPaths[i] = (TreePath) currentSelectedPaths.get(i);
                    }
                    tree.setSelectionPaths(selPaths);
                }
                tree.repaint();
            }
        }
    }

    protected void updateFarmInfo(rcNode node) {

        if (node == null) {
            farmInfo.setText("<html><body><br><br><br><br><br><br></body></html>");
            return;
        }

        synchronized (getTreeLock()) {
            StringBuilder text = new StringBuilder();
            text.append("<html><body>");
            text.append(node.client.getFarmName()).append("@").append(node.client.getHostName()).append(":")
                    .append(node.client.getPort()).append("<br>");
            text.append("Local Time: ").append(node.client.localTime).append(" MonALISA Version: ")
                    .append(node.client.mlVersion).append("<br>");
            text.append("IP Address: ").append(node.client.getIPAddress()).append("<br>");
            text.append("Group: ").append(node.client.mle.Group).append("<br>");
            text.append("Location: ").append(node.client.mle.Location).append(", Country: ")
                    .append(node.client.mle.Country).append(", LAT: ").append(node.client.mle.LAT);
            text.append(", LONG: ").append(node.client.mle.LONG).append("<br>");
            ExtendedSiteInfoEntry esie = node.client.getExtendedSiteInfoEntry();
            text.append("Contact: ").append(esie.localContactName).append(" email: ").append(esie.localContactEMail)
                    .append("<br>");
            text.append("JVM: ").append(esie.JVM_VERSION).append("\nLIBC: ").append(esie.LIBC_VERSION);
            text.append("</body></html>");
            farmInfo.setText(text.toString());
            return;
        }
    }

    protected boolean canShowFarmStatistics() {

        synchronized (getTreeLock()) {
            if (tree.getSelectionPaths().length != 1) {
                return false;
            }
            rcNode farm = (rcNode) (((DefaultMutableTreeNode) tree.getSelectionPath().getLastPathComponent())
                    .getUserObject());
            for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
                rcNode node = (rcNode) e.nextElement();
                if (node.equals(farm)) {
                    if ((node.global_param != null) && (node.global_param.size() != 0)) {
                        return true;
                    }
                    return false;
                }
            }
        }
        return false;
    }

    public void showFarmStatics() {

        synchronized (getTreeLock()) {
            if ((tree == null) || (nodes == null)) {
                return;
            }
            TreePath paths[] = tree.getSelectionPaths();
            if ((paths == null) || (paths.length != 1)) {
                return;
            }
            if (!(((DefaultMutableTreeNode) paths[0].getLastPathComponent()).getUserObject() instanceof rcNode)) {
                return;
            }
            rcNode farm = (rcNode) (((DefaultMutableTreeNode) paths[0].getLastPathComponent()).getUserObject());
            for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
                rcNode node = (rcNode) e.nextElement();
                if (node.equals(farm)) {
                    if ((node.global_param != null) && (node.global_param.size() != 0)) {
                        new FarmStatisticsDialog(basicPanel.serMonitorBase.main, node, getGaugeIcon());
                    } else {
                        JOptionPane.showMessageDialog(basicPanel.serMonitorBase.main, "The operation is not available");
                    }
                    return;
                }
            }
        }
    }

    protected boolean canShowLinkStatistics() {

        synchronized (getTreeLock()) {
            if ((tree == null) || (nodes == null)) {
                return false;
            }
            TreePath paths[] = tree.getSelectionPaths();
            if ((paths == null) || (paths.length != 1)) {
                return false;
            }
            if (!(((DefaultMutableTreeNode) paths[0].getLastPathComponent()).getUserObject() instanceof rcNode)) {
                return false;
            }
            rcNode farm = (rcNode) (((DefaultMutableTreeNode) paths[0].getLastPathComponent()).getUserObject());
            for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
                rcNode node = (rcNode) e.nextElement();
                if (node.equals(farm)) {
                    if ((node.wconn != null) && (node.wconn.size() != 0)) {
                        return true;
                    }
                    return false;
                }
            }
        }
        return false;
    }

    public void showLinkStatistics() {

        synchronized (getTreeLock()) {
            if (tree.getSelectionPaths().length != 1) {
                return;
            }
            rcNode farm = (rcNode) (((DefaultMutableTreeNode) tree.getSelectionPath().getLastPathComponent())
                    .getUserObject());
            for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
                rcNode node = (rcNode) e.nextElement();
                if (node.equals(farm)) {
                    if ((node.wconn != null) && (node.wconn.size() != 0)) {
                        new LinkStatisticsDialog(basicPanel.serMonitorBase.main, node, getGaugeIcon());
                    } else {
                        JOptionPane.showMessageDialog(basicPanel.serMonitorBase.main, "The operation is not available");
                    }
                    return;
                }
            }
        }
    }

    public String[] getSelectedFarms() {

        Vector v = new Vector();
        TreePath[] selPaths = tree.getSelectionPaths();
        if ((selPaths == null) || (selPaths.length == 0)) {
            return null;
        }
        for (TreePath path : selPaths) {
            Object obj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
            if (obj instanceof rcNode) {
                rcNode nn = (rcNode) obj;
                String str = null;
                if ((nn.client != null) && (nn.client.farm != null)) {
                    str = nn.client.farm.toString();
                } else {
                    str = nn.toString();
                }
                if ((str != null) && !v.contains(str)) {
                    v.add(str);
                }
            }
        }
        String ret[] = new String[v.size()];
        int i = 0;
        for (Iterator it = v.iterator(); it.hasNext();) {
            ret[i] = (String) it.next();
            i++;
        }
        return ret;
    }

    public rcNode[] getSelectedFarmNodes() {

        Vector v = new Vector();
        TreePath[] selPaths = tree.getSelectionPaths();
        if ((selPaths == null) || (selPaths.length == 0)) {
            return null;
        }
        for (TreePath path : selPaths) {
            Object obj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
            if (obj instanceof rcNode) {
                rcNode rc = (rcNode) obj;
                v.add(rc);
            }
        }
        rcNode ret[] = new rcNode[v.size()];
        int i = 0;
        for (Iterator it = v.iterator(); it.hasNext();) {
            ret[i] = (rcNode) it.next();
            i++;
        }
        return ret;
    }

    public void refreshKeyStore(boolean hardCheck) {

        if (hardCheck) {
            farmsChecked.clear();
        }

        boolean farmControlable = false;
        for (int i = 0; i < tree.getRowCount(); i++) {
            DefaultMutableTreeNode nn = ((DefaultMutableTreeNode) tree.getPathForRow(i).getLastPathComponent());
            Object obj = nn.getUserObject();
            if (obj instanceof rcNode) {
                rcNode farm = (rcNode) obj;
                rcNode node = null;
                for (Iterator it = nodes.iterator(); it.hasNext();) {
                    rcNode n = (rcNode) it.next();
                    if (n.equals(farm)) {
                        node = n;
                        break;
                    }
                }
                if ((node != null) && !farmsChecked.contains(node)) {
                    MonitorControl control = null;
                    try {
                        if ((node.client != null) && (node.client.trcframe != null)
                                && (node.client.trcframe.address != null)) {
                            control = MonitorControl.connectTo(node.client.trcframe.address.getHostAddress(),
                                    node.client.trcframe.remoteRegistryPort);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    ((TreeRenderer) treeRenderer).setAuxIcon(farm, control == null);
                    if (control != null) {
                        farmControlable = true;
                        try {
                            SerMonitorBase.controlModules.put(node.client.trcframe.address.getHostAddress() + ":"
                                    + node.client.trcframe.remoteRegistryPort, control);
                        } catch (Exception e) {
                            //							e.printStackTrace();
                        }
                    }
                    treeModel.nodeChanged(nn);
                    farmsChecked.add(node);
                }
            }
        }
        if (farmControlable) {
            if (!adminButton.isVisible()) {
                adminPanel.setBorder(BorderFactory.createTitledBorder("Administer"));
                ((TitledBorder) adminPanel.getBorder()).setTitleFont(new Font("Arial", Font.PLAIN, 10));
                adminButton.setVisible(true);
            }
        } else {
            if (adminButton.isVisible()) {
                adminPanel.setBorder(null);
                adminButton.setVisible(false);
            }
        }
        tree.repaint();
    }

    class TreeRenderer extends DefaultTreeCellRenderer {

        protected Hashtable labels = null; // rcNode/String -> JLabel
        protected Hashtable vLabels = null; // JLabel -> Object(value)
        protected Hashtable doubleIcons = null; // rcNode -> Boolean
        protected Hashtable icons = null; // rcNode -> Icon
        protected Hashtable basicIcons = null; // rcNode/String -> Icon
        protected Hashtable panels = null; // JLabel -> JPanel
        protected Hashtable expIcons = null; // JLabel -> JLabel(next)
        protected Hashtable isExpanded = null; // JLabel -> Boolean

        public TreeRenderer() {

            labels = new Hashtable();
            vLabels = new Hashtable();
            doubleIcons = new Hashtable();
            icons = new Hashtable();
            basicIcons = new Hashtable();
            panels = new Hashtable();
            expIcons = new Hashtable();
            isExpanded = new Hashtable();
        }

        public void remove(String groupName) {

            basicIcons.remove(groupName);
            JLabel l = (JLabel) labels.remove(groupName);
            if (l == null) {
                return;
            }
            vLabels.remove(l);
            panels.remove(l);
            JLabel l1 = (JLabel) expIcons.remove(l);
            isExpanded.remove(l);
            if (l1 != null) {
                isExpanded.remove(l1);
            }
        }

        public void remove(rcNode farm) {

            doubleIcons.remove(farm);
            icons.remove(farm);
            basicIcons.remove(farm);
            JLabel l = (JLabel) labels.remove(farm);
            if (l == null) {
                return;
            }
            vLabels.remove(l);
            panels.remove(l);
            JLabel l1 = (JLabel) expIcons.remove(l);
            isExpanded.remove(l);
            if (l1 != null) {
                isExpanded.remove(l1);
            }
        }

        public Icon getMyIcon(Object value) {

            if (value instanceof rcNode) {
                return getFarmIcon();
            } else if (value instanceof String) {
                return getGroupIcon();
            }
            return null;
        }

        protected JPanel createPanel(JLabel label, boolean expanded, boolean leaf) {

            JPanel p = new JPanel();
            int w = 0;
            p.setOpaque(true);
            BoxLayout layout = new BoxLayout(p, BoxLayout.X_AXIS);
            p.setLayout(layout);
            p.setBackground(new Color(205, 226, 247));
            if (!leaf) {
                JLabel l = new JLabel("");
                l.setOpaque(true);
                l.setBackground(new Color(205, 226, 247));
                if (expanded) {
                    l.setIcon(getPlusIcon());
                    isExpanded.put(l, Boolean.valueOf(true));
                } else {
                    l.setIcon(getMinusIcon());
                    isExpanded.put(l, Boolean.valueOf(false));
                }
                p.add(l);
                w += 18;
                expIcons.put(label, l);
            }
            p.add(label);
            FontMetrics fm = label.getFontMetrics(label.getFont());
            w += 42 + fm.stringWidth(label.getText());
            p.setPreferredSize(new Dimension(w, 15));
            panels.put(label, p);
            return p;
        }

        public void setAuxBlankIcon(rcNode farmNode) {

            if (!labels.containsKey(farmNode)) {
                return;
            }
            JLabel label = (JLabel) labels.get(farmNode);
            if (doubleIcons.containsKey(farmNode) || !basicIcons.containsKey(farmNode)) {
                return;
            }
            ImageIcon basicIcon = (ImageIcon) basicIcons.get(farmNode);
            label.setIcon(mergeIcons((ImageIcon) getBlankIcon(), basicIcon));
        }

        public void setAuxIcon(rcNode farmNode, boolean locked) {

            if (!labels.containsKey(farmNode)) {
                return;
            }
            JLabel label = (JLabel) labels.get(farmNode);
            if (doubleIcons.containsKey(farmNode) && (((Boolean) doubleIcons.get(farmNode)).booleanValue() == locked)) {
                return;
            }
            if (!basicIcons.containsKey(farmNode)) {
                return;
            }
            if (locked) {
                ImageIcon basicIcon = (ImageIcon) basicIcons.get(farmNode);
                label.setIcon(mergeIcons((ImageIcon) getLockedIcon(), basicIcon));
                icons.put(farmNode, label.getIcon());
            } else {
                ImageIcon basicIcon = (ImageIcon) basicIcons.get(farmNode);
                label.setIcon(mergeIcons((ImageIcon) getUnlockedIcon(), basicIcon));
                icons.put(farmNode, label.getIcon());
            }
            doubleIcons.put(farmNode, Boolean.valueOf(locked));
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {

            String nodeName = ((DefaultMutableTreeNode) value).getUserObject().toString();
            if (!labels.containsKey(((DefaultMutableTreeNode) value).getUserObject())) {
                JLabel label = new JLabel(nodeName);
                label.setFont(new Font("Tahoma", Font.BOLD, 11));
                try {
                    Icon icon = getMyIcon(((DefaultMutableTreeNode) value).getUserObject());
                    if (icon != null) {
                        if (((DefaultMutableTreeNode) value).getUserObject() instanceof rcNode) {
                            label.setIcon(mergeIcons((ImageIcon) getBlankIcon(), (ImageIcon) icon));
                        } else {
                            label.setIcon(icon);
                        }
                        basicIcons.put(((DefaultMutableTreeNode) value).getUserObject(), icon);
                    }
                } catch (Exception e) {
                    //					e.printStackTrace();
                }
                labels.put(((DefaultMutableTreeNode) value).getUserObject(), label);
                vLabels.put(label, value);
                if (sel) {
                    label.setBackground(getBackgroundSelectionColor());
                } else {
                    label.setBackground(getBackgroundNonSelectionColor());
                }
                if (sel) {
                    label.setForeground(getTextSelectionColor());
                } else {
                    label.setForeground(getTextNonSelectionColor());
                }
                label.setOpaque(true);
                return createPanel(label, expanded, leaf);
            }
            JLabel label = (JLabel) labels.get(((DefaultMutableTreeNode) value).getUserObject());
            if (sel) {
                label.setBackground(getBackgroundSelectionColor());
            } else {
                label.setBackground(getBackgroundNonSelectionColor());
            }
            if (sel) {
                label.setForeground(getTextSelectionColor());
            } else {
                label.setForeground(getTextNonSelectionColor());
            }
            JPanel p = null;
            if (leaf && expIcons.containsKey(label)) {
                p = createPanel(label, expanded, leaf);
                expIcons.remove(label);
            } else if (!leaf && !expIcons.containsKey(label)) {
                p = createPanel(label, expanded, leaf);
            } else {
                p = (JPanel) panels.get(label);
            }
            if (!leaf) {
                boolean exx = ((Boolean) isExpanded.get(expIcons.get(label))).booleanValue();
                if (expanded != exx) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                    TreePath path = new TreePath(node.getPath());
                    JLabel l = (JLabel) expIcons.get(label);
                    if (expanded) {
                        tree.expandPath(path);
                        l.setIcon(getPlusIcon());
                    } else {
                        tree.collapsePath(path);
                        l.setIcon(getMinusIcon());
                    }
                }
                isExpanded.put(expIcons.get(label), Boolean.valueOf(expanded));
            }
            return p;
        }

        public void mouseEvent(DefaultMutableTreeNode node) {

            JLabel l = (JLabel) labels.get(node.getUserObject());
            if (l == null) {
                return;
            }
            JLabel label = (JLabel) expIcons.get(l);
            if (label == null) {
                return;
            }
            TreePath path = new TreePath(node.getPath());
            boolean exx = ((Boolean) isExpanded.get(label)).booleanValue();
            if (exx) {
                tree.collapsePath(path);
                label.setIcon(getMinusIcon());
                isExpanded.put(label, Boolean.valueOf(false));
            } else {
                tree.expandPath(path);
                label.setIcon(getPlusIcon());
                isExpanded.put(label, Boolean.valueOf(true));
            }
            if ((currentSelectedPaths != null) && (currentSelectedPaths.size() != 0)) {
                TreePath paths[] = new TreePath[currentSelectedPaths.size()];
                for (int i = 0; i < paths.length; i++) {
                    paths[i] = (TreePath) currentSelectedPaths.get(i);
                }
                tree.setSelectionPaths(paths);
            } else {
                tree.setSelectionPaths(new TreePath[0]);
            }
        }

        public boolean treeOpen(DefaultMutableTreeNode node) {

            JLabel l = (JLabel) labels.get(node.getUserObject());
            if (l == null) {
                return false;
            }
            JLabel label = (JLabel) expIcons.get(l);
            if (label == null) {
                return false;
            }
            //			TreePath path = new TreePath(node.getPath());
            return ((Boolean) isExpanded.get(label)).booleanValue();
        }

    }

    private Icon farmIcon = null;

    Icon getFarmIcon() {

        if (farmIcon != null) {
            return farmIcon;
        }
        URL iconURL = this.getClass().getResource("/lia/images/groups/Data.gif");
        farmIcon = new ImageIcon(iconURL);
        return farmIcon;
    }

    private Icon groupIcon = null;

    Icon getGroupIcon() {

        if (groupIcon != null) {
            return groupIcon;
        }
        URL iconURL = this.getClass().getResource("/lia/images/groups/FlowGraph.gif");
        groupIcon = new ImageIcon(iconURL);
        return groupIcon;
    }

    private Icon lockedIcon = null;

    Icon getLockedIcon() {

        if (lockedIcon != null) {
            return lockedIcon;
        }
        URL iconURL = this.getClass().getResource("/lia/images/groups/Lock.gif");
        lockedIcon = new ImageIcon(iconURL);
        return lockedIcon;
    }

    private Icon unlockedIcon = null;

    Icon getUnlockedIcon() {

        if (unlockedIcon != null) {
            return unlockedIcon;
        }
        URL iconURL = this.getClass().getResource("/lia/images/groups/UnLock.gif");
        unlockedIcon = new ImageIcon(iconURL);
        return unlockedIcon;
    }

    private Icon blankIcon = null;

    Icon getBlankIcon() {

        if (blankIcon != null) {
            return blankIcon;
        }
        URL iconURL = this.getClass().getResource("/lia/images/groups/Help.gif");
        blankIcon = new ImageIcon(iconURL);
        return blankIcon;
    }

    private Icon locksIcon = null;

    private Icon getLocksIcon() {

        if (locksIcon != null) {
            return locksIcon;
        }
        URL iconURL = this.getClass().getResource("/lia/images/groups/Locks.gif");
        locksIcon = new ImageIcon(iconURL);
        return locksIcon;
    }

    //	private Icon endIcon = null;
    //	
    //	private Icon getEndIcon() {
    //		
    //		if (endIcon != null) return endIcon;
    //		URL iconURL = this.getClass().getResource("/lia/images/groups/End.gif");
    //		endIcon = new ImageIcon(iconURL);
    //		return endIcon;
    //	}
    //	
    //	private Icon end1Icon = null;
    //	
    //	private Icon getEnd1Icon() {
    //		
    //		if (end1Icon != null) return end1Icon;
    //		URL iconURL = this.getClass().getResource("/lia/images/groups/End1.gif");
    //		end1Icon = new ImageIcon(iconURL);
    //		return end1Icon;
    //	}

    private Icon plusIcon = null;
    private Icon minusIcon = null;

    Icon getPlusIcon() {

        if (plusIcon != null) {
            return plusIcon;
        }
        URL iconURL = this.getClass().getResource("/lia/images/groups/TreeMinus.gif");
        plusIcon = new ImageIcon(iconURL);
        return plusIcon;
    }

    Icon getMinusIcon() {

        if (minusIcon != null) {
            return minusIcon;
        }
        URL iconURL = this.getClass().getResource("/lia/images/groups/TreePlus.gif");
        minusIcon = new ImageIcon(iconURL);
        return minusIcon;
    }

    private Icon gaugeIcon = null;

    private Icon getGaugeIcon() {

        if (gaugeIcon != null) {
            return gaugeIcon;
        }
        URL iconURL = this.getClass().getResource("/lia/images/groups/Gauge.gif");
        gaugeIcon = new ImageIcon(iconURL);
        return gaugeIcon;
    }

    private Icon gearIcon = null;

    private Icon getGearIcon() {

        if (gearIcon != null) {
            return gearIcon;
        }
        URL iconURL = this.getClass().getResource("/lia/images/groups/Gearwheel.gif");
        gearIcon = new ImageIcon(iconURL);
        return gearIcon;
    }

    Icon mergeIcons(ImageIcon icon1, ImageIcon icon2) {

        if ((icon1 == null) || (icon2 == null)) {
            if (icon1 != null) {
                return icon1;
            } else if (icon2 != null) {
                return icon2;
            } else {
                return null;
            }
        }
        Image im1 = icon1.getImage();
        Image im2 = icon2.getImage();
        int w1 = im1.getWidth(this);
        int h1 = im1.getHeight(this);
        int w2 = im2.getWidth(this);
        int h2 = im2.getHeight(this);
        int maxW = (w1 > w2) ? w1 : w2;
        int maxH = (h1 > h2) ? h1 : h2;
        BufferedImage image = new BufferedImage((2 * maxW) + 2, maxH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        int x = (maxW - w1) / 2;
        int y = (maxH - h1) / 2;
        g2.drawImage(im1, x, y, null);
        x = (maxW - w2) / 2;
        y = (maxH - h2) / 2;
        g2.drawImage(im2, maxW + 2 + x, y, this);
        return new ImageIcon(image);
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        if ((intersectButton != null) && e.getSource().equals(intersectButton) && !intersectSelected) {
            intersectSelected = true;
            updateTreeSelection();
            return;
        }

        if ((reunionButton != null) && e.getSource().equals(reunionButton) && intersectSelected) {
            intersectSelected = false;
            updateTreeSelection();
            return;
        }

        if ((farmStatistics != null) && e.getSource().equals(farmStatistics)) {
            showFarmStatics();
            return;
        }

        if ((linkStatistics != null) && e.getSource().equals(linkStatistics)) {
            showLinkStatistics();
            return;
        }

        //		if (adminButton != null && e.getSource().equals(adminButton)) {
        //			// TODO
        //		}
    }

    @Override
    public void mouseDragged(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {

        setCursor(defaultCursor);

        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            updateFarmInfo(null);
            return;
        }
        Rectangle rect = ((BasicTreeUI) tree.getUI()).getPathBounds(tree, path);
        if (!rect.contains(e.getX(), e.getY())) {
            updateFarmInfo(null);
            return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (!((TreeNode) path.getLastPathComponent()).isLeaf()) {
            if (Math.abs(e.getX() - rect.getX()) < 20) {
                if (((TreeRenderer) treeRenderer).treeOpen((DefaultMutableTreeNode) path.getLastPathComponent())) {
                    setCursor(plusCursor);
                } else {
                    setCursor(minusCursor);
                }
            } else {
                setCursor(defaultCursor);
            }
        }
        Object obj = node.getUserObject();
        if (obj instanceof rcNode) {
            rcNode nn = (rcNode) obj;
            updateFarmInfo(nn);
        } else {
            updateFarmInfo(null);
        }
    }

    private Cursor createPlusCursor() {
        ImageIcon icon = (ImageIcon) getPlusIcon();
        Image image = icon.getImage();
        int w = image.getWidth(null);
        int h = image.getHeight(null);
        if ((w <= 0) || (h <= 0)) {
            return Cursor.getDefaultCursor();
        }
        Dimension dim = Toolkit.getDefaultToolkit().getBestCursorSize(w, h);
        if (dim == null) {
            return Cursor.getDefaultCursor();
        }
        int w1 = (int) dim.getWidth();
        int h1 = (int) dim.getHeight();
        if ((w1 == 0) || (h1 == 0)) {
            return Cursor.getDefaultCursor();
        }
        if ((w < w1) || (h < h1)) {
            BufferedImage im = new BufferedImage(w1, h1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = im.createGraphics();
            g2.drawImage(image, 0, 0, null);
            g2.dispose();
            image = im;
        }
        return Toolkit.getDefaultToolkit().createCustomCursor(image, new Point(w / 2, h / 2), "Plus");
    }

    private Cursor createMinusCursor() {
        ImageIcon icon = (ImageIcon) getMinusIcon();
        Image image = icon.getImage();
        int w = image.getWidth(null);
        int h = image.getHeight(null);
        if ((w <= 0) || (h <= 0)) {
            return Cursor.getDefaultCursor();
        }
        Dimension dim = Toolkit.getDefaultToolkit().getBestCursorSize(w, h);
        if (dim == null) {
            return Cursor.getDefaultCursor();
        }
        int w1 = (int) dim.getWidth();
        int h1 = (int) dim.getHeight();
        if ((w1 == 0) || (h1 == 0)) {
            return Cursor.getDefaultCursor();
        }
        if ((w < w1) || (h < h1)) {
            BufferedImage im = new BufferedImage(w1, h1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = im.createGraphics();
            g2.drawImage(image, 0, 0, null);
            g2.dispose();
            image = im;
        }
        return Toolkit.getDefaultToolkit().createCustomCursor(image,
                new Point(image.getWidth(null) / 2, image.getHeight(null) / 2), "Minus");
    }

    public void newKeyStoreThread(boolean hardCheck) {

        synchronized (ksThreadLock) {
            if (ksThread == null) {
                ksThread = new KeyStoreThread(this);
                ksThread.start();
            }
            ksThread.setCheck(hardCheck);
        }
    }

    static class KeyStoreThread extends Thread {

        private final Vector currentChecks;
        private FarmTreePanel panel = null;

        public KeyStoreThread(FarmTreePanel panel) {

            super("KeyStoreThread");
            currentChecks = new Vector();
            this.panel = panel;
        }

        public void setCheck(boolean hardCheck) {

            synchronized (ksThreadLock) {
                currentChecks.add(Boolean.valueOf(hardCheck));
                ksThreadLock.notifyAll();
            }
        }

        @Override
        public void run() {

            while (true) {
                boolean hardCheck = false;
                synchronized (ksThreadLock) {
                    while (currentChecks.size() == 0) {
                        try {
                            ksThreadLock.wait();
                        } catch (Exception ex) {
                            //							ex.printStackTrace();
                        }
                    }
                    hardCheck = ((Boolean) currentChecks.remove(0)).booleanValue();
                }
                panel.refreshKeyStore(hardCheck);
            }
        }
    }

}
