package lia.Monitor.JiniClient.VRVS3D.Histograms;

import java.util.Enumeration;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.DoubleContainer;
import lia.Monitor.JiniClient.CommonGUI.RCBasedHistogram;
import lia.Monitor.JiniClient.CommonGUI.rcNode;

public class Traff extends RCBasedHistogram {
	
	static String[] labels = { "IN", "OUT" };
	
	public Traff(){
		super("I/O Traffic of Reflectors", "Reflectors", "Mb/s", labels, false);
		BackgroundWorker.schedule(ttask, 4000, 6000);
	}

	/**
	 * plot rooms, audio and video data
	 */
	protected boolean updateData() {
		for(int i=0; i<crtNodes.size(); i++){
			rcNode n = (rcNode)crtNodes.get(i);
			double in = 0.0, out = 0.0;
			if(n.haux != null){
				synchronized ( n.haux ) {
					for (Enumeration en = n.haux.keys(); en.hasMoreElements();) {
						String key = (String) en.nextElement();
						if ( key.indexOf("_IN")!=-1 ) {
							in += DoubleContainer.getHashValue(n.haux, key);
//							in += ((Double)n.haux.get(key)).doubleValue();
						}
						if ( key.indexOf("_OUT")!=-1 ) {
							out += DoubleContainer.getHashValue(n.haux, key);
//							out += ((Double)n.haux.get(key)).doubleValue();
						}
					}
				}
			}
			setNodeSerValue(n, in, out);
		}
		return false;
	}
}
