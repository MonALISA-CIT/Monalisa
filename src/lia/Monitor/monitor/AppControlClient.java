package lia.Monitor.monitor;

import lia.Monitor.tcpClient.MLSerClient;

public interface AppControlClient {

	/** 
	 * Report back the status of the request to start AppControl. This
	 * callback is used when the appControl is established or after the timeout 
	 */
	public void appControlStatus(MLSerClient mlSerTClient, boolean status);
	
	/** report back the reponse for a given command */
	public void cmdResult(MLSerClient mlSerTClient, Long cmdID, String message, Object params);
}
