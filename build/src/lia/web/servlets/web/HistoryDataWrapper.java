/**
 * Wrapper for the history data pages, this is only to provide a unified way of recognizing the data that is to be ploted
 */
package lia.web.servlets.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.Vector;

import lia.Monitor.monitor.monPredicate;
import lia.web.utils.ServletExtension;

/**
 * @author costing
 * @since 27.03.2006
 */
class HistoryDataWrapper {
	/**
	 * series names
	 */
	public String[]			series;

	/**
	 * predicates
	 */
	public monPredicate[]	preds;
	
	/**
	 * selected predicates
	 */
	public monPredicate[]	selectedPreds;
	
	/**
	 * @param prop
	 * @param unselectSeries
	 * @param hmSeries
	 */
	@SuppressWarnings("null")
	public HistoryDataWrapper(final Properties prop, final String[] unselectSeries, final HashMap<String, String> hmSeries) {
		Vector<String> vSeries = ServletExtension.toVector(prop, "series.names", null);
		Vector<String> vPreds = ServletExtension.toVector(prop, "series.predicates", null);

		series = null;

		int i;

		if (vSeries.size() > 0 && vPreds.size() == vSeries.size()) {
			preds = new monPredicate[vPreds.size()];
			series = new String[vSeries.size()];

			for (i = 0; i < preds.length; i++) {
				preds[i] = ServletExtension.toPred(vPreds.get(i));
				series[i] = vSeries.get(i);
			}
		} else {
			String[] vsFarms = Utils.getValues(prop, "Farms");
			String[] vsClusters = Utils.getValues(prop, "Clusters");
			String[] vsNodes = Utils.getValues(prop, "Nodes");
			String[] vsFunctions = Utils.getValues(prop, "Functions");
			String[] vsFunctionSuff = Utils.getValues(prop, "FuncSuff");

			String[] vsWildcards = Utils.getValues(prop, "Wildcards");

			int w = -1;
			
			int len = 0;

			for (i = 0; vsWildcards != null && i < vsWildcards.length; i++) {
				int newlen = 0;

				String[] newseries = null;

				if (vsWildcards[i].equals("F")) {
					newlen = vsFarms != null ? vsFarms.length : 0;
					newseries = vsFarms;
				}

				if (vsWildcards[i].equals("C")) {
					newlen = vsClusters != null ? vsClusters.length : 0;
					newseries = vsClusters;
				}

				if (vsWildcards[i].equals("N")) {
					newlen = vsNodes != null ? vsNodes.length : 0;
					newseries = vsNodes;
				}

				if (vsWildcards[i].equals("f")) {
					newlen = vsFunctions != null ? vsFunctions.length : 0;
					newseries = vsFunctions;
				}

				if (newlen > 0) {
					w = i;
					len = newlen;
					series = newseries;
				}
			}

			if (len <= 0 || w < 0) {
				System.err.println("RETURN NULL BECAUSE : len=" + len + ", w=" + w);
				System.err.println("FARMS : '" + ServletExtension.pgets(prop, "Farms") + "'");
				System.err.println("CLUSTERS : '" + ServletExtension.pgets(prop, "Clusters") + "'");
				System.err.println("NODES : '" + ServletExtension.pgets(prop, "Nodes") + "'");
				System.err.println("FUNCTIONS : '" + ServletExtension.pgets(prop, "Clusters") + "'");
				System.err.println("FUNCSUFF : '" + ServletExtension.pgets(prop, "FuncSuff") + "'");
				System.err.println("WILDCARDS : '" + ServletExtension.pgets(prop, "Wildcards") + "'");
				
				System.err.println("Here is the parsed configuration dump:\n"+prop);
			}

			preds = new monPredicate[len];

			for (i = 0; i < len; i++) {
				preds[i] = new monPredicate();

				if (vsWildcards[w].equals("F"))
					preds[i].Farm = vsFarms[i];
				else
					preds[i].Farm = (vsFarms != null && vsFarms.length > 0) ? vsFarms[0] : "*";

				if (vsWildcards[w].equals("C"))
					preds[i].Cluster = vsClusters[i];
				else
					preds[i].Cluster = (vsClusters != null && vsClusters.length > 0) ? vsClusters[0] : "*";

				if (vsWildcards[w].equals("N"))
					preds[i].Node = vsNodes[i];
				else
					preds[i].Node = (vsNodes != null && vsNodes.length > 0) ? vsNodes[0] : "*";

				if (vsWildcards[w].equals("f"))
					preds[i].parameters = new String[] { vsFunctions[i] + ((vsFunctionSuff != null && vsFunctionSuff.length > 0) ? vsFunctionSuff[0] : "") };
				else
					preds[i].parameters = new String[] { (vsFunctions != null && vsFunctions.length > 0) ? (vsFunctions[0] + ((vsFunctionSuff != null && vsFunctionSuff.length > 0) ? vsFunctionSuff[0] : "")) : "*" };
			}
		}
		
		String[] serieso = new String[series.length];
		for (i = 0; i < series.length; i++)
			serieso[i] = series[i];

		series = Utils.sortSeries(series, prop, unselectSeries, hmSeries);
		monPredicate[] predsn = new monPredicate[preds.length];

		for (i = 0; i < series.length; i++) {
			for (int j = 0; j < serieso.length; j++)
				if (series[i].equals(serieso[j])) {
					predsn[i] = preds[j];
					break;
				}
		}

		preds = predsn;

		selectedPreds = preds;
		
		if (hmSeries.size() > 0){
			final ArrayList<monPredicate> alSelected = new ArrayList<monPredicate>(hmSeries.size());
		
			for (i=0; i<series.length; i++){
				if (hmSeries.containsKey(series[i]))
					alSelected.add(preds[i]);
			}
		
			if (alSelected.size() > 0){
				selectedPreds = new monPredicate[alSelected.size()];
				for (i=0; i<alSelected.size(); i++)
					selectedPreds[i] = alSelected.get(i);
			}
			else{
				hmSeries.clear();
			}
		}
	}

}
