/*
 * Created on Aug 24, 2010
 */
package lia.util.rrd;

/**
 * 
 * @author ramiro
 */
public final class MLRRDKey<T> {
    private final T key;
    private final int hashCode;
    
    public MLRRDKey(T key) {
        if(key == null) {
            throw new NullPointerException("The key cannot be null");
        }
        this.key = key;
        final int prime = 31;
        int result = 1;
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        this.hashCode = result;
    }

    
    public T getKey() {
        return key;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MLRRDKey<T> other = (MLRRDKey<T>) obj;
        return key.equals(other.key);
    }


    @Override
    public String toString() {
        return "MLRRDKey [" + key + "]";
    }
    
}