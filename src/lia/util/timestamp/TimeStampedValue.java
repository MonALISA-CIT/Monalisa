package lia.util.timestamp;



/**
 * 
 * A simple wrapper class for any value which can have an associated {@link Timestamp} 
 * The objects of this class are ordered by their {@link #timeStamp}
 * 
 * @author ramiro
 */
public abstract class TimeStampedValue<V> implements Timestampable<V>, Comparable<TimeStampedValue<V>> {

    /**
     * the timeStamp for this value
     */
    private final Timestamp timestamp;
    
    /**
     * the value itself
     */
    private final V value;
    
    /**
     * Constructs a new TimestampedValue.  
     * @param value the value
     * @param timeStamp the {@link Timestamp} of the value
     */
    public TimeStampedValue(V value) {
        if(this.value == null) {
            throw new NullPointerException("Null value");
        }
        
        this.value = value;
        this.timestamp = new Timestamp();
    }

    public V value() {
        return value;
    }
    
    public Timestamp timestamp() {
        return timestamp;
    }

    /**
     * the values are ordered by their {@link #timeStamp}
     */
    public int compareTo(TimeStampedValue<V> o) {
        return this.timestamp.compareTo(o.timestamp);
    }
}
