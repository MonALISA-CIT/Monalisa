package lia.Monitor.JiniClient.CommonGUI.OSG;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JPanel;
import javax.swing.ToolTipManager;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.graphical;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphTopology;
import lia.Monitor.JiniClient.CommonGUI.Gmap.LayeredTreeLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.LayoutChangedListener;
import lia.Monitor.JiniClient.CommonGUI.Gmap.LayoutTransformer;
import lia.Monitor.JiniClient.CommonGUI.Gmap.RadialLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.SpringLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.OSG.GraphAlgs.GeographicLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.OSG.GraphAlgs.GridLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.OSG.GraphAlgs.NoLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.OSG.GraphAlgs.RandomLayoutAlgorithm;
import lia.Monitor.monitor.AppConfig;
import net.jini.core.lookup.ServiceID;

/**
 * The main class used to represent the graphical panel for the OSG.
 */
public class OSGPanel extends JPanel implements graphical, LayoutChangedListener, ComponentListener {

    static final long serialVersionUID = 281020052L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(OSGPanel.class.getName());

    public OSGMenu cmdPane;
    public OSGHistoryMenu hstPane;
    public OSGCanvas canvasPane;
    public OSGFTPHelper ftpHelper;
    public OSGJOBHelper jobHelper;
    public OSGParamHelper paramHelper;

    public boolean onlyToolTip = false;
    public boolean onlyModule = false;

    public long historyTime = 30 * 60 * 1000;

    SerMonitorBase monitor;
    volatile Map<ServiceID, rcNode> nodes;
    volatile Vector<rcNode> vnodes;

    public HashMap userParams = new HashMap();

    public static final Object syncGraphObj = new Object();

    public boolean showOnlyConnectedNodes = false;

    public byte ftpTransferType = OSGFTPHelper.FTP_INPUT;

    public OSGPanel() {
        super();
        ginit();
    }

    /**
     * Method used to initiate the graphical panels.
     */
    void ginit() {

        paramHelper = new OSGParamHelper();

        ftpHelper = new OSGFTPHelper(this);
        jobHelper = new OSGJOBHelper(this);

        cmdPane = new OSGMenu(this);
        hstPane = new OSGHistoryMenu(this);
        canvasPane = new OSGCanvas(this);

        initLayout();

        setLayout(new BorderLayout());

        add(cmdPane, BorderLayout.NORTH);
        add(canvasPane, BorderLayout.CENTER);
        add(hstPane, BorderLayout.SOUTH);
        setBackground(Color.white);

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                ftpHelper.setNodes(nodes);
                jobHelper.setNodes(nodes);
                canvasPane.setNodes(nodes);
                redoParams(canvasPane.currentNodes);
                repaint();
            }
        };
        BackgroundWorker.schedule(task, 10000, 4000);
        addComponentListener(this);
    }

    public void loadParamsState() {

        userParams.putAll(OSGConstants.UPARAMS);

        try {
            String defSelParam = AppConfig.getProperty("lia.Monitor.param", null);
            String defUnselParam = AppConfig.getProperty("lia.Monitor.paramUnselected", null);
            String selParam = null; //monitor.getUserParamPreferences("lia.Monitor.param", defSelParam);
            String unselParam = null; //monitor.getUserParamPreferences("lia.Monitor.paramUnselected", defUnselParam);

            if (defSelParam != null) {
                putInUserParams(defSelParam, true);
            }
            if (defUnselParam != null) {
                putInUserParams(defUnselParam, false);
            }

            if (selParam != null) {
                putInUserParams(selParam, true);
            }
            if (unselParam != null) {
                putInUserParams(unselParam, false);
            }

            cmdPane.nodesShown.setSelected(getParamState("nodes"));
            cmdPane.cpuShown.setSelected(getParamState("cpu"));
            cmdPane.ioShown.setSelected(getParamState("io"));
            cmdPane.cpuTimeShown.setSelected(getParamState("cputime"));
            cmdPane.jobsShown.setSelected(getParamState("jobs"));
            cmdPane.fJobsShown.setSelected(getParamState("fjobs"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** helper for populate userParam
     *  puts in userParam only the param from a ","-sepparaed list, according to selected */
    public void putInUserParams(String uParams, boolean selected) {
        if (uParams == null) {
            return;
        }
        StringTokenizer tz = new StringTokenizer(uParams, ",");
        while (tz.hasMoreTokens()) {
            String ss = tz.nextToken();
            Integer oldSel = (Integer) userParams.get(ss);
            if ((oldSel == null) || (oldSel.intValue() != (selected ? 1 : 0))) {
                userParams.put(ss, Integer.valueOf(selected ? 1 : 0));
                //monitor.updateUserParamPreferences(ss, selected);
            }
        }
    }

    public boolean getParamState(String name) {
        return ((Integer) userParams.get(name)).intValue() == 1;
    }

    public void setShowShadow(boolean show) {
        canvasPane.showShadow = show;
        canvasPane.repaint();
    }

    public void setShowSphere(boolean show) {
        canvasPane.showSphere = show;
        canvasPane.repaint();
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
        this.monitor = ms;
        loadParamsState();
    }

    @Override
    public void setMaxFlowData(rcNode n, Vector v) {
    }

    @Override
    public void new_global_param(String name) {
    }

    /**
     * Stuff regarding the current graphical layout
     */

    /** The current layout transformer object */
    LayoutTransformer layoutTransformer;

    /** The name of the current layout */
    String currentLayout = "None";

    /** Flag used to cancel the current transform */
    boolean currentTransformCancelled = false;

    /** The current layout */
    GraphLayoutAlgorithm layout = null;

    public void initLayout() {

        TimerTask ttask = new LayoutUpdateTimerTask(this.getParent());
        BackgroundWorker.schedule(ttask, 4000, 4000);
        layoutTransformer = new LayoutTransformer(this);
    }

    /**
     * Class used for the current update
     */
    class LayoutUpdateTimerTask extends TimerTask {
        Component parent;

        public LayoutUpdateTimerTask(Component parent) {
            this.parent = parent;
        }

        @Override
        public void run() {
            Thread.currentThread().setName(" ( ML ) - Farms OSG - OSGPanel layout update Timer Thread");
            try {
                if ((parent != null) && parent.isVisible()) {
                    if (!currentLayout.equals("SomeLayout")) {
                        setLayoutType(currentLayout);
                    }
                    repaint();
                }
                ;
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error executing", t);
            }
        }
    };

    /**
     * Set the fix flag for the current drawn nodes.
     */
    void unfixNodes() {
        if (nodes != null) {
            for (final rcNode n : nodes.values()) {
                n.fixed = false;
            }
        }
    }

    /**
     * Called in order to set a new layout for the canvas.
     * 
     * @param type
     *                  The new type of the layout
     */
    void setLayoutType(String type) {

        if ((layout == null) && type.equals("Elastic")) {
            currentLayout = "None";
        }
        if ((layout != null) && !type.equals(currentLayout)) {
            currentLayout = "None";
            layout.finish();
        }
        if (type.equals("Random") || type.equals("Grid") || type.equals("Map") || type.equals("None")) {
            synchronized (syncGraphObj) {
                unfixNodes();
                GraphTopology topology = ftpHelper.constructTopology(ftpTransferType);
                synchronized (canvasPane.getTreeLock()) {
                    canvasPane.vGraph = topology;
                }
                if (type.equals("Random")) {
                    layout = new RandomLayoutAlgorithm(canvasPane.vGraph);
                } else if (type.equals("Grid")) {
                    layout = new GridLayoutAlgorithm(canvasPane.vGraph);
                } else if (type.equals("Map")) {
                    layout = new GeographicLayoutAlgorithm(canvasPane.vGraph);
                } else {
                    layout = new NoLayoutAlgorithm(canvasPane.vGraph);
                }
                layoutTransformer.layoutChanged();
            }
        } else if (type.equals("Radial") || type.equals("Layered")) {
            synchronized (syncGraphObj) {
                unfixNodes();
                GraphTopology topology = ftpHelper.constructTopology(ftpTransferType);
                synchronized (canvasPane.getTreeLock()) {
                    canvasPane.vGraph = topology;
                }
                if ((canvasPane.pickX == null) || (!nodes.containsValue(canvasPane.pickX))) {
                    canvasPane.pickX = canvasPane.vGraph.findARoot();
                }
                if (canvasPane.pickX != null) {
                    canvasPane.vGraph.pruneToTree(canvasPane.pickX); // convert
                }
                // the
                // graph
                // to a
                // tree
                if (type.equals("Radial")) {
                    layout = new RadialLayoutAlgorithm(canvasPane.vGraph, canvasPane.pickX);
                } else {
                    layout = new LayeredTreeLayoutAlgorithm(canvasPane.vGraph, canvasPane.pickX);
                }
                layoutTransformer.layoutChanged();
            }
        } else if (type.equals("Elastic")) {
            if (currentLayout.equals("Elastic")) {
                synchronized (syncGraphObj) {
                    GraphTopology topology = ftpHelper.constructTopology(ftpTransferType);
                    ;
                    synchronized (canvasPane.getTreeLock()) {
                        canvasPane.vGraph = topology;
                    }
                    ((SpringLayoutAlgorithm) layout).updateGT(canvasPane.vGraph);
                }
            } else {
                synchronized (syncGraphObj) {
                    unfixNodes();
                    currentLayout = "Elastic";
                    GraphTopology topology = ftpHelper.constructTopology(ftpTransferType);
                    synchronized (canvasPane.getTreeLock()) {
                        canvasPane.vGraph = topology;
                    }
                    layout = new SpringLayoutAlgorithm(canvasPane.vGraph, this);
                    ((SpringLayoutAlgorithm) layout).setStiffness(cmdPane.sldStiffness.getValue());
                    ((SpringLayoutAlgorithm) layout).setRespRange(cmdPane.sldRepulsion.getValue());
                    layout.layOut();
                }
            }
        }
    }

    /** called to stop the elastic update */
    public void stopElastic() {
        if (cmdPane.cbLayout.getSelectedItem().equals("Elastic")) {
            synchronized (OSGPanel.syncGraphObj) {
                cmdPane.cbLayout.setSelectedIndex(0);
                currentLayout = "None";
                if (layout != null) {
                    layout.finish();
                }
                layout = null;
            }
        }
    }

    /** used to compute a new layout */
    @Override
    public void computeNewLayout() {
        synchronized (syncGraphObj) {
            if (!(layout instanceof NoLayoutAlgorithm)) {
                currentLayout = "SomeLayout";
            }
            canvasPane.range.setBounds(0, 0, canvasPane.getWidth(), canvasPane.getHeight());
            canvasPane.range.grow(-canvasPane.wunit, -canvasPane.hunit);
            layout.layOut();

            // System.out.println("range: "+canvasPane.range);

            if (layout instanceof NoLayoutAlgorithm) { // currentLayout.equals("None")){
                // System.out.println("exit1 "+currentLayout+" class
                // "+layout.getClass().getName());
                layout = null;
                repaint();
                return;
            }
            // transform smoothly from the current positions to the destination
            long TRANSF_TOTAL_TIME = 2000; // 3
                                           // seconds
            long STEP_DELAY = 30; // 30 millis
            long nSteps = TRANSF_TOTAL_TIME / STEP_DELAY;

            // convert positions from relative [-1, 1] to [range]
            for (Iterator it = canvasPane.vGraph.gnodes.iterator(); it.hasNext();) {
                GraphNode gn = (GraphNode) it.next();
                if (!gn.rcnode.fixed) {
                    // System.out.println("old: "+gn.rcnode.x+"-"+gn.rcnode.y);
                    // System.out.println("pos: "+gn.pos.x+"-"+gn.pos.y);
                    gn.pos.x = canvasPane.range.x + (int) ((canvasPane.range.width * (1.0 + gn.pos.x)) / 2.0);
                    gn.pos.y = canvasPane.range.y + (int) ((canvasPane.range.height * (1.0 + gn.pos.y)) / 2.0);
                    // System.out.println("new: "+gn.pos.x+"-"+gn.pos.y);
                }
            }
            currentTransformCancelled = false;
            // perform transitions
            for (int i = 0; (i < nSteps) && !currentTransformCancelled; i++) {
                // System.out.println("transition "+i);
                for (Iterator it = canvasPane.vGraph.gnodes.iterator(); it.hasNext();) {
                    GraphNode gn = (GraphNode) it.next();
                    if (!gn.rcnode.fixed) {
                        int dx = (int) ((gn.pos.x - gn.rcnode.osgX) / (nSteps - i));
                        int dy = (int) ((gn.pos.y - gn.rcnode.osgY) / (nSteps - i));
                        gn.rcnode.osgX += dx;
                        gn.rcnode.osgY += dy;
                    }
                }
                repaint();
                try {
                    Thread.sleep(STEP_DELAY);
                } catch (InterruptedException ex) {
                }
            }

            // final positions
            for (Iterator it = canvasPane.vGraph.gnodes.iterator(); it.hasNext();) {
                GraphNode gn = (GraphNode) it.next();
                if (!gn.rcnode.fixed) {
                    gn.rcnode.osgX = (int) gn.pos.x;
                    gn.rcnode.osgY = (int) gn.pos.y;
                }
            }
            // System.out.println("exit2 "+currentLayout+" class
            // "+layout.getClass().getName());
            // invoke the NoLayout algorithm to recompute visibility for the
            // other nodes
            layout = new NoLayoutAlgorithm(canvasPane.vGraph);
            layout.layOut();
            layout = null;
            repaint();
            currentLayout = "None";
        }
    }

    @Override
    public int setElasticLayout() {
        canvasPane.range.setBounds(0, 0, getWidth(), getHeight());
        canvasPane.range.grow(-canvasPane.wunit - 2, -canvasPane.hunit - 2);
        int totalMovement = 0;
        for (Iterator it = canvasPane.vGraph.gnodes.iterator(); it.hasNext();) {
            GraphNode gn = (GraphNode) it.next();
            int nx = gn.rcnode.osgX;
            int ny = gn.rcnode.osgY;
            gn.rcnode.osgX = (int) Math.round(gn.pos.x);
            gn.rcnode.osgY = (int) Math.round(gn.pos.y);
            if (gn.rcnode.osgX < canvasPane.range.x) {
                gn.rcnode.osgX = canvasPane.range.x;
            }
            if (gn.rcnode.osgY < canvasPane.range.y) {
                gn.rcnode.osgY = canvasPane.range.y;
            }
            if (gn.rcnode.osgX > canvasPane.range.getMaxX()) {
                gn.rcnode.osgX = (int) canvasPane.range.getMaxX();
            }
            if (gn.rcnode.osgY > canvasPane.range.getMaxY()) {
                gn.rcnode.osgY = (int) canvasPane.range.getMaxY();
            }
            gn.pos.setLocation(gn.rcnode.osgX, gn.rcnode.osgY);
            totalMovement += Math.abs(gn.rcnode.osgX - nx) + Math.abs(gn.rcnode.osgY - ny);
        }
        repaint();
        return totalMovement;
    }

    private TopologyThread tthread = null;

    static final Object lock = new Object();

    public synchronized void redoTopology() {

        if (tthread == null) {
            tthread = new TopologyThread();
        }
        tthread.redoTopology();
    }

    private class TopologyThread extends Thread {

        public boolean redo = false;

        public TopologyThread() {
            super("( ML ) Topology Thread - OSG Panel");
            start();
        }

        public void redoTopology() {

            synchronized (lock) {
                redo = true;
                lock.notifyAll();
            }
        }

        @Override
        public void run() {
            while (true) {
                synchronized (lock) {
                    while (!redo) {
                        try {
                            lock.wait();
                        } catch (Exception ex) {
                        }
                    }
                    redo = false;
                }
                GraphTopology topology = OSGPanel.this.ftpHelper.constructTopology(OSGPanel.this.ftpTransferType);
                synchronized (OSGPanel.this.canvasPane.getTreeLock()) {
                    OSGPanel.this.canvasPane.vGraph = topology;
                }
                if (!redo) {
                    OSGPanel.this.repaint();
                }
            }
        }
    }

    // for param

    public void redoParams(Vector nodes) {

        if ((nodes == null) || (nodes.size() == 0)) {
            return;
        }

        HashMap nod = paramHelper.requestNodes(nodes);
        HashMap cpu = null;
        HashMap cpuno = paramHelper.requestCpuNo(nodes);
        HashMap io = null;
        HashMap jobs = null;
        HashMap fjobs = null;
        HashMap cpuTime = null;

        if (getParamState("cpu")) {
            cpu = paramHelper.requestCPU(nodes);
        }
        if (getParamState("io")) {
            io = paramHelper.requestIO(nodes);
        }
        if (getParamState("jobs")) {
            jobs = jobHelper.requestJobs(nodes);
        }
        if (getParamState("fjobs")) {
            fjobs = jobHelper.requestFinishedJobs(nodes);
        }
        if (getParamState("cputime")) {
            cpuTime = jobHelper.requestCPUTime(nodes);
        }

        synchronized (canvasPane.nodeParams) {
            canvasPane.nodeParams.clear();
            for (int i = 0; i < nodes.size(); i++) {
                rcNode n = (rcNode) nodes.get(i);
                if ((nod != null) && nod.containsKey(n)) {
                    HashMap h = null;
                    if (canvasPane.nodeParams.containsKey(n)) {
                        h = (HashMap) canvasPane.nodeParams.get(n);
                    } else {
                        h = new HashMap();
                        canvasPane.nodeParams.put(n, h);
                    }
                    h.put(paramHelper.nodesString, nod.get(n));
                }
                if ((cpu != null) && cpu.containsKey(n)) {
                    HashMap h = null;
                    if (canvasPane.nodeParams.containsKey(n)) {
                        h = (HashMap) canvasPane.nodeParams.get(n);
                    } else {
                        h = new HashMap();
                        canvasPane.nodeParams.put(n, h);
                    }
                    h.put(paramHelper.cpuString, cpu.get(n));
                }
                if ((cpuno != null) && cpuno.containsKey(n)) {
                    HashMap h = null;
                    if (canvasPane.nodeParams.containsKey(n)) {
                        h = (HashMap) canvasPane.nodeParams.get(n);
                    } else {
                        h = new HashMap();
                        canvasPane.nodeParams.put(n, h);
                    }
                    h.put(paramHelper.cpuNoString, cpuno.get(n));
                }
                if ((io != null) && io.containsKey(n)) {
                    HashMap h = null;
                    if (canvasPane.nodeParams.containsKey(n)) {
                        h = (HashMap) canvasPane.nodeParams.get(n);
                    } else {
                        h = new HashMap();
                        canvasPane.nodeParams.put(n, h);
                    }
                    h.put(paramHelper.ioString, io.get(n));
                }
                if ((jobs != null) && jobs.containsKey(n)) {
                    HashMap h = null;
                    if (canvasPane.nodeParams.containsKey(n)) {
                        h = (HashMap) canvasPane.nodeParams.get(n);
                    } else {
                        h = new HashMap();
                        canvasPane.nodeParams.put(n, h);
                    }
                    h.put(paramHelper.jobsString, jobs.get(n));
                }
                if ((fjobs != null) && fjobs.containsKey(n)) {
                    HashMap h = null;
                    if (canvasPane.nodeParams.containsKey(n)) {
                        h = (HashMap) canvasPane.nodeParams.get(n);
                    } else {
                        h = new HashMap();
                        canvasPane.nodeParams.put(n, h);
                    }
                    h.put(paramHelper.fJobsString, fjobs.get(n));
                }
                if ((cpuTime != null) && cpuTime.containsKey(n)) {
                    HashMap h = null;
                    if (canvasPane.nodeParams.containsKey(n)) {
                        h = (HashMap) canvasPane.nodeParams.get(n);
                    } else {
                        h = new HashMap();
                        canvasPane.nodeParams.put(n, h);
                    }
                    h.put(paramHelper.cpuTimeString, cpuTime.get(n));
                }
            }
        }
        canvasPane.repaint();
    }

    @Override
    public void componentResized(ComponentEvent e) {
    }

    @Override
    public void componentMoved(ComponentEvent e) {
    }

    @Override
    public void componentShown(ComponentEvent e) {

        ToolTipManager ttm = ToolTipManager.sharedInstance();
        ttm.setInitialDelay(0);
        ttm.setReshowDelay(0);
        ttm.setDismissDelay(30 * 1000);
    }

    @Override
    public void componentHidden(ComponentEvent e) {
    }

    public static void main(String args[]) {
        //		
        //		JFrame frame = new JFrame("Test");
        //		frame.setSize(800, 500);
        //		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        //		frame.getContentPane().setLayout(new BorderLayout());
        //		
        //		OSGPanel p = new OSGPanel();
        //		Vector v = new Vector(); // init some fake nodes
        //		Hashtable h = new Hashtable();
        //		Random r = new Random();
        //		for (int i=0; i<100; i++) {
        //			rcNode n = new rcNode();
        //			n.x = r.nextInt(300);
        //			n.y = r.nextInt(300);
        //			MonaLisaEntry e = new MonaLisaEntry("farm_"+UUID.randomUUID().toString(), "test,test"+r.nextInt(2));
        //			SiteInfoEntry s = new SiteInfoEntry();
        //			ExtendedSiteInfoEntry ex = new ExtendedSiteInfoEntry();
        //			ServiceID ser = new ServiceID(r.nextLong(), r.nextLong());
        //			try {
        //				n.client = new tClient("farm_"+UUID.randomUUID().toString(), "100.100.100."+r.nextInt(255), UUID.randomUUID().toString(), r.nextInt(200), r.nextInt(200), r.nextInt(200), e, s, ex, null, ser);
        //			} catch (Throwable t) { }
        //			n.shortName = n.client.getFarmName();
        //			n.sid = n.client.tClientID;
        //			n.mlentry = e;
        //			// put load5
        //			Gresult res = new Gresult();
        //			res.Nodes = r.nextInt(300);
        //			res.hist = new int[5];
        //			for (int k=0; k<4; k++) res.hist[k] = (int)(res.Nodes * r.nextDouble() / 5);
        //			res.hist[4] = res.Nodes - (res.hist[0]+res.hist[1]+res.hist[2]+res.hist[3]);
        //			n.global_param.put("Load5", res);
        //			v.add(n);
        //			h.put(n.sid, n);
        //		}
        //		p.setNodes(h, v);
        //		
        //		frame.getContentPane().add(p, BorderLayout.CENTER);
        //		
        //		frame.setVisible(true);
    }
} // end of class OSGPanel
