/*
 * Created on Aug 10, 2010
 *
 */
package lia.Monitor.ClientsFarmProxy;

import net.jini.core.lookup.ServiceID;

/**
 * 
 * @author ramiro
 */
public class FilterMuxKey {

    private final String filterName;
    private final ServiceID serviceID;
    
    //cache the hashcode; lasy cache should be ok
    private int hash = 0;
    
    public FilterMuxKey(final String filterName, final ServiceID serviceID) {
        if(filterName == null) {
            throw new NullPointerException("Null filterName");
        }
        
        if(serviceID == null) {
            throw new NullPointerException("Null serviceID");
        }
        this.filterName = filterName;
        this.serviceID = serviceID;
    }

    public String getFilterName() {
        return filterName;
    }

    
    public ServiceID getServiceID() {
        return serviceID;
    }

    @Override
    public int hashCode() {
        int result = hash;
        if(result == 0) {
            final int prime = 31;
            result = 1;
            result = prime * result + filterName.hashCode();
            result = prime * result + serviceID.hashCode();
            hash = result;
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FilterMuxKey other = (FilterMuxKey) obj;
        if (!filterName.equals(other.filterName))
            return false;
        if (!serviceID.equals(other.serviceID))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "FilterMuxKey [filterName=" + filterName + ", serviceID=" + serviceID + ", hash=" + hashCode() + "]";
    }

}
