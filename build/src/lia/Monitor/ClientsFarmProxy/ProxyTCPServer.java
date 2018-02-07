/*
 * $Id: ProxyTCPServer.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.ClientsFarmProxy;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.util.Utils;

/**
 * Base TCP Server for both Clients and ML Services 
 * 
 * @author mickyt,ramiro
 */
public class ProxyTCPServer extends Thread {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(ProxyTCPServer.class.getName());
    private static final ProxyTCPServer _theInstance;
    // private ServerSocket listenSocket = null;
    private final boolean hasToRun = true;
    private final int[] ports;
    private final String[] extAddresses;
    private final String hostname;

    private final static ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime()
            .availableProcessors() * 2);

    static {
        ProxyTCPServer tmpServer = null;

        try {
            tmpServer = new ProxyTCPServer();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Cannot instantiate ProxyTCPServer", t);
            System.exit(-1);
        }

        _theInstance = tmpServer;
    }

    public static ProxyTCPServer getInstance() {
        return _theInstance;
    }

    public int getPort() {
        return ports[0];
    }

    public int[] getPorts() {
        return ports;
    } // getPorts

    public String[] getExternalAddresses() {
        return extAddresses;
    }

    public final String getHostname() {
        return hostname;
    }

    private ProxyTCPServer() throws Exception {

        ServerSocket listenSocket = null;
        String s = AppConfig.getProperty("lia.Monitor.ClientsFarmProxy.ProxyPort", "6001");

        final ArrayList<ServerSocket> listenSockets = new ArrayList<ServerSocket>();
        StringTokenizer st = new StringTokenizer(s, " ");
        while (st.hasMoreTokens()) {
            try {
                String t = st.nextToken();
                t = t.trim();
                int bindPort = (Integer.valueOf(t)).intValue();
                listenSocket = new ServerSocket(bindPort, 50);
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "Cannot create ServerSocket", t);
                throw new Exception(t);
            } // try - catch

            new ServerPort(listenSocket).start();
            listenSockets.add(listenSocket);

        } // while

        if (listenSockets.size() == 0) {
            logger.log(Level.SEVERE, "Proxy ServerSocket not bound!! will exit");
            System.exit(1);
        } // if

        // set all local ports
        ports = new int[listenSockets.size()];
        int i = 0;

        for (ServerSocket ss : listenSockets) {
            ports[i] = ss.getLocalPort();
            i++;
        } // for

        final String extHostname = AppConfig.getProperty("lia.Monitor.ClientsFarmProxy.ProxyHostname");
        final String[] addrsProps = AppConfig.getVectorProperty("lia.Monitor.ClientsFarmProxy.ProxyAddresses");
        final List<InetAddress> validAddresses = new LinkedList<InetAddress>();
        hostname = (extHostname == null) ? InetAddress.getLocalHost().getHostName() : extHostname;

        if (addrsProps == null) {
            InetAddress[] allByName = InetAddress.getAllByName(hostname);
            if ((allByName == null) || (allByName.length == 0)) {
                throw new RuntimeException(
                        "Unable to determine the valid IP address for current host. The hostname is: " + hostname);
            }

            for (InetAddress ia : allByName) {
                if (ia.isAnyLocalAddress()) {
                    continue;
                }
                validAddresses.add(ia);
            }
        } else {
            for (final String sap : addrsProps) {
                InetAddress[] allByName = InetAddress.getAllByName(sap);
                if ((allByName == null) || (allByName.length == 0)) {
                    continue;
                }
                for (InetAddress ia : allByName) {
                    if (ia.isAnyLocalAddress()) {
                        continue;
                    }
                    validAddresses.add(ia);
                }
            }
        }

        if (validAddresses.isEmpty()) {
            throw new RuntimeException("Unable to determine any valid InetAddress! Local hostname: " + hostname);
        }

        final List<String> expAddr = new ArrayList<String>(validAddresses.size());
        for (InetAddress ia : validAddresses) {
            expAddr.add(ia.getHostAddress());
        }
        extAddresses = expAddr.toArray(new String[expAddr.size()]);
        logger.log(Level.INFO, "\n\n ==== ProxyTCPServer Inet coordinates: ===== \nhostname: " + hostname
                + "\nAddresses: " + Arrays.toString(extAddresses) + "\n ====================S============ \n\n");
    }

    class ServerPort extends Thread {

        private final ServerSocket listenSocket;
        private final int listenPort;

        public ServerPort(ServerSocket listenSocket) {
            if (listenSocket == null) {
                throw new NullPointerException("Listen socket cannot be null");
            }
            this.listenSocket = listenSocket;
            this.listenPort = this.listenSocket.getLocalPort();
            this.setName("(ML) ProxyTCPServer listen instance on port " + listenPort);

        }

        @Override
        public void run() {

            logger.log(Level.INFO, "ProxyTCPServer on port " + listenPort + " started");

            while (hasToRun) {
                try {
                    final Socket s = listenSocket.accept();
                    executor.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                //TODO
                                //add some verify/accounting in the future
                                ProxyTCPWorker.newInstance(s);
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, " ProxyTCPServer on port " + listenPort
                                        + " got exception on newConnection ( " + s
                                        + " ). will close the socket. Cause: ", t);
                                Utils.closeIgnoringException(s);
                            }
                        }
                    });

                } catch (Throwable t) {
                    logger.log(Level.WARNING, "ProxyTCPServerThread which listens on port " + listenPort
                            + " got exception in main loop", t);
                }// try - catch
            } // while
        } // run
    } // ServerPort
}
