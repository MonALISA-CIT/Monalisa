package lia.Monitor.JiniClient.CommonGUI.Gmap;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import lia.Monitor.JiniClient.CommonGUI.rcNode;

/* RADIAL GRAPHS
 *   Here is an outline of the algorithm used to draw a radial graph, given
 * a "free tree", such as the MST or the peer link connectivity.
 *   First, the topology must be created. This can be done by hand, or with
 * the help of the constructFrom*() methods in the GraphTopology class.
 *   Second, a root node must be selected. This root node will be
 * placed at the center of the screen during the drawing step. The root is
 * selected so as minimize the distance from the root to the furthest node.
 * (Here, distance is used in the graph theoretical sense, as the number
 * of edges along the path connecting two nodes.)
 *   Third, with a root selected, the tree is descended recursively. Each
 * node is assigned a parent, and upon this assignment the node tells the rest
 * of its neighbors (now children) that it will be their parent. At the same
 * time, each node calculates its weight with respect to its newly assigned
 * parent. This will simply be one plus the sum of all its childrens' weights.
 *   Fourth, the positions of the nodes are determined, again recursively.
 * Each node is assigned a radius and a sector, i.e. a pair of angles. The
 * radius is defined as the distance between the node and the root, so that the
 * radius of a child is always one greater than the radius of the parent. The
 * given sector defines the angular area in which the node must be contained.
 * The size of the sector assigned to a child is a function of the child's
 * weight, so that more crowded branches of the tree are given more space.
 *   The end result of this positioning is that each node is given a position
 * on the circumference of a circle. These positions should then scaled and/or
 * stretched to fit in the canvas where they are to be drawn, and the coordinates
 * of each rcNode updated to reflect these new positions.
 */

/** Algorithm for laying out acyclic graphs (ie free trees). */
public class RadialLayoutAlgorithm extends GraphLayoutAlgorithm {

    // Algorithm parameters
    protected double weightFactor = 0.6; // children only burden their parents by weightFactor times their weight
    // Temporary variables
	rcNode rcRoot;
    protected int maxRadius;
    protected LinkedList stack;
	
    static protected class RadialGraphNode {
        public GraphNode gn;
        public Vector children;
        public double weight;
        public double minAngle, maxAngle;
        public RadialGraphNode(GraphNode gn) {
            this.gn = gn;
            weight = 1;
            children = new Vector();
        }
    }

    public RadialLayoutAlgorithm(GraphTopology gt, rcNode root) {
        super(gt);
        rcRoot = root;
        stack = new LinkedList();
    }

	/** build the tree containing RadialGraphNodes and compute maxRadius. */
	int buildRadialTree(RadialGraphNode root, int level){
		int maxRadius = level;
		for(Enumeration en=root.gn.neighbors.keys(); en.hasMoreElements(); ){
			GraphNode child = (GraphNode) en.nextElement();
			if(stack.contains(child))
				continue;
			stack.addLast(child);
			handled.add(child);
//			System.out.println("HANDLED: "+child.rcnode.UnitName);
			RadialGraphNode rgChild = new RadialGraphNode(child);
			root.children.add(rgChild);
		}		
		for(Iterator it=root.children.iterator(); it.hasNext(); ){
			RadialGraphNode rgChild = (RadialGraphNode) it.next();
			int radius = buildRadialTree(rgChild, level+1);
			if(radius > maxRadius)
				maxRadius = radius;
			root.weight += weightFactor * rgChild.weight;
		}
		return maxRadius;
	}
	
	/** assign the position for each node within a -1,1 maximum square */
	void assignPositions(RadialGraphNode root, double x, double y, double radius, 
											   double minAngle, double maxAngle){
		root.gn.pos.setLocation(x, y);
		double sumWeight = 0;
		for(int i=0; i<root.children.size(); i++){
			RadialGraphNode rgn = (RadialGraphNode) root.children.get(i);
			sumWeight += rgn.weight;
		}
		double delaAngle = maxAngle - minAngle;
		double crtAngle = minAngle;
		for(int i=0; i<root.children.size(); i++){
			RadialGraphNode rgn = (RadialGraphNode) root.children.get(i);
			double thisAngle = rgn.weight / sumWeight * delaAngle;
			double nextAngle = crtAngle + thisAngle;
			double angle = (crtAngle + nextAngle) / 2.0;
			double nextx = x + radius * Math.cos(angle);
			double nexty = y + radius * Math.sin(angle);
			double nextMinA = angle - Math.PI + Math.PI/6.0;
			double nextMaxA = angle + Math.PI - Math.PI/6.0;
			assignPositions(rgn, nextx, nexty, radius, nextMinA, nextMaxA);
			crtAngle = nextAngle;
		}
	}

	/** compute graph layout and set nodes positions */
    public void layOut() {
		synchronized(gt.gnodes){
		    GraphNode gnRoot = null;
			if(rcRoot != null)
			    gnRoot = (GraphNode)gt.nodesMap.get(rcRoot);
			if(gnRoot != null){
				RadialGraphNode root = new RadialGraphNode(gnRoot);
				stack.addLast(gnRoot);
				handled.add(gnRoot);
//			System.out.println("HANDLED: "+gnRoot.rcnode.UnitName);
				maxRadius = buildRadialTree(root, 0);
				double radius = 1.0 / (1+maxRadius);
				assignPositions(root, 0, 0, radius, 0, 2*Math.PI);
			}
	        setHandledFlag();
			zoomHandledNodes();
			layUnhandledNodes();
			stack.clear();
		}        
    }

	public void finish() {
		// not used
		
	}

}
