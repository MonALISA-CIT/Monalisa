package lia.web.servlets.web;

import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.Vector;

import lia.util.ntp.NTPDate;
import lia.web.utils.Formatare;
import lia.web.utils.ServletExtension;

import org.jfree.chart.urls.XYURLGenerator;
import org.jfree.data.xy.XYDataset;

/**
 * @author costing
 *
 */
public final class MyXYURLGenerator implements XYURLGenerator {
	private final String					sDefaultFormat;

	private static final SimpleDateFormat	sdfDate					= new SimpleDateFormat("yyyy-MM-dd");

	private static final SimpleDateFormat	sdfTime					= new SimpleDateFormat("HH:mm:ss");

	private final Properties				prop;
	
	private boolean						bIntervalStart;

	private boolean						bIntervalEnd;

	private boolean						bStartDate;

	private boolean						bStartTime;

	private boolean						bEndDate;

	private boolean						bEndTime;

	private boolean						bValue;

	private boolean						bSeriesName;
	
	private boolean						bRealName;
	
	private boolean						bAltName;

	private boolean						bStart;

	private boolean						bEnd;

	private boolean						bSkipEvery2				= true;

	private Vector<String>				vAlternateSeriesNames	= null;
		
	private boolean						bPrefferAlternateNames	= true;

	/**
	 * @param properties
	 */
	public MyXYURLGenerator(final Properties properties) {
		prop = properties;
		
		sDefaultFormat = ServletExtension.pgets(properties, "url.format", null);
	}
	
	private String sPrevFormat = null;
	
	private void initVars(final String sFormat){
		if (sFormat == null || sFormat.length() <= 0) {
			bIntervalStart = false;
			bIntervalEnd = false;
			bStartDate = false;
			bStartTime = false;
			bEndDate = false;
			bEndTime = false;
			bValue = false;
			bSeriesName = false;
			bAltName = false;
			bRealName = false;

			bStart = false;
			bEnd = false;

			sPrevFormat = null;
			
			return;
		}
		
		if (sFormat.equals(sPrevFormat))
			return;

		bIntervalStart = sFormat.indexOf("{SS}") >= 0;
		bIntervalEnd = sFormat.indexOf("{ES}") >= 0;
		bStartDate = sFormat.indexOf("{SD}") >= 0;
		bStartTime = sFormat.indexOf("{ST}") >= 0;
		bEndDate = sFormat.indexOf("{ED}") >= 0;
		bEndTime = sFormat.indexOf("{ET}") >= 0;
		bValue = sFormat.indexOf("{V}") >= 0;
		bSeriesName = sFormat.indexOf("{S}") >= 0;
		bRealName = sFormat.indexOf("{R}") >= 0;
		bAltName = sFormat.indexOf("{A}") >= 0;

		bStart = bIntervalStart || bStartDate || bStartTime;
		bEnd = bIntervalEnd || bEndDate || bEndTime;
		
		sPrevFormat = sFormat;
	}

	/**
	 * @param b
	 */
	public void setSkipEvery2(final boolean b) {
		bSkipEvery2 = b;
	}

	/**
	 * @param v
	 */
	public void setAlternateSeriesNames(final Vector<String> v) {
		vAlternateSeriesNames = v;
	}
	
	/**
	 * @param b
	 */
	public void setPrefferAlternateNames(final boolean b){
		bPrefferAlternateNames = b;
	}
	
	@Override
	public String generateURL(final XYDataset data, final int series, final int item) {
		final String sRealName = data!=null && data.getSeriesKey(series)!=null ? data.getSeriesKey(series).toString() : null;
		final String sAltName = vAlternateSeriesNames!=null && series<vAlternateSeriesNames.size() ? (String) vAlternateSeriesNames.get(series) : null;
		
		String sSeriesName = null;

		if (bPrefferAlternateNames)
			sSeriesName = sAltName != null ? sAltName : sRealName;
		else
			sSeriesName = sRealName != null ? sRealName : sAltName;
		
		if (sSeriesName==null)
			return null;
		
		String url = ServletExtension.pgets(prop, sSeriesName+".url.format", sDefaultFormat);
			
		if (
				(bSkipEvery2 && series % 2 == 1) || 
				url == null || url.length() <= 0 || 
				ServletExtension.pgetb(prop, sSeriesName+".url.enabled", true)==false
		)
			return null;

		initVars(url);
		
		if (bSeriesName)
			url = Formatare.replace(url, "{S}", sSeriesName);
		
		if (bRealName){
			if (sRealName==null)
				return null;
			
			url = Formatare.replace(url, "{R}", sRealName);
		}
		
		if (bAltName){
			if (sAltName==null)
				return null;
			
			url = Formatare.replace(url, "{A}", sAltName);
		}

		if (bStart && data!=null) {
			final long ls = ((long) data.getXValue(series, item));
			final Date ds = new Date(ls);

			if (bIntervalStart)
				url = Formatare.replace(url, "{SS}", "" + ls);

			if (bStartDate)
				url = Formatare.replace(url, "{SD}", Formatare.encode(sdfDate.format(ds, new StringBuffer(), new FieldPosition(1)).toString()));

			if (bStartTime)
				url = Formatare.replace(url, "{ST}", Formatare.encode(sdfTime.format(ds, new StringBuffer(), new FieldPosition(1)).toString()));
		}

		if (bEnd) {
			final long le = NTPDate.currentTimeMillis();
			final Date de = new Date(le);

			if (bIntervalEnd)
				url = Formatare.replace(url, "{ES}", "" + le);

			if (bEndDate)
				url = Formatare.replace(url, "{ED}", Formatare.encode(sdfDate.format(de, new StringBuffer(), new FieldPosition(1)).toString()));

			if (bEndTime)
				url = Formatare.replace(url, "{ET}", Formatare.encode(sdfTime.format(de, new StringBuffer(), new FieldPosition(1)).toString()));
		}

		if (bValue && data!=null)
			url = Formatare.replace(url, "{V}", "" + ((long) data.getYValue(series, item)));

		return url;
	}
}
