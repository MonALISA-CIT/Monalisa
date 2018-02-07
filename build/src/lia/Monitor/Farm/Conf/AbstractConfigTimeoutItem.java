/*
 * Created on Aug 3, 2007
 * 
 * $Id: AbstractConfigTimeoutItem.java 6865 2010-10-10 10:03:16Z ramiro $
 */
package lia.Monitor.Farm.Conf;

import java.util.Date;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.Utils;
import lia.util.ntp.NTPDate;

abstract class AbstractConfigTimeoutItem implements Delayed {

    /** The Logger */
    private static final Logger logger = Logger.getLogger("lia.Monitor.Farm.Conf");

    final AtomicLong nextDeadline;
    final AtomicLong nextTimeout;
    final AtomicLong nextTimeoutNano;
    protected final long renewLease;
    private final long renewLeaseNano;
    private final long seq;
    
    private static final AtomicLong SEQ = new AtomicLong();
    protected final String name;
    protected static final ConfVerifier confVerifier = ConfVerifier.getInstance();
    
    AbstractConfigTimeoutItem(final String name, long renewLease) {
        if(name == null) throw new NullPointerException("Name cannot be null");
        this.renewLease = renewLease;
        this.renewLeaseNano = TimeUnit.MILLISECONDS.toNanos(renewLease);
        
        nextTimeout = new AtomicLong(NTPDate.currentTimeMillis() + renewLease);
        nextDeadline = new AtomicLong(Utils.nanoNow() + renewLeaseNano);
        nextTimeoutNano = new AtomicLong(nextDeadline.get());
        this.seq = SEQ.getAndIncrement();
        this.name = name;
    }
    
    public void renew() {
        final long renewUntil = NTPDate.currentTimeMillis() + renewLease;
        final long renewUntilNano = Utils.nanoNow() + renewLeaseNano;
        nextTimeout.set(renewUntil);
        nextTimeoutNano.set(renewUntilNano);
        if(logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [ ConfVerifier ] [ AbstractConfigTimeoutItem ] [ renew ] for " + this);
        }
    }

    void reschedule() {
        nextDeadline.set(nextTimeoutNano.get());
    }
    
    public long getDelay(TimeUnit unit) {
        return unit.convert(nextDeadline.get() - Utils.nanoNow(), TimeUnit.NANOSECONDS);
    }
    
    public int compareTo(Delayed o) {
        if(o == this) return 0;
        
        
        AbstractConfigTimeoutItem dfe = (AbstractConfigTimeoutItem)o;
        final long diff = nextDeadline.get() - dfe.nextDeadline.get();
        
        if(diff < 0) {
            return -1;
        } else if (diff > 0) {
            return 1;
        } else if (seq < dfe.seq) {
            return -1;
        }
        
        return 1;
    }

    public String toString() {
        return name + " renewLease=" + renewLease + " ms, nextDeadLineNano=" + nextDeadline.get() + ",nanoReference=" + Utils.nanoNow() + ",nextTimeoutNano=" + nextTimeoutNano.get() + ",nextTimeout=" + nextTimeout.get() + "/" + new Date(nextTimeout.get());
    }
    
    public abstract void timedOut();
}
