package lia.web.utils;

import java.util.Comparator;
import java.util.Date;
import java.util.StringTokenizer;

/**
 * @author costing
 *
 */
public class MailDate implements Comparable<MailDate>, Comparator<MailDate> {
	/**
	 * day
	 */
	public int					day;
	
	/**
	 * month
	 */
	public int					month;
	
	/**
	 * year
	 */
	public int					year;
	
	/**
	 * Day of week
	 */
	public int					dow;
	
	/**
	 * Hour
	 */
	public int					hour;
	
	/**
	 * Minutes
	 */
	public int					min;
	
	/**
	 * Seconds
	 */
	public int					sec;

	/**
	 * GMT offset
	 */
	public String				sDeplasareGMT;
	
	/**
	 * Local time zone
	 */
	public String				sLocalZone;

	private Date				date;

	private String				sOrigDate;

	/**
	 * 
	 */
	static final String			sShortDows[]		= { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };

	/**
	 * 
	 */
	static final String			sLongDows[]			= { "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" };

	/**
	 * 
	 */
	static final String			sLongMonths[]		= { "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December" };

	/**
	 * 
	 */
	static final String			sShortMonths[]		= { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };

	private static final String	sLowerShortDows[]	= new String[7];

	private static final String	sLowerLongDows[]	= new String[7];

	private static final String	sLowerLongMonths[]	= new String[12];

	private static final String	sLowerShortMonths[]	= new String[12];

	static {
		int i;
		for (i = 0; i < 7; i++) {
			sLowerShortDows[i] = sShortDows[i].toLowerCase();
			sLowerLongDows[i] = sLongDows[i].toLowerCase();
		}

		for (i = 0; i < 12; i++) {
			sLowerShortMonths[i] = sShortMonths[i].toLowerCase();
			sLowerLongMonths[i] = sLongMonths[i].toLowerCase();
		}
	}

	@SuppressWarnings("unused")
	private MailDate() {
		 //inhibam acest constructor
	}

	private void initLocalData() {
		day = 0;
		month = 0;
		year = 0;
		dow = 0;
		hour = 0;
		min = 0;
		sec = 0;
		sDeplasareGMT = "";
		sLocalZone = "";
	}

	/**
	 * @param dParam
	 */
	@SuppressWarnings("deprecation")
	public MailDate(Date dParam) {
		year = dParam.getYear() + 1900;
		month = dParam.getMonth();
		day = dParam.getDate();
		dow = dParam.getDay();
		hour = dParam.getHours();
		min = dParam.getMinutes();
		sec = dParam.getSeconds();
		sOrigDate = dParam.toString();
	}

	private void processHMS(String sHMS) {
		StringTokenizer st = new StringTokenizer(sHMS, ": ");
		try {
			hour = Integer.parseInt(st.nextToken());
			min = Integer.parseInt(st.nextToken());
			sec = 0;
			if (st.hasMoreTokens())
				sec = Integer.parseInt(st.nextToken());
		} catch (Exception e) {
			//Log.log(4,"MailDate","processHMS","", "HMS string: " + sHMS);
		}
	}

	/**
	 * @param data
	 */
	@SuppressWarnings("deprecation")
	public void oldProcessor(final String data) {
		try {
			int i;
			int iType;
			StringTokenizer st = new StringTokenizer(data.toLowerCase(), " ,()");
			sOrigDate = data;
			String s;

			s = st.nextToken();
			dow = -1;
			for (i = 0; i < 7; i++)
				if (sLowerLongDows[i].startsWith(s)) {
					dow = i;
					break;
				}

			iType = 1;
			if (dow >= 0) { //altfel inseamna ca incepea fara ziua saptamanii
				s = st.nextToken();
				iType = 2;
			}

			for (i = 0; i < 12; i++)
				if (sLowerLongMonths[i].startsWith(s)) {
					iType = 3;
					break;
				}

			// iType possible values
			// 1 : 29 Nov 2000 23:12:33 -0000
			// 2 : Fri, 8 Dec 2000 09:19:11 +0200 (EET)
			// 3 : Mon Dec 18 14:29:25 2000
			//     Wed Dec 20 16:38:00 GMT+02:00 2000

			if (iType < 3) {
				day = Integer.parseInt(s);

				s = st.nextToken();
				month = -1;
				for (i = 0; i < 12; i++)
					if (sLowerShortMonths[i].startsWith(s)) {
						month = i;
						break;
					}

				year = Integer.parseInt(st.nextToken());

				processHMS(st.nextToken());
			} else {
				month = i;
				day = Integer.parseInt(st.nextToken());

				processHMS(st.nextToken());

				String sTemp = st.nextToken();
				try {
					year = Integer.parseInt(sTemp);
				} catch (Exception e) {
					sDeplasareGMT = sTemp;
					year = Integer.parseInt(st.nextToken());
				}
			}

			if (st.hasMoreTokens())
				sDeplasareGMT = st.nextToken();
			if (st.hasMoreTokens())
				sLocalZone = st.nextToken();

			if (year < 30)
				year += 2000;
			else if (year < 100)
				year += 1900;

			date = new Date(year - 1900, month, day, hour, min, sec);

			year = date.getYear() + 1900;
			month = date.getMonth();
			day = date.getDate();
			dow = date.getDay();
			hour = date.getHours();
			min = date.getMinutes();
			sec = date.getSeconds();
		} catch (Exception e) {
			//Log.log(Log.WARNING, Log.COMMON, "MailDate", "oldProcessor", data, e.getMessage());
		}
	}

	/**
	 * @param data
	 */
	@SuppressWarnings("deprecation")
	public MailDate(String data) {
		initLocalData();

		@SuppressWarnings("hiding")
		Date date = new Date();
		year = date.getYear() + 1900;
		month = date.getMonth();
		day = date.getDate();
		dow = date.getDay();
		hour = date.getHours();
		min = date.getMinutes();
		sec = date.getSeconds();

		sOrigDate = data;

		try {
			date = new Date(data);
			year = date.getYear() + 1900;
			month = date.getMonth();
			day = date.getDate();

			dow = date.getDay();
			hour = date.getHours();
			min = date.getMinutes();
			sec = date.getSeconds();
		} catch (Exception e) {
			oldProcessor(data);
		}
	}

	/**
	 * @param mdParam
	 */
	@SuppressWarnings("deprecation")
	public MailDate(MailDate mdParam) {
		day = mdParam.day;
		month = mdParam.month;
		year = mdParam.year;

		dow = mdParam.dow;
		hour = mdParam.hour;
		min = mdParam.min;
		sec = mdParam.sec;
		sDeplasareGMT = new String(mdParam.sDeplasareGMT);
		sLocalZone = new String(mdParam.sLocalZone);
		sOrigDate = (mdParam.sOrigDate != null) ? mdParam.sOrigDate : (new Date(year - 1900, month, day, hour, min, sec)).toString();
	}

	private static String hmFormat(int val) {
		if (val < 0)
			return ("00");
		if (val < 10)
			return ("0" + val);
		return ("" + val);
	}

	@SuppressWarnings("deprecation")
	@Override
	public String toString() {
		Date dCurrent = new Date();
		String sResult;

		if (dCurrent.getYear() + 1900 != year) {
			sResult = day + " " + sShortMonths[month] + " " + year;
		} else if (dCurrent.getMonth() != month) {
			sResult = day + " " + sLongMonths[month];
		} else if (((dCurrent.getDate() < day - 1) || (dCurrent.getDate() > day + 1)) && (dow >= 0)) { // nu e prea corect, dar merge pentru 29 de zile din 31 :)
			sResult = sLongDows[dow] + " " + hmFormat(day);
		} else {
			if (dCurrent.getDate() > day)
				sResult = "Yesterday";
			else if (dCurrent.getDate() == day)
				sResult = "Today";
			else
				sResult = "Tomorrow";

			sResult += " " + hmFormat(hour) + ":" + hmFormat(min);
		}

		return sResult;
	}

	/**
	 * @return another string
	 */
	@SuppressWarnings("deprecation")
	public String toString2() {
		if (Math.abs((new Date()).getDate() - day) > 2) {
			return toString() + " " + hmFormat(hour) + ":" + hmFormat(min);
		}
		return toString();
	}

	/**
	 * @return full date string
	 */
	@SuppressWarnings("deprecation")
	public String toFullString() {
		return (new Date(year - 1900, month, day, hour, min, sec)).toString();
	}

	/**
	 * @return mail-header string
	 */
	public String toMailString() {
		//Wed, 16 Jan 2002 21:11:19 +0200
		String sResult = sShortDows[dow] + ", " + day + " " + sShortMonths[month] + " " + year + " " + hmFormat(hour) + ":" + hmFormat(min) + ":" + hmFormat(sec);

		if ((sDeplasareGMT != null) && (sDeplasareGMT.length() > 0))
			sResult += " " + sDeplasareGMT;
		else
			sResult += " +0300";

		return sResult;
	}

	@Override
	public int compareTo(MailDate mdParam) {
		if (mdParam == null)
			return 1;

		if (year > mdParam.year)
			return 1;
		if (year < mdParam.year)
			return -1;
		if (month > mdParam.month)
			return 1;
		if (month < mdParam.month)
			return -1;
		if (day > mdParam.day)
			return 1;
		if (day < mdParam.day)
			return -1;
		if (hour > mdParam.hour)
			return 1;
		if (hour < mdParam.hour)
			return -1;
		if (min > mdParam.min)
			return 1;
		if (min < mdParam.min)
			return -1;
		if (sec > mdParam.sec)
			return 1;
		if (sec < mdParam.sec)
			return -1;
		return 0;
	}

	@Override
	public int compare(MailDate md1, MailDate md2) {
		return md1.compareTo(md2);
	}

	@Override
	public boolean equals(Object mdParam) {
		return compareTo((MailDate) mdParam) == 0;
	}
	
	@Override
	public int hashCode() {
		return getDate().hashCode(); 
	}

	/**
	 * @return original date string
	 */
	public String getOriginalString() {
		return sOrigDate;
	}

	/**
	 * @return the date
	 */
	@SuppressWarnings("deprecation")
	public Date getDate() {
		return new Date(year - 1900, month, day, hour, min, sec);
	}

}