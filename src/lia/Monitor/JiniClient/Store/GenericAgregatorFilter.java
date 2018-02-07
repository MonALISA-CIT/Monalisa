package lia.Monitor.JiniClient.Store;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Pattern;

import lia.Monitor.Store.Fast.IDGenerator;
import lia.Monitor.monitor.ExtendedResult;
import lia.Monitor.monitor.Result;
import lia.util.ntp.NTPDate;

/**
 * @author costing
 * @since long ago :)
 */
public abstract class GenericAgregatorFilter implements Filter {
	private Vector<Result>				vResult		= null;

	private final HashMap<String, HashMap<String, HashMap<String, HashMap<String, Double>>>>		hmTempData	= new HashMap<String, HashMap<String, HashMap<String, HashMap<String, Double>>>>();

	// first run after 1.5 x getRunInterval()
	private long				lLastFlush	= NTPDate.currentTimeMillis() + getRunInterval() / 2;

	/**
	 * Get the clusters on which to make the sums
	 * 
	 * @return map<String, Pattern>
	 */
	public abstract Map<String, Pattern> getRegexClusters();

	/**
	 * Map of <String, List<String>> with the desired parameters
	 * 
	 * @return Map<String, List<String>>
	 */
	public abstract Map<String, List<String>> getInterestingData();

	/**
	 * Get the name of the virtual service.
	 * 
	 * @return any string, usually "_TOTALS_"
	 */
	public abstract String getTotalsName();

	/**
	 * How often should we run the filter?
	 * 
	 * @return time in milliseconds between flushes 
	 */
	public abstract long getRunInterval();
	
	private final int iHistoryNonZeroSkip = getHistoryNonZeroSkip();
	private final boolean bFillGaps = getFillGaps() && iHistoryNonZeroSkip>0;
	
	private final HashMap<String, NonZeroCache> hmLastNonZeroValues = new HashMap<String, NonZeroCache>();
	
	private static final class NonZeroCache {
		
		/**
		 * last non-zero value
		 */
		double dValue;
		
		/**
		 * timeout, in flush operations count
		 */
		int timeout;
		
		/**
		 * for missing records
		 * 
		 * @param d value
		 * @param i timeout
		 */
		public NonZeroCache(final double d, final int i){
			dValue = d;
			timeout = i;
		}
	}
	
	/**
	 * Override this method to put some string in front of the generated parameter names
	 * @return the prefix, if desired
	 */
	public String getParameterPrefix(){
		return "";
	}
	
	@Override
	public synchronized final Object filterData(final Object o) {
		if (o instanceof Result || o instanceof ExtendedResult)
			filterResult((Result) o);
		else if (o instanceof Collection) {
			Collection<?> c = (Collection<?>) o;
			Iterator<?> it = c.iterator();
			Object ot;

			while (it.hasNext()) {
				ot = it.next();

				if (ot instanceof Result || ot instanceof ExtendedResult)
					filterResult((Result) ot);
			}
		}

		if (vResult == null && hmTempData.size() > 0 && NTPDate.currentTimeMillis() - lLastFlush > getRunInterval()) {
			flush();
		}

		if (vResult != null) {
			Vector<Result> vTemp = vResult;
			vResult = null;
			return vTemp;
		}
		
		return null;
	}

	/**
	 * Override this if needed, default returns false.
	 * 
	 * @return whether or not to fill the gaps of non-zero series with the last non-zero value
	 */
	protected boolean getFillGaps() {
		return false;
	}
	
	/**
	 * Override this method to give a set of specific nodes to ignore
	 * 
	 * @return null if there is no node to ignore, or a Set of Strings otherwise
	 */
	protected Set<String> getIgnoreNodes(){
		return null;
	}

	private void filterResult(final Result r) {
		if (
			r.FarmName == null || r.FarmName.equals(getTotalsName()) || 
			r.ClusterName == null || 
			r.NodeName==null || r.NodeName.equals(getTotalsName())
		)
			return;

		Set<String> s = getIgnoreNodes();
		
		if (s!=null && s.contains(r.NodeName))
			return;
		
		String cluster = r.ClusterName;
		Map<String, Pattern> rec = getRegexClusters();
		if (rec != null) {
			// if we use regex clusters, try to find a match among these
			// for the current cluster name. See AlienFilter for an example
			for (Iterator<Map.Entry<String, Pattern>> rit = rec.entrySet().iterator(); rit.hasNext();) {
				Map.Entry<String, Pattern> me = rit.next();
				String key = me.getKey();
				Pattern p = me.getValue();
				if (p != null) {
					if (p.matcher(cluster).matches()) {
						cluster = key;
						break;
					}
				} else {
					if (key.equals(cluster))
						break;
				}
			}
		}
		List<String> l = getInterestingData().get(cluster);

		if (l == null)
			return;

		for (int i = 0; i < r.param_name.length; i++)
			if (l.size() == 0 || l.contains(r.param_name[i])) {
				addToStructure(r.FarmName, r.ClusterName, r.NodeName, r.param_name[i], r.param[i], iHistoryNonZeroSkip>0);
			}
	}
	
	private void addToStructure(final String sFarm, final String sCluster, final String sNode, final String sParam, final double dValue, final boolean bUpdateNonZeroHash){
		HashMap<String, HashMap<String, HashMap<String, Double>>> hmCluster = hmTempData.get(sCluster);

		if (hmCluster == null) {
			hmCluster = new HashMap<String, HashMap<String, HashMap<String, Double>>>();
			hmTempData.put(sCluster, hmCluster);
		}

		HashMap<String, HashMap<String, Double>> hmParams = hmCluster.get(sNode);

		if (hmParams == null) {
			hmParams = new HashMap<String, HashMap<String, Double>>();
			hmCluster.put(sNode, hmParams);
		}

		HashMap<String, Double> hmTemp = hmParams.get(sParam);

		if (hmTemp == null) {
			hmTemp = new HashMap<String, Double>();
			hmParams.put(sParam, hmTemp);
		}

		if (hmTemp.containsKey(sFarm)) {
			// if a farm is configured to send data more often we ignore the extra values
			return;
		}
		
		double d = dValue;
		
		if (bUpdateNonZeroHash){
			final String sResultKey = IDGenerator.generateKey(sFarm, sCluster, sNode, sParam);
			
			final NonZeroCache nzc = hmLastNonZeroValues.get(sResultKey);

			if (d < 1E-4){
				// if there is a previous non-zero value then keep it
				if (nzc!=null)
					d = nzc.dValue;
			}
			else{
				// if a previous entry exists just update it, it's more efficient
				if (nzc!=null){
					nzc.dValue = d;
					nzc.timeout = iHistoryNonZeroSkip;
				}
				else{
					hmLastNonZeroValues.put(sResultKey, new NonZeroCache(d, iHistoryNonZeroSkip));
				}
			}
		}

		hmTemp.put(sFarm, Double.valueOf(d));

		Double dVal = hmTemp.get(getTotalsName());

		if (dVal == null)
			dVal = Double.valueOf(0);

		dVal = Double.valueOf(dVal.doubleValue() + d);

		hmTemp.put(getTotalsName(), dVal);
	}
	/**
	 * Override this method to return true if the filter needs to produce totals also per farm/cluster/parameter
	 * (eg for all the nodes)
	 * 
	 * @return false, override if needed
	 */
	protected boolean getProduceTotalsPerFarms(){
		return false;
	}
	
	/**
	 * Override this method to allow the keeping of the last known non-zero value for a number of flush operations
	 * 
	 * @return the number of flush intervals that a value is kept to it's last non-zero value
	 */
	protected int getHistoryNonZeroSkip(){
		return 0;
	}

	private void flush() {
		final long lNow = NTPDate.currentTimeMillis(); 
		
		lLastFlush = lNow;

		Iterator<Map.Entry<String, HashMap<String, HashMap<String, HashMap<String, Double>>>>> it = hmTempData.entrySet().iterator();

		HashMap<String, Double> hmTotals = new HashMap<String, Double>();
		
		final boolean bProduceTotalPerFarms = getProduceTotalsPerFarms();
		
		HashMap<String, Double> hmFarmTotals = bProduceTotalPerFarms ? new HashMap<String, Double>() : null;
		
		vResult = new Vector<Result>();
		
		if (bFillGaps){
			// fill the gaps with last non-zero value from the hash
			fillGaps();
		}

		// create grand totals
		while (it.hasNext()) {
			Map.Entry<String, HashMap<String, HashMap<String, HashMap<String, Double>>>> me = it.next();

			String sCluster = me.getKey();

			HashMap<String, HashMap<String, HashMap<String, Double>>> hmCluster = me.getValue();

			Iterator<Map.Entry<String, HashMap<String, HashMap<String, Double>>>> it2 = hmCluster.entrySet().iterator();

			while (it2.hasNext()) {
				Map.Entry<String, HashMap<String, HashMap<String, Double>>> me2 = it2.next();

				String sVO = me2.getKey();

				HashMap<String, HashMap<String, Double>> hmParams = me2.getValue();

				Iterator<Map.Entry<String, HashMap<String, Double>>> it3 = hmParams.entrySet().iterator();

				Result r = new Result();
				r.FarmName = getTotalsName();
				r.ClusterName = sCluster;
				r.NodeName = sVO;
				r.time = lNow;

				while (it3.hasNext()) {
					Map.Entry<String, HashMap<String, Double>> me3 = it3.next();

					String sParam = me3.getKey();

					HashMap<String, Double> hmTemp = me3.getValue();

					Double d = hmTemp.get(getTotalsName());

					double dVal = d != null ? d.doubleValue() : 0d;

					r.addSet(getParameterPrefix()+sParam, dVal);

					d = hmTotals.get(sCluster + "\t" + sParam);

					d = Double.valueOf((d == null ? 0d : d.doubleValue()) + dVal);

					hmTotals.put(sCluster + "\t" + sParam, d);
					
					if (bProduceTotalPerFarms){
						hmTemp.remove(getTotalsName());
						
						Iterator<Map.Entry<String, Double>> itTemp = hmTemp.entrySet().iterator();
						
						while (itTemp.hasNext()){
							Map.Entry<String, Double> meTemp = itTemp.next();
							String sFarm = meTemp.getKey();
							d = meTemp.getValue();
							
							dVal = d!=null ? d.doubleValue():0;
							
							String sKey = sFarm+"\t"+sCluster+"\t"+sParam;
							
							d = hmFarmTotals.get(sKey);
							
							hmFarmTotals.put(sKey, Double.valueOf(d!=null ? d.doubleValue()+dVal : dVal));
						}
					}
					
					hmTemp.clear();
				}

				vResult.add(r);
			}
		}

		Iterator<Map.Entry<String, Double>> it2 = hmTotals.entrySet().iterator();

		while (it2.hasNext()) {
			Map.Entry<String, Double> me = it2.next();

			String s = me.getKey();
			Double d = me.getValue();

			String sCluster = s.substring(0, s.indexOf("\t"));
			String sParam = s.substring(s.indexOf("\t") + 1);

			Result r = new Result();
			r.FarmName = getTotalsName();
			r.ClusterName = sCluster;
			r.NodeName = getTotalsName();
			r.time = lNow;
			r.addSet(getParameterPrefix()+sParam, d.doubleValue());

			vResult.add(r);
		}

		if (bProduceTotalPerFarms){
			it2 = hmFarmTotals.entrySet().iterator();
					
			while (it.hasNext()){
				Map.Entry<String, Double> me = it2.next();

				String s = me.getKey();
				Double d = me.getValue();

				String sFarm = s.substring(0, s.indexOf("\t"));
				s = s.substring(s.indexOf("\t") + 1);
				String sCluster = s.substring(0, s.indexOf("\t"));
				String sParam = s.substring(s.indexOf("\t") + 1);

				Result r = new Result();
				r.FarmName = sFarm;
				r.ClusterName = sCluster;
				r.NodeName = getTotalsName();
				r.time = lNow;
				r.addSet(getParameterPrefix()+sParam, d.doubleValue());
				
				vResult.add(r);
			}
		}
		
		if (hmLastNonZeroValues.size()>0){
			final Iterator<Map.Entry<String, NonZeroCache>> it3 = hmLastNonZeroValues.entrySet().iterator();
			
			while (it.hasNext()){
				Map.Entry<String, NonZeroCache> me = it3.next();
				
				NonZeroCache nzc = me.getValue();
				
				if (--nzc.timeout < 0)
					it.remove();
			}
		}
	}

	/**
	 * 
	 */
	private void fillGaps() {
		final Iterator<Map.Entry<String, NonZeroCache>> it = hmLastNonZeroValues.entrySet().iterator();
		
		Map.Entry<String, NonZeroCache> me;
		String sKey;
		NonZeroCache nzc;
		
		while (it.hasNext()){
			me = it.next();
			
			sKey = me.getKey();
			nzc = me.getValue();
			
			if (nzc!=null){
				final IDGenerator.KeySplit split = IDGenerator.getKeySplit(sKey);
				
				addToStructure(
					split.FARM, 
					split.CLUSTER, 
					split.NODE,
					split.FUNCTION,
					nzc.dValue, 
					false
				);
			}
		}
	}

}
