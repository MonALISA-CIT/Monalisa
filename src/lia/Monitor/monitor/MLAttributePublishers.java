/*
 * $Id: MLAttributePublishers.java 6865 2010-10-10 10:03:16Z ramiro $
 * 
 * Created on Oct 23, 2007
 */
package lia.Monitor.monitor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 
 * Bridge between AttributePublisher-s and attribute producers
 * 
 * @author ramiro
 * 
 */
public class MLAttributePublishers implements AttributePublisher {

    private static final List<AttributePublisher> publishers = new CopyOnWriteArrayList<AttributePublisher>(); 
    
    private static final MLAttributePublishers _thisInstance = new MLAttributePublishers();
    
    public static final MLAttributePublishers getInstance() {
        return _thisInstance;
    }
    
    public void publish(final Object key,final Object value) {
        for(final AttributePublisher publisher : publishers)
            publisher.publish(key, value);
    }

    public void publish(final Map<?, ?> map) {
        for(final AttributePublisher publisher : publishers)
            publisher.publish(map);
    }

    public void publishNow(Map<?, ?> map) {
        for(final AttributePublisher publisher : publishers)
            publisher.publish(map);
    }

    public void publishNow(Object key, Object value) {
        for(final AttributePublisher publisher : publishers)
            publisher.publish(key, value);
    }

    public static final boolean addPublisher(final AttributePublisher pulisher) {
        return publishers.add(pulisher);
    }
    
    public static final boolean removePublisher(final AttributePublisher pulisher) {
        return publishers.remove(pulisher);
    }
}
