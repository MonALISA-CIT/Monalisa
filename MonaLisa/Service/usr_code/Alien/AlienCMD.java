import lia.Monitor.monitor.*;

import java.io.*;
import java.util.*;
import java.net.InetAddress;


public class AlienCMD extends cmdExec implements  MonitoringModule {

String[] tmetric;  // a dynamic array of tag elements 
int NR;
  
String cmd ;
String args;


public AlienCMD  () { 
   super( "AlienCMD");
   info.ResTypes = tmetric;
   System.out.println ( "Start the Interface to  the Alien CMD module " );
   isRepetitive = true;
}

public  MonModuleInfo init( MNode Node , String args ) {
    this.Node = Node;
    this.args = args;

    int ix = args.indexOf(";");
    if ( ix < 0 ) { 
      System.out.println ( " Input error in Alien CMD ... no tag / cmd delimiter ! " );
      return info;
    }

    String tags = args.substring ( 0, ix ) ;
    cmd  = args.substring ( ix+1).trim();

	 //  System.out.println ( " CMD=" + cmd );
 
      StringTokenizer tz = new StringTokenizer(tags,",");
      NR = tz.countTokens();
      tmetric = new String [NR];
      for ( int j=0; j < NR; j ++ ) {
        String tag = tz.nextToken().trim();
        tmetric[j] = tag;
		// System.out.println(" tmetric " + j + " tag : " + tag);
      }
    info.ResTypes = tmetric;
    return info;
}


public Object   doProcess() throws Exception  {
   BufferedReader buff1 = procOutput ( cmd );
  
  if ( buff1  == null ) {
    System.out.println ( " Failed  to get the  AlienCMD output " );
    throw new Exception ( " AlienCMD output buffer is null for " + Node.name);
  }


  return Parse(buff1);
}


public Vector Parse (  BufferedReader buff )  throws Exception  {

  Result rr = null ;
  Vector v = new Vector();
  rr = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(), null, tmetric );
    
  String lin;
  rr.time = (new Date()).getTime();

  try {
        for ( int i=0; i <NR; i++ ) {
             lin = buff.readLine();
             if ( lin == null ) break;
             if ( lin.equals("") ) break;
             String val = lin.trim();
             rr.param[i] = ( new Double(val)).doubleValue();
        }
	buff.close();
	if ( pro != null ) pro.destroy();
     } catch ( Exception e ) { 
        System.out.println ( "Exeption in Parsing AlienCMD output  Ex=" + e ); 
        throw e;
     }
     v.add(rr);
  // System.out.println ( " ---> " +rr );
  return v;

}

public MonModuleInfo getInfo(){
        return info;
}
public String[] ResTypes () {
  return tmetric;
}
public String getOsName() { return "linux"; }


static public void main ( String [] args ) {

  String host = "localhost" ;
  AlienCMD aa = new AlienCMD (); 
  String ad = null ;
  try {
    ad = InetAddress.getByName( host ).getHostAddress();
  } catch ( Exception e ) {
    System.out.println ( " Can not get ip for node " + e );
    System.exit(-1);
  }

  MonModuleInfo info = aa.init( new MNode (host ,ad,  null, null), "tag1,tag2;echo -e \"10 \\n 20\" ");



 try  {
  Object bb = aa.doProcess();
 } catch ( Exception e ) {
   System.out.println ( " failed to process " );
 }



}
    
 
}


