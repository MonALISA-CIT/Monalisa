package lia.Monitor.Agents.OpticalPath.v2.State;

import java.io.Serializable;

/**
 * This class describes a cross connection inside the switch
 */
public class OSwCrossConn implements Serializable {
    
    private static final long serialVersionUID = 170236909126673172L;

    public transient static final short UNKNOWN_STATUS      = 0;

    public transient static final short CCONNOK             = 1;
    public transient static final short CCONNERR            = 2;
    
    //Transitive states
    public transient static final short WFORCCONN1          = 3;
    public transient static final short WFORCCONN2          = 4;
    
    public transient static final short WFORFREE1           = 5;
    public transient static final short WFORFREE2           = 6;
    
    transient static final String[] CCONNS_STATE_NAMES =  {
        "UNKNOWN_STATUS",
        "CCONNOK", 
        "CCONNERR", 
        "WFORCCONN1", 
        "WFORCCONN2", 
        "WFORFREE1", 
        "WFORFREE2"
    };
    
    /**Source port*/
    public OSwPort sPort;
    
    /**Destination port*/
    public OSwPort dPort;
    
    public volatile short state;
    
    /**
     * If the connection is done by ML Agents this should identify the ML Path
     */
    public String mlID;
    
    private OSwCrossConn() {//this should not be used ...
        this(null, null, UNKNOWN_STATUS);
    }

    public OSwCrossConn(OSwPort sPort, OSwPort dPort, short state) {
        this.sPort = sPort;
        this.dPort = dPort;
        this.state = state;
    }
    
    public boolean equals(Object o) {

        OSwCrossConn ol = (OSwCrossConn)o;
        if(this.sPort != null && ol.sPort != null && ol.sPort.equals(this.sPort)) {
            if(this.dPort != null && ol.dPort != null && ol.dPort.equals(this.dPort)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Hash code over the same fields equals() operates
     */
    public int hashCode() {
    	int code = sPort!=null ? sPort.hashCode() : 1;
    	
    	code = code*31 + (dPort!=null ? dPort.hashCode() : 1);
    	
    	return code;
    }
    
    public static final String decodeState(final short state) {
        return "(" + state +" ) = " + OSwPort.internalDecodedName(CCONNS_STATE_NAMES, state);
    }
    
    public String getDecodedState() {
        return decodeState(state);
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{ OSwCConn ").append(sPort).append(" -> ").append(dPort).append(", State ").append(getDecodedState()).append(" }");
        return sb.toString();
    }
}
