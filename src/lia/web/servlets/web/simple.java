/**
 * 
 */
package lia.web.servlets.web;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Point;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Vector;

import lazyj.RequestWrapper;
import lia.Monitor.monitor.AppConfig;
import lia.web.utils.CacheServlet;
import lia.web.utils.ColorFactory;

import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.ThermometerPlot;
import org.jfree.chart.plot.dial.DialBackground;
import org.jfree.chart.plot.dial.DialCap;
import org.jfree.chart.plot.dial.DialPlot;
import org.jfree.chart.plot.dial.DialPointer;
import org.jfree.chart.plot.dial.DialTextAnnotation;
import org.jfree.chart.plot.dial.DialValueIndicator;
import org.jfree.chart.plot.dial.StandardDialFrame;
import org.jfree.chart.plot.dial.StandardDialRange;
import org.jfree.chart.plot.dial.StandardDialScale;
import org.jfree.data.general.DefaultValueDataset;
import org.jfree.ui.GradientPaintTransformType;
import org.jfree.ui.RectangleInsets;
import org.jfree.ui.StandardGradientPaintTransformer;

/**
 * @author costing
 * @since 2006-11-21
 */
public class simple extends CacheServlet {
	private static final long serialVersionUID = 1L;

	/**
	 * Initialize instance variables
	 */
	@Override
	public void doInit() {
		// nothing special to initialize
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
	 * This must not be cacheable until the caching mechanism is content-type aware!
	 */
	@Override
	public long getCacheTimeout() {
		return 0;
	}

	/**
	 * Display the image
	 */
	@Override
	public void execGet() {
		final long lStart = System.currentTimeMillis();

		final Properties prop = Utils.getProperties(sConfDir, gets("page"), null, true);

		setLogTiming(prop);

		final Enumeration<?> e = request.getParameterNames();

		while (e.hasMoreElements()) {
			String sParam = (String) e.nextElement();
			prop.setProperty(sParam, gets(sParam));
		}

		Vector<String> vStrings = toVector(prop, "values", null, true, true);

		Vector<Double> values = new Vector<>(vStrings.size());

		double dMax = 0;

		for (int i = 0; i < vStrings.size(); i++) {
			Object o = vStrings.get(i);

			if (o instanceof Number)
				values.add(Double.valueOf(((Number) o).doubleValue()));
			else
				try {
					double dTemp = Double.parseDouble(o.toString());

					values.add(Double.valueOf(dTemp));

					dTemp = Math.abs(dTemp);

					if (dTemp > dMax)
						dMax = dTemp;
				}
				catch (@SuppressWarnings("unused") Exception e2) {
					// ignore any conversion exception
				}
		}

		if (values.size() == 0) {
			redirect("/");
			return;
		}

		int height = pgeti(prop, "height", 200);
		int width = pgeti(prop, "width", 200);

		boolean bSmall = (height < 200 || width < 200) || (dMax >= 1000 && (height < 400 || width < 400));

		bSmall = pgetb(prop, "small", bSmall);

		JFreeChart chart = null;

		Plot plot = null;

		String sChartType = pgets(prop, "chart.type", "thermometer");

		if (sChartType.startsWith("t"))
			plot = getThermometerChart(prop, values);
		else
			if (sChartType.startsWith("dialthermo1"))
				plot = getDialThermo1Chart(prop, values, bSmall);
			else
				if (sChartType.startsWith("dialthermo2"))
					plot = getDialThermo2Chart(prop, values, bSmall);
				else
					if (sChartType.startsWith("dial1"))
						plot = getDial1Chart(prop, values, bSmall);
					else
						if (sChartType.startsWith("dial2"))
							plot = getDial2Chart(prop, values, bSmall);

		if (plot == null) {
			redirect("/");
			return;
		}

		chart = new JFreeChart(pgets(prop, "title", null), plot);

		chart.setAntiAlias(true);
		chart.setRenderingHints(Utils.ML_RENDERING_HINTS);

		chart.setBackgroundPaint(getColor(prop, "bgcolor", Color.WHITE));

		chart.removeLegend();

		RequestWrapper.setCacheTimeout(response, AppConfig.geti("lia.web.servlets.web.simple.timeout", 120));

		try {
			ChartUtilities.writeChartAsPNG(osOut, chart, width, height);
			osOut.flush();
		}
		catch (@SuppressWarnings("unused") IOException e1) {
			// ignore IO exceptions
		}

		Utils.logRequest("simple", (int) (System.currentTimeMillis() - lStart), request, false, System.currentTimeMillis() - lStart);
	}

	private static final Font FONT1 = new Font("Dialog", Font.BOLD, 14);
	private static final Font FONT2 = new Font("Dialog", Font.PLAIN, 14);
	private static final Font FONT1_SMALL = new Font("Dialog", Font.BOLD, 10);
	private static final Font FONT2_SMALL = new Font("Dialog", Font.PLAIN, 10);
	private static final Font FONT3 = new Font("Dialog", Font.PLAIN, 10);

	/**
	 * Get the tick size knowing the values to display and the number of ticks wanted
	 * 
	 * @param dMin
	 * @param dMax
	 * @param count
	 * @return size of one tick
	 */
	public static double getTickSize(double dMin, double dMax, int count) {
		double dDiff = (Math.max(dMin, dMax) - Math.min(dMin, dMax)) / count;

		double factor = 1d;

		while (dDiff > 10d) {
			dDiff /= 10d;
			factor *= 10d;
		}

		while (dDiff < 1d) {
			dDiff *= 10d;
			factor /= 10d;
		}

		dDiff *= 2;
		dDiff = Math.round(dDiff);
		dDiff /= 2;
		dDiff *= factor;

		return dDiff;
	}

	/**
	 * A simple vertical dial
	 * 
	 * @param prop
	 *            configuration options
	 * @param values
	 *            value array, the first position counts
	 * @param bSmall
	 *            small font
	 * @return the chart
	 */
	public static Plot getDial2Chart(Properties prop, Vector<Double> values, boolean bSmall) {
		double value = values.get(0).doubleValue();

		double dAbsMin = pgetd(prop, "abs.min", 10);
		double dAbsMax = pgetd(prop, "abs.max", 70);

		if (dAbsMin > value)
			dAbsMin = value;

		if (dAbsMax < value)
			dAbsMax = value;

		DefaultValueDataset dataset = new DefaultValueDataset(value);

		DialPlot plot = new DialPlot();
		plot.setView(0.78, 0.37, 0.22, 0.26);
		plot.setDataset(dataset);

		StandardDialFrame dialFrame = new StandardDialFrame();
		dialFrame.setRadius(0.80);
		dialFrame.setForegroundPaint(Color.darkGray);
		dialFrame.setStroke(new BasicStroke(3.0f));
		plot.setDialFrame(dialFrame);

		GradientPaint gp = new GradientPaint(new Point(), ColorFactory.getColor(255, 255, 255), new Point(), ColorFactory.getColor(240, 240, 240));
		DialBackground sdb = new DialBackground(gp);
		sdb.setGradientPaintTransformer(new StandardGradientPaintTransformer(GradientPaintTransformType.VERTICAL));
		plot.addLayer(sdb);

		StandardDialScale scale = new StandardDialScale(dAbsMin, dAbsMax, -8, 16.0, getTickSize(dAbsMin, dAbsMax, pgeti(prop, "ticks", 4)), pgeti(prop, "minorticks", 5));
		scale.setTickRadius(0.77);
		scale.setTickLabelOffset(-0.07);
		scale.setTickLabelFont(bSmall ? FONT2_SMALL : FONT2);
		scale.setTickLabelFormatter(new DecimalFormat(pgets(prop, "numberformat", "0")));

		plot.addScale(0, scale);

		DialPointer needle = new DialPointer.Pin();
		needle.setRadius(0.78);
		plot.addLayer(needle);

		return plot;
	}

	/**
	 * A dual dial chart
	 * 
	 * @param prop
	 *            configuration file
	 * @param values
	 *            values to display (should be at least two in this vector)
	 * @param bSmall
	 *            small font
	 * @return the chart
	 */
	public static Plot getDialThermo2Chart(Properties prop, Vector<Double> values, boolean bSmall) {
		double value1 = values.get(0).doubleValue();
		double value2 = values.get(1).doubleValue();

		double dAbsMin1 = pgetd(prop, "abs.min.1", 10);
		double dAbsMax1 = pgetd(prop, "abs.max.1", 70);

		if (dAbsMin1 > value1)
			dAbsMin1 = value1;

		if (dAbsMax1 < value1)
			dAbsMax1 = value1;

		double dAbsMin2 = pgetd(prop, "abs.min.2", 10);
		double dAbsMax2 = pgetd(prop, "abs.max.2", 70);

		if (dAbsMin2 > value2)
			dAbsMin2 = value2;

		if (dAbsMax2 < value2)
			dAbsMax2 = value2;

		DefaultValueDataset dataset1 = new DefaultValueDataset(value1);
		DefaultValueDataset dataset2 = new DefaultValueDataset(value2);

		DialPlot plot = new DialPlot();
		plot.setView(0.0, 0.0, 1.0, 1.0);
		plot.setDataset(0, dataset1);
		plot.setDataset(1, dataset2);
		plot.setDialFrame(new StandardDialFrame());
		// SimpleDialFrame dialFrame = new SimpleDialFrame();
		// dialFrame.setBackgroundPaint(Color.lightGray);
		// dialFrame.setForegroundPaint(Color.darkGray);
		// plot.setDialFrame(dialFrame);

		GradientPaint gp = new GradientPaint(new Point(), ColorFactory.getColor(255, 255, 255), new Point(), ColorFactory.getColor(170, 170, 220));
		DialBackground db = new DialBackground(gp);
		db.setGradientPaintTransformer(new StandardGradientPaintTransformer(GradientPaintTransformType.VERTICAL));
		plot.setBackground(db);

		DialTextAnnotation annotation1 = new DialTextAnnotation(pgets(prop, "text", "Temperature"));
		annotation1.setFont(bSmall ? FONT1_SMALL : FONT1);
		annotation1.setRadius(0.7);

		plot.addLayer(annotation1);

		String sNumberFormat = pgets(prop, "numberformat", "0");
		NumberFormat nf1 = new DecimalFormat(pgets(prop, "numberformat1", sNumberFormat));
		NumberFormat nf2 = new DecimalFormat(pgets(prop, "numberformat1", sNumberFormat));

		DialValueIndicator dvi = new DialValueIndicator(0);
		dvi.setNumberFormat(nf1);
		dvi.setTemplateValue(Double.valueOf(value1));
		dvi.setFont(FONT3);
		dvi.setOutlinePaint(Color.darkGray);
		dvi.setRadius(0.60);
		dvi.setAngle(-103.0);
		plot.addLayer(dvi);

		DialValueIndicator dvi2 = new DialValueIndicator(1);
		dvi2.setNumberFormat(nf2);
		dvi2.setTemplateValue(Double.valueOf(value2));
		dvi2.setFont(FONT3);
		dvi2.setOutlinePaint(Color.red);
		dvi2.setRadius(0.60);
		dvi2.setAngle(-77.0);
		plot.addLayer(dvi2);

		// TODO
		StandardDialScale scale = new StandardDialScale(dAbsMin1, dAbsMax1, -120, -300, getTickSize(dAbsMin1, dAbsMax1, pgeti(prop, "ticks", 9)), pgeti(prop, "minorticks", 5));
		scale.setTickRadius(0.88);
		scale.setTickLabelOffset(0.15);
		scale.setTickLabelFont(bSmall ? FONT2_SMALL : FONT2);
		scale.setTickLabelFormatter(nf1);
		plot.addScale(0, scale);

		// TODO
		StandardDialScale scale2 = new StandardDialScale(dAbsMin2, dAbsMax2, -120, -300, getTickSize(dAbsMin1, dAbsMax1, pgeti(prop, "ticks", 9)), pgeti(prop, "minorticks", 5));
		scale2.setTickRadius(0.50);
		scale2.setTickLabelOffset(0.15);
		scale2.setTickLabelFont(FONT3);
		scale2.setMajorTickPaint(Color.red);
		scale2.setTickLabelFormatter(nf2);
		plot.addScale(1, scale2);
		plot.mapDatasetToScale(1, 1);

		DialPointer needle = new DialPointer.Pointer(0);
		plot.addLayer(needle);

		DialPointer needle2 = new DialPointer.Pin(1);
		needle2.setRadius(0.55);
		plot.addLayer(needle2);

		DialCap cap = new DialCap();
		cap.setRadius(0.10);
		plot.setCap(cap);

		return plot;
	}

	/**
	 * A simple vertical dial
	 * 
	 * @param prop
	 *            configuration options
	 * @param values
	 *            value array, the first position counts
	 * @param bSmall
	 *            small fonts
	 * @return the chart
	 */
	public static Plot getDial1Chart(Properties prop, Vector<Double> values, boolean bSmall) {
		double value = values.get(0).doubleValue();

		double dAbsMin = pgetd(prop, "abs.min", 10);
		double dAbsMax = pgetd(prop, "abs.max", 70);

		if (dAbsMin > value)
			dAbsMin = value;

		if (dAbsMax < value)
			dAbsMax = value;

		DefaultValueDataset dataset = new DefaultValueDataset(value);
		// get data for diagrams
		DialPlot plot = new DialPlot();
		plot.setView(0.21, 0.0, 0.58, 0.30);
		plot.setDataset(dataset);

		StandardDialFrame dialFrame = new StandardDialFrame();
		dialFrame.setRadius(0.80);
		dialFrame.setForegroundPaint(Color.darkGray);
		dialFrame.setStroke(new BasicStroke(3.0f));
		plot.setDialFrame(dialFrame);

		GradientPaint gp = new GradientPaint(new Point(), ColorFactory.getColor(255, 255, 255), new Point(), ColorFactory.getColor(240, 240, 240));
		DialBackground sdb = new DialBackground(gp);
		sdb.setGradientPaintTransformer(new StandardGradientPaintTransformer(GradientPaintTransformType.VERTICAL));
		plot.addLayer(sdb);

		// TODO
		StandardDialScale scale = new StandardDialScale(dAbsMin, dAbsMax, 115.0, -50.0, 10, 1);
		scale.setTickRadius(0.88);
		scale.setTickLabelOffset(0.07);
		scale.setMajorTickIncrement(getTickSize(dAbsMin, dAbsMax, pgeti(prop, "ticks", 4)));
		scale.setTickLabelPaint(Color.WHITE);
		scale.setTickLabelFont(bSmall ? FONT2_SMALL : FONT2);
		scale.setTickLabelFormatter(new DecimalFormat(pgets(prop, "numberformat", "0")));
		plot.addScale(0, scale);

		DialPointer needle = new DialPointer.Pin();
		needle.setRadius(0.82);
		plot.addLayer(needle);

		return plot;
	}

	/**
	 * Generate a simple dial thermometer (JFreeChart : DialDemo1)
	 * 
	 * @param prop
	 *            configuration properties
	 * @param values
	 *            values to display. the first entry is displayed
	 * @param bSmall
	 *            small font
	 * @return the desired chart
	 */
	public static Plot getDialThermo1Chart(final Properties prop, final Vector<Double> values, final boolean bSmall) {
		final DialPlot plot = new DialPlot();

		plot.setView(0.0, 0.0, 1.0, 1.0);

		double mainValue = values.get(0).doubleValue();

		double dAbsMin = pgetd(prop, "abs.min", 10);
		double dAbsMax = pgetd(prop, "abs.max", 70);
		double dGreen = pgetd(prop, "max.ok", 30);
		double dYellow = pgetd(prop, "max.warn", 40);

		for (int i = 0; i < values.size(); i++) {
			double value = ((Number) values.get(i)).doubleValue();

			DefaultValueDataset dataset = new DefaultValueDataset(value);

			plot.setDataset(i, dataset);

			if (dAbsMin > value)
				dAbsMin = value;

			if (dAbsMax < value)
				dAbsMax = value;
		}

		plot.setDialFrame(new StandardDialFrame());

		GradientPaint gp = new GradientPaint(new Point(), ColorFactory.getColor(255, 255, 255), new Point(), ColorFactory.getColor(170, 170, 220));
		DialBackground db = new DialBackground(gp);
		db.setGradientPaintTransformer(new StandardGradientPaintTransformer(GradientPaintTransformType.VERTICAL));
		plot.setBackground(db);

		DialTextAnnotation annotation1 = new DialTextAnnotation(pgets(prop, "text", "Temperature"));
		annotation1.setFont(bSmall ? FONT1_SMALL : FONT1);
		annotation1.setRadius(0.7);

		plot.addLayer(annotation1);

		if (pgetb(prop, "valueindicator", true)) {
			DialValueIndicator dvi = new DialValueIndicator(0);
			dvi.setFont(FONT3);
			dvi.setTemplateValue(Double.valueOf(mainValue));
			dvi.setNumberFormat(new DecimalFormat(pgets(prop, "numberformat", "0")));
			plot.addLayer(dvi);
		}

		boolean bReversed = pgetb(prop, "reversed", false);

		StandardDialScale scale = new StandardDialScale(dAbsMin, dAbsMax, -120, -300, getTickSize(dAbsMin, dAbsMax, pgeti(prop, "ticks", 9)), pgeti(prop, "minorticks", 5));
		scale.setTickRadius(0.88);
		scale.setTickLabelOffset(0.22);
		scale.setTickLabelFont(bSmall ? FONT2_SMALL : FONT2);
		scale.setTickLabelFormatter(new DecimalFormat(pgets(prop, "numberformat", "0")));
		plot.addScale(0, scale);

		for (int i = 1; i < values.size(); i++) {
			StandardDialScale secondaryScale = new StandardDialScale(dAbsMin, dAbsMax, -120, -300, getTickSize(dAbsMin, dAbsMax, pgeti(prop, "ticks", 9)), pgeti(prop, "minorticks", 5));

			secondaryScale.setVisible(pgetb(prop, "secondaryscale_" + i + ".visible", false));
			secondaryScale.setTickRadius(0.68000000000000005D);
			secondaryScale.setTickLabelOffset(0.22);
			secondaryScale.setTickLabelFont(FONT2_SMALL);

			plot.addScale(i, secondaryScale);

			plot.mapDatasetToScale(i, i);
		}

		double dInnerRadius = 0.45;
		double dOuterRadius = dInnerRadius + 0.01;

		StandardDialRange range = new StandardDialRange(dYellow, dAbsMax, bReversed ? Color.green : Color.red);
		range.setInnerRadius(dInnerRadius);
		range.setOuterRadius(dOuterRadius);
		plot.addLayer(range);

		StandardDialRange range2 = new StandardDialRange(dGreen, dYellow, Color.orange);
		range2.setInnerRadius(dInnerRadius);
		range2.setOuterRadius(dOuterRadius);
		plot.addLayer(range2);

		StandardDialRange range3 = new StandardDialRange(dAbsMin, dGreen, bReversed ? Color.red : Color.green);
		range3.setInnerRadius(dInnerRadius);
		range3.setOuterRadius(dOuterRadius);
		plot.addLayer(range3);

		for (int i = 0; i < values.size(); i++) {
			DialPointer needle;

			if (pgetb(prop, "pointer_" + i + ".pointer", i == 0 ? true : false))
				needle = new DialPointer.Pointer(i);
			else
				needle = new DialPointer.Pin(i);

			needle.setRadius(pgetd(prop, "pointer_" + i + ".radius", i == 0 ? 1D : 0.55D));

			plot.addLayer(needle);
		}

		DialCap cap = new DialCap();
		cap.setRadius(0.1);
		plot.setCap(cap);

		return plot;
	}

	/**
	 * Create a thermometer-like chart
	 * 
	 * @param prop
	 *            configuration options
	 * @param values
	 *            values to display (only the first one is taken)
	 * @return the chart
	 */
	@SuppressWarnings("deprecation")
	public static Plot getThermometerChart(Properties prop, Vector<Double> values) {
		double value = values.get(0).doubleValue();

		DefaultValueDataset dataset = new DefaultValueDataset(value);

		ThermometerPlot thermometerplot = new ThermometerPlot(dataset);
		thermometerplot.setInsets(new RectangleInsets(5D, 5D, 5D, 5D));
		thermometerplot.setPadding(new RectangleInsets(10D, 10D, 10D, 10D));
		thermometerplot.setThermometerStroke(new BasicStroke(2.0F));
		thermometerplot.setThermometerPaint(Color.lightGray);
		thermometerplot.setUnits(1);

		double dAbsMin = pgetd(prop, "abs.min", 10);
		double dAbsMax = pgetd(prop, "abs.max", 70);
		double dGreen = pgetd(prop, "max.ok", 30);
		double dYellow = pgetd(prop, "max.warn", 40);

		if (dAbsMin > value)
			dAbsMin = value;

		if (dAbsMax < value)
			dAbsMax = value;

		thermometerplot.setRange(dAbsMin, dAbsMax);
		thermometerplot.setSubrange(0, dAbsMin, dGreen);
		thermometerplot.setSubrangePaint(0, Color.green);

		thermometerplot.setSubrange(1, dGreen, dYellow);
		thermometerplot.setSubrangePaint(1, Color.orange);

		thermometerplot.setSubrange(2, dYellow, dAbsMax);
		thermometerplot.setSubrangePaint(2, Color.red);

		String unit = pgets(prop, "unit", "c").toLowerCase();

		if (unit.indexOf("c") >= 0)
			thermometerplot.setUnits(ThermometerPlot.UNITS_CELCIUS);
		else
			if (unit.indexOf("f") >= 0)
				thermometerplot.setUnits(ThermometerPlot.UNITS_FAHRENHEIT);
			else
				if (unit.indexOf("k") >= 0)
					thermometerplot.setUnits(ThermometerPlot.UNITS_KELVIN);
				else
					if (unit.length() > 0)
						thermometerplot.setUnits(unit);
					else
						thermometerplot.setUnits(ThermometerPlot.UNITS_NONE);

		return thermometerplot;
	}

}
