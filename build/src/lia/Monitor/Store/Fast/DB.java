package lia.Monitor.Store.Fast;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Store.Fast.Replication.DBConnectionWrapper;
import lia.Monitor.Store.Fast.Replication.DatabaseBackend;
import lia.Monitor.Store.Fast.Replication.ReplicationManager;

/**
 * @author costing
 *
 */
public final class DB {

    /**
     * The logger
     */
    private static final Logger logger = Logger.getLogger(DB.class.getName());

    /**
     * Database wrapper in use
     */
    private DBConnectionWrapper db = null;
	
	/**
	 * Set to <code>true</code> to signal that the following query is read-only and, if available, a slave could be used to execute it
	 */
	private boolean													readOnlyQuery = false;
	
	/**
	 * Cursor type, defaulting to FORWARD_ONLY.
	 */
	private int														cursorType = ResultSet.TYPE_FORWARD_ONLY;
    
    /**
     * Simple constructor
     */
    public DB() {
        // nothing to do 
    }

    /**
     * Constructor + initial query
     * 
     * @param s query to execute
     */
    public DB(final String s) {
        query(s);
    }


	/**
	 * Signal that the following query is read-only and, if available, a database slave could be used to execute it. 
	 * 
	 * @param readOnly if <code>true</code> then the query can potentially go to a slave, if <code>false</code> then only the master can execute it 
	 * @return previous value of the read-only flag
	 */
	public boolean setReadOnly(final boolean readOnly){
		final boolean previousValue = this.readOnlyQuery;
		
		this.readOnlyQuery = readOnly;
		
		return previousValue;
	}

	/**
	 * Get the current value of the read-only flag
	 * 
	 * @return read-only flag
	 */
	public boolean isReadOnly(){
		return this.readOnlyQuery;
	}
	
	/**
	 * Set the cursor type to one of the ResultSet.TYPE_FORWARD_ONLY (default), ResultSet.TYPE_SCROLL_INSENSITIVE (when you need {@link #count()} or such) and so on.
	 * 
	 * @param type new cursor type
	 * @return previous cursor type
	 */
	public int setCursorType(final int type){
		final int previousType = this.cursorType;
		
		this.cursorType = type;
		
		return previousType;
	}
	
	/**
	 * @return cursor type
	 */
	public int getCursorType(){
		return this.cursorType;
	}
    
    /**
     * Get the backend that executed the last query
     * 
     * @return the backend
     */
    public DatabaseBackend getBackend() {
        return this.db != null ? this.db.getBackend() : null;
    }

    /**
     * Total number of erros
     */
    private static long lErrorCount = 0;

    /**
     * error counter
     * 
     * @return current number of errors
     */
    public static long getErrorCount() {
        return lErrorCount;
    }

    /**
     * Execute a query
     * 
     * @param sSQLQuery query to execute
     * @return true if query was successful
     */
    public boolean query(final String sSQLQuery) {
        return query(sSQLQuery, false, false);
    }

    /**
     * Get the number of rows that were changed by the previous (update) query
     * 
     * @return number of affected queries
     */
    public final int getUpdateCount() {
        if (this.db != null) {
            return this.db.getUpdateCount();
        }

        return -1;
    }

    /**
     * Get the number of rows that were selected by the previous query.
     * 
     * @return number of rows, or -1 if the query was not a select one or there was an error
     */
    public final int count() {
        if (this.db != null) {
            return this.db.count();
        }

        return -1;
    }

    /**
     * Get the error from the last operation
     * 
     * @return error message returned by the engine, or <code>null</code> if none (the operation was successful / no last op)
     */
    public String getLastError() {
        if (this.db != null) {
            return this.db.getLastError();
        }

        return null;
    }

    /**
     * Execute an update query in sync on all backends.
     * 
     * @param sSQLQuery query to execute
     * @return true if the query was successful on at least one backend
     * @see ReplicationManager#syncUpdateQuery(String)
     * @see #syncUpdateQuery(String, boolean)
     */
    public boolean syncUpdateQuery(final String sSQLQuery) {
        this.db = ReplicationManager.getInstance().syncUpdateQuery(sSQLQuery);

        return this.db != null;
    }

    /**
     * Execute an update query in sync on all backends.
     * 
     * @param sSQLQuery query to execute
     * @param ignoreErrors ignore possible (expected) errors
     * @return true if the query was successful on at least one backend
     * @see ReplicationManager#syncUpdateQuery(String, boolean)
     */
    public boolean syncUpdateQuery(final String sSQLQuery, final boolean ignoreErrors) {
        this.db = ReplicationManager.getInstance().syncUpdateQuery(sSQLQuery, ignoreErrors);

        return this.db != null;
    }

    /**
     * Execute the given query asynchronously
     * 
     * @param sSQLQuery query to execute in background
     */
    public void asyncUpdate(final String sSQLQuery) {
        ReplicationManager.getInstance().asyncUpdate(sSQLQuery);
    }

    /**
     * Execute a query
     * 
     * @param sSQLQuery query to execute
     * @param bIgnoreErrors do not log errors, useful for queries that are expected to fail
     * @return true if query was successful
     */
    public boolean query(final String sSQLQuery, final boolean bIgnoreErrors) {
        return query(sSQLQuery, bIgnoreErrors, false);
    }

    /**
     * Execute a query
     * 
     * @param sSQLQuery query to execute
     * @param ignoreErrors flag to ignore expected errors
     * @param broadcastAlways broadcast the query to all backends, even if it's not an update query
     * @return true if query was successfully executed
     */
    public boolean query(final String sSQLQuery, final boolean ignoreErrors, final boolean broadcastAlways) {
        this.db = ReplicationManager.getInstance().query(sSQLQuery, ignoreErrors, broadcastAlways, this.db, cursorType, readOnlyQuery);

        final boolean bResult = (this.db != null) && this.db.isLastQueryOK();

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Query result was " + bResult + " (ignoreErrors=" + ignoreErrors
                    + ", broadcastAlways=" + broadcastAlways + ":\n" + sSQLQuery);
        }

        return bResult;
    }

    public void close() {
        if (this.db != null) {
            this.db.close();
            this.db = null;
        }
    }

    /**
     * Try to go to the next row
     * 
     * @return true if possible
     */
    public boolean moveNext() {
        if (this.db != null) {
            return this.db.moveNext();
        }

        return false;
    }

    /**
     * Get raw string value, possibly null
     * 
     * @param sColumnName column name
     * @return raw string value
     */
    public String getns(final String sColumnName) {
        return this.db != null ? this.db.gets(sColumnName, null) : null;
    }

    /**
     * Get raw string value, possibly null
     * 
     * @param iColCount column index
     * @return raw string value
     */
    public String getns(final int iColCount) {
        return this.db != null ? this.db.gets(iColCount, null) : null;
    }

    /**
     * Get the string value, with default "" if the value is null in the db
     * 
     * @param sColumnName column name
     * @return string value
     */
    public String gets(final String sColumnName) {
        return this.db != null ? this.db.gets(sColumnName) : "";
    }

    /**
     * Get the string value, with a given default if the value is null in the db 
     * 
     * @param sColumnName column name
     * @param sDefault default value
     * @return string value
     */
    public String gets(final String sColumnName, final String sDefault) {
        return this.db != null ? this.db.gets(sColumnName, sDefault) : sDefault;
    }

    /**
     * Get the string value, with default "" if the value is null in the db
     * 
     * @param iColCount column index
     * @return string value
     */
    public String gets(final int iColCount) {
        return this.db != null ? this.db.gets(iColCount, "") : "";
    }

    /**
     * Get the string value, with a given default if the value is null in the db
     * 
     * @param iColCount column index
     * @param sDefault default value
     * @return string value
     */
    public String gets(final int iColCount, final String sDefault) {
        return this.db != null ? this.db.gets(iColCount, sDefault) : sDefault;
    }

    /**
     * Get the array of bytes for a column
     * 
     * @param sColumnName column name
     * @return byte[] value of the column
     */
    public byte[] getBytes(final String sColumnName) {
        return this.db != null ? this.db.getBytes(sColumnName) : null;
    }

    /**
     * Get the raw byte[] content of this column
     * 
     * @param iColIndex column index
     * @return content
     */
    public byte[] getBytes(final int iColIndex) {
        return this.db != null ? this.db.getBytes(iColIndex) : null;
    }

    /**
     * Get the integer value of a column, with a default of 0 if the value from the DB cannot be converted to int 
     * 
     * @param sColumnName column name
     * @return int value
     */
    public int geti(final String sColumnName) {
        return geti(sColumnName, 0);
    }

    /**
     * Get the integer value of a column, with a given default if the value from the DB cannot be converted to int
     * 
     * @param sColumnName column name
     * @param iDefault default value
     * @return int value
     */
    public int geti(final String sColumnName, final int iDefault) {
        return this.db != null ? this.db.geti(sColumnName, iDefault) : iDefault;
    }

    /**
     * Get the integer value of a column, with a default of 0 if the value from the DB cannot be converted to int
     * 
     * @param iColIndex column index
     * @return int value
     */
    public int geti(final int iColIndex) {
        return geti(iColIndex, 0);
    }

    /**
     * Get the integer value of a column, with a given default if the value from the DB cannot be converted to int
     * 
     * @param iColIndex column index
     * @param iDefault default value
     * @return int value
     */
    public int geti(final int iColIndex, final int iDefault) {
        return this.db != null ? this.db.geti(iColIndex, iDefault) : iDefault;
    }

    /**
     * Get the long value of a column, with a default of 0 if the value from the DB cannot be converted to long
     * 
     * @param colIndex column index
     * @return long value
     */
    public long getl(final int colIndex) {
        return getl(colIndex, 0);
    }

    /**
     * Get the long value of a column, with a given default if the value from the DB cannot be converted to long
     * 
     * @param colIndex column index
     * @param lDefault default value
     * @return long value
     */
    public long getl(final int colIndex, final long lDefault) {
        return this.db != null ? this.db.getl(colIndex, lDefault) : lDefault;
    }

    /**
     * Get the long value of a column, with a default of 0 if the value from the DB cannot be converted to long
     * 
     * @param sColumnName column name
     * @return long value
     */
    public long getl(final String sColumnName) {
        return getl(sColumnName, 0);
    }

    /**
     * Get the long value of a column, with a given default if the value from the DB cannot be converted to long
     * 
     * @param sColumnName column name
     * @param lDefault default value
     * @return long value
     */
    public long getl(final String sColumnName, final long lDefault) {
        return this.db != null ? this.db.getl(sColumnName, lDefault) : lDefault;
    }

    /**
     * Get the double value of a column, with a default of 0 if the value from the DB cannot be converted to double
     * 
     * @param sColumnName column name
     * @return double value
     */
    public double getd(final String sColumnName) {
        return getd(sColumnName, 0);
    }

    /**
     * Get the double value of a column, with a given default if the value from the DB cannot be converted to double
     * 
     * @param sColumnName column name
     * @param dDefault default value
     * @return double value
     */
    public double getd(final String sColumnName, final double dDefault) {
        return this.db != null ? this.db.getd(sColumnName, dDefault) : dDefault;
    }

    /**
     * Get the double value of a column, with a default of 0 if the value from the DB cannot be converted to double
     * 
     * @param iColIndex column index
     * @return double value
     */
    public double getd(final int iColIndex) {
        return getd(iColIndex, 0);
    }

    /**
     * Get the double value of a column, with a given default if the value from the DB cannot be converted to double
     * 
     * @param iColIndex column index
     * @param dDefault default value
     * @return double value
     */
    public double getd(final int iColIndex, final double dDefault) {
        return this.db != null ? this.db.getd(iColIndex, dDefault) : dDefault;
    }

    /**
     * Get the boolean value of a column
     * 
     * @param sColumn column name
     * @return boolean
     */
    public boolean getb(final String sColumn) {
        return getb(sColumn, false);
    }

    /**
     * Get the boolean value of a column
     * 
     * @param sColumn column name
     * @param bDefault default value
     * @return boolean
     */
    public boolean getb(final String sColumn, final boolean bDefault) {
        return this.db != null ? this.db.getb(sColumn, bDefault) : bDefault;
    }

    /**
     * Get the boolean value of a column
     * 
     * @param iColumn column index
     * @return boolean
     */
    public boolean getb(final int iColumn) {
        return getb(iColumn, false);
    }

    /**
     * Get the boolean value of a column
     * 
     * @param iColumn column index
     * @param bDefault default value
     * @return boolean
     */
    public boolean getb(final int iColumn, final boolean bDefault) {
        return this.db != null ? this.db.getb(iColumn, bDefault) : bDefault;
    }

    /**
     * Get the meta data information
     * 
     * @return meta data information, or null if the current query is not a SELECT one
     */
    public ResultSetMetaData getMetaData() {
        return this.db != null ? this.db.getMetaData() : null;
    }

    /**
     * Is Autovacuum enabled in PostgreSQL ?
     */
    private static boolean bAutovacuumEnabled = false;

    /**
     * True if the autovacuum status was determined
     */
    private static boolean bAutovacuumStatusDetermined = false;

    /**
     * Find out if the autovacuum is enabled in a PostgreSQL backend. The return value will be 
     * used in various maintenance procedures to decide whether or not operations like VACUUM
     * or ANALYZE are run from the code or they are left to the database daemon.
     * 
     * @return true if the database is PG and autovacuum is enabled, false otherwise
     */
    public static final boolean isAutovacuumEnabled() {
        // look only once for this parameter, it cannot be changed dynamically in the DB backend
        if (!bAutovacuumStatusDetermined) {
            DBConnectionWrapper db = ReplicationManager.getInstance().query(
                    "select setting from pg_settings where name='autovacuum';", true);

            if ((db != null) && db.isLastQueryOK() && db.isPostgreSQL() && db.moveNext()) {
                String sStatus = db.gets(1).trim().toLowerCase();

                if (sStatus.equals("on") || sStatus.startsWith("y") || sStatus.startsWith("e")
                        || sStatus.startsWith("1") || sStatus.startsWith("t")) {
                    bAutovacuumEnabled = true;
                }
            } else {
                bAutovacuumEnabled = false;
            }

            bAutovacuumStatusDetermined = true;

            logger.log(Level.INFO, "Autovacuum enabled: " + bAutovacuumEnabled);
        }

        return bAutovacuumEnabled;
    }

    /**
     * Optional execution of maintenance operations. If autovacuum is enabled the
     * operation is not executed, since the database will take care of this automatically.
     * The default is to execute the maintenance operations asynchronously.
     * 
     * @param query
     * @see #maintenance(String, boolean)
     * @return true if the operation was scheduled for execution, false if not
     */
    public final boolean maintenance(final String query) {
        return maintenance(query, false);
    }

    /**
     * Optional execution of maintenance operations. If autovacuum is enabled the
     * operation is not executed, since the database will take care of this automatically.
     * 
     * @param query maintenance operation to execute (vacuum / cluster / reindex)
     * @param sync whether to execute the operation synchronously (true) or async (false)
     * @return true if the operation was executed successfully (or was scheduled asynchronously)
     */
    public final boolean maintenance(final String query, final boolean sync) {
        if (!isAutovacuumEnabled()) {
            if (sync) {
                return syncUpdateQuery(query, true);
            }

            asyncUpdate(query);
            return true;
        }

        return false;
    }

    /**
     * Convert the result set in a column name -> value mapping
     * 
     * @return the column name -> value mapping
     */
    public final Map<String, Object> getValuesMap() {
        final ResultSetMetaData meta = getMetaData();

        if (meta == null) {
            return null;
        }

        try {
            final int count = meta.getColumnCount();

            final Map<String, Object> ret = new LinkedHashMap<String, Object>(count);

            for (int i = 1; i <= count; i++) {
                final String columnName = meta.getColumnName(i);

                ret.put(columnName, this.db.getObject(i));
            }

            return ret;
        } catch (SQLException e) {
            return null;
        }
    }
}
