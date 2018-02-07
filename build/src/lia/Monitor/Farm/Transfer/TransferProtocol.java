package lia.Monitor.Farm.Transfer;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.BoundedDropVector;
import lia.util.DropEvent;
import lia.util.threads.MonALISAExecutors;

/**
 * This class defines the basic operations for transfer-related protocols.
 * 
 * @author catac
 */
public abstract class TransferProtocol implements DropEvent {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(TransferProtocol.class.getName());

    /** Started instances of protocol instances */
    protected Hashtable htInstances;

    /** name of this protocol */
    public String name;

    /** config parameters for this protocol */
    protected Properties config;

    /** regardless of config change, do the configUpdate if it's the first time */
    private boolean firstConfigUpdate;

    /** The period for the protocol self check task, ran by the AppTransfer's timer. */
    protected long lStatusCheckSeconds = 30;

    /** Monitoring results collected when checking protocols' status */
    protected BoundedDropVector bvMonitorResults;

    /** Handler for the status checker task */
    private final ScheduledFuture sfStatusChecker;

    /**
     * Helper class used to report the status of the existing reservations.
     * If the instance's checkStatus() returns false, the instance is considered finished
     * and is removed from the hash of active instances. 
     */
    class StatusChecker implements Runnable {
        @Override
        public void run() {
            try {
                if (logger.isLoggable(Level.FINER)) {
                    logger.finer(name + "-StatusChecker... " + htInstances.size() + " instances");
                }
                synchronized (htInstances) {
                    for (Iterator iit = htInstances.values().iterator(); iit.hasNext();) {
                        ProtocolInstance pInstance = (ProtocolInstance) iit.next();
                        if (!pInstance.checkStatus(bvMonitorResults)) {
                            iit.remove();
                        }
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error checking " + name + " instance status", t);
            }
        }
    }

    /**
     * Create the Protocol Instance container
     *  
     * @param protocolName Name of this protocol.
     */
    public TransferProtocol(String protocolName) {
        this.name = protocolName;
        htInstances = new Hashtable();
        config = new Properties();
        firstConfigUpdate = true;
        bvMonitorResults = new BoundedDropVector(1000, this);
        long delay = (long) (lStatusCheckSeconds * Math.random());
        sfStatusChecker = MonALISAExecutors.getMLHelperExecutor().scheduleWithFixedDelay(new StatusChecker(), delay,
                lStatusCheckSeconds, TimeUnit.SECONDS);
    }

    /**
     * Set the config parameters for this protocol, based on the global parameters for AppTransfer. 
     * The relevant parameters are prefixed with the protocol name + ".". The protocol's config
     * will not have name+"." in parameter's names.
     * 
     * If the configuration has changed, the abstract method updateConfig() will be called.
     *   
     * @param allProperties The global configuration of AppTransfer.
     */
    public void setConfig(Properties allProperties) {
        boolean changed = firstConfigUpdate;
        Properties nConfig = new Properties();
        String myKeyPrefix = name + ".";
        for (Object element : allProperties.entrySet()) {
            Map.Entry pe = (Map.Entry) element;
            String key = (String) pe.getKey();
            if (key.startsWith(myKeyPrefix)) {
                key = key.substring(myKeyPrefix.length());
                String val = (String) pe.getValue();
                nConfig.setProperty(key, val);
                if ((config.getProperty(key) == null) || (!config.getProperty(key).equals(val))) {
                    changed = true;
                }
            }
        }
        if (changed || (nConfig.size() != config.size())) {
            synchronized (config) {
                config.clear();
                config.putAll(nConfig);
            }
            updateConfig();
        }
        firstConfigUpdate = false;
    }

    /**
     * Execute a command.
     * 
     * @param sCmd The command (including all necessary parameters): A multi-line string. 
     * The first line is the command to execute. Following lines are of the form "paramName=paramValue"
     * @return Its output.
     */
    public String exec(String sCmd) {
        int sepIdx = sCmd.indexOf('&');
        String command = sCmd.trim();
        String params = "";
        if (sepIdx != -1) {
            command = sCmd.substring(0, sepIdx).trim();
            params = sCmd.substring(sepIdx + 1);
        }
        Properties props = TransferUtils.splitString(params, "&");
        if (command.equals("start")) {
            return startInstance(props);
        } else if (command.equals("stop")) {
            return stopInstance(props);
        } else if (command.equals("help")) {
            return getProtocolUsage();
        } else {
            return execCommand(command, props);
        }
    }

    /**
     * Publish a monitoring result.
     * @param r the Result to publish.
     */
    protected void publishResult(Result r) {
        bvMonitorResults.add(r);
    }

    /**
     * Publish monitoring eResult.
     * @param er the eResult to publish.
     */
    protected void publishResult(eResult er) {
        bvMonitorResults.add(er);
    }

    /**
     * Retrieve monitoring information from this protocol instances.
     * @param lResults the given container to be filled with monitoring information from this protocol  
     */
    public void getMonitorInfo(List lResults) {
        synchronized (bvMonitorResults) {
            lResults.addAll(bvMonitorResults);
            bvMonitorResults.clear();
        }
    }

    /**
     * Called when the bvMonitorResults vector cannot hold anymore
     * monitoring results from protocol instances. This will happen if the monAppTransfer
     * module is not active in the farm's configuration.
     */
    @Override
    public void notifyDrop() {
        logger.warning("Dropping monitoring results in protocol " + name);
    }

    /**
     * Override this to support other commands (besides the classic start,stop and help).
     * @param sCmd the command to execute
     * @param props its parameters
     * @return the result string
     */
    public String execCommand(String sCmd, Properties props) {
        return "-ERR Unknown protocol command '" + sCmd + "'. Try '" + name + " help'";
    }

    /**
     * Stop immediately all protocol instances. The protocol will not be used anymore.
     */
    public void shutdownProtocol() {
        synchronized (htInstances) {
            sfStatusChecker.cancel(false);
            for (Iterator iit = htInstances.values().iterator(); iit.hasNext();) {
                ProtocolInstance pInstance = (ProtocolInstance) iit.next();
                pInstance.stop();
                pInstance.checkStatus(bvMonitorResults);
                iit.remove();
            }
        }
    }

    /**
     * If the configuration has changed, this method will be called so that each 
     * protocol can update its parameters based on the "config" properties.
     */
    public abstract void updateConfig();

    /**
     * Create a new instance of the protocol, with the given parameters.
     * 
     * @param props Properties for the new protocol instance 
     * @return The result of the operation
     */
    public abstract String startInstance(Properties props);

    /**
     * Forcibly terminate an active protocol instance.
     * 
     * @param props Properties for the
     * @return The result of the operation 
     */
    public abstract String stopInstance(Properties props);

    /**
     * Provide help regarding the parameters to be passed to this protocol
     * 
     * @return The result of the operation
     */
    public abstract String getProtocolUsage();
}
