/**
 * Module used to collect VO accounting statistics. It obtains
 * information from Condor (condor_q and the Condor history file), PBS (qstat
 * and PBS accounting logs), SGE and LSF.
 * The location of the job managers must be set in environment variables like
 * CONDOR_LOCATION, PBS_LOCATION etc. See the online user guide for more
 * documentation.
 * 
 * @author Corina Stratan
 */
package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.logging.MLLogEvent;
import lia.util.ntp.NTPDate;

/**
 * Class that stores statistical information about the jobs belonging to a VO.
 * The parameters ending with "_t" store total (cumulated) values since the
 * service started; the others store only the value for the last time interval.
 */
class VOSummaryExt implements Cloneable {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(VOSummaryExt.class.getName());

    /* parameters obtained from a queue manager: */
    long run_time_t; // total wall clock time for the jobs, in seconds

    long cpu_time_t; // total CPU time, in seconds

    double size; // total size of the jobs, in MB

    double disk_usage; // disk usage in MB

    long runningjobs; // total number of currently running jobs

    long idlejobs;

    long heldjobs;

    long unknownjobs;

    long submittedJobs;

    long finishedJobs;

    long finishedJobs_t;

    long submittedJobs_t;

    long successJobs;

    long errorJobs;

    long successJobs_t; // total number of jobs finished successfully

    long errorJobs_t; // total number of jobs finished with errors

    /* parameters obtained from the Condor history file: */
    Hashtable<String, Double> paramsCondorH;

    /* parameters obtained from LSF (bjobs command) */
    Hashtable<String, Double> paramsLSF;

    public VOSummaryExt() {
        run_time_t = 0;
        cpu_time_t = 0;
        size = 0.0;
        disk_usage = 0.0;

        runningjobs = 0;
        idlejobs = 0;
        heldjobs = 0;
        unknownjobs = 0;

        submittedJobs = 0;
        finishedJobs = 0;
        finishedJobs_t = 0;
        submittedJobs_t = 0;
        successJobs = 0;
        errorJobs = 0;
        successJobs_t = 0;
        errorJobs_t = 0;

        paramsCondorH = new Hashtable<String, Double>();
        paramsCondorH.put("CondorFinishedJobs_t", Double.valueOf(0));
        paramsCondorH.put("CondorRunTime_t", Double.valueOf(0));
        paramsCondorH.put("CondorCpuUsr_t", Double.valueOf(0));
        paramsCondorH.put("CondorCpuSys_t", Double.valueOf(0));
        paramsCondorH.put("CondorBytesSent_t", Double.valueOf(0));
        paramsCondorH.put("CondorBytesRecvd_t", Double.valueOf(0));
        paramsCondorH.put("CondorFileReadBytes_t", Double.valueOf(0));
        paramsCondorH.put("CondorFileWriteBytes_t", Double.valueOf(0));
        paramsCondorH.put("CondorMemoryUsage", Double.valueOf(0));
        paramsCondorH.put("CondorDiskUsage", Double.valueOf(0));

        paramsLSF = new Hashtable<String, Double>();
        paramsLSF.put("MemoryUsageLSF", Double.valueOf(0));
        paramsLSF.put("SwapUsageLSF", Double.valueOf(0));
        paramsLSF.put("CPUTimeLSF", Double.valueOf(0));
        paramsLSF.put("RunTimeLSF", Double.valueOf(0));
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(1024);
        sb.append("run_time_t=").append(run_time_t);
        sb.append("cpu_time_t=").append(cpu_time_t);
        sb.append("\tsize=").append(size);
        sb.append("\tdiskUsage=").append(disk_usage);
        sb.append("\trunningjobs=").append(runningjobs);
        sb.append("\tidlejobs=").append(idlejobs);
        sb.append("\theldjobs=").append(heldjobs);
        sb.append("\tunknownjobs=").append(unknownjobs);

        sb.append("\tsubmittedJobs=").append(submittedJobs);
        sb.append("\tfinishedJobs=").append(finishedJobs);
        sb.append("\tfinishedJobs_t=").append(finishedJobs_t);
        sb.append("\tsubmittedJobs_t=").append(submittedJobs_t);
        sb.append("\tsuccessJobs=").append(successJobs);
        sb.append("\terrorJobs=").append(errorJobs);
        sb.append("\tsuccessJobs_t=").append(successJobs_t);
        sb.append("\terrorJobs_t=").append(errorJobs_t);

        Enumeration<String> condorE = paramsCondorH.keys();
        while (condorE.hasMoreElements()) {
            String paramName = condorE.nextElement();
            Double paramValue = paramsCondorH.get(paramName);
            sb.append("\t" + paramName + "=").append(paramValue);
        }

        Enumeration<String> lsfE = paramsLSF.keys();
        while (lsfE.hasMoreElements()) {
            String paramName = condorE.nextElement();
            Double paramValue = paramsLSF.get(paramName);
            sb.append("\t" + paramName + "=").append(paramValue);
        }

        return sb.toString();
    }

    @Override
    public Object clone() {
        VOSummaryExt newInstance = null;
        try {
            newInstance = (VOSummaryExt) super.clone();
        } catch (CloneNotSupportedException e) {
            logger.log(Level.WARNING, "[monOsgVoJobs] Got exception:", e);
        }

        /* copy the two hashtables to the new instance */
        newInstance.paramsCondorH = new Hashtable<String, Double>();
        Enumeration<String> condorE = paramsCondorH.keys();
        while (condorE.hasMoreElements()) {
            String key = condorE.nextElement();
            Double val = paramsCondorH.get(key);
            newInstance.paramsCondorH.put(new String(key), Double.valueOf(val.doubleValue()));
        }

        newInstance.paramsLSF = new Hashtable<String, Double>();
        Enumeration<String> lsfe = paramsLSF.keys();
        while (lsfe.hasMoreElements()) {
            String key = lsfe.nextElement();
            Double val = paramsLSF.get(key);
            newInstance.paramsLSF.put(new String(key), Double.valueOf(val.doubleValue()));
        }
        return newInstance;
    }
}

/**
 * Accounting information about a job.
 */
class JobInfoExt {

    // constants for job error statuses
    public static final int JOB_OK = 0;

    public static final int JOB_SUSPENDED = 1;

    public static final int JOB_MODIFIED = 2;

    public String jobManager; // the name of the job manager (CONDOR, PBS etc.)

    public String id; // String of the following form: <job_manager>_<job_id>

    public String user;

    public String date; // the date when the job was submitted

    public String time; // the time when the job was submitted

    public long run_time; // wall clock time in seconds

    public long cpu_time; // CPU time in seconds

    public String status; // "R", "I", "F", "H", "U"

    public String priority;

    public double size; // the total virtual size of the job in MB

    public double disk_usage; // disk usage in MB

    public String VO; // the VO that owns this job

    public String serverName; // the host on which the job was submitted

    public int exit_status; // the exit status of the job

    // old value for CPU time (this is set when the job is suspended in Condor
    // and
    // condor_q reports cpu_time 0)
    public long cpu_time_old;

    public long run_time_old;

    // shows whether decreasing CPU time was reported for this job
    public int error_status_cpu;

    // shows whether decreasing run time was reported for this job
    public int error_status_run;

    public JobInfoExt() {
        jobManager = id = VO = null;
        user = date = time = null;
        status = priority = null;
        run_time = cpu_time = 0;
        size = disk_usage = 0.0;
        serverName = null;
        exit_status = 0;
        status = "U";

        cpu_time_old = run_time_old = 0;
        error_status_cpu = error_status_run = 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("jobManager=").append(jobManager);
        sb.append("\tid=").append(id);
        sb.append("\tuser=").append(user);
        sb.append("\tdate=").append(date);
        sb.append("\trun_time=").append(run_time);
        sb.append("\tcpu_time=").append(cpu_time);
        sb.append("\tstatus=").append(status);
        sb.append("\tpriority=").append(priority);
        sb.append("\tsize=").append(size);
        sb.append("\tdisk_usage=").append(disk_usage);
        sb.append("\tVO=").append(VO);
        if (serverName != null) {
            sb.append("\tserverName").append(serverName);
        }
        return sb.toString();
    }
}

/**
 * Module that collects accounting information (for the moment, from Condor,
 * PBS, LSF and SGE).
 */
public class monOsgVoJobs extends monVoModules implements MonitoringModule {

    static final long serialVersionUID = 607200518051980L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monOsgVoJobs.class.getName());

    protected String monalisaHome = null;

    /** The name of the farm */
    protected String farmName = null;

    /**
     * This should be set to false if the module is run from usr_code, so that
     * status emails will not be sent.
     */
    private static boolean isInternalModule = true;

    private static final long SEC_MILLIS = 1000;

    private static final long MIN_SEC = 60;

    /* strings used in setting values for the module arguments */
    public static final int ON = 1;

    public static final int OFF = 0;

    public static final int ALTERNATIVE = 2;

    private static final long CMD_DELAY = 90 * 1000;

    /**
     * This name will be used for the user accounts that do not belong to any
     * VO.
     */
    protected static final String noVoName = "NO_VO";

    static public final String VoModulesDir = "VoModules-v0.38";

    protected static String OsName = "linux";

    protected String ModuleName = null;

    protected String[] ResTypes = null;

    protected static String MyModuleName = "monOsgVoJobs";

    // Ramiro
    // protected static final String VoModulesDir = "VoModules-v0.9";
    boolean testmode = false;

    private boolean firstInit = true;

    private long lastRun = 0;

    /**
     * Contains the names of the available job managers as keys and their
     * locations as values.
     */
    protected HashMap<String, String> jobMgr = new HashMap<String, String>();

    /**
     * Contains the names of the available job managers as keys and the commands
     * that will be executed to find their versions as values.
     */
    protected HashMap<String, String> versionCmd = new HashMap<String, String>();

    /**
     * Contains the names of the available job managers as keys and their
     * versions as values.
     */
    protected HashMap<String, String> jobMgrVersions = new HashMap<String, String>();

    /**
     * Flag that specifies whether the module should check the exit status of
     * the commands that are executed.
     */
    protected boolean checkExitStatus = true;

    /** The location of the Condor history file. */
    protected volatile Vector<String> condorHistFiles = null;

    /**
     * This flag disables the use of the "plain" condor_q command in addition to
     * condor_q -l
     */
    protected volatile boolean condorQuickMode = false;

    /** This flag enables the parsing of the Condor history file. */
    protected volatile boolean checkCondorHist = true;

    /**
     * This parameter specifies when the condor_q command should be used with
     * the "-format" options. By default it is only an alternative solution if
     * the regular command doesn't work.
     */
    protected int condorFormatOption = ALTERNATIVE;

    protected VO_CondorAccounting condorAcc = null;

    /** This flag enables the parsing of the PBS accounting logs. */
    protected boolean checkPBSHist = true;

    /** The default location of the PSB accounting log directory. */
    protected String defaultPBSLogDir = "/usr/spool/PBS/server_priv/accounting";

    /** The location of the PSB accounting log directory. */
    protected String pbsLogDir = null;

    /** This flag disables the execution of the additional command qstat -a */
    protected boolean pbsQuickMode = true;

    protected VO_PBSAccounting pbsAcc = null;

    /**
     * This flag specifies whether the LSF bjobs command should be run with the
     * -u argument.
     */
    protected boolean lsfUserOpt = true;

    /**
     * The user for which we run the LSF bjobs command - by default is for all
     * users.
     */
    protected String lsfUserName = "all";

    /**
     * Specifies whether we should send some extra Results when a job is
     * finished, containing the average CPU time consumed.
     */
    protected boolean finishedJobsResults = true;

    /**
     * This flag enables the statistics for individual users besides the VO
     * statistics.
     */
    protected boolean individualUsersResults = false;

    /** The results that this module provides. */
    protected static String[] MyResTypes = { "RunningJobs", "IdleJobs", "HeldJobs", "SubmittedJobs", "RunTime",
            "WallClockTime", "DiskUsage", "JobsSize" };

    /* properties that should be set in ml.properties for email configuration */
    protected static String emailNotifyProperty = "lia.Monitor.modules.monOsgVoJobs.emailNotify";

    protected static String statusEmailNotifyProperty = "lia.Monitor.modules.monOsgVoJobs.statusEmailNotify";

    protected static String maxResultsEmailedProperty = "lia.Monitor.modules.monOsgVoJobs.maxResultsEmailed";

    /** The number of Condor commands that will be issued. */
    protected volatile int nCondorCommands = 0;

    /** The module's error code. It is 0 when the execution was successful. */
    protected int errorCode = 0;

    /**
     * Table that stores the execution times (in seconds) for the job manager
     * commands.
     */
    protected Hashtable<String, Long> execTimes = new Hashtable<String, Long>();

    /** The number of times doProcess was executed, modulo 2000000. */
    protected int execCnt = 0;

    /** The time when the previous doProcess was executed */
    protected long prevDoProcessTime = 0;

    /** The time when the current doProcess was executed */
    protected long crtDoProcessTime = 0;

    /** Indicates whether there was a command executed successfully. */
    protected boolean haveSuccessfulCommand = false;

    /** Indicates whether condor_q (without -format) has failed. */
    protected boolean failedCondorQ = false;

    /* ************ information from the previous doProcess(): ******* */
    /*
     * information for each job (the keys are the job IDs, the elements are
     * JobInfoExt objects)
     */
    private Hashtable<String, JobInfoExt> jobsInfo = new Hashtable<String, JobInfoExt>();

    /*
     * information for the finished jobs that are still reported by the job
     * managers (keys - job IDs, elements - JobInfoExt)
     */
    private Hashtable<String, JobInfoExt> finishedJobsInfo = new Hashtable<String, JobInfoExt>();

    /*
     * summary information for each VO (the keys are VO names, the elements are
     * VoSummaryExt objects)
     */
    private Hashtable<String, VOSummaryExt> VOjobsInfo = new Hashtable<String, VOSummaryExt>();

    /*
     * summary information for each user (the keys are user names, the elements
     * are VoSummaryExt objects)
     */
    private Hashtable<String, VOSummaryExt> userJobsInfo = new Hashtable<String, VOSummaryExt>();

    /* total for all the VOs */
    VOSummaryExt oTotalVOS = new VOSummaryExt();

    /* used to collect information about the jobs with decreasing CPU/run time */
    volatile MLLogEvent<String, Object> mlleInconsist;

    /* **** information from the current doProcess(): ****** */
    /*
     * information for each job (the keys are the job IDs, the elements are
     * JobInfoExt objects)
     */
    private Hashtable<String, JobInfoExt> currentJobsInfo = new Hashtable<String, JobInfoExt>();

    /*
     * information for the finished jobs that are still reported by the job
     * managers (keys - job IDs, elements - JobInfoExt)
     */
    private Hashtable<String, JobInfoExt> currentFinishedJobsInfo = new Hashtable<String, JobInfoExt>();

    /* summary information for each VO */
    private Hashtable<String, VOSummaryExt> currentVOjobsInfo = new Hashtable<String, VOSummaryExt>();

    /* summary information for each user */
    private Hashtable<String, VOSummaryExt> currentUserJobsInfo = new Hashtable<String, VOSummaryExt>();

    /* total for all the VOs */
    VOSummaryExt cTotalVOS = new VOSummaryExt();

    private volatile boolean shouldPublishJobInfo = true;

    /* Specifies whether all Condor the schedd daemons will be queried. */
    protected volatile boolean condorUseGlobal = false;

    /*
     * The names of the Condor schedd daemos that we will query, indexed by the
     * numbers assigned by the user in the conf file.
     */
    protected Hashtable<Integer, String> condorServers = new Hashtable<Integer, String>();

    /*
     * The names of the pools corresponding to the Condor servers, indexed by
     * the numbers assigned by the user in the conf file.
     */
    protected Hashtable<Integer, String> condorPools = new Hashtable<Integer, String>();

    /* The number of Condor servers used. */
    int nCondorServers = 0;

    /*
     * Constraints that will be imposed to the condor_q command with the
     * -constraints option.
     */
    protected String condorConstraints = null;

    /* The Condor local directory. */
    protected String condorLocalDir = null;

    /*
     * Flag that shows whether the Condor history files were found.
     */
    protected volatile boolean haveCondorHistFiles = false;

    /* The directory where Condor is installed. */
    protected String condorLocation = null;

    /* The VOs that are currently active (are running jobs). */
    protected volatile LinkedList<String> activeVos = new LinkedList<String>();

    /* The VOs active at the previous doProcess() */
    protected volatile LinkedList<String> oldActiveVos = new LinkedList<String>();

    /* The users that are currently active (are running jobs). */
    protected volatile LinkedList<String> activeUsers = new LinkedList<String>();

    /* The users active at the previous doProcess() */
    protected volatile LinkedList<String> oldActiveUsers = new LinkedList<String>();

    /* This is set to true if we find decreasing CPU time or run time for a job. */
    protected volatile boolean inconsistentJobInfo = false;

    /*
     * If the flag is true and a job has decreasing cpu time / run time, the
     * module will add the new values to the old ones.
     */
    protected volatile boolean adjustJobStatistics = false;

    /*
     * If this flag is true, the names of VOs will be sent wiith mixed cases in
     * the result.
     */
    protected volatile boolean mixedCaseVOs = false;

    /*
     * If this flag is true, the jobs of the users that do not belong to any VO
     * will be published under the name "NO_VO".
     */
    protected volatile boolean noVoStatistics = false;

    /*
     * Counts the number of errors in executing the commands that appeared so
     * far.
     */
    // protected int cmdErrCnt = 0;

    /*
     * The exception mails and warning messages in the log are generated only a
     * few times in ERR_CNT_PERIOD errors.
     */
    // protected static final int ERR_CNT_PERIOD = 20;

    /*
     * Results previously sent when some jobs finished. Used to delete the
     * parameters after a few doProcess-es.
     */
    protected LinkedList<Vector<Result>> oldFinResults = new LinkedList<Vector<Result>>();

    /*
     * Results generated at the current doProcess() for the finished jobs.
     */
    protected Vector<Result> crtFinResults = null;

    protected static final int OLD_FIN_HISTORY = 5;

    public monOsgVoJobs() {
        super(MyModuleName, MyResTypes, emailNotifyProperty, statusEmailNotifyProperty, maxResultsEmailedProperty);
        canSuspend = false;
        ModuleName = MyModuleName;
        isRepetitive = true;
        info.ResTypes = ResTypes();
        info.setName("monOsgVoJobs");
    }

    @Override
    public MNode getNode() {
        return this.Node;
    }

    @Override
    public String[] ResTypes() {
        return MyResTypes;
    }

    @Override
    public String getOsName() {
        return OsName;
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    /**
     * Initialization of this module. configuration file entry:
     * monVoJobs{test,debug,location=}%30
     */
    @Override
    public MonModuleInfo init(MNode inNode, String args) {
        String serverName = null;
        String argList[] = new String[] {};
        Node = inNode;

        /* Check the argument list */
        logger.info("Processing arguments for the module monOsgVoJobs...");
        if (args != null) {
            argList = args.split("(\\s)*,(\\s)*");
            testmode = false;
            String sVal;
            Boolean bVal;

            for (String element : argList) {

                sVal = VO_Utils.getSArgumentValue(element, "mapfile", logger);
                if (sVal != null) {
                    mapfile = sVal;
                    logger.log(Level.INFO, "Using map file: " + mapfile);
                    continue;
                }

                bVal = VO_Utils.getBArgumentValue(element, "testmode", logger);
                if (bVal != null) {
                    testmode = bVal.booleanValue();
                    continue;
                }

                bVal = VO_Utils.getBArgumentValue(element, "donotpublishjobinfo", logger);
                if (bVal != null) {
                    if (bVal.equals(Boolean.TRUE)) {
                        shouldPublishJobInfo = false;
                    } else {
                        shouldPublishJobInfo = true;
                    }
                    continue;
                }

                bVal = VO_Utils.getBArgumentValue(element, "checkcmdexitstatus", logger);
                if (bVal != null) {
                    checkExitStatus = bVal.booleanValue();
                    continue;
                }

                /* flag to enable the use of condor_q -global */
                bVal = VO_Utils.getBArgumentValue(element, "condoruseglobal", logger);
                if (bVal != null) {
                    condorUseGlobal = bVal.booleanValue();
                    continue;
                }

                /* flag to disable the use of the "plain" condor_q command */
                bVal = VO_Utils.getBArgumentValue(element, "condorquickmode", logger);
                if (bVal != null) {
                    condorQuickMode = bVal.booleanValue();
                    continue;
                }

                /* flag to enable the parsing of the Condor history file */
                bVal = VO_Utils.getBArgumentValue(element, "condorhistorycheck", logger);
                if (bVal != null) {
                    checkCondorHist = bVal.booleanValue();
                    continue;
                }

                /* flag to disable the parsing of the Condor history file */
                if (element.toLowerCase().startsWith("nocondorhistorycheck")) {
                    checkCondorHist = false;
                    logger.info("CondorHistoryCheck option disabled");
                }

                /* the location of the Condor history file */
                sVal = VO_Utils.getSArgumentValue(element, "condorhistoryfile", logger);
                if (sVal != null) {
                    if (condorHistFiles == null) {
                        condorHistFiles = new Vector<String>();
                    }
                    condorHistFiles.add(sVal);
                    logger.log(Level.INFO, "Using Condor history file: " + sVal);
                    continue;
                }

                /* flag to enable the parsing of the PBS accounting logs */
                bVal = VO_Utils.getBArgumentValue(element, "pbshistorycheck", logger);
                if (bVal != null) {
                    checkPBSHist = bVal.booleanValue();
                    continue;
                }

                /* flag to disable the parsing of the PBS accounting logs */
                if (element.toLowerCase().startsWith("nopbshistorycheck")) {
                    checkPBSHist = false;
                    logger.info("PBSHistoryCheck option disabled");
                    continue;
                }

                /* the location of the PBS accounting log directory */
                sVal = VO_Utils.getSArgumentValue(element, "pbslogdir", logger);
                if (sVal != null) {
                    pbsLogDir = sVal;
                    logger.log(Level.INFO, "Using PBS accounting log directory: " + pbsLogDir);
                    continue;
                }

                /*
                 * flag to disable the execution of the additional command qstat
                 * -a
                 */
                bVal = VO_Utils.getBArgumentValue(element, "pbsquickmode", logger);
                if (bVal != null) {
                    pbsQuickMode = bVal.booleanValue();
                    continue;
                }

                /*
                 * flag to enable/disable the -u argument for the LSF bjobs
                 * command
                 */
                bVal = VO_Utils.getBArgumentValue(element, "lsfuseroption", logger);
                if (bVal != null) {
                    lsfUserOpt = bVal.booleanValue();
                    continue;
                }

                /* the location of the PBS accounting log directory */
                sVal = VO_Utils.getSArgumentValue(element, "lsfusername", logger);
                if (sVal != null) {
                    lsfUserName = sVal;
                    logger.log(Level.INFO, "User name for LSF bjobs: " + lsfUserName);
                    continue;
                }

                /* flag to enable the use of mixed cases in VOs names */
                bVal = VO_Utils.getBArgumentValue(element, "mixedcasevos", logger);
                if (bVal != null) {
                    mixedCaseVOs = bVal.booleanValue();
                    continue;
                }

                /*
                 * flag to enable the interruption of the module execution if 3
                 * consecutive doProcess-es throwed an exception
                 */
                bVal = VO_Utils.getBArgumentValue(element, "cansuspend", logger);
                if (bVal != null) {
                    canSuspend = bVal.booleanValue();
                    continue;
                }

                bVal = VO_Utils.getBArgumentValue(element, "finishedjobsresults", logger);
                if (bVal != null) {
                    finishedJobsResults = bVal.booleanValue();
                    continue;
                }

                bVal = VO_Utils.getBArgumentValue(element, "individualusersresults", logger);
                if (bVal != null) {
                    individualUsersResults = bVal.booleanValue();
                    continue;
                }

                sVal = VO_Utils.getSArgumentValue(element, "server", logger);
                if (sVal != null) {
                    this.nCondorServers++;
                    String argName = element.split("(\\s)*=(\\s)*")[0].trim();
                    String sIdx = argName.substring(6);
                    if (sIdx.length() > 0) {
                        int idx = Integer.parseInt(sIdx);
                        condorServers.put(Integer.valueOf(idx), sVal);
                    } else {
                        condorServers.put(Integer.valueOf(nCondorServers), sVal);
                        logger.log(Level.INFO, "Added Condor Server to query: " + serverName);
                    }
                    continue;
                }

                sVal = VO_Utils.getSArgumentValue(element, "pool", logger);
                if (sVal != null) {
                    String argName = element.split("(\\s)*=(\\s)*")[0].trim();
                    String sIdx = argName.substring(4);
                    if (sIdx.length() > 0) {
                        int idx = Integer.parseInt(sIdx);
                        condorPools.put(Integer.valueOf(idx), sVal);
                    }
                    continue;
                }

                if (element.toLowerCase().startsWith("condorconstraints")) {
                    int poz = element.indexOf('=');
                    String argVal = element.substring(poz + 1);
                    condorConstraints = argVal.trim();
                    logger.log(Level.INFO, "Added Condor constraints: " + condorConstraints);
                    continue;
                }

                sVal = VO_Utils.getSArgumentValue(element, "condorformatoption", logger);
                if (sVal != null) {
                    if (sVal.toLowerCase().equals("on")) {
                        condorFormatOption = ON;
                    } else if (sVal.toLowerCase().equals("off")) {
                        condorFormatOption = OFF;
                    } else {
                        condorFormatOption = ALTERNATIVE;
                    }

                    logger.log(Level.INFO, "Condor format option: " + sVal);
                    continue;
                }

                bVal = VO_Utils.getBArgumentValue(element, "adjustjobstatistics", logger);
                if (bVal != null) {
                    adjustJobStatistics = bVal.booleanValue();
                    continue;
                }

                bVal = VO_Utils.getBArgumentValue(element, "novostatistics", logger);
                if (bVal != null) {
                    noVoStatistics = bVal.booleanValue();
                    continue;
                }

                bVal = VO_Utils.getBArgumentValue(element, "verboselogging", logger);
                if (bVal != null) {
                    verboseLogging = bVal.booleanValue();
                    continue;
                }

            } // end for
        } // end if args

        info.ResTypes = ResTypes();
        if (inNode.farm != null) {
            farmName = inNode.farm.name;
        }

        return info;
    } // end method init

    @Override
    protected void cleanupEnv() {
        // now we can clear ...
        voAccts.clear();
        voMixedCase.clear();
    }

    /**
     * (Re-)Initializes some of the data structures.
     * 
     * @param firstTime
     *            Specifies whether this is the first time we call this
     *            function.
     */
    protected void initializeEnv(boolean firstTime) throws Exception {
        logger.log(Level.INFO, "[monOsgVoJobs] Initializing monOsgVoJobs module...");
        if (isInternalModule) {
            setupEmailNotification();
        }
        loadUserVoMapTable();
        // logger.finest("After loading VoMapTable: " + voMixedCase.size() +
        // " voAccts: " + voAccts.size());
        validateMappings();
        // logger.finest("After validating mappings: " + voMixedCase.size() +
        // " voAccts: " + voAccts.size());
        printVoTable();

        /* initialize the summaries for each VO */
        if (firstTime) {
            VOjobsInfo = new Hashtable<String, VOSummaryExt>();
            userJobsInfo = new Hashtable<String, VOSummaryExt>();
            if (noVoStatistics) {
                VOjobsInfo.put(noVoName, new VOSummaryExt());
            }
            oTotalVOS = new VOSummaryExt();
        }

        // check for new VOs
        Enumeration<String> voe = voMixedCase.elements();
        while (voe.hasMoreElements()) {
            String voMixedName = voe.nextElement();
            VOSummaryExt vos = VOjobsInfo.get(voMixedName);
            if (vos == null) {
                VOjobsInfo.put(voMixedName, new VOSummaryExt());
            }
        }

        // check for new users
        Enumeration<String> acctsE = voAccts.keys();
        while (acctsE.hasMoreElements()) {
            String userName = acctsE.nextElement();
            VOSummaryExt vos = VOjobsInfo.get(userName);
            if (vos == null) {
                VOjobsInfo.put(userName, new VOSummaryExt());
            }
        }

        // check for removed VOs
        Enumeration<String> voi = VOjobsInfo.keys();
        while (voi.hasMoreElements()) {
            String key = voi.nextElement();
            if (key.equals(noVoName)) {
                continue;
            }
            if (!voMixedCase.contains(key)) {
                VOSummaryExt oVOSummary = VOjobsInfo.get(key);
                if ((oVOSummary != null) && (oTotalVOS != null)) {
                    logger.fine("Updating VO totals - removed VO " + key);
                    oTotalVOS.submittedJobs_t -= oVOSummary.submittedJobs_t;
                    oTotalVOS.finishedJobs_t -= oVOSummary.finishedJobs_t;
                    oTotalVOS.successJobs_t -= oVOSummary.successJobs_t;
                    oTotalVOS.errorJobs_t -= oVOSummary.errorJobs_t;
                    oTotalVOS.cpu_time_t -= oVOSummary.cpu_time_t;
                    oTotalVOS.run_time_t -= oVOSummary.run_time_t;
                }

                VOjobsInfo.remove(key);
            }
        }

    } // end method

    /**
     * (Re)sets the environment for this module at the beginning and when the
     * configuration file changes.
     */
    protected void setEnvironment() throws Exception {
        // save latest known state
        tmpVoAccts.clear();
        tmpVoMixedCase.clear();
        tmpVoAccts.putAll(voAccts);
        tmpVoMixedCase.putAll(voMixedCase);

        cleanupEnv();
        try {
            initializeEnv(firstInit);
        } catch (Exception e1) {
            throw e1;
        }

        computeVoAcctsDiff();

        // -- Determine the MonaALisa_HOME ---
        monalisaHome = AppConfig.getGlobalEnvProperty("MonaLisa_HOME");
        if (monalisaHome == null) {
            throw new Exception("MonaLisa_HOME environmental variable not set.");
        } // end if monalisaHome

        // -------------------------------------------------------
        // Determine the job managers being used and the location
        // of their processes for querying the queues.
        // -------------------------------------------------------
        if (firstInit) {
            firstInit = false;
            try {
                getJobManagers();
                if (jobMgr.get(VO_Utils.CONDOR) != null) {
                    condorAcc = new VO_CondorAccounting(condorHistFiles);
                }
                if (jobMgr.get(VO_Utils.PBS) != null) {
                    pbsAcc = new VO_PBSAccounting(pbsLogDir);
                }
            } catch (Exception e1) {
                throw e1;
            }
        }
        environmentSet = true;
    } // end method

    private final StringBuilder[] initCondorCmds() {
        final StringBuilder crtCmd[] = new StringBuilder[3];
        for (int i = 0; i < 3; i++) {
            crtCmd[i] = new StringBuilder(1024);
        }

        crtCmd[0].append("CONDOR_LOCATION/bin/condor_q -l");
        crtCmd[1].append("CONDOR_LOCATION/bin/condor_q");
        crtCmd[2].append("CONDOR_LOCATION/bin/condor_q");

        if (condorUseGlobal) {
            crtCmd[0].append(" -global");
            crtCmd[1].append(" -global");
            crtCmd[2].append(" -global");
        }

        crtCmd[0].append(" ");
        crtCmd[1].append(" ");
        crtCmd[2].append(" ");

        return crtCmd;
    }

    /**
     * Determines the job manages available on this system.
     * 
     * @throws Exception
     */
    public void getJobManagers() throws Exception {
        String methodName = "getJobManagers";
        String location = null;
        String var = null;
        // -------------------------------------------------------
        // Initialize the command for the job manager types
        // -------------------------------------------------------
        if (testmode) {
            jobMgr.put(VO_Utils.CONDOR, "CONDOR_LOCATION/cat " + monalisaHome + "/Service/usr_code/" + VoModulesDir
                    + "/testdata/condor");
            jobMgr.put(VO_Utils.PBS, "PBS_LOCATION/cat " + monalisaHome + "/Service/usr_code/" + VoModulesDir
                    + "/testdata/pbs");
            jobMgr.put(VO_Utils.LSF, "LSF_LOCATION/cat " + monalisaHome + "/Service/usr_code/" + VoModulesDir
                    + "/testdata/lsf");
            jobMgr.put(VO_Utils.FBS, "FBS_LOCATION/cat " + monalisaHome + "/Service/usr_code/" + VoModulesDir
                    + "/testdata/fbs");
            jobMgr.put(VO_Utils.SGE, "SGE_LOCATION/cat " + monalisaHome + "/Service/usr_code/" + VoModulesDir
                    + "/testdata/sge");
        } else {
            /* make up the Condor command */
            final StringBuilder condorCmd[] = new StringBuilder[3];
            for (int i = 0; i < 3; i++) {
                condorCmd[i] = new StringBuilder(1024);
            }

            StringBuilder crtCmd[] = initCondorCmds();

            String constraints = "";
            if (condorConstraints != null) {
                constraints = " -constraint " + "\\\"" + condorConstraints + "\\\"";
            }

            if (condorServers.size() == 0) {

                final String pool = condorPools.get(Integer.valueOf(0));
                if ((pool != null) && !pool.isEmpty()) {
                    crtCmd[0].append(" -pool ").append(pool);
                    crtCmd[1].append(" -pool ").append(pool);
                    crtCmd[2].append(" -pool ").append(pool);
                }

                crtCmd[0].append(constraints);
                crtCmd[1].append(constraints);
                crtCmd[2].append(constraints);

                VO_CondorAccounting.updateCondorCommands(condorCmd, crtCmd);
                this.nCondorCommands++;
                /*
                 * condorCmd.append(
                 * "CONDOR_LOCATION/bin/condor_q -l 2>&1 && echo OK > " +
                 * checkFiles.lastElement());
                 */
            } else {
                Enumeration<Integer> csKeys = condorServers.keys();
                while (csKeys.hasMoreElements()) {
                    Integer crtIdx = csKeys.nextElement();
                    String crtServer = condorServers.get(crtIdx);
                    String crtPool = condorPools.get(crtIdx);

                    crtCmd[0].append(" -name ").append(crtServer);
                    crtCmd[1].append(" -name ").append(crtServer);
                    crtCmd[2].append(" -name ").append(crtServer);

                    if ((crtPool != null) && !crtPool.isEmpty()) {
                        crtCmd[0].append(" -pool ").append(crtPool);
                        crtCmd[1].append(" -pool ").append(crtPool);
                        crtCmd[2].append(" -pool ").append(crtPool);
                    }
                    crtCmd[0].append(constraints);
                    crtCmd[1].append(constraints);
                    crtCmd[2].append(constraints);

                    VO_CondorAccounting.updateCondorCommands(condorCmd, crtCmd);
                    this.nCondorCommands++;
                    crtCmd = initCondorCmds();
                }
            }
            // }

            jobMgr.put(VO_Utils.CONDOR, condorCmd[0].toString());
            jobMgr.put(VO_Utils.CONDOR2, condorCmd[1].toString());
            jobMgr.put(VO_Utils.CONDOR3, condorCmd[2].toString());

            jobMgr.put(VO_Utils.PBS, "PBS_LOCATION/bin/qstat && echo " + VO_Utils.okString);
            jobMgr.put(VO_Utils.PBS2, "PBS_LOCATION/bin/qstat -a && echo " + VO_Utils.okString);
            String lsfCmd = "LSF_LOCATION/bin/bjobs -l";
            if (this.lsfUserOpt) {
                lsfCmd = lsfCmd + " -u " + this.lsfUserName;
            }
            jobMgr.put(VO_Utils.LSF, lsfCmd + " && echo " + VO_Utils.okString);
            // jobMgr.put(VO_Utils.LSF, "LSF_LOCATION/bin/bjobs -a -l && echo "
            // + VO_Utils.okString);
            jobMgr.put(VO_Utils.FBS, "FBS_LOCATION/bin/fbs lj && echo " + VO_Utils.okString);
            jobMgr.put(VO_Utils.SGE, "SGE_LOCATION/bin/SGE_ARCH/qstat -ext && echo " + VO_Utils.okString);

            versionCmd.put(VO_Utils.CONDOR, "CONDOR_LOCATION/bin/condor_version 2>&1 && echo " + VO_Utils.okString);
            versionCmd.put(VO_Utils.PBS, "PBS_LOCATION/bin/qstat --version 2>&1 && echo " + VO_Utils.okString);
            versionCmd.put(VO_Utils.LSF, "LSF_LOCATION/bin/bjobs -V 2>&1 && echo " + VO_Utils.okString);
            // jobMgr.put(VO_Utils.FBS, "FBS_LOCATION/bin/fbs lj 2>&1 && echo "
            // + VO_Utils.okString);
            versionCmd.put(VO_Utils.SGE, "SGE_LOCATION/bin/SGE_ARCH/sge_comm -V 2>&1 && echo " + VO_Utils.okString);
        }

        // ------------------------------------------------------------------
        // Check the environmental variables for the job manager location
        // If found, replace the environmental value in the command string.
        // ------------------------------------------------------------------
        for (Iterator<Map.Entry<String, String>> jmi = jobMgr.entrySet().iterator(); jmi.hasNext();) {
            Map.Entry<String, String> entry = jmi.next();

            String jobMgrKey = entry.getKey();
            String newloc = entry.getValue();

            String jobManager = jobMgrKey;
            if (jobMgrKey.startsWith(VO_Utils.CONDOR)) {
                jobManager = VO_Utils.CONDOR;
            }
            if (jobMgrKey.startsWith(VO_Utils.PBS)) {
                jobManager = VO_Utils.PBS;
            }

            String verCmd = versionCmd.get(jobManager);

            var = jobManager + "_LOCATION";
            try {
                location = AppConfig.getGlobalEnvProperty(var);
            } catch (Throwable e) {
                logger.log(Level.WARNING, "[monOsgVoJobs] Unable to getGlobalEnvProperty(" + var + "). Cause:", e);
            }
            if (location == null) {
                jmi.remove();
                versionCmd.remove(jobManager);
                continue;
            }

            if (location.length() == 0) {
                versionCmd.remove(jobManager);
                jmi.remove();
                continue;
            }
            // ---------------------------------------------------------
            // Replace the variable portion of the path with the
            // environmental variable value found.
            // (e.g. - CONDOR_LOCATION)
            // ---------------------------------------------------------
            String value = newloc.replaceAll(var, location);
            String newVerCmd = verCmd.replaceAll(var, location);

            logger.log(Level.INFO, "[monOsgVoJobs] Using " + jobMgrKey + " location: " + location);

            /* find the location of the Condor history file */
            if (jobManager.equals(VO_Utils.CONDOR)) {
                /*
                 * if we query other Condor servers than the local one, the
                 * location of the history files must be set manually in the
                 * conf file. If it wasn't set, disable history processing.
                 */
                if ((this.condorUseGlobal || (this.condorServers.size() > 0)) && (this.checkCondorHist == true)
                        && (this.condorHistFiles == null)) {
                    logger.warning("Non-local Condor servers will be used and the location of the history files was not specified. Disabling history log processing...");
                    this.checkCondorHist = false;
                }
                this.condorLocation = location;
                if (this.checkCondorHist) {
                    initCondorHistoryFiles();
                    // if (haveCondorHistFiles)
                    // logger.log(Level.INFO, "Using Condor history file: "
                    // + condorHistFiles.get(0));
                }
            }// if - Condor

            if (jobManager.equals(VO_Utils.PBS) && this.checkPBSHist) {
                if (pbsLogDir == null) {
                    /* use the default location */
                    pbsLogDir = new String(defaultPBSLogDir);
                    logger.log(Level.INFO, "Using the default location for the PBS accounting log dir: " + pbsLogDir);
                }
                File fd = new File(pbsLogDir);
                if (!fd.exists() || !fd.canRead()) {
                    logger.log(Level.WARNING,
                            "The PBS accounting log dir could not be found or is not readable. No history information will be provided.");
                    this.checkPBSHist = false;
                }
            } // if - PBS

            if (jobManager.equals(VO_Utils.SGE)) {
                String SGE_ARCH = "UNKOWN";
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(Runtime.getRuntime()
                            .exec(location + "/util/arch").getInputStream()));
                    String line = br.readLine();
                    if (line == null) {
                        break;
                    }
                    SGE_ARCH = line.trim();
                } catch (Throwable t) {
                    SGE_ARCH = "UNKOWN";
                }

                addToMsg(methodName, "SGE_ARCH = " + SGE_ARCH);
                if (SGE_ARCH.equals("UNKNOWN") || (SGE_ARCH.length() == 0)) {
                    addToMsg(methodName, " Ignoring SGE !");
                    jmi.remove();
                    continue;
                }

                value = value.replaceFirst("SGE_ARCH", SGE_ARCH);
                newVerCmd = newVerCmd.replaceFirst("SGE_ARCH", SGE_ARCH);
            }// if SGE

            jobMgr.put(jobMgrKey, value);
            versionCmd.put(jobManager, newVerCmd);
        } // end of for (Enum...

        // -----------------------------------------------------
        // Verify the remaining job managers' executable exists.
        // if not, remove it.
        // -----------------------------------------------------
        for (Iterator<Map.Entry<String, String>> jmi = jobMgr.entrySet().iterator(); jmi.hasNext();) {
            Map.Entry<String, String> entry = jmi.next();

            String jobManager = entry.getKey();

            String cmd1 = jobMgr.get(jobManager);
            StringTokenizer tz = new StringTokenizer(cmd1);
            // if (jobManager.equals("CONDOR3"))
            // tz = new StringTokenizer (cmd1.replaceAll("'", "")) ;
            int ni = tz.countTokens();
            if (ni > 0) {
                location = tz.nextToken().trim();
                File fd = new File(location);
                if (!fd.exists()) {
                    jmi.remove();
                    logger.warning("Job Manager (" + jobManager + ") Command (" + location + ") does not exist.");
                } // end if/else

            } // end if ni>0
            logger.info("Using " + jobManager + " command: " + cmd1);
        } // end of for (Enum...

        // Determine the job managers' versions
        for (Entry<String, String> vcEntry : versionCmd.entrySet()) {
            String vcKey = vcEntry.getKey();
            String vcVal = vcEntry.getValue();

            logger.info("Using command to determine " + vcKey + " version: " + vcVal);
            BufferedReader buff1 = procOutput(vcVal, CMD_DELAY);
            boolean versionOk = false;
            String vLine;
            String mgrVersion = null;
            if (buff1 != null) {
                while ((vLine = buff1.readLine()) != null) {
                    if (vLine.startsWith(VO_Utils.okString)) {
                        versionOk = true;
                    } else if (mgrVersion == null) {
                        // from the command's output
                        mgrVersion = vLine;
                    }
                }
            }

            if (versionOk && (mgrVersion != null)) {
                jobMgrVersions.put(vcKey, mgrVersion);
            } else {
                countNewError(VO_Utils.INTERNAL_JMGR_VERSION);
                MLLogEvent<String, Integer> mlle = new MLLogEvent<String, Integer>();
                mlle.logParameters.put("Error Code", Integer.valueOf(VO_Utils.INTERNAL_JMGR_VERSION));
                logger.log(crtLevel[VO_Utils.INTERNAL_JMGR_VERSION], "Could not determine version for job manager: "
                        + vcKey, new Object[] { mlle });
                jobMgrVersions.put(vcKey, vcKey);
            }
        }

    } // end method

    /**
     * Determines and verifies the location of the Condor history file. The
     * filename will be null if the file does not exist or cannot be read.
     */
    protected void initCondorHistoryFiles() {
        if ((this.condorHistFiles == null) && !this.condorUseGlobal && (this.nCondorServers == 0)) {
            /* we only need the local history file */
            String localCmd = this.condorLocation + "/bin/condor_config_val LOCAL_DIR 2>&1 && echo "
                    + VO_Utils.okString;
            logger.info("Using command to determine Condor local dir: " + localCmd);
            try {
                BufferedReader buff1 = procOutput(localCmd, CMD_DELAY);
                String vLine;
                if (buff1 != null) {
                    while ((vLine = buff1.readLine()) != null) {
                        // we only need the first line from the command's output
                        if (this.condorLocalDir == null) {
                            this.condorLocalDir = vLine.trim();
                        }
                    }
                    condorHistFiles = new Vector<String>();
                    condorHistFiles.add(this.condorLocalDir + "/spool/history");
                }
            } catch (Exception e) {
                logger.warning("Error in obtaining the location of the Condor history file. Disabling history file processing...");
                this.haveCondorHistFiles = false;
            }
        }

        File fd = null;
        if (condorHistFiles != null) {
            this.haveCondorHistFiles = true;
            for (int i = 0; i < condorHistFiles.size(); i++) {
                fd = new File((condorHistFiles.get(i)));
                if (!fd.exists() || !fd.canRead()) {
                    logger.log(Level.WARNING,
                            "The Condor history file cannot be found or cannot be read. No history information will be provided.");
                    this.haveCondorHistFiles = false;
                } else {
                    logger.info("Using Condor history file: " + condorHistFiles.get(i));
                }
            }
        }
    }

    /**
     * The "main" function of the module.
     */
    @Override
    public Object doProcess() throws Exception {
        hmNoVoUsers.clear();
        long tStart = System.nanoTime();
        String methodName = "doProcess";
        this.errorCode = 0;

        long cTime = NTPDate.currentTimeMillis();
        Result rStatus = new Result();
        rStatus.ClusterName = Node.getClusterName() + "_Totals";
        rStatus.FarmName = farmName;
        rStatus.NodeName = "Status";
        rStatus.time = cTime;
        rStatus.Module = ModuleName;

        String excMailMsg = null;
        nCurrentResults = 0;
        Vector<Object> v = new Vector<Object>();
        activeVos = new LinkedList<String>();
        activeUsers = new LinkedList<String>();
        try {
            // -- set environment (only once we hope) --
            try {
                synchronized (jobMgr) {
                    if (!environmentSet) {
                        setEnvironment();
                    } // if environmentSet
                }
            } catch (Exception exc) {
                this.errorCode = VO_Utils.INTERNAL_JMGR_SETENV;
                countNewError(this.errorCode);
                MLLogEvent<String, Object> mlle = new MLLogEvent<String, Object>();
                Integer errCode = Integer.valueOf(this.errorCode);
                mlle.logParameters.put("Error Code", errCode);
                mlle.logParameters.put("Error Message", VO_Utils.voJobsErrCodes.get(errCode));
                logger.log(crtLevel[this.errorCode], "Error seting module environment", new Object[] { mlle, exc });
                throw exc;
            }

            if (getShouldNotifyConfig()) {
                logit(" [ monOsgVoJobs ] - Notified Config changed");
                // setShouldNotifyConfig(false);
            }

            currentJobsInfo = new Hashtable<String, JobInfoExt>();
            currentFinishedJobsInfo = new Hashtable<String, JobInfoExt>();

            /*
             * copy the old VOjobsInfo data into the current one, which will
             * further be updated; the fields that do not represent total values
             * are initialised to 0
             */
            currentVOjobsInfo = new Hashtable<String, VOSummaryExt>();
            Enumeration<String> voe = VOjobsInfo.keys();
            while (voe.hasMoreElements()) {
                String voName = voe.nextElement();
                VOSummaryExt vos = VOjobsInfo.get(voName);
                VOSummaryExt vosc = (VOSummaryExt) vos.clone();
                vosc.runningjobs = vosc.idlejobs = vosc.heldjobs = 0;
                vosc.finishedJobs = vosc.unknownjobs = vosc.submittedJobs = 0;
                vosc.successJobs = vosc.errorJobs = 0;
                vosc.size = 0.0;
                vosc.disk_usage = 0.0;
                vosc.paramsCondorH.put("CondorMemoryUsage", Double.valueOf(0));
                vosc.paramsCondorH.put("CondorDiskUsage", Double.valueOf(0));
                currentVOjobsInfo.put(voName, vosc);
            }

            /* the same for the UserJobsInfo statistics */
            currentUserJobsInfo = new Hashtable<String, VOSummaryExt>();
            Enumeration<String> use = userJobsInfo.keys();
            while (use.hasMoreElements()) {
                String userName = use.nextElement();
                VOSummaryExt vos = userJobsInfo.get(userName);
                VOSummaryExt vosc = (VOSummaryExt) vos.clone();
                vosc.runningjobs = vosc.idlejobs = vosc.heldjobs = 0;
                vosc.finishedJobs = vosc.unknownjobs = vosc.submittedJobs = 0;
                vosc.successJobs = vosc.errorJobs = 0;
                vosc.size = 0.0;
                vosc.disk_usage = 0.0;
                vosc.paramsCondorH.put("CondorMemoryUsage", Double.valueOf(0));
                vosc.paramsCondorH.put("CondorDiskUsage", Double.valueOf(0));
                currentUserJobsInfo.put(userName, vosc);
            }

            /*
             * once in a while, try the plain condor_q command even if it
             * previously failed
             */
            if ((execCnt % 50) == 0) {
                this.failedCondorQ = false;
            }

            // -- Start the Job Queue Manager collectors ---
            Vector<Result> fjResults = new Vector<Result>();
            crtFinResults = new Vector<Result>();
            mlleInconsist = new MLLogEvent<String, Object>();

            boolean bret = collectJobMgrData(fjResults);
            if (bret == false) {
                if (this.errorCode == 0) {
                    this.errorCode = VO_Utils.INTERNAL_FIRST_EXEC;
                }

                // String firstErrMsg =
                // (String)VO_Utils.voJobsErrCodes.get(Integer.valueOf(this.errorCode));
                // MLLogEvent mlle = new MLLogEvent();
                // mlle.logParameters.put("Error Code",
                // Integer.valueOf(this.errorCode));
                // mlle.logParameters.put("Error Message", firstErrMsg);
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Failed getting jobs status, retrying after 20s...");
                }
                try {
                    Thread.sleep(20000);
                } catch (Throwable t) {
                    // no interest
                }

                fjResults = new Vector<Result>();
                crtFinResults = new Vector<Result>();
                mlleInconsist = new MLLogEvent<String, Object>();
                bret = collectJobMgrData(fjResults);
            }

            /*
             * if (inconsistentJobInfo) { this.errorCode =
             * VO_Utils.INTERNAL_JMGR_INCONSIST; throw new
             * Exception("Inconsistent job information, not sending any result"
             * ); }
             */

            if (bret == true) {
                if (finishedJobsResults) {
                    v.addAll(fjResults);
                }
                // put these at the end of the vector because they may contain
                // eResults
                // for finished jobs
                v.addAll(getResults());

            } else {
                throw new Exception(
                        "Second attempt to get jobs status from any job manager failed, no results were sent");
            }

            /* see if we had jobs with decreasing CPU/run time */
            if (mlleInconsist.logParameters.size() > 0) {
                mlleInconsist.logParameters.put("Error Code", Integer.valueOf(VO_Utils.INTERNAL_JMGR_INCONSIST));
                logger.log(crtLevel[VO_Utils.INTERNAL_JMGR_INCONSIST], "Got jobs with decreasing CPU or run time",
                        new Object[] { mlleInconsist });
            }

            /* create eResults for the VOs that do not have any more jobs */
            Iterator<String> it = oldActiveVos.iterator();
            while (it.hasNext()) {
                String crtVo = it.next();
                if (!activeVos.contains(crtVo)) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "VO " + crtVo + " has no more jobs");
                    }
                    // this VO has no more jobs, delete the jobs cluster
                    eResult erv = new eResult();
                    if (mixedCaseVOs) {
                        erv.ClusterName = crtVo + "_JOBS_OSG";
                    } else {
                        erv.ClusterName = crtVo.toUpperCase() + "_JOBS_OSG";
                    }
                    erv.NodeName = null;
                    erv.FarmName = farmName;
                    erv.param = null;
                    erv.param_name = null;
                    erv.Module = ModuleName;
                    v.add(erv);
                }
            }
            oldActiveVos = activeVos;
            oldActiveUsers = activeUsers;

            /*
             * remove the results gnerated for finished jobs, by a previous
             * doProcess
             */
            if (finishedJobsResults) {
                oldFinResults.addLast(crtFinResults);
                if (oldFinResults.size() > OLD_FIN_HISTORY) {
                    Vector<Result> toRemove = oldFinResults.getFirst();
                    oldFinResults.removeFirst();

                    for (int idx = 0; idx < toRemove.size(); idx++) {
                        eResult er = new eResult();
                        Result oldRes = toRemove.get(idx);
                        er.ClusterName = oldRes.ClusterName;
                        er.NodeName = oldRes.NodeName;
                        er.FarmName = farmName;
                        er.time = NTPDate.currentTimeMillis();
                        er.addSet(oldRes.param_name[0], null);
                        er.Module = ModuleName;
                        v.add(er);

                        /*
                         * see if we have other results for this VO (and for any
                         * VO)
                         */
                        boolean voFound = false;
                        boolean haveResult = false;
                        Iterator<Vector<Result>> lit = oldFinResults.iterator();
                        while (lit.hasNext()) {
                            Vector<Result> vr = lit.next();
                            for (int j = 0; j < vr.size(); j++) {
                                haveResult = true;
                                Result cr = vr.get(j);
                                if (cr.NodeName.equals(oldRes.NodeName)) {
                                    voFound = true;
                                }
                            }

                        }

                        if (!voFound) {
                            logger.log(Level.FINE, "VO " + oldRes.NodeName + " has no more old jobs");

                            eResult er1 = new eResult();
                            er1.ClusterName = oldRes.ClusterName;
                            er1.NodeName = oldRes.NodeName;
                            er1.FarmName = farmName;
                            er1.param = null;
                            er1.param_name = null;
                            er1.Module = ModuleName;
                            v.add(er1);
                        }

                        if (!haveResult) {
                            logger.log(Level.FINE, "No more old finished jobs");

                            eResult er1 = new eResult();
                            er1.ClusterName = oldRes.ClusterName;
                            er1.FarmName = farmName;
                            er1.NodeName = null;
                            er1.param = null;
                            er1.param_name = null;
                            er1.Module = ModuleName;
                            v.add(er1);
                        }

                    }
                }
            }

        } catch (Throwable e) {
            if (this.errorCode == 0) {
                this.errorCode = VO_Utils.INTERNAL_JMGR_GEN; // general error
                                                             // code
                countNewError(this.errorCode);
            }

            logger.log(crtLevel[this.errorCode], "monOsgVoJobs.doProcess() got exception:", e);
            if (isInternalModule) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                excMailMsg = methodName + " - got exception: " + sw.toString() + "\n";

            }
            // throw new Exception(e) ;
        } // end try/catch

        rStatus.addSet("VO_JOBS_Status", this.errorCode);

        if (getShouldNotifyConfig()) {
            logger.info("Sending eResults for dead VOs, if any ...");
            for (int i = 0; i < removedVOseResults.size(); i++) {
                eResult er = removedVOseResults.get(i);
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Sending eResults for " + er.NodeName);
                }
                if (!mixedCaseVOs) {
                    er.NodeName = er.NodeName.toUpperCase();
                }
                v.add(er);
                v.add(new eResult(er.FarmName, er.ClusterName + "_Rates", er.NodeName, ModuleName, er.param_name));

                // send an eResult for the jobs cluster
                /*
                 * eResult er1 = new eResult(); if (mixedCaseVOs)
                 * er1.ClusterName = er.NodeName + "_JOBS_OSG"; else
                 * er1.ClusterName = er.NodeName.toUpperCase() + "_JOBS_OSG";
                 * er1.NodeName = null; er1.param = null; er1.param_name = null;
                 * er1.Module = ModuleName; v.add(er1);
                 */

            }
            setShouldNotifyConfig(false);
            // logger.info("Module configuration changed...");
        }

        if ((excMailMsg != null) && isInternalModule && ((this.execCnt % SAMPLE_PER_ERRORS) == 0)) {
            sendExceptionEmail(excMailMsg);
        }

        prevDoProcessTime = crtDoProcessTime;
        crtDoProcessTime = NTPDate.currentTimeMillis();
        long totalProcessingTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tStart);

        /* add the results for the execution time */
        try {
            Enumeration<String> eMgr = execTimes.keys();
            while (eMgr.hasMoreElements()) {
                String crtMgr = eMgr.nextElement();
                Long lTime = execTimes.get(crtMgr);
                rStatus.addSet("ExecTime_" + crtMgr, lTime.doubleValue());
            }
            rStatus.addSet("TotalProcessingTime", totalProcessingTime);
            v.add(rStatus);
        } catch (Exception e) {
            logger.log(Level.WARNING, "monOsgVoJobs - Exception. Cause:", e);
        }

        /* add the results for the job manager versions */
        // TODO change this
        if ((execCnt % 10) == 0) {
            eResult jmvRes = new eResult();
            jmvRes.ClusterName = Node.getClusterName() + "_Totals";
            jmvRes.NodeName = "Status";
            jmvRes.Module = ModuleName;
            jmvRes.time = cTime;
            jmvRes.FarmName = farmName;

            for (Entry<String, String> jmvEntry : jobMgrVersions.entrySet()) {
                String jmvKey = jmvEntry.getKey();
                String jmvVal = jmvEntry.getValue();
                jmvRes.addSet(jmvKey + "_Version", jmvVal);
            }
            v.add(jmvRes);
        }

        // -- update the results counters ----
        nCumulatedResults += v.size();
        nCurrentResults = v.size();

        // -- save the latest results (to be included in the status email) ----
        lastResults = new StringBuilder();
        int maxResults = maxResultsEmailed;
        if (maxResults > v.size()) {
            maxResults = v.size();
        }
        for (int i = 0; i < maxResults; i++) {
            try {
                lastResults.append("\n[" + i + "]" + v.elementAt(i));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (isInternalModule) {
            sendStatusEmail();
        }

        // -- update the error logging counters ----
        refreshErrorCount();

        StringBuilder logExit = new StringBuilder(8192);

        logExit.append("Execution time for doProcess() [").append(execCnt).append("]: ").append(totalProcessingTime)
                .append(" ms");
        execCnt = (execCnt + 1) % 2000000;
        logExit.append("; Number of results returned: ").append(v.size()).append("; Error code: ")
                .append(this.errorCode);
        logger.info(logExit.toString());
        if (!hmNoVoUsers.isEmpty()) {
            final StringBuilder sbUserWarn = new StringBuilder(1024);
            sbUserWarn.append("\n\n----------------------------------------------------");
            sbUserWarn.append("\nUsers which cannot be mapped to any VOs( count = ").append(hmNoVoUsers.size())
                    .append("):\n");
            for (Entry<String, String> entry : hmNoVoUsers.entrySet()) {
                sbUserWarn.append("\n").append(entry.getKey()).append(" --> ").append(entry.getValue());
            }
            sbUserWarn.append("\n\n----------------------------------------------------");
            logger.log(Level.WARNING, sbUserWarn.toString());
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("Sending Results: " + lastResults.toString());
        }

        return v;
    } // end method

    /**
     * Constructs a Result with the information regarding a singular job, held
     * in a JobInfoExt object.
     */
    private Object jobInfoToResult(JobInfoExt jobInfo) {
        /*
         * if decreasing cpu time / run time was reported, don't make a Result
         * for the job
         */
        if ((jobInfo.error_status_cpu == JobInfoExt.JOB_SUSPENDED)
                || (jobInfo.error_status_run == JobInfoExt.JOB_SUSPENDED)) {
            return null;
        }

        Result r = new Result();

        if (mixedCaseVOs) {
            r.ClusterName = jobInfo.VO + "_JOBS_OSG";
        } else {
            r.ClusterName = jobInfo.VO.toUpperCase() + "_JOBS_OSG";
        }

        r.NodeName = jobInfo.id;
        r.FarmName = farmName;
        r.time = NTPDate.currentTimeMillis();
        if ((jobInfo.error_status_cpu == JobInfoExt.JOB_MODIFIED) && this.adjustJobStatistics) {
            r.addSet("CPUTime", jobInfo.cpu_time + jobInfo.cpu_time_old);
        } else {
            r.addSet("CPUTime", jobInfo.cpu_time);
        }

        // r.addSet("RunTime", jobInfo.run_time/(double)MIN_SEC);
        if (jobInfo.jobManager.equals(VO_Utils.CONDOR)
                || (jobInfo.jobManager.equals(VO_Utils.PBS) && (pbsQuickMode == false))) {
            if ((jobInfo.error_status_run == JobInfoExt.JOB_MODIFIED) && this.adjustJobStatistics) {
                r.addSet("RunTime", (double) (jobInfo.run_time + jobInfo.run_time_old) / (double) MIN_SEC);
                r.addSet("WallClockTime", jobInfo.run_time + jobInfo.run_time_old);
            } else {
                r.addSet("RunTime", (double) (jobInfo.run_time) / (double) MIN_SEC);
                r.addSet("WallClockTime", jobInfo.run_time);
            }
        }

        if (jobInfo.jobManager.equals(VO_Utils.CONDOR) || jobInfo.jobManager.equals(VO_Utils.LSF)
                || jobInfo.jobManager.equals(VO_Utils.SGE)) {
            r.addSet("Size", jobInfo.size);
        }
        if (jobInfo.jobManager.equals(VO_Utils.CONDOR)) {
            r.addSet("DiskUsage", jobInfo.disk_usage);
        }
        r.Module = ModuleName;

        return r;
    }

    /**
     * Gathers the current Results both for individual jobs and for VOs.
     */
    private Vector<Object> getResults() {
        Vector<Object> v = new Vector<Object>();

        /* get the rate statistics for jobs */
        Vector<Object> jd = getJobDiff();
        if ((jd != null) && (jd.size() > 0)) {
            v.addAll(jd);
        }

        // publish all the current jobs
        if (shouldPublishJobInfo) {
            logger.log(Level.FINE, "Found " + currentJobsInfo.size() + " current jobs");
            for (Enumeration<String> it = currentJobsInfo.keys(); it.hasMoreElements();) {
                String jobId = it.nextElement();
                try {
                    Object o = jobInfoToResult(currentJobsInfo.get(jobId));
                    if (o != null) {
                        v.add(o);
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exception adding JobID :- " + jobId, t);
                }
            }
        }

        /* update the cumulated parameters for each VO */
        for (Enumeration<VOSummaryExt> it = currentVOjobsInfo.elements(); it.hasMoreElements();) {
            VOSummaryExt cvos = it.nextElement();
            cvos.finishedJobs_t += cvos.finishedJobs;
            cvos.submittedJobs_t += cvos.submittedJobs;
            cvos.successJobs_t += cvos.successJobs;
            cvos.errorJobs_t += cvos.errorJobs;
        }

        for (Enumeration<VOSummaryExt> it = currentUserJobsInfo.elements(); it.hasMoreElements();) {
            VOSummaryExt cvos = it.nextElement();
            cvos.finishedJobs_t += cvos.finishedJobs;
            cvos.submittedJobs_t += cvos.submittedJobs;
            cvos.successJobs_t += cvos.successJobs;
            cvos.errorJobs_t += cvos.errorJobs;
        }

        /* get the rate statistics for VOs and users */
        Vector<Object> vd = null;
        if (lastRun != 0) {
            vd = getVODiff();
        }
        if ((vd != null) && (vd.size() > 0)) {
            v.addAll(vd);
        }

        if (individualUsersResults) {
            Vector<Object> ud = null;
            if (lastRun != 0) {
                ud = getUserDiff();
            }
            if ((ud != null) && (ud.size() > 0)) {
                v.addAll(ud);
            }
        }

        jobsInfo = currentJobsInfo;
        finishedJobsInfo = currentFinishedJobsInfo;
        VOjobsInfo = currentVOjobsInfo;
        userJobsInfo = currentUserJobsInfo;
        oTotalVOS = cTotalVOS;

        lastRun = NTPDate.currentTimeMillis();

        return v;
    }

    private double getBin(double newValue, double oldValue) {
        return newValue - oldValue;
    }

    private double getRate(double newValue, double oldValue, double dt) {
        return getBin(newValue, oldValue) / dt;
    }

    /**
     * Calculates rate statistics for VOs.
     * 
     * @return Vector with Results containing the rates.
     */
    private Vector<Object> getVODiff() {

        Vector<Object> retV = new Vector<Object>();
        long cTime = NTPDate.currentTimeMillis();
        double dTime = ((double) (cTime - lastRun)) / SEC_MILLIS; // in seconds
        // logger.info("### lastRun " + lastRun + "cTime " + cTime);

        // in this result we put the total values for all the VOs, for the
        // values of the parameters in the last time interval
        Result rt = new Result();
        rt.ClusterName = Node.getClusterName() + "_Totals";
        rt.FarmName = farmName;
        rt.NodeName = "Totals";
        rt.time = cTime;
        rt.Module = ModuleName;

        // in this result we put the total values for all the VOs, for the rates
        Result rt1 = new Result();
        rt1.ClusterName = Node.getClusterName() + "_Totals";
        rt1.NodeName = "Total_Rates";
        rt1.FarmName = farmName;
        rt1.time = cTime;
        rt1.Module = ModuleName;

        // used to compute the totals for all the VOs
        cTotalVOS = new VOSummaryExt();

        for (Enumeration<String> it = VOjobsInfo.keys(); it.hasMoreElements();) {
            String VO = it.nextElement();

            VOSummaryExt cVOSummary = currentVOjobsInfo.get(VO);
            if (cVOSummary != null) {
                VOSummaryExt oVOSummary = VOjobsInfo.get(VO);

                // in this result we put the cumulated values of the parameters
                Result r = new Result();
                r.ClusterName = Node.getClusterName();
                r.NodeName = VO;
                r.FarmName = farmName;
                if (!mixedCaseVOs) {
                    r.NodeName = r.NodeName.toUpperCase();
                }
                r.time = cTime;
                r.Module = ModuleName;

                // in this result we put the rates
                Result r1 = new Result();
                r1.ClusterName = Node.getClusterName() + "_Rates";
                r1.NodeName = VO;
                r1.FarmName = farmName;
                if (!mixedCaseVOs) {
                    r1.NodeName = r1.NodeName.toUpperCase();
                }
                r1.time = cTime;
                r1.Module = ModuleName;

                /* add the values of this VO to the total values */
                cTotalVOS.runningjobs += cVOSummary.runningjobs;
                cTotalVOS.idlejobs += cVOSummary.idlejobs;
                cTotalVOS.heldjobs += cVOSummary.heldjobs;

                cTotalVOS.submittedJobs += cVOSummary.submittedJobs;
                cTotalVOS.submittedJobs_t += cVOSummary.submittedJobs_t;

                cTotalVOS.finishedJobs += cVOSummary.finishedJobs;
                cTotalVOS.finishedJobs_t += cVOSummary.finishedJobs_t;

                cTotalVOS.successJobs += cVOSummary.successJobs;
                cTotalVOS.successJobs_t += cVOSummary.successJobs_t;
                cTotalVOS.errorJobs += cVOSummary.errorJobs;
                cTotalVOS.errorJobs_t += cVOSummary.errorJobs_t;

                cTotalVOS.size += cVOSummary.size;
                cTotalVOS.run_time_t += cVOSummary.run_time_t;
                cTotalVOS.cpu_time_t += cVOSummary.cpu_time_t;
                cTotalVOS.disk_usage += cVOSummary.disk_usage;

                if (cVOSummary.unknownjobs != 0) {
                    cTotalVOS.unknownjobs += cVOSummary.unknownjobs;
                }

                Enumeration<String> condorE = cVOSummary.paramsCondorH.keys();
                while (condorE.hasMoreElements()) {
                    String key = condorE.nextElement();
                    Double cval = cVOSummary.paramsCondorH.get(key);

                    double total_val = cTotalVOS.paramsCondorH.get(key).doubleValue();
                    total_val += cval.doubleValue();
                    cTotalVOS.paramsCondorH.put(key, Double.valueOf(total_val));
                }

                Enumeration<String> lsfe = cVOSummary.paramsLSF.keys();
                while (lsfe.hasMoreElements()) {
                    String key = lsfe.nextElement();
                    Double cval = cVOSummary.paramsLSF.get(key);

                    double total_val = cTotalVOS.paramsLSF.get(key).doubleValue();
                    total_val += cval.doubleValue();
                    cTotalVOS.paramsLSF.put(key, Double.valueOf(total_val));
                }

                /* fill the results with values for this VO */
                if (activeVos.contains(VO) || oldActiveVos.contains(VO)) {
                    // when a VO becomes inactive we send one final Result for
                    // it
                    fillSummaryResults(cVOSummary, oVOSummary, r, r1, dTime);
                }

                if (activeVos.contains(VO)) {
                    r.addSet("VO_Status", 0);
                    r1.addSet("VO_Status", 0);
                } else {
                    r.addSet("VO_Status", 1);
                    r1.addSet("VO_Status", 1);
                }
                retV.add(r);
                if (execCnt > 0) {
                    retV.add(r1);
                }

                /* if the VO has just became active send a 0 rates result */
                if (((activeVos.contains(VO) && !oldActiveVos.contains(VO)) || (!activeVos.contains(VO) && oldActiveVos
                        .contains(VO))) && (execCnt > 0)) {
                    Result rZero = new Result();
                    rZero.ClusterName = Node.getClusterName() + "_Rates";
                    rZero.NodeName = VO;
                    rZero.FarmName = farmName;
                    if (!mixedCaseVOs) {
                        rZero.NodeName = rZero.NodeName.toUpperCase();
                    }
                    if (activeVos.contains(VO)) {
                        rZero.time = prevDoProcessTime;
                    } else {
                        rZero.time = cTime + 1000;
                    }
                    rZero.Module = ModuleName;
                    Result rDummy = new Result();
                    fillSummaryResults(new VOSummaryExt(), new VOSummaryExt(), rDummy, rZero, 1);
                    retV.add(rZero);
                }
            }

        }

        /* fill the results with the total values */
        fillSummaryResults(cTotalVOS, oTotalVOS, rt, rt1, dTime);
        retV.add(rt);
        retV.add(rt1);

        return retV;
    }

    /**
     * Calculates rate statistics for users.
     * 
     * @return Vector with Results containing the rates.
     */
    private Vector<Object> getUserDiff() {

        Vector<Object> retV = new Vector<Object>();
        long cTime = NTPDate.currentTimeMillis();
        double dTime = ((double) (cTime - lastRun)) / SEC_MILLIS; // in seconds

        for (Enumeration<String> it = userJobsInfo.keys(); it.hasMoreElements();) {
            String userName = it.nextElement();

            VOSummaryExt cUserSummary = currentUserJobsInfo.get(userName);
            if (cUserSummary != null) {
                VOSummaryExt oUserSummary = userJobsInfo.get(userName);

                // in this result we put the cumulated values of the parameters
                Result r = new Result();
                r.ClusterName = Node.getClusterName() + "_Users";
                r.NodeName = userName;
                r.FarmName = farmName;
                r.time = cTime;
                r.Module = ModuleName;

                // in this result we put the rates
                Result r1 = new Result();
                r1.ClusterName = Node.getClusterName() + "_Users_Rates";
                r1.NodeName = userName;
                r1.FarmName = farmName;
                r1.time = cTime;
                r1.Module = ModuleName;

                /* fill the results with values for this user */
                if (activeUsers.contains(userName) || oldActiveUsers.contains(userName)) {
                    // when a user becomes inactive we send one final Result for
                    // it
                    fillSummaryResults(cUserSummary, oUserSummary, r, r1, dTime);
                }

                if (activeUsers.contains(userName)) {
                    r.addSet("User_Status", 0);
                    r1.addSet("User_Status", 0);
                } else {
                    r.addSet("User_Status", 1);
                    r1.addSet("User_Status", 1);
                }
                retV.add(r);
                if (execCnt > 0) {
                    retV.add(r1);
                }

                /* if the user has just became active send a 0 rates result */
                if (((activeUsers.contains(userName) && !oldActiveUsers.contains(userName)) || (!activeUsers
                        .contains(userName) && oldActiveUsers.contains(userName))) && (execCnt > 0)) {
                    Result rZero = new Result();
                    rZero.ClusterName = Node.getClusterName() + "_Users_Rates";
                    rZero.NodeName = userName;
                    rZero.FarmName = farmName;
                    if (activeUsers.contains(userName)) {
                        rZero.time = prevDoProcessTime;
                    } else {
                        rZero.time = cTime + 1000;
                    }
                    rZero.Module = ModuleName;
                    Result rDummy = new Result();
                    fillSummaryResults(new VOSummaryExt(), new VOSummaryExt(), rDummy, rZero, 1);
                    retV.add(rZero);
                }
            }

        }
        return retV;
    }

    /**
     * Fills the fields of the two Results with VO/user summary information.
     * 
     * @param cVOSummary
     *            Information from the current doProcesss().
     * @param oVOSummary
     *            Information from the previous doProcess().
     * @param r
     *            Is filled with current or cumulated values for the parameters.
     * @param r1
     *            Is filled with the values of the rates.
     * @param dtime
     *            The time interval between the previous doProcess() and the
     *            current one.
     */
    private void fillSummaryResults(VOSummaryExt cVOSummary, VOSummaryExt oVOSummary, Result r, Result r1, double dTime) {
        boolean haveCondor = false, havePBS = false, haveSGE = false, haveLSF = false;
        if (jobMgr.get("CONDOR") != null) {
            haveCondor = true;
        }
        if (jobMgr.get("PBS") != null) {
            havePBS = true;
        }
        if (jobMgr.get("SGE") != null) {
            haveSGE = true;
        }
        if (jobMgr.get("LSF") != null) {
            haveLSF = true;
        }

        r.addSet("RunningJobs", cVOSummary.runningjobs);
        r.addSet("IdleJobs", cVOSummary.idlejobs);
        r.addSet("HeldJobs", cVOSummary.heldjobs);
        r.addSet("SubmittedJobs", cVOSummary.submittedJobs);
        // r.addSet("TotalSubmittedJobs", cVOSummary.totalSubmittedJobs);
        r1.addSet("SubmittedJobs_R", getRate(cVOSummary.submittedJobs_t, oVOSummary.submittedJobs_t, dTime));
        // logger.info("### ooold submited: " + oVOSummary.submittedJobs_t +
        // " new: " + cVOSummary.submittedJobs_t);
        r.addSet("FinishedJobs", cVOSummary.finishedJobs);
        // r.addSet("TotalFinishedJobs", cVOSummary.totalFinishedJobs);
        r1.addSet("FinishedJobs_R", getRate(cVOSummary.finishedJobs_t, oVOSummary.finishedJobs_t, dTime));
        // logger.info("### ooold finished: " + oVOSummary.finishedJobs_t +
        // " new: " + cVOSummary.finishedJobs_t);

        /* parameters reported only by Condor */
        if (haveCondor && !havePBS && !haveLSF && !haveSGE) {
            r.addSet("DiskUsage", cVOSummary.disk_usage);
        }

        /* parameters reported by Condor and PBS (if pbsQuickMode is disabled) */
        if ((haveCondor || (havePBS && !pbsQuickMode)) && !haveLSF && !(havePBS && pbsQuickMode) && !haveSGE) {
            r.addSet("RunTime", (cVOSummary.run_time_t - oVOSummary.run_time_t) / (double) MIN_SEC);
            r.addSet("WallClockTime", cVOSummary.run_time_t - oVOSummary.run_time_t);
            r1.addSet("RunTime_R", getRate(cVOSummary.run_time_t, oVOSummary.run_time_t, dTime) / MIN_SEC);
            r1.addSet("WallClockTime_R", getRate(cVOSummary.run_time_t, oVOSummary.run_time_t, dTime));
        }

        if (haveCondor && checkCondorHist && haveCondorHistFiles) {
            /*
             * parameters from the history file... most of them are disabled for
             * the moment
             */
            try {
                Double cpu_usr = cVOSummary.paramsCondorH.get("CondorCpuUsr_t");
                Double cpu_sys = cVOSummary.paramsCondorH.get("CondorCpuSys_t");
                Double ocpu_usr = oVOSummary.paramsCondorH.get("CondorCpuUsr_t");
                Double ocpu_sys = oVOSummary.paramsCondorH.get("CondorCpuSys_t");
                // double CondorCPU = cpu_usr.doubleValue() +
                // cpu_sys.doubleValue();
                // double oCondorCPU = ocpu_usr.doubleValue() +
                // ocpu_sys.doubleValue();
                double cpuUsrDiff = cpu_usr.doubleValue() - ocpu_usr.doubleValue();
                if (cpuUsrDiff < 0) {
                    cpuUsrDiff = 0;
                }
                double cpuSysDiff = cpu_sys.doubleValue() - ocpu_sys.doubleValue();
                if (cpuUsrDiff < 0) {
                    cpuSysDiff = 0;
                }
                r.addSet("CPUUsrCondorHist", cpuUsrDiff);
                r.addSet("CPUSysCondorHist", cpuSysDiff);
            } catch (Exception e) {
                if (dTime > 1.0) {
                    /*
                     * if dTime = 1 we only called this function to send some 0
                     * rates for inactive VOs
                     */
                    logger.log(Level.FINE, "Got exception when getting Condor parameters", e);
                }
            }

            // r.addSet("FinishedJobsCondor", cVOSummary.CondorFinishedJobs);
            // r1.addSet("FinishedJobsCondor_R",
            // getRate(cVOSummary.CondorFinishedJobs,
            // oVOSummary.CondorFinishedJobs, dTime));
            // r.addSet("BytesSent", cVOSummary.CondorBytesSent);
            // r1.addSet("BytesSent_R", getRate(cVOSummary.CondorBytesSent,
            // oVOSummary.CondorBytesSent, dTime));
            // r.addSet("BytesRecvd", cVOSummary.CondorBytesRecvd);
            // r1.addSet("BytesRecvd_R", getRate(cVOSummary.CondorBytesRecvd,
            // oVOSummary.CondorBytesRecvd, dTime));
            // r.addSet("FileReadBytes", cVOSummary.CondorFileReadBytes);
            // r1.addSet("FileReadBytes_R",
            // getRate(cVOSummary.CondorFileReadBytes,
            // oVOSummary.CondorFileReadBytes, dTime));
            // r.addSet("FileWriteBytes", cVOSummary.CondorFileWriteBytes);
            // r1.addSet("FileWriteBytes_R",
            // getRate(cVOSummary.CondorFileWriteBytes,
            // oVOSummary.CondorFileWriteBytes, dTime));
            // r1.addSet("DiskUsageCondor_R",
            // getRate(cVOSummary.CondorDiskUsage, oVOSummary.CondorDiskUsage,
            // dTime));
            // r.addSet("MemoryUsage", cVOSummary.CondorMemoryUsage);
            // r1.addSet("MemoryUsageCondor_R",
            // getRate(cVOSummary.CondorMemoryUsage,
            // oVOSummary.CondorMemoryUsage, dTime));
        }

        /* parameters reported by Condor, PBS and LSF */
        if ((haveLSF || haveCondor || havePBS)
                && (!haveSGE && !(havePBS && !checkPBSHist) && !(haveCondor && !haveCondorHistFiles))) {
            r.addSet("FinishedJobs_Success", cVOSummary.successJobs);
            r1.addSet("FinishedJobs_Success_R", cVOSummary.successJobs / dTime);
            r.addSet("FinishedJobs_Error", cVOSummary.errorJobs);
            r1.addSet("FinishedJobs_Error_R", cVOSummary.errorJobs / dTime);
        }

        /* parameters reported by Condor, LSF and SGE */
        if ((haveCondor || haveSGE || haveLSF) && !havePBS) {
            r.addSet("JobsSize", cVOSummary.size);
        }

        /* parameters reported by Condor, PBS, LSF and SGE */
        if (haveCondor || havePBS || haveSGE || haveLSF) {
            r.addSet("CPUTime", cVOSummary.cpu_time_t - oVOSummary.cpu_time_t);
            r1.addSet("CPUTime_R", getRate(cVOSummary.cpu_time_t, oVOSummary.cpu_time_t, dTime));
        }

        if (cVOSummary.unknownjobs != 0) {
            r.addSet("UnkownJobs", cVOSummary.unknownjobs);
        }

        // in case we want to report the LSF parameters separately uncomment
        // this
        /*
         * Enumeration lsfe = cVOSummary.paramsLSF.keys(); while
         * (lsfe.hasMoreElements()) { String key = (String)lsfe.nextElement();
         * Double cval = (Double)cVOSummary.paramsLSF.get(key); Double oval =
         * (Double)cVOSummary.paramsLSF.get(key); r.addSet(key,
         * cval.doubleValue()); r1.addSet(key, getRate(cval.doubleValue(),
         * oval.doubleValue(), dTime)); }
         */

        double cTotalJobs = cVOSummary.heldjobs + cVOSummary.idlejobs + cVOSummary.runningjobs + cVOSummary.unknownjobs;
        r.addSet("TotalJobs", cTotalJobs);
    }

    /**
     * Calculates rate statistics for jobs.
     * 
     * @return Vector with Results containing the rates.
     */
    private Vector<Object> getJobDiff() {
        Vector<Object> retV = new Vector<Object>();
        for (Enumeration<String> it = jobsInfo.keys(); it.hasMoreElements();) {
            String oldJobID = it.nextElement();
            JobInfoExt oldJobInfo = jobsInfo.get(oldJobID);
            JobInfoExt crtJobInfo = currentJobsInfo.get(oldJobID);

            /* check if the job belongs now to a different VO */
            boolean bVOChanged = false;
            if ((oldJobInfo.VO != null) && (crtJobInfo != null)) {
                if (!oldJobInfo.VO.equals(crtJobInfo.VO)) {
                    bVOChanged = true;
                }
            }

            if ((crtJobInfo == null) || bVOChanged) {// finished job or VO changed
                /* remove the job from the old VO cluster */
                eResult r = new eResult();
                if (mixedCaseVOs) {
                    r.ClusterName = oldJobInfo.VO + "_JOBS_OSG";
                } else {
                    r.ClusterName = oldJobInfo.VO.toUpperCase() + "_JOBS_OSG";
                }

                r.FarmName = farmName;
                r.NodeName = oldJobInfo.id;
                r.time = NTPDate.currentTimeMillis();
                r.param = null;
                r.param_name = null;
                r.Module = ModuleName;
                retV.add(r);
            }

            if (crtJobInfo == null) { // finished job
                /* count this job as finished for the VO and for the user */
                VOSummaryExt vos = currentVOjobsInfo.get(oldJobInfo.VO);
                if (vos == null) {
                    vos = new VOSummaryExt();
                }
                vos.finishedJobs += 1;
                currentVOjobsInfo.put(oldJobInfo.VO, vos);

                VOSummaryExt userSumm = currentUserJobsInfo.get(oldJobInfo.user);
                if (userSumm == null) {
                    userSumm = new VOSummaryExt();
                }
                userSumm.finishedJobs += 1;
                currentUserJobsInfo.put(oldJobInfo.user, userSumm);

                VOSummaryExt oldvos = VOjobsInfo.get(oldJobInfo.VO);
                if (oldvos == null) {
                    logger.log(Level.WARNING, "Job finished - Unknown VO!");
                } else {
                    // oldvos.run_time_t -= oldJobInfo.run_time;
                    oldvos.size -= oldJobInfo.size;
                }

                VOSummaryExt oldUserSumm = userJobsInfo.get(oldJobInfo.user);
                if (oldUserSumm == null) {
                    logger.log(Level.WARNING, "Job finished - Unknown user account!");
                } else {
                    // oldUserSumm.run_time_t -= oldJobInfo.run_time;
                    oldUserSumm.size -= oldJobInfo.size;
                }

            }
        }

        return retV;
    }

    /**
     * Collects information from the available job managers.
     * 
     * @param fjResults
     *            Vector with additional results for the finished jobs (this is
     *            an output parameter).
     * @return
     * @throws Exception
     */
    private boolean collectJobMgrData(Vector<Result> fjResults) throws Exception {
        Vector<JobInfoExt> condorInfo = null, pbsInfo = null, sgeInfo = null, lsfInfo = null;
        boolean condorqFirstError = false;
        boolean pbsHistChecked = false;
        long t_cnt;

        this.inconsistentJobInfo = false;

        // ---- check the sanity of the environment ----
        if (jobMgr.isEmpty()) {
            throw new Exception("There are no valid job queue managers to use.");
        }

        // reset the execution times counters
        Enumeration<String> etKeys = execTimes.keys();
        while (etKeys.hasMoreElements()) {
            String sKey = etKeys.nextElement();
            execTimes.put(sKey, Long.valueOf(0));
        }

        // --- query the queue managers and get the output of the commands
        // ------
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Start querying the job managers...");
        }
        this.haveSuccessfulCommand = false;

        // perform 2 steps: in the first one we execute the usual commands and
        // in the second one we execute condor_q -format if condor_q failed
        for (int step = 1; step <= 2; step++) {
            for (Entry<String, String> entry : jobMgr.entrySet()) {
                String jobManager = null;
                BufferedReader[] cmdStreams = null;
                BufferedReader[] cmdStreams2 = null;

                jobManager = entry.getKey();
                StringBuilder erOutput = null, erOutput2 = null;

                long t1 = System.currentTimeMillis();
                try {
                    if (step == 1) {
                        if (jobManager.equals(VO_Utils.CONDOR2) || jobManager.equals(VO_Utils.PBS2)) {
                            // the additional commands will be processed
                            // together with the base ones
                            continue;
                        }
                        if (jobManager.equals(VO_Utils.CONDOR3) && (condorFormatOption != ON)
                                && !((condorFormatOption == ALTERNATIVE) && failedCondorQ)) {
                            continue;
                        }
                        if (jobManager.equals(VO_Utils.CONDOR)
                                && ((condorFormatOption == ON) || ((condorFormatOption == ALTERNATIVE) && failedCondorQ))) {
                            continue;
                        }
                    } else {
                        if (!jobManager.equals(VO_Utils.CONDOR3)) {
                            continue;
                        }
                    }

                    int nCommandsToCheck;
                    if (jobManager.startsWith(VO_Utils.CONDOR)) {
                        nCommandsToCheck = nCondorCommands;
                    } else {
                        nCommandsToCheck = 1;
                    }

                    String cmd1 = entry.getValue();

                    cmdStreams = procCmdStreams(cmd1, CMD_DELAY);

                    /* get the error output (only the first 20 lines) */
                    // errOutput = VO_Utils.getFirstLines(cmdStreams[1], 20);

                    if (cmdStreams[0] == null) {
                        Integer ierr = (VO_Utils.jobMgrNullErrors.get(jobManager));
                        this.errorCode = ierr.intValue();
                        countNewError(this.errorCode);
                        String nullErrMsg = VO_Utils.voJobsErrCodes.get(ierr);
                        nullErrMsg += "; Ignoring output from " + jobManager;
                        erOutput = VO_Utils.getFirstLines(cmdStreams[1], 20);
                        if (erOutput != null) {
                            nullErrMsg += ("\n Error output of the command: \n" + erOutput);
                        }

                        MLLogEvent<String, Object> mlle = new MLLogEvent<String, Object>();
                        mlle.logParameters.put("Error Code", Integer.valueOf(this.errorCode));
                        mlle.logParameters.put("Error Message", nullErrMsg);
                        logger.log(crtLevel[this.errorCode], nullErrMsg, new Object[] { mlle });
                        if (isInternalModule && crtLevel[this.errorCode].equals(Level.WARNING)) {
                            sendExceptionEmail("Job manager error:\n" + nullErrMsg);
                        }
                        continue;
                    }

                    if (jobManager.equals(VO_Utils.CONDOR) || jobManager.equals(VO_Utils.CONDOR3)) {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "Collecting Condor data...");
                        }
                        // get information from the condor_q command
                        try {
                            if (jobManager.equals(VO_Utils.CONDOR)) {
                                condorInfo = condorAcc.parseCondorQLongOutput(cmdStreams[0], nCommandsToCheck,
                                        checkExitStatus);
                                if (condorAcc.canUseCondorQ == false) {
                                    this.condorQuickMode = true;
                                }
                            } else {
                                if (logger.isLoggable(Level.FINE)) {
                                    logger.fine("Using condor_q with -format...");
                                }
                                condorInfo = condorAcc.parseCondorQFormatOutput(cmdStreams[0], nCommandsToCheck,
                                        checkExitStatus);
                            }

                            if (jobManager.equals(VO_Utils.CONDOR) && !condorQuickMode) {
                                String cmd2 = jobMgr.get(VO_Utils.CONDOR2);
                                /*
                                 * get the additional information from plain
                                 * condor_q
                                 */
                                cmdStreams2 = procCmdStreams(cmd2);
                                // errOutput2 =
                                // VO_Utils.getFirstLines(cmdStreams2[1], 20);

                                if (cmdStreams2[0] == null) {
                                    Integer ierr = (VO_Utils.jobMgrNullErrors.get(VO_Utils.CONDOR2));
                                    this.errorCode = ierr.intValue();
                                    countNewError(this.errorCode);
                                    String errMsg = "No output was obtained from the plain condor_q command";
                                    erOutput2 = VO_Utils.getFirstLines(cmdStreams2[1], 20);
                                    if (erOutput2 != null) {
                                        errMsg += ("\n Error output from command: " + erOutput2);
                                    }
                                    logger.log(crtLevel[this.errorCode], errMsg);
                                } else {
                                    Hashtable<String, JobInfoExt> q2Info = condorAcc.parseCondorQOutput(cmdStreams2[0],
                                            nCommandsToCheck, checkExitStatus);

                                    /*
                                     * replace the run time obtained from
                                     * condor_q -l with the run time obtained
                                     * from plain condor_q
                                     */
                                    if (logger.isLoggable(Level.FINE)) {
                                        logger.log(Level.FINE, "Getting additional info from condor_q...");
                                    }
                                    if (q2Info != null) {
                                        for (int qi = 0; qi < condorInfo.size(); qi++) {
                                            JobInfoExt jInfo = (condorInfo.get(qi));
                                            JobInfoExt j2Info = q2Info.get(jInfo.id);
                                            if (j2Info != null) {
                                                jInfo.run_time = j2Info.run_time;
                                                // logger.info("### assigned runtime: "
                                                // + jInfo.run_time);
                                            }
                                        }
                                    } // if (q2Info != null)
                                }
                            } // if (!condorQuickMode)

                        } catch (Throwable t) {
                            long t2 = System.currentTimeMillis();
                            execTimes.put(VO_Utils.CONDOR, Long.valueOf(t2 - t1));
                            if (logger.isLoggable(Level.FINE)) {
                                logger.fine("Cmd execution time: " + (t2 - t1) + "; max delay: " + CMD_DELAY);
                            }

                            if ((t2 - t1) >= CMD_DELAY) {
                                this.errorCode = VO_Utils.INTERNAL_JMGR_TIMEOUT;
                            } else if (t instanceof ModuleException) {
                                this.errorCode = ((ModuleException) t).getCode();
                            } else {
                                // logger.info("### got condor err code");
                                this.errorCode = VO_Utils.jobMgrParseErrors.get(VO_Utils.CONDOR).intValue();
                            }

                            /*
                             * if both condor_q and condor_q -format fail, don't
                             * keep -format as the default option
                             */
                            if ((step == 2) && jobManager.equals(VO_Utils.CONDOR3)
                                    && (condorFormatOption == ALTERNATIVE) && failedCondorQ) {
                                failedCondorQ = false;
                            }

                            if ((step == 1) && (condorFormatOption == ALTERNATIVE)) {
                                MLLogEvent<String, Object> mlle = new MLLogEvent<String, Object>();
                                Integer iErrCode = Integer.valueOf(this.errorCode);
                                String sErrMsg = t.getMessage(); // (String)VO_Utils.voJobsErrCodes.get(iErrCode);
                                erOutput = VO_Utils.getFirstLines(cmdStreams[1], 20);
                                if (erOutput != null) {
                                    sErrMsg += ("\n Error output from the command: \n" + erOutput);
                                }
                                mlle.logParameters.put("Error Code", iErrCode);
                                mlle.logParameters.put("CmdExecTime", Long.valueOf(t2 - t1));
                                if (sErrMsg != null) {
                                    mlle.logParameters.put("Error message", new String(sErrMsg));
                                }
                                logger.log(crtLevel[this.errorCode], "Exception parsing the output of condor_q",
                                        new Object[] { mlle, t });
                                logger.log(crtLevel[this.errorCode], sErrMsg);
                                logger.log(crtLevel[this.errorCode], "Trying condor_q -format....");
                                condorqFirstError = true;
                                this.failedCondorQ = true;

                                continue;
                            }
                            throw t;
                        }

                        // get information from the Condor history file
                        if (this.checkCondorHist) {
                            if (!this.haveCondorHistFiles && ((execCnt % 10) == 0)) {
                                logger.info("Attempting to find Condor history files...");
                                this.initCondorHistoryFiles();

                                if (this.haveCondorHistFiles) {
                                    condorAcc = new VO_CondorAccounting(condorHistFiles);
                                }
                            }
                            if (this.haveCondorHistFiles) {
                                Vector<Hashtable<String, Object>> histInfo = condorAcc.getHistoryInfo();
                                if (histInfo == null) {
                                    logger.log(Level.WARNING, "Disabling the scan of the Condor history log...");
                                    this.checkCondorHist = false;
                                    this.errorCode = VO_Utils.jobMgrLogErrors.get(VO_Utils.CONDOR).intValue();
                                } else {
                                    for (int hi = 0; hi < histInfo.size(); hi++) {
                                        Hashtable<String, Object> hJobInfo = histInfo.get(hi);
                                        Vector<Result> fjCondor = updateJobSummariesCondorHist(hJobInfo);
                                        fjResults.addAll(fjCondor);
                                    }
                                }
                            } // if (this.haveCondorHistFile)
                        }
                    } else if (jobManager.equals(VO_Utils.PBS)) {
                        logger.log(Level.FINE, "Collecting PBS data...");
                        // get information from the qstat command
                        pbsInfo = VO_PBSAccounting.parsePBSOutput(cmdStreams[0], nCommandsToCheck, checkExitStatus);
                        // processJobsInfo(pbsInfo, jobManager);

                        if (!pbsQuickMode) {
                            String cmd2 = jobMgr.get(VO_Utils.PBS2);
                            /* get the additional information from qstat -a */
                            cmdStreams2 = procCmdStreams(cmd2);
                            // errOutput2 =
                            // VO_Utils.getFirstLines(cmdStreams2[1], 20);

                            if (cmdStreams2[0] == null) {
                                Integer ierr = (VO_Utils.jobMgrNullErrors.get(VO_Utils.PBS2));
                                this.errorCode = ierr.intValue();
                                String errMsg = "No output was obtained from the qstat -a command";
                                erOutput2 = VO_Utils.getFirstLines(cmdStreams2[1], 20);
                                if (erOutput2 != null) {
                                    errMsg += ("\n Error output from command: \n" + erOutput2);
                                }
                                logger.log(Level.WARNING, errMsg);
                            } else {
                                Hashtable<String, JobInfoExt> q2Info = VO_PBSAccounting.parseQstatAOutput(
                                        cmdStreams2[0], nCommandsToCheck, checkExitStatus);

                                /* use the run time obtained from qstat -a */
                                logger.log(Level.FINE, "Getting additional info from qstat -a...");
                                if (q2Info != null) {
                                    for (int qi = 0; qi < pbsInfo.size(); qi++) {
                                        JobInfoExt jInfo = (pbsInfo.get(qi));
                                        Enumeration<String> q2Keys = q2Info.keys();
                                        while (q2Keys.hasMoreElements()) {
                                            String q2Key = q2Keys.nextElement();
                                            if (q2Key.startsWith(jInfo.id)) {

                                                JobInfoExt j2Info = q2Info.get(q2Key);
                                                if (j2Info != null) {
                                                    jInfo.run_time = j2Info.run_time;
                                                }
                                            } // if
                                        } // while

                                    }
                                } // if (q2Info != null)
                            }
                        } // if (!pbsQuickMode)

                        // get information from the PBS acounting logs
                        if (this.checkPBSHist && !pbsHistChecked) {
                            pbsHistChecked = true;
                            Vector<Hashtable<String, Object>> pbsHistInfo = pbsAcc.getHistoryInfo();
                            if (pbsHistInfo == null) {
                                logger.log(Level.WARNING, "Disabling the scan of the PBS logs...");
                                this.checkPBSHist = false;
                                this.errorCode = VO_Utils.jobMgrLogErrors.get(VO_Utils.PBS).intValue();
                            } else {
                                for (int hi = 0; hi < pbsHistInfo.size(); hi++) {
                                    Hashtable<String, Object> hJobInfo = pbsHistInfo.get(hi);
                                    Vector<Result> fjPBS = updateVoSummaryPBSHist(hJobInfo);
                                    fjResults.addAll(fjPBS);
                                }
                            }
                        } // if (checkPBSHist)
                    } else if (jobManager.equals(VO_Utils.LSF)) {
                        logger.log(Level.FINE, "Collecting LSF data...");
                        // get information from the condor_q command
                        lsfInfo = VO_LSFAccounting.parseLSFOutput(cmdStreams[0], nCommandsToCheck, checkExitStatus);
                        // processJobsInfo(lsfInfo, jobManager);
                    } else if (jobManager.equals(VO_Utils.FBS)) {
                        // ParseFBSOutput( buff1 );
                    } else if (jobManager.equals(VO_Utils.SGE)) {
                        logger.log(Level.FINEST, "[monOsgVoJobs] Collecting SGE data...");
                        // get information from the qstat command
                        sgeInfo = VO_SGEAccounting.parseSGEOutput(cmdStreams[0], nCommandsToCheck, checkExitStatus);
                        // processJobsInfo(sgeInfo, jobManager);
                    } else {
                        continue;
                    }

                    erOutput = VO_Utils.getFirstLines(cmdStreams[1], 20);
                    if (erOutput != null) {
                        logger.log(Level.FINE, "Error output from " + jobManager + ": \n" + erOutput);
                    }
                    t_cnt = System.currentTimeMillis();
                    execTimes.put(jobManager, Long.valueOf(t_cnt - t1));
                    this.haveSuccessfulCommand = true;
                } catch (Throwable t) {
                    long t2 = System.currentTimeMillis();
                    execTimes.put(jobManager, Long.valueOf(t2 - t1));
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("Cmd execution time: " + (t2 - t1) + "; max delay: " + CMD_DELAY);
                    }

                    if ((t2 - t1) >= CMD_DELAY) {
                        this.errorCode = VO_Utils.INTERNAL_JMGR_TIMEOUT;
                        // logger.log(Level.WARNING,
                        // "Got timeout when executing command...");
                    } else if (t instanceof ModuleException) {
                        this.errorCode = ((ModuleException) t).getCode();
                    } else {
                        this.errorCode = VO_Utils.jobMgrParseErrors.get(jobManager).intValue();
                    }

                    StringWriter sw = new StringWriter();
                    t.printStackTrace(new PrintWriter(sw));

                    countNewError(this.errorCode);
                    // logger.info("### count " + this.errorCode + " " +
                    // errCount[errorCode] + " level: " + crtLevel[errorCode]);
                    MLLogEvent<String, Object> mlle = new MLLogEvent<String, Object>();
                    Integer iErrCode = Integer.valueOf(this.errorCode);
                    String sErrMsg = VO_Utils.voJobsErrCodes.get(iErrCode);
                    if (cmdStreams != null) {
                        erOutput = VO_Utils.getFirstLines(cmdStreams[1], 20);
                        if (erOutput != null) {
                            if (sErrMsg == null) {
                                sErrMsg = new String();
                            }
                            sErrMsg += ("\n Error output from job manager command: \n" + erOutput);
                        }
                    }

                    if (cmdStreams2 != null) {
                        erOutput2 = VO_Utils.getFirstLines(cmdStreams2[1], 20);
                        if (erOutput2 != null) {
                            if (sErrMsg == null) {
                                sErrMsg = new String();
                            }
                            sErrMsg += ("\n Error output from job manager additional command: \n" + erOutput2);
                        }
                    }
                    mlle.logParameters.put("Error Code", iErrCode);
                    if (sErrMsg != null) {
                        mlle.logParameters.put("Error message", new String(sErrMsg));
                    }
                    mlle.logParameters.put("CmdExecTime", Long.valueOf(t2 - t1));

                    logger.log(crtLevel[this.errorCode], "Error in collectJobMgrData: ", new Object[] { mlle, t });
                    logger.log(crtLevel[this.errorCode], sErrMsg, sw.toString());

                    // this.cmdErrCnt = (this.cmdErrCnt + 1) % ERR_CNT_PERIOD;
                    if (isInternalModule && crtLevel[this.errorCode].equals(Level.WARNING)) {
                        sendExceptionEmail("Job manager error:\n" + sErrMsg + "\n Exception stacktrace: \n"
                                + sw.toString());
                        // throw new Exception("collectJobMgrData - " +
                        // sw.toString()) ;
                    }
                } // end try/catch
            } // end of for (jobMgr...)

            if (condorqFirstError) {
                if (condorFormatOption != OFF) {
                    condorqFirstError = false;
                    continue;
                }
                break;
            }
            // this.cmdErrCnt = 0;
            break;
        } // end of for (step...)

        if (!this.haveSuccessfulCommand) {
            return false;
        }

        if (condorInfo != null) {
            processJobsInfo(condorInfo, VO_Utils.CONDOR);
        }
        if (pbsInfo != null) {
            processJobsInfo(pbsInfo, VO_Utils.PBS);
        }
        if (sgeInfo != null) {
            processJobsInfo(sgeInfo, VO_Utils.SGE);
        }
        if (lsfInfo != null) {
            processJobsInfo(lsfInfo, VO_Utils.LSF);
        }

        return true;
    } // end method

    /**
     * Wrapper that takes the job information obtained from a queue manager and
     * updates the VO statistics.
     * 
     * @param qInfo
     *            Holds information for the jobs that are currently in the
     *            queue.
     * @param jobManager
     *            The name of the job manager.
     */
    void processJobsInfo(Vector<JobInfoExt> qInfo, String jobManager) {
        Hashtable<String, Integer> htJobIds = new Hashtable<String, Integer>(); // for tracking duplicate IDs
        ArrayList<String> vDuplicateIds = new ArrayList<String>();

        Iterator<JobInfoExt> qIt = qInfo.iterator();
        while (qIt.hasNext()) {
            JobInfoExt jobInfo = qIt.next();

            jobInfo.VO = getVo(jobInfo.user);
            if (jobInfo.VO == null) { // this account is not mapped to any VO
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Found job for user " + jobInfo.user + ", which does not belong to any VO");
                }

                if (hmNoVoUsers.size() < MAX_NO_VO_STATUS_MAP) {
                    long now = NTPDate.currentTimeMillis();
                    hmNoVoUsers.put(jobInfo.user, jobInfo.id + " @ [ " + now + " / " + new Date(now) + " ]");
                }

                if (this.noVoStatistics) {
                    jobInfo.VO = noVoName;
                } else {
                    // skip this job
                    continue;
                }
            }

            Integer dupId = htJobIds.get(jobInfo.id);
            if (dupId != null) {
                vDuplicateIds.add(jobInfo.id);
            } else {
                htJobIds.put(jobInfo.id, Integer.valueOf(0));
            }
            /*
             * the queue manager may report a non-zero job size even if the job
             * has not started yet
             */
            if ((jobInfo.cpu_time == 0) && (jobInfo.run_time == 0)) {
                jobInfo.size = 0;
            }

            /*
             * if we have a new job we must update the number of submitted jobs
             */
            boolean haveNewJob = false;
            if (jobsInfo.get(jobInfo.id) == null) {
                haveNewJob = true;
            }

            boolean haveOldFinishedJob = false;
            if (!jobInfo.status.equals("F")) {
                currentJobsInfo.put(jobInfo.id, jobInfo);
            } else {
                currentFinishedJobsInfo.put(jobInfo.id, jobInfo);
                if (finishedJobsInfo.containsKey(jobInfo.id)) {
                    haveOldFinishedJob = true;
                }
            }

            if (!haveOldFinishedJob) {
                try {
                    updateJobSummaries(jobInfo, haveNewJob);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("Got " + jobManager + " job: " + jobInfo);
                    }
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "updateJobSummaries() got exception: ", ex);
                    inconsistentJobInfo = true;
                }
            }
        }

        if (vDuplicateIds.size() > 0) {
            Integer iErrCode = VO_Utils.jobMgrDuplicateIds.get(jobManager);
            this.errorCode = iErrCode.intValue();
            countNewError(this.errorCode);
            MLLogEvent<String, Object> mlle = new MLLogEvent<String, Object>();
            String sErrMsg = VO_Utils.voJobsErrCodes.get(iErrCode);
            mlle.logParameters.put("Error Code", iErrCode);
            mlle.logParameters.put("Duplicate IDs", vDuplicateIds);

            if (sErrMsg != null) {
                mlle.logParameters.put("Error message", sErrMsg);
            }

            logger.log(crtLevel[this.errorCode], "Found duplicate job ID(s): " + vDuplicateIds.toString(),
                    new Object[] { mlle });
        }

    }

    /**
     * Updates the current VO statistics with information about a job.
     * 
     * @param jobInfo
     *            Job information that will be added to the VO statistics.
     */
    private void updateJobSummaries(JobInfoExt jobInfo, boolean haveNewJob) throws Exception {
        if (!this.activeVos.contains(jobInfo.VO)) {
            this.activeVos.add(jobInfo.VO);
        }
        if (!this.activeUsers.contains(jobInfo.user)) {
            this.activeUsers.add(jobInfo.user);
        }

        /* update the statistics that count the number of jobs */
        VOSummaryExt vos = currentVOjobsInfo.get(jobInfo.VO);
        if (vos == null) {
            vos = new VOSummaryExt();
        }
        updateCountStatistics(vos, jobInfo, haveNewJob);

        VOSummaryExt userSumm = currentUserJobsInfo.get(jobInfo.user);
        if (userSumm == null) {
            userSumm = new VOSummaryExt();
        }
        updateCountStatistics(userSumm, jobInfo, haveNewJob);

        /* update the run time / cpu time statistics */
        JobInfoExt oldInfo = jobsInfo.get(jobInfo.id);
        if (oldInfo == null) { // new job, we add all the run/cpu time
            vos.run_time_t += jobInfo.run_time;
            vos.cpu_time_t += jobInfo.cpu_time;
            userSumm.run_time_t += jobInfo.run_time;
            userSumm.cpu_time_t += jobInfo.cpu_time;
        } else {
            /* copy the status information from the old job record */
            jobInfo.error_status_cpu = oldInfo.error_status_cpu;
            jobInfo.error_status_run = oldInfo.error_status_run;
            jobInfo.cpu_time_old = oldInfo.cpu_time_old;
            jobInfo.run_time_old = oldInfo.run_time_old;
            if (jobInfo.status.equals("F") && (jobInfo.cpu_time < oldInfo.cpu_time)) {
                jobInfo.cpu_time = oldInfo.cpu_time;
            }
            if (jobInfo.status.equals("F") && (jobInfo.run_time < oldInfo.run_time)) {
                jobInfo.run_time = oldInfo.run_time;
            }

            long crt_run_time = jobInfo.run_time;
            long crt_cpu_time = jobInfo.cpu_time;
            long old_run_time = oldInfo.run_time;
            long old_cpu_time = oldInfo.cpu_time;
            boolean haveInconsistency = false;

            if (jobInfo.run_time < oldInfo.run_time) {
                /*
                 * Condor sometimes reports decreasing CPU/run time for
                 * suspended jobs
                 */
                haveInconsistency = true;
                if (this.adjustJobStatistics) {
                    jobInfo.error_status_run = JobInfoExt.JOB_MODIFIED;
                    jobInfo.run_time_old += oldInfo.run_time;

                } else {
                    jobInfo.error_status_run = JobInfoExt.JOB_SUSPENDED;
                    jobInfo.run_time_old = oldInfo.run_time;
                }
            }

            if (jobInfo.cpu_time < oldInfo.cpu_time) {
                haveInconsistency = true;
                if (this.adjustJobStatistics) {
                    jobInfo.cpu_time_old += oldInfo.cpu_time;
                    jobInfo.error_status_cpu = JobInfoExt.JOB_MODIFIED;
                } else {
                    jobInfo.cpu_time_old = oldInfo.cpu_time;
                    jobInfo.error_status_cpu = JobInfoExt.JOB_SUSPENDED;
                }
            }

            if (haveInconsistency) {
                this.errorCode = VO_Utils.INTERNAL_JMGR_INCONSIST;
                countNewError(this.errorCode);
                String errMsg = "*** Decreasing run time or CPU time for job " + jobInfo.id + "( cpu time: "
                        + jobInfo.cpu_time + ", old cpu time: " + oldInfo.cpu_time + " ; run time:  "
                        + jobInfo.run_time + ", old run time: " + oldInfo.run_time;

                if (mlleInconsist.logParameters.size() < 20) {
                    mlleInconsist.logParameters.put(new String(jobInfo.id), errMsg);
                }
                logger.log(crtLevel[this.errorCode], errMsg);
            }

            if (jobInfo.error_status_run == JobInfoExt.JOB_SUSPENDED) {
                old_run_time = jobInfo.run_time_old;
                if (jobInfo.run_time >= jobInfo.run_time_old) {
                    logger.fine("*** Job unsuspended: " + jobInfo.id);
                    jobInfo.error_status_run = JobInfoExt.JOB_OK;
                    crt_run_time = jobInfo.run_time;

                } else {
                    crt_run_time = jobInfo.run_time_old;
                    countNewError(VO_Utils.INTERNAL_JMGR_INCONSIST);
                    if ((oldInfo.run_time == 0) && (jobInfo.run_time > 0)) {
                        String errMsg = "*** Inconsistent run time for suspended job " + jobInfo.id + "( cpu time: "
                                + jobInfo.cpu_time + ", old cpu time: " + oldInfo.cpu_time
                                + ", cpu time before suspension: " + jobInfo.cpu_time_old + " ; run time:  "
                                + jobInfo.run_time + ", old run time: " + oldInfo.run_time + " )";

                        if (mlleInconsist.logParameters.size() < 20) {
                            mlleInconsist.logParameters.put(new String(jobInfo.id), errMsg);
                        }
                        logger.log(crtLevel[VO_Utils.INTERNAL_JMGR_INCONSIST], errMsg);
                    }
                }
            }
            if (jobInfo.error_status_run == JobInfoExt.JOB_MODIFIED) {
                logger.finest("*** Job which had decreasing CPU time: " + jobInfo.id);
                crt_run_time = jobInfo.run_time + jobInfo.run_time_old;
                old_run_time = oldInfo.run_time + oldInfo.run_time_old;
            }

            if (jobInfo.error_status_cpu == JobInfoExt.JOB_SUSPENDED) {
                old_cpu_time = jobInfo.cpu_time_old;
                if (jobInfo.cpu_time >= jobInfo.cpu_time_old) {
                    logger.fine("*** Job unsuspended: " + jobInfo.id);
                    jobInfo.error_status_cpu = JobInfoExt.JOB_OK;
                    crt_cpu_time = jobInfo.cpu_time;
                } else {
                    crt_cpu_time = jobInfo.cpu_time_old;
                    countNewError(VO_Utils.INTERNAL_JMGR_INCONSIST);
                    if ((oldInfo.cpu_time == 0) && (jobInfo.cpu_time > 0)) {
                        String errMsg = "*** Inconsistent CPU time for suspended job " + jobInfo.id + "( cpu time: "
                                + jobInfo.cpu_time + ", old cpu time: " + oldInfo.cpu_time
                                + ", cpu time before suspension:  " + jobInfo.cpu_time_old + "; run time:  "
                                + jobInfo.run_time + ", old run time: " + oldInfo.run_time + " )";

                        if (mlleInconsist.logParameters.size() < 20) {
                            mlleInconsist.logParameters.put(jobInfo.id, errMsg);
                        }
                        logger.log(crtLevel[VO_Utils.INTERNAL_JMGR_INCONSIST], errMsg);
                    }
                }
            }
            if (jobInfo.error_status_cpu == JobInfoExt.JOB_MODIFIED) {
                crt_cpu_time = jobInfo.cpu_time + jobInfo.cpu_time_old;
                old_cpu_time = oldInfo.cpu_time + oldInfo.cpu_time_old;
            }
            // logger.finest("### crt cpu time " + crt_cpu_time +
            // " old cpu time " + old_cpu_time);
            // logger.finest("### crt run time " + crt_run_time +
            // " old run time " + old_run_time);
            vos.run_time_t += (crt_run_time - old_run_time);
            vos.cpu_time_t += (crt_cpu_time - old_cpu_time);
            userSumm.run_time_t += (crt_run_time - old_run_time);
            userSumm.cpu_time_t += (crt_cpu_time - old_cpu_time);

            jobsInfo.put(jobInfo.id, jobInfo);
        }

        if (jobInfo.status != "F") {
            vos.size += jobInfo.size;
            vos.disk_usage += jobInfo.disk_usage;
            userSumm.size += jobInfo.size;
            userSumm.disk_usage += jobInfo.disk_usage;
        }

        // in case we want separate statistics for LSF, uncomment this
        /*
         * if (jobInfo.jobManager.equals("LSF")) { Double mem =
         * (Double)vos.paramsLSF.get("MemoryUsageLSF");
         * vos.paramsLSF.put("MemoryUsageLSF", Double.valueOf(mem.doubleValue()
         * + jobInfo.size));
         * Double swap = (Double)vos.paramsLSF.get("SwapUsageLSF");
         * vos.paramsLSF.put("SwapUsageLSF", Double.valueOf(swap.doubleValue() +
         * jobInfo.swap));
         * Double cpu = (Double)vos.paramsLSF.get("CPUUsageLSF");
         * vos.paramsLSF.put("CPUUsageLSF", Double.valueOf(mem.doubleValue() +
         * jobInfo.cpu_time));
         * Double runtime = (Double)vos.paramsLSF.get("CPUTimeLSF");
         * vos.paramsLSF.put("CPUTimeLSF", Double.valueOf(mem.doubleValue() +
         * jobInfo.size)); }
         */

        currentVOjobsInfo.put(jobInfo.VO, vos);
        currentUserJobsInfo.put(jobInfo.user, userSumm);
    }

    /**
     * Updates the statistics for a user of VO with the job given as parameter
     * 
     * @param vos
     *            VO or user statistics information
     * @param jobInfo
     *            Information for the current job
     * @param haveNewJob
     *            This flag is true if the job is new
     */
    protected void updateCountStatistics(VOSummaryExt vos, JobInfoExt jobInfo, boolean haveNewJob) {

        // F - finished
        if (jobInfo.status.equals("H")) {
            vos.heldjobs += 1;
        } else if (jobInfo.status.equals("R")) {
            vos.runningjobs += 1;
        } else if (jobInfo.status.equals("I")) {
            vos.idlejobs += 1;
        } else if (jobInfo.status.equals("F")) {
            if (!(jobInfo.jobManager.equals(VO_Utils.PBS) && checkPBSHist)) {
                // don't count the PBS finished jobs if we scan
                // the PBS logs, because they will appear twice
                if (haveNewJob) { // this job is not in currentJobsInfo and we
                                  // have to count it
                    vos.finishedJobs++;
                    vos.submittedJobs++;
                }

                if (jobInfo.exit_status == 0) {
                    vos.successJobs++;
                } else {
                    vos.errorJobs++;
                }
            }
        } else {
            vos.unknownjobs += 1;
        }

        if (haveNewJob && !jobInfo.status.equals("F")) {
            vos.submittedJobs++;
        }
    }

    /**
     * Updates the current VO and user statistics with information about a
     * finished job obtained from the Condor history file.
     * 
     * @param hJobInfo
     *            Job information that will be added to the VO statistics.
     */
    protected Vector<Result> updateJobSummariesCondorHist(Hashtable<String, Object> hJobInfo) {
        String user = (String) hJobInfo.get("sOwner");
        String jobVo = getVo(user);
        double jobCpuUsr = 0, jobCpuSys = 0, jobRuntime = 0;

        Vector<Result> vret = new Vector<Result>();

        if (jobVo == null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("[monOsgVoJobs] no VO for user " + user);
            }
            jobVo = noVoName;
        }

        /* search for this job in the list of jobs from the previous doProcess */
        Long lCluster = (Long) hJobInfo.get("lClusterId");
        Long lProc = (Long) hJobInfo.get("lProcId");
        String clusterId = lCluster.toString();
        String procId = lProc.toString();
        String scheddName = (String) hJobInfo.get("sScheddName");
        if (scheddName == null) {
            logger.log(Level.WARNING,
                    "Unable to determine the machine's hostname, ignoring Condor history record for job" + clusterId);
            return vret;
        }
        String cJobId = "CONDOR_" + clusterId + "." + procId + "_" + scheddName;

        JobInfoExt oldInfo = jobsInfo.get(cJobId);

        try {
            VOSummaryExt vos = currentVOjobsInfo.get(jobVo);
            if (vos == null) {
                vos = new VOSummaryExt();
            }
            VOSummaryExt userSumm = currentUserJobsInfo.get(user);
            if (userSumm == null) {
                userSumm = new VOSummaryExt();
            }

            /*
             * update the Condor specific parameters in the VO and user
             * summaries
             */
            updateSummaryCondorHist(vos, hJobInfo);
            updateSummaryCondorHist(userSumm, hJobInfo);

            /* update other parameters in the VO and user summaries */
            Double remoteCpuUsr = (Double) hJobInfo.get("dRemoteUserCpu");
            Double localCpuUsr = (Double) hJobInfo.get("dLocalUserCpu");
            if ((remoteCpuUsr != null) && (localCpuUsr != null)) {
                jobCpuUsr = remoteCpuUsr.doubleValue() + localCpuUsr.doubleValue();
            }

            Double remoteCpuSys = (Double) hJobInfo.get("dRemoteSysCpu");
            Double localCpuSys = (Double) hJobInfo.get("dLocalSysCpu");
            if ((remoteCpuSys != null) && (localCpuSys != null)) {
                jobCpuSys = remoteCpuSys.doubleValue() + localCpuSys.doubleValue();
            }

            Double runtime = (Double) hJobInfo.get("dRemoteWallClockTime");
            if (runtime != null) {
                jobRuntime = runtime.doubleValue();
            }

            if (oldInfo != null) {
                if ((jobCpuUsr + jobCpuSys) >= oldInfo.cpu_time) {
                    oldInfo.cpu_time = (long) (jobCpuUsr + jobCpuSys);
                    vos.cpu_time_t += ((jobCpuUsr + jobCpuSys) - oldInfo.cpu_time);
                    userSumm.cpu_time_t += ((jobCpuUsr + jobCpuSys) - oldInfo.cpu_time);
                } else if ((jobCpuUsr + jobCpuSys + 0.0001) < oldInfo.cpu_time) {
                    logger.info("CPU time for Condor job " + clusterId + " has inconsistent values: "
                            + (jobCpuUsr + jobCpuSys) + " in the history log and " + oldInfo.cpu_time + " in condor_q");
                }
                if (jobRuntime > oldInfo.run_time) {
                    oldInfo.run_time = (long) jobRuntime;
                    vos.run_time_t += (jobRuntime - oldInfo.run_time);
                    userSumm.run_time_t += (jobRuntime - oldInfo.run_time);
                } else {
                    logger.info("Runtime for Condor job " + clusterId + " has inconsistent values: " + (jobRuntime)
                            + " in the history log and " + oldInfo.run_time + " in condor_q");
                }
            } else {
                /*
                 * the job started after the previous doProcess and we have no
                 * record about it from condor_q
                 */
                vos.run_time_t += jobRuntime;
                userSumm.run_time_t += jobRuntime;
                vos.cpu_time_t += (jobCpuUsr + jobCpuSys);
                userSumm.cpu_time_t += (jobCpuUsr + jobCpuSys);

                Double imsize = (Double) hJobInfo.get("dImageSize");
                if (imsize != null) {
                    vos.size += imsize.doubleValue();
                    userSumm.size += imsize.doubleValue();
                }

                Double dusage = (Double) hJobInfo.get("dDiskUsage");
                if (dusage != null) {
                    vos.disk_usage += dusage.doubleValue();
                    userSumm.disk_usage += dusage.doubleValue();
                }

                vos.submittedJobs++;
                userSumm.submittedJobs++;
                vos.finishedJobs++;
                userSumm.finishedJobs++;
            }

            Long exitCode = (Long) hJobInfo.get("lExitCode");
            Long exitStatus = (Long) hJobInfo.get("lExitStatus");
            Boolean exitBySignal = (Boolean) hJobInfo.get("bExitBySignal");
            String remReason = (String) hJobInfo.get("sRemoveReason");
            if ((exitCode != null) || (exitStatus != null)) {
                boolean exitSig = false;
                boolean removed = false;
                if (exitBySignal != null) {
                    exitSig = exitBySignal.booleanValue();
                }
                if (remReason != null) {
                    removed = true;
                }

                /* try to obtain the ExitCode value which is more accurate */
                if (exitCode != null) {
                    if ((exitCode.longValue() != 0) || exitSig || removed) {
                        vos.errorJobs++;
                        userSumm.errorJobs++;
                    } else {
                        vos.successJobs++;
                        userSumm.successJobs++;
                    }
                } else {
                    /* if we don't have ExitCode, use ExitStatus */
                    if ((exitStatus.longValue() != 0) || exitSig || removed) {
                        vos.errorJobs++;
                        userSumm.errorJobs++;
                    } else {
                        vos.successJobs++;
                        userSumm.errorJobs++;
                    }
                }
            } else {
                logger.fine("No exit status reported for Condor job " + clusterId);
            }

            if (oldInfo != null) {
                /* construct a result to transmit the exit status of the job */
                Result exitRes = (Result) jobInfoToResult(oldInfo);
                /* make sure this result will be before the eResult for the job */
                exitRes.time = exitRes.time - 10;
                if (exitCode != null) {
                    exitRes.addSet("ExitStatus", exitCode.doubleValue());
                } else if (exitStatus != null) {
                    exitRes.addSet("ExitStatus", exitStatus.doubleValue());
                }
                vret.add(exitRes);
            }

            /*
             * construct the results which indicate the start time and the
             * completion time for the job
             */
            Result[] vres = new Result[4];

            Long startTime = (Long) hJobInfo.get("lJobStartDate");
            Long complTime = (Long) hJobInfo.get("lCompletionDate");
            Long crtStatus = (Long) hJobInfo.get("lEnteredCurrentStatus");
            if (((startTime != null) & (complTime != null)) && (crtStatus != null)) {
                long st = startTime.longValue();
                long ct = complTime.longValue();
                if (ct == 0) {
                    ct = crtStatus.longValue();
                    // logger.info("### Dates: " + new Date(st) + " " + new
                    // Date(ct));
                }

                for (int i = 0; i < 4; i++) {
                    vres[i] = new Result();
                    vres[i].ClusterName = "osgVO_JOBS_Finished";
                    if (mixedCaseVOs) {
                        vres[i].NodeName = jobVo;
                    } else {
                        vres[i].NodeName = jobVo.toUpperCase();
                    }

                    vres[i].Module = ModuleName;
                    vres[i].FarmName = farmName;
                    switch (i) {
                    case 0:
                        vres[i].time = st - 1;
                        break;
                    case 1:
                        vres[i].time = st;
                        break;
                    case 2:
                        vres[i].time = ct;
                        break;
                    case 3:
                        vres[i].time = ct + 1;
                    }

                    // logger.info("### cpu: " + jobCpuUsr + " " + jobCpuSys +
                    // " ct: " + ct + " st" + st);
                    double avgCpu = ((jobCpuUsr + jobCpuSys) * 1000) / (ct - st);
                    if ((i == 0) || (i == 3)) {
                        vres[i].addSet(cJobId, 0);
                    } else {
                        vres[i].addSet(cJobId, avgCpu);
                    }
                    vret.add(vres[i]);
                    logger.finest("Sending result for finished job: " + vres[i]);
                    if (i == 3) {
                        crtFinResults.add(vres[i]);
                    }
                }
            } else {
                logger.fine("Could not get job start/completion time for Condor job" + cJobId);
            }
            currentVOjobsInfo.put(jobVo, vos);
            currentUserJobsInfo.put(user, userSumm);

        } catch (Throwable t) {
            this.errorCode = VO_Utils.INTERNAL_JMGR_VOSUMCONDOR;
            MLLogEvent<String, Object> mlle = new MLLogEvent<String, Object>();
            Integer iErrCode = Integer.valueOf(this.errorCode);
            String sErrMsg = VO_Utils.voJobsErrCodes.get(iErrCode);
            mlle.logParameters.put("Error Code", iErrCode);
            if (sErrMsg != null) {
                mlle.logParameters.put("Error message", new String(sErrMsg));
            }
            logger.log(Level.WARNING, "updateVoSummaryCondor got exception ", new Object[] { mlle, t });
        }
        return vret;
    }

    /**
     * Updates the user or VO summary with information about a job, obtained
     * from the Condor history file
     * 
     * @param vos
     *            The VO or user summary.
     * @param hJobInfo
     *            The set of job parameters obtained from the history file.
     */
    protected void updateSummaryCondorHist(VOSummaryExt vos, Hashtable<String, Object> hJobInfo) {
        double jobCpuUsr = 0.0, jobCpuSys = 0.0;

        Double remoteCpuUsr = (Double) hJobInfo.get("dRemoteUserCpu");
        Double localCpuUsr = (Double) hJobInfo.get("dLocalUserCpu");
        if ((remoteCpuUsr != null) && (localCpuUsr != null)) {
            jobCpuUsr = remoteCpuUsr.doubleValue() + localCpuUsr.doubleValue();
            Double oCpuUsr = vos.paramsCondorH.get("CondorCpuUsr_t");
            vos.paramsCondorH.put("CondorCpuUsr_t", Double.valueOf(jobCpuUsr + oCpuUsr.doubleValue()));
        }

        Double remoteCpuSys = (Double) hJobInfo.get("dRemoteSysCpu");
        Double localCpuSys = (Double) hJobInfo.get("dLocalSysCpu");
        if ((remoteCpuSys != null) && (localCpuSys != null)) {
            jobCpuSys = remoteCpuSys.doubleValue() + localCpuSys.doubleValue();
            Double oCpuSys = vos.paramsCondorH.get("CondorCpuSys_t");
            vos.paramsCondorH.put("CondorCpuSys_t", Double.valueOf(jobCpuSys + oCpuSys.doubleValue()));
        }

        Double bytesSent = (Double) hJobInfo.get("dBytesSent");
        Double oldBytesSent = vos.paramsCondorH.get("CondorBytesSent_t");
        if ((bytesSent != null) && (oldBytesSent != null)) {
            vos.paramsCondorH.put("CondorBytesSent_t",
                    Double.valueOf(bytesSent.doubleValue() + oldBytesSent.doubleValue()));
        }

        Double bytesRecvd = (Double) hJobInfo.get("dBytesRecvd");
        Double oldBytesRecvd = vos.paramsCondorH.get("CondorBytesRecvd_t");
        if ((bytesRecvd != null) && (oldBytesRecvd != null)) {
            vos.paramsCondorH.put("CondorBytesRecvd_t",
                    Double.valueOf(bytesRecvd.doubleValue() + oldBytesRecvd.doubleValue()));
        }

        Double fileReadBytes = (Double) hJobInfo.get("dFileReadBytes");
        Double oldFileReadBytes = vos.paramsCondorH.get("CondorFileReadBytes_t");
        if ((fileReadBytes != null) && (oldFileReadBytes != null)) {
            vos.paramsCondorH.put("CondorFileReadBytes_t",
                    Double.valueOf(fileReadBytes.doubleValue() + oldFileReadBytes.doubleValue()));
        }

        Double fileWriteBytes = (Double) hJobInfo.get("dFileWriteBytes");
        Double oldFileWriteBytes = vos.paramsCondorH.get("CondorFileWriteBytes_t");
        if ((fileWriteBytes != null) && (oldFileWriteBytes != null)) {
            vos.paramsCondorH.put("CondorFileWriteBytes_t",
                    Double.valueOf(fileWriteBytes.doubleValue() + oldFileWriteBytes.doubleValue()));
        }
    }

    /**
     * Updates the current VO statistics with information about a finished job
     * obtained from the PBS accounting log.
     * 
     * @param hJobInfo
     *            Job information that will be added to the VO statistics.
     */
    private Vector<Result> updateVoSummaryPBSHist(Hashtable<String, Object> hJobInfo) {
        String user = (String) hJobInfo.get("sOwner");
        String jobVo = getVo(user);
        double jobCpu = 0, jobRuntime = 0;
        Vector<Result> vret = new Vector<Result>();

        if (jobVo == null) {
            logger.fine("No VO for user " + user);
            jobVo = noVoName;
        }

        /* search for this job in the list of jobs from the previous doProcess */
        String jobId = (String) hJobInfo.get("sId");
        if (jobId == null) {
            logger.info("Job found in the PBS accounting log, ID cannot be determined");
            return vret;
        }
        jobId = "PBS_" + jobId;
        Enumeration<String> jikeys = jobsInfo.keys();
        JobInfoExt oldInfo = null;
        while (jikeys.hasMoreElements()) {
            String jikey = jikeys.nextElement();
            if (jobId.startsWith(jikey)) {
                oldInfo = jobsInfo.get(jikey);
            }
        }

        try {
            VOSummaryExt vos = currentVOjobsInfo.get(jobVo);
            if (vos == null) {
                vos = new VOSummaryExt();
            }
            VOSummaryExt userSumm = currentUserJobsInfo.get(user);
            if (userSumm == null) {
                userSumm = new VOSummaryExt();
            }

            Double cpuTime = (Double) hJobInfo.get("dCpuTime");
            if (cpuTime != null) {
                jobCpu = cpuTime.doubleValue();
            }
            Double runTime = (Double) hJobInfo.get("dWallClockTime");
            if (runTime != null) {
                jobRuntime = runTime.doubleValue();
            }

            if (oldInfo != null) {
                if (jobCpu > oldInfo.cpu_time) {
                    // vos.cpu_time_t += (jobCpu - oldInfo.cpu_time);
                } else if ((jobCpu + 0.0001) < oldInfo.cpu_time) {
                    logger.info("CPU time for PBS job " + jobId + " has inconsistent values: " + jobCpu
                            + " in the accounting log and " + oldInfo.cpu_time + " in qstat");
                }
                if (jobRuntime > oldInfo.run_time) {
                    // vos.run_time_t += (jobRuntime - oldInfo.run_time);
                } else {
                    logger.info("Runtime for PBS job " + jobId + " has inconsistent values: " + jobRuntime
                            + " in the accounting log and " + oldInfo.run_time + " in qstat");
                }
            } else {
                /*
                 * the job started after the previous doProcess and we have no
                 * record about it from qstat
                 */
                vos.run_time_t += jobRuntime;
                userSumm.run_time_t += jobRuntime;
                vos.cpu_time_t += jobCpu;
                userSumm.cpu_time_t += jobCpu;
                vos.submittedJobs++;
                userSumm.submittedJobs++;
                vos.finishedJobs++;
                userSumm.finishedJobs++;
            }

            Integer exitCode = (Integer) hJobInfo.get("iExitCode");
            if (exitCode != null) {
                if (exitCode.intValue() != 0) {
                    vos.errorJobs++;
                    userSumm.errorJobs++;
                } else {
                    vos.successJobs++;
                    userSumm.successJobs++;
                }
            } else {
                logger.fine("Failed to get exit code for PBS job " + jobId);
            }

            /* construct a result to transmit the exit status of the job */
            Result exitRes = (Result) jobInfoToResult(oldInfo);
            if (exitRes != null) {
                /* make sure this result will be before the eResult for the job */
                exitRes.time = exitRes.time - 10;
                if (exitCode != null) {
                    exitRes.addSet("ExitStatus", exitCode.doubleValue());
                }
                vret.add(exitRes);
            }

            Long startTime = (Long) hJobInfo.get("lStartDate");
            Long complTime = (Long) hJobInfo.get("lCompletionDate");
            if ((startTime != null) && (complTime != null)) {
                long st = startTime.longValue();
                long ct = complTime.longValue();

                Result[] vres = new Result[4];
                for (int i = 0; i < 4; i++) {
                    vres[i] = new Result();
                    vres[i].ClusterName = "osgVO_JOBS_Finished";
                    if (mixedCaseVOs) {
                        vres[i].NodeName = jobVo;
                    } else {
                        vres[i].NodeName = jobVo.toUpperCase();
                    }

                    vres[i].Module = ModuleName;
                    vres[i].FarmName = farmName;
                    switch (i) {
                    case 0:
                        vres[i].time = st - 1;
                        break;
                    case 1:
                        vres[i].time = st;
                        break;
                    case 2:
                        vres[i].time = ct;
                        break;
                    case 3:
                        vres[i].time = ct + 1;
                    }

                    double avgCpu = 0.0;
                    if ((ct - st) > 0) {
                        avgCpu = (jobCpu * 1000) / (ct - st);
                    }

                    if ((i == 0) || (i == 3)) {
                        vres[i].addSet(jobId, 0);
                    } else {
                        vres[i].addSet(jobId, avgCpu);
                    }
                    vret.add(vres[i]);
                    if (i == 3) {
                        crtFinResults.add(vres[i]);
                    }
                    logger.finest("Sending result for finished job: " + vres[i]);
                    // logger.finest("### jobCpu " + jobCpu + " ct " + ct +
                    // " st " + st);
                } // for
            } else {
                logger.fine("Error getting job start/completion time for PBS job " + jobId);
            }

            currentVOjobsInfo.put(jobVo, vos);
            currentUserJobsInfo.put(user, userSumm);

        } catch (Throwable t) {
            this.errorCode = VO_Utils.INTERNAL_JMGR_VOSUMPBS;
            MLLogEvent<String, Object> mlle = new MLLogEvent<String, Object>();
            Integer iErrCode = Integer.valueOf(this.errorCode);
            String sErrMsg = VO_Utils.voJobsErrCodes.get(iErrCode);
            mlle.logParameters.put("Error Code", iErrCode);
            if (sErrMsg != null) {
                mlle.logParameters.put("Error message", new String(sErrMsg));
            }
            logger.log(Level.WARNING, "updateVoSummaryPBS got exception ", new Object[] { mlle, t });
        }
        return vret;
    }

    // --------------------------------------------------
    static public void main(String[] args) {
        System.out.println("args[0]: " + args[0]);
        String host = args[0];
        monOsgVoJobs aa = null;
        String ad = null;

        long t1 = System.currentTimeMillis();
        try {
            System.out.println("...instantiating OsgVoJobs");
            aa = new monOsgVoJobs();
        } catch (Exception e) {
            System.out.println(" Cannot instantiate OsgVoJobs:" + e);
            System.exit(-1);
        } // end try/catch

        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Cannot get ip for node " + e);
            System.exit(-1);
        } // end try/catch

        System.out.println("...running init method ");
        String mapFile = "/home/corina/grid3-user-vo-map.txt";
        String arg = "mapfile=" + mapFile;
        aa.init(new MNode(args[0], ad, null, null), arg);

        int sec = 1; // number of seconds to sleep before processing again
        for (int i = 0; i < 5; i++) {
            try {
                System.out.println("...sleeping " + sec + " seconds");
                Thread.sleep(sec * 1000);
                System.out.println("...running doProcess");
                aa.doProcess();
                /*
                 * if ( bb != null && bb instanceof Vector ) { Vector v =
                 * (Vector) bb; System.out.println (
                 * " Received a Vector having " + v.size() + " results" );
                 * for(int vi = 0; vi < v.size(); vi++) {
                 * System.out.println(" [ " + vi + " ] = " + v.elementAt(vi)); }
                 * }
                 */

                // -- after the 5th time, touch the map file and sleep --
                // -- to test the re-reading of the map file --
                /*
                 * if ( i == (int) 4 ) { System.out.println (
                 * "...touching map file: "+mapFile);
                 * Runtime.getRuntime().exec("touch "+mapFile);
                 * System.out.println ( "...sleeping "+sec+" seconds");
                 * Thread.sleep((int) 25000); // 25 secs }
                 */
            } catch (Exception e) {
                logger.log(Level.WARNING, "ERROR: ", e);
            } // end try/catch
        } // end for

        long t2 = System.currentTimeMillis();
        System.out.println("OsgVoJobs Testing Complete, execution time (ms): " + (t2 - t1));
        System.exit(0);
    } // end main
} // end class
