package lia.web.servlets.web;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lazyj.Format;
import lia.Monitor.Store.Cache;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.TimestampedResult;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.web.utils.ServletExtension;
import lia.web.utils.ThreadedPage;

/**
 * @author costing
 * @since Oct 1, 2010
 */
public class Rest extends ServletExtension {
	/**
	 * Logging facility
	 */
	private static final Logger logger = Logger.getLogger(Rest.class.getCanonicalName());

	private static final long serialVersionUID = 1L;

	private String sFarm;
	private String sCluster;
	private String sNode;
	private long tmin = -1;
	private long tmax = -1;
	private String sParameter;
	private String sCondition;
	private boolean timeSet = false;

	private long lPageStart = 0;

	private void parseURL(final String sPath) {
		this.sFarm = this.sCluster = this.sNode = this.sParameter = this.sCondition = null;

		this.tmin = this.tmax = -1;
		this.timeSet = false;

		final StringTokenizer st = new StringTokenizer(sPath, "/");

		final int cnt = st.countTokens();

		if (cnt >= 1)
			this.sFarm = st.nextToken();

		if (cnt >= 2)
			this.sCluster = st.nextToken();

		if (cnt >= 3)
			this.sNode = st.nextToken();

		if (cnt >= 5) {
			// are the two parameters numeric ?

			final String s4 = st.nextToken();
			final String s5 = st.nextToken();

			try {
				final long t1 = Long.parseLong(s4);
				final long t2 = Long.parseLong(s5);

				this.tmin = t1;
				this.tmax = t2;

				this.timeSet = ((t1 != 0) && (t1 != -1)) || ((t2 != 0) && (t2 != -1));
			}
			catch (@SuppressWarnings("unused") final NumberFormatException nfe) {
				this.sParameter = s4;
				this.sCondition = s5;
			}
		}

		if (st.hasMoreTokens())
			this.sParameter = st.nextToken();

		if (st.hasMoreTokens())
			this.sCondition = st.nextToken();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Farm=" + this.sFarm + "\nCluster=" + this.sCluster + "\nNode=" + this.sNode + "\nParameter=" + this.sParameter + "\ntMin=" + this.tmin + "\ntMax=" + this.tmax + "\ntimeSet="
				+ this.timeSet + "\nCondition=" + this.sCondition;
	}

	private boolean setFields(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		this.request = req;
		this.response = resp;

		this.lPageStart = System.currentTimeMillis();

		if (!ThreadedPage.acceptRequest(req, resp))
			return false;

		this.osOut = this.response.getOutputStream();
		this.pwOut = new PrintWriter(new OutputStreamWriter(this.osOut));

		parseURL(this.request.getPathInfo());

		return true;
	}

	private void endPage() {
		Utils.logRequest("Rest" + this.request.getPathInfo(), (int) (System.currentTimeMillis() - this.lPageStart), this.request, false, System.currentTimeMillis() - this.lPageStart);
	}

	private String getContentType() {
		String sContentType = "text/plain";

		String sAccept = this.request.getParameter("Accept");

		if ((sAccept == null) || (sAccept.length() == 0))
			sAccept = this.request.getHeader("Accept");

		if (sAccept != null) {
			sAccept = sAccept.toLowerCase();

			if ((sAccept.indexOf("application/json") >= 0) || (sAccept.indexOf("text/json") >= 0))
				sContentType = "application/json";
			else
				if (sAccept.indexOf("text/html") >= 0)
					sContentType = "text/html";
				else
					if (sAccept.indexOf("text/xml") >= 0)
						sContentType = "text/xml";
					else
						if (sAccept.indexOf("text/csv") >= 0)
							sContentType = "text/csv";
		}

		return sContentType;
	}

	/**
	 * Convert any ML object (Result, eResult, List of them) to a fixed form
	 *
	 * @param o
	 * @param verify
	 *            condition that the values have to meet (can be null)
	 * @return the list
	 */
	public static List<Map<String, String>> resultsToList(final Object o, final ConditionVerifier verify) {
		if (o == null)
			return null;

		final List<Map<String, String>> ret = new ArrayList<>();

		if (o instanceof Result) {
			final Result r = (Result) o;

			for (int i = 0; i < r.param.length; i++) {
				if ((verify != null) && !verify.matches(r.param[i]))
					continue;

				final Map<String, String> m = new LinkedHashMap<>();

				m.put("Farm", r.FarmName);
				m.put("Cluster", r.ClusterName);
				m.put("Node", r.NodeName);
				m.put("Parameter", r.param_name[i]);
				m.put("Value", Utils.showDouble(r.param[i]));
				m.put("Timestamp", String.valueOf(r.time));

				ret.add(m);
			}
		}
		else
			if (o instanceof eResult) {
				final eResult r = (eResult) o;

				for (int i = 0; i < r.param.length; i++) {
					if ((verify != null) && !verify.matches(r.param[i]))
						continue;

					final Map<String, String> m = new LinkedHashMap<>();

					m.put("Farm", r.FarmName);
					m.put("Cluster", r.ClusterName);
					m.put("Node", r.NodeName);
					m.put("Parameter", r.param_name[i]);
					m.put("Value", r.param[i].toString());
					m.put("Timestamp", String.valueOf(r.time));

					ret.add(m);
				}
			}
			else
				if (o instanceof Collection<?>) {
					final Collection<?> c = (Collection<?>) o;

					for (final Object o2 : c) {
						final List<Map<String, String>> temp = resultsToList(o2, verify);

						if (temp != null)
							ret.addAll(temp);
					}
				}

		return ret;
	}

	/**
	 * Convert to CSV
	 *
	 * @param list
	 * @return CSV output
	 */
	public static String collectionToCSV(final Collection<Map<String, String>> list) {
		boolean bFirst = true;

		final StringBuilder sb = new StringBuilder();

		for (final Map<String, String> m : list) {
			if (bFirst) {
				sb.append('#');

				for (final String k : m.keySet()) {
					if (sb.length() > 1)
						sb.append('|');

					sb.append(stats.escCSV(k));
				}
				sb.append('\n');

				bFirst = false;
			}

			boolean b = true;

			for (final String v : m.values()) {
				if (!b)
					sb.append('|');
				else
					b = false;

				sb.append(stats.escCSV(v));
			}

			sb.append('\n');
		}

		return sb.toString();
	}

	/**
	 * Convert to plain text
	 *
	 * @param list
	 * @return plain text
	 */
	public static String collectionToPlainText(final Collection<Map<String, String>> list) {
		final StringBuilder sb = new StringBuilder();

		for (final Map<String, String> m : list) {
			boolean b = true;

			for (final String v : m.values()) {
				if (!b)
					sb.append('/');
				else
					b = false;

				sb.append(v);
			}

			sb.append('\n');
		}

		return sb.toString();
	}

	/**
	 * Convert to JSON
	 *
	 * @param list
	 * @return JSON output
	 */
	public static String collectionToJSON(final Collection<Map<String, String>> list) {
		final StringBuilder sb = new StringBuilder("{\"results\":[");

		boolean b = true;

		for (final Map<String, String> m : list) {
			if (b)
				b = false;
			else
				sb.append(',');

			sb.append("\n{");

			boolean b2 = true;

			for (final Map.Entry<String, String> me : m.entrySet()) {
				if (b2)
					b2 = false;
				else
					sb.append(',');

				sb.append('"').append(Format.escJSON(me.getKey())).append("\":\"").append(Format.escJSON(me.getValue())).append('"');
			}

			sb.append("}");
		}

		sb.append("\n]}");

		return sb.toString();
	}

	/**
	 * Convert to XML
	 *
	 * @param list
	 * @param metadata
	 * @return XML output
	 */
	public static String collectionToXML(final Collection<Map<String, String>> list, final String metadata) {
		final StringBuilder sb = new StringBuilder("<results");

		if ((metadata != null) && (metadata.length() > 0))
			sb.append(" metadata=\"").append(Format.escHtml(metadata)).append('"');

		sb.append(" timestamp=\"").append(System.currentTimeMillis()).append("\">");

		for (final Map<String, String> m : list) {
			sb.append("\n<result");

			for (final Map.Entry<String, String> me : m.entrySet())
				sb.append(' ').append(me.getKey()).append("=\"").append(Format.escHtml(me.getValue())).append('"');

			sb.append("/>");
		}

		sb.append("\n</results>");

		return sb.toString();
	}

	/**
	 * Convert to HTML
	 *
	 * @param list
	 * @param metadata
	 *            metadata to add to the HTML main tag
	 * @return HTML output
	 */
	public static String collectionToHTML(final Collection<Map<String, String>> list, final String metadata) {
		boolean bFirst = true;

		final StringBuilder sb = new StringBuilder("<table");

		if ((metadata != null) && (metadata.length() > 0))
			sb.append(" metadata=\"").append(Format.escHtml(metadata)).append('"');

		sb.append(" timestamp=\"").append(System.currentTimeMillis()).append("\"");

		sb.append(">\n");

		for (final Map<String, String> m : list) {
			if (bFirst) {
				sb.append("<thead>\n<tr>\n");

				for (final String k : m.keySet())
					sb.append("<th>").append(Format.escHtml(k)).append("</th>\n");

				sb.append("</tr>\n</thead>\n<tbody>");

				bFirst = false;
			}

			sb.append("\n<tr>");

			for (final String v : m.values())
				sb.append("\n<td>").append(Format.escHtml(v)).append("</td>");

			sb.append("\n</tr>");
		}

		sb.append("\n</tbody>\n</table>");

		return sb.toString();
	}

	private static final Comparator<Map<String, String>> resultsComparator = new Comparator<Map<String, String>>() {

		@Override
		public int compare(final Map<String, String> o1, final Map<String, String> o2) {
			int diff = o1.get("Farm").compareToIgnoreCase(o2.get("Farm"));

			if (diff != 0)
				return diff;

			diff = o1.get("Cluster").compareToIgnoreCase(o2.get("Cluster"));

			if (diff != 0)
				return diff;

			diff = o1.get("Node").compareToIgnoreCase(o2.get("Node"));

			if (diff != 0)
				return diff;

			diff = o1.get("Parameter").compareToIgnoreCase(o2.get("Parameter"));

			return diff;
		}

	};

	/**
	 * Convert to String some Results
	 *
	 * @param results
	 * @param sContentType
	 * @param verify
	 *            condition that the values must meet (can be null)
	 * @param metadata
	 *            metadata
	 * @return the string, in a format dependent on the content type
	 */
	public static String resultsToString(final List<TimestampedResult> results, final String sContentType, final ConditionVerifier verify, final String metadata) {
		final List<Map<String, String>> resultsMap = resultsToList(results, verify);

		Collections.sort(resultsMap, resultsComparator);

		return collectionToString(resultsMap, sContentType, metadata);
	}

	/**
	 * Convert a collection to string
	 *
	 * @param c
	 * @param sContentType
	 * @param metadata
	 *            metadata to attach to the data
	 * @return string, in the format indicated by content type
	 */
	public static String collectionToString(final Collection<Map<String, String>> c, final String sContentType, final String metadata) {
		if (sContentType.equals("text/csv"))
			return collectionToCSV(c);

		if (sContentType.equals("text/plain"))
			return collectionToPlainText(c);

		if (sContentType.equals("application/json"))
			return collectionToJSON(c);

		if (sContentType.equals("text/xml"))
			return collectionToXML(c, metadata);

		if (sContentType.equals("text/html"))
			return collectionToHTML(c, metadata);

		return null;

	}

	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		if (!setFields(req, resp))
			return;

		final String sContentType = getContentType();

		this.response.setContentType(sContentType);

		if (this.sParameter != null) {
			final monPredicate pred = new monPredicate(this.sFarm, this.sCluster, this.sNode, this.tmin, this.tmax, this.sParameter.split("[|]"), null);

			final boolean bFine = logger.isLoggable(Level.FINE);
			// final boolean bFiner = logger.isLoggable(Level.FINER);
			// final boolean bFinest = logger.isLoggable(Level.FINEST);

			if (bFine) {
				logger.log(Level.FINE, "Parsed request is : " + this.toString());
				logger.log(Level.FINE, "Predicate is : " + pred);
			}

			List<TimestampedResult> results = Cache.getLastValues(pred);

			if (bFine)
				logger.log(Level.FINE, "Matching results from cache: " + results.size());

			if (this.timeSet) {
				results = Cache.filterByTime(results, pred);

				if (bFine)
					logger.log(Level.FINE, "After time filtering I'm left with: " + results.size());
			}

			final ConditionVerifier verify = this.sCondition != null ? new ConditionVerifier(this.sCondition) : null;

			final String content = resultsToString(results, sContentType, verify, this.request.getPathInfo());

			ServletExtension.writeResponse(content, req, resp, null);

			endPage();

			return;
		}

		final monPredicate pred = new monPredicate(this.sFarm == null ? "*" : this.sFarm, this.sCluster == null ? "*" : this.sCluster, this.sNode == null ? "*" : this.sNode, this.tmin, this.tmax,
				new String[] { "*" }, null);

		List<TimestampedResult> results = Cache.getLastValues(pred);

		if (this.timeSet)
			results = Cache.filterByTime(results, pred);

		final Map<String, Map<String, String>> browse = new TreeMap<>();

		for (final Object o : results) {
			String sKey = null;

			final Map<String, String> m = new LinkedHashMap<>();

			if (o instanceof Result) {
				final Result r = (Result) o;

				m.put("Farm", r.FarmName);
				sKey = r.FarmName;

				if (this.sFarm != null) {
					m.put("Cluster", r.ClusterName);
					sKey += "/" + r.ClusterName;
				}

				if (this.sCluster != null) {
					m.put("Node", r.NodeName);
					sKey += "/" + r.NodeName;
				}

				if (this.sNode != null) {
					m.put("Parameter", r.param_name[0]);
					sKey += "/" + r.param_name[0];
				}
			}
			else
				if (o instanceof eResult) {
					final eResult r = (eResult) o;

					m.put("Farm", r.FarmName);
					sKey = r.FarmName;

					if (this.sFarm != null) {
						m.put("Cluster", r.ClusterName);
						sKey += "/" + r.ClusterName;
					}

					if (this.sCluster != null) {
						m.put("Node", r.NodeName);
						sKey += "/" + r.NodeName;
					}

					if (this.sNode != null) {
						m.put("Parameter", r.param_name[0]);
						sKey += "/" + r.param_name[0];
					}
				}

			if ((sKey != null) && !browse.containsKey(sKey))
				browse.put(sKey, m);
		}

		final String content = collectionToString(browse.values(), getContentType(), this.request.getPathInfo());

		ServletExtension.writeResponse(content, req, resp, null);

		endPage();
	}

	@Override
	protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		if (!setFields(req, resp))
			return;

		endPage();
	}

	@Override
	protected void doPut(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		if (!setFields(req, resp))
			return;

		endPage();
	}

	@Override
	protected void doDelete(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		if (!setFields(req, resp))
			return;

		endPage();
	}

	public static void main(final String[] args) {
		final Rest rest = new Rest();
		rest.parseURL("GSI/CLUSTER_GSIAF_Nodes/*/-60000/-1/processes|load5/2:");

		System.err.println(rest);
		System.err.println(Arrays.toString(rest.sParameter.split("[|]")));

		final Vector<TimestampedResult> v = new Vector<>();

		final Result r = new Result();
		r.time = System.currentTimeMillis();
		r.FarmName = "f";
		r.ClusterName = "c";
		r.NodeName = "n";
		r.addSet("p1", 1);
		r.addSet("p2", 2);

		v.add(r);

		System.err.println(resultsToString(v, "application/json", null, null));
	}
}
