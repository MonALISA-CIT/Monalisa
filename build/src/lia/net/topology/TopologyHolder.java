/*
 * Created on Mar 23, 2010
 */
package lia.net.topology;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.net.topology.agents.conf.AFOXRawPort;
import lia.net.topology.agents.conf.OutgoingLink;
import lia.net.topology.ciena.CienaHost;
import lia.net.topology.ciena.CienaPort;
import lia.net.topology.force10.Force10Host;
import lia.net.topology.force10.Force10Port;
import lia.net.topology.host.ComputerHost;
import lia.net.topology.host.ComputerPort;
import lia.net.topology.opticalswitch.AFOXOSPort;
import lia.net.topology.opticalswitch.OSPort;
import lia.net.topology.opticalswitch.OpticalSwitch;
import lia.util.Utils;

/**
 * 
 * @author ramiro
 */
public class TopologyHolder implements GenericTopology {
    private static final Logger logger = Logger.getLogger(TopologyHolder.class.getName());

    private static final TopologyHolder topoHolder = new TopologyHolder();
    private final List<TopologyNotifier> notifiers = new CopyOnWriteArrayList<TopologyNotifier>();
    private final ConcurrentMap<UUID, GenericEntity> topoMap = new ConcurrentHashMap<UUID, GenericEntity>();

    public static final TopologyHolder getInstance() {
        return topoHolder;
    }

    @Override
    public GenericEntity getEntityByID(UUID id) {
        return topoMap.get(id);
    }

    @Override
    public void addEntity(GenericEntity entity) {
        GenericEntity oldEntity = topoMap.putIfAbsent(entity.id(), entity);
        for (final TopologyNotifier notif : notifiers) {
            if (oldEntity != null) {
                notif.updateEntity(oldEntity, entity);
            } else {
                notif.newEntity(entity);
            }
        }
    }

    public static final Port<?, ?> getPortByOpticalLink(OutgoingLink ol) {
        switch (ol.remoteDeviceType) {
        case HOST: {
            ComputerHost host = (ComputerHost) topoHolder.getEntityByID(UUID.nameUUIDFromBytes(ol.remoteDeviceName
                    .getBytes()));
            if (host == null) {
                return null;
            }
            return host.getPortByName(ol.remotePortName);
        }
        case F10: {
            Force10Host host = (Force10Host) topoHolder.getEntityByID(UUID.nameUUIDFromBytes(ol.remoteDeviceName
                    .getBytes()));
            if (host == null) {
                return null;
            }
            return host.getPortByName(ol.remotePortName);
        }
        case CIENA: {
            CienaHost host = (CienaHost) topoHolder
                    .getEntityByID(UUID.nameUUIDFromBytes(ol.remoteDeviceName.getBytes()));
            if (host == null) {
                return null;
            }
            return host.getPortByName(ol.remotePortName);
        }
        case AFOX: {
            OpticalSwitch host = (OpticalSwitch) topoHolder.getEntityByID(UUID.nameUUIDFromBytes(ol.remoteDeviceName
                    .getBytes()));
            if (host == null) {
                return null;
            }
            final Set<OSPort> osPorts = host.getPortSet();
            for (OSPort port : osPorts) {
                AFOXOSPort afxPort = (AFOXOSPort) port;
                AFOXRawPort afx = AFOXRawPort.valueOf(ol.remotePortName);
                if ((afx.portType == port.type()) && (afx.portRow == afxPort.getRow())
                        && (afx.portColumn == afxPort.getColumn())) {
                    return port;
                }
            }
            return host.getPortByNameAndType(ol.remotePortName, ol.remotePortType);
        }
        }

        return null;
    }

    private void checkPorts(UUID deviceID, DeviceType deviceType, Map<UUID, OutgoingLink> currentLinks) {
        Port<?, ?>[] devicePorts = new Port[0];
        switch (deviceType) {
        case HOST: {
            devicePorts = ((ComputerHost) topoMap.get(deviceID)).getPorts();
            break;
        }
        case CIENA: {
            devicePorts = ((CienaHost) topoMap.get(deviceID)).getPorts();
            break;
        }
        case F10: {
            devicePorts = ((Force10Host) topoMap.get(deviceID)).getPorts();
            break;
        }
        case AFOX: {
            devicePorts = ((OpticalSwitch) topoMap.get(deviceID)).getPorts();
            break;
        }
        default: {
            logger.log(Level.WARNING, "[ TopologyHolder ] Unable to handle (yet) device type: " + deviceType
                    + " Update needed?");
            break;
        }
        }

        for (final Port<?, ?> p : devicePorts) {
            final Link ol = p.outgoingLink();
            if ((ol != null) && currentLinks.keySet().contains(ol.sourcePort().id())) {
                continue;
            }
            if (ol != null) {
                GenericEntity.clearIDFromCache(ol.id());
                p.setLink(null);
                logger.log(Level.INFO, " !! CLEARED link: " + ol);
            }
        }
    }

    private void checkForPorts(CienaHost newHost) {
        CienaHost currentHost = (CienaHost) topoMap.get(newHost.id());
        Port<?, ?>[] currentHostPorts = currentHost.getPorts();
        Port<?, ?>[] newHostPorts = newHost.getPorts();

        final List<Port<?, ?>> cpList = Arrays.asList(currentHostPorts);

        for (final Port<?, ?> newPort : newHostPorts) {
            if (!cpList.contains(newPort)) {
                currentHost.addPort((CienaPort) newPort);
            }
        }

        //check for dead ports
        final List<Port<?, ?>> ncpList = Arrays.asList(newHostPorts);
        for (final Port<?, ?> cPort : currentHostPorts) {
            if (!ncpList.contains(cPort)) {
                currentHost.removePort((CienaPort) cPort);
            }
        }

    }

    private void checkForPorts(ComputerHost newHost) {
        ComputerHost currentHost = (ComputerHost) topoMap.get(newHost.id());
        Port<?, ?>[] currentHostPorts = currentHost.getPorts();
        Port<?, ?>[] newHostPorts = newHost.getPorts();

        final List<Port<?, ?>> cpList = Arrays.asList(currentHostPorts);

        for (final Port<?, ?> newPort : newHostPorts) {
            if (!cpList.contains(newPort)) {
                currentHost.addPort((ComputerPort) newPort);
            }
        }

        //check for dead ports
        final List<Port<?, ?>> ncpList = Arrays.asList(newHostPorts);
        for (final Port<?, ?> cPort : currentHostPorts) {
            if (!ncpList.contains(cPort)) {
                currentHost.removePort((ComputerPort) cPort);
            }
        }

    }

    private void checkForPorts(Force10Host newHost) {
        Force10Host currentHost = (Force10Host) topoMap.get(newHost.id());
        Port<?, ?>[] currentHostPorts = currentHost.getPorts();
        Port<?, ?>[] newHostPorts = newHost.getPorts();

        final List<Port<?, ?>> cpList = Arrays.asList(currentHostPorts);

        for (final Port<?, ?> newPort : newHostPorts) {
            if (!cpList.contains(newPort)) {
                currentHost.addPort((Force10Port) newPort);
            }
        }

        //check for dead ports
        final List<Port<?, ?>> ncpList = Arrays.asList(newHostPorts);
        for (final Port<?, ?> cPort : currentHostPorts) {
            if (!ncpList.contains(cPort)) {
                currentHost.removePort((Force10Port) cPort);
            }
        }

    }

    private void checkForPorts(OpticalSwitch newHost) {
        OpticalSwitch currentHost = (OpticalSwitch) topoMap.get(newHost.id());
        Port<?, ?>[] currentHostPorts = currentHost.getPorts();
        Port<?, ?>[] newHostPorts = newHost.getPorts();

        for (final Port<?, ?> newPort : newHostPorts) {
            boolean found = false;
            for (final Port<?, ?> cPort : currentHostPorts) {
                if (cPort.equals(newPort)) {
                    found = true;
                    ((AFOXOSPort) cPort).setPending(((AFOXOSPort) newPort).pendingExists());
                    break;
                }
            }

            if (!found) {
                currentHost.addPort((OSPort) newPort);
            }
        }

        //check for dead ports
        final List<Port<?, ?>> ncpList = Arrays.asList(newHostPorts);
        for (final Port<?, ?> cPort : currentHostPorts) {
            if (!ncpList.contains(cPort)) {
                currentHost.removePort((OSPort) cPort);
            }
        }

        final Set<Link> oLinks = currentHost.getCrossConnSet();
        final Set<Link> nLinks = newHost.getCrossConnSet();

        if (nLinks.equals(oLinks)) {
            return;
        }

        final List<Link> toRemove = new LinkedList<Link>();
        final List<Link> toAdd = new LinkedList<Link>();

        for (final Link link : oLinks) {
            if (!nLinks.contains(link)) {
                toRemove.add(link);
            }
        }

        for (final Link link : nLinks) {
            if (!oLinks.contains(link)) {
                toAdd.add(link);
            }
        }

        final int rLen = toRemove.size();
        if (rLen > 0) {
            currentHost.removeCrossConn(toRemove.toArray(new Link[rLen]));
            logger.log(Level.INFO, " [ checkForPorts ] removed " + rLen + " cross conns: " + toRemove);
        }

        final int aLen = toAdd.size();
        if (aLen > 0) {
            currentHost.addCrossConn(toAdd.toArray(new Link[aLen]));
            logger.log(Level.INFO, " [ checkForPorts ] added " + aLen + " cross conns: " + toAdd);
        }

    }

    public void notifyRemoteMsg(byte[] msg, TopologyNotifier notif) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = null;
        ObjectInputStream ois = null;
        if (msg != null) {
            bais = new ByteArrayInputStream(msg);
            ois = new ObjectInputStream(bais);
            try {
                final TopoMsg topoMsg = (TopoMsg) ois.readObject();
                if (topoMsg == null) {
                    logger.log(Level.WARNING,
                            "[ TopologyHolder ] [ notifyRemoteMsg ] Null TopoMsg after deserialization");
                    return;
                }

                final TopoMsg.Type topoMsgType = topoMsg.type();

                switch (topoMsgType) {
                case ML_LINKS: {
                    MLLinksMsg linksMsg = topoMsg.payload(MLLinksMsg.class);
                    if (linksMsg == null) {
                        return;
                    }
                    final UUID id = linksMsg.entityID;
                    final Map<UUID, OutgoingLink> map = linksMsg.linksMap;
                    try {
                        for (Map.Entry<UUID, OutgoingLink> entry : map.entrySet()) {
                            final UUID portID = entry.getKey();
                            final OutgoingLink ol = entry.getValue();
                            Port<?, ?> sp = null;
                            Port<?, ?> dp = null;
                            switch (linksMsg.entityType) {
                            case HOST: {
                                ComputerHost host = (ComputerHost) topoMap.get(id);
                                if (host == null) {
                                    continue;
                                }
                                sp = host.getPortByID(portID);
                                break;
                            }
                            case CIENA: {
                                CienaHost host = (CienaHost) topoMap.get(id);
                                if (host == null) {
                                    continue;
                                }
                                sp = host.getPortByID(portID);
                                break;
                            }
                            case F10: {
                                Force10Host host = (Force10Host) topoMap.get(id);
                                if (host == null) {
                                    continue;
                                }
                                sp = host.getPortByID(portID);
                                break;
                            }
                            case AFOX: {
                                OpticalSwitch host = (OpticalSwitch) topoMap.get(id);
                                if (host == null) {
                                    continue;
                                }
                                sp = host.getPortByID(portID);
                                break;
                            }
                            default: {
                                logger.log(Level.WARNING, "[ TopologyHolder ] Unable to handle (yet) TopoMsg type: '"
                                        + linksMsg.entityType + "' ! Update needed?");
                                break;
                            }
                            }
                            if (sp == null) {
                                continue;
                            }

                            Link eol = sp.outgoingLink();

                            try {
                                dp = getPortByOpticalLink(ol);
                                if (dp != null) {
                                    if ((eol != null) && eol.destinationPort().equals(dp)) {
                                        continue;
                                    }
                                    if (eol != null) {
                                        GenericEntity.clearIDFromCache(eol.id());
                                    }
                                    Link l = new Link(sp, dp, LinkState.CONNECTED);
                                    logger.log(Level.INFO, " !!!! ADDED link: " + ol);
                                    sp.setLink(l);

                                }
                                notif.updateEntity(sp.device(), sp.device);
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, " [ TopologyHolder ] Unable to set link for: " + sp + " dp: "
                                        + dp, t);
                            }
                        }
                    } finally {
                        checkPorts(id, linksMsg.entityType, map);
                    }
                    return;
                }
                case AFOX_CONFIG: {
                    final OpticalSwitch os = topoMsg.payload(OpticalSwitch.class);
                    if (topoMap.putIfAbsent(os.id(), os) == null) {
                        notif.newEntity(os);
                    } else {
                        checkForPorts(os);
                        notif.newEntity(topoMap.get(os.id()));
                    }
                    break;
                }
                case HOST_CONFIG: {
                    final ComputerHost host = topoMsg.payload(ComputerHost.class);
                    if (topoMap.putIfAbsent(host.id(), host) == null) {
                        notif.newEntity(host);
                    } else {
                        checkForPorts(host);
                        notif.newEntity(topoMap.get(host.id()));
                    }
                    break;
                }
                case CIENA_CONFIG: {
                    final CienaHost host = topoMsg.payload(CienaHost.class);
                    if (topoMap.putIfAbsent(host.id(), host) == null) {
                        notif.newEntity(host);
                    } else {
                        checkForPorts(host);
                        notif.newEntity(topoMap.get(host.id()));
                    }
                    break;
                }
                case FORCE10_CONFIG: {
                    final Force10Host host = topoMsg.payload(Force10Host.class);
                    if (topoMap.putIfAbsent(host.id(), host) == null) {
                        notif.newEntity(host);
                    } else {
                        checkForPorts(host);
                        notif.newEntity(topoMap.get(host.id()));
                    }
                    break;
                }
                default: {
                    logger.log(Level.WARNING, "[ TopologyHolder ] Unable to handle (yet) TopoMsg type: '" + topoMsgType
                            + "' ! Update needed?");
                    break;
                }
                }
            } finally {
                Utils.closeIgnoringException(ois);
                Utils.closeIgnoringException(bais);
            }
        } else {
            logger.log(Level.WARNING, "[ TopologyHolder ] [ notifyRemoteMsg ] Null msg received");
        }
    }

    @Override
    public void addTopologyNotifier(TopologyNotifier topologyNotifier) {
        notifiers.add(topologyNotifier);
    }

    @Override
    public void removeTopologyNotifier(TopologyNotifier topologyNotifier) {
        notifiers.remove(topologyNotifier);
    }

}
