package lia.Monitor.JiniClient.CommonGUI.OSG.GraphAlgs;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphTopology;

public class GridLayoutAlgorithm extends lia.Monitor.JiniClient.CommonGUI.Gmap.GridLayoutAlgorithm {

	public GridLayoutAlgorithm(GraphTopology gt) {
		super(gt);
	}

	public void layOut() {
		// select handled nodes first
	    handled.clear();
	    // reorder the nodes in gt.gnodes so that they are alphabetically sorted
	    Collections.sort(gt.gnodes, new Comparator() {
	        public int compare(Object o1, Object o2) {
	            return ((GraphNode)o1).rcnode.UnitName.compareToIgnoreCase(((GraphNode)o2).rcnode.UnitName);
	        }
	    });
        for(Iterator i = gt.gnodes.iterator(); i.hasNext(); ) {
            GraphNode gnode = (GraphNode)i.next();
            if(! handled.contains(gnode))
                handled.add(gnode);
        }
		if(handled.size() > 0){
		    Collections.sort(handled, new Comparator() {
		        public int compare(Object o1, Object o2) {
		            return ((GraphNode)o1).rcnode.UnitName.compareToIgnoreCase(((GraphNode)o2).rcnode.UnitName);
		        }
		    });
			int nrCol = (int) Math.round(Math.sqrt(handled.size()));
			int nrLin = handled.size() / nrCol;
			for(int i=0; i<handled.size(); i++){
				int lin = i / nrCol;
				int col = i % nrCol;
				GraphNode gn = (GraphNode) handled.get(i);
				double x = -0.5 + col*1.0/(nrCol-1);
				double y = -0.5 + lin*1.0/nrLin;
				gn.pos.setLocation(x, y);
			}
		}
        setHandledFlag();
		zoomHandledNodes();
		layUnhandledNodes();
	}
}
