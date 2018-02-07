package lia.Monitor.JiniClient.CommonGUI.Topology;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;

import lia.Monitor.JiniClient.CommonGUI.rcNode;

/**
 * this class computes the minimimum distance from a source node to all
 * other nodes, based on the list of active links (EntityLinks)
 */
public class Dijkstra {
	private HashSet remNodes = new HashSet(); // remaining Nodes

	/** compute a new tree, from a starting node and a set of links */
	private boolean dijkstra(rcNode snode, HashSet links){
		rcNode u, v;
		//System.out.println("dijkstra()");
		remNodes.clear();

		for(Iterator lit = links.iterator(); lit.hasNext(); ){
			EntityLink link = (EntityLink) lit.next();

			remNodes.add(link.n1);
			remNodes.add(link.n2);
			link.n1.BestEstimate = Double.MAX_VALUE;
			link.n2.BestEstimate = Double.MAX_VALUE;
			link.n1.Predecessor = null;
			link.n2.Predecessor = null;
		}

		if(remNodes.size() < 2)
			return false;

		snode.BestEstimate = 0;
		snode.Predecessor = null;

		while(! remNodes.isEmpty()) {
			u = extractClosestNode(); // from remNodes
			if(u == null){
				System.out.println("Dijkstra: Couldn't extract closest node!");
				break;
			}
			for(Iterator lit = links.iterator(); lit.hasNext(); ){
				EntityLink link = (EntityLink) lit.next();
				if(link.n1 == u)
					v = link.n2;
				else
					continue;
				double newDistance = u.BestEstimate + link.delay;
				if(v.BestEstimate > newDistance){
					v.BestEstimate = newDistance;
					v.Predecessor = u;
				}
			}
			//System.out.println("dijkstra: removing "+u.UnitName);
			remNodes.remove(u);
		}
		//System.out.println("DONE dijkstra()");
		return true;
	}
	
	private rcNode extractClosestNode() {
		rcNode closestNode = null;
		double dist = Double.MAX_VALUE;

		for(Iterator nit = remNodes.iterator(); nit.hasNext(); ){
			rcNode node = (rcNode) nit.next();
			if(node.BestEstimate < dist){
				dist = node.BestEstimate;
				closestNode = node;				
			}
		}
		return closestNode;
	}
	
	/** use dijkstra() and build a tree starting from snode */
	synchronized public Vector computeTree(rcNode snode, HashSet links) {
		//System.out.println("computeTree");
		Vector results = new Vector();
		
		if(! dijkstra(snode, links))
			return results;
		
		// build solution
		for(Iterator lit = links.iterator(); lit.hasNext(); ){
			EntityLink link = (EntityLink) lit.next();
			if(link.n2.Predecessor == link.n1)
				results.add(link);
		}
		//System.out.println("DONE computeTree");
		return results;
	}

	/** use dijkstra() and build a path starting from snode to enode*/
	synchronized public Vector computePath(rcNode snode, rcNode enode, HashSet links) {
		//System.out.println("computePath");
		Vector results = new Vector();
		
		if(! dijkstra(snode, links))
			return results;
			
		rcNode v = enode;
		while(v != null){
			rcNode u = v.Predecessor;
			if(u == null)
				break;
			for(Iterator lit = links.iterator(); lit.hasNext(); ){
				EntityLink link = (EntityLink) lit.next();
				if(link.n1 == u && link.n2 == v){
					results.add(link);
					break;
				}
			}
			v = u;
		}
		//System.out.println("DONE computePath");		
		return results;
	}
}
