/*
 * Created on Aug 10, 2007
 * 
 * $Id: AccessControlListener.java 6865 2010-10-10 10:03:16Z ramiro $
 */
package lia.Monitor.ClientsFarmProxy.acl;

/**
 * 
 * This interface must be implemented by all the listeners which may want to be
 * notified whenever the configuration for AccessControlManager has changed
 *  
 * @author ramiro
 */
public interface AccessControlListener {
    public void notifyAccessContrlChanged();
}


