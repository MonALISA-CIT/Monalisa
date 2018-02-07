package lia.net.topology.agents.conf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
public class MLAFOXConfig extends RawConfig<AFOXRawPort> {
    private static final Logger logger = Logger.getLogger(MLAFOXConfig.class.getName());
    private static final ConcurrentMap<AFOXRawPort, OutgoingLink> EMPTY_LINKS = new ConcurrentHashMap<AFOXRawPort, OutgoingLink>();
    private static final ConcurrentMap<AFOXRawPort, AFOXRawPort> EMPTY_CROSS = new ConcurrentHashMap<AFOXRawPort, AFOXRawPort>();

    private static final class ConfigEntry implements RawConfigInterface<AFOXRawPort> {
        private final String hostName;
        private final List<AFOXRawPort> hostPorts;
        private final ConcurrentMap<AFOXRawPort, OutgoingLink> outgoingLinks;
        private final ConcurrentMap<AFOXRawPort, AFOXRawPort> crossConnMap;

        ConfigEntry(String hostName, List<AFOXRawPort> hostPorts,
                ConcurrentMap<AFOXRawPort, OutgoingLink> outgoingLinks,
                ConcurrentMap<AFOXRawPort, AFOXRawPort> crossConnMap) {
            this.hostName = hostName;
            this.hostPorts = hostPorts;
            this.outgoingLinks = (outgoingLinks == null) ? EMPTY_LINKS : outgoingLinks;
            this.crossConnMap = (crossConnMap == null) ? EMPTY_CROSS : crossConnMap;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }
            if (o instanceof ConfigEntry) {
                final ConfigEntry other = (ConfigEntry) o;

                return this.hostName().equals(other.hostName()) && this.hostPorts().equals(other.hostPorts())
                        && this.outgoingLinks().equals(other.outgoingLinks())
                        && this.crossConnMap().equals(other.crossConnMap());
            }
            return false;
        }

        @Override
        public String hostName() {
            return hostName;
        }

        @Override
        public List<AFOXRawPort> hostPorts() {
            return hostPorts;
        }

        @Override
        public ConcurrentMap<AFOXRawPort, OutgoingLink> outgoingLinks() {
            return outgoingLinks;
        }

        public ConcurrentMap<AFOXRawPort, AFOXRawPort> crossConnMap() {
            return crossConnMap;
        }

        /**
         * @param notifier  
         */
        @Override
        public void addNotifier(RawConfigNotifier<AFOXRawPort> notifier) {
            throw new IllegalArgumentException("Not yet implemented");
        }

        /**
         * @param notifier  
         */
        @Override
        public void removeNotifier(RawConfigNotifier<AFOXRawPort> notifier) {
            throw new IllegalArgumentException("Not yet implemented");
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(16384);

            sb.append("SwitchName = ").append(this.hostName()).append("\n");
            final List<AFOXRawPort> afoxPortsList = this.hostPorts();
            final int afoxPortsLen = afoxPortsList.size();

            if (afoxPortsLen > 0) {
                final Map<Integer, Set<Integer>> addedPorts = new HashMap<Integer, Set<Integer>>();
                sb.append("SwitchPorts = ");
                for (final AFOXRawPort afoxPort : afoxPortsList) {
                    final int row = afoxPort.portRow;
                    final int col = afoxPort.portColumn;
                    final Set<Integer> addedCols = addedPorts.get(Integer.valueOf(row));
                    if ((addedCols == null) || !addedCols.contains(Integer.valueOf(col))) {
                        if (addedCols == null) {
                            Set<Integer> colsSet = new HashSet<Integer>();
                            colsSet.add(Integer.valueOf(col));
                            addedPorts.put(Integer.valueOf(row), colsSet);
                        } else {
                            addedCols.add(Integer.valueOf(col));
                        }
                        sb.append("(").append(row).append(",").append(col).append("); ");
                    }
                }
                sb.append("\n");
            }

            final Map<AFOXRawPort, OutgoingLink> outgoingLinks = this.outgoingLinks();
            final int outLinksLen = outgoingLinks.size();
            if (outLinksLen > 0) {
                sb.append("RemoteLinks = ");
                for (final Map.Entry<AFOXRawPort, OutgoingLink> entry : outgoingLinks.entrySet()) {
                    final AFOXRawPort afoxPort = entry.getKey();
                    final OutgoingLink link = entry.getValue();
                    final int row = afoxPort.portRow;
                    final int col = afoxPort.portColumn;

                    sb.append("(").append(row).append(",").append(col).append(") ");
                    sb.append(link.remoteDeviceType).append(":");
                    sb.append(link.remoteDeviceName).append(":");
                    sb.append(link.remotePortName).append(":").append(link.remotePortType).append("; ");
                }
                sb.append("\n");
            }

            final Map<AFOXRawPort, AFOXRawPort> crossConnMap = this.crossConnMap();
            final int crossConnLen = crossConnMap.size();

            if (crossConnLen > 0) {
                sb.append("CrossConns = ");
                for (final Map.Entry<AFOXRawPort, AFOXRawPort> entry : crossConnMap.entrySet()) {
                    final AFOXRawPort sPort = entry.getKey();
                    final AFOXRawPort dPort = entry.getValue();

                    final int srow = sPort.portRow;
                    final int scol = sPort.portColumn;

                    final int drow = dPort.portRow;
                    final int dcol = dPort.portColumn;

                    sb.append("(").append(srow).append(",").append(scol).append(") ");
                    sb.append("(").append(drow).append(",").append(dcol).append("); ");
                }
                sb.append("\n");
            }

            return sb.toString();
        }

        @Override
        public int hashCode() {
            return this.hostName().hashCode();
        }
    }

    private final AtomicReference<ConfigEntry> cfgRef = new AtomicReference<ConfigEntry>(null);

    public MLAFOXConfig(String configFile) throws IOException {
        this(new File(configFile));
    }

    public MLAFOXConfig(File configFile) throws IOException {
        super(configFile);
        reloadConfig();
    }

    private void reloadConfig() throws IOException {
        final ConfigEntry oldConf = cfgRef.get();
        String hostName = null;
        List<AFOXRawPort> hostPorts = null;
        ConcurrentMap<AFOXRawPort, OutgoingLink> outgoingLinks = null;
        ConcurrentMap<AFOXRawPort, AFOXRawPort> crossConnMap = new ConcurrentHashMap<AFOXRawPort, AFOXRawPort>();

        FileReader fr = null;
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
                if ((hostName == null) && tLine.startsWith("SwitchName")) {
                    hostName = tLine.split("(\\s)*=(\\s)*")[1];
                    continue;
                }

                if ((hostPorts == null) && tLine.startsWith("SwitchPorts")) {
                    hostPorts = new LinkedList<AFOXRawPort>();
                    String swPortTknsLine = tLine.split("(\\s)*=(\\s)*")[1].trim();
                    String[] swPortTkns = swPortTknsLine.split("(\\s)*;(\\s)*");
                    for (final String swPort : swPortTkns) {
                        final String ttp = swPort.trim();
                        if (ttp.indexOf('(') < 0) {
                            continue;
                        }
                        final String tp = ttp.substring(1, ttp.length() - 1);
                        final String[] tpTKNS = tp.split("(\\s)*,(\\s)*");

                        final String rowStr = tpTKNS[0];
                        final String colStr = tpTKNS[1];
                        if (rowStr.contains("-") && colStr.contains("-")) {
                            String[] mmaxRow = rowStr.split("(\\s)*-(\\s)*");
                            int rMin = Integer.parseInt(mmaxRow[0]);
                            int rMax = Integer.parseInt(mmaxRow[1]);
                            String[] mmaxCol = colStr.split("(\\s)*-(\\s)*");
                            int cMin = Integer.parseInt(mmaxCol[0]);
                            int cMax = Integer.parseInt(mmaxCol[1]);

                            for (int i = rMin; i <= rMax; i++) {
                                for (int j = cMin; j <= cMax; j++) {
                                    hostPorts.add(AFOXRawPort.newInstance(i, j, PortType.INPUT_PORT));
                                    hostPorts.add(AFOXRawPort.newInstance(i, j, PortType.OUTPUT_PORT));
                                }
                            }
                        } else {
                            final int pRow = Integer.parseInt(tpTKNS[0]);
                            final int pCol = Integer.parseInt(tpTKNS[1]);
                            hostPorts.add(AFOXRawPort.newInstance(pRow, pCol, PortType.INPUT_PORT));
                            hostPorts.add(AFOXRawPort.newInstance(pRow, pCol, PortType.OUTPUT_PORT));
                        }
                    }
                    //                  for(final String swPort: swPortTkns) {
                    //                      final String ttp = swPort.trim();
                    //                      final AFOXRawPort ap = AFOXRawPort.valueOf(ttp);
                    //                      hostPorts.add(ap);
                    //                      logger.log(Level.INFO, "Added port " + ap + " for token: " + ttp);
                    //                  }

                    continue;
                }

                //(raw,column):PortType     DeviceType:DeviceName:port:portType
                if ((outgoingLinks == null) && tLine.startsWith("RemoteLinks")) {
                    outgoingLinks = new ConcurrentHashMap<AFOXRawPort, OutgoingLink>();
                    String swPortTknsLine = tLine.split("(\\s)*=(\\s)*")[1].trim();
                    String[] linksTkns = swPortTknsLine.split("(\\s)*;(\\s)*");
                    for (final String rawLink : linksTkns) {
                        if (rawLink.indexOf('(') < 0) {
                            continue;
                        }

                        final String rtl[] = rawLink.trim().split("(\\s)+");
                        AFOXRawPort port = AFOXRawPort.valueOf(rtl[0].trim());

                        final String[] destinationHostPort = rtl[1].split(":");

                        final DeviceType deviceType = DeviceType.valueOf(destinationHostPort[0]);
                        final String host = destinationHostPort[1];
                        final String portName = destinationHostPort[2];
                        final PortType defaultRemotePortType = (deviceType == DeviceType.AFOX) ? PortType.INPUT_PORT
                                : PortType.INPUT_OUTPUT_PORT;
                        final PortType remotePortType = (destinationHostPort.length == 4) ? PortType
                                .valueOf(destinationHostPort[3]) : defaultRemotePortType;

                        outgoingLinks.put(port, new OutgoingLink(host, deviceType, portName, remotePortType));
                    }
                    continue;
                }

                //(raw,column) (raw,column); (raw,column) (raw,column);
                if (tLine.startsWith("CrossConnects") || tLine.startsWith("CrossConns")) {
                    String swPortTknsLine = tLine.split("(\\s)*=(\\s)*")[1].trim();
                    String[] linksTkns = swPortTknsLine.split("(\\s)*;(\\s)*");
                    for (final String rawLink : linksTkns) {
                        if (rawLink.indexOf('(') < 0) {
                            continue;
                        }
                        final String rtl[] = rawLink.trim().split("(\\s)+");
                        final AFOXRawPort sPort = AFOXRawPort.valueOf(rtl[0].trim());
                        final AFOXRawPort dPort = AFOXRawPort.valueOf(rtl[1].trim());
                        crossConnMap.put(sPort, dPort);
                    }

                    continue;
                }

            }
            final ConfigEntry newConf = new ConfigEntry(hostName, hostPorts, outgoingLinks, crossConnMap);
            if (cfgRef.compareAndSet(oldConf, newConf)) {
                newConfig(oldConf, newConf);
            }
        } finally {
            Utils.closeIgnoringException(fr);
            Utils.closeIgnoringException(br);
        }
    }

    @Override
    public String hostName() {
        final ConfigEntry cfg = cfgRef.get();
        return (cfg == null) ? null : cfgRef.get().hostName;
    }

    @Override
    public List<AFOXRawPort> hostPorts() {
        final ConfigEntry cfg = cfgRef.get();
        return (cfg == null) ? null : cfgRef.get().hostPorts;
    }

    @Override
    public ConcurrentMap<AFOXRawPort, OutgoingLink> outgoingLinks() {
        final ConfigEntry cfg = cfgRef.get();
        return (cfg == null) ? null : cfgRef.get().outgoingLinks;
    }

    public ConcurrentMap<AFOXRawPort, AFOXRawPort> crossConnMap() {
        final ConfigEntry cfg = cfgRef.get();
        if (cfg == null) {
            return EMPTY_CROSS;
        }
        return cfg.crossConnMap();
    }

    @Override
    public String toString() {
        return cfgRef.get().toString();
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
