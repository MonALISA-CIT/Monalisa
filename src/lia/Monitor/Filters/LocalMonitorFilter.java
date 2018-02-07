/*
 * $Id: LocalMonitorFilter.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.Filters;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Farm.FarmMonitor;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.util.Utils;
import lia.util.mail.PMSender;
import lia.util.ntp.NTPDate;

/**
 * 
 * @author ramiro
 */
public class LocalMonitorFilter extends GenericMLFilter {

    private static final long serialVersionUID = -2581174240636600188L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(LocalMonitorFilter.class.getName());

    private static final String Name = "LocalMonitorFilter";

    //Resend Alert after
    private static final AtomicLong dtResendTimeout = new AtomicLong(6 * 60 * 60 * 1000);

    private static volatile String[] RCPT;
    private static final Object RCPTsLock = new Object();

    private final static AtomicLong sleepTime = new AtomicLong(5 * 1000);
    private final static AtomicBoolean loadExceeds = new AtomicBoolean(false);
    public static final AtomicBoolean forceSendMail = new AtomicBoolean(false);
    private static volatile double Load5_Threshold = 15;
    private static final Object load5ThresholdLock = new Object();

    private long lastMailSent = 0L;

    public static final String EXTENDED_CMD_STATUS = Utils.getPromptLikeBinShCmd("hostname")
            + Utils.getPromptLikeBinShCmd("hostname -f") + Utils.getPromptLikeBinShCmd("hostname -i")
            + Utils.getPromptLikeBinShCmd("free -t -m") + Utils.getPromptLikeBinShCmd("uname -a")
            + Utils.getPromptLikeBinShCmd("uptime") + Utils.getPromptLikeBinShCmd("netstat -tanp")
            + Utils.getPromptLikeBinShCmd("vmstat 1 10") + Utils.getPromptLikeBinShCmd("pstree -u -p")
            + Utils.getPromptLikeBinShCmd("ps aux");

    static {
        reloadConfig();

        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                reloadConfig();
            }

        });
    }

    public LocalMonitorFilter(String farmName) {
        super(farmName);
    }

    /* from MonitorFilter */
    @Override
    public String getName() {
        return Name;
    }

    private void sendMail(String msg) {
        try {
            lastMailSent = NTPDate.currentTimeMillis();
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "\n\n [ LocalMonitorFilter ] Notif: !!! \n ");
            }
            PMSender.getInstance().sendMessage("mlstatus@monalisa.cern.ch", RCPT,
                    "[ LocalMonitorFilter ] Status @ " + FarmMonitor.getStandardEmailSubject(), msg, true);
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, " [ LocalMonitorFilter ] - Notify failed ", t);
            }
        }
    }

    /**
     * @see lia.Monitor.Filters.GenericMLFilter#getSleepTime()
     */
    @Override
    public long getSleepTime() {
        return sleepTime.get();
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
        } else {
            return;
        }

        if ((r.ClusterName != null) && r.ClusterName.equals("MonaLisa") && (r.NodeName != null)) {
            for (int j = 0; (r.param_name != null) && (j < r.param_name.length); j++) {
                if (r.param_name[j].indexOf("Load5") != -1) {
                    if (r.param[j] >= getLoadThreshold()) {
                        loadExceeds.set(true);
                    } else {
                        loadExceeds.set(false);
                    }
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, " notifyResult loadExceeds = " + loadExceeds.get() + " notified "
                                + r.param[j] + " TSh = " + getLoadThreshold());
                    }
                }
            }
        }
    }

    public static void setLoadThreshold(double newValue) {
        synchronized (load5ThresholdLock) {
            Load5_Threshold = newValue;
        }
    }

    public static double getLoadThreshold() {
        return Load5_Threshold;
    }

    public static final String getStatus() {

        StringBuilder sb = new StringBuilder(32768);
        sb.append("LoadThreashold: ").append(getLoadThreshold()).append("\n");
        Utils.appendExternalProcessStatus(new String[] { "/bin/sh", "-c", EXTENDED_CMD_STATUS }, new String[] {
                "COLUMNS=200", "PATH=/bin:/usr/bin:/usr/local/bin:/usr/sbin:/sbin:/usr/local/sbin" }, sb);
        Utils.appendExternalProcessStatus(new String[] { "/bin/sh", "-c", Utils.getPromptLikeBinShCmd("df -h") }, sb);

        return sb.toString();
    }

    /**
     * @see lia.Monitor.Filters.GenericMLFilter#expressResults()
     */
    @Override
    public Object expressResults() {
        try {
            if (forceSendMail.getAndSet(false)) {
                sendMail(getStatus());
            } else {
                if (loadExceeds.get() && ((lastMailSent + dtResendTimeout.get()) < NTPDate.currentTimeMillis())) {
                    sendMail(getStatus());
                }
            }
        } catch (Throwable t1) {
        }
        return null;
    }

    private static void reloadConfig() {

        //RCPT
        synchronized (RCPTsLock) {
            try {
                String mailaddress = AppConfig.getProperty("lia.Monitor.Filters.LocalMonitorFilter.RCPT");
                String[] tRCPTS = null;
                if (mailaddress != null) {
                    tRCPTS = Utils.getSplittedListFields(mailaddress);
                }
                if ((tRCPTS == null) || (tRCPTS.length == 0)) {
                    RCPT = new String[] { "mlstatus@monalisa.cern.ch" };
                } else {
                    RCPT = new String[tRCPTS.length + 1];
                    System.arraycopy(tRCPTS, 0, RCPT, 0, tRCPTS.length);
                    RCPT[tRCPTS.length] = "mlstatus@monalisa.cern.ch";
                }
            } catch (Throwable t) {
                RCPT = new String[] { "mlstatus@monalisa.cern.ch" };
            }

            boolean includeContactMail = false;
            try {
                includeContactMail = Boolean.valueOf(
                        AppConfig.getProperty("lia.Monitor.Filters.LocalMonitorFilter.includeContactMail", "false"))
                        .booleanValue();
            } catch (Throwable t) {
                includeContactMail = false;
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(
                            Level.FINER,
                            " [ LocalMonitorFilter ] Got exc parsing lia.Monitor.Filters.LocalMonitorFilter.includeContactMail ",
                            t);
                }
            }

            if (includeContactMail) {
                String[] contactMails = null;
                try {
                    contactMails = Utils.getSplittedListFields(AppConfig.getProperty("MonaLisa.ContactEmail", null));
                } catch (Throwable t) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, " [ LocalMonitorFilter ] Got exception parsing MonaLisa.ContactEmail",
                                t);
                    }
                    contactMails = null;
                }
                if ((contactMails != null) && (contactMails.length > 0)) {
                    String[] tmpRCPTS = new String[RCPT.length + contactMails.length];
                    System.arraycopy(RCPT, 0, tmpRCPTS, 0, RCPT.length);
                    System.arraycopy(contactMails, 0, tmpRCPTS, RCPT.length, contactMails.length);
                    RCPT = tmpRCPTS;
                }
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ LocalMonitorFilter ] RCPT = " + Arrays.toString(RCPT));
            }
        } // end sync

        double loadThreshold = 15D;

        try {
            loadThreshold = Double.parseDouble(AppConfig.getProperty(
                    "lia.Monitor.Filters.LocalMonitorFilter.Load5_Threshold", "15"));
        } catch (Throwable t) {
            loadThreshold = 15;
        }

        Load5_Threshold = loadThreshold;

        long sleepT = AppConfig.getl("lia.Monitor.Filters.LocalMonitorFilter.sleepTime", 5000L);

        if (sleepT < 1000) {
            sleepT = 5 * 1000;
        }

        sleepTime.set(sleepT);
        long rDel = AppConfig.getl("lia.Monitor.Filters.LocalMonitorFilter.ResendDelay", 360) * 60 * 1000;

        if (rDel < (1 * 60 * 1000)) {
            rDel = 1 * 60 * 1000;
        }

        dtResendTimeout.set(rDel);

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "");
        }
    }
}
