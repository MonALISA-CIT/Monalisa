package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.Utils;
import lia.util.ntp.NTPDate;
import lia.util.process.ExternalProcesses;
import lia.web.utils.DoubleFormat;

/**
 * @author costing
 * @since 2010-10-26
 */
public class DiskDF extends AbstractSchJobMonitoring {

	private static final long serialVersionUID = 1L;

	/**
	 * Excluded file system types
	 */
	final Set<String> excludedFSTypes = new HashSet<String>();

	/**
	 * Extra partitions to ignore
	 */
	final Set<String> excludedPartitions = new HashSet<String>();

	/**
	 *
	 */
	public DiskDF() {
		excludedFSTypes.add("none");
		excludedFSTypes.add("afs");
		excludedFSTypes.add("nfs");
		excludedFSTypes.add("sysfs");
		excludedFSTypes.add("proc");
		excludedFSTypes.add("fusectl");
		excludedFSTypes.add("debugfs");
		excludedFSTypes.add("tmpfs");
		excludedFSTypes.add("securityfs");
		excludedFSTypes.add("autofs");
		excludedFSTypes.add("usbfs");
		excludedFSTypes.add("devpts");
		excludedFSTypes.add("binfmt_misc");
		excludedFSTypes.add("rpc_pipefs");

		// linux-specific
		excludedPartitions.add("none");
		excludedPartitions.add("rootfs");
		excludedPartitions.add("tmpfs");
		excludedPartitions.add("sunrpc");
		excludedPartitions.add("devpts");
		excludedPartitions.add("AFS");
		excludedPartitions.add("fusectl");

		// solaris-specific
		excludedPartitions.add("proc");
		excludedPartitions.add("/devices");
		excludedPartitions.add("/dev");
		excludedPartitions.add("ctfs");
		excludedPartitions.add("mnttab");
		excludedPartitions.add("objfs");
		excludedPartitions.add("sharefs");
		excludedPartitions.add("fd");

		// mac-specific
		excludedPartitions.add("devfs");
		excludedPartitions.add("fdesc");

		String s = AppConfig.getProperty("lia.Monitor.modules.DiskDF.exclude_fstypes");

		if ((s != null) && (s.trim().length() >= 0)) {
			final StringTokenizer st = new StringTokenizer(s, " \t,;");

			while (st.hasMoreTokens())
				excludedFSTypes.add(st.nextToken());
		}

		s = AppConfig.getProperty("lia.Monitor.modules.DiskDF.exclude_partitions");

		if ((s != null) && (s.trim().length() >= 0)) {
			final StringTokenizer st = new StringTokenizer(s, " \t,;");

			while (st.hasMoreTokens())
				excludedPartitions.add(st.nextToken());
		}
	}

	@Override
	public boolean isRepetitive() {
		return true;
	}

	@Override
	public String getTaskName() {
		return "DiskDF";
	}

	/**
	 * @author costing
	 */
	private class MountPoint {
		/**
		 * Partition name
		 */
		final String partition;

		/**
		 * Mount point
		 */
		final String mountPoint;

		/**
		 * FS type
		 */
		final String fsType;

		/**
		 * Mount options
		 */
		final String options;

		/**
		 * Size, in MB
		 */
		double size = -1;

		/**
		 * Used, in MB
		 */
		double used = -1;

		/**
		 * Available, in MB
		 */
		double available = -1;

		/**
		 * Counter
		 */
		int cnt = 0;

		/**
		 * Initialize with one line from /proc/mounts
		 *
		 * @param mountLine
		 */
		public MountPoint(final String mountLine) {
			final StringTokenizer st = new StringTokenizer(mountLine);

			if (isLinuxOS()) {
				partition = st.nextToken();
				mountPoint = st.nextToken();
				fsType = st.nextToken();

				if (st.hasMoreTokens())
					options = st.nextToken();
				else
					options = null;
			}
			else
				if (isSolarisOS() || isMacOS()) {
					String sMountPoint = null;

					while (st.hasMoreTokens()) {
						final String sTok = st.nextToken();

						if (sTok.equals("on"))
							break;

						if (sMountPoint == null)
							sMountPoint = sTok;
						else
							sMountPoint += " " + sMountPoint;
					}

					final String sPartition = st.nextToken();

					if (isSolarisOS()) {
						// <mountpoint> on <device> <option> on <date>
						mountPoint = sMountPoint;
						partition = sPartition;
					}
					else {
						// <device> on <mountpoint> (<options>)
						mountPoint = sPartition;
						partition = sMountPoint;
					}

					String sOptions = null;

					while (st.hasMoreTokens()) {
						final String sTok = st.nextToken();

						if (sTok.equals("on"))
							break;

						if (sOptions == null)
							sOptions = sTok;
						else
							sOptions += " " + sTok;
					}

					options = sOptions;

					if (isSolarisOS())
						fsType = "Solaris";
					else {
						String sFsType = "MacOS";

						if (options != null) {
							final StringTokenizer stok = new StringTokenizer(options, "(),; ");

							sFsType = stok.nextToken();
						}

						fsType = sFsType;
					}
				}
				else
					throw new IllegalAccessError();

			if (partition.equals("TOTAL"))
				size = used = available = 0;
		}

		public MountPoint(final String partition, final String mountPoint, final String fsType, final String options) {
			this.partition = partition;
			this.mountPoint = mountPoint;
			this.fsType = fsType;
			this.options = options;

			if (partition.equals("TOTAL"))
				size = used = available = 0;
		}

		/**
		 * @return true if the partition is mounted read-only
		 */
		public boolean isRO() {
			if (options == null)
				return true;

			if (isSolarisOS())
				return options.indexOf("read only") >= 0;

			final StringTokenizer st = new StringTokenizer(options, "(), /");

			while (st.hasMoreTokens()) {
				final String s = st.nextToken();

				if (s.equals("ro"))
					return true;

				if (s.equals("rw"))
					return false;
			}

			return true;
		}

		/**
		 * @return true if successful, then size,used and available are filled
		 *         correctly
		 * @throws Throwable
		 */
		public boolean getDF() throws Throwable {
			final String output = ExternalProcesses.getCmdOutput(Arrays.asList("df", "-P", "-B", "1024", mountPoint), true, 30L, TimeUnit.SECONDS);

			final BufferedReader br = new BufferedReader(new StringReader(output));

			String sLine = br.readLine();
			if (sLine == null)
				return false;

			sLine = br.readLine();

			if (sLine == null)
				return false;

			final StringTokenizer st = new StringTokenizer(sLine);

			st.nextToken();

			size = Double.parseDouble(st.nextToken()) / 1024;
			used = Double.parseDouble(st.nextToken()) / 1024;
			available = Double.parseDouble(st.nextToken()) / 1024;

			br.close();

			return (size > 0) && (used >= 0) && (available >= 0);
		}

		public boolean shouldConsider() {
			if (excludedFSTypes.contains(fsType))
				return false;

			if ((partition.indexOf(':') >= 0) || partition.startsWith("automount"))
				return false;

			if (excludedPartitions.contains(partition))
				return false;

			if (isMacOS()) {
				if ((options != null) && ((options.indexOf("autofs") >= 0) || (options.indexOf("automounted") >= 0)))
					return false;

				if (partition.startsWith("map "))
					return false;
			}

			return true;
		}

		public void add(final MountPoint m) {
			size += m.size;
			used += m.used;
			available += m.available;
		}

		public void addToResult(final Result r) {
			String s = "p" + cnt;

			if (partition.equals("TOTAL"))
				s = "TOTAL";

			r.addSet(s + "_sizeMB", size);
			r.addSet(s + "_usedMB", used);
			r.addSet(s + "_availMB", available);

			r.addSet(s + "_usage", (used * 100) / size);
			r.addSet(s + "_available", (available * 100) / size);
		}

		public void addToeResult(final eResult er) {
			if (partition.equals("TOTAL"))
				return;

			final String s = "p" + cnt;

			er.addSet(s + "_partition", partition);
			er.addSet(s + "_mountpoint", mountPoint);
			er.addSet(s + "_fstype", fsType);

			if (options != null)
				er.addSet(s + "_options", options);
		}

		public double getUsage() {
			return (used * 100) / size;
		}
	}

	@Override
	public Object doProcess() throws Exception {
		if (!isLinuxOS() && !isSolarisOS())
			return null;

		final Map<String, MountPoint> m = new TreeMap<String, MountPoint>();

		final BufferedReader br;

		if (isLinuxOS())
			br = new BufferedReader(new FileReader("/proc/mounts"));
		else
			br = new BufferedReader(new StringReader(ExternalProcesses.getCmdOutput("mount", false, 30, TimeUnit.SECONDS)));

		String sLine = br.readLine();

		while ((sLine = br.readLine()) != null) {
			final MountPoint mp = new MountPoint(sLine);

			if (!mp.shouldConsider())
				// System.err.println("Not local");
				continue;

			try {
				if (!mp.getDF())
					// System.err.println("DF failed");
					continue;
			} catch (final Throwable t) {
				// System.err.println("DF exception : "+e);
				continue;
			}

			m.put(mp.mountPoint, mp);
		}

		br.close();

		final Result r = new Result(node.getFarmName(), node.getClusterName(), node.getName(), getTaskName());
		final eResult er = new eResult(node.getFarmName(), node.getClusterName(), node.getName(), getTaskName(), null);

		r.time = er.time = NTPDate.currentTimeMillis();

		final MountPoint total = new MountPoint("TOTAL", "TOTAL", "TOTAL", null);

		int cnt = 0;

		MountPoint mostUsed = null;

		for (final MountPoint mp : m.values()) {
			total.add(mp);

			mp.cnt = cnt++;

			mp.addToResult(r);
			mp.addToeResult(er);

			if (!mp.isRO() && ((mostUsed == null) || (mostUsed.getUsage() < mp.getUsage())))
				mostUsed = mp;
		}

		r.addSet("TOTAL_partitions", cnt);

		total.addToResult(r);
		total.addToeResult(er);

		final double usageThresholdWarning = AppConfig.getd("lia.Monitor.modules.DiskDF.usage_threshold_warning", 90);
		double usageThresholdError = AppConfig.getd("lia.Monitor.modules.DiskDF.usage_threshold_error", 95);

		if (usageThresholdWarning > usageThresholdError)
			usageThresholdError = usageThresholdWarning;

		if (mostUsed != null) {
			final double usage = mostUsed.getUsage();

			if (usage >= usageThresholdError) {
				er.addSet("Message", "Error: partition " + mostUsed.mountPoint + " (" + mostUsed.partition + ") is " + DoubleFormat.point(usage) + "% used (" + formatSize(mostUsed.available)
						+ " free / " + formatSize(mostUsed.size) + " total)");

				r.addSet("Status", 1);
			}
			else
				if (usage >= usageThresholdWarning) {
					er.addSet("Message", "Warning: partition " + mostUsed.mountPoint + " (" + mostUsed.partition + ") is " + DoubleFormat.point(usage) + "% used (" + formatSize(mostUsed.available)
							+ " free / " + formatSize(mostUsed.size) + " total)");

					r.addSet("Status", 2);
				}
				else
					r.addSet("Status", 0);
		}

		final Vector<Object> ret = new Vector<Object>();

		ret.add(r);
		ret.add(er);

		return ret;
	}

	private static String formatSize(final double sizeInM) {
		final String ret = DoubleFormat.size(sizeInM, "M");

		return ret.endsWith("B") ? ret : ret + "B";
	}

	@Override
	protected MonModuleInfo initArgs(final String args) {
		return null;
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(final String[] args) throws Exception {
		final DiskDF df = new DiskDF();
		df.init(new MNode("localhost", null, null), "");

		Utils.dumpResults(df.doProcess());
	}

}
