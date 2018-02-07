import lia.Monitor.monitor.*;

import java.io.*;
import java.util.*;
import java.net.InetAddress;


public class NetData extends cmdExec implements  MonitoringModule {


static String[] tmetric={"iperf","bbcpdisk","bbcpmem","bbftp"}; 

Hashtable nodes;  
Hashtable parez;
Hashtable hosts;
String base_dir ;
String cmd ;
String args;
String year;
String month;
String day;
String cur_dir;
String cur_date ;
Calendar calen;




public NetData  () { 
   super( "NetData");
   info.ResTypes = tmetric;
   isRepetitive = true;
   nodes = new Hashtable();
   parez = new Hashtable();
   hosts = new Hashtable();
}

public  MonModuleInfo init( MNode Node , String args ) {
    this.Node = Node;
    info.ResTypes = tmetric;
    this.args = args;
    base_dir = args;
     

    System.out.println  ( "Net Data INITIAL  arg=" + args );
    return info;
}

public void cale () {
  calen = Calendar.getInstance();
  year = ""+calen.get (Calendar.YEAR) ;
  int mx = (calen.get(Calendar.MONTH )+1);
  if ( mx <=9 ) 
   month = "0"+ mx;
  else 
   month = ""+mx;
  int id = calen.get(Calendar.DAY_OF_MONTH);

  if ( id <=9 ) 
    day = "0"+ id;
  else 
    day = ""+id;
  cur_dir = year+"_"+month;
  cur_date =year+"_"+month+"_"+day ;

}


public Object   doProcess() throws Exception  {
   Vector results = new Vector();
   parez.clear();
   cale ();
   File f0 = new File ( base_dir ) ;
   if ( ! f0.isDirectory () ) { 
     System.out.println ( " Basic arg is not a directory " ) ; 
     return null;
   }

   String[]  l0 = f0.list();
   
   for ( int i=0; i < l0.length ; i ++ ) {
    // System.out.println ( " i=" + i + l0[i] );

     File fn = new File ( base_dir+"/"+ l0[i] ); 
     
     if ( fn.isDirectory () ) { 
       String[] ln = fn.list() ;
       
        for ( int j=0; j < ln.length ; j ++ ) {
           //System.out.println ( " i=" + i  + "   "+ ln[j] );
           if ( ln[j].indexOf( cur_dir ) != -1 ) {  

             File fmon= new File (base_dir+"/"+l0[i]+"/"+ln[j]);
             if ( fmon.isDirectory() ) {
                String[] lmon = fmon.list () ;
                for ( int k=0; k < lmon.length; k ++ ) {
                 if ( lmon[k].indexOf ( cur_date ) != -1 ) { 
                    String type =lmon[k].substring(0,(lmon[k].indexOf(".")));
                    File rf = new File ( base_dir+"/"+l0[i]+"/"+ln[j]+"/"+lmon[k] ); 
                    String lin = null;
                    BufferedReader in = null;
                    try { 
                       in = new BufferedReader ( new FileReader( rf ) );
                       for ( ; ; ) { 
                         String lin1 = in.readLine () ;
                         if ( lin1 == null ) break;
                         lin = lin1;
                       }
                    } catch ( Exception e ) { 
                         System.out.println ( " error reading " + lmon[k]);
                    } 
                    if ( in != null ) in.close();
                    
                    if ( lin != null ) { 
                         StringTokenizer tz = new StringTokenizer( lin ) ;
                         for ( int l=0;l <3; l++ ) tz.nextToken();
                         String ipaddr = tz.nextToken();
                         tz.nextToken();

                         String time = tz.nextToken();
                         for ( int l=0;l <2; l++ ) tz.nextToken();
                         String value = tz.nextToken();
                         String key = l0[i]+ type ;
                         if ( !nodes.containsKey( key  ) ) {
                           nodes.put ( key, "0" );
                         } 
                         String last_time = (String) nodes.get( key ) ; 
                         if ( !last_time.equals ( time ) ) { 

                         if (  !hosts.containsKey( ipaddr ) ) {
                           try { 
                             String nn = InetAddress.getByName(ipaddr).getHostName();
                             hosts.put ( ipaddr, nn );
                           } catch ( Exception e ) { 
                             System.out.println ( "Failed to get Name ip=" + ipaddr );
                             String nx = l0[i].substring( l0[i].indexOf(".")+1);
                             if ( l0[i].indexOf("2") != -1 ) nx +="-2";
                             hosts.put ( ipaddr , nx ) ; 
                           } 
                         }
                          String nnode = (String)  hosts.get(ipaddr );

                         // System.out.println ( " IP="+ ipaddr +    " NAME=" + nnode ); 
                          Result rr = null ;
                          if ( parez.containsKey ( nnode ) ) { 
                              rr = ( Result) parez.get (nnode );
                          } else {
                              rr = new Result(Node.getFarmName(), Node.getClusterName(), nnode, null, null) ;
                              //rr.time = 1000*(new Long(time)).longValue();
                                rr.time = (new Date()).getTime();
                              parez.put ( nnode, rr );
                          } 
                           
                           rr.addSet ( type, 0.008*(new Double(value)).doubleValue() ); //express results in Mbs
                           nodes.put(key, time);
                         }
                    }

                    
                 }

                }
             }
           }

        }

     }
      
   }

  for ( Enumeration e = parez.elements(); e.hasMoreElements(); ) {
     Result ss = ( Result) e.nextElement();
     //System.out.println ( " REZ=" + ss );
     results.add(ss );
  }
  //System.out.println ( " REZ SIZE = " + results.size() );
  return results;
  
}


public Vector  Parse (  BufferedReader buff )  throws Exception  {
  Result rr = null ;
  Vector results = new Vector() ;
  String lin;
  StringTokenizer tz;
  long t = (new Date()).getTime();

  try {
          lin = buff.readLine(); 
	  lin = buff.readLine();
	  lin = buff.readLine();

        for ( ; ; ) {
             lin = buff.readLine();
             if ( lin == null ) break;
	     if ( lin.equals("") ) break;
	     if ( lin.equals("GATA") ) break;
             tz = new StringTokenizer ( lin ) ;
             int ni = tz.countTokens();
             if ( ni > 4 )  { 
               String host = tz.nextToken().trim();
               String status = tz.nextToken().trim();
               String r15s = tz.nextToken().trim();
               String r1m = tz.nextToken().trim();
               String r15m = tz.nextToken().trim();
               String ut = tz.nextToken().trim(); 
	       if ( host.indexOf ( args ) != -1 ) {
               if ( status.equals("ok") ) { 
                 rr = new Result(Node.getFarmName(), Node.getClusterName(), host, null, tmetric );
                 rr.time =  t;
                 rr.param[0] = ( new Double( r1m)).doubleValue();
                 rr.param[1] =  ( new Double( r15m)).doubleValue(); 
                 results.add(rr);
//                System.out.println ( " &&&&&&&& ADD REz for " + host );
               }
	       }
             }
        }
	buff.close();
//	System.out.println ( " Close Buffer " ); 
	if ( pro != null ) pro.destroy();

             
             
     } catch ( Exception e ) { 
        System.out.println ( "Exeption in Parsing LSF output  Ex=" + e ); 
        throw e;
     }
  return results;

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
  NetData aa = new NetData (); 
  String ad = null ;
  try {
    ad = InetAddress.getByName( host ).getHostAddress();
  } catch ( Exception e ) {
    System.out.println ( " Can not get ip for node " + e );
    System.exit(-1);
  }

  String bdir = "/nfs/sulky27/g.net.iepm-bw/bandwidth-tests/hercules/csvdata/";
  MonModuleInfo info = aa.init( new MNode (host ,ad,  null, null), bdir);



 try  {
  Object bb = aa.doProcess();
 } catch ( Exception e ) {
   System.out.println ( " failed to process " );
 }



}
    
 
}


