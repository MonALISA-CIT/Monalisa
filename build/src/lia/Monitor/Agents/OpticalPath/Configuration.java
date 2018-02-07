package lia.Monitor.Agents.OpticalPath;

import java.io.File;
import java.io.FileReader;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.DateFileWatchdog;

class Configuration extends Observable implements Observer {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(Configuration.class.getName());

    private class DelayedNotificationTask implements Runnable {
        long delay;
        SyncOpticalSwitchInfo oldOsi;

        public DelayedNotificationTask(SyncOpticalSwitchInfo oldOsi, long delay) {
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
    SyncOpticalSwitchInfo osi;

    public Configuration(File configFile, Observer obs) throws Exception {
        this.confFile = configFile;
        dfw = DateFileWatchdog.getInstance(this.confFile, 5 * 1000);
        dfw.addObserver(this);
        addObserver(obs);
        osi = null;
        reloadConfig();
    }

    private void reloadConfig() {
        logger.log(Level.INFO, " MLCopyAgent Reloading conf ... ");
        FileReader fr = null;

        try {
            fr = new FileReader(confFile);
            SyncOpticalSwitchInfo newOSI = SyncOpticalSwitchInfo.fromOpticalSwitchInfo(Util.getOpticalSwitchInfo(fr));
            setNewConfiguration(newOSI, 0L);
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " MLCopyAgent FINISHED Reloading conf \n\n" + osi.toString() + "\n\n");
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got Exc while reloadConfig()", t);
        } finally {
            if (fr != null) {
                try {
                    fr.close();
                } catch (Throwable t1) {
                    t1.printStackTrace();
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
    public void setNewConfiguration(SyncOpticalSwitchInfo newOSI, long delayNotify) {
        if (newOSI != null) {
            SyncOpticalSwitchInfo oldOSI = osi;
            osi = newOSI;
            if (delayNotify > 0) {
                MLCopyAgent.executor.execute(new DelayedNotificationTask(oldOSI, delayNotify));
                return;
            }
            setChanged();
            notifyObservers(oldOSI);
        }
    }

    /**
     * @return Informations about it's OpticalSwitches peers
     */
    public OpticalSwitchInfo[] getPeers() {
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
