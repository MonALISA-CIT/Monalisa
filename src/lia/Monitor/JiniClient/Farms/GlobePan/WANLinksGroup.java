package lia.Monitor.JiniClient.Farms.GlobePan;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;

import javax.media.j3d.BranchGroup;
import javax.media.j3d.Group;
import javax.vecmath.Color3f;

import lia.Monitor.JiniClient.CommonGUI.GlobeTextureLoader;
import lia.Monitor.JiniClient.CommonGUI.GlobePan.DirectedArc3D;
import lia.Monitor.JiniClient.CommonGUI.GlobePan.TexturedPie;
import lia.Monitor.JiniClient.CommonGUI.GlobePan.WorldCoordinates;
import lia.Monitor.monitor.ILink;

//import lia.Monitor.JiniClient.Farms.Mmap.JoptPan;

/*
 * TODO o Documentation
 */

@SuppressWarnings("restriction")
public class WANLinksGroup extends BranchGroup {

    public double minQuality = 0;

    public double maxQuality = 100;

    public Color3f minQualityColor = new Color3f(0, 1, 1);

    public Color3f maxQualityColor = new Color3f(0, 0, 1);

    //public int mode = JoptPan.PEERS_QUAL2H;
    private boolean animated;

    private double currentScale = 1.0;

    HashSet links;

    Hashtable arcs; // table of ILink -> DirectedArc3D

    Hashtable routers; // table CITY -> TexturedPie

    Color routerColor = new Color(0, 170, 249);

    String routerTextureFile = "lia/images/ml_router.png";

    BufferedImage routerTexture;

    public WANLinksGroup() {
        links = new HashSet();
        arcs = new Hashtable();
        routers = new Hashtable();
        ClassLoader myClassLoader = getClass().getClassLoader();
        URL imageURL = myClassLoader.getResource(routerTextureFile);
        routerTexture = GlobeTextureLoader.loadImage(imageURL);
        setCapability(ALLOW_DETACH);
        setCapability(Group.ALLOW_CHILDREN_EXTEND);
        setCapability(Group.ALLOW_CHILDREN_WRITE);
    }

    public void addWANLink(ILink link) {
        // Make a new directed arc
        WorldCoordinates start = new WorldCoordinates(link.fromLAT,
                link.fromLONG, 1.007);
        WorldCoordinates end = new WorldCoordinates(link.toLAT, link.toLONG,
                1.007);
        DirectedArc3D arc = new DirectedArc3D(start, end, getLinkColor(link),
                0.05, link);
        // Add it to our table and make it visible
        links.add(link);
        arcs.put(link, arc);
        arc.compile();
        addChild(arc);
        changeAnimationStatus(animated);

        // create if needed the routers on both sides of the WAN link
        String posFrom = (String) link.from.get("LONG") + "/"
                + (String) link.from.get("LAT");
        String cityFrom = (String) link.from.get("CITY");
        TexturedPie rFrom = (TexturedPie) routers.get(posFrom);
        if (rFrom == null) {
            rFrom = new TexturedPie(routerTexture, routerColor,
                    (String) link.from.get("LONG"), (String) link.from
                            .get("LAT"), cityFrom + " / "
                            + (String) link.from.get("COUNTRY"));
            routers.put(posFrom, rFrom);
        }
        String posTo = (String) link.to.get("LONG") + "/"
                + (String) link.to.get("LAT");
        String cityTo = (String) link.to.get("CITY");
        TexturedPie rTo = (TexturedPie) routers.get(posTo);
        if (rTo == null) {
            rTo = new TexturedPie(routerTexture, routerColor, (String) link.to
                    .get("LONG"), (String) link.to.get("LAT"), cityTo + " / "
                    + (String) link.to.get("COUNTRY"));
            routers.put(posTo, rTo);
        }
        if (!rFrom.isLive())
            addChild(rFrom);
        if (!rTo.isLive())
            addChild(rTo);

        //System.out.println("wanLink "+link.name+" added.");
    }

    private void removeRouterIfNeeded(String pos) {
        boolean removeIt = true;
        for (Iterator lit = links.iterator(); lit.hasNext();) {
            ILink link = (ILink) lit.next();
            if (pos.equals((String) link.from.get("LONG") + "/"
                    + (String) link.from.get("LAT"))
                    || pos.equals((String) link.to.get("LONG") + "/"
                            + (String) link.to.get("LAT"))) {
                removeIt = false;
                break;
            }
        }
        if (removeIt) {
            TexturedPie r = (TexturedPie) routers.get(pos);
            r.detach();
            removeChild(r);
            routers.remove(pos);
        }
    }

    public void removeWANLink(ILink link, Iterator linksIter) {
        DirectedArc3D arc = (DirectedArc3D) arcs.remove(link);
        linksIter.remove(); // remove link from links hashset
        arc.detach();
        removeChild(arc);
        removeRouterIfNeeded((String) link.from.get("LONG") + "/"
                + (String) link.from.get("LAT"));
        removeRouterIfNeeded((String) link.to.get("LONG") + "/"
                + (String) link.to.get("LAT"));
        //System.out.println("wanLink "+link.name+" removed.");
    }

    public void setScale(double scale) {
        currentScale = scale;
        for (Enumeration e = arcs.elements(); e.hasMoreElements();)
            ((DirectedArc3D) e.nextElement()).setScale(scale);
        for (Enumeration e = routers.elements(); e.hasMoreElements();)
            ((TexturedPie) e.nextElement()).setScale(scale);
    }

    public void setLinkTooltip(ILink link, String text) {
        if (arcs == null)
            return;
        DirectedArc3D arc = (DirectedArc3D) arcs.get(link);
        if (arc != null)
            arc.setTooltipText(text);
    }

    public void showLinkTooltip(ILink link) {
        if (arcs == null)
            return;
        DirectedArc3D arc = (DirectedArc3D) arcs.get(link);
        if (arc != null) {
            arc.showTooltip();
        }
    }

    public void showRouterTooltip(String pos) {
        TexturedPie r = (TexturedPie) routers.get(pos);
        if (r != null)
            r.showTooltip();
    }

    public void hideAllLinkTooltips() {
        if (arcs == null)
            return;
        for (Enumeration e = arcs.elements(); e.hasMoreElements();)
            ((DirectedArc3D) e.nextElement()).hideTooltip();
        for (Enumeration e = routers.elements(); e.hasMoreElements();)
            ((TexturedPie) e.nextElement()).hideTooltip();
    }

    public void changeAnimationStatus(boolean animateWANLinks) {
        animated = animateWANLinks;
        for (Iterator i = links.iterator(); i.hasNext();) {
            ILink link = (ILink) i.next();
            DirectedArc3D arc = (DirectedArc3D) arcs.get(link);
            arc.setAnimationStatus(animateWANLinks);
        }
        setScale(currentScale);
    }

    public void refresh() {
        setValueBounds();
        for (Iterator i = links.iterator(); i.hasNext();) {
            ILink link = (ILink) i.next();
            DirectedArc3D arc = (DirectedArc3D) arcs.get(link);
            arc.setColor(getLinkColor(link));
        }
    }

    void setValueBounds() {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (Iterator i = links.iterator(); i.hasNext();) {
            ILink link = (ILink) i.next();
            double v = getLinkValue(link);
            if (min > v)
                min = v;
            if (max < v)
                max = v;
        }
        if (min > max)
            min = max = 0;
        minQuality = min;
        maxQuality = max;
    }

    double getLinkValue(ILink link) {
        return ((Double) (link.data)).doubleValue(); // * 100
                                                                       // /
                                                                       // link.speed;
    }

    Color3f getLinkColor(ILink link) {
        //double q; //link.peersQuality[mode - 11];
        //q = ((Double) (link.data)).doubleValue() * 100 / link.speed;
        double q = getLinkValue(link);
        Color3f color = new Color3f();
        if (minQuality >= maxQuality)
            return minQualityColor;
        color.interpolate(minQualityColor, maxQualityColor,
                (float) ((q - minQuality) / (maxQuality - minQuality)));
        return color;
    }

}