package lia.Monitor.JiniClient.VRVS3D;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.DoubleContainer;
import lia.Monitor.JiniClient.CommonGUI.IpAddrCache;
import lia.Monitor.JiniClient.CommonGUI.MainBase;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.ReflRouter.IPTunnel;
import lia.Monitor.JiniClient.ReflRouter.MST;
import lia.Monitor.JiniClient.ReflRouter.ReflNode;
import lia.Monitor.JiniClient.ReflRouter.ReflRouter;
import lia.Monitor.JiniClient.VRVS3D.Gmap.GraphPan;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.DataStore;
import lia.Monitor.monitor.ILink;
import lia.Monitor.monitor.MonaLisaEntry;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.MLSerClient;
import lia.Monitor.tcpClient.tClient;
import lia.util.ntp.NTPDate;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;

/**
 * SerMonitor class for the VRVS Jini Client
 */
public class VrvsSerMonitor extends SerMonitorBase {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(VrvsSerMonitor.class.getName());

    public final static boolean showMST = Boolean.valueOf(AppConfig.getProperty("lia.Monitor.showMST", "false"))
            .booleanValue();

    MST mst;

    ReflRouter router; // ReflRouter that runs MST
    Hashtable<ServiceID, ReflNode> reflNodes; // same as nodes but containing ReflNodes, not rcNodes
    private int lastNrReflectors;

    public VrvsSerMonitor(MainBase main, Class mainClientClass) {
        super(main, mainClientClass);
        TimerTask ttask = new TimerTask() {
            @Override
            public void run() {
                try {
                    checkNodes();
                    checkIPTunnels();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Error executing", t);
                }
                try {
                    gupdate();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Error doing gupdate()", t);
                }
            }
        };
        BackgroundWorker.schedule(ttask, 4000, 4000);
    }

    @Override
    public void init() {
        reflNodes = new Hashtable();
        mst = new MST(reflNodes);
        mst.simulation = true;
        router = new ReflRouter(mst);
        if (showMST) {
            BackgroundWorker.schedule(router, 10 * 1000, 5 * 1000);
        }
        super.init();
    }

    // RC Specific ! 
    @Override
    public void setNodePosition(rcNode node) {
        //      try to get location 
        if ((node.mlentry != null) && !node.mlentry.LAT.trim().equals("") && !node.mlentry.LAT.trim().equals("N/A")
                && !node.mlentry.LONG.trim().equals(" ") && !node.mlentry.LONG.trim().equals("N/A")) {
            node.LAT = node.mlentry.LAT.trim();
            node.LONG = node.mlentry.LONG.trim();
            //logger.log(Level.INFO, "Got location for "+unitName+" LAT="+n.LAT + " LONG=" + n.LONG);
        } else {
            node.LAT = "-21.22";
            node.LONG = "-111.15";
            logger.log(Level.INFO, "Setting default location for " + node.UnitName + " LAT=" + node.LAT + " LONG="
                    + node.LONG);
        }
    }

    @Override
    synchronized public void addNode(ServiceItem si, DataStore dataStore, tClient client, String unitName, String ipad) {
        rcNode n = new rcNode();
        n.conn = new Hashtable();
        n.wconn = new Hashtable();
        n.fixed = false;
        n.selected = false;
        n.client = client;
        n.dataStore = dataStore;
        n.errorCount = 0;

        int init_max_width = 800, init_max_height = 500;
        /**
         * compute n.x and n.y so that they are inside rectangle defined by
         * ( W*fLayoutMargin, H*fLayoutMargin, W*(1-2*fLayoutMargin), H*(1-2*fLayoutMargin) )
         */
        n.x = (int) ((init_max_width * GraphPan.fLayoutMargin) + (init_max_width * (1 - (2 * GraphPan.fLayoutMargin)) * Math
                .random()));
        n.y = (int) ((init_max_height * GraphPan.fLayoutMargin) + (init_max_height * (1 - (2 * GraphPan.fLayoutMargin)) * Math
                .random()));
        n.osgX = (int) ((init_max_width * GraphPan.fLayoutMargin) + (init_max_width
                * (1 - (2 * GraphPan.fLayoutMargin)) * Math.random()));
        n.osgY = (int) ((init_max_height * GraphPan.fLayoutMargin) + (init_max_height
                * (1 - (2 * GraphPan.fLayoutMargin)) * Math.random()));
        n.UnitName = unitName;
        n.setShortName();
        n.sid = si.serviceID; //sid ;
        n.attrs = si.attributeSets;
        n.mlentry = getEntry(si, MonaLisaEntry.class);
        n.IPaddress = ipad;
        n.time = 0;
        n.haux.put("lostConn", "1"); // we haven't received anything from here yet
        n.isLayoutHandled = true;

        //set location
        setNodePosition(n);
        try {
            //get visibility option on wmap and globe panel from properties, the default option
            Preferences prefs = Preferences.userNodeForPackage(mainClientClass);
            if (prefs.get("CommonGUI.rcNode." + n.UnitName + ".bHiddenOnMap", "0").compareTo("1") == 0) {
                n.bHiddenOnMap = true;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not get node visibility for " + n.UnitName, e);
        }
        snodes.put(n.sid, n);
        vnodes.add(n);
        // ReflNodes initialization
        if (reflNodes.get(n.sid) == null) {
            ReflNode rn = new ReflNode(n.UnitName, si.serviceID, ipad);
            reflNodes.put(n.sid, rn);
        }
        // register predicates and filters
        client.addLocalClient(this, new monPredicate("*", "Peers", "*", -60, -1, null, null));
        client.addLocalClient(this, new monPredicate("*", "Reflector", "*", -60, -1, null, null));
        client.addLocalClient(this, new monPredicate("*", "Internet", "*", -40 * 1000, -1, null, null));
        client.addLocalClient(this, "TriggerAgent");

        logger.log(Level.INFO, "Added reflector " + unitName);
        gupdate();
    }

    public Vector getMST() {
        return router.getMST();
    }

    private ReflNode getReflNodeByIP(String ipx) {
        synchronized (reflNodes) {
            for (Object element : reflNodes.values()) {
                ReflNode n = (ReflNode) element;
                if (n.ipad.equals(ipx)) {
                    return n;
                }
            }
        }
        return null;
    }

    @Override
    public void processResult(MLSerClient cli, eResult er) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Got eResult:" + er);
        }
        if (er.ClusterName.equals("Reflector")) {
            rcNode ns = snodes.get(cli.tClientID);
            int vidx = er.getIndex("Version");
            if (vidx >= 0) {
                ns.haux.put("ReflVersion", er.param[vidx]);
            }
        } else if (er.ClusterName.equals("Peers")) {
            // check if a node has to be removed
            ReflNode srcNode = reflNodes.get(cli.tClientID);
            ReflNode dstNode = null;
            if (er.NodeName != null) {
                dstNode = getReflNodeByIP(IpAddrCache.getIPaddr(er.NodeName, true));
            }
            srcNode.processPeerResult(dstNode, er);
        }
    }

    @Override
    public void processResult(MLSerClient cli, Result r) {
        ReflNode srcNode = reflNodes.get(cli.tClientID);
        rcNode ns = snodes.get(cli.tClientID);
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Got result:" + r);
        }
        if ((srcNode == null) || (ns == null)) {
            logger.warning("Result from " + r.FarmName + " but refl. not in known list");
            return;
        }
        if (r.ClusterName == null) {
            //TODO: this way, ALERTs won't be received. do we really need them?
            logger.fine("Invalid result, cluster is null:" + r);
            return;
        }
        if (r.ClusterName.equals("Reflector")) {
            srcNode.processReflectorResult(r);
            if (!srcNode.checkReflActive()) {
                ns.haux.put("ErrorKey", "1");
                ns.haux.remove("Video");
                ns.haux.remove("Audio");
                ns.haux.remove("VirtualRooms");
            } else {
                ns.errorCount = 0; // we got some result, reset eror count
                ns.haux.remove("ErrorKey");
                ns.haux.remove("lostConn");
                ns.time = NTPDate.currentTimeMillis();
                for (int i = 0; i < r.param_name.length; i++) {
                    final String paramName = r.param_name[i];
                    final double paramValue = r.param[i];
                    if (paramName.equals("Video")) {
                        DoubleContainer.setHashValue(ns.haux, "Video", paramValue);
                    } else if (paramName.equals("Audio")) {
                        DoubleContainer.setHashValue(ns.haux, "Audio", paramValue);
                    } else if (paramName.equals("VirtualRooms")) {
                        DoubleContainer.setHashValue(ns.haux, "VirtualRooms", paramValue);
                    } else if (paramName.equals("Quality")) {
                        DoubleContainer.setHashValue(ns.haux, "Quality", paramValue);
                    } else if (paramName.equals("LostPackages")) {
                        DoubleContainer.setHashValue(ns.haux, "LostPackages", paramValue);
                    } else if (paramName.indexOf("_IN") != -1) {
                        DoubleContainer.setHashValue(ns.haux, paramName, paramValue);
                    } else if (paramName.indexOf("_OUT") != -1) {
                        DoubleContainer.setHashValue(ns.haux, paramName, paramValue);
                    } else if (paramName.equals("Load5")) {
                        DoubleContainer.setHashValue(ns.haux, "Load", paramValue);
                    }
                }
            }
            return;
        }
        if (r.NodeName == null) {
            logger.warning("Invalid result, node is null:" + r);
            return;
        }
        String peerIP = IpAddrCache.getIPaddr(r.NodeName, true);
        ReflNode dstNode = getReflNodeByIP(peerIP);
        rcNode nw = getNodeByIP(peerIP);
        if ((dstNode == null) || (nw == null)) {
            Level logLevel = "Peers".equals(r.ClusterName) ? Level.WARNING : Level.FINE;
            if (logger.isLoggable(logLevel)) {
                logger.log(logLevel, "Result from " + r.FarmName + " but peer refl. not-existing:\n" + r);
            }
            return;
        }
        ns.conn.put(nw, r); // obsolete
        if (r.ClusterName.equals("Internet")) {
            srcNode.processInetResult(dstNode, r);
            return;
        }
        if (r.ClusterName.equals("Peers")) {
            srcNode.processPeerResult(dstNode, r);
            if (!dstNode.checkReflActive()) {
                logger.warning("Received Peer result to INACTIVE Reflector:" + r);
            }
            return;
        }
        logger.warning("Received unhandled result:" + r);
    }

    double failsafeParseDouble(String value, double failsafe) {
        try {
            return Double.parseDouble(value);
        } catch (Throwable t) {
            return failsafe;
        }
    }

    @Override
    public void removeFarmNode(rcNode n) {
        removeAllRelatedLinks(n);
        ReflNode rn = reflNodes.remove(n.sid);
        if (rn != null) {
            logger.info("Removing reflector " + rn);
            rn.removeAllTunnels();
        }
    }

    void removeAllRelatedLinks(rcNode n) {
        for (int i = 0; i < vnodes.size(); i++) {
            rcNode m = vnodes.get(i);
            m.wconn.remove(n.sid);
            m.conn.remove(n);
        }
    }

    private void checkNodes() {
        if (Math.abs(lastNrReflectors - reflNodes.size()) > 5) {
            // consider we had a major outage (proxy reconnect, or groups change)
            // and reset the start time of the ReflRouter
            lastNrReflectors = reflNodes.size();
            ReflRouter.resetStartTime();
        }
        for (Enumeration ren = reflNodes.elements(); ren.hasMoreElements();) {
            ReflNode rn = (ReflNode) ren.nextElement();
            if (!rn.checkReflActive()) {
                rcNode ns = snodes.get(rn.sid);
                ns.haux.put("lostConn", "1");
            }
        }
    }

    private void checkIPTunnels() {
        // for each possible tunnel
        for (Enumeration rne = reflNodes.elements(); rne.hasMoreElements();) {
            ReflNode from = (ReflNode) rne.nextElement();
            rcNode ns = snodes.get(from.sid);
            if (ns == null) {
                logger.fine("snode removed while checking outgoing tunnels. Skipping for check for\n" + from);
                continue;
            }
            for (Iterator tit = from.tunnels.values().iterator(); tit.hasNext();) {
                IPTunnel tun = (IPTunnel) tit.next();
                ReflNode to = tun.to;
                rcNode nw = snodes.get(to.sid);
                if (nw == null) {
                    logger.fine("snode removed while checking incoming tunnels. Skipping check for\n" + to);
                    continue;
                }
                ILink il = (ILink) ns.wconn.get(nw.sid);
                if (tun.checkAlive()) {
                    // tunnel is (at least partially) up, update the ILink accordingly
                    if (il == null) {
                        il = new ILink("from " + ns.UnitName + " to " + nw.UnitName);
                        il.fromLAT = failsafeParseDouble(ns.LAT, -21.22D);
                        il.fromLONG = failsafeParseDouble(ns.LONG, -111.15D);
                        il.toLAT = failsafeParseDouble(nw.LAT, -21.22D);
                        il.toLONG = failsafeParseDouble(nw.LONG, -111.15D);
                        il.inetQuality = new double[4];
                        il.peersQuality = new double[4];
                        il.speed = 100;
                        ns.wconn.put(nw.sid, il);
                        if (logger.isLoggable(Level.FINER)) {
                            logger.finer("Add new ILINK = " + il);
                        }
                    }
                    if (tun.hasPeerQuality()) {
                        if (il.peersQuality == null) {
                            il.peersQuality = new double[4];
                        }
                        for (int i = 0; i < 3; i++) {
                            il.peersQuality[i] = tun.getPeerQuality((3 * i) + 1);
                        }
                    } else {
                        il.peersQuality = null;
                    }
                    if (tun.hasInetQuality()) {
                        if (il.inetQuality == null) {
                            il.inetQuality = new double[4];
                        }
                        il.inetQuality[0] = tun.getInetQuality();
                        // inetQuality[3] should be < 1.0 in order to be considered in layouts
                        il.inetQuality[3] = tun.hasHighPktLoss() ? 1 : 0;
                    } else {
                        il.inetQuality = null;
                    }
                } else {
                    logger.info("Dead \n" + tun);
                    // tunnel is fully down, remove ILink and this IPTunnel
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer("Removing dead ILINK = " + il);
                    }
                    ns.wconn.remove(nw.sid);
                    tit.remove();
                }
            }

        }
    }
}
