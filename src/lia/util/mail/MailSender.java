/*
 * Created on Nov 3, 2005
 */
package lia.util.mail;

import java.util.Date;

import lia.Monitor.monitor.EMsg;
import lia.util.ntp.NTPDate;


public abstract class MailSender {
    
    public void sendMessage(String from, String[] to, String Subject, String message) throws Exception {
        sendMessage(from, to, Subject, message, true);
    }
    
    public void sendMessage(String from, String[] to, String Subject, String message, boolean enqueue) throws Exception {
        sendMessage(from, from, to, Subject, message, enqueue);
    }

    public void sendMessage(String realFrom, String from, String[] to, String Subject, String message) throws Exception {
        sendMessage(realFrom, from, to, Subject, message, true);
    }
    
    public void sendMessage(String realFrom, String from, String[] to, String Subject, String message, boolean enqueue) throws Exception {
        sendMessage(realFrom, from, to, new Date(NTPDate.currentTimeMillis()), Subject, message, enqueue); 
    }

    public void sendMessage(String realFrom, String from, String[] to, Date date, String Subject, String message) throws Exception {
        sendMessage(new EMsg(realFrom, from, to, date, Subject, message), true);
    }
    
    public void sendMessage(String realFrom, String from, String[] to, Date date, String Subject, String message, boolean enqueue) throws Exception {
        sendMessage(new EMsg(realFrom, from, to, date, Subject, message), enqueue);
    }
    
    public abstract void sendMessage(EMsg EMsg, boolean enqueue)throws Exception;
}
