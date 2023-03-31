package lia.Monitor.modules;

import lazyj.commands.CommandOutput;
import lazyj.commands.SystemCommand;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.eResult;
import lia.util.Utils;
import lia.util.DynamicThreadPoll.SchJob;
import lia.util.ntp.NTPDate;

/**
 * @author costing
 * @since Mar 31, 2023
 */
public class monEOSVersion extends SchJob implements MonitoringModule {

	private static final long serialVersionUID = 1L;
	private MonModuleInfo mmi = null;
	private MNode mn = null;

	@Override
	public MonModuleInfo init(final MNode node, final String args) {
		mn = node;

		mmi = new MonModuleInfo();
		mmi.setName("SiteStatsModule");
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

	@Override
	public Object doProcess() throws Exception {
		final long ls = NTPDate.currentTimeMillis();

		final eResult er = new eResult();
		er.FarmName = getFarmName();
		er.ClusterName = getClusterName();
		er.NodeName = mn.getName();
		er.Module = mmi.getName();
		er.time = ls;

		addCommandOutput(er, "rpm -qa xrootd | cut -d '-' -f2", "xrootd_rpm_version");
		addCommandOutput(er, "rpm -qa eos-server | cut -d '-' -f3-", "eos_rpm_version");
		addCommandOutput(er, "rpm -qa eos-xrootd | cut -d '-' -f3-", "eos_xrootd_rpm_version");

		if (er.param != null && er.param.length > 0)
			return er;

		return null;
	}

	/**
	 * @param er
	 * @param command
	 * @param parameter
	 */
	private static void addCommandOutput(final eResult er, final String command, final String parameter) {
		final CommandOutput co = SystemCommand.bash(command, false);

		if (co.exitCode == 0) {
			final String output = co.stdout.trim();

			if (output.length() > 0 && output.length() < 100)
				er.addSet(parameter, co.stdout);
		}
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

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(final String[] args) throws Exception {
		final MFarm f = new MFarm("myFarm");
		final MCluster c = new MCluster("myCluster", f);
		final MNode n = new MNode("eosversion", c, f);

		final monEOSVersion m = new monEOSVersion();
		m.init(n, args.length > 0 ? args[0] : null);

		Utils.dumpResults(m.doProcess());
	}
}
