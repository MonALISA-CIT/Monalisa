package lia.Monitor.JiniClient.Farms.OpticalSwitch.topology;

import java.util.Iterator;

import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphNode;

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
