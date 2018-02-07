/**
 * 
 */
package lia.Monitor.monitor;

import java.util.Properties;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.DynamicThreadPoll.SchJob;

/**
 * @author costing
 * @since 2007-03-07
 */
public abstract class DynamicModule extends SchJob implements MonitoringModule {
	
	private static final Logger logger = Logger.getLogger(DynamicModule.class.getName());
	
	/**
	 * Test if this module is capable to produce some values.
	 * 
	 * @param pred
	 * @return true if the module is capable of producing data that matches the predicate, false otherwise
	 * @see #registerPredicate(monPredicate) 
	 */
	public abstract boolean matches(monPredicate pred); 

	/**
	 * Whenever the use count goes positive this method will be called to tell the module to activate itself.
	 */
	protected abstract void enable();
	
	/**
	 * Whenever the use count goes back to 0 this method will be called to tell the module to deactivate itself.
	 */
	protected abstract void disable();
	
	
	/**
	 * Internal object to ensure consistency of {@link #useCounter} 
	 */
	private final Object oLock = new Object();
	
	/**
	 * How many predicates are currently registered to receive output of this module
	 */
	private volatile int useCounter = 0;
	
	
	/**
	 * Called by the service whenever a new predicate that matches this modules' output is registered for 
	 * continuous listening.
	 * 
	 * @return true if this call activated the module, false otherwise
	 */
	public final boolean incrementUseCount(){
		synchronized (oLock){
			useCounter++;
			
			if (useCounter==1){
				enable();
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Called by the service whenever a predicate that matched this modules' output is unregistered.
	 * 
	 *  @return true if this call disabled the module, false if not
	 */
	public final boolean decrementUseCount(){
		synchronized(oLock){
			useCounter--;
			
			if (useCounter==0){
				disable();
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Get the number of currently subscribed predicates
	 * 
	 * @return the number of active subscribed predicates
	 */
	public final int getCurrentUseCount(){
		return useCounter;
	}
	
	private static final Vector vModules = new Vector();
	
	/**
	 * When the module is initialized, it should call this method to make itself visible in the world
	 * of dynamic modules.
	 */
	protected final void register(){
		vModules.add(this);
	}
	
	/**
	 * When the module is destroyed it should remove itself from the list of dynamic modules
	 */
	protected final void unregister(){
		vModules.remove(this);
	}
	
	/**
	 * This function is called by the service code when a new predicate is registered for continuous data
	 * receiving.
	 * 
	 * @param pred
	 */
	public static final void registerPredicate(final monPredicate pred){
		int matched = 0;
		int enabled = 0;
		
		synchronized (vModules){
			for (int i=vModules.size()-1; i>=0; i--){
				DynamicModule dm = (DynamicModule) vModules.get(i);
				
				if (dm.matches(pred)){
					matched++;
					
					if (dm.incrementUseCount())
						enabled++;
				}
			}
		}
		
		if (matched>0 && logger.isLoggable(Level.FINER)){
			logger.log(Level.FINER, "New predicated matched "+matched+" modules, enabling "+enabled+" of them : "+pred);
		}
	}
	
	/**
	 * This function is called by the service code when a predicate is unregistered.
	 * 
	 * @param pred
	 */
	public static final void unregisterPredicate(final monPredicate pred){
		int matched = 0;
		int disabled = 0;
		
		synchronized (vModules){
			for (int i=vModules.size()-1; i>=0; i--){
				DynamicModule dm = (DynamicModule) vModules.get(i);
				
				if (dm.matches(pred)){
					matched++;
					
					if (dm.decrementUseCount())
						disabled++;
				}
			}
		}
		
		if (matched>0 && logger.isLoggable(Level.FINER)){
			logger.log(Level.FINER, "Old predicated matched "+matched+" modules, disabling "+disabled+" of them : "+pred);
		}
	}
	
	/**
	 * Helper class to parse the module arguments
	 * 
	 * @param args
	 * @return a dictionary with the values
	 */
	public static final Properties parseArguments(final String args){
		final Properties prop = new Properties(); 
		
		final String[] splittedArgs = args.split("(\\s)*(;|,)+(\\s)*");
		
		for (int i = 0; i < splittedArgs.length; i++) {
			String[] aKeyValue = splittedArgs[i].split("(\\s)*=(\\s)*");
			if (aKeyValue.length != 2)
				continue;
			
			prop.put(aKeyValue[0], aKeyValue[1]);
		}
		
		return prop;
	}
	
}
