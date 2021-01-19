package lia.util.logging.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.RMISecurityManager;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.Subject;

import lia.Monitor.JiniSerFarmMon.MLLUSHelper;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.GenericMLEntry;
import lia.Monitor.monitor.MLJiniManagersProvider;
import lia.util.MLProcess;
import lia.util.security.MLLogin;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.config.EmptyConfiguration;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.discovery.DiscoveryEvent;
import net.jini.discovery.DiscoveryListener;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lookup.JoinManager;
import net.jini.lookup.ServiceDiscoveryEvent;
import net.jini.lookup.ServiceDiscoveryListener;
import net.jini.lookup.ServiceDiscoveryManager;
import net.jini.lookup.ServiceIDListener;
import net.jini.lookup.entry.Name;

public class MLLoggerJiniService extends Thread implements ServiceIDListener, ServiceDiscoveryListener,
        DiscoveryListener {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(MLLoggerJiniService.class.getName());

    private static MLLoggerJiniService _thisInstance;

    private final AtomicBoolean active;

    public volatile ServiceID loggerID = null;
    private transient ServiceID loggerOldID = null;

    protected transient LookupDiscoveryManager ldm;

    protected transient LeaseRenewalManager lrm;

    protected transient JoinManager jmngr;

    protected transient ServiceDiscoveryManager sdm;

    private transient Subject subject;

    private static transient String urlFS = AppConfig.getProperty("lia.Monitor.serviceidfile",
            "${lia.Monitor.monitor.logger_home}/.logger.sid").trim();
    // in seconds
    private static transient long errorTime = AppConfig.getl("lia.Monitor.serviceid.errortime", 10 * 1000);

    private MLLoggerJiniService() {
        super("( ML ) - LoggerJiniService");
        active = new AtomicBoolean(true);
    }

    public static synchronized MLLoggerJiniService getInstance() {
        if (_thisInstance == null) {
            try {
                _thisInstance = new MLLoggerJiniService();
                _thisInstance.start();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Could NOT instantiate MLLoggerJiniService ", t);
                _thisInstance = null;
            }
        }
        return _thisInstance;
    }

    /** return the codebase for this service, based on user properties */
    private String getCodebase() {
        String codebase = "";
        String dlURLs = AppConfig.getProperty("lia.util.logging.service.dlURLs",
                "http://monalisa.cern.ch/MONALISA/LOGGER_ML");

        StringTokenizer st = new StringTokenizer(dlURLs, ",");
        if ((st != null) && (st.countTokens() > 0)) {
            while ((st != null) && st.hasMoreTokens()) {
                codebase += (st.nextToken() + "/dl/MLLogger-dl.jar" + (st.hasMoreTokens() ? " " : ""));
            }
        }
        return codebase;
    }

    private void register() throws Exception {
        LookupLocator[] luss = getLUDSs();
        StringBuilder sb = new StringBuilder();
        sb.append("\n\nUsing LUSs: ");
        if ((luss == null) || (luss.length == 0)) {// normaly we should have DEFAULT_LUDs
            logger.log(Level.SEVERE, "luss == null!?!??!?!");
        } else {
            for (int i = 0; i < luss.length; i++) {
                LookupLocator locator = luss[i];
                sb.append("\nLUS " + i + ": " + locator.getHost());
            }
        }

        logger.log(Level.INFO, sb.toString());

        try {

            String codebase = getCodebase(); // startWeb (topoPath + "/dl");
            System.setProperty("java.rmi.server.codebase", codebase);
            // System.setProperty ("export.codebase",codebase);
            logger.log(Level.INFO, "using codebase: " + codebase);

            // setup configuration used by Jini helpers
            String cfg_file = AppConfig.getProperty("lia.util.logging.service.jiniconfig");
            Configuration config;
            if (cfg_file == null) {
                config = EmptyConfiguration.INSTANCE;
            } else {
                String[] configArgs = new String[] { cfg_file };
                config = ConfigurationProvider.getInstance(configArgs);
            }

            MLLoggerJiniProxy ps = new MLLoggerJiniProxy();
            long now = System.currentTimeMillis();
            ps.rTime = Long.valueOf(now);
            ps.rDate = new Date(now);
            ldm = new LookupDiscoveryManager(null, luss, this, config);

            try {
                Thread.sleep(5000);
            } catch (Exception e) {
            }

            Entry[] attrSet = getAttributes();
            this.lrm = new LeaseRenewalManager(config);

            if (urlFS != null) {
                try {
                    loggerOldID = read();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " Unable to read SID", t);
                }
            }

            if (loggerOldID != null) {
                logger.log(Level.INFO, " Service registering with a previous known SID: " + loggerOldID);
                this.jmngr = new JoinManager(ps, attrSet, loggerOldID, ldm, lrm, config);
            } else {
                logger.log(Level.INFO, " Service registering for a new SID !!! ");
                this.jmngr = new JoinManager(ps, attrSet, this, ldm, lrm, config);
            }

        } catch (Throwable e) {
            logger.log(Level.SEVERE, " Got Exception trying to register in LUS ... will  stop!!", e);
            System.exit(1);
        }

        sdm = new ServiceDiscoveryManager(ldm, new LeaseRenewalManager());
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
        }

        MLJiniManagersProvider.setManagers(ldm, sdm, this.jmngr);

        for (int i = 0; i < 100; i++) {
            if (loggerID != null) {
                break;
            }
            if (loggerOldID != null) {
                ServiceItem[] sis = MLLUSHelper.getInstance().getServiceItemBySID(loggerOldID);
                if ((sis != null) && (sis.length > 0)) {
                    if (sis[0].serviceID.equals(loggerOldID)) {
                        loggerID = loggerOldID;
                        break;
                    }
                } else {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "[ LoggerJiniService ] still not registered ...");
                    }
                }
            }
            try {
                Thread.sleep(1000);
            } catch (Throwable t) {
            }

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, ".....watting for sid ...");
            }
        }

        if (loggerID != null) {
            logger.log(Level.INFO, " Logger Service REGISTERED !! [ " + loggerID + " ] ");
        } else {
            logger.log(Level.SEVERE, " Unable to register Logger in LUSs ... will exit now!");
            System.exit(2);
        }
    }

    private Entry[] getAttributes() {
        GenericMLEntry gmle = new GenericMLEntry();

        String port = AppConfig.getProperty("lia.util.logging.service.port");
        String host = AppConfig.getProperty("lia.util.logging.service.useAddress");

        if (host != null) {
            gmle.hash.put("hostname", host);
            gmle.hash.put("port", port);
        }

        Name name = new Name("MLLogger Service [ " + host + ":" + port + " ] ");
        return new Entry[] { gmle, name };
    }

    @Override
    public void run() {
        try {
            // set security manager
            if (System.getSecurityManager() == null) {
                System.setSecurityManager(new RMISecurityManager());
            }
            register();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got exception while registering: ", t);
            active.set(false);
        }
        while (active.get()) {
            try {
                Thread.sleep(10 * 1000);
            } catch (Throwable t) {//ignore it
            }
        }
        finishIT();
    }

    private void finishIT() {
        if (jmngr != null) {
            try {
                jmngr.terminate();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Exc while unregistering from LUSs", t);
            }
        }
    }

    private LookupLocator[] getLUDSs() {
        String[] luslist = AppConfig.getVectorProperty("lia.Monitor.LUSs", "");
        if ((luslist == null) || (luslist.length == 0)) {
            return null;
        }

        ArrayList<LookupLocator> locators = new ArrayList<LookupLocator>();

        for (String host : luslist) {
            try {
                locators.add(new LookupLocator("jini://" + host));
            } catch (java.net.MalformedURLException e) {
                logger.log(Level.WARNING, "URL format error ! host=" + host + "   \n", e);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "General Exception adding LUS for address " + host, t);
            }
        }

        if (locators.size() > 0) {
            return locators.toArray(new LookupLocator[locators.size()]);
        }

        return null;
    }

    public void stopIt() {
        this.active.set(false);
    }

    /**
     * @see net.jini.lookup.ServiceIDListener#serviceIDNotify(net.jini.core.lookup.ServiceID)
     */
    @Override
    public void serviceIDNotify(ServiceID serviceID) {
        loggerID = serviceID;
        if (urlFS != null) {
            try {
                save();
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Unable to save SID ", t);
            }
        }
        logger.log(Level.INFO, " [ MLLoggerJiniService ] [ serviceIDNotify ] Service registered [ " + loggerID + " ] ");
    }

    /**
     * @see net.jini.lookup.ServiceDiscoveryListener#serviceAdded(net.jini.lookup.ServiceDiscoveryEvent)
     */
    @Override
    public void serviceAdded(ServiceDiscoveryEvent event) {
    }

    /**
     * @see net.jini.lookup.ServiceDiscoveryListener#serviceRemoved(net.jini.lookup.ServiceDiscoveryEvent)
     */
    @Override
    public void serviceRemoved(ServiceDiscoveryEvent event) {
        logger.log(Level.INFO, " Service removed " + event.getSource());
    }

    /**
     * @see net.jini.lookup.ServiceDiscoveryListener#serviceChanged(net.jini.lookup.ServiceDiscoveryEvent)
     */
    @Override
    public void serviceChanged(ServiceDiscoveryEvent event) {
        logger.log(Level.INFO, " Service changed " + event.getSource());
    }

    /**
     * @see net.jini.discovery.DiscoveryListener#discovered(net.jini.discovery.DiscoveryEvent)
     */
    @Override
    public void discovered(DiscoveryEvent e) {
        logger.log(Level.INFO, " LUS Service discovered " + e.getSource());
    }

    /**
     * @see net.jini.discovery.DiscoveryListener#discarded(net.jini.discovery.DiscoveryEvent)
     */
    @Override
    public void discarded(DiscoveryEvent e) {
        logger.log(Level.WARNING, " LUS Service discared " + e.getSource());
    }

    /**
     * @return Returns the subject.
     */
    public Subject getSubject() {
        return this.subject;
    }

    /**
     * @param subject
     *            The subject to set.
     */
    public void setSubject(Subject subject) {
        this.subject = subject;
    }

    private ServiceID read() throws Exception {

        if (urlFS == null) {
            return null;
        }
        File SIDFile = null;
        try {
            SIDFile = new File(urlFS);
            if (!SIDFile.exists() || !SIDFile.canRead() || !SIDFile.canWrite() || !SIDFile.isFile()) {
                logger.log(Level.INFO, "[BasicService] [HANDLED] Cannot read SIDFile [ " + urlFS + "] [" + "[ Exists: "
                        + SIDFile.exists() + " isFile: " + SIDFile.isFile() + " canRead: " + SIDFile.canRead()
                        + " canWrite: " + SIDFile.canWrite() + " ]");
                return null;
            }
        } catch (Throwable t) {
            logger.log(Level.INFO, "Got exception while probing for SID File", t);
            return null;
        }

        ServiceID sid = null;
        FileInputStream fis = null;
        ObjectInputStream ois = null;

        try {
            long lastModified = SIDFile.lastModified();

            fis = new FileInputStream(urlFS);
            ois = new ObjectInputStream(fis);

            sid = (ServiceID) ois.readObject();
            String iNodeF = (String) ois.readObject();
            Date lastWrite = (Date) ois.readObject();

            // Write i-node info
            Process p = MLProcess.exec(new String[] { "ls", "-i", urlFS });
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = null;
            String iNode = null;
            while ((line = br.readLine()) != null) {
                if (line.indexOf(urlFS) != -1) {
                    String[] splitLine = line.split("\\s+");
                    iNode = splitLine[0];
                }
            }
            p.waitFor();

            long lastSIDWrite = lastWrite.getTime();
            if ((lastSIDWrite == lastModified)
                    || ((lastSIDWrite < lastModified) && ((lastSIDWrite + errorTime) > lastModified))
                    || ((lastSIDWrite > lastModified) && ((lastSIDWrite - errorTime) < lastModified))) {// it's
                // OK
                if ((iNodeF != null) && (iNode != null) && iNode.equals(iNodeF)) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Got SID [ " + sid + " ] " + "from file [ " + urlFS + " ] \n"
                                + "[ fTime = " + new Date(lastModified) + " SIDTime = " + new Date(lastSIDWrite)
                                + " iNode = " + iNode + " ]");
                    }
                } else {
                    sid = null;
                }
            } else {
                logger.log(Level.INFO, "Different SID time. [ fTime = " + new Date(lastModified) + " SIDTime = "
                        + new Date(lastSIDWrite) + " ]");
                sid = null;
            }
        } catch (Throwable t) {
            logger.log(Level.INFO, "Got exception trying to read SID ", t);
            sid = null;
        } finally {
            try {
                if (ois != null) {
                    ois.close();
                }
            } catch (Throwable t1) {
            }
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (Throwable t1) {
            }
        }

        if (sid == null) {

            boolean status = false;
            try {
                status = SIDFile.delete();
            } catch (Throwable t) {
            }
            logger.log(Level.INFO, "Reading NULL SID from SIDFile [ " + urlFS
                    + " ] (maybe first time ... ?) DLT_STATUS = " + status);
        }
        return sid;

    }

    private void save() throws Exception {

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Trying to save() [ " + loggerID + " to " + urlFS + " ] ");
        }
        if (urlFS == null) {
            return;
        }

        if (loggerID == null) {
            throw new Exception(" mysid == null ");
        }

        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        boolean WRstatus = false;
        try {
            fos = new FileOutputStream(urlFS);
            oos = new ObjectOutputStream(fos);

            oos.writeObject(loggerID);
            oos.flush();

            Process p = MLProcess.exec(new String[] { "/bin/sh", "-c", "ls -i " + urlFS });
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = null;
            String iNode = null;
            while ((line = br.readLine()) != null) {
                if (line.indexOf(urlFS) != -1) {
                    String[] splitLine = line.split("\\s+");
                    iNode = splitLine[0];
                }
            }
            p.waitFor();
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "iNode = " + iNode);
            }
            if (iNode != null) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Writing iNode " + iNode);
                }
                oos.writeObject(iNode);
            }
            oos.writeObject(new Date(System.currentTimeMillis()));
            oos.flush();
            WRstatus = true;
        } catch (Throwable t) {
            logger.log(Level.INFO, "Cannot write SID to file", t);
            WRstatus = false;
        } finally {
            try {
                if (oos != null) {
                    oos.close();
                }
            } catch (Throwable t1) {
            }
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (Throwable t1) {
            }
        }

        File SIDFile = null;
        try {
            SIDFile = new File(urlFS);
            if (!SIDFile.exists() || !SIDFile.canRead() || !SIDFile.canWrite() || !SIDFile.isFile()) {
                logger.log(
                        Level.INFO,
                        "Problems writing SIDFile [ " + urlFS + "] [" + "[ Exists: " + SIDFile.exists() + " isFile: "
                                + SIDFile.isFile() + " canRead: " + SIDFile.canRead() + " canWrite: "
                                + SIDFile.canWrite() + " ]");
                return;
            }
        } catch (Throwable t) {
            logger.log(Level.INFO, "Got exception while probing for SID File", t);
            return;
        }

        if (!WRstatus) {
            boolean status = false;
            try {
                status = SIDFile.delete();
            } catch (Throwable t) {
            }
            logger.log(Level.FINE, "WRStatus == false ...trying to invalidate SIDFile [ " + urlFS + " ] DLT_STATUS = "
                    + status);
        } else {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Write SID ... ok!!");
            }
        }
    }

    public static void main(String args[]) {
        System.out.println("Starting MLLogger jini service...");
        boolean useSecLUSs = Boolean.valueOf(AppConfig.getProperty("lia.util.logging.service.useSecureLUSs", "false"))
                .booleanValue();
        if (useSecLUSs) {
            logger.log(Level.INFO, "Use Secure LUSs");
            try {
                /*
                 * set trustStore to empty string to accept any certificate in a SSL session (we don't want to
                 * authenticate the server (LUS)
                 */
                System.setProperty("javax.net.ssl.trustStore", "");

                /*
                 * gather private key and certificate chain from files
                 */
                String privateKeyPath = AppConfig.getProperty("lia.util.logging.service.privateKeyFile",
                        "/etc/grid-security/hostkey.pem");
                String certChainPath = AppConfig.getProperty("lia.util.logging.service.certChainFile",
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

                Subject.doAsPrivileged(ctxSubject, new PrivilegedAction<Object>() {

                    @Override
                    public Object run() {
                        MLLoggerJiniService tjs = MLLoggerJiniService.getInstance();
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
            MLLoggerJiniService tjs = MLLoggerJiniService.getInstance();
            tjs.setDaemon(false);
            tjs.start();
        }
    }

}
