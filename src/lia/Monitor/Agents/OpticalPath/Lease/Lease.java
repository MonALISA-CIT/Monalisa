package lia.Monitor.Agents.OpticalPath.Lease;

import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import lia.util.ntp.NTPDate;


public class Lease implements Comparable<Lease> { 
    
    private final Long id;
    private final String sessionID;
    private final String remoteAgentAddress;
    
    private long lastRenewed;
    private long expireTime;
    private long leaseRenewalInterval;
    private LeaseEventListener lel;
    private LeaseRenewal leaseRenewal;
    
    private static AtomicLong IDSeq =  new AtomicLong();
    
    private static long getID() {
        return IDSeq.getAndIncrement();
    }
    
    public Lease(String sessionID, String remoteAgentAddress, long leaseRenewInterval, LeaseEventListener lel, LeaseRenewal leaseRenewal) {
        this.id = Long.valueOf(getID());
        this.sessionID = sessionID;
        this.lel = lel;
        this.leaseRenewalInterval = leaseRenewInterval;
        this.remoteAgentAddress = remoteAgentAddress;
        this.leaseRenewal = leaseRenewal;
        renew(leaseRenewInterval);
    }
    
    public synchronized void renew(long period) {
        lastRenewed = NTPDate.currentTimeMillis();
        expireTime = lastRenewed + period;
    }
    
    public void renew() {
        renew(leaseRenewalInterval);
    }
    
    public synchronized long getExpireTime() {
        return expireTime;
    }
    
    public Long getLeaseID() {
        return this.id;
    }
   
    public boolean equals(Object o) {
        if(o == null) {
            return false;
        }
        if(o instanceof Lease) {
            return this.id.equals(((Lease)o).id);
        }
        return false;
    }
    
    public LeaseEventListener getListener() {
        return lel;
    }
    
    public void setListener(LeaseEventListener lel) {
        this.lel = lel;
    }

    public String getRemoteAgentAddress() {
        return remoteAgentAddress;
    }
    
    public long getLeaseRewalInterval() {
        return leaseRenewalInterval;
    }
    
    public String getSessionID() {
        return this.sessionID;
    }
    
    public LeaseRenewal getLeaseRenewal() {
        return leaseRenewal;
    }
    
    public int compareTo(Lease l) {

        if(expireTime < l.expireTime) {
            return -1;
        }
        
        if(expireTime > l.expireTime) {
            return 1;
        }
        
        return this.id.compareTo(l.id);//atomic sequece
    }
    
    public String toString() {
        return " Lease :- " + id + " SessionID :- " + sessionID +" LastRenewed " + new Date(lastRenewed) + " expireTime: " + new Date(expireTime) + " currentTime" + new Date(NTPDate.currentTimeMillis()); 
    }

    @Override
    public int hashCode() {
        //same as long
        final long idl = this.id.longValue();
        return (int)(idl ^ (idl >>> 32));
    }
}
