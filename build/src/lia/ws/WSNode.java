package lia.ws;

import java.io.Serializable;

/**
 * @author mickyt
 */
public class WSNode implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -954313976116906856L;

    private String nodeName;

    private String[] paramList;

    public WSNode() {
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String[] getParamList() {
        return paramList;
    }

    public void setParamList(String[] paramList) {
        this.paramList = paramList;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("WSNode Name: " + nodeName);
        sb.append("\nWSNode Params [ " + ((paramList != null) ? paramList.length : 0) + " ] : ");
        for (int i = 0; paramList != null && i < paramList.length; i++) {
            sb.append("\n " + (i + 1) + ": " + paramList[i].toString());
        }
        return sb.toString();
    }

}
