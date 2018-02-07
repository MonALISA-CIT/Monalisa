/**
 * This module obtains information about the status of the nodes from a Condor pool,
 * with the aid of the condor_status command. If "statistics" is given as argument,
 * the module provides statistics about the number of up/down nodes (the number of 
 * down nodes may be inaccurate).
 * If there is an error executing the condor_status command, the module waits for
 * a number of seconds and retries to execute it. The number of seconds to wait can
 * be specified with the "delayIfError" argument (by default it is 20).
 */
package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.net.InetAddress;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;
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

public class monPN_Condor extends cmdExec implements MonitoringModule {
    /**
     * 
     */
    private static final long serialVersionUID = -2783292793831120456L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monPN_Condor.class.getName());

    protected Integer errorCode;

    public MNode Node;
    public MonModuleInfo info;

    static public String ModuleName = "monPN_Condor";
    static public String OsName = "*";
    public boolean isRepetitive = false;

    protected StringBuilder cmdFirstLines = new StringBuilder();

    /** The names of the parameters reported by Condor */
    static protected String[] condorMetrics = { "Cpus", "TotalLoadAvg", "VirtualMemory", "TotalVirtualMemory", "Memory" };

    /** The names above will be "translated" to the Ganglia names: */
    static protected String[] myResTypes = { "NoCPUs", "Load1", "VIRT_MEM_free", "VIRT_MEM_total", "MEM_total" };

    /** The path to the Condor directory. */
    protected String condorLocation = null;
    protected String cmd = null;
    protected String localCmd = null;
    protected boolean environmentSet = false;
    protected boolean haveCommand = true;

    /** Keeps monitoring information for the Condor nodes. The keys are the names
     * of the nodes and the elements are hashtables containing (parameter name,
     * parameter value) pairs. For SMP machines, there are separate nodes in 
     * Condor for each CPU, but we'll keep only one record.
     */
    protected Hashtable condorNodesData = new Hashtable();

    /**
     * Keeps the evidence of the active nodes from the pool. The keys are the 
     * nodes' hostnames and the values are Boolean (true if the node is active,
     * false otherwise).
     */
    protected Hashtable activeNodes = new Hashtable();

    /** Determines is we send results with the number of up/down nodes */
    protected boolean showStatistics = true;

    /** Number of seconds to wait if there was an error executing the Condor
     * command. After waiting, the module retries to execute the command.
     */
    protected int delayIfError = 20;

    /** The names of the central manager daemons that we will query. */
    protected Vector serverNames = new Vector();

    /** Constraints that will be imposed to the condor_status command with the
     * -constraints option.
     */
    protected String condorConstraints = null;

    /** The total number of slots (virtual machines, i.e. CPUs). */
    protected int nTotalSlots = 0;

    /** The total number of nodes (machines). */
    protected int nTotalNodes = 0;

    /** The number of free slots (virtual machines). */
    protected int nUnclaimedSlots = 0;

    /** The number of slots in Owner state. */
    protected int nOwnerSlots = 0;

    /** The number of commands to be executed. */
    protected int nCommands = 0;

    /** The number of commands executed successfully. */
    protected int nSuccessfulCommands = 0;

    /** The time needed to execute the condor_status command. */
    protected long cmdExecTime = 0;

    private double slotsFactor = 1.0;

    /** The name of the farm */
    protected String farmName = null;

    /** status for execution process */
    private int errorCodeParam = 0;

    /** The error code of the module.  */
    protected final int ERROR_CODE_NO = 6;

    protected final long CMD_DELAY = 2 * 60 * 1000;

    protected Hashtable errorState = new Hashtable();

    /** Module status result */
    private Result statusResult;

    public monPN_Condor() {
        isRepetitive = true;
        canSuspend = false;
        /** set all errot status to available */
        for (int i = 0; i <= ERROR_CODE_NO; i++) {
            errorState.put(Integer.valueOf(i), Integer.valueOf(0));
        }
    }

    @Override
    public MonModuleInfo init(MNode Node, String args) {
        String argList[] = new String[] {};
        String serverName = null;
        this.Node = Node;
        info = new MonModuleInfo();
        isRepetitive = true;

        /* check the argument lists */
        if (args != null) {
            argList = args.split("(\\s)*,(\\s)*"); //requires java 1.4
            for (String element : argList) {
                if (element.toLowerCase().indexOf("disablestatistics") != -1) {
                    showStatistics = false;
                    logger.info("Statistics option enabled.");
                    continue;
                }

                if (element.toLowerCase().indexOf("server") != -1) {
                    try {
                        serverName = element.split("(\\s)*=(\\s)*")[1].trim();
                        serverNames.add(serverName);
                    } catch (Throwable t) {
                        logger.log(Level.INFO, " Got exception parsing server option", t);
                        serverName = null;
                    }
                    logger.log(Level.INFO, "Added schedd to query: " + serverName);
                    continue;
                }

                if (element.toLowerCase().startsWith("condorconstraints")) {
                    int poz = element.indexOf('=');
                    String argVal = element.substring(poz + 1);
                    condorConstraints = argVal.trim();
                    logger.log(Level.INFO, "Added Condor constraints: " + condorConstraints);
                    continue;
                }

                if (element.toLowerCase().indexOf("slotsfactor") != -1) {
                    try {
                        String sFactor = element.split("(\\s)*=(\\s)*")[1].trim();
                        slotsFactor = Double.parseDouble(sFactor);
                    } catch (Throwable t) {
                        logger.log(Level.INFO, " Got exception parsing SlotsFactor option", t);
                        slotsFactor = 1.0;
                    }
                    logger.log(Level.INFO, "Slots factor: " + slotsFactor);
                    continue;
                }

                if (element.toLowerCase().indexOf("cansuspend") != -1) {
                    boolean cSusp = false;
                    try {
                        cSusp = Boolean.valueOf(element.split("(\\s)*=(\\s)*")[1].trim()).booleanValue();
                    } catch (Throwable t) {
                        cSusp = false;
                    }
                    canSuspend = cSusp;
                    continue;
                }

                if (element.indexOf("DelayIfError") != -1) {
                    String argval[] = element.split("=");
                    String sDelay = argval[1].trim();
                    try {
                        delayIfError = Integer.parseInt(sDelay);
                        logger.finest("Value for DelayIfError: " + delayIfError);
                    } catch (Exception e) {
                        logger.warning("Invalid parameter value for DelayIfError");
                        delayIfError = 20;
                    }
                    continue;
                }
            } // end for 
        } // end if args

        farmName = Node.farm.name;

        info.ResTypes = myResTypes;
        info.name = ModuleName;
        return info;
    }

    /**
     * Initializes the module taking into account the environment variables.
     * @throws Exception
     */
    void setEnvironment() throws Exception {
        try {
            condorLocation = AppConfig.getGlobalEnvProperty("CONDOR_LOCATION");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Got exception when obtaining env variable: ", e);
        }
        if (condorLocation == null) {
            logger.log(Level.WARNING, "The CONDOR_LOCATION environment variable is not set!");
            throw new Exception("The CONDOR_LOCATION environment variable is not set!");
        }

        /* check if the condor_status executable actually exists */
        cmd = condorLocation + "/bin/condor_status";
        /** make a copy for local command */
        localCmd = cmd;

        File fd = new File(localCmd);
        if (!fd.exists()) {
            haveCommand = false;
            errorCodeParam = VO_Utils.PN_CMD_NOT_EXIST;
            errorCode = Integer.valueOf(errorCodeParam);

            MLLogEvent mlle = new MLLogEvent();
            mlle.logParameters.put("Error Code", errorCode);
            mlle.logParameters.put("Error Message", VO_Utils.pnErrCodes.get(errorCode));
            //setErrorState(errorCodeParam);

            logger.log(Level.WARNING, "[monPN_Condor] The local command " + cmd + " not exist.", new Object[] { mlle });
        } else {
            logger.log(Level.INFO, "[monPN_Condor] The local command: " + cmd);
            haveCommand = true;
            //setErrorState(STATUS_SUCCESS);
        }

        //File fd = new File(cmd);
        //if (!fd.exists()) { 
        //    throw new Exception("The command " + cmd + " not exist.");
        //}

        StringBuilder condorCmd = new StringBuilder();

        String constraints = "";
        if (condorConstraints != null) {
            constraints = " -constraint " + "\\\"" + condorConstraints + "\\\"";
        }

        if (serverNames.size() == 0) {
            condorCmd.append(condorLocation + "/bin/condor_status -l" + constraints);
            condorCmd.append(" && echo ML_PN_OK");
            nCommands++;
        } else {
            for (int ind = 0; ind < serverNames.size(); ind++) {
                if (condorCmd.length() > 0) {
                    condorCmd.append(" ; ");
                }
                condorCmd.append(condorLocation + "/bin/condor_status -l -pool " + serverNames.get(ind) + constraints);
                condorCmd.append(" && echo ML_PN_OK");
                nCommands++;
            }
        }

        cmd = new String(condorCmd);
        logger.log(Level.INFO, "Using Condor location: " + condorLocation);
    }

    @Override
    public String[] ResTypes() {
        return myResTypes;
    }

    @Override
    public String getOsName() {
        return OsName;
    }

    @Override
    public MNode getNode() {
        return Node;
    }

    @Override
    public String getClusterName() {
        return Node.getClusterName();
    }

    @Override
    public String getFarmName() {
        return Node.getFarmName();
    }

    @Override
    public String getTaskName() {
        return ModuleName;
    }

    @Override
    public boolean isRepetitive() {
        return isRepetitive;
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    /**
     * Set not available the errorStatus. All other status will be available.
     * @param errorCode
     */
    private void setErrorState(int errorCode) {
        for (Iterator it = errorState.keySet().iterator(); it.hasNext();) {
            Integer code = (Integer) it.next();
            errorState.put(code, Integer.valueOf(0));
        }
        errorState.put(Integer.valueOf(errorCode), Integer.valueOf(1));
    }

    /**
     * The main function of the module.
     */
    @Override
    public Object doProcess() throws Exception {
        boolean haveException = false;

        if (!environmentSet) {
            setEnvironment();
            environmentSet = true;
        }

        long tStart = System.currentTimeMillis();
        Vector ret = new Vector();

        /*
        File fd = new File(localCmd);
        if (!fd.exists()) { 
        	haveCommand = false;
        	errorCodeParam = VO_Utils.PN_CMD_NOT_EXIST;
        	errorCode = Integer.valueOf(errorCodeParam);
        	
        	MLLogEvent mlle = new MLLogEvent();
        	mlle.logParameters.put("Error Code", errorCode);
        	mlle.logParameters.put("Error Message", VO_Utils.pnErrCodes.get(errorCode));
        	//setErrorState(errorCodeParam);

            logger.log(Level.WARNING, "[monPN_Condor] The command " + localCmd + " not exist.",
            		new Object[]{mlle});
        } else {
        	haveCommand = true;
        	//setErrorState(STATUS_SUCCESS);
        } */

        /** procees the condor_status command output only if we have the condor_status command */
        if (haveCommand) {
            errorCodeParam = VO_Utils.PN_SUCCESS;
            errorCode = Integer.valueOf(errorCodeParam);
            try {
                getCommandOutput();
            } catch (Exception e) {
                // if the command fails the first time, wait for a while and try again 

                /** null or wrong output */
                if (errorCode.intValue() != 0) {
                    errorCodeParam = VO_Utils.PN_NULL_OUTPUT;
                    errorCode = Integer.valueOf(errorCodeParam);
                }

                logger.log(Level.WARNING, "monPN_Condor got exception: ", e);
                logger.log(Level.INFO, "Failed getting nodes status, retrying after " + delayIfError + " s...");

                //setErrorState(errorCodeParam);

                try {
                    Thread.sleep(delayIfError * 1000);
                } catch (Throwable t) {
                }

                try {
                    getCommandOutput();
                } catch (Exception e2) {
                    if (pro != null) {
                        pro.destroy();
                        pro = null;
                    }
                    logger.log(Level.INFO,
                            "Second attempt to get nodes status failed, no results were sent - exception:", e);
                    if (errorCode.intValue() != 0) {
                        errorCodeParam = VO_Utils.PN_SECOND_NULL_OUTPUT;
                        errorCode = Integer.valueOf(errorCodeParam);
                    }

                    MLLogEvent mlle2 = new MLLogEvent();
                    mlle2.logParameters.put("Error Code", errorCode);
                    mlle2.logParameters.put("Error Message", VO_Utils.pnErrCodes.get(errorCode));
                    mlle2.logParameters.put("First lines from the command output", cmdFirstLines.toString());
                    logger.log(Level.INFO, "monPN_Condor got exception", new Object[] { mlle2, e2 });
                    //setErrorState(errorCodeParam);

                    haveException = true;
                    //throw e2;
                }
            }

            /* generate the results based on the condorNodesData hashtable */
            if (!haveException) {
                ret = createResults();
            }
        }

        long tEnd = System.currentTimeMillis();
        long totalProcessingTime = tEnd - tStart;

        /** create the status result */
        statusResult = new Result();
        statusResult.time = NTPDate.currentTimeMillis();
        statusResult.NodeName = "Status";
        statusResult.ClusterName = "PN_Condor_Statistics";
        statusResult.FarmName = Node.getFarmName();
        statusResult.Module = ModuleName;
        statusResult.addSet("PN_Condor_Status", errorCodeParam);
        statusResult.addSet("CmdExecTime", cmdExecTime);
        statusResult.addSet("TotalProcessingTime", totalProcessingTime);

        /** add it to module results */
        ret.add(statusResult);

        logger.log(Level.INFO, "Sent " + ret.size() + " results. " + "Execution time: " + totalProcessingTime + " ms. "
                + "Status = " + errorCodeParam + " (" + VO_Utils.pnErrCodes.get(Integer.valueOf(errorCodeParam)) + ")");
        logger.log(Level.INFO, "[monPN_Condor] execution time for doProcess(): " + totalProcessingTime + " ms");

        return ret;
    }

    /**
     * Executes the condor_status command and parses its output. The information 
     * obtained is stored in the condorNodesData hashtable. 
     * @throws Exception If there was an error executing the condor_status command 
     */
    void getCommandOutput() throws Exception {
        /* execute the condor_status command */
        long t1, t2;
        t1 = System.currentTimeMillis();
        try {
            BufferedReader buffer = procOutput(cmd, CMD_DELAY);
            cmdExecTime = System.currentTimeMillis() - t1;

            if (buffer == null) {
                this.errorCode = Integer.valueOf(VO_Utils.PN_NULL_OUTPUT);
                throw new Exception("No output for the condor_status command");
            }

            /* reset the information about the active nodes */
            Enumeration akeys = this.activeNodes.keys();
            while (akeys.hasMoreElements()) {
                String hostname = (String) akeys.nextElement();
                this.activeNodes.put(hostname, new Boolean(false));
            }

            /* clear the old data */
            this.condorNodesData = new Hashtable();
            /* reset the node counters */
            this.nTotalSlots = this.nUnclaimedSlots = this.nOwnerSlots = 0;
            this.nTotalNodes = 0;

            /* fill condorNodesData with information obtained from Condor */
            this.nSuccessfulCommands = 0;
            parseCondorOutput(buffer);
        } catch (Exception e) {
            t2 = System.currentTimeMillis();
            if ((t2 - t1) >= CMD_DELAY) {
                this.errorCode = Integer.valueOf(VO_Utils.PN_COMMAND_TIMEOUT);
            }

            throw e;
        }
    }

    /**
     * Parses the output of the "condor_status -l" command and fills the
     * condorNodesData hashtable with the results.
     * @param buff Buffer containing the output of the command.
     * @throws Exception
     */
    public void parseCondorOutput(BufferedReader buff) throws Exception {
        /* buffer in which we gather the information about the current job */
        StringBuilder sb = new StringBuilder();

        boolean haveNewRecord = false;
        boolean haveErrorOutput = false; // shows if the command executed successfully

        cmdFirstLines = new StringBuilder();
        int lineCnt = 0;

        try {
            /* the information for a node is on multiple lines */
            for (String lin = buff.readLine(); lin != null; lin = buff.readLine()) {
                if (lineCnt < 10) {
                    cmdFirstLines.append(lin + "\n");
                }
                lineCnt++;

                if (lin.startsWith("MyType") || (lin.indexOf("Machine") >= 0)) {

                    //haveErrorOutput = false;
                    haveNewRecord = true;
                }

                if (lin.indexOf("ML_PN_OK") >= 0) {
                    nSuccessfulCommands++;
                    continue;
                }

                if ((lin.length() != 0) && !lin.startsWith("MyType")) {
                    /* we are in the middle of a condor_status record */
                    sb.append(lin + "\n");
                    //haveNewRecord = true;
                } else if ((lin.length() == 0) && haveNewRecord && !haveErrorOutput) {
                    /* the record for a node is finished, parse it */
                    haveNewRecord = false;
                    Hashtable nodeData = parseStatusRecord(sb);

                    /* mark the machine as active (the "Machine" attribute is
                     * the node's hostname) */
                    String hostname = (String) nodeData.get("Machine");
                    activeNodes.put(hostname, new Boolean(true));

                    /* the "Name" attribute is the name of the virtual machine
                     * (on a SMP machine there can be multiple virtual machines)
                     */
                    //String vmName = (String)nodeData.get("Name");

                    Hashtable oldNodeData = (Hashtable) condorNodesData.get(hostname);
                    if (oldNodeData == null) {
                        condorNodesData.put(hostname, nodeData);
                    } else {
                        addVMInfo(nodeData, oldNodeData);
                        condorNodesData.put(hostname, oldNodeData);
                    }

                    /* clear the buffer to prepare it for the next record */
                    sb = new StringBuilder("");
                } // end of if

            } // end of for      

            if (this.nSuccessfulCommands < this.nCommands) {
                throw new Exception("The condor_status command returned error! ");
            }

            if (haveErrorOutput) {
                //logger.log(Level.WARNING, "The condor_status command returned error!");
                throw new Exception("Error processing the output of the condor_status command");
            }

        } catch (Exception t) {
            //t.printStackTrace();
            errorCodeParam = VO_Utils.PN_PARSE_ERROR;
            this.errorCode = Integer.valueOf(errorCodeParam);
            if (this.nSuccessfulCommands < this.nCommands) {
                throw new Exception("The condor_status command returned error! - " + nSuccessfulCommands
                        + " commands of " + nCommands + "executed successfully");
            } else {
                throw t;
            }
        } // end try/catch
    }

    /**
     * Parses a record from the output of the "condor_status -l" command, containing
     * information about a single node.
     * @param rec String that contains the lines of the record.
     * @return A hashtable containing parameter names as keys and parameter values as
     * elements.
     */
    Hashtable parseStatusRecord(StringBuilder rec) {
        Hashtable nodeInfo = new Hashtable();
        String line;
        String sRec = new String(rec);
        StringTokenizer st = new StringTokenizer(sRec, "\n");

        /* process each line from the record */
        while (st.hasMoreTokens()) {
            line = st.nextToken();

            StringTokenizer lst = new StringTokenizer(line, " =");
            if (!lst.hasMoreTokens()) {
                continue;
            }
            String paramName = lst.nextToken();
            if (!lst.hasMoreTokens()) {
                continue;
            }
            String paramValue = lst.nextToken();

            if (paramName.equals("Name") || paramName.equals("Machine")) {
                String nameWithoutQuotes = paramValue.replaceAll("\"", "");
                nodeInfo.put(paramName, nameWithoutQuotes);
                continue;
            }

            if (paramName.equals("State")) {
                String nameWithoutQuotes = paramValue.replaceAll("\"", "");
                this.nTotalSlots++;
                if (nameWithoutQuotes.equals("Owner")) {
                    this.nOwnerSlots++;
                }
                if (nameWithoutQuotes.equals("Unclaimed")) {
                    this.nUnclaimedSlots++;
                }

            }

            if (paramName.equals("Cpus") || paramName.equals("TotalLoadAvg")) {
                double dval = Double.parseDouble(paramValue);
                nodeInfo.put(paramName, Double.valueOf(dval));
                continue;
            }

            if (paramName.equals("VirtualMemory") || paramName.equals("TotalVirtualMemory")
                    || paramName.equals("Memory")) {
                double dval = Double.parseDouble(paramValue);
                nodeInfo.put(paramName, Double.valueOf(dval));
                continue;
            }
        }

        return nodeInfo;
    }

    void addVMInfo(Hashtable nodeData, Hashtable oldNodeData) {
        Enumeration hkeys = nodeData.keys();

        while (hkeys.hasMoreElements()) {
            String paramName = (String) hkeys.nextElement();

            if (paramName.equals("Cpus") || paramName.equals("VirtualMemory") || paramName.equals("Memory")
                    || paramName.equals("TotalVirtualMemory")) {
                double oldval = ((Double) oldNodeData.get(paramName)).doubleValue();
                double val = ((Double) nodeData.get(paramName)).doubleValue();
                oldNodeData.put(paramName, Double.valueOf(val + oldval));
            }
        }
    }

    /**
     * Creates a Vector with Results from the contents of the condorNodesData
     * hashtable. A result will be created for each node.
     */
    public Vector createResults() {
        long cTime = NTPDate.currentTimeMillis();

        Vector results = new Vector();

        Enumeration ckeys = condorNodesData.keys();
        /* for each node */
        while (ckeys.hasMoreElements()) {
            String vmName = (String) ckeys.nextElement();

            /* initialize a Result */
            Result r = new Result();
            r.ClusterName = getClusterName();
            r.FarmName = getFarmName();
            r.NodeName = vmName;
            r.time = cTime;
            r.Module = ModuleName;

            /* add the parameter values to the result */
            Hashtable nodeData = (Hashtable) condorNodesData.get(vmName);
            Enumeration nkeys = nodeData.keys();
            while (nkeys.hasMoreElements()) {
                String paramName = (String) nkeys.nextElement();
                if (!paramName.equals("Name") && !paramName.equals("Machine")) {
                    Double paramValue = (Double) nodeData.get(paramName);
                    //double d = Double.parseDouble(paramValue);
                    double d = paramValue.doubleValue();
                    if (paramName.equals("VirtualMemory") || paramName.equals("TotalVirtualMemory")) {
                        /* these are given in KB and we transmit them in MB */
                        d /= 1024.0;
                    }
                    int idx = getIndex(condorMetrics, paramName);
                    if (idx >= 0) {
                        r.addSet(myResTypes[idx], d);
                    } else {
                        logger.log(Level.FINEST, "Unsupported Condor parameter: " + paramName);
                    }
                }
            }
            results.add(r);
        }

        if (showStatistics) {
            /* construct the Result with statistics about the active nodes */
            Result rs = new Result();
            rs.ClusterName = "PN_Condor_Statistics";
            rs.FarmName = getFarmName();
            rs.NodeName = "Statistics";
            rs.time = cTime;
            rs.Module = ModuleName;

            //double nNodes = activeNodes.size();
            //rs.addSet("Total Nodes", nNodes);
            /* count the active nodes */
            /*
            Enumeration aelems = activeNodes.elements();
            double nActiveNodes = 0;
            while(aelems.hasMoreElements()) {
            	Boolean b = (Boolean)aelems.nextElement();
            	if (b.booleanValue() == true)
            		nActiveNodes++;
            }
            */
            /*
            rs.addSet("Total Available Nodes", nActiveNodes);
            rs.addSet("Total Down Nodes", nTotalNodes - nActiveNodes);
            rs.addSet("Nodes Available for Condor", nActiveNodes - nOwnerNodes);
            rs.addSet("Total Free Nodes", nUnclaimedNodes);
            */
            /*
            rs.addSet("Total Nodes", nNodes);
            rs.addSet("Total Nodes", nTotalNodes);
            rs.addSet("Total Available Nodes", nTotalNodes - nOwnerNodes);
            rs.addSet("Total Owner Nodes", nOwnerNodes);			
            rs.addSet("Total Free Nodes", nUnclaimedNodes);
            */
            this.nTotalNodes = condorNodesData.size();
            rs.addSet("Total Nodes", nTotalNodes);
            rs.addSet("Total Slots", nTotalSlots);
            int iCpus = (int) (nTotalSlots * slotsFactor);
            rs.addSet("Total CPUs", iCpus);
            rs.addSet("Total Available Slots", nTotalSlots - nOwnerSlots);
            rs.addSet("Total Owner Slots", nOwnerSlots);
            rs.addSet("Total Free Slots", nUnclaimedSlots);

            results.add(rs);
        }

        StringBuilder sb = new StringBuilder();
        for (int vi = 0; vi < results.size(); vi++) {
            sb.append(" [ " + vi + " ] = " + results.elementAt(vi) + "\n");
        }
        logger.log(Level.FINEST, "Got Results: " + sb);

        return results;
    }

    /**
     * Finds the index of an element in an array of Strings. 
     * @param tab The array of Strings.
     * @param elem The element to be found.
     * @return The index of the element, or -1 if it is not found.
     */
    int getIndex(String[] tab, String elem) {
        int ret = -1;

        for (int i = 0; i < tab.length; i++) {
            if (tab[i].equals(elem)) {
                ret = i;
                break;
            }
        }
        return ret;
    }

    /**
     * Used to test the module outside MonALISA.
     */
    static public void main(String[] args) {
        System.out.println("args[0]: " + args[0]);
        String host = args[0];
        monPN_Condor aa = null;
        String ad = null;

        try {
            System.out.println("...instantiating PN_Condor");
            aa = new monPN_Condor();
        } catch (Exception e) {
            System.out.println(" Cannot instantiate PN_Condor:" + e);
            System.exit(-1);
        } // end try/catch

        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Cannot get ip for node " + e);
            System.exit(-1);
        } // end try/catch

        System.out.println("...running init method ");
        //MonModuleInfo info = aa.init( new MNode (args[0] ,ad,  null, null), arg);
        MonModuleInfo info = aa.init(new MNode(args[0], ad, null, null), "");

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

        System.out.println("PN_Condor Testing Complete");
        System.exit(0);
    } // end main
}
