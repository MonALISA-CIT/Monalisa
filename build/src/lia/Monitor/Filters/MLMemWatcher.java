package lia.Monitor.Filters;

import java.io.File;
import java.net.InetAddress;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.monPredicate;
import lia.util.mail.MailFactory;
import lia.util.ntp.NTPDate;

public class MLMemWatcher extends GenericMLFilter {

    /**
     * 
     */
    private static final long serialVersionUID = -8159873722951747027L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(MLMemWatcher.class.getName());

    //After how many hours should I resend the mail? (in minutes!!!)
    private static final long dtResendAlertTime = Long.valueOf(
            AppConfig.getProperty("lia.Monitor.Filters.MLMemWatcher.resendAlertTime", "120").trim()).longValue() * 1000 * 60;

    private static long MIN_FREE_MEM_LIMIT = Long.valueOf(
            AppConfig.getProperty("lia.Monitor.Filters.MLMemWatcher.MIN_FREE_MEM_LIMIT", "2").trim()).longValue() * 1048576;

    private static String Name = "MLMemWatcher";

    String fullName;

    private static String FarmHOME;

    private static final String mailaddress = AppConfig.getProperty("lia.Monitor.Filters.MLMemWatcher.RCPT",
            "ramiro@roedu.net").trim();

    private static String[] RCPT = null;

    private boolean a1Triggered;

    //  Email Stuff
    private static final String contactEmail = AppConfig.getProperty("MonaLisa.ContactEmail", null);

    private static final boolean useContactEmail = Boolean.valueOf(
            AppConfig.getProperty("include.MonaLisa.ContactEmail", "false").trim()).booleanValue();

    private long lastA1SentMail = 0;

    private static boolean isVRVSFarm = false;

    private int a1Count = 0;

    static {
        FarmHOME = AppConfig.getProperty("lia.Monitor.Farm.HOME", null);
        if (AppConfig.getProperty("lia.Monitor.isVRVS", null) != null) {
            isVRVSFarm = true;
        }
    }

    public MLMemWatcher(String farmName) {
        super(farmName);
        a1Count = 0;
        String localAddress = null;

        if (AppConfig.getProperty("lia.Monitor.useIPaddress", null) != null) {
            localAddress = AppConfig.getProperty("lia.Monitor.useIPaddress");
        }

        if ((localAddress == null) || (localAddress.length() == 0)) {
            try {
                localAddress = InetAddress.getLocalHost().getHostAddress();
            } catch (Throwable t) {

            }
        }

        this.fullName = farmName + "[ " + ((localAddress != null) ? localAddress : "Could NOT get Local Address!")
                + " ]";
        if (useContactEmail && (contactEmail != null) && (contactEmail.indexOf("@") != -1)) {
            RCPT = (mailaddress + "," + contactEmail).split("(\\s)*,(\\s)*");
        } else {
            RCPT = mailaddress.split("(\\s)*,(\\s)*");
        }
        a1Triggered = false;
        StringBuilder sb = new StringBuilder();
        sb.append("MLMemWatcher mailAddresses [ ");
        if ((RCPT == null) || (RCPT.length == 0)) {
            sb.append(" No EMAILs DEFINED ]\n");
        } else {
            for (int ri = 0; ri < RCPT.length; ri++) {
                sb.append(RCPT[ri]);
                sb.append((ri == (RCPT.length - 1)) ? " ]\n" : ",");
            }
        }
        logger.log(Level.FINER, sb.toString());
    }

    /* from MonitorFilter */
    @Override
    public String getName() {
        return Name;
    }

    private void alarm1() {
        a1Triggered = true;
        a1Count++;
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "MLMemWatcher - alarm1() count = " + a1Count);
        }
        if (isVRVSFarm) {//special filter for VRVS
            if (a1Count > 10) {
                if ((lastA1SentMail + dtResendAlertTime) < NTPDate.currentTimeMillis()) {
                    try {
                        StringBuilder msg = new StringBuilder();
                        long tMem = Runtime.getRuntime().totalMemory();
                        long fMem = Runtime.getRuntime().freeMemory();
                        long mMem = Runtime.getRuntime().maxMemory();

                        msg.append("[ ").append(new Date()).append(" / ").append(new Date(NTPDate.currentTimeMillis()))
                                .append(" ] ").append(fullName).append(" Mem Status: \n");
                        msg.append("\nTotal Mem [ " + tMem + " / " + ((double) tMem / (double) 1048576) + " ]");
                        msg.append("\nFree Mem [ " + fMem + " / " + ((double) fMem / (double) 1048576) + " ]");
                        msg.append("\nMax Mem [ " + mMem + " / " + ((double) mMem / (double) 1048576) + " ]");

                        MailFactory.getMailSender().sendMessage("mlstatus@monalisa.cern.ch",
                                new String[] { "ramiro@monalisa-chi.uslhcnet.org", "ramiro@roedu.net" },
                                "MLMemWatcher @ " + fullName, msg.toString() + "Mem Status");
                        lastA1SentMail = NTPDate.currentTimeMillis();
                    } catch (Throwable t1) {

                    }
                }
            }
            return;
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Triggering alarm1..");
        }

        if ((a1Count > 20) && (a1Count < 30)) {//do not try to create the file forever
            try {
                if (FarmHOME != null) {
                    new File(FarmHOME + "/UPDATE").createNewFile();
                }
            } catch (Throwable t) {
            }
        }

        try {
            if (a1Count > 10) {
                if ((lastA1SentMail + dtResendAlertTime) < NTPDate.currentTimeMillis()) {
                    StringBuilder msg = new StringBuilder();
                    msg.append("[ ").append(new Date()).append(" / ").append(new Date(NTPDate.currentTimeMillis()))
                            .append(" ] ").append(fullName).append(" Mem Status: \n");
                    long tMem = Runtime.getRuntime().totalMemory();
                    long fMem = Runtime.getRuntime().freeMemory();
                    long mMem = Runtime.getRuntime().maxMemory();

                    msg.append("\nTotal Mem [ " + tMem + " / " + ((double) tMem / (double) 1048576) + " ]");
                    msg.append("\nFree Mem [ " + fMem + " / " + ((double) fMem / (double) 1048576) + " ]");
                    msg.append("\nMax Mem [ " + mMem + " / " + ((double) mMem / (double) 1048576) + " ]");
                    logger.log(Level.INFO, "MLMemWatcher Sending Email: \n\n" + msg.toString() + "\n\n");

                    if ((RCPT != null) && (RCPT.length >= 1)) {
                        MailFactory.getMailSender().sendMessage("mlstatus@monalisa.cern.ch", RCPT,
                                "MLMemWatcher @ " + fullName, msg.toString() + "Mem Status");
                    } else {
                        logger.log(
                                Level.INFO,
                                "MLMemWatcher Sending Email failed !! No RCPT defined in ml.properties. \n Please add lia.Monitor.Filters.MLMemWatcher.RCPT in ml.properties");
                    }
                    lastA1SentMail = NTPDate.currentTimeMillis();
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got Exception ", t);
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Finishing executing alarm1.");
        }
    }

    /** (non-Javadoc)
     * @see lia.Monitor.Filters.GenericMLFilter#getSleepTime()
     */
    @Override
    public long getSleepTime() {
        return 5 * 1000;
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
    }

    /**
     * @see lia.Monitor.Filters.GenericMLFilter#expressResults()
     */
    @Override
    public Object expressResults() {
        // TODO Auto-generated method stub
        long cMem = Runtime.getRuntime().totalMemory();
        long freeMem = Runtime.getRuntime().freeMemory();
        long maxMem = Runtime.getRuntime().maxMemory();

        if (logger.isLoggable(Level.FINEST)) {
            StringBuilder sb = new StringBuilder();
            sb.append("MLMemWathcer - expressResults\n");
            sb.append("cMem = ").append(cMem).append("\n");
            sb.append("freeMem = ").append(freeMem).append("\n");
            sb.append("maxMem = ").append(maxMem).append("\n");
            logger.log(Level.FINEST, sb.toString());
        }
        if (((cMem + MIN_FREE_MEM_LIMIT) < maxMem) || (freeMem > MIN_FREE_MEM_LIMIT)) {
            a1Triggered = false;
            a1Count = 0;
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "All ok reseting a1Triggered and a1Count");
            }
            return null;
        }

        if (!a1Triggered) {
            a1Triggered = true;
        }

        if (a1Triggered) {
            alarm1();
        }
        return null;
    }

}