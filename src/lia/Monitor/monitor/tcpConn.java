/*
 * Created since very beginning :) $Id: tcpConn.java 7419 2013-10-16 12:56:15Z ramiro $
 */

package lia.Monitor.monitor;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.Utils;

/**
 * Wrapper class for a socket between a client and a service. <br>
 * <br>
 * <b>Important:</b> The communication will start after
 * <code>startCommunication()</code> is called, which will <b>start()</b> the
 * reader thread! <br>
 * 
 * @author ramiro
 * @see lia.Monitor.monitor.tcpConnNotifier
 * @see #startCommunication()
 */
public class tcpConn implements Runnable {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(tcpConn.class.getName());

    /** how often to send ML_PING */
    public static final long ML_PING_DELAY = TimeUnit.SECONDS.toMillis(20); // 10s

    public static final long ML_PING_DELAY_NANOS = TimeUnit.SECONDS.toNanos(20);

    /** how long should THE CLIENT wait for results */
    static final int SO_TIMEOUT = (int) ((3 * ML_PING_DELAY) + (10 * 1000L));

    static final long SO_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos((3 * ML_PING_DELAY) + (10 * 1000L));

    /** maximum time to connect with the other endPoint */
    private static final int CONNECT_TIMEOUT = 30 * 1000; // 30s

    /** last time ML_PING was sent */
    final AtomicLong lastMLPingSendTime = new AtomicLong(0L);

    // accounting
    private final AtomicLong recvBytes = new AtomicLong(0);

    private final AtomicLong confRecvBytes = new AtomicLong(0);

    private final AtomicLong sentBytes = new AtomicLong(0);

    private final AtomicLong confSentBytes = new AtomicLong(0);

    private volatile boolean countConfig = false;

    private static final boolean COUNT_CONFIG_SENT;

    public static final String ML_PING_TAG = "ML_PING";

    static final byte[] ML_PING_BYTE_MSG;

    private static final byte[] FIRST_ML_PING_BYTE_MSG;

    public static final monMessage ML_PING_MSG;

    static {

        monMessage m = new monMessage(ML_PING_TAG, new byte[512], null);

        byte[] oToSend = null;
        byte[] foToSend = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(m);
            oos.reset();
            oos.flush();
            foToSend = baos.toByteArray();
            baos.reset();

            m = new monMessage(ML_PING_TAG, null, null);
            oos.writeObject(m);
            oos.reset();
            oos.flush();
            oToSend = baos.toByteArray();
        } catch (Throwable t) {
            // probably nothing will work ... so throw a RuntimeException
            logger.log(Level.WARNING, "Cannot serialize ML_PING msg ", t);
            throw new RuntimeException("Cannot serialize ML_PING msg ");
        }

        boolean bChk = false;
        try {
            bChk = (AppConfig.getProperty("lia.Monitor.monitor.proxy_home") != null);
        } catch (Throwable t) {
            bChk = false;
        }
        COUNT_CONFIG_SENT = bChk;
        logger.log(Level.CONFIG, " [ tcpConn ] COUNT_CONFIG_SENT = " + COUNT_CONFIG_SENT);

        FIRST_ML_PING_BYTE_MSG = foToSend;
        ML_PING_BYTE_MSG = oToSend;
        ML_PING_MSG = m;
    }

    private final Object syncSend = new Object();

    private final Object oosSyncSend = new Object();

    /** the connection owner. the owner sends and receives messages */
    protected final tcpConnNotifier connNotifier;

    private final AtomicBoolean notified = new AtomicBoolean(false);

    private final AtomicBoolean started = new AtomicBoolean(false);

    /** is connection ok? */
    private final AtomicBoolean connected = new AtomicBoolean(false);

    BufferedOutputStream out = null;

    ByteArrayOutputStream baos;

    ObjectOutputStream oos;

    protected volatile Socket socket;

    // it is already synched
    private volatile boolean isSending;

    final Thread runningThread;

    private tcpConn(final tcpConnNotifier connNotifier) {
        runningThread = new Thread(this);

        isSending = false;

        if (connNotifier == null) {
            throw new NullPointerException("Null TCP Connection notifier");
        }

        this.connNotifier = connNotifier;
    }

    public static tcpConn newConnection(tcpConnNotifier connNotifier, InetAddress address, int ser_port)
            throws Exception {
        return new tcpConn(connNotifier, address, ser_port);
    }

    private tcpConn(tcpConnNotifier connNotifier, InetAddress address, int ser_port) throws Exception {
        this(connNotifier);

        runningThread.setName("( ML ) tcpConn for " + address + ":" + ser_port);

        initLocalStreams();

        connect(address, ser_port);

        connected.set(true);

        runningThread.setName(runningThread.getName() + ":-" + socket.getLocalPort());

        init(true); // from the client
    }

    /**
     * Causes this connection to start reading incoming messages and notfy
     * incoming messages for the <code>tcpConnNotifier</code>.
     * 
     * @throws IllegalThreadStateException
     *             - if is called multiple times.
     * @see tcpConnNotifier
     */
    public final void startCommunication() {
        if (started.compareAndSet(false, true)) {
            try {
                runningThread.setDaemon(true);
            } catch (Throwable t) {
                logger.log(Level.WARNING, runningThread.getName() + " Cannot setDaemon", t);
            }
            runningThread.start();
        } else {
            throw new IllegalThreadStateException(" Communication was already started ");
        }
    }

    public static tcpConn newConnection(tcpConnNotifier connNotifier, Socket socket) throws Exception {
        return new tcpConn(connNotifier, socket);
    }

    private tcpConn(tcpConnNotifier connNotifier, Socket socket) throws Exception {
        this(connNotifier);

        if (socket == null) {
            logger.log(Level.SEVERE, " socket == null  ");
            throw new NullPointerException(" socket == null  ");
        }
        this.socket = socket;

        initLocalStreams();

        connected.set(true);

        runningThread.setName("( ML ) tcpConn for " + socket.getInetAddress() + ":" + socket.getPort() + ":-"
                + socket.getLocalPort());

        init(false); // from farm
    }

    public final long getRecvBytes() {
        return recvBytes.get();
    } // getSentBytes

    public final long getConfRecvBytes() {
        return confRecvBytes.get();
    } // getConfSentBytes

    public final long getSentBytes() {
        return sentBytes.get();
    } // getSentBytes

    public final long getConfSentBytes() {
        return confSentBytes.get();
    } // getConfSentBytes

    public final InetAddress getEndPointAddress() {

        if (socket != null) {
            return socket.getInetAddress();
        }

        return null;
    }

    public final int getEndPointPort() {
        if (socket != null) {
            return socket.getPort();
        }
        return -1;
    }

    public final int getLocalPort() {
        if (socket != null) {
            return socket.getLocalPort();
        }
        return -1;
    }

    public void close_connection() {
        connected.set(false);
        if (notified.compareAndSet(false, true)) {
            logger.log(Level.INFO, runningThread.getName() + " connection closed");
            try {
                if (started.compareAndSet(false, true)) {
                    startCommunication();
                }
            } catch (Throwable ignore) {
                // not interested
            }

            cleanup();
            try {
                if (connNotifier != null) {
                    connNotifier.notifyConnectionClosed();
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, runningThread.getName()
                        + " got exception notifying the connNotifier that connection was closed", t);
            }
        }
    }

    public boolean isSending() {
        return isSending;
    }

    private void cleanup() {
        try {
            tcpConnWatchdog.remove(this);
        } catch (Throwable t) {
            logger.log(Level.WARNING, runningThread.getName() + " exc removing from watchdog", t);
        }

        try {
            Utils.closeIgnoringException(out);

            if (socket != null) {
                try {
                    socket.close();
                } catch (Throwable ignore) {
                    // not interested
                }
                socket = null;
            }
            Utils.closeIgnoringException(baos);
            Utils.closeIgnoringException(oos);

            synchronized (syncSend) {
                syncSend.notifyAll();
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, runningThread.getName() + " [ HANDLED ] IOException while cleanup()", t);
            }
        }
    }

    boolean checkSendTimeout() {
        synchronized (syncSend) {
            final long now = Utils.nanoNow();
            final long deadLine = lastMLPingSendTime.get() + SO_TIMEOUT_NANOS;

            if (isSending && (now > deadLine)) {
                logger.log(
                        Level.WARNING,
                        runningThread.getName()
                                + " cannot send msgs. Remote reader down or firewall problem. Last message delivery started at least "
                                + TimeUnit.NANOSECONDS.toSeconds(SO_TIMEOUT_NANOS)
                                + " seconds. The connection will be closed!");
                try {
                    close_connection();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " [ TCP_CONN ] [ HANDLED ] Got ex closing the conn", t);
                }
                syncSend.notifyAll();
                return false;
            }
        }

        return true;
    }

    /**
     * PLEASE USE IT WHEN YOU KNOW WHAT YOU ARE DOING!!!
     * 
     * @param buffer
     *            - Must contain a serialized Object
     */
    public void directSend(byte[] buffer) {

        final int buffLen = buffer.length;

        synchronized (syncSend) {
            // CLOSE_WAIT Problem ...
            while (isSending && connected.get()) {
                try {
                    syncSend.wait(ML_PING_DELAY);
                } catch (Throwable t) {
                    // problems in paradise?
                }

                if (!connected.get()) {
                    syncSend.notifyAll();
                    return;
                }

                checkSendTimeout();
            }// while

            if (!connected.get()) {
                isSending = false;
                syncSend.notifyAll();
                return;
            }

            isSending = true;
        }// end sync

        try {
            out.write(buffer);
            out.flush();
            lastMLPingSendTime.set(Utils.nanoNow());
        } catch (IOException e) {
            logger.log(Level.WARNING, runningThread.getName() + ": IOException sending byte array. Err = ", e);
            close_connection();
        } catch (Throwable t) {
            logger.log(Level.WARNING, runningThread.getName() + ": General Exception sending byte array. Err =", t);
            close_connection();
        }

        synchronized (syncSend) {
            isSending = false;
            // wake up a single thread
            syncSend.notify();
        }// synch

        // Impossible to have an overflow ... it's... 2179 years at 10 gbps ...
        // it's since the the holly monty python!!
        // long diff = Long.MAX_VALUE - sentBytes.get();
        // if (diff > buffLen) {
        sentBytes.addAndGet(buffLen);
        // } else {// overflow ... who knows ....
        // sentBytes.set(buffLen - diff);
        // }// if - else

        if (countConfig) {
            // same explnation ... it's ...
            // diff = Long.MAX_VALUE - confSentBytes.get();
            // if (diff > buffLen) {
            confSentBytes.addAndGet(buffLen);
            // } else {// overflow ... who knows ....
            // confSentBytes.set(buffLen - diff);
            // }// if - else
        } // if
    }

    public void sendMsg(Object o) {
        if ((o == null) || !connected.get()) {
            return;
        }
        synchronized (oosSyncSend) {
            try {
                if (oos != null) {// Just to avoid NPE
                    oos.writeObject(o);
                    oos.reset();
                    oos.flush();
                    byte[] oToSend = baos.toByteArray();
                    baos.reset();
                    if (COUNT_CONFIG_SENT && (o instanceof MonMessageClientsProxy)
                            && (((MonMessageClientsProxy) o).tag != null)
                            && ((MonMessageClientsProxy) o).tag.startsWith(monMessage.ML_CONFIG_TAG)) {
                        countConfig = true;
                    } // if
                    directSend(oToSend);

                    countConfig = false;
                } else {
                    close_connection();
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Got exception sending msg", t);
                close_connection();
            }
        }
    }

    @Override
    public void run() {
        ObjectInputStream in = null;
        ObjectOutputStream in_oos = null;
        ByteArrayOutputStream in_baos = null;

        final String localThreadName = runningThread.getName();

        try {
            if (connected.get()) {

                in_baos = new ByteArrayOutputStream();
                in_oos = new ObjectOutputStream(in_baos);
                in_oos.writeObject(ML_PING_MSG);
                in_oos.reset();
                in_oos.flush();
                in_baos.reset();

                // this is called, although should be after the one in
                // constructor
                // force a ML_PING
                lastMLPingSendTime.set(Utils.nanoNow());
                if (connected.get()) {
                    tcpConnWatchdog.addToWatch(this);
                }

                final boolean bByteCounter = AppConfig.getb("lia.Monitor.tcpConn.ByteCounter", false);
                in = new ObjectInputStream(socket.getInputStream());

                while (connected.get()) {
                    try {
                        final Object o = in.readObject();

                        if (bByteCounter) {
                            // try to get its dimmension and increment in
                            // counters
                            boolean in_countConfig = false;
                            in_oos.writeObject(o);
                            in_oos.reset();
                            in_oos.flush();
                            final int in_size = in_baos.size();
                            in_baos.reset();
                            if ((o instanceof MonMessageClientsProxy) && (((MonMessageClientsProxy) o).tag != null)
                                    && ((MonMessageClientsProxy) o).tag.startsWith(monMessage.ML_CONFIG_TAG)) {
                                in_countConfig = true;
                            } // if

                            // Impossible to have an overflow ... it's... 2179
                            // years at 10 gbps ... it's since the the
                            // holly monty python!!
                            // long diff = Long.MAX_VALUE - sentBytes.get();
                            // if (diff > in_size) {
                            recvBytes.addAndGet(in_size);
                            // } else {// overflow ... who knows ....
                            // recvBytes.set(in_size - diff);
                            // }// if - else

                            if (in_countConfig) {
                                // Impossible to have an overflow ... it's...
                                // 2179 years at 10 gbps ... it's since the
                                // the holly monty python!!
                                // diff = Long.MAX_VALUE - confSentBytes.get();
                                // if (diff > in_size) {
                                confRecvBytes.addAndGet(in_size);
                                // } else {// overflow ... who knows ....
                                // confRecvBytes.set(in_size - diff);
                                // }// if - else
                            } // if
                        }

                        if (o != null) {
                            if (!(o instanceof monMessage)) {
                                logger.log(Level.WARNING, localThreadName + ": Received an unknownObject" + o, o);
                                continue;
                            }
                            final monMessage m = (monMessage) o;

                            if ((m.tag != null) && m.tag.equals(ML_PING_TAG)) {
                                continue;
                            }

                            connNotifier.notifyMessage(o);
                        } else {
                            logger.log(Level.WARNING, localThreadName + ": Received a null message: " + o, o);
                        }
                    } catch (ClassNotFoundException es) {
                        logger.log(Level.WARNING, localThreadName
                                + " class not found exception. will ignore it. Cause: ", es);
                    } catch (IOException ioe) {
                        logger.log(Level.WARNING, localThreadName + " IO Exception. will stop! Cause: ", ioe);
                        connected.set(false);
                        break;
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, localThreadName
                                + " Got general exception while reading ... will stop! Cause: ", t);
                        connected.set(false);
                        break;
                    }
                }// while
            }// if
        } catch (Throwable genExc) {
            logger.log(Level.WARNING, " Got gen exc ", genExc);
            connected.set(false);
        } finally {
            Utils.closeIgnoringException(in_baos);
            Utils.closeIgnoringException(in_oos);
            Utils.closeIgnoringException(in);
            close_connection();
        }
    }

    /**
     * Are we connected?
     */
    public boolean isConnected() {
        return connected.get();
    }

    private void connect(InetAddress address, int ser_port) throws Exception {
        try {
            socket = new Socket();
            initSParams();
            socket.connect(new InetSocketAddress(address, ser_port), CONNECT_TIMEOUT);
        } catch (Throwable t) {
            logger.log(Level.SEVERE, " failed to open socket with the server: " + address + ":" + ser_port, t);
            close_connection();
            throw new Exception(t);
        }

    }

    private void initLocalStreams() throws Exception {
        baos = new ByteArrayOutputStream();
        oos = new ObjectOutputStream(baos);
        oos.writeObject(ML_PING_MSG);
        oos.reset();
        oos.flush();
        baos.reset();
    }

    private void initSParams() {
        try {
            socket.setTcpNoDelay(true);
        } catch (Throwable t) {
            logger.log(Level.WARNING, runningThread.getName() + " failed setTcpNoDelay: ", t);
        }

        try {
            socket.setSoTimeout(SO_TIMEOUT);
        } catch (Throwable t) {
            logger.log(Level.WARNING, runningThread.getName() + " failed setSoTimeout: ", t);
        }

        try {
            socket.setSoLinger(true, 10);
        } catch (Throwable t) {
            logger.log(Level.WARNING, runningThread.getName() + " failed setSoLinger: ", t);
        }
    }

    @Override
    public String toString() {
        if (socket == null) {
            return "(ML) tcpConn ... socket not connected yet";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("tcpConn for: Local :- [ ").append(socket.getLocalAddress()).append(":")
                .append(socket.getLocalPort());
        sb.append(" ] <-> Remote: [ ").append(socket.getInetAddress()).append(":").append(socket.getPort());
        sb.append(" ]");

        return sb.toString();
    }

    private void init(boolean alreadyInited) throws Exception {
        if (!alreadyInited) {
            initSParams();
        }

        try {
            out = new BufferedOutputStream(socket.getOutputStream());
            out.write(FIRST_ML_PING_BYTE_MSG);
            out.flush();
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, runningThread.getName() + " sent initial ML_PING message");
            }
        } catch (Throwable t) {
            logger.log(Level.SEVERE, runningThread.getName() + " failed to init the streams: ", t);
            close_connection();
            throw new Exception(t);
        }
    }
}
