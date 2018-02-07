package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.TimestampedResult;
import lia.Monitor.monitor.cmdExec;
import lia.Monitor.monitor.eResult;
import lia.util.Utils;
import lia.util.ntp.NTPDate;

/**
 *
 * @author costing
 * @since 2016-06-28
 */
public class monIPMI extends cmdExec implements MonitoringModule {
	private static final long serialVersionUID = 1;

	/** Logger used by this class */
	private static final Logger logger = Logger.getLogger(monIPMI.class.getName());

	private MonModuleInfo mmi = null;

	private MNode mn = null;

	private final long lLastCall = 0;

	private final String[] resTypes = new String[0];

	private final static String[] sPossiblePaths = new String[] { "/bin/ipmitool", "/usr/bin/ipmitool", "/usr/local/bin/ipmitool", "/opt/bin/ipmitool" };

	private String sCommand = "ipmitool";

	/**
	 * default constructor for the module
	 */
	public monIPMI() {
		super("monIPMI");
		info.ResTypes = resTypes;
		isRepetitive = true;
	}

	/**
	 * Initialize data structures
	 *
	 * @param node
	 *            ML node
	 * @param args
	 *            arguments
	 * @return module informations
	 */
	@Override
	public MonModuleInfo init(final MNode node, final String args) {
		mn = node;

		mmi = new MonModuleInfo();
		mmi.setName("monIPMI");
		mmi.setState(0);

		mmi.lastMeasurement = lLastCall;

		// try to see where the "sensors" executable is
		int iFoundCount = 0;

		for (final String sPossiblePath : sPossiblePaths) {
			final File f = new File(sPossiblePath);

			if (f.exists() && f.isFile()) {
				sCommand = sPossiblePath;
				iFoundCount++;
			}
		}

		// not found at all or found several times => let PATH decide where to look
		if (iFoundCount != 1)
			sCommand = "ipmitool";

		// a module argument will override any auto discovered, if the file exists
		if ((args != null) && (args.length() > 0)) {
			final File f = new File(args);

			if (f.exists() && f.isFile())
				sCommand = args;
		}

		// a configuration parameter will override any previously set path, if the file exists
		final String sConfigPath = AppConfig.getProperty("lia.Monitor.modules.monIPMI.path");

		if ((sConfigPath != null) && (sConfigPath.length() > 0)) {
			final File f = new File(sConfigPath);

			if (f.exists() && f.isFile())
				sCommand = sConfigPath;
			else
				logger.log(Level.WARNING, "Ignoring configured path of ipmitool (" + sConfigPath + ") since it doesn't exist");
		}

		sCommand += " sensor";

		return info;
	}

	/**
	 * This is a dynamic module so this will return an empty array
	 *
	 * @return empty array
	 */
	@Override
	public String[] ResTypes() {
		return resTypes;
	}

	/**
	 * Operating system on which this module can run.
	 *
	 * @return Obviously "Linux"
	 */
	@Override
	public String getOsName() {
		return "Linux";
	}

	private TimestampedResult createResult(final boolean isNumeric, final String clusterExtension) {
		if (!isNumeric) {
			final eResult er = new eResult();

			er.FarmName = getFarmName();
			er.ClusterName = getClusterName();

			if (clusterExtension != null)
				er.ClusterName += clusterExtension;

			er.NodeName = mn.getName();
			er.Module = mmi.getName();
			er.time = NTPDate.currentTimeMillis();

			return er;
		}

		final Result r = new Result();
		r.FarmName = getFarmName();
		r.ClusterName = getClusterName();

		if (clusterExtension != null)
			r.ClusterName += clusterExtension;

		r.NodeName = mn.getName();
		r.Module = mmi.getName();
		r.time = NTPDate.currentTimeMillis();

		return r;
	}

	/**
	 * Called periodically to get data from the sensors.
	 *
	 * @return a Vector with the results of the processing
	 * @throws Exception
	 *             if there was an error processing the output of the sensors command
	 */
	@Override
	public Object doProcess() throws Exception {
		final Map<String, TimestampedResult> ret = new HashMap<String, TimestampedResult>();

		final boolean separateClusters = AppConfig.getb("lia.Monitor.modules.monIPMI.separateClusters", true);

		if (logger.isLoggable(Level.FINE))
			logger.log(Level.FINE, "Running cmd='" + sCommand + "'");

		final BufferedReader br = procOutput(sCommand, 5000);

		if (br == null)
			logger.log(Level.WARNING, "Cannot run '" + sCommand + "'.\nFor details increase debug level of lia.Monitor.monitor.cmdExec to FINER");
		else
			try {
				String line = null;

				while ((line = br.readLine()) != null) {
					final StringTokenizer st = new StringTokenizer(line, "|");

					if (st.countTokens() > 3) {
						final String key = st.nextToken().trim().replace(' ', '_');
						final String value = st.nextToken().trim();

						if (value.equals("na"))
							continue;

						final String unit = st.nextToken().trim().replace(' ', '_');

						double dValue = -1;
						boolean isNumeric = false;

						try {
							dValue = Double.parseDouble(value);
							isNumeric = true;
						} catch (@SuppressWarnings("unused") final NumberFormatException nfe) {
							// maybe it's a hex value

							try {
								dValue = Long.decode(value).doubleValue();
								isNumeric = true;
							} catch (@SuppressWarnings("unused") final NumberFormatException nfe2) {
								// ignore, will add as Object
							}
						}

						if (separateClusters) {
							TimestampedResult result = ret.get(unit + "-" + isNumeric);

							if (result == null) {
								result = createResult(isNumeric, "_" + unit);
								ret.put(unit + "-" + isNumeric, result);
							}

							if (isNumeric)
								((Result) result).addSet(key, dValue);
							else
								((eResult) result).addSet(key, value);
						}
						else {
							TimestampedResult result = ret.get(String.valueOf(isNumeric));

							if (result == null) {
								result = createResult(isNumeric, null);
								ret.put(String.valueOf(isNumeric), result);
							}

							if (isNumeric)
								((Result) result).addSet(key + "_" + unit, dValue);
							else
								((eResult) result).addSet(key + "_" + unit, value);
						}
					}
				}

				br.close();
			} finally {
				cleanup();
			}

		if (ret.size() == 0)
			throw new IOException(
					"No ipmi detected. If the machine is correctly configured, try setting configuration parameter 'lia.Monitor.modules.monIPMI.path' to the full path to the 'ipmitool' executable.");

		return new Vector<TimestampedResult>(ret.values());
	}

	/**
	 * Node name
	 *
	 * @return node name
	 */
	@Override
	public MNode getNode() {
		return mn;
	}

	/**
	 * Cluster name
	 *
	 * @return cluster name
	 */
	@Override
	public String getClusterName() {
		return mn.getClusterName();
	}

	/**
	 * Farm name
	 *
	 * @return farm name
	 */
	@Override
	public String getFarmName() {
		return mn.getFarmName();
	}

	/**
	 * Of course this module is repetitive :)
	 *
	 * @return true
	 */
	@Override
	public boolean isRepetitive() {
		return true;
	}

	/**
	 * Task name
	 *
	 * @return task name
	 */
	@Override
	public String getTaskName() {
		return mmi.getName();
	}

	/**
	 * Module info
	 *
	 * @return info
	 */
	@Override
	public MonModuleInfo getInfo() {
		return mmi;
	}

	/**
	 * Debug method
	 *
	 * @param args
	 *            command line arguments
	 * @throws Exception
	 */
	public static void main(final String args[]) throws Exception {
		final MFarm f = new MFarm("myFarm");
		final MCluster c = new MCluster("myCluster", f);
		final MNode n = new MNode("ipmi", c, f);

		final monIPMI m = new monIPMI();
		m.init(n, args.length > 0 ? args[0] : null);

		Utils.dumpResults(m.doProcess());
	}

}
