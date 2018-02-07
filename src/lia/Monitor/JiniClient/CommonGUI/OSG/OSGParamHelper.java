package lia.Monitor.JiniClient.CommonGUI.OSG;

import java.util.HashMap;
import java.util.Vector;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.Gresult;

/**
 * Class that handles the transforms between parameters requests and actual predicates beeing sent.
 */
public class OSGParamHelper {
	
	public String nodesString	= "Nodes free/busy";
	public String cpuString		= "CPU usr/sys/idl/err";
	public String cpuNoString	= "Number of CPUs";
	public String ioString		= "IO ratio in/out";
	public String jobsString	= "Jobs Running/Idle";
	public String fJobsString	= "Finished Jobs (Succes/Error)";
	public String cpuTimeString	= "Total Jobs CPUTime";
	
	public int maxNodes = Integer.MIN_VALUE;
	
	public OSGParamHelper() {
		
	}
	
	public HashMap requestNodes(Vector nodes) {
		
		if (nodes == null || nodes.size() == 0) return new HashMap();
		
		HashMap result = new HashMap();
		
		for (int i=0; i<nodes.size(); i++) {
			rcNode n = (rcNode)nodes.get(i);
			
			Gresult ldx  = (n==null || n.global_param == null?null:(Gresult) n.global_param.get("Load5"));			

			if (ldx != null && ldx.hist != null) {
				double free = ldx.hist[0] + ldx.hist[1]+ldx.hist[2];
				double busy = ldx.hist[3] + ldx.hist[4];
				result.put(n, new double[] { free, busy, ldx.Nodes});
				if (maxNodes < ldx.Nodes) maxNodes = ldx.Nodes;
			}
		}
		return result;
	}
	
	public HashMap requestCpuNo(Vector nodes) {
		
		if (nodes == null || nodes.size() == 0) return new HashMap();
		
		HashMap result = new HashMap();
		for (int i=0; i<nodes.size(); i++) {
			rcNode n = (rcNode)nodes.get(i);
			Gresult cpuNo = (Gresult) n.global_param.get("NoCPUs");
			
			if (cpuNo == null) {
				result.put(n, new double[] { 0.0 });
			} else {
				double rpie[] = new double[1];
				rpie[0] = cpuNo.sum;
				result.put(n, rpie);
			}
		}
		return result;
	}
	
	public HashMap requestCPU(Vector nodes) {
		
		if (nodes == null || nodes.size() == 0) return new HashMap();
		
		HashMap result = new HashMap();
		for (int i=0; i<nodes.size(); i++) {
			rcNode n = (rcNode)nodes.get(i);
			Gresult usr = (Gresult) n.global_param.get("CPU_usr");
			Gresult sys = (Gresult) n.global_param.get("CPU_sys");
			Gresult nice = (Gresult) n.global_param.get("CPU_nice");
			if ((usr == null) || (nice == null)) {
				result.put(n, new double[] { 0.0, 0.0, 0.0, 0.0 });
			} else {
				double rpie[] = new double[4];

				if (usr.Nodes == usr.TotalNodes) // nodes in error 
					rpie[3] = 0;
				else
					rpie[3] =
						(double) (usr.TotalNodes - usr.Nodes)
							/ (double) usr.TotalNodes;
				rpie[0] =
					(usr.sum + nice.sum) * 0.01 / (double) usr.TotalNodes;
				if (sys != null)
					rpie[1] = sys.sum * 0.01 / (double) usr.TotalNodes;
				else
					rpie[1] = 0.0;
				rpie[2] = 1.0 - rpie[0] - rpie[1] - rpie[3];
				result.put(n, rpie);
			}
		}
		return result;
	}
	
	public HashMap requestIO(Vector nodes) {
		
		if (nodes == null || nodes.size() == 0) return new HashMap();
		
		HashMap result = new HashMap();
		for (int i=0; i<nodes.size(); i++) {
			rcNode n = (rcNode)nodes.get(i);
			Gresult inn = (Gresult) n.global_param.get("TotalIO_Rate_IN");
			Gresult outn = (Gresult) n.global_param.get("TotalIO_Rate_OUT");
			if ((inn == null) || (outn == null)) {
				result.put(n, new double[] {0.0, 0.0});
			} else {
				double rpie[] = new double[2];
				double sum = inn.mean + outn.mean;
				rpie[0] = inn.mean / sum;
				rpie[1] = outn.mean / sum;
				result.put(n, rpie);
			}
		}
		return result;
	}

	/*public HashMap requestDisk(Vector nodes) {
	
		if (nodes == null || nodes.size() == 0) return new HashMap();
		
		HashMap result = new HashMap();
		for (int i=0; i<nodes.size(); i++) {
			rcNode n = (rcNode)nodes.get(i);
			Gresult fd = (Gresult) n.global_param.get("FreeDsk");
			Gresult ud = (Gresult) n.global_param.get("UsedDsk");
			if ((fd == null) || (ud == null)) {
				result.put(n, new double[] { 0.0, 0.0 });
			} else {
				double rpie[] = new double[2];
				double sum = fd.sum + ud.sum;
				rpie[0] = ud.sum / sum;
				rpie[1] = fd.sum / sum;
				result.put(n, rpie);
			}
		}
		return result;
	}*/

} // end of class OSGParamHelper


