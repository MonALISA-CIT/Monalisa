package lia.Monitor.JiniClient.Farms.Histograms;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.RCBasedHistogram;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.Gresult;

public class LoadHistoPan extends RCBasedHistogram {
	
	static String[] labels = { ">1.0", "[0.5-1.0]","<0.5" };
	
	public LoadHistoPan(){
		super("Load Distribution of Computing Nodes", "RC name", "Nodes", labels, false);
		BackgroundWorker.schedule(ttask, 3000, 6000);
	}

	/**
	 * plot rooms, audio and video data
	 */
	protected boolean updateData() {
		if ( vnodes==null )
			return false;
		for (int iq = 0; iq < vnodes.size(); iq++) {
			rcNode n = (rcNode) vnodes.elementAt(iq);
			if (n == null)
				continue;
			//int iqc = crtNodes.indexOf(n);			
			Gresult ldx  = (n==null || n.global_param == null?null:(Gresult) n.global_param.get("Load5" ));			
			/*	if (ldx == null ) {					
				 ldx  = (n==null || n.global_param == null?null:(Gresult) n.global_param.get("Load1" ));
				 if( ldx!=null && ldx.ClusterName.indexOf("PBS")==-1 &&  ldx.ClusterName.indexOf("Condor")==-1 ) 
					 ldx=null;					 					 
			}*/
			double free, mid, loaded;
			if (ldx != null) {
				free = ldx.hist[0] + ldx.hist[1];
				mid = ldx.hist[2] + ldx.hist[3];
				loaded = ldx.hist[4];
			}else{
				free = mid = loaded = 0;
			}
			setNodeSerValue(n, loaded, mid, free);
		}
		return false;
	}
}
