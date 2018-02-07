/*
 * Created on Mar 21, 2010
 */
package lia.net.topology.force10;

import lia.net.topology.TopologyException;
import lia.net.topology.Port;

/**
 * 
 * @author ramiro
 */
public class Force10Port extends Port<Force10Host, Force10PortState> {



    /**
     * 
     */
    private static final long serialVersionUID = -664404146324541078L;

    public Force10Port(String portName, Force10Host device) throws TopologyException {
        this(portName, device, PortType.INPUT_OUTPUT_PORT, Force10PortState.UP, null);
    }
    
    public Force10Port(String portName, Force10Host device, PortType type) throws TopologyException {
        this(portName, device, type, Force10PortState.UP, null);
    }
    
    public Force10Port(String portName, Force10Host device, PortType type, Port<?, ?> remotePort) throws TopologyException {
        this(portName, device, type, Force10PortState.UP, remotePort);
    }
    
    public Force10Port(String portName, Force10Host device, PortType type, Force10PortState initialState, Port<?, ?> remotePort) throws TopologyException {
        super(portName, device, type, initialState, remotePort);
    }
}
