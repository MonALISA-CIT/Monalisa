/**
 * Authorization Service Maintains access control list , policies in ML groups
 */
package lia.util.security.authz;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.PrivilegedAction;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.Subject;

import lia.Monitor.monitor.AppConfig;
import lia.util.security.MLLogin;

import org.xml.sax.SAXException;

public final class AuthZService {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(AuthZService.class.getName());

    private final Integer port;
    private final ServerSocket sEndpoint;
    Policy policy;
    /**
     * static fields
     */
    final static ExecutorService pool;
    final static ThreadFactory tfactory;

    static {
        tfactory = new DaemonThreadFactory();
        pool = Executors.newCachedThreadPool(tfactory);

    }

    public AuthZService(int iPort) throws IOException {
        this.port = iPort;

        sEndpoint = new ServerSocket();
        InetSocketAddress socketAddress = new InetSocketAddress(port);
        sEndpoint.bind(socketAddress);
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "Port: " + iPort);
        }

    }

    public void init() throws IOException, SAXException {

        String sXML = AppConfig.getProperty("policy.path");

        if (sXML == null) {
            sXML = "policy.xml";
        }

        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "Policy file:" + sXML);
        }
        policy = Policy.getInstance(sXML);

    }

    public void readPolicy() {

    }

    public ServerSocket getSEndpoint() {
        return this.sEndpoint;
    }

    public void dispatch() {
        while (true) {
            try {

                final Socket connection = this.sEndpoint.accept();

                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        handleRequest(connection);
                    }
                };
                pool.execute(r);

            } catch (IOException e) {
                logger.warning("Got an exception while accepting connections...Continue" + e);
            }
        }
    }

    public void handleRequest(Socket client) {
        ObjectInputStream ois = null;
        ObjectOutputStream oos = null;
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "New proxy connection:" + client.getInetAddress());
        }
        try {
            oos = new ObjectOutputStream(client.getOutputStream());
            ois = new ObjectInputStream(client.getInputStream());

        } catch (Exception e1) {
            try {
                client.close();
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
            return;
        }
        try {
            // read the request from client
            AuthZRequest request = (AuthZRequest) ois.readObject();

            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Receive a request:" + request.subject);
            }
            TreeMap<String, Boolean> subjectPerm = policy.getSubjectPermissions(request.subject);
            AuthZResponse response = new AuthZResponse(request.subject, subjectPerm);
            oos.writeObject(response);
            oos.flush();
            // close streams and connections
            ois.close();
            oos.close();
            client.close();
        } catch (Exception e) {
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, e.getMessage());
            }
        }

        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "Policy checker connection end:" + client.getInetAddress());
        }

    }

    public static void main(String[] args) {

        try {
            int port = Integer.valueOf(AppConfig.getProperty("lia.util.security.authz.port", "36006")).intValue();

            AuthZService authzServer = new AuthZService(port);

            authzServer.init();

            authzServer.jiniInit();

            authzServer.dispatch();
            logger.info("AuthZService is ready.");
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Failed to init AutZhService:", t);
        }
    }

    private void jiniInit() {

        System.out.println("registering in LUSs...");
        boolean useSecLUSs = Boolean.valueOf(AppConfig.getProperty("lia.util.security.authz.useSecureLUSs", "false"))
                .booleanValue();
        if (useSecLUSs) {
            logger.log(Level.INFO, "Use Secure LUSs");
            try {
                /*
                 * set trustStore to empty string to accept any certificate in a
                 * SSL session (we don't want to authenticate the server (LUS)
                 */
                System.setProperty("javax.net.ssl.trustStore", "");

                /*
                 * gather private key and certificate chain from files
                 */
                String privateKeyPath = System.getProperty("lia.util.security.authz.privateKeyFile",
                        "/etc/grid-security/hostkey.pem");
                String certChainPath = System.getProperty("  lia.util.security.authz.certChainFile",
                        "/etc/grid-security/hostcert.pem");

                logger.log(Level.FINEST, "Loading credentials from files\n" + privateKeyPath + "\n" + certChainPath);
                /*
                 * create local subject used in auth/authz
                 */
                MLLogin serviceCredentials = new MLLogin();
                serviceCredentials.login(privateKeyPath, null, certChainPath);

                Subject ctxSubject = serviceCredentials.getSubject();
                if (ctxSubject == null) {
                    logger.log(Level.WARNING, "Subject is null");
                }
                logger.log(Level.FINE, "SUBJECT: " + ctxSubject);

                Subject.doAsPrivileged(ctxSubject, new PrivilegedAction() {
                    @Override
                    public Object run() {
                        AuthZJiniService tjs = AuthZJiniService.getInstance();
                        tjs.setDaemon(false);
                        tjs.start();
                        return null; // nothing to return
                    }
                }, null);

            } catch (Throwable t) {
                logger.log(Level.WARNING, "Cannot init service credentials....Returning...\n" + t.getMessage());
            }
        }//if (useSecLUSs) ...
        else {//start service 
            AuthZJiniService tjs = AuthZJiniService.getInstance();
            tjs.setDaemon(false);
            tjs.start();
        }
    }

}

final class DaemonThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        return thread;
    }
}
