/**
 * 
 */
package lia.util.actions;

import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import lazyj.Format;
import lia.Monitor.Store.Fast.DB;
import lia.util.MLProperties;
import lia.util.actions.Action.SeriesState;

/**
 * @author costing
 * @since Apr 14, 2008
 */
public class ActionUtils {

	private static final Logger logger = Logger.getLogger(ActionUtils.class.getName());

	private static final ExecutorService asyncActions;

	/**
	 * Constant for the OK state
	 */
	public static final int STATE_OK = 0;

	/**
	 * Constant for the ERROR state
	 */
	public static final int STATE_ERR = 1;

	/**
	 * Constant for the FLIP-FLOP state.
	 */
	public static final int STATE_FLIPFLOP = 2;

	static {
		ExecutorService es = null;

		try {
			es = lia.util.threads.MLExecutorsFactory.getCachedThreadPool("lia.util.Actions.ActionExecutor");
		} catch (Exception e) {
			logger.log(Level.WARNING, "Cannot create async executor", e);
		}

		asyncActions = es;
	}

	/**
	 * Get async executor
	 * 
	 * @return async actions executor
	 */
	public static final ExecutorService getExecutor() {
		return asyncActions;
	}

	/**
	 * Apply standard formatting to a message string. Will replace:<br>
	 * #MSG : message, if set. Usually one of the action.N.report_ok, .report_err or .report_flip_flop<br>
	 * #VALUE : result of the value evaluation. When the rule is evaluated directly, without the intermediate
	 * "value" key, this will be "&lt;unknown&gt;"<br>
	 * #REARM : 0 when it's the first decision, 1 when it's a rearmed decision
	 * #STATE : current state of the series. 0 = OK, 1 = ERR, 2 = FLIP FLOP
	 * #HIST : history of the last values
	 * #HSET : set of distinct values from the history
	 * #0 ... #N : (N=series.count), value of each key level
	 * 
	 * 
	 * @param ss
	 * @param sMessage
	 * @param sFormatParam
	 * @param mlp
	 * @return the formatted message
	 */
	public static String apply(final SeriesState ss, final String sMessage, final String sFormatParam, final MLProperties mlp) {
		String sFormat = sFormatParam;

		if (sMessage != null) {
			sFormat = MLProperties.replace(sFormat, "#MSG", sMessage);
		}

		sFormat = MLProperties.replace(sFormat, "#VALUE", ss.sValue != null ? ss.sValue : "<unknown>");

		sFormat = MLProperties.replace(sFormat, "#REARM", ss.isRearmedAction() ? "1" : "0");

		for (int i = ss.alSeriesNames.size() - 1; i >= 0; i--) {
			final String s = ss.alSeriesNames.get(i);

			if (s == null) {
				logger.log(Level.WARNING, "Series " + i + " was null : " + ss);
				continue;
			}

			sFormat = MLProperties.replace(sFormat, "#" + i, s);
		}

		sFormat = MLProperties.replace(sFormat, "#STATE", "" + ss.iState);

		sFormat = mlp.parseOption("apply", sFormat, sFormat, true);

		final TreeSet<String> tsValues = new TreeSet<String>(ss.llValuesHist);

		sFormat = MLProperties.replace(sFormat, "#HSET", collectionToString(tsValues));

		sFormat = MLProperties.replace(sFormat, "#HIST", collectionToString(ss.llValuesHist));

		if (sFormat.indexOf("#INTERVAL_ERR") >= 0) {
			final DB db = new DB();

			db.setReadOnly(true);

			db.query("SELECT as_last_err FROM action_states WHERE as_file='" + Format.escSQL(ss.getFile()) + "' AND as_key='" + Format.escSQL(ss.getKey()) + "';");

			final long lLastErr = db.moveNext() ? db.getl(1) * 1000 : 0;

			String sErrorInterval = "n/a";

			if (lLastErr > 0) {
				sErrorInterval = Format.toInterval(System.currentTimeMillis() - lLastErr);
			}

			sFormat = MLProperties.replace(sFormat, "#INTERVAL_ERR", sErrorInterval);
		}

		if (sFormat.indexOf("#INTERVAL_OK") >= 0) {
			final DB db = new DB();

			db.setReadOnly(true);

			db.query("SELECT as_last_ok FROM action_states WHERE as_file='" + Format.escSQL(ss.getFile()) + "' AND as_key='" + Format.escSQL(ss.getKey()) + "';");

			final long lLastOk = db.moveNext() ? db.getl(1) * 1000 : 0;

			String sOKInterval = "n/a";

			if (lLastOk > 0) {
				sOKInterval = Format.toInterval(System.currentTimeMillis() - lLastOk);
			}

			sFormat = MLProperties.replace(sFormat, "#INTERVAL_OK", sOKInterval);
		}

		return sFormat;
	}

	/**
	 * Make a PostgreSQL array out of the elements of the given collection
	 * 
	 * @param c
	 * @return PostgreSQL array representation of the collection elements
	 */
	public static String collectionToString(final Collection<String> c) {
		final StringBuilder sb = new StringBuilder("{");

		final Iterator<String> it = c.iterator();

		int iCnt = 0;

		while (it.hasNext()) {
			if (iCnt++ > 0) {
				sb.append(",");
			}

			sb.append(it.next().toString());
		}

		return sb.append("}").toString();
	}

}
