/**
 * This module obtains information about the status of the nodes from LSF,
 * with the aid of the lshosts and bhosts commands. If "statistics" is given as argument,
 * the module provides statistics about the number of up/down nodes. 
 * If there is an error executing the commands, the module waits for
 * a number of seconds and retries to execute it. The number of seconds to wait can
 * be specified with the "delayIfError" argument (by default it is 20).
 */
package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.net.InetAddress;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.util.logging.MLLogEvent;
import lia.util.ntp.NTPDate;

public class monPN_LSF extends cmdExec implements MonitoringModule {

    /** serial version number */
    static final long serialVersionUID = 3103200618051980L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monPN_LSF.class.getName());

    protected String OsName = "linux";
    protected String ModuleName = "monPN_LSF";
    protected String clusterModuleName = "PN_LSF";
    protected String[] ResTypes = null;

    /** The name of the monitoring parameters to be "extracted" from the LSF report */
    static String[] lsfMetric = { "ncpus", "mem", "maxmem", "swp", "maxswp", "r1m", "r15m" };
    /** Rename them into : */
    static String[] myResTypes = { "NoCPUs", "MEM_free", "MEM_total", "SWAP_free", "SWAP_total", "Load1", "Load15" };
    private static final Pattern SPACE_PATTERN = Pattern.compile("(\\s)+");

    protected int nUpNodes;
    protected int nTotalNodes;
    protected int nUsedSlots;
    protected int nTotalSlots;

    /** Stores information about the LSF nodes. The keys are the hostnames and the values
     * are hashtables with node data.
     */
    protected Hashtable lsfNodeData = new Hashtable();

    protected String bhostsCmd = null;
    protected String lshostsCmd = null;
    protected String LSFHome = null;
    protected int nrCall = 0;
    protected boolean debugmode = false;
    protected boolean statisticsmode = true;
    protected boolean environmentSet = false;
    protected long DELAY_IF_ERROR = 20;
    protected boolean firsttime = true;

    /** The total time needed to execute the bhosts and lshosts commands. */
    protected long cmdExecTime = 0;

    protected boolean differentHostOutput = false;

    /** Module status result */
    private Result statusResult;

    /** The module's error code. It is 0 when the execution was successful. */
    int errorCode = 0;

    public monPN_LSF(String TaskName) {
        super(TaskName);
        canSuspend = false;
        info.ResTypes = myResTypes;
        isRepetitive = true;
    }

    public monPN_LSF() {
        super("monPN_LSF");
        canSuspend = false;
        info.ResTypes = myResTypes;
        isRepetitive = true;
    }

    @Override
    public MonModuleInfo init(MNode inNode, String args) {
        /** the method name */
        String methodName = "init";
        /** the arguments list from configuration file entry */
        String[] argList = null;

        isRepetitive = true;
        Node = inNode;
        clusterModuleName = Node.getClusterName() + "-" + ModuleName;
        info.ResTypes = myResTypes;

        try {
            /** Check the argument list and process information */
            if (args != null) {
                /** check if file location or globus_location are passed */
                argList = args.split("(\\s)*,(\\s)*");

                for (int i = 0; i < argList.length; i++) {
                    argList[i] = argList[i].trim();
                    if (argList[i].toLowerCase().startsWith("debug")) {
                        debugmode = true;
                        logger.log(Level.INFO, ModuleName + ": " + methodName + ": overrridden Debug(" + debugmode
                                + ")");
                        continue;
                    }

                    if (argList[i].toLowerCase().startsWith("delayiferror")) {
                        try {
                            DELAY_IF_ERROR = Long.parseLong(argList[i].split("(\\s)*=(\\s)*")[1].trim());
                        } catch (Throwable t) {
                            DELAY_IF_ERROR = 20;
                        }

                        logger.log(Level.INFO, ModuleName + ": " + methodName + ": overrridden DelayIfError("
                                + DELAY_IF_ERROR + ")");
                        DELAY_IF_ERROR *= 1000;
                        continue;
                    }

                    if (argList[i].toLowerCase().startsWith("cansuspend")) {
                        boolean cSusp = false;
                        try {
                            cSusp = Boolean.valueOf(argList[i].split("(\\s)*=(\\s)*")[1].trim()).booleanValue();
                        } catch (Throwable t) {
                            cSusp = false;
                        }
                        canSuspend = cSusp;
                        continue;
                    }

                    if (argList[i].toLowerCase().startsWith("disablestatistics")) {
                        statisticsmode = false;
                        logger.log(Level.INFO, ModuleName + ": " + methodName + ": overrridden Statistics("
                                + statisticsmode + ")");
                        continue;
                    }

                    if (argList[i].toLowerCase().startsWith("differenthostoutput")) {
                        differentHostOutput = true;
                        logger.log(Level.INFO, ModuleName + ": " + methodName + ": overrridden Statistics("
                                + statisticsmode + ")");
                        continue;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        info.name = ModuleName;
        return info;
    }

    /**
     * Gets the LSF location and initializes the data structures.
     * @throws Exception
     */
    protected void setEnvironment() throws Exception {

        try {
            /** try to get the LSF_LOCATION */
            LSFHome = AppConfig.getGlobalEnvProperty("LSF_LOCATION");
            if (LSFHome == null) {
                logger.log(Level.WARNING, "The LSF_LOCATION environment variable is not set!");
                throw new Exception("LSF_LOCATION environmental variable not set.");
            }

            /** create command */
            bhostsCmd = LSFHome + "/bin/bhosts";
            lshostsCmd = LSFHome + "/bin/lshosts";
            /* check if the executables actually exists */

            File bfd = new File(bhostsCmd);
            File lfd = new File(lshostsCmd);

            if (!bfd.exists() || !lfd.exists()) {
                this.errorCode = VO_Utils.PN_CMD_NOT_EXIST;
                Integer iErrorCode = Integer.valueOf(errorCode);
                MLLogEvent mlle = new MLLogEvent();
                mlle.logParameters.put("Error Code", iErrorCode);
                mlle.logParameters.put("Error Message", VO_Utils.pnErrCodes.get(iErrorCode));
                logger.log(Level.WARNING, "The LSF commands " + bhostsCmd + " or " + lshostsCmd + " do not exist.",
                        new Object[] { mlle });
                throw new Exception("The LSF commands " + bhostsCmd + " or " + lshostsCmd + " do not exist.");
            }

            bhostsCmd = bhostsCmd + " -l 2>&1 && echo ML_OSGVOJOBS_OK";
            lshostsCmd = lshostsCmd + " -w 2>&1 && echo ML_OSGVOJOBS_OK";
            logger.log(Level.INFO, "Using LSF location: " + LSFHome);
            logger.log(Level.INFO, "Using bhosts command: " + bhostsCmd);
            logger.log(Level.INFO, "Using lshosts command: " + lshostsCmd);
        } catch (Exception ex) {
            throw new Exception("setEnvironment() - " + ex.getMessage() + " " + ex);
        }
        environmentSet = true;
    }

    /**
     * @see lia.util.DynamicThreadPoll.SchJob#doProcess()
     */
    @Override
    public Object doProcess() throws Exception {
        /** the method name */
        String methodName = "doProcess";

        long tStart = System.currentTimeMillis();
        nrCall++;
        Vector results = new Vector();

        if (!environmentSet) {
            setEnvironment();
        }

        nTotalNodes = 0;
        nUpNodes = 0;
        nTotalSlots = 0;
        nUsedSlots = 0;
        lsfNodeData.clear();

        try {
            getCommandsOutput();
        } catch (Exception e) {
            logger.log(Level.WARNING, "monPN_LSF got exception: ", e);
            logger.log(Level.INFO, "Failed getting nodes status, retrying after " + (DELAY_IF_ERROR / 1000) + "s...");
            try {
                Thread.sleep(DELAY_IF_ERROR);
            } catch (Throwable t) {
            }

            try {
                getCommandsOutput();
            } catch (Exception e2) {
                if (pro != null) {
                    pro.destroy();
                    pro = null;
                }
                logger.log(Level.INFO, "Second attempt to get nodes status failed, no results were sent");
                throw e2;
            }
        }

        results = createResults();

        long tEnd = System.currentTimeMillis();
        /** create the status result */
        statusResult = new Result();
        statusResult.time = NTPDate.currentTimeMillis();
        statusResult.NodeName = "Status";
        statusResult.ClusterName = "PN_LSF_Statistics";
        statusResult.FarmName = Node.getFarmName();
        statusResult.Module = ModuleName;
        statusResult.addSet("PN_LSF_Status", errorCode);
        statusResult.addSet("CmdExecTime", cmdExecTime);
        statusResult.addSet("TotalProcessingTime", tEnd - tStart);
        results.add(statusResult);

        logger.log(Level.INFO, ModuleName + ": " + methodName + ": " + nrCall + " => time to run: " + (tEnd - tStart)
                + "ms, sent " + results.size() + " results.");
        firsttime = false;

        return results;
    }

    void getCommandsOutput() throws Exception {

        /** execute the lshosts and bosts command */

        long t1 = System.currentTimeMillis();
        BufferedReader buff1 = procOutput(lshostsCmd, -1);
        BufferedReader buff2 = procOutput(bhostsCmd, -1);
        this.cmdExecTime = System.currentTimeMillis() - t1;

        if ((buff1 == null) || (buff2 == null)) {
            throw new Exception("No output for the LSF commands");
        }

        parseLshostsOutput(buff1);
        parseBhostsOutput(buff2);
    }

    public void parseLshostsOutput(BufferedReader buffer) throws Exception {
        String line;
        Hashtable vals = null;
        Hashtable paramIndex = null;

        boolean haveErrorOutput = true; // shows if the command executed successfully
        boolean canProcess = false;

        // Store the first 10 lines from the command's output so that
        // we can display them if there is an error
        StringBuilder firstLines = new StringBuilder();
        int lineCnt = 0;

        int okCnt = 0; // the number of commands executed successfully
        try {
            while ((line = buffer.readLine()) != null) {
                if (lineCnt < 10) {
                    firstLines.append(line + "\n");
                }
                lineCnt++;

                if (line.startsWith(VO_Utils.okString)) {
                    okCnt++;
                    continue;
                }
                if (line.startsWith("HOST_NAME") && (line.indexOf("ncpus") > 0) && (line.indexOf("maxmem") > 0)
                        && (line.indexOf("maxswp") > 0)) {
                    haveErrorOutput = false;
                    canProcess = true;
                    paramIndex = VO_Utils.getParamIndex(line);
                    continue;
                }

                if (canProcess) {
                    vals = new Hashtable();
                    String[] tokens = SPACE_PATTERN.split(line);

                    /*
                    if (tokens.length < 9)
                    	throw new Exception ("Unrecognized line format in lshosts output: " +
                    			line);
                    */
                    String sNcpus, sMaxmem, sMaxswp;
                    sNcpus = getSParameter("ncpus", tokens, paramIndex);
                    if (sNcpus != null) {
                        vals.put("ncpus", sNcpus);
                    }
                    sMaxmem = getSParameter("maxmem", tokens, paramIndex);
                    if (sMaxmem != null) {
                        vals.put("maxmem", sMaxmem);
                    }
                    sMaxswp = getSParameter("maxswp", tokens, paramIndex);
                    if (sMaxswp != null) {
                        vals.put("maxswp", sMaxswp);
                    }
                    if (lsfNodeData != null) {
                        lsfNodeData.put(tokens[0], vals);
                    }
                }

            }

            if (okCnt < 1) {
                this.errorCode = VO_Utils.PN_RET_ERROR;
                throw new Exception("The lshosts command returned with error.");
            }
        } catch (Exception exc) {
            if (this.errorCode == 0) {
                this.errorCode = VO_Utils.PN_PARSE_ERROR;
            }
            Integer iErrorCode = Integer.valueOf(errorCode);
            MLLogEvent mlle = new MLLogEvent();
            mlle.logParameters.put("Error Code", iErrorCode);
            mlle.logParameters.put("Error Message", VO_Utils.pnErrCodes.get(iErrorCode));
            mlle.logParameters.put("First lines from the command output: ", firstLines);
            logger.log(Level.WARNING, VO_Utils.pnErrCodes.get(iErrorCode), new Object[] { mlle, exc });
            throw exc;
        }
    }

    public void parseBhostsOutput(BufferedReader buffer) throws Exception {
        String hostname = null;
        String line, tokens[], sParamVal;
        Hashtable vals = null;

        boolean haveErrorOutput = true; // shows if the command executed successfully
        boolean haveNewRecord = false;

        Hashtable htHostIndex = null;
        Hashtable htLoadIndex = null;

        // Store the first 10 lines from the command's output so that
        // we can display them if there is an error
        StringBuilder firstLines = new StringBuilder();
        int lineCnt = 0;

        int okCnt = 0; // the number of commands executed successfully.

        try {
            while ((line = buffer.readLine()) != null) {
                if (lineCnt < 10) {
                    firstLines.append(line + "\n");
                }
                lineCnt++;

                if (line.startsWith(VO_Utils.okString)) {
                    okCnt++;
                    continue;
                }
                if (line.startsWith("HOST")) {
                    haveErrorOutput = false;
                    haveNewRecord = true;
                    tokens = SPACE_PATTERN.split(line);

                    hostname = tokens[1];
                    if (lsfNodeData != null) {
                        vals = (Hashtable) lsfNodeData.get(hostname);
                    }
                    if ((vals == null) || hostname.equals("lost_and_found")) {
                        logger.fine("Ignoring host: " + hostname);
                        haveNewRecord = false;
                    }
                    continue;
                }

                if (line.startsWith("STATUS") && (line.indexOf("MAX") > 0) && (line.indexOf("NJOBS") > 0)
                        && haveNewRecord) {
                    if ((htHostIndex == null) || differentHostOutput) {
                        htHostIndex = VO_Utils.getParamIndex(line);
                    }
                    line = buffer.readLine();
                    tokens = line.split("(\\s)+");

                    //if (tokens.length < 10)
                    //throw new Exception ("Unrecognized line format in bhosts output: " +
                    //	line);

                    nTotalNodes++;
                    sParamVal = getSParameter("STATUS", tokens, htHostIndex);
                    if ((sParamVal != null) && (sParamVal.equals("ok") || sParamVal.startsWith("close"))) {
                        nUpNodes++;
                    }

                    try {
                        sParamVal = getSParameter("MAX", tokens, htHostIndex);
                        if ((sParamVal != null) && !sParamVal.equals("-") && (nTotalSlots >= 0)) {
                            nTotalSlots += Integer.parseInt(sParamVal);
                        } else {
                            nTotalSlots = -1;
                        }

                        sParamVal = getSParameter("NJOBS", tokens, htHostIndex);
                        if ((sParamVal != null) && !sParamVal.equals("-")) {
                            nUsedSlots += Integer.parseInt(sParamVal);
                        }
                        continue;
                    } catch (Exception e) {
                        nTotalSlots = -1;
                    }

                }

                if ((line.indexOf("CURRENT LOAD USED FOR SCHEDULING") >= 0) && haveNewRecord) {
                    line = buffer.readLine();
                    if ((line.indexOf("r1m") < 0) || (line.indexOf("r15m") < 0) || (line.indexOf("swp") < 0)
                            || (line.indexOf("mem") < 0)) {
                        throw new Exception("Unrecognized line format in bhosts output: " + line + " ");
                    }
                    if ((htLoadIndex == null) || differentHostOutput) {
                        htLoadIndex = VO_Utils.getParamIndex(line);
                    }

                    line = buffer.readLine();
                    if (line == null) {
                        throw new Exception("Unexpected end of output for bhosts");
                    }
                    tokens = SPACE_PATTERN.split(line);

                    String sR1m, sR15m, sSwp, sMem;
                    if (vals != null) {
                        sR1m = getSParameter("r1m", tokens, htLoadIndex);
                        if (sR1m != null) {
                            vals.put("r1m", sR1m);
                        }
                        sR15m = getSParameter("r15m", tokens, htLoadIndex);
                        if (sR15m != null) {
                            vals.put("r15m", sR15m);
                        }
                        sSwp = getSParameter("swp", tokens, htLoadIndex);
                        if (sSwp != null) {
                            vals.put("swp", sSwp);
                        }
                        sMem = getSParameter("mem", tokens, htLoadIndex);
                        if (sMem != null) {
                            vals.put("mem", sMem);
                        }

                        if (lsfNodeData != null) {
                            lsfNodeData.put(hostname, vals);
                        }
                    } else {
                        Integer iErrorCode = Integer.valueOf(VO_Utils.PN_PARSE_ERROR);
                        MLLogEvent mlle = new MLLogEvent();
                        mlle.logParameters.put("Error Code", iErrorCode);
                        String errMessage = "Error - null parameter map when processing line: " + line;
                        //mlle.logParameters.put("Error Message", errMessage);
                        logger.log(Level.INFO, errMessage, new Object[] { mlle });
                    }
                    haveNewRecord = false;
                    continue;
                }

            }

            if (okCnt < 1) {
                this.errorCode = VO_Utils.PN_RET_ERROR;
                throw new Exception("The bhosts command returned with error.");
            }
        } catch (Exception exc) {
            if (this.errorCode == 0) {
                this.errorCode = VO_Utils.PN_PARSE_ERROR;
            }
            Integer iErrorCode = Integer.valueOf(errorCode);
            MLLogEvent mlle = new MLLogEvent();
            mlle.logParameters.put("Error Code", iErrorCode);
            mlle.logParameters.put("Error Message", VO_Utils.pnErrCodes.get(iErrorCode));
            mlle.logParameters.put("First lines from the command output: ", firstLines.toString());
            logger.log(Level.WARNING, VO_Utils.pnErrCodes.get(iErrorCode), new Object[] { mlle, exc });
            throw exc;
        }
    }

    public Vector createResults() {
        /** the method name */
        String methodName = "createResults";

        Vector results = new Vector();
        Result result;
        double factor = 1.0;
        long resultTime = NTPDate.currentTimeMillis();
        boolean haveResults = false;

        /** then, create result for each node */
        Enumeration nodes = lsfNodeData.keys();
        while (nodes.hasMoreElements()) {
            /** get the node host name */
            String nodeName = (String) nodes.nextElement();
            /** get the node values */
            Hashtable values = (Hashtable) lsfNodeData.get(nodeName);

            result = new Result();
            result.FarmName = Node.getFarmName();
            result.ClusterName = Node.getClusterName();
            result.NodeName = nodeName;
            result.Module = ModuleName;
            result.time = resultTime;

            boolean haveParam = false;
            String sVal = null;
            for (int i = 0; i < lsfMetric.length; i++) {
                factor = 1.0;
                double dVal;
                try {
                    sVal = (String) values.get(lsfMetric[i]);
                    if (sVal == null) {
                        continue;
                    }
                    if (sVal.startsWith("*")) {
                        sVal = sVal.substring(1);
                    }
                    if (sVal.indexOf("-") >= 0) {
                        continue;
                    }
                    if (sVal.indexOf("K") >= 0) {
                        factor = 1.0 / 1024.0;
                    } else if (sVal.indexOf("G") >= 0) {
                        factor = 1024.0;
                    }
                    if ((sVal.indexOf("K") >= 0) || (sVal.indexOf("M") >= 0) || (sVal.indexOf("G") >= 0)) {
                        sVal = sVal.substring(0, sVal.length() - 1);
                    }
                    dVal = Double.parseDouble(sVal) * factor;
                    result.addSet(myResTypes[i], dVal);
                    haveParam = true;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error parsing parameter, ignoring it: " + sVal, e);
                }
            }
            if (firsttime) {
                logger.log(Level.INFO, result.toString());
            }
            if (haveParam) {
                results.addElement(result);
            }

            haveResults = true;
        } // while

        // create the statistics cluster
        if (statisticsmode && haveResults) {
            Result statisticalResult = new Result();
            statisticalResult.time = resultTime;
            statisticalResult.FarmName = Node.getFarmName();
            statisticalResult.ClusterName = "PN_LSF_Statistics";
            statisticalResult.NodeName = "Statistics";
            statisticalResult.addSet("Total Nodes", lsfNodeData.size());
            //statisticalResult.addSet("Total Available Nodes", nUpNodes);
            if (nTotalSlots > 0) {
                statisticalResult.addSet("Total Slots", nTotalSlots);
                statisticalResult.addSet("Total Free  Slots", nTotalSlots - nUsedSlots);
            }
            statisticalResult.addSet("Total Down Nodes", lsfNodeData.size() - nUpNodes);
            results.add(statisticalResult);
            if (firsttime) {
                logger.log(Level.INFO, statisticalResult.toString());
            }
        }

        return results;
    }

    /**
     * Helper function that searches for a parameter value in a parameter list.
     * @param paramName
     * @param tokens
     * @param paramIndex
     * @return
     */
    protected String getSParameter(String paramName, String[] tokens, Hashtable paramIndex) {

        if ((paramName == null) || (tokens == null) || (paramIndex == null)) {
            return null;
        }

        Integer iIndex = (Integer) paramIndex.get(paramName);
        if ((iIndex != null) && (tokens.length > iIndex.intValue())) {
            return tokens[iIndex.intValue()];
        } else {
            return null;
        }

    }

    /**
     * @see lia.Monitor.monitor.MonitoringModule#ResTypes()
     * @see lia.Monitor.monitor.MonitoringModule#getInfo()
     * @see lia.Monitor.monitor.MonitoringModule#getOsName()
     */
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

    /**
     * Main method for testing module
     * @param args
     */
    public static void main(String[] args) {
        String host = "localhost";
        String ad = null;

        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        monPN_LSF aa = new monPN_LSF();
        MonModuleInfo info = aa.init(new MNode(host, ad, null, null), null);

        try {
            Object bb = aa.doProcess();
            if (bb instanceof Vector) {
                Vector results = (Vector) bb;
                int dim = results.size();
                System.out.println(" Received a Vector having " + dim + " results...");
                for (int i = 0; i < dim; i++) {
                    System.out.println(((Result) results.elementAt(i)).toString());
                }
            }
        } catch (Exception e) {
            System.out.println(" failed to process ");
        }
    }

}
