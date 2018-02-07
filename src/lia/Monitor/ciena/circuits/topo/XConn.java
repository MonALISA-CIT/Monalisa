/*
 * $Id: XConn.java 6865 2010-10-10 10:03:16Z ramiro $
 * Created on Dec 3, 2007
 */
package lia.Monitor.ciena.circuits.topo;

import java.util.Map;

import lia.Monitor.ciena.PST;
import lia.Monitor.ciena.tl1.TL1Response;

/**
 * @author ramiro
 */
public class XConn {

    public final String name;

    public final String alias;

    public final String circuitName;

    public final String fromEndpoint;

    public final String toEndpoint;

    private short pst;

    public XConn(String name, String alias, String circuitName, String fromEndpoint, String toEndpoint, short pst) {
        this.name = name;
        this.alias = alias;
        this.circuitName = circuitName;
        this.fromEndpoint = fromEndpoint;
        this.toEndpoint = toEndpoint;
        this.pst = pst;
    }

    public static final XConn fromTL1Response(TL1Response response) {
        final Map<String, String> pMap = response.paramsMap;

        return new XConn(pMap.get("NAME"), pMap.get("ALIAS"), pMap.get("CKTID"), pMap.get("FROMENDPOINT"), pMap.get("TOENDPOINT"), PST.pstFromString(pMap.get("PST")));

    }
}
