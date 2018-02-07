package lia.searchdaemon.comm;

import hep.io.xdr.XDRInputStream;
import hep.io.xdr.XDROutputStream;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;



public abstract class XDRAbstractComm extends Thread {

	   protected boolean hasToRun;
	    protected XDRMessageNotifier notifier;
	    protected String myName;
	    protected XDRInputStream xdris;
	    protected XDROutputStream xdros;
	    protected boolean closed;
	    protected static long keys;
	    protected static Object keyLock;
	    protected String key;
	    
	    static {
	        keyLock = new Object();
	        synchronized(keyLock) {
	            keys = 0;
	        }
	    }
	    
	    public XDRAbstractComm(String myName, XDROutputStream xdros, XDRInputStream xdris,  XDRMessageNotifier notifier) throws IOException{
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
	                XDRMessage.ERROR_MESSAGE.data += sw.getBuffer().toString();
	                try {
	                    write(XDRMessage.ERROR_MESSAGE);
	                }catch(Throwable tsend){
	                }
	                XDRMessage.ERROR_MESSAGE.data = "Generic Error ... ";
	                hasToRun = false;
	            }
	        }
	        notifier.notifyXDRCommClosed(this);
	        System.out.println(" [ "+ System.currentTimeMillis() + " ] " + myName + " K: [" + getKey() + "] exits now .... \n\n");
	        close();
	    }
	    
	    public abstract XDRMessage read() ;
	    
	    public abstract void close() ;
	    
	    private static String nextKey() {
	        synchronized(keyLock) {
	            return "" + keys++;
	        } // synchronized
	    } // nextKey
	    
	    public String getKey() {
	        if(key == null) {
	            key = nextKey();
	        } 
	        return key;
	    } // getKey
	    
	    
	    public abstract void write(XDRMessage msg) ;
	
} // XDRAbstractComm
