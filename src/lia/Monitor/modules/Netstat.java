package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.Utils;
import lia.util.process.ExternalProcesses;

/**
 * @author costing
 * @since Oct 29, 2010
 */
public class Netstat extends AbstractSchJobMonitoring {
	private final MonModuleInfo info = new MonModuleInfo();

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
		return "Netstat";
	}

	private static final void increment(final Map<String, AtomicInteger> map, final String key) {
		AtomicInteger ai = map.get(key);

		if (ai == null) {
			ai = new AtomicInteger(1);
			map.put(key, ai);
		}
		else {
			ai.incrementAndGet();
		}
	}

	private Set<String> previousKeys = null;

	@Override
	public Object doProcess() throws Exception {
		final Result r = getResult();
		final eResult er = geteResult();

		final String output = ExternalProcesses.getCmdOutput(Arrays.asList("netstat", "-a", "-n"), false, 60,
				TimeUnit.SECONDS);

		String sLine;

		boolean bUDP4 = false;
		boolean bUDP6 = false;
		boolean bTCP4 = false;
		boolean bTCP6 = false;
		boolean bUNIX = false;
		boolean bSCTP = false;

		final Map<String, AtomicInteger> states = new HashMap<>();

		if (previousKeys != null) {
			for (final String state : previousKeys)
				states.put(state, new AtomicInteger(0));
		}

		try (BufferedReader br = new BufferedReader(new StringReader(output))) {
			while ((sLine = br.readLine()) != null) {
				if (sLine.length() == 0) {
					bUDP4 = bUDP6 = bTCP4 = bTCP6 = bSCTP = bUNIX = false;
				}

				boolean bSet = false;

				if (isSolarisOS()) {
					if (sLine.equals("UDP: IPv4")) {
						bSet = bUDP4 = true;
					}

					if (sLine.equals("UDP: IPv6")) {
						bSet = bUDP6 = true;
					}

					if (sLine.equals("TCP: IPv4")) {
						bSet = bTCP4 = true;
					}

					if (sLine.equals("TCP: IPv6")) {
						bSet = bTCP6 = true;
					}

					if (sLine.startsWith("SCTP:")) {
						bSet = bSCTP = true;
					}

					if (sLine.equals("Active UNIX domain sockets")) {
						bSet = bUNIX = true;
					}

					if (bSet) {
						sLine = br.readLine();
						sLine = br.readLine();
					}
				}
				else
					if (isMacOS()) {
						if (sLine.equals("Active LOCAL (UNIX) domain sockets")) {
							bSet = bUNIX = true;
						}

						if (bSet) {
							sLine = br.readLine();
						}
					}
					else
						if (isLinuxOS()) {
							if (sLine.startsWith("Active UNIX domain sockets")) {
								bSet = bUNIX = true;

								if (bSet) {
									sLine = br.readLine();
								}
							}
						}

				if (bSet) {
					continue;
				}

				if (bUNIX) {
					increment(states, "sockets_unix");
					continue;
				}

				final String[] split = sLine.split("\\s+");

				if (split.length < 2) {
					continue;
				}

				if (isSolarisOS()) {
					if (bUDP4) {
						increment(states, "sockets_udp");
					}
					else
						if (bUDP6) {
							increment(states, "sockets_udp6");
						}
						else
							if (bTCP4 || bTCP6) {
								final String base = bTCP4 ? "sockets_tcp" : "sockets_tcp6";

								increment(states, base);
								increment(states, base + "_" + split[split.length - 1]);
							}
							else
								if (bSCTP) {
									increment(states, "sockets_sctp");
								}
				}
				else
					if (isMacOS()) {
						final String s0 = split[0];

						if (s0.startsWith("udp")) {
							if (s0.indexOf('6') >= 0) {
								increment(states, "sockets_udp6");
							}

							if (s0.indexOf('4') >= 0) {
								increment(states, "sockets_udp");
							}
						}
						else
							if (s0.startsWith("tcp")) {
								final String state = split[split.length - 1];

								if (s0.indexOf('6') >= 0) {
									increment(states, "sockets_tcp6");
									increment(states, "sockets_tcp6_" + state);
								}

								if (s0.indexOf('4') >= 0) {
									increment(states, "sockets_tcp");
									increment(states, "sockets_tcp_" + state);
								}
							}
					}
					else
						if (isLinuxOS()) {
							final String s0 = split[0];

							if (s0.startsWith("udp")) {
								if (s0.indexOf('6') >= 0) {
									increment(states, "sockets_udp6");
								}
								else {
									increment(states, "sockets_udp");
								}
							}
							else
								if (s0.startsWith("tcp")) {
									final String state = split[split.length - 1];

									if (s0.indexOf('6') >= 0) {
										increment(states, "sockets_tcp6");
										increment(states, "sockets_tcp6_" + state);
									}
									else {
										increment(states, "sockets_tcp");
										increment(states, "sockets_tcp_" + state);
									}
								}
								else
									if (s0.equals("raw")) {
										increment(states, "sockets_raw");
									}
						}
			}
		}

		for (final Map.Entry<String, AtomicInteger> me : states.entrySet()) {
			r.addSet(me.getKey(), me.getValue().doubleValue());
		}

		previousKeys = states.keySet();

		final Vector<Object> ret = new Vector<>(2);

		if ((r.param != null) && (r.param.length > 0)) {
			ret.add(r);
		}

		if ((er.param != null) && (er.param.length > 0)) {
			ret.add(er);
		}

		return ret;
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
		final Netstat si = new Netstat();
		si.init(new MNode("localhost", null, null), "");

		while (true) {
			Utils.dumpResults(si.doProcess());

			Thread.sleep(1000 * 30);
		}
	}
}
