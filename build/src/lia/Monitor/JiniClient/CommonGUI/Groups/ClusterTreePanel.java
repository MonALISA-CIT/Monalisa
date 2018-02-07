package lia.Monitor.JiniClient.CommonGUI.Groups;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.border.BevelBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MNode;

public class ClusterTreePanel extends JPanel implements TreeSelectionListener {

    /**
     * 
     */
    private static final long serialVersionUID = 544029194343495232L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(ClusterTreePanel.class.getName());

    protected JTree tree = null;
    protected DefaultMutableTreeNode root = null;
    protected Vector clusters = null;
    protected DefaultTreeModel treeModel = null;
    protected DefaultTreeCellRenderer treeRenderer = null;
    protected GroupsPanel basicPanel = null;

    protected TreePath currentSelectedPaths[] = null;

    public ClusterTreePanel(String name, GroupsPanel basicPanel) {

        super();
        this.basicPanel = basicPanel;
        root = new DefaultMutableTreeNode("");
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
                    if (obj instanceof MNode) {
                        int nrParams = ((MNode) obj).getParameterList().size();
                        return nrParams + " parameter" + (nrParams != 1 ? "s" : "");
                        //modules = ((MNode) obj).getModuleList();
                    } else if (obj instanceof MCluster) {
                        //						int nrNodes = ((MCluster) obj).getNodes().size();
                        int nrNodes = defaultmutabletreenode.getChildCount();
                        int nrParams = 0;
                        final Vector base = new Vector();
                        final TreeSet set = new TreeSet();
                        for (int i = 0; i < defaultmutabletreenode.getChildCount(); i++) {
                            MNode node = (MNode) (((DefaultMutableTreeNode) defaultmutabletreenode.getChildAt(i))
                                    .getUserObject());
                            reunionVectors(set, base, node.getParameterList());
                            nrParams += node.getParameterList().size();
                        }
                        int tparams = base.size();
                        return "<html>" + nrNodes + " node" + (nrNodes != 1 ? "s" : "") + "<br>" + tparams
                                + " unique param" + (tparams != 1 ? "s" : "") + "<br>" + nrParams + " total param"
                                + (nrParams != 1 ? "s" : "") + "</html>";
                        //modules = ((MCluster) obj).getModuleList();
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
                        if ((mouseevent.getX() - rect.getX()) < 16) {
                            ((TreeRenderer) treeRenderer).mouseEvent((DefaultMutableTreeNode) path
                                    .getLastPathComponent());
                            return;
                        }
                    }
                    // check if mouse hit a cluster or a node
                    int i = tree.getRowForLocation(mouseevent.getX(), mouseevent.getY());
                    if ((i != -1) && (mouseevent.getClickCount() == 1)) {
                        updateTreeSelection();
                    }
                }
            }
        };
        tree.addMouseListener(mouseadapter);
        KeyAdapter keyadapter = new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                synchronized (getTreeLock()) {
                    TreePath path = tree.getSelectionPath();
                    if (path == null) {
                        clearPath();
                        return;
                    }
                    updateTreeSelection();
                }
            }
        };
        tree.addKeyListener(keyadapter);

        tree.setRootVisible(false);
        tree.setEditable(false);
        tree.putClientProperty("JTree.lineStyle", "Angled");
        tree.setRowHeight(0);
        tree.setExpandsSelectedPaths(true);
        tree.setCellRenderer(treeRenderer);
        tree.addTreeSelectionListener(this);
        tree.setBackground(new Color(205, 226, 247));
        tree.setRowHeight(17);
        tree.setAutoscrolls(true);
        ToolTipManager.sharedInstance().registerComponent(tree);
        JScrollPane treeView = new JScrollPane(tree);
        treeView.setBackground(Color.black);
        treeView.setForeground(Color.green);
        treeView.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED, Color.white, Color.white, new Color(0,
                98, 137), new Color(0, 59, 95)));

        setLayout(new BorderLayout());
        add(treeView, BorderLayout.CENTER);
        setBorder(BorderFactory.createTitledBorder(name));
        treeModel = (DefaultTreeModel) tree.getModel();
    }

    /** get current selections and update other views */
    void updateTreeSelection() {

        TreePath[] selections = tree.getSelectionPaths();
        currentSelectedPaths = selections;
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

    private void auxiliarAdding(final TreeSet set, final Vector base) {

        if ((set == null) || (base == null)) {
            return;
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
            set.add(str);
        }
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
        TreeSet funcSet = null;
        TreeSet moduleSet = null;
        if (!basicPanel.groupPanel.intersectSelected) {
            funcSet = new TreeSet();
            auxiliarAdding(funcSet, func);
            if (modules != null) {
                moduleSet = new TreeSet();
                for (int i = 0; i < modules.size(); i++) {
                    final Object m = modules.get(i);
                    if (m != null) {
                        moduleSet.add(m);
                    }
                }
                auxiliarAdding(moduleSet, modules);
            }
        }
        for (int i = 1; i < obj.length; i++) {
            if (basicPanel.groupPanel.intersectSelected) {
                intersectVectors(func, getParamList(obj[i]));
                intersectVectors(modules, getModuleList(obj[i]));
            } else {
                reunionVectors(funcSet, func, getParamList(obj[i]));
                reunionVectors(moduleSet, modules, getModuleList(obj[i]));
            }
        }
        func = order(func);
        modules = order(modules);
        basicPanel.getValPanel().updateValues(func);
        basicPanel.getModPanel().updateList(modules);
    }

    private Vector getParameters(MCluster cluster) {

        Vector base = null;
        TreeSet s = null;
        rcNode nodes[] = basicPanel.groupPanel.getSelectedFarmNodes();
        Vector clusters = new Vector();
        for (rcNode node : nodes) {
            Vector cl = node.client.farm.getClusters();
            for (int k = 0; k < cl.size(); k++) {
                MCluster c = (MCluster) cl.get(k);
                if (c.getName().equals(cluster.getName())) {
                    clusters.add(c);
                }
            }
        }
        for (int k = 0; k < clusters.size(); k++) {
            MCluster cl = (MCluster) clusters.get(k);
            for (int i = 0; i < cl.getNodes().size(); i++) {
                MNode node = cl.getNodes().get(i);
                if (basicPanel.groupPanel.intersectSelected) { // is node common node ?
                    boolean is = false;
                    for (int j = 0; j < clusters.size(); j++) {
                        is = false;
                        MCluster c = (MCluster) clusters.get(j);
                        Vector mnodes = c.getNodes();
                        if ((mnodes == null) || (mnodes.size() == 0)) {
                            break;
                        }
                        for (int l = 0; l < mnodes.size(); l++) {
                            MNode n = (MNode) mnodes.get(l);
                            if (n.getName().equals(node.getName())) {
                                is = true;
                                break;
                            }
                        }
                        if (!is) {
                            break;
                        }
                    }
                    if (!is) {
                        continue;
                    }
                }
                if ((node.getParameterList() == null) || (node.getParameterList().size() == 0)) {
                    if (basicPanel.groupPanel.intersectSelected) {
                        return new Vector(); // intersect vs empty set means an empty set also
                    }
                    continue;
                }
                if (base == null) {
                    base = new Vector(node.getParameterList());
                    if (!basicPanel.groupPanel.intersectSelected) {
                        s = new TreeSet();
                        auxiliarAdding(s, node.getParameterList());
                    }
                } else {
                    if (basicPanel.groupPanel.intersectSelected) {
                        intersectVectors(base, node.getParameterList());
                    } else {
                        reunionVectors(s, base, node.getParameterList());
                    }
                }
            }
        }
        if (base == null) {
            base = new Vector();
        }
        return base;
    }

    private Vector getParameters(MNode node) {

        Vector base = null;
        TreeSet s = null;
        rcNode rcnodes[] = basicPanel.groupPanel.getSelectedFarmNodes();
        MCluster cluster = node.getCluster();
        Vector clusters = new Vector();
        for (rcNode rcnode : rcnodes) {
            Vector cl = rcnode.client.farm.getClusters();
            for (int k = 0; k < cl.size(); k++) {
                MCluster c = (MCluster) cl.get(k);
                if (c.getName().equals(cluster.getName())) {
                    clusters.add(c);
                }
            }
        }
        Vector nodes = new Vector();
        for (int k = 0; k < clusters.size(); k++) {
            Vector mnodes = ((MCluster) clusters.get(k)).getNodes();
            if (mnodes == null) {
                continue;
            }
            for (int i = 0; i < mnodes.size(); i++) {
                MNode mnode = (MNode) mnodes.get(i);
                if (mnode.getName().equals(node.getName())) {
                    nodes.add(mnode);
                }
            }
        }
        for (int i = 0; i < nodes.size(); i++) {
            MNode mnode = (MNode) nodes.get(i);
            if ((mnode.getParameterList() == null) || (mnode.getParameterList().size() == 0)) {
                if (basicPanel.groupPanel.intersectSelected) {
                    return new Vector(); // intersect vs empty set means an empty set also
                }
                continue;
            }
            if (base == null) {
                base = new Vector(mnode.getParameterList());
                if (!basicPanel.groupPanel.intersectSelected) {
                    s = new TreeSet();
                    auxiliarAdding(s, mnode.getParameterList());
                }
            } else {
                if (basicPanel.groupPanel.intersectSelected) {
                    intersectVectors(base, mnode.getParameterList());
                } else {
                    reunionVectors(s, base, mnode.getParameterList());
                }
            }
        }
        if (base == null) {
            base = new Vector();
        }
        return base;
    }

    /** get the parameter list from a MNode, MCluster or MFarm */
    private Vector getParamList(Object userObject) {

        if (userObject instanceof MNode) {
            return getParameters((MNode) userObject);
        } else if (userObject instanceof MCluster) {
            return getParameters((MCluster) userObject);
        } else {
            System.out.println("unknown instance!");
        }
        return new Vector();
    }

    private Vector getModules(MCluster cluster) {

        Vector base = null;
        TreeSet s = null;
        rcNode nodes[] = basicPanel.groupPanel.getSelectedFarmNodes();
        Vector clusters = new Vector();
        for (rcNode node : nodes) {
            Vector cl = node.client.farm.getClusters();
            for (int k = 0; k < cl.size(); k++) {
                MCluster c = (MCluster) cl.get(k);
                if (c.getName().equals(cluster.getName())) {
                    clusters.add(c);
                }
            }
        }
        for (int k = 0; k < clusters.size(); k++) {
            MCluster cl = (MCluster) clusters.get(k);
            for (int i = 0; i < cl.getNodes().size(); i++) {
                MNode node = cl.getNodes().get(i);
                if (basicPanel.groupPanel.intersectSelected) { // is node common node ?
                    boolean is = false;
                    for (int j = 0; j < clusters.size(); j++) {
                        is = false;
                        MCluster c = (MCluster) clusters.get(j);
                        Vector mnodes = c.getNodes();
                        if ((mnodes == null) || (mnodes.size() == 0)) {
                            break;
                        }
                        for (int l = 0; l < mnodes.size(); l++) {
                            MNode n = (MNode) mnodes.get(l);
                            if (n.getName().equals(node.getName())) {
                                is = true;
                                break;
                            }
                        }
                        if (!is) {
                            break;
                        }
                    }
                    if (!is) {
                        continue;
                    }
                }
                if ((node.moduleList == null) || (node.moduleList.size() == 0)) {
                    if (basicPanel.groupPanel.intersectSelected) {
                        return new Vector(); // intersect vs empty set means an empty set also
                    }
                    continue;
                }
                if (base == null) {
                    base = new Vector(node.moduleList);
                    if (!basicPanel.groupPanel.intersectSelected) {
                        s = new TreeSet();
                        auxiliarAdding(s, node.moduleList);
                    }
                } else {
                    if (basicPanel.groupPanel.intersectSelected) {
                        intersectVectors(base, node.moduleList);
                    } else {
                        reunionVectors(s, base, node.moduleList);
                    }
                }
            }
        }
        if (base == null) {
            base = new Vector();
        }
        return base;
    }

    private Vector getModules(MNode node) {

        Vector base = null;
        TreeSet s = null;
        rcNode rcnodes[] = basicPanel.groupPanel.getSelectedFarmNodes();
        MCluster cluster = node.getCluster();
        Vector clusters = new Vector();
        for (rcNode rcnode : rcnodes) {
            Vector cl = rcnode.client.farm.getClusters();
            for (int k = 0; k < cl.size(); k++) {
                MCluster c = (MCluster) cl.get(k);
                if (c.getName().equals(cluster.getName())) {
                    clusters.add(c);
                }
            }
        }
        Vector nodes = new Vector();
        for (int k = 0; k < clusters.size(); k++) {
            Vector mnodes = ((MCluster) clusters.get(k)).getNodes();
            if (mnodes == null) {
                continue;
            }
            for (int i = 0; i < mnodes.size(); i++) {
                MNode mnode = (MNode) mnodes.get(i);
                if (mnode.getName().equals(node.getName())) {
                    nodes.add(mnode);
                }
            }
        }
        for (int i = 0; i < nodes.size(); i++) {
            MNode mnode = (MNode) nodes.get(i);
            Vector modules = mnode.getModuleList();
            if (modules != null) {
                for (int ii = 0; ii < modules.size(); ii++) {
                    Object oo = modules.get(ii);
                    if (oo == null) {
                        modules.remove(ii);
                        ii--;
                    }
                }
            }
            if ((modules == null) || (modules.size() == 0)) {
                if (basicPanel.groupPanel.intersectSelected) {
                    return new Vector(); // intersect vs empty set means an empty set also
                }
                continue;
            }
            if (base == null) {
                base = new Vector(modules);
                if (!basicPanel.groupPanel.intersectSelected) {
                    s = new TreeSet();
                    auxiliarAdding(s, modules);
                }
            } else {
                if (basicPanel.groupPanel.intersectSelected) {
                    intersectVectors(base, modules);
                } else {
                    reunionVectors(s, base, modules);
                }
            }
        }
        if (base == null) {
            base = new Vector();
        }
        return base;
    }

    /** get the module list from a MNode, MCluster or MFarm */
    private Vector getModuleList(Object userObject) {

        if (userObject instanceof MNode) {
            return getModules((MNode) userObject);
        } else if (userObject instanceof MCluster) {
            return getModules((MCluster) userObject);
        }
        return new Vector();
    }

    protected Vector order(Vector base) {
        TreeMap map = new TreeMap();
        for (Iterator it = base.iterator(); it.hasNext();) {
            Object obj = it.next();
            if ((obj == null) || (obj.toString() == null)) {
                continue;
            }
            map.put(obj.toString(), obj);
        }
        Vector v = new Vector();
        for (Iterator it = map.keySet().iterator(); it.hasNext();) {
            Object obj = map.get(it.next());
            v.add(obj);
        }
        return v;
    }

    protected void clearPath() {

        tree.setSelectionPaths(null);
        basicPanel.getGroupPanel().updateTreeSelection();
    }

    public boolean areNodesSelected() {

        TreePath[] selPaths = tree.getSelectionPaths();
        if ((selPaths == null) || (selPaths.length == 0)) {
            return false;
        }
        return true;
    }

    private void intersectVectors(Vector base, Vector probe) {

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

    public void reunionVectors(final TreeSet set, final Vector base, final Vector probe) {

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

    public void updateClusters(Vector clusters, Hashtable nodes) {

        synchronized (getTreeLock()) {

            TreePath[] selPaths = tree.getSelectionPaths();
            currentSelectedPaths = selPaths;

            root.removeAllChildren();

            if ((clusters == null) || (nodes == null)) {
                treeModel.reload();
                return;
            }

            if (clusters.size() > 0) {
                TreeMap map = new TreeMap();
                for (Iterator it = clusters.iterator(); it.hasNext();) {
                    MCluster cluster = (MCluster) it.next();
                    Vector v = null;
                    if (map.containsKey(cluster.toString())) {
                        v = (Vector) map.get(cluster.toString());
                    } else {
                        v = new Vector();
                        map.put(cluster.toString(), v);
                    }
                    v.add(cluster);
                }
                clusters.clear();
                for (Iterator it = map.keySet().iterator(); it.hasNext();) {
                    Vector v = (Vector) map.get(it.next());
                    for (int i = 0; i < v.size(); i++) {
                        clusters.add(v.get(i));
                    }
                }
                for (Iterator it = clusters.iterator(); it.hasNext();) {
                    MCluster cluster = (MCluster) it.next();
                    Vector vv = (Vector) nodes.get(cluster.toString());
                    DefaultMutableTreeNode node = new DefaultMutableTreeNode(cluster);
                    root.add(node);
                    map.clear();
                    for (Iterator e1 = vv.iterator(); e1.hasNext();) {
                        MNode nod = (MNode) e1.next();
                        Vector v = null;
                        if (map.containsKey(nod.toString())) {
                            v = (Vector) map.get(nod.toString());
                        } else {
                            v = new Vector();
                            map.put(nod.toString(), v);
                        }
                        v.add(nod);
                    }
                    vv.clear();
                    for (Iterator e1 = map.keySet().iterator(); e1.hasNext();) {
                        Vector v = (Vector) map.get(e1.next());
                        for (int i = 0; i < v.size(); i++) {
                            vv.add(v.get(i));
                        }
                    }
                    for (Iterator e1 = vv.iterator(); e1.hasNext();) {
                        MNode nod = (MNode) e1.next();
                        DefaultMutableTreeNode n = new DefaultMutableTreeNode(nod);
                        node.add(n);
                    }
                }
            }
            treeModel.reload();

            Vector sel = new Vector();
            if (selPaths != null) {
                for (int i = 0; i < selPaths.length; i++) {
                    Object obj = ((DefaultMutableTreeNode) currentSelectedPaths[i].getLastPathComponent())
                            .getUserObject();
                    if (obj instanceof MCluster) {
                        MCluster cluster = (MCluster) obj;
                        for (int j = 0; j < root.getChildCount(); j++) {
                            MCluster c = (MCluster) ((DefaultMutableTreeNode) root.getChildAt(j)).getUserObject();
                            if (cluster.name.equals(c.name)) {
                                TreePath p = new TreePath(root);
                                sel.add(p.pathByAddingChild(root.getChildAt(j)));
                                break;
                            }
                        }
                    } else { // MNode
                        MCluster cluster = (MCluster) ((DefaultMutableTreeNode) (((DefaultMutableTreeNode) currentSelectedPaths[i]
                                .getLastPathComponent())).getParent()).getUserObject();
                        MNode node = (MNode) obj;
                        for (int j = 0; j < root.getChildCount(); j++) {
                            MCluster c = (MCluster) ((DefaultMutableTreeNode) root.getChildAt(j)).getUserObject();
                            if (cluster.name.equals(c.name)) {
                                TreePath p = new TreePath(root);
                                p = p.pathByAddingChild(root.getChildAt(j));
                                for (int k = 0; k < root.getChildAt(j).getChildCount(); k++) {
                                    MNode n = (MNode) ((DefaultMutableTreeNode) (root.getChildAt(j).getChildAt(k)))
                                            .getUserObject();
                                    if (n.name.equals(node.name)) {
                                        sel.add(p.pathByAddingChild(root.getChildAt(j).getChildAt(k)));
                                        break;
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }

            treeModel.reload();
            if (sel.size() != 0) {
                TreePath paths[] = new TreePath[sel.size()];
                for (int i = 0; i < sel.size(); i++) {
                    paths[i] = (TreePath) sel.get(i);
                }
                tree.setSelectionPaths(paths);
                updateTreeSelection();
            }
        }
    }

    protected TreePath[] checkPathChanges(TreePath[] selPaths) {

        if (selPaths == null) {
            return null;
        }
        TreePath path = null;
        Vector paths = new Vector();
        boolean clusterSelected = false;
        for (TreePath selPath : selPaths) {
            Object obj = ((DefaultMutableTreeNode) selPath.getLastPathComponent()).getUserObject();
            if (obj instanceof MCluster) {
                clusterSelected = true;
                path = selPath;
                break;
            }
        }
        if (clusterSelected) { // only clusters
            paths.add(path);
            for (TreePath selPath : selPaths) {
                Object obj = ((DefaultMutableTreeNode) selPath.getLastPathComponent()).getUserObject();
                if (obj instanceof MCluster) {
                    paths.add(selPath);
                }
            }
        } else { // only nodes
            path = selPaths[0];
            if (path.getPathCount() > 1) {
                MCluster cluster = (MCluster) (((DefaultMutableTreeNode) path.getPathComponent(1)).getUserObject());
                paths.add(path);
                for (int i = 1; i < selPaths.length; i++) {
                    MCluster c = (MCluster) (((DefaultMutableTreeNode) selPaths[i].getPathComponent(1)).getUserObject());
                    if (cluster.toString().equals(c.toString())) {
                        paths.add(selPaths[i]);
                    }
                }
            }
        }
        if (selPaths.length == paths.size()) {
            return null;
        }
        TreePath ret[] = new TreePath[paths.size()];
        int i = 0;
        for (Iterator it = paths.iterator(); it.hasNext();) {
            ret[i] = (TreePath) it.next();
            i++;
        }
        return ret;
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {

        synchronized (getTreeLock()) {
            TreePath[] selPaths = tree.getSelectionPaths();
            TreePath[] changedPaths = checkPathChanges(selPaths);
            if (changedPaths != null) {
                tree.setSelectionPaths(changedPaths);
            }
        }
    }

    public String[] getSelectedClusters() {

        Vector v = new Vector();
        TreePath[] selPaths = tree.getSelectionPaths();
        if ((selPaths == null) || (selPaths.length == 0)) {
            return null;
        }
        for (TreePath path : selPaths) {
            Object obj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
            if (obj instanceof MCluster) {
                v.add(((MCluster) obj).toString());
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

    public MNode[] getSelectedNodes() {

        Vector v = new Vector();
        TreePath[] selPaths = tree.getSelectionPaths();
        for (TreePath path : selPaths) {
            Object obj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
            if (obj instanceof MNode) {
                v.add(obj);
            }
        }
        MNode ret[] = new MNode[v.size()];
        int i = 0;
        for (Iterator it = v.iterator(); it.hasNext();) {
            ret[i] = (MNode) it.next();
            i++;
        }
        return ret;
    }

    public Icon getMyIcon(Object value) {

        if (value instanceof MCluster) {
            return getClusterIcon();
        } else if (value instanceof MNode) {
            return getNodeIcon();
        }
        return null;
    }

    class TreeRenderer extends DefaultTreeCellRenderer {

        protected Hashtable labels = null;
        protected Hashtable panels = null;
        protected Hashtable expIcons = null;
        protected Hashtable isExpanded = null;

        public TreeRenderer() {

            labels = new Hashtable();
            panels = new Hashtable();
            expIcons = new Hashtable();
            isExpanded = new Hashtable();
        }

        protected JPanel createPanel(JLabel label, boolean expanded, boolean leaf) {

            JPanel p = new JPanel();
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
                expIcons.put(label, l);
            }
            p.add(label);
            panels.put(label, p);
            return p;
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {

            String nodeName = "" + value;
            Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
            if (!labels.containsKey(userObj)) {
                JLabel label = new JLabel(nodeName);
                label.setFont(new Font("Tahoma", Font.BOLD, 11));
                try {
                    Icon icon = getMyIcon(((DefaultMutableTreeNode) value).getUserObject());
                    if (icon != null) {
                        label.setIcon(icon);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                labels.put(userObj, label);
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
            JLabel label = (JLabel) labels.get(userObj);
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
            tree.setSelectionPaths(currentSelectedPaths);
        }
    }

    private Icon clusterIcon = null;

    private Icon getClusterIcon() {

        if (clusterIcon != null) {
            return clusterIcon;
        }
        URL iconURL = this.getClass().getResource("/lia/images/groups/Gearwheel.gif");
        clusterIcon = new ImageIcon(iconURL);
        return clusterIcon;
    }

    private Icon nodeIcon = null;

    private Icon getNodeIcon() {

        if (nodeIcon != null) {
            return nodeIcon;
        }
        URL iconURL = this.getClass().getResource("/lia/images/groups/BlueCircle.gif");
        nodeIcon = new ImageIcon(iconURL);
        return nodeIcon;
    }

    private Icon plusIcon = null;
    private Icon minusIcon = null;

    private Icon getPlusIcon() {

        if (plusIcon != null) {
            return plusIcon;
        }
        URL iconURL = this.getClass().getResource("/lia/images/groups/TreeMinus.gif");
        plusIcon = new ImageIcon(iconURL);
        return plusIcon;
    }

    private Icon getMinusIcon() {

        if (minusIcon != null) {
            return minusIcon;
        }
        URL iconURL = this.getClass().getResource("/lia/images/groups/TreePlus.gif");
        minusIcon = new ImageIcon(iconURL);
        return minusIcon;
    }
}
