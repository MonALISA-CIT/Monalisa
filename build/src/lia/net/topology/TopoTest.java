/*
 * Created on Mar 23, 2010
 */
package lia.net.topology;

import java.util.UUID;

import lia.net.topology.Port.PortType;
import lia.net.topology.host.ComputerHost;
import lia.net.topology.host.ComputerPort;
import lia.net.topology.opticalswitch.AFOXOSPort;
import lia.net.topology.opticalswitch.OSPort;
import lia.net.topology.opticalswitch.OpticalSwitch;
import lia.net.topology.opticalswitch.OpticalSwitchType;


/**
 *
 * @author ramiro
 */
public class TopoTest {

    private static final TopologyHolder holder = TopologyHolder.getInstance();
    private static final int AFOX_ROWS = 8;
    private static final int AFOX_COLS = 12;
    
    private static final void addOS(final String name) throws TopologyException {
        OpticalSwitch os = new OpticalSwitch(OpticalSwitchType.AFOX, name); 
        int c=0; 
        for(int i=1; i <= AFOX_ROWS; i++) {
            for(int j=1; j<= AFOX_COLS; j++) {
                c++;
                PortType pt = PortType.INPUT_PORT;
                if(c < AFOX_ROWS * AFOX_COLS / 2) {
                    pt = PortType.OUTPUT_PORT;
                }
                
                AFOXOSPort.Builder<Port<?,?>> b = new AFOXOSPort.Builder<Port<?,?>>("AFOX " + c, pt, os, null, null);
                b.row(i).column(j).customerName(" gogogoogo " + c);
                os.addPort(b.build()); 
            }
        }
        holder.addEntity(os);
    }
    
    private static final void addHost(final String name) throws TopologyException {
        ComputerHost host = new ComputerHost(name);
        ComputerPort port = new ComputerPort("eth2", host);
        host.addPort(port);
        port = new ComputerPort("eth0", host);
        host.addPort(port);
        port = new ComputerPort("eth1", host);
        host.addPort(port);
        holder.addEntity(host);
    }
    
    public static final void addLink(Port<? ,?> sourcePort, Port<? ,?> destinationPort) throws TopologyException {
        sourcePort.setLink(new Link(sourcePort, destinationPort));
    }
    
    public static final void generateTopology() throws TopologyException {
        addOS("AFOX_GVA");
        addOS("AFOX_AMS");
        addOS("AFOX_CHI");
        addOS("AFOX_NYC");
        addHost("hermes1");
        addHost("store1");
        
        OpticalSwitch os = (OpticalSwitch)holder.getEntityByID(UUID.nameUUIDFromBytes("AFOX_GVA".getBytes()));
        OSPort sp = GenericEntity.entityForName(os.name() + ":" + "AFOX " + 3, AFOXOSPort.class);
        ComputerHost ch = (ComputerHost)holder.getEntityByID(UUID.nameUUIDFromBytes("hermes1".getBytes()));
        ComputerPort dp = GenericEntity.entityForName(ch.name() + ":" + "eth2", ComputerPort.class);
        addLink(sp, dp);
        sp = os.getPortByName(os.name() + ":" + "AFOX " + 83);
        addLink(dp, sp);
    }
    
    public static final void main(String[] args) throws TopologyException {
        generateTopology();
    }
}
