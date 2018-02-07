package lia.Monitor.Store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import lia.Monitor.Store.Fast.DB;
import lia.Monitor.Store.Fast.IDGenerator;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ExtendedResult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.TimestampedResult;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.ntp.NTPDate;
import lia.web.bean.Generic.WriterConfig;

/**
 * @author costing
 * @since forever
 */
public class DataSplitter {

	/**
	 * series names - series values mapping
	 */
	protected final LinkedHashMap<String, Vector<TimestampedResult>>	hmSeries;

	private final Object			lCacheLock		= new Object();

	/**
	 * Current time
	 */
	protected long				lNow;

	/**
	 * Compact interval
	 */
	protected long				lInterval;

	/**
	 * Whether or not the database is "frozen"
	 */
	public static final boolean	bFreeze			= Boolean.valueOf(AppConfig.getProperty("repository.freeze", "false")).booleanValue();

	/**
	 * Time of the freeze
	 */
	public static final long		lFreezeTime;

	/**
	 * Whether or not the current data set is already compacted
	 */
	protected boolean				bCompacted		= true;

	/**
	 * Whether or not I should compact the data at the end
	 */
	public boolean				bShouldCompact	= false;

	static {
		long lFreezeTimeTemp = 0;

		try {
			if (bFreeze) {
				long lMaxTime = AppConfig.getl("repository.freeze.epochtime", 0);

				if (lMaxTime <= 0) {
					List<WriterConfig> l = lia.web.bean.Generic.ConfigBean.getConfig();
					Collections.reverse(l);

					Iterator<WriterConfig> it = l.iterator();

					while (it.hasNext()) {
						WriterConfig wc = it.next();

						if (wc.iWriteMode >= 0 && wc.iWriteMode <= 2) {
							DB db = new DB();
							
							db.setReadOnly(true);
							
							db.query("SELECT max(rectime) AS endtime FROM " + wc.sTableName + ";");

							if (db.moveNext()) {
								if (db.getl("starttime") > 0) {
									final long lTemp = db.getl("starttime");

									if (lTemp > 0 && lTemp > lMaxTime) {
										lMaxTime = lTemp;
										break;
									}
								}
							}
						}
					}

					if (lMaxTime <= 0) {
						DB db = new DB();
						
						db.setReadOnly(true);

						if (db.query("select max(mi_lastseen) from monitor_ids;", true) && db.moveNext()) {
							lMaxTime = db.getl(1) * 1000;
						}
					}
				}

				lFreezeTimeTemp = lMaxTime > 0 ? lMaxTime : NTPDate.currentTimeMillis();

				System.err.println("[FREEZE] time ends : " + (new Date(lFreezeTimeTemp)));
			}
		} catch (Throwable t) {
			// ignore possible exceptions because of missing classes
			// farms don't need the freeze feature anyway :)
			
			
		}

		lFreezeTime = lFreezeTimeTemp;
	}

	/**
	 * Simple dummy constructor
	 */
	public DataSplitter() {
		this(16);
	}
	
	/**
	 * @param size for how many values to accommodate initially
	 */
	public DataSplitter(final int size){
		this(size, 0.75f);
	}
	
	/**
	 * @param size for how many values to accommodate initially
	 * @param fillFactor fill factor
	 */
	public DataSplitter(final int size, final float fillFactor){
		this(size, fillFactor, false);
	}
	
	/**
	 * @param size for how many values to accommodate initially
	 * @param fillFactor fill factor
	 * @param accessOrder store the value in access order or insert order
	 */
	public DataSplitter(final int size, final float fillFactor, final boolean accessOrder){
		hmSeries = new LinkedHashMap<String, Vector<TimestampedResult>>(size, fillFactor, accessOrder);
	}

	/**
	 * Start by analyzing the given Vector and splitting it into time series
	 * 
	 * @param v
	 * @param lPointsInterval
	 * @param lMax
	 */
	public DataSplitter(final Vector<TimestampedResult> v, final long lPointsInterval, final long lMax) {
		this(v, lPointsInterval, lMax, false);
	}

	/**
	 * Start by analyzing the given Vector and splitting it into time series
	 * 
	 * @param v
	 * @param lPointsInterval
	 * @param _lMax
	 * @param bAbsTime true if the min and max time are given as absolute times, not relative ones
	 */
	public DataSplitter(final Vector<TimestampedResult> v, final long lPointsInterval, final long _lMax, final boolean bAbsTime) {
		this();
		
		lNow = bFreeze ? lFreezeTime : NTPDate.currentTimeMillis();

		this.lInterval = lPointsInterval;

		long lMin = lPointsInterval;
		long lMax = _lMax;

		if (!bAbsTime) {
			lMin = lNow - lPointsInterval;
			lMax = lNow - lMax;
		}

		if (v == null || v.size() <= 0)
			return;

		for (int i = 0; i < v.size(); i++) {
			final TimestampedResult o = v.get(i);

			final long lTime = o.getTime();
			
			if ((lTime < lMin || lTime > lMax) && lPointsInterval > 0) {
				continue;
			}

			add(o);
		}
	}

	/**
	 * Get the max time available in this data splitter
	 * 
	 * @return epoch time in millis
	 */
	public long getMaxTime() {
		return lNow;
	}

	/**
	 * Get the length of the time interval that the stored data covers  
	 * 
	 * @return length in millis
	 */
	public long getInterval() {
		return lInterval;
	}

	/**
	 * Add the data from another DataSplitter object
	 * 
	 * @param ds
	 * @param lCompactInterval
	 */
	public void add(final DataSplitter ds, final long lCompactInterval) {
		if (ds == null)
			return;

		for (final Map.Entry<String, Vector<TimestampedResult>> entry: ds.hmSeries.entrySet()){
			addSingleSeries(entry.getKey(), entry.getValue(), lCompactInterval);
		}
	}

	/**
	 * Add a single data series, assumed sorted and compacted
	 * 
	 * @param v data series to add
	 * @param lCompactInterval compact interval of the overall data set
	 */
	public void addSingleSeries(final Vector<TimestampedResult> v, final long lCompactInterval) {
		if (v == null || v.size() == 0)
			return;

		final String sKey = IDGenerator.generateKey(v.get(0), 0);

		addSingleSeries(sKey, v, lCompactInterval);
	}
	
	/**
	 * Add a single data series to the given key, assumed sorted and compacted.
	 * This method is package protected, being used by Cache to bypass key generation
	 * 
	 * @param v data series to add
	 * @param lCompactInterval compact interval of the overall data set
	 */
	void addSingleSeries(final String sKey, final List<TimestampedResult> v, final long lCompactInterval) {	
		Vector<TimestampedResult> vOld = hmSeries.get(sKey);

		if (vOld == null) {
			vOld = new Vector<TimestampedResult>(v.size());
			hmSeries.put(sKey, vOld);
		}

		if (vOld.size() > 0) {
			//System.err.println("DS: merging "+vOld.size()+" and "+v.size());

			final boolean bSort = ResultComparator.getInstance().compare(vOld.lastElement(), v.iterator().next()) > 0;

			vOld.addAll(v);

			if (bSort)
				Collections.sort(vOld, ResultComparator.getInstance());
		} else {
			//System.err.println("DS: just setting "+v.size());
			vOld.addAll(v);
		}

		if (lCompactInterval > 1) {
			vOld = DataCompacter.compact(vOld, lCompactInterval);
			hmSeries.put(sKey, vOld);

			//System.err.println("DS: after compact: "+vOld.size()+" : "+vOld);
		} else {
			bCompacted = false;
		}
	}

	/**
	 * Add some value(s) to the splitter. The values can be of one of these types:
	 * 	Result
	 * 	ExtendedResult
	 * 	eResult
	 *  Collection (all the elements are added using the same function)
	 *  DataSplitter (all series are added bulk, compact disabled)
	 * 
	 * @param o the Object to be added
	 */
	public void add(final Object o) {
		String sKey = null;

		if (o instanceof Result) {
			Result r = (Result) o;

			if (r.param == null || r.param_name == null || r.param.length != r.param_name.length || r.param.length == 0)
				return;

			if (r.param.length == 1) {
				sKey = IDGenerator.generateKey(r, 0);

				add(r, sKey);
			} else {
				for (int i = 0; i < r.param.length; i++) {
					Result rNew = new Result(r.FarmName, r.ClusterName, r.NodeName, r.Module, new String[] { r.param_name[i] });
					rNew.time = r.time;
					rNew.param[0] = r.param[i];

					sKey = IDGenerator.generateKey(rNew, 0);

					add(rNew, sKey);
				}
			}
		} else if (o instanceof ExtendedResult) {
			ExtendedResult r = (ExtendedResult) o;

			if (r.param == null || r.param_name == null || r.param.length != r.param_name.length || r.param.length == 0)
				return;

			if (r.param.length == 1) {
				sKey = IDGenerator.generateKey(r, 0);

				add(r, sKey);
			} else {
				for (int i = 0; i < r.param.length; i++) {
					final ExtendedResult rNew = new ExtendedResult();
					rNew.FarmName = r.FarmName;
					rNew.ClusterName = r.ClusterName;
					rNew.NodeName = r.NodeName;
					rNew.Module = r.Module;
					rNew.addSet(r.param_name[i], r.param[i]);
					rNew.time = r.time;

					sKey = IDGenerator.generateKey(rNew, i);

					add(rNew, sKey);
				}
			}
		} else if (o instanceof eResult) {
			eResult r = (eResult) o;

			if (r.param == null || r.param_name == null || r.param.length != r.param_name.length || r.param.length == 0)
				return;

			if (r.param.length == 1) {
				sKey = IDGenerator.generateKey(r, 0);

				add(r, sKey);
			} else {
				for (int i = 0; i < r.param.length; i++) {
					final eResult rNew = new eResult();
					rNew.FarmName = r.FarmName;
					rNew.ClusterName = r.ClusterName;
					rNew.NodeName = r.NodeName;
					rNew.Module = r.Module;
					rNew.addSet(r.param_name[i], r.param[i]);
					rNew.time = r.time;

					sKey = IDGenerator.generateKey(rNew, i);

					add(rNew, sKey);
				}
			}
		} else if (o instanceof Collection<?>){
			Iterator<?> it = ((Collection<?>) o).iterator();
			
			while (it.hasNext())
				add(it.next());
		} else if (o instanceof DataSplitter){
			add((DataSplitter) o, -1);
		}
	}

	/**
	 * Add one object to a given key. The object must be an Result, ExtendedResult or eResult object
	 * with a single value inside. add(o) does this check.
	 * 
	 * @param o
	 * @param sKey
	 */
	private void add(final TimestampedResult o, final String sKey) {
		if (sKey == null)
			return;

		Vector<TimestampedResult> v = hmSeries.get(sKey);

		if (v == null) {
			v = new Vector<TimestampedResult>();
			hmSeries.put(sKey, v);
		}

		synchronized (lCacheLock) {
			v.add(o);
		}

		bCompacted = false;

	}

	/**
	 * Get the mapping of series keys - series values
	 * 
	 * @return mapping of series keys to series values arrays
	 */
	public Map<String, Vector<TimestampedResult>> getMap(){
		return hmSeries;
	}
	
	/**
	 * Get the subset of data that matches the given predicate, only by names
	 * 
	 * @param pred
	 * @return subset of data that matches the names in the predicate
	 */
	public Vector<TimestampedResult> get(final monPredicate pred) {
		// return all the values
		return Cache.getObjectsFromHash(hmSeries, pred, lCacheLock, false);
	}

	/**
	 * Get the subset of data that matches the given predicate, by names and time constraints
	 * 
	 * @param pred
	 * @return subset of data that matches the given predicate, by names and time constraints
	 */
	public Vector<TimestampedResult> getAndFilter(final monPredicate pred) {
		// filter the values by predicate time
		return Cache.getObjectsFromHash(hmSeries, pred, lCacheLock, true);
	}

	/**
	 * Convert the values back into a single array
	 * 
	 * @return values, as array
	 */
	public Vector<TimestampedResult> toVector() {
		final Vector<TimestampedResult> v = new Vector<TimestampedResult>();

		synchronized (lCacheLock) {
			Collection<Vector<TimestampedResult>> c = hmSeries.values(); 

			if (c.size()==0)
				return v;
			
			final Iterator<Vector<TimestampedResult>> it = c.iterator();
			
			if (c.size()==1){
				return new Vector<TimestampedResult>( it.next() ); 
			}
			
			synchronized (v) {
				while (it.hasNext()) {
					v.addAll(it.next());
				}

				Collections.sort(v, ResultComparator.getInstance());
			}
		}

		return v;
	}

	/**
	 * Get the compact status
	 * 
	 * @return true if the values are compacted, false if not
	 */
	public boolean isCompacted() {
		return bCompacted;
	}

	/**
	 * Force a recompacting of the values
	 * 
	 * @param lPointsInterval
	 * @param bSort
	 */
	public void recompact(final long lPointsInterval, final boolean bSort) {
		bCompacted = false;

		compact(lPointsInterval, bSort);
	}

	/**
	 * Compact the values, only if necessary
	 * 
	 * @param lPointsInterval
	 * @param bSort
	 */
	public void compact(final long lPointsInterval, final boolean bSort) {
		if (!bCompacted && lPointsInterval > 1) {
			bCompacted = true;

			final Iterator<Map.Entry<String, Vector<TimestampedResult>>> it = hmSeries.entrySet().iterator();

			Map.Entry<String, Vector<TimestampedResult>> entry;
			Vector<TimestampedResult> v;
			Vector<TimestampedResult> vTemp;

			while (it.hasNext()) {
				entry = it.next();
				v = entry.getValue();

				if (bSort)
					Collections.sort(v, ResultComparator.getInstance());

				vTemp = DataCompacter.compact(v, lPointsInterval);

				v.clear();
				v.addAll(vTemp);
			}
		} else if (bSort) {
			final Iterator<Vector<TimestampedResult>> it = hmSeries.values().iterator();

			Vector<TimestampedResult> v;

			while (it.hasNext()) {
				v = it.next();

				Collections.sort(v, ResultComparator.getInstance());
			}
		}
	}

	@Override
	public String toString() {
		final Iterator<Vector<TimestampedResult>> it = hmSeries.values().iterator();

		long cnt = 0;

		Vector<TimestampedResult> v;

		while (it.hasNext()) {
			v = it.next();
			cnt += v.size();
		}

		return hmSeries.size() + " / " + cnt;
	}

	/**
	 * Get the last value from each different time series
	 * 
	 * @return an array of values, latest one from each time series
	 */
	public ArrayList<Object> getLatestValues() {
		synchronized (lCacheLock) {
			final ArrayList<Object> al = new ArrayList<Object>(hmSeries.size());

			final Iterator<Vector<TimestampedResult>> it = hmSeries.values().iterator();

			Vector<TimestampedResult> v;

			while (it.hasNext()) {
				v = it.next();

				if (v.size() > 0)
					al.add(v.lastElement());
			}

			return al;
		}
	}

	/**
	 * Get the absolute minimum time of the values in this data set
	 * 
	 * @return epoch time in millis for the earliest value
	 */
	public final long getAbsMin() {
		long lMin = -1;

		synchronized (lCacheLock) {
			final Iterator<Vector<TimestampedResult>> it = hmSeries.values().iterator();

			while (it.hasNext()) {
				final Vector<TimestampedResult> v = it.next();

				if (v != null && v.size() > 0) {
					final TimestampedResult o = v.firstElement();

					if (o != null) {
						final long lTime = o.getTime();

						if (lTime > 0 && (lTime < lMin || lMin < 0))
							lMin = lTime;
					}
				}
			}
		}

		return lMin;
	}

	/**
	 * Get the absolute max time of the values in this data set
	 * 
	 * @return epoch time in millis
	 */
	public final long getAbsMax() {
		long lMax = -1;

		synchronized (lCacheLock) {
			final Iterator<Vector<TimestampedResult>> it = hmSeries.values().iterator();

			while (it.hasNext()) {
				final Vector<TimestampedResult> v = it.next();

				if (v != null && v.size() > 0) {
					final TimestampedResult o = v.lastElement();

					if (o != null) {
						final long lTime = o.getTime();

						if (lMax < lTime)
							lMax = lTime;
					}
				}
			}
		}

		return lMax;
	}

	/**
	 * Get number of distinct series for which we have data
	 * 
	 * @return number of distinct series
	 */
	public int count(){
		synchronized (lCacheLock){
			return hmSeries.size();
		}
	}
	
	/**
	 * Debug method
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		Result r = new Result("Farm", "Cluster", "Node", "Module", null);
		r.addSet("parameter", 1);
		r.time = System.currentTimeMillis() - 123456;
		
		DataSplitter ds = new DataSplitter();
		ds.add(r);
		
		System.err.println("Before : "+ds+" : "+r.time);
		
		ds.compact(120000, true);
		
		System.err.println("After : "+ds+" : "+((Result) ds.toVector().get(0)).time);
	}
	
}
