package lia.Monitor.Agents.OpticalPath.comm;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocket;

import lia.util.security.AuthZManager;

public class XDRAuthZSSLTcpSocket extends XDRTcpSocket {

    /** Logger used by this class **/
    private static final Logger logger = Logger.getLogger(XDRAuthZSSLTcpSocket.class.getName());

    protected AuthZManager authzManager;
    protected SSLSocket socket;

    /**
     * @param s -
     *            ssl-socket
     * @param notifier
     *            -xdrnotifier
     * @param authzManager -
     *            authorization manager used for periodically checks against the
     *            authorization policies
     * @throws IOException
     */
    public XDRAuthZSSLTcpSocket(SSLSocket s, XDRMessageNotifier notifier, AuthZManager authzManager) throws IOException {
        super(s, notifier);
        this.authzManager = authzManager;
        this.socket = s;
    }

    @Override
    public void run() {
        /*
         * -before starting talking to you let me check that if you are a
         * reliant client-
         * 
         * authorize client and save the socket for further checks against the
         * authorization policies
         */
        final boolean isAuthorized = authzManager.checkClient(socket);
        /* not an authorized client, close this connection */
        if (!isAuthorized) {
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Client " + socket + "it' not authorized. Closing connection...");
            }
            close();
        }

        // register in authorization manager for periodic checks of permissions
        authzManager.registerClient(socket);

        // ok pal, let's I agree to talk to you
        super.run();
    }

}
