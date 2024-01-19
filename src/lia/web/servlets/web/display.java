package lia.web.servlets.web;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYPointerAnnotation;
import org.jfree.chart.axis.Axis;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryAxis3D;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberAxis3D;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.block.BlockContainer;
import org.jfree.chart.block.BorderArrangement;
import org.jfree.chart.block.EmptyBlock;
import org.jfree.chart.block.LengthConstraintType;
import org.jfree.chart.block.RectangleConstraint;
import org.jfree.chart.entity.StandardEntityCollection;
import org.jfree.chart.labels.StandardCategoryToolTipGenerator;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.labels.StandardPieToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.CombinedDomainCategoryPlot;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.CombinedRangeCategoryPlot;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.DefaultDrawingSupplier;
import org.jfree.chart.plot.DrawingSupplier;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PiePlot3D;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.SeriesRenderingOrder;
import org.jfree.chart.plot.SpiderWebPlot;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.BarRenderer3D;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.renderer.category.LineRenderer3D;
import org.jfree.chart.renderer.category.StackedBarRenderer;
import org.jfree.chart.renderer.category.StackedBarRenderer3D;
import org.jfree.chart.renderer.xy.AbstractXYItemRenderer;
import org.jfree.chart.renderer.xy.StackedXYAreaRenderer;
import org.jfree.chart.renderer.xy.StackedXYAreaRenderer2;
import org.jfree.chart.renderer.xy.StackedXYBarRenderer;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.chart.renderer.xy.XYAreaRenderer2;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYSplineRenderer;
import org.jfree.chart.servlet.ServletUtilities;
import org.jfree.chart.title.CompositeTitle;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.urls.StandardCategoryURLGenerator;
import org.jfree.data.Range;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.CombinedDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Minute;
import org.jfree.data.time.Month;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.TimeTableXYDataset;
import org.jfree.data.time.Week;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.Align;
import org.jfree.ui.Layer;
import org.jfree.ui.LengthAdjustmentType;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.Size2D;
import org.jfree.ui.TextAnchor;

import lazyj.Format;
import lazyj.RequestWrapper;
import lia.Monitor.Store.Cache;
import lia.Monitor.Store.DataSplitter;
import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.TransparentStoreFast;
import lia.Monitor.Store.Fast.DB;
import lia.Monitor.Store.Fast.IDGenerator;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ExtendedResult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.TimestampedResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.ntp.NTPDate;
import lia.web.bean.Generic.ConfigBean;
import lia.web.bean.Generic.WriterConfig;
import lia.web.utils.Annotation;
import lia.web.utils.CacheServlet;
import lia.web.utils.ColorFactory;
import lia.web.utils.DoubleFormat;
import lia.web.utils.Formatare;
import lia.web.utils.Page;
import lia.web.utils.ServletExtension;

/**
 * @author costing
 * @since forever
 */
@SuppressWarnings("deprecation")
public class display extends CacheServlet {

	private static final long serialVersionUID = 1L;

	/**
	 * Logging facility
	 */
	private static final Logger logger = Logger.getLogger(Rest.class.getCanonicalName());

	/**
	 * Repository version
	 */
	public static final String sRepositoryVersion = "1.4.6";

	/**
	 * Date of last significant change
	 */
	public static final String sRepositoryDate = "2024.01.19";

	/**
	 * Wrapper HTML for the actual content (header, menu ...)
	 */
	private transient Page pMaster = null;

	private transient ByteArrayOutputStream baos = null;

	/**
	 * The colors sequence will be built based on the page configuration
	 */
	private transient Paint[] default_paint_sequence = null;

	/**
	 * Memory store
	 */
	private transient TransparentStoreFast store = null;

	/**
	 * Get a human readable representation of how much time has passed since the repository was started.
	 *
	 * @return nice uptime representation
	 */
	public static final String getUptime() {
		return Formatare.showInterval(NTPDate.currentTimeMillis() - lRepositoryStarted);
	}

	/**
	 * The time a GET request is cached
	 *
	 * @return caching time for each page
	 */
	@Override
	public long getCacheTimeout() {
		// express request for a time period

		if ((gets("image").length() > 0) || (gets("img").length() > 0) || (gets("get_statistics").length() > 0) || (gets("statistics").length() > 0) || (gets("get_series").length() > 0)) {
			if (logTiming())
				logTiming("display.getCacheTimeout(): returns 0 because it's an image or smth");

			return 0;
		}

		if (lazyj.Utils.stringToBool(gets("download_data_csv"), false)) {
			if (logTiming())
				logTiming("display.getCacheTimeout(): returns 0 because the data dump in csv format was requested");

			return 0;
		}

		final String range = request.getHeader("Range");

		if ((range != null) && (range.length() > 0)) {
			if (logTiming())
				logTiming("display.getCacheTimeout(): returns 0 because a byte range was requested");

			return 0;
		}

		if (geti("cache_timeout", -1) >= 0) {
			if (logTiming())
				logTiming("display.getCacheTimeout(): returns " + geti("cache_timeout") + " because it was forced by cache_timeout param");

			return geti("cache_timeout");
		}

		if (gets("page").length() > 0)
			try {
				final Properties prop = Utils.getProperties(sConfDir, gets("page"), null, true);

				final String kind = pgets(prop, "page", "rt").trim().toLowerCase();

				if (kind.equals("hist") || kind.equals("combined_hist") || kind.equals("image") || kind.equals("farm_info")) {
					final HttpSession sess = request.getSession(false);

					long lHistIntervalMin = pgeti(prop, "interval.min", 3600000);
					try {
						lHistIntervalMin = Long.parseLong((String) sess.getAttribute("interval.min"));
					}
					catch (@SuppressWarnings("unused") final Exception e) {
						// ignore parse exception, keep previous value
					}

					lHistIntervalMin = getl("interval.min", lHistIntervalMin);

					long lHistIntervalMax = pgeti(prop, "interval.max", 0);
					try {
						lHistIntervalMax = Long.parseLong((String) sess.getAttribute("interval.max"));
					}
					catch (@SuppressWarnings("unused") final Exception e) {
						// ignore parse exception, keep previous value
					}
					lHistIntervalMax = getl("interval.max", lHistIntervalMax);

					long diff = (lHistIntervalMin > lHistIntervalMax ? lHistIntervalMin - lHistIntervalMax : lHistIntervalMax - lHistIntervalMin) / 1000L;

					final long lOldDiff = diff;

					long lCompactPoints = pgeti(prop, "compact.displaypoints", 90);

					if (pgetb(prop, "areachart", false)) {
						final long lTemp = pgeti(prop, "compact.displaypoints.areachart", 300);

						if (lTemp > 0)
							lCompactPoints = lTemp;
					}

					diff /= lCompactPoints;

					if ((diff * 1000L) < pgetl(prop, "compact.min_interval", 60000))
						diff = pgetl(prop, "compact.min_interval", 60000) / 1000L;

					diff = Math.max(diff, AppConfig.geti("display.minCacheTime", 30));
					diff = Math.min(diff, AppConfig.geti("display.maxCacheTime", 300));
					
					if (logTiming())
						logTiming("display.getCacheTimeout() : Returning " + diff + " (" + lOldDiff + ") because " + lHistIntervalMax + "-" + lHistIntervalMin + ", "
								+ pgeti(prop, "compact.displaypoints", 90));

					return diff;
				}
			}
			catch (@SuppressWarnings("unused") final RuntimeException re) {
				// ignore
			}
			catch (@SuppressWarnings("unused") final Exception e) {
				// ignore everything
			}

		// probably real time charts, 45 seconds are enough
		if (logTiming())
			logTiming("display.getCacheTimeout() : returning default value, 45");

		return 45;
	}

	/**
	 * Allow caching of POST requests. By default the framework would not cache POST requests.
	 *
	 * @return true always
	 */
	@Override
	protected boolean allowPOSTCaching() {
		return true;
	}

	/**
	 * For the GET request add this string for the cache elements
	 */
	private static final String[] vsSessionAttribs = { "pTime", "sum", "int", "err", "log", "interval.min", "interval.max" };

	/**
	 * Add all significant modifiers to the cache key
	 *
	 * @return a suffix for the cache key that has all the modifiers needed to correctly identify this entry
	 */
	@Override
	public String getCacheKeyModifier() {
		// if this is a POST request then all the parameters are in the request
		if (bGet == false)
			return "";

		final HttpSession sess = request.getSession(false);

		final StringBuilder sbReturn = new StringBuilder();

		if (sess != null)
			for (final String vsSessionAttrib : vsSessionAttribs)
				if ((sess.getAttribute(vsSessionAttrib) != null) && (gets(vsSessionAttrib).length() <= 0))
					sbReturn.append(vsSessionAttrib).append('=').append(sess.getAttribute(vsSessionAttrib)).append('&');

		final HashMap<String, String> hmCookies = setCookieParameters(request, null);

		for (final Map.Entry<String, String> me : hmCookies.entrySet()) {
			final String sName = me.getKey();

			if (request.getParameterValues(sName) == null)
				sbReturn.append(sName).append('=').append(me.getValue()).append('&');
		}

		return sbReturn.toString();
	}

	private static final class DisplayStructure {
		/**
		 * series names
		 */
		String sSeries;

		/**
		 * statistics table to display below the chat
		 */
		String sStatistics;

		/**
		 * name of the chart file
		 */
		String sImage;

		/**
		 * expiry time
		 */
		long lTime;

		/**
		 * the only constructor
		 *
		 * @param _sImage
		 *            file name
		 * @param _sStatistics
		 *            statistics table
		 * @param _sSeries
		 *            series names
		 * @param lCacheTimeout
		 *            for how long (in seconds) will this content be cached
		 */
		public DisplayStructure(final String _sImage, final String _sStatistics, final String _sSeries, final long lCacheTimeout) {
			lTime = System.currentTimeMillis() + (lCacheTimeout * 1000);
			synchronized (llTempStructures) {
				llTempStructures.add(this);
			}

			sImage = _sImage;
			sStatistics = _sStatistics;
			sSeries = _sSeries;
		}

		/**
		 * remove this entry, including the file on disk
		 */
		public void dispose() {
			if (sImage == null)
				return;

			final String sFile = System.getProperty("java.io.tmpdir") + "/" + sImage;

			try {
				new File(sFile).delete();
			}
			catch (final Exception e) {
				System.err.println("Error deleting: '" + sFile + "': " + e + " (" + e.getMessage() + ")");
			}
			// should delete the image from disk

			sImage = null;
			sStatistics = null;
			sSeries = null;
		}

		/**
		 * make sure the files are deleted when this object is destroyed by the GC
		 *
		 * @throws Throwable
		 *             ignore this
		 */
		@Override
		protected void finalize() throws Throwable {
			dispose();
		}
	}

	/**
	 * Method to register one file to be deleted after a given amount of time.
	 *
	 * @param sFileName
	 *            path to the file to be deleted
	 * @param lSecondsToLive
	 *            delete this file after this much time, in seconds
	 */
	public static void registerImageForDeletion(final String sFileName, final long lSecondsToLive) {
		registerImageForDeletion(sFileName, "", "", lSecondsToLive);
	}

	/**
	 * Method to register one file to be deleted after a given amount of time.
	 *
	 * @param sFileName
	 *            path to the file to be deleted
	 * @param sStatistics
	 * @param sSeries
	 * @param lSecondsToLive
	 *            delete this file after this much time, in seconds
	 */
	@SuppressWarnings("unused")
	public static void registerImageForDeletion(final String sFileName, final String sStatistics, final String sSeries, final long lSecondsToLive) {
		new DisplayStructure(sFileName, sStatistics, sSeries, lSecondsToLive);
	}

	/**
	 * Images that are to be deleted later
	 */
	static final LinkedList<DisplayStructure> llTempStructures = new LinkedList<>();

	/**
	 * Iterate through the cookies and override the properties with the values of the special cookies.
	 * If the Properties file is defined then for each cookie that is seen the method looks at the
	 * value of NAME.cookie.ignore, and if this parameter is true in the Properties then the
	 * cookie value is ignored.
	 *
	 * @param _request
	 *            the http request containing the cookies
	 * @param prop
	 *            Properties object to override the values is (can be null)
	 * @return the hash with the values that were actually set
	 */
	private static HashMap<String, String> setCookieParameters(final HttpServletRequest _request, final Properties prop) {
		final Cookie[] cookies = _request.getCookies();

		final HashMap<String, String> hmCookies = new HashMap<>();

		for (int i = 0; (cookies != null) && (i < cookies.length); i++) {
			final Cookie c = cookies[i];

			final String s = c.getName();
			final String v = c.getValue();

			if ((s != null) && s.startsWith("prop_") && (v != null)) {
				final String sProp = s.substring(5);

				boolean bSet = true;

				if (prop != null) {
					bSet = !pgetb(prop, sProp + ".cookie.ignore", false);

					if (bSet)
						prop.setProperty(sProp, v);
				}

				if (bSet)
					hmCookies.put(sProp, v);
			}
		}

		return hmCookies;
	}

	/**
	 * Thread that goes through the cached images and deletes the expired ones
	 *
	 * @author costing
	 * @since May 7, 2008
	 */
	static class CleanupThread extends Thread {
		public CleanupThread() {
			setName("lia.web.servlets.web.display.CleanupThread");
			setDaemon(true);
		}

		/**
		 * Periodically iterate through the registered cache structures and remove the ones that have expired
		 */
		@Override
		public void run() {
			while (true) {
				synchronized (llTempStructures) {
					final long lTime = System.currentTimeMillis() - (1000L * 60L * 1L); // 1 min grace

					DisplayStructure ds;

					while ((llTempStructures.size() > 0) && ((llTempStructures.getFirst()).lTime < lTime)) {
						ds = llTempStructures.getFirst();
						ds.dispose();
						llTempStructures.removeFirst();
					}

					/*
					 * final Iterator it = llTempStructures.iterator();
					 *
					 * while (it.hasNext()){
					 * ds = (DisplayStructure) it.next();
					 *
					 * if (ds.references>=3){
					 * ds.dispose();
					 * it.remove();
					 * }
					 * }
					 */
				}

				try {
					Thread.sleep(1000 * 30);
				}
				catch (@SuppressWarnings("unused") final InterruptedException e) {
					return;
				}
			}
		}
	}

	static {
		(new CleanupThread()).start();
	}

	/**
	 * Initialize the basic variables.
	 */
	@Override
	public final void doInit() {
		baos = new ByteArrayOutputStream();

		pMaster = new Page(baos, sResDir + "masterpage/masterpage.res");
	}

	/**
	 * Series have some data
	 */
	HashMap<String, String> hmActualSeries = null;

	/**
	 * All unique series named
	 */
	Vector<String> vTotalSeries = null;

	/**
	 * The names in each subchart, in case the chart is a combined one
	 */
	Vector<Vector<String>> vIndividualSeries = null;

	/**
	 * Initialize the common parts of the master page
	 *
	 * @param pMaster
	 *            instance of a master page (global template)
	 * @param prop
	 *            the configuration for the currently displayed chart
	 * @param sResDir
	 *            path to the folder that holds the html templates
	 */
	public static final void initMasterPage(final Page pMaster, final Properties prop, final String sResDir) {
		final Vector<String> vAlternate = toVector(prop, "alternate.pages", null);
		final Vector<String> vAlternateDescr = toVector(prop, "alternate.descr", null);
		final Vector<String> vAlternateExplain = toVector(prop, "alternate.explain", null);

		boolean bFirst = true;

		final Page pAlternate = new Page(sResDir + "masterpage/alternate.res");
		for (int i = 0; i < vAlternate.size(); i++) {
			final String sAlternate = vAlternate.get(i);
			final String sDescr = (vAlternateDescr != null) && (vAlternateDescr.size() > i) ? (String) vAlternateDescr.get(i) : sAlternate;
			final String sExplain = (vAlternateExplain != null) && (vAlternateExplain.size() > i) ? vAlternateExplain.get(i).trim() : "";

			if (sAlternate.equals("SEPARATOR")) {
				pMaster.append("alternates", new Page(sResDir + "masterpage/alt_sep.res"));
				bFirst = true;
			}
			else
				if (sAlternate.equals("DESCRIPTION")) {
					pMaster.append("alternates", sDescr);
					bFirst = true;
				}
				else {
					pAlternate.modify("page", sAlternate);
					pAlternate.modify("pagehref", Format.replace(sAlternate, "CLEAR", ""));
					pAlternate.modify("descr", sDescr);
					pAlternate.modify("explain", sExplain);

					pAlternate.comment("com_first", !bFirst);

					bFirst = false;

					pMaster.append("alternates", pAlternate);
				}
		}

		pMaster.comment("com_alternates", vAlternate.size() > 0);

		final int iRefreshTime = pgeti(prop, "refresh.time", 0);

		if (iRefreshTime > 0) {
			pMaster.modify("comment_refresh", "");
			pMaster.modify("refresh_time", "" + iRefreshTime);
		}
		else {
			pMaster.modify("comment_refresh", "//");
			pMaster.modify("refresh_time", "3600");
		}
	}

	/**
	 * Needed to serialize requests for area charts because JFreeChart
	 * is not thread-safe and the charts look ugly.
	 */
	private static final Object oHistoryLock = new Object();

	private TreeMap<Long, HashMap<String, Double>> tmDownloadData = null;
	private TreeSet<String> tsDownloadSeries = null;

	private String sTitle = "";

	/**
	 * The dispatcher. Check what type of chart was requested and call the proper function to build the chart.
	 */
	@Override
	public final void execGet() {
		lIntervalMax = lIntervalMin = 0;
		lChartsMaxTime = lChartsMinTime = 0;
		sTitle = "";

		if (gets("statistics").length() > 0) {
			showStatistics(gets("statistics"));
			bAuthOK = true;
			return;
		}

		final String sImage = gets("image");

		if (sImage.length() > 0) {
			try {
				if (sImage.contains("..") || (!sImage.endsWith(".png") && !sImage.endsWith(".jpg")) || sImage.indexOf('\0') >= 0) {
					System.err.println("display: Illegal image file name: " + sImage);
					Utils.logRequest("display?image=" + sImage, -1, request);
					return;
				}

				// the image urls are unique, can be cached for a long time on the client
				RequestWrapper.setCacheTimeout(response, 60 * 60 * 24 * 7);

				ServletUtilities.sendTempFile(request.getParameter("image"), response);
			}
			catch (final Exception e) {
				System.out.println("Error showing image : " + e + " (" + e.getMessage() + ")");
			}

			bAuthOK = true;
			return;
		}

		if ((gets("get_statistics").length() > 0) || (gets("get_series").length() > 0)) {
			final boolean bStatistics = gets("get_statistics").length() > 0;

			final String sKey = gets(bStatistics ? "get_statistics" : "get_series");

			DisplayStructure ds = null;

			synchronized (llTempStructures) {
				final Iterator<DisplayStructure> it = llTempStructures.iterator();

				while (it.hasNext()) {
					ds = it.next();

					if (sKey.equals(ds.sImage))
						break;

					ds = null;
				}
			}

			if (ds != null)
				try {
					response.setContentType("text/html");
					final byte[] content = (bStatistics ? ds.sStatistics : ds.sSeries).getBytes();
					response.setContentLength(content.length);
					@SuppressWarnings("resource")
					final OutputStream os = response.getOutputStream();
					os.write(content);
					os.flush();
				}
				catch (final Exception e) {
					System.err.println("Cannot write the temporary strings on the output stream : " + e + " (" + e.getMessage() + ")");
				}

			bAuthOK = true;
			return;
		}

		if (gets("page").length() <= 0) {
			redirect("/");
			bAuthOK = true;
			return;
		}

		response.setContentType("text/html");

		if (store == null)
			try {
				store = (TransparentStoreFast) TransparentStoreFactory.getStore();
			}
			catch (final Exception e) {
				System.out.println("Error building store : " + e + " (" + e.getMessage() + ")");
				bAuthOK = true;
				return;
			}

		final Page p = new Page(sResDir + "display/display.res");

		final Properties prop = Utils.getProperties(sConfDir, gets("page"), null, true);

		setLogTiming(prop);

		final boolean bIgnoreInterval = (getl("interval.min", 0) < 0) || (getl("interval.max") < 0);

		if (bIgnoreInterval) {
			prop.setProperty("interval.min.cookie.ignore", "true");
			prop.setProperty("interval.max.cookie.ignore", "true");
		}

		// special cookies can override file properties
		setCookieParameters(request, prop);

		// URL parameters override everything
		Enumeration<?> eParams = request.getParameterNames();
		while (eParams.hasMoreElements()) {
			final String sParameter = (String) eParams.nextElement();

			if (sParameter.equals("page"))
				continue;

			if (bIgnoreInterval && (sParameter.equals("interval.min") || sParameter.equals("interval.max")))
				continue;

			prop.setProperty(sParameter, gets(sParameter));
		}

		final Vector<String> vSetCookies = toVector(prop, "cookies", null);

		vSetCookies.add("err");
		vSetCookies.add("log");
		vSetCookies.add("sum");
		vSetCookies.add("imgsize");
		vSetCookies.add("interval.min");
		vSetCookies.add("interval.max");

		if (logTiming())
			logTiming("set cookie vector: " + vSetCookies);

		for (int i = 0; i < vSetCookies.size(); i++) {
			final String s = vSetCookies.get(i);
			final String val = pgets(prop, s);

			final boolean bOk = setCookie("prop_" + s, val, 60 * 60 * 24 * 365); // 1 year memory :)

			if (logTiming())
				logTiming("setCookie('" + s + "', '" + val + "') : " + bOk);
		}

		String sImgSize = pgets(prop, "imgsize", "1024x600");

		if ((sImgSize == null) || (sImgSize.indexOf("x") < 1))
			sImgSize = "1024x600";

		if (logTiming())
			logTiming("got image size = " + sImgSize);

		final String sX = sImgSize.substring(0, sImgSize.indexOf("x"));
		final String sY = sImgSize.substring(sImgSize.indexOf("x") + 1);

		try {
			Integer.parseInt(sX);
			Integer.parseInt(sY);

			if (pgetb(prop, "allow.set.width", true) && (gets("width").length() == 0))
				prop.setProperty("width", sX);

			if (pgetb(prop, "allow.set.height", true) && (gets("height").length() == 0))
				prop.setProperty("height", sY);
		}
		catch (final Exception e) {
			// it means that something is wrong with the size specification

			if (logTiming())
				logTiming("exception parsing image size '" + sImgSize + "' : " + e);
		}

		p.modify("resolutions", pgets(prop, "imgsize.options", "1280x700,1024x600,800x550"));
		p.modify("defaultres", sImgSize);

		// some redirects in case i'm not the one who should be executing the request
		if ((pgets(prop, "resolution").length() > 0) || (pgets(prop, "pagetitle").length() > 0) || pgets(prop, "page", "").equals("stats")) {
			eParams = request.getParameterNames();

			final StringBuilder sb = new StringBuilder();

			while (eParams.hasMoreElements()) {
				final String sParameter = (String) eParams.nextElement();

				final String[] vsValues = request.getParameterValues(sParameter);

				for (int i = 0; (vsValues != null) && (i < vsValues.length); i++)
					sb.append(encode(sParameter) + "=" + encode(vsValues[i]) + "&");
			}

			final String sServlet = (pgets(prop, "resolution").length() > 0) && !pgets(prop, "page", "").equals("stats") ? "genimage" : "stats";

			String s = sb.toString();
			if (s.endsWith("&"))
				s = s.substring(0, s.length() - 1);

			redirect(sServlet + "?" + s);
			bAuthOK = true;
			return;
		}

		final HashMap<String, String> hmSeries = new HashMap<>();

		final String[] vsSeries = request.getParameterValues("plot_series");

		if ((vsSeries != null) && (vsSeries.length > 0))
			for (final String vsSerie : vsSeries)
				hmSeries.put(vsSerie, "");

		default_paint_sequence = Utils.DEFAULT_PAINT_SEQUENCE;

		final String[] vsColors = Utils.getValues(prop, "colors");
		if ((vsColors != null) && (vsColors.length > 0)) {
			default_paint_sequence = new Paint[vsColors.length];

			for (int i = 0; i < vsColors.length; i++)
				default_paint_sequence[i] = getColor(vsColors[i], (Color) Utils.DEFAULT_PAINT_SEQUENCE[i % Utils.DEFAULT_PAINT_SEQUENCE.length]);
		}

		hmActualSeries = new HashMap<>();
		vTotalSeries = new Vector<>();
		vIndividualSeries = new Vector<>();

		final String kind = pgets(prop, "page", "rt").toLowerCase();

		final long lStart = System.currentTimeMillis();

		final boolean download = (gets("download_data_csv").length() > 0) || (gets("download_data_html").length() > 0);

		if (download) {
			tmDownloadData = new TreeMap<>();
			tsDownloadSeries = new TreeSet<>();
		}

		try {
			if (kind.startsWith("hist"))
				synchronized (oHistoryLock) {
					p.append(buildHistoryPage(prop, hmSeries));
				}
			else
				if (kind.equals("combined_bar"))
					p.append(buildCombinedBar(prop, hmSeries, request.getParameterValues("modules")));
				else
					if (kind.equals("combined_hist"))
						synchronized (oHistoryLock) {
							p.append(buildCombinedHistory(prop, hmSeries, request.getParameterValues("modules")));
						}
					else
						if (kind.equals("image")) {
							final String sFileName = buildImage(prop, hmSeries, null);
							ServletUtilities.sendTempFile(sFileName, response);
							bAuthOK = true;
							return;
						}
						else
							if (kind.equals("farminfo"))
								p.append(buildFarmInfo(prop));
							else
								if (kind.startsWith("pie"))
									p.append(buildPiePage(prop, hmSeries));
								else
									p.append(buildRealTimePage(prop, hmSeries));
		}
		catch (final Throwable e) {
			System.out.println("Caught exception while building the '" + kind + "' page : " + e + " (" + e.getMessage() + ")");
			e.printStackTrace(System.err);
			System.err.println("Java memory info: \n" + "  Free memory : " + Runtime.getRuntime().freeMemory() + "\n" + "  Total memory: " + Runtime.getRuntime().totalMemory() + "\n"
					+ "  Max memory  : " + Runtime.getRuntime().maxMemory());
		}

		if (!download || (tsDownloadSeries.size() == 0) || (tmDownloadData.size() == 0)) {
			pMaster.append(p);

			initMasterPage(pMaster, prop, sResDir);
			pMaster.modify("title", sTitle);

			pMaster.modify("bookmark", getBookmarkURL());

			pMaster.write();
		}
		else {
			boolean bSingle = true;

			if (tsDownloadSeries.size() > 1) {
				String s1 = tsDownloadSeries.first();
				String s2 = tsDownloadSeries.last();

				s1 = s1.substring(0, s1.indexOf("@"));
				s2 = s2.substring(0, s2.indexOf("@"));

				if (!s1.equals(s2))
					bSingle = false;
			}

			final boolean bCSV = gets("download_data_csv").length() > 0;

			response.setContentType(bCSV ? "text/csv" : "text/html");
			response.addHeader("content-disposition", "attachment;filename=download_data." + (bCSV ? "csv" : "html"));

			final PrintWriter pw = new PrintWriter(baos);

			pw.print(bCSV ? "Time" : "<table>\n<tr><th>Time</th>");

			final Iterator<String> it = tsDownloadSeries.iterator();

			while (it.hasNext()) {
				final String s = it.next();

				pw.print(bCSV ? "," : "<th>");

				pw.print(bSingle ? s.substring(s.indexOf("@") + 1) : s);

				if (!bCSV)
					pw.print("</th>");
			}

			pw.println(bCSV ? "" : "</tr>");

			final Iterator<Map.Entry<Long, HashMap<String, Double>>> it2 = tmDownloadData.entrySet().iterator();

			while (it2.hasNext()) {
				final Map.Entry<Long, HashMap<String, Double>> me = it2.next();

				if (!bCSV)
					pw.print("<tr><td>");

				pw.print(me.getKey().toString());

				if (!bCSV)
					pw.print("</td>");

				final HashMap<String, Double> hmValues = me.getValue();

				final Iterator<String> it3 = tsDownloadSeries.iterator();

				while (it3.hasNext()) {
					final Double d = hmValues.get(it3.next());

					pw.print(bCSV ? "," : "<td>");

					pw.print(d == null ? "" : d.toString());

					if (!bCSV)
						pw.print("</td>");
				}

				pw.println(bCSV ? "" : "</tr>");
			}

			if (!bCSV)
				pw.println("</table>");

			pw.flush();

			tmDownloadData = null;
			tsDownloadSeries = null;
		}

		int written = 0;

		try {
			written = writeResponse(baos.toByteArray(), request, response, osOut);
		}
		catch (final IOException ioe) {
			logger.log(Level.WARNING, "Exception writing the response back to the client " + request.getRemoteAddr(), ioe);
		}

		baos = null;

		System.err.println("display: " + kind + "(" + gets("page") + "), ip: " + getHostName() + ", took " + (System.currentTimeMillis() - lStart) + "ms to complete");

		Utils.logRequest("display", written, request, false, System.currentTimeMillis() - lStart);

		bAuthOK = true;
	}

	/**
	 * Max. running time for this servlet. 10 minutes is too long actually, but let no one complain that a
	 * slow connection is not good enough to browse the ML repository.
	 *
	 * @return 10 minutes
	 */
	@Override
	protected int getMaxRunTime() {
		return 600;
	}

	/**
	 * Wrapper page for several subcharts.
	 *
	 * @param prop
	 *            properties used to define the file
	 * @return a Page object
	 */
	public Page buildFarmInfo(final Properties prop) {
		String sPage = pgets(prop, "resfile", "farminfo");

		if (sPage.indexOf("..") >= 0)
			return null;

		if (!sPage.endsWith(".res"))
			sPage += ".res";

		final Page p = new Page(sResDir + "display/" + sPage);

		final String sFarm = gets("farm");

		p.modify("farm", sFarm);
		p.modify("page", gets("page"));
		setExtraFields(p, prop);

		setMinMax(prop, null);

		showIntervalSelectionForm(prop, p, NTPDate.currentTimeMillis());

		final Vector<String> v = toVector(prop, "Farms", null);

		for (int i = 0; i < v.size(); i++) {
			final String s = v.get(i);

			p.append("opt_farm", "<option value=\"" + escHtml(s) + "\"" + (sFarm.equals(s) ? " selected" : "") + ">" + escHtml(s) + "</option>");
		}

		// try {
		// // FIX THIS LATER
		// //stats.buildStatistics(p, prop, sResDir);
		// } catch (Exception e) {
		// }

		fillPageFromProperties(p, prop);

		return p;
	}

	// ------------------------------------------------------------------------------------------------------------------------------------

	private void setMinMax(final Properties prop, final Properties propExtra) {
		if ((lIntervalMin > 0) || (lIntervalMax > 0))
			return;

		final long lDataStart = store.getStartTime();

		long lInterval = NTPDate.currentTimeMillis() - lDataStart;
		final long lILength = 1000L * 60L * 60L;

		final long lStart = pgetl(prop, "interval.start", 0);
		final long lEnd = pgetl(prop, "interval.end", 0);

		lBaseTime = 0;

		if ((lStart > 0) && (lEnd > 0)) {
			lBaseTime = lEnd;

			lInterval = (((lEnd - lStart) / lILength) + 1) * lILength;
		}

		long lMin = pgetl(prop, "interval.min", 3600000);
		long lMax = pgetl(prop, "interval.max", 0);

		if (logTiming()) {
			logTiming(" --   setMinMax : lDataStart = " + lDataStart);
			logTiming(" --   setMinMax : lInterval = " + lInterval);
			logTiming(" --   setMinMax : lILength = " + lILength);
			logTiming(" --   setMinMax : lStart = " + lStart + ", lEnd = " + lEnd);
		}

		if (lMin < lMax) {
			final long t = lMin;
			lMin = lMax;
			lMax = t;
		}

		if (logTiming())
			logTiming(" --   setMinMax : after sort : lMin = " + lMin + ", lMax = " + lMax);

		if (!pgetb(prop, "allow.any.timestamps", false)) {

			if ((lMin - lMax) < lILength)
				lMin += 2 * lILength;

			if (logTiming())
				logTiming(" --   setMinMax : after length check : lMin = " + lMin + ", lMax = " + lMax);

			if ((lMin > lInterval) || (lMax < 0) || ((lMin % lILength) != 0) || ((lMax % lILength) != 0)) {
				lMin = lInterval;
				lMax = 0;
			}

			if (logTiming())
				logTiming(" --   setMinMax : after division check : lMin = " + lMin + ", lMax = " + lMax);
		}

		final boolean bDisableRefreshIfPast = pgetb(prop, "refresh.disable_if_not_current_time", false);

		final long lRefreshMax = pgetl(prop, "refresh.disable_if_interval_larger_than", 0);

		if ((bDisableRefreshIfPast && (lMax > 0)) || ((lRefreshMax > 0) && ((lRefreshMax * 60L * 60L * 1000L) < (lMin - lMax)))) {
			prop.setProperty("refresh.time", "0");

			if (propExtra != null)
				propExtra.setProperty("refresh.time", "0");
		}

		lIntervalMin = lMin;
		lIntervalMax = lMax;

		long lCompactInterval = Utils.getCompactInterval(prop, lIntervalMin, lIntervalMax);

		if (propExtra != null) {
			final long lTemp = Utils.getCompactInterval(propExtra, lIntervalMin, lIntervalMax);
			if (lTemp > lCompactInterval)
				lCompactInterval = lTemp;
		}

		long lRefresh = pgetl(prop, "refresh.time", 60);

		if (propExtra != null)
			lRefresh = pgetl(prop, "refresh.time", lRefresh);

		if ((lRefresh > 0) && (lRefresh < (lCompactInterval / 1000))) {
			lRefresh = lCompactInterval / 1000;

			prop.setProperty("refresh.time", "" + lRefresh);

			if (propExtra != null)
				propExtra.setProperty("refresh.time", "" + lRefresh);
		}
	}

	private final Plot getCategoryPlot(final Properties prop, final HashMap<String, String> hmSeries, final HashMap<String, String> hmLegends) {
		return getCategoryPlot(prop, hmSeries, hmLegends, false);
	}

	private final Plot getCategoryPlot(final Properties prop, final HashMap<String, String> hmSeries, final HashMap<String, String> hmLegends, final boolean bRange) {
		return getCategoryPlot(prop, hmSeries, hmLegends, bRange, false);
	}

	/**
	 * Get the latest values from monitor_ids table.
	 *
	 * @param vPreds
	 *            Vector of mon_predicate objects
	 * @return a DataSplitter with the values taken from monitor_ids table
	 */
	private final static DataSplitter getDBLast(final Vector<monPredicate> vPreds) {
		final HashSet<Integer> hsIDs = new HashSet<>();

		for (int i = 0; i < vPreds.size(); i++)
			hsIDs.addAll(IDGenerator.getIDs(vPreds.get(i)));

		if (hsIDs.size() == 0)
			return new DataSplitter(1);

		final DataSplitter ds = new DataSplitter(hsIDs.size());

		final StringBuilder sbQuery = new StringBuilder("SELECT mi_id,mi_lastseen,mi_lastvalue FROM monitor_ids WHERE mi_id IN (");

		final Iterator<Integer> it = hsIDs.iterator();

		boolean bFirst = true;

		while (it.hasNext()) {
			if (!bFirst)
				sbQuery.append(",");
			else
				bFirst = false;

			sbQuery.append(it.next());
		}

		sbQuery.append(");");

		final DB db = new DB();

		db.setReadOnly(true);

		db.query(sbQuery.toString());

		while (db.moveNext()) {
			final IDGenerator.KeySplit split = IDGenerator.getKeySplit(db.geti(1));

			if (split == null)
				continue;

			final ExtendedResult r = new ExtendedResult();

			r.FarmName = split.FARM;
			r.ClusterName = split.CLUSTER;
			r.NodeName = split.NODE;
			r.addSet(split.FUNCTION, db.getd(3));

			r.time = db.getl(2) * 1000;

			ds.add(r);
		}

		return ds;
	}

	private final Plot getCategoryPlot(final Properties prop, final HashMap<String, String> hmSeries, final HashMap<String, String> hmLegends, final boolean bRange, final boolean bVertical) {
		String vsDescr[] = null;
		monPredicate[] preds;
		String[] series = null;

		final String[] vsFarms = Utils.getValues(prop, "Farms");
		final String[] vsClusters = Utils.getValues(prop, "Clusters");
		final String[] vsNodes = Utils.getValues(prop, "Nodes");
		final String[] vsFunctions = Utils.getValues(prop, "Functions");
		String[] vsFunctionSuff = Utils.getValues(prop, "FuncSuff");

		final boolean bSize = pgetb(prop, "size", false);
		final boolean bSpiderLogScale = pgetb(prop, "spider_logscale", false);

		if ((vsFunctionSuff == null) || (vsFunctionSuff.length <= 0))
			vsFunctionSuff = new String[] { "" };

		final String[] vsWildcards = Utils.getValues(prop, "Wildcards");

		int len = 0;
		int i;

		int w = -1;

		for (i = 0; (vsWildcards != null) && (i < vsWildcards.length); i++) {
			int newlen = 0;

			String[] newseries = null;

			if (vsWildcards[i].equals("F")) {
				newlen = vsFarms != null ? vsFarms.length : 0;
				newseries = vsFarms;
			}

			if (vsWildcards[i].equals("C")) {
				newlen = vsClusters != null ? vsClusters.length : 0;
				newseries = vsClusters;
			}

			if (vsWildcards[i].equals("N")) {
				newlen = vsNodes != null ? vsNodes.length : 0;
				newseries = vsNodes;
			}

			if (vsWildcards[i].equals("f")) {
				newlen = vsFunctions != null ? vsFunctions.length : 0;
				newseries = vsFunctions;
			}

			if (newlen > 0) {
				w = i;
				len = newlen;
				series = newseries;
			}
		}

		if ((len <= 0) || (w < 0) || (vsWildcards == null) || (series == null)) {
			System.err.println("len : " + len + ", w : " + w + ", so i'm returning null");
			return null;
		}

		preds = new monPredicate[len];

		for (i = 0; i < len; i++) {
			preds[i] = new monPredicate();

			if (vsWildcards[w].equals("F")) {
				if (vsFarms != null)
					preds[i].Farm = vsFarms[i];
			}
			else
				preds[i].Farm = ((vsFarms != null) && (vsFarms.length > 0)) ? vsFarms[0] : "*";

			if (vsWildcards[w].equals("C")) {
				if (vsClusters != null)
					preds[i].Cluster = vsClusters[i];
			}
			else
				preds[i].Cluster = ((vsClusters != null) && (vsClusters.length > 0)) ? vsClusters[0] : "*";

			if (vsWildcards[w].equals("N")) {
				if (vsNodes != null)
					preds[i].Node = vsNodes[i];
			}
			else
				preds[i].Node = ((vsNodes != null) && (vsNodes.length > 0)) ? vsNodes[0] : "*";

			String[] param = null;

			if (vsWildcards[w].equals("f"))
				param = new String[] { vsFunctions != null ? vsFunctions[i] : "*" };
			else
				param = (vsFunctions != null) && (vsFunctions.length > 0) ? vsFunctions : new String[] { "*" };

			preds[i].parameters = new String[param.length * vsFunctionSuff.length];

			for (int j = 0; j < param.length; j++)
				for (int k = 0; k < vsFunctionSuff.length; k++)
					preds[i].parameters[(j * vsFunctionSuff.length) + k] = param[j] + vsFunctionSuff[k];

			preds[i].tmin = -15 * 60 * 1000;
			preds[i].tmax = -1;
		}

		String[] vsMultiSeries = new String[0];
		int iMultiSeriesID = -1;

		if (!vsWildcards[w].equals("F") && (vsFarms != null) && (vsFarms.length > 1)) {
			vsMultiSeries = vsFarms;
			iMultiSeriesID = 0;
		}
		else
			if (!vsWildcards[w].equals("C") && (vsClusters != null) && (vsClusters.length > 1)) {
				vsMultiSeries = vsClusters;
				iMultiSeriesID = 1;
			}
			else
				if (!vsWildcards[w].equals("N") && (vsNodes != null) && (vsNodes.length > 1)) {
					vsMultiSeries = vsNodes;
					iMultiSeriesID = 2;
				}
				else
					if (!vsWildcards[w].equals("f") && (vsFunctions != null) && (vsFunctions.length >= 1)) {
						vsMultiSeries = vsFunctions;
						iMultiSeriesID = 3;

						if (vsFunctionSuff.length >= 1) {
							iMultiSeriesID = 4;
							vsMultiSeries = new String[vsFunctions.length * vsFunctionSuff.length];

							for (i = 0; i < vsFunctions.length; i++)
								for (int j = 0; j < vsFunctionSuff.length; j++)
									vsMultiSeries[(i * vsFunctionSuff.length) + j] = vsFunctions[i] + vsFunctionSuff[j];
						}
					}
					else
						if (vsFunctionSuff.length > 1) {
							iMultiSeriesID = 5;
							vsMultiSeries = vsFunctionSuff;
						}

		vsDescr = Utils.getValues(prop, "charts.descr");
		if (vsDescr == null)
			vsDescr = Utils.getValues(prop, "descr");

		final int descrLen = vsMultiSeries.length;

		if ((vsDescr == null) || (vsDescr.length != descrLen)) {
			final String[] vsDescrOrig = vsDescr;

			vsDescr = new String[descrLen];
			for (i = 0; i < descrLen; i++)
				if ((vsDescrOrig != null) && (vsDescrOrig.length > i) && (vsDescrOrig[i] != null))
					vsDescr[i] = vsDescrOrig[i];
				else
					vsDescr[i] = vsMultiSeries[i];
		}

		final DefaultCategoryDataset categoryDataset = new DefaultCategoryDataset();

		final String[] serieso = new String[series.length];
		for (i = 0; i < series.length; i++)
			serieso[i] = series[i];

		series = Utils.sortSeries(series, prop, request.getParameterValues("unselect"), hmSeries);
		final monPredicate[] predsn = new monPredicate[preds.length];

		for (i = 0; i < series.length; i++)
			for (int j = 0; j < serieso.length; j++)
				if (series[i].equals(serieso[j])) {
					predsn[i] = preds[j];
					break;
				}

		preds = predsn;

		final String sSecondAxisPredicate = pgets(prop, "secondaxis_predicate");
		final monPredicate pSecondary;
		final DefaultCategoryDataset cdSecondary;
		final String sSecondAxisLabel;

		if ((sSecondAxisPredicate.length() > 0) && (pgeti(prop, "secondary_axis.enabled", 1) == 1)) {
			pSecondary = toPred(sSecondAxisPredicate);
			cdSecondary = new DefaultCategoryDataset();

			sSecondAxisLabel = pgets(prop, "secondaxis_label");
		}
		else {
			pSecondary = null;
			cdSecondary = null;

			sSecondAxisLabel = null;
		}

		double dMaxValue = -1;

		final HashMap<String, String> hmDataRows = new HashMap<>();
		final HashMap<String, String> hmDataColumns = new HashMap<>();

		final HashMap<String, String> hmNonZeroRows = new HashMap<>();
		final HashMap<String, String> hmNonZeroColumns = new HashMap<>();

		final boolean bSpiderWebPlot = pgetb(prop, "spider_web_plot", false);

		final boolean bIgnoreZero = pgetb(prop, "ignore_zero", false);

		final boolean bHistoryChart = pgetb(prop, "history.chart", false);

		final String sHistoryFunction = pgets(prop, "history.function", "avg");

		long lCompactInterval = 0;

		if (bHistoryChart)
			lCompactInterval = Utils.getCompactInterval(prop, lIntervalMin, lIntervalMax);

		final Vector<String> vLocalActualSeries = new Vector<>();

		boolean bFound = false;
		for (i = 0; i < series.length; i++)
			if (hmSeries.containsKey(series[i])) {
				bFound = true;
				break;
			}

		if (!bFound)
			hmSeries.clear();

		final DataSplitter ds;

		if (bHistoryChart) {
			// get the history data in one shot

			final Vector<monPredicate> vPreds = new Vector<>();

			for (i = 0; i < series.length; i++)
				for (int j = 0; j < vsMultiSeries.length; j++) {
					final monPredicate p = preds[i];

					final monPredicate pTemp = new monPredicate();
					pTemp.Farm = iMultiSeriesID == 0 ? vsMultiSeries[j] : preds[i].Farm;
					pTemp.Cluster = iMultiSeriesID == 1 ? vsMultiSeries[j] : preds[i].Cluster;
					pTemp.Node = iMultiSeriesID == 2 ? vsMultiSeries[j] : preds[i].Node;

					if ((iMultiSeriesID == 3) || (iMultiSeriesID == 4))
						pTemp.parameters = new String[] { vsMultiSeries[j] };
					else
						if (iMultiSeriesID == 5)
							pTemp.parameters = new String[] { p.parameters[j] };
						else
							pTemp.parameters = p.parameters;

					vPreds.add(pTemp);
				}

			if (sHistoryFunction.equals("dblast"))
				ds = getDBLast(vPreds);
			else
				ds = getDataSplitter(vPreds, prop, lCompactInterval);
		}
		else
			ds = null;

		for (i = 0; i < series.length; i++) {
			if (!vTotalSeries.contains(series[i]))
				vTotalSeries.add(series[i]);

			boolean bEnabled = (hmSeries.size() == 0) || hmSeries.containsKey(series[i]);

			final String sLabel = getDescr(prop, series[i]);

			if (logTiming())
				logTiming("** SERIES : " + series[i] + " / " + sLabel);

			Result rTemp;

			boolean bSomeValue = false;

			for (int j = 0; j < vsMultiSeries.length; j++) {
				final monPredicate p = preds[i];

				final monPredicate pTemp = new monPredicate();
				pTemp.Farm = iMultiSeriesID == 0 ? vsMultiSeries[j] : preds[i].Farm;
				pTemp.Cluster = iMultiSeriesID == 1 ? vsMultiSeries[j] : preds[i].Cluster;
				pTemp.Node = iMultiSeriesID == 2 ? vsMultiSeries[j] : preds[i].Node;

				if ((iMultiSeriesID == 3) || (iMultiSeriesID == 4))
					pTemp.parameters = new String[] { vsMultiSeries[j] };
				else
					if (iMultiSeriesID == 5)
						pTemp.parameters = new String[] { p.parameters[j] };
					else
						pTemp.parameters = p.parameters;

				if (logTiming())
					logTiming("Predicate is : " + pTemp.toString());

				Vector<Result> vTemp;

				if (!bHistoryChart)
					vTemp = Utils.toResultVector(Cache.getLastValues(pTemp));
				else {
					pTemp.tmin = -lIntervalMin;
					pTemp.tmax = -lIntervalMax;

					vTemp = ds != null ? Utils.toResultVector(ds.get(pTemp)) : null;

					Utils.filterMultipleSeries(vTemp, prop, series[i], true);

					if (logTiming()) {
						logTiming("RT: " + vTemp.size() + " values for history predicate " + pTemp);
						logTiming("RT: history parameters: lCompactInterval=" + lCompactInterval + ", interval.min=" + lIntervalMin + ", interval.max=" + lIntervalMax);
					}

					if ((vTemp != null) && (vTemp.size() > 0)) {
						final boolean bAvg = sHistoryFunction.equals("avg");
						final boolean bSum = sHistoryFunction.equals("sum");
						final boolean bInt = sHistoryFunction.equals("int");
						final boolean bLast = sHistoryFunction.equals("last");
						final boolean bDBLast = sHistoryFunction.equals("dblast");

						double dVal = 0;

						if (bAvg || bSum) {
							int iCount = 0;

							for (int k = 0; k < vTemp.size(); k++) {
								final Object o = vTemp.get(k);

								if (!(o instanceof Result))
									continue;

								final Result r = (Result) o;

								if (bAvg || bSum) {
									dVal += r.param[0];
									iCount++;
								}
							}

							if (bAvg && (iCount > 1))
								dVal /= iCount;
						}

						if (bInt) {
							Utils.integrateSeries(vTemp, prop, true, lIntervalMin, lIntervalMax);

							dVal = vTemp.get(vTemp.size() - 1).param[0];
						}

						if (bLast || bDBLast)
							dVal = vTemp.lastElement().param[0];

						final Result r = Utils.copyResult(vTemp.get(0));
						vTemp.clear();
						r.param[0] = dVal;
						vTemp.add(r);
					}
				}

				if (logTiming())
					logTiming("Got : " + vTemp);

				if ((vTemp == null) || (vTemp.size() == 0))
					rTemp = null;
				else {
					final Object o = vTemp.get(0);

					if ((o != null) && (o instanceof Result)) {
						rTemp = (Result) o;

						if (vTemp.size() > 1) {
							final Result rNew = new Result();
							rNew.FarmName = rTemp.FarmName;
							rNew.ClusterName = rTemp.ClusterName;
							rNew.NodeName = rTemp.NodeName;
							rNew.time = rTemp.time;
							rNew.addSet(rTemp.param_name[0], rTemp.param[0]);

							rTemp = rNew;

							final boolean bSumMultipleValues = pgetb(prop, "multiple_values.sum", true);
							final boolean bAvgMultipleValues = pgetb(prop, "multiple_values.avg", false);

							if (bSumMultipleValues || bAvgMultipleValues) {
								int iCount = 1;

								for (int k = 1; k < vTemp.size(); k++) {
									rTemp.param[0] += vTemp.get(k).param[0];
									iCount++;
								}

								if (bAvgMultipleValues)
									rTemp.param[0] /= iCount;
							}
							else {
								Utils.filterMultipleSeries(vTemp, prop, series[i], true);

								if (vTemp.size() > 0)
									rTemp = vTemp.get(0);
							}
						}
					}
					else
						rTemp = null;
				}

				if (logTiming())
					logTiming("Value to display (" + bEnabled + ") : " + rTemp);

				if (bEnabled) {
					if (rTemp != null) {
						if ((bSpiderLogScale == true) && (rTemp.param[0] <= 0))
							continue;
						double dVal = bSpiderLogScale == true ? log10(rTemp.param[0]) : rTemp.param[0];

						if (dVal > dMaxValue)
							dMaxValue = dVal;

						if (dVal > 1E-10) {
							hmNonZeroRows.put(vsDescr[j], "");
							hmNonZeroColumns.put(series[i], "");
						}

						hmActualSeries.put(series[i], "");

						if (bSpiderWebPlot) {
							if (dVal < 1E-10)
								dVal = 1E-10;

							hmDataRows.put(vsDescr[j], "");
							hmDataColumns.put(series[i], "");
						}

						categoryDataset.addValue(dVal, vsDescr[j], sLabel);

						bSomeValue = true;
					}
					else
						if (bSpiderWebPlot)
							categoryDataset.addValue(1E-10, vsDescr[j], sLabel);
						else
							categoryDataset.addValue(null, vsDescr[j], sLabel);
				}
				else
					if (rTemp != null) {
						hmActualSeries.put(series[i], "");
						break;
					}
			}

			if (!bSomeValue)
				// categoryDataset.removeColumn(sLabel);
				bEnabled = false;

			if (bEnabled && (cdSecondary != null) && (pSecondary != null)) {
				if (vsWildcards[w].equals("F"))
					pSecondary.Farm = preds[i].Farm;
				else
					if (vsWildcards[w].equals("C"))
						pSecondary.Cluster = preds[i].Cluster;
					else
						if (vsWildcards[w].equals("N"))
							pSecondary.Node = preds[i].Node;
						else
							pSecondary.parameters = new String[] { preds[i].parameters[0] };

				final Object oTemp = Cache.getLastValue(pSecondary);

				if ((oTemp != null) && (oTemp instanceof Result))
					cdSecondary.addValue(((Result) oTemp).param[0], sSecondAxisLabel, sLabel);
				else
					cdSecondary.addValue(null, sSecondAxisLabel, sLabel);
			}
		}

		if (bIgnoreZero) {
			if (logTiming())
				logTiming("Removing zero axis");

			final Iterator<String> it = vTotalSeries.iterator();
			String s;
			while (it.hasNext()) {
				s = it.next();
				if (hmNonZeroColumns.get(s) == null) {
					try {
						categoryDataset.removeColumn(s);
					}
					catch (final Exception e) {
						log("display#getCategoryPlot: cannot remove all-zero data series '" + s + "'", e);
					}

					if (logTiming())
						logTiming("Removing zero column : " + s);
				}
			}

			if (logTiming())
				logTiming("Removing zero series");

			for (i = 0; i < categoryDataset.getRowCount(); i++) {
				final String sRow = (String) categoryDataset.getRowKey(i);
				if ((sRow != null) && (hmNonZeroRows.get(sRow) == null)) {
					categoryDataset.removeRow(i);

					if (logTiming())
						logTiming("Removing zero row : " + i);
				}
			}
		}

		Plot retPlot;

		if (bSpiderWebPlot) {
			final boolean bOrderByRows = pgetb(prop, "spider_order_by_rows", true);

			if (pgetb(prop, "spider_remove_null_axis", true)) {
				if (logTiming())
					logTiming("Removing null axis");

				final Iterator<String> it = vTotalSeries.iterator();
				String s;
				while (it.hasNext()) {
					s = it.next();
					if (hmDataColumns.get(s) == null) {
						try {
							categoryDataset.removeColumn(s);
						}
						catch (final Exception e) {
							log("display#getCategoryPlot: cannot remove data series '" + s + "'", e);
						}

						if (logTiming())
							logTiming("Removing column : " + s);
					}
				}
			}

			if (pgetb(prop, "spider_remove_null_series", true)) {
				if (logTiming())
					logTiming("Removing null series");

				for (i = 0; i < categoryDataset.getRowCount(); i++) {
					final String sRow = (String) categoryDataset.getRowKey(i);
					if ((sRow != null) && (hmDataRows.get(sRow) == null)) {
						categoryDataset.removeRow(i);

						if (logTiming())
							logTiming("Removing row : " + i);
					}
				}
			}

			final SpiderWebPlot plot = new SpiderWebPlot(categoryDataset, bOrderByRows ? org.jfree.util.TableOrder.BY_ROW : org.jfree.util.TableOrder.BY_COLUMN);

			plot.setWebFilled(pgetb(prop, "spider_web_filled", true));

			final int rad = pgeti(prop, "spider_radius", 0);

			if ((dMaxValue > 0) && (rad > 0)) {
				final int r = rad + 30; // extra margins

				BufferedImage image = new BufferedImage(r * 2, r * 2, BufferedImage.TYPE_INT_RGB);
				final Graphics2D graphics = (Graphics2D) image.getGraphics();
				final Font font = new Font("Arial", Font.BOLD, 11); // Font.PLAIN

				graphics.setColor(ColorFactory.getColor(255, 255, 255));
				graphics.fillRect(0, 0, r * 2, r * 2);

				graphics.setFont(font);

				graphics.setColor(ColorFactory.getColor(170, 190, 210));

				graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				final int iMinCircles = bSpiderLogScale == true ? (int) Math.ceil(dMaxValue) : rad / 50;
				final int iMaxCircles = rad / 30;

				double dMaxTemp = dMaxValue;
				long lFactor = 0;

				while (dMaxTemp >= 1000) { // 3 digits max
					dMaxTemp /= 10d;
					lFactor++;
				}
				while (dMaxTemp < 100) { // 3 digits min
					dMaxTemp *= 10d;
					lFactor--;
				}

				double dScaledMax = dMaxValue;

				final long lFactorTemp = lFactor;
				while (lFactor > 0) {
					dScaledMax /= 10d;
					lFactor--;
				}
				while (lFactor < 0) {
					dScaledMax *= 10d;
					lFactor++;
				}
				lFactor = lFactorTemp;

				int iCircles = iMinCircles;
				double dVisibleMax = Math.round(dMaxTemp / (10d * iCircles)) * (10d * iCircles);
				double dVisibleTemp;

				for (i = iMinCircles + 1; i <= iMaxCircles; i++) {
					dVisibleTemp = Math.round(dMaxTemp / (10d * i)) * (10d * i);

					if (Math.abs(dScaledMax - dVisibleMax) > Math.abs(dScaledMax - dVisibleTemp)) {
						dVisibleMax = dVisibleTemp;
						iCircles = i;
					}
				}

				while (lFactor > 0) {
					dVisibleMax *= 10d;
					lFactor--;
				}
				while (lFactor < 0) {
					dVisibleMax /= 10d;
					lFactor++;
				}

				if (bSpiderLogScale) {
					dVisibleMax = Math.round(dVisibleMax);

					if (iCircles > dVisibleMax)
						iCircles = (int) dVisibleMax;
				}

				final double dAbsMax = dVisibleMax > dMaxValue ? dVisibleMax : dMaxValue;

				if (dAbsMax > dMaxValue)
					plot.setMaxValue(dAbsMax);

				final FontMetrics fm = graphics.getFontMetrics();

				int iPos;
				double dValue, dValueTemp;
				for (i = 1; i <= iCircles; i++) {
					if (bSpiderLogScale)
						dValue = (dVisibleMax - iCircles) + i;
					else
						dValue = (dVisibleMax * i) / iCircles;

					dValueTemp = (dValue / dAbsMax) * rad;

					iPos = (int) (dValueTemp);

					graphics.drawOval(r - iPos - 1, r - iPos - 1, iPos * 2, iPos * 2);

					dValue = bSpiderLogScale == true ? Math.pow(10, dValue) : dValue;
					final String sValue = DoubleFormat.point(dValue);
					graphics.drawString(sValue, r + 2, r - iPos - 2);

					iPos = (int) (dValueTemp * 0.8660254037844386467868626477972782140569d);

					graphics.drawString(sValue, r + iPos + 4 + (int) (dValueTemp / 20d), ((r + (int) (dValueTemp / 2d)) - (int) (dValueTemp / 10d)) + 3);

					graphics.drawString(sValue, r - iPos - (int) (fm.stringWidth(sValue) * 0.866d) - 6 - (int) (dValueTemp / 20d), ((r + (int) (dValueTemp / 2d)) - (int) (dValueTemp / 10d)) + 3);
				}

				plot.setBackgroundImage(image);
				plot.setBackgroundImageAlignment(Align.CENTER);

				image = null;
			}

			retPlot = plot;
		}
		else { // normal bar plot
			final boolean bStack = pgetb(prop, "stack", false);
			final boolean b3D = pgetb(prop, "3d", true);

			CategoryItemRenderer cir;

			if (bStack) {
				if (b3D)
					cir = new StackedBarRenderer3D(5, 4);
				else
					cir = new StackedBarRenderer();
			}
			else
				if (b3D)
					cir = new BarRenderer3D(5, 4);
				else
					cir = new BarRenderer();

			String sSizeIn = pgets(prop, "sizein", "M");
			String sYLabel = pgets(prop, "ylabel", "");
			boolean bInBits = pgetb(prop, "datainbits", sYLabel.toLowerCase().endsWith("bps"));
			String sSuffix = pgets(prop, "tooltip.suffix", bSize ? (bInBits ? "bps" : "B") : "");

			final NumberAxis numberAxis = b3D ? new NumberAxis3D(sYLabel) : new NumberAxis(sYLabel);
			MyNumberFormat nf = new MyNumberFormat(bSize, sSizeIn, sSuffix, bInBits);
			NumberTickUnit ntu = new NumberTickUnit(bSize ? 1.024 : 1.0, nf);
			numberAxis.setTickUnit(ntu, false, false);
			numberAxis.setNumberFormatOverride(nf);

			numberAxis.setLabelFont(getLabelFont(prop, "y"));
			numberAxis.setTickLabelFont(getTickLabelFont(prop, "y"));

			if (pgetb(prop, "number.axis.inverted", false))
				numberAxis.setInverted(true);

			if (pgetb(prop, "tooltips.enabled", true)) {
				final String sFormat = pgets(prop, "tooltips.format", "{1}: {0} = {2}");

				cir.setBaseToolTipGenerator(new StandardCategoryToolTipGenerator(sFormat, nf));
			}

			CategoryAxis catAxis;

			if (b3D)
				catAxis = new CategoryAxis3D(pgets(prop, "xlabel"));
			else
				catAxis = new CategoryAxis(pgets(prop, "xlabel"));

			catAxis.setLabelFont(getLabelFont(prop, "x"));
			catAxis.setTickLabelFont(getTickLabelFont(prop, "x"));

			final CategoryPlot categoryPlot = new MyCategoryPlot(categoryDataset, catAxis, numberAxis, cir, hmLegends);

			if (pgetb(prop, "urls.enabled", false)) {
				final StandardCategoryURLGenerator scurlg = new StandardCategoryURLGenerator(pgets(prop, "urls.prefix", "display?page=" + gets("page")),
						pgets(prop, "urls.series_parameter_name", "plot_series"), pgets(prop, "urls.category_parameter_name", "category"));

				cir.setBaseItemURLGenerator(scurlg);
			}

			if (cdSecondary != null) {
				sSizeIn = pgets(prop, "secondaxis_sizein", "M");
				sYLabel = pgets(prop, "secondaxis_ylabel", "");
				bInBits = pgetb(prop, "secondaxis_datainbits", sYLabel.toLowerCase().endsWith("bps"));
				sSuffix = pgets(prop, "secondaxis_tooltip.suffix", bSize ? (bInBits ? "bps" : "B") : "");

				final NumberAxis numberAxis2 = b3D ? new NumberAxis3D(sYLabel) : new NumberAxis(sYLabel);
				nf = new MyNumberFormat(bSize, sSizeIn, sSuffix, bInBits);
				ntu = new NumberTickUnit(bSize ? 1.024 : 1.0, nf);
				numberAxis2.setTickUnit(ntu, false, false);
				numberAxis2.setNumberFormatOverride(nf);

				numberAxis2.setLabelFont(getLabelFont(prop, "y"));
				numberAxis2.setTickLabelFont(getTickLabelFont(prop, "y"));

				if (pgetb(prop, "number.axis.inverted", false))
					numberAxis2.setInverted(true);

				categoryPlot.setRangeAxis(1, numberAxis2);
				categoryPlot.setDataset(1, cdSecondary);
				categoryPlot.mapDatasetToRangeAxis(1, 1);

				final boolean bLine3D = pgetb(prop, "secondaxis_line3d", true);

				final boolean bLines = pgetb(prop, "secondaxis_line.lines_visible", false);
				final boolean bShapes = pgetb(prop, "secondaxis_line.shapes_visible", true);

				final LineAndShapeRenderer linerenderer = (bLine3D && bLines) ? new LineRenderer3D() : new LineAndShapeRenderer(bLines, bShapes);

				if (pgetb(prop, "secondaxis_tooltips.enabled", true)) {
					final String sFormat = pgets(prop, "secondaxis_tooltips.format", "{1}: {0} = {2}");

					linerenderer.setBaseToolTipGenerator(new StandardCategoryToolTipGenerator(sFormat, nf));
				}

				linerenderer.setSeriesPaint(0, getColor(prop, "secondaxis_color", Color.blue));
				linerenderer.setSeriesShape(0, getShape(pgets(prop, "secondaxis_shape", "o")));
				if (bLine3D && (linerenderer instanceof LineRenderer3D)) {
					((LineRenderer3D) linerenderer).setXOffset(pgeti(prop, "secondaxis_line3d_xoffset", 6));
					((LineRenderer3D) linerenderer).setYOffset(pgeti(prop, "secondaxis_line3d_yoffset", 5));
				}

				categoryPlot.setRenderer(1, linerenderer);

				categoryPlot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);

				if (pgetb(prop, "secondaxis_matchscale", false)) {
					final double max = Math.max(numberAxis.getUpperBound(), numberAxis2.getUpperBound());
					final double min = Math.min(numberAxis.getLowerBound(), numberAxis2.getUpperBound());

					numberAxis.setUpperBound(max);
					numberAxis2.setUpperBound(max);

					numberAxis.setLowerBound(min);
					numberAxis2.setLowerBound(min);
				}
			}

			if (bRange) {
				final CategoryAxis axis = categoryPlot.getDomainAxis();
				// axis.setLabelAngle(bVertical ? VERTICAL_ANGLE :
				// HORIZONTAL_ANGLE);
				axis.setCategoryLabelPositions(bVertical ? CategoryLabelPositions.UP_45 : CategoryLabelPositions.STANDARD);
			}

			retPlot = categoryPlot;
		}

		// see what series we actually displayed
		for (i = 0; i < categoryDataset.getRowCount(); i++) {
			final String sRow = (String) categoryDataset.getRowKey(i);

			vLocalActualSeries.add(sRow);
		}

		vIndividualSeries.add(vLocalActualSeries);

		return retPlot;
	}

	/**
	 * @param prop
	 * @param font
	 * @return font scaled to the configuration-specified size
	 */
	public static Font getScalledFont(final Properties prop, final Font font) {
		final double dScaleFont = pgetd(prop, "font.scale", -1);

		if ((dScaleFont > 0) && (font != null))
			return font.deriveFont((float) (font.getSize2D() * dScaleFont));

		return font;
	}

	/**
	 * @param prop
	 * @param axis
	 * @return the font for writing the chart tick values
	 */
	public static Font getTickLabelFont(final Properties prop, final String axis) {
		Font font = Axis.DEFAULT_TICK_LABEL_FONT;

		final double defaultFontSize = pgetd(prop, "default.font.size", -1);

		final double defaultLabelFontSize = pgetd(prop, "default.axis.font.size", defaultFontSize);

		final double defaultTickFontSize = pgetd(prop, "default.tick.font.size", defaultLabelFontSize);

		final double axisFontSize = pgetd(prop, axis + ".tick.font.size", defaultTickFontSize);

		if (axisFontSize > 0)
			font = font.deriveFont((float) axisFontSize);

		return getScalledFont(prop, font);
	}

	/**
	 * @param prop
	 * @param axis
	 * @return legend label font
	 */
	public static final Font getLabelFont(final Properties prop, final String axis) {
		Font font = Axis.DEFAULT_AXIS_LABEL_FONT;

		final double defaultFontSize = pgetd(prop, "default.font.size", -1);

		final double defaultLabelFontSize = pgetd(prop, "default.axis.font.size", defaultFontSize);

		final double axisFontSize = pgetd(prop, axis + ".axis.font.size", defaultLabelFontSize);

		if (axisFontSize > 0)
			font = font.deriveFont((float) axisFontSize);

		return getScalledFont(prop, font);
	}

	private int iDefaultHeight;

	private int iDefaultWidth;

	private final JFreeChart buildCategoryChart(final Properties prop, final HashMap<String, String> hmSeries) {
		final Plot plot = getCategoryPlot(prop, hmSeries, new HashMap<String, String>());

		if (plot == null)
			return null;

		final DrawingSupplier ds = new DefaultDrawingSupplier(getPaintSeries(prop), DefaultDrawingSupplier.DEFAULT_OUTLINE_PAINT_SEQUENCE, DefaultDrawingSupplier.DEFAULT_STROKE_SEQUENCE,
				DefaultDrawingSupplier.DEFAULT_OUTLINE_STROKE_SEQUENCE, getShapeSeries(prop));

		plot.setDrawingSupplier(ds);

		if (plot instanceof CategoryPlot) {
			boolean bVertical = true;

			final CategoryPlot categoryPlot = (CategoryPlot) plot;

			final String sOrientation = pgets(prop, "orientation");

			if ((sOrientation == null) || (sOrientation.trim().length() <= 0) || !sOrientation.trim().toLowerCase().equals("vertical"))
				bVertical = false;

			categoryPlot.setOrientation(bVertical ? PlotOrientation.VERTICAL : PlotOrientation.HORIZONTAL);

			final DefaultCategoryDataset cd = (DefaultCategoryDataset) (categoryPlot.getDataset());

			final Iterator<String> it = vTotalSeries.iterator();
			String s;
			while (it.hasNext()) {
				s = it.next();
				if (hmActualSeries.get(s) == null)
					try {
						cd.removeColumn(s);
					}
					catch (final Exception e) {
						log("display#buildCategoryChart: Cannot remove data series '" + s + "'", e);
					}
			}

			final CategoryAxis axis = categoryPlot.getDomainAxis();
			axis.setCategoryLabelPositions(bVertical ? CategoryLabelPositions.UP_45 : CategoryLabelPositions.STANDARD);
			// axis.setLabelAngle(bVertical ? VERTICAL_ANGLE : HORIZONTAL_ANGLE);

			final ValueAxis vaxis = categoryPlot.getRangeAxis();
			vaxis.setVerticalTickLabels(!bVertical);

			int height = 0;

			final boolean bStack = pgetb(prop, "stack", false);

			if (bVertical)
				height = 150 + 250;
			else
				if (bStack)
					height = 120 + (hmActualSeries.size() * 30);
				else
					height = 120 + (cd.getRowCount() * cd.getColumnCount() * 20) + (cd.getColumnCount() * 10);

			iDefaultHeight = height;
			iDefaultWidth = 800;
		}
		else
			if (plot instanceof SpiderWebPlot) {
				// nothing to do in this case, but kept here for future use
			}

		final String sChartTitle = pgets(prop, "title");

		sTitle += (sTitle.length() > 0 ? ", " : "") + sChartTitle;

		final JFreeChart chart = new JFreeChart(sChartTitle, JFreeChart.DEFAULT_TITLE_FONT, plot, true);

		// then customize it a little...
		setChartProperties(chart, prop);

		if (!pgetb(prop, "legend", true))
			chart.removeLegend();

		return chart;
	}

	private final Page buildRealTimePage(final Properties prop, final HashMap<String, String> hmSeries) {
		final Page p = new Page(sResDir + "display/rt.res");

		showOptions(sResDir, prop, p, this);

		p.modify("page", gets("page"));
		setExtraFields(p, prop);

		final JFreeChart chart = buildCategoryChart(prop, hmSeries);

		try {
			saveImage(chart, prop, iDefaultHeight, iDefaultWidth, p, getCacheTimeout());
		}
		catch (@SuppressWarnings("unused") final Exception e) {
			// ignore
		}

		final Page pSeries = new Page(sResDir + "display/hist_series.res");
		final Page pSeparate = new Page(sResDir + "display/hist_separate.res");

		displayCategories(vTotalSeries, prop, p, pSeries, pSeparate, hmSeries, hmActualSeries);

		return p;
	}

	private String sSeries;

	private final Page buildCombinedBar(final Properties prop, final HashMap<String, String> hmSeries, final String[] vsModules) {
		final Page p = new Page(sResDir + "display/rt.res");

		showOptions(sResDir, prop, p, this);

		p.modify("page", gets("page"));
		setExtraFields(p, prop);

		// create a default chart based on some sample data...
		final String title = pgets(prop, "title");

		final String[] vsCharts = Utils.getValues(prop, "charts");
		final String[] vsChartDescr = Utils.getValues(prop, "charts.descr");

		CategoryPlot parent;

		final boolean bRange = pgetb(prop, "samerange", false);

		final boolean b3D = pgetb(prop, "3d", true);

		final ValueAxis valueAxis = b3D ? new NumberAxis3D(pgets(prop, "ylabel")) : new NumberAxis(pgets(prop, "ylabel"));

		if (pgetb(prop, "number.axis.inverted", false))
			valueAxis.setInverted(true);

		valueAxis.setLabelFont(getLabelFont(prop, "y"));
		valueAxis.setTickLabelFont(getTickLabelFont(prop, "y"));

		CategoryAxis domainAxis;

		if (b3D)
			domainAxis = new CategoryAxis3D(pgets(prop, "xlabel"));
		else
			domainAxis = new CategoryAxis(pgets(prop, "xlabel"));

		domainAxis.setLabelFont(getLabelFont(prop, "x"));
		domainAxis.setTickLabelFont(getTickLabelFont(prop, "x"));

		final int gap = pgeti(prop, "plot_gap", -1);

		if (bRange) {
			parent = new CombinedRangeCategoryPlot(valueAxis);
			parent.setDomainAxis(domainAxis);

			if (gap >= 0)
				((CombinedRangeCategoryPlot) parent).setGap(gap);
		}
		else {
			parent = new CombinedDomainCategoryPlot(domainAxis);
			parent.setRangeAxis(valueAxis);

			if (gap >= 0)
				((CombinedDomainCategoryPlot) parent).setGap(gap);
		}

		final List<CategoryPlot> lCategs = new LinkedList<>();

		final HashMap<String, String> hmModules = new HashMap<>();

		if ((vsModules != null) && (vsModules.length > 0))
			for (final String vsModule : vsModules)
				for (final String vsChart : vsCharts)
					if (vsChart.equals(vsModule)) {
						hmModules.put(vsModule, "");
						break;
					}

		if (hmModules.size() <= 0)
			for (final String vsChart2 : vsCharts)
				hmModules.put(vsChart2, "");

		final String[] vsUnselectModules = request.getParameterValues("unselect_module");
		for (int i = 0; (vsUnselectModules != null) && (i < vsUnselectModules.length); i++)
			hmModules.remove(vsUnselectModules[i]);

		final String sOrientation = pgets(prop, "orientation");
		boolean bVertical = true;

		if ((sOrientation == null) || (sOrientation.trim().length() <= 0) || !sOrientation.trim().toLowerCase().equals("vertical"))
			bVertical = false;

		parent.setOrientation(bVertical ? PlotOrientation.VERTICAL : PlotOrientation.HORIZONTAL);

		final boolean bColapsedLegend = pgetb(prop, "colapsedlegend", true);

		final HashMap<String, String> hmLegends = new HashMap<>();

		final HashMap<String, String> hmSeriesOrig = new HashMap<>(hmSeries);

		hmSeries.clear();

		for (int i = 0; (vsCharts != null) && (i < vsCharts.length); i++)
			try {
				final Properties p2 = Utils.getProperties(sConfDir, vsCharts[i], prop, false);

				final HashMap<String, String> hmTemp = new HashMap<>(hmSeriesOrig);

				final CategoryPlot cp = (CategoryPlot) getCategoryPlot(p2, hmTemp, bColapsedLegend ? hmLegends : new HashMap<String, String>(), bRange, bVertical);

				hmSeries.putAll(hmTemp);

				final int rows = ((DefaultCategoryDataset) cp.getDataset()).getRowCount();
				final int cols = ((DefaultCategoryDataset) cp.getDataset()).getColumnCount();

				final CategoryDataset dataset = cp.getDataset();
				boolean bValid = false;

				if (dataset != null)
					for (int j = 0; j < rows; j++) {
						for (int k = 0; k < cols; k++) {
							final Number n = dataset.getValue(j, k);

							if ((n != null) && (n.doubleValue() > 1E-10)) {
								bValid = true;
								break;
							}
						}
						if (bValid)
							break;
					}

				if (!bValid) {
					hmModules.remove(vsCharts[i]);

					if (vIndividualSeries.size() > 0)
						vIndividualSeries.remove(vIndividualSeries.size() - 1);
				}

				if (hmModules.get(vsCharts[i]) != null)
					lCategs.add(cp);
			}
			catch (final RuntimeException re) {
				System.err.println("RuntimeException building one of the charts: " + re + " (" + re.getMessage() + ")");
			}
			catch (final Exception e) {
				System.err.println("Other Exception building one of the charts: " + e + " (" + e.getMessage() + ")");
			}

		setPlotWeights(parent, lCategs, prop);

		final Iterator<CategoryPlot> itCategs = lCategs.iterator();

		while (itCategs.hasNext()) {
			final DefaultCategoryDataset cd = (DefaultCategoryDataset) (itCategs.next()).getDataset();

			final Iterator<String> it = vTotalSeries.iterator();
			while (it.hasNext()) {
				final String s = it.next();
				if (hmActualSeries.get(s) == null)
					try {
						cd.removeColumn(s);
					}
					catch (final Exception e) {
						log("display#buildCombinedBar: cannot remove data series '" + s + "'", e);
					}
			}
		}

		final DrawingSupplier ds = new DefaultDrawingSupplier(getPaintSeries(prop), DefaultDrawingSupplier.DEFAULT_OUTLINE_PAINT_SEQUENCE, DefaultDrawingSupplier.DEFAULT_STROKE_SEQUENCE,
				DefaultDrawingSupplier.DEFAULT_OUTLINE_STROKE_SEQUENCE, getShapeSeries(prop));

		parent.setDrawingSupplier(ds);

		sTitle = title + (sTitle.length() > 0 ? " - " : "") + sTitle;

		JFreeChart chart = new JFreeChart(title, JFreeChart.DEFAULT_TITLE_FONT, parent, true);

		setChartProperties(chart, prop);

		if (!bRange) {
			final CategoryAxis axis = parent.getDomainAxis();
			// axis.setLabelAngle(bVertical ? VERTICAL_ANGLE : HORIZONTAL_ANGLE);
			axis.setCategoryLabelPositions(bVertical ? CategoryLabelPositions.UP_45 : CategoryLabelPositions.STANDARD);

			axis.setLabelFont(getLabelFont(prop, "x"));
			axis.setTickLabelFont(getTickLabelFont(prop, "x"));
		}
		else {
			final CategoryAxis axis = parent.getDomainAxis();
			axis.setCategoryLabelPositions(bVertical ? CategoryLabelPositions.STANDARD : CategoryLabelPositions.DOWN_45);
			// axis.setLabelAngle(bVertical ? VERTICAL_ANGLE : HORIZONTAL_ANGLE);

			axis.setLabelFont(getLabelFont(prop, "x"));
			axis.setTickLabelFont(getTickLabelFont(prop, "x"));
		}

		if (!pgetb(prop, "legend", true))
			chart.removeLegend();

		int height = 0;

		if (bVertical)
			height = 150 + (hmModules.size() * 170);
		else
			height = 90 + (hmActualSeries.size() * 30);

		height = pgeti(prop, "height", height);
		final int width = pgeti(prop, "width", 800);

		try {
			saveImage(chart, prop, height, width, p, getCacheTimeout());
		}
		catch (final Exception e) {
			e.printStackTrace();
		}

		chart = null;

		final Page pSeries = new Page(sResDir + "display/hist_series.res");
		final Page pSeparate = new Page(sResDir + "display/hist_separate.res");

		displayCategories(vTotalSeries, prop, p, pSeries, pSeparate, hmSeries, hmActualSeries);

		final Page pModules = new Page(sResDir + "display/modules.res");
		final Page pModule = new Page(sResDir + "display/module.res");
		for (int i = 0; (vsCharts != null) && (i < vsCharts.length); i++) {
			final String sName = vsCharts[i];

			pModule.modify("name", sName);

			if ((vsChartDescr == null) || (vsChartDescr.length <= i))
				pModule.modify("descr", sName);
			else
				pModule.modify("descr", vsChartDescr[i]);

			pModule.modify("plot", hmModules.get(sName) != null ? "1" : "0");

			pModules.append(pModule);
		}

		p.append("extra", pModules);

		return p;
	}

	/**
	 * If allowed by the properties file (auto.percents=true by default) then try to
	 * automatically distribute the space between charts based on the axis maximum value.
	 *
	 * @param plot
	 *            the original Plot
	 * @param lWeights
	 *            list of maximum values, should have the same number of values as how many subcharts are contained in the plot
	 * @param lPlots
	 *            list of subplots
	 * @param prop
	 *            .properties' file contents, will be checked for:<br>
	 *            auto.percents: boolean(true): flag to enable automatic distribution of space between subcharts,
	 *            defaults to true for charts with the same domain.<br>
	 *            auto.percents.allow_log: boolean(true): auto switch to logarithmic scaling if the order of magnitude on
	 *            the scales is different<br>
	 *            auto.percents.log: boolean(false): force logarithmic scaling <br>
	 *            auto.percents.min_weight: int(10): minimum weight(~percents) of each subchart <br>
	 *            auto.percents.max_weight: int(90): maximum weight(~percents) of each subchart <br>
	 *            percents: int[]: fixed percents (weights) in case that auto.percents=false
	 *
	 * @since 2006-06-19 / 1.2.64
	 */
	private static void setPlotWeights(final Plot plot, final List<? extends Plot> lPlots, final Properties prop) {
		if ((lPlots == null) || (lPlots.size() == 0))
			return;

		final boolean bAutoPercents = pgetb(prop, "auto.percents", !(plot instanceof CombinedRangeCategoryPlot));

		double dSum = 0;
		double dMin = -1;
		double dMax = -1;

		final int iCount = lPlots.size();

		final double[] dWeights = new double[iCount];

		boolean bAutoDone = false;

		if (bAutoPercents) {
			for (int i = 0; i < iCount; i++) {
				final Plot p = lPlots.get(i);
				ValueAxis va = null;

				if (p instanceof CategoryPlot)
					va = ((CategoryPlot) p).getRangeAxis();
				else
					if (p instanceof XYPlot)
						va = ((XYPlot) p).getRangeAxis();

				dWeights[i] = va != null ? va.getUpperBound() : 50;
			}

			for (int i = 0; i < iCount; i++) {
				final double dW = dWeights[i];

				dSum += dW;

				if ((dW > 1E-20) && ((dMin < 0) || (dW < dMin)))
					dMin = dW;

				if (dMax < dW)
					dMax = dW;
			}

			if ((dSum < 1E-20) || (dMin <= 0) || (dMax < 0))
				bAutoDone = false;
			else {
				if (dMax > 1000) {
					final double factor = dMax / 1000;

					dMin /= factor;
					dMax /= factor;
					dSum /= factor;

					for (int i = 0; i < iCount; i++)
						dWeights[i] /= factor;
				}

				if (dMin < 10) {
					final double factor = 10 / dMin;

					dMin *= factor;
					dMax *= factor;
					dSum *= factor;

					for (int i = 0; i < iCount; i++)
						dWeights[i] *= factor;
				}

				if ((pgetb(prop, "auto.percents.allow_log", true) && (((dMax / dMin) > 15) || ((dSum / dMin) > 20))) || pgetb(prop, "auto.percents.log", false)) {
					dSum = 0;

					for (int i = 0; i < iCount; i++) {
						dWeights[i] = log(dWeights[i]);
						dSum += dWeights[i];
					}
				}

				bAutoDone = true;
			}
		}

		if (!bAutoDone) {
			final String[] vsPercents = Utils.getValues(prop, "percents");

			for (int i = 0; i < iCount; i++) {
				try {
					dWeights[i] = Double.parseDouble(vsPercents[i]);
				}
				catch (@SuppressWarnings("unused") final Exception e) {
					dWeights[i] = 50;
				}

				dSum += dWeights[i];
			}
		}

		final int[] weights = new int[iCount];

		final int iMinWeight = pgeti(prop, "auto.percents.min_weight", 10);
		final int iMaxWeight = pgeti(prop, "auto.percents.max_weight", 90);

		for (int i = 0; i < iCount; i++) {
			weights[i] = (int) ((dWeights[i] * 100) / dSum);

			if (weights[i] < iMinWeight)
				weights[i] = iMinWeight;

			if (weights[i] > iMaxWeight)
				weights[i] = iMaxWeight;
		}

		for (int i = 0; i < iCount; i++)
			if ((plot instanceof CombinedDomainCategoryPlot) || (plot instanceof CombinedRangeCategoryPlot)) {
				final CategoryPlot subplot = (CategoryPlot) lPlots.get(i);

				if (plot instanceof CombinedDomainCategoryPlot)
					((CombinedDomainCategoryPlot) plot).add(subplot, weights[i]);
				else
					if (plot instanceof CombinedRangeCategoryPlot)
						((CombinedRangeCategoryPlot) plot).add(subplot, weights[i]);
			}
			else
				if (plot instanceof CombinedDomainXYPlot) {

					final XYPlot subplot = (XYPlot) lPlots.get(i);

					((CombinedDomainXYPlot) plot).add(subplot, weights[i]);
				}
	}

	private static final double log(final double d) {
		return d > 1 ? Math.log(d) : 1;
	}

	private final void displayCategories(final Vector<String> _vTotalSeries, final Properties prop, final Page p, final Page pSeries, final Page pSeparate, final HashMap<String, String> hmSeries,
			final HashMap<String, String> _hmActualSeries) {
		final boolean bDisplayAll = pgetb(prop, "displayall", true);
		final boolean bSortBySuffix = pgetb(prop, "sort.bysuffix", false);

		final String sSuffixDelimiter = pgets(prop, "sort.bysuffix.delimiter", " .-_");
		final String sSortBySuffixSeparator = pgets(prop, "sort.bysuffix.separator", "<hr size=1 noshade>");

		final Vector<String> vSeparate = toVector(prop, "separate", null);
		boolean bSeparate = false;

		String sOldNormalSuffix = null;
		String sOldSeparateSuffix = null;

		sSeries = "";

		String sSeparate = "";

		final StringBuilder sbSeries = new StringBuilder();

		final Iterator<String> it = _vTotalSeries.iterator();

		final Map<String, List<String>> separateGroups = new LinkedHashMap<>();

		for (int i = 0; i < pgeti(prop, "separate.groups", 0); i++) {
			final String groupname = i + ":" + pgets(prop, "separate.group." + i + ".name");

			separateGroups.put(groupname, toVector(prop, "separate.group." + i + ".members", null));
		}

		final TreeMap<String, StringBuilder> groups = new TreeMap<>();

		while (it.hasNext()) {
			final String sName = it.next();

			if (sbSeries.length() > 0)
				sbSeries.append(',');

			sbSeries.append(sName);

			final String sLabel = getDescr(prop, sName);

			if ((_hmActualSeries.get(sName) != null) || bDisplayAll) {
				pSeries.modify("name", sLabel);
				pSeries.modify("realname", sName);
				pSeries.modify("plot", ((hmSeries.get(sName) != null) || (hmSeries.size() <= 0)) && (_hmActualSeries.get(sName) != null) ? "1" : "0");

				boolean inGroup = false;

				for (final Map.Entry<String, List<String>> entry : separateGroups.entrySet())
					if (entry.getValue().contains(sName)) {
						inGroup = true;

						StringBuilder sb = groups.get(entry.getKey());

						if (sb == null) {
							sb = new StringBuilder();
							groups.put(entry.getKey(), sb);
						}

						pSeries.modify("group", entry.getKey().substring(entry.getKey().indexOf(':') + 1));

						final String sTemp = pSeries.toString();
						sb.append(sTemp);
						sSeries += sTemp + "<BR>";

						break;
					}

				if (!inGroup) {
					pSeries.modify("group", "default");

					if (vSeparate.contains(sName) || vSeparate.contains("^" + sName)) {
						if (bSortBySuffix) {
							final String sNewSuffix = getStringSuffix(sName, sSuffixDelimiter);

							if ((sOldSeparateSuffix != null) && !sOldSeparateSuffix.equals(sNewSuffix)) {
								p.append("separate", sSortBySuffixSeparator);
								sSeparate += sSortBySuffixSeparator;
							}

							sOldSeparateSuffix = sNewSuffix;
						}

						bSeparate = true;

						final String sTemp = pSeries.toString();
						p.append("separate", sTemp);
						sSeparate += sTemp + "<BR>";
					}
					else {
						if (bSortBySuffix) {
							final String sNewSuffix = getStringSuffix(sName, sSuffixDelimiter);

							if ((sOldNormalSuffix != null) && !sOldNormalSuffix.equals(sNewSuffix)) {
								p.append(sSortBySuffixSeparator);
								sSeries += sSortBySuffixSeparator;
							}

							sOldNormalSuffix = sNewSuffix;
						}

						final String sTemp = pSeries.toString();
						p.append(sTemp);
						sSeries += sTemp + "<BR>";
					}
				}
			}
		}

		if (groups.size() > 0)
			for (final Map.Entry<String, StringBuilder> entry : groups.entrySet()) {
				pSeparate.modify("groupname", entry.getKey().substring(entry.getKey().indexOf(':') + 1));
				pSeparate.modify("series", entry.getValue());
				p.append("separate_groups", pSeparate);
			}

		p.comment("com_separate", bSeparate);
		p.comment("com_not_separate", !bSeparate);

		p.comment("com_separate_groups", groups.size() > 0);
		p.comment("com_not_separate_groups", groups.size() == 0);

		p.modify("annotation_series", sbSeries.toString());
		p.modify("annotation_groups", pgets(prop, "annotation.groups", ""));

		sSeries = sSeparate + sSeries;

		fillPageFromProperties(p, prop);
	}

	// ------------------------------------------------------------------------------------------------------------------------------------

	private DataSplitter getDataSplitter(final Vector<monPredicate> vPreds, final Properties prop, final long lCompactInterval) {
		monPredicate vp[];

		if ((vPreds != null) && (vPreds.size() > 0)) {
			vp = new monPredicate[vPreds.size()];
			for (int i = 0; i < vp.length; i++)
				vp[i] = vPreds.get(i);
		}
		else {
			final monPredicate p = new monPredicate("*", "*", "*", -1, -1, new String[] { "*" }, null);
			vp = new monPredicate[] { p };
		}

		return getDataSplitter(vp, prop, lCompactInterval);
	}

	private DataSplitter getDataSplitter(final monPredicate[] vp, final Properties prop, final long lCompactInterval) {
		setMinMax(prop, null);

		long lMinOffset = -lIntervalMin;
		long lMaxOffset = -lIntervalMax;

		if (logTiming())
			logTiming("getDataSplitter initial : " + lMinOffset + " -> " + lMaxOffset);

		if (DataSplitter.bFreeze) {
			final long lNow = NTPDate.currentTimeMillis();

			lMinOffset -= lNow - DataSplitter.lFreezeTime;
			lMaxOffset -= lNow - DataSplitter.lFreezeTime;
		}

		if (lBaseTime > 0) {
			final long lNow = NTPDate.currentTimeMillis();

			lMinOffset = lMinOffset - (lNow - lBaseTime);
			lMaxOffset = lMaxOffset - (lNow - lBaseTime);
		}

		if (logTiming())
			logTiming("getDataSplitter final : " + lMinOffset + " -> " + lMaxOffset);

		for (int i = 0; i < vp.length; i++) {
			vp[i].tmin = lMinOffset;
			vp[i].tmax = lMaxOffset;
		}

		return store.getDataSplitter(vp, lCompactInterval);
	}

	private final static RegularTimePeriod getTableTime(final long time, final TimeZone tz, final long lCompactInterval) {
		return new MySecond(new Date(time), tz, lCompactInterval);
	}

	private final double getSmoothStart(final monPredicate pred, final Properties prop) {
		final monPredicate pt = new monPredicate(pred.Farm, pred.Cluster, pred.Node, pred.tmin, pred.tmax, pred.parameters, pred.constraints);

		pt.tmax = pt.tmin;
		pt.tmin = store.getStartTime() - NTPDate.currentTimeMillis();

		final DataSplitter ds = getDataSplitter(new monPredicate[] { pt }, prop, 6000001);

		final Vector<TimestampedResult> v = ds.get(pt);

		if ((v == null) || (v.size() <= 0))
			return 0;

		double mvalprev = 0;
		double threshold = 0;

		for (int i = 0; i < v.size(); i++) {
			final Result r = (Result) v.get(i);

			if (r.param[0] > mvalprev)
				threshold += (r.param[0] - mvalprev);

			mvalprev = r.param[0];
		}

		return threshold;
	}

	private final JFreeChart buildHistoryChart(final Properties prop, final HashMap<String, String> hmSeries) {
		final String sPageTitle = pgets(prop, "title", "History");
		final XYPlot chartPlot = buildHistoryPlot(prop, hmSeries, null, null);

		final JFreeChart chart = new JFreeChart(sPageTitle, JFreeChart.DEFAULT_TITLE_FONT, chartPlot, true);

		setHistoryChartProperties(chart, prop, null);
		setChartProperties(chart, prop);

		/*
		 * if (pgetb(prop, "areachart", false)) {
		 * int shapes = pgetb(prop, "areachart.shapes", false) ? XYAreaRenderer.AREA_AND_SHAPES : XYAreaRenderer.AREA;
		 *
		 * XYAreaRenderer axyir = pgetb(prop, "areachart.stacked", true) ? new StackedXYAreaRenderer(shapes) : new XYAreaRenderer(shapes);
		 *
		 * chartPlot.setRenderer(axyir);
		 * chartPlot.setSeriesRenderingOrder(SeriesRenderingOrder.FORWARD);
		 * }
		 */

		if (!pgetb(prop, "legend", true))
			chart.removeLegend();

		return chart;
	}

	/*
	 *
	 * ------------------------------------------------------------------------------------------------------
	 *
	 */

	private final Page buildHistoryPage(final Properties prop, final HashMap<String, String> hmSeries) {
		if (logTiming())
			logTiming("buildHistoryPage: enter");

		final Page p = new Page(sResDir + "display/hist.res");

		showOptions(sResDir, prop, p, this);

		p.modify("page", gets("page"));
		setExtraFields(p, prop);

		final int iLog = pgeti(prop, "log", 0);
		final int iErr = pgeti(prop, "err", 1);

		p.modify("log_0", iLog == 0 ? "selected" : "");
		p.modify("log_1", iLog == 1 ? "selected" : "");

		p.modify("err_0", iErr == 0 ? "selected" : "");
		p.modify("err_1", iErr == 1 ? "selected" : "");

		final String sPageTitle = pgets(prop, "title", "History");

		final boolean bTotal = pgetb(prop, "showtotal", false);

		p.comment("com_total", bTotal);

		final boolean bHistogram = pgetb(prop, "histogram_chart", false);

		final JFreeChart chart;

		if (bHistogram)
			chart = buildHistogramChart(prop, hmSeries, p);
		else {
			final XYPlot chartPlot = buildHistoryPlot(prop, hmSeries, p, null);
			chart = new JFreeChart(sPageTitle, JFreeChart.DEFAULT_TITLE_FONT, chartPlot, true);
		}

		if (logTiming())
			logTiming("buildHistoryPage: setting properties");

		setHistoryChartProperties(chart, prop, p);
		setChartProperties(chart, prop);

		final int height = pgeti(prop, "height", 430);
		final int width = pgeti(prop, "width", 800);

		final Page pSeries = new Page(sResDir + "display/hist_series.res");
		final Page pSeparate = new Page(sResDir + "display/hist_separate.res");

		if (logTiming()) {
			logTiming("buildHistoryPage: displayCategories");
			logTiming("buildHistoryPage: lIntervalMin = " + lIntervalMin);
			logTiming("buildHistoryPage: prop(interval.min) = '" + pgets(prop, "interval.min") + "'");
		}

		displayCategories(vTotalSeries, prop, p, pSeries, pSeparate, hmSeries, hmActualSeries);

		if (!pgetb(prop, "legend", true))
			chart.removeLegend();

		if (logTiming())
			logTiming("buildHistoryPage: generating the image");

		try {
			saveImage(chart, prop, height, width, p, getCacheTimeout());
		}
		catch (final Exception e) {
			e.printStackTrace();
		}

		setOption("int", p, prop);
		setOption("sum", p, prop);

		if (logTiming())
			logTiming("buildHistoryPage: complete");

		return p;
	}

	private static final Locale DEFAULT_LOCALE = Locale.getDefault();

	/**
	 * Used in histogram charts, this function returns a proper time wrapper.
	 *
	 * @param lTime
	 *            moment in time
	 * @param lCompactInterval
	 *            desired distance between two points
	 * @param tz
	 *            time zone
	 * @return bin time
	 */
	public static final RegularTimePeriod getTimePeriod(final long lTime, final long lCompactInterval, final TimeZone tz) {
		final Date d = new Date(lTime);

		if (lCompactInterval <= Utils.TIME_MINUTE)
			return new Minute(d, tz, DEFAULT_LOCALE);

		if (lCompactInterval <= Utils.TIME_HOUR)
			return new Hour(d, tz, DEFAULT_LOCALE);

		if (lCompactInterval <= Utils.TIME_DAY)
			return new Day(d, tz, DEFAULT_LOCALE);

		if (lCompactInterval <= Utils.TIME_WEEK)
			return new Week(d, tz, DEFAULT_LOCALE);

		if (lCompactInterval <= Utils.TIME_MONTH)
			return new Month(d, tz, DEFAULT_LOCALE);

		return new Minute(d, tz, DEFAULT_LOCALE);
	}

	private JFreeChart buildHistogramChart(final Properties prop, final HashMap<String, String> hmSeries, final Page p) {
		final XYPlot plot = buildHistogramPlot(prop, hmSeries, p);

		final boolean bSecondAxis = pgetb(prop, "second_axis.enabled", false);

		final String sPageTitle = pgets(prop, "title", "Histogram");

		final JFreeChart jfreechart = new JFreeChart(sPageTitle, JFreeChart.DEFAULT_TITLE_FONT, plot, true);

		sTitle += (sTitle.length() > 0 ? ", " : "") + sPageTitle;

		if (bSecondAxis) {
			jfreechart.removeLegend();

			final XYBarRenderer barRenderer = (XYBarRenderer) plot.getRenderer(0);
			final AbstractXYItemRenderer standardxyitemrenderer1 = (AbstractXYItemRenderer) plot.getRenderer(1);

			final LegendTitle legendtitle = new LegendTitle(barRenderer);
			final LegendTitle legendtitle1 = new LegendTitle(standardxyitemrenderer1);

			legendtitle.setBackgroundPaint(Color.WHITE);
			legendtitle1.setBackgroundPaint(Color.WHITE);

			legendtitle.setFrame(new BlockBorder(Color.BLACK));
			legendtitle1.setFrame(new BlockBorder(Color.BLACK));

			legendtitle.setItemFont(getScalledFont(prop, legendtitle.getItemFont()));
			legendtitle1.setItemFont(getScalledFont(prop, legendtitle.getItemFont()));

			final BlockContainer blockcontainer = new BlockContainer(new BorderArrangement());
			blockcontainer.add(legendtitle, RectangleEdge.LEFT);
			blockcontainer.add(legendtitle1, RectangleEdge.RIGHT);
			blockcontainer.add(new EmptyBlock(2000D, 0.0D));

			final CompositeTitle compositetitle = new CompositeTitle(blockcontainer);
			compositetitle.setPosition(RectangleEdge.BOTTOM);

			jfreechart.addSubtitle(compositetitle);
		}

		return jfreechart;
	}

	/**
	 * create a histogram (bin) chart
	 *
	 * @param prop
	 *            page configuration
	 * @param hmSeries
	 *            object to save the discovered series into
	 * @param p
	 *            wrapper page for the chart
	 * @return a histogram chart
	 */
	private XYPlot buildHistogramPlot(final Properties prop, final HashMap<String, String> hmSeries, final Page p) {
		setMinMax(prop, null);

		final TimeZone tz = getTimeZone(prop);

		final long lCompactInterval = Utils.getCompactInterval(prop, lIntervalMin, lIntervalMax);

		final String[] vsUnselect = request.getParameterValues("unselect");

		final HistoryDataWrapper hdw = new HistoryDataWrapper(prop, vsUnselect, hmSeries);

		long lRecompactInterval = Math.abs(lIntervalMax - lIntervalMin) / pgetl(prop, "histogram.max_points", 100);

		// now let's figure out what is the time frame for a bar
		if (lRecompactInterval <= Utils.TIME_MINUTE)
			lRecompactInterval = Utils.TIME_MINUTE;
		else
			if (lRecompactInterval <= Utils.TIME_HOUR)
				lRecompactInterval = Utils.TIME_HOUR;
			else
				if (lRecompactInterval <= Utils.TIME_DAY)
					lRecompactInterval = Utils.TIME_DAY;
				else
					if (lRecompactInterval <= Utils.TIME_WEEK)
						lRecompactInterval = Utils.TIME_WEEK;
					else
						lRecompactInterval = Utils.TIME_MONTH;

		// prop.setProperty("compact.min_interval", ""+lCompactInterval);
		prop.setProperty("disableerr", "true");

		final DataSplitter ds = getDataSplitter(hdw.selectedPreds, prop, lCompactInterval);

		final long now = ds.getMaxTime();

		final boolean bIntegrateData = pgetb(prop, "history.integrate.enable", false);

		final boolean bExistsOnly = pgetb(prop, "history.exists_only", false);

		final boolean bSecondAxis = pgetb(prop, "second_axis.enabled", false);

		final TimeTableXYDataset ttxyd = new TimeTableXYDataset();

		boolean bSum = bSecondAxis && (pgeti(prop, "sum", 0) == 1);

		final TreeMap<RegularTimePeriod, HashMap<String, Double>> tmSum = bSum ? new TreeMap<>() : null;

		// the second axis, if any
		final TimeSeriesCollection tsc = bSecondAxis ? new TimeSeriesCollection() : null;

		final ArrayList<Paint> alPaintSeries = new ArrayList<>(hdw.series.length);
		final ArrayList<Shape> alShapeSeries = new ArrayList<>(hdw.series.length);

		final Vector<String> vActualLocalSeries = new Vector<>();
		final Vector<String> vActualLocalAliases = new Vector<>();

		long lMinTime = 0;
		long lMaxTime = 0;

		final boolean bForceFullInterval = pgetb(prop, "force.fullinterval", true);

		if (bForceFullInterval) {
			lMinTime = now - lIntervalMin;
			lMaxTime = now - lIntervalMax;
		}

		final String sPageTitle = pgets(prop, "title", "Histogram chart");

		for (int k = 0; k < hdw.series.length; k++) {
			if (!vTotalSeries.contains(hdw.series[k]))
				vTotalSeries.add(hdw.series[k]);

			if (arrayContains(vsUnselect, hdw.series[k]))
				continue;

			if ((hmSeries.size() > 0) && (hmSeries.get(hdw.series[k]) == null))
				continue;

			final String sDownloadSeries = sPageTitle + "@" + hdw.series[k];

			if (tsDownloadSeries != null)
				tsDownloadSeries.add(sDownloadSeries);

			Vector<Result> vs = Utils.toResultVector(ds.get(hdw.preds[k]));

			Utils.filterMultipleSeries(vs, prop, hdw.series[k], true);

			vs = Utils.histogramData(vs, lRecompactInterval, bIntegrateData);

			if (bExistsOnly)
				Utils.booleanSeries(vs, now - lIntervalMin, now - lIntervalMax, (int) ((lIntervalMin - lIntervalMax) / lCompactInterval), prop, true);

			int rezSize = vs.size();

			if (rezSize <= 0)
				continue;

			if (pgetb(prop, "remove_allzero_series", false)) {
				boolean bAllZero = true;

				final Iterator<Result> it = vs.iterator();
				Result r;

				while (it.hasNext()) {
					r = it.next();

					if (r.param[0] > 1E-10) {
						bAllZero = false;
						break;
					}
				}

				if (bAllZero) {
					vs.clear();
					rezSize = -1;
				}
			}

			if (rezSize <= 0)
				continue;

			// if the data is all zero then the integrated series would also be all zero
			if (bIntegrateData)
				Utils.integrateSeries(vs, prop, true, lIntervalMin, lIntervalMax);

			Utils.fixupHistorySeries(vs, prop, lIntervalMin, lIntervalMax, true);

			rezSize = vs.size();
			if (rezSize <= 0)
				continue;

			if (!bForceFullInterval) {
				final Iterator<Result> it = vs.iterator();

				while (it.hasNext()) {
					final Result er = vs.firstElement();

					if (er.time > 0) {
						if ((er.time < lMinTime) || (lMinTime == 0))
							lMinTime = er.time;

						break;
					}
				}

				final ExtendedResult er = (ExtendedResult) vs.lastElement();

				if (er.time > lMaxTime)
					lMaxTime = er.time;
			}

			final String sAlias = getDescr(prop, hdw.series[k]);

			vActualLocalSeries.add(hdw.series[k]);
			vActualLocalAliases.add(sAlias);

			hmActualSeries.put(hdw.series[k], "");

			final TimeSeries ts = bSecondAxis ? new TimeSeries(null, Minute.class) : null;

			ExtendedResult er;
			double dVal;
			double dValPrev = 0;
			for (int j = 0; j < rezSize; j++) {
				er = (ExtendedResult) vs.get(j);

				// for integrated data display the value that was gained in this interval
				if (bIntegrateData) {
					dVal = er.param[0] - dValPrev;
					dValPrev = er.param[0];
				}
				else
					dVal = er.param[0];

				RegularTimePeriod rtp = getTimePeriod(er.time, lRecompactInterval, tz);

				ttxyd.add(rtp, dVal, sAlias);

				if (tmDownloadData != null) {
					if (logTiming())
						logTiming(sDownloadSeries + " : " + er.time + " (" + er.param[0] + ") : " + (rtp.getFirstMillisecond() / 1000) + ", " + (rtp.getMiddleMillisecond() / 1000) + ", "
								+ (rtp.getLastMillisecond() / 1000));

					final Long lTime = Long.valueOf(rtp.getFirstMillisecond() / 1000L);

					HashMap<String, Double> hmValues = tmDownloadData.get(lTime);
					if (hmValues == null) {
						hmValues = new HashMap<>();
						tmDownloadData.put(lTime, hmValues);
					}

					hmValues.put(sDownloadSeries, Double.valueOf(dVal));
				}

				if (ts != null) {
					// this value doesn't need timezone, it's included in the original RTP after which this one is created
					rtp = new Minute(new Date(rtp.getFirstMillisecond() + ((rtp.getLastMillisecond() - rtp.getFirstMillisecond()) / 2)));

					// always the original data, no matter it's integrated or not (it doesn't make much sense to have a second axis with the same data but ...)
					try {
						ts.add(rtp, er.param[0]);

						// if the series already contains data for this time interval then an exception will be thrown
						// if not then we can put this value for the sum series

						if (tmSum != null) {
							HashMap<String, Double> hmTemp = tmSum.get(rtp);
							if (hmTemp == null) {
								hmTemp = new HashMap<>();
								tmSum.put(rtp, hmTemp);
							}

							hmTemp.put(hdw.series[k], Double.valueOf(er.param[0]));
						}
					}
					catch (@SuppressWarnings("unused") final Exception e) {
						// System.err.println("Histogram add secondary axis data : exception : "+e+" ("+e.getMessage()+")");
					}
				}
			}

			alPaintSeries.add(getPaint(prop, hdw.series[k]));

			if (tsc != null) {
				tsc.addSeries(ts);
				alShapeSeries.add(getShape(prop, hdw.series[k]));
			}
		}

		// the sum series is displayed only when there are at least two series with some data
		if (hmActualSeries.size() < 2)
			bSum = false;

		if (bSum && tmSum != null && tsc != null && (tmSum.size() > 0) && (hmActualSeries.size() > 1)) {
			final TimeSeries tmp = new TimeSeries("SUM", Minute.class);

			final Iterator<Map.Entry<RegularTimePeriod, HashMap<String, Double>>> it = tmSum.entrySet().iterator();

			HashMap<String, Double> hmPrev = null;
			Map.Entry<RegularTimePeriod, HashMap<String, Double>> me;
			RegularTimePeriod rtp;
			HashMap<String, Double> hmValues;
			double dValue;
			Iterator<Double> it2;

			while (it.hasNext()) {
				me = it.next();

				rtp = me.getKey();
				hmValues = me.getValue();

				dValue = 0;

				// if we integrate the data then keep the last known value even if it is missing in a certain interval
				// a gap will only appear if there is absolutely no data for that interval
				if (bIntegrateData)
					if (hmPrev != null) {
						// new values override the old ones, previous values missing from the current interval are kept
						hmPrev.putAll(hmValues);
						hmValues = hmPrev;
					}
					else
						hmPrev = hmValues;

				it2 = hmValues.values().iterator();

				while (it2.hasNext())
					dValue += it2.next().doubleValue();

				tmp.add(rtp, dValue);
			}

			tsc.addSeries(tmp);
		}

		if ((lChartsMinTime == 0) || ((lChartsMinTime > lMinTime) && (lMinTime > 0)))
			lChartsMinTime = lMinTime;

		if (lChartsMaxTime < lMaxTime)
			lChartsMaxTime = lMaxTime;

		vIndividualSeries.add(vActualLocalSeries);

		final long lStart = ds.getAbsMin();
		final long lEnd = ds.getAbsMax();

		final ValueAxis domainAxis = Utils.getValueAxis(prop, lStart, lEnd);

		domainAxis.setLabelFont(getLabelFont(prop, "x"));
		domainAxis.setTickLabelFont(getTickLabelFont(prop, "x"));

		final NumberAxis na = new NumberAxis(pgets(prop, "ylabel", null));

		na.setLabelFont(getLabelFont(prop, "y"));
		na.setTickLabelFont(getTickLabelFont(prop, "y"));

		final boolean bInBits = pgetb(prop, "datainbits", pgets(prop, "ylabel", "").toLowerCase().endsWith("bps"));

		final boolean bSize = pgetb(prop, "size", false);

		String sSuffix = pgets(prop, "tooltip.suffix", bSize ? (bInBits ? "bps" : "B") : "");

		final String sSizeIn = pgets(prop, "sizein", "M");

		final NumberFormat nf = new MyNumberFormat(bSize, sSizeIn, sSuffix, bInBits);
		final NumberTickUnit ntu = new NumberTickUnit(1.0, nf);
		na.setTickUnit(ntu, false, false);
		na.setNumberFormatOverride(nf);

		if (pgetb(prop, "number.axis.inverted", false))
			na.setInverted(true);

		final XYBarRenderer barRenderer = new StackedXYBarRenderer();

		final boolean bIgnoreZero = pgetb(prop, "ignorezero", false);

		if (bIntegrateData) {
			String sTemp = sSuffix;
			if (sTemp.endsWith("ps"))
				sTemp = sTemp.substring(0, sTemp.length() - 2);
			else
				if (sTemp.endsWith("/s"))
					sTemp = sTemp.substring(0, sTemp.length() - 2);
				else
					if (sTemp.endsWith("s"))
						sTemp = sTemp.substring(0, sTemp.length() - 1);

			if (sTemp.equals("b"))
				sTemp = "B";

			sSuffix = pgets(prop, "tooltip.suffix.integrated", sTemp);
		}

		final AnnotationCollection ac = Utils.getAnnotationCollection(prop, lStart, lEnd);

		MyXYToolTipGenerator ttg = null;
		MyXYToolTipGenerator ttg2 = null;
		if (pgetb(prop, "tooltips.enabled", true)) {
			ttg = new MyXYToolTipGenerator(bIgnoreZero, bSize, sSizeIn, bInBits, pgets(prop, "tooltip.prefix", ""), sSuffix,
					pgets(prop, "labels.date.format", (lIntervalMin - lIntervalMax) < (1000 * 60 * 60 * 24 * 20) ? "MMM d, HH:mm" : "yyyy, MMM d"));
			ttg.setAlternateSeriesNames(vActualLocalAliases);
			ttg.setAnnotations(ac);

			ttg2 = new MyXYToolTipGenerator(bIgnoreZero, bSize, sSizeIn, bInBits, pgets(prop, "tooltip.prefix.second_axis", pgets(prop, "tooltip.prefix", "")),
					pgets(prop, "tooltip.suffix.second_axis", sSuffix), pgets(prop, "labels.date.format", (lIntervalMin - lIntervalMax) < (1000 * 60 * 60 * 24 * 20) ? "MMM d, HH:mm" : "yyyy, MMM d"));
			ttg2.setAlternateSeriesNames(vActualLocalAliases);
			ttg2.setAnnotations(ac);
		}

		MyXYURLGenerator xyug = null;
		if (pgetb(prop, "urls.enabled", true)) {
			xyug = new MyXYURLGenerator(prop);
			xyug.setSkipEvery2(false);
			xyug.setAlternateSeriesNames(vActualLocalSeries);
		}

		barRenderer.setBaseToolTipGenerator(ttg);
		barRenderer.setURLGenerator(xyug);

		final XYPlot plot = new XYPlot(ttxyd, domainAxis, na, barRenderer);

		Utils.addAnnotations(plot, ac, prop);

		for (int i = 0; i < alPaintSeries.size(); i++)
			barRenderer.setSeriesPaint(i, alPaintSeries.get(i));

		plot.setRenderer(0, barRenderer);

		XYLineAndShapeRenderer standardxyitemrenderer1 = null;

		if (bSecondAxis) {
			final NumberAxis numberaxis = new NumberAxis(pgets(prop, "second_axis.ylabel", null));

			numberaxis.setTickUnit(ntu, false, false);
			numberaxis.setNumberFormatOverride(nf);

			numberaxis.setLabelFont(getLabelFont(prop, "y"));
			numberaxis.setTickLabelFont(getTickLabelFont(prop, "y"));

			if (pgetb(prop, "number.axis.inverted", false))
				numberaxis.setInverted(true);

			standardxyitemrenderer1 = new XYLineAndShapeRenderer();
			standardxyitemrenderer1.setBaseShapesVisible(true);
			plot.setRangeAxis(1, numberaxis);
			plot.setRenderer(1, standardxyitemrenderer1);
			plot.setDataset(1, tsc);
			plot.mapDatasetToRangeAxis(1, 1);

			if (bSum) {
				alPaintSeries.add(getPaint(prop, "SUM"));
				alShapeSeries.add(getShape(prop, "SUM"));
			}

			for (int i = 0; i < alPaintSeries.size(); i++) {
				final Paint paint = alPaintSeries.get(i);
				standardxyitemrenderer1.setSeriesPaint(i, paint);
				standardxyitemrenderer1.setSeriesFillPaint(i, Utils.alterColor(paint, 150));
				standardxyitemrenderer1.setSeriesShape(i, alShapeSeries.get(i));
			}

			standardxyitemrenderer1.setUseFillPaint(true);
			standardxyitemrenderer1.setBaseToolTipGenerator(ttg2);
			standardxyitemrenderer1.setURLGenerator(xyug);
		}

		plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);

		showIntervalSelectionForm(prop, p, now);

		return plot;
	}

	private final String buildImage(final Properties prop, final HashMap<String, String> hmSeries, final StringBuilder sb) throws IOException {

		final String sKind = pgets(prop, "kind", "hist").toLowerCase();

		JFreeChart chart;

		if (sKind.startsWith("pie"))
			chart = buildPieChart(prop);
		else
			if (sKind.startsWith("hist"))
				synchronized (oHistoryLock) {
					chart = buildHistoryChart(prop, hmSeries);
				}
			else
				chart = buildCategoryChart(prop, hmSeries);

		final int height = pgeti(prop, "height", 200);
		final int width = pgeti(prop, "width", 400);

		final ChartRenderingInfo info = new ChartRenderingInfo(null);

		String sImage;

		synchronized (oHistoryLock) {
			sImage = ServletUtilities.saveChartAsPNG(chart, width, height, info, null);

			if (sb != null) {
				final StringWriter sw = new StringWriter();
				final PrintWriter pw = new PrintWriter(sw);

				if (pgetb(prop, "tooltips.enabled", true)) {
					ChartUtilities.writeImageMap(pw, sImage, info, pgetb(prop, "overlib_tooltips", sKind.startsWith("hist") ? false : true));
					pw.flush();
				}

				sb.append(sw.toString());
			}
		}

		registerImageForDeletion(sImage, getCacheTimeout());

		return sImage;
	}

	/**
	 * Compare two annotations based on the end time of them
	 *
	 * @author costing
	 * @since May 7, 2008
	 */
	static final class AnnotationComparator implements Comparator<Annotation>, Serializable {
		private static final long serialVersionUID = -4706502912430252790L;

		/**
		 * compare two annotations based on the end time of them
		 *
		 * @param a1
		 *            first object
		 * @param a2
		 *            second object
		 * @return objects sorted by end time of the event
		 */
		@Override
		public int compare(final Annotation a1, final Annotation a2) {
			final long lDiff = a1.to - a2.to;

			return lDiff > 0 ? 1 : (lDiff < 0 ? -1 : 0);
		}
	}

	private static final Comparator<Annotation> annComparator = new AnnotationComparator();

	/**
	 * requested start time of the data. milliseconds in the past relative to "now"
	 */
	long lIntervalMin = 0;

	/**
	 * requested end time of the data. milliseconds in the past relative to "now"
	 */
	long lIntervalMax = 0;

	/**
	 * reference time to use instead of "now". used with frozen repositories
	 */
	long lBaseTime = 0;

	/**
	 * minimum time from all the displayed charts
	 */
	long lChartsMinTime = 0;

	/**
	 * maximum time from all the displayed charts
	 */
	long lChartsMaxTime = 0;

	private static boolean arrayContains(final String[] array, final String key) {
		if ((array == null) || (array.length == 0))
			return false;

		for (final String element : array)
			if (element.equals(key))
				return true;

		return false;
	}

	private final XYPlot buildHistoryPlot(final Properties prop, final HashMap<String, String> hmSeries, final Page p, final Properties propExtra) {
		if (logTiming())
			logTiming(" -- buildHistoryPlot: enter");

		final int iLog = pgeti(prop, "log", 0);
		final int iErr = pgeti(prop, "err", 1);

		final boolean bIgnoreZero = pgetb(prop, "ignorezero", false);

		boolean bSum = getOption("sum", prop);
		final boolean bInt = getOption("int", prop);

		int i;

		final boolean bArea = pgetb(prop, "areachart", false);

		if (bArea)
			bSum = false;

		final boolean bIntegrateData = pgetb(prop, "history.integrate.enable", false);

		final boolean bAreaFinalPoint = pgetb(prop, "areachart.put_final_point", bIntegrateData);

		final boolean bExistsOnly = pgetb(prop, "history.exists_only", false);

		final boolean bForceFullInterval = pgetb(prop, "force.fullinterval", true);

		final String sPageTitle = pgets(prop, "title", "History");
		final String sYLabel = pgets(prop, "ylabel");

		sTitle += (sTitle.length() > 0 ? ", " : "") + sPageTitle;

		int iCount = 0;
		int iCountExtra = 0;

		setMinMax(prop, propExtra);

		final long lCompactInterval = Utils.getCompactInterval(prop, lIntervalMin, lIntervalMax);

		if (logTiming())
			logTiming(" --   compact interval : " + lCompactInterval + ", min = " + lIntervalMin + ", max = " + lIntervalMax);

		String[] vsUnselect = request.getParameterValues("unselect");

		if (bSum) {
			final Vector<String> vUnselect = toVector(pgets(prop, "displaysum_unselect", "SUM,AVG,_TOTALS_"));

			if (vUnselect.size() > 0) {
				final String[] newUnselect = new String[vUnselect.size() + (vsUnselect != null ? vsUnselect.length : 0)];

				for (int idx = 0; idx < vUnselect.size(); idx++)
					newUnselect[idx] = vUnselect.get(idx);

				if ((vsUnselect != null) && (vsUnselect.length > 0))
					for (int idx = 0; idx < vsUnselect.length; idx++)
						newUnselect[vUnselect.size() + idx] = vsUnselect[idx];

				vsUnselect = newUnselect;
			}
		}

		final HistoryDataWrapper hdw = new HistoryDataWrapper(prop, vsUnselect, hmSeries);

		// if the compact is disabled the sum would not be an intelligent option ...
		if (lCompactInterval == 1)
			bSum = false;

		final DataSplitter ds = getDataSplitter(hdw.selectedPreds, prop, lCompactInterval);

		if (ds == null)
			return null;

		final TimeZone tz = getTimeZone(prop);

		final long now = lBaseTime > 0 ? lBaseTime : ds.getMaxTime();

		final long now_tz = (new Second(new Date(now), tz)).getFirstMillisecond();

		final boolean bSize = pgetb(prop, "size", false);
		final boolean bTotal = pgetb(prop, "showtotal", false);

		final Page pStats = new Page(sResDir + "display/hist_statheader.res");
		final Page pStat = new Page(sResDir + "display/hist_stat.res");

		final Page pAnn = new Page(sResDir + "display/hist_annotations.res");

		pStats.modify("name", sPageTitle);
		pStats.comment("com_total", bTotal);

		double dSumMin = 0, dSumMax = 0, dSumLast = 0, dSumAvg = 0, dSumTotal = 0;

		final HashMap<Long, Double> hmSum = bSum ? new HashMap<>() : null;
		final HashMap<Long, HashMap<String, Double>> hmSumSeen = bSum ? new HashMap<>() : null;

		final long skipNull = pgeti(prop, "skipnull", 0);

		final long lIntegrateTimeBase = ServletExtension.pgetl(prop, "history.integrate.timebase", ServletExtension.pgetb(prop, "totalperminute", false) ? 60 : 1);
		final boolean bPerMin = pgetb(prop, "totalperminute", lIntegrateTimeBase == 60);
		boolean bInBits = pgetb(prop, "datainbits", sYLabel.toLowerCase().endsWith("bps"));

		final String sSizeIn = pgets(prop, "sizein", "M");

		String sMeasure = pgets(prop, "tooltip.suffix", bSize ? (bInBits ? "bps" : "B") : "");

		final String sMeasureOrig = sMeasure;

		if (bIntegrateData) {
			String sTemp = sMeasure;
			if (sTemp.endsWith("ps"))
				sTemp = sTemp.substring(0, sTemp.length() - 2);
			else
				if (sTemp.endsWith("/s"))
					sTemp = sTemp.substring(0, sTemp.length() - 2);
				else
					if (sTemp.endsWith("s"))
						sTemp = sTemp.substring(0, sTemp.length() - 1);

			if (sTemp.equals("b"))
				sTemp = "B";

			sMeasure = pgets(prop, "tooltip.suffix.integrated", sTemp);
		}

		long lMinInterval = 0;

		long lDataMinTime = Long.MAX_VALUE;
		long lDataMaxTime = Long.MIN_VALUE;

		final long lMinTime = bForceFullInterval ? now - lIntervalMin : ds.getAbsMin();
		final long lMaxTime = bForceFullInterval ? now - lIntervalMax : ds.getAbsMax();

		final AnnotationCollection ac = Utils.getAnnotationCollection(prop, lMinTime, lMaxTime);

		final List<Vector<Result>> vsData = new ArrayList<>(hdw.series.length);

		boolean bLog = (iLog == 1) && (pgetb(prop, "disablelog", false) == false);

		if (logTiming())
			logTiming(" -- buildHistoryPlot: data walk 1");

		final Vector<String> vSeparateSeries = toVector(prop, "separate_stats", null);

		final boolean bShowSeparate = pgetb(prop, "separate_stats_show", true);

		for (int k = 0; k < hdw.series.length; k++) {
			vsData.add(null);

			if (!vTotalSeries.contains(hdw.series[k]))
				vTotalSeries.add(hdw.series[k]);

			if (arrayContains(vsUnselect, hdw.series[k]))
				continue;

			if ((hmSeries.size() == 0) || hmSeries.containsKey(hdw.series[k])) {
				double total = 0;

				double max = 0;
				double min = 1;
				double avg = 0;
				long mintime = 0;
				long maxtime = 0;
				double last = 0;

				final Vector<Result> vs = Utils.toResultVector(ds.get(hdw.preds[k]));

				vsData.set(k, vs);

				Utils.filterMultipleSeries(vs, prop, hdw.series[k], true);

				if (bExistsOnly)
					Utils.booleanSeries(vs, now - lIntervalMin, now - lIntervalMax, (int) ((lIntervalMin - lIntervalMax) / lCompactInterval), prop, true);

				int rezSize = vs.size();

				if (pgetb(prop, "remove_allzero_series", false)) {
					boolean bAllZero = true;

					final Iterator<Result> it = vs.iterator();
					Result r;

					while (it.hasNext()) {
						r = it.next();

						if ((r.param[0] > 1E-10) || (r.param[0] < -1E-10)) {
							bAllZero = false;
							break;
						}
					}

					if (bAllZero) {
						vs.clear();
						rezSize = -1;
					}
				}

				// if the data is all zero then the integrated series would also be all zero
				if (bIntegrateData)
					Utils.integrateSeries(vs, prop, true, lIntervalMin, lIntervalMax);

				Utils.fixupHistorySeries(vs, prop, lIntervalMin, lIntervalMax, true);

				if (rezSize > 0) {
					final Iterator<Result> it = vs.iterator();

					mintime = 0;

					while (it.hasNext()) {
						final ExtendedResult er = (ExtendedResult) it.next();

						if (er.time > 0) {
							mintime = er.time;
							break;
						}
					}

					final ExtendedResult er = (ExtendedResult) vs.lastElement();

					maxtime = er.time;

					last = er.param[0];
				}

				if ((mintime < lDataMinTime) && (mintime >= (lMinTime - lCompactInterval)))
					lDataMinTime = mintime;

				if ((maxtime > lDataMaxTime) && (maxtime <= (lMaxTime + lCompactInterval)))
					lDataMaxTime = maxtime;

				double dVal;

				for (int l = 0; l < rezSize; l++) {
					final ExtendedResult er = (ExtendedResult) vs.get(l);

					dVal = er.param[0];

					if (min > max) {
						min = er.min;
						max = er.max;
					}

					if (er.min < min)
						min = er.min;

					if (er.max > max)
						max = er.max;

					avg += dVal;

					if (hmSumSeen != null) {
						Long lTime = Long.valueOf((now - er.time) / lCompactInterval);

						HashMap<String, Double> hmSeen = hmSumSeen.get(lTime);

						final String sKey = IDGenerator.generateKey(er, 0);

						if ((lTime.longValue() == 0L) && (hmSeen != null) && hmSeen.containsKey(sKey)) { // hack for the last points only
							lTime = Long.valueOf(-1);
							hmSeen = hmSumSeen.get(lTime);
						}

						if (hmSeen == null) {
							hmSeen = new HashMap<>();
							hmSumSeen.put(lTime, hmSeen);
						}

						if (!hmSeen.containsKey(sKey) && hmSum != null) {
							// System.err.println(" Sum : add to "+lTime);

							hmSeen.put(sKey, Double.valueOf(dVal));

							Double dSum = hmSum.get(lTime);

							dSum = Double.valueOf(dSum != null ? dSum.doubleValue() + dVal : dVal);

							hmSum.put(lTime, dSum);
						}
						else {
							// System.err.println(" Sum : already seen one value for "+lTime);
						}
					}
				}

				if (rezSize > 0)
					avg = avg / rezSize;
				else
					continue;

				if (bIgnoreZero && (Math.abs(max) < 1E-5))
					continue;

				total = avg * ((maxtime - mintime) / 1000d);

				if (bPerMin)
					total /= 60;

				if (bInBits) {
					total /= 8;

					if (sSizeIn.equals("K"))
						total /= 1.024d;
					if (sSizeIn.equals("M"))
						total /= 1.024d * 1.024d;
					if (sSizeIn.equals("G"))
						total /= 1.024d * 1.024d * 1.024d;
					if (sSizeIn.equals("T"))
						total /= 1.024d * 1.024d * 1.024d * 1.024d;
					if (sSizeIn.equals("P"))
						total /= 1.024d * 1.024d * 1.024d * 1.024d * 1.024d;
				}

				pStat.modify("name", getDescr(prop, hdw.series[k]));
				pStat.modify("realname", hdw.series[k]);

				final double dAbsMax = Math.max(Math.abs(min), Math.abs(max));

				pStat.modify("min", DoubleFormat.size(min, dAbsMax, sSizeIn, bInBits, bSize, sMeasureOrig));
				pStat.modify("avg", DoubleFormat.size(avg, dAbsMax, sSizeIn, bInBits, bSize, sMeasureOrig));
				pStat.modify("max", DoubleFormat.size(max, dAbsMax, sSizeIn, bInBits, bSize, sMeasureOrig));

				pStat.comment("com_total", bTotal);
				pStat.modify("total", DoubleFormat.size(total, dAbsMax, sSizeIn, false, bSize, "B"));

				pStat.modify("last", DoubleFormat.size(last, last, sSizeIn, bInBits, bSize, sMeasureOrig));

				pStat.modify("sorttable_min", Utils.showDouble(min));
				pStat.modify("sorttable_avg", Utils.showDouble(avg));
				pStat.modify("sorttable_max", Utils.showDouble(max));
				pStat.modify("sorttable_last", Utils.showDouble(last));
				pStat.modify("sorttable_total", Utils.showDouble(total));

				final String sSeriesColor = paintToColorString(getPaint(prop, hdw.series[k]));

				pStat.comment("com_seriescolor", sSeriesColor.length() > 0);
				pStat.comment("com_noseriescolor", sSeriesColor.length() == 0);
				pStat.modify("seriescolor", "#" + sSeriesColor);

				final List<Annotation> lAnnotations = ac.getSeriesAnnotations(hdw.series[k]);

				if ((lAnnotations != null) && (lAnnotations.size() > 0)) {
					final Iterator<Annotation> it = lAnnotations.iterator();

					while (it.hasNext()) {
						final Annotation a = it.next();

						final Date dStart = new Date(a.from);
						final Date dEnd = new Date(a.to);

						final String sDateStart = showDottedDate(dStart) + " " + showTime(dStart);
						final String sDateEnd = dEnd.getTime() > now_tz ? "<b><i>continues</i></b>" : showDottedDate(dEnd) + " " + showTime(dEnd);

						pAnn.append("(" + sDateStart + ", " + sDateEnd + ")<br>" + "<font color=#" + Utils.toHex(a.textColor) + "><b>" + a.text + "</b></font><br><br>");
					}

					pStat.modify("annotations", pAnn);
				}

				if (vSeparateSeries.contains(hdw.series[k])) {
					if (bShowSeparate) {
						pStat.comment("com_color0", (iCountExtra % 2) == 0);
						pStat.comment("com_color1", (iCountExtra % 2) != 0);

						iCountExtra++;

						pStat.modify("seriescount", iCountExtra);

						pStat.comment("com_separate", true);

						pStats.append("stat_separate", pStat);
					}
					else
						pStat.toString();
				}
				else {
					pStat.comment("com_color0", (iCount % 2) == 0);
					pStat.comment("com_color1", (iCount % 2) != 0);

					iCount++;

					pStat.modify("seriescount", iCount);

					pStat.comment("com_separate", false);

					pStats.append("stat", pStat);

					dSumTotal += total;
					dSumMin += min;
					dSumMax += max;
					dSumAvg += avg;
					dSumLast += last;
				}
			}
		}

		final boolean bTotalRow = pgetb(prop, "totalrow", false);

		if (bTotalRow) {
			final boolean bTotalAvg = pgetb(prop, "totalrow.average_total", false);

			if ((iCount > 0) && bTotalAvg) {
				dSumAvg /= iCount;
				dSumTotal /= iCount;
				dSumLast /= iCount;
				dSumMin /= iCount;
				dSumMax /= iCount;
			}

			final double dAbsMax = Math.max(Math.abs(dSumMin), Math.abs(dSumMax));

			final Page pFooter = new Page(sResDir + "display/hist_statfooter.res");

			if (bTotalAvg)
				pFooter.modify("min", DoubleFormat.size(dSumMin, dAbsMax, sSizeIn, bInBits, bSize, sMeasure));
			else
				pFooter.modify("min", "");

			pFooter.modify("avg", DoubleFormat.size(dSumAvg, dAbsMax, sSizeIn, bInBits, bSize, sMeasure));

			if (bTotalAvg)
				pFooter.modify("max", DoubleFormat.size(dSumMax, dAbsMax, sSizeIn, bInBits, bSize, sMeasure));
			else
				pFooter.modify("max", "");

			pFooter.comment("com_total", bTotal);
			pFooter.modify("total", DoubleFormat.size(dSumTotal, dAbsMax, sSizeIn, false, bSize, "B"));

			pFooter.modify("last", DoubleFormat.size(dSumLast, dSumLast, sSizeIn, bInBits, bSize, sMeasure));

			pStats.append("stat", pFooter);
		}

		if ((p != null) && pgetb(prop, "show.statistics", true)) {
			final String sTemp = pStats.toString();
			p.append("statistics", sTemp);

			if (pgetb(prop, "stats_per_row", false)) {
				p.append("statistics", "</tr><tr>");
			}
		}

		final CombinedDataset cd = new CombinedDataset();
		final TimeTableXYDataset ttxyd = new TimeTableXYDataset();

		final boolean bError = (iErr == 1) && (bInt == false) && (bIntegrateData == false) && (pgetb(prop, "disableerr", false) == false) && !bArea;

		double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;

		final Vector<String> vLocalActualSeries = new Vector<>();

		long lSkipInterval = lCompactInterval;

		final double dSkipFactor = pgetd(prop, "skipfactor", 1.5);

		if (skipNull == 0) {
			if ((((float) lMinInterval / (float) lCompactInterval) > 1.0) || (Math.abs(dSkipFactor - 1.5) < 1E-10))
				lMinInterval = (long) (lMinInterval * dSkipFactor);

			while (lMinInterval > lSkipInterval)
				lSkipInterval += lCompactInterval;
		}
		else
			if (skipNull > 0)
				lSkipInterval = skipNull * lCompactInterval;
			else
				lSkipInterval = lIntervalMin; // unite everything if skipNull<0

		if (pgetl(prop, "skipinterval", -1) > 0) {
			lSkipInterval = pgetl(prop, "skipinterval", -1) * 1000;

			if (lSkipInterval < lCompactInterval)
				lSkipInterval = lCompactInterval;
		}

		final boolean bGapIfNoData = bArea ? pgetb(prop, "gap_if_no_data.area", !bIntegrateData) : pgetb(prop, "gap_if_no_data", true);

		if (logTiming())
			logTiming(" -- buildHistoryPlot: data walk 2");

		final boolean bShowSeriesAnnotations = pgetb(prop, "annotation.show_series_texts", pgetb(prop, "annotation.show_text", true));

		final List<XYPointerAnnotation> lChartAnnotations = new LinkedList<>();

		final HashSet<Annotation> hsShownAnnotationsStart = new HashSet<>();
		final HashSet<Annotation> hsShownAnnotationsEnd = new HashSet<>();

		if (lDataMaxTime <= 0)
			lDataMaxTime = lMaxTime;

		if (lDataMinTime > lDataMaxTime)
			lDataMinTime = lMinTime;

		if (logTiming()) {
			logTiming("Start : min time=" + lMinTime + ", data min time=" + lDataMinTime);
			logTiming("End   : max time=" + lMaxTime + ", data max time=" + lDataMaxTime);
		}

		final Calendar calendar = Calendar.getInstance();

		for (i = 0; i < hdw.series.length; i++) {
			long lastMatchTime = 0;

			final String sLabel = getDescr(prop, hdw.series[i]);

			final TimeSeries chartSeries = new TimeSeries(sLabel, Second.class);
			final XYSeries errorSeries = new XYSeries(null);

			final Vector<Result> vs = vsData.get(i);

			final int size = vs != null ? vs.size() : -1;

			// logTiming("data size [" + hdw.series[i] + "] : " + vs);

			if (arrayContains(vsUnselect, hdw.series[i]))
				continue;

			if (((hmSeries.get(hdw.series[i]) == null) && (hmSeries.size() > 0))) {
				// System.err.println("Series : "+series[i]+" is disabled");
				// hmActualSeries.put(series[i], "");
			}
			else {
				if (logTiming())
					logTiming("Processing : " + hdw.series[i] + " - " + hdw.preds[i]);

				ExtendedResult er = null;
				Second second;
				double dVal;
				double dPrevValue = 0;

				LinkedList<Annotation> llAnnotationStarts = null;
				LinkedList<Annotation> llAnnotationEnds = null;

				List<Annotation> lSeriesAnnotations;

				Annotation aNextStart = null;
				Annotation aNextEnd = null;

				if (bShowSeriesAnnotations && ((lSeriesAnnotations = ac.getSeriesAnnotations(hdw.series[i])) != null) && (lSeriesAnnotations.size() > 0)) {
					llAnnotationStarts = new LinkedList<>();
					llAnnotationEnds = new LinkedList<>();

					final Iterator<Annotation> it = lSeriesAnnotations.iterator();

					while (it.hasNext()) {
						final Annotation a = it.next();

						llAnnotationStarts.add(a);
						llAnnotationEnds.add(a);
					}

					Collections.sort(llAnnotationEnds, annComparator);

					do
						if (llAnnotationStarts.size() > 0)
							aNextStart = llAnnotationStarts.removeFirst();
						else
							aNextStart = null;
					while ((aNextStart != null) && hsShownAnnotationsStart.contains(aNextStart));

					do
						if (llAnnotationEnds.size() > 0)
							aNextEnd = llAnnotationEnds.removeFirst();
						else
							aNextEnd = null;
					while ((aNextEnd != null) && hsShownAnnotationsEnd.contains(aNextEnd));
				}

				final String sDownloadSeries = sPageTitle + "@" + hdw.series[i];

				if (tsDownloadSeries != null)
					tsDownloadSeries.add(sDownloadSeries);

				for (int k = 0; (vs != null) && (k < size); k++) {
					er = (ExtendedResult) vs.elementAt(k);

					second = new Second(new Date(er.time), tz);

					dVal = er.param[0];

					if (tmDownloadData != null) {
						final Long lTime = Long.valueOf(er.time / 1000L);

						HashMap<String, Double> hmValues = tmDownloadData.get(lTime);
						if (hmValues == null) {
							hmValues = new HashMap<>();
							tmDownloadData.put(lTime, hmValues);
						}

						hmValues.put(sDownloadSeries, Double.valueOf(er.param[0]));
					}

					if (bLog && ((dVal <= 1E-21) || (er.min <= 1E-21) || (er.max <= 1E-21)))
						// disable log when there's at least one value that would mess up the display
						bLog = false;

					if (bError) {
						final long lmilli = second.getFirstMillisecond(calendar);
						// + (minute.getLastMillisecond(Utils.calendar) -
						// minute.getFirstMillisecond(Utils.calendar)) / 2;

						if (max < er.max)
							max = er.max;
						if (min > er.min)
							min = er.min;

						errorSeries.add(lmilli, er.min, false);
						errorSeries.add(lmilli, er.max, false);
						errorSeries.add(lmilli + 2, null, false);
					}
					else {
						if (max < dVal)
							max = dVal;
						if (min > dVal)
							min = dVal;
					}

					if (lastMatchTime == 0) {
						if (er.time >= lMinTime) {
							if (logTiming())
								logTiming("first value : " + er.time + " : " + er.param[0]);

							lastMatchTime = er.time;

							if (pgetb(prop, "first.null", false) && bArea && (lastMatchTime >= (lDataMinTime + lCompactInterval))) {
								final long l = lastMatchTime - lCompactInterval;

								final RegularTimePeriod rtp = getTableTime(l, tz, lCompactInterval);

								if (logTiming()) {
									logTiming("  FIRST NULL VALUE : " + l + " -> " + rtp.getFirstMillisecond() + " -> " + rtp.getLastMillisecond());
									logTiming("  lMinTime=" + lMinTime + ", lDataMinTime=" + lDataMinTime);
								}

								ttxyd.add(rtp, null, sLabel, false);
							}
							else
								if (pgetb(prop, "single.first.point", true) && bArea && (lastMatchTime >= (lDataMinTime + lCompactInterval))
										&& (lastMatchTime < (lDataMinTime + (2 * lCompactInterval)))) {
									final long l = lastMatchTime - lCompactInterval;

									final RegularTimePeriod rtp = getTableTime(l, tz, lCompactInterval);

									if (logTiming()) {
										logTiming("  FIRST SINGLE POINT : " + l + " -> " + rtp.getFirstMillisecond() + " -> " + rtp.getLastMillisecond());
										logTiming("  lMinTime=" + lMinTime + ", lDataMinTime=" + lDataMinTime);
									}

									ttxyd.add(rtp, Double.valueOf(er.param[0]), sLabel, false);
								}
						}
					}
					else {
						// check if there is a gap in the data
						final long lTimeDiff = er.time - lastMatchTime;
						final boolean bGap = lTimeDiff > lSkipInterval;

						if (bArea) {
							final double dValueDiff = er.param[0] - dPrevValue;
							for (long l = lastMatchTime + lCompactInterval; l <= (er.time - lCompactInterval); l += lCompactInterval) {
								final RegularTimePeriod rtp = getTableTime(l, tz, lCompactInterval);

								if (bGap && bGapIfNoData) {
									if (logTiming())
										logTiming("  PAUZA AREA : " + l + " -> " + rtp.getFirstMillisecond() + " -> " + rtp.getLastMillisecond());

									ttxyd.add(rtp, null, sLabel, false);
								}
								else {
									if (logTiming())
										logTiming("  FILL AREA : " + l + " -> " + rtp.getFirstMillisecond() + " -> " + rtp.getLastMillisecond());

									// interpolation between end points in case of artificial gap filling
									ttxyd.add(rtp, Double.valueOf(dPrevValue + ((dValueDiff * (l - lastMatchTime)) / lTimeDiff)), sLabel, false);
								}
							}
						}
						else
							if (bGap && bGapIfNoData) {
								if (logTiming())
									logTiming("  PAUZA POINT : " + lastMatchTime + " -> " + er.time);

								// we have a gap in a point series, just add some null values after the first value and before the last value
								try {
									chartSeries.add(new Second(new Date(lastMatchTime + 1002), tz), null);
								}
								catch (@SuppressWarnings("unused") final Exception e) {
									// ignore
								}

								try {
									chartSeries.add(new Second(new Date(er.time - 1002), tz), null);
								}
								catch (@SuppressWarnings("unused") final Exception e) {
									// ignore
								}
							}

						lastMatchTime = er.time;
					}

					if (logTiming())
						logTiming("  VALUE : " + er.time + " -> " + dVal);

					if (!bArea)
						chartSeries.addOrUpdate(second, dVal);
					else {
						final RegularTimePeriod rtp = getTableTime(er.time, tz, lCompactInterval);
						ttxyd.add(rtp, Double.valueOf(dVal), sLabel, false);
					}

					if ((aNextStart != null) && (er.time >= aNextStart.from)) {
						if (er.time <= aNextStart.to) {
							final XYPointerAnnotation xypointerannotation = new XYPointerAnnotation(aNextStart.text, er.time, dVal, (5 * Math.PI) / 4);
							xypointerannotation.setBaseRadius(35D);
							xypointerannotation.setTipRadius(10D);
							xypointerannotation.setFont(new Font("SansSerif", 0, 9));
							xypointerannotation.setPaint(aNextStart.textColor);
							xypointerannotation.setTextAnchor(TextAnchor.BOTTOM_RIGHT);

							lChartAnnotations.add(xypointerannotation);

							hsShownAnnotationsStart.add(aNextStart);
						}

						do
							if ((llAnnotationStarts != null) && (llAnnotationStarts.size() > 0))
								aNextStart = llAnnotationStarts.removeFirst();
							else
								aNextStart = null;
						while ((aNextStart != null) && hsShownAnnotationsStart.contains(aNextStart));

						// System.err.println("aNextStart = "+aNextStart);
						// System.err.println("HashSet = "+hsShownAnnotationsStart);
					}

					dPrevValue = dVal;
				}

				if (aNextEnd != null)
					for (int k = size - 1; (vs != null) && (k >= 0); k--) {
						er = (ExtendedResult) vs.elementAt(k);

						if (er.time <= aNextEnd.to) {
							if (er.time >= aNextEnd.from) {
								final XYPointerAnnotation xypointerannotation = new XYPointerAnnotation(aNextEnd.text, er.time, er.param[0], (7 * Math.PI) / 4);
								xypointerannotation.setBaseRadius(35D);
								xypointerannotation.setTipRadius(10D);
								xypointerannotation.setFont(new Font("SansSerif", 0, 9));
								xypointerannotation.setPaint(aNextEnd.textColor);
								xypointerannotation.setTextAnchor(TextAnchor.BOTTOM_LEFT);

								lChartAnnotations.add(xypointerannotation);

								hsShownAnnotationsEnd.add(aNextEnd);
							}

							do
								if ((llAnnotationEnds != null) && (llAnnotationEnds.size() > 0))
									aNextEnd = llAnnotationEnds.removeFirst();
								else
									aNextEnd = null;
							while ((aNextEnd != null) && hsShownAnnotationsEnd.contains(aNextEnd));

							if (aNextEnd == null)
								break;
						}
					}

				// add to final
				if (bArea && (lastMatchTime > 0) && (bIntegrateData || bAreaFinalPoint) && (er != null) && (er.param[0] > 0)) {
					while ((lastMatchTime + lCompactInterval) <= (now - lIntervalMax)) {
						lastMatchTime += lCompactInterval;

						final RegularTimePeriod rtp = getTableTime(lastMatchTime, tz, lCompactInterval);
						ttxyd.add(rtp, Double.valueOf(er.param[0]), sLabel, false);
					}

					if (lastMatchTime != lMaxTime) {
						final RegularTimePeriod rtp = getTableTime(lMaxTime, tz, lCompactInterval);
						ttxyd.add(rtp, Double.valueOf(er.param[0]), sLabel, false);
					}
				}
				else
					if (pgetb(prop, "last.null", false) && bArea && (lastMatchTime > 0) && (lastMatchTime <= (lDataMaxTime - lCompactInterval))) {
						final long l = lastMatchTime + lCompactInterval;

						final RegularTimePeriod rtp = getTableTime(l, tz, lCompactInterval);

						if (logTiming()) {
							logTiming("  LAST NULL VALUE : " + l + " -> " + rtp.getFirstMillisecond() + " -> " + rtp.getLastMillisecond());
							logTiming("lMaxTime=" + lMaxTime + ", lDataMaxTime=" + lDataMaxTime);
						}

						ttxyd.add(rtp, null, sLabel, false);
					}
					else
						if (pgetb(prop, "single.final.point", true) && bArea && (lastMatchTime > 0) && (lastMatchTime <= (lDataMaxTime - lCompactInterval))
								&& (lastMatchTime > (lDataMaxTime - (2 * lCompactInterval))) && (er != null)) {
							final long l = lastMatchTime + lCompactInterval;

							final RegularTimePeriod rtp = getTableTime(l, tz, lCompactInterval);

							if (logTiming()) {
								logTiming("  SINGLE LAST VALUE : " + l + " -> " + rtp.getFirstMillisecond() + " -> " + rtp.getLastMillisecond());
								logTiming("lMaxTime=" + lMaxTime + ", lDataMaxTime=" + lDataMaxTime);
							}

							ttxyd.add(rtp, Double.valueOf(er.param[0]), sLabel, false);
						}

				vLocalActualSeries.add(hdw.series[i]);
			}

			if (!bArea) {
				if (chartSeries.getItemCount() <= 0)
					vLocalActualSeries.remove(hdw.series[i]);
				else {
					// we have some data
					hmActualSeries.put(hdw.series[i], "");

					final TimeSeriesCollection tsc = new TimeSeriesCollection();
					tsc.addSeries(chartSeries);

					final XYSeriesCollection xysc = new XYSeriesCollection();
					xysc.addSeries(errorSeries);

					cd.add(tsc);
					cd.add(xysc);
				}
			}
			else {
				int is = -1;

				for (int k = 0; k < ttxyd.getSeriesCount(); k++)
					if (((String) ttxyd.getSeriesKey(k)).equals(sLabel)) {
						is = k;
						break;
					}

				if ((is >= 0) && (ttxyd.getItemCount(is) > 0))
					hmActualSeries.put(hdw.series[i], "");
				else
					vLocalActualSeries.remove(hdw.series[i]);
			}
		}

		final boolean bAverage = pgetb(prop, "average_insteadof_sum", false);

		final boolean bSumAnnotation = pgetb(prop, "displaysum_annotation", true);

		double dAvg = 0;
		int iAvgCount = 0;
		double dMin = 0;
		double dMax = -1;

		final String sSumSeriesName = bAverage ? "AVG" : "SUM";

		if (hmSum != null && (hmSum.size() > 0))
			try {
				final Vector<Long> vTimes = new Vector<>();
				vTimes.addAll(hmSum.keySet());
				Collections.sort(vTimes);
				Collections.reverse(vTimes);

				final TimeSeries chartSeries = new TimeSeries(getDescr(prop, sSumSeriesName), Second.class);

				hmActualSeries.put(sSumSeriesName, "");
				vLocalActualSeries.add(sSumSeriesName);

				final String sDownloadSeries = sPageTitle + "@" + sSumSeriesName;

				if (tsDownloadSeries != null)
					tsDownloadSeries.add(sDownloadSeries);

				Long lTime;
				long time;
				double d;

				int iStart = 1;
				int iEnd = vTimes.size() - 1;

				if (bIntegrateData || pgetb(prop, "sum.endpoints", false)) {
					iStart = 0;
					iEnd = vTimes.size();
				}

				final HashMap<String, Double> hmLastValues = new HashMap<>();

				final boolean bSumGaps = pgetb(prop, "sum.gaps", false);

				for (int j = iStart; j < iEnd; j++) {
					lTime = vTimes.get(j);

					final Double dVal = hmSum.get(lTime);

					if (dVal != null) {
						d = dVal.doubleValue();

						if (bAverage && hmSumSeen != null) {
							final HashMap<String, Double> hmSumFrom = hmSumSeen.get(lTime);

							if ((hmSumFrom != null) && (hmSumFrom.size() > 0))
								d /= hmSumFrom.size();
							else {
								logTiming("Average with no original series?!");
								continue;
							}
						}

						if (bIntegrateData && hmSumSeen != null) {
							final HashMap<String, Double> hmOriginalValues = hmSumSeen.get(lTime);

							final Iterator<Map.Entry<String, Double>> it = hmLastValues.entrySet().iterator();

							while (it.hasNext()) {
								final Map.Entry<String, Double> me = it.next();

								final String sKey = me.getKey();

								if (!hmOriginalValues.containsKey(sKey))
									d += me.getValue().doubleValue();
							}

							hmLastValues.putAll(hmOriginalValues);
						}

						time = (now - (lTime.longValue() * lCompactInterval)) - (lCompactInterval / 2);

						if ((j > 0) && (((vTimes.get(j - 1)).longValue() - lTime.longValue()) > 1))
							if (bSumGaps)
								chartSeries.add(new Second(new Date(time - lCompactInterval), tz), null);

						if (bLog && (d <= 1E-21))
							// disable log when there's at least one value that would mess up the display
							bLog = false;

						if (tmDownloadData != null) {
							lTime = Long.valueOf(time / 1000L);

							HashMap<String, Double> hmValues = tmDownloadData.get(lTime);
							if (hmValues == null) {
								hmValues = new HashMap<>();
								tmDownloadData.put(lTime, hmValues);
							}

							hmValues.put(sDownloadSeries, Double.valueOf(d));
						}

						chartSeries.add(new Second(new Date(time), tz), d);
						max = d > max ? d : max;

						dAvg += d;
						iAvgCount++;

						if (dMax < dMin)
							dMax = dMin = d;
						else {
							dMax = Math.max(dMax, d);
							dMin = Math.min(dMin, d);
						}
					}
				}

				final TimeSeriesCollection tsc = new TimeSeriesCollection();
				final XYSeriesCollection xysc = new XYSeriesCollection();
				final XYSeries errorSeries = new XYSeries(null);

				xysc.addSeries(errorSeries);

				tsc.addSeries(chartSeries);

				cd.add(tsc);
				cd.add(xysc);
			}
			catch (final Throwable t) {
				System.err.println(t + " (" + t.getMessage() + ")");
				t.printStackTrace();
			}

		if (logTiming())
			logTiming(" -- buildHistoryPlot: formatting");

		vIndividualSeries.add(vLocalActualSeries);

		final XYDataset dataset = bArea ? (XYDataset) ttxyd : (XYDataset) cd;

		if (bIntegrateData)
			bInBits = false;

		String sSuffix = pgets(prop, "tooltip.suffix", bSize ? (bInBits ? "bps" : "B") : "");

		if (bIntegrateData) {
			String sTemp = sSuffix;
			if (sTemp.endsWith("ps"))
				sTemp = sTemp.substring(0, sTemp.length() - 2);
			else
				if (sTemp.endsWith("/s"))
					sTemp = sTemp.substring(0, sTemp.length() - 2);
				else
					if (sTemp.endsWith("s"))
						sTemp = sTemp.substring(0, sTemp.length() - 1);

			if (sTemp.equals("b"))
				sTemp = "B";

			sSuffix = pgets(prop, "tooltip.suffix.integrated", sTemp);
		}

		final String sPrefix = "";

		NumberAxis na;

		if (bLog)
			na = new LogarithmicAxis(sYLabel);
		else
			na = new NumberAxis(sYLabel);

		na.setLabelFont(getLabelFont(prop, "y"));
		na.setTickLabelFont(getTickLabelFont(prop, "y"));

		final double dUnit = pgetd(prop, "tick.unit", bSize ? 1.024 : 1.0);

		final NumberFormat nf = new MyNumberFormat(bSize, sSizeIn, sSuffix, dUnit == 1.0);
		final NumberTickUnit ntu = new NumberTickUnit(dUnit, nf);
		na.setTickUnit(ntu, false, false);
		na.setNumberFormatOverride(nf);

		if (pgetb(prop, "number.axis.inverted", false))
			na.setInverted(true);

		MyXYToolTipGenerator ttg = null;
		if (pgetb(prop, "tooltips.enabled", true)) {
			ttg = new MyXYToolTipGenerator(bIgnoreZero, bSize, sSizeIn, bInBits, sPrefix, sSuffix,
					pgets(prop, "labels.date.format", (lIntervalMin - lIntervalMax) < (1000 * 60 * 60 * 24 * 20) ? "MMM d, HH:mm" : "yyyy, MMM d"));
			ttg.setAnnotations(ac);
		}

		MyXYURLGenerator xyug = null;
		if (pgetb(prop, "urls.enabled", true)) {
			xyug = new MyXYURLGenerator(prop);

			final Vector<String> v = new Vector<>(vLocalActualSeries.size() * 2);
			for (int l = 0; l < vLocalActualSeries.size(); l++) {
				v.add(vLocalActualSeries.get(l));
				v.add(vLocalActualSeries.get(l));
			}

			xyug.setAlternateSeriesNames(v);
		}

		final boolean bShowShapes = pgetb(prop, "show.shapes", true);
		final boolean bShowLines = pgetb(prop, "show.lines", true);

		int iOption = StandardXYItemRenderer.SHAPES_AND_LINES;

		if (bShowShapes)
			iOption = bShowLines ? StandardXYItemRenderer.SHAPES_AND_LINES : StandardXYItemRenderer.SHAPES;
		else
			if (bShowLines)
				iOption = StandardXYItemRenderer.LINES;

		XYItemRenderer renderer;

		if (pgetb(prop, "spline_renderer", false)) {
			final XYSplineRenderer spline = new XYSplineRenderer(pgeti(prop, "spline_precision", 5));

			spline.setBaseToolTipGenerator(ttg);
			spline.setURLGenerator(xyug);

			renderer = spline;
		}
		else {
			final StandardXYItemRenderer xyir = new StandardXYItemRenderer(iOption, ttg, xyug);

			xyir.setBaseShapesVisible(bShowShapes);

			xyir.setBaseShapesFilled(pgetb(prop, "shapes.filled", true));

			renderer = xyir;
		}

		String sTimeAxis = pgets(prop, "timeaxis", "GMT Time");
		if (sTimeAxis.length() <= 0)
			sTimeAxis = null;

		if ((lChartsMinTime == 0) || ((lChartsMinTime > lMinTime) && (lMinTime > 0)))
			lChartsMinTime = lMinTime;

		if (lChartsMaxTime < lMaxTime)
			lChartsMaxTime = lMaxTime;

		final ValueAxis valueAxis = Utils.getValueAxis(prop, lChartsMinTime, lChartsMaxTime);

		if (valueAxis != null) {
			valueAxis.setLabelFont(getLabelFont(prop, "x"));
			valueAxis.setTickLabelFont(getTickLabelFont(prop, "x"));
		}

		if (bForceFullInterval && (valueAxis != null)) {
			valueAxis.setAutoRange(false);

			RegularTimePeriod rtp = getTableTime(lChartsMinTime + lCompactInterval, tz, lCompactInterval);
			valueAxis.setLowerBound(rtp.getFirstMillisecond());

			rtp = getTableTime(lChartsMaxTime + lCompactInterval, tz, lCompactInterval);
			valueAxis.setUpperBound(rtp.getLastMillisecond());
		}

		final XYPlot chartPlot = new XYPlot(dataset, valueAxis, na, renderer);

		Utils.addAnnotations(chartPlot, ac, prop);

		final Iterator<XYPointerAnnotation> it = lChartAnnotations.iterator();

		while (it.hasNext()) {
			final XYPointerAnnotation xypa = it.next();

			chartPlot.addAnnotation(xypa);
		}

		if (bSumAnnotation && !bIntegrateData && (iAvgCount > 0)) {
			dAvg /= iAvgCount;

			String sText = pgets(prop, "displaysum_annotation.format", "Avg: %AVG, min: %MIN, max: %MAX");

			sText = Formatare.replace(sText, "%AVG", nf.format(dAvg));
			sText = Formatare.replace(sText, "%MIN", nf.format(dMin));
			sText = Formatare.replace(sText, "%MAX", nf.format(dMax));

			final Paint pSum = getPaint(prop, sSumSeriesName);

			final ValueMarker valuemarker = new ValueMarker(dAvg, pSum, Utils.SINGLE_VALUE_MARKER_STROKE);

			valuemarker.setLabelOffsetType(LengthAdjustmentType.EXPAND);
			valuemarker.setLabel(sText);
			valuemarker.setLabelFont(Utils.ANNOTATION_LABEL_FONT);

			Paint pSumLabel = pSum;

			if (pSumLabel instanceof Color)
				pSumLabel = ((Color) pSumLabel).darker();

			valuemarker.setLabelPaint(pSumLabel);
			valuemarker.setLabelAnchor(RectangleAnchor.TOP_LEFT);
			valuemarker.setLabelTextAnchor(TextAnchor.BOTTOM_LEFT);

			chartPlot.addRangeMarker(valuemarker, Layer.BACKGROUND);
		}

		if (pgetb(prop, "auto_adjust_range", false)) {
			final ValueAxis rangeAxis = chartPlot.getRangeAxis();

			if ((min < 0) && (pgetb(prop, "allow_negative_values", true) == false))
				min = 0;

			if (max < min)
				max = min;

			double diff = (max - min) / 200;

			if (diff < 0.1) {
				diff = 0.1;

				if (max > 10)
					diff = 1;
			}

			if (rangeAxis != null) {
				rangeAxis.setUpperBound(max + diff);

				if ((min - diff) < 0)
					min = min - (diff / 5);
				else
					min = min - diff;

				rangeAxis.setLowerBound(min);
			}
		}

		if (pgetd(prop, "scale.upperbound", 0) == pgetd(prop, "scale.upperbound", -1)) {
			final ValueAxis rangeAxis = chartPlot.getRangeAxis();
			if (rangeAxis != null)
				rangeAxis.setUpperBound(pgetd(prop, "scale.upperbound", 0));
		}

		if (pgetd(prop, "scale.lowerbound", 0) == pgetd(prop, "scale.lowerbound", -1)) {
			final ValueAxis rangeAxis = chartPlot.getRangeAxis();
			if (rangeAxis != null)
				rangeAxis.setLowerBound(pgetd(prop, "scale.lowerbound", 0));
		}

		// chart type

		if (logTiming())
			logTiming(" -- buildHistoryPlot: interval selection form");

		showIntervalSelectionForm(prop, p, now);

		if (bArea) {
			if (pgetb(prop, "areachart.v2", true)) {
				final XYAreaRenderer2 axyir = pgetb(prop, "areachart.stacked", true) ? new StackedXYAreaRenderer2() : new XYAreaRenderer2();

				// axyir.setToolTipGenerator(ttg);
				axyir.setBaseToolTipGenerator(ttg);
				axyir.setURLGenerator(xyug);

				chartPlot.setRenderer(axyir);
			}
			else {
				final int shapes = pgetb(prop, "areachart.shapes", false) ? XYAreaRenderer.AREA_AND_SHAPES : XYAreaRenderer.AREA;

				final XYAreaRenderer axyir = pgetb(prop, "areachart.stacked", true) ? new StackedXYAreaRenderer(shapes) : new XYAreaRenderer(shapes);

				// axyir.setToolTipGenerator(ttg);
				axyir.setBaseToolTipGenerator(ttg);
				axyir.setURLGenerator(xyug);

				chartPlot.setRenderer(axyir);

			}

			chartPlot.setSeriesRenderingOrder(SeriesRenderingOrder.FORWARD);
		}

		if (logTiming())
			logTiming(" -- buildHistoryPlot: finish");

		return chartPlot;
	}

	private final Page buildCombinedHistory(final Properties prop, final HashMap<String, String> hmSeries, final String[] vsModules) {
		try {
			if (logTiming())
				logTiming("buildCombinedHistoryPage: enter");

			final Page p = new Page(sResDir + "display/hist.res");

			showOptions(sResDir, prop, p, this);

			p.modify("page", gets("page"));
			setExtraFields(p, prop);

			final int iLog = pgeti(prop, "log", 0);
			final int iErr = pgeti(prop, "err", 1);

			p.modify("log_0", iLog == 0 ? "selected" : "");
			p.modify("log_1", iLog == 1 ? "selected" : "");

			p.modify("err_0", iErr == 0 ? "selected" : "");
			p.modify("err_1", iErr == 1 ? "selected" : "");

			// ----------------------
			final String[] vsCharts = Utils.getValues(prop, "charts");
			final String[] vsChartDescr = Utils.getValues(prop, "charts.descr");

			final HashMap<String, String> hmModules = new HashMap<>();

			if ((vsModules != null) && (vsModules.length > 0))
				for (final String vsModule : vsModules)
					for (final String vsChart2 : vsCharts)
						if (vsChart2.equals(vsModule)) {
							hmModules.put(vsModule, "");
							break;
						}

			if (hmModules.size() <= 0)
				for (final String vsChart3 : vsCharts)
					hmModules.put(vsChart3, "");

			final String[] vsUnselectModules = request.getParameterValues("unselect_module");
			for (int i = 0; (vsUnselectModules != null) && (i < vsUnselectModules.length); i++)
				hmModules.remove(vsUnselectModules[i]);

			// delay the combined domain plot creation until the first history plot is created so that we know the axis type
			final CombinedDomainXYPlot cdxyp = new MyCombinedDomainXYPlot();

			int gap = pgeti(prop, "plot_gap", -1);

			gap = pgeti(prop, "plot_gap." + (pgetb(prop, "areachart", false) ? "areachart" : "noareachart"), gap);

			if (gap != -1)
				cdxyp.setGap(gap);

			final HashMap<String, String> hmSeriesOrig = new HashMap<>(hmSeries);

			hmSeries.clear();

			final ArrayList<XYPlot> alCharts = new ArrayList<>();

			for (int i = 0; (vsCharts != null) && (i < vsCharts.length); i++)
				if (hmModules.get(vsCharts[i]) != null)
					try {
						final Properties p2 = Utils.getProperties(sConfDir, vsCharts[i], prop, false);

						if (logTiming())
							logTiming("buildCombinedHistoryPage: building plot: " + vsCharts[i]);

						XYPlot xyp;

						final HashMap<String, String> hmTemp = new HashMap<>(hmSeriesOrig);

						if (pgetb(prop, "histogram_chart", false) == false)
							xyp = buildHistoryPlot(p2, hmTemp, p, prop);
						else
							xyp = buildHistogramPlot(p2, hmTemp, p);

						alCharts.add(xyp);

						// if no selected series is found in any of the subcharts then assume everything is selected
						hmSeries.putAll(hmTemp);
					}
					catch (final Throwable t) {
						System.err.println("Cannot add subchart : " + t.getMessage());
						t.printStackTrace();
					}

			setPlotWeights(cdxyp, alCharts, prop);

			// the two long values are set by buildHistoryPlot calls
			final ValueAxis va = Utils.getValueAxis(prop, lChartsMinTime, lChartsMaxTime);
			cdxyp.setDomainAxis(va);

			if (va != null) {
				va.setLabelFont(getLabelFont(prop, "x"));
				va.setTickLabelFont(getTickLabelFont(prop, "x"));
			}

			if ((va != null) && pgetb(prop, "force.fullinterval", true)) {
				va.setAutoRange(false);

				final TimeZone tz = getTimeZone(prop);

				final long lCompactInterval = Utils.getCompactInterval(prop, lChartsMinTime, lChartsMaxTime);

				RegularTimePeriod rtp = getTableTime(lChartsMinTime + lCompactInterval, tz, lCompactInterval);
				va.setLowerBound(rtp.getFirstMillisecond());

				rtp = getTableTime(lChartsMaxTime + lCompactInterval, tz, lCompactInterval);
				va.setUpperBound(rtp.getLastMillisecond());
			}

			final AnnotationCollection ac = Utils.getAnnotationCollection(prop, lChartsMinTime, lChartsMaxTime);

			Utils.addAnnotations(cdxyp, ac, prop);

			final String sPageTitle = pgets(prop, "title", "History");

			if (pgetb(prop, "title.append.subcharts", true))
				sTitle = sPageTitle + (sTitle.length() > 0 ? " - " : "") + sTitle;
			else
				sTitle = sPageTitle;

			if (logTiming())
				logTiming("buildCombinedHistoryPage: creating the chart");

			final JFreeChart chart = new JFreeChart(sPageTitle, JFreeChart.DEFAULT_TITLE_FONT, cdxyp, true);

			if (logTiming())
				logTiming("buildCombinedHistoryPage: setting properties");

			setHistoryChartProperties(chart, prop, p);
			setChartProperties(chart, prop);

			if (!pgetb(prop, "legend", true))
				chart.removeLegend();

			int height = 100 + (hmModules.size() * 300);

			height = pgeti(prop, "height", height);
			final int width = pgeti(prop, "width", 800);

			final Page pSeries = new Page(sResDir + "display/hist_series.res");
			final Page pSeparate = new Page(sResDir + "display/hist_separate.res");

			if (logTiming()) {
				logTiming("buildCombinedHistoryPage: lIntervalMin = " + lIntervalMin);
				logTiming("buildCombinedHistoryPage: prop(interval.min) = '" + pgets(prop, "interval.min") + "'");
			}

			displayCategories(vTotalSeries, prop, p, pSeries, pSeparate, hmSeries, hmActualSeries);

			final Page pModules = new Page(sResDir + "display/modules.res");
			final Page pModule = new Page(sResDir + "display/module.res");

			final StringBuilder sbModules = new StringBuilder("<B>Modules:</B><BR>");
			for (int i = 0; (vsCharts != null) && (i < vsCharts.length); i++) {
				final String sName = vsCharts[i];

				pModule.modify("name", sName);

				if ((vsChartDescr == null) || (vsChartDescr.length <= i))
					pModule.modify("descr", sName);
				else
					pModule.modify("descr", vsChartDescr[i]);

				pModule.modify("plot", hmModules.get(sName) != null ? "1" : "0");

				final String sTemp = pModule.toString();
				pModules.append(sTemp);
				sbModules.append(sTemp).append("<BR>");
			}

			sSeries = sbModules.toString() + "<HR size=1>" + sSeries;

			p.append("extra", pModules);

			if (logTiming())
				logTiming("buildCombinedHistoryPage: generating image");

			try {
				saveImage(chart, prop, height, width, p, getCacheTimeout());
			}
			catch (final Exception e) {
				e.printStackTrace();
			}

			setOption("int", p, prop);
			setOption("sum", p, prop);

			if (logTiming())
				logTiming("buildCombinedHistoryPage: finish");

			return p;
		}
		catch (final Throwable t) {
			System.err.println(t + " : " + t.getMessage());
			t.printStackTrace();
			return null;
		}
	}

	/*
	 * Build the from data-to data selection box
	 */
	private void showIntervalSelectionForm(final Properties prop, final Page p, final long lNowParam) {
		if (p == null)
			return;

		long lNow = lNowParam;

		long lILength = 1000L * 60L * 60L;

		if (pgetb(prop, "intervalselection", false)) {
			String sFormat = "MMM d";

			final int iSelMethod = pgeti(prop, "intervalselection.method", 2);

			if (iSelMethod == 2) {
				lILength = 1000L * 60L * 60L;
				sFormat = pgets(prop, "intervalselection.method2.stringformat", "MMM d, HH:00");
			}
			else
				if (lILength < (1000L * 60L * 60L * 24L))
					sFormat = "MMM d, HH:mm";

			final TimeZone tz = getTimeZone(prop);

			final SimpleDateFormat sdf = new SimpleDateFormat(sFormat);
			sdf.setTimeZone(tz);

			final StringBuilder sb1 = new StringBuilder();
			final StringBuilder sb2 = new StringBuilder();

			long lInterval;

			if (DataSplitter.bFreeze && (iSelMethod == 2))
				lInterval = DataSplitter.lFreezeTime - store.getStartTime();
			else
				lInterval = lNow - store.getStartTime();

			final long lStart = pgetl(prop, "interval.start", 0);
			final long lEnd = pgetl(prop, "interval.end", 0);

			if ((lStart > 0) && (lEnd > 0)) {
				lInterval = lEnd - lStart;
				lNow = lEnd;
			}

			long lTime = (1 + (lInterval / lILength)) * lILength;

			// System.err.println("lInterval="+lInterval+", lILength="+lILength+", lTime="+lTime+", lIntervalMin="+lIntervalMin+", lIntervalMax="+lIntervalMax);

			if (!pgetb(prop, "intervalselection.calendar_based", false)) {
				for (; lTime >= 0; lTime -= lILength) {
					final Date d = new Date(lNow - lTime);

					final String sLabel = sdf.format(d);

					sb1.append("<option value=" + lTime + (lTime == lIntervalMin ? " selected" : "") + ">" + sLabel + "</option>");
					sb2.append("<option value=" + lTime + (lTime == lIntervalMax ? " selected" : "") + ">" + sLabel + "</option>");
				}

				if ((iSelMethod == 2) && (lTime > -lILength)) {
					final Date d = new Date(lNow - lTime);

					final String sLabel = sdf.format(d);

					sb1.append("<option value=0" + (0 == lIntervalMin ? " selected" : "") + ">" + sLabel + "</option>");
					sb2.append("<option value=0" + (0 == lIntervalMax ? " selected" : "") + ">" + sLabel + "</option>");
				}

				p.modify("opt_intervalmin", sb1.toString());
				p.modify("opt_intervalmax", sb2.toString());
			}
			else {
				final SimpleDateFormat sdf2 = new SimpleDateFormat("M/d/yyyy H:00:00");
				sdf2.setTimeZone(tz);

				if (DataSplitter.bFreeze)
					p.modify("current_date_time", sdf2.format(new Date(DataSplitter.lFreezeTime)));
				else
					p.modify("current_date_time", sdf2.format(new Date()));
			}

			p.comment("com_interval", true);
		}
		else
			p.comment("com_interval", false);
	}

	private static boolean getOption(final String s, final Properties prop) {
		return pgetb(prop, "display" + s, false) && pgetb(prop, s, false);
	}

	private static void setOption(final String s, final Page p, final Properties prop) {
		final boolean b = getOption(s, prop);

		boolean bActive = pgetb(prop, "display" + s, false);

		if (s.equals("sum") && (pgetb(prop, "areachart", false) == true))
			bActive = false;

		p.comment("com_" + s, bActive);
		p.modify(s + "_0", !b ? "selected" : "");
		p.modify(s + "_1", b ? "selected" : "");
	}

	/**
	 * Set various chart properties
	 *
	 * @param chart
	 * @param prop
	 */
	public static void setChartProperties(final JFreeChart chart, final Properties prop) {
		final Plot chartPlot = chart.getPlot();

		if (pgetb(prop, "background_image.enabled", false)) {
			final String sURL = pgets(prop, "background_image.url", "");

			final BufferedImage bi = WWTextureLoader.getTexture(sURL);

			if (bi != null)
				if (pgetb(prop, "background_image.apply_to_plot", false)) {
					chartPlot.setBackgroundImage(bi);
					chartPlot.setBackgroundImageAlignment(pgeti(prop, "background_image.position", Align.TOP_RIGHT));
				}
				else {
					chart.setBackgroundImage(bi);
					chart.setBackgroundImageAlignment(pgeti(prop, "background_image.position", Align.TOP_RIGHT));
					chart.setBackgroundImageAlpha((float) pgetd(prop, "background_image.alpha", 1f));
				}
		}

		final float fFA = (float) pgetd(prop, "foreground.alpha", 0.8D);
		final float fBA = (float) pgetd(prop, "background.alpha", 1D);

		// System.err.println("Alphas : "+fFA+"/"+fBA);

		chartPlot.setForegroundAlpha(fFA);
		chartPlot.setBackgroundAlpha(fBA);

		chart.setRenderingHints(Utils.ML_RENDERING_HINTS);

		if (pgetb(prop, "background_paint.ml_gradient", true))
			chart.setBackgroundPaint(Utils.ML_BACKGROUND_PAINT);
		else
			chart.setBackgroundPaint(getColor(prop, "background_paint.color", Color.white));

		final LegendTitle legend = chart.getLegend();

		if (legend != null)
			legend.setItemFont(getScalledFont(prop, legend.getItemFont()));

		final TextTitle title = chart.getTitle();

		if (title != null)
			title.setFont(getScalledFont(prop, title.getFont()));
	}

	/**
	 * Save the plot as image
	 *
	 * @param chart
	 * @param prop
	 * @param defaultHeight
	 * @param defaultWidth
	 * @param p
	 * @param timeout
	 * @return the image file name
	 * @throws IOException
	 */
	public static String saveImage(final JFreeChart chart, final Properties prop, final int defaultHeight, final int defaultWidth, final Page p, final long timeout) throws IOException {
		final LegendTitle legend = chart.getLegend();

		final int chartWidth = pgeti(prop, "width", defaultHeight);
		final int chartHeight = pgeti(prop, "height", defaultWidth);

		int saveChartHeight = chartHeight;
		final int saveChartWidth = chartWidth;

		if (legend != null) {
			final boolean includeLegendHeight = pgetb(prop, "chart.height.includes.legend", true);

			if (!includeLegendHeight) {
				final int constraintH = 1;
				final int constraintW = chartWidth;

				final BufferedImage testImage = new BufferedImage(constraintW, constraintH, BufferedImage.TYPE_INT_RGB);
				final Graphics2D testG2 = testImage.createGraphics();
				final RectangleConstraint cst = new RectangleConstraint(constraintW, new Range(0.0, constraintW), LengthConstraintType.RANGE, constraintH, new Range(0.0, constraintH),
						LengthConstraintType.RANGE);

				final Size2D legendSize = legend.arrange(testG2, cst);

				saveChartHeight = chartHeight + (int) legendSize.getHeight();
			}
		}

		final ChartRenderingInfo info = new ChartRenderingInfo(new StandardEntityCollection());

		final String sImage = ServletUtilities.saveChartAsPNG(chart, saveChartWidth, saveChartHeight, info, null);

		p.modify("image", sImage);
		p.modify("image_width", "" + chartWidth);
		p.modify("image_height", "" + chartHeight);

		if (pgetb(prop, "tooltips.enabled", true)) {
			final StringWriter sw = new StringWriter();
			final PrintWriter pw = new PrintWriter(sw);

			ChartUtilities.writeImageMap(pw, sImage, info, pgetb(prop, "overlib_tooltips", true));
			pw.flush();

			p.modify("map", sw.toString());
		}

		registerImageForDeletion(sImage, timeout);

		return sImage;
	}

	private void setHistoryChartProperties(final JFreeChart chart, final Properties prop, final Page p) {
		final XYPlot chartPlot = (XYPlot) chart.getPlot();

		if (!pgetb(prop, "histogram_chart", false)) {
			final DrawingSupplier ds = new DefaultDrawingSupplier(getPaintSeries(prop), DefaultDrawingSupplier.DEFAULT_OUTLINE_PAINT_SEQUENCE, DefaultDrawingSupplier.DEFAULT_STROKE_SEQUENCE,
					DefaultDrawingSupplier.DEFAULT_OUTLINE_STROKE_SEQUENCE, getShapeSeries(prop));

			chartPlot.setDrawingSupplier(ds);
		}

		final ValueAxis axis = chartPlot.getDomainAxis();

		// the value axis can be either DateAxis or PeriodAxis, depending on the selected interval
		if ((axis != null) && (axis instanceof DateAxis)) {
			final boolean bVertical = pgetb(prop, "labels.vertical", true);

			axis.setVerticalTickLabels(bVertical);

			final String sFormat = pgets(prop, "labels.date.format", (lIntervalMin - lIntervalMax) <= (1000 * 60 * 60 * 24 * 20) ? "MMM d, HH:mm" : "yyyy, MMM d");

			if (sFormat.length() > 0)
				try {
					((DateAxis) axis).setDateFormatOverride(new SimpleDateFormat(sFormat));
				}
				catch (final Exception e) {
					System.err.println(e.toString());
					e.printStackTrace();
				}
		}

		if (p != null) {
			p.comment("com_err", !(pgetb(prop, "disableerr", false) || pgetb(prop, "areachart", false)));
			p.comment("com_log", !pgetb(prop, "disablelog", false));
		}
	}

	private final void showStatistics(final String s) {
		response.setContentType("text/plain");

		pwOut.println("15MinValues\t" + Cache.size());

		final Iterator<WriterConfig> it = ConfigBean.getConfig().iterator();

		pwOut.println("Tables\t" + ConfigBean.getConfig().size());

		while (it.hasNext()) {
			final WriterConfig wc = it.next();

			if (s.equals("full")) {
				pwOut.println(wc.sTableName + "_mode\t" + wc.iWriteMode);
				pwOut.println(wc.sTableName + "_time\t" + wc.iTotalTime);
				pwOut.println(wc.sTableName + "_samples\t" + wc.iSamples);
			}

			final DB db = new DB();

			db.setReadOnly(true);

			db.query("SELECT count(rectime) AS cnt FROM " + wc.sTableName + ";");

			pwOut.println(wc.sTableName + "_records\t" + db.geti(1));
		}

		pwOut.flush();
	}

	private double getPieData(final monPredicate pred, final DataSplitter ds, final String sFunction, final Properties prop, final long lCompactInterval, final String sSeriesName) {
		if (ds == null) {
			final String sTable = pgets(prop, "history.smooth_from_table");

			if ((pgetb(prop, "history.smooth_sets", false) == false) || (sTable.length() <= 0)) {
				final Object oTemp = Cache.getLastValue(pred);

				if ((oTemp != null) && (oTemp instanceof Result))
					return ((Result) oTemp).param[0];
			}
			else
				return getSmoothStart(pred, prop);
		}
		else {
			final Vector<Result> vs = Utils.toResultVector(ds.get(pred));

			Utils.filterMultipleSeries(vs, prop, sSeriesName, true);

			if ((vs == null) || (vs.size() <= 0))
				return -1;

			int iFunc = 0; // average
			if (sFunction.equals("sum"))
				iFunc = 1;
			else
				if (sFunction.equals("min"))
					iFunc = 2;
				else
					if (sFunction.equals("min0"))
						iFunc = 5;
					else
						if (sFunction.equals("max"))
							iFunc = 3;
						else
							if (sFunction.equals("int"))
								iFunc = 4;
							else
								if (sFunction.equals("dif"))
									iFunc = 6;
								else
									if (sFunction.equals("int2"))
										iFunc = 7;
									else
										if (sFunction.equals("last"))
											iFunc = 8;
										else
											if (sFunction.equals("dblast"))
												iFunc = 9;

			double dVal = 0;

			if (iFunc == 5)
				dVal = -1E-10;

			ExtendedResult r;

			if (iFunc == 7)
				Utils.integrateSeries(vs, prop, true, lIntervalMin, lIntervalMax);

			Utils.fixupHistorySeries(vs, prop, lIntervalMin, lIntervalMax, true);

			if ((iFunc != 6) && (iFunc != 7))
				for (int i = 0; i < vs.size(); i++) {
					r = (ExtendedResult) vs.get(i);

					switch (iFunc) {
						case 0: // average
						case 1: // sum
						case 4: // int
							dVal += r.param[0];
							break;
						case 2: // min
							dVal = ((dVal > r.min) || (dVal < 1E-10)) ? r.min : dVal;
							break;
						case 5: // min 0
							dVal = ((dVal > r.min) || (dVal < 0)) ? r.min : dVal;
							break;
						case 3: // max
							dVal = (dVal < r.max) ? r.max : dVal;
							break;
						default:
							break;
					}
				}

			if (iFunc == 7) {
				if (vs.size() > 0)
					dVal = vs.lastElement().param[0];

				if (logTiming())
					logTiming("getPieData : integrate " + vs.size() + " (" + lIntervalMin + ", " + lIntervalMax + ", " + lCompactInterval + ") = " + dVal + " for " + pred);
			}

			if ((iFunc == 0) || (iFunc == 4))
				dVal /= vs.size();

			if (iFunc == 4) {
				// integral series, deprecated, this is not a correct integration method !
				dVal *= (lIntervalMin - lIntervalMax) / 1000d;

				if (pgetb(prop, "totalperminute", false))
					dVal /= 60;
				if (pgetb(prop, "datainbits", false))
					dVal /= 8;
			}

			if (iFunc == 6) {
				dVal = 0;

				if (vs.size() > 1)
					if (pgetb(prop, "history.smooth_sets", false)) {
						double dPrevValue = ((ExtendedResult) vs.get(0)).param[0];
						double d;

						for (int i = 1; i < vs.size(); i++) {
							d = ((ExtendedResult) vs.get(i)).param[0];
							final double diff = d - dPrevValue;
							dPrevValue = d;
							if (diff > 0)
								dVal += diff;
						}
					}
					else {
						final ExtendedResult r0 = (ExtendedResult) vs.get(0);
						r = (ExtendedResult) vs.get(vs.size() - 1);

						dVal = r.param[0] - r0.param[0];
					}
			}

			// last || dblast
			if ((iFunc == 8) || (iFunc == 9))
				if (vs.size() > 0)
					dVal = vs.lastElement().param[0];

			return dVal;
		}

		return -1;
	}

	private JFreeChart buildPieChart(final Properties prop) {
		return buildPieChart(prop, null, new HashMap<String, String>());
	}

	private static final String[] vsPieFunctions = { "avg", "int", "min", "min0", "max", "sum", "dif", "int2", "last", "dblast" };

	private static final String[] vsPieFunctionsDescr = { "Average", "Total", "Min (&gt; 0)", "Min (&gt;= 0)", "Max", "Sum", "Substract", "Integrate", "Last", "Cached last" };

	private JFreeChart buildPieChart(final Properties prop, final Page p, final HashMap<String, String> hmSeries) {
		final Vector<String> vSeries = toVector(prop, "series", null);
		final Vector<String> vAliases = toVector(prop, "aliases", null);

		final DefaultPieDataset dset = new DefaultPieDataset();

		final String sDefaultPred = pgets(prop, "default.pred");

		final Vector<Paint> vColors = new Vector<>();

		final Vector<String> vExplodeSeries = new Vector<>();
		final Vector<Double> vExplodePercents = new Vector<>();

		DataSplitter dsplit = null;

		final boolean bHistoryEnabled = pgetb(prop, "enablehistory", true);

		final boolean bHistory = bHistoryEnabled && !pgets(prop, "pTime", "now").equals("now");

		if (p != null)
			p.comment("com_history", bHistoryEnabled);

		final String sFunction = pgets(prop, "function", "avg").toLowerCase();

		final int iSelMethod = pgeti(prop, "intervalselection.method", 2);

		long lCompactInterval = 0;

		final Iterator<String> it = hmSeries.keySet().iterator();
		final Vector<String> vRemove = new Vector<>();

		while (it.hasNext()) {
			final String sKey = it.next();

			if (!vSeries.contains(sKey))
				vRemove.add(sKey);
		}

		for (int i = 0; i < vRemove.size(); i++)
			hmSeries.remove(vRemove.get(i));

		final HashMap<String, monPredicate> hmPreds = new HashMap<>();

		// build the predicates to select the data
		for (int i = 0; i < vSeries.size(); i++) {
			final String sName = vSeries.get(i);

			if ((hmSeries.size() > 0) && !hmSeries.containsKey(sName))
				continue;

			final String sAlias = pgets(prop, sName + ".alias", vAliases.size() > i ? (String) vAliases.get(i) : sName);

			String sPred = pgets(prop, sName + ".pred", sDefaultPred);

			sPred = replace(sPred, "$NAME", sName);
			sPred = replace(sPred, "$ALIAS", sAlias);
			sPred = replace(sPred, "$COUNT", "" + i);

			hmPreds.put(sName, toPred(sPred));
		}

		if (bHistoryEnabled) {
			if (p != null) {
				if (iSelMethod == 2)
					p.append("opt_ptime", "<option value=history" + (bHistory ? " selected" : "") + ">History</option>");

				if (!pgetb(prop, "realtime.enabled", true))
					p.comment("com_realtime", false);

				int iEnabledCount = 0;
				for (int i = 0; i < vsPieFunctions.length; i++)
					if (pgetb(prop, vsPieFunctions[i] + ".enabled", true)) {
						final String sSel = sFunction.equals(vsPieFunctions[i]) ? " selected" : "";

						p.append("opt_f", "<option value=\"" + vsPieFunctions[i] + "\"" + sSel + ">" + vsPieFunctionsDescr[i] + "</option>");

						iEnabledCount++;
					}

				p.comment("com_function", (iEnabledCount > 1) || !bHistory);
			}

			if (bHistory) {
				setMinMax(prop, null);

				lCompactInterval = Utils.getCompactInterval(prop, lIntervalMin, lIntervalMax);

				final Vector<monPredicate> vPreds = new Vector<>(hmPreds.values());

				if (sFunction.equals("dblast"))
					dsplit = getDBLast(vPreds);
				else
					dsplit = getDataSplitter(vPreds, prop, lCompactInterval);

				showIntervalSelectionForm(prop, p, dsplit.getMaxTime());
			}
		}

		if (p != null)
			p.comment("com_interval", bHistory && !sFunction.equals("dblast"));

		final String sPageTitle = pgets(prop, "title");

		final HashMap<String, Double> downloadData = new HashMap<>();

		for (int i = 0; i < vSeries.size(); i++) {
			final String sName = vSeries.get(i);

			if (!vTotalSeries.contains(sName))
				vTotalSeries.add(sName);

			final monPredicate pred = hmPreds.get(sName);

			if (pred == null)
				continue; // this series is not enabled

			final String sAlias = pgets(prop, sName + ".alias", vAliases.size() > i ? (String) vAliases.get(i) : sName);
			final Color color = (Color) getPaint(prop, sName);

			double dVal = getPieData(pred, dsplit, sFunction, prop, lCompactInterval, sName);

			if (dVal > 0) {
				if (pgetb(prop, sName + ".round_values", pgetb(prop, "default.round_values", false)))
					dVal = Math.round(dVal);

				hmActualSeries.put(sName, "");

				dset.setValue(sAlias, dVal);

				downloadData.put(sPageTitle + "@" + sAlias, Double.valueOf(dVal));

				vColors.add(color);

				if (pgetb(prop, sName + ".explode", false)) {
					vExplodeSeries.add(sAlias);
					vExplodePercents.add(Double.valueOf(pgetd(prop, sName + ".explode.ratio", 0.2D)));
				}
			}
		}

		if (tmDownloadData != null)
			tmDownloadData.put(Long.valueOf(0), downloadData);

		if (tsDownloadSeries != null)
			tsDownloadSeries.addAll(downloadData.keySet());

		final PiePlot plot = pgetb(prop, "3d", true) ? new PiePlot3D(dset) : new PiePlot(dset);

		if (plot instanceof PiePlot3D)
			((PiePlot3D) plot).setDepthFactor(pgetd(prop, "3d.depthfactor", ((PiePlot3D) plot).getDepthFactor()));

		for (int i = 0; i < vExplodeSeries.size(); i++)
			plot.setExplodePercent(vExplodeSeries.get(i), vExplodePercents.get(i).doubleValue());

		plot.setCircular(pgetb(prop, "circular", false), false);
		plot.setInteriorGap(pgetd(prop, "interiorgap", plot.getInteriorGap()));
		plot.setLabelGap(pgetd(prop, "labelgap", plot.getLabelGap()));
		plot.setShadowXOffset(pgetd(prop, "shadow.offset.x", plot.getShadowXOffset()));
		plot.setShadowYOffset(pgetd(prop, "shadow.offset.y", plot.getShadowYOffset()));
		plot.setStartAngle(pgetd(prop, "startangle", plot.getStartAngle()));

		final NumberFormat percentFormat = new java.text.DecimalFormat(pgets(prop, "percent.format", "##.##%"));

		final NumberFormat nf;

		if (pgetb(prop, "piechart.use_default_number_format", false)) {
			final StandardPieSectionLabelGenerator defaultLabelGenerator = new StandardPieSectionLabelGenerator();

			nf = defaultLabelGenerator.getNumberFormat();
		}
		else {
			final boolean bInBits = pgetb(prop, "datainbits", pgets(prop, "ylabel", "").toLowerCase().endsWith("bps"));

			final boolean bSize = pgetb(prop, "size", false);

			final String sSuffix = pgets(prop, "tooltip.suffix", bSize ? (bInBits ? "bps" : "B") : "");

			final String sSizeIn = pgets(prop, "sizein", "M");

			nf = new MyNumberFormat(bSize, sSizeIn, sSuffix, bInBits);
		}

		final StandardPieSectionLabelGenerator myLabelGenerator = new StandardPieSectionLabelGenerator(pgets(prop, "pielabel.format", "{0}: {1}"), nf, percentFormat);

		plot.setLabelGenerator(myLabelGenerator);

		if (!pgetb(prop, "labels", true))
			plot.setLabelGenerator(null);

		sTitle += (sTitle.length() > 0 ? ", " : "") + sPageTitle;

		final JFreeChart chart = new JFreeChart(sPageTitle, JFreeChart.DEFAULT_TITLE_FONT, plot, true);

		final Paint[] vPaints = new Paint[vColors.size()];
		for (int i = 0; i < vColors.size(); i++)
			vPaints[i] = vColors.get(i);

		final DrawingSupplier ds = new DefaultDrawingSupplier(vPaints, DefaultDrawingSupplier.DEFAULT_OUTLINE_PAINT_SEQUENCE, DefaultDrawingSupplier.DEFAULT_STROKE_SEQUENCE,
				DefaultDrawingSupplier.DEFAULT_OUTLINE_STROKE_SEQUENCE, Utils.ML_DEFAULT_SHAPES);

		plot.setDrawingSupplier(ds);

		setChartProperties(chart, prop);

		if (pgetb(prop, "tooltips.enabled", true))
			plot.setToolTipGenerator(new StandardPieToolTipGenerator(pgets(prop, "tooltip.format", StandardPieToolTipGenerator.DEFAULT_TOOLTIP_FORMAT), nf, percentFormat));

		if (!pgetb(prop, "legend", true))
			chart.removeLegend();

		return chart;
	}

	private Page buildPiePage(final Properties prop, final HashMap<String, String> hmSeries) {
		final Page p = new Page(sResDir + "display/pie.res");

		showOptions(sResDir, prop, p, this);

		p.modify("page", gets("page"));

		final Vector<String> vOptionNames = new Vector<>();

		for (int i = 0; i < pgeti(prop, "options", -1); i++)
			if (pgets(prop, "option_" + i + ".name").length() > 0)
				vOptionNames.add(pgets(prop, "option_" + i + ".name"));

		final Enumeration<?> e = request.getParameterNames();
		while (e.hasMoreElements()) {
			final String sKey = (String) e.nextElement();

			if (!sKey.equals("submit_plot") && !sKey.equals("plot_series") && !sKey.equals("pTime") && !sKey.equals("function") && !sKey.startsWith("interval") && !sKey.equals("quick_interval")
					&& !sKey.startsWith("unselect") && !vOptionNames.contains(sKey))
				p.append("parameters", "<input type=hidden name=\"" + escHtml(sKey) + "\" value=\"" + escHtml(gets(sKey)) + "\" />\n");
		}

		final int height = pgeti(prop, "height", 500);
		final int width = pgeti(prop, "width", 800);

		final JFreeChart chart = buildPieChart(prop, p, hmSeries);

		try {
			saveImage(chart, prop, height, width, p, getCacheTimeout());

			final Page pSeries = new Page(sResDir + "display/hist_series.res");
			final Page pSeparate = new Page(sResDir + "display/hist_separate.res");

			displayCategories(vTotalSeries, prop, p, pSeries, pSeparate, hmSeries, hmActualSeries);
		}
		catch (final Exception ex) {
			ex.printStackTrace();
		}

		return p;
	}

	/**
	 * get the shape for a given visual description
	 *
	 * @param sShape
	 *            description (should be actually one char)
	 * @return the shape
	 */
	public static final Shape getShape(final String sShape) {
		if ((sShape != null) && (sShape.length() > 0))
			return getShape(sShape.charAt(0));

		return getShape('@');
	}

	/**
	 * the shapes for the series are defined as characters in the configuration files. this method
	 * transforms a character into a Java Shape object.
	 *
	 * @param c
	 *            configuration character representing a shape
	 * @return the Shape for this character
	 */
	public static final Shape getShape(final char c) {
		switch (c) {
			case '^':
				return Utils.SHAPE_TRI_UP;
			case 'v':
				return Utils.SHAPE_TRI_DW;
			case '|':
				return Utils.SHAPE_LINE;
			case '-':
				return Utils.SHAPE_TICK;
			case '_':
				return Utils.SHAPE_LONGTICK;
			case '#':
				return Utils.SHAPE_RECT;
			case '+':
				return Utils.SHAPE_MEDRECT;
			case '=':
				return Utils.SHAPE_BIGRECT;
			case '.':
				return Utils.SHAPE_POINT;
			case '*':
				return Utils.SHAPE_STAR;
			case 'O':
				return Utils.SHAPE_BIGCIRCLE;
			case '0':
				return Utils.SHAPE_MEDCIRCLE;
			case 'o':
				return Utils.SHAPE_CIRCLE;
			case '@':
			default:
				return Utils.SHAPE_SMALLCIRCLE;
		}
	}

	/**
	 * Get the shape for a given series
	 *
	 * @param prop
	 *            configuration properties
	 * @param sName
	 *            name of the series
	 * @return the shape, according to the configuration
	 */
	public static final Shape getShape(final Properties prop, final String sName) {
		return getShape(pgets(prop, sName + ".shape", pgets(prop, "default.shape", ABPing.sDefaultShape)));
	}

	private Shape[] getShapeSeries(final Properties prop) {
		if (pgetb(prop, "areachart", false))
			return new Shape[] { Utils.SHAPE_BIGRECT };

		if (!pgetb(prop, "legend.display_custom_shapes", true))
			return Utils.ML_DEFAULT_SHAPES;

		final Vector<Shape> vShapeSeries = new Vector<>();

		for (int i = 0; i < vIndividualSeries.size(); i++) {
			final Vector<String> vLocalActualSeries = vIndividualSeries.get(i);

			for (int j = 0; j < vLocalActualSeries.size(); j++) {
				final String sName = vLocalActualSeries.get(j);

				vShapeSeries.add(getShape(prop, sName));
			}
		}

		if (vShapeSeries.size() == 0)
			vShapeSeries.add(Utils.SHAPE_CIRCLE); // at least one series

		Shape[] shape_series;
		if (!pgets(prop, "page").equals("rt") && !pgets(prop, "page").equals("combined_bar")) {
			shape_series = new Shape[vShapeSeries.size() * 2];
			for (int i = 0; i < vShapeSeries.size(); i++) {
				shape_series[i * 2] = vShapeSeries.get(i);
				shape_series[(i * 2) + 1] = Utils.SHAPE_TICK;
			}
		}
		else {
			final String sCustomShapeSeries = pgets(prop, "rt_custom_shapes");

			if (sCustomShapeSeries.length() > 0) {
				vShapeSeries.clear();

				for (int i = 0; i < sCustomShapeSeries.length(); i++) {
					final char c = sCustomShapeSeries.charAt(i);
					vShapeSeries.add(getShape(c));
				}
			}

			shape_series = new Shape[vShapeSeries.size()];
			for (int i = 0; i < vShapeSeries.size(); i++)
				shape_series[i] = vShapeSeries.get(i);
		}

		return shape_series;
	}

	private static String paintToColorString(final Paint p) {
		if (p instanceof Color)
			return Utils.toHex((Color) p);

		return null;
	}

	private Paint getPaint(final Properties prop, final String sName) {
		// the hash code provides a better way to assign colors, to be the same default color no matter the chart structure
		return getColor(prop, sName + ".color", (Color) Utils.getDefaultPaint(sName, default_paint_sequence));
	}

	private Paint[] getPaintSeries(final Properties prop) {
		final Vector<Paint> vPaintSeries = new Vector<>();

		final String sPageType = pgets(prop, "page", "rt").toLowerCase();
		final String sKind = pgets(prop, "kind", "rt").toLowerCase();

		// double the series colors in case of history pages that are not areacharts
		final boolean bDoubleSeries = ((sPageType.indexOf("hist") >= 0) || (sPageType.equals("image") && (sKind.indexOf("hist") >= 0))) && (pgetb(prop, "areachart", false) == false);

		for (int i = 0; i < vIndividualSeries.size(); i++) {
			final Vector<String> vLocalActualSeries = vIndividualSeries.get(i);

			final Iterator<String> it = vLocalActualSeries.iterator();

			while (it.hasNext()) {
				final String sSeriesName = it.next();

				final Paint p = getPaint(prop, sSeriesName);

				vPaintSeries.add(p);
			}
		}

		Paint[] paint_series;

		if (bDoubleSeries) {
			paint_series = new Paint[vPaintSeries.size() * 2];
			for (int i = 0; i < vPaintSeries.size(); i++) {
				paint_series[i * 2] = vPaintSeries.get(i);
				paint_series[(i * 2) + 1] = vPaintSeries.get(i);
			}
		}
		else {
			paint_series = new Paint[vPaintSeries.size()];
			for (int i = 0; i < vPaintSeries.size(); i++)
				paint_series[i] = vPaintSeries.get(i);
		}

		return paint_series;
	}

	/**
	 * Show the extra page options
	 *
	 * @param sResDir
	 *            path to the folder holding the html templates
	 * @param prop
	 *            configuration of the current page
	 * @param p
	 *            current wrapper page
	 * @param servlet
	 *            current servlet that is executed (this method is static to be used from other servlets too).
	 */
	public static void showOptions(final String sResDir, final Properties prop, final Page p, final ServletExtension servlet) {
		final int iOptionsCount = pgeti(prop, "options", 1);

		if (iOptionsCount <= 0)
			return;

		final Page pOpt = new Page(sResDir + "display/option.res");

		for (int i = 0; i < iOptionsCount; i++) {
			final String sName = pgets(prop, "option_" + i + ".name");

			if (sName.equals("SEPARATOR")) {
				final Page pSep = new Page(sResDir + "display/separator.res");

				pSep.modify("width", pgets(prop, "option_" + i + ".width", "20"));
				pSep.modify("text", pgets(prop, "option_" + i + ".text", "&nbsp;|&nbsp;"));
				pSep.modify("align", pgets(prop, "option_" + i + ".align", "center"));

				p.append("options", pSep);

				continue;
			}

			final String sDescr = pgets(prop, "option_" + i + ".descr", sName);
			final Vector<String> vVals = toVector(prop, "option_" + i + ".values", null);
			final Vector<String> vAlias = toVector(prop, "option_" + i + ".aliases", null);
			final String sExtra = pgets(prop, "option_" + i + ".extra", "");
			final boolean bAll = pgetb(prop, "option_" + i + ".show_all", false);

			if ((sName.length() > 0) && (vVals.size() > 0)) {
				final String sVal = servlet.gets(sName, pgets(prop, sName));

				pOpt.modify("name", sName);
				pOpt.modify("descr", sDescr);
				pOpt.modify("extra", sExtra);

				if (bAll) {
					final StringBuilder sb = new StringBuilder();

					for (int j = 0; j < vVals.size(); j++) {
						final String s = vVals.get(j);

						if (sb.length() > 0)
							sb.append(',');

						sb.append(s);
					}

					vVals.add(0, sb.toString());
					vAlias.add(0, "- All -");
				}

				for (int j = 0; j < vVals.size(); j++) {
					final String s = vVals.get(j);

					String sAlias = vAlias.size() > j ? (String) vAlias.get(j) : s;

					sAlias = getDescr(prop, s, sAlias);

					pOpt.append("values", "<option value=\"" + escHtml(s) + "\"" + (s.equals(sVal) ? " selected" : "") + ">" + escHtml(sAlias) + "</option>");

					if (s.equals(sVal))
						prop.setProperty(sName + ".alias", vAlias.size() > j ? (String) vAlias.get(j) : s);
				}

				p.append("options", pOpt);
			}
		}
	}

	private static final void setExtraFields(final Page p, final Properties prop) {
		final Vector<String> vFields = toVector(prop, "pass_fields", null);

		for (int i = 0; (vFields != null) && (i < vFields.size()); i++) {
			final String sKey = vFields.get(i);
			final String sVal = pgets(prop, sKey, "");

			p.append("extra_fields", "<input type=hidden name=\"" + escHtml(sKey) + "\" value=\"" + sVal + "\">\n");
		}
	}

	private static final double LOG10 = Math.log(10d);

	private static final double log10(final double x) {
		return Math.log(x) / LOG10;
	}

	/**
	 * Get the alias for a series in a way that allows dynamic generation of aliases, for example
	 * as results of a query that includes the full name.
	 *
	 * @param prop
	 *            configation properties
	 * @param sName
	 *            series name
	 * @return series alias
	 */
	public static final String getDescr(final Properties prop, final String sName) {
		return getDescr(prop, sName, sName);
	}

	/**
	 * Get the alias for a series in a way that allows dynamic generation of aliases, for example
	 * as results of a query that includes the full name.
	 *
	 * @param prop
	 *            configation properties
	 * @param sName
	 *            series name
	 * @param sDefault
	 *            default alias
	 * @return series alias
	 */
	public static final String getDescr(final Properties prop, final String sName, final String sDefault) {
		String sDefaultDescr = pgets(prop, "default.descr", sDefault, false);

		sDefaultDescr = Formatare.replace(sDefaultDescr, "$NAME", sName);

		sDefaultDescr = parseOption(prop, "default.descr", sDefaultDescr, sDefaultDescr, true, true);

		String sAlias = pgets(prop, sName + ".descr", sDefaultDescr, false);

		sAlias = Formatare.replace(sAlias, "$NAME", sName);

		sAlias = parseOption(prop, sName + ".descr", sAlias, sAlias, true, true);

		return sAlias;
	}
}
