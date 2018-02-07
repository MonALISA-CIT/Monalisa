/*
 * Created on Mar 23, 2010
 */
package lia.net.topology;

import java.io.Serializable;
import java.util.UUID;


/**
 *
 * @author ramiro
 */
public class TopoMsg implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -4551900465434408574L;

    public static enum Type {
        ML_LINKS,
        AFOX_CONFIG,
        HOST_CONFIG,
        CIENA_CONFIG,
        FORCE10_CONFIG
    }
    
    private final int msgVersion;
    private final Type msgType;
    private final UUID agentID;
    private final UUID serviceID;
    
    private final Object payload;
    
    public TopoMsg(UUID agentID, UUID serviceID, TopoMsg.Type msgType, Object payload) {
        this.msgVersion = 2;
        this.msgType = msgType;
        this.payload = payload;
        this.agentID = agentID;
        this.serviceID = serviceID;
    }
    
    public <T> T payload(Class<T> type) {
        return type.cast(payload);
    }
    
    public Type type() {
        return msgType;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TopoMsg(").append(msgVersion).append(") type: ").append(msgType);
        sb.append(", agentID:").append(agentID).append(", serviceID: ").append(serviceID);
        if(msgType == Type.ML_LINKS) {
            sb.append(payload);
        }
        return sb.toString();
    }
}
