/*
 * $Id: OsrpNode.java 6865 2010-10-10 10:03:16Z ramiro $
 * 
 * Created on Oct 26, 2007
 */
package lia.Monitor.ciena.osrp.topo;

import java.net.InetAddress;
import java.util.Map;

import lia.Monitor.ciena.osrp.tl1.OsrpTL1Response;
import lia.Monitor.ciena.osrp.tl1.TL1Util;

/**
 * 
 * @author ramiro
 * 
 */
public class OsrpNode {
    
    public static final short   LOCAL       =   0;
    public static final short   REMOTE      =   1;
    public static final short   NEIGHBOR    =   2;
    
    public static final String[] LOCALITY_STATES = {"LOCAL", "REMOTE", "NEIGHBOR"};
    
    /**
     * OSRP Node ID for the CD/CI
     */
    public final String id;
    
    /**
     * Name of the CD/CI
     */
    public final String name;
    
    /**
     * The locality of the switch. (LOCAL, NEIGHBOR, REMOTE)
     */
    public final short locality;
    
    /**
     * IP Address of the CD/CI
     */
    public final InetAddress ipAddress;
    
    /**
     * The key is the CTP name; value {@link OsrpCtp}
     */
    public final Map osrpCtpsMap;
    
    /**
     * The key is the LTP ID; the value {@link OsrpLtp}}
     */
    public final Map osrpLtpsMap;
    
    OsrpNode(final String id, final String name, final short locality, final InetAddress ipAddress, Map osrpCtpsMap, Map osrpLtpsMap) {
        this.id = id;
        this.name = name;
        this.locality = locality;
        this.ipAddress = ipAddress;
        this.osrpCtpsMap = osrpCtpsMap;
        this.osrpLtpsMap = osrpLtpsMap;
    }
    
    public static final String getLocality(final short locality) {
        String lStr = "N/A";
        if(locality >= 0) {
            for(int i=0; i < LOCALITY_STATES.length; i++) {
                if(locality == i) {
                    lStr = LOCALITY_STATES[i];
                    break;
                }
            }
        }
        
        return lStr + "(" + locality + ")";
    }
    
    public static final short getLocality(final String localityStr) {
        
        if(localityStr != null) {
            for(int i=0; i<LOCALITY_STATES.length; i++) {
                if(localityStr.equals(LOCALITY_STATES[i])) {
                    return (short)i;
                }
            }
        }
        
        return -1;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("OsrpNode Id:").append(id).append(", name:").append(name).append(", locality:").append(getLocality(locality)).append(", ipAddr:").append(ipAddress);
        return sb.toString();
    }
    
    public final static OsrpNode fromOsrpTL1Response(final OsrpTL1Response tl1Response, final Map osrpCtpsMap, final Map osrpLtpsMap) throws Exception {
        final String id = (String)tl1Response.singleParams.get(0);
        final String swName = TL1Util.getStringVal("SWNAME", tl1Response);
        final InetAddress ia = InetAddress.getByName(TL1Util.getStringVal("IPADDR", tl1Response));
        final short locality = getLocality(TL1Util.getStringVal("LOCAL", tl1Response));
        if(locality == LOCAL) {
            return new OsrpNode(id, swName, locality, ia, osrpCtpsMap, osrpLtpsMap);
        }
        return new OsrpNode(id, swName, locality, ia, null, osrpLtpsMap);
    }
}
