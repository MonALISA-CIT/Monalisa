package lia.Monitor.modules;

import java.io.BufferedReader;
import java.net.InetAddress;
import java.util.StringTokenizer;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.util.ntp.NTPDate;


public class monProcStat1 extends cmdExec implements MonitoringModule  {
static public String ModuleName="monProcStat1";
static public String[]  ResTypes = {"Cpu_usr", "Cpu_sys", "Cpu_idle" };

static public String Cmd1 = " cat /proc/stat ";
static public String OsName = "linux";
boolean debug;
long sin_old, sout_old;
long[] old = new long[8];
long[] cur = new long[8];

public monProcStat1 () { 
  super(  ModuleName);
  info.ResTypes = ResTypes;
  info.name = ModuleName ;
  isRepetitive = true;
  debug = Boolean.valueOf(AppConfig.getProperty("lia.module.debug","false")).booleanValue();

}


public String[] ResTypes () {
  return ResTypes;  
}
public String getOsName() { return OsName; }

public Object  doProcess() throws Exception  {
  Result res  = new Result ( Node.getFarmName(),Node.getClusterName(),Node.getName(), ModuleName, ResTypes );

 
  BufferedReader buff = procOutput ( Cmd1 );
  
  if ( buff  == null ) {
    System.out.println ( " Failed  for " + Cmd1 );
    cleanup();
    throw new Exception ( " ProcStat output is null for " + Node.name);
  }


  return process_stat_Ker24(buff); 

  
}

Result  process_stat_Ker24( BufferedReader br ) throws Exception {
   Result res  = new Result ( Node.getFarmName(),Node.getClusterName(),Node.getName(), ModuleName, ResTypes );
   String tmp;

    String lin = br.readLine();
    StringTokenizer tz = new StringTokenizer ( lin.substring(4) );

    cur[0] =  ( new Long ( tz.nextToken() ) ).longValue() ;
    cur[1] =  ( new Long ( tz.nextToken() ) ).longValue() ;
    cur[2] =  ( new Long ( tz.nextToken() ) ).longValue() ;
    cur[3] =  ( new Long ( tz.nextToken() ) ).longValue() ;

    long s1 = cur[0] - old[0] + cur[1] - old[1];
    long s2 = cur[2] - old[2] + cur[3] - old[3];
    long sum = s1 + s2;

    res.time = NTPDate.currentTimeMillis();

    res.param[0] = (double) ( s1 ) / (double) ( sum ) ;
    res.param[1] = (double) ( cur[2] -old[2] ) / (double)( sum );
    res.param[2] = (double) ( cur[3] -old[3] ) / (double)( sum);

    cleanup();
    return res;
}

public MonModuleInfo getInfo(){
        return info;
}


static public void main ( String [] args ) {
  String host = "localhost" ; //args[0] ;
  monProcStat1 aa = new monProcStat1();
  String ad = null ;
  try {
    ad = InetAddress.getByName( host ).getHostAddress();
  } catch ( Exception e ) {
    System.out.println ( " Can not get ip for node " + e );
    System.exit(-1);
  }

  MonModuleInfo info = aa.init( new MNode (host ,ad,  null, null), null, null);

  try { 
     Object cb = aa.doProcess();

     Thread.sleep(3000);
     Object bb = aa.doProcess();

      System.out.println ( (Result) bb );
  } catch ( Exception e ) { ; }


}
    
 
}


