package lia.web.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.SingleThreadModel;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lazyj.cache.ExpirationCache;
import lia.Monitor.Store.Cache;
import lia.Monitor.Store.Fast.DB;
import lia.Monitor.monitor.ExtResult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;

/**
 * Useful servlet shortcuts
 *
 * @author costing
 * @since Jul 17, 2009
 */
public abstract class ServletExtension extends HttpServlet implements SingleThreadModel {
	private static final long serialVersionUID = 5848728412056154453L;

	private static final Logger logger = Logger.getLogger(ServletExtension.class.getName());

	/**
	 * the request
	 */
	protected HttpServletRequest request = null;

	/**
	 * the response
	 */
	protected HttpServletResponse response = null;

	/**
	 * servlet output stream
	 */
	protected OutputStream osOut = null;

	/**
	 * a print writer as alternative to output stream
	 */
	protected PrintWriter pwOut = null;

	// package protected access
	/**
	 * is it a redirect?
	 */
	boolean bRedirect = false;

	/**
	 * Get a string parameter's value
	 * 
	 * @param sParam
	 * @return value
	 */
	public String gets(final String sParam) {
		return gets(sParam, "");
	}

	/**
	 * Get a string parameter's value
	 * 
	 * @param sParam
	 * @param sDefault
	 * @return value if parameter was received or the default value if not
	 */
	public String gets(final String sParam, final String sDefault) {
		try {
			String s;
			if ((sParam != null) && ((s = request.getParameter(sParam)) != null))
				return s;
		} catch (final Exception e) {
			System.err.println("gets: Exception: " + e + "(" + e.getMessage() + ")");
			e.printStackTrace();
		}

		return sDefault;
	}

	/**
	 * get the value of an integer parameter
	 * 
	 * @param sParam
	 * @param defaultVal
	 * @return value
	 */
	public int geti(final String sParam, final int defaultVal) {
		try {
			final String s = gets(sParam);
			if (s.length() > 0)
				return Integer.parseInt(s);
		} catch (final Exception e) {
			// ignore;
		}

		return defaultVal;
	}

	/**
	 * get the value of a long parameter
	 * 
	 * @param sParam
	 * @param defaultVal
	 * @return value
	 */
	public long getl(final String sParam, final long defaultVal) {
		try {
			final String s = gets(sParam);
			if (s.length() > 0)
				return Long.parseLong(s);
		} catch (final Exception e) {
			// ignore
		}

		return defaultVal;
	}

	/**
	 * get the value of an integer parameter
	 * 
	 * @param sParam
	 * @return value
	 */
	public int geti(final String sParam) {
		return geti(sParam, 0);
	}

	/**
	 * get the value of a long parameter
	 * 
	 * @param sParam
	 * @return value
	 */
	public long getl(final String sParam) {
		return getl(sParam, 0L);
	}

	/**
	 * get the value of a float parameter
	 * 
	 * @param sParam
	 * @param defaultVal
	 * @return value
	 */
	public float getf(final String sParam, final float defaultVal) {
		try {
			return Float.parseFloat(gets(sParam));
		} catch (final NumberFormatException nfe) {
			return defaultVal;
		}
	}

	/**
	 * get the value of a double parameter
	 * 
	 * @param sParam
	 * @param defaultVal
	 * @return value
	 */
	public double getd(final String sParam, final double defaultVal) {
		try {
			return Double.parseDouble(gets(sParam));
		} catch (final NumberFormatException nfe) {
			return defaultVal;
		}
	}

	/**
	 * ?
	 */
	public static final String sDefaultPage = "/";

	/**
	 * Encode a value so that it's safe to build an URL with it
	 * 
	 * @param s
	 * @return URL-encoded value
	 */
	public static String encode(final String s) {
		return Formatare.encode(s);
	}

	/**
	 * decode an URL-encoded value
	 * 
	 * @param s
	 * @return decoded value
	 */
	public static final String decode(final String s) {
		return Formatare.decode(s);
	}

	/**
	 * redirect the client to another page
	 * 
	 * @param sURL
	 * @return true if possible
	 */
	protected boolean redirect(final String sURL) {
		bRedirect = true;

		try {
			response.sendRedirect(sURL);
			return true;
		} catch (final Exception e) {
			return false;
		}
	}

	/**
	 * Check if the code sent the client somewhere else
	 * 
	 * @return true/false
	 */
	protected boolean wasRedirect() {
		return bRedirect;
	}

	/**
	 * Get the SQL-safe version of the string
	 * 
	 * @param s
	 * @return SQL-safe variant
	 */
	public static final String esc(final String s) {
		return Formatare.mySQLEscape(s);
	}

	/**
	 * Get the HTML-safe version of the string
	 * 
	 * @param s
	 * @return HTML-safe variant
	 */
	public static final String escHtml(final String s) {
		return Formatare.tagProcess(s);
	}

	/**
	 * Get a two-(or more-) digit string, with leading 0 if necessary
	 * 
	 * @param i
	 * @return a string at least two digit long
	 */
	public static final String show0(final int i) {
		if (i < 10)
			return "0" + i;

		return "" + i;
	}

	/**
	 * Show the date and time
	 * 
	 * @param d
	 * @return date and time in a nice format
	 */
	public static final String showDate(final Date d) {
		return showNamedDate(d) + " " + showTime(d);
	}

	private static final SimpleDateFormat sdfDottedDate = new SimpleDateFormat("dd.MM.yyyy");

	/**
	 * Get the date in dd.MM.yyyy notation
	 * 
	 * @param d
	 * @return formatted date
	 */
	public static final String showDottedDate(final Date d) {
		synchronized (sdfDottedDate) {
			return sdfDottedDate.format(d);
		}
	}

	private static final SimpleDateFormat sdfNamedDate = new SimpleDateFormat("dd MMM yyyy");

	/**
	 * Get the date in "dd MMM yyyy" format
	 * 
	 * @param d
	 * @return formatted date
	 */
	public static final String showNamedDate(final Date d) {
		synchronized (sdfNamedDate) {
			return sdfNamedDate.format(d);
		}
	}

	private static final SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm");

	/**
	 * Get the time from this date
	 * 
	 * @param d
	 * @return time in "HH:mm" format
	 */
	public static final String showTime(final Date d) {
		synchronized (sdfTime) {
			return sdfTime.format(d);
		}
	}

	/**
	 * Get the value of this cookie
	 * 
	 * @param sName
	 * @return cookie's value
	 */
	public String getCookie(final String sName) {
		try {
			final Cookie vc[] = request.getCookies();
			Cookie c;

			for (final Cookie element : vc) {
				c = element;
				if (c.getName().equals(sName))
					return decode(c.getValue());
			}
		} catch (final Exception e) {
			// ignore
		}

		return "";
	}

	/**
	 * Set session cookie
	 * 
	 * @param sName
	 * @param sValue
	 * @return true if set
	 */
	public boolean setCookie(final String sName, final String sValue) {
		return setCookie(sName, sValue, -1);
	}

	/**
	 * Set a persistent cookie
	 * 
	 * @param sName
	 * @param sValue
	 * @param iAge
	 * @return true if set
	 */
	public boolean setCookie(final String sName, final String sValue, final int iAge) {
		try {
			final Cookie c = new Cookie(sName, sValue);

			c.setMaxAge(iAge);

			// c.setDomain(getDomain());
			c.setPath("/");
			c.setSecure(false);
			c.setHttpOnly(true);

			response.addCookie(c);

			return true;
		} catch (final Exception e) {
			System.err.println("setCookie exception : " + e + " (" + e.getMessage() + ")");
			return false;
		}
	}

	/**
	 * Get the server address to which this connection came, like "hostname[:port]"
	 * 
	 * @return server address
	 */
	protected String getDomain() {
		return request.getServerName() + (request.getServerPort() != 80 ? (":" + request.getServerPort()) : "");
	}

	/**
	 * Write a response, respecting the byte range requests
	 * 
	 * @param content
	 * @param request
	 * @param response
	 * @param os
	 * @return written bytes
	 * @throws IOException
	 */
	public static int writeResponse(final String content, final HttpServletRequest request, final HttpServletResponse response, final OutputStream os) throws IOException {
		return writeResponse(content.getBytes(Charset.forName("UTF-8")), request, response, os);
	}

	/**
	 * Write a response, respecting the byte range requests
	 * 
	 * @param vs
	 * @param request
	 * @param response
	 * @param os
	 * @return written bytes
	 * @throws IOException
	 */
	public static int writeResponse(final byte[] vs, final HttpServletRequest request, final HttpServletResponse response, final OutputStream os) throws IOException {
		final OutputStream responseOS = os != null ? os : response.getOutputStream();

		final String range = request.getHeader("Range");

		if ((range == null) || (range.length() == 0)) {
			response.setHeader("Content-Length", String.valueOf(vs.length));

			responseOS.write(vs);

			return vs.length;
		}

		if (!range.startsWith("bytes=")) {
			response.sendError(416);
			return -1;
		}

		StringTokenizer st = new StringTokenizer(range.substring(6), ",");

		final StringBuilder responseRange = new StringBuilder();

		int first = -1;
		int last = -1;
		int count = 0;

		while (st.hasMoreTokens()) {
			final String s = st.nextToken();

			final int idx = s.indexOf('-');

			int start;
			int end;

			if (idx > 0) {
				start = Integer.parseInt(s.substring(0, idx));

				if (idx < (s.length() - 1)) {
					end = Integer.parseInt(s.substring(idx + 1));

					if (end >= vs.length)
						end = vs.length - 1;
				}
				else
					end = vs.length - 1;

				if (start > end) {
					response.sendError(416);
					return -1;
				}
			}
			else {
				start = Integer.parseInt(s.substring(idx + 1));
				end = vs.length - 1;

				start = end - start;

				if (start < 0)
					start = 0;
			}

			if ((first < 0) || (first > start))
				first = start;

			if ((last < 0) || (last < end))
				last = end;

			count += (end - start) + 1;
		}

		responseRange.append('/').append(vs.length);

		response.setHeader("Content-Length", String.valueOf(count));
		response.setHeader("Content-Range", "bytes " + first + "-" + last + "/" + vs.length);
		response.setStatus(206); // partial content

		st = new StringTokenizer(range.substring(6), ",");

		while (st.hasMoreTokens()) {
			final String s = st.nextToken();

			final int idx = s.indexOf('-');

			int start;
			int end;

			if (idx > 0) {
				start = Integer.parseInt(s.substring(0, idx));

				if (idx < (s.length() - 1)) {
					end = Integer.parseInt(s.substring(idx + 1));

					if (end >= vs.length)
						end = vs.length - 1;
				}
				else
					end = vs.length - 1;

				if (start > end) {
					response.sendError(416);
					return -1;
				}
			}
			else {
				start = Integer.parseInt(s.substring(idx + 1));
				end = vs.length - 1;

				start = end - start;

				if (start < 0)
					start = 0;
			}

			responseOS.write(vs, start, (end - start) + 1);
		}

		responseOS.flush();

		return count;
	}

	/**
	 * Human-readable long formatting, with groups of 3 digits separated by comma
	 * 
	 * @param l
	 * @return formatted long
	 */
	public static final String showDottedInt(final long l) {
		return showDottedDouble(l, 0);
	}

	/**
	 * Human-readable double formatting, with groups of 3 digits separated by comma
	 * 
	 * @param d
	 * @return formatted double
	 */
	public static final String showDottedDouble(final double d) {
		return showDottedDouble(d, 0);
	}

	/**
	 * Human-readable long formatting, with groups of 3 digits separated by comma and a given number of decimal places
	 * 
	 * @param d
	 * @param dotplaces
	 * @return formatted double
	 */
	public static final String showDottedDouble(final double d, final int dotplaces) {
		return showDottedDouble(d, dotplaces, false);
	}

	/**
	 * Human-readable long formatting, with groups of 3 digits separated by comma, a given number of decimal places and the option
	 * to make the values even more human-readable.
	 * 
	 * @param dValue
	 * @param dots
	 * @param aproximated
	 * @return formatted double
	 */
	public static final String showDottedDouble(final double dValue, final int dots, final boolean aproximated) {

		double d = dValue;
		int dotplaces = dots;

		String append = "";
		if (aproximated)
			if (Math.abs(d) > 1000000000) {
				d /= 1000000000.0;
				dotplaces = (dotplaces == 0) ? 2 : dotplaces;
				append = " bilion";
			}
			else
				if (Math.abs(d) > 1000000) {
					d /= 1000000.0;
					dotplaces = (dotplaces == 0) ? 2 : dotplaces;
					append = " milion";
				}

		long l = (long) d;
		double f = Math.abs(d) - Math.abs(l);

		String sRez = "";

		if (l == 0)
			sRez = "0";

		while (Math.abs(l) > 0) {
			if (sRez.length() > 0) {
				int i = sRez.indexOf(",");

				if (i < 0)
					i = sRez.length();

				if (i == 1)
					sRez = "00" + sRez;

				if (i == 2)
					sRez = "0" + sRez;

				sRez = "," + sRez;
			}

			sRez = (Math.abs(l) % 1000) + sRez;
			l = l / 1000;
		}

		if (dotplaces > 0) {
			String sTemp = "";

			while (dotplaces > 0) {
				f *= 10;
				sTemp += ((long) f) % 10;
				dotplaces--;
			}

			if ((long) f > 0) {
				while (sTemp.endsWith("0"))
					sTemp = sTemp.substring(0, sTemp.length() - 1);

				if (sTemp.length() > 0)
					sRez += "." + sTemp;
			}
		}

		return (d < 0) ? "-" + sRez + append : sRez + append;
	}

	/**
	 * Shortcut for pattern matching
	 * 
	 * @param s
	 * @param sRegexp
	 * @return true if the string matches the regexp
	 */
	public final static boolean matches(final String s, final String sRegexp) {
		final Pattern p = Pattern.compile(sRegexp);
		final Matcher m = p.matcher(s);
		return m.find();
	}

	/**
	 * Get the properties value for the given key
	 * 
	 * @param prop
	 * @param sKey
	 * @return value
	 */
	public static final String pgets(final Properties prop, final String sKey) {
		return pgets(prop, sKey, "");
	}

	/**
	 * Get the properties value for the given key
	 * 
	 * @param prop
	 * @param sKey
	 * @param sDefault
	 * @return value set in properties or the given default if the key is not set
	 */
	public static final String pgets(final Properties prop, final String sKey, final String sDefault) {
		return pgets(prop, sKey, sDefault, true);
	}

	private static final ExpirationCache<String, ArrayList<String>> queryCache = new ExpirationCache<String, ArrayList<String>>();

	private static final ArrayList<String> getCachedQueryResult(final String sQuery, final boolean bUseQueryCache) {
		if (bUseQueryCache) {
			final ArrayList<String> cached = queryCache.get(sQuery);

			if (cached != null)
				return cached;
		}

		final ArrayList<String> alValues = new ArrayList<String>();

		if (!sQuery.toLowerCase().startsWith("select ")) {
			logger.log(Level.WARNING, "Somebody tried to execute an illegal query: " + sQuery);

			return alValues;
		}

		final DB db = new DB();

		db.setReadOnly(true);

		db.query(sQuery);

		if (logger.isLoggable(Level.FINEST))
			logger.log(Level.FINEST, sQuery);

		while (db.moveNext())
			alValues.add(db.gets(1));

		if (bUseQueryCache)
			queryCache.put(sQuery, alValues, 1000 * 30);

		return alValues;
	}

	/**
	 * Get the number of elements in the query cache
	 * 
	 * @return number of elements
	 */
	public static final int getQueryCacheSize() {
		return queryCache.size();
	}

	/**
	 * Evaluate the given key
	 * 
	 * @param prop
	 * @param sKey
	 * @param sVal
	 * @param sDefault
	 * @param bProcessQueries
	 * @return evaluated value
	 */
	public static final String parseOption(final Properties prop, final String sKey, final String sVal, final String sDefault, final boolean bProcessQueries) {
		return parseOption(prop, sKey, sVal, sDefault, bProcessQueries, true);
	}

	/**
	 * Evaluate the given key
	 * 
	 * @param prop
	 * @param sKey
	 * @param sValDefault
	 * @param sDefault
	 * @param bProcessQueries
	 * @param bUseQueryCache
	 * @return evaluated key
	 */
	public static final String parseOption(final Properties prop, final String sKey, final String sValDefault, final String sDefault, final boolean bProcessQueries, final boolean bUseQueryCache) {
		int i = 0;

		// System.err.println("---------");
		// System.err.println("At the begining there was: sKey='"+sKey+"', sVal='"+sVal+"'");

		String sVal = sValDefault;

		StringBuilder sbVal = new StringBuilder();
		String sValSuffix = "";

		while ((i = sVal.indexOf("${")) >= 0) {
			final int i2 = sVal.indexOf("}", i);

			if (i2 > 0) {
				final String s = sVal.substring(i + 2, i2);

				if (s.equals(sKey))
					return sDefault;

				sbVal.append(sVal.substring(0, i));
				sbVal.append(pgets(prop, s));

				sVal = sVal.substring(i2 + 1);
			}
			else
				break;
		}

		if (sbVal.length() > 0) {
			// some processing occured here
			if (sVal.length() > 0)
				sbVal.append(sVal);

			sVal = sbVal.toString();
			sbVal = new StringBuilder();
		}

		// System.err.println("After the {} processing : sVal='"+sVal+"'");

		int q;

		while (bProcessQueries && ((q = sVal.indexOf("$Q")) >= 0)) {
			if (q > 0) {
				String sValPrefix = sVal.substring(0, q);
				if (sValPrefix.endsWith(","))
					sValPrefix = sValPrefix.substring(0, sValPrefix.length() - 1);

				if (sbVal.length() > 0)
					sbVal.append(',');

				sbVal.append(sValPrefix);
			}

			int p = sVal.indexOf(";", q);

			if (p < 0)
				p = sVal.length();
			else
				p++;

			sValSuffix = sVal.substring(p);

			final String sQuery = sVal.substring(q + 2, p);

			final ArrayList<String> alValues = getCachedQueryResult(sQuery, bUseQueryCache);

			for (int j = 0; j < alValues.size(); j++) {
				if (j > 0)
					sbVal.append(',');

				sbVal.append(alValues.get(j));
			}

			sVal = sValSuffix;
		}

		if (sbVal.length() > 0) {
			// some processing occurred here
			if (sVal.length() > 0) {
				if (sbVal.length() > 0)
					sbVal.append(',');

				sbVal.append(sVal);
			}

			sVal = sbVal.toString();
			sbVal = new StringBuilder();
		}

		// System.err.println("After the $Q processing : sVal='"+sVal+"'");

		while (bProcessQueries && ((q = sVal.indexOf("$C")) >= 0)) {
			if (q > 0) {
				String sValPrefix = sVal.substring(0, q);
				if (sValPrefix.endsWith(","))
					sValPrefix = sValPrefix.substring(0, sValPrefix.length() - 1);

				if (sbVal.length() > 0)
					sbVal.append(",");

				sbVal.append(sValPrefix);
			}

			int p = sVal.indexOf(";", q);

			if (p < 0)
				p = sVal.length();
			else
				p++;

			sValSuffix = sVal.substring(p);

			sVal = sVal.substring(q + 2, p).trim();

			if (sVal.endsWith(";"))
				sVal = sVal.substring(0, sVal.length() - 1);

			final TreeMap<String, String> tm = new TreeMap<String, String>(); // it's a sorted map

			if (sVal.length() >= 2) {
				final char c = sVal.charAt(0);

				sVal = sVal.substring(1).trim();

				final monPredicate pred = toPred(sVal);

				final Vector<?> v = Cache.getLastValues(pred);

				String s = null;

				for (i = 0; i < v.size(); i++) {
					String sFarmName;
					String sClusterName;
					String sNodeName;
					String sParamName;
					String sValue;

					long lTime;

					final Object o = v.get(i);

					if (o instanceof Result) {
						final Result r = (Result) o;

						sFarmName = r.FarmName;
						sClusterName = r.ClusterName;
						sNodeName = r.NodeName;
						sParamName = r.param_name[0];
						sValue = "" + r.param[0];
						lTime = r.time;
					}
					else
						if (o instanceof eResult) {
							final eResult r = (eResult) o;

							sFarmName = r.FarmName;
							sClusterName = r.ClusterName;
							sNodeName = r.NodeName;
							sParamName = r.param_name[0];
							sValue = r.param[0].toString();
							lTime = r.time;
						}
						else
							if (o instanceof ExtResult) {
								final ExtResult r = (ExtResult) o;

								sFarmName = r.FarmName;
								sClusterName = r.ClusterName;
								sNodeName = r.NodeName;
								sParamName = r.param_name[0];

								sValue = "" + r.param[0];

								lTime = r.time;
							}
							else
								continue;

					switch (c) {
					case 'C':
						s = sClusterName;
						break;
					case 'N':
						s = sNodeName;
						break;
					case 'f':
						s = sParamName;
						break;
					case 'v':
						s = sValue;
						break;
					case 't':
						s = "" + lTime;
						break;
					case 'F':
					default:
						s = sFarmName;
					}

					tm.put(s, s);
				}
			}

			final Iterator<String> it = tm.keySet().iterator();
			while (it.hasNext()) {
				if (sbVal.length() > 0)
					sbVal.append(',');

				sbVal.append(it.next());
			}

			sVal = sValSuffix;
		}

		if (sbVal.length() > 0) {
			// some processing occured here
			if (sVal.length() > 0) {
				if (sbVal.length() > 0)
					sbVal.append(',');

				sbVal.append(sVal);
			}

			sVal = sbVal.toString();
		}

		// System.err.println("After the $C processing : sVal='"+sVal+"'");

		return sVal;
	}

	/**
	 * Get the properties value for the given key
	 * 
	 * @param prop
	 * @param sKey
	 * @param sDefault
	 * @param bProcessQueries
	 *            whether or not to evaluate the expressions
	 * @return the value
	 */
	public static final String pgets(final Properties prop, final String sKey, final String sDefault, final boolean bProcessQueries) {
		return pgets(prop, sKey, sDefault, bProcessQueries, true);
	}

	/**
	 * Get the properties value for the given key
	 * 
	 * @param prop
	 * @param sKey
	 * @param sDefault
	 * @param bProcessQueries
	 * @param bUseQueryCache
	 * @return value
	 */
	public static final String pgets(final Properties prop, final String sKey, final String sDefault, final boolean bProcessQueries, final boolean bUseQueryCache) {
		if ((sKey == null) || (prop == null))
			return sDefault;

		final String sPropValue = prop.getProperty(sKey);

		if (sPropValue != null) {
			final String sVal = sPropValue.trim();

			final String sTemp = parseOption(prop, sKey, sVal, sDefault, bProcessQueries, bUseQueryCache);

			// if (sKey.equals("pivot0_3"))
			// System.err.println("sKey="+sKey+", sVal="+sVal+", sTemp="+sTemp+", bProcessQueries="+bProcessQueries);

			// cache the compiled value
			// if (sVal!=null && sTemp!=null && !sVal.equals(sTemp))
			// prop.setProperty(sKey, sTemp);

			return sTemp;
		}

		return sDefault;
	}

	/**
	 * Get the properties value as boolean
	 * 
	 * @param prop
	 * @param sKey
	 * @param bDefault
	 * @return the value, either the one set or the default
	 */
	public static final boolean pgetb(final Properties prop, final String sKey, final boolean bDefault) {
		final String s = pgets(prop, sKey);

		if (s.length() > 0) {
			final char c = s.charAt(0);

			if ((c == 't') || (c == 'T') || (c == 'y') || (c == 'Y') || (c == '1'))
				return true;

			if ((c == 'f') || (c == 'F') || (c == 'n') || (c == 'N') || (c == '0'))
				return false;
		}

		return bDefault;
	}

	/**
	 * Get the properties value as int
	 * 
	 * @param prop
	 * @param sKey
	 * @param iDefault
	 * @return value
	 */
	public static final int pgeti(final Properties prop, final String sKey, final int iDefault) {
		try {
			return Integer.parseInt(pgets(prop, sKey));
		} catch (final Exception e) {
			return iDefault;
		}
	}

	/**
	 * Get the properties value as long
	 * 
	 * @param prop
	 * @param sKey
	 * @param lDefault
	 * @return value
	 */
	public static final long pgetl(final Properties prop, final String sKey, final long lDefault) {
		try {
			return Long.parseLong(pgets(prop, sKey));
		} catch (final Exception e) {
			return lDefault;
		}
	}

	/**
	 * Get the properties value as double
	 * 
	 * @param prop
	 * @param sKey
	 * @param dDefault
	 * @return value
	 */
	public static final double pgetd(final Properties prop, final String sKey, final double dDefault) {
		try {
			return Double.parseDouble(pgets(prop, sKey));
		} catch (final Exception e) {
			return dDefault;
		}
	}

	private static final TimeZone defaultTimeZone = TimeZone.getTimeZone("GMT");

	/**
	 * Get the configured timezone
	 * 
	 * @param prop
	 * @return time zone
	 */
	public static final TimeZone getTimeZone(final Properties prop) {
		if (pgets(prop, "timezone", "GMT").equals("local") || pgets(prop, "timezone", "GMT").equals("default"))
			return TimeZone.getDefault();

		try {
			return TimeZone.getTimeZone(pgets(prop, "timezone", "GMT"));
		} catch (final Exception e) {
			System.err.println("Exception converting timezone '" + pgets(prop, "timezone", "GMT") + " : " + e + "(" + e.getMessage() + ")");
			return defaultTimeZone;
		}
	}

	/**
	 * Get the list of values for a given key
	 * 
	 * @param prop
	 * @param sProp1
	 * @param sProp2
	 * @return values
	 */
	public static final Vector<String> toVector(final Properties prop, final String sProp1, final String sProp2) {
		return toVector(prop, sProp1, sProp2, true);
	}

	/**
	 * Get the list of values for a given key
	 * 
	 * @param prop
	 * @param sProp1
	 * @param sProp2
	 * @param bProcessQueries
	 * @return values
	 */
	public static final Vector<String> toVector(final Properties prop, final String sProp1, final String sProp2, final boolean bProcessQueries) {
		return toVector(prop, sProp1, sProp2, bProcessQueries, true);
	}

	/**
	 * Get the list of values for a given key
	 * 
	 * @param prop
	 * @param sProp1
	 * @param sProp2
	 * @param bProcessQueries
	 * @param bUseQueryCache
	 * @return values
	 */
	public static final Vector<String> toVector(final Properties prop, final String sProp1, final String sProp2, final boolean bProcessQueries, final boolean bUseQueryCache) {
		final String st = pgets(prop, sProp1, pgets(prop, sProp2, "", bProcessQueries, bUseQueryCache), bProcessQueries, bUseQueryCache);

		return toVector(st);
	}

	/**
	 * Convert the value into a list of strings
	 * 
	 * @param sVal
	 * @return list
	 */
	public static final Vector<String> toVector(final String sVal) {
		final Vector<String> v = new Vector<String>();

		if (sVal == null)
			return v;

		int i;

		String s = sVal;

		while ((i = s.indexOf(",")) >= 0) {
			boolean bWasQ = false;

			if (s.trim().startsWith("$Q")) {
				i = s.indexOf(";");

				if (i < 0) {
					v.add(s.trim());
					s = null;
					break;
				}

				bWasQ = true;
			}

			v.add(s.substring(0, i).trim());
			s = s.substring(i + 1).trim();

			if (bWasQ && s.startsWith(","))
				s = s.substring(1);

			if (s.length() <= 0) {
				s = null;
				break;
			}
		}

		if ((s != null) && (s.length() > 0))
			v.add(s);

		return v;
	}

	/**
	 * debugging method
	 * 
	 * @param args
	 */
	public static void main(final String args[]) {
		System.err.println(toVector("1,2,3,$Qselect 1,2,3;,4,5,6,12345,$Qanother select(a,b,c)"));
	}

	/**
	 * Convert a string into a ML predicate
	 * 
	 * @param s
	 * @return the predicate
	 */
	public static final monPredicate toPred(final String s) {
		return Formatare.toPred(s);
	}

	/**
	 * Convert a simple time formatting into an interval in milliseconds
	 * 
	 * @param s
	 * @return interval
	 */
	public static final long strToTime(final String s) {
		long lRez = 0;

		int i = 0;

		for (; i < s.length(); i++) {
			final char c = s.charAt(i);

			if ((c >= '0') && (c <= '9'))
				lRez = (lRez * 10) + (c - '0');
			else
				break;
		}

		char cMod = 'm';

		for (; i < s.length(); i++) {
			final char c = s.charAt(i);

			if ((c == 's') || (c == 'm') || (c == 'h') || (c == 'd') || (c == 'M') || (c == 'Y'))
				cMod = c;
		}

		switch (cMod) {
		case 'Y':
			lRez *= 365L * 24 * 60 * 60 * 1000;
			break;
		case 'M':
			lRez *= 30L * 24 * 60 * 60 * 1000;
			break;
		case 'd':
			lRez *= 24L * 60 * 60 * 1000;
			break;
		case 'h':
			lRez *= 60 * 60 * 1000;
			break;
		case 'm':
			lRez *= 60 * 1000;
			break;
		case 's':
			lRez *= 1000;
			break;
		}

		return lRez;
	}

	/**
	 * Shortcut to replace all occurrences of key in the string with the given new value
	 * 
	 * @param s
	 * @param key
	 * @param val
	 * @return new string
	 */
	public static final String replace(final String s, final String key, final String val) {
		return Formatare.replace(s, key, val);
	}

	/**
	 * Set all the tags that show up in the template with the corresponding values in the dictionary
	 * 
	 * @param p
	 * @param prop
	 */
	public static final void fillPageFromProperties(final Page p, final Properties prop) {
		final Iterator<String> it = p.getTagsSet().iterator();

		while (it.hasNext()) {
			final String sKey = it.next();

			if (prop.containsKey(sKey) && pgetb(prop, sKey + ".visible", true))
				p.modify(sKey, pgets(prop, sKey));
		}
	}

	/**
	 * Get the last part of the string split by the delimiters
	 * 
	 * @param s
	 * @param sDelimiters
	 * @return last part
	 */
	public static final String getStringSuffix(final String s, final String sDelimiters) {
		if (s == null)
			return null;

		String sRet = "";

		final StringTokenizer st = new StringTokenizer(s, sDelimiters);

		while (st.hasMoreTokens())
			sRet = st.nextToken();

		return sRet;
	}

	/**
	 * Get the first part of the string up to the first delimiter
	 * 
	 * @param s
	 * @param sDelimiters
	 * @return first part
	 */
	public static final String getStringPrefix(final String s, final String sDelimiters) {
		if (s == null)
			return null;

		final StringTokenizer st = new StringTokenizer(s, sDelimiters);

		if (st.hasMoreTokens())
			return st.nextToken();

		return "";
	}

	/**
	 * Convert a hex (preffixed with 0x or not) to integer
	 * 
	 * @param sVal
	 * @return int value
	 */
	public final static int fromHex(final String sVal) {
		String s = sVal.trim().toLowerCase();

		if (s.startsWith("0x"))
			s = s.substring(2);

		int iNumber = 0;

		for (int i = 0; i < s.length(); i++) {
			final char c = s.charAt(i);

			if ((c >= '0') && (c <= '9'))
				iNumber = (iNumber * 16) + (c - '0');
			else
				if ((c >= 'a') && (c <= 'f'))
					iNumber = (iNumber * 16) + ((c - 'a') + 10);
				else
					break;
		}

		return iNumber;
	}

	/**
	 * Get the color for a given series as set in the properties
	 * 
	 * @param prop
	 * @param sParam
	 * @param cDefault
	 * @return the color
	 */
	public static final java.awt.Color getColor(final Properties prop, final String sParam, final java.awt.Color cDefault) {
		return getColor(pgets(prop, sParam), cDefault);
	}

	/**
	 * convert a string representation of a color in an int[]
	 * 
	 * @param sColorDef
	 * @return the color
	 */
	public static final int[] getRGB(final String sColorDef) {
		if (sColorDef == null)
			return null;

		String sColor = sColorDef;

		if (sColor.startsWith("#"))
			sColor = sColor.substring(1);

		sColor = sColor.trim();

		if (sColor.length() < 5)
			return null;

		int red = 0;
		int green = 0;
		int blue = 0;

		if (sColor.matches("[0-9a-fA-F]{6}")) {
			final int iColor = fromHex(sColor);

			blue = iColor % 256;
			green = (iColor >> 8) % 256;
			red = (iColor >> 16) % 256;
		}
		else
			if (sColor.indexOf(" ") > 0) {
				final StringTokenizer st = new StringTokenizer(sColor);

				try {
					red = Integer.parseInt(st.nextToken());
					green = Integer.parseInt(st.nextToken());
					blue = Integer.parseInt(st.nextToken());

					if (red > 255)
						red = 255;
					if (red < 0)
						red = 0;

					if (green > 255)
						green = 255;
					if (green < 0)
						green = 0;

					if (blue > 255)
						blue = 255;
					if (blue < 0)
						blue = 0;
				} catch (final Exception e) {
					return null;
				}
			}
			else
				return null;

		return new int[] { red, green, blue };
	}

	/**
	 * Convert a string into a color
	 * 
	 * @param sColor
	 * @param cDefault
	 * @return the color
	 */
	public static final java.awt.Color getColor(final String sColor, final java.awt.Color cDefault) {
		final int[] rgb = getRGB(sColor);

		if (rgb != null)
			return ColorFactory.getColor(rgb[0], rgb[1], rgb[2]);

		return cDefault;
	}

	/**
	 * Get the first name from the certificate chain
	 * 
	 * @param cert
	 * @return the first name of the user, or null
	 */
	public static final String getUser(final X509Certificate[] cert) {
		String sUser = null;

		if ((cert != null) && (cert.length > 0)) {
			final StringTokenizer st = new StringTokenizer(cert[0].getSubjectDN().getName(), " ,=");

			if (st.countTokens() >= 2) {
				st.nextToken();
				sUser = st.nextToken();
			}
		}

		return sUser;
	}
}
