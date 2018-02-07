/*
 * $Id: TaskManager.java 7031 2011-02-03 10:29:01Z ramiro $
 * 
 * Created on Feb 29, Anno Domini
 */
package lia.Monitor.Farm;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.modules.MLModuleInstantiationException;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.dbStore;
import lia.util.DynamicThreadPoll.ResultNotification;
import lia.util.DynamicThreadPoll.SchJobInt;


/**
 * 
 * Keeps track of monitoring modules; it's an old class somehow related with error handling from the initial Thread pool executor
 * Somehow outdated ...
 * 
 * @since the beginning
 * @author Iosif Legrand, ramiro
 */
public class TaskManager implements ResultNotification {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(TaskManager.class.getName());

    /** defulat repeat time for a job */
    private static final long DEFAULT_REPET_TIME = TimeUnit.SECONDS.toMillis(30);

    private final SchJobExecutor ThP;

    final MFarm farm;

    final FarmMonitor main;

    final Map<String, MonModuleInfo> moduleInfo;

    final Map<String, ModuleParams> mpHash;

    private final Map<String, MonitoringModule> activeModules; // boolean debug;

    private volatile URLClassLoader my_loader;

    public TaskManager(FarmMonitor main, MFarm farm, Map<String, MonModuleInfo> moduleInfo, Map<String, ModuleParams> mpHash) throws Exception {
        this.farm = farm;
        this.main = main;
        this.moduleInfo = moduleInfo;
        this.mpHash = mpHash;

        // all accesses are synchornized
        activeModules = new HashMap<String, MonitoringModule>();

        URLClassLoader tLoader = null;
        try {
            URL[] list = getExternalURLs();
            if (list != null) {
                tLoader = new URLClassLoader(list, Class.forName(getClass().getName()).getClassLoader());
            }
        } catch (Throwable e2) {
            logger.log(Level.SEVERE, "\n\n FAILED to initialize ClassLoader", e2);
        }

        my_loader = tLoader;
        ThP = new SchJobExecutor(2, 30, this);

    }

    public void notifyResult(final SchJobInt j, Object result, Throwable ex) {

        if (ex == null) {

            if (result != null) { // buffer the Results
                main.addResult(result);
            }

//            if (j.get_repet_time() > 0) { // re-schedule the job
//
//                if (j instanceof MonitoringModule) {
//                    final MonitoringModule job = (MonitoringModule) j;
//                    job.getInfo().setErrorCount(0);
//                    job.getNode().error_count = 0;
//                }
//
//                j.set_exec_time(System.currentTimeMillis() + j.get_repet_time());
//
//                ThP.addJob(j);
//            }
//
//            if (j.getExeTime() > 0) {
//                total_eff_time += j.getExeTime();
//                total_jobs_eff_time++;
//            }

            return;
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " TaskManager got exc from module", ex);
        }

//        if (j instanceof MonitoringModule) {
//            final MonitoringModule job = (MonitoringModule) j;
//            MNode node = job.getNode();
//            if (node == null) {
//                return;
//            }
//            node.error_count++;
//
//            MonModuleInfo info = job.getInfo();
//            info.addErrorCount();
//            info.setErrorDesc(ex.toString());
//
//            String tname = job.getTaskName();
//
//            if (logger.isLoggable(Level.FINER)) {
//                logger.log(Level.FINER, "ERROR Monitoring Node " + node + " task =" + tname + "  ErrCount =" + info.error_count, ex);
//            }
//
//            if (info.getErrorCount() >= MAX_MONITORING_ERRORS_COUNT) {
//                if (job.canSuspend()) {
//                    logger.log(Level.WARNING, "SUSPEND  module =" + job.getTaskName() + " at node " + node + " for " + (DEFAULT_MODULE_ERROR_TIMEOUT / 1000 / 60) + " min ");
//                    job.set_exec_time(System.currentTimeMillis() + DEFAULT_MODULE_ERROR_TIMEOUT);
//                    ThP.addJob(job);
//                    return;
//                }
//                if (logger.isLoggable(Level.FINER)) {
//                    logger.log(Level.FINER, "Module =" + job.getTaskName() + " at node " + node + " MAX_MONITORING_ERRORS_COUNT [ " + MAX_MONITORING_ERRORS_COUNT + " ] reached, but job cannot be suspended. Setting error count to 0!");
//                    job.getNode().error_count = 0;
//                }
//            }
//
//            if (job.get_repet_time() > 0) {
//                job.set_exec_time(System.currentTimeMillis() + job.get_repet_time());
//                ThP.addJob(job);
//                return;
//            }
//
//            return;
//        }

//        logger.log(Level.SEVERE, "\n\n Not a MonitoringModule Job ???? Job class = " + j.getClass(), ex);

    }

    public void startMonitoring() {
        ThP.startMonitoring();
    }

    synchronized MonModuleInfo createExModule(String module, MNode mn, String arg, long reptime) {
        MonitoringModule job = null;
        long sch_time;

        try {
            Class<MonitoringModule> cjob = (Class<MonitoringModule>) Class.forName("lia.Monitor.modules." + module);
            job = cjob.newInstance();
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Cannot instantiate lia.Monitor.modules." + module, t);
            }
        }

        if (job == null) {
            // try external class loader
            try {
                job = (MonitoringModule) (my_loader.loadClass(module).newInstance());
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "Cannot instantiate " + module + " from external URL", t);
                }
            }
        }

        if (job == null) {
            logger.log(Level.INFO, "Failed to load class  ! " + module);
            return null;
        }

        MonModuleInfo info = job.init(mn, arg);

        long repeat = 30000;
        if (reptime > 0) {
            repeat = reptime;
        }

        // TRACEPATH - HACK
        if (module != null && module.equals("monTracepath")) {

            boolean limitTracepathRate = true;

            try {
                limitTracepathRate = Boolean.valueOf(AppConfig.getProperty("lia.Monitor.Farm.TaskManager.LIMIT_TRACEPATH_RATE", "true")).booleanValue();
            } catch (Throwable ignore) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " [ TaskManager ] -  LIMIT_TRACEPATH_RATE Exc: ", ignore);
                }
                limitTracepathRate = true;
            }

            long tracepathRate = 120 * 1000;

            try {
                long ltr = Long.valueOf(AppConfig.getProperty("lia.Monitor.Farm.TaskManager.TRACEPATH_RATE", "120")).longValue();
                tracepathRate = ltr * 1000;
            } catch (Throwable ignore) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " [ TaskManager ] -  LIMIT_TRACEPATH_RATE Exc: ", ignore);
                }
                tracepathRate = 120 * 1000;
            }

            if (limitTracepathRate) {
                if (repeat < tracepathRate) {
                    repeat = tracepathRate;
                }
                logger.log(Level.INFO, " [ TaskManager ] Limit tracepath rate @ [ " + repeat + " ] ms");
            }
        }

        long now = System.currentTimeMillis();
        double jit = repeat * Math.random();

        sch_time = now + (long) jit;

        if (job.isRepetitive()) {
            job.set_exec_time(sch_time);
            job.set_repet_time(repeat);
            job.set_max_time(repeat);
        } else {
            job.set_exec_time(sch_time);
            job.set_repet_time(0);
            job.set_max_time(DEFAULT_REPET_TIME);
        }

        ThP.addJob(job);
        activeModules.put("XXXX" + module, job);

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, " Added EXTERNAL module " + module + " arg:" + arg + "reptime: " + repeat);
        }

        if (job instanceof dbStore) {
            logger.log(Level.INFO, "Adding a dbStore from a module: " + module);
            main.addOtherDBStores((dbStore) job);
        }

        return info;

    }

    synchronized MonModuleInfo createModule(String module, MNode node) {
        return createModule(module, node, -1);
    }

    synchronized MonModuleInfo createModule(String module, MNode node, long repTime) {
        MonitoringModule job = null;

        try {
            Class<MonitoringModule> cjob = (Class<MonitoringModule>) Class.forName("lia.Monitor.modules." + module);
            job = cjob.newInstance();
        } catch (ClassNotFoundException cne) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ TaskManager ] Unable to load module: " + module + ". Not found in the standard jar files. Will try the external path.", cne);
            } else {
                logger.log(Level.INFO, " [ TaskManager ] Unable to load module: " + module + ". Not found in the standard jar files. Will try the external path.");
            }
        } catch (MLModuleInstantiationException mlmie) {
            logger.log(Level.INFO, " [ TaskManager ] The module " + module + " failed to initialize from MonALISA jar files. Cause: ", mlmie);
            node.removeModule(module);
            return null;
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ TaskManager ] The module " + module + " failed to initialize from MonALISA jar files. Cause: ", t);
        }

        if (job == null) {
            try {
                if (module.indexOf("!") > 0) {
                    // first remove the old naming
                    URL[] oldurl = my_loader != null ? my_loader.getURLs() : new URL[0];
                    URL newurl = new URL(module.substring(0, module.indexOf("!")));
                    module = module.substring(module.lastIndexOf("!") + 1);

                    boolean bExists = false;

                    logger.log(Level.INFO, "new url : " + newurl.toString());
                    for (int i = 0; i < oldurl.length; i++) {
                        logger.log(Level.INFO, "Old URL : " + oldurl[i].toString());
                        if (oldurl[i].equals(newurl)) {
                            if (logger.isLoggable(Level.FINE)) {
                                logger.log(Level.FINE, "already exists, skipping");
                            }
                            bExists = true;
                            break;
                        }
                    }

                    if (!bExists) {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "adding the new url");
                        }
                        URL[] newurls = new URL[oldurl.length + 1];

                        for (int j = 0; j < oldurl.length; j++) {
                            newurls[j] = oldurl[j];
                        }

                        newurls[newurls.length - 1] = newurl;

                        my_loader = new URLClassLoader(newurls, Class.forName("lia.Monitor.Farm.TaskManager").getClassLoader());
                    }
                }

                job = (MonitoringModule) (my_loader.loadClass(module).newInstance());
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ TaskManager ]  Failed to load module " + module + " from external path also. Cause: ", t);
            }
        }

        if (job == null) {
            logger.log(Level.WARNING, " Failed to load class  ! " + module);
            node.removeModule(module);
            return null;
        }

        StringTokenizer st = new StringTokenizer(module, ".\\/");

        while (st.hasMoreTokens()) {
            module = st.nextToken();
        }

        String key = node.getKey(module);
        String param = null; // to be changed
        long repeat = 30000; //
        boolean byRequest = true;

        if ((key != null) && (mpHash.containsKey(key))) {
            ModuleParams minfo = mpHash.remove(node.getKey(module));
            param = minfo.param;
            repeat = minfo.repeat;
        }

        MonModuleInfo info = job.init(node, param);
        info.name = module;

        if (job.isRepetitive()) {
            job.set_repet_time((repTime < 0) ? repeat : repTime);
            job.set_max_time(repeat);
        } else {
            job.set_repet_time(0);
            job.set_max_time(DEFAULT_REPET_TIME);
        }

        if (byRequest) {
            node.addModule(module);
        }
        node.addParameters(job.ResTypes());
        ThP.addJob(job);
        activeModules.put(node.getKey(module), job);
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " Added module " + module + " for Node [ " + node + ", " + node.getIPaddress() + " ]");
        }
        moduleInfo.put(node.getKey(module), info);
        if (job instanceof dbStore) {
            logger.log(Level.INFO, "Adding a dbStore from a module: " + module);
            main.addOtherDBStores((dbStore) job);
        }
        return info;

    }

    synchronized void deleteModule(String module, MNode node) {
        String key = node.getKey(module);
        if (activeModules.containsKey(key)) {
            MonitoringModule job = activeModules.remove(key);
            MonModuleInfo info = moduleInfo.remove(key);
            job.stop();
            job.set_repet_time(-1);
            main.ed.removeModule(node, module, info.ResTypes);
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " removed module " + module + "  for " + node);
            }
        } else {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " Failed to remove MOdule " + module + " from node" + node);
            }
        }
    }

    public String getIPaddress(MNode n, boolean checkNodeIPAddress) {

        String ad = null;
        try {
            ad = (InetAddress.getByName(n.getName())).getHostAddress();
            n.ipAddress = ad;
        } catch (Throwable t) {
            if (checkNodeIPAddress) {
                logger.log(Level.WARNING, " Can not get ip for " + n, t);
            } else {
                logger.log(Level.FINE, " Can not get ip for " + n, t);
            }

        }

        return ad;
    }

    public synchronized void task_init() {

        final boolean checkNodeIPAddress = AppConfig.getb("lia.Monitor.Farm.TaskManager.checkNodeIPAddress", true);

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, " [ TaskManager ] [ task_init ] lia.Monitor.Farm.TaskManager.checkNodeIPAddress = " + checkNodeIPAddress);
        }

        Vector<MCluster> clus = farm.getClusters();
        for (final MCluster cl : clus) {
            if (cl.externalModule != null) {
                MNode mn = new MNode(cl.externalNode, null, cl, farm);

                String nodeIP = getIPaddress(mn, checkNodeIPAddress);
                if (checkNodeIPAddress && nodeIP == null) {
                    continue;
                }

                int indTime = cl.externalParam.indexOf("%^&");
                long reptime = 0;
                if (indTime != -1) {
                    String sTime = cl.externalParam.substring(indTime + 3);
                    try {
                        reptime = Long.parseLong(sTime) * 1000;
                    } catch (Throwable t) {
                    }
                    cl.externalParam = cl.externalParam.substring(0, indTime);
                }
                createExModule(cl.externalModule, mn, cl.externalParam, reptime);
            }
        }

        Vector<MNode> nodes = farm.getNodes();
        StringBuilder sb = new StringBuilder();

        for (final MNode n : nodes) {

            String nodeIP = getIPaddress(n, checkNodeIPAddress);
            if (checkNodeIPAddress && nodeIP == null) {
                logger.log(Level.WARNING, "Can not find the IP address for Node =" + n + " REMOVED it! ");
                main.ed.removeNode(n.getClusterName(), n);
                continue;
            }

            Vector<String> modules = (Vector<String>) n.getModuleList().clone();
            for (final String mod : modules) {
                MonModuleInfo infoc = createModule(mod, n);
                if (infoc == null) {
                    main.ed.removeModule(n, mod, null);
                    sb.append("\nErrors adding Module " + mod + " for Node [ " + n + "/" + n.getIPaddress() + " ] ");
                } else {
                    sb.append("\nModule " + mod + " ADDED for Node " + n + "/" + n.getIPaddress() + " ] ");
                }
            }
        }

        // external modules for a farm
        if (main != null && main.externalModules != null && main.externalModules.length > 0) {
            for (int iem = 0; iem < main.externalModules.length; iem++) {
                try {
                    MNode mn = new MNode();
                    mn.farm = farm;
                    createExModule(main.externalModules[iem], mn, main.externalModParams[iem], Long.valueOf(main.externalModRTime[iem]).longValue());
                    logger.log(Level.INFO, "Created external param for a Farm [ " + main.externalModules[iem] + ", " + main.externalModParams[iem] + ", " + main.externalModRTime[iem] + " ]");
                } catch (Throwable tt) {
                    logger.log(Level.WARNING, "Cannot create external param for a Farm [ " + main.externalModules[iem] + ", " + main.externalModParams[iem] + ", " + main.externalModRTime[iem] + " ]", tt);
                }
            }
        }

        logger.log(Level.CONFIG, sb.toString());

        // self monitoring part !
        new MCluster("MonaLisa", farm);
        try {
            main.ConfigAdd("MonaLisa", "localhost", null, 0);
        } catch (Exception e) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "General Exception in ConfigAdd", e);
            }
        }
        MNode me = (farm.getCluster("MonaLisa")).getNode("localhost");
        String nodeIP = getIPaddress(me, true);
        if (nodeIP == null) {
            logger.log(Level.SEVERE, " localhost does not have IP ??!?!");
        }
        myMon self = new myMon(main);
        self.init(me, null);
        me.addModule("mona");
        me.addParameters(self.ResTypes());

        long now = System.currentTimeMillis();
        self.set_exec_time(now + 1115);
        self.set_repet_time(60 * 1000);
        self.set_max_time(DEFAULT_REPET_TIME);
        ThP.addJob(self);

        new MCluster("MonaLisa_LocalSysMon", farm);
        try {
            main.ConfigAdd("MonaLisa_LocalSysMon", "localhost", null, 0);
        } catch (Exception e) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "General Exception in ConfigAdd", e);
            }
        }
        MNode meMon = (farm.getCluster("MonaLisa_LocalSysMon")).getNode("localhost");
        String nodeIP2 = getIPaddress(meMon, true);
        if (nodeIP2 == null) {
            logger.log(Level.SEVERE, " localhost does not have IP ??!?!");
        }

        if(AppConfig.getb("lia.util.threads.exportmon", true)) {
            MCluster cl = new MCluster("MonaLisa_ThPStat", farm);
            MNode mn = new MNode(cl.externalNode, null, cl, farm);
            createExModule("monThPStat", mn, null, 60 * 1000);
        }
        
        if (Boolean.valueOf(AppConfig.getProperty("lia.monitor.Farm.use_SNMP", "false")).booleanValue()) {
            createModule("snmp_Load", me);
            createModule("snmp_IO", me);
            createModule("snmp_CPU", me);
        } else {
            createModule("monProcLoad", me, 60 * 1000);
            createModule("monProcIO", me, 60 * 1000);
            createModule("monProcStat", me, 60 * 1000);
            createModule("monDiskIOStat", meMon, 60 * 1000);
        }

        createModule("monMLStat", me, 60 * 1000);
    }

    public synchronized void changeRepTime(MNode node, String module, long time) {
        MonitoringModule job = activeModules.get(node.getKey(module));
        if (job != null) {
            job.set_repet_time(time);
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Changing repetition time for " + node.toString() + " " + module);
            }
        } else {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " Can not find node / module tochange repetition time ");
            }
        }
    }

    private static URL[] getExternalURLs() {
        try {
            List<URL> _returnURLs = new LinkedList<URL>();
            String[] strURL = AppConfig.getVectorProperty("lia.Monitor.CLASSURLs");

            if (strURL != null && strURL.length > 0) {
                for (final String possibleURL : strURL) {
                    try {
                        _returnURLs.add(new URL(possibleURL));
                        logger.log(Level.INFO, "[ lia.Monitor.CLASSURLs ] external URL: " + possibleURL + " added to URLs");
                    } catch (MalformedURLException ex) {
                        logger.log(Level.WARNING, " [ lia.Monitor.CLASSURLs ] GOT A BAD URL" + possibleURL + " ...SKIPPING IT!!", ex);
                    } catch (Throwable t) {
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, "GOT a general Exception", t);
                        }
                    }
                }
            }
            return _returnURLs.toArray(new URL[_returnURLs.size()]);
        } catch (Throwable t) {/* this should not happened */
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "GOT a general Exception", t);
            }
        }
        return null;
    }
}
