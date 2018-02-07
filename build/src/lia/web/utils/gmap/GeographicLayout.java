package lia.web.utils.gmap;

import java.util.Iterator;
import java.util.Vector;

/**
 * @author costing
 *
 */
public class GeographicLayout implements GraphLayoutAlgorithm {

	public void layout(GraphTopology gt) {
		
		Vector<Node> handled = new Vector<Node>(); 
		for(Iterator<Node> i = gt.gnodes.iterator(); i.hasNext(); ) {
			Node gnode = i.next();
			for(Iterator<Node> pit = gnode.connQuality.keySet().iterator(); pit.hasNext(); ){
				Node peer = pit.next();
				if(! handled.contains(gnode))
					handled.add(gnode);
				if(! handled.contains(peer))
					handled.add(peer);
			}
		}
		for(Iterator<Node> i = gt.gnodes.iterator(); i.hasNext(); ) {
			Node gnode = i.next();
			double lngDelta = failsafeParseDouble(gnode.LONG, -111.15) + 180.0;
			double latDelta = failsafeParseDouble(gnode.LAT, -21.22) + 90.0;
			gnode.x = -1 + Math.pow(lngDelta / (360.0 / 2), 0.2);
			gnode.y = 1 - Math.pow(latDelta / (180.0 / 2), 10);
		}
//		 Rectangle2D.Double area = null;
//		 if(handled.size() == 0)
//		     return;
//		 for(Iterator it=handled.iterator(); it.hasNext();){
//			 Node gn = (Node) it.next();
//			 if(area == null) 
//				 area = new Rectangle2D.Double(gn.x, gn.y, 0, 0);
//			 else
//				 area.add(gn.x, gn.y);
//		 }
//		 double zx = (area.width > 0 ? 1.8 / area.width : 1);
//		 double zy = (area.height > 0 ? 2 / area.height : 1);
//		 double dx = (area.width > 0 ? -0.8 : 0.0);
//		 double dy = (area.height > 0 ? -1.0 : 0.0);
//		 for(Iterator it=handled.iterator(); it.hasNext();){
//		 	Node gn = (Node) it.next();
//		 	gn.x = dx + (gn.x - area.x)*zx;
//			gn.y = dy + (gn.y - area.y)*zy;
//		 }
	}
	
    /**
     * @param value
     * @param failsafe
     * @return the value
     */
    public double failsafeParseDouble(String value, double failsafe){
    	try {
    		return Double.parseDouble(value);
    	} catch ( Throwable t  ){
			System.out.println("For value "+value);
			t.printStackTrace();
    		return failsafe;
    	}
    }
	
} // end of class GeographicLayout

