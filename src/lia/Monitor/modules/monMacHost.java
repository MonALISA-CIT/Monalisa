/*
 * $Id: monMacHost.java 6865 2010-10-10 10:03:16Z ramiro $
 */
package lia.Monitor.modules;

import java.util.Vector;

import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.DataArray;
import lia.util.ntp.NTPDate;

/**
 * Mac OSX host monitoring module, to be put under *Master in myFarm.conf
 * 
 * @author costing
 * @since Jun 16, 2008, ML 1.8.7
 */
public class monMacHost extends lia.util.DynamicThreadPoll.SchJob implements MonitoringModule {

	/**
	 * some serial version
	 */
	private static final long	serialVersionUID	= 1L;

	/**
	 * Module information
	 */
	private MonModuleInfo info;
	
	/**
	 * Pointer to the MNode that we belong to
	 */
	private MNode node;
	
	/**
	 * Simple constructor
	 */
	public monMacHost(){
		info = new MonModuleInfo();
		info.name = "monMacHost"; // -> works only with java 1.5+ this.getClass().getSimpleName();
	}
	
	public Object doProcess() throws Exception {
		// for instant values and rates
		final Result r = new Result();
	
		// for strings
		final eResult er = new eResult();

		// we return both
		final Vector v = new Vector(2);

		// common components
		r.FarmName = er.FarmName = getFarmName();
		r.ClusterName = er.ClusterName = getClusterName();
		r.NodeName = er.NodeName = getNode().getName();
		r.Module = er.Module = getTaskName();
		r.time = er.time = NTPDate.currentTimeMillis();
		
		// double values
		final DataArray da = MacHostPropertiesMonitor.getData();
		
		if (da.size()>0){
			final String[] params = da.getSortedParameters();
		
			for (int i=0; i<params.length; i++){
				final String sParam = params[i];
				final double value = da.getParam(sParam);
			
				r.addSet(sParam, value);
			}
			
			v.add(r);
		}
		
		// String values
		er.addSet("os_type", System.getProperty("os.name")+" "+System.getProperty("os.version"));
		er.addSet("platform", System.getProperty("os.arch"));

		v.add(er);

		return v;
	}
	
	public String[] ResTypes() {
		return new String[0];
	}


	public String getClusterName() {
		return this.node.getClusterName();
	}

	public String getFarmName() {
		return this.node.getFarmName();
	}

	public MonModuleInfo getInfo() {
		return this.info;
	}

	public MNode getNode() {
		return this.node;
	}

	public String getOsName() {
		return System.getProperty("os.name");
	}

	public String getTaskName() {
		return this.info.getName();
	}

	public MonModuleInfo init(final MNode mnode, final String args) {
		this.node = mnode ;
		
		return this.info;
	}
	
	public boolean isRepetitive() {
		return true;
	}

	/**
	 * Debugging method
	 * 
	 * @param args
	 * @throws Exception 
	 */
	public static void main(final String[] args) throws Exception {
		final monMacHost mac = new monMacHost();
		
		final MFarm farm = new MFarm("Farm");
		final MCluster cluster = new MCluster("Cluster", farm);
		final MNode node = new MNode("Node", cluster, farm);
		
		mac.init(node, "");
		
		while (true){
			Vector v = (Vector) mac.doProcess(); 
			
			for (int i=0; i<v.size(); i++)
				System.err.println(v.get(i));
			
			Thread.sleep(60000);
		}
	}
	
}
