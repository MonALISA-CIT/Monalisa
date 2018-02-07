package lia.Monitor.Store.Sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.DataCache.DataSelect;
import lia.Monitor.Store.AbstractPersistentStore;
import lia.Monitor.Store.StoreException;
import lia.Monitor.Store.Sql.Pool.ConnectionPool;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.ntp.NTPDate;

public class MysqlStore extends AbstractPersistentStore {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(MysqlStore.class.getName());

    private static ConnectionPool connPool;

    private static String _theDriver = null;
    private static String jdbcURLString = null;
    private static final String maktab1 = "CREATE TABLE ";
    private static final String maktab2 = " (" + " rectime BIGINT," + " mfarm VARCHAR(15)," + " mcluster VARCHAR(20),"
            + " mnode VARCHAR(40)," + " mfunction VARCHAR(20)," + " mval DOUBLE PRECISION" + ") TYPE=InnoDB";

    private static final String maktabConf = "CREATE TABLE monitor_conf (conftime BIGINT, conf BINARY)";
    private static final String insertIntoConf = "INSERT INTO monitor_conf (conftime, conf) VALUES(?,?)";
    private static final String selectFromConf = "SELECT conf FROM monitor_conf WHERE conftime > ? AND conftime < ?";

    private static final String makIdxRectime1 = "CREATE INDEX rectimes";
    private static final String makIdxRectime2 = "(rectime)";
    private static final String makIdxFCNFunc1 = "CREATE INDEX farm_cluster_node_mfunction";
    private static final String makIdxFCNFunc2 = " (mfarm, mcluster, mnode, mfunction)";
    private static final String makIdxFuncNCF1 = "CREATE INDEX mfunction_cluster_node_farm";
    private static final String makIdxFuncNCF2 = " (mfunction, mnode, mcluster, mfarm)";

    private static final String dropIdxRectime = "DROP INDEX rectimes";
    private static final String dropIdxFCNFunc = "DROP INDEX farm_cluster_node_mfunction";
    private static final String dropIdxFuncNCF = "DROP INDEX mfunction_cluster_node_farm";

    private static final String insertIntoSql1 = "INSERT INTO ";
    private static final String insertIntoSql2 = " (rectime, mfarm, mcluster, mnode, mfunction, mval) VALUES (?,?,?,?,?,? )";

    //   private boolean debug ;

    //   private MysqlConnectionPoolDataSource mscp = new MysqlConnectionPoolDataSource();

    public MysqlStore(String[] consParams) throws StoreException {
        super(consParams);

        //   debug = Boolean.valueOf(AppConfig.getProperty("lia.Monitor.debug","false")).booleanValue();
        boolean okToContinue = true;

        Connection c = null;
        Statement s = null;

        try {
            String dbURL = "jdbc:mysql://" + AppConfig.getProperty("lia.Monitor.ServerName", "localhost") + ":"
                    + AppConfig.getProperty("lia.Monitor.DatabasePort", "3306") + "/"
                    + AppConfig.getProperty("lia.Monitor.DatabaseName", "mon_data");
            String userName = AppConfig.getProperty("lia.Monitor.UserName", "mon_user");
            String password = AppConfig.getProperty("lia.Monitor.Pass", "mon_pass");
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " dbURL = " + dbURL);
            }
            connPool = new ConnectionPool("com.mysql.jdbc.Driver", dbURL, userName, password, 5, 10, true);
            c = connPool.getConnection();
            s = c.createStatement();

            for (int indent = 0; (indent < consParams.length) && okToContinue; indent++) {
                try {
                    s.executeUpdate(maktab1 + consParams[indent] + maktab2);
                    s.executeUpdate(makIdxRectime1 + indent + " ON " + consParams[indent] + makIdxRectime2);
                    s.executeUpdate(makIdxFCNFunc1 + indent + " ON " + consParams[indent] + makIdxFCNFunc2);
                    s.executeUpdate(makIdxFuncNCF1 + indent + " ON " + consParams[indent] + makIdxFuncNCF2);
                } catch (SQLException ex) {
                    //TODO - the '1050' from ex.getErrorCode() means already exists!?!...or should I parse for description??!?
                    if ((ex.getErrorCode() != 1050)
                            && ((ex.getMessage() != null) && (ex.getMessage().indexOf("already") == -1))) {
                        okToContinue = false;
                        //notify this 
                        logger.log(
                                Level.WARNING,
                                "Failed to create table " + consParams[indent] + "! Exception ErrorCode: "
                                        + ex.getErrorCode() + "Exception Description " + ex.getMessage(), ex);
                    } else {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "EX ErrorCode: " + ex.getErrorCode() + " EX Message" + ex);
                            logger.log(Level.FINE, "\nThe table " + consParams[indent]
                                    + " already exists in the DB! Ok To Continue!\n");
                        }
                    }
                }
            }
        } catch (Throwable exc) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "General Exception", exc);
            }
            throw new StoreException(exc);
        } finally {
            try {
                if (s != null) {
                    s.close();
                }
            } catch (Throwable t) {
            }
            if (c != null) {
                connPool.free(c);
            }
        }

        if (!okToContinue) {
            throw new StoreException("Cannot instantiate MysqlStore -> okToContinue " + okToContinue);
        } else {
            logger.log(Level.INFO, "=======>MySqlStore instantiated!");
        }
    }

    @Override
    public Vector getResults(monPredicate p) {
        Vector res = new Vector();
        for (int indent = consParams.length - 1; indent >= 0; indent--) {
            Vector vToAdd = getResults(p, indent);
            if (vToAdd.size() > 0) {
                res.addAll(vToAdd);
            }
        }
        return res;
    }

    @Override
    public Vector getResults(monPredicate p, int indent) {
        long t1 = NTPDate.currentTimeMillis();
        Vector results = new Vector();
        String query = DataSelect.rquery(p, consParams[indent]);

        Connection c = null;
        Statement s = null;

        try {
            c = connPool.getConnection();
            s = c.createStatement();

            s.execute(query);
            ResultSet rs = s.getResultSet();

            while (rs.next()) {
                Result rez = new Result();
                rez.time = rs.getLong(1);
                rez.FarmName = rs.getString(2);
                rez.ClusterName = rs.getString(3);
                rez.NodeName = rs.getString(4);
                rez.addSet(rs.getString(5), rs.getDouble(6));

                results.add(rez);
            }

        } catch (Throwable t) {
            logger.log(Level.WARNING, " Failed to execute the query ! ", t);
        } finally {
            try {
                if (s != null) {
                    s.close();
                }
            } catch (Throwable t) {
            }

            if (c != null) {
                connPool.free(c);
            }
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(
                    Level.FINER,
                    " Selected results  on DB" + indent + ": " + results.size() + "    Dt="
                            + (NTPDate.currentTimeMillis() - t1));
        }

        return results;

    }

    @Override
    public void makePersistent(Result[] results) {
        makePersistent(results, 0);
    }

    @Override
    public void makePersistent(eResult[] results) {
        //	makePersistent(results, 0);
    }

    public void makePersistent(Result[] results, int indent) {

        if ((results == null) || (results.length == 0)) {
            return;
        }
        long t1 = NTPDate.currentTimeMillis();

        Connection c = null;
        PreparedStatement ps = null;

        try {

            c = connPool.getConnection();
            ps = c.prepareStatement(insertIntoSql1 + consParams[indent] + insertIntoSql2);

            for (Result r1 : results) {
                if (r1.param != null) {
                    for (int j = 0; j < r1.param.length; j++) {
                        ps.setLong(1, r1.time);
                        ps.setString(2, r1.FarmName);
                        ps.setString(3, r1.ClusterName);
                        ps.setString(4, r1.NodeName);
                        if (r1.param_name[j] != null) {
                            ps.setString(5, r1.param_name[j].trim());
                            ps.setFloat(6, (float) r1.param[j]);
                            ps.execute();
                        }
                    }
                }
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Store ---- >  SAVE  DB" + indent + " " + results.length + " values Dt="
                        + (NTPDate.currentTimeMillis() - t1));
            }

        } catch (Throwable t) {
            logger.log(Level.SEVERE, " Failed to save data ", t);
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }
            } catch (Throwable t) {
            }
            if (c != null) {
                connPool.free(c);
            }
        }

    }

    private class InternalDeleteResult {
        public long rectime;
        public double mval;

        public InternalDeleteResult(long rt, double val) {
            rectime = rt;
            mval = val;
        }

        public InternalDeleteResult(double val, long rt) {
            rectime = rt;
            mval = val;
        }
    }

    @Override
    public synchronized void deleteOld(long time, int indent, long avgTime) {

        String cmdSelDel = "SELECT * FROM " + consParams[indent] + " WHERE rectime < " + time;
        String cmdDel = "DELETE FROM " + consParams[indent] + " WHERE rectime < " + time;

        Vector vals = null;
        Hashtable resToComp = new Hashtable();
        Vector resultsToSave = new Vector();

        long srt = 0;
        long lrt = 0;
        double mmval = 0;
        double minval = Double.MAX_VALUE;
        double maxval = Double.MIN_VALUE;

        int deletedRows = 0, count = 0, k = 0;
        long sTime = NTPDate.currentTimeMillis();
        Connection c = null;
        Statement s = null;
        try {

            c = connPool.getConnection();
            s = c.createStatement();

        } catch (Throwable t) {
            logger.log(Level.SEVERE, " Failed to delete OLD data ", t);
            return;
        }

        try {

            ResultSet rs = s.executeQuery(cmdSelDel);

            while (rs.next()) {
                String key = rs.getString(2) + "%^$&:" + rs.getString(3) + "%^$&:" + rs.getString(4) + "%^$&:"
                        + rs.getString(5);
                if (resToComp.containsKey(key)) {
                    vals = (Vector) resToComp.get(key);
                } else {
                    vals = new Vector();
                    vals.addElement(rs.getString(2));
                    vals.addElement(rs.getString(3));
                    vals.addElement(rs.getString(4));
                    vals.addElement(rs.getString(5));
                }
                vals.addElement(new InternalDeleteResult(rs.getLong(1), rs.getDouble(6)));
                resToComp.put(key, vals);
            }//rs.next()

            rs.close();
            s.close();

            if (resToComp.size() > 0) {//add to DB[indent + 1]
                k = 0;

                for (Enumeration en = resToComp.keys(); en.hasMoreElements();) {
                    vals = (Vector) resToComp.get(en.nextElement());
                    Result rez = new Result();
                    rez.FarmName = (String) vals.elementAt(0);
                    rez.ClusterName = (String) vals.elementAt(1);
                    rez.NodeName = (String) vals.elementAt(2);
                    String mf = (String) vals.elementAt(3);

                    long leftTime = ((InternalDeleteResult) vals.elementAt(4)).rectime;
                    count = 1;
                    srt = leftTime;
                    mmval = ((InternalDeleteResult) vals.elementAt(4)).mval;

                    minval = mmval;
                    maxval = mmval;

                    for (int i = 4; i < vals.size(); i++) {
                        InternalDeleteResult idr = (InternalDeleteResult) vals.elementAt(i);

                        if (idr.rectime > (leftTime + avgTime)) {

                            for (; idr.rectime > (leftTime + avgTime); leftTime += avgTime) {
                                ;
                            }

                            rez.time = (srt + lrt) / 2;
                            rez.addSet(mf, mmval / count);
                            if (minval != maxval) {
                                rez.addSet(mf, minval);
                                rez.addSet(mf, maxval);
                            }
                            resultsToSave.addElement(rez);

                            rez = new Result();
                            rez.FarmName = (String) vals.elementAt(0);
                            rez.ClusterName = (String) vals.elementAt(1);
                            rez.NodeName = (String) vals.elementAt(2);
                            mf = (String) vals.elementAt(3);
                            count = 1;
                            srt = idr.rectime;
                            mmval = idr.mval;
                            minval = mmval;
                            maxval = mmval;

                        } else {
                            count++;
                            lrt = idr.rectime;
                            mmval += idr.mval;
                            if (idr.mval > maxval) {
                                maxval = idr.mval;
                            } else if (idr.mval < minval) {
                                minval = idr.mval;
                            }
                        }

                    }//for()

                    rez.addSet(mf, mmval / count);
                    rez.time = (srt + lrt) / 2;
                    resultsToSave.addElement(rez);

                    deletedRows += vals.size() - 4;
                }//Enumeration

                makePersistent((Result[]) resultsToSave.toArray(new Result[resultsToSave.size()]), indent + 1);

                s = c.createStatement();
                s.executeUpdate(cmdDel);
            }

            if (logger.isLoggable(Level.FINER) && (resultsToSave.size() > 0)) {
                logger.log(Level.FINER, " Make Persistent to DB" + (indent + 1) + " : " + resultsToSave.size()
                        + " values");
            }

        } catch (Throwable t) {
            logger.log(Level.SEVERE, " Failed to delete OLD data ", t);
        } finally {
            try {
                if (s != null) {
                    s.close();
                }
            } catch (Throwable t) {
                ;
            }

            if (c != null) {
                connPool.free(c);
            }
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER,
                    " DELETED: [ " + deletedRows + " ] on DB" + indent + " [ " + (NTPDate.currentTimeMillis() - sTime)
                            + " ] ms ");
        }
    }

    /**
     * Nothing to be done for this case....
     */
    @Override
    public void updateConfig(MFarm farm) {

    }

    //TODO - WS
    @Override
    public java.util.Vector getConfig(long fromTime, long toTime) {
        return null;
    }

    @Override
    public synchronized void close() {
        logger.log(Level.INFO, " MySqlStore Entering close ! ");
        connPool.closeAllConnections();
        logger.log(Level.INFO, " MySqlStore exits close ! ");
    }

    @Override
    public java.util.Vector getConfig(String farmName, long fromTime, long toTime) throws StoreException {
        return null;
    }

    @Override
    public java.lang.Object getConfig(String farmName) throws StoreException {
        return null;
    }

    public void reload() {
        // to do or not to do ?
    }

}
