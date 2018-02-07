package lia.Monitor.JiniClient.Farms.CienaMap.layout;

import java.awt.geom.Point2D;
import java.util.Iterator;

public class RandomLayoutAlgorithm extends lia.Monitor.JiniClient.CommonGUI.Gmap.RandomLayoutAlgorithm {

    public RandomLayoutAlgorithm(GraphTopology gt) {
        super(gt);
    }

    public void layOut() {
		synchronized(gt.gnodes){
		    handled.clear();
	        for(Iterator i = gt.gnodes.iterator(); i.hasNext(); ) {
	            GraphNode gnode = (GraphNode)i.next();
	            for(Iterator pit = gnode.neighbors.keySet().iterator(); pit.hasNext(); ){
	                GraphNode peer = (GraphNode)pit.next();
		            if(! handled.contains(gnode))
		                handled.add(gnode);
		            if(! handled.contains(peer))
		                handled.add(peer);
	            }
	        }
	        for(Iterator i = gt.gnodes.iterator(); i.hasNext(); ) {
	            GraphNode gnode = (GraphNode)i.next();
	            Point2D.Double p = gnode.pos;
	            if(handled.contains(gnode)){
	                p.x = Math.random() * (Math.random() < 0.5 ? 1 : -1);
	                p.y = Math.random() * (Math.random() < 0.5 ? 1 : -1);
	            }
	        }
		}
		//handled.addAll(gt.gnodes);
        setHandledFlag();
		zoomHandledNodes();
		layUnhandledNodes();
    }

	public void finish() {
		// not used here
	}
	
	/** decide if we should show the unhandled nodes */
	public void setHandledFlag(){
	    for(Iterator gnit = gt.gnodes.iterator(); gnit.hasNext(); ){
	        GraphNode gn = (GraphNode) gnit.next();
	        gn.rcnode.isLayoutHandled = handled.contains(gn);
	    }
	}

}
