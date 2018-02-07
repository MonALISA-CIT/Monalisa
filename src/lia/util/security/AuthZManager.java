package lia.util.security;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import lia.Monitor.JiniSerFarmMon.MLLUSHelper;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.GenericMLEntry;
import lia.util.security.authz.AuthZRequest;
import lia.util.security.authz.AuthZResponse;
import net.jini.core.lookup.ServiceItem;

/**
 * Authorization checker 
 * @author adim
 * @version Sep 7, 2005 8:53:46 PM
 */
public class AuthZManager extends Thread {
    protected static final int DEFAULT_AUTHZ_PORT = 6066;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(AuthZManager.class.getName());

    // authorization server used for authorize clients
    private String authzServer;
    private static long CHECK_CLIENTS_INTERVAL;
    static {
        try {
            CHECK_CLIENTS_INTERVAL = Long.parseLong(AppConfig
                    .getProperty("lia.Monitor.Agents.OpticalPath.comm.AuthZManager.check_interval"));
        } catch (Throwable t) {
            CHECK_CLIENTS_INTERVAL = 60 * 60 * 1000;// 1h
        }
    }

    private final Set clients;
    private volatile boolean hasToRun = true;

    private final Object _lock = new Object();

    public final static String OSADMINS_GROUP = "OSAdmins";
    public final static String OSDAEMONS_GROUP = "OSDaemons";
    private final String[] authorization_groups;

    public AuthZManager(String[] authorization_groups) {
        this.authorization_groups = authorization_groups;
        setAuthzServiceAddress();
        //this.authzServer = as;
        // create the clients map
        clients = Collections.synchronizedSet(new HashSet());
    }

    /**
     * Default authorization group is set to {@link #AuthZManager.OSADMINS_GROUP}
     * @param authorization_groups
     */
    public AuthZManager() {
        this.authorization_groups = new String[] { "OSADMINS_GROUP" };

        setAuthzServiceAddress();
        //this.authzServer = as;
        // create the clients map
        clients = Collections.synchronizedSet(new HashSet());
    }

    @Override
    public void run() {
        while (hasToRun) {

            synchronized (_lock) {
                if (authzServer == null) {
                    setAuthzServiceAddress();
                }
            }

            System.out.println("AuthZManager started: Using AuthzService:" + authzServer);
            synchronized (clients) {
                if (clients.size() > 0) {
                    for (Iterator iter = clients.iterator(); iter.hasNext();) {
                        SSLSocket client = (SSLSocket) iter.next();
                        if (client.isClosed()) {
                            iter.remove();
                            continue;
                        }
                        //  isAuthorized ?
                        if (!checkClient(client)) {
                            try {
                                // close the connection since the client is not
                                // authorized anymore to use this service
                                client.close();
                            } catch (IOException e) {
                                logger.log(Level.WARNING, "Closing client connection failed", e);
                            }
                            iter.remove();
                        }
                    }// for clients..
                }// if size>0
            }// synch
            try {
                Thread.sleep(CHECK_CLIENTS_INTERVAL);
            } catch (InterruptedException e) {
                hasToRun = false;
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "AuthZ Thread interrupted");
                }
            }
        }// while

    }

    public void finish() {
        this.hasToRun = false;
    }

    private Socket connectToAuthzService(final String host) throws IOException {
        Socket sock = null;
        // Create a socket with a timeout			
        String[] aHost = host.split(":");
        InetAddress addr = InetAddress.getByName(aHost[0]);
        int port = DEFAULT_AUTHZ_PORT;
        // if port supplied
        if (aHost.length == 2) {
            try {
                int p = Integer.parseInt(aHost[1]);
                port = p < 0 ? DEFAULT_AUTHZ_PORT : p;
            } catch (NumberFormatException nfe) {
                port = DEFAULT_AUTHZ_PORT;
            }
        }

        SocketAddress sockaddr = new InetSocketAddress(addr, port);
        // Create an unbound socket
        sock = new Socket();
        // This method will block no more than 60s.
        // If the timeout occurs, SocketTimeoutException is
        // thrown.
        sock.connect(sockaddr, 60000);
        return sock;
    }

    public boolean checkClient(final String subjectDN) {
        logger.log(Level.INFO, "AuthZManager checkClient:  " + subjectDN);

        AuthZResponse response;

        /*
         * this.issuerDN=certs[0].getIssuerDN().toString();
         * this.clientPublicKey=certs[0].getPublicKey();
         */
        final String fAuthZServer;
        try {
            synchronized (_lock) {
                if (authzServer == null) {
                    //search in LUS
                    setAuthzServiceAddress();
                    //failed to get authz service from LUS?
                    if (authzServer == null) {
                        //"No Authz Service available"
                        logger.log(Level.SEVERE, "No Authz Service available");
                        return false;
                    }
                }
                //not null
                fAuthZServer = authzServer;
            }

            Socket sock = null;
            ObjectInputStream ois = null;
            ObjectOutputStream oos = null;

            //try {		
            // Create an unbound socket
            sock = connectToAuthzService(fAuthZServer);

            oos = new ObjectOutputStream(sock.getOutputStream());
            ois = new ObjectInputStream(sock.getInputStream());

            AuthZRequest request = new AuthZRequest(subjectDN, authorization_groups);
            oos.writeObject(request);
            oos.flush();

            logger.fine("[AUTHZ] Request sent...Waiting response for: " + request);

            try {
                response = (AuthZResponse) ois.readObject();
            } catch (ClassNotFoundException e) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.FINEST, "Received an unknown authorization response (CCE)");
                }
                return false;
            }

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "\n\nAuthz response for " + subjectDN + ": " + response + " IsAuthorized?"
                        + response.isAuthorized());
            }
            return response.isAuthorized();

        } catch (IOException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Failed to fetch the permissions for [" + subjectDN + "] from ["
                        + authzServer + "]. Invalidating authorization service");
            }
            synchronized (_lock) {
                authzServer = null;
            }
            return false;
        }
    }

    /**
     * @param s -
     *            client connection
     * @return true is client is listed in authorization service as a trusted
     *         one, false if he is unknown in authorization server
     */
    public boolean checkClient(final SSLSocket s) {
        logger.log(Level.INFO, "AuthZManager checkClient:  " + s);
        // blocks until hanshaking completed
        SSLSession session = s.getSession();

        javax.security.cert.X509Certificate[] certs;
        try {
            certs = session.getPeerCertificateChain();
        } catch (SSLPeerUnverifiedException e) {
            e.printStackTrace();
            return false;
        }
        if (logger.isLoggable(Level.FINE)) {
            StringBuilder sb = new StringBuilder();
            String hostname = session.getPeerHost();
            sb.append("certificate chain from " + hostname + ": ChainLength" + certs.length);
            for (int i = 0; i < certs.length; i++) {
                sb.append("\n--------\nSubjectDN-X509Certificate[" + i + "]=" + certs[i].getSubjectDN());
                sb.append("\nIssuerDN-X509Certificate[" + i + "]=" + certs[i].getIssuerDN());
            }
            logger.log(Level.FINE, sb.toString());
        }
        return checkClient(certs[0].getSubjectDN().toString());

    }

    /**
     * register this client connection for period checks of permissions
     * 
     * @param s
     */

    public void registerClient(SSLSocket s) {
        synchronized (clients) {
            this.clients.add(s);
        }
    }

    /**
     * update the authorization service from LUSs
     */
    private void setAuthzServiceAddress() {
        // get IPID service address
        String tAuthzServer = null;
        try {
            MLLUSHelper.getInstance().forceUpdate();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
            }
            ServiceItem[] si = MLLUSHelper.getInstance().getAuthzServices();
            if ((si == null) || (si.length == 0) || (si[0].attributeSets.length == 0)) {
                logger.log(Level.SEVERE, "No Authz service was found (yet)");
                tAuthzServer = null;
            } else {
                GenericMLEntry gmle = (GenericMLEntry) si[0].attributeSets[0];
                if (gmle.hash != null) {
                    tAuthzServer = (String) gmle.hash.get("hostname");
                    logger.log(Level.INFO, "Found an Authz service at " + gmle);
                }
            }

        } catch (Exception ex) {
            logger.log(Level.WARNING, "While updating authz services list, got:", ex);
        }

        synchronized (_lock) {
            authzServer = tAuthzServer;
        }
    }

    //getters
    public String getAuthzServer() {
        synchronized (_lock) {
            return this.authzServer;
        }
    }
}
