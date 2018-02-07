package lia.Monitor.JiniClient.ReflRouter;

import java.net.InetAddress;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Timer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ClientsFarmProxy.ProxyServiceEntry;
import lia.Monitor.JiniClient.CommonGUI.IpAddrCache;
import lia.Monitor.JiniClient.CommonJini.JiniClient;
import lia.Monitor.monitor.DataStore;
import lia.Monitor.monitor.ExtendedSiteInfoEntry;
import lia.Monitor.monitor.GenericMLEntry;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.MLControlEntry;
import lia.Monitor.monitor.MonaLisaEntry;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.SiteInfoEntry;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.ConnMessageMux;
import lia.Monitor.tcpClient.MLSerClient;
import lia.Monitor.tcpClient.tClient;
import lia.Monitor.tcpClient.tmClient;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;

/**
 * Main Class for the Reflector Router Jini Client
 * 
 * It should compute the MST based on the ABPing results and compare the
 * results with current reflector peers. From this, it will generate the
 * commands for each reflector to change its peers in order to match
 * the MST.
 * 
 * Apart from this, it will create a report on a (say) daily basis, containing
 * - the groups of sepparated reflectors
 * - the reflector pairs with only one peer link
 * - the cycles encountered in the peer connections.
 * This report will be e-mailed to a configured list of destination addresses.
 * 
 * When encountering a cycle, or a change in the peers connection it will send
 * a report containing only the relevant information
 * - cycle formed
 * - new missing peer links
 * - newly sepparated groups
 * Missing pair peer links and sepparated groups will be reported with a configurable
 * delay since noticing - a node could just be started.
 * 
 */
public class Main extends JiniClient implements LocalDataFarmClient {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    private final long INITIAL_DELAY = 30 * 1000; // wait 1 min. for initializing before any analysis
    private final long ANALISYS_PERIOD = 20 * 1000; // wait 1 min. between 2 runs of the algorithms

    Hashtable snodes; // key is node's SID
    Hashtable sthreads; // threads for each sercive (ML farm) (Reflector)

    MST mst; // MST computing class

    public Vector local_filter_pred; //interest in...(for Result )
    private static Timer rerouteTimer = new Timer();
    private final ReflRouter router;
    private ConnMessageMux proxytmClient = null;
    private Hashtable knownConfigurations;
    private final VRVSGraphAnalyzer vgAnalyzer;

    public Main() {
        //super(Main.class, true, false);
        super(null, true, false);
        snodes = new Hashtable();
        sthreads = new Hashtable();

        mst = new MST(snodes);
        mst.simulation = false; // compute commands starting from the previous tree

        local_filter_pred = new Vector();
        local_filter_pred.add(new monPredicate("*", "Peers", "*", -1, -1, null, null));
        local_filter_pred.add(new monPredicate("*", "Internet", "*", -1, -1, null, null));
        local_filter_pred.add(new monPredicate("*", "Reflector", "*", -1, -1, null, null));

        init();
        ReflRouterJiniService.getInstance();

        router = new ReflRouter(mst);
        rerouteTimer.schedule(router, INITIAL_DELAY, 2400);

        vgAnalyzer = new VRVSGraphAnalyzer(snodes);
        rerouteTimer.schedule(vgAnalyzer, INITIAL_DELAY, ANALISYS_PERIOD);

    }

    private ReflNode getNodeByIP(String ipx) {
        for (Iterator nit = snodes.values().iterator(); nit.hasNext();) {
            ReflNode n = (ReflNode) nit.next();
            if (n.ipad.equals(ipx)) {
                return n;
            }
        }
        return null;
    }

    @Override
    synchronized public boolean AddMonitorUnit(ServiceItem si) {
        synchronized (sthreads) {
            if (!sthreads.containsKey(si.serviceID) && !snodes.containsKey(si.serviceID)) {
                ServiceThread at = new ServiceThread(si);
                sthreads.put(si.serviceID, at);
                //logger.log(Level.INFO, "SThread created for sid="+si.serviceID);
                at.start();
                return true;
            }
        }
        return false;
    }

    class ServiceThread extends Thread {
        ServiceItem si;

        public ServiceThread(ServiceItem si) {
            this.si = si;
        }

        @Override
        public void run() {
            try {
                inializeMonitorUnit(si);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "While initializingMonitorUnit, got", t);
            }
            synchronized (sthreads) {
                sthreads.remove(si.serviceID);
                sthreads.notify();
            }
        }
    }

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

            //            System.out.println("MonalisaEntry: "+mle);
            //            System.out.println("SiteInfoEntry: "+sie);
            //            System.out.println("ExtendedSiteInfoEntry: "+esie);
            //            System.out.println("MLControlEntry: "+mlce);
            //            System.out.println("GenericMLEntry: "+gmle);

            int MLCP = -1; // control port
            String ipad = null; // IP address
            String host = null; // hostname
            String un = null; // unit name
            int mlPort = 9000; //  port ?!
            int registryPort = Registry.REGISTRY_PORT; // 1099 ?!

            if (mlce != null) {
                MLCP = mlce.ControlPort.intValue();
            }
            if (sie != null) {
                ipad = sie.IPAddress;
                un = sie.UnitName;
                mlPort = (sie.ML_PORT == null) ? 9000 : sie.ML_PORT.intValue();
                registryPort = (sie.REGISTRY_PORT == null) ? Registry.REGISTRY_PORT : sie.REGISTRY_PORT.intValue();
            } else {
                logger.log(Level.WARNING, "SiteInfoEntry == null for " + si + " - discarding it");
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
                host = IpAddrCache.getHostName(ipad, true);
            } else {
                IpAddrCache.putIPandHostInCache(ipad, host);
            }
            //          if(host.indexOf("vpac") >=0 || un.indexOf("wn1") >=0)
            //              System.out.println("un="+un+" host="+host+" ip="+ipad);
            MLSerClient tcl = new tClient(un, ipad, host, mlPort, registryPort, MLCP, mle, sie, esie, proxytmClient,
                    si.serviceID);
            tcl.addFarmClient(si.serviceID);
            tcl.setServiceEntry(si);
            addNode(si, ds, tcl, un, ipad);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error while adding node", t);
            removeNode(si.serviceID);
        }
    }

    /** 
     * Wait for service threads to finish. @see lia.Monitor.JiniClient.actualizeFarms() and 
     * AddProxyService for details about this. 
     */
    @Override
    public void waitServiceThreads(String message) {
        synchronized (sthreads) {
            while (sthreads.size() > 0) {
                logger.log(Level.INFO, "Waiting for last [" + sthreads.size() + "] sericeThreads to finish " + message);
                try {
                    sthreads.wait();
                } catch (InterruptedException e) {
                    logger.log(Level.WARNING, "Interrupted while waiting.", e);
                }
            }
        }
    }

    synchronized public void addNode(ServiceItem si, DataStore dataStore, MLSerClient client, String unitName,
            String ipad) {
        ReflNode n = (ReflNode) snodes.get(client.tClientID);
        if (n == null) {
            logger.log(Level.INFO, "Added reflector: " + unitName + " [" + ipad + "]");
            n = new ReflNode(unitName, si.serviceID, ipad);
            snodes.put(n.sid, n);
            IpAddrCache.getInetAddress(ipad);
        }
        n.client = client;

        //what's the interest for this node!?!
        for (int i = 0; i < local_filter_pred.size(); i++) {
            monPredicate pre = (monPredicate) local_filter_pred.elementAt(i);
            client.addLocalClient(this, pre);
        }
        vgAnalyzer.addNode(n);
        ReflRouterJiniService.getInstance().setSeenReflectors(snodes.size());
    }

    @Override
    public void removeNode(ServiceID id) {
        try {
            ReflNode n = (ReflNode) snodes.remove(id);
            if (n != null) {
                vgAnalyzer.removeNode(n);
                n.client.deleteLocalClient(null);
                if (proxytmClient != null) {
                    proxytmClient.removeFarmClient(id);
                }
                router.removeDeadTunnels(n.removeAllTunnels());
                logger.log(Level.INFO, "Removed Reflector " + n.UnitName + " sid=" + id);
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error removing node " + id, ex);
        }
        ReflRouterJiniService.getInstance().setSeenReflectors(snodes.size());
    }

    @Override
    public void newFarmResult(MLSerClient client, Object ro) {
        if (ro == null) {
            return; // Note that NULL will be returned for predicates with no results
        }
        if (ro instanceof Result) {
            Result r = (Result) ro;
            processResult(client, r);
        } else if (ro instanceof eResult) {
            eResult r = (eResult) ro;
            processResult(client, r);
        } else if (ro instanceof Vector) {
            Vector vr = (Vector) ro;
            for (int i = 0; i < vr.size(); i++) {
                newFarmResult(client, vr.elementAt(i));
            }
        } else {
            logger.log(Level.WARNING, "Wrong Result type:\n" + ro);
        }
    }

    public void processResult(MLSerClient cli, eResult er) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Got eResult:\n" + er);
        }
        if (er.ClusterName.equals("Peers")) {
            // check if a node has to be removed
            ReflNode srcNode = (ReflNode) snodes.get(cli.tClientID);
            ReflNode dstNode = null;
            if (er.NodeName != null) {
                dstNode = getNodeByIP(IpAddrCache.getIPaddr(er.NodeName, true));
            }
            srcNode.processPeerResult(dstNode, er);
        }
    }

    public void processResult(MLSerClient cli, Result r) {
        ReflNode srcNode = (ReflNode) snodes.get(cli.tClientID);
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Got result:\n" + r);
        }
        if (srcNode == null) {
            logger.warning("Result from " + r.FarmName + " but refl. not in known list");
            return;
        }
        if (r.ClusterName == null) {
            logger.warning("Invalid result, cluster is null:" + r);
            return;
        }
        if (r.ClusterName.equals("Reflector")) {
            srcNode.processReflectorResult(r);
            return;
        }
        if (r.NodeName == null) {
            logger.warning("Invalid result, node is null:" + r);
            return;
        }
        ReflNode dstNode = getNodeByIP(IpAddrCache.getIPaddr(r.NodeName, true));
        if (dstNode == null) {
            Level logLevel = "Peers".equals(r.ClusterName) ? Level.WARNING : Level.FINE;
            if (logger.isLoggable(logLevel)) {
                logger.log(logLevel, "Result from " + r.FarmName + " but peer refl. not-existing:\n" + r);
            }
            return;
        }
        if (r.ClusterName.equals("Internet")) {
            srcNode.processInetResult(dstNode, r);
            return;
        }
        if (r.ClusterName.equals("Peers")) {
            srcNode.processPeerResult(dstNode, r);
            if (!dstNode.checkReflActive()) {
                logger.warning("Received Peer result to INACTIVE Reflector:" + r);
                vgAnalyzer.addOrphanPeer(srcNode, r);
            } else {
                vgAnalyzer.removeOrhpanPeer(srcNode, r);
            }
            return;
        }
        logger.warning("Received unhandled result:" + r);
    }

    @Override
    public boolean knownConfiguration(ServiceID farmID) {
        return true;
    }

    @Override
    public boolean verifyProxyConnection() {
        if ((proxytmClient == null) || (proxytmClient.verifyProxyConnection() == false)) {
            return false;
        }
        return true;
    }

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
                InetAddress inetAddress = InetAddress.getByName(ipAddress);
                logger.log(Level.INFO, "CONNECT PROXY at " + inetAddress.getHostName() + ":" + portNumber);

                if (knownConfigurations == null) {
                    knownConfigurations = new Hashtable();
                }
                proxytmClient = new tmClient(inetAddress, portNumber, knownConfigurations, this);
                proxytmClient.startCommunication();
                ReflRouter.resetStartTime();
            }
        }
    }

    @Override
    public synchronized void closeProxyConnection() {
        if (proxytmClient != null) {
            proxytmClient.closeProxyConnection();
            proxytmClient = null;
        }
    }

    public static void main(String[] args) {
        new Main();
    }

    /* (non-Javadoc)
     * @see lia.Monitor.JiniClient.CommonJini.JiniClient#portMapChanged(net.jini.core.lookup.ServiceID, java.util.ArrayList)
     */
    @Override
    public void portMapChanged(ServiceID id, ArrayList portMap) {
        // TODO Auto-generated method stub

    }
}
