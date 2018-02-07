package lia.Monitor.JiniClient.CommonGUI.Gmap;

import java.awt.geom.Point2D;
import java.util.Iterator;

import lia.Monitor.JiniClient.CommonGUI.rcNode;

public class GeographicLayoutAlgorithm extends GraphLayoutAlgorithm {

    public GeographicLayoutAlgorithm(GraphTopology gt) {
        super(gt);
    }

    public void layOut() {
		synchronized(gt.gnodes){
			// select handled nodes first
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
	            rcNode n = gnode.rcnode;
	            double lngDelta = failsafeParseDouble(n.LONG, -111.15) + 180.0;
	            double latDelta = failsafeParseDouble(n.LAT, -21.22) + 90.0;
	            Point2D.Double p = gnode.pos;
	            p.x = -1 + Math.pow(lngDelta / (360.0 / 2), 0.2);
	            p.y = 1 - Math.pow(latDelta / (180.0 / 2), 10);
	            //handled.add(gnode);
	        }
	        setHandledFlag();
			zoomHandledNodes();
			layUnhandledNodes();    
		}
	}

    public double failsafeParseDouble(String value, double failsafe){
    	try {
    		return Double.parseDouble(value);
    	} catch ( Throwable t  ){  
    		return failsafe;
    	}
    }
    
	public void finish() {
		// not used here
	}

}
