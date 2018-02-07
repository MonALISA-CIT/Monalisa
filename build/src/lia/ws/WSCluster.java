package lia.ws;

import java.io.Serializable;

/**
 * @author mickyt
 */
public class WSCluster implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -4347508365251301562L;

    private String clusterName;

    private WSNode[] nodeList;

    public WSCluster() {
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public WSNode[] getNodeList() {
        return nodeList;
    }

    public void setNodeList(WSNode[] nodeList) {
        this.nodeList = nodeList;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("WSCluster Name: " + clusterName);
        sb.append("\nWSCluster Nodes [ " + ((nodeList != null) ? nodeList.length : 0) + " ] : ");
        for (int i = 0; nodeList != null && i < nodeList.length; i++) {
            sb.append("\n " + (i + 1) + ": " + nodeList[i].toString());
        }
        return sb.toString();
    }
}
