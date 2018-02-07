package lia.Monitor.monitor;

import java.util.Map;

public class ILink implements java.io.Serializable  {
    /**
     * 
     */
    private static final long serialVersionUID = -675553368589745168L;
    
    public  Map<String, String>  from;
    public  Map<String, String> to;
    public String fromIP;
    public String toIP;
    public String name ;
    public Object data;
    public double fromLAT;
    public double toLAT;
    public double fromLONG;
    public double toLONG;
    public double speed; 
    public double [] peersQuality;
    public double [] inetQuality;
    public long time;
    public long timePeers;
    public long timeInet;
    
    public ILink ( String name ) {
        this.name = name;
    }

    /**
     * 		ILINK=sidFrom->sidTo from [longFrom,latFrom] to [longTo,latTo]
     */
    public String toString() { 
        return " ILINK="+name + " from [" + fromLONG+ "," + fromLAT + "] to ["+ toLONG+ ","+ toLAT +"]";
    }
    
    /**
     * compares two ilinks based on name field.<br>
     * the strings that are compared should look like this:
     * 		sidFrom->sidTo 
     */
    public boolean equals(Object obj)
    {
        return this==obj || (obj instanceof ILink) && ((ILink)obj).name.equals(name);
    }
    
    public int hashCode()
    {
        return name.hashCode();
    }
}
