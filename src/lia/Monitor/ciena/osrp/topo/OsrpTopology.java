/*
 * $Id: OsrpTopology.java 7419 2013-10-16 12:56:15Z ramiro $
 * 
 * Created on Oct 29, 2007
 * 
 */
package lia.Monitor.ciena.osrp.topo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ciena.osrp.tl1.OsrpTL1Response;
import lia.Monitor.ciena.osrp.tl1.OsrpTL1Topo;
import lia.Monitor.ciena.osrp.tl1.TL1Util;

/**
 * 
 * This class encapsulates a view of the current OSRP topology as seen by an OSRP Node ( CD/CI )
 * which is part of an OSRP network.
 * 
 * @author ramiro
 */
public class OsrpTopology {

    private static final Logger logger = Logger.getLogger(OsrpTopology.class.getName());

    //the key is the OSRP nodeId
    private Map nodesIDMap;

    //index by OSRP node name
    private Map nodesNameMap;

    //
    private final String osrpNodeId;

    //this should be sent over the wire
    private final OsrpNode[] nodes;

    public OsrpTopology(final String osrpNodeID, final OsrpNode[] nodes) throws Exception {
        this.osrpNodeId = osrpNodeID;
        this.nodes = nodes;
        initIndexes();
    }

    private final void initIndexes() throws Exception {
        this.nodesIDMap = new HashMap();
        this.nodesNameMap = new HashMap();

        for (final OsrpNode node : nodes) {
            if (nodesIDMap.containsKey(node.id)) {
                throw new Exception("Duplicate node id: " + node.id + " for node name: " + node.name);
            }

            nodesIDMap.put(node.id, node);

            if (nodesNameMap.containsKey(node.name)) {
                throw new Exception("Duplicate node name: " + node.name);
            }

            nodesNameMap.put(node.name, node);
        }
    }

    public static final OsrpTopology fromOsrpTL1Topo(final OsrpTL1Topo osrpTl1Topo) throws Exception {

        final String osrpNodeID = osrpTl1Topo.osrpNodeId;

        //first build the CTPs for the current osrpNodeID
        final TreeSet tl1Ctps = osrpTl1Topo.tl1Ctps;

        //K: the tp name in the form [<bay>]-<shelf>-<slot>-<subslot>; 
        //V: the OsrpCtp obj
        final Map ctpsMap = new HashMap();

        for (final Iterator it = tl1Ctps.iterator(); it.hasNext();) {
            final OsrpTL1Response tl1Response = (OsrpTL1Response) it.next();
            try {
                final OsrpCtp osrpCtp = OsrpCtp.fromOsrpTL1Response(tl1Response);
                ctpsMap.put(osrpCtp.tp, osrpCtp);
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ OsrpTopology ] got exception fromOsrpTL1Topo for: " + tl1Response, t);
            }
        }

        //build the LTPs
        final TreeSet tl1Ltps = osrpTl1Topo.tl1Ltps;
        final TreeSet tl1Routes = osrpTl1Topo.tl1Routes;

        //K: the SwName 
        //V: Map with K: osrpLtpID
        //            V: OsrpLTP
        final Map ltpsMap = new HashMap();

        //build a map for routemetric TL1 responses
        final Map routeMetricMap = new HashMap(osrpTl1Topo.tl1Routes.size());
        for (final Iterator it = osrpTl1Topo.tl1Routes.iterator(); it.hasNext();) {
            final OsrpTL1Response tl1RouteResponse = (OsrpTL1Response) it.next();

            try {
                final String swName = (String) tl1RouteResponse.singleParams.get(0);
                final int ltpId = Integer.valueOf((String) tl1RouteResponse.paramsMap.get("OSRPLTP")).intValue();
                routeMetricMap.put(new OsrpLtpIdentifier(swName, ltpId), tl1RouteResponse);
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ OsrpTopology ] got exception parsing the route metric map for: "
                        + tl1RouteResponse, t);
            }
        }

        for (final Iterator it = tl1Ltps.iterator(); it.hasNext();) {

            final OsrpTL1Response tl1LtpResponse = (OsrpTL1Response) it.next();
            try {
                final String swName = (String) tl1LtpResponse.singleParams.get(0);
                final int osrpLtpID = Integer.valueOf((String) tl1LtpResponse.singleParams.get(1)).intValue();

                //find the OsrpTL1Response for the specific swName; osrpLtpID
                final Integer ltpKey = Integer.valueOf(osrpLtpID);
                Map m = (Map) ltpsMap.get(swName);
                if (m == null) {
                    m = new HashMap();
                    ltpsMap.put(swName, m);
                }

                m.put(ltpKey,
                        OsrpLtp.fromOsrpTL1Response(swName, osrpLtpID, tl1LtpResponse,
                                (OsrpTL1Response) routeMetricMap.get(new OsrpLtpIdentifier(swName, osrpLtpID))));
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ OsrpTopology ] Got exception building LTP for: " + tl1LtpResponse, t);
            }
        }

        ArrayList allNodes = new ArrayList();
        //Nodes ...
        for (final Iterator itNodes = osrpTl1Topo.tl1Nodes.iterator(); itNodes.hasNext();) {
            final OsrpTL1Response response = (OsrpTL1Response) itNodes.next();
            try {
                final String lID = (String) response.singleParams.get(0);

                final String swName = TL1Util.getStringVal("SWNAME", response);
                allNodes.add(OsrpNode.fromOsrpTL1Response(response, (lID.equals(osrpNodeID) ? ctpsMap : null),
                        (Map) ltpsMap.get(swName)));
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ OsrpTopology ] got exception processing nodes for: " + response, t);
            }
        }

        return new OsrpTopology(osrpNodeID, (OsrpNode[]) allNodes.toArray(new OsrpNode[allNodes.size()]));
    }

    public final String getNodeID() {
        return osrpNodeId;
    }

    public final OsrpNode getNodeWitId(final String nodeId) {
        return (OsrpNode) nodesIDMap.get(nodeId);
    }

    public final OsrpNode getNodeWitName(final String nodeName) {
        return (OsrpNode) nodesNameMap.get(nodeName);
    }

    public final Set getAllNodesNames() {
        return nodesNameMap.keySet();
    }

    public final Set getAllNodesOsrpIDs() {
        return nodesIDMap.keySet();
    }
}
