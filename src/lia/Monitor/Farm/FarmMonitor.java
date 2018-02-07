package lia.Monitor.Farm;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import lia.Monitor.DataCache.Cache;
import lia.Monitor.Farm.Conf.CCluster;
import lia.Monitor.Farm.Conf.CNode;
import lia.Monitor.Farm.Conf.CParam;
import lia.Monitor.Farm.Conf.ConfVerifier;
import lia.Monitor.Filters.ApacheWatcher;
import lia.Monitor.Filters.RepositoryWatcher;
import lia.Monitor.JiniSerFarmMon.RegFarmMonitor;
import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.ciena.eflow.EFlowStatsConsumer;
import lia.Monitor.ciena.eflow.EFlowStatsMgr;
import lia.Monitor.monitor.AccountingResult;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.DataReceiver;
import lia.Monitor.monitor.DataStore;
import lia.Monitor.monitor.ExtResult;
import lia.Monitor.monitor.JarFinder;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitorFilter;
import lia.Monitor.monitor.MonitorUnit;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.ShutdownReceiver;
import lia.Monitor.monitor.dbStore;
import lia.Monitor.monitor.eResult;
import lia.app.AppControl;
import lia.app.abing.AppAbing;
import lia.util.MLProcess;
import lia.util.StringFactory;
import lia.util.Utils;
import lia.util.actions.ActionsManager;
import lia.util.config.PropertiesUtil;
import lia.util.mail.MailFactory;
import lia.util.net.rmi.RangePortUnicastRemoteObject;
import lia.util.ntp.NTPDate;
import lia.util.security.RCSF;
import lia.util.security.RSSF;
import lia.util.update.VRVSUpdater;
import net.jini.core.lookup.ServiceItem;

import org.apache.axis.client.AdminClient;
import org.apache.axis.i18n.Messages;
import org.apache.axis.transport.http.SimpleAxisServer;
import org.apache.axis.utils.Options;

/**
 * @author Iosif Legrand
 * @author ramiro
 */
public class FarmMonitor extends RangePortUnicastRemoteObject implements MonitorUnit, Runnable, ShutdownReceiver {

    private static final long serialVersionUID = -2871089143181685121L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(FarmMonitor.class.getName());

    public static final String username;

    static {
        String user = "user.name";
        try {
            user = System.getProperty("user.name");
        } catch (Throwable t) {
            user = "user.name";
            logger.log(Level.SEVERE,
                    "[ FarmMonitor ] [ HANDLED ] Unable to fetch user.name from env variables. Cause:", t);
        }

        username = user;
    }

    public static String hostname = "localhost";

    public static final String MON_UNKOWN_NAME = "monUNKNOWN";

    private static final AtomicLong UNK_RESULTS_MODULE_COUNT = new AtomicLong(0);

    public static String realFromAddress = username + "@" + hostname;

    private static final String CMD_WRAPPER_SCRIPT_NAME = "cmd_run.sh";

    /**
     * modified
     */
    public static AppControl appControlHandler = null;

    public static final String MonaLisa_version = "@version@";

    public static final String MonaLisa_vdate = "@vdate@";

    static public final String VoModulesDir = "@ML_VoModulesDir@";

    static public final String osgVoModulesDir = "@ML_OsgVoModulesDir@";

    static public final String PNModulesDir = "@ML_PNModulesDir@";

    public static final String MonaLisa_home;

    public static final String Farm_home;

    static public int appControlPort = -1;

    public static String FarmName;

    public final Cache cache;

    private final MFarm farm;

    public String[] externalModules;

    public String[] externalModParams;

    public String[] externalModRTime;

    public final ConcurrentMap<String, Map<String, Long>> modulesTimeoutConfig = new ConcurrentHashMap<String, Map<String, Long>>();

    public final ConcurrentMap<String, CCluster> clustersTimeoutMap = new ConcurrentHashMap<String, CCluster>();

    EdMFarm ed;

    final TaskManager taskManager;

    Vector<String> availableModules;

    final Hashtable<String, MonModuleInfo> moduleInfo;

    private static final CopyOnWriteArrayList<Pattern> hiddenClusters = new CopyOnWriteArrayList<Pattern>();

    private static final Set<String> ignoredConfModules = new ConcurrentSkipListSet<String>();

    public static volatile boolean ignoreSomeConfModules = false;

    private static String vrvsUpdateURL;

    private static volatile boolean notifyConfigeResults = false;

    static volatile int MAX_CFGSTR_LEN = 250;

    static volatile int MAX_CLUSTERNAME_LEN = MAX_CFGSTR_LEN;

    static volatile int MAX_NODENAME_LEN = MAX_CFGSTR_LEN;

    static volatile int MAX_PARAMNAME_LEN = MAX_CFGSTR_LEN;

    SimpleAxisServer sas;

    public static final String EXTENDED_CMD_STATUS = Utils.getPromptLikeBinShCmd("env")
            + Utils.getPromptLikeBinShCmd("set") + Utils.getPromptLikeBinShCmd("pwd")
            + Utils.getPromptLikeBinShCmd("hostname") + Utils.getPromptLikeBinShCmd("hostname -f")
            + Utils.getPromptLikeBinShCmd("hostname -i") + Utils.getPromptLikeBinShCmd("free -t -m")
            + Utils.getPromptLikeBinShCmd("uname -a") + Utils.getPromptLikeBinShCmd("uptime")
            + Utils.getPromptLikeBinShCmd("/sbin/ifconfig");

    // it should be synch if data receivers may be added automagically .... keep it simple and faster as possible
    private DataReceiver[] receivers;

    public static final boolean isVRVSFarm;

    public static final boolean shouldExportRMIInterface;

    public static final boolean disableConfVerifier;

    private static URLClassLoader externalClassLoader;

    static {

        try {
            checkAndLoadLocalEnvironment();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ FarmMonitor ] [ HANDLED ] got exception loading localEnv ", t);
        }

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

        try {
            bShouldExportRMIInterface = AppConfig
                    .getb("lia.Monitor.Farm.FarmMonitor.shouldExportRMIInterface", bIsVRVS);
        } catch (Throwable t) {
            logger.log(Level.INFO,
                    " [ FarmMonitor ] [ HANDLED ] Got exception trying to determine if shouldExportRMIInterface", t);
            bShouldExportRMIInterface = false;
        }

        boolean bdisableConfVerifier = false;
        try {
            bdisableConfVerifier = AppConfig.getb("lia.Monitor.Farm.FarmMonitor.disableConfVerifier", false);
        } catch (Throwable t) {
            logger.log(Level.INFO,
                    " [ FarmMonitor ] [ HANDLED ] Got exception trying to determine if disableConfVerifier", t);
            bdisableConfVerifier = false;
        }

        disableConfVerifier = bdisableConfVerifier;

        isVRVSFarm = bIsVRVS;
        shouldExportRMIInterface = bShouldExportRMIInterface;

        logger.log(Level.INFO, " [ FarmMonitor ] shouldExportRMIInterface: " + shouldExportRMIInterface + " EVO env: "
                + isVRVSFarm);
    }

    private final AtomicBoolean shouldUpdate = new AtomicBoolean(false);

    private final AtomicBoolean shouldStop = new AtomicBoolean(false);

    /* real time Results */
    private static final BlockingQueue<Object> rtResults;

    /* Other dbStores from where to gather data */
    private final Vector<dbStore> other_dbStores = new Vector<dbStore>();

    public static String MLStatFile = null;

    private static Thread tomcatThread = null;

    private volatile ConfVerifier confVerifier;

    static {

        final String mlHome = AppConfig.getProperty("MonaLisa_HOME", null);
        final String serviceHome = AppConfig.getProperty("lia.Monitor.Farm.HOME", null);

        if (mlHome == null) {
            System.err.println("[ FarmMonitor ] Cannot determine MonaLisa_HOME env variable ... ML_SER script broken?");
            System.exit(1);
        }

        if (serviceHome == null) {
            System.err
                    .println("[ FarmMonitor ] Cannot determine lia.Monitor.Farm.HOME env variable ... ML_SER script broken?");
            System.exit(2);
        }

        int maxResultQueueSize = 50000;
        try {
            maxResultQueueSize = AppConfig.geti("lia.Monitor.Farm.MAX_RESULTS_QUEUE_SIZE", 50000);
        } catch (Throwable t) {
            maxResultQueueSize = 50000;
        }

        logger.log(Level.INFO, "[ FarmMonitor ] MAX_RESULTS_QUEUE_SIZE = " + maxResultQueueSize);
        rtResults = new LinkedBlockingQueue<Object>(maxResultQueueSize);

        Farm_home = serviceHome;
        MonaLisa_home = mlHome;

        reloadCfg();

        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                reloadCfg();
            }
        });
    }

    public FarmMonitor(String[] args) throws java.rmi.RemoteException, Exception {
        super(new RCSF(), new RSSF(), shouldExportRMIInterface);

        final String prevWrapConvigValue = AppConfig.setProperty("lia.util.MLProcess.doNotUseWrapper", "true");
        logger.log(Level.INFO,
                "Trying check for new versions of scripts ... prevVal for lia.util.MLProcess.doNotUseWrapper = "
                        + prevWrapConvigValue);
        if (!compareScriptVersion(CMD_WRAPPER_SCRIPT_NAME)) {
            logger.log(Level.INFO, "Updating script: " + CMD_WRAPPER_SCRIPT_NAME);
            try {
                final boolean success = ServiceUpdaterHelper.updateScript(CMD_WRAPPER_SCRIPT_NAME);
                if (success) {
                    logger.log(Level.INFO, "[ FarmMonitor ] The script: " + CMD_WRAPPER_SCRIPT_NAME
                            + " was udpated succesfully");
                } else {
                    logger.log(Level.WARNING, "[ FarmMonitor ] Unable to update the script: " + CMD_WRAPPER_SCRIPT_NAME);
                }
            } catch (Throwable t) {
                AppConfig.setProperty("lia.util.MLProcess.doNotUseWrapper", "true");
                logger.log(Level.WARNING, "Unable to update script: " + CMD_WRAPPER_SCRIPT_NAME
                        + ". Will not use this wrapper. Cause:", t);
            }
        } else {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " Not Updating " + CMD_WRAPPER_SCRIPT_NAME);
            }
        }
        AppConfig.setProperty("lia.util.MLProcess.doNotUseWrapper", prevWrapConvigValue);

        boolean bScriptsUpdate = false;
        try {
            bScriptsUpdate = ServiceUpdaterHelper.updateScripts();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "\n\n [ FarmMonitor ] [ updateScripts ] got exception ", t);
        }

        if (bScriptsUpdate) {
            logger.log(Level.INFO, "Scripts updated! Will restart ML soon ...");
        } else {
            logger.log(Level.INFO, "All ML scripts are using the latest version.");
        }

        FarmName = args[0];

        // ************************
        // Use defaul epgsql ... if possible
        // since 1.4.0
        // ************************
        try {
            PropertiesUtil.addModifyPropertyFile(new File(Farm_home + File.separator + "ml.properties"), new File(
                    Farm_home), "lia.Monitor.use_epgsqldb", "true", "# Use embedded PostgreSQL", false, true);
        } catch (Throwable t) {
        }

        try {
            AppConfig.reloadProps();
        } catch (Throwable t) {
        }

        if (isVRVSFarm) {
            updateVRVSProps();
        }

        MLStatFile = AppConfig.getProperty("lia.Monitor.Farm.MLStatFile", Farm_home + File.separator + "MLStatFile");

        farm = new MFarm(FarmName);
        initJModules();
        moduleInfo = new Hashtable<String, MonModuleInfo>();
        final Map<String, ModuleParams> mpHash = new HashMap<String, ModuleParams>();
        ed = new EdMFarm(this, farm, mpHash);
        final ArrayList<String> cfgFiles = new ArrayList<String>();

        for (int i = 1; i < args.length; i++) {
            ed.readOldFile(args[i]);
            cfgFiles.add(args[i]);
        }

        logger.log(Level.INFO, "\n\nServiceName: " + FarmName + "\nMonaLisa Version: " + MonaLisa_version + " [ "
                + MonaLisa_vdate + " ]" + "\nMonaLisa_HOME=" + MonaLisa_home + "\n");

        final String forceIP = AppConfig.getProperty("lia.Monitor.useIPaddress");

        Cache tmpCache = null;
        try {
            tmpCache = Cache.initInstance(FarmName, this);
            addDataReceiver(tmpCache);

            tmpCache.updateConfig(farm);
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "rmi export Data Cache");
            }

            BufferedWriter bw = null;
            FileWriter fw = null;

            try {
                if ((MLStatFile != null) && (MLStatFile.length() > 0)) {
                    fw = new FileWriter(MLStatFile);
                    bw = new BufferedWriter(fw);

                    StringBuilder status = new StringBuilder();
                    status.append("\nMonALISA_Version = ").append(MonaLisa_version);
                    status.append("\nMonALISA_VDate = ").append(MonaLisa_vdate);
                    status.append("VoModulesDir = ").append(VoModulesDir);
                    status.append("tcpServer_Port = ").append(
                            (Cache.server != null) ? "" + Cache.server.lis_port : "N/A");
                    status.append("storeType = ").append(TransparentStoreFactory.getStoreType());

                    bw.write(status.toString());
                } else {
                    logger.log(Level.WARNING, "Could not write tcpServer_Port to MLStatFile [ " + MLStatFile + " ]");
                }
            } catch (Throwable tml) {
                logger.log(Level.WARNING, "Could not write current status to MLStatFile: " + MLStatFile + " Cause:",
                        tml);
            } finally {
                if (bw != null) {
                    try {
                        bw.close();
                    } catch (Throwable ignore) {
                    }
                }
                if (fw != null) {
                    try {
                        fw.close();
                    } catch (Throwable ignore) {
                    }
                }
            }
        } catch (Throwable t) {
            logger.log(Level.SEVERE, " Failed to init Data Cache. MonALISA will stop", t);
            System.exit(1);
        } finally {
            this.cache = tmpCache;
        }

        if (this.cache == null) {
            logger.log(Level.SEVERE, " Failed to init Data Cache. MonALISA will stop");
            System.exit(1);
        }

        // update the usr_code
        if (!isVRVSFarm) {
            StringBuilder mailBody = new StringBuilder(8192);
            logger.log(Level.FINER, " Probing for VoModules");
            String usrCodeUpdateStatus = update_usr_code(VoModulesDir + ".tgz", VoModulesDir);
            if (usrCodeUpdateStatus != null) {
                mailBody.append("\n").append(usrCodeUpdateStatus).append("\n");
            }

            usrCodeUpdateStatus = update_usr_code("XDRUDP.tgz", "XDRUDP");
            if (usrCodeUpdateStatus != null) {
                mailBody.append("\n").append(usrCodeUpdateStatus).append("\n");
            }

            usrCodeUpdateStatus = update_usr_code(osgVoModulesDir + ".tgz", osgVoModulesDir);
            if (usrCodeUpdateStatus != null) {
                mailBody.append("\n").append(usrCodeUpdateStatus).append("\n");
            }

            usrCodeUpdateStatus = update_usr_code(PNModulesDir + ".tgz", PNModulesDir);
            if (usrCodeUpdateStatus != null) {
                mailBody.append("\n").append(usrCodeUpdateStatus).append("\n");
            }

            try {
                String subjToAdd = getStandardEmailSubject();
                MailFactory.getMailSender().sendMessage("mlstatus@monalisa.cern.ch",
                        new String[] { "mlstatus@monalisa.cern.ch" }, " [ usr_code update status ] @ " + subjToAdd,
                        mailBody.toString());
            } catch (Throwable t1) {
            }

            logger.log(Level.FINER, " End Probing for VoModules");
        }

        addDataReceivers();

        taskManager = new TaskManager(this, farm, moduleInfo, mpHash);
        taskManager.task_init();

        // starting the cache
        Thread cacheThread = new Thread(this.cache, "(ML) Cache @ " + FarmName);
        try {
            cacheThread.setDaemon(true);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Cannot setDaemon", t);
        }
        cacheThread.start();

        // Start internal Filters
        startInternalFilters();

        // Start external Filters
        addExternalFilters();

        if (shouldExportRMIInterface || isVRVSFarm) {
            try {
                if (forceIP == null) {
                    Naming.rebind("rmi://localhost:" + RegFarmMonitor.REGISTRY_PORT + "/Farm_Monitor", this);
                } else {
                    Naming.rebind("rmi://" + forceIP + ":" + RegFarmMonitor.REGISTRY_PORT + "/Farm_Monitor", this);
                }
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "rmi export Farm Monitor");
                }
            } catch (Throwable t) {
                logger.log(Level.SEVERE, " Failed to export Farm Monitor", t);
            }
        }

        boolean startWSDL = AppConfig.getb("lia.Monitor.startWSDL", false);

        if (startWSDL) {
            // Class[] interfaces = new Class[] { WDataStore.class };

            String wsdl_port = AppConfig.getProperty("lia.Monitor.wsdl_port", "6004");

            // AXIS
            boolean wsStarted = true;
            try {
                startSimpleAxisServer(new String[] { "-p", wsdl_port });
                // sas.setDoThreads (false);
            } catch (Throwable t) {
                wsStarted = false;
                logger.log(Level.WARNING, " Failed to Start the WEB SERVICE ", t);
            }

            // System.out.println ("======> doThread !!!! "+sas.getDoThreads());

            if (wsStarted) {
                boolean wsDeployed = true;
                logger.log(Level.INFO, "Trying to deploy WS [ " + cache.getIPAddress() + ":" + wsdl_port + " ]...");
                try {
                    final ClassLoader classLoader = getClass().getClassLoader();
                    final URL url = classLoader.getResource("lia/Monitor/Farm/deploy.wsdd");
                    AdminClient adm = new AdminClient();
                    adm.process(new Options(new String[] { "-p", wsdl_port, "-h", cache.getIPAddress() }),
                            url.openStream());
                } catch (Throwable t) {
                    wsDeployed = false;
                    logger.log(Level.WARNING, " Deploy [ " + cache.getIPAddress() + ":" + wsdl_port
                            + " ] FAILED! Error: ", t);
                }

                if (wsDeployed) {
                    /*
                     * try { sas.setDoThreads(false); } catch (Exception exp) { exp.printStackTrace (); } // try - catch
                     */
                    BufferedWriter bw = null;
                    FileWriter fw = null;

                    try {

                        if ((MLStatFile != null) && (MLStatFile.length() > 0)) {
                            fw = new FileWriter(MLStatFile, true);
                            bw = new BufferedWriter(fw);
                            bw.write("wsdl_Port = " + wsdl_port + "\n");
                        } else {
                            logger.log(Level.WARNING,
                                    " [ Start Axis Server ] [ HANDLED ] Could not write wsdl_Port to MLStatFile [ "
                                            + MLStatFile + " ]");
                        }
                    } catch (Throwable tml) {
                        logger.log(Level.WARNING, " [ Start Axis Server ] Could not write wsdl_Port to MLStatFile [ "
                                + MLStatFile + " ]", tml);
                    } finally {
                        if (bw != null) {
                            try {
                                bw.close();
                            } catch (Throwable ignore) {
                            }
                        }

                        if (fw != null) {
                            try {
                                fw.close();
                            } catch (Throwable ignore) {
                            }
                        }
                    }
                    logger.log(Level.INFO, " WS [ " + cache.getIPAddress() + ":" + wsdl_port + " ] STARTED!");
                }

            }

        }

        if (AppConfig.getb("lia.Monitor.StartAbing", false)) {
            try {
                AppAbing aa = new AppAbing();
                logger.log(Level.INFO, "\n\n ABing Started ! ");
                aa.init(null);
                aa.start();
            } catch (Throwable t1) {
                t1.printStackTrace();
            }
        }

        final boolean startTomcat = AppConfig.getb("lia.Monitor.startTomcat", false);

        if (startTomcat) {
            logger.log(Level.FINE, "Starting Tomcat");

            tomcatThread = new Thread() {

                @Override
                public void run() {
                    setName("(ML) ServiceTomcatStarter");

                    try {
                        sleep(1000 * 20);
                    } catch (InterruptedException ie) {
                        // it's safe to assume it doesn't happen
                    }

                    try {
                        // use the fully qualified class name here to be free to not include Tomcat in the distribution
                        org.apache.catalina.startup.Bootstrap.main(new String[] { "start" });
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " [ FarmMonitor ] Cannot start Tomcat", t);
                    }
                }
            };

            tomcatThread.start();
        }

        if (AppConfig.getb("lia.app.start", false)) {
            try {
                appControlHandler = AppControl.getInstance();
                appControlPort = AppControl.appControlPort;
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Cannot start APP Control Interface", t);
            }
        }

        if (!disableConfVerifier && (modulesTimeoutConfig.size() > 0)) {
            try {
                logger.log(Level.INFO, " [ FarmMonitor ] There are " + modulesTimeoutConfig.size()
                        + " timeout modules [ " + modulesTimeoutConfig.keySet().toString()
                        + " ] configured; will start ConfVerifier");
                confVerifier = ConfVerifier.initInstance(this.farm, clustersTimeoutMap);
            } catch (Throwable t1) {
                logger.log(Level.WARNING, " [ FarmMonitor ] Got Exception while starting ConfVerifier Thread", t1);
            }
        } else {
            logger.log(Level.INFO, " [ FarmMonitor ] ConfVerifier not started ....");
        }

        new Thread() {

            @Override
            public void run() {
                try {
                    setName("(ML) Env checker ...");
                    send_info(cfgFiles);
                } catch (Throwable ignore) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "[ ML Env Checker ] Got exc ", ignore);
                    }
                }
            }
        }.start();

        final Thread farmMonitorThread = new Thread(this, "(ML) FarmMonitor @ " + FarmName);
        try {
            farmMonitorThread.setDaemon(true);
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ FarmMonitor ] Cannot setDaemon", t);
        }
        farmMonitorThread.start();

    }

    public MFarm getMFarm() {
        return farm;
    }

    private void initMonitorFilter(final MonitorFilter filter) throws Exception {
        filter.initCache(cache);
        cache.addFilter(filter);
    }

    /**
     * Helper function to instantiate internal filters. Logs exception in case smth bad (e.g. snow in summer) happens
     * It becomes very verbose if it has the right logging level. By default, only success is hailed ;)
     * 
     * @param fullClassName
     * @param bDefaultInit
     * @return The filter, or <code>null</code> if filter was not instantiated
     */
    private MonitorFilter initInternalFilter(final String fullClassName, boolean bDefaultInit) {
        try {
            final boolean bLogFinest = logger.isLoggable(Level.FINEST);
            final boolean bLogFiner = bLogFinest || logger.isLoggable(Level.FINER);

            if (AppConfig.getb(fullClassName, bDefaultInit)) {
                if (bLogFinest) {
                    logger.log(Level.FINEST, "Initiating internal filter: " + fullClassName + " ...");
                }
                Class<?> cf = Class.forName(fullClassName);
                Class<?> params[] = new Class[] { String.class };
                final Constructor<?> ct = cf.getConstructor(params);
                final String[] argList = new String[] { FarmName };
                final MonitorFilter zdaFilter = (MonitorFilter) ct.newInstance((Object[]) argList);
                initMonitorFilter(zdaFilter);
                logger.log(Level.INFO, "Internal filter " + fullClassName + " instantiated");
                return zdaFilter;
            }
            if (bLogFiner) {
                logger.log(Level.FINER, fullClassName + " disabled in config");
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ FarmMonitor ] [ HANDLED ] Unable to instantiate internal filter ("
                    + fullClassName + ")! Cause:", t);
        }
        return null;
    }

    private void startInternalFilters() throws Exception {

        // MFilter2
        initInternalFilter("lia.Monitor.Filters.MFilter2", true);

        cache.updateDBStores(other_dbStores);

        // MLMemWatcher
        initInternalFilter("lia.Monitor.Filters.MLMemWatcher", isVRVSFarm);

        // MemoryLeakProducer
        initInternalFilter("lia.Monitor.Filters.MemoryLeakProducer", false);

        // LocalMonitorFilter
        initInternalFilter("lia.Monitor.Filters.LocalMonitorFilter", true);

        // TriggerAgent
        initInternalFilter("lia.Monitor.Filters.TriggerAgent", false);

        // VrvsRestartTrigger
        initInternalFilter("lia.Monitor.Filters.VrvsRestartTrigger", false);

        // DC04Filter
        initInternalFilter("lia.Monitor.Filters.DC04Filter", false);

        // Apache Filter
        try {
            String sApache = AppConfig.getProperty("lia.Monitor.Filters.ApacheWatcher", "");
            if ((sApache != null) && (sApache.trim().length() > 0)) {
                StringTokenizer st = new StringTokenizer(sApache, " ");

                while (st.hasMoreTokens()) {
                    String s = st.nextToken();

                    if (s.indexOf(":") > 0) {
                        initMonitorFilter(new ApacheWatcher(s.substring(0, s.indexOf(":")),
                                s.substring(s.indexOf(":") + 1)));
                    }
                }
            }
        } catch (Throwable t) {
            logger.log(
                    Level.WARNING,
                    "[ FarmMonitor ] [ HANDLED ] Unable to instantiate internal filter (lia.Monitor.Filters.ApacheWatcher)! Cause:",
                    t);
        }

        // AliEnFilter
        initInternalFilter("lia.Monitor.Filters.AliEnFilter", false);

        // CrabFilter
        initInternalFilter("lia.Monitor.Filters.CrabFilter", false);

        // FDTFilter
        initInternalFilter("lia.Monitor.Filters.FDTFilter", false);

        // LisaFDTFilter
        initInternalFilter("lia.Monitor.Filters.LisaFDTFilter", false);

        // CienaAlarmTrigger
        initInternalFilter("lia.Monitor.Filters.CienaAlarmTrigger", false);

        // CienaOsrpTopo
        initInternalFilter("lia.Monitor.ciena.osrp.OsrpTopoFilter", false);

        // CienaSNCFilter
        initInternalFilter("lia.Monitor.ciena.circuits.CienaSNCFilter", false);

        // VCGServiceCheckFilter - did I mention how happy I am
        try {
            final EFlowStatsConsumer f = (EFlowStatsConsumer) initInternalFilter(
                    "lia.Monitor.ciena.eflow.VCGServiceCheckFilter", false);
            if (f != null) {
                EFlowStatsMgr.getInstance().registerConsumer(f);
            }
        } catch (Throwable t) {
            logger.log(Level.INFO, "[ FarmMonitor ] [ HANDLED ] Unable to instantiate the EFlowStatsMgr!!! Cause: ", t);
        }

        // CMSFilter
        initInternalFilter("lia.Monitor.Filters.CMSFilter", false);

        // MLPingWatcher
        initInternalFilter("lia.Monitor.Filters.MLPingWatcher", false);

        // MLPingTrigger - the new MLPingWatcher
        initInternalFilter("lia.Monitor.Filters.MLPing.MLPingWatcher", false);

        try {
            final String sRepository = AppConfig.getProperty("lia.Monitor.Filters.RepositoryWatcher", "");
            if ((sRepository != null) && (sRepository.trim().length() > 0)) {
                StringTokenizer st = new StringTokenizer(sRepository, " ");

                while (st.hasMoreTokens()) {
                    String s = st.nextToken();

                    if (s.indexOf(":") > 0) {
                        initMonitorFilter(new RepositoryWatcher(s.substring(0, s.indexOf(":")), s.substring(s
                                .indexOf(":") + 1)));
                    }
                }
            }
        } catch (Throwable t) {
            logger.log(
                    Level.WARNING,
                    "[ FarmMonitor ] [ HANDLED ] Unable to instantiate internal filter (lia.Monitor.Filters.RepositoryWatcher)! Cause:",
                    t);
        }

        // activates the Alerts-Actions Manager
        try {
            final String sActionsManager = AppConfig.getProperty("lia.util.actions.base_folder");
            if ((sActionsManager != null) && (sActionsManager.trim().length() > 0)) {
                DataReceiver dr = null;
                try {
                    dr = ActionsManager.getInstance();
                } catch (Throwable t) {
                    logger.log(Level.INFO, " [ FarmMonitor ] [ HANDLED ] Unable to instantiate ActionsManager", t);
                    dr = null;
                }

                if (dr != null) {
                    addDataReceiver(dr);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING,
                    "[ FarmMonitor ] [ HANDLED ] Unable to instantiate internal ActionsManager filter! Cause:", t);
        }
    }

    // add filters defined in usr_code
    public void addExternalFilters() {
        String[] externalFilters = AppConfig.getVectorProperty("lia.Monitor.ExternalFilters");
        if ((externalFilters == null) || (externalFilters.length == 0)) {
            logger.log(Level.INFO, "[ addExternalFilters ] No External Filters defined");
            return;
        }

        for (final String filterName : externalFilters) {
            logger.log(Level.INFO, "[addExternalFilters ] Trying to instantiate External Filter " + filterName);

            MonitorFilter extFilter = null;
            try {
                if (externalClassLoader != null) {
                    Class<?> pfilter = externalClassLoader.loadClass(filterName);
                    Class<?> superClass = pfilter.getSuperclass();
                    if ((superClass == null) || !superClass.getName().equals("lia.Monitor.Filters.GenericMLFilter")) {
                        logger.log(Level.WARNING, "\n\nFilter " + filterName
                                + " MUST have lia.Monitor.Filters.GenericMLFilter as it's superclass!!"
                                + "\nIt will NOT be instantiated!!\n");
                        continue;
                    }

                    final Class<?>[] cParams = new Class[] { String.class };
                    final Constructor<?> cons = pfilter.getConstructor(cParams);
                    Object[] params = new Object[] { FarmName };
                    extFilter = (MonitorFilter) cons.newInstance(params);
                    initMonitorFilter(extFilter);
                    logger.log(Level.INFO, "[ addExternalFilters ] Started an External Filter [ " + filterName + " ]");
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[addExternalFilters ] Cannot instantiate EXTERNAL Filter " + filterName, t);
            }

            logger.log(Level.INFO, "[addExternalFilters ] Finished loading External Filter " + filterName + " [ "
                    + extFilter + " ] ");
        }
    }

    public void addExtMod(String moduleName, String params, String repTime) {

        try {
            if (externalModules == null) {
                externalModules = new String[1];
                externalModParams = new String[1];
                externalModRTime = new String[1];
                externalModules[0] = moduleName;
                externalModRTime[0] = repTime;
                externalModParams[0] = params;
            } else {

                // moduleNames
                String[] temp = new String[externalModules.length + 1];
                System.arraycopy(externalModules, 0, temp, 0, externalModules.length);
                temp[externalModules.length] = moduleName;
                externalModules = temp;

                temp = new String[externalModParams.length + 1];
                System.arraycopy(externalModParams, 0, temp, 0, externalModParams.length);
                temp[externalModParams.length] = params;
                externalModParams = temp;

                temp = new String[externalModRTime.length + 1];
                System.arraycopy(externalModRTime, 0, temp, 0, externalModRTime.length);
                temp[externalModRTime.length] = repTime;
                externalModRTime = temp;
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " FarmMonitor: Got exception adding generic module " + moduleName, t);
        }
    }

    private static final void updateVRVSProps() {
        if (isVRVSFarm) {
            StringBuilder sb = new StringBuilder();

            boolean mlPropUpdated = false;

            sb.append("\n\n" + new Date() + " / " + new Date(NTPDate.currentTimeMillis()) + "Updating ml.properties");
            try {
                final File mlPropFile = new File(Farm_home + File.separator + "ml.properties");
                final File farmHomeFile = new File(Farm_home);

                PropertiesUtil.addModifyPropertyFile(mlPropFile, farmHomeFile, "lia.Monitor.memory_store_only", "true",
                        "# Use only in memory DB", false, true);

                // ////////////////////////////
                // commented since ML 1.8.10
                // //////////////////////////

                // PropertiesUtil.addModifyPropertyFile(mlPropFile, farmHomeFile, "lia.Monitor.VrvsRestartScript",
                // "${VRVS_HOME}/restart_mcu", "# Path to the script what will restart the reflector from MonALISA",
                // false, true);
                // PropertiesUtil.addModifyPropertyFile(mlPropFile, farmHomeFile, "lia.Monitor.useVrvsRestartScript",
                // "true", "# Whether to use or not VrvsRestartScript", false, true);
                // PropertiesUtil.addModifyPropertyFile(mlPropFile, farmHomeFile, "lia.Monitor.H323RestartScript",
                // "${VRVS_HOME}/restart_h323", "# Path to the script what will restart the H323 agent from MonALISA",
                // true, true);
                // PropertiesUtil.addModifyPropertyFile(mlPropFile, farmHomeFile, "lia.Monitor.useH323RestartScript",
                // "true", "# Whether to use or not H323RestartScript", false, true);
                // PropertiesUtil.addModifyPropertyFile(mlPropFile, farmHomeFile, "handlers",
                // "java.util.logging.FileHandler", null, true, true);
                // PropertiesUtil.addModifyPropertyFile(mlPropFile, farmHomeFile,
                // "java.util.logging.FileHandler.formatter", "java.util.logging.SimpleFormatter", null, false, true);
                // PropertiesUtil.addModifyPropertyFile(mlPropFile, farmHomeFile,
                // "java.util.logging.FileHandler.formatter", "java.util.logging.SimpleFormatter", null, false, true);
                // PropertiesUtil.addModifyPropertyFile(mlPropFile, farmHomeFile, "java.util.logging.FileHandler.limit",
                // "1000000", null, false, true);
                // PropertiesUtil.addModifyPropertyFile(mlPropFile, farmHomeFile, "java.util.logging.FileHandler.count",
                // "4", null, false, true);
                // PropertiesUtil.addModifyPropertyFile(mlPropFile, farmHomeFile,
                // "java.util.logging.FileHandler.append", "true", null, false, true);
                // PropertiesUtil.addModifyPropertyFile(mlPropFile, farmHomeFile,
                // "java.util.logging.FileHandler.pattern", "ML%g.log", null, false, true);
                // PropertiesUtil.addModifyPropertyFile(mlPropFile, farmHomeFile,
                // "lia.Monitor.Farm.notifyConfigeResults", "true", "#Notify eResult-s to the clients", false, true);
                mlPropUpdated = true;
            } catch (Throwable t) {
                mlPropUpdated = false;
                sb.append("\n" + new Date() + " / " + new Date(NTPDate.currentTimeMillis())
                        + "Updating ml.properties EXCEPTION!" + t);
            }
            sb.append("\n" + new Date() + " / " + new Date(NTPDate.currentTimeMillis())
                    + "Updating ml.properties FINISHED [ mlPropUpdated = " + mlPropUpdated + " ]\n\n ");

            if (mlPropUpdated) {
                try {
                    AppConfig.reloadProps();
                } catch (Throwable t) {
                    sb.append("\n Got EXCEPTION reload()-ing AppConfig!");
                }
                sb.append("\n FINISHED reload()-ing AppConfig!");
            }

            Utils.addFileContentToStringBuilder(Farm_home + File.separator + "ml.properties", sb);

            try {
                String subjToAdd = getStandardEmailSubject();
                MailFactory.getMailSender().sendMessage(FarmMonitor.realFromAddress, "ramiro@monalisa.cern.ch",
                        new String[] { "ramiro@monalisa.cern.ch" }, " [ MLVRVS UPDATE STATUS ] @ " + subjToAdd,
                        sb.toString());
            } catch (Throwable t1) {
            }
        }
    }

    public void addOtherDBStores(dbStore dbs) {
        other_dbStores.add(dbs);
        if (cache != null) {
            cache.updateDBStores(other_dbStores);
        }
    }

    /**
     * @throws java.rmi.RemoteException
     */
    @Override
    public void stopML() throws java.rmi.RemoteException {
        shouldStop.set(true);
    }

    /**
     * @throws java.rmi.RemoteException
     */
    @Override
    public void restartML() throws java.rmi.RemoteException {
        shouldUpdate.set(true);
    }

    public boolean shouldUpdate() {
        return shouldUpdate.get();
    }

    public boolean shouldStop() {
        return shouldStop.get();
    }

    /**
     * @throws java.rmi.RemoteException
     */
    public int getRegistryPort() throws java.rmi.RemoteException {
        return RegFarmMonitor.REGISTRY_PORT;
    }

    public static final ServiceItem getServiceItem() {
        return RegFarmMonitor.getServiceItem();
    }

    /**
     * @throws java.rmi.RemoteException
     */
    @Override
    public Vector<MFarm> getConfig() throws java.rmi.RemoteException {
        Vector<MFarm> a = new Vector<MFarm>();
        a.add(farm);
        return a;
    }

    /**
     * @throws java.rmi.RemoteException
     */
    @Override
    public String getUnitName() throws java.rmi.RemoteException {
        return FarmName;
    }

    public DataStore getDataStore() {
        return cache;
    }

    private final void addDataReceivers() {
        // adds additional data receivers
        String[] recs = AppConfig.getVectorProperty("lia.Monitor.DataReceivers");
        if (recs == null) {
            return;
        }
        for (String rec : recs) {
            addDataReceiver(rec);
        }
    }

    public static final void checkAndLoadLocalEnvironment() {
        final URL[] usr_code_base = get_usr_URLs();

        URLClassLoader urlCL = null;
        if (usr_code_base != null) {
            try {
                urlCL = new URLClassLoader(usr_code_base, Class.forName("lia.Monitor.Farm.FarmMonitor")
                        .getClassLoader());
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ checkAndLoadLocalEnvironment ] Cannot instantiate external ClassLoader",
                        t);
            }
        } else {
            logger.log(Level.INFO, " [ checkAndLoadLocalEnvironment ] No external URLs defined!");
        }

        externalClassLoader = urlCL;
    }

    private final void addDataReceiver(final DataReceiver dr) {
        final DataReceiver[] newReceivers = new DataReceiver[(receivers == null) ? 1 : receivers.length + 1];

        if (receivers == null) {
            newReceivers[0] = dr;
        } else {
            System.arraycopy(receivers, 0, newReceivers, 0, receivers.length);
            newReceivers[receivers.length] = dr;
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, " [ FarmMonitor ] [ addDataReceiver ]  Add Data Receiver " + dr);
        }

        receivers = newReceivers;
    }

    private final void addDataReceiver(final String cl) {
        if (externalClassLoader == null) {
            return;
        }

        try {

            Class<?> cla = externalClassLoader.loadClass(cl);
            DataReceiver dr = (DataReceiver) cla.newInstance();
            addDataReceiver(dr);
            return;
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ addDataReceiver ] Failed to load Data Receiver ClassName= " + cl
                    + " Cause: \n", t);
        }

    }

    private static final URL[] get_usr_URLs() {
        ArrayList<URL> _returnURLs = new ArrayList<URL>();

        try {
            String[] strURL = AppConfig.getVectorProperty("lia.Monitor.CLASSURLs");
            if ((strURL != null) && (strURL.length != 0)) {

                for (String element : strURL) {
                    try {
                        _returnURLs.add(new URL(element));
                    } catch (MalformedURLException ex) {
                        logger.log(Level.INFO,
                                " [ FarmMonitor ] [ get_usr_URLs ] [ HANDLED ] Got MalformedURLException adding URL [ "
                                        + element + " ] to the class loader. This URL will be ignored", ex);
                    } catch (Throwable t) {
                        logger.log(Level.INFO,
                                " [ FarmMonitor ] [ get_usr_URLs ] [ HANDLED ] Got exception adding URL [ " + element
                                        + " ] to the class loader. This URL will be ignored!", t);
                    }
                }
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ FarmMonitor ] [ get_usr_URLs ] returning: " + _returnURLs.toString());
            }
            return _returnURLs.toArray(new URL[_returnURLs.size()]);

        } catch (Throwable t) { /* this should not happened */
            logger.log(Level.WARNING, "GOT General Exception parsing URL-s defined in lia.Monitor.CLASSURLs ", t);
        }

        return null;
    }

    /**
     * @throws java.rmi.RemoteException
     */
    @Override
    synchronized public String ConfigAdd(String cluster, String node, String module, long time)
            throws java.rmi.RemoteException {
        if (cluster == null) {
            return "  Error >>>  cluster can not be null ";
        }

        MNode[] nns = ed.getOrCreate(cluster, node);

        if (nns == null) {
            return " >>>> Successful in adding Cluster " + cluster;
            // return " Error >>> the node list is null " ;
        }

        // this condition determines that we want to change the repetation time
        // for some module
        // as indicated by the param module
        // if the cluster and node are "*" this indictes that we want to change
        // the
        // repetation time of this module where ever it is executing
        if (time > 0) {
            if (module == null) {
                return " Error >>> No Module fullfilled the criteria ";

            }
            for (MNode nn : nns) {
                taskManager.changeRepTime(nn, module, time);
            }
            return " >>>> Successful in chaging repetation time for the module";

        }

        int k = 0;

        // just to indicate that we wanted to start a module ????
        boolean moduleFlag = false;
        boolean wrongIP = false;

        for (MNode nn : nns) {
            if (nn.getIPaddress() == null) {
                taskManager.getIPaddress(nn, true);
                if (nn.getIPaddress() == null) {
                    ed.removeNode(cluster, nn);
                    wrongIP = true;
                    continue;
                }
            }
            if (module != null) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, " trying to create module " + module);
                }

                MonModuleInfo info = taskManager.createModule(module, nn);
                if (info != null) {
                    ed.addModule(nn, module, info.ResTypes);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, " Succed to create module " + module);
                    }
                    k++;
                    moduleFlag = true;
                }
            }
        }

        // to update the farm config at FarmMonitorControl
        if (moduleFlag) {
            return " >>>> Added " + k + "  modules ";
        }
        if (wrongIP) {
            return " Error >>> the IP address for node could not be resolved";
        }
        return " >>> Successful in adding componenets ";
    }

    /**
     * @throws java.rmi.RemoteException
     */
    @Override
    public boolean isVRVSFarm() throws java.rmi.RemoteException {
        return isVRVSFarm;
    }

    /**
     * @throws java.rmi.RemoteException
     */
    @Override
    synchronized public String updateReflector() throws java.rmi.RemoteException {
        if (vrvsUpdateURL == null) {
            return "Cannot update because vrvsUpdateURL == null!";
        }
        String msgToReturn = "\nUsing vrvsUpdateURL = " + vrvsUpdateURL + "\n";
        String[] args = { "-jnlps", vrvsUpdateURL, "-cachedir", MonaLisa_home + "/VRVS_CACHE_JNLP", "-destdir",
                MonaLisa_home + "/VRVS_CACHE_JAR" };
        msgToReturn += "Reflector " + FarmName + " Updated Status@ Local Time: " + new java.util.Date() + "\n"
                + VRVSUpdater.updateVRVS(args);
        logger.log(Level.INFO, "\n updateReflector STATUS: " + msgToReturn + "\n");
        return msgToReturn;
    }

    @Override
    synchronized public String restartReflector() throws java.rmi.RemoteException {
        throw new RemoteException("NOT_IMPLEMENTED");
    }

    private boolean compareScriptVersion(String scriptName) {

        FileReader fr = null;
        BufferedReader localbr = null;
        BufferedReader jarbr = null;
        InputStreamReader isr = null;
        InputStream is = null;

        try {
            ClassLoader classLoader = getClass().getClassLoader();
            URL jarURL = null;

            jarURL = classLoader.getResource("lia/Monitor/Farm/" + scriptName);

            fr = new FileReader(MonaLisa_home + "/Service/CMD/" + scriptName);
            localbr = new BufferedReader(fr);

            is = jarURL.openStream();
            isr = new InputStreamReader(is);
            jarbr = new BufferedReader(isr);

            final String localVersion = getVersionFromBuffer(localbr);
            final String jarVersion = getVersionFromBuffer(jarbr);

            if (jarVersion == null) {
                return true;
            }
            if (localVersion == null) {
                return false;
            }
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, scriptName + ": localVersion: " + localVersion + " && jarVersion: "
                        + jarVersion + " [ " + jarVersion.compareToIgnoreCase(localVersion) + " ]");
            }

            return (jarVersion.compareToIgnoreCase(localVersion) == 0);

        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ FarmMonitor ] checkScriptVersion " + scriptName + " got exception. Cause ", t);
        } finally {
            Utils.closeIgnoringException(fr);
            Utils.closeIgnoringException(localbr);
            Utils.closeIgnoringException(jarbr);
            Utils.closeIgnoringException(isr);
            Utils.closeIgnoringException(is);
        }

        return true;
    }

    public static String getStandardEmailSubject() {
        String subjToAdd = FarmName + " { ";

        try {
            String groups = AppConfig.getProperty("lia.Monitor.group", null);
            subjToAdd += groups;
        } catch (Throwable t) {
        }

        subjToAdd += " } / ";

        try {
            subjToAdd += InetAddress.getLocalHost().getHostAddress();
        } catch (Throwable t) {
        }

        return subjToAdd;
    }

    private void send_info(ArrayList<String> cfgFiles) {
        String subjToAdd = getStandardEmailSubject();
        StringBuilder sb = new StringBuilder(128 * 1024);// 128k

        try {
            sb.append("\n\n MonaLisa Version: ").append(MonaLisa_version).append(" [ ").append(MonaLisa_vdate)
                    .append(" ] ");
            sb.append(" ==> Using MonaLisa_HOME=").append(MonaLisa_home);

            sb.append("\n\n ******** Last Update Log ******* \n\n");
            String lastUpdateLogFile = AppConfig.getProperty("lia.util.update.Updater.LAST_UDPDATELOG_FILE", null);
            if (lastUpdateLogFile == null) {
                if (Farm_home != null) {
                    lastUpdateLogFile = Farm_home + File.separator + "lastUpdate.log";
                } else {
                    lastUpdateLogFile = "./lastUpdate.log";
                }
            }

            Utils.addFileContentToStringBuilder(lastUpdateLogFile, sb);
            sb.append("\n\n ******** End Last Update Log ******* \n\n");

            sb.append("\n\n ******** Last Shutdown Status ******* \n\n");

            String lastShudownStatusFile = "lastShudownStatus";
            if (Farm_home != null) {
                lastShudownStatusFile = Farm_home + File.separator + "lastShudownStatus";
            }

            Utils.addFileContentToStringBuilder(lastShudownStatusFile, sb);
            sb.append("\n\n ******** End Last Shutdown Status ******* \n\n");

            sb.append("\n\n ******** Java System Props ******* \n\n");
            try {
                Properties sProps = System.getProperties();
                if (sProps == null) {
                    sb.append("[ WARNING ] Null SYSTEM Props");
                } else {
                    for (final Object key : sProps.keySet()) {
                        sb.append("\n").append(key).append(" = ").append(sProps.get(key));
                    }
                }
            } catch (Throwable t) {

            }
            sb.append("\n\n ******** End Java System Props ******* \n\n");

            sb.append("\n\n ******** MonALISA Configuration Files ******* \n\n");
            Utils.addFileContentToStringBuilder(MLStatFile, sb);
            Utils.addFileContentToStringBuilder(MonaLisa_home + File.separator + "Service" + File.separator + "CMD"
                    + File.separator + "ml_env" + ((isVRVSFarm) ? ".VRVS" : ""), sb);
            Utils.addFileContentToStringBuilder(MonaLisa_home + File.separator + "Service" + File.separator + "CMD"
                    + File.separator + "site_env", sb);
            Utils.addFileContentToStringBuilder(Farm_home + File.separator + "ml.properties", sb);
            Utils.addFileContentToStringBuilder(MLStatFile, sb);
            if ((cfgFiles != null) && (cfgFiles.size() > 0)) {
                for (int ci = 0; ci < cfgFiles.size(); ci++) {
                    Utils.addFileContentToStringBuilder(new URL(cfgFiles.get(ci)).getPath(), sb);
                }
            }
            sb.append("\n\n ******** End MonALISA Configuration Files ******* \n\n");

            sb.append("\n\n ******** Local ENV MonALISA user ******* \n\n");
            Utils.appendExternalProcessStatus(
                    new String[] { "/bin/sh", "-c", Utils.getPromptLikeBinShCmd("crontab -l") }, sb);
            Utils.appendExternalProcessStatus(new String[] { "/bin/sh", "-c", EXTENDED_CMD_STATUS }, sb);
            Utils.appendExternalProcessStatus(new String[] { "/bin/sh", "-c", Utils.getPromptLikeBinShCmd("mount") },
                    sb);
            Utils.appendExternalProcessStatus(new String[] { "/bin/sh", "-c", Utils.getPromptLikeBinShCmd("df -h") },
                    sb);
            sb.append("\n\n ******** End Local ENV MonALISA user ******* \n\n");

            try {
                if (AppConfig.getb("lia.Monitor.notifyCrontab", true)) {
                    MailFactory.getMailSender().sendMessage(realFromAddress, "mlcrontab@monalisa.cern.ch",
                            new String[] { "mlcrontab@monalisa.cern.ch" },
                            " [ Update && Startup STATUS ] @ " + subjToAdd, sb.toString());
                }
            } catch (Throwable te) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "[ FarmMonitor ] [ sendMessage ] [ HANDLED ] ", te);
                }
            }
        } catch (Throwable ignoreException) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, " [ FarmMonitor ] [ SendInfo ] [ HANDLED ] ", ignoreException);
            }
        }
    }

    private static final void appendToStatusBuffer(StringBuilder sb, String entryToAppend) {
        sb.append("\n [ ").append(new Date(NTPDate.currentTimeMillis())).append(" ] => ").append(entryToAppend)
                .append(" <= \n");
    }

    private String update_usr_code(String fileName, String dir) {

        StringBuilder sb = new StringBuilder(8192);
        String subjToAdd = getStandardEmailSubject();

        if ((new File(MonaLisa_home + "/Service/usr_code/" + dir)).exists()) {
            appendToStatusBuffer(sb, " usr_code/" + dir + " @ " + subjToAdd + " Already EXISTS!!");
            return sb.toString();
        }

        BufferedInputStream src = null;
        FileOutputStream dst = null;
        InputStream jarIS = null;

        try {
            ClassLoader classLoader = getClass().getClassLoader();
            URL jarURL = null;

            byte[] buff = new byte[8192];

            jarURL = classLoader.getResource("lia/Monitor/Farm/" + fileName);

            jarIS = jarURL.openStream();
            src = new BufferedInputStream(jarIS);
            dst = new FileOutputStream(MonaLisa_home + "/Service/usr_code/" + fileName, false);

            appendToStatusBuffer(sb, " Trying to copy [ " + jarURL.toString() + " ] ===> [ " + MonaLisa_home
                    + "/Service/usr_code/" + fileName);

            try {
                for (;;) {
                    int bNO = src.read(buff);
                    if (bNO == -1) {
                        break;
                    }
                    dst.write(buff, 0, bNO);
                }
            } catch (Throwable t) {
                appendToStatusBuffer(sb, "Cannot copy  [ " + jarURL.toString() + " ] ===> [ " + MonaLisa_home
                        + "/Service/usr_code/" + fileName + "\n Cause: \n" + Utils.getStackTrace(t));
                return sb.toString();
            } finally {
                try {
                    dst.flush();
                } catch (Throwable t) {
                }
                Utils.closeIgnoringException(dst);
                Utils.closeIgnoringException(src);
                Utils.closeIgnoringException(jarIS);
            }

        } catch (Throwable t) {
            appendToStatusBuffer(sb,
                    "Got General Exception updating " + fileName + "\n Cause: \n" + Utils.getStackTrace(t));
            return sb.toString();
        }

        String cmd = "cd " + MonaLisa_home + "/Service/usr_code" + "; gunzip < " + fileName + " | tar xvf - ";
        appendToStatusBuffer(sb, " Trying exec: [ " + cmd + " ]");
        try {
            Process pro = MLProcess.exec(new String[] { "/bin/sh", "-c", cmd });
            appendToStatusBuffer(sb, "Waiting for process to finish!");
            pro.waitFor();
            appendToStatusBuffer(sb, "Finished Waiting!");
        } catch (Throwable tt) {
            appendToStatusBuffer(sb, "Got exception executing: " + cmd + "\n\n Exc: \n" + Utils.getStackTrace(tt));
        }

        try {
            appendToStatusBuffer(sb, "Deleting : " + MonaLisa_home + "/Service/usr_code/" + fileName);
            File f = new File(MonaLisa_home + "/Service/usr_code/" + fileName);
            if (f.delete()) {
                appendToStatusBuffer(sb, MonaLisa_home + "/Service/usr_code/" + fileName + " DELETED Successfuly");
            } else {
                appendToStatusBuffer(sb, MonaLisa_home + "/Service/usr_code/" + fileName + " CAN NOT BE DELETED");
            }
        } catch (Throwable tt) {
            appendToStatusBuffer(sb, "Got Exception deleting: " + MonaLisa_home + "/Service/usr_code/" + fileName
                    + "\nExc:\n" + Utils.getStackTrace(tt));
        }

        appendToStatusBuffer(sb, " usr_code/" + dir + " finished update!");

        return sb.toString();
    }

    private String getVersionFromBuffer(BufferedReader buff) {
        try {
            for (;;) {
                String lin = buff.readLine();
                if (lin == null) {
                    break;
                }
                if (lin.indexOf("ML Script Version:") != -1) {
                    return lin.substring(lin.indexOf("ML Script Version:")).trim();
                }
            }
        } catch (Throwable t) {
        }
        return null;
    }

    @Override
    synchronized public String ConfigRemove(String cluster, String node, String module) throws java.rmi.RemoteException {
        // MUST BE REDONE !!! Asif made a horible job HERE ! cil

        // This is stupid ... but we have to use the same interface ... that's the problem
        if ((cluster != null) && (node != null) && (module != null)) {
            if (cluster.equals("VRVS_REMOVE_TOKEN_MDA") && module.equals("VRVS_REMOVE_TOKEN_MDA")) {
                return sendCMDToReflector(node);
            }
        }

        // see if we want to remove the Farm Monitoring unit
        // to Stop the Farm Monitoring service
        // if (cluster == null && node == null && module == null) {
        // en = monitors.elements();
        // System.out.println(monitors.size());
        // while (en.hasMoreElements()) {
        // monitor = (ControlI) en.nextElement();
        // monitor.removeFarm(farm);
        // monitors.remove(monitor);
        // }
        //
        // return ">>>> Farm Monitor unit "
        // + farm.toString()
        // + " removed from the configuration";
        // }
        // if we want to remove a cluster
        if ((cluster != null) && (node == null) && (module == null)) {
            try {
                MNode[] n = ed.getOrCreate(cluster, "*");
                for (MNode nod : n) {
                    Vector<String> moduleList = nod.getModuleList();
                    for (final String modName : moduleList) {
                        taskManager.deleteModule(modName, nod);
                    }
                    ed.removeNode(cluster, nod);

                }
                ed.removeCluster(cluster);
            } catch (Exception ee) {
                return " Error >>> Failed to remove cluster " + cluster;
            }

            return " >>>> Removed cluster " + cluster + " on Farm  " + farm.toString();

        } // removing a node and all its modules
        else if ((cluster != null) && (node != null) && (module == null)) {
            try {
                MNode[] n = ed.getOrCreate(cluster, node);
                if (n == null) {
                    return " Error >>> No Node with name  " + node + " exists in Cluster " + cluster;
                }
                MNode nod = n[0];
                Vector<String> moduleList = nod.getModuleList();
                for (final String modName : moduleList) {
                    taskManager.deleteModule(modName, nod);
                }
                ed.removeNode(cluster, nod);
            } catch (Exception e) {
                return " Error >>> Failed to remove Node  " + node + " From Cluster " + cluster;

            }

            return ">>>>  Removed Node  " + node + "  from  Cluster  " + cluster;

        } // if we want to stop a module for a node
        else if ((cluster != null) && (node != null) && (module != null)) {
            try {
                MNode[] n = ed.getOrCreate(cluster, node);
                if (n == null) {
                    return " Error >>> No Node with name  " + node + " exists in Cluster " + cluster;
                }
                MNode nod = n[0];
                taskManager.deleteModule(module, nod);
            } catch (Exception e) {
                return " Error >>>  Failed to remove Module  " + module + "  from  Node  " + node;
            }

            return " >>>> Removed  Module  " + module + "  from Node  " + node;

        }

        return "Error >>> Failed to recognise Request for action ..........";

    }

    // this method registers a Monitor Control unit (GUI) with this FarmMonitor
    // these units are updated for the changes which take place on this Farm
    /**
     * @throws RemoteException
     */
    @Override
    public MFarm init(/* ControlI mc */) throws RemoteException {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, " added Control I ");
        }

        // if (!monitors.contains(mc))
        // monitors.add(mc);
        try {
            // mc.updateConfig ( farm, FarmName ) ;

            // mc.addFarm(farm);
            return farm;
            // sendUpdate();
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "failed to update config ! ", t);
            }
        }
        return null;
    }

    private final class StreamReader extends Thread {

        private final StringBuilder dst;

        private final String prefix;

        private final AtomicBoolean hasToRun = new AtomicBoolean(true);

        private final BufferedReader br;

        private final AtomicBoolean finished;

        StreamReader(String name, BufferedReader br, String prefix) {
            super(" ( ML ) StreamReader :- " + ((name == null) ? "null" : name));
            this.br = br;
            this.dst = new StringBuilder();
            this.prefix = prefix;
            finished = new AtomicBoolean(false);
        }

        public void stopIt() {
            this.hasToRun.set(false);
        }

        private boolean checkStatus() {
            return hasToRun.get();
        }

        @Override
        public void run() {
            try {
                String line = null;
                while (checkStatus()) {
                    try {
                        if (br.ready()) {
                            while (br.ready() && ((line = br.readLine()) != null)) {
                                dst.append(prefix).append(line).append("\n");
                            }
                        } else {
                            try {
                                Thread.sleep(100);
                            } catch (Throwable t1) {
                            }
                        }
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " Exc ", t);
                    }
                }// while

                // last feed
                try {
                    while (br.ready() && ((line = br.readLine()) != null)) {
                        dst.append(prefix).append(line).append("\n");
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " Exc ", t);
                }

                try {
                    br.close();
                } catch (Throwable t) {
                }
            } finally {
                finished.set(true);
            }
        }

        public StringBuilder getStatus() {
            while (!finished.get()) {
                try {
                    Thread.sleep(100);
                } catch (Throwable t) {
                }
            }
            return dst;
        }
    }

    private String sendCMDToReflector(String cmdName) throws java.rmi.RemoteException {
        if (cmdName == null) {
            throw new RemoteException("No Action Specified");
        } else if (!cmdName.equals("start") && !cmdName.equals("stop") && !cmdName.equals("status")
                && !cmdName.equals("restart")) {
            throw new RemoteException("No SUCH Action ... Should be either stop | start | status | restart");
        }

        StringBuilder sb = new StringBuilder();
        String cmd = null;

        if (cmdName.equals("stop")) {
            cmd = AppConfig.getProperty("lia.Monitor.VrvsStopScript", null);
        } else if (cmdName.equals("start")) {
            cmd = AppConfig.getProperty("lia.Monitor.VrvsStartScript", null);
        } else if (cmdName.equals("status")) {
            cmd = AppConfig.getProperty("lia.Monitor.VrvsStatusScript", null);
        } else if (cmdName.equals("restart")) {
            cmd = AppConfig.getProperty("lia.Monitor.VrvsRestartScript", null);
        }

        sb.append("\nReflector :- ").append(FarmName);
        sb.append(" remote cmd = [ ").append(cmdName).append(" ] @ LocalTime: ");
        sb.append(new Date()).append("\n");

        if (cmd == null) {
            sb.append("The command for [ ").append(cmdName).append(" ] is not set in ml.properties ...\n");
            throw new RemoteException(sb.toString());
        }

        sb.append("Using the following command [").append(cmd).append("] on local system\n");

        try {
            logger.log(Level.INFO, "Vrvs action = [ " + cmd + " ] starting...\n");
            Process procVrvs = MLProcess.exec(cmd);

            InputStream is = procVrvs.getInputStream();
            InputStream es = procVrvs.getErrorStream();

            StreamReader stdout = new StreamReader(" STDOUT for " + cmd, new BufferedReader(new InputStreamReader(is)),
                    "STDOUT > ");
            StreamReader stderr = new StreamReader(" STDERR for " + cmd, new BufferedReader(new InputStreamReader(es)),
                    "STDERR > ");

            stdout.start();
            stderr.start();

            procVrvs.waitFor();

            stdout.stopIt();
            stderr.stopIt();

            sb.append("\n[" + cmd + "] Remote OUTPUT:\n");
            sb.append(stdout.getStatus());
            sb.append(stderr.getStatus());

            logger.log(Level.INFO, "Vrvs action = [ " + cmd + " ] terminated\n");
        } catch (Throwable t) {
            sb.append("\nRemote call FAILED! Err: " + t.getMessage());
            logger.log(Level.WARNING, " [ " + cmdName + " ] STATUS: " + sb.toString(), t);
            throw new RemoteException(sb.toString());
        }

        sb.append("\nRemote call STATUS = OK");
        logger.log(Level.INFO, " [ " + cmd + " ] STATUS: " + sb.toString());
        return sb.toString();

    }

    @Override
    public void remove(String name) throws java.rmi.RemoteException {
        // monitors.remove(name);
        throw new RemoteException("NOT_IMPLEMENTED");
    }

    public void addResult(Object r) {
        if (r instanceof Collection<?>) {
            addResults((Collection<?>) r);
        } else {
            try {
                StringFactory.convert(r);
            } catch (Throwable _) {
            }

            if (!rtResults.offer(r)) {
                logger.log(Level.WARNING, "[ FarmMonitor ] [ addResult ] The result " + r
                        + " was ignored .... Queue is FULL!");
            }
        }
    }

    private void notifyReceiver(final DataReceiver rcv, Object o) throws Exception {
        if (o instanceof Result) {
            final Result r = (Result) o;
            if ((r.Module == null) || (r.Module.trim().length() == 0)) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " [ FarmMonitor ] [ notifyReceiver ] setting module name to: "
                            + MON_UNKOWN_NAME + " for r: " + r);
                }
                r.Module = MON_UNKOWN_NAME;
                UNK_RESULTS_MODULE_COUNT.incrementAndGet();
            }
            rcv.addResult(r);
        } else if (o instanceof eResult) {
            if (checkForConfigResult((eResult) o)) {
                if (notifyConfigeResults) {
                    rcv.addResult((eResult) o);
                }
                return;
            }
            rcv.addResult((eResult) o);
        } else if (o instanceof ExtResult) {
            rcv.addResult((ExtResult) o);
        } else if (o instanceof AccountingResult) {
            rcv.addResult((AccountingResult) o);
        } else if (o instanceof Collection<?>) {
            final Collection<?> c = (Collection<?>) o;
            for (Object r : c) {
                if (r != null) {
                    notifyReceiver(rcv, r);
                }
            }
        }
    }

    private void notifyReceivers() throws InterruptedException {

        final Object o = rtResults.take();

        if (o == null) {
            return;
        }

        for (DataReceiver receiver : receivers) {
            try {
                notifyReceiver(receiver, o);
            } catch (Throwable t) {
                logger.log(Level.INFO, "FarmMonitor: error notifying receiver", t);
            }
        }
    }

    private boolean checkForConfigResult(eResult er) throws Exception {

        if (er == null) {
            return false;
        }
        if (er.ClusterName == null) {
            return false;
        }

        boolean retb = false;

        synchronized (farm.getConfLock()) {
            final MCluster mc = farm.getCluster(er.ClusterName);

            if (er.NodeName == null) {// should remove all the nodes in the cluster
                if (mc != null) {
                    // this is just a cross check.
                    // the remove code should alwayes be true ...
                    // we already took the farm.getConfLock() ...
                    final boolean bRemove = farm.removeCluster(mc);
                    logger.log(Level.INFO, "[ FarmMonitor ] [ checkResultConf ] removing cluster: " + er.ClusterName
                            + " from config [ Received: " + er + " ]. Remove code: " + bRemove);
                } else {
                    logger.log(Level.INFO, "[ FarmMonitor ] [ checkResultConf ] removing cluster: " + er.ClusterName
                            + " from config [ Received: " + er + " ]. Cluster no longer in conf.");
                }

                return true;
            }

            if (mc == null) {
                if ((er.NodeName != null) && (er.param != null) && (er.param_name != null)) {
                    return false;
                }

                logger.log(Level.INFO, "[ FarmMonitor ] [ checkResultConf ] config result ( " + er + " ) but Cluster: "
                        + er.ClusterName + " no longer in conf.");
                return true;
            }

            final MNode mn = mc.getNode(er.NodeName);
            if ((er.param_name == null) || (er.param == null)) {
                if (mn != null) {
                    final boolean bRemove = mc.removeNode(mn);
                    logger.log(Level.INFO, "[ FarmMonitor ] [ checkResultConf ] removing Node: " + er.NodeName
                            + " / Cluster: " + er.ClusterName + " from config [ Received: " + er + " ]. Remove code: "
                            + bRemove);
                } else {
                    logger.log(Level.INFO, " [ FarmMonitor ] [ checkResultConf ] removing node: " + er.NodeName
                            + " / Cluster: " + er.ClusterName + " [ Received: " + er
                            + " ]. Node in no longer in the config");
                }
                return true;
            }

            if (mn == null) {
                if ((er.param != null) && (er.param_name != null)) {
                    return false;
                }

                logger.log(Level.INFO, "[ FarmMonitor ] [ checkResultConf ] config result ( " + er + " ) but node: "
                        + er.NodeName + " /cluster: " + er.ClusterName + " no longer in conf.");
                return true;
            }

            if (er.param.length != er.param_name.length) {
                logger.log(Level.WARNING, "[ FarmMonitor ] [ checkResultConf ] config result ( " + er
                        + " ) param_name.len != param.len");
                throw new Exception("[ FarmMonitor ] [ checkResultConf ] config result ( " + er
                        + " ) param_name.len != param.len");
            }

            retb = false;
            final Vector<String> params = mn.getParameterList();

            for (int i = 0; i < er.param_name.length; i++) {
                final String ParamName = er.param_name[i];
                if (ParamName == null) {
                    continue;
                }
                if (er.param[i] == null) {
                    boolean bRemove = params.remove(ParamName);
                    logger.log(Level.INFO, "[ FarmMonitor ] [ checkResultConf ] removing Param: " + ParamName
                            + " /Node: " + er.NodeName + " / Cluster: " + er.ClusterName + " from config [ Received: "
                            + er + " ]. Remove code: " + bRemove);
                    retb = true;
                }
            }
        }

        return retb;
    }

    private boolean checkSize(Result r) {

        if (r.ClusterName.length() > MAX_CLUSTERNAME_LEN) {
            logger.log(Level.WARNING, " The ClusterName " + r.ClusterName + " is too big. Max size is "
                    + MAX_CLUSTERNAME_LEN);
            return false;
        }

        if (r.NodeName.length() > MAX_NODENAME_LEN) {
            logger.log(Level.WARNING, " The NodeName " + r.NodeName + " is too big. Max size is " + MAX_NODENAME_LEN);
            return false;
        }

        for (String element : r.param_name) {
            if ((element != null) && (element.length() > MAX_PARAMNAME_LEN)) {
                logger.log(Level.WARNING, " The param_name " + element + " is too big. Max size is "
                        + MAX_PARAMNAME_LEN);
                return false;
            }
        }

        return true;
    }

    public void addResults(Collection<?> c) {
        for (Object name : c) {
            addChecked(name);
        }
    }

    public static final long getTotalResUnkModCount() {
        return UNK_RESULTS_MODULE_COUNT.get();
    }

    private void addChecked(Object o) {

        final long lastCount = farm.modCount();

        Result r = null;
        if (o instanceof Result) {
            r = (Result) o;
        } else if (o instanceof ExtResult) {
            r = ((ExtResult) o).getResult();
        } else if (o instanceof eResult) {
            eResult er = (eResult) o;

            if (er.FarmName == null) {
                er.FarmName = FarmName;
            }

            if (ignoreResultForConfig(er)) {
                addResult(er);
                return;
            }

            if (er.ClusterName == null) {
                logger.log(Level.WARNING, " [ FarmMonitor ] [ addChecked ] ignoring eResult ... cluster is null: " + er);
                return;
            }

            for (final Pattern p : hiddenClusters) {
                if (p.matcher(er.ClusterName).matches()) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "\n\n\n [ FarmMonitor ] Adding hidden eResult " + er);
                    }
                    addResult(er);
                    return;
                }
            }

            boolean bConfResult = ((er.NodeName == null) || (er.param == null) || (er.param_name == null));

            if (bConfResult) {
                addResult(o);
                return;
            }

            r = new Result(FarmName, er.ClusterName, er.NodeName, er.Module, er.param_name);
        } else if (o instanceof AccountingResult) {
            AccountingResult ar = (AccountingResult) o;
            r = new Result(FarmName, ar.sGroup, ar.sUser, "Accounting", new String[] { "job accounting" });
        }

        if ((r.ClusterName == null) || (r.NodeName == null) || (r.param == null) || (r.param_name == null)) {
            logger.log(Level.WARNING, " [ FarmMonitor ] [ addExternal ] IGNORED RESULT! Null elements in Result:\n "
                    + r.toString() + "\n Stack trace: ", new Throwable());
            return;
        }

        if ((r.ClusterName.trim().length() == 0) || (r.NodeName.trim().length() == 0)) {
            logger.log(
                    Level.WARNING,
                    " [ FarmMonitor ] [ addExternal ] IGNORED RESULT! Cluster or Node name in Result:\n "
                            + r.toString() + "\n is blank. Stack trace: ", new Throwable());
            return;
        }

        final int paramNameLen = r.param_name.length;
        final int paramLen = r.param.length;

        if (paramNameLen != paramLen) {
            logger.log(Level.WARNING,
                    " [ FarmMonitor ] [ addExternal ] IGNORED RESULT! Different len for r.param ( " + paramLen
                            + " ) and r.param_name ( " + paramNameLen + " ):\n " + r.toString() + "\n Stack trace: ",
                    new Throwable());
            return;
        }

        if ((paramNameLen <= 0) || (paramLen <= 0)) {
            logger.log(Level.WARNING,
                    " [ FarmMonitor ] [ addExternal ] IGNORED RESULT! The len cannot be zero. for r.param ( "
                            + paramLen + " ) and r.param_name ( " + paramNameLen + " ):\n " + r.toString()
                            + "\n Stack trace: ", new Throwable());
            return;
        }

        for (int i = 0; i < paramNameLen; i++) {
            final String pName = r.param_name[i];
            if (pName.trim().length() == 0) {
                logger.log(Level.WARNING, " [ FarmMonitor ] [ addExternal ] IGNORED RESULT! The r.param_name[ " + i
                        + " ] is blank \n " + r.toString() + "\n Stack trace: ", new Throwable());
                return;
            }
        }

        if (ignoreResultForConfig(r)) {
            addResult(r);
            return;
        }

        for (final Pattern p : hiddenClusters) {
            if (p.matcher(r.ClusterName).matches()) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "\n\n\n [ FarmMonitor ] Adding hidden result " + r);
                }
                addResult(r);
                return;
            }
        }

        if (!checkSize(r)) {
            return;
        }
        r.FarmName = FarmName;

        if ((r.Module == null) || (r.Module.trim().length() == 0)) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ FarmMonitor ] [ addChecked ] setting module name to: " + MON_UNKOWN_NAME
                        + " for r: " + r);
            }
            r.Module = MON_UNKOWN_NAME;
            UNK_RESULTS_MODULE_COUNT.incrementAndGet();
        }

        // it is possible to add clusters/nodes/param by "multiple" threads ...
        // te only reasonable reasone was to sync everything; bleah

        try {
            synchronized (farm.getConfLock()) {

                MCluster cl = farm.getCluster(r.ClusterName);
                if (cl == null) {
                    cl = farm.addClusterIfAbsent(new MCluster(r.ClusterName, farm));
                }
                MNode n = cl.getNode(r.NodeName);
                if (n == null) {
                    n = cl.addNodeIfAbsent(new MNode(r.NodeName, null, cl, farm));
                }

                for (final String paramName : r.param_name) {
                    if (paramName == null) {
                        logger.log(Level.WARNING,
                                " [ FarmMonitor ] [ addExternal ] IGNORED RESULT! null param name :\n " + r.toString()
                                        + "\n Stack trace: ", new Throwable());
                        return;
                    }
                    n.addParamIfAbsent(paramName);
                }

                if (r.Module != null) {
                    n.addModule(r.Module);

                    final Map<String, Long> ht = modulesTimeoutConfig.get(r.Module);

                    if (ht != null) {

                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, " [ FarmMonitor ] result for timeout: " + r);
                        }

                        long paramTimeout = -1;
                        long nodeTimeout = -1;
                        long clusTimeout = -1;

                        try {
                            clusTimeout = ht.get("ClusterTimeout").longValue();
                        } catch (Throwable t) {
                            clusTimeout = -1;
                        }

                        try {
                            nodeTimeout = ht.get("NodeTimeout").longValue();
                        } catch (Throwable t) {
                            nodeTimeout = -1;
                        }

                        try {
                            paramTimeout = ht.get("ParamTimeout").longValue();
                        } catch (Throwable t) {
                            paramTimeout = -1;
                        }

                        // update CCluster time
                        CCluster cc = clustersTimeoutMap.get(r.ClusterName);

                        if (cc == null) {
                            if (clusTimeout > 0) {
                                if (logger.isLoggable(Level.FINER)) {
                                    logger.log(Level.FINER, "[ FarmMonitor ] creating CCluster: " + r.ClusterName);
                                }
                                cc = new CCluster(r.Module, cl, clusTimeout);
                                final CCluster tmpCCluster = clustersTimeoutMap.putIfAbsent(r.ClusterName, cc);
                                if (tmpCCluster != null) {
                                    cc = tmpCCluster;
                                    cc.renew();
                                } else {
                                    if (logger.isLoggable(Level.FINE)) {
                                        logger.log(Level.FINE, "[ FarmMonitor ] [ CREATE CCluster ] " + r.ClusterName);
                                    }
                                    confVerifier.reschedule(cc);
                                }
                            }
                        } else {
                            cc.renew();
                        }

                        if (cc != null) {
                            CNode cn = cc.cNodes.get(r.NodeName);
                            if (cn == null) {
                                if (nodeTimeout > 0) {
                                    if (logger.isLoggable(Level.FINER)) {
                                        logger.log(Level.FINER, " [ FarmMonitor ] creating CNode: " + r.NodeName);
                                    }
                                    cn = new CNode(cc, n, nodeTimeout);
                                    final CNode tmpCNode = cc.cNodes.putIfAbsent(r.NodeName, cn);
                                    // if other thread already added the key ... just update the time
                                    if (tmpCNode != null) {
                                        cn = tmpCNode;
                                        cn.renew();
                                    } else {
                                        if (logger.isLoggable(Level.FINE)) {
                                            logger.log(Level.FINE, " [ FarmMonitor ] [ CREATE CNode ] " + r.NodeName);
                                        }
                                        confVerifier.reschedule(cn);
                                    }
                                }
                            } else {
                                cn.renew();
                            }

                            if (cn != null) {
                                for (final String paramName : r.param_name) {
                                    if (paramName == null) {
                                        continue;
                                    }
                                    CParam cp = cn.cParams.get(paramName);
                                    if (cp == null) {
                                        if (paramTimeout > 0) {
                                            if (logger.isLoggable(Level.FINER)) {
                                                logger.log(Level.FINER, " [ FarmMonitor ] creating CParam: "
                                                        + paramName);
                                            }
                                            cp = new CParam(cn, paramName, paramTimeout);
                                            final CParam tmpCParam = cn.cParams.putIfAbsent(paramName, cp);
                                            // if other thread already added the key ... just update the time
                                            if (tmpCParam != null) {
                                                cp = tmpCParam;
                                                cp.renew();
                                            } else {
                                                if (logger.isLoggable(Level.FINE)) {
                                                    logger.log(Level.FINE, " [ FarmMonitor ] [ CREATE CParam ] "
                                                            + paramName);
                                                }
                                                confVerifier.reschedule(cp);
                                            }
                                        }
                                    } else {
                                        cp.renew();
                                    }
                                }// for param names

                            }// if cn != null

                        }// if cc!=null
                    }// if module should be monitored for timeout

                }

            }// sync
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ FarmMonitor ] [ HANDLED ] Got exception matching result with current config",
                    t);
        }

        addResult(o);

        if (lastCount != farm.modCount()) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Node added/removed Notify new Conf");
            }
        }
    }

    public static final boolean ignoreResultForConfig(final Result r) {
        if (ignoreSomeConfModules && (r.Module != null) && ignoredConfModules.contains(r.Module)) {
            if ((r.ClusterName != null) && !r.ClusterName.equals("MonaLisa")) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "\n\n\n [ FarmMonitor ] Adding hidden result " + r);
                }
                return true;
            }
        }

        return false;
    }

    public static final boolean ignoreResultForConfig(final eResult er) {
        if (ignoreSomeConfModules && (er.Module != null) && ignoredConfModules.contains(er.Module)) {
            if ((er.ClusterName != null) && !er.ClusterName.equals("MonaLisa")) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "\n\n\n [ FarmMonitor ] Adding hidden eResult " + er);
                }
                return true;
                // addResult(er);
                // return;
            }
        }
        return false;
    }

    public long getAddedClustersNo() {
        return farm.getTotalAddedClusters();
    }

    public long getRemovedClustersNo() {
        return farm.getTotalRemovedClusters();
    }

    public long getAddedNodesNo() {
        return farm.getTotalAddedNodes();
    }

    public long getRemovedNodesNo() {
        return farm.getTotalRemovedNodes();
    }

    public long getAddedParamsNo() {
        return farm.getTotalAddedParams();
    }

    public long getRemovedParamsNo() {
        return farm.getTotalRemovedParams();
    }

    @Override
    public void Shutdown() {
        cache.Shutdown();
    }

    @Override
    public void run() {

        // starting the TaskManager
        try {
            taskManager.startMonitoring();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Exception starting monitoring. MLService will stop now. Cause:", t);
            System.exit(1);
        }

        logger.log(Level.INFO, " [ FarmMonitor ] main thread started ... ");

        for (;;) {
            try {
                notifyReceivers();
            } catch (InterruptedException ie) {
                logger.log(Level.WARNING, "FarmMonitor: Got Interrupted Exception MainLoop:", ie);
                Thread.interrupted();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "FarmMonitor: Got Exception MainLoop:", t);
            }
        }
    }

    public void initJModules() {
        // finds the modules in the jar file

        JarFinder finder = new JarFinder("lia.Monitor.monitor.MonitoringModule", MonaLisa_home + File.separator
                + "Service" + File.separator + "lib" + File.separator + "FarmMonitor.jar");
        availableModules = finder.searchForClasses("lia/Monitor/modules");

        if (logger.isLoggable(Level.FINE)) {
            final StringBuilder sb = new StringBuilder();
            sb.append(" Modules in the jar file : \n");
            for (int i = 0; i < availableModules.size(); i++) {
                if (logger.isLoggable(Level.FINE)) {
                    sb.append(availableModules.elementAt(i)).append(" ");
                }
            }
            logger.log(Level.FINE, sb.toString());
        }

        farm.setAvModules(availableModules);
    }

    public static final boolean sendMail(String from, String[] to, String subject, String message,
            boolean appendDefaultSubject) {
        String subj = null;
        if (FarmName != null) {
            subj = " [ " + FarmName + " ]";
        } else {
            subj = "";
        }

        try {
            subj += " [ " + hostname + " / " + InetAddress.getLocalHost().getHostAddress() + " ] ";
        } catch (Throwable t) {
        }

        if (appendDefaultSubject) {
            subj = subj + subject;
        }

        try {
            MailFactory.getMailSender().sendMessage(FarmMonitor.realFromAddress, from, to, subj, message);
        } catch (Throwable t) {
        }
        return true;
    }

    public void startSimpleAxisServer(String args[]) {

        sas = new SimpleAxisServer();

        Options opts = null;
        try {
            opts = new Options(args);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return;
        }

        try {

            int port = opts.getPort();
            ServerSocket ss = null;
            // Try five times
            for (int i = 0; i < 5; i++) {
                try {
                    ss = new ServerSocket(port);
                    break;
                } catch (java.net.BindException be) {
                    be.printStackTrace();
                    if (i < 4) {
                        // At 3 second intervals.
                        Thread.sleep(3000);
                    } else {
                        throw new Exception(Messages.getMessage("unableToStartServer00", Integer.toString(port)));
                    }
                }
            }
            sas.setServerSocket(ss);
            sas.start();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

    } // startSimpleAxisServer

    private static final void reloadHiddenModulesConfig() {
        final TreeSet<String> newSet = new TreeSet<String>();
        try {
            final String ignoredModulesProp = AppConfig.getProperty("lia.Monitor.Farm.IgnoredModulesConfig", null);
            if (ignoredModulesProp == null) {
                return;
            }
            final String[] hc = ignoredModulesProp.split("(\\s)*,(\\s)*");
            if (hc != null) {
                for (String element : hc) {
                    newSet.add(element);
                }
            }
        } finally {
            ignoredConfModules.addAll(newSet);
            ignoredConfModules.retainAll(newSet);
            ignoreSomeConfModules = (ignoredConfModules.size() > 0);
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [ Hidden Modules ] : ignoreSomeConfModules ( " + ignoreSomeConfModules + " ) : "
                    + ignoredConfModules);
        }
    }

    private static final void reloadHiddenClustersConfig() {
        final ArrayList<Pattern> newSet = new ArrayList<Pattern>();

        try {
            final String hiddenClus = AppConfig.getProperty("lia.Monitor.Farm.HiddenClusters", null);
            String hiddenOSWClusterName = AppConfig.getProperty("lia.Monitor.Farm.OSwConfigHiddenCluster",
                    "OSwConfigHCluster");
            String hiddenTopoClusterName = AppConfig.getProperty("lia.Monitor.Farm.TopoConfigHiddenCluster",
                    "TopoConfigHCluster");

            Pattern p = null;
            if (hiddenOSWClusterName == null) {
                p = Pattern.compile("OSwConfigHCluster");
            } else {
                p = Pattern.compile(hiddenOSWClusterName);
            }

            newSet.add(p);

            Pattern p2 = null;
            if (hiddenTopoClusterName == null) {
                p2 = Pattern.compile("TopoConfigHCluster");
            } else {
                p2 = Pattern.compile(hiddenTopoClusterName);
            }
            newSet.add(p2);

            if (hiddenClus != null) {
                final String[] hc = hiddenClus.split("(\\s)*,(\\s)*");
                if ((hc != null) && (hc.length > 0)) {

                    for (String element : hc) {
                        if ((element != null) && (element.length() > 0)) {
                            newSet.add(Pattern.compile(element));
                        }
                    }

                    if (logger.isLoggable(Level.FINE)) {
                        StringBuilder sb = new StringBuilder(1024);
                        sb.append("\n\n");
                        sb.append("HiddenClusters = ");
                        for (final Iterator<Pattern> it = newSet.iterator(); it.hasNext();) {
                            sb.append(it.next()).append(it.hasNext() ? ", " : "");
                        }
                        sb.append("\n\n");
                        logger.log(Level.INFO, sb.toString());
                    }
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ FarmMonitor ]  [ reloadHiddenClustersConfig ] Exception ", t);
        } finally {
            hiddenClusters.addAllAbsent(newSet);
            hiddenClusters.retainAll(newSet);
        }
    }

    private static final void reloadCfg() {
        final String sLevel = AppConfig.getProperty("lia.Monitor.Farm.FarmMonitor.level");
        Level loggingLevel = null;
        if (sLevel != null) {
            try {
                loggingLevel = Level.parse(sLevel);
            } catch (Throwable t) {
                loggingLevel = null;
            }

            logger.setLevel(loggingLevel);
        }

        try {
            reloadHiddenClustersConfig();
        } catch (Throwable t) {
        }

        try {
            reloadHiddenModulesConfig();
        } catch (Throwable t) {
        }

        try {
            vrvsUpdateURL = null;
            try {
                vrvsUpdateURL = AppConfig.getProperty("lia.Monitor.Farm.vrvsUpdateURL",
                        "http://monalisa.cacr.caltech.edu/VRVS_UPDATE");
            } catch (Throwable t) {
                vrvsUpdateURL = "http://monalisa.cacr.caltech.edu/VRVS_UPDATE";
            }

            notifyConfigeResults = false;
            try {
                notifyConfigeResults = AppConfig.getb("lia.Monitor.Farm.notifyConfigeResults", false);
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " (RE)Loaded lia.Monitor.Farm.notifyConfigeResults = "
                            + notifyConfigeResults);
                }
            } catch (Throwable t) {
                notifyConfigeResults = false;
                logger.log(Level.WARNING, " Exception (RE)Loading lia.Monitor.Farm.notifyConfigeResults = "
                        + notifyConfigeResults, t);
            }

            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (Throwable t) {
            }
            realFromAddress = username + "@" + hostname;

            MAX_CFGSTR_LEN = 250;
            try {
                MAX_CFGSTR_LEN = AppConfig.geti("lia.Monitor.Farm.FarmMonitor.MAX_CFGSTR_LEN", MAX_CFGSTR_LEN);
            } catch (Throwable t) {
                MAX_CFGSTR_LEN = 250;
            }

            MAX_NODENAME_LEN = MAX_CFGSTR_LEN;
            try {
                MAX_NODENAME_LEN = AppConfig.geti("lia.Monitor.Farm.FarmMonitor.MAX_NODENAME_LEN", MAX_CFGSTR_LEN);
            } catch (Throwable t) {
                MAX_NODENAME_LEN = MAX_CFGSTR_LEN;
            }

            MAX_CLUSTERNAME_LEN = MAX_CFGSTR_LEN;
            try {
                MAX_CLUSTERNAME_LEN = AppConfig
                        .geti("lia.Monitor.Farm.FarmMonitor.MAX_CLUSTERNAME_LEN", MAX_CFGSTR_LEN);
            } catch (Throwable t) {
                MAX_CLUSTERNAME_LEN = MAX_CFGSTR_LEN;
            }

            MAX_PARAMNAME_LEN = MAX_CFGSTR_LEN;
            try {
                MAX_PARAMNAME_LEN = AppConfig.geti("lia.Monitor.Farm.FarmMonitor.MAX_PARAMNAME_LEN", MAX_CFGSTR_LEN);
            } catch (Throwable t) {
                MAX_PARAMNAME_LEN = MAX_CFGSTR_LEN;
            }

        } catch (Throwable t) {
        }
    }

    static public void main(String args[]) {

        if (args.length >= 2) {

            try {
                final FarmMonitor fm = new FarmMonitor(args);
                System.out.println("FarmMonitor " + fm.getUnitName() + " started");
            } catch (Exception e) {
                System.exit(-1);
            }

        } else {
            System.out.println("Usage: java -jar FarmMonitor.jar FarmName config_file_1 [config_file_n]");
        }

    }
}
