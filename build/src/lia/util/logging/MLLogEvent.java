package lia.util.logging;

import java.util.Hashtable;


public class MLLogEvent<K,V> {
    
    public final Hashtable<K, V> logParameters;
    boolean canBeBuffered;
    
    public MLLogEvent() {
        canBeBuffered = false;
        logParameters = new Hashtable<K, V>();
    }
    
    /**
     * logParam put() wrapper ... FeatureRequest from Florin  
     * @param key
     * @param value
     */
    public void put(K key, V value) {
        logParameters.put(key, value);
    }
}
