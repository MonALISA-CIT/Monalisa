package lia.Monitor.Agents.OpticalPath.v2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Agents.OpticalPath.v2.State.OSwConfig;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwFSM;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwLink;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwPort;

/**
 * This class should keep the last "known" OSPathFinder in memory
 * It should trigger a recompute whenever a change is detected
 * It will keep two graphs in mem ... one with simplex 
 */
class OSPathFinder {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(OSPathFinder.class.getName());

    private final OpticalPathAgent_v2 theAgent;
    private final PriorityQueue remainingVertices;
    private final ArrayList nodesInPath;

    public OSPathFinder(OpticalPathAgent_v2 theAgent) {
        this.theAgent = theAgent;
        this.remainingVertices = new PriorityQueue();
        this.nodesInPath = new ArrayList();
    }

    /**
     * Computes Dijkstra starting with the <code>source</code>
     * 
     * @param source
     * @param destination
     * @param isFdx - the determined path should be full duplex
     * @param addMLID - Optical Path that 
     * @return a {@link DNode} which represents the <code>dstOS</code> or null if no possible path was found 
     */
    synchronized DNode getShortestPath(final OSwConfig srcOS, final OSwConfig dstOS, final boolean isFdx,
            final String allowedMLIDCrossConn) throws ProtocolException {

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [ OSPathFinder ] getShortestPath ( src =" + srcOS.name + ", dst = " + dstOS.name
                    + ", isFDX = " + isFdx + ", addMLID = " + allowedMLIDCrossConn + ")");
        }

        if (srcOS.equals(dstOS)) {
            return new DNode(srcOS);
        }

        DNode.NODE_SEQUENCER = 0;
        remainingVertices.clear();
        nodesInPath.clear();
        //keeps a map K: Optical Switch Name and V: DNode
        HashMap osNameToDNodeMap = new HashMap();

        DNode s = new DNode(srcOS);
        s.distance = 0;
        s.predecessor = null;

        remainingVertices.offer(s);
        osNameToDNodeMap.put(srcOS.name, s);

        do {

            DNode u = (DNode) remainingVertices.poll();

            if (u == null) {
                break;
            }
            if (u.oswConfig == dstOS) {
                if (!nodesInPath.contains(u)) {
                    nodesInPath.add(u);
                }
                break;
            }

            if (u.oswConfig.osPorts == null) {
                throw new ProtocolException("DNode has an oswConfig for: " + u.oswConfig.name
                        + " with no OS Ports associated");
            }

            //relax all neighbours 
            int osPortsLen = u.oswConfig.osPorts.length;
            for (int i = 0; i < osPortsLen; i++) {
                OSwPort oswPort = u.oswConfig.osPorts[i];
                //check only for outgoing ports which are connected (have fiber) and have other switches at the other end
                if ((oswPort.fiberState == OSwPort.FIBER) && (oswPort.type == OSwPort.OUTPUT_PORT)
                        && (oswPort.oswLink != null) && (oswPort.oswLink.type == OSwLink.TYPE_SWITCH)) {

                    //check if the other end is already in the shortest path
                    if (nodesInPath.contains(oswPort.oswLink.destination)) {
                        continue;
                    }

                    //get the "remote" peer
                    OSwConfig remoteOSwConfig = null;
                    if (oswPort.oswLink.destination == OSwFSM.getInstance().oswConfig.name) {
                        remoteOSwConfig = OSwFSM.getInstance().oswConfig;
                    } else {
                        Object key = theAgent.swNameAddrHash.get(oswPort.oswLink.destination);
                        if (key == null) {
                            continue;
                        }
                        remoteOSwConfig = (OSwConfig) theAgent.otherConfs.get(key);
                    }

                    if ((remoteOSwConfig == null) || (remoteOSwConfig.osPorts == null)) {
                        //maybe not in local config yet; or incomplete config
                        continue;
                    }

                    //check if this port is in any cross connect ... if yes, check if the cross connect has allowedMLIDCrossConn ID
                    if (!OpticalPathAgent_v2.checkPort(oswPort, allowedMLIDCrossConn, u.oswConfig)) {
                        continue;
                    }

                    int remotePortsNo = remoteOSwConfig.osPorts.length;

                    //check the remote "input" port
                    OSwPort remotePort = null;
                    for (int rpi = 0; rpi < remotePortsNo; rpi++) {
                        OSwPort tmpRemotePort = remoteOSwConfig.osPorts[rpi];
                        if ((tmpRemotePort.name != null)
                                && tmpRemotePort.name.equals(oswPort.oswLink.destinationPortName)
                                && (tmpRemotePort.type == OSwPort.INPUT_PORT)) {
                            remotePort = tmpRemotePort;
                            break;
                        }
                    }

                    if (remotePort == null) {
                        continue;
                    }
                    if (!OpticalPathAgent_v2.checkPort(remotePort, allowedMLIDCrossConn, remoteOSwConfig)) {
                        continue;
                    }

                    double fdxCost = 0;

                    if (isFdx) {
                        OSwPort localPearPort = null;
                        //check the local input port
                        for (int il = 0; il < osPortsLen; il++) {
                            OSwPort tmpLocalPearPort = u.oswConfig.osPorts[il];
                            //check only for outgoing ports which are connected (have fiber) and have other switches at the other end
                            if ((tmpLocalPearPort.fiberState == OSwPort.FIBER)
                                    && (tmpLocalPearPort.type == OSwPort.INPUT_PORT)
                                    && tmpLocalPearPort.name.equals(oswPort.name)) {
                                localPearPort = tmpLocalPearPort;
                                break;
                            }
                        }

                        if (localPearPort == null) {
                            continue;
                        }
                        if (!OpticalPathAgent_v2.checkPort(localPearPort, allowedMLIDCrossConn, u.oswConfig)) {
                            continue;
                        }

                        //check the remote "output" port
                        remotePort = null;
                        for (int rpi = 0; rpi < remotePortsNo; rpi++) {
                            OSwPort tmpRemotePort = remoteOSwConfig.osPorts[rpi];
                            if ((tmpRemotePort.name != null)
                                    && tmpRemotePort.name.equals(oswPort.oswLink.destinationPortName)
                                    && (tmpRemotePort.type == OSwPort.INPUT_PORT)) {
                                remotePort = tmpRemotePort;
                                break;
                            }
                        }

                        if (remotePort == null) {
                            continue;
                        }
                        if (!OpticalPathAgent_v2.checkPort(remotePort, allowedMLIDCrossConn, remoteOSwConfig)) {
                            continue;
                        }
                        fdxCost = oswPort.oswLink.quality;

                    }//if (isFDX)

                    //everything is ok with the link(s) and end ports and now we can "relax" the peer
                    double possibleCost = u.distance
                            + ((isFdx) ? ((oswPort.oswLink.quality + fdxCost) / 2.0) : oswPort.oswLink.quality);
                    DNode v = (DNode) osNameToDNodeMap.get(remoteOSwConfig.name);
                    if (v == null) {
                        v = new DNode(remoteOSwConfig);
                        osNameToDNodeMap.put(remoteOSwConfig.name, v);
                    }

                    if (v.distance > possibleCost) {
                        if (remainingVertices.contains(v)) {
                            remainingVertices.remove(v);
                        }
                        v.distance = possibleCost;
                        v.predecessor = u;
                        v.predecessorPort = oswPort.name;
                        v.localPort = remotePort.name;
                        remainingVertices.add(v);
                    }

                    if (!nodesInPath.contains(u)) {
                        nodesInPath.add(u);
                    }
                }//if ( PORT OK )
            }// for all the ports

        } while (remainingVertices.size() > 0);

        if (nodesInPath == null) {
            return null;
        }
        int pathLen = nodesInPath.size();

        if (pathLen == 0) {
            return null;
        }
        DNode cElem = (DNode) nodesInPath.get(pathLen - 1);
        if (!cElem.oswConfig.equals(dstOS)) {
            return null;
        }

        return cElem;
    }

}
