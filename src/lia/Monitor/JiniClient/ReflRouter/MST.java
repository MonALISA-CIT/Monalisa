package lia.Monitor.JiniClient.ReflRouter;

import java.text.NumberFormat;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;

/**
 * Computes MST like in VRVS client but with Reflector Router specifics
 * Uses the same Boruvka MST algorithm.
 */
public class MST {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(MST.class.getName());

    private static double MOMENTUM_FACTOR = 0.8; // help to decide when to change from the old
    private static double TRESHOLD_COST = 10; // tree to the next tree
    Hashtable nodes; // key = UnitName; value = ReflNode
    protected Vector trees; // temporary vector of subtrees (vectors of nodes) that are joint according to the algorithm
    public Vector crtTree; // vector with IPTunnels that form the current tree
    public Vector nextTree; // vector with IPTunnels that form the computed tree
    // the differences between crtTree and nextTree:
    public Vector toActivate; // vector with IPTunnels that are going to be activated
    public Vector toDeactivate; // vector with IPTunnels that are going to be deactivated
    public Vector oldTree; // previous tree, used in simulation mode
    public boolean simulation; // if true, previous computed tree is used as starting point

    private static NumberFormat numberFormat = NumberFormat.getNumberInstance();
    static {
        numberFormat.setMaximumFractionDigits(2);
    }

    /**
     * empty constructor
     */
    public MST() {
        // nothing to do here...
    }

    /**
     * Constructor of the MST class
     * @param nodes hashtable with all nodes, pointer to the nodes hashtable
     * from main class of JReflRouterClient
     */
    public MST(Hashtable nodes) {
        this.nodes = nodes;
        trees = new Vector();
        toActivate = new Vector();
        toDeactivate = new Vector();
        crtTree = new Vector();
        nextTree = new Vector();
        oldTree = new Vector();
    }

    /**
     * init old tree with the currently selected peer connections
     */
    public void initOldTree() {
        oldTree.clear();
        StringBuilder dump = new StringBuilder();
        if (logger.isLoggable(Level.FINER)) {
            dump.append("Existing reflectors and tunnels:\n");
        }
        for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
            ReflNode from = (ReflNode) e.nextElement();
            if (logger.isLoggable(Level.FINER)) {
                dump.append(from).append("\n");
            }
            for (Enumeration e1 = from.tunnels.elements(); e1.hasMoreElements();) {
                IPTunnel tun = (IPTunnel) e1.nextElement();
                if (tun.getCrtStatus() == IPTunnel.ACTIVE) {
                    oldTree.add(tun);
                    if (logger.isLoggable(Level.FINER)) {
                        dump.append(" ==").append(tun).append("\n");
                    }
                } else {
                    if (logger.isLoggable(Level.FINEST)) {
                        dump.append(" --").append(tun).append("\n");
                    }
                }
            }
        }
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, dump.toString());
        }
    }

    /** 
     * initialization phase. First, we have to compute the restrictions for the MST
     * some nodes/tunnels must or must not appear into the final MST according to
     * the active/inactive status of the corresponding reflectors
     */
    private void computeRestrictions() {
        // first, init tunnels' next state and build the current tree
        for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
            ReflNode from = (ReflNode) e.nextElement();
            for (Enumeration e1 = from.tunnels.elements(); e1.hasMoreElements();) {
                IPTunnel tun = (IPTunnel) e1.nextElement();
                tun.checkAlive();
                if (tun.getCrtStatus() == IPTunnel.ACTIVE) {
                    crtTree.add(tun);
                }
                tun.setNextStatus(IPTunnel.INACTIVE, "not selected");
            }
        }
        // for each possible tunnel
        for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
            ReflNode from = (ReflNode) e.nextElement();

            if (from.checkReflActive()) {
                // if node is active it appears in the MST
                Vector subtree = new Vector();
                subtree.add(from);
                trees.add(subtree);
            } else {
                // source reflector is inactive; this must not participate to the MST
                // we should send "disconnect" cmds to this reflector to disable its
                // peer links, but since it is inactive, these cmds shwould fail
                for (Enumeration e1 = from.tunnels.elements(); e1.hasMoreElements();) {
                    IPTunnel tun = (IPTunnel) e1.nextElement();
                    tun.setNextStatus(IPTunnel.MUST_DEACTIVATE, "src inactive");
                }
                continue; // check next node
            }
            // for each possible pair
            for (Enumeration e1 = nodes.elements(); e1.hasMoreElements();) {
                ReflNode to = (ReflNode) e1.nextElement();

                // source active; destination inactive;
                if (!to.checkReflActive()) {
                    // if there's a peer link from->to, disconnect it
                    IPTunnel tun = (IPTunnel) from.tunnels.get(to.UnitName);
                    if (tun != null) {
                        tun.setNextStatus(IPTunnel.MUST_DEACTIVATE, "dest inactive");
                    }
                    continue; // check next possible peer
                }
                // both from and to are active
                // Get tunnels from->to and to->from; create missing tunnel (only if one exists!)
                IPTunnel t12 = (IPTunnel) from.tunnels.get(to.UnitName);
                IPTunnel t21 = (IPTunnel) to.tunnels.get(from.UnitName);
                // if there is no tunnel between these 2 nodes, move on
                if ((t12 == null) && (t21 == null)) {
                    continue;
                }
                // if its one way only, create the other as well
                if ((t12 != null) && (t21 == null)) {
                    t21 = new IPTunnel(to, from);
                    to.tunnels.put(from.UnitName, t21);
                }
                // and in the other direction
                if ((t12 == null) && (t21 != null)) {
                    t12 = new IPTunnel(from, to);
                    from.tunnels.put(to.UnitName, t12);
                }
                // if we have both, but none has any up-to-date info, remove both
                if (!(t12.checkAlive() || t21.checkAlive())) {
                    from.tunnels.remove(to.UnitName);
                    to.tunnels.remove(from.UnitName);
                    continue;
                }
                // if there are already peer links between these reflectors (one or both)
                if (t12.hasPeerQuality() || t21.hasPeerQuality()) {
                    // but we don't have both RTTime pings
                    if (!(t12.hasInetQuality() && t21.hasInetQuality())) {
                        // this tunnel must exist in the MST
                        t12.setNextStatus(IPTunnel.MUST_ACTIVATE, "has PeerQ, no InetQ");
                        continue;
                    }
                }
                // if it has high packet loss
                if (t12.hasHighPktLoss() || t21.hasHighPktLoss()) {
                    t12.setNextStatus(IPTunnel.MUST_DEACTIVATE, "high PktLoss");
                    continue;
                }
                // if there are no peer links between these two reflectors
                if (!t12.hasPeerQuality() && !t21.hasPeerQuality()) {
                    // and at most one RTTime ping
                    if (!(t12.hasInetQuality() && t21.hasInetQuality())) {
                        // this tunnel must NOT appear in the MST
                        t12.setNextStatus(IPTunnel.MUST_DEACTIVATE, "no PeerQ, no InetQ");
                        continue;
                    }
                }
                // otherwise, this link's next state is INACTIVE and will be considered
                // in computing the MST
            }
        }
    }

    /**
     * computes the toActivate and toDeactivate vector of tunnels, as the 
     * differences between the tunnels passed as parameters
     * @param orig the set of tunnels that form the current tree
     * @param now the set of tunnels that form the computed min span tree
     */
    protected void computeDifferences(Vector orig, Vector now) {
        // all tunnels that are present in the orig vector and not in the
        // now vector must be deactivated
        for (int i = 0; i < orig.size(); i++) {
            IPTunnel tun = (IPTunnel) orig.get(i);
            if (!now.contains(tun)) {
                toDeactivate.add(tun);
            }
        }
        // and all tunnels that exist in now tree but not in the orig tree
        // must be activated
        for (int i = 0; i < now.size(); i++) {
            IPTunnel tun = (IPTunnel) now.get(i);
            if (!orig.contains(tun)) {
                toActivate.add(tun);
            }
        }
    }

    /**
     * select the nextTree as the current/old tree.
     * This should be called when the ReflRouted decides that the commands should be sent
     */
    public void commitST() {
        oldTree.clear();
        oldTree.addAll(nextTree);
    }

    /**
     * decides wether nextTree is better than oldTree, taking into account the
     * momentum factor
     * @param msg append the reason to this string buffer
     * @return true if cost of next tree is better than MOM_FACT * old tree
     */
    public boolean STisBetter(StringBuilder msg) {
        //		double nextCost = tunnelsCost(nextTree);
        //		double oldCost = tunnelsCost(oldTree);
        double nextCost = tunnelsCost(toActivate);
        double oldCost = tunnelsCost(toDeactivate);
        boolean momentumCond = (nextCost < (MOMENTUM_FACTOR * oldCost));
        boolean tresholdCond = (nextCost < (oldCost - TRESHOLD_COST));
        msg.append(numberFormat.format(nextCost)).append(momentumCond ? " < " : " > ");
        msg.append(numberFormat.format(oldCost)).append("*");
        msg.append(numberFormat.format(MOMENTUM_FACTOR));
        msg.append(" momentum=").append(momentumCond).append(" ");
        msg.append(numberFormat.format(nextCost)).append(tresholdCond ? " < " : " > ");
        msg.append(numberFormat.format(oldCost)).append("-");
        msg.append(numberFormat.format(TRESHOLD_COST));
        msg.append(" treshold=").append(tresholdCond);
        return momentumCond && tresholdCond;
    }

    /**
     * Invoked by ReflRouter when MST must be recomputed. It computes two vectors of
     * tunnels (commands) that must be activated (toActivate) and deactivated (toDeactivate)
     * in order to convert the current tree to the minimum spanning tree.
     * 
     * A selected tunnel consist of link from A to B and from B to A i.e. both must
     * be selected and the selection is made taking into account both links' quality.	 
     *
     */
    public void computeST() {
        MOMENTUM_FACTOR = AppConfig.getd("lia.Monitor.JiniClient.ReflRouter.MST.MOMENTUM_FACTOR", 0.8);
        TRESHOLD_COST = AppConfig.getd("lia.Monitor.JiniClient.ReflRouter.MST.TRESHOLD_COST", 20);
        trees.clear();
        crtTree.clear();
        nextTree.clear();
        toActivate.clear();
        toDeactivate.clear();
        //		if(simulation)
        //			printTunnels("MST: Old tree's tunnels:", oldTree);

        // first, initalize trees and crtTree
        computeRestrictions();

        if (simulation) {
            // assume that last given commands took effect
            crtTree.clear();
            crtTree.addAll(oldTree);
        } else {
            // start from the current real configuration
            initOldTree();
        }

        IPTunnel bestTunnel[] = new IPTunnel[2];
        IPTunnel crtTunnel[] = new IPTunnel[2];

        // the nextTree is built by the MST algorithm
        boolean madeConnection = true;
        Vector startSubtree;
        Vector bestSubtree;
        while (madeConnection) {
            madeConnection = false;
            // for a subtree
            double bestCost = Double.MAX_VALUE;
            //IPTunnel bestTunnel [] = null;			// in fact, 2 links A-B & B-A
            bestTunnel[0] = null;
            bestTunnel[1] = null;
            startSubtree = null;
            bestSubtree = null;
            // for each subtree
            for (int i = 0; i < trees.size(); i++) {
                Vector subtree = (Vector) trees.get(i);
                // with all other subtrees, try to find the best tunnel between them
                for (int j = i + 1; j < trees.size(); j++) {
                    Vector subtree1 = (Vector) trees.get(j);
                    // try any node in the first subtree with any other node in the 2nd subtree
                    crtTunnel[0] = null;
                    crtTunnel[1] = null;
                    if (!getBestTunnelPair(subtree, subtree1, crtTunnel)) {
                        continue;
                    }
                    //System.out.println("crtTun "+crtTunnel[0]+" "+crtTunnel[1]);
                    double crtCost = crtTunnel[0].getInetQuality() + crtTunnel[1].getInetQuality();
                    // links already in MST are preferred 
                    if (crtTree.contains(crtTunnel[0]) || crtTree.contains(crtTunnel[1])) {
                        crtCost = Math.min(crtCost * MOMENTUM_FACTOR, crtCost - TRESHOLD_COST);
                    }
                    // check if this tunnel is better
                    // still, at this point, the selection is done by cost, not by MUST_CONNECT links
                    // because typically, these are links with problems
                    if (bestCost > crtCost) {
                        bestCost = crtCost; // goal: minimize cost
                        bestTunnel[0] = crtTunnel[0];
                        bestTunnel[1] = crtTunnel[1];
                        startSubtree = subtree;
                        bestSubtree = subtree1;
                    }
                }
            }
            //System.out.println(bestTunnel[0]+" "+bestTunnel[1]);
            if ((bestTunnel[0] != null) && (bestTunnel[1] != null)) {
                // i want to keep the MUST_ACTIVATE flags and therefore i change it only if inactive
                if (bestTunnel[0].getNextStatus() == IPTunnel.INACTIVE) {
                    bestTunnel[0].setNextStatus(IPTunnel.ACTIVE, "better");
                }
                if (bestTunnel[1].getNextStatus() == IPTunnel.INACTIVE) {
                    bestTunnel[1].setNextStatus(IPTunnel.ACTIVE, "better");
                }
                nextTree.add(bestTunnel[0]); // from A to B
                nextTree.add(bestTunnel[1]); // and from B to A
                // concatenate subtree with subtree1
                if (logger.isLoggable(Level.FINE)) {
                    StringBuilder sb = new StringBuilder("Joining:");
                    sb.append(treeToString(startSubtree)).append("+ ").append(treeToString(bestSubtree));
                    sb.append(" :: ").append(bestTunnel[0].from.UnitName);
                    sb.append(" <-> ").append(bestTunnel[1].from.UnitName);
                    sb.append(" = ").append(bestTunnel[0].getInetQuality());
                    logger.fine(sb.toString());
                }
                startSubtree.addAll(bestSubtree);
                trees.remove(bestSubtree);
                madeConnection = true;
            }
        }
        if (!simulation) {
            printTunnels("MST: Computed tree's tunnels:", nextTree);
        }
        // compute the toActivate and toDeactivate Vectors of tunnels
        computeDifferences(crtTree, nextTree);
        if (!simulation) {
            printTunnels("Tunnels to deactivate:", toDeactivate);
        }
        if (!simulation) {
            printTunnels("Tunnels to activate:", toActivate);
        }
    }

    /**
     * return the best pair of links between two nodes belonging to two different
     * subtrees; For this, it considers the InetQuality of the link, returned
     * by the ABPing module running at the reflector site.
     * @param st1 the first subtree
     * @param st2 the second subtree
     * @return a vector with 2 links
     */
    private boolean getBestTunnelPair(Vector st1, Vector st2, IPTunnel[] result) {
        double bestCost = Double.MAX_VALUE;
        boolean foundPair = false;
        // for each node in st1 with each in st2 find minimum cost pair
        for (int i = 0; i < st1.size(); i++) {
            ReflNode n1 = (ReflNode) st1.get(i);
            for (int j = 0; j < st2.size(); j++) {
                ReflNode n2 = (ReflNode) st2.get(j);
                // check if tunnels from N1 to N2 and back exist
                IPTunnel t12 = (IPTunnel) n1.tunnels.get(n2.UnitName);
                IPTunnel t21 = (IPTunnel) n2.tunnels.get(n1.UnitName);
                // first, this tunnel must exist
                if ((t12 == null) || (t21 == null)) {
                    continue;
                }
                // this tunnel pair MUST be active, so we return it
                if ((t12.getNextStatus() == IPTunnel.MUST_ACTIVATE) || (t21.getNextStatus() == IPTunnel.MUST_ACTIVATE)) {
                    result[0] = t12;
                    result[1] = t21;
                    return true;
                }
                // this tunnel pair MUST NOT be active, so we just skip it
                if ((t12.getNextStatus() == IPTunnel.MUST_DEACTIVATE)
                        || (t21.getNextStatus() == IPTunnel.MUST_DEACTIVATE)) {
                    continue;
                }
                // then, in order to be taken into comparison, it must have both InetQalities
                if (!(t12.hasInetQuality() && t21.hasInetQuality())) {
                    continue;
                }
                double crtCost = t12.getInetQuality() + t21.getInetQuality();
                // already selected links are preferred 
                if (crtTree.contains(t12) || crtTree.contains(t21)) {
                    crtCost = Math.min(crtCost * MOMENTUM_FACTOR, crtCost - TRESHOLD_COST);
                }
                // check if current tunnel pair is better than the current best
                if (bestCost > crtCost) {
                    result[0] = t12;
                    result[1] = t21;
                    bestCost = crtCost;
                    foundPair = true;
                }
            }
        }
        //System.out.println("bC="+bestCost);
        return foundPair;
    }

    /**
     * get a string representation of the tree
     * @param v vector with nodes belonging to the same tree
     * @return a string with reflectors' names that are in this tree
     */
    private String treeToString(Vector v) {
        String rez = "";
        for (Enumeration e1 = v.elements(); e1.hasMoreElements();) {
            ReflNode n1 = (ReflNode) e1.nextElement();
            rez += n1.UnitName + " ";
        }
        return rez;
    }

    //	/**
    //	 * check if two reflectors are in the same (sub)tree
    //	 * @param r1 first reflector
    //	 * @param r2 second reflector
    //	 * @return true or false
    //	 */
    //	boolean inTheSameTree(ReflNode r1, ReflNode r2){
    //		for(int i = 0; i<trees.size(); i++){
    //			Vector subtree = (Vector) trees.get(i);
    //			if(subtree.contains(r1) && subtree.contains(r2))
    //				return true;
    //		}
    //		return false;
    //	}

    /**
     * @param message explanis what kind of tunnels contains v
     * @param v vector with tunnels
     */
    public void printTunnels(String message, Vector v) {
        String priority;
        double cost = tunnelsCost(v);
        StringBuilder text = new StringBuilder();
        text.append("PRINT_TUNNELS: " + message + "\n");
        for (int i = 0; i < v.size(); i++) {
            IPTunnel tun = (IPTunnel) v.get(i);
            int nextStatus = tun.getNextStatus();
            boolean wantChange = tun.getCrtStatus() != nextStatus;
            if (wantChange) {
                if ((nextStatus == IPTunnel.ACTIVE) || (nextStatus == IPTunnel.INACTIVE)) {
                    priority = "  OPTN -> ";
                } else {
                    priority = "  MUST -> ";
                }
            } else {
                priority = "       -> ";
            }
            text.append(priority).append(tun.toString()).append("\n");
        }
        text.append("Total cost = ").append(numberFormat.format(cost));
        logger.log(Level.INFO, text.toString());
    }

    /**
     * Returns the sum of the tunnels' cost
     * @param v vector with tunnels
     * @return the sum of the tunnels' costs
     */
    public double tunnelsCost(Vector v) {
        double cost = 0;
        for (int i = 0; i < v.size(); i++) {
            IPTunnel tun = (IPTunnel) v.get(i);
            if (tun.hasInetQuality()) {
                cost += tun.getInetQuality();
            }
        }
        return cost;
    }

    /**
     * print MST to console
     */
    public void printMST() {
        StringBuilder text = new StringBuilder();
        text.append("CURRENT ACTIVE TUNNELS:\n");
        for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
            ReflNode n = (ReflNode) e.nextElement();
            //logger.log(Level.INFO, "Tunnels for "+n);
            text.append("Tunnels for " + n + "\n");
            for (Enumeration e1 = n.tunnels.elements(); e1.hasMoreElements();) {
                IPTunnel tun = (IPTunnel) e1.nextElement();
                //logger.log(Level.INFO, "  ===>"+tun);
                text.append("===>" + tun + "\n");
            }
        }
        logger.log(Level.INFO, text.toString());
    }
}
