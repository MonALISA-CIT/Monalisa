/*
 * Created on Mar 21, 2010
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.net.topology.ciena;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

import lia.net.topology.GenericEntityWithState;
import lia.net.topology.TopologyException;

/**
 * 
 * @author ramiro
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class CienaHost extends GenericEntityWithState<CienaState> {


    /**
     * 
     */
    private static final long serialVersionUID = 239147257738250756L;
    
    protected final Set<CienaPort> ports = new CopyOnWriteArraySet<CienaPort>();

    public CienaHost(String name) throws TopologyException {
        super(name, CienaState.UP);
    }

    public void addPort(CienaPort cienaPort) {
        ports.add(cienaPort);
    }
    
    public void removePort(CienaPort cienaPort) {
        ports.remove(cienaPort);
    }
    
    public CienaPort getPortByName(final String portName) {
        //TODO - binary search
        for(final CienaPort p: ports) {
            if(p.name().equals(portName)) {
                return p;
            }
        }
        return null;
    }
    
    public CienaPort[] getPorts() {
        return ports.toArray(new CienaPort[ports.size()]);
    }
    public CienaPort getPortByID(final UUID id) {
        //TODO - binary search
        for(final CienaPort p: ports) {
            if(p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }

}
