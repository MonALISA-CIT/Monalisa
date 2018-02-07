package lia.util.security;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.rmi.server.RMIServerSocketFactory;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import lia.Monitor.monitor.AppConfig;
import lia.util.net.Util;

public class OSRSSF implements RMIServerSocketFactory, Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -3287310962030969441L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(OSRSSF.class.getName());

    @Override
    public int hashCode() {
        int retHash = 0;

        try {

            String store = AppConfig.getProperty("lia.Monitor.OS.SKeyStore");
            String passwd = "monalisa";

            if (store != null) {
                retHash += store.hashCode();
            }

            if (passwd != null) {
                retHash += passwd.hashCode();
            }

            return retHash;
        } catch (Throwable t) {

        }

        return super.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return this.getClass() == o.getClass();
    }

    @Override
    public ServerSocket createServerSocket(int port) throws IOException {
        SSLServerSocketFactory ssf = null;
        SSLServerSocket ss = null;
        try {
            // set up key manager to do server authentication

            String store = AppConfig.getProperty("lia.Monitor.OS.SKeyStore");
            String passwd = "monalisa";

            logger.log(Level.CONFIG, "   RSSF KS = " + store);

            SSLContext ctx;
            KeyManagerFactory kmf;
            TrustManagerFactory tmf;

            KeyStore ks;
            // TrustManagerFactory tmf;
            char[] storepswd = passwd.toCharArray();
            // char[] keypswd = Config.getKeyPassword().toCharArray();
            ctx = SSLContext.getInstance("TLS");

            /* IBM or Sun vm ? */
            if (System.getProperty("java.vm.vendor").toLowerCase().indexOf("ibm") != -1) {
                kmf = KeyManagerFactory.getInstance("IBMX509", "IBMJSSE");
                tmf = TrustManagerFactory.getInstance("IBMX509", "IBMJSSE");
            } else {
                kmf = KeyManagerFactory.getInstance("SunX509");
                tmf = TrustManagerFactory.getInstance("SunX509");
            }
            ks = KeyStore.getInstance("JKS");
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Loading KS");
            }

            ks.load(new FileInputStream(store), storepswd);

            try {
                Certificate[] cfs = Util.getCCB();
                for (int icfs = 0; icfs < cfs.length; icfs++) {
                    ks.setCertificateEntry("a" + icfs + "rca", cfs[icfs]);
                }
            } catch (Throwable t) {
                ks.load(new FileInputStream(store), storepswd);
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "KS Loaded");
            }

            kmf.init(ks, storepswd);
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "KMF Init (OK)!");
            }

            tmf.init(ks);
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "TMF Init (OK)!");
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Tryng to init CTX!");
            }
            if (AppConfig.getProperty("lia.util.security.OSRSSF.useDefaultTrustManager") != null) {
                // default tust-manager (SUN/IBM) makes the standard checks on client;s certificate chain (authentication)
                ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            } else if (AppConfig.getProperty("lia.util.security.OSRSSF.useAuthZTrustManager") != null) {
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "Using external authorization ..");
                }
                ctx.init(kmf.getKeyManagers(), new TrustManager[] { new AuthZTrustManager(ks) }, null);
            } else {
                ctx.init(kmf.getKeyManagers(), new TrustManager[] { new FarmMonitorTrustManager(ks) }, null);
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "CTX inited!");
            }

            ssf = ctx.getServerSocketFactory();

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Creating SSocket");
            }

            ss = (SSLServerSocket) ssf.createServerSocket();

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "SSocket created!");
            }
            // MUST USE ss.bind() ... otherwise will not bound
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "SSocket binding on port " + port);
            }
            ss.bind(new InetSocketAddress(port));
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "SSocket bounded on port " + port);
            }
            ss.setNeedClientAuth(true);

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "SSocket FINISHED ok! Bounded on " + port);
            }

        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Got Exception", t);
            }
            // MUST BE THROWN!!!!
            throw new IOException(t.getMessage());
        }
        return ss;
    }
}
