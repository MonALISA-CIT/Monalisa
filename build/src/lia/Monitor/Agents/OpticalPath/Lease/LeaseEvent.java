package lia.Monitor.Agents.OpticalPath.Lease;

/**
 * 
 */
public class LeaseEvent {
    
    //the one and only type
    public static final int     LEASE_EXPIRED       =       1;
    
    public final Lease lease;
    public final int eventType;
    
    public LeaseEvent(Lease source, int type) {
        this.lease = source;
        this.eventType = type;
    }
}
