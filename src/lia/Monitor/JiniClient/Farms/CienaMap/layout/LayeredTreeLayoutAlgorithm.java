package lia.Monitor.JiniClient.Farms.CienaMap.layout;

import java.util.Enumeration;
import java.util.Iterator;

import lia.Monitor.JiniClient.Farms.CienaMap.CienaNode;

public class LayeredTreeLayoutAlgorithm extends lia.Monitor.JiniClient.CommonGUI.Gmap.LayeredTreeLayoutAlgorithm {

	CienaNode rcRoot;
	
	public LayeredTreeLayoutAlgorithm(GraphTopology gt, CienaNode root) {
		super(gt, null);
		rcRoot = root;
	}

	int buildLayeredTree(LayeredTreeNode root, int level){
		int maxLevel = level;
		for(Enumeration en=root.gn.neighbors.keys(); en.hasMoreElements(); ){
			GraphNode child = (GraphNode) en.nextElement();
			if(stack.contains(child))
				continue;
			stack.addLast(child);
			handled.add(child);
			LayeredTreeNode ltChild = new LayeredTreeNode(child);
			root.children.add(ltChild);
		}
		for(Iterator it=root.children.iterator(); it.hasNext(); ){
			LayeredTreeNode ltChild = (LayeredTreeNode) it.next();
			int lev = buildLayeredTree(ltChild, level+1);
			if(lev > maxLevel)
				maxLevel = lev;
			root.weight += weightFactor * ltChild.weight;
		}
		return maxLevel;
	}

	void assignPositions(LayeredTreeNode root, double left, double right, 
											   double height, double incrHeight){
		root.gn.pos.setLocation((left+right)/2.0, height);
		double sumWeight = 0;
		for(int i=0; i<root.children.size(); i++){
			LayeredTreeNode rgn = (LayeredTreeNode) root.children.get(i);
			sumWeight += rgn.weight;
		}
		double deltaX = right - left;
		double crtX = left;
		double deltaY = incrHeight * 0.07;
		for(int i=0; i<root.children.size(); i++){
			LayeredTreeNode ltn = (LayeredTreeNode) root.children.get(i);
			double nextX = crtX + ltn.weight / sumWeight * deltaX;
			assignPositions(ltn, crtX, nextX, deltaY + height+incrHeight, -deltaY+incrHeight/**0.999*/);
			deltaY *= -1;	// change the sign
			crtX = nextX;
		}
	}
	
	public void layOut() {
		synchronized(gt.gnodes){
		    GraphNode gnRoot = null;
		    if(rcRoot != null)
		        gnRoot = (GraphNode)gt.nodesMap.get(rcRoot);
			if(gnRoot != null){
				stack.addLast(gnRoot);
				handled.add(gnRoot);
				LayeredTreeNode root = new LayeredTreeNode(gnRoot);
				maxLevels = buildLayeredTree(root, 0);
				double incrHeight = 2.0 / (maxLevels + 1);
				assignPositions(root, -1, 1, -1, incrHeight);
			}
			setHandledFlag();
			zoomHandledNodes();
			layUnhandledNodes();
			stack.clear();
		}
	}

	public void finish() {
		// TODO Auto-generated method stub
	}

	/** decide if we should show the unhandled nodes */
	public void setHandledFlag(){
	    for(Iterator gnit = gt.gnodes.iterator(); gnit.hasNext(); ){
	        GraphNode gn = (GraphNode) gnit.next();
	        gn.rcnode.isLayoutHandled = handled.contains(gn);
	    }
	}

}
