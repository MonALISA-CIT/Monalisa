/**
*
*/
package lia.util.actions;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import lazyj.Utils;
import lia.Monitor.Store.Fast.DB;
import lia.util.MLProcess;
import lia.util.MLProperties;
import lia.util.actions.Action.SeriesState;
import lia.util.mail.MailSender;
import lia.util.ntp.NTPDate;
import lia.web.utils.Annotation;
import lia.web.utils.Formatare;

import org.jivesoftware.smack.XMPPConnection;

import ymsg.network.Session;

/**
 * Standard actions that ML can take by itself:
 * <ul>
 * <li>Executing a command<br>
 * 	<code>
 *  action.2.report_err=low<br>
 *  action.2.type=command<br>
 *  action.2.execute=/home/monalisa/MLrepository/bin/resubmit.sh<br>
 *  </code>
 * </li>
 * <li>Sending an email<br>
 * <code>
 *  action.0.report_err=high<br>
 *  action.0.report_ok=normal<br>
 *  action.0.type=email<br>
 *  action.0.from=ml_notification_service@pcalimonitor.cern.ch<br>
 *  action.0.to=Costin.Grigoras@cern.ch,latchezar.betev@cern.ch<br>
 *  action.0.subject=Temperature in server room is #MSG<br>
 *  action.0.body=Average temperature is : $Eavg($Cv%/UPS/localhost/#0;);
 * </code>
 * </li>
 * <li>Logging the event in a log file<br>
 * <code>
 *  action.1.report_err=low<br>
 *  action.1.report_ok=ok<br>
 *  action.1.type=log<br>
 *  action.1.file=/home/monalisa/MLrepository/logs/queue_size.log<br>
 *  action.1.message=$Edatetime("yyyy-MM-dd HH:mm:ss", now());: Waiting jobs : $CvCERN/ALICE_Users_Jobs_Summary/aliprod/WAITING_jobs; (#MSG)<br>
 * </code>
 * </li>
 * <li>Sending a Y!M message<br>
 * <code>
 *  action.0.report_err=low<br>
 *  action.0.type=yahoo<br>
 *  action.0.username=repository_alice<br>
 *  action.0.password=************<br>
 *  action.0.message=TaskQueue is #MSG on waiting jobs ($CvCERN/ALICE_Users_Jobs_Summary/aliprod/#0;)<br>
 *  action.0.accounts=costin_grig<br>
 * </code>
 * </li>
 * <li>Sending a Jabber message<br>
 * <code>
 *  action.0.message=TaskQueue is #MSG on waiting jobs ($CvCERN/ALICE_Users_Jobs_Summary/aliprod/#0;)<br>
 *  action.0.username=gmail_account<br>
 *  action.0.password=**********<br>
 *  action.0.accounts=costing<br>
 *  action.0.ssl=true<br>
 *  action.0.host=google<br>
 *  action.0.service=google<br>
 *  action.0.port=5223<br>
 *  action.0.resource=MLRepository<br>
 * </code>
 * </li>
 * <li>Adding an annotation to the repository charts<br>
 * <code>
 *  action.3.report_err=offline<br>
 *  action.3.report_ok=online<br>
 *  action.3.type=annotation<br>
 *  action.3.series=#0<br>
 *  action.3.groups=6<br>
 *  action.3.short_text=#1 is down<br>
 *  action.3.full_text=$Cv#0/AliEnServicesStatus/#1/Message;<br>
 * </code>
 * </li>
 * </ul>
 * 
 * @author costing
 * @since May 22, 2007
 */
public class StandardActions implements ActionTaker {
    /**
     * event logger
     */
    private static final Logger logger = Logger.getLogger(StandardActions.class.getName());

    /**
     * @author costing
     * @since Apr 14, 2008
     */
    private static final class SendMail implements Runnable {
        /**
         * 
         */
        private final String[] vsTo;

        /**
         * 
         */
        private final String body;

        /**
         * 
         */
        private final MailSender msSender;

        /**
         * 
         */
        private final String from;

        /**
         * 
         */
        private final String subject;

        /**
         * @param _vsTo
         * @param _body
         * @param _msSender
         * @param _from
         * @param _subject
         */
        SendMail(final String[] _vsTo, final String _body, final MailSender _msSender, final String _from,
                final String _subject) {
            this.vsTo = _vsTo;
            this.body = _body;
            this.msSender = _msSender;
            this.from = _from;
            this.subject = _subject;
        }

        @Override
        public void run() {
            try {
                this.msSender.sendMessage(this.from, this.vsTo, this.subject, this.body);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Exception sending an email", e);
            }
        }
    }

    /**
     * @author costing
     * @since Apr 14, 2008
     */
    private static final class LogWriter implements Runnable {
        /**
         * 
         */
        private final String line;

        /**
         * 
         */
        private final String file;

        /**
         * @param _line
         * @param _file
         */
        LogWriter(final String _line, final String _file) {
            this.line = _line;
            this.file = _file;
        }

        @Override
        public void run() {
            try {
                final PrintWriter pw = new PrintWriter(new FileWriter(this.file, true));

                pw.println(this.line);

                pw.flush();

                pw.close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Exception logging to file", e);
            }
        }
    }

    /**
     * @author costing
     * @since Apr 14, 2008
     */
    private final class JabberSender implements Runnable {
        /**
         * 
         */
        private final String host;

        /**
         * 
         */
        private final String[] vs;

        /**
         * 
         */
        private final String line;

        /**
         * 
         */
        private final String password;

        /**
         * 
         */
        private final String resource;

        /**
         * 
         */
        private final String service;

        /**
         * 
         */
        private final String user;

        /**
         * @param _host
         * @param _vs
         * @param _line
         * @param _password
         * @param _resource
         * @param _service
         * @param _user
         */
        JabberSender(final String _host, final String[] _vs, final String _line, final String _password,
                final String _resource, final String _service, final String _user) {
            this.host = _host;
            this.vs = _vs;
            this.line = _line;
            this.password = _password;
            this.resource = _resource;
            this.service = _service;
            this.user = _user;
        }

        @Override
        public void run() {
            try {
                synchronized (hmJabberSessions) {
                    org.jivesoftware.smack.XMPPConnection conn = hmJabberSessions.get(this.user);

                    if (conn == null) {

                        if (this.host.toLowerCase().equals("google")) {
                            conn = new org.jivesoftware.smack.GoogleTalkConnection();
                            conn.login(this.user, this.password);
                        } else {
                            conn = StandardActions.this.bSSL ? new org.jivesoftware.smack.SSLXMPPConnection(this.host,
                                    StandardActions.this.iPort, this.service)
                                    : new org.jivesoftware.smack.XMPPConnection(this.host, StandardActions.this.iPort,
                                            this.service);
                            conn.login(this.user, this.password, this.resource, true);
                        }
                    }

                    try {
                        for (String element : this.vs) {
                            conn.createChat(element).sendMessage(this.line);
                        }
                    } catch (Exception e) {
                        // an exception in sending is most probably because the previously
                        // established
                        // connection is not active any more
                        hmJabberSessions.remove(this.user);

                        try {
                            conn.close();
                            conn = null;
                        } catch (Throwable t) {
                            // ignore
                        }

                        try {
                            Thread.sleep(1000);
                        } catch (Throwable t) {
                            // ignore
                        }

                        // try to establish a new connection and resend the messages
                        if (this.host.toLowerCase().equals("google")) {
                            conn = new org.jivesoftware.smack.GoogleTalkConnection();
                            conn.login(this.user, this.password);
                        } else {
                            conn = StandardActions.this.bSSL ? new org.jivesoftware.smack.SSLXMPPConnection(this.host,
                                    StandardActions.this.iPort, this.service)
                                    : new org.jivesoftware.smack.XMPPConnection(this.host, StandardActions.this.iPort,
                                            this.service);
                            conn.login(this.user, this.password, this.resource, true);
                        }

                        for (String element : this.vs) {
                            conn.createChat(element).sendMessage(this.line);
                        }

                        // if we had no exception to this point it means that everything is ok
                        // so let's put back the session to the hash
                        hmJabberSessions.put(this.user, conn);
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Cannot send Jabber message because", t);
            }
        }
    }

    /**
     * @author costing
     * @since Apr 14, 2008
     */
    private static final class YMSender implements Runnable {
        /**
         * 
         */
        private final String line;

        /**
         * 
         */
        private final String password;

        /**
         * 
         */
        private final String[] vs;

        /**
         * 
         */
        private final String user;

        /**
         * @param _line
         * @param _password
         * @param _vs
         * @param _user
         */
        YMSender(final String _line, final String _password, final String[] _vs, final String _user) {
            this.line = _line;
            this.password = _password;
            this.vs = _vs;
            this.user = _user;
        }

        @Override
        public void run() {
            try {
                synchronized (hmYahooSessions) {
                    // try first to use a previously established connection
                    ymsg.network.Session _session = hmYahooSessions.get(this.user);

                    if (_session == null) {
                        // if there is no connection yet to yahoo for this user, then try to
                        // establish one
                        _session = new ymsg.network.Session();
                        _session.login(this.user, this.password);
                        hmYahooSessions.put(this.user, _session);
                    }

                    try {
                        for (String element : this.vs) {
                            _session.sendMessage(element, this.line);
                        }
                    } catch (Throwable e) {
                        // an exception in sending is most probably because the previously
                        // established
                        // connection is not active any more
                        hmYahooSessions.remove(this.user);

                        try {
                            _session.logout();
                            _session = null;
                        } catch (Throwable t) {
                            // ignore
                        }

                        try {
                            Thread.sleep(1000);
                        } catch (Throwable t) {
                            // ignore
                        }

                        // try to establish a new connection and resend the messages
                        _session = new ymsg.network.Session();
                        _session.login(this.user, this.password);
                        for (String element : this.vs) {
                            _session.sendMessage(element, this.line);
                        }

                        // if we had no exception to this point it means that everything is
                        // ok
                        // so let's put back the session to the hash
                        hmYahooSessions.put(this.user, _session);
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Cannot send Y! message because", t);
            }
        }
    }

    /** Cache of yahoo connections */
    static final HashMap<String, Session> hmYahooSessions = new HashMap<String, Session>();

    /** Cache of jabber connections */
    static final HashMap<String, XMPPConnection> hmJabberSessions = new HashMap<String, XMPPConnection>();

    /**
     * What kind of action will we take?
     * 0 = command
     * 1 = email
     * 2 = log file
     * 3 = yahoo msg
     * 4 = jabber
     * 5 = annotation
     */
    int iType;

    /**
     * Command to execute
     */
    String sCommand; // in case that iType==0, the command to execute
    /**
     * Command timeout
     */
    long lTimeout;

    /**
     * Apparent source of the email
     */
    String sFrom; // in case that iType==1
    /**
     * Email recipient(s)
     */
    String sTo;
    /**
     * Mail subject
     */
    String sSubject;
    /**
     * Mail body
     */
    String sBody;
    /**
     * Should we send the mail through the proxy or directly from the local machine
     */
    boolean bProxyMail;

    /**
     * Log file
     */
    String sLogFile; // in case that iType==2
    /**
     * Line format
     */
    String sLine;

    /**
     * What is the message for the OK situations
     */
    String sMessageOK = null; // in all the cases
    /**
     * What is the message for the ERROR situations
     */
    String sMessageERR = null;
    /**
     * What is the message for the FLIP-FLOP situations
     */
    String sMessageFlipFlop = null;

    /**
     * Yahoo account / jabber account
     */
    String sUser; // in case that iType==3 & 4
    /**
     * Password for the account
     */
    String sPassword;
    /**
     * Message recipients
     */
    String sAccounts;

    // jabber extra stuff
    /**
     * Jabber uses secure connections?
     */
    boolean bSSL;

    /**
     * Jabber server
     */
    String sHost;

    /**
     * Jabber service, defaults to the host name
     */
    String sService;

    /**
     * Jabber port
     */
    int iPort;

    /**
     * Jabber resource (?)
     */
    String sResource;

    ////////////// Annotation-related fields
    /** Groups */
    String sAnnotationGroups;

    /** Series */
    String sAnnotationSeries;

    /** Short text */
    String sAnnotationText;

    /** Long text */
    String sAnnotationMessage;

    /** Automatically delete annotations that are shorter than this. 0 = don't delete anything */
    long lDeleteAnnotationIfBelow = 0;

    private MLProperties mlp;

    /**
     * Default constructor
     */
    public StandardActions() {
        // nothing, all is done in initAction
    }

    @Override
    public void initAction(final int iEntry, final MLProperties prop) throws Exception {
        this.mlp = prop;

        final String sType = prop.gets("action." + iEntry + ".type").trim().toLowerCase();

        if (sType.equals("email") || sType.equals("mail")) {
            this.iType = 1;
        } else if (sType.equals("command")) {
            this.iType = 0;
        } else if (sType.equals("log")) {
            this.iType = 2;
        } else if (sType.equals("yahoo")) {
            this.iType = 3;
        } else if (sType.equals("jabber")) {
            this.iType = 4;
        } else if (sType.equals("annotation")) {
            this.iType = 5;
        } else {
            throw new Exception("action." + iEntry
                    + ".type should have one of the [email,command,log,yahoo,jabber,annotation] values");
        }

        if (this.iType == 1) {
            this.sFrom = prop.gets("action." + iEntry + ".from", "", false);
            this.sTo = prop.gets("action." + iEntry + ".to", "", false);
            this.sSubject = prop.gets("action." + iEntry + ".subject", "", false);
            this.sBody = prop.gets("action." + iEntry + ".body", "", false);

            if ((this.sFrom.length() == 0) || (this.sTo.length() == 0) || (this.sSubject.length() == 0)
                    || (this.sBody.length() == 0)) {
                throw new Exception("you should also define action." + iEntry + ".[from,to,subject,body]");
            }

            this.bProxyMail = prop.getb("action." + iEntry + ".proxy_mail", false);
        }

        if (this.iType == 0) {
            this.sCommand = prop.gets("action." + iEntry + ".execute", "", false);

            if (this.sCommand.length() == 0) {
                throw new Exception("you should also define action." + iEntry + ".[execute]");
            }

            this.lTimeout = prop.getl("action." + iEntry + ".timeout", 2000);
        }

        if (this.iType == 2) {
            this.sLogFile = prop.gets("action." + iEntry + ".file", "", false);
            this.sLine = prop.gets("action." + iEntry + ".message", "", false);

            if ((this.sLogFile.length() == 0) || (this.sLine.length() == 0)) {
                throw new Exception("you should also define action." + iEntry + ".[file,message]");
            }
        }

        if ((this.iType == 3) || (this.iType == 4)) {
            this.sLine = prop.gets("action." + iEntry + ".message", "", false);
            this.sUser = prop.gets("action." + iEntry + ".username", "", false);
            this.sPassword = prop.gets("action." + iEntry + ".password", "", false);
            this.sAccounts = prop.gets("action." + iEntry + ".accounts", "", false);

            if ((this.sLine.length() == 0) || (this.sUser.length() == 0) || (this.sPassword.length() == 0)
                    || (this.sAccounts.length() == 0)) {
                throw new Exception("you should also define action." + iEntry + ".[username,password,accounts,message]");
            }
        }

        if (this.iType == 4) {
            // jabber / google talk details

            this.bSSL = prop.getb("action." + iEntry + ".ssl", false);
            this.sHost = prop.gets("action." + iEntry + ".host", "", false);
            this.sService = prop.gets("action." + iEntry + ".service", this.sHost, false);
            this.iPort = prop.geti("action." + iEntry + ".port", this.bSSL ? 5223 : 5222);
            this.sResource = prop.gets("action." + iEntry + ".resource", "MLRepository", false);

            if (this.sHost.length() == 0) {
                throw new Exception(
                        "you should also define action."
                                + iEntry
                                + ".[ssl=boolean (default: true), host=string ('google' is special), port=number (default: 5222 or 5223 if SSL), resource=string (default: MLRepository), service=string (default: value of 'host')] for Jabber accounts");
            }
        }

        if (this.iType == 5) {
            // annotation

            this.sAnnotationSeries = prop.gets("action." + iEntry + ".series", "", false);
            this.sAnnotationGroups = prop.gets("action." + iEntry + ".groups", "", false);
            this.sAnnotationText = prop.gets("action." + iEntry + ".short_text", "", false);
            this.sAnnotationMessage = prop.gets("action." + iEntry + ".full_text", "", false);
            this.lDeleteAnnotationIfBelow = prop.getl("action." + iEntry + ".autodelete_if_below", 0) * 1000;
        }

        this.sMessageOK = prop.gets("action." + iEntry + ".report_ok", "", false);
        this.sMessageERR = prop.gets("action." + iEntry + ".report_err", "", false);
        this.sMessageFlipFlop = prop.gets("action." + iEntry + ".report_flip_flop", "", false);

        if ((this.sMessageOK.length() == 0) && (this.sMessageERR.length() == 0)
                && (this.sMessageFlipFlop.length() == 0)) {
            throw new Exception("at least one of the action." + iEntry
                    + ".[report_ok,report_err,report_flip_flop] should have a value explaining the event");
        }
    }

    /**
     * Change the state
     * 
     * @param ss
     */
    @Override
    public void takeAction(final SeriesState ss) {
        String sMessage;

        switch (ss.iState) {
        case ActionUtils.STATE_OK:
            if (this.sMessageOK.length() == 0) {
                return;
            }
            sMessage = this.sMessageOK;
            break;
        case ActionUtils.STATE_ERR:
            if (this.sMessageERR.length() == 0) {
                return;
            }
            sMessage = this.sMessageERR;
            break;
        case ActionUtils.STATE_FLIPFLOP:
            if (this.sMessageFlipFlop.length() == 0) {
                return;
            }
            sMessage = this.sMessageFlipFlop;
            break;
        default:
            logger.log(Level.FINE, "Unknown state : " + ss.iState);
            return;
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "  type = " + this.iType + ", message = " + sMessage);
        }

        if (this.iType == 0) {
            final String sExec = ActionUtils.apply(ss, sMessage, this.sCommand, this.mlp);

            ActionUtils.getExecutor().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        logger.log(Level.FINER, "Executing : '" + sExec + "'");

                        final Process p = MLProcess.exec(new String[] { "/bin/sh", "-c", sExec },
                                StandardActions.this.lTimeout);

                        if (logger.isLoggable(Level.FINEST)) {
                            BufferedReader br = null;

                            try {
                                br = new BufferedReader(new InputStreamReader(p.getErrorStream()));

                                String sErrorLine;

                                while ((sErrorLine = br.readLine()) != null) {
                                    logger.log(Level.FINEST, "Err line : " + sErrorLine);
                                }
                            } catch (Throwable t) {
                                // ignore
                            } finally {
                                if (br != null) {
                                    try {
                                        br.close();
                                    } catch (Exception e) {
                                        // ignore
                                    }
                                }
                            }
                        }

                        p.waitFor();
                    } catch (Throwable e) {
                        logger.log(Level.WARNING, "Exception executing command '" + sExec + "'", e);
                    }
                }
            });
        }

        if (this.iType == 1) {
            sendMail(ss, sMessage, this.mlp, this.sFrom, this.sTo, this.sSubject, this.sBody, this.bProxyMail);
        }

        if (this.iType == 2) {
            final String _sFile = ActionUtils.apply(ss, sMessage, this.sLogFile, this.mlp);
            final String _sLine = ActionUtils.apply(ss, sMessage, this.sLine, this.mlp);

            ActionUtils.getExecutor().submit(new LogWriter(_sLine, _sFile));
        }

        if (this.iType == 3) {
            final String _sUser = ActionUtils.apply(ss, sMessage, this.sUser, this.mlp);
            final String _sPassword = ActionUtils.apply(ss, sMessage, this.sPassword, this.mlp);
            final String _sLine = ActionUtils.apply(ss, sMessage, this.sLine, this.mlp);
            final String _sAccounts = ActionUtils.apply(ss, sMessage, this.sAccounts, this.mlp);

            System.setProperty("ymsg.network.loginTimeout", "30");

            final String[] vs = _sAccounts.split(",");

            ActionUtils.getExecutor().submit(new YMSender(_sLine, _sPassword, vs, _sUser));
        }

        if (this.iType == 4) {
            final String _sUser = ActionUtils.apply(ss, sMessage, this.sUser, this.mlp);
            final String _sPassword = ActionUtils.apply(ss, sMessage, this.sPassword, this.mlp);
            final String _sLine = ActionUtils.apply(ss, sMessage, this.sLine, this.mlp);
            final String _sAccounts = ActionUtils.apply(ss, sMessage, this.sAccounts, this.mlp);

            final String _sHost = ActionUtils.apply(ss, sMessage, this.sHost, this.mlp);
            final String _sService = ActionUtils.apply(ss, sMessage, this.sService, this.mlp);
            final String _sResource = ActionUtils.apply(ss, sMessage, this.sResource, this.mlp);

            final String[] vs = _sAccounts.split(",");

            ActionUtils.getExecutor().submit(
                    new JabberSender(_sHost, vs, _sLine, _sPassword, _sResource, _sService, _sUser));
        }

        if (this.iType == 5) {
            // annotations
            final Set<Integer> sGroups = Annotation.decodeGroups(ActionUtils.apply(ss, sMessage,
                    this.sAnnotationGroups, this.mlp));
            final Set<String> sSeries = Annotation.decode(ActionUtils.apply(ss, sMessage, this.sAnnotationSeries,
                    this.mlp));

            final String sText = ActionUtils.apply(ss, sMessage, this.sAnnotationText, this.mlp);

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Taking annotation action for " + ss.iState + " @ " + ss.getKey() + " : "
                        + sGroups + " / " + sSeries + " / " + sText);
            }

            final Annotation a = new Annotation();

            a.groups.addAll(sGroups);
            a.services.addAll(sSeries);
            a.text = sText;

            if (ss.iState == ActionUtils.STATE_ERR) {
                final String sMesg = ActionUtils.apply(ss, sMessage, this.sAnnotationMessage, this.mlp);

                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "State is error, adding annotation with message = " + sMesg);
                }

                // close all open ones before inserting the new entry
                closeOpenAnnotations(a, this.lDeleteAnnotationIfBelow);

                // finally, insert this new one				
                a.setDescription(sMesg);

                a.from = NTPDate.currentTimeMillis();
                a.to = a.from + (1000L * 60 * 60 * 24 * 365);

                a.updateDatabase();
            } else if (ss.iState == ActionUtils.STATE_OK) {
                // simply close all open ones
                closeOpenAnnotations(a, this.lDeleteAnnotationIfBelow);
            }
        }
    }

    private static final void closeOpenAnnotations(final Annotation similarTo, final long lDeleteAnnotationIfBelow) {
        String q = "SELECT * FROM annotations WHERE a_to>extract(epoch from now())::int AND a_text='"
                + Formatare.mySQLEscape(similarTo.text) + "'";

        if (similarTo.groups.size() > 0) {
            q += " AND a_groups='" + Annotation.encode(similarTo.groups) + "'";
        }

        if (similarTo.services.size() > 0) {
            q += " AND a_services='" + Formatare.mySQLEscape(Annotation.encode(similarTo.services)) + "'";
        }

        q += ";";

        final DB db = new DB();
        
        db.setReadOnly(true);

        db.query(q);

        while (db.moveNext()) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Found old annotation : " + db.geti("a_id"));
            }

            final Annotation a = new Annotation(db);

            a.to = NTPDate.currentTimeMillis();

            if ((lDeleteAnnotationIfBelow > 0) && ((a.to - a.from) < lDeleteAnnotationIfBelow)) {
                a.deleteDatabaseEntry();
            } else {
                a.updateDatabase();
            }
        }
    }

    /**
     * Send a mail as a result of an action
     * 
     * @param ss SeriesState object, used to replace some variables in the fields
     * @param sMessage message with the state
     * @param mlp configuration options
     * @param sFrom sender
     * @param sTo receiver
     * @param sSubject subject
     * @param sBody mail body
     * @param bProxyMail whether or not to send the mail through the proxy
     * @return true if everything was ok, false if there was an error
     */
    public static boolean sendMail(final SeriesState ss, final String sMessage, final MLProperties mlp,
            final String sFrom, final String sTo, final String sSubject, final String sBody, final boolean bProxyMail) {
        lia.util.mail.MailSender ms = null;

        if (bProxyMail) {
            try {
                ms = lia.util.mail.PMSender.getInstance();
            } catch (Throwable t) {
                // ignore
            }
        } else {
            try {
                ms = lia.util.mail.DirectMailSender.getInstance();
            } catch (Throwable t) {
                // ignore
            }
        }

        if (ms == null) {
            logger.log(Level.WARNING, "Cannot load the mail sender class");
        } else {
            final String _sFrom = ActionUtils.apply(ss, sMessage, sFrom, mlp);
            final String _sTo = ActionUtils.apply(ss, sMessage, sTo, mlp);
            final String _sSubject = ActionUtils.apply(ss, sMessage, sSubject, mlp);
            String _sBody = MLProperties.replace(ActionUtils.apply(ss, sMessage, sBody, mlp), "\\n", "\r\n");

            if (_sBody.startsWith("<HTML>")) {
                _sBody = Utils.htmlToText(_sBody.substring(6));
            }

            try {
                final StringTokenizer st = new StringTokenizer(_sTo, ",; \t[](){}<>|\'\"");

                final ArrayList<String> al = new ArrayList<String>(st.countTokens());

                while (st.hasMoreTokens()) {
                    final String s = st.nextToken();

                    if (s.indexOf("@") > 0) {
                        al.add(s);
                    }
                }

                if (al.size() > 0) {
                    final String[] vsTo = new String[al.size()];

                    for (int i = 0; i < al.size(); i++) {
                        vsTo[i] = al.get(i);
                    }

                    final lia.util.mail.MailSender msSender = ms;

                    ActionUtils.getExecutor().submit(new SendMail(vsTo, _sBody, msSender, _sFrom, _sSubject));
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Exception sending mail", e);
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        return "Type: " + this.iType;
    }

}