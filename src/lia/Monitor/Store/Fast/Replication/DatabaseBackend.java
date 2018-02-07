package lia.Monitor.Store.Fast.Replication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.util.MLProperties;
import lia.web.utils.DoubleFormat;
import lia.web.utils.Formatare;

/**
 * This class receives requests to execute queries both sync or async
 * 
 * @author costing
 * @since Jan 20, 2009
 */
public class DatabaseBackend extends Thread {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(DatabaseBackend.class.getName());

    /**
     * At how many lines we rotate the logs 
     */
    private static final int MAX_RECORDS_PER_FILE = 500000;

    /**
     * Backend is starting. This state should only hold until we read the saved state from file
     */
    private static final int STATE_STARTING = 0;

    /**
     * Backend is in sync and everything is ok
     */
    private static final int STATE_ONLINE = 1;

    /**
     * Backend is not connected any more, waiting for the database to come back to live
     */
    private static final int STATE_OFFLINE = 2;

    /**
     * Sync lost, cannot continue
     */
    private static final int STATE_ERROR = 3;

    /**
     * Backend is again online but we are busy recovering pending queries from log file
     */
    private static final int STATE_RECOVERING = 4;

    /**
     * Where to save the status
     */
    private static final String STATUS_FILENAME = "status";

    /**
     * Where to save the resume position
     */
    private static final String RESUME_FILENAME = "resume";

    /**
     * Queue of pending updates in the database
     */
    private final LinkedBlockingQueue<String> updateQueue = new LinkedBlockingQueue<String>(AppConfig.geti(
            "lia.Monitor.Store.Fast.Replication.update_queue_limit", 50000));

    /**
     * Current state of this backend
     */
    private final AtomicInteger state = new AtomicInteger(STATE_STARTING);

    /**
     * Where are all our files
     */
    private final File baseFolder;

    /**
     * When recovering, this flag will give the backend more chances of finishing inserting before
     * logging more queries to a new file.
     */
    private final AtomicBoolean recoveryFinishAttempt = new AtomicBoolean(false);

    /**
     * A unique identifier of this backend, in form "host:port"
     */
    private final String backendName;

    /**
     * Actual database parameters
     */
    private final MLProperties dbConfig;

    /**
     * For internal synchronization between operations
     */
    private final Object activityLock = new Object();

    private final AtomicInteger aiActiveQueries = new AtomicInteger(0);

    /**
     * Synchronize access to lTotalQueries*
     */
    private final Object totalLock = new Object();

    /**
     * Total time it took to execute queries, in milliseconds
     */
    private long lTotalQueriesTime = 0;

    /**
     * Total number of queries
     */
    private long lTotalQueriesCount = 0;

    /**
     * How many times was this backend picked for executing a query?
     */
    private final AtomicInteger aiPicked = new AtomicInteger(0);

    /**
     * This constructor receives the unique identifier ("hostname:port") and a set of configuration parameters
     * 
     * @param name unique ID
     * @param prop configuration
     */
    DatabaseBackend(final String name, final MLProperties prop) {
        super("DatabaseBackend " + name);

        setDaemon(true);

        this.backendName = name;

        this.dbConfig = prop;

        final String folder = AppConfig.getProperty("replication.base_folder", ".");

        this.baseFolder = new File(folder, this.backendName);

        final boolean created = this.baseFolder.mkdirs();

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, this.backendName + ": folder created: " + created);
        }

        loadStatus();
    }

    /**
     * Get database connection parameters
     * 
     * @return connection parameters
     */
    MLProperties getConfig() {
        return this.dbConfig;
    }

    /**
     * Increment when executing a query
     */
    void incrementUsage() {
        aiActiveQueries.incrementAndGet();
    }

    /**
     * Decrement at the end of the query
     */
    void decrementUsage() {
        aiActiveQueries.decrementAndGet();
    }

    /**
     * After executing a query, update the counters
     * 
     * @param milliseconds how many milliseconds the query took to execute
     */
    void addQueryTime(final long milliseconds) {
        synchronized (this.totalLock) {
            this.lTotalQueriesCount++;
            this.lTotalQueriesTime += milliseconds;
        }
    }

    /**
     * Increment the pick count
     */
    void incrementPicked() {
        this.aiPicked.incrementAndGet();
    }

    /**
     * Get number of times this backend was picked for query execution
     * 
     * @return count
     */
    int getPickedCount() {
        return this.aiPicked.get();
    }

    /**
     * Get number of executed queries on this backend
     * 
     * @return number of executed queries
     */
    long getQueryCount() {
        synchronized (this.totalLock) {
            return this.lTotalQueriesCount;
        }
    }

    /**
     * Get the average time for executing a query on this backend
     * 
     * @return average
     */
    double getAverageQueryTime() {
        synchronized (this.totalLock) {
            if (this.lTotalQueriesCount > 0) {
                return (double) this.lTotalQueriesTime / this.lTotalQueriesCount;
            }
        }

        return 0;
    }

    /**
     * Reset counters
     */
    void resetQueryTimers() {
        synchronized (this.totalLock) {
            // keep a bit of history from the previous iteration
            if (this.lTotalQueriesCount > 100) {
                this.lTotalQueriesTime = (long) (getAverageQueryTime() * 10);
                this.lTotalQueriesCount = 10;
            }
        }

        aiPicked.set(0);
    }

    /**
     * Create some value that describes the quality of this backend
     * 
     * @return some arbitrary score
     */
    private double getScore() {
        return waitingQueries() + (getAverageQueryTime() * 5); // average query gets below 1ms since most queries are inserts
    }

    /**
     * Load last status from the file on disk
     */
    private void loadStatus() {
        BufferedReader br = null;

        try {
            br = new BufferedReader(new FileReader(new File(this.baseFolder, STATUS_FILENAME)));

            String sLine = br.readLine();

            int previousState = sLine == null ? STATE_ONLINE : Integer.parseInt(sLine);

            if (previousState == STATE_RECOVERING) {
                // start OFFLINE and let the code switch to RECOVERING whenever possible
                previousState = STATE_OFFLINE;
            }

            updateState(previousState);

            br.close();
            br = null;
        } catch (Throwable t) {
            // no previous state, start ONLINE
            updateState(STATE_ONLINE);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        logger.log(Level.INFO, this.backendName + ": loaded state: " + this.state.get());
    }

    /**
     * Get this backend's name
     * 
     * @return name
     */
    public String getBackendName() {
        return this.backendName;
    }

    @Override
    public String toString() {
        return this.backendName + "(" + waitingQueries() + ", " + DoubleFormat.point(getScore()) + ")";
    }

    /**
     * Check whether this connection is usable
     * 
     * @return true if the connection is usable, false if it is not in sync and thus cannot be used
     */
    boolean isOnline() {
        return this.state.get() == STATE_ONLINE;
    }

    /**
     * Get a direct database connection to the database server pointed in the configuration
     * 
     * @return a new connection
     */
    DBConnectionWrapper getConnection() {
        return new DBConnectionWrapper(this);
    }

    /**
     * Sleep for some time
     * 
     * @param ms interval in milliseconds
     */
    private static final void sleepTime(final long ms) {
        try {
            sleep(ms);
        } catch (InterruptedException ie) {
            // ignore
        }
    }

    /**
     * Queue an SQL update query to be executed asynchronously
     * 
     * @param sQuery query to execute
     */
    void queueUpdate(final String sQuery) {
        synchronized (this.activityLock) {
            boolean bScheduled = false;

            if (this.recoveryFinishAttempt.get()) {
                // wait a bit (up to ~2 seconds), to give the recovery a chance to a clean finish
                for (int i = 0; (i < 100) && this.recoveryFinishAttempt.get(); i++) {
                    sleepTime(20);
                }

                this.recoveryFinishAttempt.set(false);
            }

            if (isOnline()) {
                bScheduled = this.updateQueue.offer(sQuery);
            }

            if (!bScheduled) {
                if (isOnline()) {
                    updateState(STATE_OFFLINE);
                }

                log(sQuery);
            }
        }
    }

    /**
     * Current log file name
     */
    private String activeLogFile = null;

    /**
     * Writer to the current log file
     */
    private PrintWriter logFile = null;

    /**
     * How many lines are there in the current log file already?
     */
    private volatile int writtenRecords = 0;

    /**
     * Close the current log file
     */
    private void closeLog() {
        this.logFile.flush();
        this.logFile.close();
        this.logFile = null;
        this.activeLogFile = null;
    }

    /**
     * Create a new log file and start writing to it
     * 
     * @return true if everything went ok, false if the file creation has failed
     */
    private boolean openLog() {
        if (this.logFile != null) {
            closeLog();
        }

        try {
            this.activeLogFile = "" + System.currentTimeMillis();

            this.logFile = new PrintWriter(new FileWriter(new File(this.baseFolder, this.activeLogFile)));

            logger.log(Level.WARNING, this.backendName + ": started logging to '" + this.activeLogFile + "'");
        } catch (IOException ioe) {
            logger.log(Level.SEVERE, this.backendName + ": cannot create logging file '" + this.activeLogFile + "'",
                    ioe);

            this.activeLogFile = null;
            this.logFile = null;
            return false;
        }

        return true;
    }

    /**
     * Update the backend state
     * 
     * @param newState new state
     * @return true if the state was correctly updated, false if the state could not be saved to disk
     */
    private synchronized boolean updateState(final int newState) {
        if (this.state.get() == newState) {
            //System.err.println("old state = new state = "+newState);
            return true;
        }

        this.state.set(newState);

        PrintWriter pw = null;

        try {
            pw = new PrintWriter(new FileWriter(new File(this.baseFolder, STATUS_FILENAME)));

            pw.println(newState);
            pw.flush();
            pw.close();

            return pw.checkError();
        } catch (Exception e) {
            logger.log(Level.SEVERE, this.backendName + ": cannot update state", e);

            return false;
        } finally {
            if (pw != null) {
                pw.close();
            }
        }
    }

    /**
     * Execute a simple text query
     * 
     * @return true if the connection is ok, false if not
     */
    private boolean testConnection() {
        final DBConnectionWrapper conn = getConnection();

        try {
            return conn.query("SELECT 1234;") && (conn.geti(1) == 1234);
        } finally {
            conn.close();
        }
    }

    /**
     * Check the DB status by executing a query. It will switch to OFFLINE if the query cannot be executed, but 
     * won't switch back to ONLINE since this is the job of the main thread.
     * 
     * @return true if DB can be used, false if not.
     */
    boolean testStatus() {
        synchronized (this.activityLock) {
            final boolean bWorking = testConnection();

            if (!bWorking && (this.state.get() == STATE_ONLINE)) {
                logger.log(Level.FINE, "DB backend " + this.backendName + " is offline");
                updateState(STATE_OFFLINE);
            }

            return bWorking;
        }
    }

    /**
     * Log one query to a log file. In case this function returns <code>false</code> once, it means
     * the synchronization is lost and this backend will be marked as offline for good. The only way
     * to get out of this state is to re-initialize the database, remove the "status" file from this
     * backend's log directory and restart the store client.
     * 
     * @param sQuery query to log
     * @return true if everything was ok, false if the query could not be logged.
     */
    private boolean log(final String sQuery) {
        if (this.state.get() == STATE_ERROR) {
            // synchronization was lost, we don't have a chance to sync again
            return false;
        }

        if ((this.writtenRecords > MAX_RECORDS_PER_FILE) || (this.logFile == null)) {
            if (!openLog()) {
                updateState(STATE_ERROR);
                return false;
            }

            this.writtenRecords = 0;
        }

        this.logFile.println(Formatare.encode(sQuery));

        this.writtenRecords++;

        if (this.logFile.checkError()) {
            updateState(STATE_ERROR);
            return false;
        }

        return true;
    }

    /**
     * Drain the queue to log file
     */
    private void drainToLog() {
        synchronized (this.activityLock) {
            String query;

            boolean bOk = true;

            while ((query = this.updateQueue.poll()) != null) {
                bOk = bOk && log(query);
            }
        }
    }

    @Override
    public void run() {
        final boolean isMemOnly = ((AppConfig.getb("lia.Monitor.memory_store_only", false) || (AppConfig.geti(
                "lia.Monitor.Store.TransparentStoreFast.web_writes", -1) <= 0)) && !AppConfig.getb("lia.Monitor.Store.forceDBManagement", false));
        
        if (isMemOnly) {
            return;
        }
        
        final int initialState = this.state.get();
        
        // test the connection status
        if (testConnection() && (initialState == STATE_OFFLINE)) {
            // the backend was offline when we last ran but now it's back online
            // start recovery immediately

            startRecovery();
        }

        while (true) {
            try {
                final String query = this.updateQueue.poll(60, TimeUnit.SECONDS);

                //System.err.println("Thread tick, state="+state.get()+":"+query);

                if ((this.state.get() == STATE_ONLINE) && (query != null)) {
                    // happy case, backend is online and a query was received
                    final DBConnectionWrapper db = getConnection();

                    final boolean bOk = db.query(query, true);

                    db.close();

                    // start logging only if there is a problem talking to the database
                    if (db.isConnectionProblem()) {
                        logger.log(Level.WARNING, this.backendName + " cannot be reached, switching to offline mode");

                        updateState(STATE_OFFLINE);
                        synchronized (this.activityLock) {
                            log(query);
                        }
                        drainToLog();
                    } else if (!bOk) {
                        // query failed from other problems (consistency?)
                        logger.log(Level.WARNING, "Failed executing query on " + this.backendName + "\n" + query + "\n"
                                + db.getLastError());
                    }

                    continue;
                }

                if (this.state.get() == STATE_OFFLINE) {
                    // can it be that the database is back online ?

                    if (testConnection()) {
                        startRecovery();
                    }
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    /**
     * Current recovery thread
     */
    private Thread recoveryThread = null;

    /**
     * Start recovery from log files
     * 
     * @return true if the recovery thread was started, false if the thread was already running
     */
    private boolean startRecovery() {
        if ((this.recoveryThread == null) || !this.recoveryThread.isAlive()) {
            logger.log(Level.INFO, this.backendName + ": backend is alive again, start recovery");

            this.recoveryThread = new Thread("Recovery for " + this.backendName) {
                @Override
                public void run() {
                    recover();
                }
            };

            this.recoveryThread.setDaemon(true);
            this.recoveryThread.start();

            // give the recovery thread a chance to start
            yield();

            return true;
        }

        logger.log(Level.INFO, this.backendName + ": recovery already in progress");

        return false;
    }

    /**
     * Execute recovery from log files
     */
    void recover() {
        updateState(STATE_RECOVERING);

        ArrayList<Long> timestamps;

        String sResumeFile = "";
        int iResumeLine = 0;

        BufferedReader brResume = null;

        try {
            brResume = new BufferedReader(new FileReader(new File(this.baseFolder, RESUME_FILENAME)));

            sResumeFile = brResume.readLine();

            final String sResumeLine = brResume.readLine();

            iResumeLine = sResumeLine == null ? 0 : Integer.parseInt(sResumeLine);

            brResume.close();
            brResume = null;
        } catch (IOException e) {
            // ignore
        } finally {
            if (brResume != null) {
                try {
                    brResume.close();
                } catch (IOException ioe) {
                    // ignore
                }
            }
        }

        logger.log(Level.FINE, this.backendName + ": recovery resume from: " + sResumeFile + ":" + iResumeLine);

        do {
            // list files from the folder
            final String[] files = this.baseFolder.list();
            timestamps = new ArrayList<Long>(files.length);

            // filter only the ones that look like timestamps
            for (String file : files) {
                //System.err.println("File: "+files[i]);

                try {
                    timestamps.add(Long.valueOf(file));
                } catch (Exception e) {
                    // ignore files that are not timestamps
                }
            }

            // sort in time
            Collections.sort(timestamps);

            //System.err.println("Files: "+timestamps);

            for (int i = 0; i < timestamps.size(); i++) {
                final String sFilename = "" + timestamps.get(i);

                logger.log(Level.INFO, this.backendName + ": executing contents of '" + sFilename + "'");

                if (sFilename.equals(this.activeLogFile)) {
                    synchronized (this.activityLock) {
                        this.recoveryFinishAttempt.set(true);
                        closeLog();
                    }
                }

                if (!importFile(sFilename, sResumeFile, iResumeLine)) {
                    updateState(STATE_OFFLINE);
                    this.recoveryFinishAttempt.set(false);
                    return;
                }
            }
        } while (timestamps.size() > 0);

        updateState(STATE_ONLINE);
        this.recoveryFinishAttempt.set(false);
    }

    /**
     * Save the resume state to file.
     * 
     * @param sFilename current log file name
     * @param importedLines current log position (in number of lines)
     * @return true if the state was saved ok, false if the resume file could not be created
     */
    boolean saveResume(final String sFilename, final int importedLines) {
        PrintWriter pw = null;

        try {
            pw = new PrintWriter(new FileWriter(new File(this.baseFolder, RESUME_FILENAME)));

            pw.println(sFilename);
            pw.println(importedLines);

            pw.flush();
            pw.close();

            pw = null;

            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, this.backendName + ": cannot save recovery resume checkpoint", e);

            return false;
        } finally {
            if (pw != null) {
                try {
                    pw.flush();
                    pw.close();
                } catch (Exception e) {
                    // ignore;
                }
            }
        }
    }

    /**
     * Access lock
     */
    Object oLock = new Object();

    /**
     * File name where to resume from
     */
    volatile String sResumeFilename = null;

    /**
     * Line number that we have reached
     */
    volatile int iResumeImportedLines = 0;

    /**
     * Periodically save the position
     */
    volatile ResumeSaveThread asyncSaver = null;

    private void asyncSaveResume(final String sFilename, final int importedLines) {
        synchronized (oLock) {
            sResumeFilename = sFilename;
            iResumeImportedLines = importedLines;

            if (asyncSaver == null) {
                asyncSaver = new ResumeSaveThread();
                asyncSaver.start();
            } else {
                oLock.notifyAll();
            }
        }
    }

    /**
     * Save the log position every second. The thread will exit if there is no change in the 
     * position for 1 minute, but another one will be created on the fly if necessary.
     * 
     * @author costing
     */
    private class ResumeSaveThread extends Thread {
        public ResumeSaveThread() {
            super("AsyncResumeSaver for " + DatabaseBackend.this.getName());
            setDaemon(true);
        }

        @Override
        public void run() {
            while (true) {
                synchronized (oLock) {
                    if (sResumeFilename != null) {
                        saveResume(sResumeFilename, iResumeImportedLines);

                        sResumeFilename = null;
                    } else {
                        asyncSaver = null;
                        break;
                    }
                }

                try {
                    sleep(1000);
                } catch (InterruptedException ie) {
                    // ignore
                }

                synchronized (oLock) {
                    if (sResumeFilename == null) {
                        try {
                            oLock.wait(60000);
                        } catch (InterruptedException ie) {
                            // ignore
                        }
                    }
                }
            }
        }
    }

    /**
     * Remove the resume file, since it's no longer needed
     * 
     * @return true if the file was removed, false if not (not existing?)
     */
    private boolean clearResume() {
        synchronized (oLock) {
            sResumeFilename = null;
            iResumeImportedLines = 0;
        }

        return new File(this.baseFolder, RESUME_FILENAME).delete();
    }

    /**
     * Execute the contents of this log file
     * 
     * @param sFilename log file name
     * @param resumeFilename is this the resume file ?
     * @param skipLines how many lines to skip if sFilename.equals(resumeFilename) ?
     * @return true if the file was fully imported, false if not
     */
    private boolean importFile(final String sFilename, final String resumeFilename, final int skipLines) {
        final File f = new File(this.baseFolder, sFilename);

        BufferedReader br = null;

        final DBConnectionWrapper db = getConnection();

        try {
            br = new BufferedReader(new FileReader(f));

            String sLine;

            int readLines = 0;

            if (sFilename.equals(resumeFilename) && (skipLines > 0)) {
                logger.log(Level.INFO, "Skipping " + skipLines + " from " + sFilename);

                int remaining = skipLines;

                while (remaining > 0) {
                    sLine = br.readLine();

                    if (sLine == null) {
                        logger.log(Level.INFO, "Reached EOF of " + sFilename + " while skipping (" + remaining
                                + " lines left to skip). I will delete the file now.");

                        // hmmm, actually everything was imported
                        if (!f.delete()) {
                            logger.log(Level.SEVERE, "Cannot delete " + f
                                    + ". Correct the permissions and I'll resume.");
                            updateState(STATE_OFFLINE);
                            return false;
                        }

                        clearResume();
                        return true;
                    }

                    remaining--;
                }

                readLines = skipLines;
            }

            while ((sLine = br.readLine()) != null) {
                final String sQuery = Formatare.decode(sLine);

                Thread.currentThread().setName(
                        "Recovery for " + this.backendName + " at : " + sFilename + ":" + readLines);

                final boolean bOk = db.query(sQuery, true);

                db.close();

                if (!db.isConnectionProblem()) {
                    readLines++;

                    if (!bOk) {
                        logger.log(Level.WARNING, "Failed executing logged query on " + this.backendName + "\n"
                                + sQuery);
                    }

                    asyncSaveResume(sFilename, readLines);
                } else {
                    logger.log(Level.WARNING, this.backendName + ": cannot connect to execute query from '" + sFilename
                            + "':" + readLines + " :\n" + sQuery);
                    return false;
                }
            }

            logger.log(Level.INFO, "Reached EOF of " + sFilename + ", " + readLines + " total lines");

            br.close();
            br = null;

            if (!f.delete()) {
                logger.log(Level.SEVERE, "Cannot delete " + f + ". Correct the permissions and I'll resume.");
                updateState(STATE_OFFLINE);
                return false;
            }

            clearResume();

            return true;
        } catch (IOException e) {
            logger.log(Level.WARNING, "IO Exception while reading and executing query log", e);

            return false;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ioe) {
                    // ignore
                }
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (sResumeFilename != null) {
            saveResume(sResumeFilename, iResumeImportedLines);
        }

        drainToLog();
    }

    /**
     * Number of waiting queries in the queue, +1 if some query is currently under execution
     * 
     * @return number of waiting + executing queries
     */
    private int waitingQueries() {
        return this.updateQueue.size() + this.aiActiveQueries.get();
    }

}
