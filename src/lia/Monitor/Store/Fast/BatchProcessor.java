package lia.Monitor.Store.Fast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.Fast.Replication.ReplicationManager;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;
import lia.util.StringFactory;
import lia.util.ntp.NTPDate;
import lia.web.utils.Formatare;

/**
 * @author costing
 *
 */
public final class BatchProcessor extends Thread {

	/** The Logger */
	private static final Logger logger = Logger.getLogger(BatchProcessor.class.getName());

	private static Vector<String> v = new Vector<String>();

	private static Vector<String> vTemp = new Vector<String>();

	/* Results TableName ... "Mapping" */
	private static final Object sync = new Object();

	private static Vector<Result> vResults = new Vector<Result>();

	private static Vector<Result> vResultsTemp = new Vector<Result>();

	private static Vector<String> vTables = new Vector<String>();

	private static Vector<String> vTablesTemp = new Vector<String>();

	private static Vector<Double> vMins = new Vector<Double>();

	private static Vector<Double> vMinsTemp = new Vector<Double>();

	private static Vector<Double> vMaxs = new Vector<Double>();

	private static Vector<Double> vMaxsTemp = new Vector<Double>();

	private static Vector<Double> vVals = new Vector<Double>();

	private static Vector<Double> vValsTemp = new Vector<Double>();

	private static Vector<String> vParams = new Vector<String>();

	private static Vector<String> vParamsTemp = new Vector<String>();

	private static Vector<Long> vTimes = new Vector<Long>();

	private static Vector<Long> vTimesTemp = new Vector<Long>();

	// queue size()
	private static int BUFFER_SIZE = AppConfig.geti("lia.Monitor.Store.Fast.BatchProcessor.BUFFER_SIZE", 50000);

	private static int DROP_ZEROES = AppConfig.geti("lia.Monitor.Store.Fast.BatchProcessor.DROP_ZEROES", BUFFER_SIZE / 10);

	private static boolean IGNORE_UNDERFLOWS = AppConfig.getb("lia.Monitor.Store.Fast.BatchProcessor.IGNORE_UNDERFLOWS", true);

	private static final int MAX_SLEEP_BETWEEN_FLUSHES = 500;

	private static final int DONT_SLEEP_BELOW = 100;

	private static final String sOverrunOutputFile = AppConfig.getProperty("lia.Monitor.Store.Fast.BatchProcessor.OverrunLogFile", "overrun.sql");

	private static final File fOverrunOutputFile = new File(sOverrunOutputFile);

	private static final Vector<MonitorIDs> vUpdateIDsLNZ = new Vector<MonitorIDs>();

	private static final Vector<MonitorIDs> vUpdateIDsLastSeen = new Vector<MonitorIDs>();

	private static AtomicLong alInsertCounter = new AtomicLong();

	private static final class MonitorIDs {

		final int id;

		final double lastvalue;

		final int lastseen;

		final int lastnonzero;

		/**
		 * @param iId
		 * @param lLastseen
		 * @param dLastvalue
		 */
		public MonitorIDs(final int iId, final int lLastseen, final double dLastvalue) {
			this.id = iId;
			this.lastseen = lLastseen;
			this.lastvalue = dLastvalue;
			this.lastnonzero = -1;
		}

		/**
		 * @param iId
		 * @param lLastnonzero
		 */
		public MonitorIDs(final int iId, final int lLastnonzero) {
			this.id = iId;
			this.lastnonzero = lLastnonzero;
			this.lastseen = -1;
			this.lastvalue = -1;
		}

	}

	private static void updateBufferSize() {
		BUFFER_SIZE = AppConfig.geti("lia.Monitor.Store.Fast.BatchProcessor.BUFFER_SIZE", 50000);

		DROP_ZEROES = AppConfig.geti("lia.Monitor.Store.Fast.BatchProcessor.DROP_ZEROES", BUFFER_SIZE / 10);

		IGNORE_UNDERFLOWS = AppConfig.getb("lia.Monitor.Store.Fast.BatchProcessor.IGNORE_UNDERFLOWS", true);
	}

	/**
	 * Add a query to be executed asynchronously
	 * 
	 * @param sQuery
	 */
	public static void add(final String sQuery) {
		if (v.size() > BUFFER_SIZE) {
			return;
		}

		v.add(sQuery);
	}

	/**
	 * Add a record to be added to V1 tables asynchronously
	 * 
	 * @param tableName
	 * @param rectime
	 * @param r
	 * @param paramName
	 * @param mval
	 * @param mmin
	 * @param mmax
	 */
	public static void add(String tableName, long rectime, Result r, String paramName, double mval, double mmin, double mmax) {
		if (vTables.size() > BUFFER_SIZE) {
			return;
		}

		synchronized (sync) {
			vTables.add(tableName);
			vTimes.add(Long.valueOf(rectime));
			vResults.add(r);
			vVals.add(Double.valueOf(mval));
			vMins.add(Double.valueOf(mmin));
			vMaxs.add(Double.valueOf(mmax));
			vParams.add(paramName);
		}
	}

	private static final class IDObject implements Comparable<IDObject> {
		final String sTable;

		final long rectime;

		final int id;

		final double mval;

		final double mmin;

		final double mmax;

		IDObject(final String s, final long l, final int i, final double d1, final double d2, final double d3) {
			sTable = StringFactory.get(s);
			rectime = l;
			id = i;
			mval = d1;
			mmin = d2;
			mmax = d3;
		}

		/*
		 * Order by: table name, time, id
		 */
		@Override
		public int compareTo(final IDObject io) {
			long l = sTable.compareTo(io.sTable);

			if (l == 0) {
				l = rectime - io.rectime;
			}

			if (l == 0) {
				l = id - io.id;
			}

			return l > 0 ? 1 : (l == 0 ? 0 : -1);
		}

		@Override
		public boolean equals(final Object o) {
			return compareTo((IDObject) o) == 0;
		}

		@Override
		public int hashCode() {
			return 42; // any arbitrary constant will do
		}

		@Override
		public String toString() {
			return sTable + ": " + rectime + ", " + id + ", " + mval + ", " + mmin + ", " + mmax + "\n";
		}

	}

	private static Vector<IDObject> vIDObjects = new Vector<IDObject>();

	/**
	 * Add a value to V2 or V4 structures
	 * 
	 * @param tableName
	 * @param rectime
	 * @param id
	 * @param mval
	 * @param mmin
	 * @param mmax
	 */
	public static void add(String tableName, long rectime, int id, double mval, double mmin, double mmax) {
		addIDObject(vIDObjects, tableName, id, rectime, mval, mmin, mmax);
	}

	private static Vector<IDObject> vIDObjects3 = new Vector<IDObject>();

	/**
	 * Add a value to V3 structures
	 * 
	 * @param tableName
	 * @param rectime
	 * @param mval
	 * @param mmin
	 * @param mmax
	 */
	public static void add(String tableName, long rectime, double mval, double mmin, double mmax) {
		addIDObject(vIDObjects3, tableName, -1, rectime, mval, mmin, mmax);
	}

	private static long lLastWarningLogged = 0;

	private static long lDiscardedResults = 0;

	private static final long WARNING_PERIOD = 1000 * 60 * 15;

	private static String sCannonicalOutputFileName = null;

	private static final Object fileFlushLock = new Object();

	private static PrintWriter pwDumper = null;

	private static boolean bDumpedSomething = false;

	private long lLastDumpTime = 0;

	private static void addIDObject(final Vector<IDObject> vData, final String tableName, final int id, final long rectime, final double mval, final double mmin, final double mmax) {
		// if the buffer is growing start ignoring zero values
		if ((vData.size() > DROP_ZEROES) && (mmax < 1E-20)) {
			lDiscardedResults++;
			return;
		}

		if (IGNORE_UNDERFLOWS && Math.abs(mmin) < Double.MIN_NORMAL && mmin != 0) {
			lDiscardedResults++;
			return;
		}

		if (vData.size() > BUFFER_SIZE) {
			lDiscardedResults++;

			if (sCannonicalOutputFileName == null) {
				try {
					sCannonicalOutputFileName = fOverrunOutputFile.getCanonicalPath();
				} catch (@SuppressWarnings("unused") IOException ioe) {
					sCannonicalOutputFileName = "<cannot determine full path for '" + sCannonicalOutputFileName + "'>";
				}
			}

			if ((System.currentTimeMillis() - lLastWarningLogged) > WARNING_PERIOD) {
				logger.log(Level.WARNING, "Buffer overrun, " + lDiscardedResults + ", please do something to improve system's performance!\n Will try to log missing values to "
						+ sCannonicalOutputFileName + " so that you can insert them at a later time.");
				lLastWarningLogged = System.currentTimeMillis();
				lDiscardedResults = 0;
			}

			synchronized (fileFlushLock) {
				try {
					if (pwDumper == null) {
						pwDumper = new PrintWriter(new FileWriter(fOverrunOutputFile, true));
					}

					pwDumper.println("INSERT INTO " + tableName + " VALUES (" + rectime + "," + (id > 0 ? id + "," : "") + mval + "," + mmin + "," + mmax + ");");

					bDumpedSomething = true;
				} catch (IOException e) {
					logger.log(Level.WARNING, "Buffer overrun and I cannot write to " + sCannonicalOutputFileName + " : " + tableName + " (" + rectime + "," + (id > 0 ? id + "," : "") + mval + ","
							+ mmin + "," + mmax + ")", e);

					if (pwDumper != null) {
						pwDumper.close();
					}

					pwDumper = null;
				}
			}

			return;
		}

		vData.add(new IDObject(tableName, rectime, id, mval, mmin, mmax));
	}

	/**
	 * Update the last non zero field for a given ID
	 * 
	 * @param id
	 * @param lastnonzero
	 */
	public static void idUpdate(final int id, final int lastnonzero) {
		vUpdateIDsLNZ.add(new MonitorIDs(id, lastnonzero));
	}

	/**
	 * Update last seen / last value fields for a given ID
	 * 
	 * @param id
	 * @param lastseen
	 * @param lastvalue
	 */
	public static void idUpdate(final int id, final int lastseen, final double lastvalue) {
		vUpdateIDsLastSeen.add(new MonitorIDs(id, lastseen, lastvalue));
	}

	/**
	 * Buffer length
	 * 
	 * @return max buffer length
	 */
	public static int getBufferSize() {
		return BUFFER_SIZE;
	}

	/**
	 * Find out how many entries are waiting in V1 buffers
	 * 
	 * @return no. of entries waiting to be written
	 */
	public static int getBatchV1Size() {
		return vResults.size();
	}

	/**
	 * Find out how many entries are waiting in V2 / V4 buffers
	 * 
	 * @return no. of entries waiting to be written
	 */
	public static int getBatchV2Size() {
		return vIDObjects.size();
	}

	/**
	 * Find out how many entries are waiting in V3 buffers
	 * 
	 * @return no. of entries waiting to be written
	 */
	public static int getBatchV3Size() {
		return vIDObjects3.size();
	}

	/**
	 * Find out how many entries are waiting in generic query buffers
	 * 
	 * @return no. of entries waiting to be written
	 */
	public static int getBatchQuerySize() {
		return v.size();
	}

	static {
		BatchProcessor bp = new BatchProcessor();
		bp.setDaemon(true);
		bp.start();
	}

	private BatchProcessor() {
		super("(ML) BatchProcessor");
	}

	private static int saveStatements() {
		int iRet = 0;

		try {
			synchronized (v) {
				vTemp.clear();
				vTemp.addAll(v);
				v.clear();
			}

			if (vTemp.size() > 0) {
				execute(vTemp);
				iRet += vTemp.size();
			}

			ArrayList<MonitorIDs> al = null;
			synchronized (vUpdateIDsLastSeen) {
				if (vUpdateIDsLastSeen.size() > 0) {
					al = new ArrayList<MonitorIDs>(vUpdateIDsLastSeen);
					vUpdateIDsLastSeen.clear();
				}
			}

			if (al != null) {
				if (saveUpdateIDs(al, true)) {
					iRet += al.size();
				}
				al = null;
			}

			synchronized (vUpdateIDsLNZ) {
				if (vUpdateIDsLNZ.size() > 0) {
					al = new ArrayList<MonitorIDs>(vUpdateIDsLNZ);
					vUpdateIDsLNZ.clear();
				}
			}

			if (al != null) {
				if (saveUpdateIDs(al, false)) {
					iRet += al.size();
				}
				al = null;
			}
		} catch (Throwable t) {
			logger.log(Level.WARNING, " BatchProcessor saveStatements ", t);
		}

		return iRet;
	}

	/**
	 * @param al
	 * @param b
	 */
	private static boolean saveUpdateIDs(final ArrayList<MonitorIDs> al, final boolean lastseen) {
		final DB db = new DB();

		for (int i = 0; i < al.size(); i++) {
			final MonitorIDs mi = al.get(i);

			if (lastseen) {
				db.query("UPDATE monitor_ids SET mi_lastseen=" + mi.lastseen + ",mi_lastvalue=" + mi.lastvalue + " WHERE mi_id=" + mi.id + ";");
			}
			else {
				db.query("UPDATE monitor_ids SET mi_lastnonzero=" + mi.lastnonzero + " WHERE mi_id=" + mi.id + ";");
			}
		}

		return true;
	}

	private static boolean hasDB() {
		return (!TransparentStoreFactory.isMemoryStoreOnly()) && (ReplicationManager.getInstance().getOnlineBackendsCount() > 0);
	}

	private static final int MAX_INSERT_LENGTH = 10000;

	private static int saveIDObjects() {
		ArrayList<IDObject> alData;

		synchronized (vIDObjects) {
			if (vIDObjects.size() > 0) {
				alData = new ArrayList<IDObject>(vIDObjects);
				vIDObjects.clear();
			}
			else {
				return 0;
			}
		}

		Collections.sort(alData);

		final int vSize = alData.size();

		String oldTableName = null;

		final List<IDObject> buffer = new ArrayList<IDObject>(Math.min(alData.size(), MAX_INSERT_LENGTH));

		for (int i = 0; i < vSize; i++) {
			final IDObject o = alData.get(i);

			if (oldTableName == null) {
				oldTableName = o.sTable;
				buffer.add(o);
				continue;
			}

			if (!o.sTable.equals(oldTableName)) {
				enqueueObjects(buffer);
				oldTableName = o.sTable;
			}

			buffer.add(o);

			if (buffer.size() > MAX_INSERT_LENGTH)
				enqueueObjects(buffer);
		}

		enqueueObjects(buffer);

		return alData.size();
	}

	private static int INSERT_POOL_SIZE = 0;

	private static BlockingQueue<Runnable> INSERT_TASKS = null;
	private static ThreadPoolExecutor INSERT_EXECUTOR = null;

	private static void enqueueObjects(final List<IDObject> objects) {
		if (INSERT_POOL_SIZE == 0) {
			INSERT_POOL_SIZE = AppConfig.geti("lia.Monitor.Store.Fast.BatchProcessor.concurrentInserts", 1);

			if (INSERT_POOL_SIZE > 1) {
				int tasksPerThread = AppConfig.geti("lia.Monitor.Store.Fast.BatchProcessor.tasksPerThread", 2);

				if (tasksPerThread < 1)
					tasksPerThread = 2;

				INSERT_TASKS = new LinkedBlockingQueue<Runnable>(INSERT_POOL_SIZE * tasksPerThread);

				INSERT_EXECUTOR = new ThreadPoolExecutor(INSERT_POOL_SIZE, INSERT_POOL_SIZE, 1, TimeUnit.MINUTES, INSERT_TASKS, new ThreadPoolExecutor.CallerRunsPolicy());
			}
		}

		if (INSERT_EXECUTOR != null) {
			final ArrayList<IDObject> arrayClone = new ArrayList<BatchProcessor.IDObject>(objects);
			objects.clear();

			INSERT_EXECUTOR.submit(new Runnable() {
				@Override
				public void run() {
					flushObjects(arrayClone);
				}
			});
		}
		else
			flushObjects(objects);
	}

	static void flushObjects(final List<IDObject> objects) {
		if (objects == null || objects.size() == 0)
			return;

		// final long lStart = System.nanoTime();

		final String tableName = objects.get(0).sTable;

		final StringBuilder sb = new StringBuilder(100 + 60 * objects.size());

		sb.append("INSERT INTO ").append(tableName).append(" VALUES ");

		boolean first = true;

		for (IDObject o : objects) {
			if (first)
				first = false;
			else
				sb.append(',');

			sb.append('(').append(o.rectime).append(',').append(o.id).append(',').append(o.mval).append(',').append(o.mmin).append(',').append(o.mmax).append(')');
		}

		final String q = sb.toString();

		final DB db = new DB();

		if (!db.query(q, true)) {
			if (hasDB()) {
				if (logger.isLoggable(Level.INFO)) {
					logger.log(Level.INFO, "Creating table: " + tableName);
				}

				if (logger.isLoggable(Level.FINE)) {
					logger.log(Level.FINE, "INSERT failed");
				}
				// this is for DBWriter4, to create tables that don't yet exist
				if (db.syncUpdateQuery("CREATE TABLE " + tableName + " (rectime int, id int, mval real, mmin real, mmax real) WITHOUT OIDS;", false)) {
					db.syncUpdateQuery("CREATE INDEX " + tableName + "_idx ON " + tableName + " (id, rectime);", false);
					db.syncUpdateQuery("CREATE INDEX " + tableName + "_time_idx ON " + tableName + " (rectime);", false);
					db.maintenance("CLUSTER " + tableName + "_time_idx ON " + tableName + ";");
					db.maintenance("ALTER TABLE " + tableName + " ALTER rectime SET statistics 1000");
					db.maintenance("ALTER TABLE " + tableName + " ALTER id SET statistics 1000");

					db.query(q);
				}
			}
		}

		// System.err.println("Flushing " + objects.size() + " to table " + tableName + " took " + (System.nanoTime() - lStart) / 1000000. + " millis");

		objects.clear();
	}

	private static int saveIDObjects3() {
		final ArrayList<IDObject> alData;

		synchronized (vIDObjects3) {
			if (vIDObjects3.size() > 0) {
				alData = new ArrayList<IDObject>(vIDObjects3);
				vIDObjects3.clear();
			}
			else {
				return 0;
			}
		}

		// sort the values to insert by the table name, then by the time
		Collections.sort(alData);

		final DB db = new DB();

		for (int j = 0; j < alData.size(); j++) {
			final IDObject o = alData.get(j);

			final String q = "INSERT INTO " + o.sTable + " VALUES (" + o.rectime + ", " + o.mval + ", " + o.mmin + ", " + o.mmax + ");";

			if (!db.query(q, true) && hasDB()) {
				createV3Table(o.sTable);
				db.query(q);
			}
		}

		return alData.size();
	}

	private static final void createV3Table(final String sTableName) {
		final DB db = new DB();

		if (db.syncUpdateQuery("CREATE TABLE " + sTableName + " (rectime integer, mval " + DBWriter3.sFloatingPointDef + ", mmin " + DBWriter3.sFloatingPointDef + ", mmax "
				+ DBWriter3.sFloatingPointDef + ") WITHOUT OIDS;")) {
			db.query("ALTER TABLE " + sTableName + " ALTER rectime SET statistics 100;", false, true);
			db.query("ALTER TABLE " + sTableName + " ALTER mval SET statistics 0;", false, true);
			db.query("ALTER TABLE " + sTableName + " ALTER mmax SET statistics 0;", false, true);
			db.query("ALTER TABLE " + sTableName + " ALTER mmin SET statistics 0;", false, true);
			db.syncUpdateQuery("CREATE INDEX " + sTableName + "_idx ON " + sTableName + " (rectime);", false);
			db.query("CLUSTER " + sTableName + "_idx ON " + sTableName + ";", false, true);
		}
	}

	private static int saveResults() {
		int no = 0;
		try {
			synchronized (sync) {

				vResultsTemp.clear();
				vResultsTemp.addAll(vResults);
				vResults.clear();

				no = vResultsTemp.size();

				vTimesTemp.clear();
				vTimesTemp.addAll(vTimes);
				vTimes.clear();

				if (no != vTimesTemp.size()) {
					logger.log(Level.WARNING, " BatchProcessor different Results [ " + no + " ] and Times [ " + vTimesTemp.size());
					vTablesTemp.clear();
					vParamsTemp.clear();
					vValsTemp.clear();
					vMinsTemp.clear();
					vMaxsTemp.clear();
					return 0;
				}

				vTablesTemp.clear();
				vTablesTemp.addAll(vTables);
				vTables.clear();

				if (no != vTablesTemp.size()) {
					logger.log(Level.WARNING, " BatchProcessor different Results [ " + no + " ] and Tables [ " + vTablesTemp.size());
					vParamsTemp.clear();
					vValsTemp.clear();
					vMinsTemp.clear();
					vMaxsTemp.clear();
					return 0;
				}

				vParamsTemp.clear();
				vParamsTemp.addAll(vParams);
				vParams.clear();
				if (no != vParamsTemp.size()) {
					logger.log(Level.WARNING, " BatchProcessor different Results [ " + no + " ] and Params [ " + vParamsTemp.size());
					vValsTemp.clear();
					vMinsTemp.clear();
					vMaxsTemp.clear();
					return 0;
				}

				vValsTemp.clear();
				vValsTemp.addAll(vVals);
				vVals.clear();
				if (no != vValsTemp.size()) {
					logger.log(Level.WARNING, " BatchProcessor different Results [ " + no + " ] and Vals [ " + vValsTemp.size());
					vMinsTemp.clear();
					vMaxsTemp.clear();
					return 0;
				}

				vMinsTemp.clear();
				vMinsTemp.addAll(vMins);
				vMins.clear();
				if (no != vMinsTemp.size()) {
					logger.log(Level.WARNING, " BatchProcessor different Results [ " + no + " ] and Mins [ " + vMinsTemp.size());
					vMaxsTemp.clear();
					return 0;
				}

				vMaxsTemp.clear();
				vMaxsTemp.addAll(vMaxs);
				vMaxs.clear();
				if (no != vMaxsTemp.size()) {
					logger.log(Level.WARNING, " BatchProcessor different Results [ " + no + " ] and Mins [ " + vMaxsTemp.size());
					return 0;
				}
			} // synchronized

			if (no > 0) {
				final DB db = new DB();

				for (int i = 0; i < no; i++) {
					final String tableName = vTablesTemp.elementAt(i);

					String q = "INSERT INTO " + tableName + " (rectime, mfarm, mcluster, mnode, mfunction, mval, mmin, mmax) VALUES (";

					final Long lRectime = vTimesTemp.elementAt(i);
					final Result r = vResultsTemp.elementAt(i);
					final String paramName = vParamsTemp.elementAt(i);
					final Double dVal = vValsTemp.elementAt(i);
					final Double dMin = vMinsTemp.elementAt(i);
					final Double dMax = vMaxsTemp.elementAt(i);

					q += lRectime.longValue() + "," + e(r.FarmName) + "," + e(r.ClusterName) + "," + e(r.NodeName) + "," + e(paramName) + "," + dVal.doubleValue() + "," + dMin.doubleValue() + ","
							+ dMax.doubleValue() + ");";

					db.query(q);
				}
			}
		} catch (Throwable t) {
			logger.log(Level.WARNING, " BatchProcessor saveResults", t);
		}

		return no;
	}

	/**
	 * Get the SQL formatted string
	 * 
	 * @param s
	 *            string to insert
	 * @return SQL string
	 */
	private static String e(final String s) {
		return s == null ? "NULL" : "'" + Formatare.mySQLEscape(s) + "'";
	}

	private static volatile long lSavedCount;
	private static volatile long lSavedTime;

	/**
	 * Statistics, find out the average time to insert one value in the database
	 * 
	 * @return average time in millis to insert one value in the database
	 */
	public static double getTimePerValue() {
		long lc = lSavedCount;
		long lt = lSavedTime;

		lSavedCount = lSavedTime = 0;

		return lc > 0 ? (double) lt / (double) lc : -1;
	}

	/**
	 * Get the number of "INSERT" statements executed since the last call to this function.
	 * 
	 * @return number of executed insert statements
	 */
	public static long getInsertCount() {
		return alInsertCounter.getAndSet(0);
	}

	@Override
	public void run() {

		int sleepTime = 0, prevSleep = 0, count;

		long startTime, endTime;

		int iStatements, iResults, iIDObjects, iIDObjects3;

		int iRuns = 0;

		while (true) {
			try {
				startTime = NTPDate.currentTimeMillis();

				iStatements = saveStatements();
				iResults = saveResults();
				iIDObjects = saveIDObjects();
				iIDObjects3 = saveIDObjects3();

				endTime = NTPDate.currentTimeMillis();

				count = iStatements + iResults + iIDObjects + iIDObjects3;

				if (count > 0) {
					if (logger.isLoggable(Level.FINE)) {
						logger.log(Level.FINE, "Saved " + count + " values (" + iStatements + ", " + iResults + ", " + iIDObjects + ", " + iIDObjects3 + ") in " + (endTime - startTime) + " ms");
					}

					alInsertCounter.addAndGet(count);
					lSavedCount += count;
					lSavedTime += (endTime - startTime);
				}

				sleepTime = MAX_SLEEP_BETWEEN_FLUSHES / (count + 1);

				sleepTime = ((sleepTime / 3) + ((2 * prevSleep) / 3)) + 1;

				prevSleep = sleepTime;

				if (sleepTime > DONT_SLEEP_BELOW) {
					try {
						sleep(sleepTime);
					} catch (@SuppressWarnings("unused") Exception e) {
						// ignore
					}
				}

				if (++iRuns > 60) {
					updateBufferSize();
					iRuns = 0;

					synchronized (fileFlushLock) {
						if (bDumpedSomething) {
							pwDumper.flush();

							lLastDumpTime = NTPDate.currentTimeMillis();
						}
						else {
							if ((pwDumper != null) && ((NTPDate.currentTimeMillis() - lLastDumpTime) > (1000 * 60 * 10))) {
								pwDumper.close();
								pwDumper = null;
							}
						}
					}
				}
			} catch (Throwable t) {
				logger.log(Level.WARNING, "Main JStoreClient got General Exception in main loop", t);
			}
		} // End Main Loop

	}

	private static void execute(final Vector<String> vSQLQueries) {
		final DB db = new DB();

		final int nr = vSQLQueries.size();

		for (int i = 0; i < nr; i++) {
			db.query(vSQLQueries.elementAt(i));
		}
	}

}