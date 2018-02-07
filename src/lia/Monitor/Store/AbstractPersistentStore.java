package lia.Monitor.Store;


/**
 * The methods provided with this abstract class MUST be implemented in any of the subclasses.
 * This class provides a standard interface for all the storing methods (Sql, XML, JDO, File System, etc)
 * 
 * @see lia.Monitor.monitor.monPredicate
 * @see lia.Monitor.monitor.Result
 */
public abstract class AbstractPersistentStore {

 /**
 * 
 */
protected final String[] consParams;

/**
 * Every subclass should throw a <code>StoreException</code> if for some reasons cannot be initializated.
 * @param constraintParams 
 * 
 * @see lia.Monitor.Store.StoreException
 */ 

public AbstractPersistentStore(String[] constraintParams){ 
	this.consParams = constraintParams; 
}

/**
 * Makes an array of <code>Result</code>s persistent. If for some reasons the values cannot be stored a 
 * <code>StoreException</code> MUST be thrown.
 * 
 * @param values The <code>Result</code>s that must be stored.
 * @throws lia.Monitor.Store.StoreException
 * @see lia.Monitor.monitor.Result
 */ 
public abstract void makePersistent(lia.Monitor.monitor.Result[] values) throws StoreException;

/**
 * @param values
 * @throws StoreException
 */
public abstract void makePersistent(lia.Monitor.monitor.eResult[] values) throws StoreException;

/**
 * Deletes the data older than <code>time</code>. If for some reasons the values cannot be deleted than a 
 * <code>StoreException</code> MUST be thrown.
 * 
 * @param time Time in milliseconds. It's an absolute time (milliseconds since January 1, 1970, 00:00:00 GMT)
 * @param indent 
 * @param avgTime 
 * @throws StoreException 
 */ 
  //public abstract void deleteOld(long time) throws StoreException;

//  public abstract void deleteOld(long time, int indent, int rowNo) throws StoreException;
   
   /*
    * The new version. 
    */
  public abstract void deleteOld(long time, int indent, long avgTime) throws StoreException;

/**
 * Retrives the values that match a <code>monPredicate</code>. If for some reasons the values cannot be retrived than a 
 * <code>StoreException</code> MUST be thrown.
 * @param p 
 * 
 * @see lia.Monitor.Store.StoreException
 * @return A <code>java.util.Vector</code> of <code>Results</code>.
 * @throws lia.Monitor.Store.StoreException
 */ 
public abstract java.util.Vector<Object> getResults(lia.Monitor.monitor.monPredicate p) throws StoreException;

/**
 * @param p
 * @param indent
 * @return the values
 * @throws StoreException
 */
public abstract java.util.Vector<Object> getResults(lia.Monitor.monitor.monPredicate p, int indent) throws StoreException;

/**
 * This method MUST store the Farm configuration (Farm Name, Cluster Names, Nodes, Module Names).
 * It is subject to change in the following versions! If for some reasons the configuration cannot be stored a 
 * <code>StoreException</code> MUST be thrown.
 * @param mfarm 
 * 
 * @see lia.Monitor.monitor.MFarm
 * @throws lia.Monitor.Store.StoreException
 */ 
public abstract void updateConfig(lia.Monitor.monitor.MFarm mfarm) throws StoreException;

/**
 * @param fromTime
 * @param toTime
 * @return the configurations
 * @throws StoreException
 */
public abstract java.util.Vector<lia.ws.WSFarm> getConfig(long fromTime, long toTime) throws StoreException;

/**
 * @param farmName
 * @param fromTime
 * @param toTime
 * @return the configurations
 * @throws StoreException
 */
public abstract java.util.Vector<lia.ws.WSFarm> getConfig(String farmName,long fromTime, long toTime) throws StoreException;

/**
 * @param farmName
 * @return the configuration
 * @throws StoreException
 */
public abstract Object getConfig(String farmName) throws StoreException;


/**
 * @return ?
 */
public final String[] getConsParams() { return this.consParams;}

/**
 * 
 */
public synchronized void close(){
	//nothing
}
}

