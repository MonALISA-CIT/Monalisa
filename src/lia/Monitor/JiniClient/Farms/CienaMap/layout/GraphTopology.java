package lia.Monitor.JiniClient.Farms.CienaMap.layout;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;

import lia.Monitor.JiniClient.Farms.CienaMap.CienaLTP;
import lia.Monitor.JiniClient.Farms.CienaMap.CienaNode;

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

    public GraphNode add(CienaNode rcnode) {
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

	public void add(CienaNode from, CienaNode to, double linkValue){
		synchronized(gnodes){
			GraphNode fromGN = add(from);
			GraphNode toGN = add(to);
			fromGN.neighbors.put(toGN, Double.valueOf(linkValue));
		}
	}

	public boolean isLink(CienaNode from, CienaNode to){
		GraphNode fromGN = (GraphNode) nodesMap.get(from);
		GraphNode toGN = (GraphNode) nodesMap.get(to);
		if(fromGN == null || toGN == null)
			return false;
		return fromGN.neighbors.containsKey(toGN);
	}
	
	public void removeLink(CienaNode from, CienaNode to){
		GraphNode fromGN = (GraphNode) nodesMap.get(from);
		GraphNode toGN = (GraphNode) nodesMap.get(to);
		if(fromGN == null || toGN == null)
			return;
		fromGN.neighbors.remove(toGN);
	}
	
	public double getLinkValue(CienaNode from, CienaNode to){
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

	public GraphNode getGN(CienaNode n){
        synchronized(gnodes){
        	for(Iterator it=gnodes.iterator(); it.hasNext();){
				GraphNode gn = (GraphNode) it.next();
				if(gn.rcnode == n)
					return gn;
        	}
        }
        return null;	
	}
	
    public void remove(CienaNode rcnode) {
    	if(rcnode == null)
    		return;
        synchronized(gnodes){
        	GraphNode gn = (GraphNode) nodesMap.remove(rcnode);
        	gnodes.remove(gn);
        	for(Iterator it=gnodes.iterator(); it.hasNext();){
				GraphNode peer = (GraphNode) it.next();
				if(peer.neighbors.containsKey(gn))
					peer.neighbors.remove(gn);
        	}
        }
    }

    /** helper function for pruneToTree - see its description */
    public void pruneToTreeHelper(CienaNode start) {
		GraphNode fromGN = (GraphNode) nodesMap.get(start);
		if(fromGN == null){
			System.out.println("Error pruning tree - starting from "+start.UnitName);
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
    public void pruneToTree(CienaNode start){
        start.Predecessor = start;
        pruneToTreeHelper(start);
    }

    /** Construct a GraphTopology from Max Flow. */
    public static GraphTopology constructCienaTree(Hashtable hnodes) {
		GraphTopology gt = new GraphTopology();
		for(Enumeration e1=hnodes.elements(); e1.hasMoreElements();){
			CienaNode n1 = (CienaNode) e1.nextElement();
			gt.add(n1);
			for(Iterator e2 = n1.osrpLtpsMap.keySet().iterator(); e2.hasNext();){
				CienaLTP n2 = (CienaLTP) n1.osrpLtpsMap.get(e2.next());
				if (hnodes.containsKey(n2.rmtName)) {
					gt.add(n1, (CienaNode)hnodes.get(n2.rmtName), n2.maxBW);
				}
			}
		}
		return gt;
    }
    
	/** this is used in vrvs client to find a root when a tree layout 
	 * must be drawn, and there is no starting node selected
	 * @return the rcNode to start from.
	 */
	public CienaNode findCienaRoot(){
		for(Iterator gnit = gnodes.iterator(); gnit.hasNext(); ){
			GraphNode gn = (GraphNode) gnit.next();
			if(gn.neighbors.size() > 0)
				return gn.rcnode;
		}
		return null;
	}

}
