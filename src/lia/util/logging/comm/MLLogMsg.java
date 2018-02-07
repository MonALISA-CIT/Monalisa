package lia.util.logging.comm;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

import lia.util.Utils;
import lia.util.logging.MLLogRecord;

public class MLLogMsg implements Serializable {
    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = -7488458749802182802L;
    private transient static final AtomicLong seq = new AtomicLong(0);
    public long id;
    public MLLogRecord[] lrs;
    public boolean reqNotif;
    
    public MLLogMsg() {
        this.id = seq.getAndIncrement();
        reqNotif = false;
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder(1024);
        try {
            sb.append("[ ").append(id).append(" ] :- reqNotif").append(reqNotif).append("\n");
            if(lrs == null) {
                sb.append(" Null MLLogRecord[]\n");
            } else {
                for(int i=0; i<lrs.length; i++) {
                    sb.append(" [").append(i).append("] = ");
                    if(lrs[i] == null) {
                        sb.append("null");
                    } else {
                        sb.append(lrs[i].toString());
                    }
                    sb.append("\n");
                }
            }
        }catch(Throwable t) {
            sb.append("Got exc in MLLogMsg.toString()").append(Utils.getStackTrace(t)).append("\n");
        }
        return sb.toString();
    }
}
