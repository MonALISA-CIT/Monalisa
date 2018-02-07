package lia.Monitor.Store;

import java.util.ArrayList;
import java.util.Vector;

import lia.Monitor.monitor.dbStore;
import lia.Monitor.monitor.monPredicate;

/**
 * Store interface
 */
public interface TransparentStoreInt extends dbStore {

	/**
	 * Delete values older than the given timestamp
	 * 
	 * @param time
	 */
	public void deleteOld(long time);
	
	/**
	 * Store a new value
	 * 
	 * @param r
	 */
	public void addData ( Object r );
	
	/**
	 * Store all values in this buffer
	 * 
	 * @param v
	 */
	public void addAll ( Vector<Object> v );
	
	/**
	 * Store an updated configuration for a service
	 * 
	 * @param farm
	 */
	public void updateConfig (lia.Monitor.monitor.MFarm farm);
	
	/**
	 * @param fromTime
	 * @param toTime
	 * @return the services' configuration between the two time limits
	 */
	public Vector<lia.ws.WSConf> getConfig ( long fromTime, long toTime );
	
	/**
	 * @param FarmName
	 * @param fromTime
	 * @param toTime
	 * @return this service's configuration between the two time limits 
	 */
	public Vector<lia.ws.WSConf> getConfig ( String FarmName, long fromTime, long toTime );
	
	/**
	 * @param FarmName
	 * @return the latest configuration of this service
	 */
	public Object getConfig ( String FarmName );
	
	/**
	 * @return the services for which there is some configuration stored
	 */
	public Vector<String> getConfigurationFarms ();
	
	/**
	 * @return the latest received values for each series
	 */
	public ArrayList<Object> getLatestValues ();
	
	/**
	 * close it
	 */
	public void close ();
	
	/**
	 * callback for configuration changes
	 */
	public void reload();
	
	/**
	 * @param vAccept
	 * @param vReject
	 */
	public void setStoreFilters(Vector<monPredicate> vAccept, Vector<monPredicate> vReject);
}
