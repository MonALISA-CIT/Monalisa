package lia.util;

import java.util.Date;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ShutdownReceiver;
import lia.util.logging.MLLogEvent;
import lia.util.ntp.NTPDate;
import sun.misc.Signal;
import sun.misc.SignalHandler;

/**
 * It will work only with SUN JVMs! 
 */
public class MLSignalHandler implements SignalHandler {

    /**
     * The singleton instance class
     */
    private static MLSignalHandler _thisInstance = null;

    /**
     * Logging component
     */
    private static final Logger logger = Logger.getLogger(MLSignalHandler.class.getName());

    private static final AtomicLong signalHUPTime = new AtomicLong(0L);
    private final Vector<ShutdownReceiver> registeredModules;
    private final AtomicBoolean isShuttingDown;
    private static long SIGNALS_DELTA;
    private static boolean installMLSignalHandler;
    private static long NOTIFY_DELTA;
    private long lastWarningLogger;
    private final Object syncLock;

    public static final String EXTENDED_CMD_STATUS = Utils.getPromptLikeBinShCmd("pstree -u -p")
            + Utils.getPromptLikeBinShCmd("ps aux") + Utils.getPromptLikeBinShCmd("hostname")
            + Utils.getPromptLikeBinShCmd("hostname -f") + Utils.getPromptLikeBinShCmd("hostname -i")
            + Utils.getPromptLikeBinShCmd("uname -a") + Utils.getPromptLikeBinShCmd("uptime");

    static {
        try {
            SIGNALS_DELTA = AppConfig.getl("lia.util.MLSignalHandler.SIGNALS_DELTA", 10000);
        } catch (Throwable t) {
            SIGNALS_DELTA = 10000;
        }

        if (SIGNALS_DELTA < 1000) {
            SIGNALS_DELTA = 10000;
        }

        try {
            NOTIFY_DELTA = AppConfig.getl("lia.util.MLSignalHandler.NOTIFY_DELTA", 5 * 60 * 1000);
        } catch (Throwable t) {
            NOTIFY_DELTA = 5 * 60 * 1000;
        }

        if (NOTIFY_DELTA < (30 * 1000)) {
            NOTIFY_DELTA = 5 * 60 * 1000;
        }

        try {
            installMLSignalHandler = AppConfig.getb("lia.util.MLSignalHandler.installMLSignalHandler", true);
        } catch (Throwable t) {
            installMLSignalHandler = true;
        }
    }

    private MLSignalHandler() {
        syncLock = new Object();
        registeredModules = new Vector<ShutdownReceiver>();
        isShuttingDown = new AtomicBoolean(false);
        lastWarningLogger = 0;

        //SIGINT seems to be ignored by the JVM on my machine ... dunno 
        Signal.handle(new Signal("INT"), this);
        Signal.handle(new Signal("HUP"), this);
        Signal.handle(new Signal("TERM"), this);
    }

    public synchronized static final MLSignalHandler getInstance() {
        if (!installMLSignalHandler) {
            return null;
        }
        if (_thisInstance == null) {
            try {
                _thisInstance = new MLSignalHandler();
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " Cannot instantiate MLSignalHandler", t);
                }
                _thisInstance = null;
            }
        }
        return _thisInstance;
    }

    public void addModule(ShutdownReceiver module) {
        if (isShuttingDown.get()) {
            logger.log(Level.INFO, " MLSignalHandler: ML is shutting down ");
            return;
        }
        if (module != null) {
            synchronized (registeredModules) {
                if (!registeredModules.contains(module)) {
                    registeredModules.add(module);
                    logger.log(Level.INFO, " MLSignalHandler: [ " + module.getClass() + " ] registered ");
                } else {
                    logger.log(Level.INFO, " MLSignalHandler: [ " + module.getClass() + " ] already registered ");
                }
            }
        }
    }

    public void removeModule(ShutdownReceiver module) {
        if (isShuttingDown.get()) {
            logger.log(Level.INFO, " MLSignalHandler: ML is shutting down ");
            return;
        }
        if (module == null) {
            return;
        }
        synchronized (registeredModules) {
            if (!registeredModules.contains(module)) {
                logger.log(Level.INFO, " MLSignalHandler: [ " + module.getClass() + " ] NOT registered ");
            } else {
                registeredModules.remove(module);
                logger.log(Level.INFO, " MLSignalHandler: [ " + module.getClass() + " ] removed");
            }
        }
    }

    public static final String getStatus() {

        StringBuilder sb = new StringBuilder(32768);
        Utils.appendExternalProcessStatus(new String[] { "/bin/sh", "-c", EXTENDED_CMD_STATUS }, new String[] {
                "COLUMNS=200", "PATH=/bin:/usr/bin:/usr/local/bin:/usr/sbin:/sbin:/usr/local/sbin" }, sb);

        return sb.toString();
    }

    public void shutdownNow(int exitCode) {
        try {
            ShutdownManager.getInstance().shutdownNow();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ MLSignalHandler ] Error while shutting down", t);
        }

        try {
            for (ShutdownReceiver shutDownReceiver : registeredModules) {
                try {
                    shutDownReceiver.Shutdown();
                } catch (Throwable t) {
                    //ignore exception in ShutdownReceiver's code 
                }
            }
        } catch (Throwable ignore) {
        } finally {
            try {
                System.out.flush();
                System.err.flush();
            } catch (Throwable igonre) {
            }

            System.exit(exitCode);
        }
    }

    @Override
    public void handle(Signal s) {
        try {
            logger.log(Level.INFO, "Got Signal " + s.getName() + " [ " + s.getNumber() + " ]");
            final long cTime = System.currentTimeMillis();
            final long nTime = NTPDate.currentTimeMillis();
            if (s.getName().equals("HUP")) {
                signalHUPTime.set(cTime);
                logger.log(Level.INFO, " SIGHUP received ");
            } else if (s.getName().equals("TERM")) {
                final long diffHUPTime = TimeUnit.NANOSECONDS.toMillis(Math.abs(cTime - signalHUPTime.get()));

                if (diffHUPTime <= SIGNALS_DELTA) {
                    isShuttingDown.set(true);
                    final String msg = "ML_SER stop request @ SysDate: " + new Date(cTime) + " | NtpDate: "
                            + new Date(nTime);
                    logger.log(Level.INFO, msg);
                    System.out.println(msg);
                    shutdownNow(0);
                } else {
                    synchronized (syncLock) {
                        long now = System.currentTimeMillis();
                        if ((lastWarningLogger + NOTIFY_DELTA) < now) {
                            lastWarningLogger = now;
                            MLLogEvent mlle = new MLLogEvent();
                            mlle.put("LastHUP", Long.valueOf(signalHUPTime.get()));
                            mlle.put("Status", getStatus());
                            logger.log(Level.WARNING, " SIGTERM received without SIGHUP and SIGINT first ... ",
                                    new Object[] { mlle });
                        }
                    }
                }
            }
        } catch (Throwable t) {
            MLLogEvent mlle = new MLLogEvent();
            mlle.put("LastHUP", Long.valueOf(signalHUPTime.get()));
            logger.log(Level.INFO, " Exc in handle Signal", new Object[] { t, mlle });
        }
    }
}