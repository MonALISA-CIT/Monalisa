package lia.Monitor.modules;

import java.net.InetAddress;
import java.util.Date;
import java.util.Vector;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.snmpMon;
import lia.util.StringFactory;
import lia.util.ntp.NTPDate;

import org.opennms.protocols.snmp.SnmpObjectId;
import org.opennms.protocols.snmp.SnmpOctetString;
import org.opennms.protocols.snmp.SnmpSyntax;
import org.opennms.protocols.snmp.SnmpVarBind;

public class snmp_DiskIO extends snmpMon implements MonitoringModule  {
static public String ModuleName="snmp_Disk";

static public String [] ResTypes = { "DiskIORead", "DiskIOWrite"};
static String sOid = ".1.3.6.1.4.1.2021.8.332.101";
static public String OsName = "*";

static SnmpObjectId read=new SnmpObjectId (".1.3.6.1.4.1.2021.8.332.101.1");  
static SnmpObjectId write=new SnmpObjectId (".1.3.6.1.4.1.2021.8.332.101.2");
long[] oldRes = null; 
long lTime = 0;
long cTime = 0;

public snmp_DiskIO () { 
  super( sOid, ModuleName);
  oldRes = new long[ResTypes.length];
  oldRes[0] = 0;
  oldRes[1] = 0;
  info.ResTypes = ResTypes;
  info.name = ModuleName ;
}


public String[] ResTypes () { return ResTypes; }
public String getOsName() { return OsName; }


public Object   doProcess() throws Exception {
       Vector res = results();
       cTime = NTPDate.currentTimeMillis();
       
       long cRead = 0;
       long cWrite = 0;
       
       for ( int i=0; i < res.size() ; i ++ ) {
          SnmpVarBind vb =  (SnmpVarBind )  res.elementAt(i);
          SnmpObjectId idx = vb.getName();
          SnmpSyntax ss1 = ( SnmpSyntax ) vb.getValue();
          if ( read.isRootOf (idx ) ) {
            SnmpOctetString ocst = ( SnmpOctetString )  ss1 ;
            String value =  StringFactory.get( ocst.getString() );
            cRead = Long.valueOf(value).longValue();
          }
          
          if ( write.isRootOf (idx ) ) {
              SnmpOctetString ocst = ( SnmpOctetString )  ss1 ;
              String value =  StringFactory.get ( ocst.getString() );
              cWrite = Long.valueOf(value).longValue();
          }

       }
       
       if ( cRead == 0 || cWrite == 0 ) {
       	       System.out.println(new Date() +  
		" >>>>>>>>>>>> [ " + Node.getName() + "]DiskIO_Debug newR: " + cRead + " newW: " + cWrite + "\n");
	       return null;
       }

       Result result  = null;
       
       if (lTime != 0) {
           
           //machine restart or overflow counter ( ignore the result ) 
           if ( cRead < oldRes[0] || cWrite < oldRes[1] ) {
               oldRes[0] = cRead;
               oldRes[1] = cWrite;
               lTime = cTime;
               return result;
           }

           double tmp = 1024D*(double)(cTime - lTime);

           result  = new Result ( Node.getFarmName(), Node.getClusterName(),Node.getName(), ModuleName, ResTypes );
           result.time =  cTime;
           
           result.param[0] = diffUnsignedInt(cRead, oldRes[0]);
           result.param[0] = (result.param[0]/tmp)*512D;
           
           result.param[1] = diffUnsignedInt(cWrite, oldRes[1]);
           result.param[1] = (result.param[1]/tmp)*512D;
       }

       System.out.println(new Date() +  " DiskIO_Debug oldR: " + oldRes[0] + " newR: " + cRead + "\n" +
		       " oldW: " + oldRes[1] + " newW: " + cWrite + "\n" +
		       " lTime: " + new Date(lTime) + " cTime: " + new Date(cTime) + "\n" +
		       " Result = \n" + result + "\n\n");
       oldRes[0] = cRead;
       oldRes[1] = cWrite;
       lTime = cTime;
       return result;
}

private long diffUnsignedInt(long newValue, long oldValue) {
    if (newValue >= oldValue) return (newValue - oldValue);
    return 0;

    // This is not good if the machine was restarted!!!
//    return (2L*(long)Integer.MAX_VALUE)-oldValue+newValue;
}


static public void main ( String [] args ) {
  String host = args[0] ;
  snmp_DiskIO aa = new snmp_DiskIO();
  String ad = null ;
  try {
    ad = InetAddress.getByName( host ).getHostAddress();
  } catch ( Exception e ) {
    System.out.println ( " Can not get ip for node " + e );
    System.exit(-1);
  }

  MonModuleInfo info = aa.init( new MNode (args[0] ,ad,  null, null), null);

 try { 
  for(;;) {
      Result  cc = (Result) aa.doProcess();
      System.out.println ( cc );
      try{
          Thread.sleep(10*1000);
      }catch( Exception ex ){
          
      }
  }
 } catch ( Exception e ) { }

}
    
 
}


