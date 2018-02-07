package lia.Monitor.Agents.OpticalPath.v2.State;

import java.io.Serializable;

/**
 *	This class describes an "OSwLink" between two
 *  Optical Switches
 */
public class OSwLink implements Serializable {

    private static final long serialVersionUID = 2768846788588242433L;
    
    public static final transient short UNKNOWN_TYPE    =   0;
    public static final transient short TYPE_HOST       =   1;
    public static final transient short TYPE_SWITCH     =   2;

    public static final transient double MIN_QUAL       =   Double.MIN_VALUE;
    public static final transient double MAX_QUAL       =   Double.MAX_VALUE;
    

    /**
     * the name of the connected peer
     */
    public String destination;
    
    /**
     * destination port name
     */
    public String destinationPortName;

    /**
     * quality of the link - will be used by the routing algorithm
     */
    public double quality;
    
    /**
     * type of the connected peer ( switch or host )
     */ 
    public short  type;
    
    private OSwLink() {//this should not be used ...
        this(null, null, MIN_QUAL, UNKNOWN_TYPE);
    }
    
    public OSwLink(String destination, String destinationPortName, double quality, short type){
        this.destination = destination;
        this.destinationPortName = destinationPortName;
        this.quality = quality;
        this.type = type;
    }

    public String getDecodedType() {
        if(type == 0) return "unknown";
        switch(type){
            case TYPE_HOST: return "host";
            case TYPE_SWITCH: return "switch";
        	default: return "unknown";
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("[ OSwLink: -> ").append(destination);
        sb.append(":").append(destinationPortName);
        sb.append("\tQual <-> ").append(quality);
        sb.append("\tType <-> ").append(getDecodedType());
        sb.append(" ]");
        
        return sb.toString();
    }
    
}
