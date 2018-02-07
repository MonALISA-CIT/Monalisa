package lia.Monitor.JiniClient.Store;

import lia.Monitor.monitor.MonaLisaEntry;
import net.jini.core.lookup.ServiceID;

/**
 * @author costing
 *
 */
public class rcStoreNode  {
    /**
     * 
     */
    public int myID ;
    /**
     * 
     */
    public ServiceID sid ;
    /**
     * 
     */
    public MonaLisaEntry mlentry;
    /**
     * 
     */
    public String ipad;
    /**
     * 
     */
    public JtClient client;
    /**
     * 
     */
    public int errorCount;
    /**
     * 
     */
    public String UnitName ;
}
