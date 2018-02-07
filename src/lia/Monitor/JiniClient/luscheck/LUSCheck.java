package lia.Monitor.JiniClient.luscheck;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniClient.CommonJini.JiniClient;
import lia.Monitor.monitor.AppConfig;
import lia.util.MLProcess;
import lia.util.mail.DirectMailSender;
import lia.util.ntp.NTPDate;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;

/**
 * Simple ML Client that only checks the LUS. If it fails
 * to respond, it runs a script and sends an email with the result.
 * 
 * @author catac
 */

public class LUSCheck extends JiniClient {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(LUSCheck.class.getName());
    Timer checkTimer;
    long checkPeriod = 60 * 1000; // how often we check the LUS
    long siUpdateTimeout = 240 * 1000; // after how long, if the SIs are not updated, trigger the alert
    String triggerScript = null;
    String[] mailTo = { "Catalin.Cirstoiu@cern.ch" };
    String host = "localhost";

    /** Main program */
    public static void main(String[] args) {
        new LUSCheck();
    }

    public LUSCheck() {
        super(null, true, false);
        logger.log(Level.INFO, "Initializing...");
        init();

        triggerScript = AppConfig.getProperty("lia.Monitor.JiniClient.util.LUSCheck.triggerScript");
        if ((triggerScript != null) && (triggerScript.trim().length() > 0)) {
            logger.log(Level.INFO, "In case LUS doesn't respond, /bin/sh -c '" + triggerScript + "' will be executed");
        } else {
            logger.log(Level.WARNING,
                    "Trigger script not defined. Will NOT take any actions in case LUS doesn't respond!");
            triggerScript = null;
        }

        try {
            host = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            logger.log(Level.WARNING, "Cannot get my hostname. Using localhost.", e);
        }

        String mailDest = AppConfig.getProperty("lia.Monitor.JiniClient.util.LUSCheck.mailTo",
                "Catalin.Cirstoiu@cern.ch");
        mailTo = mailDest.split(",");
        logger.log(Level.INFO, "Mail alerts concerning LUS@" + host + " are sent to " + mailDest);

        initLUSQuery();

        siUpdateTimeout = AppConfig.getl("lia.Monitor.JiniClient.util.LUSCheck.siUpdateTimeout", 240) * 1000;
        logger.log(Level.INFO, "Triggering alert if ServiceItems are not updated after " + (siUpdateTimeout / 1000)
                + " seconds.");
        checkPeriod = AppConfig.getl("lia.Monitor.JiniClient.util.LUSCheck.checkPeriod", 60) * 1000;
        logger.log(Level.INFO, "Scheduling LUS periodical check each " + (checkPeriod / 1000) + " seconds.");

        checkTimer = new Timer();
        checkTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                checkLUS();
            };
        }, checkPeriod, checkPeriod);
        logger.log(Level.INFO, "Initialization finished.");
    }

    void initLUSQuery() {
        logger.log(Level.INFO, "Asking for ML Proxies, ML Services...");
        ServiceItem psi[];
        ServiceItem ssi[];
        do {
            psi = mlLusHelper.getProxies();
            ssi = mlLusHelper.getServices();
            mlLusHelper.forceUpdate();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
            }
        } while ((psi == null) || (ssi == null));
        logger.log(Level.INFO, "Found " + psi.length + " proxies and " + ssi.length + " services!");
    }

    /** Do the actual check */
    void checkLUS() {
        Thread.currentThread().setName("(ML) - Periodical check timer");
        logger.log(Level.FINER, "Starting periodical check ...");
        ServiceItem psi[] = mlLusHelper.getProxies();
        ServiceItem ssi[] = mlLusHelper.getServices();
        long siUpdateDelay = NTPDate.currentTimeMillis() - mlLusHelper.getLastUpdateTime();

        if ((psi == null) || (ssi == null) || (psi.length == 0) || (ssi.length == 0)
                || (siUpdateDelay > siUpdateTimeout)) {
            StringBuilder msg = new StringBuilder("Failed to get updated proxies or services from LUS!!");
            if (psi == null) {
                msg.append("\nProxies list is NULL!");
            } else {
                msg.append("\nLUS sees ").append(psi.length).append(" proxies");
            }
            if (ssi == null) {
                msg.append("\nServices list is NULL!");
            } else {
                msg.append("\nLUS sees ").append(ssi.length).append(" services");
            }
            msg.append("\nServiceItems update delay: ").append(siUpdateDelay / 1000).append(" sec. ");
            msg.append(" Treshold set to: ").append(siUpdateTimeout / 1000).append(" sec.");
            if (triggerScript != null) {
                msg.append("\nRunning trigger script:");
                try {
                    Process p = MLProcess.exec(triggerScript, 30 * 1000);
                    BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                    String line;
                    while ((line = out.readLine()) != null) {
                        msg.append("\nOUT: ").append(line);
                    }
                    while ((line = err.readLine()) != null) {
                        msg.append("\nERR: ").append(line);
                    }
                    out.close();
                    err.close();
                } catch (Throwable t) {
                    msg.append("\nError running the trigger script\n").append(t);
                }
            } else {
                msg.append("\nNOT taking any action, since triggerScript isn't defined.");
            }
            try {
                DirectMailSender.sendMessageFromThread("LUScheck@" + host, mailTo, "LUSCheck@" + host, msg.toString());
            } catch (Throwable t) {
                msg.append("\nFailed sending email notification\n").append(t);
            }
            logger.log(Level.WARNING, msg.toString());
        } else {
            logger.log(Level.FINE, "Got " + psi.length + " proxies, " + ssi.length + " services and "
                    + (siUpdateDelay / 1000) + " seconds update delay.");
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Periodical check finished with " + (psi == null ? "NULL" : "" + psi.length)
                    + " proxies, " + (ssi == null ? "NULL" : "" + ssi.length) + " services and "
                    + (siUpdateDelay / 1000) + " seconds update delay.");
        }
    }

    @Override
    public boolean AddMonitorUnit(ServiceItem s) {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Adding service item " + s);
        }
        return true;
    }

    @Override
    public void AddProxyService(ServiceItem s) throws Exception {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Adding proxy service " + s);
        }
    }

    @Override
    public void closeProxyConnection() {
        logger.log(Level.FINE, "Closing connection with proxy");
    }

    @Override
    public boolean knownConfiguration(ServiceID farmID) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "known Config for " + farmID);
        }
        return true;
    }

    @Override
    public void portMapChanged(ServiceID id, ArrayList portMap) {
        // n/a
    }

    @Override
    public void removeNode(ServiceID id) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Removed node " + id);
        }
    }

    @Override
    public boolean verifyProxyConnection() {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Verifying conn with proxy...");
        }
        return true;
    }

    @Override
    public void waitServiceThreads(String message) {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Waiting Service Threads " + message);
        }
    }
}
