/*
 * $Id: AttributePublisher.java 6865 2010-10-10 10:03:16Z ramiro $
 * 
 * Created on Oct 23, 2007
 */
package lia.Monitor.monitor;

import java.util.Map;

/**
 * 
 * Generic interface for GMLEPublisher
 * 
 * @author ramiro
 * 
 */
public interface AttributePublisher {

    public void publish(final Object key, final Object value);

    public void publish(final Map<?, ?> map);

    public void publishNow(final Map<?, ?> map);

    public void publishNow(final Object key, final Object value);

}

