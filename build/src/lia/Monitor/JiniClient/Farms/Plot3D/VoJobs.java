package lia.Monitor.JiniClient.Farms.Plot3D;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.JMoreMenu;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.graphical;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Groups.Plot.PlotIntervalSelector;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.MLSerClient;
import net.jini.core.lookup.ServiceID;
import plot.math.MLSeries;
import plot3d.XYLinePanel;

/** A class to represent in 3d the jobs vs time vs VOs */
public class VoJobs extends JPanel implements graphical, LocalDataFarmClient, ActionListener, ComponentListener {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(VoJobs.class.getName());

    /** The monitor base */
    SerMonitorBase monitor;
    /** The current nodes */
    volatile Map<ServiceID, rcNode> nodes;
    volatile Vector<rcNode> vnodes;
    Vector crtNodes;

    private long timeOfLastResult;
    private long timeOfFirstResult;
    boolean continuous = true;

    JMenuItem mPeriod; // menuitem shown when user
    JMenuItem switchNonZero;

    boolean plotZeroValues = false;

    boolean shouldRedraw = false;

    /* list of jradiobuttonmenuitem objects */
    protected Vector voSelection;
    protected Vector vos;
    protected String currentSelectedVO;
    protected String lastSelectedVO = null;
    protected JMenu voMenu = null;
    protected ButtonGroup group;

    /* current results map(vo, map(farm, MLSeries))*/
    Hashtable results;
    /* series containing non-zero results */
    Hashtable goodSeries;

    /* array of predicates to send.... */
    private final monPredicate preds[];

    /* the 3d panel */
    XYLinePanel panel3d;
    private boolean alreadyAdded = false;

    private long defaultTime = 2 * 60 * 60 * 1000;

    public VoJobs() {
        super();
        timeOfFirstResult = Long.MAX_VALUE;
        timeOfLastResult = Long.MIN_VALUE;
        setLayout(new BorderLayout());
        crtNodes = new Vector();
        voSelection = new Vector();
        vos = new Vector();
        group = new ButtonGroup();
        results = new Hashtable();
        goodSeries = new Hashtable();
        // initialize only once the predicates...
        preds = new monPredicate[2];
        preds[0] = new monPredicate("*", "VO_JOBS", "*", -defaultTime, -1, new String[] { "Total Jobs" }, null);
        preds[1] = new monPredicate("*", "%VO_JOBS", "*", -defaultTime, -1, new String[] { "TotalJobs" }, null);
        panel3d = new XYLinePanel(null, "Local time", "Farms", "Total Jobs");
        panel3d.setDataInterval(defaultTime);
        addComponentListener(this);
        startTimer();
    }

    private void startTimer() {
        TimerTask ttask = new TimerTask() {
            @Override
            public void run() {
                if (panel3d == null) {
                    return;
                }
                updateNodes();
                if (shouldRedraw && isVisible()) {
                    try {
                        shouldRedraw = false;
                        redraw();
                    } catch (Throwable ex) {
                        ex.printStackTrace();
                        logger.log(Level.WARNING, "Error executing ", ex);
                    }
                }
            }
        };
        BackgroundWorker.schedule(ttask, 2000, 6000);
    }

    protected void redraw() {

        if (panel3d == null) {
            return;
        }
        if ((lastSelectedVO == null) || (currentSelectedVO == null) || !lastSelectedVO.equals(currentSelectedVO)) {
            panel3d.clear();
            goodSeries.clear();
            if (currentSelectedVO == null) {
                return;
            }
            Hashtable h = (Hashtable) results.get(currentSelectedVO);
            if (h == null) {
                return;
            }
            for (Enumeration en = h.keys(); en.hasMoreElements();) {
                Object o = en.nextElement();
                MLSeries ser = (MLSeries) h.get(o);
                if (ser == null) {
                    continue;
                }
                if (!ser.hasNonZeroValues()) {
                    goodSeries.put(o, Boolean.FALSE);
                    if (!plotZeroValues) {
                        continue;
                    }
                }
                goodSeries.put(o, Boolean.TRUE);
                TreeMap data = ser.data();
                for (Iterator it = data.keySet().iterator(); it.hasNext();) {
                    Number n = (Number) it.next();
                    if (n == null) {
                        continue;
                    }
                    Number val = (Number) data.get(n);
                    if (val == null) {
                        continue;
                    }
                    panel3d.addPoint(ser.name(), n.longValue(), val.doubleValue());
                }
            }
            lastSelectedVO = currentSelectedVO;
        }
        panel3d.updateSeries();
    }

    /**
     * registers predicates for new nodes or
     * deletes rezidual nodes
     *
     */
    public void updateNodes() {
        synchronized (getTreeLock()) {
            if (vnodes == null) {
                return;
            }

            // check if there are new nodes
            for (int i = 0; i < vnodes.size(); i++) {
                rcNode n = vnodes.get(i);
                if ((!crtNodes.contains(n))) {
                    // n is new and in group grid3* or ost*; we should register with predicate for VO_ data
                    boolean added = false;
                    for (int k = 0; k < crtNodes.size(); k++) {
                        rcNode on = (rcNode) crtNodes.get(k);
                        if (on.UnitName.compareToIgnoreCase(n.UnitName) >= 0) {
                            crtNodes.add(k, n);
                            added = true;
                            break;
                        }
                    }
                    if (!added) {
                        crtNodes.add(n);
                    }
                    for (monPredicate pred : preds) {
                        n.client.addLocalClient(this, pred);
                    }
                }
            }
            // check if there are removed nodes
            for (int i = 0; i < crtNodes.size(); i++) {
                rcNode n = (rcNode) crtNodes.get(i);
                if (!vnodes.contains(n)) {
                    n.client.deleteLocalClient(this);//unregistres panel to receive data from removed farm
                    crtNodes.remove(i);
                    for (Enumeration en = results.keys(); en.hasMoreElements();) {
                        Object o = en.nextElement();
                        if (o == null) {
                            continue;
                        }
                        Hashtable h = (Hashtable) results.get(o);
                        if (h == null) {
                            continue;
                        }
                        h.remove(n);
                    }
                    goodSeries.remove(n);
                    i--;
                }
            }
            //check to see if new vos are available
            getVOs();
        }
    }

    protected void addVO(String voName) {

        JPopupMenu.setDefaultLightWeightPopupEnabled(false); /* trick to mix swing with jogl */
        vos.add(voName);
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(voName);
        group.add(item);
        if (voSelection.size() == 0) {
            currentSelectedVO = voName;
            lastSelectedVO = currentSelectedVO;
            item.setSelected(true);
        }
        item.addActionListener(this);
        item.setEnabled(false);
        if ((voMenu == null) && (panel3d != null)) {
            JMenuBar menubar = panel3d.getMenuBar();
            JMenu menu = null;
            for (int i = 0; i < menubar.getMenuCount(); i++) {
                JMenu m = menubar.getMenu(i);
                if (m.getText().equals("View")) {
                    menu = m;
                    break;
                }
            }
            if (menu == null) {
                menu = new JMenu("View");
                menubar.add(menu);
            }
            mPeriod = new JMenuItem("Plot interval");
            mPeriod.addActionListener(this);
            menu.addSeparator();
            menu.add(mPeriod);
            switchNonZero = new JMenuItem("Show all values");
            switchNonZero.addActionListener(this);
            menu.add(switchNonZero);
            voMenu = new JMoreMenu("Selected VO");
            menubar.add(voMenu);
            for (int i = 0; i < voSelection.size(); i++) {
                voMenu.add((JRadioButtonMenuItem) voSelection.get(i));
                shouldRedraw = true;
            }
            panel3d.setTitle("TotalJobs for " + currentSelectedVO);
        }
        if (voMenu != null) {
            voMenu.add(item);
        }
        voSelection.add(item);
        results.put(voName, new Hashtable());
    }

    protected void removeVO(String voName) {

        if (vos.contains(voName)) {
            int pos = vos.indexOf(voName);
            vos.remove(voName);
            JRadioButtonMenuItem item = (JRadioButtonMenuItem) voSelection.remove(pos);
            if (voMenu != null) {
                voMenu.remove(item);
            }
            if (item.isSelected()) {
                if (vos.size() == 0) {
                    currentSelectedVO = null;
                    redraw();
                } else {
                    JRadioButtonMenuItem it = (JRadioButtonMenuItem) voSelection.get(0);
                    it.setSelected(true);
                    currentSelectedVO = it.getText();
                    redraw();
                }
                shouldRedraw = true;
            }
            results.remove(voName);
        }
    }

    protected void getVOs() {
        //here will be put name of vo and a flag to indicate that this vo still gives results
        ArrayList hVoStillActive = new ArrayList();
        //any vo that is not active will be removed
        try {
            for (Iterator it = crtNodes.iterator(); it.hasNext();) {
                rcNode node = (rcNode) it.next();
                Vector v = null;
                if ((node == null) || (node.client == null) || (node.client.farm == null)
                        || (node.client.farm.getClusters() == null)) {
                    continue;
                }
                Vector clusters = node.client.farm.getClusters();
                for (int i = 0; i < clusters.size(); i++) {
                    MCluster cluster = (MCluster) clusters.get(i);
                    if (cluster.name.endsWith("VO_JOBS")) {
                        v = cluster.getNodes();
                        for (Iterator it2 = v.iterator(); it2.hasNext();) {
                            String vo = ((MNode) it2.next()).toString().toUpperCase();
                            // add the new vo
                            if (!vos.contains(vo)) {
                                addVO(vo);
                            }
                            if (!hVoStillActive.contains(vo)) {
                                hVoStillActive.add(vo);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        ;
        try {
            for (int i = 0; i < vos.size(); i++) {
                String vo = (String) vos.get(i);
                if (!hVoStillActive.contains(vo)) {
                    //this vo is no more active, so remove it
                    removeVO(vo);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        ;
    }

    @Override
    public void updateNode(rcNode node) {
    }

    @Override
    public void gupdate() {
    }

    @Override
    public void setNodes(Map<ServiceID, rcNode> nodes, Vector<rcNode> vnodes) {
        this.nodes = nodes;
        this.vnodes = vnodes;
    }

    @Override
    public void setSerMonitor(SerMonitorBase ms) {
        synchronized (getTreeLock()) {
            this.monitor = ms;
            panel3d.setFrame(ms.main);
            updateNodes();
        }
    }

    @Override
    public void setMaxFlowData(rcNode n, Vector v) {
    }

    @Override
    public void new_global_param(String name) {
    }

    protected void setResult(MLSerClient client, Result r) {

        if ((r == null) || (r.param_name == null) || (r.param == null)) {
            return;
        }

        if (r.ClusterName.endsWith("VO_JOBS")) {
            // the name of the vo is nodeName
            String vo = r.NodeName.toUpperCase();
            if (!vos.contains(vo)) {
                return;
            }
            Hashtable h = null;
            if (!results.containsKey(vo)) {
                h = new Hashtable();
                results.put(vo, h);
            } else {
                h = (Hashtable) results.get(vo);
            }
            rcNode n = nodes.get(client.tClientID);
            if (n == null) {
                return;
            }
            MLSeries ser = null;
            if (!h.containsKey(n)) {
                ser = new MLSeries(r.FarmName);
                h.put(n, ser);
            } else {
                ser = (MLSeries) h.get(n);
            }
            ser.setDataInterval(defaultTime);
            // now add the values
            for (int i = 0; i < r.param_name.length; i++) {
                if ((r.param_name[i] == null) || Double.isInfinite(r.param[i]) || Double.isNaN(r.param[i])) {
                    continue;
                }
                if (r.param_name[i].equals("Total Jobs") || r.param_name[i].equals("TotalJobs")) {
                    ser.add(r.time, r.param[i]);
                    for (int k = 0; k < voSelection.size(); k++) {
                        JRadioButtonMenuItem item = (JRadioButtonMenuItem) voSelection.get(k);
                        if (item.getText().equals(vo)) {
                            item.setEnabled(true);
                            break;
                        }
                    }
                    timeOfLastResult = Math.max(timeOfLastResult, r.time);
                    timeOfFirstResult = Math.min(timeOfFirstResult, r.time);
                    if ((currentSelectedVO != null) && currentSelectedVO.equals(vo) && (panel3d != null)) {
                        if (plotZeroValues) {
                            panel3d.addPoint(r.FarmName, r.time, r.param[i]);
                            shouldRedraw = true;
                        } else {
                            if (ser.hasNonZeroValues()) {
                                if (goodSeries.containsKey(n) && !((Boolean) goodSeries.get(n)).booleanValue()) {
                                    TreeMap data = ser.data();
                                    for (Iterator it = data.keySet().iterator(); it.hasNext();) {
                                        Number nr = (Number) it.next();
                                        if (nr == null) {
                                            continue;
                                        }
                                        Number val = (Number) data.get(nr);
                                        if (val == null) {
                                            continue;
                                        }
                                        panel3d.addPoint(ser.name(), nr.longValue(), val.doubleValue());
                                    }
                                } else {
                                    panel3d.addPoint(r.FarmName, r.time, r.param[i]);
                                }
                                goodSeries.put(n, Boolean.TRUE);
                                shouldRedraw = true;
                            } else {
                                panel3d.removeSeries(ser.name());
                                goodSeries.put(n, Boolean.FALSE);
                                shouldRedraw = true;
                            }
                        }

                    }
                }
            }
        }
    }

    @Override
    public void newFarmResult(MLSerClient client, Object ro) {
        if (ro == null) {
            return;
        }
        if (ro instanceof Result) {
            Result r = (Result) ro;
            setResult(client, r);
        } else if (ro instanceof Vector) {
            Vector vr = (Vector) ro;
            for (int i = 0; i < vr.size(); i++) {
                newFarmResult(client, vr.elementAt(i));
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        Object src = e.getSource();
        if (src.equals(switchNonZero)) {
            plotZeroValues = !plotZeroValues;
            switchNonZero.setText(plotZeroValues ? "Hide" : "Show all values");
            lastSelectedVO = null;
            redraw();
            return;
        }
        if (src.equals(mPeriod)) {
            PlotIntervalSelector is = new PlotIntervalSelector(monitor.main, timeOfFirstResult, (continuous ? -1
                    : timeOfLastResult), null, null);
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
                for (int i = 0; i < crtNodes.size(); i++) {
                    rcNode n = (rcNode) crtNodes.get(i);
                    if (n.client != null) {
                        n.client.deleteLocalClient(this);
                    }
                }
                lastSelectedVO = null;
                if (timeOfFirstResult < timeOfLastResult) {
                    for (Enumeration en1 = results.elements(); en1.hasMoreElements();) {
                        Hashtable h = (Hashtable) en1.nextElement();
                        if (h == null) {
                            return;
                        }
                        for (Enumeration en = h.keys(); en.hasMoreElements();) {
                            MLSeries ser = (MLSeries) h.get(en.nextElement());
                            if (ser == null) {
                                continue;
                            }
                            ser.data().clear();
                        }
                    }
                }
                if (continuous) {
                    panel3d.setDataInterval(length);
                    defaultTime = length;
                } else {
                    panel3d.setDataInterval(-1l);
                    defaultTime = -1;
                }
                redraw();
                timeOfFirstResult = Long.MAX_VALUE;
                timeOfLastResult = Long.MIN_VALUE;
                if (continuous) {
                    start = -length;
                    logger.log(Level.INFO, "Registering for data on last " + (start / 1000 / 60) + " minutes");
                } else {
                    logger.log(Level.INFO, "Registering for data from =" + new Date(start) + " to=" + new Date(end));
                }
                for (int k = 0; k < 2; k++) {
                    preds[k].tmin = start;
                    preds[k].tmax = end;
                    for (int i = 0; i < crtNodes.size(); i++) {
                        rcNode n = (rcNode) crtNodes.get(i);
                        if (n.client != null) {
                            n.client.addLocalClient(this, preds[k]);
                        }
                    }
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error requesting data");
            }
            return;
        }
        for (int i = 0; i < voSelection.size(); i++) {
            JRadioButtonMenuItem item = (JRadioButtonMenuItem) voSelection.get(i);
            if (src.equals(item)) {
                currentSelectedVO = item.getText();
                if (panel3d != null) {
                    panel3d.setTitle("TotalJobs for " + currentSelectedVO);
                }
                redraw();
                return;
            }
        }
    }

    @Override
    public void componentHidden(ComponentEvent e) {
    }

    @Override
    public void componentMoved(ComponentEvent e) {
    }

    @Override
    public void componentResized(ComponentEvent e) {
    }

    @Override
    public void componentShown(ComponentEvent e) {
        if (!alreadyAdded) {
            add(panel3d, BorderLayout.CENTER);
            revalidate();
            alreadyAdded = true;
        }
    }

} // end of class VoJobs

