/*
 * Created on Jun 26, 2012
 */
package lia.Monitor.ciena.eflow;

import java.io.Serializable;


/**
 *
 * @author ramiro
 */
public class EFlowStatsSer implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 5158144792246786169L;

    private final String name;
    private final double speed;
    private final long timestamp;
    private final long timestampNano;
    
    /**
     * @param name
     * @param speed
     * @param timestamp
     * @param timestampNano
     */
    private EFlowStatsSer(String name, double speed, long timestamp, long timestampNano) {
        this.name = name;
        this.speed = speed;
        this.timestamp = timestamp;
        this.timestampNano = timestampNano;
    }
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("EFlowStatsSer [name=")
               .append(name)
               .append(", speed=")
               .append(speed)
               .append(", timestamp=")
               .append(timestamp)
               .append(", timestampNano=")
               .append(timestampNano)
               .append("]");
        return builder.toString();
    }
    
    public static EFlowStatsSer fromEFlowStat(EFlowStats eflowStats) {
        return new EFlowStatsSer(eflowStats.mlName, eflowStats.getLastSpeed(), eflowStats.lastUpdateMillis(), eflowStats.lastUpdateNano());
    }
}
