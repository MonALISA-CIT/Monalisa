/*
 * Created on Aug 30, 2007
 * 
 * $Id: MFarmConfigUtils.java 7419 2013-10-16 12:56:15Z ramiro $
 * 
 */
package lia.util;

import java.util.Comparator;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;

/**
 * 
 * Hopefully a better version of CompConfig. It assumes that all the lists for 
 * MCluster/MNode/Params are sorted.
 * 
 * It includes also a method to "dump" a MFarm object
 *  
 * @author ramiro
 * 
 * @since ML 1.8.0
 */
public class MFarmConfigUtils {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(MFarmConfigUtils.class.getName());

    public static final MClusterNameComparator CLUSTER_NAME_COMPARATOR = new MClusterNameComparator();
    public static final MNodeNameComparator NODE_NAME_COMPARATOR = new MNodeNameComparator();

    public static final class MClusterNameComparator implements Comparator {

        @Override
        public int compare(final Object c1, final Object c2) {
            return ((MCluster) c1).name.compareTo(((MCluster) c2).name);
        }
    }

    public static final class MNodeNameComparator implements Comparator {

        @Override
        public int compare(Object n1, Object n2) {
            return ((MNode) n1).name.compareTo(((MNode) n2).name);
        }
    }

    private static MNode[] compareParams(final MNode newNode, final MNode oldNode, final StringBuilder debugInfo) {

        MNode[] diff = new MNode[2];

        final Vector newParamList = newNode.getParameterList();

        final Vector oldParamList = oldNode.getParameterList();

        if (debugInfo != null) {
            debugInfo.append("\n[ compareParams ] for newNode: ").append(newNode).append(" oldNode: ").append(oldNode);
        }

        synchronized (oldParamList) {

            int iOldParam = 0;
            int iNewParam = 0;
            final int newPLen = newParamList.size();

            for (; (iNewParam < newPLen) && (iOldParam < oldParamList.size());) {
                final String oldParam = (String) oldParamList.get(iOldParam);
                final String newParam = (String) newParamList.get(iNewParam);

                final int c = newParam.compareTo(oldParam);

                if (debugInfo != null) {
                    debugInfo.append("\n[ compareParams ] iNewParam=").append(iNewParam).append(", newParam=")
                            .append(newParam).append(" | iOldParam=").append(iOldParam).append(" oldParam=")
                            .append(oldParam).append(" compare = ").append(c);
                }

                if (c == 0) {
                    iNewParam++;
                    iOldParam++;

                    continue;
                }

                if (c < 1) {
                    // new param
                    if (diff[0] == null) {
                        diff[0] = new MNode(newNode.name, newNode.ipAddress, null, null);
                    }
                    diff[0].getParameterList().add(newParam);

                    iNewParam++;

                    oldParamList.add(newParam);
                    iOldParam++;

                    if (debugInfo != null) {
                        debugInfo.append("\n[ compareParams ] NEW PARAM ADDED: iNewParam=").append(iNewParam)
                                .append(", newParam=").append(newParam).append(" | iOldParam=").append(iOldParam)
                                .append(" oldParam=").append(oldParam).append(" compare = ").append(c);
                    }

                    continue;

                }

                // old param no longer in the new config
                if (diff[1] == null) {
                    diff[1] = new MNode(oldNode.name, oldNode.ipAddress, null, null);
                }
                diff[1].getParameterList().add(oldParam);

                oldParamList.remove(iOldParam);
                if (debugInfo != null) {
                    debugInfo.append("\n[ compareParams ] OLD PARAM REMOVED: iNewParam=").append(iNewParam)
                            .append(", newParam=").append(newParam).append(" | iOldParam=").append(iOldParam)
                            .append(" oldParam=").append(oldParam).append(" compare = ").append(c);
                }

            }// for - both idexes

            //check for new params at the end of newParamList
            for (; iNewParam < newPLen; iNewParam++) {
                final String param = (String) newParamList.get(iNewParam);

                // new cluster
                if (diff[0] == null) {
                    diff[0] = new MNode(newNode.name, newNode.ipAddress, null, null);
                }

                diff[0].getParameterList().add(param);

                oldParamList.add(param);
                iOldParam++;

                if (debugInfo != null) {
                    debugInfo.append("\n[ compareParams ] NEW PARAM ADDED at the end: iNewParam=").append(iNewParam)
                            .append(", newParam=").append(param).append(" | iOldParam=").append(iOldParam)
                            .append(" oldParam=").append(oldParamList.get(iOldParam - 1));
                }

            }

            for (; iOldParam < oldParamList.size();) {
                final String param = (String) oldParamList.get(iOldParam);

                // remove cluster
                if (diff[1] == null) {
                    diff[1] = new MNode(oldNode.name, oldNode.ipAddress, null, null);
                }
                diff[1].getParameterList().add(param);
                oldParamList.remove(iOldParam);

                if (debugInfo != null) {
                    debugInfo.append("\n[ compareParams ] OLD PARAM REMOVED at the end iOldParam=")
                            .append(iOldParam - 1).append(" oldParam=");
                }
            }

            return diff;

        }//end sync
    } // compareParams

    private static MCluster[] compareNodes(final MCluster newCluster, final MCluster oldCluster,
            final StringBuilder debugInfo) {
        MCluster[] diff = new MCluster[2];

        final Vector newNodeList = newCluster.getNodes();

        final Vector oldNodeList = oldCluster.getNodes();

        if (debugInfo != null) {
            debugInfo.append("\n[ compareParams ] for newCluster: ").append(newCluster).append(" oldCluster: ")
                    .append(oldCluster);
        }

        synchronized (oldNodeList) {

            int iOldNode = 0;
            int iNewNode = 0;
            final int newNLen = newNodeList.size();

            for (; (iNewNode < newNLen) && (iOldNode < oldNodeList.size());) {
                final MNode oldNode = (MNode) oldNodeList.get(iOldNode);
                final MNode newNode = (MNode) newNodeList.get(iNewNode);

                final int c = NODE_NAME_COMPARATOR.compare(newNode, oldNode);

                if (debugInfo != null) {
                    debugInfo.append("\n[ compareNodes ] iNewNode=").append(iNewNode).append(", newNode=")
                            .append(newNode).append(" | iOldNode=").append(iOldNode).append(" oldNode=")
                            .append(oldNode).append(" compare = ").append(c);
                }

                if (c == 0) {
                    // check for params changes
                    final MNode[] diffParams = compareParams(newNode, oldNode, debugInfo);

                    if (diffParams[0] != null) {
                        if (diff[0] == null) {
                            diff[0] = new MCluster(newCluster.name, null);
                        }

                        diff[0].getNodes().add(diffParams[0]);
                    }

                    if (diffParams[1] != null) {
                        if (diff[1] == null) {
                            diff[1] = new MCluster(oldCluster.name, null);
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
                        diff[0] = new MCluster(newCluster.name, null);
                    }
                    final MNode newNodeToAdd = MNode.fromMNode(newNode, oldCluster);
                    diff[0].getNodes().add(newNodeToAdd);

                    iNewNode++;

                    oldNodeList.add(newNodeToAdd);
                    iOldNode++;

                    if (debugInfo != null) {
                        debugInfo.append("\n[ compareNodes ] NEW NODE ADDED iNewNode=").append(iNewNode)
                                .append(", newNode=").append(newNode).append(" | iOldNode=").append(iOldNode)
                                .append(" oldNode=").append(oldNode).append(" compare = ").append(c);
                    }

                    continue;
                }

                // old node no longer in the new config
                if (diff[1] == null) {
                    diff[1] = new MCluster(oldCluster.name, null);
                }
                diff[1].getNodes().add(new MNode(oldNode.name, oldCluster, oldCluster.getFarm()));

                if (debugInfo != null) {
                    debugInfo.append("\n[ compareNodes ] OLD NODE REMOVED iNewNode=").append(iNewNode)
                            .append(", newNode=").append(newNode).append(" | iOldNode=").append(iOldNode)
                            .append(" oldNode=").append(oldNode).append(" compare = ").append(c);
                }

                oldNodeList.remove(iOldNode);

            }// for

            for (; iNewNode < newNLen; iNewNode++) {
                final MNode newNode = (MNode) newNodeList.get(iNewNode);

                // new node added
                if (diff[0] == null) {
                    diff[0] = new MCluster(newCluster.name, null);
                }
                final MNode newNodeToAdd = MNode.fromMNode(newNode, oldCluster);

                diff[0].getNodes().add(newNodeToAdd);

                oldNodeList.add(newNodeToAdd);

                iOldNode++;

                if (debugInfo != null) {
                    debugInfo.append("\n[ compareNodes ] NEW NODE ADDED at the end iNewNode=").append(iNewNode)
                            .append(", newNode=").append(newNode).append(" | iOldNode=").append(iOldNode)
                            .append(" oldNode=").append(oldNodeList.get(iOldNode - 1));
                }
            }

            for (; iOldNode < oldNodeList.size();) {
                final MNode oldNode = (MNode) oldNodeList.get(iOldNode);

                // removed node
                if (diff[1] == null) {
                    diff[1] = new MCluster(oldNode.name, null);
                }
                diff[1].getNodes().add(new MNode(oldNode.name, oldCluster, oldCluster.getFarm()));
                oldNodeList.remove(iOldNode);

                if (debugInfo != null) {
                    debugInfo.append("\n[ compareNodes ] OLD NODE REMOVED at the end iOldNode=").append(iOldNode)
                            .append(" oldNode=").append(oldNode);
                }
            }

            return diff;

        }//end sync

    } // compareNodes

    /**
     * 
     * @param newConf - is expected to be always sorted
     * @param oldConf - is expected to be always sorted !
     * @param debugInfo
     * @return
     */
    public static MFarm[] compareAndUpdateClusters(final MFarm newConf, final MFarm oldConf,
            final StringBuilder debugInfo) {
        MFarm[] diff = new MFarm[2];

        final Vector newClusterList = newConf.getClusters();
        final Vector oldClusterList = oldConf.getClusters();

        synchronized (oldClusterList) {

            String fName = newConf.name;
            if (fName == null) {
                fName = oldConf.name;
            }

            if (fName == null) {
                logger.log(Level.WARNING, " [ Exception Conf ] FarmName is null in compareClusters");
                return diff;
            }

            //iterator for oldClusterList
            int iOldCluster = 0;

            //iterator for newClusterList
            int iNewCluster = 0;
            final int newCLen = newClusterList.size();

            int nodesCount = 0;

            for (; (iNewCluster < newCLen) && (iOldCluster < oldClusterList.size());) {

                final MCluster oldCluster = (MCluster) oldClusterList.get(iOldCluster);
                final MCluster newCluster = (MCluster) newClusterList.get(iNewCluster);
                nodesCount += newCluster.getNodes().size();

                final int c = CLUSTER_NAME_COMPARATOR.compare(newCluster, oldCluster);

                if (debugInfo != null) {
                    debugInfo.append("\n[ compareClusters ] iNewCluster=").append(iNewCluster).append(", newCluster=")
                            .append(newCluster).append(" | iOldCluster=").append(iOldCluster).append(" oldCluster=")
                            .append(oldCluster).append(" compare = ").append(c);
                }

                if (c == 0) {
                    // check for nodes/params changes
                    final MCluster[] diffNodes = compareNodes(newCluster, oldCluster, debugInfo);

                    if (diffNodes[0] != null) {
                        if (diff[0] == null) {
                            diff[0] = new MFarm(newConf.name);
                        }

                        diff[0].getClusters().add(diffNodes[0]);
                    }

                    if (diffNodes[1] != null) {
                        if (diff[1] == null) {
                            diff[1] = new MFarm(oldConf.name);
                        }

                        diff[1].getClusters().add(diffNodes[1]);
                    }

                    iOldCluster++;
                    iNewCluster++;

                    continue;
                }

                if (c < 1) {
                    // new cluster; add it to oldClusterList
                    if (diff[0] == null) {
                        diff[0] = new MFarm(newConf.name);
                    }

                    final MCluster newClusterToAdd = MCluster.fromMCluster(newCluster, oldConf);

                    diff[0].getClusters().add(newClusterToAdd);

                    iNewCluster++;

                    //add the new cluster
                    oldClusterList.add(newClusterToAdd);
                    iOldCluster++;

                    if (debugInfo != null) {
                        debugInfo.append("\n[ compareClusters ] NEW CLUSTER ADDED iNewCluster=").append(iNewCluster)
                                .append(", newCluster=").append(newCluster).append(" | iOldCluster=")
                                .append(iOldCluster).append(" oldCluster=").append(oldCluster).append(" compare = ")
                                .append(c);
                    }

                    continue;
                }

                // old cluster no longer in the new config
                if (diff[1] == null) {
                    diff[1] = new MFarm(oldConf.name);
                }
                diff[1].getClusters().add(new MCluster(oldCluster.name, oldConf));

                //remove the oldCluster; do not increment the old contor
                oldClusterList.remove(iOldCluster);

                if (debugInfo != null) {
                    debugInfo.append("\n[ compareClusters ] OLD CLUSTER REMOVED iNewCluster=").append(iNewCluster)
                            .append(", newCluster=").append(newCluster).append(" | iOldCluster=").append(iOldCluster)
                            .append(" oldCluster=").append(oldCluster).append(" compare = ").append(c);
                }

            }// for - oldClusteList

            for (; iNewCluster < newCLen; iNewCluster++) {
                final MCluster newCluster = (MCluster) newClusterList.get(iNewCluster);

                // new cluster
                if (diff[0] == null) {
                    diff[0] = new MFarm(newConf.name);
                }
                final MCluster newClusterToAdd = MCluster.fromMCluster(newCluster, oldConf);

                diff[0].getClusters().add(newClusterToAdd);

                oldClusterList.add(newClusterToAdd);
                iOldCluster++;

                if (debugInfo != null) {
                    debugInfo.append("\n[ compareClusters ] NEW CLUSTER ADDED at the end iNewCluster=")
                            .append(iNewCluster).append(", newCluster=").append(newCluster).append(" | iOldCluster=")
                            .append(iOldCluster).append(" oldCluster=").append(oldClusterList.get(iOldCluster - 1));
                }

            }

            for (; iOldCluster < oldClusterList.size();) {
                final MCluster oldCluster = (MCluster) oldClusterList.get(iOldCluster);

                // remove cluster
                if (diff[1] == null) {
                    diff[1] = new MFarm(newConf.name);
                }
                diff[1].getClusters().add(oldCluster);

                oldClusterList.remove(iOldCluster);

                if (debugInfo != null) {
                    debugInfo.append("\n[ compareClusters ] OLD CLUSTER REMOVED at the end iOldCluster=")
                            .append(iOldCluster).append(" oldCluster=").append(oldCluster);
                }
            }

            return diff;

        }//end sync newClusterList
    } // compareClusters

    /**
     * 
     * Returns a "nice" indented text-based MFarm view
     * 
     * @param mfarm
     * @return
     */
    public static final String getMFarmDump(final MFarm mfarm) {
        if (mfarm == null) {
            return "null";
        }

        StringBuilder sb = new StringBuilder(1024);
        sb.append("\nMFarm: ").append(mfarm.name);
        final Vector clusterList = mfarm.getClusters();
        synchronized (clusterList) {
            final int clSize = clusterList.size();
            for (int iClus = 0; iClus < clSize; iClus++) {
                final MCluster cluster = (MCluster) clusterList.get(iClus);

                sb.append("\n|--");
                if (cluster == null) {
                    sb.append(" ( ******************* null cluster ********************** )");
                    continue;
                }

                if (cluster.name == null) {
                    sb.append(" ( ********************** null cluster name ************* )");
                    continue;
                }

                sb.append(cluster.name);

                if (cluster.getNodes() == null) {
                    sb.append("( null node list )");
                    continue;
                }

                final Vector nodeList = cluster.getNodes();
                synchronized (nodeList) {
                    final int nlSize = nodeList.size();
                    for (int iNode = 0; iNode < nlSize; iNode++) {
                        final MNode node = (MNode) nodeList.get(iNode);
                        sb.append("\n|\t|--");
                        if (node == null) {
                            sb.append(" ( ************************ null node **********************)");
                            continue;
                        }

                        if (node.name == null) {
                            sb.append(" ( ********************** null node name ************* )");
                            continue;
                        }

                        sb.append(node.name);

                        if (node.getParameterList() == null) {
                            sb.append("( null parameter list )");
                            continue;
                        }
                        final Vector paramList = node.getParameterList();
                        synchronized (paramList) {
                            final int plSize = paramList.size();
                            for (int iParam = 0; iParam < plSize; iParam++) {
                                sb.append("\n|\t|\t|--").append(paramList.get(iParam));
                            }
                        }
                    }
                }//end sync nodeList
            }//end for - cluster
        }//end sync - clusterList

        return sb.toString();
    }

}
