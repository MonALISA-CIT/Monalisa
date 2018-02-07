package lia.Monitor.JiniClient.CommonGUI;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.Hashtable;
import java.util.Map;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JPanel;

import net.jini.core.lookup.ServiceID;
import plot.BarChartPanel;
import plot.StackedBarChartPanel;

/**
 * A generic histogram for Regional Center based graphs. 
 */
public class RCBasedHistogram extends JPanel implements graphical, ComponentListener {

    /**
     * 
     */
    private static final long serialVersionUID = -8805084466358466895L;

    private static final Logger logger = Logger.getLogger(RCBasedHistogram.class.getName());

    protected SerMonitorBase monitor;
    protected Map<ServiceID, rcNode> nodes;
    protected Vector<rcNode> vnodes;
    protected Vector crtNodes;
    protected Hashtable serValues; // the key is the rcNode
    protected int serSize; // number of series to be plotted
    protected TimerTask ttask; // the timerTask that should be rescheduled to redraw the plot

    int UPD_COUNT = 10;
    BarChartPanel barChart; // the chart
    int updCounter = 1; // counter used to update rarely the chart when it's not visible
    boolean shouldRedraw; // if a redraw should be performed
    String chartTitle; // title of this chart
    boolean panelResized = false; // when the panel was resized
    boolean chartChanged = false; // when chart changed between redraws
    boolean redrawPostponed = false;// if redraw was already postponed...
    boolean firstTime = true; // flag to mark the first run

    /**
     * @param title Title of the histogram
     * @param xAxisName X axis name
     * @param yAxisName Y axis name
     * @param labels list of labels for each value that is plotted
     * @param isVertical direction flag
     */
    public RCBasedHistogram(String title, String xAxisName, String yAxisName, String[] labels, boolean isVertical) {

        this.serSize = labels.length;
        this.chartTitle = title;
        double[] min = new double[serSize];
        double[] max = new double[serSize];
        int[] count = new int[serSize];

        for (int i = 0; i < labels.length; i++) {
            min[i] = 0;
            max[i] = 1;
            count[i] = 1;
        }

        barChart = new StackedBarChartPanel(title, xAxisName, yAxisName, labels, min, max, count, isVertical);
        barChart.changeDepth(3, 4);
        setLayout(new BorderLayout());
        add(barChart, BorderLayout.CENTER);
        crtNodes = new Vector();
        serValues = new Hashtable();
        addComponentListener(this);

        ttask = new TimerTask() {
            @Override
            public void run() {
                try {
                    if (isVisible()) {
                        shouldRedraw = false;
                        if (updateRC()) {
                            shouldRedraw = true;
                        }
                        updateData();
                        if (shouldRedraw) {
                            //						System.out.println(chartTitle+": starting"+(isVisible()?" ":" HIDDEN ")+"redraw..." + new Date());
                            //						long start = NTPDate.currentTimeMillis();
                            updateNodesSerValues();
                            barChart.setLowerMargin(0.5 / (1.0 + crtNodes.size()));
                            barChart.setUpperMargin(0.5 / (1.0 + crtNodes.size()));
                            barChart.redrawChart();
                            //						long end = NTPDate.currentTimeMillis();
                            //						System.out.println(chartTitle+" ... finished"+(isVisible()?" ":" HIDDEN ")+"redraw in "+(end-start)+" ms");
                        }
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Error executing", t);
                }
            }
        };
    }

    @Override
    public void updateNode(rcNode node) {
        // empty
    }

    /**
     * called to check if the configuration - set of RCs has changed
     * @return if the RC config has changed
     */
    protected boolean updateRC() {
        if (vnodes == null) {
            return false;
        }
        boolean confModif = false;
        // check for removed nodes
        for (int i = 0; i < crtNodes.size(); i++) {
            rcNode n = (rcNode) crtNodes.get(i);
            if (!vnodes.contains(n)) {
                crtNodes.remove(i);
                serValues.remove(n);
                confModif = true;
                i--;
            }
        }
        for (int i = 0; i < vnodes.size(); i++) {
            rcNode nx = vnodes.get(i);
            if (!crtNodes.contains(nx)) {
                addCrtNode(nx);
                double vv[] = new double[serSize];
                for (int k = 0; k < serSize; k++) {
                    vv[k] = 0;
                }
                serValues.put(nx, vv);
                confModif = true;
            }
        }
        if (confModif) {
            barChart.setCategoriesNumber(crtNodes.size());
            String lastName = "";
            for (int i = 0; i < crtNodes.size(); i++) {
                rcNode nx = (rcNode) crtNodes.get(i);
                if (nx.shortName.equals(lastName.trim())) {
                    lastName = lastName + " ";
                } else {
                    lastName = nx.shortName;
                }
                barChart.addXTick(lastName, i);
                //				System.out.println("addXTick "+lastName+" at "+i);
            }
            barChart.redrawChart();
        }
        return confModif;
    }

    /**
     *  used to keep the crtNodes sorted
     */
    void addCrtNode(rcNode nn) {
        for (int i = 0; i < crtNodes.size(); i++) {
            rcNode nc = (rcNode) crtNodes.get(i);
            if (nc.UnitName.compareToIgnoreCase(nn.UnitName) > 0) {
                crtNodes.add(i, nn);
                return;
            }
        }
        crtNodes.add(nn);
    }

    /**
     * transmit all values to the barChart
     */
    void updateNodesSerValues() {
        for (int i = 0; i < crtNodes.size(); i++) {
            rcNode n = (rcNode) crtNodes.get(i);
            double[] vv = (double[]) serValues.get(n);
            if (vv != null) {
                //				System.out.println("setAsincValues for "+n.UnitName+" at "+i);
                for (int j = 0; j < serSize; j++) {
                    barChart.setAsyncValue(j, i, vv[j]);
                }
            }
        }
    }

    /**
     * set the value for a certain node/series
     * @param n node
     * @param ser series = 0...nr. of labels - 1
     * @param value value that should be plot
     */
    protected void setNodeSerValue(rcNode n, int ser, double value) {
        double[] vv = (double[]) serValues.get(n);
        if (vv == null) {
            logger.log(Level.FINE, "setNodeSerValueX: node is null!");
        } else {
            if (vv[ser] != value) {
                //System.out.println("ser="+ser+" oldValue="+vv[ser]+" new="+value);
                vv[ser] = value;
                shouldRedraw = true;
            }
        }
    }

    /**
     * set the value for a series with one value only
     * @param n
     * @param value
     */
    protected void setNodeSerValue(rcNode n, double value) {
        double[] vv = (double[]) serValues.get(n);
        if (vv == null) {
            logger.log(Level.FINE, "setNodeSerValue1: node is null!");
        } else {
            if (vv[0] != value) {
                //System.out.println("ser=0 oldValue="+vv[0]+" new="+value);
                vv[0] = value;
                shouldRedraw = true;
            }
        }
    }

    /**
     * set the value for a series with two values
     * @param n
     * @param value1
     * @param value2
     */
    protected void setNodeSerValue(rcNode n, double value1, double value2) {
        double[] vv = (double[]) serValues.get(n);
        if (vv == null) {
            logger.log(Level.FINE, "setNodeSerValue2: node is null!");
        } else {
            if ((vv[0] != value1) || (vv[1] != value2)) {
                //System.out.println("ser=0 oldValues="+vv[0]+", "+vv[1]
                //		+" new="+value1+", "+value2);
                vv[0] = value1;
                vv[1] = value2;
                shouldRedraw = true;
            }
        }
    }

    /**
     * set the value for a series with three values
     * @param n
     * @param value1
     * @param value2
     * @param value3
     */
    protected void setNodeSerValue(rcNode n, double value1, double value2, double value3) {
        double[] vv = (double[]) serValues.get(n);
        if (vv == null) {
            logger.log(Level.FINE, "setNodeSerValue3: node is null!");
        } else {
            if ((vv[0] != value1) || (vv[1] != value2) || (vv[2] != value3)) {
                //System.out.println("n="+n.UnitName+" oldValues="+vv[0]+", "+vv[1]+", "+vv[2]
                //		+" new="+value1+", "+value2+", "+value3);
                vv[0] = value1;
                vv[1] = value2;
                vv[2] = value3;
                shouldRedraw = true;
            }
        }
    }

    /**
     * refresh plotted data - this should be redefined
     * setNodeSerValue method should be used
     * @return if the RC data has changed
     */
    protected boolean updateData() {
        for (int i = 0; i < crtNodes.size(); i++) {
            // 0 is the series number;
            // you should call from 0 to ... nr of labels-1
            // to add values for all series plotted
            setNodeSerValue((rcNode) crtNodes.get(i), 0, Math.random());
        }
        // if plotted data has changed, return true
        // else return false
        return true;
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
        //		System.out.println("RESIZED "+chartTitle+" to "+d);
        //		panelResized = true;
        barChart.setDimension(d);
    }

    @Override
    public void componentShown(ComponentEvent e) {
        updateRC();
        updateData();
        updateNodesSerValues();
        barChart.setLowerMargin(0.5 / (1.0 + crtNodes.size()));
        barChart.setUpperMargin(0.5 / (1.0 + crtNodes.size()));
        barChart.redrawChart();
    }
}