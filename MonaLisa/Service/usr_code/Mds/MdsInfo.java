import lia.Monitor.monitor.*;
import org.globus.mds.*;
import java.util.*;
import java.io.*;
import java.util.*;
import java.net.InetAddress;

public class MdsInfo extends cmdExec implements MonitoringModule  {

static public String ModuleName="MdsInfo";

static public String[]  ResTypes = {"condorQLength"};

static public String cpuUserInfo = "top -n 1";
static public String OsName = "linux";

//Constructor 
public MdsInfo() { 
  super(ModuleName);
  info.ResTypes = ResTypes;
  isRepetitive = true;
}

public String[] ResTypes () {
  return ResTypes;  
}

public String getOsName() { 
  return OsName; 
}

public MonModuleInfo getInfo(){
        return info;
}

public Object doProcess() {

  //Get the result 
  //BufferedReader buff1 = procOutput (cpuUserInfo);
  String buff1 = getMDS(); 
	
  if ( buff1  == null ) {
    System.out.println ( " Failed  for " + full_cmd );
    return null;
  }

  System.out.println("MDS="+buff1);

  //After querying, create a Result object to store the results
  Result result  = new Result ( Node.getFarmName(),Node.getClusterName(),Node.getName(), ModuleName, ResTypes );

  result.time = (new Date()).getTime();

  //Right now storing a dummy value
  result.param[0] = ( new Double(0.3)).doubleValue();
  return result;
}

public String getMDS(){
	String res="";

        MDS mds = new MDS("testulix.phys.ufl.edu", "2135");
        try{
                mds.connect();
                String bindDN ="Mds-Vo-name=local, o=Grid";
                Hashtable result;
                result = mds.search(bindDN,"(objectclass=MdsCpu)", MDS.ONELEVEL_SCOPE);
                System.out.println(result);
                mds.disconnect();
        }catch(MDSException e) {
                System.err.println( "Error:"+ e.getLdapMessage() );
        }

 	return "dummy";
}

static public void main ( String [] args ) {

  String host = args[0] ;
  MdsInfo aa = new MdsInfo();
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
