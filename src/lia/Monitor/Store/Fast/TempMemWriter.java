/*
 * $Id: TempMemWriter.java 7533 2014-09-09 14:29:58Z costing $
 */
package lia.Monitor.Store.Fast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import lia.Monitor.Store.DataSplitter;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ExtendedResult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.TimestampedResult;
import lia.util.ntp.NTPDate;
import lia.util.threads.MonALISAExecutors;

/**
 * 
 * @author costing
 * 
 */
public final class TempMemWriter extends Writer implements TempMemWriterInterface {
	/**
	 * Actual data
	 */
	List<TimestampedResult>		lData;

	/**
	 * Smallest timestamp
	 */
	public long		lMinTime;

	/**
	 * Largest timestamp
	 */
	public long		lMaxTime;

	/**
	 * How many values can be kept
	 */
	int		iLimit;

	/**
	 * Max number of values that could be kept
	 */
	int		iHardLimit;

	/**
	 * Statistics
	 */
	public long		lServedRequests	= 0;

	/**
	 * @param i
	 */
	public void setHardLimit(int i) {
		if (i < 64 || i > 10240)
			return;

		iHardLimit = i * 1024;
	}

	/**
	 * 
	 */
	public TempMemWriter() {
		lData = new LinkedList<TimestampedResult>();

		lMinTime = lMaxTime = NTPDate.currentTimeMillis();

		MonALISAExecutors.getMLStoreExecutor().scheduleWithFixedDelay(new TempMemWriterTask(), 500, 500, TimeUnit.MILLISECONDS);

		iHardLimit = 256;

		long lMax = Runtime.getRuntime().maxMemory() / (1024L * 1024L);

		if (lMax >= 120)
			iHardLimit = 512;
		if (lMax >= 300)
			iHardLimit = 1024;
		if (lMax >= 500)
			iHardLimit = 2048;
		if (lMax >= 1000)
			iHardLimit = 8192;

		try {
			iHardLimit = Integer.parseInt(AppConfig.getProperty("lia.Monitor.Store.MemoryBufferSize", "" + iHardLimit));
		} catch (Exception e) {
			// ignore
		}

		if (iHardLimit < 64)
			iHardLimit = 64;

		if (iHardLimit > 10240)
			iHardLimit = 10240;

		iHardLimit *= 1024;

		iLimit = 64 * 1024;
	}

	@Override
	public final int save() {
		return 0;
	}

	@Override
	public final void storeData(final Object o) {
		if (o instanceof ExtendedResult)
			storeData((ExtendedResult) o);
		else if (o instanceof Result)
			storeData((Result) o);
		else if (o instanceof Collection<?>) {
			for (Object el: (Collection<?>)o)
				storeData(el);
		}
	}

	/**
	 * @return all values
	 */
	public final Vector<TimestampedResult> getDataAsVector() {
		synchronized (lData) {
			final Vector<TimestampedResult> v = new Vector<TimestampedResult>(lData.size());
			v.addAll(lData);
			lServedRequests++;
			return v;
		}
	}

	/*
	 * A new sample was received.
	 * 
	 * First the (Farm/Cluster/Node/Parameter) pair is looked up in the map:
	 * - if a CacheElement exists, then this object is notified with the new data
	 * - if a CacheElement does not exist, then a new one is created and added to the map
	 */
	private void storeData(final Result r) {
		if (r == null || r.param_name == null || r.param_name.length <= 0)
			return;

		for (int i = 0; i < r.param_name.length; i++)
			addData(r.time, r, r.param_name[i], r.param[i], r.param[i], r.param[i]);
	}

	private void storeData(final ExtendedResult r) {
		if (r == null || r.param_name == null)
			return;

		if (r.param_name.length == 1)
			addDataToList(r);
		else
			for (int i = 0; i < r.param_name.length; i++)
				addData(r.time, r, r.param_name[i], r.param[i], r.min, r.max);
	}

	@Override
	public final String getTableName() {
		return "TempMemWriter";
	}

	@Override
	public final long getTotalTime() {
		//return lMaxTime - lMinTime;
		return NTPDate.currentTimeMillis() - lMinTime;
	}

	@Override
	public final int getLimit() {
		return iLimit;
	}

	@Override
	public final int getHardLimit() {
		return iHardLimit;
	}

	@Override
	public final int getSize() {
		synchronized (lData) {
			return lData.size();
		}
	}

	@Override
	public final long getServedRequests() {
		return lServedRequests;
	}

	@Override
	public final boolean cleanup(final boolean bCleanHash) {
		return true;
	}

	private final void addData(final long rectime, final Result r, final String sParamName, final double mval, final double mmin, final double mmax) {
		final ExtendedResult er = new ExtendedResult();

		er.FarmName = r.FarmName;
		er.ClusterName = r.ClusterName;
		er.NodeName = r.NodeName;
		er.addSet(sParamName, mval);
		er.min = mmin;
		er.max = mmax;
		er.time = rectime;

		addDataToList(er);
	}

	private final void addDataToList(final ExtendedResult er) {
		synchronized (lData) {
			lData.add(er);

			if (er.time > lMaxTime)
				lMaxTime = er.time;
		}
	}

	/**
	 * @author costing
	 * @since Jun 13, 2010
	 */
	final class TempMemWriterTask implements Runnable {
		@Override
		public void run() {
			synchronized (lData) {
				// adjust the limit size
				final long total = Runtime.getRuntime().maxMemory(); // instead of totalMemory()
				final long free = Runtime.getRuntime().freeMemory() + (total - Runtime.getRuntime().totalMemory());

				long t = total / 10;
				if (t > 20 * 1024 * 1024)
					t = 20 * 1024 * 1024;

				if (((free < t) && (iLimit > 16)) || (iLimit > iHardLimit))
					iLimit = (iLimit / 3) * 2;

				while ((free > t * 2) && (lData.size() > ((2 * iLimit) / 3) || iLimit - lData.size() < 1000) && (iLimit < iHardLimit))
					iLimit = (iLimit * 3) / 2;

				if (lData.size() > (11 * iLimit) / 10) {
					int iDownLimit = (9 * iLimit) / 10;
					for (int i = lData.size(); i >= iDownLimit; i--)
						lData.remove(0);

					if (lData.size() > 0) {
						ExtendedResult er = (ExtendedResult) lData.get(0);

						lMinTime = er.time;
					} else
						lMinTime = NTPDate.currentTimeMillis();
				}
			}
		}
	}

	@Override
	public ArrayList<Object> getLatestValues() {
		return new DataSplitter(getDataAsVector(), 0, 0, true).getLatestValues();
	}

}
