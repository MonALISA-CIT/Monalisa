package lia.Monitor.Farm.Transfer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ShutdownReceiver;
import lia.util.ShutdownManager;
import lia.util.fdt.FDTListener;
import lia.util.fdt.xdr.LisaXDRModule;
import lia.util.fdt.xdr.XDRMessage;
import lia.util.threads.MonALISAExecutors;

/**
 * Support for handling the FDT Transfer Protocol. This class manages a FDT server and
 * several clients which are controlled through the start/stopInstance methods.
 * 
 * The monitoring information about the effective transfers will be received by monFDTMon
 * which has to be present in ML service's configuration.
 * 
 * Configuration parameters:
 * fdt.path.fdt.jar	- path to fdt.jar
 * fdt.java.bin - path to the java executable
 * fdt.server.start - true/false - whether to start or not the FDT server
 * fdt.server.port - the port on which the FDT server will be listening
 * fdt.server.options - options to pass directly to the FDT server
 * fdt.server.lisaFdtPort - port on which the monFDTMon module is configured to listen
 * fdt.basedir - start the FDT server and the client instances in this directory
 * 
 * @author catac
 */
public class FDTProtocol extends TransferProtocol implements ShutdownReceiver {

    /** Logger used by this class */
    private final static Logger logger = Logger.getLogger(FDTProtocol.class.getName());

    /** Directory where FDT logs will be stored */
    public static String fdtLogDir = AppConfig.getProperty("lia.Monitor.Farm.HOME") + "/fdt_logs";

    static {
        try {
            File logdir = new File(fdtLogDir);
            logdir.mkdir();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to create the FDT log directory [ " + fdtLogDir + " ]", ex);
        }
    }

    /** Remove FDT logs after this interval, if they are not alive anymore */
    public static long fdtLogsTTL = AppConfig.getl("lia.Monitor.Farm.Transfer.FDT.logsTTL", 3600) * 1000;

    /** Path to java executable */
    public static String sJavaBin;

    /** Path to fdt.jar */
    public static String sPathFDT;

    /** The server instance, or null if server not started. */
    private FDTInstance serverInstance;

    /** FDT server's options */
    public static String serverOptions;

    /** FDT server and client's base directory */
    public static String fdtBasedir;

    /** Port on which the FDT server listens for client connections */
    public static int serverPort;

    /** Port on which the monFDTMon listens for monitoring information */
    public static int lisaFdtPort;

    /** The amount = times(start&server=true) - times(stop&server=true) were called */
    private final AtomicInteger serverUsage;

    /** Handler for the log cleaner task */
    private final ScheduledFuture sfLogCleaner;

    /** Handler for the LISA XDR module commands */
    private final XDRCommandHandler xdrCommandHandler;

    /** 
     * Helper to clean old and unused anymore log files.
     * Logs are deleted if older than lia.Monitor.Farm.Transfer.FDT.logsTTL interval.
     */
    class LogCleaner implements Runnable {
        @Override
        public void run() {
            try {
                if (logger.isLoggable(Level.FINER)) {
                    logger.finer("FDT LogCleaner started... checking for old logs to remove.");
                }
                File dir = new File(fdtLogDir);
                if (dir.isDirectory()) {
                    File[] files = dir.listFiles();
                    long now = System.currentTimeMillis(); // we need system's time here!
                    for (File file : files) {
                        if ((now - file.lastModified()) > fdtLogsTTL) {
                            // file older than timeout. Check if there's still a FDT instance pointing to it
                            // (it could be a server that didn't transfer anything for this interval)
                            String fName = file.getName();
                            int len = fName.length();
                            if (len > 8) { // len > "fdt_.log".length()
                                String id = fName.substring(4, len - 4);
                                if (!htInstances.containsKey(id)) {
                                    // there is no such instance running. Cleanup the log
                                    logger.info("Cleaning up FDT logfile " + fName);
                                    file.delete();
                                }
                            }
                        }
                    }
                } else {
                    logger.warning("Cannot find FDT log directory in [ " + fdtLogDir + " ]. Trying to create it.");
                    File logdir = new File(fdtLogDir);
                    logdir.mkdir();
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error while checking FDT logs", t);
            }
        }
    }

    /**
     * Initialize the FDT Transfer Protocol.
     * @param appTransfer the AppTransfer that created this protocol.
     */
    public FDTProtocol() {
        super("fdt");
        serverOptions = "";
        fdtBasedir = "";
        serverPort = 54321;
        serverInstance = null;
        lisaFdtPort = 11002;
        serverUsage = new AtomicInteger(0);
        xdrCommandHandler = new XDRCommandHandler();
        // we still need to register as a shutdown listener because we run FDT
        // with no time limit and therefore it will not be added to the process
        // watchdog's list
        ShutdownManager.getInstance().addModule(this);
        sfLogCleaner = MonALISAExecutors.getMLHelperExecutor().scheduleWithFixedDelay(new LogCleaner(), 1, 1,
                TimeUnit.MINUTES);
    }

    @Override
    public String startInstance(Properties props) {
        StringBuilder sbRes = new StringBuilder("-ERR Failed to start ");
        if (Boolean.valueOf(props.getProperty("server", "false")).booleanValue()) {
            handleServerUsage(true);
            if (serverInstance == null) {
                sbRes.append("server. Check ML service logs.");
            } else {
                sbRes.setLength(0);
                sbRes.append("+OK Server active on port\n").append(serverPort);
            }
            return sbRes.toString();
        }
        sbRes.append("transfer. ");
        String transferID = props.getProperty("requestID");
        String destIP = props.getProperty("destIP");
        String files = props.getProperty("files");
        String fdtOptions = props.getProperty("fdtOptions", "");
        String maxSpeed = props.getProperty("bandwidth");
        String period = props.getProperty("period");

        boolean mem2mem = Boolean.valueOf(props.getProperty("mem2mem", "false")).booleanValue();

        long lMaxSpeed = -1;
        if (maxSpeed != null) {
            lMaxSpeed = TransferUtils.bitsToBytes(TransferUtils.parseBKMGps(maxSpeed));
        }
        long lPeriod = -1;
        try {
            lPeriod = Long.parseLong(period);
        } catch (NumberFormatException nfe) {
            sbRes.append("value for period is not a number: ").append(period);
        }
        if (transferID == null) {
            sbRes.append("requestID missing");
        } else if (destIP == null) {
            sbRes.append("destIP missing");
        } else if ((files == null) && (mem2mem == false)) {
            sbRes.append("files or mem2mem missing or mem2mem not set to true");
        } else if (mem2mem && (lPeriod < 0)) {
            sbRes.append("the period argument is mandatory for mem2mem requests");
        } else if (htInstances.get(transferID) != null) {
            sbRes.append("transfer ").append(transferID).append(" already started!");
        } else {
            FDTInstance fdtInstance = new FDTInstance(transferID, destIP, fdtOptions, lMaxSpeed, files, lPeriod);
            if (fdtInstance.start()) {
                htInstances.put(transferID, fdtInstance);
                sbRes.setLength(0); // reset the response buffer
                sbRes.append("+OK Transfer ").append(transferID).append(" started");
            } else {
                sbRes.append("Cannot start transfer. See service logs.");
            }
        }
        return sbRes.toString();
    }

    @Override
    public String stopInstance(Properties props) {
        StringBuilder sbRes = new StringBuilder("-ERR Failed to stop transfer. ");
        if (Boolean.valueOf(props.getProperty("server", "false")).booleanValue()) {
            handleServerUsage(false);
            sbRes.setLength(0);
            sbRes.append("+OK Server status: ");
            sbRes.append(serverInstance != null ? "active" : "inactive");
            sbRes.append(" usage=").append(serverUsage.get());
            return sbRes.toString();
        }
        String transferID = props.getProperty("requestID");
        if (transferID == null) {
            sbRes.append("requestID missing");
        } else if (htInstances.get(transferID) == null) {
            sbRes.append("transfer ").append(transferID).append(" not existing!");
        } else {
            FDTInstance fdtInstance = (FDTInstance) htInstances.get(transferID);
            if (fdtInstance.stop()) {
                sbRes.setLength(0);
                sbRes.append("+OK Transfer ").append(transferID).append(" stopped.");
            } else {
                sbRes.append("Cannot stop transfer. See service logs.");
            }
        }
        return sbRes.toString();
    }

    /** 
     * Handle a start&server=true or stop&server=true command: modify the server usage accordingly 
     * and then simulate a configuration update, to start it if necessary.
     */
    private void handleServerUsage(boolean active) {
        if (serverUsage.addAndGet(active ? 1 : -1) < 0) {
            serverUsage.set(0);
        }
        updateConfig();
    }

    @Override
    public void updateConfig() {
        sPathFDT = config.getProperty("path.fdt.jar", AppConfig.getProperty("MonaLisa_HOME") + "/Service/lib/fdt.jar");
        sJavaBin = config.getProperty("java.bin", AppConfig.getProperty("java.home") + "/bin/java");
        boolean fdtServer = Boolean.valueOf(config.getProperty("server.start", "false")).booleanValue()
                || (serverUsage.get() > 0);
        String fdtOptions = config.getProperty("server.options", "");
        String basedir = config.getProperty("basedir", ".");
        int fdtPort = 54321;
        try {
            fdtPort = Integer.parseInt(config.getProperty("server.port", "54321"));
        } catch (NumberFormatException nfe) {
            logger.log(Level.WARNING, "Filed to parse fdt.server.port. Using default: " + fdtPort);
        }
        int fdtLisaPort = 11002;
        try {
            fdtLisaPort = Integer.parseInt(config.getProperty("server.lisaFdtPort", "11002"));
        } catch (NumberFormatException nfe) {
            logger.log(Level.WARNING, "Failed to parse server.lisaFdtPort. Using the default: " + fdtLisaPort);
        }
        FDTListener.getInstance(fdtLisaPort).instanceStarted("FDTClientController", xdrCommandHandler);
        // If either start.server differs from the current server status or its options have changed 
        if ((fdtServer != (serverInstance != null)) || (!serverOptions.equals(fdtOptions))
                || (!fdtBasedir.equals(basedir)) || (serverPort != fdtPort) || (lisaFdtPort != fdtLisaPort)) {
            final String serverName = "server";
            logger.fine("FDT server status/options changed.");
            // stop the current server, if one is running
            if (serverInstance != null) {
                serverInstance.stop();
                serverInstance.checkStatus(new ArrayList());
                htInstances.remove(serverName);
                serverInstance = null;
            }
            lisaFdtPort = fdtLisaPort;
            serverPort = fdtPort;
            serverOptions = fdtOptions;
            fdtBasedir = basedir;
            // and start a new one if the case
            if (fdtServer) {
                serverInstance = new FDTInstance(serverName, serverOptions);
                if (serverInstance.start()) {
                    htInstances.put(serverName, serverInstance);
                } else {
                    htInstances.remove(serverName);
                    serverInstance = null;
                }
            }
        }
    }

    /** ML is shutting down! Stop all running instances */
    @Override
    public void Shutdown() {
        shutdownProtocol();
    }

    /** Overrides the shutdownProtocol from TransferProtocol **/
    @Override
    public void shutdownProtocol() {
        super.shutdownProtocol();
        sfLogCleaner.cancel(false);
    }

    /** Add support for the keepAlive command in this protocol */
    @Override
    public String execCommand(String sCmd, Properties props) {
        if (sCmd.equals("setRunParams")) {
            StringBuilder sbRes = new StringBuilder("-ERR Failed to setRunParams to a FDT request. ");
            String requestID = props.getProperty("requestID");
            String maxSpeed = props.getProperty("bandwidth");

            if (requestID == null) {
                sbRes.append("requestID is missing.");
            } else {
                FDTInstance fdtInst = (FDTInstance) htInstances.get(requestID);
                if (fdtInst == null) {
                    sbRes.append("request with ID " + requestID + " is not running.");
                } else {
                    sbRes.setLength(0);
                    sbRes.append("+OK");
                    if (maxSpeed != null) {
                        long lMaxSpeed = TransferUtils.bitsToBytes(TransferUtils.parseBKMGps(maxSpeed));
                        fdtInst.setMaxSpeed(lMaxSpeed);
                        sbRes.append(" bandwidth set");
                    }
                    // other params may be added here
                }
            }
            return sbRes.toString();
        }
        return super.execCommand(sCmd, props);
    }

    @Override
    public String getProtocolUsage() {
        StringBuilder sb = new StringBuilder("FDTProtocol:\n");
        sb.append("fdt start&requestID=string&destIP=host_or_ip&(files=string|mem2mem=true)[&fdtOptions=string][&bandwidth=number][&period=number]\n");
        sb.append("\tstart a new transfer, with the given parameters\n");
        sb.append("fdt setRunParams&requestID=string[&bandwidth=number]\n");
        sb.append("\tset parameters for a running FDT client\n");
        sb.append("fdt stop&requestID=string\n");
        sb.append("\tstop immediately the transfer with the given requestID\n");
        sb.append("fdt start&server=true\n");
        sb.append("\tstart the FDT server, if not started already. This returns the port on which the server listens\n");
        sb.append("fdt stop&server=true\n");
        sb.append("\tstop the FDT server, if needed (not configured to run AND no other clients need it)");
        sb.append("fdt help\n");
        sb.append("\treturn this help\n");
        sb.append("Parameters:\n");
        sb.append("\trequestID\t-a string representing the request ID\n");
        sb.append("\tdestIP\t-host or IP of the machine to which the files should be transferred\n");
        sb.append("\tfiles\t-space sepparated list of files to be transferred\n");
        sb.append("\tmem2mem\t-if this is present instead of the files property, a memory to memory test will be performed .\n");
        sb.append("\tperiod\t-number, only when mem2mem is true. Length of this bw test, in seconds");
        sb.append("\tfdtOptions\t-optional; string with options to be passed as they are given\n");
        sb.append("\tbandwidth\t-optional; limit the transfer speed to this number, in bps (bits/second)\n");
        return sb.toString();
    }

    /**
     * Handle commands coming from the started FDT instances. 
     * These will ask periodically for running parameters, such as the maximum speed.
     * 
     * This allows tuning its speed at runtime, as the transfer goes. This is the
     * basis for intelligent priority-based scheduling => the high priority transfers
     * could squeeze lower priority ones.
     */
    private class XDRCommandHandler implements LisaXDRModule {

        @Override
        public XDRMessage execCommand(String module, String command, List args) {
            if (command.equals("getControlParams")) {
                String requestID = (String) args.get(0);
                if (requestID != null) {
                    FDTInstance fdtInst = (FDTInstance) htInstances.get(requestID);
                    if (fdtInst != null) {
                        StringBuilder sbRes = new StringBuilder();
                        sbRes.append("limit ").append(fdtInst.getMaxSpeed());
                        // possibly other parameters here
                        return XDRMessage.getSuccessMessage(sbRes.toString());
                    }
                    logger.warning("Got getControlParams request from unknown fdtMonID: " + requestID);
                    return XDRMessage.getErrorMessage("Unknown fdtMonID.");
                }
                return XDRMessage.getErrorMessage("Invalid command; fdtMonID is missing");
            }
            return XDRMessage.getErrorMessage("Unknown command: " + command);
        }

        @Override
        public List getCommandSet() {
            return null;
        }

        @Override
        public String getCommandUsage(String command) {
            return null;
        }
    }
}
