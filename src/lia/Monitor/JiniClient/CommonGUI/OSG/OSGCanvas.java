package lia.Monitor.JiniClient.CommonGUI.OSG;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.text.NumberFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;

import javax.swing.JPanel;
import javax.swing.ToolTipManager;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphTopology;
import lia.Monitor.JiniClient.CommonGUI.Gmap.SpringLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Sphere.SphereTexture;
import lia.Monitor.tcpClient.tClient;
import net.jini.core.lookup.ServiceID;

/**
 * The class that draws the objects in the panel.
 */
public class OSGCanvas extends JPanel implements MouseListener, MouseMotionListener {

    static final long serialVersionUID = 281020051L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(OSGCanvas.class.getName());

    public double minValue;
    public double maxValue;
    public Color minColor;
    public Color maxColor;

    Vector<rcNode> currentNodes;

    rcNode pick;
    rcNode pickX;

    OSGPanel owner;
    Color bgColor = Color.WHITE; //new Color(230, 230, 250);
    static Font nodesFont = new Font("Arial", Font.PLAIN, 10);
    Color clHiFTP = new Color(255, 100, 100);
    int wunit = 0;
    int hunit = 0;
    Rectangle range = new Rectangle();
    Rectangle actualRange = new Rectangle();

    /** The current graph topology */
    GraphTopology vGraph = null;

    public HashMap nodeParams; // map node -> HashMap ( map param string -> double[] values )

    private final Vector links;

    private final Cursor moveCursor = new Cursor(Cursor.MOVE_CURSOR);
    private final Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);

    public static final NumberFormat df = NumberFormat.getInstance();
    private static final NumberFormat zf = NumberFormat.getInstance();

    static {
        zf.setMinimumFractionDigits(0);
        zf.setMaximumFractionDigits(0);
        df.setMinimumFractionDigits(2);
        df.setMaximumFractionDigits(2);
    }

    public boolean showShadow = false;
    public boolean showSphere = false;
    Image shadow;
    Image bump;
    static ConvolveOp blurOp;
    final static Color shadowColor = new Color(0.0f, 0.0f, 0.0f, 0.3f);

    public OSGCanvas(OSGPanel owner) {

        this.owner = owner;
        currentNodes = new Vector();
        nodeParams = new HashMap();
        links = new Vector();
        addMouseListener(this);
        setBackground(bgColor);

        blurOp = getBlurOp(7);

        minValue = 0.0;
        maxValue = 100.0;
        minColor = Color.green;
        maxColor = clHiFTP;
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    public void setNodes(Map<ServiceID, rcNode> nodes) {

        // first add the new nodes
        for (final rcNode n : nodes.values()) {
            if (!currentNodes.contains(n)) {
                currentNodes.add(n);
                try {
                    owner.ftpHelper.addNode(n);
                    owner.jobHelper.addNode(n);
                } catch (Throwable t) {
                    logger.warning("OSGCanvas -  Got exception " + t);
                }
            }
        }

        // after delete nodes
        for (int i = 0; i < currentNodes.size(); i++) {
            rcNode n = currentNodes.get(i);
            if (!nodes.containsKey(n.sid)) {
                currentNodes.remove(i);
                owner.ftpHelper.deleteNode(n);
                owner.jobHelper.deleteNode(n);
                i--;
            } else {
                rcNode nn1 = nodes.get(n.sid);
                if (!nn1.equals(n)) {
                    currentNodes.remove(i);
                    owner.ftpHelper.deleteNode(n);
                    owner.jobHelper.deleteNode(n);
                    i--;
                }
            }
        }
        owner.repaint();
        owner.redoTopology();
    }

    void rescaleIfNeeded() {
        range.setBounds(0, 0, getWidth(), getHeight());
        range.grow(-wunit, -hunit);
        boolean firstNode = true;
        for (int i = 0; i < currentNodes.size(); i++) {
            rcNode n = currentNodes.get(i);
            if (firstNode) {
                firstNode = false;
                actualRange.setBounds(n.osgX, n.osgY, 1, 1);
            } else {
                actualRange.add(n.osgX, n.osgY);
            }
        }
        if (firstNode) {
            return;
        }
        double zx = actualRange.getWidth() / range.getWidth();
        double zy = actualRange.getHeight() / range.getHeight();

        if (zx > 1) {
            actualRange.width = (int) (actualRange.width / zx);
        }
        if (zy > 1) {
            actualRange.height /= (int) (actualRange.height / zy);
        }

        double dx = 0;
        double dy = 0;

        if (actualRange.x < range.x) {
            dx = range.x - actualRange.x;
        }
        if (actualRange.getMaxX() > range.getMaxX()) {
            dx = range.getMaxX() - actualRange.getMaxX();
        }
        if (actualRange.y < range.y) {
            dy = range.y - actualRange.y;
        }
        if (actualRange.getMaxY() > range.getMaxY()) {
            dy = range.getMaxY() - actualRange.getMaxY();
        }

        for (int i = 0; i < currentNodes.size(); i++) {
            rcNode n = currentNodes.get(i);
            if (zx > 1) {
                n.osgX = (int) (n.osgX / zx);
            }
            if (zy > 1) {
                n.osgY = (int) (n.osgY / zy);
            }
            if (n.fixed) {
                continue;
            }
            n.osgX = (int) (n.osgX + dx);
            n.osgY = (int) (n.osgY + dy);
            if (vGraph != null) {
                GraphNode gn = (GraphNode) vGraph.nodesMap.get(n);
                if (gn != null) {
                    gn.pos.setLocation(n.osgX, n.osgY);
                }
            }
        }
    }

    //	public synchronized void paint(Graphics g) {
    //		if ( this.getParent().isVisible() )
    //		    update(g);
    //	}

    @Override
    public synchronized void paintComponent(Graphics g) {
        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Dimension d = getSize();
        links.clear();
        g.setColor(bgColor);
        g.setFont(nodesFont);
        g.fillRect(0, 0, d.width, d.height);
        FontMetrics fm = g.getFontMetrics();

        if (owner.currentLayout.equals("None")) {
            rescaleIfNeeded();
        }

        double minPerformance = Double.MAX_VALUE;
        double maxPerformance = Double.MIN_VALUE;

        Vector alreadyPainted = new Vector();

        for (int i = 0; i < currentNodes.size(); i++) {
            rcNode n = currentNodes.get(i);

            if (tClient.isOSgroup(n.mlentry.Group) || (vGraph == null)) {
                continue;
            }
            ExtendedGraphNode nn = (ExtendedGraphNode) vGraph.getGN(n);
            if (nn == null) {
                continue;
            }
            Hashtable nbs = nn.neighbors;
            Hashtable nbstr = nn.neighborsStrings;
            for (Enumeration en = nbs.keys(); en.hasMoreElements();) {
                GraphNode node = (GraphNode) en.nextElement();
                rcNode n1 = node.rcnode;
                if (tClient.isOSgroup(n1.mlentry.Group) || (vGraph == null) || !currentNodes.contains(n1)) {
                    continue;
                }
                double val = ((Double) nbs.get(node)).doubleValue();
                Color cc;
                //				if (Math.abs(val) < 0.001 ) {
                //					cc = Color.RED;			// error link
                //					lbl = "?";
                //				} else {
                cc = getColor(val);
                //				}
                if (val < minPerformance) {
                    minPerformance = val;
                }
                if (val > maxPerformance) {
                    maxPerformance = val;
                }
                if (!nbstr.containsKey(node)) {
                    String str = df.format(val);
                    drawConn(g, n, n1, str, cc, 0);
                } else {
                    String str = (String) nbstr.get(node);
                    String[] vals = str.split("/");
                    if ((vals != null) && !vals[0].equals("0.00")) {
                        drawConn(g, n, n1, vals[0], cc, 0);
                    }
                    if ((vals != null) && !vals[1].equals("0.00")) {
                        drawConn(g, n1, n, vals[1], cc, 0);
                    }
                }
                if (!alreadyPainted.contains(n)) {
                    alreadyPainted.add(n);
                }
                if (!alreadyPainted.contains(n1)) {
                    alreadyPainted.add(n1);
                }
            }
        }

        boolean r = true;
        if (minPerformance >= maxPerformance) {
            owner.cmdPane.csFTP.setColors(Color.GREEN, Color.GREEN);
            if (minPerformance == Double.MAX_VALUE) {
                owner.cmdPane.csFTP.setValues(0, 0);
                r = false;
            }
        } else {
            owner.cmdPane.csFTP.setColors(Color.GREEN, clHiFTP); // red(min) --> yellow(max)
        }
        if (r) {
            owner.cmdPane.csFTP.setValues(minPerformance, 2 * maxPerformance);
            maxValue = maxPerformance;
            minValue = minPerformance;
        }

        for (int i = 0; i < currentNodes.size(); i++) {
            rcNode n = currentNodes.get(i);
            if (owner.onlyModule && !haveOsgModules(n) && !alreadyPainted.contains(n)) {
                continue;
            }
            if ((n.mlentry != null) && tClient.isOSgroup(n.mlentry.Group)) {
                continue;
            }
            if (!owner.showOnlyConnectedNodes) {
                paintNode(g, n, fm);
            } else if (alreadyPainted.contains(n)) {
                paintNode(g, n, fm);
            }
        }
    }

    public boolean haveOsgModules(rcNode n) {
        return n.client.farm.getModuleList().contains("monOsgVoJobs")
                || n.client.farm.getModuleList().contains("monOsgVO_IO");
    }

    public Color getColor(double val) {
        if (val == -1) {
            return null;
        }

        double delta = Math.abs(maxValue - minValue);
        if (Math.abs(delta) < 1E-5) {
            return minColor;
        }
        int R, G, B;
        if (maxValue > minValue) {
            R = (int) (((val - minValue) * (maxColor.getRed() - minColor.getRed())) / delta) + minColor.getRed();
            G = (int) (((val - minValue) * (maxColor.getGreen() - minColor.getGreen())) / delta) + minColor.getGreen();
            B = (int) (((val - minValue) * (maxColor.getBlue() - minColor.getBlue())) / delta) + minColor.getBlue();
        } else {
            R = (int) (((val - maxValue) * (minColor.getRed() - maxColor.getRed())) / delta) + maxColor.getRed();
            G = (int) (((val - maxValue) * (minColor.getGreen() - maxColor.getGreen())) / delta) + maxColor.getGreen();
            B = (int) (((val - maxValue) * (minColor.getBlue() - maxColor.getBlue())) / delta) + maxColor.getBlue();
        }

        if (R < 0) {
            R = 0;
        }
        if (R > 255) {
            R = 255;
        }
        if (G < 0) {
            G = 0;
        }
        if (G > 255) {
            G = 255;
        }
        if (B < 0) {
            B = 0;
        }
        if (B > 255) {
            B = 255;
        }

        return new Color(R, G, B);
    }

    private void drawString(Graphics gg, String str, int x, int y) {

        if ((owner.ftpTransferType == OSGFTPHelper.FTP_INPUT) || (owner.ftpTransferType == OSGFTPHelper.FTP_INPUT_RATE)) {
            gg.setColor(OSGConstants.IN_COLOR);
            gg.drawString(str + " kB", x, y);
            return;
        }

        if ((owner.ftpTransferType == OSGFTPHelper.FTP_OUTPUT)
                || (owner.ftpTransferType == OSGFTPHelper.FTP_OUTPUT_RATE)) {
            gg.setColor(OSGConstants.OUT_COLOR);
            gg.drawString(str + " kB", x, y);
            return;
        }

        FontMetrics fm = gg.getFontMetrics();
        String p[] = str.split("/");
        if ((p == null) || (p.length < 2)) {
            gg.setColor(Color.black);
            if (owner.ftpTransferType == OSGFTPHelper.FTP_INOUT) {
                gg.drawString(str + " kB", x, y);
            }
            if (owner.ftpTransferType == OSGFTPHelper.FTP_INOUT_RATE) {
                gg.drawString(str + " kB/s", x, y);
            }
            return;
        }
        gg.setColor(OSGConstants.IN_COLOR);
        gg.drawString(p[0], x, y);
        x += fm.stringWidth(p[0]);
        gg.setColor(Color.black);
        gg.drawString("/", x, y);
        x += fm.stringWidth("/");
        gg.setColor(OSGConstants.OUT_COLOR);
        gg.drawString(p[1], x, y);
    }

    /**
     * Position of arrow is computed based on nOrder: if 0, is centered, with a dimension of 6,
     * if 1 or more, is shifted up or down a little bit 
     * @param gg
     * @param n
     * @param n1
     * @param lbl
     * @param col
     * @param nOrder
     */
    public void drawConn(Graphics gg, rcNode n, rcNode n1, String lbl, Color col, int nOrder) {

        final int DD = 6;
        int dd;
        if (nOrder == 0) {
            dd = DD;
        } else {
            dd = 4 * nOrder;
            //final int dd = 6;
        }

        gg.setColor(col);

        if (n.equals(n1)) {
            if (n.osgLimits == null) {
                return;
            }
            final int arcx = (n.osgLimits.width / 2) + 30;
            final int arcy = 30;
            gg.drawOval(n.osgLimits.x + (n.osgLimits.width / 2), (n.osgLimits.y + (n.osgLimits.height / 2))
                    - (arcy / 2), arcx, arcy);
            int x = n.osgLimits.x + (n.osgLimits.width / 2) + arcx;
            int y = n.osgLimits.y + (n.osgLimits.height / 2);
            int[] axv = { x, x - 6, x + 6, x };
            int[] ayv = { y, y + 8, y + 8, y };
            gg.fillPolygon(axv, ayv, 4);
            if ((lbl != null) && (lbl.length() != 0)) {
                //int ddl = 6;
                // String lbl = "" + (int) perf ;
                FontMetrics fm = gg.getFontMetrics();
                int x1 = x + 8;
                int y1 = y + (fm.getHeight() / 2);
                drawString(gg, lbl, x1, y1);
            }
            Link link = new Link(n.osgLimits.x + (n.osgLimits.width / 2), (n.osgLimits.y + (n.osgLimits.height / 2))
                    - (arcy / 2), n, arcx, arcy);
            links.add(link);
            return;
        }

        int dx = (n1.osgX - n.osgX);
        int dy = n1.osgY - n.osgY;
        float l = (float) Math.sqrt((dx * dx) + (dy * dy));
        float dir_x = dx / l;
        float dir_y = dy / l;
        int dd1 = dd;

        int x1p = n.osgX - (int) (dd1 * dir_y);
        int x2p = n1.osgX - (int) (dd1 * dir_y);
        int y1p = n.osgY + (int) (dd1 * dir_x);
        int y2p = n1.osgY + (int) (dd1 * dir_x);

        Link link = new Link(x1p, y1p, x2p, y2p, n, n1);
        links.add(link);

        gg.drawLine(x1p, y1p, x2p, y2p);

        int xv = (int) ((x1p + x2p + (((x1p - x2p) * (float) nOrder) / 20.0f)) / 2.0f);
        int yv = (int) ((y1p + y2p + (((y1p - y2p) * (float) nOrder) / 20.0f)) / 2.0f);

        float aa;//(float) (dd) / (float) 2.0;
        if (nOrder == 0) {
            aa = dd / 2.0f;
        } else {
            aa = 2.0f;
        }

        int[] axv = { (int) ((xv - (aa * dir_x)) + (2 * aa * dir_y)), (int) (xv - (aa * dir_x) - (2 * aa * dir_y)),
                (int) (xv + (2 * aa * dir_x)), (int) ((xv - (aa * dir_x)) + (2 * aa * dir_y)) };

        int[] ayv = { (int) (yv - (aa * dir_y) - (2 * aa * dir_x)), (int) ((yv - (aa * dir_y)) + (2 * aa * dir_x)),
                (int) (yv + (2 * aa * dir_y)), (int) (yv - (aa * dir_y) - (2 * aa * dir_x)) };

        gg.fillPolygon(axv, ayv, 4);

        if ((lbl != null) && (lbl.length() != 0)) {
            //int ddl = 6;
            // String lbl = "" + (int) perf ;
            FontMetrics fm = gg.getFontMetrics();
            int wl = fm.stringWidth(lbl) + 1;
            int hl = fm.getHeight() + 1;

            int off = 2;

            int xl = xv;
            int yl = yv;

            if ((dir_x >= 0) && (dir_y < 0)) {
                xl = xl + off;
                yl = yl + hl;
            }
            if ((dir_x <= 0) && (dir_y > 0)) {
                xl = xl - wl - off;
                yl = yl - off;
            }

            if ((dir_x > 0) && (dir_y >= 0)) {
                xl = xl - wl - off;
                yl = yl + hl;
            }
            if ((dir_x < 0) && (dir_y < 0)) {
                xl = xl + off;
                yl = yl - off;
            }

            drawString(gg, lbl, xl, yl);
        }
        gg.setColor(Color.black);
    }

    public void paintNode(Graphics g, rcNode n, FontMetrics fm) {

        final int size = 12; // minimum size
        final int maxSize = 20;
        final int fillSize = 7;

        int x = n.osgX;
        int y = n.osgY;
        String nodeName = n.shortName;

        double[] nod = null;
        double[] cpu = null;
        double[] io = null;
        double[] jobs = null;
        double[] fjobs = null;
        double totalCpuTime = 0.0;
        HashMap voCpu = null;

        synchronized (nodeParams) {
            if (nodeParams.containsKey(n)) {
                HashMap h = (HashMap) nodeParams.get(n);
                if (h.containsKey(owner.paramHelper.nodesString)) {
                    nod = (double[]) h.get(owner.paramHelper.nodesString);
                }
                if (h.containsKey(owner.paramHelper.cpuString)) {
                    cpu = (double[]) h.get(owner.paramHelper.cpuString);
                }
                if (h.containsKey(owner.paramHelper.ioString)) {
                    io = (double[]) h.get(owner.paramHelper.ioString);
                }
                if (h.containsKey(owner.paramHelper.jobsString)) {
                    jobs = (double[]) h.get(owner.paramHelper.jobsString);
                }
                if (h.containsKey(owner.paramHelper.fJobsString)) {
                    fjobs = (double[]) h.get(owner.paramHelper.fJobsString);
                }
                if (h.containsKey(owner.paramHelper.cpuTimeString)) {
                    voCpu = (HashMap) h.get(owner.paramHelper.cpuTimeString);
                    if (voCpu != null) {
                        totalCpuTime = ((Double) voCpu.get("Total")).doubleValue();
                    }
                }
            }
        }

        if (nod == null) {
            setOSGLimits(n, x - size, y - size, 2 * size, 2 * size);
            if (showShadow) {
                ((Graphics2D) g).drawImage(shadow, n.osgLimits.x, n.osgLimits.y, null);
            }
            g.setColor(Color.yellow);
            g.fillOval(n.osgLimits.x, n.osgLimits.y, n.osgLimits.width, n.osgLimits.height);
            if (showSphere) {
                ((Graphics2D) g).drawImage(bump, n.osgLimits.x, n.osgLimits.y, null);
                //				Paint defaultPaint = ((Graphics2D)g).getPaint();
                //				SphereTexture.setTexture(g, n.osgLimits);
                //				g.fillOval(n.osgLimits.x, n.osgLimits.y, n.osgLimits.width, n.osgLimits.height);
                //				((Graphics2D)g).setPaint(defaultPaint);
            }
            g.setColor(Color.black);
            if (n.equals(pickX)) {
                g.setColor(Color.red);
            }
            g.drawOval(n.osgLimits.x, n.osgLimits.y, n.osgLimits.width, n.osgLimits.height);
            if (n.equals(pickX)) {
                g.drawOval(n.osgLimits.x - 1, n.osgLimits.y - 1, n.osgLimits.width, n.osgLimits.height);
            }
            g.setColor(Color.black);
            g.drawString(nodeName, (n.osgLimits.x + (n.osgLimits.width / 2)) - (fm.stringWidth(nodeName) / 2),
                    n.osgLimits.y + n.osgLimits.height + 3 + fm.getAscent());
            return;
        }

        int nrNodes = (int) nod[2];
        if (nrNodes == 0) {
            setOSGLimits(n, x - size, y - size, 2 * size, 2 * size);
            if (showShadow) {
                ((Graphics2D) g).drawImage(shadow, n.osgLimits.x, n.osgLimits.y, null);
            }
            g.setColor(Color.red);
            g.fillOval(n.osgLimits.x, n.osgLimits.y, n.osgLimits.width, n.osgLimits.height);
            if (showSphere) {
                ((Graphics2D) g).drawImage(bump, n.osgLimits.x, n.osgLimits.y, null);
                //				Paint defaultPaint = ((Graphics2D)g).getPaint();
                //				SphereTexture.setTexture(g, n.osgLimits);
                //				g.fillOval(n.osgLimits.x, n.osgLimits.y, n.osgLimits.width, n.osgLimits.height);
                //				((Graphics2D)g).setPaint(defaultPaint);
            }
            g.setColor(Color.black);
            if (n.equals(pickX)) {
                g.setColor(Color.red);
            }
            g.drawOval(n.osgLimits.x, n.osgLimits.y, n.osgLimits.width, n.osgLimits.height);
            if (n.equals(pickX)) {
                g.drawOval(n.osgLimits.x - 1, n.osgLimits.y - 1, n.osgLimits.width, n.osgLimits.height);
            }
            g.setColor(Color.black);
            g.drawString(nodeName, (n.osgLimits.x + (n.osgLimits.width / 2)) - (fm.stringWidth(nodeName) / 2),
                    n.osgLimits.y + n.osgLimits.height + 3 + fm.getAscent());
            return;
        }

        int middleDim = ((nrNodes * (maxSize - size)) / owner.paramHelper.maxNodes) + size;

        int dim = middleDim;
        int angle = 0;

        if (owner.onlyToolTip == false) {
            if ((io != null) && isNotAllZero(io)) {
                dim += fillSize;
            }
            if ((cpu != null) && isNotAllZero(cpu)) {
                dim += fillSize;
            }
            if ((jobs != null) && isNotAllZero(jobs)) {
                dim += fillSize;
            }
            if ((fjobs != null) && (fjobs[0] != 0.0) && (fjobs[0] == (fjobs[1] + fjobs[2]))) {
                dim += fillSize;
            }
            if ((voCpu != null) && (totalCpuTime != 0.0)) {
                dim += fillSize;
            }
        }

        setOSGLimits(n, x - dim, y - dim, 2 * dim, 2 * dim);

        if (showShadow) {
            ((Graphics2D) g).drawImage(shadow, n.osgLimits.x, n.osgLimits.y, null);
        }

        // first draw io
        if ((io != null) && isNotAllZero(io) && (owner.onlyToolTip == false)) {
            double in = io[0];
            double out = io[1];
            if ((in == 0.0) && (out == 0.0)) {
                in = out = 1.0;
            }
            angle = (int) ((360 * in) / (in + out));
            g.setColor(OSGConstants.IO_IN.color);
            g.fillArc(x - dim, y - dim, 2 * dim, 2 * dim, 0, angle);
            g.setColor(OSGConstants.IO_OUT.color);
            g.fillArc(x - dim, y - dim, 2 * dim, 2 * dim, angle, 361 - angle);
            g.setColor(Color.black);
            if (n.equals(pickX)) {
                g.setColor(Color.red);
            }
            g.drawOval(x - dim, y - dim, 2 * dim, 2 * dim);
            if (n.equals(pickX)) {
                g.drawOval(x - dim - 1, y - dim - 1, 2 * dim, 2 * dim);
            }
            dim -= fillSize;
        }

        // draw cpu
        if ((cpu != null) && isNotAllZero(cpu) && (owner.onlyToolTip == false)) {
            double usr = cpu[0];
            double sys = cpu[1];
            double idle = cpu[2];
            double err = cpu[3];
            if ((usr == 0.0) && (sys == 0.0) && (idle == 0.0) && (err == 0.0)) {
                usr = sys = idle = err = 1.0;
            }
            double total = usr + sys + idle + err;
            // draw cpu
            angle = (int) ((360 * usr) / total);
            g.setColor(OSGConstants.CPU_USR.color);
            g.fillArc(x - dim, y - dim, 2 * dim, 2 * dim, 0, angle);
            int angle1 = (int) ((360 * sys) / total);
            g.setColor(OSGConstants.CPU_SYS.color);
            g.fillArc(x - dim, y - dim, 2 * dim, 2 * dim, angle, angle1);
            angle += angle1;
            angle1 = (int) ((360 * idle) / total);
            g.setColor(OSGConstants.CPU_IDLE.color);
            g.fillArc(x - dim, y - dim, 2 * dim, 2 * dim, angle, angle1);
            angle += angle1;
            angle1 = 361 - angle;
            g.setColor(OSGConstants.CPU_ERR.color);
            g.fillArc(x - dim, y - dim, 2 * dim, 2 * dim, angle, angle1);
            g.setColor(Color.black);
            if (n.equals(pickX) && (io == null)) {
                g.setColor(Color.red);
            }
            g.drawOval(x - dim, y - dim, 2 * dim, 2 * dim);
            if (n.equals(pickX) && (io == null)) {
                g.drawOval(x - dim - 1, y - dim - 1, 2 * dim, 2 * dim);
            }
            dim -= fillSize;
        }

        // draw jobs
        if ((jobs != null) && isNotAllZero(jobs) && (owner.onlyToolTip == false)) {
            double jr = jobs[0];
            double ji = jobs[1];

            if ((jr == 0.0) && (ji == 0.0)) {
                jr = ji = 1.0;
            }
            double total = jr + ji;
            angle = (int) ((360 * jr) / total);
            g.setColor(OSGConstants.RUNNING_JOBS.color);
            g.fillArc(x - dim, y - dim, 2 * dim, 2 * dim, 0, angle);

            int angle1 = 361 - angle;
            g.setColor(OSGConstants.IDLE_JOBS.color);
            g.fillArc(x - dim, y - dim, 2 * dim, 2 * dim, angle, angle1);

            g.setColor(Color.black);
            if (n.equals(pickX) && (io == null) && (cpu == null)) {
                g.setColor(Color.red);
            }
            g.drawOval(x - dim, y - dim, 2 * dim, 2 * dim);
            if (n.equals(pickX) && (io == null) && (cpu == null)) {
                g.drawOval(x - dim - 1, y - dim - 1, 2 * dim, 2 * dim);
            }
            dim -= fillSize;
        }

        //draw fjobs
        if ((fjobs != null) && (fjobs[0] != 0.0) && (fjobs[0] == (fjobs[1] + fjobs[2])) && (owner.onlyToolTip == false)) {
            double fjs = fjobs[1];
            double fje = fjobs[2];
            if ((fjs == 0.0) && (fje == 0.0)) {
                fjs = fje = 1.0;
            }
            double total = fjs + fje;
            angle = (int) ((360 * fjs) / total);
            g.setColor(OSGConstants.FINISHED_S_JOBS.color);
            g.fillArc(x - dim, y - dim, 2 * dim, 2 * dim, 0, angle);
            int angle1 = 361 - angle;
            g.setColor(OSGConstants.FINISHED_E_JOBS.color);
            g.fillArc(x - dim, y - dim, 2 * dim, 2 * dim, angle, angle1);

            g.setColor(Color.black);
            if (n.equals(pickX) && (io == null) && (cpu == null) && (jobs == null)) {
                g.setColor(Color.red);
            }
            g.drawOval(x - dim, y - dim, 2 * dim, 2 * dim);
            if (n.equals(pickX) && (io == null) && (cpu == null) && (jobs == null)) {
                g.drawOval(x - dim - 1, y - dim - 1, 2 * dim, 2 * dim);
            }
            dim -= fillSize;
        }

        // draw CPU time consumed per VO 
        if ((voCpu != null) && (totalCpuTime != 0.0) && (owner.onlyToolTip == false)) {

            Color voColor = new Color(0, 0, 0);
            double voCpuTime = 0.0;
            int i = 0, angle1;

            for (Iterator it = voCpu.keySet().iterator(); it.hasNext();) {
                String voName = (String) it.next();
                if (voName.equals("Total")) {
                    continue;
                }
                voColor = OSGColor.getVoColor(voName);
                voCpuTime = ((Double) voCpu.get(voName)).doubleValue();
                if (i == 0) {
                    angle = (int) ((360 * voCpuTime) / totalCpuTime);
                    g.setColor(voColor);
                    g.fillArc(x - dim, y - dim, 2 * dim, 2 * dim, 0, angle);
                } else if (i == (voCpu.keySet().size() - 2)) {
                    angle1 = 360 - angle;
                    g.setColor(voColor);
                    g.fillArc(x - dim, y - dim, 2 * dim, 2 * dim, angle, angle1);
                } else {
                    angle1 = (int) ((360 * voCpuTime) / totalCpuTime);
                    g.setColor(voColor);
                    g.fillArc(x - dim, y - dim, 2 * dim, 2 * dim, angle, angle1);
                    angle += angle1;
                }
                i++;
            }

            /*angle = (int)(360 * cputime / totalCpuTime);
            g.setColor(voColor);
            g.fillArc(x-dim, y-dim, 2*dim, 2*dim, 0, angle);
            
            int angle1;
            for(int i = 1; i < voCpu.size()-1; i++){
            	info = (Object [])voCpu.get(i);
            	voColor = OSGColor.getVoColor((String)info[0]);
            	cputime = ((Double)info[1]).doubleValue();
            	
            	angle1 = (int)(360 * cputime / totalCpuTime);
            	g.setColor(voColor);
            	g.fillArc(x-dim, y-dim, 2*dim, 2*dim, angle, angle1);
            	angle += angle1;
            }
            info = (Object [])voCpu.get(voCpu.size()-2);
            voColor = OSGColor.getVoColor((String)info[0]);
            cputime = ((Double)info[1]).doubleValue();
            
            angle1 = 360 - angle;
            g.setColor(voColor);
            g.fillArc(x-dim, y-dim, 2*dim, 2*dim, angle, angle1);
            */

            g.setColor(Color.black);
            if (n.equals(pickX) && (io == null) && (cpu == null) && (jobs == null) && (fjobs == null)) {
                g.setColor(Color.red);
            }
            g.drawOval(x - dim, y - dim, 2 * dim, 2 * dim);
            if (n.equals(pickX) && (io == null) && (cpu == null) && (jobs == null) && (fjobs == null)) {
                g.drawOval(x - dim - 1, y - dim - 1, 2 * dim, 2 * dim);
            }
            dim -= fillSize;
        }

        // draw free and busy
        double busy = nod[1];
        double free = nod[0];
        if ((busy == 0) && (free == 0)) {
            free = busy = 1.0;
        }
        if (owner.onlyModule && !n.client.farm.getModuleList().contains("monOsgVoJobs")) {
            g.setColor(OSGConstants.CPU_NO.color);
            angle = 360;
            g.fillArc(x - dim, y - dim, 2 * dim, 2 * dim, 0, angle);
        } else {
            g.setColor(OSGConstants.FREE_NODES.color);
            angle = (int) ((360 * free) / (free + busy));
            g.fillArc(x - dim, y - dim, 2 * dim, 2 * dim, 0, angle);
            g.setColor(OSGConstants.BUSY_NODES.color);
            g.fillArc(x - dim, y - dim, 2 * dim, 2 * dim, angle, 361 - angle);
        }
        g.setColor(Color.black);
        if (n.equals(pickX) && (io == null) && (cpu == null) && (jobs == null) && (fjobs == null)) {
            g.setColor(Color.red);
        }
        g.drawOval(x - dim, y - dim, 2 * dim, 2 * dim);
        if (n.equals(pickX) && (io == null) && (cpu == null) && (jobs == null) && (fjobs == null)) {
            g.drawOval(x - dim - 1, y - dim - 1, 2 * dim, 2 * dim);
        }

        if (showSphere) {
            ((Graphics2D) g).drawImage(bump, n.osgLimits.x, n.osgLimits.y, null);
            //			Paint defaultPaint = ((Graphics2D)g).getPaint();
            //			SphereTexture.setTexture(g, n.osgLimits);
            //			g.fillOval(n.osgLimits.x, n.osgLimits.y, n.osgLimits.width, n.osgLimits.height);
            //			((Graphics2D)g).setPaint(defaultPaint);
        }

        g.setColor(Color.black);
        g.drawString(nodeName, (n.osgLimits.x + (n.osgLimits.width / 2)) - (fm.stringWidth(nodeName) / 2),
                n.osgLimits.y + n.osgLimits.height + 3 + fm.getAscent());
    }

    private boolean isNotAllZero(double[] values) {
        for (double value : values) {
            if (value != 0.0) {
                return true;
            }
        }
        return false;
    }

    private String getToolTipText(rcNode n) {

        double[] nod = null;
        double[] cpu = null;
        double[] cpuno = null;
        double[] io = null;
        double[] jobs = null;
        double[] fjobs = null;
        double totalCpuTime = 0.0;
        HashMap voCpu = null;

        synchronized (nodeParams) {
            if (nodeParams.containsKey(n)) {
                HashMap h = (HashMap) nodeParams.get(n);
                if (h.containsKey(owner.paramHelper.nodesString)) {
                    nod = (double[]) h.get(owner.paramHelper.nodesString);
                }
                if (h.containsKey(owner.paramHelper.cpuString)) {
                    cpu = (double[]) h.get(owner.paramHelper.cpuString);
                }
                if (h.containsKey(owner.paramHelper.cpuNoString)) {
                    cpuno = (double[]) h.get(owner.paramHelper.cpuNoString);
                }
                if (h.containsKey(owner.paramHelper.ioString)) {
                    io = (double[]) h.get(owner.paramHelper.ioString);
                }
                if (h.containsKey(owner.paramHelper.jobsString)) {
                    jobs = (double[]) h.get(owner.paramHelper.jobsString);
                }
                if (h.containsKey(owner.paramHelper.fJobsString)) {
                    fjobs = (double[]) h.get(owner.paramHelper.fJobsString);
                }
                if (h.containsKey(owner.paramHelper.cpuTimeString)) {
                    voCpu = (HashMap) h.get(owner.paramHelper.cpuTimeString);
                    if (voCpu != null) {
                        totalCpuTime = ((Double) voCpu.get("Total")).doubleValue();
                    }
                }
            } else {
                return null;
            }
        }

        OSGToolTip osgtt = new OSGToolTip("#FFFFFF");
        if (nod != null) {
            osgtt.addEntry("Nodes", OSGConstants.TOTAL_NODES.color, zf, nod[2]);
            osgtt.addEntry("Free", OSGConstants.FREE_NODES.color, zf, nod[0], (nod[0] / nod[2]) * 100);
            osgtt.addEntry("Busy", OSGConstants.BUSY_NODES.color, zf, nod[1], (nod[1] / nod[2]) * 100);
            if ((cpuno != null) && (cpuno[0] != 0)) {
                osgtt.addEntry("Number of CPUs", OSGConstants.CPU_NO.color, zf, cpuno[0]);
            }
            osgtt.changeBg();
        }

        if (cpu != null) {
            osgtt.addEntry("CPU Sys", OSGConstants.CPU_SYS.color, df, cpu[0]);
            osgtt.addEntry("CPU Usr", OSGConstants.CPU_USR.color, df, cpu[1]);
            osgtt.addEntry("CPU Idle", OSGConstants.CPU_IDLE.color, df, cpu[2]);
            osgtt.addEntry("CPU Err", OSGConstants.CPU_ERR.color, df, cpu[3]);
            osgtt.changeBg();
        }

        if (io != null) {
            osgtt.addEntry("TotalIO Rate In", OSGConstants.IO_IN.color, df, io[0]);
            osgtt.addEntry("TotalIO Rate Out", OSGConstants.IO_OUT.color, df, io[1]);
            osgtt.changeBg();
        }
        if (voCpu != null) {
            osgtt.addEntry("Number of VOs", OSGConstants.VOS_NUMBER.color, zf, voCpu.size() - 1);
            osgtt.addEntry("Total CPU time consumed [hours]", OSGConstants.CPU_TIME.color, df, totalCpuTime);
            if (totalCpuTime != 0.0) {
                for (Iterator it = voCpu.keySet().iterator(); it.hasNext();) {
                    String voName = (String) it.next();
                    if (voName.equals("Total")) {
                        continue;
                    }
                    double voCpuTime = ((Double) voCpu.get(voName)).doubleValue();
                    if (voCpuTime != 0.00) {
                        osgtt.addEntry("CPU TIME for " + voName + " ", OSGColor.getVoColor(voName), df, voCpuTime,
                                (voCpuTime / totalCpuTime) * 100);
                    }
                }
            }
            osgtt.changeBg();
        }

        if (jobs != null) {
            osgtt.addEntry("Running Jobs", OSGConstants.RUNNING_JOBS.color, zf, jobs[0],
                    (jobs[0] / (jobs[1] + jobs[0])) * 100);
            osgtt.addEntry("Idle Jobs", OSGConstants.IDLE_JOBS.color, zf, jobs[1],
                    (jobs[1] / (jobs[1] + jobs[0])) * 100);
            osgtt.changeBg();
        }
        if (fjobs != null) {
            osgtt.addEntry("Finished Jobs", OSGConstants.FINISHED_JOBS.color, zf, fjobs[0]);
            if ((fjobs[0] != 0.0) && (fjobs[0] == (fjobs[1] + fjobs[2]))) {
                osgtt.addEntry("Succes", OSGConstants.FINISHED_S_JOBS.color, zf, fjobs[1], (fjobs[1] / fjobs[0]) * 100);
                osgtt.addEntry("Errors", OSGConstants.FINISHED_E_JOBS.color, zf, fjobs[2], (fjobs[2] / fjobs[0]) * 100);
            }
            osgtt.changeBg();
        }
        return osgtt.getToolTip(n.UnitName);
    }

    /*	private String getToolTipText(Link l) {
    		
    		if (l.n2 != null)
    			return owner.ftpHelper.constructLinkHelper(l.n1, l.n2);
    		return owner.ftpHelper.constructLinkHelper(l.n1, l.n1);
    	}
    */
    @Override
    public String getToolTipText(MouseEvent e) {

        int x = e.getX();
        int y = e.getY();

        // check if the mouse if over a node
        for (int i = 0; i < currentNodes.size(); i++) {
            rcNode n = currentNodes.get(i);
            if (n == null) {
                continue;
            }
            if ((n.mlentry != null) && tClient.isOSgroup(n.mlentry.Group)) {
                continue;
            }
            n.selected = false;
            if (n.osgLimits == null) {
                continue;
            }
            if (owner.showOnlyConnectedNodes && (vGraph != null)) {
                GraphNode nn = vGraph.getGN(n);
                if ((nn.neighbors == null) || (nn.neighbors.size() == 0)) {
                    continue;
                }
            }
            if (n.osgLimits.contains(x, y)) {
                return getToolTipText(n);
            }
        }
        // check if the mouse if over a link
        //for (int i=0; i<links.size(); i++) {
        //	Link link = (Link)links.get(i);
        //	if (link.intersect(x, y)) {
        //		return getToolTipText(link);
        //	}
        //}
        return null;
    }

    private ConvolveOp getBlurOp(int size) {
        float[] data = new float[size * size];
        float value = 1 / (float) (size * size);
        for (int i = 0; i < data.length; i++) {
            data[i] = value;
        }
        return new ConvolveOp(new Kernel(size, size, data));
    }

    private BufferedImage createShadowMask(BufferedImage image) {
        BufferedImage mask = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int argb = image.getRGB(x, y);
                argb = ((int) (((argb >> 24) & 0xFF) * 0.5) << 24) | (shadowColor.getRGB() & 0x00FFFFFF);
                mask.setRGB(x, y, argb);
            }
        }
        return mask;
    }

    public static Image toImage(BufferedImage bufferedImage) {
        return Toolkit.getDefaultToolkit().createImage(bufferedImage.getSource());
    }

    private void createShadow(int w, int h) {

        BufferedImage bshadow = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration().createCompatibleImage(w + 14, h + 14, Transparency.TRANSLUCENT);
        Graphics2D g = bshadow.createGraphics();
        g.setColor(Color.cyan);
        g.fillOval(6, 6, w, h);
        blurOp.filter(createShadowMask(bshadow), bshadow);
        shadow = toImage(bshadow);
    }

    private void createBump(int w, int h) {

        BufferedImage bbump = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration().createCompatibleImage(w, h, Transparency.TRANSLUCENT);
        Graphics2D g = bbump.createGraphics();
        Rectangle rect = new Rectangle(0, 0, w, h);
        SphereTexture.setTexture(g, rect);
        g.fillOval(0, 0, w, h);
        bump = toImage(bbump);
    }

    private void setOSGLimits(rcNode n, int x, int y, int w, int h) {

        if ((w / 2) > wunit) {
            wunit = w / 2;
        }
        if ((h / 2) > hunit) {
            hunit = h / 2;
        }

        if (n.osgLimits == null) {
            n.osgLimits = new Rectangle(x, y, w, h);
        } else {
            n.osgLimits.setBounds(x, y, w, h);
        }
        if (showShadow) {
            createShadow(w, h);
        }
        if (showSphere) {
            createBump(w, h);
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {

        if (pick != null) {
            setCursor(moveCursor);
            int minx = wunit + 2;
            int miny = hunit + 2;
            int maxx = getWidth() - minx;
            int maxy = getHeight() - miny;
            int x = e.getX();
            int y = e.getY();

            if (x < minx) {
                x = minx;
            }
            if (y < miny) {
                y = miny;
            }
            if (x > maxx) {
                x = maxx;
            }
            if (y > maxy) {
                y = maxy;
            }

            pick.osgX = x;
            pick.osgY = y;
            if (vGraph != null) {
                GraphNode gn = (GraphNode) vGraph.nodesMap.get(pick);
                if (gn != null) {
                    gn.pos.setLocation(pick.osgX, pick.osgY);
                    // notify the thread that the position has changed
                    if (owner.layout instanceof SpringLayoutAlgorithm) {
                        ((SpringLayoutAlgorithm) owner.layout).notifyRunnerThread();
                    }
                }
            }
            repaint();
        } else {
            setCursor(defaultCursor);
        }
        e.consume();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {

        addMouseMotionListener(this);
        int x = e.getX();
        int y = e.getY();
        pick = null;
        for (int i = 0; i < currentNodes.size(); i++) {
            rcNode n = currentNodes.get(i);
            if (n == null) {
                continue;
            }
            if ((n.mlentry != null) && tClient.isOSgroup(n.mlentry.Group)) {
                continue;
            }
            n.selected = false;
            if (n.osgLimits == null) {
                continue;
            }
            if (owner.showOnlyConnectedNodes && (vGraph != null)) {
                GraphNode nn = vGraph.getGN(n);
                boolean found = false;
                for (int k = 0; k < currentNodes.size(); k++) {
                    rcNode n1 = currentNodes.get(k);
                    GraphNode nn1 = vGraph.getGN(n1);
                    if ((nn1.neighbors == null) || (nn1.neighbors.size() == 0)) {
                        continue;
                    }
                    for (Enumeration en1 = nn1.neighbors.keys(); en1.hasMoreElements();) {
                        GraphNode nn2 = (GraphNode) en1.nextElement();
                        if (nn2.rcnode.equals(n)) {
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        break;
                    }
                }
                if (!found) {
                    if ((nn.neighbors == null) || (nn.neighbors.size() == 0)) {
                        continue;
                    }
                }
            }
            //			System.out.println(n+" - "+n.osgLimits);
            if (n.osgLimits.contains(x, y)) {
                pick = n;
                //				System.out.println("pick = "+pick);
                pick.fixed = true;
                break;
            }
        }
        int but = e.getButton();

        if (but == MouseEvent.BUTTON3) {
            synchronized (OSGPanel.syncGraphObj) {
                pickX = pick;
            }
            if (owner.currentLayout.equals("Elastic") || owner.cmdPane.cbLayout.getSelectedItem().equals("Layered")
                    || owner.cmdPane.cbLayout.getSelectedItem().equals("Radial")) {
                owner.currentTransformCancelled = true;
                owner.setLayoutType((String) owner.cmdPane.cbLayout.getSelectedItem());
            }
        }

        if (but == MouseEvent.BUTTON1) {
            if (pick == null) {
                //setMaxFlow ( null ) ;  
            } else {
                // setMaxFlow ( pick );
                pick.selected = true;
                try {
                    pick.client.setVisible(!pick.client.isVisible());
                } catch (Throwable t) {
                    logger.warning("OSGCanvas -  Got exception " + t);
                }
                pick.fixed = false;
                pick = null;
                repaint();
            }
        }
        e.consume();
    }

    @Override
    public void mouseReleased(MouseEvent e) {

        setCursor(defaultCursor);
        removeMouseMotionListener(this);

        if ((pick != null) /*&& (e.getButton() == MouseEvent.BUTTON2)*/) {
            pick.fixed = false;
            //    pick = null;
        }
        // notify the thread that the position has changed
        if (owner.layout instanceof SpringLayoutAlgorithm) {
            ((SpringLayoutAlgorithm) owner.layout).notifyRunnerThread();
        }
        repaint();
        e.consume();
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        setCursor(defaultCursor);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        setCursor(defaultCursor);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        setCursor(defaultCursor);
    }

    static class Link {

        int x1, y1, x2, y2;
        rcNode n1;
        rcNode n2;
        int arcx, arcy;

        public Link(int x1, int y1, int x2, int y2, rcNode n1, rcNode n2) {

            this.x1 = x1;
            this.x2 = x2;
            this.y1 = y1;
            this.y2 = y2;
            this.n1 = n1;
            this.n2 = n2;
        }

        public Link(int x, int y, rcNode n, int arcx, int arcy) {

            this.x1 = x;
            this.y1 = y;
            this.n1 = n;
            this.n2 = null;
            this.arcx = arcx;
            this.arcy = arcy;
        }

        public boolean intersect(int x, int y) {

            if (n2 != null) {
                int dist = (int) (Math.abs(((x2 - x1) * (y1 - y)) - ((x1 - x) * (y2 - y1))) / Math
                        .sqrt(((x2 - x1) * (x2 - x1)) + ((y2 - y1) * (y2 - y1))));
                //				if (dist < 2) {
                //					System.out.println(n1.shortName+" - "+n2.shortName);
                //				}
                return (dist < 2);
            }
            return ((x > x1) && (x < (x1 + arcx)) && (y > y1) && (y < (y1 + arcy)));
        }
    }

} // end of class OSGCanvas

