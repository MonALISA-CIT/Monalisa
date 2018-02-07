package lia.Monitor.JiniClient.CommonGUI.OSG;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphTopology;

/**
 * A class which extends the GraphTopology by adding string information for the links.
 */
public class ExtendedGraphTopology extends GraphTopology {

    public GraphNode add(rcNode rcnode) {
		
        if(rcnode == null)
        	return null;
        synchronized(gnodes){
        	GraphNode gn = (GraphNode) nodesMap.get(rcnode);
        	if(gn == null){
        		gn = new ExtendedGraphNode(rcnode);
        		nodesMap.put(rcnode, gn);
        		gnodes.add(gn);
        	}
        	return gn;
        }	
    }
	
	public void add(rcNode from, rcNode to, double linkValue, String linkString){
		
		super.add(from, to, linkValue);
		synchronized(gnodes){
			GraphNode fromGN = add(from);
			GraphNode toGN = add(to);
			fromGN.neighbors.put(toGN, Double.valueOf(linkValue));
			if (fromGN instanceof ExtendedGraphNode) {
				((ExtendedGraphNode)fromGN).neighborsStrings.put(toGN, linkString);
			}
		}
	}
	
} // end of class ExtendedGraphTopology

