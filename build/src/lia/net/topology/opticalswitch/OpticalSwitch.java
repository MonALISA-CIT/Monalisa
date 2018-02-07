/*
 * Created on Mar 20, 2010
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.net.topology.opticalswitch;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

import lia.net.topology.GenericEntityWithState;
import lia.net.topology.TopologyException;
import lia.net.topology.Link;
import lia.net.topology.Port.PortType;

/**
 *  
 * ML view of an Optical Switch
 * 
 * @author ramiro
 */
public class OpticalSwitch extends GenericEntityWithState<OpticalSwitchState> {

    /**
     * 
     */
    private static final long serialVersionUID = 7811039194564665704L;

    /**
     * type of the switch
     */
    protected final OpticalSwitchType type;

    protected final Set<OSPort> ports = new CopyOnWriteArraySet<OSPort>();
    protected final Set<Link> crossConnects = new CopyOnWriteArraySet<Link>();
    
    /**
     * 
     * @param type
     * @param name
     * @throws TopologyException 
     */
    public OpticalSwitch(OpticalSwitchType type, String name) throws TopologyException {
        this(type, UUID.nameUUIDFromBytes(name.getBytes()), name);
    }
    
    /**
     * 
     * @param type
     * @param id
     * @param name
     * @throws TopologyException 
     */
    public OpticalSwitch(OpticalSwitchType type, UUID id, String name) throws TopologyException {
        super(name, OpticalSwitchState.UP);
        this.type = type;
    }
 
    public OpticalSwitchType switchType() {
        return type;
    }
    
    public void addPort(OSPort... osPort) {
        ports.addAll(Arrays.asList(osPort));
    }

    public void addPortSet(Set<OSPort> osPortSet) {
        ports.addAll(osPortSet);
    }
    
    public void removePort(OSPort... osPort) {
        ports.removeAll(Arrays.asList(osPort));
    }
    
    public void addCrossConn(Link... crossConnect) {
        crossConnects.addAll(Arrays.asList(crossConnect));
    }

    public void addCrossConnSet(Set<Link> crossConnectSet) {
        crossConnects.addAll(crossConnectSet);
    }

    public void removeCrossConn(Link... crossConnect) {
        crossConnects.removeAll(Arrays.asList(crossConnect));
    }
    
    public Set<OSPort> getPortSet() {
        return Collections.unmodifiableSet(ports);
    }

    public Set<Link> getCrossConnSet() {
        return Collections.unmodifiableSet(crossConnects);
    }
    
    public OSPort getPortByNameAndType(final String portName, PortType portType) {
        for(final OSPort osp: ports) {
            if(osp.name().equals(portName) && osp.type() == portType) {
                return osp;
            }
        }
        return null;
    }
    public OSPort getPortByName(final String portName) {
        //TODO - binary search
        for(final OSPort osp: ports) {
            if(osp.name().equals(portName)) {
                return osp;
            }
        }
        return null;
    }
    
    public OSPort getPortByID(final UUID id) {
        //TODO - binary search
        for(final OSPort osp: ports) {
            if(osp.id().equals(id)) {
                return osp;
            }
        }
        return null;
    }
    
    public Link[] getCrossConnects() {
        return crossConnects.toArray(new Link[crossConnects.size()]);
    }
    
    public void clearCross() {
        crossConnects.clear();
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("OpticalSwitchInfo -: ").append(type).append(" :- ").append(name()).append(" :- ").append(id());
        return sb.toString();
    }

    /**
     * @return
     */
    public OSPort[] getPorts() {
        return ports.toArray(new OSPort[ports.size()]);
    }
}
