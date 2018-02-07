
import java.io.*;
import java.net.*;
import java.util.*;

import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.*;

public class  McastGanglia extends Thread {
    /** Logger Name */
    private static final transient String COMPONENT = "lia.mtools";
    /** The Logger */ 
    private static final transient Logger logger = Logger.getLogger(COMPONENT);

    protected MulticastSocket socket = null;
    protected boolean hasToRun ;
    byte[] buf = new byte[4096];
    private MNode Node;
    private String Module; 

    private Vector rezBuff = new Vector();
    private Vector rezTmpBuff = new Vector();

    static MGangliaMetrics gm = new MGangliaMetrics();

    public McastGanglia(InetAddress group, int port, MNode Node, String Module ) throws IOException {
    this.Node = Node;
    this.Module = Module;
    socket  = new MulticastSocket(port);
    socket.joinGroup(group);
    if ( socket == null ) {
      System.out.println ( " s = null " );
      System.exit( -1 );
    }
    hasToRun = true;
    start();

    }

    public void run() {



     while ( hasToRun) {
            try {
                DatagramPacket packet  = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                InetAddress address = packet.getAddress();
                int len = packet.getLength();
                 byte[] data = packet.getData();
	    
        int key = XDRUtils.decodeInt32(data,0);
        if ( key >= MGangliaMetrics.gMetrics.length || key < 0 ) {
            logger.log(Level.INFO, "No such metric. key = " + key );
        } else if ( key < MGangliaMetrics.MIN_RESERVED_KEY || key > MGangliaMetrics.MAX_RESERVED_KEY ) {
                Result r = null;
                String gModule = MGangliaMetrics.gMetrics[key];
                Ganglia2MLMetric g2ml = (Ganglia2MLMetric)gm.g2mlh.get(gModule);
                if ( g2ml != null ) {
//                   System.out.println(" [ " + address.getHostName() + " ] " + key + " gm " + gModule + " mlm " + g2ml.mlMetric );
                    r = new Result();
                    r.NodeName = address.getHostName();
                    r.FarmName = Node.getFarmName();
                    r.ClusterName = Node.getClusterName();
                    r.Module = Module;
                    r.time = System.currentTimeMillis();
                    double value = 0;
                    switch ( g2ml.xdrType ) {
                        case MGangliaMetrics.XDR_INT16:
                            value = XDRUtils.decodeInt32( data, 4 );
                            break;
                        case MGangliaMetrics.XDR_INT32: 
                            value = XDRUtils.decodeInt32( data, 4 );
                            break;
                        case MGangliaMetrics.XDR_INT64: 
                            value = XDRUtils.decodeInt64( data, 4 );
                            break;
                        case MGangliaMetrics.XDR_REAL32: 
                            value = XDRUtils.decodeReal32( data, 4 );
                            break;
                        case MGangliaMetrics.XDR_REAL64: 
                            value = XDRUtils.decodeReal64( data, 4 );
                            break;
                    }
                    
                    //Quick Fix for memory and Network 
                    if ( g2ml.gMetric.equals("bytes_in") || g2ml.gMetric.equals("bytes_out") ) {
                        value = (value*8.0D)/(1000.0D*1000.0D);//Mb
                    } else if ( g2ml.gMetric.indexOf("mem_") != -1 ) {
                        value /= 1024.0D;//MB
                    }
                    r.addSet( g2ml.mlMetric, value );
                    rezBuff.add(r);
                }
            }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        socket.close();
    }
    
    public Vector getResults() {
        synchronized ( rezBuff ) {
            if ( rezBuff.size() == 0 )
                return null;
            rezTmpBuff.clear();
            rezTmpBuff.addAll(rezBuff);
            rezBuff.clear();
        }
        
        return rezTmpBuff;
    }

    public static void main(String[] args) throws IOException {
        try {
            new McastGanglia(InetAddress.getByName("239.2.11.71"), 8649, new MNode("localhost", null, null ), "monMcastGanlia");
        } catch ( Exception ex ) {
            ex.printStackTrace();
        }
    }
}

