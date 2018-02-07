package lia.Monitor.ClientsFarmProxy;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MonMessageClientsProxy;
import net.jini.core.lookup.ServiceID;

public class MFarmClientConfigInfo {
    private final AtomicReference<MonMessageClientsProxy> configMessageRef;

    private final ServiceID serviceID;

    // just for debugging
    final String fName;

    private final AtomicLong paramsCount = new AtomicLong(0L);

    private final AtomicLong nodesCount = new AtomicLong(0L);

    MFarmClientConfigInfo(final ServiceID serviceID, final MonMessageClientsProxy configMessage, int nc, int pc) {
        if ((serviceID == null) || (configMessage == null) || (configMessage.result == null)) {
            throw new NullPointerException("serviceID ( " + serviceID + " ) must be != null; configMessage ( "
                    + configMessage + " ) must be != null; configMessage.result ( " + configMessage.result
                    + ") must be != null");
        }
        this.configMessageRef = new AtomicReference<MonMessageClientsProxy>(configMessage);
        this.serviceID = serviceID;
        fName = ((MFarm) configMessage.result).name;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(" MFarmClientConfigInfo SID: ").append(serviceID);
        sb.append(" fName: ").append(fName);
        sb.append(" nc: ").append(getNodesCount()).append(" pc: ");
        sb.append(getParamsCount());
        return sb.toString();
    }

    public MonMessageClientsProxy getConfigMessage() {
        return configMessageRef.get();
    }

    public MonMessageClientsProxy getAndSetConfigMessage(MonMessageClientsProxy newConfig) {
        return configMessageRef.getAndSet(newConfig);
    }

    public long getParamsCount() {
        return paramsCount.get();
    }

    public void setParamsCount(final long paramsC) {
        paramsCount.set(paramsC);
    }

    public long getNodesCount() {
        return nodesCount.get();
    }

    public void setNodesCount(final long nodesC) {
        nodesCount.set(nodesC);
    }
}
