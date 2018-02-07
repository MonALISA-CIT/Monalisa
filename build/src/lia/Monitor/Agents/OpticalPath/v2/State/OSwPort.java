package lia.Monitor.Agents.OpticalPath.v2.State;

import java.io.Serializable;

/**
 * This class encapsulates the state for an Optical Switch Port
 */
public class OSwPort implements Serializable {
    
    
    /**
     * Serial Version ID 
     */
    private static final long serialVersionUID = 529467035848397976L;
    
    //Port Type
    public static final transient short     UNKNOWN_PORT_TYPE  =    0;
    public static final transient short     INPUT_PORT         =    1;
    public static final transient short     OUTPUT_PORT        =    2;
    public static final transient short     MULTICAST_PORT     =    3;
    
    //Fiber States
    public static final transient short     UNKNOWN_FIBER_STATE =    0;
    public static final transient short     FIBER               =    1;
    public static final transient short     NOFIBER             =    2;
    
    //final states for optical power states
    public static final transient short     UNKLIGHT            =    0;
    public static final transient short     LIGHTOK             =    1;
    public static final transient short     NOLIGHT             =    2;
    public static final transient short     NOLIGHT_SIMULATED   =    3;
    public static final transient short     LIGHTERR            =    4;
    public static final transient short     LIGHTERR_SIMULATED  =    5;
    
    //transient states for optical power state
    public static final transient short     WFORLIGHT1          =    6;
    public static final transient short     WFORLIGHT2          =    7;

    static final transient String[] PORT_TYPE_NAMES  = new String[] {
        "unknown",
        "in",
        "out",
        "multicast"
    };

    static final transient String[] FIBER_STATE_NAMES  = new String[] {
        "UNKNOWN_FIBER_STATE",
        "FIBER_PRESENT",
        "NO_FIBER_PRESENT"
    };
    
    static final transient String[] POWER_STATE_NAMES  = new String[] {
        "UNKLIGHT",
        "LIGHTOK",
        "NOLIGHT",
        "NOLIGHT_SIMULATED",
        "LIGHTERR",
        "LIGHTERR_SIMULATED",
        "WFORLIGHT1",
        "WFORLIGHT2"
    };
    
    /**
     * The name of the port
     */
    public String name;
    
    /**
     * Optional label for the port ...
     */
    public String label;
    
    /**
     * Last known optical power on the port
     */
    public double power;
    
    /**
     * Can be INPUT_PORT or OUTPUT_PORT, or MULTICAST_PORT
     */
    public volatile short type;
    
    /**
     * Can be FIBER, NOFIBER, UNKNOWN_STATE
     */
    public volatile short fiberState;
    
    /**
     * Can be LIGHTOK ... WFORLIGHT2
     */
    public volatile short powerState;
    
    /**
     * This is used only as a -20dBm threashold ... per port threshold?! in the future
     */
    public transient double minPower;
    public transient double maxPower;

    /**
     * Connection to other switch/host
     */
    public OSwLink oswLink;
    
    //precalculated hashcode
    private int hashCode;
    
    public OSwPort(String portName, short type) {
        this(portName, Double.MIN_VALUE, type);
    }
    
    public OSwPort(String portName, double power, short type) {
        this.name = portName;
        this.power = power;
        this.type = type;
        minPower = -20;
        maxPower = 20;
        this.hashCode = name.hashCode() + type; 
    }
    
    public int hashCode() {
        return hashCode;
    }
    
    public boolean equals(Object o) {
        OSwPort osp = (OSwPort)o;
        return ((this.type == osp.type) && osp.name.equals(name));
    }
    
    public OSwPort getIOPear() {
        return new OSwPort(name, (type==INPUT_PORT)?OUTPUT_PORT:INPUT_PORT);
    }

    public String getExtendedState() {
        
        StringBuilder sb = new StringBuilder();
        
        //
        sb.append(name).append("_").append(getDecodedPortType());
        
        if(label!=null) {
            sb.append(" - ").append(label);
        }
        
        sb.append(", FiberState: ").append(getDecodedPortFiberState());
        sb.append(", PowerState: ").append(getDecodedPortPowerState());
        sb.append(", ").append(((oswLink==null)?"No external links":oswLink.toString()));
        
        return sb.toString();
    }
    
    public final String getDecodedPortType() {
        return decodePortType(type);
    }
    
    public final String getDecodedPortFiberState() {
        return decodePortFiberState(fiberState);
    }
    
    public final String getDecodedPortPowerState() {
        return decodePortPowerState(powerState);
    }
    
    public final static String decodePortType(final short type) {
        return internalDecodedName(PORT_TYPE_NAMES, type);
    }
    
    public static final String decodePortFiberState(final short fiberState) {
        return "(" + fiberState +") = " + internalDecodedName(FIBER_STATE_NAMES, fiberState);
    }
    
    public static final String decodePortPowerState(final short portPowerState) {
        return "(" + portPowerState +" ) = " + internalDecodedName(POWER_STATE_NAMES, portPowerState);
    }
    
    /**
     * 
     */
    public String toString() {
        return name + "_" + getDecodedPortType();
    }
    
    //Helper function used to decode states ...
    static final String internalDecodedName(String[] names, int idx) {
        if(idx <= 0 || idx > names.length) {
            return names[0];
        }
        return names[idx];
    }
}