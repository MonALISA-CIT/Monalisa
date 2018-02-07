/**
 * 
 */
package lia.util.Pathload.server.manager;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Because the pathload clients use a pull model
 * to aquire their tokens and the backend cache
 * is a push model one, a manager is needed to keep
 * the cache clean of dead peers
 * 
 * @author heri
 *
 */
public class GarbageManager {

	/**
	 * The delay between PathloadGarbageCollector runs
	 */
	public static final int GARBAGE_MANAGER_PERIOD = 60 * 1000;
	
	private ConfigurationManager cm;
	private Timer timerExec;

	/**
	 * This TimerTask cleans up dead Peers from within the cache.
	 * It does so by running the cleanupDeadPeers method of the 
	 * ConfigurationManager which in turn runs cleanupDeadPeers
	 * on the PeerCache
	 * 
	 * @author heri
	 *
	 */
	private class CollectDeadPeers extends TimerTask {

		public void run() {
			cm.cleanUpDeadPeers();
		}		
	}
	
	/**
	 * This is required for serialization
	 */
	private static final long serialVersionUID = -3731556587850585455L;

	
	/**
	 * Default constructor
	 */
	public GarbageManager() {
	}

	/**
	 * Startup Management Timers
	 *
	 */
	public void startup() {
		cm = ConfigurationManager.getInstance();
		timerExec = new Timer(true);
		timerExec.schedule(new CollectDeadPeers(), 0, GarbageManager.GARBAGE_MANAGER_PERIOD);		
	}
	
	/**
	 * Shutdown gracefully
	 *
	 */
	public void shutdown() {
		if (timerExec != null) {
			timerExec.cancel();
		}
	}
}
