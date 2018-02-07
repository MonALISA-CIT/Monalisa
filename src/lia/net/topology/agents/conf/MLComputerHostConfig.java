/*
 * Created on Mar 24, 2010
 */
package lia.net.topology.agents.conf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.net.topology.DeviceType;
import lia.net.topology.Port.PortType;
import lia.util.Utils;

/**
 * 
 * @author ramiro
 */
public class MLComputerHostConfig extends RawConfig<HostRawPort> {

    private static final Logger logger = Logger.getLogger(MLComputerHostConfig.class.getName());

    private static final class ConfigEntry implements RawConfigInterface<HostRawPort> {
        private final String hostName;
        private final List<HostRawPort> hostPorts;
        private final ConcurrentMap<HostRawPort, OutgoingLink> outgoingLinks;

        ConfigEntry(String hostName, List<HostRawPort> hostPorts, ConcurrentMap<HostRawPort, OutgoingLink> outgoingLinks) {
            this.hostName = hostName;
            this.hostPorts = hostPorts;
            this.outgoingLinks = outgoingLinks;
        }

        @Override
        public String hostName() {
            return hostName;
        }

        @Override
        public List<HostRawPort> hostPorts() {
            return hostPorts;
        }

        @Override
        public Map<HostRawPort, OutgoingLink> outgoingLinks() {
            return outgoingLinks;
        }

        /**
         * @param notifier  
         */
        @Override
        public void addNotifier(RawConfigNotifier<HostRawPort> notifier) {
            throw new IllegalArgumentException("Not yet implemented");
        }

        /**
         * @param notifier  
         */
        @Override
        public void removeNotifier(RawConfigNotifier<HostRawPort> notifier) {
            throw new IllegalArgumentException("Not yet implemented");
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(getClass().getName()).append(" HostName: ").append(hostName());
            sb.append(" ports: ").append(hostPorts());
            sb.append(" links: ").append(outgoingLinks());
            return sb.toString();
        }

    }

    private final AtomicReference<ConfigEntry> cfgRef = new AtomicReference<ConfigEntry>(null);

    public MLComputerHostConfig(String configFile) throws IOException {
        this(new File(configFile));
    }

    public MLComputerHostConfig(File configFile) throws IOException {
        super(configFile);
        try {
            reloadConfig();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Exception reloading config", t);
        }
    }

    @Override
    public String hostName() {
        final ConfigEntry cfg = cfgRef.get();
        return (cfg == null) ? null : cfgRef.get().hostName;
    }

    @Override
    public List<HostRawPort> hostPorts() {
        final ConfigEntry cfg = cfgRef.get();
        return (cfg == null) ? null : cfgRef.get().hostPorts;
    }

    @Override
    public ConcurrentMap<HostRawPort, OutgoingLink> outgoingLinks() {
        final ConfigEntry cfg = cfgRef.get();
        return (cfg == null) ? null : cfgRef.get().outgoingLinks;
    }

    private void reloadConfig() throws IOException {
        FileReader fr = null;

        String hostName = null;
        List<HostRawPort> hostPorts = null;
        ConcurrentMap<HostRawPort, OutgoingLink> outgoingLinks = null;

        BufferedReader br = null;
        try {
            fr = new FileReader(configFile);
            br = new BufferedReader(fr);
            for (;;) {
                final String line = br.readLine();
                if (line == null) {
                    break;
                }
                final String tLine = line.trim();
                if ((hostName == null) && tLine.startsWith("ComputerName")) {
                    hostName = tLine.split("(\\s)*=(\\s)*")[1];
                    continue;
                }

                if ((hostPorts == null) && tLine.startsWith("ComputerPorts")) {
                    hostPorts = new LinkedList<HostRawPort>();
                    String swPortTknsLine = tLine.split("(\\s)*=(\\s)*")[1].trim();
                    String[] swPortTkns = swPortTknsLine.split("(\\s)*;(\\s)*");
                    for (final String swPort : swPortTkns) {
                        hostPorts.add(HostRawPort.newInstance(swPort.trim(), PortType.INPUT_OUTPUT_PORT));
                    }
                    continue;
                }

                //localPort DeviceType:DeviceName:port:portType
                if ((outgoingLinks == null) && tLine.startsWith("RemoteLinks")) {
                    outgoingLinks = new ConcurrentHashMap<HostRawPort, OutgoingLink>();
                    String swPortTknsLine = tLine.split("(\\s)*=(\\s)*")[1].trim();
                    String[] linksTkns = swPortTknsLine.split("(\\s)*;(\\s)*");
                    for (final String rawLink : linksTkns) {
                        final String rtl[] = rawLink.trim().split("(\\s)+");
                        final String sourcePort = rtl[0];
                        final String[] destinationHostPort = rtl[1].split(":");

                        final DeviceType deviceType = DeviceType.valueOf(destinationHostPort[0]);
                        final String host = destinationHostPort[1];
                        final String portName = destinationHostPort[2];
                        final PortType defaultRemotePortType = (deviceType == DeviceType.AFOX) ? PortType.INPUT_PORT
                                : PortType.INPUT_OUTPUT_PORT;
                        final PortType remotePortType = (destinationHostPort.length == 4) ? PortType
                                .valueOf(destinationHostPort[3]) : defaultRemotePortType;

                        outgoingLinks.put(HostRawPort.newInstance(sourcePort.trim(), PortType.INPUT_OUTPUT_PORT),
                                new OutgoingLink(host, deviceType, portName, remotePortType));
                    }
                    continue;
                }

            }

            if ((hostName == null) || (hostPorts == null)) {
                throw new IllegalArgumentException("Config is invalid");
            }

            final ConfigEntry newConf = new ConfigEntry(hostName, hostPorts, outgoingLinks);
            final ConfigEntry oldConf = cfgRef.getAndSet(newConf);
            newConfig(oldConf, newConf);

        } finally {
            Utils.closeIgnoringException(fr);
            Utils.closeIgnoringException(br);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName()).append(" HostName: ").append(hostName());
        sb.append(" ports: ").append(hostPorts());
        sb.append(" links: ").append(outgoingLinks());
        return sb.toString();
    }

    @Override
    public void configFileChanged() {
        try {
            reloadConfig();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception reloading config", t);
        }
    }

}
