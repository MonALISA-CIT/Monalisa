package lia.web.utils;

import java.util.Date;
import java.util.StringTokenizer;

/**
 * @author costing
 *
 */
public class PDate {	//postgres date decoder, moronic feature

    /**
     * @param s
     * @return the decoded date
     */
    @SuppressWarnings("deprecation")
	public static synchronized Date decode(String s){
	// 2002-01-24 00:00:00+02
	StringTokenizer st = new StringTokenizer(s, " /.:-+");
	
	Date d;
	
	try{
	    d = new Date(s);
	}
	catch (Exception e){
	    try{
		d = new Date();
		d.setYear(Integer.parseInt(st.nextToken())-1900);
		d.setMonth(Integer.parseInt(st.nextToken())-1);
		d.setDate(Integer.parseInt(st.nextToken()));
		d.setHours(Integer.parseInt(st.nextToken()));
	        d.setMinutes(Integer.parseInt(st.nextToken()));
	        d.setSeconds(Integer.parseInt(st.nextToken()));
	    }
	    catch (Exception ee){
		try{
		    d = (new MailDate(s)).getDate();
		}
		catch (Exception eee){
		    try{
			// 2003-01-31
			st = new StringTokenizer(s, " -");
			d = new Date();
			d.setHours(0);
			d.setHours(0);
			d.setMinutes(0);
			d.setSeconds(0);
			
			d.setYear(Integer.parseInt(st.nextToken())-1900);
			d.setMonth(Integer.parseInt(st.nextToken())-1);
			d.setDate(Integer.parseInt(st.nextToken()));
		    }
		    catch (Exception eeee){
		        d = new Date();
		    }
		}
	    }
	}
	
	return d;
    }
    
    /**
     * @param d
     * @return the date
     */
    public static synchronized String encode(Date d){
	// to be done if needed
	
	return d.toString();
    } 
    
}
