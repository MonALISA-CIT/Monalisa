package lia.Monitor.Farm.Conf;



public class CParam extends AbstractConfigTimeoutItem {

    public final CNode cNode;

    public CParam(final CNode cNode, final String name, final long renewInterval) {
        super(name, renewInterval);
        this.cNode = cNode;
    }
    
    public void timedOut() {
        confVerifier.removeParam(this);
    }
    
    public String toString() {
        return "{CParam: " + super.toString() + " CNode: " + cNode.name + ", CCluster: " + cNode.cCluster.name + "}";
    }
}