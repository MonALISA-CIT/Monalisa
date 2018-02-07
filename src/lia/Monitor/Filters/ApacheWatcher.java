package lia.Monitor.Filters;

import java.util.Date;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.DataCache.Cache;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MonitorClient;
import lia.Monitor.monitor.MonitorFilter;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.dbStore;
import lia.util.mail.MailFactory;
import lia.util.ntp.NTPDate;

public class ApacheWatcher implements MonitorFilter, Runnable {

    /**
     * 
     */
    private static final long serialVersionUID = -5592752349147634161L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(ApacheWatcher.class.getName());

    private static final String contactEmail = AppConfig.getProperty("MonaLisa.ContactEmail", null);
    private static final boolean useContactEmail = Boolean.valueOf(
            AppConfig.getProperty("include.MonaLisa.ContactEmail", "false")).booleanValue();

    private MFarm farm = null;

    private String sNodeName = null;
    private String sClusterName = null;

    private boolean triggered = false;
    private String[] RCPT;

    private boolean active = true;

    private static final int ALARM_TIME = 10;

    public ApacheWatcher(String _sClusterName, String _sNodeName) {
        sNodeName = _sNodeName;
        sClusterName = _sClusterName;

        if (useContactEmail && (contactEmail != null) && (contactEmail.indexOf("@") != -1)) {
            RCPT = new String[] { "webwatcher@monalisa-chi.uslhcnet.org", contactEmail };
        } else {
            RCPT = new String[] { "webwatcher@monalisa-chi.uslhcnet.org" };
        }
    }

    @Override
    public void confChanged() {
    }

    /* from MonitorFilter */
    @Override
    public String getName() {
        return "ApacheWatcher:" + sClusterName + ":" + sNodeName;
    }

    @Override
    public void initdb(dbStore datastore, MFarm farm) {
        this.farm = farm;
    }

    @Override
    public void initCache(Cache cache) {
    }

    @Override
    public void addClient(MonitorClient client) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " Got client! - I shouldn't!!");
        }
        //clients.add( wcli);
    }

    @Override
    public void removeClient(MonitorClient client) {
        //clients.remove(client);
    }

    public long lLastReceived = NTPDate.currentTimeMillis();

    @Override
    public void addNewResult(Object o) {
        if (o == null) {
            return;
        }

        Result r = null;

        if (o instanceof Result) {
            r = (Result) o;
        } else {
            if (o instanceof Vector) {
                Iterator it = ((Vector) o).iterator();
                while (it.hasNext()) {
                    addNewResult(it.next());
                }
            }

            return;
        }

        if (r.ClusterName.equals(sClusterName) && r.NodeName.equals(sNodeName)) {
            //logger.log(Level.INFO, "ApacheWatcher : received some values for "+sClusterName+":"+sNodeName);

            triggered = false;
            lLastReceived = NTPDate.currentTimeMillis();
        }
    }

    @Override
    public boolean isAlive() {
        return active;
    }

    @Override
    public void finishIt() {
        logger.log(Level.INFO, "Requested to finish!!!");
        active = false;
    }

    @Override
    public void run() {
        while (active) {
            if (((NTPDate.currentTimeMillis() - lLastReceived) > (1000 * 60 * ALARM_TIME)) && !triggered) {
                alarm();
            } else {
                //logger.log(Level.FINE, "ApacheWacher : site "+sClusterName+":"+sNodeName+" is alive");
            }

            try {
                Thread.sleep(1000 * 10);
            } catch (Exception e) {
                logger.log(Level.FINE, "ApacheWatcher : Interrupted ...");
                active = false;
            }
        }
    }

    private void alarm() {
        triggered = true;

        try {
            MailFactory.getMailSender().sendMessage(
                    "webwatcher@monalisa-chi.uslhcnet.org",
                    RCPT,
                    "Apache is down on : " + sNodeName,
                    "Apache did not respond in the last " + ALARM_TIME + " minutes on " + sNodeName + "."
                            + "\nLocal Time is : " + (new Date()).toString() + "\nNTPDate Time is : "
                            + (new Date(NTPDate.currentTimeMillis())).toString() + "\n");
        } catch (Throwable t) {
            logger.log(Level.WARNING, "ApacheWatcher : got exception sending mail", t);
        }
    }

}
