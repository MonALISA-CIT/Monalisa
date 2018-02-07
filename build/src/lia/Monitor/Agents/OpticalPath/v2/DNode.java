package lia.Monitor.Agents.OpticalPath.v2;

import lia.Monitor.Agents.OpticalPath.v2.State.OSwConfig;

class DNode implements Comparable {
    
    static int NODE_SEQUENCER;
    
    /** distance from the starting node */
    double distance;
    
    OSwConfig oswConfig;
    DNode predecessor;
    
    String predecessorPort;
    String localPort;
    
    int seq;
    
    DNode(OSwConfig oswConfig) {
        this.oswConfig = oswConfig;
        this.distance = Double.MAX_VALUE;
        this.seq = NODE_SEQUENCER++;
    }

    public int compareTo(Object o) {
        if(this == o) return 0;

        DNode n = (DNode)o;
        if(n.distance > distance) {
            return 1;
        }
        
        if(n.distance == distance && this.seq > n.seq) {
            return 1;
        }
        
        return -1;
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ DNode ---> SwName: ").append(oswConfig.name).append(", localPort: ").append(localPort);
        sb.append(", predecessorPort: ").append(predecessorPort).append(" Predecessor: ").append(predecessor).append(" ]");
        return sb.toString();
    }
}
