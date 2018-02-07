package lia.web.servlets.web;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Store.DataSplitter;
import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.TransparentStoreFast;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.web.utils.Formatare;
import lia.web.utils.Page;
import lia.web.utils.ThreadedPage;

import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.entity.StandardEntityCollection;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.CombinedDomainCategoryPlot;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.CombinedRangeCategoryPlot;
import org.jfree.chart.plot.CombinedRangeXYPlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.servlet.ServletUtilities;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.statistics.HistogramType;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.DefaultXYDataset;

/**
 * @author costing
 * @since Jul 18, 2007
 */
public class Correlations extends ThreadedPage {
	
	private static final Logger	logger = Logger.getLogger(Correlations.class.getName());
	
	/**
	 * 
	 */
	private static final long	serialVersionUID	= 1L;

	@Override
	public void doInit() {
		// nothing
	}
	
	private static final HashSet<String> EXCLUDED_TAGS = new HashSet<String>();
	
	static {
		EXCLUDED_TAGS.add("page");
		EXCLUDED_TAGS.add("quick_interval");
		EXCLUDED_TAGS.add("interval_date_low");
		EXCLUDED_TAGS.add("interval_date_high");
		EXCLUDED_TAGS.add("interval.min");
		EXCLUDED_TAGS.add("interval.max");
		EXCLUDED_TAGS.add("submit_plot");
	}
	
	/**
	 * Receive the request and dispatch it.
	 * Puts all the received parameters in a Properties map and calls getChart(Properties)
	 * to generate the JFreeChart object based on the request. Then it generates the image and 
	 * sends it to the client.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void execGet(){
		final long lStart = System.currentTimeMillis();
		
		final Page pMaster = new Page(osOut, sResDir + "masterpage/masterpage.res");
		
		final Page pCorrelations = new Page(sResDir + "correlations/correlations.res");
		
		final String sPage = gets("page", "global");
		
		final Properties prop = Utils.getProperties(sConfDir, sPage);
		
		pMaster.modify("comment_refresh", "//");
		
		prop.remove("interval.min");
		prop.remove("interval.max");
		
		pCorrelations.modify("page", sPage);
		
		final HashSet hsExcludedTags = new HashSet(EXCLUDED_TAGS);
		
		for (int i=0; i<pgeti(prop, "options", 0); i++){
			final String sOptionName = pgets(prop, "option_"+i+".name", null);
			
			if (sOptionName!=null)
				hsExcludedTags.add(sOptionName);
		}
		
		// put all the received parameters in the configuration map
		
		final Enumeration<String> e = request.getParameterNames();
		
		while (e.hasMoreElements()){
			final String sKey = e.nextElement();
			
			final String[] values = request.getParameterValues(sKey);
			
			String sValue = "";
			
			if (values==null || values.length==0)
				continue;
			
			if (values.length>1){
				for (int i=0; i<values.length; i++)
					sValue += (i>0 ? "," : "") + values[i];
			}
			else{
				sValue = values[0];
			}
			
			if (sKey.endsWith(".enabled")){
				logger.log(Level.FINER, "I've detected that this series is checked: "+sKey);
				
				prop.setProperty("series.some_enabled", "true");
			}
			
			prop.setProperty(sKey, sValue);
			
			// add the hidden fields
			if (!hsExcludedTags.contains(sKey) && !sKey.endsWith(".enabled"))
				pCorrelations.append("hidden_parameters", "<input type=hidden name='"+Formatare.tagProcess(sKey)+"' value='"+Formatare.tagProcess(sValue)+"'>\n");
		}
		
		// now the real work
		final Statistics stats = new Statistics();
		prop.put("statistics", stats);
	
		final JFreeChart chart = getChart(prop);
	
		if (chart!=null){
			final ChartRenderingInfo info = new ChartRenderingInfo(new StandardEntityCollection());
			final StringWriter sw = new StringWriter();
			final PrintWriter pw = new PrintWriter(sw);
		
			try{
				final String sImage = ServletUtilities.saveChartAsPNG(chart, pgeti(prop, "width", 800), pgeti(prop, "height", 500), info, null);
				ChartUtilities.writeImageMap(pw, sImage, info, true);

				pw.flush();

				pCorrelations.modify("map", sw.toString());
				pCorrelations.modify("image", sImage);
				
				display.registerImageForDeletion(sImage, 300);
			}
			catch (Exception ex){
				logger.log(Level.WARNING, "Exception generating the chart", ex);
			}
		}
		
		// let's build the list of series
		final StringTokenizer st = new StringTokenizer(pgets(prop, "series.names"), ",");
		
		final Page pSeries = new Page(sResDir+"correlations/series.res");
		
		while (st.hasMoreTokens()){
			final String sSeriesName = st.nextToken();
			
			final boolean bEnabled = pgetb(prop, sSeriesName+".enabled", false);
			
			pSeries.modify("name", sSeriesName);
			pSeries.check("enabled", bEnabled);
			
			pCorrelations.append("series", pSeries);
		}
		
		// add some statistics
		stats.write(pCorrelations, sResDir);
		
		// display the options
		display.showOptions(sResDir, prop, pCorrelations, this);
		showIntervalSelectionForm(pCorrelations, prop);
		
		pMaster.modify("bookmark", getBookmarkURL());
		
		// OK, dump the product to the user
		pMaster.append(pCorrelations);
		
		pMaster.write();

		// log request statistics
		final int iRequestTime = (int)(System.currentTimeMillis()-lStart);		
		System.err.println("Correlations (" + sPage + "), ip: " + getHostName() + ", took " + iRequestTime + "ms to complete");
		Utils.logRequest("Correlations", iRequestTime, request);
	}

	private static void showIntervalSelectionForm(final Page p, final Properties prop){
		final SimpleDateFormat sdf = new SimpleDateFormat("M/d/yyyy H:mm:00");
		p.modify("current_date_time", sdf.format(new Date()));
		
		final long lMin = pgetl(prop, "interval.min", 86400000); 
		final long lMax = pgetl(prop, "interval.max", 0);
		
		p.modify("interval.min", (lMin/1000)*1000);
		p.modify("interval.max", (lMax/1000)*1000);
	}
	
	/**
	 * Iterate through the "charts" and produce all the individual charts. Then assembles them
	 * in a single big chart, if they all have the same type.
	 * 
	 * @return
	 */
	private JFreeChart getChart(final Properties prop) {
		final String[] chartNames = pgetvs(prop, "charts");
		
		final ArrayList<Plot> plots = new ArrayList<Plot>(chartNames.length);
		
		for (String sChartName: chartNames){
			try{
				Plot p = getPlot(prop, sChartName);

				if (p!=null)
					plots.add(p);
			}
			catch (Exception e){
				logger.log(Level.WARNING, "Exception creating the '"+sChartName+"'");
			}
		}
	
		Plot plot = null;
		
		if (plots.size()==1){
			plot = plots.get(0);
		}
		else{
			int iCategory = 0;
			int iXY = 0;
			int iOth = 0;
			
			for (Plot p: plots){
				if (p instanceof CategoryPlot)
					iCategory ++;
				else
				if (p instanceof XYPlot)
					iXY ++;
				else
					iOth ++;
			}
			
			if (iCategory>0 && (iXY+iOth==0)){
				if (pgetb(prop, "samerange", true)){
					CombinedRangeCategoryPlot crcp = new CombinedRangeCategoryPlot();
				
					ValueAxis va = null;
					
					for (Plot p: plots){
						if (va==null)
							va = ((CategoryPlot) p).getRangeAxis();
						
						crcp.add((CategoryPlot) p);
						
						crcp.setRangeAxis(va);
					}
					
					plot = crcp;
				}
				else{
					CombinedDomainCategoryPlot cdcp = new CombinedDomainCategoryPlot();
					
					for (Plot p: plots){
						cdcp.add((CategoryPlot) p);
					}

					plot = cdcp;
				}
			}
			else
			if (iXY>0 && (iCategory+iOth==0)){
				if (pgetb(prop, "samerange", false)){
					CombinedRangeXYPlot crxyp = new CombinedRangeXYPlot();
					
					ValueAxis va = null;
					
					for (Plot p: plots){
						if (va==null)
							va = ((XYPlot) p).getRangeAxis();
						
						crxyp.add((XYPlot) p);
					}
					
					crxyp.setRangeAxis(va);
					
					plot = crxyp;
				}
				else{
					CombinedDomainXYPlot cdxyp = new CombinedDomainXYPlot();
					
					ValueAxis va = null; 
					
					for (Plot p: plots){
						if (va==null)
							va = ((XYPlot) p).getDomainAxis();
						
						cdxyp.add((XYPlot) p);
					}
					
					cdxyp.setDomainAxis(va);
					
					plot = cdxyp;
				}
			}
			else
			if (iXY+iCategory+iOth==0){
				logger.log(Level.WARNING, "Given configuration doesn't create any chart");
			}
			else{
				logger.log(Level.WARNING, "Different chart types detected: "+iCategory+" category plots, "+iXY+" XY plots, "+iOth+" other plots");

				plot = plots.get(0);
			}
		}
		
		if (plot==null)
			return null;
		
		final JFreeChart chart = new JFreeChart(pgets(prop, "title", ""), plot);
		
		display.setChartProperties(chart, prop);
		
		return chart;
	}
	
	/**
	 * Add a unique series name to the list of names
	 * 
	 * @param prop
	 * @param sSeriesName
	 */
	private static final void addSeries(final Properties prop, final String sSeriesName){
		prop.setProperty("series.names", pgets(prop, "series.names", "")+","+sSeriesName);
	}
	
	/**
	 * @param prop
	 * @param string
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private Plot getPlot(final Properties prop, final String sChartName) {
		final String sType = pgets(prop, sChartName+".type", pgets(prop, "type", "history"));
		
		final String[] vsNames = pgetvs(prop, sChartName+".names");
		
		final String[] vsPreds = pgetvs(prop, sChartName+".preds");
		
		long tMin = Long.MAX_VALUE;
		long tMax = Long.MIN_VALUE;
		
		final HashMap<String, List<monPredicate>> mSeries = new HashMap<String, List<monPredicate>>();
		
		final Set<monPredicate> uniquePreds = new HashSet<monPredicate>();
		
		for (int i=0; i<vsNames.length; i++){
			String sPreds;
			
			if (i<vsPreds.length)
				logger.log(Level.FINE, "pred = "+vsPreds[i]);
			else
				logger.log(Level.FINE, "vspreds length is too small for "+i+" : "+vsPreds.length);
			
			if (vsPreds.length<=i || vsPreds[i]==null || vsPreds[i].equals("SEPARATE"))
				sPreds = pgets(prop, sChartName+"."+vsNames[i]+".pred", 
					pgets(prop, vsNames[i]+".pred", null)
				);
			else
				sPreds = vsPreds[i];
			
			if (sPreds==null || sPreds.length()==0){
				logger.log(Level.WARNING, "No predicate defined for "+sChartName+" / "+vsNames[i]);
				continue;
			}
			
			final String[] seriesPreds = sPreds.split(",");
			
			final ArrayList<monPredicate> lPreds = new ArrayList<monPredicate>(seriesPreds.length); 
			
			for (String sPred: seriesPreds){
				monPredicate p = toPred(sPred);
					
				boolean bMinSet = false;
				
				final long lIntMin = pgetl(prop, "interval.min", -1);
				final long lIntMax = pgetl(prop, "interval.max", -1);
				
				if (lIntMin>=0){
					p.tmin = -lIntMin;
					bMinSet = true;
				}
				
				boolean bMaxSet = false;
				
				if (lIntMax>=0){
					p.tmax = -lIntMax;
					bMaxSet = true;
				}
				
				p = TransparentStoreFactory.normalizePredicate(p);
				
				if (!bMinSet){
					prop.setProperty("interval.min", ""+(-p.tmin));
				}
				
				if (!bMaxSet){
					prop.setProperty("interval.max", ""+(-p.tmax));
				}
				
				tMin = Math.min(p.tmin, tMin);
				tMax = Math.max(p.tmax, tMax);
				
				lPreds.add(p);
				uniquePreds.add(p);
			}
			
			if (lPreds.size()>0)
				mSeries.put(vsNames[i], lPreds);
			else
				logger.log(Level.WARNING, "No predicates are defined for "+sChartName+" / "+vsNames[i]);
		}
		
		long lCompactInterval = -1;
		
		if (sType.equals("history") || sType.equals("histogram") || sType.equals("scatter")){
			// default for history charts, ~1-1.5 pixels per point
			long lPoints = 600;
			
			if (sType.equals("histogram"))
				lPoints = 10000;
			else
			if (sType.equals("scatter"))
				lPoints = 1500;
			
			// override default value with the settings in the configuration file
			lPoints = pgetl(prop, sChartName+".points", pgetl(prop, "points", lPoints)); 
			
			if (lPoints>0){
				lCompactInterval = Math.abs(tMin - tMax) / lPoints;
			
				if (lCompactInterval < pgetl(prop, "compact.min_interval", 60000))
					lCompactInterval = pgetl(prop, "compact.min_interval", 60000);
			}
		}
		
		if (logger.isLoggable(Level.FINE))
			logger.log(Level.FINE, "Compact interval : "+lCompactInterval);
		
		DataSplitter ds;
		
		if (sType.equals("history"))
			ds = ((TransparentStoreFast) TransparentStoreFactory.getStore()).getDataSplitter(uniquePreds.toArray(new monPredicate[0]), lCompactInterval);
		else
			ds = ((TransparentStoreFast) TransparentStoreFactory.getStore()).getUniformDataSplitter(uniquePreds.toArray(new monPredicate[0]), lCompactInterval);

		if (logger.isLoggable(Level.FINE))
			logger.log(Level.FINE, "Got from storage : "+ds);
		
		final List<Data> series = new ArrayList<Data>(vsPreds.length);
				
		for (int i=0; i<vsNames.length; i++){
			final String sName = vsNames[i];
			
			final List<monPredicate> preds = mSeries.get(sName);
			
			if (preds==null || preds.size()==0)
				continue;
			
			final double dDropYBelow = pgetd(prop, sChartName+"."+sName+".dropy_below",
				pgetd(prop, sName+".dropy_below",
					pgetd(prop, sChartName+".dropy_below", 
						pgetd(prop, "dropy_below", -1)
					)
				)
			);
	
			final double dDropYAbove= pgetd(prop, sChartName+"."+sName+".dropy_above",
				pgetd(prop, sName+".dropy_above", 
					pgetd(prop, sChartName+".dropy_above", 
						pgetd(prop, "dropy_above", -1)
					)
				)
			);
			
			final boolean bDropYBelow = dDropYBelow>=0;
			final boolean bDropYAbove = dDropYAbove>=0;

			final double dDivide = pgetd(prop, sChartName+"."+sName+".divide",
					pgetd(prop, sName+".divide", 
						pgetd(prop, sChartName+".divide", 
							pgetd(prop, "divide", 1)
						)
					)
				);

			if (logger.isLoggable(Level.FINER))
				logger.log(Level.FINER, "Drop parameters for "+sChartName+" / "+sName+" : below: "+bDropYBelow+" ("+dDropYBelow+"), above: "+bDropYAbove+" ("+dDropYAbove+")");
			
			final String sSeriesAlias = pgets(prop, sChartName+"."+vsNames[i]+".alias", 
					pgets(prop, vsNames[i]+".alias", vsNames[i])
			);
			
			if (!sType.equals("scatter"))
				addSeries(prop, sSeriesAlias);
			
			final boolean bSomeEnabled = pgetb(prop, "series.some_enabled", false);
			final boolean bThisSeriesEnabled = pgetb(prop, sSeriesAlias+".enabled", false); 
			
			logger.log(Level.FINER, "Series: "+sSeriesAlias+", some enabled="+bSomeEnabled+", this enabled="+bThisSeriesEnabled);
			
			if (bSomeEnabled && !sType.equals("scatter") && !bThisSeriesEnabled)
				continue;
			
			Data d = new Data(sSeriesAlias); 

			final ArrayList<Data> alData = new ArrayList<Data>(preds.size());
			
			for (monPredicate pred: preds){
				final Data dTemp = new Data(pred.toString());
				
				final Vector v = ds.getAndFilter(pred);
					
				if (logger.isLoggable(Level.FINER)){
					logger.log(Level.FINER, "Data vector for "+pred+" is : "+v.size());
					
					//if (logger.isLoggable(Level.FINEST))
					//	logger.log(Level.FINEST, "Results: "+v);
				}
			
				for (Object o : v) {
					if (o != null && o instanceof Result) {
						final Result r = (Result) o;

						if ((bDropYBelow && r.param[0]<dDropYBelow) || (bDropYAbove && r.param[0]>dDropYAbove))
							continue;
					
						dTemp.add(r.time, r.param[0]/dDivide);
					}
				}
				
				alData.add(dTemp);
			}
						
			if (alData.size()==1){
				// in this case we don't have any doubts, we just take the single series we have and plot it
				d.data.addAll(alData.get(0).data);
			}
			else{
				// this case is more complicated. What to do when there are several predicates involved?
				
				final String sFunction = pgets(prop, sChartName+"."+vsNames[i]+".function",
					pgets(prop, sChartName+".function",
						pgets(prop, "function", "sum")
					)
				);
				
				logger.log(Level.FINER, sChartName+"."+vsNames[i]+" : aggregating function = "+sFunction);
				
				final Data dFirst = alData.get(0);
				alData.remove(0);
				
				if (sFunction.equals("diff"))
					d = dFirst.getDiff(alData.get(0), lCompactInterval);
				else
				if (sFunction.equals("avg"))
					d = dFirst.getAvg(alData, lCompactInterval);
				else
					d = dFirst.getSum(alData, lCompactInterval);
				
				d.sName = sSeriesAlias;
			}
			
			if (d.data.size()>0 || sType.equals("scatter")){
				series.add(d);
			}
		}
		
		if (series.size()==0){
			logger.log(Level.FINE, "I have no series to display, returning null");
			
			return null;
		}
		
		Plot plot = null;
		
		if (logger.isLoggable(Level.FINE)){
			logger.log(Level.FINE, "Building chart for type='"+sType+"' with "+series.size()+" series");
		}
		
		if (sType.equals("history")){
			plot = getHistoryChart(prop, sChartName, series);
		}
		else
		if (sType.equals("histogram")){
			plot = getHistogramChart(prop, sChartName, series);
		}
		else
		if (sType.equals("scatter")){
			plot = getScatterChart(prop, sChartName, series, lCompactInterval);
		}
		else{
			logger.log(Level.WARNING, "Unrecognized chart type: '"+sType+"'");
		}
		
		if (logger.isLoggable(Level.FINER)){
			logger.log(Level.FINER, "Generated plot is "+(plot==null ? "null" : plot.getClass().getName()));
		}
		
		return plot;
	}

	/**
	 * @param prop
	 * @param sChartName
	 * @param series
	 * @return
	 */
	private Plot getScatterChart(final Properties prop, final String sChartName, final List<Data> series, final long lCompactInterval) {
		if (series.size()<2)
			return null;
		
		final List<Data> l = new ArrayList<Data>(series.size()/2);
		
		for (int i=0; i<series.size()-1; i+=2){
			l.add(series.get(i).getCorrelated(series.get(i+1), lCompactInterval));
		}
		
		final DefaultXYDataset dataset = new DefaultXYDataset();
		
		final Statistics stats = (Statistics) prop.get("statistics");
		
		final boolean bSomeEnabled = pgetb(prop, "series.some_enabled", false);
		
		for (Data d: l){
			addSeries(prop, d.sName);
			
			final boolean bThisSeriesEnabled = pgetb(prop, d.sName+".enabled", false); 
			
			logger.log(Level.FINER, "Series: "+d.sName+", some enabled="+bSomeEnabled+", this enabled="+bThisSeriesEnabled);
			
			if (bSomeEnabled && !bThisSeriesEnabled)
				continue;
			
			if (d.data.size()>0){
				dataset.addSeries(d.sName, d.getXYDataArray());
				
				prop.setProperty(d.sName+".enabled", "true");
				
				stats.addData(sChartName, d);
			}
		}
		
		final ValueAxis xAxis = new NumberAxis(pgets(prop, sChartName+".xlabel", pgets(prop, "xlabel", null)));
		
		final ValueAxis yAxis = new NumberAxis(pgets(prop, sChartName+".ylabel", pgets(prop, "ylabel", null)));

		setXAxisProperties(prop, sChartName, xAxis);
		
		setYAxisProperties(prop, sChartName, yAxis);
		
		final XYItemRenderer renderer = new StandardXYItemRenderer(StandardXYItemRenderer.SHAPES);
		renderer.setToolTipGenerator(new StandardXYToolTipGenerator());
		
		final XYPlot plot = new XYPlot(dataset, xAxis, yAxis, renderer);
		
		return plot;
	}
	
	/**
	 * @param prop
	 * @param sChartName
	 * @param series
	 * @return
	 */
	private XYPlot getHistogramChart(final Properties prop, final String sChartName, final List<Data> series) {
		if (logger.isLoggable(Level.FINEST)){
			logger.log(Level.FINEST, "parameters: "+sChartName+", "+series);
		}
		
		final HistogramDataset dataset = new HistogramDataset();
		
		final Statistics stats = (Statistics) prop.get("statistics");
		
		for (Data d: series){
			dataset.addSeries(
				d.sName, 
				d.getDataArray(), 
				pgeti(prop, sChartName+".bins", 80)
			);
			
			prop.setProperty(d.sName+".enabled", "true");
			
			stats.addData(sChartName, d);
		}
	
		final String sHistogramType = pgets(prop, sChartName+".histogramtype", "frequency");
		
		HistogramType type = HistogramType.FREQUENCY;
		
		if (sHistogramType.equals("relative"))
			type = HistogramType.RELATIVE_FREQUENCY;
		else
		if (sHistogramType.equals("scale_to_1"))
			type = HistogramType.SCALE_AREA_TO_1;
		
		dataset.setType(type);
		
		final XYBarRenderer renderer = new XYBarRenderer();
		renderer.setDrawBarOutline(false);
		
		final ValueAxis xAxis = new NumberAxis(pgets(prop, sChartName+".xlabel", pgets(prop, "xlabel", null)));
		
		final ValueAxis yAxis = new NumberAxis(pgets(prop, sChartName+".ylabel", pgets(prop, "ylabel", null)));

		setXAxisProperties(prop, sChartName, xAxis);
		
		setYAxisProperties(prop, sChartName, yAxis);
		
		renderer.setToolTipGenerator(new StandardXYToolTipGenerator());
		
		XYPlot plot = new XYPlot(dataset, xAxis, yAxis, renderer);
		
		return plot;
	}

	/**
	 * @param prop
	 * @param series
	 * @return
	 */
	@SuppressWarnings("boxing")
	private XYPlot getHistoryChart(final Properties prop, final String sChartName, final List<Data> series) {
		if (logger.isLoggable(Level.FINEST)){
			logger.log(Level.FINEST, "parameters: "+sChartName+", "+series);
		}
		
		final TimeSeriesCollection tsc = new TimeSeriesCollection();
		
		Double min = null;
		Double max = null;
		
		final Statistics stats = (Statistics) prop.get("statistics");
		
		for (Data d: series){
			tsc.addSeries(d.toTimeseries());
			
			prop.setProperty(d.sName+".enabled", "true");
			
			stats.addData(sChartName, d);
			
			if (min==null){
				min = d.getMinX();
				max = d.getMaxX();
			}
			else{
				min = Math.min(min, d.getMinX());
				max = Math.max(max, d.getMaxX());
			}
		}
		
		logger.log(Level.FINER, "min/max x values are : "+min+" / "+max);
		
		final ValueAxis xAxis = Utils.getValueAxis(prop, (long)min.doubleValue(), (long)max.doubleValue());
		
		final ValueAxis yAxis = new NumberAxis(pgets(prop, sChartName+".ylabel", pgets(prop, "ylabel", null)));
		
		logger.log(Level.FINER, "yaxis : "+yAxis.toString());
				
		//setXAxisProperties(prop, sChartName, xAxis);
		
		logger.log(Level.FINER, "xaxis after properties : "+xAxis.toString());
		
		setYAxisProperties(prop, sChartName, yAxis);
		
		logger.log(Level.FINER, "yaxis after properties : "+yAxis.toString());
		
		final XYItemRenderer renderer = new StandardXYItemRenderer(StandardXYItemRenderer.SHAPES_AND_LINES);
		renderer.setToolTipGenerator(
				new MyXYToolTipGenerator(
						false,
						false,
						"",
						false,
						"",
						"",
						pgets(prop, "labels.date.format", (max - min) < 1000 * 60 * 60 * 24 * 20 ? "MMM d, HH:mm" : "yyyy, MMM d")
				)
		);
		
		XYPlot plot = new XYPlot(tsc, xAxis, yAxis, renderer); 
		
		return plot;
	}

	private static void setXAxisProperties(final Properties prop, final String sChartName, final ValueAxis yAxis){
		setAxisProperties(prop, sChartName, yAxis, 'x');
	}

	private static void setYAxisProperties(final Properties prop, final String sChartName, final ValueAxis yAxis){
		setAxisProperties(prop, sChartName, yAxis, 'y');
	}
	
	private static void setAxisProperties(final Properties prop, final String sChartName, final ValueAxis axis, final char cAxis){
		final boolean bDefaultAutorange = pgetb(prop, sChartName+".autorange", pgetb(prop, "autorange", true));
		
		final boolean bAutorange = pgetb(prop, sChartName+".autorange_"+cAxis, pgetb(prop, "autorange_"+cAxis, bDefaultAutorange)); 
		
		logger.log(Level.FINE, "Setting autorange of "+sChartName+" / "+cAxis+" to : "+bAutorange+" ("+bDefaultAutorange+" is the default)");
		
		final double dMin = pgetd(prop, sChartName+".min"+cAxis, pgetd(prop, "min"+cAxis, -1));
		final double dMax = pgetd(prop, sChartName+".max"+cAxis, pgetd(prop, "max"+cAxis, -1));
		
		axis.setAutoRange(bAutorange || (dMin<0 && dMax<0));

		if (dMin>=0){
			logger.log(Level.FINE, "Setting lower bound to : "+dMin);
			axis.setLowerBound(dMin);
		}
		
		if (dMax>=0){
			logger.log(Level.FINE, "Setting upper bound to : "+dMax);
			axis.setUpperBound(dMax);
		}
	}
	
	/**
	 * @param prop
	 * @param sKey
	 * @return the values as an array
	 */
	public static String[] pgetvs(final Properties prop, final String sKey){
		String sVal = pgets(prop, sKey);
		
		if (sVal==null || sVal.length()==0)
			return new String[0];
		
		return sVal.split(",");
	}
	
}
