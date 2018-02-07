import lia.Monitor.monitor.*;

import java.io.*;
import java.util.*;
import java.net.InetAddress;


public class PBSjobs extends cmdExec implements  MonitoringModule {



String[] tmetric;
String [] ijobs;
  
boolean filter_jobs; 
String  cmd ="qstat";
//String  cmd=  "/usr/pbs/bin/qstat " ;

String args;


public PBSjobs  () { 
   super( "PBSjobs");
   info.ResTypes = tmetric;
   System.out.println ( "Start the Interface to  PBS jobs " );
   filter_jobs=false;
   isRepetitive = true;
}

public  MonModuleInfo init( MNode Node , String args ) {
    this.Node = Node;

    System.out.println ( "Start the PBS module with args="+ args );

    if ( args != null ) { 
      StringTokenizer tz = new StringTokenizer(args,",");
      int k = tz.countTokens();
      tmetric = new String [k*2];
      ijobs = new String[k];
      filter_jobs= true;
      for ( int j=0; j < k; j ++ ) {
        String jname = tz.nextToken().trim();
        tmetric[2*j] = "Running_"+ jname;
        tmetric[2*j+1] = "Pending_"+jname;
        ijobs[j] = jname;
      }
    } 

    if ( filter_jobs == false ) {
         tmetric = new String [2];
         tmetric[0] = "Running" ;
         tmetric[1] = "Pending" ;
    }


    info.ResTypes = tmetric;
    this.args = args;
    return info;
}


public Object   doProcess() throws Exception  {
   BufferedReader buff1 = procOutput ( cmd );
  
  if ( buff1  == null ) {
    System.out.println ( " Failed  to get the  PBS output (CMD="+cmd+")" );
    throw new Exception ( " PBS  output  is null for " + Node.name);
  }

  return Parse(buff1);
}


public Result   Parse (  BufferedReader buff )  throws Exception  {

  Result rr = null ;
  rr = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(), null, tmetric );
    
  String lin;
  StringTokenizer tz;
  rr.time = (new Date()).getTime();

  try {
          lin = buff.readLine(); 
	  lin = buff.readLine();

        for ( ; ; ) {
             lin = buff.readLine();
             System.out.println ( lin);
             if ( lin == null ) break;
	     if ( lin.equals("") ) break;
             tz = new StringTokenizer ( lin ) ;
             int ni = tz.countTokens();
             if ( ni > 4 )  { 
               String pid = tz.nextToken().trim();
               String jname = tz.nextToken().trim();
               String user = tz.nextToken().trim();
               String usetime = tz.nextToken().trim();
               String status = tz.nextToken().trim();

               if ( filter_jobs ) {
                  for ( int l=0; l < ijobs.length; l++ ) {
                      if ( jname.indexOf( ijobs[l]) != -1 ) {
                         if ( status.equals("R"))  {
                            rr.param[2*l]++;
                         }
                         if ( status.equals("Q"))  {
                            rr.param[2*l+1]++;
                         }
                         break;
                      }
                  }
                } else { 
                   if ( status.equals("R"))  {
                      rr.param[0]++;
                   }
                   if ( status.equals("Q"))  {
                      rr.param[1]++;
                   }
                }


             }
        }
	buff.close();
	if ( pro != null ) pro.destroy();

     } catch ( Exception e ) { 
        System.out.println ( "Exeption in Parsing PBS output  Ex=" + e ); 
        throw e;
     }
  //System.out.println ( " PBS result  ---> " +rr );
  return rr;

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
  PBSjobs aa = new PBSjobs (); 
  String filter = null;
  String ad = null ;
  System.out.println ( " Ruuning the test PBS module " );
  try {
    ad = InetAddress.getByName( host ).getHostAddress();
  } catch ( Exception e ) {
    System.out.println ( " Can not get ip for node " + e );
    System.exit(-1);
  }

  if ( (args != null ) && (args.length >0 )) {
   filter = args[0] ;
  } else {
   filter = null ;
  }
  MonModuleInfo info = aa.init( new MNode (host ,ad,  null, null), filter);



 try  {
  Object bb = aa.doProcess();
  Result br = ( Result) bb;
  System.out.println ( br);
 } catch ( Exception e ) {
   System.out.println ( " failed to run the PBS module " +e );
 }



}
    
 
}


