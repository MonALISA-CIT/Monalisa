/*
 * Created on Jul 2, 2012
 */
package lia.Monitor.ciena.eflow.client;

import lia.Monitor.tcpClient.MLSerClient;
import net.jini.core.lookup.ServiceID;


/**
 *
 * @author ramiro
 */
public class BasicServiceNode {

    protected final ServiceID serviceID;
    protected final String serviceName;
    protected final MLSerClient client;

    /**
     * 
     */
    protected BasicServiceNode(final ServiceID serviceID, final String serviceName, MLSerClient client) {
        if(serviceID == null) {
            throw new NullPointerException("Null service ID");
        }
        
        if(serviceName == null) {
            throw new NullPointerException("Null service name");
        }
        
        if(client == null) {
            throw new NullPointerException("Null client");
        }
        
        this.serviceID = serviceID;
        this.serviceName = serviceName;
        this.client = client;
    }

    
    /**
     * @return the serviceID
     */
    public ServiceID getServiceID() {
        return serviceID;
    }

    
    /**
     * @return the serviceName
     */
    public String getServiceName() {
        return serviceName;
    }


    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((serviceID == null) ? 0 : serviceID.hashCode());
        return result;
    }


    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BasicServiceNode other = (BasicServiceNode) obj;
        if (serviceID == null) {
            if (other.serviceID != null)
                return false;
        } else if (!serviceID.equals(other.serviceID))
            return false;
        return true;
    }


    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("BasicServiceNode [serviceID=").append(serviceID).append(", serviceName=").append(serviceName).append("]");
        return builder.toString();
    }

    
}
