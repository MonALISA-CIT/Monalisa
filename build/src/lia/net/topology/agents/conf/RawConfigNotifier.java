/*
 * Created on Mar 26, 2010
 */
package lia.net.topology.agents.conf;


/**
 * @author ramiro
 */
public interface RawConfigNotifier<P> {

    /**
     * 
     * @param oldConfig
     * @param newConfig
     */
    public void notifyConfig(RawConfigInterface<P> oldConfig, RawConfigInterface<P> newConfig);
}
