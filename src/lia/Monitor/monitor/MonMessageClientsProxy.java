package lia.Monitor.monitor;

import java.io.Serializable;

import net.jini.core.lookup.ServiceID;

public class MonMessageClientsProxy extends monMessage implements Serializable {
	
	/**
     * Before toString() 
     */
    private static final long serialVersionUID = 3687522097552925228L;
    
    public final ServiceID farmID;

	public MonMessageClientsProxy(String tag, Object ident, Object result, ServiceID farmID) {
        super(tag, ident, result);
        this.farmID = farmID;
	}

    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append(" farmID=").append(farmID);
        sb.append("; ").append(super.toString());
        
        return sb.toString();
    }
}
