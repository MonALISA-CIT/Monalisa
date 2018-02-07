package lia.Monitor.JiniClient.CommonGUI.OSG;

/**
 * @author florinpop
 *
 */

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.Buffer;
import lia.Monitor.tcpClient.MLSerClient;
import lia.Monitor.tcpClient.ResultProcesserInterface;
import net.jini.core.lookup.ServiceID;
import plot.math.MLSeries;

/**
 * Class that handles the current Jobs Status.
 */
public class OSGJOBHelper implements LocalDataFarmClient, ResultProcesserInterface {

	public Vector currentNodes; // the current rcNodes
	
	volatile Map<ServiceID, rcNode> hnodes;
	long realtimeStart = - 180 * 60 * 1000; // last 3 houres
	
	OSGComputer osgC;
	HashMap collectedValues;
	HashMap collectedSeries;
	
	private Buffer buffer;
	OSGPanel owner;
	
	String[] collectedParams = new String[] {
			"RunningJobs",
			"IdleJobs",
			"CPUTime_R", 
			"FinishedJobs_R",
			"FinishedJobs_Success_R",
			"FinishedJobs_Error_R"
	};
		
	/** Constructor */
	public OSGJOBHelper(OSGPanel owner) {
		
		this.owner = owner;
		currentNodes = new Vector();
		collectedSeries = new HashMap();
		//voCpuValues = new HashMap();
		collectedValues = new HashMap();
		for(int i = 0; i < collectedParams.length; i++){
			HashMap values = new HashMap();
			collectedValues.put(collectedParams[i], values);
		}
		
		osgC = new OSGComputer();
		
		buffer = new Buffer(this, "ML (Result Buffer for result of OSGJOBHelper)");
		buffer.start();
	}
	
	/** once a new node is found send a predicate for osg vo's*/
	public void addNode(rcNode n) {
		monPredicate pred1 = new monPredicate("*", "osgVO_JOBS", "*", realtimeStart, -1, new String[] { "RunningJobs", "IdleJobs"}, null);
		monPredicate pred2 = new monPredicate("*", "osgVO_JOBS_Rates", "*", realtimeStart, -1, new String[] {"CPUTime_R", "FinishedJobs_R", "FinishedJobs_Success_R", "FinishedJobs_Error_R"}, null);
		n.client.addLocalClient(this, pred1);
		n.client.addLocalClient(this, pred2);
		currentNodes.add(n);
		//System.out.println(n.toString());
	}
	
	/** if a node is no longer available, deregister for results */
	public void deleteNode(rcNode n) {
		
		if (currentNodes.contains(n)) {
			n.client.deleteLocalClient(this);
			currentNodes.remove(n);
		}
		for(int i = 0; i < collectedParams.length; i++){
			HashMap values = (HashMap)collectedValues.get(collectedParams[i]);
			values.remove(n);
			collectedValues.put(collectedParams[i],values);
		}		
	}
	
	/** set the current nodes */
	public synchronized void setNodes(Map<ServiceID, rcNode> hnodes) {
		
		this.hnodes = hnodes;
	}
	
	/** called when a new result appear */
	public void newFarmResult(MLSerClient client, Object res) {
		
		buffer.newFarmResult(client, res);
	}
	
	public void process(MLSerClient client, Object res) {
		
		if (res instanceof Result) {
			rcNode n = null;
			synchronized (this) {
				n = (rcNode)hnodes.get(client.tClientID);
			}
			if (n != null)
				process(n, (Result)res);
		} else if (res instanceof Vector) {
			Vector v = (Vector)res;
			for (int i=0; i<v.size(); i++)
				process(client, v.get(i));
		}
	}
	
	/** deal with the actual result */
	private synchronized void process(rcNode node, Result res) {
		
		if (res.param_name == null || res.param == null) return;
		if (res.param_name == null || res.param_name.length == 0) return;
		
		HashMap voSeries = (HashMap)collectedSeries.get(node);
		if(voSeries==null){
			voSeries = new HashMap();
		}
		
		HashMap series = (HashMap)voSeries.get(res.NodeName);
		if(series==null){
			series = new HashMap();
		}
		
		for (int i=0; i<res.param_name.length; i++) {
			if(res.param_name[i].indexOf("_R")>0){
				MLSeries s = (MLSeries)series.get(res.param_name[i]);
				if(s==null){
					s = new MLSeries(res.NodeName+"_"+res.param_name[i]);
				}
				s.add(res.time, res.param[i]);
				osgC.updateSeries(s, -realtimeStart);
				series.put(res.param_name[i], s);
			}
			HashMap values = (HashMap)collectedValues.get(res.param_name[i]);
			if(values == null) continue;
			Vector v = null;
			if (values.containsKey(node)){
				v = (Vector)values.get(node);
			} else {
				v = new Vector();
			}
			for (int k=0; k<v.size(); k++) {
				Object[] obj = (Object[])v.get(k);
				if (obj[0].equals(res.NodeName)) {
					v.remove(k);
					k--;
				}
			}
			v.add(new Object[] { res.NodeName, Double.valueOf(res.param[i]), Long.valueOf(res.time) });
			values.put(node, v);
			collectedValues.put(res.param_name[i], values);
		}
		voSeries.put(res.NodeName, series);
		collectedSeries.put(node,voSeries);
	}
	
	public synchronized HashMap requestJobs(Vector nodes) {
		
		if (nodes == null || nodes.size() == 0) return new HashMap();
		
		HashMap result = new HashMap();
		
		boolean isData = false;
		
		for (int i=0; i<nodes.size(); i++) {
			rcNode n = (rcNode)nodes.get(i);
			if(((Vector)n.client.farm.getModuleList()).contains("monOsgVoJobs")){
				Vector jr = (Vector) ((HashMap)collectedValues.get("RunningJobs")).get(n);
				Vector ji = (Vector) ((HashMap)collectedValues.get("IdleJobs")).get(n);
				double rpie[] = new double[] { 0.0, 0.0 };
				if (jr != null) {
					isData = true;
					for(int j = 0; j<jr.size(); j++){
						Object []jr_com = (Object [])jr.get(j);
						rpie[0]+=((Double)jr_com[1]).doubleValue();
					}
				}
				if (ji != null){
					isData = true;
					for(int j = 0; j<ji.size(); j++){
						Object []ji_com = (Object [])ji.get(j);
						rpie[1]+=((Double)ji_com[1]).doubleValue();
					}
				}
				
				if(isData==true)
					result.put(n, rpie);
			}
		}
		return result;
	}
	
	public synchronized HashMap requestFinishedJobs(Vector nodes) {
		
		if (nodes == null || nodes.size() == 0) return new HashMap();
		
		HashMap result = new HashMap();
		MLSeries series;
		
		boolean isData = false;
		
		for (int i=0; i<nodes.size(); i++) {
			rcNode n = (rcNode)nodes.get(i);
			if(((Vector)n.client.farm.getModuleList()).contains("monOsgVoJobs")){
				Vector fj = (Vector) ((HashMap)collectedValues.get("FinishedJobs_R")).get(n);
				Vector fjs = (Vector) ((HashMap)collectedValues.get("FinishedJobs_Success_R")).get(n);
				Vector fje = (Vector) ((HashMap)collectedValues.get("FinishedJobs_Error_R")).get(n);
				HashMap ss = (HashMap)collectedSeries.get(n);
				double rpie[] = new double[] { 0.0, 0.0, 0.0 };
				if (fj != null) {
					isData = true;
					for(int j = 0; j<fj.size(); j++){
						Object []jr_com = (Object [])fj.get(j);
						series = (MLSeries)( (HashMap) ss.get(jr_com[0])).get("FinishedJobs_R");
						Double integralValue = Double.valueOf(osgC.getOSGIntegral(series, 1024, owner.historyTime));
						rpie[0] += integralValue.doubleValue();
						//rpie[0]+=((Double)jr_com[1]).doubleValue();
					}
				}
				if (fjs != null){
					isData = true;
					for(int j = 0; j<fjs.size(); j++){
						Object []js_com = (Object [])fjs.get(j);
						series = (MLSeries)( (HashMap) ss.get(js_com[0])).get("FinishedJobs_Success_R");
						Double integralValue = Double.valueOf(osgC.getOSGIntegral(series, 1024, owner.historyTime));
						rpie[1] += integralValue.doubleValue();
						//rpie[1]+=((Double)js_com[1]).doubleValue();
					}
				}
				if (fje != null){
					isData = true;
					for(int j = 0; j<fje.size(); j++){
						Object []je_com = (Object [])fje.get(j);
						series = (MLSeries)( (HashMap) ss.get(je_com[0])).get("FinishedJobs_Error_R");
						Double integralValue = Double.valueOf(osgC.getOSGIntegral(series, 1024, owner.historyTime));
						rpie[2] += integralValue.doubleValue();
						//rpie[2]+=((Double)je_com[1]).doubleValue();
					}
				}
				
				if(isData==true)
					result.put(n, rpie);
			}
		}
		return result;
	}

	public synchronized HashMap requestCPUTime(Vector nodes) {
		
		if (nodes == null || nodes.size() == 0) return new HashMap();
		
		HashMap result = new HashMap();
		MLSeries series;
		
		for (int i=0; i<nodes.size(); i++) {
			rcNode n = (rcNode)nodes.get(i);
			double totalCPU = 0.0;
			Vector v = (Vector) ((HashMap)collectedValues.get("CPUTime_R")).get(n);
			HashMap ss = (HashMap)collectedSeries.get(n);
			HashMap info = new HashMap();

			if (v != null) {
				for(int j = 0; j<v.size(); j++){
					Object []v_com = (Object [])v.get(j);
					
					//totalCPU +=((Double)v_com[1]).doubleValue();
					//series = (MLSeries)((HashMap)collectedSeries.get(v_com[0])).get("CPUTime_R");
					//info.put(v_com[0], v_com[1]);
					
					series = (MLSeries)( (HashMap) ss.get(v_com[0])).get("CPUTime_R");
					Double integralValue = Double.valueOf(osgC.getOSGIntegral(series, 1024, owner.historyTime) / 3600.0);
					totalCPU += integralValue.doubleValue();
					info.put(v_com[0], integralValue);
				}
				info.put("Total", Double.valueOf(totalCPU));
				result.put(n, info);
			}
		}
		return result;
	}
	
} // end of class OSGJOBSHelper
