/*
 * $Id: tcpServer.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.DataCache;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.monMessage;
import lia.util.exporters.TCPRangePortExporter;

/**
 * 
 * @author Iosif Legrand
 * @author ramiro
 *
 */
public class tcpServer extends Thread {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(tcpServer.class.getName());

    Cache cache;

    public int lis_port = 0;

    protected boolean active;

    private final Map<String, tcpClientWorker> connections = new ConcurrentHashMap<String, tcpClientWorker>();

    private ServerSocket listen_socket = null;

    public tcpServer(Cache cache) throws Exception {
        super();

        final boolean bStartServer = AppConfig.getb("lia.Monitor.DataCache.startTcpServer", true);
        this.cache = cache;
        active = false;

        if (bStartServer) {
            final String forceIP = AppConfig.getProperty("lia.Monitor.useIPaddress");

            try {
                if (forceIP != null) {
                    listen_socket = TCPRangePortExporter.bind(50, InetAddress.getByName(forceIP));
                } else {
                    listen_socket = TCPRangePortExporter.bind(50, null);
                }
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "Failed to create server socket", t);
                throw new Exception(t);
            }

            if (listen_socket == null) {
                throw new Exception("ServerSocket == null in TCP Server!!!");
            }
            lis_port = listen_socket.getLocalPort();
            active = true;

            logger.log(Level.INFO, "\n Start TCP Server @ " + cache.getIPAddress() + ":" + lis_port + "\n");
        } else {
            active = false;
        }

        this.setName("(ML) tcpServer @ " + cache.getIPAddress() + ":" + lis_port);

        try {
            this.setDaemon(true);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Cannot setDaemon", t);
        }

        start();
    }

    @Override
    public void run() {
        while (active) {
            try {
                final Socket client_sock = listen_socket.accept();
                final InetAddress ad = client_sock.getInetAddress();
                final int port = client_sock.getPort();
                final String host = ad.getHostName();
                final String ck = host + ":" + port + ":-" + client_sock.getLocalPort();

                logger.log(Level.INFO, "\nGOT tClient from " + ck + "\n");

                if (!connections.containsKey(ck)) {
                    try {
                        tcpClientWorker uc = tcpClientWorker.newInstance(cache, this, client_sock, ck);
                        connections.put(ck, uc);
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Cannot instantiate the worker for the connection", t);
                        connections.remove(ck);
                    }
                } else {
                    logger.log(Level.WARNING, "\n\n TCP  Server --- Connection exist ! !!!   FIXME \n\n");
                }

            } catch (Throwable t) {
                logger.log(Level.WARNING, "Got Exception in the main loop", t);
            }
            yield();
        }
    }

    void newResult(Object o) {
        if (connections.size() == 0) {
            return;
        }
        for (final tcpClientWorker lw : connections.values()) {
            lw.addNewResult(o);
        }
    }

    /**
     * 
     * @return number of Connected Clients
     */
    public int getConnNo() {
        return connections.size();
    }

    void updateConfig(MFarm farm) {
        if (connections.size() == 0) {
            return;
        }
        monMessage msg = new monMessage(monMessage.ML_CONFIG_TAG, null, farm);
        for (final tcpClientWorker lw : connections.values()) {
            lw.WriteObject(msg, tcpClientWorker.ML_CONFIG_MESSAGE);
        }
    }

    public String getConnKey(Socket s) {
        try {
            String host = s.getInetAddress().getHostName();
            int port = s.getPort();
            return host + ":" + port + ":-" + s.getLocalPort();
        } catch (Throwable t) {
        }

        return null;
    }

    public void disconnectClient(String toKey) {
        connections.remove(toKey);
    }

}
