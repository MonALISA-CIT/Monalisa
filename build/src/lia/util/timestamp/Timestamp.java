package lia.util.timestamp;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import lia.util.Utils;
import lia.util.ntp.NTPDate;

/**
 * Helper class which also keeps a nanosecond ac
 * @author ramiro
 */
public class Timestamp implements Comparable<Timestamp> {

    private final static AtomicLong SEQ = new AtomicLong();
    
    /**
     * milliseconds since January 1, 1970, 00:00:00 GMT
     */
    public final long millis;
    
    /**
     * the nanos, usualy given by {@link #nanosNow()}
     */
    public final long nanos;
    
    /**
     * used in case the comparison it's tight with the default timestamps ( millis and nanos )
     */
    private final long seq;
    
    public Timestamp() {
        this(NTPDate.currentTimeMillis());
    }
    
    /**
     * Constructs a new timestamp. The {@link #nanos} are considered System.
     * @param millis milliseconds since January 1, 1970, 00:00:00 GMT.
     */
    protected Timestamp(long millis) {
        this(millis, Utils.nanoNow());
    }
    
    /**
     * Constructs a new timestamp. 
     * @param millis
     * @param nanos
     */
    protected Timestamp(long millis, long nanos) {
        this.millis = millis;
        this.nanos = nanos;
        this.seq = SEQ.getAndIncrement();
    }
    
    public int compareTo(Timestamp o) {
        if(this == o) return 0;
        
        if(this.millis < o.millis) return -1;
        if(this.millis > o.millis) return 1;
        
        if(this.nanos < o.nanos) return -1;
        if(this.nanos > o.nanos) return 1;
        
        //check the seq; it's highly unlikely to have the same nanos, but ...
        return (this.seq < o.seq)? -1 : 1;
    }

    /**
     * @return The difference between {@link System#nanoTime() Utils.nanoNow()} and {@link #NANO_REFERENCE}
     */
    public static final long nanosNow() {
        return Utils.nanoNow();
    }

    public static long duration(Timestamp start, Timestamp end, TimeUnit durationUnit) {
        return durationUnit.convert(end.nanos - start.nanos, TimeUnit.NANOSECONDS);
    }
}
