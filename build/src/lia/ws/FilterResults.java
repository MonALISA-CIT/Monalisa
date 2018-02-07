package lia.ws;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import lia.Monitor.monitor.AppConfig;

/**
 * @author mickyt
 */
public class FilterResults implements Filter {

	static final int INTERVAL_DIMMENSION = 5000;
	static final int MAX_RETURNED_VALUES = AppConfig.geti("lia.ws.FilterResults.MAX_RETURNED_VALUES", 5000);

	static {
		System.err.println("Max number of results: lia.ws.FilterResults.MAX_RETURNED_VALUES= " + MAX_RETURNED_VALUES);
	}

	public Vector filterValues(Vector rez) throws org.apache.axis.AxisFault {

		Hashtable hash = new Hashtable();
		Vector rezFinal = new Vector();
		int step = 0;

		if (rez == null || rez.size() == 0)
			throw new org.apache.axis.AxisFault("No results found ...");

		for (int i = 0; i < rez.size(); i++) {

			if (rezFinal.size() >= MAX_RETURNED_VALUES) {
				String msg = "Too many results (" + rezFinal.size() + ") ..." + ".Max allowed: lia.ws.FilterResults.MAX_RETURNED_VALUES= "
						+ MAX_RETURNED_VALUES;
				System.err.println(msg);
				throw new org.apache.axis.AxisFault(msg);
			}

			if (!(rez.elementAt(i) instanceof lia.Monitor.monitor.Result) && !(rez.elementAt(i) instanceof lia.Monitor.monitor.eResult))
				continue;

			/* lia.Monitor.monitor.Result */
			Object db_result = /* (lia.Monitor.monitor.Result) */rez.elementAt(i);
			// get the key for this result

			lia.Monitor.monitor.Result dbResult = null;
			lia.Monitor.monitor.eResult dbeResult = null;

			String key = "";
			if (db_result instanceof lia.Monitor.monitor.Result) {
				key = ((lia.Monitor.monitor.Result) db_result).FarmName + ((lia.Monitor.monitor.Result) db_result).ClusterName
						+ ((lia.Monitor.monitor.Result) db_result).NodeName;
				dbResult = (lia.Monitor.monitor.Result) db_result;

			}

			if (db_result instanceof lia.Monitor.monitor.eResult) {
				key = ((lia.Monitor.monitor.eResult) db_result).FarmName + ((lia.Monitor.monitor.eResult) db_result).ClusterName
						+ ((lia.Monitor.monitor.eResult) db_result).NodeName;
				dbeResult = (lia.Monitor.monitor.eResult) db_result;
				// if (!(dbeResult.param[0] instanceof String)) {
				// continue;
				// dbeResult.param[0]=dbeResult.param[0].toString();
				// System.out.println (" ==> not string eResult! "+dbeResult.param[0]);
				// }
			} // if

			if (hash.containsKey(key)) {

				GatherStep gs = (GatherStep) hash.get(key);
				String paramN = "";

				if (db_result instanceof lia.Monitor.monitor.Result)
					paramN = ((lia.Monitor.monitor.Result) db_result).param_name[0];

				if (db_result instanceof lia.Monitor.monitor.eResult)
					paramN = ((lia.Monitor.monitor.eResult) db_result).param_name[0];

				if (gs.paramNames.contains(paramN)) {

					// if another value for this parameter arrived
					// throw it to the rezFinal Vector and set the
					// new value in the hash

					// add the result from the hash to the return value
					lia.ws.Result getToRezFinal = new lia.ws.Result();
					getToRezFinal.setFarmName(gs.farmName);
					getToRezFinal.setClusterName(gs.clusterName);
					getToRezFinal.setNodeName(gs.nodeName);

					Hashtable h = new Hashtable();
					for (int j = 0; j < gs.paramNames.size(); j++) {
						h.put(gs.paramNames.elementAt(j), gs.paramValues.elementAt(j));
					} // for

					getToRezFinal.setParam(h);
					getToRezFinal.setTime(gs.lastTime / 2 + gs.initTime / 2);
					rezFinal.add(getToRezFinal);

					// set the new value in the hash
					if (db_result instanceof lia.Monitor.monitor.Result) {
						gs = new GatherStep(dbResult.FarmName, dbResult.ClusterName, dbResult.NodeName);
						gs.setInitTime(dbResult.time);
						gs.setLastTime(dbResult.time);
						gs.addParamName(dbResult.param_name[0]);
						gs.addParamValue(Double.valueOf(dbResult.param[0]));
					}

					if (db_result instanceof lia.Monitor.monitor.eResult) {
						gs = new GatherStep(dbeResult.FarmName, dbeResult.ClusterName, dbeResult.NodeName);
						gs.setInitTime(dbeResult.time);
						gs.setLastTime(dbeResult.time);
						gs.addParamName(dbeResult.param_name[0]);
						gs.addParamValue(dbeResult.param[0]);
					}

					hash.put(key, gs);

				} else {

					// verify time to see that it is accepted
					long initTime = gs.initTime;
					long resultTime = 0;

					if (db_result instanceof lia.Monitor.monitor.Result) {
						resultTime = dbResult.time;
					}// if

					if (db_result instanceof lia.Monitor.monitor.eResult) {
						resultTime = dbeResult.time;
					}// if

					if (Math.abs(initTime - resultTime) < INTERVAL_DIMMENSION) {

						// if the value is in the current interval
						// add the parameter value in the hash
						if (db_result instanceof lia.Monitor.monitor.Result) {
							gs.addParamName(dbResult.param_name[0]);
							gs.addParamValue(Double.valueOf(dbResult.param[0]));
							gs.setLastTime(dbResult.time);
						}

						if (db_result instanceof lia.Monitor.monitor.eResult) {
							gs.addParamName(dbeResult.param_name[0]);
							gs.addParamValue(dbeResult.param[0]);
							gs.setLastTime(dbeResult.time);
						}

						hash.put(key, gs);

					} else {
						// if the time for this parameter is far away
						// throw the value from the hash in the rezFinal
						// and set the new result in the hash
						// add the result from the hash to the return value
						lia.ws.Result getToRezFinal = new lia.ws.Result();
						getToRezFinal.setFarmName(gs.farmName);
						getToRezFinal.setClusterName(gs.clusterName);
						getToRezFinal.setNodeName(gs.nodeName);

						Hashtable h = new Hashtable();

						for (int j = 0; j < gs.paramNames.size(); j++) {
							h.put(gs.paramNames.elementAt(j), gs.paramValues.elementAt(j));
						} // for

						getToRezFinal.setParam(h);
						getToRezFinal.setTime(gs.lastTime / 2 + gs.initTime / 2);

						rezFinal.add(getToRezFinal);

						if (db_result instanceof lia.Monitor.monitor.Result) {
							// set the new value in the hash
							gs = new GatherStep(dbResult.FarmName, dbResult.ClusterName, dbResult.NodeName);
							gs.setInitTime(dbResult.time);
							gs.setLastTime(dbResult.time);
							gs.addParamName(dbResult.param_name[0]);
							gs.addParamValue(Double.valueOf(dbResult.param[0]));
						}

						if (db_result instanceof lia.Monitor.monitor.eResult) {
							// set the new value in the hash
							gs = new GatherStep(dbeResult.FarmName, dbeResult.ClusterName, dbeResult.NodeName);
							gs.setInitTime(dbeResult.time);
							gs.setLastTime(dbeResult.time);
							gs.addParamName(dbeResult.param_name[0]);
							gs.addParamValue(dbeResult.param[0]);
						}

						hash.put(key, gs);

					} // if - else

				} // if - else

			} else { // the first value of this type in the current interval.

				GatherStep gs = null;
				if (db_result instanceof lia.Monitor.monitor.eResult) {

					gs = new GatherStep(dbeResult.FarmName, dbeResult.ClusterName, dbeResult.NodeName);
					gs.setInitTime(dbeResult.time);
					gs.setLastTime(dbeResult.time);
					gs.addParamName(dbeResult.param_name[0]);
					gs.addParamValue(dbeResult.param[0]);
				}

				if (db_result instanceof lia.Monitor.monitor.Result) {

					gs = new GatherStep(dbResult.FarmName, dbResult.ClusterName, dbResult.NodeName);
					gs.setInitTime(dbResult.time);
					gs.setLastTime(dbResult.time);
					gs.addParamName(dbResult.param_name[0]);
					gs.addParamValue(Double.valueOf(dbResult.param[0]));
				}

				hash.put(key, gs);
			}

		} // for

		// get the values from the hash
		for (Enumeration e = hash.elements(); e.hasMoreElements();) {
			if (rezFinal.size() >= MAX_RETURNED_VALUES) {
				throw new org.apache.axis.AxisFault("Too many results ...");
			}

			GatherStep gs = (GatherStep) e.nextElement();

			lia.ws.Result getToRezFinal = new lia.ws.Result();
			getToRezFinal.setFarmName(gs.farmName);
			getToRezFinal.setClusterName(gs.clusterName);
			getToRezFinal.setNodeName(gs.nodeName);

			Hashtable h = new Hashtable();

			for (int j = 0; j < gs.paramNames.size(); j++) {
				h.put(gs.paramNames.elementAt(j), gs.paramValues.elementAt(j));
			} // for

			getToRezFinal.setParam(h);
			getToRezFinal.setTime(gs.lastTime / 2 + gs.initTime / 2);
			rezFinal.add(getToRezFinal);

		} // for

		return rezFinal;
	} // filterValues

}
