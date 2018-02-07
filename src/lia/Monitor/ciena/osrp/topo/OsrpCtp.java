/*
 * $Id: OsrpCtp.java 7419 2013-10-16 12:56:15Z ramiro $
 * 
 * Created on Oct 26, 2007
 */
package lia.Monitor.ciena.osrp.topo;

import java.util.Map;
import java.util.logging.Logger;

import lia.Monitor.ciena.PST;
import lia.Monitor.ciena.osrp.tl1.OsrpTL1Response;
import lia.Monitor.ciena.osrp.tl1.TL1Util;

/**
 * 
 * Wrapper class for all the parameters of an OSRP Call Admission Control (CAC) termination point.
 * It should contain all the params returned by the TL1 command: rtrv-osrp-ctp
 * <br><br>
 *
 * <b>Output Format</b> ( as specified in    TL1 INTERFACE MANUAL   Release 5.2 )
<pre>
            SID DATE TIME
         M  CTAG COMPLD
            "osrpCtp,[OSRPCTPID=osrpCtpCommonId],
         [OSRPOOBCTPID=osrpOobCtpCommonId],
         [RMTOSRPCTPID=remoteOsrpCtpCommonId],[ALIAS=alias],
         [RMTALIAS=remoteAlias],[RMTNM=remoteNode],
         [RMTSUPTP=remoteSupportingTP],[DELAY=delay],[OSRPLTP=osrpLtpId],
         [RMTOSRPLTP=remoteOsrpLtpId],[PST=primaryState],
         [RMTPST=remoteprimaryState]"
         ;
</pre>
 * <br>
 * Sample output for rtrv-osrp-ctp:<br><br>
 * 
<pre>
;rtrv-osrp-ctp::ALL:1;
IP 1
<
 
CHGO 07-11-02 09:19:06
M  1 COMPLD
   "1-A-1-1,OSRPCTPID=0,OSRPOOBCTPID=0,RMTOSRPCTPID=0,ALIAS=,RMTALIAS=,RMTNM=NYCY,RMTSUPTP=1-A-1-1,DELAY=1,OSRPLTP=1,RMTOSRPLTP=1,PST=IS-NR,RMTPST=IS-NR"
   "1-A-1-2,OSRPCTPID=0,OSRPOOBCTPID=0,RMTOSRPCTPID=,ALIAS=,RMTALIAS=Unknown,RMTNM=Unknown,RMTSUPTP=Unknown,DELAY=1,OSRPLTP=2,RMTOSRPLTP=0,PST=OOS-AU,RMTPST=NULL"
   "1-A-2-1,OSRPCTPID=0,OSRPOOBCTPID=0,RMTOSRPCTPID=,ALIAS=,RMTALIAS=Unknown,RMTNM=Unknown,RMTSUPTP=Unknown,DELAY=1,OSRPLTP=9,RMTOSRPLTP=0,PST=OOS-AU,RMTPST=NULL"
   "1-A-2-2,OSRPCTPID=0,OSRPOOBCTPID=0,RMTOSRPCTPID=,ALIAS=,RMTALIAS=Unknown,RMTNM=Unknown,RMTSUPTP=Unknown,DELAY=1,OSRPLTP=10,RMTOSRPLTP=0,PST=OOS-AU,RMTPST=NULL"
   "1-A-7-1,OSRPCTPID=0,OSRPOOBCTPID=0,RMTOSRPCTPID=,ALIAS=,RMTALIAS=Unknown,RMTNM=Unknown,RMTSUPTP=Unknown,DELAY=1,OSRPLTP=49,RMTOSRPLTP=0,PST=OOS-AU,RMTPST=NULL"
   "1-A-7-2,OSRPCTPID=0,OSRPOOBCTPID=0,RMTOSRPCTPID=,ALIAS=,RMTALIAS=Unknown,RMTNM=Unknown,RMTSUPTP=Unknown,DELAY=1,OSRPLTP=50,RMTOSRPLTP=0,PST=OOS-AU,RMTPST=NULL"
   "1-A-8-1,OSRPCTPID=0,OSRPOOBCTPID=0,RMTOSRPCTPID=,ALIAS=,RMTALIAS=Unknown,RMTNM=Unknown,RMTSUPTP=Unknown,DELAY=1,OSRPLTP=57,RMTOSRPLTP=0,PST=OOS-AU,RMTPST=NULL"
   "1-A-8-2,OSRPCTPID=0,OSRPOOBCTPID=0,RMTOSRPCTPID=,ALIAS=,RMTALIAS=Unknown,RMTNM=Unknown,RMTSUPTP=Unknown,DELAY=1,OSRPLTP=58,RMTOSRPLTP=0,PST=OOS-AU,RMTPST=NULL"
;
</pre>
 *
 *<br>
 *
 * @see PST
 * @see OsrpLtp
 * @see OsrpNode
 * 
 * @author ramiro
 * 
 */
public class OsrpCtp {

    private static final Logger logger = Logger.getLogger(OsrpCtp.class.getName());

    /**
     * The OSRP CAC termination point parameter specifies the AID of the
     * physical port that supports the OSRP CAC termination point to be
     * retrieved in [&lt;bay&gt;]-&lt;shelf&gt;-&lt;slot&gt;-&lt;subslot&gt; format
     */
    public final String tp;

    /**
     * The alias parameter indicates the user-defined label for the OSRP CAC
     * termination point being retrieved
     */
    public final String alias;

    /**
     * The delay parameter indicates the physical delay of signals in milliseconds for
     * the OSRP CAC termination point
     */
    public final long delay;

    /**
     * The OSRP CAC termination point identifier parameter is the ID of the local
     * OSRP CTP within the associated OSRP LTP
     */
    public final int osrpCtpId;

    /**
     * The OSRP link termination point parameter indicates the identifier of the OSRP
     * LTP in which this OSRP CAC termination point is aggregated
     */
    public final int osrpLtpId;

    /**
     * Specifies the common identifier for a given Out-of-Band OSRP CTP within an
     * OSRP LTP. This identifier is used for SNC connection
     */
    public final int osrpOOBCtpCommonId;

    /**
     * The primary state indicates the administrative state of the port.
     * 
     * @see lia.Monitor.ciena.PST
     */
    public final short pst;

    /**
     * The remote alias parameter indicates the user-specified label of the remote
     * OSRP CAC termination point.
     */
    public final String rmtAlias;

    /**
     * The remote node parameter indicates the name of the node in which the line
     * terminates
     */
    public final String remoteOsrpNodeName;

    /**
     * The remote OSRP CAC termination point identifier parameter indicates the ID of
     * the remote OSRP CTP within its OSRP LTP
     */
    public final int rmtOsrpCtpId;

    /**
     * The remote OSRP link termination point parameter is the ID of the LTP into
     * which the remote OSRP CAC termination point is aggregated. The value 0 for
     * this parameter indicates that the remote OSRP CTP is not part of a OSRP LTP
     */
    public final int rmtOsrpLtpId;

    /**
     * The remote primary state parameter is the administrative state of the remote
     * OSRP CAC termination point
     * 
     * @see PST
     */
    public final short rmtPst;

    /**
     * The remote supporting termination point indicates the physical port of the
     * termination point of the line
     */
    public final String rmtTp;

    /**
     * 
     * Creates a new instance of Osrp Call Admission Control (CAC) Terminal Point
     * 
     * @param tp - The OSRP Call Admission Control terminal point. It represents the physical port
     * of this OsrpCtp.
     * @param alias - User-defined alias for this terminal point
     * @param delay - delay in milliseconds
     * @param osrpCtpId - The id of this OsrpCtp inside the OsrpLtp 
     * @param osrpLtpId - The id of OsrpLtp
     * @param osrpOOBCtpCommonId - The OOB OSRP CTP within an OSRP LTP used for SNC connection
     * @param pst - Primary state which encapsulates the administrative state of the port
     * @param rmtAlias - User-defined alias for the remote OSRP CTP
     * @param remoteOsrpNodeName - The remote OsrpNode name in which the line terminates
     * @param rmtOsrpCtpId - The remote OsrpCtp id inside its OsrpLtp
     * @param rmtOsrpLtpId - The remote OsrpLtp id in which the remote OsrpCtp is aggregated. <B>If this paramener is 0 the remote OSRP CTP is not part of an OSRP LTP.</B> 
     * @param rmtPst - The remote PST
     * @param rmtTp - The remote physical port of the TP.
     *  
     */
    OsrpCtp(final String tp, final String alias, final long delay, final int osrpCtpId, final int osrpLtpId,
            final int osrpOOBCtpCommonId, final short pst, final String rmtAlias, final String remoteOsrpNodeName,
            final int rmtOsrpCtpId, final int rmtOsrpLtpId, final short rmtPst, final String rmtTp) {

        this.tp = tp;
        this.alias = alias;
        this.delay = delay;
        this.osrpCtpId = osrpCtpId;
        this.osrpLtpId = osrpLtpId;
        this.osrpOOBCtpCommonId = osrpOOBCtpCommonId;
        this.pst = pst;
        this.rmtAlias = rmtAlias;
        this.remoteOsrpNodeName = remoteOsrpNodeName;
        this.rmtOsrpCtpId = rmtOsrpCtpId;
        this.rmtOsrpLtpId = rmtOsrpLtpId;
        this.rmtPst = rmtPst;
        this.rmtTp = rmtTp;
    }

    public static final OsrpCtp fromOsrpTL1Response(final OsrpTL1Response osrpTL1Response) throws Exception {
        final String tp = (String) osrpTL1Response.singleParams.get(0);
        if (tp == null) {
            throw new Exception("[ OsrpCtp ] Unable to parse the OSRP CAC Termination Point from OsrpTL1Response: "
                    + osrpTL1Response);
        }

        final Map map = osrpTL1Response.paramsMap;

        final String alias = (String) map.get("ALIAS");

        final long delay = TL1Util.getLongVal("DELAY", osrpTL1Response);
        final int osrpCtpId = TL1Util.getIntVal("OSRPCTPID", osrpTL1Response);
        final int osrpLtpId = TL1Util.getIntVal("OSRPLTP", osrpTL1Response);
        final int osrpOOBCtpCommonId = TL1Util.getIntVal("OSRPOOBCTPID", osrpTL1Response);
        final short pst = PST.pstFromString((String) map.get("PST"));
        final String rmtAlias = (String) map.get("RMTALIAS");
        final String remoteOsrpNodeName = (String) map.get("RMTNM");
        final int rmtOsrpCtpId = TL1Util.getIntVal("RMTOSRPCTPID", osrpTL1Response);
        final int rmtOsrpLtpId = TL1Util.getIntVal("RMTOSRPLTP", osrpTL1Response);
        final short rmtPst = PST.pstFromString((String) map.get("RMTPST"));
        final String rmtTp = (String) map.get("RMTSUPTP");

        return new OsrpCtp(tp, alias, delay, osrpCtpId, osrpLtpId, osrpOOBCtpCommonId, pst, rmtAlias,
                remoteOsrpNodeName, rmtOsrpCtpId, rmtOsrpLtpId, rmtPst, rmtTp);
    }
}
