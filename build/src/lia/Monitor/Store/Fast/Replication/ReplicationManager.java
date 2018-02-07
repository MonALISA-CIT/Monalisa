package lia.Monitor.Store.Fast.Replication;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.util.MLProperties;
import lia.web.utils.DoubleFormat;

/**
 * This class is responsible for handling the database backends, executing queries and replicating them if necessary
 * 
 * @author costing
 * @since Jan 20, 2009
 */
public final class ReplicationManager {

    /**
     * logger
     */
    private static final Logger logger = Logger.getLogger(ReplicationManager.class.getName());

    /**
     * Singleton
     */
    private static ReplicationManager instance;

    /**
     * Get the active instance of the ReplicationManager
     * 
     * @return the only instance
     */
    public static synchronized ReplicationManager getInstance() {
        if (instance == null) {
            instance = new ReplicationManager();
        }

        return instance;
    }

    /**
     * Available database backends
     */
    private ArrayList<DatabaseBackend> backends;

    /**
     * Configuration lock
     */
    private final ReadWriteLock configLock = new ReentrantReadWriteLock();

    /**
     * Read lock, for normal operations
     */
    private final Lock configReadLock = this.configLock.readLock();

    /**
     * Whenever there is a change, reload
     */
    private final Lock configWriteLock = this.configLock.writeLock();

    /**
     * The constructor is private, call {@link #getInstance()} instead.
     */
    private ReplicationManager() {
        init();
    }

    /**
     * Read settings from the configuration file and create the internal objects to represent it
     */
    private void init() {
        this.configWriteLock.lock();

        try {
            this.backends = new ArrayList<DatabaseBackend>();

            for (int i = 0; i < 128; i++) {
                final String sPrefix = "lia.Monitor." + (i == 0 ? "" : i + ".");

                final String driver = AppConfig.getProperty(sPrefix + "jdbcDriverString");

                if ((driver == null) || (driver.length() == 0)) {
                    break;
                }

                final String server = AppConfig.getProperty(sPrefix + "ServerName", "127.0.0.1");
                final String dbname = AppConfig.getProperty(sPrefix + "DatabaseName", "mon_data");
                final String port = AppConfig.getProperty(sPrefix + "DatabasePort", "5432");
                final String user = AppConfig.getProperty(sPrefix + "UserName", "mon_user");
                final String pass = AppConfig.getProperty(sPrefix + "Pass", "mon_pass");
                final String sconn = AppConfig.getProperty(sPrefix + "ConnectionString");

                // what should be the name of this backend?
                final String name = AppConfig.getProperty(sPrefix + "Name", server + ":" + port + ":" + dbname);

                final MLProperties prop = new MLProperties();
                prop.set("driver", driver);
                prop.set("host", server);
                prop.set("port", port);
                prop.set("database", dbname);
                prop.set("user", user);
                prop.set("password", pass);

                if ((sconn != null) && (sconn.length() > 0)) {
                    prop.set("conn_string", sconn);
                }

                final DatabaseBackend backend = new DatabaseBackend(name, prop);
                backend.start();

                this.backends.add(backend);

                logger.log(Level.INFO, "DB backend: " + backend);
            }
        } finally {
            this.configWriteLock.unlock();
        }
    }

    /**
     * Get the total number of configured backends
     * 
     * @return total number of configured backends
     */
    public int getBackendsCount() {
        this.configReadLock.lock();

        try {
            return this.backends.size();
        } finally {
            this.configReadLock.unlock();
        }
    }

    /**
     * Get all the online backends
     * 
     * @return an ArrayList of DatabaseBackend objects
     */
    private ArrayList<DatabaseBackend> getOnlineBackends() {
        this.configReadLock.lock();

        final ArrayList<DatabaseBackend> onlineBackends = new ArrayList<DatabaseBackend>(this.backends.size());

        try {
            for (int i = this.backends.size() - 1; i >= 0; i--) {
                final DatabaseBackend backend = this.backends.get(i);

                if (backend.isOnline()) {
                    onlineBackends.add(backend);
                }
            }
        } finally {
            this.configReadLock.unlock();
        }

        return onlineBackends;
    }

    /**
     * Get the number of backends that are online now
     * 
     * @return number of active backends
     */
    public int getOnlineBackendsCount() {
        return getOnlineBackends().size();
    }

    /**
     * Execute a query
     * 
     * @param sQuery query to execute
     * @return the response
     * @see #query(String, boolean, boolean)
     */
    public DBConnectionWrapper query(final String sQuery) {
        return query(sQuery, false);
    }

    /**
     * Execute a query, possibly ignoring the generated errors
     * 
     * @param sQuery query to execute
     * @param ignoreErrors flag to ignore errors or not
     * @return the response
     * @see #query(String, boolean, boolean)
     */
    public DBConnectionWrapper query(final String sQuery, final boolean ignoreErrors) {
        return query(sQuery, ignoreErrors, false);
    }

    /**
     * Execute a query on all backends. When this method finishes the query was executed on all
     * online backends and scheduled for later execution in the offline ones.
     * 
     * @param sQuery query to execute
     * @see #syncUpdateQuery(String, boolean)
     * @return a connection wrapper to one of the backends that executed the query successfully, <code>null</code> if none did 
     */
    public DBConnectionWrapper syncUpdateQuery(final String sQuery) {
        return syncUpdateQuery(sQuery, false);
    }

    /**
     * Execute a query on all backends. When this method finishes the query was executed on all
     * online backends and scheduled for later execution in the offline ones.
     * 
     * @param sQuery query to execute
     * @param ignoreErrors whether or not to ignore errors
     * @return a connection wrapper to one of the backends that executed the query successfully, <code>null</code> if none did 
     */
    public DBConnectionWrapper syncUpdateQuery(final String sQuery, final boolean ignoreErrors) {
        this.configReadLock.lock();

        DBConnectionWrapper ret = null;

        try {
            for (int i = this.backends.size() - 1; i >= 0; i--) {
                final DatabaseBackend backend = this.backends.get(i);

                boolean bExecuted = false;

                if (backend.isOnline()) {
                    final DBConnectionWrapper db = backend.getConnection();

                    if (db.query(sQuery, ignoreErrors) && (ret == null)) {
                        ret = db;
                    }

                    bExecuted = !db.isConnectionProblem();
                }

                if (!bExecuted) {
                    backend.queueUpdate(sQuery);
                }
            }
        } finally {
            this.configReadLock.unlock();
        }

        return ret;
    }

    private static Random r = new Random(System.currentTimeMillis());

    /**
     * Execute an SQL query
     *
     * @param sQuery SQL query string
     * @param ignoreErrors ignore SQL errors
     * @param broadcastAlways force the query to be submitted to all backends 
     * 
     * @return the connection wrapper, or <code>null</code> if the query could not be executed on any of the backends
     */
    public DBConnectionWrapper query(final String sQuery, final boolean ignoreErrors, final boolean broadcastAlways) {
        return query(sQuery, ignoreErrors, broadcastAlways, null, ResultSet.TYPE_FORWARD_ONLY, false);
    }

    /**
     * Execute an SQL query
     *
     * @param sQuery SQL query string
     * @param ignoreErrors ignore SQL errors
     * @param broadcastAlways force the query to be submitted to all backends
     * @param previous previous connection. If not <code>null</code> then it will try to use the same backend. 
     * @param cursorType ResultSet cursor type
     * @param readOnly optimize for SELECT queries
     * 
     * @return the connection wrapper, or <code>null</code> if the query could not be executed on any of the backends
     */
    public DBConnectionWrapper query(final String sQuery, final boolean ignoreErrors, final boolean broadcastAlways,
            final DBConnectionWrapper previous, final int cursorType, final boolean readOnly) {
        final ArrayList<DatabaseBackend> onlineBackends = getOnlineBackends();

        final int size = onlineBackends.size();

        if (size == 0) {
            // no backend is online

            return null;
        }

        int startBy = -1;

        if ((previous != null) && (previous.dbBackend != null)) {
            for (int i = 0; i < size; i++) {
                if (onlineBackends.get(i) == previous.getBackend()) {
                    startBy = i;
                    break;
                }
            }
        }

        if (startBy < 0) {
            startBy = r.nextInt(size);
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "online backends:\n" + onlineBackends);
        }

        logAndResetStats();

        for (int i = 0; i < size; i++) {
            final DatabaseBackend backend = onlineBackends.get((i + startBy) % size);

            if (i == 0) {
                backend.incrementPicked();
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Picked " + backend);
            }

            final DBConnectionWrapper db = backend.getConnection();

            db.setCursorType(cursorType);
            db.setReadOnly(readOnly);
            
            if (!db.query(sQuery, ignoreErrors)) {
                // could not connect to the server to execute the query
                // in this case we'll try the next server in chain
                if (db.isConnectionProblem()) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "Query failed, testing " + backend + " if it can still talk to the DB");
                    }

                    backend.testStatus();
                    continue;
                }
            } else {
                if (db.isUpdateQuery() || broadcastAlways) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "Broadcasting update query: " + sQuery);
                    }

                    // broadcast update only when something has changed
                    queueUpdate(sQuery, backend);
                }
            }

            return db;
        }

        // No DB backend was available to execute the query.

        return null;
    }

    private long lLastLog = System.currentTimeMillis();

    /**
     * Log usage statistics
     */
    private synchronized void logAndResetStats() {
        if ((System.currentTimeMillis() - this.lLastLog) > (1000 * 60)) {
            this.lLastLog = System.currentTimeMillis();

            this.configReadLock.lock();

            try {
                if (logger.isLoggable(Level.FINE)) {
                    final StringBuilder sb = new StringBuilder("Backends:\n");

                    for (int i = 0; i < backends.size(); i++) {
                        final DatabaseBackend db = backends.get(i);

                        sb.append("\t");
                        sb.append(db.getBackendName()).append(" :\t");
                        sb.append(db.isOnline() ? "online,\t" : "offline,\t");
                        sb.append(db.getQueryCount()).append(" queries,\t");
                        sb.append(DoubleFormat.point(db.getAverageQueryTime())).append("ms average query time,\t");
                        sb.append(db.getPickedCount()).append(" picked count");
                        sb.append("\n");

                        db.resetQueryTimers();
                    }

                    logger.log(Level.FINE, sb.toString());
                } else {
                    for (int i = 0; i < backends.size(); i++) {
                        final DatabaseBackend db = backends.get(i);
                        db.resetQueryTimers();
                    }
                }
            } finally {
                this.configReadLock.unlock();
            }
        }
    }

    /**
     * Send an update to all backends, apart from one (the one that just had a sync update)
     * 
     * @param sQuery query to execute
     * @param exclude the backend to exclude, or <code>null</code> for queuing the query on all backends
     */
    private void queueUpdate(final String sQuery, final DatabaseBackend exclude) {
        this.configReadLock.lock();

        try {
            for (int i = this.backends.size() - 1; i >= 0; i--) {
                final DatabaseBackend backend = this.backends.get(i);

                if (backend != exclude) {
                    backend.queueUpdate(sQuery);
                }
            }
        } finally {
            this.configReadLock.unlock();
        }
    }

    /**
     * Execute the given query in background on all backends
     * 
     * @param sQuery
     */
    public void asyncUpdate(final String sQuery) {
        queueUpdate(sQuery, null);
    }
}
