/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package lia.Monitor.ciena.circuits.topo;

import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import lia.Monitor.ciena.circuits.topo.tl1.TL1CDCICircuitsHolder;
import lia.Monitor.ciena.tl1.TL1Response;

/**
 *
 * @author ramiro
 */
public class CDCICircuitsHolder {

    public final String swName;
    /**
     * K: sncName, V: SNC
     */
    public final Map<String, SNC> sncMap = new ConcurrentHashMap<String, SNC>();
    /**
     * K: xconnName, V: XConn
     */
    public final Map<String, XConn> xconnMap = new ConcurrentHashMap<String, XConn>();
    /**
     * K: CTP Name, V: CTP
     */
    public final Map<String, CTP> stsMap = new ConcurrentHashMap<String, CTP>();

    public CDCICircuitsHolder(String swName) {
        this.swName = swName;
    }

    public static final CDCICircuitsHolder fromTL1CircuitsHolder(TL1CDCICircuitsHolder tl1CircuitsHolder) {

        final String swName = tl1CircuitsHolder.swName;
        final CDCICircuitsHolder cdciCircuitsHolder = new CDCICircuitsHolder(swName);


        //build the CTPs
        final TreeSet<TL1Response> tl1Ctps = tl1CircuitsHolder.tl1CTPs;

        for (final TL1Response tl1Response: tl1Ctps) {
            final CTP ctp = CTP.fromTL1Response(tl1Response);
            cdciCircuitsHolder.stsMap.put(ctp.name, ctp);
        }

        //build the cross connects map
        final TreeSet<TL1Response> tl1XConns = tl1CircuitsHolder.tl1XConns;
        for (final TL1Response tl1Response: tl1XConns) {
            final XConn xconn = XConn.fromTL1Response(tl1Response);
            cdciCircuitsHolder.xconnMap.put(xconn.name, xconn);
        }

        //build the SNCs
        final TreeSet<TL1Response> tl1SNCs = tl1CircuitsHolder.tl1SNCs;
        for (final TL1Response tl1Response: tl1SNCs) {
            final SNC snc = SNC.fromTL1Response(tl1Response, "N/A");
            cdciCircuitsHolder.sncMap.put(snc.sncName, snc);
        }
        
        return cdciCircuitsHolder;
    }
}
