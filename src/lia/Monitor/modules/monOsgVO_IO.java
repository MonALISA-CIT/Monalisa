package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.net.InetAddress;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.TimeZone;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.LogWatcher;
import lia.util.logging.MLLogEvent;
import lia.util.ntp.NTPDate;

/**
 * 
 * @author Florin Pop
 * 
 * This module compute the value for this tree structure from globus log file
 * Tree result:
 *     \_user(VO)*
 *          |__ftpInput
 *          |__ftpOutput
 *          |__ftpRateIn
 *          |__ftpRateOut
 *          |__VO_Status
 *          |__(src,dest)*
 *                |__ftpInput_sitename
 *                |__ftpOutput_sitename
 *                |__ftpRateIn_sitename
 *                |__ftpRateOut_sitename
 * The data structure is voTotals from the base class. and voAccts for VO users
 */

public class monOsgVO_IO extends monVoModules implements MonitoringModule {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monOsgVO_IO.class.getName());

    /** serial version number */
    static final long serialVersionUID = 1706200525091981L;

    /** information for email notifyer */
    private static String inNotifyProperty = "lia.Monitor.modules.monOsgVO_IO.emailNotify";
    private static String inStatusNotifyProperty = "lia.Monitor.modules.monOsgVO_IO.statusEmailNotify";
    private static String inResultsEmailedProperty = "lia.Monitor.modules.monOsgVO_IO.maxResultsEmailed";

    /** Module's error codes */
    private int errorCodeParam = 0;
    private Integer errorCode;
    private final int STATUS_SUCCESS = 0;
    private final int STATUS_PARAM_PROCESSING = 1;
    private final int STATUS_FTP_CODE = 2;
    private final int STATUS_ERROR_LOG_LINE = 3;
    private final int STATUS_ERROR_READING_LOG = 4;
    private final int STATUS_ERROR_FINDING_LOG = 5;
    private final int TRANSFER_CODE = 226;

    /** Module status result */
    private Result statusResult;

    /** set's of constants */
    protected final int LINE_ELEMENTS_NO = 16;
    protected final int TIME_TO_REMOVE = 5;

    /** Module name */
    protected static String MyModuleName = "monOsgVO_IO";

    /** strings that represent the MonALISA, vdt and globus location */
    protected String vdtLocation = null;
    protected String globusLocation = null;

    protected static final String GRIDFTP_LOG = "/var/gridftp.log";
    protected String ftplog = null; // GLOBUS_LOCATION + GRIDFTP_LOG;

    /**
     * Name of parameters calculated with this module. To add a parameter, just
     * add his name in the list below.
     */
    protected static String[] MyResTypes = {
    /* 0 */"ftpInput",
    /* 1 */"ftpOutput",
    /* 2 */"ftpRateIn",
    /* 3 */"ftpRateOut"
    /** other name for result parameter */
    };

    /** Module results */
    Vector moduleResults, transferResult;

    /** watcher to log file */
    protected LogWatcher lw = null;

    /** if it is the fisrt time when we run the doProcess */
    protected boolean firstTime = true;
    protected boolean singleTotals = false;

    protected boolean MixedCaseVOs = false;

    protected long call_no = 0;
    protected long lastTime = 0;
    protected double doProcessInterval;
    /** Timestamp for module results */
    protected long resultTime;

    protected boolean haveLogFile = false;
    protected boolean canReadLog = false;

    /** last transfers informations */
    protected Vector tpHist = new Vector();
    protected Vector voHist = new Vector();
    protected static long seq_no = 0;

    /** Total result for all IO */
    UserFTPData monTotals;

    /**
     * {(user): (site1), (site2), ..., (siten)}
     * ........................................
     * {(user): (site1), (site2), ..., (siten)}
     */
    Hashtable siteTable = new Hashtable();

    /** Module constructor */
    public monOsgVO_IO() {
        /** the base class constructor */
        super(MyModuleName, MyResTypes, inNotifyProperty, inStatusNotifyProperty, inResultsEmailedProperty);

        canSuspend = false;
        isRepetitive = true;

        info.ResTypes = ResTypes();
        transferResult = new Vector();
        moduleResults = new Vector();

        /** prepare the new string buffer for new log messages */
        sb = new StringBuilder();

        /** write messages to ML log file */
        logger.log(Level.INFO, "Constructor of " + ModuleName + " at " + currDate);
        logger.log(Level.INFO, "Info content: name: " + info.name + " / id: " + info.id + " / type: " + info.type
                + " / state: " + info.state + " / err: " + info.error_count + ".");
    }

    /**
     * init module with node and arguments configuration file entry:
     * *Node{monOsgVO_IO, localhost, <arguments>}%30 <arguments> is a comme
     * separated list (no quoted) ftplog=/path-to-ftplog
     * mapfile=/globus-location
     */
    @Override
    public MonModuleInfo init(MNode inNode, String args) {
        /** the method name */
        String methodName = "init";
        /** the arguments list from configuration file entry */
        String[] argList = null;

        Node = inNode;
        clusterModuleName = Node.getClusterName() + "-" + ModuleName;
        logger.log(Level.INFO, methodName + "(): Instantiating instance for Cluster (node in cf) " + clusterModuleName
                + " at " + currDate + "\narguments: " + ((args == null) ? "NO ARGUMENTS" : args) + "\nNode Info: name "
                + (Node.name == null ? "null" : Node.name) + " short_name "
                + (Node.name_short == null ? "null" : Node.name_short) + " cl.name "
                + (Node.cluster == null ? "null" : Node.cluster.name) + " fa.name "
                + (Node.farm == null ? "null" : Node.farm.name) + ".");

        /** Check the argument list and process information */
        if (args != null) {
            /** check if file location or globus_location are passed */
            argList = args.split("(\\s)*,(\\s)*");

            for (int i = 0; i < argList.length; i++) {

                argList[i] = argList[i].trim();
                logger.log(Level.INFO, "Argument " + i + ":" + argList[i] + ".");

                /** location of ftp logfile */
                if (argList[i].toLowerCase().indexOf("ftplog") != -1) {
                    try {
                        ftplog = argList[i].split("(\\s)*=(\\s)*")[1].trim();
                        logger.log(Level.INFO, "overrridden FtpLog(" + ftplog + ")");
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, ModuleName + " Got exception parsing server option: " + t);
                    }
                    continue;
                }
                /** location of map file (users - vos) */
                if (argList[i].toLowerCase().indexOf("mapfile") != -1) {
                    try {
                        mapfile = argList[i].split("(\\s)*=(\\s)*")[1].trim();
                        logger.log(Level.INFO, "overrridden MapFile(" + mapfile + ")");

                    } catch (Throwable t) {
                        logger.log(Level.WARNING, ModuleName + " Got exception parsing server option: " + t);
                    }
                    continue;
                }
                /** debug mode */
                if (argList[i].toLowerCase().startsWith("debug")) {
                    debugmode = true;
                    logger.log(Level.INFO, "overrridden Debug(" + debugmode + ")");
                    continue;
                }
                /** Mixed Case Mode for VO name */
                if (argList[i].toLowerCase().startsWith("mixedcasevos")) {
                    MixedCaseVOs = true;
                    logger.log(Level.INFO, "overrridden MixedCaseVOs(" + MixedCaseVOs + ")");
                    continue;
                }
                /** Can suspend module option */
                if (argList[i].toLowerCase().indexOf("cansuspend") != -1) {
                    boolean cSusp = false;
                    try {
                        cSusp = Boolean.valueOf(argList[i].split("(\\s)*=(\\s)*")[1].trim()).booleanValue();
                    } catch (Throwable t) {
                        cSusp = false;
                    }
                    canSuspend = cSusp;
                    logger.log(Level.INFO, "overrridden CanSuspend(" + cSusp + ")");
                    continue;
                }
            }
        }
        info.ResTypes = ResTypes();
        return info;
    }

    /**
     * Set the MonALISA, VDT, Globus locations and other data structures
     * @throws Exception
     */
    protected void setEnvironment() throws Exception {
        /** the method name */
        String methodName = "setEnvironment";

        tmpVoAccts.clear();
        tmpVoMixedCase.clear();
        tmpVoAccts.putAll(voAccts);
        tmpVoMixedCase.putAll(voMixedCase);

        cleanupEnv();

        try {
            initializeEnv();
            environmentSet = true;
        } catch (Exception e1) {
            throw e1;
        }

        computeVoAcctsDiff();

        try {
            /** Determine the MonaALisa_HOME */
            monalisaHome = getEnvValue("MonaLisa_HOME");
            if (monalisaHome == null) {
                throw new Exception("MonaLisa_HOME environmental variable not set.");
            }
            logger.log(Level.INFO, "MonaLisa_HOME = " + monalisaHome);

            /** Determine the VDT_LOCATION */
            vdtLocation = getEnvValue("VDT_LOCATION");
            if (vdtLocation == null) {
                vdtLocation = monalisaHome + "/..";
            }
            logger.log(Level.INFO, "VDT_LOCATION = " + vdtLocation);

            /** Determine the GLOBUS_LOCATION */
            if (ftplog == null) {
                globusLocation = getEnvValue("GLOBUS_LOCATION");
                if (globusLocation == null) {
                    throw new Exception("GLOBUS_LOCATION environmental variable not set.");
                }
                logger.log(Level.INFO, "GLOBUS_LOCATION = " + globusLocation);
            }

            /** set the ftplog path */
            if (ftplog == null) {
                ftplog = globusLocation + GRIDFTP_LOG;
            }
            logger.log(Level.INFO, "ftplog = " + ftplog);

            /** check to see if GRIDFTP_LOG exists and readable */
            File probe = new File(ftplog);
            if (!probe.isFile()) {
                /** set the error code and add log message */
                errorCodeParam = STATUS_ERROR_FINDING_LOG;
                haveLogFile = false;
                /** put error message in MLLogEvent */
                errorCode = Integer.valueOf(errorCodeParam);
                try {
                    MLLogEvent mlle = new MLLogEvent();
                    mlle.logParameters.put("Error Code", errorCode);
                    mlle.logParameters.put(errorCode, VO_Utils.voIOErrCodes.get(errorCode));
                    logger.log(Level.WARNING, "Gridftp log (" + ftplog
                            + ") not found. No GridFTP transfer information will be provide.", new Object[] { mlle });
                } catch (Exception e) {
                }

            } else if (!probe.canRead()) {
                /** set the error code and add log message */
                errorCodeParam = STATUS_ERROR_READING_LOG;
                haveLogFile = false;
                /** put error message in MLLogEvent */
                errorCode = Integer.valueOf(errorCodeParam);
                try {
                    MLLogEvent mlle = new MLLogEvent();
                    mlle.logParameters.put("Error Code", errorCode);
                    mlle.logParameters.put(errorCode, VO_Utils.voIOErrCodes.get(errorCode));
                    logger.log(Level.WARNING, "Gridftp log (" + ftplog
                            + ") is not readable. No GridFTP transfer information will be provide.",
                            new Object[] { mlle });
                } catch (Exception e) {
                }
            } else {
                logger.log(Level.INFO,
                        "Gridftp log (" + ftplog + ")\n  last modified " + (new Date(probe.lastModified())).toString()
                                + " - size(" + probe.length() + " Bytes)");
                haveLogFile = true;
            }

        } catch (Exception ex) {
            logger.log(Level.SEVERE, methodName + "() - " + ex.getMessage() + " " + ex);
            //throw new Exception(methodName + "() - " + ex.getMessage() + " " + ex);
        }
    }

    @Override
    public Object doProcess() throws Exception {
        /** the method name */
        String methodName = "doProcess";

        /** two time stamp: start and finish of doProcess */
        long start, stop;

        /** the vo name mapped from grid3-user-vo-map file */
        Enumeration VOUsers;

        /** start doProcess with success code */
        errorCodeParam = STATUS_SUCCESS;

        try {
            /** start time of doProcess */
            start = NTPDate.currentTimeMillis();
            /** record the start moment (time in miliseconds) */
            setStartTime();

            /** clear result of the doProcess method and local totals data */
            moduleResults.clear();
            transferResult.clear();
            //voTotals.clear();

            /** set the environment (only once, i hope) */
            if (!environmentSet) {
                setEnvironment();
            }

            /** init data for VO_IO_Totals cluster */
            monTotals = !singleTotals ? new UserFTPData(Node.getClusterName() + "_Totals", "Total_Trafic")
                    : new UserFTPData("osgVO_IO_Totals", Node.getClusterName() + "_Totals");

            /** set the status to good */
            statusGood = true;

            /** fist time when I run this method */
            if (firstTime == true) {

                /** for second time */
                firstTime = false;

                /** list of all VO from map file */
                VOUsers = VoList();

                /** first time, create the totals data for VOs */
                while (VOUsers.hasMoreElements()) {

                    String user = (String) VOUsers.nextElement();

                    Vector v = new Vector();
                    v.add(new UserFTPData(user, "totals", false));
                    voTotals.put(user, v);

                    Vector sites = new Vector();
                    siteTable.put(user, sites);
                }

                /** if have a log file, put a watcher on it */
                if (haveLogFile) {
                    lw = new LogWatcher(ftplog);
                }

                /** first time, if you want, module return zero results */
                resultTime = NTPDate.currentTimeMillis();
                moduleResults = createResultsVector();

                /** last time when doProcess was called */
                lastTime = start;

                /** message to ML log file */
                logger.log(Level.INFO, "First time for doProcess(): " + new Date(start));

            } else {
                /** interval between two call of this method (in seconds) */
                doProcessInterval = (start - lastTime) / 1000.0;
                /** last time when doProcess was called */
                lastTime = start;

                /** shouldProceess will be true when we have and can read the gridftp log file */
                boolean shouldProcess = false;

                /** list of all VO from map file */
                VOUsers = VoList();

                /** put in siteTable hash only the new VOs */
                while (VOUsers.hasMoreElements()) {
                    String user = (String) VOUsers.nextElement();
                    if (!siteTable.containsKey(user)) {
                        siteTable.put(user, new Vector());
                    }

                    /** if VO exist, remove all values that refers to the site trasfers. Keep the total and reset it */
                    if (voTotals.containsKey(user)) {
                        Vector values = (Vector) voTotals.get(user);
                        UserFTPData total = (UserFTPData) values.get(0);
                        total.reset();
                        values.clear();
                        values.add(0, total);
                        voTotals.put(user, values);
                    } else {
                        Vector v = new Vector();
                        v.add(new UserFTPData(user, "totals", false));
                        voTotals.put(user, v);
                    }
                }

                /** test the gridftp.log file */
                boolean haveLog;
                File probe = new File(ftplog);
                if (!probe.isFile()) {
                    /** can not find it, change the status */
                    errorCodeParam = STATUS_ERROR_FINDING_LOG;
                    haveLog = false;
                    /** put error message in MLLogEvent */
                    errorCode = Integer.valueOf(errorCodeParam);
                    try {
                        MLLogEvent mlle = new MLLogEvent();
                        mlle.logParameters.put("Error Code", errorCode);
                        mlle.logParameters.put(errorCode, VO_Utils.voIOErrCodes.get(errorCode));
                        logger.log(Level.WARNING, "Gridftp log (" + ftplog
                                + ") not found. No GridFTP transfer information will be provide.",
                                new Object[] { mlle });
                    } catch (Exception e) {
                    }
                } else if (!probe.canRead()) {
                    /** can not read it, change the status */
                    errorCodeParam = STATUS_ERROR_READING_LOG;
                    haveLog = false;
                    /** put error message in MLLogEvent */
                    errorCode = Integer.valueOf(errorCodeParam);
                    try {
                        MLLogEvent mlle = new MLLogEvent();
                        mlle.logParameters.put("Error Code", errorCode);
                        mlle.logParameters.put(errorCode, VO_Utils.voIOErrCodes.get(errorCode));
                        logger.log(Level.WARNING, "Gridftp log (" + ftplog
                                + ") is not readable. No GridFTP transfer information will be provide.",
                                new Object[] { mlle });
                    } catch (Exception e) {
                    }
                } else {
                    haveLog = true;
                }

                /** 
                 * Set the shouldProcess flag.
                 * We test the state of gridtfp.log file for last time and this time.
                 * It is true we process data from log file.
                 */
                if ((haveLogFile == false) && (haveLog == true)) {
                    /** put a watcher to log file */
                    lw = new LogWatcher(ftplog);
                    haveLogFile = true;
                    logger.log(
                            Level.INFO,
                            "Gridftp log (" + ftplog + ")\n  last modified "
                                    + (new Date(probe.lastModified())).toString() + " - size(" + probe.length()
                                    + " Bytes)");
                    shouldProcess = false;
                }
                if ((haveLogFile == true) && (haveLog == true)) {
                    shouldProcess = true;
                }
                if (haveLog == false) {
                    haveLogFile = false;
                }

                if (shouldProcess == true) {
                    /** take into a buffer the last part of gridftp log file */
                    BufferedReader logBuffer = lw.getNewChunk();
                    if (logBuffer == null) {
                        /** change the status */
                        errorCodeParam = STATUS_ERROR_LOG_LINE;
                        /** put error message in MLLogEvent */
                        errorCode = Integer.valueOf(errorCodeParam);
                        try {
                            MLLogEvent mlle = new MLLogEvent();
                            mlle.logParameters.put("Error Code", errorCode);
                            mlle.logParameters.put(errorCode, VO_Utils.voIOErrCodes.get(errorCode) + " NULL buffer.");
                            logger.log(Level.WARNING, "BufferedReader is null for [ " + ftplog + " ] at " + call_no
                                    + "th call.", new Object[] { mlle });
                        } catch (Exception e) {
                        }
                    } else {
                        /** parse and buid the result */
                        try {
                            parseFtpLog(logBuffer);
                        } catch (Throwable t) {
                            if (logBuffer != null) {
                                logBuffer.close();
                            }
                            /** change the status */
                            errorCodeParam = STATUS_ERROR_LOG_LINE;
                            /** put error message in MLLogEvent */
                            errorCode = Integer.valueOf(errorCodeParam);
                            try {
                                MLLogEvent mlle = new MLLogEvent();
                                mlle.logParameters.put("Error Code", errorCode);
                                mlle.logParameters.put(errorCode, VO_Utils.voIOErrCodes.get(errorCode));
                                logger.log(Level.WARNING, "Throwable exception when parse gridftp.log file.",
                                        new Object[] { mlle, t });
                            } catch (Exception e) {
                            }
                        }

                        /** close the buffer */
                        if (logBuffer != null) {
                            logBuffer.close();
                        }

                        /** after the date was cumulated in the hashtabe, compute the rates */
                        computeFTPRate(doProcessInterval);
                        monTotals.computeRate(doProcessInterval);

                        /**
                         * Create the results for update to ML
                         * results in KBytes for ftpInput, ftpOutput, ftpInput_sitename, ftpOutput_sitename
                         * and in KBytes/s for ftpRateIn, ftpRateOut, ftpRateIn_sitename, ftpRateOut_sitename 
                         */
                        resultTime = NTPDate.currentTimeMillis();
                        moduleResults = createResultsVector();
                    }
                }

                /** update transfers results */
                updateTransfers();
                /** add transfer Results that should remove */
                for (int i = 0; i < transferResult.size(); i++) {
                    moduleResults.add(transferResult.get(i));
                }
            }

            /** if grid3-user-vo-map.txt changed, remove old vo (send null eResults for each old vo) */
            if (getShouldNotifyConfig()) {
                for (int i = 0; i < removedVOseResults.size(); i++) {
                    eResult er = removedVOseResults.get(i);
                    logger.fine("Sending eResult for " + er.NodeName);
                    if (er != null) {
                        if (MixedCaseVOs == false) {
                            er.NodeName = er.NodeName.toUpperCase();
                        }
                        er.Module = ModuleName;
                        moduleResults.add(er);
                        siteTable.remove(er.NodeName);
                    }
                }
                logger.info("[ monOsgVO_IO) ] - Notified Config changed");
                setShouldNotifyConfig(false);
            }

            /** record the finish moment (date and time in miliseconds) */
            setFinishTime();
            /** last time when doProcess was called */
            stop = NTPDate.currentTimeMillis();

            /** create the status result */
            statusResult = new Result();
            statusResult.time = resultTime;
            statusResult.NodeName = "Status";
            statusResult.ClusterName = Node.getClusterName() + "_Totals";
            statusResult.FarmName = Node.getFarmName();
            statusResult.Module = ModuleName;
            statusResult.addSet("VO_IO_Status", errorCodeParam);

            moduleResults.add(statusResult);

            /** message to ML log file */
            logger.log(Level.INFO, methodName + " - monOsgVO_IO [" + call_no + "] " + "Sent " + moduleResults.size()
                    + " results. " + "Execution time: " + (stop - start) + " ms. " + "Status=" + errorCodeParam + ": "
                    + VO_Utils.voIOErrCodes.get(Integer.valueOf(errorCodeParam)));

            /** increment call_no */
            call_no = (call_no + 1) % Long.MAX_VALUE;

            // ---- update the results counters ----
            nCumulatedResults += moduleResults.size();
            nCurrentResults = moduleResults.size();

            // ---- save the latest results (to be included in the status email) ----
            lastResults = new StringBuilder();
            int maxResults = maxResultsEmailed;
            if (maxResults > moduleResults.size()) {
                maxResults = moduleResults.size();
            }
            for (int i = 0; i < maxResults; i++) {
                try {
                    lastResults.append("\n[" + i + "]" + moduleResults.elementAt(i));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            /** confirm by Email */
            sendStatusEmail();

        } catch (Throwable ex) {
            statusGood = false;
            logger.log(Level.SEVERE, "[" + call_no + "]: Got exception: " + ex);
            sendExceptionEmail(methodName + " FATAL ERROR - " + ex.getMessage());
        }

        return moduleResults;
    }

    /**
     * Parse the all buffer from log file and add data
     * @param buffer
     * @throws Exception
     */
    protected void parseFtpLog(BufferedReader buffer) throws Exception {
        /** the method name */
        String methodName = "parseFtpLog";
        /** a line from buffer */
        String line;
        /** elements of a line */
        Hashtable lineResult = new Hashtable();
        /** value of lines number for printing process in ML logfile */
        int maxlinesNo = 5;
        /** numer of lines of buffer */
        int linesNo = 0;

        /** parse the buffer (part of a logfile) */
        try {
            while ((line = buffer.readLine()) != null) {
                /** special cases */
                if (line.length() == 0) {
                    continue; // empty line
                }
                if (line.startsWith(COMMENT)) {
                    continue; // comment
                }

                /** process the line from log file */
                lineResult = parseLine(line);
                /** some debug information for first 5 lines */
                if (debugmode && (linesNo < maxlinesNo)) {
                    logger.info(line);
                    if (lineResult != null) {
                        logger.info("Length of result after process line " + linesNo + ": " + lineResult.size());
                    }
                    linesNo++;
                }
                /** process the line result */
                if (lineResult != null) {
                    updateVOData(lineResult);
                }
            }
        } catch (Exception e) {
            String msg = " Exception in processing gridftp log output.";
            /** change the status */
            errorCodeParam = STATUS_ERROR_LOG_LINE;
            /** Send error message and line to MLLogEvent */
            errorCode = Integer.valueOf(errorCodeParam);
            try {
                MLLogEvent mlle = new MLLogEvent();
                mlle.logParameters.put("Error Code", errorCode);
                mlle.logParameters.put(errorCode, VO_Utils.voIOErrCodes.get(errorCode) + msg);
                logger.log(Level.WARNING, msg, new Object[] { mlle, e });
            } catch (Exception ex) {
            }
            throw new Exception(methodName + " Exception: " + e);
        }
    }

    /**
     * Parse a line with some specific structure. If the line in the log file
     * changes the structure by adding or removing an element, you must modify 
     * just the LINE_ELEMENTS_NO constant from the begin of this module.
     * 
     * Structure of a line in the log file (an example) is: 
     * [ 0] DATE=20050616052722.475032
     * [ 1] HOST=osg.rcac.purdue.edu
     * [ 2] PROG=wuftpd
     * [ 3] NL.EVNT=FTP_INFO
     * [ 4] START=20050616052722.410910
     * [ 5] USER=ivdgl
     * [ 6] FILE=/tmp/gridcat-_-test.gridcat.20252.remote
     * [ 7] BUFFER=87380
     * [ 8] BLOCK=65536
     * [ 9] NBYTES=28
     * [10] VOLUME=/tmp
     * [11] STREAMS=1
     * [12] STRIPES=1
     * [13] DEST=1[129.79.4.64]
     * [14] TYPE=STOR
     * [15] CODE=226
     * 
     * Time format is YYYYMMDDHHMMSS.UUUUUU (microsecs).
     * DATE: time the transfer completed.
     * START: time the transfer started.
     * HOST: hostname of the server.
     * USER: username on the host that transfered the file.
     * BUFFER: tcp buffer size (if 0 system defaults were used).
     * BLOCK: the size of the data block read from the disk and posted to the network.
     * NBYTES: the total number of bytes transfered.
     * VOLUME: the disk partition where the transfer file is stored.
     * STREAMS: the number of parallel TCP streams used in the transfer.
     * STRIPES: the number of stripes used on this end of the transfer.
     * DEST: the destination host.
     * TYPE: the transfer type, RETR is a send and STOR is a receive (ftp 959 commands).
     * CODE: the FTP rfc959 completion code of the transfer. 226 indicates success, 5xx or 4xx are failure codes.
     */
    protected Hashtable parseLine(String line) {
        /** the method name */
        String methodName = "parseLine";

        if (line == null) {
            /** change the status */
            errorCodeParam = STATUS_ERROR_LOG_LINE;
            /** Send error message and line to MLLogEvent */
            errorCode = Integer.valueOf(errorCodeParam);
            try {
                MLLogEvent mlle = new MLLogEvent();
                mlle.logParameters.put("Error Code", errorCode);
                mlle.logParameters.put(errorCode, VO_Utils.voIOErrCodes.get(errorCode) + " NULL line.");
                logger.log(Level.WARNING, "Error in parsing line process - NULL line.", new Object[] { mlle });
            } catch (Exception e) {
            }
            return null;
        }

        /** result of parse */
        Hashtable result = new Hashtable();
        /** elements of a line */
        String[] lineElements = new String[] {};

        lineElements = line.split("\\s+");

        /** test if the line is complete */
        if (lineElements.length == LINE_ELEMENTS_NO) {
            for (int i = 0; i < LINE_ELEMENTS_NO; i++) {
                String key = lineElements[i].split("=")[0].trim();
                String value = lineElements[i].split("=")[1].trim();
                result.put(key, value);
            }
            return result;
        }
        errorCodeParam = STATUS_ERROR_LOG_LINE;
        /** Send error message and line to MLLogEvent */
        errorCode = Integer.valueOf(errorCodeParam);
        try {
            MLLogEvent mlle = new MLLogEvent();
            mlle.logParameters.put("Error Code", errorCode);
            mlle.logParameters.put(errorCode, VO_Utils.voIOErrCodes.get(errorCode));
            logger.log(Level.WARNING, methodName + "(): Error in parsing line process.", new Object[] { mlle });
        } catch (Exception e) {
        }

        return null;
    }

    /** Time format is YYYYMMDDHHMMSS.UUUUUU (microsecs). */
    public long parseTime(String date) {

        int defaultLength = "YYYYMMDDHHMMSS.UUUUUU".length();
        /** if legnth of date < 21 (length of YYYYMMDDHHMMSS.UUUUUU) add zeros */
        if (date.length() < defaultLength) {
            logger.fine("legnth of date < 21 + (date = " + date + ")");
        }
        for (int i = date.length(); i < defaultLength; i++) {
            date = date + "0";
        }
        /** trunc to miliseconds */
        date = date.substring(0, date.length() - 3);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss.SSS");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date d = new Date();
        try {
            d = sdf.parse(date, new ParsePosition(0));
        } catch (NullPointerException e) {
        }
        ;
        return d.getTime();
    }

    /**
     * Add data from info to a tree structure of information
     * @param info - information from line result
     */
    protected void updateVOData(Hashtable info) throws Exception {
        /** the method name */
        String methodName = "updateVOData";

        String user = getVo((String) info.get("USER"));
        if (user == null) {
            errorCodeParam = STATUS_PARAM_PROCESSING;
            errorCode = Integer.valueOf(errorCodeParam);
            try {
                MLLogEvent mlle = new MLLogEvent();
                mlle.logParameters.put("Error Code", errorCode);
                mlle.logParameters.put(errorCode, VO_Utils.voIOErrCodes.get(errorCode) + " Unknown VO.");
                logger.log(Level.WARNING, methodName + "(): unknown VO.", new Object[] { mlle });
            } catch (Exception e) {
            }
            return;
        }

        String src, dest, sd;
        double newFtpIn, newFtpOut;
        Result[] res = new Result[4];
        double factor = 1024.0;

        try {
            /** find a new user (i hope not) */
            if (!voTotals.containsKey(user)) {
                Vector userData = new Vector();
                UserFTPData ufd = new UserFTPData(user, "totals", false);
                userData.add(ufd);
                voTotals.put(user, userData);
                siteTable.put(user, userData);
            }

            /** get the result's vector for user */
            Vector userData = (Vector) voTotals.get(user);
            UserFTPData ufdTotals = (UserFTPData) userData.get(0);

            /** get the destination */
            dest = (String) info.get("DEST");
            int p1 = dest.indexOf('[');
            int p2 = dest.lastIndexOf(']');
            if ((p1 + 1) <= (p2 - 1)) {
                dest = dest.substring(p1 + 1, p2);
                dest = getMachineName(dest);
                dest = getMachineDomain(dest);
            }

            /** get the source */
            src = (String) info.get("HOST");
            if (Character.isDigit(src.charAt(0))) {
                src = getMachineName(src);
            }
            src = getMachineDomain(src);

            /** construct the new parameter name */
            String paramName = "ftpRate";
            if (info.get("TYPE").equals("STOR")) {
                paramName += "Input_" + src + "_" + seq_no;
            }
            if (info.get("TYPE").equals("RETR")) {
                paramName += "Output_" + dest + "_" + seq_no;
            }

            /** get the interval range */
            long start = parseTime((String) info.get("START"));
            long stop = parseTime((String) info.get("DATE"));

            if (stop >= start) {
                /** because start and stop are in milliseconds, if they are equals,
                 * transfer was made in < 1 milliseconds and consider that transfer was made in 1 milliseconds */
                if (start == stop) {
                    start = start - 1;
                }
                /** create the results for current transfer */
                for (int i = 0; i < 4; i++) {
                    res[i] = new Result();
                    res[i].FarmName = Node.getFarmName();
                    res[i].ClusterName = Node.getClusterName() + "_Transfers";
                    res[i].NodeName = MixedCaseVOs ? user : user.toUpperCase();
                    res[i].Module = ModuleName;
                    switch (i) {
                    case 0:
                        res[i].time = start - 1;
                        break;
                    case 1:
                        res[i].time = start;
                        break;
                    case 2:
                        res[i].time = stop;
                        break;
                    case 3:
                        res[i].time = stop + 1;
                        break;
                    }
                    if ((i == 0) || (i == 3)) {
                        res[i].addSet(paramName, 0D);
                    }
                    if ((i == 1) || (i == 2)) {
                        res[i].addSet(paramName,
                                (1000.0 * (Double.valueOf(((String) info.get("NBYTES")))).doubleValue())
                                        / (stop - start) / factor);
                    }

                    transferResult.addElement(res[i]);
                    tpHist.add(new Object[] { user, paramName, Long.valueOf(call_no) });
                    if (!voHist.contains(user)) {
                        voHist.add(user);
                    }
                }
                seq_no = (seq_no + 1) % Long.MAX_VALUE;
            } else {
                errorCodeParam = STATUS_PARAM_PROCESSING;
                errorCode = Integer.valueOf(errorCodeParam);
                try {
                    String msg = VO_Utils.voIOErrCodes.get(errorCode);
                    msg = msg + " DATA before START (DATA=" + (String) info.get("DATE") + " START="
                            + (String) info.get("START") + ").";
                    msg = msg + "\nafter parsing: DATA=" + stop + " START=" + start + ").";
                    MLLogEvent mlle = new MLLogEvent();
                    mlle.logParameters.put("Error Code", errorCode);
                    mlle.logParameters.put(errorCode, msg);
                    logger.log(Level.WARNING, methodName + "(): " + msg, new Object[] { mlle });
                } catch (Exception e) {
                }
            }

            /** add the site in sites Table */
            FTPSite ftpsrc = new FTPSite(src);
            FTPSite ftpdest = new FTPSite(dest);

            Vector sl = (Vector) siteTable.get(user);
            if (sl == null) {
                sl = new Vector();
                sl.add(ftpsrc);
                sl.add(ftpdest);
            } else {
                boolean isSrc = false, isDest = false;
                for (int i = 0; i < sl.size(); i++) {
                    FTPSite aux = (FTPSite) sl.get(i);
                    if ((aux.siteName).equals(src)) {
                        isSrc = true;
                    }
                    if ((aux.siteName).equals(dest)) {
                        isDest = true;
                    }
                    if ((isSrc == true) && (isDest == true)) {
                        break;
                    }
                }
                if (isSrc == false) {
                    sl.add(ftpsrc);
                }
                if (isDest == false) {
                    sl.add(ftpdest);
                }
            }
            siteTable.put(user, sl);

            int ts = Integer.parseInt((String) info.get("CODE"));
            if (ts != TRANSFER_CODE) {
                errorCodeParam = STATUS_FTP_CODE;
                /** send a MLLogeEvent to report a rong CODE for ftp transfer */
                errorCode = Integer.valueOf(ts);
                String msg = "Trasfer CODE = " + ts + ". " + "VO = " + user + ". " + "DEST = " + dest + ". "
                        + "HOST = " + src + ". ";
                MLLogEvent mlle = new MLLogEvent();
                mlle.logParameters.put("Error Code", errorCode);
                mlle.logParameters.put(errorCode, msg);
                logger.log(Level.INFO, msg, new Object[] { mlle });
            }

            /** input */
            if (info.get("TYPE").equals("STOR")) {
                sd = src;
                newFtpIn = (Double.valueOf(((String) info.get("NBYTES")))).doubleValue();
                ufdTotals.ftpInput += newFtpIn;
                userData.setElementAt(ufdTotals, 0);
                monTotals.ftpInput += newFtpIn;

                int goodPosition = getGoodResult(userData, sd);
                UserFTPData ufdSD;
                if (goodPosition == -1) {
                    ufdSD = new UserFTPData(user, sd);
                    ufdSD.ftpInput = ufdSD.ftpInput + newFtpIn;
                    userData.add(ufdSD);
                } else {
                    ufdSD = (UserFTPData) userData.elementAt(goodPosition);
                    ufdSD.ftpInput = ufdSD.ftpInput + newFtpIn;
                    userData.setElementAt(ufdSD, goodPosition);
                }
            }

            /** output */
            if (info.get("TYPE").equals("RETR")) {

                sd = dest;
                newFtpOut = (Double.valueOf(((String) info.get("NBYTES")))).doubleValue();
                ufdTotals.ftpOutput = ufdTotals.ftpOutput + newFtpOut;
                userData.setElementAt(ufdTotals, 0);
                monTotals.ftpOutput += newFtpOut;

                int goodPosition = getGoodResult(userData, sd);
                UserFTPData ufdSD;
                if (goodPosition == -1) {
                    ufdSD = new UserFTPData(user, sd);
                    ufdSD.ftpOutput = ufdSD.ftpOutput + newFtpOut;
                    userData.add(ufdSD);
                } else {
                    ufdSD = (UserFTPData) userData.elementAt(goodPosition);
                    ufdSD.ftpOutput = ufdSD.ftpOutput + newFtpOut;
                    userData.setElementAt(ufdSD, goodPosition);
                }
            }
            /** put the new values */
            voTotals.put(user, userData);

        } catch (Exception e) {
            errorCodeParam = STATUS_PARAM_PROCESSING;
            errorCode = Integer.valueOf(errorCodeParam);
            MLLogEvent mlle = new MLLogEvent();
            mlle.logParameters.put("Error Code", errorCode);
            mlle.logParameters.put(errorCode, VO_Utils.voIOErrCodes.get(errorCode) + " - DATA before START");
            logger.log(Level.WARNING, methodName + "(): Got Exception: " + e, new Object[] { mlle, e });
        }
    }

    /** update the transfers data. Send eReslts for some parameter or some node if is neccesery to remove it */
    protected void updateTransfers() {

        String methodName = "updateTransfers";
        boolean itOk = false;

        try {
            /** remove all param older than 5 doProcess call */
            for (int i = 0; i < tpHist.size(); i++) {

                Object[] o = (Object[]) tpHist.get(i);
                long callNoValue = ((Long) o[2]).longValue();

                if ((call_no - callNoValue) >= TIME_TO_REMOVE) {

                    eResult er = new eResult();
                    er.FarmName = Node.getFarmName();
                    er.ClusterName = Node.getClusterName() + "_Transfers";
                    er.NodeName = MixedCaseVOs ? (String) o[0] : ((String) o[0]).toUpperCase();
                    er.Module = ModuleName;
                    er.addSet((String) o[1], null);

                    transferResult.addElement(er);
                    tpHist.remove(i);
                    i--;
                }
            }

            /** remove nodes (VOs) if not contains any results */
            for (int i = 0; i < voHist.size(); i++) {

                String voName = (String) voHist.get(i);
                boolean shouldRemove = true;

                for (int j = 0; j < tpHist.size(); j++) {
                    Object[] o = (Object[]) tpHist.get(j);
                    if (((String) o[0]).equals(voName)) {
                        shouldRemove = false;
                        break;
                    }
                }

                if (shouldRemove) {
                    eResult er = new eResult();
                    er.FarmName = Node.getFarmName();
                    er.ClusterName = Node.getClusterName() + "_Transfers";
                    er.NodeName = MixedCaseVOs ? voName : voName.toUpperCase();
                    er.Module = ModuleName;
                    er.param = null;
                    er.param_name = null;
                    transferResult.addElement(er);
                    voHist.remove(i);
                    i--;
                    itOk = true;
                }
            }

            /** remove cluster name (if no transfers are) or node name if no trnsfers are for an VO */
            if (itOk && (voHist.size() == 0)) {
                eResult er = new eResult();
                er.FarmName = Node.getFarmName();
                er.ClusterName = Node.getClusterName() + "_Transfers";
                er.NodeName = null;
                er.Module = ModuleName;
                er.param = null;
                er.param_name = null;
                transferResult.addElement(er);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, methodName + "() Exception: ", e);
        }
        ;
    }

    /** verify if result exsist in result name */
    protected int getGoodResult(Vector v, String s) {
        /** the method name */
        //String methodName = "getGoodResult";
        for (int i = 0; i < v.size(); i++) {
            UserFTPData aux = (UserFTPData) v.get(i);
            if (s.equals(aux.node_name)) {
                return i;
            }
        }
        return -1;
    }

    /** return full name of site */
    protected String getMachineName(String ip) {
        String methodName = "getMachineName";
        String dom = null;
        try {
            dom = InetAddress.getByName(ip).getCanonicalHostName();
        } catch (Exception e) {
            logger.log(Level.WARNING, methodName + " Cannot get ip for node " + e);
            dom = ip;
        }
        return dom;
    }

    /** return domain name of site */
    protected String getMachineDomain(String name) {
        if (Character.isDigit(name.charAt(0))) {
            return name;
        }
        String[] domElements = new String[] {};
        String domain = null;
        domElements = name.split("\\.");
        int n = domElements.length;
        if (n >= 2) {
            domain = domElements[n - 2] + "." + domElements[n - 1];
        } else {
            domain = name;
        }
        return domain;
    }

    /** Compute the rate of input and output */
    protected void computeFTPRate(double interval) {
        /** the method name */
        String methodName = "computeFTPRate";
        try {
            Enumeration e = voTotals.keys();
            while (e.hasMoreElements()) {
                String user = (String) e.nextElement();
                Vector ud = (Vector) voTotals.get(user);
                for (int i = 0; i < ud.size(); i++) {
                    UserFTPData ufd = (UserFTPData) ud.get(i);
                    ufd.computeRate(interval);
                    ud.setElementAt(ufd, i);
                }
                voTotals.put(user, ud);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, methodName + " Got Exc:", e);
        }
    }

    /** return the values for the site with specified name */
    FTPSite getSite(Vector ftps, String name) {
        for (int i = 0; i < ftps.size(); i++) {
            if (((FTPSite) ftps.elementAt(i)).siteName.equals(name)) {
                return (FTPSite) ftps.get(i);
            }
        }
        return null;
    }

    /**
     * Create a vector of Result - output of this Module
     * @return vector results
     */
    protected Vector createResultsVector() {
        String methodName = "createResultsVector";
        try {
            Vector results = new Vector();
            double factor = 1024.0;
            Result result;
            double voStatus;

            /** first, create general totals Result */
            result = new Result(Node.getFarmName(), monTotals.user_name, monTotals.node_name, ModuleName, MyResTypes);
            result.time = resultTime;
            result.param[0] = monTotals.ftpInput / factor;
            result.param[1] = monTotals.ftpOutput / factor;
            result.param[2] = monTotals.ftpRateIn / factor;
            result.param[3] = monTotals.ftpRateOut / factor;
            results.addElement(result);

            /** then, create VO_user totals Result */
            Enumeration e = voTotals.keys();
            while (e.hasMoreElements()) {

                String user = (String) e.nextElement();
                Vector values = (Vector) voTotals.get(user);

                UserFTPData voTotal = (UserFTPData) values.get(0);

                result = new Result();
                result.FarmName = Node.getFarmName();
                result.ClusterName = Node.getClusterName();
                if (MixedCaseVOs) {
                    result.NodeName = voTotal.user_name;
                } else {
                    result.NodeName = (voTotal.user_name).toUpperCase();
                }
                result.time = resultTime;
                result.Module = ModuleName;

                /** send rates only if have values for its*/
                if (values.size() > 1) {
                    voStatus = 0D;
                    if (voTotal.shouldSend == true) {
                        result.addSet("ftpInput", voTotal.ftpInput / factor);
                        result.addSet("ftpOutput", voTotal.ftpOutput / factor);
                        result.addSet("ftpRateIn", voTotal.ftpRateIn / factor);
                        result.addSet("ftpRateOut", voTotal.ftpRateOut / factor);
                    } else {
                        voTotal.shouldSend = true;
                        /** send a zero for rates before transfers */
                        Result r_rate = new Result();
                        r_rate.FarmName = Node.getFarmName();
                        r_rate.ClusterName = Node.getClusterName();
                        if (MixedCaseVOs) {
                            r_rate.NodeName = voTotal.user_name;
                        } else {
                            r_rate.NodeName = (voTotal.user_name).toUpperCase();
                        }
                        r_rate.time = resultTime - (long) (1000 * doProcessInterval);
                        r_rate.Module = ModuleName;
                        r_rate.addSet("ftpInput", 0D);
                        r_rate.addSet("ftpOutput", 0D);
                        r_rate.addSet("ftpRateIn", 0D);
                        r_rate.addSet("ftpRateOut", 0D);
                        results.add(r_rate);

                        /** add gridftp values for current VO */
                        result.addSet("ftpInput", voTotal.ftpInput / factor);
                        result.addSet("ftpOutput", voTotal.ftpOutput / factor);
                        result.addSet("ftpRateIn", voTotal.ftpRateIn / factor);
                        result.addSet("ftpRateOut", voTotal.ftpRateOut / factor);
                    }
                } else {
                    voStatus = 1D;
                    if ((values.size() == 1) && (voTotal.shouldSend == true)) {
                        /** send a zero for rates after transfers */
                        voTotal.shouldSend = false;
                        result.addSet("ftpInput", 0D);
                        result.addSet("ftpOutput", 0D);
                        result.addSet("ftpRateIn", 0D);
                        result.addSet("ftpRateOut", 0D);
                    }
                }
                values.setElementAt(voTotal, 0);

                /** Add a parameter for VO transfer status: 1 = if have data, 0 = if not */
                result.addSet("VO_Status", voStatus);

                /** Get the list of trasfer domains for current VO */
                Vector userSites = (Vector) siteTable.get(user);

                /** send values for site parameters */
                for (int i = 0; i < userSites.size(); i++) {
                    FTPSite site = (FTPSite) userSites.elementAt(i);
                    int ufdPos = getGoodResult(values, site.siteName);
                    /** if it is the firs time when have a transfer, send a zero result in the past */
                    if (ufdPos >= 0) {
                        if (site.isFirst == true) {
                            Result r0 = new Result();
                            r0.FarmName = Node.getFarmName();
                            r0.ClusterName = Node.getClusterName();
                            if (MixedCaseVOs) {
                                r0.NodeName = voTotal.user_name;
                            } else {
                                r0.NodeName = (voTotal.user_name).toUpperCase();
                            }
                            r0.time = resultTime - (long) (1000 * doProcessInterval);
                            r0.Module = ModuleName;
                            r0.addSet("ftpInput_" + site.siteName, 0D);
                            r0.addSet("ftpRateIn_" + site.siteName, 0D);
                            r0.addSet("ftpOutput_" + site.siteName, 0D);
                            r0.addSet("ftpRateOut_" + site.siteName, 0D);
                            site.isFirst = false;
                            userSites.setElementAt(site, i);
                            results.add(r0);
                        }
                        voTotal = (UserFTPData) values.get(ufdPos);
                        result.addSet("ftpInput_" + voTotal.node_name, voTotal.ftpInput / factor);
                        result.addSet("ftpRateIn_" + voTotal.node_name, voTotal.ftpRateIn / factor);
                        result.addSet("ftpOutput_" + voTotal.node_name, voTotal.ftpOutput / factor);
                        result.addSet("ftpRateOut_" + voTotal.node_name, voTotal.ftpRateOut / factor);
                    } else {
                        result.addSet("ftpInput_" + site.siteName, 0D);
                        result.addSet("ftpRateIn_" + site.siteName, 0D);
                        result.addSet("ftpOutput_" + site.siteName, 0D);
                        result.addSet("ftpRateOut_" + site.siteName, 0D);
                        userSites.remove(i);
                        i--;
                    }
                }
                siteTable.put(user, userSites);

                results.addElement(result);
            }

            return results;

        } catch (Exception e) {
            logger.log(Level.WARNING, methodName + " Got Exc:", e);
            return null;
        }
    }

    public class UserFTPData {
        public String user_name = new String("user");
        public String node_name = new String("node");
        public double ftpInput, ftpOutput;
        public double ftpRateIn, ftpRateOut;
        public boolean shouldSend;

        UserFTPData(String clusterName, String nodeName) {
            user_name = clusterName;
            node_name = nodeName;
            ftpInput = 0D;
            ftpOutput = 0D;
            ftpRateIn = 0D;
            ftpRateOut = 0D;
            shouldSend = false;
        }

        UserFTPData(String clusterName, String nodeName, boolean sS) {
            user_name = clusterName;
            node_name = nodeName;
            ftpInput = 0D;
            ftpOutput = 0D;
            ftpRateIn = 0D;
            ftpRateOut = 0D;
            shouldSend = sS;
        }

        /** reset the values for this ftp user */
        void reset() {
            ftpInput = 0D;
            ftpOutput = 0D;
            ftpRateIn = 0D;
            ftpRateOut = 0D;
        }

        void computeRate(double interval) {
            ftpRateIn = ftpInput / interval;
            ftpRateOut = ftpOutput / interval;
        }
    };

    public class FTPSite {
        public String siteName;
        public boolean isFirst;

        public FTPSite(String name) {
            siteName = name;
            isFirst = true;
        }
    }

    /** main function - a test function in standalone mode (in usr_code) */
    static public void main(String[] args) {
        System.out.println("args[0]: " + args[0]);
        String host = args[0];
        monOsgVO_IO aa = null;
        String ad = null;

        try {
            System.out.println("...instantiating monOsgVO_IO");
            aa = new monOsgVO_IO();
        } catch (Exception e) {
            System.out.println(" Cannot instantiate monOsgVO_IO:" + e);
            System.exit(-1);
        } // end try/catch

        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Cannot get ip for node " + e);
            System.exit(-1);
        } // end try/catch

        System.out.println("...running init method ");
        String mapFile = "grid3-user-vo-map.txt";
        String arg = "mapfile=" + mapFile;
        MonModuleInfo info = aa.init(new MNode(args[0], ad, null, null), arg);

        int sec = 20; // number of seconds to sleep before processing again
        for (int i = 0; i < 500; i++) {
            try {
                System.out.println("...sleeping " + sec + " seconds");
                Thread.sleep(sec * 1000);
                System.out.println("...running doProcess");
                Object bb = aa.doProcess();
                if ((bb != null) && (bb instanceof Vector)) {
                    Vector v = (Vector) bb;
                    System.out.println(" Received a Vector having " + v.size() + " results");
                    for (int vi = 0; vi < v.size(); vi++) {
                        System.out.println(" [ " + vi + " ] = " + v.elementAt(vi));
                    }
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "ERROR: ", e);
            } // end try/catch
        } // end for

        System.out.println("monOsgVO_IO Testing Complete.");
        System.exit(0);
    } // end main

}// end class
