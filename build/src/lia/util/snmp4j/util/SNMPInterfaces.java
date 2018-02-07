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
import org.snmp4j.smi.Gauge32;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.VariableBinding;

/**
 * Class used to gather informations for Network Interfaces through the use of SNMP.
 */
public class SNMPInterfaces {

    private static final Logger logger = Logger.getLogger(SNMPInterfaces.class.getName());

    private final SNMPFactory snmp;
    private final String defaultOptions;
    private final String snmpAddress;
    private final String farmName;
    private final String clusterName;
    private final String moduleName;

    private final Hashtable ifSupport = new Hashtable();

    /** A textual string containing information about the interface. This string should include the name of the manufacturer, the product name and the 
     * version of the interface hardware/software.*/
    static final String ifDescrOID = "1.3.6.1.2.1.2.2.1.2.";
    /** The type of interface. Additional values for ifType are assigned by the Internet Assigned Numbers Authority (IANA), through updating the 
     * syntax of the IANAifType textual convention. */
    static final String ifTypeOID = "1.3.6.1.2.1.2.2.1.3.";
    /** The size of the largest packet which can be sent/received on the interface, specified in octets. For interfaces that are used for transmitting network 
     * datagrams, this is the size of the largest network datagram that can be sent on the interface. */
    static final String ifMtuOID = "1.3.6.1.2.1.2.2.1.4.";
    /** An estimate of the interface's current bandwidth in bits per second. */
    static final String ifSpeedOID = "1.3.6.1.2.1.2.2.1.5.";
    /** The interface's address at its protocol sub-layer. For example, for an 802.x interface, this object normally contains a MAC address. The interface's 
     * media-specific MIB must define the bit and byte ordering and the format of the value of this object. For interfaces which do not have such an 
     * address (e.g., a serial line), this object should contain an octet string of zero length. */
    static final String ifPhysAddressOID = "1.3.6.1.2.1.2.2.1.6.";
    /** The desired state of the interface. */
    static final String ifAdminStatusOID = "1.3.6.1.2.1.2.2.1.7.";
    /** The current operational state of the interface. */
    static final String ifOperStatusOID = "1.3.6.1.2.1.2.2.1.8.";
    /** The value of sysUpTime at the time the interface entered its current operational state. If the current state was entered prior to the last 
     * re-initialization of the local network management subsystem, then this object contains a zero value. */
    static final String ifLastChangeOID = "1.3.6.1.2.1.2.2.1.9.";
    /** The total number of octets received on the interface, including framing characters. Discontinuities in the value of this counter can occur at 
     * re-initialization of the management system, and at other times as indicated by the value of ifCounterDiscontinuityTime. */
    static final String ifInOctetsOID = "1.3.6.1.2.1.2.2.1.10.";
    /** The number of packets, delivered by this sub-layer to a higher (sub-)layer, which were not addressed to a multicast or broadcast address at this 
     * sub-layer. Discontinuities in the value of this counter can occur at re-initialization of the management system, and at other times as indicated by 
     * the value of ifCounterDiscontinuityTime. */
    static final String ifInUcastPktsOID = "1.3.6.1.2.1.2.2.1.11.";
    /** The number of inbound packets which were chosen to be discarded even though no errors had been detected to prevent their being deliverable 
     * to a higher-layer protocol. One possible reason for discarding such a packet could be to free up buffer space. Discontinuities in the value of this 
     * counter can occur at re-initialization of the management system, and at other times as indicated by the value of ifCounterDiscontinuityTime. */
    static final String ifInDiscardsOID = "1.3.6.1.2.1.2.2.1.13.";
    /** For packet-oriented interfaces, the number of inbound packets that contained errors preventing them from being deliverable to a higher-layer 
     * protocol. For character-oriented or fixed-length interfaces, the number of inbound transmission units that contained errors preventing them from 
     * being deliverable to a higher-layer protocol. Discontinuities in the value of this counter can occur at re-initialization of the management system, 
     * and at other times as indicated by the value of ifCounterDiscontinuityTime. */
    static final String ifInErrorsOID = "1.3.6.1.2.1.2.2.1.14.";
    /** For packet-oriented interfaces, the number of packets received via the interface which were discarded because of an unknown or unsupported 
     * protocol. For character-oriented or fixed-length interfaces that support protocol multiplexing the number of transmission units received via the 
     * interface which were discarded because of an unknown or unsupported protocol. For any interface that does not support protocol multiplexing, 
     * this counter will always be 0. Discontinuities in the value of this counter can occur at re-initialization of the management system, and at other 
     * times as indicated by the value of ifCounterDiscontinuityTime. */
    static final String ifInUnknownProtosOID = "1.3.6.1.2.1.2.2.1.15.";
    /** The total number of octets transmitted out of the interface, including framing characters. Discontinuities in the value of this counter can occur 
     * at re-initialization of the management system, and at other times as indicated by the value of ifCounterDiscontinuityTime. */
    static final String ifOutOctetsOID = "1.3.6.1.2.1.2.2.1.16.";
    /** The total number of packets that higher-level protocols requested be transmitted, and which were not addressed to a multicast or broadcast 
     * address at this sub-layer, including those that were discarded or not sent. Discontinuities in the value of this counter can occur at re-initialization 
     * of the management system, and at other times as indicated by the value of ifCounterDiscontinuityTime. */
    static final String ifOutUcastPktsOID = "1.3.6.1.2.1.2.2.1.17.";
    /** The number of outbound packets which were chosen to be discarded even though no errors had been detected to prevent their being transmitted. 
     * One possible reason for discarding such a packet could be to free up buffer space. Discontinuities in the value of this counter can occur at 
     * re-initialization of the management system, and at other times as indicated by the value of ifCounterDiscontinuityTime. */
    static final String ifOutDiscardsOID = "1.3.6.1.2.1.2.2.1.19.";
    /** For packet-oriented interfaces, the number of outbound packets that could not be transmitted because of errors. For character-oriented or 
     * fixed-length interfaces, the number of outbound transmission units that could not be transmitted because of errors. Discontinuities in the value 
     * of this counter can occur at re-initialization of the management system, and at other times as indicated by the value of ifCounterDiscontinuityTime. */
    static final String ifOutErrorsOID = "1.3.6.1.2.1.2.2.1.20.";
    /** The IP address to which this entry's addressing information pertains. */
    static final String ipAdEntAddrOID = "1.3.6.1.2.1.4.20.1.1.";
    /** The index value which uniquely identifies the interface to which this entry is applicable. The interface identified by a particular value of this 
     * index is the same interface as identified by the same value of ifIndex. */
    static final String ipAdEntIfIndexOID = "1.3.6.1.2.1.4.20.1.2.";
    /** The subnet mask associated with the IP address of this entry. The value of the mask is an IP address with all the network bits set to 1 and all 
     * the hosts bits set to 0. */
    static final String ipAdEntNetMaskOID = "1.3.6.1.2.1.4.20.1.3.";
    /** The size of the largest IP datagram which this entity can re-assemble from incoming IP fragmented datagrams received on this interface.*/
    static final String ipAdEntReasmMaxSizeOID = "1.3.6.1.2.1.4.20.1.5.";

    /** Flags */
    boolean ifTypeFlag = true;
    boolean ifMtuFlag = true;
    boolean ifSpeedFlag = true;
    boolean ifPhysAddressFlag = true;
    boolean ifAdminStatusFlag = true;
    boolean ifOperStatusFlag = true;
    boolean ifLastChangeFlag = true;
    boolean ifInOctetsFlag = true;
    boolean ifInUcastPktsFlag = true;
    boolean ifInDiscardsFlag = true;
    boolean ifInErrorsFlag = true;
    boolean ifInUnknownProtosFlag = true;
    boolean ifOutOctetsFlag = true;
    boolean ifOutUcastPktsFlag = true;
    boolean ifOutDiscardsFlag = true;
    boolean ifOutErrorsFlag = true;
    boolean ipAdEntAddrFlag = true;
    boolean ipAdEntIfIndexFlag = true;
    boolean ipAdEntNetMaskFlag = true;
    boolean ipAdEntReasmMaxSizeFlag = true;

    /** See SNMPFactory for details about defaultOptions....like -A, -a, -v... */
    public SNMPInterfaces(String defaultOptions, SNMPFactory snmp, String snmpAddress, String farmName,
            String clusterName, String moduleName) {
        this.snmp = snmp;
        this.defaultOptions = defaultOptions.trim();
        this.snmpAddress = snmpAddress.trim();
        this.farmName = farmName;
        this.clusterName = clusterName;
        this.moduleName = moduleName;
    }

    public void setFlags(boolean ifTypeFlag, boolean ifMtuFlag, boolean ifSpeedFlag, boolean ifPhysAddressFlag,
            boolean ifAdminStatusFlag, boolean ifOperStatusFlag, boolean ifLastChangeFlag, boolean ifInOctetsFlag,
            boolean ifInUcastPktsFlag, boolean ifInDiscardsFlag, boolean ifInErrorsFlag, boolean ifInUnknownProtosFlag,
            boolean ifOutOctetsFlag, boolean ifOutUcastPktsFlag, boolean ifOutDiscardsFlag, boolean ifOutErrorsFlag,
            boolean ipAdEntAddrFlag, boolean ipAdEntIfIndexFlag, boolean ipAdEntNetMaskFlag,
            boolean ipAdEntReasmMaxSizeFlag) {

        this.ifTypeFlag = ifTypeFlag;
        this.ifMtuFlag = ifMtuFlag;
        this.ifSpeedFlag = ifSpeedFlag;
        this.ifPhysAddressFlag = ifPhysAddressFlag;
        this.ifAdminStatusFlag = ifAdminStatusFlag;
        this.ifOperStatusFlag = ifOperStatusFlag;
        this.ifLastChangeFlag = ifLastChangeFlag;
        this.ifInOctetsFlag = ifInOctetsFlag;
        this.ifInUcastPktsFlag = ifInUcastPktsFlag;
        this.ifInDiscardsFlag = ifInDiscardsFlag;
        this.ifInErrorsFlag = ifInErrorsFlag;
        this.ifInUnknownProtosFlag = ifInUnknownProtosFlag;
        this.ifOutOctetsFlag = ifOutOctetsFlag;
        this.ifOutUcastPktsFlag = ifOutUcastPktsFlag;
        this.ifOutDiscardsFlag = ifOutDiscardsFlag;
        this.ifOutErrorsFlag = ifOutErrorsFlag;
        this.ipAdEntAddrFlag = ipAdEntAddrFlag;
        this.ipAdEntIfIndexFlag = ipAdEntIfIndexFlag;
        this.ipAdEntNetMaskFlag = ipAdEntNetMaskFlag;
        this.ipAdEntReasmMaxSizeFlag = ipAdEntReasmMaxSizeFlag;
    }

    public void getResults(Vector res) {

        PDU[] pdu = snmp.run(defaultOptions + " -p GETBULK -Ow " + snmpAddress + " 1.3.6.1.2.1.2", null, null);
        if ((pdu == null) || (pdu.length == 0)) {
            return; // no result
        }
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
                    if (oid.startsWith(ifDescrOID)) {
                        String id = oid.substring(ifDescrOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.NodeName = param;
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.NodeName = param;
                        }
                    } else if (oid.startsWith(ifTypeOID) && ifTypeFlag) {
                        String id = oid.substring(ifTypeOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IfType", getIfType(param));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IfType", getIfType(param));
                        }
                    } else if (oid.startsWith(ifMtuOID) && ifMtuFlag) {
                        String id = oid.substring(ifMtuOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IfMtu", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IfMtu", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ifSpeedOID) && ifSpeedFlag) {
                        String id = oid.substring(ifSpeedOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IfSpeed", Long.valueOf(((Gauge32) vb.getVariable()).getValue()));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IfSpeed", Long.valueOf(((Gauge32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ifPhysAddressOID) && ifPhysAddressFlag) {
                        String id = oid.substring(ifPhysAddressOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IfPhysAddress", param);
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IfPhysAddress", param);
                        }
                    } else if (oid.startsWith(ifAdminStatusOID) && ifAdminStatusFlag) {
                        String status = "Unknown";
                        if (param.equals("1")) {
                            status = "Up";
                        } else if (param.equals("2")) {
                            status = "Down";
                        } else if (param.equals("3")) {
                            status = "Testing";
                        }
                        String id = oid.substring(ifAdminStatusOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IfAdminStatus", status);
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IfAdminStatus", status);
                        }
                    } else if (oid.startsWith(ifOperStatusOID) && ifOperStatusFlag) {
                        String id = oid.substring(ifOperStatusOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IfOperStatus", getIfOperStatus(param));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IfOperStatus", getIfOperStatus(param));
                        }
                    } else if (oid.startsWith(ifLastChangeOID) && ifLastChangeFlag) {
                        String id = oid.substring(ifLastChangeOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IfLastChange", param);
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IfLastChange", param);
                        }
                    } else if (oid.startsWith(ifInOctetsOID) && ifInOctetsFlag) {
                        String id = oid.substring(ifInOctetsOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IfInOctets", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IfInOctets", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ifInUcastPktsOID) && ifInUcastPktsFlag) {
                        String id = oid.substring(ifInUcastPktsOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IfInUcastPkts", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IfInUcastPkts", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ifInDiscardsOID) && ifInDiscardsFlag) {
                        String id = oid.substring(ifInDiscardsOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IfInDiscards", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IfInDiscards", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ifInErrorsOID) && ifInErrorsFlag) {
                        String id = oid.substring(ifInErrorsOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IfInErrors", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IfInErrors", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ifInUnknownProtosOID) && ifInUnknownProtosFlag) {
                        String id = oid.substring(ifInUnknownProtosOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IfInUnknownProtos", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IfInUnknownProtos", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ifOutOctetsOID) && ifOutOctetsFlag) {
                        String id = oid.substring(ifOutOctetsOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IfOutOctets", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IfOutOctets", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ifOutUcastPktsOID) && ifOutUcastPktsFlag) {
                        String id = oid.substring(ifOutUcastPktsOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IfOutUcastPkts", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IfOutUcastPkts", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ifOutDiscardsOID) && ifOutDiscardsFlag) {
                        String id = oid.substring(ifOutDiscardsOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IfOutDiscards", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IfOutDiscards", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ifOutErrorsOID) && ifOutErrorsFlag) {
                        String id = oid.substring(ifOutErrorsOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IfOutErrors", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IfOutErrors", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        }
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                    logger.warning(t.getLocalizedMessage());
                }
            }
        }
        Hashtable h = new Hashtable(); // for ip and stuff
        pdu = snmp.run(defaultOptions + " -p GETBULK -Ow " + snmpAddress + " 1.3.6.1.2.1.4.20", null, null);
        if ((pdu != null) && (pdu.length != 0)) {
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
                        if ((param == null) || (param.length() == 0)) {
                            continue;
                        }
                        if (oid.startsWith(ipAdEntAddrOID) && ipAdEntAddrFlag) {
                            String id = oid.substring(ipAdEntAddrOID.length());
                            if (!h.containsKey(id)) {
                                Hashtable h1 = new Hashtable();
                                h.put(id, h1);
                                h1.put("IpAdEntAddr", param);
                            } else {
                                Hashtable h1 = (Hashtable) h.get(id);
                                h1.put("IpAdEntAddr", param);
                            }
                        } else if (oid.startsWith(ipAdEntIfIndexOID) && ipAdEntIfIndexFlag) {
                            String id = oid.substring(ipAdEntIfIndexOID.length());
                            if (!h.containsKey(id)) {
                                Hashtable h1 = new Hashtable();
                                h.put(id, h1);
                                h1.put("IpAdEntIfIndex", param);
                            } else {
                                Hashtable h1 = (Hashtable) h.get(id);
                                h1.put("IpAdEntIfIndex", param);
                            }
                        } else if (oid.startsWith(ipAdEntNetMaskOID) && ipAdEntNetMaskFlag) {
                            String id = oid.substring(ipAdEntNetMaskOID.length());
                            if (!h.containsKey(id)) {
                                Hashtable h1 = new Hashtable();
                                h.put(id, h1);
                                h1.put("IpAdEntNetMask", param);
                            } else {
                                Hashtable h1 = (Hashtable) h.get(id);
                                h1.put("IpAdEntNetMask", param);
                            }
                        } else if (oid.startsWith(ipAdEntReasmMaxSizeOID) && ipAdEntReasmMaxSizeFlag) {
                            String id = oid.substring(ipAdEntReasmMaxSizeOID.length());
                            if (!h.containsKey(id)) {
                                Hashtable h1 = new Hashtable();
                                h.put(id, h1);
                                h1.put("IpAdEntReasmMaxSize", param);
                            } else {
                                Hashtable h1 = (Hashtable) h.get(id);
                                h1.put("IpAdEntReasmMaxSize", param);
                            }
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                        logger.warning(t.getLocalizedMessage());
                    }
                }
            }
        }
        for (Enumeration en = h.keys(); en.hasMoreElements();) {
            Hashtable h1 = (Hashtable) h.get(en.nextElement());
            if (h1 == null) {
                continue;
            }
            if (h1.containsKey("IpAdEntIfIndex")) {
                String index = (String) h1.get("IpAdEntIfIndex");
                if (ifSupport.containsKey(index)) {
                    eResult result = (eResult) ifSupport.get(index);
                    for (Enumeration en1 = h1.keys(); en1.hasMoreElements();) {
                        String key = (String) en1.nextElement();
                        if (key == null) {
                            continue;
                        }
                        String value = (String) h1.get(key);
                        if (value == null) {
                            continue;
                        }
                        result.addSet(key, value);
                    }
                }
            }
        }
        for (Enumeration en = ifSupport.keys(); en.hasMoreElements();) {
            String id = (String) en.nextElement();
            if (id == null) {
                continue;
            }
            eResult result = (eResult) ifSupport.get(id);
            if (result == null) {
                continue;
            }
            result.addSet("IfIndex", id);
            if (result.NodeName != null) {
                res.add(result);
            }
        }
    }

    /** status: 1=up, 2=down, 3=testing */
    public boolean setIfAdminStatus(int ifIndex, int status) {

        if ((status < 1) || (status > 3)) {
            return false;
        }
        PDU pdu[] = snmp.run(defaultOptions + " -p SET " + snmpAddress + " " + ifAdminStatusOID + "." + ifIndex
                + "={i}" + status, null, null);
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

    static final String types[][] = new String[][] { { " 1", "other" }, { "2", "regular1822" }, { "3", "hdh1822" },
            { "4", "ddnX25" }, { "5", "rfc877x25" }, { "6", "ethernetCsmacd" }, { "7", "iso88023Csmacd" },
            { "8", "iso88024TokenBus" }, { "9", "iso88025TokenRing" }, { "10", "iso88026Man" }, { "11", "starLan" },
            { "12", "proteon10Mbit" }, { "13", "proteon80Mbit" }, { "14", "hyperchannel" }, { "15", "fddi" },
            { "16", "lapb" }, { "17", "sdlc" }, { "18", "ds1" }, { "19", "e1" }, { "20", "basicISDN" },
            { "21", "primaryISDN" }, { "22", "propPointToPointSerial" }, { "23", "ppp" }, { "24", "softwareLoopback" },
            { "25", "eon" }, { "26", "ethernet3Mbit" }, { "27", "nsip" }, { "28", "slip" }, { "29", "ultra" },
            { "50", "sonetPath" }, { "51", "sonetVT" }, { "52", "smdsIcip" }, { "53", "propVirtual" },
            { "54", "propMultiplexor" }, { "55", "ieee80212" }, { "56", "fibreChannel" }, { "57", "hippiInterface" },
            { "58", "frameRelayInterconnect" }, { "59", "aflane8023" }, { "60", "aflane8025" }, { "61", "cctEmul" },
            { "62", "fastEther" }, { "63", "isdn" }, { "64", "v11" }, { "65", "v36" }, { "66", "g703at64k" },
            { "67", "g703at2mb" }, { "68", "qllc" }, { "69", "fastEtherFX" }, { "70", "channel" },
            { "71", "ieee80211" }, { "72", "ibm370parChan" }, { "73", "escon" }, { "74", "dlsw" }, { "75", "isdns" },
            { "76", "isdnu" }, { "77", "lapd" }, { "78", "ipSwitch" }, { "79", "rsrb" }, { "80", "atmLogical" },
            { "81", "ds0" }, { "82", "ds0Bundle" }, { "83", "bsc" }, { "84", "async" }, { "85", "cnr" },
            { "86", "iso88025Dtr" }, { "87", "eplrs" }, { "88", "arap" }, { "89", "propCnls" }, { "90", "hostPad" },
            { "91", "termPad" }, { "92", "frameRelayMPI" }, { "93", "x213" }, { "94", "adsl" }, { "95", "radsl" },
            { "96", "sdsl" }, { "97", "vdsl" }, { "98", "iso88025CRFPInt" }, { "99", "myrinet" }, { "120", "v37" },
            { "121", "x25mlp" }, { "122", "x25huntGroup" }, { "123", "trasnpHdlc" }, { "124", "interleave" },
            { "125", "fast" }, { "126", "ip" }, { "127", "docsCableMaclayer" }, { "128", "docsCableDownstream" },
            { "129", "docsCableUpstream" }, { "130", "a12MppSwitch" }, { "131", "tunnel" }, { "132", "coffee" },
            { "133", "ces" }, { "134", "atmSubInterface" }, { "135", "l2vlan" }, { "136", "l3ipvlan" },
            { "137", "l3ipxvlan" }, { "138", "digitalPowerline" }, { "139", "mediaMailOverIp" }, { "140", "dtm" },
            { "141", "dcn" }, { "142", "ipForward" }, { "143", "msdsl" }, { "144", "ieee1394" }, { "145", "if-gsn" },
            { "146", "dvbRccMacLayer" }, { "147", "dvbRccDownstream" }, { "148", "dvbRccUpstream" },
            { "149", "atmVirtual" }, { "150", "mplsTunnel" }, { "151", "srp" }, { "152", "voiceOverAtm" },
            { "153", "voiceOverFrameRelay" }, { "154", "idsl" }, { "155", "compositeLink" }, { "156", "ss7SigLink" },
            { "157", "propWirelessP2P" }, { "158", "frForward" }, { "159", "rfc1483" }, { "160", "usb" },
            { "161", "ieee8023adLag" }, { "162", "bgppolicyaccounting" }, { "163", "frf16MfrBundle" },
            { "164", "h323Gatekeeper" }, { "165", "h323Proxy" }, { "166", "mpls" }, { "167", "mfSigLink" },
            { "168", "hdsl2" }, { "169", "shdsl" }, { "170", "ds1FDL" }, { "171", "pos" }, { "172", "dvbAsiIn" },
            { "196", "opticalTransport" }, { "197", "propAtm" }, { "198", "voiceOverCable" } };

    private String getIfType(String type) {

        if (type == null) {
            return "Null";
        }
        for (String[] type2 : types) {
            if (type2[0].equals(type)) {
                return type2[1];
            }
        }
        return type;
    }

    static final String operStatus[][] = new String[][] { { "1", "Up" }, { "2", "Down" }, { "3", "Testing" },
            { "4", "Unknown" }, { "5", "Dormant" }, { "6", "NotPresent" }, { "7", "LowerLayerDown" } };

    private String getIfOperStatus(String id) {

        if (id == null) {
            return "Null";
        }
        for (String[] operStatu : operStatus) {
            if (operStatu[0].equals(id)) {
                return operStatu[1];
            }
        }
        return "Unknown";
    }

    /** For testing */
    public static void main(String args[]) {

        //		logger.setLevel(Level.FINEST);
        SNMPFactory factory = new SNMPFactory();
        SNMPInterfaces route = new SNMPInterfaces("-c private -v 2c", factory, "udp:141.85.99.136/161", "RB",
                "SNMPInterfaces", "SNMPModule");
        Vector v = new Vector();
        route.getResults(v);
        for (int i = 0; i < v.size(); i++) {
            System.out.println(v.get(i));
        }
        route.setIfAdminStatus(1, 2);
    }

} // end of class SNMPInterfaces

