package lia.Monitor.Filters;

import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Farm.FarmMonitor;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.util.mail.MailFactory;
import lia.util.ntp.NTPDate;

public class DC04Filter extends GenericMLFilter {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(DC04Filter.class.getName());

    //How long should wait for results
    private static final long dtResultTimeout = Long.valueOf(
            AppConfig.getProperty("lia.Monitor.Filters.DC04Filter.ResultDelay", "20")).longValue() * 1000 * 60;

    //Resend Alert after
    private static final long dtResendTimeout = Long.valueOf(
            AppConfig.getProperty("lia.Monitor.Filters.DC04Filter.ResendDelay", "360")).longValue() * 1000 * 60;

    private static String Name = "DC04Filter";

    private static final String mailaddress = AppConfig.getProperty("lia.Monitor.Filters.DC04RCPT",
            "ramiro@cs.pub.ro,ramiro@roedu.net");

    private static String[] RCPT = null;

    private static Hashtable ht = new Hashtable();
    private static Hashtable rht = new Hashtable();

    public DC04Filter(String farmName) {
        super(farmName);
        if ((mailaddress == null) || (mailaddress.length() == 0)) {
            RCPT = new String[] { "ramiro@cs.pub.ro", "ramiro@roedu.net" };
        } else {
            Vector addr = new Vector();
            for (StringTokenizer st = new StringTokenizer(mailaddress, ","); st.hasMoreTokens();) {
                String nt = st.nextToken();
                if ((nt != null) && (nt.length() > 0) && (nt.indexOf("@") != -1)) {
                    addr.add(nt);
                }
            }
            if (addr.size() > 0) {
                RCPT = (String[]) addr.toArray(new String[addr.size()]);
            } else {
                RCPT = new String[] { "ramiro@cs.pub.ro", "ramiro@roedu.net" };
            }
        }//else
    }

    /* from MonitorFilter */
    @Override
    public String getName() {
        return Name;
    }

    private void sendMail(String host, String msg) {
        try {
            MailFactory.getMailSender().sendMessage(FarmMonitor.realFromAddress, "support@monalisa.cern.ch", RCPT,
                    "[ DC04 ] Host DOWN @ " + host, msg);
        } catch (Throwable t) {
            logger.log(Level.WARNING, " DC04 Filter - Could not send mail", t);
        }
    }

    private void verifyNodes() {
        long now = NTPDate.currentTimeMillis();

        Vector deadHosts = new Vector();
        for (Enumeration en = ht.keys(); en.hasMoreElements();) {
            String key = (String) en.nextElement();
            long vTime = ((Long) ht.get(key)).longValue();
            if (now > vTime) {
                deadHosts.add(key);
                ht.remove(key);
                logger.log(Level.INFO, " Host " + key + " removed from filter");
            }
        }//for
        if ((deadHosts.size() == 0) && (rht.size() == 0)) {
            return;
        }

        for (int i = 0; i < deadHosts.size(); i++) {
            String host = (String) deadHosts.elementAt(i);
            rht.put(host, Long.valueOf(now + dtResendTimeout));
            logger.log(Level.WARNING, " Notifying dead Host " + host);
            sendMail(host, " [ " + new Date() + " ] Host " + host + " did not respond for the last "
                    + (dtResultTimeout / (1000 * 60)) + " minutes!");
        }

        for (Enumeration en = rht.keys(); en.hasMoreElements();) {
            String host = (String) en.nextElement();
            long time = ((Long) rht.get(host)).longValue();
            if (time < now) {
                logger.log(Level.WARNING, " Host " + host + " STILL not responding");
                sendMail(host, " [ " + new Date() + " ] Host " + host + " STILL not responding for the last "
                        + (dtResendTimeout / (1000 * 60)) + " minutes!");
                rht.put(host, Long.valueOf(now + dtResendTimeout));
            }
        }
    }//end verifyNodes()

    /**
     * @see lia.Monitor.Filters.GenericMLFilter#getSleepTime()
     */
    @Override
    public long getSleepTime() {
        return 10 * 1000;
    }

    /**
     * @see lia.Monitor.Filters.GenericMLFilter#getFilterPred()
     */
    @Override
    public monPredicate[] getFilterPred() {
        return null;
    }

    /**
     * @see lia.Monitor.Filters.GenericMLFilter#notifyResult(java.lang.Object)
     */
    @Override
    public void notifyResult(Object o) {
        if (o == null) {
            return;
        }

        Result r = null;

        if (o instanceof Result) {
            r = (Result) o;
        } else if (o instanceof Vector) {
            Vector rv = (Vector) o;
            for (int i = 0; i < rv.size(); i++) {
                addNewResult(rv.elementAt(i));
            }
        } else {
            return;
        }

        if ((r.ClusterName != null) && (r.ClusterName.indexOf("MS") != -1) && (r.NodeName != null)) {
            for (int j = 0; (r.param_name != null) && (j < r.param_name.length); j++) {
                String key = r.NodeName;
                if (r.param_name[j].indexOf("Load") != -1) {
                    if (!ht.containsKey(key)) {
                        logger.log(Level.INFO, " DC04 Filter ADD HOST [ " + key + " ] ");
                    }
                    ht.put(key, Long.valueOf(NTPDate.currentTimeMillis() + dtResultTimeout));
                    rht.remove(key);
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, " Got Res from " + key);
                    }
                } else {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, " Got Res from " + key + " param: " + r.param_name[j]);
                    }
                }
            }
        }
    }

    /**
     * @see lia.Monitor.Filters.GenericMLFilter#expressResults()
     */
    @Override
    public Object expressResults() {
        try {
            verifyNodes();
        } catch (Throwable t1) {
        }
        return null;
    }

}
