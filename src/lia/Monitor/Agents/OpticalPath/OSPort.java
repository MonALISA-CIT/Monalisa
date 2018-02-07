package lia.Monitor.Agents.OpticalPath;

import java.io.Serializable;


public class OSPort implements Serializable {

    private static final long serialVersionUID = 483230699585649938L;
    
    public static final short  INPUT_PORT         =   1;
    public static final short  OUTPUT_PORT        =   2;
    
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
    public Double power;
    
    /**
     * Could be INPUT_PORT or OUTPUT_PORT
     */
    public Short  type;
    
    /**
     * This should NOT be used!
     *
     */
    
    public Double minPower;
    
    public Double maxPower;
    
    private OSPort() {
        this(null, null, null, null);
    }
    
    public OSPort(String portName, Short type) {
        this(portName, null, null, type);
    }
    
    public OSPort(String portName, short type) {
        this(portName, null, null, new Short(type));
    }

    public OSPort(String portName, String label, Double power, Short type) {
        this.name = portName;
        this.label = label;
        this.power = power;
        this.type = type;
        minPower = Double.valueOf(-20);
        maxPower = Double.valueOf(20);
    }
    
    public int hashCode() {
        return name.hashCode() + type.hashCode();
    }
    
    public boolean equals(Object o) {
        OSPort osp = (OSPort)o;
        return (osp.name.equals(name) && osp.type.equals(type));
    }
    
    public OSPort getPear() {
        return new OSPort(name, (type.shortValue()==INPUT_PORT)?OUTPUT_PORT:INPUT_PORT);
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if(label!=null) {
            sb.append(" - ").append(label);
        }
        sb.append(" ~ ");
        sb.append((type.shortValue()==INPUT_PORT)?"IN":"OUT").append(" ~ ");
        sb.append(" Power:  ").append(power).append(" ==-== ");
        
        return sb.toString();
    }
}
