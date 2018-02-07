/*
 * Created on Mar 23, 2010
 */
package lia.net.topology;

import java.util.UUID;


/**
 * @author ramiro
 */
public interface GenericTopology {

    public GenericEntity getEntityByID(UUID id);
    public void addEntity(GenericEntity entity);
    public void addTopologyNotifier(TopologyNotifier topologyNotifier);
    public void removeTopologyNotifier(TopologyNotifier topologyNotifier);
}
