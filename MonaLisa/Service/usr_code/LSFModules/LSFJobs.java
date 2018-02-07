import lia.Monitor.monitor.*;

import java.io.*;
import java.util.*;
import java.net.InetAddress;


public class LSFJobs extends cmdExec implements  MonitoringModule {


static String[] tmetric={"Running","Pending","Done/h","Exit/h"}; 
  
String cmd ;
String args;


public LSFJobs  () { 
   super( "LSFJobs");
   info.ResTypes = tmetric;
   System.out.println ( "Start the Interface to  LSF jobs " );
   isRepetitive = true;
}

public  MonModuleInfo init( MNode Node , String args ) {
    this.Node = Node;
    info.ResTypes = tmetric;
    this.args = args;
/*  agrs is not used.  It may be used to pass certain parameters to the module 
    for selecting / flitering the types of jobs for which the module will 
    report values
*/ 

/* 
    the bjobs command rns on a remote host using ssh and cerificate autheentification 
    the additinal echos lines are uded to force ssh connection to close. 
    (looks a a bug in versions of ssh were sometings these connctions are not closed" 
*/ 
    cmd=  "ssh -n -2 -x -t -C  cil@lxplus  ' /usr/local/lsf/bin/bjobs -a -u all ; echo XGATA ; echo $HOST' ~.";
//    normal CMD 
//    cmd = "/usr/local/lsf/bin/bjobs -a -u all " ;

    System.out.println ( " CMD   = " + cmd ) ;
    System.out.println ( " ARGS  = " + args );

    return info;
}


public Object   doProcess() throws Exception  {
  BufferedReader buff1 = procOutput ( cmd );
  
  if ( buff1  == null ) {
    System.out.println ( " Failed  to get the  LSF output " );
    if ( pro != null ) pro.destroy();
    throw new Exception ( " LSF load  output  is null for " + Node.name);
  }


  return Parse(buff1);
}


public Vector   Parse (  BufferedReader buff )  throws Exception  {

  Result rr = null ;
  rr = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(), null, tmetric );
    
  String lin;
  StringTokenizer tz;
  rr.time = (new Date()).getTime();

  try {
          lin = buff.readLine(); 
	  lin = buff.readLine();
	  lin = buff.readLine();

        for ( ; ; ) {
             lin = buff.readLine();
             if ( lin == null ) break;
	     if ( lin.equals("") ) break;
	     if ( lin.equals("XGATA") ) break;
             tz = new StringTokenizer ( lin ) ;
             int ni = tz.countTokens();
             if ( ni > 4 )  { 
               String pid = tz.nextToken().trim();
               String user = tz.nextToken().trim();
               String status = tz.nextToken().trim();
               String queue = tz.nextToken().trim();
               String ohost = tz.nextToken().trim();

	       if ( status.equals("RUN")) rr.param[0]++;
	       if ( status.equals("PEND")) rr.param[1]++;;
               if ( status.equals ("DONE")) rr.param[2]++; 
	       if ( status.equals ("EXIT")) rr.param[3]++;
             }
        }
	buff.close();
//	System.out.println ( " Close Buffer " ); 
	if ( pro != null ) pro.destroy();

             
             
     } catch ( Exception e ) { 
        if ( pro != null ) pro.destroy();
        System.out.println ( "Exception in Parsing LSF output  Ex=" + e ); 
        throw e;
     }
//  System.out.println ( " ---> " +rr );
    Vector vres = new Vector();
    vres.add (rr );

  return vres;

}

public MonModuleInfo getInfo(){
        return info;
}
public String[] ResTypes () {
  return tmetric;
}
public String getOsName() { return "linux"; }


static public void main ( String [] args ) {

  String host = "localhost" ;
  LSFJobs aa = new LSFJobs (); 
  String ad = null ;
  try {
    ad = InetAddress.getByName( host ).getHostAddress();
  } catch ( Exception e ) {
    System.out.println ( " Can not get ip for node " + e );
    System.exit(-1);
  }

  MonModuleInfo info = aa.init( new MNode (host ,ad,  null, null), "no_args");



 try  {
   Object bb = aa.doProcess();
   if ( bb instanceof Result ) { 
     System.out.println ( " R ->" + (Result)bb ) ;
   }
 } catch ( Exception e ) {
   System.out.println ( " failed to process " );
 }



}
    
 
}


