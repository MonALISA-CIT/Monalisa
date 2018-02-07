/*
 * Created on Nov 4, 2005
 *
 */
package lia.Monitor.monitor;

/**
 * All the classes that needs to be notified when the ml.prop was
 * reloaded should implement this interface and register in AppConfig 
 */
public interface AppConfigChangeListener {
    /**
     * This method is called every time ml.prop is changed
     * this methd should finish in short time ...
     */
    public void notifyAppConfigChanged();
}
