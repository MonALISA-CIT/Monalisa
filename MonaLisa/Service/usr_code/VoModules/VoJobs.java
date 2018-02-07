
import lia.Monitor.monitor.*;

import java.io.*;
import java.util.*;
import java.lang.*;
import java.net.InetAddress;


public class VoJobs extends cmdExec implements MonitoringModule  {

static public String OsName = "linux";
static public String ModuleName="VoJobs";
static public String[] ResTypes = {"Total Jobs","Idle Jobs","Running Jobs","Held Jobs","Total Submissions","Failed Submissions","PingOnly Submissions","Failed Jobs","Job Success Efficiency"};

static public boolean testmode  = false;
static public boolean debugMode = false;

public Date currDate     =  new Date();
public String vo           =  null;
public Vector  unixAccount = null;
public String monalisaHome = null;
public Hashtable jobMgr    = new Hashtable();
public Hashtable voTotals  = new Hashtable();
public VoAccounts voMetrics = new VoAccounts();

static public String testpath = "/home/weigand/MonALISA/MonaLisa.v098/Service/usr_code/VoModules/testdata";

private String[] nodes = null;

//------------------------------------------
public VoJobs () { 
  super(  ModuleName );
  info.ResTypes = ResTypes;
  isRepetitive = true;
}


//==============================================================
// configuration file entry: VoJobs{test,debug,location=}%30
public  MonModuleInfo init( MNode Node , String args ) {
  
    String arg = null;
    String volist = null;
    this.Node = Node;

    logit("Instantiating instance at "+currDate );
    logit("Arguments: "+args );
    // ------------------------
    // Check the argument list
    // ------------------------
    if (args != null ) {
      StringTokenizer tz = new StringTokenizer(args,",");
      int k = tz.countTokens();
      if ( k > 0 ) {
         nodes = new String[k];
      } 
      //module name first
      for ( int j=0; j < k; j ++ ) {
        arg = tz.nextToken().trim();
                nodes[j] = arg;
      }
    } // end if args

    // -------------------------------------
    // Determine the MonaALisa_HOME 
    // -------------------------------------
    monalisaHome = System.getProperty("MonaLisa_HOME");
    if ( monalisaHome == null ) {
      logit("MonaLisa_HOME environmental variable not set.");
    }
    else {
      logit("MonaLisa_HOME="+monalisaHome);
    }

    // -------------------------------------------------------
    // Determine the job managers being used and the location
    // of there processes for querying the queues.
    // -------------------------------------------------------
    getJobManagers();

    info.ResTypes = ResTypes;
    return info;
}

//=========================================================
public void getJobManagers() {
    String location = null;
    String var      = null;
    // -------------------------------------------------------
    // Initialize the command for the job manager types 
    // -------------------------------------------------------
    if (testmode) { 
      jobMgr.put("TESTCONDOR","TESTCONDOR_LOCATION/cat "+testpath+"/condor_q");
      jobMgr.put("TESTPBS",   "TESTPBS_LOCATION/cat "+testpath+"/qstat"); 
      jobMgr.put("TESTLSF",   "TESTLSF_LOCATION/cat "+testpath+"/bjobs");
      jobMgr.put("TESTJGW",   "TESTJGW_LOCAITION/bin/cat "+testpath+"/jgw ");
    }
    else {
      jobMgr.put("CONDOR","CONDOR_LOCATION/bin/condor_q");
      jobMgr.put("PBS",   "PBS_LOCATION/bin/qstat");
      jobMgr.put("LSF",   "LSF_LOCATION/bin/bjobs -a -u all");  
    }

    //------------------------------------------------------------------
    // Check the environmental variables for the job manager location
    // If found, replace the environmental value in the command string.
    //------------------------------------------------------------------
    for (Enumeration jm = jobMgr.keys(); jm.hasMoreElements();) {
      String jobManager = (String) jm.nextElement();
      var  = (String) jobManager+"_LOCATION";
      try {
        location = getEnvVariable((String) var);
      } catch (Exception e) {;}
      if ( location == null ) {
        logit("Job Manager ("+jobManager+") not used.");
        jobMgr.remove(jobManager);
      }
      else {
        //---------------------------------------------------------
        // Replace the variable portion of the path with the
        // environmental variable value found.
        // (e.g. - CONDOR_LOCATION)
        //---------------------------------------------------------
        String newloc = (String) jobMgr.get(jobManager);
        String value = newloc.replaceFirst(var,location);
        jobMgr.put(jobManager, (String) value);
        logit("Job Manager ("+jobManager+") to be used. Location: "+location);
      }
    } // end of for (Enum...

    //-----------------------------------------------------
    // Verify the remaining job managers executable exist.
    // if not, remove it and throw an exception or not.
    //-----------------------------------------------------
    for (Enumeration jm = jobMgr.keys(); jm.hasMoreElements();) {
      String jobManager = (String) jm.nextElement();
      String cmd1 = (String) jobMgr.get(jobManager); 
      StringTokenizer tz = new StringTokenizer ( cmd1 ) ;
      int ni = tz.countTokens();
      if ( ni > 0 )  {
        location = tz.nextToken().trim();
        File fd = new File(location);
        if ( fd.exists() ) { logit("Job Manager ("+jobManager+") Command("+location+") available.");
        }
        else {
          logit("ERROR: Job Manager ("+jobManager+") Command ("+location+") does not exist.");
          jobMgr.remove(jobManager);
        }
      } 
    } // end of for (Enum... 
  //---------------------------------------------------------------------
  // We could check for an jobMgr table at this point.
  // The pros are they could not get ML up without fixing this
  //     or
  // We check in doProcess and let it suspend the job periodically.
  //---------------------------------------------------------------------
}

//=========================================================
public String getEnvVariable(String var) throws Exception {
  String value = null;
  try {
    BufferedReader buff1 = procOutput ("echo $"+var ); 
    value = buff1.readLine();
    if ( (int) value.length() == (int) 0 ) {
      value = null;
    }
  } catch (Exception e) {;}
    return value;  
}
//=========================================================
public String[] ResTypes () {
  return ResTypes;  
}
//=========================================================
public String getOsName() { return OsName; }

//=======================================================================
//-- doProcess ----------------
public Object   doProcess() throws Exception {

 try { 
   //--------------------------------------
   // Initialize the VO totals table
   //--------------------------------------
   voMetrics.initializeTotalsTable();

   currDate = new Date();
   logit("--- "+currDate+" -------------");

   // --------------------------------------
   // check the sanity of the environment 
   // --------------------------------------
   if ( monalisaHome == null ) {
     throw new Exception ("MonaLisa_HOME variable not set.");
    }

   // ------------------------------------------
   // Start the Job Queue Manager collectors
   // ------------------------------------------
   collectJobMgrData();

   // ------------------------------------------
   // Start the Globus Gatekeep log parser. 
   // ------------------------------------------
   collectGatekeeperData();
   

  } catch (Exception e) {
         logit("FATAL ERROR -"+e);
         throw e;
    }
  return createResults();
}

//=== collectGatekeeperData ==========================
private void   collectGatekeeperData() throws Exception {
 String cmd = null;

 try{ 
     
     logit("Gatekeeper log parser starting");
     if (testmode) {
       cmd = "/bin/cat "+testpath+"/gatekeeper";
     }
     else {
       cmd = "python "+monalisaHome+"/Service/usr_code/VoModules/bin/parseGatekeeper.py 2>&1";
     }
     logit("Command - " + cmd );

     BufferedReader buff1 = procOutput ( cmd );
     if ( buff1  == null ) {
       throw new Exception ("Command line process failed unexpectedly");
     }

     ParseGateKeeperOutput(buff1);

  } catch (Exception e) {
         logit("FATAL ERROR -"+e);
         throw e;
    }
}

//=== collectJobMgrData ==========================
private void   collectJobMgrData() throws Exception {

 try{ 

   // ---- check the sanity of the environment ----
   if ( jobMgr.isEmpty() ) {
     throw new Exception ("There are no valid job queue managers to use.");
    }

   // --- query each queue managers -------------
   for (Enumeration queue = jobMgr.keys(); queue.hasMoreElements();) {
     String jobManager = (String) queue.nextElement();
     logit("Job Queue Manager - "+jobManager);

     String cmd1 = (String) jobMgr.get(jobManager); 
     logit("Command - " + cmd1 );

     BufferedReader buff1 = procOutput ( cmd1 );
  
     debug("Returned from procOutput");
     if ( buff1  == null ) {
        logit("Failed  for " + cmd1 );
        throw new Exception ("Command line process failed unexpectedly");
     }
     if (testmode) { 
        if (jobManager.equals( (String) "TESTCONDOR")) 
           ParseCondorOutput( buff1 );
        else if (jobManager.equals( (String) "TESTPBS")) 
           ParsePBSOutput( buff1 );
        else if (jobManager.equals( (String) "TESTLSF")) 
           ParseLSFOutput( buff1 );
        else 
          throw new Exception ("Invalid job manager ("+jobManager+").  Internal error.");
     } // end if test mode
     else {
        if      (jobManager.equals( (String) "CONDOR")) 
           ParseCondorOutput( buff1 );
        else if ( jobManager.equals( (String) "PBS"))  
           ParsePBSOutput( buff1 );
        else if (jobManager.equals( (String) "LSF")) 
           ParseLSFOutput( buff1 );
        else 
           throw new Exception ("Invalid job manager ("+jobManager+").  Internal error.");
     } // end else if testmode

   } // end of for

 } catch (Exception e) {
         logit("FATAL ERROR -"+e);
         throw e;
    }
}

//============================================================
public  Vector createResults() throws Exception {
  Vector results = new Vector();
  
  logit("Creating results object");
  try {
    // CAUTION: The results vector MUST pass the VO (NodeName) back in the
    //          SAME physical sequence as the listed in the .conf file.:
    for ( int n = 0; n < nodes.length; n++) {
      String VO = nodes[n];
    
      // Created result object that stores data in database
      // Result(farmName(weigand_farm), cluster(sdss) , name(hotdog62).....
      // *sdss (cluster)
      Result result  = new Result ( Node.getFarmName(),Node.getClusterName(), VO,ModuleName, ResTypes );
      // updates time 
      result.time =  ( new Date()).getTime();

      // --------------------------------------------------------------
      // Updates each type of data based on ResTypes with the VO totals
      // --------------------------------------------------------------
      for ( int i=0; i < ResTypes.length ; i++) {
        Double value = voMetrics.getMetric(VO,ResTypes[i]);
        result.param[i] = (double) value.doubleValue();
      }
      results.add(result); 
    } // end for Enu
 } catch ( Exception e) { 
       logit("FATAL ERROR: "+e );
       throw e;
   }
  logit("RETURNING "+results.size()+" updates");
  for ( int i = 0; i < results.size(); i++ ) {
    logit("...["+i+"]"+ (Result)results.elementAt(i));
  }
  return results;
}

// --------------------------------------------------
void logit(String msg) {
    System.out.println ( ModuleName+": " + msg );
}
// --------------------------------------------------
void debug(String msg){
  if (debugMode) {
    logit(msg);
  }
}
// --------------------------------------------------
public MonModuleInfo getInfo(){
        return info;
}

// ====== Parse Condor Ouput ==========================
void  ParseCondorOutput (  BufferedReader buff ) throws Exception {
  //--------------------------------------------------------------------
  // Job statuses:
  //   U = unexpanded (never been run), 
  //   H = on hold, 
  //   R = running, 
  //   I = idle (waiting for a machine to execute on), 
  //   C = completed, 
  //   X = removed.

  StringTokenizer tz;

  debug("Starting to process output");
  try {
    for ( ; ; ) {
       String lin = buff.readLine();
       // --- end of file ----
       debug("line - "+lin);
       if ( lin == null )  break;
       //if ( lin.equals("") ) break;
       //Find the specific fields so we can substring the line
       // ID      OWNER            SUBMITTED     CPU_TIME ST PRI SIZE CMD 
       // 185.0   sdss           10/8  20:09   0+00:00:00 R  0   0.0  data
       //
       tz = new StringTokenizer ( lin ) ;
       int ni = tz.countTokens();
       if ( ni > 4 )  {
         String id        = tz.nextToken().trim();
         String user      = tz.nextToken().trim();
         String date      = tz.nextToken().trim();
         String time      = tz.nextToken().trim();
         String cputime   = tz.nextToken().trim();
         String status    = tz.nextToken().trim();
         if ( status.equals("H"))  { 
           voMetrics.updateTotals(user,"Total Jobs");
           voMetrics.updateTotals(user,"Held Jobs");
         }
         else if ( status.equals("R"))  { 
           voMetrics.updateTotals(user,"Total Jobs");
           voMetrics.updateTotals(user,"Running Jobs");
         }
         else if ( status.equals("I"))  { 
           voMetrics.updateTotals(user,"Total Jobs");
           voMetrics.updateTotals(user,"Idle Jobs");
         }
       } // end of if
    } // end of for      
  } catch ( Exception e ) {
           logit("Exception in Parsing Condor output  Ex=" +e) ;
           throw e;
    }
}

// ====== Parse PBS Ouput ==========================
void  ParsePBSOutput (  BufferedReader buff ) throws Exception {
  //--------------------------------------------------------------------
  // Job states:
  //  C - Job is completed and leaving the queue on it's own.
  //  X - Job is removed from the queue
  //  E - Job is exiting after having run.
  //  H - Job is held.
  //  Q - job is queued, eligable to run or routed.
  //  R - job is running.
  //  I - job is idle.
  //  T - job is being moved to new location.
  //  W - job is waiting for its execution time
  //      (-a option) to be reached.
  //  S - (Unicos only) job is suspend.

  StringTokenizer tz;

  try {
        for ( ; ; ) {
           String lin = buff.readLine();
           debug("line: " + lin );
           // --- end of file ----
           if ( lin == null )  break;
           //if ( lin.equals("") ) break;

           //Find the specific fields so we can substring the line
           //Job id       Name             User             Time Use S Queue
           //------------ ---------------- ---------------- -------- - -----
           //22930.bh1    calmob           uscms01          70:21:58 R bg
           //
           tz = new StringTokenizer ( lin ) ;
           int ni = tz.countTokens();
           if ( ni > 4 )  {
             String pid     = tz.nextToken().trim();
             String jname   = tz.nextToken().trim();
             String user    = tz.nextToken().trim();
             String usetime = tz.nextToken().trim();
             String status  = tz.nextToken().trim();
             if ( status.equals("R"))  { 
               voMetrics.updateTotals(user,"Total Jobs");
               voMetrics.updateTotals(user,"Running Jobs");
             }
             else if ( status.equals("C"))  { 
               voMetrics.updateTotals(user,"Total Jobs");
               voMetrics.updateTotals(user,"Running Jobs");
             }
             else if ( status.equals("E"))  { 
               voMetrics.updateTotals(user,"Total Jobs");
               voMetrics.updateTotals(user,"Running Jobs");
             }
             else if ( status.equals("H"))  { 
               voMetrics.updateTotals(user,"Total Jobs");
               voMetrics.updateTotals(user,"Held Jobs");
             }
             else if ( status.equals("Q"))  { 
               voMetrics.updateTotals(user,"Total Jobs");
               voMetrics.updateTotals(user,"Idle Jobs");
             }
             else if ( status.equals("T"))  { 
               voMetrics.updateTotals(user,"Total Jobs");
               voMetrics.updateTotals(user,"Idle Jobs");
             }
             else if ( status.equals("I"))  { 
               voMetrics.updateTotals(user,"Total Jobs");
               voMetrics.updateTotals(user,"Idle Jobs");
             }
             else if ( status.equals("W"))  { 
               voMetrics.updateTotals(user,"Total Jobs");
               voMetrics.updateTotals(user,"Idle Jobs");
             }
             else if ( status.equals("S"))  { 
               voMetrics.updateTotals(user,"Total Jobs");
               voMetrics.updateTotals(user,"Running Jobs");
             }
           } // end if > 4
       } // end of for
  } catch ( Exception e ) {
            logit("Exception in Parsing PBS output  Ex=" +e) ;
           throw e;
    }
}

// ====== Parse LSF Ouput ==========================
void  ParseLSFOutput (  BufferedReader buff ) throws Exception {
  //--------------------------------------------------------------------
  // Job states:
  //  PEND  - Job is pending, not yet started.
  //  PSUSP - Job is suspended while pending.
  //  RUN   - Job is running.
  //  USUSP - Job is suspended while running.
  //  SSUSP - Job is suspended due to load or job queue closed
  //  DONE  - Job has terminated with status of 0.
  //  EXIT  - Job has termianted with non-zero status.
  //  UNKWN - Job is lost
  //  WAIT  - Job is waiting for its execution time
  //  ZOMBI - Job is will becopme a zombie for a couple reasons.

  StringTokenizer tz;

  try {
    for ( ; ; ) {
       String lin = buff.readLine();
       debug("line: " + lin );
       // --- end of file ----
       if ( lin == null )  break;
       //if ( lin.equals("") ) break;

       //Find the specific fields so we can substring the line
       //JOBID  USER    STAT QUEUE  FROM_HOST   EXEC_HOST   JOB_NAME   SUBMIT_TIME
       //262115 vacavan RUN  medium pdsflx003.n pdsflx278.n *u0i0a-033 Oct 20 16:17 
       //
       tz = new StringTokenizer ( lin ) ;
       int ni = tz.countTokens();
       if ( ni > 4 )  {
         String pid     = tz.nextToken().trim();
         String user    = tz.nextToken().trim();
         String status  = tz.nextToken().trim();

         if ( status.equals("PEND"))  { 
               voMetrics.updateTotals(user,"Total Jobs");
               voMetrics.updateTotals(user,"Idle Jobs");
             }
         if ( status.equals("PSUSP")) { 
               voMetrics.updateTotals(user,"Total Jobs");
               voMetrics.updateTotals(user,"Held Jobs");
             }
         if ( status.equals("RUN"))   { 
               voMetrics.updateTotals(user,"Total Jobs");
               voMetrics.updateTotals(user,"Running Jobs");
             }
         if ( status.equals("USUSP")) { 
               voMetrics.updateTotals(user,"Total Jobs");
               voMetrics.updateTotals(user,"Held Jobs");
             }
         if ( status.equals("SSUSP")) { 
               voMetrics.updateTotals(user,"Total Jobs");
               voMetrics.updateTotals(user,"Held Jobs");
             }
         if ( status.equals("USUSP")) { 
               voMetrics.updateTotals(user,"Total Jobs");
               voMetrics.updateTotals(user,"Held Jobs");
             }
         if ( status.equals("UNKWN")) { 
               voMetrics.updateTotals(user,"Total Jobs");
               voMetrics.updateTotals(user,"Idle Jobs");
             }
         if ( status.equals("WAIT"))  { 
               voMetrics.updateTotals(user,"Total Jobs");
               voMetrics.updateTotals(user,"Idle Jobs");
             }
         if ( status.equals("ZOMBI")) { 
               voMetrics.updateTotals(user,"Total Jobs");
               voMetrics.updateTotals(user,"Idle Jobs");
             }
       } // end if > 4
    } // end of for
  } catch ( Exception e ) {
    logit("Exception in Parsing LSF output  Ex=" +e) ;
           throw e;
    } 
}

// ====== Parse parseGatekeeper.py Output ==========================
void  ParseGateKeeperOutput (  BufferedReader buff ) throws Exception {
  /*
     The parseGatekeeper.py program generates totals by unix user in 
     a whitespace delimited format:
      unix_user total_submission failed_submissions pings failed_jobs
      sdss 6 0 0 0
      usatlas1 267 0 57 0
      sekhri 5 0 0 0
      uscms01 244 0 43 0
    
      The final metric "Job Success Effiecency" is a derived value after
      all the Totals by VO have been performed so it will be calculated 
      after all the lines have been processed. 
  */

  StringTokenizer tz;
  String value = null;
  Double d = new Double(0);

  try {
    for ( ; ; ) {
       String lin = buff.readLine();
       debug("line: " + lin );
       // --- end of file ----
       if ( lin == null )  break;

       //-----------------------------------------------------------------
       //  unix_user total_submission failed_submissions pings failed_jobs
       //  sdss 6 0 0 0
       //-----------------------------------------------------------------
       tz = new StringTokenizer ( lin ) ;
       int ni = tz.countTokens();
       if ( ni == 5 )  {
         String user = tz.nextToken().trim();
         value       = tz.nextToken().trim();
         voMetrics.updateTotals(user,"Total Submissions",value);
         value       = tz.nextToken().trim();
         voMetrics.updateTotals(user,"Failed Submissions",value);
         value       = tz.nextToken().trim();
         voMetrics.updateTotals(user,"PingOnly Submissions",value);
         value       = tz.nextToken().trim();
         voMetrics.updateTotals(user,"Failed Jobs",value);
       } // end if == 5
    } // end of for

    //-----------------------------------------------------------------
    //  Calculate the submission efficiency for the each VO
    //-----------------------------------------------------------------
    for (Enumeration vl = voMetrics.VoList(); vl.hasMoreElements();) {
      String vo = (String) vl.nextElement();
      double total  = voMetrics.getMetric(vo,"Total Submissions").doubleValue();
      double failed = voMetrics.getMetric(vo,"Failed Jobs").doubleValue();
      double efficiency = new Double(100).doubleValue();
      if ( total > 0 ) {
        efficiency = (( total - failed) / total ) * (double) 100.0;
      }
      String eff = d.toString(efficiency);
      voMetrics.updateVoTotals(vo,"Job Success Efficiency", (String) eff); 
      
    }
  
    
  } catch ( Exception e ) {
    logit("Exception in Parsing LSF output  Ex=" +e) ;
           throw e;
    } 
}

 


// --------------------------------------------------
static public void main ( String [] args ) {
  String host = args[0] ;
  VoJobs aa = new VoJobs();
  String ad = null ;

  try {
    ad = InetAddress.getByName( host ).getHostAddress();
  } catch ( Exception e ) {
    System.out.println ( " Cannot get ip for node " + e );
    System.exit(-1);
  }

  MonModuleInfo info = aa.init( new MNode (args[0] ,ad,  null, null), "debug");
  //MonModuleInfo info = aa.init( new MNode (args[0] ,ad,  null, null), null);

  try {
    Object bb = aa.doProcess();
    System.out.println ( (Result) bb );
  } catch (Exception e) {
    System.out.println ( "ERROR: "+e );
     
  }


}
 
}


