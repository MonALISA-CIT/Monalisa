package lia.Monitor.Farm.Transfer;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.Result;
import lia.util.fdt.ProcessRunner;
import lia.util.ntp.NTPDate;

/**
 * Hold and control a FDT protocol instance, either server or client. FDT is started as a
 * separate process with the command built using the given parameters. Transfer's monitoring
 * is performed using the monFDTMon module which has to be started in this ML and
 * configured to listen on FDTProtocol.lisaFdtPort.
 * 
 * @author catac
 */
public class FDTInstance implements ProtocolInstance {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(FDTInstance.class.getName());

    /** The TransferID, same as the reservationID. This is null for server. */
    private String transferID;
    /** the full command to execute */
    private final String javaCmd;
    /** the exit code */
    private int exitCode = -2;
    /** Path to the log file */
    private final String logFile;
    /** User has requested end of this instance. */
    private boolean userRequestedStop;

    /** Whether this FTD instance is for a mem2mem test */
    private boolean mem2mem = false;
    /** Period for which this mem2mem test is performed, in millis */
    private long period = -1;
    /** Moment when this mem2mem test has started */
    private long startTime;

    /** the command will be executed by this process runner */
    private final ProcessRunner prBackgroundProcess = new ProcessRunner();
    /** Whether the start method was called */
    private volatile boolean started = false;

    /** Maximum speed allowed for this FDT transfer. This can be changed dynamically.
     *  See XDRCommandHandler in FDTProtocol. */
    private long maxSpeed;

    /** 
     * Create a Start the instance in the client mode.
     * @param transferID The transferID
     * @param destIP The destination IP/hostname
     * @param fdtOptions Other options directly passed to FDT
     * @param maxSpeed if positive, limit the transfer speed to this value, in bytes per second
     * @param files space separated list of files. If this is null, the started FDT instance will
     * perform a memory to memory benchmark.
     */
    public FDTInstance(String transferID, String destIP, String fdtOptions, long maxSpeed, String files,
            long periodSeconds) {
        this.transferID = transferID;
        this.maxSpeed = maxSpeed;
        logFile = FDTProtocol.fdtLogDir + "/fdt_" + transferID + ".log";

        // build the command line
        StringBuilder sbCmd = new StringBuilder();
        sbCmd.append("cd ").append(FDTProtocol.fdtBasedir).append("; ");
        sbCmd.append(FDTProtocol.sJavaBin);
        sbCmd.append(" -jar ").append(FDTProtocol.sPathFDT);
        if (maxSpeed > 0) {
            sbCmd.append(" -limit ").append(maxSpeed);
        }
        sbCmd.append(" -lisafdtclient localhost:").append(FDTProtocol.lisaFdtPort);
        sbCmd.append(" -c ").append(destIP);
        sbCmd.append(" -monID ").append(transferID);
        sbCmd.append(" ").append(fdtOptions);
        if (files != null) {
            sbCmd.append(" -d ."); // TODO: find a proper way to do this
            sbCmd.append(" ").append(files);
        } else {
            sbCmd.append(" -d /dev/null /dev/zero");
            period = periodSeconds * 1000;
            mem2mem = true;
        }
        sbCmd.append(" 0<&- &>").append(logFile);
        javaCmd = sbCmd.toString();
    }

    /**
     * Start the instance in the server mode.
     * @param srvOptions Options strings to pass directly to FDT.
     */
    public FDTInstance(String name, String srvOptions) {
        StringBuilder sbCmd = new StringBuilder();
        logFile = FDTProtocol.fdtLogDir + "/fdt_" + name + ".log";

        // prepare the command line
        sbCmd.append("cd ").append(FDTProtocol.fdtBasedir).append("; ");
        sbCmd.append(FDTProtocol.sJavaBin);
        sbCmd.append(" -jar ").append(FDTProtocol.sPathFDT);
        sbCmd.append(" -lisafdtserver localhost:").append(FDTProtocol.lisaFdtPort);
        sbCmd.append(" -p ").append(FDTProtocol.serverPort);
        sbCmd.append(" ").append(srvOptions);
        sbCmd.append(" 0<&- &>").append(logFile);
        javaCmd = sbCmd.toString();
    }

    @Override
    public boolean start() {
        try {
            logger.info("Starting FDT instance with " + javaCmd);
            startTime = NTPDate.currentTimeMillis();
            prBackgroundProcess.startProcess(javaCmd);
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "Error starting FDT "
                    + (transferID == null ? "server." : "transfer " + transferID), ioe);
            return false;
        } finally {
            started = true;
        }
        return true;
    }

    @Override
    public boolean stop() {
        try {
            logger.info("Stopping FDT instance with " + javaCmd);
            prBackgroundProcess.stopProcess();
            userRequestedStop = true;
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "Error stopping FDT "
                    + (transferID == null ? "server." : "transfer " + transferID), ioe);
            return false;
        }
        return true;
    }

    @Override
    public boolean checkStatus(List lResults) {
        if (!started) {
            return true; // not yet started, but soon to start. Keep it in the hashes
        }
        boolean running = prBackgroundProcess.isRunning();
        long now = NTPDate.currentTimeMillis();
        if (mem2mem) {
            long timeLeft = (startTime + period) - now;
            Result r = new Result(TransferUtils.farmName, "FDT_Transfers", transferID, TransferUtils.resultsModuleName,
                    new String[] { "period", "timeleft" }, new double[] { period / 1000, timeLeft / 1000 });
            r.time = now;
            lResults.add(r);
            if (running && (timeLeft <= 0)) {
                logger.info("Period for mem2mem FDT " + transferID + " has finished (" + (period / 1000) + " sec).");
                stop(); // stop it now
                running = false;
            }
        }
        if (!running) {
            if (transferID == null) {
                // server stopped
                if (!userRequestedStop) {
                    logger.info("FDT Server not running while it should! Trying to restart it!");
                    start();
                    return true;
                }
            } else {
                // some client stopped
                exitCode = prBackgroundProcess.exitCode();
                Result r = new Result(TransferUtils.farmName, "FDT_Transfers", transferID,
                        TransferUtils.resultsModuleName, new String[] { "ExitCode" }, new double[] { exitCode });
                r.time = now;
                lResults.add(r);
            }
        }
        return running;
    }

    /** Set new max speed for this transfer. This is called when scheduler decides to change it. */
    public void setMaxSpeed(long aMaxSpeed) {
        maxSpeed = aMaxSpeed;
    }

    /** Get the max speed for this transfer. This is called by XDRCommandHandler,
     * when the FDT running instance is asking for controlling parameters.
     */
    public long getMaxSpeed() {
        return maxSpeed;
    }
}
