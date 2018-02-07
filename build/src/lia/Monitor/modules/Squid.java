package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.Utils;
import lia.util.ntp.NTPDate;

/**
 * @author costing
 * @since 2014-06-13
 */
public class Squid extends AbstractSchJobMonitoring {

	/** Logger used by this class */
	private static final Logger logger = Logger.getLogger(monXrootd.class.getName());

	private static final long serialVersionUID = 1L;

	private int port = 3128;

	private final char[] separators = new char[] { '/', ' ', '%' };

	@Override
	public Object doProcess() throws Exception {
		final Vector<Object> ret = new Vector<Object>();

		final Result r = new Result(node.farm.name, node.cluster.name, node.name, "Squid", null);
		r.time = NTPDate.currentTimeMillis();

		final eResult er = new eResult(r.FarmName, r.ClusterName, r.NodeName, "Squid", null);
		er.time = r.time;

		try {
			final Socket s = new Socket(node.name, port);

			final PrintWriter pw = new PrintWriter(s.getOutputStream());
			pw.print("GET cache_object://localhost/5min HTTP/1.0\r\n\r\n");
			pw.flush();
			// pw.close();

			final BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));

			String line = br.readLine();

			StringTokenizer st = new StringTokenizer(line, " ");

			boolean ok = true;

			if (!st.nextToken().startsWith("HTTP/")) {
				r.addSet("Status", 2);
				er.addSet("Message", "Unexpected first line: "+line);
				ok = false;
			} else {
				final int statusCode = Integer.parseInt(st.nextToken());

				if (statusCode != 200) {
					r.addSet("Status", 3);
					er.addSet("Message", "HTTP code: "+statusCode);
					r.addSet("HTTPCode", statusCode);
					ok = false;
				}
			}

			if (ok) {
				while ((line = br.readLine()) != null) {
					final int idx = line.indexOf('=');

					if (idx > 0) {
						final String key = line.substring(0, idx).trim();
						final String value = line.substring(idx + 1).trim();

						int idx2 = value.length();

						for (final char c : separators) {
							final int idx3 = value.indexOf(c);

							if (idx3 > 0 && idx3 < idx2)
								idx2 = idx3;
						}

						try {
							final double d = Double.parseDouble(value.substring(0, idx2));

							final String extra = value.substring(idx2).trim();

							r.addSet(key, d);

							if (extra.length() > 0)
								er.addSet(key + "_unit", extra);
						} catch (final Exception e) {
							logger.log(Level.WARNING, "Exception parsing: " + line, e);
						}
					}
				}

				r.addSet("Status", 0);
			}

			s.close();
		} catch (final IOException ioe) {
			// ignore
			r.addSet("Status", 1);

			er.addSet("Message", ioe.getMessage());
			
			logger.log(Level.WARNING, "Exception getting the monitoring data from "+node.name+":"+port, ioe);
		}

		if (r.param != null)
			ret.add(r);

		if (er.param != null)
			ret.add(er);

		return ret;
	}

	@Override
	protected MonModuleInfo initArgs(final String args) {
		if (args != null && args.length() > 0) {
			final StringTokenizer st = new StringTokenizer(args, ";");

			while (st.hasMoreTokens()) {
				final StringTokenizer st2 = new StringTokenizer(st.nextToken(), "=:");

				if (st2.countTokens() != 2)
					continue;

				final String key = st2.nextToken();
				final String value = st2.nextToken();

				if (key.equalsIgnoreCase("port"))
					try {
						port = Integer.parseInt(value);
					} catch (final NumberFormatException nfe) {
						// ignore
					}
			}
		}

		logger.log(Level.INFO, "Querying " + node.name + ":" + port);

		return null;
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(final String[] args) throws Exception {
		final Squid df = new Squid();
		MFarm farm = new MFarm("test");
		MCluster cluster = new MCluster("Squid", farm);

		df.init(new MNode("alien.spacescience.ro", cluster, farm), "");

		Utils.dumpResults(df.doProcess());
	}

}
