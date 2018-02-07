package lia.Monitor.Agents.OpticalPath.comm;

import hep.io.xdr.XDRInputStream;
import hep.io.xdr.XDROutputStream;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public abstract class XDRGenericComm extends Thread {

    private boolean hasToRun;
    XDRMessageNotifier notifier;
    String myName;
    private XDRInputStream xdris;
    private XDROutputStream xdros;
    private boolean closed;
    private static long keys;
    private static Object keyLock;
    private String key;
    
    static {
        keyLock = new Object();
        synchronized(keyLock) {
            keys = 0;
        }
    }
    
    /**
     * @throws IOException  
     */
    public XDRGenericComm(String myName, XDROutputStream xdros, XDRInputStream xdris,  XDRMessageNotifier notifier) throws IOException{
        setName(myName);
        this.notifier = notifier;
        this.myName = myName;
        this.xdris = xdris;
        this.xdros = xdros;
        hasToRun = true;
        closed = false;
        key = null;
    }

    public void run() {
        System.out.println(" [ "+ System.currentTimeMillis() + " ] " + myName + " enter run() .... ");
        while(hasToRun){
            try {
                XDRMessage xdrMsg = read();
                if (xdrMsg == null) continue;
                notifier.notifyXDRMessage(xdrMsg, this);
            }catch(Throwable t){
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                XDRMessage msg = XDRMessage.getErrorMessage(sw.getBuffer().toString());
                try {
                    write(msg);
                }catch(Throwable tsend){
                    tsend.printStackTrace();
                }
                hasToRun = false;
            }
        }
        notifier.notifyXDRCommClosed(this);
        System.out.println(" [ "+ System.currentTimeMillis() + " ] " + myName + " K: [" + getKey() + "] exits now .... \n\n");
        close();
    }
    
    public XDRMessage read() {
        try {
            XDRMessage msg = new XDRMessage();
            
            msg.xdrMessageSize = xdris.readInt();

            msg.opCode = xdris.readInt();
            xdris.pad();
            msg.id = xdris.readString();
            xdris.pad();
            msg.olID = xdris.readString();
            xdris.pad();
            msg.data = xdris.readString();
            xdris.pad();
            
            return msg;
        }catch(Throwable t) {
            t.printStackTrace();
            close();
        }
        return null;
    }
    
    public void close() {
        hasToRun = false;
        if(!closed) {//allow multiple invocation for close()
            closed = true;
            try {
                if(xdris != null) xdris.close();
                if(xdros != null) xdros.close();
            }catch(Throwable t){
                t.printStackTrace();
            }
        }
    }
    
    private static String nextKey() {
        synchronized(keyLock) {
            return "" + keys++;
        }
    }
    
    public String getKey() {
        if(key == null) {
            key = nextKey();
        } 
        return key;
    }
    
    private int getXDRSize(String data) {
        int size = 0;
        if (data != null && data.length() != 0) {
            size = data.length()+4;
            /* the length of the XDR representation must be a multiple of 4,
               so there might be some extra bytes added*/
            if (size % 4 != 0)
              size += (4 - size % 4);
        }
        return size;
    }
    
    private int getXDRSize(XDRMessage msg) {
        int size = 8;
        
        size += getXDRSize(msg.data);
        size += getXDRSize(msg.id);
        size += getXDRSize(msg.olID);
        
        return size;
    }
    
    public synchronized void write(XDRMessage msg) {
        try {
            msg.xdrMessageSize = getXDRSize(msg);
            
            xdros.writeInt(msg.xdrMessageSize);
            xdros.pad();
            xdros.writeInt(msg.opCode);
            xdros.pad();
            xdros.writeString(msg.id);
            xdros.pad();
            xdros.writeString(msg.olID);
            xdros.pad();
            xdros.writeString(msg.data);
            xdros.pad();
            xdros.flush();
        }catch(Throwable t) {
            System.out.println("Communication error ... closing socket");
            t.printStackTrace();
            close();
        }
    }
}
