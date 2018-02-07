package lia.Monitor.modules;

import java.io.BufferedReader;
import java.net.InetAddress;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.cmdExec;
import lia.Monitor.monitor.eResult;
import lia.util.ntp.NTPDate;

public class monTraceroute extends cmdExec implements MonitoringModule  {
	static public String ModuleName="monTraceroute";
	static public String[]  ResTypes = {"route" };

	static public final String tracerouteCmd = "traceroute ";
	static public final String OsName = "linux";
//	  boolean debug;


	public monTraceroute () { 
	  super(  ModuleName );
	  info.ResTypes = ResTypes;
	  info.name = ModuleName ;
	  isRepetitive = true;
//		debug = Boolean.valueOf(AppConfig.getProperty("lia.ping.debug","false")).booleanValue();

	}


	public String[] ResTypes () {
	  return ResTypes;  
	}
	public String getOsName() { return OsName; }

	public Object  doProcess() throws Exception  {

	  String cmd1 = tracerouteCmd + " " +  Node.getIPaddress() ;
	  BufferedReader buff = procOutput ( cmd1 );
  
	  if ( buff  == null ) {
		cleanup();
		throw new Exception ( " ping output2 is null for " + Node.name );
	  }
	
	  String route = "";
	  
	  try {
	      route = getTracerouteOut( buff );
	  }catch(Throwable t){
	      cleanup();
	      throw new Exception(t);
	  }
	  

	  eResult eresult  = new eResult ( Node.getFarmName(),Node.getClusterName(),Node.getName(), ModuleName, ResTypes );
	
	  eresult.time =  NTPDate.currentTimeMillis();
	  eresult.param[0] = route;

	  cleanup();
	  return eresult;
	}

	public MonModuleInfo getInfo(){
			return info;
	}

	String  getTracerouteOut (  BufferedReader buff ) throws Exception {
 	StringBuilder sb = new StringBuilder();
	  try {
			for ( ; ; ) {
				 String lin = buff.readLine();
				 if ( lin == null ) break;
				 sb.append(lin);
				 sb.append("\n");
			}
		 } catch ( Throwable t ) {
			 throw new Exception(t);
		 }

	  return sb.toString();
	}

	static public void main ( String [] args ) {
	  String host = args[0] ;
	  monTraceroute aa = new monTraceroute();
	  String ad = null ;
	  try {
		ad = InetAddress.getByName( host ).getHostAddress();
	  } catch ( Exception e ) {
		System.out.println ( " Can not get ip for node " + e );
		System.exit(-1);
	  }

	  MonModuleInfo info = aa.init( new MNode (args[0] ,ad,  null, null), null, null);

	  try { 
		 Object bb = aa.doProcess();
		  System.out.println ( (eResult) bb );
	  } catch ( Exception e ) { ; }
	}
}
