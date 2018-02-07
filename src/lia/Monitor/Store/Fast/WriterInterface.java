/**
 * 
 */
package lia.Monitor.Store.Fast;

import lia.Monitor.monitor.Result;

/**
 * @author costing
 *
 */
public interface WriterInterface {
	/**
	 * @param rectime
	 * @param r
	 * @param iParam
	 * @param mval
	 * @param mmin
	 * @param mmax
	 * @return true if the value was inserted
	 */
	public boolean insert(long rectime, Result r, int iParam, double mval, double mmin, double mmax);
}
