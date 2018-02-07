package lia.Monitor.JiniClient.CommonGUI.OSG;

import java.awt.geom.Point2D;
import java.util.Hashtable;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphNode;

/**
 * Extends the default GraphNode.
 */
public class ExtendedGraphNode extends GraphNode {

    public Hashtable neighborsStrings;

	public ExtendedGraphNode() {
		super();
		neighborsStrings = new Hashtable();
	}
	
    public ExtendedGraphNode(rcNode rcnode) {
		super(rcnode);
		pos = new Point2D.Double(rcnode.osgX, rcnode.osgY);
		neighborsStrings = new Hashtable();
    }

    public ExtendedGraphNode(rcNode rcnode, Hashtable neighbors) {
		super(rcnode, neighbors);
		pos = new Point2D.Double(rcnode.osgX, rcnode.osgY);
		neighborsStrings = new Hashtable();
    }

} // end of class ExtendedGraphNode

