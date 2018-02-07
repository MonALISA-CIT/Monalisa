/*
 * Created on Aug 14, 2007
 * 
 * $Id: SortedVectorNotifier.java 6865 2010-10-10 10:03:16Z ramiro $
 * 
 */
package lia.Monitor.monitor;

/**
 * 
 * Simple notifier used by the {@link SortedVector} class to notify
 * structure changes
 * 
 * @author ramiro
 * 
 */
public interface SortedVectorNotifier {

    public void elementRemoved(Object o);
    public void elementAdded(Object o);
    
}
