package lia.Monitor.modules;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.util.Utils;
import lia.util.threads.MonALISAExecutors;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * @author costing
 * @since 2014-07-31
 */
public class SquidsBySNMP extends AbstractSchJobMonitoring {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Logging facility
	 */
	static final Logger logger = Logger.getLogger(SquidsBySNMP.class.getCanonicalName());

	private String initArgs = "";

	/**
     * 
     */
	public SquidsBySNMP() {
	}

	@Override
	public boolean isRepetitive() {
		return true;
	}

	@Override
	public String getTaskName() {
		return "SquidsBySNMP";
	}

	private String sJSONListURL = "http://wlcg-squid-monitor.cern.ch/grid-squids.json";

	private String sActiveMLList = "http://cvmfsmon.cern.ch/rest/*/IPs/localhost/ip_visible*?Accept=text/json";

	private final Map<String, snmp_Squid> activeModules = new HashMap<String, snmp_Squid>();

	private long lLastRefreshList = 0;

	private void refreshModulesList() {
		if (System.currentTimeMillis() - this.lLastRefreshList < 1000 * 60 * AppConfig.geti("lia.Monitor.modules.SquidsBySNMP.refreshIntervalMinutes", 30))
			return;

		this.lLastRefreshList = System.currentTimeMillis();

		final Set<String> targetHosts;

		try {
			targetHosts = getTargetHosts();
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Exception parsing " + this.sJSONListURL, t);
			return;
		}

		if (targetHosts == null || targetHosts.size() == 0)
			return;

		final Set<String> targetHostsCopy = new HashSet<String>(targetHosts);

		Set<String> activeMLHosts = null;

		try {
			activeMLHosts = getActiveMLHosts();
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Exception parsing " + this.sActiveMLList, t);
		}

		if (activeMLHosts != null)
			targetHostsCopy.removeAll(activeMLHosts);

		final Set<String> modulesToRemove = new HashSet<String>(this.activeModules.keySet());
		modulesToRemove.removeAll(targetHostsCopy);

		for (final String host : modulesToRemove)
			this.activeModules.remove(host);

		targetHostsCopy.removeAll(this.activeModules.keySet());

		for (final String host : targetHostsCopy) {
			final Set<InetAddress> publicAddresses = new HashSet<InetAddress>();

			try {
				for (final InetAddress ia : InetAddress.getAllByName(host))
					if (activeMLHosts != null && activeMLHosts.contains(ia.getHostAddress()))
						logger.log(Level.INFO, "Ignoring SNMP service " + host + " because all its IP addresses match services already monitored by a MonALISA service");
					else
						if ((ia instanceof Inet4Address) && !ia.isAnyLocalAddress() && !ia.isMulticastAddress())
							publicAddresses.add(ia);
			}
			catch (final UnknownHostException e1) {
				logger.log(Level.WARNING, "Ignoring " + host + " since it cannot be resolved", e1);
			}

			for (final InetAddress ia : publicAddresses) {
				final String ad = ia.getHostAddress();

				try {
					final snmp_Squid newModule = new snmp_Squid();

					final String nodeKey = publicAddresses.size() > 1 ? host + "_" + ad : host;

					newModule.init(new MNode(nodeKey, ad, this.node.cluster, this.node.farm), this.initArgs);

					this.activeModules.put(nodeKey, newModule);
				}
				catch (final Exception e) {
					logger.log(Level.WARNING, "Exception instantiating snmp module for " + host + " (" + ad + ")", e);
				}
			}
		}
	}

	private Set<String> getTargetHosts() {
		final Set<String> ret = new HashSet<String>();

		final StringTokenizer st = new StringTokenizer(this.sJSONListURL, ",");

		while (st.hasMoreTokens()) {
			final String url = st.nextToken();

			try {
				String result;
				try {
					result = lazyj.Utils.download(url, null);
				}
				catch (final IOException e) {
					logger.log(Level.WARNING, "Exception fetching " + url, e);

					return null;
				}

				final JSONParser parser = new JSONParser();

				Object parseResult;
				try {
					parseResult = parser.parse(result);
				}
				catch (final ParseException e) {
					logger.log(Level.WARNING, "Exception parsing content of " + url, e);

					return null;
				}

				final JSONObject root = (JSONObject) parseResult;

				ret.addAll(root.keySet());
			}
			catch (final Throwable t) {
				logger.log(Level.WARNING, "Other error parsing content of " + url, t);
			}
		}

		return ret;
	}

	private Set<String> getActiveMLHosts() {
		final StringTokenizer st = new StringTokenizer(this.sActiveMLList, ",");

		final Set<String> ret = new HashSet<String>();

		while (st.hasMoreTokens()) {
			final String url = st.nextToken();

			try {
				String result;
				try {
					result = lazyj.Utils.download(url, null);
				}
				catch (final IOException e) {
					logger.log(Level.WARNING, "Exception fetching " + this.sJSONListURL, e);

					return null;
				}

				final JSONParser parser = new JSONParser();

				Object parseResult;
				try {
					parseResult = parser.parse(result);
				}
				catch (final ParseException e) {
					logger.log(Level.WARNING, "Exception parsing content of " + this.sJSONListURL, e);

					return null;
				}

				final JSONObject root = (JSONObject) parseResult;

				final JSONArray entries = (JSONArray) root.get("results");

				if (entries == null)
					return null;

				for (final Object o : entries) {
					final String hostname = (String) ((JSONObject) o).get("Farm");

					ret.add(hostname);
					ret.add((String) ((JSONObject) o).get("Value"));

					try {
						for (final InetAddress ia : InetAddress.getAllByName(hostname))
							ret.add(ia.getHostAddress());
					}
					catch (final Throwable t) {
						// ignore
					}
				}
			}
			catch (final Throwable t) {
				logger.log(Level.WARNING, "Other error parsing " + url, t);
			}
		}

		return ret;
	}

	private static class SNMPQuery implements Runnable {
		private final snmp_Squid host;

		private Vector<Object> results = null;

		public SNMPQuery(final snmp_Squid host) {
			this.host = host;
		}

		@Override
		public void run() {
			try {
				this.results = (Vector<Object>) this.host.doProcess();
			}
			catch (final Throwable t) {
				logger.log(Level.WARNING, "Exception executing snmp query on " + this.host.getNode().name, t);
			}
		}

		public Vector<Object> getData() {
			return this.results;
		}
	}

	private Vector<Object> cacheData = null;
	private long lastCacheRefresh = 0;

	@Override
	public Object doProcess() throws Exception {
		if (this.cacheData == null || (System.currentTimeMillis() - this.lastCacheRefresh > 1000 * 60 * AppConfig.getl("lia.Monitor.modules.SquidsBySNMP.cacheData", 5))) {
			this.lastCacheRefresh = System.currentTimeMillis();

			refreshModulesList();

			this.cacheData = new Vector<Object>(this.activeModules.size() * 2);

			final ScheduledExecutorService executor = MonALISAExecutors.getMLHelperExecutor();

			final List<Future<SNMPQuery>> futures = new ArrayList<Future<SNMPQuery>>(this.activeModules.size());

			for (final snmp_Squid host : this.activeModules.values()) {
				final SNMPQuery query = new SNMPQuery(host);

				futures.add(executor.submit(query, query));
			}

			for (final Future<SNMPQuery> future : futures) {
				final SNMPQuery query = future.get();

				final Vector<Object> results = query.getData();

				if (results != null)
					this.cacheData.addAll(results);
			}
		}

		return this.cacheData;
	}

	@Override
	protected MonModuleInfo initArgs(final String args) {
		if (args != null && args.length() > 0) {
			final StringTokenizer st = new StringTokenizer(args, ";");

			while (st.hasMoreTokens()) {
				final String confToken = st.nextToken().trim();

				final int idxEq = confToken.indexOf('=');
				final int idxCo = confToken.indexOf(':');

				final int idx = idxEq > 0 ? (idxCo > 0 ? Math.min(idxEq, idxCo) : idxEq) : idxCo;

				if (idx > 0) {
					final String key = confToken.substring(0, idx).trim();
					final String value = confToken.substring(idx + 1).trim();

					if (key.equalsIgnoreCase("listURL"))
						this.sJSONListURL = value;
					else
						if (key.equalsIgnoreCase("mlURL"))
							this.sActiveMLList = value;
				}
			}
		}

		this.initArgs = args;

		return null;
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(final String[] args) throws Exception {
		final SquidsBySNMP sbs = new SquidsBySNMP();
		sbs.init(new MNode("localhost", null, null), "");

		while (true) {
			Utils.dumpResults(sbs.doProcess());

			try {
				Thread.sleep(60 * 1000);
			}
			catch (final InterruptedException ie) {
				// ignore
			}
		}
	}
}
