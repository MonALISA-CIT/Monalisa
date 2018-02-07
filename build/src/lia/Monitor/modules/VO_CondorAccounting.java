package lia.Monitor.modules;

import java.io.BufferedReader;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import lia.util.LogWatcher;
import lia.util.logging.MLLogEvent;

/**
 * Helper class used by the monOsgVoJobs module to obtain accounting information
 * from Condor. It parses the output of the condor_q command (to obtain information
 * for currently running jobs) and the Condor history file (to obtain information
 * about the finished jobs).
 */
public class VO_CondorAccounting {

    private static final Logger logger = Logger.getLogger(VO_CondorAccounting.class.getName());

    /**
     * Array with the names we use for the ClassAd parameters; a character
     * is inserted before the ClassAd name to indicate the type of the parameter.
     */
    public static String classAdParams[];

    /**
     * Hashtable that maps the names of the parameters from Condor ClassAds
     * to the names we use.
     */
    public static final HashMap<String, String> htParamNames = new HashMap<String, String>();

    /**
     * Table with the values that some of the parameters will be multiplied with.
     */
    public static final HashMap<String, Number> valMultipliers = new HashMap<String, Number>();

    /**
     * Shows the correspondence between the parameter types and the condor_q -format
     * specifiers.
     */
    public static final HashMap<String, String> formatSpecifiers = new HashMap<String, String>();

    // Mapping between the integer values of the job statuses and the corresponding
    // Strings:
    // 5 - H (on hold),
    // 2 - R (running),
    // 1 - I (idle, waiting for a machine to execute on),
    // --------------------
    // These states are not inlcuded in any metric:
    // 4 - C (completed),
    // ? - U (unexpanded - never been run),
    // 3 - R (removed).
    protected String[] jobStatusNames = { "X", "I", "R", "R", "C", "H" };

    /*
     * Buffer used to keep the lines from the history file that contain
     * information for a job.
     */
    // StringBuilder logBuffer = null;
    /*
     * Used to store information about the job currently read from the
     * history file.
     */
    // protected Hashtable htJobInfo;

    /*
     * Used to obtain only the lines that were added to the history file
     * since the previous read operation.
     */
    protected LogWatcher[] watchers = null;

    /*
     * Indicates whether a warning should be logged if the output of the
     * condor_q command is empty or if there was an error. A warning will only be
     * logged the first time in a series of consecutive errors.
     */
    volatile boolean logWarning = true;

    /*
     * Shows whether we can use the plain condor_q command in addition to condor_q -l.
     * If the schedd name given by condor_q is different than the one given by GlobalJobId
     * in condor_q -l, we can't correlate the info from condor_q and condor_q -l.
     */
    volatile boolean canUseCondorQ = true;

    private static final Pattern SPACE_PATTERN = Pattern.compile("(\\s)+");

    private static final Pattern DOT_PATTERN = Pattern.compile("\\.");

    private static final Pattern ID_PATTERN = Pattern.compile("#");

    private static final long SEC_MILLIS = 1000;

    private static final long MIN_MILLIS = 60 * SEC_MILLIS;

    private static final long HOUR_MILLIS = 60 * MIN_MILLIS;

    private static final long DAY_MILLIS = 24 * HOUR_MILLIS;

    static {
        classAdParams = new String[24];
        classAdParams[3] = "lJobStartDate";
        classAdParams[1] = "lCompletionDate";
        classAdParams[2] = "lEnteredCurrentStatus";
        classAdParams[0] = "lClusterId";
        classAdParams[4] = "lProcId";
        classAdParams[5] = "sGlobalJobId";
        classAdParams[23] = "sOwner";
        classAdParams[7] = "sCmd";
        classAdParams[8] = "dLocalUserCpu";
        classAdParams[9] = "dLocalSysCpu";
        classAdParams[10] = "dRemoteUserCpu";
        classAdParams[11] = "dRemoteSysCpu";
        classAdParams[12] = "dRemoteWallClockTime";
        classAdParams[13] = "dImageSize";
        classAdParams[14] = "dBytesSent";
        classAdParams[15] = "dBytesRecvd";
        classAdParams[16] = "dFileReadBytes";
        classAdParams[17] = "dFileWriteBytes";
        classAdParams[18] = "dDiskUsage";
        classAdParams[19] = "lJobStatus";
        classAdParams[20] = "bExitBySignal";
        classAdParams[21] = "lExitCode";
        classAdParams[22] = "lExitStatus";
        classAdParams[6] = "sRemoveReason";

        htParamNames.put("JobStartDate", "lJobStartDate");
        htParamNames.put("CompletionDate", "lCompletionDate");
        htParamNames.put("EnteredCurrentStatus", "lEnteredCurrentStatus");
        htParamNames.put("ClusterId", "lClusterId");
        htParamNames.put("ProcId", "lProcId");
        htParamNames.put("GlobalJobId", "sGlobalJobId");
        htParamNames.put("Owner", "sOwner");
        htParamNames.put("Cmd", "sCmd");
        htParamNames.put("LocalUserCpu", "dLocalUserCpu");
        htParamNames.put("LocalSysCpu", "dLocalSysCpu");
        htParamNames.put("RemoteUserCpu", "dRemoteUserCpu");
        htParamNames.put("RemoteSysCpu", "dRemoteSysCpu");
        htParamNames.put("RemoteWallClockTime", "dRemoteWallClockTime");
        htParamNames.put("ImageSize", "dImageSize");
        htParamNames.put("BytesSent", "dBytesSent");
        htParamNames.put("BytesRecvd", "dBytesRecvd");
        htParamNames.put("FileReadBytes", "dFileReadBytes");
        htParamNames.put("FileWriteBytes", "dFileWriteBytes");
        htParamNames.put("DiskUsage", "dDiskUsage");
        htParamNames.put("JobStatus", "lJobsStatus");
        htParamNames.put("ExitBySignal", "bExitBySignal");
        htParamNames.put("ExitCode", "lExitCode");
        htParamNames.put("ExitStatus", "lExitStatus");
        htParamNames.put("RemoveReason", "sRemoveReason");

        valMultipliers.put("lJobStartDate", Long.valueOf(1000));
        valMultipliers.put("lCompletionDate", Long.valueOf(1000));
        valMultipliers.put("lEnteredCurrentStatus", Long.valueOf(1000));
        valMultipliers.put("dDiskUsage", Double.valueOf(1.0 / 1024.0));
        valMultipliers.put("dImageSize", Double.valueOf(1.0 / 1024.0));

        formatSpecifiers.put("d", "lf");
        formatSpecifiers.put("l", "ld");
        formatSpecifiers.put("s", "s");
        formatSpecifiers.put("b", "d");

    }

    /**
     * Constructor for the VO_CondorAccounting class.
     * 
     * @param histFile
     *            the complete path of the Condor history file.
     */
    public VO_CondorAccounting(Vector<String> histFiles) {
        logger.log(Level.INFO, "[VO_CondorAccounting] Initalizing...");
        if (histFiles != null) {
            watchers = new LogWatcher[histFiles.size()];
            for (int i = 0; i < histFiles.size(); i++) {
                watchers[i] = new LogWatcher((histFiles.get(i)));
            }
        } else {
            final Integer errCode = Integer.valueOf(VO_Utils.CONDOR_NO_HIST);
            final MLLogEvent<String, Integer> mlle = new MLLogEvent<String, Integer>();
            mlle.logParameters.put("Error Code", errCode);
            logger.log(Level.INFO, VO_Utils.voJobsErrCodes.get(errCode), new Object[] { mlle });
            watchers = null;
        }
        // htJobInfo = new Hashtable();
    }

    /**
     * Collect the new information added to the history file since the last
     * time we checked it.
     * 
     * @return A Vector with Hashtables, each hashtable containing information
     *         about a job.
     */
    public Vector<Hashtable<String, Object>> getHistoryInfo() {
        Vector<Hashtable<String, Object>> histInfo = new Vector<Hashtable<String, Object>>();
        Hashtable<String, Object> htJobInfo = new Hashtable<String, Object>();
        String line;
        Integer errCode = VO_Utils.jobMgrLogErrors.get("CONDOR");

        logger.log(Level.FINEST, "Checking Condor history files...");
        if (watchers == null) {
            return null;
        }

        try {
            for (LogWatcher watcher : watchers) {
                final BufferedReader br = watcher.getNewChunk();

                if (br == null) {
                    MLLogEvent<String, Integer> mlle = new MLLogEvent<String, Integer>();
                    mlle.logParameters.put("Error Code", errCode);
                    logger.log(Level.WARNING, VO_Utils.voJobsErrCodes.get(errCode), new Object[] { mlle });
                    return null;
                }

                while ((line = br.readLine()) != null) {
                    // logger.finest("Parsing Condor history line: " + line);
                    if (line.startsWith("***")) { // we have a new record
                        // parse the record that has just finished
                        histInfo.add(htJobInfo);
                        htJobInfo = new Hashtable<String, Object>();
                    } else {
                        parseClassAdLine(line, htJobInfo);
                    }
                }
            }
        } catch (Throwable t) {
            MLLogEvent<String, Integer> mlle = new MLLogEvent<String, Integer>();
            mlle.logParameters.put("Error Code", errCode);
            logger.log(Level.WARNING, "Error reading Condor history log", new Object[] { mlle, t });
            return null;
        }

        return histInfo;
    }

    /**
     * Parses a line from a ClassAd record.
     * 
     * @param lin
     *            The line to be parsed.
     * @param htInfo
     *            Hashtable which holds information from the current job record. After
     *            parsing the parameter value contained in the current line will be added to the hashtable.
     */
    final void parseClassAdLine(String lin, Map<String, Object> htInfo) {
        double dVal;
        long lVal;
        String[] globalIds = null;
        String nameWithoutQuotes = null;

        try {
            StringTokenizer lst = new StringTokenizer(lin, " =");
            String adParamName = lst.nextToken();
            String paramValue = lst.nextToken();

            String modParamName = htParamNames.get(adParamName);
            if (modParamName == null) {
                return;
            }

            final char c = modParamName.charAt(0);

            switch (c) {
            case 'l': {
                // for not overwriting the global IDs, if they exist
                if ((modParamName.equals("lClusterId") || modParamName.equals("lProcId"))
                        && (htInfo.get(modParamName) != null)) {
                    break;
                }
                lVal = Long.parseLong(paramValue);
                Long lMult = (Long) valMultipliers.get(modParamName);
                if (lMult != null) {
                    lVal *= lMult.longValue();
                }
                htInfo.put(modParamName, Long.valueOf(lVal));
                break;
            }
            case 'd': {
                dVal = Double.parseDouble(paramValue);
                Double dMult = (Double) valMultipliers.get(modParamName);
                if (dMult != null) {
                    dVal *= dMult.doubleValue();
                }
                htInfo.put(modParamName, Double.valueOf(dVal));
                break;
            }
            case 's': {
                nameWithoutQuotes = paramValue.replaceAll("\"", "");
                htInfo.put(modParamName, nameWithoutQuotes);
                break;
            }
            case 'b': {
                htInfo.put(modParamName, Boolean.valueOf(paramValue));
                break;
            }
            default: {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "VO_CondorAccounting - parseClassAdLine - ignoring ... classAddParam: '" + c
                            + "' for param: " + modParamName);
                }
            }
            }

            /* for some parameters we need further processing */
            if (adParamName.equals("JobStatus")) {
                lVal = Long.parseLong(paramValue);
                if ((lVal >= 0) && (lVal <= 5)) {
                    htInfo.put("sJobStatus", jobStatusNames[(int) lVal]);
                } else {
                    htInfo.put("sJobStatus", "X"); // unknown status
                }
            }

            if (adParamName.equals("GlobalJobId")) {
                globalIds = ID_PATTERN.split(nameWithoutQuotes);
                htInfo.put("sScheddName", globalIds[0]);
            }

        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error parsing Condor ClassAd parameter: " + lin + " / Cause: ", t);
            Integer errCode = VO_Utils.jobMgrRecordErrors.get(VO_Utils.CONDOR);
            MLLogEvent<String, Integer> mlle = new MLLogEvent<String, Integer>();
            mlle.logParameters.put("Error Code", errCode);
            logger.log(Level.INFO, "Error parsing Condor ClassAd parameter: " + lin + "\n", new Object[] { mlle, t });
        }

        /*
         * if we found a GlobalJobId, replace the ClusterId and ProcId with what
         * we have in GlobalJobId
         */
        if (globalIds != null) {
            try {
                // logger.info("GlobalJobId: " + globalIds[1] + " from arr: " + Arrays.toString(globalIds));
                final boolean bFirst = globalIds[1].indexOf(':') > 0;
                String gSplit = bFirst ? globalIds[1] : globalIds[2];
                if (gSplit.indexOf(':') >= 0) {
                    final String[] tokens = DOT_PATTERN.split(gSplit);
                    long lCluster = Long.parseLong(tokens[0]);
                    long lProc = Long.parseLong(tokens[1]);
                    htInfo.put("lClusterId", Long.valueOf(lCluster));
                    htInfo.put("lProcId", Long.valueOf(lProc));
                }
            } catch (Throwable e) {
                logger.log(Level.WARNING, "Error parsing GlobalJobId for output line:\n" + lin + "\n Cause:", e);
            }
        }
    }

    /**
     * Parses the output of the condor_q -l command, which displays information
     * in the same format as the history file.
     * 
     * @param buff
     *            The output of the condor_q -l command.
     * @param nCommandsToCheck
     *            The number of commands that were executed.
     * @param checkExitStatus
     *            Specifies whether the exit status of the
     *            commands should be checked.
     * @return A Vector with JobInfoExt objects correspunding to the current jobs
     * @throws Exception
     */
    final Vector<JobInfoExt> parseCondorQLongOutput(BufferedReader buff, int nCommandsToCheck, boolean checkExitStatus)
            throws Exception {

        final boolean isFine = logger.isLoggable(Level.FINE);
        final boolean isFiner = isFine || logger.isLoggable(Level.FINER);
        final boolean isFinest = isFiner || logger.isLoggable(Level.FINEST);

        final StringBuilder fullOutput = (isFiner) ? new StringBuilder(16384) : null;

        final Vector<JobInfoExt> ret = new Vector<JobInfoExt>();
        // StringBuilder sb = new StringBuilder();
        HashMap<String, Object> qInfo = new HashMap<String, Object>();
        Integer parseErrCode = VO_Utils.jobMgrParseErrors.get(VO_Utils.CONDOR);
        Integer retErrCode = VO_Utils.jobMgrCmdErrors.get(VO_Utils.CONDOR);

        // shows if we have a new ClassAd to parse
        boolean haveNewRecord = false;
        // shows if the command returned an error message
        boolean haveErrorOutput = true;
        // shows if the output of the command was empty
        boolean haveEmptyOutput = true;

        // the hostname of the machine where the schedd runs
        String scheddName = InetAddress.getLocalHost().getHostName();

        // the number of commands executed successfully
        int okCnt = 0;

        // Store the first 10 lines from the command's output so that
        // we can display them if there is an error
        StringBuilder firstLines = new StringBuilder();
        int lineCnt = 0;
        final char EOL = '\n';

        try {
            for (;;) {

                final String lin = buff.readLine();
                if (lin == null) {
                    break;
                }

                if (lineCnt < 10) {
                    firstLines.append(lin).append(EOL);
                }
                lineCnt++;

                if (isFiner) {
                    fullOutput.append(lin).append(EOL);
                    if (isFinest) {
                        logger.log(Level.FINEST, "condor_q -l lin[" + lineCnt + "] >" + lin);
                    }
                }

                // skip the empty lines (except the ones that separate the ClassAds)
                if ((lin.length() == 0) && !haveNewRecord) {
                    continue;
                }

                if (lin.startsWith(VO_Utils.okString)) {
                    okCnt++;
                    continue;
                }

                if (lin.indexOf("All queues are empty") >= 0) {
                    haveErrorOutput = false;
                    continue;
                }

                if (lin.length() > 0) {
                    haveEmptyOutput = false;
                }

                if (lin.indexOf("-- Schedd") >= 0) {
                    StringTokenizer st = new StringTokenizer(lin, ": ");
                    st.nextToken();
                    st.nextToken();
                    scheddName = st.nextToken();
                    haveErrorOutput = false;
                    continue;
                }

                // skip the additional messages: lines that start
                // with "--" and lines that start with whitespace
                if (lin.length() > 0) {
                    if (lin.startsWith("--") || (Character.isWhitespace(lin.charAt(0)) && !haveNewRecord)) {
                        haveErrorOutput = false;
                        continue;
                    }
                }

                if (lin.length() != 0) {
                    if (lin.indexOf("=") > 0) {
                        // we probably have a ClassAd
                        haveErrorOutput = false;
                        // sb.append(lin + "\n");
                        parseClassAdLine(lin, qInfo);
                        haveNewRecord = true;
                    }

                } else if ((lin.length() == 0) && haveNewRecord && !haveErrorOutput) {
                    // the record for a job is finished, parse it
                    haveNewRecord = false;
                    JobInfoExt jobInfo = extractJobInfo(qInfo, scheddName);
                    if (jobInfo != null) {
                        ret.add(jobInfo);
                        if (!jobInfo.serverName.equals(scheddName)) {
                            logger.warning("Inconsistent schedd names in condor_q and condor_q -l; condor_q will not be used further...");
                            this.canUseCondorQ = false;
                        }
                    } else {
                        haveErrorOutput = true;
                        break;
                    }

                    // sb = new StringBuilder("");
                    qInfo = new HashMap<String, Object>(); // parseClassAdRecord(sb);
                } // end of if
            } // end of for

            if (logWarning) {
                if (haveErrorOutput && !haveEmptyOutput) {
                    logger.log(Level.INFO, "The condor_q -l command output has an unrecognized format.");
                }

                if (haveEmptyOutput) {
                    logger.log(Level.FINE, "The condor_q -l command has an empty output.");
                }
                logWarning = false;
            }

            if (!haveErrorOutput && !haveEmptyOutput) {
                logWarning = true;
            }

            if (haveErrorOutput && !haveEmptyOutput) {
                // logger.warning("Error output from condor_q -l: " + firstLines.toString());
                throw new ModuleException(VO_Utils.voJobsErrCodes.get(parseErrCode) + "\n Command output:\n"
                        + firstLines.toString(), parseErrCode.intValue());
            }

            if ((okCnt < nCommandsToCheck) && checkExitStatus) {
                // logger.warning("Error output from condor_q -l: " + firstLines.toString());
                throw new ModuleException(VO_Utils.voJobsErrCodes.get(retErrCode) + "\n Command output:\n"
                        + firstLines.toString(), retErrCode.intValue());
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "parseCondorQLongOutput got exception - command output: First lines:\n "
                    + firstLines.toString() + "\n. Cause:", t);
            throw new Exception(t);
        } finally {
            if (isFine) {
                if (isFiner) {
                    final StringBuilder finMsg = new StringBuilder(fullOutput.length() + 512);
                    finMsg.append("\n\n Full parseCondorQLongOutput: \n\n").append(fullOutput.toString())
                            .append("\n - parseCondorQLongOutput returning: ").append(ret.size())
                            .append(" results. Parsed: " + lineCnt + " lines. okCnt=" + okCnt);
                    logger.log(Level.FINER, finMsg.toString());
                } else {
                    logger.log(Level.FINE, "\n - parseCondorQLongOutput returning: " + ret.size()
                            + " results. Parsed: " + lineCnt + " lines. okCnt=" + okCnt);
                }
            }
        }

        return ret;
    } // end method

    /**
     * Parses the output of the condor_q command, executed with "-format" options.
     * 
     * @param buff
     *            The output of the condor_q -format command.
     * @param nCommandsToCheck
     *            The number of commands that were executed.
     * @param checkExitStatus
     *            Specifies whether the exit status of the
     *            commands should be verified.
     * @return A Vector with JobInfoExt objects correspunding to the current jobs
     * @throws Exception
     */
    final Vector<JobInfoExt> parseCondorQFormatOutput(BufferedReader buff, int nCommandsToCheck, boolean checkExitStatus)
            throws Exception {
        int lineCnt = 0;

        final boolean isFine = logger.isLoggable(Level.FINE);
        final boolean isFiner = isFine || logger.isLoggable(Level.FINER);
        final boolean isFinest = isFiner || logger.isLoggable(Level.FINEST);

        final StringBuilder fullOutput = (isFiner) ? new StringBuilder(16384) : null;

        Vector<JobInfoExt> ret = new Vector<JobInfoExt>();
        Hashtable<String, Object> qInfo = new Hashtable<String, Object>();
        Integer parseErrCode = VO_Utils.jobMgrParseErrors.get(VO_Utils.CONDOR3);
        Integer retErrCode = VO_Utils.jobMgrCmdErrors.get(VO_Utils.CONDOR3);

        boolean haveNewRecord = false;
        boolean haveErrorOutput = true; // shows if the command executed successfully
        boolean haveEmptyOutput = true;

        int okCnt = 0; // number of commands executed successfully

        String scheddName = InetAddress.getLocalHost().getHostName();

        // Store the first 10 lines from the command's output so that
        // we can display them if there is an error
        StringBuilder firstLines = new StringBuilder();

        final char EOL = '\n';

        try {
            for (;;) {
                final String lin = buff.readLine();
                if (lin == null) {
                    break;
                }

                if (lineCnt < 10) {
                    firstLines.append(lin).append(EOL);
                }
                lineCnt++;

                if (isFiner) {
                    fullOutput.append(lin).append(EOL);
                    if (isFinest) {
                        logger.log(Level.FINEST, "condor_q -format lin[" + lineCnt + "]: " + lin);
                    }
                }

                // skip the empty lines (except the ones that separate the ClassAds)
                if ((lin.length() == 0) && !haveNewRecord) {
                    continue;
                }

                if (lin.startsWith(VO_Utils.okString)) {
                    okCnt++;
                    continue;
                }

                if (lin.indexOf("All queues are empty") >= 0) {
                    haveErrorOutput = false;
                    continue;
                }

                if (lin.length() > 0) {
                    haveEmptyOutput = false;
                }

                // normally a "-- Schedd" line shouldn't be here...
                // but check this just in case
                if (lin.indexOf("-- Schedd") >= 0) {
                    StringTokenizer st = new StringTokenizer(lin, ": ");
                    st.nextToken();
                    st.nextToken();
                    scheddName = st.nextToken();
                }

                // skip the additional messages: lines that start
                // with "--" and lines that start with whitespace
                if (lin.length() > 0) {
                    if (lin.startsWith("--") || (Character.isWhitespace(lin.charAt(0)) && !haveNewRecord)) {
                        continue;
                    }
                }

                if (lin.startsWith("MLFORMAT_OK_START")) {
                    haveErrorOutput = false;
                    haveNewRecord = true;
                    continue;
                }

                if (lin.indexOf("=") > 0) {
                    // we probably have a ClassAd
                    parseClassAdLine(lin, qInfo);
                }

                if (lin.startsWith("MLFORMAT_OK_STOP")) {
                    // the record for a job is finished, parse it
                    haveNewRecord = false;
                    JobInfoExt jobInfo = extractJobInfo(qInfo, scheddName);
                    if (jobInfo != null) {
                        ret.add(jobInfo);
                    } else {
                        haveErrorOutput = true;
                        break;
                    }
                    qInfo = new Hashtable<String, Object>();
                } // end of if
            } // end of for

            if (haveNewRecord) {
                haveErrorOutput = true;
            }

            if (logWarning) {
                if (haveErrorOutput && !haveEmptyOutput) {
                    logger.log(Level.INFO, "The condor_q -format command output has an unrecognized format.");
                }

                if (haveEmptyOutput) {
                    logger.log(Level.INFO, "The condor_q -format command has an empty output.");
                }
                logWarning = false;
            }

            if (!haveErrorOutput && !haveEmptyOutput) {
                logWarning = true;
            }

            if (haveErrorOutput && !haveEmptyOutput) {
                // logger.warning("Error output from condor_q -format: " + firstLines.toString());
                throw new ModuleException(VO_Utils.voJobsErrCodes.get(parseErrCode) + "\n Command output:\n"
                        + firstLines.toString(), parseErrCode.intValue());
            }

            if ((okCnt < nCommandsToCheck) && checkExitStatus) {
                // logger.warning("Error output from condor_q -format: " + firstLines.toString());
                throw new ModuleException(VO_Utils.voJobsErrCodes.get(retErrCode) + "\n Command output:\n"
                        + firstLines.toString(), retErrCode.intValue());
            }

        } catch (Throwable t) {
            logger.log(Level.WARNING,
                    "parseCondorQFormatOutput got exception - command output:\n" + firstLines.toString()
                            + "\n\n Cause:", t);
            throw new Exception(t);
        } finally {
            if (isFine) {
                if (isFiner) {
                    final StringBuilder finMsg = new StringBuilder(fullOutput.length() + 512);
                    finMsg.append("\n\n Full parseCondorQFormatOutput: \n\n").append(fullOutput.toString())
                            .append("\n - parseCondorQFormatOutput returning: ").append(ret.size())
                            .append(" results. Parsed: " + lineCnt + " lines. okCnt=" + okCnt);
                    logger.log(Level.FINER, finMsg.toString());
                } else {
                    logger.log(Level.FINE, "\n - parseCondorQFormatOutput returning: " + ret.size()
                            + " results. Parsed: " + lineCnt + " lines. okCnt=" + okCnt);
                }
            }
        }

        return ret;
    } // end method

    /**
     * Parses the output of the condor_q command.
     * 
     * @param buff
     *            The output of the condor_q command.
     * @param nComandsToCheck
     *            The number of commands that were executed.
     * @param checkExitStatus
     *            Specifies whether the exit status of the
     *            commands should be verified.
     * @return A hashtable with JobInfoExt objects correspunding to the jobs
     * @throws Exception
     */

    final Hashtable<String, JobInfoExt> parseCondorQOutput(BufferedReader buff, int nCommandsToCheck,
            boolean checkExitStatus) throws Exception {

        final boolean isFine = logger.isLoggable(Level.FINE);
        final boolean isFiner = isFine || logger.isLoggable(Level.FINER);
        final boolean isFinest = isFiner || logger.isLoggable(Level.FINEST);

        final StringBuilder fullOutput = (isFiner) ? new StringBuilder(16384) : null;

        // --------------------------------------------------------------------
        // The integer values of the job statuses:
        // 5 - H (on hold),
        // 2 - R (running),
        // 1 - I (idle, waiting for a machine to execute on),
        // --------------------
        // These states are not inlcuded in any metric:
        // 4 - C (completed),
        // ? - U (unexpanded - never been run),
        // 3 - R (removed).

        Hashtable<String, JobInfoExt> htRet = new Hashtable<String, JobInfoExt>();
        String scheddName = InetAddress.getLocalHost().getHostName();
        boolean haveErrorOutput = true;
        boolean haveEmptyOutput = true;
        Integer parseErrCode = VO_Utils.jobMgrParseErrors.get(VO_Utils.CONDOR2);
        Integer retErrCode = VO_Utils.jobMgrCmdErrors.get(VO_Utils.CONDOR2);

        // Store the first 10 lines from the command's output so that
        // we can display them if there is an error
        StringBuilder firstLines = new StringBuilder();
        int lineCnt = 0;
        final char EOL = '\n';

        int okCnt = 0; // the number of commands executed successfully
        try {
            boolean canProcess = false;
            for (;;) {

                final String lin = buff.readLine();
                if (lin == null) {
                    break;
                }

                if (lineCnt < 10) {
                    firstLines.append(lin).append(EOL);
                }
                lineCnt++;

                if (isFiner) {
                    fullOutput.append(lin).append(EOL);
                    if (isFinest) {
                        logger.log(Level.FINEST, "condor_q lin[" + lineCnt + "] >" + lin);
                    }
                }

                if (lin.equals("")) {
                    continue;
                }

                if (lin.indexOf("All queues are empty") >= 0) {
                    haveErrorOutput = false;
                    continue;
                }

                if (lin.startsWith(VO_Utils.okString)) {
                    okCnt++;
                    continue;
                }

                if (lin.length() > 0) {
                    haveEmptyOutput = false;
                }

                // Find the specific fields so we can substring the line
                // ID OWNER SUBMITTED RUN_TIME ST PRI SIZE CMD
                // 185.0 sdss 10/8 20:09 0+00:00:00 R 0 0.0 data
                //
                if (!canProcess && (lin.indexOf("ID") != -1) && (lin.indexOf("OWNER") != -1)) {
                    canProcess = true;
                    haveErrorOutput = false;
                    continue;
                }

                // if we have a line like:
                // 35 jobs; 2 idle, 33 running, 0 held
                if (lin.indexOf("jobs;") != -1) {
                    canProcess = false;
                    continue;
                }

                // if we have a line like:
                // -- Schedd: tier2b.cacr.caltech.edu : <192.168.0.254:33273>
                if (lin.indexOf("-- Schedd") >= 0) {
                    StringTokenizer st = new StringTokenizer(lin, ": ");
                    st.nextToken();
                    st.nextToken();
                    scheddName = st.nextToken();
                    haveErrorOutput = false;
                    continue;
                }

                // skip the additional messages: lines that start
                // with "--"
                if (lin.length() > 0) {
                    if (lin.startsWith("--")) {
                        haveErrorOutput = false;
                        continue;
                    }
                }

                if (canProcess) {
                    String[] columns = SPACE_PATTERN.split(lin.trim());

                    if (columns.length > 6) {
                        JobInfoExt jobInfo = new JobInfoExt();
                        jobInfo.jobManager = "CONDOR";

                        String condorid = columns[0]; // .replaceFirst(".0", "");
                        jobInfo.id = jobInfo.jobManager + "_" + condorid;
                        if (scheddName != null) {
                            jobInfo.id += ("_" + scheddName);
                        }

                        try {
                            jobInfo.user = columns[1];
                            jobInfo.date = columns[2];
                            jobInfo.time = columns[3];
                            jobInfo.run_time = parseCondorTime(columns[4]) / SEC_MILLIS;
                            jobInfo.status = columns[5];
                            jobInfo.priority = columns[6];
                            jobInfo.size = Double.parseDouble(columns[7]);
                        } catch (Exception e) {
                            Integer errCode = VO_Utils.jobMgrRecordErrors.get(VO_Utils.CONDOR);
                            MLLogEvent<String, Integer> mlle = new MLLogEvent<String, Integer>();
                            mlle.logParameters.put("Error Code", errCode);
                            logger.log(Level.INFO, "Error parsing condor_q line: " + lin + "\n",
                                    new Object[] { mlle, e });
                            continue;
                        }
                        htRet.put(jobInfo.id, jobInfo);
                    } // end of if
                } else { // canProcess == false
                    // throw new Exception("condor_q returned error");
                }
            } // end of for

            String errMsg = "Error output from condor_q: " + firstLines.toString();
            if (haveErrorOutput && !haveEmptyOutput) {
                // logger.warning(errMsg);
                throw new ModuleException(VO_Utils.voJobsErrCodes.get(parseErrCode) + "\n" + errMsg,
                        parseErrCode.intValue());
            }

            if ((okCnt < nCommandsToCheck) && checkExitStatus) {
                // logger.warning(errMsg);
                throw new ModuleException(VO_Utils.voJobsErrCodes.get(retErrCode) + "\n" + errMsg,
                        retErrCode.intValue());
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "parseCondorQOutput got exception - command output\n" + firstLines.toString()
                    + "\n Cause:", t);
            throw new Exception(t);
        } finally {
            if (isFine) {
                if (isFiner) {
                    final StringBuilder finMsg = new StringBuilder(fullOutput.length() + 512);
                    finMsg.append("\n\n Full parseCondorQOutput: \n\n").append(fullOutput.toString())
                            .append("\n - parseCondorQOutput returning: ").append(htRet.size())
                            .append(" results. Parsed: " + lineCnt + " lines. okCnt=" + okCnt);
                    logger.log(Level.FINER, finMsg.toString());
                } else {
                    logger.log(Level.FINE, "\n - parseCondorQOutput returning: " + htRet.size() + " results. Parsed: "
                            + lineCnt + " lines. okCnt=" + okCnt);
                }
            }
        }

        return htRet;
    } // end method

    /**
     * Creates a JobInfo objects with the attributes contained in the
     * hashtable given as parameter.
     * 
     * @param jmParams
     *            Hashtable that contains (paramName, paramValue) pairs
     *            for the job and was obtained from parsing a ClassAd.
     * @param scheddName
     *            The name of the schedd daemon that handles the job.
     */
    protected JobInfoExt extractJobInfo(Map<String, Object> jmParams, String scheddName) {
        JobInfoExt jobInfo = new JobInfoExt();
        String paramsScheddName = (String) jmParams.get("sScheddName");
        if (paramsScheddName != null) {
            jobInfo.serverName = paramsScheddName;
        } else {
            jobInfo.serverName = scheddName;
        }

        jobInfo.jobManager = "CONDOR";

        jobInfo.user = (String) jmParams.get("sOwner");
        String clusterId = ((Long) jmParams.get("lClusterId")).toString();
        String procId = ((Long) jmParams.get("lProcId")).toString();
        jobInfo.id = jobInfo.jobManager + "_" + clusterId + "." + procId;
        if (scheddName != null) {
            jobInfo.id += ("_" + jobInfo.serverName);
        }

        // if some essential job parameters are missing
        if ((jobInfo.user == null) || (clusterId == null) || (procId == null)) {
            MLLogEvent<String, Integer> mlle = new MLLogEvent<String, Integer>();
            mlle.logParameters.put("Error Code", VO_Utils.jobMgrRecordErrors.get(VO_Utils.CONDOR));
            logger.log(Level.INFO, "Incompletely specified Condor job", new Object[] { mlle });
            return null;
        }

        Double runtime = (Double) jmParams.get("dRemoteWallClockTime");
        if (runtime != null) {
            jobInfo.run_time = runtime.longValue();
        }

        jobInfo.status = (String) jmParams.get("sJobStatus");
        Double imsize = (Double) jmParams.get("dImageSize");
        if (imsize != null) {
            jobInfo.size = imsize.doubleValue();
        }

        Double dusage = (Double) jmParams.get("dDiskUsage");
        if (dusage != null) {
            jobInfo.disk_usage = dusage.doubleValue();
        } else {
            logger.info("Disk usage not defined for Condor job");
        }

        Double remoteCpuUsr = (Double) jmParams.get("dRemoteUserCpu");
        Double localCpuUsr = (Double) jmParams.get("dLocalUserCpu");
        Double remoteCpuSys = (Double) jmParams.get("dRemoteSysCpu");
        Double localCpuSys = (Double) jmParams.get("dLocalSysCpu");

        if ((localCpuUsr != null) && (remoteCpuUsr != null) && (localCpuSys != null) && (remoteCpuSys != null)) {
            jobInfo.cpu_time = localCpuUsr.longValue() + remoteCpuUsr.longValue() + localCpuSys.longValue()
                    + remoteCpuSys.longValue();
        }

        return jobInfo;
    }

    /**
     * Parses the "RUN_TIME" field from the output of the condor_q command.
     * Time is given as dd+hh:mm:ss and the function returns the equivalent
     * number of milliseconds.
     */
    private static long parseCondorTime(String cpuTime) {
        long sum = 0;

        String[] dh = cpuTime.split("\\+");

        sum += Long.parseLong(dh[0]) * DAY_MILLIS;

        String[] hms = dh[1].split(":");

        sum += Long.valueOf(hms[0]).longValue() * HOUR_MILLIS;
        sum += Long.valueOf(hms[1]).longValue() * MIN_MILLIS;
        sum += Long.valueOf(hms[2]).longValue() * SEC_MILLIS;

        return sum;
    }

    /**
     * Adds a Condor command to the buffers that contains the existing commands.
     * 
     * @param buff
     *            Buffer that contains the "condor_q -l" commands.
     * @param buff2
     *            Buffer that contains the plain "condor_q" commands.
     * @param cmd
     *            The "condor_q -l" command.
     * @param cmd2
     *            The plain "condor_q" command.
     */
    public static void updateCondorCommands(StringBuilder buff[], StringBuilder crtCmd[]) {
        int i;
        for (i = 0; i < 3; i++) {
            if (buff[i].length() > 0) {
                buff[i].append(" ; ");
            }
            buff[i].append(crtCmd[i]);
        }

        // add the "-format" options
        // buff[2].append(" '");
        for (i = 0; i < classAdParams.length; i++) {
            String paramType = classAdParams[i].substring(0, 1);
            String paramPlainName = classAdParams[i].substring(1);
            // TODO internal error here
            String option = " -format \\\"";
            if (i == 0) {
                option += "MLFORMAT_OK_START\\\\n";
            }
            option += (paramPlainName + " = %" + formatSpecifiers.get(paramType) + "\\\\n");
            if (i == (classAdParams.length - 1)) {
                option += "MLFORMAT_OK_STOP\\\\n";
            }
            option += ("\\\" " + paramPlainName);
            buff[2].append(option);
        }
        // buff[2].append("'");

        for (i = 0; i < 3; i++) {
            // buff[i].append(" 2>&1");
            buff[i].append(" && echo " + VO_Utils.okString);
        }

    }

}
