/*
 * $Id: Writer.java 7541 2014-11-18 15:24:12Z costing $
 */
package lia.Monitor.Store.Fast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import lia.Monitor.monitor.AccountingResult;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ExtResult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.ResultUtils;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.ntp.NTPDate;
import lia.web.utils.Formatare;

/**
 * @author costing
 * @since forever
 */
public abstract class Writer implements WriterInterface {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(Writer.class.getName());

    /**
     * Writer type
     */
    int iWriteMode = -1;

    /**
     * Table name or table preffix
     */
    String sTableName = null;

    /**
     * Last values for each series
     */
    protected Map<Object, CacheElement> m = null;

    /**
     * Lock for accessing the last values map
     */
    protected Object mLock = new Object();

    /**
     * Whether or not this writer is active
     */
    protected AtomicBoolean online = new AtomicBoolean(false);

    /**
     * Save some value in this structure
     * 
     * @param o
     *            value to store
     */
    protected abstract void storeData(Object o);

    /**
     * Find out the table name
     * 
     * @return table name or name preffix
     */
    public String getTableName() {
        return this.sTableName;
    }

    /**
     * What is the time interval covered by this structure
     * 
     * @return interval in millis, relative to now()
     */
    public abstract long getTotalTime();

    /**
     * Maintenance operations
     * 
     * @param bCleanHash
     *            clean hash in memory
     * @return true on success
     */
    public abstract boolean cleanup(boolean bCleanHash);

    /**
     * Save last values to persistent db structures
     * 
     * @return number of database rows affected
     */
    public abstract int save();

    /**
     * When was this structure brought online
     */
    public AtomicLong onlineSince = new AtomicLong();

    /**
     * configuration access lock
     */
    private final ReadWriteLock rwConfigLock = new ReentrantReadWriteLock();

    /**
     * read lock
     */
    private final Lock configReadLock = this.rwConfigLock.readLock();

    /**
     * write lock
     */
    private final Lock configWriteLock = this.rwConfigLock.writeLock();

    /**
     * Values firewall - accept list of predicates
     */
    private final ArrayList<monPredicate> alAccept = new ArrayList<monPredicate>();

    /**
     * Values firewall - reject list of predicates
     */
    private final ArrayList<monPredicate> alReject = new ArrayList<monPredicate>();

    static {
        final boolean isMemOnly = (AppConfig.getb("lia.Monitor.memory_store_only", false) || (AppConfig.geti(
                "lia.Monitor.Store.TransparentStoreFast.web_writes", -1) <= 0));
        if (!isMemOnly) {
            logger.log(Level.INFO, "Loading settings table...");
            // make sure we have the settings table
            final DB db = new DB();

            if (db.syncUpdateQuery("CREATE TABLE monitor_settings (tablename text, key text, value text);", true)) {
                db.syncUpdateQuery("CREATE UNIQUE INDEX monitor_settings_pkey ON monitor_settings(tablename, key);");
            }
            logger.log(Level.INFO, "Settings table loaded.");
        } else {
            logger.log(Level.INFO, "MemOnly storage. Not loading settings table");
        }
    }

    /**
     * Get a persistent setting
     * 
     * @param sKey
     *            key to get
     * @return persistent setting
     */
    public String getSetting(final String sKey) {
        final DB db = new DB();

        db.setReadOnly(true);
        
        db.query("SELECT value FROM monitor_settings WHERE tablename='" + e(this.sTableName) + "' AND key='" + e(sKey)
                + "';");

        return db.gets(1);
    }

    /**
     * Escape string to SQL-safe one
     * 
     * @param s
     *            string to escape
     * @return SQL safe version
     */
    private static final String e(final String s) {
        return Formatare.mySQLEscape(s);
    }

    /**
     * Get a persistent setting, as a long.
     * 
     * @param sKey
     *            key to get
     * @param lDefault
     *            default value to return if missing
     * @param bSetBackDefault
     *            if true and a default is not found, set back the default value as the setting
     * @return the value converted to long, or the default if the setting is not present or is not a number
     */
    public long getSettingLong(final String sKey, final long lDefault, final boolean bSetBackDefault) {
        try {
            return Long.parseLong(getSetting(sKey));
        } catch (NumberFormatException ne) {
            if (bSetBackDefault) {
                setSetting(sKey, lDefault);
            }

            return lDefault;
        }
    }

    /**
     * Make a setting persistent in the database
     * 
     * @param sKey
     *            key to store
     * @param sValue
     *            value for it
     */
    public void setSetting(final String sKey, final String sValue) {
        final DB db = new DB();

        db.query("UPDATE monitor_settings SET value='" + e(sValue) + "' WHERE tablename='" + e(this.sTableName)
                + "' AND key='" + e(sKey) + "';");

        if (db.getUpdateCount() == 0) {
            db.query("INSERT INTO monitor_settings (tablename, key, value) VALUES ('" + e(this.sTableName) + "', '"
                    + e(sKey) + "', '" + e(sValue) + "');");
        }
    }

    /**
     * Make a setting persistent in the database
     * 
     * @param sKey
     *            key
     * @param lValue
     *            value
     */
    public void setSetting(final String sKey, final long lValue) {
        setSetting(sKey, String.valueOf(lValue));
    }

    /**
     * Set the predicates that are allowed to be stored into this structure
     * 
     * @param sAcceptConstraints
     *            list of predicates in String format
     * @param sRejectConstraints
     *            list of predicates in String format
     */
    public void setConstaints(final String sAcceptConstraints, final String sRejectConstraints) {
        this.configWriteLock.lock();

        try {
            this.alAccept.clear();

            if ((sAcceptConstraints != null) && (sAcceptConstraints.length() > 0)) {
                StringTokenizer st = new StringTokenizer(sAcceptConstraints, ",");

                while (st.hasMoreTokens()) {
                    monPredicate pred = Formatare.toPred(st.nextToken());

                    if (pred != null) {
                        this.alAccept.add(pred);
                    }
                }
            }

            this.alReject.clear();
            if ((sRejectConstraints != null) && (sRejectConstraints.length() > 0)) {
                StringTokenizer st = new StringTokenizer(sRejectConstraints, ",");

                while (st.hasMoreTokens()) {
                    monPredicate pred = Formatare.toPred(st.nextToken());

                    if (pred != null) {
                        this.alReject.add(pred);
                    }
                }
            }
        } finally {
            this.configWriteLock.unlock();
        }
    }

    /**
     * Add a value into this storage with respect to the currently set constraints
     * 
     * @param o
     *            object to store
     */
    public final void addSample(final Object o) {
        if (o == null) {
            return;
        }

        this.configReadLock.lock();

        try {
            final Object oTemp = ResultUtils.valuesFirewall(o, this.alAccept, this.alReject);

            if (oTemp != null) {
                if (this.bIgnore) {
                    if (this.bIgnoreResult && (oTemp instanceof Result)) {
                        return;
                    } else if (this.bIgnoreEResult && (oTemp instanceof eResult)) {
                        return;
                    } else if (this.bIgnoreExtResult && (oTemp instanceof ExtResult)) {
                        return;
                    } else if (this.bIgnoreAccountingResult && (oTemp instanceof AccountingResult)) {
                        return;
                    }
                }

                storeData(oTemp);
            }
        } finally {
            this.configReadLock.unlock();
        }
    }

    /**
     * Used in {@link #setIgnoreDataFlags(int)}
     */
    public static final int FLAGS_RESULT = 1;

    /**
     * Used in {@link #setIgnoreDataFlags(int)}
     */
    public static final int FLAGS_ERESULT = 2;

    /**
     * Used in {@link #setIgnoreDataFlags(int)}
     */
    public static final int FLAGS_EXTRESULT = 4;

    /**
     * Used in {@link #setIgnoreDataFlags(int)}
     */
    public static final int FLAGS_ACCOUNTINGRESULT = 8;

    /**
     * Flag to ignore Result objects
     */
    private boolean bIgnoreResult = false;

    /**
     * Flag to ignore eResult objects
     */
    private boolean bIgnoreEResult = false;

    /**
     * Flag to ignore ExtResult objects
     */
    private boolean bIgnoreExtResult = false;

    /**
     * Flag to ignore AccountingResult objects
     */
    private boolean bIgnoreAccountingResult = false;

    /**
     * Flag to ignore something :)
     */
    private boolean bIgnore = false;

    /**
     * For this writer, make it ignore some of the possible data types directly
     * 
     * @param flags
     *            a bit mask build with FLAGS_* constants
     * @return old flags
     */
    public int setIgnoreDataFlags(final int flags) {
        int iOldFlags = 0;
        if (this.bIgnore) {
            if (this.bIgnoreResult) {
                iOldFlags |= FLAGS_RESULT;
            }
            if (this.bIgnoreEResult) {
                iOldFlags |= FLAGS_ERESULT;
            }
            if (this.bIgnoreExtResult) {
                iOldFlags |= FLAGS_EXTRESULT;
            }
            if (this.bIgnoreAccountingResult) {
                iOldFlags |= FLAGS_ACCOUNTINGRESULT;
            }
        }

        this.bIgnoreResult = (flags & FLAGS_RESULT) != 0;
        this.bIgnoreEResult = (flags & FLAGS_ERESULT) != 0;
        this.bIgnoreExtResult = (flags & FLAGS_EXTRESULT) != 0;
        this.bIgnoreAccountingResult = (flags & FLAGS_ACCOUNTINGRESULT) != 0;

        this.bIgnore = this.bIgnoreResult || this.bIgnoreEResult || this.bIgnoreExtResult
                || this.bIgnoreAccountingResult;

        return iOldFlags;
    }

    /**
     * Make this writer active
     * 
     * @param onlineTime
     *            when was this writer made active ?
     * @return true
     */
    public boolean setOnline(long onlineTime) {
        this.onlineSince.set(onlineTime);
        this.online.set(true);
        return true;
    }

    /**
     * Find out whether or not this writer is active
     * 
     * @return true is this structure is active
     */
    public final boolean isOnline() {
        return this.online.get();
    }

    /**
     * Delete all structures belonging to this storage
     * 
     * @return true on success
     */
    public boolean deleteAll() {
        return true;
    }

    /**
     * Disable this structure
     * 
     * @return true on success
     */
    public final boolean setOffline() {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, " [ Writer ] " + this.sTableName + " BECOME OFFLINE !");
        }
        this.online.set(false);
        return true;
    }

    /**
     * Find out when was this structure made active
     * 
     * @return time
     */
    public long getOnlineTime() {
        return this.onlineSince.longValue();
    }

    /**
     * Wrapper of ByteArrayOutputStream and ByteArrayInputStream
     * used to avoid copying of large byte[] around.
     * 
     * @author costing
     * @since Service 1.5.6, Repository 1.2.62, 2006-05-12
     */
    public static final class BAOSExtension extends ByteArrayOutputStream {

        /**
         * The only constructor
         * 
         * @param size
         *            initial size
         */
        public BAOSExtension(final int size) {
            super(size);
        }

        /**
         * Get the input stream for the same underlying buffer
         * 
         * @return input stream
         */
        public ByteArrayInputStream getBAIS() {
            return new ByteArrayInputStream(this.buf, 0, this.count);
        }

        /**
         * Get the size of the underlying buffer
         * 
         * @return size
         */
        public int getAllocatedSize() {
            return this.buf.length;
        }
    }

    /**
     * Get the byte[] representation of an object
     * 
     * @param o
     *            object to serialize
     * @return object serialization
     */
    public static final byte[] serializeToBytes(final Object o) {
        final BAOSExtension byteArray = serializeObject(o);

        return byteArray != null ? byteArray.toByteArray() : new byte[0];
    }

    /**
     * An estimation of future size
     */
    private static volatile int iOldArraySize = 10000;

    /**
     * Serialize an object to put in db
     * 
     * @param o
     *            object
     * @return serialized
     */
    private static final BAOSExtension serializeObject(final Object o) {
        try {
            final BAOSExtension baos = new BAOSExtension(iOldArraySize);

            final ObjectOutputStream oos = new ObjectOutputStream(baos);

            oos.writeObject(o);
            oos.flush();
            oos.close();

            iOldArraySize = (iOldArraySize / 4) + (baos.size() / 2) + (baos.getAllocatedSize() / 4);

            return baos;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Writer Serializer exception", t);
            return null;
        }
    }

    /**
     * Get a string with an SQL-safe representation of this object
     * 
     * @param o
     *            object
     * @return SQL-safe string containing object representation
     */
    public static final String serializeToString(Object o) {
        try {
            byte vb[] = serializeToBytes(o);

            if (vb.length == 0) {
                return null;
            }

            char vc[];

            if (vb.length > 10000) {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final GZIPOutputStream gzipos = new GZIPOutputStream(baos);

                gzipos.write(vb, 0, vb.length);
                gzipos.flush();
                gzipos.close();
                baos.flush();
                baos.close();

                vb = baos.toByteArray();
                vc = new char[(vb.length * 2) + 1];

                vc[vb.length * 2] = 'X';
            } else {
                vc = new char[vb.length * 2];
            }

            for (int i = 0; i < vb.length; i++) {
                int k = vb[i];

                k += 128;
                vc[i * 2] = (char) ((k / 16) + 'A');
                vc[(i * 2) + 1] = (char) ((k % 16) + 'A');
            }

            return new String(vc);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Writer Serializer exception", t);
            return null;
        }
    }

    /**
     * Convert back from byte[] to an object instance
     * 
     * @param from
     *            serialized data
     * @return object
     */
    public static final Object deserializeFromBytes(final byte[] from) {
        try {
            final ByteArrayInputStream bais = new ByteArrayInputStream(from);
            final ObjectInputStream ois = new ObjectInputStream(bais);
            return ois.readObject();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Writer Deserializer exception", t);
        }
        return null;
    }

    /**
     * Convert back from String to an object instance
     * 
     * @param s
     *            serialized to String version
     * @return object
     */
    public static final Object deserializeFromString(final String s) {
        return deserializeFromString(s, false);
    }

    /**
     * Convert back from String to an object instance
     * 
     * @param s
     *            serialized to String version
     * @param bLog
     *            log errors
     * @return object
     */
    public static final Object deserializeFromString(final String s, final boolean bLog) {
        try {
            if ((s != null) && (s.length() > 0)) {
                int l = s.length() / 2;

                byte[] vb2 = new byte[l];
                char[] vc2 = s.toCharArray();

                for (int i = 0; i < l; i++) {
                    int k = ((vc2[i * 2] - 'A') * 16) + (vc2[(i * 2) + 1] - 'A');

                    vb2[i] = (byte) k;
                    vb2[i] -= 128;
                }

                if ((s.length() % 2) != 0) {
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    final byte[] buff = new byte[10240];

                    final GZIPInputStream gzipis = new GZIPInputStream(new ByteArrayInputStream(vb2));

                    int r;

                    while ((r = gzipis.read(buff, 0, buff.length)) > 0) {
                        baos.write(buff, 0, r);
                    }

                    baos.flush();
                    vb2 = baos.toByteArray();
                }

                return deserializeFromBytes(vb2);
            }

            if (bLog) {
                logger.log(Level.INFO, "Writer Deserializer: initial string was empty");
            }

            return null;
        } catch (Throwable t) {
            if (bLog) {
                logger.log(Level.WARNING, "Writer Deserializer exception: ", t);
            }
            return null;
        }
    }

    @Override
    public final boolean insert(final long rectime, final Result r, final int iParam, final double mval,
            final double mmin, final double mmax) {
        if (Double.isNaN(mval) || Double.isNaN(mmin) || Double.isNaN(mmax) || Double.isInfinite(mval)
                || Double.isInfinite(mmin) || Double.isInfinite(mmax)) {
            logger.log(Level.FINE, " [Writer] ** Skipping : (" + iParam + ") " + r);
            logger.log(Level.FINE, " [Writer] ** Because  : " + mval + ", " + mmin + ", " + mmax);
            return false;
        }

        if ((this.iWriteMode == 0) || (this.iWriteMode == 1)) {
            BatchProcessor.add(this.sTableName, rectime, r, r.param_name[iParam], mval, mmin, mmax);
        } else if ((this.iWriteMode == 3) || (this.iWriteMode == 4)) {
            ((MemWriter) this).addData(rectime, r, r.param_name[iParam], mval, mmin, mmax);
        } else if (((this.iWriteMode >= 5) && (this.iWriteMode <= 8)) || (this.iWriteMode == 11)
                || (this.iWriteMode == 12)) {
            final Integer id = IDGenerator.getId(r, iParam);

            if (id == null) {
                System.err.println("ID was null for : " + r + " / iParam = " + iParam);

                return false;
            }

            IDGenerator.updateLastSeen(id, rectime, mval);

            if ((this.iWriteMode == 5) || (this.iWriteMode == 6)) {
                BatchProcessor.add(this.sTableName, rectime, id.intValue(), mval, mmin, mmax);
            } else if ((this.iWriteMode == 7) || (this.iWriteMode == 8)) {
                BatchProcessor.add(this.sTableName + "_" + id, (int) (rectime / 1000), mval, mmin, mmax);
            } else {
                BatchProcessor.add(this.sTableName + "_" + nameTransform(r.param_name[iParam]), (int) (rectime / 1000),
                        id.intValue(), mval, mmin, mmax);
            }
        }

        return true;
    }

    /**
     * Get the table name suffix for a parameter name
     * 
     * @param sName
     *            parameter name
     * @return table name suffix
     */
    public static final String nameTransform(final String sName) {
        if ((sName == null) || (sName.length() == 0)) {
            return sName;
        }

        final char[] vc = sName.trim().toLowerCase().toCharArray();

        final StringBuilder sb = new StringBuilder(20);

        char c;

        for (char element : vc) {
            c = element;

            if (((c >= 'a') && (c <= 'z')) || ((c >= '0') && (c <= '9')) || (c == '_')) {
                sb.append(c);
            }

            if (sb.length() == sb.capacity()) {
                return sb.toString();
            }
        }

        return sb.toString();
    }

    /**
     * Clean the last values hash of old entries
     * 
     * @param lInterval
     *            cleanup interval
     */
    public final void cleanHash(final long lInterval) {
        if (this.m == null) {
            return;
        }

        synchronized (this.mLock) {
            final long now = NTPDate.currentTimeMillis();

            final Iterator<Map.Entry<Object, CacheElement>> it = this.m.entrySet().iterator();

            CacheElement ce;
            Map.Entry<Object, CacheElement> me;

            while (it.hasNext()) {
                me = it.next();

                ce = me.getValue();

                if (ce.lLastUpdate < (now - (2 * lInterval))) {
                    ce.checkFlush(now);

                    if (!ce.bSomeData) {
                        it.remove();
                    }
                }
            }
        }
    }

    /**
     * How to escape a slash
     */
    private static final char[] ESC_SLASH = new char[] { '\\', '\\' };

    /**
     * Quote
     */
    private static final char[] ESC_QUOTE = new char[] { '\'', '\'' };

    /**
     * Double quote
     */
    private static final char[] ESC_DQUOTE = new char[] { '\\', '"' };

    /**
     * \n
     */
    private static final char[] ESC_N = new char[] { '\\', 'n' };

    /**
     * \r
     */
    private static final char[] ESC_R = new char[] { '\\', '0' };

    /**
     * char(0)
     */
    private static final char[] ESC_0 = new char[] { '\\', '0' };

    /**
     * Make a SQL-safe string out of the given parameter
     * 
     * @param s
     *            string to escape
     * @return SQL-safe string
     */
    public final static String esc(final String s) {
        final char[] vc = s.toCharArray();
        final int l = vc.length;

        final StringBuilder sb = new StringBuilder(l + 30);

        char c;

        for (int i = 0; i < l; i++) {
            c = vc[i];
            switch (c) {
            case '\\':
                sb.append(ESC_SLASH);
                break;
            case '\'':
                sb.append(ESC_QUOTE);
                break;
            case '\"':
                sb.append(ESC_DQUOTE);
                break;
            case '\n':
                sb.append(ESC_N);
                break;
            case '\r':
                sb.append(ESC_R);
                break;
            case (char) 0:
                sb.append(ESC_0);
                break;
            default:
                sb.append(c);
            }
        }

        return sb.toString();
    }

    /**
     * Make the last values map persistent in the database
     * 
     * @param sTableName
     *            table name or table preffix of the writer
     * @param mData
     *            data to write
     */
    protected static final void savePrevData(final String sTableName,
            final HashMap<? extends Object, CacheElement> mData) {
        // TODO : is it still worth to do this ?
    }

    /**
     * Get the previous map from the database for a given table name
     * 
     * @param sTableName
     *            table name
     * @return last values map from the database
     */
    protected static final HashMap<Object, CacheElement> readPrevData(final String sTableName) {
        // TODO: only if necessary (and it doesn't seem to be)

        return null;
    }

    /**
     * lock
     */
    private static final Object oLock = new Object();

    /**
     * Execute maintenance operations serialized, to not overload the database with concurrent expensive operations.
     * 
     * @param sQuery
     *            query to execute
     * @param bIgnoreErrors
     *            whether or not to ignore possible errors
     * @param _db
     *            database object to execute queries through. Can be null, in this case a fresh DB object will be
     *            created
     * @return true if the operation was successful, false if not
     */
    protected static final boolean execMaintenance(final String sQuery, final boolean bIgnoreErrors, DB _db) {
        final DB db = _db != null ? _db : new DB();

        synchronized (oLock) {
            return db.query(sQuery, bIgnoreErrors, true);
        }
    }

}
