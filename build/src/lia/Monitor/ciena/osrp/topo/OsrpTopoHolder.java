/*
 * $Id: OsrpTopoHolder.java 7419 2013-10-16 12:56:15Z ramiro $
 * 
 * Created on Nov 3, 2007
 * 
 */
package lia.Monitor.ciena.osrp.topo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ciena.osrp.tl1.OsrpTL1Topo;
import lia.util.threads.MonALISAExecutors;

/**
 * 
 * 
 * @author ramiro
 * 
 */
public class OsrpTopoHolder {

    private static final Logger logger = Logger.getLogger(OsrpTopoHolder.class.getName());

    private static final ConcurrentHashMap topoMap = new ConcurrentHashMap();

    private final static long EXPIRE_DELAY = 150 * 1000;

    private static final class CleanupTask implements Runnable {
        @Override
        public void run() {
            try {
                for (Iterator it = topoMap.entrySet().iterator(); it.hasNext();) {
                    Map.Entry entry = (Map.Entry) it.next();
                    TopoEntry topoEntry = (TopoEntry) entry.getValue();
                    final long now = System.currentTimeMillis();
                    if ((topoEntry.lastUpdate.get() + EXPIRE_DELAY) < now) {
                        logger.log(Level.INFO,
                                " [ OsrpTopoHolder ] [ CleanupTask ] removed OsrpTopoID: " + entry.getKey());
                        it.remove();
                    }
                }
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINER, " [ OsrpTopoHolder ] [ CleanupTask ] got exception ", t);
                }
            }
        }
    }

    private final static class TopoEntry {

        final OsrpTL1Topo osrpTl1Topo;

        final OsrpTopology osrpTopology;

        final AtomicLong lastUpdate = new AtomicLong(0);

        TopoEntry(final OsrpTL1Topo osrpTl1Topo, final OsrpTopology osrpTopology) {
            this.osrpTl1Topo = osrpTl1Topo;
            this.osrpTopology = osrpTopology;
            lastUpdate.set(System.currentTimeMillis());
        }
    }

    static {
        MonALISAExecutors.getMLHelperExecutor().scheduleAtFixedRate(new CleanupTask(), 20, 5, TimeUnit.SECONDS);
    }

    public static final void notifyTL1Responses(final OsrpTL1Topo[] osrpTl1Topos) {

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST,
                    "\n\n[ OsrpTopoHolder ] [ notifyTL1Responses ] OSRP TL1 Topo(s):\n" + Arrays.toString(osrpTl1Topos)
                            + "\n\n");
        }

        for (OsrpTL1Topo osrpTl1Topo2 : osrpTl1Topos) {

            final OsrpTL1Topo osrpTl1Topo = osrpTl1Topo2;
            final TopoEntry entry = (TopoEntry) topoMap.get(osrpTl1Topo.osrpNodeId);

            // check if already cached or if the same topology as in previous iterations
            if ((entry == null) || (entry.osrpTl1Topo == null) || !osrpTl1Topo.equals(entry.osrpTl1Topo)) {
                try {
                    topoMap.put(osrpTl1Topo.osrpNodeId,
                            new TopoEntry(osrpTl1Topo, OsrpTopology.fromOsrpTL1Topo(osrpTl1Topo)));
                    if (logger.isLoggable(Level.FINE)) {
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, "[ OsrpTopoHolder ] [ notifyTL1Responses ] New TL1 topo: "
                                    + osrpTl1Topo);
                        } else {
                            logger.log(Level.FINE, "[ OsrpTopoHolder ] [ notifyTL1Responses ] Added topo for nodeID: "
                                    + osrpTl1Topo.osrpNodeId);
                        }
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING,
                            " [ OsrpTopoHolder ] [ notifyTL1Responses ] got exception parsing TL1 topo " + osrpTl1Topo,
                            t);
                }
            } else {
                entry.lastUpdate.set(System.currentTimeMillis());
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "[ OsrpTopoHolder ] [ notifyTL1Responses ] OSRP TL1 Topo for nodeID: "
                            + osrpTl1Topo.osrpNodeId + " already in the local cache");
                }
            }
        }
    }

    public static final long getlastUpdate(final String osrpNodeID) {
        final TopoEntry entry = (TopoEntry) topoMap.get(osrpNodeID);
        return entry.lastUpdate.get();
    }

    public static final OsrpTopology getOsrptopology(final String osrpNodeID) {
        final TopoEntry entry = (TopoEntry) topoMap.get(osrpNodeID);

        if (entry != null) {
            return entry.osrpTopology;
        }

        return null;
    }

    public static final Set getAllOsrpNodeIDs() {
        return topoMap.keySet();
    }

    public static final OsrpTL1Topo[] getAllOsrpTL1Topo() {
        ArrayList ret = new ArrayList();
        for (Iterator it = topoMap.values().iterator(); it.hasNext();) {
            final TopoEntry entry = (TopoEntry) it.next();
            ret.add(entry.osrpTl1Topo);
        }

        return (OsrpTL1Topo[]) ret.toArray(new OsrpTL1Topo[ret.size()]);
    }
}
