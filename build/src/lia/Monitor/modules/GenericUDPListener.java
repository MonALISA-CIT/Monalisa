/*
 * $Id: GenericUDPListener.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.modules;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.util.net.FloodControl;

/**
 * 
 * @author ramiro
 * @author Catalin Cirstoiu
 */
public class GenericUDPListener extends Thread {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(GenericUDPListener.class.getName());

    /**
     * Listening socket
     */
    protected DatagramSocket socket = null;

    /**
     * True until stopped
     * @see #stopIt()
     */
    protected final AtomicBoolean hasToRun = new AtomicBoolean(false);
    private final byte[] buf = new byte[65535];
    private final GenericUDPNotifier notif;
    private final UDPAccessConf accessConf;
    private FloodControl floodController;
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final int port;
    private final InetAddress localAddress;

    /**
     * @param port
     * @param notif
     */
    public GenericUDPListener(final int port, final GenericUDPNotifier notif) {
        this(null, port, notif, null);
    }

    /**
     * @param port
     * @param notif
     * @param conf
     */
    public GenericUDPListener(final int port, final GenericUDPNotifier notif, final UDPAccessConf conf) {
        this(null, port, notif, conf);
    }

    /**
     * @param laddr
     * @param port
     * @param notif
     * @param conf
     */
    public GenericUDPListener(final InetAddress laddr, final int port, final GenericUDPNotifier notif,
            final UDPAccessConf conf) {
        super(" ( ML ) Generic UDP Listener on port [ " + port + " ]");
        try {

            this.localAddress = laddr;
            this.port = port;

            hasToRun.set(true);
            this.floodController = new FloodControl();
        } finally {
            this.accessConf = conf;
            this.notif = notif;
            setDaemon(true);
            start();
        }
    }

    private void bindSocket() throws IOException {

        if (localAddress == null) {
            socket = new DatagramSocket(port);
        } else {
            socket = new DatagramSocket(port, localAddress);
        }

        if (socket == null) {
            logger.log(Level.SEVERE, " GenericUDP socket == null !! ");
            throw new IOException(" Unable to bind the UDP socket on port " + port
                    + " ]. GenericUDP socket == null !! ");
        }

        // setting the SO_RCVBUF size
        int origBS = socket.getReceiveBufferSize();
        int desiredBS = AppConfig.geti("lia.Monitor.modules.GenericUDPListener.SO_RCVBUF_SIZE", 0);
        if (desiredBS > 0) {
            socket.setReceiveBufferSize(desiredBS);
        }
        int newBS = socket.getReceiveBufferSize();
        logger.log(Level.INFO, "Generic UDP Listener SO_RCVBUF size: " + origBS
                + (desiredBS > 0 ? ", desired: " + desiredBS + ", set to: " + newBS + "." : "."));
    }

    /**
     * @param rate
     */
    public void setMaxMsgRate(final int rate) {
        floodController.setMaxMsgRate(rate);
    }

    /**
     * 
     */
    public void stopIt() {
        hasToRun.set(false);
        cleanup();
    }

    private void cleanup() {
        if (finished.compareAndSet(false, true)) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Throwable ignore) {
                    // ignore
                }
            }
        }
    }

    /**
     * All the IO oper on the socket should be done only from this thread
     */
    @Override
    public void run() {

        try {
            final DatagramPacket packet = new DatagramPacket(buf, buf.length);

            while (hasToRun.get()) {

                //check if socket is still bound
                boolean bSockNotBound = ((socket == null) || socket.isClosed() || !socket.isBound());
                if (bSockNotBound) {
                    int ioErrC = 0;
                    while ((ioErrC < 10) && bSockNotBound) {
                        try {
                            if (socket != null) {
                                try {
                                    socket.close();
                                    socket = null;
                                } catch (Throwable ignore) {
                                    // ignore
                                }
                            }
                            bindSocket();
                        } catch (IOException ioe) {
                            logger.log(Level.WARNING, " [ GenericUDPListener ]  Unable to bind socket IOErrorCount = "
                                    + ioErrC, ioe);
                            try {
                                Thread.sleep(500);
                            } catch (Throwable ignore) {
                                // ignore
                            }
                        } finally {
                            ioErrC++;
                            bSockNotBound = ((socket == null) || socket.isClosed() || !socket.isBound());
                        }
                    }//while(ioErrC < 10)
                }

                //receive the datagrams
                try {
                    socket.receive(packet);
                    final InetAddress address = packet.getAddress();
                    if (floodController.shouldDrop(address)) {
                        continue;
                    }

                    if (accessConf != null) {
                        if (!accessConf.checkIP(address)) {
                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, "The IP [ " + address
                                        + " ] is not allowed to send datagrams...ignoring it");
                            }
                            continue;
                        }
                    }

                    int len = packet.getLength();
                    byte[] data = packet.getData();
                    if ((len > 0) && (notif != null)) {
                        notif.notifyData(len, data, address);
                    }

                } catch (Throwable t) {
                    logger.log(Level.WARNING, " [ GenericUDPListener ] Failed receiving/notifying UDP datagram.", t);
                }
            }
        } finally {
            cleanup();
        }
    }
}
