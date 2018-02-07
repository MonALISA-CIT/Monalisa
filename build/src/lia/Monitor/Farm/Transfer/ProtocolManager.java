package lia.Monitor.Farm.Transfer;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This manages the transfer protocols. Splits the whole AppTransfer's configuration on 
 * a per-protocol basis and gives it to the corresponding protocol.
 * 
 * Commands are also split and forwarded to the protocol in cause.
 * 
 * It also allows collecting monitoring information from each protocol. In order for this
 * to work, the monAppTransfer monitoring module must be in the ML's configuration.
 * 
 * @author catac
 */
public class ProtocolManager {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(ProtocolManager.class.getName());

    /** 
     * AppTransfer's used protocols. 
     * Key = protocol's name; value = corresponding TransferProtocol extended instance.
     */
    private final Hashtable htUsedProtocols;

    private static final ProtocolManager _theInstance = new ProtocolManager();

    private ProtocolManager() {
        htUsedProtocols = new Hashtable();
    }

    /**
     * Get the one and only instance of the Protocol Manager
     * @return the instance
     */
    public static ProtocolManager getInstance() {
        return _theInstance;
    }

    /**
     * Setup the used protocols.
     * @param prop configuration properties to pass to each protocol. 
     * Each protocol receives its own properties only.  
     */
    public void configProtocols(Properties prop) {
        String protocols = prop.getProperty("protocols", "").trim();
        if (logger.isLoggable(Level.INFO)) {
            if (protocols.length() == 0) {
                logger.warning("No transfer protocols defined!");
            } else {
                logger.info("(Re)Initializing the following transfer protocols: " + protocols + ".");
            }
        }

        HashSet hsUnusedProto = new HashSet();
        hsUnusedProto.addAll(htUsedProtocols.keySet());

        StringTokenizer usedProtos = new StringTokenizer(protocols, ",");
        while (usedProtos.hasMoreTokens()) {
            String proto = usedProtos.nextToken().trim().toLowerCase();
            TransferProtocol tp = (TransferProtocol) htUsedProtocols.get(proto);
            if (tp == null) {
                if (proto.equals("fdt")) {
                    tp = new FDTProtocol();
                } else if (proto.equals("rsv")) {
                    tp = new ReservationProtocol();
                } else if (proto.equals("link")) {
                    tp = new LinkProtocol();
                }
                if (tp != null) {
                    htUsedProtocols.put(tp.name, tp);
                } else {
                    logger.warning("Unknown protocol " + proto + "! Ignoring it!");
                    continue;
                }
            }
            tp.setConfig(prop);
            hsUnusedProto.remove(proto); // protocol still used; remove it from the unused list 
        }

        // shutdown the unused protocols
        for (Iterator meit = htUsedProtocols.entrySet().iterator(); meit.hasNext();) {
            Map.Entry pem = (Map.Entry) meit.next();
            String name = (String) pem.getKey();
            TransferProtocol tp = (TransferProtocol) pem.getValue();
            if (hsUnusedProto.contains(name)) {
                meit.remove();
                tp.shutdownProtocol();
            }
        }
    }

    /** Terminate immediately all running protocols and their instances. */
    public void shutdownProtocols() {
        for (Iterator pit = htUsedProtocols.values().iterator(); pit.hasNext();) {
            TransferProtocol tp = (TransferProtocol) pit.next();
            tp.shutdownProtocol();
            pit.remove();
        }
    }

    /**
     * Execute an AppControl command and return its output to the user. This command
     * can be executed by the protocol manager or forwarded to the corresponding protocol.
     * @param sCmd the command string to execute.
     */
    public String execCommand(String sCmd) {
        int spcIdx = sCmd.indexOf(" ");
        String command = (spcIdx == -1 ? sCmd : sCmd.substring(0, spcIdx));
        String params = (spcIdx == -1 ? "" : sCmd.substring(spcIdx + 1));
        StringBuilder sb = new StringBuilder();
        if (command.equals("protocols")) {
            for (Iterator pit = htUsedProtocols.keySet().iterator(); pit.hasNext();) {
                sb.append(pit.next());
                if (pit.hasNext()) {
                    sb.append("\n");
                }
            }
        } else {
            // the command is for one the available protocols
            // the first word in the command is the protocol name
            TransferProtocol tp = (TransferProtocol) htUsedProtocols.get(command);
            if (tp == null) {
                sb.append("-ERR Command or Protocol unknown: '").append(command).append("'");
            } else {
                sb.append(tp.exec(params));
            }
        }
        return sb.toString();
    }

    /**
     * Retrieve monitoring information from all protocols.
     * @param lResults list where to add the monitoring information collected from each protocol. 
     */
    public void getMonitorInfo(List lResults) {
        for (Iterator pit = htUsedProtocols.values().iterator(); pit.hasNext();) {
            TransferProtocol tp = (TransferProtocol) pit.next();
            tp.getMonitorInfo(lResults);
        }
    }

    /** Get the instance of the requested protocol
     * @param protocolName the name of the protocol to retrieve
     * @return the requested protocol or null if no protocol with that name is currently used.
     */
    public TransferProtocol getTransferProtocol(String protocolName) {
        return (TransferProtocol) htUsedProtocols.get(protocolName);
    }
}
