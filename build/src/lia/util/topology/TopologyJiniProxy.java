package lia.util.topology;

import java.io.Serializable;
import java.util.Date;

import lia.Monitor.monitor.TopologySI;

public class TopologyJiniProxy implements Serializable, TopologySI {
    
    //keep the compatibility ....
    static final long serialVersionUID = -6099649664946270329L;
    
    public Long rTime;
    public Date rDate;
}
