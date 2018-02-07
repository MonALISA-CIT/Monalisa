package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;

import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.Monitor.monitor.eResult;
import lia.util.Utils;
import lia.util.ntp.NTPDate;

/**
 * MySQL monitoring module, relying on "show global status;" output. It expects the mysql client in PATH and the configuration to be generated with mysql_config_editor, so that simply running<br>
 * <code>mysql -B -e "show global status;"</code><br>
 * returns the expected dump of status variables.
 * 
 * @author costing
 * @since 2015-09-17
 */
public class monMySQL extends cmdExec implements MonitoringModule {

	/**
	 * 
	 */
	private static final long serialVersionUID = 9216690559563396895L;
	private MonModuleInfo mmi = null;
	private MNode mn = null;

	@Override
	public MonModuleInfo init(final MNode node, final String args) {
		mn = node;

		mmi = new MonModuleInfo();
		mmi.setName("monMySQL");
		mmi.setState(0);

		mmi.lastMeasurement = NTPDate.currentTimeMillis();

		return mmi;
	}

	@Override
	public String[] ResTypes() {
		return mmi.getResType();
	}

	@Override
	public String getOsName() {
		return "Linux";
	}

	private final Map<String, Double> prevValues = new HashMap<String, Double>();

	@Override
	public Object doProcess() throws Exception {
		if (mmi.getState() != 0)
			throw new IOException("there was some exception during init ...");

		BufferedReader br = null;

		final Map<String, Double> doubleValues = new HashMap<String, Double>();
		final Map<String, String> stringValues = new HashMap<String, String>();

		try {
			br = procOutput("mysql -B -e 'show global status'", 5000);

			String line;
			while ((line = br.readLine()) != null) {
				final StringTokenizer st = new StringTokenizer(line);

				if (st.countTokens() == 2) {
					final String key = st.nextToken();

					final String value = st.nextToken();

					try {
						doubleValues.put(key, Double.valueOf(value));
					} catch (final NumberFormatException nfe) {
						stringValues.put(key, value);
					}
				}
			}

		} catch (final Throwable t) {
			System.err.println(t);
		} finally {
			if (br != null)
				br.close();
		}
		final Vector<Object> vr = new Vector<Object>();

		final Result r = new Result();
		final eResult er = new eResult();
		er.FarmName = r.FarmName = getFarmName();
		er.ClusterName = r.ClusterName = getClusterName();
		er.NodeName = r.NodeName = mn.getName();
		er.Module = r.Module = mmi.getName();
		er.time = r.time = NTPDate.currentTimeMillis();

		final Double currentUptime = doubleValues.get("Uptime");
		final Double prevUptime = prevValues.get("Uptime");

		final double divTime = (currentUptime != null && prevUptime != null) ? (currentUptime.doubleValue() - prevUptime.doubleValue()) : -1;

		double cacheHits = 0;
		double selectCommands = 0;

		for (final Map.Entry<String, Double> entry : doubleValues.entrySet()) {
			final String key = entry.getKey();
			final double value = entry.getValue().doubleValue();

			r.addSet(key, value);

			if (divTime > 0) {
				final Double prevValue = prevValues.get(key);

				if (prevValue != null) {
					final double rate = (value - prevValue.doubleValue()) / divTime;

					r.addSet(key + "_R", rate);

					if (key.equals("Qcache_hits"))
						cacheHits = rate;
					else if (key.equals("Com_select"))
						selectCommands = rate;
				}
			}
		}

		if (cacheHits + selectCommands > 0)
			r.addSet("QCache_hit_ratio", cacheHits / (cacheHits + selectCommands));

		for (final Map.Entry<String, String> entry : stringValues.entrySet())
			er.addSet(entry.getKey(), entry.getValue());

		if (r.param_name != null && r.param_name.length > 0)
			vr.addElement(r);

		if (er.param_name != null && er.param_name.length > 0)
			vr.addElement(er);

		prevValues.clear();
		prevValues.putAll(doubleValues);

		mmi.setLastMeasurement(r.time);

		return vr;
	}

	@Override
	public MNode getNode() {
		return mn;
	}

	@Override
	public String getClusterName() {
		return mn.getClusterName();
	}

	@Override
	public String getFarmName() {
		return mn.getFarmName();
	}

	@Override
	public boolean isRepetitive() {
		return true;
	}

	@Override
	public String getTaskName() {
		return mmi.getName();
	}

	@Override
	public MonModuleInfo getInfo() {
		return mmi;
	}

	public static void main(final String[] args) throws Exception {
		final MFarm f = new MFarm("myFarm");
		final MCluster c = new MCluster("myCluster", f);
		final MNode n = new MNode("mySQL", c, f);

		final monMySQL m = new monMySQL();
		m.init(n, args.length > 0 ? args[0] : null);

		m.doProcess();

		Thread.sleep(1000 * 30);

		Utils.dumpResults(m.doProcess());
	}
}
