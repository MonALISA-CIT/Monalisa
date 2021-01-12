package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

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
 * Parsing the output of `xrd` commands to find out the version and the disk space
 * 
 * @author costing
 * @since 2007-02-22
 */
public class monXrdSpace extends cmdExec implements MonitoringModule {
	/** Logger used by this class */
	private static final Logger logger = Logger.getLogger(monXrdSpace.class.getCanonicalName());

	private static final long serialVersionUID = 1;

	private MonModuleInfo mmi = null;

	private MNode mn = null;

	private final long lLastCall = 0;

	private final String[] resTypes = new String[0];

	private String sCommand = "xrdfs";

	private int xrootdRedirectorPort = 1095;

	/**
	 * default constructor for the module
	 */
	public monXrdSpace() {
		super("monXrdSpace");
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
		mmi.setName("monXrdSpace");
		mmi.setState(0);

		mmi.lastMeasurement = lLastCall;

		if ((args != null) && (args.length() > 0)) {
			final StringTokenizer st = new StringTokenizer(args);

			while (st.hasMoreTokens()) {
				final String tok = st.nextToken();

				try {
					final int port = Integer.parseInt(tok);

					xrootdRedirectorPort = port;
				}
				catch (final NumberFormatException nfe) {
					sCommand = tok;
				}
			}
		}

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

	/**
	 * Called periodically to get data from the sensors.
	 * 
	 * @return a Vector with the results of the processing
	 * @throws Exception
	 *             if there was an error processing the output of the sensors command
	 */
	@Override
	public Object doProcess() throws Exception {
		final long ls = NTPDate.currentTimeMillis();

		Result r = new Result();
		final eResult er = new eResult();

		er.FarmName = r.FarmName = getFarmName();
		er.ClusterName = r.ClusterName = getClusterName();
		er.NodeName = r.NodeName = mn.getName();
		er.Module = r.Module = mmi.getName();
		er.time = r.time = ls;

		final String versionCommand = sCommand + " " + mn.getName() + (mn.getName().contains(":") ? "" : ":" + xrootdRedirectorPort) + " query config version";

		String version = null;

		try (BufferedReader br = procOutput(versionCommand, 75000)) {
			if (br == null) {
				logger.log(Level.WARNING, "Cannot run '" + sCommand + "'.\nFor details increase debug level of lia.Monitor.monitor.cmdExec to FINER");
			}
			else {
				String line = br.readLine();

				if (line != null) {
					line = line.trim();
					if (!line.equals("version") && !line.startsWith("["))
						if (line.startsWith("v"))
							version = "Xrootd " + line;
						else
							if (line.startsWith("dCache "))
								version = "dCache " + line.substring(line.indexOf(' ') + 1).trim();
							else
								version = line;
				}
			}
		}
		finally {
			cleanup();
		}

		if (version != null) {
			er.addSet("xrootd_version", version);

			final String command = sCommand + " " + mn.getName() + (mn.getName().contains(":") ? "" : ":" + xrootdRedirectorPort) + " spaceinfo /";

			final StringBuilder sb = new StringBuilder(command).append('\n');

			try (BufferedReader br2 = procOutput(command, 1000 * 1000)) {

				if (br2 == null) {
					logger.log(Level.WARNING, "Cannot run '" + command + "'.\nFor details increase debug level of lia.Monitor.monitor.cmdExec to FINER");
				}
				else {
					String line = null;

					double total = -1;
					double free = -1;

					while ((line = br2.readLine()) != null) {
						sb.append(line).append('\n');

						if (line.length() == 0) {
							continue;
						}

						final int idx = line.indexOf(':');

						if (idx < 0) {
							continue;
						}

						final String key = line.substring(0, idx).trim();

						if (key.equals("Total") || key.equals("Free") || key.equals("Largest chunk")) {
							final double value = Double.parseDouble(line.substring(idx + 1).trim());

							if (key.equals("Total")) {
								r.addSet("space_total", value);
								total = value;
							}
							else
								if (key.equals("Free")) {
									r.addSet("space_free", value);
									free = value;
								}
								else {
									r.addSet("space_largestfreechunk", value);
								}
						}
					}

					if ((total <= 0) || (free <= 0) || (free > total)) {
						sb.append("\n\nPATH: ").append(System.getenv("PATH")).append("\nLD_LIBRARY_PATH: ").append(System.getenv("LD_LIBRARY_PATH"));

						logger.log(Level.WARNING, "xrd output was not ok:\nFull command: " + sb.toString());

						r = null;
					}

					br2.close();
				}
			}
			finally {
				cleanup();
			}
		}

		final Vector<Object> vReturn = new Vector<>();

		if (((r == null) || (r.param == null) || (r.param.length == 0)) && ((er.param == null) || (er.param.length == 0))) {
			throw new IOException("Command failed to produce any relevant output: " + versionCommand);
		}

		if ((r != null) && (r.param != null) && (r.param.length > 0)) {
			vReturn.add(r);
		}

		if ((er.param != null) && (er.param.length > 0)) {
			vReturn.add(er);
		}

		return vReturn;
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
		final MCluster c = new MCluster("ALICE::ISS::FILE", f);
		final MNode n = new MNode("storage02.spacescience.ro:1094", c, f);

		final monXrdSpace m = new monXrdSpace();
		m.init(n, (args != null) && (args.length > 0) ? args[0] : null);

		Utils.dumpResults(m.doProcess());
	}
}
