package lia.Monitor.Store.Fast;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.Fast.Replication.ReplicationManager;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ExtResult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.StringFactory;
import lia.web.utils.Formatare;

/**
 * @author costing
 *
 */
public final class IDGenerator {

    private static final Logger logger = Logger.getLogger(IDGenerator.class.getName());

    /**
     * keep an LRU list of ids
     */
    private static final LinkedHashMap<String, IDEntry> hmIDs = new LinkedHashMap<String, IDEntry>(1024, 0.75f, true);

    /**
     * keep an LRU list of reverse mappings
     */
    private static final LinkedHashMap<Integer, IDEntry> hmReverseIDs = new LinkedHashMap<Integer, IDEntry>(1024,
            0.75f, true);

    /**
     * @author costing
     * @since Jun 3, 2008
     */
    private static final class IDEntry {
        /**
         * ID in the monitor_ids table
         */
        final int id;

        /**
         * Key, as "Farm/Cluster/Node/Parameter"
         */
        final String key;

        /**
         * Split of the key into its components
         */
        final KeySplit split;

        /**
         * When have we last seen a value from this parameter?
         */
        long lLastSeen;

        /**
         * When was this parameter non-zero last?
         */
        long lLastNonZero;

        /**
         * Simple initialization
         * 
         * @param iID
         * @param sKey
         */
        public IDEntry(final int iID, final String sKey) {
            this.id = iID;
            this.key = sKey;

            this.split = new KeySplit(key);
        }
    }

    private static IDEntry getEntry(final Integer id) {
        return hmReverseIDs.get(id);
    }

    private static IDEntry getEntry(final String key) {
        return hmIDs.get(key);
    }

    /**
     * Splitting a key string like "Farm/Cluster/Node/Function" in its components
     * 
     * @author costing
     * @since Jun 2, 2008
     */
    public static final class KeySplit {
        /**
         * farm name
         */
        public final String FARM;

        /**
         * cluster name
         */
        public final String CLUSTER;

        /**
         * node name
         */
        public final String NODE;

        /**
         * parameter name
         */
        public final String FUNCTION;

        /**
         * @param sKey
         */
        public KeySplit(final String sKey) {
            final StringTokenizer st = new StringTokenizer(sKey, SEPARATOR);
            String sFarm = st.nextToken();
            String sCluster = st.nextToken();
            String sNode = st.nextToken();
            String sFunction = st.nextToken();

            if (st.hasMoreTokens()) {
                sFunction += st.nextToken("");
            }

            if (sFarm.equals("null")) {
                sFarm = null;
            } else {
                sFarm = StringFactory.get(sFarm);
            }

            if (sCluster.equals("null")) {
                sCluster = null;
            } else {
                sCluster = StringFactory.get(sCluster);
            }

            if (sNode.equals("null")) {
                sNode = null;
            } else {
                sNode = StringFactory.get(sNode);
            }

            if (sFunction.equals("null")) {
                sFunction = null;
            } else {
                sFunction = StringFactory.get(sFunction);
            }

            FARM = sFarm;
            CLUSTER = sCluster;
            NODE = sNode;
            FUNCTION = sFunction;
        }
    }

    static {
        init();
    }

    private static boolean bInitialized = false;

    /**
     * Initialize the IDs with the values saved in the database, if a database is available.
     * 
     * @return <code>true</code> if it did something, <code>false</code> if not
     */
    private static synchronized boolean init() {
        if (!bInitialized && hasDB()) {
            initStructure();
            readIDs();

            bInitialized = true;

            return true;
        }

        return false;
    }

    private static void initStructure() {
        final DB db = new DB();

        if (!db.syncUpdateQuery(
                "CREATE TABLE monitor_ids (mi_id int primary key, mi_key varchar(255), mi_lastseen int default 0, mi_lastvalue "
                        + DBWriter3.sFloatingPointDef + " default 0, mi_lastnonzero int default 0);", true)) {
            db.query("ALTER TABLE monitor_ids ADD COLUMN mi_lastseen int;", true, true);
            db.query("ALTER TABLE monitor_ids ALTER COLUMN mi_lastseen SET DEFAULT 0;", true, true);
            db.query("ALTER TABLE monitor_ids ADD COLUMN mi_lastvalue " + DBWriter3.sFloatingPointDef + ";", true, true);
            db.query("ALTER TABLE monitor_ids ALTER COLUMN mi_lastvalue SET DEFAULT 0;", true, true);
            db.query("ALTER TABLE monitor_ids ADD COLUMN mi_lastnonzero int;", true, true);
            db.query("ALTER TABLE monitor_ids ALTER COLUMN mi_lastnonzero SET DEFAULT 0;", true, true);
        }

        db.query("CREATE UNIQUE INDEX monitor_ids_key_uidx ON monitor_ids (mi_key);", true, true);
        db.query("CREATE INDEX monitor_ids_lastseen_idx ON monitor_ids (mi_lastseen);", true, true);
        db.query("CREATE INDEX monitor_ids_lastnonzero_idx ON monitor_ids (mi_lastnonzero);", true, true);
        db.query("ALTER TABLE monitor_ids ALTER COLUMN mi_lastseen SET STATISTICS 1000;", true, true);
        db.query("ALTER TABLE monitor_ids ALTER COLUMN mi_lastnonzero SET STATISTICS 1000;", true, true);
        db.maintenance("CLUSTER monitor_ids_lastseen_idx ON monitor_ids;");
        db.maintenance("ANALYZE monitor_ids;");
    }

    private static void readIDs() {
        hmIDs.clear();
        hmReverseIDs.clear();

        if (hasDB()) {
            final DB db = new DB();
            
            db.setReadOnly(true);

            Integer id;
            String sKey;

            if (db.query("SELECT mi_id,mi_key,mi_lastseen,mi_lastnonzero FROM monitor_ids;")) {
                while (db.moveNext()) {
                    id = Integer.valueOf(db.geti(1));
                    sKey = db.gets(2);

                    final IDEntry entry = new IDEntry(db.geti(1), sKey);

                    hmIDs.put(sKey, entry);
                    hmReverseIDs.put(id, entry);

                    entry.lLastSeen = db.getl(3) * 1000;
                    entry.lLastNonZero = db.getl(4) * 1000;
                }
            }
        }
    }

    /**
     * Iterate through the database IDs and set the VO names in upper case
     */
    public static synchronized void correctVONames() {
        final Vector<Integer> vIDs = getIDs(new monPredicate("*", "VO_%", "*", -1, -1, new String[] { "*" }, null));
        vIDs.addAll(getIDs(new monPredicate("*", "osgVoStorage", "*", -1, -1, new String[] { "*" }, null)));
        vIDs.addAll(getIDs(new monPredicate("*", "osgVO_%", "*", -1, -1, new String[] { "*" }, null)));
        vIDs.addAll(getIDs(new monPredicate("Totals", "Totals", "*", -1, -1, new String[] { "*" }, null)));

        final DB db = new DB();

        for (int i = 0; i < vIDs.size(); i++) {
            final Integer id = vIDs.get(i);

            if (id == null) {
                continue;
            }

            final KeySplit split = getKeySplit(id);

            if (split == null) {
                continue;
            }

            final String sNode = split.NODE;

            if ((sNode == null) || (split.CLUSTER == null) || split.CLUSTER.endsWith("_Totals")) {
                continue;
            }

            if (!sNode.equals(sNode.toUpperCase()) && !sNode.startsWith("Total")) {
                final String sNewKey = generateKey(split.FARM, split.CLUSTER, sNode.toUpperCase(), split.FUNCTION);

                if (getId(sNewKey) != null) {
                    // an uppercase variant already exists

                    removeID(id);
                } else {
                    // I can switch to upper case

                    db.query("UPDATE monitor_ids SET mi_key='" + Formatare.mySQLEscape(sNewKey) + "' WHERE mi_id=" + id
                            + ";");

                    readIDs(); // ensure consistency for other IDs
                }
            }
        }

        // apply id migration only once ...
        db.query("SELECT mi_id FROM monitor_ids WHERE mi_key LIKE '_TOTALS_/%/%/Tftp%' LIMIT 1;");
        if (!db.moveNext()) {
            db.query("update monitor_ids set mi_key=split_part(mi_key,\'/\',1)||\'/\'||split_part(mi_key,\'/\',2)||\'/\'||split_part(mi_key,\'/\',3)||\'/T\'||split_part(mi_key,\'/\',4) where mi_key like \'_TOTALS_/osg%\';");
            db.query("update monitor_ids set mi_key=split_part(mi_key,\'/\',1)||\'/\'||split_part(mi_key,\'/\',2)||\'/\'||split_part(mi_key,\'/\',3)||\'/T\'||split_part(mi_key,\'/\',4) where mi_key like \'%/osg%/_TOTALS_/%\';");
        }

        db.query("SELECT mi_id FROM monitor_ids WHERE mi_key like 'Totals/Totals/%/XRunning%' LIMIT 1;");
        if (!db.moveNext()) {
            db.query("update monitor_ids set mi_key=split_part(mi_key,\'/\',1)||\'/\'||split_part(mi_key,\'/\',2)||\'/\'||split_part(mi_key,\'/\',3)||\'/X\'||split_part(mi_key,\'/\',4) where mi_key like \'Totals/Totals/%\';");
        }

        readIDs();
    }

    private static int iUpdateCount = 0;

    /**
     * When was the last time when we read the update threshold value from the App.properties file
     * The option is 
     *    lia.Monitor.Store.Fast.IDGenerator.update_threshold=interval in minutes, default 30
     */
    private static long lLastUpdateThreshold = 0;

    /**
     * How much time must pass until the value is updated for a given series.
     * Can be set to zero so that updates to monitor_ids table occur immediately.
     */
    private static long lUpdateThreshold = 30;

    /**
     * How much time must pass until we update the last known non-zero column in the table
     * Can be set to zero so that updates to monitor_ids table occur immediately.
     */
    private static long lUpdateNonZeroThreshold = 8 * 60;

    /**
     * Update the last seen time for a series
     * 
     * @param id series ID
     * @param lTime last time
     * @param dLastValue last value
     */
    public static void updateLastSeen(final Integer id, final long lTime, final double dLastValue) {
        if (lTime > (lLastUpdateThreshold + (1000 * 60))) {
            lUpdateThreshold = AppConfig.getl("lia.Monitor.Store.Fast.IDGenerator.update_threshold", lUpdateThreshold);
            lUpdateNonZeroThreshold = AppConfig.getl("lia.Monitor.Store.Fast.IDGenerator.update_non_zero_threshold",
                    lUpdateNonZeroThreshold);

            lLastUpdateThreshold = lTime;
        }

        final IDEntry entry = getEntry(id);

        if (entry == null) {
            return;
        }

        if (((lTime - entry.lLastSeen) > (1000L * 60L * lUpdateThreshold))) {
            entry.lLastSeen = lTime;

            BatchProcessor.idUpdate(entry.id, (int) (lTime / 1000), dLastValue);

            iUpdateCount++;
        }

        if ((dLastValue > 1E-15) && ((lTime - entry.lLastNonZero) > (1000L * 60L * lUpdateNonZeroThreshold))) {
            entry.lLastNonZero = lTime;

            BatchProcessor.idUpdate(entry.id, (int) (lTime / 1000));

            iUpdateCount++;
        }
    }

    /**
     * Find out how many updates were done in the monitor_ids table
     * 
     * @return number of updates since last maintenance operation
     */
    public static int getUpdateCount() {
        return iUpdateCount;
    }

    /**
     * After a maintenance operation reset the operations count
     */
    public static void resetUpdateCount() {
        iUpdateCount = 0;
    }

    /**
     * Find out how many IDs we have in memory
     * 
     * @return count of IDs
     */
    public synchronized static int size() {
        return hmIDs.size();
    }

    /**
     * From a Result/eResult/ExtResult generate a key for one of the parameters
     * 
     * @param o
     * @param param
     * @return unique key
     */
    public static String generateKey(final Object o, final int param) {
        if (o instanceof Result) {
            return generateKey((Result) o, param);
        }

        if (o instanceof eResult) {
            return generateKey((eResult) o, param);
        }

        if (o instanceof ExtResult) {
            return generateKey((ExtResult) o, param);
        }

        return null;
    }

    /**
     * Generate unique key for one of the parameters in a Result
     * 
     * @param r
     * @param param
     * @return unique key
     */
    public static String generateKey(final Result r, final int param) {
        return generateKey(r.FarmName, r.ClusterName, r.NodeName, r.param_name[param]);
    }

    /**
     * Generate unique key for one of the parameters in an eResult
     * 
     * @param r
     * @param param
     * @return unique key
     */
    public static String generateKey(final eResult r, final int param) {
        return generateKey(r.FarmName, r.ClusterName, r.NodeName, r.param_name[param]);
    }

    /**
     * Generate unique key for one of the parameters in an ExtResult
     * 
     * @param r
     * @param param
     * @return unique key
     */

    public static String generateKey(final ExtResult r, final int param) {
        return generateKey(r.FarmName, r.ClusterName, r.NodeName, r.param_name[param]);
    }

    private static final String SEPARATOR = "/";

    /**
     * Generate unique key for a fully specified f/c/n/p combinatino
     * 
     * @param sFarm
     * @param sCluster
     * @param sNode
     * @param sFunction
     * @return unique key
     */

    public static String generateKey(final String sFarm, final String sCluster, final String sNode,
            final String sFunction) {
        if ((sFarm != null) && (sFarm.length() == 0)) {
            return null;
        }
        if ((sCluster != null) && (sCluster.length() == 0)) {
            return null;
        }
        if ((sNode != null) && (sNode.length() == 0)) {
            return null;
        }
        if ((sFunction != null) && (sFunction.length() == 0)) {
            return null;
        }

        return StringFactory.get(sFarm + SEPARATOR + sCluster + SEPARATOR + sNode + SEPARATOR + sFunction);
    }

    /**
     * Find out the ID for a given Result & parameter position
     * 
     * @param r
     * @param param
     * @return unique ID
     */
    public static Integer getId(final Result r, final int param) {
        return getId(generateKey(r, param));
    }

    /**
     * Find out the ID for a fully specified key
     * 
     * @param sFarm
     * @param sCluster
     * @param sNode
     * @param sFunction
     * @return unique ID
     */
    public static Integer getId(final String sFarm, final String sCluster, final String sNode, final String sFunction) {
        return getId(generateKey(sFarm, sCluster, sNode, sFunction));
    }

    private static int getMaxID() {
        int iMaxID = 0;

        final Iterator<IDEntry> it = hmIDs.values().iterator();

        while (it.hasNext()) {
            final IDEntry entry = it.next();

            if (entry.id > iMaxID) {
                iMaxID = entry.id;
            }
        }

        return iMaxID;
    }

    /**
     * Can we access the database?
     * 
     * @return <code>true</code> if database support is enabled and at least one backend is available, <code>false</code> if not
     */
    private static boolean hasDB() {
        return (!TransparentStoreFactory.isMemoryStoreOnly())
                && (ReplicationManager.getInstance().getOnlineBackendsCount() > 0);
    }

    /**
     * find out or generate ID for a given key
     * 
     * @param sKey
     * @return unique ID
     */
    public static synchronized Integer getId(final String sKey) {
        if (sKey == null) {
            return null;
        }

        IDEntry entry = getEntry(sKey);

        if (entry == null) {
            int nextVal = getMaxID() + 1;

            if (!TransparentStoreFactory.isMemoryStoreOnly()) {
                if (!hasDB()) {
                    // should have DB support but no backend is online

                    return null;
                }

                // make sure the structure is created, if the DB was not available at startup
                if (init()) {
                    // something happened, try again

                    entry = getEntry(sKey);

                    if (entry != null) {
                        return Integer.valueOf(entry.id);
                    }
                }

                final DB db = new DB();
                
                db.setReadOnly(true);
                
                if (db.query("SELECT max(mi_id) AS max_id FROM monitor_ids;") && db.moveNext()
                        && (db.geti(1) >= nextVal)) {
                    nextVal = db.geti(1) + 1;
                }

                if (!db.syncUpdateQuery("INSERT INTO monitor_ids (mi_id, mi_key) VALUES (" + nextVal + ", '"
                        + Formatare.mySQLEscape(sKey) + "');", true)) {
                    if (db.query("SELECT mi_id FROM monitor_ids WHERE mi_key='" + Formatare.mySQLEscape(sKey) + "';")
                            && db.moveNext() && (db.geti(1) > 0)) {
                        nextVal = db.geti(1);

                        logger.fine("In fact I had the key='" + sKey + "' in the database with ID = " + nextVal);
                    } else {
                        logger.warning("Could not insert the new ID : " + nextVal + " for the new key : '" + sKey
                                + "' !!!");
                        return null;
                    }
                }
            }

            entry = new IDEntry(nextVal, sKey);

            hmIDs.put(sKey, entry);
            hmReverseIDs.put(Integer.valueOf(nextVal), entry);
        }

        return Integer.valueOf(entry.id);
    }

    /**
     * Find out the key associated with a given ID
     * 
     * @param id
     * @return unique key
     */
    public static synchronized String getKey(final int id) {
        return getKey(Integer.valueOf(id));
    }

    /**
     * Find out the key associated with a given ID
     * 
     * @param id
     * @return unique key
     */
    public static synchronized String getKey(final Integer id) {
        return getEntry(id).key;
    }

    private static KeySplit ksLast = null;

    private static String sLastKey = "";

    /**
     * Get the distinct fields for a given key
     * 
     * @param sKey
     * @return the fields, or null if there was a problem parsing the key
     */
    public static synchronized KeySplit getKeySplit(final String sKey) {
        final IDEntry entry = getEntry(sKey);

        if (entry != null) {
            return entry.split;
        }

        try {
            return new KeySplit(sKey);
        } catch (Exception e) {
            // ignore format errors
        }

        return null;
    }

    /**
     * Get the individual fields that correspond to a given ID 
     * 
     * @param id
     * @return a KeySplit
     */
    public static synchronized KeySplit getKeySplit(final Integer id) {
        final IDEntry entry = getEntry(id);

        return entry != null ? entry.split : null;
    }

    /**
     * Get the individual fields that correspond to a given ID 
     * 
     * @param id
     * @return a KeySplit
     */
    public static KeySplit getKeySplit(final int id) {
        return getKeySplit(Integer.valueOf(id));
    }

    private static void split(final String sKey) {
        if (sKey == null) {
            ksLast = null;
            return;
        }

        if (sKey.equals(sLastKey)) {
            return;
        }

        ksLast = getKeySplit(sKey);

        sLastKey = ksLast == null ? "" : sKey;
    }

    /**
     * Split the key and get the Farm part
     * 
     * @param sKey
     * @return Farm name
     * @deprecated
     * @see #getKeySplit(String)
     */
    @Deprecated
    public static synchronized String getFarm(final String sKey) {
        split(sKey);
        return ksLast != null ? ksLast.FARM : null;
    }

    /**
     * Split the key and get the Cluster part
     * 
     * @param sKey
     * @return Cluster name
     * @deprecated
     * @see #getKeySplit(String)
     */
    @Deprecated
    public static synchronized String getCluster(final String sKey) {
        split(sKey);
        return ksLast != null ? ksLast.CLUSTER : null;
    }

    /**
     * Split the key and get the Node part
     * 
     * @param sKey
     * @return Node name
     * @deprecated
     * @see #getKeySplit(String)
     */
    @Deprecated
    public static synchronized String getNode(final String sKey) {
        split(sKey);
        return ksLast != null ? ksLast.NODE : null;
    }

    /**
     * Split the key and get the Parameter part
     * 
     * @param sKey
     * @return Parameter name
     * @deprecated
     * @see #getKeySplit(String)
     */
    @Deprecated
    public static synchronized String getFunction(final String sKey) {
        split(sKey);
        return ksLast != null ? ksLast.FUNCTION : null;
    }

    /**
     * Get a Vector with all IDs
     * 
     * @return all IDs
     */
    public static synchronized Vector<Integer> getAllIDs() {
        return new Vector<Integer>(hmReverseIDs.keySet());
    }

    /**
     * Remove one entry
     * 
     * @param sKey
     * @return true on success
     */
    public static synchronized boolean removeID(final String sKey) {
        if (sKey == null) {
            return false;
        }

        final IDEntry entry = getEntry(sKey);

        if (entry != null) {
            hmIDs.remove(sKey);
            hmReverseIDs.remove(Integer.valueOf(entry.id));

            if (!hasDB()) {
                return true;
            }

            final DB db = new DB();
            db.query("DELETE FROM monitor_ids WHERE mi_id=" + entry.id + ";");

            if (db.getUpdateCount() > 0) {
                iUpdateCount += db.getUpdateCount();
            } else if (db.getUpdateCount() < 0) {
                return false;
            }

            return true;
        }

        return false;
    }

    /**
     * Remove an entry based on its ID
     * 
     * @param id
     * @return true on success
     */
    public static synchronized boolean removeID(final Integer id) {
        return removeID(getKey(id));
    }

    /**
     * Get all the IDs for the series that match a given predicate
     * 
     * @param pred
     * @return Vector of IDs that match
     */
    public static synchronized Vector<Integer> getIDs(final monPredicate pred) {
        final String[] vsFunctions;

        if ((pred.parameters == null) || (pred.parameters.length <= 0)) {
            vsFunctions = new String[] { "" };
        } else {
            vsFunctions = pred.parameters;
        }

        final Vector<Integer> vRez = new Vector<Integer>();

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Predicate = " + pred.toString());
        }

        for (String vsFunction : vsFunctions) {
            String sFarm = pred.Farm != null ? pred.Farm : "";
            String sCluster = pred.Cluster != null ? pred.Cluster : "";
            String sNode = pred.Node != null ? pred.Node : "";

            String sFunction = vsFunction;

            if ((sFarm.length() > 0) && (sFarm.indexOf("*") < 0) && (sFarm.indexOf("%") < 0) && (sCluster.length() > 0)
                    && (sCluster.indexOf("*") < 0) && (sCluster.indexOf("%") < 0) && (sNode.length() > 0)
                    && (sNode.indexOf("*") < 0) && (sNode.indexOf("%") < 0) && (sFunction.length() > 0)
                    && (sFunction.indexOf("*") < 0) && (sFunction.indexOf("%") < 0)) {
                final String sKey = generateKey(sFarm, sCluster, sNode, sFunction);

                final IDEntry entry = getEntry(sKey);

                if (entry != null) {
                    final Integer id = Integer.valueOf(entry.id);

                    vRez.add(id);

                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "ID=" + id + " for non-wildcard predicate: " + sKey);
                    }
                } else {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "No ID for non-wildcard predicate: " + sKey);
                    }
                }
            } else {
                if (sFarm.length() <= 0) {
                    sFarm = "*";
                }
                sFarm = Formatare.replace(sFarm, "*", ".*");
                sFarm = Formatare.replace(sFarm, "%", ".*");
                if (sFarm.equals(".*")) {
                    sFarm = ".+";
                }

                if (sCluster.length() <= 0) {
                    sCluster = "*";
                }
                sCluster = Formatare.replace(sCluster, "*", ".*");
                sCluster = Formatare.replace(sCluster, "%", ".*");
                if (sCluster.equals(".*")) {
                    sCluster = ".+";
                }

                if (sNode.length() <= 0) {
                    sNode = "*";
                }
                sNode = Formatare.replace(sNode, "*", ".*");
                sNode = Formatare.replace(sNode, "%", ".*");
                if (sNode.equals(".*")) {
                    sNode = ".+";
                }

                if (sFunction.length() <= 0) {
                    sFunction = "*";
                }
                sFunction = Formatare.replace(sFunction, "*", ".*");
                sFunction = Formatare.replace(sFunction, "%", ".*");
                if (sFunction.equals(".*")) {
                    sFunction = ".+";
                }

                final String sID = generateKey(sFarm, sCluster, sNode, sFunction);

                if (sID == null) {
                    continue;
                }

                final Pattern p;

                try {
                    p = Pattern.compile("^" + sID + "$");
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Check your data query, I cannot compile the regexp pattern : " + sID);
                    continue;
                }

                String sLargestPart = "";

                String[] parts = sID.split("\\.(\\+|\\*)");

                for (String part : parts) {
                    if (part.length() > sLargestPart.length()) {
                        sLargestPart = part;
                    }
                }

                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "Regexp = " + sID + ", largest non-regexp part = " + sLargestPart);
                }

                final Iterator<Map.Entry<String, IDEntry>> it = hmIDs.entrySet().iterator();

                while (it.hasNext()) {
                    final Map.Entry<String, IDEntry> entry = it.next();

                    final String sKey = entry.getKey();

                    if ((sLargestPart.length() <= 1) || (sKey.indexOf(sLargestPart) >= 0)) { // try to avoid regexp matching if possible
                        final Matcher m = p.matcher(sKey);

                        if (m.matches()) {
                            final IDEntry identry = entry.getValue();

                            vRez.add(Integer.valueOf(identry.id));
                        }
                    }
                }
            }
        }

        return vRez;

    }

    private static int v4OldIDSize = 0;
    private static HashMap<String, TreeSet<Integer>> v4OldMap = null;

    /**
     * Build a Map<String, TreeSet<Integer>> with all IDs that belong to each V4 table.
     * 
     * @return IDs mapping
     */
    public static synchronized Map<String, TreeSet<Integer>> getV4TablesMapping() {

        // return the cached request if nothing has changed
        if ((hmIDs.size() == v4OldIDSize) && (v4OldMap != null)) {
            return v4OldMap;
        }

        final HashMap<String, TreeSet<Integer>> tm = new HashMap<String, TreeSet<Integer>>();

        for (Map.Entry<Integer, IDEntry> me : hmReverseIDs.entrySet()) {

            final Integer id = me.getKey();

            final IDEntry entry = me.getValue();

            final String f = Writer.nameTransform(entry.split.FUNCTION);

            if (f == null) {
                continue;
            }

            TreeSet<Integer> ts = tm.get(f);

            if (ts == null) {
                ts = new TreeSet<Integer>();
                tm.put(f, ts);
            }

            ts.add(id);
        }

        v4OldIDSize = hmIDs.size();
        v4OldMap = tm;

        return tm;
    }

    /**
     * Debug method
     * 
     * @param args
     */
    public static void main(String[] args) {
        KeySplit ks = new KeySplit("A/B/C/a/b/c/d/e/s");

        System.err.println(ks.FUNCTION);
    }

}
