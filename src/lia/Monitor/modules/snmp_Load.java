package lia.Monitor.modules;

import java.net.InetAddress;
import java.util.Vector;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.snmpMon;
import lia.util.StringFactory;
import lia.util.ntp.NTPDate;

import org.opennms.protocols.snmp.SnmpOctetString;
import org.opennms.protocols.snmp.SnmpSyntax;
import org.opennms.protocols.snmp.SnmpVarBind;

public class snmp_Load extends snmpMon implements MonitoringModule  {
static public String ModuleName="snmp_Load";

static public String [] ResTypes = { "Load5", "Load10", "Load15"};
static String sOid = ".1.3.6.1.4.1.2021.10.1.3";
static public String OsName = "*";

public snmp_Load () { 
  super( sOid, ModuleName);
  info.ResTypes = ResTypes;
  info.name = ModuleName ;
}


public String[] ResTypes () { return ResTypes; }
public String getOsName() { return OsName; }

//Default canSuspend, but there are cases ( DC04 Filter )
public boolean canSuspend() { 
    boolean canS = true;
    try {
        canS = Boolean.valueOf(AppConfig.getProperty("lia.Monitor.modules.snmp_Load.canSuspend","true")).booleanValue();
    }catch(Throwable t1){
        canS = true;
    }
    return canS; 
}
         
public Object   doProcess() throws Exception {
       Vector res = results();
// parse the output !!  should be done better !
       if ( res.size() != 3 )  {
         throw new Exception ( " snmp Load failed " );
       }

       Result result  = new Result ( Node.getFarmName(), Node.getClusterName(),Node.getName(), ModuleName, ResTypes );
       result.time =  NTPDate.currentTimeMillis();

       for ( int i=0; i < res.size() ; i ++ ) {
          SnmpVarBind vb =  (SnmpVarBind )  res.elementAt(i);
          SnmpSyntax ss1 = ( SnmpSyntax ) vb.getValue();
          if ( ss1 instanceof  SnmpOctetString ) {
             SnmpOctetString ocst = ( SnmpOctetString )  ss1 ;
             String value =  StringFactory.get(ocst.getString() );
             double dvalue = Double.valueOf(value).doubleValue();
             result.param[i] = dvalue;
          }
       }


       return result;
}


static public void main ( String [] args ) {
  String host = args[0] ;
  snmp_Load aa = new snmp_Load();
  String ad = null ;
  try {
    ad = InetAddress.getByName( host ).getHostAddress();
  } catch ( Exception e ) {
    System.out.println ( " Can not get ip for node " + e );
    System.exit(-1);
  }

  MonModuleInfo info = aa.init( new MNode (args[0] ,ad,  null, null), null);

 try { 
  Object bb = aa.doProcess();
  Thread th = new Thread();
//  th.sleep( 30000 ) ;
	Thread.sleep( 30000 ) ;

  Result  cc = (Result) aa.doProcess();
 System.out.println ( cc );
 } catch ( Exception e ) { }
 // System.out.println ( "\n\n" + cc.toTonyFormat() );

}
    
 
}


