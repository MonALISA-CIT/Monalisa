package lia.Monitor.Store.Fast;

import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.eResult;
import lia.util.ntp.NTPDate;
import lia.web.utils.Formatare;

/**
 * @author costing
 *
 */
public class eDBWriter extends Writer implements AppConfigChangeListener {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(eDBWriter.class.getName());

    private String driverString = "org.postgresql.Driver";

    private boolean shouldAnalyzeTables = false;

    private final long lGraphTotalTime; // in milliseconds

    /**
     * @param _lGraphTotalTime
     * @param _sTableName
     */
    public eDBWriter(long _lGraphTotalTime, String _sTableName) {
        AppConfig.addNotifier(this);
        reloadConfig();
        lGraphTotalTime = _lGraphTotalTime;
        sTableName = _sTableName;

        mLock = _sTableName + "_lock";

        initDBStructure();
    }

    @Override
    public boolean setOnline(final long now) {
        final DB db = new DB();

        db.query("DELETE FROM monitor_tables_onlinetime WHERE mt_tablename='" + sTableName + "'");
        db.query("INSERT INTO monitor_tables_onlinetime (mt_tablename, online_since) VALUES ('" + sTableName + "',"
                + now + ")");

        analyzeTable();

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, " [ eDBW ] " + sTableName + " BECOME ONLINE :) ! [ "
                    + (NTPDate.currentTimeMillis() - now) + " ] ");
        }

        return super.setOnline(now);
    }

    @Override
    public boolean deleteAll() {
        final DB db = new DB();

        db.query("DROP TABLE " + sTableName);
        initDBStructure();
        analyzeTable();
        return true;
    }

    /*
     * Tries to create the table.
     */

    private final void initDBStructure() {
        boolean bCreate = false;

        final DB db = new DB();

        if (driverString.indexOf("mckoi") == -1) {
            if (driverString.indexOf("postgres") >= 0) {
                bCreate = db.syncUpdateQuery("CREATE TABLE " + sTableName + " (" + "rectime bigint," + "mfarm text,"
                        + "mcluster text," + "mnode text," + "mfunction text," + "mval text" + ") WITHOUT OIDS;", true);

                db.syncUpdateQuery("ALTER TABLE " + sTableName + " ALTER mfunction SET statistics 100;");
                db.syncUpdateQuery("ALTER TABLE " + sTableName + " ALTER mnode SET statistics 100;");
                db.syncUpdateQuery("ALTER TABLE " + sTableName + " ALTER rectime SET statistics 100;");
                db.syncUpdateQuery("ALTER TABLE " + sTableName + " ALTER mcluster SET statistics 10;");
                db.syncUpdateQuery("ALTER TABLE " + sTableName + " ALTER mfarm SET statistics 1;");
                db.syncUpdateQuery("ALTER TABLE " + sTableName + " ALTER mval SET statistics 0;");
            } else {
                bCreate = db.syncUpdateQuery("CREATE TABLE " + sTableName + " (" + "rectime BIGINT," + "mfarm VARCHAR(100),"
                        + "mcluster VARCHAR(100)," + "mnode VARCHAR (100)," + "mfunction VARCHAR(100),"
                        + "mval LONGBLOB" + ") TYPE=InnoDB;", true);
            }
        } else {
            bCreate = db.syncUpdateQuery("CREATE TABLE " + sTableName + " (" + "rectime BIGINT,"
                    + "mfarm VARCHAR(100) INDEX_NONE," + "mcluster VARCHAR(100) INDEX_NONE,"
                    + "mnode VARCHAR (100) INDEX_NONE," + "mfunction VARCHAR(100) INDEX_NONE," + "mval BLOB INDEX_NONE"
                    + ");", true);
        }

        if (bCreate) {
            db.syncUpdateQuery("CREATE INDEX " + sTableName + "_rectime_cnf_idx ON " + sTableName
                    + " (rectime, mcluster, mnode, mfunction);", true);
        }

        db.setReadOnly(true);
        
        db.query("SELECT online_since FROM monitor_tables_onlinetime WHERE mt_tablename='" + sTableName + "'");
        while (db.moveNext()) {
            onlineSince.set(db.getl("online_since"));
        }
        
        db.setReadOnly(false);
        
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, " [ eDBW ] " + sTableName + " online_since = " + onlineSince + " [ "
                    + new Date(onlineSince.get()) + " ] ");
        }

    }

    @Override
    public final void storeData(final Object o) {
        if (!isOnline()) {
            return;
        }

        if (o instanceof eResult) {
            storeData((eResult) o);
        }
    }

    private final void storeData(final eResult r) {
        if ((r == null) || (r.param_name == null) || (r.param_name.length <= 0)) {
            return;
        }

        final DB db = new DB();

        try {
            for (int i = 0; i < r.param_name.length; i++) {
                db.query("INSERT INTO " + sTableName + " (rectime, mfarm, mcluster, mnode, mfunction, mval) VALUES ("
                        + r.time + "," + (r.FarmName == null ? "NULL" : "'" + r.FarmName + "'") + ","
                        + (r.ClusterName == null ? "NULL" : "'" + r.ClusterName + "'") + ","
                        + (r.NodeName == null ? "NULL" : "'" + r.NodeName + "'") + "," + "'"
                        + Formatare.mySQLEscape(r.param_name[i]) + "', '"
                        + Formatare.mySQLEscape(serializeToString(r.param[i])) + "')");
            }
        } catch (Throwable t) {
            // ignore
        }
    }

    @Override
    public long getTotalTime() {
        return lGraphTotalTime;
    }

    private long lastCleanupTime = 0;

    private final long cleanupInterval = 60 * 1000; //10 min

    private long lLastAnalyze = 0; // will ANALYZE at the first run

    private static final long lAnalyzeInterval = 1000 * 60 * 30; // 30 minutes

    @Override
    public final boolean cleanup(final boolean bCleanHash) {
        final long now = NTPDate.currentTimeMillis();

        if ((lastCleanupTime + cleanupInterval) < now) {

            if ((now - lLastAnalyze) > lAnalyzeInterval) {
                analyzeTable();
            }
            // keep a fixed interval between runs, do not flood the db with cleanup requests
            lastCleanupTime = NTPDate.currentTimeMillis();
        }

        return true;
    }

    private final void reloadConfig() {
        try {
            try {
                driverString = AppConfig.getProperty("lia.Monitor.jdbcDriverString", "org.postgresql.Driver");
            } catch (Throwable t) {
                driverString = "org.postgresql.Driver";
            }
            try {
                shouldAnalyzeTables = Boolean
                        .valueOf(AppConfig.getProperty("lia.Monitor.shouldAnalyzeTables", "false")).booleanValue();
            } catch (Throwable t) {
                shouldAnalyzeTables = false;
            }

        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception reloading config", t);
        }
    }

    private final void analyzeTable() {
        if (shouldAnalyzeTables) {
            if (driverString.indexOf("postgres") >= 0) {
                if (!DB.isAutovacuumEnabled()) {
                    execMaintenance("ANALYZE " + sTableName + ";", false, null);
                }
            } else if (driverString.indexOf("mysql") >= 0) {
                execMaintenance("ANALYZE TABLE " + sTableName + ";", false, null);
            }
        }
        lLastAnalyze = NTPDate.currentTimeMillis();
    }

    @Override
    public void notifyAppConfigChanged() {
        reloadConfig();
    }

    @Override
    public int save() {
        return 0;
    }

    @Override
    public String toString() {
        return "eDBWriter(" + sTableName + ", " + iWriteMode + ")";
    }
}
