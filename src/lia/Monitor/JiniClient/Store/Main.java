package lia.Monitor.JiniClient.Store;

import java.net.InetAddress;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ClientsFarmProxy.ProxyServiceEntry;
import lia.Monitor.JiniClient.CommonJini.JiniClient;
import lia.Monitor.Store.DataSplitter;
import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.TransparentStoreFast;
import lia.Monitor.Store.TransparentStoreInt;
import lia.Monitor.Store.Fast.DB;
import lia.Monitor.Store.Fast.IDGenerator;
import lia.Monitor.Store.Fast.Writer;
import lia.Monitor.monitor.AccountingResult;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppControlClient;
import lia.Monitor.monitor.DataReceiver;
import lia.Monitor.monitor.DataStore;
import lia.Monitor.monitor.ExtResult;
import lia.Monitor.monitor.ExtendedSiteInfoEntry;
import lia.Monitor.monitor.Gresult;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.MonMessageClientsProxy;
import lia.Monitor.monitor.MonaLisaEntry;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.ResultUtils;
import lia.Monitor.monitor.ShutdownReceiver;
import lia.Monitor.monitor.SiteInfoEntry;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.MLSerClient;
import lia.util.StringFactory;
import lia.util.actions.ActionsManager;
import lia.util.ntp.NTPDate;
import lia.web.utils.Formatare;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;

/**
 * Main class for the Jini Store Client
 */
public class Main extends JiniClient implements ShutdownReceiver, LocalDataFarmClient, AppControlClient {

    /** Logger used by this class */
    static final Logger logger = Logger.getLogger(Main.class.getName());

    /**
     * store instance
     */
    TransparentStoreInt store;

    /** rcNodeS Related */
    final Object nodesSync = new Object();

    /**
     * 
     */
    Hashtable<String, rcStoreNode> nodes;

    /**
     * 
     */
    Hashtable<ServiceID, rcStoreNode> snodes;

    /**
     * 
     */
    Hashtable<ServiceID, ServiceThread> sthreads;

    /**
     * Periodic saving
     */
    static final Timer saveTimer = new Timer(true);

    /** buffers of lia.Monitor.monitor.Result for storing online data */
    Vector<Object> buff = null;

    private final Vector<DataReceiver> vReceivers = new Vector<DataReceiver>();

    /** interest in...(for Gresult) */
    public HashSet<String> local_filter_global_param;

    /**
     * gResult filter
     */
    public HashSet<String> local_filter_clusters;

    /** interest in...(for Result ) */
    public Vector<monPredicate> local_filter_pred;

    /** */
    final AtomicReference<tmProxyStore> connectionToProxyPointer = new AtomicReference<tmProxyStore>();

    /**
     * Data producers local to the repository
     */
    Vector<DataProducer> vDirectInserters;

    // we assume that all the services have NTP enabled, so by default we trust the time they set
    private static final boolean bUpdateResultTime = AppConfig.getb("lia.Monitor.JStore.storeLocalTime", false);

    private static final boolean bCorrectTimestamps = AppConfig.getb("lia.Monitor.JStore.correctTimestamps", true);

    private static long iSavedValues = 0;

    /** Servlet container to use. Currently possible values: tomcat, bajie. Default: tomcat */
    private static String sServletContainer = AppConfig.getProperty("lia.Monitor.JStore.ServletContainer", "tomcat");

    /**
     * Statistics function
     * 
     * @return the number of received values since the last call of this function
     */
    public static long getAndFlushSavedValues() {
        long l = iSavedValues;
        iSavedValues = 0;
        return l;
    }

    private static Main mainInstance = null;

    /**
     * Singleton, lazy initialization
     * 
     * @return the single instance of this class
     */
    public static synchronized Main getInstance() {
        if ((mainInstance == null) && !DataSplitter.bFreeze) {
            mainInstance = new Main();
        }

        return mainInstance;
    }

    /**
     * whether or not to put the VO names in upper case (migration issue)
     */
    boolean bCorrectVONames = false;

    /**
     * For totals, the community name, defaults to "osg"
     */
    static String community = AppConfig.getProperty("lia.Monitor.JiniClient.Store.community", "osg");

    /**
     * 
     */
    static String gatekeeper_suffix = AppConfig.getProperty("lia.Monitor.JiniClient.Store.gatekeeper_suffix", "");

    @Override
    public synchronized void closeProxyConnection() {
        final tmProxyStore connectionToProxy = connectionToProxyPointer.getAndSet(null);

        if (connectionToProxy != null) {
            connectionToProxy.closeProxyConnection();
        } // if
    } // closeProxyConnection

    /**
     * Active services
     */
    Hashtable<String, Set<String>> htFarms;

    private final Vector<String> vFilters;

    private final Vector<Filter> vDynamicFilters = new Vector<Filter>();

    /**
     * Default constructor
     */
    public Main() {
        super(null, true, true);

        htFarms = new Hashtable<String, Set<String>>();

        try {
            lia.web.servlets.web.ABPing.initDBStructure();
        } catch (Throwable t) {
            // ignore
        }

        try {
            // use TMW3 for repositories by default
            if (AppConfig.geti("lia.Monitor.Store.TransparentStoreFast.mem_writer_version", -1) == -1) {
                System.setProperty("lia.Monitor.Store.TransparentStoreFast.mem_writer_version", "3");
            }
        } catch (Throwable t) {
            // ignore
        }

        store = TransparentStoreFactory.getStore();

        try {
            ((Writer) ((TransparentStoreFast) store).getTempMemWriter()).setIgnoreDataFlags(Writer.FLAGS_EXTRESULT
                    | Writer.FLAGS_ACCOUNTINGRESULT);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Cannot set ignore flags on the memory writer", t);
        }

        vDirectInserters = new Vector<DataProducer>();

        bCorrectVONames = AppConfig.getb("lia.Monitor.JStore.correctVONames", false);

        if (bCorrectVONames) {
            IDGenerator.correctVONames();
        } else {
            IDGenerator.getUpdateCount();
        }

        try {
            int iTomcatPort = AppConfig.geti("lia.Repository.tomcat_port", 0);

            if (iTomcatPort > 0) {
                logger.log(Level.INFO, "JStoreClient: starting exporter on port : " + iTomcatPort);

                synchronized (lia.web.utils.ThreadedPage.oLock) {
                    if (lia.web.utils.ThreadedPage.getExporter() == null) {
                        lia.web.utils.ThreadedPage.setExporter(new lia.web.utils.ExportStatistics(iTomcatPort));
                    }
                }
            } else {
                logger.log(Level.INFO, "JStoreClient: no port defined for the exporter");
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "JStoreClient: cannot start exporter because: " + t);
        }

        try {
            int iDirectInserters = AppConfig.geti("lia.Monitor.JStore.inserters", 0);

            for (int i = 0; i < iDirectInserters; i++) {
                String sProgram = AppConfig.getProperty("lia.Monitor.JStore.execute_" + i + ".path");
                long lSeconds = AppConfig.getl("lia.Monitor.JStore.execute_" + i + ".time", 60);
                boolean bPos = AppConfig.getb("lia.Monitor.JStore.execute_" + i + ".only_positives", false);

                if ((sProgram != null) && (sProgram.trim().length() > 0) && (lSeconds > 0)) {
                    DirectInsert di = new DirectInsert(sProgram, lSeconds * 1000, bPos);

                    vDirectInserters.add(di);
                }
            }
        } catch (Exception e) {
            System.err.println("Main : exception builing inserters : " + e);
            e.printStackTrace();
        }

        addDataReceiver(new CacheUpdater());

        buff = new Vector<Object>();

        // default SQL_STORE

        nodes = new Hashtable<String, rcStoreNode>();
        snodes = new Hashtable<ServiceID, rcStoreNode>();

        local_filter_global_param = new HashSet<String>();
        local_filter_clusters = new HashSet<String>();

        local_filter_pred = new Vector<monPredicate>();

        String[] vsFilters = AppConfig.getVectorProperty("lia.Monitor.JiniClient.Store.filters", "MFilter2");

        vFilters = new Vector<String>();

        for (int i = 0; (vsFilters != null) && (i < vsFilters.length); i++) {
            vFilters.add(vsFilters[i]);
        }
        String[] vsGlobalClusters = AppConfig.getVectorProperty("lia.Monitor.JiniClient.Store.global_clusters");
        if (vsGlobalClusters != null) {
            for (String vsGlobalCluster : vsGlobalClusters) {
                String ns = vsGlobalCluster.replaceAll("(%)+", "(\\\\w)*");
                local_filter_clusters.add(ns);
                logger.log(Level.INFO, "global clusters : " + vsGlobalCluster + " Re = " + ns);
            }
        }

        String[] vsGlobalParams = AppConfig.getVectorProperty("lia.Monitor.JiniClient.Store.global_params");
        if (vsGlobalParams != null) {
            for (String vsGlobalParam : vsGlobalParams) {
                local_filter_global_param.add(vsGlobalParam);
                logger.log(Level.INFO, "global params : " + vsGlobalParam);
            }
        }

        String[] vsPreds = AppConfig.getVectorProperty("lia.Monitor.JiniClient.Store.predicates");
        if (vsPreds != null) {
            for (String vsPred : vsPreds) {
                if ((vsPred != null) && (vsPred.trim().length() > 0)) {
                    monPredicate pred = lia.web.utils.Formatare.toPred(vsPred);

                    if (pred != null) {
                        local_filter_pred.add(pred);

                        logger.log(Level.INFO, "pred : " + pred);
                    }
                }
            }
        }

        final String[] vsDynFilters = AppConfig.getVectorProperty("lia.Monitor.JiniClient.Store.dynamic_filters");
        if ((vsDynFilters != null) && (vsDynFilters.length > 0)) {
            System.err.println("Enabling dynamic filters ... (" + vsDynFilters.length + ") : ");

            for (String vsDynFilter : vsDynFilters) {
                if (vsDynFilter.equals("OSGFilter")) {
                    System.err.println("  OSGFilter");
                    addFilter(new OSGFilter(), vsDynFilter);
                    addFilter(new OSGFilterRates(), vsDynFilter);
                    continue;
                }

                if (vsDynFilter.equals("VOStorageFilter")) {
                    System.err.println("  VOStorageFilter");
                    addFilter(new VOStorageFilter(), vsDynFilter);
                    continue;
                }

                if (vsDynFilter.equals("AliEnFilter")) {
                    System.err.println("  AliEnFilter");
                    addFilter(new AliEnFilter(), vsDynFilter);
                    continue;
                }

                try {
                    final Filter f = (Filter) Class.forName(vsDynFilter).newInstance();
                    addFilter(f, vsDynFilter);
                    System.err.println("  (extern)" + vsDynFilter);
                } catch (Throwable t) {
                    System.err.println("  Cannot instantiate '" + vsDynFilter + "' because: " + t + " ("
                            + t.getMessage() + ")");
                }
            }

            System.err.println("ok");
        } else {
            System.err.println("Dynamic filters disabled");
        }

        final String[] vsDataProducers = AppConfig.getVectorProperty("lia.Monitor.JiniClient.Store.data_producers");
        if ((vsDataProducers != null) && (vsDataProducers.length > 0)) {
            System.err.println("Enabling data producers filters (" + vsDataProducers.length + ") : ");

            for (String vsDataProducer : vsDataProducers) {
                try {
                    final DataProducer dp = (DataProducer) Class.forName(vsDataProducer).newInstance();
                    vDirectInserters.add(dp);
                    System.err.println("  " + vsDataProducer + " : ok");
                } catch (Throwable t) {
                    System.err.println("Cannot instantiate '" + vsDataProducer + "' because: " + t + " ("
                            + t.getMessage() + ")");
                }
            }

            System.err.println("done");
        } else {
            System.err.println("Local data producers disabled");
        }

        sthreads = new Hashtable<ServiceID, ServiceThread>();

        init();

        long lSaveTimerInterval = AppConfig.getl("lia.Monitor.JiniClient.Store.save_timer", 60) * 1000; // 1 minute

        saveTimer.scheduleAtFixedRate(new SaveInfo(), 0, lSaveTimerInterval);

        AppConfig.addNotifier(this);

        addDataReceiver(ActionsManager.getInstance());

        final String[] vsInstantiate = AppConfig.getVectorProperty("lia.Monitor.JiniClient.Store.load_on_startup");

        if (vsInstantiate != null) {
            System.err.println("Touching static classes (" + vsInstantiate.length + ") : ");

            for (final String sClass : vsInstantiate) {
                try {
                    Class.forName(sClass).newInstance();
                    System.err.println("    " + sClass + " : ok");
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Cannot instantiate '" + sClass + "' : ", t);
                }
            }
        }
    }

    private void addFilter(final Filter f, final String sNiceName) {
        vDynamicFilters.add(f);

        final String sFilterName = f.getClass().getName();

        final String[] vsAccept = AppConfig.getVectorProperty(sFilterName + ".outputfilter.accept",
                AppConfig.getProperty(sNiceName + ".outputfilter.accept", ""));
        final String[] vsReject = AppConfig.getVectorProperty(sFilterName + ".outputfilter.reject",
                AppConfig.getProperty(sNiceName + ".outputfilter.reject", ""));

        final List<monPredicate> lAcceptPreds = new ArrayList<monPredicate>();
        final List<monPredicate> lRejectPreds = new ArrayList<monPredicate>();

        for (int i = 0; (vsAccept != null) && (i < vsAccept.length); i++) {
            final monPredicate pred = lia.web.utils.Formatare.toPred(vsAccept[i]);

            if (pred != null) {
                lAcceptPreds.add(pred);
            }
        }

        for (int i = 0; (vsReject != null) && (i < vsReject.length); i++) {
            final monPredicate pred = lia.web.utils.Formatare.toPred(vsReject[i]);

            if (pred != null) {
                lRejectPreds.add(pred);
            }
        }

        if (lAcceptPreds.size() > 0) {
            hmFilterOutputAccept.put(sFilterName, lAcceptPreds);
            hmFilterOutputAccept.put(sNiceName, lAcceptPreds);
        }

        if (lRejectPreds.size() > 0) {
            hmFilterOutputAccept.put(sFilterName, lRejectPreds);
            hmFilterOutputAccept.put(sNiceName, lRejectPreds);
        }
    }

    /**
     * Last number of jobs in each VO
     */
    static final HashMap<String, HashMap<String, HashMap<String, Result>>> hmLastJob = new HashMap<String, HashMap<String, HashMap<String, Result>>>();

    /**
     * Last result for a zero value, to skip a single zero value
     */
    static final HashMap<String, Result> hmLastZero = new HashMap<String, Result>();

    private static final String[] getGroups(final String group) {
        String[] retV;
        if ((group == null) || (group.length() == 0)) {
            return new String[0];
        }

        final StringTokenizer st = new StringTokenizer(group.trim(), ",");
        int count = st.countTokens();
        if (count > 0) {
            retV = new String[count];
            int vi = 0;
            while (st.hasMoreTokens()) {
                String token = st.nextToken().trim();
                if ((token != null) && (token.length() > 0)) {
                    retV[vi++] = token;
                }
            }

            return retV;
        }

        return new String[0];
    }

    /**
     * @param dr
     */
    public void addDataReceiver(final DataReceiver dr) {
        vReceivers.add(dr);
    }

    /**
     * package protected, used by JtClient only
     * 
     * @param f
     */
    void updateConfig(final lia.Monitor.monitor.MFarm f) {
        for (int i = 0; i < vReceivers.size(); i++) {
            try {
                vReceivers.get(i).updateConfig(f);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    /**
     * @author costing
     * @since forever
     */
    class SaveInfo extends TimerTask {

        @Override
        public void run() {
            Vector<Object> vTemp = null;

            for (int i = 0; i < vDirectInserters.size(); i++) {
                final DataProducer dp = vDirectInserters.get(i);

                try {
                    final Vector<Object> v = dp.getResults();

                    if ((v != null) && (v.size() > 0)) {
                        if (vTemp == null) {
                            vTemp = v;
                        } else {
                            vTemp.addAll(v);
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Exception running a DataProducer", e);
                }
            }

            newData(vTemp, true);

            final ArrayList<Object> buff1;

            synchronized (buff) {
                buff1 = new ArrayList<Object>(buff);
                buff.clear();
            }

            // Calculate total number of CPUs per farm
            HashMap<String, HashMap<String, Double>> hm = new HashMap<String, HashMap<String, Double>>();

            HashMap<String, HashMap<String, Double>> hmJOBS = new HashMap<String, HashMap<String, Double>>();
            HashMap<String, String> hmJOBSseen = new HashMap<String, String>();

            final long lNow = NTPDate.currentTimeMillis();

            for (int i = 0; i < buff1.size(); i++) {
                final Object o = buff1.get(i);

                if (o instanceof Result) {
                    final Result r = (Result) o;

                    if (r.ClusterName.matches("^PN.*")) {
                        if (r.param_name != null) {
                            for (int j = 0; j < r.param_name.length; j++) {
                                if (r.param_name[j].equals("NoCPUs")) {
                                    HashMap<String, Double> hm2 = hm.get(r.FarmName);

                                    if (hm2 == null) {
                                        hm2 = new HashMap<String, Double>();
                                        hm.put(r.FarmName, hm2);
                                    }

                                    hm2.put(r.NodeName, Double.valueOf(r.param[j]));
                                }
                            }
                        }
                    }

                    if ((r.FarmName != null)
                            && !r.FarmName.equals("_TOTALS_")
                            && (r.ClusterName != null)
                            && (r.ClusterName.equals("VO_JOBS") || r.ClusterName.equals(community + "VO_JOBS"
                                    + gatekeeper_suffix)) && (r.NodeName != null) && !r.NodeName.equals("_TOTALS_")) {
                        for (int j = 0; j < r.param_name.length; j++) {
                            if (r.param_name[j].matches("^(Running|Idle|Held|Failed|Total) ?Jobs$")) {
                                Result r2 = new Result(r.FarmName, "VO_JOBS", r.NodeName, null, null);
                                r2.time = lNow;

                                String sParameter = r.param_name[j];

                                if (sParameter.indexOf(" ") < 0) {
                                    int idx = sParameter.indexOf("Jobs");

                                    sParameter = sParameter.substring(0, idx) + " " + sParameter.substring(idx);
                                }

                                r2.addSet(sParameter, r.param[j]);

                                synchronized (hmLastJob) {
                                    HashMap<String, HashMap<String, Result>> hm2 = hmLastJob.get(r.NodeName);

                                    if (hm2 == null) {
                                        hm2 = new HashMap<String, HashMap<String, Result>>();
                                        hmLastJob.put(r.NodeName, hm2);
                                    }

                                    HashMap<String, Result> hm3 = hm2.get(sParameter);

                                    if (hm3 == null) {
                                        hm3 = new HashMap<String, Result>();
                                        hm2.put(sParameter, hm3);
                                    }

                                    final Result rTemp = hm3.get(r.FarmName);

                                    final String sTempKey = r2.FarmName + "/" + r2.NodeName + "/" + sParameter;

                                    if ((rTemp == null) || (r2.param[0] > 0.1)) {
                                        hm3.put(r.FarmName, r2);

                                        if (hmLastZero.get(sTempKey) != null) {
                                            // System.err.println("Removing zero markup for '"+sTempKey+"'");
                                            hmLastZero.remove(sTempKey);
                                        }
                                    } else {
                                        Result rLastZero = hmLastZero.get(sTempKey);

                                        if ((rTemp.param[0] > 0.5)
                                                && (((r2.time - rTemp.time) < (1000 * 50)) || (rLastZero == null) || ((r2.time - rLastZero.time) < (1000 * 60)))) {
                                            System.err.println("Ignoring '" + sTempKey + "' because value="
                                                    + r2.param[0] + ", " + rTemp.param[0]);
                                            hmLastZero.put(sTempKey, r2);
                                        } else {
                                            // System.err.println("Validating '"+sTempKey+"' because value="+r2.param[0]+", "+rTemp.param[0]);
                                            hm3.put(r.FarmName, r2);
                                            hmLastZero.remove(sTempKey);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (r.ClusterName.startsWith("JOB")) {
                        HashMap<String, Double> hmt = hmJOBS.get(r.FarmName);

                        if (hmt == null) {
                            hmt = new HashMap<String, Double>();
                            hmJOBS.put(r.FarmName, hmt);
                        }

                        for (int j = 0; j < r.param_name.length; j++) {
                            String s = r.param_name[j].trim();

                            if (s.toLowerCase().startsWith("pending")) {
                                s = "TotalPending";
                            } else if (s.toLowerCase().startsWith("running")) {
                                s = "TotalRunning";
                            } else {
                                continue;
                            }

                            if (hmJOBSseen.get(r.FarmName + "/" + r.NodeName + "/" + r.param_name[j]) != null) {
                                continue;
                            }

                            hmJOBSseen.put(r.FarmName + "/" + r.NodeName + "/" + r.param_name[j], "");

                            Double d = hmt.get(s);
                            if (d == null) {
                                d = Double.valueOf(0D);
                            }
                            d = Double.valueOf(d.doubleValue() + r.param[j]);
                            hmt.put(s, d);
                        }
                    }

                }
            }

            Iterator<Map.Entry<String, HashMap<String, Double>>> it = hm.entrySet().iterator();

            while (it.hasNext()) {
                final Map.Entry<String, HashMap<String, Double>> me = it.next();

                final String sFarm = me.getKey();
                double cpus = 0;

                final HashMap<String, Double> hm2 = me.getValue();

                final Iterator<Double> it2 = hm2.values().iterator();

                while (it2.hasNext()) {
                    cpus += it2.next().doubleValue();
                }

                if (cpus > 0) {
                    final Result r = new Result(sFarm, "Totals", "Farm", null, null);
                    r.time = lNow;
                    r.addSet("NoCPUs", cpus);

                    newData(r, false);
                }
            }
            hm = null;

            synchronized (hmLastJob) {
                Iterator<Map.Entry<String, HashMap<String, HashMap<String, Result>>>> itlj = hmLastJob.entrySet()
                        .iterator();

                while (itlj.hasNext()) {
                    final Map.Entry<String, HashMap<String, HashMap<String, Result>>> me = itlj.next();

                    final String sExperiment = me.getKey();

                    final HashMap<String, HashMap<String, Result>> hm2 = me.getValue();

                    final Iterator<Map.Entry<String, HashMap<String, Result>>> it2 = hm2.entrySet().iterator();

                    while (it2.hasNext()) {
                        final Map.Entry<String, HashMap<String, Result>> me2 = it2.next();

                        final String sFunc = me2.getKey();

                        final HashMap<String, Result> hm3 = me2.getValue();

                        final Iterator<Map.Entry<String, Result>> it3 = hm3.entrySet().iterator();

                        double dVal = 0;

                        int iCountNew = 0;

                        while (it3.hasNext()) {
                            final Map.Entry<String, Result> me3 = it3.next();

                            final Result r = me3.getValue();

                            if (r.time > (lNow - (1000L * 60L * 20L))) {
                                dVal += r.param[0];

                                if (r.time > (lNow - (1000L * 60L * 14L))) {
                                    iCountNew++;
                                }
                            } else {
                                it3.remove();
                            }
                        }

                        if (iCountNew > 0) {
                            final Result r = new Result("Totals", "Totals", sExperiment, null, null);
                            r.time = lNow;

                            r.addSet("X" + sFunc, dVal);

                            newData(r, false);
                        } else { // if there's no recent data it's time to clear the hash
                            hm3.clear();
                        }
                    }
                }

                Iterator<Map.Entry<String, Result>> itlz = hmLastZero.entrySet().iterator();

                while (itlz.hasNext()) {
                    Map.Entry<String, Result> me = itlz.next();

                    Result r = me.getValue();

                    if (r.time < (lNow - (1000L * 60L * 20L))) {
                        it.remove();
                    }
                }
            }

            it = hmJOBS.entrySet().iterator();

            while (it.hasNext()) {
                final Map.Entry<String, HashMap<String, Double>> me = it.next();

                final String sFarm = me.getKey();

                final HashMap<String, Double> hmt = me.getValue();

                if ((hmt != null) && (hmt.size() > 0)) {
                    final Result r = new Result(sFarm, "JOBS", "Totals", null, null);
                    r.time = lNow;

                    final Iterator<Map.Entry<String, Double>> it3 = hmt.entrySet().iterator();

                    while (it3.hasNext()) {
                        final Map.Entry<String, Double> me3 = it3.next();

                        final String sFunc = me3.getKey();
                        r.addSet(sFunc, me3.getValue().doubleValue());
                    }

                    newData(r, false);
                }
            }

            hmJOBS = null;
            hmJOBSseen = null;
        } // run()

    } // class SaveInfo

    /**
     * @param o
     * @param bQueue
     */
    void newData(final Object o, final boolean bQueue) {
        if (o == null) {
            return;
        }

        StringFactory.convert(o);

        if (o instanceof Vector) {
            @SuppressWarnings("unchecked")
            final Vector<Object> v = (Vector<Object>) o;

            store.addAll(v);

            iSavedValues += v.size();
        } else {
            store.addData(o);

            iSavedValues++;
        }

        notifyReceivers(o);

        if (bQueue) {
            buff.add(o);
        }
    }

    @Override
    public boolean AddMonitorUnit(final ServiceItem si) {
        // serviceInformation( si );
        synchronized (nodesSync) {
            if (snodes.containsKey(si.serviceID)) {
                return false;
            }
        }

        if (sthreads.containsKey(si.serviceID)) {
            logger.log(Level.WARNING, " An active thread is running for SID=" + si.serviceID);
            return false;
        }

        MonaLisaEntry mle = getEntry(si, MonaLisaEntry.class);

        boolean cg = checkServiceGroup(mle);

        if (!cg) {
            return false;
        }

        ServiceThread at = new ServiceThread(si);
        sthreads.put(si.serviceID, at);
        at.start();
        return true;
    }

    /**
     * @param mle
     * @return ?
     */
    boolean checkServiceGroup(final MonaLisaEntry mle) {
        if (mle == null) {
            return false;
        }
        if ((mle.Group == null) || (mle.Group.equals(" ")) || (mle.Group.equals(""))) {
            return false;
        }
        String[] xgs = getGroups(mle.Group);
        if ((xgs == null) || (xgs.length == 0)) {
            return false;
        }
        for (String xg : xgs) {
            if (SGroups.containsKey(xg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Last value for each service, to avoid updates in the DB with the same value
     */
    final Hashtable<String, String> htLat = new Hashtable<String, String>();

    /**
     * Last value for each service, to avoid updates in the DB with the same value
     */
    final Hashtable<String, String> htLong = new Hashtable<String, String>();

    /**  */
    class ServiceThread extends Thread {

        /**  */
        ServiceItem si;

        /** */
        JtClient tcl;

        /**
         * @param _si
         */
        ServiceThread(ServiceItem _si) {
            super("(ML) ServiceThread for " + _si);
            this.si = _si;
        }

        @Override
        public void run() {
            try {
                DataStore ds = (DataStore) si.service;
                if (ds == null) {
                    logger.log(Level.WARNING, "Service could not be deserialized", new Object[] { si });
                } else {
                    final MonaLisaEntry mle = getEntry(si, MonaLisaEntry.class);
                    final SiteInfoEntry sie = getEntry(si, SiteInfoEntry.class);
                    String ipadList = null;
                    Set<String> ipad = null;
                    String un = null;
                    int mlPort = 9000;

                    if (sie != null) {
                    	if (sie.IPAddress!=null){
                    		ipadList = sie.IPAddress;
                    		
                    		final StringTokenizer st = new StringTokenizer(ipadList, ",; \r\n\t");
                    		ipad = new LinkedHashSet<String>();
                    		
                    		while (st.hasMoreTokens()){
                    			final String s = st.nextToken();
                    			
                    			if (s.length()>0)
                    				ipad.add(s);
                    		}
                    	}
                    	
                        un = sie.UnitName;
                        mlPort = (sie.ML_PORT == null) ? 9000 : sie.ML_PORT.intValue();
                    } else {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "SiteInfoEntry == null for " + si);
                        }
                    }

                    if (ipad != null && ipad.size()>0) {
                        try {
                            synchronized (saveTimer) { // some static object ...
                                final Set<String> sOldIP = htFarms.get(un);

                                boolean bOtherServiceWithSameIP = false;

                                synchronized (htFarms) {
                                    final Iterator<Map.Entry<String, Set<String>>> it = htFarms.entrySet().iterator();

                                    while (it.hasNext()) {
                                        final Map.Entry<String, Set<String>> me = it.next();
                                        
                                        if (!me.getKey().equals(un)){
	                                        for (final String addr: ipad){
		                                        if (me.getValue().contains(addr)) {
		                                            bOtherServiceWithSameIP = true;
		                                            break;
		                                        }
	                                        }
                                        }
                                    }
                                }

                                // if the ip has changed or another farm name had this farm name
                                if ((sOldIP == null) || !sOldIP.containsAll(ipad) || bOtherServiceWithSameIP) {
                               		htFarms.put(un, ipad);
                               		
                               		if (logger.isLoggable(Level.FINE)){
                               			logger.log(Level.FINE, "htFarms is updated for '"+un+"' and now it is:\n"+htFarms);
                               		}

                                    if (!TransparentStoreFactory.isMemoryStoreOnly()) {
                                        DB db = new DB();
                                        
                                        db.setReadOnly(true);
                                        
                                        String sFirstIP = ipad.iterator().next();

                                        if (db.query("SELECT mfarmsource FROM abping WHERE mfarmsource='" + Formatare.mySQLEscape(sFirstIP) + "';") && !db.moveNext()) {
                                            DB db2 = new DB();

                                            db.query("SELECT distinct mfarmsource FROM abping;");

                                            if (db.moveNext()) {
                                                do {
                                                    db2.syncUpdateQuery("INSERT INTO abping VALUES ('" + Formatare.mySQLEscape(sFirstIP) + "', '" + Formatare.mySQLEscape(db.gets(1)) + "', 0);",true);
                                                    db2.syncUpdateQuery("INSERT INTO abping VALUES ('" + Formatare.mySQLEscape(db.gets(1)) + "', '" + Formatare.mySQLEscape(sFirstIP) + "', 0);", true);
                                                } while (db.moveNext());

                                                db2.syncUpdateQuery("DELETE FROM abping WHERE mfarmsource=mfarmdest;");
                                            } else {
                                                // there's no farm yet, put this value twice because it will be deleted at a later time
                                                // System.err.println("no farm, inserting: "+ipad);
                                                db2.syncUpdateQuery("INSERT INTO abping VALUES ('"+ Formatare.mySQLEscape(sFirstIP) + "', '" + Formatare.mySQLEscape(sFirstIP) + "', 0);");
                                            }
                                        }

                                        if (AppConfig.getb("lia.Monitor.JiniClient.Store.delete_same_ip", true)) {
                                            db.query("SELECT name, ip FROM abping_aliases WHERE name!='" + Formatare.mySQLEscape(un) + "' AND regexp_split_to_array(ip, ',|;|\\\\s') && regexp_split_to_array('"+Formatare.mySQLEscape(ipadList)+"', ',|;|\\\\s');");

                                            while (db.moveNext()) {
                                            	logger.log(Level.WARNING, "Removing service '"+db.gets(1)+"' because it conflicts on IP addresses with '"+un+"': "+db.gets(1)+" && "+ipadList);
                                                removeServiceCaches(db.gets(1));
                                            }

                                            db.syncUpdateQuery("DELETE FROM abping_aliases WHERE name!='" + Formatare.mySQLEscape(un) + "' AND regexp_split_to_array(ip, ',|;|\\\\s') && regexp_split_to_array('"+Formatare.mySQLEscape(ipadList)+"', ',|;|\\\\s');");
                                        }
                                        
                                        db.setCursorType(ResultSet.TYPE_SCROLL_INSENSITIVE);
                                        
                                        db.query("SELECT ip FROM abping_aliases WHERE name='"+Formatare.mySQLEscape(un)+"'");
                                        
                                        boolean update = false;
                                        
                                        if (db.count() > 1){
                                            db.syncUpdateQuery("DELETE FROM abping_aliases WHERE name='" + Formatare.mySQLEscape(un) + "';");
                                            
                                            removeServiceCaches(un);
                                        }
                                        else
                                        if (db.count() == 1){
                                        	final StringTokenizer st = new StringTokenizer(db.gets(1), ",; \r\n\t");
                                        	
                                        	final Set<String> existingIPs = new LinkedHashSet<String>();
                                        	
                                        	while (st.hasMoreTokens())
                                        		existingIPs.add(st.nextToken());
                                        	
                                        	if (existingIPs.containsAll(ipad) && existingIPs.size() > ipad.size())
                                        		ipad.addAll(existingIPs);
                                        	
                                        	update = true;
                                        }
                                        
                                        final StringBuilder sbIPs = new StringBuilder();
                                        
                                        for (final String s: ipad){
                                        	if (sbIPs.length()>0)
                                        		sbIPs.append(',');
                                        	
                                        	sbIPs.append(s);
                                        }
                                        
                                        ipadList = sbIPs.toString();

                                        if (update)
                                        	db.syncUpdateQuery("UPDATE abping_aliases SET ip='" + Formatare.mySQLEscape(ipadList) + "' WHERE name='" + Formatare.mySQLEscape(un) + "';");
                                        else
                                            db.syncUpdateQuery("INSERT INTO abping_aliases (ip, name) VALUES ('" + Formatare.mySQLEscape(ipadList) + "', '" + Formatare.mySQLEscape(un) + "');");
                                    }
                                }

                                if ((mle != null) && (mle.LAT != null) && (mle.LONG != null) && (mle.LAT.length() > 0) && (mle.LONG.length() > 0) && !TransparentStoreFactory.isMemoryStoreOnly()) {
                                    String latOld = htLat.get(un);
                                    String longOld = htLong.get(un);

                                    if ((latOld == null) || !latOld.equals(mle.LAT) || (longOld == null) || !longOld.equals(mle.LONG)) {
                                        htLat.put(un, mle.LAT);
                                        htLong.put(un, mle.LONG);
                                        (new DB()).syncUpdateQuery("UPDATE abping_aliases SET geo_lat='"
                                                + Formatare.mySQLEscape(mle.LAT) + "', geo_long='"
                                                + Formatare.mySQLEscape(mle.LONG) + "' WHERE name='"
                                                + Formatare.mySQLEscape(un) + "';");
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            System.err.println(t + "(" + t.getMessage() + ")");
                            t.printStackTrace();
                        }
                    }

                    if ((un != null) && (ipad != null)) {
                        InetAddress add = null;
                        try {
                            add = InetAddress.getByName(ipad.iterator().next());
                        } catch (Throwable t) {
                            // ignore
                        }

                        synchronized (nodesSync) {
                            if (!snodes.containsKey(si.serviceID)) {
                                if (nodes.containsKey(un)) {
                                    logger.log(Level.FINER, " REMOVE OLD SERVICE with this name" + un
                                            + " [ SHOULD NOT APPEAR ]");
                                    rcStoreNode ndel = nodes.get(un);
                                    removeNode(ndel.sid);
                                }
                            }
                        }

                        if ((mle != null) && (mlPort > 0)) {
                            tcl = new JtClient(si.serviceID, un, add, connectionToProxyPointer.get());
                            System.out.println("Main ==== > add a new JtClient for " + si.serviceID);
                            tcl.addFarmClient(si.serviceID);
                            if (logger.isLoggable(Level.FINE)) {
                                logger.log(Level.FINE, " Adding Data store at address = " + ipad + ":" + mlPort);
                            }

                            addNode(si, ds, tcl, un, ipadList);
                        } else {
                            if (mle == null) {
                                if (logger.isLoggable(Level.FINE)) {
                                    logger.log(Level.FINE, "MonaLisaEntry == null  for SI " + si);
                                }
                            } else {
                                if (logger.isLoggable(Level.FINE)) {
                                    logger.log(Level.FINE, "ML_PORT <= 0 " + mlPort);
                                }
                            } // else
                        } // else
                    } else {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, " un or ipad == null un: " + un + " ipad: " + ipad);
                        }
                    }

                    if (!TransparentStoreFactory.isMemoryStoreOnly() && (un != null)) {
                        final ExtendedSiteInfoEntry esie = getEntry(si, ExtendedSiteInfoEntry.class);

                        if (esie != null) {
                            final DB db = new DB();

                            // java.vm.version: 1.5.0_05-b05 java.version: 1.5.0_05
                            String sJavaVer = esie.JVM_VERSION;

                            if (sJavaVer.indexOf(":") >= 0) {
                                sJavaVer = sJavaVer.substring(sJavaVer.lastIndexOf(":") + 1).trim();
                            }

                            // > libc-2.3.2.so\nSHOULD_UPDATE ="true"
                            String sLibcVer = esie.LIBC_VERSION;

                            if (sLibcVer.indexOf("\n") > 0) {
                                sLibcVer = sLibcVer.substring(0, sLibcVer.indexOf("\n"));

                                if (sLibcVer.indexOf(" ") > 0) {
                                    sLibcVer = sLibcVer.substring(sLibcVer.lastIndexOf(" ") + 1).trim();
                                }
                            }

                            boolean bAutoUpdate = false;

                            if (esie.LIBC_VERSION.indexOf("\"true\"") > 0) {
                                bAutoUpdate = true;
                            }

                            db.syncUpdateQuery("UPDATE abping_aliases SET " + "java_ver='"
                                    + Formatare.mySQLEscape(sJavaVer) + "', " + "libc_ver='"
                                    + Formatare.mySQLEscape(sLibcVer) + "', " + "autoupdate=" + (bAutoUpdate ? 1 : 0)
                                    + ", " + "contact_email='" + Formatare.mySQLEscape(esie.localContactEMail) + "', "
                                    + "contact_name='" + Formatare.mySQLEscape(esie.localContactName) + "'"
                                    + " WHERE name='" + Formatare.mySQLEscape(un) + "';");
                        }
                    }
                }
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "GOT General Execption", t);
                }
            }
            synchronized (sthreads) {
                sthreads.remove(si.serviceID);
                sthreads.notify();
                this.tcl = null;
                this.si = null;
            }
        }
    }

    /**
     * When a service entry in the abping_aliases table is removed, clear all the caches for this entry
     * to force an update of the fields when the service is discovered again
     * 
     * @param sName
     *            service name
     */
    void removeServiceCaches(final String sName) {
        htLat.remove(sName);
        htLong.remove(sName);

        JtClient.htVersions.remove(sName);
    }

    /**
     * Wait for service threads to finish.
     * 
     * @see lia.Monitor.JiniClient.CommonJini.JiniClient#actualizeFarms(ArrayList)
     * @see lia.Monitor.JiniClient.CommonJini.JiniClient#AddProxyService(ServiceItem)
     */
    @Override
    public void waitServiceThreads(final String message) {
        synchronized (sthreads) {
            while (sthreads.size() > 0) {
                logger.log(Level.FINE, "Waiting for last [" + sthreads.size() + "] sericeThreads to finish " + message);
                try {
                    sthreads.wait(10000);
                } catch (InterruptedException e) {
                    logger.log(Level.WARNING, "Interrupted while waiting.", e);
                }
            }
        }
    }

    /**
     * RC Specific !
     * 
     * @param si
     * @param dataStore
     * @param client
     * @param unitName
     * @param ipad
     */
    public void addNode(final ServiceItem si, final DataStore dataStore, final JtClient client, final String unitName,
            final String ipad) {

        logger.log(Level.INFO, "-> Discovered service : " + unitName);
        rcStoreNode n = new rcStoreNode();
        n.client = client;
        n.errorCount = 0;

        n.UnitName = unitName;
        n.sid = si.serviceID; // sid ;
        n.mlentry = getEntry(si, MonaLisaEntry.class);
        n.ipad = ipad;

        synchronized (nodesSync) {
            snodes.put(n.sid, n);
            nodes.put(unitName, n);
        }

        final long lLastTime = getFarmLastTime(n.UnitName) - NTPDate.currentTimeMillis() - 60000;

        // what's the interest for this node!?!
        for (int i = 0; i < local_filter_pred.size(); i++) {
            // clone the original predicate
            monPredicate pre = TransparentStoreFactory.normalizePredicate(local_filter_pred.elementAt(i));

            if (!TransparentStoreFactory.isMemoryStoreOnly()) {
                pre.tmin = lLastTime;
                pre.tmax = -1;

                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "  predicate is " + pre + " because last time is " + lLastTime);
                }
            } else {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "  predicate is " + pre + ", untouched");
                }
            }

            client.addLocalClient(this, pre);
        }

        for (int i = 0; i < vFilters.size(); i++) {
            client.addLocalClient(this, vFilters.get(i));
        }

        logger.log(Level.FINE, "-> Added " + local_filter_pred.size() + " predicates and Filters " + vFilters + " for "
                + n.UnitName);

        notifyConnectionsMonitors(unitName, true);

        client.addAppControlClient(this);
    }

    private final Vector<ConnectionMonitor> vConnectionsMonitors = new Vector<ConnectionMonitor>();

    /**
     * @param cm
     */
    public void addConnectionMonitor(final ConnectionMonitor cm) {
        vConnectionsMonitors.add(cm);
    }

    /**
     * @param cm
     */
    public void removeConnectionMonitor(final ConnectionMonitor cm) {
        vConnectionsMonitors.remove(cm);
    }

    private void notifyConnectionsMonitors(final String sServiceName, final boolean bOnline) {
        for (int i = 0; i < vConnectionsMonitors.size(); i++) {
            final ConnectionMonitor cm = vConnectionsMonitors.get(i);

            cm.notifyServiceActivity(sServiceName, bOnline);
        }
    }

    /**
     * Get the last time when a service was last seen online, not more than 1 hour ago.
     * If there is no information in the database then it defaults to 15 minutes ago.
     * 
     * @param unitName
     *            the service name
     * @return the last time (epoch, in millis) when the service was last seen online, or 0 if there is no info
     *         available
     */
    public static final long getFarmLastTime(final String unitName) {
        if (TransparentStoreFactory.isMemoryStoreOnly()) {
            return NTPDate.currentTimeMillis();
        }

        final DB db = new DB();
        
        db.setReadOnly(true);

        if (db.query("SELECT max(mi_lastseen) FROM monitor_ids WHERE mi_key LIKE '" + unitName + "/%';")
                && db.moveNext()) {
            long lTime = db.getl(1) * 1000L;

            long lHistoryRequestMaxTime = AppConfig.getl("lia.Monitor.JiniClient.Store.historyRequestMaxTime",
                    60 * 60 * 1) * 1000;

            if ((NTPDate.currentTimeMillis() - lTime) > lHistoryRequestMaxTime) {
                lTime = NTPDate.currentTimeMillis() - lHistoryRequestMaxTime;
            }

            return lTime;
        }

        // this service was unknown until now, probably it just started and it doesn't have history data anyway
        return NTPDate.currentTimeMillis() - (1000 * 60 * 15);
    }

    private void notifyReceivers(final Object ro) {
        if ((vReceivers.size() <= 0) || (ro == null)) {
            return;
        }

        if (ro instanceof Collection) {
            final Iterator<?> it = ((Collection<?>) ro).iterator();

            while (it.hasNext()) {
                notifyReceivers(it.next());
            }
        }

        for (int j = 0; j < vReceivers.size(); j++) {
            final DataReceiver dr = vReceivers.get(j);

            try {
                if (ro instanceof Result) {
                    dr.addResult((Result) ro);
                } else if (ro instanceof eResult) {
                    dr.addResult((eResult) ro);
                } else if (ro instanceof AccountingResult) {
                    dr.addResult((AccountingResult) ro);
                } else if (ro instanceof ExtResult) {
                    dr.addResult((ExtResult) ro);
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Failed to notify receiver " + j, t);
            }
        }
    }

    @Override
    public void newFarmResult(MLSerClient mlSerClient, final Object ro) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Received result: " + ro);
        }

        if (ro == null) {
            return;
        }

        if (ro instanceof Gresult) {
            setGlobalVal((Gresult) ro);
            return;
        }

        if (ro instanceof Collection) {
            final Iterator<?> it = ((Collection<?>) ro).iterator();

            while (it.hasNext()) {
                newFarmResult(mlSerClient, it.next());
            }

            return;
        }

        if (!((ro instanceof Result) || (ro instanceof eResult))) {
            return;
        }

        for (int i = 0; i < vDynamicFilters.size(); i++) {
            Object o = null;

            final Filter f = vDynamicFilters.get(i);

            try {
                o = f.filterData(ro);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Exception filtering data with filter " + i, t);
            }

            if (o != null) {
                o = filterFilterOutput(f.getClass().getName(), o);
            }

            if (o != null) {
                // filter output is not interesting for periodic flushes
                newData(o, false);
            }
        }

        if (ro instanceof Result) {
            processResult((Result) ro);
        }

        if (ro instanceof eResult) {
            processResult((eResult) ro);
        }
    }

    private static final HashMap<String, List<monPredicate>> hmFilterOutputAccept = new HashMap<String, List<monPredicate>>();

    private static final HashMap<String, List<monPredicate>> hmFilterOutputReject = new HashMap<String, List<monPredicate>>();

    /**
     * @param name
     * @param o
     * @return filtered output
     */
    private static Object filterFilterOutput(final String name, final Object o) {
        if (o == null) {
            return o;
        }

        final List<monPredicate> lAccept = hmFilterOutputAccept.get(name);
        final List<monPredicate> lReject = hmFilterOutputReject.get(name);

        return ResultUtils.valuesFirewall(o, lAccept, lReject);
    }

    private static Result grToResult(final Gresult gr, final long now) {
        final Result rez = new Result();

        rez.time = now;

        rez.FarmName = gr.FarmName;
        rez.ClusterName = gr.ClusterName;

        // MFilter2
        if (gr.Module.equals("Load5")) {
            rez.addSet("Load_05", gr.hist[0] + gr.hist[1]);
            rez.addSet("Load_51", gr.hist[2] + gr.hist[3] + gr.hist[4]);
        } else if (gr.Module.equals("TotalIO_Rate_IN") || gr.Module.equals("TotalIO_Rate_OUT")) {
            rez.addSet(gr.Module, gr.sum);
        } else if (gr.Module.equals("NoCPUs")) {
            rez.addSet("NoCPUs", gr.sum);
        }

        // now from Lemon
        else if (gr.Module.equals("LoadAvg")) {
            for (int i = 0; i < gr.hist.length; i++) {
                rez.addSet("LoadAvg_" + i, gr.hist[i]);
            }
        } else if (gr.Module.equals("NumberOfUsers") || gr.Module.startsWith("swap_space_")
                || gr.Module.startsWith("mem_space_") || gr.Module.startsWith("DisksSize")
                || gr.Module.startsWith("eth0_NumKB")) {
            rez.addSet(gr.Module, gr.sum);
        } else if (gr.Module.startsWith("CPUUtilPerc") || gr.Module.equals("DisksReadRate")
                || gr.Module.equals("DisksWriteRate")) {
            rez.addSet(gr.Module, gr.mean);
        }

        return rez;
    }

    /**
     * @param gr
     */
    public void setGlobalVal(final Gresult gr) {
        setGlobalVal(gr, NTPDate.currentTimeMillis());
    }

    /**
     * @param gr
     * @param now
     */
    public void setGlobalVal(final Gresult gr, final long now) {
        if (gr == null) {
            return;
        }

        // filtering based on Cluster name
        if ((local_filter_clusters != null) && (local_filter_clusters.size() > 0)) {
            if (gr.ClusterName != null) {
                boolean matched = false;
                for (String reClus : local_filter_clusters) {
                    if (gr.ClusterName.matches(reClus)) {
                        matched = true;
                        break;

                    }
                }

                if (!matched) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "Ignoring Gresult " + gr + " because Cluster Name [ " + gr.ClusterName
                                + " ] not in the list ");
                        return;
                    }
                }
            }
        }

        if (local_filter_global_param.contains(gr.Module)) {
            newData(grToResult(gr, now), true);
        }
    }

    /**
     * @param r
     */
    public void processResult(final Result r) {
        final long now = NTPDate.currentTimeMillis();

        if (bUpdateResultTime
                || (bCorrectTimestamps && ((r.time > (now + (1000 * 60))) || (r.time < (now - (1000 * 24 * 60 * 60)))))) {
            r.time = now;
        }

        if (bCorrectVONames) {
            if ((r.ClusterName != null)
                    && (r.NodeName != null)
                    && (r.ClusterName.startsWith("VO_") || r.ClusterName.equals(community + "VoStorage") || (r.ClusterName
                            .startsWith(community + "VO_") && !r.ClusterName.endsWith("_Totals")))
                    && !r.NodeName.startsWith("Total")) {
                final String s = r.NodeName.toUpperCase();

                if (!r.NodeName.equals(s)) {
                    r.NodeName = s;
                }
            }
        }

        if (r.ClusterName.equals("Master")) {
            double in = 0, out = 0;
            int inParams = 0, outParams = 0;
            for (int i = 0; i < r.param_name.length; i++) {
                if (r.param_name[i].indexOf("_IN") != -1) {
                    in += r.param[i];
                    inParams++;
                } else if (r.param_name[i].indexOf("_OUT") != -1) {
                    out += r.param[i];
                    outParams++;
                }
            }

            if ((outParams + inParams) > 0) {
                final Result newr = new Result();

                newr.FarmName = r.FarmName;
                newr.NodeName = r.NodeName;
                newr.ClusterName = r.ClusterName;
                newr.time = r.time;

                for (int i = 0; i < r.param_name.length; i++) {
                    if ((r.param_name[i].indexOf("_IN") == -1) || (r.param_name[i].indexOf("_OUT") == -1)) {
                        newr.addSet(r.param_name[i], r.param[i]);
                    }
                }
                newr.addSet("Traffic_IN", in);
                newr.addSet("Traffic_OUT", out);

                newData(newr, true);
            } else {
                newData(r, true);
            }
        } else {
            newData(r, true);
        }
    }

    /**
     * @param er
     */
    public void processResult(final eResult er) {
        final long now = NTPDate.currentTimeMillis();

        if (bUpdateResultTime || (er.time > (now + (1000 * 60)))) {
            er.time = now;
        }

        newData(er, true);
    }

    /**
     * 
     */
    public void verifyNodes() {
        // does nothing in Store client
    }

    @Override
    public void removeNode(final ServiceID id) {

        final tmProxyStore connectionToProxy = connectionToProxyPointer.get();

        if ((connectionToProxy != null) && connectionToProxy.isActive()) {
            connectionToProxy.removeFarmClient(id);
        }

        synchronized (nodesSync) {
            if ((id != null) && snodes.containsKey(id)) {
                rcStoreNode n = snodes.remove(id);
                if (n != null) {
                    if (n.client != null) {
                        n.client.active = false;
                        n.client.deleteLocalClient(null); // unregister all predicates and filters
                        n.client.deleteAppControlClient(this);
                    }
                    if (n.UnitName != null) {
                        nodes.remove(n.UnitName);
                        logger.log(Level.INFO, "Service " + n.UnitName + " is offline");

                        notifyConnectionsMonitors(n.UnitName, false);

                        // remove the name from the Name -> IP mappings, the next time it is discovered it will generate
                        // an full DB info update for this service
                        htFarms.remove(n.UnitName);
                    } else {
                        logger.log(Level.WARNING, ">>>> removeNode: Node " + n
                                + " has UnitName NULL! [ SHOULD NOT GET HERE]");
                    }

                    htControlStatus.remove(n.UnitName);
                } else {
                    logger.log(Level.WARNING, ">>>> removeNode: Node is NULL! [ SHOULD NOT GET HERE]");
                }
            }
        }
    }

    @Override
    public void Shutdown() {
        store.close();
    }

    /**
     * @param farmID
     */
    @Override
    public boolean knownConfiguration(final ServiceID farmID) {
        return true;
    }

    @Override
    public boolean verifyProxyConnection() {
        final tmProxyStore connectionToProxy = connectionToProxyPointer.get();

        if ((connectionToProxy == null) || (connectionToProxy.verifyProxyConnection() == false)) {
            return false;
        }

        return true;
    }

    @Override
    public void AddProxyService(final ServiceItem si) throws Exception {
        if (si == null) {
            return;
        }

        Entry[] proxyEntry = si.attributeSets;
        if (proxyEntry != null) {

            if (proxyEntry.length > 0) {
                int portNumber = ((ProxyServiceEntry) proxyEntry[0]).proxyPort.intValue();
                String ipAddress = ((ProxyServiceEntry) proxyEntry[0]).ipAddress;
                InetAddress inetAddress = InetAddress.getByName(ipAddress);
                logger.log(Level.INFO, "====> FOUND PROXY FROM ADDRESS: " + inetAddress.toString());

                tmProxyStore cToProxy = null;
                try {
                    cToProxy = new tmProxyStore(inetAddress, portNumber,
                            new Hashtable<ServiceID, MonMessageClientsProxy>(), this);
                    cToProxy.startCommunication();
                    this.connectionToProxyPointer.set(cToProxy);
                } catch (Exception ex) {
                    if (cToProxy != null) {
                        try {
                            cToProxy.closeProxyConnection();
                        } catch (Throwable t) {
                            //ignore it
                        }
                    }
                    throw ex;
                }

            } // if
        }// if
    }

    /**
     * Entry point for the Repository
     * 
     * @param args
     */
    public static void main(final String[] args) {
        if ("bajie".equals(sServletContainer)) {
            BajieHttpSrvStarter.startBajie();
        } else {
            TomcatStarter.startTomcat();
        }

        getInstance();

        ((TransparentStoreFast) TransparentStoreFactory.getStore()).getStartTime();

        SimpleExporter.main(null);
    }

    /*
     * (non-Javadoc)
     * @see lia.Monitor.JiniClient.CommonJini.JiniClient#portMapChanged(net.jini.core.lookup.ServiceID,
     * java.util.ArrayList)
     */
    /**
     * @param id
     * @param portMap
     */
    @Override
    public void portMapChanged(ServiceID id, ArrayList<?> portMap) {
        // nothing
    }

    /**
     * Dynamically register a new predicate. The new predicate will be sent
     * to all the known services and it will also be automatically sent to all the
     * newly discovered services.
     * 
     * @param pred
     *            the predicate to register
     * @return true if the registration was successful, false if the same predicate was already registered
     */
    public final boolean registerPredicate(final monPredicate pred) {

        synchronized (local_filter_pred) {
            for (int i = 0; i < local_filter_pred.size(); i++) {
                final monPredicate p = local_filter_pred.get(i);

                if (JtClient.predicatesComparator.compare(p, pred) == 0) {
                    return false;
                }
            }

            local_filter_pred.add(pred);
        }

        synchronized (nodesSync) {

            logger.log(Level.FINE, "Sending predicate to all known services : " + pred);

            final Iterator<rcStoreNode> itServices = snodes.values().iterator();

            while (itServices.hasNext()) {
                final rcStoreNode rc = itServices.next();

                logger.log(Level.FINEST, "Sending predicate to : " + rc.client.farm);

                rc.client.addLocalClient(this, pred);
            }
        }

        return true;
    }

    /**
     * Stop listening for one predicate. It will also remove the given predicate
     * from the list of predicates that are sent to a newly discovered service.
     * 
     * @param pred
     *            the predicate to remove
     * @return true if the predicate existed and was removed, false if the predicate was not registered
     */
    public final boolean unregisterPredicate(final monPredicate pred) {
        synchronized (local_filter_pred) {

            boolean bFound = false;

            final Iterator<monPredicate> it = local_filter_pred.iterator();

            while (it.hasNext()) {
                final monPredicate p = it.next();

                if (JtClient.predicatesComparator.compare(p, pred) == 0) {
                    it.remove();
                    bFound = true;
                }
            }

            if (!bFound) {
                return false;
            }
        }

        synchronized (nodesSync) {

            logger.log(Level.FINE, "Removing predicate from all known services : " + pred);

            final Iterator<rcStoreNode> itServices = snodes.values().iterator();

            while (itServices.hasNext()) {
                final rcStoreNode rc = itServices.next();

                logger.log(Level.FINEST, "Removing predicate from : " + rc.client.farm);

                rc.client.unregister(pred);
            }
        }

        return true;
    }

    /*
     * (non-Javadoc)
     * @see lia.Monitor.monitor.AppConfigChangeListener#notifyAppConfigChanged()
     */
    @Override
    public void notifyAppConfigChanged() {

        logger.log(Level.FINE, "I've been notified that the configuration file has changed");

        final String[] vsPreds = AppConfig.getVectorProperty("lia.Monitor.JiniClient.Store.predicates");
        final ArrayList<monPredicate> alPreds = new ArrayList<monPredicate>();

        for (int i = 0; (vsPreds != null) && (i < vsPreds.length); i++) {
            if ((vsPreds[i] != null) && (vsPreds[i].trim().length() > 0)) {
                monPredicate pred = lia.web.utils.Formatare.toPred(vsPreds[i]);

                alPreds.add(pred);
            }
        }

        final ArrayList<monPredicate> alAdd = new ArrayList<monPredicate>();
        final ArrayList<monPredicate> alRemove = new ArrayList<monPredicate>();

        synchronized (local_filter_pred) {
            for (int i = 0; i < alPreds.size(); i++) {

                final monPredicate predNew = alPreds.get(i);

                boolean bFound = false;

                for (int j = 0; j < local_filter_pred.size(); j++) {
                    final monPredicate predOld = local_filter_pred.get(j);

                    if (JtClient.predicatesComparator.compare(predNew, predOld) == 0) {
                        bFound = true;
                        break;
                    }
                }

                if (!bFound) {
                    alAdd.add(predNew);
                }
            }

            for (int i = 0; i < local_filter_pred.size(); i++) {

                final monPredicate predOld = local_filter_pred.get(i);

                boolean bFound = false;

                for (int j = 0; j < alPreds.size(); j++) {
                    final monPredicate predNew = alPreds.get(j);

                    if (JtClient.predicatesComparator.compare(predNew, predOld) == 0) {
                        bFound = true;
                        break;
                    }
                }

                if (!bFound) {
                    alRemove.add(predOld);
                }

            }

            for (int i = 0; i < alRemove.size(); i++) {
                final monPredicate predRemove = alRemove.get(i);

                final boolean bUnregisterResult = unregisterPredicate(predRemove);

                logger.log(Level.INFO, "Remove predicate : " + predRemove + " -- " + bUnregisterResult);
            }

            for (int i = 0; i < alAdd.size(); i++) {
                final monPredicate predAdd = alAdd.get(i);

                final boolean bRegisterResult = registerPredicate(predAdd);

                logger.log(Level.INFO, "Add predicate : " + predAdd + " -- " + bRegisterResult);
            }
        }
    }

    private final Hashtable<String, Boolean> htControlStatus = new Hashtable<String, Boolean>();

    /**
     * @param service
     * @return 0 = ok, can be controlled<br>
     *         1 = cannot be controlled<br>
     *         2 = no control info available (yet?)
     */
    public int getControlStatus(final String service) {
        if ((service == null) || (service.length() == 0)) {
            return 2;
        }

        final Boolean b = htControlStatus.get(service);

        if (b != null) {
            return b.booleanValue() ? 0 : 1;
        }

        return 2;
    }

    /**
     * Get the wrapper for a service connection for a service with the given name
     * 
     * @param name
     * @return the {@link MLSerClient} wrapper or <code>null</code> if the service is not online
     */
    private MLSerClient getService(final String name) {
        synchronized (nodesSync) {
            final Iterator<rcStoreNode> itServices = snodes.values().iterator();

            while (itServices.hasNext()) {
                final rcStoreNode rc = itServices.next();

                if (rc.UnitName.equals(name)) {
                    return rc.client;
                }
            }
        }

        return null;
    }

    private final AtomicLong cmdSequence = new AtomicLong(0);

    private final Hashtable<Long, Long> htNotificationMap = new Hashtable<Long, Long>();

    private final Hashtable<Long, AtomicInteger> htReturnCount = new Hashtable<Long, AtomicInteger>();

    private final Hashtable<Long, TreeMap<Long, CommandResult>> htReturnedValues = new Hashtable<Long, TreeMap<Long, CommandResult>>();

    /**
     * Execute a list of commands on a remote site
     * 
     * @param service
     * @param commands
     * @return a List of {@link CommandResult} entries
     */
    public final List<CommandResult> executeCommands(final String service, final String[] commands) {
        if ((service == null) || (commands == null) || (commands.length == 0)) {
            // invalid parameters, exit quickly
            return null;
        }

        if (getControlStatus(service) != 0) {
            // the SSL connection with this service is not established
            return null;
        }

        final MLSerClient client = getService(service);

        if (client == null) {
            // weird, if the control status is "established" we should have the client here ...
            htControlStatus.remove(service);

            return null;
        }

        final Long lCmdSeqenceID = Long.valueOf(cmdSequence.incrementAndGet());

        final TreeMap<Long, CommandResult> tmCommands = new TreeMap<Long, CommandResult>();

        int iCommandsCount = 0;

        final long lStart = System.currentTimeMillis();

        synchronized (htReturnedValues) {
            htReturnedValues.put(lCmdSeqenceID, tmCommands);

            htReturnCount.put(lCmdSeqenceID, new AtomicInteger(0));

            for (String command : commands) {
                final Long lID = client.sendAppControlCmd(this, command, null);

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "ID: " + lID + " for command: " + command);
                }

                if (lID != null) {
                    tmCommands.put(lID, new CommandResult(command));
                    htNotificationMap.put(lID, lCmdSeqenceID);
                    iCommandsCount++;
                }
            }
        }

        synchronized (lCmdSeqenceID) {
            while (((System.currentTimeMillis() - lStart) < (1000L * 60 * 3 * iCommandsCount))
                    && (htReturnCount.get(lCmdSeqenceID).get() < iCommandsCount) && (getControlStatus(service) == 0)) {
                try {
                    lCmdSeqenceID.wait(1000);
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
        }

        final List<CommandResult> lRet;

        synchronized (htReturnedValues) {
            // cleanup sequence
            lRet = new ArrayList<CommandResult>(tmCommands.values());

            final Iterator<Long> it = tmCommands.keySet().iterator();

            while (it.hasNext()) {
                htNotificationMap.remove(it.next());
            }

            htReturnedValues.remove(lCmdSeqenceID);
            htReturnCount.remove(lCmdSeqenceID);
        }

        return lRet;
    }

    @Override
    public void appControlStatus(final MLSerClient mlSerTClient, final boolean status) {
        htControlStatus.put(mlSerTClient.getFarmName(), Boolean.valueOf(status));
    }

    /**
     * @param mlSerTClient
     * @param params
     */
    @Override
    public void cmdResult(final MLSerClient mlSerTClient, final Long cmdID, final String message, final Object params) {
        // callback method from the appcontrol layer
        synchronized (htReturnedValues) {
            final Long lCmdSequenceID = htNotificationMap.get(cmdID);

            if (lCmdSequenceID == null) {
                return;
            }

            final TreeMap<Long, CommandResult> tmCommands = htReturnedValues.get(lCmdSequenceID);

            final CommandResult cr = tmCommands.get(cmdID);

            if (cr != null) {
                cr.output = message;
                htReturnCount.get(lCmdSequenceID).incrementAndGet();
            }

            synchronized (lCmdSequenceID) {
                lCmdSequenceID.notifyAll();
            }
        }
    }
    
    public Map<String, Set<String>> getFarmIPAddresses(){
    	return htFarms;
    }
    
	public boolean addIPAddress(final String serviceName, final String ipAddress) {
		final Set<String> oldIPs;

		synchronized (htFarms) {
			oldIPs = htFarms.get(serviceName);

			if (oldIPs == null || oldIPs.contains(ipAddress)) {
				return false;
			}

			for (final Map.Entry<String, Set<String>> entry : htFarms.entrySet()) {
				if (!entry.getKey().equals(serviceName) && entry.getValue().contains(ipAddress)) {
					logger.log(Level.WARNING, "Asked to add '" + ipAddress + "' to '" + serviceName + "' but this IP is in the list for '" + entry.getKey() + "'");
					return false;
				}
			}

			oldIPs.add(ipAddress);
		}

		final StringBuilder sbIPs = new StringBuilder();

		for (final String s : oldIPs) {
			if (sbIPs.length() > 0)
				sbIPs.append(',');

			sbIPs.append(s);
		}

		final String ipadList = sbIPs.toString();

		final DB db = new DB();

		db.syncUpdateQuery("UPDATE abping_aliases SET ip='" + Formatare.mySQLEscape(ipadList) + "' WHERE name='" + Formatare.mySQLEscape(serviceName) + "';");

		final int cnt = db.getUpdateCount();

		if (cnt == 0) {
			logger.log(Level.WARNING, "Update query to add '" + ipAddress + "' (yielding to '" + ipadList + "') to '" + serviceName + "' failed to update anything in the database.");
			return false;
		}

		return true;
	}
}
