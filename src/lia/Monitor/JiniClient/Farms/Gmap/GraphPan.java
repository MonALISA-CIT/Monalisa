package lia.Monitor.JiniClient.Farms.Gmap;

import java.awt.Color;
import java.awt.Component;
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
import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JPanel;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.DLink;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.pie;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GeographicLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphTopology;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GridLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.LayeredTreeLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.LayoutChangedListener;
import lia.Monitor.JiniClient.CommonGUI.Gmap.LayoutTransformer;
import lia.Monitor.JiniClient.CommonGUI.Gmap.NoLayoutLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.RadialLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.RandomLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.SpringLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Sphere.SphereTexture;
import lia.Monitor.JiniClient.Farms.FarmsSerMonitor;
import lia.Monitor.tcpClient.tClient;
import net.jini.core.lookup.ServiceID;

public class GraphPan extends JPanel implements MouseListener, MouseMotionListener, LayoutChangedListener {
    /**
     * 
     */
    private static final long serialVersionUID = -276331220186205146L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(GraphPan.class.getName());

    volatile Map<ServiceID, rcNode> nodes;
    public Vector maxFlow;

    rcNode pick;
    rcNode pickX;

    //OpticalConnectivityToolTip OSConToolTip = new OpticalConnectivityToolTip();

    //	Image offscreen;
    //	Dimension offscreensize;
    //	Graphics2D offgraphics;
    SerMonitorBase monitor;
    GmapPan gmapPan;
    Color bgColor = Color.WHITE; //new Color(230, 230, 250);
    static Font nodesFont = new Font("Arial", Font.PLAIN, 10);
    Color clHiPing = new Color(255, 100, 100);
    int wunit = 0;
    int hunit = 0;
    String pieKey = "noLoadPie";
    Rectangle range = new Rectangle();
    Rectangle actualRange = new Rectangle();

    private final DecimalFormat lblFormat = new DecimalFormat("###,###.#");

    // layout & stuff
    GraphLayoutAlgorithm layout = null;
    GraphTopology vGraph = null;
    Object syncGraphObj = new Object();
    LayoutTransformer layoutTransformer;
    String currentLayout = "None";
    boolean currentTransformCancelled = false;
    boolean showOnlyConnectedNodes;

    static Image shadow;
    static Image bump;
    static ConvolveOp blurOp;
    final static Color shadowColor = new Color(0.0f, 0.0f, 0.0f, 0.3f);

    public GraphPan(GmapPan gmapPan) {
        this.gmapPan = gmapPan;
        addMouseListener(this);
        //addMouseMotionListener(this);
        setBackground(bgColor);
        TimerTask ttask = new LayoutUpdateTimerTask(this.getParent());
        BackgroundWorker.schedule(ttask, 4000, 4000);
        layoutTransformer = new LayoutTransformer(this);
    }

    class LayoutUpdateTimerTask extends TimerTask {
        Component parent;

        public LayoutUpdateTimerTask(Component parent) {
            this.parent = parent;
        }

        @Override
        public void run() {
            Thread.currentThread().setName(" ( ML ) - Farms Gmap - GraphPan layout update Timer Thread");
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

    public void setNodesTab(Map<ServiceID, rcNode> nodes) {
        this.nodes = nodes;
    }

    public void setSerMonitor(SerMonitorBase monitor) {
        this.monitor = monitor;
    }

    public void setPie(String k) {
        pieKey = k;
    }

    public void setMaxFlow(rcNode n) {
        setMaxFlowData(n, ((FarmsSerMonitor) monitor).getMaxFlow(n));
    }

    public void setMaxFlowData(rcNode n, Vector v) {
        maxFlow = v;
    }

    public boolean isHotLink(rcNode n1, rcNode n2) {
        if (maxFlow == null) {
            return false;
        }
        for (int i = 0; i < maxFlow.size(); i++) {
            DLink dl = (DLink) maxFlow.elementAt(i);
            if (dl.cequals(n1.sid, n2.sid)) {
                return true;
            }
        }
        return false;
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

    private ConvolveOp getBlurOp(int size) {
        float[] data = new float[size * size];
        float value = 1 / (float) (size * size);
        for (int i = 0; i < data.length; i++) {
            data[i] = value;
        }
        return new ConvolveOp(new Kernel(size, size, data));
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
        if (blurOp == null) {
            blurOp = getBlurOp(7);
        }
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

    public void paintNode(Graphics g, rcNode n, FontMetrics fm) {
        int x = n.x;
        int y = n.y;
        String nodeName = n.szOpticalSwitch_Name == null ? n.shortName : n.szOpticalSwitch_Name;
        //System.out.println("painting "+n.UnitName+" "+n.x+" "+n.y);

        int w2 = (fm.stringWidth(nodeName) + 15) / 2;
        int h2 = (fm.getHeight() + 10) / 2;
        boolean update = false;
        if (w2 > wunit) {
            wunit = w2;
            update = true;
        }
        if (h2 > hunit) {
            update = true;
            hunit = h2;
        }

        if (update) {
            createShadow(wunit * 2, hunit * 2);
            createBump(wunit * 2, hunit * 2);
        }

        if (n.limits == null) {
            n.limits = new Rectangle(x - wunit, y - hunit, wunit * 2, hunit * 2);
        } else {
            n.limits.setBounds(x - wunit, y - hunit, wunit * 2, hunit * 2);
        }

        if (gmapPan.optPan.kbShadow.isSelected()) {
            ((Graphics2D) g).drawImage(shadow, n.limits.x, n.limits.y, null);
        }

        if (n.errorCount > 0) { // || (n.haux.get("lostConn") != null && n.haux.get("lostConn").equals("1") )){
            g.setColor(Color.red);
            g.fillOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
            if (gmapPan.optPan.kbMakeNice.isSelected()) {
                ((Graphics2D) g).drawImage(bump, n.limits.x, n.limits.y, null);
            }
        } else {
            pie px = (pie) n.haux.get(pieKey);
            if ((px == null) || (px.len == 0)) {
                g.setColor(Color.yellow);
                g.fillOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
                if (gmapPan.optPan.kbMakeNice.isSelected()) {
                    ((Graphics2D) g).drawImage(bump, n.limits.x, n.limits.y, null);
                }
            } else {
                int u1 = 0;
                for (int i = 0; i < px.len; i++) {
                    g.setColor(px.cpie[i]);
                    int u2 = (int) (px.rpie[i] * 360);
                    g.fillArc(n.limits.x, n.limits.y, n.limits.width, n.limits.height, u1, u2);
                    u1 += u2;
                }
                if (gmapPan.optPan.kbMakeNice.isSelected()) {
                    ((Graphics2D) g).drawImage(bump, n.limits.x, n.limits.y, null);
                }
            }
        }

        g.setColor(Color.black);
        if (n.equals(pickX)) {
            g.setColor(Color.red);
        }
        g.drawOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
        if (n.equals(pickX)) {
            g.drawOval(n.limits.x - 1, n.limits.y - 1, n.limits.width, n.limits.height);
        }
        g.drawString(nodeName, (n.limits.x + 7 + wunit) - w2, ((n.limits.y + 5 + hunit) - h2) + fm.getAscent());
    }

    public void drawConn(Graphics gg, rcNode n, rcNode n1, String lbl, Color col) {
        drawConn(gg, n, n1, lbl, col, 0);
    }

    /**
     * nOrder has values:
     * 0 - means that default value for dd is used: 6
     * 1-.. - means that a value of 4*nOrder is used for dd
     * Also, position of arrow is computed based on nOrder: if 0, is centered, with a dimension of 6,
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
        }

        gg.setColor(col);

        int dx = (n1.x - n.x);
        int dy = n1.y - n.y;
        float l = (float) Math.sqrt((dx * dx) + (dy * dy));
        float dir_x = dx / l;
        float dir_y = dy / l;
        int dd1 = dd;

        int x1p = n.x - (int) (dd1 * dir_y);
        int x2p = n1.x - (int) (dd1 * dir_y);
        int y1p = n.y + (int) (dd1 * dir_x);
        int y2p = n1.y + (int) (dd1 * dir_x);

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

            gg.drawString(lbl, xl, yl);
        }
        gg.setColor(Color.black);
    }

    void setColors4ABPing() {
        if (nodes == null) {
            return;
        }
        if (nodes.size() == 0) {
            return;
        }

        double minPerformance = Double.MAX_VALUE;
        double maxPerformance = Double.MIN_VALUE;
        double perf, lp;
        rcNode n;
        for (rcNode ns : nodes.values()) {
            if (ns.conn.size() > 0) {
                //ILink link = new ILink("");
                for (Enumeration e1 = ns.conn.keys(); e1.hasMoreElements();) {
                    n = (rcNode) e1.nextElement();
                    perf = ns.connPerformance(n);
                    lp = ns.connLP(n);
                    if (lp == 1.0) {
                        continue;
                    }
                    if (perf < minPerformance) {
                        minPerformance = perf;
                    }
                    if (perf > maxPerformance) {
                        maxPerformance = perf;
                    }
                }
            }
        }
        if (minPerformance >= maxPerformance) {
            gmapPan.optPan.csPing.setColors(Color.GREEN, Color.GREEN); // red(min) --> yellow(max)
            if (minPerformance == Double.MAX_VALUE) {
                gmapPan.optPan.csPing.setValues(0, 0);
                return;
            }
        } else {
            gmapPan.optPan.csPing.setColors(Color.GREEN, clHiPing); // red(min) --> yellow(max)
        }
        gmapPan.optPan.csPing.setValues(minPerformance, maxPerformance);
    }

    Color getColor4ABPing(double val) {
        return gmapPan.optPan.csPing.getColor(val);
    }

    @Override
    public synchronized void paintComponent(Graphics g) {
        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                gmapPan.optPan.kbMakeNice.isSelected() ? RenderingHints.VALUE_ANTIALIAS_ON
                        : RenderingHints.VALUE_ANTIALIAS_OFF);

        Dimension d = getSize();
        g.setColor(bgColor);
        g.setFont(nodesFont);
        g.fillRect(0, 0, d.width, d.height);

        if (currentLayout.equals("None")) {
            rescaleIfNeeded();
        }
        int i = 0;
        for (final rcNode n : nodes.values()) {
            if (tClient.isOSgroup(n.mlentry.Group)) {
                continue;
            }
            for (Enumeration e1 = n.conn.keys(); e1.hasMoreElements();) {
                rcNode n1 = (rcNode) e1.nextElement();

                if (showOnlyConnectedNodes && (!n.isLayoutHandled || !n1.isLayoutHandled)) {
                    continue;
                }

                double val = n.connPerformance(n1);
                String lbl = lblFormat.format(val);
                Color cc;
                if (n.connLP(n1) == 1.0) {
                    cc = Color.RED; // error link
                    lbl = "?";
                } else {
                    cc = getColor4ABPing(val);
                }
                if (isHotLink(n, n1)) {
                    cc = Color.blue;
                } else if (!gmapPan.optPan.kbShowPing.isSelected()) {
                    continue;
                }
                drawConn(g, n, n1, lbl, cc);
            }
            //for each os link, check to see if already put
        }

        FontMetrics fm = g.getFontMetrics();
        for (final rcNode n : nodes.values()) {
            if ((n.mlentry != null) && tClient.isOSgroup(n.mlentry.Group)) {
                continue;
            }
            if (!showOnlyConnectedNodes || n.isLayoutHandled) {
                paintNode(g, n, fm);
            }
        }
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
        for (final rcNode n : nodes.values()) {
            if (n == null) {
                continue;
            }
            if ((n.mlentry != null) && tClient.isOSgroup(n.mlentry.Group)) {
                continue;
            }
            n.selected = false;
            if ((n.limits == null) || (showOnlyConnectedNodes && !n.isLayoutHandled)) {
                continue;
            }
            if (n.limits.contains(x, y)) {
                pick = n;
                pick.fixed = true;
                break;
            }
        }
        int but = e.getButton();

        if (but == MouseEvent.BUTTON3) {
            synchronized (syncGraphObj) {
                pickX = pick;
            }
            setMaxFlow(pickX);
            if (currentLayout.equals("Elastic") || gmapPan.cbLayout.getSelectedItem().equals("Layered")
                    || gmapPan.cbLayout.getSelectedItem().equals("Radial")) {
                currentTransformCancelled = true;
                setLayoutType((String) gmapPan.cbLayout.getSelectedItem());
            }
        }

        if (but == MouseEvent.BUTTON1) {
            if (pick == null) {
                //setMaxFlow ( null ) ;  
            } else {
                // setMaxFlow ( pick );
                pick.selected = true;
                pick.client.setVisible(!pick.client.isVisible());
                pick.fixed = false;
                pick = null;
                repaint();
            }
        }
        e.consume();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        removeMouseMotionListener(this);

        if ((pick != null) /*&& (e.getButton() == MouseEvent.BUTTON2)*/) {
            pick.fixed = false;
            //    pick = null;
        }
        // notify the thread that the position has changed
        if (layout instanceof SpringLayoutAlgorithm) {
            ((SpringLayoutAlgorithm) layout).notifyRunnerThread();
        }
        repaint();
        e.consume();
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (pick != null) {
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

            pick.x = x;
            pick.y = y;
            if (vGraph != null) {
                GraphNode gn = (GraphNode) vGraph.nodesMap.get(pick);
                if (gn != null) {
                    gn.pos.setLocation(pick.x, pick.y);
                    // notify the thread that the position has changed
                    if (layout instanceof SpringLayoutAlgorithm) {
                        ((SpringLayoutAlgorithm) layout).notifyRunnerThread();
                    }
                }
            }
            repaint();
        }
        e.consume();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        //System.out.println("mouse moved inside gmap");
        /*		int x = e.getX();
        		int y = e.getY();
        		rcNode nodeOver = null;
        		for (Enumeration en = nodes.elements(); en.hasMoreElements();) {
        			rcNode n = (rcNode) en.nextElement();
        			n.selected = false;
        			
        			if (n == null || n.limits == null || (showOnlyConnectedNodes && !n.isLayoutHandled))
        					continue; 
        			if ( n.limits.contains(x, y) && n.szOpticalSwitch_Name!=null ) {//mouse over node and node si optical switch
        				//System.out.println("(x,y)=("+x+","+y+") for node "+n.UnitName);
        			    nodeOver = n;
        				break;
        			}
        		}
        		if ( nodeOver!=null ) {
        		    if ( nodeOver != OSConToolTip.getNode() ) {
        				//System.out.println("show tooltip for node "+nodeOver.UnitName);
        				OSConToolTip.setForTesting(true);
        		        OSConToolTip.setNode( nodeOver);
        		        OSConToolTip.showPopup( this, x, y);
        		    };
        		} else
        		    if ( OSConToolTip.isVisible() ) {
        		        OSConToolTip.hidePopup();
        		        OSConToolTip.setNode(null);
        		    }
        		e.consume();
        */}

    void rescaleIfNeeded() {
        //		synchronized(syncRescale){
        range.setBounds(0, 0, getWidth(), getHeight());
        range.grow(-wunit, -hunit);
        boolean firstNode = true;
        for (final rcNode n : nodes.values()) {
            if (firstNode) {
                firstNode = false;
                actualRange.setBounds(n.x, n.y, 1, 1);
            } else {
                actualRange.add(n.x, n.y);
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

        for (final rcNode n : nodes.values()) {
            if (zx > 1) {
                n.x = (int) (n.x / zx);
            }
            if (zy > 1) {
                n.y = (int) (n.y / zy);
            }
            if (n.fixed) {
                continue;
            }
            n.x = (int) (n.x + dx);
            n.y = (int) (n.y + dy);
            if (vGraph != null) {
                GraphNode gn = (GraphNode) vGraph.nodesMap.get(n);
                if (gn != null) {
                    gn.pos.setLocation(n.x, n.y);
                }
            }
        }
        //		}
    }

    @Override
    public void computeNewLayout() {
        synchronized (syncGraphObj) {
            if (!(layout instanceof NoLayoutLayoutAlgorithm)) {
                currentLayout = "SomeLayout";
            }
            //System.out.println("enter "+currentLayout+" class "+layout.getClass().getName());
            range.setBounds(0, 0, getWidth(), getHeight());
            range.grow(-wunit, -hunit);
            layout.layOut();

            if (layout instanceof NoLayoutLayoutAlgorithm) { //currentLayout.equals("None")){
                //System.out.println("exit1 "+currentLayout+" class "+layout.getClass().getName());
                vGraph = null;
                layout = null;
                repaint();
                return;
            }
            // transform smoothly from the current positions to the destination
            long TRANSF_TOTAL_TIME = 2000; // 3 seconds
            long STEP_DELAY = 30; // 30 millis
            long nSteps = TRANSF_TOTAL_TIME / STEP_DELAY;

            // convert positions from relative [-1, 1] to [range]
            for (Iterator it = vGraph.gnodes.iterator(); it.hasNext();) {
                GraphNode gn = (GraphNode) it.next();
                if (!gn.rcnode.fixed) {
                    gn.pos.x = range.x + (int) ((range.width * (1.0 + gn.pos.x)) / 2.0);
                    gn.pos.y = range.y + (int) ((range.height * (1.0 + gn.pos.y)) / 2.0);
                }
            }
            currentTransformCancelled = false;
            // perform transitions
            for (int i = 0; (i < nSteps) && !currentTransformCancelled; i++) {
                //		    System.out.println("transition "+i);
                for (Iterator it = vGraph.gnodes.iterator(); it.hasNext();) {
                    GraphNode gn = (GraphNode) it.next();
                    if (!gn.rcnode.fixed) {
                        int dx = (int) ((gn.pos.x - gn.rcnode.x) / (nSteps - i));
                        int dy = (int) ((gn.pos.y - gn.rcnode.y) / (nSteps - i));
                        //				    if(gn.rcnode.UnitName.equals("CALTECH"))
                        //				        System.out.println("moving C["+gn.rcnode.x+", "+gn.rcnode.y+"] -> ["+gn.pos.x+", "+gn.pos.y+"]"); // by "+dx+", "+dy);
                        gn.rcnode.x += dx;
                        gn.rcnode.y += dy;
                    }
                }
                repaint();
                try {
                    Thread.sleep(STEP_DELAY);
                } catch (InterruptedException ex) {
                }
            }

            // final positions
            for (Iterator it = vGraph.gnodes.iterator(); it.hasNext();) {
                GraphNode gn = (GraphNode) it.next();
                if (!gn.rcnode.fixed) {
                    gn.rcnode.x = (int) gn.pos.x;
                    gn.rcnode.y = (int) gn.pos.y;
                }
            }
            //System.out.println("exit2 "+currentLayout+" class "+layout.getClass().getName());
            // invoke the NoLayout algorithm to recompute visibility for the other nodes
            layout = new NoLayoutLayoutAlgorithm(vGraph);
            layout.layOut();
            vGraph = null;
            layout = null;
            repaint();
            currentLayout = "None";
        }
    }

    @Override
    public int setElasticLayout() {
        //synchronized(syncRescale){
        range.setBounds(0, 0, getWidth(), getHeight());
        range.grow(-wunit - 2, -hunit - 2);
        //System.out.println("setting elastic layout");
        // set positions for the unhandled nodes
        //if(vGraph == layout.gt)
        int totalMovement = 0;
        for (Iterator it = vGraph.gnodes.iterator(); it.hasNext();) {
            GraphNode gn = (GraphNode) it.next();
            int nx = gn.rcnode.x;
            int ny = gn.rcnode.y;
            gn.rcnode.x = (int) Math.round(gn.pos.x);
            gn.rcnode.y = (int) Math.round(gn.pos.y);
            //				if(layout.handled.contains(gn)){
            if (gn.rcnode.x < range.x) {
                gn.rcnode.x = range.x;
            }
            if (gn.rcnode.y < range.y) {
                gn.rcnode.y = range.y;
            }
            if (gn.rcnode.x > range.getMaxX()) {
                gn.rcnode.x = (int) range.getMaxX();
            }
            if (gn.rcnode.y > range.getMaxY()) {
                gn.rcnode.y = (int) range.getMaxY();
            }
            gn.pos.setLocation(gn.rcnode.x, gn.rcnode.y);
            totalMovement += Math.abs(gn.rcnode.x - nx) + Math.abs(gn.rcnode.y - ny);
            //				}
        }
        repaint();
        return totalMovement;
    }

    void unfixNodes() {
        for (final rcNode n : nodes.values()) {
            n.fixed = false;
        }
    }

    //	public double getLinkValue(rcNode n1, rcNode n2) {
    //		double coef = 2.0;
    //		if(currentLayout.indexOf("tree") >= 0){
    //			if(! isHotLink(n1, n2))
    //				return -1;
    //			else 
    //				coef = 1.0;
    //		}
    //		return n1.connPerformance(n2) / 2;
    //	}

    void setLayoutType(String type) {
        //System.out.println("setLayoutType:"+type);
        if ((layout == null) && type.equals("Elastic")) {
            currentLayout = "None";
        }
        if ((layout != null) && !type.equals(currentLayout)) {
            currentLayout = "None";
            layout.finish();
        }
        boolean bUseInet;
        //boolean bUseOS;
        bUseInet = gmapPan.optPan.kbShowPing.isSelected();
        //bUseOS = gmapPan.optPan.kbShowOS.isSelected();
        if (type.equals("Random") || type.equals("Grid") || type.equals("Map") || type.equals("None")) {
            synchronized (syncGraphObj) {
                unfixNodes();
                vGraph = GraphTopology.constructGraphFromInetAndMaxFlow(nodes, maxFlow, bUseInet);//, bUseOS);
                if (type.equals("Random")) {
                    layout = new RandomLayoutAlgorithm(vGraph);
                } else if (type.equals("Grid")) {
                    layout = new GridLayoutAlgorithm(vGraph);
                } else if (type.equals("Map")) {
                    layout = new GeographicLayoutAlgorithm(vGraph);
                } else {
                    layout = new NoLayoutLayoutAlgorithm(vGraph);
                }
                layoutTransformer.layoutChanged();
            }
        } else if (type.equals("Radial") || type.equals("Layered")) {
            synchronized (syncGraphObj) {
                unfixNodes();
                vGraph = GraphTopology.constructGraphFromInetAndMaxFlow(nodes, maxFlow, bUseInet);//, bUseOS);
                if ((pickX == null) || (!nodes.containsValue(pickX))) {
                    pickX = vGraph.findARoot();
                }
                //System.out.println("layout "+type+" from "+pickX.UnitName);
                if (pickX != null) {
                    vGraph.pruneToTree(pickX); // convert the graph to a tree
                }
                if (type.equals("Radial")) {
                    layout = new RadialLayoutAlgorithm(vGraph, pickX);
                } else {
                    layout = new LayeredTreeLayoutAlgorithm(vGraph, pickX);
                }
                layoutTransformer.layoutChanged();
            }
        } else if (type.equals("Elastic")) {
            if (currentLayout.equals("Elastic")) {
                synchronized (syncGraphObj) {
                    vGraph = GraphTopology.constructGraphFromInetAndMaxFlow(nodes, maxFlow, bUseInet);//, bUseOS);
                    ((SpringLayoutAlgorithm) layout).updateGT(vGraph);
                }
            } else {
                synchronized (syncGraphObj) {
                    unfixNodes();
                    currentLayout = "Elastic";
                    vGraph = GraphTopology.constructGraphFromInetAndMaxFlow(nodes, maxFlow, bUseInet);//, bUseOS);
                    layout = new SpringLayoutAlgorithm(vGraph, this);
                    ((SpringLayoutAlgorithm) layout).setStiffness(gmapPan.sldStiffness.getValue());
                    ((SpringLayoutAlgorithm) layout).setRespRange(gmapPan.sldRepulsion.getValue());
                    layout.layOut();
                }
            }
        }
        //currentLayout = type;
    }
}
