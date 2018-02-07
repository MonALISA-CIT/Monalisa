package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.Monitor.monitor.eResult;
import lia.util.ntp.NTPDate;

/**
 * This Module can be used to run a command / script to get the status of
 * one or several services.
 * 
 * It expects that the output of the command looks like this:
 * 
 * ----------------------
 * Service1 	Status		2	Memory		23
 * Service2		Status		4	Memory		238
 * ....
 * SCRIPTRESULT	status	3	Message		error message
 * ----------------------
 * 
 * The first word is the name of the service. Then, on the same line, a 
 * variable number of pairs (parameter name, value). The value can be
 * number or string. If it can be interpreted as number, it will produce
 * a result, otherwise an eResult will be created. Service name, parameters
 * and the values must be TAB sepparated.
 * 
 * The last line must consist of only the word DONE to confirm the normal 
 * termination of the script. Alternatively, you can put something like:
 * SCRIPTRESULT and then some parameters to describe the end status. This kind
 * of line will be considered like a DONE line.
 * 
 * The module can be put in myFarm.conf like this:
 * 
 * *ClusterName{monStatusCmd, localhost, "full command to be executed"}%RunInterval
 * or, if you want to change the default timeout from 120 sec to 300 sec:
 * *ClusterName{monStatusCmd, localhost, "full command to be executed, timeOut=300"}%RunInterval
 * 
 * @author catac
 */
public class monStatusCmd extends cmdExec implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = 4038076491792915211L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monStatusCmd.class.getName());

    private String command = "echo DONE"; // default command; do nothing
    private long timeout = 2 * 60 * 1000;

    String[] resTypes = new String[0];

    public monStatusCmd() {
        super("monStatusCmd");
        info.ResTypes = resTypes;
        isRepetitive = true;
    }

    @Override
    public MonModuleInfo init(MNode Node, String arg) {
        this.Node = Node;
        arg = arg.trim();
        if ((arg.startsWith("\"") || arg.startsWith("'")) && (arg.endsWith("\"") || arg.endsWith("'"))) {
            arg = arg.substring(1, arg.length() - 1).trim();
        }
        int vidx = arg.indexOf(",");
        if (vidx >= 0) {
            command = arg.substring(0, vidx).trim();
            StringTokenizer stk = new StringTokenizer(arg.substring(vidx + 1));
            while (stk.hasMoreTokens()) {
                String paramValue = stk.nextToken().trim();
                int eidx = paramValue.indexOf("=");
                if (eidx >= 0) {
                    String param = paramValue.substring(0, eidx).trim();
                    String value = paramValue.substring(eidx + 1).trim();
                    if (param.equalsIgnoreCase("timeout")) {
                        try {
                            timeout = Long.parseLong(value) * 1000;
                        } catch (NumberFormatException ex) {
                            logger.log(Level.WARNING, "Cannot parse timeout " + value + " keeping default 120 sec.");
                            timeout = 2 * 60 * 1000;
                        }
                    }
                } else {
                    logger.log(Level.WARNING, "Unknown parameter: " + paramValue);
                }
            }
        } else {
            command = arg;
        }

        logger.log(Level.INFO, "Initialised with cmd='" + command + "', timeout=" + (timeout / 1000) + " sec.");
        return info;
    }

    @Override
    public Object doProcess() throws Exception {
        Vector vrez = new Vector(); // the results will be put here
        long startTime = NTPDate.currentTimeMillis();
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Running cmd='" + command + "'");
        }
        BufferedReader br = procOutput(command, timeout);
        if (br == null) {
            logger.log(Level.WARNING, "Cannot run '" + command
                    + "'.\nFor details increase debug level of lia.Monitor.monitor.cmdExec to FINER");
            return vrez;
        }
        boolean doneOK = false; // got to the end of the response
        String errMsg = null;

        Result r = new Result();
        boolean useR = false; // I am going to use this Result
        eResult er = new eResult();
        boolean useER = false; // I am going to use this eResult
        long pTime = NTPDate.currentTimeMillis();

        try {
            String line = null;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0) {
                    continue;
                }
                if (line.equals("DONE")) {
                    doneOK = true;
                    break;
                }
                // at this point it should have at least one token - the service's name
                StringTokenizer stk = new StringTokenizer(line, "\t");
                String service = stk.nextToken().trim();
                if (service.equals("SCRIPTRESULT")) {
                    doneOK = true;
                }
                // prepare to get the list of parameters
                while (true) {
                    String param = null;
                    String valueS = null;
                    while (stk.hasMoreTokens() && (param == null)) {
                        param = stk.nextToken().trim();
                    }
                    if (param == null) {
                        break;
                    }
                    while (stk.hasMoreTokens() && (valueS == null)) {
                        valueS = stk.nextToken().trim();
                    }
                    if (valueS == null) {
                        break;
                    }
                    if ((param.length() == 0) || (valueS.length() == 0)) {
                        continue; // skip empty pairs
                    }
                    // try to see if it's a number
                    try {
                        double value = Double.parseDouble(valueS);
                        useR = true;
                        r.addSet(param, value); // yep, it's a number; add it as Result
                    } catch (NumberFormatException ex) {
                        useER = true;
                        er.addSet(param, valueS); // nope, it's a string; add it as eResult
                    }
                }
                if (useR) {
                    r.FarmName = getFarmName();
                    r.ClusterName = getClusterName();
                    r.NodeName = service;
                    r.Module = TaskName;
                    r.time = pTime;
                    vrez.add(r);
                    r = new Result();
                    useR = false;
                }
                if (useER) {
                    er.FarmName = getFarmName();
                    er.ClusterName = getClusterName();
                    er.NodeName = service;
                    er.Module = TaskName;
                    er.time = pTime;
                    vrez.add(er);
                    er = new eResult();
                    useER = false;
                }
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error reading output from '" + command + "'", ex);
        } finally {
            long runTime = NTPDate.currentTimeMillis() - startTime;
            if (runTime >= timeout) {
                errMsg = "Timeout after " + (runTime / 1000) + " sec. Script terminated.";
                logger.log(Level.WARNING, "Error executing " + command + ". " + errMsg);
            }
            try {
                if (br != null) {
                    br.close();
                }
            } catch (Throwable t) {
            }
        }
        cleanup();
        if (!doneOK) {
            // the command didn't finish with DONE; we may decide to suspend module? (return null)
            //			logger.log(Level.WARNING, "Output didn't end with DONE or SCRIPTRESULT. Data might be incomplete for cmd='"+command+"'");
            if (errMsg != null) {
                r.FarmName = getFarmName();
                r.ClusterName = getClusterName();
                r.NodeName = "SCRIPTRESULT";
                r.Module = TaskName;
                r.time = pTime;
                r.addSet("Status", 1);
                vrez.add(r);
                er.FarmName = getFarmName();
                er.ClusterName = getClusterName();
                er.NodeName = "SCRIPTRESULT";
                er.Module = TaskName;
                er.time = pTime;
                er.addSet("Message", errMsg);
                vrez.add(er);
            }
        }
        return vrez;
    }

    @Override
    public String[] ResTypes() {
        return resTypes;
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    @Override
    public String getOsName() {
        return "Linux";
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("pass as parameters: ClusterName 'command to run'");
            System.exit(1);
        }
        String cluster = args[0];
        String cmd = args[1];
        MFarm f = new MFarm("myFarm");
        MCluster c = new MCluster(cluster, f);
        MNode n = new MNode("localhost", c, f);

        monStatusCmd m = new monStatusCmd();
        m.init(n, cmd);

        try {
            Vector vr = (Vector) m.doProcess();
            System.out.println("Results: " + vr);
        } catch (Exception ex) {
            System.out.println("Failed to run doProcess");
            ex.printStackTrace();
        }
    }
}
