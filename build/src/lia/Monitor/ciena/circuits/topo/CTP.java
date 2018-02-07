/*
 * $Id: CTP.java 6865 2010-10-10 10:03:16Z ramiro $
 * 
 * Created on Dec 3, 2007
 */
package lia.Monitor.ciena.circuits.topo;

import java.util.List;
import java.util.Map;

import lia.Monitor.ciena.PST;
import lia.Monitor.ciena.tl1.TL1Response;

/**
 * 
 * @author ramiro
 */
public class CTP {

    public final String aid;
    public final String name;
    public final String alias;
    public final String crsConn;
    public final String gtpName;
    private short pst;
    public final String supTP;
    public final String sncName;

    public CTP(String aid, String name, String alias, String crsConn, String gtpName, String sncName, String supTP, short pst) {
        this.aid = aid;
        this.name = name;
        this.alias = alias;
        this.crsConn = crsConn;
        this.gtpName = gtpName;
        this.pst = pst;
        this.supTP = supTP;
        this.sncName = sncName;
    }

    public static final CTP fromTL1Response(TL1Response response) {
        final List<String> sParams = response.singleParams;
        final Map<String, String> pMap = response.paramsMap;

        return new CTP(sParams.get(0), pMap.get("NAME"), pMap.get("ALIAS"), pMap.get("CRSCONN"), pMap.get("GTPNAME"), 
                pMap.get("SUPCK"), pMap.get("SUPTP"),
                PST.pstFromString(pMap.get("PST")));

    }
}
