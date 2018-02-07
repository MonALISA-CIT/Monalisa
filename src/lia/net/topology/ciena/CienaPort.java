/*
 * Created on Mar 21, 2010
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.net.topology.ciena;

import lia.net.topology.TopologyException;
import lia.net.topology.Port;

/**
 * 
 * @author ramiro
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class CienaPort extends Port<CienaHost, CienaPortState> {



    /**
     * 
     */
    private static final long serialVersionUID = 3761105697427289684L;

    public CienaPort(String portName, CienaHost device) throws TopologyException {
        this(portName, device, PortType.INPUT_OUTPUT_PORT, CienaPortState.UP, null);
    }
    
    public CienaPort(String portName, CienaHost device, PortType type) throws TopologyException {
        this(portName, device, type, CienaPortState.UP, null);
    }
    
    public CienaPort(String portName, CienaHost device, PortType type, Port<?, ?> remotePort) throws TopologyException {
        this(portName, device, type, CienaPortState.UP, remotePort);
    }
    
    public CienaPort(String portName, CienaHost device, PortType type, CienaPortState initialState, Port<?, ?> remotePort) throws TopologyException {
        super(portName, device, type, initialState, remotePort);
    }
}
