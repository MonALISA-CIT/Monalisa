
import java.io.*;
import java.net.*;
import java.util.*;



import lia.Monitor.monitor.*;

// Writes results into a UDP Server
//

import lia.Monitor.monitor.Result;



public class UDPwriter extends Thread implements lia.Monitor.monitor.DataReceiver {
      DatagramSocket socket;
      String host;
      int port ;
      ObjectOutputStream out = null;
      ObjectInputStream in = null;
      public InetAddress iaHost ;
      DatagramPacket packet;
      Vector buffa;
      Vector buffb;
      boolean hasToRun;

public  UDPwriter () {
  this ( "pccit8.cern.ch", 7777 );
}

	
public UDPwriter ( String host, int port )  {
        this.host = host;
        this.port = port;
        try {
          iaHost =  InetAddress.getByName(host);
        } catch ( Exception e ) { 
          System.out.println ( " ERROR in finding host = " + host + "  " + e );
        }

        hasToRun= true;
        buffa = new Vector();
        try { 
          socket = new DatagramSocket();
        } catch ( Exception e ) {
          System.out.println ( " ERROR in creating a DS  " + e );
        }


        start();
}

public void addResult ( Result r ) {
     buffa.add (r ) ;
     return;
}

public void addResult ( eResult r ) {

}

public void updateConfig(lia.Monitor.monitor.MFarm f) {
// this method is called any time the configuration is changed 
//
}
public void run() {
  while ( hasToRun )  {
    try { sleep (1000) ; } catch ( Exception e ) {; }
    if ( buffa.size() > 0 )  {
       sendResults();
     }
  }
}


synchronized void sw_buff() {
 buffb = buffa;
 buffa = new Vector();
}

private void sendResults() {
  sw_buff() ;
  
  for ( int i=0; i < buffb.size() ; i++ ) {
    sendResult ( (Result) buffb.elementAt(i) );
  }
  buffb=null;

}

void sendResult ( Result rs ) {

 //String rsf = rs.toTonyFormat() + '\n';
  String rsf = rs.toString();
 byte[] rsb = rsf.getBytes();
 packet = new DatagramPacket( rsb, rsb.length, iaHost, port ) ; 
 if ( socket == null ) {
        try {
          socket = new DatagramSocket();
        } catch ( Exception e ) {
          System.out.println ( " ERROR in creating a DS  " + e );
        }
 }


 if ( socket != null ) { 
   try { 
//      System.out.println ( " host"+ host + " ih=" +iaHost + " port = " +port );
      socket.send( packet );
   }  catch ( Exception e1 ) {
      System.out.println ( " ERROR in sending the message  " + e1 );
      addResult( rs ) ; 
   }
 } else { 
   addResult (rs ); // put the value back in the buffer !
 }

}
    

void finish () { hasToRun = false ; }


// main for tests 

public static void main ( String[] args ) {

 int port = 50001;
 String host = "localhost";
 if ( args.length > 0 ) host = args[0];
 UDPwriter udpw = new UDPwriter ( host, port ) ;
 String [] para = { "p1","p2","p3"} ;

 for ( int i=0; i < 10; i ++ ) {
  Result r = new Result ( "farm", "cluster", "node", "module", para);
  
  udpw.addResult (r );
 }

}

}
