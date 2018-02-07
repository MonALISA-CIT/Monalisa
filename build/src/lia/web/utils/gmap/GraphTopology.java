package lia.web.utils.gmap;

import java.util.LinkedList;

/**
 */
public class GraphTopology {

	/** List of GraphNodes that make up this graph. */
    public LinkedList<Node> gnodes;
	
	/**
	 * 
	 */
	public GraphTopology() {
		gnodes = new LinkedList<Node>();
	}
	
	/**
	 * @param node
	 */
	public void add(Node node) {
		gnodes.add(node);
	}
	
} // end of class GraphTopology
