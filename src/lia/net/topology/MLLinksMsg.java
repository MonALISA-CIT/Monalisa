/*
 * Created on Mar 25, 2010
 */
package lia.net.topology;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import lia.net.topology.agents.conf.OutgoingLink;

/**
 * 
 * @author ramiro
 */
public class MLLinksMsg implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 265574076044152505L;
    
    public final UUID entityID;
    public final Map<UUID, OutgoingLink> linksMap;
    public final DeviceType entityType;
    
    public MLLinksMsg(UUID entityID, DeviceType entityType, Map<UUID, OutgoingLink> linksMap) {
        this.entityID = entityID;
        this.entityType = entityType;
        this.linksMap = new TreeMap<UUID, OutgoingLink>(linksMap);
    }
}
