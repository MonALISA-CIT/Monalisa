
import lia.Monitor.monitor.*;

import java.io.*;
import java.util.*;
import java.net.InetAddress;


public class myPing extends cmdExec implements MonitoringModule  {
static public String ModuleName="myPing";
static public String[]  ResTypes = {"PingBandwidth", "RTT", "LostPackages" };

static public String pingCmd1 = "ping -c 5 -s 32 ";
static public String pingCmd2 = "ping -c 5 -s 2048 ";
static public String OsName = "linux";


public myPing () { 
  super(  ModuleName);
  info.ResTypes = ResTypes;
  isRepetitive = true;
}


public String[] ResTypes () {
  return ResTypes;  
}
public String getOsName() { return OsName; }
public Object   doProcess() {

  String cmd1 = pingCmd1 + " " +  Node.getIPaddress() ;
  String cmd2 = pingCmd2 + " " +  Node.getIPaddress() ;

  BufferedReader buff1 = procOutput ( cmd1 );
  
  if ( buff1  == null ) {
    System.out.println ( " Failed  for " + full_cmd );
    return null;
  }
  double[] rez1=  ParsePingOut ( buff1 );

   buff1 = procOutput ( cmd2 );

  if ( buff1  == null ) {
    System.out.println ( " Failed  for " + full_cmd );
    return null;
  }
  double[] rez2=  ParsePingOut ( buff1 );

  if ( (rez1[0] <0 ) || (rez2[0] <0 ) ) return null;
  if ( (rez1[1] <0 ) || (rez2[1] <0 ) ) return null;


  Result result  = new Result ( Node.getFarmName(),Node.getClusterName(),Node.getName(), ModuleName, ResTypes );

  result.time =  ( new Date()).getTime();
  result.param[2] = (rez1[1] + rez2[1] )/2;

  result.param[1] = rez1[0];

  double dt = rez2[0] - rez1[0] ;
  if ( dt > 2.0  ) {
      result.param[0] = 2012 /dt ; 
  } else {
       result.param[0] = 1000.0;
  }
  
  //System.out.println ( " Result = " + result );
  return result;
}

public MonModuleInfo getInfo(){
        return info;
}

double[]  ParsePingOut (  BufferedReader buff ) {
  String lost = null;
  String mean = null;
  double[] rez = new double[2];
  try {
        for ( ; ; ) {
             String lin = buff.readLine();
             if ( lin == null ) break;
             if (lin.indexOf ("min/avg" ) != -1 ) {
               int i1 = lin.indexOf("=");
               int i2 = lin.indexOf("/", i1 );
               int i3 = lin.indexOf("/", i2+1 );
               mean = lin.substring( i2+1, i3 );
               //System.out.println ( " Mean =" + mean);
             }
             if ( lin.indexOf (", ") != -1 ) {
                 int i1 = lin.indexOf(", "); 
                 int i2 = lin.indexOf(", ", i1 + 2 );
                 int i3 = lin.indexOf("%", i2 +1 );
                 lost = lin.substring( i2+1, i3 );
                 //System.out.println ( " Lost =" + lost);
             }
  //           System.out.println ( lin ) ;
        }
     } catch ( Exception e ) { ; }

   if ( mean != null )  rez[0] = ( new Double( mean)).doubleValue();
   else rez[0] = -1;
   if ( lost != null )  rez[1] =  ( new Double( lost)).doubleValue(); 
   else rez[1] = -1;

  return rez;

}

static public void main ( String [] args ) {
  String host = args[0] ;
  myPing aa = new myPing();
  String ad = null ;
  try {
    ad = InetAddress.getByName( host ).getHostAddress();
  } catch ( Exception e ) {
    System.out.println ( " Can not get ip for node " + e );
    System.exit(-1);
  }

  MonModuleInfo info = aa.init( new MNode (args[0] ,ad,  null, null), null, null);

  Object bb = aa.doProcess();

  System.out.println ( (Result) bb );


}
    
 
}


