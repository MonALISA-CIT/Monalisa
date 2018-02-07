package lia.Monitor.modules;

import java.net.InetAddress;
import java.util.StringTokenizer;
import java.util.Vector;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.snmpMon;
import lia.util.ntp.NTPDate;

import org.opennms.protocols.snmp.SnmpCounter32;
import org.opennms.protocols.snmp.SnmpObjectId;
import org.opennms.protocols.snmp.SnmpSyntax;
import org.opennms.protocols.snmp.SnmpVarBind;

public class snmp_IOp extends snmpMon implements MonitoringModule  {
static public String ModuleName = "snmp_IOp";
static String[] sOid = {".1.3.6.1.2.1.2.2.1.10", ".1.3.6.1.2.1.2.2.1.16"};
static public String OsName = "*";
String[] types; 
int[] sports;


long last_measured = -1;
long [][] contrs;
long [][] old_contrs;
long [][] tmp;
int ports =0;
boolean debug = false;



public snmp_IOp () {
    super( sOid, ModuleName);
    info.setName(ModuleName);
}

public  MonModuleInfo init( MNode Node , String arg) {
    this.Node = Node;
    NR=sOid.length;
   // this.sOid = sOid;
    init_args ( arg );
    info = new MonModuleInfo ();

    info.ResTypes = types;
    return info;
}


void init_args ( String list ) {
// System.out.println ( " INIT snmpIOp LIST= " + list );
 StringTokenizer tz = new StringTokenizer( list, ";" ) ;
 int Nargs = tz.countTokens() ;

 types = new String[2*Nargs];
 sports = new int[Nargs];
 
 int k =0 ; 
 while ( tz.hasMoreTokens()  ) {
   String ss = tz.nextToken();
   int i1 = ss.indexOf( "=" );
   int nport =  ( Integer.valueOf( ss.substring(0,i1).trim() )).intValue();
   String pname = ss.substring ( i1+1 ).trim();
 //  System.out.println ( " INIT snmpIOp pNAME = " +  pname );
   types[2*k] = pname + "_IN";
   types[2*k+1] = pname + "_OUT";
   sports[k++] = nport ;
 }
}    


public String[] ResTypes () {
  return types;
}
public String getOsName() { return OsName; }


         

public Object   doProcess() throws Exception {
      Vector[] res = mresults();

      if ( (last_measured > 0 ) && (res.length != 2) ) {
        return null;
      }

      if ( res[0] == null || res[1] == null ) {
          throw new Exception(" got null result in snmp_IO");
      }
      
      if ( res[0].size() == 0 && res[1].size() == 0 ) {
          throw new Exception(" got 0 size result in snmp_IO");
      }
      
      if ( contrs == null ) contrs = new long[2][];

      for ( int m=0; m <2 ; m++ ) {
       for ( int i=0; i < res[m].size() ; i ++ ) {
          if ( contrs[m] == null ) { contrs[m] = new long [res[m].size()] ; }
          SnmpVarBind vb =  (SnmpVarBind )  res[m].elementAt(i);
          SnmpObjectId idx = vb.getName();
          //System.out.println ( " I=" + i + "   idx= " + idx );
          SnmpSyntax ss1 = ( SnmpSyntax ) vb.getValue();
          if ( ss1 instanceof  SnmpCounter32  ) {
             SnmpCounter32 ssc = (SnmpCounter32) ss1 ;
             long lc = ssc.getValue()  ;
             contrs[m][i] = lc;
          }
       }
      }

      if ( old_contrs == null ) {
       old_contrs = contrs;
       contrs = null;
       last_measured = NTPDate.currentTimeMillis();
       return null;
      }

      
       Result result  = new Result ( Node.getFarmName(), Node.getClusterName(), Node.getName(),  ModuleName, types );
   

    result.time = NTPDate.currentTimeMillis();

     for ( int m=0; m < 2 ; m++ ) {
        for ( int sl =0; sl<sports.length ; sl ++ ) {
          int j = sports[sl]; 
          long diff = contrs[m][j] - old_contrs[m][j];
          if ( diff  < 0 ) {
              diff = ( 2* (long)(Integer.MAX_VALUE) -  old_contrs[m][j] ) +  contrs[m][j] ;
           }

           if ( diff < 0 )//should not get here 
            diff=0; 
           long dt =  result.time - last_measured ;
		   result.param[2*sl + m] = (double) (diff * 8.0D) / 1000.0D /(double) dt;//Mbps
        }
      }


       last_measured =  result.time;
       info.setLastMeasurement(last_measured);
       tmp = old_contrs;
       old_contrs = contrs;
       contrs = tmp;
       return result;
}


static public void main ( String [] args ) throws Exception {
  String host = args[0] ;
  snmp_IOp aa = new snmp_IOp();
  String ad = null ;
  try {
    ad = InetAddress.getByName( host ).getHostAddress();
  } catch ( Exception e ) {
    System.out.println ( " Can not get ip for node " + e );
    System.exit(-1);
  }

  String arg = "1=Port1; 2=Port2; 7=Port7 ";
  MonModuleInfo info = aa.init( new MNode (args[0] ,ad, null, null), arg);
  Object bb = aa.doProcess();
  Thread th = new Thread();
  try {
    Thread.sleep( 30000 );
  } catch ( Exception e ) { }

  Result  cc = (Result) aa.doProcess();
  System.out.println ( cc );

}
 
}


