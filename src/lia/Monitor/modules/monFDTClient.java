package lia.Monitor.modules;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.Result;
import lia.util.ShutdownManager;
import lia.util.fdt.FDTListener;
import lia.util.fdt.FDTManagedController;
import lia.util.fdt.xdr.XDRMessage;
import lia.util.ntp.NTPDate;

/**
 * Module used to control/report information about a FDT Client
 * 
 * @author adim
 */
public class monFDTClient extends FDTManagedController {

    private static final long serialVersionUID = -2431447364463205440L;
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monFDTClient.class.getName());

    private final String[] sResTypes = new String[0]; // dynamic

    static public String ModuleName = "FDTClient";

    static public String resultsModName = "monXDRUDP"; // report the results as coming from this module

    static Random randomGenerator = new Random(System.currentTimeMillis());

    private int lMeasurementID = Math.abs(randomGenerator.nextInt());

    @Override
    public MonModuleInfo init(MNode node, String args) {
        // update the module info
        MonModuleInfo info = super.init(node, args);
        info.ResTypes = sResTypes;
        info.setName(ModuleName);
        try {
            // KILL prv client
            super.getStdoutFirstLines("pkill -9 -f lisafdtclient ", 0);
        } catch (Throwable t) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Cannot cleanup the previous FDT Client", t);
            }
        }
        // start (if is not started already) an FDT monitoring server and register ourserlves as listner for FDTClient
        // commands
        FDTListener.getInstance().instanceStarted(ModuleName, this);

        ShutdownManager.getInstance().addModule(this);
        return info;
    }

    @Override
    public Object doProcess() throws Exception {

        if (info.getState() != 0) {
            throw new IOException("[FDTClient: " + this.Node.getName() + "]  Module could not be initialized");
        }
        final String sFDTClientsURL = getModuleConfiguration().getProperty("controlURL",
                AppConfig.getProperty("fdt.controlURL", DEFAULT_FDT_CONF_URL))
                + FDT_CLIENTS_CONF;

        // by default, the client should not start
        // it should stop if it fails to get its config
        Properties newConfig = new Properties();
        newConfig.clear();
        newConfig.put("client.start", "0");

        // check the URL and perform start/restart actions of fdt client
        URLConnection conn = null;
        try {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "[FDTClient] Reading ..." + sFDTClientsURL.toString());
            }
            final String myName = getName();
            String status = (prBackgroundProcess.isRunning()) ? "0" : "1";
            String sURL = sFDTClientsURL + "&user.name=" + encode(System.getProperty("user.name")) + "&machine.name="
                    + encode(myName == null ? InetAddress.getLocalHost().toString() : myName) + "&client.status="
                    + encode(status);
            URL url = new URL(sURL);
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "[FDTClient] Announcement " + sURL);
            }
            conn = getURLConnection(url);
            conn.connect();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "[FDTClient] Cannot open Client URL: " + sFDTClientsURL, e);
            conn = null;
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "[FDTClient] Internal error:", t);
            conn = null;
        }

        if (conn != null) {
            // Read lines from fdt.clients
            try {
                final InputStream is = conn.getInputStream();
                newConfig.load(is);
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "[FDTClient] Got config:" + newConfig);
                }
                is.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "[FDTClient] Error while reading config from URL.", e);
            }
        }
        synchronized (pRemoteConfig) {
            pRemoteConfig.clear();
            pRemoteConfig.putAll(newConfig);
        }

        applyConfiguration(pRemoteConfig);

        // return the cache monitoring information received from FDT
        return getBufferedResults();
    }

    /**
     * @see lia.Monitor.monitor.MonitoringModule#ResTypes()
     */
    @Override
    public String[] ResTypes() {
        return sResTypes;
    }

    // module is ignored here since we know for sure that we are in client mode.
    @Override
    public XDRMessage execCommand(String module, String command, List args) {
        final long rightNow = NTPDate.currentTimeMillis();
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "[FDTClient] New XDR command received. Cmd: " + command + " Args:" + args);
        }
        try {
            XDRMessage xdrOutput = new XDRMessage();
            final String myName = getName();
            if (command.equalsIgnoreCase("monitorTransfer")) {
                if (args.size() < 3) {
                    final String key = (String) args.get(0);
                    if ((key != null) && "RESTARTME".equalsIgnoreCase(key.trim())) {
                        // RESTART THE CLIENT (todo: factor-out this code)
                        try {
                            stopFDT();
                            startFDT(pRemoteConfig);
                            bActive = true;
                            reportStatus("client.status", "0");
                            if (logger.isLoggable(Level.FINEST)) {
                                logger.log(Level.FINEST,
                                        "[FDTClient]  Restarted [ Running=" + prBackgroundProcess.isRunning() + " ]");
                            }
                        } catch (IOException e) {
                            logger.log(Level.WARNING, "[FDTClient] Error while trying to stop & startFDT.", e);
                        }
                        return XDRMessage.getSuccessMessage("Restarted");
                    }// RESTART

                    logger.log(Level.WARNING, "[FDTClient] Invalid monitorTransfer parameters");
                    xdrOutput.payload = "Invalid monitorTransfer parameters";
                    xdrOutput.status = XDRMessage.ERROR;

                } else {
                    try {
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
                                        "[FDTClient-MON] [skipped] Invalid mon value. Cause: " + e.getMessage());
                            }
                        }
                        paramNames.add("MeasurementID");
                        paramValues.add(Double.valueOf(lMeasurementID));
                        String[] aParamNames = (String[]) paramNames.toArray(new String[paramNames.size()]);
                        double[] aParamValues = new double[paramValues.size()];
                        int i = 0;
                        for (Iterator iterator = paramValues.iterator(); iterator.hasNext(); i++) {
                            aParamValues[i] = ((Double) iterator.next()).doubleValue();
                        }
                        Result rResult = new Result(Node.getFarmName(), "FDT_Link_" + linkName, myName + "_"
                                + (String) args.get(0), resultsModName, aParamNames, aParamValues);
                        rResult.time = rightNow;
                        addToBufferedResults(rResult);
                        // apMon.sendParameters("Link_" + linkName, myName + "_" + args.get(0), paramNames.size(),
                        // paramNames, paramValues);
                        xdrOutput.payload = "OK";
                        xdrOutput.status = XDRMessage.SUCCESS;
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "[FDTClient-MON] Monitoring values published: Servers {0} {1} {2}",
                                    new Object[] { myName + "_" + args.get(0), paramNames, paramValues });
                        }
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

    @Override
    protected void startFDT(Properties config) throws IOException {
        final String ip;
        String cmd;
        synchronized (config) {
            ip = config.getProperty("client.dest_ip");
            cmd = FDT_CLIENT_CMD_PREFIX + " " + config.getProperty("client.command") + " " + FDT_CLIENT_CMD_SUFFIX;
        }
        if (cmd != null) {
            cmd = cmd.replaceAll("%JAR%", getFDTJar()).replaceAll("%IP%", ip);
        }
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "[FDTClient] Starting FDT client for[" + ip + "] using:[" + cmd + "]");
        }
        try {
            String trace = getStdoutFirstLines("tracepath " + ip, 100).toString();
            reportTrace(ip, trace);
        } catch (Throwable t) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Cannot get tracepath output. Cause:", t);
            }
        }
        lMeasurementID++;
        prBackgroundProcess.startProcess(cmd);

    }

    @Override
    protected void stopFDT() throws IOException {
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "[FDTClient] Stopping FDT client with cmd: " + prBackgroundProcess.getSCmd());
        }
        prBackgroundProcess.stopProcess();
    }

    /**
     * Apply the remote configuration read from the control servlet
     * 
     * @param config
     */
    private void applyConfiguration(Properties config) {
        boolean bNewStatus = false;
        String ip = null;
        try {
            synchronized (config) {
                bNewStatus = Integer.parseInt(config.getProperty("client.start")) > 0 ? true : false;
                linkName = config.getProperty("link.name");
                ip = config.getProperty("client.dest_ip");
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error while taking config parameters", ex);
        }
        String crtCommand = "";
        if (!isNull(ip)) {
            crtCommand = FDT_CLIENT_CMD_PREFIX.replaceAll("%JAR%", getFDTJar()).replaceAll("%IP%", ip) + " "
                    + config.getProperty("client.command") + " " + FDT_CLIENT_CMD_SUFFIX;
        }
        bNewStatus = bNewStatus && !isNull(crtCommand);

        bActive = prBackgroundProcess.isRunning();
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "[FDTClient] PrvActive:[" + bActive + "] PrvCmd[:" + prvCommand + "] CrtActive:["
                    + bNewStatus + "] CrtCmd:[" + crtCommand + "] \n");
        }

        if ((bActive && !bNewStatus) || (!isNull(prvCommand) && isNull(crtCommand))) {
            try {
                stopFDT();
                bActive = false;
                reportStatus("client.status", "1");
            } catch (IOException e) {
                logger.log(Level.WARNING, "[FDTClient] Error while stopFDT and reportStatus.", e);
            }
            prvCommand = crtCommand;
            return;
        }

        if (bNewStatus) // if green flag on configuration service
        {
            if (!bActive || ((isNull(prvCommand) && !isNull(crtCommand)) || !crtCommand.equals(prvCommand))) {
                try {
                    stopFDT();
                    startFDT(config);
                    bActive = true;
                    reportStatus("client.status", "0");
                } catch (IOException e) {
                    logger.log(Level.WARNING, "[FDTClient] Error while restartFDT and reportStatus.", e);
                }
                prvCommand = crtCommand;
                return;
            }
        }

        prvCommand = crtCommand;
        if (logger.isLoggable(Level.FINEST)) {
            logger.fine("[FDTCLient] No change in client status [running:" + bActive + "]");
        }
        return;
    }

}
