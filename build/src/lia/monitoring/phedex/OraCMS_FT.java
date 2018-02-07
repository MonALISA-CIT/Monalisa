package lia.monitoring.phedex;
//package cms;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.Date;
import java.util.Observable;
import java.util.Vector;

import lia.Monitor.Store.Sql.Pool.ConnectionPool;

public class OraCMS_FT extends Observable implements Runnable {

    private String jdbcURL;

    private String userName;

    private String passwd;

    private String tableName;

    private String[] sites;

    private String[] params;

    private Object syncConf;

    private long sleepTime;

    private boolean hasToRun;

    private Vector localStore;

    private boolean newOraConf;

    long lastTime;
    ConnectionPool connPool;

    public OraCMS_FT() {
        connPool = null;
        syncConf = new Object();
        localStore = new Vector();
        newOraConf = false;
        lastTime = 0;
        hasToRun = true;
    }

    public void setConf(String jdbcURL, String userName, String passwd, String tableName, String sites[],
            String[] params, long sleepTime) {
        synchronized (syncConf) {
            if (jdbcURL != null) {
                if (this.jdbcURL == null || !this.jdbcURL.equalsIgnoreCase(jdbcURL)) newOraConf = true;
                this.jdbcURL = jdbcURL;
            }

            if (userName != null) {
                if (this.userName == null || !this.userName.equals(userName)) newOraConf = true;
                this.userName = userName;
            }

            if (passwd != null) {
                if (this.passwd == null || !this.passwd.equals(passwd)) newOraConf = true;
                this.passwd = passwd;
            }

            this.tableName = tableName;
            this.sleepTime = sleepTime;

            if (sites != null && sites.length > 0) {
                this.sites = new String[sites.length];
                System.arraycopy(sites, 0, this.sites, 0, sites.length);
            } else {
                this.sites = null;
            }

            if (params != null && params.length > 0) {
                this.params = new String[params.length];
                System.arraycopy(params, 0, this.params, 0, params.length);
            } else {
                this.params = null;
            }
        }
    }

    private String getSQLQuery() {
        StringBuilder sb = new StringBuilder();

        synchronized (syncConf) {

            if (tableName == null || tableName.length() == 0) return null;
            if (params == null || params.length == 0) return null;

            sb.append("SELECT TIMESTAMP, NODE, ");
            for (int i = 0; i < params.length; i++) {
                sb.append(params[i]);
                if (i == params.length - 1) {
                    sb.append(" FROM ");
                    sb.append(tableName);
                } else {
                    sb.append(", ");
                }
            }

            if (sites == null || sites.length == 0) return sb.toString();

            sb.append(" WHERE NODE IN (");
            for (int j = 0; j < sites.length; j++) {
                sb.append("'");
                sb.append(sites[j]);
                sb.append("'");
                if (j == sites.length - 1) {
                    sb.append(")");
                } else {
                    sb.append(", ");
                }
            }
        }

        return sb.toString();
    }

    public void stopIT() {
        hasToRun = false;
    }

    private Vector getDBData(Connection conn, String sqlQuery) {
        Vector retV = new Vector();
        try {
            if (conn == null) return null;

            Statement stmt = conn.createStatement();

            ResultSet rs = stmt.executeQuery(sqlQuery);

            ResultSetMetaData rsm = rs.getMetaData();
            int columnCount = rsm.getColumnCount();

            while (rs.next()) {
                try {
                    FTOraRow fto = new FTOraRow();
                    lastTime = rs.getLong(1);
                    fto.time = lastTime* 1000;
                    fto.node = rs.getString(2);
                    for (int i = 3; i <= columnCount; i++) {
                        fto.values.put(rsm.getColumnName(i), Double.valueOf(rs.getDouble(i)));
                    }
                    retV.add(fto);
                } catch (Throwable t1) {
                    t1.printStackTrace();
                }
            }//while()
	    stmt.close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return retV;
    }

    private Connection getConnection() {
        Connection conn = null;
        try {
            if (newOraConf) {
                System.out.println("\n\n [ " + new Date() + " ] New Ora Conf ... ");
                if (connPool != null) {
                    connPool.closeAllConnections();
                }
                connPool = new ConnectionPool("oracle.jdbc.driver.OracleDriver", jdbcURL, userName, passwd, 1, 1, false);
            }
            conn = connPool.getConnection();
            newOraConf = false;
        } catch (Throwable t) {
            newOraConf = true;
            System.out.println("Got exception trying to (re)use a connection");
            t.printStackTrace();
            //almost no chance ...
            releaseConnection(conn);
            conn = null;
        }
        return conn;
    }

    private boolean checkNewData(Connection conn) {
	boolean retV = false;

        try {
            Statement st = conn.createStatement();
            if (tableName == null) return false;
            ResultSet rs = st.executeQuery("SELECT MAX(UPDATE_STAMP) FROM " + tableName);
            if(rs.next()) {
                if(rs.getLong(1) > lastTime) retV = true;
            } 
	    st.close();
        }catch(Throwable t){
            t.printStackTrace();
	    retV = false;
            newOraConf = true;
        }
        return retV;
    }
    
    private void releaseConnection(Connection conn) {
        if (conn == null) return;
        try {
            connPool.free(conn);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void run() {

        while (hasToRun) {
            try {
                Thread.sleep(sleepTime);
            } catch (Exception e) {

            }
            
            long sTime = System.currentTimeMillis();
            Connection conn = null;

            try {
                String sqlQuery = getSQLQuery();
                if (sqlQuery == null) continue;

                conn = getConnection();
                if (conn == null) continue;
                if(checkNewData(conn)) {
                    System.out.println("New data in db ... ");
                    localStore.addAll(getDBData(conn, sqlQuery));
                    setChanged();
                    notifyObservers();
                }
            } catch (Throwable t2) {
                newOraConf = true;
                t2.printStackTrace();
            } finally {
                releaseConnection(conn);
                conn = null;
            }
            
            System.out.println(" [ " + new Date() + " ]  Dt = " + (System.currentTimeMillis() - sTime));
        }
    }

    public Vector getData() {
        Vector retV = null;

        synchronized (localStore) {
            if (localStore.size() != 0) {
                retV = new Vector(localStore);
            }
            localStore.clear();
        }

        return retV;

    }

    public static void main(String[] args) {
        OraCMS_FT oft = new OraCMS_FT();
        oft.setConf("jdbc:oracle:oci8:@cms", "cms_transfermgmt_reader", "slightlyjaundiced",
                "t_info_transfer_status", null, new String[] { "N_FILES", "SZ_FILES"}, 60 * 1000);
        new Thread(oft, "( ML ) OraCMS_FT Thread").start();

        for (;;) {
            try {
                try {
                    Thread.sleep(30 * 1000);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                Vector v = oft.getData();

                if (v == null) {
                    System.out.println("Got null Vector");
                    continue;
                }
                
                
                if (v.size() == 0) {
                    System.out.println("Got 0 size() Vector");
                    continue;
                }

                for (int i = 0; i < v.size(); i++) {
                    System.out.println("[ " + i + " ] = " + v.elementAt(i).toString());
                }

                if ( oft.connPool != null ) {
                    System.out.println("\n\n ConnPool Stat : " + oft.connPool.toString() + "\n\n");
                }

            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
