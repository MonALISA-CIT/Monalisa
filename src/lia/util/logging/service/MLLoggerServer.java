package lia.util.logging.service;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;

/**
 * 
 * @author ramiro
 */
public class MLLoggerServer extends Thread {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(MLLoggerServer.class.getName());

    private int listenPort;

    private String listenAddress;

    ServerSocket ss;

    private AtomicBoolean hasToRun;
    
    private static MLLoggerServer _theInstance;
    
    //it is not used for the moment ...
    private Vector proxyConns;
    
    private MLLoggerServer() throws Exception {
        super("( ML ) MLLoggerServer Thread");
        ss = new ServerSocket();
        hasToRun = new AtomicBoolean(true);
        proxyConns = new Vector();
        
        try {
            reloadConf();
            startServer();
        } catch(Throwable t) {
            hasToRun.set(false);
            throw new Exception(t);
        } finally {
            start();
        }
    }

    public static synchronized MLLoggerServer getInstance() {
        if(_theInstance == null) {
            try {
                _theInstance = new MLLoggerServer();
            } catch(Throwable t) {
                logger.log(Level.WARNING, " [ MLLoggerServer ] Cannot instantiate the server", t);
            }
        }
        
        return _theInstance;
    }
    
    private void startServer() throws Exception {
        InetSocketAddress isa = null;
        
        if(listenAddress == null) {
            throw new Exception("listenAddress cannot be null");
        }
        
        isa = new InetSocketAddress(listenAddress, listenPort);
        
        ss.bind(isa);
    }

    private void reloadConf() {
        try {

            listenPort = AppConfig.geti("lia.util.logging.service.port", 46006);
            listenAddress = AppConfig.getProperty("lia.util.logging.service.useAddress");

            if (listenAddress == null) {
                logger.log(Level.INFO, " No lia.util.logging.service.useAddress defined in configuration file ... will try to determine it");
                String localhost;
                try {
                    localhost = InetAddress.getLocalHost().getHostAddress();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Cannot determine localhost address", t);
                    localhost = null;
                }
                listenAddress = localhost;
            }

           if(listenAddress != null) {
               System.setProperty("lia.util.logging.service.useAddress", listenAddress);
               System.setProperty("lia.util.logging.service.port", "" + listenPort);
           }

        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ MLLoggerServer ] Got Exception reloading config", t);
        }
    }

    public void stopIt() {
        hasToRun.set(false);
        try {
            if(ss != null) {
                ss.close();
            }
        } catch(Throwable t) {
          logger.log(Level.WARNING, "Got exception closing server socket", t);  
        }
    }
    
    private void cleanup() {
        try {
            if(ss != null) {
                ss.close();
                ss = null;
            }
        } catch(Throwable t) {
          logger.log(Level.WARNING, "Got exception closing server socket", t);  
        }
    }

    public void remove(MLProxyConn conn) {
        proxyConns.remove(conn);
    }
    
    public void run() {
        while(hasToRun.get()) {
            try {
                Socket s = ss.accept();
                if(s != null) {
                    final MLProxyConn mpc = MLProxyConn.newInstance(s);
                    if(mpc != null) {
                        proxyConns.add(mpc);
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Got exeception in accept()", t);
            }
        }//while()
        
        cleanup();
    }

}
