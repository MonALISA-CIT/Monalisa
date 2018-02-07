
import java.net.InetAddress;
import java.util.StringTokenizer;
import java.util.Vector;

import lia.Monitor.monitor.*;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;

public class monMcastGanglia extends cmdExec implements MonitoringModule {

public MNode Node;
public MonModuleInfo info ;

static public String ModuleName = "monMcastGaglia";
static public String OsName = "*";
String[] types; 
public boolean isRepetitive = false;

InetAddress gAddress = null;
int gPort = 8649;
McastGanglia mgth = null;
long last_measured = -1;
boolean debug = false;

public monMcastGanglia () {
    try {
        gAddress = InetAddress.getByName("239.2.11.71");
    } catch ( Exception e ){;}
    isRepetitive = true;
}

public MonModuleInfo init( MNode Node , String arg) {
    this.Node = Node;
    init_args ( arg );
    info = new MonModuleInfo ();
    isRepetitive = true;
    try {
        mgth = new McastGanglia( gAddress, gPort, Node, ModuleName );
    } catch ( Throwable t ) {
        t.printStackTrace();
    }
    info.ResTypes = types;
    return info;
}


void init_args ( String list ) {
 if ( list == null || list.length() == 0 )
    return;
 StringTokenizer tz = new StringTokenizer( list, ";" ) ;

 while ( tz.hasMoreTokens()  ) {
   String ss = tz.nextToken();
   if ( ss.indexOf("GangliaMcastAddress") != -1 ) {
       int eqPos = ss.indexOf("=");
       if ( eqPos != -1 && eqPos < ss.length() ) {
           try {
               gAddress =  InetAddress.getByName(ss.substring(eqPos+1, ss.length()).trim());
           } catch ( Exception ex ){}
       }
   } else if ( ss.indexOf("GangliaMcastPort") != -1 ) {
       int eqPos = ss.indexOf("=");
       if ( eqPos != -1 && eqPos < ss.length() ) {
           try {
               gPort =  Integer.valueOf(ss.substring(eqPos+1, ss.length()).trim()).intValue();
           } catch ( Exception ex ){}
       }
   }
 }

}


public String[] ResTypes () {
  return types;
}
public String getOsName() { return OsName; }


public MNode getNode () { return Node ; }
public String getClusterName () { return Node.getClusterName() ; }
public String getFarmName () {    return Node.getFarmName() ; }
public String getTaskName () {    return ModuleName ; }
public boolean isRepetitive() { return isRepetitive; }
public MonModuleInfo getInfo() { return info; }
         

public Object doProcess() {
    return mgth.getResults();
}


static public void main ( String [] args ) {
  String host = args[0] ;
  monMcastGanglia ga = new monMcastGanglia();
  String ad = null ;
  try {
    ad = InetAddress.getByName( host ).getHostAddress();
  } catch ( Exception e ) {
    System.out.println ( " Can not get ip for node " + e );
    System.exit(-1);
  }

  String arg = "GangliaMcastAddress=239.2.11.71; GangliaMcastPort=8649";
  MonModuleInfo info = ga.init( new MNode (args[0], null, null), arg );

  for(;;) {
      try {
        Thread.sleep( 30000 );
      } catch ( Exception e ) { }
    Vector  cc = (Vector) ga.doProcess();
    System.out.println ( "S: "  + ((cc == null)?0:cc.size()) );
  }

}
 
}
