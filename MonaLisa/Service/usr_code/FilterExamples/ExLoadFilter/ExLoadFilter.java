import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Filters.GenericMLFilter;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Gresult;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MonitorClient;
import lia.Monitor.monitor.MonitorFilter;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;

/**
 * This is a simple filter that computes min, max and mean value for Load5
 * on a cluster.
 *
 */
public class ExLoadFilter extends GenericMLFilter {

    private final static String Name = "ExLoadFilter";

    Hashtable hnodes;
    Hashtable tmp_hnodes;

    /**
     *  Parameter name that I'm interested in
     */
    public final static String para = "Load5";

    /**
     *  Cluster Name that I'm interested in
     */
    public final static String clus = "MonaLisa";
    String farmName = null;

    /**
     * Predicates for filtering online data
     */
    private monPredicate[] monPreds = null;

    private int testInc = 0;

    /**
     *Constructor for the ExLoadFilter object
     *
     * @param  farmName  Description of the Parameter
     */
    public ExLoadFilter( String farmName ) {
        super( farmName );
        hnodes = new Hashtable();
        tmp_hnodes = new Hashtable();
        /*
         *  If this filter should receive all the 'on-line' data coming from ML
         *  please comment the following line
         *  The first three parameters are used to filter FarmName, ClusterName and NodeName
         *  They accept * as wildcard. The next two are used to filter data based
         *  on time(for filters they should be -1, -1). The next parameter is an array
         *  of String(s) used to filter on the online Result(s) based on Parameter Name
         *  In this example are filtered all Results that contains data for Load5
         *  parameter for all nodes from Clusters that starts with PN.
         *  To acheive this an wildcard is used for ClusterName "PN*"
         */
        monPreds = new monPredicate[]{new monPredicate( "*", clus + "*", "*", -1, -1, new String[]{para}, null )};
    }


    /**
     *  Override from GenericMLFilter
     *
     * @return    The name of this Fitlter
     */
    public String getName() {
        return Name;
    }


    /**
     *  Override from GenericMLFilter
     *  Gets the filterPred attribute of the ExLoadFilter object
     *
     * @return    A vector of monPredicate(s) for filtering real-time data
     */
    public monPredicate[] getFilterPred() {
        return monPreds;
    }


    /**
     *  Override from GenericMLFilter
     *
     * @return    how often should expressResults be called (in millis)
     */
    public long getSleepTime() {
        return 2 * 6 * 1000;
    }


    /**
     *  Override from GenericMLFilter
     *
     * @param  o  A Result or a Vector of Result(s)
     */
    public void notifyResult( Object o ) {
        if ( o != null ) {
            if ( o instanceof Vector ) {
                Vector v = (Vector) o;

                for ( int i = 0; i < v.size(); i++ ) {
                    notifyResult( v.elementAt( i ) );
                }

            } else if ( o instanceof Result ) {
                Result r = (Result) o;

                /*
                 *  Just double checking
                 */
                if ( r.ClusterName.indexOf( clus ) != -1 && r.getIndex( para ) != -1 ) {
                    System.out.println( " [ " + getName() + " ] Adding a result to hash: " + r );
                    tmp_hnodes.put( r.NodeName, r );
                }
            }
        }
    }


    /**
     *  This method clean-up the hash for online data and place the data in
     *  another hash used to fill the Gresult(s) that will be sent to the
     *  registered clients.
     *  The synchronized part should be kept as small as possible.
     */
    private void exchangeBuffers() {

        /*
         *  clear older data
         */
        hnodes.clear();

        /*
         *  keep the synchronized section as small as possible
         */
        synchronized ( tmp_hnodes ) {
            hnodes.putAll( tmp_hnodes );
            tmp_hnodes.clear();
        }

    }


    /**
     *  Override from GenericMLFilter
     *  This method is called from time to time (@see getSleepTime()). It should
     *  interpret the data and send it to the registered clients.
     *
     *  In this simple example only one Gresult is sent, because the filter is
     *  interested only in Load5 on Cluster PN
     *
     * @return    a Vector of Gresult(s) or Result(s)
     */
    public Object expressResults() {

        System.out.println( " [ " + getName() + " ] " + new Date() + " Starting expressResults" );
        /*
         *  Return null if the filter did not receive any Result(s)
         */
        if ( tmp_hnodes.size() == 0 ) {
            System.out.println( " [ " + getName() + " ] " + new Date() + " End expressResults ... NO Results got since last iteration ... returning null!" );
            return null;
        }

        /*
         *  Clean up the receving buffer and use hnodes for future analysis
         */
        exchangeBuffers();

        Gresult xr = new Gresult( farmName, clus, para );

        Vector ans = new Vector();
        double[] values = new double[hnodes.size()];

        xr.TotalNodes = xr.Nodes = hnodes.size();

        if ( hnodes != null && hnodes.size() > 0 ) {

            int k = 0;

            for ( Enumeration e2 = hnodes.elements(); e2.hasMoreElements();  ) {
                Result r = (Result) e2.nextElement();

                if ( r != null ) {
                    int indx = r.getIndex( para );

                    values[k++] = r.param[indx];
                }
            }

            fill_data( xr, values );
            xr.time = System.currentTimeMillis();
            System.out.println( " [ " + getName() + " ] Notify a NEW Gresult: " + toString( xr ) );
            ans.add( xr );
	    
	    //This Result will be sent "back" in ML
	    String[] pName = new String[]{"MyFakeLoad"};
	    Result testR = new Result("F1", "MyFilterCluster", "Node1", "mod1", pName);
	    testR.param[0] = .79;
	    testR.time = System.currentTimeMillis();
            ans.add( testR );

//            ans.add( hnodes.get( "ml.bucharest.roedu.net" ) );
        }
        System.out.println( " [ " + getName() + " ] " + new Date() + " End expressResults ... Returning " + ans.size() + " Gresults." );
        return ans;
    }


    /**
     *  Helper function for pretty printing a Gresult
     *
     * @param  gr  Gresult to print
     * @return     String representation of Gresult
     */
    private String toString( Gresult gr ) {
        StringBuffer sb = new StringBuffer();

        sb.append( gr.ClusterName );
        sb.append( "\t" );
        sb.append( gr.FarmName );
        sb.append( "\t" );
        sb.append( gr.Module );
        sb.append( "\t" );
        sb.append( new Date( gr.time ) );
        sb.append( "\t" );
        sb.append( "NoNodes = " + gr.Nodes );
        sb.append( "\t" );
        sb.append( "min = " + gr.min );
        sb.append( "\t" );
        sb.append( "max = " + gr.max );
        sb.append( "\t" );
        sb.append( "mean = " + gr.mean );
        return sb.toString();
    }


    /**
     *  Helper function to calculate min, max and mean value for received data
     *
     * @param  xr      The Gresult to fill
     * @param  values  Values used to fill Gresult
     */
    private void fill_data( Gresult xr, double[] values ) {

        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        int vlen = values.length;

        for ( int i = 0; i < vlen; i++ ) {
            sum += values[i];
            if ( min > values[i] ) {
                min = values[i];
            }
            if ( max < values[i] ) {
                max = values[i];
            }
        }

        xr.mean = sum / (double) vlen;
        xr.sum = sum;
        xr.max = max;
        xr.min = min;
    }

}

