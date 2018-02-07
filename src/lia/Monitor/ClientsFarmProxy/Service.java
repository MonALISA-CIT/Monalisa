/*
 * $Id: Service.java 7419 2013-10-16 12:56:15Z ramiro $
 */

package lia.Monitor.ClientsFarmProxy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.rmi.RMISecurityManager;
import java.security.PrivilegedExceptionAction;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;

import lia.Monitor.ClientsFarmProxy.MLLogger.ServiceLoggerManager;
import lia.Monitor.ClientsFarmProxy.Monitor.ExportStatistics;
import lia.Monitor.JiniSerFarmMon.MLLUSHelper;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.GenericMLEntry;
import lia.Monitor.monitor.MLJiniManagersProvider;
import lia.util.MLProcess;
import lia.util.Utils;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lookup.JoinManager;
import net.jini.lookup.ServiceDiscoveryManager;
import net.jini.lookup.ServiceIDListener;
import net.jini.lookup.entry.Name;
import apmon.ApMon;

import com.sun.jini.tool.ClassServer;

/**
 * @author mickyt
 */
public class Service implements ServiceI, ServiceIDListener {

    private static final Logger logger = Logger.getLogger(Service.class.getName());

    private LookupDiscoveryManager ldm;

    private ServiceDiscoveryManager sdm;

    private JoinManager jm;

    private static final transient Service _thisInstance;

    private final AtomicReference<ServiceItem[]> farmsServiceItemReference = new AtomicReference<ServiceItem[]>();

    private final boolean canReceiveLogs;
    private final boolean canSendMails;

    private static final AtomicLong sleepTime = new AtomicLong(60 * 1000);// in seconds

    private final long time_refresh = TimeUnit.MINUTES.toNanos(2); // in millisec.

    private final long time_refresh_apMon = TimeUnit.MINUTES.toNanos(1);

    private long last_entry_refresh;

    private long last_entry_refresh_apMon = 0;

    public volatile ServiceID proxyID = null;

    private transient ServiceID proxyOldSid = null;

    public static final String MonaLisa_version = "@version@";

    public static final String MonaLisa_vdate = "@vdate@";

    private static final long lProxyStarted = Utils.nanoNow();

    private static transient String urlFS = AppConfig.getProperty("lia.Monitor.serviceidfile",
            "${lia.Monitor.monitor.proxy_home}/.proxyService.sid").trim();

    // in seconds
    private static transient long errorTime = Long.valueOf(
            AppConfig.getProperty("lia.Monitor.serviceid.errortime", "10").trim()).longValue() * 1000;

    // otherwise got "java.rmi.NoSuchObjectException: no such object in table"
    // ?!??!
    protected ProxyService ps = null;

    protected ProxyServiceI proxy = null;

    private ExportStatistics collectMon;

    private Integer lastVal;

    private Configuration config;

    Runnable shutdownThread = new Runnable() {

        @Override
        public void run() {
            logger.log(Level.INFO, "TERMINATE - unregister proxy ");
            unregister();
        } // run
    }; // shutdownThread

    static {
        _thisInstance = new Service();
        reloadConfig();

        AppConfig.addNotifier(new AppConfigChangeListener() {
            @Override
            public void notifyAppConfigChanged() {
                reloadConfig();
            }
        });

    }

    public class LookupThread extends Thread {

        private final ServiceTemplate serviceTemplate;

        public LookupThread(final ServiceTemplate serviceTemplate) {
            this.serviceTemplate = serviceTemplate;
        }

        @Override
        public void run() {

            setName("(ML) Services LookupThread");

            for (;;) {
                try {
                    try {
                        Thread.sleep(sleepTime.get());
                    } catch (InterruptedException ie) {
                        logger.log(Level.WARNING, " LookupThread " + getName()
                                + " got interrupted exception while sleeping", ie);
                        Thread.interrupted();
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " LookupThread " + getName()
                                + " got interrupted exception while sleeping", t);
                    }

                    final long sTime = Utils.nanoNow();

                    ServiceItem[] si = null;
                    if (sdm != null) {

                        try {
                            si = sdm.lookup(serviceTemplate, 1, 5000, null, 5 * 60 * 1000);
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, " LookupThread " + getName() + " got ex in lookup", t);
                        }

                        farmsServiceItemReference.set(si);
                    } else {
                        logger.log(Level.WARNING,
                                " [ Service ] [ LookupThread ] SDM is null. No Jini lookup available yet!");
                    }

                    final long now = Utils.nanoNow();
                    final long lookupDT = now - sTime;

                    // modify generic attributes from time to time
                    if ((now - last_entry_refresh) > time_refresh) {
                        refreshGenericEntry();
                    } // if

                    if ((now - last_entry_refresh_apMon) >= time_refresh_apMon) {
                        sendApMonParams();
                    }// if

                    logger.log(Level.INFO, " [ LookupThread ] lookup took: " + TimeUnit.NANOSECONDS.toMillis(lookupDT)
                            + " ( " + TimeUnit.NANOSECONDS.toMillis(Utils.nanoNow() - sTime) + ") ms. Total services: "
                            + ((si == null) ? "N/A" : si.length));

                } catch (Throwable t) {
                    logger.log(Level.WARNING, " LookupThread " + getName() + " got ex Main Loop", t);
                }
            } // while
        } // run
    }

    public static final long getStartTime() {
        return lProxyStarted;
    }

    public static Service getInstance() {
        return _thisInstance;
    }

    public static ServiceI getServiceI() {
        return _thisInstance;
    }

    private Service() {

        canReceiveLogs = AppConfig.getb("lia.Monitor.ClientsFarmProxy.Service.canReceiveLogs", false);
        canSendMails = AppConfig.getb("lia.Monitor.ClientsFarmProxy.Service.canSendMails", false);

        String mgroup = AppConfig.getProperty("lia.Monitor.group", "ml");
        logger.log(Level.INFO, "Proxy ML Service group(s)" + mgroup);

        StringTokenizer tz = new StringTokenizer(mgroup, ",");
        int nt = tz.countTokens();
        String[] groups = new String[nt];

        if (canReceiveLogs) {
            ServiceLoggerManager.getInstance();
        }

        int ii = 0;
        while (tz.hasMoreTokens()) {
            String ss = tz.nextToken();
            groups[ii++] = ss;
        }

        // ServiceTemplate[] sts = new ServiceTemplate[groups.length];
        // for (int griter = 0; griter < groups.length; griter++) {
        // sts[griter] = new ServiceTemplate(null, new Class[] {
        // lia.Monitor.monitor.DataStore.class},
        // new Entry[] { new MonaLisaEntry(null, groups[griter])});
        // }

        // ST = new ServiceTemplate(null, new Class[] {
        // lia.Monitor.monitor.DataStore.class}, null);
        // new LookupThread(" ( ML ) LookupThread for " + mgroup, new
        // ServiceTemplate[]{ST}).start();

        // new LookupThread(" ( ML ) LookupThread for ", new
        // ServiceTemplate[]{ST}).start();

        // collectMon.start();

    } // Service

    @Override
    public void unregisterMessages(int clientID) {
        unregisterMessages(Integer.valueOf(clientID));
    }

    public void unregisterMessages(Integer clientID) {
        ServiceCommunication.unregisterMessages(clientID);
    }

    @Override
    public Vector<ServiceItem> getFarms() {
        return FarmCommunication.getFarms();
    } // getFarms

    @Override
    public Vector<ServiceItem> getFarmsByGroup(String[] groups) {
        return FarmCommunication.getFarmsByGroup(groups);
    } // getFarmsByGroup

    @Override
    public Vector<ServiceID> getFarmsIDs() {
        return ServiceCommunication.getFarmsIDs();
    }

    LookupLocator[] getLUDSs() throws Exception {

        final String luslist = AppConfig.getProperty("lia.Monitor.ClientsFarmProxy.LUSs", null);

        if (luslist == null) {
            throw new Exception("lia.Monitor.ClientsFarmProxy.LUSs is not specified");
        }

        StringTokenizer tz = new StringTokenizer(luslist, ",");
        int count = tz.countTokens();
        LookupLocator[] locators = new LookupLocator[count];

        int i = 0;
        while (tz.hasMoreTokens()) {
            String host = tz.nextToken();
            try {
                logger.log(Level.INFO, " LUS  Host = " + host);
                locators[i] = new LookupLocator("jini://" + host);
                i++;
            } catch (java.net.MalformedURLException e) {
                System.out.println("URL format error ! host=" + host + "   ");
                e.printStackTrace();
            }
        }

        if (i == count) {
            return locators;
        }

        LookupLocator[] nlocators = new LookupLocator[i];
        for (int j = 0; j < i; j++) {
            nlocators[j] = locators[j];
        }

        return nlocators;
    }

    public Entry[] getAttributes() {

        Entry[] entry = null;
        ProxyServiceEntry serviceEntry = new ProxyServiceEntry();

        String proxyGroup = AppConfig.getProperty("lia.Monitor.ClientsFarmProxy.ProxyGroup", "farm_proxy");

        serviceEntry.proxyPort = Integer.valueOf(ProxyTCPServer.getInstance().getPort());
        serviceEntry.proxyGroup = proxyGroup;

        // now set all ports
        // ProxyPortsEntry ppe = new ProxyPortsEntry();
        // ppe.proxyPorts = serviceCommunication.getPorts();

        try {

            String serviceName = AppConfig.getProperty("lia.Monitor.ClientsFarmProxy.Name");
            if (serviceName == null) {
                try {
                    serviceName = InetAddress.getLocalHost().getHostName();
                } catch (Throwable t) {
                    serviceName = "N/A";
                }
            }

            String webhost = null;
            String forceIP = AppConfig.getProperty("lia.Monitor.useIPaddress");

            if (forceIP != null) {
                webhost = forceIP;

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " HOST FORCED TO " + webhost);
                }
            }

            String ipAddress = webhost;
            if (ipAddress == null) {
                InetAddress iadr = InetAddress.getLocalHost();
                ipAddress = iadr.getHostAddress();
            }

            serviceEntry.ipAddress = ipAddress;
            entry = new Entry[3];
            GenericMLEntry gmle = new GenericMLEntry();
            lastVal = getNumberOfClients();
            gmle.hash.put("nrClients", lastVal);

            logger.log(Level.INFO, " bCanSendMails = " + canSendMails);
            gmle.hash.put(FarmWorker.EMAIL_MSG_ID, Boolean.valueOf(canSendMails));

            logger.log(Level.INFO, " bCanReceiveLogs = " + canReceiveLogs);
            gmle.hash.put(FarmWorker.MLLOG_MSG_ID, Boolean.valueOf(canReceiveLogs));

            String lat = AppConfig.getProperty("lia.Monitor.ClientsFarmProxy.LAT");
            if (lat != null) {
                gmle.hash.put("LAT", lat);
            } // LAT

            String longit = AppConfig.getProperty("lia.Monitor.ClientsFarmProxy.LONG");
            if (longit != null) {
                gmle.hash.put("LONG", longit);
            } // LONGIT

            String as = AppConfig.getProperty("lia.Monitor.ClientsFarmProxy.AS");
            if (as != null) {
                gmle.hash.put("AS", as);
            } // AS

            String net = AppConfig.getProperty("lia.Monitor.ClientsFarmProxy.netName");
            if (net != null) {
                gmle.hash.put("netName", net);
            } // AS

            String country = AppConfig.getProperty("lia.Monitor.ClientsFarmProxy.countryCode");
            if (country != null) {
                gmle.hash.put("countryCode", country);
            } // AS

            String continent = AppConfig.getProperty("lia.Monitor.ClientsFarmProxy.continentCode");
            if (continent != null) {
                gmle.hash.put("continentCode", continent);
            } // AS

            gmle.hash.put("proxyPorts", ServiceCommunication.getPorts());
            gmle.hash.put("proxyExtAddrs", ServiceCommunication.getExternalAddresses());
            gmle.hash.put("proxyExtHostname", ServiceCommunication.getHostname());

            entry[0] = serviceEntry;
            entry[1] = gmle;
            int port = ProxyTCPServer.getInstance().getPort();
            entry[2] = new Name("Proxy Service @ " + serviceName + " :- [" + ipAddress + ":" + port + "]");
            // entry[3] = ppe;
            last_entry_refresh = Utils.nanoNow();
        } catch (Throwable e) {
            logger.log(Level.WARNING, " [ Service ] getAttributes got exception. Cause: ", e);
        } // try - catch
        return entry;

    } // getAttributes

    private void refreshGenericEntry() {

        GenericMLEntry gml = null;
        GenericMLEntry gmle = new GenericMLEntry();

        Entry[] attrs = jm.getAttributes();
        if (attrs != null) {
            for (Entry attr : attrs) {
                if (attr instanceof GenericMLEntry) {
                    gml = (GenericMLEntry) attr;
                    break;
                } // if
            } // for

            if (gml != null) {
                final Hashtable<Object, Object> hash = gml.hash;
                if (hash != null) {
                    gmle.hash.clear();
                    gmle.hash.putAll(hash);
                } // if
            } // if
        } // for

        Integer val = getNumberOfClients();
        gmle.hash.put("nrClients", val);
        boolean bCanSendMails = false;
        try {
            bCanSendMails = AppConfig.getb("lia.Monitor.ClientsFarmProxy.Service.canSendMails", false);
        } catch (Throwable t) {
            bCanSendMails = false;
        }
        gmle.hash.put(FarmWorker.EMAIL_MSG_ID, Boolean.valueOf(bCanSendMails));

        gmle.hash.put(FarmWorker.MLLOG_MSG_ID, Boolean.valueOf(canReceiveLogs));

        final double load5 = collectMon.getMediatedLoad();
        gmle.hash.put("Load5", Double.valueOf(load5));

        jm.modifyAttributes(new GenericMLEntry[] { new GenericMLEntry() }, new GenericMLEntry[] { gmle });

        lastVal = val;
        last_entry_refresh = Utils.nanoNow();
    } // refreshGenercEntry

    @Override
    public void serviceIDNotify(ServiceID serviceID) {

        proxyID = serviceID;
        if (urlFS != null) {
            try {
                save();
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Unable to save SID ", t);
            }
        }
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
            logger.log(Level.FINE, "Trying to save() [ " + proxyID + " to " + urlFS + " ] ");
        }
        if (urlFS == null) {
            return;
        }

        if (proxyID == null) {
            throw new Exception(" mysid == null ");
        }

        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        boolean WRstatus = false;
        try {
            fos = new FileOutputStream(urlFS);
            oos = new ObjectOutputStream(fos);

            oos.writeObject(proxyID);
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

    public String startWeb(String dlDir) throws Exception {

        String proxyPortdl = AppConfig.getProperty("lia.Monitor.ClientsFarmProxy.ProxyPort-dl", "6002");

        Integer portNumber = Integer.valueOf(proxyPortdl);

        String webhost = null;
        String forceIP = AppConfig.getProperty("lia.Monitor.useIPaddress");

        if (forceIP != null) {
            webhost = forceIP;

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " HOST FORCED TO " + webhost);
            }
        }

        if (webhost == null) {
            InetAddress inetAddress = InetAddress.getLocalHost();
            String ipAddress = inetAddress.getHostAddress();
            webhost = ipAddress;
        }

        String codebase = "http://" + webhost + ":" + portNumber.intValue() + "/ClientsFarmProxy-dl.jar";

        (new ClassServer(portNumber.intValue(), dlDir, true, true)).start();
        logger.log(Level.INFO, "Http server started on port " + portNumber.intValue());

        return codebase;

    } // startWeb

    public void unregister() {

        if (jm != null) {
            jm.terminate();
        }// if
        logger.log(Level.SEVERE, "Proxy Service JoinManager TERMINATE FINISHED!");
    } // unregister

    @Override
    public boolean register() {

        try {
            String proxy_home = System.getProperty("lia.Monitor.monitor.proxy_home");
            if (proxy_home == null) {
                logger.log(Level.SEVERE, "proxy_home unspecified");
                return false;
            }

            String codebase = AppConfig.getProperty("lia.Monitor.ClientsFarmProxy.codebase");

            if (codebase == null) {
                codebase = startWeb(proxy_home + "/dl");
            } // if
            System.setProperty("java.rmi.server.codebase", codebase);

            logger.log(Level.INFO,
                    "\n [ Service ] [ register ] Using CODEBASE:  " + System.getProperty("java.rmi.server.codebase"));

            LookupLocator[] locators = getLUDSs();

            if ((locators == null) || (locators.length == 0)) {
                logger.log(Level.WARNING, "Unable to create any lookup locators ...");
                return false;
            }

            ldm = new LookupDiscoveryManager(null, locators, null);

            try {

                // Exporter exporter = new
                // BasicJeriExporter(TcpServerEndpoint.getInstance(7777),
                // new BasicILFactory(),
                // true);

                // export an object of this class

                // exporter.unexport (true);

                // String CONFIG_FILE = "jeri.config";

                // String[] configArgs = new String[] { CONFIG_FILE};

                // get the configuration (by default a FileConfiguration)
                // Configuration config = ConfigurationProvider.getInstance(configArgs);
                // logger.log(Level.INFO, "\nConfiguration: " + config.toString() + "\n");
                // and use this to construct an exporter
                // Exporter exporter = (Exporter) config.getEntry("lia.Monitor.ClientsFarmProxy.ProxyService",
                // "exporter", Exporter.class);
                // ps = new ProxyService(this);
                // export an object of this class
                // proxy = (ProxyServiceI) exporter.export(ps);
                // proxy = (ProxyServiceI) exporter.export(new LightProxyService());

                // logger.log(Level.INFO, "!!!!!!! ====> Proxy is " + proxy.toString());

                if (urlFS != null) {
                    try {
                        proxyOldSid = read();
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " Unable to read SID", t);
                    }
                }

                final Entry[] attrs = getAttributes();
                final LightProxyService proxy = new LightProxyService();

                proxy._key = "" + System.currentTimeMillis() + "&Up since: " + lProxyStarted + " Up: "
                        + (Utils.nanoNow() - lProxyStarted);

                if (proxyOldSid != null) {
                    logger.log(Level.INFO, " Service registering with a previous known SID: " + proxyOldSid);
                    jm = new JoinManager(proxy, attrs, proxyOldSid, ldm, new LeaseRenewalManager());
                } else {
                    logger.log(Level.INFO, " Service registering for a new SID !!! ");
                    jm = new JoinManager(proxy, attrs, this, ldm, new LeaseRenewalManager());
                }

                sdm = new ServiceDiscoveryManager(ldm, new LeaseRenewalManager());
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                }

                MLJiniManagersProvider.setManagers(ldm, sdm, jm);

                for (int i = 0; i < 120; i++) {
                    if (proxyID != null) {
                        break;
                    }
                    if (proxyOldSid != null) {
                        ServiceItem[] sis = MLLUSHelper.getInstance().getServiceItemBySID(proxyOldSid);
                        if ((sis != null) && (sis.length > 0)) {
                            if (sis[0].serviceID.equals(proxyOldSid)) {
                                proxyID = proxyOldSid;
                                break;
                            }
                        } else {
                            if (logger.isLoggable(Level.FINEST)) {
                                logger.log(Level.FINEST, " Proxy still not registered ...");
                            }
                        }
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        logger.log(Level.INFO, " Got InterruptedException wainting for SID", ie);
                        Thread.interrupted();
                    } catch (Throwable t) {
                        logger.log(Level.INFO, " Got general exception wainting for SID", t);
                    }

                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, ".....watting for sid ...");
                    }
                }

                if (proxyID != null) {
                    logger.log(Level.INFO, " Proxy Service REGISTERED !! [ " + proxyID + " ] ");
                } else {
                    logger.log(Level.SEVERE, " Unable to register proxy in LUSs ... will exit now!");
                    System.exit(1);
                }

            } catch (Throwable t) {
                logger.log(Level.INFO, " Got general exception wainting in register", t);
                return false;
            }

        } catch (Throwable t) {
            logger.log(Level.INFO, " Got general exception wainting in register", t);
            return false;
        }
        return true;
    } // register

    @Override
    public ServiceItem[] getFarmServices() {
        return farmsServiceItemReference.get();
    } // getFarmServices

    public void addParam(String param, Integer type, Object value) {
        if (collectMon != null) {
            collectMon.addParam(param, type, value);
        }
    } // addParam

    private void sendApMonParams() {
        Integer nr = getNumberOfClients();
        collectMon.addParam("nrClients", Integer.valueOf(ApMon.XDR_INT32), nr);

        int sentM = (int) ServiceCommunication.getNrSentM();
        collectMon.addParam("nrSentMsg", Integer.valueOf(ApMon.XDR_INT32), Integer.valueOf(sentM));

        int receivedM = (int) ServiceCommunication.getNrReceivedM();
        collectMon.addParam("nrReceivedMsg", Integer.valueOf(ApMon.XDR_INT32), Integer.valueOf(receivedM));

        int receivedAgentsMsg = (int) ServiceCommunication.getNrAgentsMsg();
        collectMon.addParam("nrAgentsMsg", Integer.valueOf(ApMon.XDR_INT32), Integer.valueOf(receivedAgentsMsg));

        collectMon.addParam("FreeMemory", Integer.valueOf(ApMon.XDR_REAL64),
                Double.valueOf(Runtime.getRuntime().freeMemory()));
        collectMon.addParam("TotalMemory", Integer.valueOf(ApMon.XDR_REAL64),
                Double.valueOf(Runtime.getRuntime().totalMemory()));

        collectMon.addParam("Uptime", Integer.valueOf(ApMon.XDR_REAL64),
                Double.valueOf(TimeUnit.NANOSECONDS.toSeconds((Utils.nanoNow() - lProxyStarted))));

        int farmsNr = ServiceCommunication.getFarmsNr();
        collectMon.addParam("farmsNr", Integer.valueOf(ApMon.XDR_INT32), Integer.valueOf(farmsNr));

        final Map<Integer, ClientsCommunication.ClientAccountingInfo> clientsRate = ClientsCommunication
                .getClienAccountingInfo();
        double totalClientsTraffic = 0;

        for (Map.Entry<Integer, ClientsCommunication.ClientAccountingInfo> entry : clientsRate.entrySet()) {

            final String clientName = ClientsCommunication.getClientName(entry.getKey());
            if (clientName == null) {
                continue;
            }
            ClientsCommunication.ClientAccountingInfo cInfo = entry.getValue();
            totalClientsTraffic += cInfo.totalRate;

            collectMon.addParam("client_" + clientName + "_rate", Integer.valueOf(ApMon.XDR_REAL64), cInfo.totalRate);
            collectMon.addParam("client_" + clientName + "_confsrate", Integer.valueOf(ApMon.XDR_REAL64),
                    cInfo.confRate);
        } // for

        collectMon.addParam("total_clients_traffic", Integer.valueOf(ApMon.XDR_REAL64), totalClientsTraffic);

        clientsRate.clear();

        HashMap<String, TreeMap<String, Integer>> nodesParams = ServiceCommunication.parseConfigurations();
        if (nodesParams != null) {
            final TreeMap<String, Integer> nodes = nodesParams.get("nodes");
            if (nodes != null) {
                for (final Map.Entry<String, Integer> entry : nodes.entrySet()) {

                    final String grup = entry.getKey();
                    final Integer value = entry.getValue();

                    collectMon.addParam(grup + "_nodes", Integer.valueOf(ApMon.XDR_INT32), value);
                } // for
            } // if

            final TreeMap<String, Integer> params = nodesParams.get("params");
            if (params != null) {
                for (Map.Entry<String, Integer> entry : params.entrySet()) {

                    final String grup = entry.getKey();
                    final Integer value = entry.getValue();

                    collectMon.addParam(grup + "_params", Integer.valueOf(ApMon.XDR_INT32), value);
                } // for
            } // if

        } // if

        last_entry_refresh_apMon = Utils.nanoNow();
    }

    @Override
    public Integer getNumberOfClients() {
        return ClientsCommunication.getNumberOfClients();
    }

    @Override
    public ServiceID getProxyID() {
        return proxyID;
    }

    public static void main(String args[]) {

        try {

            if ((args != null) && (args.length == 1)) {
                if ((args[0] != null) && (args[0].equalsIgnoreCase("-version") || args[0].equalsIgnoreCase("-v"))) {
                    System.out.println("\nMonALISA Proxy Version: " + MonaLisa_version + " [ " + MonaLisa_vdate
                            + " ]\n");
                    System.exit(0);
                }
            }

            logger.log(Level.INFO, " \n\nMonALISA Proxy STARTED: " + MonaLisa_version + " [ " + MonaLisa_vdate
                    + " ]\n\n ");

            //disable StringFactory intern() inside service
            try {
                final String sIntern = AppConfig.getProperty("lia.util.StringFactory.use_intern", null);

                if (sIntern == null) {
                    System.setProperty("lia.util.StringFactory.use_intern", "false");
                    logger.log(Level.FINER, " [ RegFarmMonitor ] set lia.util.StringFactory.use_intern = false");
                } else {
                    logger.log(Level.FINER, " [ RegFarmMonitor ] lia.util.StringFactory.use_intern = " + sIntern);
                }

                logger.log(
                        Level.INFO,
                        " [ RegFarmMonitor ] StringFactory useIntern() is "
                                + AppConfig.getProperty("lia.util.StringFactory.use_intern", null));
            } catch (Throwable t) {
                logger.log(Level.SEVERE, " [ RegFarmMonitor ] Unable to set lia.util.StringFactory.use_intern", t);
            }

            if (System.getSecurityManager() == null) {
                System.setSecurityManager(new RMISecurityManager());
            }

            try {
                System.setProperty("sun.net.client.defaultConnectTimeout", "30000");
                System.setProperty("sun.net.client.defaultReadTimeout", "30000");
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error setting socket connect and read timeouts", t);
            }

            try {
                System.setProperty("networkaddress.cache.ttl", "7200");// 6h
                java.security.Security.setProperty("networkaddress.cache.ttl", "7200");
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error setting IP Cache TTL", t);
            }

            Service service = Service.getInstance();

            logger.log(Level.FINE, "Start registering the service");
            String CONFIG_FILE = "jeri.config";
            String[] configArgs = new String[] { CONFIG_FILE };

            // get the configuration (by default a FileConfiguration)
            service.config = ConfigurationProvider.getInstance(configArgs);
            System.out.println("Configuration: " + service.config.toString());

            service.init();
            logger.log(Level.INFO, "Service initialized ...");

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

    } // main

    /**
     * Init the service in secure/unsecure mode
     * 
     * @throws Exception
     */
    public void init() throws Exception {

        LoginContext loginContext = (LoginContext) config.getEntry("lia.Monitor.ClientsFarmProxy.ProxyService",
                "loginContext", LoginContext.class, null);

        logger.log(Level.INFO, "[CREDENTIALS] Login finished: " + loginContext);

        if (loginContext == null) {
            logger.log(
                    Level.SEVERE,
                    "[CREDENTIALS] Login failed.Could not find a loginContext entry in <jeri.config>. I won't be able to register in secure LUSs");
            initAsSubject();
        } else {
            // credentialas loaded, so register the service using the specified
            // credentials
            loginContext.login();
            Subject.doAsPrivileged(loginContext.getSubject(), new PrivilegedExceptionActionImpl(), null);
            logger.log(Level.INFO, "[SECURE REG]Succesfully logged in !");
        }

    }

    public void initAsSubject() throws Exception {

        // ....we've setup the our communication, so let's register ourselves in
        // Reggie service
        boolean reg = register();
        if (reg == false) {
            logger.log(Level.WARNING, "Error while trying to register the service");
            throw new Exception("Error while trying to register the service");
        }

        collectMon = ExportStatistics.getInstance();

        final ServiceTemplate ST = new ServiceTemplate(null, new Class[] { lia.Monitor.monitor.DataStore.class }, null);

        new LookupThread(ST).start();

        Runtime.getRuntime().addShutdownHook(new Thread(shutdownThread));
    }

    class PrivilegedExceptionActionImpl implements PrivilegedExceptionAction<Object> {

        @Override
        public Object run() throws Exception {
            initAsSubject();
            return null;
        }
    }

    private static final void reloadConfig() {
        try {
            sleepTime.set(AppConfig.getl("lia.Monitor.ClientsFarmProxy.Service.lookupDelay", 60) * 1000L);// in seconds
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ Service ] got exception parsing lia.Monitor.sleepTime ", t);
            sleepTime.set(60 * 1000);
        }
    }

}
