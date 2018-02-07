package lia.Monitor.JiniClient.Farms.CienaMap.layout;

import java.util.Iterator;

public class NoLayoutLayoutAlgorithm extends lia.Monitor.JiniClient.CommonGUI.Gmap.NoLayoutLayoutAlgorithm {

    public NoLayoutLayoutAlgorithm(GraphTopology gt) {
        super(gt);
    }

	/** decide if we should show the unhandled nodes */
	public void setHandledFlag(){
	    for(Iterator gnit = gt.gnodes.iterator(); gnit.hasNext(); ){
	        GraphNode gn = (GraphNode) gnit.next();
	        gn.rcnode.isLayoutHandled = handled.contains(gn);
	    }
	}

}
