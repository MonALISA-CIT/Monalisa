package lia.Monitor.Agents.OpticalPath;

import java.io.Serializable;

/**
 *	This class describes an "OpticalLink" between two
 *  Optical Switches
 */
public class OpticalLink implements Serializable {


    private static final long serialVersionUID = 3904683773307009330L;
    
    public static final short TYPE_HOST       =   1;
    public static final short TYPE_SWITCH     =   2;

    /**
     * Optical Fiber present
     */
    public static final int CONNECTED		= 1;
    
    /**
     * Light present ... connection made by ml_path ( opticalLinkID != null )
     * MUST BE CONNECTED
     */
    public static final int ML_CONN         = 2;

    /**
     * Optical Fiber present
     * No Light is present
     */
    public static final int FREE            = 4;

    /**
     * No Optical Fiber present
     * No Light is present
     */
    public static final int DISCONNECTED	= 8;
    
    /**
     * CONNECTED, No Light but opticalLinkID != null
     */
    public static final int CONN_FAIL       = 16;
    
    /**
     * CONNECTED & Input Light but no opticalLinkID or cross connect
     */
    public static final int IDLE_LIGHT      = 32;
    
    /**
     * CONNECTED & Light & Cross-Connect
     */
    public static final int OTHER_CONN      = 64;
    
    
    private transient static final int[] STATES = new int[] {
        CONNECTED, ML_CONN, FREE, DISCONNECTED, CONN_FAIL, IDLE_LIGHT, OTHER_CONN
    };
    
    private transient static final String[] STATE_NAMES = new String[]{
        "CONNECTED", "ML_CONN", "FREE", "DISCONNECTED", "CONN_FAIL", "IDLE_LIGHT", "OTHER_CONN"
        };
    
    public static final double MIN_QUAL			=	-500;
    public static final double MAX_QUAL			=	500;
    
    
    public static transient final Integer CONNECTED_FREE = Integer.valueOf(CONNECTED | FREE); 
    public static transient final Integer CONNECTED_ML_CONN = Integer.valueOf(CONNECTED | ML_CONN); 
    public static transient final Integer CONNECTED_ML_IDLE_LIGHT = Integer.valueOf(CONNECTED | IDLE_LIGHT); 
    public static transient final Integer CONNECTED_OTHER_CONN = Integer.valueOf(CONNECTED | OTHER_CONN); 

    /**
     * local port name
     */
    public final OSPort port;
    
    /**
     * the name of the connected peer
     */
    public final String destination;
    
    /**
     * destination port name
     */
    public final String destinationPortName;

    /**
     * quality of the link - will be used by the routing algorithm
     */
    public Double quality;
    
    /**
     * this should identify if a link is made from ml_path command or not
     */
    public String opticalLinkID;
    
    /**
     * type of the connected peer ( switch or host )
     */ 
    public Short  type;
    
    /**
     * current state of the link
     */
    public Integer state;
    
    public OpticalLink(OSPort port, String destination, String destinationPortName, Double quality, Short type, Integer state){
        this.port = port;
        this.destination = destination;
        this.destinationPortName = destinationPortName;
        this.quality = quality;
        this.type = type;
        this.state = state;
    }

    public OpticalLink(OSPort port, String destination, Double quality, Short type, Integer state){
        this(port, destination, null, quality, type, state);
    }

    public OpticalLink(OpticalLink oldOL){
        this.port = oldOL.port;
        this.destination = oldOL.destination;
        this.destinationPortName = oldOL.destinationPortName;
        this.quality = oldOL.quality;
        this.type = oldOL.type;
        this.state = oldOL.state;
    }
    
    public String getDecodedState() {
        if(state == null) return "state is NULL!!!!!!";
        StringBuilder sb = new StringBuilder();
        boolean bFirst = true;
        for(int i=0; i<STATES.length; i++) {
            if((state.intValue() & STATES[i]) == STATES[i]) {
                if(!bFirst) {
                    sb.append(" | ");
                }
                sb.append(STATE_NAMES[i]);
                bFirst = false;
            }
        }
        return sb.toString();
    }
    
    public String getDecodedType() {
        if(type == null) return null;
        switch(type.shortValue()){
            case TYPE_HOST: return "host";
            case TYPE_SWITCH: return "switch";
        	default: return "Unknown == " + type;
        }
    }

    public boolean equals(Object o) {
        if(o == null || !(o instanceof OpticalLink)) return false;
        
        OpticalLink ol = (OpticalLink)o;
        if(this.port != null && ol.port != null && ol.port.equals(this.port) 
                && this.destination != null && ol.destination != null && ol.destination.equals(this.destination)
                ) {
                return true;
         }
        return false;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("[ OpticalLink: ");

        sb.append("LocalPort: ").append(port.toString()).append(" -> ").append(destination);
        if(destinationPortName != null) {
            sb.append(":").append(destinationPortName);
        }
        sb.append("\tQual <-> ").append(quality);
        sb.append("\tType <-> ").append(getDecodedType());
        sb.append("\t{ ").append(getDecodedState()).append(" }");
        sb.append("\topticalLinkID <-> ").append(opticalLinkID);
        sb.append(" ]");
        
        return sb.toString();
    }

    @Override
    public int hashCode() {
        // TODO Auto-generated method stub
        return this.port.hashCode();
    }
    
}
