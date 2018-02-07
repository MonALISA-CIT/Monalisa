package lia.web.servlets.web;

import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import lia.web.utils.Annotation;
import lia.web.utils.DoubleFormat;
import lia.web.utils.Formatare;

import org.jfree.chart.labels.StandardXYToolTipGenerator;

/**
 * @author costing
 *
 */
public class MyXYToolTipGenerator extends StandardXYToolTipGenerator {
	private static final long			serialVersionUID		= 6221460327770145265L;

	private boolean						ignoreZero, size, bits;

	private String						sizeIn, suffix, prefix;

	private java.text.SimpleDateFormat	sdf;

	private Vector<String>						vAlternateSeriesNames	= null;

	private transient AnnotationCollection       ac = null;

	/**
	 * @param bIgnoreZero
	 * @param bSize
	 * @param sSizeIn
	 * @param bBits
	 * @param sPrefix
	 * @param sSuffix
	 * @param sDateFormat
	 */
	public MyXYToolTipGenerator(boolean bIgnoreZero, boolean bSize, String sSizeIn, boolean bBits, String sPrefix, String sSuffix, String sDateFormat) {
		super();

		ignoreZero = bIgnoreZero;
		size = bSize;
		sizeIn = sSizeIn;
		suffix = sSuffix;
		prefix = sPrefix;
		bits = bBits;

		sdf = new java.text.SimpleDateFormat(sDateFormat);

		if (sPrefix.endsWith("_"))
			prefix = sPrefix.substring(0, sPrefix.length() - 1) + " ";

		if (sSuffix.startsWith("_"))
			suffix = " " + sSuffix.substring(1);
	}

	/**
	 * @param v
	 */
	public void setAlternateSeriesNames(final Vector<String> v) {
		vAlternateSeriesNames = v;
	}
	
	/**
	 * @param collection
	 */
	public void setAnnotations(final AnnotationCollection collection){
		ac = collection;
	}

	@Override
	public String generateToolTip(final org.jfree.data.xy.XYDataset dataset, final int series, final int item) {
		try {
			if (dataset.getY(series, item) == null)
				return null;

			String sSeriesName = null;

			if (dataset.getSeriesKey(series) != null)
				sSeriesName = dataset.getSeriesKey(series).toString();
			else if (vAlternateSeriesNames != null && series < vAlternateSeriesNames.size())
				sSeriesName = vAlternateSeriesNames.get(series);

			if (sSeriesName == null)
				return null;

			final double d = dataset.getY(series, item).doubleValue();

			if (ignoreZero && d < 1E-10)
				return null;

			final long lTime = dataset.getX(series, item).longValue();
			
			final String sDate = sdf.format(new java.util.Date(lTime));

			String sVal = size ? DoubleFormat.size(d, sizeIn, bits) : DoubleFormat.point(d);

			if (sVal.toLowerCase().endsWith("b") && (suffix.toLowerCase().startsWith("b") || suffix.length() == 0))
				sVal = sVal.substring(0, sVal.length() - 1);

			String sRet = sSeriesName + ": (" + sDate + ") " + prefix + sVal + suffix;
			
			if (ac!=null){
				Iterator<Annotation> it = ac.getChartAnnotations().iterator();
				
				boolean bFirst = true;
				
				while (it.hasNext()){
					final Annotation a = it.next();
					
					if (a.bValue)
						continue;
					
					if (lTime>=a.from && lTime<=a.to){
						if (bFirst){
							sRet += "<br>";
							bFirst = false;
						}
						sRet += "<br><font color=#"+Utils.toHex(a.textColor)+"><b>"+Formatare.jsEscape(a.text)+"</b></font>";
					}
				}
				
				if (!bFirst)
					sRet += "<br>";

				List<Annotation> l = ac.getSeriesAnnotations(sSeriesName);
				
				if (l != null) {
					it = l.iterator();

					while (it.hasNext()) {
						final Annotation a = it.next();

						if (lTime >= a.from && lTime <= a.to)
							sRet += "<br>" + Formatare.jsEscape(a.text);
					}
				}
			}
			
			return sRet;
		} catch (Exception e) {
			System.err.println(e);
			e.printStackTrace();
		}

		return null;
	}

}
