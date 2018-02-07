package lia.Monitor.Farm.Transfer;

import java.util.List;


/**
 * Defines the basic operations of a Transfer Protocol instance. 
 * 
 * @author catac
 */
public interface ProtocolInstance {

	/**
	 * Start the instance with the parameters passed in the constructor.
	 * @return True if successfully started.
	 */
	public boolean start();
	
	/**
	 * Stop immediately the instance
	 * @return True if successfully stopped.
	 */
	public boolean stop();
	
	/** 
	 * Check the status for this protocol instance.
	 * @return false if the instance is finished and can be removed from the list of active instances. 
	 */
	public boolean checkStatus(List lResults);
	
}
