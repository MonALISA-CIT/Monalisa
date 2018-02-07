/*
 * Created on Mar 20, 2010
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.net.topology;

/**
 * 
 * @author ramiro
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class Link extends GenericEntityWithState<LinkState>{
    /**
     * 
     */
    private static final long serialVersionUID = 2603389138669181790L;
    
    protected final Port<?, ?> sourcePort;
    protected final Port<?, ?> destinationPort;
    
    public Link(Port<?, ?> sourcePort, Port<?, ?> destinationPort) throws TopologyException {
        this(sourcePort, destinationPort, LinkState.CONNECTED);
    }
    
    public Link(Port<?, ?> sourcePort, Port<?, ?> destinationPort, LinkState initialState) throws TopologyException {
        super(sourcePort.device()+ ":" + sourcePort.name() + ":" + sourcePort.type() + " -> " + destinationPort.device()+ ":" + destinationPort.name() + ":" + destinationPort.type(), initialState);
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
    }
    
    public Port<?, ?> sourcePort() {
        return sourcePort;
    }

    public Port<?, ?> destinationPort() {
        return destinationPort;
    }
    
    @Override
    public String toString() {
        return getClass().getName() + " " + name;
    }
    
}
