package lia.util;

import java.io.Serializable;
import java.util.Comparator;

import net.jini.core.lookup.ServiceID;

public final class ServiceIDComparator implements Comparator<ServiceID>, Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 2661563069251747189L;
    
    private static final ServiceIDComparator INSTANCE = new ServiceIDComparator();
    
    public static final ServiceIDComparator getInstance() {
        return INSTANCE;
    }
    
    private ServiceIDComparator() {
        //singleton
    }

    /**
     * 
     * @throws NullPointerException if one of the two parameters is null; if both are null returns 0
     */
    public int compare(ServiceID sid1, ServiceID sid2) {
        
        if(sid1 == sid2) return 0;
        
        if(sid1 == null || sid2 == null) {
            throw new NullPointerException("Null serviceIDs");
        }
        
        final long lm1 = sid1.getMostSignificantBits();
        final long lm2 = sid2.getMostSignificantBits();
        
        if(lm1 < lm2) {
            return -1;
        } else if(lm1 > lm2) {
            return 1;
        } else {
            final long ll1 = sid1.getLeastSignificantBits();
            final long ll2 = sid1.getLeastSignificantBits();
            if(ll1 < ll2) {
                return -1;
            } else if(ll1 > ll2) {
                return 1;
            }
        }
        
        return 0;
    }

}
