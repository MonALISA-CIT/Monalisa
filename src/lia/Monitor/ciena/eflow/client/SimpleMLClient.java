package lia.Monitor.ciena.eflow.client;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ClientsFarmProxy.ProxyServiceEntry;
import lia.Monitor.JiniClient.CommonJini.JiniClient;
import lia.Monitor.ciena.eflow.client.VCGClientCheckerConfig.CfgEntry;
import lia.Monitor.monitor.DataStore;
import lia.Monitor.monitor.ExtendedSiteInfoEntry;
import lia.Monitor.monitor.GenericMLEntry;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.MLControlEntry;
import lia.Monitor.monitor.MonMessageClientsProxy;
import lia.Monitor.monitor.MonaLisaEntry;
import lia.Monitor.monitor.SiteInfoEntry;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.ConnMessageMux;
import lia.Monitor.tcpClient.MLSerClient;
import lia.Monitor.tcpClient.tmClient;
import lia.util.Utils;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;

/**
 * 
 */
public class SimpleMLClient<T extends BasicServiceNode> extends JiniClient {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(SimpleMLClient.class.getName());

    final Hashtable<ServiceID, T> snodes; // key is node's SID

    final Hashtable<ServiceID, ServiceThread> sthreads; // threads for each sercive (ML farm) (Reflector)

    private final AtomicReference<ConnMessageMux> connMessageMuxPointer = new AtomicReference<ConnMessageMux>(null);

    protected final ServiceNodeFactory<T> nodeFactory;

    protected final ConcurrentHashMap<String, LocalDataFarmClient> filtersMap = new ConcurrentHashMap<String, LocalDataFarmClient>();

    protected final ConcurrentHashMap<monPredicate, LocalDataFarmClient> predicatesMap = new ConcurrentHashMap<monPredicate, LocalDataFarmClient>();

    public SimpleMLClient(ServiceNodeFactory<T> nodeFactory) {
        // super(Main.class, true, false);
        super(null, true, false);
        this.nodeFactory = nodeFactory;

        snodes = new Hashtable<ServiceID, T>();
        sthreads = new Hashtable<ServiceID, ServiceThread>();

    }

    /**
     * @param filterName
     * @param notifier
     * @return
     */
    public LocalDataFarmClient getAndSetFilterNotifier(String filterName, LocalDataFarmClient notifier) {
        return filtersMap.put(filterName, notifier);
    }

    public LocalDataFarmClient setFilterNotifierIfAbsent(String filterName, LocalDataFarmClient notifier) {
        return filtersMap.putIfAbsent(filterName, notifier);
    }

    public LocalDataFarmClient getAndSetPredicateNotifier(monPredicate predicate, LocalDataFarmClient notifier) {
        return predicatesMap.put(predicate, notifier);
    }

    public LocalDataFarmClient setPredicateNotifierIfAbsent(monPredicate predicate, LocalDataFarmClient notifier) {
        return predicatesMap.putIfAbsent(predicate, notifier);
    }

    @Override
    synchronized public boolean AddMonitorUnit(ServiceItem si) {
        synchronized (sthreads) {
            if (!sthreads.containsKey(si.serviceID) && !snodes.containsKey(si.serviceID)) {
                ServiceThread at = new ServiceThread(this, si);
                sthreads.put(si.serviceID, at);
                // logger.log(Level.INFO, "SThread created for sid="+si.serviceID);
                at.start();
                return true;
            }
        }
        return false;
    }

    static final class ServiceThread extends Thread {

        final ServiceItem si;

        final SimpleMLClient<?> client;

        public ServiceThread(SimpleMLClient<?> client, ServiceItem si) {
            this.si = si;
            this.client = client;
        }

        @Override
        public void run() {
            try {
                client.inializeMonitorUnit(si);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "While initializingMonitorUnit, got", t);
            }
            synchronized (client.sthreads) {
                client.sthreads.remove(si.serviceID);
                client.sthreads.notify();
            }
        }
    }

    public void inializeMonitorUnit(ServiceItem si) {
        try {
            if (si == null || si.service == null) {
                logger.log(Level.WARNING, "Service could not be deserialized", new Object[] {
                    si
                });
                return;
            }
            final DataStore ds = DataStore.class.cast(si.service);
            final MonaLisaEntry mle = getEntry(si, MonaLisaEntry.class);
            final SiteInfoEntry sie = getEntry(si, SiteInfoEntry.class);
            final MLControlEntry mlce = getEntry(si, MLControlEntry.class);
            final GenericMLEntry gmle = getEntry(si, GenericMLEntry.class);
            final ExtendedSiteInfoEntry esie = getEntry(si, ExtendedSiteInfoEntry.class);

            if (logger.isLoggable(Level.FINER)) {
                final StringBuilder sb = new StringBuilder();
                sb.append("\n **** Jini attributes **** \n");
                sb.append("\nMonalisaEntry: ").append(mle);
                sb.append("\nSiteInfoEntry: ").append(sie);
                sb.append("\nExtendedSiteInfoEntry: ").append(esie);
                sb.append("\nMLControlEntry: ").append(mlce);
                sb.append("\nGenericMLEntry: ").append(gmle);
                logger.log(Level.FINER, "[inializeMonitorUnit]" + sb.toString());
            }

            String ipad = null; // IP address
            String un = null; // unit name
            if (mlce != null) {
                mlce.ControlPort.intValue();
            }
            if (sie != null) {
                ipad = sie.IPAddress;
                un = sie.UnitName;
            } else {
                logger.log(Level.WARNING, "SiteInfoEntry == null for " + si + " - discarding it");
                return;
            }
            if (un == null || ipad == null) {
                logger.log(Level.WARNING, "UnitName or IPaddress null for " + si + " - discarding it");
                return;
            }
            if (gmle != null && gmle.hash != null) {
                final String ip = (String) gmle.hash.get("ipAddress");
                if (!ipad.equals(ip)) {
                    logger.log(Level.WARNING, "IP address differs in gmle from service for " + un + " serIP=" + ipad + " gmleIP=" + ip);
                }
            } else {
                logger.log(Level.WARNING, "No gmle host/ip information for " + un);
            }

            InetAddress ia = null;
            try {
                ia = InetAddress.getByName(ipad);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Unable to determine InetAddress for " + ipad + " Cause: ", t);
            }

            if (ia == null) {
                logger.log(Level.WARNING, "The InetAddress is undefined for " + si + " - discarding it");
                return;
            }

            MLSerClient tcl = new VCGSerClient(si.serviceID, un, ia, connMessageMuxPointer.get());
            tcl.addFarmClient(si.serviceID);
            tcl.setServiceEntry(si);
            addNode(si, ds, tcl, un, ipad);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error while adding node", t);
            removeNode(si.serviceID);
        }
    }

    /**
     * Wait for service threads to finish. @see lia.Monitor.JiniClient.actualizeFarms() and
     * AddProxyService for details about this.
     */
    @Override
    public void waitServiceThreads(String message) {
        synchronized (sthreads) {
            while (sthreads.size() > 0) {
                logger.log(Level.INFO, "Waiting for last [" + sthreads.size() + "] sericeThreads to finish " + message);
                try {
                    sthreads.wait();
                } catch (InterruptedException e) {
                    logger.log(Level.WARNING, "Interrupted while waiting.", e);
                }
            }
        }
    }

    synchronized public void addNode(ServiceItem si, DataStore dataStore, MLSerClient client, String unitName, String ipad) {
        T n = snodes.get(client.tClientID);
        if (n == null) {
            logger.log(Level.INFO, "Added service end-point: " + unitName + " [" + ipad + "]");
            n = nodeFactory.newServiceNodeInstace(si, dataStore, client, unitName, ipad);
            snodes.put(n.serviceID, n);
            for (Iterator<Map.Entry<String, LocalDataFarmClient>> iterator = filtersMap.entrySet().iterator(); iterator.hasNext();) {
                final Map.Entry<String, LocalDataFarmClient> entry = iterator.next();
                client.addLocalClient(entry.getValue(), entry.getKey());
            }

            for (Iterator<Map.Entry<monPredicate, LocalDataFarmClient>> iterator = predicatesMap.entrySet().iterator(); iterator.hasNext();) {
                final Map.Entry<monPredicate, LocalDataFarmClient> entry = iterator.next();
                client.addLocalClient(entry.getValue(), entry.getKey());
            }
        }
    }

    @Override
    public void removeNode(ServiceID id) {
        try {
            T n = snodes.remove(id);
            if (n != null) {
                n.client.deleteLocalClient(null);
                final ConnMessageMux connMessageMux = connMessageMuxPointer.get();
                if (connMessageMux != null)
                    connMessageMux.removeFarmClient(id);
                logger.log(Level.INFO, "Removed ServiceNode " + n.getServiceName() + " sid=" + id);
            }
        } catch (Throwable tex) {
            logger.log(Level.WARNING, "Error removing node " + id, tex);
        }
    }

    /**
     * @param farmID
     */
    @Override
    public boolean knownConfiguration(ServiceID farmID) {
        return true;
    }

    @Override
    public boolean verifyProxyConnection() {
        final ConnMessageMux connMessageMux = connMessageMuxPointer.get();
        return (connMessageMux != null && connMessageMux.verifyProxyConnection());
    }

    @Override
    public synchronized void AddProxyService(ServiceItem si) throws Exception {
        if (si == null)
            return;
        waitServiceThreads("before setting new Proxy");
        final ProxyServiceEntry pse = Utils.getEntry(si, ProxyServiceEntry.class);

        if (pse == null) {
            return;
        }

        final int portNumber = pse.proxyPort.intValue();
        final String ipAddress = pse.ipAddress;
        InetAddress inetAddress = InetAddress.getByName(ipAddress);
        logger.log(Level.INFO, "[SimpleMLClient] CONNECT TO PROXY " + inetAddress.getHostName() + ":" + portNumber);

        ConnMessageMux connMessageMux = null;

        try {
            connMessageMux = new tmClient(inetAddress, portNumber, new ConcurrentHashMap<ServiceID, MonMessageClientsProxy>(), this);
            final ConnMessageMux existing = connMessageMuxPointer.getAndSet(connMessageMux);
            if (existing != null) {
                logger.log(Level.WARNING, "[SimpleMLClient] it seems that there is already a connection with the proxy .... will close it first");
                existing.closeProxyConnection();
            }
            connMessageMux.startCommunication();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "[SimpleMLClient] exception connecting with the proxy. Starting clean-up. Cause: ", ex);
            if (connMessageMux != null) {
                logger.log(Level.INFO, "[SimpleMLClient] Clean-up NEW connection ... ");
                try {
                    connMessageMux.closeProxyConnection();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "[SimpleMLClient] exception clean-up NEW connection. Cause:", t);
                }
            }

            try {
                final ConnMessageMux existing = connMessageMuxPointer.getAndSet(null);
                if (existing != null) {
                    logger.log(Level.INFO, "[SimpleMLClient] Clean-up EXISTING connection also ... ");
                    try {
                        existing.closeProxyConnection();
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "[SimpleMLClient] exception clean-up EXISTING connection. Cause:", t);
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[SimpleMLClient] exception clean-up EXISTING connection. Cause:", t);
            }
            throw ex;
        }
    }

    @Override
    public synchronized void closeProxyConnection() {
        final ConnMessageMux connMessageMux = connMessageMuxPointer.getAndSet(null);

        if (connMessageMux != null) {
            connMessageMux.closeProxyConnection();
        }
    }

    /**
     * @param id
     * @param portMap
     */
    @Override
    public void portMapChanged(ServiceID id, ArrayList<?> portMap) {
        // TODO - I don't know, I really don't know
    }

}
