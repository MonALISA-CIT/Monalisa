import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.net.FloodControl;

public class  GenericUDPListener extends Thread {
    /** Logger Name */
    private static final transient String COMPONENT = "lia.Monitor.modules.GenericUDPListener";
    /** The Logger */ 
    private static final transient Logger logger = Logger.getLogger(COMPONENT);

    protected DatagramSocket socket = null;
    protected boolean hasToRun ;
    byte[] buf = new byte[8192];
    private GenericUDPNotifier notif = null;
    
    private UDPAccessConf accessConf;
    private Object locker = new Object();
    private FloodControl floodController;
        
    public GenericUDPListener( int port, GenericUDPNotifier notif ) throws IOException {
        this(null, port, notif, null);
    }

    public GenericUDPListener( int port, GenericUDPNotifier notif, UDPAccessConf conf ) throws IOException {
        this(null, port, notif, conf);
    }

    public GenericUDPListener(InetAddress laddr, int port, GenericUDPNotifier notif, UDPAccessConf conf ) throws IOException {
        super(" ( ML ) Generic UDP Listener on port [ " + port +" ]");
        if (laddr == null) {
            socket  = new DatagramSocket(port);
        } else {
            socket = new DatagramSocket(port, laddr);
        }
        logger.log(Level.INFO, " Generic UDP Listener started on port " + port);
        if ( socket == null ) {
                System.out.println ( " s = null " );
                System.exit( -1 );
        }
        
        hasToRun = true;
        this.notif = notif;
        this.floodController = new FloodControl();
        this.accessConf = conf;
        
        start();
    }
    
    public void setAccessConf(UDPAccessConf accessConf) {
        synchronized(locker) {
            if(logger.isLoggable(Level.FINEST)){
                logger.log(Level.FINEST, "GenericUDPLister got new Conf....");
            }
            this.accessConf = accessConf;
        }
    }
    
    public void setMaxMsgRate(int rate){
    	floodController.setMaxMsgRate(rate);
    }
    
    public void run() {

     while ( hasToRun) {
            try {
                DatagramPacket packet  = new DatagramPacket(buf, buf.length);  // TODO: move this above while?
                socket.receive(packet);
                InetAddress address = packet.getAddress();
                if(floodController.shouldDrop(address))
                	continue;
                synchronized(locker) {
                    if (accessConf != null) {
                        if ( !accessConf.checkIP(address) ) {
                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, "The IP [ " +address+" ] is not allowed to send datagrams...ignoring it"); 
                            }
                            continue;
                        }
                    }
                }//end sync
                int len = packet.getLength();
                byte[] data = packet.getData();
                if ( len > 0 && notif != null ) {
                    notif.notifyData(len, data, address);
                }
	    
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed receiving UDP datagram.");
            }
        }
        socket.close();
    }
    

}

