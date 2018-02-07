/*
 * $Id: SNC.java 6865 2010-10-10 10:03:16Z ramiro $
 * Created on Dec 3, 2007
 */
package lia.Monitor.ciena.circuits.topo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import lia.Monitor.ciena.PST;
import lia.Monitor.ciena.tl1.TL1Response;

/**
 * @author ramiro
 */
public class SNC {

    public final String sncName;

    public final String alias;

    public final String remoteNode;

    public final AtomicInteger rate;

    private short pst;

    private final AtomicReference<String> routeReference;

    SNC(String sncName, String alias, String remoteNode, int rate, final String route, short pst) {
        this.sncName = sncName;
        this.alias = alias;
        this.remoteNode = remoteNode;
        this.rate = new AtomicInteger(rate);
        this.pst = pst;
        this.routeReference = new AtomicReference<String>(route);
    }

    public static final SNC fromTL1Response(TL1Response response, final String route) {
        final List<String> sParams = response.singleParams;
        final Map<String, String> pMap = response.paramsMap;

        final String rateS = pMap.get("RATE");
        int rate = 0;
        if (rateS != null) {
            try {
                rate = Integer.parseInt(rateS);
            } catch (Throwable t) {
                rate = 0;
            }
        }

        return new SNC(sParams.get(0), pMap.get("ALIAS"), pMap.get("RMNODE"), rate, route, PST.pstFromString(pMap.get("PST")));

    }

    void setRoute(String route) {
        this.routeReference.set(route);
    }

    public String getRoute() {
        return routeReference.get();
    }
}
