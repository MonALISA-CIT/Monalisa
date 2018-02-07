package lia.searchdaemon.comm;

import hep.io.xdr.XDRInputStream;
import hep.io.xdr.XDROutputStream;

import java.io.IOException;

public class XDRClientComm extends XDRAbstractComm {

 
    
    public XDRClientComm(String myName, XDROutputStream xdros, XDRInputStream xdris,  XDRMessageNotifier notifier) throws IOException{
		super (myName, xdros, xdris, notifier);
        setName(myName);
        this.notifier = notifier;
        this.myName = myName;
        this.xdris = xdris;
        this.xdros = xdros;
        hasToRun = true;
        closed = false;
        key = null;
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
            
            if(msg.opCode == -2) {
                try{
                    write(XDRMessage.ECHO_REPLAY_MESSAGE);
                }catch(Throwable t){
                    t.printStackTrace();
                }
                return null;
            }
            if(msg.opCode == -3) {
                return null;
            }
            return msg;
        }catch(Throwable t) {
            t.printStackTrace();
            close();
        }
        return null;
    } // read
    
    public void close() {
        hasToRun = false;
        if(!closed) {//allow multiple invocation for close()
            closed = true;
            try {
                if(xdris != null) xdris.close();
                if(xdros != null) xdros.close();
            }catch(Throwable t){
                
            }
        }
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
