package lia.web.utils.gmap;

import java.awt.geom.Rectangle2D;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

/**
 */
public class RadialLayout implements GraphLayoutAlgorithm {

    double weightFactor = 0.6; // children only burden their parents by weightFactor times their weight
	Node gnRoot;
    int maxRadius;
    LinkedList<Node> stack;
	Vector<Node> handled = new Vector<Node>();
	
	/**
	 * @author costing
	 *
	 */
	static protected class RadialGraphNode {
        Node gn;
        Vector<RadialGraphNode> children;
        double weight;
        double minAngle, maxAngle;
		
        RadialGraphNode(Node _gn) {
            this.gn = _gn;
            weight = 1;
            children = new Vector<RadialGraphNode>();
        }
    }
	
	/**
	 * @param root
	 */
	public RadialLayout(Node root) {
        gnRoot = root;
        stack = new LinkedList<Node>();
    }
	
	public void layout(GraphTopology gt) {
		
		if(gnRoot != null){
			RadialGraphNode root = new RadialGraphNode(gnRoot);
			stack.addLast(gnRoot);
			handled.add(gnRoot);
			maxRadius = buildRadialTree(root, 0);
			double radius = 1.0 / (1+maxRadius);
			assignPositions(root, 0, 0, radius, 0, 2*Math.PI);
		}
		 Rectangle2D.Double area = null;
		 if(handled.size() == 0)
		     return;
		 
		 for(Iterator<Node> it=handled.iterator(); it.hasNext();){
			 Node gn = it.next();
			 if(area == null) 
				 area = new Rectangle2D.Double(gn.x, gn.y, 0, 0);
			 else
				 area.add(gn.x, gn.y);
		 }
		 
		 if (area==null)
			 return;
		 
		 double zx = (area.width > 0 ? 1.8 / area.width : 1);
		 double zy = (area.height > 0 ? 2 / area.height : 1);
		 double dx = (area.width > 0 ? -0.8 : 0.0);
		 double dy = (area.height > 0 ? -1.0 : 0.0);
		 for(Iterator<Node> it=handled.iterator(); it.hasNext();){
		 	Node gn = it.next();
		 	gn.x = dx + (gn.x - area.x)*zx;
			gn.y = dy + (gn.y - area.y)*zy;
		 }
	}
	
	/** build the tree containing RadialGraphNodes and compute maxRadius. */
	int buildRadialTree(RadialGraphNode root, int level){
		int maxRadiusLocal = level;
		for(Enumeration<Node> en=root.gn.connQuality.keys(); en.hasMoreElements(); ){
			Node child = en.nextElement();
			if(stack.contains(child))
				continue;
			stack.addLast(child);
			handled.add(child);
			RadialGraphNode rgChild = new RadialGraphNode(child);
			root.children.add(rgChild);
		}		
		for(Iterator<RadialGraphNode> it=root.children.iterator(); it.hasNext(); ){
			RadialGraphNode rgChild = it.next();
			int radius = buildRadialTree(rgChild, level+1);
			if(radius > maxRadiusLocal)
				maxRadiusLocal = radius;
			root.weight += weightFactor * rgChild.weight;
		}
		return maxRadiusLocal;
	}
	
	/** assign the position for each node within a -1,1 maximum square */
	void assignPositions(RadialGraphNode root, double x, double y, double radius, 
											   double minAngle, double maxAngle){
		root.gn.x = (int)x;
		root.gn.y = (int)y;
		double sumWeight = 0;
		for(int i=0; i<root.children.size(); i++){
			RadialGraphNode rgn = root.children.get(i);
			sumWeight += rgn.weight;
		}
		double delaAngle = maxAngle - minAngle;
		double crtAngle = minAngle;
		for(int i=0; i<root.children.size(); i++){
			RadialGraphNode rgn = root.children.get(i);
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
	
} // end of class RadialLayout


