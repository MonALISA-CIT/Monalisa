package lia.util.mail;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.EMsg;
import lia.util.MLProcess;
import lia.util.Utils;

import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

public class DirectMailSender extends MailSender implements Runnable, AppConfigChangeListener {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(DirectMailSender.class.getName());

    private static volatile int MAX_EMAIL_MSGS = 500;

    private static DirectMailSender me = null;

    static String lastline; // last line read from mailhost

    static String result; // holds msg if an error occurs

    private final ArrayBlockingQueue<EMsg> mailq;

    private final boolean hasToRun = true;

    private static int CONNECT_TIMEOUT;

    private static int SOCK_TIMEOUT;

    private static boolean DEVEL_STATION;

    private static boolean USE_LOCAL_MAIL;

    private static String LOCAL_MAIL_COMMAND;

    private static String mailServerHostname;

    private static int mailServerPort;

    private DirectMailSender() {
        reloadConfig();
        mailq = new ArrayBlockingQueue<EMsg>(MAX_EMAIL_MSGS);
    }

    @Override
    public void notifyAppConfigChanged() {
        reloadConfig();
    }

    private void reloadConfig() {
        CONNECT_TIMEOUT = Integer.valueOf(AppConfig.getProperty("lia.util.mail.CONNECT_TIMEOUT", "10")).intValue() * 1000;
        SOCK_TIMEOUT = Integer.valueOf(AppConfig.getProperty("lia.util.mail.SOCK_TIMEOUT", "40")).intValue() * 1000;
        try {
            String st = AppConfig.getProperty("lia.util.mail.DEVEL_STATION", "false");
            if (st != null) {
                DEVEL_STATION = Boolean.valueOf(st).booleanValue();
            } else {
                DEVEL_STATION = false;
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ DMS ] Got ex parsing lia.util.mail.DEVEL_STATION", t);
            }
            DEVEL_STATION = false;
        }

        boolean useLocalMail = false;
        try {
            useLocalMail = Boolean.valueOf(AppConfig.getProperty("lia.util.mail.USE_LOCAL_MAIL", "false"))
                    .booleanValue();
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ DMS ] Got ex parsing lia.util.mail.USE_LOCAL_MAIL", t);
            }
            useLocalMail = false;
        }

        USE_LOCAL_MAIL = useLocalMail;

        String localMailCmd = null;
        try {
            localMailCmd = AppConfig.getProperty("lia.util.mail.LOCAL_MAIL_COMMAND");
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ DMS ] Got ex parsing lia.util.mail.USE_LOCAL_MAIL", t);
            }
        }

        LOCAL_MAIL_COMMAND = localMailCmd;

        String sMailServer = null;
        try {
            sMailServer = AppConfig.getProperty("lia.util.mail.MailServer");
        } catch (Throwable t) {
            sMailServer = null;
        }
        mailServerHostname = sMailServer;

        int iMailServerPort = 25;
        try {
            iMailServerPort = Integer.parseInt(AppConfig.getProperty("lia.util.mail.MailServerPort", "25"));
        } catch (Throwable t) {
            iMailServerPort = 25;
        }
        mailServerPort = iMailServerPort;

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [ DMS ] Config params" + " DEVEL_STATION = " + DEVEL_STATION + " USE_LOCAL_MAIL "
                    + USE_LOCAL_MAIL + " LOCAL_MAIL_COMMAND " + LOCAL_MAIL_COMMAND);
        }

    }

    public static synchronized DirectMailSender getInstance() {
        if (me == null) {
            try {
                MAX_EMAIL_MSGS = AppConfig.geti("lia.util.mail.MAX_EMAIL_MSGS", 500);
            } catch (Throwable t) {
                MAX_EMAIL_MSGS = 50;
            }
            me = new DirectMailSender();
            AppConfig.addNotifier(me);
            Thread myThread = new Thread(me, "(ML) DMS Thread !");
            try {
                myThread.setDaemon(true);
            } catch (Throwable t1) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "DMS Thread cannot set DAEMON", t1);
                }
            }
            myThread.start();
        }
        return me;
    }

    /**
     * Helper function to send email based on MX records found ( if any ) in DNS
     * 
     * @param from
     *            -
     *            From field
     * @param to
     *            -
     *            Array of String[] ( will appear in To Field )
     * @param Subject
     * @param message
     * @throws Exception
     */
    @Override
    public void run() {
        logger.log(Level.INFO, " [ DMS ] Started ... ");
        while (hasToRun) {
            try {
                if (mailq.size() != 0) {
                    final ArrayList<EMsg> tmpV = new ArrayList<EMsg>();
                    mailq.drainTo(tmpV);

                    for (final EMsg em : tmpV) {
                        try {
                            sendMessageFromThread(em.from, em.to, em.subject, em.message);
                        } catch (Throwable sendT) {
                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, " Got Exc ", sendT);
                            }
                        }
                    }
                }

                try {
                    Thread.sleep(1000);
                } catch (Exception e1) {
                    // sleep here
                }
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " Got Exc sending mail", t);
                }
            }
        }
    }

    public static final void sendMessageFromThread(String from, String[] to, String Subject, String message)
            throws Exception {
        if (DEVEL_STATION) {
            return;
        }
        sendMessageFromThread(from, from, to, Subject, message);
    }

    private static final SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z");

    public static final void sendMessageFromThread(String realFrom, String from, String[] to, String Subject,
            String message) throws Exception {
        if (DEVEL_STATION) {
            return;
        }
        int port = 25;
        if ((to == null) || (to.length == 0)) {
            logger.log(Level.WARNING, "Cannot send to Nobody!? to " + ((to == null) ? "== null" : " length == 0"));
            return;
        }

        if (USE_LOCAL_MAIL) {
            StringBuilder mailProcCMD = new StringBuilder();
            try {
                String mailCmd = LOCAL_MAIL_COMMAND;
                if (mailCmd == null) {
                    mailCmd = "mail";
                }
                mailProcCMD.append("echo \\\"").append(message).append("\\\" | ").append(mailCmd);
                mailProcCMD.append(" -s \\\"").append(Subject).append("\\\"");
                for (String element : to) {
                    mailProcCMD.append(" ").append(element);
                }
                // Process p = MLProcess.exec(new String[] {"/bin/sh", "-c", mailProcCMD.toString()});
                Process p = MLProcess.exec(mailProcCMD.toString());
                p.waitFor();
                p.destroy();
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " Exc executing [ " + mailProcCMD.toString() + " ]", t);
                }
            }
            return;
        }

        String toAddr = "";
        for (int i = 0; i < to.length; i++) {
            toAddr += to[i] + ((i < (to.length - 1)) ? "," : "");
        }

        for (String toc : to) {
            try {
                String host = null;

                int k = toc.indexOf("@");

                if (k < 0) {
                    logger.log(Level.WARNING, "Wrong email address " + toc);
                    continue;
                }

                String d[] = toc.split("@");

                if ((d == null) || (d.length == 0)) {
                    logger.log(Level.WARNING, "Wrong email address " + toc);
                    continue;
                }

                String pd = d[d.length - 1];

                String dp[] = pd.split("\\.");

                if ((dp == null) || (dp.length == 0)) {
                    logger.log(Level.WARNING, "Wrong email address " + toc);
                    continue;
                }

                String pdomain = null;
                for (String dtk : dp) {
                    if ((dtk != null) && (dtk.length() > 0)) {
                        if (pdomain == null) {
                            pdomain = dtk;
                        } else {
                            pdomain += "." + dtk;
                        }
                    }
                }

                if ((pdomain == null) || (pdomain.length() == 0) || (pdomain.indexOf(".") == -1)) {
                    logger.log(Level.WARNING, "Wrong email address " + toc);
                    continue;
                }
                int lpi = pdomain.lastIndexOf(".");
                String domain = pdomain.substring(0, lpi + 1);
                String lastp = pdomain.substring(lpi + 1);

                if ((lastp == null) || (lastp.length() == 0) || lastp.endsWith("-")) {
                    logger.log(Level.WARNING, "Wrong email address " + toc);
                    continue;
                }

                String clastp = lastp.replaceAll("[^((\\w)+(\\-)*)]", "");

                if ((clastp == null) || (clastp.length() == 0)) {
                    logger.log(Level.WARNING, "Wrong email address " + toc);
                    continue;
                }

                domain += clastp;

                boolean mailNotSent = true;
                if (domain.length() > 0) {
                    logger.fine("Lookup");

                    Lookup lookup = new Lookup(domain, Type.MX);

                    logger.fine("running");

                    lookup.run();

                    logger.fine("done");

                    Record[] records;

                    if ((lookup.getResult() == Lookup.SUCCESSFUL) && ((records = lookup.getAnswers()) != null)) {
                        for (int j = 0; (j < records.length) && mailNotSent; j++) {
                            try {
                                host = records[j].getAdditionalName().toString();

                                if (logger.isLoggable(Level.FINEST)) {
                                    logger.log(Level.FINEST, "Trying Hostname " + host + " for " + toc);
                                }
                                if (host == null) {
                                    continue;
                                }
                                StringBuilder hm = new StringBuilder();
                                hm.append("From: ").append(from).append("\r\n");
                                hm.append("To: ").append(toAddr).append("\r\n");

                                String sDate;

                                synchronized (sdf) {
                                    sDate = sdf.format(new Date());
                                }

                                final String msgID = Long.toString(getRandomPositiveLong(), Character.MAX_RADIX) + "."
                                        + Long.toString(getRandomPositiveLong(), Character.MAX_RADIX) + "."
                                        + Long.toString(getRandomPositiveLong(), Character.MAX_RADIX) + "."
                                        + Long.toString(getRandomPositiveLong(), Character.MAX_RADIX);

                                String dPart = "example.com";

                                if (realFrom.indexOf("@") >= 0) {
                                    dPart = realFrom.split("@")[1];
                                }
                                hm.append("Message-ID: <").append(msgID).append("@").append(dPart).append(">\r\n");

                                hm.append("Date: ").append(sDate).append("\r\n");
                                hm.append("Subject: ").append(Subject).append("\r\n");
                                hm.append("User-Agent: MonALISA Simple Mail Sender\r\n\r\n");
                                hm.append(message);
                                if (mailServerHostname != null) {
                                    directSend(mailServerHostname, mailServerPort, realFrom, toc, hm.toString());
                                } else {
                                    directSend(host, port, realFrom, toc, hm.toString());
                                }
                                mailNotSent = false;
                            } catch (Throwable t) {
                                if (logger.isLoggable(Level.FINEST)) {
                                    logger.log(Level.FINEST, "Got Exception using host " + host + " for " + toc, t);
                                    logger.log(Level.FINEST, "Will try next MX");
                                }
                            } // try next MX
                        }
                    } else { // try to send directly
                        try {
                            host = domain;
                            if (logger.isLoggable(Level.FINEST)) {
                                logger.log(Level.FINEST, "Trying Hostname " + host + " for " + toc);
                            }

                            // duplicate - I know, I know ... when it will me more time ...
                            String hm = "From: " + from + "\r\n";
                            hm += "To: " + toAddr + "\r\n";

                            String sDate;

                            synchronized (sdf) {
                                sDate = sdf.format(new Date());
                            }

                            hm += "Date: " + sDate + "\r\n";
                            hm += "Subject: " + Subject + "\r\n";
                            hm += "User-Agent: MonALISA Simple Mail Sender\r\n\r\n";
                            hm += message;
                            if (mailServerHostname != null) {
                                directSend(mailServerHostname, mailServerPort, realFrom, toc, hm);
                            } else {
                                directSend(host, port, realFrom, toc, hm);
                            }
                            mailNotSent = false;
                        } catch (Throwable t) {
                            if (logger.isLoggable(Level.FINEST)) {
                                logger.log(Level.FINEST, "Got Exception using host " + host + " for " + toc, t);
                                logger.log(Level.FINEST, "Will try next MX");
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "Got General Exception sending to " + toc, t);
                }
            }
        }
    }

    @Override
    public void sendMessage(String from, String[] to, String Subject, String message) throws Exception {
        if (DEVEL_STATION) {
            return;
        }
        if (mailq.size() > MAX_EMAIL_MSGS) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "MAX_EMAILS_MSGS [ " + MAX_EMAIL_MSGS + " ] reached! Dropping mails....");
            }
            return;
        }
        mailq.add(new EMsg(from, to, Subject, message));
    }

    @Override
    public void sendMessage(String mailFrom, String from, String[] to, String Subject, String message) throws Exception {
        if (DEVEL_STATION) {
            return;
        }
        if (mailq.size() > MAX_EMAIL_MSGS) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "MAX_EMAILS_MSGS [ " + MAX_EMAIL_MSGS + " ] reached! Dropping mails....");
            }
            return;
        }
        mailq.add(new EMsg(mailFrom, from, to, Subject, message));
    }

    private static final void directSend(String host, int port, String sender, String receiver, String msg)
            throws Exception {

        Socket s = null;
        PrintStream p = null;
        BufferedReader br = null;
        try {
            s = new Socket();
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT);
            try {
                s.setTcpNoDelay(true);
                s.setSoTimeout(SOCK_TIMEOUT);
                s.setSoLinger(true, 10);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[DMS] Unable to set socket options NO_DELAY TIMEOUT SO_LINGER. Cause:", t);
            }

            p = new PrintStream(s.getOutputStream());
            br = new BufferedReader(new InputStreamReader(s.getInputStream()));
            expect(br, "220", "no greeting");

            String helohost = InetAddress.getLocalHost().getHostName();
            p.print("HELO " + helohost + "\r\n");
            p.flush();
            expect(br, "250", "helo");

            // RFC 821
            // the correct MAIL command is:
            // MAIL <SP> FROM:<ramiro@roedu.net>
            // Yahoo uses strict RFC
            p.print("MAIL FROM:<" + sender + ">\r\n");
            p.flush();
            expect(br, "250", "mail from");

            // RCPT <SP> TO:<ramiro@roedu.net>
            // Yahoo uses strict RFC
            p.print("RCPT TO:<" + receiver + ">\r\n");
            p.flush();
            expect(br, "250", "rcpt to");

            p.print("DATA\r\n");
            p.flush();
            expect(br, "354", "data");
            // Use two CRLF's above because we need a null line following
            // standard fields to indicate following DATA is message body.
            // now send the message
            BufferedReader brs = new BufferedReader(new StringReader(msg));
            for (;;) {
                String ln = brs.readLine();
                if (ln == null) {
                    break;
                }
                if (ln.equals(".")) {
                    ln = ".."; // to avoid premature truncation
                }
                p.print(ln + "\r\n");
            }

            p.print("\r\n.\r\n");
            p.flush();
            expect(br, "250", "end of data");

            p.print("QUIT\r\n");
            p.flush();
            expect(br, "221", "quit");
        } catch (Throwable t) {
            throw new Exception(t);
        } finally {
            Utils.closeIgnoringException(p);
            Utils.closeIgnoringException(br);
            try {
                if (s != null) {
                    s.close();
                }
            } catch (Throwable t) {
                // ignore socket close exception
            }
        }
    }

    @Override
    public void sendMessage(EMsg eMsg, boolean enqueue) throws Exception {
        if (enqueue) {
            mailq.add(eMsg);
        } else {
            sendMessageFromThread(eMsg.from, eMsg.to, eMsg.subject, eMsg.message);
        }
    }

    private static void expect(BufferedReader br, String expected, String msg) throws Exception {
        // this method reads the mailhost reply and checks that it is as
        // expected
        lastline = br.readLine();
        if ((lastline != null) && (lastline.indexOf(expected) == -1)) {
            throw new Exception(msg + ":" + lastline);
        }
    }

    private static final long getRandomPositiveLong() {
        final Random r = new Random();
        long ret = -1;
        while (ret < 0) {
            ret = r.nextLong();
        }

        return ret;
    }

    public static void main(String args[]) throws Exception {
        // DirectMailSender.sendMessageFromThread("ramiro@gigi.com", new String[]{"ramiro@cern.ch", "ramiro@roedu.net"},
        // "MDA MERGE :) ", "Chiar ca merge...brici! \n\n--\nAIWA,\nRamiro");
        // DirectMailSender.getInstance().sendMessage("ramiro@inspirel.cern.ch", new
        // String[]{"Costin.Grigoras@cern.ch","Ramiro.Voicu@cern.ch"}, "Test Direct Send", " MERGE? ");
        System.setProperty("lia.util.mail.USE_LOCAL_MAIL", "false");
        // System.setProperty("lia.util.mail.LOCAL_MAIL_COMMAND", "/usr/bin/mail");

        // DirectMailSender.getInstance().sendMessage("ramiro@inspirel.cern.ch", new String[] {
        // "costin.grigoras@cern.ch", "ramiro@cern.ch", "costing@basca.ro", "ramiro@roedu.net", "costing@gmail.com",
        // "ramiro.voicu@gmail.com"
        // }, "Test ML Direct Send ( ultimul inainte de commit in CVS )",
        // " FORJA pe mai multe adrese \n\nAIWA,\nRamiro?!? ");

        for (;;) {
            System.out.println(new Date() + "... Sending mail ... ");
            DirectMailSender.getInstance().sendMessage("ramiro@cern.ch", new String[] { "ramiro@hep.caltech.edu" },
                    "Test ML HEP ID - " + Long.toString(getRandomPositiveLong(), Character.MAX_RADIX),
                    "HEP test mail - Ignore it!\n\nRamiro");
            System.out.println(new Date() + "... mail queued ... ");

            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(40));
            } catch (Throwable t) {
                // we're happy
            }
        }

    }

}
