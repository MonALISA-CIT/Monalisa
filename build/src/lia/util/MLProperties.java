/**
 * Utility class to process enhanced .properties file.
 */
package lia.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.ExtResult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.TimestampedResult;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.ntp.NTPDate;

/**
 * Utility class to process enhanced .properties file.<br>
 * <br>
 * Such a file can have a special "include" key, which is a list of comma-separated file names 
 * (without the .properties extension) relative to the same base folder.<br>
 * <br>
 * Each value can contain constructions like:
 * <ul>
 * 	<li> <code>${other_key}</code> , to include the value of another key in that place </li>
 * 	<li> <code>$QSQL SELECT QUERY;</code> , to include the results of a database query (the values from the first column
 *      are included, separated by commas</li>
 * 	<li> <code>$C[FCNfv]Predicate;</code> , to include the last known value from the memory cache like: F=Farm name,
 *      C=Cluster name, N=Node name, f=parameter name, v=value. In case of multiple results the list of
 *      results will be comma-separated.</li>
 * </ul>
 * <br>
 * The <code>Predicate</code> can be given in one of the following forms:
 * <ul>
 *  <li> <code>Farm/Cluster/Node/tmin/tmax/ParameterList</code></li>
 *  <li> <code>Farm/Cluster/Node/tmin/ParameterList</code> (tmax=-1)</li>
 *  <li> <code>Farm/Cluster/Node/ParameterList</code> (tmin = tmax = -1)</li>
 *  <li> <code>Farm/Cluster/Node</code> (tmin = tmax = -1, ParameterList="*")</li>
 *  <li> <code>Farm/Cluster</code> (tmin = tmax = -1, Node = ParameterList="*")</li>
 *  <li> <code>Farm</code> (tmin = tmax = -1, Cluster = Node = ParameterList="*")</li>
 * </ul>
 * <br>
 * The <code>ParameterList</code> is a list of strings separated by "|".
 *  
 * @author costing
 * @since 2006-06-06
 */
public final class MLProperties {

    private static final Logger logger = Logger.getLogger(MLProperties.class.getName());

    /** The original contens */
    private Properties prop = null;

    /** Cached key lookups */
    private HashMap<String, String> hmCached = null;

    /** Remember the file name */
    private String sConfigFile = null;

    /** Constructor values, for reloading */
    private String sConfDir, sFileName;

    private MLProperties pSuper;

    private HashMap<String, String> hmExtraSet = null;

    /**
     * Default constructor, empty properties
     */
    public MLProperties() {
        prop = new Properties();
    }

    /**
     * Constructor based on the full path to the .properties file. It accepts the files with
     * or without the ".properties" extension (if it is not provided then it will be appended
     * automatically).
     * 
     * @param sFile full path to the configuration file
     * @throws IOException
     */
    public MLProperties(final String sFile) throws IOException {
        if ((sFile == null) || (sFile.length() == 0)) {
            return;
        }

        if (sFile.endsWith(".properties")) {
            sFileName = sFile.substring(0, sFile.lastIndexOf('.'));
        } else {
            sFileName = sFile;
        }

        int idx = sFileName.indexOf(File.separator);

        if (idx < 0) {
            sConfDir = new File(".").getAbsolutePath();
        } else {
            sConfDir = sFileName.substring(0, idx);
            sFileName = sFileName.substring(idx + 1);
        }

        pSuper = null;

        reload();
    }

    /**
     * Load the contents of a .properties file from the sConfDir path.
     * If the pSuper parameter is not null then the values from the given MLProperties are inherited.
     * Any key defined in this class will override any inherited key.
     * 
     * @param confDir base folder where the configuration files reside
     * @param fileName file name, without the ".properties" extension 
     * @param parent options to inherit. Keys with the same name defined in the current file will override the ones inherited. 
     * 
     * @throws IOException an exception might be thrown if the file cannot be read 
     */
    public MLProperties(final String confDir, final String fileName, final MLProperties parent) throws IOException {
        this.sConfDir = confDir;
        this.sFileName = fileName;
        this.pSuper = parent;

        reload();
    }

    /**
     * Get the name of the file that was loaded
     * @return the full path to the file
     */
    public String getConfigFileName() {
        return sConfigFile;
    }

    /**
     * Re-read the same configuration file. Also clears the cache.
     * 
     * @throws IOException in case there is a problem reading the file
     */
    public void reload() throws IOException {
        prop = load(sConfDir, sFileName, pSuper, new HashSet<String>());

        hmCached = null;

        if (hmExtraSet != null) {
            prop.putAll(hmExtraSet);
        }
    }

    /**
     * Recursive function to actually load the contents of the given .properties file.
     * Iterates through all the "include" keys and recursively loads them.
     * 
     * @param confDir   the base folder for all .properties file, should end with "/" !
     * @param fileName  file name without the .properties extension, can contain folder names
     * @param parent     default values
     * @param hsIncluded previously included files, to not include them again
     * @return           the final Properties
     */
    private Properties load(final String confDir, final String fileName, final MLProperties parent,
            final HashSet<String> hsIncluded) {
        Properties properties = parent != null ? new Properties(parent.prop) : new Properties();

        FileInputStream fis = null;

        try {
            sConfigFile = confDir + (confDir.endsWith(File.separator) ? "" : File.separator) + fileName + ".properties";

            fis = new FileInputStream(sConfigFile);

            properties.load(fis);
        } catch (IOException ioe) {
            sConfigFile = null;

            return properties;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ioe2) {
                    // ignore
                }
            }
        }

        final String sInclude = properties.getProperty("include");

        if ((sInclude != null) && (sInclude.length() > 0)) {
            final StringTokenizer st = new StringTokenizer(sInclude, ";, \t");

            while (st.hasMoreTokens()) {
                final String sIncludeFile = st.nextToken();

                if (!hsIncluded.contains(sIncludeFile)) {
                    hsIncluded.add(sIncludeFile);

                    final Properties pTemp = load(confDir, sIncludeFile, null, hsIncluded);

                    pTemp.putAll(properties);

                    properties = pTemp;
                }
            }
        }

        return properties;
    }

    /**
     * Keep a cache of previous database queries to avoid executing the same query multiple times.
     */
    static final HashMap<String, QueryCacheEntry> hmQueryCache = new HashMap<String, QueryCacheEntry>();

    /**
     * A query cache entry, contains the cached values and the expire time. 
     */
    private static class QueryCacheEntry {
        /**
         * Values returned by the cached query
         */
        final ArrayList<String> value;

        /**
         * The point in time when the entry will not be valid any more
         */
        final long lExpires;

        /**
         * Create a new cache entry with a list of values and an expiration time
         * 
         * @param _value values to be cached
         * @param _lExpires point in time when the entry will not be valid anyt
         */
        QueryCacheEntry(final ArrayList<String> _value, final long _lExpires) {
            value = _value;
            lExpires = _lExpires;
        }
    }

    /**
     * Cleanup the query cache by removing the values that have the expiration time &lt; current time; 
     */
    private static final class QueryCacheCleaner extends Thread {
        /**
         * Default constructor
         */
        public QueryCacheCleaner() {
            super("(ML) MLProperties.QueryCacheCleaner");
            try {
                setDaemon(true);
            } catch (Exception e) {
                // ignore
            }
        }

        /**
         * Periodically remove old entries from the query results cache
         */
        @Override
        public void run() {
            while (true) {
                final long lNow = NTPDate.currentTimeMillis();

                synchronized (hmQueryCache) {
                    final Iterator<Map.Entry<String, QueryCacheEntry>> it = hmQueryCache.entrySet().iterator();

                    while (it.hasNext()) {
                        final Map.Entry<String, QueryCacheEntry> me = it.next();

                        final QueryCacheEntry qce = me.getValue();

                        if (lNow > qce.lExpires) {
                            it.remove();
                        }
                    }
                }

                try {
                    sleep(5000);
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    static {
        (new QueryCacheCleaner()).start();
    }

    /**
     * Get the cached values from a previous identic query. If there is no such entry or
     * it has already expired then execute the query and store the results in the cache.
     */
    private static ArrayList<String> getCachedQueryResult(final String sQuery) {
        QueryCacheEntry qce;

        synchronized (hmQueryCache) {
            qce = hmQueryCache.get(sQuery);
        }

        if ((qce != null) && (NTPDate.currentTimeMillis() < qce.lExpires)) {
            return qce.value;
        }

        final ArrayList<String> alValues = new ArrayList<String>();

        try {
            final lia.Monitor.Store.Fast.DB db = new lia.Monitor.Store.Fast.DB();
            
            db.setReadOnly(true);
            
            db.query(sQuery);

            while (db.moveNext()) {
                alValues.add(db.gets(1));
            }
        } catch (Throwable t) {
            // ignore
        }

        qce = new QueryCacheEntry(alValues, NTPDate.currentTimeMillis() + (1000 * 30));

        synchronized (hmQueryCache) {
            hmQueryCache.put(sQuery, qce);
        }

        return alValues;
    }

    /**
     * Parse a value to include other keys, database queries or cached results from memory.
     * 
     * @param sKey the key that is parsed
     * @param sValOrig original value
     * @param sDefault default value, in case of an error in parsing
     * @param bProcessQueries whether or not to execute database queries / cache lookups
     * @return the processed value
     */
    public String parseOption(final String sKey, final String sValOrig, final String sDefault,
            final boolean bProcessQueries) {
        int i = 0;

        StringBuilder sbVal = new StringBuilder();
        String sValSuffix = "";
        String sVal = sValOrig;

        // see if there are any other keys' values to include
        while ((i = sVal.indexOf("${")) >= 0) {
            final int i2 = sVal.indexOf("}", i);

            if (i2 > 0) {
                final String s = sVal.substring(i + 2, i2);

                if (s.equals(sKey)) {
                    return sDefault;
                }

                sbVal.append(sVal.substring(0, i));
                sbVal.append(gets(s, "", bProcessQueries));

                sVal = sVal.substring(i2 + 1);
            } else {
                break;
            }
        }

        if (sbVal.length() > 0) {
            // some processing occured here
            if (sVal.length() > 0) {
                sbVal.append(sVal);
            }

            sVal = sbVal.toString();
            sbVal = new StringBuilder();
        }

        int q;

        // process the database queries
        while (bProcessQueries && ((q = sVal.indexOf("$Q")) >= 0)) {
            if (q > 0) {
                String sValPrefix = sVal.substring(0, q);

                sbVal.append(sValPrefix);
            }

            int p = sVal.indexOf(";", q);

            if (p < 0) {
                p = sVal.length();
            } else {
                p++;
            }

            sValSuffix = sVal.substring(p);

            final String sQuery = sVal.substring(q + 2, p);

            final ArrayList<String> alValues = getCachedQueryResult(sQuery);

            for (int j = 0; j < alValues.size(); j++) {
                if (j > 0) {
                    sbVal.append(',');
                }

                sbVal.append(alValues.get(j));
            }

            sVal = sValSuffix;
        }

        if (sbVal.length() > 0) {
            // some processing occurred here
            if (sVal.length() > 0) {
                sbVal.append(sVal);
            }

            sVal = sbVal.toString();
            sbVal = new StringBuilder();
        }

        // now process the data cache queries
        while (bProcessQueries && ((q = sVal.indexOf("$C")) >= 0)) {
            if (q > 0) {
                sbVal.append(sVal.substring(0, q));
            }

            final int p = sVal.indexOf(";", q);

            if (p < 0) {
                sValSuffix = "";
                sVal = sVal.substring(q + 2);
            } else {
                sValSuffix = sVal.substring(p + 1);
                sVal = sVal.substring(q + 2, p);
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Evaluating cache request : '" + sVal + "'");
            }

            final TreeSet<String> ts = new TreeSet<String>(); //it's a sorted collection

            if (sVal.length() >= 2) {
                final char c = sVal.charAt(0);

                sVal = sVal.substring(1).trim();

                final monPredicate pred = toPred(sVal);

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "Predicate is : " + pred);
                }

                final Vector<TimestampedResult> v;
                try {
                    v = lia.Monitor.Store.Cache.getLastValues(pred);
                } catch (Throwable t) {
                    continue;
                }

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "Values were : " + v);
                }

                String s = null;

                for (i = 0; i < v.size(); i++) {
                    String sFarmName;
                    String sClusterName;
                    String sNodeName;
                    String sParamName;
                    String sValue;

                    final TimestampedResult o = v.get(i);

                    if (o instanceof Result) {
                        final Result r = (Result) o;

                        sFarmName = r.FarmName;
                        sClusterName = r.ClusterName;
                        sNodeName = r.NodeName;
                        sParamName = r.param_name[0];
                        sValue = "" + r.param[0];
                    } else if (o instanceof eResult) {
                        final eResult r = (eResult) o;

                        sFarmName = r.FarmName;
                        sClusterName = r.ClusterName;
                        sNodeName = r.NodeName;
                        sParamName = r.param_name[0];
                        sValue = r.param[0].toString();
                    } else if (o instanceof ExtResult) {
                        final ExtResult r = (ExtResult) o;

                        sFarmName = r.FarmName;
                        sClusterName = r.ClusterName;
                        sNodeName = r.NodeName;
                        sParamName = r.param_name[0];

                        sValue = "" + r.param[0];
                    } else {
                        continue;
                    }

                    switch (c) {
                    case 'C':
                        s = sClusterName;
                        break;
                    case 'N':
                        s = sNodeName;
                        break;
                    case 'f':
                        s = sParamName;
                        break;
                    case 'v':
                        s = sValue;
                        break;
                    case 't':
                        s = "" + o.getTime();
                        break;
                    case 'F':
                    default:
                        s = sFarmName;
                    }

                    ts.add(s);
                }
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Extracted values : " + ts);
            }

            final Iterator<String> it = ts.iterator();

            if (it.hasNext()) {
                sbVal.append(it.next().toString());

                while (it.hasNext()) {
                    sbVal.append(',').append(it.next().toString());
                }
            }

            sVal = sValSuffix;
        }

        if (sbVal.length() > 0) {
            if (sVal.length() > 0) {
                sbVal.append(sVal);
            }

            sVal = sbVal.toString();
            sbVal = new StringBuilder();
        }

        while (bProcessQueries && ((q = sVal.indexOf("$E")) >= 0)) {
            if (q > 0) {
                String sValPrefix = sVal.substring(0, q);

                sbVal.append(sValPrefix);
            }

            final int p = sVal.indexOf(";", q);

            final String sQuery;

            if (p < 0) {
                sQuery = sVal.substring(q + 2);
                sVal = "";
            } else {
                sQuery = sVal.substring(q + 2, p);
                sVal = sVal.substring(p + 1);
            }

            try {
                sbVal.append(JEPHelper.evaluateExpression(sQuery));
            } catch (Throwable t) {
                // ignores
            }
        }

        if (sbVal.length() > 0) {
            if (sVal.length() > 0) {
                sbVal.append(sVal);
            }

            sVal = sbVal.toString();
        }

        return sVal;
    }

    /**
     * Parse a String and extract the coresponding monPredicate.
     * @param s the String to parse. See class description for the format. 
     * @return the monPredicate
     */
    public static monPredicate toPred(final String s) {
        if ((s == null) || (s.trim().length() <= 0)) {
            return null;
        }

        final StringTokenizer st = new StringTokenizer(s, "/");

        if (!st.hasMoreTokens()) {
            return null;
        }

        final String sFarm = st.hasMoreTokens() ? st.nextToken() : "*";
        final String sCluster = st.hasMoreTokens() ? st.nextToken() : "*";
        final String sNode = st.hasMoreTokens() ? st.nextToken() : "*";

        final String sTime1 = st.hasMoreTokens() ? st.nextToken() : "-1";
        final String sTime2 = st.hasMoreTokens() ? st.nextToken() : "-1";

        // The default format is F/C/N/time1/time2/P
        // but it accepts two alternatives:
        // F/C/N/time1/P	with time2 = -1
        // F/C/N/P			with time1 = time2 = -1
        // the alternatives are chosen based on the existence of the other parameters and on the fact that they are numeric or not
        String sFunc = st.hasMoreTokens() ? st.nextToken() : null;

        long lTime1, lTime2;

        try {
            lTime1 = Long.parseLong(sTime1);

            try {
                lTime2 = Long.parseLong(sTime2);
            } catch (Exception e) {
                lTime2 = -1;
                sFunc = sTime2;
            }
        } catch (Exception e) {
            lTime1 = lTime2 = -1;
            sFunc = sTime1;
        }

        String[] vsFunc;
        if (sFunc == null) {
            vsFunc = null;
        } else {
            final ArrayList<String> v = new ArrayList<String>();
            final StringTokenizer st2 = new StringTokenizer(sFunc, "|");

            while (st2.hasMoreTokens()) {
                v.add(st2.nextToken().trim());
            }

            if (v.size() > 0) {
                vsFunc = new String[v.size()];
                for (int j = 0; j < v.size(); j++) {
                    vsFunc[j] = v.get(j);
                }
            } else {
                vsFunc = null;
            }
        }

        return new monPredicate(sFarm, sCluster, sNode, lTime1, lTime2, vsFunc, null);
    }

    /**
     * Get the String value for a given key. If the key is not defined then the empty string is returned.
     * Queries will be processed. 
     * 
     * @param sKey the key to get the value for
     * @return the value
     * 
     * @see #gets(String, String, boolean)
     */
    public String gets(final String sKey) {
        return gets(sKey, "");
    }

    /**
     * Get the String value for a given key, returning the given default value if the key is not defined.
     * Queries will be processed.
     * 
     * @param sKey the key to get the value for
     * @param sDefault default value to return in case the key is not defined
     * @return the value
     * 
     * @see #gets(String, String, boolean)
     */
    public String gets(final String sKey, final String sDefault) {
        return gets(sKey, sDefault, true);
    }

    /**
     * Get the String value for a given key, returning the given default value if the key is not defined.
     * The value that is returned is also stored in a cache so that future requests to the same key
     * will return the value from the cache. This also means that if the key is not defined then the
     * given default value will be cached and returned the next time this function is called. <code>null</code>
     * values are not cached.
     * 
     * @param sKey the key to get the value for
     * @param sDefault default value to return in case the key is not defined. 
     * @param bProcessQueries flag to process or not process the database/memory cache queries
     * @return value for this key
     */
    public String gets(final String sKey, final String sDefault, final boolean bProcessQueries) {
        if (sKey == null) {
            return sDefault;
        }

        if (hmCached != null) {
            final String sValue = hmCached.get(sKey);

            if (sValue != null) {
                return sValue;
            }
        }

        String sReturn = sDefault;

        if (prop.getProperty(sKey) != null) {
            final String sVal = prop.getProperty(sKey).trim();

            sReturn = parseOption(sKey, sVal, sDefault, bProcessQueries);
        }

        if (sReturn != null) {
            if (hmCached == null) {
                hmCached = new HashMap<String, String>();
            }

            hmCached.put(sKey, sReturn);
        }

        return sReturn;
    }

    /**
     * Parse an option to return the boolean value.
     * It returns true if the value starts with t,T,y,Y or 1 and false if the value starts with f,F,n,N or 0.
     * In any other case it returns the given default value. 
     * 
     * @param sKey the key to get the value for
     * @param bDefault default value
     * @return a boolean
     */
    public boolean getb(final String sKey, final boolean bDefault) {
        final String s = gets(sKey, "" + bDefault);

        if (s.length() > 0) {
            final char c = s.charAt(0);

            if ((c == 't') || (c == 'T') || (c == 'y') || (c == 'Y') || (c == '1')) {
                return true;
            }

            if ((c == 'f') || (c == 'F') || (c == 'n') || (c == 'N') || (c == '0')) {
                return false;
            }
        }

        return bDefault;
    }

    /**
     * Get the integer value for a key. Returns the given default value if the key is not defined or the value
     * is not an integer reprezentation.
     * 
     * @param sKey the key to get the value for
     * @param iDefault default value
     * @return an integer
     */
    public int geti(final String sKey, final int iDefault) {
        try {
            return Integer.parseInt(gets(sKey, "" + iDefault));
        } catch (Exception e) {
            return iDefault;
        }
    }

    /**
     * Get the long value for a key. Returns the given default value if the key is not defined or the value
     * is not a long reprezentation.
     * 
     * @param sKey the key to get the value for
     * @param lDefault default value
     * @return a long
     */
    public long getl(final String sKey, final long lDefault) {
        try {
            return Long.parseLong(gets(sKey, "" + lDefault));
        } catch (Exception e) {
            return lDefault;
        }
    }

    /**
     * Get the double value for a key. Returns the given default value if the key is not defined or the value
     * is not a double reprezentation.
     * 
     * @param sKey the key to get the value for
     * @param dDefault default value
     * @return a double
     */
    public double getd(final String sKey, final double dDefault) {
        try {
            return Double.parseDouble(gets(sKey, "" + dDefault));
        } catch (Exception e) {
            return dDefault;
        }
    }

    /**
     * Split a value by "," and return a Vector of String parts.
     * 
     * @param sKey the key to get the values for
     * @return a Vector of String parts
     */
    public Vector<String> toVector(final String sKey) {
        final String sVal = gets(sKey);

        final StringTokenizer st = new StringTokenizer(sVal, ",");

        final Vector<String> vReturn = new Vector<String>();

        while (st.hasMoreTokens()) {
            vReturn.add(st.nextToken());
        }

        return vReturn;
    }

    /**
     * Method to clear the cache in order to force the evaluation of the keys once again.
     */
    public void clearCache() {
        hmCached.clear();
    }

    /**
     * Debug method
     * 
     * @return dump of the internal structures
     */
    @Override
    public String toString() {
        return "Original properties:\n" + (prop != null ? prop.toString() : "null") + "\n\nCached values:\n"
                + (hmCached != null ? hmCached.toString() : "null") + "\n";
    }

    /**
     * Handy function for fast replacement of string parts.
     * 
     * @param sOrig original string
     * @param sWhat string to search for
     * @param sWith the new value that will replace the searched string in all occurences
     * @return the original string with the replacements
     */
    public static String replace(final String sOrig, final String sWhat, final String sWith) {
        if (sOrig == null) {
            return null;
        }

        String s = sOrig;

        StringBuilder sb = null;

        final int iWlen = sWhat.length();
        int i;
        while ((i = s.indexOf(sWhat)) >= 0) {
            if (sb == null) {
                int diff = iWlen - sWith.length();
                if (diff < 0) {
                    diff = 0;
                } else {
                    diff *= 2; // assume 2 appearances of this replacement
                }

                sb = new StringBuilder(s.length() + diff);
            }

            sb.append(s.substring(0, i));
            sb.append(sWith);
            s = s.substring(i + iWlen);
        }

        if (sb != null) {
            sb.append(s);
            return sb.toString();
        }

        // no replacements to do
        return s;
    }

    /**
     * Override the given key with a new value.
     * 
     * @param sKey key to override
     * @param sValue new value
     */
    public void set(final String sKey, final String sValue) {
        prop.put(sKey, sValue);

        if (hmCached != null) {
            hmCached.remove(sKey);
        }

        if (hmExtraSet == null) {
            hmExtraSet = new HashMap<String, String>();
        }

        hmExtraSet.put(sKey, sValue);
    }

    /**
     * Get a copy of all the values in this dictionary, with all the values resolved
     * 
     * @return clone
     */
    public Properties getProperties() {
        final Properties ret = new Properties();

        final Iterator<Object> it = prop.keySet().iterator();

        while (it.hasNext()) {
            final String sKey = (String) it.next();

            ret.setProperty(sKey, gets(sKey));
        }

        return ret;
    }

}
