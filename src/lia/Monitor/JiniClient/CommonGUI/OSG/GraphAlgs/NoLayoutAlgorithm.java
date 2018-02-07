package lia.Monitor.JiniClient.CommonGUI.OSG.GraphAlgs;

import java.util.Iterator;

import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphTopology;
import lia.Monitor.JiniClient.CommonGUI.Gmap.NoLayoutLayoutAlgorithm;

public class NoLayoutAlgorithm extends NoLayoutLayoutAlgorithm {

    public NoLayoutAlgorithm(GraphTopology gt) {
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
		}
		//handled.addAll(gt.gnodes);
        setHandledFlag();
		zoomHandledNodes();
		layUnhandledNodes();
    }
} // end of class NoLayoutAlgorithm
