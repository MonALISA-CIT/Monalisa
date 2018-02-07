/*
 * $Id: MFarmHelper.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.tcpClient;

import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonMessageClientsProxy;
import lia.util.MFarmConfigUtils;

/**
 * 
 * 
 * @author mluc
 * @author ramiro
 * 
 */
public final class MFarmHelper {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(MFarmHelper.class.getName());

    public static class ConfigCountChanges {

        int nClustersUnChanged;

        int nClustersChanged;

        int nNodesUnChanged;

        int nNodesChanged;

        int nParamsUnChanged;

        int nParamsChanged;

        public ConfigCountChanges() {
            nClustersChanged = 0;
            nClustersUnChanged = 0;
            nNodesChanged = 0;
            nNodesUnChanged = 0;
            nParamsChanged = 0;
            nParamsUnChanged = 0;
        }
    }

    public static ConfigCountChanges newCounter() {
        return new ConfigCountChanges();
    }

    public static void farmSumReport(MFarm farm) {
        if (farm == null) {
            return;
        }
        Vector v = farm.getClusters(), v1, vN, vPL;
        int nrClusters = v != null ? v.size() : 0;
        vN = farm.getNodes();
        int nrNodes = vN != null ? vN.size() : 0;
        vPL = farm.getParameterList();
        int nrParams = vPL != null ? vPL.size() : 0;
        int tparams = 0;
        for (int i = 0; i < v.size(); i++) {
            v1 = ((MCluster) v.get(i)).getNodes();
            if (v1 != null) {
                for (int j = 0; j < v1.size(); j++) {
                    tparams += ((MNode) v1.get(j)).getParameterList().size();
                }
            }
        }
        ;
        System.out.println("farm " + farm.name + " " + nrClusters + " cluster" + (nrClusters != 1 ? "s" : "") + "<br>"
                + nrNodes + " node" + (nrNodes != 1 ? "s" : "") + "<br>" + nrParams + " unique param"
                + (nrParams != 1 ? "s" : "") + "<br>" + tparams + " total param" + (tparams != 1 ? "s" : ""));
    }

    /**
     * reports debug info for configuration messages: updates to each config by comparing the old config with the new
     * one; treats also the diff configs
     * 
     * @param oldMsg
     * @param msg
     */
    public static void configReport(MonMessageClientsProxy oldMsg, MonMessageClientsProxy msg) {
        if ((oldMsg == null) && (msg.result instanceof MFarm)) {
            MFarm crtFarm = (MFarm) msg.result;
            // if ( crtFarm.name.equals("CIT_CMS_T2") ) {
            System.out.println("First configuration for: " + crtFarm.name);
            listClusters(crtFarm, "CRT");
            // }
            return;
        }
        /**
         * first check is to be sure that we're not processing the same message twice, as it is sent once more by
         * addFarmClient.
         */
        boolean bNewMessage = true;
        if ((oldMsg != null) && (oldMsg.result == msg.result)) {
            bNewMessage = false;
        }
        if (bNewMessage /* && oldMsg != null && oldMsg.result!=null */) {
            MFarm oldFarm = (oldMsg != null ? (MFarm) oldMsg.result : null);
            String sFarmName = (oldFarm != null ? oldFarm.name : "" + msg.farmID);
            // logger.warning("Configuration change for:
            // "+crtFarm.name);
            // if ( !sFarmName.equals("CIT_CMS_T2") )
            // return;
            System.out.println("Configuration update for: " + sFarmName);
            if (msg.result instanceof MFarm) {
                MFarm newFarm = (MFarm) msg.result;
                ConfigCountChanges counter = newCounter();
                if (oldFarm == null) {
                    listClusters(newFarm, "NEW");
                } else if (!compareClusters(newFarm, oldFarm, counter)) {
                    System.out.println("no chages for " + sFarmName + "; " + counter.nClustersUnChanged + " clusters "
                            + counter.nNodesUnChanged + " nodes " + counter.nParamsUnChanged + " params");
                } else {
                    System.out.println(sFarmName + " has\n clusters: " + counter.nClustersChanged + " changed and "
                            + counter.nClustersUnChanged + " unchanged \n" + " nodes: " + counter.nNodesChanged
                            + " changed and " + counter.nNodesUnChanged + " unchanged \n" + " params: "
                            + counter.nParamsChanged + " changed and " + counter.nParamsUnChanged + " unchanged");
                }
            } else if ((msg.result instanceof MFarm[]) && (((MFarm[]) msg.result).length == 2)) {
                System.out.println(sFarmName + " received diff config:");

                listClusters(((MFarm[]) msg.result)[0], "ADD");
                listClusters(((MFarm[]) msg.result)[1], "REMOVE");
            }
        }
    }

    public static void listClusters(MFarm f, String prefix) {
        if ((f != null) && (f.getClusters() != null)) {
            for (int i = 0; i < f.getClusters().size(); i++) {
                MCluster c = f.getClusters().get(i);
                if ((c.getNodes() == null) || (c.getNodes().size() == 0)) {
                    System.out.println(prefix + " cluster " + c.name);
                } else {
                    listNodes(c, prefix);
                }
            }
        }
    }

    public static void listNodes(MCluster c, String prefix) {
        if ((c != null) && (c.getNodes() != null)) {
            for (int i = 0; i < c.getNodes().size(); i++) {
                MNode n = c.getNodes().get(i);
                if ((n.getParameterList() == null) || (n.getParameterList().size() == 0)) {
                    System.out.println(prefix + " node " + n.name + " in cluster " + c.name);
                } else {
                    listParams(n, prefix);
                }
            }
        }
    }

    public static void listParams(MNode n, String prefix) {
        if ((n != null) && (n.getParameterList() != null)) {
            for (int i = 0; i < n.getParameterList().size(); i++) {
                String s = n.getParameterList().get(i);
                System.out.println(prefix + " param " + s + " in node " + n.name
                        + (n.cluster != null ? " in cluster " + n.cluster.name : ""));
            }
        }
    }

    /** helper for config changes debugging */
    public static boolean compareClusters(final MFarm fn, final MFarm fo, ConfigCountChanges counter) {
        if ((fo == null) || (fn == null)) {
            return false;
        }

        boolean bChange = false;

        for (int i = 0; i < fo.getClusters().size(); i++) {
            MCluster oClus = fo.getClusters().get(i);
            if (oClus == null) {
                continue;
            }
            MCluster nClus = fn.getCluster(oClus.name);

            if (nClus == null) {
                System.out.println("REMOVED cluster " + oClus.name);
                counter.nClustersChanged++;
                bChange = true;
            } else {
                counter.nClustersUnChanged++;
                bChange |= compareNodes(nClus, oClus, counter);
            }
        }

        for (int i = 0; i < fn.getClusters().size(); i++) {
            MCluster nClus = fn.getClusters().get(i);
            if (nClus == null) {
                continue;
            }
            MCluster oClus = fo.getCluster(nClus.name);

            if (oClus == null) {
                System.out.println("ADDED cluster " + nClus.name);
                counter.nClustersChanged++;
                bChange = true;
            }
        }
        return bChange;
    }

    /**
     * helper for config changes debugging
     * 
     * @param counter
     */
    public static boolean compareNodes(final MCluster cn, final MCluster co, ConfigCountChanges counter) {

        boolean bChange = false;

        for (int i = 0; i < co.getNodes().size(); i++) {
            final MNode oNode = co.getNodes().get(i);
            if (oNode == null) {
                continue;
            }
            final MNode nNode = cn.getNode(oNode.name);

            if (nNode == null) {
                System.out.println("REMOVED node " + oNode.name + " in cluster " + co.name);
                counter.nNodesChanged++;
                bChange = true;
            } else {
                counter.nNodesUnChanged++;
                bChange |= compareParams(nNode, oNode, counter);
            }
        }
        for (int i = 0; i < cn.getNodes().size(); i++) {
            MNode nNode = cn.getNodes().get(i);
            if (nNode == null) {
                continue;
            }
            MNode oNode = co.getNode(nNode.name);

            if (oNode != null) {
                System.out.println("ADDED node " + nNode.name + " in cluster " + cn.name);
                bChange = true;
                counter.nNodesChanged++;
            }
        }
        return bChange;
    }

    /**
     * helper for config changes debugging
     * 
     * @param counter
     */
    public static boolean compareParams(final MNode nn, final MNode no, ConfigCountChanges counter) {

        boolean bChange = false;

        for (int i = 0; i < no.getParameterList().size(); i++) {
            String param = no.getParameterList().get(i);
            if (param == null) {
                continue;
            }
            if (!nn.getParameterList().contains(param)) {
                System.out.println("REMOVED param " + param + " in node " + no.name + " in cluster " + no.cluster.name);
                bChange = true;
                counter.nParamsChanged++;
            } else {
                counter.nParamsUnChanged++;
            }
        }

        for (int i = 0; i < nn.getParameterList().size(); i++) {
            String param = nn.getParameterList().get(i);
            if (param == null) {
                continue;
            }

            if (!no.getParameterList().contains(param)) {
                System.out.println("ADDED param " + param + " in node " + nn.name + " in cluster " + nn.cluster.name);
                bChange = true;
                counter.nParamsChanged++;
            }
        }
        return bChange;
    }

    /**
     * removes all clusters present in remFarm from crtFarm after previously all nodes have been removed from clusters
     * that appear in both farms
     * 
     * @param crtFarm
     * @param remFarm
     */
    public static void removeClusters(final MFarm crtFarm, final MFarm remFarm) {
        if (remFarm == null) {
            return;
        }
        MCluster crtClus, remClus;
        if (remFarm.getClusters() == null) {
            return;// nothin' to remove
        }
        if (crtFarm.getClusters() == null) {
            return;// nothin' here to be removed
        }

        for (Object element : remFarm.getClusters()) {
            remClus = (MCluster) element;

            if ((remClus != null) && (remClus.name != null)) {

                crtClus = crtFarm.getCluster(remClus.name);

                if (crtClus == null) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, " [ MFarmHelper ] [ removeClusters ] Unable to find cluster: "
                                + remClus.name);
                    }
                    continue; //already removed ....
                }
                // initially zero
                int nNodesCount = (remClus.getNodes() != null ? remClus.getNodes().size() : 0);
                // if I have reason to try and remove nodes... do it
                if ((crtClus.getNodes() != null) && (crtClus.getNodes().size() > 0) && (nNodesCount > 0)) {
                    removeNodes(crtClus, remClus);
                }
                // remove a cluster only if a remove cluster is received with no nodes,
                // no matter that the current cluster has no nodes left
                if (nNodesCount == 0) {
                    if (!crtFarm.removeCluster(crtClus)) {
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, " [ MFarmHelper ] [ removeCluters ] Unable to remove cluster: "
                                    + crtClus);
                        }
                    }
                }
            }
        }

        // TODO update farm's other infos
        crtFarm.getParameterList();
        crtFarm.getModuleList();
        if ((remFarm.getAvModules() != null) && (crtFarm.getAvModules() != null)) {
            String oAM, nAM;
            for (int i = 0; i < crtFarm.getAvModules().size(); i++) {
                oAM = crtFarm.getAvModules().get(i);
                if (oAM == null) {
                    continue;
                }
                for (int j = 0; j < remFarm.getAvModules().size(); j++) {
                    nAM = remFarm.getAvModules().get(j);
                    if (nAM == null) {
                        continue;
                    }
                    if (oAM.equals(nAM)) {
                        crtFarm.getAvModules().remove(i);
                        // remove also from rem because this param will never appear again
                        remFarm.getAvModules().remove(j);
                        // decrement i because one element was removed from i position, so check again this position
                        i--;
                        break;
                    }
                }
            }
        }
        ;

    }

    /**
     * removes all nodes present in remCluster from crtCluster after previously all params have been removed from nodes
     * that appear in both clusters
     * 
     * @param crtCluster
     * @param remCluster
     */
    public static void removeNodes(final MCluster crtCluster, final MCluster remCluster) {
        // new version
        for (final MNode remNode : remCluster.getNodes()) {
            if ((remNode != null) && (remNode.name != null)) {
                final MNode crtNode = crtCluster.getNode(remNode.name);
                if (crtNode == null) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, " [ MFarmHelper ] [ removeNodes ] Unable to find node: " + remNode.name
                                + " in cluster: " + crtCluster);
                    }
                    continue;
                }

                if ((remNode.getParameterList() == null) || (remNode.getParameterList().size() == 0)) {
                    crtCluster.removeNode(crtNode);
                } else {
                    removeParams(crtNode, remNode);
                }
            }
        }

        // TODO update cluster's other params
        crtCluster.updateModulesAndParameters();
    }

    /**
     * removes all parameters present in remNode from crtNode
     * 
     * @param crtNode
     * @param remNode
     */
    public static void removeParams(final MNode crtNode, final MNode remNode) {

        if ((remNode.getParameterList() != null) && (crtNode.getParameterList() != null)) {
            String crtParam, remParam;
            // new
            for (Object element : remNode.getParameterList()) {
                remParam = (String) element;
                if (!crtNode.getParameterList().remove(remParam)) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, " [ MFarmHelper ] [ removeParams ] The param " + remParam
                                + " for node: " + remNode + " cluster: " + remNode.getCluster()
                                + " was not found in the current config");
                    }
                }
            }
        }

        // TODO remove other params: modules...
        if ((remNode.moduleList != null) && (crtNode.moduleList != null)) {
            String oModule, nModule;
            for (int i = 0; i < crtNode.moduleList.size(); i++) {
                oModule = crtNode.moduleList.get(i);
                if (oModule == null) {
                    continue;
                }
                for (int j = 0; j < remNode.moduleList.size(); j++) {
                    nModule = remNode.moduleList.get(j);
                    if (nModule == null) {
                        continue;
                    }
                    if (oModule.equals(nModule)) {
                        crtNode.moduleList.remove(i);
                        // remove also from rem node because this param will never appear again
                        remNode.moduleList.remove(j);
                        // decrement i because one element was removed from i position, so check again this position
                        i--;
                        break;
                    }
                }
            }
        }
        ;
    }

    /**
     * adds all clusters present in addFarm to crtFarm it checks if those clusters are already there first, and then
     * updates only nodes
     * 
     * @param crtFarm
     * @param addFarm
     */
    public static void addClusters(final MFarm crtFarm, final MFarm addFarm) {
        if (addFarm == null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINE, " [ MFarmHelper ] Null addFarm param in addClusters ... ignoring crtFarm: "
                        + MFarmConfigUtils.getMFarmDump(crtFarm));
            }
            return;
        }

        MCluster crtClus, addClus;
        if (addFarm.getClusters() == null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINE,
                        " [ MFarmHelper ] Null list of clusters for addFarm in addClusters ... ignoring crtFarm: "
                                + MFarmConfigUtils.getMFarmDump(crtFarm));
            }
            return;
        }

        for (int j = 0; j < addFarm.getClusters().size(); j++) {
            addClus = addFarm.getClusters().get(j);
            if ((addClus == null) || (addClus.name == null)) {
                continue;
            }
            crtClus = crtFarm.getCluster(addClus.name);

            if (crtClus == null) {
                crtFarm.addClusterIfAbsent(translateCluster(addClus, crtFarm));
            } else /* if ( crtClus!=null ) */{// not really needed the extra validation
                addNodes(crtClus, addClus);
            }
        }
        ;

        // TODO recompute farm params
        crtFarm.getParameterList();
        crtFarm.getModuleList();
        if (addFarm.getAvModules() != null) {
            String addAM, crtAM;
            boolean bExists;
            for (int i = 0; i < addFarm.getAvModules().size(); i++) {
                addAM = addFarm.getAvModules().get(i);
                if (addAM == null) {
                    continue;// should not insert null values
                }
                bExists = false;
                if (crtFarm.getAvModules() != null) {
                    for (int j = 0; j < crtFarm.getAvModules().size(); j++) {
                        crtAM = crtFarm.getAvModules().get(j);
                        if (crtAM == null) {
                            continue;
                        }
                        if (addAM.equals(crtAM)) {
                            bExists = true;
                            break;
                        }
                    }
                }
                if (!bExists) {
                    if (crtFarm.getAvModules() == null) {
                        crtFarm.setAvModules(new Vector());
                    }
                    crtFarm.getAvModules().add(addAM);
                }
            }
        }
    }

    /**
     * adds nodes to cluster crt from add Cluster, and, if node already present, updates params list
     * 
     * @param crtCluster
     * @param addCluster
     */
    public static void addNodes(final MCluster crtCluster, final MCluster addCluster) {
        MNode crtNode, addNode;

        if (addCluster.getNodes() == null) {
            return;
        }

        boolean shouldRecompute = false;

        for (int j = 0; j < addCluster.getNodes().size(); j++) {
            addNode = addCluster.getNodes().get(j);

            if ((addNode == null) || (addNode.name == null)) {
                continue;
            }

            crtNode = crtCluster.getNode(addNode.name);

            if (crtNode == null) {
                addNode.cluster = crtCluster;
                addNode.farm = crtCluster.getFarm();
                crtCluster.addNodeIfAbsent(addNode);
            } else /* if ( crtNode!=null ) */{// not really needed the extra validation
                addParams(crtNode, addNode);
            }

            shouldRecompute = true;
        }

        if (shouldRecompute) {
            // TODO recompute cluster params
            crtCluster.updateModulesAndParameters();
        }
    }

    /**
     * adds new parameters from add node to crt node
     * 
     * @param crtNode
     * @param addNode
     */
    public static void addParams(final MNode crtNode, final MNode addNode) {
        if ((addNode.getParameterList() == null) && (addNode.moduleList == null)) {
            return;
        }

        if (addNode.getParameterList() != null) {
            String addParam;

            for (int i = 0; i < addNode.getParameterList().size(); i++) {
                addParam = addNode.getParameterList().get(i);
                if (addParam == null) {
                    continue;
                }

                crtNode.addParamIfAbsent(addParam);
            }
        }

        if (addNode.moduleList != null) {
            String addModule, crtModule;
            boolean bExists;

            for (int i = 0; i < addNode.moduleList.size(); i++) {
                addModule = addNode.moduleList.get(i);
                if (addModule == null) {
                    continue; // skip null elements
                }

                bExists = false;
                if (crtNode.moduleList != null) {
                    for (int j = 0; j < crtNode.moduleList.size(); j++) {
                        crtModule = crtNode.moduleList.get(j);
                        if (addModule.equals(crtModule)) {
                            bExists = true;
                            break;
                        }
                    }
                }

                if (!bExists) {
                    if (crtNode.moduleList == null) {
                        crtNode.moduleList = new Vector<String>();
                    }

                    crtNode.moduleList.add(addModule);
                }
            }
        }
    }

    /**
     * translates a given cluster, changing only the parent farm
     * 
     * @param crtCluster
     * @param crtFarm
     * @return newCluster
     */
    public static MCluster translateCluster(MCluster crtCluster, MFarm crtFarm) {
        final MCluster newCluster = new MCluster(crtCluster.getName(), crtFarm);
        newCluster.externalModule = crtCluster.externalModule;
        newCluster.externalNode = crtCluster.externalNode;
        newCluster.externalParam = crtCluster.externalParam;
        newCluster.getNodes().addAll(crtCluster.getNodes());
        if (newCluster.getNodes() != null) {
            for (int i = 0; i < newCluster.getNodes().size(); i++) {
                final MNode node = newCluster.getNodes().get(i);
                if (node == null) {
                    continue;
                }
                node.cluster = newCluster;
                node.farm = crtFarm;
            }
        }

        newCluster.updateModulesAndParameters();

        return newCluster;
    }

}
