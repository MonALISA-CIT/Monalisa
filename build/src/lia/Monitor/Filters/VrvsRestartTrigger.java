package lia.Monitor.Filters;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Farm.FarmMonitor;
import lia.Monitor.modules.VrvsUtil;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.util.MLProcess;
import lia.util.mail.MailFactory;
import lia.util.ntp.NTPDate;

public class VrvsRestartTrigger extends GenericMLFilter {

    /**
     * 
     */
    private static final long serialVersionUID = 2319548494504957892L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(VrvsRestartTrigger.class.getName());

    //restart cmd
    private static final String restartCmd = AppConfig.getProperty("lia.Monitor.VrvsRestartScript", null);

    //H323 restart cmd
    private static String restartH323Cmd = AppConfig.getProperty("lia.Monitor.H323RestartScript", null);

    //should I restart the reflector using 'restartCmd'?
    private static final boolean useRestartCmd = Boolean.valueOf(
            AppConfig.getProperty("lia.Monitor.useVrvsRestartScript", "false").trim()).booleanValue();

    //should I restart the reflector using 'restartH323Cmd'?
    private final boolean useH323RestartCmd = Boolean.valueOf(
            AppConfig.getProperty("lia.Monitor.useH323RestartScript", "false").trim()).booleanValue();;

    //After how many hours should I resend the mail? (in minutes!!!)
    private static final long dtResendAlertTime = Long.valueOf(
            AppConfig.getProperty("lia.Monitor.Filters.VrvsRestartTrigger.resendAlertTime", "360").trim()).longValue() * 1000 * 60;

    //but no often than
    private static final long dtResendAlertTime2 = Long.valueOf(
            AppConfig.getProperty("lia.Monitor.Filters.VrvsRestartTrigger.resendAlertTime", "360").trim()).longValue() * 1000 * 60;

    private static final long restartH323Delay = Long.valueOf(
            AppConfig.getProperty("lia.Monitor.Filters.VrvsRestartTrigger.restartH323Delay", "10").trim()).longValue() * 1000;
    private static long resendAlertTime;

    private static long resendH323AlertTime;

    private static String Name = "VrvsRestartTrigger";

    String fullName;

    private static final String fromAddr = "mlvrvs@monalisa-chi.uslhcnet.org";

    // after 'dt1' sec sending email
    private static long dt1 = Long.valueOf(
            AppConfig.getProperty("lia.Monitor.Filters.VrvsRestartTrigger.Alarm1Time", "70").trim()).longValue() * 1000;

    private static long atime1;

    // after 'dt2' trying to restart the reflector!
    private static long dt2 = Long.valueOf(
            AppConfig.getProperty("lia.Monitor.Filters.VrvsRestartTrigger.Alarm2Time", "200").trim()).longValue() * 1000;

    private static long atime2;

    // after 'dt1' sec sending email
    private static long H323dt1 = Long.valueOf(
            AppConfig.getProperty("lia.Monitor.Filters.VrvsRestartTrigger.H323Alarm1Time", "90").trim()).longValue() * 1000;

    private static long H323atime1;

    // after 'dt2' trying to restart the reflector!
    private static long H323dt2 = Long.valueOf(
            AppConfig.getProperty("lia.Monitor.Filters.VrvsRestartTrigger.H323Alarm2Time", "220").trim()).longValue() * 1000;

    private static long H323atime2;

    private static final String rcptMailAddresses = AppConfig.getProperty("lia.Monitor.vrvs.VrvsRestartTrigerRCPT",
            "mlvrvs@monalisa-chi.uslhcnet.org").trim();
    private static String hostName;

    private static String[] RCPT = null;

    private static boolean a1Triggered;

    private static boolean a2Triggered;

    private static boolean a1H323Triggered;

    private static boolean a2H323Triggered;
    private static final String H323cmd = "vrvs/check_h323/end";

    //Delay the trigger to start ( in seconds )
    private static final long DELAY_START = Long.valueOf(
            AppConfig.getProperty("lia.Monitor.Filters.VrvsRestartTrigger.startDelay", "120").trim()).longValue() * 1000;

    //  Email Stuff
    private static final String contactEmail = AppConfig.getProperty("MonaLisa.ContactEmail", null);

    private static final boolean useContactEmail = Boolean.valueOf(
            AppConfig.getProperty("include.MonaLisa.ContactEmail", "false").trim()).booleanValue();

    private long lastA1SentMail = 0;
    private long lastA2SentMail = 0;

    private long lastA1H323SentMail = 0;
    private final long lastA2H323SentMail = 0;

    public static String username = "user.name";
    public static final String[] netstatCmd = new String[] { "netstat", "-tanp" };
    public static String[] pstreeCmd;
    private static final String[] envp = new String[] { "PATH=/bin:/usr/bin:/sbin:/usr/sbin:/usr/local/bin:/usr/local/sbin" };
    private static final String H323VerifyString = AppConfig.getProperty(
            "lia.Monitor.Filters.VrvsRestartTrigger.H323VerifyString", "H323 host(s) connected:");
    private static final int port = Integer.valueOf(AppConfig.getProperty("lia.Monitor.VRVS_port", "46011").trim())
            .intValue();

    private boolean H323VerifierStarted = false;
    private boolean canProcess = false;
    private long stime;

    private boolean firstTime = true;

    static {
        try {
            username = System.getProperty("user.name");
        } catch (Throwable t) {
            username = "vrvs";
        }
        pstreeCmd = new String[] { "pstree", "-u", username, "-p" };
    }

    private static void checkAndSetH323() throws Exception {
        Object reflLock = VrvsUtil.getReflLock();
        synchronized (reflLock) {
            BufferedReader br = VrvsUtil.syncTcpCmd(hostName, port, H323cmd);
            String line = null;
            boolean h323ok = false;
            if (br != null) {
                for (line = br.readLine(); line != null; line = br.readLine()) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, " Line from H323: " + line);
                    }
                    if (line.indexOf(H323VerifyString) != -1) {
                        h323ok = true;
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, "H323 Ok");
                        }
                        break;
                    }
                }
                br.close();
            }
            if (h323ok) {
                H323atime1 = NTPDate.currentTimeMillis() + H323dt1;
                a1H323Triggered = false;
                H323atime2 = NTPDate.currentTimeMillis() + H323dt2;
                a2H323Triggered = false;
            } else {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "H323 NOT Ok!!! a1 [ " + a1Triggered + ", " + a1H323Triggered + " ]\n"
                            + "a2 [ " + a2Triggered + ", " + a2H323Triggered + " ]\n");
                }
            }
        }
    }

    class RestartH323Thread extends Thread {

        public RestartH323Thread() {
            super(" (ML) RestartH323Thread");
        }

        @Override
        public void run() {
            try {
                StringBuilder sb = new StringBuilder();
                int count = 1;

                logger.log(Level.WARNING, "H323 DEAD! Trying restart...");
                sb.append("H323 Restart Status @ " + fullName);
                Process procVrvs = null;
                boolean restarted = true;

                try {
                    while (a2H323Triggered) {
                        appendSystemStatus(sb);
                        count++;
                        Object reflLock = VrvsUtil.getReflLock();
                        synchronized (reflLock) {
                            //should not get here ... but let's synchronize() things anyway
                            //it is possible to be restarted the entire 
                            if (!a2H323Triggered) {
                                restarted = false;
                                break;
                            }
                            sb.append("\n" + new Date() + ": Trying to restart using cmd = " + restartH323Cmd);
                            procVrvs = MLProcess.exec(restartH323Cmd.trim());
                            sb.append("\n" + new Date() + ": Process created ... ");
                            sb.append("\n\n----------------------------------------------------\n\n");

                            InputStream in = procVrvs.getInputStream();
                            InputStream er = procVrvs.getErrorStream();

                            BufferedReader br = new BufferedReader(new InputStreamReader(in));
                            BufferedReader brerr = new BufferedReader(new InputStreamReader(er));

                            String line;
                            sb.append("\n\nSTDOUT:\n");
                            while ((br != null) && ((line = br.readLine()) != null)) {
                                sb.append(line);
                                sb.append("\n");
                            }

                            procVrvs.waitFor();

                            sb.append("\n\nSTDERR:\n");
                            while ((brerr != null) && brerr.ready() && ((line = brerr.readLine()) != null)) {
                                sb.append(line);
                                sb.append("\n");
                            }

                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, "H323 restarting terminated");
                            }
                            procVrvs = null;
                            sb.append("\n\n-----------------------------------------------------\n");
                            sb.append("\n" + new Date() + " Finish restarting H323!");
                            try {
                                Thread.sleep(2000);
                            } catch (Exception e1) {
                            }
                        }
                        try {
                            Thread.sleep(restartH323Delay);
                        } catch (Throwable t1) {

                        }
                        checkAndSetH323();
                        if (a2H323Triggered) {
                            sb.append("\n\n==============================================================\n");
                            sb.append("\n\n [ " + new Date() + " ] Retry to restart H323 count = " + count);
                        }
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exception restarting H323 ", t);
                    sb.append("\nGot Exception restarting H323" + t);
                }
                if (restarted) {
                    appendSystemStatus(sb);
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, sb.toString());
                    }
                    MailFactory.getMailSender().sendMessage(FarmMonitor.realFromAddress, fromAddr, RCPT,
                            "H323 Restart Status @ " + fullName, sb.toString());
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "Mail sending terminated");
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Got Exception", t);
            }
        }
    }

    class H323Verifier extends Thread {

        long verifyTime;
        boolean hasToRun = true;

        public H323Verifier() {
            verifyTime = Long.valueOf(
                    AppConfig.getProperty("lia.Monitor.Filters.VrvsRestartTrigger.H323VerifyTime", "30").trim())
                    .longValue() * 1000;
        }

        @Override
        public void run() {
            logger.log(Level.INFO, "H323Verifier started [ " + hostName + ":" + port + " @ " + (verifyTime / 1000)
                    + " s ] ");
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "H323Verifier started [ " + H323VerifyString + " ] ");
            }
            while (hasToRun) {
                try {
                    try {
                        Thread.sleep(verifyTime);
                    } catch (Throwable t) {
                    }
                    checkAndSetH323();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got Exception verifying H323", t);
                }
            }
        }
    }

    public VrvsRestartTrigger(String farmName) {
        super(farmName);
        String localAddress = AppConfig.getProperty("lia.Monitor.useIPaddress", null);

        if ((localAddress == null) || (localAddress.length() == 0)) {
            try {
                localAddress = InetAddress.getLocalHost().getHostAddress();
            } catch (Throwable t) {

            }
        }

        this.fullName = farmName + "[ " + ((localAddress != null) ? localAddress : "Could NOT get Local Address!")
                + " ]";

        boolean addContactEmail = (useContactEmail && (contactEmail != null) && (contactEmail.indexOf("@") != -1));
        StringTokenizer rcptStk = new StringTokenizer(rcptMailAddresses, ",");
        RCPT = new String[rcptStk.countTokens() + (addContactEmail ? 1 : 0)];
        for (int i = 0; rcptStk.hasMoreTokens(); i++) {
            RCPT[i] = rcptStk.nextToken();
        }
        if (addContactEmail) {
            RCPT[RCPT.length - 1] = contactEmail;
        }

        firstTime = true;
        //        RCPT = new String[] {"ramiro@roedu.net","ramiro@monalisa-chi.uslhcnet.org"};
    }

    /* from MonitorFilter */
    @Override
    public String getName() {
        return Name;
    }

    private void appendSystemStatus(StringBuilder sb) {
        if (sb == null) {
            return;
        }
        sb.append("\n\n$netstat -tanp|grep [ vrvs | 46010 ]\n\n");
        try {
            Process p = MLProcess.exec(netstatCmd, envp);
            InputStream in = p.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line = null;
            while ((br != null) && ((line = br.readLine()) != null)) {
                //instead of | grep vrvs for netstat cmd
                if ((line.indexOf("vrvs") != -1) || (line.indexOf("46010") != -1)) {
                    sb.append(line);
                    sb.append("\n");
                }
            }

            p.waitFor();

        } catch (Throwable t) {
            sb.append("\n Got exc while executing " + netstatCmd + ". Exc =  " + t + "\n\n");
        }
        sb.append("\n---------------------- End netstat -----------------------\n");
        sb.append("\n$pstree -u " + username + " | grep vrvs -----------------------\n\n");
        try {
            Process p = MLProcess.exec(pstreeCmd, envp);
            InputStream in = p.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line = null;

            while ((br != null) && ((line = br.readLine()) != null)) {
                //instead of | grep vrvs for pstree cmd
                if (line.indexOf("vrvs") != -1) {
                    sb.append(line);
                    sb.append("\n");
                }
            }

            p.waitFor();
        } catch (Throwable t) {
            sb.append("\n Got exc while executing " + pstreeCmd + ". Exc =  " + t + "\n\n");
        }
        sb.append("\n---------------------- End pstree -----------------------\n\n");
    }

    /** problem with vrvs_mgr -> send mail */
    private void alarm1() {
        a1Triggered = true;

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Triggering alarm1..");
        }

        try {
            if ((lastA1SentMail + dtResendAlertTime2) < NTPDate.currentTimeMillis()) {
                logger.log(Level.WARNING, "vrvs_mgr DOWN! Sending Email...");
                StringBuilder msg = new StringBuilder();
                msg.append("[ " + new Date() + " / ").append(new Date(NTPDate.currentTimeMillis()))
                        .append(" ] vrvs_mgr seems to be down @ " + fullName);
                appendSystemStatus(msg);
                MailFactory.getMailSender().sendMessage("mlvrvs@monalisa-chi.uslhcnet.org", RCPT,
                        "vrvs_mgr Down @ " + fullName, msg.toString());
                lastA1SentMail = NTPDate.currentTimeMillis();
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got Exception ", t);
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Finishing executing alarm1.");
        }

        resendAlertTime = NTPDate.currentTimeMillis() + dtResendAlertTime;

    }

    /** problem with h323 -> send mail */
    private void alarm1H323() {
        a1H323Triggered = true;

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Triggering alarm1H323...");
        }

        try {
            if ((lastA1H323SentMail + dtResendAlertTime2) < NTPDate.currentTimeMillis()) {
                logger.log(Level.WARNING, "vrvs_h323 DOWN! Sending Email...");
                StringBuilder msg = new StringBuilder();
                msg.append("[ " + new Date() + " / ").append(new Date(NTPDate.currentTimeMillis()))
                        .append(" ] H323 seems to be down @ " + fullName);
                appendSystemStatus(msg);
                MailFactory.getMailSender().sendMessage("mlvrvs@monalisa-chi.uslhcnet.org", RCPT,
                        "vrvs_h323 Down @ " + fullName, msg.toString());
                lastA1H323SentMail = NTPDate.currentTimeMillis();
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got Exception ", t);
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Finishing executing alarm1H323.");
        }

        resendH323AlertTime = NTPDate.currentTimeMillis() + dtResendAlertTime;

    }

    /** problem with vrvs_mgr -> try to restart it (if restartCmd avaliable) & send mail */
    private void alarm2() {
        a2Triggered = true;
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Triggering alarm2..");
        }

        if (!useRestartCmd) {
            try {
                logger.log(Level.WARNING, "vrvs_mgr DOWN! Should restart but this option is disalbed! Sending Email...");
                if ((lastA2SentMail + dtResendAlertTime2) < NTPDate.currentTimeMillis()) {
                    StringBuilder msg = new StringBuilder();
                    msg.append("[ " + new Date() + " / ")
                            .append(new Date(NTPDate.currentTimeMillis()))
                            .append(" ] vrvs_mgr @ " + fullName
                                    + " is down, \nbut it has not been restarted because this option is disabled");
                    appendSystemStatus(msg);
                    MailFactory.getMailSender().sendMessage(FarmMonitor.realFromAddress, fromAddr, RCPT,
                            "vrvs_mgr Down @ " + fullName + "! Restart DISABLED", msg.toString());
                    lastA2SentMail = NTPDate.currentTimeMillis();
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Got Exception ", t);
            }
            return;
        }

        try {
            StringBuilder sb = new StringBuilder(1024);
            logger.log(Level.WARNING, "vrvs_mgr DEAD! Trying restart...");
            sb.append("vrvs_mgr Restart Status @ " + fullName);
            appendSystemStatus(sb);
            Process procVrvs = null;

            try {
                if (restartCmd != null) {
                    Object reflLock = VrvsUtil.getReflLock();
                    synchronized (reflLock) {
                        sb.append("\n" + new Date() + " / " + new Date(NTPDate.currentTimeMillis())
                                + " Trying to restart using cmd = " + restartCmd);
                        procVrvs = MLProcess.exec(restartCmd.trim());
                        sb.append("\n\n----------------------------------------------------\n\n");

                        InputStream in = procVrvs.getInputStream();
                        InputStream er = procVrvs.getErrorStream();

                        BufferedReader br = new BufferedReader(new InputStreamReader(in));
                        BufferedReader brerr = new BufferedReader(new InputStreamReader(er));

                        String line;
                        sb.append("\n\nSTDOUT:\n");
                        while ((br != null) && ((line = br.readLine()) != null)) {
                            sb.append(line);
                            sb.append("\n");
                        }

                        procVrvs.waitFor();

                        sb.append("\n\nSTDERR:\n");
                        while ((brerr != null) && brerr.ready() && ((line = br.readLine()) != null)) {
                            sb.append(line);
                            sb.append("\n");
                        }

                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, sb.toString());
                        }

                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, "Vrvs restarting terminated");
                        }

                        procVrvs = null;
                        sb.append("\n\n-----------------------------------------------------\n");
                        sb.append("\n" + new Date() + " / " + new Date(NTPDate.currentTimeMillis())
                                + " Finish restarting the reflector!");
                        try {
                            Thread.sleep(2000);
                        } catch (Exception e1) {
                        }
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, " Trying checkAndSetH323()");
                        }
                        checkAndSetH323();
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, "Finished checkAndSetH323()");
                        }
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Got exception restarting the reflector ", t);
                sb.append("\nGot Exception restarting the reflector" + t);
            }
            appendSystemStatus(sb);
            MailFactory.getMailSender().sendMessage(FarmMonitor.realFromAddress, fromAddr, RCPT,
                    "Restart Status @ " + fullName, sb.toString());
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Mail sending terminated");
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got Exception", t);
        }
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Finished alarm2..");
        }
    }

    /** problem with h323 -> try to restart it (if restartCmd avaliable) & send mail */
    private void alarm2H323() {
        a2H323Triggered = true;
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Triggering alarm2H323...");
        }

        if (!useH323RestartCmd) {
            try {
                logger.log(Level.WARNING,
                        "vrvs_h323 DOWN! Should restart but this option is disalbed! Sending Email...");
                if ((lastA2H323SentMail + dtResendAlertTime2) < NTPDate.currentTimeMillis()) {
                    StringBuilder msg = new StringBuilder();
                    msg.append("[ " + new Date() + " / " + new Date(NTPDate.currentTimeMillis()) + " ] vrvs_h323 @ "
                            + fullName + " is down, \nbut it has not been restarted because this option is disabled");
                    appendSystemStatus(msg);
                    MailFactory.getMailSender().sendMessage(FarmMonitor.realFromAddress, fromAddr, RCPT,
                            "vrvs_h323 Down @ " + fullName + "! Restart DISABLED", msg.toString());
                    lastA2SentMail = NTPDate.currentTimeMillis();
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Got Exception ", t);
            }
            return;
        }
        if (restartH323Cmd != null) {
            new RestartH323Thread().start();
        } else {
            logger.log(Level.WARNING, "Cannot restart H323 because restartH323Cmd == null !!");
        }
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " Finished alarm2H323..");
        }
    }

    /**
     * @see lia.Monitor.Filters.GenericMLFilter#getSleepTime()
     */
    @Override
    public long getSleepTime() {
        return 1000;
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

        if ((r.ClusterName.equals("Reflector") && (r.Module.indexOf("Client") != -1))
                || (r.ClusterName.equals("Peers") && (r.Module.indexOf("Conn") != -1))) {
            atime1 = NTPDate.currentTimeMillis() + dt1;
            a1Triggered = false;
            atime2 = NTPDate.currentTimeMillis() + dt2;
            a2Triggered = false;
            if (!H323VerifierStarted && (r.ClusterName.equals("Reflector") && (r.Module.indexOf("Client") != -1))) {
                if (r.NodeName != null) {
                    hostName = r.NodeName;
                    new H323Verifier().start();
                    H323VerifierStarted = true;
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
            if (firstTime) {
                firstTime = false;
                a1Triggered = false;
                a2Triggered = false;
                stime = NTPDate.currentTimeMillis() + DELAY_START;
                logger.log(Level.INFO, " alarm1 " + (dt1 / 1000) + "s alarm2 " + (dt2 / 1000) + "s alarm3 "
                        + "s DELAY_START " + (DELAY_START / 1000) + "s");
                atime1 = NTPDate.currentTimeMillis() + dt1;
                atime2 = NTPDate.currentTimeMillis() + dt2;
                return null;
            }
            long now = NTPDate.currentTimeMillis();
            if (canProcess) {

                boolean mgrAlarm = false;
                if ((now > resendAlertTime) && a1Triggered) {
                    a1Triggered = false;
                }

                if ((now > atime1) && !a1Triggered) {
                    mgrAlarm = true;
                    alarm1();
                }

                if ((now > atime2) && !a2Triggered) {
                    mgrAlarm = true;
                    alarm2();
                }

                if (!a1Triggered && !a2Triggered && !mgrAlarm) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "\nVerifying H323\n");
                    }
                    //we can check for H323 ... otherwise it seems that the reflector is down
                    if ((now > resendH323AlertTime) && a1H323Triggered) {
                        a1H323Triggered = false;
                    }

                    if ((now > H323atime1) && !a1H323Triggered) {
                        alarm1H323();
                    }

                    if ((now > H323atime2) && !a2H323Triggered) {
                        alarm2H323();
                    }
                }
            } else if (now > stime) {
                canProcess = true;
                logger.log(Level.INFO, "VRVSRestartTrigger Entered Main Loop");
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "VRVSRestartTrigger Exception in main loop", t);
        }

        return null;
    }
}