package lia.Monitor.JiniClient.VRVS3D.Mmap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.Enumeration;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.DLink;
import lia.Monitor.JiniClient.CommonGUI.DoubleContainer;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Mmap.JColorScale;
import lia.Monitor.JiniClient.CommonGUI.Mmap.MapCanvasBase;
import lia.Monitor.JiniClient.VRVS3D.JoptPan;
import lia.Monitor.JiniClient.VRVS3D.VrvsSerMonitor;
import lia.Monitor.monitor.ILink;
import net.jini.core.lookup.ServiceID;

public class Mmap extends MapCanvasBase {

    /**
     * 
     */
    private static final long serialVersionUID = -935843763273559141L;

    private static final Logger logger = Logger.getLogger(Mmap.class.getName());

    JoptPan optPan;
    Vector mstData;

    public Mmap() {
        super();
        optPan = new JoptPan(this);
        // initialize the color and format of the 2 combo boxes
        optPan.csNodes.setColors(Color.CYAN, Color.CYAN); // cyan(min) --> blue(max)
        optPan.csNodes.setValues(0, 0);
        optPan.csPeers.setColors(Color.RED, Color.GREEN); // red(min) --> green(max)
        optPan.csPeers.setLabelFormat("###.##", "%");
        optPan.csPeers.setValues(0, 100);
        optPan.csPing.setColors(Color.RED, Color.RED); // red(min) --> yellow(max)
        optPan.csPing.setLabelFormat("###.##", "");
        optPan.csPing.setValues(0, 0);
        optPan.csMST.setColors(Color.MAGENTA, Color.MAGENTA); // magenta
        optPan.csMST.setValues(0, 0);
        optPan.csMST.setLabelFormat("", "");

        add(optPan, BorderLayout.NORTH);

        TimerTask ttask = new TimerTask() {
            @Override
            public void run() {
                try {
                    if (monitor != null) {
                        computeDataColors();
                    }
                    repaint();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Error executing", t);
                }
            }
        };
        BackgroundWorker.schedule(ttask, 4000, 4000);
    }

    @Override
    protected void plotOverImage(Graphics g) {
        if (optPan.wmapNotif) {
            optPan.wmapNotif = false;
            computeDataColors();
        }
        plotTraffic(g);
        plotNodes(g);
    }

    void computeDataColors() {
        mstData = ((VrvsSerMonitor) monitor).getMST();
        computeNodesColors();
        computeTrafficColors();
    }

    void computeNodesColors() {
        if (vnodes == null) {
            return;
        }
        // check all nodes and make a statistic
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        double crt = 0.0;
        for (int i = 0; i < vnodes.size(); i++) {
            rcNode n = vnodes.get(i);
            crt = getNodeValue(n);
            if (crt == -1) {
                continue;
            }
            min = (min < crt) ? min : crt;
            max = (max > crt) ? max : crt;
        }
        if (min > max) {
            min = max = 0.0;
        }
        if (min <= max) {
            optPan.csNodes.setValues(min, max);
            if ((max - min) < 1E-2) {
                optPan.csNodes.setColors(Color.CYAN, Color.CYAN);
            } else {
                optPan.csNodes.setColors(Color.CYAN, Color.BLUE);
            }
        }
    }

    @Override
    protected void plotNodes(Graphics g) {
        if (vnodes == null) {
            return;
        }
        for (int i = 0; i < vnodes.size(); i++) {
            rcNode n = vnodes.get(i);
            plotCN(n, g);
        }
    }

    /**
     * this is called from plotCN in base class
     */
    @Override
    protected void plotCNWorker(rcNode n, int x, int y, Graphics g) {
        int R = 6;
        if (n.errorCount > 0) {
            g.setColor(Color.red);
        } else {
            Color c = null;
            if ((n.haux.get("ErrorKey") != null) && n.haux.get("ErrorKey").equals("1")) {
                //              System.out.println(n.UnitName+"-ErrorKey>"+n.haux.get("ErrorKey"));
                c = Color.PINK;
            }
            if ((n.haux.get("lostConn") != null) && n.haux.get("lostConn").equals("1")) {
                //              System.out.println(n.UnitName+"-lostConn>"+n.haux.get("lostConn"));
                c = Color.RED;
            } else {
                Color cc = getNodeColor(n);
                if (cc != null) {
                    c = cc;
                }
            }
            if (c == null) {
                c = Color.PINK;
            }
            g.setColor(c);
        }
        g.fillArc(x - R, y - R, 2 * R, 2 * R, 0, 360);
        g.setColor(Color.red);
        g.drawArc(x - R, y - R, 2 * R, 2 * R, 0, 360);
    }

    /**
     * computes the value for the selected item in the JOptPan combobox
     * @param n the node
     * @return the corresponding value
     */
    public double getNodeValue(rcNode node) {
        try {
            switch (optPan.nodesShow) {
            case JoptPan.NODE_AUDIO:
                return DoubleContainer.getHashValue(node.haux, "Audio");
            case JoptPan.NODE_VIDEO:
                return DoubleContainer.getHashValue(node.haux, "Video");
            case JoptPan.NODE_TRAFFIC:
                double in = -1.0,
                out = -1.0;
                synchronized (node.haux) {//do not modify the hash
                    for (Enumeration en = node.haux.keys(); en.hasMoreElements();) {
                        String key = (String) en.nextElement();
                        if (key.indexOf("_IN") != -1) {
                            double tin = DoubleContainer.getHashValue(node.haux, key);
                            if (tin >= 0) {
                                if (in < 0) {
                                    in = tin;
                                } else {
                                    in += tin;
                                }
                            }
                        } else if (key.indexOf("_OUT") != -1) {
                            double tout = DoubleContainer.getHashValue(node.haux, key);
                            if (tout >= 0) {
                                if (out < 0) {
                                    out = tout;
                                } else {
                                    out += tout;
                                }
                            }
                        }
                    }//end for
                }//end sync
                if ((in + out) < 0) {
                    return -1;
                }
                return in + out;
            case JoptPan.NODE_LOAD:
                return DoubleContainer.getHashValue(node.haux, "Load");
            case JoptPan.NODE_VIRTROOMS:
                return DoubleContainer.getHashValue(node.haux, "VirtualRooms");
            default:
                return -1;
            }
        } catch (NullPointerException e) {
            return -1;
        }
    }

    //  public double getNodeValue(rcNode n){
    //  if (n == null)
    //  return -1;
    //  Object o;
    //  switch ( optPan.nodesShow ) {
    //  case JoptPan.NODE_AUDIO:
    //  o = n.haux.get("Audio");
    //  break;
    //  case JoptPan.NODE_VIDEO:
    //  o = n.haux.get("Video");
    //  break;
    //  case JoptPan.NODE_TRAFFIC:
    //  double in = -1.0, out = -1.0;
    //  synchronized ( n.haux ) {//do not modify the hash
    //  for (Enumeration en = n.haux.keys(); en.hasMoreElements();) {
    //  String key = (String) en.nextElement();
    //  if ( key.indexOf("_IN") != -1 ) {
    //  Double tin = (Double)n.haux.get(key);
    //  if ( tin != null )
    //  if ( in < 0) 
    //  in = tin.doubleValue();
    //  else
    //  in += tin.doubleValue();
    //  } else if ( key.indexOf("_OUT") != -1 ) {
    //  Double tout = (Double)n.haux.get(key);
    //  if ( tout != null )
    //  if ( out < 0)
    //  out = tout.doubleValue();
    //  else
    //  out += tout.doubleValue();
    //  }
    //  }//end for
    //  }//end sync
    //  if ( in + out < 0 ) return -1;
    //  return in + out;
    //  case JoptPan.NODE_LOAD:
    //  o = n.haux.get("Load");
    //  break;
    //  case JoptPan.NODE_VIRTROOMS:
    //  o = n.haux.get("VirtualRooms");
    //  break;
    //  default:
    //  return -1;
    //  }
    //  if (o == null)
    //  return -1;
    //  else
    //  return ((Double)o).doubleValue();
    //  }

    /**
     * computes the color for a node, as the value between minimum
     * and maximum values
     * @param n the node
     * @return the corresponding color
     */
    public Color getNodeColor(rcNode n) {
        double val = getNodeValue(n);
        if (val == -1) {
            return null;
        }
        double delta = optPan.csNodes.maxValue - optPan.csNodes.minValue;
        if (Math.abs(delta) < 1E-5) {
            return optPan.csNodes.minColor;
        }
        int R = (int) ((val * (optPan.csNodes.maxColor.getRed() - optPan.csNodes.minColor.getRed())) / delta)
                + optPan.csNodes.minColor.getRed();
        int G = (int) ((val * (optPan.csNodes.maxColor.getGreen() - optPan.csNodes.minColor.getGreen())) / delta)
                + optPan.csNodes.minColor.getGreen();
        int B = (int) ((val * (optPan.csNodes.maxColor.getBlue() - optPan.csNodes.minColor.getBlue())) / delta)
                + optPan.csNodes.minColor.getBlue();
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

    void computeTrafficColors() {
        if (vnodes == null) {
            return;
            // first, check all links and see which is the best and which is
            // the worst for the current quality selected in the link menu
        }

        double min_i = Double.MAX_VALUE;
        double max_i = Double.MIN_VALUE;
        double min_p = Double.MAX_VALUE;
        double max_p = Double.MIN_VALUE;
        double crt = 0.0;
        for (int i = 0; i < vnodes.size(); i++) {
            rcNode n = vnodes.get(i);
            for (Enumeration e1 = n.wconn.elements(); e1.hasMoreElements();) {
                ILink link = (ILink) e1.nextElement();
                if (link == null) {
                    continue;
                }
                if (link.inetQuality != null) {
                    crt = link.inetQuality[0];
                    min_i = (min_i < crt) ? min_i : crt;
                    max_i = (max_i > crt) ? max_i : crt;
                }
                if (link.peersQuality != null) {
                    crt = link.peersQuality[0];
                    min_p = (min_p < crt) ? min_p : crt;
                    max_p = (max_p > crt) ? max_p : crt;
                }
            }
        }

        if ((min_i == Double.MAX_VALUE) || (max_i == Double.MIN_VALUE)) {
            min_i = max_i = 0;
        }
        if ((min_p == Double.MAX_VALUE) || (max_p == Double.MIN_VALUE)) {
            min_p = max_p = 0;
        }

        optPan.csPing.setValues(max_i, min_i); // lower value is better
        //optPan.csPeers.setValues(min_p, max_p);	// peers quality should remain 0-100%

        if (Math.round(min_i) == Math.round(max_i)) {
            optPan.csPing.setColors(Color.RED, Color.RED);
        } else {
            optPan.csPing.setColors(Color.RED, Color.YELLOW);
        }
    }

    /**
     * compute the min/max values for the traffic and call plotILink for
     * each link
     * @param g graphic context
     */
    public void plotTraffic(Graphics g) {
        if (vnodes == null) {
            return;
        }
        for (int i = 0; i < vnodes.size(); i++) {
            rcNode n = vnodes.get(i);
            if (!n.bHiddenOnMap) {
                for (Enumeration e1 = n.wconn.keys(); e1.hasMoreElements();) {
                    ServiceID nwSid = (ServiceID) e1.nextElement();
                    rcNode nw = nodes.get(nwSid);
                    if (!nw.bHiddenOnMap) {
                        ILink link = (ILink) n.wconn.get(nwSid);
                        if ((getLinkQuality(link) != -1)
                                && (optPan.kbShowPeers.isSelected() || optPan.kbShowPing.isSelected())) {
                            plotILink(link, g, LINK_TYPE_DIRECTED, getLinkQualityColor(link));
                        }
                        if (optPan.kbShowMST.isSelected() && isHotLink(n.sid, nw.sid)) {
                            plotILink(link, g, LINK_TYPE_UNDIRECTED, optPan.csMST.minColor);
                        }
                    }
                }
            }
        }

        /*		
         int imin = (int) Math.round(min);
         int imax = (int) Math.round(max);
         
         if (optPan.kbShowPeers.isSelected() && !optPan.kbShowPing.isSelected())
         optPan.csPing.setValues(min, max);	
         else
         optPan.csPing.setValues(max, min);			// the lower value is better!!!
         
         if(imin == imax){
         if(imax > 50)
         optPan.csPing.setColors(Color.RED, Color.RED);
         else
         optPan.csPing.setColors(Color.RED, Color.YELLOW);
         }else
         optPan.csPing.setColors(Color.RED, Color.YELLOW);
         */
        //      Vector deadLinks = new Vector();
        //      for (int i=0; i<vnodes.size(); i++) {
        //      rcNode n = (rcNode) vnodes.get(i);
        //      if ( n.wconn.size() > 0 )  {
        //      deadLinks.clear();
        //      for ( Enumeration e1 = n.wconn.elements(); e1.hasMoreElements(); ) {
        //      ILink link = (ILink ) e1.nextElement();
        //      if( nodes.get(link.name) == null ){
        //      deadLinks.add(link.name);
        //      logger.log(Level.INFO, "dead Link: " + link);
        //      }else{
        //      if((getLinkQuality(link) != -1) && (optPan.kbShowPeers.isSelected() || 
        //      optPan.kbShowPing.isSelected())){
        //      plotILink(link, g, LINK_TYPE_DIRECTED, getLinkQualityColor(link));
        //      }
        //      if(optPan.kbShowMST.isSelected() && isHotLink(n.UnitName, link.name)){
        //      plotILink(link, g, LINK_TYPE_UNDIRECTED, optPan.csMST.minColor);
        //      }
        //      }
        //      }
        //      for( Enumeration e1 = deadLinks.elements(); e1.hasMoreElements(); )
        //      n.wconn.remove(e1.nextElement());
        //      }
        //      }
    }

    /**
     * return the link's quality, if exists, according to the
     * current menu item selected int the link combo box
     */
    public double getLinkQuality(ILink link) {
        double rez = -1;
        if (link == null) {
            return rez;
        }
        if (optPan.kbShowPing.isSelected()) {
            if (link.inetQuality != null) {
                rez = link.inetQuality[0];
            }
        }
        if (optPan.kbShowPeers.isSelected()) {
            if (link.peersQuality != null) {
                rez = link.peersQuality[optPan.peersShow - 11];
            }
        }
        return rez;
    }

    /**
     * compute the color for the link, using @link getLinkQuality
     * @param lnk the link
     * @return the color
     */
    public Color getLinkQualityColor(ILink lnk) {
        double val = getLinkQuality(lnk);
        if (val == -1) {
            return null;
        }

        JColorScale crtCs = null;
        if (optPan.kbShowPing.isSelected() && (lnk.inetQuality != null)) {
            crtCs = optPan.csPing;
        }
        if (optPan.kbShowPeers.isSelected() && (lnk.peersQuality != null)) {
            crtCs = optPan.csPeers;
        }
        if (crtCs == null) {
            return null;
        }

        double delta = Math.abs(crtCs.maxValue - crtCs.minValue);
        if (Math.abs(delta) < 1E-5) {
            return crtCs.minColor;
        }
        int R, G, B;
        if (crtCs.maxValue > crtCs.minValue) {
            R = (int) ((val * (crtCs.maxColor.getRed() - crtCs.minColor.getRed())) / delta) + crtCs.minColor.getRed();
            G = (int) ((val * (crtCs.maxColor.getGreen() - crtCs.minColor.getGreen())) / delta)
                    + crtCs.minColor.getGreen();
            B = (int) ((val * (crtCs.maxColor.getBlue() - crtCs.minColor.getBlue())) / delta)
                    + crtCs.minColor.getBlue();
        } else {
            R = (int) ((val * (crtCs.minColor.getRed() - crtCs.maxColor.getRed())) / delta) + crtCs.maxColor.getRed();
            G = (int) ((val * (crtCs.minColor.getGreen() - crtCs.maxColor.getGreen())) / delta)
                    + crtCs.maxColor.getGreen();
            B = (int) ((val * (crtCs.minColor.getBlue() - crtCs.maxColor.getBlue())) / delta)
                    + crtCs.maxColor.getBlue();
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

    /**
     * tells if a link is in MST
     * @param n1 name of the 
     * @param n2
     * @return
     */
    boolean isHotLink(ServiceID n1, ServiceID n2) {
        if (mstData == null) {
            return false;
        }
        for (int i = 0; i < mstData.size(); i++) {
            DLink dl = (DLink) mstData.elementAt(i);
            if (dl.cequals(n1, n2)) {
                //System.out.println("wmap: hotlink: "+n1+" - "+n2);
                return true;
            }
        }
        return false;
    }

    /**
     * get the tool tip text for the rcnode under the mouse cursor
     */
    @Override
    protected String getMapToolTipText(MouseEvent event) {
        int x = event.getX();
        int y = event.getY();
        for (final rcNode nod : nodes.values()) {
            if (nod == null) {
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
                double val = getNodeValue(nod);
                if (val == -1) {
                    return nod.UnitName;
                } else {
                    return nod.UnitName + " [ " + optPan.csNodes.formatter.format(val) + " " + optPan.csNodes.units
                            + " ]";
                }
            }
        }
        return null;
    }
}
