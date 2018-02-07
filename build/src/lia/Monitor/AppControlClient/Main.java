package lia.Monitor.AppControlClient;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppControlClient;
import lia.Monitor.tcpClient.MLSerClient;
import lia.util.Utils;

public class Main extends WindowAdapter implements CommunicateMsg, AppControlClient {
    /** The Logger */
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    SSLSocketFactory ssf = null;
    SSLSocket s = null;
    PrintWriter pw;
    public BufferedReader br;

    private MLSerClient tclient = null;
    private final Object syncProxyComm = new Object();
    private AtomicBoolean usingProxyComm = null;
    private PipedWriter rcvCmds;

    public ModulesMainFrame cgi;

    public Main() {
        cgi = new ModulesMainFrame(this);
        if (cgi != null) {
            cgi.addWindowListener(this);
        }
    } //constructor

    public boolean initProxyComm(MLSerClient tclient) {
        this.tclient = tclient;
        if (Utils.compareVersion(tclient.mlVersion, "1.6.13") < 0) {
            return false;
        }
        tclient.addAppControlClient(this);
        synchronized (syncProxyComm) {
            if (usingProxyComm == null) {
                // we don't know yet if we use Proxy for this... wait for the response
                try {
                    logger.log(Level.INFO, "Trying to initialize AppControl communication through proxy");
                    syncProxyComm.wait();
                } catch (InterruptedException ex) {
                    logger.log(Level.WARNING, "Interrupted while waiting for response on AppControl -> initProxyComm");
                    return false;
                }
            }
        }
        if (usingProxyComm.get()) {
            rcvCmds = new PipedWriter();
            try {
                br = new BufferedReader(new PipedReader(rcvCmds));
                logger.log(Level.INFO, "AppControl communicates through proxy.");
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error creating pipes...", ex);
                return false;
            }
        }
        return usingProxyComm.get();
    }

    @Override
    public void windowClosed(WindowEvent e) {
        if (tclient != null) {
            tclient.deleteAppControlClient(this);
        }
        if (cgi != null) {
            try {
                cgi.dispose();
                cgi = null;
            } catch (Throwable t) {

            }
        }

        if (br != null) {
            try {
                br.close();
            } catch (Throwable tt) {
            }
        }

        if (pw != null) {
            try {
                pw.close();
            } catch (Throwable tt) {
            }
        }

        if (s != null) {
            try {
                s.close();
            } catch (Throwable t) {

            }
        }
    }

    public boolean createSSLConnection(String address, int port) throws Exception {

        // set up key manager to do server authentication

        String store = AppConfig.getProperty("lia.Monitor.KeyStore");
        String passwd = AppConfig.getProperty("lia.Monitor.KeyStorePass");

        if ((store == null) || (passwd == null)) {
            return false;
        }

        SSLContext ctx;
        KeyManagerFactory kmf;
        KeyStore ks;
        char[] storepswd = passwd.toCharArray();
        ctx = SSLContext.getInstance("TLS");

        /* IBM or Sun vm ? */
        if (System.getProperty("java.vm.vendor").toLowerCase().indexOf("ibm") != -1) {
            kmf = KeyManagerFactory.getInstance("IBMX509", "IBMJSSE");
        } else {
            kmf = KeyManagerFactory.getInstance("SunX509");
        }

        ks = KeyStore.getInstance("JKS");

        ks.load(new FileInputStream(store), storepswd);
        kmf.init(ks, storepswd);
        ctx.init(kmf.getKeyManagers(), new TrustManager[] { new lia.util.security.FarmMonitorTrustManager() }, null);
        ssf = ctx.getSocketFactory();
        s = (SSLSocket) ssf.createSocket();

        try {
            s.connect(new InetSocketAddress(address, port));
            pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
            br = new BufferedReader(new InputStreamReader(s.getInputStream()));
        } catch (Throwable t) {
            logger.warning("Failed opening AppControl SSL connection to " + address + ":" + port);
        }
        return true;
    } //createSSLConnection

    @Override
    public void sendCommand(String command) {
        synchronized (syncProxyComm) {
            if ((usingProxyComm != null) && usingProxyComm.get()) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Sending AppControl command: " + command);
                }
                tclient.sendAppControlCmd(this, command, null);
            } else {
                pw.println(command);
                pw.flush();
            }
        }
    } //sendCommand

    @Override
    public String receiveResponseLine() throws Exception {
        return br.readLine();
    } //receiveResponseLine

    public static Main getInstance(MLSerClient tclient, String host, int port) {
        Main mainObject = new Main();
        try {
            boolean useProxyComm = false;
            if (tclient != null) {
                useProxyComm = mainObject.initProxyComm(tclient);
            }
            if (!useProxyComm) {
                if (port != -1) {
                    if (!mainObject.createSSLConnection(host, port)) {
                        return null;
                    }
                } else {
                    return null;
                }
            }

            // get available modules
            String str = "";
            try {
                mainObject.sendCommand("loadedmodules");
                // wait for a response
                str = mainObject.receiveResponseLine();
            } catch (Throwable t) {
                logger.warning(t.getMessage());
                return mainObject = null;
            }
            if (str.startsWith("+OK")) { // a correct response
                while (((str = mainObject.br.readLine()) != null) && !str.equals(".")) {
                    mainObject.cgi.addModule(str);
                } //while
            } else { //an error occured
                System.out.println("error : " + str);
            } // if - else 
            //            mainObject.cgi.showGUI(); 
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Failed to initialize the secure connection with the farm", t);
            mainObject = null;
        }
        return mainObject;

    }

    public void showWindow() {
        if (cgi != null) {
            try {
                cgi.refreshModulesTree();
            } catch (Exception e) {
                e.printStackTrace();
            }
            cgi.setVisible(true);
        }
    }

    /** status of the AppControl communication through proxy is known */
    @Override
    public void appControlStatus(MLSerClient mlSerTClient, boolean status) {
        logger.log(Level.FINE, "AppControl status: " + status);
        synchronized (syncProxyComm) {
            usingProxyComm = new AtomicBoolean(status);
            if (status == false) {
                if (rcvCmds != null) {
                    try {
                        rcvCmds.close();
                    } catch (IOException e) {
                        logger.log(Level.FINE, "Error closing the rcvCmds pipe", e);
                    }
                    rcvCmds = null;
                }
            }

            syncProxyComm.notify();
        }
    }

    /** received a command result through proxy communication */
    @Override
    public void cmdResult(MLSerClient mlSerTClient, Long cmdID, String message, Object params) {
        try {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Received message: " + message);
            }
            rcvCmds.write(message);
            rcvCmds.flush();
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error receiving messages", ex);
            try {
                if (rcvCmds != null) {
                    rcvCmds.close();
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error closing pipe...", e);
            }
            rcvCmds = new PipedWriter();
            try {
                br = new BufferedReader(new PipedReader(rcvCmds));
                logger.log(Level.INFO, "Pipes recreated fine.");
            } catch (IOException ioe) {
                logger.log(Level.SEVERE, "Error creating pipes...", ioe);
            }
        }
    }

    public static void main(String args[]) throws Exception {

        Main mainObject = Main.getInstance(null, "localhost", 9005);
        if (mainObject != null) {
            mainObject.showWindow();
        } else {
            System.exit(1);
        }
    }
} //Main
