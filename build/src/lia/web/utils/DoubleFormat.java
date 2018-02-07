package lia.web.utils;

/**
 * String formatting functions
 * 
 * @author costing
 * @since forever
 */
public final class DoubleFormat {

	/**
	 * Display this value as a human readable size
	 * 
	 * @param d
	 * @return human readable size in bytes
	 */
	public static String size(final double d) {
		return size(d, "");
	}

	/**
	 * Format a number as size
	 * 
	 * @param d
	 * @param dMax
	 * @param sSizeIn
	 * @param bInBits
	 * @param bSize
	 * @param sMeasure
	 * @return size
	 */
	public static String size(final double d, final double dMax, final String sSizeIn, final boolean bInBits, final boolean bSize, final String sMeasure) {
		String sTemp;

		if (bSize) {
			sTemp = size(dMax > 0.001 || dMax < -0.001 ? d : 0, sSizeIn, bInBits);

			if (sTemp.toLowerCase().endsWith("b") && sMeasure.toLowerCase().startsWith("b"))
				sTemp = sTemp.substring(0, sTemp.length() - 1) + sMeasure;
			else
				sTemp = sTemp + sMeasure;
		} else {
			sTemp = point(dMax > 0.001 || dMax < -0.001 ? d : 0);
		}

		return sTemp;
	}

	/**
	 * Format a size
	 * 
	 * @param d
	 * @param sSize
	 * @param bBit
	 * @return size
	 */
	public static String size(final double d, final String sSize, final boolean bBit) {
		return bBit ? size_bit(d, sSize) : size(d, sSize);
	}

	/**
	 * Format a size in bytes
	 * 
	 * @param d
	 * @param sSize
	 * @return size in bytes
	 */
	public static String size(final double d, final String sSize) {
		return size(d, sSize, 1024d);
	}

	/**
	 * Generic size formatter
	 * 
	 * @param _d
	 * @param _sSize
	 * @param dDiv
	 * @return size
	 */
	public static final String size(final double _d, final String _sSize, final double dDiv) {
		String sSize = _sSize.toUpperCase();

		final boolean bMinus = _d<0;
		
		double d = Math.abs(_d);
		if (dDiv>1){
			for (int i=0; i<10 && d > dDiv; i++) {
				d /= dDiv;
	
				if (sSize.equals("") || sSize.equals("B"))
					sSize = "K";
				else if (sSize.equals("K"))
					sSize = "M";
				else if (sSize.equals("M"))
					sSize = "G";
				else if (sSize.equals("G"))
					sSize = "T";
				else if (sSize.equals("T"))
					sSize = "P";
				else if (sSize.equals("P"))
					sSize = "X";
			}
	
			for (int i=0; i<10 && d < 0.1d && sSize.length() > 0 && !sSize.equals("B"); i++) {
				d *= dDiv;
	
				switch (sSize.charAt(0)) {
					case 'X':
						sSize = "P";
						break;
					case 'P':
						sSize = "T";
						break;
					case 'T':
						sSize = "G";
						break;
					case 'G':
						sSize = "M";
						break;
					case 'M':
						sSize = "K";
						break;
					case 'K':
						sSize = "B";
						break;
				}
			}
		}

		if (dDiv < 1024d && sSize.equals("B"))
			sSize = "b";

		String sRez = point(d);
		
		if (bMinus && !sRez.equals("0"))
			sRez = "-"+sRez;

		return sRez + (sSize.equals("") ? "" : (" " + sSize));
	}

	/**
	 * Format a size in bits (divide by 1000)
	 * 
	 * @param d
	 * @param sSize
	 * @return size in bits
	 */
	public static String size_bit(final double d, final String sSize) {
		return size(d, sSize, 1000d);
	}

	/**
	 * Show a nice number, with the number of decimal places chosen automatically depending
	 * on the number to format 
	 * 
	 * @param _d
	 * @return nice floating point number representation
	 */
	public static String point(final double _d) {
		String sRez;

		final boolean bMinus = _d<0;
		
		double d = Math.abs(_d);
		
		if (d < 10) {
			d = Math.round(d * 1000) / 1000.0d;
			sRez = "" + d;
		} else if (d < 100) {
			d = Math.round(d * 100) / 100.0d;
			sRez = "" + d;
		} else if (d < 1000) {
			d = Math.round(d * 10) / 10.0d;
			sRez = "" + d;
		} else {
			sRez = "" + ((long) d);
		}

		while (sRez.indexOf(".") >= 0 && sRez.endsWith("0")) {
			sRez = sRez.substring(0, sRez.length() - 1);
		}

		if (sRez.endsWith("."))
			sRez = sRez.substring(0, sRez.length() - 1);

		if (bMinus && !sRez.equals("0"))
			sRez = "-"+sRez;
		
		return sRez;
	}

	/**
	 * Debug method
	 * 
	 * @param args
	 */
	public static void main(String args[]){
		String sSizeIn = "B";
		boolean bInBits = false;
		boolean bSize = true;
		String sMeasureOrig = "";
		
		//for (double last=-6555.3333333; last<=1000; last += 100)
			//System.err.println(size(last, last, sSizeIn, bInBits, bSize, sMeasureOrig));
		
		System.err.println(size(-0.000000000001, 0.1, sSizeIn, bInBits, bSize, sMeasureOrig));
	}
	
}
