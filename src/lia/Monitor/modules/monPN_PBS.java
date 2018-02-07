package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.net.InetAddress;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.util.logging.MLLogEvent;
import lia.util.ntp.NTPDate;

/**
 * 
 * @author Florin Pop
 *
 */

public class monPN_PBS extends cmdExec implements MonitoringModule {

    /** serial version number */
    static final long serialVersionUID = 1706200525091981L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monPN_PBS.class.getName());

    /** The module name, cluster name, and OS */
    static public String ModuleName = "monPN_PBS";
    static public String clusterModuleName = "PN_PBS";
    static public String OsName = "linux";

    protected boolean environmentSet = false;
    protected String[] ResTypes = null;

    /** The names of the parameters reported by PBS */
    static String[] pbsMetric = { "ncpus", "availmem", "physmem", "loadave" };

    /** The names above will be "translated" to the Ganglia names */
    static String[] myResTypes = { "NoCPUs", "VIRT_MEM_free", "MEM_total", "Load1" };

    /** The path to the PBS directory */
    String pbsLocation = null;
    String cmd = null;
    String localCmd = null;
    protected boolean haveCommand = true;

    /** 
     * Keeps monitoring information for the PBS nodes. The keys are the names
     * of the nodes and the elements are hashtables containing (parameter name,
     * parameter value) pairs.
     */
    protected Hashtable pbsNodeData = new Hashtable();

    /** Determines is we send results with the number of up/down nodes */
    protected boolean statisticsmode = true;

    /** Allows to make statistics just for the nodes with a specified label */
    protected String nodesLabel = null;

    /** 
     * Number of seconds to wait if there was an error executing the pbsnodes
     * command. After waiting, the module retries to execute the command.
     */
    protected long DELAY_IF_ERROR = 20;

    protected long CMD_DELAY = 2 * 60 * 1000;

    /** The names of the central manager daemons that we will query. */
    protected Vector serverNames = new Vector();

    /** The total number of nodes (machines). */
    protected int totalNodeNo;

    /** The total number of free nodes (no jobs in execution). */
    protected int freeNodesNo;

    /** The total number of free nodes (not available machine). */
    protected int downNodeNo;

    /** doProcess run order */
    protected long call_no = 0;

    /** if is true, print some debug informations */
    protected boolean debugmode = false;

    /** if is true, we are at the first module doProcess run */
    protected boolean firsttime = true;

    /** The time needed to execute the pbsnodes command. */
    protected long cmdExecTime = 0;

    /** Module status result */
    private Result statusResult;

    /** status for execution process */
    private int errorCodeParam = 0;

    /** Module's error codes */
    private Integer errorCode;

    StringBuilder cmdFirstLines = new StringBuilder();

    public monPN_PBS(String TaskName) {
        super(TaskName);
        canSuspend = false;
        isRepetitive = true;
        info.ResTypes = myResTypes;
    }

    public monPN_PBS() {
        super("monPN_PBS");
        canSuspend = false;
        isRepetitive = true;
        info.ResTypes = myResTypes;
    }

    @Override
    public MonModuleInfo init(MNode inNode, String args) {
        /** the method name */
        String methodName = "init";

        /** the arguments list from configuration file entry */
        String[] argList = new String[] {};

        info = new MonModuleInfo();
        isRepetitive = true;
        Node = inNode;
        clusterModuleName = Node.getClusterName() + "-" + ModuleName;
        info.ResTypes = myResTypes;

        /** name of the servers that we interogate for nodes informations */
        String serverName = null;

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
                    if (argList[i].toLowerCase().indexOf("server") != -1) {
                        try {
                            serverName = argList[i].split("(\\s)*=(\\s)*")[1].trim();
                            serverNames.add(serverName);
                        } catch (Throwable t) {
                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, " Got exception parsing server option", t);
                            }
                            serverName = null;
                        }
                        logger.log(Level.INFO, ModuleName + ": " + methodName + ": overrridden Server(" + serverName
                                + ")");
                        continue;
                    }
                    if (argList[i].toLowerCase().indexOf("cansuspend") != -1) {
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
                    if (argList[i].toLowerCase().indexOf("nodeslabel") != -1) {
                        try {
                            nodesLabel = argList[i].split("(\\s)*=(\\s)*")[1].trim();
                        } catch (Throwable t) {
                            nodesLabel = null;
                        }
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
     * Get the PBS locations and set other data structures
     * @throws Exception
     */
    protected void setEnvironment() throws Exception {
        /** the method name */
        StringBuilder pbsCmd = new StringBuilder();
        /** try to get the PBS_LOCATION */
        try {
            pbsLocation = AppConfig.getGlobalEnvProperty("PBS_LOCATION");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Got exception when obtaining env variable: ", e);
        }
        if (pbsLocation == null) {
            logger.log(Level.WARNING, "PBS_LOCATION environmental variable not set!");
            throw new Exception("PBS_LOCATION environmental variable not set!");
        }

        /** create command */
        cmd = pbsLocation + "/bin/pbsnodes";

        /** make a copy for local command */
        localCmd = cmd;

        try {
            /** check if the pbsnodes executable actually exists */
            File fd = new File(localCmd);
            if (!fd.exists()) {
                haveCommand = false;
                errorCodeParam = VO_Utils.PN_CMD_NOT_EXIST;
                /** put error message in MLLogEvent */
                errorCode = Integer.valueOf(errorCodeParam);
                try {
                    MLLogEvent mlle = new MLLogEvent();
                    mlle.logParameters.put("Error Code", errorCode);
                    mlle.logParameters.put(errorCode, VO_Utils.pnErrCodes.get(errorCode));
                    logger.log(Level.WARNING, "[monPN_PBS] The local command " + cmd + " not exist.",
                            new Object[] { mlle });
                } catch (Exception ex) {
                }
            } else {
                logger.log(Level.INFO, "[monPN_PBS] The local command: " + cmd);
                haveCommand = true;
            }

            /** create the command that the module has to execute */
            if (serverNames.size() == 0) {
                pbsCmd.append(cmd + " -a");
                if (this.nodesLabel != null) {
                    pbsCmd.append(" " + nodesLabel);
                }
            } else {
                for (int ind = 0; ind < serverNames.size(); ind++) {
                    if (pbsCmd.length() > 0) {
                        pbsCmd.append(" ; ");
                    }
                    pbsCmd.append(cmd + " -s " + serverNames.get(ind) + " -a");
                    if (this.nodesLabel != null) {
                        pbsCmd.append(" " + nodesLabel);
                    }
                }
            }

            cmd = pbsCmd.toString();

            logger.log(Level.INFO, "Using PBS location: " + pbsLocation + "\nUsing command: " + cmd);
        } catch (Exception ex) {
            logger.log(Level.WARNING, "setEnvironment() " + ex.getMessage() + " " + ex);
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

        /** the module results */
        Vector results = new Vector();

        /** set status on SUCCESS */
        errorCodeParam = VO_Utils.PN_SUCCESS;

        /** set the start time for doProcess method */
        long start = System.currentTimeMillis();

        /** increment call_no */
        call_no = (call_no + 1) % Long.MAX_VALUE;

        /** set the environmental variable for this module (only first time) */
        if (!environmentSet) {
            setEnvironment();
        }

        /** reset the monitoring information for the PBS nodes. */
        downNodeNo = 0;
        totalNodeNo = 0;
        freeNodesNo = 0;
        pbsNodeData.clear();

        /** check if the pbsnodes executable actually exists */
        File fd = new File(localCmd);
        if (!fd.exists()) {
            haveCommand = false;
            errorCodeParam = VO_Utils.PN_CMD_NOT_EXIST;
            /** put error message in MLLogEvent */
            errorCode = Integer.valueOf(errorCodeParam);
            try {
                MLLogEvent mlle = new MLLogEvent();
                mlle.logParameters.put("Error Code", errorCode);
                mlle.logParameters.put(errorCode, VO_Utils.pnErrCodes.get(errorCode));
                logger.log(Level.WARNING, "[monPN_PBS] The command " + localCmd + " not exist.", new Object[] { mlle });
            } catch (Exception ex) {
            }
        } else {
            haveCommand = true;
        }

        /** procees the pbsnodes command output only if we have the pbsnodes command */
        if (haveCommand) {
            try {
                /** run pbsnodes and parse the output */
                getCommandOutput();
            } catch (Exception e) {
                /** null or wrong output */
                errorCodeParam = VO_Utils.PN_NULL_OUTPUT;
                /** put error message in MLLogEvent */
                errorCode = Integer.valueOf(errorCodeParam);
                try {
                    MLLogEvent mlle = new MLLogEvent();
                    mlle.logParameters.put("Error Code", errorCode);
                    mlle.logParameters.put(errorCode, VO_Utils.pnErrCodes.get(errorCode));
                    logger.log(Level.WARNING, "monPN_PBS got exception: " + e, new Object[] { mlle, e });
                    logger.log(Level.INFO, "Failed getting nodes status, retrying after " + (DELAY_IF_ERROR / 1000)
                            + " s...");
                } catch (Exception ex) {
                }

                try {
                    Thread.sleep(DELAY_IF_ERROR);
                } catch (Throwable t) {
                }

                /** try to run pbsnodes command again */
                try {
                    getCommandOutput();
                } catch (Exception e2) {
                    /** null or rong output */
                    if (pro != null) {
                        pro.destroy();
                        pro = null;
                    }
                    errorCodeParam = VO_Utils.PN_SECOND_NULL_OUTPUT;
                    /** put error message in MLLogEvent */
                    errorCode = Integer.valueOf(errorCodeParam);
                    try {
                        MLLogEvent mlle = new MLLogEvent();
                        mlle.logParameters.put("Error Code", errorCode);
                        mlle.logParameters.put(errorCode, VO_Utils.pnErrCodes.get(errorCode));
                        logger.log(Level.INFO, "Second attempt to get nodes status failed, no results were sent",
                                new Object[] { mlle, e2 });
                    } catch (Exception ex) {
                    }
                }
            }
            results = createResults();
        }

        /** set the stop time for doProcess method */
        long stop = System.currentTimeMillis();

        /** create the status result */
        statusResult = new Result();
        statusResult.time = NTPDate.currentTimeMillis();
        statusResult.NodeName = "Status";
        statusResult.ClusterName = "PN_PBS_Statistics";
        statusResult.FarmName = Node.getFarmName();
        statusResult.Module = ModuleName;
        statusResult.addSet("PN_PBS_Status", errorCodeParam);
        statusResult.addSet("CmdExecTime", cmdExecTime);
        statusResult.addSet("TotalProcessingTime", (stop - start));

        /** add it to module results */
        results.add(statusResult);

        logger.log(Level.INFO, methodName + " - monPN_PBS [" + call_no + "] " + "Sent " + results.size() + " results. "
                + "Execution time: " + (stop - start) + " ms. " + "Status=" + errorCodeParam + ": "
                + VO_Utils.pnErrCodes.get(Integer.valueOf(errorCodeParam)));

        firsttime = false;

        return results;
    }

    void getCommandOutput() throws Exception {
        /** the method name */
        String methodName = "getCommandOutput";

        long t1 = System.currentTimeMillis();

        try {
            /** execute the pbsnodes command */
            BufferedReader buffer = procOutput(cmd, CMD_DELAY);
            this.cmdExecTime = System.currentTimeMillis() - t1;

            if (buffer == null) {
                errorCodeParam = VO_Utils.PN_NULL_OUTPUT;
                /** put error message in MLLogEvent */
                errorCode = Integer.valueOf(errorCodeParam);
                try {
                    MLLogEvent mlle = new MLLogEvent();
                    mlle.logParameters.put("Error Code", errorCode);
                    mlle.logParameters.put(errorCode, VO_Utils.pnErrCodes.get(errorCode));
                    logger.log(Level.INFO, methodName + "(): No output for the pbsnodes command", new Object[] { mlle });
                } catch (Exception ex) {
                }
            } else {
                parsePBSOutput(buffer);
            }

        } catch (Exception t) {
            long t2 = System.currentTimeMillis();
            logger.fine("Cmd execution time: " + (t2 - t1) + "; max delay: " + CMD_DELAY);
            if ((t2 - t1) > CMD_DELAY) {
                String msg = " Got timeout when executing pbsnodes command.";
                /** null or rong output */
                errorCodeParam = VO_Utils.PN_COMMAND_TIMEOUT;
                /** put error message in MLLogEvent */
                errorCode = Integer.valueOf(errorCodeParam);
                try {
                    MLLogEvent mlle = new MLLogEvent();
                    mlle.logParameters.put("Error Code", errorCode);
                    mlle.logParameters.put(errorCode, VO_Utils.pnErrCodes.get(errorCode));
                    logger.log(Level.WARNING, msg, new Object[] { mlle });
                } catch (Exception ex) {
                }
            }
            throw t;
        }

    }

    public void parsePBSOutput(BufferedReader buffer) throws Exception {
        /** the method name */
        String methodName = "parsePBSOutput";
        String line;
        String node_name = "";
        Hashtable vals = new Hashtable();

        boolean haveErrorOutput = true; // shows if the command executed successfully
        cmdFirstLines = new StringBuilder();
        int lineCnt = 0;

        try {
            while ((line = buffer.readLine()) != null) {
                if (lineCnt < 10) {
                    cmdFirstLines.append(line + "\n");
                }
                lineCnt++;

                haveErrorOutput = false;
                if (!line.startsWith(" ") && (line.length() != 0)) {
                    vals = new Hashtable();
                    node_name = line;
                }

                if (line.trim().startsWith("state =")) {
                    String state = line.split(" = ")[1];

                    if (state.indexOf("free") >= 0) {
                        freeNodesNo++;
                    }
                    if (state.indexOf("down") >= 0) {
                        downNodeNo++;
                    }
                }

                if (line.trim().startsWith("status = ")) {
                    line = (line.trim()).substring("status = ".length());
                    String[] lineElements = new String[] {};
                    lineElements = line.split(",");
                    for (String lineElement : lineElements) {
                        String key = lineElement.split("=")[0].trim();
                        String value = lineElement.split("=")[1].trim();
                        if (isGoodParameter(key) >= 0) {
                            vals.put(key, value);
                        }
                    }
                }

                if (line.trim().startsWith("resources_available.mem")) {
                    String key = "availmem";
                    String value = line.split(" = ")[1];
                    vals.put(key, value);
                    errorCodeParam = VO_Utils.PN_NON_TORQUE;
                }

                if (line.trim().startsWith("resources_available.ncpus")) {
                    String key = "ncpus";
                    String value = line.split(" = ")[1];
                    vals.put(key, value);
                    errorCodeParam = VO_Utils.PN_NON_TORQUE;
                }

                if (line.length() == 0) {
                    if (vals.size() != 0) {
                        pbsNodeData.put(node_name, vals);
                        totalNodeNo++;
                    }
                }
            }
            if (haveErrorOutput) {
                errorCodeParam = VO_Utils.PN_NULL_OUTPUT;
                logger.log(Level.WARNING, methodName + "() The pbsnodes command returned error!");
            }
        } catch (Exception t) {
            errorCodeParam = VO_Utils.PN_PARSE_ERROR;
            /** put error message in MLLogEvent */
            errorCode = Integer.valueOf(errorCodeParam);
            try {
                MLLogEvent mlle = new MLLogEvent();
                mlle.logParameters.put("Error Code", errorCode);
                mlle.logParameters.put(errorCode, VO_Utils.pnErrCodes.get(errorCode));
                logger.log(Level.WARNING, methodName + "(): " + t.getMessage(), new Object[] { mlle, t });
            } catch (Exception e) {
            }
            throw t;
        } // end try/catch
    }

    int isGoodParameter(String param_name) {
        for (int i = 0; i < pbsMetric.length; i++) {
            if (param_name.equals(pbsMetric[i])) {
                return i;
            }
        }
        return -1;
    }

    public Vector createResults() {
        /** the method name */
        String methodName = "createResults";

        Vector results = new Vector();

        double factor = 1024.0;
        long resultTime = NTPDate.currentTimeMillis();
        boolean haveResults = false;

        /** then, create result for each user */
        Enumeration nodes = pbsNodeData.keys();
        while (nodes.hasMoreElements()) {
            /** get the node host name */
            String nodeName = (String) nodes.nextElement();
            /** get the node values */
            Hashtable values = (Hashtable) pbsNodeData.get(nodeName);

            /** create the totals Result for this user */
            Result result = new Result();
            result.FarmName = Node.getFarmName();
            result.ClusterName = Node.getClusterName();
            result.NodeName = nodeName;
            result.Module = ModuleName;
            result.time = resultTime;

            for (int i = 0; i < pbsMetric.length; i++) {
                String value = (String) values.get(pbsMetric[i]);
                if (value != null) {
                    if (value.indexOf("kb") > 0) {
                        value = value.substring(0, value.length() - 2);
                        result.addSet(myResTypes[i], Double.parseDouble(value) / factor);
                    } else {
                        result.addSet(myResTypes[i], Double.parseDouble(value));
                    }
                }
            }
            if (firsttime) {
                logger.log(Level.INFO, result.toString());
            }
            results.addElement(result);

            haveResults = true;
        }
        /** create statistical cluster 	*/
        if (statisticsmode && haveResults) {
            Result statisticalResult = new Result();
            statisticalResult.time = resultTime;
            statisticalResult.FarmName = Node.getFarmName();
            statisticalResult.ClusterName = "PN_PBS_Statistics";
            statisticalResult.NodeName = "Statistics";
            statisticalResult.Module = ModuleName;
            statisticalResult.addSet("Total Nodes", totalNodeNo + downNodeNo);
            statisticalResult.addSet("Total Available Nodes", totalNodeNo);
            statisticalResult.addSet("Total Free Nodes", freeNodesNo);
            statisticalResult.addSet("Total Down Nodes", downNodeNo);
            results.add(statisticalResult);
            if (firsttime) {
                logger.log(Level.INFO, statisticalResult.toString());
            }
        }

        return results;
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

        monPN_PBS aa = new monPN_PBS();
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
