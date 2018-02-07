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
 * Class used to gather informations for OSPF through the use of SNMP.
 */
public class SNMPOSPF {

    private static final Logger logger = Logger.getLogger(SNMPOSPF.class.getName());

    private final SNMPFactory snmp;
    private final String defaultOptions;
    private final String snmpAddress;
    private final String farmName;
    private final String clusterName;
    private final String moduleName;

    static final String node = "SNMPOSPF";

    /** A 32-bit integer uniquely identifying the router in the Autonomous System. By convention, to ensure uniqueness, this should default to the value of 
     * one of the router's IP interface addresses. */
    static final String ospfRouterIdOID = "1.3.6.1.2.1.14.1.1.";
    /** The administrative status of OSPF in the router. The value 'enabled' denotes that the OSPF Process is active on at least one interface; 
     * 'disabled' disables it on all interfaces. */
    static final String ospfAdminStatOID = "1.3.6.1.2.1.14.1.2.";
    /** The current version number of the OSPF protocol is 2. */
    static final String ospfVersionNumberOID = "1.3.6.1.2.1.14.1.3.";
    /** A flag to note whether this router is an area border router. */
    static final String ospfAreaBdrRtrStatusOID = "1.3.6.1.2.1.14.1.4.";
    /** A flag to note whether this router is an Autonomous System border router. */
    static final String ospfASBdrRtrStatusOID = "1.3.6.1.2.1.14.1.5.";
    /** The number of external (LS type 5) link-state advertisements in the link-state database. */
    static final String ospfExternLSACountOID = "1.3.6.1.2.1.14.1.6.";
    /** The 32-bit unsigned sum of the LS checksums of the external link-state advertisements contained in the link-state database. This sum can be 
     * used to determine if there has been a change in a router's link state database, and to compare the link-state database of two routers. */
    static final String ospfExternLSACksumSumOID = "1.3.6.1.2.1.14.1.7.";
    /** The router's support for type-of-service routing. */
    static final String ospfTOSSupportOID = "1.3.6.1.2.1.14.1.8.";
    /** The number of new link-state advertisements that have been originated. This number is incremented each time the router originates a new LSA. */
    static final String ospfOriginateNewLSAsOID = "1.3.6.1.2.1.14.1.9.";
    /** The number of link-state advertisements received determined to be new instantiations. This number does not include newer instantiations of 
     * self-originated link-state advertisements. */
    static final String ospfRxNewLSAsOID = "1.3.6.1.2.1.14.1.10.";
    /** The maximum number of non-default AS-external-LSAs entries that can be stored in the link-state database. If the value is -1, then there is no limit.
     * When the number of non-default AS-external-LSAs in a router's link-state database reaches ospfExtLsdbLimit, the router enters Overflow-State. 
     * The router never holds more than ospfExtLsdbLimit non-default AS-external-LSAs in its database. OspfExtLsdbLimit MUST be set identically in all 
     * routers attached to the OSPF backbone and/or any regular OSPF area. (i.e., OSPF stub areas and NSSAs are excluded). */
    static final String ospfExtLsdbLimitOID = "1.3.6.1.2.1.14.1.11.";
    /** A Bit Mask indicating whether the router is forwarding IP multicast (Class D) datagrams based on the algorithms defined in the Multicast 
     * Extensions to OSPF. Bit 0, if set, indicates that the router can forward IP multicast datagrams in the router's directly attached areas (called 
     * intra-area multicast routing). Bit 1, if set, indicates that the router can forward IP multicast datagrams between OSPF areas (called inter-area 
     * multicast routing). Bit 2, if set, indicates that the router can forward IP multicast datagrams between Autonomous Systems (called inter-AS 
     * multicast routing). Only certain combinations of bit settings are allowed, namely: 0 (no multicast forwarding is enabled), 1 (intra-area multicasting 
     * only), 3 (intra-area and inter-area multicasting), 5 (intra-area and inter-AS multicasting) and 7 (multicasting everywhere). By default, no multicast 
     * forwarding is enabled. */
    static final String ospfMulticastExtensionsOID = "1.3.6.1.2.1.14.1.12.";
    /** The number of seconds that, after entering OverflowState, a router will attempt to leave OverflowState. This allows the router to again originate 
     * non-default AS-external-LSAs. When set to 0, the router will not leave Overflow-State until restarted. */
    static final String ospfExitOverflowIntervalOID = "1.3.6.1.2.1.14.1.13";
    /** The router's support for demand routing. */
    static final String ospfDemandExtensionsOID = "1.3.6.1.2.1.14.1.14.";
    /** The authentication type specified for an area. Additional authentication types may be assigned locally on a per Area basis. */
    static final String ospfAuthTypeOID = "1.3.6.1.2.1.14.2.1.2.";
    /** The area's support for importing AS external link-state advertisements. */
    static final String ospfImportASExternOID = "1.3.6.1.2.1.14.2.1.3.";
    /** The number of times that the intra-area route table has been calculated using this area's link-state database. This is typically done using 
     * Dijkstra's algorithm. */
    static final String ospfSpfRunsOID = "1.3.6.1.2.1.14.2.1.4.";
    /** The total number of area border routers reachable within this area. This is initially zero, and is calculated in each SPF Pass. */
    static final String ospfAreaBdrRtrCountOID = "1.3.6.1.2.1.14.2.1.5.";
    /** The total number of Autonomous System border routers reachable within this area. This is initially zero, and is calculated in each SPF Pass. */
    static final String ospfASBdrRtrCountOID = "1.3.6.1.2.1.14.2.1.6.";
    /** The total number of link-state advertisements in this area's link-state database, excluding AS External LSA's. */
    static final String ospfAreaLSACountOID = "1.3.6.1.2.1.14.2.1.7.";
    /** The 32-bit unsigned sum of the link-state advertisements' LS checksums contained in this area's link-state database. This sum excludes external 
     * (LS type 5) link-state advertisements. The sum can be used to determine if there has been a change in a router's link state database, and to 
     * compare the link-state database of two routers. */
    static final String ospfAreaLSACksumSumOID = "1.3.6.1.2.1.14.2.1.8.";
    /** The variable ospfAreaSummary controls the import of summary LSAs into stub areas. It has no effect on other areas. If it is noAreaSummary, the 
     * router will neither originate nor propagate summary LSAs into the stub area. It will rely entirely on its default route. If it is sendAreaSummary, 
     * the router will both summarize and propagate summary LSAs. */
    static final String ospfAreaSummaryOID = "1.3.6.1.2.1.14.2.1.9.";
    /** This variable displays the status of the entry. Setting it to 'invalid' has the effect of rendering it inoperative. The internal effect (row removal) is 
     * implementation dependent. */
    static final String ospfAreaStatusOID = "1.3.6.1.2.1.14.2.1.10.";
    /** The 32 bit identifier for the Stub Area. On creation, this can be derived from the instance. */
    static final String ospfStubAreaIDOID = "1.3.6.1.2.1.14.3.1.1.";
    /** The Type of Service associated with the metric. On creation, this can be derived from the instance. */
    static final String ospfStubTOSOID = "1.3.6.1.2.1.14.3.1.2.";
    /** The metric value applied at the indicated type of service. By default, this equals the least metric at the type of service among the interfaces to 
     * other areas. */
    static final String ospfStubMetricOID = "1.3.6.1.2.1.14.3.1.3.";
    /** This variable displays the validity or invalidity of the entry. Setting it to 'invalid' has the effect of rendering it inoperative. The internal effect 
     * (row removal) is implementation dependent. */
    static final String ospfStubStatusOID = "1.3.6.1.2.1.14.3.1.4.";
    /** This variable displays the type of metric advertised as a default route. */
    static final String ospfStubMetricTypeOID = "1.3.6.1.2.1.14.3.1.5.";
    /** The 32 bit identifier of the Area from which the LSA was received. */
    static final String ospfLsdbAreaIdOID = "1.3.6.1.2.1.14.4.1.1.";
    /** The type of the link state advertisement. Each link state type has a separate advertisement format. */
    static final String ospfLsdbTypeOID = "1.3.6.1.2.1.14.4.1.2.";
    /** The Link State ID is an LS Type Specific field containing either a Router ID or an IP Address; it identifies the piece of the routing domain that is 
     * being described by the advertisement. */
    static final String ospfLsdbLSIDOID = "1.3.6.1.2.1.14.4.1.3.";
    /** The 32 bit number that uniquely identifies the originating router in the Autonomous System. */
    static final String ospfLsdbRouterIdOID = "1.3.6.1.2.1.14.4.1.4.";
    /** The sequence number field is a signed 32-bit integer. It is used to detect old and duplicate link state advertisements. The space of sequence 
     * numbers is linearly ordered. The larger the sequence number the more recent the advertisement. */
    static final String ospfLsdbSequenceOID = "1.3.6.1.2.1.14.4.1.5.";
    /** This field is the age of the link state advertisement in seconds. */
    static final String ospfLsdbAgeOID = "1.3.6.1.2.1.14.4.1.6.";
    /** This field is the checksum of the complete contents of the advertisement, excepting the age field. The age field is excepted so that an 
     * advertisement's age can be incremented without updating the checksum. The checksum used is the same that is used for ISO connectionless 
     * datagrams; it is commonly referred to as the Fletcher checksum. */
    static final String ospfLsdbChecksumOID = "1.3.6.1.2.1.14.4.1.7.";
    /** The Area the Address Range is to be found within. */
    static final String ospfAreaRangeAreaIDOID = "1.3.6.1.2.1.14.5.1.1.";
    /** The IP Address of the Net or Subnet indicated by the range. */
    static final String ospfAreaRangeNetOID = "1.3.6.1.2.1.14.5.1.2.";
    /** The Subnet Mask that pertains to the Net or Subnet. */
    static final String ospfAreaRangeMaskOID = "1.3.6.1.2.1.14.5.1.3.";
    /** This variable displays the validity or invalidity of the entry. Setting it to 'invalid' has the effect of rendering it inoperative. The internal effect 
     * (row removal) is implementation dependent. */
    static final String ospfAreaRangeStatusOID = "1.3.6.1.2.1.14.5.1.4.";
    /** The IP Address of the Host. */
    static final String ospfHostIpAddressOID = "1.3.6.1.2.1.14.6.1.1.";
    /** The Type of Service of the route being configured. */
    static final String ospfHostTOSOID = "1.3.6.1.2.1.14.6.1.2.";
    /** The Metric to be advertised. */
    static final String ospfHostMetricOID = "1.3.6.1.2.1.14.6.1.3.";
    /** This variable displays the validity or invalidity of the entry. Setting it to 'invalid' has the effect of rendering it inoperative. The internal effect 
     * (row removal) is implementation dependent. */
    static final String ospfHostStatusOID = "1.3.6.1.2.1.14.6.1.4.";
    /** The Area the Host Entry is to be found within. By default, the area that a subsuming OSPF interface is in, or 0.0.0.0 */
    static final String ospfHostAreaIDOID = "1.3.6.1.2.1.14.6.1.5.";
    /** The IP address of this OSPF interface. */
    static final String ospfIfIpAddressOID = "1.3.6.1.2.1.14.7.1.1.";
    /** A 32-bit integer uniquely identifying the area to which the interface connects. Area ID 0.0.0.0 is used for the OSPF backbone. */
    static final String ospfIfAreaIdOID = "1.3.6.1.2.1.14.7.1.3.";
    /** The OSPF interface type. By way of a default, this field may be intuited from the corresponding value of ifType. Broadcast LANs, such as Ethernet 
     * and IEEE 802.5, take the value 'broadcast', X.25, Frame Relay, and similar technologies take the value 'nbma', and links that are definitively point 
     * to point take the value 'pointToPoint'. */
    static final String ospfIfTypeOID = "1.3.6.1.2.1.14.7.1.4.";
    /** The OSPF interface's administrative status. The value 'enabled' denotes that neighbor relationships may be formed on the interface, and the 
     * interface will be advertised as an internal route to some area. The value 'disabled' denotes that the interface is external to OSPF. */
    static final String ospfIfAdminStatOID = "1.3.6.1.2.1.14.7.1.5.";
    /** The priority of this interface. Used in multi-access networks, this field is used in the designated router election algorithm. The value 0 signifies that 
     * the router is not eligible to become the designated router on this particular network. In the event of a tie in this value, routers will use their router id 
     * as a tie breaker. */
    static final String ospfIfRtrPriorityOID = "1.3.6.1.2.1.14.7.1.6.";
    /** The estimated number of seconds it takes to transmit a link- state update packet over this interface. */
    static final String ospfIfTransitDelayOID = "1.3.6.1.2.1.14.7.1.7.";
    /** The number of seconds between link-state advertisement retransmissions, for adjacencies belonging to this interface. This value is also used when 
     * retransmitting database description and link-state request packets. */
    static final String ospfIfRetransIntervalOID = "1.3.6.1.2.1.14.7.1.8.";
    /** The length of time, in seconds, between the Hello packets that the router sends on the interface. This value must be the same for all routers 
     * attached to a common network. */
    static final String ospfIfHelloIntervalOID = "1.3.6.1.2.1.14.7.1.9.";
    /** The number of seconds that a router's Hello packets have not been seen before it's neighbors declare the router down. This should be some multiple 
     * of the Hello interval. This value must be the same for all routers attached to a common network. */
    static final String ospfIfRtrDeadIntervalOID = "1.3.6.1.2.1.14.7.1.10.";
    /** The larger time interval, in seconds, between the Hello packets sent to an inactive non-broadcast multiaccess neighbor. */
    static final String ospfIfPollIntervalOID = "1.3.6.1.2.1.14.7.1.11.";
    /** The OSPF Interface State. */
    static final String ospfIfStateOID = "1.3.6.1.2.1.14.7.1.12.";
    /** The IP Address of the Designated Router. */
    static final String ospfIfDesignatedRouterOID = "1.3.6.1.2.1.14.7.1.13.";
    /** The IP Address of the Backup Designated Router. */
    static final String ospfIfBackupDesignatedRouterOID = "1.3.6.1.2.1.14.7.1.14.";
    /** The number of times this OSPF interface has changed its state, or an error has occurred. */
    static final String ospfIfEventsOID = "1.3.6.1.2.1.14.7.1.15.";
    /** This variable displays the status of the entry. Setting it to 'invalid' has the effect of rendering it inoperative. The internal effect (row removal) is 
     * implementation dependent. */
    static final String ospfIfStatusOID = "1.3.6.1.2.1.14.7.1.17.";

    /** Flags */
    boolean ospfRouterIdFlag = true;
    boolean ospfAdminStatFlag = true;
    boolean ospfVersionNumberFlag = true;
    boolean ospfAreaBdrRtrStatusFlag = true;
    boolean ospfASBdrRtrStatusFlag = true;
    boolean ospfExternLSACountFlag = true;
    boolean ospfExternLSACksumSumFlag = true;
    boolean ospfTOSSupportFlag = true;
    boolean ospfOriginateNewLSAsFlag = true;
    boolean ospfRxNewLSAsFlag = true;
    boolean ospfExtLsdbLimitFlag = true;
    boolean ospfMulticastExtensionsFlag = true;
    boolean ospfExitOverflowIntervalFlag = true;
    boolean ospfDemandExtensionsFlag = true;
    boolean ospfAreaTableFlag = true;
    boolean ospfStubAreaTableFlag = true;
    boolean ospfLSDBAreaTableFlag = true;
    boolean ospfRangeAreaTableFlag = true;
    boolean ospfHostTableFlag = true;
    boolean ospfIFTableFlag = true;

    /** See SNMPFactory for details about defaultOptions....like -A, -a, -v... */
    public SNMPOSPF(String defaultOptions, SNMPFactory snmp, String snmpAddress, String farmName, String clusterName,
            String moduleName) {
        this.snmp = snmp;
        this.defaultOptions = defaultOptions.trim();
        this.snmpAddress = snmpAddress.trim();
        this.farmName = farmName;
        this.clusterName = clusterName;
        this.moduleName = moduleName;
    }

    public boolean supportOSPF() {
        PDU pdu[] = snmp.run(defaultOptions + " -p GET " + snmpAddress + " 1.3.6.1.2.1.14.2", null, null);
        if ((pdu == null) || (pdu.length == 0)) {
            return false;
        }
        boolean support = false;
        for (PDU element : pdu) {
            if (element.getType() == PDU.REPORT) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINE, SNMPFactory.getReport(element));
                }
                continue;
            }
            for (int k = 0; k < element.size(); k++) {
                VariableBinding vb = element.get(k);
                if (!vb.isException()) {
                    support = true;
                    break;
                }
            }
            if (support) {
                break;
            }
        }
        return support;
    }

    public void getResults(Vector res) {

        PDU[] pdu = snmp.run(defaultOptions + " -p GETBULK -Ow " + snmpAddress + " 1.3.6.1.2.1.14", null, null);
        if ((pdu == null) || (pdu.length == 0)) {
            return; // no result
        }
        eResult result = null;
        Hashtable ospfAreaTable = new Hashtable();
        Hashtable ospfStubAreaTable = new Hashtable();
        Hashtable ospfLSDBAreaTable = new Hashtable();
        Hashtable ospfRangeAreaTable = new Hashtable();
        Hashtable ospfHostTable = new Hashtable();
        Hashtable ospfIFTable = new Hashtable();
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
                    if (oid.startsWith(ospfRouterIdOID) && ospfRouterIdFlag) {
                        result.addSet("OspfRouterId", param);
                    } else if (oid.startsWith(ospfAdminStatOID) && ospfAdminStatFlag) {
                        result.addSet("OspfAdminStat", param);
                    } else if (oid.startsWith(ospfVersionNumberOID) && ospfVersionNumberFlag) {
                        result.addSet("OspfVersionNumber", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ospfAreaBdrRtrStatusOID) && ospfAreaBdrRtrStatusFlag) {
                        String status = (param.equals("1") ? "True" : "False");
                        result.addSet("OspfAreaBdrRtrStatus", status);
                    } else if (oid.startsWith(ospfASBdrRtrStatusOID) && ospfASBdrRtrStatusFlag) {
                        String status = (param.equals("1") ? "True" : "False");
                        result.addSet("OspfASBdrRtrStatus", status);
                    } else if (oid.startsWith(ospfExternLSACountOID) && ospfExternLSACountFlag) {
                        result.addSet("OspfExternLSACount", Long.valueOf(((Gauge32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ospfExternLSACksumSumOID) && ospfExternLSACksumSumFlag) {
                        result.addSet("OspfExternLSACksumSum",
                                Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ospfTOSSupportOID) && ospfTOSSupportFlag) {
                        String status = (param.equals("1") ? "True" : "False");
                        result.addSet("OspfTOSSupport", status);
                    } else if (oid.startsWith(ospfOriginateNewLSAsOID) && ospfOriginateNewLSAsFlag) {
                        result.addSet("OspfOriginateNewLSAs", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ospfRxNewLSAsOID) && ospfRxNewLSAsFlag) {
                        result.addSet("OspfRxNewLSAs", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ospfExtLsdbLimitOID) && ospfExtLsdbLimitFlag) {
                        result.addSet("OspfExtLsdbLimit", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ospfMulticastExtensionsOID) && ospfMulticastExtensionsFlag) {
                        result.addSet("OspfMulticastExtensions",
                                Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ospfExitOverflowIntervalOID) && ospfExitOverflowIntervalFlag) {
                        result.addSet("OspfExitOverflowInterval",
                                Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(ospfDemandExtensionsOID) && ospfDemandExtensionsFlag) {
                        String status = (param.equals("1") ? "True" : "False");
                        result.addSet("OspfDemandExtensions", status);
                    } else if (oid.startsWith(ospfAuthTypeOID) && ospfAreaTableFlag) {
                        String id = oid.substring(ospfAuthTypeOID.length());
                        if (!ospfAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.NodeName = "OSPF_Area_" + id;
                            result1.addSet("OspfAuthType", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        } else {
                            eResult result1 = (eResult) ospfAreaTable.get(id);
                            result1.addSet("OspfAuthType", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ospfImportASExternOID) && ospfAreaTableFlag) {
                        String id = oid.substring(ospfImportASExternOID.length());
                        String truth = (param.equals("1") ? "True" : "False");
                        if (!ospfAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.NodeName = "OSPF_Area_" + id;
                            result1.addSet("OspfImportASExtern", truth);
                        } else {
                            eResult result1 = (eResult) ospfAreaTable.get(id);
                            result1.addSet("OspfImportASExtern", truth);
                        }
                    } else if (oid.startsWith(ospfSpfRunsOID) && ospfAreaTableFlag) {
                        String id = oid.substring(ospfSpfRunsOID.length());
                        if (!ospfAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.NodeName = "OSPF_Area_" + id;
                            result1.addSet("OspfSpfRuns", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        } else {
                            eResult result1 = (eResult) ospfAreaTable.get(id);
                            result1.addSet("OspfSpfRuns", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ospfAreaBdrRtrCountOID) && ospfAreaTableFlag) {
                        String id = oid.substring(ospfAreaBdrRtrCountOID.length());
                        if (!ospfAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.NodeName = "OSPF_Area_" + id;
                            result1.addSet("OspfAreaBdrRtrCount", Long.valueOf(((Gauge32) vb.getVariable()).getValue()));
                        } else {
                            eResult result1 = (eResult) ospfAreaTable.get(id);
                            result1.addSet("OspfAreaBdrRtrCount", Long.valueOf(((Gauge32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ospfASBdrRtrCountOID) && ospfAreaTableFlag) {
                        String id = oid.substring(ospfASBdrRtrCountOID.length());
                        if (!ospfAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.NodeName = "OSPF_Area_" + id;
                            result1.addSet("OspfASBdrRtrCount", Long.valueOf(((Gauge32) vb.getVariable()).getValue()));
                        } else {
                            eResult result1 = (eResult) ospfAreaTable.get(id);
                            result1.addSet("OspfASBdrRtrCount", Long.valueOf(((Gauge32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ospfAreaLSACountOID) && ospfAreaTableFlag) {
                        String id = oid.substring(ospfAreaLSACountOID.length());
                        if (!ospfAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.NodeName = "OSPF_Area_" + id;
                            result1.addSet("OspfAreaLSACount", Long.valueOf(((Gauge32) vb.getVariable()).getValue()));
                        } else {
                            eResult result1 = (eResult) ospfAreaTable.get(id);
                            result1.addSet("OspfAreaLSACount", Long.valueOf(((Gauge32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ospfAreaLSACksumSumOID) && ospfAreaTableFlag) {
                        String id = oid.substring(ospfAreaLSACksumSumOID.length());
                        if (!ospfAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.NodeName = "OSPF_Area_" + id;
                            result1.addSet("OspfAreaLSACksumSum",
                                    Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        } else {
                            eResult result1 = (eResult) ospfAreaTable.get(id);
                            result1.addSet("OspfAreaLSACksumSum",
                                    Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ospfAreaSummaryOID) && ospfAreaTableFlag) {
                        String id = oid.substring(ospfAreaSummaryOID.length());
                        String s = (param.equals("1") ? "NoAreaSummary" : "SendAreaSummary");
                        if (!ospfAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.NodeName = "OSPF_Area_" + id;
                            result1.addSet("OspfAreaSummary", s);
                        } else {
                            eResult result1 = (eResult) ospfAreaTable.get(id);
                            result1.addSet("OspfAreaSummary", s);
                        }
                    } else if (oid.startsWith(ospfAreaStatusOID) && ospfAreaTableFlag) {
                        String id = oid.substring(ospfAreaStatusOID.length());
                        if (!ospfAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.NodeName = "OSPF_Area_" + id;
                            result1.addSet("OspfAreaStatus", getOSPFAreaStatus(param));
                        } else {
                            eResult result1 = (eResult) ospfAreaTable.get(id);
                            result1.addSet("OspfAreaStatus", getOSPFAreaStatus(param));
                        }
                    } else if (oid.startsWith(ospfStubAreaIDOID) && ospfStubAreaTableFlag) {
                        String id = oid.substring(ospfStubAreaIDOID.length());
                        if (!ospfStubAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.NodeName = "OSPF_Stub_Area_" + param;
                        } else {
                            eResult result1 = (eResult) ospfAreaTable.get(id);
                            result1.NodeName = "OSPF_Stub_Area_" + param;
                        }
                    } else if (oid.startsWith(ospfStubTOSOID) && ospfStubAreaTableFlag) {
                        String id = oid.substring(ospfStubTOSOID.length());
                        if (!ospfStubAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfStubTOS", param);
                        } else {
                            eResult result1 = (eResult) ospfAreaTable.get(id);
                            result1.addSet("OspfStubTOS", param);
                        }
                    } else if (oid.startsWith(ospfStubMetricOID) && ospfStubAreaTableFlag) {
                        String id = oid.substring(ospfStubMetricOID.length());
                        if (!ospfStubAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfStubMetric", param);
                        } else {
                            eResult result1 = (eResult) ospfAreaTable.get(id);
                            result1.addSet("OspfStubMetric", param);
                        }
                    } else if (oid.startsWith(ospfStubStatusOID) && ospfStubAreaTableFlag) {
                        String id = oid.substring(ospfStubStatusOID.length());
                        if (!ospfStubAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfStubStatus", param);
                        } else {
                            eResult result1 = (eResult) ospfAreaTable.get(id);
                            result1.addSet("OspfStubStatus", param);
                        }
                    } else if (oid.startsWith(ospfStubMetricTypeOID) && ospfStubAreaTableFlag) {
                        String id = oid.substring(ospfStubMetricTypeOID.length());
                        if (!ospfStubAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfStubMetricType", getOSPFMetricType(param));
                        } else {
                            eResult result1 = (eResult) ospfAreaTable.get(id);
                            result1.addSet("OspfStubMetricType", getOSPFMetricType(param));
                        }
                    } else if (oid.startsWith(ospfLsdbAreaIdOID) && ospfLSDBAreaTableFlag) {
                        String id = oid.substring(ospfLsdbAreaIdOID.length());
                        if (!ospfLSDBAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfLSDBAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.NodeName = "OSPF_LSDB_Area_" + param;
                        } else {
                            eResult result1 = (eResult) ospfLSDBAreaTable.get(id);
                            result1.NodeName = "OSPF_LSDB_Area_" + param;
                        }
                    } else if (oid.startsWith(ospfLsdbTypeOID) && ospfLSDBAreaTableFlag) {
                        String id = oid.substring(ospfLsdbTypeOID.length());
                        if (!ospfLSDBAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfLSDBAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfLsdbType", getOSPFLSDBType(param));
                        } else {
                            eResult result1 = (eResult) ospfLSDBAreaTable.get(id);
                            result1.addSet("OspfLsdbType", getOSPFLSDBType(param));
                        }
                    } else if (oid.startsWith(ospfLsdbLSIDOID) && ospfLSDBAreaTableFlag) {
                        String id = oid.substring(ospfLsdbLSIDOID.length());
                        if (!ospfLSDBAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfLSDBAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfLsdbLSID", param);
                        } else {
                            eResult result1 = (eResult) ospfLSDBAreaTable.get(id);
                            result1.addSet("OspfLsdbLSID", param);
                        }
                    } else if (oid.startsWith(ospfLsdbRouterIdOID) && ospfLSDBAreaTableFlag) {
                        String id = oid.substring(ospfLsdbRouterIdOID.length());
                        if (!ospfLSDBAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfLSDBAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfLsdbRouterId", param);
                        } else {
                            eResult result1 = (eResult) ospfLSDBAreaTable.get(id);
                            result1.addSet("OspfLsdbRouterId", param);
                        }
                    } else if (oid.startsWith(ospfLsdbSequenceOID) && ospfLSDBAreaTableFlag) {
                        String id = oid.substring(ospfLsdbSequenceOID.length());
                        if (!ospfLSDBAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfLSDBAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfLsdbSequence",
                                    Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        } else {
                            eResult result1 = (eResult) ospfLSDBAreaTable.get(id);
                            result1.addSet("OspfLsdbSequence",
                                    Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ospfLsdbAgeOID) && ospfLSDBAreaTableFlag) {
                        String id = oid.substring(ospfLsdbAgeOID.length());
                        if (!ospfLSDBAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfLSDBAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfLsdbAge", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        } else {
                            eResult result1 = (eResult) ospfLSDBAreaTable.get(id);
                            result1.addSet("OspfLsdbAge", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ospfLsdbChecksumOID) && ospfLSDBAreaTableFlag) {
                        String id = oid.substring(ospfLsdbChecksumOID.length());
                        if (!ospfLSDBAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfLSDBAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfLsdbChecksum",
                                    Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        } else {
                            eResult result1 = (eResult) ospfLSDBAreaTable.get(id);
                            result1.addSet("OspfLsdbChecksum",
                                    Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ospfAreaRangeAreaIDOID) && ospfRangeAreaTableFlag) {
                        String id = oid.substring(ospfAreaRangeAreaIDOID.length());
                        if (!ospfRangeAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfRangeAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.NodeName = "OSPF_Range_Area_" + param;
                        } else {
                            eResult result1 = (eResult) ospfRangeAreaTable.get(id);
                            result1.NodeName = "OSPF_Range_Area_" + param;
                        }
                    } else if (oid.startsWith(ospfAreaRangeNetOID) && ospfRangeAreaTableFlag) {
                        String id = oid.substring(ospfAreaRangeNetOID.length());
                        if (!ospfRangeAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfRangeAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfAreaRangeNet", param);
                        } else {
                            eResult result1 = (eResult) ospfRangeAreaTable.get(id);
                            result1.addSet("OspfAreaRangeNet", param);
                        }
                    } else if (oid.startsWith(ospfAreaRangeMaskOID) && ospfRangeAreaTableFlag) {
                        String id = oid.substring(ospfAreaRangeMaskOID.length());
                        if (!ospfRangeAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfRangeAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfAreaRangeMask", param);
                        } else {
                            eResult result1 = (eResult) ospfRangeAreaTable.get(id);
                            result1.addSet("OspfAreaRangeMask", param);
                        }
                    } else if (oid.startsWith(ospfAreaRangeStatusOID) && ospfRangeAreaTableFlag) {
                        String id = oid.substring(ospfAreaRangeStatusOID.length());
                        if (!ospfRangeAreaTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfRangeAreaTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfAreaRangeStatus", param);
                        } else {
                            eResult result1 = (eResult) ospfRangeAreaTable.get(id);
                            result1.addSet("OspfAreaRangeStatus", param);
                        }
                    } else if (oid.startsWith(ospfHostIpAddressOID) && ospfHostTableFlag) {
                        String id = oid.substring(ospfHostIpAddressOID.length());
                        if (!ospfHostTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfHostTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.NodeName = "OSPF_Host_" + param;
                        } else {
                            eResult result1 = (eResult) ospfHostTable.get(id);
                            result1.NodeName = "OSPF_Host_" + param;
                        }
                    } else if (oid.startsWith(ospfHostTOSOID) && ospfHostTableFlag) {
                        String id = oid.substring(ospfHostTOSOID.length());
                        if (!ospfHostTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfHostTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfHostTOS", param);
                        } else {
                            eResult result1 = (eResult) ospfHostTable.get(id);
                            result1.addSet("OspfHostTOS", param);
                        }
                    } else if (oid.startsWith(ospfHostMetricOID) && ospfHostTableFlag) {
                        String id = oid.substring(ospfHostMetricOID.length());
                        if (!ospfHostTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfHostTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfHostMetric", param);
                        } else {
                            eResult result1 = (eResult) ospfHostTable.get(id);
                            result1.addSet("OspfHostMetric", param);
                        }
                    } else if (oid.startsWith(ospfHostStatusOID) && ospfHostTableFlag) {
                        String id = oid.substring(ospfHostStatusOID.length());
                        if (!ospfHostTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfHostTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfHostStatus", param);
                        } else {
                            eResult result1 = (eResult) ospfHostTable.get(id);
                            result1.addSet("OspfHostStatus", param);
                        }
                    } else if (oid.startsWith(ospfHostAreaIDOID) && ospfHostTableFlag) {
                        String id = oid.substring(ospfHostAreaIDOID.length());
                        if (!ospfHostTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfHostTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfHostAreaID", param);
                        } else {
                            eResult result1 = (eResult) ospfHostTable.get(id);
                            result1.addSet("OspfHostAreaID", param);
                        }
                    } else if (oid.startsWith(ospfIfIpAddressOID) && ospfIFTableFlag) {
                        String id = oid.substring(ospfIfIpAddressOID.length());
                        if (!ospfIFTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfIFTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.NodeName = "OSPF_IF_" + param;
                        } else {
                            eResult result1 = (eResult) ospfIFTable.get(id);
                            result1.NodeName = "OSPF_IF_" + param;
                        }
                    } else if (oid.startsWith(ospfIfAreaIdOID) && ospfIFTableFlag) {
                        String id = oid.substring(ospfIfAreaIdOID.length());
                        if (!ospfIFTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfIFTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfIfAreaId", param);
                        } else {
                            eResult result1 = (eResult) ospfIFTable.get(id);
                            result1.addSet("OspfIfAreaId", param);
                        }
                    } else if (oid.startsWith(ospfIfTypeOID) && ospfIFTableFlag) {
                        String id = oid.substring(ospfIfTypeOID.length());
                        if (!ospfIFTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfIFTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfIfType", getOSPFIfType(param));
                        } else {
                            eResult result1 = (eResult) ospfIFTable.get(id);
                            result1.addSet("OspfIfType", getOSPFIfType(param));
                        }
                    } else if (oid.startsWith(ospfIfAdminStatOID) && ospfIFTableFlag) {
                        String id = oid.substring(ospfIfAdminStatOID.length());
                        if (!ospfIFTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfIFTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfIfAdminStat", param);
                        } else {
                            eResult result1 = (eResult) ospfIFTable.get(id);
                            result1.addSet("OspfIfAdminStat", param);
                        }
                    } else if (oid.startsWith(ospfIfRtrPriorityOID) && ospfIFTableFlag) {
                        String id = oid.substring(ospfIfRtrPriorityOID.length());
                        if (!ospfIFTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfIFTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfIfRtrPriority", param);
                        } else {
                            eResult result1 = (eResult) ospfIFTable.get(id);
                            result1.addSet("OspfIfRtrPriority", param);
                        }
                    } else if (oid.startsWith(ospfIfTransitDelayOID) && ospfIFTableFlag) {
                        String id = oid.substring(ospfIfTransitDelayOID.length());
                        if (!ospfIFTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfIFTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfIfTransitDelay", param);
                        } else {
                            eResult result1 = (eResult) ospfIFTable.get(id);
                            result1.addSet("OspfIfTransitDelay", param);
                        }
                    } else if (oid.startsWith(ospfIfRetransIntervalOID) && ospfIFTableFlag) {
                        String id = oid.substring(ospfIfRetransIntervalOID.length());
                        if (!ospfIFTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfIFTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfIfRetransInterval", param);
                        } else {
                            eResult result1 = (eResult) ospfIFTable.get(id);
                            result1.addSet("OspfIfRetransInterval", param);
                        }
                    } else if (oid.startsWith(ospfIfHelloIntervalOID) && ospfIFTableFlag) {
                        String id = oid.substring(ospfIfHelloIntervalOID.length());
                        if (!ospfIFTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfIFTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfIfHelloInterval", param);
                        } else {
                            eResult result1 = (eResult) ospfIFTable.get(id);
                            result1.addSet("OspfIfHelloInterval", param);
                        }
                    } else if (oid.startsWith(ospfIfRtrDeadIntervalOID) && ospfIFTableFlag) {
                        String id = oid.substring(ospfIfRtrDeadIntervalOID.length());
                        if (!ospfIFTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfIFTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfIfRtrDeadInterval",
                                    Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        } else {
                            eResult result1 = (eResult) ospfIFTable.get(id);
                            result1.addSet("OspfIfRtrDeadInterval",
                                    Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ospfIfPollIntervalOID) && ospfIFTableFlag) {
                        String id = oid.substring(ospfIfPollIntervalOID.length());
                        if (!ospfIFTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfIFTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfIfPollInterval",
                                    Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        } else {
                            eResult result1 = (eResult) ospfIFTable.get(id);
                            result1.addSet("OspfIfPollInterval",
                                    Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ospfIfStateOID) && ospfIFTableFlag) {
                        String id = oid.substring(ospfIfStateOID.length());
                        if (!ospfIFTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfIFTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfIfState", getOSPFIfState(param));
                        } else {
                            eResult result1 = (eResult) ospfIFTable.get(id);
                            result1.addSet("OspfIfState", getOSPFIfState(param));
                        }
                    } else if (oid.startsWith(ospfIfDesignatedRouterOID) && ospfIFTableFlag) {
                        String id = oid.substring(ospfIfDesignatedRouterOID.length());
                        if (!ospfIFTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfIFTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfIfDesignatedRouter", param);
                        } else {
                            eResult result1 = (eResult) ospfIFTable.get(id);
                            result1.addSet("OspfIfDesignatedRouter", param);
                        }
                    } else if (oid.startsWith(ospfIfBackupDesignatedRouterOID) && ospfIFTableFlag) {
                        String id = oid.substring(ospfIfBackupDesignatedRouterOID.length());
                        if (!ospfIFTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfIFTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfIfBackupDesignatedRouter", param);
                        } else {
                            eResult result1 = (eResult) ospfIFTable.get(id);
                            result1.addSet("OspfIfBackupDesignatedRouter", param);
                        }
                    } else if (oid.startsWith(ospfIfEventsOID) && ospfIFTableFlag) {
                        String id = oid.substring(ospfIfEventsOID.length());
                        if (!ospfIFTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfIFTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfIfEvents", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        } else {
                            eResult result1 = (eResult) ospfIFTable.get(id);
                            result1.addSet("OspfIfEvents", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ospfIfStatusOID) && ospfIFTableFlag) {
                        String id = oid.substring(ospfIfStatusOID.length());
                        if (!ospfIFTable.containsKey(id)) {
                            eResult result1 = new eResult();
                            ospfIFTable.put(id, result1);
                            result1.FarmName = farmName;
                            result1.ClusterName = clusterName;
                            result1.Module = moduleName;
                            result1.addSet("OspfIfStatus", getOSPFIfStatus(param));
                        } else {
                            eResult result1 = (eResult) ospfIFTable.get(id);
                            result1.addSet("OspfIfStatus", getOSPFIfStatus(param));
                        }
                    }
                } catch (Throwable t) {
                    logger.warning(t.getLocalizedMessage());
                }
            }
        }
        if ((result != null) && (result.param_name != null) && (result.param_name.length != 0)) {
            res.add(result);
        }
        if (ospfAreaTable.size() != 0) {
            for (Enumeration en = ospfAreaTable.keys(); en.hasMoreElements();) {
                String id = (String) en.nextElement();
                if (id == null) {
                    continue;
                }
                eResult result1 = (eResult) ospfAreaTable.get(id);
                if ((result1 != null) && (result1.param_name != null) && (result1.param_name.length != 0)) {
                    res.add(result1);
                }
            }
        }
        if (ospfStubAreaTable.size() != 0) {
            for (Enumeration en = ospfStubAreaTable.keys(); en.hasMoreElements();) {
                String id = (String) en.nextElement();
                if (id == null) {
                    continue;
                }
                eResult result1 = (eResult) ospfStubAreaTable.get(id);
                if ((result1 != null) && (result1.NodeName != null) && (result1.param_name != null)
                        && (result1.param_name.length != 0)) {
                    res.add(result1);
                }
            }
        }
        if (ospfLSDBAreaTable.size() != 0) {
            for (Enumeration en = ospfLSDBAreaTable.keys(); en.hasMoreElements();) {
                String id = (String) en.nextElement();
                if (id == null) {
                    continue;
                }
                eResult result1 = (eResult) ospfLSDBAreaTable.get(id);
                if ((result1 != null) && (result1.NodeName != null) && (result1.param_name != null)
                        && (result1.param_name.length != 0)) {
                    res.add(result1);
                }
            }
        }
        if (ospfRangeAreaTable.size() != 0) {
            for (Enumeration en = ospfRangeAreaTable.keys(); en.hasMoreElements();) {
                String id = (String) en.nextElement();
                if (id == null) {
                    continue;
                }
                eResult result1 = (eResult) ospfRangeAreaTable.get(id);
                if ((result1 != null) && (result1.NodeName != null) && (result1.param_name != null)
                        && (result1.param_name.length != 0)) {
                    res.add(result1);
                }
            }
        }
        if (ospfHostTable.size() != 0) {
            for (Enumeration en = ospfHostTable.keys(); en.hasMoreElements();) {
                String id = (String) en.nextElement();
                if (id == null) {
                    continue;
                }
                eResult result1 = (eResult) ospfHostTable.get(id);
                if ((result1 != null) && (result1.NodeName != null) && (result1.param_name != null)
                        && (result1.param_name.length != 0)) {
                    res.add(result1);
                }
            }
        }
        if (ospfIFTable.size() != 0) {
            for (Enumeration en = ospfIFTable.keys(); en.hasMoreElements();) {
                String id = (String) en.nextElement();
                if (id == null) {
                    continue;
                }
                eResult result1 = (eResult) ospfIFTable.get(id);
                if ((result1 != null) && (result1.NodeName != null) && (result1.param_name != null)
                        && (result1.param_name.length != 0)) {
                    res.add(result1);
                }
            }
        }
    }

    public boolean setOSPFRouterID(String routerID) {

        PDU pdu[] = snmp.run(defaultOptions + " -p SET " + snmpAddress + " " + ospfRouterIdOID + "0={s}" + routerID,
                null, null);
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

    public boolean setOSPFAdminStat(boolean enabled) {

        String status = (enabled ? "enabled" : "disabled");
        PDU pdu[] = snmp.run(defaultOptions + " -p SET " + snmpAddress + " " + ospfAdminStatOID + "0={s}" + status,
                null, null);
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

    public boolean setOSPFASBdrRtrStatus(boolean flag) {

        String status = (flag ? "1" : "2");
        PDU pdu[] = snmp.run(
                defaultOptions + " -p SET " + snmpAddress + " " + ospfASBdrRtrStatusOID + "0={s}" + status, null, null);
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

    public boolean setOSPFExtLsdbLimit(int value) {

        PDU pdu[] = snmp.run(defaultOptions + " -p SET " + snmpAddress + " " + ospfExtLsdbLimitOID + "0={s}" + value,
                null, null);
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

    public boolean setOSPFMulticastExtensions(int value) {

        PDU pdu[] = snmp.run(defaultOptions + " -p SET " + snmpAddress + " " + ospfMulticastExtensionsOID + "0={s}"
                + value, null, null);
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

    public boolean setOSPFExitOverflowInterval(int value) {

        PDU pdu[] = snmp.run(defaultOptions + " -p SET " + snmpAddress + " " + ospfExitOverflowIntervalOID + "0={s}"
                + value, null, null);
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

    public boolean setOSPFDemandExtensions(boolean flag) {

        String status = (flag ? "1" : "2");
        PDU pdu[] = snmp.run(defaultOptions + " -p SET " + snmpAddress + " " + ospfDemandExtensionsOID + "0={s}"
                + status, null, null);
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

    public boolean setOSPFAuthType(int areaID, int ospfAuthType) {

        PDU pdu[] = snmp.run(defaultOptions + " -p SET " + snmpAddress + " " + ospfAuthTypeOID + areaID + "={i}"
                + ospfAuthType, null, null);
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

    public boolean setOSPFImportASExtern(int areaID, boolean status) {

        int s = (status ? 1 : 2);
        PDU pdu[] = snmp.run(defaultOptions + " -p SET " + snmpAddress + " " + ospfImportASExternOID + areaID + "={i}"
                + s, null, null);
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

    static final String[][] rowStatus = new String[][] { { "1", "Active" }, { "2", "NotInService" },
            { "3", "NotReady" }, { "4", "CreateAndGo" }, { "5", "CreateAndWait" }, { "6", "Destroy" } };

    private String getOSPFAreaStatus(String status) {

        if (status == null) {
            return "Null";
        }
        for (String[] rowStatu : rowStatus) {
            if (rowStatu[0].equals(status)) {
                return rowStatu[1];
            }
        }
        return "Unknown";
    }

    static final String[][] metricTypes = new String[][] { { "1", "OspfMetric" }, { "2", "ComparableCost" },
            { "3", "NonComparable" } };

    private String getOSPFMetricType(String type) {

        if (type == null) {
            return "Null";
        }
        for (String[] metricType : metricTypes) {
            if (metricType[0].equals(type)) {
                return metricType[1];
            }
        }
        return "Unknown";
    }

    static final String[][] lsdbTypes = new String[][] { { "1", "RouterLink" }, { "2", "NetworkLink" },
            { "3", "SummaryLink" }, { "4", "AsSummaryLink" }, { "5", "AsExternalLink" } };

    private String getOSPFLSDBType(String type) {

        if (type == null) {
            return "Null";
        }
        for (String[] lsdbType : lsdbTypes) {
            if (lsdbType[0].equals(type)) {
                return lsdbType[1];
            }
        }
        return "Unknown";
    }

    static final String[][] ifTypes = new String[][] { { "1", "Broadcast" }, { "2", "Nbma" }, { "3", "PointToPoint" } };

    private String getOSPFIfType(String type) {

        if (type == null) {
            return "Null";
        }
        for (String[] ifType : ifTypes) {
            if (ifType[0].equals(type)) {
                return ifType[1];
            }
        }
        return "Unknown";
    }

    static final String[][] ifStates = new String[][] { { "1", "Down" }, { "2", "Loopback" }, { "3", "Waiting" },
            { "4", "PointToPoint" }, { "5", "DesignatedRouter" }, { "6", "BackupDesignatedRouter" },
            { "7", "OtherDesignatedRouter" } };

    private String getOSPFIfState(String state) {

        if (state == null) {
            return "Null";
        }
        for (String[] ifState : ifStates) {
            if (ifState[0].equals(state)) {
                return ifState[1];
            }
        }
        return "Unknown";
    }

    static final String[][] ifStatus = new String[][] { { "1", "Active" }, { "2", "NotInService" },
            { "3", "NotReady" }, { "4", "CreateAndGo" }, { "5", "CreateAndWait" }, { "6", "Destroy" } };

    private String getOSPFIfStatus(String status) {

        if (status == null) {
            return "Null";
        }
        for (String[] ifStatu : ifStatus) {
            if (ifStatu[0].equals(status)) {
                return ifStatu[1];
            }
        }
        return "Unknown";
    }

    /** For testing */
    public static void main(String args[]) {

        logger.setLevel(Level.FINEST);
        SNMPFactory factory = new SNMPFactory();
        SNMPOSPF route = new SNMPOSPF("-c private -v 2c", factory, "udp:141.85.99.136/161", "RB", "SNMPOSPF",
                "SNMPModule");
        System.out.println("Support OSPF = " + route.supportOSPF());
        Vector v = new Vector();
        route.getResults(v);
        for (int i = 0; i < v.size(); i++) {
            System.out.println(v.get(i));
        }
    }

} // end of class SNMPOSPF

