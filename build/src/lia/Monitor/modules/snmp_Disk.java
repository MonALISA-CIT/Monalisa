package lia.Monitor.modules;

import java.net.InetAddress;
import java.util.Vector;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.snmpMon;
import lia.util.StringFactory;
import lia.util.ntp.NTPDate;

import org.opennms.protocols.snmp.SnmpInt32;
import org.opennms.protocols.snmp.SnmpObjectId;
import org.opennms.protocols.snmp.SnmpOctetString;
import org.opennms.protocols.snmp.SnmpSyntax;
import org.opennms.protocols.snmp.SnmpVarBind;

public class snmp_Disk extends snmpMon implements MonitoringModule  {
static public String ModuleName="snmp_Disk";

static public String [] ResTypes = { "FreeDsk", "UsedDsk"};
static String sOid = ".1.3.6.1.4.1.2021.9";
static public String OsName = "*";

static SnmpObjectId part=new SnmpObjectId (".1.3.6.1.4.1.2021.9.1.2");  
static SnmpObjectId free=new SnmpObjectId (".1.3.6.1.4.1.2021.9.1.7");
static SnmpObjectId used=new SnmpObjectId (".1.3.6.1.4.1.2021.9.1.8");


public snmp_Disk () { 
  super( sOid, ModuleName);
  info.ResTypes = ResTypes;
  info.name = ModuleName ;
}


public String[] ResTypes () { return ResTypes; }
public String getOsName() { return OsName; }


         

public Object   doProcess() throws Exception {
       Vector res = results();

       Result result  = new Result ( Node.getFarmName(), Node.getClusterName(),Node.getName(), ModuleName, ResTypes );
       result.time =  NTPDate.currentTimeMillis();

       double SumUsed = 0;
       double SumFree = 0;
       for ( int i=0; i < res.size() ; i ++ ) {
          SnmpVarBind vb =  (SnmpVarBind )  res.elementAt(i);
          SnmpObjectId idx = vb.getName();
          SnmpSyntax ss1 = ( SnmpSyntax ) vb.getValue();
          if ( part.isRootOf (idx ) ) {
            SnmpOctetString ocst = ( SnmpOctetString )  ss1 ;
            String value =  StringFactory.get( ocst.getString() );
          }
          if ( free.isRootOf (idx ) ) {
             SnmpInt32 ssc = (SnmpInt32) ss1 ;
             long lc = ssc.getValue()  ;
             SumFree += (double) lc /1000000 ;
          }
          if ( used.isRootOf (idx ) ) {
             SnmpInt32 ssc = (SnmpInt32) ss1 ;
             long lc = ssc.getValue()  ;
             SumUsed += (double) lc /1000000 ;
          }

       }

            
       result.param[0] = SumFree ;
       result.param[1] = SumUsed ;


       return result;
}


static public void main ( String [] args ) {
  String host = args[0] ;
  snmp_Disk aa = new snmp_Disk();
  String ad = null ;
  try {
    ad = InetAddress.getByName( host ).getHostAddress();
  } catch ( Exception e ) {
    System.out.println ( " Can not get ip for node " + e );
    System.exit(-1);
  }

  MonModuleInfo info = aa.init( new MNode (args[0] ,ad,  null, null), null);

 try { 
  Result  cc = (Result) aa.doProcess();
  System.out.println ( cc );
 } catch ( Exception e ) { }

}
    
 
}


