/*
 * $Id: OsrpLtp.java 6865 2010-10-10 10:03:16Z ramiro $
 *  
 * Created on Oct 26, 2007
 */
package lia.Monitor.ciena.osrp.topo;

import lia.Monitor.ciena.osrp.tl1.OsrpTL1Response;
import lia.Monitor.ciena.osrp.tl1.TL1Util;

/**
 * 
 * Encapsulates an OSRP LTP ( Link Termination Point ) parameteres
 * Each OSRP LTP may aggregate multiple OSRP CTPs. 
 * 
 * It wraps two TL1 commands:<br> 
 * rtrv-osrp-ltp<br>
 * rtrv-osrp-routemetric<br>
 * 
 * @see OsrpCtp
 * @see OsrpNode 
 * 
 * @author ramiro
 * 
 */
public class OsrpLtp {

    //TODO !!! Buggy Doc
    /**
     * The OSRP node parameter specifies the node name or OSRP node
     * identifier of the OSRP node
     */
    public final String osrpNode;

    /**
     * The administrative weight parameter indicates the level of importance given to
     * the OSRP LTP. The OSRP LTP with the lowest weight is chosen for OSRP first
     */
    public final int admw;

    /**
     * The alias parameter indicates the user-defined label for the OSRP LTP
     */
    public final String alias;
    
    /**
     * The delay parameter indicates the physical delay of signals in milliseconds for
     * the OSRP LTP
     */
    public final long delay;
    
    /**
     * The hello state parameter indicates the state of Hello protocol
     */
    public final String hState;
    
    /**
     * The locality parameter indicates whether the node is local, a neighbor, or remote
     * 
     * @see OsrpNode.locality
     */
    public final short locality;
    
    /**
     * The OSRP CAC termination points parameter is a list of CAC termination points
     * that are aggregated to make up this OSRP link termination point. This list can
     * have zero to 20 local supporting TTPs (physical port) of OSRP CAC termination
     * points
     */
    public final String[] osrpCtps;

    /**
     * The OSRP link termination point ID parameter indicates the port identifier of the
     * OSRP LTP for which information is being retrieved
     */
    public final int osrpLtpId;
    
    public final boolean oobEnabled;
    
    /**
     * The protection bundle identifier parameter indicates the ID of the protection
     * bundles to which the specified OSRP link termination point belongs. The value 0
     * means that the OSRP LTP does not belong to any protection bundles
     */
    public final int pBId;
    
    /**
     * The remote node OSRP link termination point alias parameter indicates the first
     * 32 bytes of the label of the remote link (the corresponding link coming from the
     * other node). This is valid only when the hello state has reached the two way
     * inside state.
     */
    public final String rmtAlias;
    
    /**
     * The remote node OSRP link termination point identifier parameter indicates the
     * OSRP LTP identifier of the remote link (that is, the corresponding link coming
     * from the other node). This is valid only when the hello state has reached the two
     * way inside state.
     */
    public final int rmtLId;

    /**
     * The remote node parameter indicates the node name of the remote link (that is,
     * the corresponding link coming from the other node). This is valid only when the
     * hello state has reached the two way inside state.
     */
    public final String rmtName;
    
    ///////////////////////////////////////////////
    /////// params from from rtrv-osrp-routemetric
    //////////////////////////////////////////////

    /**
     * The maximum bandwidth parameter indicates the maximum bandwidth available
     * on this OSRP link for the specific service class
     */
    public final long maxBW;
    

    /**
     * The available bandwidth remaining parameter indicates the available bandwidth
     * on the interface for this specific service class
     */
    public final long avlBW;
    
    /**
     * The priority parameter indicates the service priority level of the link
     */
    public final long prio;
    
    /**
     * The support protection types parameter indicates all protection types supported
     * for the OSRP termination point
     */
    public final String prot;
    
    /**
     * The transfer delay parameter indicates the number of microseconds that transfer
     * is delayed for the specified categories
     */
    public final long xferd;
    
    OsrpLtp(final String osrpNode, final int admw, final String alias,
            final long delay, final String hState, final short locality, 
            final String[] osrpCtps, final int osrpLtpId, final boolean oobEnabled,
            final int pBId, final String rmtAlias, final int rmtLId,
            final String rmtName, final int maxBW, final int avlBW,
            final long prio, final String prot, final long xferd) {
        
        this.osrpNode = osrpNode;
        this.admw = admw;
        this.alias = alias;
        this.delay = delay;
        this.hState = hState;
        this.locality = locality;
        this.osrpCtps = osrpCtps;
        this.osrpLtpId = osrpLtpId;
        this.oobEnabled = oobEnabled;
        this.pBId = pBId;
        this.rmtAlias = rmtAlias;
        this.rmtLId = rmtLId;
        this.rmtName = rmtName;
        this.maxBW = maxBW;
        this.avlBW = avlBW;
        this.prio = prio;
        this.prot = prot;
        this.xferd = xferd;
    }
    
    static final OsrpLtp fromOsrpTL1Response(final String swName, final int ltpId, final OsrpTL1Response ltpResponse, final OsrpTL1Response routeMetricResponse) {
        final String alias = TL1Util.getStringVal("ALIAS", ltpResponse);
        final String rmtName = TL1Util.getStringVal("RMTNM", ltpResponse);
        final int rmtLId = TL1Util.getIntVal("RMTLID", ltpResponse);
        final String rmtAlias = TL1Util.getStringVal("RMTALIAS", ltpResponse);
        final long delay = TL1Util.getLongVal("DELAY", ltpResponse);
        final int pBId = TL1Util.getIntVal("PBID", ltpResponse);
        final String hState = (String)ltpResponse.paramsMap.get("HSTATE");
        final String osrpCtpsList = TL1Util.getStringVal("OSRPCTPS", ltpResponse);
        final String[] osrpCtps = (osrpCtpsList != null)?osrpCtpsList.split("&"):null; 
        final int admw = TL1Util.getIntVal("ADMW", ltpResponse);
        final String oobEnabledProp = TL1Util.getStringVal("OOBENABLED", ltpResponse);
        final boolean oobEnabled = (oobEnabledProp != null && oobEnabledProp.trim().equalsIgnoreCase("YES"))?true:false;
        final short locality = OsrpNode.getLocality(TL1Util.getStringVal("LOCAL", ltpResponse));
        
        if(routeMetricResponse == null) {
            return new OsrpLtp(swName, admw, alias, delay, hState, locality, 
                               osrpCtps, ltpId, oobEnabled, pBId, rmtAlias, rmtLId, rmtName,
                               -1, -1, -1, "N/A", -1);
        }
        
        final int maxBW = TL1Util.getIntVal("MAXBW", routeMetricResponse);
        final int avlBW = TL1Util.getIntVal("AVLBW", routeMetricResponse);
        final long prio = TL1Util.getLongVal("PRIO", routeMetricResponse);
        final String proto = TL1Util.getStringVal("PROTO", routeMetricResponse);
        final long xferd = TL1Util.getLongVal("XFERD", routeMetricResponse);
        
        return new OsrpLtp(swName, admw, alias, delay, hState, locality, 
                           osrpCtps, ltpId, oobEnabled, pBId, rmtAlias, rmtLId, rmtName,
                           maxBW, avlBW, prio, proto, xferd);
    }
}
