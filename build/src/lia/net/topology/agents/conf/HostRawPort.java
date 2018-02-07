/*
 * Created on Mar 25, 2010
 */
package lia.net.topology.agents.conf;

import lia.net.topology.Port.PortType;

/**
 *
 * @author ramiro
 */
public final class HostRawPort implements Comparable<HostRawPort> {
    
//    private static final TreeSet<HostRawPort> cache = new TreeSet<HostRawPort>();
    
    public final String portName;
    public final PortType portType;
    
    private HostRawPort(String portName, PortType portType) {
        this.portType = portType;
        this.portName = portName;
    }

    public static HostRawPort newInstance(String portName, PortType portType) {
        HostRawPort hp = new HostRawPort(portName, portType);
//        final HostRawPort other = cache.floor(hp);
//        if(other != null && other.equals(hp)) {
//            return other;
//        }
//        cache.add(hp);
        return hp;
    }
    
    @Override
    public int hashCode() {
        return portName.hashCode() + portType.hashCode();
    }
    
    @Override
    public boolean equals(Object o) {
        if(o instanceof HostRawPort) {
            final HostRawPort other = (HostRawPort)o;
            return (this.portType == other.portType && this.portName.equals(other.portName)); 
        }
        
        return false;
    }
    
    public int compareTo(HostRawPort other) {
        final int diffPortType = this.portType.ordinal() - other.portType.ordinal();
        return (diffPortType < 0)? -1: (diffPortType > 0)? 1: this.portName.compareTo(other.portName); 
    }

    @Override
    public String toString() {
        return getClass().getName() + "[" + portName  + ":" + portType + "]";
    }

}