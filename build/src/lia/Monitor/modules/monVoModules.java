package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map.Entry;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.Monitor.monitor.eResult;
import lia.util.DateFileWatchdog;
import lia.util.Utils;
import lia.util.logging.MLLogEvent;
import lia.util.mail.MailFactory;
import lia.util.ntp.NTPDate;

abstract class monVoModules extends cmdExec implements Observer, AppConfigChangeListener {

    /**
     * 
     */
    private static final long serialVersionUID = 5588477830931732741L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monVoModules.class.getName());

    private final MLLogEvent mlle = new MLLogEvent();

    private Integer errorCode;

    protected final ArrayList<eResult> removedVOseResults = new ArrayList<eResult>();

    protected static String OsName = "linux";

    protected String ModuleName = null;

    protected String[] ResTypes = null;

    protected String clusterModuleName = null;

    // ---- grid3 user to VO account map file ----------
    protected static final String MAP_FILE = "/monitoring/grid3-user-vo-map.txt";

    protected volatile String mapfile = null;

    // wathcdog used to notify if mapFile has changed
    protected DateFileWatchdog mapFileWatchdog = null;

    // ---- environmental variables -------------------
    protected static Properties globalEnv = null;

    protected boolean mustNotifyCfg;

    // -- current status of module (overrides email notification interval --
    protected boolean statusGood = true;

    protected boolean previousStatus = true;

    // -- contains the last reported set of metric results --
    protected StringBuilder lastResults = new StringBuilder();

    // ---- Strings used to identify records in map file --------
    static protected String COMMENT = "#"; // comment line

    static protected String VOI = "#voi "; // lower case VO names

    static protected String VOC = "#VOc "; // mixed case VO names

    // --- tables for tracking VO metrics ----------------
    protected Hashtable<String, String> voAccts = new Hashtable<String, String>();

    protected Hashtable<String, String> voMixedCase = new Hashtable<String, String>();

    protected Hashtable voTotals = new Hashtable();

    // --- tmp tables for tracking VO metrics ----------------
    protected Hashtable<String, String> tmpVoAccts = new Hashtable<String, String>();

    protected Hashtable<String, String> tmpVoMixedCase = new Hashtable<String, String>();

    // -------------------------------------------------
    // counter to control display of results in log file
    // limited to every RESULTS_FREQEUNCY (eg- every 20th time)
    // -------------------------------------------------
    protected long resultsCnt = 20;

    static protected long RESULTS_FREQUENCY = 20;

    // -- Performance monitoring --
    protected long doProcessStartTime = 0;

    protected Vector vtime = new Vector(30);

    protected Vector vperf = new Vector(30);

    // -- General stuff ---------------
    protected MNode Node = null;

    protected Date currDate = new Date(NTPDate.currentTimeMillis());

    protected String monalisaHome = null;

    protected boolean testmode = false;

    protected boolean debugmode = false;

    protected boolean environmentSet = false;

    // -- Email Stuff --
    protected long lastTimeEmailSent = 0;

    protected long lastNCFGEmailSent = 0;

    protected long emailRepeatRate = 0;

    protected static long EXCEPTION_REPEAT_RATE = 4 * 60 * 60 * 1000; // every 4 hours

    protected static long EMAIL_REPEAT_RATE = 8 * 60 * 60 * 1000; // every 8 hours

    protected static long EMAIL_CFG_REPEAT_RATE = 1 * 60 * 60 * 1000; // every 1 hours

    protected static long MAP_FILE_WATCHDOG_CHECK_RATE = 30 * 1000;// every 30 seconds

    protected static String ML_SUPPORT_EMAIL = "mlvomodules@monalisa.cern.ch";

    protected static String RCPT[] = null;

    protected static String contactEmail = new String();

    protected static String MLGroup = new String();

    protected static boolean useContactEmail = false;

    protected String emailNotifyProperty = "lia.Monitor.modules.monVoModules.emailNotify";

    protected String statusEmailNotifyProperty = "lia.Monitor.modules.monVoModules.statusEmailNotify";

    // for the maximum number of Results sent in a status email
    protected String maxResultsEmailedProperty = "lia.Monitor.modules.monVoModules.maxResultsEmailed";

    protected int nCurrentResults = 0;

    protected int nCumulatedResults = 0; // the number of Results generated between 2 consecutive status emails

    protected static int MAX_RESULTS_DEFAULT = 50; // default maximum number of Results sent in a status email

    protected boolean sendEmails = true; // whether we should send emails or no

    protected boolean sendStatusEmails = true; // whether we should send status emails

    protected int maxResultsEmailed = 50; // maximum number of Results to put in a status email

    private final int STATUS_ERROR_MAPPING_VO = 1;

    private final int STATUS_ERROR_READING_MAPFILE = 2;

    private final int STATUS_ERROR_FINDING_MAPFILE = 3;

    // --- StringBuilder used to accumulate messages ------
    protected StringBuilder sb = new StringBuilder();

    // --- For errors logging --------------------------
    /**
     * If this flag is true, log all the errors with the WARNING level;
     * otherwise, log the errors from time to time with WARNING and
     * the other times with FINEST
     */
    protected boolean verboseLogging = false;

    /** The maximum error code number for this module. */
    protected final static int MAX_ERR_CODES = 200;

    /** Log the errors with WARNING once in SMAPLES_PER_ERROR. */
    protected final static int SAMPLE_PER_ERRORS = 10;

    /**
     * Count of errors in the current doProcess (each type
     * of error has its own counter).
     */
    protected int[] errCount;

    /** Total error counters (incremented circularly). */
    protected int[] crtErrCount;

    /** Current logging level for each type of error. */
    protected Level[] crtLevel;

    protected boolean shouldNotifyConfig;

    protected HashMap<String, String> hmNoVoUsers;

    protected static volatile int MAX_NO_VO_STATUS_MAP = 250;

    static {
        try {
            String sMAP_FILE_WATCHDOG_CHECK_RATE = AppConfig.getProperty("lia.Monitor.modules.monVoModules", "20");
            MAP_FILE_WATCHDOG_CHECK_RATE = Long.valueOf(sMAP_FILE_WATCHDOG_CHECK_RATE).longValue() * 1000;
        } catch (Throwable t) {
            MAP_FILE_WATCHDOG_CHECK_RATE = 30 * 1000;
        }
    }

    // ======= CONSTRUCTORS ==============================
    protected monVoModules(String ModName, String[] inResTypes, String inNotifyProperty) {
        super(ModName);
        canSuspend = false;
        shouldNotifyConfig = false;
        this.ModuleName = ModName;
        this.clusterModuleName = ModName;
        this.emailNotifyProperty = inNotifyProperty;
        mustNotifyCfg = false;
        this.ResTypes = inResTypes;
        hmNoVoUsers = new HashMap<String, String>();

        initErrLogging();

        reloadConfig();
        AppConfig.addNotifier(this);
    } // end method

    protected monVoModules(String ModName, String[] inResTypes, String inNotifyProperty, String inStatusNotifyProperty,
            String inResultsEmailedProperty) {
        super(ModName);
        hmNoVoUsers = new HashMap<String, String>();
        canSuspend = false;
        shouldNotifyConfig = false;
        this.ModuleName = ModName;
        this.clusterModuleName = ModName;
        this.emailNotifyProperty = inNotifyProperty;
        this.statusEmailNotifyProperty = inStatusNotifyProperty;
        this.maxResultsEmailedProperty = inResultsEmailedProperty;
        mustNotifyCfg = false;
        this.ResTypes = inResTypes;

        initErrLogging();
        reloadConfig();
        AppConfig.addNotifier(this);
    } // end method

    // ===== PUBLIC METHODS ========================================
    @Override
    public MNode getNode() {
        return this.Node;
    }

    @Override
    public String[] ResTypes() {
        return ResTypes;
    }

    public String getOsName() {
        return OsName;
    }

    public MonModuleInfo getInfo() {
        return info;
    }

    // ==== PRIVATE METHODS ======================================
    protected void cleanupEnv() {
        addToMsg("cleanupEnv", "\nCleanup VO metrics\n");

        // now we can clear ...
        voAccts.clear();
        voMixedCase.clear();
        voTotals.clear();
    }

    protected void computeVoAcctsDiff() {

        debug(" Old map = " + tmpVoMixedCase.toString() + " New Map: " + voMixedCase.toString());
        // SHOULD NEVER HAPPEN!
        if (Node == null) {
            logger.log(Level.WARNING, "\n\n [ ProtocolException ] [ monVoModules ] Node is null!! \n\n");
            return;
        }

        removedVOseResults.clear();
        boolean SN = false;
        StringBuilder sb = new StringBuilder(" [ computeVoAcctsDiff ] Dead VOs [ ");
        for (Enumeration<String> en = tmpVoMixedCase.elements(); en.hasMoreElements();) {
            String VO = en.nextElement();
            if (!voMixedCase.contains(VO)) {
                eResult er = new eResult(Node.getFarmName(), Node.getClusterName(), VO, ModuleName, null);
                removedVOseResults.add(er);

                SN = true;
                sb.append(VO);
            }
        }

        if (!SN) {
            sb.append(" No VOs removed ]");
        } else {
            sb.append(" ]");
        }

        logit(sb.toString());
        addToMsg("computeVoAcctsDiff", sb.toString());
        setShouldNotifyConfig(SN);
    }

    protected void initializeEnv() throws Exception {
        String methodName = "initializeEnv";
        debug("Instantiating " + ModuleName);
        // -- display the result types --
        String tmpTypes = new String();
        tmpTypes = tmpTypes.concat("Result types: ");
        for (String resType : ResTypes) {
            tmpTypes = tmpTypes.concat(resType + ",");
        }
        logit(tmpTypes);
        addToMsg(methodName, tmpTypes);
        setupEmailNotification();
        loadUserVoMapTable();
        validateMappings();
        printVoTable();
    } // end method

    // =========================================================
    protected void setupEmailNotification() throws Exception {
        /* Establishes the parameters for email notifications */
        String methodName = "setupEmailNotification";
        // -- get the ML group ----
        try {
            MLGroup = AppConfig.getProperty("lia.Monitor.group", null);
        } catch (Exception e) {
            addToMsg(methodName, "No lia.Monitor.group property set");
        }
        addToMsg(methodName, "lia.Monitor.group.............." + MLGroup);

        // -- get the contact email property ----
        try {
            contactEmail = AppConfig.getProperty("MonaLisa.ContactEmail", null);
        } catch (Exception e) {
            addToMsg(methodName, "No MonaLisa.ContactEmail property set");
        }
        addToMsg(methodName, "MonaLisa.ContactEmail.........." + contactEmail);

        // -- see if email should be sent ---
        try {
            useContactEmail = Boolean.valueOf(AppConfig.getProperty("include.MonaLisa.ContactEmail", "false"))
                    .booleanValue();
        } catch (Exception e) {
            addToMsg(methodName, "No include.MonaLisa.ContactEmail property set");
        }
        addToMsg(methodName, "include.MonaLisa.ContactEmail.." + useContactEmail);

        // --- establish who to send email to ----------
        if (useContactEmail && (contactEmail != null) && (contactEmail.indexOf("@") != -1)) {
            RCPT = new String[] { ML_SUPPORT_EMAIL, contactEmail };
        } else {
            RCPT = new String[] { ML_SUPPORT_EMAIL };
        } // end if/else
        String rcpts = new String();
        for (String element : RCPT) {
            rcpts = rcpts + element + " ";
        }
        addToMsg(methodName, "Email recipients..............." + rcpts);

    } // end method

    // ==================================================================
    protected void loadUserVoMapTable() throws Exception {
        /*
         * The configuration file containing the mappings of Unix user name
         * to VO name is a whitespace delimited file in the format....
         * unix_user vo_name
         * Comments are on a line starting with an asterick (*).
         * Empty lines are ignored.
         * This method loads the file into a hash table for use in other methods.
         */
        String methodName = "loadUserVoMapTable";

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
                debug("..getting mapfile");
                mapfile = getMapFile();
            } // end if
            debug("checking existence of map file");
            // --verify the map file exists and is readable ----
            File probe = new File(mapfile);
            if (!probe.isFile()) {
                errorCode = Integer.valueOf(STATUS_ERROR_FINDING_MAPFILE);
                mlle.logParameters.put(VO_Utils.voErrCodes.get(errorCode), errorCode);
                throw new Exception("map file(" + mapfile + ") not found.");
            } // end if
            if (!probe.canRead()) {
                logerr("map file (" + mapfile + ") is not readable.");
                errorCode = Integer.valueOf(STATUS_ERROR_READING_MAPFILE);
                mlle.logParameters.put(VO_Utils.voErrCodes.get(errorCode), errorCode);
                throw new Exception("map file (" + mapfile + ") is not readable.");
            } // end if
            logit("map file stats:\n (" + mapfile + ")\n  last modified " + (new Date(probe.lastModified())).toString()
                    + " - size(" + probe.length() + " Bytes)");
            addToMsg(
                    methodName,
                    "map file stats:\n (" + mapfile + ")\n  last modified "
                            + (new Date(probe.lastModified())).toString() + " - size(" + probe.length() + " Bytes)");

            if (mapFileWatchdog != null) {// just extra check ...
                if (!mapFileWatchdog.getFile().equals(probe)) {// has changed or has been deleted ?!?
                    mapFileWatchdog.stopIt();
                    mapFileWatchdog = null;
                }
            }

            if (mapFileWatchdog == null) {
                mapFileWatchdog = DateFileWatchdog.getInstance(probe, MAP_FILE_WATCHDOG_CHECK_RATE);
                mapFileWatchdog.addObserver(this);
                logit("DateFileWatchdog for (" + mapFileWatchdog.getFile() + ") has been started");
                addToMsg(methodName, "DateFileWatchdog for (" + mapFileWatchdog.getFile() + ") has been started");
                environmentSet = false;
            }

            // ---------------------------
            // Start processing the file
            // ---------------------------
            addToMsg(methodName, "User-VO map table(" + mapfile + ")");
            FileReader fr = new FileReader(mapfile);
            BufferedReader br = new BufferedReader(fr);
            Vector ignoredAccounts = new Vector();
            while ((record = br.readLine()) != null) {
                debug("record: " + record);
                StringTokenizer tz = new StringTokenizer(record);
                if (tz.countTokens() == 0) { // check for empty line
                    continue;
                }
                debug("processing: " + record);
                addToMsg(methodName, "processing: " + record);
                // -----------------------------------------------------------------
                // Get the first work in the line and use it to see the record type
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
                    debug("VOc: " + vocCnt + " VOs");
                    for (int i = 0; i < vocCnt; i++) {
                        vo = tz.nextToken().trim();
                        debug("lower(" + voiList[i] + ") upper(" + vo + ")");
                        voMixedCase.put(voiList[i], vo);
                    } // end for
                    continue; // read next record
                } // end if

                // --- process comment record ----
                if (COMMENT.equals(record.substring(0, 1))) { // check for comment
                    continue; // read next record
                } // end if

                // ---------------------------------------------------------------
                // Process the unix to lower case mapping records
                // (anything that falls through to here is considered a mapping)
                // ---------------------------------------------------------------
                // --- verify that the #voi and $VOc records was found first --------
                if (!vocFound) {
                    br.close();
                    throw new Exception("The #voi and #VOc records must precede the unix to VO mapping records.");
                }
                // ----------------------------------------------------
                // Since we already stripped off the first token, there should only
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
                        logerr("Multiple mappings for unix account (" + unix + ") ... this account will be ignored");
                        addToMsg(methodName, "Multiple mappings for unix account (" + unix
                                + ") ... this account will be ignored");
                        // throw new Exception("Multiple mappings for unix account ("+unix+")");
                    } // end if
                    voAccts.put(unix, vo);
                } else {
                    // br.close();
                    logerr("Unable to determine mapping from this entry(" + record + ")");
                    addToMsg(methodName, "Unable to determine mapping from this entry(" + record + ")");
                    // throw new Exception("Unable to determine mapping from this entry("+record+")");
                } // end if
            }

            if (ignoredAccounts.size() > 0) {
                logerr("Ignored unix accounts: " + ignoredAccounts.toString());
                addToMsg(methodName, "Ignored unix accounts: " + ignoredAccounts.toString());
                for (int iai = 0; iai < ignoredAccounts.size(); iai++) {
                    voAccts.remove(ignoredAccounts.elementAt(iai));
                }
            }

            br.close();
        } catch (Exception e) {
            errorCode = Integer.valueOf(STATUS_ERROR_MAPPING_VO);
            mlle.logParameters.put(VO_Utils.voErrCodes.get(errorCode), errorCode);
            throw new Exception("ERROR in mapping file: " + e.getMessage());
        }
    } // end method

    // == Classes to get path and environment ==========================
    // -----------------------------------------------------------------
    protected String getMapFile() throws Exception {
        /*
         * Returns the User-VO account mapping file name (full path).
         * Returns null, if not set.
         * First, checkx VDT_LOCATION env variabls
         * Then, sets it to MonaLisa_HOME + /..
         */
        String methodName = "getMapFile";
        String mapfile = null;
        debug("Searching for User-VO map file.");
        String mappath = getEnvValue("VDT_LOCATION");
        if (mappath == null) {
            addToMsg(methodName, "VDT_LOCATION variable not set...checking MonaLisa_HOME.");
            mappath = getEnvValue("MonaLisa_HOME");
            if (mappath != null) {
                mappath = mappath + "/..";
            } else {
                addToMsg(methodName, "MonaLisa_HOME variable not set.");
                throw new Exception("Unable to determine " + MAP_FILE + " location. Terminating.");
            } // end if/else
        } // end if
          // set the file name
        mapfile = mappath + MAP_FILE;
        addToMsg(methodName, "User-VO map file used:" + mapfile);
        return mapfile;
    } // end method

    // ============================================================
    protected String getEnvValue(String key) {
        /*
         * Returns the the value of the env variable if set.
         * Returns null, if not set.
         * First checks the properties option (-D) on the command.
         * Then, the OS env variablse.
         */
        // Check if it is a system property on the command (-D option)
        String methodName = "getEnvValue";
        String res = System.getProperty(key);
        if (res == null) {
            // if not, check the env
            addToMsg(methodName, key + " variable not set...checking UNIX env.");
            res = AppConfig.getGlobalEnvProperty(key);
        }
        if (res != null) {
            addToMsg(methodName, key + "=" + res);
        }
        return res;
    } // end method

    // ======================================
    protected Enumeration<String> VoList() {
        return voMixedCase.elements();
    }

    // ======================================
    protected void initializeTotalsTable() {
        voTotals = new Hashtable();
    }

    // ========================================
    protected void updateTotals(String unixAcct, String metric) {
        /* Increments metric totals by 1. */
        updateTotals(unixAcct, metric, "1");
    }

    // ========================================
    protected void updateTotals(String unixAcct, String metric, String value) {
        /*
         * Increments the metric totals by the value specified.
         * If the unix account does not exist, it ignores it
         */
        String vo = null;
        // -------------------------------------
        // Verify that the user belongs to a VO
        // -------------------------------------
        vo = getVo(unixAcct);
        if (vo == null) {
            return; // this is not a user we are interested in
        }
        updateVoTotals(vo, metric, value);
    }

    // ========================================
    protected void updateVoTotals(String vo, String metric, String value) {
        Hashtable metricsTable = new Hashtable();
        Double oldvalue = Double.valueOf(0);
        Double newvalue = Double.valueOf(value);
        // ---------------------------------------------------------------
        // Create a new entry if the VO does not exist in the totals table
        // ---------------------------------------------------------------
        if (voTotals.containsKey(vo)) {
            metricsTable = (Hashtable) voTotals.get(vo);
        } else {
            metricsTable.put(metric, Double.valueOf(0));
        }
        // --------------------------
        // Check if the metric exists
        // --------------------------
        if (metricsTable.containsKey(metric)) {
            oldvalue = (Double) metricsTable.get(metric);
        } else {
            metricsTable.put(metric, Double.valueOf(0));
        }
        // --------------------------
        // Add to the value
        // --------------------------
        newvalue = Double.valueOf(oldvalue.doubleValue() + newvalue.doubleValue());
        metricsTable.put(metric, newvalue);
        voTotals.put(vo, metricsTable);
    } // end method

    // ======================================
    /*
     * Returns a specific metric for a specified VO.
     * Returns zero if VO or metric is not found.
     */
    protected Double getMetric(String vo, String metric) {

        Hashtable metricsTable = new Hashtable();
        Double value = Double.valueOf(0);

        if (voTotals.containsKey(vo)) {
            metricsTable = (Hashtable) voTotals.get(vo);
        } else {
            return value;
        }
        if (metricsTable.containsKey(metric)) {
            value = (Double) metricsTable.get(metric);
        } else {
            return value;
        }

        return value;
    }

    // ======================================
    protected String getVo(String unix) {
        String voLower = null;
        String vo = null;
        voLower = voAccts.get(unix);
        if (voLower != null) {
            vo = voMixedCase.get(voLower);
        }
        return vo;
    }

    // ==========================
    protected void printVoTable() {
        /* Prints the constructed user-VO map table. */
        StringBuilder output = new StringBuilder();
        output.append("Printing unix / vo map table:");
        for (Enumeration e = voAccts.keys(); e.hasMoreElements();) {
            String unix = (String) e.nextElement();
            String vo = voAccts.get(unix);
            String VO = getVo(unix);
            output.append("\n  unix->VO mappings: " + unix + " -> " + vo + " -> " + VO);
        } // end for
        logit(output.toString());
        sb.append("\n" + output.toString());
    } // end method

    // --------------------------------------------------
    protected Vector createResults() throws Exception {
        double factor = 1.0;
        return createResults(factor);
    } // end method

    // --------------------------------------------------
    protected Vector createResults(double factor) throws Exception {
        String methodName = "createResults";
        Vector results = new Vector();

        debug("Creating results object");
        try {
            for (Enumeration vl = VoList(); vl.hasMoreElements();) {
                String VO = (String) vl.nextElement();

                // ---------------------------------------------------------------
                // Created result object that stores data in database
                // Result(farmName(weigand_farm), cluster(sdss) , name(hotdog62).....
                // *sdss (cluster)
                // ---------------------------------------------------------------
                Result result = new Result(Node.getFarmName(), Node.getClusterName(), VO, ModuleName, ResTypes);
                // updates time
                result.time = NTPDate.currentTimeMillis();

                // -----------------------------------------
                // Updates each ResTypes with the VO totals
                // -----------------------------------------
                for (int i = 0; i < ResTypes.length; i++) {
                    Double value = getMetric(VO, ResTypes[i]);
                    result.param[i] = value.doubleValue() / factor;
                }
                results.add(result);
            } // end for Enu
        } catch (Throwable t) {
            throw new Exception(methodName + "() FATAL ERROR: " + t.getMessage());
        }
        logit("RETURNING " + results.size() + " updates");

        // -- update the results counters ----
        nCumulatedResults += results.size();
        nCurrentResults = results.size();

        // -- save the latest results ----
        lastResults = new StringBuilder();
        int maxResults = maxResultsEmailed;
        if (maxResults > results.size()) {
            maxResults = results.size();
        }
        for (int i = 0; i < maxResults; i++) {
            lastResults.append("\n[" + i + "]" + results.elementAt(i));
        } // end forMNode nRemove = (MNode)mc.nodeList.get(i);

        // -- print results every n-th time --
        resultsCnt = resultsCnt + 1;
        if (resultsCnt > RESULTS_FREQUENCY) {
            logit(lastResults.toString());
            resultsCnt = 0;
        } // end if resultsCnt

        if (getShouldNotifyConfig()) {
            results.addAll(removedVOseResults);
            removedVOseResults.clear();
        }

        return results;
    }// end method

    protected synchronized void setShouldNotifyConfig(boolean newValue) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " setShouldNotifyConfig New: " + newValue + " Old: " + shouldNotifyConfig,
                    new Exception("debug"));
        }

        if (shouldNotifyConfig != newValue) {
            mustNotifyCfg = true;
            shouldNotifyConfig = newValue;
        }
    }

    protected synchronized boolean getShouldNotifyConfig() {
        return shouldNotifyConfig;
    }

    // ==========================
    protected void validateMappings() throws Exception {
        /*
         * Validates the unix-VO mappings.
         * This performs all validation that cannot be performed until all
         * mapping records loaded.
         * We need to insure that every unix account will map to a VO and
         * that every VOc record specified will be mapped to by at least
         * one unix account.
         */

        String ignoredVOs = "";
        // -- Determine if there is a unix account defined for each #voi specified
        for (Enumeration e = voMixedCase.keys(); e.hasMoreElements();) {
            String vo = (String) e.nextElement();
            if (!voAccts.contains(vo)) {
                ignoredVOs += (ignoredVOs.length() == 0) ? vo : ", " + vo;
                voMixedCase.remove(vo);
                logerr("No unix account will map to VO (" + vo + ")");
                addToMsg("validateMappings", "No unix account will map to VO (" + vo + ")");
                // throw new Exception("No unix account will map to VO ("+vo+")");
            } // end if
        } // end for

        addToMsg("validateMappings", " voMixedCase = " + voMixedCase.toString());
        addToMsg("validateMappings", " ignoredVO = " + ignoredVOs);

        String ignoredUNIXAccts = "";
        // -- Determine if there is a #voi for each unix account
        for (Enumeration e = voAccts.keys(); e.hasMoreElements();) {
            String unix = (String) e.nextElement();
            String vo = getVo(unix);
            if (vo == null) {
                ignoredUNIXAccts += (ignoredUNIXAccts.length() == 0) ? unix : ", " + unix;
                voAccts.remove(unix);
                logerr("No VO account for unix account (" + unix + ")");
                addToMsg("validateMappings", "No VO account for unix account (" + unix + ")");
                // throw new Exception("No VO account for unix account ("+unix+")");
            } // end if
        } // end for

        addToMsg("validateMappings", " voAccts = " + voAccts.toString());
        addToMsg("validateMappings", " ignoredUNIXAccts = " + ignoredUNIXAccts);

    } // end method

    // == EMAIL METHODS ============================================
    protected void sendStatusEmail() {
        if (sendStatusEmails) {
            debug("sendStatusEmail: StringBuilder length = " + sb.length());
            if (sb.length() > 0) {
                logit(sb.toString());
            } // end if
            sendEmail("( Status )");
        }
    } // end method

    // ========================================================
    protected void sendExceptionEmail(String inMsg) {
        sb.append("\n\nException Message at " + (new Date() + " / " + new Date(NTPDate.currentTimeMillis())) + ":\n\n"
                + inMsg);
        logerr(sb.toString());
        sendEmail(" ( Exception )");
    } // end method

    // ========================================================
    protected void sendEmail(String inType) {
        if (sendEmails) {
            sendEmailNotification(inType);
        } else {
            debug("sendEmail: emailNotifyProperty is false!");
        }
        sb = new StringBuilder();
    } // end method

    // ========================================================
    protected void sendEmailNotification(String type) {
        String methodName = "sendEmailNotification";
        debug(methodName + ": Attempting to send email... checking type");
        if ((type == null) || (type.length() == 0)) {
            type = "( Status )";
        }
        // --- set the repeat rate based on status -----
        if (statusGood) {
            emailRepeatRate = EMAIL_REPEAT_RATE;
        } else {
            emailRepeatRate = EXCEPTION_REPEAT_RATE;
        }
        // --- if the status changed from previous time, reset the last time sent--
        // --- so email is forced out (hopefully it will not yo-yo)
        previousStatus = statusGood;

        // --- check node name -----
        try {
            debug(methodName + ": Attempting to send email... checking node name");
            // -- Verify node name is a real system ---
            String FarmNameORAddress = Node.getFarmName();
            try {
                FarmNameORAddress += " [ " + InetAddress.getLocalHost().getHostName() + " / "
                        + InetAddress.getLocalHost().getHostAddress() + " ] ";
            } catch (Throwable t) {
                logerr(methodName + ": Caught exception in getLocalHost().getHostName");
            }

            debug(methodName + ": Attempting to send email... checking lastTimeEmailSent [ "
                    + new Date(lastTimeEmailSent) + " ] / " + lastTimeEmailSent + " mustNotifyCfg " + mustNotifyCfg);
            // -- send the mail if time -------
            if (((lastTimeEmailSent + emailRepeatRate) < System.currentTimeMillis()) || mustNotifyCfg) {
                if (mustNotifyCfg) {
                    if (((lastNCFGEmailSent + EMAIL_CFG_REPEAT_RATE) > System.currentTimeMillis())
                            && ((lastTimeEmailSent + emailRepeatRate) > System.currentTimeMillis())) {// DO NOT SPAM ME :)!
                        return;
                    }
                    type = "( NotifyUserMapChanged )";
                    lastNCFGEmailSent = System.currentTimeMillis();
                }

                setShouldNotifyConfig(false);
                mustNotifyCfg = false;
                lastTimeEmailSent = System.currentTimeMillis();
                debug(methodName + ": Attempting to send email... checking MLGroup(" + MLGroup + ")");

                // --- add last reported results -------
                if (lastResults.length() > 0) {
                    sb.append("\n\nLast reported result objects:");
                    sb.append(lastResults.toString());
                } // end if lastResults
                sb.append("\n\nNumber of results from the current execution: ").append(nCurrentResults);
                sb.append("\nNumber of results cumulated since the previous email: ").append(nCumulatedResults);

                nCumulatedResults = 0;

                try {
                    if (hmNoVoUsers.size() > 0) {
                        sb.append("\n\n----------------------------------------------------");
                        sb.append("\nUsers which cannot be mapped to any VOs:\n");
                        for (Entry<String, String> entry : hmNoVoUsers.entrySet()) {
                            sb.append("\n").append(entry.getKey()).append(" --> ").append(entry.getValue());
                        }
                    }// if
                } catch (Throwable t) {
                    sb.append("\nGot Exception trying to print NO_VO mappings").append(Utils.getStackTrace(t));
                } finally {
                    hmNoVoUsers.clear();
                }
                sb.append("\n\n----------------------------------------------------\n\n");

                // --- add in any performance measurements -------
                if (vtime.size() > 0) {
                    sb.append("\n\nPerformance statistics:");
                    for (int i = 0; (i < vtime.size()) && (i < vperf.size()); i++) {
                        sb.append("\n     " + ((Date) vtime.elementAt(i)).toString() + " >>>> [ "
                                + ((Long) vperf.elementAt(i)).longValue() + " ] ms");
                    } // end for
                } // end if vtime.size
                  // --- send it ------
                if (testmode) {
                    logit(methodName + ": Operating in TESTMODE. Email notification would normally be sent to "
                            + ML_SUPPORT_EMAIL + "\n" + sb.toString());
                } else {
                    logit(methodName + ": Email notification sent to " + ML_SUPPORT_EMAIL + "\n" + sb.toString());
                    MailFactory.getMailSender().sendMessage(ML_SUPPORT_EMAIL, RCPT,
                            clusterModuleName + " " + type + " @ " + FarmNameORAddress, sb.toString());
                } // if testmode
                clearTimes();
            } // end if lastTime
        } catch (Throwable t) {
            logit(methodName + ": EMAIL Notification FAILED.");
        }
    } // end method

    // ===========================================================
    protected void addToMsg(String inMethod, String inMsg) {
        sb.append("\n" + clusterModuleName + "[" + inMethod + "]: " + inMsg);
    } // end method

    protected void logit(String msg) {
        logger.log(Level.INFO, clusterModuleName + ": " + msg);
    } // end method

    // ------------------------------
    protected void logerr(String msg) {
        logger.log(Level.SEVERE, clusterModuleName + ": " + msg);
    } // end method

    // ------------------------------
    protected void debug(String msg) {
        if (debugmode) {
            logit(msg);
        }
    } // end method

    // ===============================================================
    // Methods used for measuring performance on the metrics collectors
    // ---------------------------------
    protected void setStartTime() {
        doProcessStartTime = NTPDate.currentTimeMillis();
    } // end method

    // ---------------------------------
    protected void setFinishTime() {
        // insure we do not exceed the capacity of the vector or let it grow inifinitely
        if (vtime.size() == vtime.capacity()) {
            clearTimes();
        } // end if
        vtime.add(new Date(NTPDate.currentTimeMillis()));
        vperf.add(Long.valueOf(NTPDate.currentTimeMillis() - doProcessStartTime));
    } // end method

    // ---------------------------------
    protected void clearTimes() {
        vtime.clear();
        vperf.clear();
    } // end method

    // ===============================================================
    // The Vo map file has changed
    @Override
    public void update(Observable o, Object arg) {
        if ((o != null) && (mapFileWatchdog != null) && o.equals(mapFileWatchdog)) {// just extra check
            logit("\n\n===> User vo Map has changed !! ... The env will be reloaded\n\n");
            environmentSet = false;
        }
    }

    private void reloadConfig() {
        try {
            sendEmails = AppConfig.getb(emailNotifyProperty, true);
        } catch (Throwable t) {
            sendEmails = true;
        }

        try {
            sendStatusEmails = AppConfig.getb(statusEmailNotifyProperty, true);
        } catch (Throwable t) {
            sendStatusEmails = true;
        }

        try {
            maxResultsEmailed = AppConfig.geti(maxResultsEmailedProperty, MAX_RESULTS_DEFAULT);
        } catch (Throwable t) {
            maxResultsEmailed = MAX_RESULTS_DEFAULT;
        }

        try {
            // should be in minutes
            EXCEPTION_REPEAT_RATE = AppConfig.getl("lia.Monitor.modules.monVoModules.EXCEPTION_REPEAT_RATE", 4 * 60) * 60 * 1000;
        } catch (Throwable t) {
            EXCEPTION_REPEAT_RATE = 4 * 60 * 60 * 1000;
        }

        try {
            // should be in minutes
            EMAIL_REPEAT_RATE = AppConfig.getl("lia.Monitor.modules.monVoModules.EMAIL_REPEAT_RATE", 8 * 60) * 60 * 1000;
        } catch (Throwable t) {
            EMAIL_REPEAT_RATE = 1 * 60 * 60 * 1000;
        }

        try {
            // should be in minutes
            EMAIL_CFG_REPEAT_RATE = AppConfig.getl("lia.Monitor.modules.monVoModules.EMAIL_CFG_REPEAT_RATE", 1 * 60) * 60 * 1000;
        } catch (Throwable t) {
            EMAIL_CFG_REPEAT_RATE = 1 * 60 * 60 * 1000;
        }

        try {
            MAX_NO_VO_STATUS_MAP = AppConfig.geti("lia.Monitor.modules.monVoModules.MAX_NO_VO_STATUS_MAP", 250);
        } catch (Throwable t) {
            MAX_NO_VO_STATUS_MAP = 250;
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, " sendEmails = " + sendEmails + "; sendStatusEmails = " + sendStatusEmails
                    + "; maxResultsEmailed = " + maxResultsEmailed + "; MAX_NO_VO_STATUS_MAP = " + MAX_NO_VO_STATUS_MAP
                    + "; EXCEPTION_REPEAT_RATE = " + ((EXCEPTION_REPEAT_RATE / (60 * 1000)) + " min")
                    + "; EMAIL_REPEAT_RATE = " + ((EMAIL_REPEAT_RATE / (60 * 1000)) + " min")
                    + "; EMAIL_CFG_REPEAT_RATE = " + ((EMAIL_CFG_REPEAT_RATE / (60 * 1000)) + " min"));
        }
    }

    @Override
    public void notifyAppConfigChanged() {
        reloadConfig();
    }

    // --- helper methods for error logging -------
    public void initErrLogging() {
        errCount = new int[MAX_ERR_CODES];
        crtLevel = new Level[MAX_ERR_CODES];
        crtErrCount = new int[MAX_ERR_CODES];

        for (int i = 0; i < MAX_ERR_CODES; i++) {
            errCount[i] = crtErrCount[i] = 0;
            crtLevel[i] = Level.WARNING;
        }
    }

    /**
     * Updates the error counters when we have a new error.
     * 
     * @param code
     *            The error's code.
     */
    public void countNewError(int code) {
        crtErrCount[code]++;
        if (!verboseLogging) {
            if (errCount[code] == 0) {
                crtLevel[code] = Level.WARNING;
            } else {
                crtLevel[code] = Level.FINEST;
            }
        }
    }

    /**
     * Updates the error counters after a doProcess().
     * Should be called once per doProcess();
     */
    public void refreshErrorCount() {
        // logger.info("### refreshErrCount...");
        for (int i = 0; i < MAX_ERR_CODES; i++) {
            if (crtErrCount[i] == 0) {
                // if there was no err with this code in the current doProcess,
                // reset the counter
                errCount[i] = 0;
            } else {
                // increment the counter circularly
                errCount[i] = (errCount[i] + 1) % SAMPLE_PER_ERRORS;
            }
        }
    }

    // ################################################################3
    // --------------------------------------------------
    static public void main(String[] args) {
        String test = new String();
        String result = new String();
        String vo = null;
        String unixacct = null;
        String unixAccounts = null;
        String metric = null;
        String value = null;
        Double newvalue = Double.valueOf(0);
        String modName = "testVoAccounts";
        int testnum = 0;

        // boolean debug = true;
        boolean debug = false;
        try {
            /*
             * VoAccounts map = new VoAccounts(modName,debug);
             * //------------------
             * testnum++;
             * unixacct = "uscms01";
             * result = "CMS";
             * test = "\nTEST "+testnum+": Get VO for "+unixacct+"- result: "+result+" --------";
             * System.out.println(test);
             * vo = map.getVo(unixacct);
             * System.out.println ( "Unix: "+unixacct+"  VO: "+vo);
             * if ( vo.equals(result) ) { System.out.println("PASSED");
             * } else throw new Exception("FAILED TESTS");
             * //------------------
             * testnum++;
             * unixacct = "uscms02";
             * result = "CMS";
             * test = "\nTEST "+testnum+": Get VO for "+unixacct+"- result: "+result+" --------";
             * System.out.println(test);
             * vo = map.getVo(unixacct);
             * System.out.println ( "Unix: "+unixacct+"  VO: "+vo);
             * if ( vo.equals(result) ) { System.out.println("PASSED");
             * } else throw new Exception("FAILED TESTS");
             * //------------------
             * testnum++;
             * unixacct = "uscms03";
             * result = null;
             * test = "\nTEST "+testnum+": Get VO for "+unixacct+"- result: "+result+" --------";
             * System.out.println(test);
             * vo = map.getVo(unixacct);
             * System.out.println ( "Unix: "+unixacct+"  VO: "+vo);
             * if ( vo == null) { System.out.println("PASSED");
             * } else throw new Exception("FAILED TESTS");
             * //------------------
             * testnum++;
             * unixacct = "uscms01";
             * metric = "Running Jobs";
             * value = "1.03";
             * test = "\nTEST "+testnum+": Updating unixacct("+unixacct+") metric("+metric+") value("+value+")";
             * System.out.println(test);
             * map.updateTotals(unixacct, metric, value );
             * vo = "CMS"; // should map to this
             * newvalue = map.getMetric((String) vo, metric);
             * System.out.println ( "VO: "+vo+" Value: " + newvalue.doubleValue() );
             * if ( newvalue.doubleValue() == (double) 1.03) { System.out.println("PASSED");
             * } else throw new Exception("FAILED TESTS");
             * //------------------
             * testnum++;
             * unixacct = "uscms01";
             * metric = "Running Jobs";
             * value = "100.03";
             * test = "\nTEST "+testnum+": Updating unix("+unixacct+") metric("+metric+") value("+value+")";
             * System.out.println(test);
             * map.updateTotals(unixacct, metric, value );
             * vo = "CMS"; // should map to this
             * newvalue = map.getMetric(vo, metric);
             * System.out.println ( " VO:"+vo+" Metric:"+metric+" Value: " + newvalue.doubleValue() );
             * if ( newvalue.doubleValue() == (double) 101.06) { System.out.println("PASSED");
             * } else throw new Exception("FAILED TESTS");
             * //------------------
             * testnum++;
             * unixacct = "uscms02";
             * metric = "Running Jobs";
             * value = "200";
             * test = "\nTEST "+testnum+": Updating unixacct("+unixacct+") metric("+metric+") value("+value+")";
             * System.out.println(test);
             * map.updateTotals(unixacct, metric, value );
             * vo = "CMS"; // should map to this
             * newvalue = map.getMetric(vo, metric);
             * System.out.println ( " VO:"+vo+" Metric:"+metric+" Value: " + newvalue.doubleValue() );
             * if ( newvalue.doubleValue() == (double) 301.06) { System.out.println("PASSED");
             * } else throw new Exception("FAILED TESTS");
             * //------------------
             * testnum++;
             * vo = "CMS";
             * metric = "nothing";
             * value = "1.03";
             * test = "\nTEST "+testnum+": Updating unixacct("+unixacct+") metric("+metric+") value("+value+")";
             * System.out.println(test);
             * System.out.println("          (allows metrics to be built dynamically)");
             * map.updateTotals(unixacct, metric, value );
             * newvalue = map.getMetric(vo, metric);
             * System.out.println ( " VO:"+vo+" Metric: "+metric+" Value:"+ newvalue.doubleValue() );
             * if ( newvalue.doubleValue() == (double) 1.03) { System.out.println("PASSED");
             * } else throw new Exception("FAILED TESTS");
             * //------------------
             * testnum++;
             * vo = "USATLAS";
             * metric = "Running Jobs";
             * test = "\nTEST "+testnum+": Retrieving vo("+vo+") metric("+metric+")";
             * System.out.println(test);
             * System.out.println("---- (Invalid VO. Should retrieve zero)");
             * map.updateTotals(unixacct, metric, value );
             * newvalue = map.getMetric(vo, metric);
             * System.out.println ( " VO:"+vo+ " Metric:"+metric+" Value: " + newvalue.doubleValue() );
             * if ( newvalue.doubleValue() == (double) 0) { System.out.println("PASSED");
             * } else throw new Exception("FAILED TESTS");
             * //------------------
             * testnum++;
             * unixacct = "uscms01";
             * metric = "Running Jobs";
             * value = "100.03";
             * test = "\nTEST "+testnum+": Updating unixacct("+unixacct+") metric("+metric+") value("+value+")";
             * System.out.println(test);
             * System.out.println("       initializing tables");
             * System.out.println("       Should show value "+value+" and no cumulative from previous tests");
             * map.initializeTotalsTable();
             * map.updateTotals(unixacct, metric, value );
             * vo = "CMS"; // should map to this
             * newvalue = map.getMetric(vo, metric);
             * System.out.println ( " VO:"+vo+" Value: " + newvalue.doubleValue() );
             * if ( newvalue.doubleValue() == (double) 100.03) { System.out.println("PASSED");
             * } else throw new Exception("FAILED TESTS");
             * //------------------
             * testnum++;
             * unixacct = "uscms02";
             * metric = "Running Jobs";
             * value = "200";
             * test = "\nTEST "+testnum+": Updating unixacct("+unixacct+") metric("+metric+") value("+value+")";
             * System.out.println(test);
             * System.out.println("       uscms02 is in map table. Should update CMS totals ");
             * System.out.println("       Should show value 300.3");
             * map.updateTotals(unixacct, metric, value );
             * vo = "CMS"; // should map to this
             * newvalue = map.getMetric(vo, metric);
             * System.out.println ( " VO:"+vo+" Value: " + newvalue.doubleValue() );
             * if ( newvalue.doubleValue() == (double) 300.03) { System.out.println("PASSED");
             * } else throw new Exception("FAILED TESTS");
             * //------------------
             * testnum++;
             * unixacct = "uscms03";
             * metric = "Running Jobs";
             * value = "500";
             * test = "\nTEST "+testnum+": Updating unixacct("+unixacct+") metric("+metric+") value("+value+")";
             * System.out.println(test);
             * System.out.println("       uscms03 is not in map table. Should have no affect on CMS totals ");
             * map.updateTotals(unixacct, metric, value );
             * vo = "CMS"; // Should show that 500 for uscms03 has no affect on CMS
             * System.out.println("       Should show value "+300.03);
             * newvalue = map.getMetric(vo, metric);
             * System.out.println ( " VO:"+vo+" Value: " + newvalue.doubleValue() );
             * if ( newvalue.doubleValue() == (double) 300.03) { System.out.println("PASSED");
             * } else throw new Exception("FAILED TESTS");
             * //------------------
             * // acct =a.getUnixAccountFromFile( vo );
             * // System.out.println ( "From file       - VO: "+vo+"  Unix account: "+acct);
             */
        } catch (Exception e) {
            System.out.println("  *************************************************");
            System.out.println("  ********** FAILED *******************************");
            System.out.println("  Caught exception");
            System.out.println("  Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.out.println("  *************************************************");
            System.exit(-1);
        }
        System.out.println("\n#### PASSES ALL TESTS ####\n");
        System.exit(0);
    }

}
