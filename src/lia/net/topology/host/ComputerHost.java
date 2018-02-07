/*
 * Created on Mar 21, 2010
 */
package lia.net.topology.host;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

import lia.net.topology.GenericEntityWithState;
import lia.net.topology.TopologyException;

/**
 * 
 * @author ramiro
 */
public class ComputerHost extends GenericEntityWithState<ComputerHostState> {

    /**
     * 
     */
    private static final long serialVersionUID = 4334191665133044572L;

    protected final Set<ComputerPort> ports = new CopyOnWriteArraySet<ComputerPort>();

    public ComputerHost(String name) throws TopologyException {
        super(name, ComputerHostState.UP);
    }

    public void addPort(ComputerPort computerPort) {
        ports.add(computerPort);
    }
    
    public void removePort(ComputerPort computerPort) {
        ports.remove(computerPort);
    }
    
    public ComputerPort getPortByName(final String portName) {
        //TODO - binary search
        for(final ComputerPort p: ports) {
            if(p.name().equals(portName)) {
                return p;
            }
        }
        return null;
    }
    
    public ComputerPort[] getPorts() {
        return ports.toArray(new ComputerPort[ports.size()]);
    }
    public ComputerPort getPortByID(final UUID id) {
        //TODO - binary search
        for(final ComputerPort p: ports) {
            if(p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }

}
