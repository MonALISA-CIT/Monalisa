package lia.Monitor.JiniSerFarmMon;

import java.io.Serializable;

import lia.Monitor.monitor.DataStore;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MonitorClient;
import lia.Monitor.monitor.MonitorFilter;
import lia.Monitor.monitor.monPredicate;

/**
 * Empty RMI Stub
 *
 * @since MLProxy Service is used to MUX the connections
 * @author ramiro
 */
public class NoImplProxy implements Serializable, DataStore {

    /**
     * 
     */
    private static final long serialVersionUID = 6009140374552511029L;

    /**
     * If this is not changed Jini will give the same SID
     */
    public String _key = "N/A";

    /**
     * @param c
     * @param p
     */
    public void Register(MonitorClient c, monPredicate p) {
        // Empty RMI Stub
    }

    /**
     * @param c
     */
    public void unRegister(MonitorClient c) {
        // Empty RMI Stub
    }

    /**
     * @param c
     * @param key
     */
    public void unRegister(MonitorClient c, Integer key) {
        // Empty RMI Stub
    }

    /**
     * @param c
     */
    public MFarm confRegister(MonitorClient c) {
        return null;
    }

    public String getIPAddress() {
        return "N/A";
    }

    public String getUnitName() {
        return "N/A";
    }

    public String getLocalTime() {
        return "N/A";
    }

    /**
     * @param mfliter
     */
    public void addFilter(MonitorFilter mfliter) {
        // Empty RMI Stub
    }

    public String[] getFilterList() {
        return new String[] {
            "N/A"
        };
    }

    /**
     * @param c
     * @param filter
     */
    public void Register(MonitorClient c, String filter) {
        // Empty RMI Stub
    }

}
