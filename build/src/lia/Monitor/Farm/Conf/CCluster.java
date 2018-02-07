/*
 * $Id: CCluster.java 6865 2010-10-10 10:03:16Z ramiro $
 */

package lia.Monitor.Farm.Conf;

import java.util.concurrent.ConcurrentHashMap;

import lia.Monitor.monitor.MCluster;

/**
 * Helper class which records the timestamp for the last received Result 
 * @author ramiro
 */
public final class CCluster extends AbstractConfigTimeoutItem {

    //K: NodeName V:CNode
    public final ConcurrentHashMap<String, CNode> cNodes;
    public final String moduleName;
    final MCluster cluster;
    
    public CCluster(final String moduleName, final MCluster cluster, long renewInterval) {
        super(cluster.name, renewInterval);
        if(moduleName == null) throw new NullPointerException("ModuleName cannot be null for CCluster constructor");
        this.cluster = cluster;
        this.cNodes = new ConcurrentHashMap<String, CNode>();
        this.moduleName = moduleName;
    }
    
    public void timedOut() {
        confVerifier.removeCluster(this);
    }

    public String toString() {
        return "{CCluster: " + super.toString() + "}";
    }
}
