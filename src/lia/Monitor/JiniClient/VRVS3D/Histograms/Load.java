package lia.Monitor.JiniClient.VRVS3D.Histograms;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.DoubleContainer;
import lia.Monitor.JiniClient.CommonGUI.RCBasedHistogram;
import lia.Monitor.JiniClient.CommonGUI.rcNode;

public class Load extends RCBasedHistogram {
	
	static String[] labels = { "Load" };
	
	public Load(){
		super("Load Distribution of Reflectors", "Reflectors", "Load", labels, false);
		BackgroundWorker.schedule(ttask, 3000, 6000);
	}

	/**
	 * plot load data
	 */
	protected boolean updateData() {
		for(int i=0; i<crtNodes.size(); i++){
			rcNode n = (rcNode)crtNodes.get(i);
			double load = 0.0;
			if(n.haux != null)
				load = Math.max(DoubleContainer.getHashValue(n.haux, "Load"), 0.0);
			setNodeSerValue(n, load);
		}
		return false;
	}
}
