package lia.Monitor.JiniClient.CommonGUI.Groups.Plot;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import lia.Monitor.GUIs.Unit;
import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.LocalDataFarmProvider;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.MLSerClient;
import plot.ClusterBarChart;

/**
 * uses GlobalClusterBarChart from plot.jar to plot summary info about a
 * cluster
 */
public class MultipleBarNodeSummaryPlot implements LocalDataFarmClient, MultipleDataPlotter, ComponentListener,
        WindowListener, ActionListener {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(MultipleBarNodeSummaryPlot.class.getName());

    /** summary type */
    public static final int BCSP_AVERAGE = 0;
    public static final int BCSP_SUM = 1;
    public static final int BCSP_INTEGRAL = 2;
    public static final int BCSP_MINMAX = 3;

    DataPlotterParent parent;
    Vector dataproviders;
    rcNode[] selectedNodes;
    ClusterBarChart gcbc;
    Vector predicates;
    boolean addClusterName;
    String[] parameters;
    String title;

    JMenuItem mPeriod;
    long timeOfLastResult;
    long timeOfFirstResult;
    boolean receivedNewData = false;
    boolean queryHasResults = false;
    int receivingData = 0;
    boolean continuous = true;
    String timeZone;
    String localTime;

    TimerTask ttask;
    Thread tthread;
    boolean closed = false;
    int resultsCount = 0;
    int results = 0;
    boolean multipleFarms = false;

    final Object lock = new Object();
    Vector notProcessed = new Vector();

    HashMap currentUnit = null;
    Unit baseUnit = null;

    public MultipleBarNodeSummaryPlot(DataPlotterParent parent, rcNode[] selectedNodes, Vector predicates,
            Vector clusters, int summaryType, boolean pieOrBar, HashMap yAxisUnit) {

        this.selectedNodes = selectedNodes;
        this.parent = parent;
        this.predicates = predicates;
        title = (String) clusters.get(0);
        for (int i = 1; i < clusters.size(); i++) {
            title += ", " + (String) clusters.get(i);
        }
        addClusterName = clusters.size() > 1;
        // setup parameters = labels on the right side of the chart
        parameters = ((monPredicate) predicates.get(0)).parameters;

        if ((parameters == null) || (parameters.length == 0)) {
            return;
        }

        if ((yAxisUnit != null) && (yAxisUnit.size() != 0)) {
            baseUnit = (Unit) yAxisUnit.get(parameters[0]);
            currentUnit = yAxisUnit;
        }

        gcbc = new ClusterBarChart(title, false, false, summaryType, pieOrBar, (baseUnit != null) ? baseUnit.toString()
                : null);
        gcbc.changeDepth(3, 4);
        gcbc.setTimeoutTime(60 * 1000);

        JMenu menu = gcbc.getViewMenu();
        mPeriod = new JMenuItem("Summary interval");
        mPeriod.addActionListener(this);
        menu.addSeparator();
        menu.add(mPeriod);

        timeOfFirstResult = Long.MAX_VALUE;

        gcbc.showMe(true);
        gcbc.rupdate();

        ttask = new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName(" ( ML ) - GroupsPlot - MultipleBarNodeSummaryPlot Timer Thread");
                update();
            }
        };
        queryHasResults = false;
        dataproviders = new Vector();
        // register as a listener for these predicates
        for (Iterator pit = predicates.iterator(); pit.hasNext();) {
            monPredicate pred = (monPredicate) pit.next();
            sendPredicate(pred);
        }
        multipleFarms = (dataproviders.size() > 1);
        tthread = BackgroundWorker.controlledSchedule(ttask, 4000, 4000);
        gcbc.chartPanel.addComponentListener(this);
        gcbc.addWindowListener(this);
        gcbc.startProgressBar(true);
    }

    public void update() {

        synchronized (lock) {
            try {
                if ((receivingData == 0) && receivedNewData) {
                    receivedNewData = false;
                    int gFactor = gcbc.getNumberOfNodes();
                    gFactor = gFactor * gFactor;
                    gcbc.setLowerMargin(0.5 / (1.0 + gFactor));
                    gcbc.setUpperMargin(0.5 / (1.0 + gFactor));
                    gcbc.setIntervalString(timeOfFirstResult, timeOfLastResult);
                    gcbc.stopProgressBar();
                    gcbc.rupdate();
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error executing", t);
            }
        }
    }

    protected void addDataProvider(LocalDataFarmProvider provider) {

        boolean found = false;
        for (Iterator it = dataproviders.iterator(); it.hasNext();) {
            LocalDataFarmProvider p = (LocalDataFarmProvider) it.next();
            if (p.equals(provider)) {
                found = true;
                break;
            }
        }
        if (!found) {
            dataproviders.add(provider);
        }
    }

    protected void sendPredicate(monPredicate predicate) {

        if (selectedNodes != null) {
            for (rcNode selectedNode : selectedNodes) {
                if (selectedNode.client.farm.toString().equals(predicate.Farm)) {
                    selectedNode.client.addLocalClient(this, predicate);
                    addDataProvider(selectedNode.client);
                    resultsCount++;
                }
            }
        }
    }

    boolean plotResult(Result r) {

        if ((r.param_name == null) || (r.param == null)) {
            return false;
        }

        boolean ret = false;
        try {
            timeOfLastResult = Math.max(timeOfLastResult, r.time);
            timeOfFirstResult = Math.min(timeOfFirstResult, r.time);
            String nn = null;
            if (multipleFarms) {
                nn = (addClusterName ? r.FarmName + "/" + r.ClusterName + "/" : r.FarmName + "/") + r.NodeName;
            } else {
                nn = (addClusterName ? r.ClusterName + "/" : "") + r.NodeName;
            }
            for (String parameter : parameters) {
                for (int j = 0; j < r.param.length; j++) {
                    if (parameter.equals(r.param_name[j])) {
                        if (!Double.isNaN(r.param[j]) && !Double.isInfinite(r.param[j])) {
                            if ((currentUnit != null) && (baseUnit != null) && currentUnit.containsKey(r.param_name[j])) {
                                Unit u = (Unit) currentUnit.get(r.param_name[j]);
                                gcbc.addPoint(nn, r.param_name[j], r.time, convert(u, r.param[j]));
                            } else {
                                gcbc.addPoint(nn, r.param_name[j], r.time, r.param[j]);
                            }
                            receivedNewData = true;
                            ret = true;
                        } else {
                            logger.warning("Got a NaN result");
                        }
                    }
                }
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINE, t.getLocalizedMessage());
            }
        }
        return ret;
    }

    private double convert(Unit u, double val) {
        if (u == null) {
            return val;
        }
        long diffTimeMultiplicator = 1l;
        if ((baseUnit.lTimeMultiplier != 0l) && (u.lTimeMultiplier != 0l)) {
            diffTimeMultiplicator = baseUnit.lTimeMultiplier / u.lTimeMultiplier;
        }
        if (diffTimeMultiplicator == 0l) {
            diffTimeMultiplicator = 1l;
        }
        long diffUnitMultiplicator = 1l;
        if ((baseUnit.lUnitMultiplier != 0l) && (u.lUnitMultiplier != 0l)) {
            diffUnitMultiplicator = baseUnit.lUnitMultiplier / u.lUnitMultiplier;
        }
        if (diffUnitMultiplicator == 0l) {
            diffUnitMultiplicator = 1l;
        }
        val = (val * diffTimeMultiplicator) / diffUnitMultiplicator;
        return val;
    }

    @Override
    public void newFarmResult(MLSerClient client, Object ro) {
        if (!testAlive()) {
            if (!closed) {
                notProcessed.add(new Object[] { client, ro });
            }
            return;
        }

        while (notProcessed.size() != 0) {
            Object[] o = (Object[]) notProcessed.remove(0);
            newFarmResult((MLSerClient) o[0], o[1]);
        }

        resultsCount--;
        if ((ro == null) && (resultsCount <= 0)) {
            if (!queryHasResults) {
                gcbc.stopProgressBar();
                queryHasResults = true;
                JOptionPane.showMessageDialog(gcbc, "There is no data available for your request!\n"
                        + "Please use 'Summary interval' from 'View' menu\n" + "to select other interval.");
            }
            return;
        }
        if (ro == null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINE, "Got a null result");
            }
            return; // some of the farms don't have this result...
        }
        queryHasResults = true;

        if (ro instanceof Result) {
            Result r = (Result) ro;
            synchronized (lock) {
                receivingData++;
            }
            boolean ret = plotResult(r);
            synchronized (lock) {
                receivingData--;
            }
            if (ret && (results < 300)) {
                synchronized (lock) {
                    results++;
                    if ((results % 30) == 0) {
                        update();
                    }
                }
            }
        } else if (ro instanceof eResult) {
            // 	System.out.println("Got eResult " + ro);
        } else if (ro instanceof Vector) {
            Vector vr = (Vector) ro;
            if (vr.size() == 0) {
                return;
            }
            for (int i = 0; i < vr.size(); i++) {
                newFarmResult(client, vr.get(i));
            }
        } else {
            logger.log(Level.WARNING, " Wrong Result type in MonPlot ! " + ro + " >>>>> Class name: " + ro.getClass(),
                    new Object[] { ro });
            return;
        }
    }

    @Override
    public synchronized boolean stopIt(rcNode node) {
        if (!closed) {
            if ((dataproviders.size() <= 1) || (node == null)) {
                gcbc.removeComponentListener(this);
                gcbc.removeWindowListener(this);
                if ((dataproviders != null) && (dataproviders.size() != 0)) {
                    for (int i = 0; i < dataproviders.size(); i++) {
                        LocalDataFarmProvider prov = (LocalDataFarmProvider) dataproviders.get(i);
                        prov.deleteLocalClient(this);
                    }
                }
                gcbc.dispose();
                if (tthread != null) {
                    BackgroundWorker.cancel(tthread);
                    tthread = null;
                }
                closed = true;
                return true;
            }
            dataproviders.remove(node.client);
            node.client.deleteLocalClient(this);
            try {
                gcbc.removeNode(node.client.farm.getName() + "/");
            } catch (Exception e) {
                logger.warning(e.getLocalizedMessage());
            }
            return false;
        }
        return true;
    }

    /**
     * this is not the local time! Instead, it is the interval for which the
     * data displayed is summarized
     */
    @Override
    public void setLocalTime(String dd) {
        if (dd != null) {
            try {
                localTime = dd.substring(1, 6);
                String dd1 = dd.substring(1 + dd.indexOf("("), dd.indexOf(")"));
                gcbc.setTimeZone(timeZone = MultipleMonDataPlot.adjustTimezone(dd1));
            } catch (Exception ex) {
                System.out.println("Error parsing LocalTime:." + dd + ".");
                ex.printStackTrace();
            }
        }
    }

    /** called to add farm name on the title bar */
    @Override
    public void setFarmName(String farmName) {
        gcbc.setTitle(farmName + ": " + title);
    }

    /** called to add the country flag on the title bar */
    @Override
    public void setCountryCode(String cc) {
        gcbc.setCountryCode(cc);
    }

    public synchronized boolean testAlive() {

        if (gcbc == null) {
            return false;
        }
        if (gcbc.isVisible()) {
            return true;
        }
        //		if (!closed) {
        //			for (Iterator it = dataproviders.iterator(); it.hasNext(); ) {
        //				LocalDataFarmProvider provider = (LocalDataFarmProvider)it.next();
        //				provider.deleteLocalClient(this);
        //			}
        //			parent.stopPlot(this);
        //		}
        return false;
    }

    @Override
    public void componentHidden(ComponentEvent e) {
    }

    @Override
    public void componentMoved(ComponentEvent e) {
        // empty
    }

    @Override
    public void componentResized(ComponentEvent e) {

        try {
            Dimension d = gcbc.chartPanel.getSize();
            gcbc.setDimension(d);
        } catch (Exception ex) {
        }
    }

    @Override
    public void componentShown(ComponentEvent e) {
        // empty
    }

    @Override
    public void windowActivated(WindowEvent e) {
        // empty
    }

    @Override
    public void windowClosed(WindowEvent e) {
        if (gcbc == null) {
            return;
        }
        if (!closed) {
            for (Iterator it = dataproviders.iterator(); it.hasNext();) {
                LocalDataFarmProvider provider = (LocalDataFarmProvider) it.next();
                provider.deleteLocalClient(this);
            }
            parent.stopPlot(this);
        }
    }

    @Override
    public void windowClosing(WindowEvent e) {
        // empty
    }

    @Override
    public void windowDeactivated(WindowEvent e) {
        // empty
    }

    @Override
    public void windowDeiconified(WindowEvent e) {
        // empty
    }

    @Override
    public void windowIconified(WindowEvent e) {
        // empty
    }

    @Override
    public void windowOpened(WindowEvent e) {
        // empty
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        if (e.getSource() == mPeriod) {
            PlotIntervalSelector is = new PlotIntervalSelector(gcbc, timeOfFirstResult, (continuous ? -1
                    : timeOfLastResult), localTime, timeZone);
            is.setVisible(true);
            if (!is.closedOK()) {
                is.dispose();
                is = null;
                return;
            }
            long start = is.getStartTime();
            long end = is.getEndTime();
            long length = is.getIntervalLength();
            is.dispose();
            is = null;
            continuous = (end == -1);
            try {
                for (Iterator it = dataproviders.iterator(); it.hasNext();) {
                    LocalDataFarmProvider provider = (LocalDataFarmProvider) it.next();
                    provider.deleteLocalClient(this);
                }
                if (timeOfFirstResult < timeOfLastResult) {
                    gcbc.deleteAllIntervals(timeOfFirstResult, timeOfLastResult);
                }
                gcbc.setIntervalString(-1, -1);
                gcbc.rupdate();
                gcbc.stopProgressBar();
                gcbc.startProgressBar(true);
                gcbc.rupdate();
                timeOfFirstResult = Long.MAX_VALUE;
                timeOfLastResult = Long.MIN_VALUE;
                if (continuous) {
                    start = -length;
                    //                	System.out.println("start = "+start+" end="+end);
                } else {
                    //                	System.out.println("start ="+new Date(start)+" end="+new
                    // Date(end));
                }
                queryHasResults = false;
                dataproviders = new Vector();
                // register as a listener for these predicates
                for (Iterator pit = predicates.iterator(); pit.hasNext();) {
                    monPredicate pred = (monPredicate) pit.next();
                    pred.tmin = start;
                    pred.tmax = end;
                    sendPredicate(pred);
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error requesting data");
            }
        }
    }
}
