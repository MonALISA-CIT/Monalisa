/*
 * Created on Aug 24, 2010
 */
package org.uslhcnet.rrd;

import org.uslhcnet.rrd.config.DSConfig;

/**
 * 
 * @author ramiro
 */
public class DSValue<T> {

    private final long timestamp;
    private final T value;
    private final DSConfig ds;
    
    public DSValue(long timestamp, T value, DSConfig ds) {
        super();
        this.timestamp = timestamp;
        this.value = value;
        this.ds = ds;
    }

    
    /**
     * @return the timestamp
     */
    public long timestamp() {
       return timestamp;
    }

    
    /**
     * @return the value
     */
    public T value() {
        return value;
    }

    
    /**
     * @return the ds
     */
    public DSConfig ds() {
        return ds;
    }


    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "DSValue [timestamp=" + timestamp + ", value=" + value + ", ds=" + ds + "]";
    }
    
}
