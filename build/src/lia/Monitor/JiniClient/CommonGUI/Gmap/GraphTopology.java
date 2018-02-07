package lia.Monitor.JiniClient.CommonGUI.Gmap;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Vector;

import lia.Monitor.JiniClient.CommonGUI.DLink;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Topology.EntityLink;
import lia.Monitor.JiniClient.Farms.OSGmap.OSGraphPan;
import lia.Monitor.JiniClient.VRVS3D.Gmap.GraphPan;
import lia.Monitor.monitor.ILink;
import lia.Monitor.monitor.OSLink;
import lia.Monitor.tcpClient.tClient;
import net.jini.core.lookup.ServiceID;

public class GraphTopology {

    /** List of GraphNodes that make up this graph. */
    public LinkedList gnodes;
	public Hashtable nodesMap;
	
    public GraphTopology() {
    	nodesMap = new Hashtable();
        gnodes = new LinkedList();
    }

    public GraphTopology(LinkedList gnodes) {
		nodesMap = new Hashtable();
        this.gnodes = gnodes;
    }

    public int size() {
        return gnodes.size();
    }

    public GraphNode add(rcNode rcnode) {
        if(rcnode == null)
        	return null;
        synchronized(gnodes){
        	GraphNode gn = (GraphNode) nodesMap.get(rcnode);
        	if(gn == null){
        		gn = new GraphNode(rcnode);
        		nodesMap.put(rcnode, gn);
        		gnodes.add(gn);
        	}
        	return gn;
        }	
    }

	public void add(rcNode from, rcNode to, double linkValue){
		synchronized(gnodes){
			GraphNode fromGN = add(from);
			GraphNode toGN = add(to);
			fromGN.neighbors.put(toGN, Double.valueOf(linkValue));
//			toGN.parent = fromGN;
		}
	}

	public boolean isLink(rcNode from, rcNode to){
		GraphNode fromGN = (GraphNode) nodesMap.get(from);
		GraphNode toGN = (GraphNode) nodesMap.get(to);
		if(fromGN == null || toGN == null)
			return false;
		return fromGN.neighbors.containsKey(toGN);
	}
	
	public void removeLink(rcNode from, rcNode to){
		GraphNode fromGN = (GraphNode) nodesMap.get(from);
		GraphNode toGN = (GraphNode) nodesMap.get(to);
		if(fromGN == null || toGN == null)
			return;
		fromGN.neighbors.remove(toGN);
	}
	
	public double getLinkValue(rcNode from, rcNode to){
		GraphNode fromGN = (GraphNode) nodesMap.get(from);
		GraphNode toGN = (GraphNode) nodesMap.get(to);
		if(fromGN == null || toGN == null)
			return -1;
		Double val = (Double) fromGN.neighbors.get(toGN);
		if(val == null)
			return -1;
		else
			return val.doubleValue();
	}

	public GraphNode getGN(rcNode n){
        synchronized(gnodes){
        	for(Iterator it=gnodes.iterator(); it.hasNext();){
				GraphNode gn = (GraphNode) it.next();
				if(gn.rcnode == n)
					return gn;
        	}
        }
        return null;	
	}
	
    public void remove(rcNode rcnode) {
    	if(rcnode == null)
    		return;
        synchronized(gnodes){
        	GraphNode gn = (GraphNode) nodesMap.remove(rcnode);
        	gnodes.remove(gn);
        	for(Iterator it=gnodes.iterator(); it.hasNext();){
				GraphNode peer = (GraphNode) it.next();
				if(peer.neighbors.containsKey(gn))
					peer.neighbors.remove(gn);
//        		if(peer.parent == gn)
//        			peer.parent = null;
        	}
        }
    }

    /** helper function for pruneToTree - see its description */
    public void pruneToTreeHelper(rcNode start) {
		GraphNode fromGN = (GraphNode) nodesMap.get(start);
		if(fromGN == null){
			System.out.println("Error pruning tree - starting from "+start.UnitName+" @ "+start.IPaddress);
			return;
		}
		for(Iterator pit = fromGN.neighbors.keySet().iterator(); pit.hasNext(); ){
			GraphNode peer = (GraphNode) pit.next();
			if(peer.rcnode.Predecessor == null)
				peer.rcnode.Predecessor = start;
			else
				pit.remove();
		}
		for(Iterator pit = fromGN.neighbors.keySet().iterator(); pit.hasNext(); ){
			GraphNode peer = (GraphNode) pit.next();
			pruneToTree(peer.rcnode);
		}
    }
    
    /** make a tree from a graph, by cutting edges that could create a cycle
     * The graph is walked in breadth, starting from a node
     */
    public void pruneToTree(rcNode start){
        start.Predecessor = start;
        pruneToTreeHelper(start);
    }

    /** Construct a GraphTopology from Max Flow. */
    public static GraphTopology constructTreeFromMaxFlow(Hashtable hnodes, Vector mfData) {
		GraphTopology gt = new GraphTopology();
		for(Enumeration e1=hnodes.elements(); e1.hasMoreElements();){
			rcNode n1 = (rcNode) e1.nextElement();
			gt.add(n1);
			for(Enumeration e2 = n1.conn.keys(); e2.hasMoreElements();){
				rcNode n2 = (rcNode) e2.nextElement();
				if(n1.connLP(n2) < 1.0)	// if it has lost packages < 100%
					if(isHotLink(n1, n2, mfData))
						gt.add(n1, n2, n1.connPerformance(n2));
			}
		}
		return gt;
    }

	static public boolean isHotLink(rcNode n1, rcNode n2, Vector maxFlow) {
		if (maxFlow == null)
			return false;
		for (int i = 0; i < maxFlow.size(); i++) {
			DLink dl = (DLink) maxFlow.elementAt(i);
			if (dl.cequals(n1.sid, n2.sid))
				return true;
		}
		return false;
	}
	
    /** Construct a GraphTopology from Internet links. */
    public static GraphTopology constructGraphInet(Hashtable hnodes) {
		GraphTopology gt = new GraphTopology();
		for(Enumeration e1=hnodes.elements(); e1.hasMoreElements();){
			rcNode n1 = (rcNode) e1.nextElement();
			gt.add(n1);
			if(n1.haux.get("lostConn") != null)
				continue;
			for(Enumeration e2 = n1.conn.keys(); e2.hasMoreElements();){
				rcNode n2 = (rcNode) e2.nextElement();
				// if it has lost packages < 100%
				// and peer node isn't dead
				if((n1.connLP(n2) < 1.0) && (n2.haux.get("lostConn") == null))	
					gt.add(n1, n2, n1.connPerformance(n2));
			}
		}
		return gt;
    }

    /** Construct a GraphTopology from Internet links. */
    public static GraphTopology constructGraphInet4VRVS(Hashtable hnodes) {
    	GraphTopology gt = new GraphTopology();
    	for(Enumeration e1=hnodes.elements(); e1.hasMoreElements();){
    		rcNode n1 = (rcNode) e1.nextElement();
    		gt.add(n1);
    		if(n1.haux.get("lostConn") != null)
    			continue;
    		for(Enumeration e2 = n1.conn.keys(); e2.hasMoreElements();){
    			rcNode n2 = (rcNode) e2.nextElement();
    			// if it has lost packages < 100%
    			// and peer node isn't dead
    			if(n2.haux.get("lostConn") != null)
    				continue;
			    Object objLink = n1.wconn.get(n2.sid);
			    if ( !(objLink instanceof ILink) )
			        continue;
				ILink link = (ILink) objLink;
    			if(link == null || link.inetQuality == null)
    				continue;
    			if(link.inetQuality[3] < 1.0)	
    				gt.add(n1, n2, link.inetQuality[0]);
    		}
    	}
    	return gt;
    }

    /** Construct a GraphTopology as an unconnected graph. */
    public static GraphTopology constructUnconnectedGraph(Hashtable hnodes) {
		GraphTopology gt = new GraphTopology();
		for(Enumeration e1=hnodes.elements(); e1.hasMoreElements();){
			rcNode n1 = (rcNode) e1.nextElement();
			gt.add(n1);
		}
		return gt;
    }
    
    /** construct a GraphTopology from MST */
    public static GraphTopology constructTreeFromMST(Hashtable hnodes, Vector mfData) {
//		System.out.println("constructTreeFromMST");
    	GraphTopology gt = new GraphTopology();
    	for(Enumeration en=hnodes.elements(); en.hasMoreElements(); ){
    		rcNode n = (rcNode) en.nextElement();
    		gt.add(n);
    	}
		for(int i=0; i<mfData.size(); i++){
			DLink dlink = (DLink) mfData.get(i);
			if(dlink == null) continue;
			rcNode n1 = (rcNode) hnodes.get(dlink.fromSid);
			rcNode n2 = (rcNode) hnodes.get(dlink.toSid);
			if(n1 == null || n2 == null) continue;
//			System.out.println("treeFromMST: "+n1.UnitName+" <-> "+n2.UnitName);
			gt.add(n1, n2, dlink.dist);
//			gt.add(n2, n1);
		}
		return gt;
    }
    
	/** construct a GraphTopology from Peers */
	public static GraphTopology constructTreeFromPeers(Hashtable hnodes) {
		GraphTopology gt = new GraphTopology();
		for(Enumeration e1=hnodes.elements(); e1.hasMoreElements();){
			rcNode n1 = (rcNode) e1.nextElement();
			gt.add(n1);
			for(Enumeration e2 = n1.wconn.keys(); e2.hasMoreElements();){
				ServiceID n2sid = (ServiceID) e2.nextElement();
			    Object objLink = n1.wconn.get(n2sid);
			    if ( !(objLink instanceof ILink) )
			        continue;
				ILink link = (ILink) objLink;
				rcNode n2 = (rcNode) hnodes.get(n2sid);
				if(n2 == null || link == null || link.peersQuality == null) 
					continue;
				gt.add(n1, n2, (link.peersQuality != null ? 110 - link.peersQuality[0] : -1 ));
			}
		}
		return gt;
	}

	/** using Peers, Inet, and MST links buid a Graph for the layout algorithms */
	public static GraphTopology constructGraphFromPeersPingAndMST(Hashtable hnodes, Vector mfData,
	        	boolean usePeers, boolean usePing, boolean useMST){
		GraphTopology gt = new GraphTopology();
		for(Enumeration e1=hnodes.elements(); e1.hasMoreElements();){
			rcNode n1 = (rcNode) e1.nextElement();
			gt.add(n1);
			n1.Predecessor = null;
			if(n1.haux.get("lostConn") != null)
    			continue;
			for(Enumeration e2 = n1.wconn.keys(); e2.hasMoreElements();){
				ServiceID n2sid = (ServiceID) e2.nextElement();
			    Object objLink = n1.wconn.get(n2sid);
			    if ( !(objLink instanceof ILink) )
			        continue;
				ILink link = (ILink) objLink;
				rcNode n2 = (rcNode) hnodes.get(n2sid);
				if(n2 == null || link == null || n2.haux.get("lostConn") != null) 
					continue;
				double val = 0;
				int nrVal = 0;
				if(usePeers && link.peersQuality != null){
				    val += 110 - link.peersQuality[0];
				    nrVal++;
				}
				if(usePing && link.inetQuality != null && link.inetQuality[3] < 1.0){	
	    				val += link.inetQuality[0];
	    				nrVal++;
				}
				if(nrVal > 0){
				    gt.add(n1, n2, val/nrVal);
				    gt.add(n2, n1, val/nrVal);
				}
			}
		}
		if(useMST){
		    for(int i=0; i<mfData.size(); i++){
				DLink dlink = (DLink) mfData.get(i);
				if(dlink == null) 
				    continue;
				rcNode n1 = (rcNode) hnodes.get(dlink.fromSid);
				rcNode n2 = (rcNode) hnodes.get(dlink.toSid);
				if(n1 == null || n2 == null) 
				    continue;
				double d = dlink.dist;
				if(d < 10)
				    d = 10;
				gt.add(n1, n2, d);
				gt.add(n2, n1, d);
			}
		}
		return gt;
	}
	
	public static GraphTopology constructGraphFromPeersPingAndMSTAndUsers(Hashtable hnodes, Vector mfData,
        	boolean usePeers, boolean usePing, boolean useMST, GraphPan vrvsgpan){
	GraphTopology gt = new GraphTopology();
	for(Enumeration e1=hnodes.elements(); e1.hasMoreElements();){
		rcNode n1 = (rcNode) e1.nextElement();
		gt.add(n1);
		n1.Predecessor = null;
		if(n1.haux.get("lostConn") != null)
			continue;
		//if node is fake node, it has no wconn...
		Object []triplet=null;
		if ( (triplet=vrvsgpan.LTFNcontains(0,n1))!=null ) {
		    rcNode n2 = (rcNode)(triplet[2]);
//		    System.out.println("adding links from "+n1.UnitName+" to "+n2.UnitName);
		    gt.add(n1, n2, 5);
		    gt.add(n2, n1, 5);
		} else { //normal node, so enumerate its links
			for(Enumeration e2 = n1.wconn.keys(); e2.hasMoreElements();){
				ServiceID n2sid = (ServiceID) e2.nextElement();
			    Object objLink = n1.wconn.get(n2sid);
			    if ( !(objLink instanceof ILink) )
			        continue;
				ILink link = (ILink) objLink;
				rcNode n2 = (rcNode) hnodes.get(n2sid);
				if(n2 == null || link == null || n2.haux.get("lostConn") != null) 
					continue;
				double val = 0;
				int nrVal = 0;
				if(usePeers && link.peersQuality != null){
				    val += 110 - link.peersQuality[0];
				    nrVal++;
				}
				if(usePing && link.inetQuality != null && link.inetQuality[3] < 1.0){	
	    				val += link.inetQuality[0];
	    				nrVal++;
				}
				if(nrVal > 0){
				    gt.add(n1, n2, val/nrVal);
				    gt.add(n2, n1, val/nrVal);
				}
			}
		};
	}
	if(useMST){
	    for(int i=0; i<mfData.size(); i++){
			DLink dlink = (DLink) mfData.get(i);
			if(dlink == null) 
			    continue;
			rcNode n1 = (rcNode) hnodes.get(dlink.fromSid);
			rcNode n2 = (rcNode) hnodes.get(dlink.toSid);
			if(n1 == null || n2 == null) 
			    continue;
			double d = dlink.dist;
			if(d < 10)
			    d = 10;
			gt.add(n1, n2, d);
			gt.add(n2, n1, d);
		}
	}
	return gt;
}

	/** using Peers, Inet, and MST links buid a Tree for the layout algorithms */
	public static GraphTopology constructTreeFromPeersPingAndMST(Hashtable hnodes, Vector mfData,
        	boolean usePeers, boolean usePing, boolean useMST, rcNode startNode){
	    GraphTopology gt = constructGraphFromPeersPingAndMST(hnodes, mfData, usePeers, usePing, useMST);
	    gt.pruneToTree(startNode);
	    return gt;
	}

    /** Construct a GraphTopology from Internet links (if used) and max flow (if available) */
	public static GraphTopology constructGraphFromInetAndMaxFlow(Map<ServiceID, rcNode> hnodes, Vector maxFlow,
	        	boolean useInet){
		GraphTopology gt = new GraphTopology();
		
		for(final rcNode n1 : hnodes.values() ){
			if ( n1.mlentry!=null && tClient.isOSgroup(n1.mlentry.Group) )
			    continue;
			gt.add(n1);
			n1.Predecessor = null;
		}
		
		if(useInet){
			for(final rcNode n1: hnodes.values() ){
				if ( n1.mlentry!=null && tClient.isOSgroup(n1.mlentry.Group) )
				    continue;
				if(n1.haux.get("lostConn") != null)
					continue;
				for(Enumeration e2 = n1.conn.keys(); e2.hasMoreElements();){
					rcNode n2 = (rcNode) e2.nextElement();
					// if it has lost packages < 100% - REMOVED THIS CONDITION
					// and peer node isn't dead
					//if((n1.connLP(n2) < 1.0) && (n2.haux.get("lostConn") == null))
					//if(n2.haux.get("lostConn") == null){
						gt.add(n1, n2, n1.connPerformance(n2));
						gt.add(n2, n1, n2.connPerformance(n1));
					//}
				}
			}
		}
		if(maxFlow != null){
		    //System.out.println("adding maxflow");
			for(final rcNode n1 : hnodes.values()){
				if ( n1.mlentry!=null && tClient.isOSgroup(n1.mlentry.Group) )
				    continue;
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

	/** Construct a GraphTopology from Internet links (if used) and max flow (if available) */
	public static GraphTopology constructGraphFromInetAndMaxFlowAndOS(Hashtable hnodes, Vector maxFlow,
	        	boolean useInet, boolean useOS, OSGraphPan ospan){
		GraphTopology gt = new GraphTopology();
		
		for(Enumeration e1=hnodes.elements(); e1.hasMoreElements(); ){
			rcNode n1 = (rcNode) e1.nextElement();
			gt.add(n1);
			n1.Predecessor = null;
		}
		
		OSLink link;
		
		for(Enumeration e1=hnodes.elements(); e1.hasMoreElements(); ){
		    rcNode n1 = (rcNode) e1.nextElement();
		    if(n1.haux.get("lostConn") != null)
		        continue;
		    if(useInet){
		        for(Enumeration e2 = n1.conn.keys(); e2.hasMoreElements();){
		            rcNode n2 = (rcNode) e2.nextElement();
		            // if it has lost packages < 100% - REMOVED THIS CONDITION
		            // and peer node isn't dead
		            //if((n1.connLP(n2) < 1.0) && (n2.haux.get("lostConn") == null))
		            //if(n2.haux.get("lostConn") == null){
		            gt.add(n1, n2, n1.connPerformance(n2));
		            gt.add(n2, n1, n2.connPerformance(n1));
		            //}
		        }
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
	
	// not used anymore
	public static GraphTopology constructGraphFromTopology(Hashtable traces, String what){
		GraphTopology gt = new GraphTopology();
		for(Enumeration entr=traces.keys(); entr.hasMoreElements(); ){
			String ipns = (String) entr.nextElement();
			// ipns = source IP
			Hashtable tracePeers = (Hashtable) traces.get(ipns);
			for(Enumeration enpr=tracePeers.keys(); enpr.hasMoreElements(); ){
				String ipnw = (String) enpr.nextElement();
				// ipnw = dest IP
				Hashtable traceValues = (Hashtable) tracePeers.get(ipnw);

				// get the entity trace and delay trace
				Vector crtTrace = (Vector) traceValues.get(what+"-trace");
				Vector crtDelay = (Vector) traceValues.get(what+"-delay");
				
				if(crtTrace == null || crtDelay == null){
					System.out.println("GraphTopology: TRACE NULL for "+what+" :: "+ipns+" -> "+ipnw);
					continue;
				}
				for(int i=1; i<crtTrace.size(); i++){
					rcNode n1 = (rcNode) crtTrace.get(i-1);
					rcNode n2 = (rcNode) crtTrace.get(i);
					String delay = (String) crtDelay.get(i);
					gt.add(n1, n2, Double.parseDouble(delay));
				}
			}
		}
		return gt;
	}
	
	/** create a graph using the EntityLinks built by GTopoArea */
	public static GraphTopology constructGraphFromEntityLinks(Vector links){
		GraphTopology gt = new GraphTopology();
		for(int i=0; i<links.size(); i++){
			EntityLink link = (EntityLink) links.get(i);
			link.n1.Predecessor = link.n2.Predecessor = null; // this is used in pruneToTree
			gt.add(link.n1, link.n2, link.delay);
		}
		return gt;
	}
	
	/** this is used in vrvs client to find a root when a tree layout 
	 * must be drawn, and there is no starting node selected
	 * @return the rcNode to start from.
	 */
	public rcNode findARoot(){
		for(Iterator gnit = gnodes.iterator(); gnit.hasNext(); ){
			GraphNode gn = (GraphNode) gnit.next();
			if(gn.neighbors.size() > 0)
				return gn.rcnode;
		}
		return null;
	}
}
