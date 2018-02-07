package lia.Monitor.JiniClient.CommonGUI.Gmap;

import java.util.Iterator;

public class NoLayoutLayoutAlgorithm extends GraphLayoutAlgorithm {

    public NoLayoutLayoutAlgorithm(GraphTopology gt) {
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
		}
		//handled.addAll(gt.gnodes);
        setHandledFlag();
		zoomHandledNodes();
		layUnhandledNodes();
    }

	public void finish() {
		// not used here
	}

}
