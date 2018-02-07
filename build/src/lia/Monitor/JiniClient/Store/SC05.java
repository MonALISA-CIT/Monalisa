package lia.Monitor.JiniClient.Store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Pattern;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ExtendedResult;
import lia.Monitor.monitor.Result;
import lia.util.ntp.NTPDate;

/**
 * @author costing
 *
 */
public class SC05 implements Filter {

	private Vector<Result>			vResult		= null;

	private final HashMap	hmTempData	= new HashMap();

	private long			lLastFlush	= NTPDate.currentTimeMillis();

	/**
	 * @return pattern map
	 */
	public Map<String, Pattern> getRegexClusters() {
		return null;
	}

	private static final HashMap<String, List<String>>	INTERESTING_DATA	= new HashMap<String, List<String>>();
	static {
		INTERESTING_DATA.put("WAN", new ArrayList<String>());

		INTERESTING_DATA.put("scNodes", new ArrayList<String>());

		INTERESTING_DATA.put("iLinks", new ArrayList<String>());

		INTERESTING_DATA.put("WLinks", new ArrayList<String>());

		INTERESTING_DATA.put("SEAs", new ArrayList<String>());

		INTERESTING_DATA.put("SC05s", new ArrayList<String>());
	}

	/**
	 * @return clusters
	 */
	public Map<String, List<String>> getInterestingData() {
		return INTERESTING_DATA;
	}

	/**
	 * @return totals
	 */
	public String getTotalsName() {
		return "_TOTALS_";
	}

	/**
	 * @return interval
	 */
	public long getRunInterval() {
		long lRunInterval = 30 * 1000;

		try {
			lRunInterval = Integer.parseInt(AppConfig.getProperty("Repository.Filters.SC05.run_time", "30")) * 1000;
		}
		catch (Exception e) {
			// default run time = 30 seconds
		}

		return lRunInterval;
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

		if (this.vResult == null
				&& hmTempData.size() > 0
				&& NTPDate.currentTimeMillis() - this.lLastFlush > getRunInterval()) {
			flush();
		}

		if (vResult != null) {
			Vector<Result> vTemp = vResult;
			vResult = null;

			//System.err.println("Flush: Returning: "+vTemp);

			return vTemp;
		}

		return null;
	}

	private void filterResult(Result r) {
		if (r.FarmName == null || r.FarmName.equals(getTotalsName())
				|| r.ClusterName == null)
			return;

		String cluster = r.ClusterName;
		Map<String, Pattern> rec = getRegexClusters();
		if (rec != null) {
			// if we use regex clusters, try to find a match among these
			// for the current cluster name. See AlienFilter for an example
			for (Iterator<Map.Entry<String, Pattern>> rit = rec.entrySet().iterator(); rit.hasNext();) {
				Map.Entry<String, Pattern> me = rit.next();
				final String key = me.getKey();
				final Pattern p = me.getValue();
				if (p != null) {
					if (p.matcher(cluster).matches()) {
						cluster = key;
						break;
					}
				}
				else {
					if (key.equals(cluster))
						break;
				}
			}
		}
		List<String> l = getInterestingData().get(cluster);

		if (l == null)
			return;

		String sKey = r.NodeName;

		for (int i = 0; i < r.param_name.length; i++) {
			String sParam = r.param_name[i];

			if (l.size() == 0 || l.contains(sParam)) {
				HashMap hmCluster = (HashMap) hmTempData.get(cluster);

				if (hmCluster == null) {
					hmCluster = new HashMap();
					hmTempData.put(cluster, hmCluster);
				}

				HashMap hmParams = (HashMap) hmCluster.get(sKey);

				if (hmParams == null) {
					hmParams = new HashMap();
					hmCluster.put(sKey, hmParams);
				}

				HashMap hmTemp = (HashMap) hmParams.get(r.param_name[i]);

				if (hmTemp == null) {
					hmTemp = new HashMap();
					hmParams.put(r.param_name[i], hmTemp);
				}

				if (hmTemp.containsKey(r.FarmName)) {
					// if a farm is configured to send data more often we keep the max value, the totals charts look better this way :)

					Double dTemp = (Double) hmTemp.get(r.FarmName);

					if (dTemp == null || r.param[i] > dTemp.doubleValue())
						hmTemp.put(r.FarmName, Double.valueOf(r.param[i]));
				}
				else {
					hmTemp.put(r.FarmName, Double.valueOf(r.param[i]));
				}
			}
		}
	}

	private void flush() {
		lLastFlush = NTPDate.currentTimeMillis();

		Iterator it = hmTempData.entrySet().iterator();

		HashMap hmTotals = new HashMap();

		vResult = new Vector();

		while (it.hasNext()) {
			Map.Entry me = (Map.Entry) it.next();

			String sCluster = (String) me.getKey();

			HashMap hmCluster = (HashMap) me.getValue();

			Iterator it2 = hmCluster.entrySet().iterator();

			while (it2.hasNext()) {
				Map.Entry me2 = (Map.Entry) it2.next();

				HashMap hmParams = (HashMap) me2.getValue();

				Iterator it3 = hmParams.entrySet().iterator();

				while (it3.hasNext()) {
					Map.Entry me3 = (Map.Entry) it3.next();

					String sParam = (String) me3.getKey();

					HashMap hmTemp = (HashMap) me3.getValue();

					Iterator it4 = hmTemp.values().iterator();

					double dVal = 0d;

					while (it4.hasNext()) {
						Double dTemp = (Double) it4.next();

						dVal += dTemp.doubleValue();
					}

					hmTemp.clear();

					if (sParam.endsWith("_IN"))
						sParam = "Total_IN";
					else if (sParam.endsWith("_OUT"))
						sParam = "Total_OUT";
					else
						continue;

					Double d = (Double) hmTotals.get(sCluster + "\t" + sParam);

					d = Double.valueOf((d == null ? 0d : d.doubleValue()) + dVal);

					hmTotals.put(sCluster + "\t" + sParam, d);
				}
			}
		}

		it = hmTotals.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry me = (Map.Entry) it.next();

			String s = (String) me.getKey();
			Double d = (Double) me.getValue();

			String sCluster = s.substring(0, s.indexOf("\t"));
			String sParam = s.substring(s.indexOf("\t") + 1);

			Result r = new Result();
			r.FarmName = getTotalsName();
			r.ClusterName = sCluster;
			r.NodeName = getTotalsName();
			r.time = NTPDate.currentTimeMillis();
			r.addSet(sParam, d.doubleValue());

			vResult.add(r);
		}
	}

}
