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

public class RepositoryWatcher implements MonitorFilter, Runnable {

    /**
     * 
     */
    private static final long serialVersionUID = -5389123437381559407L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(RepositoryWatcher.class.getName());

    private static final String contactEmail = AppConfig.getProperty("MonaLisa.ContactEmail", null);
    private static final boolean useContactEmail = Boolean.valueOf(
            AppConfig.getProperty("include.MonaLisa.ContactEmail", "false")).booleanValue();

    private MFarm farm = null;

    private String sNodeName = null;
    private String sClusterName = null;

    private boolean triggered = false;
    private boolean triggered2 = false;
    private String[] RCPT;

    private boolean active = true;

    public RepositoryWatcher(String _sClusterName, String _sNodeName) {
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
        return "RepositoryWatcher" + sClusterName + ":" + sNodeName;
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
    public double dLast15 = 10;

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
            triggered = false;
            lLastReceived = NTPDate.currentTimeMillis();

            //logger.log(Level.INFO, "RepositoryWatcher : received some values for "+sClusterName+":"+sNodeName);

            for (int i = 0; (r.param_name != null) && (i < r.param_name.length); i++) {
                if (r.param_name[i].equals("15MinValues")) {
                    triggered2 = false;
                    dLast15 = r.param[i];
                    break;
                }
            }
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
            if (((NTPDate.currentTimeMillis() - lLastReceived) > (1000 * 60 * 5)) && !triggered) {
                alarm();
            } else {
                //logger.log(Level.FINE, "RepositoryWacher : site "+sClusterName+":"+sNodeName+" is alive");
            }

            if ((dLast15 < 1.0) && !triggered2) {
                alarm2();
            } else {
                //logger.log(Level.FINE, "RepositoryWacher : site "+sClusterName+":"+sNodeName+" : has valid 15min data");
            }

            try {
                Thread.sleep(1000 * 10);
            } catch (Exception e) {
                logger.log(Level.FINE, "RepositoryWatcher : Interrupted ...");
                active = false;
            }
        }
    }

    private void alarm() {
        triggered = true;

        try {
            logger.log(Level.WARNING, "RepositoryWatcher : repository '" + sNodeName + "' is down!");
            MailFactory.getMailSender().sendMessage(
                    "webwatcher@monalisa-chi.uslhcnet.org",
                    RCPT,
                    "Repository is down on : " + sNodeName,
                    "Repository did not respond in the last 10 minutes on " + sNodeName + "." + "\nLocal Time is : "
                            + (new Date()).toString() + "\nNTPDate Time is : "
                            + (new Date(NTPDate.currentTimeMillis())).toString() + "\n");
        } catch (Throwable t) {
            logger.log(Level.WARNING, "RepositoryWatcher : got exception sending mail", t);
        }
    }

    private void alarm2() {
        triggered2 = true;

        try {
            logger.log(Level.WARNING, "RepositoryWatcher : repository '" + sNodeName + "' doesn't collect data");
            MailFactory.getMailSender().sendMessage(
                    "webwatcher@monalisa-chi.uslhcnet.org",
                    RCPT,
                    "Repository collector down : " + sNodeName,
                    "The repository on " + sNodeName + " did not receive any values in the last 15 minutes."
                            + "\nLocal Time is : " + (new Date()).toString() + "\nNTPDate is : "
                            + (new Date(NTPDate.currentTimeMillis())).toString() + "\n");
        } catch (Throwable t) {
            logger.log(Level.WARNING, "RepositoryWatcher : got exception sending mail", t);
        }
    }

}
