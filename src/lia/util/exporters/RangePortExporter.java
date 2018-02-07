/*
 * Created on Jul 16, 2003
 *
 */
package lia.util.exporters;

import java.util.Hashtable;

import lia.Monitor.monitor.AppConfig;

/**
 * 
 * Base class for exporters
 * 
 */
public class RangePortExporter {
    protected static int MIN_BIND_PORT = Integer.valueOf(AppConfig.getProperty("lia.Monitor.MIN_BIND_PORT", "9000")).intValue();
    protected static int MAX_BIND_PORT = Integer.valueOf(AppConfig.getProperty("lia.Monitor.MAX_BIND_PORT", "9010")).intValue();
    
    /** 
     * Ports already allocated [object, port] 
     * it is filled by subclasses
     * 
     * */
    protected static final Hashtable allocatedPorts = new Hashtable();
}
