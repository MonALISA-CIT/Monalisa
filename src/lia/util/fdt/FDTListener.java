package lia.util.fdt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import lia.Monitor.monitor.AppConfig;
import lia.util.fdt.xdr.LisaXDRModule;
import lia.util.fdt.xdr.TLSSocketFactory;
import lia.util.fdt.xdr.XDRMessage;
import lia.util.fdt.xdr.XDRTcpSocket;

public class FDTListener {
    private static final Logger logger = Logger.getLogger(FDTListener.class.getName());

    // ConcurrentHashMap<int listenPort, FDTListener listener>
    private static ConcurrentHashMap _listenerInstances = new ConcurrentHashMap();
    private static Object sync = new Object();

    // for each started instance, we have a listening server
    private transient LisaXDRTcpServer server;
    // and a list of Known LISA XDR modules
    // protected ConcurrentHashMap<String, Module> chmXDRModules = new ConcurrentHashMap<String, Module>();
    protected ConcurrentHashMap chmXDRModules = new ConcurrentHashMap();

    /** Get a FDTListener instance on a specified port */
    public static final FDTListener getInstance(int listenPort) {
        synchronized (sync) {
            Integer IPort = Integer.valueOf(listenPort);
            FDTListener listenerInstance = (FDTListener) _listenerInstances.get(IPort);
            if (listenerInstance == null) {
                listenerInstance = new FDTListener();
                listenerInstance.init(listenPort);
                _listenerInstances.put(IPort, listenerInstance);
            }
            return listenerInstance;
        }
    }

    /** Get a FDTListener instance on the default port */
    public static final FDTListener getInstance() {
        int iPort = 11001;
        try {
            iPort = AppConfig.geti("lia.util.fdt.dport", 11001);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error parsing the default fdt port ", t);
        }
        return getInstance(iPort);
    }

    private FDTListener() {
        // empty
    }

    private void init(int iPort) {

        try {
            /*
             * start the  server-socket associated w/ this daemon iPort
             */
            server = new LisaXDRTcpServer(iPort);
            Thread thread = new Thread(server);
            thread.setDaemon(true);
            thread.start();
        } catch (Exception ex) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "[LISA XDRDaemon can not start] ", ex);
            }
            return;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    public void instanceStarted(String alias, LisaXDRModule instance) {
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "[XDRDaemon] [{0}] Active XDR Module added to XDR-dispatcher : ",
                    new Object[] { alias });
        }
        chmXDRModules.put(alias, instance);
    }

    public XDRMessage execModuleCommand(String module, String command, List args) {

        LisaXDRModule lisaModule = (LisaXDRModule) chmXDRModules.get(module);
        // unknown module or not an XDR module?
        if (lisaModule == null) {
            // this should never happen
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "Unknown module: " + module);
            }
            if (module != null) {
                chmXDRModules.remove(module);
            }
            return XDRMessage.getErrorMessage("Unknown module: " + module);
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "New XDR command received. Module: " + module + "Cmd: " + command + " Args:"
                    + args);
        }
        return lisaModule.execCommand(module, command, args);

    }

    private class LisaXDRTcpServer implements Runnable {
        private volatile boolean hasToRun;

        private final int port;

        protected String myName;

        ServerSocket ss = null;

        // XDRMessageNotifier notifier;

        /** connections pool handler */
        final protected ExecutorService pool;

        final private ThreadFactory tfactory;

        /**
         * Creates a simple XDR TCP Server
         * 
         * @param port
         * @param bindAddress
         * @throws Exception
         */
        public LisaXDRTcpServer(String bindAddress, int port) throws Exception {
            setMyName("( LISA2 ) XDRTcpServer :- Listening on port [ " + port + " ] ");
            this.port = port;
            ss = new ServerSocket();
            ss.bind(new InetSocketAddress(bindAddress, port));
            hasToRun = true;
            tfactory = new DaemonThreadFactory();
            pool = Executors.newCachedThreadPool(tfactory);
        }

        /**
         * Create a XDR Server listening on loopback interaace
         * 
         * @param port
         * @throws Exception
         */
        public LisaXDRTcpServer(int port) throws Exception {
            this("127.0.0.1", port);
        }

        /**
         * @param name:
         *            friendly name
         * @param ss:
         *            server socket
         * @throws Exception
         */
        public LisaXDRTcpServer(String name, ServerSocket ss) throws Exception {
            setMyName((name == null) ? "( LISA ) XDRTcpServer :- Listening on port [ " + ss.getLocalPort() + " ] "
                    : name);
            this.ss = ss;
            this.port = ss.getLocalPort();
            hasToRun = true;
            tfactory = new DaemonThreadFactory();
            pool = Executors.newCachedThreadPool(tfactory);
        }

        /**
         * @param ss:
         *            server socket
         * @throws Exception
         */
        public LisaXDRTcpServer(ServerSocket ss) throws Exception {
            this(null, ss);
        }

        /**
         * Creates a SSL XDR Server listening on <code>iPort</code>
         * 
         * @param iPort
         * @param sKeystore
         * @param sKeystorePassword
         * @throws Exception
         * @throws IOException
         */
        public LisaXDRTcpServer(int iPort, String sKeystore, String sKeystorePassword) throws IOException, Exception {
            // TODO: auth client (for now we limit by binding on localhost
            this("( LISA2 ) SSL XDR TcpServer :- Listening on port [ " + iPort + " ] ", TLSSocketFactory
                    .createServerSocket("127.0.0.1", iPort, sKeystore, sKeystorePassword, false));
        }

        /**
         * @see java.lang.Runnable#run()
         */
        @Override
        public void run() {
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "XDRTcpServerSocket entering main loop ... listening on port " + port);
            }
            while (hasToRun) {
                try {
                    Socket s = ss.accept();
                    s.setTcpNoDelay(true);
                    // add the client to the connection pool
                    pool.execute(new LisaXDRTcpSocket(s));
                    if (logger.isLoggable(Level.INFO)) {
                        logger.log(Level.INFO, "New client connection added to connection-pool" + s);
                    }
                } catch (Throwable t) {
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.log(Level.WARNING, "Exception while trying to stop the XDRDaemon: ", t);
                    }
                }
            }
        }

        public void stop() {
            // terminate the main loop
            hasToRun = false;
            // stop accepting connections
            try {
                this.ss.close();
            } catch (Throwable t) {
            } finally {
                ss = null;
            }
            // terminate the current tasks in pool
            try {
                pool.shutdown();
                if (!pool.awaitTermination(2L, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (Exception e) {
                // nothing to do, we've already done our best
                // cipsm: At least inform the user ?
                e.printStackTrace();
            }
        }

        /**
         * @return the friendly name of this communication endpoint
         */
        public String getMyName() {
            return this.myName;
        }

        /**
         * @param myName:
         *            communication endpoint friendly name
         */
        public void setMyName(String myName) {
            this.myName = myName;
        }

        /**
         * custom thread factory used in connection pool
         */

        private final class DaemonThreadFactory implements ThreadFactory {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                return thread;
            }
        }

        protected ExecutorService getDispatcherPool() {
            return this.pool;
        }
    }// LisaXDRSSLTcpServer

    /**
     * SSL Socket opened on server side of XDR Connection this implements the session between the client and daemon on a separate cached thread
     */

    private class LisaXDRTcpSocket extends XDRTcpSocket {
        private final Logger logger = Logger.getLogger(LisaXDRTcpSocket.class.getName());
        private String name;
        private SSLSocket sslSocket;

        public LisaXDRTcpSocket(Socket s) throws IOException {
            super(s);
            this.name = "[NOT_INTIALIZED] " + s.toString();
            if (s instanceof SSLSocket) {
                this.sslSocket = (SSLSocket) s;
            }
        }

        public String getPeerDN() {
            if (sslSocket == null) {
                return null;
            }

            // blocks until hanshaking completed
            SSLSession session = sslSocket.getSession();

            javax.security.cert.X509Certificate[] certs;
            try {
                certs = session.getPeerCertificateChain();
            } catch (SSLPeerUnverifiedException e) {
                logger.log(Level.WARNING, "Uset not authenticated", e);
                return null;
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
            return certs[0].getSubjectDN().toString();
        }

        @Override
        protected void initSession() throws Exception {
            logger.log(Level.INFO, "SESSION-INIT for " + getLocalAddress() + " <->" + getInetAddress() + " Peer DN: "
                    + getPeerDN());
            this.name = "[ACTIVE] " + getLocalAddress() + "<->" + getInetAddress();
        }

        @Override
        protected void xdrSession() throws Exception {

            while (true) {

                XDRMessage input = read();
                if ((input == null) || (input.payload == null) || (input.payload.length() == 0)) {
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.log(Level.WARNING, name + " received an unknown message");
                    }
                    continue;
                }

                String sCommand = input.payload.trim();
                String[] splittedCmd = sCommand.split("(\\s)+");
                if (splittedCmd.length == 0) {
                    continue;
                }

                // command
                sCommand = splittedCmd[0];
                // args
                List args = new LinkedList(Arrays.asList(splittedCmd));
                args.remove(0);

                // interpret command
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " COMMAND:" + sCommand);
                }

                if ("create_pipes".equals(sCommand)) {
                    // create pipes having owner the supplied username
                    if (args.size() < 1) {
                        XDRMessage xdrOutput = XDRMessage.getErrorMessage("Cannot understand request:" + sCommand);
                        write(xdrOutput);
                    } else {
                        logger.log(Level.INFO, "+++Creating pipes for" + args.get(0));
                        XDRMessage xdrOutput = XDRMessage.getSuccessMessage("Pipes created" + args.get(0));
                        write(xdrOutput);
                    }
                } else if ("list".equalsIgnoreCase(sCommand)) {

                    /* NOT BACKPORTED YET */

                } else if ("exec".equalsIgnoreCase(sCommand)) {
                    if (args.size() < 2) {
                        if (logger.isLoggable(Level.WARNING)) {
                            logger.log(Level.WARNING, "Bad command: EXEC \n --usage: exec module_name command [args] ");
                        }
                        // TODO - report back to client this error, continue
                        continue;
                    }
                    String sModule = (String) args.get(0);
                    String sModuleCommand = (String) args.get(1);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, " EXEC cmd for " + sModule + " CMD:" + sModuleCommand);
                    }
                    XDRMessage xdrOutput = FDTListener.this.execModuleCommand(sModule, sModuleCommand,
                            args.subList(2, args.size()));
                    write(xdrOutput);
                }
                // unknown command
                else {
                    XDRMessage xdrOutput = XDRMessage.getErrorMessage("Cannot understand request:" + sCommand);
                    write(xdrOutput);
                }
            }
            /*
             * } catch (Throwable t) { if (logger.isLoggable(Level.FINEST)) logger.log(Level.FINEST, " Fatal error in XDR session {"+name+"}...Closing it",t); else if
             * (logger.isLoggable(Level.WARNING)) logger.log(Level.WARNING, "Fatal error in XDR session {"+name+"}...Closing it",t.getMessage()); } close();
             */
        }

        @Override
        protected void notifyXDRCommClosed() {
            logger.log(Level.INFO, "SESSION closed: " + getLocalAddress() + " <->" + getInetAddress());
        }

    }// LisaXDRSSLTcpSocket

}
