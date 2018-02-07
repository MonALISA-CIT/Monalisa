package lia.util.logging.service;

import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.monMessage;
import lia.Monitor.monitor.tcpConn;
import lia.Monitor.monitor.tcpConnNotifier;
import lia.util.logging.comm.MLLogMsg;
import net.jini.core.lookup.ServiceID;

/**
 * wrapper class for tcpConn with a ML proxy service  
 * @author ramiro
 */
public class MLProxyConn implements tcpConnNotifier {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(MLProxyConn.class.getName());

    private final tcpConn conn;
    private ServiceID proxySID;
    
    private MLProxyConn(Socket s) throws Exception {
        conn = tcpConn.newConnection(this, s);
        if(conn == null) {
            throw new NullPointerException("tcpConn is null");
        }
    }

    public static final MLProxyConn newInstance(Socket s) throws Exception {
        final MLProxyConn mlpc = new MLProxyConn(s);
        if(mlpc != null && mlpc.conn != null) {
            mlpc.conn.startCommunication();
        } else {
            throw new Exception("conn is null");
        }
        return mlpc;
    }
    public void notifyConnectionClosed() {
        logger.log(Level.INFO, " Closing connection with proxy [ " + proxySID + " ] :- " + ((conn != null)?conn.toString():"null"));
        try {
            MLLoggerServer.getInstance().remove(this);
        }catch(Throwable t){//ignore
            logger.log(Level.WARNING, " Got exception removing MLProxyConn [ " +  proxySID + " ] from MLLogerServer", t);
        }
    }

    public void notifyMessage(Object o) {
        if(o == null) {
            logger.log(Level.WARNING, " [ MLProxyConn ]  got a null object! Ignoring it");
            return;
        }
        
        if(o instanceof monMessage) {
            Object r = ((monMessage)o).result;
            
            try {
                
                if(proxySID == null) {
                    if(r instanceof ServiceID) {
                        proxySID = (ServiceID)r;
                        logger.log(Level.INFO, "\n\n Comm with proxy [ " + proxySID + " ] started. " + conn.toString()+"\n\n\n");
                    } else {
                        logger.log(Level.WARNING, "\n\n [ MLProxyConn ] Protocol Exception ? proxySID == null and first msg != ServiceID ... I will close the connection \n\n\n");
                        conn.close_connection();
                    }
                    return;
                }
                
                if(r instanceof MLLogMsg) {
                    MLLogMsg mlm = (MLLogMsg)r;
                    if(logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "\n\n [ MLProxyConn ] Notify remote log message from Proxy [ " + proxySID + " ] :\n " + mlm.toString() );
                    }
                    MLLoggerService.getInstance().notifyRemoteLogMsg(mlm);
                }
            } catch(Throwable t) {
                logger.log(Level.WARNING, " [ MLProxyConn ] got exception in notifyMessage() ", t);
            }
        }
    }
}
