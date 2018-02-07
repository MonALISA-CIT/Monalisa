package lia.Monitor.Store.Fast;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import lia.Monitor.monitor.ExtendedResult;
import lia.Monitor.monitor.Result;
import lia.util.ntp.NTPDate;

/**
 * Memory writer
 */
public class MemWriter extends Writer {
	/**
	 * Milliseconds
	 */
	long									lGraphTotalTime;								// in milliseconds

	/**
	 * Number of samples
	 */
	long									lGraphSamples;									// integer, >0

	private long							lastCleanupTime	= 0;

	private long							cleanupInterval	= 60 * 1000;

	/**
	 * Actual interval between two points
	 */
	public long								lInterval;

	private List<ExtendedResult>			lData;

	private long							lLimit;

	/**
	 * @param _lGraphTotalTime
	 * @param _lGraphSamples
	 * @param _sTableName
	 * @param _iWriteMode
	 * @param _lLimit
	 */
	public MemWriter(long _lGraphTotalTime, long _lGraphSamples, String _sTableName, int _iWriteMode, long _lLimit) {
		lGraphTotalTime = _lGraphTotalTime;
		lGraphSamples = _lGraphSamples;
		sTableName = _sTableName;

		iWriteMode = _iWriteMode;

		lInterval = lGraphTotalTime / lGraphSamples;

		m = new HashMap<Object, CacheElement>();

		lData = new LinkedList<ExtendedResult>();

		this.lLimit = _lLimit;
	}

	@Override
	public int save() {
		return 0;
	}

	@Override
	public void storeData(final Object o) {
		if (o instanceof Result)
			storeData((Result) o);
	}

	/**
	 * @return all values in the buffer
	 */
	public Vector<Object> getDataAsVector() {
		synchronized (lData) {
			Vector<Object> v = new Vector<Object>();
			v.addAll(lData);
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
	private synchronized final void storeData(final Result r) {
		int i;

		if (r == null || r.param_name == null || r.param_name.length <= 0)
			return;

		for (i = 0; i < r.param_name.length; i++) {
			if (iWriteMode == 3) {
				final String sKey = IDGenerator.generateKey(r, i);

				if (sKey == null)
					continue;

				CacheElement ce;

				synchronized (mLock) {
					ce = m.get(sKey);
				}

				if (ce != null) {
					ce.update(r, true);
				} else {
					ce = new CacheElement(lInterval, r, i, r.time, true, this);

					synchronized (mLock) {
						m.put(sKey, ce);
					}
				}
			} else {
				addData(r.time, r, r.param_name[i], r.param[i], r.param[i], r.param[i]);
			}
		}
	}

	@Override
	public long getTotalTime() {
		return lGraphTotalTime;
	}

	@Override
	public final boolean cleanup(boolean bCleanHash) {
		long now = NTPDate.currentTimeMillis();

		if (lInterval>0)
			cleanHash(lInterval);
		
		if (lastCleanupTime + cleanupInterval < now) {
			synchronized (lData) {
				boolean bDel = false;
				do {
					bDel = false;

					if (lData.size() > 0) {
						ExtendedResult r = lData.get(0);

						if (now - r.time > lGraphTotalTime) {
							lData.remove(0);
							bDel = true;
						}
					}
				} while (bDel);

				java.util.Collections.sort(lData);
			}

			lastCleanupTime = now;
		}

		return true;
	}

	/**
	 * @param rectime
	 * @param r
	 * @param sParamName
	 * @param mval
	 * @param mmin
	 * @param mmax
	 */
	public void addData(long rectime, Result r, String sParamName, double mval, double mmin, double mmax) {
		ExtendedResult er = new ExtendedResult();

		er.FarmName = r.FarmName;
		er.ClusterName = r.ClusterName;
		er.NodeName = r.NodeName;
		er.addSet(sParamName, mval);
		er.min = mmin;
		er.max = mmax;
		er.time = rectime;

		synchronized (lData) {
			while (lLimit > 0 && lData.size() >= lLimit) {
				lData.remove(0);
			}

			lData.add(er);
		}
	}

	@Override
    public String toString(){
    	return "MemWriter("+sTableName+", "+iWriteMode+")";
    }

}
