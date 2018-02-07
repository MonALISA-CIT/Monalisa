package lia.Monitor.modules;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.snmpMon2;
import lia.util.ntp.NTPDate;
import snmp.SNMPObject;

/**
 * @author costing
 * @since 2014-07-30
 */
public class snmp_Squid extends snmpMon2 implements MonitoringModule {
	private static final long serialVersionUID = -8431030196784344871L;

	/** The Logger */
	private static final Logger logger = Logger.getLogger(snmp_Squid.class.getName());

	static public String ModuleName = "snmp_Squid";

	// http://wiki.squid-cache.org/Features/Snmp
	private static final String MIB_BASE = "1.3.6.1.4.1.3495.1.";

	private static final Map<String, String> ABS_VALUES = new HashMap<String, String>();

	private static final Map<String, String> STRING_VALUES = new HashMap<String, String>();

	private static final Map<String, String> COUNTERS = new HashMap<String, String>();

	private static final Map<String, String> RATES = new HashMap<String, String>();

	private static final String[] sOids;

	static {
		ABS_VALUES.put(MIB_BASE + "1.1.0", "vm_size"); // Storage Mem size in KB
		ABS_VALUES.put(MIB_BASE + "1.2.0", "storage_size"); // Storage Swap size in KB
		ABS_VALUES.put(MIB_BASE + "2.5.1.0", "cache_mem"); // The value of the cache_mem parameter in MB
		ABS_VALUES.put(MIB_BASE + "2.5.2.0", "swap_max_size"); // The total of the cache_dir space allocated in MB
		ABS_VALUES.put(MIB_BASE + "2.5.3.0", "swap_high_WM"); // Cache Swap High Water Mark (percentage)
		ABS_VALUES.put(MIB_BASE + "2.5.4.0", "swap_low_WM"); // Cache Swap Low Water Mark (percentage)
		ABS_VALUES.put(MIB_BASE + "3.1.5.0", "cpu_usage"); // The percentage use of the CPU
		ABS_VALUES.put(MIB_BASE + "3.1.6.0", "max_res_size"); // Maximum Resident Size in KB
		ABS_VALUES.put(MIB_BASE + "3.1.7.0", "num_obj_count"); // Number of objects stored by the cache
		ABS_VALUES.put(MIB_BASE + "3.1.10.0", "fd_unused"); // Available number of file descriptors
		ABS_VALUES.put(MIB_BASE + "3.1.11.0", "fd_reserved"); // Reserved number of file descriptors
		ABS_VALUES.put(MIB_BASE + "3.1.12.0", "fd_inuse"); // Number of file descriptors in use
		ABS_VALUES.put(MIB_BASE + "3.1.13.0", "fd_maxused"); // Highest file descriptors in use
		ABS_VALUES.put(MIB_BASE + "3.2.1.14.0", "swap_size"); // Storage Swap size
		ABS_VALUES.put(MIB_BASE + "3.2.1.15.0", "cache_clients"); // Number of clients accessing cache
		ABS_VALUES.put(MIB_BASE + "3.2.2.1.1.5", "client_http.all_median_svc_time"); // HTTP all service time (ms)
		ABS_VALUES.put(MIB_BASE + "3.2.2.1.3.5", "client_http.miss_median_svc_time"); // HTTP miss service time (ms)
		ABS_VALUES.put(MIB_BASE + "3.2.2.1.4.5", "client_http.nm_median_svc_time"); // HTTP hit not-modified service time (ms)
		ABS_VALUES.put(MIB_BASE + "3.2.2.1.5.5", "client_http.hit_median_svc_time"); // HTTP hit service time (ms)
		ABS_VALUES.put(MIB_BASE + "3.2.2.1.6.5", "icp.query_median_svc_time"); // ICP query service time (ms)
		ABS_VALUES.put(MIB_BASE + "3.2.2.1.7.5", "icp.reply_median_svc_time"); // ICP reply service time (ms)
		ABS_VALUES.put(MIB_BASE + "3.2.2.1.8.5", "dns.median_svc_time"); // DNS service time (ms)
		ABS_VALUES.put(MIB_BASE + "3.2.2.1.11.5", "client_http.nh_median_svc_time"); // HTTP refresh hit service time (ms)
		ABS_VALUES.put(MIB_BASE + "3.2.2.1.9.5", "client_http.request_hit_ratio"); // Request Hit Ratios (%)
		ABS_VALUES.put(MIB_BASE + "3.2.2.1.10.5", "client_http.byte_hit_ratio"); // Byte Hit Ratios (%)
		ABS_VALUES.put(MIB_BASE + "4.1.1.0", "ip_cache_entries"); // IP Cache Entries
		ABS_VALUES.put(MIB_BASE + "4.2.1.0", "fqdn_cache_entries"); // FQDN Cache entries
		ABS_VALUES.put(MIB_BASE + "4.3.3.0", "dns_servers"); // Number of external dnsserver processes

		STRING_VALUES.put(MIB_BASE + "2.3.0", "version");

		COUNTERS.put(MIB_BASE + "3.1.4.0", "cpu_time"); // Amount of cpu seconds consumed

		RATES.put(MIB_BASE + "3.1.1.0", "page_faults"); // Page faults with physical i/o
		RATES.put(MIB_BASE + "3.1.2.0", "http_io.reads"); // HTTP I/O number of reads
		RATES.put(MIB_BASE + "3.1.9.0", "unlink.requests"); // Requests given to unlinkd
		RATES.put(MIB_BASE + "3.2.1.1.0", "client_http.requests"); // Number of HTTP requests received
		RATES.put(MIB_BASE + "3.2.1.2.0", "client_http.hits"); // Number of HTTP Hits sent to clients from cache
		RATES.put(MIB_BASE + "3.2.1.3.0", "client_http.errors"); // Number of HTTP Errors sent to clients
		RATES.put(MIB_BASE + "3.2.1.4.0", "client_http.kbytes_in"); // Number of HTTP KB's received from clients
		RATES.put(MIB_BASE + "3.2.1.5.0", "client_http.kbytes_out"); // Number of HTTP KB's sent to clients
		RATES.put(MIB_BASE + "3.2.1.6.0", "icp.pkts_sent"); // Number of ICP messages sent
		RATES.put(MIB_BASE + "3.2.1.7.0", "icp.pkts_recv"); // Number of ICP messages received
		RATES.put(MIB_BASE + "3.2.1.8.0", "icp.kbytes_sent"); // Number of ICP KB's transmitted
		RATES.put(MIB_BASE + "3.2.1.9.0", "icp.kbytes_recv"); // Number of ICP KB's received
		RATES.put(MIB_BASE + "3.2.1.10.0", "server.all.requests"); // All requests from the client for the cache server
		RATES.put(MIB_BASE + "3.2.1.11.0", "server.all.errors"); // All errors for the cache server from client requests
		RATES.put(MIB_BASE + "3.2.1.12.0", "server.all.kbytes_in"); // KB's of traffic received from servers
		RATES.put(MIB_BASE + "3.2.1.13.0", "server.all.kbytes_out"); // KB's of traffic sent to servers
		RATES.put(MIB_BASE + "4.1.2.0", "ip_cache_requests"); // Number of IP Cache requests
		RATES.put(MIB_BASE + "4.1.3.0", "ip_cache_hits"); // Number of IP Cache hits
		RATES.put(MIB_BASE + "4.1.4.0", "ip_cache_pending_hits"); // Number of IP Cache pending hits
		RATES.put(MIB_BASE + "4.1.5.0", "ip_cache_negative_hits"); // Number of IP Cache pending hits
		RATES.put(MIB_BASE + "4.1.6.0", "ip_cache_misses"); // Number of IP Cache misses
		RATES.put(MIB_BASE + "4.1.7.0", "blocking_gethostbyname"); // Number of blocking gethostbyname requests
		RATES.put(MIB_BASE + "4.1.8.0", "ip_cache_release_attempts"); // Number of attempts to release locked IP Cache entries
		RATES.put(MIB_BASE + "4.2.2.0", "fqdn_cache_requests"); // Number of FQDN Cache requests
		RATES.put(MIB_BASE + "4.2.3.0", "fqdn_cache_hits"); // Number of FQDN Cache hits
		RATES.put(MIB_BASE + "4.2.4.0", "fqdn_cache_pending_hits"); // Number of FQDN Cache pending hits
		RATES.put(MIB_BASE + "4.2.5.0", "fqdn_cache_negative_hits"); // Number of FQDN Cache negative hits
		RATES.put(MIB_BASE + "4.2.6.0", "fqdn_cache_misses"); // Number of FQDN Cache misses
		RATES.put(MIB_BASE + "4.2.7.0", "blocking_gethostbyaddr"); // Number of blocking gethostbyaddr requests
		RATES.put(MIB_BASE + "4.3.1.0", "dns_requests"); // Number of external dnsserver requests
		RATES.put(MIB_BASE + "4.3.2.0", "dns_replies"); // Number of external dnsserver replies

		sOids = new String[ABS_VALUES.size() + STRING_VALUES.size() + COUNTERS.size() + RATES.size()];

		int cnt = 0;

		for (final String s : ABS_VALUES.keySet())
			sOids[cnt++] = s;

		for (final String s : STRING_VALUES.keySet())
			sOids[cnt++] = s;

		for (final String s : COUNTERS.keySet())
			sOids[cnt++] = s;

		for (final String s : RATES.keySet())
			sOids[cnt++] = s;
	}

	public snmp_Squid() {
		super(sOids);
	}

	@Override
	public MonModuleInfo init(final MNode node, final String args) {
		iRemotePort = 3401;
		iSNMPVersion = SNMPV2;

		if (args != null && args.length() > 0) {
			final StringTokenizer st = new StringTokenizer(args, ",;");

			while (st.hasMoreTokens()) {
				final String confToken = st.nextToken().trim();

				final int idxEq = confToken.indexOf('=');
				final int idxCo = confToken.indexOf(':');

				final int idx = idxEq > 0 ? (idxCo > 0 ? Math.min(idxEq, idxCo) : idxEq) : idxCo;

				if (idx > 0) {
					final String key = confToken.substring(0, idx).trim();
					final String value = confToken.substring(idx + 1).trim();

					if (key.equalsIgnoreCase("port"))
						iRemotePort = Integer.parseInt(value);
					else if (key.equalsIgnoreCase("snmpversion"))
						iSNMPVersion = Integer.parseInt(value) - 1;
					else if (key.equalsIgnoreCase("readTimeout"))
						iReadTimeOut = Integer.parseInt(value);
					else if (key.equalsIgnoreCase("community"))
						sCommunity = value;
				}
			}
		}

		try {
			init(node);
		} catch (final SocketException e) {
			// severe init error, cannot continue..
			logger.log(Level.SEVERE, "[SNMP] CommInterface could not be initialized", e);
			info.addErrorCount();
			info.setState(1); // error
			info.setErrorDesc("CommInterface could not be initialized");
		}

		info.name = ModuleName;
		return info;
	}

	@Override
	public String getOsName() {
		return "*";
	}

	private Map<String, Double> previousValues = null;
	private long lastIterationTime = 0;

	@Override
	public Object doProcess() throws Exception {
		if (info.getState() != 0)
			throw new IOException("[snmp_Squid ERR] Module could not be initialized");

		final Result r = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(), ModuleName);
		final eResult er = new eResult(Node.getFarmName(), Node.getClusterName(), Node.getName(), ModuleName, null);

		final Vector<Object> ret = new Vector<Object>(2);
		ret.add(r);
		ret.add(er);

		r.time = er.time = NTPDate.currentTimeMillis();

		Map<String, SNMPObject> res = null;

		final double deltaS;

		try {
			final long lStart = System.currentTimeMillis();

			res = snmpBulkGet();

			final long lEnd = System.currentTimeMillis();

			r.addSet("QueryTime_ms", lEnd - lStart);

			deltaS = (lEnd - lastIterationTime) / 1000d;

			lastIterationTime = lEnd;

			if (deltaS <= 0) {
				r.addSet("Status", 2);
				er.addSet("Message", "Time flows back");

				previousValues = null;

				return ret;
			}

			if (res == null || res.size() == 0) {
				r.addSet("Status", 2);
				er.addSet("Message", "Got no answer from the server");

				previousValues = null;

				return ret;
			}

			r.addSet("Status", 0);
			r.addSet("QueryTime_ms", lEnd - lStart);
		} catch (final Throwable t) {
			if (logger.isLoggable(Level.FINE))
				logger.log(Level.FINE, "Got exc for Node [ " + Node.getName() + " ] :", t);
			else if (logger.isLoggable(Level.WARNING))
				logger.log(Level.WARNING, "Got exc for Node [ " + Node.getName() + " ] :", t);

			r.addSet("Status", 1);
			er.addSet("Message", t.getMessage());

			lastIterationTime = 0;
			previousValues = null;

			return ret;
		}

		final Map<String, Double> currentValues = new HashMap<String, Double>();

		for (final Map.Entry<String, SNMPObject> entry : res.entrySet()) {
			final String mib = entry.getKey();
			final SNMPObject obj = entry.getValue();

			String paramName = STRING_VALUES.get(mib);

			if (paramName != null)
				er.addSet(paramName, obj.toString());
			else {
				double d;

				final Object value = obj.getValue();

				if (value instanceof BigInteger)
					d = ((BigInteger) value).doubleValue();
				else {
					logger.log(Level.WARNING, "Error for Node [ " + Node.getName() + " ] mib " + mib + " : unexpected value type " + value.getClass().getCanonicalName());

					continue;
				}

				paramName = ABS_VALUES.get(mib);

				if (paramName != null)
					r.addSet(paramName, d);
				else {
					currentValues.put(mib, Double.valueOf(d));

					final Double previousValue = previousValues != null ? previousValues.get(mib) : null;

					if (previousValue == null || previousValue.doubleValue() > d)
						continue;

					final double delta = d - previousValue.doubleValue();

					paramName = COUNTERS.get(mib);

					if (paramName != null)
						r.addSet(paramName, delta);
					else {
						paramName = RATES.get(mib);

						if (paramName != null)
							r.addSet(paramName, delta / deltaS);
						else
							logger.log(Level.WARNING, "What is this mib? " + mib);
					}
				}
			}
		}

		previousValues = currentValues;

		return ret;
	}

	static public void main(final String[] args) {

		final String host = "atlassq.ihep.ac.cn";// args[0] ;
		final snmp_Squid aa = new snmp_Squid();
		System.setProperty("lia.Monitor.SNMP_version", "2c");
		String ad = null;
		try {
			ad = InetAddress.getByName(host).getHostAddress();
		} catch (final Exception e) {
			System.out.println(" Can not get ip for node " + e);
			System.exit(-1);
		}

		aa.init(new MNode(host, ad, null, null), null);

		while (true) {
			Object cc = null;
			try {
				cc = aa.doProcess();
			} catch (final Exception e) {
				e.printStackTrace();
			}

			try {
				Thread.sleep(60000);
			} catch (final Exception e) {
				// ignore
			}

			System.out.println("[SIM]  Result" + cc);
		}
	}

	@Override
	public String[] ResTypes() {
		return null;
	}

}
