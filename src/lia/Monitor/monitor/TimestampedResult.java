package lia.Monitor.monitor;

/**
 * The common attribute of all results, the time
 * 
 * @author costing
 *
 */
public interface TimestampedResult {
	/**
	 * @return the epoch time, in milliseconds, of the result
	 */
	public long getTime();
}
