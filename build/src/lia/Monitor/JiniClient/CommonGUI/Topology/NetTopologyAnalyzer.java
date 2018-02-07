package lia.Monitor.JiniClient.CommonGUI.Topology;

import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.IpAddrCache;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.MLSerClient;
import net.jini.core.lookup.ServiceID;

public class NetTopologyAnalyzer implements LocalDataFarmClient {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(NetTopologyAnalyzer.class.getName());

    private static final String confIgnoreFarms = AppConfig.getProperty("lia.Monitor.Topology.ignoreFarms", "");

    final Map<ServiceID, rcNode> nodes; // nodes from SerMonitor (SID : rcNode)
    Vector<rcNode> knownNodes; // vector with nodes that are already known;
    // see registerAsListenerForNewNodes for details
    SerMonitorBase monitor = null; // the SerMonitor 
    DecimalFormat formatter;

    public Vector routers; // "router-like" rcNodes
    // rcNode.haux contains:
    // usageCount - Integar - how many times this node is used
    // net - String - network name
    // as - String - AS number
    // descr - String - a description about this node
    // city - String - 
    // state - String -
    // country - String -
    public Vector nets; // "net-like" rcNodes :
    // rcNode.IPAddress = ''
    // rcNode.UnitName = net name
    // rcNode.haux contains:
    // usageCount - Integar - how many times this node is used
    // net - String - network name
    // as - String - AS number
    // descr - String - a description about this node
    // city - String - 
    // state - String -
    // country - String -
    public Vector ases; // "as-like" rcNodes : 
    // rcNode.IPAddress = ''
    // rcNode.UnitName = AS Number
    // rcNode.haux contains:
    // usageCount - Integar - how many times this node is used
    // net - String - network name
    // as - String - AS number
    // descr - String - a description about this node
    // city - String - 
    // state - String -
    // country - String -
    public Vector farms; // "farm-like" rcNodes : 
    // copies of the rcNodes from nodes Hashtable. This copy is made to 
    // allow having different position and attributes
    // rcNode.IPAddress = ''
    // rcNode.UnitName = AS Number
    // rcNode.haux contains:
    // usageCount - Integar - how many times this node is used
    // net - String - network name
    // as - String - AS number
    // descr - String - a description about this node
    // city - String - 
    // state - String -
    // country - String -

    public Hashtable links;
    // (key: rcNode - source entity (a rcNode found in routers, nets, ases or farms)
    //  val: Hashtable:
    //       (key: rcNode - dest entity (a rcNode in previous mentioned vectors)
    //		  val: EntityLink - containing information about this link
    //		 )
    // )

    public Hashtable traces;
    // (key: source farm IP address
    //  val: Hashtable :
    //		(key: dest farm IP address
    //		 val: Hashtable
    //			(key: "router-trace"
    //			 val: Vector - elems: EntityLinks, from links Hash of Hashes)
    //			(key: "net-trace"
    //			 val: Vector - elems: EntityLinks, from links Hash of Hashes)
    //			(key: "as-trace"
    //			 val: Vector - elems: EntityLinks, from links Hash of Hashes)
    //		)
    //	)

    //	private Hashtable replacedLinks;	//list of pairs oldLink -> newLink (see replaceNodeWith)
    public AliasCache aliasCache;
    private final TraceFilter traceFilter;
    private final Vector ignoredFarmsIP; // list with IPs of the farms to be ignored - extracted from confIgnoreFarms

    public NetTopologyAnalyzer(Map<ServiceID, rcNode> nodes, SerMonitorBase monitor) {
        this.nodes = nodes;
        this.monitor = monitor;
        traces = new Hashtable();
        links = new Hashtable();
        routers = new Vector();
        nets = new Vector();
        ases = new Vector();
        farms = new Vector();
        knownNodes = new Vector();
        formatter = new DecimalFormat("###,###.#");
        //		replacedLinks = new Hashtable();
        aliasCache = new AliasCache(this);
        aliasCache.setRoutersAndFarms(routers, farms);
        traceFilter = new TraceFilter(this);
        traceFilter.start();
        ignoredFarmsIP = new Vector();

        // initialize ignoredFarmsIP
        try {
            if (confIgnoreFarms != null) {
                StringTokenizer ifStk = new StringTokenizer(confIgnoreFarms, ",");
                while (ifStk.hasMoreTokens()) {
                    ignoredFarmsIP.add(ifStk.nextToken());
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error initializing ignored farms", t);
        }
        // supervise nodes. Register as a Tracepath listener for new farm nodes
        TimerTask ttask = new TimerTask() {
            @Override
            public void run() {
                try {
                    registerAsListenerForNewNodes();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        };
        BackgroundWorker.schedule(ttask, 4 * 1000, 7 * 1000);
    }

    /** Register Tracepath listener for new nodes */
    void registerAsListenerForNewNodes() {
        if ((monitor == null) || (monitor.main == null) || !monitor.main.topologyShown) {
            return;
        }
        // first check for new nodes
        for (final rcNode n : nodes.values()) {
            if (!knownNodes.contains(n)) {
                // n is a new node!
                knownNodes.add(n);
                if (ignoredFarmsIP.contains(n.IPaddress)) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "NOT registering for " + n.UnitName + " - farm ignored");
                    }
                    continue;
                }
                logger.log(Level.FINE, "Registering for " + n.UnitName);
                //n.client.addLocalClient(this, "TracepathFilter");
                monPredicate pre = new monPredicate("*", "Tracepath", "*", -1 * 60 * 1000, -1, null, null);
                n.client.addLocalClient(this, pre);
            }
        }
        // check for deleted nodes
        for (Iterator<rcNode> nit = knownNodes.iterator(); nit.hasNext();) {
            final rcNode n = nit.next();
            if (!nodes.containsValue(n)) {
                // n was deleted; also remove it from knownNodes
                logger.log(Level.FINE, "Removing " + n.UnitName);
                nit.remove();
            }
        }
    }

    /** 
     * remove traces starting from farm with this IP 
     */
    synchronized public void removeFarmTraces(String ipAddress) {
        rcNode deletedNode = findRCnodeByIP(farms, ipAddress);
        if (deletedNode == null) {
            return;
        }
        for (int i = 0; i < farms.size(); i++) {
            rcNode from = (rcNode) farms.get(i);
            if (deletedNode == from) {
                continue;
            }
            removeTraces(from.IPaddress, deletedNode.IPaddress);
            removeTraces(deletedNode.IPaddress, from.IPaddress);
        }
        farms.remove(deletedNode);
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Removed ALL traces FROM/TO " + ipAddress);
        }
    }

    /**
     * remove all traces between these two ip-s 
     */
    private void removeTraces(String ipns, String ipnw) {
        Hashtable peerFarms = (Hashtable) traces.get(ipns);
        if (peerFarms == null) {
            return;
        }
        Hashtable traceValues = (Hashtable) peerFarms.get(ipnw);
        if (traceValues == null) {
            return;
        }
        for (Enumeration enTr = traceValues.elements(); enTr.hasMoreElements();) {
            Vector entLinksTrace = (Vector) enTr.nextElement();
            removeTrace(entLinksTrace);
        }
        traceValues.clear();
        peerFarms.remove(ipnw);
        if (peerFarms.size() == 0) {
            traces.remove(ipns);
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Removed all traceValues FROM " + ipns + " to " + ipnw);
        }
    }

    /**
     * remove all linkEntities on this trace
     */
    private void removeTrace(Vector linksTrace) {
        if (linksTrace == null) {
            return;
        }
        for (int i = 0; i < linksTrace.size(); i++) {
            EntityLink link = (EntityLink) linksTrace.get(i);
            removeLink(link);
        }
        linksTrace.clear();
    }

    /**
     * remove link and if the case, also the nodes
     * and the links between them.
     * @param link the link to be removed
     */
    private void removeLink(EntityLink link) {
        removeNode(link.n1);
        removeNode(link.n2);
        link.usageCount--;
        // if link isn't used anymore, physically remove it
        if (link.usageCount <= 0) {
            Hashtable peers = (Hashtable) links.get(link.n1);
            if (peers != null) {
                peers.remove(link.n2);
                if (peers.size() == 0) {
                    links.remove(link.n1);
                }
            }
        }
    }

    /**
     * remove the given node (from farms, ases, nets, routers) if its
     * usage count is = 1; otherwize, just decrease its usage count;
     * returns true if node removed and false if just decreased its usage count 
     */
    private boolean removeNode(rcNode n) {
        int uc = ((Integer) n.haux.get("usageCount")).intValue();
        if (uc <= 1) {
            boolean really = false;
            if (routers.contains(n)) {
                really |= routers.remove(n);
            } else if (farms.contains(n)) {
                really |= farms.remove(n);
            } else if (ases.contains(n)) {
                really |= ases.remove(n);
            } else if (nets.contains(n)) {
                really |= nets.remove(n);
            }
            if (really) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "Removed " + n.UnitName + " @ " + n.IPaddress);
                }
            }
            return true;
        }
        n.haux.put("usageCount", Integer.valueOf(uc - 1));
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Decreasing USAGE COUNT for " + n.UnitName + " @ " + n.IPaddress + " to "
                    + (uc - 1));
        }
        return false;
    }

    /**
     * increase the usage count for a rcNode
     */
    private void useNode(rcNode n) {
        int uc = ((Integer) n.haux.get("usageCount")).intValue();
        n.haux.put("usageCount", Integer.valueOf(1 + uc));
    }

    /**
     * check if there is a link between n1 and n2, and
     * - if exists, return it
     * - if doesn't, and create == true, create and return it
     * - else return null 
     */
    private EntityLink getELink(rcNode n1, rcNode n2, boolean create) {
        Hashtable peers = (Hashtable) links.get(n1);
        if (peers == null) {
            if (create) {
                peers = new Hashtable();
                links.put(n1, peers);
            } else {
                return null;
            }
        }
        EntityLink link = (EntityLink) peers.get(n2);
        if (link == null) {
            if (create) {
                link = new EntityLink(n1, n2);
                peers.put(n2, link);
            } else {
                return null;
            }
        }
        if (create) {
            useNode(n1);
            useNode(n2);
            link.usageCount++;
        }
        return link;
    }

    /**
     *  get the traces hashtable between two IPs, building the hashtable structure if necessary 
     */
    private Hashtable getTraceValues(String sourceIP, String destIP) {
        Hashtable peerFarms = (Hashtable) traces.get(sourceIP);
        if (peerFarms == null) {
            peerFarms = new Hashtable();
            traces.put(sourceIP, peerFarms);
        }
        Hashtable traceValues = (Hashtable) peerFarms.get(destIP);
        if (traceValues == null) {
            traceValues = new Hashtable();
            peerFarms.put(destIP, traceValues);
        }
        return traceValues;
    }

    /**
     * check if first and last IPs in hashTrace are equal to sourceIP and destIP.
     * This is used to avoid adding traces that end with "no reply" instead of a certain host  
     * @param hashTrace - a vector containing hostInfo hashtables
     * @param sourceIP - source ip
     * @param destIP - destination ip
     * @return true / false
     */
    private boolean isCompleteTrace(Vector<Map<String, String>> hashTrace, String sourceIP, String destIP) {
        if (hashTrace.size() == 0) {
            return false;
        }
        Map<String, String> src = hashTrace.get(0);
        Map<String, String> dest = hashTrace.get(hashTrace.size() - 1);
        String srcIP = src.get("ip");
        String dstIP = dest.get("ip");
        boolean srcExists = monitor.getTracepathNodeByIP(srcIP) != null;
        boolean dstExists = monitor.getTracepathNodeByIP(dstIP) != null;
        return srcExists && dstExists && sourceIP.equals(srcIP) && destIP.equals(dstIP);
    }

    /** process a result coming from a farm */
    synchronized public void processResult(MLSerClient client, eResult er) {
        if (!er.ClusterName.equals("Tracepath")) {
            return;
        }
        //		if(! (er.FarmName.equals("test-caltech") || 
        //				er.FarmName.equals("test-wn1-ro")
        //				|| er.FarmName.equals("test-starlight")))
        //			return;
        try {
            //			long t1 = NTPDate.currentTimeMillis();
            // identify source and dest IPs for this trace
            rcNode ns1 = nodes.get(client.tClientID);
            if (ns1 == null) {
                return;
            }
            String ipns = ns1.IPaddress;
            String ipnw = IpAddrCache.getIPaddr(er.NodeName, true);
            if (ipnw == null) {
                return; // ipnw not resolved yet. Skip till nextTime
            }
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Processing Result FROM " + ipns + " TO " + ipnw + " << " + er.Module
                        + " param.len=" + er.param.length);
            }
            if (er.param.length == 0) {
                // remove trace between these two farms
                //removeTraces(ipns, ipnw);
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Ignoring trace-expire for " + ns1.UnitName + " to " + er.NodeName);
                }
                return;
            }
            // It seems we've got a valid result...
            Vector<Map<String, String>> hashTrace = (Vector<Map<String, String>>) er.param[0];
            if (hashTrace == null) {
                return;
            }
            // fix for unknown source nodes
            if (hashTrace.size() > 0) {
                Map<String, String> srcNode = hashTrace.get(0);
                srcNode.put("ip", ipns);
            }
            // fix traces ending with "?" nodes
            while (hashTrace.size() > 0) {
                Map<String, String> lastNode = hashTrace.get(hashTrace.size() - 1);
                if ("?".equals(lastNode.get("ip"))) {
                    hashTrace.remove(lastNode);
                } else {
                    break;
                }
            }
            if (ignoredFarmsIP.contains(ipns) || ignoredFarmsIP.contains(ipnw)) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Ignoring trace because of IGNORED FARMS");
                }
                return;
            }
            Hashtable traceValues = getTraceValues(ipns, ipnw);
            if (!isCompleteTrace(hashTrace, ipns, ipnw)) {
                //if(ns1.UnitName.equals("ARDA"))
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Trace INCOMPLETE! Dropping... " + ns1.UnitName + " TO " + er.NodeName);//+" trace:\n"+hashTrace);
                }
                traceValues.clear();
                return;
            }
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Updating trace FROM " + ipns + " TO " + ipnw);
            }
            // create / update routerTrace
            Vector oldRouterTrace = (Vector) traceValues.get("router-trace");
            Vector newRouterTrace = updateRouterTrace(oldRouterTrace, hashTrace);
            removeDuplicates(newRouterTrace);

            // check for new IPs in this trace and try to find aliases
            aliasCache.checkTrace(newRouterTrace);

            if (oldRouterTrace != newRouterTrace) {
                removeTrace(oldRouterTrace);
                traceValues.put("router-trace", newRouterTrace);
            }
            // create / update netTrace
            Vector oldNetTrace = (Vector) traceValues.get("net-trace");
            Vector newNetTrace = updateEntityTrace(oldNetTrace, hashTrace, "net", nets);
            if (oldNetTrace != newNetTrace) {
                removeTrace(oldNetTrace);
                traceValues.put("net-trace", newNetTrace);
            }
            // create / update ASTrace 
            Vector oldASTrace = (Vector) traceValues.get("as-trace");
            Vector newASTrace = updateEntityTrace(oldASTrace, hashTrace, "as", ases);
            if (oldASTrace != newASTrace) {
                removeTrace(oldASTrace);
                traceValues.put("as-trace", newASTrace);
            }
            //				logger.log(Level.INFO, "Updating trace FROM "+ipns+" TO "+ipnw);
            //			long t2 = NTPDate.currentTimeMillis();
            //			System.out.println("topoAnaTime: "+(t2-t1)+" ms");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private rcNode createRCNode(Map<String, String> hostInfo, String name) {
        //		System.out.println("Creating node "+name+" for:\n"+hostInfo);
        rcNode n = new rcNode();
        n.x = (int) (800 * Math.random());
        n.y = (int) (500 * Math.random());
        n.IPaddress = hostInfo.get("ip");
        ;
        n.UnitName = name;
        n.haux.put("usageCount", Integer.valueOf(0));
        // optional information
        n.LAT = hostInfo.get("lat");
        n.LONG = hostInfo.get("long");
        String net = hostInfo.get("net");
        if (net != null) {
            n.haux.put("net", net);
        }
        String as = hostInfo.get("as");
        if (as != null) {
            n.haux.put("as", as);
        }
        String descr = hostInfo.get("descr");
        if (descr != null) {
            n.haux.put("descr", descr);
        }
        String city = hostInfo.get("city");
        if (city != null) {
            n.haux.put("city", city);
        }
        String state = hostInfo.get("state");
        if (state != null) {
            n.haux.put("state", state);
        }
        String country = hostInfo.get("country");
        if (country != null) {
            n.haux.put("country", country);
        }
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Creating node " + n.UnitName + " @ " + n.IPaddress);
        }
        return n;
    }

    /**
     * search in "where" for a node with "UnitName" and return it 
     */
    public static rcNode findRCnodeByName(Vector where, String name) {
        if ((name == null) || name.equals("?")) {
            return null;
        }
        for (int i = 0; i < where.size(); i++) {
            rcNode n = (rcNode) where.get(i);
            if (n.UnitName.equals(name)) {
                return n;
            }
        }
        return null;
    }

    /**
     * search in "where" for a node with "ipAddress" and return it 
     */
    public static rcNode findRCnodeByIP(Vector where, String ipAddress) {
        if (ipAddress.equals("?")) {
            return null;
        }
        try {
            for (int i = 0; i < where.size(); i++) {
                rcNode n = (rcNode) where.get(i);
                if (n.IPaddress.equals(ipAddress)) {
                    return n;
                }
            }
        } catch (Exception ex) {
            //ignore exception, go on...
        }
        return null;
    }

    /**
     * replace all fractions of trace that contain a "?" with other sections with "?" to avoid
     * having multiple parallel "?"s if not necessary
     * @param trace
     */
    private void removeDuplicates(Vector trace) {
        boolean inRegion = false;
        int start = -1, end = -1;
        for (int i = 0; i < trace.size(); i++) {
            EntityLink link = (EntityLink) trace.get(i);
            if (link.n2.IPaddress.equals("?") && !inRegion) {
                inRegion = true;
                start = i;
            }
            if (!link.n2.IPaddress.equals("?") && inRegion) {
                inRegion = false;
                end = i;
                globalSearchAndReplaceRegion(trace, start, end);
                start = end = -1;
            }
        }
    }

    /**
     * for each possible trace, test if a fraction of matches with trace[start...end]
     * and do the necessary replacements
     * @param trace
     * @param start
     * @param end
     */
    private void globalSearchAndReplaceRegion(Vector trace, int start, int end) {
        for (Enumeration enNodes = traces.elements(); enNodes.hasMoreElements();) {
            // get the peers for a farm (don't care which one)
            Hashtable peers = (Hashtable) enNodes.nextElement();
            for (Enumeration enPeers = peers.elements(); enPeers.hasMoreElements();) {
                // for each peer, get the traceValues
                Hashtable traceValues = (Hashtable) enPeers.nextElement();
                // we are interested only in router-traces...
                Vector someTrace = (Vector) traceValues.get("router-trace");
                if ((someTrace != null) && (someTrace != trace)) {
                    if (searchAndReplaceRegion(someTrace, trace, start, end)) {
                        return; // successfull replace; don't search anymore
                    }
                }
            }
        }
    }

    /**
     * search and replace trace[start...end] with EntityLinks from someTrace if they match
     * @param someTrace - some other trace
     * @param trace - current trace
     * @param start - positions in trace
     * @param end - positions in trace
     * @return - true/false wether the replace was made or not
     */
    private boolean searchAndReplaceRegion(Vector someTrace, Vector trace, int start, int end) {
        boolean changed = false;
        int len = (end - start) + 1;
        int matched = 0;
        for (int i = 0; i < someTrace.size(); i++) {
            EntityLink someLink = (EntityLink) someTrace.get(i);
            EntityLink link = (EntityLink) trace.get(start + matched);
            if ((someLink != link) && someLink.n1.IPaddress.equals(link.n1.IPaddress)
                    && someLink.n2.IPaddress.equals(link.n2.IPaddress)) {
                matched++;
            } else {
                i -= matched;
                matched = 0;
            }
            if (matched == len) {
                //				System.out.println("Matched!");
                int srcBase = (i - len) + 1;
                for (int j = 0; j < len; j++) {
                    link = (EntityLink) trace.get(start + j);
                    removeLink(link);
                    link = (EntityLink) someTrace.get(srcBase + j);
                    useNode(link.n1);
                    useNode(link.n2);
                    link.usageCount++;
                    trace.set(start + j, link);
                }
                changed = true;
                //				rcNode ns1 = ((EntityLink)someTrace.get(0)).n1;
                //				rcNode nf1 = ((EntityLink)someTrace.get(someTrace.size()-1)).n2;
                //				rcNode ns2 = ((EntityLink)trace.get(0)).n1;
                //				rcNode nf2 = ((EntityLink)trace.get(trace.size()-1)).n2;
                //				System.out.println("Replaced region. SRC="+ns1.UnitName+"->"+nf1.UnitName
                //						+" DST="+ns2.UnitName+"->"+nf2.UnitName);
                break;
            }
        }
        return changed;
    }

    /**
     * updates a router trace (or creates it from scratch if old one is null)
     * @param oldTrace - vector of Entity Links
     * @param hashTrace - vector of hastables with infos for each host in the trace
     * @return - the updated router trace
     */
    private Vector<EntityLink> updateRouterTrace(Vector<EntityLink> oldTrace, Vector<Map<String, String>> hashTrace) {
        Vector<EntityLink> rez = (oldTrace != null ? oldTrace : new Vector<EntityLink>());
        rcNode crtNode = null;
        rcNode lastNode = null;
        rcNode farmNode = null;
        rcNode monitorNode = null;
        for (int i = 0; i < hashTrace.size(); i++) {
            Map<String, String> hostInfo = hashTrace.get(i);
            String ip = hostInfo.get("ip");

            monitorNode = monitor.getTracepathNodeByIP(ip);
            farmNode = findRCnodeByIP(farms, ip);

            // check (and build if needed) this node as a new farm node (farms vector)
            if ((monitorNode != null) && (farmNode == null)) {
                farmNode = createRCNode(hostInfo, monitorNode.UnitName);
                farmNode.setShortName();
                farmNode.LAT = monitorNode.LAT;
                farmNode.LONG = monitorNode.LONG;
                farms.add(farmNode);
            }
            // check (and build if needed) this node as a new router node (routers vector)
            crtNode = (farmNode != null ? farmNode : findRCnodeByIP(routers, ip));

            boolean ghostRouterAdded = false;
            if (crtNode == null) {
                crtNode = createRCNode(hostInfo, hostInfo.get("host"));
                if (crtNode.IPaddress.equals("?")) {
                    ghostRouterAdded = true;
                }
                routers.add(crtNode); // what if it's a "?" router
            }
            if (lastNode == null) {
                lastNode = crtNode; // this is the first node
                continue;
            }
            // from now we should have valid lastNode and crtNode-s
            EntityLink link = (i - 1) < rez.size() ? (EntityLink) rez.get(i - 1) : null;
            // link should be initialized with previous link on this trace (if exists)
            // check if that value is OK; if not, recreate the link
            if ((link != null)
                    && (!link.n1.IPaddress.equals(lastNode.IPaddress) || !link.n2.IPaddress.equals(crtNode.IPaddress))) {
                removeLink(link);
                link = null;
            }
            // if link is <> from rez(i-1), create it and also put it in rez
            if (link == null) {
                link = getELink(lastNode, crtNode, true);
                if (rez.size() == (i - 1)) {
                    rez.add(link);
                } else {
                    rez.set(i - 1, link);
                }
            } else {
                if (ghostRouterAdded) {
                    routers.remove(crtNode);
                    crtNode = link.n2;
                }
            }
            link.delay = (.8 * link.delay) + (.2 * Double.parseDouble(hostInfo.get("delay")));
            link.fields |= EntityLink.FIELD_DELAY;
            lastNode = crtNode;
        }

        // if the new trace is shorter than oldTrace, remove last links  
        while (rez.size() > (hashTrace.size() - 1)) {
            int i = rez.size() - 1;
            EntityLink link = rez.get(i);
            removeLink(link);
            rez.remove(i);
        }
        return rez;
    }

    /**
     * update or create a trace for rcNodes found in whatVect
     * The trace will be a vector of EntityLinks, stored in links hash of hashes
     * @param oldTrace - old trace of this kind, vector of EntityLinks 
     * @param hashTrace - vector of hostInfo hashtables
     * @param what - one of "net" or "as"
     * @param whatVect - one of nets or ases Vectors
     * @return updated oldTrace or a new Vector if oldTrace was null
     */
    private Vector<EntityLink> updateEntityTrace(Vector<EntityLink> oldTrace, Vector<Map<String, String>> hashTrace,
            String what, Vector whatVect) {
        Vector<EntityLink> rez = oldTrace != null ? oldTrace : new Vector<EntityLink>();
        rcNode crtNode = null;
        rcNode lastNode = null;
        rcNode farmNode = null;
        //		rcNode monitorNode = null;
        double totalDelay = 0;
        int rezIdx = 0;
        for (int i = 0; i < hashTrace.size(); i++) {
            Map<String, String> hostInfo = hashTrace.get(i);
            String ip = hostInfo.get("ip");
            String name = hostInfo.get(what);
            if ((name == null) || ip.equals("?")) {
                continue;
            }

            // check if this is a farm (it should have been created in updateRouterTrace)
            farmNode = findRCnodeByIP(farms, ip);

            // check (and build if needed) this node as a new router node (routers vector)
            crtNode = (farmNode != null ? farmNode : findRCnodeByName(whatVect, name));

            if (crtNode == null) {
                crtNode = createRCNode(hostInfo, name);
                whatVect.add(crtNode);
            }
            if (lastNode == null) {
                lastNode = crtNode; // this is the first node
                continue;
            }
            double delay = Double.parseDouble(hostInfo.get("delay"));
            if (lastNode == crtNode) {
                totalDelay += delay;
                continue;
            }
            // from now we should have two valid and different nodes for last and crt Node-s
            EntityLink link = rezIdx < rez.size() ? (EntityLink) rez.get(rezIdx) : null;
            if ((link != null) && ((link.n1 != lastNode) || (link.n2 != crtNode))) {
                removeLink(link);
                link = null;
            }
            if (link == null) {
                link = getELink(lastNode, crtNode, true);
                if (rez.size() == rezIdx) {
                    rez.add(link);
                } else {
                    rez.set(rezIdx, link);
                }
            }
            link.delay = (.8 * link.delay) + (.2 * totalDelay);
            link.fields |= EntityLink.FIELD_DELAY;
            rezIdx++;
            totalDelay = 0;
            lastNode = crtNode;
        }
        while (rez.size() > rezIdx) {
            EntityLink link = rez.get(rezIdx);
            removeLink(link);
            rez.remove(rezIdx);
        }
        return rez;
    }

    @Override
    public void newFarmResult(MLSerClient client, Object res) {
        if (res instanceof eResult) {
            //processResult(client, (eResult) res);
            traceFilter.addOldTraceResult(client, (eResult) res);
        } else if (res instanceof Vector) {
            Vector v = (Vector) res;
            for (int i = 0; i < v.size(); i++) {
                newFarmResult(client, v.get(i));
            }
        } else {
            if (res instanceof Result) {
                Result r = (Result) res;
                if (r.ClusterName.equals("Tracepath")) {
                    //System.out.println("RECEIVED new result\n"+r);
                    traceFilter.addNewTraceResult(client, r);
                } else {
                    logger.log(Level.FINE, "Ignored Result is of Result type and is in cluster "
                            + ((Result) res).ClusterName);
                }
            } else {
                if (res != null) {
                    logger.log(Level.INFO, "Ignoring Result from " + client.farm.name + " = " + res);
                }
            }
        }
    }
}
