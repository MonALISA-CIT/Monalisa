package lia.ws;

import java.io.Serializable;
import java.util.Date;

/**
 * @author mickyt
 */
public class WSConf implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -3210258448012425086L;

    private WSFarm wsFarm;

    private long confTime;

    public WSConf() {

    }

    public long getConfTime() {
        return confTime;
    }

    public void setConfTime(long confTime) {
        this.confTime = confTime;
    }

    public WSFarm getWsFarm() {
        return wsFarm;
    }

    public void setWsFarm(WSFarm wsFarm) {
        this.wsFarm = wsFarm;
    }

    public String toString() {
        return "WSConf time: " + new Date(confTime) + wsFarm.toString();
    }
}
