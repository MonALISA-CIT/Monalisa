/*
 * Created on Mar 24, 2010
 */
package lia.net.topology.agents;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniSerFarmMon.GMLEPublisher;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Gresult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.net.topology.DeviceType;
import lia.net.topology.GenericEntity;
import lia.net.topology.MLLinksMsg;
import lia.net.topology.TopoMsg;
import lia.net.topology.agents.conf.HostRawPort;
import lia.net.topology.agents.conf.MLComputerHostConfig;
import lia.net.topology.agents.conf.OutgoingLink;
import lia.net.topology.agents.conf.RawConfigInterface;
import lia.net.topology.host.ComputerHost;
import lia.net.topology.host.ComputerPort;
import lia.util.Utils;
import lia.util.ntp.NTPDate;

/**
 * @author ramiro
 */
public class ComputerHostAgent extends TopoAgent<MLComputerHostConfig, HostRawPort> {

    /**
     * 
     */
    private static final long serialVersionUID = -9066933336159877461L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(ComputerHostAgent.class.getName());

    ComputerHost host;

    AtomicBoolean shouldReloadConfig = new AtomicBoolean(false);

    private final class ComputerHostStateFetcher implements Runnable {

        @Override
        public void run() {
            try {
                publishAttrs();
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ AFOXStateFetcher ] get exception ", t);
            }
        }
    }

    private final void publishAttrs() {
        try {
            HashMap<String, String> hm = new HashMap<String, String>();

            hm.put("TOPO_CONFIG_CLUSTER", TOPO_CONFIG_CLUSTER);

            GMLEPublisher.getInstance().publishNow(hm);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got exception publishing attrs", t);
        }

    }

    private static final class ResultMonitorTask implements Runnable {

        @Override
        public void run() {
            // not implemented yet
        }
    }

    private final class ConfigPublisherTask implements Runnable {

        @Override
        public void run() {
            try {
                if (shouldReloadConfig.compareAndSet(true, false)) {
                    updateState();
                }
                deliverResults2ML(expressResults());
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Unable to deliver results", t);
            }
        }
    }

    private MLLinksMsg getLinks() {
        Map<HostRawPort, OutgoingLink> confMap = config.outgoingLinks();
        if (confMap == null) {
            final Map<UUID, OutgoingLink> linksMap = Collections.emptyMap();
            return new MLLinksMsg(host.id(), DeviceType.HOST, linksMap);
        }

        Map<UUID, OutgoingLink> pMap = new HashMap<UUID, OutgoingLink>(confMap.size());
        ComputerPort[] allPorts = host.getPorts();
        for (final Map.Entry<HostRawPort, OutgoingLink> entry : confMap.entrySet()) {
            final HostRawPort rawPort = entry.getKey();
            for (final ComputerPort cPort : allPorts) {
                if (cPort.name().equals(rawPort.portName)) {
                    pMap.put(cPort.id(), entry.getValue());
                    break;
                }
            }
        }

        return new MLLinksMsg(host.id(), DeviceType.HOST, pMap);
    }

    private final Object expressResults() {
        if (host != null) {
            final Vector<Object> retV = new Vector<Object>();
            eResult er = new eResult();
            er.time = NTPDate.currentTimeMillis();
            er.ClusterName = TOPO_CONFIG_CLUSTER;
            er.NodeName = "localhost";
            er.FarmName = getFarmName();
            try {
                byte[] buff = Utils.writeObject(new TopoMsg(agentID, serviceID, TopoMsg.Type.HOST_CONFIG, host));
                er.addSet("TOPO_CONFIG", buff);
                logger.log(Level.INFO, "Sending eResult  [ " + buff.length + " ]: \n" + er.toString() + "\n\n");
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Cannot serialize OS state", t);
            }

            try {
                byte[] buff = Utils.writeObject(new TopoMsg(agentID, serviceID, TopoMsg.Type.ML_LINKS, getLinks()));
                er.addSet("LINKS_CONFIG", buff);
                logger.log(Level.INFO, "Sending eResult [ " + buff.length + " ]: \n" + er.toString() + "\n\n");
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Cannot serialize OS state", t);
            }

            retV.add(er);
            return retV;
        }
        logger.log(Level.INFO, " [ ] OS still null.");
        return null;
    }

    public ComputerHostAgent(String agentName, String agentGroup, String farmID) {
        super(agentName, agentGroup, farmID, getLocalConfig());
        updateState();
    }

    private static final MLComputerHostConfig getLocalConfig() {
        MLComputerHostConfig config = null;
        try {
            config = new MLComputerHostConfig(AppConfig.getProperty("HOSTAGENT_CONFIG_FILE"));
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Unable to load ComputerHostAgent agent. Cause:", t);
            throw new InstantiationError("Unable to load ComputerHostAgent agent");
        }

        return config;
    }

    private void invalidateHost() {
        GenericEntity.clearIDFromCache(host.id());
        for (ComputerPort cp : this.host.getPorts()) {
            if (cp.outgoingLink() != null) {
                GenericEntity.clearIDFromCache(cp.outgoingLink().id());
            }
            GenericEntity.clearIDFromCache(cp.id());
        }
    }

    private void updateState() {
        try {
            if (host != null) {
                invalidateHost();
                host = null;
            }
            host = new ComputerHost(config.hostName());
            for (HostRawPort hrp : config.hostPorts()) {
                host.addPort(new ComputerPort(hrp.portName, host, hrp.portType));
            }
            logger.log(Level.INFO, " ComputerAgent reloaded config");
        } catch (Throwable t) {
            logger.log(Level.WARNING, " got exception update state", t);
        }

    }

    /**
     * @param r  
     */
    @Override
    public void addNewResult(Object r) {
        // TODO Auto-generated method stub

    }

    private void deliverResults2ML(Object o) {

        if (STANDALONE) {
            return;
        }

        if (o != null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ AFOXAgent ] delivering results to ML! " + o);
            }
            Vector<Object> notifResults = new Vector<Object>();
            Vector<Object> storeResults = new Vector<Object>();

            if (o instanceof Vector) {
                @SuppressWarnings("unchecked")
                Vector<Object> allResults = (Vector<Object>) o;

                if (allResults.size() > 0) {
                    for (int i = 0; i < allResults.size(); i++) {
                        Object r = allResults.elementAt(i);
                        if (r != null) {
                            if (r instanceof Gresult) {
                                notifResults.add(r);
                            } else {
                                storeResults.add(r);
                            }
                        }
                    }
                }
            } else if (o instanceof Result[]) {// notify an Array of
                // ResultS...but not a Vector
                Result[] rez = (Result[]) o;
                for (Result element : rez) {
                    notifResults.add(element);
                }
            } else {// notify anything else
                notifResults.add(o);
            }
            if (notifResults.size() > 0) {
                informClients(notifResults);
            }
            if (storeResults.size() > 0) {
                notifyCache(storeResults);
            }
        }
    }

    private void notifyCache(Vector<Object> storeResults) {
        cache.notifyInternalResults(storeResults);
    }

    @Override
    public void doWork() {
        logger.log(Level.INFO, " ComputerHostAgent STARTED sus pe gard !");
        // TODO Auto-generated method stub
        monitorExec.scheduleWithFixedDelay(new ResultMonitorTask(), 10, 10, TimeUnit.SECONDS);
        monitorExec.scheduleWithFixedDelay(new ConfigPublisherTask(), 15, 10, TimeUnit.SECONDS);
        monitorExec.scheduleWithFixedDelay(new ComputerHostStateFetcher(), 15, 40, TimeUnit.SECONDS);
        try {
            for (;;) {
                try {
                    Thread.sleep(10 * 1000);
                } catch (Throwable t1) {
                    logger.log(Level.WARNING, " Exception in loop ", t1);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Exception OUTSIDE loop. AGENT LOOP WILL FINISH NOW!! ", t);
        }
    }

    /**
     * @param msg  
     */
    @Override
    public void processMsg(Object msg) {
        // TODO Auto-generated method stub

    }

    @Override
    public void notifyConfig(RawConfigInterface<HostRawPort> oldConfig, RawConfigInterface<HostRawPort> newConfig) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n old config \n\n ").append(oldConfig).append("\n\n new config \n\n").append(newConfig)
                .append("\n\n");
        shouldReloadConfig.compareAndSet(false, true);
        logger.log(Level.INFO, sb.toString());
    }

    public static void main(String[] args) {
        System.setProperty("HOSTAGENT_CONFIG_FILE", "/home/ramiro/MLHOST.config");

        new ComputerHostAgent("Test", "Test", UUID.randomUUID().toString()).doWork();
    }

}
