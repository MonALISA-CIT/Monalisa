package lia.util.snmp4j.util;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.eResult;
import lia.util.snmp4j.SNMPFactory;

import org.snmp4j.PDU;
import org.snmp4j.smi.Counter32;
import org.snmp4j.smi.Counter64;
import org.snmp4j.smi.Gauge32;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.VariableBinding;

/**
 * Class used to gather informations for TCP through the use of SNMP.
 */
public class SNMPTCP {

    private static final Logger logger = Logger.getLogger(SNMPTCP.class.getName());

    private final SNMPFactory snmp;
    private final String defaultOptions;
    private final String snmpAddress;
    private final String farmName;
    private final String clusterName;
    private final String moduleName;

    static final String node = "SNMPTCP";

    /** The algorithm used to determine the timeout value used for retransmitting unacknowledged octets. */
    static final String tcpRtoAlgorithmOID = "1.3.6.1.2.1.6.1.";
    /** The minimum value permitted by a TCP implementation for the retransmission timeout, measured in milliseconds. More refined semantics for 
     * objects of this type depend on the algorithm used to determine the retransmission timeout; in particular, the IETF standard algorithm rfc2988(5) 
     * provides a minimum value. */
    static final String tcpRtoMinOID = "1.3.6.1.2.1.6.2.";
    /** The maximum value permitted by a TCP implementation for the retransmission timeout, measured in milliseconds. More refined semantics for 
     * objects of this type depend on the algorithm used to determine the retransmission timeout; in particular, the IETF standard algorithm rfc2988(5) 
     * provides an upper bound (as part of an adaptive backoff algorithm). */
    static final String tcpRtoMaxOID = "1.3.6.1.2.1.6.3.";
    /** The limit on the total number of TCP connections the entity can support. In entities where the maximum number of connections is dynamic, this 
     * object should contain the value -1. */
    static final String tcpMaxConnOID = "1.3.6.1.2.1.6.4.";
    /** The number of times that TCP connections have made a direct transition to the SYN-SENT state from the CLOSED state. Discontinuities in the 
     * value of this counter are indicated via discontinuities in the value of sysUpTime. */
    static final String tcpActiveOpensOID = "1.3.6.1.2.1.6.5.";
    /** The number of times TCP connections have made a direct transition to the SYN-RCVD state from the LISTEN state. Discontinuities in the value of 
     * this counter are indicated via discontinuities in the value of sysUpTime. */
    static final String tcpPassiveOpensOID = "1.3.6.1.2.1.6.6.";
    /** The number of times that TCP connections have made a direct transition to the CLOSED state from either the SYN-SENT state or the SYN-RCVD 
     * state, plus the number of times that TCP connections have made a direct transition to the LISTEN state from the SYN-RCVD state. Discontinuities 
     * in the value of this counter are indicated via discontinuities in the value of sysUpTime. */
    static final String tcpAttemptFailsOID = "1.3.6.1.2.1.6.7.";
    /** The number of times that TCP connections have made a direct transition to the CLOSED state from either the ESTABLISHED state or the 
     * CLOSE-WAIT state. Discontinuities in the value of this counter are indicated via discontinuities in the value of sysUpTime. */
    static final String tcpEstabResetsOID = "1.3.6.1.2.1.6.8.";
    /** The number of TCP connections for which the current state is either ESTABLISHED or CLOSE-WAIT. */
    static final String tcpCurrEstabOID = "1.3.6.1.2.1.6.9.";
    /** The total number of segments received, including those received in error. This count includes segments received on currently established 
     * connections. Discontinuities in the value of this counter are indicated via discontinuities in the value of sysUpTime. */
    static final String tcpInSegsOID = "1.3.6.1.2.1.6.10.";
    /** The total number of segments sent, including those on current connections but excluding those containing only retransmitted octets. Discontinuities 
     * in the value of this counter are indicated via discontinuities in the value of sysUpTime. */
    static final String tcpOutSegsOID = "1.3.6.1.2.1.6.11.";
    /** The total number of segments retransmitted; that is, the number of TCP segments transmitted containing one or more previously transmitted octets. 
     * Discontinuities in the value of this counter are indicated via discontinuities in the value of sysUpTime. */
    static final String tcpRetransSegsOID = "1.3.6.1.2.1.6.12.";
    /** The total number of segments received in error (e.g., bad TCP checksums). Discontinuities in the value of this counter are indicated via 
     * discontinuities in the value of sysUpTime. */
    static final String tcpInErrsOID = "1.3.6.1.2.1.6.14.";
    /** The number of TCP segments sent containing the RST flag. Discontinuities in the value of this counter are indicated via discontinuities in the value 
     * of sysUpTime. */
    static final String tcpOutRstsOID = "1.3.6.1.2.1.6.15.";
    /** The total number of segments received, including those received in error. This count includes segments received on currently established 
     * connections. This object is the 64-bit equivalent of tcpInSegs. Discontinuities in the value of this counter are indicated via discontinuities in the 
     * value of sysUpTime. */
    static final String tcpHCInSegsOID = "1.3.6.1.2.1.6.17.";
    /** The total number of segments sent, including those on current connections but excluding those containing only retransmitted octets. This object is 
     * the 64-bit equivalent of tcpOutSegs. Discontinuities in the value of this counter are indicated via discontinuities in the value of sysUpTime. */
    static final String tcpHCOutSegsOID = "1.3.6.1.2.1.6.18.";
    /** The state of this TCP connection. The only value that may be set by a management station is deleteTCB(12). Accordingly, it is appropriate for 
     * an agent to return a `badValue' response if a management station attempts to set this object to any other value. If a management station sets this 
     * object to the value deleteTCB(12), then the TCB (as defined in [RFC793]) of the corresponding connection on the managed node is deleted, 
     * resulting in immediate termination of the connection. As an implementation-specific option, a RST segment may be sent from the managed node to 
     * the other TCP endpoint (note, however, that RST segments are not sent reliably). */
    static final String tcpConnStateOID = "1.3.6.1.2.1.6.13.1.1.";
    /** The address type of tcpConnectionLocalAddress. */
    static final String tcpConnectionLocalAddressTypeOID = "1.3.6.1.2.1.6.19.1.1.";
    /** The local IP address for this TCP connection. The type of this address is determined by the value of tcpConnectionLocalAddressType.*/
    static final String tcpConnectionLocalAddressOID = "1.3.6.1.2.1.6.19.1.2.";
    /** The local port number for this TCP connection. */
    static final String tcpConnectionLocalPortOID = "1.3.6.1.2.1.6.19.1.3.";
    /** The address type of tcpConnectionRemAddress. */
    static final String tcpConnectionRemAddressTypeOID = "1.3.6.1.2.1.6.19.1.4.";
    /** The remote IP address for this TCP connection. The type of this address is determined by the value of tcpConnectionRemAddressType. */
    static final String tcpConnectionRemAddressOID = "1.3.6.1.2.1.6.19.1.5.";
    /** The remote port number for this TCP connection. */
    static final String tcpConnectionRemPortOID = "1.3.6.1.2.1.6.19.1.6.";
    /** The state of this TCP connection. The value listen(2) is included only for parallelism to the old tcpConnTable and should not be used. A 
     * connection in LISTEN state should be present in the tcpListenerTable. The only value that may be set by a management station is deleteTCB(12). 
     * Accordingly, it is appropriate for an agent to return a `badValue' response if a management station attempts to set this object to any other value. */
    static final String tcpConnectionStateOID = "1.3.6.1.2.1.6.19.1.7.";
    /** The system's process ID for the process associated with this connection, or zero if there is no such process. This value is expected to be the same as 
     * HOST-RESOURCES-MIB:: hrSWRunIndex or SYSAPPL-MIB::sysApplElmtRunIndex for some row in the appropriate tables. */
    static final String tcpConnectionProcessOID = "1.3.6.1.2.1.6.19.1.8.";
    /** The address type of tcpListenerLocalAddress. The value should be unknown (0) if connection initiations to all local IP addresses are accepted. */
    static final String tcpListenerLocalAddressTypeOID = "1.3.6.1.2.1.6.20.1.1.";
    /** The local IP address for this TCP connection. */
    static final String tcpListenerLocalAddressOID = "1.3.6.1.2.1.6.20.1.2.";
    /** The local port number for this TCP connection. */
    static final String tcpListenerLocalPortOID = "1.3.6.1.2.1.6.20.1.3.";
    /** The system's process ID for the process associated with this listener, or zero if there is no such process. This value is expected to be the same as 
     * HOST-RESOURCES-MIB::hrSWRunIndex or SYSAPPL-MIB::sysApplElmtRunIndex for some row in the appropriate tables. */
    static final String tcpListenerProcessOID = "1.3.6.1.2.1.6.20.1.4.";

    /** Flags */
    boolean tcpRtoAlgorithmFlag = true;
    boolean tcpRtoMinFlag = true;
    boolean tcpRtoMaxFlag = true;
    boolean tcpMaxConnFlag = true;
    boolean tcpActiveOpensFlag = true;
    boolean tcpPassiveOpensFlag = true;
    boolean tcpAttemptFailsFlag = true;
    boolean tcpEstabResetsFlag = true;
    boolean tcpCurrEstabFlag = true;
    boolean tcpInSegsFlag = true;
    boolean tcpOutSegsFlag = true;
    boolean tcpRetransSegsFlag = true;
    boolean tcpInErrsFlag = true;
    boolean tcpOutRstsFlag = true;
    boolean tcpHCInSegsFlag = true;
    boolean tcpHCOutSegsFlag = true;
    boolean tcpConnFlag = true;
    boolean tcpConnectionFlag = true;
    boolean tcpListenerFlag = true;

    /** See SNMPFactory for details about defaultOptions....like -A, -a, -v... */
    public SNMPTCP(String defaultOptions, SNMPFactory snmp, String snmpAddress, String farmName, String clusterName,
            String moduleName) {
        this.snmp = snmp;
        this.defaultOptions = defaultOptions.trim();
        this.snmpAddress = snmpAddress.trim();
        this.farmName = farmName;
        this.clusterName = clusterName;
        this.moduleName = moduleName;
    }

    public void setFlags(boolean tcpRtoAlgorithmFlag, boolean tcpRtoMinFlag, boolean tcpRtoMaxFlag,
            boolean tcpMaxConnFlag, boolean tcpActiveOpensFlag, boolean tcpPassiveOpensFlag,
            boolean tcpAttemptFailsFlag, boolean tcpEstabResetsFlag, boolean tcpCurrEstabFlag, boolean tcpInSegsFlag,
            boolean tcpOutSegsFlag, boolean tcpRetransSegsFlag, boolean tcpInErrsFlag, boolean tcpOutRstsFlag,
            boolean tcpHCInSegsFlag, boolean tcpHCOutSegsFlag, boolean tcpConnFlag, boolean tcpConnectionFlag,
            boolean tcpListenerFlag) {

        this.tcpRtoAlgorithmFlag = tcpRtoAlgorithmFlag;
        this.tcpRtoMinFlag = tcpRtoMinFlag;
        this.tcpRtoMaxFlag = tcpRtoMaxFlag;
        this.tcpMaxConnFlag = tcpMaxConnFlag;
        this.tcpActiveOpensFlag = tcpActiveOpensFlag;
        this.tcpPassiveOpensFlag = tcpPassiveOpensFlag;
        this.tcpAttemptFailsFlag = tcpAttemptFailsFlag;
        this.tcpEstabResetsFlag = tcpEstabResetsFlag;
        this.tcpCurrEstabFlag = tcpCurrEstabFlag;
        this.tcpInSegsFlag = tcpInSegsFlag;
        this.tcpOutSegsFlag = tcpOutSegsFlag;
        this.tcpRetransSegsFlag = tcpRetransSegsFlag;
        this.tcpInErrsFlag = tcpInErrsFlag;
        this.tcpOutRstsFlag = tcpOutRstsFlag;
        this.tcpHCInSegsFlag = tcpHCInSegsFlag;
        this.tcpHCOutSegsFlag = tcpHCOutSegsFlag;
        this.tcpConnFlag = tcpConnFlag;
        this.tcpConnectionFlag = tcpConnectionFlag;
        this.tcpListenerFlag = tcpListenerFlag;
    }

    public void getResults(Vector res) {

        PDU[] pdu = snmp.run(defaultOptions + " -p GETBULK -Ow " + snmpAddress + " 1.3.6.1.2.1.6", null, null);
        if ((pdu == null) || (pdu.length == 0)) {
            return; // no result
        }
        eResult result = null;
        Hashtable tcpConnectionTable = new Hashtable();
        Hashtable tcpListenerTable = new Hashtable();
        for (PDU element : pdu) {
            if (element.getType() == PDU.REPORT) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINE, SNMPFactory.getReport(element));
                }
                continue;
            }
            for (int k = 0; k < element.size(); k++) {
                try {
                    VariableBinding vb = element.get(k);
                    if (vb.isException()) {
                        continue;
                    }
                    String oid = vb.getOid().toString().trim();
                    String param = vb.getVariable().toString();
                    if ((param == null) || (param.length() == 0) || param.equals("Null")) {
                        continue;
                    }
                    if (result == null) {
                        result = new eResult();
                        result.FarmName = farmName;
                        result.ClusterName = clusterName;
                        result.Module = moduleName;
                        result.NodeName = node;
                    }
                    if (oid.startsWith(tcpRtoAlgorithmOID) && tcpRtoAlgorithmFlag) {
                        result.addSet("TcpRtoAlgorithm", getTCPRToAlgorithm(param));
                    } else if (oid.startsWith(tcpRtoMinOID) && tcpRtoMinFlag) {
                        result.addSet("TcpRtoMin", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(tcpRtoMaxOID) && tcpRtoMaxFlag) {
                        result.addSet("TcpRtoMax", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(tcpMaxConnOID) && tcpMaxConnFlag) {
                        result.addSet("TcpMaxConn", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(tcpActiveOpensOID) && tcpActiveOpensFlag) {
                        result.addSet("TcpActiveOpens", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(tcpPassiveOpensOID) && tcpPassiveOpensFlag) {
                        result.addSet("TcpPassiveOpens", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(tcpAttemptFailsOID) && tcpAttemptFailsFlag) {
                        result.addSet("TcpAttemptFails", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(tcpEstabResetsOID) && tcpEstabResetsFlag) {
                        result.addSet("TcpEstabResets", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(tcpCurrEstabOID) && tcpCurrEstabFlag) {
                        result.addSet("TcpCurrEstab", Long.valueOf(((Gauge32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(tcpInSegsOID) && tcpInSegsFlag) {
                        result.addSet("TcpInSegs", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(tcpOutSegsOID) && tcpOutSegsFlag) {
                        result.addSet("TcpOutSegs", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(tcpRetransSegsOID) && tcpRetransSegsFlag) {
                        result.addSet("TcpRetransSegs", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(tcpInErrsOID) && tcpInErrsFlag) {
                        result.addSet("TcpInErrs", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(tcpOutRstsOID) && tcpOutRstsFlag) {
                        result.addSet("TcpOutRsts", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(tcpHCInSegsOID) && tcpHCInSegsFlag) {
                        result.addSet("TcpHCInSegs", Long.valueOf(((Counter64) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(tcpHCOutSegsOID) && tcpHCOutSegsFlag) {
                        result.addSet("TcpHCOutSegs", Long.valueOf(((Counter64) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(tcpConnStateOID) && tcpConnFlag) {
                        String id = oid.substring(tcpConnStateOID.length()); // since it's IPv4, format it
                        String p[] = id.trim().replace('.', ',').split(",");
                        if ((p != null) && (p.length == 10)) {
                            String sIP = p[0] + "." + p[1] + "." + p[2] + "." + p[3];
                            String sPort = p[4];
                            String dIP = p[5] + "." + p[6] + "." + p[7] + "." + p[8];
                            String dPort = p[9];
                            eResult result1 = new eResult();
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.NodeName = sIP + ":" + sPort + " - " + dIP + ":" + dPort;
                            result1.addSet("TcpConnState", getTCPConnState(param));
                            res.add(result1);
                        }
                    } else if (oid.startsWith(tcpConnectionLocalAddressTypeOID) && tcpConnectionFlag) {
                        String id = oid.substring(tcpConnectionLocalAddressTypeOID.length());
                        Hashtable h = null;
                        if (!tcpConnectionTable.containsKey(id)) {
                            h = new Hashtable();
                            tcpConnectionTable.put(id, h);
                        } else {
                            h = (Hashtable) tcpConnectionTable.get(id);
                        }
                        h.put("TcpConnectionLocalAddressType", getTCPAddressType(param));
                    } else if (oid.startsWith(tcpConnectionLocalAddressOID) && tcpConnectionFlag) {
                        String id = oid.substring(tcpConnectionLocalAddressOID.length());
                        Hashtable h = null;
                        if (!tcpConnectionTable.containsKey(id)) {
                            h = new Hashtable();
                            tcpConnectionTable.put(id, h);
                        } else {
                            h = (Hashtable) tcpConnectionTable.get(id);
                        }
                        h.put("TcpConnectionLocalAddress", param);
                    } else if (oid.startsWith(tcpConnectionLocalPortOID) && tcpConnectionFlag) {
                        String id = oid.substring(tcpConnectionLocalPortOID.length());
                        Hashtable h = null;
                        if (!tcpConnectionTable.containsKey(id)) {
                            h = new Hashtable();
                            tcpConnectionTable.put(id, h);
                        } else {
                            h = (Hashtable) tcpConnectionTable.get(id);
                        }
                        h.put("TcpConnectionLocalPort", param);
                    } else if (oid.startsWith(tcpConnectionRemAddressTypeOID) && tcpConnectionFlag) {
                        String id = oid.substring(tcpConnectionRemAddressTypeOID.length());
                        Hashtable h = null;
                        if (!tcpConnectionTable.containsKey(id)) {
                            h = new Hashtable();
                            tcpConnectionTable.put(id, h);
                        } else {
                            h = (Hashtable) tcpConnectionTable.get(id);
                        }
                        h.put("TcpConnectionRemAddressType", getTCPAddressType(param));
                    } else if (oid.startsWith(tcpConnectionRemAddressOID) && tcpConnectionFlag) {
                        String id = oid.substring(tcpConnectionRemAddressOID.length());
                        Hashtable h = null;
                        if (!tcpConnectionTable.containsKey(id)) {
                            h = new Hashtable();
                            tcpConnectionTable.put(id, h);
                        } else {
                            h = (Hashtable) tcpConnectionTable.get(id);
                        }
                        h.put("TcpConnectionRemAddress", param);
                    } else if (oid.startsWith(tcpConnectionRemPortOID) && tcpConnectionFlag) {
                        String id = oid.substring(tcpConnectionRemPortOID.length());
                        Hashtable h = null;
                        if (!tcpConnectionTable.containsKey(id)) {
                            h = new Hashtable();
                            tcpConnectionTable.put(id, h);
                        } else {
                            h = (Hashtable) tcpConnectionTable.get(id);
                        }
                        h.put("TcpConnectionRemPort", param);
                    } else if (oid.startsWith(tcpConnectionStateOID) && tcpConnectionFlag) {
                        String id = oid.substring(tcpConnectionStateOID.length());
                        Hashtable h = null;
                        if (!tcpConnectionTable.containsKey(id)) {
                            h = new Hashtable();
                            tcpConnectionTable.put(id, h);
                        } else {
                            h = (Hashtable) tcpConnectionTable.get(id);
                        }
                        h.put("TcpConnectionState", getTCPConnState(param));
                    } else if (oid.startsWith(tcpConnectionProcessOID) && tcpConnectionFlag) {
                        String id = oid.substring(tcpConnectionProcessOID.length());
                        Hashtable h = null;
                        if (!tcpConnectionTable.containsKey(id)) {
                            h = new Hashtable();
                            tcpConnectionTable.put(id, h);
                        } else {
                            h = (Hashtable) tcpConnectionTable.get(id);
                        }
                        h.put("TcpConnectionProcess", param);
                    } else if (oid.startsWith(tcpListenerLocalAddressTypeOID) && tcpListenerFlag) {
                        String id = oid.substring(tcpListenerLocalAddressTypeOID.length());
                        Hashtable h = null;
                        if (!tcpListenerTable.containsKey(id)) {
                            h = new Hashtable();
                            tcpListenerTable.put(id, h);
                        } else {
                            h = (Hashtable) tcpListenerTable.get(id);
                        }
                        h.put("TcpListenerLocalAddressType", getTCPAddressType(param));
                    } else if (oid.startsWith(tcpListenerLocalAddressOID) && tcpListenerFlag) {
                        String id = oid.substring(tcpListenerLocalAddressOID.length());
                        Hashtable h = null;
                        if (!tcpListenerTable.containsKey(id)) {
                            h = new Hashtable();
                            tcpListenerTable.put(id, h);
                        } else {
                            h = (Hashtable) tcpListenerTable.get(id);
                        }
                        h.put("TcpListenerLocalAddress", param);
                    } else if (oid.startsWith(tcpListenerLocalPortOID) && tcpListenerFlag) {
                        String id = oid.substring(tcpListenerLocalPortOID.length());
                        Hashtable h = null;
                        if (!tcpListenerTable.containsKey(id)) {
                            h = new Hashtable();
                            tcpListenerTable.put(id, h);
                        } else {
                            h = (Hashtable) tcpListenerTable.get(id);
                        }
                        h.put("TcpListenerLocalPort", param);
                    } else if (oid.startsWith(tcpListenerProcessOID) && tcpListenerFlag) {
                        String id = oid.substring(tcpListenerProcessOID.length());
                        Hashtable h = null;
                        if (!tcpListenerTable.containsKey(id)) {
                            h = new Hashtable();
                            tcpListenerTable.put(id, h);
                        } else {
                            h = (Hashtable) tcpListenerTable.get(id);
                        }
                        h.put("TcpListenerProcess", param);
                    }
                } catch (Throwable t) {
                    logger.warning(t.getLocalizedMessage());
                }
            }
        }
        if ((result != null) && (result.param_name != null) && (result.param_name.length != 0)) {
            res.add(result);
        }
        if (tcpConnectionTable.size() != 0) {
            for (Enumeration en = tcpConnectionTable.keys(); en.hasMoreElements();) {
                String id = (String) en.nextElement();
                if (id == null) {
                    continue;
                }
                Hashtable h = (Hashtable) tcpConnectionTable.get(id);
                if ((h == null) || (h.size() == 0)) {
                    continue;
                }
                String localAddress = (String) h.get("TcpConnectionLocalAddress");
                String localPort = (String) h.get("TcpConnectionLocalPort");
                String remoteAddress = (String) h.get("TcpConnectionRemAddress");
                String remotePort = (String) h.get("TcpConnectionRemPort");
                if ((localAddress == null) || (localPort == null) || (remoteAddress == null) || (remotePort == null)) {
                    continue;
                }
                eResult result1 = new eResult();
                result1.FarmName = farmName;
                result1.ClusterName = clusterName;
                result1.Module = moduleName;
                result1.NodeName = localAddress + ":" + localPort + " - " + remoteAddress + ":" + remotePort;
                String localAddressType = (String) h.get("TcpConnectionLocalAddressType");
                if (localAddressType != null) {
                    result1.addSet("TcpConnectionLocalAddressType", localAddressType);
                }
                String remoteAddressType = (String) h.get("TcpConnectionRemAddressType");
                if (remoteAddressType != null) {
                    result1.addSet("TcpConnectionRemAddressType", remoteAddressType);
                }
                String connectionState = (String) h.get("TcpConnectionState");
                if (connectionState != null) {
                    result1.addSet("TcpConnectionState", connectionState);
                }
                String connectionProcess = (String) h.get("TcpConnectionProcess");
                if (connectionProcess != null) {
                    result1.addSet("TcpConnectionProcess", connectionProcess);
                }
                res.add(result1);
            }
        }
        if (tcpListenerTable.size() != 0) {
            for (Enumeration en = tcpListenerTable.keys(); en.hasMoreElements();) {
                String id = (String) en.nextElement();
                if (id == null) {
                    continue;
                }
                Hashtable h = (Hashtable) tcpListenerTable.get(id);
                if ((h == null) || (h.size() == 0)) {
                    continue;
                }
                String localAddress = (String) h.get("TcpListenerLocalAddress");
                String localPort = (String) h.get("TcpListenerLocalPort");
                if ((localAddress == null) || (localPort == null)) {
                    continue;
                }
                eResult result1 = new eResult();
                result1.FarmName = farmName;
                result1.ClusterName = clusterName;
                result1.Module = moduleName;
                result1.NodeName = localAddress + ":" + localPort;
                String localAddressType = (String) h.get("TcpListenerLocalAddressType");
                if (localAddressType != null) {
                    result1.addSet("TcpListenerLocalAddressType", localAddressType);
                }
                String connectionProcess = (String) h.get("TcpListenerProcess");
                if (connectionProcess != null) {
                    result1.addSet("TcpListenerProcess", connectionProcess);
                }
                res.add(result1);
            }
        }
    }

    /** The parameters indicate the connection to which we are addressing */
    public boolean closeConnection(String sourceIP, String sourcePort, String destIP, String destPort) {

        StringBuilder buf = new StringBuilder();
        buf.append(defaultOptions).append(" -p SET ").append(snmpAddress).append(" ");
        buf.append(tcpConnStateOID).append(sourceIP).append(".").append(sourcePort).append(".");
        buf.append(destIP).append(".").append(destPort).append("={i}12");
        PDU pdu[] = snmp.run(buf.toString(), null, null);
        if ((pdu == null) || (pdu.length == 0)) {
            return false;
        }
        for (PDU element : pdu) {
            if (element.getErrorStatus() != 0) {
                logger.info(element.toString());
                return false;
            }
            for (int k = 0; k < element.size(); k++) {
                try {
                    VariableBinding vb = element.get(k);
                    if (vb.isException()) {
                        logger.info(element.toString());
                        return false;
                    }
                } catch (Throwable t) {
                    logger.warning(t.getLocalizedMessage());
                }
            }
        }
        return true;
    }

    static final String RToAlgorithms[][] = new String[][] { { "1", "Other" }, { "2", "Constant" }, { "3", "Rsre" },
            { "4", "Vanj" }, { "5", "Rfc2988" } };

    private String getTCPRToAlgorithm(String type) {

        if (type == null) {
            return "Null";
        }
        for (String[] rToAlgorithm : RToAlgorithms) {
            if (rToAlgorithm[0].equals(type)) {
                return rToAlgorithm[1];
            }
        }
        return "Unknown";
    }

    static final String connStates[][] = new String[][] { { "1", "Closed" }, { "2", "Listen" }, { "3", "SynSent" },
            { "4", "SynReceived" }, { "5", "Established" }, { "6", "FinWait1" }, { "7", "FinWait2" },
            { "8", "CloseWait" }, { "9", "LastAck" }, { "10", "Closing" }, { "11", "TimeWait" }, { "12", "DeleteTCB" } };

    private String getTCPConnState(String state) {

        if (state == null) {
            return "Null";
        }
        for (String[] connState : connStates) {
            if (connState[0].equals(state)) {
                return connState[1];
            }
        }
        return "Unknown";
    }

    static final String addressTypes[][] = new String[][] { { "0", "Unknown" }, { "1", "IPv4" }, { "2", "IPv6" },
            { "3", "IPv4z" }, { "4", "IPv6z" }, { "16", "DNS" } };

    private String getTCPAddressType(String type) {

        if (type == null) {
            return "Null";
        }
        for (String[] addressType : addressTypes) {
            if (addressType[0].equals(type)) {
                return addressType[1];
            }
        }
        return "Unknown";
    }

    /** For testing */
    public static void main(String args[]) {

        logger.setLevel(Level.FINEST);
        SNMPFactory factory = new SNMPFactory();
        SNMPTCP route = new SNMPTCP("-c private -v 2c", factory, "udp:141.85.99.136/161", "RB", "SNMPTCP", "SNMPModule");
        //		route.setFlags(false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, true, false, false);
        Vector v = new Vector();
        route.getResults(v);
        for (int i = 0; i < v.size(); i++) {
            System.out.println(v.get(i));
        }
        route.closeConnection("127.0.0.1", "25", "0.0.0.0", "0");
    }

} // end of class SNMPTCP

