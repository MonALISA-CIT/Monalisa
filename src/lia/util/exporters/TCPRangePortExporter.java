/*
 * Created on Jul 16, 2003
 *
 */
package lia.util.exporters;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;

/**
 * All ServerSockets MUST be binded using this class!
 */
public final class TCPRangePortExporter extends RangePortExporter {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(TCPRangePortExporter.class.getName());

    public static final ServerSocket bind(int backlog, InetAddress addr) throws Exception {
        ServerSocket listen_socket = null;
        try {
            for (int bindPort = MIN_BIND_PORT; bindPort <= MAX_BIND_PORT; bindPort++) {
                if (allocatedPorts.containsValue(Integer.valueOf(bindPort))) {
                    continue;
                }
                try {
                    listen_socket = new ServerSocket(bindPort, backlog, addr);
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "ServerSocket exported [ " + bindPort + " ] ");
                    }
                    allocatedPorts.put(listen_socket, Integer.valueOf(bindPort));
                    break;
                } catch (Throwable t) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINEST, " Got Exception....Trying next port.", t);
                    }
                    if (bindPort == MAX_BIND_PORT) {
                        logger.log(Level.SEVERE, "MAX_BIND_PORT (" + MAX_BIND_PORT + ") reached");
                        throw new Exception("MAX_BIND_PORT (" + MAX_BIND_PORT + ") reached");
                    }
                }//catch()
            }//for()
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "General Exception. Cannot bind ServerSocket.", t);
            throw new Exception(t);
        }
        return listen_socket;
    }

    public static final int bind(ServerSocket ss) throws Exception {
        try {
            for (int bindPort = MIN_BIND_PORT; bindPort <= MAX_BIND_PORT; bindPort++) {
                try {
                    final String forceIP = AppConfig.getProperty("lia.Monitor.useIPaddress");
                    if (forceIP == null) {
                        ss.bind(new InetSocketAddress(bindPort));
                    } else {
                        ss.bind(new InetSocketAddress(InetAddress.getByName(forceIP), bindPort));
                    }

                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, " [ TCPRangePortExporter ] ServerSocket exported [ " + bindPort + " ] ");
                    }

                    allocatedPorts.put(ss, Integer.valueOf(bindPort));
                    return bindPort;
                } catch (Throwable t) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, " Got Exception. Trying next port.", t);
                    }
                    if (bindPort == MAX_BIND_PORT) {
                        logger.log(Level.SEVERE, "MAX_BIND_PORT (" + MAX_BIND_PORT + ") reached");
                        throw new Exception("MAX_BIND_PORT (" + MAX_BIND_PORT + ") reached");
                    }
                }//catch()
            }//for()
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "General Exception. Cannot bind ServerSocket.", t);
            throw new Exception(t);
        }

        //should not get here
        return -1;
    }
}