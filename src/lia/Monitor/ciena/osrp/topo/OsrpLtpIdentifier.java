/*
 * $Id: OsrpLtpIdentifier.java 6865 2010-10-10 10:03:16Z ramiro $
 * 
 * Created on Nov 7, 2007
 */
package lia.Monitor.ciena.osrp.topo;


/**
 * It is used only to identify an LTP ( swName; ltpID )
 * @author ramiro
 */
class OsrpLtpIdentifier {

    public final String swName;
    public final int osrpId;
    
    OsrpLtpIdentifier(final String swName, final int osrpId) {
        this.swName = swName;
        this.osrpId = osrpId;
    }
    
    public boolean equals(Object o) {
        if(this == o) return true;
        
        if(o instanceof OsrpLtpIdentifier) {
            final OsrpLtpIdentifier other = (OsrpLtpIdentifier)o;
            return (other.swName.equals(this.swName) && osrpId == other.osrpId);
        }
        
        return false;
    }
    
    public int hashCode() {
        return (this.swName.hashCode() + osrpId);
    }
}
