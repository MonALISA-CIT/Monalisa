package lia.ws;

import java.util.Hashtable;

/**
 * @author mickyt
 */
public class Result implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 6198480924330340735L;

    private java.lang.String clusterName;

    private java.lang.String farmName;

    private java.lang.String nodeName;

    private Hashtable param;

    private long time;

    public Result() {
    }

    public java.lang.String getClusterName() {
        return clusterName;
    }

    public void setClusterName(java.lang.String clusterName) {
        this.clusterName = clusterName;
    }

    public java.lang.String getFarmName() {
        return farmName;
    }

    public void setFarmName(java.lang.String farmName) {
        this.farmName = farmName;
    }

    public java.lang.String getNodeName() {
        return nodeName;
    }

    public void setNodeName(java.lang.String nodeName) {
        this.nodeName = nodeName;
    }

    public Hashtable getParam() {
        return param;
    } // getParam

    public void setParam(Hashtable param) {
        this.param = param;
    } // setParam

    public long getTime() {
        return time;
    } // getTime

    public void setTime(long time) {
        this.time = time;
    } // setTime

} // class Result
