/*
 * $Id: MLLUSHelper.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.JiniSerFarmMon;

import java.io.IOException;
import java.io.StringReader;
import java.rmi.RMISecurityManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ClientsFarmProxy.ProxyServiceEntry;
import lia.Monitor.ClientsFarmProxy.ProxyServiceI;
import lia.Monitor.monitor.ABPingEntry;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.AuthZSI;
import lia.Monitor.monitor.DataStore;
import lia.Monitor.monitor.MLJiniManagersProvider;
import lia.Monitor.monitor.MLLoggerSI;
import lia.Monitor.monitor.PathloadDiscoverySI;
import lia.Monitor.monitor.ReflRouterSI;
import lia.Monitor.monitor.TopologySI;
import lia.util.Utils;
import lia.util.ntp.NTPDate;
import lia.util.threads.MonALISAExecutors;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationFile;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lookup.ServiceDiscoveryManager;

/**
 * Helper class to get ServiceItemS from LUSs. This class uses the {@link ServiceDiscoveryManager} from
 * {@link MLJiniManagersProvider}
 * 
 * @author ramiro
 */
public class MLLUSHelper implements Runnable {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger("lia.Monitor.JiniSerFarmMon.MLLUSHelper");

    private static final ServiceTemplate proxyTemplate = new ServiceTemplate(null, new Class[] { ProxyServiceI.class },
            null);

    private static final ServiceTemplate MLServiceTemplate = new ServiceTemplate(null, new Class[] { DataStore.class },
            null);

    private static final ServiceTemplate topologyTemplate = new ServiceTemplate(null, new Class[] { TopologySI.class },
            null);

    private static final ServiceTemplate authzTemplate = new ServiceTemplate(null, new Class[] { AuthZSI.class }, null);

    private static final ServiceTemplate pathloadTemplate = new ServiceTemplate(null,
            new Class[] { PathloadDiscoverySI.class }, null);

    private static final ServiceTemplate loggerTemplate = new ServiceTemplate(null, new Class[] { MLLoggerSI.class },
            null);

    private static final ServiceTemplate reflRouterTemplate = new ServiceTemplate(null,
            new Class[] { ReflRouterSI.class }, null);

    private final AtomicReference<ServiceItem[]> serviceSIReference = new AtomicReference<ServiceItem[]>();

    private final AtomicReference<ABPingEntry[]> ABPingEntriesReference = new AtomicReference<ABPingEntry[]>();

    private final AtomicReference<ServiceItem[]> proxySIReference = new AtomicReference<ServiceItem[]>();

    private final AtomicReference<ServiceItem[]> topologySIReference = new AtomicReference<ServiceItem[]>();

    private final AtomicReference<ServiceItem[]> authzSIReference = new AtomicReference<ServiceItem[]>();

    private final AtomicReference<ServiceItem[]> pathloadSIReference = new AtomicReference<ServiceItem[]>();

    private volatile AtomicReference<ServiceItem[]> loggerSIReference = new AtomicReference<ServiceItem[]>();

    private volatile AtomicReference<ServiceItem[]> reflRouterSIReference = new AtomicReference<ServiceItem[]>();

    // when the requested ServiceItems were last updated
    private static AtomicLong lastUpdateTime = new AtomicLong(0);

    // singleton
    private static final MLLUSHelper _thisInstance = new MLLUSHelper();

    private final AtomicBoolean shouldGetMLSer = new AtomicBoolean(false);

    private final AtomicBoolean shouldGetProxySer = new AtomicBoolean(false);

    private final AtomicBoolean shouldGetTopoSer = new AtomicBoolean(false);

    private final AtomicBoolean shouldGetAuthzSer = new AtomicBoolean(false);

    private final AtomicBoolean shouldGetPathloadSer = new AtomicBoolean(false);

    private final AtomicBoolean shouldGetLoggerSer = new AtomicBoolean(false);

    private final AtomicBoolean shouldGetReflRouterSer = new AtomicBoolean(false);

    /** Delay between checking; by default 2 minutes */
    private static final AtomicLong checkDelay = new AtomicLong(2 * 60);

    // internal sync stuff which keep the state of the publisher;
    /**
     * used to force the publishing. it should be true only when the publish was forced using <code>updateNow()</code>
     * methods
     */
    private static boolean shouldForceRecheck = true;

    /** is there a publishing task already running ?? */
    private static boolean isAlreadyRunning = false;

    /** sync guard for the state boolean variables <code>shouldPublish</code> and <code>publishing</code> */
    private static final Object stateLock = new Object();

    private final AtomicLong runCount = new AtomicLong(0);

    static {
        reloadConf();

        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                reloadConf();
            }
        });

        MonALISAExecutors.getMLHelperExecutor().schedule(_thisInstance, checkDelay.get(), TimeUnit.SECONDS);

    }

    private static void reloadConf() {
        long recheckDelay = 120;

        try {
            recheckDelay = AppConfig.getl("lia.Monitor.JiniSerFarmMon.MLLUSHelper.RECHECK_DELAY", 120);
        } catch (Throwable t) {
            recheckDelay = 120;
        }

        checkDelay.set(recheckDelay);

        final String sLevel = AppConfig.getProperty("lia.Monitor.JiniSerFarmMon.MLLUSHelper.level");
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
            logger.log(Level.FINER, "[ MLLUSHelper ] reloadedConf. Logging level: " + loggingLevel);
        }

    }

    public static final MLLUSHelper getInstance() {
        return _thisInstance;
    }

    private MLLUSHelper() {
        String proxyGroup = AppConfig.getProperty("lia.Monitor.ClientsFarmProxy.ProxyGroup", null);

        ProxyServiceEntry pse = new ProxyServiceEntry();
        pse.proxyGroup = proxyGroup;
    }

    private static final ServiceItem[] getServicesForTemplate(ServiceTemplate st) {
        ServiceItem[] si = null;
        try {
            final ServiceDiscoveryManager SDM = MLJiniManagersProvider.getServiceDiscoveryManager();
            if (SDM != null) {
                si = SDM.lookup(st, 1000, null);
                lastUpdateTime.set(NTPDate.currentTimeMillis());
                synchronized (stateLock) {
                    shouldForceRecheck = false;
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error doing jini lookup for " + st.getClass(), t);
        }
        return si;
    }

    private void updateServicesList() {

        final ArrayList<ABPingEntry> ABPingEntriesList = new ArrayList<ABPingEntry>();
        ServiceItem[] si = null;

        try {
            si = getServicesForTemplate(MLServiceTemplate);

            if ((si == null) || (si.length == 0)) {
                return;
            }

            final int len = si.length;
            for (int i = 0; i < len; i++) {
                ABPingEntry abpe = Utils.getEntry(si[i], ABPingEntry.class);
                if (abpe != null) {
                    ABPingEntriesList.add(abpe);
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "\n\n Host = " + abpe.HostName + "/" + abpe.FullHostName + " PORT = "
                                + abpe.PORT);
                    }
                }
            }// for

        } finally {
            serviceSIReference.set(si);
            this.ABPingEntriesReference.set(ABPingEntriesList.toArray(new ABPingEntry[ABPingEntriesList.size()]));
        }
    }

    public ServiceItem[] getProxies() {
        shouldGetProxySer.set(true);
        return proxySIReference.get();
    } // getProxies

    public ServiceItem[] getTopologyServices() {
        shouldGetTopoSer.set(true);
        return topologySIReference.get();
    } // getProxies

    public ServiceItem[] getServices() {
        shouldGetMLSer.set(true);
        return this.serviceSIReference.get();
    } // getServices

    public ServiceItem[] getAuthzServices() {
        shouldGetAuthzSer.set(true);
        return authzSIReference.get();
    } // getProxies

    public ServiceItem[] getLoggerServices() {
        shouldGetLoggerSer.set(true);
        return loggerSIReference.get();
    }

    public ServiceItem[] getReflRouterServices() {
        shouldGetReflRouterSer.set(true);
        return reflRouterSIReference.get();
    }

    /**
     * Get all locally available ServiceItems. If no items are available, enable the JiniDiscoveryManager to make this
     * kinds of requests.
     * 
     * @return The ServiceItems related to Pathload
     */
    public ServiceItem[] getPathloadServices() {
        shouldGetPathloadSer.set(true);
        return pathloadSIReference.get();
    }

    /**
     * just notify the thread that it should update the services, proxies etc. lists regardless of the SLEEP_TIME
     * between 2 normal updates
     */
    public void forceUpdate() {
        checkAndStartTaskNow();
    }

    private void checkAndStartTaskNow() {
        synchronized (stateLock) {
            if (isAlreadyRunning) {
                shouldForceRecheck = true;
            } else {
                // spawn a new publish task right now!
                MonALISAExecutors.getMLHelperExecutor().submit(new Runnable() {

                    @Override
                    public void run() {
                        recheckAndResched(false);
                    }
                });
            }
        }
    }

    /**
     * Returns the time of the last update of the requested ServiceItems.
     */
    public long getLastUpdateTime() {
        return lastUpdateTime.get();
    }

    private void getUpdatesFromLUSs() {
        runCount.incrementAndGet();
        StringBuilder sb = null;
        final boolean isFinest = logger.isLoggable(Level.FINEST);
        final boolean isFiner = isFinest || logger.isLoggable(Level.FINER);
        final boolean isFine = isFiner || logger.isLoggable(Level.FINE);

        if (isFine) {
            sb = new StringBuilder(8192);
        }

        // to be sure that the Thread.sleep executes in case of exceptions
        if (isFiner) {
            sb.append(" shouldGetProxySer [ ").append(shouldGetProxySer).append(" ] ");
            sb.append(" shouldGetMLSer [ ").append(shouldGetMLSer).append(" ] ");
            sb.append(" shouldGetTopoSer [ ").append(shouldGetTopoSer).append(" ] ");
            sb.append(" shouldGetAuthzSer [ ").append(shouldGetAuthzSer).append(" ] ");
            sb.append(" shouldGetPathloadSer [ ").append(shouldGetPathloadSer).append(" ] ");
            sb.append(" shouldGetLoggerSer [ ").append(shouldGetLoggerSer).append(" ] ");
            sb.append(" shouldGetReflRouterSer [ ").append(shouldGetReflRouterSer).append(" ] ");
        }

        final long sTime = Utils.nanoNow();

        try {
            if (shouldGetProxySer.get()) {
                proxySIReference.set(getServicesForTemplate(proxyTemplate));
                if (isFiner) {
                    final ServiceItem[] proxySI = proxySIReference.get();
                    final String count = (proxySI == null) ? "null" : "" + proxySI.length;
                    sb.append("\n ProxyServices [ ").append(count).append(" ]");
                }
            }

            if (shouldGetMLSer.get()) {
                updateServicesList();
                if (isFiner) {
                    final ServiceItem[] serviceSI = serviceSIReference.get();
                    final String count = (serviceSI == null) ? "null" : "" + serviceSI.length;
                    sb.append("\n MLServices [ ").append(count).append(" ]");
                }
            }

            if (shouldGetTopoSer.get()) {
                topologySIReference.set(getServicesForTemplate(topologyTemplate));
                if (isFiner) {
                    final ServiceItem[] topologySI = topologySIReference.get();
                    final String count = (topologySI == null) ? "null" : "" + topologySI.length;
                    sb.append("\n TopoServices [ ").append(count).append(" ]");
                }
            }

            if (shouldGetAuthzSer.get()) {
                authzSIReference.set(getServicesForTemplate(authzTemplate));
                if (isFiner) {
                    final ServiceItem[] authzSI = authzSIReference.get();
                    final String count = (authzSI == null) ? "null" : "" + authzSI.length;
                    sb.append("\n AuthzServices [ ").append(count).append(" ]");
                }
            }
            if (shouldGetPathloadSer.get()) {
                pathloadSIReference.set(getServicesForTemplate(pathloadTemplate));
                if (isFiner) {
                    final ServiceItem[] pathloadSI = pathloadSIReference.get();
                    final String count = (pathloadSI == null) ? "null" : "" + pathloadSI.length;
                    sb.append("\n PathloadServices [ ").append(count).append(" ]");
                }
            }

            if (shouldGetLoggerSer.get()) {
                loggerSIReference.set(getServicesForTemplate(loggerTemplate));
                if (isFiner) {
                    final ServiceItem[] loggerSI = loggerSIReference.get();
                    final String count = (loggerSI == null) ? "null" : "" + loggerSI.length;
                    sb.append("\n LoggerServices [ ").append(count).append(" ]");
                }
            }

            if (shouldGetReflRouterSer.get()) {
                reflRouterSIReference.set(getServicesForTemplate(reflRouterTemplate));
                if (isFiner) {
                    final ServiceItem[] reflRouterSI = reflRouterSIReference.get();
                    final String count = (reflRouterSI == null) ? "null" : "" + reflRouterSI.length;
                    sb.append("\n ReflRouterServices [ ").append(count).append(" ]");
                }
            }
        } finally {
            if (isFine) {
                sb.append("\n [ MLLUSHelperTask ] Quering the LUSs took [ ")
                        .append(TimeUnit.NANOSECONDS.toMillis(Utils.nanoNow() - sTime)).append(" ] ms \n");
                logger.log(Level.FINE, sb.toString());
            }
        }
    }

    private void recheckAndResched(final boolean shouldResched) {

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "[ MLLUSHelperTask ] [ recheckAndResched ] started. shouldResched = "
                    + shouldResched);
        }

        synchronized (stateLock) {
            if (!isAlreadyRunning) {
                shouldForceRecheck = false;
                isAlreadyRunning = true;
            } else {
                // reschedule a normal publish
                if (shouldResched) {
                    MonALISAExecutors.getMLHelperExecutor().schedule(_thisInstance, checkDelay.get(), TimeUnit.SECONDS);
                    return;
                }
            }
        }// end sync

        try {
            for (;;) {
                getUpdatesFromLUSs();

                synchronized (stateLock) {
                    if (shouldForceRecheck) {
                        shouldForceRecheck = false;
                        isAlreadyRunning = true;
                        continue;
                    }

                    isAlreadyRunning = false;
                }

                break;
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ MLLUSHelperTask ] Exception main loop ", t);
        } finally {

            synchronized (stateLock) {
                isAlreadyRunning = false;
            }

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "[ MLLUSHelperTask ] ... exits main loop. isAlreadyRunning="
                        + isAlreadyRunning + ", shouldForceRecheck=" + shouldForceRecheck + ", shouldResched="
                        + shouldResched);
            }

            if (shouldResched) {
                MonALISAExecutors.getMLHelperExecutor().schedule(_thisInstance, checkDelay.get(), TimeUnit.SECONDS);
            }
        }
    }

    public ABPingEntry[] getABPingEntries() {
        shouldGetMLSer.set(true);
        return this.ABPingEntriesReference.get();
    }

    public ServiceItem[] getServiceItemBySID(ServiceID sid) {
        ServiceItem[] si = null;
        try {
            ServiceDiscoveryManager SDM = MLJiniManagersProvider.getServiceDiscoveryManager();
            if (SDM != null) {
                try {
                    si = SDM.lookup(new ServiceTemplate(sid, null, null), 1000, null);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "[ MLLUSHelper ] Got exc querying the LUSs", t);
                }
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "Returning ServiceItem by SID [ " + sid + " ] SI = " + Arrays.toString(si));
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exc querying the LUSs", t);
        }

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST,
                    " [ MLLusHelper ] getServiceItemBySID(" + sid + ") returning " + Arrays.toString(si));
        }
        return si;
    }

    // only used from main() - for testing
    private static Configuration getBasicExportConfig() {
        StringBuilder config = new StringBuilder();
        String[] options = new String[] { "-" };

        config.append("import java.net.NetworkInterface;\n");
        config.append("net.jini.discovery.LookupDiscovery {\n");
        config.append("multicastInterfaces = new NetworkInterface[]{};\n");
        config.append("}//net.jini.discovery.LookupDiscovery\n");

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Config content:\n\n" + config.toString());
        }

        StringReader reader = new StringReader(config.toString());

        try {
            return new ConfigurationFile(reader, options);
        } catch (ConfigurationException ce) {
            logger.log(Level.SEVERE, "Cannot get config object", ce);
        }
        return null;
    }

    private static int getAttrCount(ServiceItem[] sis) {
        if ((sis == null) || (sis.length == 0)) {
            return 0;
        }

        int attrCount = 0;
        for (ServiceItem si : sis) {
            attrCount += si.attributeSets.length;
        }
        return attrCount;
    }

    /**
     * 
     * @return - a Map with key (String) the name of the parameter; Value an Integer representing the number of Services
     */
    public Map<String, Double> getMonitoringParams() {
        Map<String, Double> m = new HashMap<String, Double>();

        m.put("MLH_RCount", Double.valueOf(runCount.get()));

        if (shouldGetProxySer.get()) {
            ServiceItem[] sis = proxySIReference.get();
            m.put("MLH_ProxySerCount", Double.valueOf((sis == null) ? 0 : sis.length));
        }

        if (shouldGetMLSer.get()) {
            ServiceItem[] sis = serviceSIReference.get();
            m.put("MLH_MLSerCount", Double.valueOf((sis == null) ? 0 : sis.length));
        }

        if (shouldGetTopoSer.get()) {
            ServiceItem[] sis = topologySIReference.get();
            m.put("MLH_TopoSerCount", Double.valueOf((sis == null) ? 0 : sis.length));
        }

        if (shouldGetAuthzSer.get()) {
            ServiceItem[] sis = authzSIReference.get();
            m.put("MLH_AuthzSerCount", Double.valueOf((sis == null) ? 0 : sis.length));
        }

        if (shouldGetPathloadSer.get()) {
            ServiceItem[] sis = pathloadSIReference.get();
            m.put("MLH_PathLoadSerCount", Double.valueOf((sis == null) ? 0 : sis.length));
        }

        if (shouldGetLoggerSer.get()) {
            ServiceItem[] sis = loggerSIReference.get();
            m.put("MLH_LoggerSerCount", Double.valueOf((sis == null) ? 0 : sis.length));
        }

        if (shouldGetReflRouterSer.get()) {
            ServiceItem[] sis = reflRouterSIReference.get();
            m.put("MLH_ReflRouterSerCount", Double.valueOf((sis == null) ? 0 : sis.length));
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [ MLLUSHelper ] getMonitoringParam returns: " + m);
        }

        return m;
    }

    public static void main(String[] args) {
        // get SecurityManager
        if (System.getSecurityManager() == null) {
            System.setSecurityManager(new RMISecurityManager());
        }

        try {

            // get specified LookupLocators[]
            LookupLocator[] lookupLocators = Utils.getLUDSs("monalisa.cern.ch");
            Configuration cfgLUSs = null;
            try {
                cfgLUSs = getBasicExportConfig();
            } catch (Throwable t1) {
                t1.printStackTrace();
                cfgLUSs = null;
                System.exit(-1);
            }
            LookupDiscoveryManager lookupDiscoveryManager = null;
            try {
                lookupDiscoveryManager = new LookupDiscoveryManager(null, lookupLocators, null, cfgLUSs);
            } catch (Throwable e) {
                e.printStackTrace();
                System.exit(-2);
            }

            LeaseRenewalManager lrm = new LeaseRenewalManager();

            ServiceDiscoveryManager sdm = new ServiceDiscoveryManager(lookupDiscoveryManager, lrm);

            MLJiniManagersProvider.setManagers(null, sdm, null);

        } catch (IOException e) {
            logger.log(Level.WARNING, " IOException: ", e);
        }

        MLLUSHelper mllsh = MLLUSHelper.getInstance();

        while (true) {
            try {
                ServiceItem[] sis = mllsh.getServices();
                if (sis != null) {
                    int serviceCount = sis.length;
                    int attrCount = getAttrCount(sis);
                    System.out.println(" There are " + serviceCount + " MLServices and " + attrCount + " Attibutes ");
                }
                sis = mllsh.getProxies();
                if (sis != null) {
                    int serviceCount = sis.length;
                    int attrCount = getAttrCount(sis);
                    System.out.println(" There are " + serviceCount + " MLProxies and " + attrCount + " Attibutes ");
                }

                sis = mllsh.getAuthzServices();
                if (sis != null) {
                    int serviceCount = sis.length;
                    int attrCount = getAttrCount(sis);
                    System.out
                            .println(" There are " + serviceCount + " AuthzServices and " + attrCount + " Attibutes ");
                }

                sis = mllsh.getTopologyServices();
                if (sis != null) {
                    int serviceCount = sis.length;
                    int attrCount = getAttrCount(sis);
                    System.out.println(" There are " + serviceCount + " TopologyServices and " + attrCount
                            + " Attibutes ");
                }

            } catch (Throwable t) {
                logger.log(Level.WARNING, " Got ex", t);
            }
            ServiceItem[] sis = mllsh.getServices();
            if (sis != null) {
                int serviceCount = sis.length;
                int attrCount = getAttrCount(sis);
                System.out.println(" There are " + serviceCount + " MLServices and " + attrCount + " Attibutes ");
            }

            System.out.println("\n Monitoring param: \n" + getInstance().getMonitoringParams());

            try {
                Thread.sleep(20000);
            } catch (Throwable t) {
                //comentezi?
            }
        }
    }

    @Override
    public void run() {
        recheckAndResched(true);
    }
}
