/*
 * $Id: DBWriter4.java 7600 2016-08-02 12:12:17Z costing $
 */
package lia.Monitor.Store.Fast;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.TransparentStoreFast;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;
import lia.util.ntp.NTPDate;

/**
 * This storage type has one table for each unique parameter name. It's a better representation for
 * the repository
 * 
 * @author costing
 */
public final class DBWriter4 extends Writer {

    /** Logger */
    private static final Logger logger = Logger.getLogger(DBWriter4.class.getName());

    /** The constant for the compact mode */
    public static final int DB4_MODE_COMPACT = 11;

    /** The constant for the raw mode */
    public static final int DB4_MODE_RAW = 12;

    /** For how long we will keep the data in this structure. Interval as milliseconds. */
    final long lGraphTotalTime;

    /** How many samples (if compacting data) should be stored for the given interval. */
    private final long lGraphSamples;

    /** Derived constant, the interval between two points in time, if compact is enabled */
    private final long lInterval;

    /** Whether or not this structure is in compact mode */
    private final boolean bCompact;

    /** When have we last executed a DELETE ? */
    long lLastDelete = 0;

    /** When have we last executed a CLUSTER ? */
    long lLastCluster = 0;

    /** When have we last executed a ANALYZE ? */
    long lLastAnalyze = 0;

    /** When have we last executed a REINDEX ? */
    long lLastReindex = 0;

    /**
     * @param _lGraphTotalTime
     * @param _lGraphSamples
     * @param _sTableName
     * @param _iWriteMode
     */
    public DBWriter4(final long _lGraphTotalTime, final long _lGraphSamples, final String _sTableName,
            final int _iWriteMode) {
        // copy all the values to local final fields
        lGraphTotalTime = _lGraphTotalTime;
        lGraphSamples = _lGraphSamples;
        sTableName = _sTableName;
        iWriteMode = _iWriteMode;

        bCompact = (iWriteMode == DB4_MODE_COMPACT) && (lGraphSamples > 0);

        if ((iWriteMode == DB4_MODE_COMPACT) && !bCompact) {
            logger.log(Level.WARNING, "For " + sTableName
                    + " you selected the compact mode but didn't specify a number of samples. Using raw mode instead.");
        }

        // generate derived data
        lInterval = bCompact ? lGraphTotalTime / lGraphSamples : -1;

        // initialize the hash of not-yet-written averaged data	
        m = null;

        if (bCompact && (lInterval < (1000 * 60 * 10))) {
            m = readPrevData(sTableName);
        }

        if (m == null) {
            m = new HashMap<Object, CacheElement>();
        } else {
            // the values were read from the database, CEs must be updated

            final Iterator<CacheElement> it = m.values().iterator();

            while (it.hasNext()) {
                final CacheElement ce = it.next();

                ce.lInterval = lInterval;
                ce.setWriter(this);
            }
        }

        final long lNow = NTPDate.currentTimeMillis();

        // start executing cleanup operations in 1/5 of the normal execution time from now
        lLastDelete = getSettingLong("lastDelete", lNow - ((getDeleteInterval() * 4) / 5), true);
        lLastAnalyze = getSettingLong("lastAnalyze", lNow - ((getAnalyzeInterval() * 4) / 5), true);
        lLastCluster = getSettingLong("lastCluster", lNow - ((getClusterInterval() * 4) / 5), true);
        lLastReindex = getSettingLong("lastReindex", lNow - ((getReindexInterval() * 4) / 5), true);
    }

    /**
     * How often should we delete old data? 
     * The answer is: 1/10 of the time interval, no more than 14 days though
     */
    private long getDeleteInterval() {
        long lRet = lGraphTotalTime / 10;

        if (lRet > (1000L * 60 * 60 * 24 * 14)) {
            lRet = 1000L * 60 * 60 * 24 * 14;
        }

        return lRet;
    }

    /**
     * How often should we analyze old data? 
     * The answer is: 1/5 of the time interval, no more often than 2 weeks
     */
    private long getAnalyzeInterval() {
        long lRet = lGraphTotalTime / 5;

        if (lRet > (1000L * 60 * 60 * 24 * 14)) {
            lRet = 1000L * 60 * 60 * 24 * 14;
        }

        return lRet;
    }

    /**
     * How often should we cluster old data? 
     * The answer is: 1/10 of the time interval, no more than 14 days
     */
    private long getClusterInterval() {
        long lRet = lGraphTotalTime / 10;

        if (lRet > (1000L * 60 * 60 * 24 * 14)) {
            lRet = 1000L * 60 * 60 * 24 * 14;
        }

        return lRet;
    }

    /**
     * How often should we reindex the tables? 
     * The answer is: 1/2 of the time interval, no more often than 1 month
     */
    private long getReindexInterval() {
        long lRet = lGraphTotalTime / 2;

        if (lRet > (1000L * 60 * 60 * 24 * 30)) {
            lRet = 1000L * 60 * 60 * 24 * 30;
        }

        return lRet;
    }

    /**
     * 
     */
    boolean bCleanupStarted = false;

    /*
     * (non-Javadoc)
     * 
     * @see lia.Monitor.Store.Fast.Writer#cleanup(boolean)
     */
    @Override
    public boolean cleanup(final boolean bCleanHash) {
        final long lNow = NTPDate.currentTimeMillis();

        if (lInterval > 0) {
            cleanHash(lInterval);
        }

        if (bCleanupStarted) {
            return false;
        }

        final boolean bDBMaintenance = !DB.isAutovacuumEnabled();

        boolean bLocalDelete = (lNow - lLastDelete) > getDeleteInterval();

        if (bLocalDelete) {
            try {
                final TransparentStoreFast store = (TransparentStoreFast) TransparentStoreFactory.getStore();

                // if we are in a repository environment, then we know for sure if we should delete some data
                bLocalDelete = (lNow - store.getStartTime()) > ((lGraphTotalTime / 100) * 95);
            } catch (Throwable t) {
                // ignore
            }
        }

        final boolean bDelete = bLocalDelete;

        final boolean bAnalyze = AppConfig.getb("lia.Monitor.Store.Fast.DBWriter4.vacuum_analyze", bDBMaintenance)
                && ((lNow - lLastAnalyze) > getAnalyzeInterval());

        final boolean bCluster = AppConfig.getb("lia.Monitor.Store.Fast.DBWriter4.cluster", bDBMaintenance)
                && ((lNow - lLastCluster) > getClusterInterval());

        final boolean bReindex = (lNow - lLastReindex) > getReindexInterval();

        int iOperationsCount = 0;

        if (bDelete) {
            iOperationsCount++;
        }
        if (bAnalyze) {
            iOperationsCount++;
        }
        if (bCluster) {
            iOperationsCount++;
        }
        if (bReindex) {
            iOperationsCount++;
        }

        if (iOperationsCount < 1) {
            return false;
        }

        new Thread() {
            @Override
            public void run() {
                bCleanupStarted = true;

                final String sTitle = "(ML) DB4 cleaner for " + sTableName + " (" + (new Date()) + ") - ";

                logger.log(Level.INFO, "DB4 CLEANUP START ('" + sTableName + "', del=" + bDelete + ", cluster="
                        + bCluster + ", analyze=" + bAnalyze + ", reindex=" + bReindex + ")");

                try {
                    int iDeletedTables = 0;
                    int iClusteredTables = 0;
                    int iAnalyzedTables = 0;
                    int iTestedTables = 0;
                    int iDropedTables = 0;
                    int iReindexedTables = 0;

                    final DB db = new DB();
                    
                    db.setReadOnly(true);
                    
                    db.query("SELECT tablename FROM pg_tables WHERE tablename LIKE '" + sTableName + "_%';");

                    final ArrayList<String> alTables = new ArrayList<String>();

                    while (db.moveNext()) {
                        alTables.add(db.gets(1));
                    }
                    
                    logger.log(Level.INFO, "DB4 CLEANUP ('"+sTableName+"'): "+alTables.size()+" tables to check");
                    
                    db.setReadOnly(false);

                    final long lDelThreshold = (lNow - lGraphTotalTime) / 1000;

                    long lTotalDelete = 0;

                    long lTotalActualTime = 0;

                    for (int i = alTables.size() - 1; i >= 0; i--) {

                        setName(sTitle + (alTables.size() - i) + " / " + alTables.size());

                        final String sName = alTables.get(i);

                        final long lStarted = NTPDate.currentTimeMillis();

                        boolean bLocalCluster = false;

                        boolean bLocalAnalyze = false;

                        boolean bSkipReindex = false;

                        if (bDelete) {
                            if (execMaintenance("DELETE FROM " + sName + " WHERE rectime<" + lDelThreshold + ";", true,
                                    db)) {
                                final int iUpdateCount = db.getUpdateCount();

                                lTotalDelete += iUpdateCount;

                                iDeletedTables++;

                                if (iUpdateCount > 1000000) {
                                    // after deleting lots of values we should do a CLUSTER on that table
                                    // (only if autovacuum is not enabled)
                                    bLocalCluster = bDBMaintenance;
                                } else if (iUpdateCount > 10000) {
                                    bLocalAnalyze = bDBMaintenance;
                                }

                                if (iUpdateCount == 0) {
                                    // probably the table is empty and we can just remove it now
                                    iTestedTables++;

                                    if (db.query("SELECT count(1) FROM " + sName + ";") && db.moveNext()
                                            && (db.geti(1, -1) == 0)) {
                                        db.syncUpdateQuery("DROP TABLE " + sName + ";");

                                        iDropedTables++;

                                        continue;
                                    }
                                }
                            } else {
                                // if the DELETE query failed it means that the table doesn't exist
                                // yet / any more, so don't try to execute other queries on it
                                continue;
                            }
                        }

                        if (bLocalCluster || bCluster) {
                            if (!execMaintenance("CLUSTER " + sName + "_time_idx ON " + sName + ";", true, db)) {
                                if (!execMaintenance("CREATE INDEX " + sName + "_time_idx ON " + sName + "(rectime);",
                                        true, db)) {
                                    continue;
                                }

                                execMaintenance("CLUSTER " + sName + "_time_idx ON " + sName + ";", false, db);
                            }

                            // force ANALYZE to update statistics on the contents of that table
                            bLocalAnalyze = true;

                            // after a CLUSTER we don't have to reindex
                            bSkipReindex = true;

                            iClusteredTables++;
                        }

                        if (bLocalAnalyze || bAnalyze) {
                            execMaintenance("VACUUM ANALYZE " + sName + ";", false, db);

                            iAnalyzedTables++;
                        }

                        if (bReindex && !bSkipReindex) {
                            execMaintenance("REINDEX TABLE " + sName + ";", false, db);

                            iReindexedTables++;
                        }

                        final long lEnded = NTPDate.currentTimeMillis();

                        lTotalActualTime += lEnded - lStarted;

                        // do not cause too much load, give the database some time to save the
                        // accumulated values
                        try {
                            Thread.sleep(((lEnded - lStarted) * 2) + (1000 * 60));
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }

                    if (bDelete) {
                        lLastDelete = NTPDate.currentTimeMillis();
                        setSetting("lastDelete", lLastDelete);
                    }

                    if (bCluster) {
                        lLastCluster = NTPDate.currentTimeMillis();
                        setSetting("lastCluster", lLastCluster);
                    }

                    if (bAnalyze || ((iAnalyzedTables * 10) > (iDeletedTables * 9))) {
                        // if we have actually analyzed more than 90% of the tables consider the job as done

                        lLastAnalyze = NTPDate.currentTimeMillis();
                        setSetting("lastAnalyze", lLastAnalyze);
                    }

                    if (bReindex || ((iClusteredTables * 10) > (iDeletedTables * 9))) {
                        // if we executed CLUSTER on more than 90% of the tables, REINDEX is no longer required

                        lLastReindex = NTPDate.currentTimeMillis();
                        setSetting("lastReindex", lLastReindex);
                    }

                    logger.log(Level.INFO, "DB4 CLEANUP ('" + sTableName + "', del=" + bDelete + " (" + lTotalDelete
                            + ", " + iDeletedTables + " tables touched), cluster=" + bCluster + " (" + iClusteredTables
                            + " operations), analyze=" + bAnalyze + " (" + iAnalyzedTables + " operations), reindex="
                            + bReindex + " (" + iReindexedTables + " operations), empty tables: " + iTestedTables
                            + " tests, " + iDropedTables + " droped, " + "took: "
                            + ((NTPDate.currentTimeMillis() - lNow) / 60000d) + " minutes ("
                            + (lTotalActualTime / 60000d) + " work minutes)");

                } finally {
                    bCleanupStarted = false;
                }
            }
        }.start();

        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see lia.Monitor.Store.Fast.Writer#getTotalTime()
     */
    @Override
    public long getTotalTime() {
        return lGraphTotalTime;
    }

    /*
     * (non-Javadoc)
     * 
     * @see lia.Monitor.Store.Fast.Writer#save()
     */
    @Override
    public int save() {
        return 0;
    }

    /*
     * (non-Javadoc)
     * 
     * @see lia.Monitor.Store.Fast.Writer#storeData(java.lang.Object)
     */
    @Override
    public void storeData(final Object o) {
        if ((o == null) || !(o instanceof Result)) {
            return;
        }

        final Result r = (Result) o;

        if ((r.param_name == null) || (r.param_name.length <= 0)) {
            return;
        }

        CacheElement ce;
        Integer id;

        if (bCompact) {
            for (int i = r.param_name.length - 1; i >= 0; i--) {
                id = IDGenerator.getId(r, i);

                if (id == null) {
                    continue;
                }

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
            }
        } else {
            for (int i = r.param_name.length - 1; i >= 0; i--) {
                insert(r.time, r, i, r.param[i], r.param[i], r.param[i]);
            }
        }
    }

    @Override
    public String toString() {
        return "DBWriter4(" + sTableName + ", " + iWriteMode + ")";
    }

}
