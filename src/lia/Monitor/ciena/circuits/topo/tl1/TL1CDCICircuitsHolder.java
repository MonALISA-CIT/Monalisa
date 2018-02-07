/*
 * $Id: TL1CDCICircuitsHolder.java 6865 2010-10-10 10:03:16Z ramiro $
 * 
 */
package lia.Monitor.ciena.circuits.topo.tl1;

import java.io.Serializable;
import java.util.Arrays;
import java.util.TreeSet;

import lia.Monitor.ciena.tl1.TL1Response;

/**
 *
 * @author ramiro
 */
public class TL1CDCICircuitsHolder implements Serializable {

    /**
     * @since it appeared
     */
    private static final long serialVersionUID = -7708343333645502032L;
    
    public final String swName;
    public final TreeSet<TL1Response> tl1CTPs;
    public final TreeSet<TL1Response> tl1SNCs;
    public final TreeSet<TL1Response> tl1SNCsRoutes;
    public final TreeSet<TL1Response> tl1GTPs;
    public final TreeSet<TL1Response> tl1VCGs;
    public final TreeSet<TL1Response> tl1XConns;

    public TL1CDCICircuitsHolder(String swName, TL1Response[] tl1CTPs, TL1Response[] tl1SNCs, TL1Response[] tl1SNCsRoutes, TL1Response[] tl1GTPs, TL1Response[] tl1VCGs, TL1Response[] tl1XConns) {
        this(swName,
        new TreeSet<TL1Response>(Arrays.asList(tl1CTPs)),
        new TreeSet<TL1Response>(Arrays.asList(tl1SNCs)),
        new TreeSet<TL1Response>(Arrays.asList(tl1SNCsRoutes)),
        new TreeSet<TL1Response>(Arrays.asList(tl1GTPs)),
        new TreeSet<TL1Response>(Arrays.asList(tl1VCGs)),
        new TreeSet<TL1Response>(Arrays.asList(tl1XConns)));
    }

    public TL1CDCICircuitsHolder(String swName, TreeSet<TL1Response> tl1CTPs, TreeSet<TL1Response> tl1SNCs, TreeSet<TL1Response> tl1SNCsRoutes, TreeSet<TL1Response> tl1GTPs, TreeSet<TL1Response> tl1VCGs, TreeSet<TL1Response> tl1XConns) {
        this.swName = swName;
        this.tl1CTPs = tl1CTPs;
        this.tl1SNCs = tl1SNCs;
        this.tl1SNCsRoutes = tl1SNCsRoutes;
        this.tl1GTPs = tl1GTPs;
        this.tl1VCGs = tl1VCGs;
        this.tl1XConns = tl1XConns;
    }

    public boolean equals(Object o) {
        if (o instanceof TL1CDCICircuitsHolder) {
            final TL1CDCICircuitsHolder tl1Topo = (TL1CDCICircuitsHolder) o;
            return (this.swName.equals(tl1Topo.swName) &&
                    this.tl1CTPs.equals(tl1Topo.tl1CTPs) &&
                    this.tl1SNCs.equals(tl1Topo.tl1SNCs) &&
                    this.tl1SNCsRoutes.equals(tl1Topo.tl1SNCsRoutes) &&
                    this.tl1GTPs.equals(tl1Topo.tl1GTPs) &&
                    this.tl1VCGs.equals(tl1Topo.tl1VCGs) &&
                    this.tl1XConns.equals(tl1Topo.tl1XConns));
        }

        throw new ClassCastException("TL1CDCICircuitsHolder equals unk class: " + o.getClass());
    }

    public int hashCode() {
        return this.swName.hashCode();
    }
}
