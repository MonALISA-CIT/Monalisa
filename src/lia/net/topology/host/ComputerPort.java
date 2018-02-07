/*
 * Created on Mar 21, 2010
 */
package lia.net.topology.host;

import lia.net.topology.TopologyException;
import lia.net.topology.Port;

/**
 * 
 * @author ramiro
 */
public class ComputerPort extends Port<ComputerHost, ComputerPortState> {


    /**
     * 
     */
    private static final long serialVersionUID = 4856309963946428539L;

    public ComputerPort(String portName, ComputerHost device) throws TopologyException {
        this(portName, device, PortType.INPUT_OUTPUT_PORT, ComputerPortState.UP, null);
    }
    
    public ComputerPort(String portName, ComputerHost device, PortType type) throws TopologyException {
        this(portName, device, type, ComputerPortState.UP, null);
    }
    
    public ComputerPort(String portName, ComputerHost device, PortType type, Port<?, ?> remotePort) throws TopologyException {
        this(portName, device, type, ComputerPortState.UP, remotePort);
    }
    
    public ComputerPort(String portName, ComputerHost device, PortType type, ComputerPortState initialState, Port<?, ?> remotePort) throws TopologyException {
        super(portName, device, type, initialState, remotePort);
    }
}
