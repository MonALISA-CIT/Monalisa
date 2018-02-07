package lia.Monitor.JiniClient.CommonGUI.OSG.GraphAlgs;

import java.awt.geom.Point2D;
import java.util.Iterator;

import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphTopology;

public class RandomLayoutAlgorithm extends lia.Monitor.JiniClient.CommonGUI.Gmap.RandomLayoutAlgorithm {

    public RandomLayoutAlgorithm(GraphTopology gt) {
        super(gt);
    }

    public void layOut() {
		synchronized(gt.gnodes){
		    handled.clear();
	        for(Iterator i = gt.gnodes.iterator(); i.hasNext(); ) {
	            GraphNode gnode = (GraphNode)i.next();
	            if(! handled.contains(gnode))
	                handled.add(gnode);
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

} // end of class RandomLayoutAlgorithm

