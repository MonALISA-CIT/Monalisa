package lia.Monitor.Store;

import java.io.Serializable;
import java.util.Comparator;

import lia.Monitor.monitor.TimestampedResult;

/**
 * Simple comparator for *Result objects, to order them in time
 * 
 * @author costing
 */
public final class ResultComparator implements java.util.Comparator<TimestampedResult>,Serializable {
	private static final long	serialVersionUID	= 1L;
	
	private static final ResultComparator instance = new ResultComparator();
	
	/**
	 * Singleton
	 * 
	 * @return the only instance of this object
	 */
	public static Comparator<TimestampedResult> getInstance(){
		return instance;
	}
	
	@Override
	public int compare(final TimestampedResult o1, final TimestampedResult o2) {
		final long t1 = o1.getTime();
		final long t2 = o2.getTime();

		if (t1 < t2)
			return -1;
		if (t1 > t2)
			return 1;

		return 0;
	}
}
