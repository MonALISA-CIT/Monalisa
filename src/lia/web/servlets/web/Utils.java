package lia.web.servlets.web;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Paint;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.PeriodAxis;
import org.jfree.chart.axis.PeriodAxisLabelInfo;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Minute;
import org.jfree.data.time.Month;
import org.jfree.data.time.Second;
import org.jfree.data.time.Week;
import org.jfree.ui.Layer;
import org.jfree.ui.LengthAdjustmentType;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.RectangleInsets;
import org.jfree.ui.TextAnchor;

import lazyj.Format;
import lazyj.cache.ExpirationCache;
import lia.Monitor.DataCache.DataSelect;
import lia.Monitor.Store.Fast.IDGenerator;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ExtendedResult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.web.utils.Annotation;
import lia.web.utils.Annotations;
import lia.web.utils.ColorFactory;
import lia.web.utils.ServletExtension;
import lia.web.utils.ThreadedPage;

/**
 * Helper class for servlets
 *
 * @author costing
 * @since forever
 */
public final class Utils {

	private static final Logger logger = Logger.getLogger(Utils.class.getName());

	/**
	 * Small horizontal line
	 */
	public static final Shape SHAPE_TICK = new Rectangle2D.Double(-2.5, -0.5, 5, 1);

	/**
	 * Long horizontal line
	 */
	public static final Shape SHAPE_LONGTICK = new Rectangle2D.Double(-6, -1.5, 12, 3);

	/**
	 * Small circle (1.5 px radius)
	 */
	public static final Shape SHAPE_SMALLCIRCLE = new Ellipse2D.Double(-1.5, -1.5, 3, 3);

	/**
	 * Circle (2.5 px radius)
	 */
	public static final Shape SHAPE_CIRCLE = new Ellipse2D.Double(-2.5, -2.5, 5, 5);

	/**
	 * Bigger circle (3.5 px radius)
	 */
	public static final Shape SHAPE_MEDCIRCLE = new Ellipse2D.Double(-3.5, -3.5, 7, 7);

	/**
	 * Very big circle (4.5 px radius)
	 */
	public static final Shape SHAPE_BIGCIRCLE = new Ellipse2D.Double(-4.5, -4.5, 9, 9);

	/**
	 * Triangle up
	 */
	public static final Shape SHAPE_TRI_UP = new Polygon(new int[] { -4, 4, 0 }, new int[] { 4, 4, -4 }, 3);

	/**
	 * Triangle down
	 */
	public static final Shape SHAPE_TRI_DW = new Polygon(new int[] { -4, 4, 0 }, new int[] { -3, -3, 5 }, 3);

	/**
	 * Vertical line
	 */
	public static final Shape SHAPE_LINE = new Rectangle2D.Double(-1, -5, 1, 10);

	/**
	 * Rectangle (6x6 px)
	 */
	public static final Shape SHAPE_RECT = new Rectangle2D.Double(-3, -3, 6, 6);

	/**
	 * Big rectangle (9x9 px)
	 */
	public static final Shape SHAPE_BIGRECT = new Rectangle2D.Double(-4.5, -4.5, 9, 9);

	/**
	 * 7x7 px rectangle
	 */
	public static final Shape SHAPE_MEDRECT = new Rectangle2D.Double(-3.5, -3.5, 7, 7);

	/**
	 * Just a point (1x1 px)
	 */
	public static final Shape SHAPE_POINT = new Rectangle2D.Double(0, 0, 1, 1);

	/**
	 * Weird shape
	 */
	public static final Shape SHAPE_STAR = new Polygon(new int[] { 0, 1, 3, 1, 3, 0, -3, -1, -3, -1 }, new int[] { 3, 1, 1, 0, -3, -1, -3, 0, 1, 1 }, 10);

	/**
	 * Default shape series
	 */
	static final Shape[] ML_DEFAULT_SHAPES = new Shape[] { SHAPE_CIRCLE, SHAPE_TICK };

	/**
	 * ML trademark gradient on the back of the charts
	 */
	public static final Paint ML_BACKGROUND_PAINT = new GradientPaint(0, 0, Color.white, 0, 2000, Color.blue);

	/**
	 * Default paint series. Will be built in a static block later.
	 */
	static final Paint[] ML_DEFAULT_PAINT;

	/**
	 * Various rendering settings for the images
	 */
	public static final RenderingHints ML_RENDERING_HINTS;

	/**
	 * Default paint series
	 */
	static final Paint[] DEFAULT_PAINT_SEQUENCE = { ColorFactory.getColor(255, 0, 0), ColorFactory.getColor(255, 60, 60), ColorFactory.getColor(255, 120, 120), ColorFactory.getColor(255, 180, 180),
			ColorFactory.getColor(255, 140, 0), ColorFactory.getColor(255, 200, 60), ColorFactory.getColor(255, 255, 0), ColorFactory.getColor(255, 255, 80), ColorFactory.getColor(255, 255, 160),
			ColorFactory.getColor(0, 255, 0), ColorFactory.getColor(60, 255, 0), ColorFactory.getColor(120, 255, 0), ColorFactory.getColor(180, 255, 0), ColorFactory.getColor(0, 255, 255),
			ColorFactory.getColor(120, 255, 255), ColorFactory.getColor(0, 0, 255), ColorFactory.getColor(60, 60, 255), ColorFactory.getColor(120, 120, 255), ColorFactory.getColor(180, 180, 255),
			ColorFactory.getColor(255, 0, 255), ColorFactory.getColor(255, 120, 255), ColorFactory.getColor(230, 230, 230), ColorFactory.getColor(200, 200, 200), ColorFactory.getColor(170, 170, 170),
			ColorFactory.getColor(140, 140, 140), ColorFactory.getColor(110, 110, 110), ColorFactory.getColor(80, 80, 80), ColorFactory.getColor(0, 0, 0), ColorFactory.getColor(180, 0, 0),
			ColorFactory.getColor(180, 60, 60), ColorFactory.getColor(180, 120, 120), ColorFactory.getColor(0, 180, 0), ColorFactory.getColor(60, 180, 60), ColorFactory.getColor(120, 180, 120),
			ColorFactory.getColor(0, 0, 180), ColorFactory.getColor(60, 60, 180), ColorFactory.getColor(120, 120, 180), ColorFactory.getColor(180, 140, 0), ColorFactory.getColor(106, 13, 162),
			ColorFactory.getColor(49, 147, 150), ColorFactory.getColor(142, 145, 69), ColorFactory.getColor(137, 97, 74) };

	/**
	 * Minute length, in millis
	 */
	public static final long TIME_MINUTE = 1000 * 60;

	public static Paint getDefaultPaint(final String seriesName) {
		return getDefaultPaint(seriesName, DEFAULT_PAINT_SEQUENCE);
	}

	public static Paint getDefaultPaint(final String seriesName, final Paint[] paintSequence) {
		int colorIndex = seriesName.hashCode();

		if (colorIndex < 0)
			colorIndex *= -1;

		return paintSequence[colorIndex % paintSequence.length];
	}

	/**
	 * Hour length, in millis
	 */
	public static final long TIME_HOUR = TIME_MINUTE * 60;

	/**
	 * Day length, in millis
	 */
	public static final long TIME_DAY = TIME_HOUR * 24;

	/**
	 * Week length, in millis
	 */
	public static final long TIME_WEEK = TIME_DAY * 7;

	/**
	 * 30 days month length, in millis
	 */
	public static final long TIME_MONTH = TIME_DAY * 30;

	static {
		ML_DEFAULT_PAINT = new Paint[DEFAULT_PAINT_SEQUENCE.length * 2];

		for (int i = 0; i < DEFAULT_PAINT_SEQUENCE.length; i++) {
			ML_DEFAULT_PAINT[i * 2] = DEFAULT_PAINT_SEQUENCE[i];
			ML_DEFAULT_PAINT[(i * 2) + 1] = DEFAULT_PAINT_SEQUENCE[i];
		}

		ML_RENDERING_HINTS = new RenderingHints(null);

		ML_RENDERING_HINTS.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		ML_RENDERING_HINTS.put(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
		ML_RENDERING_HINTS.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		ML_RENDERING_HINTS.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		ML_RENDERING_HINTS.put(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
		ML_RENDERING_HINTS.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		ML_RENDERING_HINTS.put(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
	}

	/**
	 * Get the contents of a configuration file from a base configuration folder
	 *
	 * @param sConfDir
	 * @param sFile
	 * @return the configuration
	 */
	public static Properties getProperties(final String sConfDir, final String sFile) {
		return getProperties(sConfDir, sFile, null);
	}

	/**
	 * Get the contents of a configuration file from a base configuration folder, with an inherited configuration
	 *
	 * @param sConfDir
	 * @param sFile
	 * @param pOld
	 * @return the configuration
	 */
	public static Properties getProperties(final String sConfDir, final String sFile, final Properties pOld) {
		return getProperties(sConfDir, sFile, pOld, true);
	}

	/**
	 * Get the contents of a configuration file from a base configuration folder, with an inherited configuration
	 *
	 * @param sConfDir
	 * @param sFileName
	 * @param pOld
	 * @param includeDefault
	 * @return the configuration
	 */
	public static Properties getProperties(final String sConfDir, final String sFileName, final Properties pOld, final boolean includeDefault) {
		Properties prop = new Properties();

		final Vector<String> vFiles = new Vector<String>();

		String sFile = sFileName;

		final Object o = prop.getProperty("include");
		prop.setProperty("include", (o != null ? o.toString() + " " : "") + sFile + (includeDefault ? " global" : ""));

		final Vector<String> vIncludes = new Vector<String>();

		final StringTokenizer st = new StringTokenizer(prop.getProperty("include"), ";, \t");
		while (st.hasMoreTokens()) {
			final String sIncludeFile = st.nextToken().trim();

			if (sIncludeFile.length() > 0)
				vIncludes.add(sIncludeFile);
		}

		while (vIncludes.size() > 0) {
			sFile = vIncludes.get(0);
			vIncludes.remove(0);

			if (vFiles.contains(sFile))
				continue;

			vFiles.add(sFile);

			FileInputStream fis = null;

			try {
				final String sFullFileName = sConfDir + sFile + ".properties";

				final Properties pTemp = new Properties();

				fis = new FileInputStream(sFullFileName);

				pTemp.load(fis);

				fis.close();

				if (pTemp.getProperty("include") != null) {
					final StringTokenizer st2 = new StringTokenizer(pTemp.getProperty("include"), ";, \t");

					while (st2.hasMoreTokens()) {
						final String sIncludeFile = st2.nextToken().trim();

						if (sIncludeFile.length() > 0)
							vIncludes.add(0, sIncludeFile);
					}
				}

				pTemp.putAll(prop);

				prop = pTemp;
			}
			catch (final IOException e) {
				System.err.println(e + " (" + e.getMessage() + ")");
				e.printStackTrace();
			}
			finally {
				if (fis != null)
					try {
						fis.close();
					}
					catch (final IOException ioe) {
						// ignore
					}
			}
		}

		if (pOld != null) {
			final Properties pTemp = new Properties();
			pTemp.putAll(pOld);
			pTemp.putAll(prop);

			prop = pTemp;
		}

		return prop;
	}

	/**
	 * Get the array of values for a key
	 *
	 * @param p
	 * @param sKey
	 * @return the array of values for a key
	 */
	public static String[] getValues(final Properties p, final String sKey) {
		if ((p == null) || (sKey == null))
			return null;

		final Vector<String> v = ServletExtension.toVector(p, sKey, null);

		if (v.size() <= 0)
			return null;

		final int size = v.size();

		final String[] vs = new String[size];

		for (int i = 0; i < size; i++)
			vs[i] = v.get(i);

		return vs;
	}

	/**
	 * Try to determine if the data query returned actually multiple data series, if so then cleanup
	 * the result depending on the user options
	 *
	 * @param vOrig
	 *            original data series, possibly with multiple separate data series included
	 * @param prop
	 *            options to select one of them (or maybe disable this feature at all)
	 * @param sSeriesName
	 *            the series name, to replace it in the options when trying to match something
	 * @param replaceInitialData
	 *            whether or not to replace the data in the initial vector or just return it
	 * @return the cleaned data series
	 */
	public static Vector<Result> filterMultipleSeries(final Vector<Result> vOrig, final Properties prop, final String sSeriesName, final boolean replaceInitialData) {
		final Vector<Result> v = new Vector<Result>();

		if (ServletExtension.pgetb(prop, "multiple_series.allow", false) || (vOrig == null) || (vOrig.size() <= 1))
			return vOrig;

		final int rezSize = vOrig.size();

		// try to determine if there are multiple series in the data set
		String sKey = null;
		boolean bMultipleSeries = false;
		String sTempKey;

		for (int i = 0; i < rezSize; i++) {
			sTempKey = IDGenerator.generateKey(vOrig.get(i), 0);

			if ((sKey != null) && ((sTempKey == null) || !sTempKey.equals(sKey))) {
				bMultipleSeries = true;
				break;
			}

			sKey = sTempKey;
		}

		if (!bMultipleSeries)
			return vOrig;

		// what shall we do now with parallel data ?
		boolean bMultipleSeriesAny = ServletExtension.pgetb(prop, "multiple_series.any", true);

		final String sMultipleSeriesPredicates = ServletExtension.pgets(prop, "multiple_series.predicate_order", null);

		Vector<monPredicate> vPredicates = null;

		if ((sMultipleSeriesPredicates != null) && (sMultipleSeriesPredicates.length() > 0)) {
			bMultipleSeriesAny = false;

			vPredicates = new Vector<monPredicate>();

			final StringTokenizer st = new StringTokenizer(sMultipleSeriesPredicates, ",");

			while (st.hasMoreTokens()) {
				String sPred = st.nextToken();

				sPred = ServletExtension.replace(sPred, "$SERIES", sSeriesName);

				sPred = ServletExtension.parseOption(prop, "predicate_order", sPred, sPred, true, true);

				vPredicates.add(ServletExtension.toPred(sPred));
			}
		}

		Result r;
		final Iterator<Result> it = vOrig.iterator();

		if (bMultipleSeriesAny || (vPredicates == null) || (vPredicates.size() == 0))
			// whatever key was first found will be the only one that will remain in the final data
			// set
			while (it.hasNext()) {
				r = it.next();

				sTempKey = IDGenerator.generateKey(r, 0);

				if ((sTempKey != null) && sTempKey.equals(sKey))
					v.add(r);
			}
		else {
			// keep only the data that matches the predicates, in the order of the predicates
			monPredicate pred = null;
			monPredicate tempPred = null;
			final int iPredSize = vPredicates.size();

			while (it.hasNext()) {
				r = it.next();

				if (pred == null)
					for (int i = 0; i < iPredSize; i++) {
						tempPred = vPredicates.get(i);
						if (DataSelect.matchResult(r, tempPred) != null) {
							pred = tempPred;
							break;
						}
					}

				if ((pred != null) && (DataSelect.matchResult(r, pred) != null))
					v.add(r);
			}
		}

		if (replaceInitialData) {
			vOrig.clear();
			vOrig.addAll(v);
		}

		return v;
	}

	/**
	 * For a given data series create an alternative data series with 1=there was some data and 0=no
	 * data in that period If the initial dataset is empty (null or size()==0) then this function
	 * doesn't build an all-zero series.
	 *
	 * @param vOrig
	 *            the original data series
	 * @param tmin
	 *            low end of the time interval (epoch, in millis)
	 * @param tmax
	 *            high end of the time interval (epoch, in millis)
	 * @param nrValues
	 *            number of values to consider
	 * @param prop
	 *            the options for this page
	 * @param replaceInitialData
	 *            whether or not to replace the original contents of the data vector with the
	 *            integrated series
	 * @return the boolean data series
	 */
	public static Vector<Result> booleanSeries(final Vector<Result> vOrig, final long tmin, final long tmax, final int nrValues, final Properties prop, final boolean replaceInitialData) {
		if ((vOrig == null) || (vOrig.size() == 0))
			return vOrig;

		final Vector<Result> v = new Vector<Result>(nrValues);

		final Result rStamp = vOrig.get(0);

		final long intervalLength = (tmax - tmin) / nrValues;

		ExtendedResult er;

		for (int i = 0; i <= nrValues; i++) {
			er = copyResult(rStamp);

			er.time = tmin + (intervalLength * i);
			er.param[0] = 0;
			er.min = 0;
			er.max = 0;

			v.add(er);
		}

		Result r;
		long j;

		for (int i = 0; i < vOrig.size(); i++) {
			r = vOrig.get(i);
			j = (r.time - tmin) / intervalLength;

			er = (ExtendedResult) v.get((int) j);
			er.param[0] = er.min = er.max = 1;

			if (((r.time - tmin - (j * intervalLength)) > ((2 * intervalLength) / 5)) && (j < (v.size() - 1))) {
				er = (ExtendedResult) v.get((int) j + 1);
				er.param[0] = er.min = er.max = 1;
			}
		}

		// make skipinterval relative to the data measurement interval
		final long lSkipInterval = ServletExtension.pgetl(prop, "skipinterval", ServletExtension.pgetl(prop, "default.measurement_interval", 60)) * 1000;

		// first of all check if there are 0 values at the begining
		int i = 0;
		while ((i < v.size()) && (((ExtendedResult) v.get(i)).param[0] == 0))
			i++;

		if ((i > 0) && (i < (v.size() - 1)) && ((((ExtendedResult) v.get(i + 1)).time - ((ExtendedResult) v.get(0)).time) <= lSkipInterval))
			for (int k = 0; k <= i; k++) {
				er = (ExtendedResult) v.get(k);

				er.param[0] = er.min = er.max = 1;
			}

		for (; i < (v.size() - 1); i++) {
			er = (ExtendedResult) v.get(i);

			if (Math.abs(er.param[0] - 1) < 1E-10) {
				final ExtendedResult erNext = (ExtendedResult) v.get(i + 1);

				if (erNext.param[0] == 0) {
					int k = i + 1;

					// j might be the last index of the data series, if all next values are 0
					// or just the last index of an 0 value before a 1 value
					while ((k < (v.size() - 1)) && (((ExtendedResult) v.get(k)).param[0] == 0))
						k++;

					if ((((ExtendedResult) v.get(k)).time - er.time) < lSkipInterval)
						for (++i; i <= k; i++) {
							er = (ExtendedResult) v.get(i);
							er.param[0] = er.min = er.max = 1;
						}
					else
						i = k + 1; // go over
				}
			}
		}

		if (replaceInitialData) {
			vOrig.clear();
			vOrig.addAll(v);
		}

		return v;
	}

	/**
	 * Create a copy (clone) of a Result object, adding an empty parameter with the default value 0
	 * if the original Result didn't have any parameters
	 *
	 * @param r
	 *            the object to be copied
	 * @return the copy
	 */
	public static ExtendedResult copyResult(final Result r) {
		final ExtendedResult er = new ExtendedResult();

		er.FarmName = r.FarmName;
		er.ClusterName = r.ClusterName;
		er.NodeName = r.NodeName;

		for (int i = 0; (r.param_name != null) && (r.param != null) && (i < r.param_name.length) && (i < r.param.length); i++)
			er.addSet(r.param_name[i], r.param[i]);

		if ((r.param == null) || (r.param_name == null) || (r.param.length == 0) || (r.param_name.length == 0))
			er.addSet("", 0d);

		er.time = r.time;

		if (r instanceof ExtendedResult) {
			final ExtendedResult erTemp = (ExtendedResult) r;

			er.min = erTemp.min;
			er.max = erTemp.max;
		}

		return er;
	}

	/**
	 * Find out what is the compact interval for a chart, depending on the selected interval and the
	 * display options
	 *
	 * @param prop
	 *            the properties (options) file
	 * @param lIntervalMin
	 *            low end of the time interval (in millis, time in the past relative to the current
	 *            time)
	 * @param lIntervalMax
	 *            high end of the time interval (in millis, time in the past relative to the current
	 *            time)
	 * @return the desired time interval between two data points, in millis
	 */
	public static long getCompactInterval(final Properties prop, final long lIntervalMin, final long lIntervalMax) {
		final boolean bArea = ServletExtension.pgetb(prop, "areachart", false);

		final String sPoints = ServletExtension.pgets(prop, bArea ? "compact.displaypoints.areachart" : "compact.displaypoints", "auto");

		long lDataPoints = 90;

		try {
			lDataPoints = Long.parseLong(sPoints);
		}
		catch (final Exception e) {
			// not a number, try to figure out the number of points depending on the image size

			final int iSize = ServletExtension.pgeti(prop, "width", 800);

			if (iSize > 100)
				if (bArea)
					lDataPoints = (int) ((iSize - 100) / ServletExtension.pgetd(prop, "auto.point.size.areachart", 1));
				else
					lDataPoints = (int) ((iSize - 100) / ServletExtension.pgetd(prop, "auto.point.size", 6));

			if (logger.isLoggable(Level.FINE))
				logger.log(Level.FINE, "I've chosen to display ~" + lDataPoints + " because image size is " + iSize);
		}

		long lCompactInterval = (lIntervalMin - lIntervalMax) / lDataPoints;

		if (lCompactInterval < ServletExtension.pgetl(prop, "compact.min_interval", 60000))
			lCompactInterval = ServletExtension.pgetl(prop, "compact.min_interval", 60000);

		if (lCompactInterval < 1000)
			lCompactInterval = 1000;

		if (ServletExtension.pgetb(prop, "compact.disable", false))
			lCompactInterval = 1; // 1ms

		return lCompactInterval;
	}

	/**
	 * get the default measurement interval
	 *
	 * @param prop
	 * @param lCompactInterval
	 * @return the measurement interval, in millis
	 */
	public static long getMeasurementInterval(final Properties prop, final long lCompactInterval) {
		long lMeasurementInterval = ServletExtension.pgetl(prop, "default.measurement_interval", lCompactInterval / 1000) * 1000;

		if (lMeasurementInterval < lCompactInterval)
			lMeasurementInterval = lCompactInterval;

		return lMeasurementInterval;
	}

	/**
	 * Filter out only the Result objects from a generic array
	 *
	 * @param v
	 * @return only instances of Result from the original vector
	 */
	public static Vector<Result> toResultVector(final Vector<?> v) {
		if (v == null)
			return null;

		final Vector<Result> ret = new Vector<Result>(v.size());

		for (final Object o : v)
			if (o instanceof Result)
				ret.add((Result) o);

		return ret;
	}

	/**
	 * Create an integrated series of this data
	 *
	 * @param vOrig
	 *            the original data
	 * @param prop
	 *            the properties file
	 * @param replaceOriginalData
	 *            whether or not to replace the original contents of the data vector with the
	 *            integrated series
	 * @param lIntervalMin
	 *            low end of the time interval (in millis, time in the past relative to the current
	 *            time)
	 * @param lIntervalMax
	 *            high end of the time interval (in millis, time in the past relative to the current
	 *            time)
	 * @return the integrated series
	 */
	public static Vector<Result> integrateSeries(final Vector<Result> vOrig, final Properties prop, final boolean replaceOriginalData, final long lIntervalMin, final long lIntervalMax) {
		if (vOrig == null)
			return null;

		final Vector<Result> v = new Vector<Result>(vOrig.size());

		if (vOrig.size() == 0)
			return v;

		long lPrevDataTime = 0;
		double dInc;
		double last4int = 0;

		final long lCompactInterval = getCompactInterval(prop, lIntervalMin, lIntervalMax);
		final long lMeasurementInterval = getMeasurementInterval(prop, lCompactInterval);

		final long lIntegrateTimeBase = ServletExtension.pgetl(prop, "history.integrate.timebase", ServletExtension.pgetb(prop, "totalperminute", false) ? 60 : 1);

		final boolean bDataInBits = ServletExtension.pgetb(prop, "datainbits", false);

		if (logger.isLoggable(Level.FINE))
			logger.log(Level.FINE, "Parameters: lCompactInterval=" + lCompactInterval + ", lMeasurementInterval=" + lMeasurementInterval + ", lIntegrateTimeBase=" + lIntegrateTimeBase
					+ ", bDataInBits=" + bDataInBits + ", data size: " + v.size());

		ExtendedResult r;

		final boolean bDebug = logger.isLoggable(Level.FINEST);

		for (int i = 0; i < vOrig.size(); i++) {
			final Object o = vOrig.get(i);

			if (!(o instanceof Result))
				continue;

			r = copyResult((Result) o);

			if ((lPrevDataTime == 0) || ((r.time - lPrevDataTime) > lMeasurementInterval))
				lPrevDataTime = r.time - lMeasurementInterval;

			dInc = r.param[0] * ((r.time - lPrevDataTime) / (lIntegrateTimeBase * 1000d));

			if (bDebug)
				logger.log(Level.FINEST, "New value: " + r.param[0] + " @ " + r.time + ", old time = " + lPrevDataTime + ", increment value = " + dInc);

			last4int += dInc;

			lPrevDataTime = r.time;

			r.min = r.max = r.param[0] = bDataInBits ? last4int / 8 : last4int;

			v.add(r);
		}

		if (replaceOriginalData) {
			vOrig.clear();
			vOrig.addAll(v);
		}

		return v;
	}

	/**
	 * Apply some filters to the data series (round, scale, timestamp alignment, using min/max
	 * values instead of avg)
	 *
	 * @param vOrig
	 *            original data series
	 * @param prop
	 *            properties (options) to apply
	 * @param lIntervalMin
	 *            low end of the time interval (in millis, time in the past relative to the current
	 *            time)
	 * @param lIntervalMax
	 *            high end of the time interval (in millis, time in the past relative to the current
	 *            time)
	 * @param replaceOriginalData
	 *            whether or not to replace the original contents of the data vector with the
	 *            integrated series
	 * @return a data series with the applied options
	 */
	public static Vector<Result> fixupHistorySeries(final Vector<Result> vOrig, final Properties prop, final long lIntervalMin, final long lIntervalMax, final boolean replaceOriginalData) {
		if (vOrig == null)
			return null;

		final Vector<Result> v = new Vector<Result>(vOrig.size());

		if (vOrig.size() == 0)
			return v;

		final boolean bArea = ServletExtension.pgetb(prop, "areachart", false);
		final boolean bUseMaxValue = ServletExtension.pgetb(prop, "history.use_max_value", false);
		final boolean bUseMinValue = ServletExtension.pgetb(prop, "history.use_min_value", false);
		final boolean bRound = ServletExtension.pgetb(prop, "history.round_values", false);
		final double dScaleAllValues = ServletExtension.pgetd(prop, "data.scalefactor", 1d);
		final boolean bAlignTimestamps = ServletExtension.pgetb(prop, "data.align_timestamps", bArea) || (ServletExtension.pgets(prop, "download_data_csv").length() > 0)
				|| (ServletExtension.pgets(prop, "download_data_html").length() > 0);

		final boolean bScaleAllValues = Math.abs(dScaleAllValues - 1d) > 1E-10;

		final long lCompactInterval = getCompactInterval(prop, lIntervalMin, lIntervalMax);

		ExtendedResult er;

		for (int i = 0; i < vOrig.size(); i++) {
			er = copyResult(vOrig.get(i));

			if (bScaleAllValues) {
				er.param[0] *= dScaleAllValues;
				er.min *= dScaleAllValues;
				er.max *= dScaleAllValues;
			}

			if (bUseMaxValue)
				er.min = er.param[0] = er.max;

			if (bUseMinValue)
				er.max = er.param[0] = er.min;

			if (bRound) {
				er.param[0] = Math.round(er.param[0]);
				er.min = Math.round(er.min);
				er.max = Math.round(er.max);
			}

			if (bAlignTimestamps && (lCompactInterval > 1))
				er.time = (er.time / lCompactInterval) * lCompactInterval;

			v.add(er);
		}

		if (replaceOriginalData) {
			vOrig.clear();
			vOrig.addAll(v);
		}

		return v;
	}

	/**
	 * Get the sorted series names, based on the current configuration
	 *
	 * @param series
	 * @param prop
	 * @param vsUnselect
	 * @param hmSeries
	 * @return sorted series
	 */
	public static String[] sortSeries(final String[] series, final Properties prop, final String vsUnselect[], final HashMap<String, ?> hmSeries) {
		if ((series == null) || (series.length == 0))
			return new String[0];

		final Vector<String> v = new Vector<String>();

		final boolean bSortDisplay = ServletExtension.pgetb(prop, "sort", true);
		final boolean bSortBySuffix = ServletExtension.pgetb(prop, "sort.bysuffix", false);

		final String sSuffixDelimiter = ServletExtension.pgets(prop, "sort.bysuffix.delimiter", " .-_");

		for (final String serie : series)
			v.add(serie);

		if (bSortDisplay)
			Collections.sort(v, new MyStringComparator(bSortBySuffix ? sSuffixDelimiter : null, true));

		for (int i = 0; i < v.size(); i++)
			series[i] = v.get(i);

		if ((vsUnselect != null) && (vsUnselect.length > 0))
			for (final String element : vsUnselect)
				hmSeries.remove(element);

		return series;
	}

	private static final PeriodAxisLabelInfo PALI_HOUR_MIN = new PeriodAxisLabelInfo(org.jfree.data.time.Minute.class, new SimpleDateFormat("HH:mm"));

	private static final PeriodAxisLabelInfo PALI_HOUR = new PeriodAxisLabelInfo(org.jfree.data.time.Hour.class, new SimpleDateFormat("HH:00"));

	private static final PeriodAxisLabelInfo PALI_DAY = new PeriodAxisLabelInfo(org.jfree.data.time.Day.class, new SimpleDateFormat("d"));

	private static final PeriodAxisLabelInfo PALI_WEEK = new PeriodAxisLabelInfo(org.jfree.data.time.Week.class, new SimpleDateFormat("'Week' w"));

	private static final PeriodAxisLabelInfo PALI_MONTH = new PeriodAxisLabelInfo(org.jfree.data.time.Month.class, new SimpleDateFormat("MMM"));

	private static final PeriodAxisLabelInfo PALI_DATE = new PeriodAxisLabelInfo(org.jfree.data.time.Day.class, new SimpleDateFormat("dd MMM yyyy"), new RectangleInsets(0D, 0D, 0D, 0D),
			new Font("SansSerif", Font.BOLD, 10), Color.blue.darker(), true, PeriodAxisLabelInfo.DEFAULT_DIVIDER_STROKE, PeriodAxisLabelInfo.DEFAULT_DIVIDER_PAINT);

	private static final PeriodAxisLabelInfo PALI_MONTH_YEAR = new PeriodAxisLabelInfo(org.jfree.data.time.Month.class, new SimpleDateFormat("MMM yyyy"), new RectangleInsets(1D, 1D, 1D, 1D),
			new Font("SansSerif", Font.BOLD, 10), Color.blue.darker(), true, PeriodAxisLabelInfo.DEFAULT_DIVIDER_STROKE, PeriodAxisLabelInfo.DEFAULT_DIVIDER_PAINT);

	private static final PeriodAxisLabelInfo PALI_YEAR = new PeriodAxisLabelInfo(org.jfree.data.time.Year.class, new SimpleDateFormat("yyyy"), new RectangleInsets(0D, 0D, 0D, 0D),
			new Font("SansSerif", Font.BOLD, 10), Color.blue.darker(), true, PeriodAxisLabelInfo.DEFAULT_DIVIDER_STROKE, PeriodAxisLabelInfo.DEFAULT_DIVIDER_PAINT);

	/**
	 * Show different time axis depending on the selected time interval
	 *
	 * @param prop
	 * @param lMin
	 * @param lMax
	 * @return the time axis
	 */
	public static ValueAxis getValueAxis(final Properties prop, final long lMin, final long lMax) {
		if (ServletExtension.pgetb(prop, "timeaxis.hide", false))
			return null;

		final long lDiff = Math.abs(lMax - lMin);

		if ((lDiff < 1000) || (lMax == 0) || (lMin == 0)) {
			logger.log(Level.FINE, "returning a null axis because: " + lDiff + " / " + lMin + " / " + lMax);

			return new DateAxis(null);
		}

		String sTimeAxis = null;

		final PeriodAxisLabelInfo[] aperiodaxislabelinfo = new PeriodAxisLabelInfo[2];

		Class<?> majorTickTime = Hour.class;

		if (lDiff <= (TIME_HOUR * 2)) {
			aperiodaxislabelinfo[0] = PALI_HOUR_MIN;
			aperiodaxislabelinfo[1] = PALI_DATE;
			sTimeAxis = ServletExtension.pgets(prop, "timeaxis", "GMT Time");
		}
		else
			if (lDiff <= ((TIME_DAY * 2) + (TIME_HOUR * 1))) {
				aperiodaxislabelinfo[0] = PALI_HOUR;
				aperiodaxislabelinfo[1] = PALI_DATE;
				sTimeAxis = ServletExtension.pgets(prop, "timeaxis", "GMT Time");
			}
			else
				if (lDiff <= ((TIME_WEEK * 6) + (TIME_DAY * 1))) {
					aperiodaxislabelinfo[0] = PALI_DAY;
					aperiodaxislabelinfo[1] = PALI_MONTH_YEAR;
					majorTickTime = Day.class;
				}
				else
					if (lDiff <= ((TIME_MONTH * 3) + (TIME_DAY * 1))) {
						// for 1.5 to 3 months show the weeks only
						aperiodaxislabelinfo[0] = PALI_WEEK;
						aperiodaxislabelinfo[1] = PALI_MONTH_YEAR;
						majorTickTime = Week.class;
					}
					else {
						// for at least 3 months just show the month names
						aperiodaxislabelinfo[0] = PALI_MONTH;
						aperiodaxislabelinfo[1] = PALI_YEAR;
						majorTickTime = Month.class;
					}

		if (ServletExtension.pgetd(prop, "font.scale", -1d) > 0)
			for (int i = 0; i < aperiodaxislabelinfo.length; i++)
				if (aperiodaxislabelinfo[i] != null) {
					final PeriodAxisLabelInfo a = aperiodaxislabelinfo[i];

					final Font newFont = display.getScalledFont(prop, a.getLabelFont());

					aperiodaxislabelinfo[i] = new PeriodAxisLabelInfo(a.getPeriodClass(), a.getDateFormat(), a.getPadding(), newFont, a.getLabelPaint(), a.getDrawDividers(), a.getDividerStroke(),
							a.getDividerPaint());
				}

		final PeriodAxis periodaxis = new PeriodAxis(sTimeAxis);

		periodaxis.setLabelInfo(aperiodaxislabelinfo);

		periodaxis.setMajorTickTimePeriodClass(majorTickTime);

		// make sure that the minor ticks aren't visible
		periodaxis.setMinorTickMarksVisible(false);

		periodaxis.setAutoRangeTimePeriodClass(Minute.class);

		return periodaxis;
	}

	/**
	 * Apply brightness to a Paint, only if it is a Color instance, otherwise returns a grey color
	 *
	 * @param p
	 * @param diff
	 * @return the changed color
	 */
	public static Color alterColor(final Paint p, final int diff) {
		return alterColor(p instanceof Color ? (Color) p : ColorFactory.getColor(150, 150, 150), diff);
	}

	/**
	 * Modify a color with a fixed offset (brightness)
	 *
	 * @param c
	 *            original color
	 * @param diff
	 *            the difference to apply, >0 = brighter, <0 = darker
	 * @return the modified color
	 */
	public static Color alterColor(final Color c, final int diff) {
		int r = c.getRed() + diff;
		if (r < 0)
			r = 0;
		if (r > 255)
			r = 255;

		int g = c.getGreen() + diff;
		if (g < 0)
			g = 0;
		if (g > 255)
			g = 255;

		int b = c.getBlue() + diff;
		if (b < 0)
			b = 0;
		if (b > 255)
			b = 255;

		return ColorFactory.getColor(r, g, b);
	}

	private static final char vcHexChars[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	/**
	 * Convert a single composite color to a double hex digit representation
	 *
	 * @param iColorComponent
	 * @return the 2 hex digit representation of this color component
	 */
	public static final String toHex(final int iColorComponent) {
		int i = iColorComponent;

		if (i < 0)
			i = 0;

		if (i > 255)
			i = 255;

		return "" + vcHexChars[(i / 16) % 16] + vcHexChars[i % 16];
	}

	/**
	 * Convert an [R,G,B] int[] to 'RRGGBB' String representation
	 *
	 * @param v
	 * @return the hex string representation of this color
	 */
	public static final String toHex(final int v[]) {
		final StringBuilder sbRez = new StringBuilder(6);
		for (final int element : v)
			sbRez.append(toHex(element));

		return sbRez.toString();
	}

	/**
	 * Create an RRGGBB representation of an {@link Color} object
	 *
	 * @param c
	 *            the color
	 * @return the 'RRGGBB' string
	 */
	public static final String toHex(final Color c) {
		return toHex(c.getRed()) + toHex(c.getGreen()) + toHex(c.getBlue());
	}

	/**
	 * Create a single bin for a given interval either by averaging the values (bIntegrated=false) or by
	 * adding the values (bIntegrated=true) from a lInterval of data.
	 *
	 * @param vOrig
	 *            Vector containing the original data to be histogram-ed
	 * @param lInterval
	 *            time interval, in milliseconds, to create a bin at
	 * @param bIntegrated
	 *            flag indicating if the values should be added (true) or averaged (false)
	 * @return a Vector with the histogram bins, one for each interval where is some data
	 */
	public static final Vector<Result> histogramData(final Vector<?> vOrig, final long lInterval, final boolean bIntegrated) {
		final Vector<Result> v = new Vector<Result>();

		Result rOld = null;
		int iValues = 0;

		if (logger.isLoggable(Level.FINER))
			logger.log(Level.FINER, "Parameters: " + vOrig.size() + " elements, interval=" + lInterval + ", integrated=" + bIntegrated);

		final boolean bLog = logger.isLoggable(Level.FINEST);

		for (int i = 0; i < vOrig.size(); i++) {
			final Object o = vOrig.get(i);

			if ((o == null) || !(o instanceof Result))
				continue;

			final Result r = (Result) o;

			if (bLog)
				logger.log(Level.FINEST, "New result is : " + r);

			final long lTime = ((r.time / lInterval) * lInterval) + (lInterval / 2);

			if (bLog)
				logger.log(Level.FINEST, "  middle time is : " + lTime);

			if ((rOld == null) || (rOld.time != lTime)) {
				if (rOld != null) {
					if (!bIntegrated && (iValues > 0))
						rOld.param[0] /= iValues;

					if (bLog)
						logger.log(Level.FINEST, "  first flushing the old result: " + rOld);

					v.add(rOld);
				}

				rOld = copyResult(r);
				rOld.time = lTime;
				rOld.param[0] = 0d;
				iValues = 0;
			}

			rOld.param[0] += r.param[0];
			iValues++;
		}

		if (rOld != null) {
			if (!bIntegrated && (iValues > 0))
				rOld.param[0] /= iValues;

			if (bLog)
				logger.log(Level.FINEST, "  flushing the last result: " + rOld);

			v.add(rOld);
		}

		return v;
	}

	/**
	 * Get all the annotations that apply to one chart.<br>
	 * Configuration options:<br>
	 * <li>annotation.disabled (false) : flag to completely disable annotations
	 * <li>annotation.groups ("") : groups to which this chart belong to (integer values separated by comma).
	 * A single 0 means that this chart wants to see all the annotations from any group
	 * <li>annotation.chart_wide ("") : list of series names that should be considered chart-wide
	 * <li>annotation.text_always_top (false) : flag to force the text to be always displayed at the top of the chart
	 * <li>annotation.debug (false) : flag to enable debug messages
	 *
	 * @param prop
	 *            configuration file
	 * @param lStartTime
	 *            beginning of the interval
	 * @param lEndTime
	 *            end of the interval
	 * @return a {@link List} of {@link Annotation} objects
	 */
	@SuppressWarnings("deprecation")
	private static List<Annotation> getAnnotations(final Properties prop, final long lStartTime, final long lEndTime) {
		if (ServletExtension.pgetb(prop, "annotation.disabled", false))
			return null;

		final Set<Integer> groups = new TreeSet<Integer>();

		final String sGroups = ServletExtension.pgets(prop, "annotation.groups", "");

		final String sChartWide = ServletExtension.pgets(prop, "annotation.chart_wide", "");

		if (sGroups.length() == 0)
			return null;

		final StringTokenizer st = new StringTokenizer(sGroups, ",");

		boolean bFoundZero = false;

		while (st.hasMoreTokens())
			try {
				final int i = Integer.parseInt(st.nextToken().trim());

				bFoundZero = bFoundZero || (i == 0);

				groups.add(Integer.valueOf(i));
			}
			catch (final Exception e) {
				// ignore
			}

		if (groups.size() == 0)
			return null;

		if (bFoundZero && (groups.size() == 1))
			groups.clear();

		final List<Annotation> lAnnotations = Annotations.getAnnotations(lStartTime, lEndTime, groups);

		final TimeZone tz = ServletExtension.getTimeZone(prop);

		Iterator<Annotation> it = lAnnotations.iterator();

		final List<Annotation> alSelected = new ArrayList<Annotation>();

		// determine which are chart-wide and which are series-wide
		while (it.hasNext()) {
			final Annotation a = it.next();

			if (((a.services.size() == 0) || ((sChartWide.length() > 0) && a.services.contains(sChartWide))) && !a.bValue)
				alSelected.add(a);
		}

		// prepare the structures for addAnnotations call
		final int count = alSelected.size();

		final boolean bAlwaysTop = ServletExtension.pgetb(prop, "annotation.text_always_top", false);

		final boolean bDebug = ServletExtension.pgetb(prop, "annotation.debug", false);

		if (bDebug)
			System.err.println("Annotations : start=" + lStartTime + ", end=" + lEndTime);

		for (int i = 0; i < count; i++) {
			final Annotation a = alSelected.get(i);

			if (bDebug)
				System.err.println("  " + i);

			if (bDebug)
				System.err.println("    from=" + a.from);

			a.leftSpace = a.from - lStartTime;

			if (bDebug)
				System.err.println("      initial : " + a.leftSpace);

			for (int j = 0; j < i; j++) {
				if (!bAlwaysTop && (((i + j) % 2) != 0)) {
					if (bDebug)
						System.err.println("      skip : " + j + " because imparity");
					continue;
				}

				final Annotation aLeft = alSelected.get(j);

				long lLeftSpace = a.from - aLeft.to;

				// check if the left annotation will display its text on it's right
				if (aLeft.rightSpace >= aLeft.leftSpace) {
					if (bDebug)
						System.err.println("      left space will be shared with " + j);
					lLeftSpace /= 2; // the space from left would be shared by two
				}

				if (lLeftSpace < a.leftSpace) {
					if (bDebug)
						System.err.println("      updating with " + j + " because " + lLeftSpace + " < " + a.leftSpace);
					a.leftSpace = lLeftSpace;
				}
			}

			if (bDebug)
				System.err.println("      final : " + a.leftSpace);

			if (bDebug)
				System.err.println("    to=" + a.to);

			a.rightSpace = lEndTime - a.to;

			if (bDebug)
				System.err.println("      initial : " + a.rightSpace);

			for (int j = 0; j < count; j++) {
				if ((i == j) || (!bAlwaysTop && (((i + j) % 2) != 0))) {
					if (bDebug)
						System.err.println("      skip : " + j + " because imparity");
					continue;
				}

				final Annotation aRight = alSelected.get(j);

				if (a.to <= aRight.to) {
					if (bDebug)
						System.err.println("      check " + j + " because " + a.to + " <= " + aRight.to);
					if ((aRight.from - a.to) < a.rightSpace) {
						if (bDebug)
							System.err.println("      updating with " + j + " because " + aRight.from + " - " + a.to + " < " + a.rightSpace);
						a.rightSpace = aRight.from - a.to;
					}
				}
			}

			if (bDebug)
				System.err.println("    final : " + a.rightSpace);
		}

		it = lAnnotations.iterator();

		// convert all times to chart times (altered by the same timezone as the rest of the data)
		while (it.hasNext()) {
			final Annotation a = it.next();

			if (!a.bValue) {
				a.from = (new Second(new Date(a.from), tz)).getFirstMillisecond();
				a.to = (new Second(new Date(a.to), tz)).getLastMillisecond();
			}
		}

		return lAnnotations;
	}

	/**
	 * Get the annotations for one chart in the form of an {@link AnnotationCollection}, that splits the
	 * {@link Annotation} objects into chart-wide and series-specific.<br>
	 * From the configuration file the parameter annotation.chart_wide ("") could force some series-wide annotations
	 * to become chart-wide.
	 *
	 * @param prop
	 *            configuration file contents
	 * @param lStart
	 *            start interval
	 * @param lEnd
	 *            end interval
	 * @return an AnnotationCollection
	 */
	public static AnnotationCollection getAnnotationCollection(final Properties prop, final long lStart, final long lEnd) {
		return new AnnotationCollection(getAnnotations(prop, lStart, lEnd), ServletExtension.pgets(prop, "annotation.chart_wide", ""));
	}

	/**
	 * Annotations stroke
	 */
	public static final Stroke SINGLE_VALUE_MARKER_STROKE = new BasicStroke(2.0F);

	/**
	 * Annotations font
	 */
	public static final Font ANNOTATION_LABEL_FONT = new Font("SansSerif", 0, 11);

	/**
	 * Add the global annotations to a chart in form of colored bars on the background of the chart.<br>
	 * Configuration file parameters that are taken into account:<br>
	 * <li>annotation.enabled (true) : flag to completely disable chart-wide annotations
	 * <li>annotation.alpha (0.5d) : seems to have no effect when put in background
	 * <li>annotation.show_text (true) : flag to disable showing the text, but to leave the areas
	 * <li>annotation.text_always_top (false) : flag to force the text to be always at the top of the chart, default is alternative top / center
	 * <li>annotation.debug (false) : flag to produce debug messages
	 *
	 * @param plot
	 * @param ac
	 * @param prop
	 */
	public static void addAnnotations(final XYPlot plot, final AnnotationCollection ac, final Properties prop) {
		if ((ac == null) || (ac.getChartAnnotations().size() == 0) || !ServletExtension.pgetb(prop, "annotation.enabled", true))
			return;

		final float fAlpha = (float) ServletExtension.pgetd(prop, "annotation.alpha", 0.5d);

		final boolean bShowTexts = ServletExtension.pgetb(prop, "annotation.show_text", true);

		final boolean bAlwaysTop = ServletExtension.pgetb(prop, "annotation.text_always_top", false);

		final boolean bDebug = ServletExtension.pgetb(prop, "annotation.debug", false);

		final Iterator<Annotation> it = ac.getChartAnnotations().iterator();

		int i = 0;

		while (it.hasNext()) {
			final Annotation a = it.next();

			if (bDebug)
				System.err.println("Annotation " + i + " : value=" + a.bValue);

			if (a.bValue) {
				if (bDebug)
					System.err.println("add value marker : " + (a.from / 1000) + " - " + (a.to / 1000));

				if (a.from == a.to) {
					final ValueMarker valuemarker = new ValueMarker(a.from / 1000d, a.color, SINGLE_VALUE_MARKER_STROKE);

					if (bShowTexts && (a.text.length() > 0)) {
						valuemarker.setLabelOffsetType(LengthAdjustmentType.EXPAND);
						valuemarker.setLabel(a.text);
						valuemarker.setLabelFont(ANNOTATION_LABEL_FONT);
						valuemarker.setLabelPaint(a.textColor);
						valuemarker.setLabelAnchor(RectangleAnchor.TOP_LEFT);
						valuemarker.setLabelTextAnchor(TextAnchor.BOTTOM_LEFT);
					}

					plot.addRangeMarker(valuemarker, Layer.BACKGROUND);
				}
				else {
					final IntervalMarker intervalmarker = new IntervalMarker(a.from / 1000d, a.to / 1000d);

					intervalmarker.setPaint(a.color);

					if (bShowTexts && (a.text.length() > 0)) {
						intervalmarker.setLabelOffsetType(LengthAdjustmentType.EXPAND);
						intervalmarker.setLabel(a.text);
						intervalmarker.setLabelFont(ANNOTATION_LABEL_FONT);
						intervalmarker.setLabelPaint(a.textColor);
						intervalmarker.setLabelAnchor(RectangleAnchor.TOP_LEFT);
						intervalmarker.setLabelTextAnchor(TextAnchor.BOTTOM_LEFT);
					}

					intervalmarker.setAlpha(fAlpha);

					plot.addRangeMarker(intervalmarker, Layer.BACKGROUND);
				}

				continue;
			}

			if (bDebug)
				System.err.println("Annotation " + i + " : left=" + a.leftSpace + ", right=" + a.rightSpace);

			final boolean bLeft = a.leftSpace > a.rightSpace;

			final boolean bTop = ((i % 2) == 0) || bAlwaysTop;

			i++;

			final RectangleAnchor labelAnchor;
			final TextAnchor textAnchor;

			if (bLeft) {
				if (bTop) {
					labelAnchor = RectangleAnchor.TOP_LEFT;
					textAnchor = TextAnchor.TOP_RIGHT;
				}
				else {
					labelAnchor = RectangleAnchor.LEFT;
					textAnchor = TextAnchor.CENTER_RIGHT;
				}
			}
			else
				if (bTop) {
					labelAnchor = RectangleAnchor.TOP_RIGHT;
					textAnchor = TextAnchor.TOP_LEFT;
				}
				else {
					labelAnchor = RectangleAnchor.RIGHT;
					textAnchor = TextAnchor.CENTER_LEFT;
				}

			if (a.from == a.to) {
				final ValueMarker valuemarker = new ValueMarker(a.from, a.color, SINGLE_VALUE_MARKER_STROKE);

				if (bShowTexts && (a.text.length() > 0)) {
					valuemarker.setLabelOffsetType(LengthAdjustmentType.EXPAND);
					valuemarker.setLabel(a.text);
					valuemarker.setLabelPaint(a.textColor);
					valuemarker.setLabelAnchor(labelAnchor);
					valuemarker.setLabelTextAnchor(textAnchor);
					valuemarker.setLabelFont(ANNOTATION_LABEL_FONT);
				}

				valuemarker.setAlpha(fAlpha);

				plot.addDomainMarker(valuemarker, Layer.BACKGROUND);
			}
			else {
				final IntervalMarker intervalmarker = new IntervalMarker(a.from, a.to);

				intervalmarker.setPaint(a.color);

				if (bShowTexts && (a.text.length() > 0)) {
					intervalmarker.setLabelOffsetType(LengthAdjustmentType.EXPAND);
					intervalmarker.setLabel(a.text);
					intervalmarker.setLabelFont(ANNOTATION_LABEL_FONT);
					intervalmarker.setLabelPaint(a.textColor);
					intervalmarker.setLabelAnchor(labelAnchor);
					intervalmarker.setLabelTextAnchor(textAnchor);
				}

				intervalmarker.setAlpha(fAlpha);

				plot.addDomainMarker(intervalmarker, Layer.BACKGROUND);
			}
		}
	}

	// 14/Jul/2006:17:11:25 +0200
	private static final SimpleDateFormat apacheTimeFormat = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss ZZ");

	/**
	 * Log a request to a servlet in the Apache log format for later analysis with standard software (awstats / webalizer etc)
	 *
	 * @param sServletName
	 *            the servlet that was executed
	 * @param iSize
	 *            size of the response, in bytes
	 * @param request
	 *            HttpServletRequest to get other information from (referer, browser, http method, requested page)
	 */
	public static synchronized void logRequest(final String sServletName, final int iSize, final HttpServletRequest request) {
		logRequest(sServletName, iSize, request, true);
	}

	private static final class JSPExecution {
		final long lStarted = System.nanoTime();
		final String jspName;

		public JSPExecution(final String jspName) {
			this.jspName = jspName;
		}

		/**
		 * @return elapsed time, in milliseconds
		 */
		public double toMillis() {
			final long delta = System.nanoTime() - lStarted;

			return delta / 1000000d;
		}
	}

	private static ExpirationCache<Long, JSPExecution> jspExecutionStarted = new ExpirationCache<Long, JSPExecution>(10000);

	public static synchronized void logRequest(final String sServletName, final int iSize, final HttpServletRequest request, final boolean incrementCounters) {
		logRequest(sServletName, iSize, request, incrementCounters, -1);
	}

	/**
	 * Log a request to a servlet in the Apache log format for later analysis with standard software (awstats / webalizer etc)
	 *
	 * @param sServletName
	 *            the servlet that was executed
	 * @param iSize
	 *            size of the response, in bytes
	 * @param request
	 *            HttpServletRequest to get other information from (referer, browser, http method, requested page)
	 * @param incrementCounters
	 *            flag to increment or not the counters (see {@link ThreadedPage#incrementRequestCount()}
	 * @param executionTime
	 *            execution time, in milliseconds
	 */
	public static synchronized void logRequest(final String sServletName, final int iSize, final HttpServletRequest request, final boolean incrementCounters, final double executionTime) {

		if (incrementCounters && !sServletName.startsWith("START "))
			ThreadedPage.incrementRequestCount();

		final String sLogFile = AppConfig.getProperty("web_log_file", null);

		if (sLogFile == null)
			return;

		final boolean bLogStart = AppConfig.getb("web_log_file.log_start", false);

		final boolean isStartStatement = sServletName.startsWith("START ");

		if (isStartStatement) {
			jspExecutionStarted.put(Long.valueOf(Thread.currentThread().getId()), new JSPExecution(sServletName.substring(6).trim()), 1000 * 60 * 30);

			if (!bLogStart)
				return;
		}

		PrintWriter pw = null;

		try {
			pw = new PrintWriter(new FileWriter(sLogFile, true));

			final String sIP = request.getRemoteAddr();

			if (!isStartStatement) {
				if (sIP.indexOf(':') >= 0)
					ThreadedPage.incrementIPv6RequestCount();
				else
					ThreadedPage.incrementIPv4RequestCount();
			}

			String sDate;

			synchronized (apacheTimeFormat) {
				sDate = apacheTimeFormat.format(new Date());
			}

			final String sPage = request.getParameter("page");

			final String sURL = request.getMethod() + (sServletName.startsWith("/") ? " " : " /") + sServletName + (sPage != null ? "?page=" + sPage : "") + " HTTP/1.1";

			String sReferer = request.getHeader("Referer");
			if (sReferer == null)
				sReferer = "-";

			String sBrowser = request.getHeader("User-Agent");
			if (sBrowser == null)
				sBrowser = "-";

			String account = "-";

			if (request.getUserPrincipal() != null)
				account = request.getUserPrincipal().getName();

			final int port = request.getLocalPort();

			final JSPExecution execution = isStartStatement ? null : jspExecutionStarted.remove(Long.valueOf(Thread.currentThread().getId()));

			double executionTimeReal = executionTime;

			if (execution != null && sServletName.startsWith(execution.jspName)) {
				executionTimeReal = execution.toMillis();

				if (executionTimeReal >= 0)
					ThreadedPage.addJSPMeasurement(executionTimeReal);
			}

			pw.println(sIP + " " + port + " " + account + " [" + sDate + "] \"" + sURL + "\" 200 " + iSize + " \"" + sReferer + "\" \"" + sBrowser + "\" " + Format.point(executionTimeReal));

			pw.flush();
		}
		catch (final Throwable t) {
			// ignore
		}
		finally {
			if (pw != null)
				pw.close();
		}
	}

	private static final NumberFormat NF_DOUBLE = new DecimalFormat("0.###########");

	/**
	 * Print large values in a plain format
	 *
	 * @param d
	 * @return formatted double
	 */
	public static final String showDouble(final double d) {
		synchronized (NF_DOUBLE) {
			return NF_DOUBLE.format(d);
		}
	}

	/**
	 * Debug method
	 *
	 * @param args
	 */
	public static void main(final String args[]) {
		System.err.println(apacheTimeFormat.format(new Date()));
	}
}
