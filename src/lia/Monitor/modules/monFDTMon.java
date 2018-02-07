package lia.Monitor.modules;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.util.DynamicThreadPoll.SchJob;
import lia.util.fdt.FDTListener;
import lia.util.fdt.xdr.LisaXDRModule;
import lia.util.fdt.xdr.XDRMessage;
import lia.util.ntp.NTPDate;

/**
 * Module used to report information about a FDT Server and several clients.
 * 
 * The module was designed to received monitoring information from the FDT instances
 * started through lia.app.transfer.AppTransfer -> FDTProtocol, but can be used to receive
 * info from FDT when started by hand with -lisafdtclient/-localfdtserver localhost:port.
 * 
 * The module accepts as parameter the "port" to listen to monitoring connections from FDT:
 * ^monFDTMon{ParamTimeout=300,NodeTimeout=300,ClusterTimeout=300,port=11002}%5
 * Default port is 11001.
 * 
 * @author catac, based on monFDTClient and monFDTServer by AdiM.
 */
public class monFDTMon extends SchJob implements LisaXDRModule, MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = -5537104043321607094L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monFDTMon.class.getName());

    static protected String OsName = "*";
    protected MNode Node;
    protected MonModuleInfo info;
    private final Properties pModuleConfiguration = new Properties();
    private final String[] sResTypes = new String[0]; // dynamic
    static public String ModuleName = "monFDTMon";
    Vector bufferedResults = new Vector();

    public monFDTMon() {
        // empty
    }

    /**
     * Init the module info and parse the arguments
     */
    @Override
    public MonModuleInfo init(MNode node, String args) {
        this.Node = node;
        info = new MonModuleInfo();
        try {
            init_args(args);
            info.setState(0);
        } catch (Exception e) {
            info.setState(1);// error
        }
        info.ResTypes = sResTypes;
        info.setName(ModuleName);
        int lisaFdtPort = 11001;
        try {
            lisaFdtPort = Integer.parseInt(pModuleConfiguration.getProperty("port", "11001"));
        } catch (NumberFormatException nfe) {
            logger.warning("Failed to parse the listen port: " + pModuleConfiguration.getProperty("port")
                    + ". Assuming default (11001)");
        }
        // start (if is not started already) an FDT monitoring server and register ourselves as listener for FDT commands
        logger.log(Level.FINE, "Initializing " + ModuleName + " to listen for fdt xdr connections on port "
                + lisaFdtPort);
        FDTListener.getInstance(lisaFdtPort).instanceStarted("FDTClient", this);
        FDTListener.getInstance(lisaFdtPort).instanceStarted("FDTServer", this);
        return info;
    }

    /**
     * Parse module configuration : monduleName{params}%30 params: params: semicolon separated list of key=value
     */
    protected void init_args(String list) throws Exception {
        String[] splittedArgs = list.split("(\\s)*(;|,)+(\\s)*");
        for (String splittedArg : splittedArgs) {
            String[] aKeyValue = splittedArg.split("(\\s)*=(\\s)*");
            if (aKeyValue.length != 2) {
                continue;
            }
            pModuleConfiguration.put(aKeyValue[0], aKeyValue[1]);
        }
    }

    @Override
    public boolean isRepetitive() {
        return true;
    }

    @Override
    public String[] ResTypes() {
        return sResTypes;
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
    public MonModuleInfo getInfo() {
        return info;
    }

    @Override
    public String getOsName() {
        return OsName;
    }

    @Override
    public String getTaskName() {
        return info.getName();
    }

    /**
     * @return the Name used by FDTServer to report statistics 
     */
    protected String getName() {
        String name = AppConfig.getProperty("MonALISA_ServiceName");
        if (name == null) {
            try {
                name = InetAddress.getLocalHost().toString();
            } catch (Throwable e) {
                name = "localhost";
            }
        }
        return name;
    }

    /**
     * Get the buffered Results and clear the internal buffer for the next round
     * 
     * @return a collection with the current Results
     */
    public Collection getBufferedResults() {
        if ((bufferedResults == null) || (bufferedResults.size() == 0)) {
            return null;
        }
        Vector rV = null;
        synchronized (bufferedResults) {
            rV = new Vector(bufferedResults.size());
            rV.addAll(bufferedResults);
            bufferedResults.clear();
        }
        return rV;
    }

    /** 
     * Add a result to the internal buffer
     * @param r The Result to be added. 
     */
    public void addToBufferedResults(Result r) {
        if (bufferedResults == null) {
            return;
        }
        bufferedResults.add(r);
    }

    @Override
    public List getCommandSet() {
        List lCommandSet = new LinkedList();
        lCommandSet.add("monitorTransfer transfer_id parameter value\n");
        return lCommandSet;
    }

    @Override
    public String getCommandUsage(String command) {
        if (command.equalsIgnoreCase("startTransfer")) {
            return "startTransfer IPsrc IPdst filesz \n\t IPsrc: source address \n\t IPdst: dest address\n\t filesz: size of file to transfer \n\t Request performance improvements for data transfer";
        } else if (command.equalsIgnoreCase("endTransfer")) {
            return "endTransfer: \n\t Request reverting the settings";
        } else {
            return "Unknown command";
        }
    }

    @Override
    public Object doProcess() throws Exception {
        // return the cache monitoring information received from FDT
        Collection vResults = getBufferedResults();
        if (logger.isLoggable(Level.FINER)) {
            logger.finer("Collecting FDT results: " + vResults);
        }
        return vResults;
    }

    @Override
    public XDRMessage execCommand(String module, String command, List args) {
        final long rightNow = NTPDate.currentTimeMillis();
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "[FDTMon] New XDR command received. Module: " + module + " Cmd: " + command
                    + " Args:" + args);
        }
        try {
            XDRMessage xdrOutput = new XDRMessage();
            if (command.equalsIgnoreCase("monitorTransfer")) {
                String key = ((String) args.get(0)).trim();
                if (args.size() < 3) {
                    logger.log(Level.WARNING, "[FDTMon] Invalid monitorTransfer parameters");
                    xdrOutput.payload = "Invalid monitorTransfer parameters";
                    xdrOutput.status = XDRMessage.ERROR;
                } else {
                    try {
                        String clusterName = null;
                        String nodeName = null;
                        if (module.equals("FDTClient")) {
                            // FDT_MON data is sent by the client, but with the FDTServer module name
                            clusterName = "FDT_Transfers";
                            nodeName = key;
                        } else if (module.equals("FDTServer")) {
                            if ("FDT_PARAMS".equalsIgnoreCase(key)) {
                                clusterName = "FDT_Server";
                                nodeName = getName();
                            } else if (key.startsWith("FDT_MON")) {
                                clusterName = "FDT_MON";
                                nodeName = getName() + key.substring("FDT_MON".length());
                            } else {
                                clusterName = "FDT_Server_Clients";
                                nodeName = key;
                            }
                        }
                        Vector paramNames = new Vector();
                        Vector paramValues = new Vector();
                        String sName, sValue;
                        for (int j = 1; j < (args.size() - 1); j += 2) {
                            try {
                                sName = ((String) args.get(j)).trim();
                                sValue = ((String) args.get(j + 1)).trim();
                                paramNames.addElement(sName);
                                paramValues.addElement(Double.valueOf(sValue));
                            } catch (Exception e) {
                                logger.log(Level.WARNING,
                                        "[FDTMon] [skipped] Invalid mon value. Cause: " + e.getMessage());
                            }
                        }
                        String[] aParamNames = (String[]) paramNames.toArray(new String[paramNames.size()]);
                        double[] aParamValues = new double[paramValues.size()];
                        int i = 0;
                        for (Iterator iterator = paramValues.iterator(); iterator.hasNext(); i++) {
                            aParamValues[i] = ((Double) iterator.next()).doubleValue();
                        }
                        if ((clusterName != null) && (nodeName != null)) {
                            Result rResult = new Result(Node.getFarmName(), clusterName, nodeName, ModuleName,
                                    aParamNames, aParamValues);
                            rResult.time = rightNow;
                            addToBufferedResults(rResult);
                            if (logger.isLoggable(Level.FINE)) {
                                logger.log(Level.FINE, "[FDTMon] Monitoring values published: {0} {1} {2} {3}",
                                        new Object[] { module, key, paramNames, paramValues });
                            }
                        } else {
                            logger.warning("Couldn't set cluster and Node name for the result. Not sending it!");
                        }
                        xdrOutput.payload = "OK";
                        xdrOutput.status = XDRMessage.SUCCESS;
                    } catch (NumberFormatException e) {
                        logger.warning("Wrong parameter:" + args.get(2) + ".  integer/float/double values required");
                        xdrOutput.payload = "Wrong parameter.  integer/float/double values required";
                        xdrOutput.status = XDRMessage.ERROR;
                    }
                }
            } else {
                logger.warning("\nUnknown request: " + command);
                xdrOutput.payload = "Unknwon request";
                xdrOutput.status = XDRMessage.ERROR;
            }
            return xdrOutput;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Cannot execute command " + command + "\n Cause: ", t);
            return XDRMessage.getErrorMessage("Cannot execute command " + command + "\n Cause: " + t.getMessage());
        }
    }
}
