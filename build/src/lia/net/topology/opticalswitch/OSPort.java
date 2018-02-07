/*
 * Created on Mar 20, 2010
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.net.topology.opticalswitch;

import lia.net.topology.TopologyException;
import lia.net.topology.Port;


/**
 * 
 * @author ramiro
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class OSPort extends Port<OpticalSwitch, OSPortState> {

    /**
     * 
     */
    private static final long serialVersionUID = 4863765018302856276L;

    public OSPort(String portName, OpticalSwitch device, PortType portType, OSPortState initialState, Port<?, ?> outgoingPort) throws TopologyException {
        super(portName, device, portType, initialState, outgoingPort);
    }
    
}
