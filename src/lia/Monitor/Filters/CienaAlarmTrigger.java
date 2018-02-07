package lia.Monitor.Filters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.Utils;
import lia.util.ciena.CienaUtils;
import lia.util.ciena.ParsedCienaTl1Alarm;
import lia.util.mail.MailFactory;
import lia.util.ntp.NTPDate;

public class CienaAlarmTrigger extends GenericMLFilter implements AppConfigChangeListener {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(CienaAlarmTrigger.class.getName());

    private final BlockingQueue<eResult> lastResults = new LinkedBlockingQueue<eResult>();

    private long lastAlarmSent;
    private long lastAlarmSentNano;

    private volatile long minAlertTimeNano;

    private String[] RCPT = null;

    private String[] AISNCS = null;

    public CienaAlarmTrigger(String farmName) {
        super(farmName);
        reloadConf();
        AppConfig.addNotifier(this);
    }

    private static final long serialVersionUID = 3540775009046623800L;

    @Override
    public Object expressResults() {

        final boolean isFine = logger.isLoggable(Level.FINE);
        final boolean isFiner = isFine || logger.isLoggable(Level.FINER);
        final boolean isFinest = isFiner || logger.isLoggable(Level.FINEST);

        if (isFinest) {
            logger.log(
                    Level.FINEST,
                    "[ CienaAlarmTrigger ] minAlertTime = " + TimeUnit.NANOSECONDS.toMinutes(minAlertTimeNano)
                            + " minutes, " + "lastAlarmSentNano = " + lastAlarmSentNano + " lastAlarmSent = "
                            + new Date(lastAlarmSent) + "RCPT = " + Arrays.toString(RCPT) + " AISNCS = "
                            + Arrays.toString(AISNCS));
        }

        // TODO Auto-generated method stub

        final long now = NTPDate.currentTimeMillis();
        final long nanoNow = Utils.nanoNow();
        final long dtNanos = nanoNow - lastAlarmSentNano;

        if (isFinest) {
            logger.log(Level.FINER,
                    "[ CienaAlarmTrigger ] lastAlarmSentNano=" + lastAlarmSentNano + ", nowNano=" + nanoNow
                            + " dtNanos=" + dtNanos + ", dtNanosInSeconds=" + TimeUnit.NANOSECONDS.toSeconds(dtNanos)
                            + ", minAlertTimeNano=" + minAlertTimeNano + ", minAlertTimeNanoSeconds="
                            + TimeUnit.NANOSECONDS.toSeconds(minAlertTimeNano));
        }

        if ((lastAlarmSentNano > 0) && (dtNanos < minAlertTimeNano)) {
            if (isFiner) {
                logger.log(Level.FINER,
                        "[ CienaAlarmTrigger ] no new results to analyze minAlertTimeNano not yet reached");
            }
            return null;
        }

        final int cLen = lastResults.size();
        if (cLen == 0) {
            if (isFiner) {
                logger.log(Level.FINER, "[ CienaAlarmTrigger ] no new results in filter queue. returning.");
            }
            return null;
        }

        final ArrayList<eResult> lastResultsTmp = new ArrayList<eResult>(cLen);

        lastResults.drainTo(lastResultsTmp);

        if (lastResultsTmp.size() > 0) {
            logger.log(Level.INFO, "[ CienaAlarmTrigger ] Notifying " + lastResultsTmp.size() + " alarms: "
                    + lastResultsTmp);

            StringBuilder subj = new StringBuilder(8192);
            StringBuilder sb = new StringBuilder(16384);

            subj.append("Ciena Alarms @ ").append(farmName).append(" - ").append(lastResultsTmp.size())
                    .append(" total");

            boolean bShouldSendMail = false;

            sb.append("\n\nThere are ").append(lastResultsTmp.size()).append(" alarm(s) at ").append(farmName)
                    .append(": \n\n");

            for (String element : AISNCS) {

                StringBuilder tsb = new StringBuilder();
                int almsCount = 0;

                for (Iterator<eResult> it = lastResultsTmp.iterator(); it.hasNext();) {
                    final eResult er = it.next();
                    if (er.param.length > 0) {
                        try {
                            ParsedCienaTl1Alarm TL1Response = CienaUtils.parseTL1ResponseLine((String) er.param[0]);
                            if (TL1Response.aisnc.equals(element)) {
                                bShouldSendMail = true;
                                logger.log(Level.INFO, "Adding alarm: " + er.param[0]);
                                tsb.append(er.param[0]).append("\n");
                                almsCount++;
                                it.remove();
                            }
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, "For line: [" + er.param[0] + "] Got exc:", t);
                        }
                    }
                }

                if (almsCount > 0) {
                    sb.append("\n ").append(almsCount).append(" ").append(element).append(" alarm(s):\n\n");
                    subj.append(" / ").append(almsCount).append(" ").append(element);
                    sb.append(tsb);
                    sb.append("\n");
                } else {
                    subj.append(" / ").append(0).append(" ").append(element);
                    sb.append("\n 0 ").append(element).append(" alarm(s).\n");
                }
            }

            lastAlarmSent = now;
            lastAlarmSentNano = nanoNow;

            if (lastResultsTmp.size() > 0) {
                sb.append("\n ").append(lastResultsTmp.size()).append(" other types of alarm(s):\n\n");
                subj.append(" / ").append(lastResultsTmp.size()).append(" other");
                for (eResult er : lastResultsTmp) {
                    if (er.param.length > 0) {
                        sb.append(er.param[0]).append("\n");
                    }
                }
            }

            try {
                MailFactory.getMailSender().sendMessage("mlstatus@monalisa.cern.ch", RCPT, subj.toString(),
                        sb.toString());
                logger.log(Level.INFO, " [ CienaAlarmTrigger ] Notified alarms: lastAlarmSent = "
                        + new Date(lastAlarmSent) + ", lastAlarmSentNano = " + lastAlarmSentNano + " \n" + "Rcpt: "
                        + Arrays.toString(RCPT) + "\n" + "Subj: " + subj.toString() + "\n" + "Msg: " + sb.toString()
                        + "\n");
            } catch (Throwable t) {
                logger.log(Level.WARNING, "\n\n [ CienaAlarmTrigger ] cannot notify errors by mail. Cause", t);
            }

        }
        return null;
    }

    @Override
    public monPredicate[] getFilterPred() {
        return null;
    }

    @Override
    public String getName() {
        return "CienaAlarmTrigger";
    }

    @Override
    public long getSleepTime() {
        return 5 * 1000;
    }

    @Override
    public void notifyResult(Object o) {
        if ((o != null) && (o instanceof eResult)) {
            eResult er = (eResult) o;
            if ((er.Module != null) && er.Module.equals("monCienaAlm")) {
                logger.log(Level.INFO, " CienaAlarmTrigger - ADDING eResult: " + er.toString());
                lastResults.add(er);
            } else {
                logger.log(Level.INFO, " CienaAlarmTrigger - IGNORING eResult: " + er.toString());
            }

        }
    }

    private void reloadConf() {
        try {
            minAlertTimeNano = TimeUnit.MINUTES.toNanos(AppConfig.getl(
                    "lia.Monitor.Filters.CienaAlarmTrigger.minAlertTime", 15));
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception parsing lia.Monitor.Filters.CienaAlarmTrigger.minAlertTime", t);
            minAlertTimeNano = TimeUnit.MINUTES.toNanos(15);
        }

        String mailaddress = "ramiro@roedu.net,Ramiro.Voicu@cern.ch";
        try {
            mailaddress = AppConfig.getProperty("lia.Monitor.Filters.CienaAlarmTrigger.RCPT",
                    "ramiro@roedu.net,Ramiro.Voicu@cern.ch").trim();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "lia.Monitor.Filters.CienaAlarmTrigger.RCPT", t);
            mailaddress = "ramiro@roedu.net,Ramiro.Voicu@cern.ch";
        }

        RCPT = mailaddress.split("(\\s)*,(\\s)*");

        String aisncs = "CR,MJ";
        try {
            aisncs = AppConfig.getProperty("lia.Monitor.Filters.CienaAlarmTrigger.AISNCS", "CR,MJ,MN").trim();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "lia.Monitor.Filters.CienaAlarmTrigger.AISNCS", t);
            mailaddress = "CR,MJ";
        }

        AISNCS = aisncs.split("(\\s)*,(\\s)*");

        for (int i = 0; i < AISNCS.length; i++) {
            AISNCS[i] = AISNCS[i].toUpperCase();
        }

        logger.log(Level.INFO, " minAlertTime = " + TimeUnit.NANOSECONDS.toMinutes(minAlertTimeNano)
                + " minutes, RCPT = " + Arrays.toString(RCPT) + " AISNCS = " + Arrays.toString(AISNCS));
    }

    @Override
    public void notifyAppConfigChanged() {
        reloadConf();
    }

}
