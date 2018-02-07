/*
 * Created on Mar 23, 2010
 */
package lia.net.topology.agents;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.net.topology.Link;
import lia.net.topology.Port;
import lia.net.topology.Port.PortType;
import lia.net.topology.TopologyException;
import lia.net.topology.agents.conf.AFOXRawPort;
import lia.net.topology.agents.conf.MLAFOXConfig;
import lia.net.topology.agents.conf.OutgoingLink;
import lia.net.topology.opticalswitch.AFOXOSPort;
import lia.net.topology.opticalswitch.OpticalSwitch;
import lia.net.topology.opticalswitch.OpticalSwitchType;

import com.telescent.afox.global.SM_InOrOut_CurAndPending;
import com.telescent.afox.msg.AFOXFullUpdateReturnMsg;
import com.telescent.afox.msg.AFOXGetInputRFIDRetMsg;

/**
 * 
 * @author ramiro
 */
public class MLTranslation {

    private static final Logger logger = Logger.getLogger(MLTranslation.class.getName());

    public static OpticalSwitch fromConfigAndMsg(AFOXFullUpdateReturnMsg msg,
            Map<AFOXRawPort, AFOXGetInputRFIDRetMsg> rfidMap, MLAFOXConfig config) throws TopologyException {
        OpticalSwitch os = new OpticalSwitch(OpticalSwitchType.AFOX, config.hostName());
        final List<AFOXRawPort> configPorts = config.hostPorts();
        for (final AFOXRawPort pp : configPorts) {
            try {
                if (pp.portType == PortType.INPUT_PORT) {
                    SM_InOrOut_CurAndPending smIN = msg.SMCurrentIns[pp.portRow - 1][pp.portColumn - 1];
                    AFOXOSPort.Builder<Port<?, ?>> bIN = new AFOXOSPort.Builder<Port<?, ?>>(smIN.AFOXName,
                            PortType.INPUT_PORT, os, null, null);
                    bIN.row(pp.portRow).column(pp.portColumn).customerName(smIN.CustomerName).afoxRawPort(pp)
                            .pendingExists(smIN.PendingExists);
                    final AFOXGetInputRFIDRetMsg rfidMsg = rfidMap.get(pp);
                    if ((rfidMsg != null) && (rfidMsg.SerialNumber != null)) {
                        bIN.serialNumber(rfidMsg.SerialNumber);
                    }
                    os.addPort(bIN.build());
                }
                if (pp.portType == PortType.OUTPUT_PORT) {
                    SM_InOrOut_CurAndPending smOUT = msg.SMCurrentOuts[pp.portRow - 1][pp.portColumn - 1];
                    AFOXOSPort.Builder<Port<?, ?>> bOUT = new AFOXOSPort.Builder<Port<?, ?>>(smOUT.AFOXName,
                            PortType.OUTPUT_PORT, os, null, null);
                    bOUT.row(pp.portRow).column(pp.portColumn).customerName(smOUT.CustomerName).afoxRawPort(pp)
                            .pendingExists(smOUT.PendingExists);
                    final AFOXGetInputRFIDRetMsg rfidMsg = rfidMap.get(pp);
                    if ((rfidMsg != null) && (rfidMsg.SerialNumber != null)) {
                        bOUT.serialNumber(rfidMsg.SerialNumber);
                    }
                    os.addPort(bOUT.build());
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Exception in fromConfigAndMsg for port: " + pp, t);
            }
        }

        return os;
    }

    public static String afoxConfigToString(MLAFOXConfig mlAFOXConfig) {
        if (mlAFOXConfig == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(16384);

        sb.append("SwitchName = ").append(mlAFOXConfig.hostName()).append("\n");
        final List<AFOXRawPort> afoxPortsList = mlAFOXConfig.hostPorts();

        if (afoxPortsList.size() > 0) {
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
                    sb.append("(").append(row).append(",").append(col).append(") ; ");
                }
            }
            sb.append("\n");
        }

        final Map<AFOXRawPort, OutgoingLink> outgoingLinks = mlAFOXConfig.outgoingLinks();
        if (outgoingLinks.size() > 0) {
            sb.append("RemoteLinks = ");
            for (final Map.Entry<AFOXRawPort, OutgoingLink> entry : outgoingLinks.entrySet()) {
                final AFOXRawPort afoxPort = entry.getKey();
                final OutgoingLink link = entry.getValue();
                final int row = afoxPort.portRow;
                final int col = afoxPort.portColumn;

                sb.append("(").append(row).append(",").append(col).append(")  ");
                sb.append(link.remoteDeviceType).append(":");
                sb.append(link.remoteDeviceName).append(":");
                sb.append(link.remotePortName).append(":").append(link.remotePortType);
                sb.append(" ; ");
            }
            sb.append("\n");
        }

        final Map<AFOXRawPort, AFOXRawPort> crossConnMap = mlAFOXConfig.crossConnMap();
        if (crossConnMap.size() > 0) {
            sb.append("CrossConns = ");
            for (final Map.Entry<AFOXRawPort, AFOXRawPort> entry : crossConnMap.entrySet()) {
                final AFOXRawPort sPort = entry.getKey();
                final AFOXRawPort dPort = entry.getValue();

                final int srow = sPort.portRow;
                final int scol = sPort.portColumn;

                final int drow = dPort.portRow;
                final int dcol = dPort.portColumn;

                sb.append("(").append(srow).append(",").append(scol).append(") ");
                sb.append("(").append(drow).append(",").append(dcol).append(") ; ");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public static OpticalSwitch fromAfoxConfig(MLAFOXConfig config) throws TopologyException {
        final OpticalSwitch os = new OpticalSwitch(OpticalSwitchType.AFOX, config.hostName());
        final List<AFOXRawPort> configPorts = config.hostPorts();
        final Map<AFOXRawPort, AFOXOSPort> rPMAP = new HashMap<AFOXRawPort, AFOXOSPort>();
        for (final AFOXRawPort pp : configPorts) {
            try {
                final String pName = "(" + pp.portRow + "," + pp.portColumn + ")";
                final String rfidName = "(" + pp.portRow + "," + pp.portColumn + ")";
                if ((pp.portType == PortType.INPUT_PORT) || (pp.portType == PortType.OUTPUT_PORT)) {
                    AFOXOSPort.Builder<Port<?, ?>> bIN = new AFOXOSPort.Builder<Port<?, ?>>(pName, pp.portType, os,
                            null, null);
                    bIN.row(pp.portRow).column(pp.portColumn).customerName(pName).afoxRawPort(pp);
                    bIN.serialNumber(rfidName);
                    final AFOXOSPort osPort = bIN.build();
                    rPMAP.put(pp, osPort);
                    os.addPort(osPort);
                } else {
                    logger.log(Level.WARNING, " Afox switch accepts only INPUT_PORT or OUTPUT_PORT. The port: " + pp
                            + " will be ignored");
                }

            } catch (Throwable t) {
                logger.log(Level.WARNING, " Exception in fromConfigAndMsg for port: " + pp, t);
            }
        }
        final Map<AFOXRawPort, AFOXRawPort> xConfMap = config.crossConnMap();

        for (final Map.Entry<AFOXRawPort, AFOXRawPort> entry : xConfMap.entrySet()) {
            final AFOXRawPort sRawPort = entry.getKey();
            final AFOXRawPort dRawPort = entry.getValue();

            final AFOXOSPort sPort = rPMAP.get(sRawPort);
            final AFOXOSPort dPort = rPMAP.get(dRawPort);
            os.addCrossConn(new Link(sPort, dPort));
        }
        return os;
    }
}
