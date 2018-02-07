package lia.Monitor.JiniClient.Store;

import java.util.Vector;

import lia.Monitor.DataCache.DataSelect;
import lia.Monitor.monitor.AccountingResult;
import lia.Monitor.monitor.DataReceiver;
import lia.Monitor.monitor.ExtResult;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.rrd.RRDDataReceiver;

/**
 * @author costing
 *
 */
public class SimpleClient {

	/**
	 * @param args
	 */
	public static void main(String args[]) {
		// start the repository service
		Main jClient = new Main();

		// register a MyDataReceiver object to receive any new information
		// jClient.addDataReceiver(new MyDataReceiver());
		jClient.addDataReceiver(new RRDDataReceiver());

		// another data consumer logs every received value in rotating log files
		// jClient.addDataReceiver(ResultFileLogger.getLoggerInstance());

		// wait 2 minutes
		try {
			Thread.sleep(120 * 1000);
		}
		catch (Throwable t) {
			// ignore
		}

		// dynamically add a new predicate to watch for
		// jClient.registerPredicate(new monPredicate("*", "*", "*", -1, -1, new
		// String[]{"Load5"}, null));

		// receive Load5 data for 2 minutes
		try {
			Thread.sleep(120 * 1000);
		}
		catch (Throwable t) {
			// ignore
		}

		// then remove the predicate
		jClient.unregisterPredicate(new monPredicate("*", "*", "*", -1, -1, new String[] { "Load5" }, null));

		// Uncomment this if you would like to stop the client when main() exits
		// jClient.stopIt();
	}

	/**
	 * This is a very simple data receiver that puts some filters on the
	 * received data and outputs the matching values on the console.
	 */
	private static class MyDataReceiver implements DataReceiver {

		private final Vector<monPredicate> vWatchFor; // the predicates for the data i'm interested in

		public MyDataReceiver() {
			vWatchFor = new Vector<monPredicate>();

			// i'm interested in any Load and cluster usage (free nodes / busy
			// nodes) data
			// /- farm name, * = any farm
			// / /- cluster name, * = any cluster
			// / / /- node name, * = any node
			// / / / /- (-1, -1) = (min, max) time mean any new data
			// / / / / /- the actual parameter
			vWatchFor.add(new monPredicate("*", "*", "*", -1, -1, new String[] { "Load5" }, null));
			vWatchFor.add(new monPredicate("*", "*", "*", -1, -1, new String[] { "Load_05" }, null));
			vWatchFor.add(new monPredicate("*", "*", "*", -1, -1, new String[] { "Load_15" }, null));
		}

		@Override
		public void addResult(final Result r) {
			for (int i = 0; i < vWatchFor.size(); i++) {
				final monPredicate pred = vWatchFor.get(i);

				// extract from "r" only the data i'm interested in
				final Result rTemp = DataSelect.matchResult(r, pred);

				// if "r" contains something interesting then display this
				// information
				// and the history values for the last 30 minutes
				if (rTemp != null) {
					System.out.println("Data received : " + rTemp.FarmName + "/" + rTemp.ClusterName + "/"
						+ rTemp.NodeName + " : " + rTemp.param_name[0] + "=" + rTemp.param[0]);
				}
			}
		}

		@Override
		public void addResult(final eResult er) {
			// eResults have the same structure as the Result objects except for
			// the "param" attribute, that is defined as:
			// Object[] param;
			// this object is used to transport arbitrary objects (e.g. String
			// objects ...) instead of the typical double values.
		}

		@Override
		public void addResult(final ExtResult er) {
			// TODO implement this
		}

		@Override
		public void addResult(final AccountingResult ar) {
			// TODO implement this
		}

		@Override
		public void updateConfig(final MFarm f) {
			// TODO implement this
		}

	}

}
