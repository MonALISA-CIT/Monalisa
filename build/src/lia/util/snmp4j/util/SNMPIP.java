package lia.util.snmp4j.util;

import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.eResult;
import lia.util.snmp4j.SNMPFactory;

import org.snmp4j.PDU;
import org.snmp4j.smi.Counter32;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.VariableBinding;

/**
 * Class used to gather informations for IP networking through the use of SNMP.
 */
public class SNMPIP {

    private static final Logger logger = Logger.getLogger(SNMPIP.class.getName());

    private final SNMPFactory snmp;
    private final String defaultOptions;
    private final String snmpAddress;
    private final String farmName;
    private final String clusterName;
    private final String moduleName;

    static final String node = "SNMPIP";

    /** The indication of whether this entity is acting as an IP gateway in respect to the forwarding of datagrams received by, but not addressed to, this 
     * entity. IP gateways forward datagrams. IP hosts do not (except those source-routed via the host). Note that for some managed nodes, this object may 
     * take on only a subset of the values possible. Accordingly, it is appropriate for an agent to return a `badValue' response if a management station 
     * attempts to change this object to an inappropriate value. */
    static final String ipForwardingOID = "1.3.6.1.2.1.4.1.";
    /** The default value inserted into the Time-To-Live field of the IP header of datagrams originated at this entity, whenever a TTL value is not supplied 
     * by the transport layer protocol. */
    static final String ipDefaultTTLOID = "1.3.6.1.2.1.4.2.";
    /** The total number of input datagrams received from interfaces, including those received in error. */
    static final String ipInReceivesOID = "1.3.6.1.2.1.4.3.";
    /** The number of input datagrams discarded due to errors in their IP headers, including bad checksums, version number mismatch, other format 
     * errors, time-to-live exceeded, errors discovered in processing their IP options, etc. */
    static final String ipInHdrErrorsOID = "1.3.6.1.2.1.4.4.";
    /** The number of input datagrams discarded because the IP address in their IP header's destination field was not a valid address to be received at 
     * this entity. This count includes invalid addresses (e.g., 0.0.0.0) and addresses of unsupported Classes (e.g., Class E). For entities which are not 
     * IP Gateways and therefore do not forward datagrams, this counter includes datagrams discarded because the destination address was not 
     * a local address. */
    static final String ipInAddrErrorsOID = "1.3.6.1.2.1.4.5.";
    /** The number of input datagrams for which this entity was not their final IP destination, as a result of which an attempt was made to find a 
     * route to forward them to that final destination. In entities which do not act as IP Gateways, this counter will include only those packets which were 
     * Source-Routed via this entity, and the Source-Route option processing was successful.*/
    static final String ipForwDatagramsOID = "1.3.6.1.2.1.4.6.";
    /** The number of locally-addressed datagrams received successfully but discarded because of an unknown or unsupported protocol. */
    static final String ipInUnknownProtosOID = "1.3.6.1.2.1.4.7.";
    /** The number of input IP datagrams for which no problems were encountered to prevent their continued processing, but which were discarded 
     * (e.g., for lack of buffer space). Note that this counter does not include any datagrams discarded while awaiting re-assembly. */
    static final String ipInDiscardsOID = "1.3.6.1.2.1.4.8.";
    /** The total number of input datagrams successfully delivered to IP user-protocols (including ICMP).*/
    static final String ipInDeliversOID = "1.3.6.1.2.1.4.9.";
    /** The total number of IP datagrams which local IP user-protocols (including ICMP) supplied to IP in requests for transmission. Note that this counter 
     * does not include any datagrams counted in ipForwDatagrams. */
    static final String ipOutRequestsOID = "1.3.6.1.2.1.4.10.";
    /** The number of output IP datagrams for which no problem was encountered to prevent their transmission to their destination, but which were 
     * discarded (e.g., for lack of buffer space). Note that this counter would include datagrams counted in ipForwDatagrams if any such packets met this 
     * (discretionary) discard criterion. */
    static final String ipOutDiscardsOID = "1.3.6.1.2.1.4.11.";
    /** The number of IP datagrams discarded because no route could be found to transmit them to their destination. Note that this counter includes any 
     * packets counted in ipForwDatagrams which meet this `no-route' criterion. Note that this includes any datagrams which a host cannot route because 
     * all of its default gateways are down. */
    static final String ipOutNoRoutesOID = "1.3.6.1.2.1.4.12.";
    /** The maximum number of seconds which received fragments are held while they are awaiting reassembly at this entity. */
    static final String ipReasmTimeoutOID = "1.3.6.1.2.1.4.13.";
    /** The number of IP fragments received which needed to be reassembled at this entity. */
    static final String ipReasmReqdsOID = "1.3.6.1.2.1.4.14.";
    /** The number of IP datagrams successfully re-assembled.*/
    static final String ipReasmOKsOID = "1.3.6.1.2.1.4.15.";
    /** The number of failures detected by the IP re-assembly algorithm (for whatever reason: timed out, errors, etc). Note that this is not necessarily a 
     * count of discarded IP fragments since some algorithms (notably the algorithm in RFC 815) can lose track of the number of fragments by combining 
     * them as they are received.*/
    static final String ipReasmFailsOID = "1.3.6.1.2.1.4.16.";
    /** The number of IP datagrams that have been successfully fragmented at this entity. */
    static final String ipFragOKsOID = "1.3.6.1.2.1.4.17.";
    /** The number of IP datagrams that have been discarded because they needed to be fragmented at this entity but could not be, e.g., because their 
     * Don't Fragment flag was set. */
    static final String ipFragFailsOID = "1.3.6.1.2.1.4.18.";
    /** The number of IP datagram fragments that have been generated as a result of fragmentation at this entity. */
    static final String ipFragCreatesOID = "1.3.6.1.2.1.4.19.";
    /** The number of routing entries which were chosen to be discarded even though they are valid. One possible reason for discarding such an entry 
     * could be to free-up buffer space for other routing entries.*/
    static final String ipRoutingDiscardsOID = "1.3.6.1.2.1.4.23.";

    /** Flags */
    boolean ipForwardingFlag = true;
    boolean ipDefaultTTLFlag = true;
    boolean ipInReceivesFlag = true;
    boolean ipInHdrErrorsFlag = true;
    boolean ipInAddrErrorsFlag = true;
    boolean ipForwDatagramsFlag = true;
    boolean ipInUnknownProtosFlag = true;
    boolean ipInDiscardsFlag = true;
    boolean ipInDeliversFlag = true;
    boolean ipOutRequestsFlag = true;
    boolean ipOutDiscardsFlag = true;
    boolean ipOutNoRoutesFlag = true;
    boolean ipReasmTimeoutFlag = true;
    boolean ipReasmReqdsFlag = true;
    boolean ipReasmOKsFlag = true;
    boolean ipReasmFailsFlag = true;
    boolean ipFragOKsFlag = true;
    boolean ipFragFailsFlag = true;
    boolean ipFragCreatesFlag = true;
    boolean ipRoutingDiscardsFlag = true;

    /** See SNMPFactory for details about defaultOptions....like -A, -a, -v... */
    public SNMPIP(String defaultOptions, SNMPFactory snmp, String snmpAddress, String farmName, String clusterName,
            String moduleName) {
        this.snmp = snmp;
        this.defaultOptions = defaultOptions.trim();
        this.snmpAddress = snmpAddress.trim();
        this.farmName = farmName;
        this.clusterName = clusterName;
        this.moduleName = moduleName;
    }

    public void setFlags(boolean ipForwardingFlag, boolean ipDefaultTTLFlag, boolean ipInReceivesFlag,
            boolean ipInHdrErrorsFlag, boolean ipInAddrErrorsFlag, boolean ipForwDatagramsFlag,
            boolean ipInUnknownProtosFlag, boolean ipInDiscardsFlag, boolean ipInDeliversFlag,
            boolean ipOutRequestsFlag, boolean ipOutDiscardsFlag, boolean ipOutNoRoutesFlag,
            boolean ipReasmTimeoutFlag, boolean ipReasmReqdsFlag, boolean ipReasmOKsFlag, boolean ipReasmFailsFlag,
            boolean ipFragOKsFlag, boolean ipFragFailsFlag, boolean ipFragCreatesFlag, boolean ipRoutingDiscardsFlag) {

        this.ipForwardingFlag = ipForwardingFlag;
        this.ipDefaultTTLFlag = ipDefaultTTLFlag;
        this.ipInReceivesFlag = ipInReceivesFlag;
        this.ipInHdrErrorsFlag = ipInHdrErrorsFlag;
        this.ipInAddrErrorsFlag = ipInAddrErrorsFlag;
        this.ipForwDatagramsFlag = ipForwDatagramsFlag;
        this.ipInUnknownProtosFlag = ipInUnknownProtosFlag;
        this.ipInDiscardsFlag = ipInDiscardsFlag;
        this.ipInDeliversFlag = ipInDeliversFlag;
        this.ipOutRequestsFlag = ipOutRequestsFlag;
        this.ipOutDiscardsFlag = ipOutDiscardsFlag;
        this.ipOutNoRoutesFlag = ipOutNoRoutesFlag;
        this.ipReasmTimeoutFlag = ipReasmTimeoutFlag;
        this.ipReasmReqdsFlag = ipReasmReqdsFlag;
        this.ipReasmOKsFlag = ipReasmOKsFlag;
        this.ipReasmFailsFlag = ipReasmFailsFlag;
        this.ipFragOKsFlag = ipFragOKsFlag;
        this.ipFragFailsFlag = ipFragFailsFlag;
        this.ipFragCreatesFlag = ipFragCreatesFlag;
        this.ipRoutingDiscardsFlag = ipRoutingDiscardsFlag;
    }

    public void getResults(Vector res) {

        PDU[] pdu = snmp.run(defaultOptions + " -p GETBULK -Ow " + snmpAddress + " 1.3.6.1.2.1.4", null, null);
        if ((pdu == null) || (pdu.length == 0)) {
            return; // no result
        }
        eResult result = null;
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
                    if (oid.startsWith(ipOutRequestsOID) && ipOutRequestsFlag) {
                        result.addSet("IpOutRequests", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ipOutDiscardsOID) && ipOutDiscardsFlag) {
                        result.addSet("ipOutDiscards", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ipOutNoRoutesOID) && ipOutNoRoutesFlag) {
                        result.addSet("IpOutNoRoutes", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ipReasmTimeoutOID) && ipReasmTimeoutFlag) {
                        result.addSet("IpReasmTimeout", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ipReasmReqdsOID) && ipReasmReqdsFlag) {
                        result.addSet("IpReasmReqds", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ipReasmOKsOID) && ipReasmOKsFlag) {
                        result.addSet("IpReasmOKs", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ipReasmFailsOID) && ipReasmFailsFlag) {
                        result.addSet("IpReasmFails", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ipFragOKsOID) && ipFragOKsFlag) {
                        result.addSet("IpFragOKs", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ipFragFailsOID) && ipFragFailsFlag) {
                        result.addSet("IpFragFails", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ipFragCreatesOID) && ipFragCreatesFlag) {
                        result.addSet("IpFragCreates", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ipForwardingOID) && ipForwardingFlag) {
                        String cap = "Unknown";
                        if (param.equals("1")) {
                            cap = "Forwarding";
                        } else if (param.equals("2")) {
                            cap = "Not-forwarding";
                        }
                        result.addSet("IpForwarding", cap);
                    } else if (oid.startsWith(ipDefaultTTLOID) && ipDefaultTTLFlag) {
                        result.addSet("IpDefaultTTL", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ipInReceivesOID) && ipInReceivesFlag) {
                        result.addSet("IpInReceives", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ipInHdrErrorsOID) && ipInHdrErrorsFlag) {
                        result.addSet("IpInHdrErrors", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ipInAddrErrorsOID) && ipInAddrErrorsFlag) {
                        result.addSet("IpInAddrErrors", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ipForwDatagramsOID) && ipForwDatagramsFlag) {
                        result.addSet("IpForwDatagrams", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ipInUnknownProtosOID) && ipInUnknownProtosFlag) {
                        result.addSet("IpInUnknownProtos", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ipInDiscardsOID) && ipInDiscardsFlag) {
                        result.addSet("IpInDiscards", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ipInDeliversOID) && ipInDeliversFlag) {
                        result.addSet("IpInDelivers", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ipRoutingDiscardsOID) && ipRoutingDiscardsFlag) {
                        result.addSet("IpRoutingDiscards", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                    logger.warning(t.getLocalizedMessage());
                }
            }
        }
        if ((result != null) && (result.param_name != null) && (result.param_name.length != 0)) {
            res.add(result);
        }
    }

    public boolean setIpDefaultTTL(int ttl) {

        PDU pdu[] = snmp.run(defaultOptions + " -p SET " + snmpAddress + " " + ipDefaultTTLOID + "0={i}" + ttl, null,
                null);
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

    /** For testing */
    public static void main(String args[]) {

        //		logger.setLevel(Level.FINEST);
        SNMPFactory factory = new SNMPFactory();
        SNMPIP route = new SNMPIP("-c private -v 2c", factory, "udp:141.85.99.136/161", "RB", "SNMPIP", "SNMPModule");
        Vector v = new Vector();
        route.getResults(v);
        for (int i = 0; i < v.size(); i++) {
            System.out.println(v.get(i));
        }
        boolean ret = route.setIpDefaultTTL(128);
    }

} // end of class SNMPIP

