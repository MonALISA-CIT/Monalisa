package lia.app.transfer;

import java.util.Iterator;
import java.util.Properties;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Farm.Transfer.ProtocolManager;
import lia.app.AppUtils;

/**
 * This is the entry point to the transfer protocols. It receives commands and configuration 
 * from the user using the app-control interface and passes them to the ProtocolManager for handling.
 *  
 * @author catac
 */
public class AppTransfer implements lia.app.AppInt {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(AppTransfer.class.getName());

    /** Path to the AppTransfer's config file **/
    private String configFile;

    /** 
     * Global properties for AppTransfer and its used protocols. The protocols' parameters
     * are prefixed with the protocol's name + ".".
     */
    private final Properties prop;

    /** Is the App started ? */
    private boolean bRunning;

    /** Create the AppTransfer */
    public AppTransfer() {
        prop = new Properties();
        bRunning = false;
        logger.info("AppTransfer created.");
    }

    @Override
    public boolean start() {
        bRunning = true;
        return true;
    }

    @Override
    public boolean stop() {
        bRunning = false;
        logger.info("Stopping AppTransfer on admin request.");
        ProtocolManager.getInstance().shutdownProtocols();
        return true;
    }

    @Override
    public boolean restart() {
        return stop() && start();
    }

    @Override
    public int status() {
        return bRunning ? AppUtils.APP_STATUS_RUNNING : AppUtils.APP_STATUS_STOPPED;
    }

    @Override
    public String info() {
        // xml with the version & stuff
        StringBuilder sb = new StringBuilder();
        sb.append("<config app=\"Transfer\">\n");
        sb.append("<file name=\"info\">\n");

        //TODO: add info from each protocol
        sb.append("Protocol info to be added.");

        sb.append("</file>");
        sb.append("</config>");
        logger.info("info called; returning " + sb.toString());
        return sb.toString();
    }

    @Override
    public String exec(String sCmd) {
        if (logger.isLoggable(Level.FINER)) {
            logger.finer("Executing command '" + sCmd + "'");
        }
        String result = ProtocolManager.getInstance().execCommand(sCmd);
        if (logger.isLoggable(Level.FINER)) {
            logger.finer("Command finished with:\n" + result);
        }
        return result;
    }

    @Override
    public boolean update(String sUpdate) {
        return true;
    }

    @Override
    public boolean update(String sUpdate[]) {
        return true;
    }

    @Override
    public String getConfiguration() {
        StringBuilder sb = new StringBuilder();
        TreeSet ts = new TreeSet(prop.keySet());
        for (Iterator tsit = ts.iterator(); tsit.hasNext();) {
            String s = (String) tsit.next();
            sb.append(s + "=" + prop.getProperty(s) + "\n");
        }
        return sb.toString();
    }

    @Override
    public boolean updateConfiguration(String s) {
        return AppUtils.updateConfig(configFile, s) && init(configFile);
    }

    @Override
    public boolean init(String sPropFile) {
        logger.info("Setting configuration from file " + sPropFile);
        configFile = sPropFile;
        AppUtils.getConfig(prop, configFile);
        try {
            ProtocolManager.getInstance().configProtocols(prop);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Failed initializing protocols:", t);
        }
        return start();
    }

    @Override
    public String getName() {
        return "lia.app.transfer.AppTransfer";
    }

    @Override
    public String getConfigFile() {
        return configFile;
    }
}
