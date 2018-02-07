package lia.Monitor.Agents.OpticalPath.v2.State;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * This class should be used only to transport the state over the wire
 * 
 * @author ramiro
 */
public class OSwConfig implements Serializable {

    private static final long serialVersionUID = 3244405607872857417L;
    
    public static final transient int   UNKNOWN             =   0;
    public static final transient int   CALIENT             =   1;
    public static final transient int   GLIMMERGLASS        =   2;

    /**
     * Switch name
     */
    public String name;
    
    /**
     * Switch type
     */
    public int type;
    
    /**
     * K: Hostname; V: OSwLink
     */
    public HashMap localHosts;
    
    /**
     * Local Ports
     */
    public OSwPort[] osPorts;
    
    public OSwCrossConn[] crossConnects;
    
    /**
     * Can the agent use the TL1 connection ? 
     */
    public volatile boolean isAlive;
    
    public OSwConfig() {
        osPorts = new OSwPort[0];
        crossConnects = new OSwCrossConn[0];
        localHosts = new HashMap(0);
    }

    public OSwConfig(String name, int type, HashMap localHosts, OSwPort[] osPorts) {
        this.name = name;
        this.type = type;
        this.localHosts = localHosts;
        this.osPorts = osPorts;
    }
    
    public static final String getOSType(int type) {
        switch(type) {
            case CALIENT: return "CALIENT";
            case GLIMMERGLASS: return "GLIMMERGLASS";
            default: return "UNKNOWN_TYPE";
        }
    }
    
    public String getExtendedStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n <<< Optical Switch Configuration for [").append(name).append("] ");
        sb.append(" Type: ").append(getOSType(type)).append(" >>>");
        sb.append("\n\n OSPorts: \n\n");
        if(osPorts == null || osPorts.length == 0) {
            sb.append(" No OS Ports defined");
        } else {
            for(int i=0; i<osPorts.length; i++) {
                sb.append(osPorts[i].getExtendedState()).append("\n");
            }
        }
        sb.append("\n\n CrossConnects: \n\n").append(Arrays.toString(crossConnects));
        sb.append("\n\n Local Hosts: \n\n").append(localHosts);
        sb.append("\n >>> END Optical Switch Configuration for [").append(name).append("] <<< ");
        return sb.toString();
    }
    
    public OSwPort getPort(String portName, short portType) {
        for(int i=0; i<osPorts.length; i++) {
            if(osPorts[i].type == portType && osPorts[i].name.equals(portName)) {
                return osPorts[i];
            }
        }
        
        return null;
    }
    
    public OSwPort[] getOutputPortsForEndPoint(String endPointName) {
        ArrayList ret = new ArrayList();
        for(int i=0; i<osPorts.length; i++) {
            OSwPort oswPort = osPorts[i]; 
            if(oswPort.type == OSwPort.OUTPUT_PORT && oswPort.oswLink != null
                    && oswPort.oswLink.destination != null
                    && oswPort.oswLink.destination.equals(endPointName)) {
                ret.add(oswPort);
            }
        }
        if(ret.size() == 0) {
            return null;
        }
        
        return (OSwPort[]) ret.toArray(new OSwPort[ret.size()]);
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n <<< Optical Switch Configuration for [").append(name).append("] ");
        sb.append(" Type: ").append(getOSType(type)).append(" >>>");
        sb.append("\n\n OSPorts: \n\n").append(Arrays.toString(osPorts));
        sb.append("\n\n CrossConnects: \n\n").append(Arrays.toString(crossConnects));
        sb.append("\n\n Local Hosts: \n\n").append(localHosts);
        sb.append("\n >>> END Optical Switch Configuration for [").append(name).append("] <<< ");
        return sb.toString();
    }
}
