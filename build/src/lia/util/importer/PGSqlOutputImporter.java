package lia.util.importer;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FileReader;
import java.io.IOException;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 *
 * @author ramiro
 */
public class PGSqlOutputImporter {

    private static final class OutSeries implements Comparable<OutSeries>{
        private final long rectime;
        private final double value;
        private final String mfarm;
        private final String mcluster;
        private final String mnode;
        private final String mfunction;

        OutSeries(final long rectime, final double value, final String mfarm, final String mcluster, final String mnode, final String mfunction) {
            this.rectime = rectime;
            this.value = value;
            this.mfarm = mfarm;
            this.mcluster = mcluster;
            this.mnode = mnode;
            this.mfunction = mfunction;
        }

        private static OutSeries fromString(final String s) {
            final String ts = s.trim();
            if(!Character.isDigit(ts.charAt(0))) {
                throw new IllegalArgumentException("The input string should start with a digit");
            }
            final String[] tokens = ts.split("(\\s)*\\|(\\s)*");
            try {
                return new OutSeries(Long.parseLong(tokens[0]), Double.parseDouble(tokens[5]), tokens[1], tokens[2], tokens[3], tokens[4]) ;
            }catch(Throwable t) {
                System.err.println("Expcetion for line: " + s + ". Ignoring it. Cause:");
                t.printStackTrace();
            }

            return null;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("OutSeries :- rectime:").append(rectime);
            sb.append(", value:").append(value);
            sb.append(", mfarm:").append(mfarm);
            sb.append(", mcluster:").append(mcluster);
            sb.append(", mnode:").append(mnode);
            sb.append(", mfunction:").append(mfunction);
            return sb.toString();
        }

        public int compareTo(OutSeries o) {
            final long diff = rectime - o.rectime;
            return (diff < 0)? -1 : (diff > 0)? 1: 0;
        }
    }

        /**
     * Helper method use to close a {@link Closeable} ignoring eventual exception
     * @param c the closeable
     */
    @SuppressWarnings("FinalStaticMethod")
    public static final void closeIgnoringExceptions(Closeable c) {
        try {
            if(c != null) {
                c.close();
            }
        }catch(Throwable _){}
    }

    public static String getSQL(OutSeries os, final String tName, int id) {
        return "INSERT INTO " + tName + " (rectime, id, mval, mmin, mmax) VALUES ("+os.rectime/1000L+", " +id+", " +os.value + ", " + os.value + ", " + os.value + ");";
    }
    
    public static void main(String[] args) throws IOException {
        final String fileName = "/home/ramiro/GVA_WAN.values";
        final long minrectime = 1267500534L * 1000;
        final long maxrectime = 1267514474L * 1000;
        final String tableName = "wan_rawstatus_chi_level3gva_level3";
        final int id = 5099;
        
        BufferedReader br = null;
        FileReader fr = null;
        try {
            fr = new FileReader(fileName);
            br = new BufferedReader(fr);
            StringBuilder sb = new StringBuilder();
            final SortedSet<OutSeries> set = new TreeSet<OutSeries>();
            for(;;) {
                final String line = br.readLine();
                if(line == null) {
                    break;
                }
                try {
                    final OutSeries os = OutSeries.fromString(line);
                    if(os.rectime <= maxrectime && os.rectime >= minrectime && os.mfunction.contains("Oper") && os.mfunction.contains("GVA1-GVA2")) {
//                        System.out.println(os);
                        set.add(os);
                    }
                }catch(Throwable t) {
                    //t.printStackTrace();
                }
            }

            for(OutSeries os: set) {
              sb.append("\n").append(getSQL(os, tableName, id));
            }
            System.err.flush();
            System.out.flush();
            System.out.println(sb.toString());
        } finally {
            closeIgnoringExceptions(fr);
            closeIgnoringExceptions(br);
        }


    }
}
