package lia.Monitor.JiniClient.Farms.OpticalSwitch.topology;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.Farms.OpticalSwitch.OpticalSwitchGraphPan;
import lia.Monitor.monitor.OSLink;

public class GraphTopology extends lia.Monitor.JiniClient.CommonGUI.Gmap.GraphTopology {

    public GraphTopology() {
    	super();
    }

    public GraphTopology(LinkedList gnodes) {
		super(gnodes);
    }

    public int size() {
        return super.size();
    }


    /** Construct a GraphTopology from Internet links (if used) and max flow (if available) */
	public static GraphTopology constructGraphFromInetAndMaxFlowAndOS(Hashtable hnodes, Vector maxFlow,
	        	boolean useInet, boolean useOS, OpticalSwitchGraphPan ospan){
		GraphTopology gt = new GraphTopology();
		
		for(Enumeration e1=hnodes.elements(); e1.hasMoreElements(); ){
			rcNode n1 = (rcNode) e1.nextElement();
			gt.add(n1);
			n1.Predecessor = null;
		}
		
		OSLink link;
		
		for(Enumeration e1=hnodes.elements(); e1.hasMoreElements(); ){
		    rcNode n1 = (rcNode) e1.nextElement();
//		    if(n1.haux.get("lostConn") != null)
//		        continue;
		    if(useInet){
//		        for(Enumeration e2 = n1.conn.keys(); e2.hasMoreElements();){
//		            rcNode n2 = (rcNode) e2.nextElement();
//		            // if it has lost packages < 100% - REMOVED THIS CONDITION
//		            // and peer node isn't dead
//		            //if((n1.connLP(n2) < 1.0) && (n2.haux.get("lostConn") == null))
//		            //if(n2.haux.get("lostConn") == null){
//		            gt.add(n1, n2, n1.connPerformance(n2));
//		            gt.add(n2, n1, n2.connPerformance(n1));
//		            //}
//		        }
		    };
		    if ( useOS ) {
	            rcNode rcDest;
	            Object con[];
		        for ( Iterator it2 = n1.wconn.values().iterator(); it2.hasNext(); ) {
		            Object objLink = it2.next();
		            if ( !(objLink instanceof OSLink) )
		                continue;
		            link = (OSLink)objLink;
		            rcDest = link.rcDest;
                    if (rcDest==null && ospan != null) { //if no destination, check to see if fake link, so check the hash for fake links/nodes and that the destination node is stii in nodes structure
						for (int k=0; k<ospan.lLinkToFakeNode.size(); k++) {
							con = (Object[])ospan.lLinkToFakeNode.get(k);
							if (con[3] != null && con[3].equals(n1) && con[0] != null && ((rcNode)con[0]).UnitName != null && ((rcNode)con[0]).UnitName.equals(link.szDestName)) {
								rcDest = (rcNode)con[0];
								break;
							}
						}
                    }
					if ( rcDest==null )
					    continue;
		            double dVal;
		            if ( link.rcDest!=null ) {
		                dVal = gt.getLinkValue(n1, rcDest);
		                //System.out.println("link "+link+" with dVal="+dVal);
		            } else dVal = -1;
		            if ( dVal == -1 )
		                dVal = link.nState*300;
		            else
		                dVal = dVal/2.0;
		            gt.add(n1, rcDest, dVal);
		            if ( link.rcDest!=null ) {
		                dVal = gt.getLinkValue(link.rcDest, n1);
		                //System.out.println("link "+link+" with dVal="+dVal);
		            } else dVal = -1;
		            if ( dVal == -1 )
		                dVal = link.nState*300;
		            else
		                dVal = dVal/2.0;
		            gt.add(rcDest, n1, dVal);
		        };
		    }
		}
		if(maxFlow != null){
		    //System.out.println("adding maxflow");
			for(Enumeration e1=hnodes.elements(); e1.hasMoreElements();){
				rcNode n1 = (rcNode) e1.nextElement();
				for(Enumeration e2 = n1.conn.keys(); e2.hasMoreElements();){
					rcNode n2 = (rcNode) e2.nextElement();
					if(n1.connLP(n2) < 1.0)	// if it has lost packages < 100%
						if(isHotLink(n1, n2, maxFlow)){
						    //System.out.println("mx flow link "+n1.UnitName+" - "+n2.UnitName);
							gt.add(n1, n2, n1.connPerformance(n2));
						}
				}
			}
		}
		return gt;
	}
    
}
