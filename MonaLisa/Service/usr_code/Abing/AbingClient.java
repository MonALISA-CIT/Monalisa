import lia.Monitor.monitor.*;

import java.io.*;
import java.util.*;
import java.net.InetAddress;
import java.net.Socket;

/**

   Simple Interface to Abing
   The module takes a list of hosts as the argument and performs
   sequentially Abing measurements.  In the near future we will use the
   discovery mechanism to configure Abing measurements at the global level


   To Run the module :
   1)  set the correct path to abing client program  in this module ( cmd_base ) 
       A binary version is also in  this directory 
   2)  recompile the code with  ./comp 

   3)  edit ml.prportoes and add the path to this directory for dynamic loading 

      lia.Monitor.CLASSURLs=file:${MonaLisa_HOME}/Service/usr_code/Abing/

   4)  Add in your "farm.conf" file :
       *AbingBW {AbingClient,localhost, monalisa.cacr.caltech.edu,monalisa-starlight.cern.ch}%120
 
        The last argument is list (separated by ",") of destinations to monitor 
        Abing reflector need to run on these sites 
        Abing reflector is an "embedded" application in ML and can be started automatically by the ML service
*/

public class AbingClient extends cmdExec implements  MonitoringModule {

/** 
  The name of the monitoring parameters to be "extracted" from the Abing report 
*/

static String[] tmetric={"BW_to", "BW_from"}; 

String ListHosts[];
String host;
  
String cmd_base= "/home/ramiro/WORK/MSRC/MonaLisa/Service/usr_code/Abing/abing -n 5 -d ";


public AbingClient  () { 
   super( "AbingInt");
   info.ResTypes = tmetric;
   System.out.println ( "Start the Abing Module " );
   isRepetitive = true;
}

public  MonModuleInfo init( MNode Node , String arg1) {
    System.out.println( " INIT Abing Module " + arg1 );
    this.Node = Node;
    info.ResTypes = tmetric;
	
     
    if ( arg1 != null )   { 
      StringTokenizer tz = new StringTokenizer ( arg1, "," ); 
      int no = tz.countTokens(); 
      ListHosts = new String [no]; 	
      for ( int i=0; i< no; i++ ) { 
       String h1 = tz.nextToken();
       ListHosts[i] = h1.trim();
      }	
    }
	host = Node.getIPaddress();


    return info;
}


public Object   doProcess()  throws Exception {

   Vector results = new Vector();
   for ( int i=0; i < ListHosts.length ; i ++ ) { 
      String cmd = cmd_base + " " + ListHosts[i]; 
      
      System.out.println("cmd = " + cmd); 
      BufferedReader buff1 = procOutput ( cmd );
  
      if ( buff1  == null ) {
         System.out.println ( " Failed  to get the the Abing output " + ListHosts[i] );
         if (pro !=null )  pro.destroy();
         throw new Exception ( " Abing output is null for " + ListHosts[i]);
      }

      Result r1 = Parse ( buff1, ListHosts[i] ) ;
      if ( r1 != null ) {  
        results.add (r1);
      } 
    }

  return   results ;

}


public Result Parse (  BufferedReader buff , String toHost)   throws Exception  {
  int i1, i2;
  Result rr = null ;

  rr = new Result() ; rr.NodeName = toHost;

  rr = new Result( Node.getFarmName(), Node.getClusterName(), toHost, "AbingClient", tmetric );
  long time =  ( new Date()).getTime();
  rr.time =  time * 1000;

  try {
        for ( ; ; ) {
             String lin = buff.readLine();
             if ( lin == null ) break;
             System.out.println(" Lin = " + lin); 
             if ( lin.indexOf ( "ABW(Avg/Sdev)" ) != -1 ) {
               i1 = lin.indexOf ("To:");
               i2 = lin.indexOf ("/", i1+2 );
               String toBW = lin.substring( i1+4, i2 ) ; 
               
               i1 = lin.indexOf ("From:");
               i2 = lin.indexOf ("/", i1+2 );
               String fromBW =  lin.substring( i1+6, i2 ) ;

               rr.param[0] = (new Double ( toBW)).doubleValue();
               rr.param[1] = (new Double ( fromBW)).doubleValue();
               break;
              }

        } 
        buff.close();
        if ( pro != null ) pro.destroy();

     } catch ( Exception e ) { 
       System.out.println ( "Exeption in Abing  Ex=" ); 
       e.printStackTrace();
       buff.close();
       if ( pro != null ) pro.destroy();
       throw e;
     }

  //System.out.println ( rr );
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

  String host = "bigmac.fnal.gov" ;
  String ad = null ;
  try {
    ad = InetAddress.getByName( host ).getHostAddress();
  } catch ( Exception e ) {
    System.out.println ( " Can not get ip for node " + e );
    System.exit(-1);
  }

  AbingClient aa = new AbingClient ();
  MonModuleInfo info = aa.init( new MNode (host ,ad,  null, null), "alice01.rogrid.pub.ro,ui.rogrid.pub.ro");


 try { 
  Object bb = aa.doProcess();

  if ( bb instanceof Vector ) {
    Vector v = (Vector)bb;
    if (v != null){
       System.out.println ( " Received a Vector having " + v.size() + " results" );
       for ( int i=0; i<v.size(); i++) {
    	    System.out.println(v.elementAt(i));
       } 
           
    }
  }
 }  catch ( Exception e ) {
   System.out.println ( " failed to process " );
 }




}
    
 
}


