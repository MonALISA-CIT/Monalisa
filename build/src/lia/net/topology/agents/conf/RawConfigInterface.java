/*
 * Created on Mar 26, 2010
 */
package lia.net.topology.agents.conf;

import java.util.List;
import java.util.Map;

/**
 * 
 * @author ramiro
 */
public interface RawConfigInterface<T> {
    public String hostName();
    public List<T> hostPorts();
    public Map<T, OutgoingLink> outgoingLinks();
    public void addNotifier(RawConfigNotifier<T> notifier);
    public void removeNotifier(RawConfigNotifier<T> notifier);
}
