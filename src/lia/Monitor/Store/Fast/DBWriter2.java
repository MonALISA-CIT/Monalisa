/*
 * $Id: DBWriter2.java 7541 2014-11-18 15:24:12Z costing $
 */
package lia.Monitor.Store.Fast;

import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;
import lia.util.ntp.NTPDate;

/**
 * @author costing
 *
 */
public final class DBWriter2 extends Writer {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(DBWriter2.class.getName());

    private final String driverString = AppConfig.getProperty("lia.Monitor.jdbcDriverString", "org.postgresql.Driver")
            .trim();

    private static boolean shouldCompactTables = Boolean.valueOf(
            AppConfig.getProperty("lia.Monitor.shouldCompactTables", "true")).booleanValue();

    private static boolean shouldReadLastValues = Boolean.valueOf(
            AppConfig.getProperty("lia.Monitor.Store.shouldReadLastValues", "true")).booleanValue();

    private final long lGraphTotalTime; // in milliseconds

    private final long lGraphSamples; // integer, >0

    private long lastCleanupTime = 0;

    private long cleanupInterval = 10 * 60 * 1000; //10 min

    private long lInterval;

    private long lSaveInterval;

    /**
     * @param _lGraphTotalTime
     * @param _lGraphSamples
     * @param _sTableName
     */
    public DBWriter2(long _lGraphTotalTime, long _lGraphSamples, String _sTableName) {
        this(_lGraphTotalTime, _lGraphSamples, _sTableName, 0);
    }

    /**
     * @param _lGraphTotalTime
     * @param _lGraphSamples
     * @param _sTableName
     * @param _iWriteMode
     */
    public DBWriter2(long _lGraphTotalTime, long _lGraphSamples, String _sTableName, int _iWriteMode) {
        lGraphTotalTime = _lGraphTotalTime;
        lGraphSamples = _lGraphSamples;
        sTableName = _sTableName;

        iWriteMode = _iWriteMode;

        if (lGraphSamples > 1) {
            lInterval = lGraphTotalTime / lGraphSamples;
        } else {
            lInterval = 0;
            iWriteMode = 6;
        }

        initDBStructure();

        readPreviousValues();

        cleanupInterval = lGraphTotalTime / 100;
        if (cleanupInterval < (1000 * 60 * 2)) {
            cleanupInterval = 1000 * 60 * 2;
        }

        lastCleanupTime = NTPDate.currentTimeMillis() - (cleanupInterval / 2);

        lSaveInterval = lInterval / 5;
        if (lSaveInterval < (1000 * 30)) {
            lSaveInterval = 1000 * 30;
        }
    }

    /*
     * Tries to create the table.
     */

    private final void initDBStructure() {
        boolean bShouldImport = false;
        logger.log(Level.INFO, " ShouldCompact Tables: " + shouldCompactTables);

        DB db = new DB();

        if (driverString.indexOf("mckoi") == -1) {
            if (driverString.indexOf("postgres") >= 0) {
                bShouldImport = db.syncUpdateQuery("CREATE TABLE " + sTableName + " (" + "rectime bigint," + "id int,"
                        + "mval real," + "mmin real," + "mmax real" + ") WITHOUT OIDS;", true);

                db.query("ALTER TABLE " + sTableName + " ALTER id SET statistics 100;");
                db.query("ALTER TABLE " + sTableName + " ALTER rectime SET statistics 100;");
                db.query("ALTER TABLE " + sTableName + " ALTER mval SET statistics 0;");
                db.query("ALTER TABLE " + sTableName + " ALTER mmax SET statistics 0;");
                db.query("ALTER TABLE " + sTableName + " ALTER mmin SET statistics 0;");
            } else {
                bShouldImport = db.syncUpdateQuery("CREATE TABLE " + sTableName + " (" + "rectime BIGINT," + "id int,"
                        + "mval FLOAT," + "mmin FLOAT," + "mmax FLOAT" + ") TYPE=InnoDB;", true);
            }

            if (bShouldImport) {
                db.syncUpdateQuery("CREATE INDEX " + sTableName + "_rectime_id_idx ON " + sTableName
                        + " (id, rectime);", true);
            }
        } else {
            db.syncUpdateQuery("CREATE TABLE " + sTableName + " (" + "rectime BIGINT," + "id int,"
                    + "mval DOUBLE INDEX_NONE," + "mmin DOUBLE INDEX_NONE," + "mmax DOUBLE INDEX_NONE" + ");", true);

            if (shouldCompactTables) {
                long start = NTPDate.currentTimeMillis();
                logger.log(Level.WARNING, " Start Comapact [ " + sTableName + " ]");
                db.query("COMPACT TABLE " + sTableName + ";");
                logger.log(Level.WARNING, "COMPACT TABLE " + sTableName + " [ " + (NTPDate.currentTimeMillis() - start)
                        + " ] ");
            }
        }
    }

    @Override
    public int save() {
        return 0;
    }

    /*
     * At program startup, the map is filled with CacheElement objects reprezenting the last known values
     */
    private final void readPreviousValues() {
        DB db = new DB();

        synchronized (mLock) {
            m = null;

            if ((iWriteMode == 6) || (lInterval < (1000 * 60 * 10))) {
                m = new HashMap<Object, CacheElement>();
                return;
            }

            m = readPrevData(sTableName);

            if (m == null) {
                logger.log(Level.INFO, "DBWriter2 (" + sTableName
                        + ") : falling back to reading last values from the table");
                m = new HashMap<Object, CacheElement>();

                if (shouldReadLastValues) {
                	db.setReadOnly(true);
                	
                    db.query("SELECT id,mval,rectime FROM " + sTableName + " ORDER BY rectime DESC LIMIT 10000;");
                    
                    db.setReadOnly(false);

                    while (db.moveNext()) {
                        final Integer id = Integer.valueOf(db.geti(1));

                        if (m.get(id) == null) {
                            final String v[] = new String[1];

                            final IDGenerator.KeySplit split = IDGenerator.getKeySplit(id);

                            if (split == null) {
                                continue;
                            }

                            v[0] = split.FUNCTION;

                            final Result r = new Result(split.FARM, split.CLUSTER, split.NODE, null, v);

                            r.param[0] = db.getd(2);

                            final CacheElement ce = new CacheElement(lInterval, r, 0, db.getl(3), false, this);

                            m.put(id, ce);
                        }
                    }
                }

                save();

            } else {
                // make sure the cache elements know about the real interval, just in case it was changed in the config. file
                Iterator<CacheElement> it = m.values().iterator();

                while (it.hasNext()) {
                    CacheElement ce = it.next();

                    ce.lInterval = lInterval;
                    ce.setWriter(this);
                }
            }
        }
    }

    @Override
    public void storeData(final Object o) {
        if (o instanceof Result) {
            storeData((Result) o);
        }
    }

    /*
     * A new sample was received.
     * 
     * First the (Farm/Cluster/Node/Parameter) pair is looked up in the map:
     * - if a CacheElement exists, then this object is notified with the new data
     * - if a CacheElement does not exist, then a new one is created and added to the map
     */
    private final void storeData(final Result r) {
        int i;

        if ((r == null) || (r.param_name == null) || (r.param_name.length <= 0)) {
            return;
        }

        for (i = 0; i < r.param_name.length; i++) {
            if (iWriteMode == 5) {
                // mediated data
                final Integer id = IDGenerator.getId(r, i);

                if (id == null) {
                    continue;
                }

                CacheElement ce;

                synchronized (mLock) {
                    ce = m.get(id);
                }

                if (ce != null) {
                    ce.update(r, true);
                } else {
                    ce = new CacheElement(lInterval, r, i, r.time, true, this);

                    synchronized (mLock) {
                        m.put(id, ce);
                    }
                }
            } else {
                // unmediated data
                insert(r.time, r, i, r.param[i], r.param[i], r.param[i]);
            }
        }
    }

    @Override
    public long getTotalTime() {
        return lGraphTotalTime;
    }

    private long lDelCount = 0;

    private long lAnalyzeCount = 0;

    private static final int FULL_VACUUM_LIMIT = 1000000;

    @Override
    public final boolean cleanup(final boolean bCleanHash) {
        final long now = NTPDate.currentTimeMillis();

        if (lInterval > 0) {
            cleanHash(lInterval);
        }

        DB db = new DB();

        if ((lastCleanupTime + cleanupInterval) < now) {
            if (execMaintenance("DELETE FROM " + sTableName + " WHERE rectime<" + (now - lGraphTotalTime) + ";", true,
                    db)) {
                lDelCount += db.getUpdateCount();
                lAnalyzeCount += db.getUpdateCount();
            }

            if (shouldCompactTables && (lAnalyzeCount > (FULL_VACUUM_LIMIT / 10))) {
                if ((driverString.indexOf("postgres") >= 0) && !DB.isAutovacuumEnabled()) {
                    execMaintenance("VACUUM ANALYZE " + sTableName + ";", false, db);
                }

                if (driverString.indexOf("mysql") >= 0) {
                    execMaintenance("ANALYZE TABLE " + sTableName + ";", false, db);
                }

                lAnalyzeCount = 0;
            }

            if (lDelCount > FULL_VACUUM_LIMIT) {
                if (lAnalyzeCount == 0) {
                    try {
                        Thread.sleep(20000);
                    } catch (Exception e) {
                        // ignore
                    }
                }

                if (driverString.indexOf("mckoi") != -1) {
                    execMaintenance("COMPACT TABLE " + sTableName + ";", false, db);
                }

                if ((driverString.indexOf("postgres") >= 0) && !DB.isAutovacuumEnabled()) {
                    execMaintenance("VACUUM FULL ANALYZE " + sTableName + ";", false, db);
                }

                if (driverString.indexOf("mysql") >= 0) {
                    execMaintenance("OPTIMIZE TABLE " + sTableName + ";", false, db);
                }

                lDelCount = 0;
            }

            lastCleanupTime = NTPDate.currentTimeMillis();
        }

        return true;
    }

    @Override
    public String toString() {
        return "DBWriter2(" + sTableName + ", " + iWriteMode + ")";
    }
}
