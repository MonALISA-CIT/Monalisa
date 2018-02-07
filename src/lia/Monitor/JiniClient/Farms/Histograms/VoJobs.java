package lia.Monitor.JiniClient.Farms.Histograms;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JCheckBox;
import javax.swing.JPanel;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.graphical;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.Gresult;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.MLSerClient;
import net.jini.core.lookup.ServiceID;
import plot.CombinedChart;
import plot.CombinedChartPanel;

public class VoJobs extends JPanel implements graphical, ActionListener, ComponentListener, LocalDataFarmClient {
    /**
     * 
     */
    private static final long serialVersionUID = 1437678639330926689L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(VoJobs.class.getName());

    SerMonitorBase monitor;
    volatile Map<ServiceID, rcNode> nodes;
    volatile Vector<rcNode> vnodes;
    Hashtable graphs; // hashtable with all checkbox
    Hashtable name2title; // correspondence between results names and titles in charts
    JPanel graphsPan;

    monPredicate[] preds;

    int NRC; // number of RCs
    Vector crtNodes;
    Hashtable results; // current results
    //Timer timer;
    int invisibleUpdates;
    boolean shouldRedraw;

    String[] seriesNamesJOBS = new String[] { "Running Jobs", "Idle Jobs", "Held Jobs" };
    String[] seriesNamesJOBSNew = new String[] { "RunningJobs", "IdleJobs", "HeldJobs" };
    Color[] colorsJOBS = new Color[] { Color.GREEN, Color.YELLOW, Color.RED };
    String[] seriesNamesFarmsUsage = new String[] { "High Load", "Medium Load", "Low Load" };
    Color[] colorsFarmsUsage = new Color[] { Color.MAGENTA, Color.PINK, Color.CYAN };
    String[] seriesNamesCPUs = new String[] { "CPUs" };
    Color[] colorsCPUs = new Color[] { Color.BLUE };
    String[] seriesTotalJobs = new String[] { "Total Running", "Total Idle", "Total Held" };
    Color[] colorsTotalJobs = new Color[] { new Color(128, 255, 128), new Color(255, 255, 128),
            new Color(255, 128, 128) };

    GridLayout grid1Lay = new GridLayout(1, 10);
    GridLayout grid2Lay = new GridLayout(0, 10);

    CombinedChartPanel combinedChartPanel = null;

    HashSet newModuleDetected = new HashSet();

    static class Value {
        double val;

        Value() {
            this.val = 0;
        }

        Value(double val) {
            this.val = val;
        }
    }

    public VoJobs() {
        setLayout(new BorderLayout());

        crtNodes = new Vector();
        graphs = new Hashtable();

        addComponentListener(this);

        results = new Hashtable();
        name2title = new Hashtable();

        // predicates
        preds = new monPredicate[4];
        preds[0] = new monPredicate("*", "VO_JOBS", "*", -2 * 60 * 1000, -1, new String[] { "Running Jobs",
                "Idle Jobs", "Held Jobs" }, null);
        preds[1] = new monPredicate("*", "PN%", "*", -2 * 60 * 1000, -1, new String[] { "NoCPUs", "Load5" }, null);
        preds[2] = new monPredicate("*", "osgVO_JOBS", "*", -2 * 60 * 1000, -1, new String[] { "RunningJobs",
                "IdleJobs", "HeldJobs" }, null);
        preds[3] = new monPredicate("*", "LcgVO_JOBS", "*", -2 * 60 * 1000, -1, new String[] { "RunningJobs",
                "IdleJobs", "HeldJobs" }, null);

        graphsPan = new JPanel();
        graphsPan.setLayout(grid1Lay);

        JCheckBox cbClusterUsage = new JCheckBox("Cluster Usage", true);
        cbClusterUsage.addActionListener(this);
        cbClusterUsage.setToolTipText("Cluster Usage");
        JCheckBox cbTotalJobs = new JCheckBox("Total Jobs", true);
        cbTotalJobs.addActionListener(this);
        cbTotalJobs.setToolTipText("Total Jobs");

        JCheckBox cbCPUs = new JCheckBox("Number of CPUs", false);
        cbCPUs.addActionListener(this);
        cbCPUs.setToolTipText("Number of CPUs");

        /**
         * moved to setResult function, when a new result is coming
         */
        /*		JCheckBox cbATLASJobs = new JCheckBox("ATLAS Jobs", false);
        		cbATLASJobs.addActionListener(this);
        		JCheckBox cbBTeVJobs = new JCheckBox("BTeV Jobs", false);
        		cbBTeVJobs.addActionListener(this);
        		JCheckBox cbiVDgLJobs = new JCheckBox("iVDgL Jobs", false);
        		cbiVDgLJobs.addActionListener(this);
        		JCheckBox cbLIGOJobs = new JCheckBox("LIGO Jobs", false);
        		cbLIGOJobs.addActionListener(this);
        		JCheckBox cbSDSSJobs = new JCheckBox("SDSS Jobs", false);
        		cbSDSSJobs.addActionListener(this);
        		JCheckBox cbUSCMSJobs = new JCheckBox("USCMS Jobs", false);
        		cbUSCMSJobs.addActionListener(this);
        */
        graphs.put("FarmUsage", cbClusterUsage);
        graphsPan.add(cbClusterUsage);
        graphs.put("TotalJobs", cbTotalJobs);
        graphsPan.add(cbTotalJobs);
        graphs.put("TotCPUs", cbCPUs);
        graphsPan.add(cbCPUs);
        /*		graphs.put("ATLAS", cbATLASJobs); graphsPan.add(cbATLASJobs);
        		graphs.put("BTeV", cbBTeVJobs); graphsPan.add(cbBTeVJobs);
        		graphs.put("iVDgL", cbiVDgLJobs); graphsPan.add(cbiVDgLJobs);
        		graphs.put("LIGO", cbLIGOJobs); graphsPan.add(cbLIGOJobs);
        		graphs.put("SDSS", cbSDSSJobs); graphsPan.add(cbSDSSJobs);
        		graphs.put("USCMS", cbUSCMSJobs); graphsPan.add(cbUSCMSJobs);
        */
        combinedChartPanel = new CombinedChartPanel("Global Statistics", "Farms", false);

        add(graphsPan, BorderLayout.NORTH);
        add(combinedChartPanel, BorderLayout.CENTER);
        // add charts corresponding to default-selected checkboxes
        for (Enumeration eng = graphs.elements(); eng.hasMoreElements();) {
            JCheckBox cb = (JCheckBox) eng.nextElement();
            if (cb.isSelected()) {
                String title = cb.getText();
                addChart(title);
            }
        }
        //		combinedChartPanel.setPreferredSize(new Dimension(800, 500));
        //		combinedChartPanel.setDimension(new Dimension(800, 500));
        //		setPreferredSize(new Dimension(800, 500));
        startTimer();
    }

    void redrawAll() {
        //long nStartTime = NTPDate.currentTimeMillis();
        //System.out.println("VoJobs: redraw");
        updateRC();
        updateData();
        setCCSize();
        combinedChartPanel.redraw();
        //long nEndTime = NTPDate.currentTimeMillis();
        //System.out.println("VoJobs: redraw - ended time: "+(nEndTime-nStartTime)+" ms");
    }

    void addChart(String title) {
        double[] min3 = new double[] { 0, 0, 0 };
        double[] max3 = new double[] { 1, 1, 1 };
        int[] cnt3 = new int[] { 1, 1, 1 };
        double[] min1 = new double[] { 0 };
        double[] max1 = new double[] { 1 };
        int[] cnt1 = new int[] { 1 };

        String[] series = null;
        Color[] colors = null;
        double[] min = null;
        double[] max = null;
        int[] cnt = null;
        int type = 0;

        if (title.equals("Number of CPUs")) {
            series = seriesNamesCPUs;
            colors = colorsCPUs;
            min = min1;
            max = max1;
            cnt = cnt1;
            type = CombinedChart.TYPE_3D_BAR;
            name2title.put("Procs", "Number of CPUs");
        } else if (title.equals("Cluster Usage")) {
            series = seriesNamesFarmsUsage;
            colors = colorsFarmsUsage;
            min = min3;
            max = max3;
            cnt = cnt3;
            type = CombinedChart.TYPE_STACKED_3D_BAR;
            name2title.put("FarmUsage", "Cluster Usage");
        } else if (title.equals("Total Jobs")) {
            series = seriesTotalJobs;
            colors = colorsTotalJobs;
            min = min1;
            max = max1;
            cnt = cnt1;
            type = CombinedChart.TYPE_STACKED_3D_BAR;
            name2title.put("TotalJobs", "Total Jobs");
        } else if (title.indexOf(" Jobs") >= 0) {
            series = seriesNamesJOBS;
            colors = colorsJOBS;
            min = min3;
            max = max3;
            cnt = cnt3;
            type = CombinedChart.TYPE_STACKED_3D_BAR;
            name2title.put(title.substring(0, title.indexOf(" ")), title);
        } else {
            System.out.println("Unknown chart!!");
        }
        //System.out.println("adding chart "+title);
        combinedChartPanel.addNewBarChart(title, title, series, colors, min, max, cnt, type);
        combinedChartPanel.changeDepth(title, 3, 4);
        redrawAll();
        //		updateRC();
        //		setCCSize();
        //		combinedChartPanel.redraw();
        //		shouldRedraw = true;
    }

    void removeChart(String title) {
        combinedChartPanel.removeBarChart(title);
        for (Enumeration en = name2title.keys(); en.hasMoreElements();) {
            String key = (String) en.nextElement();
            if (name2title.get(key).equals(title)) {
                name2title.remove(key);
                //System.out.println("removing chart "+title);
                redrawAll();
                //				setCCSize();
                //				combinedChartPanel.redraw();
                //				shouldRedraw = true;
                //				updateRC();
                return;
            }
        }
    }

    /** set combined chart size */
    private void setCCSize() {
        Dimension d = getSize();
        //System.out.println("panSize="+d);
        d.setSize(d.width, d.height - graphsPan.getHeight());
        //		System.out.println("cc_Size="+d);
        combinedChartPanel.setDimension(d);
    }

    private void startTimer() {
        TimerTask ttask = new TimerTask() {
            @Override
            public void run() {
                if (shouldRedraw && isVisible()) {
                    try {
                        shouldRedraw = false;
                        redrawAll();
                    } catch (Throwable ex) {
                        logger.log(Level.WARNING, "Error executing ", ex);
                    }
                } else {
                    //System.out.println("startTimer: skipping");
                    updateNodes();
                }
            }
        };
        BackgroundWorker.schedule(ttask, 2000, 6000);
    }

    /** well.. it seems this in never called, but it's in interface */
    @Override
    public void updateNode(rcNode node) {
        // empty
    }

    protected void getVOs() {
        //here will be put name of vo and a flag to indicate that this vo still gives results
        ArrayList hVoStillActive = new ArrayList();
        //any vo that is not active will be removed
        try {
            for (Iterator it = crtNodes.iterator(); it.hasNext();) {
                rcNode node = (rcNode) it.next();
                Vector v = null;
                Vector clusters = node.client.farm.getClusters();
                for (int i = 0; i < clusters.size(); i++) {
                    MCluster cluster = (MCluster) clusters.get(i);
                    if ((cluster.name.indexOf("VO_JOBS") != -1) && (cluster.name.indexOf("NO_VO_JOBS") == -1)) {
                        v = cluster.getNodes();
                        for (Iterator it2 = v.iterator(); it2.hasNext();) {
                            String vo = ((MNode) it2.next()).toString();
                            if (!graphs.containsKey(vo)) {
                                //								System.out.println("new vo: "+vo);
                                JCheckBox cbJobs = new JCheckBox(vo + " Jobs", false);
                                cbJobs.addActionListener(this);
                                cbJobs.setToolTipText(vo + " Jobs");
                                graphs.put(vo, cbJobs);
                                graphsPan.add(cbJobs);
                                if (graphsPan.getComponentCount() > 10) {
                                    if (graphsPan.getLayout() != grid2Lay) {
                                        graphsPan.setLayout(grid2Lay);
                                    }
                                } else {
                                    if (graphsPan.getLayout() != grid1Lay) {
                                        graphsPan.setLayout(grid1Lay);
                                    }
                                }
                                ;
                                repaint();
                            }
                            if (!hVoStillActive.contains(vo)) {
                                hVoStillActive.add(vo);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
        }
        ;
        try {
            for (Enumeration en = graphs.keys(); en.hasMoreElements();) {
                String vo = (String) en.nextElement();
                /**
                		graphs.put("FarmUsage", cbClusterUsage); graphsPan.add(cbClusterUsage);
                		graphs.put("TotalJobs", cbTotalJobs); graphsPan.add(cbTotalJobs);
                		graphs.put("TotCPUs", cbCPUs); graphsPan.add(cbCPUs);
                 */
                if (!vo.equals("FarmUsage") && !vo.equals("TotalJobs") && !vo.equals("TotCPUs")
                        && !hVoStillActive.contains(vo)) {
                    //this vo is no more active, so remove it
                    JCheckBox cbJobs = (JCheckBox) graphs.remove(vo);
                    graphsPan.remove(cbJobs);
                    String title = cbJobs.getText();
                    removeChart(title);
                    repaint();
                }
            }
        } catch (Exception ex) {
        }
        ;
    }

    /**
     * registers predicates for new nodes or
     * deletes rezidual nodes
     *
     */
    public void updateNodes() {
        if (vnodes == null) {
            return;
        }

        NRC = vnodes.size();
        // check if there are new nodes
        for (int i = 0; i < vnodes.size(); i++) {
            rcNode n = vnodes.get(i);
            if ((!crtNodes.contains(n))
                    && ((n.mlentry.Group.indexOf("grid3") >= 0) || (n.mlentry.Group.indexOf("osg") >= 0) || (n.mlentry.Group
                            .indexOf("OSG") >= 0))) {
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
                results.put(n, new Hashtable());

                for (monPredicate pred : preds) {
                    n.client.addLocalClient(this, pred);
                    //				System.out.println("Registered for data with "+n.UnitName);
                }
            }
        }
        // check if there are removed nodes
        for (int i = 0; i < crtNodes.size(); i++) {
            rcNode n = (rcNode) crtNodes.get(i);
            if (!vnodes.contains(n)) {
                n.client.deleteLocalClient(this);//unregistres panel to receive data from removed farm
                crtNodes.remove(i);
                results.remove(n);
                newModuleDetected.remove(n);
                //n.client.deleteLocalClient(this);
                //				System.out.println("Unregistring for data from "+n.UnitName);
                i--;
            }
        }

        //check to see if new vos are available
        getVOs();
    }

    /** called when the number of RCs changes => the graphic is redrawn */
    void updateRC() {
        if (vnodes == null) {
            return;
        }
        //System.out.println("updateRC: start");
        //componentResized(null);
        updateNodes();

        // set graphs ticks
        String lastName = "";
        for (Enumeration eg = name2title.elements(); eg.hasMoreElements();) {
            String grName = (String) eg.nextElement();
            //			System.out.println("setCategNR "+crtNodes.size());
            //			if(crtNodes.size() == 0)
            //				continue;
            combinedChartPanel.setCategoriesNumber(grName, crtNodes.size());
            //System.out.println("addXTick "+grName);
            for (int i = 0; i < crtNodes.size(); i++) {
                String farmName = ((rcNode) crtNodes.get(i)).UnitName;
                if (farmName.equals(lastName.trim())) {
                    lastName = lastName + " ";
                } else {
                    lastName = farmName;
                }
                combinedChartPanel.addXTick(grName, farmName, i);
            }
        }
        //System.out.println("updateRC: end");
    }

    /** called when data is updated and the graphic must be redrawn */
    void updateData() {
        //		System.out.println("VoJobs: updateData crtNodes="+crtNodes.size());
        //System.out.println("updateData: start");
        String pfree = "FarmUsage/Low Load";
        String pmed = "FarmUsage/Medium Load";
        String pfull = "FarmUsage/High Load";
        String totCPUs = "Procs/CPUs";
        String totJobsRunning = "TotalJobs/Total Running";
        String totJobsIdle = "TotalJobs/Total Idle";
        String totJobsHeld = "TotalJobs/Total Held";
        for (int i = 0; i < crtNodes.size(); i++) {
            rcNode n = (rcNode) crtNodes.get(i);
            Hashtable rlist = (Hashtable) results.get(n);

            // compute farm total load
            if (rlist == null) {
                rlist = new Hashtable();
                results.put(n, rlist);
            }
            Gresult ldx = ((n == null) || (n.global_param == null) ? null : (Gresult) n.global_param.get("Load5"));
            /*if (ldx == null ) {					
            	 ldx  = (n==null || n.global_param == null?null:(Gresult) n.global_param.get("Load1" ));
            	 if(ldx!=null && ldx.ClusterName.indexOf("PBS")==-1 &&  ldx.ClusterName.indexOf("Condor")==-1 ) 
            		 ldx=null;					 					 
            }*/
            Value vfree = (Value) rlist.get(pfree);
            if (vfree == null) {
                vfree = new Value();
                rlist.put(pfree, vfree);
            }
            Value vmed = (Value) rlist.get(pmed);
            if (vmed == null) {
                vmed = new Value();
                rlist.put(pmed, vmed);
            }
            Value vfull = (Value) rlist.get(pfull);
            if (vfull == null) {
                vfull = new Value();
                rlist.put(pfull, vfull);
            }
            if (ldx != null) {
                vfull.val = ldx.hist[4]; // / (double) ldx.Nodes;
                vmed.val = (ldx.hist[3] + ldx.hist[2]); // / (double) ldx.Nodes;
                vfree.val = (ldx.hist[0] + ldx.hist[1]); // / (double) ldx.Nodes;
            }

            // compute total number of CPUs
            Value vcpus = (Value) rlist.get(totCPUs);
            if (vcpus == null) {
                vcpus = new Value();
                rlist.put(totCPUs, vcpus);
            }
            vcpus.val = 0;
            for (Enumeration en = rlist.keys(); en.hasMoreElements();) {
                String key = (String) en.nextElement();
                if (key.startsWith("PN") && (key.indexOf("NoCPUs") >= 0)) {
                    vcpus.val += ((Value) rlist.get(key)).val;
                }
            }

            // compute total number of Jobs
            Value vTotJobsRunning = (Value) rlist.get(totJobsRunning);
            if (vTotJobsRunning == null) {
                vTotJobsRunning = new Value();
                rlist.put(totJobsRunning, vTotJobsRunning);
            }
            vTotJobsRunning.val = 0;
            Value vTotJobsIdle = (Value) rlist.get(totJobsIdle);
            if (vTotJobsIdle == null) {
                vTotJobsIdle = new Value();
                rlist.put(totJobsIdle, vTotJobsIdle);
            }
            vTotJobsIdle.val = 0;
            Value vTotJobsHeld = (Value) rlist.get(totJobsHeld);
            if (vTotJobsHeld == null) {
                vTotJobsHeld = new Value();
                rlist.put(totJobsHeld, vTotJobsHeld);
            }
            vTotJobsHeld.val = 0;
            if (newModuleDetected.contains(n)) {
                for (Enumeration en = rlist.keys(); en.hasMoreElements();) {
                    String key = (String) en.nextElement();
                    double v = ((Value) rlist.get(key)).val;
                    if (key.indexOf("IdleJobs") >= 0) {
                        vTotJobsIdle.val += v;
                    }
                    if (key.indexOf("RunningJobs") >= 0) {
                        vTotJobsRunning.val += v;
                    }
                    if (key.indexOf("HeldJobs") >= 0) {
                        vTotJobsHeld.val += v;
                    }
                }
            } else {
                for (Enumeration en = rlist.keys(); en.hasMoreElements();) {
                    String key = (String) en.nextElement();
                    double v = ((Value) rlist.get(key)).val;
                    if (key.indexOf("Idle Jobs") >= 0) {
                        vTotJobsIdle.val += v;
                    }
                    if (key.indexOf("Running Jobs") >= 0) {
                        vTotJobsRunning.val += v;
                    }
                    if (key.indexOf("Held Jobs") >= 0) {
                        vTotJobsHeld.val += v;
                        //					if(key.endsWith(" Jobs") && (! key.startsWith("Total")))
                        //					vTotJobs.val += ((Value) rlist.get(key)).val;
                    }
                }
            }

        }
        //		// print all data
        //		for(int i=0; i<crtNodes.size(); i++){
        //			rcNode n = (rcNode) crtNodes.get(i);
        //			Hashtable rlist = (Hashtable) results.get(n);
        //			for(Enumeration en=rlist.keys(); en.hasMoreElements(); ){
        //				String key = (String) en.nextElement();
        //				System.out.println(n.UnitName+" => "+key+" = "+((Value)rlist.get(key)).val);
        //			}
        //		}
        // try to plot all this data
        for (int i = 0; i < crtNodes.size(); i++) {
            rcNode n = (rcNode) crtNodes.get(i);
            Hashtable rlist = (Hashtable) results.get(n);
            if (rlist == null) {
                rlist = new Hashtable();
                results.put(n, rlist);
            }
            for (Enumeration enCh = name2title.keys(); enCh.hasMoreElements();) {
                String key = (String) enCh.nextElement();
                String title = (String) name2title.get(key);

                String[] params = null;
                if (title.equals("Cluster Usage")) {
                    params = seriesNamesFarmsUsage;
                } else if (title.equals("Number of CPUs")) {
                    params = seriesNamesCPUs;
                } else if (title.equals("Total Jobs")) {
                    params = seriesTotalJobs;
                } else {
                    if (newModuleDetected.contains(n)) {
                        params = seriesNamesJOBSNew;
                    } else {
                        params = seriesNamesJOBS;
                    }
                }

                for (int ser = 0; ser < params.length; ser++) {
                    //					System.out.println("Value for "+n.UnitName+"@"+key+"/"+params[ser]+" ?");
                    Value val = (Value) rlist.get(key + "/" + params[ser]);
                    double v = 0;
                    if (val != null) {
                        v = val.val;
                    }
                    //System.out.println("Plotting "+n.UnitName+":"+key+"/"+params[ser]+" = "+v);
                    combinedChartPanel.setAsyncValue(title, ser, i, v);
                }
            }
        }
        //System.out.println("updateData: end");
        //		last_update = NTPDate.currentTimeMillis();
    }

    /** called when ususal data changes in client... a good way to catch
     * events like nodes(farms) adding / removing.
     */
    @Override
    public void gupdate() {

        if (NRC != vnodes.size()) {
            shouldRedraw = true;
            //			//System.out.println("updateRC");
            //			updateRC();
        }
    }

    @Override
    public void setNodes(Map<ServiceID, rcNode> nodes, Vector<rcNode> vnodes) {
        this.nodes = nodes;
        this.vnodes = vnodes;
    }

    @Override
    public void setSerMonitor(SerMonitorBase ms) {
        this.monitor = ms;
    }

    @Override
    public void setMaxFlowData(rcNode n, Vector v) {
        // empty
    }

    @Override
    public void new_global_param(String name) {
        // empty
    }

    @Override
    public void componentHidden(ComponentEvent e) {
        // empty
    }

    @Override
    public void componentMoved(ComponentEvent e) {
        // empty
    }

    @Override
    public void componentResized(ComponentEvent e) {
        redrawAll();
        //		setCCSize();
        //		combinedChartPanel.redraw();
        //shouldRedraw = true;
    }

    @Override
    public void componentShown(ComponentEvent e) {
        // empty
        redrawAll();
        //		updateRC();
        //		setCCSize();
        //		combinedChartPanel.redraw();
    }

    /** this will store the new result in the structures for easy plotting */
    void setResult(MLSerClient client, Result r) {

        if ((r == null) || (r.param_name == null) || (r.param == null)) {
            return;
        }
        //System.out.println("R:"+r);
        // VO_JOBS 
        rcNode n = nodes.get(client.tClientID);
        if (n == null) {
            return;
        }

        if (r.ClusterName.equals("VO_JOBS")) {
            if (newModuleDetected.contains(n)) {
                return;
            }
            Hashtable rlist = (Hashtable) results.get(n);
            if (rlist == null) {
                rlist = new Hashtable();
                results.put(n, rlist);
            }
            for (int i = 0; i < r.param.length; i++) {
                String param = r.param_name[i];
                boolean add = false;
                //				if(param.equals("Total Jobs")) add = true;
                //				else 
                if (param.equals("Idle Jobs")) {
                    add = true;
                } else if (param.equals("Running Jobs")) {
                    add = true;
                } else if (param.equals("Held Jobs")) {
                    add = true;
                }
                if (add) {
                    String key = /*r.ClusterName+"/"+*/r.NodeName + "/" + param;
                    Value v = (Value) rlist.get(key);
                    if (v == null) {
                        v = new Value(r.param[i]);
                        shouldRedraw = true;
                        rlist.put(key, v);
                    } else if (v.val != r.param[i]) {
                        v.val = r.param[i];
                        shouldRedraw = true;
                    }
                }
            }
            return;
        }
        // Number of CPUs for each farm
        if (r.ClusterName.startsWith("PN")) {
            Hashtable rlist = (Hashtable) results.get(n);
            if (rlist == null) {
                rlist = new Hashtable();
                results.put(n, rlist);
            }
            for (int i = 0; i < r.param.length; i++) {
                String param = r.param_name[i];
                if (param.equals("NoCPUs")) {
                    String key = r.ClusterName + "/" + r.NodeName + "/" + param;
                    Value v = (Value) rlist.get(key);
                    if (v == null) {
                        v = new Value(r.param[i]);
                        shouldRedraw = true;
                        rlist.put(key, v);
                    } else if (v.val != r.param[i]) {
                        v.val = r.param[i];
                        shouldRedraw = true;
                    }
                }
            }
            return;
        }
        if (r.ClusterName.equals("osgVO_JOBS") || r.ClusterName.equals("LcgVO_JOBS")) {
            Hashtable rlist = (Hashtable) results.get(n);
            if (rlist == null) {
                rlist = new Hashtable();
                results.put(n, rlist);
            }
            for (int i = 0; i < r.param.length; i++) {
                String param = r.param_name[i];
                boolean add = false;
                //				if(param.equals("Total Jobs")) add = true;
                //				else 
                if (param.equals("IdleJobs")) {
                    add = true;
                } else if (param.equals("RunningJobs")) {
                    add = true;
                } else if (param.equals("HeldJobs")) {
                    add = true;
                }
                if (add) {
                    if (!newModuleDetected.contains(n)) {
                        newModuleDetected.add(n);
                        Vector v = new Vector();
                        for (Enumeration en = rlist.keys(); en.hasMoreElements();) {
                            String key = (String) en.nextElement();
                            if ((key.indexOf("Idle Jobs") != -1) || (key.indexOf("Running Jobs") != -1)
                                    || (key.indexOf("Held Jobs") != -1)) {
                                v.add(key);
                            }
                        }
                        for (int ii = 0; ii < v.size(); ii++) {
                            rlist.remove(v.get(ii));
                        }
                    }
                    String key = /*r.ClusterName+"/"+*/r.NodeName + "/" + param;
                    Value v = (Value) rlist.get(key);
                    if (v == null) {
                        v = new Value(r.param[i]);
                        shouldRedraw = true;
                        rlist.put(key, v);
                    } else if (v.val != r.param[i]) {
                        v.val = r.param[i];
                        shouldRedraw = true;
                    }
                }
            }
            return;
        }
    }

    /** this is called when a new result is received */
    @Override
    public void newFarmResult(MLSerClient client, Object ro) {
        if (ro == null) {
            return;
        }
        //System.out.println("vojobs result: "+ro);
        if (ro instanceof Result) {
            Result r = (Result) ro;
            //logger.log(Level.INFO, "VOJobs Result from "+client.farm.name+" = "+r);
            setResult(client, r);
        } else if (ro instanceof eResult) {
            //	System.out.println(" Got eResult " + ro);
        } else if (ro instanceof Vector) {
            Vector vr = (Vector) ro;
            //System.out.println(new Date()+" V["+vr.size()+"] from "+client.farm.name);
            for (int i = 0; i < vr.size(); i++) {
                newFarmResult(client, vr.elementAt(i));
            }
        } else {
            logger.log(Level.WARNING, "Wrong Result type in VoJob from " + client.farm.name + ": " + ro);
            return;
        }

        //		long now = NTPDate.currentTimeMillis();
        //		if ( now - last_update > 4000 ) {
        //			last_update = now;
        //			if(isVisible()){
        //				updateData();
        //			}
        //		 }
    }

    /** called when a checkbox state changes */
    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            JCheckBox cb = (JCheckBox) e.getSource();
            if (cb.isSelected()) {
                addChart(cb.getText());
            } else {
                removeChart(cb.getText());
            }
        } catch (ClassCastException ex) {
            System.out.println("event not from a checkbox");
        }
    }
}
