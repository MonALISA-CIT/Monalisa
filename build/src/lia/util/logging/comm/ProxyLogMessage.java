package lia.util.logging.comm;

import java.io.Serializable;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import lia.util.Utils;

public class ProxyLogMessage implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 7725500080819805874L;
    
    private static AtomicLong seq = new AtomicLong(0);
    public long ackID;
    public long id;
    public Properties props;
    
    public ProxyLogMessage() {
        id = seq.getAndIncrement();
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder(2048);
        try {
            sb.append(" id: ").append(id).append(" ackID: ").append(ackID);
            sb.append(" Props: \n" ).append(props).append("\n");
        }catch(Throwable t) {
            sb.append("Got exc in ProxyLogMessage.toString()").append(Utils.getStackTrace(t)).append("\n");
        }
        return sb.toString();
    }
}
