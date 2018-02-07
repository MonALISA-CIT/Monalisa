/*
 * $Id: DBWriter3.java 7541 2014-11-18 15:24:12Z costing $
 */
package lia.Monitor.Store.Fast;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;
import lia.util.ntp.NTPDate;

/**
 * @author costing
 *
 */
public final class DBWriter3 extends Writer {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(DBWriter3.class.getName());

    private final String driverString = AppConfig.getProperty("lia.Monitor.jdbcDriverString", "org.postgresql.Driver")
            .trim();

    /**
     * Flag that tells whether or not at the end of a table cleanup we should compact the table too  
     */
    static boolean shouldCompactTables = Boolean.valueOf(
            AppConfig.getProperty("lia.Monitor.shouldCompactTables", "true")).booleanValue();

    /** Total table time */
    long lGraphTotalTime; // in milliseconds

    private final long lGraphSamples; // integer, >0

    /** Time of the last cleanup operation */
    long lastCleanupTime = 0;

    /**
     * Time of the last compact operation
     */
    long lastCompactTime = 0;

    private long cleanupInterval = 10 * 60 * 1000;

    private long lInterval;

    private long lSaveInterval;

    /** package protected, to be used in BatchProcessor */
    static boolean bPrefferDoublePrecision = false;

    /** package protected, to be used in BatchProcessor */
    static String sFloatingPointDef = "real";

    static {
        try {
            String sPrefferDouble = AppConfig.getProperty("lia.monitor.store.mode3.preffer_double");

            if ((sPrefferDouble != null) && (sPrefferDouble.length() > 0)
                    && (sPrefferDouble.startsWith("1") || sPrefferDouble.toLowerCase().startsWith("t"))) {
                bPrefferDoublePrecision = true;
            }
        } catch (Exception e) {
            // ignore
        }

        if (bPrefferDoublePrecision) {
            sFloatingPointDef = "double precision";
        }
    }

    /**
     * @param _lGraphTotalTime
     * @param _lGraphSamples
     * @param _sTableName
     * @throws IOException
     */
    public DBWriter3(long _lGraphTotalTime, long _lGraphSamples, String _sTableName) throws IOException {
        this(_lGraphTotalTime, _lGraphSamples, _sTableName, 7);
    }

    /**
     * @param _lGraphTotalTime
     * @param _lGraphSamples
     * @param _sTableName
     * @param _iWriteMode
     * @throws IOException
     */
    public DBWriter3(long _lGraphTotalTime, long _lGraphSamples, String _sTableName, int _iWriteMode)
            throws IOException {
        lGraphTotalTime = _lGraphTotalTime;
        lGraphSamples = _lGraphSamples;
        sTableName = _sTableName;

        iWriteMode = _iWriteMode;

        if (lGraphSamples > 1) {
            lInterval = lGraphTotalTime / lGraphSamples;
        } else {
            lInterval = 0;
            iWriteMode = 8;
        }

        initDBStructure();

        readPreviousValues();

        cleanupInterval = lGraphTotalTime / 50;
        if (cleanupInterval < (1000 * 60 * 60 * 24)) {
            cleanupInterval = 1000 * 60 * 60 * 24;
        }

        lSaveInterval = lInterval / 5;
        if (lSaveInterval < (1000 * 30)) {
            lSaveInterval = 1000 * 30;
        }

        lastCleanupTime = NTPDate.currentTimeMillis() - ((3 * cleanupInterval) / 10);
        lastCompactTime = NTPDate.currentTimeMillis();
    }

    /*
     * Tries to create the table.
     */

    private final void initDBStructure() throws IOException {
        // logger.log(Level.INFO, " ShouldCompact Tables: " +shouldCompactTables);
        if (driverString.indexOf("postgres") == -1) {
            throw new IOException("This write mode is available for PostgreSQL database ONLYsSQLQuery!");
        }

        DB db = new DB();

        // These should exist from the database creation time, this commands only work with
        // superuser rights ...
        db.syncUpdateQuery("CREATE FUNCTION plpgsql_call_handler() RETURNS OPAQUE AS 'plpgsql.so' LANGUAGE 'C';", true);
        db.syncUpdateQuery("CREATE LANGUAGE 'plpgsql' HANDLER plpgsql_call_handler LANCOMPILER 'PL/pgSQL';", true);

        db.syncUpdateQuery("CREATE TYPE monitoring_data AS (rectime integer, mval " + sFloatingPointDef + ", mmin "
                + sFloatingPointDef + ", mmax " + sFloatingPointDef + ");", true);

        db.syncUpdateQuery("create or replace function select_data_"
                + sTableName
                + "(int, int, int, int) returns setof monitoring_data as '\n"
                + "declare\n"
                + "    series      alias for $1;\n"
                + "    tmin        alias for $2;\n"
                + "    tmax        alias for $3;\n"
                + "    interval    alias for $4;\n"
                + "\n"
                + "    table_rec   record;\n"
                + "\n"
                + "    fresult     monitoring_data;\n"
                + "\n"
                + "    dValue      "
                + sFloatingPointDef
                + ";\n"
                + "    dPrevValue  "
                + sFloatingPointDef
                + ";\n"
                + "    dMin        "
                + sFloatingPointDef
                + ";\n"
                + "    dMax        "
                + sFloatingPointDef
                + ";\n"
                + "    lLastUpdate int;\n"
                + "    lLastWrite  int;\n"
                + "\n"
                + "    first       bool;\n"
                + "    wasfirst    bool;\n"
                + "    somedata    bool;\n"
                + "\n"
                + "    dIntersect  "
                + sFloatingPointDef
                + ";\n"
                + "    dAvg       "
                + sFloatingPointDef
                + ";\n"
                + "begin\n"
                + "    first    := true;\n"
                + "    wasfirst := false;\n"
                + "    somedata := false;\n"
                + "\n"
                + "    for table_rec in execute ''select rectime,mval,mmin,mmax from "
                + sTableName
                + "_'' || series || '' where rectime>='' || tmin || '' and rectime<='' || tmax || '' order by rectime asc'' LOOP\n"
                + "        if first then\n"
                + "            lLastUpdate := table_rec.rectime;\n"
                + "            lLastWrite  := (table_rec.rectime/interval)*interval;\n"
                + "            dValue      := table_rec.mval;\n"
                + "            dPrevValue  := table_rec.mval;\n"
                + "            dMin        := table_rec.mmin;\n"
                + "            dMax        := table_rec.mmax;\n"
                + "\n"
                + "            first       := false;\n"
                + "            wasfirst    := true;\n"
                + "\n"
                + "            somedata    := true;\n"
                + "        else\n"
                + "           wasfirst := false;\n"
                + "\n"
                + "           if table_rec.rectime - lLastWrite > 2*interval then\n"
                + "               if somedata then\n"
                + "                   fresult.rectime := lLastWrite + interval/2;\n"
                + "                   fresult.mval    := dValue;\n"
                + "                   fresult.mmin    := dMin;\n"
                + "                   fresult.mmax    := dMax;\n"
                + "\n"
                + "                   return next fresult;\n"
                + "               end if;\n"
                + "\n"
                + "               dValue      := 0;\n"
                + "               dMin        := 0;\n"
                + "               dMax        := 0;\n"
                + "               dPrevValue  := 0;\n"
                + "\n"
                + "               somedata := false;\n"
                + "\n"
                + "               while table_rec.rectime - lLastWrite > 2*interval loop\n"
                + "                   lLastWrite  := lLastWrite + interval;\n"
                + "               end loop;\n"
                + "\n"
                + "               lLastUpdate := lLastWrite;\n"
                + "           end if;\n"
                + "\n"
                + "           if not somedata then\n"
                + "               dMin       := table_rec.mmin;\n"
                + "               dMax       := table_rec.mmax;\n"
                + "               dPrevValue := table_rec.mval;\n"
                + "               dValue     := table_rec.mval;\n"
                + "               while table_rec.rectime - lLastWrite >= interval loop\n"
                + "                   lLastWrite := lLastWrite + interval;\n"
                + "               end loop;\n"
                + "\n"
                + "               lLastUpdate:= table_rec.rectime;\n"
                + "               somedata   := true;\n"
                + "           else\n"
                + "               if table_rec.rectime - lLastWrite >= interval then\n"
                + "		    if (table_rec.rectime > lLastUpdate) then\n"
                + "                   	dIntersect := dPrevValue + (table_rec.mval - dPrevValue) * (lLastWrite + interval - lLastUpdate) / (table_rec.rectime - lLastUpdate);\n"
                + "                   	dAvg       := (dValue * (lLastUpdate - lLastWrite) + ((dPrevValue + dIntersect)/2) * (lLastWrite + interval - lLastUpdate)) / interval;\n"
                + "\n"
                + "                   	if dIntersect < dMin then\n"
                + "                       	dMin := dIntersect;\n"
                + "                   	end if;\n"
                + "\n"
                + "                   	if dIntersect > dMax then\n"
                + "                       	dMax := dIntersect;\n"
                + "                   	end if;\n"
                + "\n"
                + "                   	lLastWrite := lLastWrite + interval;\n"
                + "\n"
                + "                   	fresult.rectime := lLastWrite - interval/2;\n"
                + "                   	fresult.mval    := dAvg;\n"
                + "                   	fresult.mmin    := dMin;\n"
                + "                   	fresult.mmax    := dMax;\n"
                + "\n"
                + "                   	return next fresult;\n"
                + "\n"
                + "                   	dValue     := (dIntersect + table_rec.mval) / 2;\n"
                + "                   	dPrevValue := table_rec.mval;\n"
                + "\n"
                + "                   	if dIntersect < table_rec.mmin then\n"
                + "                       	dMin := dIntersect;\n"
                + "                   	else\n"
                + "                       	dMin := table_rec.mmin;\n"
                + "                   	end if;\n"
                + "\n"
                + "                   	if dIntersect > table_rec.mmax then\n"
                + "                       	dMax := dIntersect;\n"
                + "                   	else\n"
                + "                       	dMax := table_rec.mmax;\n"
                + "                   	end if;\n"
                + "\n"
                + "                   	lLastUpdate := table_rec.rectime;\n"
                + "		    end if;\n"
                + "               else\n"
                + "                   if table_rec.rectime > lLastWrite then\n"
                + "                       dValue := (dValue * (lLastUpdate - lLastWrite) + ((table_rec.mval + dPrevValue) / 2) * (table_rec.rectime - lLastUpdate)) / (table_rec.rectime - lLastWrite);\n"
                + "                   else\n"
                + "                       dValue := (dValue + dPrevValue + table_rec.mval) / 3;\n"
                + "                   end if;\n" + "                   \n"
                + "                   dPrevValue := table_rec.mval;\n" + "\n"
                + "                   if table_rec.mmin < dMin then\n"
                + "                       dMin := table_rec.mmin;\n" + "                   end if;\n" + "\n"
                + "                   if table_rec.mmax > dMax then\n"
                + "                       dMax := table_rec.mmax;\n" + "                   end if;\n" + "\n"
                + "                   lLastUpdate := table_rec.rectime;\n" + "               end if;\n"
                + "           end if;\n" + "       end if;\n" + "    end loop;\n" + "\n"
                + "    if ((lLastWrite != lLastUpdate) or wasfirst) and table_rec.rectime is not null then\n"
                + "        fresult.rectime := lLastWrite/2 + table_rec.rectime/2;\n"
                + "        fresult.mval    := dValue;\n" + "        fresult.mmin    := dMin;\n"
                + "        fresult.mmax    := dMax;\n" + "\n" + "        return next fresult;\n" + "    end if;\n"
                + "\n" + "    return ;\n" + "end;\n" + "' language plpgsql;");
    }

    @Override
    public int save() {
        return 0;
    }

    /*
     * At program startup, the map is filled with CacheElement objects reprezenting the last known
     * values
     */
    private final void readPreviousValues() {
        DB db = new DB();

        db.setReadOnly(true);
        
        synchronized (mLock) {
            m = null;

            if ((iWriteMode == 8) || (lInterval < (1000 * 60 * 10))) {
                m = new HashMap<Object, CacheElement>();
                return;
            }

            m = readPrevData(sTableName);

            if (m == null) {
                logger.log(Level.INFO, "DBWriter3 (" + sTableName
                        + ") : falling back to reading last values from the table");
                m = new HashMap<Object, CacheElement>();

                final Vector<Integer> vIDs = IDGenerator.getAllIDs();

                for (int i = 0; i < vIDs.size(); i++) {
                    final Integer id = vIDs.get(i);

                    db.query("SELECT rectime,mval FROM " + sTableName + "_" + id + " ORDER BY rectime DESC LIMIT 1;",
                            true);

                    if (!db.moveNext()) {
                        continue;
                    }

                    final IDGenerator.KeySplit split = IDGenerator.getKeySplit(id);

                    final String v[] = new String[1];

                    v[0] = split.FUNCTION;

                    final Result r = new Result(split.FARM, split.CLUSTER, split.NODE, null, v);

                    r.param[0] = db.getd(2);

                    final CacheElement ce = new CacheElement(lInterval, r, 0, db.getl(1) * 1000L, false, this);

                    m.put(id, ce);
                }

                save();
            } else {
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
     * A new sample was received. First the (Farm/Cluster/Node/Parameter) pair is looked up in the
     * map: - if a CacheElement exists, then this object is notified with the new data - if a
     * CacheElement does not exist, then a new one is created and added to the map
     */
    private final void storeData(Result r) {
        int i;

        if ((r == null) || (r.param_name == null) || (r.param_name.length <= 0)) {
            // System.err.println("DBW3: return immediately because : "+r);
            return;
        }

        // System.err.println("DBW3 : add : "+r);

        for (i = 0; i < r.param_name.length; i++) {
            if (iWriteMode == 7) {
                // mediated data
                final Integer id = IDGenerator.getId(r, i);

                if (id == null) {
                    // System.err.println("DBW3: ID is null for "+r+" / "+i);

                    continue;
                }

                // System.err.println("ID is "+id+" for "+r);

                CacheElement ce;

                synchronized (mLock) {
                    ce = m.get(id);
                }

                if (ce != null) {
                    // System.err.println("Update r["+i+"]");

                    ce.update(r, true);
                } else {
                    // System.err.println("New r["+i+"]");

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

    /**
     * Flag to indicate when a cleanup operation is in progress 
     */
    volatile boolean bCleanupStarted = false;

    @Override
    public final boolean cleanup(final boolean bCleanHash) {
        final long now = NTPDate.currentTimeMillis();

        if (lInterval > 0) {
            cleanHash(lInterval);
        }

        final boolean bDBMaintenance = !DB.isAutovacuumEnabled();

        final boolean bShouldCluster = ((now - lastCompactTime) > (2 * cleanupInterval))
                && ((now - lastCompactTime) > (1000 * 60 * 60 * 72));

        if (((lastCleanupTime + cleanupInterval) < now) && !bCleanupStarted) {
            new Thread("(ML) DB3 cleaner for " + sTableName) {
                @Override
                public void run() {
                    final DB db = new DB();

                    bCleanupStarted = true;

                    final Vector<Integer> vIDs = IDGenerator.getAllIDs();

                    java.util.Collections.sort(vIDs);

                    final long lLimit = (now - lGraphTotalTime) / 1000;

                    for (int i = 0; i < vIDs.size(); i++) {
                        final long lStart = NTPDate.currentTimeMillis();

                        final Integer id = vIDs.get(i);

                        final String sTable = sTableName + "_" + id;

                        if (execMaintenance("DELETE FROM " + sTable + " WHERE rectime<" + lLimit + ";", true, db)) {
                            final int iUpdateCount = db.getUpdateCount();

                            // the table exists, so we can apply maintenance to it
                            if (db.query("SELECT count(1) AS cnt FROM " + sTable + ";")) {
                                if (db.geti(1, -1) == 0) {
                                    db.syncUpdateQuery("DROP TABLE " + sTable + ";");
                                } else if (bDBMaintenance) {
                                    if (iUpdateCount > 0) {
                                        if (bShouldCluster) {
                                            execMaintenance("CLUSTER " + sTable + "_idx ON " + sTable + ";", false, db);
                                            execMaintenance("ANALYZE " + sTable + ";", false, db);
                                        } else if (shouldCompactTables) {
                                            execMaintenance("VACUUM ANALYZE " + sTable + ";", false, db);
                                        }
                                    } else {
                                        execMaintenance("ANALYZE " + sTable + ";", false, db);
                                    }
                                }
                            }
                        }

                        lastCleanupTime = NTPDate.currentTimeMillis();

                        try {
                            final long lDiff = lastCleanupTime - lStart;

                            // do not cause too much load during this operations
                            Thread.sleep(2 * lDiff);
                        } catch (Exception e) {
                            // ignore
                        }
                    }

                    if (bDBMaintenance) {
                        if (!execMaintenance(
                                "REINDEX SYSTEM " + AppConfig.getProperty("lia.Monitor.DatabaseName", "mon_data") + ";",
                                true, db)) {
                            execMaintenance(
                                    "REINDEX DATABASE " + AppConfig.getProperty("lia.Monitor.DatabaseName", "mon_data")
                                            + ";", false, db);
                        }
                    }

                    logger.log(Level.INFO,
                            "DB3 CLEANUP FOR " + sTableName + "  TOOK : "
                                    + ((NTPDate.currentTimeMillis() - now) / 1000) + "sec for " + vIDs.size()
                                    + " tables");

                    // remember the time at which the operation actually finished, not when it
                    // started
                    lastCleanupTime = NTPDate.currentTimeMillis();

                    if (bShouldCluster) {
                        lastCompactTime = NTPDate.currentTimeMillis();
                    }

                    bCleanupStarted = false;
                }
            }.start();
        }

        return true;
    }

    @Override
    public String toString() {
        return "DBWriter3(" + sTableName + ", " + iWriteMode + ")";
    }

}
