/*
 * $Id: AppControl.java 7419 2013-10-16 12:56:15Z ramiro $
 */

package lia.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import lia.Monitor.DataCache.AppControlEngine;
import lia.Monitor.monitor.AppConfig;
import lia.util.Utils;
import lia.util.exporters.TCPRangePortExporter;
import lia.util.net.Util;
import lia.util.security.FarmMonitorTrustManager;

/**
 * 
 * The place holder for available and instantiated modules. It encapsulates the entire logic to load, unload, start,
 * stop or execute a module
 * 
 * @author costing, mickyt
 * 
 */
public final class AppControl {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(AppControl.class.getName());

    // holds a list with class names for available modules (found in Control/lib )
    static final ConcurrentSkipListSet<String> modules = new ConcurrentSkipListSet<String>();

    static ClassLoader cloader = (new Object()).getClass().getClassLoader();

    // holds the list with "loaded" modules.
    // The key: config_file_name
    // The value: the instance of the module ( AppInt )
    static ConcurrentHashMap<String, AppInt> loadedModules = new ConcurrentHashMap<String, AppInt>();

    static AppControl _theInstance = null;

    static SSLServerSocketFactory ssf = null;

    static SSLServerSocket ss = null;

    static String Control_HOME = null;

    static String MonaLisa_HOME = null;

    public static int appControlPort = -1;

    public static final String END_CMD_STRING = ".";

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    private AppControl() {
        try {
            (new File(Control_HOME + File.separator + "conf")).mkdirs();
        } catch (Exception e) {
        }

        try {
            (new File(Control_HOME + File.separator + "lib")).mkdirs();
        } catch (Exception e) {
        }
    }

    /**
     * Get a single instance of this class
     * 
     * @param startMainLoop :
     *            if true, the thread main loop is started, if false just the internal data structures are initialized
     *            but not the main loop (this mode is used in {@linkplain AppControlEngine}). <b>Note:</b> This may be
     *            ignored if this method is used to get a previous created instance of this class. It is effective when
     *            the method is used to initialize the singleton instance.
     * @return
     */
    public static final synchronized AppControl getInstance(boolean startMainLoop) {
        if (_theInstance == null) {
            MonaLisa_HOME = AppConfig.getProperty("MonaLisa_HOME", MonaLisa_HOME);
            Control_HOME = MonaLisa_HOME + File.separator + "Control";
            initialize();
            _theInstance = new AppControl();
            if (startMainLoop) {
                startServerSocket();
            }
        }
        return _theInstance;
    }

    synchronized public static final AppControl getInstance() {
        boolean shouldStartServer = false;
        try {
            shouldStartServer = AppConfig.getb("lia.app.AppControl.startLocalServer", false);
        } catch (Throwable t) {
            logger.log(
                    Level.WARNING,
                    " [ AppConfig ] [ HANDLED ] lia.app.AppControl.startLocalServer cannot be understand ... will not start listening server",
                    t);
        }
        return getInstance(shouldStartServer);
    }

    /**
     * Create a new SSL server socket and start listening for incoming connections.<br>
     * This is optional since the new services have the AppControl tunneled through proxy connections
     * 
     * @see AppControlEngine
     * @return
     */
    synchronized private static final void startServerSocket() {
        try {
            // set up key manager to do server authentication

            String store = AppConfig.getProperty("lia.Monitor.SKeyStore");/* "store/server.ks"; */
            String passwd = AppConfig.getProperty("lia.Monitor.SKeyStorePass", "monalisa");

            // String alias=AppConfig.getProperty("lia.Monitor.AdminUser");

            SSLContext ctx;
            KeyManagerFactory kmf;
            KeyStore ks;
            TrustManagerFactory tmf;
            char[] storepswd = passwd.toCharArray();
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

            ks.load(new FileInputStream(store), storepswd);
            try {
                Certificate[] cfs = Util.getCCB();
                for (int icfs = 0; icfs < cfs.length; icfs++) {
                    ks.setCertificateEntry("a" + icfs + "rca", cfs[icfs]);
                }
            } catch (Throwable t) {
                ks.load(new FileInputStream(store), storepswd);
            }

            kmf.init(ks, storepswd);
            tmf.init(ks);
            ctx.init(kmf.getKeyManagers(), new TrustManager[] { new FarmMonitorTrustManager(ks) }, null);
            ssf = ctx.getServerSocketFactory();
            ss = (SSLServerSocket) ssf.createServerSocket();
            appControlPort = TCPRangePortExporter.bind(ss);
            ss.setNeedClientAuth(true);
        } catch (Throwable t) {
            appControlPort = -1;
            ss = null;
        }
        // start the SSL server
        final Thread tSSLServer = new SSLServer();
        tSSLServer.setDaemon(true);
        tSSLServer.start();
    }

    /**
     * SSL server used to accept direct connections<br>
     * This was made optional since the service may use the tunnel proxy connections for AppControl communication.
     */
    private static class SSLServer extends Thread {

        public volatile boolean hasToRun = true;

        public SSLServer() {
            super("( ML ) AppControl SSL Server Thread");
        }

        @Override
        public final void run() {
            if ((ss == null) || (appControlPort == -1)) {
                hasToRun = false;
            }
            while (hasToRun) {
                try {
                    Socket s = null;
                    try {
                        s = ss.accept();
                    } catch (Throwable ex) {
                        logger.log(Level.INFO, "AppControl Got Exception main loop (handled) ====> accept ", ex);
                    }
                    if (s != null) {
                        AppChatHandler h = new AppChatHandler(s);
                        h.setDaemon(true);
                        h.start();
                    }
                    try {
                        Thread.sleep(400);
                    } catch (Exception e) {
                    }

                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "AppControl Got Exception main loop (handled)", t);
                }
            }
        }
    }

    public static Collection<AppInt> getLoadedModules() {
        return loadedModules.values();
    } // getLoadedModules

    public static final void initClassLoader() {
        File f = new File(Control_HOME + File.separator + "lib");

        File[] vf = f.listFiles();

        modules.clear();

        final ArrayList vurls = new ArrayList();

        for (int i = 0; (vf != null) && (i < vf.length); i++) {
            if (vf[i].isFile() && vf[i].getName().endsWith(".jar")) {

                URL url = null;
                URLClassLoader ucl = null;

                try {
                    // TODO - added in ML v1.2.12 (Please remove it if all MLs have greater values)
                    // very nice hack :)
                    if (vf[i].getAbsolutePath().indexOf("bash.jar") != -1) {
                        try {
                            vf[i].delete();
                            continue;
                        } catch (Throwable t1) {
                        }
                    }
                    // end very nice hack :)

                    url = new URL("file://" + vf[i].getAbsolutePath());
                    ucl = new URLClassLoader(new URL[] { url });
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "exception in urlclassloader", t);
                    continue;
                }

                try {
                    JarFile jf = new JarFile(vf[i]);

                    Enumeration e = jf.entries();

                    boolean bFound = false;

                    while (e.hasMoreElements()) {
                        JarEntry je = (JarEntry) e.nextElement();

                        if (!je.isDirectory() && je.getName().endsWith(".class")) {
                            String s = je.getName();
                            s = s.substring(0, s.length() - 6);
                            s = s.replace('/', '.');
                            s = s.replace('\\', '.');

                            Class c = ucl.loadClass(s);

                            Class[] vc = c.getInterfaces();

                            for (Class element : vc) {
                                if (element.getName().equals("lia.app.AppInt")) {
                                    modules.add(s);
                                    bFound = true;
                                    break;
                                }
                            }
                        }
                    }

                    if (bFound) {
                        vurls.add(url);
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "exception opening jar", t);
                }
            }
        }

        if (vurls.size() > 0) {
            cloader = new URLClassLoader((URL[]) vurls.toArray(new URL[vurls.size()]));
        } else {
            cloader = modules.getClass().getClassLoader();
        }
    }

    public static void initialize() {
        initClassLoader();

        Properties p = new Properties();

        AppUtils.getConfig(p, "modules.properties");

        Enumeration e = p.propertyNames();

        loadedModules.clear();

        while (e.hasMoreElements()) {
            String s = (String) e.nextElement();
            // ignore if it is in properties

            // hack only for lia.app.bash.AppBash
            try {
                if ((s != null) && (s.length() > 0)) {
                    String className = p.getProperty(s);

                    if ((className != null) && (className.length() > 0)
                            && (className.indexOf("lia.app.bash.AppBash") != -1)) {
                        continue;
                    }
                }
            } catch (Throwable t1) {
            }
            // END hack

            try {
                AppInt ai = (AppInt) (cloader.loadClass(p.getProperty(s)).newInstance());
                ai.init(s);
                loadedModules.put(s, ai);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "AppControl : exception initializing " + s, t);

                try {
                    Properties p2 = new Properties();
                    AppUtils.getConfig(p2, "modules.properties");
                    p2.remove(s);
                    Enumeration e2 = p2.propertyNames();
                    StringBuilder sb = new StringBuilder();
                    while (e2.hasMoreElements()) {
                        String str = (String) e2.nextElement();
                        sb.append(str + "=" + p2.getProperty(str) + "\n");
                    }
                    AppUtils.updateConfig("modules.properties", sb.toString());
                } catch (Throwable t2) {
                    logger.log(Level.WARNING, "AppControl : cannot autoremove this module ...");
                }
            }
        }
    }

    /**
     * @param sLine:
     *            input line read from the remote client
     * @param pw :
     *            the response stream
     */
    public void dispatch(final String sLine, final PrintWriter pw) {

        String exceptionErr = null;

        try {
            String sCommand = sLine.trim();
            String sParams = "";

            if (sCommand.indexOf(" ") > 0) {
                sParams = sCommand.substring(sCommand.indexOf(" ")).trim();
                sCommand = sCommand.substring(0, sCommand.indexOf(" ")).trim().toLowerCase();
            }

            if (sCommand.equals("help")) {
                pw.println("+OK available commands are :");
                pw.println("availablemodules");
                pw.println("loadedmodules");
                pw.println("deletemodule enc(<module name>) enc(<configuration file>)");
                pw.println("createmodule enc(<module name>) enc(<configuration file>)");
                pw.println("start enc(<module name> : <configuration file>)");
                pw.println("stop enc(<module name> : <configuration file>)");
                pw.println("restart enc(<module name> : <configuration file>)");
                pw.println("status enc(<module name> : <configuration file>)");
                pw.println("info enc(<module name> : <configuration file>)");
                pw.println("exec enc(<module name> : <configuration file>) enc(<command>)");
                pw.println("update enc(<module name> : <configuration file>) enc(<update command>)*");
                pw.println("getconfig enc(<module name> : <configuration file>)");
                pw.println("updateconfig enc(<module name> : <configuration file>) enc(<configuration file contents>)");
                pw.println("upload enc(<file name>) enc(<binary file contents>)");
                pw.println("");
                pw.println("  update command syntax :");
                pw.println("enc(<file name>) <line (from info)> enc(<key/section name (from info)>) <command> [enc(<value>)]");
                pw.println("");
                pw.println("  commands (some might do nothing on some modules):");
                pw.println("update        :  change the value for the specified key or section");
                pw.println("rename        :  change the key's or section's name");
                pw.println("delete        :  delete the specified key or section");
                pw.println("insert        :  insert a key after the specified line");
                pw.println("insertsection :  insert a section after the specified line");
                pw.println("");
                pw.println("enc = url encoding using UTF-8");
                return;
            }

            if (sCommand.equals("availablemodules")) {
                StringBuilder sb = new StringBuilder();
                sb.append("+OK available modules").append(LINE_SEPARATOR);

                final Iterator it = modules.iterator();
                while (it.hasNext()) {
                    sb.append(it.next()).append(LINE_SEPARATOR);
                }
                pw.print(sb.toString());
                return;
            }

            if (sCommand.equals("loadedmodules")) {
                StringBuilder sb = new StringBuilder();
                sb.append("+OK loaded modules").append(LINE_SEPARATOR);
                for (Object element : loadedModules.values()) {
                    final AppInt appInt = (AppInt) element;
                    String appName = null;
                    String confFile = null;
                    int status = -1;
                    try {
                        appName = appInt.getName();
                        confFile = appInt.getConfigFile();
                        status = appInt.status();
                        sb.append(appName).append(" : ").append(confFile).append(" : ").append(status)
                                .append(LINE_SEPARATOR);
                    } catch (Throwable t) {
                        exceptionErr = "[ AppCtrl :- dispatch ] got exception processing command  \"loadedmodules\" appName: "
                                + appName + " confFile: " + confFile + " status: " + status;
                        logger.log(Level.WARNING, exceptionErr, t);
                        exceptionErr += " Cause: \n" + Utils.getStackTrace(t);
                        return;
                    }
                }
                pw.print(sb.toString());
                return;
            }

            if (sCommand.equals("createmodule")) {
                String sModule = sParams.substring(0, sParams.indexOf(" ")).trim();
                String sFile = sParams.substring(sParams.indexOf(" ")).trim();

                sModule = AppUtils.dec(sModule);
                sFile = AppUtils.dec(sFile);

                if (modules.contains(sModule)) {
                    if ((sFile.indexOf("/") >= 0) || (sFile.indexOf("\\") >= 0)) {
                        pw.println("-ERR illegal file name");
                    } else {
                        File f = new File(Control_HOME + File.separator + "conf" + File.separator + sFile);

                        if (f.exists()) {
                            pw.println("-ERR this file already exists");
                            return;
                        }

                        FileWriter fw = new FileWriter(Control_HOME + File.separator + "conf" + File.separator + sFile);
                        fw.flush();
                        fw.close();

                        Properties p = new Properties();
                        AppUtils.getConfig(p, "modules.properties");
                        p.setProperty(sFile, sModule);

                        Enumeration e = p.propertyNames();

                        StringBuilder sb = new StringBuilder();
                        while (e.hasMoreElements()) {
                            String str = (String) e.nextElement();
                            sb.append(str + "=" + p.getProperty(str) + "\n");
                        }
                        AppUtils.updateConfig("modules.properties", sb.toString());

                        initialize();

                        pw.println("+OK module created");
                    }
                } else {
                    pw.println("-ERR the specified module does no exist");
                }

                return;
            }

            if (sCommand.equals("deletemodule")) {
                String sModule = sParams.substring(0, sParams.indexOf(" ")).trim();
                String sFile = sParams.substring(sParams.indexOf(" ")).trim();

                sModule = AppUtils.dec(sModule);
                sFile = AppUtils.dec(sFile);

                final AppInt ai = loadedModules.get(sFile);
                if ((ai != null) && ai.getName().equals(sModule)) {

                    loadedModules.remove(sFile);

                    Properties p = new Properties();
                    AppUtils.getConfig(p, "modules.properties");
                    p.remove(sFile);

                    Enumeration e = p.propertyNames();

                    StringBuilder sb = new StringBuilder();
                    while (e.hasMoreElements()) {
                        String str = (String) e.nextElement();
                        sb.append(str + "=" + p.getProperty(str) + "\n");
                    }
                    AppUtils.updateConfig("modules.properties", sb.toString());

                    try {
                        File f = new File(Control_HOME + File.separator + "conf" + File.separator + sFile);
                        f.delete();
                    } catch (Exception ee) {
                    }

                    pw.println("+OK module removed");
                } else {
                    pw.println("-ERR cannot find the module");
                }

                return;
            }

            if (sCommand.equals("start") || sCommand.equals("stop") || sCommand.equals("restart")
                    || sCommand.equals("status") || sCommand.equals("info") || sCommand.equals("exec")
                    || sCommand.equals("update") || sCommand.equals("getconfig") || sCommand.equals("updateconfig")) {

                String sModules = sParams;
                if (sModules.indexOf(" ") > 0) {
                    sModules = sModules.substring(0, sModules.indexOf(" ")).trim();
                    sParams = sParams.substring(sParams.indexOf(" ")).trim();
                } else {
                    sParams = "";
                }

                sModules = AppUtils.dec(sModules);

                String sName = sModules.substring(0, sModules.indexOf(":")).trim();
                String sConf = sModules.substring(sModules.indexOf(":") + 1).trim();

                AppInt ai = loadedModules.get(sConf);
                if ((ai != null) && ai.getName().equals(sName)) {
                    if (sCommand.equals("start")) {
                        if (ai.start()) {
                            pw.println("+OK started");
                        } else {
                            pw.println("-ERR cannot start");
                        }
                    } else if (sCommand.equals("restart")) {
                        if (ai.restart()) {
                            pw.println("+OK restarted");
                        } else {
                            pw.println("-ERR cannot restart");
                        }
                    } else if (sCommand.equals("stop")) {
                        if (ai.stop()) {
                            pw.println("+OK stopped");
                        } else {
                            pw.println("-ERR cannot stop");
                        }
                    } else if (sCommand.equals("status")) {
                        pw.println("+OK status is");
                        pw.println("" + ai.status());
                    } else if (sCommand.equals("info")) {
                        pw.println("+OK info");
                        pw.println(ai.info());
                    } else if (sCommand.equals("exec")) {
                        pw.println("+OK exec");
                        // aici trebuie tratat cazul in care output-ul continue o linie cu "."
                        String ex = ai.exec(AppUtils.dec(sParams));
                        if (ex != null) {
                            while (ex.indexOf("\n.\n") >= 0) {
                                ex = ex.substring(0, ex.indexOf("\n.\n")) + "\n..\n"
                                        + ex.substring(ex.indexOf("\n.\n") + "\n.\n".length());
                            }
                        }
                        pw.println(ex);
                    } else if (sCommand.equals("update")) {
                        StringTokenizer st = new StringTokenizer(sParams, " ");

                        ArrayList list = new ArrayList();

                        while (st.hasMoreTokens()) {
                            list.add(AppUtils.dec(st.nextToken()));
                        }

                        if ((list.size() > 0) && ai.update((String[]) list.toArray(new String[list.size()]))) {
                            pw.println("+OK update ok");
                        } else {
                            pw.println("-ERR error updating");
                        }
                    } else if (sCommand.equals("getconfig")) {
                        pw.println("+OK configuration follows");
                        pw.println(AppUtils.enc(ai.getConfiguration()));
                    } else if (sCommand.equals("updateconfig")) {
                        if (ai.updateConfiguration(AppUtils.dec(sParams))) {
                            pw.println("+OK configuration updated ok");
                        } else {
                            pw.println("-ERR configuration could not be updated");
                        }
                    }
                } else {
                    pw.println("-ERR the specified module is not running");
                }

                return;
            }

            if (sCommand.equals("upload")) {
                String sFile = sParams.substring(0, sParams.indexOf(" ")).trim();
                String sContents = sParams.substring(sParams.indexOf(" ")).trim();

                sFile = AppUtils.dec(sFile);
                sContents = AppUtils.dec(sContents);

                if ((sFile == null) || (sFile.length() <= 0) || !sFile.endsWith(".jar") || (sContents == null)
                        || (sContents.length() <= 0)) {
                    throw new IOException("");
                }

                if ((sFile.indexOf("/") >= 0) || (sFile.indexOf("\\") >= 0)) {
                    throw new IOException("");
                }

                FileOutputStream fos = new FileOutputStream(Control_HOME + File.separator + "lib" + File.separator
                        + sFile);
                StringReader sr = new StringReader(sContents);

                char vc[] = new char[1024];
                byte vb[] = new byte[1024];
                int r = 0;

                do {
                    r = sr.read(vc, 0, vc.length);

                    for (int i = 0; i < r; i++) {
                        vb[i] = (byte) vc[i];
                    }

                    fos.write(vb, 0, r);
                } while (r == vc.length);

                fos.flush();
                fos.close();

                pw.println("+OK file uploaded");

                // re-initialize the modules
                initialize();

                return;
            }

            pw.println("-ERR unknown command");
        } catch (Throwable t) {
            exceptionErr = "[ AppCtrl :- dispatch ] got exception processing command " + sLine;
            logger.log(Level.WARNING, "exceptionErr", t);
            exceptionErr += " Cause: " + Utils.getStackTrace(t);

        } finally {

            if (exceptionErr != null) {// try to notify the client ...
                try {
                    pw.println("-ERR error parsing your query");
                    pw.println(exceptionErr);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " [ AppControl :- dispatch FINALLY !! ] "
                            + "error trying to send exceptionErr", t);
                }
            }

            try {
                pw.println(END_CMD_STRING);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[ AppControl :- dispatch FINALLY !! ] error notifying end cmd \".\" ", t);
            }

            try {
                pw.flush();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[ AppControl :- dispatch FINALLY !! ] error notifying pw.flush() ", t);
            }
        }

    }

    public final static void main(String args[]) throws Exception {
        AppControl ac = getInstance();

        Object o = new Object();

        synchronized (o) {
            o.wait();
        }

    }

    private static final class AppChatHandler extends Thread {

        BufferedReader br;

        PrintWriter pw;

        Socket sock;

        boolean binit;

        public AppChatHandler(Socket s) {
            super(" ( ML ) AppChatHandler " + s.getInetAddress());
            binit = true;
            try {
                sock = s;

                br = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                pw = new PrintWriter(new OutputStreamWriter(sock.getOutputStream()));

                // sock.setSoTimeout(10*1000);

            } catch (Throwable e) {
                logger.log(Level.SEVERE, "AppChatHandler exception in constructor", e);
                close();
                binit = false;
            }
        }

        private final void close() {
            try {
                br.close();
            } catch (Throwable ignore) {
            }

            try {
                pw.flush();
            } catch (Throwable ignore) {
            }

            try {
                pw.close();
            } catch (Throwable ignore) {
            }

            try {
                sock.close();
            } catch (Throwable ignore) {
            }
        }

        @Override
        public final void run() {
            if (!binit) {
                logger.log(Level.WARNING, "handler : cannot run, init failed");
                return;
            }

            String s = null;

            try {
                while ((s = br.readLine()) != null) {
                    // interpret the command and send response
                    AppControl.getInstance().dispatch(s, pw);
                }
            } catch (Exception e) {
                // do not show it
                // logger.log(Level.WARNING, "AppControl : exception while reading : ", e);
            }
            close();
        }

    }

}
