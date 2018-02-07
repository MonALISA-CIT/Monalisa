package lia.web.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.StringTokenizer;

import lia.Monitor.monitor.monPredicate;

/**
 * Various formatting functions
 * 
 * @author costing
 * @since forever
 */
public final class Formatare {

	/**
	 * Nice integer representation, for sizes
	 * 
	 * @param dim
	 * @return integer representation as size
	 */
	public static String intFormat(final int dim) {
		return intFormat((long) dim);
	}

	/**
	 * Nice integer representation, for sizes
	 * 
	 * @param dim
	 * @return integer representation as size
	 */
	public static String intFormat(long dim) {
		String s;

		if (dim < 1024) {
			s = dim + "";
			return s;
		}

		if (dim < 1024 * 1024) { //dimensiune in K
			s = "" + (dim / 1024);
			if ((dim < 1024 * 100) && ((dim % 1024) / 102 != 0))
				s += "." + ((dim % 1024) / 102);
			s += " Kb";
			return s;
		}

		s = "" + (dim / (1024 * 1024));
		if ((dim % (1024 * 1024)) / 104857 != 0)
			s += "." + ((dim % (1024 * 1024)) / 104857);
		s += " Mb";
		return s;
	}

	private static final char[] tagChars     = new char[] {    '&',    '<',    '>',      '"',     '\'',  '$'};
	private static final String[] tagStrings = new String[]{"&amp;", "&lt;", "&gt;", "&quot;", "&apos;", "&#36;"};
	
	/**
	 * HTML escaping
	 * 
	 * @param line
	 * @return HTML-escaped string
	 */
	public static String tagProcess(final String line) {
		return replaceChars(line, tagChars, tagStrings);
	}

	private static final char[] sqlChars    = new char[] {  '\\',   '\'',    '"',  '\n',  '\r', (char)0};
	private static final String[] sqlStrings =new String[]{"\\\\", "\'\'", "\\\"", "\\n", "\\r",    "\\0"}; 
	
	/**
	 * SQL escaping
	 * 
	 * @param s
	 * @return SQL-safe string
	 */
	public static String mySQLEscape(final String s) {
		return replaceChars(s, sqlChars, sqlStrings);
	}

	private static final char[] jsChars    = new char[]  {     '&',   '\\',      '"',   '\'',  '\n',  '\r',    '>',    '<' };
	private static final String[] jsStrings = new String[]{ "&amp;", "\\\\", "&quot;", "\\\'", "\\n", "\\r", "&gt;", "&lt;" };
	
	/**
	 * JavaScript escaping
	 * 
	 * @param s
	 * @return JS-safe string
	 */
	public static String jsEscape(final String s) {
		return replaceChars(s, jsChars, jsStrings);		
	}

	private static final char[] jsChars2    = new char[]  {   '\\',      '"',   '\'',  '\n',  '\r' };
	private static final String[] jsStrings2 = new String[]{ "\\\\", "&quot;", "\\\'", "\\n", "\\r" };

	/**
	 * Another version of JS escaping
	 * 
	 * @param s
	 * @return JS-safe string
	 */
	public static String jsEscape2(final String s){
		return replaceChars(s, jsChars2, jsStrings2);
	}

	/**
	 * Fast method to replace some characters with some strings
	 * 
	 * @param sOrig
	 * @param chars
	 * @param with
	 * @return original string with any of the characters in 'chars' replaced by the corresponding string in 'with'
	 */
	public static String replaceChars(final String sOrig, final char[] chars, final String[] with){
		if (chars==null || with==null || chars.length==0 || chars.length!=with.length || sOrig==null)
			return null;
		
		final StringBuilder sb = new StringBuilder(sOrig.length());
		
		final char[] vc = sOrig.toCharArray();
		
		final int charsLength = chars.length;
		
		nextchar:
		for (int i=0; i<vc.length; i++){
			for (int j=charsLength-1; j>=0; j--)
				if (vc[i] == chars[j]){
					sb.append(with[j]);
					continue nextchar;
				}
			
			sb.append(vc[i]);
		}
		
		return sb.toString();
	}
	
	/**
	 * Short memory for number of replacements
	 */
	private static volatile int	iOldReplacements1	= 2;

	/**
	 * Another short memory for number of replacements
	 */
	private static volatile int	iOldReplacements2	= 2;

	/**
	 * Replace a sequence of text with another sequence in an original string
	 * 
	 * @param s original text
	 * @param sWhat what to search and replace
	 * @param sWith the new text to put in place
	 * @return the modified text
	 */
	public static String replace(final String s, final String sWhat, final String sWith) {
		if (s==null || sWhat==null || sWhat.length()==0)
			return s;

		StringBuilder sb = null;

		final int iWhatLen = sWhat.length();
		final int iWithLen = sWith.length();
		
		int iOld = 0;
		int i = 0;
		int iReplacements = 0;
		
		while ((i = s.indexOf(sWhat, iOld)) >= 0) {
			if (sb == null) {
				final int diff = (iWhatLen - iWithLen) * (iOldReplacements1 * 2 + iOldReplacements2 + 1) / 3;
				sb = new StringBuilder(s.length() + (diff > 0 ? diff : 0));
			}

			if (iOld<i)
				sb.append(s.substring(iOld, i));
			
			sb.append(sWith);
			
			iOld = i + iWhatLen;
			
			iReplacements++;
		}

		iOldReplacements2 = iOldReplacements1;
		iOldReplacements1 = iReplacements;

		if (sb != null) {
			sb.append(s.substring(iOld));
			return sb.toString();
		}
		
		// no replacements to do
		return s;
	}


	/**
	 * Get the URLEncoded version of the string, using the UTF-8 encoding
	 * 
	 * @param s
	 * @return UTF-8 URLEncoded version of the original string
	 */
	public static String encode(final String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			// this will never happen (hopefully :) )
			return null;
		}
	}

	/**
	 * Decode a URLEncoded string using the UTF-8 encoding 
	 * 
	 * @param s
	 * @return UTF-8 decoded version of the string
	 */
	public static String decode(final String s) {
		try {
			return URLDecoder.decode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			// this will never happen 
			return null;
		}
	}
	
	private static String mergePredParts(final String s1, final String s2){
		if (s1==null || s1.length()==0)
			return s2!=null ? s2 : "";
		
		if (s2==null || s2.length()==0)
			return s1;
		
		return s1+"/"+s2;
	}

	/**
	 * Parse the given string and convert it in a {@link monPredicate} object
	 * 
	 * @param s
	 * @return the {@link monPredicate} object that corresponds to this string
	 */
	public static monPredicate toPred(final String s) {
		if (s == null || s.trim().length() <= 0)
			return null;

		final StringTokenizer st = new StringTokenizer(s, "/");

		if (!st.hasMoreTokens())
			return null;

		final String sFarm = st.hasMoreTokens() ? st.nextToken() : "*";
		final String sCluster = st.hasMoreTokens() ? st.nextToken() : "*";
		final String sNode = st.hasMoreTokens() ? st.nextToken() : "*";

		final String sTime1 = st.hasMoreTokens() ? st.nextToken() : "";
		final String sTime2 = st.hasMoreTokens() ? st.nextToken() : "";
		
		// The default format is F/C/N/time1/time2/P
		// but it accepts two alternatives:
		// F/C/N/time1/P	with time2 = -1
		// F/C/N/P			with time1 = time2 = -1
		// the alternatives are chosen based on the existence of the other parameters and on the fact that they are numeric or not
		String sFunc = st.hasMoreTokens() ? st.nextToken("") : null;

		if (sFunc!=null && sFunc.startsWith("/"))
			sFunc = sFunc.substring(1);
		
		long lTime1 = -1;
		long lTime2 = -1;

		try {
			lTime1 = Long.parseLong(sTime1);

			try {
				lTime2 = Long.parseLong(sTime2);
			}
			catch (Exception e) {
				sFunc = mergePredParts(sTime2, sFunc);
			}
		}
		catch (Exception e) {
			sFunc = mergePredParts(mergePredParts(sTime1, sTime2), sFunc);
		}

		String[] vsFunc;
		if (sFunc == null) {
			vsFunc = null;
		} 
		else {
			final ArrayList<String> v = new ArrayList<String>();
			final StringTokenizer st2 = new StringTokenizer(sFunc, "|");

			while (st2.hasMoreTokens()) {
				v.add(st2.nextToken().trim());
			}

			if (v.size() > 0) {
				vsFunc = new String[v.size()];
				for (int j = 0; j < v.size(); j++)
					vsFunc[j] = v.get(j);
			} else {
				vsFunc = null;
			}
		}

		return new monPredicate(sFarm, sCluster, sNode, lTime1, lTime2, vsFunc, null);
	}

    /**
     * From the given string get the real name of the user 
     * 
     * @param from
     * @return the name of the user from the full email address
     */
    public static String extractMailName(final String from) {
        if (from.length() <= 0) return "";

        final StringTokenizer st = new StringTokenizer(from, "<>\"'`");

        if (st.hasMoreTokens()){
            String s = st.nextToken().trim();
            
            if (s.indexOf('@')>0)
            	s = s.substring(0, s.indexOf('@'));
            
            return s;
        }
        
        return from;
    }

    /**
     * Extract the actual email address from the complex email string
     * 
     * @param line
     * @return the email address
     */
    public static String extractMailAddress(final String line) {
        final StringTokenizer st = new StringTokenizer(line, "<>\"'`");

        String s = "";
        while (st.hasMoreTokens()){
            s = st.nextToken();
        }

        return s.trim();
    }

	/**
	 * Display nicely an interval given in milliseconds
	 * 
	 * @param lInterval
	 * @return nice human representation
	 */
	public static final String showInterval(final long lInterval) {
		if (lInterval<0)
			return "-";
		
		long lMillis = lInterval%1000;
		
		long l = lInterval / 1000;

		final long s = l%60;
		l /= 60;
		final long m = l % 60;
		l /= 60;
		final long h = l % 24;
		l /= 24;
		final long d = l;

		String sResult;
		
		if (d>0){
			sResult = d + " day" + (d != 1 ? "s" : "") + ", " +
				(h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
		}
		else
		if (h>0){
			sResult = h + ":" + (m < 10 ? "0" : "") + m; 
		}
		else
		if (m>0){
			sResult = m +"m "+s+"s";
		}
		else{
			sResult = DoubleFormat.point(s+lMillis/1000d)+"s";
		}
		
		return sResult;
	}

	/**
	 * Strip a text of any HTML markups it might have
	 * 
	 * @param s
	 * @return the plain text from the HTML-rich source
	 */
	public static String stripHTML(final String s) {
		final StringBuilder sb = new StringBuilder(s.length());

		int iOld = 0;
		int idx;
		
		boolean bDel = false;
		
		while ( (idx=s.indexOf('<', iOld)) >= 0){
			if (idx>iOld){
				if (bDel && sb.length()>0)
					sb.append(' ');
				
				sb.append(s.substring(iOld, idx));
			}
			
			int iEnd = s.indexOf('>', idx);
			
			if (iEnd>0){
				bDel = true;				
			}
			else{
				// last tag doesn't close, ignore
				iOld = s.length();
				break;
			}
			
			iOld = iEnd+1;
		}
		
		if (bDel && sb.length()>0)
			sb.append(' ');
		
		sb.append(s.substring(iOld));
		
		String sRet = sb.toString();
		
		for (int i=0; i<tagStrings.length; i++){
			sRet = replace(sRet, tagStrings[i], new String(new char[]{tagChars[i]}));
		}
		
		return sRet;
	}
	

	private final static SimpleDateFormat[]	sdfFormats	= new SimpleDateFormat[] { 
				new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z"), // 0
				new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"), // 1
				new SimpleDateFormat("yyyy-MM-dd"), // 2
				new SimpleDateFormat("dd.MM.yyyy HH:mm:ss Z"), // 3
				new SimpleDateFormat("dd.MM.yyyy HH:mm:ss"), // 4
				new SimpleDateFormat("dd.MM.yyyy"), // 5
				new SimpleDateFormat("MM/dd/yyyy HH:mm:ss Z"), // 6
				new SimpleDateFormat("MM/dd/yyyy HH:mm:ss"), // 7
				new SimpleDateFormat("MM/dd/yyyy"), // 8
				new SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z"), // 9
				new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"), // 10
				new SimpleDateFormat("yyyy/MM/dd"), // 11
				new SimpleDateFormat("MMM dd HH:mm:ss zzzz yyyy"), // 12 - Mar 14 13:04:30 CET 2007
				new SimpleDateFormat("EEE MMM dd HH:mm:ss zzzz yyyy"), // 13 - Wed Mar 14 13:04:30 CET 2007
	};

	/**
	 * Transform a string that represents a Date into a real Date object. It will try several formats, including
	 * date-only and time-only representations.
	 * 
	 * @param s string to convert
	 * @return date representation, or null if the conversion was not possible.
	 */
	@SuppressWarnings("deprecation")
	public static final Date parseDate(final String s) {
		if (s==null || s.length()==0)
			return null;
		
		// first try the default Date constructor, maybe it is in this format
		try{
			return new Date(s);
		}
		catch (IllegalArgumentException ile){
			//System.err.println("Date parser didn't work");
		}
		
		try{
			long l = Long.parseLong(s);
			
            if (l<2000000000)
                l*=1000;

            return new Date(l);
		}
		catch (NumberFormatException nfe){
			// ignore
		}
		
		// try all the date formats
		for (int i = 0; i < sdfFormats.length; i++) {
			try {
				Date d = sdfFormats[i].parse(s);
				
				//System.err.println("Parser ok : "+i);
				
				return d;
			}
			catch (ParseException e) {
				// ignore this
			}
		}

		// if nothing worked so far, maybe this is a time only, so try to add the current date to it and parse it again 
		final String sNew = sdfFormats[2].format(new Date()) + " " + s;
			
		for (int i = 0; i < 2; i++){
			try {
				return sdfFormats[i].parse(sNew);
			}
			catch (ParseException e) {
				// ignore this too
			}			
		}
		
		return null;
	}

	
	/**
	 * Debug method
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		//System.err.println(stripHTML("asdf<a href=x bubu</a><a></a>gi&amp;&apos;gi<br blabla"));
		System.err.println(toPred("CIT_CMS_T2/DiskIO/%/md0_kB_read/s|md0_kB_write/s|sdb_kB_read/s|sdb_kB_write/s|sdc_kB_read/s|sdc_kB_write/s|sdd_kB_read/s|sdd_kB_write/s|sde_kB_read/s|sde_kB_write/s"));
		System.err.println(toPred("F/C/N/-1/-1/blabla/s|bubu/s|gigi/s"));
		System.err.println(toPred("F/C/N/-1/blabla/s|bubu/s|gigi/s"));
		System.err.println(toPred("F/C/N/blabla/s|bubu/s|gigi/s"));
		System.err.println(toPred("F/C/N"));
		System.err.println(toPred("F/C"));
		System.err.println(toPred("F/*/*/-2/-34/aaa/aaa/aaa|bbbbbb/1/1/2/3/"));
		System.err.println(toPred(""));
	}

}
