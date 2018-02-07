package lia.web.utils.gmap;

import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Vector;

/**
 */
public class GridLayout implements GraphLayoutAlgorithm {
	
	public void layout(GraphTopology gt) {
		
		Collections.sort(gt.gnodes, new Comparator<Node>() {
			public int compare(Node n1, Node n2) {
				return n1.name.compareToIgnoreCase(n2.name);
			}
		});
		
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
		if(handled.size() > 0){
			Collections.sort(handled, new Comparator<Node>() {
				public int compare(Node n1, Node n2) {
					return n1.name.compareToIgnoreCase(n2.name);
				}
			});
			int nrCol = (int) Math.round(Math.sqrt(handled.size()));
			int nrLin = handled.size() / nrCol;
			for(int i=0; i<handled.size(); i++){
				int lin = i / nrCol;
				int col = i % nrCol;
				Node gn = handled.get(i);
				gn.x = -0.5 + col*1.0/(nrCol);
				gn.y = -0.5 + lin*1.0/nrLin;
			}
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
		
		int i = 0;
		for(Iterator<Node> it=gt.gnodes.iterator(); it.hasNext();){
			Node gn = it.next();
			if(handled.contains(gn)) 
				continue;
			gn.y = -1.0 + 2.0 * (i++) / (gt.gnodes.size() - handled.size());
			gn.x = -1.0; 
		}
		
	}
} // end of class GridLayout

