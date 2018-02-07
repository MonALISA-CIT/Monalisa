/*
* Created on Aug 10, 2007
* 
* $Id: CompConfigV2.java 7419 2013-10-16 12:56:15Z ramiro $
* 
*/
package lia.Monitor.ClientsFarmProxy;

import java.util.Comparator;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;

/**
 * Hopefully a better version of CompConfig.
 * 
 * @author ramiro
 */
public class CompConfigV2 {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(CompConfigV2.class.getName());

    public static final class MClusterNameComparator implements Comparator<MCluster> {

        @Override
        public int compare(MCluster c1, MCluster c2) {
            return c1.name.compareTo(c2.name);
        }
    }

    public static final MClusterNameComparator clusterNameComparator = new MClusterNameComparator();

    public static final class MNodeNameComparator implements Comparator<MNode> {

        @Override
        public int compare(MNode n1, MNode n2) {
            return n1.name.compareTo(n2.name);
        }
    }

    public static final MNodeNameComparator nodeNameComparator = new MNodeNameComparator();

    private static MNode[] compareParams(final MNode newNode, final MNode oldNode,
            final MFarmClientConfigInfo lastConfMon) {

        if (newNode == null) {
            throw new NullPointerException("compareParams. newNode cannot be null. oldNode: " + oldNode);
        }

        if (oldNode == null) {
            throw new NullPointerException("compareParams. oldNode cannot be null. newNode: " + newNode);
        }

        if (!newNode.name.equals(oldNode.name)) {
            //  assert ... ? or throw exception
            logger.log(Level.WARNING, " [ CompConfigV2 ] { BUGGG??? ?! } compareParams: newNode.name = " + newNode.name
                    + " oldNode.name: " + oldNode.name);
            throw new IllegalArgumentException("compareParams: newNode.name = " + newNode.name + " oldNode.name: "
                    + oldNode.name);
        }

        final String nodeName = newNode.name;

        MNode[] diff = new MNode[2];

        final Vector<String> newParamList = newNode.getParameterList();

        final Vector<String> oldParamList = oldNode.getParameterList();

        int iOldParam = 0;
        int iNewParam = 0;
        final int newPLen = newParamList.size();
        final int oldPLen = oldParamList.size();

        for (; (iNewParam < newPLen) && (iOldParam < oldPLen);) {
            final String oldParam = oldParamList.get(iOldParam);
            final String newParam = newParamList.get(iNewParam);

            final int c = newParam.compareTo(oldParam);

            if (c == 0) {
                iNewParam++;
                iOldParam++;

                continue;
            }

            if (c < 1) {
                // new param
                if (diff[0] == null) {
                    diff[0] = new MNode(nodeName, newNode.ipAddress, null, null);
                }
                diff[0].getParameterList().add(newParam);

                iNewParam++;
                continue;

            }

            // old cluster no longer in the new config
            if (diff[1] == null) {
                diff[1] = new MNode(nodeName, oldNode.ipAddress, null, null);
            }
            diff[1].getParameterList().add(oldParam);

            iOldParam++;

        }// for - oldClusteList

        for (; iNewParam < newPLen; iNewParam++) {
            final String param = newParamList.get(iNewParam);

            // new cluster
            if (diff[0] == null) {
                diff[0] = new MNode(nodeName, newNode.ipAddress, null, null);
            }
            diff[0].getParameterList().add(param);
        }

        for (; iOldParam < oldPLen; iOldParam++) {
            final String param = oldParamList.get(iOldParam);

            // remove param
            if (diff[1] == null) {
                diff[1] = new MNode(nodeName, oldNode.ipAddress, null, null);
            }
            diff[1].getParameterList().add(param);
        }

        return diff;
    } // compareParams

    private static MCluster[] compareNodes(final MCluster newCluster, final MCluster oldCluster,
            final MFarmClientConfigInfo lastConfMon) {

        if (newCluster == null) {
            throw new NullPointerException("compareNodes. newCluster cannot be null. oldCluster: " + oldCluster);
        }

        if (oldCluster == null) {
            throw new NullPointerException("compareNodes. oldCluster cannot be null. newCluster: " + newCluster);
        }

        if (!newCluster.name.equals(oldCluster.name)) {
            //  assert ... ? or throw exception
            logger.log(Level.WARNING, " [ CompConfigV2 ] { BUGGG??? ?! } compareNodes: newCluster.name = "
                    + newCluster.name + " oldCluster.name: " + oldCluster.name);
            throw new IllegalArgumentException("compareNodes: newCluster.name = " + newCluster.name
                    + " oldCluster.name: " + oldCluster.name);
        }

        final String clusterName = newCluster.name;

        MCluster[] diff = new MCluster[2];

        final Vector<MNode> newNodeList = newCluster.getNodes();

        final Vector<MNode> oldNodeList = oldCluster.getNodes();

        int iOldNode = 0;
        int iNewNode = 0;
        final int newNLen = newNodeList.size();
        final int oldNLen = oldNodeList.size();

        //        int cParamsCount = lastConfMon.getParamsCount();

        for (; (iNewNode < newNLen) && (iOldNode < oldNLen);) {
            final MNode oldNode = oldNodeList.get(iOldNode);
            final MNode newNode = newNodeList.get(iNewNode);
            //            cParamsCount += newNode.getParameterList().size();

            final int c = nodeNameComparator.compare(newNode, oldNode);

            if (c == 0) {
                // check for params changes
                final MNode[] diffParams = compareParams(newNode, oldNode, lastConfMon);

                if (diffParams[0] != null) {
                    if (diff[0] == null) {
                        diff[0] = new MCluster(clusterName, null);
                    }

                    diff[0].getNodes().add(diffParams[0]);
                }

                if (diffParams[1] != null) {
                    if (diff[1] == null) {
                        diff[1] = new MCluster(clusterName, null);
                    }

                    diff[1].getNodes().add(diffParams[1]);
                }

                iNewNode++;
                iOldNode++;
                continue;
            }

            if (c < 1) {
                // new node
                if (diff[0] == null) {
                    diff[0] = new MCluster(clusterName, null);
                }
                diff[0].getNodes().add(newNode.newStrippedInstance());

                iNewNode++;

                continue;
            }

            // old node no longer in the new config
            if (diff[1] == null) {
                diff[1] = new MCluster(clusterName, null);
            }
            diff[1].getNodes().add(oldNode.newStrippedInstance());

            iOldNode++;

        }// for

        for (; iNewNode < newNLen; iNewNode++) {
            final MNode newNode = newNodeList.get(iNewNode);

            // new node added
            if (diff[0] == null) {
                diff[0] = new MCluster(clusterName, null);
            }
            diff[0].getNodes().add(newNode.newStrippedInstance());

            final Vector<String> newParamList = newNode.getParameterList();

            //            int pCount = lastConfMon.getParamsCount();
            //            pCount += newParamList.size();
            //            lastConfMon.setParamsCount(pCount);
        }

        for (; iOldNode < oldNLen; iOldNode++) {
            final MNode oldNode = oldNodeList.get(iOldNode);

            // removed node
            if (diff[1] == null) {
                diff[1] = new MCluster(clusterName, null);
            }
            diff[1].getNodes().add(oldNode.newStrippedInstance());
        }

        //        lastConfMon.setParamsCount(cParamsCount);

        return diff;
    } // compareNodes

    /**
     * 
     * @param newConf
     * @param oldConf -
     *            is expected to be always sorted !
     * @param debugInfo
     * @return
     */
    public static MFarm[] compareClusters(final MFarm newConf, final MFarm oldConf,
            final MFarmClientConfigInfo lastConfMon) {

        if (newConf == null) {
            throw new NullPointerException("compareClusters. newConf cannot be null. oldConf: " + oldConf);
        }

        if (oldConf == null) {
            throw new NullPointerException("compareClusters. oldConf cannot be null. newConf: " + newConf);
        }

        if (!newConf.name.equals(oldConf.name)) {
            //  assert ... ? or throw exception
            logger.log(Level.WARNING, " [ CompConfigV2 ] { BUGGG??? ?! } compareClusters: newConf.name = "
                    + newConf.name + " oldConf.name: " + oldConf.name);
            throw new IllegalArgumentException("compareClusters: newConf.name = " + newConf.name + " oldConf.name: "
                    + oldConf.name);
        }

        final String fName = newConf.name;

        final MFarm[] diff = new MFarm[2];

        final Vector<MCluster> newClusterList = newConf.getClusters();

        final Vector<MCluster> oldClusterList = oldConf.getClusters();

        int iOldCluster = 0;
        int iNewCluster = 0;
        final int newCLen = newClusterList.size();
        final int oldCLen = oldClusterList.size();

        //        int nodesCount = 0;

        synchronized (lastConfMon) {

            //            lastConfMon.setParamsCount(0);

            for (; (iNewCluster < newCLen) && (iOldCluster < oldCLen);) {

                final MCluster oldCluster = oldClusterList.get(iOldCluster);
                final MCluster newCluster = newClusterList.get(iNewCluster);
                //                nodesCount += newCluster.getNodes().size();

                final int c = clusterNameComparator.compare(newCluster, oldCluster);

                if (c == 0) {
                    // check for nodes/params changes
                    final MCluster[] diffNodes = compareNodes(newCluster, oldCluster, lastConfMon);

                    if (diffNodes[0] != null) {
                        if (diff[0] == null) {
                            diff[0] = new MFarm(fName);
                        }

                        diff[0].getClusters().add(diffNodes[0]);
                    }

                    if (diffNodes[1] != null) {
                        if (diff[1] == null) {
                            diff[1] = new MFarm(fName);
                        }

                        diff[1].getClusters().add(diffNodes[1]);
                    }

                    iOldCluster++;
                    iNewCluster++;

                    continue;
                }

                if (c < 1) {
                    // new cluster
                    if (diff[0] == null) {
                        diff[0] = new MFarm(fName);
                    }
                    diff[0].getClusters().add(newCluster.newStrippedInstance());

                    final Vector<MNode> newNodeList = newCluster.getNodes();

                    //                    int pCount = lastConfMon.getParamsCount();
                    //
                    //                    for (final MNode nn : newNodeList) {
                    //                        pCount += nn.getParameterList().size();
                    //                    }
                    //
                    //                    lastConfMon.setParamsCount(pCount);

                    iNewCluster++;

                    continue;
                }

                // old cluster no longer in the new config
                if (diff[1] == null) {
                    diff[1] = new MFarm(fName);
                }
                diff[1].getClusters().add(oldCluster.newStrippedInstance());

                iOldCluster++;

            }// for - oldClusteList

            for (; iNewCluster < newCLen; iNewCluster++) {
                final MCluster newCluster = newClusterList.get(iNewCluster);

                // new cluster
                if (diff[0] == null) {
                    diff[0] = new MFarm(fName);
                }
                diff[0].getClusters().add(newCluster.newStrippedInstance());

                final Vector<MNode> newNodeList = newCluster.getNodes();

                //                int pCount = lastConfMon.getParamsCount();
                //
                //                for (final MNode nn : newNodeList) {
                //                    pCount += nn.getParameterList().size();
                //                }
                //
                //                lastConfMon.setParamsCount(pCount);
            }

            for (; iOldCluster < oldCLen; iOldCluster++) {
                final MCluster oldCluster = oldClusterList.get(iOldCluster);

                // remove cluster
                if (diff[1] == null) {
                    diff[1] = new MFarm(fName);
                }
                diff[1].getClusters().add(oldCluster.newStrippedInstance());
            }

            //            lastConfMon.setNodesCount(nodesCount);
        } // end sync

        return diff;

    } // compareClusters

}
