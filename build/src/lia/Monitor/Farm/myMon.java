/*
 * $Id: myMon.java 6989 2010-11-20 08:21:29Z ramiro $
 */
package lia.Monitor.Farm;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.DataCache.Cache;
import lia.Monitor.DataCache.ProxyWorker;
import lia.Monitor.JiniSerFarmMon.MLLUSHelper;
import lia.Monitor.JiniSerFarmMon.RegFarmMonitor;
import lia.Monitor.modules.MacHostPropertiesMonitor;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.Monitor.monitor.tcpConnWatchdog;
import lia.util.DataArray;
import lia.util.Utils;
import lia.util.logging.relay.MLLogSender;
import lia.util.mail.PMSender;
import lia.util.ntp.NTPDate;
import lia.util.proc.OSProccessStatWrapper;
import lia.util.proc.ProcFSUtil;

/**
 * Provides internal monitoring
 * 
 * @author Iosif Legrand
 * @author ramiro
 */
public class myMon extends cmdExec implements MonitoringModule {

    /**
     * @since ML 1.5.4
     */
    private static final long serialVersionUID = -3406640435991167000L;
    private static final double MEGABYTE_FACTOR = 1024D * 1024D;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(myMon.class.getName());

    public MNode Node;

    public String ClusterName;

    public String FarmName;

    private String mlPidS = null;

    private int mlPidI = -1;

    int mlUserID = -1;

    private Map<Integer, OSProccessStatWrapper> currentProcs = null;

    private Map<Integer, OSProccessStatWrapper> prevProcs = null;

    long usrTimeJiffiesML = 0;

    long sysTimeJiffiesML = 0;

    long cusrTimeJiffiesML = 0;

    long csysTimeJiffiesML = 0;

    long lastSysUptimeJiffies = 0;

    // MFarm parameters
    private long clusNo = 0;// how many clusters

    private long nodeNo = 0;// nodes

    private long paramNo = 0;// parameters

    final static public String ModuleName = "mona";

    private static final String PROC_STAT_JAVA_CMD = "(java)";

    static public String[] ResTypes = {
            "Max Memory", "Memory", "Free Memory", "MLCPUTime", "MLUsrCPUTime", "MLSysCPUTime", "MLThreads", "CMLUsrCPUTime", "CMLSysCPUTime", "CurrentParamNo", "TotalAddedParamsNo", "TotalRemovedParamsNo", "TotalStoreWait", "TotalCollectedValues", "CollectedValuesRate", "TotalLocalProcesses", "UninterruptibleLocalProcesses",
            "RunnableLocalProcesses", "SleepingLocalProcesses", "TracedLocalProcesses", "ZombieLocalProcesses", "UnknownStateLocalProcesses", "PMS_Queue_Size", "PMS_WQueue_Size", "LogS_Queue_Size", "LogS_WQueue_Size", "LogS_TSent"
    };

    boolean isRepetitive = true;

    protected boolean canSuspend = false;

    MonModuleInfo info;

    final Cache cache;

    long last_time_nano = -1;

    long current_time_nano = -1;

    long current_time = -1;

    long last_jobsdone = -1;

    long last_eff_time = -1;

    int last_jobs_eff = -1;

    long last_collectedValuesNo = -1;

    // last elem is for undefined state ... the OS works in mysterious ways ?! - should be 0 all the time
    private static final char[] SHORT_PROCS_STATE = new char[] {
            'D', 'R', 'S', 'T', 'Z', '/'
    };

    private double[] procsAccount = new double[SHORT_PROCS_STATE.length];

    FarmMonitor main;

    public myMon(FarmMonitor main) {
        super(ModuleName);
        info = new MonModuleInfo();
        info.ResTypes = ResTypes;
        isRepetitive = true;
        this.cache = main.cache;
        this.main = main;
    }

    public String[] ResTypes() {
        return ResTypes;
    }

    public MNode getNode() {
        return Node;
    }

    public String getClusterName() {
        return Node.getClusterName();
    }

    public String getFarmName() {
        return Node.getFarmName();
    }

    public String getTaskName() {
        return ModuleName;
    }

    public MonModuleInfo getInfo() {
        return info;
    }

    public boolean isRepetitive() {
        return true;
    }

    // Timer testTimer = new Timer(true);
    public MonModuleInfo init(MNode Node, String param) {
        logger.log(Level.INFO, "Timer Task Scheduled ");
        this.Node = Node;
        return info;
    }

    public String getOsName() {
        return "*";
    }

    private void checkML_PID() {
        String Farm_home = null;

        BufferedReader bf = null;
        FileReader fr = null;

        String cMLPIDs = null;
        try {
            Farm_home = AppConfig.getProperty("lia.Monitor.Farm.HOME", null);
            if (Farm_home == null || Farm_home.length() == 0) {
                logger.log(Level.WARNING, "myMon Could not determine Farm_HOME");
                mlPidS = null;
            } else {
                fr = new FileReader(Farm_home + "/.ml.pid");
                bf = new BufferedReader(fr);
                cMLPIDs = bf.readLine().trim();
            }
        } catch (Throwable tt) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "myMon Could not determine MonALISA PID [ " + Farm_home + "/.ml.pid ] ", tt);
            }
        } finally {
            if (bf != null) {
                try {
                    bf.close();
                } catch (Throwable ignoreError) {
                }
                ;
            }
            if (fr != null) {
                try {
                    fr.close();
                } catch (Throwable ignoreError) {
                }
                ;
            }
        }

        try {
            BufferedReader br = procOutput("id -u");
            if (br != null) {
                mlUserID = Integer.parseInt(br.readLine());
            }
        } catch (Throwable t) {
            mlUserID = -1;
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Could NOT determine MonALISA UID from", t);
            }
        } finally {
            cleanup();
        }

        if (mlPidS == null && cMLPIDs != null && cMLPIDs.length() > 0) {
            mlPidS = cMLPIDs;
            logger.log(Level.INFO, "MonALISA PID = " + mlPidS);
            mlPidI = Integer.parseInt(mlPidS);
        } else {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Could NOT determine MonALISA PID from [ " + Farm_home + "/.ml.pid ] ");
            }
        }
    }

    // private final Result prepareResult() {
    // Result result = null;
    // try {
    // result = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(), ModuleName, ResTypes);
    // result.time = NTPDate.currentTimeMillis();
    //
    // System.arraycopy(INIT_PARAM_FILL, 0, result.param, 0, INIT_PARAM_FILL.length);
    // } catch(Throwable t) {
    // logger.log(Level.WARNING, " [ HANDLED ] Got exc preparing myMon result", t);
    // }
    // return result;
    // }

    private final Result getResult() {
        Result r = new Result();
        r.FarmName = Node.getFarmName();
        r.ClusterName = Node.getClusterName();
        r.NodeName = Node.getName();
        r.Module = ModuleName;
        r.time = current_time;
        return r;
    }

    private final Result fillParamStats() {
        Result result = getResult();
        try {
            updateMFarmMonParams();
            result.addSet("CurrentParamNo", paramNo);
            // Jini Managers restart count
            result.addSet("JMgrRCount", RegFarmMonitor.getRestartMgrCount());
            // Jini Managers verify count
            result.addSet("JMgrVCount", RegFarmMonitor.getVerifyJMgrCount());
            result.addSet("TotalAddedClustersNo", main.getAddedClustersNo());
            result.addSet("TotalAddedNodesNo", main.getAddedNodesNo());
            result.addSet("TotalAddedParamsNo", main.getAddedParamsNo());
            result.addSet("TotalRemovedClustersNo", main.getRemovedClustersNo());
            result.addSet("TotalRemovedNodesNo", main.getRemovedNodesNo());
            result.addSet("TotalRemovedParamsNo", main.getRemovedParamsNo());
            result.addSet("TotalStoreWait", Cache.toStoreResults.size());
            final long c_collectedValuesNo = cache.getTotalParametersCollected();
            result.addSet("TotalCollectedValues", c_collectedValuesNo);
            result.addSet("TotalUnkResModCount", FarmMonitor.getTotalResUnkModCount());
            if (last_time_nano < 0) {
                result.addSet("CollectedValuesRate", 0);
            } else {
                result.addSet("CollectedValuesRate", ((c_collectedValuesNo - last_collectedValuesNo) / (double) TimeUnit.NANOSECONDS.toSeconds(current_time_nano - last_time_nano)));
            }
            last_collectedValuesNo = c_collectedValuesNo;

        } catch (Throwable t) {
            result = null;
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ myMon ] [ fillParamStats ] [ HANDLED ]  Exc ", t);
            }
        }
        return result;
    }

    private boolean isChild(int pid, int ppid, Map<Integer, OSProccessStatWrapper> cProcs) {
        if (pid == ppid)
            return true;

        int tppid = pid;

        while (tppid != 1) {
            final OSProccessStatWrapper ospw = cProcs.get(Integer.valueOf(tppid));
            tppid = ospw.ppid;
            if (tppid == ppid || ospw.tgid == ppid)
                return true;
        }

        return false;
    }

    private TreeSet<Integer> getFinishedProcs(int uid, String command) {
        if (currentProcs == null || prevProcs == null)
            return null;
        TreeSet<Integer> retv = new TreeSet<Integer>();

        for (Map.Entry<Integer, OSProccessStatWrapper> entry : prevProcs.entrySet()) {

            final Integer key = entry.getKey();
            final OSProccessStatWrapper ospw = entry.getValue();
            if (ospw.uid != uid)
                continue;
            if (command != null && ospw.cmd != null && !ospw.cmd.equals(command))
                continue;
            if (currentProcs.containsKey(key))
                continue;

            retv.add(key);
        }
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " Finished procs [ " + retv.size() + " ] ... Set: " + retv.toString());
        }
        return retv;
    }

    private final Result fillMLCPUTimeStats() {
        Result result = getResult();
        long startTime = System.currentTimeMillis();

        try {
            if (mlPidI == -1 || mlUserID < 0) {
                try {
                    checkML_PID();
                } catch (Throwable t) {
                    logger.log(Level.FINER, " [ myMon ] [ fillMLCPUTimeStats2 ] checkML_PID() ", t);
                }
            }

            if (mlPidI == -1 || mlUserID < 0) {
                return null;
            }

            long newUsrTimeJiffiesML = 0;
            long newSysTimeJiffiesML = 0;
            long newcUsrTimeJiffiesML = 0;
            long newcSysTimeJiffiesML = 0;

            if (currentProcs == null)
                return null;

            long mlStartTimeJiffies = 0;
            StringBuilder sb = null;

            if (logger.isLoggable(Level.FINER)) {
                sb = new StringBuilder(2048);
            }

            long prevUsrTimeJiffiesML = 0;
            long prevSysTimeJiffiesML = 0;
            long prevcUsrTimeJiffiesML = 0;
            long prevcSysTimeJiffiesML = 0;

            TreeSet<Integer> set = getFinishedProcs(mlUserID, PROC_STAT_JAVA_CMD);
            boolean hasFinishedProcs = (set != null && set.size() != 0);
            if (hasFinishedProcs) {// try to recalc all the times
                if (logger.isLoggable(Level.FINEST)) {
                    sb.append(" There are some finished procs ").append(set).append(" ... will recalc MLTimes\n");
                }
                for (OSProccessStatWrapper ospw : prevProcs.values()) {
                    if (ospw.uid == mlUserID & ospw.cmd != null && ospw.cmd.equals(PROC_STAT_JAVA_CMD)) {
                        if (set.contains(Integer.valueOf(ospw.pid)))
                            continue;
                        if (isChild(ospw.pid, mlPidI, prevProcs)) {
                            prevUsrTimeJiffiesML += ospw.usrTimeJiffies;
                            prevSysTimeJiffiesML += ospw.sysTimeJiffies;
                            prevcUsrTimeJiffiesML += ospw.cusrTimeJiffies;
                            prevcSysTimeJiffiesML += ospw.csysTimeJiffies;
                        }
                    }
                }
            }// if - recalc

            for (OSProccessStatWrapper ospw : currentProcs.values()) {
                if (logger.isLoggable(Level.FINEST)) {
                    sb.append(" Checking: PID = ").append(ospw.pid).append("\n");
                }

                if (ospw.uid == mlUserID && ospw.cmd != null && ospw.cmd.equals(PROC_STAT_JAVA_CMD)) {
                    if (mlPidI == ospw.pid) {
                        if (logger.isLoggable(Level.FINER)) {
                            sb.append(" Adding MLROOTProc: ").append(ospw).append("\n");
                        }
                        mlStartTimeJiffies = ospw.startTimeJiffies;
                        newUsrTimeJiffiesML += ospw.usrTimeJiffies;
                        newSysTimeJiffiesML += ospw.sysTimeJiffies;
                        newcUsrTimeJiffiesML += ospw.cusrTimeJiffies;
                        newcSysTimeJiffiesML += ospw.csysTimeJiffies;
                    } else if (isChild(ospw.pid, mlPidI, currentProcs)) {
                        if (logger.isLoggable(Level.FINER)) {
                            sb.append(" Adding MLProc: ").append(ospw).append("\n");
                        }
                        newUsrTimeJiffiesML += ospw.usrTimeJiffies;
                        newSysTimeJiffiesML += ospw.sysTimeJiffies;
                        newcUsrTimeJiffiesML += ospw.cusrTimeJiffies;
                        newcSysTimeJiffiesML += ospw.csysTimeJiffies;
                    }
                }

            }// for

            if (logger.isLoggable(Level.FINER)) {
                sb.append("fillMLCPUTimeStats2 parsing [ ").append((System.currentTimeMillis() - startTime)).append(" ]\n");
                logger.log(Level.FINER, sb.toString() + "\n\n");
            }

            long cSysUptimeJiffies = 0;
            double mlUptimeJiffies = -1;

            try {
                cSysUptimeJiffies = ProcFSUtil.getSystemUptime();
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " Got exception in getSystemUptime() ... MLCPUTime cannot be reported", t);
                }
                result = null;
                return result;
            }

            if (lastSysUptimeJiffies == 0L) {
                mlUptimeJiffies = cSysUptimeJiffies - mlStartTimeJiffies;
            } else {
                mlUptimeJiffies = cSysUptimeJiffies - lastSysUptimeJiffies;
            }

            long diffUsrTime = newUsrTimeJiffiesML - (hasFinishedProcs ? prevUsrTimeJiffiesML : usrTimeJiffiesML);
            long diffSysTime = newSysTimeJiffiesML - (hasFinishedProcs ? prevSysTimeJiffiesML : sysTimeJiffiesML);
            long diffTotalMLTime = diffUsrTime + diffSysTime;

            if (diffSysTime >= 0 && diffUsrTime >= 0) {// I know it sounds stupid but there are kernel bugs
                result.addSet("MLCPUTime", (diffTotalMLTime / mlUptimeJiffies) * 100);
            }

            if (diffUsrTime < 0) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " diffUsrTime [ " + diffUsrTime + " ]< 0 jiffies overflow or kernel bug?!?");
                }
            } else {
                result.addSet("MLUsrCPUTime", (diffUsrTime / mlUptimeJiffies) * 100);
            }

            if (diffSysTime < 0) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " diffSysTime [ " + diffSysTime + " ]< 0 jiffies overflow or kernel bug?!?");
                }
            } else {
                result.addSet("MLSysCPUTime", (diffSysTime / mlUptimeJiffies) * 100);
            }

            long diffcUsrTime = newcUsrTimeJiffiesML - (hasFinishedProcs ? prevcUsrTimeJiffiesML : cusrTimeJiffiesML);
            if (diffcUsrTime < 0) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " diffcUsrTime [ " + diffcUsrTime + " ]< 0 jiffies overflow or kernel bug?!?");
                }
            } else {
                result.addSet("CMLUsrCPUTime", (diffcUsrTime / mlUptimeJiffies) * 100);
            }

            long diffcSysTime = newcSysTimeJiffiesML - (hasFinishedProcs ? prevcSysTimeJiffiesML : csysTimeJiffiesML);
            if (diffcSysTime < 0) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " diffcSysTime [ " + diffcUsrTime + " ]< 0 jiffies overflow or kernel bug?!?");
                }
            } else {
                result.addSet("CMLSysCPUTime", (diffcSysTime / mlUptimeJiffies) * 100);
            }

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "\n\n [ " + mlUptimeJiffies + " ] diffTotalMLTime = " + diffTotalMLTime + " diffUsrTime = " + diffUsrTime + " newUsrTimeJiffiesML = " + newUsrTimeJiffiesML + " usrTimeJiffiesML = " + usrTimeJiffiesML + " diffSysTime = " + diffSysTime + " newSysTimeJiffiesML = "
                        + newSysTimeJiffiesML + " sysTimeJiffiesML = " + sysTimeJiffiesML + "\n\n");
            }

            usrTimeJiffiesML = newUsrTimeJiffiesML;
            sysTimeJiffiesML = newSysTimeJiffiesML;
            cusrTimeJiffiesML = newcUsrTimeJiffiesML;
            csysTimeJiffiesML = newcSysTimeJiffiesML;
            lastSysUptimeJiffies = cSysUptimeJiffies;
        } catch (Throwable t) {
            result = null;
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "[ myMon ] [ fillMLCPUTimeStats ] [ HANDLED ] Exception while processing the buffer", t);
            }
        }
        
        try {
            if (result == null) {
                result = getResult();
            }
            
            int totalThreads = -1;
            
            ThreadGroup tg = Thread.currentThread().getThreadGroup();
            while (tg.getParent() != null) {
                tg = tg.getParent();
            }
            totalThreads = tg.activeCount();
            
            result.addSet("MLThreads", totalThreads);

        }catch(Throwable t) {
            if(logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ myMon ] Unable to determine active number of threads ", t);
            }
        }

        return result;
    }

    private final Result fillProcsStats() {
        Result result = getResult();
        int totalProcs = 0;
        try {
            Collection<OSProccessStatWrapper> cProcs = currentProcs.values();
            totalProcs = cProcs.size();
            for (int it = 0; it < procsAccount.length; it++) {
                procsAccount[it] = 0;
            }

            for (OSProccessStatWrapper ospw : cProcs) {
                for (int j = 0; j < SHORT_PROCS_STATE.length; j++) {
                    if (ospw.state == SHORT_PROCS_STATE[j]) {
                        procsAccount[j]++;
                        break;
                    }
                }// for - SHORT_PROCS_STAT
            }// for cProcs

            result.addSet("TotalLocalProcesses", totalProcs);
            result.addSet("UninterruptibleLocalProcesses", procsAccount[0]);
            result.addSet("RunnableLocalProcesses", procsAccount[1]);
            result.addSet("SleepingLocalProcesses", procsAccount[2]);
            result.addSet("TracedLocalProcesses", procsAccount[3]);
            result.addSet("ZombieLocalProcesses", procsAccount[4]);
            result.addSet("UnknownStateLocalProcesses", procsAccount[5]);

        } catch (Throwable t) {
            result = null;
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "[ myMon ] [ fillProcsStats ] [ HANDLED ] Exception while processing the buffer", t);
            }
        }

        return result;
    }

    private final Result fillPMSStats() {
        Result result = getResult();

        try {
            result.addSet("PMS_Queue_Size", PMSender.getInstance().getPMSSize());
            result.addSet("PMS_WQueue_Size", PMSender.getInstance().getPMSWSize());
            result.addSet("LogS_Queue_Size", MLLogSender.getInstance().getMLLogSSize());
            result.addSet("LogS_WQueue_Size", MLLogSender.getInstance().getMLLogSWSize());
            result.addSet("LogS_TSent", MLLogSender.getInstance().getTotalSentMsgs());
        } catch (Throwable t) {
            result = null;
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ myMon ] [ fillPMSStats ] [ HANDLED ]  Exc ", t);
            }
        }

        return result;
    }

    private final Result fillMLLUSHelperStats() {
        Result result = null;
        try {
            final Map<String, Double> m = MLLUSHelper.getInstance().getMonitoringParams();
            if (m == null || m.size() == 0)
                return null;

            result = getResult();

            for (final Map.Entry<String, Double> entry : m.entrySet()) {
                result.addSet(entry.getKey(), entry.getValue());
            }
        } catch (Throwable t) {
            result = null;
            logger.log(Level.INFO, " [ myMon ] [ fillMLLUSHelperStats ] [ HANDLED ]  Exc ", t);
        }

        return result;
    }

    private final Result fillTCWStats() {
        Result result = null;
        try {
            final Map<String, Double> m = tcpConnWatchdog.getMonitoringParams();
            if (m == null || m.size() == 0)
                return null;

            result = getResult();

            for (final Map.Entry<String, Double> entry : m.entrySet()) {
                result.addSet(entry.getKey(), entry.getValue());
            }
        } catch (Throwable t) {
            result = null;
            logger.log(Level.INFO, " [ myMon ] [ fillTCWStats ] [ HANDLED ]  Exc ", t);
        }

        return result;
    }

    private final Result fillPWStats() {
        Result result = null;
        try {
            final Map<String, Double> m = ProxyWorker.getMonitoringParams();
            if (m == null || m.size() == 0)
                return null;

            result = getResult();

            for (final Map.Entry<String, Double> entry : m.entrySet()) {
                result.addSet(entry.getKey(), entry.getValue());
            }
        } catch (Throwable t) {
            result = null;
            logger.log(Level.INFO, " [ myMon ] [ fillPWStats ] [ HANDLED ]  Exc ", t);
        }

        return result;
    }

    private static final boolean IS_MAC;

    static {
        final String sOS = System.getProperty("os.name");

        IS_MAC = sOS != null && sOS.indexOf("Mac") >= 0;
    }

    public Object doProcess() {
        // Result result = prepareResult();
        List<Result> retv = new ArrayList<Result>();
        try {
            current_time = NTPDate.currentTimeMillis();
            current_time_nano = Utils.nanoNow();

            try {
                currentProcs = ProcFSUtil.getCurrentProcsHash();
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " [ myMon ] ProcFSUtil.getCurrentProcsHash exc", t);
                }
            }
            final Result result = getResult();
            if(result != null) {
                result.addSet("Max Memory", Runtime.getRuntime().maxMemory() / MEGABYTE_FACTOR);
                result.addSet("Memory", Runtime.getRuntime().totalMemory() / MEGABYTE_FACTOR);
                result.addSet("Free Memory", Runtime.getRuntime().freeMemory() / MEGABYTE_FACTOR);
                retv.add(result);
            }

            Result r = fillMLCPUTimeStats();
            if (r != null)
                retv.add(r);

            r = fillParamStats();
            if (r != null)
                retv.add(r);

            r = fillProcsStats();
            if (r != null)
                retv.add(r);

            if (currentProcs != null && currentProcs.size() > 0) {
                prevProcs = currentProcs;
            }

            r = fillPMSStats();
            if (r != null)
                retv.add(r);

            r = fillMLLUSHelperStats();
            if (r != null)
                retv.add(r);

            r = fillPWStats();
            if (r != null)
                retv.add(r);

            r = fillTCWStats();
            if (r != null)
                retv.add(r);

            if (IS_MAC) {
                r = fillFromMAC();
                if (r != null)
                    retv.add(r);
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "[ myMon ] [ HANDLED ] got ex main loop ... ", t);
            }
            return null;
        } finally {
            last_time_nano = current_time_nano;
        }

        currentProcs = null;
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ myMon ] Returning ... \n" + retv);
        }
        return retv;
    }

    /**
     * If the underlying operating system is a Mac, use a common method of grabbing the values
     * 
     * @return
     */
    private Result fillFromMAC() {
        final Result r = getResult();

        final DataArray da = MacHostPropertiesMonitor.getData();

        r.addSet("CPU_idle", da.getParam("cpu_idle"));
        r.addSet("CPU_int", 0);
        r.addSet("CPU_iowait", 0);
        r.addSet("CPU_nice", 0);
        r.addSet("CPU_softint", 0);
        r.addSet("CPU_sys", da.getParam("cpu_sys"));
        r.addSet("CPU_usr", da.getParam("cpu_usr"));
        r.addSet("Load5", da.getParam("load1"));
        r.addSet("Load10", da.getParam("load5"));
        r.addSet("Load15", da.getParam("load15"));
        r.addSet("Page_in", da.getParam("blocks_in_R"));
        r.addSet("Page_out", da.getParam("blocks_out_R"));
        r.addSet("Swap_in", da.getParam("swap_in_R"));
        r.addSet("Swap_out", da.getParam("swap_out_R"));
        r.addSet("eth0_IN", da.getParam("eth0_in_R"));
        r.addSet("eth0_OUT", da.getParam("eth0_out_R"));

        return r;
    }

    private void updateMFarmMonParams() {
        clusNo = 0;// how many clusters
        nodeNo = 0;// nodes
        paramNo = 0;// parameters

        if (main == null)
            return;
        final MFarm farm = main.getMFarm();
        if (farm == null)
            return;

        final Vector<MCluster> cList = farm.getClusters();
        if (cList != null) {
            final int cListSize = cList.size();
            clusNo += cListSize;
            for (MCluster clus : cList) {
                if (clus == null)
                    continue;
                final Vector<MNode> vnodes = clus.getNodes();
                if (vnodes != null) {
                    final int nSize = vnodes.size();
                    nodeNo += nSize;
                    for (MNode node : vnodes) {
                        if (node != null && node.getParameterList() != null) {
                            paramNo += node.getParameterList().size();
                        }
                    }
                }
            }
        }
    }

}
