package lia.Monitor.JiniClient.ReflRouter;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;
import lia.util.mail.DirectMailSender;
import lia.util.ntp.NTPDate;

/**
 * This class will analyze the following aspects on the graph generated 
 * by the peers connections between VRVS reflectors:
 * - the groups of sepparated reflectors
 * - the reflector pairs with only one peer link
 * - the cycles encountered in the peer connections.
 */
public class VRVSGraphAnalyzer extends TimerTask {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(VRVSGraphAnalyzer.class.getName());

    private final Hashtable snodes; // key=SID; value=ReflNode

    private double POOR_QUALITY = 60; // if PeerQuality < 60%, consider the peer link as poor
    private long STATUS_REPORT_INTERVAL = 6 * 60 * 60 * 1000; // 6 hours for now 
    private long STATE_HISTORY_PERIOD = 60 * 60 * 1000; // keep status about a node for this time (if not updated)
    // nodes states
    private static final String STATE_IN_CYCLE = "In Cycle";
    private static final String STATE_ASYM_PEER = "Has asym peer";
    private static final String STATE_ORPHAN_PEER = "Has Orphan Peer";
    private static final String STATE_UNSTABLE_NODE = "Unstable node";
    private static final String STATE_POOR_QUAL_PEER = "Has poor Quality Peer";

    // alert levels
    private static final String ALERT_PANIC = "PANIC!";
    private static final String ALERT_WARNING = "Warning";
    private static final String ALERT_INFO = "Information";
    private static final String ALERT_STATUS = "Status report";
    private static final String ALERT_NONE = "None";

    private static final String[] alertLevel = new String[] { ALERT_NONE, ALERT_STATUS, ALERT_INFO, ALERT_WARNING,
            ALERT_PANIC };

    private static final Hashtable state2Alert = new Hashtable();
    // now we will define the mapping between states and alerts
    static {
        state2Alert.put(STATE_IN_CYCLE, ALERT_PANIC);
        state2Alert.put(STATE_ASYM_PEER, ALERT_WARNING);
        state2Alert.put(STATE_ORPHAN_PEER, ALERT_WARNING);
        state2Alert.put(STATE_UNSTABLE_NODE, ALERT_WARNING);
        state2Alert.put(STATE_POOR_QUAL_PEER, ALERT_INFO);
    }

    private static final Hashtable alert2Email = new Hashtable();
    // now we define the mapping between alerts and email addresses
    //	static {
    //		alert2Email.put(ALERT_PANIC, new String [] {"catalin.cirstoiu@cern.ch", "ramiro.voicu@cern.ch"});
    //		alert2Email.put(ALERT_WARNING, new String [] {"catalin.cirstoiu@cern.ch", "ramiro.voicu@cern.ch"});
    //		alert2Email.put(ALERT_INFO, new String [] {"catalin.cirstoiu@cern.ch", "ramiro.voicu@cern.ch"});
    //		alert2Email.put(ALERT_STATUS, new String [] {"catalin.cirstoiu@cern.ch", "ramiro.voicu@cern.ch"});
    //	}

    private final Hashtable orphanPeers; // key="ReflNode.UnitName-r.NodeName"; value=Result
    private final Vector orphans; // contains Strings with the keys form orphanPeers
    private final Vector cycles; // contains vectors (cyles) with ReflNodes
    private final Vector islands; // contains vectors (islands) with ReflNodes; connex components
    private final Vector asymTunnels; // contains IPTunnels that have no corresponding IPTunnel
    private final Vector poorQualTunnels; // contains IPTunnels
    private final Hashtable onOffHistory; // key=UnitName; value=Vector containing times in last hour when connection with ML on that reflector was lost 
    private final Hashtable unstableNodes; // key=UnitName; value=Long = number of state changes last hour
    private final Hashtable stateHistory; // key = UnitName; value=Hash(key=State; value=last time)
    private final Hashtable lastState; // key = UnitName; value=Hash(key=State; value=time) ; cleared each iteration
    private final Hashtable alertCause; // key = UnitName; value=Hash(key=State; value=last time)

    private final Object syncData; // used to synchronize getting and computing the above vectors and hashes

    private long lastEmailTime; // time when last email was sent; used for status report;
    private String hostname;

    public VRVSGraphAnalyzer(Hashtable snodes) {
        this.snodes = snodes;
        syncData = new Object();
        orphanPeers = new Hashtable();
        orphans = new Vector();
        cycles = new Vector();
        islands = new Vector();
        asymTunnels = new Vector();
        poorQualTunnels = new Vector();
        onOffHistory = new Hashtable();
        unstableNodes = new Hashtable();
        stateHistory = new Hashtable();
        lastState = new Hashtable();
        alertCause = new Hashtable();
        try {
            hostname = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        initDefaults();
    }

    private String[] splitCommaString(String str) {
        StringTokenizer stk = new StringTokenizer(str, ",");
        String[] rez = new String[stk.countTokens()];
        int i = 0;
        while (stk.hasMoreTokens()) {
            rez[i++] = stk.nextToken();
        }
        return rez;
    }

    private void initDefaults() {
        alert2Email
                .put(ALERT_PANIC, splitCommaString(AppConfig.getProperty("PANIC_EMAIL", "catalin.cirstoiu@cern.ch")));
        alert2Email.put(ALERT_WARNING,
                splitCommaString(AppConfig.getProperty("WARNING_EMAIL", "catalin.cirstoiu@cern.ch")));
        alert2Email.put(ALERT_INFO, splitCommaString(AppConfig.getProperty("INFO_EMAIL", "catalin.cirstoiu@cern.ch")));
        alert2Email.put(ALERT_STATUS,
                splitCommaString(AppConfig.getProperty("STATUS_EMAIL", "catalin.cirstoiu@cern.ch")));

        POOR_QUALITY = Double.parseDouble(AppConfig.getProperty("POOR_QUALITY", "60")); // 60 %
        STATUS_REPORT_INTERVAL = Long.parseLong(AppConfig.getProperty("STATUS_REPORT_INTERVAL", "21600")) * 1000; // 6 hours
        STATE_HISTORY_PERIOD = Long.parseLong(AppConfig.getProperty("STATE_HISTORY_PERIOD", "3600")) * 1000; // 1 hour
    }

    /**
     * Called form Main; when the end of a link cannot be found, that means the link is orphaned  
     */
    public void addOrphanPeer(ReflNode n, Result r) {
        orphanPeers.put(n.UnitName + " -> " + r.NodeName, r);
    }

    /**
     * Called from Main; when both ends of a peer link are identified, remove any possible 
     * old orphaned information about this link  
     */
    public void removeOrhpanPeer(ReflNode n, Result r) {
        orphanPeers.remove(n.UnitName + " -> " + r.NodeName);
    }

    /**
     * Generate a vector with the orphaned peer links, i.e. there is
     * a peer link from A to B, but reflector B isn't known in the system.
     * For now, this vector contains strings.
     * Results are put into this.orphans vector. 
     */
    private void computeOrphanPeers() {
        orphans.clear();
        for (Enumeration eno = orphanPeers.keys(); eno.hasMoreElements();) {
            orphans.add(eno.nextElement());
        }
    }

    /** 
     * Generate a vector with asymmetrical IPTunnels, i.e. there is peer link
     * from A to B, but there isn't a peer link from B to A. These tunnels are put 
     * in asymmTunnels Vector. 
     * 
     * The method also generates a vector of IPTunnels that have poor quality, i.e.
     * less than a treshold. 
     */
    private void computeAsymPeers() {
        asymTunnels.clear();
        poorQualTunnels.clear();

        for (Enumeration ens = snodes.elements(); ens.hasMoreElements();) {
            ReflNode ns = (ReflNode) ens.nextElement();
            for (Enumeration enn = ns.tunnels.elements(); enn.hasMoreElements();) {
                IPTunnel tun = (IPTunnel) enn.nextElement();
                if (!tun.hasPeerQuality()) {
                    continue; // it's not a Peers link; probably an ABPing link
                }
                ReflNode nn = tun.to;
                // so we have a peer link from ns to nn; check for reverse peer link
                IPTunnel revTun = (IPTunnel) nn.tunnels.get(ns.UnitName);
                if ((revTun == null) || !revTun.hasPeerQuality()) {
                    // if there is not such a tunnel, add the initial tunnel to the
                    // list of asymmetrical peer links
                    asymTunnels.add(tun);
                }
                if (tun.hasPeerQuality() && (tun.getPeerQuality() < POOR_QUALITY)) {
                    poorQualTunnels.add(tun);
                }
            }
        }
    }

    //	private Vector findPathToRoot(ReflNode start, Hashtable parents){
    //		Vector rez = new Vector();
    //		rez.add(start);
    //		for(ReflNode next = (ReflNode) parents.get(start); next != start; next = (ReflNode) parents.get(start)){
    //			rez.add(next);
    //			start = next;
    //		}
    //		return rez;
    //	}
    //	
    //	private ReflNode findRoot(ReflNode start, Hashtable parents){
    //		for(ReflNode next = (ReflNode) parents.get(start); next != start; next = (ReflNode) parents.get(start)){
    //			start = next;
    //		}
    //		return start;
    //	}
    //	
    //	private void walk(ReflNode start, Hashtable parents, Vector cycles){
    //		ReflNode startParent = (ReflNode) parents.get(start);
    //		ReflNode startRoot = findRoot(start, parents);
    //		for(Enumeration ent = start.tunnels.elements(); ent.hasMoreElements(); ){
    //			IPTunnel tun = (IPTunnel) ent.nextElement();
    //			ReflNode next = tun.to;
    //			if(next == startParent){
    //				// ignore return link (i.e. in next->start->next, link start->next is this, and is skipped)
    //				continue; 
    //			}
    //			ReflNode nextRoot = findRoot(next, parents);
    //			if(startRoot == nextRoot){
    //				// cycle found. Get and record it
    //				Vector startPath = findPathToRoot(start, parents);
    //				Vector nextPath = findPathToRoot(next, parents);
    //				// eliminate the common part of the tree
    //				while(startPath.size() >= 2 && nextPath.size() >= 2 
    //						&& startPath.get(startPath.size() - 2) == nextPath.get(nextPath.size() - 2)){
    //					startPath.remove(startPath.size() - 1);
    //					nextPath.remove(nextPath.size() - 1);
    //				}
    //				Collections.reverse(startPath);
    //				startPath.addAll(nextPath);
    //				cycles.add(startPath);
    //			}else{
    //				// 
    //			}
    //		}
    //	}

    /**
     * check if two cyles are identical, i.e. elements repeat in the same order, 
     * either in one way or the other. 
     */
    private boolean cyclesEqual(Vector ca, Vector cb) {
        if ((ca.size() != cb.size()) || (ca.size() == 0)) {
            return false;
        }
        ReflNode na = (ReflNode) ca.get(0);
        int j = 0; // find in cb[j] the head of the ca cycle (na) 
        for (; j < cb.size(); j++) {
            if (na.equals(cb.get(j))) {
                break;
            }
        }
        if (j == cb.size()) {
            return false; // head not found
        }
        int i = 0;
        for (; i < ca.size(); i++) {
            if (!ca.get(i).equals(cb.get((j + i) % cb.size()))) {
                break;
            }
        }
        if (i == ca.size()) {
            return true;
        }
        // this way it failed; compare in the other direction
        for (; i < ca.size(); i++) {
            if (!ca.get(i).equals(cb.get(Math.abs(j - i) % cb.size()))) {
                break;
            }
        }
        if (i == ca.size()) {
            return true;
        }
        return false;
    }

    /** 
     * Perform a depth walk over the Reflectors graph, extracting the cycles found. This
     * method will also help at the discovery of unconnected components of the graphs.
     */
    private void depthWalk(ReflNode crt, Stack stack, Vector cycles, Set visited) {
        ReflNode prev = stack.isEmpty() ? null : (ReflNode) stack.peek();
        stack.push(crt);
        visited.add(crt);
        for (Enumeration ent = crt.tunnels.elements(); ent.hasMoreElements();) {
            IPTunnel tun = (IPTunnel) ent.nextElement();
            if (!tun.hasPeerQuality()) {
                continue; // it's not a Peers link; probably an ABPing link
            }
            ReflNode next = tun.to;
            if (next.equals(prev)) {
                // ignore return link (i.e. in prev->crt->prev, link crt->prev is this, and is skipped)
                continue;
            }
            int cycleIndex;
            if ((cycleIndex = stack.lastIndexOf(next)) != -1) {
                // found a cycle; record it
                Vector cycle = new Vector();
                for (int i = cycleIndex; i < stack.size(); i++) {
                    cycle.add(stack.get(i));
                }
                //TODO: we should add it only if it doesn't exists yet!
                boolean found = false;
                for (Iterator cit = cycles.iterator(); cit.hasNext();) {
                    Vector otherCycle = (Vector) cit.next();
                    if (cyclesEqual(cycle, otherCycle)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    cycles.add(cycle);
                }
            } else {
                // continue to walk further through the graph
                depthWalk(next, stack, cycles, visited);
            }
        }
        stack.pop();
    }

    /**
     * See depthWalk(..) for details
     */
    private void computeCyclesAndIslands() {
        Stack tmpStack = new Stack();
        Set visitedNodes = new HashSet();
        Set lastVisited = new HashSet();
        cycles.clear();
        islands.clear();

        for (Enumeration enn = snodes.elements(); enn.hasMoreElements();) {
            ReflNode n = (ReflNode) enn.nextElement();
            if (!visitedNodes.contains(n)) {
                depthWalk(n, tmpStack, cycles, visitedNodes);
                // a new island is formed by the nodes in the difference between
                // visitedNodes and lastVisited sets
                Vector island = new Vector();
                for (Iterator vnit = visitedNodes.iterator(); vnit.hasNext();) {
                    ReflNode vn = (ReflNode) vnit.next();
                    if (!lastVisited.contains(vn)) {
                        island.add(vn);
                        lastVisited.add(vn);
                    }
                }
                // the new island should not be empty!
                islands.add(island);
            }
        }
    }

    /**
     * Called from Main when a node was added to record the time in the onOffHistory 
     */
    public void addNode(ReflNode n) {
        Vector history = (Vector) onOffHistory.get(n.UnitName);
        if (history == null) {
            history = new Vector();
            onOffHistory.put(n.UnitName, history);
        }
        history.add(Long.valueOf(NTPDate.currentTimeMillis()));
    }

    /**
     * Called from Main when a node was removed to record the time in the onOffHistory 
     */
    public void removeNode(ReflNode n) {
        addNode(n); // for now, I don't make the difference
        synchronized (orphanPeers) {
            // remove orphan links from this node
            for (Iterator nit = orphanPeers.keySet().iterator(); nit.hasNext();) {
                String link = (String) nit.next();
                if (link.startsWith(n.UnitName + " ")) {
                    nit.remove();
                }
            }
        }
    }

    /**
     * Based on the onOffHistory create a list of unstable nodes, i.e. nodes that 
     * restarted more than 5 times in the last hour. Erase all data older than an hour.
     */
    private void computeUnstableNodes() {
        long hourAgo = NTPDate.currentTimeMillis() - (60 * 60 * 1000);
        unstableNodes.clear();
        for (Enumeration enn = onOffHistory.keys(); enn.hasMoreElements();) {
            String name = (String) enn.nextElement();
            Vector hist = (Vector) onOffHistory.get(name);
            int times = 0;
            for (Iterator tit = hist.iterator(); tit.hasNext();) {
                long time = ((Long) tit.next()).longValue();
                if (time < hourAgo) {
                    tit.remove();
                    continue;
                }
                times++;
            }
            if (times >= 10) { // 5 starts + 5 stops
                unstableNodes.put(name, Long.valueOf(times / 2));
            }
        }
    }

    /**
     * Update the state for a certain node in the lastState hash. Return true if there was
     * an old time for this state or false if there wasn't. 
     */
    private boolean updateState(String unitName, String state, Hashtable lastState) {
        long now = NTPDate.currentTimeMillis();
        Hashtable nodeState = (Hashtable) lastState.get(unitName);
        if (nodeState == null) {
            nodeState = new Hashtable();
            lastState.put(unitName, nodeState);
        }
        return (nodeState.put(state, Long.valueOf(now)) == null ? false : true);
    }

    /**
     * Based on the other Vector and hashes, compute the last state hash for all interesting 
     * nodes and put it into the lastState Hashtable. This method must be called after the
     * other compute... methods.
     */
    private void computeLastState() {
        lastState.clear();
        // nodes in cycle
        for (Iterator cit = cycles.iterator(); cit.hasNext();) {
            Vector cycle = (Vector) cit.next();
            for (Iterator nit = cycle.iterator(); nit.hasNext();) {
                ReflNode n = (ReflNode) nit.next();
                updateState(n.UnitName, STATE_IN_CYCLE, lastState);
            }
        }
        // nodes with orphan peers
        for (Iterator oit = orphans.iterator(); oit.hasNext();) {
            String orphanLink = (String) oit.next();
            int idx = orphanLink.indexOf(" -> ");
            if (idx > 0) {
                String name = orphanLink.substring(0, idx);
                updateState(name, STATE_ORPHAN_PEER, lastState);
            }
        }
        // unstable nodes
        for (Enumeration nit = unstableNodes.keys(); nit.hasMoreElements();) {
            String name = (String) nit.nextElement();
            updateState(name, STATE_UNSTABLE_NODE, lastState);
        }
        // nodes with asymmetric peers
        for (Iterator tit = asymTunnels.iterator(); tit.hasNext();) {
            IPTunnel tun = (IPTunnel) tit.next();
            updateState(tun.from.UnitName, STATE_ASYM_PEER, lastState);
            updateState(tun.to.UnitName, STATE_ASYM_PEER, lastState);
        }
        // nodes with peers that have low quality
        for (Iterator tit = poorQualTunnels.iterator(); tit.hasNext();) {
            IPTunnel tun = (IPTunnel) tit.next();
            updateState(tun.from.UnitName, STATE_POOR_QUAL_PEER, lastState);
            updateState(tun.to.UnitName, STATE_POOR_QUAL_PEER, lastState);
        }
    }

    /**
     * From an alert string gat the alert level. See updateHistory for details of its usage.  
     */
    private int getAlertLevel(String alertString) {
        for (int i = 0; i < alertLevel.length; i++) {
            if (alertString.equals(alertLevel[i])) {
                return i;
            }
        }
        return 0;
    }

    private int updateStateHistory() {
        int aLevel = getAlertLevel(ALERT_NONE);
        alertCause.clear();
        // update the stateHistory hash with the new states from lastState hash
        for (Enumeration uen = lastState.keys(); uen.hasMoreElements();) {
            String unitName = (String) uen.nextElement();
            Hashtable states = (Hashtable) lastState.get(unitName);
            for (Enumeration sen = states.keys(); sen.hasMoreElements();) {
                String state = (String) sen.nextElement();
                // do I care about the time in the state now? I don't think so..
                if (!updateState(unitName, state, stateHistory)) {
                    // there wasn't such a state for this node in the recent history.
                    int crtALevel = getAlertLevel((String) state2Alert.get(state));
                    // if previous cause of alert was significant lower than the current one,
                    if (crtALevel > aLevel) {
                        alertCause.clear();
                        logger.log(Level.INFO, "####### CLEARING ALERT!");
                        aLevel = crtALevel;
                    }
                    // if current cause of alert has the same importance as the previous,
                    if (crtALevel == aLevel) {
                        updateState(unitName, state, alertCause);
                        logger.log(Level.INFO, "ADD ALERT: " + unitName + " ~ " + state);
                    }
                }
            }
        }
        // remove from stateHistory states older than 1 hour
        long anHourAgo = NTPDate.currentTimeMillis() - STATE_HISTORY_PERIOD;
        for (Enumeration uen = stateHistory.keys(); uen.hasMoreElements();) {
            String unitName = (String) uen.nextElement();
            Hashtable states = (Hashtable) stateHistory.get(unitName);
            for (Enumeration sen = states.keys(); sen.hasMoreElements();) {
                String state = (String) sen.nextElement();
                long time = ((Long) states.get(state)).longValue();
                if (time < anHourAgo) {
                    states.remove(state);
                }
            }
            if (states.size() == 0) {
                stateHistory.remove(unitName);
            }
        }
        return aLevel;
    }

    private String createReport(int aLevel) {
        StringBuilder report = new StringBuilder("VRVS Alert Level: ");
        report.append(alertLevel[aLevel]).append(" @ ").append(new Date(NTPDate.currentTimeMillis()));
        report.append("\nReflRouter running on: ").append(hostname);
        report.append(" MasterMode=").append(ReflRouterJiniService.getInstance().isMasterMode());
        report.append("\n==================================================\n\n");
        // cause of alert
        if (alertCause.size() > 0) {
            report.append("Nodes & states causing alert:\n");
            for (Enumeration uen = alertCause.keys(); uen.hasMoreElements();) {
                String unitName = (String) uen.nextElement();
                report.append(unitName + ":");
                Hashtable states = (Hashtable) alertCause.get(unitName);
                for (Enumeration sen = states.keys(); sen.hasMoreElements();) {
                    String state = (String) sen.nextElement();
                    report.append(" " + state);
                }
                report.append("\n");
            }
            report.append("\n");
        }
        // cycles
        if (cycles.size() == 0) {
            report.append("NO Cycles!\n\n");
        } else {
            report.append("CYCLES FOUND:\n");
            report.append("-------------\n");
            for (Iterator cit = cycles.iterator(); cit.hasNext();) {
                Vector cycle = (Vector) cit.next();
                for (Iterator nit = cycle.iterator(); nit.hasNext();) {
                    ReflNode n = (ReflNode) nit.next();
                    report.append(n.UnitName + " ");
                }
                report.append("\n");
            }
            report.append("\n");
        }
        // orphan peers
        if (orphans.size() == 0) {
            report.append("NO Orphan Peers!\n\n");
        } else {
            report.append("ORPHAN PEERS FOUND:\n");
            report.append("-------------------\n");
            for (Iterator oit = orphans.iterator(); oit.hasNext();) {
                report.append(((String) oit.next()) + "\n");
            }
            report.append("\n");
        }
        // asymetric peers
        if (asymTunnels.size() == 0) {
            report.append("NO Asymmetrical Peers!\n\n");
        } else {
            report.append("ASYMMETRICAL PEERS FOUND:\n");
            report.append("-------------------------\n");
            for (Iterator ait = asymTunnels.iterator(); ait.hasNext();) {
                IPTunnel tun = (IPTunnel) ait.next();
                report.append(tun.from.UnitName + " -> " + tun.to.UnitName + "\n");
            }
            report.append("\n");
        }
        // quality-asymetric peers
        if (poorQualTunnels.size() == 0) {
            report.append("NO Poor Quality Peers!\n\n");
        } else {
            report.append("POOR QUALITY PEERS FOUND:\n");
            report.append("-------------------------\n");
            for (Iterator qit = poorQualTunnels.iterator(); qit.hasNext();) {
                IPTunnel tun = (IPTunnel) qit.next();
                report.append(tun.from.UnitName + " -> " + tun.to.UnitName + " [ " + tun.getPeerQuality() + " ]\n");
            }
            report.append("\n");
        }
        // unstable nodes
        if (unstableNodes.size() == 0) {
            report.append("NO unstable nodes!\n\n");
        } else {
            report.append("UNSTABLE NODES FOUND:\n");
            report.append("---------------------\n");
            for (Enumeration enn = unstableNodes.keys(); enn.hasMoreElements();) {
                String name = (String) enn.nextElement();
                report.append(name + " restarted " + unstableNodes.get(name) + " times last hour.\n");
            }
            report.append("\n");
        }
        // islands
        if (islands.size() <= 1) {
            report.append("NO Multiple islands!\n\n");
        } else {
            report.append("MULTIPLE ISLANDS FOUND:\n");
            report.append("-----------------------\n");
            for (Iterator iit = islands.iterator(); iit.hasNext();) {
                Vector island = (Vector) iit.next();
                for (Iterator nit = island.iterator(); nit.hasNext();) {
                    ReflNode n = (ReflNode) nit.next();
                    report.append(n.UnitName + " ");
                }
                report.append("\n\n");
            }
            report.append("\n");
        }

        return report.toString();
    }

    @Override
    public void run() {
        try {
            logger.log(Level.INFO, "Running VRVSGraphAnalyzer...");
            if ((snodes == null) || (snodes.size() == 0)) {
                return;
            }

            computeOrphanPeers();
            computeAsymPeers();
            computeCyclesAndIslands();
            computeUnstableNodes();
            computeLastState();
            long now = NTPDate.currentTimeMillis();
            int aLevel = updateStateHistory();

            if ((aLevel == 0) && (now > (lastEmailTime + STATUS_REPORT_INTERVAL))) {
                aLevel = getAlertLevel(ALERT_STATUS);
            }

            String report = createReport(aLevel);
            if (aLevel > 0) {
                String[] to = (String[]) alert2Email.get(alertLevel[aLevel]);
                if (to.length > 0) {
                    try {
                        DirectMailSender.getInstance().sendMessage("mlstatus@monalisa.cern.ch", to,
                                "VRVS Alert: " + alertLevel[aLevel] + " @ " + hostname, report);
                        logger.log(Level.INFO, report);
                        lastEmailTime = now;
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error sending mail", e);
                    }
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error running VRVSGraphAnalyzer:", t);
        }
    }
}
