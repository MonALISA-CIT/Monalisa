package lia.Monitor.Store.Fast.Replication;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.util.MLProperties;
import lia.web.utils.Formatare;

/**
 * Wrapper for JDBC connections and collection of useful functions related to database connections. It is also a connection pool, recycling previously established sessions and closing the idle ones.
 * 
 * @author costing
 * @since Oct 15, 2006
 */
public class DBConnectionWrapper implements Closeable {
	/**
	 * Java logger
	 */
	static final Logger logger = Logger.getLogger(DBConnectionWrapper.class.getName());

	/**
	 * List of connections for each known target
	 */
	static final HashMap<String, LinkedList<DBConnection>> hmConn = new HashMap<String, LinkedList<DBConnection>>();

	/**
	 * Synchronization object for sensitive parts
	 */
	static final Object oConnLock = new Object();

	/**
	 * Is this the first line?
	 */
	private boolean first;

	/**
	 * Is this an update query ?
	 */
	private boolean bIsUpdate;

	/**
	 * The actual database connection
	 */
	private DBConnection dbc;

	/**
	 * Current result set
	 */
	private ResultSet rsRezultat;

	/**
	 * Configuration options
	 */
	final DatabaseBackend dbBackend;

	/**
	 * The error message from the last operation
	 * 
	 * @see #getLastError()
	 */
	private String sError;

	/**
	 * Set to <code>true</code> to signal that the following query is read-only and, if available, a slave could be used to execute it
	 */
	private boolean readOnlyQuery = false;

	/**
	 * Cursor type, defaulting to FORWARD_ONLY.
	 */
	private int cursorType = ResultSet.TYPE_FORWARD_ONLY;
	
	private static final int MAX_CONNECTIONS = AppConfig.geti("lia.Monitor.Store.Fast.Replication.MAX_CONNECTIONS", 100);

	/**
	 * Create a connection to the database using the parameters in this properties file. The following keys are extracted:<br>
	 * <ul>
	 * <li><b>driver</b> : org.postgresql.Driver, com.mysql.jdbc.Driver ...</li>
	 * <li><b>host</b> : server's ip address, defaults to 127.0.0.1</li>
	 * <li><b>port</b> : tcp port to connect to on the <i>host</i>, if it is missing the standard port for each database type is used</li>
	 * <li><b>database</b> : name of the database to connect to</li>
	 * <li><b>user</b> : supply this account name when connecting</li>
	 * <li><b>password</b> : password for the account</li>
	 * </ul>
	 * Any other keys will be passed as arguments to the driver. You might be interested in:<br>
	 * <ul>
	 * <li><a href="http://dev.mysql.com/doc/refman/5.1/en/connector-j-reference-configuration-properties.html" target=_blank>MySQL</a>:
	 * <ul>
	 * <li><b>connectTimeout</b> : timeout in milliseconds for a new connection, default is 0 (infinite)</li>
	 * <li><b>useCompression</b> : true/false, default false</li>
	 * </ul>
	 * </li>
	 * <li><a href="http://jdbc.postgresql.org/documentation/82/connect.html" target=_blank>PostgreSQL</a>:
	 * <ul>
	 * <li><b>ssl</b> : present=true for now</li>
	 * <li><b>charSet</b> : string</li>
	 * </ul>
	 * </li>
	 * </ul>
	 * check the provided links for more information.
	 * 
	 * @param backend
	 *            backend
	 */
	DBConnectionWrapper(final DatabaseBackend backend) {
		this.dbBackend = backend;
		this.dbc = null;
		this.first = false;
		this.bIsUpdate = true;
		this.rsRezultat = null;
	}

	/**
	 * Create a connection to the database using the parameters in this properties file, then execute the given query.
	 * 
	 * @param backend
	 *            backend
	 * @param sQuery
	 *            query to execute after connecting
	 * @see DBConnectionWrapper#DBConnectionWrapper(DatabaseBackend)
	 */
	DBConnectionWrapper(final DatabaseBackend backend, final String sQuery) {
		this(backend);

		query(sQuery);
	}

	/**
	 * Get a free connection from the pool
	 * 
	 * @param sConn
	 *            connection key
	 * @return a free connection, or <code>null</code> if there is none free
	 */
	private static final DBConnection getFreeConnection(final String sConn, final boolean readOnly) {
		synchronized (oConnLock) {
			LinkedList<DBConnection> ll = hmConn.get(sConn);

			if (ll != null) {
				final Iterator<DBConnection> it = ll.iterator();

				DBConnection usable = null;

				while (it.hasNext()) {
					final DBConnection dbt = it.next();

					if (dbt.canUse()) {
						boolean bErrorConn = false;

						try {
							bErrorConn = dbt.conn.isClosed();
						} catch (final Exception e) {
							logger.log(Level.FINE, "Error querying connection status", e);

							bErrorConn = true;
						}

						if (bErrorConn) {
							dbt.close();
							it.remove();
							continue;
						}

						if (dbt.isReadOnly() == readOnly) {
							dbt.use();
							return dbt;
						}

						usable = dbt;
					}
				}

				if (usable != null) {
					usable.use();
					return usable;
				}
			} else {
				ll = new LinkedList<DBConnection>();
				hmConn.put(sConn, ll);
			}
		}

		return null;
	}

	/**
	 * Get the database backend that describes this connection
	 * 
	 * @return the DatabaseBackend that created this object
	 */
	public DatabaseBackend getBackend() {
		return this.dbBackend;
	}

	/**
	 * Build a unique key
	 * 
	 * @return unique connection key
	 */
	private String getKey() {
		final MLProperties prop = this.dbBackend.getConfig();

		if (prop == null)
			return "?";

		final String sConn = prop.gets("conn_string");

		if (sConn.length() > 0)
			return sConn;

		return prop.gets("driver") + "/" + prop.gets("host", "127.0.0.1") + "/" + prop.gets("port") + "/" + prop.gets("database") + "/" + prop.gets("user") + "/" + prop.gets("password");
	}

	/**
	 * Signal that the following query is read-only and, if available, a database slave could be used to execute it.
	 * 
	 * @param readOnly
	 *            if <code>true</code> then the query can potentially go to a slave, if <code>false</code> then only the master can execute it
	 * @return previous value of the read-only flag
	 */
	public boolean setReadOnly(final boolean readOnly) {
		final boolean previousValue = this.readOnlyQuery;

		this.readOnlyQuery = readOnly;

		return previousValue;
	}

	/**
	 * Get the current value of the read-only flag
	 * 
	 * @return read-only flag
	 */
	public boolean isReadOnly() {
		return this.readOnlyQuery;
	}

	/**
	 * Set the cursor type to one of the ResultSet.TYPE_FORWARD_ONLY (default), ResultSet.TYPE_SCROLL_INSENSITIVE (when you need {@link #count()} or such) and so on.
	 * 
	 * @param type
	 *            new cursor type
	 * @return previous cursor type
	 */
	public int setCursorType(final int type) {
		final int previousType = this.cursorType;

		this.cursorType = type;

		return previousType;
	}

	/**
	 * @return cursor type
	 */
	public int getCursorType() {
		return this.cursorType;
	}

	/**
	 * Check if this connection is done to a PostgreSQL database (if we are using the PG JDBC driver)
	 * 
	 * @return true if the connection is done to a PostgreSQL database
	 */
	public boolean isPostgreSQL() {
		final MLProperties prop = this.dbBackend.getConfig();

		return (prop != null) && (prop.gets("driver").toLowerCase(Locale.getDefault()).indexOf("postgres") >= 0);
	}

	/**
	 * Check if this connection is done to a MySQL database (if we are using the MySQL JDBC driver)
	 * 
	 * @return true if the connection is done to a MySQL database
	 */
	public boolean isMySQL() {
		final MLProperties prop = this.dbBackend.getConfig();

		return (prop != null) && (prop.gets("driver").toLowerCase(Locale.getDefault()).indexOf("mysql") >= 0);
	}

	/**
	 * Check if this connection is to an Oracle instance
	 * 
	 * @return true if the connection is to an Oracle instance
	 */
	public boolean isOracle() {
		final MLProperties prop = this.dbBackend.getConfig();

		if (prop == null)
			return false;

		return (prop.gets("driver").toLowerCase().indexOf("oracle") >= 0) || (prop.gets("conn_string").toLowerCase().indexOf("oracle") >= 0);
	}

	/**
	 * Was there a connectivity problem or a query issue?
	 */
	private boolean connectionProblem = false;

	/**
	 * Check whether the last query failed because a connection could not be established or because an internal error in the query
	 * 
	 * @return true if the connection was our problem, false if the query failed from other reasons
	 */
	boolean isConnectionProblem() {
		return this.connectionProblem;
	}

	/**
	 * Try to get a free connection from the pool, or create a new one.
	 * 
	 * @return true if a connection was acquired, false if not
	 */
	private final boolean connect() {
		final String sConn = getKey();

		for (int i = 0; i < 3; i++) {
			this.dbc = getFreeConnection(sConn, isReadOnly());

			if (this.dbc != null)
				return true;

			synchronized (oConnLock) {
				final LinkedList<DBConnection> ll = hmConn.get(sConn);

				if (ll.size() < MAX_CONNECTIONS) {
					this.dbc = new DBConnection(this.dbBackend.getConfig(), sConn);
					if (this.dbc.canUse()) {
						this.dbc.use();
						ll.add(this.dbc);
						return true;
					}
					this.dbc.close();
					this.dbc = null;
				}
			}

			try {
				Thread.sleep(50);
			} catch (final InterruptedException e) {
				// ignore improbable interruption
			}
		}

		return false;
	}

	static {
		System.setProperty("PGDATESTYLE", "ISO");
	}

	/**
	 * Database connection wrapper
	 * 
	 * @author costing
	 */
	private static final class DBConnection {

		/**
		 * Actual JDBC connection
		 */
		Connection conn;

		/**
		 * 0 - not connected 1 - ready 2 - in use 3 - error or disconnected
		 */
		int iBusy;

		/**
		 * When this cached connection was last used
		 */
		long lLastAccess;

		/**
		 * Connection key
		 */
		private final String sConn;

		private boolean readOnly = false;

		/**
		 * Establish a new connection
		 * 
		 * @param prop
		 *            connection properties
		 * @param _sConn
		 *            connection key
		 */
		public DBConnection(final MLProperties prop, final String _sConn) {
			this.conn = null;
			this.iBusy = 0;

			this.sConn = _sConn;

			final String driver = prop.gets("driver");

			try {
				Class.forName(driver);
			} catch (final Exception e) {
				logger.log(Level.SEVERE, "Cannot find driver '" + driver + "'", e);
				this.iBusy = 3;
				return;
			}

			String connection = null;

			try {
				connection = prop.gets("conn_string");

				if (connection.length() == 0) {
					connection = "jdbc:";

					if (driver.indexOf("mysql") >= 0)
						connection += "mysql:";
					else if (driver.indexOf("postgres") >= 0)
						connection += "postgresql:";
					else {
						// UNKNOWN DRIVER
						this.iBusy = 3;
						return;
					}

					connection += "//" + prop.gets("host", "127.0.0.1");

					if (prop.gets("port").length() > 0)
						connection += ":" + prop.gets("port");

					connection += "/" + prop.gets("database");
				}

				this.conn = DriverManager.getConnection(connection, prop.getProperties());
				this.iBusy = 1;
			} catch (final SQLException e) {
				logger.log(Level.FINE, "Cannot connect to the database with connection string: '" + connection + "'", e);

				this.iBusy = 3;
			}
		}

		/**
		 * Get the established connection
		 * 
		 * @return the JDBC connection
		 */
		public final Connection getConnection() {
			return this.conn;
		}

		/**
		 * Find out if this connection is free to use
		 * 
		 * @return true if free, false if busy or other error
		 */
		public final boolean canUse() {
			if (this.iBusy == 1) {
				if (System.currentTimeMillis() - this.lLastAccess > 1000 * 60) {
					boolean isValid = false;

					try {
						isValid = this.conn.isValid(10);
					} catch (final SQLException sqle) {
						// ignore
					}

					if (!isValid)
						close();

					return isValid;
				}

				return true;
			}

			return false;
		}

		/**
		 * Use this connection, by marking it as busy and setting the last access time to the current time.
		 * 
		 * @return true if the connection was free and could be used, false if it was not available
		 */
		public final boolean use() {
			if (this.iBusy == 1) {
				this.iBusy = 2;

				this.lLastAccess = System.currentTimeMillis();

				return true;
			}

			return false;
		}

		/**
		 * Mark a previously used connection as free to be used by somebody else
		 * 
		 * @return true if the connection was in use and was freed, false if the connection was in other state
		 */
		public final boolean free() {
			if (this.iBusy == 2) {
				this.iBusy = 1;
				return true;
			}
			close();
			return false;
		}

		/**
		 * Really close a connection to the database
		 */
		public final void close() {
			if (this.conn != null) {
				try {
					this.conn.close();
				} catch (final Exception e) {
					logger.log(Level.WARNING, "Cannot close " + this.sConn, e);
				}

				this.conn = null;
			}

			this.iBusy = 3;
		}

		/**
		 * On object deallocation make sure that the connection is properly closed.
		 */
		@Override
		protected final void finalize() {
			if (this.conn != null)
				try {
					this.conn.close();
				} catch (final Exception e) {
					logger.log(Level.WARNING, "Cannot close " + this.sConn + " on finalize", e);
				}
		}

		/**
		 * Check the (cached) status of this connection. Doesn't go to the driver to ask for it but instead this is the last requested state.
		 * 
		 * @return the read-only status of this connection
		 */
		public boolean isReadOnly() {
			return this.readOnly;
		}

		/**
		 * Set the read-only connection flag. Only goes to the driver with the request in case the flag is different from the previously known value.
		 * 
		 * @param newValue
		 * @return previous value of the read only flag
		 */
		public boolean setReadOnly(final boolean newValue) {
			final boolean wasReadOnly = this.readOnly;

			if (newValue != this.readOnly)
				try {
					this.conn.setReadOnly(newValue);
					this.readOnly = newValue;
				} catch (final SQLException sqle) {
					// ignore
				}

			return wasReadOnly;
		}
	}

	/**
	 * @author costing
	 * @since 15.12.2006
	 */
	static class CleanupThread extends Thread {

		/**
		 * Create the thread with some name to display in the stack trace
		 */
		public CleanupThread() {
			super("lazyj.DBFunctions: cleanup thread");
		}

		/**
		 * Flag to stop the cleanup thread. Used only when the machine stops or smth ...
		 */
		boolean bShouldStop = false;

		/**
		 * Connection cleaner thread, periodically checks for too many idle connection and closes them.
		 */
		@Override
		public void run() {
			int iRunCount = 0;
			long now;
			LinkedList<DBConnection> ll;
			int iIdleCount;
			Iterator<DBConnection> it;
			DBConnection dbc;
			boolean bIdle;
			boolean bClose;
			int iTotalCount;
			int iUnclosed = 0;
			int iClosedToGC = 0;

			while (!this.bShouldStop) {
				now = System.currentTimeMillis();

				iTotalCount = 0;

				synchronized (oConnLock) {
					final Iterator<Map.Entry<String, LinkedList<DBConnection>>> itEntry = hmConn.entrySet().iterator();

					while (itEntry.hasNext()) {
						final Map.Entry<String, LinkedList<DBConnection>> me = itEntry.next();

						ll = me.getValue();

						iIdleCount = 0;
						it = ll.iterator();

						while (it.hasNext()) {
							dbc = it.next();

							bIdle = (dbc.iBusy == 1);

							if (bIdle)
								iIdleCount++;

							// - in use for more than 2 min, such a query takes too long and we should remove the connection from the pool
							// - limit the number of idle connections
							// - any connection left in an error state by a query
							bClose = ((dbc.iBusy == 2) && ((now - dbc.lLastAccess) > (1000 * 60 * 2))) || (bIdle && ((iIdleCount > 5) || ((now - dbc.lLastAccess) > (1000 * 60 * 5))))
									|| ((dbc.iBusy != 2) && (dbc.iBusy != 1)); // error case

							if (bClose) {
								iClosedToGC++;

								if (dbc.iBusy != 2)
									// is not in use
									dbc.close();
								else {
									logger.log(Level.INFO, "Not closing busy connection");
									iUnclosed++;
								}

								it.remove();
								if (bIdle)
									iIdleCount--;
							}
						}

						iTotalCount += ll.size();
					}

					iRunCount++;
				}

				// when we remove connection make sure the resources are really freed by JVM
				if (iClosedToGC > 20) {
					iClosedToGC = 0;
					System.gc();
					Thread.yield();
					// System.gc();
					// Thread.yield();
				}

				try {
					Thread.sleep(2000);
				} catch (final InterruptedException e) {
					// ignore an interruption
				}
			}
		}

	}

	/**
	 * Cleanup thread
	 */
	private static CleanupThread tCleanup = null;

	static {
		startThread();
	}

	/**
	 * Start the cleanup thread. Should not be called externally since it is called automatically at the first use of this class.
	 */
	static public final synchronized void startThread() {
		if (tCleanup == null) {
			tCleanup = new CleanupThread();
			try {
				tCleanup.setDaemon(true);
			} catch (final Throwable e) {
				// it's highly unlikely for an exception to occur here
			}
			tCleanup.start();
		}
	}

	/**
	 * Signal the thread that it's time to stop. You should only call this when the JVM is about to shut down, and not even then it's necessary to do so.
	 */
	static public final synchronized void stopThread() {
		if (tCleanup != null) {
			tCleanup.bShouldStop = true;
			tCleanup = null;
		}
	}

	/**
	 * Number of updated rows
	 */
	private int iUpdateCount = -1;

	/**
	 * Get the number of rows that were changed by the previous query.
	 * 
	 * @return number of changed rows, can be negative if the query was not an update one
	 */
	public final int getUpdateCount() {
		return this.iUpdateCount;
	}

	/**
	 * Current statement
	 */
	private Statement stat = null;

	@Override
	public void close() {
		if (this.rsRezultat != null) {
			try {
				this.rsRezultat.close();
			} catch (final Throwable t) {
				// ignore this
			}

			this.rsRezultat = null;
		}

		if (this.stat != null) {
			try {
				this.stat.close();
			} catch (final Throwable t) {
				// ignore this
			}

			this.stat = null;
		}

	}

	/**
	 * Override the default destructor to properly close any resources in use.
	 */
	@Override
	protected void finalize() {
		close();
	}

	/**
	 * Was the last query correctly executed ?
	 */
	private boolean lastQueryOK = false;

	/**
	 * Check if the query has executed ok or not
	 * 
	 * @return true if query was executed ok, false if not
	 */
	public boolean isLastQueryOK() {
		return this.lastQueryOK;
	}

	/**
	 * Execute a query.
	 * 
	 * @param sQuery
	 *            SQL query to execute
	 * @return true if the query succeeded, false if there was an error
	 */
	boolean query(final String sQuery) {
		return query(sQuery, false);
	}

	/**
	 * Execute an error and as an option you can force to ignore any errors, no to log them if you expect a query to fail.
	 * 
	 * @param sQuery
	 *            query to execute
	 * @param bIgnoreErrors
	 *            true if you want to hide any errors
	 * @return true if the query succeeded, false if there was an error
	 */
	final boolean query(final String sQuery, final boolean bIgnoreErrors) {
		this.lastQueryOK = executeQuery(sQuery, bIgnoreErrors);

		return this.lastQueryOK;
	}

	/**
	 * Execute a query
	 * 
	 * @param sQuery
	 *            query to execute
	 * @param bIgnoreErrors
	 *            ignore errors or not
	 * @return true if the query was correctly executed
	 */
	private boolean executeQuery(final String sQuery, final boolean bIgnoreErrors) {
		if (this.rsRezultat != null) {
			try {
				this.rsRezultat.close();
			} catch (final Throwable e) {
				// ignore this
			}

			this.rsRezultat = null;
		}

		if (this.stat != null) {
			try {
				this.stat.close();
			} catch (final Throwable e) {
				// ignore this
			}

			this.stat = null;
		}

		this.bIsUpdate = false;
		this.iUpdateCount = -1;
		this.first = false;

		final long lStartTime = System.currentTimeMillis();

		this.connectionProblem = false;

		this.sError = null;

		if (!connect()) {
			this.sError = "Cannot connect to the database " + getKey();

			logger.log(Level.FINE, this.sError);

			final long lTime = System.currentTimeMillis() - lStartTime;

			this.dbBackend.addQueryTime(lTime);

			this.connectionProblem = true;

			return false;
		}

		this.dbBackend.incrementUsage();

		final Connection conn = this.dbc.getConnection();

		try {
			this.dbc.setReadOnly(isReadOnly());

			this.stat = conn.createStatement(getCursorType(), ResultSet.CONCUR_READ_ONLY);

			if (this.stat.execute(sQuery, Statement.NO_GENERATED_KEYS))
				this.rsRezultat = this.stat.getResultSet();
			else {
				this.bIsUpdate = true;
				this.iUpdateCount = this.stat.getUpdateCount();

				this.stat.close();
				this.stat = null;
			}

			if (!this.bIsUpdate) {
				this.first = true;
				try {
					if (!this.rsRezultat.next())
						this.first = false;
				} catch (final Exception e) {
					this.first = false;
				}
			} else
				this.first = false;

			this.dbc.free();

			return true;
		} catch (final Exception e) {
			this.rsRezultat = null;
			this.first = false;

			sError = e.getMessage();

			boolean bFatalError = false;

			try {
				if (conn.isClosed()) {
					logger.log(Level.FINE, "Connection lost during query " + getKey());
					bFatalError = true;
				}
			} catch (final SQLException sqle) {
				logger.log(Level.FINE, "Error querying connection status", sqle);
				bFatalError = true;
			}

			if (bFatalError)
				this.connectionProblem = true;

			if (bFatalError || (!bIgnoreErrors && (sError.indexOf("duplicate key") < 0) && (sError.indexOf("drop table") < 0))) {
				logger.log(Level.WARNING, "Error executing query on " + getKey() + " (fatal error=" + bFatalError + ", ignore errors=" + bIgnoreErrors + ") : '" + sQuery + "'", e);
				// in case of an error, close the connection
				this.dbc.close();
			} else {
				// if the error is expected, or not fatal, silently free the connection for later use
				if (logger.isLoggable(Level.FINE))
					logger.log(Level.FINE, "Error executing query on " + getKey() + " (fatal error=" + bFatalError + ", ignore errors=" + bIgnoreErrors + ") : '" + sQuery + "'", e);

				this.dbc.free();
			}

			return false;
		} finally {
			final long lTime = System.currentTimeMillis() - lStartTime;

			this.dbBackend.decrementUsage();
			this.dbBackend.addQueryTime(lTime);
		}
	}

	/**
	 * Check if the last executed query was an update query or a select one.
	 * 
	 * @return true if update, false if select
	 * @see #isLastQueryOK()
	 */
	public boolean isUpdateQuery() {
		return this.bIsUpdate;
	}

	/**
	 * Get the error from the last operation
	 * 
	 * @return error message returned by the engine, or <code>null</code> if none (the operation was successful)
	 */
	public String getLastError() {
		return sError;
	}

	/**
	 * Get the number of rows that were selected by the previous query.
	 * 
	 * @return number of rows, or -1 if the query was not a select one or there was an error
	 */
	public final int count() {
		if (this.bIsUpdate || (this.rsRezultat == null))
			return -1;

		try {
			final int pos = this.rsRezultat.getRow();

			final boolean bFirst = this.rsRezultat.isBeforeFirst();
			final boolean bLast = this.rsRezultat.isAfterLast();

			this.rsRezultat.last();

			final int ret = this.rsRezultat.getRow();

			if (bFirst)
				this.rsRezultat.beforeFirst();
			else if (bLast)
				this.rsRezultat.afterLast();
			else if (pos <= 0)
				this.rsRezultat.first();
			else
				this.rsRezultat.absolute(pos);

			return ret;
		} catch (final Throwable t) {
			return -1;
		}
	}

	/**
	 * Get the current position in the result set
	 * 
	 * @return current position, -1 if there was an error
	 * @see ResultSet#getRow()
	 */
	public final int getPosition() {
		try {
			return this.rsRezultat.getRow();
		} catch (final Throwable t) {
			return -1;
		}
	}

	/**
	 * Jump an arbitrary number of rows.
	 * 
	 * @param count
	 *            can be positive or negative
	 * @return true if the jump was possible, false if not
	 * @see ResultSet#relative(int)
	 */
	public final boolean relative(final int count) {
		try {
			final boolean bResult = this.rsRezultat.relative(count);

			if (bResult)
				this.first = false;

			return bResult;
		} catch (final Throwable t) {
			return false;
		}
	}

	/**
	 * Jump to an absolute position in the result set
	 * 
	 * @param position
	 *            new position
	 * @return true if the positioning was possible, false otherwise
	 * @see ResultSet#absolute(int)
	 */
	public final boolean absolute(final int position) {
		try {
			final boolean bResult = this.rsRezultat.absolute(position);

			if (bResult)
				this.first = false;

			return bResult;
		} catch (final Throwable t) {
			return false;
		}
	}

	/**
	 * Jump to the next row in the result
	 * 
	 * @return true if there is a next entry to jump to, false if not
	 */
	public final boolean moveNext() {
		if (this.bIsUpdate)
			return false;

		if (this.first) {
			this.first = false;
			return true;
		}

		if (this.rsRezultat != null)
			try {
				if (!this.rsRezultat.next())
					return false;

				return true;
			} catch (final Exception e) {
				return false;
			}

		return false;
	}

	/**
	 * Get the string value of a column. By default will return "" if there is any problem (column missing, value is null ...)
	 * 
	 * @param sColumnName
	 *            column name
	 * @return the value for the column with the same name from the current row
	 */
	public final String gets(final String sColumnName) {
		return gets(sColumnName, "");
	}

	/**
	 * Get the string value of a column. It will return the given default if there is any problem (column missing, value is null ...)
	 * 
	 * @param sColumnName
	 *            column name
	 * @param sDefault
	 *            default value to return on error cases
	 * @return the value for the column with the same name from the current row
	 */
	public final String gets(final String sColumnName, final String sDefault) {
		if ((this.dbc == null) || (this.rsRezultat == null))
			return sDefault;

		try {
			final String sTemp = this.rsRezultat.getString(sColumnName);
			return ((sTemp == null) || this.rsRezultat.wasNull()) ? sDefault : sTemp.trim();
		} catch (final Throwable e) {
			return sDefault;
		}
	}

	/**
	 * Get the string value of a column. By default will return "" if there is any problem (column missing, value is null ...)
	 * 
	 * @param iColumn
	 *            column number (1 = first column of the result set)
	 * @return the value for the column
	 */
	public final String gets(final int iColumn) {
		return gets(iColumn, "");
	}

	/**
	 * Get the string value of a column. It will return the given default if there is any problem (column missing, value is null ...)
	 * 
	 * @param iColumn
	 *            column number (1 = first column of the result set)
	 * @param sDefault
	 *            default value to return on error cases
	 * @return the value for the column with the same name from the current row
	 */
	public final String gets(final int iColumn, final String sDefault) {
		if ((this.dbc == null) || (this.rsRezultat == null))
			return sDefault;
		try {
			final String sTemp = this.rsRezultat.getString(iColumn);
			return sTemp != null ? sTemp : sDefault;
		} catch (final Exception e) {
			return sDefault;
		}
	}

	/**
	 * Get the value of a column as a Date object. Will return the current date/time as default if there is a problem parsing the column.
	 * 
	 * @param sColumnName
	 *            column name
	 * @return a Date representation of this column
	 */
	public final Date getDate(final String sColumnName) {
		return getDate(sColumnName, new Date());
	}

	/**
	 * Get the value of a column as a Date object. Will return the given default Date if there is a problem parsing the column.
	 * 
	 * @param sColumnName
	 *            column name
	 * @param dDefault
	 *            default value to return in case of an error at parsing
	 * @return a Date representation of this column
	 */
	public final Date getDate(final String sColumnName, final Date dDefault) {
		if ((this.dbc == null) || (this.rsRezultat == null))
			return dDefault;

		try {
			final Date d = this.rsRezultat.getDate(sColumnName);

			if (d != null)
				return d;
		} catch (final Exception e) {
			// ignore this
		}

		try {
			final Date d = Formatare.parseDate(this.rsRezultat.getString(sColumnName).trim());

			if (d != null)
				return d;
		} catch (final Exception e) {
			// ignore this
		}

		return dDefault;
	}

	/**
	 * Get the value of a column as a Date object. Will return the current date/time as default if there is a problem parsing the column.
	 * 
	 * @param iColumn
	 *            column number ( 1 = first column of the result set )
	 * @return a Date representation of this column
	 */
	public final Date getDate(final int iColumn) {
		return getDate(iColumn, new Date());
	}

	/**
	 * Get the value of a column as a Date object. Will return the given default Date if there is a problem parsing the column.
	 * 
	 * @param iColumn
	 *            column number ( 1 = first column of the result set )
	 * @param dDefault
	 *            default value to return in case of an error at parsing
	 * @return a Date representation of this column
	 */
	public final Date getDate(final int iColumn, final Date dDefault) {
		if ((this.dbc == null) || (this.rsRezultat == null))
			return dDefault;

		try {
			final Date d = this.rsRezultat.getDate(iColumn);

			if (d != null)
				return d;
		} catch (final Exception e) {
			// ignore this
		}

		try {
			final Date d = Formatare.parseDate(this.rsRezultat.getString(iColumn).trim());

			if (d != null)
				return d;
		} catch (final Exception e) {
			// ignore this
		}

		return dDefault;
	}

	/**
	 * Get the array of bytes for a column
	 * 
	 * @param sColumnName
	 *            column name
	 * @return byte[] value of the column
	 */
	public byte[] getBytes(final String sColumnName) {
		if (this.rsRezultat == null)
			return null;

		try {
			final byte[] retv = this.rsRezultat.getBytes(sColumnName);
			return retv;
		} catch (final Exception e) {
			// ignore
		}

		return null;
	}

	/**
	 * Get the array of bytes for a column
	 * 
	 * @param iColIndex
	 *            column index
	 * @return byte[] value of the column
	 */
	public byte[] getBytes(final int iColIndex) {
		if (this.rsRezultat == null)
			return null;

		try {
			final byte[] retv = this.rsRezultat.getBytes(iColIndex);
			return retv;
		} catch (final Exception e) {
			// ignore
		}

		return null;
	}

	/**
	 * Get the integer value of a column. Will return 0 by default if the column value cannot be parsed into an integer.
	 * 
	 * @param sColumnName
	 *            column name
	 * @return the integer value of this column
	 */
	public final int geti(final String sColumnName) {
		return geti(sColumnName, 0);
	}

	/**
	 * Get the integer value of a column. Will return the given default value if the column value cannot be parsed into an integer.
	 * 
	 * @param sColumnName
	 *            column name
	 * @param iDefault
	 *            default value to return in case of a parsing error
	 * @return the integer value of this column
	 */
	public final int geti(final String sColumnName, final int iDefault) {
		if ((this.dbc == null) || (this.rsRezultat == null))
			return iDefault;
		try {
			final int iTemp = this.rsRezultat.getInt(sColumnName);
			return this.rsRezultat.wasNull() ? iDefault : iTemp;
		} catch (final Exception e) {
			return iDefault;
		}
	}

	/**
	 * Get the integer value of a column. Will return 0 by default if the column value cannot be parsed into an integer.
	 * 
	 * @param iColumn
	 *            column number
	 * @return the integer value of this column
	 */
	public final int geti(final int iColumn) {
		return geti(iColumn, 0);
	}

	/**
	 * Get the integer value of a column. Will return the given default value if the column value cannot be parsed into an integer.
	 * 
	 * @param iColumn
	 *            column number
	 * @param iDefault
	 *            default value to return in case of a parsing error
	 * @return the integer value of this column
	 */
	public final int geti(final int iColumn, final int iDefault) {
		if ((this.dbc == null) || (this.rsRezultat == null))
			return iDefault;
		try {
			final int iTemp = this.rsRezultat.getInt(iColumn);
			if (this.rsRezultat.wasNull())
				return iDefault;
			return iTemp;
		} catch (final Exception e) {
			return iDefault;
		}
	}

	/**
	 * Get the long value of a column. Will return 0 by default if the column value cannot be parsed into a long.
	 * 
	 * @param sColumnName
	 *            column name
	 * @return the long value of this column
	 */
	public final long getl(final String sColumnName) {
		return getl(sColumnName, 0);
	}

	/**
	 * Get the long value of a column. Will return the given default value if the column value cannot be parsed into a long.
	 * 
	 * @param sColumnName
	 *            column name
	 * @param lDefault
	 *            default value to return in case of a parsing error
	 * @return the long value of this column
	 */
	public final long getl(final String sColumnName, final long lDefault) {
		if ((this.dbc == null) || (this.rsRezultat == null))
			return lDefault;
		try {
			final long lTemp = this.rsRezultat.getLong(sColumnName);
			return this.rsRezultat.wasNull() ? lDefault : lTemp;
		} catch (final Throwable e) {
			return lDefault;
		}
	}

	/**
	 * Get the long value of a column. Will return 0 by default if the column value cannot be parsed into a long.
	 * 
	 * @param iColCount
	 *            column count
	 * @return the long value of this column
	 */
	public final long getl(final int iColCount) {
		return getl(iColCount, 0);
	}

	/**
	 * Get the long value of a column. Will return the given default value if the column value cannot be parsed into a long.
	 * 
	 * @param iColCount
	 *            column count
	 * @param lDefault
	 *            default value to return in case of a parsing error
	 * @return the long value of this column
	 */
	public final long getl(final int iColCount, final long lDefault) {
		if ((this.dbc == null) || (this.rsRezultat == null))
			return lDefault;
		try {
			final long lTemp = this.rsRezultat.getLong(iColCount);
			return this.rsRezultat.wasNull() ? lDefault : lTemp;
		} catch (final Throwable e) {
			return lDefault;
		}
	}

	/**
	 * Get the float value of a column. Will return 0 by default if the column value cannot be parsed into a float.
	 * 
	 * @param sColumnName
	 *            column name
	 * @return the float value of this column
	 */
	public final float getf(final String sColumnName) {
		return getf(sColumnName, 0);
	}

	/**
	 * Get the float value of a column. Will return the given default value if the column value cannot be parsed into a float.
	 * 
	 * @param sColumnName
	 *            column name
	 * @param fDefault
	 *            default value to return in case of a parsing error
	 * @return the float value of this column
	 */
	public final float getf(final String sColumnName, final float fDefault) {
		if ((this.dbc == null) || (this.rsRezultat == null))
			return fDefault;
		try {
			final float fTemp = this.rsRezultat.getFloat(sColumnName);
			return this.rsRezultat.wasNull() ? fDefault : fTemp;
		} catch (final Exception e) {
			return fDefault;
		}
	}

	/**
	 * Get the double value of a column. Will return 0 by default if the column value cannot be parsed into a double.
	 * 
	 * @param sColumnName
	 *            column name
	 * @return the double value of this column
	 */
	public final double getd(final String sColumnName) {
		return getd(sColumnName, 0);
	}

	/**
	 * Get the double value of a column. Will return 0 by default if the column value cannot be parsed into a double.
	 * 
	 * @param sColumnName
	 *            column name
	 * @param dDefault
	 *            default value to return in case of a parsing error
	 * @return the double value of this column
	 */
	public final double getd(final String sColumnName, final double dDefault) {
		if ((this.dbc == null) || (this.rsRezultat == null))
			return dDefault;
		try {
			final double dTemp = this.rsRezultat.getDouble(sColumnName);
			return this.rsRezultat.wasNull() ? dDefault : dTemp;
		} catch (final Throwable e) {
			return dDefault;
		}
	}

	/**
	 * Get double
	 * 
	 * @param iColIndex
	 *            column index
	 * @return double value from db, or default 0 if missing
	 */
	public final double getd(final int iColIndex) {
		return getd(iColIndex, 0d);
	}

	/**
	 * Get double
	 * 
	 * @param iColIndex
	 *            column index
	 * @param dDefault
	 *            default value
	 * @return double value from db, or the default if missing
	 */
	public final double getd(final int iColIndex, final double dDefault) {
		if ((this.dbc == null) || (this.rsRezultat == null))
			return dDefault;
		try {
			final double dTemp = this.rsRezultat.getDouble(iColIndex);
			return this.rsRezultat.wasNull() ? dDefault : dTemp;
		} catch (final Throwable e) {
			return dDefault;
		}
	}

	/**
	 * Get the boolean value of a column
	 * 
	 * @param sColumn
	 *            column name
	 * @param bDefault
	 *            default value
	 * @return true/false, obviously :)
	 */
	public final boolean getb(final String sColumn, final boolean bDefault) {
		final String s = gets(sColumn);

		if (s.length() > 0) {
			final char c = s.charAt(0);

			if ((c == 't') || (c == 'T') || (c == 'y') || (c == 'Y') || (c == '1'))
				return true;
			if ((c == 'f') || (c == 'F') || (c == 'n') || (c == 'N') || (c == '0'))
				return false;
		}

		return bDefault;
	}

	/**
	 * Get the boolean value of a column
	 * 
	 * @param iColumn
	 *            column index
	 * @param bDefault
	 *            default value
	 * @return true/false, obviously :)
	 */
	public final boolean getb(final int iColumn, final boolean bDefault) {
		final String s = gets(iColumn);

		if (s.length() > 0) {
			final char c = s.charAt(0);

			if ((c == 't') || (c == 'T') || (c == 'y') || (c == 'Y') || (c == '1'))
				return true;
			if ((c == 'f') || (c == 'F') || (c == 'n') || (c == 'N') || (c == '0'))
				return false;
		}

		return bDefault;
	}

	/**
	 * Extract a PostgreSQL array into a Collection of String objects
	 * 
	 * @param sColumn
	 *            column name
	 * @return the values in the array, as Strings
	 * @since 1.0.3
	 */
	public final Collection<String> getStringArray(final String sColumn) {
		return decode(gets(sColumn));
	}

	/**
	 * Extract a PostgreSQL array into a Collection of String objects
	 * 
	 * @param iColumn
	 *            column index
	 * @return the values in the array, as Strings
	 * @since 1.0.3
	 */
	public final Collection<String> getStringArray(final int iColumn) {
		return decode(gets(iColumn));
	}

	/**
	 * Extract a PostgreSQL array into a Collection of Integer objects
	 * 
	 * @param sColumn
	 *            column name
	 * @return the values in the array, as Integers
	 * @since 1.0.3
	 */
	public final Collection<Integer> getIntArray(final String sColumn) {
		return decodeToInt(gets(sColumn));
	}

	/**
	 * Extract a PostgreSQL array into a Collection of Integer objects
	 * 
	 * @param iColumn
	 *            column index
	 * @return the values in the array, as Integers
	 * @since 1.0.3
	 */
	public final Collection<Integer> getIntArray(final int iColumn) {
		return decodeToInt(gets(iColumn));
	}

	/**
	 * Convert each entry from an array to Integer.
	 * 
	 * @param sValue
	 *            text representation of the array
	 * @return array of integers
	 */
	private static Collection<Integer> decodeToInt(final String sValue) {
		final Collection<String> lValues = decode(sValue);

		final ArrayList<Integer> l = new ArrayList<Integer>(lValues.size());

		for (final String s : lValues)
			try {
				l.add(Integer.valueOf(s));
			} catch (final NumberFormatException nfe) {
				// ignore
			}

		return l;
	}

	/**
	 * Given an array in PostgreSQL format, convert it to a Java array of Strings.
	 * 
	 * @param sValue
	 *            postgresql array representation as string
	 * @return array of strings
	 */
	private static Collection<String> decode(final String sValue) {
		if ((sValue == null) || (sValue.length() < 2) || (sValue.charAt(0) != '{') || (sValue.charAt(sValue.length() - 1) != '}'))
			return new ArrayList<String>(0);

		final StringTokenizer st = new StringTokenizer(sValue.substring(1, sValue.length() - 1), ",");

		final ArrayList<String> l = new ArrayList<String>(st.countTokens());

		while (st.hasMoreTokens()) {
			String s = st.nextToken();

			if (s.charAt(0) == '"') {
				while (((s.length() < 2) || (s.charAt(s.length() - 1) != '"') || (s.charAt(s.length() - 2) == '\\')) && st.hasMoreTokens())
					s += "," + st.nextToken();

				// s = s.substring(1, s.length()-1).replace("\\\"", "\"").replace("\\\\", "\\");
				s = s.substring(1, s.length() - 1);
				s = Formatare.replace(s, "\\\"", "\"");
				s = Formatare.replace(s, "\\\\", "\\");
			}

			l.add(s);
		}

		return l;
	}

	/**
	 * Generate a PostgreSQL array representation of the given one-dimensional collection. For details consult the <a href="http://www.postgresql.org/docs/8.2/static/arrays.html">documentation</a>.
	 * 
	 * @param array
	 *            array of any object
	 * @return a string encoding of the values
	 */
	public static String encodeArray(final Collection<?> array) {
		final StringBuilder sb = new StringBuilder();

		final Iterator<?> it = array.iterator();

		while (it.hasNext()) {
			final Object o = it.next();

			String s = o.toString();
			s = Formatare.replace(s, "\"", "\\\"");
			s = Formatare.jsEscape(s);

			if (sb.length() > 0)
				sb.append(',');

			sb.append('"').append(s).append('"');
		}

		return "'{" + sb.toString() + "}'";
	}

	/**
	 * Get the meta information for the current query. You can look at this structure to extract column names, types and so on.
	 * 
	 * @return the meta information for the current query.
	 */
	public final ResultSetMetaData getMetaData() {
		if ((this.dbc == null) || (this.rsRezultat == null))
			return null;

		try {
			return this.rsRezultat.getMetaData();
		} catch (final Exception e) {
			// ignore this
		}

		return null;
	}

	/**
	 * Get the Java object equivalent of this column's type
	 * 
	 * @param column
	 * @return java object
	 */
	public final Object getObject(final int column) {
		if ((this.dbc == null) || (this.rsRezultat == null))
			return null;

		try {
			return this.rsRezultat.getObject(column);
		} catch (final Exception e) {
			// ignore
		}

		return null;
	}

	/**
	 * Get the Java object equivalent of this column's type
	 * 
	 * @param column
	 * @return java object
	 */
	public final Object getObject(final String column) {
		if ((this.dbc == null) || (this.rsRezultat == null))
			return null;

		try {
			return this.rsRezultat.getObject(column);
		} catch (final Exception e) {
			// ignore
		}

		return null;
	}

	/**
	 * A shortcut to find out the column names for this query
	 * 
	 * @return an array of column names
	 */
	public final String[] getColumnNames() {
		final ResultSetMetaData rsmd = getMetaData();

		if (rsmd == null)
			return new String[0];

		try {
			final int count = rsmd.getColumnCount();

			final String vs[] = new String[count];

			for (int i = 1; i <= count; i++)
				vs[i] = rsmd.getColumnName(i);

			return vs;
		} catch (final Throwable e) {
			return new String[0];
		}
	}

	/**
	 * Statistics : get the number of connections currently established
	 * 
	 * @return the number of active connections
	 */
	public static final long getActiveConnectionsCount() {
		long lCount = 0;

		synchronized (oConnLock) {
			final Iterator<LinkedList<DBConnection>> it = hmConn.values().iterator();

			while (it.hasNext()) {
				final LinkedList<DBConnection> ll = it.next();

				lCount += ll.size();
			}
		}

		return lCount;
	}
}
