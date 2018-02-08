package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.InetAddress;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.TimestampedResult;
import lia.Monitor.monitor.eResult;
import lia.util.ntp.NTPDate;

/**
 * @author ML
 */
public class monJenkins extends monGenericUDP {
	private static final long serialVersionUID = 1L;

	/** Logger used by this class */
	private static final Logger logger = Logger.getLogger(monJenkins.class.getName());

	/**
	 * Module name
	 */
	static final public String MODULE_NAME = "monJenkins";

	/**
	 * 
	 */
	public monJenkins() {
		super(MODULE_NAME);

		this.resTypes = null;
		this.gPort = 8885;
	}

	@Override
	public MonModuleInfo init(final MNode node, final String arg) {
		this.Node = node;
		init_args(arg);
		this.info = new MonModuleInfo();

		logger.log(Level.INFO, "Initializing with: ListenPort=" + this.gPort);

		try {
			this.udpLS = new GenericUDPListener(this.gPort, this, null);
		} catch (final Throwable tt) {
			logger.log(Level.WARNING, " Cannot create UDPListener !", tt);
		}

		this.isRepetitive = true;

		this.info.ResTypes = this.resTypes;
		this.info.name = MODULE_NAME;
		OsName = "linux";

		return this.info;
	}

	@Override
	void init_args(final String args) {
		String list = args;

		if ((list == null) || (list.length() == 0))
			return;
		if (list.startsWith("\""))
			list = list.substring(1);
		if (list.endsWith("\"") && (list.length() > 0))
			list = list.substring(0, list.length() - 1);
		final String params[] = list.split("(\\s)*,(\\s)*");
		if ((params == null) || (params.length == 0))
			return;

		for (final String param : params) {
			final int itmp = param.indexOf("ListenPort");
			if (itmp != -1) {
				final String tmp = param.substring(itmp + "ListenPort".length()).trim();
				final int iq = tmp.indexOf("=");
				final String port = tmp.substring(iq + 1).trim();
				try {
					this.gPort = Integer.valueOf(port).intValue();
				} catch (final Throwable tt) {
					// ignore
				}
				continue;
			}
		}
	}

	private final Vector<TimestampedResult> tempData = new Vector<TimestampedResult>();

	@Override
	public void notifyData(final int len, final byte[] data, final InetAddress source) {
		if (data == null || data.length < 4 || len < 4 || len > data.length)
			return;

		final String content = new String(data, 0, len);

		final BufferedReader br = new BufferedReader(new StringReader(content));

		try {
			String line;

			while ((line = br.readLine()) != null) {
				line = line.trim();

				if (line.length() < 4)
					continue;

				final StringTokenizer st = new StringTokenizer(line);

				if (st.countTokens() < 3 || st.countTokens() % 2 != 1)
					continue;

				final String key = st.nextToken();

				final int idx = key.indexOf('/');

				if (idx < 0)
					continue;

				final String clusterName = key.substring(0, idx);
				final String nodeName = key.substring(idx + 1).replace('/', '|');

				Result r = null;

				eResult er = null;

				while (st.hasMoreTokens()) {
					final String paramName = st.nextToken();
					final String paramValue = st.nextToken();

					try {
						final double d = Double.parseDouble(paramValue);

						if (r == null) {
							r = new Result(this.Node.getFarmName(), clusterName, nodeName, MODULE_NAME);
							r.time = NTPDate.currentTimeMillis();
						}

						r.addSet(paramName, d);
					} catch (final NumberFormatException nfe) {
						if (er == null) {
							er = new eResult(this.Node.getFarmName(), clusterName, nodeName, MODULE_NAME, null);
							er.time = NTPDate.currentTimeMillis();
						}

						er.addSet(paramName, paramValue);
					}
				}

				if (r != null)
					tempData.add(r);

				if (er != null)
					tempData.add(er);
			}
		} catch (final IOException e) {
			// ignore
		} finally {
			try {
				br.close();
			} catch (final IOException ioe) {
				// ignore
			}
		}
	}

	/** build a result vector with the current data */
	@Override
	public Object doProcess() throws Exception {
		final Vector<TimestampedResult> vrez = new Vector<TimestampedResult>();

		synchronized (tempData) {
			vrez.addAll(tempData);
			tempData.clear();
		}

		return vrez;
	}

	/**
	 * Module debug method
	 * 
	 * @param args
	 */
	public static void main(final String[] args) {
		final String host = "localhost"; // args[0] ;

		final monJenkins aa = new monJenkins();
		String ad = null;
		try {
			ad = InetAddress.getByName(host).getHostAddress();
		} catch (final Exception e) {
			System.out.println(" Can not get ip for node " + e);
			System.exit(-1);
		}

		aa.init(new MNode(host, ad, null, null), "\"ListenPort=8885\"");

		for (;;)
			try {
				final Object bb = aa.doProcess();
				try {
					Thread.sleep(1 * 1000);
				} catch (final Exception e1) {
					// ignore
				}

				if ((bb != null) && (bb instanceof Vector)) {
					final Vector<?> res = (Vector<?>) bb;
					if (res.size() > 0) {
						System.out.println("Got a Vector with " + res.size() + " results");
						for (int i = 0; i < res.size(); i++)
							System.out.println(" { " + i + " } >>> " + res.elementAt(i));
					}
				}
			} catch (final Exception e) {
				// ignore
			}
	}
}
