/*
 * $Id: TL1Telnet.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.util.telnet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;

/**
 * It should be the super class of all TL1 telnet instances
 * 
 * @author ramiro
 */
public abstract class TL1Telnet {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(TL1Telnet.class.getName());

    public static final String PING_CONN_CTAG = "pcon";
    public static final String AUTH_CONN_CTAG = "acon";
    public static final String TL1_COMPLD_TAG = "COMPLD";
    public static final String TL1_FINISH_CMD = ";";
    public static final String TL1_RESPONSE_CMD_CODE = "M";
    public static final String PING_TL1_CMD = "rtrv-hdr:::" + PING_CONN_CTAG + ";\n";

    protected static volatile long MIN_CONNECT_DELAY_NANOS;
    protected static final long MIN_CONNECT_DELAY_DEFAULT_NANOS = TimeUnit.SECONDS.toNanos(60);

    // do a 'PING' if no command was issued for 60 seconds
    // otherwise the connection will be lost
    private static final long MAX_CONN_PING_DELAY_DEFAULT_NANOS = TimeUnit.SECONDS.toNanos(60);

    // connection parameters
    private final String hostName;
    private final int port;
    private final String username;
    private final String passwd;

    // the one and only socket ...
    private Socket sock;

    private final Date fDate;

    private final TL1TelnetThread pingDaemon;

    private long lastConnectNanos; // 10 s

    private static final AtomicInteger SOCK_TIMEOUT = new AtomicInteger(90);

    // 20s
    private static final int CONNECT_TIMEOUT = 20 * 1000;

    // Wrapper streams for
    // the socket
    protected BufferedWriter cmdWriter;

    protected BufferedReader cmdReader;

    private long lastCmdSentNanos;

    // keeps a buffer for the last executed TL1 command
    private final ArrayList<String> lines;

    /**
     * Is there a socket connected and authenticated with the switch ?
     */
    private final AtomicBoolean connected;

    /**
     * Used to notify the daemon thread when it needs to stop
     */
    private final AtomicBoolean alive;

    private String localConnName; // allow multiple calls to cleanup()

    protected AtomicBoolean cleanupPerformed;

    /**
     * used to serialize the requests to the NE
     */
    private final Object lock = new Object();

    static {

        reloadConf();

        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                reloadConf();
            }
        });
    }

    /**
     * This class is used to "ping" the connection if it is idle and to
     * reconnect in case the connection is lost
     */
    private class TL1TelnetThread extends Thread {

        @Override
        public void run() {

            while (alive.get()) {
                try {

                    if (!connected.get()) {
                        try {
                            connect();
                            logger.log(Level.INFO, " [ TL1Telnet ] Connected to [ " + localConnName + " ]");
                            pingDaemon.setName("( ML ) TL1 Telnet Ping Daemon [ " + localConnName + " ] ");
                        } catch (Throwable t) {
                            cleanup();
                            logger.log(Level.WARNING, " [ TL1Telnet ] Got Exception while connect()-ing to the switch",
                                    t);
                        }
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (Throwable t1) {
                    }

                    final long nanoNow = System.nanoTime();
                    if (nanoNow > (lastCmdSentNanos + MAX_CONN_PING_DELAY_DEFAULT_NANOS)) {
                        synchronized (lock) {
                            if (nanoNow > (lastCmdSentNanos + MAX_CONN_PING_DELAY_DEFAULT_NANOS)) {
                                try {
                                    pingConn();
                                } catch (Throwable t) {
                                    logger.log(Level.WARNING,
                                            " [ TL1Telnet ] Got Exception while ping()-ing the connection... ", t);
                                    cleanup();
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "TL1Telnet got Exc main loop", t);
                    cleanup();
                    try {
                        Thread.sleep(1000);
                    } catch (Throwable t1) {
                    }
                }
            }

            // Exit main Thread
            logger.log(Level.WARNING, " [ TL1TelnetDaemon ] for [" + localConnName
                    + "] will exit now ... active == false");
            cleanup();
        }
    }

    TL1Telnet(String username, String passwd, String hostName, int port, boolean failOnFirstAttempt) throws Exception {
        fDate = new Date();
        connected = new AtomicBoolean(false);

        // start with a buff of 64 lines
        lines = new ArrayList<String>(64);

        alive = new AtomicBoolean(false);
        cleanupPerformed = new AtomicBoolean(false);
        reloadConf();

        if ((username == null) || (passwd == null) || (hostName == null)) {
            throw new OSTelnetException("[ TL1Telnet ] Username, Passwd and Hostname MUST BE != null",
                    OSTelnetException.NULL_AUTH_PARAMS);
        }

        this.username = username;
        this.passwd = passwd;
        this.hostName = hostName;
        this.port = port;

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ TL1Telnet ] [ " + hostName + ":" + port + " / " + username + ":" + passwd
                    + " ] ");
        }

        alive.set(true);

        if (failOnFirstAttempt) {
            connect();
        } else {
            try {
                connect();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Failed to connect. Will retry later ...");
            }
        }

        pingDaemon = new TL1TelnetThread();
        pingDaemon.setDaemon(true);
        pingDaemon.start();
        logger.log(Level.INFO, " [ TL1Telnet ] Connected to [ " + localConnName + " ]");
        pingDaemon.setName("( ML ) TL1 Telnet Ping Daemon [ " + localConnName + " ] ");

    }

    private void connect() throws Exception {

        if (connected.get()) {
            return;
        }

        synchronized (lock) {
            if (connected.get()) {
                return;
            }

            final long nanoNow = System.nanoTime();
            localConnName = hostName + ":" + port;
            final long nextConnect = lastConnectNanos + MIN_CONNECT_DELAY_NANOS;

            if (nextConnect > nanoNow) {
                fDate.setTime(System.currentTimeMillis() + TimeUnit.NANOSECONDS.toMillis(nextConnect - nanoNow));
                throw new OSTelnetException(" [ TL1Telnet ] Cannot connect sooner then " + fDate,
                        OSTelnetException.CANNOT_CONNECT_SOONER);
            }

            cleanupPerformed.set(false);
            lastConnectNanos = nanoNow;

            final Socket sock = new Socket();
            sock.connect(new InetSocketAddress(hostName, port), CONNECT_TIMEOUT);

            try {// can ignore this
                sock.setTcpNoDelay(true);
                sock.setSoLinger(true, 1);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Got exception setting TCP_NO_DELAY and SO_LINGER", t);
            }

            // WE MUST HAVE SOCK_TIMEOUT ... so throw Exception if not
            sock.setSoTimeout(SOCK_TIMEOUT.get() * 1000);

            cmdReader = new BufferedReader(new InputStreamReader(sock.getInputStream()), 512 * 1024);
            cmdWriter = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream()));
            doAuth(username, passwd);
            localConnName += ":-" + sock.getLocalPort();
            this.sock = sock;
            connected.set(true);
            connected();
        }

    }

    protected void doAuth(String userName, String passwd) throws OSTelnetException, IOException {
        internalExec(true, "act-user::" + userName + ":" + AUTH_CONN_CTAG + "::" + passwd + TL1_FINISH_CMD,
                AUTH_CONN_CTAG, TL1_COMPLD_TAG);
    }

    private void cleanup() {

        // this function should not throw an Exception ...
        try {
            synchronized (lock) {
                if (cleanupPerformed.compareAndSet(false, true)) {

                    connected.set(false);
                    try {
                        if (cmdWriter != null) {
                            cmdWriter.close();
                        }
                    } catch (Throwable t) {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, " [ TL1Telnet ] Got exception closing cmdWriter", t);
                        }
                    } finally {
                        cmdWriter = null;
                    }

                    try {
                        if (cmdReader != null) {
                            cmdReader.close();
                        }
                    } catch (Throwable t) {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, " [ TL1Telnet ] Got exception closing cmdReader", t);
                        }
                    } finally {
                        cmdReader = null;
                    }

                    try {
                        if (sock != null) {
                            sock.close();
                        }
                    } catch (Throwable t) {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, " [ TL1Telnet ] Got exception closing socket", t);
                        }
                    } finally {
                        sock = null;
                    }

                    logger.log(Level.INFO, " [ TL1Telnet ] Connection [ " + localConnName + " ] closed");

                    localConnName = hostName + ":" + port;
                    pingDaemon.setName("( ML ) TL1 Telnet Ping Daemon [ " + localConnName + " ] ");
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ TL1Telnet ] Got exception closing the connection [ " + localConnName
                    + " ] to the switch", t);
        }
    }

    private static void reloadConf() {
        long lMIN_CONNECT_DELAY = MIN_CONNECT_DELAY_DEFAULT_NANOS;
        int sockTimeout = 90;

        try {
            lMIN_CONNECT_DELAY = TimeUnit.SECONDS.toNanos(AppConfig.getl("lia.util.telnet.TL1Telnet.MIN_CONNECT_DELAY",
                    TimeUnit.NANOSECONDS.toSeconds(MIN_CONNECT_DELAY_DEFAULT_NANOS)));
        } catch (Throwable t) {
            lMIN_CONNECT_DELAY = MIN_CONNECT_DELAY_DEFAULT_NANOS;
        }

        MIN_CONNECT_DELAY_NANOS = lMIN_CONNECT_DELAY;

        try {
            sockTimeout = AppConfig.geti("lia.util.telnet.TL1Telnet.SO_TIMEOUT", 90);
        } catch (Throwable t) {
            sockTimeout = 90;
        }

        SOCK_TIMEOUT.set((sockTimeout < 0) ? 90 : sockTimeout);

        logger.log(Level.INFO,
                "\n[ TL1Telnet ] MIN_CONNECT_DELAY = " + TimeUnit.NANOSECONDS.toSeconds(MIN_CONNECT_DELAY_NANOS)
                        + " seconds; SO_TIMEOUT = " + SOCK_TIMEOUT.get() + " seconds\n");

        final String sLevel = AppConfig.getProperty("lia.util.telnet.TL1Telnet.level");
        Level loggingLevel = null;
        if (sLevel != null) {
            try {
                loggingLevel = Level.parse(sLevel);
            } catch (Throwable t) {
                loggingLevel = null;
            }

            logger.setLevel(loggingLevel);
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [ TL1Telnet ] reloadedConf. Logging level: " + loggingLevel);
        }

    }

    /**
     * ALWAYS called from synchronized(lock), more specific from internalExec
     * Reads from the stream and fills internal buffer Checks for TL1 syntax
     * (e.g. TL1 responses must always finish with ';' It also checks for ctag
     * 
     * @param ctag
     *            - TL1 tag that is expected
     * @param expectedTL1Code
     *            - checks if the line containing ctag has the expected TL1
     *            code; if this parameter is null the returned TL1 code will be
     *            ignored
     * @returns - true if expected ctag was found in the response; false
     *          otherwise
     * @throws OSTelnetException
     *             with INVALID_TL1_RESPONSE_CODE if checkForTL1ReturnCode !=
     *             returned code
     * @throws IOException
     *             if any I/O errors occur during reading
     */
    private boolean readTL1Respose(final String ctag, final String expectedTL1Code) throws OSTelnetException,
            IOException {

        lines.clear();
        boolean retb = false;
        boolean checked = (expectedTL1Code == null);

        String line = null;
        String rLine = null;

        StringBuilder sbLogFinest = new StringBuilder();

        try {
            while (true) {

                line = cmdReader.readLine();
                if (line == null) {
                    // should not be null even for a wrong command!!
                    // ALL COMMANDS END WITH ';'
                    // ... but let's protect ML
                    cleanup();
                    throw new OSTelnetException(
                            "Expected TL1 end tag ';' ... null response ( maybe the stream was closed )",
                            OSTelnetException.NULL_REMOTE_RESPONSE);
                }

                if (logger.isLoggable(Level.FINEST)) {
                    sbLogFinest.append(line).append("\n");
                }

                if (!retb && line.startsWith(TL1_RESPONSE_CMD_CODE) && (line.indexOf(ctag) != -1)) {
                    rLine = line;
                    retb = true;
                    if (!checked && (line.indexOf(expectedTL1Code) != -1)) {
                        checked = true;
                    }
                }

                lines.add(line);

                if (line.startsWith(TL1_FINISH_CMD) && (line.trim().length() == 1)) {
                    break;
                }

            }// while
        } finally {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, " readTL1Response for ctag: " + ctag + " expTL1Code: " + expectedTL1Code
                        + " got from NE:\n" + sbLogFinest.toString());
            }
        }

        if (retb && !checked) {
            throw new OSTelnetException("Expected TL1 code [" + expectedTL1Code + "] received: " + rLine,
                    OSTelnetException.INVALID_TL1_RESPONSE_CODE, getInternalBuffer());
        }

        return retb;
    }

    /**
     * @return - The current lines in the buffer
     */
    private String getInternalBuffer() {
        int cLen = lines.size();
        StringBuilder sb = new StringBuilder(cLen * 80);
        for (int i = 0; i < cLen; i++) {
            sb.append(lines.get(i)).append("\n");
        }
        return sb.toString();
    }

    /**
     * @param cmd
     *            - TL1 Command
     * @param ctag
     *            - Correlection Tag
     * @throws Exception
     * @see #execCmd(String, String, String)
     */
    public final void execCmd(final String cmd, final String ctag) throws Exception {
        internalExec(false, cmd, ctag, TL1_COMPLD_TAG);
    }

    /**
     * This method can be used to send TL1 commands for which the <b>entire<b>
     * TL1 response is not important (e.g. making/deleting cross connects). It
     * can check for TL1 returning codes like COMPLD.
     * 
     * @param cmd
     *            - TL1 Command
     * @param ctag
     *            - Expected Correlation Tag ( mandatory )
     * @param expectedTL1Code
     *            - Expected TL1 Code returned by this command; can be null, in
     *            which case the TL1 code returned from the switch will not be
     *            checked
     * @throws OSTelnetException
     * @throws IOException
     * @see #execCmdAndGet(String, String, String)
     */
    public final void execCmd(final String cmd, final String ctag, final String expectedTL1Code)
            throws OSTelnetException, IOException {
        internalExec(false, cmd, ctag, expectedTL1Code);
    }

    /**
     * This is used only by this classes methods !
     * 
     * @param skipConnCheck
     * @param cmd
     * @param ctag
     * @param expectedTL1Code
     * @throws OSTelnetException
     * @throws IOException
     */
    protected void internalExec(boolean skipConnCheck, final String cmd, final String ctag, final String expectedTL1Code)
            throws OSTelnetException, IOException {
        long sTime = System.nanoTime();

        if ((cmd == null) || (ctag == null)) {
            throw new OSTelnetException("cmd and ctag must be != null", OSTelnetException.NULL_CMD_PARAMS);
        }

        synchronized (lock) {

            if (!skipConnCheck && !connected.get()) {
                throw new OSTelnetException("No connection to the switch (yet)", OSTelnetException.NOT_CONNECTED);
            }

            try {
                cmdWriter.write(cmd.toCharArray());
                cmdWriter.flush();

                lastCmdSentNanos = System.nanoTime();
                while (!readTL1Respose(ctag, expectedTL1Code)) {
                    // Autonomous messages will be logged
                    StringBuilder sblog = new StringBuilder(8192);
                    sblog.append("\n\n [ TL1Telnet ] [ ParseError ] Sent cmd [").append(cmd);
                    sblog.append("] received: \n").append(getInternalBuffer()).append("\n\n");
                    logger.log(Level.WARNING, sblog.toString());
                }

            } catch (OSTelnetException ote) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.WARNING, "[ TL1Telnet ] Got OSTelnetException in doCmd() for cmd [" + cmd + "]",
                            ote);
                }
                throw ote;
            } catch (IOException ioe) {
                // IO Exception ... nothing to do with normal OSTelnetExceptions
                cleanup();
                throw ioe;
            } catch (Throwable t) {
                // general exception ... should not happen
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.WARNING, "[ TL1Telnet ] Got generic exception in doCmd() for cmd [" + cmd + "]", t);
                }
                cleanup();
                throw new OSTelnetException(t);
            } finally {
                if (logger.isLoggable(Level.FINER)) {
                    final StringBuilder sb = new StringBuilder(8192);
                    for (int i = 0; i < lines.size(); i++) {
                        sb.append(lines.get(i)).append("\n");
                    }
                    logger.log(Level.FINER, "\n [ TL1Telnet ] [ internalExec ] Executing CMD [" + cmd + "] dt = "
                            + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sTime) + " ] ms for " + localConnName
                            + "\nGot from switch:\n*******\n " + sb.toString() + "\n");
                }
            }
        }// end sync
    }

    /**
     * @see #execCmdAndGet(String, String, String)
     */
    public final String[] execCmdAndGet(final String cmd, final String ctag) throws OSTelnetException, IOException {
        return execCmdAndGet(cmd, ctag, TL1_COMPLD_TAG);
    }

    /**
     * The same as <code/>execCmd(String, String, String)</code> but the output
     * is returned
     * 
     * @param cmd
     *            - TL1 Command to be executed
     * @param ctag
     *            - TL1 Correlation Tag
     * @param expectedTL1Code
     *            - can be null. If not null will check if it is the same with
     *            the returned TL1 code for this command
     * @return An array containing the lines of the TL1 response from the switch
     * @throws OSTelnetException
     * @throws IOException
     * @see #execCmd(String, String, String)
     */
    public final String[] execCmdAndGet(final String cmd, final String ctag, final String expectedTL1Code)
            throws OSTelnetException, IOException {
        synchronized (lock) {
            execCmd(cmd, ctag, expectedTL1Code);
            return lines.toArray(new String[lines.size()]);
        }
    }

    /**
     * @param cmd
     * @param ctag
     * @return
     * @throws Exception
     * @depricated - use execCmdAndGet ( faster and reduced memory consumption )
     * @see #execCmdAndGet(String, String)
     */
    public final StringBuilder doCmd(String cmd, String ctag) throws Exception {
        synchronized (lock) {
            execCmd(cmd, ctag);
            return new StringBuilder(getInternalBuffer());
        }
    }

    public final void stopIt() {
        alive.set(false);
    }

    public final boolean isActive() {
        return alive.get();
    }

    public final boolean isConnected() {
        return connected.get();
    }

    public void pingConn() throws Exception {
        execCmd(PING_TL1_CMD, PING_CONN_CTAG);
    }

    public abstract void connected();
}
