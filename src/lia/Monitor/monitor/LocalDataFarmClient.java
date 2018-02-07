package lia.Monitor.monitor;

import lia.Monitor.tcpClient.MLSerClient;

/**
 * The same as LocalDataClient except it also returns the 
 * corresponding MLSerClient for that result 
 */
public interface LocalDataFarmClient {
	 public void newFarmResult(MLSerClient client, Object res);
}
