import lia.Monitor.monitor.*;

import java.io.*;
import java.util.*;
import java.net.InetAddress;

/**
  Interface to Ganglia using gmon 
*/

public class IGanglia extends cmdExec implements  MonitoringModule {

/** 
  The name of the monitoring parameters to be "extracted" from the Ganglia report 
*/

static String[] metric={"cpu_num","cpu_user","cpu_system","cpu_nice","bytes_in","bytes_out","load_five","load_one", "load_fifteen", "proc_run", "mem_free"}; 


/**
  Rename them into : 
*/

static String[] tmetric={"NoCPUs","CPU_usr","CPU_sys","CPU_nice", "TotalIO_Rate_IN", "TotalIO_Rate_OUT", "Load5","Load1","Load15", "proc_run", "MEM_free"}; 
  
String cmd ;
static public String ModuleName="IGanglia";

public IGanglia  () { 
   super( "IGanglia");
   info.ResTypes = tmetric;
   System.out.println ( "Start the Interface to  Ganglia " );
   isRepetitive = true;
}

public  MonModuleInfo init( MNode Node , String arg1) {
    System.out.println( " INIT GANGLIA MODULE " + arg1 );
    this.Node = Node;
    info.ResTypes = tmetric;
    String port = "8649" ;  // default Ganglia port 
    if ( arg1 != null )  port = arg1;

    cmd=  "telnet "+ Node.getIPaddress() + " "+ port ;

    System.out.println ( " The Ganglia CMD=" + cmd );

    return info;
}


public Object   doProcess()  throws Exception {

   BufferedReader buff1 = procOutput ( cmd );
  
  if ( buff1  == null ) {
    System.out.println ( " Failed  to get the the Ganglia output " );
    if (pro !=null )  pro.destroy();
    throw new Exception ( " Ganglia output  is null for " + Node.name);
  }

  return   Parse ( buff1 ) ;

}


public Vector  Parse (  BufferedReader buff )   throws Exception  {
  int i1, i2;
  Result rr = null ;
  Vector results = new Vector() ;

  try {
        for ( ; ; ) {
             String lin = buff.readLine();
             if ( lin == null ) break;
            // System.out.println(" " + lin );
              
             if ( lin.indexOf ("<HOST") != -1 ) {
               i1 = lin.indexOf ("=");
               i2 = lin.indexOf ("\"", i1+2 );
            
               rr = new Result() ; rr.NodeName = lin.substring( i1+2, i2 );
               rr.ClusterName = Node.getClusterName();
               rr.FarmName = Node.getFarmName(); 
	       rr.Module = ModuleName;
               i1 = lin.indexOf ( "ED=" );
               i2 = lin.indexOf ( "\"",i1+4);
               long time = (new Long ( lin.substring(i1+4,i2 ))).longValue();
               rr.time =  time * 1000;

             } else  {
               if ( lin.indexOf ("/HOST>") != -1 ) {
                 if ( rr != null ) results.add(rr);
               } else {


               for ( int l=0; l < metric.length ; l++ ) { 

                 if ( lin.indexOf (metric[l]) != -1 ) {
                 i1 = lin.indexOf ( "VAL=" ) ;
                 i2 = lin.indexOf ( "\"", i1 +5 ) ;
                 String sval = lin.substring(i1+5,i2 ) ;
                 double val = (new Double ( lin.substring(i1+5,i2 ))).doubleValue();
//               trasform IO measurments in mb/s !
                 if ( metric[l].indexOf( "bytes" ) != -1 ) { 
                    val = val*8/1000000.0;
                 }
//               converet memory units from KB in MB 
               if ( metric[l].indexOf( "mem" ) != -1 ) {
                    val = val/1000.0;
                 }

                 rr.addSet(tmetric[l], val );
               }
              }
             
             }
            

           }
        } 
        buff.close();
        if ( pro != null ) pro.destroy();

     } catch ( Exception e ) { 
       System.out.println ( "Exeption in Get Ganglia  Ex=" + e ); 
       buff.close();
       if ( pro != null ) pro.destroy();
       throw e;
     }

  //System.out.println ( results.size() );
  return results;

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

  IGanglia aa = new IGanglia ();
  MonModuleInfo info = aa.init( new MNode (host ,ad,  null, null),  null);


 try { 
  Object bb = aa.doProcess();

  if ( bb instanceof Vector ) {
    System.out.println ( " Received a Vector having " + ((Vector) bb).size() + " results" );
  }
 }  catch ( Exception e ) {
   System.out.println ( " failed to process " );
 }




}
    
 
}


