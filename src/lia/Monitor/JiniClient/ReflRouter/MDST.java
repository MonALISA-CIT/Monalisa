package lia.Monitor.JiniClient.ReflRouter;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Computes MDST like in VRVS client but with Reflector Router specifics
 * Uses dijkstra & all.
 */
public class MDST extends MST {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(MDST.class.getName());

    private final static double MOMENTUM_FACTOR = 0.8;
    private final Vector activeNodes; // nodes that are used to compute the tree
    private double[] bestTreesCost; // the cost of the path from one node to all the others
    private Dijkstra[] oldDijkstra; // last computed tree
    private Dijkstra[] dijkstra; // current computed tree
    private Vector[] compCNX; // activeNodes is split (if necessary) in connex components
    private Vector[] oldCompCNX; // last activeNodes

    /**
     * Constructor of the MST class
     * @param nodes hashtable with all nodes, pointer to the nodes hashtable
     * from main class of JReflRouterClient
     */
    public MDST(Hashtable nodes) {
        super(nodes);
        activeNodes = new Vector();
        oldDijkstra = null;
        dijkstra = null;
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
                tun.setNextStatus(IPTunnel.INACTIVE, "none");
            }
        }
        // for each possible tunnel
        for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
            ReflNode from = (ReflNode) e.nextElement();

            from.checkReflActive();
            if (from.checkReflActive()) {
                // if node is active it appears in the MST
                activeNodes.add(from);
            } else {
                // source reflector is inactive; this must not participate to the MST
                // we should send "disconnect" cmds to this reflector to disable its
                // peer links, but since it is inactive, these cmds shwould fail
                for (Enumeration e1 = from.tunnels.elements(); e1.hasMoreElements();) {
                    IPTunnel tun = (IPTunnel) e1.nextElement();
                    tun.setNextStatus(IPTunnel.MUST_DEACTIVATE, "src not active");

                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "MST: tunnel " + tun + " must NOT be ACTIVE! (0.5)");
                    }

                }
                continue; // check next node
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "MST:: peers for " + from);
            }

            // for each possible pair
            for (Enumeration e1 = nodes.elements(); e1.hasMoreElements();) {
                ReflNode to = (ReflNode) e1.nextElement();

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "MST:: - 1 ->v" + to);
                }

                // source active; destination inactive;
                if (!to.checkReflActive()) {
                    // if there's a peer link from->to, disconnect it
                    IPTunnel tun = (IPTunnel) from.tunnels.get(to.UnitName);
                    if (tun != null) {
                        tun.checkAlive();
                        tun.setNextStatus(IPTunnel.MUST_DEACTIVATE, "dest not active");
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, "MST: tunnel " + tun + " must NOT be ACTIVE! (1)");
                        }
                    }
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "MST:: - 2 ->v" + to);
                    }
                    continue; // check next possible peer
                }

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "MST:: - 3 ->v" + to);
                }
                // both from and to are active
                // Get tunnels from->to and to->from; create missing tunnel (only if one exists!)
                IPTunnel t12 = (IPTunnel) from.tunnels.get(to.UnitName);
                IPTunnel t21 = (IPTunnel) to.tunnels.get(from.UnitName);
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "t12=" + t12 + " t21=" + t21);
                }
                if ((t12 == null) && (t21 == null)) {
                    continue;
                }
                if ((t12 != null) && (t21 == null)) {
                    t21 = new IPTunnel(to, from);
                    to.tunnels.put(from.UnitName, t21);
                }
                if ((t12 == null) && (t21 != null)) {
                    t12 = new IPTunnel(from, to);
                    from.tunnels.put(to.UnitName, t12);
                }
                t12.checkAlive();
                t21.checkAlive();

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "MST:: - 4 ->v" + to);
                }

                // if there are already peer links between these reflectors (one or both)
                if (t12.hasPeerQuality() || t21.hasPeerQuality()) {
                    // but we don't have both RTTime pings
                    if (!(t12.hasInetQuality() && t21.hasInetQuality())) {
                        // this tunnel must exist in the MST
                        t12.setNextStatus(IPTunnel.MUST_ACTIVATE, "has PeerQ but no Inet params");
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, "MST: tunnel " + t12
                                    + " must be ACTIVE! - has peerq, no Inet params");
                        }
                        continue;
                    }
                }
                // if there are no peer links between these two reflectors
                if (!t12.hasPeerQuality() && !t21.hasPeerQuality()) {
                    // and at most one RTTime ping
                    if (!(t12.hasInetQuality() && t21.hasInetQuality())) {
                        // this tunnel must NOT appear in the MST
                        t12.setNextStatus(IPTunnel.MUST_DEACTIVATE, "no PeerQ and incomplete Inet params");
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, "MST: tunnel " + t12
                                    + " must NOT be ACTIVE - no peerq, incomplete inet params !");
                        }
                        continue;
                    }
                    // or it has high packet loss
                    if (t12.hasHighPktLoss() || t21.hasHighPktLoss()) {
                        t12.setNextStatus(IPTunnel.MUST_DEACTIVATE, "no PeerQ, high packet loss");
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, "MST: tunnel " + t12
                                    + " must NOT be ACTIVE - no peerq, high packet loss !");
                        }
                    }
                }
                // otherwise, this link's next state is INACTIVE and will be considered
                // in computing the MST
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "MST: tunnel " + t12 + " will participate to the MST (4)");
                }
            }
        }
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "MST: finished computing restrictions!");
        }
    }

    /**
     * Compute the Dijkstra alg. for a set of reflNodes, starting from a certain node
     */
    class Dijkstra {
        private final Vector nodes; // the list of nodes
        private final Vector tunnels;
        private final int[] parent; // for each node, it's parent
        private final double[] distance; // the distance between source and each node
        private final int n; // number of nodes
        private int source; // the source node for last compute()

        /**
         * Construct the class with a set of nodes
         * @param nodes
         */
        Dijkstra(Vector nodes) {
            this.nodes = nodes;
            tunnels = new Vector();
            n = nodes.size();

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "######### Dijkstra constructor n=" + n);
            }

            parent = new int[n];
            distance = new double[n];
            for (int i = 0; i < n; i++) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, ((ReflNode) nodes.get(i)).UnitName + "=" + i + " ");
                }
            }
        }

        /**
         * compute the minimum path from a node to all the others 
         * @param s the starting point, index int the nodes vector from the constructor
         */
        void compute(int s) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "######### Dijk: compute(" + s + ")");
            }
            int i, j;
            double MAX = 1E+50;
            this.source = s;

            tunnels.clear();
            for (i = 0; i < n; i++) {
                parent[i] = -1;
                distance[i] = MAX;
            }
            distance[s] = 0;
            boolean madeConnection = true;
            while (madeConnection) {
                IPTunnel[] bestPair = null;
                double bestPairCost = MAX;
                int bestNS = -1, bestNN = -1;

                // as long as we can connect a new node,
                for (i = 0; i < n; i++) {
                    if (distance[i] == MAX) {
                        continue;
                    }
                    // select a node that is already in the tree
                    ReflNode ns = (ReflNode) nodes.get(i);
                    for (j = 0; j < n; j++) {
                        if (distance[j] < MAX) {
                            continue;
                        }
                        // select a node that isn't in the tree
                        ReflNode nn = (ReflNode) nodes.get(j);
                        // check if there is a connection between these two
                        IPTunnel[] pair = getTunnelPair(ns, nn);
                        if (pair == null) {
                            continue;
                        }
                        //  check if this pair is better than the last one
                        double cost = getTunnelPairCost(pair) + distance[i];
                        if (cost < bestPairCost) {
                            bestPair = pair;
                            bestPairCost = cost;
                            bestNS = i;
                            bestNN = j;
                        }
                    }
                }
                // if we actually found a connection
                if (bestPair != null) {
                    tunnels.add(bestPair[0]);
                    tunnels.add(bestPair[1]);
                    distance[bestNN] = bestPairCost;
                    parent[bestNN] = bestNS;
                } else {
                    madeConnection = false;
                }
            }
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "\n######### Dijk: compute finished");
            }
        }

        /**
         * compute the distance between two nodes, using using links in the computed tree
         * @param n1 first node, index in nodes
         * @param n2 second node, index in nodes
         * @return shortest distance from n1 to n2
         */
        double getDistance(int n1, int n2) {
            int i, j;
            int[] p1 = new int[n]; // nodes to root for n1
            int[] p2 = new int[n]; // nodes to root for n2
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "######### getDistance(" + n1 + ", " + n2 + ")");
            }
            // compute list of nodes up to root, starting from n1
            p1[i = 0] = n1;
            while (parent[p1[i]] >= 0) {
                p1[i + 1] = parent[p1[i]];
                i++;
            }
            p2[j = 0] = n2;
            while (parent[p2[j]] >= 0) {
                p2[j + 1] = parent[p2[j]];
                j++;
            }
            // eliminate the common part of the two paths
            while ((i > 0) && (j > 0) && (p1[i - 1] == p2[j - 1])) {
                i--;
                j--;
            }
            // the distance between n1 and n2 is the sum of the distances between
            // this ramification and each of the two nodes
            double d1 = 0, d2 = 0;
            while (i > 0) {
                d1 += distance[p1[i - 1]] - distance[p1[i]];
                i--;
            }
            while (j > 0) {
                d2 += distance[p2[j - 1]] - distance[p2[j]];
                j--;
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "######### distance = " + (d1 + d2));
            }

            return d1 + d2;
        }

        /**
         * return the list of tunnels in the computed tree
         * @return vector of tunnels
         */
        Vector getTree() {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "######### getTree");
            }
            Vector rez = new Vector();
            rez.addAll(tunnels);
            return rez;
        }

        /**
         * computes the diameter of the computed tree
         * @return the longest path between two nodes on the computed tree
         */
        double getDiameter() {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "######### getDiameter");
            }
            double max = 0;
            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    double dist = getDistance(i, j);
                    if (dist > max) {
                        max = dist;
                    }
                }
            }
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "######### diameter = " + max);
            }
            return max;
        }

        /**
         * update distances according to current parrent vector. This is used
         * to check later if the old tree is better (has a diameter comparable)
         * than the current tree. See computeST() for more details
         */
        void updateDistances() {
            for (int i = 0; i < n; i++) {
                distance[i] = computeDist(i);
            }
        }

        /**
         * computes the distance from a node to root using live data, not distances
         * vector
         * @param i the node to compute distance to
         * @return the distance
         */
        double computeDist(int i) {
            if (parent[i] == -1) {
                return 0;
            }
            ReflNode ns = (ReflNode) nodes.get(i);
            ReflNode nn = (ReflNode) nodes.get(parent[i]);
            return computeDist(parent[i]) + getTunnelPairCost(getTunnelPair(ns, nn));
        }
    }

    /**
     * select the nextTree as the current/old tree.
     * This should be called when the ReflRouted decides that the commands should be sent
     */
    @Override
    public void commitST() {
        oldTree.clear();
        oldTree.addAll(nextTree);
        oldDijkstra = dijkstra;
        oldCompCNX = compCNX;

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "######## Switching trees!");
        }
    }

    /**
     * decides wether nextTree is better than oldTree, taking into account the
     * momentum factor
     * @return true if cost of next tree is better than MOM_FACT * old tree
     */
    public boolean STisBetter() {
        if (oldDijkstra == null) {
            oldDijkstra = dijkstra;
            return true;
        } else {
            // check if different number of cnx. comp.
            if (oldDijkstra.length != dijkstra.length) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "@@@@@@@ different number of cnx. comp!");
                }
                return true;
            }
            for (int c = 0; c < dijkstra.length; c++) {
                if (dijkstra[c].n != oldDijkstra[c].n) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "@@@@@@@ different number of nodes in cnx. comp!");
                    }
                    return true;
                }
                oldDijkstra[c].updateDistances();
                double oldDiam = oldDijkstra[c].getDiameter();
                dijkstra[c].updateDistances();
                bestTreesCost[c] = dijkstra[c].getDiameter();
                // if a tree from a cnx. comp. is better, update all.
                if (bestTreesCost[c] < (MOMENTUM_FACTOR * oldDiam)) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "@@@@@@@ found a better subtree");
                    }
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * compute an array with connex components of nodes
     * @param nodes ReflNode-s
     * @return an array that contains vectors with nodes in the same cnx component
     */
    Vector[] getCompCNX(Vector nodes) {
        int n = nodes.size();
        int[] comp = new int[n];
        int[] nrcomp = new int[n];
        int i, j, k, u, replace, search;

        for (i = 0; i < n; i++) {
            comp[i] = i;
            nrcomp[i] = -1;
        }
        boolean connMade = true;
        while (connMade) {
            connMade = false;
            for (i = 0; i < n; i++) {
                replace = comp[i];
                ReflNode ns = (ReflNode) nodes.get(i);
                for (j = i + 1; j < n; j++) {
                    search = comp[j];
                    if (replace == search) {
                        continue;
                    }
                    ReflNode nn = (ReflNode) nodes.get(j);
                    if (getTunnelPair(ns, nn) != null) {
                        for (k = 0; k < n; k++) {
                            if (comp[k] == search) {
                                comp[k] = replace;
                                connMade = true;
                            }
                        }
                    }
                }
            }
        }
        // count the components
        for (i = 0; i < n; i++) {
            nrcomp[comp[i]] = comp[i];
        }
        k = 0;
        for (i = 0; i < n; i++) {
            if (nrcomp[i] != -1) {
                k++;
            }
        }
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "######### Nr of CNX comp: " + k);
        }
        Vector[] rez = new Vector[k];
        for (j = 0, i = 0; j < k; j++) {
            // j is the connex component
            rez[j] = new Vector();
            // nrcomp[i] is the number of the connex component
            while ((i < n) && (nrcomp[i] == -1)) {
                i++;
            }
            if (i == n) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "######## CNX comp FAILED");
                }
            }
            // u goes through all nodes and inserts it in the current cnx. comp.
            for (u = 0; u < n; u++) {
                if (comp[u] == nrcomp[i]) {
                    rez[j].add(nodes.get(u));
                }
            }
            // go search the next component
            i++;
        }
        for (i = 0; i < k; i++) {
            for (j = 0; j < rez[i].size(); j++) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " " + ((ReflNode) rez[i].get(j)).UnitName);
                }
            }
        }
        return rez;
    }

    /**
     * Invoked by ReflRouter when MDST must be recomputed. It computes two vectors of
     * tunnels (commands) that must be activated (toActivate) and deactivated (toDeactivate)
     * in order to convert the current tree to the minimum spanning tree.
     * 
     * A selected tunnel consist of link from A to B and from B to A i.e. both must
     * be selected and the selection is made taking into account both links' quality.	 
     *
     */
    @Override
    public void computeST() {
        activeNodes.clear();
        crtTree.clear();
        nextTree.clear();
        toActivate.clear();
        toDeactivate.clear();
        //if(simulation)
        //	printTunnels("######### MDST: Old tree's tunnels:", oldTree);

        // first, initalize trees and crtTree
        computeRestrictions();

        /* TEMPORARY *///if(simulation){
        crtTree.clear();
        crtTree.addAll(oldTree);
        //}

        // compute the connex components for the activeNodes vector
        compCNX = getCompCNX(activeNodes);
        dijkstra = new Dijkstra[compCNX.length];
        bestTreesCost = new double[compCNX.length];
        trees.clear();
        // the nextTree is built by the MDST algorithm
        for (int c = 0; c < compCNX.length; c++) {
            dijkstra[c] = new Dijkstra(compCNX[c]);
            int bestTreeSource = -1;
            double minDiam = -1;
            bestTreesCost[c] = Double.MAX_VALUE;
            // compute Dijkstra algorithm for each node and select the tree
            // that has the minimum diameter
            for (int i = 0; i < compCNX[c].size(); i++) {
                dijkstra[c].compute(i);
                minDiam = dijkstra[c].getDiameter();
                if (minDiam < bestTreesCost[c]) {
                    bestTreeSource = i;
                    bestTreesCost[c] = minDiam;
                }
            }
            if (bestTreeSource >= 0) {
                // compute once again staring from the best source to have the
                // parents vector in Dijkstra class set according to this tree
                dijkstra[c].compute(bestTreeSource);
                trees.addAll(dijkstra[c].getTree());
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "######### minDiam = " + minDiam + " bestSrc=" + bestTreeSource);
                }
                nextTree.addAll(trees);
            }
        }

        //if(simulation)
        //	printTunnels("######### MDST: Computed tree's tunnels:", nextTree);
        // compute the toActivate and toDeactivate Vectors of tunnels
        computeDifferences(crtTree, nextTree);
        if (simulation) {
            printTunnels("######### Tunnels to deactivate:", toDeactivate);
        }
        if (simulation) {
            printTunnels("######### Tunnels to activate:", toActivate);
        }
    }

    /**
     * get the tunnel pair between two nodes, if it exists
     * @param n1 the first node
     * @param n2 the second node
     * @return a vector with the two tunnles, or null if there is no tunnel
     */
    private IPTunnel[] getTunnelPair(ReflNode n1, ReflNode n2) {
        IPTunnel t12 = (IPTunnel) n1.tunnels.get(n2.UnitName);
        IPTunnel t21 = (IPTunnel) n2.tunnels.get(n1.UnitName);
        if ((t12 == null) || (t21 == null) || !t12.hasInetQuality() || !t21.hasInetQuality()) {
            return null;
        } else {
            IPTunnel pair[] = new IPTunnel[2];
            pair[0] = t12;
            pair[1] = t21;
            return pair;
        }
    }

    /**
     * return the cost of a tunnel pair (obtained from getTunnelPair), taking into
     * account the current state of the links (wether they are selected or not).
     * @param pair the vector with tow tunnels
     * @return the cost
     */
    private double getTunnelPairCost(IPTunnel[] pair) {
        double crtCost = pair[0].getInetQuality() + pair[1].getInetQuality();
        if (crtTree.contains(pair[0]) || crtTree.contains(pair[1])) {
            crtCost *= MOMENTUM_FACTOR;
        }
        return crtCost;
    }

}
