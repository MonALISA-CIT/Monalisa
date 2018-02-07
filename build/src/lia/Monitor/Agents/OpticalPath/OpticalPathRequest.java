package lia.Monitor.Agents.OpticalPath;

import java.util.Hashtable;
import java.util.concurrent.CountDownLatch;

/**
 * 
 * Should be alive from the since the switch revceives the request
 * util it releaseses the optical path
 */
public class OpticalPathRequest {
    public String id;
    
    public String source;
    public String destination;
    
    // K: agentAddr V: sPort - dPort
    public Hashtable links;
    public Hashtable sentCMDs;
    
    // K: agentAddr Lease
    public Hashtable leases;
    
    public String readableOpticalPath;
    public String pDconn;

    public StringBuilder status;
    
    //used to wait for remote commands
    private CountDownLatch cdl;
    /**
     * be more verbose
     */
    public boolean verbose;
    
    /**
     * whether or not the connection should be full-duplex
     */
    public boolean isFDX;
    
    public OpticalPathRequest(String id, boolean isFDX, boolean verbose) {
        this.links = new Hashtable();
        this.sentCMDs = new Hashtable();
        this.leases = new Hashtable();
        this.id = id;
        status = new StringBuilder();
        this.isFDX = isFDX;
        this.verbose = verbose;
    }
    
    public synchronized void setCountDownLatch(CountDownLatch cdl) {
        this.cdl = cdl;
    }

    public synchronized CountDownLatch getCountDownLatch() {
        return cdl;
    }
}
