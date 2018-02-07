/*
 * Created on Nov 20, 2003
 */
package lia.Monitor.JiniClient.Store;

import lia.Monitor.monitor.AccountingResult;
import lia.Monitor.monitor.DataReceiver;
import lia.Monitor.monitor.ExtResult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;

/**
 * @author costing
 *
 */
public class CacheUpdater implements DataReceiver {

	private boolean bHasToRun;

	/**
	 * 
	 */
	public CacheUpdater() {
		bHasToRun = true;
	}

	@Override
	public void addResult(final Result a) {
		if (bHasToRun) {
			try {
				lia.Monitor.Store.Cache.addToCache(a);
			}
			catch (Throwable t) {
				System.err.println("Exception in add to Cache of a Result : " + t);
				t.printStackTrace();
				System.err.println("Java memory info: \n" + "  Free memory : " + Runtime.getRuntime().freeMemory()
					+ "\n" + "  Total memory: " + Runtime.getRuntime().totalMemory() + "\n" + "  Max memory  : "
					+ Runtime.getRuntime().maxMemory());
			}
		}
	}

	@Override
	public void addResult(final eResult a) {
		if (bHasToRun) {
			try {
				lia.Monitor.Store.Cache.addToCache(a);
			}
			catch (Throwable t) {
				System.err.println("Exception in add to Cache of an eResult : " + t);
				t.printStackTrace();
				System.err.println("Java memory info: \n" + "  Free memory : " + Runtime.getRuntime().freeMemory()
					+ "\n" + "  Total memory: " + Runtime.getRuntime().totalMemory() + "\n" + "  Max memory  : "
					+ Runtime.getRuntime().maxMemory());
			}
		}
	}

	@Override
	public void addResult(final ExtResult a) {
		if (bHasToRun) {
			try {
				lia.Monitor.Store.Cache.addToCache(a);
			}
			catch (Throwable t) {
				System.err.println("Exception in add to Cache of an ExtResult : " + t);
				t.printStackTrace();
				System.err.println("Java memory info: \n" + "  Free memory : " + Runtime.getRuntime().freeMemory()
					+ "\n" + "  Total memory: " + Runtime.getRuntime().totalMemory() + "\n" + "  Max memory  : "
					+ Runtime.getRuntime().maxMemory());
			}
		}
	}

	@Override
	public void addResult(final AccountingResult ar) {
		// ignore accouting results while updating the cache
	}

	@Override
	public void updateConfig(final lia.Monitor.monitor.MFarm f) {
		// the cache doesn't hold configuration objects
	}

}
