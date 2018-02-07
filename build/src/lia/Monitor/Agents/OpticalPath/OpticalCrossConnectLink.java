package lia.Monitor.Agents.OpticalPath;

import java.io.Serializable;

/**
 * This class describes a connection in the switch
 */
public class OpticalCrossConnectLink implements Serializable {

    private static final long serialVersionUID = 3546643196227498035L;
    
    //TODO - We must define some states
    public static final int OK                  = 1;
    public static final int ERROR               = 2;
    public static final int REMOVED             = 4;
    
    /**Source port*/
    public final OSPort sPort;
    
    /**Destination port*/
    public final OSPort dPort;
    
    /**the status of the link*/
    public Integer status;

    public OpticalCrossConnectLink(OSPort sPort, OSPort dPort, Integer status) {
        if(sPort == null || dPort == null) {
            throw new NullPointerException("The ports cannot be null");
        }
        this.sPort = sPort;
        this.dPort = dPort;
        this.status = status;
    }
    
    public boolean equals(Object o) {
        if(o instanceof OpticalCrossConnectLink) {
            final OpticalCrossConnectLink other = (OpticalCrossConnectLink)o;
            return this.sPort.equals(other.sPort) && this.dPort.equals(other.dPort);
        }
        return false;
    }
    
    public String getStringStatus() {
        switch(status.intValue()) {
            case OK: {
                return "OK";
            }
            case ERROR: {
                return "ERROR";
            }
        }
        return "NoSuchStatus";
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ OpticalCrossConnect: ").append(sPort).append(" -> ").append(dPort).append(" = " + getStringStatus() + " ]");
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return sPort.hashCode() + dPort.hashCode();
    }
}
