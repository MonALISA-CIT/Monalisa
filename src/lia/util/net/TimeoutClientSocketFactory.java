package lia.util.net;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.rmi.server.RMIClientSocketFactory;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;

public class TimeoutClientSocketFactory implements RMIClientSocketFactory, Serializable {

    private static final long serialVersionUID = -6235123910320552975L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(TimeoutClientSocketFactory.class.getName());

    /** maximum time to connect with the other endPoint */
    private static final int CONNECT_TIMEOUT = Integer.valueOf(
            AppConfig.getProperty("lia.util.net.TimeoutClientSocketFactory.CONNECT_TIMEOUT", "10")).intValue() * 1000; //10s
    /** maximum time to wait for data */
    private static final int SO_TIMEOUT = Integer.valueOf(
            AppConfig.getProperty("lia.util.net.TimeoutClientSocketFactory.SO_TIMEOUT", "20")).intValue() * 1000; //20s
    private static Hashtable hps = new Hashtable();

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        Socket s = null;
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, " [ TimeoutClientSocketFactory ] RMI is trying to connect [ " + host + ":" + port
                    + " ]");
        }
        if (!hps.containsKey(host + ":" + port)) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ TimeoutClientSocketFactory ] ... creating socket [ " + host + ":" + port
                        + " ]");
            }

            s = new Socket();
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT);
            //what if an exception is thrown...should I catch it, or is the socket already broken?
            s.setSoTimeout(SO_TIMEOUT);
            hps.put(host + ":" + port, s);

        } else {
            s = (Socket) hps.get(host + ":" + port);
            if ((s != null) && s.isConnected() && !s.isClosed()) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " [ TimeoutClientSocketFactory ] ... reusing socket " + s);
                }
                return s;
            }

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ TimeoutClientSocketFactory ] ... creating socket [ " + host + ":" + port
                        + " ]");
            }
            s = new Socket();
            hps.put(host + ":" + port, s);
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT);
            //what if an exception is thrown...should I catch it, or is the socket already broken?
            //            s.setSoTimeout(SO_TIMEOUT);
        }

        return s;
    }
}