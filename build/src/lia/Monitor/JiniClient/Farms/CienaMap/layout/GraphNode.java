package lia.Monitor.JiniClient.Farms.CienaMap.layout;

import java.awt.geom.Point2D;
import java.util.Hashtable;

import lia.Monitor.JiniClient.Farms.CienaMap.CienaNode;

public class GraphNode extends lia.Monitor.JiniClient.CommonGUI.Gmap.GraphNode {

    /** User data associated with this node. */
    public CienaNode rcnode;

    public GraphNode() {
    	super();
    }

    public GraphNode(CienaNode rcnode) {
    	super();
		pos = new Point2D.Double(rcnode.x, rcnode.y);
        this.rcnode = rcnode;
    }

    public GraphNode(CienaNode rcnode, Hashtable neighbors) {
		pos = new Point2D.Double(rcnode.x, rcnode.y);
        this.rcnode = rcnode;
        this.neighbors = neighbors;
    }

}
