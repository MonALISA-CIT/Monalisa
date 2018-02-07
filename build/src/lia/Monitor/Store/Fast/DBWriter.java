/*
 * $Id: DBWriter.java 7541 2014-11-18 15:24:12Z costing $
 */
package lia.Monitor.Store.Fast;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.Result;
import lia.util.ntp.NTPDate;

/**
 * @author costing
 */
public final class DBWriter extends Writer implements AppConfigChangeListener {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(DBWriter.class.getName());

    private final String driverString;

    private volatile boolean shouldCompactTables = true;

    private final long lGraphTotalTime; // in

    // milliseconds

    private final long lGraphSamples; // integer,

    // >0

    private final AtomicLong lastCleanupTime = new AtomicLong(0L);

    private final long cleanupInterval;

    private final long lInterval;

    private final long lSaveInterval;

    private final DB db;

    private volatile boolean shouldAnalyzeTables = false;

    /**
     * @param _lGraphTotalTime
     * @param _lGraphSamples
     * @param _sTableName
     */
    public DBWriter(long _lGraphTotalTime, long _lGraphSamples, String _sTableName) {
        this(_lGraphTotalTime, _lGraphSamples, _sTableName, 0);
    }

    /**
     * @param _lGraphTotalTime
     * @param _lGraphSamples
     * @param _sTableName
     * @param _iWriteMode
     */
    public DBWriter(long _lGraphTotalTime, long _lGraphSamples, String _sTableName, int _iWriteMode) {
        AppConfig.addNotifier(this);
        reloadConfig();
        onlineSince = new AtomicLong(0);
        lGraphTotalTime = _lGraphTotalTime;
        lGraphSamples = _lGraphSamples;
        sTableName = _sTableName;

        String tmpDriverString = "org.postgresql.Driver";
        try {
            tmpDriverString = AppConfig.getProperty("lia.Monitor.jdbcDriverString", "org.postgresql.Driver");
        } catch (Throwable t) {
            tmpDriverString = "org.postgresql.Driver";
        }
        driverString = tmpDriverString;

        iWriteMode = _iWriteMode;

        if (lGraphSamples > 1) {
            lInterval = lGraphTotalTime / lGraphSamples;
        } else {
            lInterval = 0;
            iWriteMode = 1;
        }

        db = new DB();

        initDBStructure();

        readPreviousValues();

        long tmplSaveInterval = lInterval / 5;
        if (tmplSaveInterval < (1000 * 30)) {
            tmplSaveInterval = 1000 * 30;
        }
        lSaveInterval = tmplSaveInterval;

        long tmpCleanup = lGraphTotalTime / 100;
        if (tmpCleanup < (1000 * 60 * 2)) {
            tmpCleanup = 1000 * 60 * 2;
        }

        cleanupInterval = tmpCleanup;
    }

    @Override
    public boolean deleteAll() {
        db.syncUpdateQuery("DROP TABLE " + sTableName);
        initDBStructure();
        analyzeTable();
        return true;
    }

    @Override
    public boolean setOnline(final long now) {

        db.syncUpdateQuery("DELETE FROM monitor_tables_onlinetime WHERE mt_tablename='" + sTableName + "'");
        db.syncUpdateQuery("INSERT INTO monitor_tables_onlinetime (mt_tablename, online_since) VALUES ('" + sTableName
                + "'," + now + ")");
        analyzeTable();

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, " [ DBW ] " + sTableName + " BECOME ONLINE :) ! [ "
                    + (NTPDate.currentTimeMillis() - now) + " ] ");
        }

        return super.setOnline(now);
    }

    /*
     * Tries to create the table.
     */

    private final void initDBStructure() {
        boolean bShouldImport = false;
        logger.log(Level.INFO, " ShouldCompact Tables: " + shouldCompactTables);
        if (driverString.indexOf("mckoi") == -1) {
            if (driverString.indexOf("postgres") >= 0) {
                bShouldImport = db.syncUpdateQuery("CREATE TABLE " + sTableName + " (" + "rectime bigint,"
                        + "mfarm text," + "mcluster text," + "mnode text," + "mfunction text,"
                        + "mval double precision," + "mmin double precision," + "mmax double precision"
                        + ") WITHOUT OIDS;", true);

                db.query("ALTER TABLE " + sTableName + " ALTER mfunction SET statistics 100;");
                db.query("ALTER TABLE " + sTableName + " ALTER mnode SET statistics 100;");
                db.query("ALTER TABLE " + sTableName + " ALTER rectime SET statistics 100;");
                db.query("ALTER TABLE " + sTableName + " ALTER mcluster SET statistics 10;");
                db.query("ALTER TABLE " + sTableName + " ALTER mfarm SET statistics 1;");
                db.query("ALTER TABLE " + sTableName + " ALTER mval SET statistics 0;");
                db.query("ALTER TABLE " + sTableName + " ALTER mmax SET statistics 0;");
                db.query("ALTER TABLE " + sTableName + " ALTER mmin SET statistics 0;");
            } else {
                bShouldImport = db.syncUpdateQuery("CREATE TABLE " + sTableName + " (" + "rectime BIGINT,"
                        + "mfarm VARCHAR(100)," + "mcluster VARCHAR(100)," + "mnode VARCHAR (100),"
                        + "mfunction VARCHAR(100)," + "mval DOUBLE," + "mmin DOUBLE," + "mmax DOUBLE"
                        + ") TYPE=InnoDB;", true);
            }

            if (bShouldImport) {
                db.syncUpdateQuery("CREATE INDEX " + sTableName + "_rectime_cnf_idx ON " + sTableName
                        + " (rectime, mcluster, mnode, mfunction);", true);
            }
        } else {
            db.syncUpdateQuery("CREATE TABLE " + sTableName + " (" + "rectime BIGINT,"
                    + "mfarm VARCHAR(100) INDEX_NONE," + "mcluster VARCHAR(100) INDEX_NONE,"
                    + "mnode VARCHAR (100) INDEX_NONE," + "mfunction VARCHAR(100) INDEX_NONE,"
                    + "mval DOUBLE INDEX_NONE," + "mmin DOUBLE INDEX_NONE," + "mmax DOUBLE INDEX_NONE" + ");", true);

            if (shouldCompactTables) {
                long start = NTPDate.currentTimeMillis();
                logger.log(Level.WARNING, " Start Comapact [ " + sTableName + " ]");
                db.query("COMPACT TABLE " + sTableName + ";");
                logger.log(Level.WARNING, "COMPACT TABLE " + sTableName + " [ " + (NTPDate.currentTimeMillis() - start)
                        + " ] ");
            }
        }
        db.syncUpdateQuery("INSERT INTO saved_bprevdata (sp_name, sp_value) VALUES ('" + sTableName + "', NULL);", true);

        db.setReadOnly(true);
        
        db.query("SELECT online_since FROM monitor_tables_onlinetime WHERE mt_tablename='" + sTableName + "'", false);
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, " Executed : SELECT online_since FROM monitor_tables_onlinetime WHERE mt_tablename='" + sTableName + "'");
        }
        
        while (db.moveNext()) {
            onlineSince.set(db.getl("online_since"));
        }
        
        db.setReadOnly(false);
        
        logger.log(Level.INFO, " [ DBW ] " + sTableName + " online_since = " + onlineSince + " [ "
                + new Date(onlineSince.get()) + " ] ");
    }

    private final AtomicLong lLastSaved = new AtomicLong(0L);

    @Override
    public int save() {
        if ((iWriteMode != 0) || (lInterval < (1000 * 60 * 10))) {
            return 0;
        }

        if ((NTPDate.currentTimeMillis() - lLastSaved.get()) < lSaveInterval) {
            // System.out.println("skip : "+sTableName+" : "+lGraphTotalTime+" :
            // "+(NTPDate.currentTimeMillis() - lLastSaved));
            return 0;
        }

        lLastSaved.set(NTPDate.currentTimeMillis());

        HashMap<Object, CacheElement> mTemp;

        synchronized (mLock) {
            mTemp = new HashMap<Object, CacheElement>(m);
        }

        savePrevData(sTableName, mTemp);

        return 1;
    }

    /*
     * At program startup, the map is filled with CacheElement objects reprezenting the last known
     * values
     */
    private final void readPreviousValues() {
        synchronized (mLock) {
            m = null;

            if ((iWriteMode == 1) || (lInterval < (1000 * 60 * 10))) {
                m = new HashMap<Object, CacheElement>();
                return;
            }

            m = readPrevData(sTableName);

            if (m == null) {
                logger.log(Level.INFO, "DBWriter (" + sTableName
                        + ") : falling back to reading last values from the table");
                m = new HashMap<Object, CacheElement>();
                
                db.setReadOnly(true);

                db.query("SELECT * FROM " + sTableName + " ORDER BY rectime DESC LIMIT 10000;");
                
                db.setReadOnly(false);

                while (db.moveNext()) {
                    final String sKey = IDGenerator.generateKey(db.getns("mfarm"), db.getns("mcluster"),
                            db.getns("mnode"), db.getns("mfunction"));

                    if (m.get(sKey) == null) {
                        String v[] = new String[1];

                        v[0] = db.gets("mfunction");

                        final Result r = new Result(db.getns("mfarm"), db.getns("mcluster"), db.getns("mnode"), null, v);

                        r.param[0] = db.getd("mval");

                        final CacheElement ce = new CacheElement(lInterval, r, 0, db.getl("rectime"), false, this);

                        m.put(sKey, ce);
                    }
                }

            } else {
                // make sure the cache elements know about the real interval, just in case it was
                // changed in the config. file

                final Iterator<CacheElement> it = m.values().iterator();

                CacheElement ce;

                while (it.hasNext()) {
                    ce = it.next();

                    ce.lInterval = lInterval;
                    ce.setWriter(this);
                }
            }
        }
    }

    @Override
    public void storeData(final Object o) {
        if (!isOnline()) {
            return;
        }
        if (o instanceof Result) {
            storeData((Result) o);
        }
    }

    /*
     * A new sample was received. First the (Farm/Cluster/Node/Parameter) pair is looked up in the
     * map: - if a CacheElement exists, then this object is notified with the new data - if a
     * CacheElement does not exist, then a new one is created and added to the map
     */
    private final void storeData(final Result r) {
        if (!isOnline()) {
            return;
        }
        int i;

        // long lTimeNow = NTPDate.currentTimeMillis();

        if ((r == null) || (r.param_name == null) || (r.param_name.length <= 0)) {
            return;
        }

        for (i = 0; i < r.param_name.length; i++) {
            if (iWriteMode == 0) {
                final String sKey = IDGenerator.generateKey(r, i);

                if (sKey == null) {
                    continue;
                }

                CacheElement ce;

                synchronized (mLock) {
                    ce = m.get(sKey);
                }

                if (ce != null) {
                    ce.update(r, true);
                } else {
                    ce = new CacheElement(lInterval, r, i, r.time, true, this);

                    synchronized (mLock) {
                        m.put(sKey, ce);
                    }
                }
            } else {
                // do not mediate the data, write it to the database at once
                insert(r.time, r, i, r.param[i], r.param[i], r.param[i]);
            }
        }
    }

    @Override
    public long getTotalTime() {
        return lGraphTotalTime;
    }

    private final AtomicLong lLastAnalyze = new AtomicLong(0L); // will ANALYZE at the first
    // run

    private final static long lAnalyzeInterval = 1000 * 60 * 30; // 30 mins

    @Override
    public final boolean cleanup(final boolean bCleanHash) {
        final long now = NTPDate.currentTimeMillis();

        if (lInterval > 0) {
            cleanHash(lInterval);
        }

        if ((lastCleanupTime.get() + cleanupInterval) < now) {
            if ((now - lLastAnalyze.get()) > lAnalyzeInterval) {
                analyzeTable();
            }

            lastCleanupTime.set(NTPDate.currentTimeMillis());
        }

        return true;
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
        lLastAnalyze.set(NTPDate.currentTimeMillis());
    }

    private final void reloadConfig() {
        try {
            try {
                shouldCompactTables = Boolean.valueOf(AppConfig.getProperty("lia.Monitor.shouldCompactTables", "true"))
                        .booleanValue();
            } catch (Throwable t) {
                shouldCompactTables = true;
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

    @Override
    public void notifyAppConfigChanged() {
        reloadConfig();
    }

    @Override
    public String toString() {
        return "DBWriter(" + sTableName + ", " + iWriteMode + ")";
    }
}
