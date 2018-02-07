/**
 * 
 */
package lia.Monitor.JiniClient.Store;

/**
 * @author costing
 *
 */
public interface ConnectionMonitor {

	/**
	 * @param serviceName
	 * @param online
	 */
	void notifyServiceActivity(String serviceName, boolean online);

}
