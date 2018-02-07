package lia.ws;

import java.io.Serializable;

/**
 * @author mickyt
 */
public class WSFarm implements Serializable {

    private static final long serialVersionUID = 5801980349730579730L;

    private String farmName;

    private WSCluster[] clusterList;

    public WSFarm() {
    }

    public WSCluster[] getClusterList() {
        return clusterList;
    }

    public void setClusterList(WSCluster[] clusterList) {
        this.clusterList = clusterList;
    }

    public String getFarmName() {
        return farmName;
    }

    public void setFarmName(String farmName) {
        this.farmName = farmName;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("WSFarm Name: " + farmName);
        sb.append("\nWSFarm Clusters [ " + ((clusterList != null) ? clusterList.length : 0) + " ] : ");
        for (int i = 0; clusterList != null && i < clusterList.length; i++) {
            sb.append("\n " + (i + 1) + ": " + clusterList[i].toString());
        }
        return sb.toString();
    }
}
