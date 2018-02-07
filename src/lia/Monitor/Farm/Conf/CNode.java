/*
 * $Id: CNode.java 6865 2010-10-10 10:03:16Z ramiro $
 */

package lia.Monitor.Farm.Conf;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lia.Monitor.monitor.MNode;

/**
 * 
 * @author ramiro
 * 
 */
public final class CNode extends AbstractConfigTimeoutItem {

    //K: ParamName V:CParam
    public final ConcurrentMap<String, CParam> cParams;
    public final CCluster cCluster;
    final MNode node;
    
    public CNode(final CCluster cCluster, final MNode node, long renewInterval) {
        super(node.name, renewInterval);
        this.node = node;
        cParams = new ConcurrentHashMap<String, CParam>();
        this.cCluster = cCluster;
    }
    
    public void timedOut() {
        confVerifier.removeNode(this);
    }
    
    public String toString() {
        return "{CNode: " + super.toString() + ", CCluster: " + cCluster.name + "}";
    }

}
