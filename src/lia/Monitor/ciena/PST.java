/*
 * $Id: PST.java 6865 2010-10-10 10:03:16Z ramiro $
 * 
 * Created on Nov 2, 2007
 */
package lia.Monitor.ciena;


/**
 * 
 * Place holder for all CD/CI Primary State (PST). <br>
 * The primary state parameter is used to indicate the operation state of the equipment being retrieved. The following
 * TL1 commands use the PST parameter:<br>
 * RTRV-CRS-STSpc<br>
 * RTRV-EQPT<br>
 * RTRV-FFP-OCn<br>
 * RTRV-GTP<br>
 * RTRV-OCn<br>
 * RTRV-OSRP-CTP<br>
 * RTRV-OSRP-LTP<br>
 * RTRV-OSRP-NODE<br>
 * RTRV-SNC-STSpc<br>
 * RTRV-VRPG-OCn<br>
 * 
 * @author ramiro
 */
public final class PST {

    /**
     * This STATE is undefined, or (State N/A). It should
     * be used only to signal state which is Not Available
     * 
     */
    public static final short    S_NA       = (short) 0;

    /**
     * In service, normal.
     */
    public static final short    IS_NR      = (short) 1;

    /**
     * In service, abnormal.
     */
    public static final short    IS_ANR     = (short) 2;

    /**
     * Out of service, autonomous. Equipment has been removed from service automatically.
     */
    public static final short    OOS_AU     = (short) 3;

    /**
     * Out of service, autonomous managed. Equipment has been removed from service automatically and managed out of
     * service by a user.
     */
    public static final short    OOS_AUMA   = (short) 4;

    /**
     * Out of service, managed. Equipment has been managed out of service by a user.
     */
    public static final short    OOS_MA     = (short) 5;

    public static final String[] PST_STATES = {
            "S-NA", "IS-NR", "IS-ANR", "OOS-AU", "OOS-AUMA", "OOS-MA"
                                            };

    public static final short pstFromString(final String pstStr) {
        if(pstStr != null) {
            for(int i=1; i<PST_STATES.length; i++) {
                if(PST_STATES[i].equalsIgnoreCase(pstStr)) {
                    return (short)i;
                }
            }
        }//end if
        
        return 0;
    }
    
    public static final String pstString(final short pst) {
        if(pst < 0 || pst >= PST_STATES.length) {
            return PST_STATES[0];
        }
        return PST_STATES[pst];
    }

}
