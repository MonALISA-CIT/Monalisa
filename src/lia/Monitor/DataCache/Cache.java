package lia.Monitor.DataCache;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;

import lia.Monitor.Agents.IDS.IDSAgent;
import lia.Monitor.Agents.OpticalPath.MLCopyAgent;
import lia.Monitor.Agents.OpticalPath.v2.OpticalPathAgent_v2;
import lia.Monitor.Farm.FarmMonitor;
import lia.Monitor.JiniSerFarmMon.RegFarmMonitor;
import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.TransparentStoreInt;
import lia.Monitor.monitor.AccountingResult;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.DataReceiver;
import lia.Monitor.monitor.DataStore;
import lia.Monitor.monitor.ExtResult;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MonitorClient;
import lia.Monitor.monitor.MonitorFilter;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.ShutdownReceiver;
import lia.Monitor.monitor.cmonMessage;
import lia.Monitor.monitor.dbStore;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monMessage;
import lia.Monitor.monitor.monPredicate;
import lia.net.topology.agents.AFOXAgent;
import lia.net.topology.agents.CienaAgent;
import lia.net.topology.agents.ComputerHostAgent;
import lia.net.topology.agents.Force10Agent;
import lia.util.DropEvent;
import lia.util.MLProcess;
import lia.util.Utils;
import lia.util.logging.relay.MLLogSender;
import lia.util.mail.MailFactory;
import lia.util.mail.PMSender;
import lia.util.ntp.NTPDate;
import lia.util.threads.MonALISAExecutors;

/**
 * @author Iosif Legrand
 * @author ramiro
 */
public class Cache implements DataReceiver, DataStore, Runnable, ShutdownReceiver, DropEvent {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(Cache.class.getName());

    private static final boolean SHOULD_STORE_CONF = AppConfig.getb(
            "lia.Monitor.Store.TransparentStoreFast.SHOULD_STORE_CONF", false);

    transient volatile AgentsEngine agentsEngine;

    final transient AppControlEngine appControlDispatcher;

    final String appControlDispatcherNullCause;

    private static volatile boolean iStoreStarted = false;

    // //////////////////////////////////
    // RMI CODE REMOVED - since ML 1.8.0
    // /////////////////////////////////

    // private static final ConcurrentHashMap clients = new ConcurrentHashMap();

    // private static final Vector cclients = new Vector();

    private transient ArrayList<dbStore> dbStores = new ArrayList<dbStore>();

    private final AtomicBoolean bSwitched = new AtomicBoolean(false);

    private static final ConcurrentHashMap<String, MonitorFilter> filters = new ConcurrentHashMap<String, MonitorFilter>();

    String Name;

    public static transient tcpServer server;

    public static transient ProxyWorker pw;

    private static MFarm farm;

    private final FarmMonitor main;

    static TransparentStoreInt store;

    private static StoreFlusher storeFlusher = null;

    String myIPaddress = null;

    private static final transient SimpleDateFormat dateform = new SimpleDateFormat(" HH:mm '('z')'");

    private static final AtomicLong defaultAnnounceUpdate = new AtomicLong(2 * 60 * 1000);

    // used to count total number of collected values ( params in Result )
    private final AtomicLong collectedValues = new AtomicLong(0);

    private static Cache _thisInstance;

    private static boolean instanceInited = false;

    /* real time Results */
    public static final BlockingQueue<Object> toStoreResults;

    private static String username = "username";

    private static String hostname = "localhost";

    private static String realFromAddress = username + "@" + hostname;

    static {

        int maxResultQueueSize = 15000;
        try {
            maxResultQueueSize = AppConfig.geti("lia.Monitor.Farm.MAX_RESULTS_QUEUE_SIZE", 15000);
        } catch (Throwable t) {
            maxResultQueueSize = 15000;
        }

        logger.log(Level.INFO, " [ FarmMonitor ] MAX_RESULTS_QUEUE_SIZE = " + maxResultQueueSize);
        toStoreResults = new LinkedBlockingQueue<Object>(maxResultQueueSize);

        try {
            username = System.getProperty("user.name");
        } catch (Throwable t) {

        }

        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Throwable t) {
        }

        realFromAddress = username + "@" + hostname;

        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                reloadConfig();
            }

        });

    }

    private static final class StoreFlusher extends Thread {

        private final AtomicBoolean hasToRun;

        long lastConfWrite;

        private final AtomicBoolean newConf;

        public StoreFlusher() {
            super("( ML ) StoreFlusher");
            hasToRun = new AtomicBoolean(true);
            lastConfWrite = 0;
            newConf = new AtomicBoolean(true);
        }

        private void store() throws InterruptedException {
            final Object o = toStoreResults.take();
            store.addData(o);
            lia.Monitor.Store.Cache.addToCache(o);
        }

        public void updateConfig() {
            newConf.set(true);
        }

        @Override
        public void run() {
            while (hasToRun.get()) {
                try {
                    if (store != null) {
                        store();
                    }

                    if (SHOULD_STORE_CONF && newConf.get()
                            && ((lastConfWrite + (10 * 60 * 1000)) < System.currentTimeMillis())) {
                        if (store != null) {

                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, " [ StoreFlusher ] making cfg persistent");
                            }

                            store.updateConfig(farm);
                            lastConfWrite = System.currentTimeMillis();
                            newConf.set(false);
                        }
                    }
                } catch (InterruptedException ie) {
                    logger.log(Level.WARNING,
                            " [ Cache ] [ HANDLED ] StoreFlusher got InterruptedException in main loop", ie);
                    Thread.interrupted();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " [ Cache ] [ HANDLED ] StoreFlusher got general exception in main loop",
                            t);
                }
            }
        }
    }// class StoreFlusher

    private static final class ConfCheckerTask implements Runnable {

        private long lastModCount = 0;

        @Override
        public void run() {

            try {
                final long currentModCount = farm.modCount();

                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "[ ConfCheckerTask ] lastModCount = " + lastModCount + " farm.modCount() = "
                            + currentModCount);
                }

                if (currentModCount != lastModCount) {
                    lastModCount = currentModCount;
                    sendConf();
                }

            } catch (Throwable t) {
                logger.log(Level.WARNING, "[ Cache ] [ ConfCheckerTask ] [ HANDLED ] exc: ", t);
            } finally {
                ScheduledFuture<?> sf = null;
                while (sf == null) {
                    try {
                        sf = MonALISAExecutors.getMLHelperExecutor().schedule(this,
                                defaultAnnounceUpdate.get() + Math.round(Math.random() * 100), TimeUnit.MILLISECONDS);
                    } catch (Throwable t) {
                        sf = null;
                        logger.log(
                                Level.WARNING,
                                " [ ConfCheckerTask ] Unable to schedule for the moment ... Retry in a few millis. Cause:",
                                t);
                    }

                    if (sf != null) {
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, " [ ConfCheckerTask ] rescheduled ... ");
                        }
                        break;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (Throwable _) {
                    }
                }
            }
        }
    }// class ConfCheckerTask

    private static final class ClientsVerifierTask implements Runnable {

        @Override
        public void run() {
            try {
                // //////////////////
                // RMI CODE REMOVED
                // /////////////////

                // for (Enumeration en = clients.keys(); en.hasMoreElements();) {
                // MonitorClient cl = (MonitorClient) en.nextElement();
                // ClientWorker cw = (ClientWorker) clients.get(cl);
                // if (!cw.active) stopClientWorker(cl);
                // }

                for (Map.Entry<String, MonitorFilter> entry : filters.entrySet()) {

                    final String fkey = entry.getKey();
                    final MonitorFilter fa = entry.getValue();

                    if (!fa.isAlive()) {
                        stopFilter(fkey, fa);
                    }
                }

                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "tcpClients: " + server.getConnNo());
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ Cache ] [ ClientsVerifierTask ] [ HANDLED ] exc: ", t);
            }
        }
    }// class ClientsVerifierTask

    public static final Cache initInstance(final String n, final FarmMonitor main) {

        synchronized (Cache.class) {
            if (!instanceInited) {
                try {
                    _thisInstance = new Cache(n, main);
                } finally {
                    instanceInited = true;
                    Cache.class.notifyAll();
                }
            }
        }

        return _thisInstance;
    }

    public static final Cache getInstance() {
        synchronized (Cache.class) {
            while (!instanceInited) {
                try {
                    Cache.class.wait();
                } catch (Throwable t) {
                }
            }
        }

        return _thisInstance;
    }

    private Cache(final String n, final FarmMonitor main) {
        this.Name = n;
        this.main = main;
        // lastValues = new Hashtable();

        farm = main.getMFarm();

        // default SQL_STORE
        boolean shouldCreateStore = true;
        try {
            shouldCreateStore = AppConfig.getb("lia.Monitor.CreatePersistentStore", true);
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        "[ DataCache ] [<init>] Exception parsing lia.Monitor.CreatePersistentStore. Cause: ", t);
            }
            shouldCreateStore = true;
        }

        if (!shouldCreateStore) {
            store = null;
            logger.log(Level.INFO,
                    "\n\n[ DataCache ] [<init>] MonALISA will not use a PersistentStore ... no persistent history available!");
        } else {
            logger.log(Level.INFO, "[ Cache ] Trying to create store");
            store = TransparentStoreFactory.getStore();
            logger.log(Level.INFO, "[ Cache ] Store store created");
        }

        if (store != null) {
            storeFlusher = new StoreFlusher();
            storeFlusher.start();
        }
        iStoreStarted = true;

        try {
            String forceIP = AppConfig.getProperty("lia.Monitor.useIPaddress");
            if (forceIP != null) {
                myIPaddress = (InetAddress.getByName(forceIP)).getHostAddress();
            } else {
                myIPaddress = (InetAddress.getLocalHost()).getHostAddress();
            }

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "CACHE use IP: " + myIPaddress);
            }

        } catch (Throwable t) {
            logger.log(Level.SEVERE, "\n *** ERROR --->Can not get Ip | host name \n", t);
        }

        tcpServer tServer = null;
        try {
            tServer = new tcpServer(this);
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "\n [ DataCache ] Cannot Start ML tcpServer \n", t);
            tServer = null;
        }
        server = tServer;

        StringBuilder sb = new StringBuilder();
        AppControlEngine tmpAppControlDispatcher = null;
        // start the AppControl dispatcher ?
        if (AppConfig.getb("lia.app.tunneled.start", true)) {
            try {
                tmpAppControlDispatcher = new AppControlEngine();
                logger.log(Level.INFO, "Tunneled APP Control Interface started");
                sb.append("Tunneled APP Control Interface started OK!");
            } catch (Throwable t) {
                tmpAppControlDispatcher = null;
                logger.log(Level.WARNING, "Cannot start tunneled APP Control Interface", t);
                sb.append("Tunneled APP Control Interface did not start because got exception in initialization. Cause:");
                sb.append(Utils.getStackTrace(t)).append("\n");
            }
        } else {
            sb.append("Tunneled APP Control Interface did not start because lia.app.tunneled.start = false");
        }

        appControlDispatcher = tmpAppControlDispatcher;
        appControlDispatcherNullCause = sb.toString();

        pw = ProxyWorker.getInstance(this, server);

        PMSender.setPW(pw);
        MLLogSender.setPW(pw);

    }

    public static boolean internalStoreStarted() {
        return iStoreStarted;
    }

    public static final MFarm getMFarm() {
        return farm;
    }

    /**
     * @throws java.rmi.RemoteException
     */
    public int getRegistryPort() throws java.rmi.RemoteException {
        return RegFarmMonitor.REGISTRY_PORT;
    }

    /**
     * @throws java.rmi.RemoteException
     */
    public int getMLPort() throws java.rmi.RemoteException {
        return server.lis_port;
    }

    @Override
    public String getIPAddress() {
        return myIPaddress;
    }

    @Override
    public String getUnitName() {
        return farm.name;
    }

    public static final String getMLVersion() {
        return FarmMonitor.MonaLisa_version;
    }

    // public Date getLocalTime() { return new Date() ; }
    @Override
    public String getLocalTime() {
        return dateform.format(new Date(NTPDate.currentTimeMillis()));

    }

    public void agentsMsg(monMessage mm) {

        if (mm == null) {
            logger.log(Level.WARNING, "Cache ===> agentsMsg null ... return");
            return;
        }

        agentsEngine.messageReceived(mm);

    } // agentsMsg

    @Override
    public void run() {

        boolean initedAgentPlatform = false;
        for (;;) {
            try {
                if (!initedAgentPlatform && (pw.proxyConnectionsCount() != 0)) {

                    initedAgentPlatform = true;

                    agentsEngine = new AgentsEngine(pw);

                    logger.log(Level.INFO, "AgentsEngine started");

                    agentsEngine.newProxyConns();

                    // logger.log (Level.INFO, "====> Adding TestAgent ... ");
                    // TestAgent ta = new TestAgent ("TestAgent", "TestGroup", si.serviceID.toString());
                    // agentsEngine.addAgent (ta);

                    final boolean startMLCP = AppConfig.getb("lia.Monitor.DataCache.Cache.StartMLCopyAgent", false);
                    final String mlCopyAgentGroup = AppConfig.getProperty(
                            "lia.Monitor.DataCache.Cache.MLCopyAgent_group", null);
                    final String mlCopyAgentProtocolVersion = AppConfig.getProperty(
                            "lia.Monitor.DataCache.Cache.MLCopyAgent_ProtocolVersion", "v1");

                    if (startMLCP) {
                        if (mlCopyAgentGroup == null) {
                            logger.log(Level.WARNING,
                                    " Cannot start MLCopyAgent ... please specify a grup in ml.properties");
                        } else {
                            if ((mlCopyAgentProtocolVersion != null) && mlCopyAgentProtocolVersion.equals("v2")) {
                                OpticalPathAgent_v2 mlca = new OpticalPathAgent_v2("OpticalPathAgent_v2",
                                        mlCopyAgentGroup + "_v2", RegFarmMonitor.getServiceItem().serviceID.toString());
                                agentsEngine.addAgent(mlca);
                                mlca.initCache(this);
                                this.addFilter(mlca);
                            } else {
                                MLCopyAgent mlca = new MLCopyAgent("MLCopyAgent", mlCopyAgentGroup,
                                        RegFarmMonitor.getServiceItem().serviceID.toString());
                                agentsEngine.addAgent(mlca);
                                mlca.initCache(this);
                                this.addFilter(mlca);
                            }
                            logger.log(Level.INFO, "MLCopyAgent started.");
                        }
                    } // if

                    // start the IDSAgent?
                    final boolean startMLIDSAgent = AppConfig
                            .getb("lia.Monitor.DataCache.Cache.StartMLIDSAgent", false);
                    if (startMLIDSAgent) {
                        IDSAgent mlids = new IDSAgent("MLIDSAgent", "MLIDSAgentGroup",
                                RegFarmMonitor.getServiceItem().serviceID.toString());
                        agentsEngine.addAgent(mlids);
                        mlids.initCache(this);
                        // add IDSFilter
                        this.addFilter(mlids);
                        logger.log(Level.INFO, "MLIDSAgent started.");
                    }

                    final boolean startMLAFOXAgent = AppConfig.getb("lia.Monitor.DataCache.Cache.StartMLAFOXAgent",
                            false);
                    if (startMLAFOXAgent) {
                        AFOXAgent mlids = new AFOXAgent("AFOXAgent", "MLTopoAgentGroup",
                                RegFarmMonitor.getServiceItem().serviceID.toString());
                        agentsEngine.addAgent(mlids);
                        mlids.initCache(this);
                        // add IDSFilter
                        this.addFilter(mlids);
                        logger.log(Level.INFO, "MLTopoAgent started.");
                    }

                    final boolean startMLComputerHostAgent = AppConfig.getb(
                            "lia.Monitor.DataCache.Cache.StartComputerHostAgent", false);
                    if (startMLComputerHostAgent) {
                        ComputerHostAgent mlids = new ComputerHostAgent("ComputerHostAgent", "MLTopoAgentGroup",
                                RegFarmMonitor.getServiceItem().serviceID.toString());
                        agentsEngine.addAgent(mlids);
                        mlids.initCache(this);
                        // add IDSFilter
                        this.addFilter(mlids);
                        logger.log(Level.INFO, "ComputerHostAgent started.");
                    }

                    final boolean startForce10Agent = AppConfig.getb("lia.Monitor.DataCache.Cache.StartForce10Agent",
                            false);
                    if (startForce10Agent) {
                        Force10Agent mlids = new Force10Agent("Force10Agent", "MLTopoAgentGroup",
                                RegFarmMonitor.getServiceItem().serviceID.toString());
                        agentsEngine.addAgent(mlids);
                        mlids.initCache(this);
                        // add IDSFilter
                        this.addFilter(mlids);
                        logger.log(Level.INFO, "Force10Agent started.");
                    }

                    final boolean startCienaAgent = AppConfig
                            .getb("lia.Monitor.DataCache.Cache.StartCienaAgent", false);
                    if (startCienaAgent) {
                        CienaAgent mlids = new CienaAgent("CienaAgent", "MLTopoAgentGroup",
                                RegFarmMonitor.getServiceItem().serviceID.toString());
                        agentsEngine.addAgent(mlids);
                        mlids.initCache(this);
                        // add IDSFilter
                        this.addFilter(mlids);
                        logger.log(Level.INFO, "CienaAgent started.");
                    }
                }// if (si != null && pw == null)

                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                }

                if (initedAgentPlatform) {
                    ScheduledFuture<?> sf = null;
                    while (sf == null) {
                        try {
                            sf = MonALISAExecutors.getMLHelperExecutor().schedule(new ConfCheckerTask(),
                                    defaultAnnounceUpdate.get() + Math.round(Math.random() * 100),
                                    TimeUnit.MILLISECONDS);
                        } catch (Throwable t) {
                            sf = null;
                            logger.log(
                                    Level.WARNING,
                                    " [ ConfCheckerTask ] Unable to schedule for the moment ... Retry in a few millis. Cause:",
                                    t);
                        }

                        if (sf != null) {
                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, " [ ConfCheckerTask ] rescheduled ... ");
                            }
                            break;
                        }

                        try {
                            Thread.sleep(1000);
                        } catch (Throwable _) {
                        }
                    }

                    MonALISAExecutors.getMLHelperExecutor().scheduleWithFixedDelay(new ClientsVerifierTask(),
                            91 + Math.round(Math.random() * 2), 120 + Math.round(Math.random() * 8), TimeUnit.SECONDS);
                    break;
                }

            } catch (Throwable t) {
                logger.log(Level.WARNING, "Got exception Cache MAIN LOOP", t);
            }
        }
    }

    public void notifyInternalResults(Vector<?> v) {
        if ((v != null) && (v.size() > 0)) {
            main.addResults(v);
        }
    }

    @Override
    public void addResult(Result r) {
        if (r.param != null) {
            collectedValues.addAndGet(r.param.length);
        }

        if (pw != null) {
            pw.newResult(r);
        }

        inform_clients(r);

        if (server != null) {
            server.newResult(r);
        }

        if (store != null) {
            if (!toStoreResults.offer(r)) {
                logger.log(Level.WARNING, " [ CACHE ] [ Buffer FULL ] ... notify-ing internal store");
                notifyDrop();
            }
        }
    }

    @Override
    public void addResult(ExtResult r) {
        if (r.param != null) {
            collectedValues.addAndGet(r.param.length);
        }

        if (pw != null) {
            pw.newResult(r);
        }

        inform_clients(r);
        if (server != null) {
            server.newResult(r);
        }

    }

    @Override
    public void addResult(eResult r) {
        if (r.param != null) {
            collectedValues.addAndGet(r.param.length);
        }
        if (pw != null) {
            pw.newResult(r);
        }

        inform_clients(r);

        if (server != null) {
            server.newResult(r);
        }

        if (store != null) {
            if (!toStoreResults.offer(r)) {
                logger.log(Level.WARNING, " [ CACHE ] [ Buffer FULL ] ... notify-ing internal store");
                notifyDrop();
            }
        }
    }

    @Override
    public void addResult(AccountingResult r) {
        if (r.vsParams != null) {
            collectedValues.addAndGet(r.vsParams.size());
        }
        if (pw != null) {
            pw.newResult(r);
        }

        if (server != null) {
            server.newResult(r);
        }

        inform_clients(r);

        if (store != null) {
            if (!toStoreResults.offer(r)) {
                logger.log(Level.WARNING, " [ CACHE ] [ Buffer FULL ] ... notify-ing internal store");
                notifyDrop();
            }
        }
    }

    public long getTotalParametersCollected() {
        return collectedValues.get();
    }

    @Override
    public void updateConfig(MFarm farm) throws Exception {
        // newConf.set(true);
    }

    private static void sendConf() {

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [ Cache ] sendConf() ");
        }

        try {
            for (MonitorFilter fa : filters.values()) {
                try {
                    if (fa.isAlive()) {
                        fa.confChanged();
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " Got exc while notif config changed for filter .... " + fa.getName(), t);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exc while notif filters .... ", t);
        }

        // //////////////////////////////////
        // RMI CODE REMOVED - since ML 1.8.0
        // /////////////////////////////////

        // for (int i = 0; i < cclients.size(); i++) {
        // MonitorClient cli = (MonitorClient) cclients.elementAt(i);
        // try {
        // cli.newConfig(farm);
        // } catch (Exception ex) {
        // logger.log(Level.WARNING, " Failed to send new conf to client ... remove it ", ex);
        // cclients.removeElementAt(i);
        // }
        // }// end for

        try {
            server.updateConfig(farm);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[CACHE] [ sendUpdate ] [ HANDLED ] Notif SERVER conf got exc", t);
        }

        try {
            if (pw != null) {
                pw.updateConfig(farm);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[CACHE] [ sendUpdate ] [ HANDLED ] Notif ProxyWroker(s) conf got exc", t);
        }
        try {
            if (storeFlusher != null) {
                storeFlusher.updateConfig();
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[CACHE] [ sendUpdate ] [ HANDLED ] Notif StoreFlusher conf got exc", t);
        }
    }

    public static final cmonMessage compressObject(Object o) {
        cmonMessage cm = null;
        try {
            final Deflater compresser = new Deflater();

            StringBuilder sbLog = null;
            byte[] buff;

            if (logger.isLoggable(Level.FINEST)) {
                sbLog = new StringBuilder();
                sbLog.append("\nMem Total [ ").append(Runtime.getRuntime().totalMemory()).append(" ] ");
                sbLog.append("Mem Free [ ").append(Runtime.getRuntime().freeMemory()).append(" ] ");
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);

            oos.writeObject(o);
            oos.flush();
            baos.flush();
            buff = baos.toByteArray();
            if (logger.isLoggable(Level.FINEST)) {
                sbLog.append("\ncompressObject UNcompressed size = ").append(buff.length).append(" bytes");
                sbLog.append("\nMem Total [ ").append(Runtime.getRuntime().totalMemory()).append(" ] ");
                sbLog.append("Mem Free [ ").append(Runtime.getRuntime().freeMemory()).append(" ] ");
            }
            compresser.reset();
            compresser.setInput(buff);
            compresser.finish();

            // buff cannot be null !? ... else Exception
            byte[] output = new byte[buff.length];// in the worst case...no compression
            byte[] outc = new byte[compresser.deflate(output)];

            System.arraycopy(output, 0, outc, 0, outc.length);

            output = null;

            if (logger.isLoggable(Level.FINEST)) {
                sbLog.append("\ncommpressObject compressed size = ").append(outc.length).append("bytes");
                sbLog.append("\nMem Total [ ").append(Runtime.getRuntime().totalMemory()).append(" ] ");
                sbLog.append("Mem Free [ ").append(Runtime.getRuntime().freeMemory()).append(" ] ");
                logger.log(Level.FINEST, "\n\n compress Status:\n " + sbLog.toString() + "\n");
            }
            // System.gc();//hope it works...

            cm = new cmonMessage(buff.length, outc);
        } catch (Throwable t) {
            cm = null;
        }
        return cm;
    }

    @Override
    synchronized public void Register(MonitorClient client, monPredicate predicate) {
        //

        // if (!clients.containsKey(client)) {
        // logger.log(Level.INFO, " ========> New Client =" + client);
        // ClientWorker cw = new ClientWorker(this, client, predicate);
        // clients.put(client, cw);
        // } else {
        // ClientWorker cw = (ClientWorker) clients.get(client);
        // if (cw.active) {
        // cw.addPredicate(predicate);
        // } else {
        // stopClientWorker(client);
        // ClientWorker cw1 = new ClientWorker(this, client, predicate);
        // clients.put(client, cw1);
        // }
        // }

    }

    // FILTERS !

    /**
     * @throws java.rmi.RemoteException
     */
    @Override
    public void addFilter(MonitorFilter f) throws java.rmi.RemoteException {
        String name = f.getName();
        if (filters.containsKey(name)) {
            logger.log(Level.WARNING, " Filter " + name + " is active can not add it");
            return;
        }
        filters.put(name, f);
        f.initdb(store, farm);
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, " Filter " + name + " added");
        }
    }

    /**
     * @throws java.rmi.RemoteException
     */
    @Override
    public String[] getFilterList() throws java.rmi.RemoteException {
        return filters.keySet().toArray(new String[0]);
    }

    /**
     * @throws java.rmi.RemoteException
     */
    @Override
    synchronized public void Register(MonitorClient c, String filter) throws java.rmi.RemoteException {

        final MonitorFilter f = filters.get(filter);
        if (f == null) {
            logger.log(Level.WARNING, " Filter " + filter + " not ACTIVE...Cannot Register with such filter");
            return;
        }
        f.addClient(c);

    }

    void notifyPMS(Object o) {
        if (pw != null) {
            pw.notifyPMS(o);
        }
    }

    public static void stopFilter(String fkey, MonitorFilter fa) {
        filters.remove(fkey);
        fa.finishIt();
        fa = null;
    }

    @Override
    public MFarm confRegister(MonitorClient client) {
        // //////////////////////////////////
        // RMI CODE REMOVED - since ML 1.8.0
        // /////////////////////////////////

        // cclients.add(client);
        return farm;
    }

    public static void stopClientWorker(MonitorClient client) {

        // if (logger.isLoggable(Level.FINE)) {
        // logger.log(Level.FINE, " Stop Client Worker " + client);
        // }
        // ClientWorker cw = (ClientWorker) clients.remove(client);
        // if (cw != null) {
        // cw.finishIt();
        // cw = null;
        // // System.gc();
        // }
    }

    @Override
    public void unRegister(MonitorClient client) {
        // if (clients.containsKey(client)) {
        // stopClientWorker(client);
        // }
    }

    public Vector<Object> select(monPredicate p) {
        ArrayList<Object> retV = new ArrayList<Object>();
        try {
            if (store != null) {
                Vector<?> v = null;
                if (p.bLastVals) {
                    v = lia.Monitor.Store.Cache.getLastValues(p);
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER,
                                " Got RT query [ " + p + " ] returning " + (v == null ? "null" : "" + v.size())
                                        + " values");
                    }
                }

                if ((v == null) || (v.size() == 0)) {
                    if (store != null) {
                        v = store.select(p);
                    } else {
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, " [ Cache ] Null store for predicate ( " + p + ") ");
                        }
                    }
                }
                if ((v != null) && (v.size() != 0)) {
                    retV.addAll(v);
                }
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER,
                        " [ Cache ] Got exception looking for lia.Monitor.Store.Cache.getLastValues || store.select( "
                                + p + ") ", t);
            }
        }

        synchronized (dbStores) {
            for (final dbStore db : dbStores) {
                try {
                    Vector<?> v = db.select(p);
                    if ((v != null) && (v.size() > 0)) {
                        retV.addAll(v);
                    }
                } catch (Throwable t) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, " [ Cache ] Got exception looking dbStores " + db + " predicate: " + p,
                                t);
                    }
                }
            }
        }
        return new Vector<Object>(retV);
    }

    public void updateDBStores(Vector<dbStore> dbStores) {
        if ((dbStores == null) || (dbStores.size() == 0)) {
            return;
        }
        synchronized (this.dbStores) {
            this.dbStores.clear();
            this.dbStores.addAll(dbStores);
        }

    }

    public void funRegister(MonitorClient client) {

        for (final MonitorFilter f : filters.values()) {
            f.removeClient(client);
        }
    }

    @Override
    public void unRegister(MonitorClient client, Integer dkey) {
        // if (clients.containsKey(client)) {
        // ClientWorker cw = (ClientWorker) clients.get(client);
        // cw.deletePredicate(dkey);
        // if (logger.isLoggable(Level.FINEST)) {
        // logger.log(Level.FINEST, " Delete predicate id=" + dkey);
        // logger.log(Level.FINEST, "Predicate size==" + cw.getPredicatesSize());
        // }
        // }
    }

    void inform_clients(Object r) {
        // for (Enumeration e = clients.elements(); e.hasMoreElements();) {
        // ClientWorker lw = (ClientWorker) e.nextElement();
        // lw.addNewResult(r);
        // }

        for (final MonitorFilter fa : filters.values()) {
            if (fa.isAlive()) {
                fa.addNewResult(r);
            }
        }
    }

    /**
     * @throws java.rmi.RemoteException
     */
    public String getName() throws java.rmi.RemoteException {
        return Name;
    }

    @Override
    public void Shutdown() {
        if (store != null) {
            store.close();
        }
    }

    private static final void reloadConfig() {
        final String sLevel = AppConfig.getProperty("lia.Monitor.DataCache.Cache.level");
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
            defaultAnnounceUpdate.set(AppConfig.getl("lia.Monitor.DataCache.defaultAnnounceUpdate",
                    defaultAnnounceUpdate.get()));
        } catch (Throwable t) {
            logger.log(Level.INFO,
                    " [ Cache ] [ reloadConfig ] got exception setting lia.Monitor.DataCache.defaultAnnounceUpdate ", t);
            defaultAnnounceUpdate.set(2 * 60 * 1000);
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [ Cache ] reloadedConf. Logging level: " + loggingLevel
                    + " lia.Monitor.DataCache.defaultAnnounceUpdate =" + defaultAnnounceUpdate.get());
        }
    }

    /**
     * this is called by the local receive buffer if the buffer is full ... The method will spawn a new thread and will
     * try to switch the storage to memory ... This will happen only once, a notification maill will be sent.
     */
    @Override
    public void notifyDrop() {
        if (bSwitched.compareAndSet(false, true)) {

            new Thread() {

                @Override
                public void run() {
                    StringBuilder sb = null;
                    Throwable switchException = null;

                    try {
                        setName("(ML) Cache store switcher - notifyDrop");
                        setDaemon(true);
                    } catch (Throwable ignore) {
                    }

                    try {
                        logger.log(Level.WARNING,
                                "\n\n[ Cache ] [ dropEvent ] - Database buffer full, switching to memory storage\n\n");
                        System.setProperty("lia.Monitor.Store.TransparentStoreFast.web_writes", "0");
                        store.reload();
                        logger.log(Level.INFO, "\n\n[ Cache ] [ dropEvent ] - Storage switching complete\n\n");
                    } catch (Throwable t) {
                        logger.log(Level.SEVERE,
                                "\n\n[ Cache ] [ dropEvent ] Unable to switch to memory storage. Cause:", t);
                        switchException = t;
                    }

                    sb = new StringBuilder(16384);
                    if (switchException != null) {
                        sb.append(
                                "\n\n [ Cache ] [ dropEvent ] Database buffer full and switching to memory storage got exception:\n")
                                .append(Utils.getStackTrace(switchException));
                    } else {
                        sb.append("\n\n [ Cache ] [ dropEvent ] Database buffer full, switching to memory storage was successful ... more to follow:\n");
                    }

                    sb.append("\nStorage configuration:\n");
                    sb.append("----------------------\n");

                    try {
                        int nr = Integer.parseInt(AppConfig.getProperty(
                                "lia.Monitor.Store.TransparentStoreFast.web_writes", "0"));
                        sb.append("\nWriters : ").append(nr);
                        sb.append("\n");

                        for (int i = 0; i < nr; i++) {
                            final long lTotalTime = Long.parseLong(AppConfig.getProperty(
                                    "lia.Monitor.Store.TransparentStoreFast.writer_" + i + ".total_time", "0").trim()) * 1000;
                            final long lSamples = Long.parseLong(AppConfig.getProperty(
                                    "lia.Monitor.Store.TransparentStoreFast.writer_" + i + ".samples", "1").trim());
                            final String sTableName = AppConfig
                                    .getProperty("lia.Monitor.Store.TransparentStoreFast.writer_" + i + ".table_name",
                                            "writer_" + i).trim();

                            final int iWriteMode = Integer.parseInt(AppConfig.getProperty(
                                    "lia.Monitor.Store.TransparentStoreFast.writer_" + i + ".writemode", "0").trim());

                            sb.append("\nTotal time: ").append(lTotalTime);
                            sb.append("\nSamples   : ").append(lSamples);
                            sb.append("\nTable name: ").append(sTableName);
                            sb.append("\nWrite mode: ").append(iWriteMode);
                            sb.append("\n");
                        }
                    } catch (Throwable t) {
                        sb.append("\n\nGot exception checking store configuration:\n");
                        sb.append(Utils.getStackTrace(t)).append("\n\n");
                    }

                    try {

                        final String sFarmHome = AppConfig.getProperty("lia.Monitor.Farm.HOME", null);

                        sb.append("\nAccount: ").append(realFromAddress);
                        sb.append("\nFarm: ").append(getUnitName()).append(" (").append(getIPAddress()).append(") : ")
                                .append(getMLVersion());
                        sb.append("\nMemory: ").append(Runtime.getRuntime().freeMemory()).append(" free / ")
                                .append(Runtime.getRuntime().totalMemory()).append(" total / ")
                                .append(Runtime.getRuntime().maxMemory()).append(" max");
                        sb.append("\nML Home: ").append(AppConfig.getProperty("MonaLisa_HOME", null));
                        sb.append("\nFarm Home: ").append(sFarmHome);
                        sb.append("\n");
                        sb.append("\nStorage details:\n");
                        sb.append("----------------\n");
                        sb.append("\nemysql : ").append(AppConfig.getProperty("lia.Monitor.use_emysqldb", "false"));
                        sb.append("\nepgsql : " + AppConfig.getProperty("lia.Monitor.use_epgsqldb", "false") + "\n");
                        sb.append("\nstoreType : " + TransparentStoreFactory.getStoreType() + "\n");
                        sb.append("\n");
                        sb.append("\ndriver : ").append(
                                AppConfig.getProperty("lia.Monitor.jdbcDriverString", "default_driver_string"));
                        sb.append("\nserver : ").append(AppConfig.getProperty("lia.Monitor.ServerName", "127.0.0.1"))
                                .append(":").append(AppConfig.getProperty("lia.Monitor.DatabasePort", "0"));
                        sb.append("\ndbname : ").append(AppConfig.getProperty("lia.Monitor.DatabaseName", "mon_data"));
                        sb.append("\naccount: ").append(AppConfig.getProperty("lia.Monitor.UserName", "username"))
                                .append(":").append(AppConfig.getProperty("lia.Monitor.Pass", ""));
                        sb.append("\n");

                        if (sFarmHome != null) {
                            sb.append("\nMLStatFile contents:\n");
                            sb.append("--------------------\n");
                            sb.append(getFileContents(sFarmHome + File.separator + "MLStatFile", true));
                            sb.append("\n--------------------\n");
                            sb.append("\nml.properties contents:\n");
                            sb.append("--------------------\n");
                            sb.append(getFileContents(sFarmHome + File.separator + "MLStatFile", true));
                        }

                        sb.append("\n");

                        sb.append("mount output:\n");
                        sb.append("-------------\n");
                        sb.append(getOutput("mount", 100, 250));

                        sb.append("\n");

                        sb.append("df output:\n");
                        sb.append("----------\n");
                        sb.append(getOutput("df", 100, 250));

                        sb.append("\n");

                        sb.append("vmstat output:\n");
                        sb.append("--------------\n");
                        sb.append(getOutput("vmstat 1 5", 100, 250));

                        sb.append("\n");

                        sb.append("uptime:\n");
                        sb.append("-------\n");
                        sb.append(getOutput("uptime", 100, 250));

                        sb.append("\n");

                        sb.append("libc:\n");
                        sb.append("-----\n");
                        sb.append(getOutput("ls -l /lib/libc-*", 100, 250));

                        sb.append("\n");

                        String os = getFileContents("/etc/redhat-release", false);
                        if (os != null) {
                            sb.append("OS: RedHat: " + os + "\n\n");
                        }

                        os = getFileContents("/etc/slackware-version", false);
                        if (os != null) {
                            sb.append("OS: Slackware: " + os + "\n\n");
                        }

                        os = getFileContents("/etc/SuSE-release", false);
                        if (os != null) {
                            sb.append("OS: SuSE: " + os + "\n\n");
                        }

                        os = getFileContents("/etc/debian_version", false);
                        if (os != null) {
                            sb.append("OS: Debian: " + os + "\n\n");
                        }

                    } catch (Throwable t) {
                        if (sb != null) {
                            sb.append("\n\nGot Exception gathering local env informations: \n").append(
                                    Utils.getStackTrace(t));
                        }
                    } finally {
                        try {
                            MailFactory.getMailSender().sendMessage(
                                    realFromAddress,
                                    "mlstatus@monalisa.cern.ch",
                                    new String[] { "mlstatus@monalisa.cern.ch", "ramiro.voicu@gmail.com" },
                                    "[ Cache ] [ dropEvent ] Storage problem at " + getUnitName() + " / "
                                            + AppConfig.getProperty("lia.Monitor.group", null), sb.toString());
                        } catch (Throwable ignore) {
                        }
                    }
                }
            }.start();
        }// if already switched
    }

    private static final String getFileContents(final String sFile, final boolean bShowError) {
        BufferedReader br = null;
        FileReader fr = null;

        try {
            fr = new FileReader(sFile);
            br = new BufferedReader(fr);
            final StringBuilder sb = new StringBuilder();

            String sLine = null;
            while ((sLine = br.readLine()) != null) {
                sb.append(sLine + "\n");
            }

            return sb.toString();
        } catch (Throwable t) {
            if (bShowError) {
                return "cannot open file: " + t + " (" + t.getMessage() + ")\n";
            }
            return null;
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (Throwable ignore) {
            }

            try {
                if (fr != null) {
                    fr.close();
                }
            } catch (Throwable ignore) {
            }

        }
    }

    private static final String getOutput(String cmd, int iWait, int iDivision) {
        BoundedThread bt = new BoundedThread(cmd);
        bt.start();

        for (int i = 0; i < iWait; i++) {
            if (bt.sOutput == null) {
                try {
                    Thread.sleep(iDivision);
                } catch (InterruptedException ie) {
                    return "interrupted while waiting for " + cmd + " to be executed\n";
                }
            } else {
                return bt.sOutput;
            }
        }

        return bt.killProcess();
    }

    private static class BoundedThread extends Thread {

        String sOutput = null;

        String cmd = null;

        public BoundedThread(String cmd) {
            this.cmd = cmd;
        }

        Process pro = null;

        @Override
        public void run() {
            try {
                pro = MLProcess.exec(new String[] { "/bin/sh", "-c", cmd });
                StringBuilder sb = new StringBuilder();
                String sLine = null;

                pro.getOutputStream().close();

                BufferedReader br = new BufferedReader(new InputStreamReader(pro.getInputStream()));
                while ((sLine = br.readLine()) != null) {
                    sb.append(sLine + "\n");
                }

                br = new BufferedReader(new InputStreamReader(pro.getErrorStream()));
                while ((sLine = br.readLine()) != null) {
                    sb.append("stderr: " + sLine + "\n");
                }

                pro.waitFor();

                sOutput = sb.toString();
            } catch (Throwable t) {
                sOutput = "Cannot execute: " + cmd + ": " + t + " (" + t.getMessage() + ")\n";
            }
        }

        public String killProcess() {
            try {
                pro.destroy();

                return cmd + " had to be stopped\n";
            } catch (Throwable t) {
                return cmd + " timedout but couldn't be stopped because " + t + " (" + t.getMessage() + ")\n";
            }
        }
    }

}
