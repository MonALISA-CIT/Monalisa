package lia.Monitor.Store;

import java.util.Vector;

import lia.Monitor.Store.Fast.CacheElement;
import lia.Monitor.Store.Fast.WriterInterface;
import lia.Monitor.monitor.ExtendedResult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.TimestampedResult;

/**
 * @author costing
 * @since forever
 */
public final class DataCompacter implements WriterInterface {
	/**
	 * Create an averaged data series
	 * 
	 * @param v original data
	 * @param lInterval compact interval, the minimum distance in ms between two consecutive points
	 * @return the compacted data
	 */
	public static Vector<TimestampedResult> compact(final Vector<TimestampedResult> v, final long lInterval) {
		if (v == null || v.size() < 2 || lInterval < 1)
			return v!=null ? new Vector<TimestampedResult>(v) : null;

		DataCompacter dc = null;
		
		int i = 0;
		
		for (; i<v.size(); i++){
			Object o = v.get(i);
		    
			if (o instanceof Result){
				dc = new DataCompacter(lInterval, (Result) o, 0);
				break;
			}
		}
		
		if (dc==null)
			return new Vector<TimestampedResult>();
		
		Object o;
		
		for (++i; i<v.size(); i++){
			o = v.get(i);
			
			if (o instanceof Result)
				dc.addResult((Result) o);
		}
		
		dc.flush();
		
		return dc.vResult;
	}
	
	private final CacheElement ce;
	
	private final Vector<TimestampedResult> vResult = new Vector<TimestampedResult>();
	
	private DataCompacter(final long lCompactInterval, final Result r, final int iParam){
		ce = new CacheElement(lCompactInterval, r, iParam, r.time, false, this);
	}
	
	private void addResult(final Result r){
		ce.update(r, true);
	}
	
	private void flush(){
		ce.flush();
	}

	@Override
	public boolean insert(final long rectime, final Result r, final int iParam, final double mval, final double mmin, final double mmax) {
		final ExtendedResult er = new ExtendedResult();

		er.FarmName = r.FarmName;
		er.ClusterName = r.ClusterName;
		er.NodeName = r.NodeName;
		
		er.time = rectime;

		er.addSet(r.param_name[iParam], mval);
		er.min = mmin;
		er.max = mmax;
		
		vResult.add(er);
		
		return true;
	}
	
	/**
	 * debug method
	 * 
	 * @param args
	 */
	public static void main(String args[]){
		Vector<TimestampedResult> v = new Vector<TimestampedResult>();
		
		v.add(produce(1000, 1));
		
//		v.add(produce(2000, 2));
		v.add(produce(12000, 3));
		v.add(produce(31000, 2));
		
		v.add(produce(71000, 3));
		v.add(produce(75000, 4));
		
		System.out.println(compact(v, 10000));
	}
	
	private static ExtendedResult produce(long lTime, double dVal, double dMin, double dMax){
		final ExtendedResult er = new ExtendedResult();
		er.min = dMin;
		er.max = dMax;

		er.FarmName = "F";
		er.ClusterName = "C";
		er.NodeName = "N";
		
		er.time = lTime;
		er.addSet("f", dVal);
		
		return er;
	}
	
	private static ExtendedResult produce(long lTime, double dVal){
		return produce(lTime, dVal, dVal, dVal);
	}
}
