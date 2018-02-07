package lia.util.importer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lia.Monitor.Store.Fast.IDGenerator;
import lia.Monitor.Store.Fast.Writer;

public class sync {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static AtomicInteger finishedTasks = new AtomicInteger(0);
    private static volatile int totalTasks = 0;
    
    private static final class DataSetDetails {

        private final String name;

        private final Integer sourceID;

        private final Integer destinationID;

        public DataSetDetails(String name, Integer sourceID, Integer destinationID) {
            super();
            this.name = name;
            this.sourceID = sourceID;
            this.destinationID = destinationID;
        }

        /*
         * (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("DataSetDetails [name=").append(name).append(", sourceID=").append(sourceID).append(", destinationID=").append(destinationID).append("]");
            return builder.toString();
        }
    }

    private static final String vsSourceTables[] = {
            "m_1y_20sec", "m_1y_200sec", "m_1y_90m"
    };

    private static final String vsDestTables[] = {
            "m_1y_20sec", "m_1y_200sec", "m_1y_90m"
    };

    private static final int viBaseTimes[] = {
            20, 200, 5400
    };

    // private static final int viSkipFactors[] = {3, , 1};

    static final HashMap<String, Integer> sIDMap = new HashMap<String, Integer>();

    static final HashMap<String, Integer> dIDMap = new HashMap<String, Integer>();

    static final long startTime = 1296284400;

    static final long endTime = 1296345600;

    public static void main(String args[]) throws Exception {
        DBS dbSource = new DBS();
        DBP dbDestination = new DBP();

        System.err.println("Getting source ids");
        dbSource.query("SELECT * FROM monitor_ids;");

        while (dbSource.moveNext()) {
            sIDMap.put(dbSource.gets("mi_key"), dbSource.geti("mi_id"));
        }

        System.err.println("Getting destination ids");
        dbDestination.query("SELECT * FROM monitor_ids;");

        while (dbDestination.moveNext()) {
            dIDMap.put(dbDestination.gets("mi_key"), dbDestination.geti("mi_id"));
        }

        System.err.println("There are: " + sIDMap.size() + " series in the source DB and " + dIDMap.size() + " series in the destination DB.");
        final List<DataSetDetails> seriesList = new ArrayList<DataSetDetails>(sIDMap.size());
        StringBuilder sbIgn = new StringBuilder();
        for(final Map.Entry<String, Integer> dEntry: dIDMap.entrySet()) {
            final String name = dEntry.getKey();
            final Integer dID = dEntry.getValue();
            if(name.indexOf("localhost") >=0 || name.indexOf("hermes3") >=0) {
                sbIgn.append("\n" + name);
                continue;
            }
//            if(name.indexOf("2607") < 0 ) {
//                continue;
//            }
//            if(name.indexOf("IN") < 0) {
//                continue;
//            }
            
            final Integer sID = sIDMap.get(name);
            if(sID == null) {
                System.err.println(" Unable to determine source ID for " + name + " / " + dID);
            } else {
                seriesList.add(new DataSetDetails(name, sID, dID));
                if(!sID.equals(dID)) {
                    System.err.println(" Different ids for (" + name + ") sourceID=" + sID + " destID=" + dID);
                }
            }
        }
        totalTasks = seriesList.size();
        System.err.println("There are: " + totalTasks + " IDs which will be synchronized");
        System.out.println("Ignored series: \n" + sbIgn.toString());
        final List<Callable<Object>> callables = new LinkedList<Callable<Object>>();
        for(final DataSetDetails dsd: seriesList)
            callables.add(Executors.callable(new WorkerTask(dsd)));
        
        executor.invokeAll(callables);
        
        System.exit(0);
    }

    private static final class WorkerTask implements Runnable {

        final DataSetDetails dataSet;

        public WorkerTask(DataSetDetails dataSet) {
            this.dataSet = dataSet;
        }

        public void run() {

            Connection conn = null;
            
            try {

                DBS dbs = new DBS();
                DBP dbp = new DBP();

                for(final String tablePrefix: vsSourceTables) {
                    DBP.DBConnection dbc = dbp.new DBConnection();
                    conn = dbc.getConnection();
                    conn.setAutoCommit(false);

                    final String sKey = dataSet.name;

                    int idp = dIDMap.get(sKey);
                    int ids = sIDMap.get(sKey);

                    final String fName = IDGenerator.getKeySplit(sKey).FUNCTION;
                    String sName = Writer.nameTransform(fName);

                    if (dbs.query("SELECT rectime,mval,mmin,mmax FROM " + tablePrefix + "_" + sName + " WHERE rectime > " + startTime + " AND rectime < " + endTime + " AND id=" + ids + " ORDER BY rectime ASC;")) {
                        final String sSql = "INSERT INTO " + tablePrefix + "_" + sName + " VALUES (?, ?, ?, ?, ?);";
                        System.out.println(" executing " + sSql);
                        PreparedStatement ps = conn.prepareStatement(sSql);
                        while (dbs.moveNext()) {
                            ps.setInt(1, dbs.geti(1));
                            ps.setInt(2, idp);
                            ps.setFloat(3, (float) dbs.getd(2));
                            ps.setFloat(4, (float) dbs.getd(3));
                            ps.setFloat(5, (float) dbs.getd(4));
                            ps.executeUpdate();
                        }
                        ps.close();
                    }
                    try {
                        conn.commit();
                        conn.close();
                    }catch(Throwable t) {
                        t.printStackTrace();
                        break;
                    }
                }
            } catch (Throwable e) {
                System.err.println(" Exception for (" + dataSet + ") Cause:");
                e.printStackTrace();
            } finally {
                if(conn != null) {
                    try {
                        if(!conn.isClosed()) {
                            conn.commit();
                            conn.close();
                        }
                    }catch(Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
            
            final int iFinishedTasks = finishedTasks.incrementAndGet();
            System.err.println("Worker for data: " + dataSet + " FINISHED! Total finishedTasks=" + iFinishedTasks + " / " + totalTasks);
        }
        
        
    }

}
