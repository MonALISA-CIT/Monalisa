package lia.Monitor.JiniClient.CommonGUI.Groups;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.ListModel;
import javax.swing.border.BevelBorder;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.graphical;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Groups.Plot.DataPlotterParent;
import lia.Monitor.JiniClient.CommonGUI.Groups.Plot.MultipleBarClusterSummaryPlot;
import lia.Monitor.JiniClient.CommonGUI.Groups.Plot.MultipleBarMonDataPlotClus;
import lia.Monitor.JiniClient.CommonGUI.Groups.Plot.MultipleBarNodeSummaryPlot;
import lia.Monitor.JiniClient.CommonGUI.Groups.Plot.MultipleDataPlotter;
import lia.Monitor.JiniClient.CommonGUI.Groups.Plot.MultipleMonData3DPlot;
import lia.Monitor.JiniClient.CommonGUI.Groups.Plot.MultipleMonData3DPlotClus;
import lia.Monitor.JiniClient.CommonGUI.Groups.Plot.MultipleMonData3DPlotClusGlobal;
import lia.Monitor.JiniClient.CommonGUI.Groups.Plot.MultipleMonDataPlot;
import lia.Monitor.JiniClient.CommonGUI.Groups.Plot.MultipleMonDataPlotClus;
import lia.Monitor.JiniClient.CommonGUI.Groups.Plot.MultipleMonDataPlotClusGlobal;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.monPredicate;
import net.jini.core.lookup.ServiceID;

public class GroupsPanel extends JPanel implements graphical, DataPlotterParent {

    /**
     * 
     */
    private static final long serialVersionUID = 6799526104800895511L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(GroupsPanel.class.getName());

    protected volatile Map<ServiceID, rcNode> hnodes = null;
    protected Vector<rcNode> vnodes = null;
    protected SerMonitorBase serMonitorBase = null;
    protected static final int MAX_INV = 2;
    protected int invisibleUpdates = MAX_INV;

    protected FarmTreePanel groupPanel = null;
    protected ClusterTreePanel clusterPanel = null;
    protected ParametersListPanel valPanel = null;
    protected ModulesListPanel modPanel = null;
    protected Color c;
    protected GroupsPanel basicPanel = null;
    protected Hashtable winList = new Hashtable();
    protected DataPlotterParent thisReference;

    public GroupsPanel() {

        super();
        thisReference = this;
    }

    @Override
    public void updateNode(rcNode node) {
        // nothing
    }

    @Override
    public void gupdate() {
        // nothing
    }

    public void refresh() {

        if ((vnodes == null) || (hnodes == null)) {
            return;
        }
        groupPanel.add(hnodes);
    }

    @Override
    public void setNodes(Map<ServiceID, rcNode> nodes, Vector<rcNode> vnodes) {

        this.hnodes = nodes;
        this.vnodes = vnodes;
        refresh();
    }

    @Override
    public void setSerMonitor(SerMonitorBase ms) {
        this.serMonitorBase = ms;
        ms.setBasicPanel(this);
        valPanel.setRegistry(ms.getRegistry());
    }

    public SerMonitorBase getSerMonitorBase() {

        return serMonitorBase;
    }

    @Override
    public void setMaxFlowData(rcNode n, Vector v) {
        // nothing
    }

    @Override
    public void new_global_param(String name) {
        // nothing
    }

    public void init() {

        basicPanel = this;
        c = new Color(205, 226, 247);
        setBackground(c);
        setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED, Color.white, Color.white, new Color(0, 98, 137),
                new Color(0, 59, 95)));

        JPanel upperPanel = new JPanel();
        upperPanel.setLayout(new GridLayout(0, 3));
        groupPanel = new FarmTreePanel("Farms", this);
        clusterPanel = new ClusterTreePanel("Clusters", this);
        modPanel = new ModulesListPanel();
        valPanel = new ParametersListPanel(modPanel);

        JPanel p1 = new JPanel();
        p1.setLayout(new GridLayout(2, 0));
        p1.add(valPanel);
        p1.add(modPanel);

        upperPanel.add(groupPanel);
        upperPanel.add(clusterPanel);
        upperPanel.add(p1);

        setLayout(new BorderLayout());
        add(upperPanel, BorderLayout.CENTER);

        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BorderLayout());
        JPanel p2 = new JPanel();
        p2.setLayout(new BorderLayout());
        p2.add(groupPanel.radioButtonsPanel, BorderLayout.CENTER);
        JPanel p3 = new JPanel();
        p3.setLayout(new GridLayout(0, 1));
        p3.add(groupPanel.farmStatisticsPanel);
        p3.add(groupPanel.linkStatisticsPanel);
        p2.add(p3, BorderLayout.SOUTH);
        southPanel.add(p2, BorderLayout.WEST);
        southPanel.add(groupPanel.infoPanel, BorderLayout.CENTER);
        southPanel.add(groupPanel.adminPanel, BorderLayout.EAST);

        add(southPanel, BorderLayout.SOUTH);

        PlotListener pl = new PlotListener();

        valPanel.mn2D.addActionListener(pl);
        valPanel.mn3D.addActionListener(pl);
        valPanel.barshow.addActionListener(pl);
        valPanel.mnBarAverage.addActionListener(pl);
        valPanel.mnBarSum.addActionListener(pl);
        valPanel.mnBarIntegral.addActionListener(pl);
        valPanel.mnBarMinMax.addActionListener(pl);
        valPanel.mnPieAverage.addActionListener(pl);
        valPanel.mnPieSum.addActionListener(pl);
        valPanel.mnPieIntegral.addActionListener(pl);
        valPanel.mnPieMinMax.addActionListener(pl);

        TimerTask ttask = new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName(" ( ML ) - Groups - GroupsPanel Timer Thread");
                if (!isVisible()) {
                    invisibleUpdates--;
                    if (invisibleUpdates > 0) {
                        return;
                    }
                    invisibleUpdates = MAX_INV;
                }
                refresh();
            }
        };
        BackgroundWorker.schedule(ttask, 1000, 4000);
        ttask = new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName("( ML ) - Groups - Refresh key store Timer Thread");
                groupPanel.newKeyStoreThread(true);
            }
        };
        BackgroundWorker.schedule(ttask, 4000, 5 * 60 * 60 * 1000); // 5 hours
    }

    public FarmTreePanel getGroupPanel() {

        return groupPanel;
    }

    public ClusterTreePanel getClusterPanel() {

        return clusterPanel;
    }

    public ParametersListPanel getValPanel() {

        return valPanel;
    }

    public ModulesListPanel getModPanel() {

        return modPanel;
    }

    public void refreshKeyStore() {

        groupPanel.newKeyStoreThread(true);
    }

    /** called from a plotter window when it is closed */
    @Override
    public void stopPlot(MultipleDataPlotter win) {

        if (winList != null) {
            for (Enumeration en = winList.keys(); en.hasMoreElements();) {
                Object key = en.nextElement();
                Vector v = (Vector) winList.get(key);
                for (int i = 0; i < v.size(); i++) {
                    MultipleDataPlotter plot = (MultipleDataPlotter) v.get(i);
                    if (plot.equals(win)) {
                        v.remove(i);
                        i--;
                    }
                }
            }
            win.stopIt(null);
        }
    }

    public static void main(String args[]) {

        JFrame frame = new JFrame("test");
        GroupsPanel panel = new GroupsPanel();
        panel.init();
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(panel, BorderLayout.CENTER);
        frame.setSize(500, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    class PlotListener implements ActionListener {

        final static long DEFAULT_HISTORY = -2 * 60 * 60 * 1000;
        // history plots for two hours by default
        final static long DEFAULT_REALTIME = -1 * 60 * 1000;
        // realtime plots with data over last 1 minutes;
        final static long DEFAULT_SUMMARY = -30 * 60 * 1000;
        // summary plots with data over last 30 minutes;
        final static long DEFAULT_HIST_IPERF = -24 * 60 * 60 * 1000;
        // very rare measurements
        final static long DEFAULT_HIST_VOMOD = -2 * 60 * 60 * 1000;
        // vo modules - history for last 2 hours
        final static long DEFAULT_RT_IPERF = -24 * 60 * 60 * 1000;
        // very rare measurements
        final static long DEFAULT_RT_VOMOD = -7 * 60 * 1000;
        // vo modules - realtime data from last 7 minutes

        final static int TYPE_HISTORY = 1; // types
                                           // of
                                           // charts
        final static int TYPE_REALTIME = 2;
        final static int TYPE_SUMMARY = 3;

        HashMap currentUnits = null;
        boolean clusterSelected = false;
        Vector clusters = null;
        Vector predicates = null;
        rcNode[] selectedNodes = null;

        private void helpSelection() {
            JOptionPane.showMessageDialog(basicPanel, "Please select at least a Cluster or at least a Node \n"
                    + "and one or more parameters to plot!", "Information", JOptionPane.INFORMATION_MESSAGE);
        }

        private void helpClusterSelection() {
            JOptionPane.showMessageDialog(basicPanel,
                    "Please select at least a Cluster or a few Nodes and one or more parameters to plot!",
                    "Information", JOptionPane.INFORMATION_MESSAGE);
        }

        /**
         * build a list with predicates generated from the current tree
         * selection, create a list with all selected clusters set
         * clusterSelected to true if more than one node was selected
         */
        private void buildPredicates(Object source, boolean modifiers) {

            clusterSelected = false;
            clusters = new Vector();
            predicates = new Vector();
            //change to a synchronized call for modList
            Object[] paramObjs = valPanel.getSelectedValues();
            currentUnits = valPanel.getCurrentUnits();
            String[] params = new String[paramObjs.length];
            System.arraycopy(paramObjs, 0, params, 0, paramObjs.length);
            if ((params == null) || (params.length == 0)) {
                return;
            }

            String[] farms = groupPanel.getSelectedFarms();
            selectedNodes = groupPanel.getSelectedFarmNodes();
            String[] pclusters = clusterPanel.getSelectedClusters();
            if ((farms == null) || (pclusters == null)) {
                return;
            }
            if (pclusters.length != 0) {
                clusterSelected = true;
            }
            MNode[] nodes = clusterPanel.getSelectedNodes();
            for (String farm : farms) {
                if (pclusters.length != 0) {
                    for (int j = 0; j < pclusters.length; j++) {
                        if (!clusters.contains(pclusters[j])) {
                            clusters.add(pclusters[j]);
                        }
                        monPredicate pred = new monPredicate(farm, pclusters[j], "*", 0, -1, params, null);
                        predicates.add(pred);
                    }
                } else {
                    for (MNode node : nodes) {
                        String cluster = node.getClusterName();
                        if (!clusters.contains(cluster)) {
                            clusters.add(cluster);
                        }
                        monPredicate pred = new monPredicate(farm, cluster, node.name, 0, -1, params, null);
                        predicates.add(pred);
                    }
                }
            }

            // if we have more than one node, it's a cluster plot
            if (predicates.size() > 1) {
                clusterSelected = true;
            }
            // if we have some valid predicates, set the start time for each of
            // them
            // and if we have a Cluster History data plot, restrict the number
            // of parameters to one
            if (predicates.size() > 0) {
                long startTime = getHistoryTime(source);
                String[] newParams = null;
                if (clusterSelected && (source.equals(valPanel.mn2D) || source.equals(valPanel.mn3D)) && !modifiers) {
                    newParams = new String[] { params[0] };
                }
                for (Iterator pit = predicates.iterator(); pit.hasNext();) {
                    monPredicate pred = (monPredicate) pit.next();
                    pred.tmin = startTime;
                    //System.out.println("startTime="+(startTime/1000/60)+"
                    // min");
                    if (newParams != null) {
                        pred.parameters = newParams;
                    }
                }
            }
        }

        /**
         * based on current selection and the desired plot, suggest a default
         * history time if there are different types of clusters, choose the
         * smallest time interval
         */
        private long getHistoryTime(Object source) {
            long rezTime = Long.MIN_VALUE;
            long defaultTime = 0;
            int type = 0;
            if (source.equals(valPanel.mn2D) || source.equals(valPanel.mn3D)) {
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
            // if no hack was applied, use default value for this kind of plot
            // (history, realtime, summary)
            if (rezTime == Long.MIN_VALUE) {
                rezTime = defaultTime;
            }
            logger.log(Level.INFO, "Registering for data on last " + (rezTime / 1000 / 60) + " minutes");
            return rezTime;
        }

        /** called when one of the plot buttons was pushed */
        @Override
        public void actionPerformed(ActionEvent e) {

            MultipleDataPlotter crtPlot = null;
            Object source = e.getSource();
            int modifiers = e.getModifiers();
            boolean mod = ((modifiers & InputEvent.SHIFT_MASK) != 0);
            buildPredicates(source, mod);

            if ((selectedNodes == null) || (predicates == null) || (selectedNodes.length == 0)
                    || (predicates.size() == 0)) {
                helpSelection();
                return;
            }
            if (source == valPanel.mn2D) {
                // it's a history plot
                if (clusterSelected) {
                    if (mod) {
                        crtPlot = new MultipleMonDataPlotClusGlobal(thisReference, selectedNodes, predicates, clusters);
                        ((MultipleMonDataPlotClusGlobal) crtPlot).setCurrentUnit(currentUnits);
                    } else {
                        crtPlot = new MultipleMonDataPlotClus(thisReference, selectedNodes, predicates, clusters);
                        ((MultipleMonDataPlotClus) crtPlot).setCurrentUnit(currentUnits);
                    }
                } else {
                    crtPlot = new MultipleMonDataPlot(thisReference, selectedNodes, predicates);
                    ((MultipleMonDataPlot) crtPlot).setCurrentUnit(currentUnits);
                }
            } else if (source == valPanel.mn3D) {
                // it's a history plot
                if (clusterSelected) {
                    if (mod) {
                        crtPlot = new MultipleMonData3DPlotClusGlobal(thisReference, selectedNodes, predicates,
                                clusters);
                        ((MultipleMonData3DPlotClusGlobal) crtPlot).setCurrentUnit(currentUnits);
                    } else {
                        crtPlot = new MultipleMonData3DPlotClus(thisReference, selectedNodes, predicates, clusters);
                        ((MultipleMonData3DPlotClus) crtPlot).setCurrentUnit(currentUnits);
                    }
                } else {
                    crtPlot = new MultipleMonData3DPlot(thisReference, selectedNodes, predicates);
                    ((MultipleMonData3DPlot) crtPlot).setCurrentUnit(currentUnits);
                }
            } else if (source == valPanel.barshow) {
                // no, it's a realtime plot
                for (Iterator pit = predicates.iterator(); pit.hasNext();) {
                    monPredicate pred = (monPredicate) pit.next();
                    pred.bLastVals = true;
                }
                crtPlot = new MultipleBarMonDataPlotClus(thisReference, selectedNodes, predicates, clusters,
                        currentUnits);
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
                        summaryType = MultipleBarClusterSummaryPlot.BCSP_AVERAGE;
                    } else if ((source == valPanel.mnBarSum) || (source == valPanel.mnPieSum)) {
                        summaryType = MultipleBarClusterSummaryPlot.BCSP_SUM;
                    } else if ((source == valPanel.mnBarIntegral) || (source == valPanel.mnPieIntegral)) {
                        summaryType = MultipleBarClusterSummaryPlot.BCSP_INTEGRAL;
                    } else if ((source == valPanel.mnBarMinMax) || (source == valPanel.mnPieMinMax)) {
                        summaryType = MultipleBarClusterSummaryPlot.BCSP_MINMAX;
                    }

                    if (valPanel.lastSummaryBtn == valPanel.clusterSummary) {
                        crtPlot = new MultipleBarClusterSummaryPlot(thisReference, selectedNodes, predicates, clusters,
                                summaryType, pieOrBar, currentUnits);
                    } else if (valPanel.lastSummaryBtn == valPanel.nodeSummary) {
                        crtPlot = new MultipleBarNodeSummaryPlot(thisReference, selectedNodes, predicates, clusters,
                                summaryType, pieOrBar, currentUnits);
                    } else {
                        logger.log(Level.WARNING, "event from unknown source " + source);
                    }
                } else {
                    helpClusterSelection();
                }
            }
            if ((predicates == null) || (predicates.size() == 0)) {
                return;
            }
            if (crtPlot != null) {
                crtPlot.setFarmName(formFarmName());
                if (uniqueFarm()) {
                    String farmName = ((monPredicate) predicates.get(0)).Farm;
                    for (Object element : serMonitorBase.getVNodes()) {
                        rcNode node = (rcNode) element;
                        if (node.client.farm.toString().equals(farmName)) {
                            crtPlot.setCountryCode(node.mlentry.Country);
                            crtPlot.setLocalTime(node.client.localTime);
                            break;
                        }
                    }
                    //				crtPlot.setCountryCode(countryCode);
                    //				crtPlot.setLocalTime(localTime);
                }
                rcNode[] farms = groupPanel.getSelectedFarmNodes();
                if (farms != null) {
                    for (int k = 0; k < farms.length; k++) {
                        if (farms[k] == null) {
                            continue;
                        }
                        Vector v = null;
                        if (!winList.containsKey(farms[k])) {
                            v = new Vector();
                            winList.put(farms[k], v);
                        } else {
                            v = (Vector) winList.get(farms[k]);
                        }
                        v.add(crtPlot);
                    }
                }
            }
        }

        private String formFarmName() {

            Vector v = new Vector();
            StringBuilder ret = new StringBuilder();
            for (Iterator it = predicates.iterator(); it.hasNext();) {
                monPredicate pred = (monPredicate) it.next();
                if (!v.contains(pred.Farm)) {
                    v.add(pred.Farm);
                    ret.append(pred.Farm).append("-");
                }
            }
            if (ret.length() == 0) {
                return "unavailable";
            }
            return ret.toString().substring(0, ret.length() - 1);
        }

        private boolean uniqueFarm() {

            String farmName = ((monPredicate) predicates.get(0)).Farm;
            for (Iterator it = predicates.iterator(); it.hasNext();) {
                monPredicate pred = (monPredicate) it.next();
                if (!pred.Farm.equals(farmName)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** the parent farm is dead, so stop all plotting windows */
    public void stopIt(rcNode node) {

        if ((winList != null) && winList.containsKey(node)) {
            Vector v = (Vector) winList.get(node);
            for (int i = 0; i < v.size(); i++) {
                MultipleDataPlotter win = (MultipleDataPlotter) v.get(i);
                win.stopIt(node);
                v.remove(i);
                i--;
            }
            winList.remove(node);
        }
    }

}
