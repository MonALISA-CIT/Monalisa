package lia.Monitor.Agents.OpticalPath.v2;

import java.io.File;
import java.io.FileReader;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Agents.OpticalPath.v2.State.OSwConfig;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwFSM;
import lia.util.DateFileWatchdog;

class LocalConfigurationManager extends Observable implements Observer {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(LocalConfigurationManager.class.getName());

    private class DelayedNotificationTask implements Runnable {
        long delay;
        OSwConfig oldOsi;

        public DelayedNotificationTask(OSwConfig oldOsi, long delay) {
            this.oldOsi = oldOsi;
            this.delay = delay;
        }

        @Override
        public void run() {
            long sTime = System.currentTimeMillis();
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "DelayedNotificationTask - enters RUN");
            }
            try {
                try {
                    synchronized (this) {
                        this.wait(delay);
                    }
                } catch (Throwable t) {

                }
                setChanged();
                notifyObservers(oldOsi);
            } catch (Throwable t) {

            }
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "DelayedNotificationTask - exits RUN [ " + (System.currentTimeMillis() - sTime)
                        + " ]");
            }
        }
    }

    private final File confFile;
    private final DateFileWatchdog dfw;
    OSwConfig oswConfig;

    public LocalConfigurationManager(File configFile, Observer obs) throws Exception {
        this.confFile = configFile;
        dfw = DateFileWatchdog.getInstance(this.confFile, 5 * 1000);
        dfw.addObserver(this);
        addObserver(obs);
        oswConfig = null;
        reloadConfig();
    }

    private void reloadConfig() {
        logger.log(Level.INFO, " OpticalPathAgent_v2 Reloading conf ... ");
        FileReader fr = null;

        try {
            fr = new FileReader(confFile);
            OSwConfig newOSwConfig = Util.getOpticalSwitchConfig(fr);
            setNewConfiguration(newOSwConfig, 0L);
            logger.log(Level.INFO, " OpticalPathAgent_v2 FINISHED Reloading conf \n\n" + oswConfig.toString() + "\n\n");
            OSwFSM.getInstance().changeConfig(newOSwConfig);
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got Exc while reloadConfig()", t);
        } finally {
            if (fr != null) {
                try {
                    fr.close();
                } catch (Throwable t1) {
                    logger.log(Level.WARNING, " Got Exc closing the file reader", t1);
                }
            }//if()
        }

    }

    /**
     * This method will notify all the registered oservers.
     * The caller has the posibility to delay the notification ... ( e.g. MCONN | DCONN cmd-s ) 
     * 
     * @param newOSI
     * @param delayNotify - How long to delay the notification. 
     * If delayNotify <= 0 the cahange will be notified immediately.
     */
    public void setNewConfiguration(OSwConfig newOSI, long delayNotify) {
        if (newOSI != null) {
            OSwConfig oldOSI = oswConfig;
            oswConfig = newOSI;
            if (delayNotify > 0) {
                OpticalPathAgent_v2.executor.execute(new DelayedNotificationTask(oldOSI, delayNotify));
                return;
            }
            setChanged();
            notifyObservers(oldOSI);
        }
    }

    /**
     * @return Informations about it's OpticalSwitches peers
     */
    public OSwConfig[] getPeers() {
        return null;
    }

    /**
     * @see java.util.Observer#update(java.util.Observable, java.lang.Object)
     */
    @Override
    public void update(Observable o, Object arg) {
        if ((dfw != null) && (o != null) && o.equals(dfw)) {
            reloadConfig();
        }
    }
}
