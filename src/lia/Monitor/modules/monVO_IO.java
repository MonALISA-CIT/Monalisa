package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.net.InetAddress;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.util.LogWatcher;
import lia.util.ntp.NTPDate;

/**
 * This module compute the value for this tree structure from globus log file
 * Tree result:
 *    \_user*
 *       |__ftpInput
 *       |__ftpOutput
 *       |__ftpRateIn
 *       |__ftpRateOut
 *       |__(src,dest)*
 *       		|__ftpInput
 *       		|__ftpOutput
 *       		|__ftpRateIn
 *       		|__ftpRateOut
 *
 * The data structure is voTotals from the base class.
 * and voAccts for VO users
 */

public class monVO_IO extends monVoModules implements MonitoringModule {
    /** serial version number */
    static final long serialVersionUID = 1706200525091981L;
    /** information for email notifyer */
    protected static String emailNotifyProperty = "lia.Monitor.notifyVOFTP";
    /** Module name */
    protected static String MyModuleName = "monVO_IO";
    protected static String clusterName = "osgVO_IO";
    /** strings that represent the MonALISA, vdt and globus location */
    protected String monalisaHome = null;
    protected String vdtLocation = null;
    protected String globusLocation = null;
    protected static final String GRIDFTP_LOG = "/var/gridftp.log";
    protected String ftplog = null; // GLOBUS_LOCATION + GRIDFTP_LOG;

    /** set's of constants */
    protected final int LINE_ELEMENTS_NO = 16;

    /**
     * Name of parameters calculated with this module.
     * To add a parameter, just add his name in the list below.
     */
    protected static String[] MyResTypes = {
    /* 0*/"ftpInput", /* 1*/"ftpOutput",
    /* 2*/"ftpRateIn", /* 3*/"ftpRateOut"
    /** other name for result parameter */
    };

    /** watcher to log file */
    protected LogWatcher lw = null;

    /** if it is the fisrt time when we run the doProcess */
    boolean firstTime = true;

    protected long lastTime = 0, doProcessInterval;

    UserFTPData monTotals;

    static int nrCall = 0;

    /**
     * Module constructor
     */
    public monVO_IO() {
        /** the base class constructor */
        super(MyModuleName, MyResTypes, emailNotifyProperty);

        String methodName = "constructor";
        addToMsg(methodName, "Constructor of " + ModuleName + " at " + currDate);
        addToMsg(methodName, "Info content: name " + info.name + " id " + info.id + " type " + info.type + " state "
                + info.state + " err " + info.error_count + ".");

        isRepetitive = true;
        info.ResTypes = ResTypes();
        /** write the last messges in the ML log file*/
        logit(sb.toString());
        /** prepare the new string buffer for new log messages */
        sb = new StringBuilder();
    }

    /**
     * init module with node and arguments
     * configuration file entry: *Node{monVO_IO, localhost, <arguments>}%30
     * <arguments> is a comme separated list (no quoted)
     * ftplog=/path-to-ftplog
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

        addToMsg(methodName, "Instantiating instance for Cluster (node in cf) " + clusterModuleName + " at " + currDate);
        addToMsg(methodName, "arguments: " + ((args == null) ? "NO ARGUMENTS" : args));
        addToMsg(methodName, "Node Info: name " + (Node.name == null ? "null" : Node.name) + " short_name "
                + (Node.name_short == null ? "null" : Node.name_short) + " cl.name "
                + (Node.cluster == null ? "null" : Node.cluster.name) + " fa.name "
                + (Node.farm == null ? "null" : Node.farm.name) + ".");

        /** Check the argument list and process information */
        if (args != null) {
            /** check if file location or globus_location are passed */
            argList = args.split(",");

            for (int i = 0; i < argList.length; i++) {

                argList[i] = argList[i].trim();
                logit("Argument " + i + ":" + argList[i] + ".");
                if (argList[i].startsWith("ftplog=")) {
                    ftplog = argList[i].substring("ftplog=".length()).trim();
                    addToMsg(methodName, "overrridden ftplog(" + ftplog + ")");
                }
                if (argList[i].startsWith("mapfile=")) {
                    mapfile = argList[i].substring("mapfile=".length()).trim();
                    addToMsg(methodName, "overrridden mapfile(" + mapfile + ")");
                }
                if (argList[i].startsWith("debug")) {
                    debugmode = true;
                    addToMsg(methodName, "overrridden debug(" + debugmode + ")");
                }
            }
        }
        info.ResTypes = ResTypes();
        return info;
    }

    /**
     * Set the MonALISA, VDT, Globus locations and other parameter
     * @throws Exception
     */
    protected void setEnvironment() throws Exception {
        /** the method name */
        String methodName = "setEnvironment";

        try {
            /** Determine the MonaALisa_HOME */
            monalisaHome = getEnvValue("MonaLisa_HOME");
            if (monalisaHome == null) {
                throw new Exception("MonaLisa_HOME environmental variable not set.");
            }
            addToMsg(methodName, "MonaLisa_HOME=" + monalisaHome);

            /** Determine the VDT_LOCATION */
            vdtLocation = getEnvValue("VDT_LOCATION");
            if (vdtLocation == null) {
                vdtLocation = monalisaHome + "/..";
            }
            addToMsg(methodName, "VDT_LOCATION=" + vdtLocation);

            /** Determine the GLOBUS_LOCATION */
            if (ftplog == null) {
                globusLocation = getEnvValue("GLOBUS_LOCATION");
                if (globusLocation == null) {
                    throw new Exception("GLOBUS_LOCATION environmental variable not set.");
                }
                addToMsg(methodName, "GLOBUS_LOCATION=" + globusLocation);
            }

            /** set the ftplog values */
            if (ftplog == null) {
                ftplog = globusLocation + GRIDFTP_LOG;
            }
            addToMsg(methodName, "ftplog...." + ftplog);

            /** check to see if GRIDFTP_LOG exists and readable */
            File probe = new File(ftplog);
            if (!probe.isFile()) {
                throw new Exception("Gridftp log (" + ftplog + ") not found.");
            }
            if (!probe.canRead()) {
                throw new Exception("Gridftp log (" + ftplog + ") is not readable.");
            }
            addToMsg(methodName,
                    "Gridftp log (" + ftplog + ")\n  last modified " + (new Date(probe.lastModified())).toString()
                            + " - size(" + probe.length() + " Bytes)");

        } catch (Exception ex) {
            throw new Exception("setEnvironment() - " + ex.getMessage() + " " + ex);
        }
        environmentSet = true;
    }

    @Override
    public Object doProcess() throws Exception {
        /** the method name */
        String methodName = "doProcess";

        /** result of the doProcess method */
        Vector result = new Vector();
        nrCall++;
        try {
            //long start = NTPDate.currentTimeMillis();

            /**
             * load the user name in voAccts
             * cleanup the voTotals... must add information about VO users
             */
            initializeEnv();
            voTotals = new Hashtable();

            /** add all users and set totals to zero */
            Enumeration e = VoList();
            while (e.hasMoreElements()) {
                String user = (String) e.nextElement();
                UserFTPData ufd = new UserFTPData(user, "totals");
                Vector v = new Vector();
                v.add(ufd);
                voTotals.put(user, v);
            }

            monTotals = new UserFTPData("osgVO_IO_Totals", "Total_Trafic");

            /** set the status to good */
            statusGood = true;

            /** fist time when I run this method */
            if (firstTime == true) {
                /** record the start moment (time in miliseconds) */
                setStartTime();

                /** for second time :) */
                firstTime = false;

                /** i will return zero results */
                result = createResultsVector();

                /** set the environment (only once we hope) */
                if (!environmentSet) {
                    setEnvironment();
                }

                /** but i put a watche to a log file */
                lw = new LogWatcher(ftplog);

                /** confirm by Email*/
                sendStatusEmail();

                /** record the finish moment (date and time in miliseconds) */
                setFinishTime();

                /** last time when doProcess was called */
                lastTime = NTPDate.currentTimeMillis();

                /** message to ML log file */
                //System.out.println(" first time when i was called: "+(new Date(NTPDate.currentTimeMillis())));
                addToMsg(methodName, " first time when i was called: " + (new Date(NTPDate.currentTimeMillis())));
            } else {
                //System.out.println("### again : "+(new Date(NTPDate.currentTimeMillis())));
                /** interval between two call of this method (in seconds) */
                long currentTime = NTPDate.currentTimeMillis();
                doProcessInterval = (currentTime - lastTime) / 1000;
                /** last time when doProcess was called */
                lastTime = currentTime;

                result = new Vector();

                /** take the par of log file */
                BufferedReader logBuffer = lw.getNewChunk();
                if (logBuffer == null) {
                    throw new Exception("Buffered Reader is null for [ " + ftplog + " ]");
                }

                /** record the start moment (time in miliseconds) */
                setStartTime();

                /** parse and buid the result */
                try {
                    parseFtpLog(logBuffer);
                } catch (Throwable t) {
                    if (logBuffer != null) {
                        logBuffer.close();
                    }
                    throw t;
                }

                /** close the buffer */
                if (logBuffer != null) {
                    logBuffer.close();
                }

                /** after the date was cumulated in the hashtabe, compute the rates */
                //computeFTPRate(doProcessInterval);
                monTotals.computeRate(doProcessInterval);

                /** create the results for update to ML (results in KBytes) */
                result = createResultsVector();

                /** confirm by Email*/
                sendStatusEmail();

                /** record the finish moment (date and time in miliseconds) */
                setFinishTime();

                /** message to ML log file */
                addToMsg(methodName, " again at: " + (new Date(NTPDate.currentTimeMillis())));
            }

            //long stop = NTPDate.currentTimeMillis();
            //System.out.println("[doProceesor - new] "+nrCall+" : "+(stop-start)+" milli-seconds");
        } catch (Throwable ex) {
            statusGood = false;
            sendExceptionEmail(methodName + " FATAL ERROR - " + ex.getMessage());
            throw new Exception(ex);
        }
        return result;
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
        int maxlinesNo = 7;
        /** numer of lines of buffer */
        int linesNo = 0;

        /** parse the buffer (part of a logfile) */
        try {
            while ((line = buffer.readLine()) != null) {
                linesNo++;
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
                if (linesNo < 5) {
                    debug(line);
                    if (lineResult != null) {
                        debug("Length: " + lineResult.size());
                    }
                }
                /** process the line result */
                if (lineResult != null) {
                    updateVOData(lineResult);
                }
            }
            debug(linesNo + " lines read from gridftp.log file");
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception(methodName + "() Exception: " + e);
        }
    }

    /**
     * Parse a line with some specific structure.
     * If the line in the log file changes the structure, you must modify just
     * the constants from the begin of this module.
     * 
     * Structure of a line in the log file (an example) is:
     *   [ 0] DATE=20050616052722.475032
     *   [ 1] HOST=osg.rcac.purdue.edu
     *   [ 2] PROG=wuftpd
     *   [ 3] NL.EVNT=FTP_INFO
     *   [ 4] START=20050616052722.410910
     *   [ 5] USER=ivdgl
     *   [ 6] FILE=/tmp/gridcat-_-test.gridcat.20252.remote
     *   [ 7] BUFFER=87380
     *   [ 8] BLOCK=65536
     *   [ 9] NBYTES=28
     *   [10] VOLUME=/tmp
     *   [11] STREAMS=1
     *   [12] STRIPES=1
     *   [13] DEST=1[129.79.4.64]
     *   [14] TYPE=STOR
     *   [15] CODE=226
     */
    protected Hashtable parseLine(String line) {
        /** the method name */
        String methodName = "parseLine";

        if (line == null) {
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
                //System.out.println("("+key+","+value+")");
                result.put(key, value);
            }
            return result;
        }
        return null;
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
            return;
        }
        String src, dest, sd;
        double newFtpIn, newFtpOut;
        try {
            if (!voTotals.containsKey(user)) {
                Vector userData = new Vector();
                UserFTPData ufd = new UserFTPData(user, "totals");
                userData.add(ufd);
                voTotals.put(user, userData);
            }

            /** get the result's vector for user */
            Vector userData = (Vector) voTotals.get(user);
            UserFTPData ufdTotals = (UserFTPData) userData.get(0);

            /** get the destination */
            dest = (String) info.get("DEST");
            int p1 = dest.indexOf('[');
            int p2 = dest.lastIndexOf(']');
            if ((p1 + 1) <= (p2 - 1)) {
                dest = dest.substring(p1 + 1, p2 - 1);
                dest = getMachineName(dest);
                dest = getMachineDomain(dest);
            }

            /** get the source */
            src = (String) info.get("HOST");
            src = getMachineDomain(src);

            /** build the pair (src<=>dest) */
            //sd = src+"<=>"+dest;
            //System.out.println(sd);

            /** input */
            if (info.get("TYPE").equals("STOR")) {

                sd = src;

                newFtpIn = (Double.valueOf(((String) info.get("NBYTES")))).doubleValue();

                ufdTotals.ftpInput += newFtpIn;
                userData.setElementAt(ufdTotals, 0);

                monTotals.ftpInput += ufdTotals.ftpInput;
                monTotals.computeRate(doProcessInterval);

                int goodPosition = getGoodResult(userData, src);
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
                ufdSD.computeRate(doProcessInterval);
            }

            /** output */
            if (info.get("TYPE").equals("RETR")) {

                sd = dest;

                newFtpOut = (Double.valueOf(((String) info.get("NBYTES")))).doubleValue();

                ufdTotals.ftpOutput = ufdTotals.ftpOutput + newFtpOut;
                userData.setElementAt(ufdTotals, 0);

                monTotals.ftpOutput += ufdTotals.ftpOutput;
                monTotals.computeRate(doProcessInterval);

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
                ufdSD.computeRate(doProcessInterval);
            }

            /** get the new values */
            voTotals.put(user, userData);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception(methodName + "() Exception: " + e);
        }
    }

    protected int getGoodResult(Vector v, String s) {
        /** the method name */
        String methodName = "getGoodResult";
        for (int i = 0; i < v.size(); i++) {
            UserFTPData aux = (UserFTPData) v.get(i);
            if (s.equals(aux.node_name)) {
                return i;
            }
        }
        return -1;
    }

    protected String getMachineName(String ip) {
        String dom = null;
        try {
            dom = InetAddress.getByName(ip).getCanonicalHostName();
        } catch (Exception e) {
            System.out.println(" Cannot get ip for node " + e);
            dom = ip;
        }
        return dom;
    }

    protected String getMachineDomain(String name) {
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

    /**
     * Compute the rate of input and output
     */
    //protected void computeFTPRate(long interval){
    /** the method name */
    //	String methodName = "computeFTPRate";
    //	Enumeration e = voTotals.keys();
    //	while(e.hasMoreElements()){
    //		String user = (String)e.nextElement();
    //		Vector ud = (Vector)voTotals.get(user);
    //		for(int i=0;i<ud.size();i++){
    //			UserFTPData ufd = (UserFTPData)ud.get(i);
    //			ufd.computeRate(interval);
    //			ud.setElementAt(ufd,i);
    //		}
    //		voTotals.put(user,ud);
    //	}
    //}

    /**
     * Create a vector of Result - output of this Module
     * @return vector results
     */
    protected Vector createResultsVector() {
        String methodName = "createResultsVector";
        Vector results = new Vector();
        double factor = 1024.0;
        Result result;

        /** first, create general totals Result */
        result = new Result(Node.getFarmName(), "osg_VO_IO_Totals", monTotals.node_name, ModuleName, MyResTypes);
        result.time = NTPDate.currentTimeMillis();
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
            UserFTPData aux = (UserFTPData) values.get(0);
            result = new Result(Node.getFarmName(), clusterName, aux.user_name, ModuleName, MyResTypes);
            result.time = NTPDate.currentTimeMillis();
            result.param[0] = aux.ftpInput / factor;
            result.param[1] = aux.ftpOutput / factor;
            result.param[2] = aux.ftpRateIn / factor;
            result.param[3] = aux.ftpRateOut / factor;
            results.addElement(result);
            for (int i = 1; i < values.size(); i++) {
                aux = (UserFTPData) values.get(i);
                result.addSet("ftpInput_" + aux.node_name, aux.ftpInput / factor);
                result.addSet("ftpRateIn_" + aux.node_name, aux.ftpRateIn / factor);
                result.addSet("ftpOutput_" + aux.node_name, aux.ftpOutput / factor);
                result.addSet("ftpRateOut_" + aux.node_name, aux.ftpRateOut / factor);
                results.addElement(result);
            }
        }
        return results;
    }

    public class UserFTPData {
        public String user_name = new String("user");
        public String node_name = new String("node");
        public double ftpInput, ftpOutput;
        public double ftpRateIn, ftpRateOut;

        UserFTPData(String clusterName, String nodeName) {
            user_name = clusterName;
            node_name = nodeName;
            ftpInput = 0.0;
            ftpOutput = 0.0;
            ftpRateIn = 0.0;
            ftpRateOut = 0.0;
        }

        void computeRate(long interval) {
            ftpRateIn = ftpInput / interval;
            ftpRateOut = ftpOutput / interval;
        }
    };

    /** main function - a test function */
    static public void main(String[] args) {
        /** write this function if you want to test the module without MonALISA
         * started. Do you want ?
         */
    }

}// end class
