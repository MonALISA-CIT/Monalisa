package lia.util.security;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import lia.Monitor.monitor.AppConfig;

public class AuthZTrustManager implements X509TrustManager {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(AuthZTrustManager.class.getName());

    /** The default trust manager to delegate the main authentication decisions . */
    private final X509TrustManager defaultTrustManager;

    /** The authorization manager to delegate the main authorization decisions . */
    private final AuthZManager authorizationManager;

    public AuthZTrustManager() throws NoSuchAlgorithmException, KeyStoreException {
        this(null);
    }

    public AuthZTrustManager(KeyStore ks) throws NoSuchAlgorithmException, KeyStoreException {
        final String tmf_algo = AppConfig.getProperty("lia.util.security.AuthZMonitorTrustManagerAlgo",
                TrustManagerFactory.getDefaultAlgorithm());
        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmf_algo);
        tmf.init(ks);
        this.defaultTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];

        //String authzService = AppConfig.getProperty("lia.Monitor.Agents.OpticalPath.comm.rmi_authz", "ui.rogrid.pub.ro");
        //this.authorizationManager = new AuthZManager(authzService);
        this.authorizationManager = new AuthZManager();
        this.authorizationManager.start();
        logger.log(Level.INFO, "AuthZTrustManager loaded");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "[CHECK-CLIENT]: " + chain + " Type:" + authType);
        }
        //default authentication - cheks the client chain against the trusted CA-s certificates
        defaultTrustManager.checkClientTrusted(chain, authType);

        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "[Authentication passed..Continue with authz]: " + chain + " Type:" + authType);
        }
        //authorization
        //extract the DN of the first certificate ( user DN)
        final String clientDN = chain[0].getSubjectDN().toString();

        if (!authorizationManager.checkClient(clientDN)) {
            logger.log(Level.INFO,
                    " Client [" + clientDN + "] it's not authorized in " + authorizationManager.getAuthzServer());
            /* not an authorized client, close this connection */
            throw new CertificateException(" Client [" + clientDN + "] it's not authorized in "
                    + authorizationManager.getAuthzServer());
        } else {
            logger.log(Level.INFO,
                    " Client [" + clientDN + "] IS authorized in " + authorizationManager.getAuthzServer());
        }

        // cannot register in authorization manager for periodic checks of permissions because from RMI we don't 
        //have access to the socket connecting client
        //authorizationManager.registerClient(clientSocket);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
        return;
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return defaultTrustManager.getAcceptedIssuers();
    }

}
