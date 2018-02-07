package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import lia.util.LogWatcher;
import lia.util.logging.MLLogEvent;

/**
 * Helper class used by the monOsgVoJobs module to obtain accounting information
 * from PBS. It parses the output of the qstat command, to obtain information
 * for currently running jobs.
 */
public class VO_PBSAccounting {

    private static final Logger logger = Logger.getLogger(VO_PBSAccounting.class.getName());

    private static final long SEC_MILLIS = 1000;

    private static final long MIN_MILLIS = 60 * SEC_MILLIS;

    private static final long HOUR_MILLIS = 60 * MIN_MILLIS;

    private static final Pattern SPACE_PATTERN = Pattern.compile("(\\s)+");

    private static final Pattern EQ_PATTERN = Pattern.compile("=");

    private static final Pattern COL_PATTERN = Pattern.compile(":");

    /*
     * Used to obtain only the lines that were added to the history file
     * since the previous read operation.
     */
    LogWatcher watcher = null;

    /* The PBS accounting logs directory. */
    File logDir = null;

    /* The path of the current PBS accounting log. */
    protected String crtPBSLog = null;

    /**
     * Constructor for the VO_PBSAccounting class.
     * 
     * @param pbsLog
     *            The complete path of the last PBS accounting log.
     */
    public VO_PBSAccounting(String logDirName) {
        logger.log(Level.INFO, "[VO_PBSAccounting] Initalizing...");
        if (logDirName != null) {
            this.logDir = new File(logDirName);
            this.crtPBSLog = getLastLog(logDir);
            if (this.crtPBSLog == null) {
                logger.log(Level.INFO, "There is no log in the PBS log dir!");
            } else {
                watcher = new LogWatcher(crtPBSLog);
            }
        } else {
            logger.log(Level.INFO,
                    "No PBS history information will be provided (either the feature was disabled or the logs were not found)");
            watcher = null;
        }

    }

    /**
     * Collects the new information added to the accounting logs since the last
     * time we checked them.
     * 
     * @return A Vector with Hashtables, each hashtable containing information
     *         about a job.
     */
    public Vector<Hashtable<String, Object>> getHistoryInfo() {
        Vector<Hashtable<String, Object>> histInfo = new Vector<Hashtable<String, Object>>();
        logger.log(Level.FINEST, "Checking PBS logs...");
        if (watcher == null) {
            return null;
        }

        try {
            BufferedReader br = watcher.getNewChunk();
            getFinishedJobs(br, histInfo);

            /* check if a new log was created */
            String lastLog = getLastLog(this.logDir);
            logger.fine("Last PBS log " + lastLog);
            if (!lastLog.equals(this.crtPBSLog)) {
                this.crtPBSLog = lastLog;
                watcher.setFilename(lastLog);
                br = watcher.getNewChunk();
                getFinishedJobs(br, histInfo);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error parsing the PBS log: ", e);
            return null;
        }
        return histInfo;
    }

    protected void getFinishedJobs(BufferedReader br, Vector<Hashtable<String, Object>> jobsInfo) throws Exception {
        String line;
        if (br == null) {
            throw new Exception("Error reading the PBS log " + watcher.getFilename());
        }

        while ((line = br.readLine()) != null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("Parsing line from PBS log: " + line);
            }
            Hashtable<String, Object> histJobInfo = parseLogRecord(line);
            if (histJobInfo != null) {
                jobsInfo.add(histJobInfo);
            }
        }
    }

    protected Hashtable<String, Object> parseLogRecord(String line) throws Exception {
        Hashtable<String, Object> jobInfo = new Hashtable<String, Object>();
        String[] recList = line.split(";");

        if (recList.length != 4) {
            throw new Exception("Error parsing PBS log - line: " + line);
        }

        /* we only process "E" (exit) records */
        if (!recList[1].equals("E")) {
            return null;
        }

        /* process the completion date of the job */
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault());
            ParsePosition pp = new ParsePosition(0);
            Date complDate = sdf.parse(recList[0], pp);
            jobInfo.put("lCompletionDate", Long.valueOf(complDate.getTime()));
        } catch (Exception exc) {
            logger.warning("Error parsing date: " + recList[0]);
        }
        jobInfo.put("sId", recList[2]);

        String[] accList = SPACE_PATTERN.split(recList[3]);
        for (String element : accList) {
            String[] params = EQ_PATTERN.split(element);
            if (params.length != 2) {
                throw new Exception("Error parsing PBS log - parameter: " + element);
            }

            if (params[0].equals("user")) {
                jobInfo.put("sOwner", params[1]);
            }
            if (params[0].equals("resources_used.cput")) {
                jobInfo.put("dCpuTime", Double.valueOf(parsePBSTime(params[1]) / ((double) SEC_MILLIS)));
            }
            if (params[0].equals("resources_used.walltime")) {
                jobInfo.put("dWallClockTime", Double.valueOf(parsePBSTime(params[1]) / ((double) SEC_MILLIS)));
            }
            if (params[0].equals("Exit_status")) {
                jobInfo.put("iExitCode", Integer.valueOf(params[1]));
            }
            if (params[0].equals("start")) {
                long lVal = Long.parseLong(params[1]) * 1000;
                jobInfo.put("lStartDate", Long.valueOf(lVal));
            }
        }

        return jobInfo;
    }

    /**
     * Parses the output of the qstat command.
     * 
     * @param buff
     *            The output of the qstat command.
     * @param nCommandsToCheck
     *            The number of commands that were executed.
     * @param checkExitStatus
     *            Specifies whether the commands' exit status
     *            should be verified.
     * @return A Vector with JobInfoExt objects correspunding to the current jobs
     * @throws Exception
     */
    public static Vector<JobInfoExt> parsePBSOutput(BufferedReader buff, int nCommandsToCheck, boolean checkExitStatus)
            throws Exception {
        /*
         * Job states:
         * R - job is running.
         * C - Job is completed and leaving the queue on it's own.
         * E - Job is exiting after having run.
         * H - Job is held.
         * Q - job is queued, eligable to run or routed.
         * T - job is being moved to new location.
         * I - job is idle.
         * W - job is waiting for its execution time
         * (-a option) to be reached.
         * S - (Unicos only) job is suspend.
         * ----------
         * These states are not included in any metric:
         * X - Job is removed from the queue
         */
        Vector<JobInfoExt> ret = new Vector<JobInfoExt>();
        boolean startParsing = false;
        Integer retErrCode = VO_Utils.jobMgrCmdErrors.get(VO_Utils.PBS);
        Hashtable<String, Integer> htQIndex = null;

        // Store the first 10 lines from the command's output so that
        // we can display them if there is an error
        StringBuilder firstLines = new StringBuilder();
        int lineCnt = 0;

        int okCnt = 0; // number of comands executed successfully.

        for (;;) {
            String lin = buff.readLine();

            // --- end of file ----
            if (lin == null) {
                break;
            }

            if (lineCnt < 10) {
                firstLines.append(lin + "\n");
            }
            lineCnt++;
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("Parsing line from PBS qstat output: " + lin);
            }

            if (lin.startsWith(VO_Utils.okString)) {
                okCnt++;
                continue;
            }

            if ((lin.indexOf("Name") >= 0) && (lin.indexOf("User") >= 0)) {
                if (htQIndex == null) {
                    String tmpLin = lin.replaceFirst("Job id", "Job_id").replaceFirst("Time Use", "Time_Use");
                    htQIndex = VO_Utils.getParamIndex(tmpLin);
                }
            }

            if (lin.indexOf("----") >= 0) {
                startParsing = true;
                continue;
            }
            if (startParsing == false) {
                continue;
            }

            // if ( lin.equals("") ) break;

            JobInfoExt jobInfo = new JobInfoExt();
            jobInfo.jobManager = "PBS";

            // Find the specific fields so we can substring the line
            // Job id Name User Time Use S Queue
            // ------------ ---------------- ---------------- -------- - -----
            // 22930.bh1 calmob uscms01 70:21:58 R bg
            //
            String[] columns = SPACE_PATTERN.split(lin.trim());
            if (columns.length > 4) {
                String sParam = VO_Utils.getSParameter("Job_id", columns, htQIndex);
                jobInfo.id = jobInfo.jobManager + "_" + sParam.trim();

                jobInfo.user = VO_Utils.getSParameter("User", columns, htQIndex);
                try {
                    String cputime = VO_Utils.getSParameter("Time_Use", columns, htQIndex);
                    jobInfo.cpu_time = parsePBSTime(cputime) / SEC_MILLIS;

                    String status = VO_Utils.getSParameter("S", columns, htQIndex);
                    if (status.equals("R") || status.equals("S")) {
                        jobInfo.status = "R";
                    } else if (status.equals("E") || status.equals("C")) {
                        jobInfo.status = "F";
                    } else if (status.equals("H")) {
                        jobInfo.status = "H";
                    } else if (status.equals("Q") || status.equals("T") || status.equals("I") || status.equals("W")) {
                        jobInfo.status = "I";
                    } else {
                        jobInfo.status = "U";
                    }
                } catch (Exception e) {
                    Integer errCode = VO_Utils.jobMgrRecordErrors.get(VO_Utils.PBS);
                    MLLogEvent<String, Integer> mlle = new MLLogEvent<String, Integer>();
                    mlle.logParameters.put("Error Code", errCode);
                    logger.log(Level.INFO, "Error parsing PBS qstat line: " + lin + "\n", new Object[] { mlle, e });
                }
                ret.add(jobInfo);
            } // end if > 4

        } // end of for

        if ((okCnt < nCommandsToCheck) && checkExitStatus) {
            // logger.warning("Error output from PBS qstat: " + firstLines.toString());
            throw new ModuleException("The PBS qstat command returned with error - command output: \n"
                    + firstLines.toString(), retErrCode.intValue());
        }

        return ret;

    } // end method

    /**
     * Parses the output of the qstat -a command.
     * 
     * @param buff
     *            The output of the qstat command.
     * @param nCommandsToCheck
     *            The number of commands that were executed.
     * @param checkExitStatus
     *            Specifies whether the commands' exit status
     *            should be verified.
     * @return A Hashtable with JobInfoExt objects correspunding to the current
     *         jobs, indexed by the job IDs.
     * @throws Exception
     */
    public static Hashtable<String, JobInfoExt> parseQstatAOutput(BufferedReader buff, int nCommandsToCheck,
            boolean checkExitStatus) throws Exception {
        /*
         * Job states:
         * R - job is running.
         * C - Job is completed and leaving the queue on it's own.
         * E - Job is exiting after having run.
         * H - Job is held.
         * Q - job is queued, eligable to run or routed.
         * T - job is being moved to new location.
         * I - job is idle.
         * W - job is waiting for its execution time
         * (-a option) to be reached.
         * S - (Unicos only) job is suspend.
         * ----------
         * These states are not included in any metric:
         * X - Job is removed from the queue
         */
        Hashtable<String, JobInfoExt> ret = new Hashtable<String, JobInfoExt>();
        Hashtable<String, Integer> htQIndex = null;
        boolean startParsing = false;
        Integer retErrCode = VO_Utils.jobMgrCmdErrors.get(VO_Utils.PBS2);

        // Store the first 10 lines from the command's output so that
        // we can display them if there is an error
        StringBuilder firstLines = new StringBuilder();
        int lineCnt = 0;

        int okCnt = 0; // number of comands executed successfully.

        for (;;) {
            String lin = buff.readLine();

            // --- end of file ----
            if (lin == null) {
                break;
            }

            if (lineCnt < 10) {
                firstLines.append(lin + "\n");
            }
            lineCnt++;
            logger.finest("Parsing line from PBS qstat output: " + lin);

            if (lin.startsWith(VO_Utils.okString)) {
                okCnt++;
                continue;
            }

            if ((lin.indexOf("Job ID") >= 0) && (lin.indexOf("Time") >= 0)) {
                if (htQIndex == null) {
                    String tmpLin = lin.replaceFirst("Job ID", "Job_ID").replaceFirst("Time", "RTime");
                    htQIndex = VO_Utils.getParamIndex(tmpLin);
                }
            }

            if (lin.indexOf("----") >= 0) {
                startParsing = true;
                continue;
            }
            if (startParsing == false) {
                continue;
            }

            // if ( lin.equals("") ) break;

            JobInfoExt jobInfo = new JobInfoExt();
            jobInfo.jobManager = "PBS";

            // Req'd Req'd Elap
            // Job ID Username Queue Jobname SessID NDS TSK Memory Time S Time
            // --------------- -------- -------- ---------- ------ --- --- ------ ----- - -----
            // 146860.red xjwu zeng_lon BN 23053 1 4 -- 5000: R 11:57
            String[] columns = SPACE_PATTERN.split(lin.trim());
            if (columns.length > 10) {
                String sParam = VO_Utils.getSParameter("Job_ID", columns, htQIndex);
                jobInfo.id = jobInfo.jobManager + "_" + sParam.trim();
                jobInfo.user = VO_Utils.getSParameter("Username", columns, htQIndex);

                String status = VO_Utils.getSParameter("S", columns, htQIndex);
                if (status.equals("R") || status.equals("S")) {
                    jobInfo.status = "R";
                } else if (status.equals("E") || status.equals("C")) {
                    jobInfo.status = "F";
                } else if (status.equals("H")) {
                    jobInfo.status = "H";
                } else if (status.equals("Q") || status.equals("T") || status.equals("I") || status.equals("W")) {
                    jobInfo.status = "I";
                } else {
                    jobInfo.status = "U";
                }
                try {
                    String sRuntime = VO_Utils.getSParameter("Time", columns, htQIndex);
                    jobInfo.run_time = parseQstatATime(sRuntime) / SEC_MILLIS;
                } catch (Exception e) {
                    // we probably have a "--" here
                    Integer errCode = VO_Utils.jobMgrRecordErrors.get(VO_Utils.PBS);
                    MLLogEvent<String, Integer> mlle = new MLLogEvent<String, Integer>();
                    mlle.logParameters.put("Error Code", errCode);
                    logger.log(Level.FINE, "Error parsing PBS qstat -a line: " + lin + "\n", new Object[] { mlle, e });
                }
                ret.put(jobInfo.id, jobInfo);
            }

        } // end of for

        if ((okCnt < nCommandsToCheck) && checkExitStatus) {
            // logger.warning("Error output from PBS qstat: " + firstLines.toString());
            throw new ModuleException("The PBS qstat -a command returned with error - command output: \n"
                    + firstLines.toString(), retErrCode.intValue());
        }

        return ret;

    } // end method

    /**
     * Parses the time field from the output of the qstat command.
     * The format of the filed: hh:mm:ss
     * 
     * @return Time in milliseconds.
     */
    private static long parsePBSTime(String cpuTime) {
        long sum = 0;

        String[] hms = COL_PATTERN.split(cpuTime);
        if (hms.length == 3) {
            sum += ((Long.valueOf(hms[0]).longValue() * HOUR_MILLIS) + (Long.valueOf(hms[1]).longValue() * MIN_MILLIS) + (Long
                    .valueOf(hms[2]).longValue() * SEC_MILLIS));
        } else if (hms.length == 2) {
            sum += ((Long.valueOf(hms[0]).longValue() * MIN_MILLIS) + (Long.valueOf(hms[1]).longValue() * SEC_MILLIS));
        } else if (hms.length == 1) {
            sum += Long.valueOf(hms[0]).longValue() * SEC_MILLIS;
        }
        return sum;
    }

    /**
     * Parses the run time field from the output of the qstat -a command.
     * The format of the filed: hh:mm
     * 
     * @return Time in milliseconds.
     */
    private static long parseQstatATime(String cpuTime) {
        long sum = 0;

        String[] hms = COL_PATTERN.split(cpuTime);
        if (hms.length == 2) {
            sum += ((Long.valueOf(hms[0]).longValue() * HOUR_MILLIS) + (Long.valueOf(hms[1]).longValue() * MIN_MILLIS));
        } else if (hms.length == 1) {
            sum += (Long.valueOf(hms[0]).longValue() * MIN_MILLIS);
        }
        return sum;
    }

    /**
     * Scans a directory containing log files named after the date in which they
     * are created and returns the last log.
     */
    public static String getLastLog(File logDir) {
        File[] logs = logDir.listFiles();
        if ((logs == null) || (logs.length == 0)) {
            return null;
        }

        String lastLog = logs[0].getName();
        int idx = 0;
        for (int i = 1; i < logs.length; i++) {
            if (lastLog.compareTo(logs[i].getName()) < 0) {
                lastLog = logs[i].getName();
                idx = i;
            }
        }
        String thePath = null;
        try {
            thePath = logs[idx].getCanonicalPath();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Unable to get cannonical path for: " + logs[idx] + " Cause:", t);
        }
        return thePath;
    }
}
