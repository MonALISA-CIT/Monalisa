package lia.Monitor.JiniClient.Farms.Histograms;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.Map;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JPanel;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.graphical;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.Result;
import lia.util.ntp.NTPDate;
import net.jini.core.lookup.ServiceID;
import plot.BarChartPanel;

public class Traff extends JPanel implements graphical, ComponentListener {

    /**
     * 
     */
    private static final long serialVersionUID = -8405238160495182139L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(Traff.class.getName());

    SerMonitorBase monitor;
    Map<ServiceID, rcNode> nodes;
    Vector crtWconn;
    Vector<rcNode> vnodes;

    String[] labels = { "IN", "OUT" };
    double[] min = { 0, 0 };
    double[] max = { 1, 1 };
    int[] count = { 1, 1 };
    BarChartPanel barChart;
    protected boolean shouldRedraw;
    protected int invisibleUpdates;
    long linkExpiringInterval = 5 * 60 * 1000;

    public Traff() {
        barChart = new BarChartPanel("WAN Traffic", "WAN Links", "Mb/s", labels, min, max, count, false);
        barChart.changeDepth(3, 4);
        setLayout(new BorderLayout());
        add(barChart, BorderLayout.CENTER);
        crtWconn = new Vector();
        addComponentListener(this);
        startTimer();
    }

    private void startTimer() {
        TimerTask ttask = new TimerTask() {
            @Override
            public void run() {
                try {
                    checkWLinks();
                    updateWLinksName();
                    updateWLinksData();
                    if (isVisible()) {
                        if (shouldRedraw) {
                            barChart.setLowerMargin(0.5 / (1.0 + crtWconn.size()));
                            barChart.setUpperMargin(0.5 / (1.0 + crtWconn.size()));
                            shouldRedraw = false;
                            barChart.redrawChart();
                        }
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Error executing", t);
                }
            }
        };
        BackgroundWorker.schedule(ttask, 4000, 6000);
    }

    @Override
    public void updateNode(rcNode node) {
        // empty
    }

    public void newData(Result r, rcNode ns) {
        //System.out.println("WLINK from "+ns.UnitName+": "+r);
        if ((r.param_name == null) || (r.param == null)) {
            return;
        }
        for (int i = 0; i < r.param_name.length; i++) {
            String na = r.param_name[i];
            int ji = na.lastIndexOf("_");
            if ((ji == -1) || (!(na.endsWith("_IN") || na.endsWith("_OUT")))) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Got invalid wan link from farm:" + ns.UnitName + "\n" + r);
                }
                continue;
            }
            String rna = na.substring(0, ji);
            WLink link = getWLink(rna, ns);
            link.time = NTPDate.currentTimeMillis();
            if (na.indexOf("_IN") != -1) {
                if (link.in != r.param[i]) {
                    link.in = r.param[i];
                    shouldRedraw = true;
                }
            } else {
                if (link.out != r.param[i]) {
                    link.out = r.param[i];
                    shouldRedraw = true;
                }
            }
        }
    }

    private void addWLink(WLink link) {
        for (int i = 0; i < crtWconn.size(); i++) {
            WLink ol = (WLink) crtWconn.get(i);
            if (ol.name.compareToIgnoreCase(link.name) > 0) {
                crtWconn.add(i, link);
                shouldRedraw = true;
                return;
            }
        }
        crtWconn.add(link);
        shouldRedraw = true;
    }

    private WLink getWLink(String name, rcNode baseNode) {
        for (int i = 0; i < crtWconn.size(); i++) {
            WLink link = (WLink) crtWconn.get(i);
            if (link.name.equals(name)) {
                return link;
            }
        }
        WLink link = new WLink(name, baseNode);
        addWLink(link);
        return link;
    }

    public void removeWLinksFrom(rcNode baseNode) {
        for (int i = 0; i < crtWconn.size(); i++) {
            WLink link = (WLink) crtWconn.get(i);
            if (link.baseNode.equals(baseNode)) {
                crtWconn.remove(i);
                i--;
            }
        }
        shouldRedraw = true;
    }

    public void setLinkExpiringInterval(long expiringInterval) {
        linkExpiringInterval = expiringInterval;
    }

    private void checkWLinks() {
        long now = NTPDate.currentTimeMillis();
        for (int i = 0; i < crtWconn.size(); i++) {
            WLink link = (WLink) crtWconn.get(i);
            if ((!vnodes.contains(link.baseNode)) || ((now - link.time) > linkExpiringInterval)) {
                crtWconn.remove(i);
                i--;
                shouldRedraw = true;
            }
        }
    }

    private void updateWLinksName() {
        for (int i = 0; i < crtWconn.size(); i++) {
            WLink link = (WLink) crtWconn.get(i);
            barChart.addXTick(link.name, i);
        }
        barChart.setCategoriesNumber(crtWconn.size());
    }

    private void updateWLinksData() {
        for (int i = 0; i < crtWconn.size(); i++) {
            WLink link = (WLink) crtWconn.get(i);
            barChart.setAsyncValue(0, i, link.in);
            barChart.setAsyncValue(1, i, link.out);
        }
    }

    @Override
    public void gupdate() {
        // empty
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
        Dimension d = getSize();
        barChart.setDimension(d);
    }

    @Override
    public void componentShown(ComponentEvent e) {
        checkWLinks();
        updateWLinksName();
        updateWLinksData();
        barChart.setLowerMargin(0.5 / (1.0 + crtWconn.size()));
        barChart.setUpperMargin(0.5 / (1.0 + crtWconn.size()));
        barChart.redrawChart();
    }
}
