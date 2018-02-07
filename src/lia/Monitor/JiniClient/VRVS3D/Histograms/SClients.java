package lia.Monitor.JiniClient.VRVS3D.Histograms;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.DoubleContainer;
import lia.Monitor.JiniClient.CommonGUI.RCBasedHistogram;
import lia.Monitor.JiniClient.CommonGUI.rcNode;

public class SClients extends RCBasedHistogram {
	
	static String[] labels = { "Rooms", "Audio", "Video" };
	
	public SClients(){
		super("VRVS Clients", "Reflectors", "no", labels, false);
		BackgroundWorker.schedule(ttask, 5000, 6000);
	}

	/**
	 * plot rooms, audio and video data
	 */
	protected boolean updateData() {
		for(int i=0; i<crtNodes.size(); i++){
			rcNode n = (rcNode)crtNodes.get(i);
			double rooms = 0.0, video = 0.0, audio = 0.0;
			if(n.haux != null){
				rooms = Math.max(DoubleContainer.getHashValue(n.haux, "VirtualRooms"), 0.0);
				video = Math.max(DoubleContainer.getHashValue(n.haux, "Video"), 0.0);
				audio = Math.max(DoubleContainer.getHashValue(n.haux, "Audio"), 0.0);
//				Double dval = (Double)n.haux.get("VirtualRooms");
//				if(dval != null)
//					rooms = dval.doubleValue();
//				dval = (Double)n.haux.get("Video");
//				if(dval != null)
//					video = dval.doubleValue();
//				dval = (Double)n.haux.get("Audio");
//				if(dval != null)
//					audio = dval.doubleValue();
			}
			//System.out.println("updateData "+n.UnitName+" "+rooms+" "+audio+" "+video);
			setNodeSerValue(n, rooms, audio, video);
		}
		return false;
	}
}
