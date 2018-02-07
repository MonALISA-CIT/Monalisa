package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Observable;
import java.util.Observer;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.Monitor.monitor.eResult;
import lia.util.DateFileWatchdog;
import lia.util.ntp.NTPDate;

public class monVO_JOBS extends cmdExec implements MonitoringModule, Observer {

    /**
     * 
     */
    private static final long serialVersionUID = -5138731603421248058L;
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monVO_JOBS.class.getName());

    protected String monalisaHome = null;

    private static final long SEC_MILLIS = 1000;
    private static final long MIN_MILLIS = 60 * SEC_MILLIS;
    private static final long HOUR_MILLIS = 60 * MIN_MILLIS;
    private static final long DAY_MILLIS = 24 * HOUR_MILLIS;

    // wathcdog used to notify if mapFile has changed
    protected DateFileWatchdog mapFileWatchdog = null;
    protected boolean environmentSet = false;
    private static final String COMMENT = "#"; // comment line
    private static final String VOI = "#voi "; // lower case VO names
    private static final String VOC = "#VOc "; // mixed case VO names

    static public final String VoModulesDir = "VoModules-v0.38";
    protected static String OsName = "linux";
    protected String ModuleName = null;
    protected String[] ResTypes = null;
    protected static String MyModuleName = "monVO_JOBS";

    private static final Pattern SPACE_PATTERN = Pattern.compile("(\\s)+");
    // Ramiro
    // protected static final String VoModulesDir = "VoModules-v0.9";

    protected static final String MAP_FILE = "/monitoring/grid3-user-vo-map.txt";
    protected String mapfile = null;
    boolean testmode;

    // --- tables for tracking VO metrics ----------------
    protected Hashtable<String, String> voAccts = new Hashtable<String, String>();
    protected Hashtable<String, String> voMixedCase = new Hashtable<String, String>();
    private long lastRun = 0;

    // --- tmp tables for tracking VO metrics ----------------
    protected Hashtable<String, String> tmpVoAccts = new Hashtable<String, String>();
    protected Hashtable<String, String> tmpVoMixedCase = new Hashtable<String, String>();

    protected final HashMap<String, String> jobMgr = new HashMap<String, String>();

    // save some GC-s! and some speed at .equals()
    private static final String CONDOR = "CONDOR";
    private static final String PBS = "PBS";
    private static final String LSF = "LSF";
    private static final String FBS = "FBS";
    private static final String SGE = "SGE";

    static public String testpath = "/home/weigand/MonALISA/MonaLisa.v098/Service/usr_code/VoModules/testdata";
    protected static long MAP_FILE_WATCHDOG_CHECK_RATE = 30 * 1000;// every 30
                                                                   // seconds

    // holds JobInfo-s about the jobs returned from the queue managers
    // the key is the jobID

    // last doProcess()
    private Hashtable<String, JobInfo> jobsInfo = new Hashtable<String, JobInfo>();
    // current doProcess()
    private Hashtable<String, JobInfo> currentJobsInfo = new Hashtable<String, JobInfo>();

    // summary for each VO
    // the key is the VO

    // last doProcess()
    private Hashtable<String, VOSummary> VOjobsInfo = new Hashtable<String, VOSummary>();

    // current doProcess()
    private Hashtable<String, VOSummary> currentVOjobsInfo = new Hashtable<String, VOSummary>();

    static {
        try {
            String sMAP_FILE_WATCHDOG_CHECK_RATE = AppConfig.getProperty("lia.Monitor.modules.monVoModules", "20");
            MAP_FILE_WATCHDOG_CHECK_RATE = Long.valueOf(sMAP_FILE_WATCHDOG_CHECK_RATE).longValue() * 1000;
        } catch (Throwable t) {
            MAP_FILE_WATCHDOG_CHECK_RATE = 30 * 1000;
        }
    }

    private class VOSummary {
        private long run_time;
        private long size;

        private long runningjobs;
        private long idlejobs;
        private long heldjobs;
        private long finishedjobs;
        private long unknownjobs;

        public VOSummary() {
            run_time = 0;
            size = 0;
            runningjobs = 0;
            idlejobs = 0;
            heldjobs = 0;
            finishedjobs = 0;
            unknownjobs = 0;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("run_time=").append(run_time);
            sb.append("\tsize=").append(size);
            sb.append("\trunningjobs=").append(runningjobs);
            sb.append("\tidlejobs=").append(idlejobs);
            sb.append("\theldjobs=").append(heldjobs);
            sb.append("\tfinishedjobs=").append(finishedjobs);
            sb.append("\tunknownjobs=").append(unknownjobs);
            return sb.toString();
        }
    }

    private static class JobInfo {
        private String id;
        private String user;
        private String date;
        private long run_time;
        private String status;
        private String priority;
        private double size;
        private String VO;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("id=").append(id);
            sb.append("\tuser=").append(user);
            sb.append("\tdate=").append(date);
            sb.append("\trun_time=").append(run_time);
            sb.append("\tstatus=").append(status);
            sb.append("\tpriority=").append(priority);
            sb.append("\tsize=").append(size);
            sb.append("\tVO=").append(VO);
            return sb.toString();
        }

        /**
         * @param time the time to set
         */
        public void setTime(String time) {
        }
    }

    // ==============================================================
    public monVO_JOBS() {
        super("monVO_JOBS");
        isRepetitive = true;
        info.ResTypes = ResTypes();
    } // end method

    @Override
    public MNode getNode() {
        return this.Node;
    }

    @Override
    public String[] ResTypes() {
        return ResTypes;
    }

    @Override
    public String getOsName() {
        return OsName;
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    private boolean shouldPublishJobInfo = true;

    // ==============================================================
    // configuration file entry: monVoJobs{test,debug,location=}%30
    @Override
    public MonModuleInfo init(MNode inNode, String args) {
        String argList[] = new String[] {};
        Node = inNode;

        // ------------------------
        // Check the argument list
        // ------------------------
        if (args != null) {
            // check if file location or globus_location are passed
            argList = args.split(","); // requires java 1.4
            for (String element : argList) {
                if (element.startsWith("mapfile=")) {
                    mapfile = element.substring("mapfile=".length()).trim();
                }
                if (element.startsWith("test")) {
                    testmode = true;
                } else {
                    testmode = false;
                }
                if (element.indexOf("doNotPublishJobInfo") != -1) {
                    shouldPublishJobInfo = false;
                } else {
                    shouldPublishJobInfo = true;
                }
            } // end for
        } // end if args

        info.ResTypes = ResTypes();
        return info;
    } // end method

    // ==== PRIVATE METHODS ======================================
    protected void cleanupEnv() {
        // now we can clear ...
        voAccts.clear();
        voMixedCase.clear();
    }

    protected void computeVoAcctsDiff() {
        ArrayList<String> removedVOs = new ArrayList<String>();
        for (Enumeration<String> en = tmpVoMixedCase.elements(); en.hasMoreElements();) {
            String VO = en.nextElement();
            if (!voMixedCase.contains(VO)) {
                removedVOs.add(VO);
            }
        }

        if ((removedVOs.size() > 0) && (Node != null)) {
            MCluster mc = Node.getCluster();
            if ((mc != null) && (mc.getNodes() != null)) {
                for (int i = 0; i < mc.getNodes().size(); i++) {
                    MNode nRemove = mc.getNodes().get(i);
                    if ((nRemove != null) && (nRemove.name != null) && removedVOs.contains(nRemove.name)) {
                        mc.removeNode(nRemove);
                    }
                }
            }
        }

        // do it outside for .... to make sure that we do not notify half a
        // configuration
        // and also keep the sync part as small as posible
        // setShouldNotifyConfig(SN);
    }

    protected void initializeEnv() throws Exception {
        // -- display the result types --
        loadUserVoMapTable();
        validateMappings();
    } // end method

    // ======================================
    protected String getVo(String unix) {
        String voLower = null;
        String vo = null;
        voLower = voAccts.get(unix.toLowerCase());
        if (voLower != null) {
            vo = voMixedCase.get(voLower);
        }
        return vo;
    }

    // ==========================
    protected void validateMappings() throws Exception {
        /*
         * Validates the unix-VO mappings.
         * 
         * This performs all validation that cannot be performed until all
         * mapping records loaded.
         * 
         * We need to insure that every unix account will map to a VO and that
         * every VOc record specified will be mapped to by at least one unix
         * account.
         */

        String ignoredVOs = "";
        // -- Determine if there is a unix account defined for each #voi
        // specified
        for (Enumeration<String> e = voMixedCase.keys(); e.hasMoreElements();) {
            String vo = e.nextElement();
            if (!voAccts.contains(vo)) {
                ignoredVOs += (ignoredVOs.length() == 0) ? vo : ", " + vo;
                voMixedCase.remove(vo);
                // throw new
                // Exception("No unix account will map to VO ("+vo+")");
            } // end if
        } // end for

        String ignoredUNIXAccts = "";
        // -- Determine if there is a #voi for each unix account
        for (Enumeration<String> e = voAccts.keys(); e.hasMoreElements();) {
            String unix = e.nextElement();
            String vo = getVo(unix);
            if (vo == null) {
                ignoredUNIXAccts += (ignoredUNIXAccts.length() == 0) ? unix : ", " + unix;
                voAccts.remove(unix);
                // throw new
                // Exception("No VO account for unix account ("+unix+")");
            } // end if
        } // end for
    } // end method

    protected String getMapFile() throws Exception {
        /*
         * Returns the User-VO account mapping file name (full path). Returns
         * null, if not set.
         * 
         * First, checkx VDT_LOCATION env variabls Then, sets it to
         * MonaLisa_HOME + /..
         */
        String mapfile = null;
        String mappath = AppConfig.getGlobalEnvProperty("VDT_LOCATION");
        if (mappath == null) {
            mappath = AppConfig.getGlobalEnvProperty("MonaLisa_HOME");
            if (mappath != null) {
                mappath = mappath + "/..";
            } else {
                throw new Exception("Unable to determine " + MAP_FILE + " location. Terminating.");
            } // end if/else
        } // end if
          // set the file name
        mapfile = mappath + MAP_FILE;
        return mapfile;
    } // end method

    // ==================================================================
    protected void loadUserVoMapTable() throws Exception {
        // -- clean the hashtables used in the mappings ---
        cleanupEnv();

        // ----------------------------------------------
        String record = null;
        // ----------------------------------------------
        String unix = null;
        String vo = null;
        String[] voiList = null;

        boolean voiFound = false; // indicates if #voi map file reocrd was found
        boolean vocFound = false; // indicates if #VOc map file reocrd was found

        int voiCnt = 0; // number of voi VOs in map file
        int vocCnt = 0; // number of VOc VOs in map file

        // String[] config = new String[];

        try {
            // -- get the User-VO map file name ---------
            if (mapfile == null) {
                mapfile = getMapFile();
            } // end if
              // --verify the map file exists and is readable ----
            File probe = new File(mapfile);
            if (!probe.isFile()) {
                throw new Exception("map file(" + mapfile + ") not found.");
            } // end if
            if (!probe.canRead()) {
                throw new Exception("map file (" + mapfile + ") is not readable.");
            } // end if

            if (mapFileWatchdog != null) {// just extra check ...
                if (!mapFileWatchdog.getFile().equals(probe)) {// has changed or
                                                               // has been
                                                               // deleted ?!?
                    mapFileWatchdog.stopIt();
                    mapFileWatchdog = null;
                }
            }

            if (mapFileWatchdog == null) {
                mapFileWatchdog = DateFileWatchdog.getInstance(probe, MAP_FILE_WATCHDOG_CHECK_RATE);
                mapFileWatchdog.addObserver(this);
                environmentSet = false;
            }

            // ---------------------------
            // Start processing the file
            // ---------------------------
            FileReader fr = new FileReader(mapfile);
            BufferedReader br = new BufferedReader(fr);
            ArrayList<String> ignoredAccounts = new ArrayList<String>();
            while ((record = br.readLine()) != null) {
                StringTokenizer tz = new StringTokenizer(record);
                if (tz.countTokens() == 0) { // check for empty line
                    continue;
                }
                // -----------------------------------------------------------------
                // Get the first work in the line and use it to see the record
                // type
                // -----------------------------------------------------------------
                String token1 = tz.nextToken().trim();

                // --- process #voi record ----
                if (VOI.equals(token1 + " ")) { // lower case VO names
                    // --- verify that there are not more than 1 #voi record
                    if (voiFound) {
                        br.close();
                        throw new Exception("Multiple #voi records found in map file.");
                    }
                    voiFound = true;
                    voiCnt = tz.countTokens();
                    voiList = new String[voiCnt];
                    for (int i = 0; i < voiCnt; i++) {
                        vo = tz.nextToken().trim();
                        voiList[i] = vo;
                    } // end for
                    continue; // read next record
                } // end if

                // --- process #VOc record ----
                if (VOC.equals(token1 + " ")) { // lower case VO names
                    if (vocFound) {
                        br.close();
                        throw new Exception("Multiple #VOc records found in map file.");
                    }
                    vocFound = true;
                    // --- verify that the #voi record was found first --------
                    if (!voiFound) {
                        br.close();
                        throw new Exception("The #voi record must precede the #VOc record.");
                    }
                    // --- verify there is a 1 for 1 map of voi to voc VOs ----
                    vocCnt = tz.countTokens();
                    if (voiCnt != vocCnt) {
                        br.close();
                        throw new Exception("#voi(" + voiCnt + ") and #VOc(" + vocCnt + ") entries do not match.");
                    }
                    // -- build the lower to mixed case mappings -------------
                    for (int i = 0; i < vocCnt; i++) {
                        vo = tz.nextToken().trim();
                        voMixedCase.put(voiList[i], vo);
                    } // end for
                    continue; // read next record
                } // end if

                // --- process comment record ----
                if (record.trim().startsWith(COMMENT)) { // check for comment
                    continue; // read next record
                } // end if

                // ---------------------------------------------------------------
                // Process the unix to lower case mapping records
                // (anything that falls through to here is considered a mapping)
                // ---------------------------------------------------------------
                // --- verify that the #voi and $VOc records was found first
                // --------
                if (!vocFound) {
                    br.close();
                    throw new Exception("The #voi and #VOc records must precede the unix to VO mapping records.");
                }
                // ----------------------------------------------------
                // Since we already stripped off the first token, there should
                // only
                // be 1 left. Otherwise, it is probably a bad record and we will
                // ignore it.
                // ----------------------------------------------------
                int ni = tz.countTokens();
                if (ni != 1) {
                    continue; // presumably just a bad line in the file
                }
                // --------------------------------------------------------
                // This should be a mapping line of user to lower case VO
                // --------------------------------------------------------
                if (ni == 1) {
                    unix = token1;
                    vo = tz.nextToken().trim();
                    if (voAccts.containsKey(unix)) {
                        // br.close();
                        ignoredAccounts.add(unix);
                        // throw new
                        // Exception("Multiple mappings for unix account ("+unix+")");
                    } // end if
                    voAccts.put(unix, vo);
                } else {
                    // br.close();
                    // throw new
                    // Exception("Unable to determine mapping from this entry("+record+")");
                } // end if
            }

            if (ignoredAccounts.size() > 0) {
                for (int iai = 0; iai < ignoredAccounts.size(); iai++) {
                    voAccts.remove(ignoredAccounts.get(iai));
                }
            }

            br.close();
        } catch (Exception e) {
            throw new Exception("ERROR in mapping file: " + e.getMessage());
        }
    } // end method

    // =====================================================
    protected void setEnvironment() throws Exception {
        // save latest known state
        tmpVoAccts.clear();
        tmpVoMixedCase.clear();
        tmpVoAccts.putAll(voAccts);
        tmpVoMixedCase.putAll(voMixedCase);

        cleanupEnv();
        try {
            initializeEnv();
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
        try {
            getJobManagers();
        } catch (Exception e1) {
            throw e1;
        }
        environmentSet = true;
    } // end method

    // =========================================================
    public void getJobManagers() throws Exception {
        String location = null;
        String var = null;
        // -------------------------------------------------------
        // Initialize the command for the job manager types
        // -------------------------------------------------------
        if (testmode) {
            jobMgr.put(CONDOR, "CONDOR_LOCATION/cat " + monalisaHome + "/Service/usr_code/" + VoModulesDir
                    + "/testdata/condor");
            jobMgr.put(PBS, "PBS_LOCATION/cat " + monalisaHome + "/Service/usr_code/" + VoModulesDir + "/testdata/pbs");
            jobMgr.put(LSF, "LSF_LOCATION/cat " + monalisaHome + "/Service/usr_code/" + VoModulesDir + "/testdata/lsf");
            jobMgr.put(FBS, "FBS_LOCATION/cat " + monalisaHome + "/Service/usr_code/" + VoModulesDir + "/testdata/fbs");
            jobMgr.put(SGE, "SGE_LOCATION/cat " + monalisaHome + "/Service/usr_code/" + VoModulesDir + "/testdata/sge");
        } else {
            jobMgr.put(CONDOR, "CONDOR_LOCATION/bin/condor_q 2>&1");
            jobMgr.put(PBS, "PBS_LOCATION/bin/qstat 2>&1");
            jobMgr.put(LSF, "LSF_LOCATION/bin/bjobs -a -w -u all 2>&1");
            jobMgr.put(FBS, "FBS_LOCATION/bin/fbs lj 2>&1");
            jobMgr.put(SGE, "SGE_LOCATION/bin/SGE_ARCH/qstat -s zprs 2>&1");
        }

        // ------------------------------------------------------------------
        // Check the environmental variables for the job manager location
        // If found, replace the environmental value in the command string.
        // ------------------------------------------------------------------
        for (Iterator<Map.Entry<String, String>> jmi = jobMgr.entrySet().iterator(); jmi.hasNext();) {
            Entry<String, String> entry = jmi.next();

            String jobManager = entry.getKey();
            String newloc = entry.getValue();
            var = jobManager + "_LOCATION";

            try {
                location = AppConfig.getGlobalEnvProperty(var);
            } catch (Throwable e) {
                logger.log(Level.WARNING, "[monVO_JOBS] Exception getGlobalEnvProperty(" + var + "). Cause:", e);
            }

            if (location == null) {
                jmi.remove();
            } else {
                // ---------------------------------------------------------
                // Replace the variable portion of the path with the
                // environmental variable value found.
                // (e.g. - CONDOR_LOCATION)
                // ---------------------------------------------------------
                String value = newloc.replaceFirst(var, location);

                if (jobManager.equals(SGE)) {
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

                    if (SGE_ARCH.equals("UNKNOWN") || (SGE_ARCH.length() == 0)) {
                        jmi.remove();
                        continue;
                    }

                    value = value.replaceFirst("SGE_ARCH", SGE_ARCH);
                }// if SGE

                jobMgr.put(jobManager, value);
            }
        } // end of for (Enum...

        // -----------------------------------------------------
        // Verify the remaining job managers' executable exists.
        // if not, remove it and throw an exception or not.
        // -----------------------------------------------------
        for (Entry<String, String> entry : jobMgr.entrySet()) {
            String jobManager = entry.getKey();
            String cmd1 = entry.getValue();

            StringTokenizer tz = new StringTokenizer(cmd1);
            int ni = tz.countTokens();
            if (ni > 0) {
                location = tz.nextToken().trim();
                File fd = new File(location);
                if (!fd.exists()) {
                    throw new Exception("Job Manager (" + jobManager + ") Command (" + location + ") does not exist.)");
                } // end if/else
            } // end if ni>0
        } // end of for (Enum...

    } // end method

    // =======================================================================
    @Override
    public Object doProcess() throws Exception {
        Vector<Object> v = null;

        try {

            currentVOjobsInfo = new Hashtable<String, VOSummary>();
            currentJobsInfo = new Hashtable<String, JobInfo>();

            // -- set environment (only once we hope --
            synchronized (jobMgr) {
                if (!environmentSet) {
                    setEnvironment();
                } // if environmentSet
            }

            // -- Start the Job Queue Manager collectors ---
            collectJobMgrData();
            v = getResults();
        } catch (Throwable e) {
            throw new Exception(e);
        } // end try/catch

        return v;
    } // end method

    private Object jobInfoToResult(JobInfo jobInfo) {
        Result r = new Result();

        r.ClusterName = jobInfo.VO + "_JOBS";
        r.NodeName = jobInfo.id;
        r.time = NTPDate.currentTimeMillis();
        r.addSet("RunTime", jobInfo.run_time / MIN_MILLIS);
        r.addSet("Size", jobInfo.size);
        r.Module = ModuleName;

        return r;
    }

    private Vector<Object> getResults() {
        Vector<Object> v = new Vector<Object>();

        // publish all the current jobs
        if (shouldPublishJobInfo) {
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

        Vector<Object> jd = getJobDiff();
        if ((jd != null) && (jd.size() > 0)) {
            v.addAll(jd);
        }

        // calculate RateS
        Vector<Object> vd = null;
        if (lastRun != 0) {
            vd = getVODiff();
        }

        if ((vd != null) && (vd.size() > 0)) {
            v.addAll(vd);
        }

        jobsInfo = currentJobsInfo;
        VOjobsInfo = currentVOjobsInfo;

        lastRun = NTPDate.currentTimeMillis();

        return v;
    }

    private double getBin(double newValue, double oldValue) {
        return newValue - oldValue;
    }

    private double getRate(double newValue, double oldValue, double dt) {
        return getBin(newValue, oldValue) / dt;
    }

    private Vector<Object> getVODiff() {

        Vector<Object> retV = new Vector<Object>();

        long cTime = NTPDate.currentTimeMillis();
        double dTime = (cTime - lastRun) / 1000; // in seconds

        for (Enumeration<String> it = VOjobsInfo.keys(); it.hasMoreElements();) {
            String VO = it.nextElement();
            VOSummary cVOSummary = currentVOjobsInfo.get(VO);
            if (cVOSummary != null) {
                VOSummary oVOSummary = VOjobsInfo.get(VO);
                Result r = new Result();
                r.ClusterName = Node.getClusterName();
                r.NodeName = VO;
                r.time = cTime;
                r.Module = ModuleName;
                double cTotalJobs = cVOSummary.heldjobs + cVOSummary.idlejobs + cVOSummary.runningjobs
                        + cVOSummary.unknownjobs;
                double oTotalJobs = oVOSummary.heldjobs + oVOSummary.idlejobs + oVOSummary.runningjobs
                        + oVOSummary.unknownjobs;

                r.addSet("RunningJobs", cVOSummary.runningjobs);
                r.addSet("RunningJobs_R", getRate(cVOSummary.runningjobs, oVOSummary.runningjobs, dTime));
                r.addSet("IdleJobs", cVOSummary.idlejobs);
                r.addSet("IdleJobs_R", getRate(cVOSummary.idlejobs, oVOSummary.idlejobs, dTime));
                r.addSet("HeldJobs", cVOSummary.heldjobs);
                r.addSet("HeldJobs_R", getRate(cVOSummary.heldjobs, oVOSummary.heldjobs, dTime));
                r.addSet("FinishedJobs", cVOSummary.finishedjobs);
                r.addSet("FinishedJobs_R", getRate(cVOSummary.finishedjobs, oVOSummary.finishedjobs, dTime));
                r.addSet("RunTime_bin", getBin(cVOSummary.run_time, oVOSummary.run_time) / MIN_MILLIS);
                r.addSet("RunTime_R", getRate(cVOSummary.run_time, oVOSummary.run_time, dTime) / MIN_MILLIS);
                r.addSet("JobsSize_bin", getBin(cVOSummary.size, oVOSummary.size));
                r.addSet("JobsSize_R", getRate(cVOSummary.size, oVOSummary.size, dTime));

                if (cVOSummary.unknownjobs != 0) {
                    r.addSet("UnkownJobs", cVOSummary.unknownjobs);
                }
                r.addSet("TotalJobs", cTotalJobs);
                r.addSet("TotalJobs_R", getRate(cTotalJobs, oTotalJobs, dTime));

                retV.add(r);
            } else {// should delete this VO
                eResult er = new eResult();
                er.ClusterName = Node.getClusterName();
                er.NodeName = VO;
                er.param = null;
                er.param_name = null;
                retV.add(er);

                eResult er1 = new eResult();
                er1.ClusterName = VO + "_JOBS";
                er1.NodeName = null;
                er1.param = null;
                er1.param_name = null;
                retV.add(er1);
            }
        }

        return retV;
    }

    private Vector<Object> getJobDiff() {
        Vector<Object> retV = new Vector<Object>();
        for (Enumeration<String> it = jobsInfo.keys(); it.hasMoreElements();) {
            String oldJobID = it.nextElement();
            JobInfo oldJobInfo = jobsInfo.get(oldJobID);
            if (!currentJobsInfo.containsKey(oldJobID)) {// a finished job
                eResult r = new eResult();
                r.ClusterName = oldJobInfo.VO + "_JOBS";
                r.NodeName = oldJobInfo.id;
                r.time = NTPDate.currentTimeMillis();
                r.param = null;
                r.param_name = null;
                r.Module = ModuleName;

                VOSummary vos = currentVOjobsInfo.get(oldJobInfo.VO);
                if (vos == null) {
                    vos = new VOSummary();
                }
                vos.finishedjobs += 1;
                currentVOjobsInfo.put(oldJobInfo.VO, vos);

                VOSummary oldvos = VOjobsInfo.get(oldJobInfo.VO);
                if (oldvos == null) {
                    logger.log(Level.WARNING, " JOB FINISHED !!! NO VO ?!?!??!");
                } else {
                    oldvos.run_time -= oldJobInfo.run_time;
                    oldvos.size -= oldJobInfo.size;
                }

                retV.add(r);
            }
        }

        return retV;
    }

    private Vector<Object> collectJobMgrData() throws Exception {
        Vector<Object> results = new Vector<Object>();

        try {
            // ---- check the sanity of the environment ----
            if (jobMgr.isEmpty()) {
                throw new Exception("There are no valid job queue managers to use.");
            }

            // --- query each queue managers -------------
            for (Entry<String, String> entry : jobMgr.entrySet()) {

                String jobManager = entry.getKey();
                String cmd1 = entry.getValue();

                BufferedReader buff1 = procOutput(cmd1);

                if (buff1 == null) {
                    throw new Exception("Command line process failed unexpectedly");
                }

                if (jobManager.equals(CONDOR)) {
                    ParseCondorOutput(buff1);
                } else if (jobManager.equals(PBS)) {
                    ParsePBSOutput(buff1);
                } else if (jobManager.equals(LSF)) {
                    ParseLSFOutput(buff1);
                } else if (jobManager.equals(FBS)) {
                    ParseFBSOutput(buff1);
                } else if (jobManager.equals(SGE)) {
                    ParseSGEOutput(buff1);
                } else {
                    throw new Exception("Invalid job manager (" + jobManager + ").  Internal error.");
                }
            } // end of for

        } catch (Throwable t) {
            throw new Exception("collectJobMgrData - " + t.getMessage());
        } // end try/catch

        return results;
    } // end method

    // ====== Parse Condor Ouput ==========================
    void ParseCondorOutput(BufferedReader buff) throws Exception {
        // --------------------------------------------------------------------
        // Job statuses:
        // H = on hold,
        // R = running,
        // I = idle (waiting for a machine to execute on),
        // --------------------
        // These states are not inlcuded in any metric:
        // C = completed,
        // U = unexpanded (never been run),
        // X = removed.

        try {
            boolean canProcess = false;
            for (String lin = buff.readLine(); lin != null; lin = buff.readLine()) {
                try {
                    // if ( lin.equals("") ) break;
                    // Find the specific fields so we can substring the line
                    // ID OWNER SUBMITTED CPU_TIME ST PRI SIZE CMD
                    // 185.0 sdss 10/8 20:09 0+00:00:00 R 0 0.0 data
                    //
                    if (!canProcess) {
                        if ((lin.indexOf("ID") != -1) && (lin.indexOf("OWNER") != -1)) {
                            canProcess = true;
                        }
                        continue;
                    }

                    if (lin.indexOf(";") != -1) {
                        continue;
                    }

                    String[] columns = SPACE_PATTERN.split(lin.trim());

                    if (columns.length > 6) {
                        JobInfo jobInfo = new JobInfo();
                        jobInfo.id = columns[0];
                        jobInfo.user = columns[1];
                        jobInfo.date = columns[2];
                        jobInfo.setTime(columns[3]);
                        jobInfo.run_time = parseCondorTime(columns[4]);
                        jobInfo.status = columns[5];
                        jobInfo.priority = columns[6];
                        jobInfo.size = Double.parseDouble(columns[7]);

                        jobInfo.VO = getVo(jobInfo.user);
                        if (jobInfo.VO == null) {
                            continue; // this is not a user we are interested in
                        }
                        currentJobsInfo.put(jobInfo.id, jobInfo);
                        updateVoSummary(jobInfo);

                    } // end of if
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exc parsing condor_q output at line [" + lin + "]", t);
                }
            } // end of for

        } catch (Throwable t) {
            throw new Exception("ParseCondorOutput - " + t.getMessage());
        } // end try/catch

    } // end method

    private long parseCondorTime(String cpuTime) {
        long sum = 0;

        String[] dh = cpuTime.split("\\+");

        sum += Long.parseLong(dh[0]) * DAY_MILLIS;

        String[] hms = dh[1].split(":");

        sum += Long.valueOf(hms[0]).longValue() * HOUR_MILLIS;
        sum += Long.valueOf(hms[1]).longValue() * MIN_MILLIS;
        sum += Long.valueOf(hms[2]).longValue() * SEC_MILLIS;

        return sum;
    }

    private void updateVoSummary(JobInfo jobInfo) {
        try {
            VOSummary vos = currentVOjobsInfo.get(jobInfo.VO);
            if (vos == null) {
                vos = new VOSummary();
            }

            if (jobInfo.status.equals("H")) {
                vos.heldjobs += 1;
            } else if (jobInfo.status.equals("R")) {
                vos.runningjobs += 1;
            } else if (jobInfo.status.equals("I")) {
                vos.idlejobs += 1;
            } else {
                vos.unknownjobs += 1;
            }

            vos.run_time += jobInfo.run_time;
            vos.size += jobInfo.size;

            currentVOjobsInfo.put(jobInfo.VO, vos);

        } catch (Throwable t) {
            logger.log(Level.WARNING, "updateVoSummary got exc", t);
        }
    }

    // ====== Parse PBS Ouput ==========================
    void ParsePBSOutput(BufferedReader buff) throws Exception {
        // TO BE DONE
    } // end method

    // ====== Parse LSF Ouput ==========================
    void ParseLSFOutput(BufferedReader buff) throws Exception {
        // TO BE DONE
    } // end method

    // ====== Parse FBS Ouput ==========================
    void ParseFBSOutput(BufferedReader buff) throws Exception {
        // TO BE DONE
    } // end method

    // ====== Parse SGE Ouput ==========================
    void ParseSGEOutput(BufferedReader buff) throws Exception {
        // TO BE DONE
    } // end method

    // ===============================================================
    // The Vo map file has changed
    @Override
    public void update(Observable o, Object arg) {
        if ((o != null) && (mapFileWatchdog != null) && o.equals(mapFileWatchdog)) {// just
            // extra
            // check
            environmentSet = false;
        }
    }

    // --------------------------------------------------
    static public void main(String[] args) {
        System.out.println("args[0]: " + args);
        String host = args[0];
        monVO_JOBS aa = null;
        String ad = null;

        try {
            System.out.println("...instantiating VoJobs");
            aa = new monVO_JOBS();
        } catch (Exception e) {
            System.out.println(" Cannot instantiate VoJobs:" + e);
            System.exit(-1);
        } // end try/catch

        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Cannot get ip for node " + e);
            System.exit(-1);
        } // end try/catch

        System.out.println("...running init method ");
        String mapFile = "./testdata/grid3-user-vo-map.txt";
        //String arg = "test,mapfile=" + mapFile;
        // MonModuleInfo info = aa.init( new MNode (args[0] ,ad, null, null),
        // arg);
        aa.init(new MNode(args[0], ad, null, null), "");

        int sec = 2; // number of seconds to sleep before processing again
        for (int i = 0; i < 8; i++) {
            try {
                System.out.println("...sleeping " + sec + " seconds");
                Thread.sleep(sec * 1000);
                System.out.println("...running doProcess");
                Object bb = aa.doProcess();
                if ((bb != null) && (bb instanceof Vector)) {
                    @SuppressWarnings("unchecked")
                    Vector<Object> v = (Vector<Object>) bb;
                    System.out.println(" Received a Vector having " + v.size() + " results");
                    for (int vi = 0; vi < v.size(); vi++) {
                        System.out.println(" [ " + vi + " ] = " + v.elementAt(vi));
                    }
                }

                // -- after the 5th time, touch the map file and sleep --
                // -- to test the re-reading of the map file --
                if (i == 4) {
                    System.out.println("...touching map file: " + mapFile);
                    Runtime.getRuntime().exec("touch " + mapFile);
                    System.out.println("...sleeping " + sec + " seconds");
                    Thread.sleep(25000); // 25 secs
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "ERROR: ", e);
            } // end try/catch
        } // end for

        System.out.println("VoJobs Testing Complete");
        System.exit(0);
    } // end main
} // end class

