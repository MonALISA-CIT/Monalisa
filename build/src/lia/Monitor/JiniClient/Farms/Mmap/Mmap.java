package lia.Monitor.JiniClient.Farms.Mmap;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JComboBox;

import lia.Monitor.JiniClient.CommonGUI.MFilter2Constants;
import lia.Monitor.JiniClient.CommonGUI.NodeToolTipText;
import lia.Monitor.JiniClient.CommonGUI.pie;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Mmap.MapCanvasBase;
import lia.Monitor.monitor.ILink;
import lia.Monitor.monitor.Result;

public class Mmap extends MapCanvasBase {

    /**
     * 
     */
    private static final long serialVersionUID = 5914750313107117257L;

    private static final Logger logger = Logger.getLogger(Mmap.class.getName());

    JoptPan optPan;
    Color rtrColor = new Color(0, 170, 249);
    private final Object syncGparamCombobox = new Object();
    String pieKey = "noLoadPie";

    public Mmap() {
        super();
        optPan = new JoptPan(this);
        // initialize the color and format of the 2 combo boxes
        optPan.csPing.setColors(Color.GREEN, Color.GREEN); // red(min) --> yellow(max)
        optPan.csPing.setLabelFormat("###.##", "");
        optPan.csPing.setValues(0, 0);
        //Color lg = new Color(10, 255, 10);
        optPan.csWAN.setColors(Color.CYAN, Color.CYAN); //lg, lg);  // light green(min) --> green(max)
        optPan.csWAN.setLabelFormat("###.##", "");
        optPan.csWAN.setValues(0, 0);

        //set combo listener
        optPan.gparam.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if ((e == null) || (e.getSource() == null)) {
                    return;
                }
                JComboBox cb = (JComboBox) e.getSource();
                String paramName = (String) cb.getSelectedItem();
                if (paramName == null) {
                    return;
                }

                for (String element : MFilter2Constants.MenuAccept) {
                    if (paramName.equals(element)) {
                        gupdate();
                        return;
                    }
                }
            }
        });

        add(optPan, BorderLayout.NORTH);
    }

    @Override
    protected void plotOverImage(Graphics g) {
        updateABPingData();
        updateWANData();
        if (optPan.kbShowPing.isSelected()) {
            plotABPingTraffic(g);
        }
        if (optPan.kbShowWAN.isSelected()) {
            plotWANTraffic(g);
        }
        plotNodes(g);
    }

    @Override
    protected void plotNodes(Graphics g) {
        if (nodes == null) {
            return;
        }
        if (nodes.size() == 0) {
            return;
        }

        for (final rcNode n : nodes.values()) {
            if ((n.LAT != null) || (n.LONG != null)) {
                plotCN(n, g);
            }
        }
    }

    void updateABPingData() {
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
        for (final rcNode ns : nodes.values()) {
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
            optPan.csPing.setColors(Color.GREEN, Color.GREEN); // red(min) --> yellow(max)
            if (minPerformance == Double.MAX_VALUE) {
                optPan.csPing.setValues(0, 0);
                return;
            }
        } else {
            optPan.csPing.setColors(Color.GREEN, Color.YELLOW); // red(min) --> yellow(max)
        }
        optPan.csPing.setValues(minPerformance, maxPerformance);

    }

    void plotABPingTraffic(Graphics g) {
        if (nodes == null) {
            return;
        }
        if (nodes.size() == 0) {
            return;
        }

        ILink link = new ILink("");
        for (final rcNode ns : nodes.values()) {
            if ((ns.conn.size() > 0) && !ns.bHiddenOnMap) {
                for (Enumeration e1 = ns.conn.keys(); e1.hasMoreElements();) {
                    rcNode nw = (rcNode) e1.nextElement();
                    if (!nw.bHiddenOnMap) {
                        link.fromLAT = failsafeParseDouble(ns.LAT, -21.22D);
                        link.fromLONG = failsafeParseDouble(ns.LONG, -111.15D);
                        link.toLAT = failsafeParseDouble(nw.LAT, -21.22D);
                        link.toLONG = failsafeParseDouble(nw.LONG, -111.15D);
                        link.speed = ns.connPerformance(nw);
                        link.data = Double.valueOf(ns.connLP(nw));

                        Color col;
                        if (((Double) link.data).doubleValue() == 1.0) {
                            col = Color.RED;
                        } else {
                            col = optPan.csPing.getColor(link.speed);
                        }
                        plotILink(link, g, LINK_TYPE_DIRECTED, col);
                    }
                }
            }
        }
    }

    /**
     * this is called from plotCN in base class
     */
    @Override
    protected void plotCNWorker(rcNode n, int x, int y, Graphics g) {
        int R = 6;

        if (n.errorCount > 0) {
            g.setColor(Color.RED);
            g.fillArc(x - R, y - R, 2 * R, 2 * R, 0, 360);
            g.setColor(Color.WHITE);
            g.fillArc(x - 1, y - 1, 3, 3, 0, 360);
        } else {
            pie px = (pie) n.haux.get(pieKey);
            //pie px = (pie ) n.haux.get("LoadPie") ;
            if ((px == null) || (px.len == 0)) {
                g.setColor(Color.yellow);
                g.fillArc(x - R, y - R, 2 * R, 2 * R, 0, 360);
            } else {
                int u1 = 0;
                for (int i = 0; i < px.len; i++) {
                    g.setColor(px.cpie[i]);
                    int u2 = (int) (px.rpie[i] * 360);
                    g.fillArc(x - R, y - R, 2 * R, 2 * R, u1, u2);
                    u1 += u2;
                }
            }
        }
        g.setColor(Color.red);
        g.drawArc(x - R, y - R, 2 * R, 2 * R, 0, 360);
    }

    /*	//a version
     protected void plotCNWorker(rcNode n, int x, int y, Graphics g) {
     int R = 6 ;
     
     if (  n.errorCount > 0 )  {
     g.setColor( Color.RED );
     g.drawOval( x-3, y -3, 5, 5);
     } else  {
     pie px = (pie ) n.haux.get(pieKey) ;
     if ( ( px == null ) || ( px.len == 0 ) ) {
     g.setColor ( Color.yellow );
     g.drawOval( x-3, y -3, 5, 5);
     } else {
     g.setColor ( Color.green );
     g.drawOval( x-3, y -3, 5, 5);
     int width, height;//should be general for all nodes, influenced by zooming?
     width = 30;
     height = 10;
     int posx, posy;
     posx = x+10;
     posy = y-10;
     g.setColor( Color.DARK_GRAY);
     g.drawLine( x, y, posx, posy-2);
     g.drawLine( x, y, posx, posy-1);
     g.drawLine( x, y, posx, posy);
     g.drawLine( x, y, posx+1, posy);
     g.drawLine( x, y, posx+2, posy);
     g.setColor( Color.DARK_GRAY);
     g.drawRect( posx+1, posy-height, width+1, height+1);
     g.setColor( Color.BLACK);
     g.drawRect( posx, posy-height-1, width+1, height+1);
     int u1 = posx+1, u2= width;
     for ( int i=px.len-1; i >=0 ; i -- ) {
     g.setColor ( px.cpie[i] );
     g.fillRect( posx+1, posy-height, u2, height);
     u2 -= (int) (px.rpie[i] * width ) ;
     }
     }
     }
     g.setColor( Color.WHITE );
     g.drawLine( x, y, x, y);
     }
     */

    /*
     * updates min and max values for colored bar of WAN Traffic
     */
    void updateWANData() {
        if (nodes == null) {
            return;
        }
        if (nodes.size() == 0) {
            return;
        }

        double minPerformance = Double.MAX_VALUE;
        double maxPerformance = Double.MIN_VALUE;
        double minLimit = Double.MAX_VALUE;
        double maxLimit = Double.MIN_VALUE;
        double perf, lp;
        ILink link;
        for (final rcNode ns : nodes.values()) {
            if (ns.wconn.size() > 0) {
                //ILink link = new ILink("");
                for (Enumeration e1 = ns.wconn.elements(); e1.hasMoreElements();) {
                    Object objLink = e1.nextElement();
                    if (!(objLink instanceof ILink)) {
                        continue;
                    }
                    link = (ILink) objLink;
                    double v = ((Double) (link.data)).doubleValue();
                    if (v < minPerformance) {
                        minPerformance = v;
                    }
                    if (v > maxPerformance) {
                        maxPerformance = v;
                    }
                    v = link.speed;
                    if (v < minLimit) {
                        minLimit = v;
                    }
                    if (v > maxLimit) {
                        maxLimit = v;
                    }
                }
            }
        }
        if (minLimit > maxLimit) {
            optPan.csWAN.setLimitValues(0, 0);
        } else {
            optPan.csWAN.setLimitValues(minLimit, maxLimit);
        }
        if (minPerformance >= maxPerformance) {
            optPan.csWAN.setColors(Color.CYAN, Color.CYAN); // red(min) --> yellow(max)
            if (minPerformance == Double.MAX_VALUE) {
                optPan.csWAN.setValues(0, 0);
                return;
            }
        } else {
            optPan.csWAN.setColors(Color.CYAN, Color.BLUE); // red(min) --> yellow(max)
        }
        optPan.csWAN.setValues(minPerformance, maxPerformance);

    }

    void plotWANTraffic(Graphics g) {
        if (nodes == null) {
            return;
        }
        if (nodes.size() == 0) {
            return;
        }
        int linkType = LINK_TYPE_DIRECTED;
        if (optPan.kbShowWANVal.isSelected()) {
            linkType |= LINK_TYPE_SHOW_VALUE;
        }
        double deltaLimitVal = optPan.csWAN.maxLimitValue - optPan.csWAN.minLimitValue;
        for (final rcNode n : nodes.values()) {
            if ((n.wconn.size() > 0) && !n.bHiddenOnMap) {
                for (Enumeration e1 = n.wconn.elements(); e1.hasMoreElements();) {
                    Object objLink = e1.nextElement();
                    if (!(objLink instanceof ILink)) {
                        continue;
                    }
                    ILink link = (ILink) objLink;
                    //plotILink(link , g,	linkType, optPan.csWAN.minColor);
                    Stroke oldStroke = ((Graphics2D) g).getStroke();
                    if (deltaLimitVal > 0) {
                        ((Graphics2D) g)
                                .setStroke(new BasicStroke(
                                        (float) (1 + ((2.5 * (link.speed - optPan.csWAN.minLimitValue)) / (deltaLimitVal + 0.1)))));
                    }
                    ;
                    plotILink(link, g, linkType, optPan.csWAN.getColor(((Double) (link.data)).doubleValue()));

                    ((Graphics2D) g).setStroke(oldStroke);
                    paintRouters(g, link);
                }
            }
        }
    }

    void paintRouters(Graphics g, ILink link) {
        //      double xlat1 = link.fromLAT * 0.017453293;
        //      double xlat2 = link.toLAT * 0.017453293;
        //      double xlong1 = link.fromLONG * 0.017453293;
        //      double xlong2 = link.toLONG * 0.017453293;
        //      
        //      int klong1 = (int) img2panX(rad2imgX(xlong1));
        //      int klong2 = (int) img2panX(rad2imgX(xlong2));
        //      
        //      int klat1 = (int) img2panY(rad2imgY(xlat1));
        //      int klat2 = (int) img2panY(rad2imgY(xlat2));

        int klong1 = long2panX(link.from.get("LONG"));
        int klat1 = lat2panY(link.from.get("LAT"));
        int klong2 = long2panX(link.to.get("LONG"));
        int klat2 = lat2panY(link.to.get("LAT"));

        paintRouter(g, klong1, klat1);
        paintRouter(g, klong2, klat2);
    }

    void paintRouter(Graphics g, int x, int y) {
        int R = 6;
        g.setColor(rtrColor);
        g.fillOval(x - R, y - R, 2 * R, 2 * R);
        g.setColor(Color.WHITE);
        g.drawOval(x - R, y - R, 2 * R, 2 * R);
        g.drawLine(x - 1, y - 1, (x - R) + 3, (y - R) + 3);
        g.drawLine(x - 1, y + 1, (x - R) + 3, (y + R) - 3);
        g.drawLine(x + 1, y - 1, (x + R) - 3, (y - R) + 3);
        g.drawLine(x + 1, y + 1, (x + R) - 3, (y + R) - 3);
    }

    /**
     * get the tool tip text for the rcnode under the mouse cursor
     */
    @Override
    protected String getMapToolTipText(MouseEvent event) {
        int x = event.getX();
        int y = event.getY();
        int[] CheckPos = new int[2];
        CheckPos[0] = x;
        CheckPos[1] = y;
        if (nodes == null) {
            return null;
        }
        for (final rcNode nod : nodes.values()) {
            // check node tooltips
            if (nod == null) {
                return null;
            }
            if (nod.haux == null) {
                return null;
            }
            //check node visibility for gmap
            //go to next node under this one
            if (nod.bHiddenOnMap) {
                continue;
            }
            Point pos = (Point) nod.haux.get("MapPos");
            if (pos == null) {
                return null;
            }
            if ((Math.abs(pos.x - x) < 6) && (Math.abs(pos.y - y) < 6)) {
                return NodeToolTipText.getToolTip(nod);
            }
            // check WAN router tooltips
            if (nod.wconn.size() > 0) {
                for (Enumeration el = nod.wconn.elements(); el.hasMoreElements();) {
                    Object objLink = el.nextElement();
                    if (!(objLink instanceof ILink)) {
                        continue;
                    }
                    ILink link = (ILink) objLink;
                    int klong = long2panX(link.from.get("LONG"));
                    int klat = lat2panY(link.from.get("LAT"));
                    if ((Math.abs(klong - x) < 6) && (Math.abs(klat - y) < 6)) {
                        return "Router @ " + link.from.get("CITY") + " / " + link.from.get("COUNTRY");
                    }
                    klong = long2panX(link.to.get("LONG"));
                    klat = lat2panY(link.to.get("LAT"));
                    if ((Math.abs(klong - x) < 6) && (Math.abs(klat - y) < 6)) {
                        return "Router @ " + link.to.get("CITY") + " / " + link.to.get("COUNTRY");
                    }
                }
                //if checkbox is checked and for this node there are WAN connections to other nodes
                //for each one check to see if mouse pointer is in the small arrow for each link
                if (optPan.kbShowWAN.isSelected()) {
                    for (Enumeration e1 = nod.wconn.elements(); e1.hasMoreElements();) {
                        Object objLink = e1.nextElement();
                        if (!(objLink instanceof ILink)) {
                            continue;
                        }
                        ILink link = (ILink) objLink;
                        if (useILink(link, null, LINK_TYPE_DIRECTED, null, 1, CheckPos)) {
                            //if(link.name.indexOf("->") < 0){	
                            String text, shortText;
                            text = "WAN: "
                                    + link.name
                                    + " = "
                                    + optPan.csWAN.formatter.format(((Double) (link.data)).doubleValue())
                                    + " / "
                                    + optPan.csWAN.formatter.format(link.speed)
                                    + " Mbps -> "
                                    + optPan.csWAN.formatter.format((((Double) (link.data)).doubleValue() * 100)
                                            / link.speed) + " % ";
                            shortText = "WAN: "
                                    + link.name
                                    + " "
                                    + optPan.csWAN.formatter.format(((Double) (link.data)).doubleValue())
                                    + "Mbps ("
                                    + optPan.csWAN.formatter.format(((((Double) (link.data)).doubleValue()) * 100.0)
                                            / link.speed) + "%)";
                            return shortText;
                        }
                        ;
                    }
                }
            }
            ;
            //as above, only that it applies to ABping
            if (optPan.kbShowPing.isSelected() && (nod.conn.size() > 0)) {
                ILink link = new ILink("");
                for (Enumeration e1 = nod.conn.keys(); e1.hasMoreElements();) {
                    rcNode nw = (rcNode) e1.nextElement();
                    link.fromLAT = failsafeParseDouble(nod.LAT, -21.22D);
                    link.fromLONG = failsafeParseDouble(nod.LONG, -111.15D);
                    link.toLAT = failsafeParseDouble(nw.LAT, -21.22D);
                    link.toLONG = failsafeParseDouble(nw.LONG, -111.15D);
                    link.fromIP = nod.IPaddress;
                    link.toIP = nw.IPaddress;
                    link.inetQuality = ((Result) nod.conn.get(nw)).param;
                    //link.speed = nod.connPerformance(nw);
                    //link.data = Double.valueOf(nod.connLP(nw));
                    if (useILink(link, null, LINK_TYPE_DIRECTED, null, 1, CheckPos)) {
                        String text, shortText;
                        text = "ABPing: " + link.fromIP + "->" + link.toIP + ": RTTime= "
                                + optPan.csPing.formatter.format(link.inetQuality[0]) + ", RTT= "
                                + optPan.csPing.formatter.format(link.inetQuality[1]) + " ms, Lost Packages= "
                                + optPan.csPing.formatter.format(link.inetQuality[2] * 100) + " % ";
                        shortText = "ABPing: " + link.fromIP + "->" + link.toIP + ": RTT="
                                + optPan.csPing.formatter.format(link.inetQuality[1]) + " (Lost="
                                + optPan.csPing.formatter.format(link.inetQuality[2] * 100) + " %)";
                        //System.out.println(shortText);
                        return shortText;
                    }
                    ;
                }
            }
            ;
        }
        return null;
    }

    @Override
    public void gupdate() {

        String paramName = (String) optPan.gparam.getSelectedItem();
        if (paramName == null) {
            repaint();
            return;
        }

        if (paramName.equals(MFilter2Constants.MenuAccept[0])) {
            setPie("CPUPie");
        }
        if (paramName.equals(MFilter2Constants.MenuAccept[1])) {
            setPie("IOPie");
        }
        if (paramName.equals(MFilter2Constants.MenuAccept[2])) {
            setPie("LoadPie");
        }
        if (paramName.equals(MFilter2Constants.MenuAccept[3])) {
            setPie("DiskPie");
        }

        repaint();
    }

    void setPie(String key) {
        pieKey = key;
    }

    @Override
    public void new_global_param(String mod) {
        int k = -1;

        for (int i = 0; i < MFilter2Constants.acceptg.length; i++) {
            if (mod.equals(MFilter2Constants.acceptg[i])) {
                k = i;
                break;
            }
        }
        if (k < 0) {
            return;
        }

        synchronized (syncGparamCombobox) {
            int f = 0;
            for (int i = 0; i < optPan.gparam.getItemCount(); i++) {
                String pa2 = (String) optPan.gparam.getItemAt(i);
                if (pa2.equals(MFilter2Constants.MenuAccept[k])) {
                    f = 1;
                }
            }

            if (f == 0) {
                logger.log(Level.FINE, "Adding in MmapPan gparam combobox: " + MFilter2Constants.MenuAccept[k]);
                optPan.gparam.addItem(MFilter2Constants.MenuAccept[k]);
                optPan.gparam.repaint();
            }
        }
    }

}
