package lia.Monitor.JiniClient.CommonGUI.Topology;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.StringTokenizer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniSerFarmMon.MLLUSHelper;
import lia.Monitor.monitor.GenericMLEntry;
import net.jini.core.lookup.ServiceItem;

/**
 * AliasCache is used by NetTopologyAnalizer and GTopoArea to set known IPs
 * and get IP aliases
 */
public class AliasCache {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(AliasCache.class.getName());
    static String ipidQuery = null; //AppConfig.getProperty("lia.Monitor.TopologyIPid" ,"http://monalisa2.cern.ch/cgi-bin/topo/getipids.py");

    static final int FULL_UPDATE = 500;
    static final int CHECK_MODIF = 30;
    static final int CLEANUP_GRS = 300;
    static final int IPID_SERV_UPDATE = 50;

    Vector ipGroups; // we store all IPs in groups each group contains a Vector with router-like rcNodes with IPs on the same machine
    Vector knownRouters = null; // link to the routers Vector in NetTopologyAnalizer
    Vector knownFarms = null; // link to the farms Vector in NetTopologyAnalizer
    Vector newRouters = null;
    String lastModifTime = "1"; // time of last modification in ipid service
    int timerCounter = 0;
    private NetTopologyAnalyzer netTopoAnalyzer;

    public AliasCache(NetTopologyAnalyzer netTopoAna) {
        ipGroups = new Vector();
        newRouters = new Vector();
        this.netTopoAnalyzer = netTopoAna;
        TimerTask ttask = new TimerTask() {
            NetTopologyAnalyzer nta = netTopoAnalyzer;

            public void getIPidServiceAddress() {
                // get IPID service address
                ServiceItem[] si = MLLUSHelper.getInstance().getTopologyServices();
                if ((si == null) || (si.length == 0) || (si[0].attributeSets.length == 0)) {
                    logger.log(Level.INFO, "No IPID service was found (yet)");
                    ipidQuery = null;
                } else {
                    GenericMLEntry gmle = (GenericMLEntry) si[0].attributeSets[0];
                    if (gmle.hash != null) {
                        ipidQuery = (String) gmle.hash.get("URL") + "/GetIPids";
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "Found an IPID service"); // URL: "+ipidQuery);
                        }
                    }
                }
            }

            @Override
            public void run() {
                Thread.currentThread().setName(" ( ML ) - Topology - AliasCache Timer Thread");
                try {
                    if (!nta.monitor.main.topologyShown) {
                        return;
                    }
                    if (ipidQuery == null) {
                        getIPidServiceAddress();
                    }
                    timerCounter++;
                    if ((timerCounter % IPID_SERV_UPDATE) == 0) {
                        getIPidServiceAddress();
                    }
                    if ((timerCounter % FULL_UPDATE) == 0) {
                        updateRouterData(true);
                    } else if ((timerCounter % CLEANUP_GRS) == 0) {
                        cleanupIpGroups();
                    } else if ((timerCounter % CHECK_MODIF) == 0) {
                        updateRouterData(false);
                    } else {
                        resolveNewRouters();
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        };
        BackgroundWorker.schedule(ttask, 4 * 1000, 4 * 1000);
    }

    /**
     * check if the given rcNode is an unknown router 
     */
    private void checkRouter(rcNode r) {
        if (!r.IPaddress.equals("?") && (findRouterGroup(r) == -1)) {
            newRouters.add(r);
        }
    }

    /**
     * check for new IPs in the given trace
     * @param trace - vector of EntityLinks 
     */
    public void checkTrace(Vector trace) {
        for (int i = 0; i < trace.size(); i++) {
            EntityLink link = (EntityLink) trace.get(i);
            if (i == 0) {
                checkRouter(link.n1);
            }
            checkRouter(link.n2);
        }
    }

    /**
     *  remove deleted IPs from the cache
     */
    private void cleanupIpGroups() {
        for (int gi = 0; gi < ipGroups.size(); gi++) {
            Vector group = (Vector) ipGroups.get(gi);
            for (int ri = 0; ri < group.size(); ri++) {
                rcNode r = (rcNode) group.get(ri);
                if (!knownRouters.contains(r) && !knownFarms.contains(r)) {
                    group.remove(ri);
                    ri--;
                }
            }
            if (group.size() == 0) {
                ipGroups.remove(gi);
                gi--;
            }
        }
    }

    /**
     * build a query and send it to the ipid service to
     * get information about the new routers.
     * 
     * this should be called after checkTrace(..), but asynchronously
     */
    private void resolveNewRouters() {
        if (newRouters.size() == 0) {
            return;
        }
        Vector temp = new Vector();
        synchronized (newRouters) {
            temp.addAll(newRouters);
            newRouters.clear();
        }

        if ((ipidQuery == null) || !ipidQuery.startsWith("http")) {
            return; // skip resolving ipid data
        }

        StringBuilder query = new StringBuilder(ipidQuery + "?0");
        int count = 0;
        int initialLen = query.length();
        for (int i = 0; i < temp.size(); i++) {
            rcNode r = (rcNode) temp.get(i);
            query.append("+" + r.IPaddress);
            count++;
            if (count > 40) {
                processIpidQuery(query.toString());
                count = 0;
                query.setLength(initialLen);
            }
        }
        processIpidQuery(query.toString());
        temp.clear();
    }

    /**
     * check for updated data about routers. 
     * If full==true, perform a full update, discarding previous information
     * 
     * this should be called with (false) from time to time, and with (true)
     * quite rarely 
     */
    private void updateRouterData(boolean full) {
        if ((knownFarms.size() == 0) || (knownRouters.size() == 0)) {
            return;
        }
        if ((ipidQuery == null) || !ipidQuery.startsWith("http")) {
            return; // skip resolving ipid data
        }
        String query = ipidQuery + "?" + (full ? "1" : lastModifTime);
        processIpidQuery(query);
    }

    /**
     * process a Ipid Query, and regroup routers as needed. 
     */
    private void processIpidQuery(String query) {
        try {
            //logger.log(Level.INFO, "IPID Q: "+query);
            URLConnection uconn = new URL(query).openConnection();
            uconn.setDefaultUseCaches(false);
            uconn.setUseCaches(false);
            BufferedReader br = new BufferedReader(new InputStreamReader(uconn.getInputStream()));
            String line = null;
            lastModifTime = br.readLine(); // this is always there
            //logger.log(Level.INFO, "IPID T: "+lastModifTime);
            while ((line = br.readLine()) != null) {
                //logger.log(Level.INFO, "IPID A: "+line);
                groupRouters(getRcNodesFromIPs(line));
            }
            br.close();
            removeEmptyIpGroups();
            //dumpIpGroups();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error processing Ipid query " + query, ex);
        }
    }

    private void dumpIpGroups() {
        System.out.println("######## DUMPING GROUPS");
        for (int i = 0; i < ipGroups.size(); i++) {
            Vector group = (Vector) ipGroups.get(i);
            for (int j = 0; j < group.size(); j++) {
                rcNode r = (rcNode) group.get(j);
                System.out.print(r.UnitName + "=" + r.IPaddress + " ");
            }
            System.out.println();
        }
    }

    /**
     * remove all groups in ipGroups that are empty. 
     * This is a lighter version for cleanupIpGroups()
     */
    private void removeEmptyIpGroups() {
        for (int i = 0; i < ipGroups.size(); i++) {
            Vector group = (Vector) ipGroups.get(i);
            if (group.size() == 0) {
                ipGroups.remove(i);
                i--;
            }
        }
    }

    /**
     * make sure that all rcNodes in 'group' are in the same 
     * ipGroup.
     */
    private void groupRouters(Vector group) {
        int indexes[] = new int[group.size()];
        int finIdx = -1;
        int idx = -1;
        // find the group index in ipGroups for each of the rcNodes in 'group'
        for (int i = 0; i < group.size(); i++) {
            rcNode r = (rcNode) group.get(i);
            idx = indexes[i] = findRouterGroup(r);
            if (idx != -1) {
                finIdx = idx;
            }
        }
        // if none of the rcNodes was found, add all these as a new group
        if (finIdx == -1) {
            ipGroups.add(group);
        } else {
            // else, set all rcNodes in group in the same ipGroup 
            Vector finGroup = (Vector) ipGroups.get(finIdx);
            for (int i = 0; i < group.size(); i++) {
                if (indexes[i] != finIdx) {
                    // if node i from group wasn't found in finIdx ipGroup, add it there
                    rcNode r = (rcNode) group.get(i);
                    // if node i was found in other group, delete it from there
                    if (indexes[i] != -1) {
                        Vector iniGroup = (Vector) ipGroups.get(indexes[i]);
                        iniGroup.remove(r);
                    }
                    finGroup.add(r);
                }
            }
            // now check for nodes in finIdx that aren't in group and put them separately
            Vector sep = new Vector();
            for (int i = 0; i < finGroup.size(); i++) {
                if (!group.contains(finGroup.get(i))) {
                    sep.add(finGroup.get(i));
                    finGroup.remove(i);
                    i--;
                }
            }
            if (sep.size() > 0) {
                ipGroups.add(sep);
            }
        }
    }

    /** 
     * return a Vector containing valid (existing in knownRouters) rcNodes 
     * from knownRouters and knownFarms 
     */
    private Vector getRcNodesFromIPs(String list) {
        Vector rez = new Vector();
        StringTokenizer stk = new StringTokenizer(list, " ");
        while (stk.hasMoreTokens()) {
            String ip = stk.nextToken();
            rcNode r = NetTopologyAnalyzer.findRCnodeByIP(knownRouters, ip);
            if (r == null) {
                r = NetTopologyAnalyzer.findRCnodeByIP(knownFarms, ip);
            }
            if (r != null) {
                rez.add(r);
            }
        }
        return rez;
    }

    /**
     * this should be called only once from NetTopologyAnalizer
     */
    public void setRoutersAndFarms(Vector routers, Vector farms) {
        knownRouters = routers;
        knownFarms = farms;
    }

    /**
     * find the group index for this router
     */
    public int findRouterGroup(rcNode router) {
        for (int i = 0; i < ipGroups.size(); i++) {
            Vector group = (Vector) ipGroups.get(i);
            if (group.contains(router)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * for a given node, return other node that represents this one 
     */
    public rcNode getLeaderNode(rcNode node) {
        int index = findRouterGroup(node);
        return index == -1 ? node : getGroupLeader(index);
    }

    /**
     * get the leader for a group, i.e. rcNode that will be
     * drawn instead of the real node 
     */
    public rcNode getGroupLeader(int index) {
        try {
            Vector group = (Vector) ipGroups.get(index);
            return (rcNode) group.get(0);
        } catch (ArrayIndexOutOfBoundsException ex) {
            logger.log(Level.WARNING, "Invalid group", ex);
            return null;
        }
    }

    /**
     * set the leader rcNode for a group, i.e. rcNode that will
     * be drawn instead of others in the same group 
     */
    public void setGroupLeader(int index, rcNode router) {
        try {
            Vector group = (Vector) ipGroups.get(index);
            synchronized (group) {
                group.remove(router);
                group.add(0, router);
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
            logger.log(Level.WARNING, "Invalid group", ex);
        }
    }

}
