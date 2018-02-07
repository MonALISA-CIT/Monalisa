package lia.monitoring.lemon;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GenericUDPListener extends Thread {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(GenericUDPListener.class.getName());

    protected DatagramSocket socket = null;
    protected boolean hasToRun;
    byte[] buf = new byte[8192];
    private GenericUDPNotifier notif = null;

    private UDPAccessConf accessConf;
    private final Object locker = new Object();

    public GenericUDPListener(int port, GenericUDPNotifier notif) throws IOException {
        this(null, port, notif, null);
    }

    public GenericUDPListener(int port, GenericUDPNotifier notif, UDPAccessConf conf) throws IOException {
        this(null, port, notif, conf);
    }

    public GenericUDPListener(InetAddress laddr, int port, GenericUDPNotifier notif, UDPAccessConf conf)
            throws IOException {
        super(" ( ML ) Generic UDP Listener on port [ " + port + " ]");
        if (laddr == null) {
            socket = new DatagramSocket(port);
        } else {
            socket = new DatagramSocket(port, laddr);
        }
        logger.log(Level.INFO, " Generic UDP Listener started on port " + port);
        if (socket == null) {
            System.out.println(" s = null ");
            System.exit(-1);
        }

        this.accessConf = conf;

        hasToRun = true;
        this.notif = notif;
        start();
    }

    public void setAccessConf(UDPAccessConf accessConf) {
        synchronized (locker) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "GenericUDPLister got new Conf....");
            }
            this.accessConf = accessConf;
        }
    }

    @Override
    public void run() {

        while (hasToRun) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                InetAddress address = packet.getAddress();
                synchronized (locker) {
                    if (accessConf != null) {
                        if (!accessConf.checkIP(address)) {
                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, "The IP [ " + address
                                        + " ] is not allowed to send datagrams...ignoring it");
                            }
                            continue;
                        }
                    }
                }//end sync
                int len = packet.getLength();
                byte[] data = packet.getData();
                if ((len > 0) && (notif != null)) {
                    notif.notifyData(len, data);
                }

            } catch (Throwable t) {
                logger.log(Level.WARNING, " Lemon :- GenericUDPListener got Exc", t);
            }
        }
        socket.close();
    }
}
