/*
 * $Id: OsrpTL1Topo.java 6865 2010-10-10 10:03:16Z ramiro $
 * 
 * Created on Nov 4, 2007
 * 
 */
package lia.Monitor.ciena.osrp.tl1;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeSet;

import lia.Monitor.ciena.osrp.topo.OsrpNode;


public class OsrpTL1Topo implements Serializable {

    private static final long serialVersionUID = 6615660723309595060L;
    
    /**
     * The OSRP ID from which the topo is seen
     */
    public final String osrpNodeId;
    
    public final TreeSet tl1Nodes;
    public final TreeSet tl1Ltps;
    public final TreeSet tl1Ctps;
    public final TreeSet tl1Routes;

    public OsrpTL1Topo(final OsrpTL1Response[] tl1Nodes,
            final OsrpTL1Response[] tl1Ltps, final OsrpTL1Response[] tl1Ctps, final OsrpTL1Response[] tl1Routes
            ) throws Exception {
        
        this(
             new TreeSet(Arrays.asList(tl1Nodes)),
             new TreeSet(Arrays.asList(tl1Ltps)), 
             new TreeSet(Arrays.asList(tl1Ctps)),
             new TreeSet(Arrays.asList(tl1Routes))
             );
    }
    
    public OsrpTL1Topo(final TreeSet tl1NodesSet,
            final TreeSet tl1LtpsSet, final TreeSet tl1CtpsSet, final TreeSet tl1RoutesSet
            ) throws Exception {
        
        this.tl1Nodes = tl1NodesSet;
        this.tl1Ltps = tl1LtpsSet;
        this.tl1Ctps = tl1CtpsSet;
        this.tl1Routes = tl1RoutesSet;
        
        String localOsrpNodeID = null;
        
        //Search the LOCAL CD/CI ... check also that there is only one LOCAL node in the 'rtrv-osrp-node' TL1 response
        for(Iterator it = tl1NodesSet.iterator(); it.hasNext();) {
            final OsrpTL1Response osrpTL1Response = (OsrpTL1Response)it.next();
            final short locality = OsrpNode.getLocality((String)osrpTL1Response.paramsMap.get("LOCAL"));
            if(locality == OsrpNode.LOCAL) {
                if(localOsrpNodeID == null) {
                    localOsrpNodeID = (String)osrpTL1Response.singleParams.get(0);
                } else {
                    throw new Exception("There are two LOCAL CD/CIs in the response ... Smth wrong with the response?");
                }
            }
        }
        
        if(localOsrpNodeID == null) {
            throw new Exception("Unable to find the LOCAL CD/CI param");
        }
        
        this.osrpNodeId = localOsrpNodeID;
        
        
    }

    public boolean equals(Object o) {
        if(o instanceof OsrpTL1Topo) {
            final OsrpTL1Topo tl1Topo = (OsrpTL1Topo)o;
            return (this.osrpNodeId.equals(tl1Topo.osrpNodeId) &&
                    this.tl1Nodes.equals(tl1Topo.tl1Nodes) &&
                    this.tl1Ltps.equals(tl1Topo.tl1Ltps) &&
                    this.tl1Ctps.equals(tl1Topo.tl1Ctps) &&
                    this.tl1Routes.equals(tl1Topo.tl1Routes)
                    );
        }
        
        throw new ClassCastException("OsrpTL1Topo equals unk class: " + o.getClass());
    }
    
    public int hashCode() {
        return this.osrpNodeId.hashCode();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n ********* OsrpTL1Topo for osrpNodeID: ").append(osrpNodeId);
        sb.append("\n\n=== tl1Nodes ===\n").append(tl1Nodes);
        sb.append("\n\n=== tl1Ltps ===\n").append(tl1Ltps);
        sb.append("\n\n=== tl1Ctps ===\n").append(tl1Ctps);
        sb.append("\n\n=== tl1Routes ===\n").append(tl1Routes);
        sb.append("\n\n ********* END OsrpTL1Topo for osrpNodeID: ").append(osrpNodeId);
        return sb.toString();
    }
}
