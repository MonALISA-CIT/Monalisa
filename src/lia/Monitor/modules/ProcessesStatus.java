package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.Result;
import lia.util.Utils;
import lia.util.proc.OSProccessStatWrapper;
import lia.util.proc.ProcFSUtil;
import lia.util.process.ExternalProcesses;

/**
 * @author costing
 * @since 2010-10-29
 */
public class ProcessesStatus extends AbstractSchJobMonitoring {

	private final MonModuleInfo info = new MonModuleInfo();

	/**
	 * Message logger
	 */
	// private static final Logger logger = Logger.getLogger(ProcessesStatus.class.getName());

	/**
	 * stop complaining :)
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public boolean isRepetitive() {
		return true;
	}

	@Override
	public String getTaskName() {
		return "ProcessesStatus";
	}

	@SuppressWarnings("null")
	@Override
	public Object doProcess() throws Exception {
		final Result r = getResult();

		final Map<String, AtomicInteger> counters = new HashMap<>();

		counters.put("R", new AtomicInteger(0));
		counters.put("D", new AtomicInteger(0));
		counters.put("S", new AtomicInteger(0));
		counters.put("Z", new AtomicInteger(0));

		if (isLinuxOS()) {
			final OSProccessStatWrapper[] processes = ProcFSUtil.getCurrentProcs();

			char oldState = 0;

			AtomicInteger aiOld = null;

			if (processes != null) {
				for (final OSProccessStatWrapper p : processes) {
					final char state = p.state;

					if (state == oldState) {
						aiOld.incrementAndGet();
						continue;
					}

					final String sState = String.valueOf(state);

					AtomicInteger ai = counters.get(sState);

					if (ai != null) {
						ai.incrementAndGet();
					}
					else {
						ai = new AtomicInteger(1);
						counters.put(sState, ai);
					}

					oldState = state;
					aiOld = ai;
				}
			}
		}
		else {
			// mac or solaris, need the output from ps

			final List<String> command = new ArrayList<>(4);

			command.add("ps");
			command.add("-A");
			command.add("-o");

			if (isSolarisOS()) {
				command.add("s");
			}
			else {
				command.add("state");
			}

			try {
				final String output = ExternalProcesses.getCmdOutput(command, true, 30L, TimeUnit.SECONDS);

				try (BufferedReader br = new BufferedReader(new StringReader(output))) {
					String sLine;

					String sOldState = null;

					AtomicInteger aiOld = null;

					while ((sLine = br.readLine()) != null) {
						if (sLine.equals(sOldState)) {
							aiOld.incrementAndGet();
							continue;
						}

						AtomicInteger ai = counters.get(sLine);

						if (ai != null) {
							ai.incrementAndGet();
						}
						else {
							ai = new AtomicInteger(1);
							counters.put(sLine, ai);
						}

						sOldState = sLine;
						aiOld = ai;
					}
				}
			}
			catch (final Throwable t) {
				// ignore
			}
		}

		int total = 0;

		for (final Map.Entry<String, AtomicInteger> me : counters.entrySet()) {
			r.addSet("processes_" + me.getKey(), me.getValue().doubleValue());

			total += me.getValue().get();
		}

		r.addSet("processes", total);

		final Vector<Object> v = new Vector<>(1);

		v.add(r);

		return v;
	}

	@Override
	protected MonModuleInfo initArgs(final String args) {
		info.name = getTaskName();
		return info;
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(final String[] args) throws Exception {
		final ProcessesStatus df = new ProcessesStatus();
		df.init(new MNode("localhost", null, null), "");

		Utils.dumpResults(df.doProcess());
	}

}
