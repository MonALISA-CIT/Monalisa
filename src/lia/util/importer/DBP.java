package lia.util.importer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DBP {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(DBP.class.getName());

    private final Statement batchStatement = null;
    private String sCurrentSQLQuery = "";
    private boolean first = true;
    private boolean isUpdate = false;
    protected ResultSet rsRezultat = null;
    public int iCurrentPos = 0;
    private final String driverString = "org.postgresql.Driver";
    private DBConnection dbc;
    static Map mConnections;

    static {
        mConnections = new HashMap();
    };

    public DBP() {
        dbc = null;
    }

    public DBP(String s) {
        this();
        query(s);
    }

    public static String queryLock = new String("queryLock");

    public class DBConnection {
        private Connection connex = null;

        public int iBusy = 2; // not connected yet

        private String lock = null;

        private long lastAccess = 0;

        public DBConnection() {
            lock = new String("lock");
            lastAccess = System.currentTimeMillis();
            connect();
        }

        public boolean connect() {
            return connectPostgreSQL();
        }

        public boolean connectPostgreSQL() {
            connex = null;

            String dbURL = "jdbc:postgresql://hermes3.uslhcnet.org:5544/mon_data";

            String userName = "mon_user";
            String password = "mon_pass";

            if (password == null) {
                password = "";
            }

            try {
                Class.forName(driverString);

                java.util.Properties info = new java.util.Properties();
                info.put("user", userName);
                info.put("password", password);
                info.put("charSet", "utf-8");
                connex = DriverManager.getConnection(dbURL, info);

                iBusy = 0;
                return true;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "DB : exception : " + e + " (" + e.getMessage() + ") FOR : " + dbURL + " ("
                        + userName + "/" + password + ")");
                iBusy = 3;
                return false;
            }

        }

        public Connection getConnection() {
            return connex;
        }

        public boolean use() {
            if (iBusy == 0) {
                iBusy = 1;
                lastAccess = System.currentTimeMillis();
                return true;
            } else {
                return false;
            }
        }

        public boolean free() {
            if (iBusy == 1) {
                iBusy = 0;
                return true;
            } else {
                return false;
            }
        }

        public void close() {
            try {
                connex.close();
            } catch (Exception e) {
            }

            iBusy = 10;
        }

    }

    static String connect_sync;
    static CleanupThread tCleanup = null;

    public Connection getConnection() {
        if (!connect()) {
            System.err.println("DB.getConnection() : cannot connect");
            return null;
        }

        if (dbc != null) {
            //System.err.println("DB.getConnection() : i return a good connection");
            return dbc.getConnection();
        } else {
            System.err.println("DB.getConnection() : cannot use");
            return null;
        }
    }

    public void closeConnection() {
        if (dbc != null) {
            dbc.free();
        }
    }

    static {
        connect_sync = new String("connect_sync");
        startThread();
    }

    static public synchronized void startThread() {
        if (tCleanup == null) {
            tCleanup = new CleanupThread();
            tCleanup.start();
        }
    }

    static public synchronized void stopThread() {
        if (tCleanup != null) {
            try {
                tCleanup.stopThread();
            } catch (Exception e) {
            }

            tCleanup = null;
        }
    }

    static private class CleanupThread extends Thread {

        public CleanupThread() {
            super("(ML) CleanUpThread");
            try {
                setDaemon(true);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Cannot setDaemon", t);
            }
        }

        private boolean shouldStop = false;

        @Override
        public void run() {
            while (!shouldStop) {
                synchronized (connect_sync) {
                    Iterator it = mConnections.keySet().iterator();

                    while (it.hasNext()) {
                        String sConn = (String) it.next();

                        List l = (List) mConnections.get(sConn);

                        if (l != null) {
                            boolean finish = true;

                            do {
                                finish = true;

                                Iterator it2 = l.iterator();

                                int iPos = 0;

                                while (it2.hasNext()) {
                                    DBConnection dbc = (DBConnection) it2.next();

                                    if ((dbc.iBusy > 2)
                                            || ((System.currentTimeMillis() - dbc.lastAccess) > (1000 * 60 * 2)) // max. 2 min of running or of innactivity
                                    ) {
                                        l.remove(iPos);
                                        finish = false;
                                        break;
                                    }
                                    iPos++;
                                }
                            } while (!finish);
                        }
                    }
                }

                System.gc();

                try {
                    sleep(1000 * 59); // every 59 sec.
                } catch (Exception e) {
                }
            }
        }

        public void stopThread() {
            shouldStop = true;
        }
    }

    private boolean connect() {
        dbc = null;

        synchronized (connect_sync) {
            List l = (List) mConnections.get("monalisa_databases");

            if (l != null) {
                Iterator it = l.iterator();

                while (it.hasNext()) {
                    DBConnection temp = (DBConnection) it.next();

                    if (temp.use()) {
                        dbc = temp;
                        break;
                    }
                }
            } else {
                l = new LinkedList();
                mConnections.put("monalisa_databases", l);
            }

            if (dbc == null) {
                dbc = new DBConnection();
                if (dbc.use()) {
                    l.add(dbc);
                } else {
                    logger.log(Level.SEVERE, "DB : cannot use the new dest connection ?!");
                    return false;
                }
            }
        }

        return true;
    }

    public boolean update(String sSQLQuery) {
        return update(sSQLQuery, false);
    }

    public boolean update(String sSQLQuery, boolean bIgnoreErrors) {
        return doQuery(sSQLQuery, true, bIgnoreErrors);
    }

    public boolean query(String sSQLQuery) {
        return query(sSQLQuery, false);
    }

    public boolean query(String sSQLQuery, boolean bIgnoreErrors) {
        return doQuery(sSQLQuery, false, bIgnoreErrors);
    }

    private boolean doQuery(String sSQLQuery, boolean bIsUpdate, boolean bIgnoreErrors) {
        if (driverString.indexOf("mckoi") >= 0) {
            synchronized (queryLock) {
                return doQuerySync(sSQLQuery, bIsUpdate, bIgnoreErrors);
            }
        } else {
            return doQuerySync(sSQLQuery, bIsUpdate, bIgnoreErrors);
        }
    }

    private boolean doQuerySync(String sSQLQuery, boolean bIsUpdate, boolean bIgnoreErrors) {
        rsRezultat = null;
        sCurrentSQLQuery = "";

        isUpdate = bIsUpdate;

        if (!connect()) {
            return false;
        }

        try {
            Statement stat = dbc.getConnection().createStatement();

            if (!isUpdate) {
                rsRezultat = stat.executeQuery(sSQLQuery);
            } else {
                stat.executeUpdate(sSQLQuery);
            }

            sCurrentSQLQuery = new String(sSQLQuery);

            if (!isUpdate) {
                first = true;
                try {
                    if (!rsRezultat.next()) {
                        first = false;
                    }
                } catch (Exception e) {
                    first = false;
                }
            } else {
                first = false;
            }

            dbc.free();
        } catch (Exception e) {
            rsRezultat = null;
            first = false;

            if (!bIgnoreErrors) {
                dbc.close(); // if it's an ignorred error then it's expected to crash, so don't assume a connection problem

                String s = e + " (" + e.getMessage() + ")";

                if (s.indexOf("Cannot insert a duplicate key") < 0) { // ignoram asta
                    logger.log(Level.INFO, "DB.query : error at '" + sSQLQuery + "' with the message : IGNORING", e);
                }
            } else {
                dbc.free();
            }

            return false;
        }

        iCurrentPos = 0;
        return true;
    }

    public boolean moveNext() {
        if (isUpdate) {
            return false;
        }

        if (first) {
            first = false;
            return true;
        }

        if (rsRezultat != null) {
            try {
                if (!rsRezultat.next()) {
                    return false;
                }
                iCurrentPos++;
                return true;
            } catch (Exception e) {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean requery() {
        if ((rsRezultat != null) && (sCurrentSQLQuery.length() > 0)) {
            return query(sCurrentSQLQuery, isUpdate);
        } else {
            return false;
        }
    }

    public String getns(String sColumnName) {
        try {
            return rsRezultat.getString(sColumnName);
        } catch (Exception e) {
            return null;
        }
    }

    public String gets(String sColumnName) {
        return gets(sColumnName, "");
    }

    public String gets(int iColCount) {
        return gets(iColCount, "");
    }

    public String gets(String sColumnName, String sDefault) {
        if ((dbc == null) || (rsRezultat == null)) {
            return sDefault;
        }

        try {
            String sTemp = rsRezultat.getString(sColumnName);
            if (sTemp == null) {
                sTemp = sDefault;
            }
            return sTemp.trim();
        } catch (Exception e) {
            return sDefault;
        }
    }

    public String gets(int iColCount, String sDefault) {
        if ((dbc == null) || (rsRezultat == null)) {
            return sDefault;
        }

        try {
            String sTemp = rsRezultat.getString(iColCount);
            if (sTemp == null) {
                sTemp = sDefault;
            }
            return sTemp.trim();
        } catch (Exception e) {
            return sDefault;
        }
    }

    public byte[] getBytes(String sColumnName) {
        if ((dbc == null) || (rsRezultat == null)) {
            return null;
        }

        try {
            byte[] retv = rsRezultat.getBytes(sColumnName);
            return retv;
        } catch (Exception e) {
        }

        return null;
    }

    public int geti(String sColumnName) {
        return geti(sColumnName, 0);
    }

    public int getl(int colIndex) {
        return (colIndex);
    }

    public int geti(String sColumnName, int iDefault) {
        if ((dbc == null) || (rsRezultat == null)) {
            return iDefault;
        }
        try {
            int iTemp = rsRezultat.getInt(sColumnName);

            if (rsRezultat.wasNull()) {
                return iDefault;
            }
            return iTemp;
        } catch (Exception e) {
            return iDefault;
        }
    }

    public long getl(int colIndex, long lDefault) {
        if ((dbc == null) || (rsRezultat == null)) {
            return lDefault;
        }

        try {
            long lTemp = rsRezultat.getLong(colIndex);

            if (rsRezultat.wasNull()) {
                return lDefault;
            } else {
                return lTemp;
            }
        } catch (Exception e) {
            return lDefault;
        }
    }

    public long getl(String sColumnName) {
        return getl(sColumnName, 0);
    }

    public long getl(String sColumnName, long lDefault) {
        if ((dbc == null) || (rsRezultat == null)) {
            return lDefault;
        }

        try {
            long lTemp = rsRezultat.getLong(sColumnName);

            if (rsRezultat.wasNull()) {
                return lDefault;
            } else {
                return lTemp;
            }
        } catch (Exception e) {
            return lDefault;
        }
    }

    public double getd(String sColumnName) {
        return getd(sColumnName, 0);
    }

    public double getd(String sColumnName, double dDefault) {
        if ((dbc == null) || (rsRezultat == null)) {
            return dDefault;
        }

        try {
            double dTemp = rsRezultat.getDouble(sColumnName);

            if (rsRezultat.wasNull()) {
                return dDefault;
            } else {
                return dTemp;
            }
        } catch (Exception e) {
            return dDefault;
        }
    }
}
