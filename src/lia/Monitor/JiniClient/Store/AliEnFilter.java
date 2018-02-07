package lia.Monitor.JiniClient.Store;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.Vector;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ExtendedResult;
import lia.Monitor.monitor.Result;
import lia.util.ntp.NTPDate;

/**
 * This implements the repository filter for AliEn jobs, nodes, SEs and 
 * traffic monitoring. Data from all sites is summed, being produced
 * results like the following:
 * _TOTALS_/Site_Jobs_Summary/{node}/{param}; for example node=jobs; param=RUNNING_jobs
 * _TOTALS_/Site_Nodes_Summary/{node}/{param}; for example param=NoCPUs
 * _TOTALS_/SE_Traffic_Summary/_TOTALS_/{param}; for example param=transf_rd_mbytes - for all SEs
 * _TOTALS_/SE_AliEnTraffic_Summary/stuff
 * _TOTALS_/Site_SE_AliEnTraffic_Summary/stuff
 * _TOTALS_/Site_Traffic_Summary/Incoming__TOTALS_/{param}; for example param=transf_mbytes - sum of incoming traffic
 */

public class AliEnFilter implements Filter, Observer {

	/** current totals for all farms will be put here */
	private final HashMap<String, HashMap<String, HashMap<String, Double>>> hmTotalClusters = new HashMap<String, HashMap<String, HashMap<String, Double>>>();
	
	/** current totals for all groups will be here */
	private final HashMap<String, HashMap<String, HashMap<String, Double>>> hmGroupClusters = new HashMap<String, HashMap<String, HashMap<String, Double>>>(); 
	
	/** current summed keys (f/c/n/p), in the same "flush" */
	private final HashSet<String> hsSummedKeys = new HashSet<String>();

	/** extra values for a f/c/n/p in the same "flush" will be here */
	private final HashMap<String, Double> hmCrtExtraValues = new HashMap<String, Double>();
	
	/** extra values for a f/c/n/p in the previous "flush" will be put here */
	private final HashMap<String, Double> hmLastExtraValues = new HashMap<String, Double>();

	/** received & computed rates will be stored here as (f/c/n/p key, recv. time) pairs */   
	private final HashMap<String, RRPair> hmRecentRates = new HashMap<String, RRPair>();
	
	/** how many flushes were done; this is used to remove old rates from hmRecentRates */
	private long rrCount = 0;
	
	/** temp to store the nodes that are going to be summed during sumResult phase */ 
	private final Vector<String> tmpNodes = new Vector<String>();
	
	/** the clusters we want to aggregate from the farms */
	private static final HashSet<String> hsInterestingData = new HashSet<String>();
	static {
		hsInterestingData.add("Site_Jobs_Summary");
		hsInterestingData.add("Site_Nodes_Summary");
		hsInterestingData.add("SE_Traffic_Summary");
		hsInterestingData.add("SE_AliEnTraffic_Summary");
		hsInterestingData.add("Site_SE_AliEnTraffic_Summary");
		hsInterestingData.add("Site_Traffic_Summary");
	}
	
	/** the centrally produced data which should be summed as well. For these F/C/N/f, F:=N; N:="sum" */
	private static final HashSet<String> hsInterestingCentralData = new HashSet<String>();
	private static final String centralSite = "CERN";
	static {
		hsInterestingCentralData.add("ALICE_Sites_Jobs_Summary");
	}
	
	/** the group for each farm; Note that each farm is in at most one group! */
	private static final Hashtable<String, String> hmGroupForFarm = new Hashtable<String, String>();
	
	private long lLastFlush = NTPDate.currentTimeMillis(); // when last flush happened
	private static final long FLUSH_TIME = 2 * 60 * 1000; // ~ 2 minutes rates
	private static final String TOTALS = "_TOTALS_"; // total farm name and node-prefix
	private static final String GROUPS = "_GROUPS_"; // group summary farm name.
	
	private boolean debugOn = false;

	/**
	 * 
	 */
	public AliEnFilter(){
		recreateGroupForFarms();
		ServiceGroups.getInstance().addObserver(this);
	}
	
	/** 
	 * This is called by the repos core whenever there is some new data to filter.
	 * Each getRunInterval() it returns the vector of generated results from the given data. 
	 */
	@Override
	public synchronized final Object filterData(final Object o) {
		try{
			Result zeroRate = null;
			if (o instanceof Result || o instanceof ExtendedResult){
				filterResult((Result) o);
				zeroRate = zerofyResultRates((Result) o);
			}
	
			if((hmTotalClusters.size() > 0 || hmGroupClusters.size() > 0) && (NTPDate.currentTimeMillis() - lLastFlush > FLUSH_TIME)){
				Vector<Result> flushed = flush();
				Vector<Result> zeroRates = zerofyVectorRates(flushed);
				Vector<Result> endRates = cleanupRecentRates();
				// now add all the generated values to the produced summaries. ORDER IS IMPORTANT!
				Vector<Result> summaries = new Vector<Result>();
				
				if(zeroRate != null)
					summaries.add(zeroRate);
				
				if(zeroRates != null)
					summaries.addAll(zeroRates);
				
				if(endRates != null)
					summaries.addAll(endRates);
				
				summaries.addAll(flushed); // this has to be last
				
				if(debugOn)
					System.out.println("FLUSHING RESULTS:\n"+summaries);
				
				return summaries;
			}
			return zeroRate;
		}catch(Throwable t){
			t.printStackTrace();
			return null;
		}
	}

	/** 
	 * Analyze a Result or an ExtendedResult and put the aggregated results
	 * in hmCluster hash. Also, here are created totals for all nodes in the same
	 * cluster. For Site_Traffic_Summary special totals are computed
	 * for the Incomming_, Outgoing_, Internal_ and Inter-Site values.
	 */
	private void filterResult(final Result rToFilter) {
		Result r = rToFilter;
		
		if((r.FarmName == null) 
				|| (r.FarmName.equals(TOTALS))
				|| (r.ClusterName == null))
			return;
		
		final String cluster = r.ClusterName;

		if(centralSite.equals(r.FarmName) && hsInterestingCentralData.contains(cluster)){
			// we are going to simulate that the global results received from CERN, concerning
			// all the sites (as nodes) are coming in fact from the farms by creating another result
			// and using it for the summaries
			if(r.NodeName.equals(TOTALS))
				return;
			Result rr = new Result(r.NodeName, cluster, "sum", r.Module, r.param_name, r.param);
			r = rr;
		}else if(! hsInterestingData.contains(cluster))
			return; // skip non-interesting results

		final String node = r.NodeName;
		debugOn = "true".equals(AppConfig.getProperty("lia.Monitor.JiniClient.Store.AliEnFilter.debug", "false"));
		
		if(debugOn)
			System.out.println("FilterResult:"+r);
		
		tmpNodes.clear();
		// for this cluster/node, compute the sum for all farms
		tmpNodes.add(node); // it is important for this to be the first
		// for all nodes in this cluster, compute the sum for all farms
		tmpNodes.add(TOTALS);
		// for AliEn Site Traffic summary, compute the specific totals for 
		// incomming, outgoing, internal and inter site traffic with data from all farms
		if(cluster.equals("Site_Traffic_Summary")){
			if(node.startsWith("Incoming_")){
				tmpNodes.add("Incoming_"+TOTALS);
			}else if(node.startsWith("Outgoing_")){
				tmpNodes.add("Outgoing_"+TOTALS);
			}else if(node.startsWith("Internal_")){
				tmpNodes.add("Internal_"+TOTALS);
			}else if(node.indexOf('-') != -1){
				tmpNodes.add(TOTALS+"Inter-site");
			}
		}
		
		sumResult(r, tmpNodes);
	}
	
	/** add the given Result to the total hashes for all the given nodes */
	private void sumResult(final Result r, final Vector<String> nodesToSum){
		final String farm = r.FarmName;
		final String cluster = r.ClusterName;
		for(int i = 0; i < r.param_name.length; i++){
			final String paramName = r.param_name[i];
			final double paramValue = r.param[i];
			
			// when analyzing the first node (i.e. the real farm/cluster/node/param of the result):
			// - if its key isn't already in hsSummedKeys, add the other nodes regardless of the presence of their keys
			// - else store it in hmCrtExtraValues; add the rest of nodes to the value of hmCrtExtraValues
			boolean isFirstNode = true;
			boolean summedFirstNode = false;
			for(final Iterator<String> nit = nodesToSum.iterator(); nit.hasNext(); ){
				final String node = nit.next();
				final String key = farm+'\t'+cluster+'\t'+node+'\t'+paramName;
				
				if(isFirstNode){
					// currently analyzing first node
					if(! hsSummedKeys.contains(key)){
						// sum it and set the key
						summedFirstNode = true;
						hsSummedKeys.add(key);
						sumValue(hmTotalClusters, cluster, node, paramName, paramValue);
						sumGroupValue(hmGroupClusters, getGroup(farm), cluster+"_", node, paramName, paramValue);
					}else{
						// store it in the extra values
						summedFirstNode = false;
						if(debugOn){
							if(hmCrtExtraValues.get(key) == null)
								System.out.println("Keep extraVal: "+key+"="+paramValue);
							else
								System.out.println("Overwrite extraVal: "+key+"="+paramValue);
						}
						hmCrtExtraValues.put(key, Double.valueOf(paramValue));
					}
				}else{
					// not the first node anymore
					if(summedFirstNode){
						// the first node was summed; we just sum this
						hsSummedKeys.add(key);
						sumValue(hmTotalClusters, cluster, node, paramName, paramValue);
						// it doesn't make sense to sum to GROUPS for the other nodes here (TOTALS & stuff)
					}else{
						// the first node was put in extraValues
						// We have to add this to the crt extraValue
						final Double prevVal = hmCrtExtraValues.get(key);
						if(prevVal == null)
							hmCrtExtraValues.put(key, Double.valueOf(paramValue));
						else
							hmCrtExtraValues.put(key, Double.valueOf(prevVal.doubleValue() + paramValue));
						if(debugOn)
							System.out.println("Adding extraVal: "+key+"="+paramValue);
					}
				}
				// not first node anymore
				isFirstNode = false;
			}
		}
	}
	
	/** 
	 * just add to the given hmSummaryClusters the new value
	 * In fact, the operation is most of the times "sum", for a 'min' node it 
	 * is minimum; for 'max' node it's maximum of the previous value and the 
	 * currently given value.
	 * TODO: for 'med' node it should compute the average... i.e. diving the sum 
	 * to the number of values in the flush stage.
	 */
	private void sumValue(final HashMap<String, HashMap<String, HashMap<String, Double>>> hmSummaryClusters, final String cluster, final String node, final String paramName, final double value){
		HashMap<String, HashMap<String, Double>> hmNodes = hmSummaryClusters.get(cluster);
		if(hmNodes == null){
			hmNodes = new HashMap<String, HashMap<String, Double>>();
			hmSummaryClusters.put(cluster, hmNodes);
		}
		
		HashMap<String, Double> hmParams = hmNodes.get(node);
		if(hmParams == null){
			hmParams = new HashMap<String, Double>();
			hmNodes.put(node, hmParams);
		}
		
		double v = value;
		
		if(Double.isNaN(v))
			v = 0;
		
		Double dVal = hmParams.get(paramName);
		if(dVal == null)
			dVal = Double.valueOf(v);
		else{
			if(node.equals("min") || node.endsWith("_min"))
				dVal = Double.valueOf(Math.min(dVal.doubleValue(), v));
			else if(node.equals("max") || node.endsWith("_max"))
				dVal = Double.valueOf(Math.max(dVal.doubleValue(), v));
			else
				dVal = Double.valueOf(dVal.doubleValue() + v);	
		}
			
		if(debugOn && cluster.equals("Site_Jobs_Summary") && (paramName.startsWith("DONE_jobs") || paramName.equals("RUNNING_jobs")))
			System.out.println("Aggregating totals: "+cluster+"/"+node+"/"+paramName+" = "+v);
		hmParams.put(paramName, dVal);
	}

	/** For Site_Traffic_Summary/Incoming_$SITE also generate summaries based on getGroup($SITE) */
	private void sumGroupValue(HashMap<String, HashMap<String, HashMap<String, Double>>> hmSummaryClusters, String farmGroup, String cluster, String node, String paramName, double value){
		sumValue(hmSummaryClusters, farmGroup, cluster+node, paramName, value);
		if(cluster.equals("Site_Traffic_Summary_")){
			int idx_ = node.indexOf("_");
			if(idx_ != -1){
				String direction = node.substring(0, idx_);
				if(direction.equals("Incoming") 
						|| direction.equals("Outgoing") 
						|| direction.equals("Internal")){
					String peerFarm = node.substring(idx_+1);
					String peerGroup = getGroup(peerFarm);
					if(! peerGroup.equals(peerFarm)){ // otherwise it would sum twice for peerFarm
						sumValue(hmSummaryClusters, farmGroup, cluster+direction+"_"+peerGroup, paramName, value);
					}
				}
			}
		}
	}
	
	/** this is called whenever the farms' groups change */ 
	@Override
	public void update(Observable o, Object arg) {
		recreateGroupForFarms();
	}
	/** recreate the groups to which each farm belongs */
	private void recreateGroupForFarms(){
		synchronized (hmGroupForFarm) {
			if(debugOn)
				System.out.println("Recreating Groups for Farms ...");
			hmGroupForFarm.clear();
			for(final Iterator<Map.Entry<String, Set<String>>> gfit = ServiceGroups.getInstance().getGroups().entrySet().iterator(); gfit.hasNext(); ){
				final Map.Entry<String, Set<String>> gfme = gfit.next();
				final String group = gfme.getKey();
				final Set<String> sFarms = gfme.getValue();
				for(final String farm: sFarms){
					hmGroupForFarm.put(farm, group);
				}
			}
		}
	}
	
	/** 
	 * For the given farm, return its group or, if it doesn't belong to any group,
	 * return the given name.
	 */ 
	private static String getGroup(String farm){
		String group = hmGroupForFarm.get(farm);
		return (group != null ? group : farm);
	}
	
	/**
	 * Convert all the aggregated data in hmClusters hashes into a Vector of Results
	 * returning them to the caller. 
	 */
	private Vector<Result> flush() {
		Vector<Result> vResult = new Vector<Result>();
		lLastFlush = NTPDate.currentTimeMillis();

		// add, if needed, the extra values from the LastExtraValues hashmap
		for(Iterator<Map.Entry<String, Double>> kit = hmLastExtraValues.entrySet().iterator(); kit.hasNext(); ){
			Map.Entry<String, Double> meLastValue = kit.next();
			String key = meLastValue.getKey();
			Double value = meLastValue.getValue(); 
			if((value != null) && (! hsSummedKeys.contains(key))){
				String [] path = key.split("\t");
				String farm = path[0]; // not used
				String cluster = path[1];
				String node = path[2];
				String paramName = path[3];
				if(debugOn)
					System.out.println("Adding lastExtraValue: "+key+" = "+value);
				sumValue(hmTotalClusters, cluster, node, paramName, value.doubleValue());
				if(node.indexOf(TOTALS) == -1){
					// add only the non _TOTALS_ things
					sumGroupValue(hmGroupClusters, getGroup(farm), cluster+"_", node, paramName, value.doubleValue());
				}
			}
		}
		flushClusters(vResult, hmTotalClusters, TOTALS);
		flushClusters(vResult, hmGroupClusters, GROUPS);
		// clean the list ok summed keys
		hsSummedKeys.clear();
		// move CrtExtraValues to LastExtraValues 
		hmLastExtraValues.clear();
		hmLastExtraValues.putAll(hmCrtExtraValues);
		hmCrtExtraValues.clear();
		return vResult;
	}
	
	/** Flush the summaries for the given hmClusters, with the given farmName */
	private void flushClusters(final Vector<Result> vResult, final HashMap<String, HashMap<String, HashMap<String, Double>>> hmSummaryClusters, final String farmName){
		for(final Iterator<Map.Entry<String, HashMap<String, HashMap<String, Double>>>> cit = hmSummaryClusters.entrySet().iterator(); cit.hasNext();){
			final Map.Entry<String, HashMap<String, HashMap<String, Double>>> cme = cit.next();
			final String cluster = cme.getKey();
			final HashMap<String, HashMap<String, Double>> hmNodes = cme.getValue();
			
			for(Iterator<Map.Entry<String, HashMap<String, Double>>> nit = hmNodes.entrySet().iterator(); nit.hasNext(); ){
				final Map.Entry<String, HashMap<String, Double>> nme = nit.next();
				final String node = nme.getKey();
				final HashMap<String, Double> hmParams = nme.getValue();

				final Result r = new Result();
				r.FarmName = farmName;
				r.ClusterName = cluster;
				r.NodeName = node;
				r.time = NTPDate.currentTimeMillis();

				for(Iterator<Map.Entry<String, Double>> pit = hmParams.entrySet().iterator(); pit.hasNext(); ){
					final Map.Entry<String, Double> pme = pit.next();
					final String paramName = pme.getKey();
					r.addSet(paramName, pme.getValue().doubleValue());
				}
				if(debugOn && cluster.equals("Site_Jobs_Summary"))
						System.out.println("Flushing result:"+r);
				vResult.add(r);
				hmParams.clear();  // prepare for the next summary
			}
			hmNodes.clear();
		}
		hmSummaryClusters.clear();
	}
	
	/** 
	 * For a given vector of results, run zerofyRates on each of them, 
	 * potentially producing a vector with zerofied rates. 
	 */
	private Vector<Result> zerofyVectorRates(Collection<Result> vr){
		Vector<Result> results = null;
		
		for(final Result rit: vr){
			final Result r = zerofyResultRates(rit);
			
			if(r != null){
				if(results == null)
					results = new Vector<Result>();
				
				results.add(r);
			}
		}
		return results;
	}
	
	/**
	 * Given a result, it checks the _R paramNames and if they don't appear 
	 * in hmRecentRates, it will generate a 0 value for those params with a 
	 * time of current time - FLUSH_TIME. If there is no new rate, it returns null. 
	 */
	private Result zerofyResultRates(Result r){
		Result rr = null;
		for(int i=0; i<r.param_name.length; i++){
			String paramName = r.param_name[i];
			if(paramName.endsWith("_R")){
				// so this parameter is a rate; we have to analyze it
				final String key = r.FarmName+'\t'+r.ClusterName+'\t'+r.NodeName+'\t'+paramName;
				final Object prevTime = hmRecentRates.put(key, new RRPair(rrCount, r.time));
				
				if(prevTime == null){
					// it is unknown in the recent rates hash -> we have to zerofy it
					if(rr == null){
						rr = new Result(r.FarmName, r.ClusterName, r.NodeName, "AliEnFilter", new String[0]);
						rr.time = r.time - FLUSH_TIME;
					}
					rr.addSet(paramName, 0);
				}
			}
		}
		if(rr != null && debugOn)
			System.out.println("Starting rates:"+rr);
		return rr;
	}

	/**
	 * This is called each flush and its purpose is to cleanup the hmRecentRates 
	 * hash of old series (that are considered as ended). At the same time, it is
	 * created a result with a zero value for that rate.
	 */
	private Vector<Result> cleanupRecentRates(){
		Vector<Result> endRates = null;
		
		rrCount++;
		for(Iterator<Map.Entry<String, RRPair>> rrit = hmRecentRates.entrySet().iterator(); rrit.hasNext(); ){
			Map.Entry<String, RRPair> rrme = rrit.next();
			String key = rrme.getKey();
			RRPair rrPair = rrme.getValue();
			if(rrPair.rrCount < rrCount - 1){
				rrit.remove();
				// this rate serie has missed a flush; consider it ended. 
				// add a zero with the time of last result + FLUSH_TIME
				String [] path = key.split("\t"); 
				Result r = new Result(path[0], path[1], path[2], "AliEnFilter", new String [] { path[3]} );
				r.time = rrPair.rTime + FLUSH_TIME;
				if(debugOn)
					System.out.println("Cleaning up rate:"+r);
				if(endRates == null)
					endRates = new Vector<Result>();
				
				endRates.add(r);
			}
		}
		return endRates;
	}

	/** 
	 * We need this class because we cannot do the expiration of series on a time based
	 * policy because results come from different MLs with different current times.
	 * 
	 * We also need the result time to create a end of series result with a proper time. */
	private class RRPair {
		@SuppressWarnings("hiding")
		long rrCount;	// refresh rate count
		long rTime;		// result time 
		
		public RRPair(long rrCount, long rTime){
			this.rrCount = rrCount;
			this.rTime = rTime;
		}
	}
}
