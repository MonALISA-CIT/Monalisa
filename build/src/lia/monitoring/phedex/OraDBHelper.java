package lia.monitoring.phedex;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Date;
import java.util.HashMap;
import java.util.Vector;

public final class OraDBHelper {

    public static final HashMap[] getValues(Connection conn, String tableName, String[] fields) throws Exception {
        return getValues(conn, tableName, fields, null);
    }

    public static final HashMap[] getValues(Connection conn, String tableName, String[] fields, String sqlConstraint) throws Exception {
        if(conn == null) {
            throw new Exception("Cannot use connection because is null");
        }

        long sTime = System.currentTimeMillis();
        String SQLQuery = getSQLQuery(tableName, fields, sqlConstraint);

        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(SQLQuery);
        Vector v = new Vector();
        
        while(rs.next()) {
            HashMap hm = new HashMap();
            for(int i=0; i<fields.length; i++){
                hm.put(fields[i], rs.getObject(fields[i]));
            }
            if(hm.size() > 0) {
                v.add(hm);
            }
        }
        
        rs.close();
        stmt.close();

        System.out.println(new Date() + " Executing ... " + SQLQuery + " .... took " + (System.currentTimeMillis() - sTime) + " ms");
        
        if(v.size() == 0) return null;
        
        return (HashMap[])v.toArray(new HashMap[v.size()]);
        
    }
    
    private static final String getSQLQuery(String tableName, String[] fields, String sqlConstraint) throws Exception {
        if(tableName == null) {
            throw new Exception("Table name cannot be null");
        }
        
        if(fields == null) {
            throw new Exception("fields cannot be null");
        }
        
        if(tableName.length() == 0) {
            throw new Exception("Table name has to be a not 0 length String");
        }

        if(fields.length < 1) {
            throw new Exception("There must be at least one field specified");
        }
        
        StringBuilder sb = new StringBuilder(512);
        sb.append("SELECT ");
        
        for(int i=0; i<fields.length; i++) {
            sb.append(fields[i]);
            if(i == fields.length - 1) {
                sb.append(" ");
                break;
            } 
            sb.append(", ");
        }
        
        sb.append("FROM ").append(tableName);
        
        if(sqlConstraint != null) {
            sqlConstraint = sqlConstraint.trim();
            if(sqlConstraint.length() > 0) {
                sb.append(" ").append(sqlConstraint);
            }
        }
        return sb.toString();
    }
    
    private static final String getSQLQuery(String tableName, String[] fields) throws Exception {
        return getSQLQuery(tableName, fields, null);
    }
}
