/*
 * $Id: CompConfig.java 6865 2010-10-10 10:03:16Z ramiro $
 */
package lia.Monitor.ClientsFarmProxy;

import java.util.TreeMap;
import java.util.Vector;

import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;

/**
 * 
 * @author mickyt
 */
public class CompConfig {
    
    public static MNode[] compareParams(final MNode nn, final MNode no) {
        return compareParams(nn, no, null);
    }
    
    public static MNode[] compareParams(final MNode nn, final MNode no, final StringBuilder debugInfo) {

        MNode[] diff = new MNode[2];

        diff[0] = null;
        diff[1] = null;

        TreeMap<String, String> sortedNewParams = new TreeMap<String, String>();
        final Vector<String> newParamList = nn.getParameterList();
        for (final String param: newParamList) {
            if(param != null) {
                sortedNewParams.put(param, param);
            } // if
        } // for

        TreeMap<String, String> sortedOldParams = new TreeMap<String, String>();
        final Vector<String> oldParamList = no.getParameterList();
        for (final String param: oldParamList) {
            if(param != null) {
                sortedOldParams.put(param, param);
            } // if
        } // for

        for (final String param: oldParamList) {
            if (!sortedNewParams.containsKey(param)) {
                // if not created, create it
                if (diff[1] == null) {
                    diff[1] = new MNode (nn.name, nn.ipAddress, null, null);
                } // if	
                if(debugInfo != null) {
                    debugInfo.append("\nREMOVED param: ").append(param).append(" for node ").append(nn.name);
                }
                diff[1].getParameterList().add(param);
            } // if 

        } // for

        for (final String param: newParamList) {
            if (!sortedOldParams.containsKey(param)) {
                if(debugInfo != null) {
                    debugInfo.append("\nADDED param: ").append(param).append(" for node ").append(nn.name);
                }
                // if not created, create it
                if (diff[0] == null) {
                    diff[0] = new MNode (nn.name, nn.ipAddress, null, null);
                } // if
                diff[0].getParameterList().add(param);
            } // if
        } // for

        return diff ;
    } // compareParams

    public static MCluster[] compareNodes(final MCluster cn, final MCluster co) {
        return compareNodes(cn, co, null);
    }
    
    public static MCluster[] compareNodes(final MCluster cn, final MCluster co, final StringBuilder debugInfo) {

        MCluster[] diff = new MCluster[2];

        diff[0] = null;
        diff[1] = null;

        TreeMap<String, MNode> sortedNewNodes = new TreeMap<String, MNode>();
        final Vector<MNode> newNodeList = cn.getNodes();
        for (final MNode node: newNodeList) {
            if(node.name != null) {
                sortedNewNodes.put(node.name, node);
            }
        }

        TreeMap<String, MNode> sortedOldNodes = new TreeMap<String, MNode>();
        final Vector<MNode> oldNodeList = co.getNodes();
        for (final MNode node: oldNodeList) {
            if(node.name != null) {
                sortedOldNodes.put(node.name, node);
            }
        }

        for (final MNode oNode: oldNodeList) {
            MNode nNode = sortedNewNodes.get(oNode.name);
            if (nNode == null) {
                if(debugInfo != null) {
                    debugInfo.append("\nREMOVED node: ").append(oNode.name).append(" in cluster ").append(co.name);
                }

                if (diff[1] == null) {
                    diff[1] = new MCluster (cn.name, null);
                } // if
                MNode newN = new MNode (oNode.name, oNode.ipAddress, null, null);
                diff[1].getNodes().add(newN);

            } else {

                // compare parameters to see the diference
                MNode[] chNodes= compareParams(nNode, oNode, debugInfo);

                if (chNodes[0] != null) {
                    if (diff[0] == null) {
                        diff[0] = new MCluster (cn.name, null);
                    } // if
                    if(debugInfo != null) {
                        debugInfo.append("\nADDED node ... new param: ").append(nNode.name).append(" in cluster ").append(cn.name);
                    }
                    diff[0].getNodes().add(chNodes[0]);
                } // if

                if (chNodes[1] != null) {
                    if (diff[1] == null) {
                        diff[1] = new MCluster (cn.name, null);
                    } // if
                    diff[1].getNodes().add(chNodes[1]);
                    if(debugInfo != null) {
                        debugInfo.append("\nREMOVED node ").append(nNode.name).append(" in cluster ").append(cn.name);
                    }
                } // if

            } // if - else
        }

        for (final MNode nNode: newNodeList) {
            if (!sortedOldNodes.containsKey(nNode.name)) {
                if(debugInfo != null) {
                    debugInfo.append("\nADDED node ... new node ").append(nNode.name).append(" in cluster ").append(cn.name);
                }
                if (diff[0] == null) {
                    diff[0] = new MCluster (cn.name, null);
                } // if
                MNode newN = new MNode (nNode.name, nNode.ipAddress, null, null);
                newN.getParameterList().addAll (nNode.getParameterList());
                diff[0].getNodes().add(newN);
            } // if
        } // for

        return diff;
    } // compareNodes

    public static MFarm[] compareClusters(final MFarm fn, final MFarm fo) {
        return compareClusters(fn, fo, null);
    }
    
    public static MFarm[] compareClusters(final MFarm fn, final MFarm fo, final StringBuilder debugInfo) {
        MFarm[] diff = new MFarm[2];

        diff[0] = null;
        diff[1] = null;
        
        final Vector<MCluster> newClustersList = fn.getClusters();

        TreeMap<String, MCluster> sortedNewClusters = new TreeMap<String, MCluster>();
        for (final MCluster cluster: newClustersList) {
            if(cluster.name != null) {
                sortedNewClusters.put(cluster.name, cluster);
            }
        }

        final Vector<MCluster> oldClustersList = fo.getClusters();
        TreeMap<String, MCluster> sortedOldCluster = new TreeMap<String, MCluster>();
        for (final MCluster cluster: oldClustersList) {
            if(cluster.name != null) {
                sortedOldCluster.put(cluster.name, cluster);
            }
        }

        for (final MCluster oClus: oldClustersList) {
            MCluster nClus = sortedNewClusters.get(oClus.name);

            if (nClus == null) {
                if (diff[1] == null) {
                    diff[1] = new MFarm(fn.name);
                }//if

                final MCluster newC = new MCluster(oClus.name, null);
                diff[1].getClusters().add(newC);
                if(debugInfo != null) {
                    debugInfo.append("\nREMOVED cluster ... ").append(oClus.name);
                }

            } else {
                MCluster[] chClus = compareNodes(nClus, oClus, debugInfo);

                if (chClus[0] != null) {
                    if (diff[0] == null) {
                        diff[0] = new MFarm (fn.name);
                    }
                    if(debugInfo != null) {
                        debugInfo.append("\nADDED cluster ... new node ").append(nClus.name);
                    }
                    diff[0].getClusters().add(chClus[0]);
                } // if

                if (chClus[1] != null) {
                    if (diff[1] == null) {
                        diff[1] = new MFarm (fn.name);
                    }
                    if(debugInfo != null) {
                        debugInfo.append("\nREMOVED cluster ... removed node ").append(nClus.name);
                    }
                    diff[1].getClusters().add(chClus[1]);
                } // if
            } // else
        }//for - oldClusteList

        for (final MCluster nClus: newClustersList) {
            if (!sortedOldCluster.containsKey(nClus.name)) {
                if(debugInfo != null) {
                    debugInfo.append("\nADDED cluster ... new cluster").append(nClus.name);
                }
                
                if (diff[0] == null) {
                    diff[0] = new MFarm (fn.name);
                } // if
                MCluster newC = new MCluster (nClus.name, null);
                final Vector<MNode> oldNodeList = nClus.getNodes(); 
                for (final MNode oldmn: oldNodeList) {
                    MNode mn = new MNode (oldmn.name, oldmn.ipAddress, null, null);
                    mn.getParameterList().addAll(oldmn.getParameterList());
                    mn.moduleList.addAll(oldmn.getParameterList());
                    newC.getNodes().add(mn);
                } // for
                diff[0].getClusters().add(newC);
            } // if
        } // for

        return diff;

    } // compareClusters

    public static void main (String args[]) {

        MNode n1 = new MNode("node1", "192.168.1.1", null, null);
        n1.getParameterList().add("param1");
        n1.getParameterList().add("param2");
        n1.getParameterList().add("param3");

        MNode n2 = new MNode("node1", "192.168.1.1", null, null);
        n2.getParameterList().add("param1");
        n2.getParameterList().add("param2");
        n2.getParameterList().add("param4");


        MNode[] nodes = CompConfig.compareParams(n1, n2);
        System.out.println ("Nodes: "+nodes[0]+" "+nodes[1]);


        MCluster c1 = new MCluster ("testCluster", null);
        c1.getNodes().add(n1);

        MCluster c2 = new MCluster ("testCluster", null);
        c2.getNodes().add(n2);

        MFarm f1 = new MFarm ("testfarm");
        f1.getClusters().add(c1);

        MFarm f2 = new MFarm ("testfarm");
        f2.getClusters().add(c2);

        MFarm[] f = CompConfig.compareClusters(f1, f2);
        System.out.println (f[0]+" "+((MCluster)f[0].getClusters().elementAt(0)).getNodes() );
        System.out.println (f[1]+" "+f[1].getClusters().elementAt(0));

    } // main


} // CompConfig
