package lia.Monitor.JiniClient.CommonGUI;

import java.net.InetAddress;
import java.net.URL;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;

import lia.Monitor.ClientsFarmProxy.ProxyServiceEntry;
import lia.Monitor.JiniClient.CommonGUI.Groups.GroupsPanel;
import lia.Monitor.JiniClient.CommonGUI.Jogl.DataGlobals;
import lia.Monitor.JiniClient.CommonGUI.Topology.NetTopologyAnalyzer;
import lia.Monitor.JiniClient.CommonJini.JiniClient;
import lia.Monitor.control.MonitorControl;
import lia.Monitor.monitor.DataStore;
import lia.Monitor.monitor.ExtendedSiteInfoEntry;
import lia.Monitor.monitor.GenericMLEntry;
import lia.Monitor.monitor.Gresult;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MLControlEntry;
import lia.Monitor.monitor.MonMessageClientsProxy;
import lia.Monitor.monitor.MonaLisaEntry;
import lia.Monitor.monitor.OSLink;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.SiteInfoEntry;
import lia.Monitor.monitor.eResult;
import lia.Monitor.tcpClient.Buffer;
import lia.Monitor.tcpClient.ConnMessageMux;
import lia.Monitor.tcpClient.MLSerClient;
import lia.Monitor.tcpClient.ResultProcesserInterface;
import lia.Monitor.tcpClient.tClient;
import lia.Monitor.tcpClient.tmClient;
import lia.util.StringFactory;
import lia.util.ntp.NTPDate;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;

abstract public class SerMonitorBase extends JiniClient implements LocalDataFarmClient, ResultProcesserInterface {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(SerMonitorBase.class.getName());

    public MainBase main;

    protected Map<ServiceID, rcNode> snodes;

    protected Vector<rcNode> vnodes;

    // a new hash of farms previously discovered and currently removed... see
    // point 8) in
    // http://www.hep.caltech.edu/~litvin/evo-ml-reqs-v6.txt
    protected final Map<ServiceID, String> removedNodes;

    protected HashSet<String> global_param;

    public Vector<graphical> graphs;

    public NetTopologyAnalyzer netTopology;

    protected final long linkExpiringDelta = 2 * 60 * 1000; // after 2 minutes,
                                                            // links expire

    protected long lastRedrawTime = 0;

    protected long redrawingDelta = 2 * 1000; // don't redraw more often than
                                              // this

    // private boolean bDebug=false;
    // private Timer removeUnselGroupsTimer = new Timer();

    private volatile ConnMessageMux proxytmClient = null;

    private final Map<ServiceID, MonMessageClientsProxy> knownConfigurations = new ConcurrentHashMap<ServiceID, MonMessageClientsProxy>();

    public GroupsPanel basicPanel = null;

    public static final Map<String, MonitorControl> controlModules = new ConcurrentHashMap<String, MonitorControl>();

    // TODO - smth is fishy here
    // there are two PortAdminListener interfaces in two different packages ?!?!
    // public static final Map<String, PortAdminListener> osControlModules = new
    // ConcurrentHashMap<String,
    // PortAdminListener>();
    public static final Map<String, Object> osControlModules = new ConcurrentHashMap<String, Object>();

    final Vector<ServiceItem> SItoInit; // contains ServiceItem-s

    final Hashtable<ServiceID, ServiceThread> workingSTreads; // contains
                                                              // key=ServiceID;
                                                              // value =
                                                              // ServiceThread

    final Vector<ServiceThread> sThreadsPool; // contains ServiceThreads

    private static int STHREADS_POOL_SIZE = 10;

    private Buffer buffer;

    /** time when this object was initialized */
    public long lStartTime = -1;

    /**
     * This field is a hack used for providing the unit for some known
     * parameters
     */
    private lia.Monitor.GUIs.Registry registry;

    /** saves the last count of allFarms computed in groups update function */
    private int nLastFarmsGroupsCount = -1;

    /**
     * last time when the groups vector obtained from allFarms vector was
     * updated
     */
    private long nLastFarmsGroupsUpdate = -1;

    /** minimum time between two consecutive updates of menu groups list */
    private final long UPDATE_TIME_FARMS_GROUPS = 10 * 1000;

    public rcNode getNodeByIP(String ipx) {
        for (final rcNode n : snodes.values()) {
            if ((n == null) || (n.IPaddress == null)) {
                continue;
            }
            if (n.IPaddress.equals(ipx)) {
                return n;
            }
        }
        return null;
    }

    public void setBasicPanel(GroupsPanel panel) {

        this.basicPanel = panel;
    }

    /** get a rcNode that has a "Tracepath" cluster by IP address */
    public rcNode getTracepathNodeByIP(String ip) {
        for (final rcNode n : vnodes) {
            if (n.IPaddress.equals(ip)) {
                final MonMessageClientsProxy m = knownConfigurations.get(n.sid);
                if (m == null) {
                    return null;
                }
                MFarm config = (MFarm) m.result;
                if (config.getCluster("Tracepath") != null) {
                    return n;
                }
            }
        }
        return null;
    }

    @Override
    public synchronized void closeProxyConnection() {
        if (proxytmClient != null) {
            proxytmClient.closeProxyConnection();
            proxytmClient = null;
        } // if
    } // closeProxyConnection

    class ServiceThread extends Thread {

        boolean hasToRun = true;

        String idleStatus = "(ML) Service Thread - IDLE";

        String busyStatus = "(ML) Service Thread for ";

        int id = 0;

        public ServiceThread(int id) {
            setName(idleStatus);
            this.id = id;
        }

        @Override
        public void run() {
            // System.out.println("STH["+id+"] - created");
            while (hasToRun) {
                ServiceItem si = null;
                synchronized (SItoInit) {
                    // System.out.println("STH["+id+"] waiting for job ; wth="
                    // +workingSTreads.size()+ " si="+SItoInit.size());

                    while (SItoInit.size() == 0) {
                        long startedToWait = System.currentTimeMillis();
                        long maxWait = 30 * 1000;
                        try {
                            SItoInit.wait(maxWait);
                        } catch (InterruptedException ex) {
                            // ignore
                        }
                        if ((SItoInit.size() == 0) && (workingSTreads.size() == 0)) {
                            // also check workingSThreads because another thread
                            // might
                            // have got my job before me ...
                            long now = System.currentTimeMillis();
                            if ((now - startedToWait) >= maxWait) {
                                // System.out.println("STH["+id+"] - no more service items... giving up");
                                sThreadsPool.remove(this);
                                hasToRun = false;
                                return;
                            }
                        }
                    }
                    si = SItoInit.remove(SItoInit.size() - 1);
                    // System.out.println("STH["+id+"] - got job "+si.serviceID);
                    workingSTreads.put(si.serviceID, this);
                }
                setName(busyStatus + si.serviceID);
                try {
                    inializeMonitorUnit(si);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "While initializingMonitorUnit, got", t);
                }
                synchronized (SItoInit) {
                    workingSTreads.remove(si.serviceID);
                    SItoInit.notifyAll();
                    // System.out.println("STH["+id+"] - finished job; wth="
                    // +workingSTreads.size()+ " si="+SItoInit.size());
                }
                setName(idleStatus);
            }
        }
    }

    // private static final String[] getGroups(String group) {
    // String[] retV = null;
    // if (group == null || group.length() == 0) return null;
    // StringTokenizer st = new StringTokenizer(group.trim(), ",");
    // int count = st.countTokens();
    // if (count > 0) {
    // retV = new String[count];
    // int vi = 0;
    // while (st.hasMoreTokens()) {
    // String token = st.nextToken().trim();
    // if (token != null && token.length() > 0) {
    // retV[vi++] = token;
    // }
    // }
    // }
    // return retV;
    // }

    public SerMonitorBase(MainBase main, Class<?> mainClientClass) {
        super(mainClientClass, true, false);
        this.main = main;
        this.lStartTime = NTPDate.currentTimeMillis();
        snodes = new ConcurrentHashMap<ServiceID, rcNode>();
        vnodes = new Vector<rcNode>();
        removedNodes = new ConcurrentHashMap<ServiceID, String>();
        global_param = new HashSet<String>();
        graphs = new Vector<graphical>();
        netTopology = new NetTopologyAnalyzer(snodes, this);

        sThreadsPool = new Vector<ServiceThread>();
        SItoInit = new Vector<ServiceItem>();
        workingSTreads = new Hashtable<ServiceID, ServiceThread>();
    } // MSerMonitorBase

    /** init the pool of service threads */
    void initSthreadsPool() {
        for (int i = 0; i < STHREADS_POOL_SIZE; i++) {
            ServiceThread sth = new ServiceThread(i);
            sth.start();
            // System.out.println("Starting another STH "+sThreadsPool.size());
            sThreadsPool.add(sth);
        }
    }

    public void init2() {
        // if ( bDebug )
        // System.out.println("init2 in SerMonitorBase");
        final SerMonitorBase smb = this;
        Thread smbInit = new Thread() {

            @Override
            public void run() {
                try {
                    smb.init();
                    initSthreadsPool();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Error in SerMonitorBase init ", t);
                }
            }
        };
        smbInit.start();
        // TODO: to comment while in beta testing
        /* ExportClientStatistics statExporter = */
        // new ExportClientStatistics(smb);
        registry = new lia.Monitor.GUIs.Registry(); // initialize only once in
                                                    // SerMonitorBase, used it
                                                    // in any plot class
        registry.init();
        buffer = new Buffer(this, "ML (Result Buffer for result of SerMonitorBase)");
        buffer.start();
    } // init

    public lia.Monitor.GUIs.Registry getRegistry() {
        return registry;
    }

    /** return the number of monitored nodes */
    public int getNodesCount() {
        return snodes.size();
    }

    public void addGraph(graphical g) {
        graphs.add(g);
        g.setSerMonitor(this);
        g.setNodes(snodes, vnodes);
        synchronized (global_param) {
            if (global_param.size() > 0) {
                for (String string : global_param) {
                    g.new_global_param(string);
                }
            }
        }
    } // addGraph

    /**
     * removes a graphical from set Feb 2, 2005 - 7:45:14 PM
     */
    public void removeGraph(graphical g) {
        graphs.remove(g);
    }

    public void gupdate() {
        long now = NTPDate.currentTimeMillis();
        if ((now - lastRedrawTime) < redrawingDelta) {
            return;
        }

        for (int i = 0; i < graphs.size(); i++) {
            graphs.elementAt(i).gupdate();
        }
        // main.sMon.update();
        // if ( main.jlbStatistics!=null )
        // main.jlbStatistics.setText(main.sMon.getStatistics());
        lastRedrawTime = now;
    } // gupdate

    public void new_global_param(String module) {
        for (int i = 0; i < graphs.size(); i++) {
            graphs.elementAt(i).new_global_param(module);
        }
    } // new_global_param

    Timer tGroupViewUpdate = null;

    Object objSyncGVU = new Object();

    long nLastGVUpdateRequest = -1;

    /**
     * selection of groups changed, so inform panels and proxy
     */
    protected void GroupViewUpdate() {
        if (proxytmClient != null) {
            /**
             * inform proxy that the selection of groups has changed but with a
             * small delay, so that if the user selects several groups one by
             * one, the client would send to the proxy only one message
             */
            synchronized (objSyncGVU) {
                nLastGVUpdateRequest = NTPDate.currentTimeMillis();
                // if ( bDebug )
                // System.out.println("<mluc> new request to group view update");
                if (tGroupViewUpdate == null) {
                    tGroupViewUpdate = new Timer();
                    tGroupViewUpdate.schedule(new TimerTask() {

                        @Override
                        public void run() {
                            synchronized (objSyncGVU) {
                                long curTime = NTPDate.currentTimeMillis();
                                if ((curTime - nLastGVUpdateRequest) > 5000) {// if
                                                                              // at
                                                                              // least
                                                                              // 5
                                                                              // seconds
                                                                              // have
                                                                              // passed
                                    // since laste update request/groups
                                    // selection modification
                                    // then send message to proxy
                                    tGroupViewUpdate = null;
                                    if (proxytmClient != null) {
                                        proxytmClient.sendMsg(createActiveGroupsMessage());
                                    }
                                    // and cancel thread
                                    cancel();
                                    // if ( bDebug )
                                    // System.out.println("<mluc> send groups update request to proxy");
                                }
                            }
                        }

                    }, 1000, 1000);// check from second to second if it should
                                   // update groups
                }
            }
        }
        gupdate();
    } // GroupViewUpdate

    @Override
    public synchronized void AddProxyService(ServiceItem si) throws Exception {
        if (si == null) {
            return;
        }
        waitServiceThreads("before setting new Proxy");
        Entry[] proxyEntry = si.attributeSets;
        if (proxyEntry != null) {
            if (proxyEntry.length > 0) {
                int portNumber = ((ProxyServiceEntry) proxyEntry[0]).proxyPort.intValue();
                String ipAddress = ((ProxyServiceEntry) proxyEntry[0]).ipAddress;

                String hostName = ((ProxyServiceEntry) proxyEntry[0]).proxyName;
                if (hostName == null) {
                    hostName = IpAddrCache.getHostName(ipAddress, true);
                } else {
                    IpAddrCache.putIPandHostInCache(ipAddress, hostName);
                }
                if (hostName == null) {
                    hostName = ipAddress;
                }

                // InetAddress inetAddress = InetAddress.getByName(ipAddress);
                logger.log(Level.INFO, " [ SerMonitorBase ] [ AddProxyService ] CONNECT PROXY at " + hostName + ":"
                        + portNumber);

                /**
                 * first try to use the ipaddr cache to find immediately the
                 * inetaddress object. if that fails, find it with the waiting
                 * version
                 */
                InetAddress inetAddress = IpAddrCache.getInetAddress(hostName);
                if (inetAddress == null) {
                    inetAddress = IpAddrCache.getInetAddressHelper(hostName);
                }
                // try to start connection only if we have the address object
                if (inetAddress != null) {
                    proxytmClient = new tmClient(inetAddress, portNumber, knownConfigurations, this);
                    proxytmClient.startCommunication();
                }
            } // if
        }// if
    } // AddProxyService

    public void inializeMonitorUnit(ServiceItem si) {
        try {
            DataStore ds = (DataStore) si.service;
            if (ds == null) {
                logger.log(Level.WARNING, "Service could not be deserialized", new Object[] { si });
                return;
            }
            MonaLisaEntry mle = getEntry(si, MonaLisaEntry.class);
            SiteInfoEntry sie = getEntry(si, SiteInfoEntry.class);
            ExtendedSiteInfoEntry esie = getEntry(si, ExtendedSiteInfoEntry.class);
            MLControlEntry mlce = getEntry(si, MLControlEntry.class);
            GenericMLEntry gmle = getEntry(si, GenericMLEntry.class);

            // System.out.println("MonalisaEntry: "+mle);
            // System.out.println("SiteInfoEntry: "+sie);
            // System.out.println("ExtendedSiteInfoEntry: "+esie);
            // System.out.println("MLControlEntry: "+mlce);
            // System.out.println("GenericMLEntry: "+gmle);

            int MLCP = -1; // control port
            String ipad = null; // IP address
            String host = null; // hostname
            String un = null; // unit name
            int mlPort = 9000; // port ?!
            int registryPort = Registry.REGISTRY_PORT; // 1099 ?!

            if (mlce != null) {
                MLCP = mlce.ControlPort.intValue();
            }
            if (sie != null) {
                ipad = sie.IPAddress;
                un = sie.UnitName;
                // System.out.println
                // ("<mickyd> Add node: "+mle.Name+" for unit name: "+un);
                mlPort = (sie.ML_PORT == null) ? 9000 : sie.ML_PORT.intValue();
                registryPort = (sie.REGISTRY_PORT == null) ? Registry.REGISTRY_PORT : sie.REGISTRY_PORT.intValue();
            } else {
                logger.log(Level.SEVERE, "\n\n [HANDLED] SiteInfoEntry == null for " + si + " - discarding it \n\n");
                return;
            }
            if ((un == null) || (ipad == null)) {
                logger.log(Level.WARNING, "UnitName or IPaddress null for " + si + " - discarding it");
                return;
            }
            if ((gmle != null) && (gmle.hash != null)) {
                host = (String) gmle.hash.get("hostName");
                String ip = (String) gmle.hash.get("ipAddress");
                if (!ipad.equals(ip)) {
                    logger.log(Level.WARNING, "IP address differs in gmle from service for " + un + " serIP=" + ipad
                            + " gmleIP=" + ip);
                }
            } else {
                logger.log(Level.WARNING, "No gmle host/ip information for " + un);
            }
            if (host == null) {
                host = IpAddrCache.getHostName(ipad, false);
            } else {
                IpAddrCache.putIPandHostInCache(ipad, host);
            }
            // if(host.indexOf("vpac") >=0 || un.indexOf("wn1") >=0)
            // System.out.println("un="+un+" host="+host+" ip="+ipad);
            tClient tcl = new tClient(un, ipad, host, mlPort, registryPort, MLCP, mle, sie, esie, proxytmClient,
                    si.serviceID);
            if (logger.isLoggable(Level.FINE) && (esie != null) && (mle != null)) {
                logger.log(Level.FINE, mle.Group + "\t/\t" + mle.Name + "\t/\t" + " JVM version: " + esie.JVM_VERSION);
            }
            tcl.addFarmClient(si.serviceID);
            tcl.setServiceEntry(si);
            addNode(si, ds, tcl, un, ipad);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error while adding node", t.getMessage());
            t.printStackTrace();
            removeNode(si.serviceID);
        }
    }

    @Override
    public boolean AddMonitorUnit(ServiceItem si) {
        // logger.log(Level.INFO, "AddMonitorUnit sid="+si.serviceID);
        // long t1 = NTPDate.currentTimeMillis();
        // inializeMonitorUnit(si);
        // long t2 = NTPDate.currentTimeMillis();
        // System.out.println("Node added in "+(t2-t1)+" millis");
        synchronized (SItoInit) {
            if (!SItoInit.contains(si.serviceID) && !workingSTreads.containsKey(si.serviceID)) {
                SItoInit.add(si);
                if (sThreadsPool.size() < STHREADS_POOL_SIZE) {
                    ServiceThread sth = new ServiceThread(sThreadsPool.size());
                    sth.start();
                    // System.out.println("Starting another STH "+sThreadsPool.size());
                    sThreadsPool.add(sth);
                }
                SItoInit.notifyAll();
                if (removedNodes.containsKey(si.serviceID)) {
                    removedNodes.remove(si.serviceID);
                }
                return true;
            }
        }
        return false;
        // if (sthreads.containsKey(si.serviceID)) {
        // if (logger.isLoggable(Level.FINER)) {
        // logger.log(Level.FINER, " An active thread is running for SID=" +
        // si.serviceID);
        // }
        // return;
        // }
        //
        // //ADDED FROM ServiceThread
        // //TODO - Verify it AGAIN!
        //
        // ServiceThread at = new ServiceThread(si);
        // sthreads.put(si.serviceID, at);
        // at.start();

    } // AddMonitorUnit

    /**
     * Wait for service threads to finish. @see
     * lia.Monitor.JiniClient.actualizeFarms() and AddProxyService for details
     * about this.
     */
    @Override
    public void waitServiceThreads(String message) {
        synchronized (SItoInit) {
            while ((workingSTreads.size() > 0) || (SItoInit.size() > 0)) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Waiting for STH (working:" + workingSTreads.size() + ", SI to init:"
                            + SItoInit.size() + ") to finish " + message);
                }
                try {
                    SItoInit.wait();
                } catch (InterruptedException e) {
                    logger.log(Level.WARNING, "Interrupted while waiting.", e);
                }
            }
        }
    }

    public ImageIcon loadIcon(String resource) {
        ImageIcon ico = null;
        try {
            // URL resLoc = myClassLoader.getResource(resource);
            URL resLoc = new URL(resource);
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, " Icon URL = " + resLoc);
            }
            ico = new ImageIcon(resLoc);
        } catch (Exception e) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " Failed to get icon " + resource, e);
            }
        }
        return ico;
    }

    @Override
    public void newFarmResult(MLSerClient client, Object ro) {

        if (buffer == null) {
            return;
        }
        StringFactory.convert(ro);
        buffer.newFarmResult(client, ro);
    }

    @Override
    public void process(MLSerClient client, Object ro) {
        if (ro == null) {
            return;
        }

        if (ro instanceof Gresult) {
            setGlobalVal(client, (Gresult) ro);
            return;
        } else if (ro instanceof Result) {
            Result r = (Result) ro;
            processResult(client, r);
        } else if (ro instanceof eResult) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, " Got eResult " + ro);
            }
            processResult(client, (eResult) ro);
        } else if (ro instanceof Vector) {
            Vector<?> vr = (Vector<?>) ro;
            if (vr.size() == 0) {
                return;
            }
            for (int i = 0; i < vr.size(); i++) {
                process(client, vr.elementAt(i));
            }
        } else {
            logger.log(Level.WARNING, " Wrong Result type in SerMonitorBase ! ", new Object[] { ro });
            return;
        }
    }

    abstract public void processResult(MLSerClient client, Result r);

    abstract public void processResult(MLSerClient client, eResult er);

    public void setGlobalVal(MLSerClient client, Gresult gr) {

        rcNode ns = snodes.get(client.tClientID);
        if (ns == null) {
            return;
        }
        ns.time = NTPDate.currentTimeMillis();
        ns.global_param.put(gr.Module, gr);

        synchronized (global_param) {
            if (!global_param.contains(gr.Module)) {
                new_global_param(gr.Module);
                global_param.add(gr.Module);
            }
        }
        gupdate();
    }

    /**
     * the position for node is about to change, so some data for it must be
     * invalidated:<br>
     * the links to and from this node
     * 
     * @param node
     */
    public void reconsiderPositionForNodes() {
        rcNode node;
        OSLink osLink;
        // first update all nodes positions
        for (int i = 0; i < vnodes.size(); i++) {
            node = vnodes.get(i);
            setNodePosition(node);
        }
        // and then update links between them
        for (int i = 0; i < vnodes.size(); i++) {
            node = vnodes.get(i);
            if (node != null) {
                try {
                    // search for oslinks that leave this node to correct their
                    // position
                    for (Iterator it2 = node.wconn.values().iterator(); it2.hasNext();) {
                        Object objLink = it2.next();
                        if (!(objLink instanceof OSLink)) {
                            continue;
                        }
                        osLink = (OSLink) objLink;
                        float srcLat, srcLong, dstLat, dstLong;
                        srcLat = DataGlobals.failsafeParseFloat(osLink.rcSource.LAT, -21.22f);
                        srcLong = DataGlobals.failsafeParseFloat(osLink.rcSource.LONG, -111.15f);
                        dstLat = DataGlobals.failsafeParseFloat(osLink.rcDest.LAT, -21.22f);
                        dstLong = DataGlobals.failsafeParseFloat(osLink.rcDest.LONG, -111.15f);
                        // System.out.println("Link from "+osLink.rcSource.UnitName+" to "+osLink.rcDest.UnitName);
                        if ((srcLat != osLink.fromLAT) || (srcLong != osLink.fromLONG) || (dstLat != osLink.toLAT)
                                || (dstLong != osLink.toLONG)) {
                            // update link's start and end coordinates
                            // System.out.println("Change from "+osLink.rcSource.UnitName+" to "+osLink.rcDest.UnitName+" new coordinates: ("+srcLat+","+srcLong+")->("+dstLat+","+dstLong+")");
                            osLink.fromLAT = srcLat;
                            osLink.fromLONG = srcLong;
                            osLink.toLAT = dstLat;
                            osLink.toLONG = dstLong;
                        }
                    }
                } catch (Exception ex) {
                    // ignore
                }
                // node.conn.clear();
                // node.wconn.clear();
            }
        }
    }

    /**
     * resets current coordinates for node, taking in account
     * mainBase.bAutomaticPosition
     * 
     * @param node
     */
    public abstract void setNodePosition(rcNode node);

    @Override
    public void portMapChanged(ServiceID id, ArrayList portMap) {
        try {
            final rcNode n = snodes.get(id);
            if ((n != null) && (n.client != null)) {
                n.client.portMapChanged(portMap);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.log(Level.WARNING, "Error updating node " + id, ex);
        }
    }

    @Override
    public void removeNode(ServiceID id) {
        try {
            final rcNode n = snodes.remove(id);
            if (n != null) {
                vnodes.remove(n);
                n.client.deleteLocalClient(null);
                if ((proxytmClient != null) && proxytmClient.isActive()) {
                    proxytmClient.removeFarmClient(id);
                }
                removeFarmNode(n);
                netTopology.removeFarmTraces(n.IPaddress);
                n.client.setActive(false);
                n.client.stopIt();
                if (basicPanel != null) {
                    basicPanel.stopIt(n);
                }
                // logger.log(Level.INFO, "Removed farm " + n.UnitName + " sid="
                // + id);
                if ((n.client != null) && (n.client.trcframe != null) && (n.client.trcframe.address != null)) {
                    MonitorControl m = controlModules.remove(n.client.trcframe.address.getHostAddress() + ":"
                            + n.client.trcframe.remoteRegistryPort);
                    n.client.trcframe.control = null;
                    if (m != null) {
                        m.dispose();
                    }
                }
                removedNodes.put(id, n.toString());
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error removing node " + id, ex);
        }
        gupdate();
        //
        // logger.log(Level.INFO, "Remove farm with id ===> " + id);
        // try {
        // rcNode nn = (rcNode) snodes.get(id);
        // if (nn != null)
        // nn.client.deleteLocalClient(null);
        //
        // try {
        // if(proxytmClient != null)
        // proxytmClient.removeFarmClient(id);
        // } catch (Exception ex) {
        // ex.printStackTrace();
        // }
        // if (snodes.containsKey(id)) {
        // rcNode n1 = (rcNode) snodes.get(id);
        // removeFarmNode(n1);
        // snodes.remove(id);
        // n1.client.setActive(false);
        // vnodes.remove(n1);
        // for (int i = 0; i < vnodes.size(); i++) {
        // rcNode n = (rcNode) vnodes.get(i);
        // n.conn.remove(n);
        // }
        // }
        // } catch (Exception except) {
        // System.out.println("Eroare ciudata");
        // except.printStackTrace();
        // } // try - catch
        // gupdate();
    }

    @Override
    public boolean verifyProxyConnection() {
        if ((proxytmClient == null) || (proxytmClient.verifyProxyConnection() == false)) {
            return false;
        }
        return true;
    }

    public ConnMessageMux getTmClient() {
        return proxytmClient;
    }

    public Vector<rcNode> getVNodes() {

        return vnodes;
    }

    abstract public void addNode(ServiceItem si, DataStore dataStore, tClient client, String unitName, String ipad);

    abstract public void removeFarmNode(rcNode n);

    @Override
    public boolean knownConfiguration(ServiceID farmID) {
        return knownConfigurations.containsKey(farmID);
    }

    public MFarm getConfiguration(ServiceID farmID) {
        return (MFarm) knownConfigurations.get(farmID).result;
    }

    /**
     * neccessary function to corelate the groups available in menu with the
     * received service ids from proxy using the allFarms vector and the message
     * received from time to time from the proxy containing the si-s <br>
     * checks if a specified time has passed since last update to ignore updates
     * that are likely to make no chages. it also checks if the vector of farms
     * has changed, and if it did (increased in size), updates the groups
     */
    @Override
    public void updateGroups() {
        int nCurrentCount = allFarms.size();
        long nCurrentTime = NTPDate.currentTimeMillis();
        if ((nLastFarmsGroupsCount != -1) && (nCurrentCount <= nLastFarmsGroupsCount) && (nLastFarmsGroupsUpdate != -1)
                && ((nCurrentTime - nLastFarmsGroupsUpdate) < UPDATE_TIME_FARMS_GROUPS)) {
            nLastFarmsGroupsCount = nCurrentCount;
            // logger.info("Group menu list update request too soon, ignored.");
            return;
        }
        nLastFarmsGroupsCount = nCurrentCount;
        nLastFarmsGroupsUpdate = nCurrentTime;
        main.addGroups(getFarmsGroups());
    }

    // public void actualizeFarms(Vector activeFarms) {
    //
    // super.actualizeFarms(activeFarms);
    // if (activeFarms == null) //no farm was found
    // return;
    // // main.refreshNumberOfNodes();
    // }
}
