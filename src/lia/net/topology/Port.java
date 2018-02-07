/*
 * Created on Mar 20, 2010
 */
package lia.net.topology;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;


/**
 * 
 * @author ramiro
  */
public class Port<D extends GenericEntity, S extends Enum<S>> extends GenericEntityWithState<S> {
    
    /**
     * 
     */
    private static final long serialVersionUID = -9214819992627258707L;

    public enum PortType { INPUT_PORT, OUTPUT_PORT, INPUT_OUTPUT_PORT }
    
    protected final D device;
    
    protected final PortType portType;
    
    protected final AtomicReference<Link>  outgoingLink = new AtomicReference<Link>(null);

    protected Port(String portName, D device, PortType portType, S initialState) throws TopologyException {
        this(portName, device, portType, initialState, null, LinkState.DISCONNECTED);
    }
    
    protected Port(String portName, D device, PortType portType, S initialState, Port<?,?> remotePort) throws TopologyException {
        this(portName, device, portType, initialState, remotePort, LinkState.CONNECTED);
    }
    
    protected Port(String portName, D device, PortType portType, S initialState, Port<?,?> remotePort, LinkState initialRemoteLinkState) throws TopologyException {
        super(portName, UUID.nameUUIDFromBytes((device.name() + ":" + portName + ":" + portType).getBytes()), initialState);
        this.device = device;
        this.portType = portType;
        if(remotePort != null) {
            this.outgoingLink.set(new Link(this, remotePort, initialRemoteLinkState));
        }
    }
    
    public D device() {
        return device;
    }
    
    public PortType type() {
        return portType;
    }
    
    public Link setLink(Link newLink) {
        return outgoingLink.getAndSet(newLink);
    }
    
    public Link outgoingLink() {
        return outgoingLink.get();
    }
    
    @Override
    public String toString() {
        return getClass().getName() + "," + id + "," + ((device == null)?"null":device.name()) + ":" + name + ", state:" + state.get();
    }
}
