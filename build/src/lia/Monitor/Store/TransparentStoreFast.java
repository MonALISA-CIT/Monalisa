/*
 * $Id: TransparentStoreFast.java 7561 2015-02-13 11:54:07Z costing $
 */
package lia.Monitor.Store;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.DataCache.DataSelect;
import lia.Monitor.Store.Fast.AccountingWriter;
import lia.Monitor.Store.Fast.DB;
import lia.Monitor.Store.Fast.DBWriter;
import lia.Monitor.Store.Fast.DBWriter2;
import lia.Monitor.Store.Fast.DBWriter3;
import lia.Monitor.Store.Fast.DBWriter4;
import lia.Monitor.Store.Fast.IDGenerator;
import lia.Monitor.Store.Fast.MemWriter;
import lia.Monitor.Store.Fast.TempMemWriter;
import lia.Monitor.Store.Fast.TempMemWriter3;
import lia.Monitor.Store.Fast.TempMemWriter4;
import lia.Monitor.Store.Fast.TempMemWriterInterface;
import lia.Monitor.Store.Fast.Writer;
import lia.Monitor.Store.Fast.eDBWriter;
import lia.Monitor.Store.Fast.Replication.ReplicationManager;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.ExtendedResult;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.ResultUtils;
import lia.Monitor.monitor.TimestampedResult;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.StringFactory;
import lia.util.ntp.NTPDate;
import lia.util.threads.MonALISAExecutors;
import lia.web.utils.Formatare;

/**
 * TransparentStoreInt interface implementation
 * 
 * @author costing
 * @author ramiro
 */
public final class TransparentStoreFast implements TransparentStoreInt, Runnable, AppConfigChangeListener {

    /**
     * Java logger
     */
    static final Logger logger = Logger.getLogger(TransparentStoreFast.class.getName());

    private static final String deleteLastConf = "DELETE FROM monitor_n_conf WHERE conftime < ";

    private static final long LAST_CONF_MAXTIME = AppConfig.getl(
            "lia.Monitor.Store.TransparentStoreFast.LAST_CONF_MAXTIME", 60 * 24 * 30) * 1000L * 60;

    private static final boolean SHOULD_STORE_CONF = AppConfig.getb(
            "lia.Monitor.Store.TransparentStoreFast.SHOULD_STORE_CONF", false);

    private static final boolean SHOULD_KEEP_MEM_CONF;

    private static double memoryQueryThreshold = AppConfig.getd(
            "lia.Monitor.Store.TransparentStoreFast.memoryQueryThreshold", 10);

    static {
        boolean bMemoryConfs = true;

        try {
            if (lia.web.servlets.web.display.sRepositoryVersion.length() > 0) {
                bMemoryConfs = false;
            }
        } catch (Throwable t) {
            // ok, so this is not a repository, the configuration will be kept in memory by default
        }

        bMemoryConfs = AppConfig.getb("lia.Monitor.Store.TransparentStoreFast.SHOULD_KEEP_MEM_CONF", bMemoryConfs);

        SHOULD_KEEP_MEM_CONF = bMemoryConfs;

        logger.log(Level.INFO, " [ TSF ] SHOULD_STORE_CONF = " + SHOULD_STORE_CONF + ", SHOULD_KEEP_MEM_CONF = "
                + SHOULD_KEEP_MEM_CONF);
    }

    private static long lLastConfCleanupTime = 0;

    private static final String driverString = AppConfig.getProperty("lia.Monitor.jdbcDriverString",
            "org.postgresql.Driver").trim();

    /**
     * If this is !=null then values will be written to the plain text file
     */
    public ResultFileLogger rfl = null;

    /**
     * All storage elements
     */
    Writer[] vWriters;

    /**
     * Locks, one per each storage element
     */
    String[] vWriterLocks;

    /**
     * For each storage element, what is the base name for the tables
     */
    String[] sTableNames;

    /**
     * This thread will execute periodic cleanup actions
     */
    private ScheduledFuture<?> cleanupThread = null;

    /**
     * Storage element types
     */
    int[] vTypes;

    /**
     * Samples (for mediated tables)
     */
    long[] vSamples;

    /**
     * Memory writer
     */
    private Writer tmw;

    private boolean bSomeRotatingStructures = false;

    /**
     * How many writers are defined
     */
    private int definedWritersLen;

    private HashMap<String, Writer> hmWriters;

    private final ConcurrentHashMap<String, lia.ws.WSConf> hmFarmConfig = new ConcurrentHashMap<String, lia.ws.WSConf>();

    private final ReadWriteLock rwConfigLock = new ReentrantReadWriteLock();

    private final Lock configReadLock = rwConfigLock.readLock();
    private final Lock configWriteLock = rwConfigLock.writeLock();

    /**
     * The only constructor, loads the configuration. It is package protected to make sure only
     * {@link TransparentStoreFactory} can create it.
     */
    TransparentStoreFast() {
        load(false);
    }

    /**
     * Get the memory buffer
     * 
     * @return the memory buffer
     */
    public TempMemWriterInterface getTempMemWriter() {
        return (TempMemWriterInterface) tmw;
    }

    /**
     * Update last seen column in the monitor_ids table
     * 
     * @author costing
     * @since Jun 3, 2008
     */
    static final class MonitorIDsUpdater {

        /**
         * This virtual writer updates the last seen time for the ID entries
         * 
         * @param o
         */
        public static void addSample(final Object o) {
            if ((o == null) || !(o instanceof Result)) {
                return;
            }

            final Result r = (Result) o;

            for (int i = 0; (i < r.param_name.length) && (i < r.param.length); i++) {
                final Integer id = IDGenerator.getId(r, i);

                if (id != null) {
                    IDGenerator.updateLastSeen(id, r.time, r.param[i]);
                }
            }
        }
    }

    private MonitorIDsUpdater miUpdater = null;

    @Override
    public void reload() {
        try {
            load(true);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Reloading failed with : ", t);
        }
    }

    private final Vector<monPredicate> vDontStore = new Vector<monPredicate>();
    private final Vector<monPredicate> vStoreOnly = new Vector<monPredicate>();
    private final Vector<monPredicate> vStoreMemory = new Vector<monPredicate>();

    /**
     * Set the exclusion list, predicates that filter the data we don't want stored in the database.
     * @param vAccept 
     * @param vReject 
     * 
     * @see lia.Monitor.Store.TransparentStoreInt#setStoreFilters(Vector, Vector)
     * @see #setStoreFilters(Vector, Vector, Vector)
     */
    @Override
    public void setStoreFilters(final Vector<monPredicate> vAccept, final Vector<monPredicate> vReject) {
        setStoreFilters(vAccept, vReject, null);
    }

    /**
     * Set the exclusion list, predicates that filter the data we don't want stored in the database.
     * @param vAccept 
     * @param vReject 
     * @param vExtraMemory
     * 
     * @see lia.Monitor.Store.TransparentStoreInt#setStoreFilters(Vector, Vector)
     */
    public void setStoreFilters(final Vector<monPredicate> vAccept, final Vector<monPredicate> vReject,
            final Vector<monPredicate> vExtraMemory) {
        configWriteLock.lock();

        try {
            vDontStore.clear();

            if (vReject != null) {
                vDontStore.addAll(vReject);
            }

            vStoreOnly.clear();

            vStoreMemory.clear();

            if (vAccept != null) {
                vStoreOnly.addAll(vAccept);
                vStoreMemory.addAll(vAccept);
            }

            if (vExtraMemory != null) {
                vStoreMemory.addAll(vExtraMemory);
            }
        } finally {
            configWriteLock.unlock();
        }
    }

    /**
     * Set the exclusion list based on the configuration properties
     * 
     * @see #setDontStore(Vector)
     */
    private void setDontStore() {
        String[] vsDontStore = AppConfig.getVectorProperty("lia.Monitor.JiniClient.Store.dontStore");
        final Vector<monPredicate> vNewDontStore = new Vector<monPredicate>();

        if (vsDontStore != null) {
            for (String element : vsDontStore) {
                monPredicate pred = lia.web.utils.Formatare.toPred(element);

                vNewDontStore.add(pred);

                logger.log(Level.INFO, "don't store : " + pred);
            }
        }

        vsDontStore = AppConfig.getVectorProperty("lia.Monitor.Store.dontStore");
        if (vsDontStore != null) {
            for (String element : vsDontStore) {
                final monPredicate pred = lia.web.utils.Formatare.toPred(element);

                vNewDontStore.add(pred);

                logger.log(Level.INFO, "don't store (new): " + pred);
            }
        }

        final String[] vsStoreOnly = AppConfig.getVectorProperty("lia.Monitor.Store.storeOnly");
        final Vector<monPredicate> vNewStoreOnly = new Vector<monPredicate>();

        if (vsStoreOnly != null) {
            for (String element : vsStoreOnly) {
                final monPredicate pred = lia.web.utils.Formatare.toPred(element);

                vNewStoreOnly.add(pred);

                logger.log(Level.INFO, "store only : " + pred);
            }
        }

        final String[] vsStoreMemory = AppConfig.getVectorProperty("lia.Monitor.Store.storeMemory");
        final Vector<monPredicate> vNewStoreMemory = new Vector<monPredicate>();

        if (vsStoreMemory != null) {
            for (String element : vsStoreMemory) {
                final monPredicate pred = lia.web.utils.Formatare.toPred(element);

                vNewStoreMemory.add(pred);

                logger.log(Level.INFO, "extra store in memory only : " + pred);
            }
        }

        setStoreFilters(vNewStoreOnly, vNewDontStore, vNewStoreMemory);
    }

    /*
     * Initialize the storage based on the properties file
     */
    private void load(final boolean bIsReloading) {
        configWriteLock.lock();

        try {
            AppConfig.removeNotifier(this);

            bSomeRotatingStructures = false;

            hmWriters = new HashMap<String, Writer>();

            hmFarmConfig.clear();

            if (tmw == null) {
                final int option = AppConfig.geti("lia.Monitor.Store.TransparentStoreFast.mem_writer_version", 3);

                logger.log(Level.INFO, "Using memory buffer version : " + option);

                switch (option) {
                case 1: {
                    tmw = new TempMemWriter();
                    break;
                }
                case 4: {
                    tmw = new TempMemWriter4();
                    break;
                }
                default: {
                    tmw = new TempMemWriter3();
                }
                }
            }

            tmw.setConstaints(AppConfig.getProperty("lia.Monitor.Store.membuffer.constraints"),
                    AppConfig.getProperty("lia.Monitor.Store.membuffer.reject"));

            if (AppConfig.getb("lia.Monitor.Store.TransparentStoreFast.force_monitor_ids_update", false)) {
                miUpdater = new MonitorIDsUpdater();
            } else {
                miUpdater = null;
            }

            try {
                int nr = AppConfig.geti("lia.Monitor.Store.TransparentStoreFast.web_writes", -1);

                if (nr == -1) {
                    nr = 3;
                    logger.log(Level.WARNING, "\n\n No Tables defined. Using a default hardcoded configuration.\n\n");

                    System.setProperty("lia.Monitor.Store.TransparentStoreFast.writer_0.total_time", "10800");
                    System.setProperty("lia.Monitor.Store.TransparentStoreFast.writer_0.table_name", "monitor_s_3hour");
                    System.setProperty("lia.Monitor.Store.TransparentStoreFast.writer_0.writemode", "1");

                    System.setProperty("lia.Monitor.Store.TransparentStoreFast.writer_1.total_time", "10800");
                    System.setProperty("lia.Monitor.Store.TransparentStoreFast.writer_1.table_name",
                            "monitor_s_e_3hour");
                    System.setProperty("lia.Monitor.Store.TransparentStoreFast.writer_1.writemode", "2");

                    System.setProperty("lia.Monitor.Store.TransparentStoreFast.writer_2.total_time", "36000");
                    System.setProperty("lia.Monitor.Store.TransparentStoreFast.writer_2.table_name", "monitor_s_10hour");
                    System.setProperty("lia.Monitor.Store.TransparentStoreFast.writer_2.samples", "60");
                    System.setProperty("lia.Monitor.Store.TransparentStoreFast.writer_2.writemode", "0");
                }

                if (((nr == 0) || AppConfig.getb("lia.Monitor.JStore.logResults", false))
                        && !AppConfig.getb("lia.Monitor.JStore.disable_internal_results_logger", false)) {
                    if (rfl == null) { // if no storage is defined then try to log the result for a
                                       // while
                        rfl = ResultFileLogger.getLoggerInstance(); // use existent instance if any
                    }
                }

                if (nr == 0) {
                    TransparentStoreFactory.setMemoryStoreOnly(true);
                }

                definedWritersLen = nr;
                vWriters = new Writer[2 * nr];
                vWriterLocks = new String[2 * nr];
                vTypes = new int[2 * nr];
                sTableNames = new String[2 * nr];
                vSamples = new long[2 * nr];

                if (TransparentStoreFactory.isMemoryStoreOnly()) {
                    return;
                }

                DB db = new DB();

                if (driverString.indexOf("postgres") >= 0) {
                    db.syncUpdateQuery(
                            "CREATE TABLE monitor_n_conf (conftime BIGINT, mfarm VARCHAR(100), conf text) WITHOUT OIDS;",
                            true);
                    db.syncUpdateQuery("CREATE TABLE saved_bprevdata (sp_name text primary key, sp_value bytea) WITHOUT OIDS;",
                            true);
                } else {
                    db.syncUpdateQuery("CREATE TABLE monitor_n_conf (conftime BIGINT, mfarm VARCHAR(100), conf BLOB)", true);
                    db.syncUpdateQuery(
                            "CREATE TABLE saved_bprevdata (sp_name varchar(50) primary key, sp_value blob) TYPE=InnoDB;",
                            true);
                    db.syncUpdateQuery("ALTER TABLE saved_bprevdata MODIFY sp_value LONGBLOB", true);
                }

                db.syncUpdateQuery(
                        "CREATE TABLE monitor_tables_onlinetime (mt_tablename varchar(50) primary key, online_since bigint default 0)",
                        true);
                db.syncUpdateQuery("CREATE TABLE monitor_tables (mt_tablename varchar(50) primary key, mt_status int default 0)",
                        true);
                db.syncUpdateQuery("UPDATE monitor_tables SET mt_status=mt_status-1 WHERE mt_status>=0");

                List<Integer> lNewTables = new ArrayList<Integer>();
                List<String> lAllTables = new ArrayList<String>();
                long now = NTPDate.currentTimeMillis();

                for (int i = 0; i < nr; i++) {
                    long lTotalTime = Long.parseLong(AppConfig.getProperty(
                            "lia.Monitor.Store.TransparentStoreFast.writer_" + i + ".total_time", "0").trim()) * 1000;
                    long lSamples = Long.parseLong(AppConfig.getProperty(
                            "lia.Monitor.Store.TransparentStoreFast.writer_" + i + ".samples", "1").trim());
                    String sTableName = AppConfig.getProperty(
                            "lia.Monitor.Store.TransparentStoreFast.writer_" + i + ".table_name", "writer_" + i).trim();

                    int iWriteMode = Integer.parseInt(AppConfig.getProperty(
                            "lia.Monitor.Store.TransparentStoreFast.writer_" + i + ".writemode", "0").trim());

                    final String sAcceptConstraints = AppConfig
                            .getProperty("lia.Monitor.Store.TransparentStoreFast.writer_" + i + ".constraints");
                    final String sRejectConstraints = AppConfig
                            .getProperty("lia.Monitor.Store.TransparentStoreFast.writer_" + i + ".reject");

                    vTypes[i] = iWriteMode;
                    vTypes[i + nr] = iWriteMode;
                    vSamples[i] = lSamples;
                    vSamples[i + nr] = lSamples;

                    if ((lTotalTime > 0) && (sTableName.length() > 0) && ((iWriteMode == 0) || (iWriteMode == 1))) {
                        try {

                            int iImport = Integer.parseInt(AppConfig.getProperty(
                                    "lia.Monitor.Store.TransparentStoreFast.writer_" + i + ".import", "1").trim());

                            String sBackupTableName = sTableName + "_backup_rrd";
                            vWriters[i] = new DBWriter(lTotalTime, lSamples, sTableName, iWriteMode);
                            vWriters[i + nr] = new DBWriter(lTotalTime, lSamples, sBackupTableName, iWriteMode);

                            bSomeRotatingStructures = true;

                            vWriters[i].setConstaints(sAcceptConstraints, sRejectConstraints);
                            vWriters[i + nr].setConstaints(sAcceptConstraints, sRejectConstraints);

                            long lpw = vWriters[i].getOnlineTime();
                            long lbw = vWriters[i + nr].getOnlineTime();

                            if (lpw >= lbw) {
                                vWriters[i].setOnline((lpw == 0) ? now : lpw);
                                vWriters[i + nr].setOffline();
                            } else {
                                vWriters[i].setOffline();
                                vWriters[i + nr].setOnline((lbw == 0) ? now : lbw);
                            }

                            sTableNames[i] = sTableName;
                            sTableNames[i + nr] = sBackupTableName;
                            vWriterLocks[i] = "lock_" + i;
                            vWriterLocks[i + nr] = "lock_" + i;

                            db.query("SELECT * FROM monitor_tables WHERE mt_tablename='" + sTableName + "';");

                            if (db.moveNext()) {
                                db.query("UPDATE monitor_tables SET mt_status=1 WHERE mt_tablename='" + sTableName
                                        + "';");
                                lAllTables.add(sTableName);
                            } else {
                                logger.log(Level.INFO, "New table detected : " + sTableName);
                                db.query("INSERT INTO monitor_tables (mt_tablename, mt_status) VALUES ('" + sTableName
                                        + "', 1);");
                                if (iImport > 0) {
                                    lNewTables.add(Integer.valueOf(i));
                                }
                            }

                            db.query("SELECT * FROM monitor_tables WHERE mt_tablename='" + sBackupTableName + "';");

                            if (db.moveNext()) {
                                db.query("UPDATE monitor_tables SET mt_status=1 WHERE mt_tablename='"
                                        + sBackupTableName + "';");
                                lAllTables.add(sBackupTableName);
                            } else {
                                logger.log(Level.INFO, "New table detected : " + sBackupTableName);
                                db.query("INSERT INTO monitor_tables (mt_tablename, mt_status) VALUES ('"
                                        + sBackupTableName + "', 1);");
                                if (iImport > 0) {
                                    lNewTables.add(Integer.valueOf(i + nr));
                                }
                            }

                            db.query("SELECT * FROM monitor_tables WHERE mt_tablename='" + sTableName + "';");

                            if (db.moveNext()) {
                                db.query("UPDATE monitor_tables SET mt_status=1 WHERE mt_tablename='" + sTableName
                                        + "';");
                                lAllTables.add(sTableName);
                            } else {
                                logger.log(Level.INFO, "New table detected : " + sTableName);
                                db.query("INSERT INTO monitor_tables (mt_tablename, mt_status) VALUES ('" + sTableName
                                        + "', 1);");
                                if (iImport > 0) {
                                    lNewTables.add(Integer.valueOf(i));
                                }
                            }

                            db.query("SELECT * FROM monitor_tables WHERE mt_tablename='" + sBackupTableName + "';");

                            if (db.moveNext()) {
                                db.query("UPDATE monitor_tables SET mt_status=1 WHERE mt_tablename='"
                                        + sBackupTableName + "';");
                                lAllTables.add(sTableName);
                            } else {
                                logger.log(Level.INFO, "New table detected : " + sTableName);
                                db.query("INSERT INTO monitor_tables (mt_tablename, mt_status) VALUES ('"
                                        + sBackupTableName + "', 1);");
                                if (iImport > 0) {
                                    lNewTables.add(Integer.valueOf(i));
                                }
                            }

                            hmWriters.put(sTableName, vWriters[i]);
                            hmWriters.put(sBackupTableName, vWriters[i + nr]);

                        } catch (Throwable e) {
                            vWriters[i] = null;
                            sTableNames[i] = null;
                            vWriters[i + nr] = null;
                            sTableNames[i + nr] = null;
                            logger.log(Level.SEVERE, "Exception in initializing the store element '" + sTableName
                                    + "' : ", e);
                        }
                    } else if ((lTotalTime > 0) && (sTableName.length() > 0) && (iWriteMode == 2)) {
                        try {
                            String sBackupTableName = sTableName + "_backup_rrd";
                            vWriters[i] = new eDBWriter(lTotalTime, sTableName);
                            vWriters[i + nr] = new eDBWriter(lTotalTime, sBackupTableName);

                            bSomeRotatingStructures = true;

                            vWriters[i].setConstaints(sAcceptConstraints, sRejectConstraints);
                            vWriters[i + nr].setConstaints(sAcceptConstraints, sRejectConstraints);

                            long lpw = vWriters[i].getOnlineTime();
                            long lbw = vWriters[i + nr].getOnlineTime();

                            if (lpw >= lbw) {
                                vWriters[i].setOnline((lpw == 0) ? now : lpw);
                                vWriters[i + nr].setOffline();
                            } else {
                                vWriters[i].setOffline();
                                vWriters[i + nr].setOnline((lbw == 0) ? now : lbw);
                            }

                            sTableNames[i] = sTableName;
                            sTableNames[i + nr] = sBackupTableName;
                            vWriterLocks[i] = "lock_" + i;
                            vWriterLocks[i + nr] = "lock_" + (i + nr);

                            db.query("SELECT * FROM monitor_tables WHERE mt_tablename='" + sTableName + "';");

                            if (db.moveNext()) {
                                db.query("UPDATE monitor_tables SET mt_status=1 WHERE mt_tablename='" + sTableName
                                        + "';");
                            } else {
                                logger.log(Level.INFO, "New object table detected : " + sTableName);
                                db.query("INSERT INTO monitor_tables (mt_tablename, mt_status) VALUES ('" + sTableName
                                        + "', 1);");
                            }

                            db.query("SELECT * FROM monitor_tables WHERE mt_tablename='" + sBackupTableName + "';");

                            if (db.moveNext()) {
                                db.query("UPDATE monitor_tables SET mt_status=1 WHERE mt_tablename='"
                                        + sBackupTableName + "';");
                            } else {
                                logger.log(Level.INFO, "New object table detected : " + sTableName);
                                db.query("INSERT INTO monitor_tables (mt_tablename, mt_status) VALUES ('"
                                        + sBackupTableName + "', 1);");
                            }

                            hmWriters.put(sTableName, vWriters[i]);
                            hmWriters.put(sBackupTableName, vWriters[i + nr]);
                        } catch (Throwable e) {
                            vWriters[i] = null;
                            vWriters[i + nr] = null;
                            sTableNames[i] = null;
                            sTableNames[i + nr] = null;
                            logger.log(Level.SEVERE, "Exception in initializing the store element '" + sTableName
                                    + "' : ", e);
                        }
                    } else if ((lTotalTime > 0) && (sTableName.length() > 0)
                            && ((iWriteMode == 3) || (iWriteMode == 4))) {
                        try {
                            int iImport = Integer.parseInt(AppConfig.getProperty(
                                    "lia.Monitor.Store.TransparentStoreFast.writer_" + i + ".import", "0").trim());
                            long lLimit = Long.parseLong(AppConfig.getProperty(
                                    "lia.Monitor.Store.TransparentStoreFast.writer_" + i + ".countLimit", "-1").trim());

                            vWriters[i] = new MemWriter(lTotalTime, lSamples, sTableName, iWriteMode, lLimit);

                            vWriters[i].setConstaints(sAcceptConstraints, sRejectConstraints);

                            sTableNames[i] = sTableName;
                            vWriterLocks[i] = "lock_" + i;

                            logger.log(Level.INFO, "Memory table : " + sTableName);

                            hmWriters.put(sTableName, vWriters[i]);

                            if (iImport > 0) {
                                lNewTables.add(Integer.valueOf(i));
                            }

                            vWriters[i].setOnline(now);
                        } catch (Throwable e) {
                            vWriters[i] = null;
                            sTableNames[i] = null;
                            logger.log(Level.SEVERE, "Exception in initializing the store element '" + sTableName
                                    + "' : ", e);
                        }
                    } else if ((lTotalTime > 0) && (sTableName.length() > 0)
                            && ((iWriteMode == 5) || (iWriteMode == 6))) {
                        try {
                            int iImport = Integer.parseInt(AppConfig.getProperty(
                                    "lia.Monitor.Store.TransparentStoreFast.writer_" + i + ".import", "1").trim());

                            vWriters[i] = new DBWriter2(lTotalTime, lSamples, sTableName, iWriteMode);

                            vWriters[i].setConstaints(sAcceptConstraints, sRejectConstraints);

                            sTableNames[i] = sTableName;
                            vWriterLocks[i] = "lock_" + i;

                            db.query("SELECT * FROM monitor_tables WHERE mt_tablename='" + sTableName + "';");

                            logger.log(Level.INFO, "ID table : " + sTableName);

                            if (db.moveNext()) {
                                db.query("UPDATE monitor_tables SET mt_status=1 WHERE mt_tablename='" + sTableName
                                        + "';");
                                lAllTables.add(sTableName);
                            } else {
                                logger.log(Level.INFO, "New table detected : " + sTableName);
                                db.query("INSERT INTO monitor_tables (mt_tablename, mt_status) VALUES ('" + sTableName
                                        + "', 1);");
                                if (iImport > 0) {
                                    lNewTables.add(Integer.valueOf(i));
                                }
                            }

                            hmWriters.put(sTableName, vWriters[i]);

                            vWriters[i].setOnline(now);
                        } catch (Throwable e) {
                            vWriters[i] = null;
                            sTableNames[i] = null;
                            logger.log(Level.SEVERE, "Exception in initializing the store element '" + sTableName
                                    + "' : ", e);
                        }

                    } else if ((lTotalTime > 0) && (sTableName.length() > 0)
                            && ((iWriteMode == 7) || (iWriteMode == 8))) {
                        int iImport = 0;

                        try {
                            vWriters[i] = new DBWriter3(lTotalTime, lSamples, sTableName, iWriteMode);

                            vWriters[i].setConstaints(sAcceptConstraints, sRejectConstraints);

                            sTableNames[i] = sTableName;
                            vWriterLocks[i] = "lock_" + i;

                            db.query("SELECT * FROM monitor_tables WHERE mt_tablename='" + sTableName + "';");

                            logger.log(Level.INFO, "ID table v3: " + sTableName);

                            if (db.moveNext()) {
                                db.query("UPDATE monitor_tables SET mt_status=1 WHERE mt_tablename='" + sTableName
                                        + "';");
                                lAllTables.add(sTableName);
                            } else {
                                logger.log(Level.INFO, "New table detected : " + sTableName);
                                db.query("INSERT INTO monitor_tables (mt_tablename, mt_status) VALUES ('" + sTableName
                                        + "', 1);");
                                if (iImport > 0) {
                                    lNewTables.add(Integer.valueOf(i));
                                }
                            }

                            hmWriters.put(sTableName, vWriters[i]);

                            vWriters[i].setOnline(now);

                        } catch (Throwable e) {
                            vWriters[i] = null;
                            sTableNames[i] = null;
                            logger.log(Level.SEVERE, "Exception in initializing the store element '" + sTableName
                                    + "' : ", e);
                        }
                    } else if ((lTotalTime > 0) && (sTableName.length() > 0)
                            && ((iWriteMode == 9) || (iWriteMode == 10))) {
                        try {

                            vWriters[i] = new AccountingWriter(sTableName, lTotalTime, iWriteMode);

                            vWriters[i].setConstaints(sAcceptConstraints, sRejectConstraints);

                            sTableNames[i] = sTableName;
                            vWriterLocks[i] = "lock_" + i;
                            logger.log(Level.INFO, "Accounting table: " + sTableNames[i]);

                            hmWriters.put(sTableName, vWriters[i]);

                            vWriters[i].setOnline(now);

                        } catch (Exception e) {
                            vWriters[i] = null;
                            sTableNames[i] = null;
                            logger.log(Level.SEVERE, "Exception in initializing the accounting writer: ", e);
                        }
                    } else if ((lTotalTime > 0) && (sTableName.length() > 0)
                            && ((iWriteMode == 11) || (iWriteMode == 12))) {
                        vWriters[i] = new DBWriter4(lTotalTime, lSamples, sTableName, iWriteMode);
                        vWriters[i].setConstaints(sAcceptConstraints, sRejectConstraints);
                        sTableNames[i] = sTableName;
                        vWriterLocks[i] = "lock_" + i;

                        logger.log(Level.INFO, "DB4 table: " + sTableName);

                        hmWriters.put(sTableName, vWriters[i]);

                        vWriters[i].setOnline(now);
                    }
                }

                List<String> lDeletedTables = new ArrayList<String>();
                // mark old tables as deleted
                if (!bIsReloading) {
                    db.query("SELECT mt_tablename FROM monitor_tables WHERE mt_status=0;");
                    DB db2 = new DB();
                    while (db.moveNext()) {
                        logger.log(Level.INFO, "Marking old table as deleted : " + db.gets(1));
                        db2.query("DROP TABLE " + db.gets(1) + "_deleted;", true);
                        db2.query("ALTER TABLE " + db.gets(1) + " RENAME TO " + db.gets(1) + "_deleted ;");
                        db2.query("DELETE FROM saved_bprevdata WHERE sp_name='" + db.gets(1) + "';");

                        if (db2.query("SELECT * FROM " + db.gets(1) + "_deleted LIMIT 1;", true) && db2.moveNext()) {
                            lDeletedTables.add(db.gets(1) + "_deleted");
                        }
                    }
                }

                if (driverString.indexOf("postgres") >= 0) {
                    db.maintenance("VACUUM monitor_tables;");
                    db.maintenance("REINDEX TABLE monitor_tables;");
                }

                // import old data into the new tables
                if ((lNewTables.size() > 0) && ((lAllTables.size() > 0) || (lDeletedTables.size() > 0))
                        && !bIsReloading) {
                    String sQuery = "";

                    for (int i = 0; i < lAllTables.size(); i++) {
                        logger.log(Level.INFO, "Importing old data from existing table : " + lAllTables.get(i));

                        sQuery += (sQuery.length() > 0 ? " UNION " : "") + "SELECT * FROM " + (lAllTables.get(i));
                    }

                    for (int i = 0; i < lDeletedTables.size(); i++) {
                        logger.log(Level.INFO, "Importing old data from deleted table : " + lDeletedTables.get(i));

                        sQuery += (sQuery.length() > 0 ? " UNION " : "") + "SELECT * FROM " + (lDeletedTables.get(i));
                    }

                    sQuery += " ORDER BY rectime ASC;";

                    db.query(sQuery);

                    int iCount = 0;

                    while (db.moveNext()) {
                        Result r = new Result();
                        r.param_name = new String[1];
                        r.param = new double[1];

                        r.time = db.getl("rectime");

                        r.param_name[0] = db.gets("mfunction");
                        r.param[0] = db.getd("mval");

                        r.FarmName = db.getns("mfarm");
                        r.ClusterName = db.getns("mcluster");
                        r.NodeName = db.getns("mnode");

                        for (int j = 0; j < lNewTables.size(); j++) {
                            int i = (lNewTables.get(j)).intValue();

                            tmw.addSample(r);
                            vWriters[i].addSample(r);
                        }

                        iCount++;
                    }

                    logger.log(Level.INFO, iCount + " records imported");
                }

                if (cleanupThread == null) {
                    cleanupThread = MonALISAExecutors.getMLStoreExecutor().scheduleWithFixedDelay(this, 80, 20,
                            TimeUnit.SECONDS);
                }
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "TransparentStoreFast : could not initialize the writers!", t);
            }

            setDontStore();

            AppConfig.addNotifier(this);
        } finally {
            configWriteLock.unlock();
        }
    }

    /*
     * Add some data to the database.
     * 
     * @see lia.Monitor.Store.TransparentStoreInt#addData(java.lang.Object)
     */
    @Override
    public void addData(final Object _o) {
        // actual data add
        if (_o == null) {
            return;
        }

        configReadLock.lock();

        try {
            final Object oMem = ResultUtils.valuesFirewall(_o, vStoreMemory, vDontStore);

            if (oMem == null) {
                // the memory filters include the persistent store filters
                return;
            }

            tmw.addSample(oMem);

            final Object o = ResultUtils.valuesFirewall(oMem, vStoreOnly, vDontStore);

            if (o == null) {
                return;
            }

            if (rfl != null) {
                rfl.addResult(o);
            }

            for (Writer vWriter : vWriters) {
                if (vWriter != null) {
                    if (vWriter.isOnline()) {
                        // System.err.println("TSF : send : "+o+" to writer "+i);
                        vWriter.addSample(o);
                    }
                }
            }

            if (miUpdater != null) {
                MonitorIDsUpdater.addSample(o);
            }

        } finally {
            configReadLock.unlock();
        }
    }

    /*
     * Adds all the Result values from a Vector object.
     */
    @Override
    public void addAll(final Vector<Object> v) {
        if (v != null) {
            synchronized (v) {
                for (int i = 0; i < v.size(); i++) {
                    addData(v.get(i));
                }
            }
        }
    }

    /*
     * Save farm configurations in the database.
     * 
     * @see lia.Monitor.Store.TransparentStoreInt#updateConfig(lia.Monitor.monitor.MFarm)
     */
    @Override
    public void updateConfig(final MFarm farm) {
        if (!SHOULD_KEEP_MEM_CONF && (!SHOULD_STORE_CONF || TransparentStoreFactory.isMemoryStoreOnly())) {
            return;
        }

        try {
            lia.ws.WSFarm wsf = lia.ws.WSUtils.getWSFarmInstance(farm);
            lia.ws.WSConf wsconf = new lia.ws.WSConf();
            long confTime = NTPDate.currentTimeMillis();
            wsconf.setWsFarm(wsf);
            wsconf.setConfTime(confTime);

            if (SHOULD_KEEP_MEM_CONF) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "[ TSF ] updating configurations hash");
                }

                hmFarmConfig.put(farm.name, wsconf);
            }

            if (TransparentStoreFactory.isMemoryStoreOnly() || !SHOULD_STORE_CONF) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " [ TSF ] New conf but not making it persistent isMemoryStoreOnly = "
                            + TransparentStoreFactory.isMemoryStoreOnly() + " SHOULD_STORE_CONF = " + SHOULD_STORE_CONF);
                }

                return;
            }

            final DB db = new DB();

            db.query("INSERT INTO monitor_n_conf (conftime, mfarm, conf) VALUES(" + confTime + ","
                    + Formatare.mySQLEscape(farm.name) + ",'" + Formatare.mySQLEscape(Writer.serializeToString(wsconf))
                    + "');");

            cleanupConf();
        } catch (Throwable t) {
            // ignore case when WS is not available
        }
    }

    /*
     * Get the last known configurations for all farms
     * 
     * @see lia.Monitor.Store.TransparentStoreInt#getConfigurationFarms()
     */
    @Override
    public Vector<String> getConfigurationFarms() {
        final Vector<String> retV = new Vector<String>();

        retV.addAll(hmFarmConfig.keySet());

        if (TransparentStoreFactory.isMemoryStoreOnly()) {
            return retV;
        }

        final DB db = new DB();
        
        db.setReadOnly(true);
        
        db.query("SELECT DISTINCT mfarm FROM monitor_n_conf GROUP BY mfarm");

        while (db.moveNext()) {
            final String sFarm = db.gets(1);

            if (!retV.contains(sFarm)) {
                retV.add(sFarm);
            }
        }

        return retV;
    } //getFarmConfigurations

    /*
     * Get a filtered configuration list for some time interval
     * 
     * @see lia.Monitor.Store.TransparentStoreInt#getConfig(long, long)
     */
    @Override
    public Vector<lia.ws.WSConf> getConfig(final long fromTime, final long toTime) {
        if (TransparentStoreFactory.isMemoryStoreOnly()) {
            final Vector<lia.ws.WSConf> retV = new Vector<lia.ws.WSConf>();

            final Iterator<lia.ws.WSConf> it = hmFarmConfig.values().iterator();

            try {
                while (it.hasNext()) {
                    final lia.ws.WSConf wsconf = it.next();

                    if ((wsconf != null) && (wsconf.getConfTime() >= fromTime) && (wsconf.getConfTime() <= toTime)) {
                        retV.add(wsconf);
                    }
                }
            } catch (Throwable t) {
                // ignore case when WS is not available
            }

            return retV;
        }

        return getConfigsByQuery("SELECT conf FROM monitor_n_conf WHERE conftime > " + fromTime + " AND conftime < "
                + toTime + ";");
    }

    private static Vector<lia.ws.WSConf> getConfigsByQuery(final String query) {
        final DB db = new DB();

        db.setReadOnly(true);
        
        db.query(query);

        final Vector<lia.ws.WSConf> ret = new Vector<lia.ws.WSConf>();

        while (db.moveNext()) {
            try {
                ret.add((lia.ws.WSConf) Writer.deserializeFromString(db.getns(1), true));
            } catch (Throwable t) {
                // ignore
            }
        }

        return ret;
    }

    private static Object getConfigByQuery(final String query) {
        final Vector<lia.ws.WSConf> v = getConfigsByQuery(query);

        if (v.size() > 0) {
            return v.get(0);
        }

        return null;
    }

    /*
     * Get the configuration for a specified farm.
     * 
     * @see lia.Monitor.Store.TransparentStoreInt#getConfig(java.lang.String)
     */
    @Override
    public Object getConfig(final String FarmName) {

        Object config = hmFarmConfig.get(FarmName);

        if ((config != null) || TransparentStoreFactory.isMemoryStoreOnly()) {
            return config;
        }

        long cfTime = -1;

        final DB db = new DB();
        
        db.setReadOnly(true);

        if (db.query("SELECT MAX(conftime) AS mconftime FROM monitor_n_conf WHERE mfarm = '" + FarmName + "';")
                && db.moveNext()) {
            cfTime = db.getl(1, 0);
        }

        if (cfTime > 0) {
            config = getConfigByQuery("SELECT conf FROM monitor_n_conf WHERE mfarm = "
                    + Formatare.mySQLEscape(FarmName) + " AND conftime = " + cfTime);
        }

        return config;
    }

    /*
     * Get the configuration for one farm for a given time interval.
     * 
     * @see lia.Monitor.Store.TransparentStoreInt#getConfig(java.lang.String, long, long)
     */
    @Override
    public Vector<lia.ws.WSConf> getConfig(final String FarmName, final long fromTime, final long toTime) {
        if (TransparentStoreFactory.isMemoryStoreOnly()) {
            final Vector<lia.ws.WSConf> retV = new Vector<lia.ws.WSConf>();

            try {
                final lia.ws.WSConf wsconf = (lia.ws.WSConf) getConfig(FarmName);

                if ((wsconf != null) && (wsconf.getConfTime() >= fromTime) && (wsconf.getConfTime() <= toTime)) {
                    retV.add(wsconf);
                }
            } catch (Throwable t) {
                // ignore case when WS is not available
            }

            return retV;
        }

        return getConfigsByQuery("SELECT conf FROM monitor_n_conf WHERE mfarm = " + Formatare.mySQLEscape(FarmName)
                + " AND conftime > " + fromTime + " AND conftime < " + toTime + ";");
    }

    @Override
    public void deleteOld(long time) {
        // not implemented as a function, the cleanupThread will do this job
    }

    @Override
    public void close() {
        configWriteLock.lock();

        try {
            for (Writer vWriter : vWriters) {
                if (vWriter != null) {
                    vWriter.save();
                }
            }

            stopThread();
        } finally {
            configWriteLock.unlock();
        }
    }

    private boolean bShouldCleanHash = true;

    /**
     * Set whether or not the storage should call periodic cleanups.
     * 
     * @param b
     */
    public void setCleanHash(final boolean b) {
        bShouldCleanHash = b;
    }

    /*
     * Rotate RRD-like table structures
     */
    private void checkForRotate() {
        configReadLock.lock();

        try {
            // quick exit if there are no rotating structures
            if (!bSomeRotatingStructures) {
                return;
            }
        } finally {
            configReadLock.unlock();
        }

        configWriteLock.lock();

        try {
            long now = NTPDate.currentTimeMillis();

            for (int i = 0; i < definedWritersLen; i++) {
                Writer writer = vWriters[i];
                Writer peerWriter = vWriters[i + definedWritersLen];
                if ((writer != null) && (peerWriter != null)) {

                    Writer onlineWriter = null;
                    Writer offlineWriter = null;

                    if (writer.isOnline()) {
                        onlineWriter = writer;
                        offlineWriter = peerWriter;
                    } else {
                        //Extra check
                        if (peerWriter.isOnline()) {
                            onlineWriter = peerWriter;
                            offlineWriter = writer;
                        }
                    }

                    if ((onlineWriter == null) || (offlineWriter == null)) {
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, " Peer writers but both offline !!! for index = " + i);
                        }
                        continue;
                    }

                    //Just check to see if they must be rotated
                    if ((onlineWriter.getOnlineTime() + onlineWriter.getTotalTime()) < now) {//we should switch
                        //DO NOT ADD ANY DATA DURING THE SWITCH!!
                        onlineWriter.setOffline();
                        offlineWriter.deleteAll();
                        offlineWriter.setOnline(now);

                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER,
                                    " [ TSF ] [ checkForRotate ] Offline now:" + onlineWriter.getTableName()
                                            + " Online now: " + offlineWriter.getTableName());
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // ignore
        } finally {
            configWriteLock.unlock();
        }
    }

    /*
     * Thread that cleans the database tables, other than the actual data tables.
     */
    @Override
    public void run() {
        if (DataSplitter.bFreeze) {
            stopThread();
            return;
        }

        int iSavedPrevDataCount = 0;
        int iVacuumCount = 0;
        int iIDUpdateCount = 0;
        int iIDReindexCount = 0;

        final boolean bPostgres = driverString.indexOf("postgres") >= 0;
        final boolean bMysql = driverString.indexOf("mysql") >= 0;

        try {
            Thread.sleep(1000 * 60);
        } catch (Exception e) {
            // ignore
        }

        try {
            try {
                Thread.sleep(1000 * 20);
            } catch (Exception e) {
                // ignore
            }

            checkForRotate();

            configReadLock.lock();

            try {
                for (Writer vWriter : vWriters) {
                    if (vWriter != null) {
                        int iSave = vWriter.save();
                        vWriter.cleanup(bShouldCleanHash);

                        iSavedPrevDataCount += iSave;
                        iVacuumCount += iSave;
                    }
                }
            } finally {
                configReadLock.unlock();
            }

            final DB db = new DB();

            if (iVacuumCount > 100) {
                // these tables get very big pretty fast, better not let them grow too big
                if (bPostgres) {
                    db.maintenance("VACUUM ANALYZE saved_bprevdata;");
                    db.maintenance("VACUUM ANALYZE monitor_n_conf;");
                }

                iVacuumCount = 0;
            }

            if (IDGenerator.getUpdateCount() > 2000) {
                iIDUpdateCount += IDGenerator.getUpdateCount();
                iIDReindexCount += IDGenerator.getUpdateCount();
                IDGenerator.resetUpdateCount();

                if (bPostgres) {
                    db.maintenance("ANALYZE monitor_ids;");
                }
            }

            if (iIDUpdateCount > 50000) {
                iIDUpdateCount = 0;

                if (bPostgres) {
                    //db.query("CLUSTER monitor_ids;");
                    db.maintenance("VACUUM ANALYZE monitor_ids;");
                }
            }

            if (iIDReindexCount > 1000000) {
                iIDReindexCount = 0;

                if (bPostgres) {
                    db.maintenance("VACUUM FULL monitor_ids;");
                    db.maintenance("REINDEX TABLE monitor_ids;");
                    db.maintenance("ANALYZE monitor_ids;");
                }
            }

            if (iSavedPrevDataCount > 1000) {
                if (bPostgres) {
                    db.maintenance("VACUUM FULL ANALYZE saved_bprevdata;");
                    db.maintenance("VACUUM FULL ANALYZE monitor_tables_onlinetime;");
                    db.maintenance("VACUUM FULL ANALYZE monitor_n_conf;");
                    db.maintenance("VACUUM FULL ANALYZE monitor_settings;");
                    db.maintenance("REINDEX TABLE monitor_tables_onlinetime;");
                    db.maintenance("REINDEX TABLE saved_bprevdata;");
                    db.maintenance("REINDEX TABLE monitor_n_conf;");
                    db.maintenance("REINDEX TABLE monitor_settings;");

                    if (db.maintenance("VACUUM ANALYZE abping;", true)) { // a repository ?
                        db.maintenance("REINDEX TABLE abping;");
                        db.maintenance("VACUUM ANALYZE abping_aliases;");
                        db.maintenance("REINDEX TABLE abping_aliases;");
                        db.maintenance("VACUUM ANALYZE abping_aliases_extra;");
                    }
                }

                if (bMysql) {
                    db.query("ANALYZE TABLE saved_bprevdata;", false, true);
                    db.query("ANALYZE TABLE monitor_tables_onlinetime;", false, true);
                    db.query("ANALYZE TABLE monitor_n_conf;", false, true);
                }

                iSavedPrevDataCount = 0;
            }

            if (bPostgres) {
                pgDatabaseCleanup();
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ TSF ] [ HANDLED ] ", t);
        }
    }

    private static volatile long lLastDatabaseCleanup = 0;

    private static final void pgDatabaseCleanup() {
        if ((NTPDate.currentTimeMillis() - lLastDatabaseCleanup) > (1000 * 60 * 60 * 24)) {
            // vacuum / reindex system tables at most once a day
            lLastDatabaseCleanup = NTPDate.currentTimeMillis();

            final DB db = new DB();
            
            db.setReadOnly(true);

            db.query("SELECT tablename FROM pg_tables WHERE tablename LIKE 'pg_%';");

            final LinkedList<String> lSystemTables = new LinkedList<String>();

            while (db.moveNext()) {
                lSystemTables.add(db.gets(1));
            }

            final Iterator<String> itTables = lSystemTables.iterator();

            while (itTables.hasNext()) {
                String sTable = itTables.next();

                db.maintenance("VACUUM FULL ANALYZE " + sTable + ";");
            }

            // PostgreSQL version 8.1+ understands REINDEX SYSTEM, while older versions know about REINDEX DATABASE
            db.maintenance("REINDEX SYSTEM " + AppConfig.getProperty("lia.Monitor.DatabaseName", "mon_data") + ";");
        }
    }

    /*
     * Class that compares two table definitions in order to extract data from the best structure available 
     */
    private static final class TableTime implements Comparable<TableTime> {

        /**
         * Table length
         */
        public final long lTime;

        /**
         * Definition count
         */
        public final int indent;

        /**
         * Simple constructor
         * 
         * @param i indent
         * @param l time
         */
        public TableTime(final int i, final long l) {
            indent = i;
            lTime = l;
        }

        @Override
        public int compareTo(final TableTime t) {
            if (t == null) {
                return 1;
            }

            if (lTime < t.lTime) {
                return -1;
            } else if (lTime == t.lTime) {
                if (indent < t.indent) {
                    return -1;
                } else if (indent > t.indent) {
                    return 1;
                } else {
                    return 0;
                }
            }
            return 1;
        }

        @Override
        public boolean equals(final Object o) {
            return compareTo((TableTime) o) == 0;
        }

        @Override
        public int hashCode() {
            return indent;
        }

        @Override
        public String toString() {
            return "(" + indent + "). " + lTime;
        }
    }

    /*
     * Implementation of the dbStore.select(p) interface method.
     * 
     * @see lia.Monitor.monitor.dbStore#select(lia.Monitor.monitor.monPredicate)
     */
    @Override
    public Vector<TimestampedResult> select(final monPredicate p) {
        try {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "TSF: select(" + p + ")");
            }

            final Vector<TimestampedResult> vResults = getResults(new monPredicate[] { p }, false);

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "Got values: " + vResults);
            }

            return vResults;
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ TSF ] Got exception on storage query", t);
            return new Vector<TimestampedResult>(1);
        }
    }

    /**
     * Used by SimpleExporter to query the storage
     * @param p array of predicates
     * @return the results
     */
    public Vector<TimestampedResult> getResults(final monPredicate p[]) {
        return getResults(p, false);
    }

    /**
     * Common crossing point for getResults(monPredicate[]) and select(monPredicate)
     */
    private final Vector<TimestampedResult> getResults(final monPredicate p[], final boolean bNormalized) {
        if ((p == null) || (p.length == 0)) {
            return new Vector<TimestampedResult>(1);
        }

        final DataSplitter ds = getDataSplitter(p, -1, bNormalized);

        final Vector<TimestampedResult> vResults = ds.toVector();

        return vResults;
    }

    /**
     * A little bit more intelligent splitter, with faster add for already split series
     */
    private static final class DataSplitter3 extends DataSplitter {

        /**
         * Simple constructor
         * @param size initial size
         */
        public DataSplitter3(final int size) {
            super(size);

            bCompacted = true;

            lNow = bFreeze ? lFreezeTime : NTPDate.currentTimeMillis();
        }

        /**
         * @param id
         * @param v
         */
        public void putData(final int id, final Vector<TimestampedResult> v) {
            hmSeries.put(IDGenerator.getKey(id), v);
        }

        /**
         * @param l
         */
        public void setInterval(final long l) {
            lInterval = l;
        }

    }

    /*
     * The splitter for DBWriter3 structures (one table per id)
     */
    private DataSplitter getDataSplitter3(final monPredicate p[], final int indent, final int interval,
            final boolean bNormalized) {
        final HashSet<Integer> hsIDs = new HashSet<Integer>();

        final long now = NTPDate.currentTimeMillis();

        if ((p == null) || (p.length == 0)) {
            return new DataSplitter(1);
        }

        for (int i = 0; i < p.length; i++) {
            if (!bNormalized) {
                p[i] = TransparentStoreFactory.normalizePredicate(p[i]);
            }

            hsIDs.addAll(IDGenerator.getIDs(p[i]));
        }

        if (hsIDs.size() <= 0) {
            return new DataSplitter(1);
        }

        final DataSplitter3 ds = new DataSplitter3(hsIDs.size());

        long tmin = now + p[0].tmin;
        long tmax = now + p[0].tmax;

        ds.setInterval(tmax - tmin);

        tmin /= 1000;
        tmax /= 1000;

        final DB db = new DB();

        final Iterator<Integer> it = hsIDs.iterator();
        while (it.hasNext()) {
            final Vector<TimestampedResult> vRez = new Vector<TimestampedResult>();

            final Integer id = it.next();

            if (id == null) {
                continue;
            }

            final IDGenerator.KeySplit split = IDGenerator.getKeySplit(id);

            if (split == null) {
                continue;
            }

            final String q;

            if (interval > 1) {
                q = "SELECT rectime,mval,mmin,mmax FROM select_data_" + vWriters[indent].getTableName() + "(" + id
                        + ", " + tmin + ", " + tmax + ", " + interval + ");";
            } else {
                q = "SELECT rectime,mval,mmin,mmax FROM " + vWriters[indent].getTableName() + "_" + id
                        + " WHERE rectime>=" + tmin + " AND rectime<=" + tmax + " ORDER BY rectime ASC;";
            }

            db.setReadOnly(true);
            
            // the table does not exist, no problem, just skip it silently
            if (!db.query(q, true)) {
                continue;
            }

            ExtendedResult rez;

            synchronized (vRez) {
                while (db.moveNext()) {
                    rez = new ExtendedResult();
                    rez.time = db.getl(1) * 1000;
                    rez.FarmName = split.FARM;
                    rez.ClusterName = split.CLUSTER;
                    rez.NodeName = split.NODE;
                    rez.addSet(split.FUNCTION, db.getd(2));
                    rez.min = db.getd(3);
                    rez.max = db.getd(4);

                    vRez.add(rez);
                }
            }

            if (vRez.size() > 0) {
                ds.putData(id.intValue(), vRez);
            }
        }

        return ds;
    }

    /*
     * A single big table
     */
    private Vector<Object> getResults2(final monPredicate p[], final int indent, final boolean bNormalized) {
        //logger.log(Level.INFO, "getResults2 : "+p.length+" preds, indent = "+indent);

        final DB db = new DB();

        final HashSet<Integer> hsIDs = new HashSet<Integer>();

        for (int i = 0; i < p.length; i++) {
            if (!bNormalized) {
                p[i] = TransparentStoreFactory.normalizePredicate(p[i]);
            }

            hsIDs.addAll(IDGenerator.getIDs(p[i]));
        }

        //logger.log(Level.INFO, "Final IDs : "+vIDs.size());

        final Vector<Object> vRez = new Vector<Object>();

        if (hsIDs.size() <= 0) {
            return vRez;
        }

        final StringBuilder sbIDs = new StringBuilder();

        final Iterator<Integer> it = hsIDs.iterator();
        while (it.hasNext()) {
            if (sbIDs.length() > 0) {
                sbIDs.append(",");
            }

            sbIDs.append(it.next());
        }

        final long tmin = NTPDate.currentTimeMillis() + p[0].tmin;
        final long tmax = NTPDate.currentTimeMillis() + p[0].tmax;

        final String q = "SELECT rectime,id,mval,mmin,mmax FROM " + vWriters[indent].getTableName()
                + " WHERE rectime>=" + tmin + " AND rectime<=" + tmax + " AND id IN (" + sbIDs.toString()
                + ") ORDER BY rectime ASC;";

        db.setReadOnly(true);
        
        db.query(q);

        synchronized (vRez) {
            while (db.moveNext()) {
                final IDGenerator.KeySplit split = IDGenerator.getKeySplit(db.geti(2));

                if (split == null) {
                    continue;
                }

                final ExtendedResult rez = new ExtendedResult();
                rez.time = db.getl(1);
                rez.FarmName = split.FARM;
                rez.ClusterName = split.CLUSTER;
                rez.NodeName = split.NODE;
                rez.min = db.getd(4);
                rez.max = db.getd(5);
                rez.addSet(split.FUNCTION, db.getd(3));

                vRez.add(rez);
            }
        }

        //logger.log(Level.INFO, "getResults2 completed: "+vRez.size()+" objects");

        return vRez;
    }

    /*
     * Get the data from non-ids tables
     */
    private Vector<Object> getResults(final monPredicate p, final int indent, final String query) {
        final Vector<Object> results = new Vector<Object>();

        synchronized (results) {
            try {
                final DB db = new DB();
                
                db.setReadOnly(true);
                
                db.query(query);

                if ((vTypes[indent] == 1) || (vTypes[indent] == 0)) {
                    ExtendedResult rez;

                    while (db.moveNext()) {
                        rez = new ExtendedResult();
                        rez.time = db.getl("rectime");
                        rez.FarmName = db.gets("mfarm");
                        rez.ClusterName = db.gets("mcluster");
                        rez.NodeName = db.gets("mnode");
                        rez.addSet(db.gets("mfunction"), db.getd("mval"));
                        rez.min = db.getd("mmin");
                        rez.max = db.getd("mmax");

                        StringFactory.convert(rez);

                        if (DataSelect.matchResult(rez, p) != null) {
                            results.add(rez);
                        }
                    }
                } else if (vTypes[indent] == 2) { // eResult
                    eResult rez;

                    while (db.moveNext()) {
                        rez = new eResult();
                        rez.time = db.getl("rectime");
                        rez.FarmName = db.gets("mfarm");
                        rez.ClusterName = db.gets("mcluster");
                        rez.NodeName = db.gets("mnode");
                        rez.addSet(db.gets("mfunction"), Writer.deserializeFromString(db.gets("mval")));

                        StringFactory.convert(rez);

                        if (DataSelect.matchResult(rez, p) != null) {
                            results.add(rez);
                        }
                    }
                }
            } catch (Throwable ee) {
                logger.log(Level.WARNING, " Failed to execute the query ! ", ee);
            }
        }
        return results;
    }

    /**
     * Set the flag to stop the cleanup thread nicely
     */
    public final void stopThread() {
        configWriteLock.lock();
        try {
            if (cleanupThread != null) {
                cleanupThread.cancel(false);
                cleanupThread = null;
            }
            logger.log(Level.INFO, "\n\n ======> [ TSF ] Cleanup task cancelled ! < ========= \n\n ");
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ TSF ] exception trying to cancel the cleanup task", t);
        } finally {
            configWriteLock.unlock();
        }
    }

    /*
     * Remove the old configurations
     */
    private static final void cleanupConf() {
        final long lNow = NTPDate.currentTimeMillis();

        if ((lNow - lLastConfCleanupTime) > (1000 * 60 * 60)) { // once every hour
            // at most ...
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Deleting old configurations");
            }

            //do not delete at startup
            final DB db = new DB();

            if (lLastConfCleanupTime != 0) { // skip at startup for faster init
                db.query(deleteLastConf + (lNow - LAST_CONF_MAXTIME) + ";");
            }

            lLastConfCleanupTime = lNow;
        }
    }

    /**
     * Used by the statistics web page
     * @param vPreds data selection criteria
     * @param lCompactInterval distance in time between two points in the final data set
     * @return the data series
     */
    public DataSplitter getDataSplitter(final Vector<monPredicate> vPreds, final long lCompactInterval) {
        if ((vPreds == null) || (vPreds.size() <= 0)) {
            return null;
        }

        final monPredicate p[] = new monPredicate[vPreds.size()];

        for (int i = 0; i < vPreds.size(); i++) {
            p[i] = vPreds.get(i);
        }

        return getDataSplitter(p, lCompactInterval);
    }

    /**
     * Used by the display servlet and the getResults() method.
     * @param p data selection criteria
     * @param lCompactInterval distance in time between two points in the final data set
     * @return the data series
     */
    public DataSplitter getDataSplitter(final monPredicate p[], final long lCompactInterval) {
        return getDataSplitter(p, lCompactInterval, false);
    }

    private List<TableTime> getWritersList() {
        final List<TableTime> l = new ArrayList<TableTime>();

        for (int indent = 0; indent < definedWritersLen; indent++) {
            if (vWriters[indent] != null) {
                l.add(new TableTime(indent, vWriters[indent].getTotalTime()));
            } else {
                logger.log(Level.WARNING, " [ TSF ] [ HANDLED ] Writer element " + indent + "/" + vWriters.length
                        + " is null. Please fix the configuration.");
            }
        }
        Collections.sort(l);

        return l;
    }

    /**
     * This function extracts data only from the database and from a single table only, the one that
     * has enough data to fit the entire interval specified in the predicates.
     * 
     * It is designed to be used by the repository special 
     * 
     * @param p array of predicates. Only the first one is used to determine the interval
     * @param lCompactInterval if &gt;0 will force the use of a table that data at least of this granularity
     * @return a DataSplitter object
     */
    public DataSplitter getUniformDataSplitter(final monPredicate p[], final long lCompactInterval) {
        configReadLock.lock();

        try {
            if ((p == null) || (p.length == 0)) {
                return new DataSplitter(1);
            }

            final DataSplitter dsRez = new DataSplitter(p.length);

            for (int i = 0; i < p.length; i++) {
                p[i] = TransparentStoreFactory.normalizePredicate(p[i]);
            }

            // determine the best table

            final List<TableTime> lEligibles = getEligibleTables(lCompactInterval, false);

            if (lEligibles.size() == 0) {
                logger.log(Level.FINER, "No eligible tables");

                return dsRez;
            }

            logger.log(Level.FINER, "Eligible tables list: " + lEligibles);

            //Collections.reverse(lEligibles);

            final Iterator<TableTime> it = lEligibles.iterator();

            TableTime ttBest = null;

            while (it.hasNext()) {
                final TableTime tt = it.next();

                int type = vTypes[tt.indent];

                if ((type == 2) || (type == 9) || (type == 10) || (type < 0)) {
                    continue;
                }

                if ((tt.lTime + p[0].tmin) > 0) {
                    logger.log(Level.FINER, "Best eligible table: " + tt.indent + " of type " + type);

                    ttBest = tt;
                    break;
                }

                logger.log(Level.FINER, "Skip eligible table " + tt.indent + " because it doesn't cover the interval");
            }

            if (ttBest == null) {
                ttBest = lEligibles.get(lEligibles.size() - 1);

                logger.log(Level.FINER, "Choosing the last of the eligible tables, " + ttBest.indent
                        + ", because no other matched our query");
            }

            // query the data from it

            addQueryResults(dsRez, p, ttBest, lCompactInterval);

            // return the Result objects, we don't care about other object types

            return dsRez;
        } finally {
            configReadLock.unlock();
        }
    }

    /**
     * Get the list of tables that can provide the requested level of detail
     * 
     * @param lCompactInterval granularity
     * @return list of tables that can provide this level of detail
     */
    private List<TableTime> getEligibleTables(final long lCompactInterval, final boolean bPrepareOrder) {
        final List<TableTime> l = getWritersList();

        final Vector<TableTime> vEligibles = new Vector<TableTime>();

        final Iterator<TableTime> it = l.iterator();

        if (lCompactInterval > 0) {
            while (it.hasNext()) {
                final TableTime t = it.next();

                // only tables that contain mediated values
                if ((vTypes[t.indent] == 0) || (vTypes[t.indent] == 3) || (vTypes[t.indent] == 5)
                        || (vTypes[t.indent] == 7) || (vTypes[t.indent] == 11)) {
                    final long lSamples = vSamples[t.indent];
                    if (lSamples <= 0) {
                        continue;
                    }

                    final long lInterval = vWriters[t.indent].getTotalTime() / lSamples;

                    if (lInterval <= lCompactInterval) {
                        vEligibles.add(t);
                    }
                }
            }

            if (bPrepareOrder) {
                Collections.reverse(vEligibles);
            }

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "Eligibles tables : " + vEligibles);
            }
        }

        return vEligibles.size() > 0 ? vEligibles : new ArrayList<TableTime>(l);
    }

    /*
     * Internal method to build a splitter from the extracted data, no matter the actual storage type
     */
    private DataSplitter getDataSplitter(final monPredicate p[], final long lCompactInterval, final boolean bNormalized) {
        if ((p == null) || (p.length <= 0)) {
            return null;
        }

        configReadLock.lock();

        try {
            final long now = NTPDate.currentTimeMillis();

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "Predicate is : " + p[0]);
            }

            if (!bNormalized) {
                for (int i = 0; i < p.length; i++) {
                    p[i] = TransparentStoreFactory.normalizePredicate(p[i]);
                }
            }

            final DataSplitter dsRez = new DataSplitter(new Vector<TimestampedResult>(1), now + p[0].tmin, now + p[0].tmax);

            long tmax = p[0].tmax;
            long tmin = p[0].tmin;

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "Normalized predicate : " + p[0]);
                logger.log(Level.FINEST, "Compact interval : " + lCompactInterval);
            }

            final long tmwTotalTime = tmw.getTotalTime();

            final long lTempMin = -1 * tmwTotalTime;

            if ((tmax > lTempMin)
                    && (TransparentStoreFactory.isMemoryStoreOnly() || ((Math.abs(tmin - tmax) / memoryQueryThreshold) < tmwTotalTime))) {
                long lTime = tmin > lTempMin ? tmin : lTempMin;

                DataSplitter ds = null;

                if (tmw instanceof TempMemWriter) {
                    // System.err.println("TempMemWriter 1");
                    Vector<TimestampedResult> vData = ((TempMemWriter) tmw).getDataAsVector();

                    ds = new DataSplitter(vData, lTime, tmax, false);
                } else if (tmw instanceof TempMemWriter3) {
                    // System.err.println("TempMemWriter 3");
                    ds = ((TempMemWriter3) tmw).getDataSplitter(p);
                } else if (tmw instanceof TempMemWriter4) {
                    // System.err.println("TempMemWriter 4");
                    ds = ((TempMemWriter4) tmw).getDataSplitter();
                }

                if (ds != null) {
                    if (tmw instanceof TempMemWriter3) {
                        dsRez.add(ds, -1);
                    } else {
                        Vector<TimestampedResult> vt;
                        for (int i = 0; i < p.length; i++) {
                            p[i].tmin = lTime;
                            p[i].tmax = tmax;

                            vt = ds.getAndFilter(p[i]);

                            if ((vt != null) && (vt.size() > 0)) {
                                dsRez.add(vt);
                            }
                        }
                    }
                }

                long lTemp = dsRez.getAbsMin();

                tmax = lTemp > 0 ? lTemp - NTPDate.currentTimeMillis() : lTempMin;

                logger.log(Level.FINER, "Memory absmin = " + lTemp + ", lTempMin = "
                        + (NTPDate.currentTimeMillis() + lTempMin) + ", tmax=" + tmax);
            }

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "Extracted from memory: " + dsRez);
            }

            if ((tmax <= tmin) || ((lCompactInterval > 1) && (tmax <= (tmin + lCompactInterval)))
                    || ((tmax <= (tmin + (2 * 60 * 1000))) && (lTempMin < tmin))) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER,
                            "exiting quickly because the memory buffer contained all possible values (tmax=" + tmax
                                    + ", tmin=" + tmin + ", lCompactInterval=" + lCompactInterval + ", lTempMin="
                                    + lTempMin + ")");
                }

                return compact(dsRez, lCompactInterval);
            }

            final long tmaxMem = tmax;

            // first of all select all the tables with enough samples
//            Iterator<TableTime> it = getEligibleTables(lCompactInterval, true).iterator();
            final List<TableTime> l = new LinkedList<TableTime>(getWritersList());
//            Collections.reverse(l);
            
            Iterator<TableTime> it = l.iterator();

            while (it.hasNext()) {
                final TableTime t = it.next();

                // skip : eResults, accounting results (average & raw)
                if ((vTypes[t.indent] < 0) || (vTypes[t.indent] == 2) || (vTypes[t.indent] == 9)
                        || (vTypes[t.indent] == 10)) {
                    continue;
                }

                final long ttime = -1 * vWriters[t.indent].getTotalTime();

                if ((tmax <= ttime) || (tmax <= tmin)) {
                    continue;
                }

                for (int i = 0; i < p.length; i++) {
                    p[i].tmin = tmin > ttime ? tmin : ttime;
                    p[i].tmax = tmax;
                }

                if (logger.isLoggable(Level.FINEST)){
                	logger.log(Level.FINEST, "Extracting data from "+t+" with predicates "+Arrays.toString(p));
                }
                
                addQueryResults(dsRez, p, t, lCompactInterval);

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "At this point ds is: " + dsRez);
                }

                if (tmin >= ttime) {
                    break;
                }

                tmax = ttime;
            }

            tmax = tmaxMem;

            // now do the same for the eResults for this interval

            it = getWritersList().iterator();
            while (it.hasNext()) {
                final TableTime t = it.next();

                if (vTypes[t.indent] != 2) {
                    continue;
                }

                final long ttime = -1 * vWriters[t.indent].getTotalTime();

                if ((tmax <= ttime) || (tmax <= tmin)) {
                    continue;
                }

                for (int i = 0; i < p.length; i++) {
                    p[i].tmin = tmin > ttime ? tmin : ttime;
                    p[i].tmax = tmax; // set by getDataSplitter
                }

                addQueryResults(dsRez, p, t, lCompactInterval);

                if (tmin >= ttime) {
                    break;
                }

                tmax = ttime;
            }

            return compact(dsRez, lCompactInterval);
        } finally {
            configReadLock.unlock();
        }
    }

    private void addQueryResults(final DataSplitter dsRez, final monPredicate[] p, final TableTime t,
            final long lCompactInterval) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Extracting " + p[0] + " from " + vWriters[t.indent] + " (" + t.indent
                    + ") of type " + vTypes[t.indent]);
        }

        switch (vTypes[t.indent]) {
        case 0:
        case 1:
        case 2:
            dsRez.add(getOldResults(p, t.indent, true));
            break;
        case 5:
        case 6:
            dsRez.add(getResults2(p, t.indent, true));
            break;
        case 7:
        case 8:
            dsRez.add(getDataSplitter3(p, t.indent, (int) (lCompactInterval / 1000), true), lCompactInterval);
            break;
        case 11:
        case 12:
            dsRez.add(getDataSplitter4(p, t.indent, lCompactInterval, true), lCompactInterval);
            break;
        }

    }

    private static DataSplitter compact(final DataSplitter ds, final long lCompactInterval) {
        if (lCompactInterval <= 1) {
            return ds;
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Before compact: (" + ds.isCompacted() + ") : " + ds + ", " + lCompactInterval);
        }

        ds.compact(lCompactInterval, true);

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "After compact : " + ds);
        }

        return ds;
    }

    /*
     * Extract the data from tables like Farm | Cluster | Node | Function | rectime | mmval | mmin | mmax
     */
    private Vector<Object> getOldResults(final monPredicate p[], final int indent, final boolean bNormalized) {
        Vector<Object> vRez = new Vector<Object>();

        if ((p == null) || (p.length == 0)) {
            return vRez;
        }

        if (!bNormalized) {
            for (int i = 0; i < p.length; i++) {
                p[i] = TransparentStoreFactory.normalizePredicate(p[i]);
            }
        }

        for (monPredicate element : p) {
            // select data from both tables, online and offline
            String query = DataSelect.rquery(element, vWriters[indent].getTableName());

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " Query 1 is '" + query + "' for " + element);
            }

            if (query.indexOf("WHERE 0=1") < 0) {
                vRez.addAll(getResults(element, indent, query));
            }

            // the alternative table now
            query = DataSelect.rquery(element, vWriters[indent + definedWritersLen].getTableName());

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " Query 2 is '" + query + "' for " + element);
            }

            if (query.indexOf("WHERE 0=1") < 0) {
                vRez.addAll(getResults(element, indent, query));
            }
        }

        return vRez;
    }

    /**
     * Get the data from the structure as one table per parameter name, different series have IDs in
     * one bigger table.
     */
    private DataSplitter getDataSplitter4(final monPredicate[] p, final int table, final long lCompactInterval,
            final boolean bNormalized) {
        return getDataSplitter4(p, (DBWriter4) vWriters[table], lCompactInterval, bNormalized);
    }

    /**
     * Get history data from a type-4 storage structure. 
     * 
     * @param p list of predicates
     * @param writer a pointer to the desired structure
     * @param lCompactInterval averaging interval, can be set to -1 to get everything
     * @return history data that matches
     */
    public static DataSplitter getDataSplitter4(final monPredicate[] p, final DBWriter4 writer,
            final long lCompactInterval) {
        return getDataSplitter4(p, writer, lCompactInterval, false);
    }

    private static DataSplitter getDataSplitter4(final monPredicate[] p, final DBWriter4 writer,
            final long lCompactInterval, final boolean bNormalized) {
        if ((p == null) || (p.length == 0)) {
            return null;
        }

        if (!bNormalized) {
            for (int i = 0; i < p.length; i++) {
                p[i] = TransparentStoreFactory.normalizePredicate(p[i]);
            }
        }

        final HashMap<String, TreeSet<Integer>> hmIDs = new HashMap<String, TreeSet<Integer>>();

        for (monPredicate element : p) {
            final Vector<Integer> vIDs = IDGenerator.getIDs(element);

            for (int j = vIDs.size() - 1; j >= 0; j--) {
                final Integer id = vIDs.get(j);

                final IDGenerator.KeySplit split = IDGenerator.getKeySplit(id);

                if (split == null) {
                    continue;
                }

                final String sTableExt = Writer.nameTransform(split.FUNCTION);

                TreeSet<Integer> tsTableIDs = hmIDs.get(sTableExt);

                if (tsTableIDs == null) {
                    tsTableIDs = new TreeSet<Integer>();
                    hmIDs.put(sTableExt, tsTableIDs);
                }

                tsTableIDs.add(id);
            }
        }

        //System.err.println("get from DB4: base table name = "+vWriters[table].getTableName());
        //System.err.println("Extracting from DB: "+hmIDs);
        //System.err.println("Pred = "+p[0]);

        final long now = NTPDate.currentTimeMillis();

        long tmin = now + p[0].tmin;
        long tmax = now + p[0].tmax;

        final DataSplitter ds = new DataSplitter(null, tmin, tmax, true);

        tmin /= 1000;
        tmax /= 1000;

        if (hmIDs.size() == 0) {
            // nothing to do, the predicates didn't match anything
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Return quickly because there is no ID that matches the predicate");
            }

            return ds;
        }

        if (hmIDs.size() == 1) {
            // only one entry, won't be executed in parallel no matter the configuration			
            final Map.Entry<String, TreeSet<Integer>> me = hmIDs.entrySet().iterator().next();
            final String sTableExt = me.getKey();
            final TreeSet<Integer> tsTableIDs = me.getValue();

            // all eggs in one bin. Can we do something to speed it up ?
            if ((tsTableIDs.size() > 10) && (ReplicationManager.getInstance().getOnlineBackendsCount() > 1)) {
                // yes, we would be better off executing in parallel
                final int chunks = Math.min(2 * ReplicationManager.getInstance().getOnlineBackendsCount(),
                        workers.size());

                final int elements = 1 + (tsTableIDs.size() / chunks);

                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Splitting " + tsTableIDs.size() + " IDs in " + chunks + " chunks, ~"
                            + elements + " elements each");
                }

                final Iterator<Integer> it = tsTableIDs.iterator();

                final LinkedBlockingQueue<Runnable> results = new LinkedBlockingQueue<Runnable>();

                int count = 0;

                TreeSet<Integer> tsChunk = new TreeSet<Integer>();

                while (it.hasNext()) {
                    if (tsChunk.size() > elements) {
                        queueWork(new DB4QueryExecutor(writer, lCompactInterval, tmin, tmax, ds, sTableExt, tsChunk),
                                results);
                        count++;
                        tsChunk = new TreeSet<Integer>();
                    }

                    tsChunk.add(it.next());
                }

                if (tsChunk.size() > 0) {
                    queueWork(new DB4QueryExecutor(writer, lCompactInterval, tmin, tmax, ds, sTableExt, tsChunk),
                            results);
                    count++;
                }

                while (count > 0) {
                    try {
                        results.take();
                        count--;
                    } catch (Exception e) {
                        // ignore
                    }
                }

                return ds;
            }

            // it's not worth doing anything in parallel 

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Executing in single shot mode, everything (" + tsTableIDs.size()
                        + " ids) are in a single table: " + sTableExt);
            }

            getTableFromWriter4(writer, lCompactInterval, tmin, tmax, ds, sTableExt, tsTableIDs);

            return ds;
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Splitting work in " + hmIDs.size() + " jobs");
        }

        // split the work in independent pieces and pass them to the workers
        final Iterator<Map.Entry<String, TreeSet<Integer>>> it = hmIDs.entrySet().iterator();

        final LinkedBlockingQueue<Runnable> results = new LinkedBlockingQueue<Runnable>();

        int count = 0;

        while (it.hasNext()) {
            final Map.Entry<String, TreeSet<Integer>> me = it.next();
            final String sTableExt = me.getKey();
            final TreeSet<Integer> tsTableIDs = me.getValue();

            final DB4QueryExecutor qe = new DB4QueryExecutor(writer, lCompactInterval, tmin, tmax, ds, sTableExt,
                    tsTableIDs);

            queueWork(qe, results);
            count++;
        }

        while (count > 0) {
            try {
                results.take();
                count--;
            } catch (InterruptedException ie) {
                // ignore
            }
        }

        return ds;
    }

    private static final class Work implements Runnable {

        private final Runnable taskToExecute;
        private final LinkedBlockingQueue<Runnable> queue;

        public Work(final Runnable toRun, final LinkedBlockingQueue<Runnable> finishQueue) {
            this.taskToExecute = toRun;
            this.queue = finishQueue;
        }

        @Override
        public void run() {
            try {
                taskToExecute.run();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "A task failed to execute correctly", t);
            }

            queue.add(taskToExecute);
        }
    }

    private static void queueWork(final Runnable job, final LinkedBlockingQueue<Runnable> finishQueue) {
        workQueue.add(new Work(job, finishQueue));
    }

    /**
     * Portions of SELECT statements to be executed by the backends
     */
    static final LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>();

    private static final class Worker extends Thread {
        public Worker(final int id) {
            super("TSF - Worker " + id);
        }

        @Override
        public void run() {
            while (true) {
                try {
                    final Runnable work = workQueue.take();

                    work.run();
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
        }
    }

    private static final List<Worker> workers;

    static {
        // by default create wokers twice as many as database backends, so in average each backend
        // will receive at peak times two concurrent queries
        final int workersCount = AppConfig.geti("lia.Monitor.Store.parallel_queries", 2 * ReplicationManager
                .getInstance().getBackendsCount());

        logger.log(Level.INFO, "Starting " + workersCount + " query workers");

        workers = new ArrayList<Worker>(workersCount);

        for (int i = 0; i < workersCount; i++) {
            final Worker w = new Worker(i + 1);
            w.start();

            workers.add(w);
        }
    }

    private static final class DB4QueryExecutor implements Runnable {
        private final DBWriter4 db4writer;
        private final long compactInterval;
        private final long tMin;
        private final long tMax;
        private final DataSplitter splitter;
        private final String tableExt;
        private final TreeSet<Integer> tsTableIDs;

        public DB4QueryExecutor(final DBWriter4 writer, final long lCompactInterval, final long tmin, final long tmax,
                final DataSplitter ds, final String sTableExt, final TreeSet<Integer> tableIDs) {

            this.db4writer = writer;
            this.compactInterval = lCompactInterval;
            this.tMin = tmin;
            this.tMax = tmax;
            this.splitter = ds;
            this.tableExt = sTableExt;
            this.tsTableIDs = tableIDs;
        }

        @Override
        public void run() {
            getTableFromWriter4(db4writer, compactInterval, tMin, tMax, splitter, tableExt, tsTableIDs);
        }
    }

    /**
     * @param writer
     * @param lCompactInterval
     * @param tmin
     * @param tmax
     * @param ds
     * @param sTableExt
     * @param tableIDs
     */
    static void getTableFromWriter4(final DBWriter4 writer, final long lCompactInterval, final long tmin,
            final long tmax, final DataSplitter ds, final String sTableExt, final TreeSet<Integer> tableIDs) {

        TreeSet<Integer> tsTableIDs = tableIDs;

        final StringBuilder sbQuery = new StringBuilder("SELECT rectime,id,mval,mmin,mmax FROM ");
        sbQuery.append(writer.getTableName()).append('_').append(sTableExt).append(" WHERE ");
        sbQuery.append("rectime>=" + tmin + " AND rectime<=" + tmax);

        boolean bUseIN = true;

        // try to see if it's worth just to exclude some IDs
        if (tsTableIDs.size() > 50) {
            final TreeSet<Integer> tsTableNotIDs = new TreeSet<Integer>();

            final TreeSet<Integer> tsAllIDs = IDGenerator.getV4TablesMapping().get(sTableExt);

            if (tsAllIDs != null) {
                final Iterator<Integer> it2 = tsAllIDs.iterator();

                while (it2.hasNext()) {
                    final Integer i = it2.next();

                    if (!tsTableIDs.contains(i)) {
                        tsTableNotIDs.add(i);
                    }
                }
            }

            logger.log(Level.FINER, "Exclude: " + tsTableNotIDs.size() + ", include: " + tsTableIDs.size());

            if (tsTableNotIDs.size() < 10) {
                tsTableIDs = tsTableNotIDs;
                bUseIN = false;
            }
        }

        if (tsTableIDs.size() > 0) {
            sbQuery.append(" AND id ");
            sbQuery.append(bUseIN ? "IN (" : "NOT IN (");

            final Iterator<Integer> itID = tsTableIDs.iterator();
            boolean bNotFirst = false;
            while (itID.hasNext()) {
                if (bNotFirst) {
                    sbQuery.append(',');
                } else {
                    bNotFirst = true;
                }

                sbQuery.append(itID.next());
            }

            sbQuery.append(")");
        }

        sbQuery.append(" ORDER BY id ASC,rectime ASC;");

        final DB db = new DB();

        db.setReadOnly(true);
        
        // if the table is missing, don't bother going on
        if (!db.query(sbQuery.toString(), true)) {
            return;
        }

        int oldID = -123;

        Vector<TimestampedResult> vData = new Vector<TimestampedResult>(0);

        String sFarm = null;
        String sCluster = null;
        String sNode = null;
        String sFunction = null;

        int count = 0;

        while (db.moveNext()) {
            int id = db.geti(2);

            if (id != oldID) {
                if (vData.size() > 0) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "TSF: Adding single series: " + vData.size() + " for ID " + oldID);
                    }

                    synchronized (ds) {
                        ds.addSingleSeries(vData, lCompactInterval);
                    }

                    count += vData.size();

                    vData = new Vector<TimestampedResult>();
                } else {
                    if (oldID > 0) {
                        logger.log(Level.FINER, "TSF: No data for id " + oldID);
                    }
                }

                final IDGenerator.KeySplit split = IDGenerator.getKeySplit(id);

                if (split != null) {
                    sFarm = split.FARM;
                    sCluster = split.CLUSTER;
                    sNode = split.NODE;
                    sFunction = split.FUNCTION;

                    oldID = id;
                } else {
                    oldID = -1;
                    // this ID doesn't show up any more in the database ... ignore it
                    continue;
                }
            }

            final ExtendedResult er = new ExtendedResult();
            er.time = db.getl(1) * 1000;
            er.FarmName = sFarm;
            er.ClusterName = sCluster;
            er.NodeName = sNode;
            er.addSet(sFunction, db.getd(3));
            er.min = db.getd(4);
            er.max = db.getd(5);

            vData.add(er);
        }

        if (vData.size() > 0) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "TSF: Adding last single series: " + vData.size() + " for ID " + oldID);
            }

            synchronized (ds) {
                ds.addSingleSeries(vData, lCompactInterval);
            }

            count += vData.size();
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Extracted " + count + " results from " + writer + "(" + sTableExt + ", "
                    + tsTableIDs.size() + " IDs) using " + db.getBackend());
        }
    }

    @Override
    public String toString() {
        return "TransparentStoreFast";
    }

    @Override
    public ArrayList<Object> getLatestValues() {
        try {
            return Cache.getLastValues();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " TransparentStoreFast Exc :- getLatestValues()", t);
        }
        return null;
    }

    /**
     * Flag showing if the thread determining the data start time was started or not
     */
    volatile boolean bStartTimeThreadStarted = false;

    /**
     * Find out the moment in time for the first stored data
     * 
     * @return absolute time in epoch millis
     */
    public synchronized final long getStartTime() {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "getStartTime called with prev lDataStartTime = " + lDataStartTime
                    + ", lMaxHistoryTime = " + lMaxHistoryTime);
        }

        if (lDataStartTime > 0) {
            if ((NTPDate.currentTimeMillis() - lMaxHistoryTime) > lDataStartTime) {
                return NTPDate.currentTimeMillis() - lMaxHistoryTime;
            }

            return lDataStartTime;
        }

        if (lDataStartTime == -1) {
            lDataStartTime = 0;

            // find out what is the largest defined table
            for (Writer vWriter : vWriters) {
                Writer w = vWriter;

                if ((w == null) || !w.isOnline()) {
                    continue;
                }

                if (w.getTotalTime() > lMaxHistoryTime) {
                    lMaxHistoryTime = w.getTotalTime();
                }
            }

            try {
                final long l = Long.parseLong(AppConfig.getProperty("lia.web.history_starttime", "" + lDataStartTime));

                if (l > 0) {
                    lDataStartTime = l;
                    System.err.println("DataStartTime override from configuration file : " + lDataStartTime);

                    return lDataStartTime;
                }
            } catch (Exception e) {
                // ignore
            }

            if (!bStartTimeThreadStarted) {
                new GetStartTimeThread().start();

                bStartTimeThreadStarted = true;
            }

            // wait max. 10 seconds for the value to be determined
            for (int i = 0; (i < 100) && (lDataStartTime <= 0); i++) {
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                    // ignore
                }
            }

            if (lDataStartTime > 0) {
                return lDataStartTime;
            }
        }
        
        if (tmw!=null){
        	return NTPDate.currentTimeMillis() - tmw.getTotalTime();
        }

        return NTPDate.currentTimeMillis();
    }

    /**
     * First data timestamp
     */
    volatile long lDataStartTime = -1;

    /**
     * Largest defined storage structure
     */
    long lMaxHistoryTime = -1;

    private final class GetStartTimeThread extends Thread {
        /**
         * Async method to find out the data start time
         */
        public GetStartTimeThread() {
            super("(ML) web/display : GetStartTimeThread");

            try {
                setDaemon(true);
            } catch (Exception e) {
                // ignore
            }
        }

        @Override
        public void run() {
            int iSelected = -1;
            int iSelectedSamples = -1;

            // from the tables with the maximum length, take the one with the least number of samples
            for (int i = 0; i < vWriters.length; i++) {
                Writer w = vWriters[i];

                if ((w == null) || !w.isOnline()) {
                    continue;
                }

                if (w.getTotalTime() == lMaxHistoryTime) {
                    int iSamples = -1;

                    if ((vTypes[i] == 0) || (vTypes[i] == 3) || (vTypes[i] == 5) || (vTypes[i] == 7)
                            || (vTypes[i] == 11)) {
                        iSamples = (int) vSamples[i];
                    }

                    if ((iSelected < 0) || (iSelectedSamples == -1)
                            || ((iSamples > 0) && (iSelectedSamples > iSamples))) {
                        iSelected = i;
                        iSelectedSamples = iSamples;
                    }
                }
            }

            if (iSelected < 0) {
                return;
            }

            if ((vTypes[iSelected] == 0) || (vTypes[iSelected] == 1)
                    || ((vTypes[iSelected] >= 3) && (vTypes[iSelected] <= 6))) {
                final DB db = new DB();
                
                db.setReadOnly(true);
                
                db.query("SELECT rectime FROM " + vWriters[iSelected].getTableName()
                        + " ORDER BY rectime ASC LIMIT 1;");

                if (db.moveNext() && (db.getl(1) > 0)) {
                    long lTemp = db.getl(1);

                    if ((lTemp > 0) && ((lTemp < lDataStartTime) || (lDataStartTime == 0))) {
                        lDataStartTime = lTemp;
                    }
                }
            }

            if ((vTypes[iSelected] == 7) || (vTypes[iSelected] == 8)) {
                final Vector<Integer> vIDs = IDGenerator.getAllIDs();

                final DB db = new DB();
                
                db.setReadOnly(true);
                
                final int count = vIDs.size() > 100 ? 100 : vIDs.size();

                for (int i = 0; (i < vIDs.size()) && ((i < count) || (lDataStartTime <= 0)); i++) {
                    int id = i * (vIDs.size() / count);

                    if (id < vIDs.size()) {
                        id = vIDs.get(id).intValue();
                    } else {
                        continue;
                    }

                    if (!db.query("SELECT rectime FROM " + vWriters[iSelected].getTableName() + "_" + id
                            + " ORDER BY rectime ASC LIMIT 1;", true)) {
                        continue;
                    }

                    if (db.moveNext() && (db.getl(1) > 0)) {
                        long lTemp = db.getl(1) * 1000;

                        if ((lTemp < lDataStartTime) || (lDataStartTime == 0)) {
                            lDataStartTime = lTemp;
                        }
                    }
                }
            }

            if ((vTypes[iSelected] == 11) || (vTypes[iSelected] == 12)) {
                final DB db = new DB("SELECT distinct lower(split_part(mi_key,'/',4)) from monitor_ids;");

                final DB db2 = new DB();

                int iCount = 0;

                while (db.moveNext()) {
                    if (db2.query(
                            "SELECT rectime FROM " + vWriters[iSelected].getTableName() + "_"
                                    + Writer.nameTransform(db.gets(1)) + " ORDER BY rectime ASC LIMIT 1;", true)
                            && db2.moveNext()) {
                        final long lTemp = db2.getl("rectime") * 1000;

                        if (lTemp > 0) {
                            if ((lTemp < lDataStartTime) || (lDataStartTime == 0)) {
                                lDataStartTime = lTemp;
                            }

                            if (++iCount > 100) {
                                break;
                            }
                        }
                    }
                }
            }

            if (lDataStartTime == 0) {
                lDataStartTime = -1; // try again later
            }

            System.err.println("DataStartTime = " + lDataStartTime);

            bStartTimeThreadStarted = false;
        }
    }

    /* (non-Javadoc)
     * @see lia.Monitor.monitor.AppConfigChangeListener#notifyAppConfigChanged()
     */
    @Override
    public void notifyAppConfigChanged() {
        setDontStore();

        memoryQueryThreshold = AppConfig.getd("lia.Monitor.Store.TransparentStoreFast.memoryQueryThreshold",
                memoryQueryThreshold);

        //reload();
    }

}
