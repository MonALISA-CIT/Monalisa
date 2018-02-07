/*
 * $Id: GenericUDPResult.java 6865 2010-10-10 10:03:16Z ramiro $
 */
package lia.Monitor.monitor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 
 * @author ramiro
 */
public class GenericUDPResult {

    public long rtime;
    public long senderID;
    public String clusterName;
    public String nodeName;
    public final List paramValues;
    //this was added to keep track of params order ...
    public final List paramNames;

    public GenericUDPResult() {
        senderID = -1;
        paramValues = new ArrayList();
        paramNames = new ArrayList();
    }

    public void addParam(String name, Object value) {
        if (name == null || value == null) {
            return;
        }
        if (name.trim().length() == 0) {
            return;
        }
        paramNames.add(name);
        paramValues.add(value);
    }

    //Just for Debugging 
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(" [ ").append(new Date(rtime)).append(" ] ");
        sb.append("Cluster:").append(clusterName).append("\t");
        sb.append("Node:").append(nodeName).append("\t");

        if (paramValues != null && paramValues.size() > 0) {
            final int len = paramValues.size();
            for (int i = 0; i < len; i++) {
                sb.append(paramNames.get(i)).append(" = ").append(paramValues.get(i)).append("\t");
            }
        } else {
            sb.append(" No PARAMS!");
        }
        return sb.toString();
    }
}

