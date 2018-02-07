import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import lia.Monitor.Filters.GenericMLFilter;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MonitorClient;
import lia.Monitor.monitor.MonitorFilter;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.monitor.monPredicate;
import lia.util.mail.DirectMailSender;

/**
 * This is a simple Filter/Trigger agent which will send an email if the load
 * on the Master node will be higher than 1.
 *
 */
public class ExTrigger extends GenericMLFilter {

    /*
     *  Recipient's addresses used by this filter/trigger to notify the problem
     */
    private final static String[] RCPT = {"ramiro@roedu.net"};

    /*
     *  Filter's Name
     */
    private final static String Name = "ExTrigger";

    /*
     *  Vector of monPredicates used to filter 'online' flux of data
     *  coming from the monitoring modules
     */
    private monPredicate[] monPreds = null;

    /*
     *  internal var used to keep track of last sent email
     */
    private long lastSentMail = 0;

    //every hour...
    private final static long MAIL_DELAY_NOTIF = 1 * 60 * 60 * 1000;

    /**
     *  Load5 threshold...above this we have an alarm() to trigger
     */
    public final static double MAX_LOAD = .9;


    /**
     *Constructor for the ExTrigger object
     *
     * @param  farmName  - short FarmName
     */
    public ExTrigger( String farmName ) {
        super( farmName );

        /*
         *  If this filter should receive all the 'on-line' data coming from ML
         *  please comment the following line
         *  The first three parameters are used to filter FarmName, ClusterName and NodeName
         *  They accept * as wildcard. The next two are used to filter data based
         *  on time(for filters they should be -1, -1). The next parameter is an array
         *  of String(s) used to filter on the online Result(s) based on Parameter Name
         *  In this example are filtered all Results that contains data for Load5 parameter
         *  coming from Cluster Monalisa
         */
        monPreds = new monPredicate[]{new monPredicate( "*", "MonaLisa", "*", -1, -1, new String[]{"Load5"}, null )};
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
     *  Gets the filterPred attribute of the ExTrigger object
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
     *  In this example no data is processed in expressResults,
     *  so the filter sleeps for every 5 hours
     */
    public long getSleepTime() {
        return 5 * 60 * 60 * 1000;
    }


    /**
     *  Override from GenericMLFilter
     *
     * @param  o  A Result or a Vector of Result(s)
     */
    public void notifyResult( Object o ) {

        if ( o == null ) {
            return;
        }

        Result r = null;

        if ( o instanceof Result ) {
            r = (Result) o;
            System.out.println( " [ " + getName() + " ] Got a result: " + r );
            if ( r.param != null ) {
                for ( int i = 0; i < r.param.length; i++ ) {
                    if ( r.param[i] > MAX_LOAD ) {
                        System.out.println( " [ " + getName() + " ] MAX_LOAD [ " + MAX_LOAD + " ] riched." );
                        alarm();
                    }
                }
            }
        } else if ( o instanceof Vector ) {

            Vector rv = (Vector) o;

            for ( int i = 0; i < rv.size(); i++ ) {
                notifyResult( rv.elementAt( i ) );
            }

        } else {
            return;
        }
    }


    /**
     */
    public void alarm() {
        if ( lastSentMail + MAIL_DELAY_NOTIF < System.currentTimeMillis() ) {
            System.out.println( " [ " + getName() + " ] Sending mail...." );
            sendMail( "support@monalisa.cern.ch", "Load5 on MasterNode is higher than " + MAX_LOAD,
                "Test Message from ExTrigger @ " + farmName + "\n\n:)" );
            lastSentMail = System.currentTimeMillis();
        } else {
            //Take some action ...
            System.out.println( " [ " + getName() + " ] Taking action ...." );
        }
    }


    /**
     *  Override from GenericMLFilter
     *  This method is called from time to time (@see getSleepTime()). It should
     *  interpret the data and send it to the registered clients.
     *
     *  In this simple example no data are sent to clients. The filter acts as a Trigger
     *
     * @return    a Vector of Gresult(s) or null
     */
    public Object expressResults() {
        return null;
    }


    /**
     *  Helper method to send an email to one or more RCPTs
     *
     * @param  subj  Subject of the mail
     * @param  msg   Body of the mail
     * @param  from
     */
    private void sendMail( String from, String subj, String msg ) {
        try {
            DirectMailSender.getInstance().sendMessage( "support@monalisa.cern.ch", RCPT, subj, msg );
        } catch ( Throwable t ) {
            System.out.println( " [ " + getName() + " ] Cannot send email...Got Exception: " + t.getMessage() );
        }
    }
}

