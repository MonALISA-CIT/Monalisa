package lia.Monitor.JiniClient.CommonGUI.Gmap;

import java.awt.geom.Point2D;
import java.util.Hashtable;

import lia.Monitor.JiniClient.CommonGUI.rcNode;

public class GraphNode {

    /** User data associated with this node. */
    public rcNode rcnode;
    public Point2D.Double pos;
    public Point2D.Double force = new Point2D.Double();
//    public GraphNode parent;			// if tree

    /** Neighbors of this node -> contains pairs (GraphNode, Double) 
     * where Double is the value of the link from this to that node */
    public Hashtable neighbors;

    public GraphNode() {
        pos = new Point2D.Double();
        neighbors = new Hashtable();
    }

    public GraphNode(rcNode rcnode) {
		pos = new Point2D.Double(rcnode.x, rcnode.y);
		neighbors = new Hashtable();
        this.rcnode = rcnode;
    }

    public GraphNode(rcNode rcnode, Hashtable neighbors) {
		pos = new Point2D.Double(rcnode.x, rcnode.y);
        this.rcnode = rcnode;
        this.neighbors = neighbors;
    }

}
