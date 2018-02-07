package lia.Monitor.Agents.OpticalPath.comm;

import javax.net.ssl.SSLSocket;

import lia.Monitor.monitor.AppConfig;
import lia.util.security.AuthZManager;
import lia.util.security.RSSF;

public class XDRAuthZSSLTcpServer extends XDRTcpServer {

    private volatile boolean hasToRun;
    private final int port;

    private final AuthZManager authzManager;

    /**
     * @param port -
     *            port to bind to
     * @param notifier -
     *            an implementation of the XDRMessageNotifier interface used for
     *            communication with peers
     * @throws Exception
     */
    public XDRAuthZSSLTcpServer(int port, XDRMessageNotifier notifier) throws Exception {
        super("( ML ) XDRAuthZTcpServer :- Listening on port [ " + port + " ] ", new RSSF().createServerSocket(port,
                AppConfig.getProperty("lia.Monitor.OS.SKeyStore"), RSSF.DEFAULT_TM), notifier);

        this.port = port;
        //* we are not using static configuration for authroziation services anymore - get them from jini *// 
        //String authzService = AppConfig.getProperty("lia.Monitor.Agents.OpticalPath.comm.tcp_authz", "ui.rogrid.pub.ro");
        //this.authzManager = new AuthZManager(authzService);
        this.authzManager = new AuthZManager(new String[] { AuthZManager.OSDAEMONS_GROUP });
        //start listening
        this.hasToRun = true;
    }

    @Override
    public void run() {
        authzManager.start();
        System.out.println("XDRAuthZSSLTcpServer entering main loop ... listening on port " + port);
        while (hasToRun) {
            try {
                SSLSocket s = (SSLSocket) ss.accept();
                s.setTcpNoDelay(true);
                // pass the authorization manager for initial
                // checks/registration
                new XDRAuthZSSLTcpSocket(s, notifier, authzManager).start();

            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    /**
     * stops this tcpserver
     */
    public void finish() {
        this.hasToRun = false;
    }

    /**
     * DEBUG     * 
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        new XDRAuthZSSLTcpServer(9323, null).start();
    }

}
