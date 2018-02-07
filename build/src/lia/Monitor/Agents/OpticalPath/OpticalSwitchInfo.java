package lia.Monitor.Agents.OpticalPath;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;

public class OpticalSwitchInfo implements Serializable {


    private static final long serialVersionUID = 3906366017574810418L;
    
    public static final short       UNKNOWN             =   0;
    public static final short       CALIENT             =   1;
    public static final short       GLIMMERGLASS        =   2;

    /**
     * Switch Name
     */
    public String name;
    
    /** Calient or Glimmerglass */
    public Short  type;
    
    /**
     * Connection map
     * 
     * key - OSPort
     * value - OpticalLink
     * 
     */
    public HashMap<OSPort, OpticalLink> map;
    
    /**
     * Optical CrossConnects inside a switch
     * key - sourcePort
     */
    public HashMap<OSPort, OpticalCrossConnectLink> crossConnects;
    
    /**
     * whether the OS is responding or not to TL1 commands
     */
    public boolean isAlive;
    
    public OpticalSwitchInfo() {
        name = null;
        map = new HashMap<OSPort, OpticalLink>();
        type = new Short(CALIENT);//default
        crossConnects = new HashMap<OSPort, OpticalCrossConnectLink>();
        isAlive = false;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[OpticalSwitchInfo] Name = ");
        sb.append(name);
        sb.append("\n--->ConnMap: \n");
        if (map == null || map.size() == 0) {
            sb.append("No links defined...\n");
        } else {
            for(Iterator<OSPort> it=map.keySet().iterator();it.hasNext();) {
                OSPort portNo = it.next();
                sb.append(" [ ").append(portNo).append(" ---> ").append(map.get(portNo)).append(" ]\n");
            }
        }
        sb.append("\n--->END ConnMap\n");
        sb.append("\n--->Cross-Connect Links: \n");
        if (crossConnects == null || crossConnects.size() == 0) {
            sb.append("No Cross-Connect Links defined... (yet)\n");
        } else {
            for(Iterator<?> it=crossConnects.entrySet().iterator();it.hasNext();) {
                sb.append(it.next().toString()).append("\n");
            }
        }
        sb.append("\n--->END Cross-Connect Links\n\n");
        return sb.toString();
    }
    
    @Override
    public boolean equals(Object o) {
        if(o == null) return false;
        if(this == o) return true;
        if(o instanceof OpticalSwitchInfo){
            OpticalSwitchInfo osi = (OpticalSwitchInfo)o;
            if(this.name == null || osi.name == null) return false;
            if(this.name.equals(osi.name)) {
                if(this.map == null) {
                    if(osi.map == null) return true;
                    return false;
                }
                if(osi.map == null) return false;
                if (osi.map.equals(this.map)) {
                    if(this.crossConnects == null) {
                        if(osi.crossConnects == null) return true;
                        return false;
                    }
                    if(osi.crossConnects == null) return false;
                    return osi.crossConnects.equals(this.crossConnects);
                }
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        // TODO Auto-generated method stub
        return name.hashCode();
    }
}