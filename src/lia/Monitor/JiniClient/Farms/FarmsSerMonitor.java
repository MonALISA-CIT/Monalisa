package lia.Monitor.JiniClient.Farms;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import lia.Monitor.Agents.OpticalPath.OpticalLink;
import lia.Monitor.Agents.OpticalPath.OpticalSwitchInfo;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwConfig;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwCrossConn;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwLink;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwPort;
import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.IpAddrCache;
import lia.Monitor.JiniClient.CommonGUI.MainBase;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.pie;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.Farms.Histograms.Traff;
import lia.Monitor.JiniClient.Farms.OSGmap.OSGmapPan;
import lia.Monitor.JiniClient.Farms.OpticalSwitch.OpticalSwitchPan;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.DataStore;
import lia.Monitor.monitor.Gresult;
import lia.Monitor.monitor.ILink;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MonMessageClientsProxy;
import lia.Monitor.monitor.MonaLisaEntry;
import lia.Monitor.monitor.NFLink;
import lia.Monitor.monitor.OSLink;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.MLSerClient;
import lia.Monitor.tcpClient.tClient;
import lia.net.topology.TopologyHolder;
import lia.util.geo.iNetGeoManager;
import lia.util.ntp.NTPDate;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;

/**
 * SerMonitor class for the Farms Jini Client
 */
public class FarmsSerMonitor extends SerMonitorBase {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(FarmsSerMonitor.class.getName());

    //
    // Activate with -Dlia.Monitor.Farms.GlobeLinksType=openflow
    //
    public static enum GlobeLinksType {
        NETFLOW, FDT, OPENFLOW, UNDEFINED
    }

    static long NODE_EXPIRE_TIME = 2 * 60 * 1000; // after some time, consider node lost

    static long WAN_LINK_EXPIRE_TIME = 15 * 60 * 1000; // after some time, consider link lost

    static long NETFLOW_LINK_EXPIRE_TIME = 1 * 60 * 1000; // after some time, consider link lost

    static long PING_LINK_EXPIRE_TIME = 6 * 60 * 1000; // after some time, consider link lost

    static long OS_LINK_EXPIRE_TIME = 5 * 60 * 1000; // after some time, consider link lost

    boolean bHasOSGmapPanel = false;// boolean value that is set only if the client requested to have osgmap panel

    boolean bHasCienaPanel = false; // boolean value that is set only if the client requested to have ciena panel

    boolean bHasOpticalSwitchPanel = false; // boolean value that is set only if the client requested to have
                                            // OpticalSwitch panel

    public static final boolean DEBUG = AppConfig.getb("lia.Monitor.debug", false);

    Dijkstra dj;

    rcNode pick;

    Traff traff;

    public iNetGeoManager iNetGeo;

    // public boolean bOSLinkRemoved=false;//indicates that an OSLink has just been removed, so some action must be
    // taken immediatelly
    public final GlobeLinksType globeLinksType;

    public final boolean hasGlobeLinks;

    //
    //
    // Openflow service name
    // -Dlia.Monitor.JiniClient.Farms.FarmsSerMonitor.OFservices=
    //
    private final Set<String> openflowServicesSet;

    public FarmsSerMonitor(MainBase main, Class<?> mainClientClass) {
        super(main, mainClientClass);
        globeLinksType = getGlobeLinksTypeFromEnv();
        hasGlobeLinks = (globeLinksType != GlobeLinksType.UNDEFINED);
        final String[] OF_SERVICES = AppConfig.getVectorProperty(
                "lia.Monitor.JiniClient.Farms.FarmsSerMonitor.OFservices", "pccit16_devtrunk");
        if ((OF_SERVICES == null) || (OF_SERVICES.length == 0)) {
            this.openflowServicesSet = Collections.emptySet();
        } else {
            this.openflowServicesSet = new HashSet<String>(Arrays.asList(OF_SERVICES));
        }

        main.nStopCreateMonitorTime = NTPDate.currentTimeMillis();
        logger.info("Monitor constructor initialisation time is: "
                + (main.nStopCreateMonitorTime - main.nStartCreateMonitorTime) + " miliseconds");
        long nStartNetGeo = NTPDate.currentTimeMillis();
        iNetGeo = new iNetGeoManager();
        long nStopNetGeo = NTPDate.currentTimeMillis();
        logger.info("iNetGeoManager init in " + (nStopNetGeo - nStartNetGeo) + " miliseconds");
        TimerTask ttask = new TimerTask() {

            @Override
            public void run() {
                // System.out.println("farmSerMonitor->checking ..."+ (new Date()));
                Thread.currentThread().setName(" ( ML ) - Farms - FarmSerMonitor Timer Thread");
                try {
                    makeCPUpie();
                    makeIOpie();
                    makeLoadpie();
                    makeDiskpie();
                    check4deadNodes();
                    check4deadLinks();
                    if (pick != null) {
                        Vector djv = dj.Compute(pick);
                        for (int i = 0; i < graphs.size(); i++) {
                            graphs.elementAt(i).setMaxFlowData(pick, djv);
                        }
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Error executing", t);
                }
            }
        };
        BackgroundWorker.schedule(ttask, 4000, 4000);
    }

    public static final GlobeLinksType getGlobeLinksTypeFromEnv() {
        GlobeLinksType ret = GlobeLinksType.UNDEFINED;
        final String sGlobeLinksType = AppConfig.getProperty("lia.Monitor.Farms.GlobeLinksType", "UNDEFINED");
        if (sGlobeLinksType != null) {
            try {
                ret = GlobeLinksType.valueOf(sGlobeLinksType.toUpperCase());
            } catch (Throwable t) {
                ret = GlobeLinksType.UNDEFINED;
            }
        }

        return ret;
    }

    @Override
    public void init() {
        // System.out.println("init in FarmsSerMonitor");
        dj = new Dijkstra(snodes);
        pick = null;
        super.init();
    }

    // TODO fix this... should be like in the VRVS client
    public void addTraff(Traff traff) {
        this.traff = traff;
        traff.setLinkExpiringInterval(WAN_LINK_EXPIRE_TIME);
    }

    @Override
    public void setNodePosition(rcNode node) {
        node.LAT = null;
        node.LONG = null;
        // try to get location
        // if not optical switch, then put automatic location
        // or if checkbox for automatic reposition is on, use ->
        if ( /* node.szOpticalSwitch_Name==null || */main.bAutomaticPosition) {
            Map<String, String> geoTab = iNetGeo.getNodeGeo(node.IPaddress);
            if (geoTab != null) {
                node.LAT = geoTab.get("LAT");
                node.LONG = geoTab.get("LONG");
                node.CITY = geoTab.get("CITY");
            }
        }
        // if no automatic location available
        // put farm's owner version of location
        if ((node.LAT == null) || (node.LONG == null)
                || (node.CITY == null /* || node.mlentry.Group.compareTo("OSwitch")==0 */)) {
            if ((node.mlentry != null) && (node.mlentry.LAT != null) && !node.mlentry.LAT.trim().equals("")
                    && !node.mlentry.LAT.equals("N/A") && (node.mlentry.LONG != null)
                    && !node.mlentry.LONG.trim().equals("") && !node.mlentry.LONG.equals("N/A")) {
                node.LAT = node.mlentry.LAT;
                node.LONG = node.mlentry.LONG;
            }
        }
        // if still not found, set some default coords
        if ((node.LAT == null) || (node.LONG == null)) {
            node.LAT = "-21.22";
            node.LONG = "-111.15";
        }
    }

    /**
     * convert a string to a double value. If there would be an exception
     * return the failsafe value
     * 
     * @param value
     *            initial value
     * @param failsafe
     *            failsafe value
     * @return final value
     */
    public double failsafeParseDouble(String value, double failsafe) {
        try {
            return Double.parseDouble(value);
        } catch (Throwable t) {
            return failsafe;
        }
    }

    @Override
    public void addNode(final ServiceItem si, final DataStore dataStore, final tClient client, final String unitName,
            final String ipad) {

        rcNode n = new rcNode();
        n.snodes = snodes;
        n.conn = new Hashtable();
        n.wconn = new Hashtable();
        n.fixed = false;
        n.selected = false;
        n.client = client;
        n.dataStore = dataStore;
        n.errorCount = 0;

        n.x = (int) (800 * Math.random());
        n.y = (int) (500 * Math.random());
        n.osgX = (int) (800 * Math.random());
        n.osgY = (int) (500 * Math.random());
        // int init_max_width=800, init_max_height=500;
        // n.x = (int) ( init_max_width*GraphPan.fLayoutMargin +
        // init_max_width*(1-2*GraphPan.fLayoutMargin)*Math.random() );
        // n.y = (int) ( init_max_height*GraphPan.fLayoutMargin +
        // init_max_height*(1-2*GraphPan.fLayoutMargin)*Math.random() );
        n.UnitName = unitName;
        n.setShortName();
        n.sid = si.serviceID;
        n.attrs = si.attributeSets;
        n.mlentry = getEntry(si, MonaLisaEntry.class);
        n.IPaddress = ipad;
        n.isLayoutHandled = true;

        // set optical switch name for this node
        final MonMessageClientsProxy configuration = (client.msgMux != null ? client.msgMux.knownConfigurations
                .get(n.sid) : null);
        if (configuration != null) {
            try {
                for (final MCluster cluster : ((MFarm) configuration.result).getClusters()) {
                    final String sCluster = cluster.name;
                    if (sCluster.startsWith("OS_")) {
                        final Vector<String> v = cluster.getParameterList();

                        for (final String param : v) {
                            if (param.equals("OSIConfig")) {
                                n.szOpticalSwitch_Name = sCluster.substring(3);
                                // logger.log(Level.INFO,
                                // "for node "+n.UnitName+" got optical switch "+n.szOpticalSwitch_Name);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                n.szOpticalSwitch_Name = null;
            }
        } // if

        setNodePosition(n);

        // int init_max_width=main.jpMain.getWidth(), init_max_height=main.jpMain.getHeight();
        // double lngDelta = failsafeParseDouble(n.LONG, -111.15) + 180.0;
        // double latDelta = failsafeParseDouble(n.LAT, -21.22) + 90.0;
        // System.out.println("node "+n.UnitName+" long="+n.LONG+" lat="+n.LAT);
        // n.x = (int)(init_max_width*lngDelta/360.0);
        // n.y = (int)(init_max_height*(180.0-lngDelta)/180.0);

        // try finding an icon for the service
        // if (n.mlentry != null && n.mlentry.IconUrl != null && !n.mlentry.IconUrl.trim().equals("")) {
        // String add = n.mlentry.IconUrl;
        // if (logger.isLoggable(Level.INFO)) {
        // logger.log(Level.INFO, "Trying to load service icon from " + add);
        // }
        // n.icon = loadIcon(add);
        // }
        // if (n.icon == null) {
        // if (logger.isLoggable(Level.FINE)) {
        // logger.log(Level.FINE, "try to load an icon from local");
        // }
        // n.icon = loadIcon("lia/images/" + n.UnitName + ".gif");
        // }
        // if (n.icon == null) {
        // n.icon = loadIcon("lia/images/cms1.gif");
        // }
        try {
            // get visibility option on wmap and globe panel from properties, the default option
            Preferences prefs = Preferences.userNodeForPackage(mainClientClass);
            if (prefs.get("CommonGUI.rcNode." + n.UnitName + ".bHiddenOnMap", "0").compareTo("1") == 0) {
                n.bHiddenOnMap = true;
            }
        } catch (Exception e) {
            // System.out.println("Could not get node visibility for "+n.UnitName);
            e.printStackTrace();
            // could not get previous state, ignore
        }
        snodes.put(n.sid, n);
        vnodes.add(n);

        // logger.log(Level.INFO, "Add new farm "+n.UnitName+" sid="+n.sid);

        // long t1 = -40000;
        long t2 = -60;
        long t3 = -20 * 60 * 1000;

        // register for wan information
        monPredicate pre1 = new monPredicate("*", "WAN", "*", t3, -1, null, null);
        client.addLocalClient(this, pre1);
        // register for ttl timings between services
        monPredicate pre = new monPredicate("*", "ABPing", "*", t2, -1, null, null);
        client.addLocalClient(this, pre);
        client.addLocalClient(this, "MFilter2");
        if (bHasCienaPanel) {
            client.addLocalClient(this, "OsrpTopoFilter");
            client.addLocalClient(this, "CienaSNCFilter");
        }
        // register to collect state data about optical switches
        boolean foundNewCluster = false;
        if (client.getGMLEntry() != null) {
            final Hashtable hash = client.getGMLEntry().hash;
            // System.out.println(hash);
            if (hash.containsKey("OS_CONFIG_CLUSTER")) {
                final String cluster = hash.get("OS_CONFIG_CLUSTER").toString();
                monPredicate pre3 = new monPredicate("*", cluster, "*", t2, -1, null, null);
                client.addLocalClient(this, pre3);
                foundNewCluster = true;
                if (configuration != null) {
                    n.szOpticalSwitch_Name = ((MFarm) configuration.result).name;
                }
            }
            final String topoConfigCluster = (String) hash.get("TOPO_CONFIG_CLUSTER");
            if (topoConfigCluster != null) {
                final monPredicate pTopo = new monPredicate("*", topoConfigCluster, "*", t2, -1, null, null);
                client.addLocalClient(this, pTopo);
            }
        }
        // if it is a normal farms client, then register for optical switches and netflow information
        // try {
        // throw new Exception(""+main.bGridsClient);
        // } catch (Throwable t) {
        // t.printStackTrace();
        // }
        if (!main.bGridsClient) {
            if (!foundNewCluster) {
                monPredicate pre3 = new monPredicate("*", "OS_*", "*", t2, -1, null, null);
                client.addLocalClient(this, pre3);
            }
            if (hasGlobeLinks) {
                switch (globeLinksType) {
                case FDT: {
                    if (unitName.equals("FDT_SC10") || unitName.equals("FDTDYNES_SC10")) {
                        monPredicate pre4 = new monPredicate("*", "Links_Summary", "*", t3, -1, new String[] {
                                "NET_IN_Mb", "NET_OUT_Mb" }, null);
                        client.addLocalClient(this, pre4);
                    }
                    break;
                }
                case NETFLOW: {
                    monPredicate pre4 = new monPredicate("*", "NetFlow", "*", t3, -1, null, null);
                    client.addLocalClient(this, pre4);
                    break;
                }
                case OPENFLOW: {
                    if (openflowServicesSet.contains(unitName)) {
                        monPredicate pre4 = new monPredicate(unitName, "Flows_%", "*", t2, -1,
                                new String[] { "traffic_out" }, null);
                        pre4.bLastVals = true;
                        monPredicate pre5 = new monPredicate(unitName, "Links_%", "*", t2, -1,
                                new String[] { "Bandwidth" }, null);
                        pre5.bLastVals = true;
                        logger.log(Level.INFO, "[OpenFlow] results Register for " + unitName + "; pre = " + pre4);
                        client.addLocalClient(this, pre4);
                        client.addLocalClient(this, pre5);
                    }
                    break;
                }
                case UNDEFINED:
                    break;
                default:
                    break;
                }
            }
        }

        gupdate();
    }

    final ArrayList<rcNode> sentOSConf = new ArrayList<rcNode>();

    final ArrayList<rcNode> sentTopoConf = new ArrayList<rcNode>();

    public void reCheckGMLEEntries(final ArrayList<ServiceItem> activeFarms) {
        long t2 = -60;
        synchronized (vnodes) {

            ArrayList<rcNode> toEmpty = new ArrayList<rcNode>();
            for (rcNode n : sentOSConf) {
                if (!vnodes.contains(n)) {
                    toEmpty.add(n);
                }
            }
            for (rcNode n : toEmpty) {
                sentOSConf.remove(n);
            }
            toEmpty.clear();
            for (rcNode n : sentTopoConf) {
                if (!vnodes.contains(n)) {
                    toEmpty.add(n);
                }
            }
            for (rcNode n : toEmpty) {
                sentTopoConf.remove(n);
            }

            for (rcNode n : vnodes) {
                final ServiceItem exServiceItem = n.client.getServiceEntry();
                if (exServiceItem != null) {
                    final ServiceID sid = exServiceItem.serviceID;
                    for (final ServiceItem sit : activeFarms) {
                        if (sit.serviceID.equals(sid)) {
                            n.client.setServiceEntry(sit);
                        }
                    }
                }
                if (n.client.getGMLEntry() != null) {
                    final Hashtable hash = n.client.getGMLEntry().hash;
                    // System.out.println(hash);
                    if (hash.containsKey("OS_CONFIG_CLUSTER") && !sentOSConf.contains(n)) {
                        final String cluster = hash.get("OS_CONFIG_CLUSTER").toString();
                        monPredicate pre3 = new monPredicate("*", cluster, "*", t2, -1, null, null);
                        n.client.addLocalClient(this, pre3);
                        sentOSConf.add(n);
                    }
                    final String topoConfigCluster = (String) hash.get("TOPO_CONFIG_CLUSTER");
                    if ((topoConfigCluster != null) && !sentTopoConf.contains(n)) {
                        final monPredicate pTopo = new monPredicate("*", topoConfigCluster, "*", t2, -1, null, null);
                        n.client.addLocalClient(this, pTopo);
                        sentTopoConf.add(n);
                    }
                }
            }
        }
    }

    @Override
    public void actualizeFarms(ArrayList<ServiceItem> activeFarms) {
        super.actualizeFarms(activeFarms);
        reCheckGMLEEntries(activeFarms);
    }

    public Vector getMaxFlow(rcNode n) {
        pick = n;
        if (n == null) {
            return null;
        }
        return dj.Compute(n);
    }

    public void evaluateGlobal(Result r) {
        gupdate();
    }

    public void makeLoadpie() {

        for (int i = 0; i < vnodes.size(); i++) {
            rcNode n = vnodes.get(i);
            if (n == null) {
                continue;
            }

            pie p1 = (pie) n.haux.get("LoadPie");
            if (p1 == null) {
                p1 = new pie(3);
                p1.cpie[0] = Color.pink;
                p1.cpie[1] = Color.blue;
                p1.cpie[2] = Color.green;
                n.haux.put("LoadPie", p1);
            }
            synchronized (p1) {
                Gresult ldx = ((n == null) || (n.global_param == null) ? null : (Gresult) n.global_param.get("Load5"));
                /*
                 * if (ldx == null ) {
                 * ldx = (n==null || n.global_param == null?null:(Gresult) n.global_param.get("Load1" ));
                 * if( ldx!=null && ldx.ClusterName.indexOf("PBS")==-1 && ldx.ClusterName.indexOf("Condor")==-1 )
                 * ldx=null;
                 * }
                 */
                if ((ldx == null) || (ldx.hist == null)) {
                    p1.len = 0;
                } else {
                    p1.len = 3;
                    p1.rpie[0] = ldx.hist[4] / (double) ldx.Nodes;
                    p1.rpie[1] = (ldx.hist[3] + ldx.hist[2]) / (double) ldx.Nodes;
                    p1.rpie[2] = (ldx.hist[0] + ldx.hist[1]) / (double) ldx.Nodes;
                }
            }
        }
    }

    public void makeIOpie() {

        for (int i = 0; i < vnodes.size(); i++) {
            rcNode n = vnodes.get(i);
            if (n == null) {
                continue;
            }

            pie p1 = (pie) n.haux.get("IOPie");
            if (p1 == null) {
                p1 = new pie(2);
                p1.cpie[0] = Color.pink;
                p1.cpie[1] = Color.blue;
                n.haux.put("IOPie", p1);
            }
            synchronized (p1) {
                Gresult inn = n.global_param.get("TotalIO_Rate_IN");
                Gresult outn = n.global_param.get("TotalIO_Rate_OUT");
                if ((inn == null) || (outn == null)) {
                    p1.len = 0;
                } else {
                    p1.len = 2;
                    double sum = inn.mean + outn.mean;
                    p1.rpie[0] = inn.mean / sum;
                    p1.rpie[1] = outn.mean / sum;
                }
            }
        }

    }

    public void makeDiskpie() {

        for (int i = 0; i < vnodes.size(); i++) {
            rcNode n = vnodes.get(i);
            if (n == null) {
                continue;
            }

            pie p1 = (pie) n.haux.get("DiskPie");
            if (p1 == null) {
                p1 = new pie(2);
                p1.cpie[0] = Color.pink;
                p1.cpie[1] = Color.green;
                n.haux.put("DiskPie", p1);
            }
            synchronized (p1) {
                Gresult fd = n.global_param.get("FreeDsk");
                Gresult ud = n.global_param.get("UsedDsk");
                if ((fd == null) || (ud == null)) {
                    p1.len = 0;
                } else {
                    p1.len = 2;
                    double sum = fd.sum + ud.sum;
                    p1.rpie[0] = ud.sum / sum;
                    p1.rpie[1] = fd.sum / sum;
                }
            }
        }

    }

    public void makeCPUpie() {
        for (int i = 0; i < vnodes.size(); i++) {
            rcNode n = vnodes.get(i);
            if (n == null) {
                continue;
            }

            pie p1 = (pie) n.haux.get("CPUPie");
            if (p1 == null) {
                p1 = new pie(4);
                p1.cpie[0] = Color.pink;
                p1.cpie[1] = Color.blue;
                p1.cpie[2] = Color.green;
                p1.cpie[3] = Color.red;
                n.haux.put("CPUPie", p1);
            }
            synchronized (p1) {
                Gresult usr = n.global_param.get("CPU_usr");
                Gresult sys = n.global_param.get("CPU_sys");
                Gresult nice = n.global_param.get("CPU_nice");

                if ((usr == null) || (nice == null)) {
                    p1.len = 0;
                } else {
                    p1.len = 4;

                    if (usr.Nodes == usr.TotalNodes) {
                        p1.rpie[3] = 0;
                    } else {
                        p1.rpie[3] = (double) (usr.TotalNodes - usr.Nodes) / (double) usr.TotalNodes;
                    }

                    p1.rpie[0] = ((usr.sum + nice.sum) * 0.01) / usr.TotalNodes;

                    if (sys != null) {
                        p1.rpie[1] = (sys.sum * 0.01) / usr.TotalNodes;
                    } else {
                        p1.rpie[1] = 0.0;
                    }

                    p1.rpie[2] = 1.0 - p1.rpie[0] - p1.rpie[1] - p1.rpie[3];
                }
            }
        }
    }

    @Override
    public void processResult(MLSerClient client, eResult er) {

        if (er == null) {
            return;
        }
        if (er.NodeName == null) {
            return;
        }

        rcNode ns = snodes.get(client.tClientID);
        if (ns == null) {
            return;
        }

        if ((ns.mlentry != null) && (ns.mlentry.Group != null) && !tClient.isOSgroup(ns.mlentry.Group)) {
            return; // can not process this one..
        }

        String topoConfigCluster = null;
        if ((ns.client != null) && (ns.client.getGMLEntry() != null)) {
            final Hashtable hash = ns.client.getGMLEntry().hash;
            topoConfigCluster = (hash != null) ? (String) hash.get("TOPO_CONFIG_CLUSTER") : null;
        }

        if ((topoConfigCluster != null) && er.ClusterName.startsWith(topoConfigCluster) && (er.param != null)
                && (er.param_name != null)) {

            // add the new panel
            if (bHasOpticalSwitchPanel && !main.hasGraphical("TopoMap")) {
                OpticalSwitchPan panel = OpticalSwitchPan.getInstance();
                main.addGraphical(panel, "TopoMap", "Optical Switch Map", "optical_switch");
            }

            for (int i = 0; (i < er.param.length) && (i < er.param_name.length); i++) {
                if (er.param_name[i].equals("TOPO_CONFIG") || er.param_name[i].equals("LINKS_CONFIG")) {
                    try {
                        byte[] array = (byte[]) er.param[i];
                        TopologyHolder.getInstance().notifyRemoteMsg(array, ns);
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " Exception while reading topo msg. Cause ", t);
                    }
                }
            }
            return;
        }

        boolean isV2 = false;
        String os = "OS_";
        if ((ns.client != null) && (ns.client.getGMLEntry() != null)) {
            final Hashtable hash = ns.client.getGMLEntry().hash;
            if (hash.containsKey("OSProtocolVersion")) {
                if (hash.get("OSProtocolVersion").toString().equals("v2")) {
                    isV2 = true;
                }
            }
            if (hash.containsKey("OS_CONFIG_CLUSTER")) {
                os = hash.get("OS_CONFIG_CLUSTER").toString();
            }
        }

        // if result is an oslink
        if (er.ClusterName.startsWith(os) && (er.param != null) && (er.param_name != null)) {
            // set node support for optical switches
            OpticalSwitchInfo osi = null;
            OSwConfig newOsi = null;
            for (int i = 0; (i < er.param.length) && (i < er.param_name.length); i++) {
                if (er.param_name[i].equals("OSIConfig")) {
                    try {
                        byte[] array = (byte[]) er.param[i];
                        if (array != null) {
                            ByteArrayInputStream bais = new ByteArrayInputStream(array);
                            ObjectInputStream ois = new ObjectInputStream(bais);
                            if (!isV2) {
                                osi = (OpticalSwitchInfo) ois.readObject();
                                ns.newOpticalResult(osi);
                                ns.szOpticalSwitch_Name = osi.name;
                                if (AppConfig.getProperty("lia.Monitor.OSGmap.debug", "false").equals("true")) {
                                    logger.log(Level.WARNING, "new OSIConfig for " + ns.szOpticalSwitch_Name);
                                }
                            } else {
                                newOsi = (OSwConfig) ois.readObject();
                                ns.newOpticalResult(newOsi);
                                ns.szOpticalSwitch_Name = newOsi.name;
                                if (AppConfig.getProperty("lia.Monitor.OSGmap.debug", "false").equals("true")) {
                                    logger.log(Level.WARNING, "new OSIConfig for " + ns.szOpticalSwitch_Name);
                                }
                                // System.out.println(newOsi);
                            }
                            break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        return;
                    }
                }
            }

            // code for creating/managing optical links
            if ((!isV2 && (osi != null) && (osi.map != null)) || (isV2 && (newOsi != null) && (newOsi.osPorts != null))) {
                // TODO: this action should also be put in addNode...???
                // first check to see if graphical panel osgmap is loaded or not
                // and load it if neccessary
                if (bHasOSGmapPanel && !main.hasGraphical("OS GMap")) {
                    // System.out.println(bHasOSGmapPanel+":"+main.hasGraphical("OS GMap"));
                    OSGmapPan panel = OSGmapPan.getInstance();
                    main.addGraphical(panel, "OS GMap", "Optical Switch GMap", "osgmap");
                }
                if (!isV2 && (osi != null)) {
                    Collection map_col = osi.map.values();
                    if (map_col != null) {
                        // first, add new links from map to wconn
                        try {
                            for (Iterator it = map_col.iterator(); it.hasNext();) {
                                OpticalLink opLink = (OpticalLink) it.next();
                                // check to see if link already exists, based on above attributes, or create it
                                // otherwise
                                // this should find duplicate fake nodes that share the same name...
                                String key = OSLink.ToString(ns.szOpticalSwitch_Name, opLink.port, opLink.destination,
                                        opLink.destinationPortName);
                                OSLink osLink = (OSLink) ns.wconn.get(key);
                                if (osLink == null) {
                                    // check type of link to see if destination node exists
                                    if (opLink.type.shortValue() == OpticalLink.TYPE_HOST) {
                                        // create new link
                                        osLink = new OSLink(ns, null, opLink.port, opLink.destinationPortName,
                                                ns.szOpticalSwitch_Name, opLink.destination, opLink.opticalLinkID);
                                        osLink.setFlags(OSLink.OSLINK_FLAG_FAKE_NODE);
                                        if (AppConfig.getProperty("lia.Monitor.OSGmap.debug", "false").equals("true")) {
                                            logger.warning(key + " Got fake link for " + ns + " - "
                                                    + opLink.destination + " ->: " + opLink.port.name + "("
                                                    + opLink.port.type + ")" + osLink.toString() + " - " + opLink.state
                                                    + " - " + opLink.opticalLinkID);
                                        }
                                        ns.wconn.put(key, osLink);
                                        osLink.setOpReference(opLink);
                                        if (AppConfig.getProperty("lia.Monitor.OSGmap.debug", "false").equals("true")) {
                                            logger.warning("new OS fake link created: " + osLink);
                                        }
                                    } else { // TYPE_SWITCH
                                        // set destination based on name
                                        // check first for its destination
                                        try {
                                            rcNode node = null;
                                            for (Object element : snodes.values()) {
                                                node = (rcNode) element;
                                                if ((node.szOpticalSwitch_Name != null)
                                                        && node.szOpticalSwitch_Name.equals(opLink.destination)) {
                                                    osLink = new OSLink(ns, node, opLink.port,
                                                            opLink.destinationPortName, ns.szOpticalSwitch_Name,
                                                            opLink.destination, opLink.opticalLinkID);
                                                    // System.out.println("new OS link created: "+osLink);
                                                    if (AppConfig.getProperty("lia.Monitor.OSGmap.debug", "false")
                                                            .equals("true")) {
                                                        logger.warning(key + " Got link for " + ns + " - "
                                                                + opLink.destination + " ->: " + opLink.port.name + "("
                                                                + opLink.port.type + ")" + osLink.toString() + " - "
                                                                + opLink.state + " - " + opLink.opticalLinkID);
                                                    }
                                                    ns.wconn.put(key, osLink);
                                                    osLink.setOpReference(opLink);
                                                    break;
                                                }
                                            }
                                        } catch (Exception ex) {
                                            ex.printStackTrace();
                                            logger.warning(ex.getLocalizedMessage());
                                        }
                                    }
                                }
                                if (osLink != null) {
                                    // optical link exists, so update it's data
                                    if (AppConfig.getProperty("lia.Monitor.OSGmap.debug", "false").equals("true")) {
                                        logger.warning("before: " + osLink + " update state to: " + osLink.getState());
                                    }
                                    osLink.linkID = opLink.opticalLinkID;
                                    osLink.updateState(opLink.state);
                                    if (AppConfig.getProperty("lia.Monitor.OSGmap.debug", "false").equals("true")) {
                                        logger.warning(osLink + " update state to: " + opLink.state);
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            logger.warning(ex.getLocalizedMessage());
                        }
                        // second, remove old links from wconn if not any more in map
                        try {
                            for (Iterator it = ns.wconn.values().iterator(); it.hasNext();) {
                                OSLink osLink = (OSLink) it.next();
                                // iterrate through map values to search for an optical link with reference
                                if (!osi.map.containsValue(osLink.opReference)) {
                                    it.remove();
                                }
                            }
                        } catch (Exception ex) {
                            logger.warning(ex.getLocalizedMessage());
                        }
                    }
                }

                if (isV2 && (newOsi != null)) { // new code added to treat the OSwConfig type of result
                    // first check for the fakes nodes...
                    if ((newOsi.localHosts != null) && (newOsi.localHosts.size() != 0)) {
                        for (Iterator it = newOsi.localHosts.keySet().iterator(); it.hasNext();) {
                            String hostName = (String) it.next();
                            OSwLink[] link = (OSwLink[]) newOsi.localHosts.get(hostName);
                            // get the port (in this case will be the output port..
                            if (newOsi.osPorts != null) {
                                for (OSwLink element : link) {
                                    for (final OSwPort port : newOsi.osPorts) {
                                        if ((port.type == OSwPort.INPUT_PORT)
                                                && port.name.equals(element.destinationPortName)) {
                                            String linkId = null;
                                            if (newOsi.crossConnects != null) {
                                                for (final OSwCrossConn cc : newOsi.crossConnects) {
                                                    if (cc.sPort.equals(port)) {
                                                        linkId = cc.mlID;
                                                        break;
                                                    }
                                                }
                                            }
                                            String key = OSLink.ToString(ns.szOpticalSwitch_Name, port,
                                                    element.destination, element.destinationPortName);
                                            if (ns.wconn.containsKey(key)) {
                                                OSLink osLink = (OSLink) ns.wconn.get(key);
                                                osLink.updateStateV2(port.fiberState, port.powerState, linkId);
                                                osLink.setDirty(true);
                                            } else {
                                                OSLink osLink = new OSLink(ns, null, port, null,
                                                        ns.szOpticalSwitch_Name, hostName, linkId);
                                                osLink.setFlags(OSLink.OSLINK_FLAG_FAKE_NODE);
                                                osLink.updateStateV2(port.fiberState, port.powerState, linkId);
                                                osLink.setDirty(true);
                                                ns.wconn.put(key, osLink);
                                                if (AppConfig.getProperty("lia.Monitor.OSGmap.debug", "false").equals(
                                                        "true")) {
                                                    logger.warning("new OS fake link created: " + osLink);
                                                }
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // now continue with the source type of ports
                    if ((newOsi.osPorts != null) && (newOsi.osPorts.length != 0)) {
                        for (final OSwPort port : newOsi.osPorts) {
                            if (port.type == OSwPort.MULTICAST_PORT) {
                                continue;
                            }
                            final OSwLink link = port.oswLink;
                            if (link == null) {
                                continue; // not interested in this port
                            }
                            OSLink osLink = null;
                            if (link.type == OSwLink.TYPE_HOST) {
                                String linkId = null;
                                if (newOsi.crossConnects != null) {
                                    for (final OSwCrossConn cc : newOsi.crossConnects) {
                                        if ((port.type == OSwPort.INPUT_PORT) && cc.sPort.equals(port)) {
                                            linkId = cc.mlID;
                                            break;
                                        } else if ((port.type == OSwPort.OUTPUT_PORT) && cc.dPort.equals(port)) {
                                            linkId = cc.mlID;
                                            break;
                                        }
                                    }
                                }
                                // create new link
                                String key = OSLink.ToString(ns.szOpticalSwitch_Name, port, link.destination,
                                        link.destinationPortName);
                                if (ns.wconn.containsKey(key)) {
                                    osLink = (OSLink) ns.wconn.get(key);
                                    osLink.updateStateV2(port.fiberState, port.powerState, linkId);
                                    osLink.setDirty(true);
                                } else {
                                    osLink = new OSLink(ns, null, port, link.destinationPortName,
                                            ns.szOpticalSwitch_Name, link.destination, linkId);
                                    osLink.setFlags(OSLink.OSLINK_FLAG_FAKE_NODE);
                                    osLink.updateStateV2(port.fiberState, port.powerState, linkId);
                                    osLink.setDirty(true);
                                    ns.wconn.put(key, osLink);
                                    if (AppConfig.getProperty("lia.Monitor.OSGmap.debug", "false").equals("true")) {
                                        logger.warning("new OS fake link created: " + osLink);
                                    }
                                }

                            } else { // TYPE_SWITCH
                                // set destination based on name
                                // check first for its destination
                                try {
                                    rcNode node = null;
                                    for (Object element : snodes.values()) {
                                        node = (rcNode) element;
                                        if ((node.szOpticalSwitch_Name != null)
                                                && node.szOpticalSwitch_Name.equals(link.destination)) {
                                            String linkId = null;
                                            if (newOsi.crossConnects != null) {
                                                for (final OSwCrossConn cc : newOsi.crossConnects) {
                                                    if ((port.type == OSwPort.INPUT_PORT) && cc.sPort.equals(port)) {
                                                        linkId = cc.mlID;
                                                        break;
                                                    } else if ((port.type == OSwPort.OUTPUT_PORT)
                                                            && cc.dPort.equals(port)) {
                                                        linkId = cc.mlID;
                                                        break;
                                                    }
                                                }
                                            }
                                            String key = OSLink.ToString(ns.szOpticalSwitch_Name, port,
                                                    link.destination, link.destinationPortName);
                                            if (ns.wconn.containsKey(key)) {
                                                osLink = (OSLink) ns.wconn.get(key);
                                                osLink.updateStateV2(port.fiberState, port.powerState, linkId);
                                                osLink.setDirty(true);
                                            } else {
                                                osLink = new OSLink(ns, node, port, link.destinationPortName,
                                                        ns.szOpticalSwitch_Name, link.destination, linkId);
                                                if (AppConfig.getProperty("lia.Monitor.OSGmap.debug", "false").equals(
                                                        "true")) {
                                                    logger.log(Level.FINE, "new OS link created: " + osLink);
                                                }
                                                ns.wconn.put(key, osLink);
                                                osLink.setFlags(OSLink.OSLINK_FLAG_NORMAL);
                                                osLink.setDirty(true);
                                                osLink.updateStateV2(port.fiberState, port.powerState, linkId);
                                            }
                                            break;
                                        }
                                    }
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                    logger.warning(ex.getLocalizedMessage());
                                }
                            }
                        }
                        // second, remove old links from wconn if not any more in map
                        final LinkedList toRemove = new LinkedList();
                        try {
                            for (Iterator it = ns.wconn.keySet().iterator(); it.hasNext();) {
                                Object key = it.next();
                                OSLink osLink = (OSLink) ns.wconn.get(key);
                                if (!osLink.isDirty()) {
                                    toRemove.addLast(key);
                                } else {
                                    osLink.setDirty(false);
                                }
                            }
                        } catch (Exception ex) {
                            logger.warning(ex.getLocalizedMessage());
                        }
                        for (Iterator it = toRemove.iterator(); it.hasNext();) {
                            ns.wconn.remove(it.next());
                        }
                    }
                }
                gupdate();// probably a link changed
            }
        }// end if optical switch eResult
    }

    @Override
    public void processResult(MLSerClient client, Result r) {
        // testAlive() ;
        if (r == null) {
            return;
            // // check if the connection with the farm is lost
            // if (r.Module == null
            // && r.ClusterName.equals("Peers")
            // && r.param_name[0].equals("lostConn")) {
            // rcNode ns = (rcNode) nodes.get(r.FarmName);
            // if(ns == null) return;
            // //ns.errorCount = (int)r.param[0];
            // ErrorNode(ns.sid);
            // //if(ns.errorCount > 1)
            // // removeNode(ns.sid);
            // // if(traff != null)
            // // traff.checkWconn();
            // gupdate();
            // return;
            // }
        }

        if (r.NodeName == null) {
            evaluateGlobal(r);
            return;
        }

        rcNode ns = snodes.get(client.tClientID);
        if (ns == null) {
            return;
        }
        ns.errorCount = 0; // we got some result, reset error count
        ns.time = r.time = NTPDate.currentTimeMillis();
        ns.haux.remove("lostConn");

        if (r.ClusterName.equals("WAN")) {
            addWAN(ns, r);
            gupdate();
            return;
        }

        // System.out.println("<mluc> received result: "+r);
        // if ("FDT_SC10".equals(r.FarmName) && "Links_Summary".equals(r.ClusterName))
        // System.out.println("<mluc> received result from FDT_SC10");
        // if ( "Links_Summary".equals(r.ClusterName) )
        // System.out.println("<mluc> received result for cluster Links_Summary");
        if (hasGlobeLinks) {
            final String cName = r.ClusterName;
            final String fName = r.FarmName;

            final boolean hNF = (globeLinksType == GlobeLinksType.NETFLOW) && (cName != null)
                    && cName.equals("NetFlow");
            final boolean hFDT = (globeLinksType == GlobeLinksType.FDT) && (cName != null)
                    && cName.equals("Links_Summary") && (fName != null) && fName.equals("FDT_SC10");
            final boolean hOF = (globeLinksType == GlobeLinksType.OPENFLOW) && (fName != null)
                    && openflowServicesSet.contains(fName);
            if (hNF || hFDT || hOF) {
                addNetFlow(ns, r);
                gupdate();
                return;
            }
        }

        String ipx = IpAddrCache.getIPaddr(r.NodeName, true);
        rcNode nw = getNodeByIP(ipx);

        if (nw == null) {
            return;
        }

        if (r.ClusterName.indexOf("ABPing") >= 0) {
            ns.conn.put(nw, r);
        } else {
            System.out.println("UNK:" + r);
        }
        // gupdate();
    }

    protected void addWAN(rcNode nw, Result r) {

        if ((r == null) || (r.param_name == null) || (r.param == null)) {
            return;
        }

        if (traff != null) {
            traff.newData(r, nw);
        }
        Object objLink;
        for (int i = 0; i < r.param_name.length; i++) {
            objLink = nw.wconn.get(r.param_name[i]);
            if ((objLink != null) && !(objLink instanceof ILink)) {
                continue;
            }
            ILink il = (ILink) objLink;
            if (il == null) {
                il = iNetGeo.getLink(r.param_name[i]);
                if (il != null) {
                    // ILink l1 = fixGeo.resolveILink(l);
                    il.data = Double.valueOf(r.param[i]);
                    il.time = r.time;
                    nw.wconn.put(r.param_name[i], il);
                }
            } else {
                il.data = Double.valueOf(r.param[i]);
                il.time = r.time;
            }
        }
    }

    /**
     * TODO: change from NetFlow to FDT
     * - search the fdt link in inetgeo
     * - set values IN and OUT for all links returned
     * search for all links in wconn that are of form FDT_ + r.NodeName + _IN | _OUT + i
     * 
     * @author mluc
     * @since Nov 15, 2006
     * @param nw
     * @param r
     */
    protected void addNetFlow(rcNode nw, Result r) {
        Object objLink;
        switch (globeLinksType) {
        case FDT: {
            // System.out.println("<mluc> got fdt result: "+r);
            // try to find the first link that should be for this result and of type FDT
            int index = 1;
            objLink = nw.wconn.get("FDT_" + r.NodeName + "_IN" + index);
            if ((objLink != null) && !(objLink instanceof NFLink)) {
                // System.out.println("1");
                return; // invalid object stored??
            }

            // identify the indexes for NET_IN and NET_OUT
            int indexParamIN = -1, indexParamOUT = -1;
            for (int i = 0; i < r.param_name.length; i++) {
                if (r.param_name[i].startsWith("NET_IN")) {
                    indexParamIN = i;
                } else if (r.param_name[i].startsWith("NET_OUT")) {
                    indexParamOUT = i;
                }
            }
            // System.out.println("indexparamin="+indexParamIN+" indexparamout="+indexParamOUT);
            if (objLink == null) {
                // create the links to set the data for them
                ArrayList links = NFLink.convert(iNetGeo.getLinks("FDT_" + r.NodeName));
                if (links == null) {
                    links = NFLink.convert(iNetGeo.getLinks(r.NodeName));
                }
                if (links != null) {
                    // as the order doesn't matter, because the links have geografical coordinates,
                    // all links have same name and only the suffix _IN or _OUT differ
                    int indexIN = 1, indexOUT = 1;
                    // System.out.println("there are "+links.size()+" links for node "+r.NodeName);
                    for (int i = 0; i < links.size(); i++) {
                        NFLink link = (NFLink) links.get(i);
                        if (link.name.endsWith("_IN")) {
                            if (indexParamIN != -1) {
                                link.data = Double.valueOf(r.param[indexParamIN]);
                                // System.out.println("parameter throughput has value: "+il.data);
                                link.time = r.time;
                            } else {
                                link.data = Double.valueOf(0);
                            }
                            nw.wconn.put("FDT_" + r.NodeName + "_IN" + indexIN, link);
                            // System.out.println("<mluc> created segment "+indexIN+" for link "+link);
                            indexIN++;
                            continue;
                        }
                        if (link.name.endsWith("_OUT")) {
                            if (indexParamOUT != -1) {
                                link.data = Double.valueOf(r.param[indexParamOUT]);
                                link.time = r.time;
                            } else {
                                link.data = Double.valueOf(0);
                            }
                            nw.wconn.put("FDT_" + r.NodeName + "_OUT" + indexOUT, link);
                            // System.out.println("<mluc> created segment "+indexOUT+" for link "+link);
                            indexOUT++;
                            continue;
                        }
                    }
                }
                // else
                // System.out.println("Am intrat pe else la links");
            } else {
                // System.out.println("am intrat pe else");
                // if the link IN with index 1 is there, it means that there are also other links with bigger
                // indexes
                // and for OUT also
                NFLink link = null;
                do {
                    if (indexParamIN != -1) {
                        link = (NFLink) nw.wconn.get("FDT_" + r.NodeName + "_IN" + index);
                        if (link != null) {
                            link.data = Double.valueOf(r.param[indexParamIN]);
                            link.time = r.time;
                            // System.out.println("<mluc> updated segment "+index+" for link "+link);
                        }
                    }
                    if (indexParamOUT != -1) {
                        link = (NFLink) nw.wconn.get("FDT_" + r.NodeName + "_OUT" + index);
                        if (link != null) {
                            link.data = Double.valueOf(r.param[indexParamOUT]);
                            link.time = r.time;
                            // System.out.println("<mluc> updated segment "+index+" for link "+link);
                        }
                    }
                    index++;
                } while (link != null);
            }
            break;
        }
        case NETFLOW: {
            int i;
            for (i = 0; i < r.param_name.length; i++) {
                if ("throughput".equals(r.param_name[i])) {
                    break;
                }
            }
            if (i >= r.param_name.length) {
                // System.out.println("throughput parameter not found in result");
                return; // no param present
            }
            // System.out.println("throughput parameter found on position "+i);
            objLink = nw.wconn.get(/* "NetFlow_"+ */r.NodeName);// );
            if ((objLink != null) && !(objLink instanceof NFLink)) {
                return; // invalid object stored??
            }
            NFLink il = (NFLink) objLink;
            if (il == null) {
                il = NFLink.convert(iNetGeo.getLink("NetFlow_" + r.NodeName));
                if (il != null) {
                    if (il.name.startsWith("NetFlow_")) {
                        il.name = il.name.substring(8);
                    }
                    il.data = Double.valueOf(r.param[i]);
                    // System.out.println("parameter throughput has value: "+il.data);
                    il.time = r.time;
                    nw.wconn.put(/* "NetFlow_"+ */r.NodeName, il);
                }
            } else {
                il.data = Double.valueOf(r.param[i]);
                // System.out.println("parameter throughput has value: "+il.data);
                il.time = r.time;
            }
            break;
        }
        case OPENFLOW: {
            if (DEBUG) {
                System.out.println("OF res: " + r);
            }
            System.out.println("OF res: " + r);
            int idx = r.getIndex("traffic_out");
            boolean bLink = false;
            if (idx < 0) {
                idx = r.getIndex("Bandwidth");
                if (idx < 0) {
                    return;
                }
                bLink = true;
            }
            final String nodeName = r.NodeName;
            if (nodeName == null) {
                return;
            }

            final String[] tks = nodeName.split("_");
            if ((tks == null) || (tks.length < 1)) {
                return;
            }

            final String iNetGeoFlowName = ((bLink) ? "NetLink_" : "OpenFlow_") + tks[0];

            final String linkName = ((bLink) ? "NetLink_" : "OpenFlow_") + nodeName;
            objLink = nw.wconn.get(linkName);
            if ((objLink != null) && !(objLink instanceof NFLink)) {
                return; // invalid object stored??
            }

            final double val = r.param[idx];
            if (objLink == null) {
                final ArrayList ilinksList = iNetGeo.getLinks(iNetGeoFlowName);
                final ArrayList<NFLink> nfLinks = NFLink.convert(ilinksList, linkName, true);
                if (nfLinks != null) {
                    for (final NFLink nf : nfLinks) {
                        System.out.println("----->>>> OF NFLink: " + nf);
                        if (nf.name.startsWith("OpenFlow_")) {
                            nf.name = nf.name.substring(9);
                        }
                        nf.data = Double.valueOf(val);
                        nf.time = r.time;
                        if (DEBUG) {
                            if (bLink) {
                                System.out.println("New NetLink link added: " + linkName + " ---> " + nf);
                            } else {
                                System.out.println("New OpenFlow link added: " + linkName + " ---> " + nf);
                            }
                        }
                        nw.wconn.put(linkName, nf);
                    }
                }
            } else {
                NFLink link = null;
                link = (NFLink) nw.wconn.get(linkName);
                if (link != null) {
                    if (DEBUG) {
                        if (bLink) {
                            System.out.println("UPDATE NetLink link added: " + linkName + " ---> " + link);
                        } else {
                            System.out.println("UPDATE OpenFlow link added: " + linkName + " ---> " + link);
                        }
                    }

                    link.time = r.time;
                    link.data = Double.valueOf(val);
                }
            }
            break;
        }
        case UNDEFINED:
            break;
        default:
            break;

        }
    }

    @Override
    public void removeFarmNode(rcNode n) {
        check4deadLinks();
        if (traff != null) {
            traff.removeWLinksFrom(n);
        }

        // remove OSLinks
        removeOSLinks(n);
    }

    private void removeOSLinks(rcNode n) {
        Object objLink;
        try {
            // remove all links starting from this node
            for (Iterator it = n.wconn.values().iterator(); it.hasNext();) {
                objLink = it.next();
                if (objLink instanceof OSLink) {
                    OSLink link = (OSLink) objLink;
                    logger.log(Level.INFO, "OS: " + link + " detroyed start node -> removing");
                    // n.wconn.remove(link.toString());
                    it.remove();
                }
            }
        } catch (Exception ex) {
            // ignore error, will be removed at a later moment or never
        }
        try {
            // remove all links to get into this node
            for (int i = 0; i < vnodes.size(); i++) {
                rcNode node = vnodes.get(i);
                for (Iterator it = node.wconn.values().iterator(); it.hasNext();) {
                    objLink = it.next();
                    if (objLink instanceof OSLink) {
                        if (((OSLink) objLink).rcDest == n) {
                            logger.log(Level.INFO, "OS: " + objLink + " detroyed end node -> removing");
                            it.remove();
                        }
                        ;
                    }
                }
            }
        } catch (Exception ex) {
            // ignore error, will be removed at a later moment or never
        }
    }

    void check4deadNodes() {
        long now = NTPDate.currentTimeMillis();
        for (final rcNode n : snodes.values()) {
            if (n == null) {
                continue;
            }
            if ((now - n.time) > NODE_EXPIRE_TIME) {
                n.haux.put("lostConn", "1");
            }
        }
    }

    void check4deadLinks() {

        long now = NTPDate.currentTimeMillis();
        for (final rcNode n : snodes.values()) {
            if ((n == null) || (n.getOpticalSwitch() != null)) {
                continue;
            }
            // check wan links...
            Object objLink;
            for (Enumeration enl = n.wconn.keys(); enl.hasMoreElements();) {
                Object key = enl.nextElement();
                objLink = n.wconn.get(key);
                if (objLink instanceof ILink) {
                    ILink link = (ILink) objLink;
                    if ((now - link.time) > WAN_LINK_EXPIRE_TIME) {
                        logger.log(Level.INFO, "WAN: " + link.name + " inactive -> removing");
                        n.wconn.remove(key);
                    }
                } else if (objLink instanceof OSLink) {
                    OSLink link = (OSLink) objLink;
                    if ((now - link.time) > OS_LINK_EXPIRE_TIME) {
                        logger.log(Level.INFO, "OS: " + link + " inactive -> removing");
                        n.wconn.remove(key);
                    }
                } else if (objLink instanceof NFLink) {
                    NFLink link = (NFLink) objLink;
                    if ((now - link.time) > NETFLOW_LINK_EXPIRE_TIME) {
                        switch (globeLinksType) {
                        case FDT: {
                            logger.log(Level.WARNING, "FDT: " + link + " inactive -> removing");
                            break;
                        }
                        case NETFLOW: {
                            logger.log(Level.INFO, "NetFlow: " + link + " inactive -> removing");
                            break;
                        }
                        case OPENFLOW: {
                            logger.log(Level.WARNING, "OpenFlow: " + link + " inactive -> removing");
                            break;
                        }
                        case UNDEFINED: {
                            logger.log(Level.WARNING, " *UNDEFINED LINK* : " + link + " inactive -> removing");
                            break;
                        }
                        default: {
                            logger.log(Level.WARNING, " *UNDEFINED _ DEFAULT _ LINK* : " + link
                                    + " inactive -> removing");
                            break;
                        }
                        }
                        n.wconn.remove(key);
                    }
                }
            }
            // and ping links
            for (Enumeration enw = n.conn.keys(); enw.hasMoreElements();) {
                rcNode nw = (rcNode) enw.nextElement();
                // if node deleted, remove this
                if (!vnodes.contains(nw)) {
                    n.conn.remove(nw);
                    continue;
                }
                Result r = (Result) n.conn.get(nw);
                if (r == null) {
                    continue;
                }
                if ((now - r.time) > PING_LINK_EXPIRE_TIME) {
                    logger.log(Level.INFO, "ABPing link [" + n.UnitName + "->" + nw.UnitName + "] inactive -> removing");
                    n.conn.remove(nw);
                }
            }
        }
    }
}
