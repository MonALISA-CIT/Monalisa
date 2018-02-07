/*
 * Created on Mar 21, 2010
 */
package lia.net.topology.force10;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

import lia.net.topology.GenericEntityWithState;
import lia.net.topology.TopologyException;

/**
 * 
 * @author ramiro
 */
public class Force10Host extends GenericEntityWithState<Force10PortState> {


    /**
     * 
     */
    private static final long serialVersionUID = 2985077696280969238L;
    
    protected final Set<Force10Port> ports = new CopyOnWriteArraySet<Force10Port>();

    public Force10Host(String name) throws TopologyException {
        super(name, Force10PortState.UP);
    }

    public void addPort(Force10Port computerPort) {
        ports.add(computerPort);
    }
    
    public void removePort(Force10Port computerPort) {
        ports.remove(computerPort);
    }
    
    public Force10Port getPortByName(final String portName) {
        //TODO - binary search
        for(final Force10Port p: ports) {
            if(p.name().equals(portName)) {
                return p;
            }
        }
        return null;
    }
    
    public Force10Port[] getPorts() {
        return ports.toArray(new Force10Port[ports.size()]);
    }
    public Force10Port getPortByID(final UUID id) {
        //TODO - binary search
        for(final Force10Port p: ports) {
            if(p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }

}
