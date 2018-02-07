package lia.Monitor.Plot;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TimeZone;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.WindowConstants;

import lia.Monitor.GUIs.DataPlotterParent;
import lia.Monitor.GUIs.Unit;
import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.StringResultPanel;
import lia.Monitor.JiniClient.CommonGUI.TimeUtil;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.LocalDataFarmProvider;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.MLSerClient;
import lia.util.ntp.NTPDate;
import plot.NewDateChart;
import plot.NewXYLineChart;
import plot.newSimPlot;

public class MonDataPlotClus implements LocalDataFarmClient, DataPlotter, ActionListener, ComponentListener,
        WindowListener, TimeSliderAdjustmentListener {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(MonDataPlotClus.class.getName());

    DataPlotterParent parent;
    LocalDataFarmProvider dataprovider;
    newSimPlot p;
    JMenuItem stringResultsItems;
    JMenuItem mPeriod; // menuitem shown when user right-clicks the plot
    JMenuItem mnPeriod;
    JMenuItem mTime;
    boolean localTimeZone = false;

    private long timeOfLastResult;
    private long timeOfFirstResult;
    boolean continuous = true;
    String timeZone;
    String localTime;
    Vector predicates;
    Vector clusters;
    String title = "";
    HashSet nodes; // nodes discovered issuing the given list of predicates
    boolean addClusterName; // if we should add the cluster name to the node name
    boolean receivedNewData;
    boolean queryHasResults;
    int receivingData = 0; // used to avoid starting redraw while still receiving data
    boolean closed = false;
    TimerTask ttask;
    Thread tthread;
    int resultsCount = 0;
    int results = 0;

    final Object lock = new Object();
    Vector notProcessed = new Vector();
    TimeUtil timeUtil = new TimeUtil();

    // cipsm - this frame is used to represent the string values obtained as result
    StringResultPanel stringResultPanel = null;
    JFrame stringResultFrame = null;
    Component plotPanel = null;
    boolean plotResult = false;
    JMenuBar menubar = null;
    IntervalSelector timeSelector;

    HashMap currentUnit = null;
    Unit baseUnit = null;

    long minimumPredicateTime = 0l;
    long maximumPredicateTime = 1l;

    public MonDataPlotClus(DataPlotterParent parent, LocalDataFarmProvider dataprovider, Vector predicates,
            Vector clusters) {
        this.dataprovider = dataprovider;
        this.parent = parent;
        this.predicates = predicates;
        this.clusters = clusters;
        nodes = new HashSet();

        // we should have a single parmeter - set it as the title of this chart
        String[] params = ((monPredicate) predicates.get(0)).parameters;
        title = params[0];
        if (params.length > 1) {
            logger.log(Level.WARNING, "More that one parameter in MonDataPlotClus!");
        }
        queryHasResults = false;
        // register as a listener for these predicates
        for (Iterator pit = predicates.iterator(); pit.hasNext();) {
            monPredicate pred = (monPredicate) pit.next();
            dataprovider.addLocalClient(this, pred);
            resultsCount++;
        }
        addClusterName = clusters.size() > 1;

        p = new newSimPlot("Cluster History Plot", title, "Service Local Time", " ", true);
        p.setTimeZone(TimeZone.getDefault().getID());

        p.ginit();
        p.showMe(true);
        p.setTimeout(60 * 1000);

        long inter = -1l;
        for (Iterator pit = predicates.iterator(); pit.hasNext();) {
            monPredicate pred = (monPredicate) pit.next();
            minimumPredicateTime = Math.min(minimumPredicateTime, pred.tmin);
            if (pred.tmax < 0) {
                maximumPredicateTime = pred.tmax;
            }
            long d = -1 * pred.tmin;
            if (d > inter) {
                inter = d;
            }
        }
        ((NewXYLineChart) p.chartFrame).setDataInterval(inter);

        JMenu menu = p.chartFrame.getViewMenu();
        mTime = new JMenuItem("Modify timezone");
        mTime.addActionListener(this);
        menu.addSeparator();
        menu.add(mTime);
        mPeriod = new JMenuItem("Plot interval");
        mPeriod.addActionListener(this);
        menu.addSeparator();
        menu.add(mPeriod);

        mnPeriod = new JMenuItem("Show plot interval selector");
        mnPeriod.addActionListener(this);
        menu.add(mnPeriod);

        timeOfFirstResult = Long.MAX_VALUE;
        ttask = new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName(" ( ML ) - Plot - MonDataPlotClus Timer Thread");
                update();
            }
        };
        tthread = BackgroundWorker.controlledSchedule(ttask, 4000, 4000);

        p.chartFrame.addComponentListener(this);
        p.chartFrame.addWindowListener(this);
        ((NewXYLineChart) p.chartFrame).startProgressBar(true);
        ((NewXYLineChart) p.chartFrame).setDimension();
    }

    public void setCurrentUnit(HashMap unit) {
        if ((unit != null) && (unit.size() != 0) && (p != null)) {
            baseUnit = (Unit) unit.get(title);
            if (baseUnit == null) {
                return;
            }
            ((NewXYLineChart) p.chartFrame).setYAxisLabel(baseUnit.toString());
            currentUnit = unit;
        }
    }

    public void update() {

        synchronized (lock) {
            try {
                if ((receivingData == 0) && receivedNewData) {
                    receivedNewData = false;
                    ((NewXYLineChart) p.chartFrame).stopProgressBar();
                    p.rupdate();
                }
            } catch (Throwable t) {
                t.printStackTrace();
                logger.log(Level.WARNING, "Error executing", t);
            }
        }
    }

    public boolean testAlive() {

        if (p == null) {
            return false;
        }
        if (p.isVisible()) {
            return true;
        }
        //        if(!closed){
        //        	dataprovider.deleteLocalClient(this);
        //        	((XYLineChart)p.chartFrame).stopProgressBar();
        //        	parent.stopPlot(this);
        //        }
        return false;
    }

    private void formStringResultFrame() {
        stringResultFrame = new JFrame("History string results");
        stringResultFrame.getContentPane().setLayout(new BorderLayout());
        stringResultFrame.getContentPane().add(stringResultPanel, BorderLayout.CENTER);
        stringResultFrame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        stringResultFrame.setSize(new Dimension(500, 500));
        stringResultsItems = new JMenuItem("Show string results");
        JMenu menu = p.getChartFrame().getViewMenu();
        stringResultsItems.addActionListener(this);
        menu.addSeparator();
        menu.add(stringResultsItems);
        JOptionPane.showMessageDialog(p.getChartFrame(),
                "There is string data available also... please see View->Show string results menu item.");
    }

    @Override
    public synchronized void newFarmResult(MLSerClient client, Object ro) {

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
                ((NewXYLineChart) p.chartFrame).stopProgressBar();
                queryHasResults = true;
                if (timeSelector != null) {
                    long current = NTPDate.currentTimeMillis();
                    timeSelector.setRange(current - (2 * 60 * 60 * 1000), current, current);
                }
                JOptionPane.showMessageDialog(p.getChartFrame(), "There is no data available for your request!\n"
                        + "Please use 'Plot interval' from 'View' menu\n" + "to select other interval.");
            }
            return;
        }
        if (ro == null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINE, "Got null result");
            }
            return; // some of the farms don't have this result...
        }
        queryHasResults = true;
        if (ro instanceof Result) {
            if ((stringResultPanel != null) && (plotPanel != null)) { // already added the string results
                p.getChartFrame().getContentPane().removeAll();
                p.getChartFrame().getContentPane().setLayout(new BorderLayout());
                p.getChartFrame().getContentPane().add(plotPanel, BorderLayout.CENTER);
                p.getChartFrame().setJMenuBar(menubar);
                p.getChartFrame().validate();
                p.getChartFrame().repaint();
                plotPanel = null;
                formStringResultFrame();
            }

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
            //	System.out.println("Got eResult " + ro);
            eResult r = (eResult) ro;
            boolean ret = false;
            if ((r.param != null) && (r.param_name != null)) {
                for (int k = 0; (k < r.param.length) && (k < r.param_name.length); k++) {
                    if (r.param[k] instanceof String) {
                        if (stringResultPanel == null) {
                            stringResultPanel = new StringResultPanel();
                            if (!plotResult) { // directly into the bco
                                plotPanel = p.getChartFrame().getContentPane().getComponent(0);
                                stringResultPanel.setPreferredSize(plotPanel.getSize());
                                menubar = p.getChartFrame().getJMenuBar();
                                p.getChartFrame().setJMenuBar(null);
                                p.getChartFrame().getContentPane().removeAll();
                                p.getChartFrame().getContentPane().setLayout(new BorderLayout());
                                p.getChartFrame().getContentPane().add(stringResultPanel, BorderLayout.CENTER);
                                p.getChartFrame().validate();
                                p.getChartFrame().repaint();
                            } else {
                                formStringResultFrame();
                            }
                        }
                        timeUtil.setTimeZone(timeZone);
                        stringResultPanel.addStringResult(timeUtil.getTime(r.time), r.FarmName, r.ClusterName,
                                r.NodeName, r.Module, r.param_name[k], (String) r.param[k], false);
                        receivedNewData = true;
                        ret = true;
                    }
                }
                if (ret && (results < 5000)) {
                    synchronized (lock) {
                        results++;
                        if ((results % 30) == 0) {
                            update();
                        }
                    }
                }
            }

        } else if (ro instanceof Vector) {
            Vector vr = (Vector) ro;
            if (vr.size() == 0) {
                return;
            }
            for (int i = 0; i < vr.size(); i++) {
                newFarmResult(client, vr.get(i));
            }
        } else {
            logger.log(Level.WARNING, " Wrong Result type in MonPlot ! ", new Object[] { ro });
            return;
        }
    }

    boolean plotResult(Result r) {

        if ((p == null) || (p.chartFrame == null)) {
            return false;
        }
        synchronized (p.chartFrame.getTreeLock()) {
            if ((r == null) || (r.param == null) || (r.param_name == null)) {
                return false;
            }
            if ((r.time < minimumPredicateTime) || ((maximumPredicateTime > 0) && (r.time > maximumPredicateTime))) {
                return false;
            }

            boolean ret = false;
            try {
                double tdis = r.time;
                timeOfLastResult = Math.max(timeOfLastResult, r.time);
                timeOfFirstResult = Math.min(timeOfFirstResult, r.time);
                if (timeSelector != null) {
                    timeSelector.setRange(timeOfFirstResult, timeOfLastResult, NTPDate.currentTimeMillis());
                }
                String key = (addClusterName ? r.ClusterName + "/" : "") + r.NodeName.trim();
                if (!nodes.contains(key)) {
                    p.addSet(key);
                    nodes.add(key);
                }
                if (!Double.isNaN(r.param[0]) && !Double.isInfinite(r.param[0])) {
                    if ((currentUnit != null) && (baseUnit != null) && currentUnit.containsKey(r.param_name[0])) {
                        Unit u = (Unit) currentUnit.get(r.param_name[0]);
                        p.add(key, tdis, convert(u, r.param[0]));
                    } else {
                        p.add(key, tdis, r.param[0]);
                    }
                    receivedNewData = true;
                    ret = true;
                } else {
                    logger.warning("Got a NaN result");
                }
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINE, t.getLocalizedMessage());
                }
            }
            return ret;
        }
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

    /** called from RCMonPanel to set the local time */
    @Override
    public void setLocalTime(String dd) {
        if (dd != null) {
            try {
                localTime = dd.substring(1, 6);
                dd = dd.substring(1 + dd.indexOf("("), dd.indexOf(")"));
                timeZone = MonDataPlot.adjustTimezone(dd);
                timeUtil.setTimeZone(timeZone);
                if (!localTimeZone) {
                    p.setTimeZone(timeZone);
                    if (timeSelector != null) {
                        timeSelector.setTimeZone(timeZone);
                    }
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Invalid local time");
            }
        }
    }

    /** called to add farm name on the title bar */
    @Override
    public void setFarmName(String farmName) {
        p.chartFrame.setTitle(farmName + ": " + title);
    }

    /** called to add the country flag on the title bar */
    @Override
    public void setCountryCode(String cc) {
        p.chartFrame.setCountryCode(cc);
    }

    /** called when this chart should be disposed */
    @Override
    public void stopIt() {
        if (!closed) {
            if ((p != null) && (p.chartFrame != null)) {
                p.chartFrame.removeComponentListener(this);
                p.chartFrame.removeWindowListener(this);
            }
            if (mPeriod != null) {
                mPeriod.removeActionListener(this);
            }
            dataprovider.deleteLocalClient(this);
            if (p != null) {
                p.dispose();
                p = null;
            }
            if (tthread != null) {
                BackgroundWorker.cancel(tthread);
                tthread = null;
            }
            closed = true;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        if (e.getSource().equals(mTime)) {
            if (localTimeZone) {
                if (timeZone != null) {
                    p.setTimeZone(timeZone);
                    if (timeSelector != null) {
                        timeSelector.setTimeZone(timeZone);
                    }
                    ((NewDateChart) p.chartFrame).chart.getXYPlot().getDomainAxis().setLabel("Service Local Time");
                    localTimeZone = false;
                    p.rupdate();
                }
            } else {
                p.setTimeZone(TimeZone.getDefault().getID());
                if (timeSelector != null) {
                    timeSelector.setTimeZone(TimeZone.getDefault().getID());
                }
                ((NewDateChart) p.chartFrame).chart.getXYPlot().getDomainAxis().setLabel("Local Time");
                localTimeZone = true;
                p.rupdate();
            }
            return;
        }

        if (e.getSource().equals(stringResultsItems)) {
            if (!stringResultFrame.isShowing()) {
                stringResultFrame.setVisible(true);
            } else {
                stringResultFrame.toFront();
            }
            return;
        }

        if (e.getSource().equals(mnPeriod)) {
            if (timeSelector == null) {
                timeSelector = new IntervalSelector(timeOfFirstResult, timeOfLastResult, NTPDate.currentTimeMillis());
                if (localTimeZone) {
                    timeSelector.setTimeZone(TimeZone.getDefault().getID());
                } else {
                    timeSelector.setTimeZone(timeZone);
                }
                timeSelector.addAdjustmentListener(this);
            } else {
                timeSelector.setRange(timeOfFirstResult, timeOfLastResult, NTPDate.currentTimeMillis());
            }
            if (mnPeriod.getText().equals("Show plot interval selector")) {
                p.chartFrame.getContentPane().add(timeSelector, BorderLayout.NORTH);
                p.chartFrame.pack();
                p.chartFrame.repaint();
                mnPeriod.setText("Hide plot interval selector");
            } else {
                p.chartFrame.getContentPane().remove(timeSelector);
                p.chartFrame.pack();
                p.chartFrame.repaint();
                mnPeriod.setText("Show plot interval selector");
            }
            return;
        }

        if (e.getSource().equals(mPeriod)) {
            PlotIntervalSelector is = new PlotIntervalSelector(p.getChartFrame(), timeOfFirstResult, (continuous ? -1
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
                dataprovider.deleteLocalClient(this);
                if (timeOfFirstResult < timeOfLastResult) {
                    p.clearAll();
                }
                if (continuous) {
                    ((NewXYLineChart) p.chartFrame).setDataInterval(length);
                } else {
                    ((NewXYLineChart) p.chartFrame).setDataInterval(-1l);
                }
                ((NewXYLineChart) p.chartFrame).stopProgressBar();
                ((NewXYLineChart) p.chartFrame).startProgressBar(true);
                p.rupdate();
                timeOfFirstResult = Long.MAX_VALUE;
                timeOfLastResult = Long.MIN_VALUE;
                if (continuous) {
                    start = -length;
                    logger.log(Level.INFO, "Registering for data on last " + (start / 1000 / 60) + " minutes");
                } else {
                    logger.log(Level.INFO, "Registering for data from =" + new Date(start) + " to=" + new Date(end));
                }
                queryHasResults = false;
                for (Iterator pit = predicates.iterator(); pit.hasNext();) {
                    monPredicate pred = (monPredicate) pit.next();
                    pred.tmin = start;
                    pred.tmax = end;
                    minimumPredicateTime = Math.min(minimumPredicateTime, pred.tmin);
                    if (pred.tmax < 0) {
                        maximumPredicateTime = pred.tmax;
                    } else if (maximumPredicateTime >= 0) {
                        maximumPredicateTime = Math.max(maximumPredicateTime, pred.tmax);
                    }
                    dataprovider.addLocalClient(this, pred);
                    resultsCount++;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                logger.log(Level.WARNING, "Error requesting data");
            }
        }
    }

    @Override
    public void componentHidden(ComponentEvent e) {
        //        testAlive();
    }

    @Override
    public void componentMoved(ComponentEvent e) {
    }

    @Override
    public void componentResized(ComponentEvent e) {
        try {
            ((NewXYLineChart) p.chartFrame).setDimension();
        } catch (Exception ex) {
            //			ex.printStackTrace();
            // ignore it
        }
    }

    @Override
    public void componentShown(ComponentEvent e) {
    }

    @Override
    public void windowActivated(WindowEvent e) {
    }

    @Override
    public void windowClosed(WindowEvent e) {
        if (p == null) {
            return;
        }
        if (!closed) {
            dataprovider.deleteLocalClient(this);
            ((NewXYLineChart) p.chartFrame).stopProgressBar();
            parent.stopPlot(this);
        }
    }

    @Override
    public void windowClosing(WindowEvent e) {
    }

    @Override
    public void windowDeactivated(WindowEvent e) {
    }

    @Override
    public void windowDeiconified(WindowEvent e) {
    }

    @Override
    public void windowIconified(WindowEvent e) {
    }

    @Override
    public void windowOpened(WindowEvent e) {
    }

    @Override
    public void adjustmentValueChanged() {
        if (timeSelector == null) {
            return;
        }
        long start = timeSelector.getMinRange();
        long end = timeSelector.getMaxRange();
        long length = end - start;
        continuous = timeSelector.isContinuous();
        try {
            dataprovider.deleteLocalClient(this);
            if (timeOfFirstResult < timeOfLastResult) {
                p.clearAll();
            }
            ((NewXYLineChart) p.chartFrame).stopProgressBar();
            ((NewXYLineChart) p.chartFrame).startProgressBar(true);
            if (continuous) {
                ((NewXYLineChart) p.chartFrame).setDataInterval(length);
            } else {
                ((NewXYLineChart) p.chartFrame).setDataInterval(-1l);
            }
            p.rupdate();
            timeOfFirstResult = Long.MAX_VALUE;
            timeOfLastResult = Long.MIN_VALUE;
            if (continuous) {
                start = -length;
                logger.log(Level.INFO, "Registering for data on last " + (start / 1000 / 60) + " minutes");
            } else {
                logger.log(Level.INFO, "Registering for data from =" + new Date(start) + " to=" + new Date(end));
            }
            queryHasResults = false;
            for (Iterator pit = predicates.iterator(); pit.hasNext();) {
                monPredicate pred = (monPredicate) pit.next();
                pred.tmin = start;
                pred.tmax = end;
                minimumPredicateTime = Math.min(minimumPredicateTime, pred.tmin);
                if (pred.tmax < 0) {
                    maximumPredicateTime = pred.tmax;
                } else if (maximumPredicateTime >= 0) {
                    maximumPredicateTime = Math.max(maximumPredicateTime, pred.tmax);
                }
                dataprovider.addLocalClient(this, pred);
                resultsCount++;
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error requesting data");
        }
    }
}
