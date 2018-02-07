/**
 * 
 */
package lia.util.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import lazyj.Format;
import lia.Monitor.DataCache.DataSelect;
import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.Fast.DB;
import lia.Monitor.monitor.AccountingResult;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.DataReceiver;
import lia.Monitor.monitor.ExtResult;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.MLProperties;
import lia.util.actions.Action.SeriesState;
import lia.util.ntp.NTPDate;

/**
 * @author costing
 *
 */
public class ActionsManager implements DataReceiver, Runnable, AppConfigChangeListener {

    private static final Logger logger = Logger.getLogger(ActionsManager.class.getName());

    private static ActionsManager amInstance = null;

    /**
     * Singleton class, this is the way to get the only instance
     * 
     * @return the only instance of this class
     */
    public static synchronized ActionsManager getInstance() {
        if (amInstance == null) {
            if (!TransparentStoreFactory.isMemoryStoreOnly()) {
                final DB db = new DB();

                if (db.syncUpdateQuery(
                        "CREATE TABLE action_states (as_file varchar(255), as_key varchar(255) not null, as_value int not null default 0, as_last_ok int default 0, as_last_err int default 0, as_last_flipflop int default 0, as_lastchange int default 0);",
                        true)) {
                    db.syncUpdateQuery("CREATE UNIQUE INDEX action_states_pkey ON action_states(as_file, as_key);");
                }
            }

            amInstance = new ActionsManager();

            AppConfig.addNotifier(amInstance);
        }

        return amInstance;
    }

    private final Object oLock = new Object();

    private String sActionsFolder = null;

    private ArrayList<Action> alPeriodicActions = null;
    private ArrayList<Action> alEventActions = null;
    private ArrayList<Action> alAllActions = null;

    private ActionsManager() {
        reload();
    }

    private void reload() {
        synchronized (this.oLock) {
            this.sActionsFolder = AppConfig.getProperty("lia.util.actions.base_folder", null);

            this.alPeriodicActions = new ArrayList<Action>();
            this.alEventActions = new ArrayList<Action>();
            this.alAllActions = new ArrayList<Action>();

            if (this.sActionsFolder == null) {
                logger.log(Level.INFO, "Disabling because the property 'lia.util.actions.base_folder' is not defined");

                this.bHasToRun = false;
                return;
            }

            final File f = new File(this.sActionsFolder);

            if (!f.exists() || !f.isDirectory() || !f.canRead()) {
                logger.log(Level.INFO,
                        "Disabling because the property 'lia.util.actions.base_folder' doesn't point to an accessible folder");

                this.bHasToRun = false;
                return;
            }

            final String[] files = f.list();

            for (int i = 0; (files != null) && (i < files.length); i++) {
                String sFile = files[i];

                if (sFile.endsWith(".properties")) {
                    sFile = sFile.substring(0, sFile.lastIndexOf(".properties"));

                    logger.log(Level.FINER, "Reading configuration file: " + sFile);

                    try {
                        final MLProperties mlp = new MLProperties(this.sActionsFolder, sFile, null);

                        mlp.set("properties_file", sFile);

                        final Action a = new Action(mlp);

                        if (a.isPeriodicAction()) {
                            this.alPeriodicActions.add(a);
                        }

                        if (a.isEventAction()) {
                            this.alEventActions.add(a);
                        }

                        this.alAllActions.add(a);
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Caught exception while instantiating an action", t);
                    }
                }
            }

            if (this.alAllActions.size() == 0) {
                logger.log(Level.INFO, "Disabling because no valid .properties file existed in '" + this.sActionsFolder
                        + "'");

                this.bHasToRun = false;
            } else {
                if (!this.bHasToRun) {
                    this.bHasToRun = true;

                    (new Thread(this, "(ML) ActionsManager")).start();
                }

                logger.log(Level.INFO, "Observing: " + this.alAllActions.size() + " total files, "
                        + this.alPeriodicActions.size() + " periodic actions and " + this.alEventActions.size()
                        + " event-based actions");
            }
        }
    }

    private volatile boolean bHasToRun = false;

    private static final int UPDATE_VACUUM_THRESHOLD = 10000;
    private static int iUpdateCount = (UPDATE_VACUUM_THRESHOLD * 9) / 10;

    @Override
    public void run() {

        int initialDelay = AppConfig.geti("lia.util.actions.initial_delay", 0);

        if (initialDelay > 0) {
            try {
                Thread.sleep(initialDelay * 1000);
            } catch (InterruptedException ie) {
                // ignore
            }
        }

        while (this.bHasToRun) {
            synchronized (this.oLock) {
                for (int i = this.alPeriodicActions.size() - 1; i >= 0; i--) {
                    try {
                        this.alPeriodicActions.get(i).check();
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Exception executing action", t);
                    }
                }
            }

            try {
                Thread.sleep(AppConfig.geti("lia.util.actions.period_heartbeat", 15000));
            } catch (InterruptedException ie) {
                // ignore
            }

            if (iUpdateCount > UPDATE_VACUUM_THRESHOLD) {
                final DB db = new DB();

                if (db.maintenance("VACUUM ANALYZE action_states;", true)) {
                    db.maintenance("REINDEX TABLE action_states;");
                }

                iUpdateCount = 0;
            }

            if (!TransparentStoreFactory.isMemoryStoreOnly()) {
                final long lNow = NTPDate.currentTimeMillis() / 1000;

                synchronized (this.oLock) {
                    for (int i = this.alAllActions.size() - 1; i >= 0; i--) {
                        final Action a = this.alAllActions.get(i);

                        if (a.getAndResetActionTaken()) {
                            final DB db = new DB();

                            final ArrayList<SeriesState> alSeries = a.getSeriesList();

                            for (int j = alSeries.size() - 1; j >= 0; j--) {
                                final Action.SeriesState ss = alSeries.get(j);

                                String sTimeField = "as_last_ok";
                                if (ss.iState == ActionUtils.STATE_ERR) {
                                    sTimeField = "as_last_err";
                                } else if (ss.iState == ActionUtils.STATE_FLIPFLOP) {
                                    sTimeField = "as_last_flipflop";
                                }

                                db.query("UPDATE action_states SET " + "as_value=" + ss.iState + ",as_lastchange="
                                        + lNow + "," + sTimeField + "=" + lNow + " WHERE as_file='"
                                        + Format.escSQL(a.sFile) + "' AND as_key='" + Format.escSQL(ss.getKey()) + "';");

                                if (db.getUpdateCount() < 1) {
                                    db.query("INSERT INTO action_states "
                                            + "(as_file, as_key, as_value, as_lastchange," + sTimeField + ") VALUES "
                                            + "('" + Format.escSQL(a.sFile) + "', '" + Format.escSQL(ss.getKey())
                                            + "', " + ss.iState + ", " + lNow + ", " + lNow + ");");
                                }

                                iUpdateCount++;
                            }
                        }
                    }
                }
            }
        }

    }

    @Override
    public void addResult(final Result r) throws Exception {
        synchronized (this.oLock) {
            if ((this.alEventActions.size() == 0) || (r == null)) {
                return;
            }

            for (int i = this.alEventActions.size() - 1; i >= 0; i--) {
                final Action a = this.alEventActions.get(i);

                if (DataSelect.matchResult(r, a.getTrigger()) != null) {
                    a.check();
                }
            }
        }
    }

    @Override
    public void addResult(final eResult r) throws Exception {
        synchronized (this.oLock) {
            if ((this.alEventActions.size() == 0) || (r == null)) {
                return;
            }

            for (int i = this.alEventActions.size() - 1; i >= 0; i--) {
                final Action a = this.alEventActions.get(i);

                if (DataSelect.matchResult(r, a.getTrigger()) != null) {
                    a.check();
                }
            }
        }
    }

    @Override
    public void addResult(final ExtResult r) throws Exception {
        // ignored, only watching out for Result or eResult
    }

    @Override
    public void addResult(final AccountingResult r) throws Exception {
        // ignored, only watching out for Result or eResult
    }

    @Override
    public void updateConfig(final MFarm farm) throws Exception {
        // ignore configurations
    }

    @Override
    public void notifyAppConfigChanged() {
        reload();
    }

}
