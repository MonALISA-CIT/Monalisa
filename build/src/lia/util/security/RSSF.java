/*
 * $Id: RSSF.java 7419 2013-10-16 12:56:15Z ramiro $
 */
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

/**
 * 
 * RMI Server Socket Factory for MLService
 * @author ramiro
 * 
 */
public class RSSF implements RMIServerSocketFactory, Serializable {

    private static final long serialVersionUID = 4659681386033348356L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(RSSF.class.getName());

    public static final int CUSTOM_TM = 0;

    public static final int DEFAULT_TM = 1;

    @Override
    public int hashCode() {
        int retHash = 0;

        try {

            String store = AppConfig.getProperty("lia.Monitor.SKeyStore");
            String passwd = AppConfig.getProperty("lia.Monitor.SKeyStorePass", "monalisa");

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

    public ServerSocket createServerSocket(int port, String store, int iTrustManageType) throws IOException {
        SSLServerSocketFactory ssf = null;
        SSLServerSocket ss = null;
        try {
            // set up key manager to do server authentication

            String passwd = AppConfig.getProperty("lia.Monitor.SKeyStorePass", "monalisa");

            // String alias=AppConfig.getProperty("lia.Monitor.AdminUser");

            logger.log(Level.CONFIG, "RSSF KS = " + store);

            SSLContext ctx;
            KeyManagerFactory kmf;
            KeyStore ks;
            TrustManagerFactory tmf;
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
            if (iTrustManageType == CUSTOM_TM) {
                ctx.init(kmf.getKeyManagers(), new TrustManager[] { new FarmMonitorTrustManager(ks) }, null);
            } else {// DEFAULT
                // default tust-manager (SUN/IBM) makes the standard checks on client;s certificate chain (authentication)
                ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
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

            final String forceIP = AppConfig.getProperty("lia.Monitor.useIPaddress");

            if (forceIP != null) {
                ss.bind(new InetSocketAddress(forceIP, port));
            } else {
                ss.bind(new InetSocketAddress(port));
            }

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

    // standard store in MonALISA
    @Override
    public ServerSocket createServerSocket(int port) throws IOException {
        return this.createServerSocket(port, AppConfig.getProperty("lia.Monitor.SKeyStore"), CUSTOM_TM);
    }

}
