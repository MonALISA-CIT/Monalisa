package lia.Monitor.JiniSerFarmMon;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.rmi.NoSuchObjectException;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.security.PrivilegedExceptionAction;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Agents.BService.BasicService;
import lia.Monitor.DataCache.Cache;
import lia.Monitor.DataCache.ProxyWorker;
import lia.Monitor.Farm.FarmMonitor;
import lia.Monitor.Farm.ABPing.ABPingFastReply;
import lia.Monitor.modules.monABPing;
import lia.Monitor.monitor.ABPingEntry;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.DataStore;
import lia.Monitor.monitor.EmbeddedAppEntry;
import lia.Monitor.monitor.ExtendedSiteInfoEntry;
import lia.Monitor.monitor.GenericMLEntry;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MLControlEntry;
import lia.Monitor.monitor.MLJiniManagersProvider;
import lia.Monitor.monitor.MonaLisaEntry;
import lia.Monitor.monitor.MonitorClient;
import lia.Monitor.monitor.MonitorFilter;
import lia.Monitor.monitor.MonitorUnit;
import lia.Monitor.monitor.ShutdownReceiver;
import lia.Monitor.monitor.SiteInfoEntry;
import lia.Monitor.monitor.monPredicate;
import lia.app.AppInt;
import lia.util.MLProcess;
import lia.util.MLSignalHandler;
import lia.util.SecureContextExecutor;
import lia.util.ShutdownManager;
import lia.util.Utils;
import lia.util.exporters.RegistryRangePortExporter;
import lia.util.ntp.NTPDate;
import lia.util.threads.MonALISAExecutors;
import lia.util.update.AppProperties;
import lia.util.update.AppRemoteURLUpdater;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lookup.JoinManager;
import net.jini.lookup.entry.Name;

/**
 * The main class for MonALISA Service
 * 
 * @author Iosif Legrand
 * @author ramiro
 */
public class RegFarmMonitor extends BasicService implements Runnable, DataStore, ShutdownReceiver {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(RegFarmMonitor.class.getName());

    // remember the uptime :) ... hope you will have a long and happy life
    private static volatile long ML_START_TIME;

    private static final transient long ML_START_TIME_NANO;

    private long ltvLUS = 0; // Jini's dumb "proxy"

    private static final NoImplProxy proxy = new NoImplProxy();

    private long lastLUSErrorMailSent = 0;

    private long lastJiniMgrRestart = 0;

    private static boolean jniDebug = false;

    private static final AtomicLong LUS_VERIFY_DELAY = new AtomicLong(10 * 60 * 1000);// 10min

    private static final AtomicLong LUS_MAIL_DELAY = new AtomicLong(12 * 60 * 60 * 1000); // 12h

    private static final AtomicLong JINIMGR_RESTART_DELAY = new AtomicLong(4 * 60 * 60 * 1000); // 4h

    private static boolean shouldExportRMIInterface;

    private static boolean shouldStartAdminInterface;

    private static String lockFSFile; // how often to check for updates

    private static final int CHECK_FOR_UPDATES_DELAY;

    public static transient int REGISTRY_PORT = Registry.REGISTRY_PORT; // 1099

    public static boolean isVRVS;

    private final transient String name;

    private static transient String FarmHOME;

    private static volatile FarmMonitor farmMonitor;

    private static final transient AtomicLong verifyJMgrCount = new AtomicLong(0);

    MonitorUnit monitorUnit;

    DataStore dataStore;

    GenericMLEntry gmle = new GenericMLEntry(); // private transient static boolean debug = false;

    private static final transient boolean SHOULD_UPDATE;

    private transient static long verifDelta = 30; // Embedded Applications Entries

    private transient Hashtable previousEntries = new Hashtable();

    private int asResolverErrors = 0;

    private transient AtomicBoolean isShutDown = new AtomicBoolean(false);

    private static final AtomicBoolean bVerifyLUS = new AtomicBoolean(true);

    static {

        ML_START_TIME_NANO = Utils.nanoNow();

        boolean shouldUPDATE = AppConfig.getb("lia.Monitor.update", false);
        String[] updateURLs = null;
        if (shouldUPDATE) {
            updateURLs = AppConfig.getVectorProperty("lia.monitor.updateURLs");
        }

        final List<URL> urls = new LinkedList<URL>();
        if ((updateURLs == null) || (updateURLs.length == 0)) {
            shouldUPDATE = false;
        } else {
            shouldUPDATE = false;
            for (String updateURL : updateURLs) {
                if (updateURL != null) {
                    try {
                        urls.add(new URL(updateURL));
                        shouldUPDATE = true;
                    } catch (Throwable t) {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, " Unable to parse url: " + updateURL);
                        }
                    }
                }
            }
        }

        int tmpVal = 10;
        try {
            tmpVal = AppConfig.geti("lia.Monitor.check_for_updates_delay", 10);
        } catch (Throwable t) {
            tmpVal = 10;
        }

        CHECK_FOR_UPDATES_DELAY = tmpVal;
        shouldUPDATE = (urls.size() > 0);
        SHOULD_UPDATE = shouldUPDATE;
    }

    private static final void reloadTimes() {

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ RegFarmMonitor ] reloadTimes() ");
        }

        try {
            final long tmpVal = AppConfig.getl("lia.Monitor.JiniSerFarmMon.JINIMGR_RESTART_DELAY", ((isVRVS) ? 1
                    : (4 * 60))) * 60 * 1000;
            JINIMGR_RESTART_DELAY.set(tmpVal);
        } catch (Throwable t) {
            JINIMGR_RESTART_DELAY.set(((isVRVS) ? 1 : (4 * 60)) * 60 * 1000);
        }
        try {
            final long tmpVal = AppConfig.getl("lia.Monitor.JiniSerFarmMon.LUS_VERIFY_DELAY", (isVRVS) ? 1 : 10) * 60 * 1000;
            LUS_VERIFY_DELAY.set(tmpVal);
        } catch (Throwable t) {
            LUS_VERIFY_DELAY.set(10 * 60 * 1000);
        }

        try {
            final long tmpVal = AppConfig.getl("lia.Monitor.JiniSerFarmMon.LUS_MAIL_DELAY", ((isVRVS) ? 60 : (6 * 60))) * 60 * 1000;
            LUS_MAIL_DELAY.set(tmpVal);
        } catch (Throwable t) {
            LUS_MAIL_DELAY.set(((isVRVS) ? 60 : (6 * 60)) * 60 * 1000);
        }

        try {
            final boolean tmpVal = AppConfig.getb("lia.Monitor.JiniSerFarmMon.CHECK_JINI", true);
            bVerifyLUS.set(tmpVal);
        } catch (Throwable t) {
            logger.log(Level.WARNING,
                    " [ RegFarmMonitor ] Unable to determine lia.Monitor.JiniSerFarmMon.CHECK_JINI; cause:", t);
            bVerifyLUS.set(false);
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "[ RegFarmMonitor ]  lia.Monitor.JiniSerFarmMon.CHECK_JINI = " + bVerifyLUS.get()
                    + "; lia.Monitor.JiniSerFarmMon.JINIMGR_RESTART_DELAY="
                    + (JINIMGR_RESTART_DELAY.get() / (60 * 1000)) + " min"
                    + "; lia.Monitor.JiniSerFarmMon.LUS_VERIFY_DELAY=" + (LUS_VERIFY_DELAY.get() / (60 * 1000))
                    + " min");
        }

        final String sLevel = AppConfig.getProperty("lia.Monitor.JiniSerFarmMon.RegFarmMonitor.level");
        Level loggingLevel = null;
        if (sLevel != null) {
            try {
                loggingLevel = Level.parse(sLevel);
            } catch (Throwable t) {
                loggingLevel = null;
            }

            logger.setLevel(loggingLevel);
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [ RegFarmMonitor ] reloadedConf. Logging level: " + loggingLevel);
        }

    }

    private static final AtomicBoolean internalRestarting = new AtomicBoolean(false);

    public static final boolean internalRestarting() {
        return internalRestarting.get();
    }

    public static final boolean restartML() {
        if (internalRestarting.compareAndSet(false, true)) {
            try {
                final String MonaLisa_HOME = AppConfig.getProperty("MonaLisa_HOME");
                StringBuilder sb = new StringBuilder();
                sb.append("#!/bin/bash").append("\n\n");
                sb.append("#\n");
                sb.append("#restart ML from java script").append("\n");
                sb.append("#\n");
                sb.append("sleep 5\n");
                sb.append(
                        MonaLisa_HOME + File.separator + "Service" + File.separator + "CMD" + File.separator
                                + "ML_SER ").append("restart ").append(" 0<&- 1>&- 2>&-");
                sb.append("\n");
                final File f = File.createTempFile("MonALISA_restart", ".sh");
                f.deleteOnExit();
                FileWriter fw = null;
                BufferedWriter bw = null;
                try {
                    fw = new FileWriter(f);
                    bw = new BufferedWriter(fw);
                    bw.write(sb.toString());
                    bw.flush();
                    f.setExecutable(true, true);
                    f.setReadable(true, true);
                    f.setWritable(true, false);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "(restartML) Unable to create file: '" + f + "' Cause: ", t);
                    return false;
                } finally {
                    Utils.closeIgnoringException(bw);
                    Utils.closeIgnoringException(fw);
                }

                ProcessBuilder pb = new ProcessBuilder(f.getAbsolutePath());
                logger.log(Level.INFO, "[ Restarting ML for update ] trying cmd " + pb.command());
                final Process restartProc = pb.start();
                Utils.closeIgnoringException(restartProc.getInputStream());
                Utils.closeIgnoringException(restartProc.getOutputStream());
                Utils.closeIgnoringException(restartProc.getErrorStream());
                logger.log(Level.INFO,
                        "[ Restarting ML for update ] cmd sent; all process I/O streams closed ... waiting for restart");
                return true;
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Exception restarting ML", t);
                internalRestarting.set(false);
            }
        }

        return false;
    }

    private static final class UpdateVerifier implements Runnable {

        private static final String MY_VERSION = "@mlversionshort@";

        private static final String MY_BUILDID = "@mlbuildid@";

        private final File UPDATE_FILE = new File(FarmHOME + File.separator + "UPDATE");

        private final String[] args;

        private final File cacheDir;

        private final File destDir;

        UpdateVerifier() {
            final String cacheDirS = AppConfig.getProperty("CACHE_DIR");
            this.cacheDir = new File(cacheDirS);
            if (!this.cacheDir.exists()) {
                if (!cacheDir.mkdirs()) {
                    logger.log(Level.WARNING, "!!! CONFIG ERROR !!! Unable to create updater cache dir: " + cacheDir);
                }
            } else {
                logger.log(Level.WARNING, "Updater cache dir: " + cacheDir + " exists");
            }

            final String destDirS = AppConfig.getProperty("DEST_DIR");
            this.destDir = new File(destDirS);

            if (!this.destDir.exists()) {
                if (!destDir.mkdirs()) {
                    logger.log(Level.WARNING, "!!! CONFIG ERROR !!! Unable to create updater DEST dir: " + destDir);
                }
            } else {
                logger.log(Level.WARNING, "Updater dest dir: " + destDir + " exists");
            }

            final String urls = AppConfig.getProperty("URL_LIST_UPDATE");
            args = new String[] { "-cachedir", cacheDir.getAbsolutePath(), "-destdir", destDir.getAbsolutePath(),
                    "-jnlps", urls };
            logger.log(Level.INFO, "UpdateVerifierTask - Using cacheDir:" + cacheDir.getAbsolutePath() + " destDir: "
                    + destDir.getAbsolutePath() + " urls: " + urls);
        }

        private final void notifyUpdate() {
            try {
                UPDATE_FILE.createNewFile();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[ UpdateVerifier ] Failed to notify update. Unable to create file: "
                        + UPDATE_FILE, t);
            }
        }

        @Override
        public void run() {
            final long sTime = System.nanoTime();
            try {
                if (AppConfig.getb("force_restart", false)) {
                    notifyUpdate();
                    if (restartML()) {
                        logger.log(Level.INFO, " ML will be restarted");
                    }
                }
                if (!Cache.internalStoreStarted()) {
                    logger.log(Level.INFO, "[ UpdateVerifier ] iStore not started, delay update verify ");

                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "[ UpdateVerifier ] iStore not started, delay update verify ");
                    }
                    return;
                }

                AppRemoteURLUpdater urlUpdater = new AppRemoteURLUpdater(args, false);
                AppProperties appProps = null;
                try {
                    appProps = urlUpdater.getRemoteAppProperties(AppConfig.getVectorProperty("URL_LIST_UPDATE"),
                            cacheDir, "MLService");
                    final String remoteVersion = appProps.appVersion;
                    final String remoteBuildID = appProps.appBuildID;

                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, " Remote version " + remoteVersion + " myVersion " + MY_VERSION
                                + " remotebuildID: " + remoteBuildID + " myBuildID: " + MY_BUILDID);
                    }
                    if ((remoteVersion != null) && (MY_VERSION != null) && (remoteBuildID != null)
                            && (MY_BUILDID != null)) {
                        if (remoteVersion.equals(MY_VERSION) && remoteBuildID.equals(MY_BUILDID)) {
                            if (logger.isLoggable(Level.FINEST)) {
                                logger.log(Level.INFO, " Same version. No update needed");
                            }
                            return;
                        }
                        logger.log(Level.INFO, " New update: Remote version " + remoteVersion + " myVersion "
                                + MY_VERSION + " remotebuildID: " + remoteBuildID + " myBuildID: " + MY_BUILDID);
                    } else {
                        return;
                    }

                    // update needed
                    if (urlUpdater.doUpdate(true)) {
                        // the updater throws exception in case it fails
                        notifyUpdate();
                        restartML();
                    } else {
                        logger.log(Level.WARNING, " Unable to sync local cache. Will try again ...");
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " Unable to fetch app properties from remote URLs", t);
                }
            } finally {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE,
                            " Updater task finished in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sTime)
                                    + " ms");
                }
            }

        }
    }

    private static final class FarmAliveTask implements Runnable {

        final File f = new File(FarmHOME + File.separator + "ALIVE");

        @Override
        public void run() {
            try {
                f.createNewFile();
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(
                            Level.FINE,
                            " [ RegFarmMonitor ] [ FarmAlive Task ] [ HANDLED ] FAILED TO ANNOUNCE ALIVE TO THE WRAPPER ",
                            t);
                }
            }
        }
    }

    /**
     * This is used to determine my geographic location using the topology service(s).
     * If successful, the information is published in the LUSs, as a GenericMLEntry.
     */
    class AsResolverTask implements Runnable {

        final TreeSet<String> topoServices = new TreeSet<String>();

        String as = null;

        String net = null;

        String country = null;

        String continent = null;

        String LONG = null;

        String LAT = null;

        public AsResolverTask() {
            MLLUSHelper.getInstance().forceUpdate();
            MLLUSHelper.getInstance().getTopologyServices();
            MLLUSHelper.getInstance().forceUpdate();
        }

        /** get the address(es) of the topology service(s) */
        public void getGeoServiceAddress() {
            ServiceItem[] si = MLLUSHelper.getInstance().getTopologyServices();
            if ((si == null) || (si.length == 0) || (si[0].attributeSets.length == 0)) {
                logger.log(Level.INFO, "No Geo service was found (yet)");
                topoServices.clear();
                asResolverErrors++;
            } else {
                for (ServiceItem element : si) {
                    GenericMLEntry gmle = (GenericMLEntry) element.attributeSets[0];
                    if (gmle.hash != null) {
                        final String baseUrl = (String) gmle.hash.get("URL");
                        if (!topoServices.contains(baseUrl)) {
                            topoServices.add(baseUrl);
                            logger.log(Level.INFO, "Adding Topo service at " + baseUrl); // URL
                        }
                    }
                }
            }
        }

        /** Query the FindIP servlet of all available topology services to get AS and NET. */
        private void getASandNET(String ipAddress) {
            for (Iterator<String> tsi = topoServices.iterator(); tsi.hasNext() && (as == null);) {
                String allQuery = tsi.next() + "/FindIP?" + ipAddress;
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Querying topo service to get AS and NET:\n" + allQuery);
                }

                BufferedReader br = null;
                InputStreamReader is = null;

                try {
                    URLConnection uconn = new URL(allQuery).openConnection();
                    uconn.setDefaultUseCaches(false);
                    uconn.setUseCaches(false);
                    is = new InputStreamReader(uconn.getInputStream());
                    br = new BufferedReader(is);
                    String line = null;
                    while (((line = br.readLine()) != null) && !line.equals("")) {
                        // read data 'till end of file or empty line received
                        line = line.replaceAll("[ ]+", " ");
                        String key = line.substring(0, line.indexOf(":")).toLowerCase();
                        if ((as == null) && key.equalsIgnoreCase("origin")) {
                            as = line.substring(line.indexOf("AS") + 2);
                        }
                        if ((net == null) && key.equalsIgnoreCase("netname")) {
                            net = line.substring(line.indexOf(":") + 2);
                        }
                    }
                } catch (Throwable ex) {
                    logger.log(Level.WARNING, "Error getting AS", ex);
                } finally {
                    Utils.closeIgnoringException(br);
                    Utils.closeIgnoringException(is);
                }
            }
            if (as == null) {
                asResolverErrors++;
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Failed to get AS from any of the queried topo services...");
                }
            }
        }

        /** Query the FindAS servlet of all available topology services to get the location data */
        private String getGeo(String as) {
            for (String string : topoServices) {
                String allQuery = string + "/FindAS?" + as;
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Querying topo service to get Geographic data:\n" + allQuery);
                }

                BufferedReader br = null;
                InputStreamReader is = null;

                try {
                    URLConnection uconn = new URL(allQuery).openConnection();
                    uconn.setDefaultUseCaches(false);
                    uconn.setUseCaches(false);
                    is = new InputStreamReader(uconn.getInputStream());
                    br = new BufferedReader(is);
                    String line = null;
                    while (((line = br.readLine()) != null) && !line.equals("")) {
                        // read data 'till end of file or empty line received
                        line = line.replaceAll("[ ]+", " ");
                        br.close();
                        return line;
                    }
                } catch (Throwable ex) {
                    logger.log(Level.WARNING, "Error getting Geo data", ex);
                } finally {
                    Utils.closeIgnoringException(br);
                    Utils.closeIgnoringException(is);
                }
            }
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Failed to get Geo data from any of the queried topo services...");
            }
            asResolverErrors++;
            return null;
        }

        private String getCountry(String geo) {
            return geo.substring(geo.length() - 5, geo.length() - 3);
        }

        private String getContinent(String geo) {
            return geo.substring(geo.length() - 2);
        }

        private String getLONG(String geo) {
            int i = geo.indexOf('\t', geo.indexOf('\t') + 1) + 1;
            int j = geo.indexOf('\t', i);
            return geo.substring(i, j);
        }

        private String getLAT(String geo) {
            int i = geo.indexOf('\t') + 1;
            int j = geo.indexOf('\t', i);
            return geo.substring(i, j);
        }

        @Override
        public void run() {
            try {
                String IPAddress = dataStore.getIPAddress();
                if (IPAddress == null) {
                    logger.log(Level.SEVERE, "I don't know my own IP address!. Cancelling AsResolverTask");
                    asResolver.cancel(false);
                    asResolver = null;
                }
                getGeoServiceAddress();
                if (topoServices.size() > 0) {
                    getASandNET(IPAddress);
                    if (as != null) {
                        String geo = getGeo(as);
                        if ((geo != null) && (geo.length() >= 5)) {
                            country = getCountry(geo);
                            continent = getContinent(geo);
                            LONG = getLONG(geo);
                            LAT = getLAT(geo);
                        }
                        synchronized (gmle.hash) {
                            if ((as != null) && (as.length() > 0)) {
                                gmle.hash.put("AS", as);
                            }
                            if ((net != null) && (net.length() > 0)) {
                                gmle.hash.put("NET", net);
                            }
                            if ((country != null) && (country.length() > 0)) {
                                gmle.hash.put("COUNTRY", country);
                            }
                            if ((continent != null) && (continent.length() > 0)) {
                                gmle.hash.put("CONTINENT", continent);
                            }
                            if ((LONG != null) && (LONG.length() > 0)) {
                                gmle.hash.put("LONG", LONG);
                            }
                            if ((LAT != null) && (LAT.length() > 0)) {
                                gmle.hash.put("LAT", LAT);
                            }

                            if (!gmle.hash.isEmpty()) {
                                try {
                                    GMLEPublisher.getInstance().publishNow(gmle.hash);
                                    logger.log(
                                            Level.INFO,
                                            "Attr Published : [ AS = " + gmle.hash.get("AS") + " NET = "
                                                    + gmle.hash.get("NET") + " CONTINENT = "
                                                    + gmle.hash.get("CONTINENT") + " COUNTRY = "
                                                    + gmle.hash.get("COUNTRY") + " LONG = " + gmle.hash.get("LONG")
                                                    + " LAT = " + gmle.hash.get("LAT") + " ]");
                                } catch (Throwable t) {
                                    asResolverErrors++;
                                    logger.log(Level.WARNING, "Could not publish WHOIS Network Info in LUSs", t);
                                    return;
                                }
                            }

                        }
                        // at least partially, we were successful. Terminate this timer task.
                        asResolver.cancel(false);
                        asResolver = null;
                        return;
                    }
                }
            } catch (Throwable t) {
                asResolverErrors++;
                logger.log(Level.WARNING, "Error in asResolver", t);
            }
            if (asResolverErrors > 10) {
                logger.log(Level.INFO, "Too many errors in getting AS/Geo data. Giving up.");
                asResolver.cancel(false);
                asResolver = null;
            }
        }
    }

    ScheduledFuture asResolver = null;

    private String[] args;

    /**
     * @return Service uptime in millis since 1970 ( hopefully ... if NTPDate works fine)
     */
    public static final long getServiceStartTime() {
        return ML_START_TIME;
    }

    /**
     * @return Service uptime in millis ( it should be more reliable ... somehow based on local system nanoTime)
     */
    public static final long getServiceUpTime() {
        return TimeUnit.NANOSECONDS.toMillis(Utils.nanoNow() - ML_START_TIME_NANO);
    }

    @Override
    public String getIPAddress() throws java.rmi.RemoteException {
        return dataStore.getIPAddress();
    }

    @Override
    public String getUnitName() throws java.rmi.RemoteException {
        return dataStore.getUnitName();
    }

    public RegFarmMonitor(final String args[]) throws Exception {
        // debug =
        // Boolean.valueOf(AppConfig.getProperty("lia.Monitor.debug","false")).booleanValue();

        if ((args == null) || (args.length == 0)) {
            logger.log(Level.SEVERE, "[ RegFarmMonitor ] [<init>] Arguments cannot be null!");
            System.exit(1);
        }

        if ((args[0] == null) || (args[0].length() == 0)) {
            logger.log(Level.SEVERE, "[ RegFarmMonitor ] [<init>] The service name cannot be null");
            System.exit(1);
        }

        this.name = args[0];
        System.setProperty("MonALISA_ServiceName", this.name);

        if (args.length <= 1) {
            logger.log(Level.SEVERE, "[ RegFarmMonitor ] [<init>] No config file specified");
            System.exit(1);
        }

        for (int i = 1; i < args.length; i++) {
            args[i] = "file:" + args[i];
        }
        SecureContextExecutor.getInstance().execute(new PrivilegedExceptionAction<Object>() {

            /*
             * - if register() set up the subject then start the following threads
             * in a privileged context. - the main thread performs update actions on
             * joinmanager, so, it needs a privileged context in order to
             * authenticate to LUSs
             */

            @Override
            public Object run() {
                MonALISAExecutors.getMLHelperExecutor().scheduleWithFixedDelay(new FarmAliveTask(), 0, verifDelta,
                        TimeUnit.SECONDS);
                if (SHOULD_UPDATE) {
                    MonALISAExecutors.getMLHelperExecutor().scheduleWithFixedDelay(new UpdateVerifier(), 1,
                            CHECK_FOR_UPDATES_DELAY, TimeUnit.MINUTES);
                }
                GMLEPublisher.getInstance();
                registerSdHook();
                register(args);
                startMainThread();
                return null;
            }
        });
    }

    private void startMainThread() {
        (new Thread(this, "(ML) JiniSerFarmMon")).start();
    }

    /*
     * Starts the maint thread and register the shutdown hook
     */
    private void registerSdHook() {
        try {
            if (!jniDebug) {
                MLSignalHandler mlsh = MLSignalHandler.getInstance();
                if (mlsh != null) {
                    mlsh.addModule(this);
                } else {
                    ShutdownManager sm = ShutdownManager.getInstance();
                    sm.addModule(this);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Unable to register shutdown hook", t);
        }
    }

    public int getRegistryPort() {
        return REGISTRY_PORT;
    }

    /**
     * @throws java.rmi.RemoteException
     */
    public int getMLPort() throws java.rmi.RemoteException {
        return Cache.server.lis_port;
    }

    /**
     * @throws RemoteException
     */
    public String getName() throws RemoteException {
        return name;
    }

    @Override
    public Entry[] getAttributes() {

        Name nameEntry = new Name(name);

        String gg = AppConfig.getProperty("lia.Monitor.group", "test");
        if ((gg == null) || (gg.length() == 0)) {
            gg = "test";
        }

        String splitedGroups[] = Utils.getSplittedListFields(gg);
        StringBuilder sbg = new StringBuilder();
        for (int i = 0; i < splitedGroups.length; i++) {
            sbg.append(splitedGroups[i]).append((i < (splitedGroups.length - 1)) ? "," : "");
        }

        logger.log(Level.INFO, "[ RegFMonitor ] Lookup groups: [" + sbg.toString() + "]");
        MonaLisaEntry mle = new MonaLisaEntry(name, sbg.toString());

        mle.Location = AppConfig.getProperty("MonaLisa.Location", "N/A");
        mle.Country = AppConfig.getProperty("MonaLisa.Country", "N/A");
        mle.LAT = AppConfig.getProperty("MonaLisa.LAT", "N/A");
        mle.LONG = AppConfig.getProperty("MonaLisa.LONG", "N/A");
        mle.SiteUrl = AppConfig.getProperty("MonaLisa.SiteUrl", null);
        mle.IconUrl = AppConfig.getProperty("MonaLisa.IconUrl", null);

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "ICON WEB = " + WebAddress + "/MyIcon.gif");
            logger.log(Level.FINER, "ML_PORT = " + Cache.server.lis_port);
        }

        SiteInfoEntry sie = new SiteInfoEntry();
        sie.ML_PORT = Integer.valueOf(Cache.server.lis_port);
        sie.REGISTRY_PORT = Integer.valueOf(REGISTRY_PORT);

        sie.IPAddress = null;
        sie.UnitName = farmMonitor.getMFarm().name;

        try {
            sie.IPAddress = dataStore.getIPAddress();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Unable to get local IP address");
        }

        if (logger.isLoggable(Level.FINER)) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n\n>>>>>Sie Entry<<<<\n\n");
            sb.append("  LIS PORT = ").append(sie.ML_PORT);
            sb.append("\n  IP = ").append(sie.IPAddress);
            sb.append("\n  REGISTRY_PORT = ").append(sie.REGISTRY_PORT);
            sb.append("\n  UNIT_NAME = ").append(sie.UnitName);
            sb.append("\n\n>>>>>End Sie Entry<<<<\n\n");
            logger.log(Level.FINER, sb.toString());
        }

        // ABPingEntry
        ABPingEntry abpe = null;
        if (monABPing.shouldPublishABPE) {
            abpe = new ABPingEntry();
            try {
                abpe.PORT = Integer.valueOf(ABPingFastReply.PORT);
                abpe.IPAddress = monABPing.myIPaddress;
                abpe.FullHostName = monABPing.myFullHostname;
                abpe.HostName = monABPing.myHostname;
            } catch (Throwable t) {
            }
        }

        // AppControl Entry
        MLControlEntry mlce = new MLControlEntry();
        mlce.ControlPort = Integer.valueOf(FarmMonitor.appControlPort);
        System.out.println("MLCP = " + mlce.ControlPort);

        ExtendedSiteInfoEntry esie = new ExtendedSiteInfoEntry();

        esie.localContactName = AppConfig.getProperty("MonaLisa.ContactName", "N/A");
        esie.localContactEMail = AppConfig.getProperty("MonaLisa.ContactEmail", "N/A");

        try {
            esie.JVM_VERSION = "java.vm.version: " + System.getProperty("java.vm.version") + " java.version: "
                    + System.getProperty("java.version");
        } catch (Throwable t) {
            // ignore if
        }

        InputStream out = null;
        BufferedReader br = null;
        Process pro = null;
        try {
            pro = MLProcess.exec(new String[] { "ldd", "--version" });
            out = pro.getInputStream();
            br = new BufferedReader(new InputStreamReader(out));
            String line = null;

            while ((line = br.readLine()) != null) {
                int i = line.indexOf(" ");
                if ((i >= 0) && (line.indexOf("ldd") >= 0)) {
                    esie.LIBC_VERSION = line.substring(i + 1);
                }
            }

            pro.waitFor();
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Cannot determine libc version", t);
            }
        } finally {
            if (pro != null) {
                try {
                    pro.destroy();
                } catch (Throwable ignore) {
                    // ignore
                }
            }
            Utils.closeIgnoringException(out);
            Utils.closeIgnoringException(br);
        }

        esie.LIBC_VERSION += "\nSHOULD_UPDATE =";
        boolean readFlag = false;

        // try to see if the ml_env has the SHOULD_UPDATE flag set to true
        try {
            String MonaLisa_HOME = AppConfig.getProperty("MonaLisa_HOME", null);
            String ml_env_dir = AppConfig.getGlobalEnvProperty("CONFDIR", MonaLisa_HOME + File.separator + "Service"
                    + File.separator + "CMD");
            if (ml_env_dir != null) {
                br = null;
                FileReader fr = null;

                try {
                    fr = new FileReader(ml_env_dir + File.separator + "ml_env");
                    br = new BufferedReader(fr);

                    for (;;) {
                        String line = br.readLine();
                        if (line == null) {
                            break;
                        }
                        int dc = line.indexOf("#");
                        int ds = line.indexOf("SHOULD_UPDATE");
                        int de = line.indexOf("=");

                        if ((ds != -1) && ((dc == -1) || (ds < dc))) {
                            if (de > ds) {
                                String flag = line.substring(de + 1);
                                readFlag = true;
                                esie.LIBC_VERSION += flag;
                                break;
                            }
                        }
                    }
                } finally {
                    Utils.closeIgnoringException(fr);
                    Utils.closeIgnoringException(br);
                }
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ RegFarmMonitor ] Unable to parse ml_env file", t);
            }
        }
        if (!readFlag) {
            esie.LIBC_VERSION += " N/A";
        }
        asResolver = MonALISAExecutors.getMLHelperExecutor().scheduleWithFixedDelay(new AsResolverTask(), 5, 30,
                TimeUnit.SECONDS);

        if (abpe != null) {
            return new Entry[] { nameEntry, mle, sie, esie, abpe, mlce, gmle };
        }

        return new Entry[] { nameEntry, mle, sie, esie, mlce, gmle };
    }

    private void forcedStop() {
        try {
            new File(FarmHOME + File.separator + ".ml.pid").delete();
            new File(FarmHOME + File.separator + "ALIVE").delete();
        } catch (Throwable t) {
            // ignore it
        }
        System.exit(0);
    }

    public static final long getVerifyJMgrCount() {
        return verifyJMgrCount.get();
    }

    // It was a BUG in Jini2 which was fixed in Jini2_002.
    // After repeated network failures...the service does not (re)registered
    // This function could be removed ... when all MLs updates to 1.2.12.
    //
    // The problem is still there for "latest" version of Jini 2.1 :(
    //
    private void verifyLUSs() {
        if ((ltvLUS + TimeUnit.MILLISECONDS.toNanos(LUS_VERIFY_DELAY.get())) < Utils.nanoNow()) {// just do the
                                                                                                 // job :)
            verifyJMgrCount.incrementAndGet();
            long sTime = Utils.nanoNow();
            boolean foundMe = false;

            JoinManager jmngr = MLJiniManagersProvider.getJoinManager();
            LookupDiscoveryManager ldm = MLJiniManagersProvider.getLookupDiscoveryManager();

            if ((jmngr == null) || (ldm == null)) {
                return;
            }
            StringBuilder sb = new StringBuilder(8192);
            try {
                // which are the current LUSs with which I am registered
                ServiceRegistrar[] jmgrSR = jmngr.getJoinSet();
                // which are the current LUSs to which I have a "reference"(the proxy is local)
                ServiceRegistrar[] ldmSR = ldm.getRegistrars();
                // I am looking for what LUSs ?
                LookupLocator[] ldmLL = ldm.getLocators();

                if ((jmgrSR != null) && (ldmSR != null) && (ldmLL != null)) {

                    boolean deepFound = ((jmgrSR.length > 0) && (jmgrSR.length == ldmLL.length));

                    if (deepFound) {
                        for (ServiceRegistrar element : jmgrSR) {

                            boolean found = false;
                            for (LookupLocator element2 : ldmLL) {
                                if (element.getLocator().toString().equals(element2.toString())) {
                                    found = true;
                                    break;
                                }
                            }// for()

                            if (!found) {
                                sb.append("\n\n =======> The JoinManager's LL ")
                                        .append(element.getLocator().toString());
                                sb.append(" was not found though LDM LL's <=======\n\n");
                                deepFound = false;
                                break;
                            }
                        }// for()

                        try {
                            for (int i = 0; i < 30; i++) {
                                final ServiceItem[] sis = MLLUSHelper.getInstance().getServiceItemBySID(mySid);
                                if ((sis != null) && (sis.length > 0)) {
                                    if (sis[0].serviceID.equals(mySid)) {
                                        foundMe = true;
                                        break;
                                    }
                                }

                                if (logger.isLoggable(Level.FINER)) {
                                    logger.log(Level.FINER, " [ verifyLUSs ] not found myself in the LUSs ... retrying");
                                }

                            }
                        } catch (Throwable tfind) {
                            sb.append("\n\n GENERAL EXC in verifyLUSs looking for myself in the LUSs: \n"
                                    + Utils.getStackTrace(tfind));
                        } finally {
                            if (!foundMe) {
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException ieing) {
                                    // we'll retry in 1second
                                }
                                sb.append("\n Unable to find myself in the LUSs");
                                if ((lastJiniMgrRestart + TimeUnit.MILLISECONDS.toNanos(JINIMGR_RESTART_DELAY.get())) < Utils
                                        .nanoNow()) {
                                    sb.append("\n\n Restarting managers ... Unable to find myself in the lookup");
                                    String status = null;
                                    try {
                                        status = restartJiniManagers(proxy);
                                    } catch (Throwable ex) {
                                        status = "\nCaught exception while REtrying to restartJiniManagers ex:\n"
                                                + Utils.getStackTrace(ex);
                                    }
                                    sb.append("\n\n Restarting JiniManagers ... \n\n\n").append(status);
                                    lastJiniMgrRestart = Utils.nanoNow();
                                } else {
                                    sb.append("\n\n Restarting JiniManagers ... delayed ...");
                                }

                                if (logger.isLoggable(Level.FINER)) {
                                    logger.log(Level.FINER,
                                            "\n [ verifyLUSs ] -> Status ( !foundMe ) \n" + sb.toString());
                                }

                                sendLUSMail(sb.toString());
                            }
                        }
                    }

                    if (deepFound) {// everything ok
                        lastJiniMgrRestart = Utils.nanoNow();
                        if (logger.isLoggable(Level.FINER)) {
                            sb.append("\nJoin Manager - Service Registrar(s):\n");
                            for (ServiceRegistrar element : jmgrSR) {
                                LookupLocator ll = element.getLocator();
                                sb.append("\n " + ll.toString() + " [ " + ll.getHost() + ":" + ll.getPort() + " ]");
                            }
                            sb.append("\n");
                            logger.log(Level.FINER, "[ verifyLUSs ] -> Status ( OK )\n" + sb.toString()
                                    + "\n foundMe ( " + foundMe + " ): " + mySid);
                        }
                        return;
                    }
                }

                if ((jmgrSR == null) || (jmgrSR.length == 0)) {
                    sb.append("\n The service has no LUSs with which it is registered");
                } else {
                    sb.append("\nJoin Manager - Service Registrar(s):\n");
                    for (ServiceRegistrar element : jmgrSR) {
                        LookupLocator ll = element.getLocator();
                        sb.append("\n " + ll.toString() + " [ " + ll.getHost() + ":" + ll.getPort() + " ]");
                    }
                    sb.append("\n");
                }

                if ((ldmSR == null) || (ldmSR.length == 0)) {
                    sb.append("\n The Lookup Discovery Manager has no LUSs with which it is registered ( network failure ?)");
                } else {
                    sb.append("\nLookup Discovery Manager - Service Registrar(s):\n");
                    for (ServiceRegistrar element : ldmSR) {
                        LookupLocator ll = element.getLocator();
                        sb.append("\n " + ll.toString() + " [ " + ll.getHost() + ":" + ll.getPort() + " ]");
                    }
                }

                if ((ldmLL == null) || (ldmLL.length == 0)) {
                    sb.append("\n The Lookup Discovery Manager has no LUSs to search for!?!?? ( no LUS defined ! verify ml.properties )");
                } else {
                    sb.append("\nLookup locator(s):\n");
                    for (LookupLocator ll : ldmLL) {
                        sb.append("\n " + ll.toString() + " [ " + ll.getHost() + ":" + ll.getPort() + " ]");
                    }
                }

                if ((lastJiniMgrRestart + TimeUnit.MILLISECONDS.toNanos(JINIMGR_RESTART_DELAY.get())) < Utils.nanoNow()) {// I
                                                                                                                          // should
                                                                                                                          // restart
                                                                                                                          // the
                                                                                                                          // managers

                    lastJiniMgrRestart = Utils.nanoNow();
                    String status = null;
                    try {
                        status = restartJiniManagers(proxy);
                    } catch (Throwable ex) {
                        status = "\nCaught exception while trying to restartJiniManagers:\n" + Utils.getStackTrace(ex);
                    }

                    sb.append("\n\n Restarting JiniManagers ... \n\n\n").append(status);
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "\n [ verifyLUSs ] -> Restarted managers: \n" + sb.toString());
                    }
                    sendLUSMail(sb.toString());

                } else {// if(lastJiniMgrRestart)
                    sb.append("\n\n Restarting JiniManagers ... delayed ...");
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "\n [ verifyLUSs ] -> Status ( NOT OK ) \n" + sb.toString());
                    }
                }

            } catch (NoSuchObjectException nsoe) {// this exception was a real problem ... do not know if still is!

                sb.append("\n\n NoSuchObjectException EXC in verifyLUSs: " + Utils.getStackTrace(nsoe));
                if ((lastJiniMgrRestart + TimeUnit.MILLISECONDS.toNanos(JINIMGR_RESTART_DELAY.get())) < Utils.nanoNow()) {
                    String status = null;
                    try {
                        status = restartJiniManagers(proxy);
                    } catch (Throwable ex) {
                        status = "\nCaught exception while REtrying to restartJiniManagers after NoSuchObjectException ex:\n"
                                + Utils.getStackTrace(ex);
                    }
                    sb.append("\n\n Restarting JiniManagers ... \n\n\n").append(status);
                    lastJiniMgrRestart = Utils.nanoNow();
                } else {
                    sb.append("\n\n Restarting JiniManagers ... delayed ...");
                }

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "\n [ verifyLUSs ] -> Status ( NSOE ) \n" + sb.toString());
                }

                sendLUSMail(sb.toString());
            } catch (IOException ioe) {
                sb.append("\n\n IOException EXC in verifyLUSs: " + Utils.getStackTrace(ioe));
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "\n [ verifyLUSs ] -> Status ( IOE ) \n" + sb.toString());
                }
                sendLUSMail(sb.toString());
            } catch (Throwable t) {
                sb.append("\n\n GENERAL EXC in verifyLUSs: " + Utils.getStackTrace(t));
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "\n [ verifyLUSs ] -> Status ( Throwable ) \n" + sb.toString());
                }
                sendLUSMail(sb.toString());
            } finally {
                ltvLUS = Utils.nanoNow();
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " [ verifyLUSs ] took " + TimeUnit.NANOSECONDS.toMillis(ltvLUS - sTime)
                            + " ms\n\n ");
                }
            }// END - try {}catch

        }// if - just do the job :)
    }

    private final void sendLUSMail(final String message) {
        if ((lastLUSErrorMailSent + LUS_MAIL_DELAY.get()) < NTPDate.currentTimeMillis()) {
            try {
                FarmMonitor.sendMail("mlstatus@monalisa.cern.ch", new String[] { "mlstatus@monalisa.cern.ch" },
                        "[ LUS STATUS ] ", message, true);
            } catch (Throwable t1) {
            }
            lastLUSErrorMailSent = NTPDate.currentTimeMillis();
        }
    }

    @Override
    public void run() {
        ltvLUS = Utils.nanoNow();
        if (farmMonitor.shouldStop()) {
            forcedStop();
        }
        for (;;) {
            try {
                ServiceItem myServiceItem = getServiceItem();
                while (myServiceItem == null) {
                    try {
                        Thread.sleep(100);
                    } catch (Throwable t) {
                    }
                    myServiceItem = getServiceItem();
                }

                ProxyWorker.setServiceID(myServiceItem);

                try {
                    Thread.sleep(10 * 1000);
                } catch (Exception e) {
                }

                if (farmMonitor.shouldStop()) {
                    forcedStop();
                }

                if (bVerifyLUS.get()) {
                    verifyLUSs();
                }

            } catch (Throwable t) {
                logger.log(Level.WARNING, "[ RegFarmMonitor ] Got exc main loop", t);
            }
        }
    }

    private static String getCodebaseFromUpdateURLs() {
        String codebase = "";
        String rawURLs = null;
        if (!isVRVS) {
            rawURLs = AppConfig.getProperty("lia.monitor.updateURLs",
                    "http://monalisa.cacr.caltech.edu/FARM_ML,http://monalisa.cern.ch/MONALISA/FARM_ML");
        } else {
            rawURLs = AppConfig.getProperty("lia.monitor.updateURLs",
                    "http://monalisa.cacr.caltech.edu/VRVS_ML,http://monalisa.cern.ch/MONALISA/VRVS_ML");
        }

        StringTokenizer st = new StringTokenizer(rawURLs, ",");
        if ((st != null) && (st.countTokens() > 0)) {
            while ((st != null) && st.hasMoreTokens()) {
                codebase += (st.nextToken() + "/MLService/Service/ml_dl/farm_mon_dl.jar" + (st.hasMoreTokens() ? " "
                        : ""));
            }
        }

        return codebase;
    }

    public void register(String args[]) {
        this.args = args;
        String home = AppConfig.getProperty("MonaLisa_HOME", null);
        final boolean useExternalCodebase = AppConfig.getb("lia.Monitor.useExternalCodebase", true);

        String codebase = "";
        if (useExternalCodebase) {
            codebase += getCodebaseFromUpdateURLs();
        }

        String codebase1 = null;
        boolean useFarmCodebase = false;
        try {
            useFarmCodebase = AppConfig.getb("lia.Monitor.useFarmCodebase", false);
        } catch (Throwable t) {
            useFarmCodebase = false;
        }

        if (useFarmCodebase) {
            codebase1 = startWeb(home + File.separator + "Service" + File.separator + "ml_dl");
        }

        if (useFarmCodebase && (codebase1 != null) && (codebase1.length() != 0)) {
            codebase += (((codebase.length() == 0) ? "" : " ") + codebase1);
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "useExternalCodebase = " + useExternalCodebase + " useFarmCodebase == "
                    + useFarmCodebase + "; codebase1 = " + codebase1 + " codebase: " + codebase);
        }

        // set security manager
        if (System.getSecurityManager() == null) {
            System.setSecurityManager(new RMISecurityManager());
        }

        System.setProperty("java.rmi.server.codebase", codebase);

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE,
                    " [ RegFarmMonitor ] java.rmi.server.codebase: " + System.getProperty("java.rmi.server.codebase"));
        }

        myTemplate = new ServiceTemplate(null, new Class[] { lia.Monitor.monitor.DataStore.class }, null);

        try {
            try {
                farmMonitor = new FarmMonitor(this.args);
            } catch (Throwable t) {

                boolean bDelete = false;
                final String mlPidFileName = FarmHOME + File.separator + ".ml.pid";
                try {
                    final File mlpidF = new File(mlPidFileName);
                    bDelete = mlpidF.delete();
                } catch (Throwable ignore) {
                    bDelete = false;
                    logger.log(Level.WARNING, " Unable to delete mlPID file: '" + mlPidFileName + "' Cause: ", t);
                }

                if (!bDelete) {
                    logger.log(Level.WARNING, " Unable to delete mlPID file: '" + mlPidFileName + "' No reason given.");
                }

                bDelete = false;
                final String aliveFileName = FarmHOME + File.separator + "ALIVE";
                try {
                    final File aliveF = new File(aliveFileName);
                    bDelete = aliveF.delete();
                } catch (Throwable ignore) {
                    bDelete = false;
                    logger.log(Level.WARNING, " Unable to delete mlALIVE file: '" + aliveFileName + "' Cause: ", t);
                }

                if (!bDelete) {
                    logger.log(Level.WARNING, " Unable to delete mlALIVE file: '" + aliveFileName
                            + "' No reason given.");
                }
                logger.log(Level.SEVERE, " Failed to init FarmMonitor ! \n MonALISA will exit!", t);
                System.exit(-1);
            }
            monitorUnit = farmMonitor;
            dataStore = farmMonitor.getDataStore();

            // DataStore proxy = (DataStore) getRemote();
            String IPAddress = "";
            try {
                IPAddress = dataStore.getIPAddress();
            } catch (Exception e) {
            }
            if (IPAddress == null) {
                logger.log(Level.SEVERE,
                        "\n\n MonALISA was not able to determine your IP address. Please set your IP in ml.properties");
                System.exit(1);
            }

            if (IPAddress.trim().startsWith("127.")) {
                logger.log(
                        Level.SEVERE,
                        "\n\n MonALISA determine that your IP address starts with 127.x.x.x. Please set your IP in ml.properties or fix you local configuration");
                System.exit(1);
            }

            if (IPAddress.equals("0.0.0.0") || IPAddress.equals("255.255.255.255")) {
                logger.log(
                        Level.SEVERE,
                        "\n\n MonALISA determine that your IP address starts is either 0.0.0.0, either 255.255.255.255. Please set your IP in ml.properties");
                System.exit(1);
            }

            if (IPAddress.trim().startsWith("192.168.") || IPAddress.startsWith("10.")) { // ONLY
                logger.log(
                        Level.WARNING,
                        "\n\n MonALISA WAS NOT Started on Public IP Address. If your machine has one, please set your public IP in ml.properties");
            }
            proxy._key = farmMonitor.getMFarm().name + "&%&" + IPAddress + ":" + Cache.server.lis_port + " Date: "
                    + System.currentTimeMillis();

            String status = restartJiniManagers(proxy);
            lastJiniMgrRestart = Utils.nanoNow();

            try {
                FarmMonitor.sendMail("mlstatus@monalisa.cern.ch", new String[] { "mlstatus@monalisa.cern.ch" },
                        "[ (Re)StartJiniManagers ] ", status, true);
            } catch (Throwable t1) {
            }

        } catch (Throwable t) {
            logger.log(Level.SEVERE, "\n\n Failed to init the service ! Cause:\n", t);
            System.exit(-1);
        }

    }

    // dummy methods to map the Cache as a JINI service
    // //////////////////////////////////
    // RMI CODE REMOVED - since ML 1.8.0
    // /////////////////////////////////

    @Override
    public void Register(MonitorClient c, monPredicate p) throws java.rmi.RemoteException {
        throw new RemoteException("Not available any more");
        // dataStore.Register(c, p);
    }

    @Override
    public void unRegister(MonitorClient c) throws java.rmi.RemoteException {
        throw new RemoteException("Not available any more");
        // dataStore.unRegister(c);
    }

    @Override
    public void unRegister(MonitorClient c, Integer i) throws java.rmi.RemoteException {
        throw new RemoteException("Not available any more");
        // dataStore.unRegister(c, i);
    }

    @Override
    public MFarm confRegister(MonitorClient c) throws java.rmi.RemoteException {
        throw new RemoteException("Not available any more");
        // return dataStore.confRegister(c);
    }

    @Override
    public String getLocalTime() throws java.rmi.RemoteException {
        throw new RemoteException("Not available any more");
        // return dataStore.getLocalTime();
    }

    @Override
    public void addFilter(MonitorFilter mfliter) throws java.rmi.RemoteException {
        throw new RemoteException("Not available any more");
        // dataStore.addFilter(mfliter);
    }

    @Override
    public String[] getFilterList() throws java.rmi.RemoteException {
        throw new RemoteException("Not available any more");
        // return dataStore.getFilterList();
    }

    @Override
    public void Register(MonitorClient c, String filter) throws java.rmi.RemoteException {
        throw new RemoteException("Not available any more");
        // dataStore.Register(c, filter);
    }

    @Override
    public void Shutdown() {
        if (isShutDown.compareAndSet(false, true)) {
            try {
                // try a last 'Process Status'
                Runtime.getRuntime().exec(
                        new String[] { "/bin/sh", "-c",
                                "/bin/sh -c \"date; ps aux; echo; ps -elf; echo; pstree\" &>lastShudownStatus &" });

                // stop the DB
                String MonaLisa_HOME = "../..";
                try {
                    MonaLisa_HOME = AppConfig.getProperty("MonaLisa_HOME", null);
                } catch (Throwable ignore) {
                }
                Runtime.getRuntime().exec(
                        new String[] {
                                "/bin/sh",
                                "-c",
                                "/bin/sh -c \"sleep 10; " + MonaLisa_HOME
                                        + "/Service/CMD/ML_SER stopedb\" &>/dev/null &" });

                boolean shouldReastartFromHook = false;
                try {
                    shouldReastartFromHook = Boolean.valueOf(
                            AppConfig.getProperty("lia.Monitor.JiniSerFarmMon.RegFarmMonitor.shouldReastartFromHook",
                                    "false")).booleanValue();
                } catch (Throwable ignore) {
                    shouldReastartFromHook = false;
                }

                if (shouldReastartFromHook) {
                    long checkDelay = (verifDelta / 1000) + 10;
                    // try to restart ML ... if necessary
                    Runtime.getRuntime().exec(
                            new String[] {
                                    "/bin/sh",
                                    "-c",
                                    "/bin/sh -c \"sleep " + checkDelay + "; " + MonaLisa_HOME
                                            + "/Service/CMD/CHECK_UPDATE; sleep " + checkDelay + "; " + MonaLisa_HOME
                                            + "/Service/CMD/CHECK_UPDATE\" &>/dev/null &" });
                }

            } catch (Throwable t) {
                t.printStackTrace();
            }

            try {
                farmMonitor.Shutdown();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

    }

    public static native void registerHandler();

    private static FileLock theBikeKernelLock; // :)

    private static FileChannel theBigLockFC;

    private static RandomAccessFile theBigLockFile;

    /**
     * The streams for .ml.lock file opened by the function <b>MUST</b> never be closed,
     * or the FileLock will be lost
     */
    private static final void checkLock() {

        FileLock tmpLock = null;

        try {
            File f = new File(lockFSFile);
            if (f.exists()) {
                if (!f.canRead()) {
                    stopJVM(" The " + lockFSFile
                            + " file is needed to read/write the service ID. It does not have read permissions.\n"
                            + " Is MonALISA suppose to run from a different account?. The service will stop now");
                }

                if (!f.canWrite()) {
                    stopJVM(" The " + lockFSFile
                            + " file is needed to read/write the service ID. It does not have write permissions.\n"
                            + " Is MonALISA suppose to run from a different account?. The service will stop now");
                }

            } else {
                try {
                    f.createNewFile();
                } catch (Throwable t) {
                    stopJVM(" The " + lockFSFile
                            + " file is needed to read/write the service ID. It seems that the file does not exists"
                            + " and cannot be created because: " + Utils.getStackTrace(t));
                }
            }

            try {
                theBigLockFile = new RandomAccessFile(f, "rw");
                theBigLockFC = theBigLockFile.getChannel();
                tmpLock = theBigLockFC.tryLock();

                if ((tmpLock == null) || !tmpLock.isValid()) {
                    stopJVM(getDefaultShutdownMsg(lockFSFile, null));
                }

                theBigLockFC.truncate(0);
                theBigLockFC.position(0);

            } catch (Throwable t) {
                stopJVM(getDefaultShutdownMsg(lockFSFile, t));
            }

            try {

                Process p = MLProcess.exec(new String[] { "uname", "-a" });
                InputStream is = null;
                String line = null;
                BufferedReader br = null;
                InputStreamReader isr = null;

                theBigLockFile.writeBytes("\nML Started @ " + DateFormat.getInstance().format(new Date()) + " on: ");
                try {
                    is = p.getInputStream();
                    isr = new InputStreamReader(is);
                    br = new BufferedReader(isr);
                    while ((line = br.readLine()) != null) {
                        theBigLockFile.writeBytes(line + "\n");
                    }
                    p.waitFor();
                } finally {
                    if (isr != null) {
                        try {
                            isr.close();
                        } catch (Throwable t1) {
                        }
                    }
                    if (is != null) {
                        try {
                            is.close();
                        } catch (Throwable t1) {
                        }
                    }
                    if (br != null) {
                        try {
                            br.close();
                        } catch (Throwable t1) {
                        }
                    }
                }

            } catch (Throwable t) {
                logger.log(Level.WARNING, " Got exception trying to write to process `uname -a`", t);
            } finally {
            }

            try {
                Process p = MLProcess.exec(new String[] { "id" });
                InputStream is = null;
                String line = null;
                BufferedReader br = null;
                InputStreamReader isr = null;

                theBigLockFile.writeBytes("by: ");
                try {
                    is = p.getInputStream();
                    isr = new InputStreamReader(is);
                    br = new BufferedReader(isr);
                    while ((line = br.readLine()) != null) {
                        theBigLockFile.writeBytes(line + "\n");
                    }
                    p.waitFor();
                } finally {
                    if (isr != null) {
                        try {
                            isr.close();
                        } catch (Throwable t1) {
                        }
                    }
                    if (is != null) {
                        try {
                            is.close();
                        } catch (Throwable t1) {
                        }
                    }
                    if (br != null) {
                        try {
                            br.close();
                        } catch (Throwable t1) {
                        }
                    }
                }

            } catch (Throwable t) {
                logger.log(Level.WARNING, " Got exception trying to write to process `id`", t);
            } finally {
            }
        } catch (Throwable t) {
            stopJVM(getDefaultShutdownMsg(lockFSFile, t));
        }

        try {
            theBigLockFile.writeBytes("\n");
            theBigLockFC.force(true);
        } catch (Throwable t) {
            System.out.println(" Got exception flushing the data to the lock file");
            t.printStackTrace();
        }

        theBikeKernelLock = tmpLock;

        if ((theBikeKernelLock == null) || !theBikeKernelLock.isValid()) {
            stopJVM(getDefaultShutdownMsg(lockFSFile, null));
        }

    }

    private static final String getDefaultShutdownMsg(String urlFS, Throwable t) {
        StringBuilder sb = new StringBuilder(1024);

        sb.append("\nThe JVM Cannot take the lock for ").append(urlFS);
        if (t != null) {
            sb.append(" because got an exception: ").append(Utils.getStackTrace(t));
        }

        sb.append("\n Possible reasons are:");
        sb.append("\n 1. ML already runns in this account and it is a bug in the ML_SER script. Please email developers: developers@monalisa.cern.ch ");
        sb.append("\n 2. ML already runns in another account on this machine and has the same FARM_HOME defined in CMD/ml_env. ");
        sb.append("\n 3. If the FARM_HOME defined in CMD/ml_env is a network file system (AFS, NFS, etc) it is possible that ");
        sb.append("\n   the service already runs on another machine.");
        sb.append("\n Please check the file ").append(urlFS).append(" to see when was the service started last time\n");

        return sb.toString();
    }

    /**
     * add/modify Embedded Applications entries to LUSs
     */
    private void refreshEmbeddedAppEntries() throws Exception {

        JoinManager jmngr = MLJiniManagersProvider.getJoinManager();
        if (jmngr == null) {
            return;
        }
        final Collection<AppInt> loadedModules = lia.app.AppControl.getLoadedModules();
        Hashtable currentEntriesHT = new Hashtable();
        Vector entriesToModify = new Vector();
        Vector entriesToAdd = new Vector();

        final Iterator iit = loadedModules.iterator();
        for (; iit.hasNext();) {
            try {
                EmbeddedAppEntry eae = new EmbeddedAppEntry();
                synchronized (loadedModules) {
                    AppInt appInt = (AppInt) iit.next();
                    eae.Name = appInt.getName();
                    eae.ConfigFile = appInt.getConfigFile();
                    eae.State = Integer.valueOf(appInt.status());
                } // synchronized
                String key = new String(eae.Name + eae.ConfigFile);
                if (previousEntries.containsKey(key)) {
                    EmbeddedAppEntry previousEntry = (EmbeddedAppEntry) previousEntries.get(key);
                    if (!previousEntry.equals(eae)) // modify
                    {
                        entriesToModify.addElement(eae);
                    }
                } else { // add
                    entriesToAdd.addElement(eae);
                }
                currentEntriesHT.put(key, eae);
            } catch (Exception e) {
                e.printStackTrace();
            } // try - catch
        } // for
          // delete
        Vector entriesToDelete = new Vector();
        Enumeration previousEntriesKeys = previousEntries.keys();
        while (previousEntriesKeys.hasMoreElements()) {
            String pk = (String) previousEntriesKeys.nextElement();
            if (currentEntriesHT.containsKey(pk) == false) {
                EmbeddedAppEntry eToDel = (EmbeddedAppEntry) previousEntries.get(pk);
                entriesToDelete.addElement(eToDel);
            }
        }
        /*
         * modify Attributes for embedded applications add new Entry delete
         * entries for unloaded modules modify entries for changed modules
         */
        // add
        if (entriesToAdd.size() > 0) {
            Entry[] attrSet = (Entry[]) entriesToAdd.toArray(new Entry[entriesToAdd.size()]);

            jmngr.addAttributes(attrSet);
        }

        // modify
        if (entriesToModify.size() > 0) {
            EmbeddedAppEntry[] attrSet = new EmbeddedAppEntry[entriesToModify.size()];
            for (int i = 0; i < entriesToModify.size(); i++) {
                attrSet[i] = (EmbeddedAppEntry) entriesToModify.elementAt(i);
            }
            EmbeddedAppEntry[] attrSetTemplates = new EmbeddedAppEntry[entriesToModify.size()];

            for (int k = 0; k < entriesToModify.size(); k++) {
                attrSetTemplates[k] = new EmbeddedAppEntry();
                attrSetTemplates[k].ConfigFile = attrSet[k].ConfigFile;
                attrSetTemplates[k].Name = attrSet[k].Name;
                attrSetTemplates[k].State = null;
            } // for
            jmngr.modifyAttributes(attrSetTemplates, attrSet);

            // -->DEBUG
            for (int idbg = 0; idbg < entriesToModify.size(); idbg++) {
                EmbeddedAppEntry dbg_eToMod = (EmbeddedAppEntry) entriesToModify.elementAt(idbg);
                System.out.println("Entry to modify: " + dbg_eToMod.Name + ":" + dbg_eToMod.ConfigFile + ":"
                        + dbg_eToMod.State);
            }
            // <--DEBUG
        }

        // delete
        if (entriesToDelete.size() > 0) {
            Entry[] attrSet = new Entry[entriesToDelete.size()];
            for (int idel = 0; idel < entriesToDelete.size(); idel++) {
                attrSet[idel] = null;
            }
            Entry[] attrSetTemplates = (Entry[]) entriesToDelete.toArray(new Entry[entriesToDelete.size()]);
            jmngr.modifyAttributes(attrSetTemplates, attrSet);
            // -->DEBUG
            for (int idbg = 0; idbg < entriesToDelete.size(); idbg++) {
                EmbeddedAppEntry dbg_eToDel = (EmbeddedAppEntry) entriesToDelete.elementAt(idbg);
                System.out.println("Entry to delete: " + dbg_eToDel.Name + ":" + dbg_eToDel.ConfigFile + ":"
                        + dbg_eToDel.State);
            }
            // <--DEBUG
        }

        previousEntries.clear();
        previousEntries.putAll(currentEntriesHT);
        currentEntriesHT.clear();
        currentEntriesHT = null;

    }

    private static final void checkAndLoadLocalEnvironment() {

        String tmpLockFSFile = null;

        try {
            tmpLockFSFile = AppConfig.getProperty("lia.Monitor.JiniSerFarmMon.RegFarmMonitor.lockfile",
                    "${lia.Monitor.Farm.HOME}" + File.separator + ".ml.lock").trim();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ RegFarmMonitor ] Unable to determine the lock file. Cause: ", t);
            tmpLockFSFile = null;
        }

        lockFSFile = tmpLockFSFile;

        boolean bIsVRVS = false;
        boolean bShouldExportRMIInterface = false;
        try {
            if (AppConfig.getProperty("lia.Monitor.isVRVS", null) != null) {
                bIsVRVS = true;
            }
        } catch (Throwable t) {
            logger.log(
                    Level.INFO,
                    " [ FarmMonitor ] [ HANDLED ] Got exception trying to determine if running in EVO/VRVS environment",
                    t);
            bIsVRVS = false;
        }

        if (!bIsVRVS) {
            try {
                if (AppConfig.getProperty("lia.Monitor.isEVO", null) != null) {
                    bIsVRVS = true;
                }
            } catch (Throwable t) {
                logger.log(
                        Level.INFO,
                        " [ FarmMonitor ] [ HANDLED ] Got exception trying to determine if running in EVO/VRVS environment",
                        t);
                bIsVRVS = false;
            }
        }

        isVRVS = bIsVRVS;

        try {
            bShouldExportRMIInterface = AppConfig
                    .getb("lia.Monitor.Farm.FarmMonitor.shouldExportRMIInterface", bIsVRVS);
        } catch (Throwable t) {
            logger.log(Level.INFO,
                    " [ FarmMonitor ] [ HANDLED ] Got exception trying to determine if shouldExportRMIInterface", t);
            bShouldExportRMIInterface = false;
        }

        shouldExportRMIInterface = bShouldExportRMIInterface;

        boolean bStartAdminInterface = false;
        try {
            bStartAdminInterface = AppConfig.getb("lia.Monitor.Agents.OpticalPath.MLCopyAgent.startAdminInterface",
                    false);
        } catch (Throwable t) {
            logger.log(
                    Level.INFO,
                    " [ FarmMonitor ] [ HANDLED ] Got exception trying to determine the property lia.Monitor.Agents.OpticalPath.MLCopyAgent.startAdminInterface",
                    t);
            bStartAdminInterface = false;
        }

        shouldStartAdminInterface = bStartAdminInterface;

        reloadTimes();

        String tmpHome = null;
        try {
            tmpHome = AppConfig.getProperty("lia.Monitor.Farm.HOME", null);
        } catch (Throwable t) {
            logger.log(Level.SEVERE,
                    " [ checkAndPoulateLocalEnvironment ] Got exception trying to determine lia.Monitor.Farm.HOME", t);
            tmpHome = null;
        }

        FarmHOME = tmpHome;

        if (FarmHOME == null) {
            logger.log(
                    Level.SEVERE,
                    " [ FarmMonitor ] [ SEVERE ] Unable to determine lia.Monitor.Farm.HOME environment variable ... ML Service will stop now ");
            System.exit(1);
        }

        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                reloadTimes();
            }
        });

    }

    public static void main(String args[]) throws Exception {

        final String MonaLisa_version = "@version@";
        final String MonaLisa_vdate = "@vdate@";

        if ((args != null) && (args.length == 1)) {
            if ((args[0] != null) && args[0].equals("-version")) {
                System.out.println("\nMonALISA Version: " + MonaLisa_version + " [ " + MonaLisa_vdate + " ]\n");
                System.exit(0);
            }
        }

        try {
            System.setProperty("java.lang.Integer.IntegerCache.high", "70000");
        } catch (Throwable ignore) {
            // can fail
        }

        try {
            System.in.close();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Unable to close system.in stream. Cause: ", t);
        }

        try {
            StringBuilder sb = new StringBuilder(4096);
            sb.append("\n\n -> MonALISA STARTED on: ").append(new Date());
            sb.append("\n -> OS: ").append(System.getProperty("os.name")).append(" ")
                    .append(System.getProperty("os.version"));
            sb.append("\n -> OS arch: ").append(System.getProperty("os.arch"));
            sb.append("\n -> java version: ").append(System.getProperty("java.version")).append("; vm version: ")
                    .append(System.getProperty("java.vm.version")).append("; vm.info: ")
                    .append(System.getProperty("java.vm.info"));
            sb.append("\n -> user name: ").append(System.getProperty("user.name"));
            sb.append("\n -> user home: ").append(System.getProperty("user.home"));
            sb.append("\n -> user dir: ").append(System.getProperty("user.dir")).append("\n\n");
            logger.log(Level.INFO, sb.toString());
        } catch (Throwable t) {
            // if some security managers are in place
            logger.log(Level.WARNING, "\n\n ML got exception trying to get the env. Cause: ", t);
        }

        checkAndLoadLocalEnvironment();
        ML_START_TIME = NTPDate.currentTimeMillis();

        if (FarmHOME == null) {
            System.err
                    .println("\n\n Unable to determine lia.Monitor.Farm.HOME environment variable. MonALISA will stop now!");
            System.exit(1);
        }

        if (AppConfig.getb("lia.Monitor.JiniSerFarmMon.RegFarmMonitor.disableLockChecking", false)) {
            // log this in both ML.log and ML0.log
            logger.log(Level.WARNING, "\n\n Locking check is disabled \n\n");
            System.out.println("\n\n Locking check is disabled \n\n");
        } else {
            if (lockFSFile == null) {
                logger.log(Level.WARNING,
                        " [ RegFarmMonitor ] the lock file is null. The lock chcking is disabled ....");
            } else {
                checkLock();

                if ((theBikeKernelLock == null) || !theBikeKernelLock.isValid()) {
                    stopJVM(getDefaultShutdownMsg(lockFSFile, null));
                }
            }
        }

        if (System.getSecurityManager() == null) {
            System.setSecurityManager(new RMISecurityManager());
        }

        try {
            String debugH = System.getProperty("lia.Monitor.JNI_DEBUG_HANDLER", "false");
            if (Boolean.valueOf(debugH).booleanValue()) {
                logger.log(Level.INFO, "Trying to install JNI_HANDLER");
                System.loadLibrary("Handler");
                registerHandler();
                jniDebug = true;
                logger.log(Level.INFO, "JNI_HANDLER installed");
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Cannot load JNI_HANDLER", t);
        }

        final int lRecentData = AppConfig.geti("lia.web.Cache.RecentData", -1);
        if (lRecentData < 0) {
            try {
                System.setProperty("lia.web.Cache.RecentData", "1");
            } catch (Throwable t1) {
                logger.log(Level.WARNING, " Error setting lia.web.Cache.RecentData", t1);
            }
        }

        try {
            System.setProperty("lia.Monitor.STIME", "" + ML_START_TIME);
        } catch (Throwable t1) {
            logger.log(Level.WARNING, "Error setting STIME", t1);
        }
        try {
            System.setProperty("sun.net.client.defaultConnectTimeout", "30000");
            System.setProperty("sun.net.client.defaultReadTimeout", "30000");
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error setting socket connect and read timeouts", t);
        }

        logger.log(Level.INFO, "Update TMP_CACHE:" + AppConfig.getProperty("CACHE_DIR"));

        try {
            System.setProperty("networkaddress.cache.ttl", "7200");// 6h
            java.security.Security.setProperty("networkaddress.cache.ttl", "7200");
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error setting IP Cache TTL", t);
        }

        try {
            boolean jiniUsesNIO = false;
            try {
                jiniUsesNIO = AppConfig.getb("lia.Monitor.JiniSerFarmMon.jiniUsesNIO", false);
            } catch (Throwable t1) {
                jiniUsesNIO = false;
            }

            if (jiniUsesNIO) {
                System.setProperty("com.sun.jini.jeri.tcp.useNIO", "true");
            } else {
                System.setProperty("com.sun.jini.jeri.tcp.useNIO", "false");
            }

        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error setting property com.sun.jini.jeri.tcp.useNIO", t);
        }

        final String forceIP = AppConfig.getProperty("lia.Monitor.useIPaddress");
        if (forceIP != null) {
            System.setProperty("java.rmi.server.hostname", forceIP);
        }

        if (shouldExportRMIInterface || shouldStartAdminInterface) {
            try {
                RegFarmMonitor.REGISTRY_PORT = RegistryRangePortExporter.createRegistry();
                logger.log(Level.INFO, "REGISTRY_PORT: " + RegFarmMonitor.REGISTRY_PORT);
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "\n\n ---> Failed to start rmiregistry ", t);
            }
        }

        // disable StringFactory intern() inside service
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

        RegFarmMonitor rf = new RegFarmMonitor(args);

    }// end main
}
