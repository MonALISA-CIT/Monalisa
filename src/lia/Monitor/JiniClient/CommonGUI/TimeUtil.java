package lia.Monitor.JiniClient.CommonGUI;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class TimeUtil {

	protected TimeZone timeZone = null;
	
	protected static final String[][] tzs = {
		{"A", "GMT+1"},
		{"ACDT", "GMT+10"} ,
		{"ACST", "GMT+9"},
		{"ADT", "GMT-3"}, 
		{"AEDT", "GMT+11"},
		{"AEST", "GMT+10"},
		{"AKDT", "GMT-8"}, 
		{"AKST", "GMT-9"}, 
		{"AST", "GMT-4"}, 
		{"AWST", "GMT+8"}, 
		{"B", "GMT+2"}, 
		{"BST", "GMT+1"}, 
		{"C", "GMT+3"}, 
		{"CDT", "GMT+10"},
		{"CDTA", "GMT+10"},
		{"CDTN", "GMT-5"},
		{"CEST", "GMT+2"}, 
		{"CET", "GMT+1"}, 
		{"CST", "GMT+9"}, 
		{"CSTA", "GMT+9"},
		{"CSTN", "GMT-6"},
		{"CXT", "GMT+7"}, 
		{"D", "GMT+4"}, 
		{"E", "GMT+5"}, 
		{"EDT", "GMT+11"},
		{"EDTA", "GMT+11"},
		{"EDTN", "GMT-4"},
		{"EEST", "GMT+3"}, 
		{"EET", "GMT+2"}, 
		{"EST", "GMT+10"},
		{"ESTA", "GMT+10"},
		{"ESTN", "GMT-5"},
		{"F", "GMT+6"}, 
		{"G", "GMT+7"}, 
		{"GMT", "GMT"},
		{"H", "GMT+8"}, 
		{"HAA", "GMT-3"}, 
		{"HAC", "GMT-5"}, 
		{"HADT", "GMT-9"}, 
		{"HAE", "GMT-4"}, 
		{"HAP", "GMT-7"}, 
		{"HAR", "GMT-6"}, 
		{"HAST", "GMT-10"}, 
		{"HAT", "GMT-2"}, 
		{"HAY", "GMT-8"}, 
		{"HNA", "GMT-4"}, 
		{"HNC", "GMT-6"}, 
		{"HNE", "GMT-5"}, 
		{"HNP", "GMT-8"}, 
		{"HNR", "GMT-7"}, 
		{"HNT", "GMT-3"}, 
		{"HNY", "GMT-9"}, 
		{"I", "GMT+9"}, 
		{"IST", "GMT+1"}, 
		{"K", "GMT+10"}, 
		{"L", "GMT+11"}, 
		{"M", "GMT+12"}, 
		{"MDT", "GMT-6"}, 
		{"MESZ", "GMT+2"}, 
		{"MEZ", "GMT+1"}, 
		{"MST", "GMT-7"}, 
		{"N", "GMT-1"}, 
		{"NDT", "GMT-2"}, 
		{"NFT", "GMT+11"}, 
		{"NST", "GMT-3"}, 
		{"O", "GMT-2"}, 
		{"P", "GMT-3"}, 
		{"PDT", "GMT-7"}, 
		{"PST", "GMT-8"}, 
		{"Q", "GMT-4"}, 
		{"R", "GMT-5"}, 
		{"S", "GMT-6"}, 
		{"T", "GMT-7"}, 
		{"U", "GMT-8"}, 
		{"UTC", "GMT"}, 
		{"V", "GMT-9"}, 
		{"W", "GMT-10"}, 
		{"WEST", "GMT+1"}, 
		{"WET", "GMT"}, 
		{"WST", "GMT+8"}, 
		{"X", "GMT-11"}, 
		{"Y", "GMT-12"}, 
		{"Z", "GMT"}		
	};
	
	protected String formatTimeZone( String tz ) {
		
		for (int i=0; i<tzs.length; i++)
			if (tz.equals(tzs[i][0]))
				return tzs[i][1];
		
		return tz;
	}
	
	public void setTimeZone(String timeZ) {
		
		if (timeZ == null)
			return;
		timeZ = formatTimeZone(timeZ);
		TimeZone tz = TimeZone.getTimeZone(timeZ);
		if (tz == null)
			return;	
		timeZone = tz;
	}

	public String getTime(long time) {
		
		if (time <= 0) {
			return "";
		}
		DateFormat df = new SimpleDateFormat();
		if (timeZone != null)
			df.setTimeZone(timeZone);
		Date d1 = new Date(time);
		return new String(df.format(d1, new StringBuffer(), new FieldPosition(0)));
	}
}
