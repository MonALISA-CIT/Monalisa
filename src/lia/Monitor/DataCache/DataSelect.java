package lia.Monitor.DataCache;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.ntp.NTPDate;
import lia.web.utils.Formatare;

public class DataSelect {

    private static final String DEFAULT_TABLE_NAME = "mondata";

    private static final String GROUP_PATTERN = "(?:\\s*(?:MAX|MIN|AVG|min|max|avg)\\s*)";

    private static final String FLOAT_PATTERN = "(?:\\s*(?:\\d+(?:\\.\\d+)?|\\d*\\.\\d+)\\s*)";

    private static final String CONDITION_PATTERN = "(?:\\s*(?:<|>|=|>=|<=|<>)\\s*)";

    private static final String AND_PATTERN = "(?:\\s+(?:AND|and)\\s+)";

    private static final String OR_PATTERN = "(?:\\s+(?:OR|or)\\s+)";

    private static final String LOGICAL_PATTERN = "(?:" + AND_PATTERN + "|" + OR_PATTERN + ")";

    // private static final String ONE_OR_MORE_SQLWORDS_PATTERN = "(?:(:?\\s*\\**)*(:?\\s*\\w+)+(?:\\s*\\**)+)+";
    // private static final String SQL_STRING_PATTERN =
    // "(?:\\s*(?:\\*"+ONE_OR_MORE_SQLWORDS_PATTERN+"\\*?)|(?:\\*?"+ONE_OR_MORE_SQLWORDS_PATTERN+"\\*))";

    private static final Pattern AND_SplitPattern = Pattern.compile(AND_PATTERN);

    private static final Pattern OR_SplitPattern = Pattern.compile(OR_PATTERN);

    /**
     * <p>
     * Builds a query based on <code>monPredicate</code> p
     * 
     * @param The
     *            predicate
     * @param tableName
     * @return A <code>String</code> representing the query if the <code>monPredicate</code> is valid or
     *         <code>null</code> if the <code>monPredicate</code> is invalid.
     * 
     */
    public static final synchronized String rquery(monPredicate p, String tableName) {
        StringBuilder rsb = new StringBuilder(128);
        rsb.delete(0, rsb.length());
        rsb.append("SELECT * FROM ");
        rsb.append(((tableName != null) ? tableName : DEFAULT_TABLE_NAME));
        rsb.append(" WHERE ");

        // first of all test the predicate ...
        if (!testPredicate(p)) return null;

        boolean bAnd = false;

        // time
        if (p.tmin > 0) {
            bAnd = true;

            rsb.append("  rectime > ");
            rsb.append(p.tmin);

            if (p.tmax > 0) {
                rsb.append(" AND  rectime < ");
                rsb.append(p.tmax);
            }
        }

        if (p.tmin <= 0) {
            if (p.tmin > -100) { // no history info should be here ...
                // some clients use (-60,0) instead of (-1,-1) so it's better to compare with something else than -1
                rsb.append("0=1");
                return rsb.toString();
            }

            long ntime = NTPDate.currentTimeMillis() + p.tmin;
            rsb.append("  rectime>");
            rsb.append(ntime);

            bAnd = true;

            if (p.tmax < -1000) { // is there really an upper limit ?
                ntime = NTPDate.currentTimeMillis() + p.tmax;
                rsb.append(" AND rectime<");
                rsb.append(ntime);
            }
        }

        // farms, clusters and nodes
        if (!All(p.Farm)) {
            if (bAnd) rsb.append(" AND ");

            bAnd = true;

            if (SomeSQLString(p.Farm)) {
                rsb.append("mfarm LIKE '");
                rsb.append(p.Farm);
                rsb.append("' ");
            } else {
                rsb.append("mfarm='");
                rsb.append(p.Farm);
                rsb.append("' ");
            }
        }

        if (!All(p.Cluster)) {
            if (bAnd) rsb.append(" AND ");

            bAnd = true;

            if (SomeSQLString(p.Cluster)) {
                rsb.append("mcluster LIKE '");
                rsb.append(p.Cluster);
                rsb.append("' ");
            } else {
                rsb.append("mcluster='");
                rsb.append(p.Cluster);
                rsb.append("' ");
            }
        }

        if (!All(p.Node)) {
            if (bAnd) rsb.append(" AND ");

            bAnd = true;

            if (SomeSQLString(p.Node)) {
                rsb.append("mnode LIKE '");
                rsb.append(p.Node);
                rsb.append("' ");
            } else {
                rsb.append("mnode='");
                rsb.append(p.Node);
                rsb.append("' ");
            }
        }

        // parameters
        if ((p.parameters != null) && (p.parameters.length > 0)) {
            if (bAnd) rsb.append(" AND ");

            bAnd = true;
            rsb.append("(");
            for (int i = 0; i < p.parameters.length; i++) {

                if (p.parameters[i] == null || p.parameters[i].length() == 0) continue;

                if (SomeSQLString(p.parameters[i])) {
                    rsb.append("mfunction LIKE '");
                    rsb.append(p.parameters[i]);
                    rsb.append("' ");
                } else {
                    rsb.append("mfunction='");
                    rsb.append(p.parameters[i]);
                    rsb.append("' ");
                }

                if (p.constraints != null && p.constraints[i] != null && p.constraints[i].length() > 0) {

                    replaceGroupFunctions(p, i);
                    rsb.append(" AND( ");
                    String[] AND_SplitConstraints = AND_SplitPattern.split(p.constraints[i]);
                    for (int j = 0; j < AND_SplitConstraints.length; j++) {
                        String[] OR_SplitConstraints = OR_SplitPattern.split(AND_SplitConstraints[j]);
                        for (int k = 0; k < OR_SplitConstraints.length; k++) {
                            rsb.append(" mval ");
                            rsb.append(OR_SplitConstraints[k]);
                            rsb.append(((k != OR_SplitConstraints.length - 1) ? " OR " : " "));
                        }
                        if (j != AND_SplitConstraints.length - 1) rsb.append(" AND ");
                    }
                    rsb.append(" ) ");
                }
                if (i != (p.parameters.length - 1)) {
                    rsb.append(" OR ");
                }
            }
            rsb.append(" ) ");
        }

        if (!bAnd) rsb.append(" true "); // in case there was no condition

        rsb.append(" order by rectime asc");
        
        int SQL_SIZE_LIMIT = 10000;
        
        try {
            SQL_SIZE_LIMIT = Integer.valueOf(AppConfig.getProperty("lia.Monitor.DataCache.DataSelect.SQL_SIZE_LIMIT", "10000")).intValue(); 
        }catch(Throwable t){
            SQL_SIZE_LIMIT = 10000;
        }
        
        if(SQL_SIZE_LIMIT > 0) {
            rsb.append(" limit ").append(SQL_SIZE_LIMIT);
        }
        
        return rsb.toString();
    }

    public static void main(String args[]) {	
        monPredicate p = new monPredicate("*", "*", "*", -1, -1, null, null);

        System.err.println(rquery(p, "monitor_1y"));

        p = new monPredicate("*", "%WAN%", "*", -300000, -100000, null, null);

        System.err.println(rquery(p, "monitor_1y"));

        p = new monPredicate("ABILENE", "%WAN%", "node", -300000, -100000, new String[] { "param1", "param2"}, null);

        System.err.println(rquery(p, "monitor_1y"));
    }

    /**
     * <p>
     * Builds a query based on <code>monPredicate</code> p
     * 
     * @param The
     *            predicate
     * @param Implicit
     *            value for tableName
     * @return A <code>String</code> representing the query if the <code>monPredicate</code> is valid or
     *         <code>null</code> if the <code>monPredicate</code> is invalid.
     * 
     */
    static public final String rquery(monPredicate p) {
        return rquery(p, DEFAULT_TABLE_NAME);
    }

    /**
     * <p>
     * Builds a query based on <code>monPredicate</code> p
     * 
     * @param The
     *            predicate
     * @return A <code>String</code> representing the query if the <code>monPredicate</code> is valid or
     *         <code>null</code> if the <code>monPredicate</code> is invalid.
     * 
     */
    static public final String rquery_new(monPredicate p) {
        // first of all test the predicate ...
        if (!testPredicate(p)) return null;

        String ans = "SELECT RT.rectime , MF.mfarm, MC.mcluster, MN.mnode, MP.mfunction, DV.mval " + // dvalues
                                                                                                        // should be
                                                                                                        // changed for
                                                                                                        // String
                "FROM rectimes AS RT, dvalues AS DV, mfarm AS MF, mcluster AS MC, mnode AS MN, moduleparams AS MP WHERE ";

        // time
        if ((p.tmin > 0) && (p.tmax > 0)) {
            ans += "(RT.rectime > " + p.tmin + " AND  RT.rectime < " + p.tmax + ") AND ";
        }

        if (p.tmin > 0) {
            ans += "RT.rectime>" + p.tmin + " AND ";
        }

        if (p.tmin < 0) {
            ans += "RT.rectime>" + (NTPDate.currentTimeMillis() + p.tmin) + " AND ";
        }
        ans += "DV.id_t=RT.id_t ";
        // end time

        // farms, clusters and nodes
        if (!All(p.Farm)) {
            if (SomeSQLString(p.Farm)) {
                ans += "AND MF.mfarm LIKE '" + p.Farm + "' ";
            } else {
                ans += "AND MF.mfarm='" + p.Farm + "' ";
            }
        }
        ans += "AND MC.id_f=MF.id_f ";

        if (!All(p.Cluster)) {
            if (SomeSQLString(p.Cluster)) {
                ans += "AND MC.mcluster LIKE '" + p.Cluster + "' ";
            } else {
                ans += "AND MC.mcluster='" + p.Cluster + "' ";
            }
        }
        ans += "AND MC.id_c=MN.id_c ";

        if (!All(p.Node)) {
            if (SomeSQLString(p.Node)) {
                ans += "AND MN.mnode LIKE '" + p.Node + "' ";
            } else {
                ans += "AND MN.mnode ='" + p.Node + "' ";
            }
        }
        ans += "AND DV.id_n=MN.id_n ";

        // parameters
        if ((p.parameters != null) && (p.parameters.length > 0)) {
            ans += "AND (";
            for (int i = 0; i < p.parameters.length; i++) {

                ans += "(MP.mfunction='" + p.parameters[i] + "' ";
                if (p.constraints != null && p.constraints[i] != null && p.constraints[i].length() > 0) {
                    replaceGroupFunctions(p, i);
                    ans += "AND( ";
                    String[] AND_SplitConstraints = AND_SplitPattern.split(p.constraints[i]);
                    for (int j = 0; j < AND_SplitConstraints.length; j++) {
                        String[] OR_SplitConstraints = OR_SplitPattern.split(AND_SplitConstraints[j]);
                        for (int k = 0; k < OR_SplitConstraints.length; k++)
                            ans += "DV.mval " + OR_SplitConstraints[k] + ((k != OR_SplitConstraints.length - 1) ? " OR " : " ");
                        // again dvalues....
                        if (j != AND_SplitConstraints.length - 1) ans += "AND ";
                    }
                    ans += ")  ";
                }
                ans += ")  ";
                if (i != (p.parameters.length - 1)) ans += "OR ";
            }
            ans += ")  ";
        }
        ans += "AND MP.id_mp = DV.id_mp ";
        // end functions

        ans += "ORDER BY RT.rectime";

        return ans;

    }

    static final boolean All(String test) {
        // defines the * condition
        if (test == null) return true;
        if (test.equals("")) return true;
        if (test.equals("*")) return true;
        return false;
    }

    // There is ONLY ONE wildcard %! It matches one or more characters.
    // The * character is NOT an wildcard! The * is treated like an usual character, same as the SPACE " " character.

    // Examples:
    //
    // %lx%ch ========> It matches lx0032.cern.ch, lx0002.cern.ch, aslxasdch, etc
    // %Intern% *Far% ========> It matches Internal *Farm but doesn't match Internal Farm
    // %Intern%Far% ========> It matches Internal Farm, Internal * Farm, InternalFarm, Internal *Farm.
    // % ========> It matches everything. Same as * in the function static boolean All ( String test ).
    static final boolean SomeSQLString(String test) {
        if (test == null || test.length() == 0) return false;
        if (test.indexOf("%") == -1) return false;

        // everything seems ok
        return true;
    }

    // the general ideea any % ---> (\w*\s*\\**)*
    static final String buildPatternFromString(String str) {

        if (All(str)) return "(:?\\w*\\s*\\**)*";

        return str.replaceAll("%", "(:?\\\\w*\\\\s*\\\\**)*");
    }

    static final public Result matchResult(final Result r, final monPredicate p) {
        if (r == null || p == null) return null;

        // matchTime returns "true" always, so just skip it for now
        // if (!matchTime(r.time, p.tmin, p.tmax))
        // return null;

        if (!matchString(p.Farm, r.FarmName)) return null;
        if (!matchString(p.Cluster, r.ClusterName)) return null;
        if (!matchString(p.Node, r.NodeName)) return null;

        if (p.parameters != null) {
            if(r.param == null || r.param_name == null) {
                return null;
            }
            
            final Result nr = new Result(r.FarmName, r.ClusterName, r.NodeName, r.Module, null);

            nr.time = r.time;
            for (int i = 0; i < r.param.length && i < r.param_name.length; i++) {
                
                
                for (int j = 0; j < p.parameters.length; j++) {
                    if (matchString(p.parameters[j], r.param_name[i])) {
                        nr.addSet(r.param_name[i], r.param[i]);
                    }
                }
            }

            if (nr.param != null && nr.param.length > 0) { return nr; }
        } else {
            return r;
        }

        return null;
    }

    static final public eResult matchResult(final eResult r, final monPredicate p) {
        if (r == null || p == null) return null;

        // if (!matchTime(r.time, p.tmin, p.tmax))
        // return null;
        if (!matchString(p.Farm, r.FarmName)) return null;
        if (!matchString(p.Cluster, r.ClusterName)) return null;
        if (!matchString(p.Node, r.NodeName)) return null;

        
        if (p.parameters != null) {
            if(r.param == null || r.param_name == null) {
                return null;
            }
            
            final eResult nr = new eResult(r.FarmName, r.ClusterName, r.NodeName, r.Module, null);

            nr.time = r.time;
            for (int i = 0; i < r.param.length; i++) {
                for (int j = 0; j < p.parameters.length; j++) {
                    if (matchString(p.parameters[j], r.param_name[i])) {
                        nr.addSet(r.param_name[i], r.param[i]);
                    }
                }
            }

            if (nr.param != null && nr.param.length > 0) { return nr; }
        } else {
            return r;
        }

        return null;
    }

    /**
     * Check if the data matches some pattern. The pattern can contain as wildcards '%' or '*'
     * either of which will match a portion of any length of the data string.
     * 
     * @param p pattern
     * @param d data
     * @return true if data matches the pattern, false if not
     */
    static final public boolean matchString(final String p, final String d) {
        if (p == null) 
        	return false;

        if (p.equals("*") || p.equals("%")) 
        	return true;

        if (d == null)
        	return false;

        final int i = p.indexOf('*');
        final int j = p.indexOf('%');
        
        if (i == -1 && j == -1) {
        	// simple case, no wildcards to match
        	return p.equals(d); 
        }
        
        if ((i == -1 && j == p.length()-1) || (j == -1 && i == p.length()-1)){
        	// ends with one wildcard, will try to match the rest
            final String p1 = p.substring(0, p.length() - 1);
            return d.startsWith(p1);
        }

        if (i == 0 || j == 0) {
        	// starts with wildcard; if there is no other wildcard we have another simple cases
        	final int iLast = p.lastIndexOf('*');
        	final int jLast = p.lastIndexOf('%');
        	
        	if (iLast<=0 && jLast<=0){
        		// there is no other wildcard in the string, will match if d ends with the rest of the string
                final String p1 = p.substring(1);            
                return d.endsWith(p1);
        	}
        }
        
        // gets complicated, falling back to pattern matching
        String sPattern = Formatare.replace(p, "*", ".*");
        sPattern = Formatare.replace(sPattern, "%", ".*");
        
        try{
        	return d.matches("^"+sPattern+"$");
        }
        catch (PatternSyntaxException pse){
        	// ignore
        }
        
        return false;
    }

    // TODO
    static public final boolean matchTime(final long t, final long tmin, final long tmax) {

        if ((tmin == -1) && (tmax == -1)) return true;
        /*
         * if ( tmin < 0 ) {
         * 
         * if ( t > NTPDate.currentTimeMillis() + tmin ) return true ; return false;
         *  }
         */
        /*
         * if ( (tmin == 0 ) && ( tmax < 0 ) ) { long TMAX = ( (Result ) data.lastElement() ).time; if ( ( TMAX - t ) <=
         * Math.abs( tmax ) ) return true; return false; }
         * 
         * if ( (t >= tmin ) && ( t <= tmax )) return true; return false;
         */

        return true;
    }

    /**
     * <p>
     * Test the <code>predicate</code> p against some patterns.
     * </p>
     * 
     * @param p
     *            Predicate that is tested
     * @return <code>true</code> if the test succeded; <code>false</code> if the test fails
     */
    public static final boolean testPredicate(monPredicate p) {
        boolean returnValue = true;

        if (p.constraints == null || p.constraints.length == 0) return true;

        String condPattern = "(?:" + CONDITION_PATTERN + "(" + FLOAT_PATTERN + "|(" + GROUP_PATTERN + ")" + ")" + ")";

        String pattern = GROUP_PATTERN + "|((" + condPattern + ")(" + LOGICAL_PATTERN + ")?)+";

        for (int i = 0; i < p.constraints.length; i++) {
            if (p.constraints[i] != null && p.constraints[i].length() != 0 && !Pattern.matches(pattern, p.constraints[i])) {

                returnValue = false;
                break;
            }
        }
        return returnValue;
    }

    private static final void replaceGroupFunctions(monPredicate p, int i) {

        if (p.constraints[i].indexOf("MIN") != -1 || p.constraints[i].indexOf("min") != -1) {
            p.constraints[i] = p.constraints[i].replaceAll("MIN", "" + calculateMin(p, i));
        }

        if (p.constraints[i].indexOf("MAX") != -1 || p.constraints[i].indexOf("max") != -1) {
            p.constraints[i] = p.constraints[i].replaceAll("MAX", "" + calculateMax(p, i));
        }

        if (p.constraints[i].indexOf("AVG") != -1 || p.constraints[i].indexOf("avg") != -1) {
            p.constraints[i] = p.constraints[i].replaceAll("AVG", "" + calculateAvg(p, i));
        }
    }

    public static final float calculateMin(monPredicate p, int colNumber) {
        return calculateSqlGroupFunc("MIN", p, colNumber);
    }

    public static final float calculateMax(monPredicate p, int colNumber) {
        return calculateSqlGroupFunc("MAX", p, colNumber);
    }

    public static final float calculateAvg(monPredicate p, int colNumber) {
        return calculateSqlGroupFunc("AVG", p, colNumber);
    }

    public static final float calculateSqlGroupFunc(String fName, monPredicate p, int colNumber) {
        String sqlQuery = "SELECT " + fName + "(" + p.parameters[colNumber] + ") FROM mondata WHERE";

        if (!All(p.Farm)) {
            sqlQuery += " AND mfarm='" + p.Farm + "' ";
        }

        if (!All(p.Cluster)) {
            sqlQuery += " AND mcluster='" + p.Cluster + "' ";
        }

        if (!All(p.Node)) {
            sqlQuery += " AND mnode='" + p.Node + "' ";
        }

        return 0;
    }

}
