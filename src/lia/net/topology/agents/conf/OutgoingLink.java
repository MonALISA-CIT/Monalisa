/*
 * Created on Mar 25, 2010
 */
package lia.net.topology.agents.conf;

import java.io.Serializable;

import lia.net.topology.DeviceType;
import lia.net.topology.Port.PortType;

/**
 * @author ramiro
 */
public final class OutgoingLink implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1202596642678933960L;

    public final String remoteDeviceName;

    public final DeviceType remoteDeviceType;

    public final String remotePortName;

    public final PortType remotePortType;

    public OutgoingLink(final String remoteDeviceName, final DeviceType remoteDeviceType, final String remotePortName, final PortType remotePortType) {
        this.remoteDeviceName = remoteDeviceName;
        this.remotePortName = remotePortName;
        this.remoteDeviceType = remoteDeviceType;
        this.remotePortType = remotePortType;
    }

    @Override
    public int hashCode() {
        return this.remoteDeviceName.hashCode() + this.remotePortName.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof OutgoingLink) {
            OutgoingLink other = (OutgoingLink) o;
            return (this.remoteDeviceName.equals(other.remoteDeviceName) && this.remoteDeviceType.equals(other.remoteDeviceType) && this.remotePortName.equals(other.remotePortName) && this.remotePortType.equals(other.remotePortType));
        }
        return false;
    }

    public String toString() {
        return getClass().getName() + "->" + remoteDeviceType + ":" + remoteDeviceName + ":" + remotePortName + ":" + remotePortType;
    }
}
