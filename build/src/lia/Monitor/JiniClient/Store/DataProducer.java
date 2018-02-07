/**
 * 
 */
package lia.Monitor.JiniClient.Store;

import java.util.Vector;

/**
 * @author costing
 * @since Jun 12, 2007
 */
public interface DataProducer {

	/**
	 * Get the newly produced data.
	 * This method will be called once per minute from {@link Main#saveTimer}
	 * 
	 * @return new data made available, or null if nothing is to be published
	 */
	public Vector<Object> getResults();
	
}
