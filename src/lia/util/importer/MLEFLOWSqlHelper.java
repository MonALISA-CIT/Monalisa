package lia.util.importer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import lia.Monitor.Store.Fast.Writer;
import lia.util.Utils;

/**
 * @author ramiro
 */
public class MLEFLOWSqlHelper {

    private static final String MLEFLOW_FILE_PATH = "/home/ramiro/MLEFLOW.tmp";
    static final HashMap<String, Integer> sIDMap = new HashMap<String, Integer>();

    private static final DBS dbConnection = new DBS();

    //TODO - make a list out of this !
    private static final long HOLE_TIMESTAMP = 1306720760L;
    
    private static final class SDTables {
        
        private final String eflowName;
        private final String revEflowName;
        
        private final Integer srcINID;
        private final Integer srcOUTID;
        private final Integer dstINID;
        private final Integer dstOUTID;
        
        private final String[] srcTableNames;
        private final String[] dstTableNames;
        
        private final long holeStartTime;
        private final long holeEndTime;

        
        /**
         * 
         * @param eflowName
         * @throws IllegalArgumentException if the {@code eflowName} does not contain a '-'
         * @throws NullPointerException if eflowName is null
         */
        private SDTables(String eflowName) {
            
            if(eflowName == null) {
                throw new NullPointerException("Null eflowName");
            }
            
            final int idx = eflowName.indexOf('-');
            if(idx <= 0) {
                throw new IllegalArgumentException("Wrong MLEFLOW name; no - found: " + eflowName);
            }
        
            this.eflowName = eflowName;
            //
            //eflow name is smth like CHI_CERN_Abilene_IPv6_180-GVA_CERN_Abilene_IPv6_180
            //
            
            final String srcFlow = eflowName.substring(0, idx);
            final String dstFlow = eflowName.substring(idx+1);
            this.revEflowName = dstFlow + "-" + srcFlow;
            
            final String srcTableName = Writer.nameTransform(eflowName);
            final String dstTableName = Writer.nameTransform(revEflowName);
            final int len = vsDestTables.length;
            this.srcTableNames = new String[len];
            this.dstTableNames = new String[len];
            
            for(int i=0; i<len; i++) {
                srcTableNames[i] = vsDestTables[i] + "_" + srcTableName;
                dstTableNames[i] = vsDestTables[i] + "_" + dstTableName;
            }
            
            this.srcINID = sIDMap.get("_TOTALS_/MLEFLOWS/_INDIVIDUAL_/"+ eflowName + "_IN");
            this.srcOUTID = sIDMap.get("_TOTALS_/MLEFLOWS/_INDIVIDUAL_/"+ eflowName + "_OUT");
            this.dstINID = sIDMap.get("_TOTALS_/MLEFLOWS/_INDIVIDUAL_/"+ revEflowName + "_IN");
            this.dstOUTID = sIDMap.get("_TOTALS_/MLEFLOWS/_INDIVIDUAL_/"+ revEflowName + "_OUT");
            
            final String sminQuery = "select min(rectime) from " + srcTableNames[0] + " where rectime > " + HOLE_TIMESTAMP + " and id = " + srcOUTID + ";";
            dbConnection.query(sminQuery);
            
            if (dbConnection.moveNext()) {
                holeEndTime = dbConnection.geti(1);
            } else {
                holeEndTime = 0L;
                System.err.println(" Unable to determine the starting timestamp for missing data for: " + eflowName);
            }
            
            final String smaxQuery = "select max(rectime) from " + srcTableNames[0] + " where rectime < " + HOLE_TIMESTAMP + " and id = " + srcOUTID + ";";
            dbConnection.query(smaxQuery);
            
            if (dbConnection.moveNext()) {
                holeStartTime = dbConnection.geti(1);
            } else {
                holeStartTime = 0L;
                System.err.println(" Unable to determine the starting timestamp for missing data for: " + eflowName);
            }
            
            
        }


        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("SDTables [eflowName=")
                   .append(eflowName)
                   .append(", revEflowName=")
                   .append(revEflowName)
                   .append(", srcINID=")
                   .append(srcINID)
                   .append(", srcOUTID=")
                   .append(srcOUTID)
                   .append(", dstINID=")
                   .append(dstINID)
                   .append(", dstOUTID=")
                   .append(dstOUTID)
                   .append(", srcTableNames=")
                   .append(Arrays.toString(srcTableNames))
                   .append(", dstTableNames=")
                   .append(Arrays.toString(dstTableNames))
                   .append(", holeStartTime=")
                   .append(holeStartTime)
                   .append("/")
                   .append(new Date(holeStartTime * 1000L))
                   .append(", holeEndTime=")
                   .append(holeEndTime)
                   .append("/")
                   .append(new Date(holeEndTime * 1000L))
                   .append("]");
            return builder.toString();
        }

        
    }
    
    private final static List<String> eflowNames = new LinkedList<String>();

    //K - MLEFLOW name; V dst table names
    private final static Map<String, SDTables> tablesMap = new HashMap<String, SDTables>();

    private static final String vsDestTables[] = {
            "m_1y_20sec", "m_1y_200sec", "m_1y_90m"
    };

    private static final void loadEflowNames() throws IOException {
        BufferedReader br = null;
        FileReader fr = null;

        final Properties p = new Properties();

        try {
            fr = new FileReader(MLEFLOW_FILE_PATH);
            br = new BufferedReader(fr);
            p.load(br);
        } finally {
            Utils.closeIgnoringException(br);
            Utils.closeIgnoringException(fr);
        }

        @SuppressWarnings({
                "unchecked", "rawtypes"
        })
        Map<String, String> pMap = new HashMap<String, String>((Map) p);
        eflowNames.addAll(pMap.values());
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        
        try {
            
             System.out.println("Getting source ids");
            dbConnection.query("SELECT * FROM monitor_ids;");

            while (dbConnection.moveNext()) {
                sIDMap.put(dbConnection.gets("mi_key"), Integer.valueOf(dbConnection.geti("mi_id")));
            }
            
            System.err.println("There are: " + sIDMap.size() + " series in the DB");
            loadEflowNames();
            System.out.println(eflowNames.toString());
            for(final String eflowName: eflowNames) {
                tablesMap.put(eflowName, new SDTables(eflowName));
            }

            System.out.println("\n\n Tables Map: \n\n");
            
            StringBuilder sb = new StringBuilder(8192);
            final int tLen = vsDestTables.length;
            for(final SDTables sdTable: tablesMap.values()) {
                final long sTime = sdTable.holeStartTime;
                final long eTime = sdTable.holeEndTime;
                if(sTime == 0 || eTime == 0) {
                    System.err.println("Ignoring because time is unable to determine missing data bounds: " + sdTable);
                } else {
                    final Integer srcINID = sdTable.srcINID;
                    final Integer srcOUTID = sdTable.srcOUTID;
                    final Integer dstINID = sdTable.dstINID;
                    final Integer dstOUTID = sdTable.dstOUTID;
                    
                    if(srcINID == null || srcOUTID == null || 
                            dstINID == null || dstOUTID == null ) {
                        System.err.println("Ignoring because time is unable to determine data IDs: " + sdTable);
                    } else {
                        System.out.println("\n\n" + sdTable + "\n\n");
                        for(int i=0; i<tLen; i++) {
                            final StringBuilder sbSql = new StringBuilder();
                            sbSql.append("insert into ").append(sdTable.srcTableNames[i]);
                            sbSql.append(" select rectime,").append(srcINID).append(",mval,mmin,mmax");
                            sbSql.append(" from ").append(sdTable.dstTableNames[i]);
                            sbSql.append(" where id=").append(dstOUTID).append(" and ");
                            sbSql.append(" rectime >= ").append(sTime).append(" and rectime <= ").append(eTime).append(";\n");

                            sbSql.append("insert into ").append(sdTable.srcTableNames[i]);
                            sbSql.append(" select rectime,").append(srcOUTID).append(",mval,mmin,mmax");
                            sbSql.append(" from ").append(sdTable.dstTableNames[i]);
                            sbSql.append(" where id=").append(dstINID).append(" and ");
                            sbSql.append(" rectime >= ").append(sTime).append(" and rectime <= ").append(eTime).append(";");
                            
                            System.out.println(sbSql.toString());
                            sb.append(sbSql).append("\n");
                        }
                    }
                }
            }
            
            System.out.println("\n\n SQL Statements: \n\n\n" );
            
            System.out.println(sb.toString());
            
            System.out.println("\n\n END SQL Statements: \n\n\n" );

            
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
