package lia.web.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ExtendedResult;
import lia.util.LRUMap;
import lia.util.ntp.NTPDate;
import lia.web.servlets.web.Utils;

/**
 * @author costing
 * @since forever
 */
public abstract class ThreadedPage extends ServletExtension implements Runnable {
	private static final long serialVersionUID = 1L;

	/** Logger */
	private static final Logger logger = Logger.getLogger(ThreadedPage.class.getName());

	/**
	 * if everything is ok with this page
	 */
	protected boolean bAuthOK = false;

	private boolean bFinishedOK = false;

	/**
	 * True if this is a GET request, false otherwise
	 */
	protected boolean bGet = false;

	/**
	 * Should I really execute this request ?
	 */
	protected boolean bShouldExecute = true;

	private static Object exporter = null;

	/**
	 * when was the repository first started
	 */
	public static final long lRepositoryStarted = NTPDate.currentTimeMillis();

	/**
	 * Servlet initialization code
	 */
	public abstract void doInit();

	/**
	 * Base html template directory
	 */
	protected String sResDir = "";

	/**
	 * Base configuration directory
	 */
	protected String sConfDir = "";

	/**
	 * Get the exported object
	 *
	 * @return some UDP exporter
	 */
	public static final Object getExporter() {
		return exporter;
	}

	/**
	 * Set the exporter object
	 *
	 * @param o
	 */
	public static final void setExporter(final Object o) {
		exporter = o;
	}

	@Override
	public final void doGet(final HttpServletRequest req, final HttpServletResponse resp) {
		bGet = true;
		execute(req, resp);
	}

	@Override
	public final void doPost(final HttpServletRequest req, final HttpServletResponse resp) {
		bGet = false;
		execute(req, resp);
	}

	private static final int TIME_DIVISION = 10;

	/* Cleanup Thread */

	private static class CleanupThread extends Thread {
		private final String sConfDir;

		/**
		 * @param confDir
		 */
		public CleanupThread(final String confDir) {
			sConfDir = confDir;
			setName("lia.web.utils.ThreadedPage.CleanupThread");
			setDaemon(true);
		}

		@Override
		public void run() {
			while (true) {
				try {
					sleep(1 * 60 * 1000); // save stats every minute
				}
				catch (final Exception e) {
					// return; // somebody interrupted us ... ok ...
				}

				if (sConfDir != null)
					try {
						final PrintWriter pw = new PrintWriter(new FileWriter(sConfDir + "REPOSITORY.total_requests"));
						pw.println("" + lTotalRequests);
						pw.flush();
						pw.close();
					}
					catch (final Exception e) {
						System.err.println("WEB: cannot update the repository totals : " + e + " (" + e.getMessage() + ")");
					}
			}
		}
	}

	private static CleanupThread cleanup = null;

	/* ***************** */

	private static volatile long lRequests = 0;

	private static volatile long lCurrentRequestSeq = 0;

	/**
	 * Statistics function, how many request were served in this session
	 *
	 * @return number of requests
	 */
	public static long getRequestCount() {
		return lRequests;
	}

	/**
	 * @return number of IPv4 requests
	 */
	public static long getIPv4Requests() {
		return ipv4Requests;
	}

	/**
	 * @return number of IPv6 requests
	 */
	public static long getIPv6Requests() {
		return ipv6Requests;
	}

	/**
	 * Synchronization object
	 */
	public static final Object oLock = new Object();

	private static long lFirstRunDate = -1;

	/**
	 * Total number of requests
	 */
	public static volatile long lTotalRequests = 0;

	private long lCurrentRequest = 0;

	private long lTimingStarted = 0;

	private boolean bTimingEnabled = false;

	private static volatile long ipv4Requests = 0;

	private static volatile long ipv6Requests = 0;

	/**
	 * Increment the number of requests
	 */
	public static final void incrementRequestCount() {
		lRequests++;
		lTotalRequests++;
	}

	/**
	 * Increment the number of IPv4 requests
	 */
	public static final void incrementIPv4RequestCount() {
		ipv4Requests++;
	}

	/**
	 * Increment the number of IPv6 requests
	 */
	public static final void incrementIPv6RequestCount() {
		ipv6Requests++;
	}

	/**
	 * Get the first time when this repository was ever started
	 *
	 * @return time in millis of the first start
	 */
	public static long getFirstRunEpoch() {
		return lFirstRunDate;
	}

	/**
	 * Configure the page debugging based on a page configuration
	 *
	 * @param prop
	 */
	protected final void setLogTiming(final Properties prop) {
		bTimingEnabled = pgetb(prop, "lia.web.page_timing", bTimingEnabled);
	}

	/**
	 * Request firewall (to ban certain abusive indexing services, users etc)
	 *
	 * @param request
	 * @param response
	 * @return true if the request should be processed further, false if not
	 */
	public static boolean acceptRequest(final HttpServletRequest request, final HttpServletResponse response) {
		if (AppConfig.getb("lia.web.robots_exclusion.enabled", true)) {
			boolean bRejected = false;
			String sRejectReason = null;

			String sUA = request.getHeader("User-Agent");

			if ((sUA == null) || (sUA.length() == 0) || sUA.equals("-")) {
				bRejected = AppConfig.getb("lia.web.robots_exclusion_null_UA", true);

				if (bRejected)
					sRejectReason = "UA=<NULL>";
			}

			if (!bRejected && (sUA != null)) {
				final String[] vsNames = AppConfig.getVectorProperty("lia.web.robots_exclusion.names", "msnbot,searchpreview,psbot");

				if ((vsNames != null) && (vsNames.length > 0)) {
					sUA = sUA.toLowerCase();

					for (final String vsName : vsNames) {
						final String s = vsName.trim().toLowerCase();

						if ((s.length() > 0) && sUA.startsWith(s)) {
							bRejected = true;
							sRejectReason = "UA=" + sUA;
							break;
						}
					}
				}
			}

			if (!bRejected) {
				final String[] vsIPNames = AppConfig.getVectorProperty("lia.web.robots_exclusion.ip_names");

				if ((vsIPNames != null) && (vsIPNames.length > 0)) {
					final String sHostName = getHostName(request.getRemoteAddr());

					for (final String vsIPName : vsIPNames) {
						final String s = vsIPName.trim().toLowerCase();

						if ((s.length() > 0) && (sHostName.indexOf(s) >= 0)) {
							bRejected = true;
							sRejectReason = "HOST=" + sHostName;
							break;
						}
					}
				}
			}

			if (!bRejected) {
				final String[] vsIPClasses = AppConfig.getVectorProperty("lia.web.robots_exclusion.ip_classes");

				if ((vsIPClasses != null) && (vsIPClasses.length > 0)) {
					final String sIP = request.getRemoteAddr();

					for (final String vsIPClasse : vsIPClasses) {
						final String s = vsIPClasse.trim().toLowerCase();

						if ((s.length() > 0) && sIP.startsWith(s)) {
							bRejected = true;
							sRejectReason = "IP=" + sIP;
						}
					}
				}
			}

			if (bRejected) {
				Utils.logRequest("reject_" + request.getRequestURI() + "?" + request.getQueryString() + "&reason=" + sRejectReason, 1, request, false, 0);

				logger.fine("rejected request from: " + getHostName(request.getRemoteAddr()) + ", user agent: " + request.getHeader("User-Agent") + " because : " + sRejectReason);

				try {
					response.setContentType("text/html");

					final PrintWriter pwOut = response.getWriter();
					pwOut.println(
							"<html><head><title>MonALISA Grid Monitoring tool</title></head><body><a href='http://monalisa.caltech.edu/' alt='MonALISA Grid Monitoring tool'>MonALISA home page</a></body></html>");
					pwOut.flush();
					pwOut.close();
				}
				catch (final Exception e) {
					// ignore
				}

				return false;
			}
		}

		return true;
	}

	private final static DateFormat rfc1123Format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);

	static {
		rfc1123Format.setTimeZone(TimeZone.getTimeZone("GMT"));
	}

	/**
	 * @param d
	 * @return RFC1123-formatted date
	 */
	public static synchronized String toHttpDate(final Date d) {
		return rfc1123Format.format(d);
	}

	private final void execute(final HttpServletRequest req, final HttpServletResponse resp) {
		bRedirect = false;

		bTimingEnabled = AppConfig.getb("lia.web.page_timing", false);

		request = req;
		response = resp;

		if (!acceptRequest(req, resp)) {
			bRedirect = true;
			return;
		}

		lCurrentRequest = lCurrentRequestSeq++;

		if (bTimingEnabled)
			lTimingStarted = NTPDate.currentTimeMillis();

		final ServletContext sc = getServletContext();

		sResDir = sc.getRealPath("/");
		if (!sResDir.endsWith("/"))
			sResDir += "/";

		sConfDir = sResDir + "WEB-INF/conf/";

		sResDir += "WEB-INF/res/";

		final String sExtraResPath = gets("res_path");

		if ((sExtraResPath.length() > 0) && !sExtraResPath.startsWith(".") && !sExtraResPath.startsWith("/") && !sExtraResPath.endsWith("/") && (sExtraResPath.indexOf("..") < 0))
			sResDir += sExtraResPath + "/";

		final String sExtraCookies = req.getHeader("MLSetCookies");

		HttpSession sess = null;

		if ((sExtraCookies != null) && (sExtraCookies.length() > 0) && ((sess = request.getSession(true)) != null)) {
			final StringTokenizer st = new StringTokenizer(sExtraCookies, "&");

			while (st.hasMoreTokens()) {
				final String s = st.nextToken();

				final int idx = s.indexOf("=");

				if (idx > 0) {
					final String sKey = s.substring(0, idx);
					final String sVal = s.substring(idx + 1);

					sess.setAttribute(sKey, sVal);
				}
			}
		}

		synchronized (oLock) {
			if (cleanup == null) {
				cleanup = new CleanupThread(sConfDir);
				cleanup.start();
			}

			if (lFirstRunDate < 0) {
				BufferedReader br = null;

				try {
					br = new BufferedReader(new FileReader(sConfDir + "REPOSITORY.start_date"));
					lFirstRunDate = Long.parseLong(br.readLine());
				}
				catch (final RuntimeException re) {
					// ignore
				}
				catch (final Exception e) {
					lFirstRunDate = NTPDate.currentTimeMillis();

					try {
						final PrintWriter pw = new PrintWriter(new FileWriter(sConfDir + "REPOSITORY.start_date"));
						pw.println("" + lFirstRunDate);
						pw.flush();
						pw.close();
					}
					catch (final Exception e2) {
						System.err.println("WEB: cannot set the repository start date : " + e2 + " (" + e2.getMessage() + ")");
					}
				}
				finally {
					if (br != null)
						try {
							br.close();
						}
						catch (final IOException ioe) {
							// ignore
						}
				}

				try {
					br = new BufferedReader(new FileReader(sConfDir + "REPOSITORY.total_requests"));
					lTotalRequests = Long.parseLong(br.readLine());
				}
				catch (final RuntimeException re) {
					// ignore
				}
				catch (final Exception e) {
					lTotalRequests = 0;
				}
				finally {
					if (br != null)
						try {
							br.close();
						}
						catch (final IOException ioe) {
							// ignore
						}
				}
			}

			if (gets("cache_refresh_request").length() <= 0)
				incrementRequestCount();

			if (exporter == null) {
				final String sSite = AppConfig.getProperty("lia.web.is_site", null);

				try {
					if (sSite == null) {
						int port = AppConfig.geti("lia.Repository.tomcat_port", 0);

						if (port == 0)
							port = req.getServerPort();

						exporter = new ExportStatistics(port);
					}
				}
				catch (final Throwable t) {
					System.err.println("Cannot initiate ExportStatistics, falling back to Site");
				}

				if (exporter == null)
					try {
						exporter = new ExportSiteStatistics(req.getServerPort());
					}
					catch (final Throwable t) {
						System.err.println("Cannot initiate ExportSiteStatistics either, disabling export: " + t + " (" + t.getMessage() + ")");
						t.printStackTrace();
						exporter = new Object();
					}
			}
		}

		response.setHeader("P3P", "CP=\"NOI DSP COR LAW CURa DEVa TAIa PSAa PSDa OUR BUS UNI COM NAV\"");
		response.setHeader("Expires", "0");
		response.setHeader("Last-Modified", toHttpDate(new Date()));
		response.setHeader("Cache-Control", "no-cache");
		response.setHeader("Pragma", "no-cache");
		response.setHeader("Connection", "keep-alive");
		response.setCharacterEncoding("UTF-8");

		bFinishedOK = false;
		bAuthOK = false;

		final Thread t = new Thread(this, "(ML) WEB Page : " + request.getRequestURI() + "?" + request.getQueryString() + " (page=" + request.getParameter("page") + ")");

		t.start();

		final int iSleep = 1000 / TIME_DIVISION;
		final long lStartTime = System.currentTimeMillis();
		final long lMaxAllowedTime = lStartTime + (getMaxRunTime() * 1000L);

		while ((!bFinishedOK) && (System.currentTimeMillis() < lMaxAllowedTime))
			try {
				Thread.sleep(iSleep);
			}
			catch (final InterruptedException e) {
				System.err.println("ThreadedPage: caught (and ignored) interrupted exception in sleep: " + e + " (" + e.getMessage() + ")");
				e.printStackTrace();
			}

		if (!bFinishedOK) {
			try {
				t.interrupt();
			}
			catch (final Throwable tt) {
				// ignore
			}

			// redirect(sDefaultPage);
			try {
				response.setContentType("text/html");

				pwOut.println("<!-- page timeout, getMaxRunTime()= " + getMaxRunTime() + ", lStart=" + lStartTime + ", now=" + System.currentTimeMillis() + ", bFinishedOk = " + bFinishedOK
						+ ", bShouldExecute = " + bShouldExecute + " -->");
				pwOut.flush();
			}
			catch (final Exception e) {
				// ignore
			}
		}
	}

	/**
	 * Debug method
	 *
	 * @return true if the debug is enabled, false if not
	 */
	protected final boolean logTiming() {
		return bTimingEnabled;
	}

	/**
	 * Log a debug message, if the debugging is enabled
	 *
	 * @param sMessage
	 */
	protected final void logTiming(final String sMessage) {
		if (bTimingEnabled)
			System.err.println(" (" + lCurrentRequest + ") @ " + (NTPDate.currentTimeMillis() - lTimingStarted) + " : " + sMessage);
	}

	/**
	 * Default maximum run time for a servlet
	 *
	 * @return 120 = 2 minutes
	 */
	protected int getMaxRunTime() {
		return 120; // default, 120 seconds
	}

	private final void init_page() {
		try {
			osOut = response.getOutputStream();
			pwOut = new PrintWriter(osOut);
		}
		catch (final Exception e) {
			// inore
		}
	}

	/**
	 * @return ?
	 */
	protected boolean getDebugFlag() {
		return false;
	}

	private static int iRequests = 0;
	private static long lTotalTime = 0;
	private static double dMinTime = -1;
	private static double dMaxTime = -1;

	private static final Object oStatsLock = new Object();

	private static void updateStats(final long lTime) {
		if (lTime < 0)
			return;

		synchronized (oStatsLock) {
			if (iRequests + jspMeasurements == 0) {
				dMinTime = dMaxTime = lTime;
			}
			else {
				dMinTime = Math.min(dMinTime, lTime);
				dMaxTime = Math.max(dMaxTime, lTime);
			}

			iRequests++;
			lTotalTime += lTime;
		}
	}

	private static long jspMeasurements = 0;
	private static double totalJSPExecutionTime = 0;

	/**
	 * Inform about a JSP execution where the execution time is known
	 *
	 * @param dTime
	 */
	public static void addJSPMeasurement(final double dTime) {
		synchronized (oStatsLock) {
			if (iRequests + jspMeasurements == 0) {
				dMinTime = dMaxTime = dTime;
			}
			else {
				dMinTime = Math.min(dMinTime, dTime);
				dMaxTime = Math.max(dMaxTime, dTime);
			}

			jspMeasurements++;
			totalJSPExecutionTime += dTime;
		}
	}

	private static long lLastStatsCalled = System.currentTimeMillis();

	/**
	 * Get the average, min and max time it took to generate dynamic pages since the last call
	 *
	 * @return page generation times
	 */
	public static ExtendedResult getStats() {
		synchronized (oStatsLock) {
			final double dt = System.currentTimeMillis() - lLastStatsCalled;
			lLastStatsCalled = System.currentTimeMillis();

			if (iRequests + jspMeasurements > 0) {
				final ExtendedResult er = new ExtendedResult();

				er.addSet("msPerRequest", (lTotalTime + totalJSPExecutionTime) / (iRequests + jspMeasurements));
				er.min = dMinTime;
				er.max = dMaxTime;

				if (iRequests > 0)
					er.addSet("msPerServlet", (double) lTotalTime / iRequests);

				if (jspMeasurements > 0)
					er.addSet("msPerJSP", totalJSPExecutionTime / jspMeasurements);

				if (dt > 1) {
					er.addSet("servlet_R", iRequests / dt);
					er.addSet("jsp_R", jspMeasurements / dt);
				}

				iRequests = 0;
				lTotalTime = 0;
				totalJSPExecutionTime = 0;
				jspMeasurements = 0;
				dMinTime = dMaxTime = -1;

				return er;
			}

			return null;
		}
	}

	@Override
	public final void run() {
		bShouldExecute = true;

		final long lStartTime = System.currentTimeMillis();

		try {
			init_page();
			masterInit();

			if (!bShouldExecute) {
				bFinishedOK = true;
				return;
			}

			doInit();

			if (bGet)
				execGet();
			else
				execPost();
		}
		catch (final Throwable t) {
			System.err.println("ThreadedPage execution exception : " + t + " (" + t.getMessage() + ")");
			t.printStackTrace();
			System.err.println("Java memory info: \n" + "  Free memory : " + Runtime.getRuntime().freeMemory() + "\n" + "  Total memory: " + Runtime.getRuntime().totalMemory() + "\n"
					+ "  Max memory  : " + Runtime.getRuntime().maxMemory());
		}
		finally {
			updateStats(System.currentTimeMillis() - lStartTime);
		}

		if (shouldClose()) {
			try {
				osOut.flush();
			}
			catch (final Exception ioe) {
				// ignore
			}

			try {
				osOut.close();
			}
			catch (final Exception e) {
				// ignore
			}
		}

		bFinishedOK = true;
	}

	/**
	 * Whether or not the wrapper should close the output streams
	 *
	 * @return false by default, this is to be overriden
	 */
	protected boolean shouldClose() {
		return false;
	}

	/**
	 * cine mosteneste trebuie sa implementeze cel putin functia asta!
	 */
	public abstract void execGet();

	/**
	 * to be overriden, by default falls back to the execGet() one.
	 */
	public void execPost() {
		execGet();
	}

	/**
	 * to be overriden, by default does nothing
	 */
	protected void masterInit() {
		// to be overriden
	}

	/**
	 * Try to find out the reverse DNS name for the client that made this request.
	 *
	 * @return the reversed name or the original IP address, or if it encountered a fatal error in trying
	 *         to reverse the name return the default string "cannot.find.host.name"
	 */
	protected final String getHostName() {
		try {
			return getHostName(request.getRemoteAddr());
		}
		catch (final Exception e) {
			return "cannot.find.host.name(" + e + ")";
		}
	}

	private static final LRUMap<String, IPCacheEntry> ipCache = new LRUMap<String, IPCacheEntry>(AppConfig.geti("lia.web.utils.ThreadedPage.ipCache.size", 10000));

	/**
	 * Randomizer for expiration times
	 */
	static final Random rand = new Random(System.currentTimeMillis());

	private static final class IPCacheEntry {
		/**
		 * Name for the corresponding IP address
		 */
		public final String sName;

		/**
		 * Expire time
		 */
		public final long lExpires;

		/**
		 * @param name
		 * @param sIP
		 */
		public IPCacheEntry(final String name, final String sIP) {
			sName = name;

			lExpires = System.currentTimeMillis() + (long) (1000 * 60 * (sIP.equals(name) ? 30 : 120) * (1 + rand.nextFloat()));
		}
	}

	private static String getCachedHostName(final String sIP) {
		synchronized (ipCache) {
			final IPCacheEntry cache = ipCache.get(sIP);

			if (cache == null)
				return null;

			if (cache.lExpires < System.currentTimeMillis()) {
				ipCache.remove(sIP);
				return null;
			}

			return cache.sName;
		}
	}

	private static void putCachedHostName(final String sIP, final String sName) {
		final IPCacheEntry cache = new IPCacheEntry(sName, sIP);

		synchronized (ipCache) {
			ipCache.put(sIP, cache);
		}

		resolveQueue.remove(sIP);
	}

	/**
	 * Try to reverse a given IP address
	 *
	 * @param _sIP
	 * @return the reversed name or the original IP address if the reverse process is not possible
	 */
	public static String getHostName(final String _sIP) {
		return getHostName(_sIP, false);
	}

	/**
	 * DNS async resolver queue
	 */
	static final LinkedBlockingQueue<String> resolveQueue = new LinkedBlockingQueue<String>(2000);

	private static final class AsyncNameSolver extends Thread {
		/**
		 * Default constructor
		 */
		public AsyncNameSolver() {
			super("lia.web.utils.ThreadedPage.AsyncNameSolver");
			setDaemon(true);
		}

		@Override
		public void run() {
			while (true) {
				try {
					final String s = resolveQueue.take();

					setName("lia.web.utils.ThreadedPage.AsyncNameSolver (" + resolveQueue.size() + "): " + s);

					// force name lookup
					getHostName(s, false);
				}
				catch (final Exception e) {
					// ignore
				}

				setName("lia.web.utils.ThreadedPage.AsyncNameSolver (" + resolveQueue.size() + ")");
			}
		}
	}

	private static final Thread asyncNameSolver = new AsyncNameSolver();

	private static final boolean useTimeoutResolver = AppConfig.getb("lia.web.utils.ThreadedPage.dns.withTimeout", true);

	static {
		asyncNameSolver.start();
	}

	/**
	 * Try to reverse a given IP address
	 *
	 * @param _sIP
	 * @param bCacheOnly
	 *            if <code>true</code> then the hostname will be returned only if found in cache
	 *            and if it's not found then an async lookup will be scheduled. If <code>false</code> a blocking lookup
	 *            will be executed if needed.
	 * @return the reversed name or the original IP address if the reverse process is not possible
	 */
	public static String getHostName(final String _sIP, final boolean bCacheOnly) {
		if ((_sIP == null) || (_sIP.length() <= 0))
			return _sIP;

		final String sIP = _sIP.toLowerCase();

		final String sName = getCachedHostName(sIP);

		if (sName != null)
			return sName;

		if (bCacheOnly) {
			if (!resolveQueue.contains(sIP))
				resolveQueue.offer(sIP);

			return sIP;
		}

		final InetAddress addr;

		try {
			addr = InetAddress.getByName(sIP);
		}
		catch (final Exception e) {
			// not an IP address
			return sIP;
		}

		String sHostName = sIP;

		if (!addr.isSiteLocalAddress() && !addr.isLinkLocalAddress() && !addr.isLoopbackAddress()) {
			try {
				sHostName = useTimeoutResolver ? getHostNameWithTimeout(addr) : addr.getHostName();
			}
			catch (final Exception e) {
				// ignore
			}

			if ((sHostName == null) || (sHostName.length() == 0))
				sHostName = sIP;
		}

		// cannot be resolved, cache the negative answer
		putCachedHostName(sIP, sHostName);

		return sHostName;
	}

	private static char[] HEX = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	static Properties javaxNamingContext = new Properties();

	static {
		javaxNamingContext.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
		javaxNamingContext.put("com.sun.jndi.dns.timeout.initial", String.valueOf(AppConfig.geti("lia.web.utils.ThreadedPage.dns.initialTimeout", 500)));
		javaxNamingContext.put("com.sun.jndi.dns.timeout.retries", String.valueOf(AppConfig.geti("lia.web.utils.ThreadedPage.dns.retries", 2)));
	}

	private static String getHostNameWithTimeout(final InetAddress addr) {
		final byte[] b = addr.getAddress();
		final StringBuilder sb = new StringBuilder();

		if (addr instanceof Inet6Address) {
			for (int i = b.length - 1; i >= 0; i--) {
				sb.append(HEX[b[i] & 0xF]).append('.');
				sb.append(HEX[(b[i] & 0xF0) >> 4]).append('.');
			}

			sb.append("ip6.arpa.");
		}
		else {
			for (int i = b.length - 1; i >= 0; i--)
				sb.append(b[i] & 0xFF).append('.');

			sb.append("in-addr.arpa.");
		}

		InitialDirContext idc;

		try {
			idc = new InitialDirContext(javaxNamingContext);
		}
		catch (final NamingException ex) {
			// ignore, just return null if any error
			return null;
		}

		try {
			final Attributes attrs = idc.getAttributes(sb.toString(), new String[] { "PTR" });
			final Attribute attr = attrs.get("PTR");

			if (attr != null) {
				String trimmedFQDN = (String) attr.get(0);

				if (trimmedFQDN.endsWith("."))
					trimmedFQDN = trimmedFQDN.substring(0, trimmedFQDN.length() - 1);

				return trimmedFQDN;
			}
		}
		catch (final Throwable th) {
			// ignore, for any error return null
		}

		return null;
	}

	/**
	 * Compose an URL that has all the parameters encoded as GET parameters
	 *
	 * @return the URL of the current page
	 */
	protected String getBookmarkURL() {
		final String sPage = request.getParameter("page");

		Properties prop = null;

		if (sPage != null)
			prop = lia.web.servlets.web.Utils.getProperties(sConfDir, sPage, null, true);
		// System.err.println("getBookmarkURL : prop size is : "+prop.size()+" for '"+sConfDir+"', '"+sPage+"'");

		final TreeMap<String, TreeSet<String>> tmReq = new TreeMap<String, TreeSet<String>>();

		final Cookie[] vCookies = request.getCookies();

		final TreeSet<String> cookiesToOverride = new TreeSet<String>();

		for (int i = 0; (vCookies != null) && (i < vCookies.length); i++) {
			String sName = vCookies[i].getName();

			final String sValue = vCookies[i].getValue();

			if (sName.equals("JSESSIONID") || sName.startsWith("__utm") || sName.equals("vdnsessid") || sName.equals("vdnsessid") || sName.equals("LAZYJ_ID") || sName.equals("void")
					|| sName.equals("AI_SESSION") || sName.equals("cod") || sName.equals("codMLMenu") || sName.equals("csd") || sName.equals("csdMLMenu") || sName.startsWith("lastval_div_")
					|| sName.equals("_ga"))
				continue;

			if (prop != null) {
				final String sPropValue = pgets(prop, sName);

				if ((sPropValue != null) && sPropValue.equals(sValue))
					// cookie has the same value as the configuration file, don't pass it
					continue;
			}

			if (sName.startsWith("prop_"))
				sName = sName.substring(5);

			if ((prop != null) && ServletExtension.pgetb(prop, sName + ".cookie.ignore", false))
				continue;

			TreeSet<String> tsValues = tmReq.get(sName);

			if (tsValues == null) {
				tsValues = new TreeSet<String>();
				tmReq.put(sName, tsValues);
			}

			tsValues.add(sValue);

			cookiesToOverride.add(sName);
		}

		final Enumeration<?> e = request.getParameterNames();

		while (e.hasMoreElements()) {
			final String sName = (String) e.nextElement();

			if (sName.startsWith("interval_date_") || sName.startsWith("interval_hour_") || sName.equals("quick_interval"))
				continue;

			TreeSet<String> tsValues = tmReq.get(sName);

			if (tsValues == null) {
				tsValues = new TreeSet<String>();
				tmReq.put(sName, tsValues);
			}
			else
				// url parameters override completely the cookies
				tsValues.clear();

			final String[] values = request.getParameterValues(sName);

			for (int i = 0; (values != null) && (i < values.length); i++)
				tsValues.add(values[i]);
		}

		final Iterator<Map.Entry<String, TreeSet<String>>> it = tmReq.entrySet().iterator();

		final StringBuilder sb = new StringBuilder();

		while (it.hasNext()) {
			final Map.Entry<String, TreeSet<String>> me = it.next();

			final String sName = me.getKey();

			if (sName.equals("dont_cache") || sName.equals("submit_plot"))
				// by default don't pass these irrelevant URL arguments
				if (!pgetb(prop, sName + ".url.pass", false))
					continue;

			final TreeSet<String> ts = me.getValue();

			final Iterator<String> itValues = ts.iterator();

			while (itValues.hasNext()) {
				final String sValue = itValues.next();

				if (!cookiesToOverride.contains(sName) && pgets(prop, sName).equals(sValue))
					continue; // no point in doing this, the value is anyway the default value from the configuration file

				if (sb.length() > 0)
					sb.append('&');

				sb.append(Formatare.encode(sName)).append('=').append(Formatare.encode(sValue));
			}
		}

		final String sParam = sb.toString();

		return request.getServletPath() + (sParam.length() > 0 ? "?" + sParam : "");
	}

	public static void main(final String[] args) {
		System.err.println(toHttpDate(new Date()));
	}

}
