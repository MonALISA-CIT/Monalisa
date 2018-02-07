/*
 * Created on Nov 2, 2005
 */
package lia.Monitor.monitor;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import lia.util.ntp.NTPDate;

/**
 * A simple EmailMessage -- should be able to go over the wire ;) 
 */
public class EMsg implements Serializable {
    

    /**
     * @since ML 1.4.4
     */
    private static final long serialVersionUID = 1432403963216022651L;

    /**
     * Problems with Exchange server @ CERN ... have to real username @ hotname
     */
    public String mailFrom;
    
    //No Comment :)
    public final String from;
    public final String[] to;
    public final String subject;
    public final String message;
    
    //Submission date ... ( should be the local date when first submitted from ML )
    //if null it will be the current Date
    public final String date;
    
    //should be enough - will not keep so much mails ( ever ... hmm ... we'll see :) ) 
    /**
     * Unique EMsg id 
     */
    private final Integer id;
    
    private static transient final String sMailDateFormat = "EEE, dd MMM yyyy KK:mm:ss Z";
    private static transient final AtomicInteger SEQUENCER = new AtomicInteger(0);
    
    public EMsg() {
        this(null, null, null, new Date(NTPDate.currentTimeMillis()), null, null);
    }
            
    public EMsg(String from, String[] to, String Subject, String message){
        this(from, from, to, new Date(NTPDate.currentTimeMillis()), Subject, message);
    }
    
    public EMsg(String mailFrom, String from, String[] to, String Subject, String message){
        this(mailFrom, from, to, new Date(NTPDate.currentTimeMillis()), Subject, message);
    }

    public EMsg(String mailFrom, String from, String[] to, String date, String Subject, String message){
        this.mailFrom = mailFrom;
        this.from = from;
        this.to = to;
        this.date = (date==null)?formatLongGMTDate(null):date;
        this.subject = Subject;
        this.message = message;
        this.id = Integer.valueOf(SEQUENCER.getAndIncrement());
    }

    public EMsg(String mailFrom, String from, String[] to, Date date, String Subject, String message){
        this.mailFrom = mailFrom;
        this.from = from;
        this.to = to;
        this.date = formatLongGMTDate(date);
        this.subject = Subject;
        this.message = message;
        this.id = Integer.valueOf(SEQUENCER.getAndIncrement());
    }
    
    /**
     * Formats a given date Thu, 03 Nov 2005 03:20:10 +0100
     * @param date
     * @return formated date
     */
    public static final String formatLongGMTDate(final Date date) {
        final DateFormat dateFormatter = new SimpleDateFormat(sMailDateFormat, Locale.US); 
        if(date == null) {
            return dateFormatter.format(new Date(NTPDate.currentTimeMillis()));
        }
        return dateFormatter.format(date);
    }
    
    public Integer getID() {
        return id;
    }
    
    public String getHeader() {
        StringBuilder sb = new StringBuilder();
        sb.append("\nRealFrom: ").append(mailFrom);
        sb.append("\nFrom: ").append(from);
        
        sb.append("\nTo: ");
        if(to != null && to.length > 0) {
            for(int i=0; i<to.length; i++) {
                sb.append(to[i]);
                if(i < to.length-1) {
                    sb.append(", ");
                }
            }
        }
        
        sb.append("\nID: ").append(id);
        sb.append("\nDate: ").append(date);
        sb.append("\nSubject: ").append(subject);
        return sb.toString();
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getHeader());
        sb.append("\n\n").append(message);
        return sb.toString();
    }
}
