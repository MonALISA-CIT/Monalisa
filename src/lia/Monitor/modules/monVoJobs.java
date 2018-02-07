package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Logger;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;

public class monVoJobs extends monVoModules implements MonitoringModule {

    /**
       * 
       */
    private static final long serialVersionUID = 2995177534797168938L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monVoJobs.class.getName());

    //JGW  static protected String MyModuleName ="monVoJobs";
    protected static String MyModuleName = "monVoJobs";
    protected static String[] MyResTypes = { "Total Jobs", "Idle Jobs", "Running Jobs", "Held Jobs",
            "Total Submissions", "Failed Submissions", "PingOnly Submissions", "Failed Jobs", "Job Success Efficiency" };

    //save some GC-s! and some speed at .equals()
    private static final String CONDOR = "CONDOR";
    private static final String PBS = "PBS";
    private static final String LSF = "LSF";
    private static final String FBS = "FBS";
    private static final String SGE = "SGE";

    protected static String emailNotifyProperty = "lia.Monitor.notifyVOJOBS";

    //Ramiro  
    //protected static final String VoModulesDir = "VoModules-v0.9";

    static public final String VoModulesDir = "@ML_VoModulesDir@";

    protected HashMap jobMgr = new HashMap();

    protected String gatekeeperCmd = null; // Command for gatekeeper parser

    static public String testpath = "/home/weigand/MonALISA/MonaLisa.v098/Service/usr_code/VoModules/testdata";

    //==============================================================
    public monVoJobs() {
        super(MyModuleName, MyResTypes, emailNotifyProperty);
        canSuspend = false;
        String methodName = "constructor";
        addToMsg(methodName, "Constructor for " + ModuleName + " at " + currDate);
        addToMsg(methodName, "Info content: name " + info.name + " id " + info.id + " type " + info.type + " state "
                + info.state + " err " + info.error_count + ".");
        isRepetitive = true;
        info.ResTypes = ResTypes();
        logger.info(sb.toString());
        sb = new StringBuilder();
    } // end method

    //==============================================================
    // configuration file entry: monVoJobs{test,debug,location=}%30
    @Override
    public MonModuleInfo init(MNode inNode, String args) {
        String methodName = "init";
        String argList[] = new String[] {};
        Node = inNode;
        clusterModuleName = Node.getClusterName() + "-" + ModuleName;

        addToMsg(methodName, "Instantiating instance for Cluster (node in cf) " + clusterModuleName + " at " + currDate);
        addToMsg(methodName, "arguments: " + ((args == null) ? "NO ARGUMENTS" : args));
        addToMsg(methodName, "Node Info: name " + (Node.name == null ? "null" : Node.name) + " short_name "
                + (Node.name_short == null ? "null" : Node.name_short) + " cl.name "
                + (Node.cluster == null ? "null" : Node.cluster.name) + " fa.name "
                + (Node.farm == null ? "null" : Node.farm.name) + ".");

        // ------------------------
        // Check the argument list
        // ------------------------
        if (args != null) {
            //check if file location or globus_location are passed
            argList = args.split(","); //requires java 1.4
            for (int j = 0; j < argList.length; j++) {
                argList[j] = argList[j].trim();
                addToMsg(methodName, "Argument " + j + ":" + argList[j] + ".");
                if (argList[j].startsWith("mapfile=")) {
                    mapfile = argList[j].substring("mapfile=".length()).trim();
                    addToMsg(methodName, "overridden mapfile(" + mapfile + ")");
                    continue;
                }
                if (argList[j].startsWith("debug")) {
                    debugmode = true;
                    addToMsg(methodName, "overridden debugmode(" + debugmode + ")");
                    continue;
                }
                if (argList[j].startsWith("test")) {
                    testmode = true;
                    addToMsg(methodName, "testmode(" + testmode + ")");
                    continue;
                }
                if (argList[j].toLowerCase().indexOf("cansuspend") != -1) {
                    boolean cSusp = false;
                    try {
                        cSusp = Boolean.valueOf(argList[j].split("(\\s)*=(\\s)*")[1].trim()).booleanValue();
                    } catch (Throwable t) {
                        cSusp = false;
                    }
                    canSuspend = cSusp;
                    continue;
                }
            } // end for 
        } // end if args
        addToMsg(methodName, "Arguments(" + args + ") testmode==" + testmode + " debugmode==" + debugmode);

        info.ResTypes = ResTypes();
        return info;
    } // end method

    //=====================================================
    protected void setEnvironment() throws Exception {
        String methodName = "setEnvironment";
        logit("Setting environment");
        // -- Establish map table ---
        //What happen if the map table file is partialy good ?!?! Only a few accounts!

        //save latest known state
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
        monalisaHome = getEnvValue("MonaLisa_HOME");
        if (monalisaHome == null) {
            throw new Exception("MonaLisa_HOME environmental variable not set.");
        } //end if monalisaHome

        //  --- display the gatekeeper parser used ------
        if (testmode) {
            gatekeeperCmd = "/bin/cat " + monalisaHome + "/Service/usr_code/" + VoModulesDir + "/testdata/gatekeeper";
        } else {
            gatekeeperCmd = "python " + monalisaHome + "/Service/usr_code/" + VoModulesDir
                    + "/bin/parseGatekeeper.py 2>&1";
        }
        addToMsg(methodName, "MonaLisa_HOME  = " + monalisaHome);
        addToMsg(methodName, "Gatekeeper CMD = " + gatekeeperCmd);
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

    //=========================================================
    public void getJobManagers() throws Exception {
        String methodName = "getJobManagers";
        String location = null;
        String var = null;
        addToMsg(methodName, "Starting to get JobManagers");
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

        //------------------------------------------------------------------
        // Check the environmental variables for the job manager location
        // If found, replace the environmental value in the command string.
        //------------------------------------------------------------------
        for (Iterator it = jobMgr.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();

            String jobManager = (String) entry.getKey();
            String newloc = (String) entry.getValue();

            var = jobManager + "_LOCATION";
            addToMsg(methodName, "Processing " + jobManager + " [ " + var + " ]");
            try {
                location = getEnvValue(var);
            } catch (Exception e) {
                ;
            }

            if (location == null) {
                addToMsg(methodName, "Job Manager (" + jobManager + ") not used. No " + var
                        + " defined in the environment");
                it.remove();
            } else {
                //---------------------------------------------------------
                // Replace the variable portion of the path with the
                // environmental variable value found.
                // (e.g. - CONDOR_LOCATION)
                //---------------------------------------------------------
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

                    addToMsg(methodName, "SGE_ARCH = " + SGE_ARCH);
                    if (SGE_ARCH.equals("UNKNOWN") || (SGE_ARCH.length() == 0)) {
                        addToMsg(methodName, " Ignoring SGE !");
                        it.remove();
                        continue;
                    }

                    value = value.replaceFirst("SGE_ARCH", SGE_ARCH);
                }//if SGE

                jobMgr.put(jobManager, value);
                addToMsg(methodName, "Job Manager (" + jobManager + ") to be used. Command: " + value);
            }
        } // end of for (Enum...

        //-----------------------------------------------------
        // Verify the remaining job managers' executable exists.
        // if not, remove it and throw an exception or not.
        //-----------------------------------------------------
        for (Iterator jm = jobMgr.entrySet().iterator(); jm.hasNext();) {
            Map.Entry entry = (Map.Entry) jm.next();

            String jobManager = (String) entry.getKey();
            String cmd1 = (String) entry.getValue();

            StringTokenizer tz = new StringTokenizer(cmd1);
            int ni = tz.countTokens();
            if (ni > 0) {
                location = tz.nextToken().trim();
                File fd = new File(location);
                if (fd.exists()) {
                    addToMsg(methodName, "Job Manager (" + jobManager + ") Command(" + cmd1 + ") available.");
                } else {
                    throw new Exception("Job Manager (" + jobManager + ") Command (" + location + ") does not exist.)");
                } // end if/else
            } // end if ni>0
        } // end of for (Enum... 
        addToMsg(methodName, "Finished getting JobManagers [ " + jobMgr.size() + " JobManager(s) found ] ");
    } // end method

    //=======================================================================
    @Override
    public Object doProcess() throws Exception {
        String methodName = "doProcess";
        Vector v = null;

        try {
            //-- set the status to good --
            statusGood = true;

            //-- set environment (only once we hope --
            if (!environmentSet) {
                setEnvironment();
            } // if environmentSet

            // -- record the start time --
            setStartTime();

            // -- Initialize the VO totals table ---
            initializeTotalsTable();

            // -- Start the Job Queue Manager collectors ---
            collectJobMgrData();

            // -- Start the Globus Gatekeeper log parser --- 
            collectGatekeeperData();

            // -- record the finish -----
            setFinishTime();

            // -- create the results for update to ML-----
            v = createResults();

            // -- send a status update -----
            sendStatusEmail();

            if (getShouldNotifyConfig()) {
                logit(" [ monVoJobs ] - Notified Config changed");
                setShouldNotifyConfig(false);
            }

        } catch (Throwable e) {
            statusGood = false;
            sendExceptionEmail(methodName + " FATAL ERROR: " + e.getMessage());
            throw new Exception(e);
        } // end try/catch

        return v;
    } //end method

    //=== collectGatekeeperData ==========================
    private void collectGatekeeperData() throws Exception {
        try {
            debug("Gatekeeper log parser starting");
            debug("Command - " + gatekeeperCmd);
            BufferedReader buff1 = procOutput(gatekeeperCmd);
            if (buff1 == null) {
                throw new Exception("Command line process failed unexpectedly");
            }
            ParseGateKeeperOutput(buff1);
        } catch (Exception e) {
            logerr("collectGatekeeperData() FATAL ERROR -" + e.getMessage());
            throw e;
        }
    } // end method

    //=== collectJobMgrData ==========================
    private void collectJobMgrData() throws Exception {
        try {
            // ---- check the sanity of the environment ----
            if (jobMgr.isEmpty()) {
                throw new Exception("There are no valid job queue managers to use.");
            }

            // --- query each queue managers -------------
            for (Iterator it = jobMgr.entrySet().iterator(); it.hasNext();) {
                Map.Entry entry = (Map.Entry) it.next();
                String jobManager = (String) entry.getKey();
                debug("Job Queue Manager - " + jobManager);

                String cmd1 = (String) entry.getValue();
                debug("Command - " + cmd1);

                BufferedReader buff1 = procOutput(cmd1);

                debug("Returned from procOutput");
                if (buff1 == null) {
                    logerr("Failed  for " + cmd1);
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

        } catch (Exception e) {
            throw new Exception("collectJobMgrData - " + e.getMessage());
        } // end try/catch
    } // end method

    // ====== Parse Condor Ouput ==========================
    void ParseCondorOutput(BufferedReader buff) throws Exception {
        //--------------------------------------------------------------------
        // Job statuses:
        //   H = on hold, 
        //   R = running, 
        //   I = idle (waiting for a machine to execute on), 
        //  --------------------
        //  These states are not inlcuded in any metric:
        //   C = completed, 
        //   U = unexpanded (never been run), 
        //   X = removed.

        StringTokenizer tz;
        int linecnt = 0;
        int maxlinecnt = 7;

        debug("Starting to process output");
        try {
            for (;;) {
                String lin = buff.readLine();
                linecnt++;
                if (linecnt <= maxlinecnt) {
                    debug("Condor output: " + lin);
                } else {
                    debug("Condor output: " + lin);
                }

                // --- end of file ----
                if (lin == null) {
                    break;
                }
                //if ( lin.equals("") ) break;
                //Find the specific fields so we can substring the line
                // ID      OWNER            SUBMITTED     CPU_TIME ST PRI SIZE CMD 
                // 185.0   sdss           10/8  20:09   0+00:00:00 R  0   0.0  data
                //
                tz = new StringTokenizer(lin);
                int ni = tz.countTokens();
                if (ni > 4) {
                    String id = tz.nextToken().trim();
                    String user = tz.nextToken().trim();
                    String date = tz.nextToken().trim();
                    String time = tz.nextToken().trim();
                    String cputime = tz.nextToken().trim();
                    String status = tz.nextToken().trim();
                    if (status.equals("H")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Held Jobs");
                    } else if (status.equals("R")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Running Jobs");
                    } else if (status.equals("I")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Idle Jobs");
                    }
                } // end of if
            } // end of for      
            debug("Condor output - " + linecnt + " total lines. Only " + maxlinecnt + " displayed");
        } catch (Exception e) {
            throw new Exception("ParseCondorOutput - " + e.getMessage());
        } // end try/catch
    } // end method

    //====== Parse PBS Ouput ==========================
    void ParsePBSOutput(BufferedReader buff) throws Exception {
        /* 
           Job states:
            R - job is running.
            C - Job is completed and leaving the queue on it's own.
            E - Job is exiting after having run.
            H - Job is held.
            Q - job is queued, eligable to run or routed.
            T - job is being moved to new location.
            I - job is idle.
            W - job is waiting for its execution time
                (-a option) to be reached.
            S - (Unicos only) job is suspend.
            ----------
            These states are not included in any metric:
            X - Job is removed from the queue
        */

        StringTokenizer tz;
        int linecnt = 0;
        int maxlinecnt = 7;

        try {
            for (;;) {
                String lin = buff.readLine();
                linecnt++;
                if (linecnt <= maxlinecnt) {
                    debug("PBS output: " + lin);
                } else {
                    debug("PBS output: " + lin);
                }

                // --- end of file ----
                if (lin == null) {
                    break;
                    //if ( lin.equals("") ) break;
                }

                //Find the specific fields so we can substring the line
                //Job id       Name             User             Time Use S Queue
                //------------ ---------------- ---------------- -------- - -----
                //22930.bh1    calmob           uscms01          70:21:58 R bg
                //
                tz = new StringTokenizer(lin);
                int ni = tz.countTokens();
                if (ni > 4) {
                    String pid = tz.nextToken().trim();
                    String jname = tz.nextToken().trim();
                    String user = tz.nextToken().trim();
                    String usetime = tz.nextToken().trim();
                    String status = tz.nextToken().trim();
                    if (status.equals("R")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Running Jobs");
                    } else if (status.equals("C")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Running Jobs");
                    } else if (status.equals("E")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Running Jobs");
                    } else if (status.equals("H")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Held Jobs");
                    } else if (status.equals("Q")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Idle Jobs");
                    } else if (status.equals("T")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Idle Jobs");
                    } else if (status.equals("I")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Idle Jobs");
                    } else if (status.equals("W")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Idle Jobs");
                    } else if (status.equals("S")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Running Jobs");
                    }
                } // end if > 4
            } // end of for
            debug("PBS output - " + linecnt + " total lines. Only " + maxlinecnt + " displayed");
        } catch (Exception e) {
            throw new Exception("ParsePBSOutput - " + e.getMessage());
        } // end try/catch
    } // end method

    //====== Parse LSF Ouput ==========================
    void ParseLSFOutput(BufferedReader buff) throws Exception {
        /*
           Job states:
            PEND  - Job is pending, not yet started.
            PSUSP - Job is suspended while pending.
            RUN   - Job is running.
            USUSP - Job is suspended while running.
            SSUSP - Job is suspended due to load or job queue closed
            UNKWN - Job is lost
            WAIT  - Job is waiting for its execution time
            ZOMBI - Job is idle and becomes a zombie for a couple reasons.
            ---------
           These states are not included in any metric:
            DONE  - Job has terminated with status of 0.
            EXIT  - Job has termianted with non-zero status.
        */

        StringTokenizer tz;
        int linecnt = 0;
        int maxlinecnt = 7;

        try {
            for (;;) {
                String lin = buff.readLine();
                linecnt++;
                if (linecnt <= maxlinecnt) {
                    debug("LSF output: " + lin);
                } else {
                    debug("LSF output: " + lin);
                }

                // --- end of file ----
                if (lin == null) {
                    break;
                }

                //Find the specific fields so we can substring the line
                //JOBID  USER    STAT QUEUE  FROM_HOST   EXEC_HOST   JOB_NAME   SUBMIT_TIME
                //262115 vacavan RUN  medium pdsflx003.n pdsflx278.n *u0i0a-033 Oct 20 16:17 
                //
                tz = new StringTokenizer(lin);
                int ni = tz.countTokens();
                if (ni > 4) {
                    String pid = tz.nextToken().trim();
                    String user = tz.nextToken().trim();
                    String status = tz.nextToken().trim();

                    if (status.equals("PEND")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Idle Jobs");
                    } else if (status.equals("PSUSP")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Held Jobs");
                    } else if (status.equals("RUN")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Running Jobs");
                    } else if (status.equals("USUSP")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Held Jobs");
                    } else if (status.equals("SSUSP")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Held Jobs");
                    } else if (status.equals("UNKWN")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Idle Jobs");
                    } else if (status.equals("WAIT")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Idle Jobs");
                    } else if (status.equals("ZOMBI")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Idle Jobs");
                    }
                } // end if > 4
            } // end of for
            debug("LFS output - " + linecnt + " total lines. Only " + maxlinecnt + " displayed");
        } catch (Exception e) {
            throw new Exception("ParseLFSOutput - " + e.getMessage());
        } // end try/catch 
    } // end method

    //====== Parse FBS Ouput ==========================
    void ParseFBSOutput(BufferedReader buff) throws Exception {
        //--------------------------------------------------------------------
        // Job states:
        //  running  - Job is running.
        //  pending  - Job is pending.

        StringTokenizer tz;
        int linecnt = 0;
        int maxlinecnt = 7;

        try {
            for (;;) {
                String lin = buff.readLine();
                linecnt++;
                if (linecnt <= maxlinecnt) {
                    debug("FBS output: " + lin);
                } else {
                    debug("FBS output: " + lin);
                }

                // --- end of file ----
                if (lin == null) {
                    break;
                }

                //Find the specific fields so we can substring the line
                //JobID  State      User     Sections 
                //------ ---------- -------- ------------
                //18908  running    uscms01  GlobusExec:* 
                //18909  pending    uscms01  GlobusExec:* 
                //
                tz = new StringTokenizer(lin);
                int ni = tz.countTokens();
                if (ni > 3) {
                    String pid = tz.nextToken().trim();
                    String status = tz.nextToken().trim();
                    String user = tz.nextToken().trim();
                    if (status.equals("running")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Running Jobs");
                    } else if (status.equals("pending")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Held Jobs");
                    }
                } // end if > 3
            } // end of for
            debug("FBS output - " + linecnt + " total lines. Only " + maxlinecnt + " displayed");
        } catch (Exception e) {
            throw new Exception("ParseFBSOutput - " + e.getMessage());
        } // end try/catch 
    } // end method

    //====== Parse SGE Ouput ==========================
    void ParseSGEOutput(BufferedReader buff) throws Exception {
        //--------------------------------------------------------------------
        // Job states:                           metric
        // ----------------------                ------------
        //  d  - Job is deleted                  none, not in totals
        //  t  - Job is transferring.            IDLE
        //  r  - Job is running                  RUNNING
        //  R  - Job is restarted   
        //  s  - Job is suspended                HELD 
        //  S  - Job is suspended                HELD
        //  T  - Job is threshold                none, not in totals
        //  w  - Job is waiting                  IDLE
        //  h  - Job is in hold                  HELD
        // qw,hqw,Rr - these are states that kind of show history. The current state
        //             is the last character.
        //--------------------------------------------------------------------

        StringTokenizer tz;
        int linecnt = 0;
        int maxlinecnt = 7;

        try {
            for (;;) {
                String lin = buff.readLine();
                linecnt++;
                if (linecnt <= maxlinecnt) {
                    debug("SGE output: " + lin);
                } else {
                    debug("SGE output: " + lin);
                }

                // --- end of file ----
                if (lin == null) {
                    break;
                }

                //Find the specific fields so we can substring the line
                /*
                job-ID  prior name       user     state submit/start at     queue      master
                ------------------------------------------------------------------------------
                 602802     0 ana_200209 uscms01  r     08/05/2004 12:25:13 pdsflx034. MASTER
                 602298    -5 d20040804n uscms01  Rr    08/05/2004 12:44:20 pdsflx084. MASTER
                 109834     0 Reboot pds ivgdl    qw    04/06/2004 20:14:56
                    733     0 TopJesFit_ sdss     Eqw   02/02/2004 17:49:55
                 602975    -5 pr20040805 atlas    qw    08/05/2004 12:41:26
                 601392    -5 mark200408 wwoodvas hqw   08/05/2004 05:58:09
                */
                //
                tz = new StringTokenizer(lin);
                int ni = tz.countTokens();
                if (ni > 6) {
                    String pid = tz.nextToken().trim();
                    String prior = tz.nextToken().trim();
                    String name = tz.nextToken().trim();
                    String user = tz.nextToken().trim();
                    String status = tz.nextToken().trim();
                    // This code is dealing with the multiple character job states
                    // shown in the example above. The last character is considered the
                    // current state.
                    if (status.length() > 1) {
                        status = status.substring(status.length() - 1);
                    }
                    // Assign the metric
                    if (status.equals("d")) {
                        continue; // no metric
                    } else if (status.equals("t")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Idle Jobs");
                    } else if (status.equals("r")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Running Jobs");
                    } else if (status.equals("s")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Held Jobs");
                    } else if (status.equals("S")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Held Jobs");
                    } else if (status.equals("T")) {
                        continue; // no metric
                    } else if (status.equals("w")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Idle Jobs");
                    } else if (status.equals("h")) {
                        updateTotals(user, "Total Jobs");
                        updateTotals(user, "Held Jobs");
                    }
                } // end if > 6
            } // end of for
            debug("SGE output - " + linecnt + " total lines. Only " + maxlinecnt + " displayed");
        } catch (Exception e) {
            throw new Exception("ParseSGEOutput - " + e.getMessage());
        } // end try/catch 
    } // end method

    // ====== Parse parseGatekeeper.py Output ==========================
    void ParseGateKeeperOutput(BufferedReader buff) throws Exception {
        /*
           The parseGatekeeper.py program generates totals by unix user in 
           a whitespace delimited format:
            unix_user total_submission failed_submissions pings failed_jobs
            sdss 6 0 0 0
            usatlas1 267 0 57 0
            sekhri 5 0 0 0
            uscms01 244 0 43 0
          
            The final metric "Job Success Effiecency" is a derived value after
            all the Totals by VO have been performed so it will be calculated 
            after all the lines have been processed. 
        */
        StringTokenizer tz;
        String user = null;
        String value = null;
        Double d = Double.valueOf(0);
        int linecnt = 0;
        int maxlinecnt = 7;

        try {
            for (;;) {
                String lin = buff.readLine();
                linecnt++;
                debug("parseGatekeeper output: " + lin);

                // --- end of file ----
                if (lin == null) {
                    break;
                }

                //-----------------------------------------------------------------
                //  unix_user total_submission failed_submissions pings failed_jobs
                //  sdss 6 0 0 0
                //-----------------------------------------------------------------
                tz = new StringTokenizer(lin);
                int ni = tz.countTokens();
                if (ni == 5) {
                    user = tz.nextToken().trim();
                    value = tz.nextToken().trim();
                    updateTotals(user, "Total Submissions", value);
                    value = tz.nextToken().trim();
                    updateTotals(user, "Failed Submissions", value);
                    value = tz.nextToken().trim();
                    updateTotals(user, "PingOnly Submissions", value);
                    value = tz.nextToken().trim();
                    updateTotals(user, "Failed Jobs", value);
                } // end if == 5
            } // end of for
            debug("parseGatekeeper.py - " + linecnt + " total lines. Only " + maxlinecnt + " displayed");

            //-----------------------------------------------------------------
            //  Calculate the submission efficiency for the each VO
            //-----------------------------------------------------------------
            for (Enumeration vl = VoList(); vl.hasMoreElements();) {
                String vo = (String) vl.nextElement();
                double total = getMetric(vo, "Total Submissions").doubleValue();
                double failed = getMetric(vo, "Failed Jobs").doubleValue();
                double efficiency = Double.valueOf(100).doubleValue();
                if (total > 0) {
                    efficiency = ((total - failed) / total) * 100.0;
                }
                String eff = Double.toString(efficiency);
                updateVoTotals(vo, "Job Success Efficiency", eff);

            } // end for
        } catch (Exception e) {
            throw new Exception("ParseGateKeeperOutput - " + e.getMessage());
        } // end try/catch
    } // end method

    // --------------------------------------------------
    static public void main(String[] args) {
        System.out.println("args[0]: " + args);
        String host = args[0];
        monVoJobs aa = null;
        String ad = null;

        try {
            System.out.println("...instantiating VoJobs");
            aa = new monVoJobs();
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
        String arg = "test,mapfile=" + mapFile;
        MonModuleInfo info = aa.init(new MNode(args[0], ad, null, null), arg);

        int sec = 2; // number of seconds to sleep before processing again
        for (int i = 0; i < 8; i++) {
            try {
                System.out.println("...sleeping " + sec + " seconds");
                Thread.sleep(sec * 1000);
                System.out.println("...running doProcess");
                Object bb = aa.doProcess();
                // -- after the 5th time, touch the map file and sleep --
                // -- to test the re-reading of the map file         --
                if (i == 4) {
                    System.out.println("...touching map file: " + mapFile);
                    Runtime.getRuntime().exec("touch " + mapFile);
                    System.out.println("...sleeping " + sec + " seconds");
                    Thread.sleep(25000); // 25 secs
                }
            } catch (Exception e) {
                System.out.println("ERROR: " + e);
            } // end try/catch
        } // end for

        System.out.println("VoJobs Testing Complete");
        System.exit(0);
    } // end main
} // end class

