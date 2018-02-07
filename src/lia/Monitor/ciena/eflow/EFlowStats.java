/*
 * Created on Dec 4, 2011
 */
package lia.Monitor.ciena.eflow;

import java.math.BigInteger;
import java.util.Date;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import lia.util.Utils;
import lia.util.ntp.NTPDate;

public final class EFlowStats implements Comparable<EFlowStats> {
	private static final EFlowStatsMgr MGR = EFlowStatsMgr.getInstance();
	
    public final String name;
    public final String mlName;
    
    private BigInteger lastValue;
    private long lastNanoUpdate;
    private long lastNTPUpdate;
    private double lastSpeed;
    
    public volatile boolean collectPM = false;
    
    private final ReadLock rLock;
    private final WriteLock wLock;
    
    public EFlowStats(final String name, final String mlName) {
        this.name = name;
        this.mlName = mlName;
        final ReentrantReadWriteLock master = new ReentrantReadWriteLock();
        rLock = master.readLock();
        wLock = master.writeLock();
    }
    
    public final BigInteger lastCounterValue() {
        rLock.lock();
        try {
            return lastValue;
        }finally {
            rLock.unlock();
        }
    }
    
    public double getSpeedAndSetLastValue(final String newValue) {
        
       final BigInteger newVal = new BigInteger(newValue);
       final long nanoNow = Utils.nanoNow();
       
       boolean bNotif = false;
       wLock.lock();
       try {
           double lastSpeed = -1;
           try {
               if(lastValue != null) {
                   final double factor = ( nanoNow - lastNanoUpdate ) / 8000D ;
                   lastSpeed = newVal.subtract(lastValue).doubleValue() / factor;
                   bNotif = true;
                   return lastSpeed;
               }
           } finally {
               lastValue = newVal;
               lastNanoUpdate = nanoNow;
               lastNTPUpdate = NTPDate.currentTimeMillis();
               this.lastSpeed = lastSpeed;
           }
           
           return -1D;
       } finally {
           wLock.unlock();
           if(bNotif) {
        	   MGR.newEFlowStats(this);
           }
       }
    }
    
    public double getLastSpeed() {
        rLock.lock();
        try {
            return lastSpeed;
        }finally {
            rLock.unlock();
        }
    }
    
    @Override
    public int hashCode() {
        return this.name.hashCode();
    }
    
    @Override
    public boolean equals(Object o) {
        if(this == o) return true;
        return name.equals(((EFlowStats)o).name);
    }
    
    @Override
    public int compareTo(EFlowStats other) {
        return name.compareTo(other.name);
    }

    public long lastUpdateMillis() {
        return lastNTPUpdate;
    }
    
    public long lastUpdateNano() {
        return lastNanoUpdate;
    }
    
    @Override
    public String toString() {
        return "EFlowStats [name=" + name + ", mlName=" + mlName + ", lastValue=" + lastValue + ", lastSpeed=" + lastSpeed + ", lastNTPUpdate=" + new Date(lastNTPUpdate) + ", lastNanoUpdate=" + lastNanoUpdate + ", collectPM=" + collectPM + "]";
    }
    
}