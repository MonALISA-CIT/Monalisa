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
import org.snmp4j.smi.UnsignedInteger32;
import org.snmp4j.smi.VariableBinding;

/**
 * Class used to gather informations for MPLS through the use of SNMP.
 */
public class SNMPMPLS {

    private static final Logger logger = Logger.getLogger(SNMPMPLS.class.getName());

    private final SNMPFactory snmp;
    private final String defaultOptions;
    private final String snmpAddress;
    private final String farmName;
    private final String clusterName;
    private final String moduleName;

    static final String node = "SNMPMPLS";

    Hashtable ifSupport = new Hashtable();
    boolean ifsInitialized = false;

    /** A textual string containing information about the interface. This string should include the name of the manufacturer, the product name and the 
     * version of the interface hardware/software.*/
    static final String ifDescrOID = "1.3.6.1.2.1.2.2.1.2.";
    /** This is the minimum value of an MPLS label that this LSR is willing to receive on this interface. */
    static final String mplsInterfaceLabelMinInOID = "1.3.6.1.2.1.10.166.2.1.1.1.2.";
    /** This is the maximum value of an MPLS label that this LSR is willing to receive on this interface. */
    static final String mplsInterfaceLabelMaxInOID = "1.3.6.1.2.1.10.166.2.1.1.1.3.";
    /** This is the minimum value of an MPLS label that this LSR is willing to send on this interface. */
    static final String mplsInterfaceLabelMinOutOID = "1.3.6.1.2.1.10.166.2.1.1.1.4.";
    /** This is the maximum value of an MPLS label that this LSR is willing to send on this interface. */
    static final String mplsInterfaceLabelMaxOutOID = "1.3.6.1.2.1.10.166.2.1.1.1.5.";
    /** This value indicates the total amount of usable bandwidth on this interface and is specified in kilobits per second (Kbps). This variable is not 
     * applicable when applied to the interface with index 0. When this value cannot be measured, this value should contain the nominal bandwidth. */
    static final String mplsInterfaceTotalBandwidthOID = "1.3.6.1.2.1.10.166.2.1.1.1.6.";
    /** This value indicates the total amount of available bandwidth available on this interface and is specified in kilobits per second (Kbps). This value 
     * is calculated as the difference between the amount of bandwidth currently in use and that specified in mplsInterfaceTotalBandwidth. This variable 
     * is not applicable when applied to the interface with index 0. When this value cannot be measured, this value should contain the nominal bandwidth. */
    static final String mplsInterfaceAvailableBandwidthOID = "1.3.6.1.2.1.10.166.2.1.1.1.7.";
    /** If the value of the mplsInterfaceIndex for this entry is zero, then this entry corresponds to the per-platform label space for all interfaces configured 
     * to use that label space. In this case the perPlatform(0) bit MUST be set; the perInterface(1) bit is meaningless and MUST be ignored. */
    static final String mplsInterfaceLabelParticipationTypeOID = "1.3.6.1.2.1.10.166.2.1.1.1.8.";
    /** This object counts the number of labels that are in use at this point in time on this interface in the incoming direction. If the interface participates 
     * in only the per-platform label space, then the value of the instance of this object MUST be identical to the value of the instance with index 0. If the 
     * interface participates in the per-interface label space, then the instance of this object MUST represent the number of per-interface labels that are in 
     * use on this interface. */
    static final String mplsInterfacePerfInLabelsInUseOID = "1.3.6.1.2.1.10.166.2.1.2.1.1.";
    /** This object counts the number of labeled packets that have been received on this interface and which were discarded because there was no 
     * matching cross-connect entry. This object MUST count on a per-interface basis regardless of which label space the interface participates in. */
    static final String mplsInterfacePerfInLabelLookupFailuresOID = "1.3.6.1.2.1.10.166.2.1.2.1.2.";
    /** This object counts the number of top-most labels in the outgoing label stacks that are in use at this point in time on this interface. This object 
     * MUST count on a per-interface basis regardless of which label space the interface participates in. */
    static final String mplsInterfacePerfOutLabelsInUseOID = "1.3.6.1.2.1.10.166.2.1.2.1.3.";
    /** This object counts the number of outgoing MPLS packets that required fragmentation before transmission on this interface. This object MUST 
     * count on a per-interface basis regardless of which label space the interface participates in. */
    static final String mplsInterfacePerfOutFragmentedPktsOID = "1.3.6.1.2.1.10.166.2.1.2.1.4.";
    /** This object contains the next available value to be used for mplsInSegmentIndex when creating entries in the mplsInSegmentTable. The special value 
     * of a string containing the single octet 0x00 indicates that no new entries can be created in this table. Agents not allowing managers to create 
     * entries in this table MUST set this object to this special value. */
    static final String mplsInSegmentIndexNextOID = "1.3.6.1.2.1.10.166.2.1.3.";
    /** This object represents the interface index for the incoming MPLS interface. A value of zero represents all interfaces participating in the per-platform 
     * label space. This may only be used in cases where the incoming interface and label are associated with the same mplsXCEntry. Specifically, given a 
     * label and any incoming interface pair from the per-platform label space, the outgoing label/interface mapping remains the same. If this is not the 
     * case, then individual entries MUST exist that can then be mapped to unique mplsXCEntries. */
    static final String mplsInSegmentInterfaceOID = "1.3.6.1.2.1.10.166.2.1.4.1.2";
    /** If the corresponding instance of mplsInSegmentLabelPtr is zeroDotZero then this object MUST contain the incoming label associated with this 
     * in-segment. If not this object SHOULD be zero and MUST be ignored. */
    static final String mplsInSegmentLabelOID = "1.3.6.1.2.1.10.166.2.1.4.1.3.";
    /** The number of labels to pop from the incoming packet. Normally only the top label is popped from the packet and used for all switching decisions 
     * for that packet. This is indicated by setting this object to the default value of 1. If an LSR supports popping of more than one label, this object MUST 
     * be set to that number. This object cannot be modified if mplsInSegmentRowStatus is active(1). */
    static final String mplsInSegmentNPopOID = "1.3.6.1.2.1.10.166.2.1.4.1.5.";
    /** The IANA address family [IANAFamily] of packets received on this segment, which is used at an egress LSR to deliver them to the appropriate layer 
     * 3 entity. A value of other(0) indicates that the family type is either unknown or undefined; this SHOULD NOT be used at an egress LSR. This object 
     * cannot be modified if mplsInSegmentRowStatus is active(1). */
    static final String mplsInSegmentAddrFamilyOID = "1.3.6.1.2.1.10.166.2.1.4.1.6.";
    /** Index into mplsXCTable which identifies which cross-connect entry this segment is part of. The string containing the single octet 0x00 indicates 
     * that this entry is not referred to by any cross-connect entry. When a cross-connect entry is created which this in-segment is a part of, this object is 
     * automatically updated to reflect the value of mplsXCIndex of that cross-connect entry. */
    static final String mplsInSegmentXCIndexOID = "1.3.6.1.2.1.10.166.2.1.4.1.7.";
    /** Denotes the entity that created and is responsible for managing this segment. */
    static final String mplsInSegmentOwnerOID = "1.3.6.1.2.1.10.166.2.1.4.1.8.";
    /** This variable is used to create, modify, and/or delete a row in this table. When a row in this table has a row in the active(1) state, no objects in 
     * this row can be modified except the mplsInSegmentRowStatus and mplsInSegmentStorageType. */
    static final String mplsInSegmentRowStatusOID = "1.3.6.1.2.1.10.166.2.1.4.1.10.";
    /** This variable indicates the storage type for this object. The agent MUST ensure that this object's value remains consistent with the associated 
     * mplsXCEntry. Conceptual rows having the value 'permanent' need not allow write-access to any columnar objects in the row. */
    static final String mplsInSegmentStorageTypeOID = "1.3.6.1.2.1.10.166.2.1.4.1.11.";
    /** This value represents the total number of octets received by this segment. It MUST be equal to the least significant 32 bits of 
     * mplsInSegmentPerfHCOctets if mplsInSegmentPerfHCOctets is supported according to the rules spelled out in RFC2863. */
    static final String mplsInSegmentPerfOctetsOID = "1.3.6.1.2.1.10.166.2.1.5.1.1.";
    /** Total number of packets received by this segment. */
    static final String mplsInSegmentPerfPacketsOID = "1.3.6.1.2.1.10.166.2.1.5.1.2.";
    /** The number of errored packets received on this segment. */
    static final String mplsInSegmentPerfErrorsOID = "1.3.6.1.2.1.10.166.2.1.5.1.3.";
    /** The number of labeled packets received on this in-segment, which were chosen to be discarded even though no errors had been detected to 
     * prevent their being transmitted. One possible reason for discarding such a labeled packet could be to free up buffer space. */
    static final String mplsInSegmentPerfDiscardsOID = "1.3.6.1.2.1.10.166.2.1.5.1.4.";
    /** The total number of octets received. This is the 64 bit version of mplsInSegmentPerfOctets, if mplsInSegmentPerfHCOctets is supported according 
     * to the rules spelled out in RFC2863. */
    static final String mplsInSegmentPerfHCOctetsOID = "1.3.6.1.2.1.10.166.2.1.5.1.5.";
    /** The value of sysUpTime on the most recent occasion at which any one or more of this segment's Counter32 or Counter64 suffered a discontinuity. 
     * If no such discontinuities have occurred since the last reinitialization of the local management subsystem, then this object contains a zero value. */
    static final String mplsInSegmentPerfDiscontinuityTimeOID = "1.3.6.1.2.1.10.166.2.1.5.1.6.";
    /** This object contains the next available value to be used for mplsOutSegmentIndex when creating entries in the mplsOutSegmentTable. The special 
     * value of a string containing the single octet 0x00 indicates that no new entries can be created in this table. Agents not allowing managers to create 
     * entries in this table MUST set this object to this special value. */
    static final String mplsOutSegmentIndexNextOID = "1.3.6.1.2.1.10.166.2.1.6.";
    /** This value must contain the interface index of the outgoing interface. This object cannot be modified if mplsOutSegmentRowStatus is active(1). 
     * The mplsOutSegmentRowStatus cannot be set to active(1) until this object is set to a value corresponding to a valid ifEntry. */
    static final String mplsOutSegmentInterfaceOID = "1.3.6.1.2.1.10.166.2.1.7.1.2.";
    /** This value indicates whether or not a top label should be pushed onto the outgoing packet's label stack. The value of this variable MUST be set to 
     * true(1) if the outgoing interface does not support pop-and-go (and no label stack remains). For example, on ATM interface, or if the segment 
     * represents a tunnel origination. Note that it is considered an error in the case that mplsOutSegmentPushTopLabel is set to false, but the 
     * cross-connect entry which refers to this out-segment has a non-zero mplsLabelStackIndex. The LSR MUST ensure that this situation does not happen. 
     * This object cannot be modified if mplsOutSegmentRowStatus is active(1). */
    static final String mplsOutSegmentPushTopLabelOID = "1.3.6.1.2.1.10.166.2.1.7.1.3.";
    /** If mplsOutSegmentPushTopLabel is true then this represents the label that should be pushed onto the top of the outgoing packet's label stack. Otherwise 
     * this value SHOULD be set to 0 by the management station and MUST be ignored by the agent. This object cannot be modified if 
     * mplsOutSegmentRowStatus is active(1). */
    static final String mplsOutSegmentTopLabelOID = "1.3.6.1.2.1.10.166.2.1.7.1.4.";
    /** Indicates the next hop Internet address type. Only values unknown(0), ipv4(1) or ipv6(2) have to be supported. A value of unknown(0) is allowed 
     * only when the outgoing interface is of type point-to-point. If any other unsupported values are attempted in a set operation, the agent MUST return 
     * an inconsistentValue error. */
    static final String mplsOutSegmentNextHopAddrTypeOID = "1.3.6.1.2.1.10.166.2.1.7.1.6.";
    /** The internet address of the next hop. The type of this address is determined by the value of the mplslOutSegmentNextHopAddrType object. This 
     * object cannot be modified if mplsOutSegmentRowStatus is active(1). */
    static final String mplsOutSegmentNextHopAddrOID = "1.3.6.1.2.1.10.166.2.1.7.1.7.";
    /** Index into mplsXCTable which identifies which cross-connect entry this segment is part of. A value of the string containing the single octet 0x00 
     * indicates that this entry is not referred to by any cross-connect entry. When a cross-connect entry is created which this out-segment is a part of, this 
     * object MUST be updated by the agent to reflect the value of mplsXCIndex of that cross-connect entry. */
    static final String mplsOutSegmentXCIndexOID = "1.3.6.1.2.1.10.166.2.1.7.1.8.";
    /** Denotes the entity which created and is responsible for managing this segment. */
    static final String mplsOutSegmentOwnerOID = "1.3.6.1.2.1.10.166.2.1.7.1.9.";
    /** For creating, modifying, and deleting this row. When a row in this table has a row in the active(1) state, no objects in this row can be modified 
     * except the mplsOutSegmentRowStatus or mplsOutSegmentStorageType. */
    static final String mplsOutSegmentRowStatusOID = "1.3.6.1.2.1.10.166.2.1.7.1.11.";
    /** This variable indicates the storage type for this object. The agent MUST ensure that this object's value remains consistent with the associated 
     * mplsXCEntry. Conceptual rows having the value 'permanent' need not allow write-access to any columnar objects in the row. */
    static final String mplsOutSegmentStorageTypeOID = "1.3.6.1.2.1.10.166.2.1.7.1.12.";
    /** This value contains the total number of octets sent on this segment. It MUST be equal to the least significant 32 bits of mplsOutSegmentPerfHCOctets 
     * if mplsOutSegmentPerfHCOctets is supported according to the rules spelled out in RFC2863. */
    static final String mplsOutSegmentPerfOctetsOID = "1.3.6.1.2.1.10.166.2.1.8.1.1.";
    /** This value contains the total number of packets sent on this segment. */
    static final String mplsOutSegmentPerfPacketsOID = "1.3.6.1.2.1.10.166.2.1.8.1.2.";
    /** Number of packets that could not be sent due to errors on this segment. */
    static final String mplsOutSegmentPerfErrorsOID = "1.3.6.1.2.1.10.166.2.1.8.1.3.";
    /** The number of labeled packets attempted to be transmitted on this out-segment, which were chosen to be discarded even though no errors had 
     * been detected to prevent their being transmitted. One possible reason for discarding such a labeled packet could be to free up buffer space. */
    static final String mplsOutSegmentPerfDiscardsOID = "1.3.6.1.2.1.10.166.2.1.8.1.4.";
    /** Total number of octets sent. This is the 64 bit version of mplsOutSegmentPerfOctets, if mplsOutSegmentPerfHCOctets is supported according to 
     * the rules spelled out in RFC2863. */
    static final String mplsOutSegmentPerfHCOctetsOID = "1.3.6.1.2.1.10.166.2.1.8.1.5.";
    /** The value of sysUpTime on the most recent occasion at which any one or more of this segment's Counter32 or Counter64 suffered a discontinuity. 
     * If no such discontinuities have occurred since the last reinitialization of the local management subsystem, then this object contains a zero value. */
    static final String mplsOutSegmentPerfDiscontinuityTimeOID = "1.3.6.1.2.1.10.166.2.1.8.1.6.";
    /** This object contains the next available value to be used for mplsXCIndex when creating entries in the mplsXCTable. A special value of the zero 
     * length string indicates that no more new entries can be created in the relevant table. Agents not allowing managers to create entries in this table 
     * MUST set this value to the zero length string. */
    static final String mplsXCIndexNextOID = "1.3.6.1.2.1.10.166.2.1.9.";
    /** This value identifies the label switched path that this cross-connect entry belongs to. This object cannot be modified if mplsXCRowStatus is 
     * active(1) except for this object. */
    static final String mplsXCLspIdOID = "1.3.6.1.2.1.10.166.2.1.10.1.4.";
    /** Primary index into mplsLabelStackTable identifying a stack of labels to be pushed beneath the top label. Note that the top label identified by the 
     * out-segment ensures that all the components of a multipoint-to-point connection have the same outgoing label. A value of the string containing the 
     * single octet 0x00 indicates that no labels are to be stacked beneath the top label. This object cannot be modified if mplsXCRowStatus is active(1). */
    static final String mplsXCLabelStackIndexOID = "1.3.6.1.2.1.10.166.2.1.10.1.5.";
    /** Denotes the entity that created and is responsible for managing this cross-connect. */
    static final String mplsXCOwnerOID = "1.3.6.1.2.1.10.166.2.1.10.1.6.";
    /** For creating, modifying, and deleting this row. When a row in this table has a row in the active(1) state, no objects in this row except this object 
     * and the mplsXCStorageType can be modified. */
    static final String mplsXCRowStatusOID = "1.3.6.1.2.1.10.166.2.1.10.1.7.";
    /** This variable indicates the storage type for this object. The agent MUST ensure that the associated in and out segments also have the same 
     * StorageType value and are restored consistently upon system restart. This value SHOULD be set to permanent(4) if created as a result of a static LSP 
     * configuration. Conceptual rows having the value 'permanent' need not allow write-access to any columnar objects in the row. */
    static final String mplsXCStorageTypeOID = "1.3.6.1.2.1.10.166.2.1.10.1.8.";
    /** The desired operational status of this segment. */
    static final String mplsXCAdminStatusOID = "1.3.6.1.2.1.10.166.2.1.10.1.9.";
    /** The actual operational status of this cross-connect. */
    static final String mplsXCOperStatusOID = "1.3.6.1.2.1.10.166.2.1.10.1.10.";
    /** The maximum stack depth supported by this LSR. */
    static final String mplsMaxLabelStackDepthOID = "1.3.6.1.2.1.10.166.2.1.11.";
    /** This object contains the next available value to be used for mplsLabelStackIndex when creating entries in the mplsLabelStackTable. The special 
     * string containing the single octet 0x00 indicates that no more new entries can be created in the relevant table. Agents not allowing managers 
     * to create entries in this table MUST set this value to the string containing the single octet 0x00. */
    static final String mplsLabelStackIndexNextOID = "1.3.6.1.2.1.10.166.2.1.12.";
    /** The label to be pushed. */
    static final String mplsLabelStackLabelOID = "1.3.6.1.2.1.10.166.2.1.13.1.3.";
    /** For creating, modifying, and deleting this row. When a row in this table has a row in the active(1) state, no objects in this row except this object 
     * and the mplsLabelStackStorageType can be modified. */
    static final String mplsLabelStackRowStatusOID = "1.3.6.1.2.1.10.166.2.1.13.1.5.";
    /** This variable indicates the storage type for this object. This object cannot be modified if mplsLabelStackRowStatus is active(1). No objects are 
     * required to be writable for rows in this table with this object set to permanent(4). */
    static final String mplsLabelStackStorageTypeOID = "1.3.6.1.2.1.10.166.2.1.13.1.6.";
    /** If this object is set to true(1), then it enables the emission of mplsXCUp and mplsXCDown notifications; otherwise these notifications are not emitted. */
    static final String mplsXCNotificationsEnableOID = "1.3.6.1.2.1.10.166.2.1.15.";
    /** This value of this object is perPlatform(1), then this means that the label space type is per platform. If this object is perInterface(2), then this means 
     * that the label space type is per Interface. */
    static final String mplsLdpEntityGenericLabelSpaceOID = "1.3.6.1.2.1.10.166.7.1.1.1.1.3.";
    /** This value represents either the InterfaceIndex of the 'ifLayer' where these Generic Label would be created, or 0 (zero). The value of zero means 
     * that the InterfaceIndex is not known. However, if the InterfaceIndex is known, then it must be represented by this value. If an InterfaceIndex 
     * becomes known, then the network management entity (e.g., SNMP agent) responsible for this object MUST change the value from 0 (zero) to the 
     * value of the InterfaceIndex. */
    static final String mplsLdpEntityGenericIfIndexOrZeroOID = "1.3.6.1.2.1.10.166.7.1.1.1.1.4.";
    /** The storage type for this conceptual row. Conceptual rows having the value 'permanent(4)' need not allow write-access to any columnar objects in 
     * the row. */
    static final String mplsLdpEntityGenericLRStorageTypeOID = "1.3.6.1.2.1.10.166.7.1.1.1.1.5.";
    /** The status of this conceptual row.  */
    static final String mplsLdpEntityGenericLRRowStatusOID = "1.3.6.1.2.1.10.166.7.1.1.1.1.6.";

    /** Flags */
    boolean mplsInterfaceFlag = true;
    boolean mplsInterfacePerfFlag = true;
    boolean mplsInSegmentIndexNextFlag = true;
    boolean mplsInSegmentFlag = true;
    boolean mplsInSegmentPerfFlag = true;
    boolean mplsOutSegmentIndexNextFlag = true;
    boolean mplsOutSegmentFlag = true;
    boolean mplsOutSegmentPerfFlag = true;
    boolean mplsXCIndexNextFlag = true;
    boolean mplsXCTableFlag = true;
    boolean mplsMaxLabelStackDepthFlag = true;
    boolean mplsLabelStackIndexNextFlag = true;
    boolean mplsLabelStackFlag = true;
    boolean mplsXCNotificationsEnableFlag = true;
    boolean mplsGenericLabelFlag = true;

    /** See SNMPFactory for details about defaultOptions....like -A, -a, -v... */
    public SNMPMPLS(String defaultOptions, SNMPFactory snmp, String snmpAddress, String farmName, String clusterName,
            String moduleName) {
        this.snmp = snmp;
        this.defaultOptions = defaultOptions.trim();
        this.snmpAddress = snmpAddress.trim();
        this.farmName = farmName;
        this.clusterName = clusterName;
        this.moduleName = moduleName;
    }

    public void getResults(Vector res) {

        PDU[] pdu = snmp.run(defaultOptions + " -p GETBULK -Ow " + snmpAddress + " 1.3.6.1.2.1.10.166", null, null);
        if ((pdu == null) || (pdu.length == 0)) {
            return; // no result
        }
        eResult result = null;
        Hashtable mplsIfs = new Hashtable();
        Hashtable mplsIfsPerf = new Hashtable();
        Hashtable mplsInSegments = new Hashtable();
        Hashtable mplsInSegmentsPerf = new Hashtable();
        Hashtable mplsOutSegments = new Hashtable();
        Hashtable mplsOutSegmentsPerf = new Hashtable();
        Hashtable mplsXCTable = new Hashtable();
        Hashtable mplsStackLabel = new Hashtable();
        Hashtable mplsGenericLabel = new Hashtable();
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
                    if (mplsInterfaceFlag && checkMPLSInterfaces(mplsIfs, oid, vb)) {
                        continue;
                    }
                    if (mplsInterfacePerfFlag && checkMPLSInterfacesPerf(mplsIfsPerf, oid, vb)) {
                        continue;
                    }
                    if (mplsInSegmentIndexNextFlag && oid.startsWith(mplsInSegmentIndexNextOID)) {
                        if (result == null) {
                            result = new eResult();
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.NodeName = node;
                        }
                        result.addSet("MplsInSegmentIndexNext", param);
                        continue;
                    }
                    if (mplsInSegmentFlag && checkMPLSInSegment(mplsInSegments, oid, vb)) {
                        continue;
                    }
                    if (mplsInSegmentPerfFlag && checkMPLSInSegmentPerf(mplsInSegmentsPerf, oid, vb)) {
                        continue;
                    }
                    if (mplsOutSegmentIndexNextFlag && oid.startsWith(mplsOutSegmentIndexNextOID)) {
                        if (result == null) {
                            result = new eResult();
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.NodeName = node;
                        }
                        result.addSet("MplsOutSegmentIndexNext", param);
                        continue;
                    }
                    if (mplsOutSegmentFlag && checkMPLSOutSegment(mplsOutSegments, oid, vb)) {
                        continue;
                    }
                    if (mplsOutSegmentPerfFlag && checkMPLSOutSegmentPerf(mplsOutSegmentsPerf, oid, vb)) {
                        continue;
                    }
                    if (mplsXCIndexNextFlag && oid.startsWith(mplsXCIndexNextOID)) {
                        if (result == null) {
                            result = new eResult();
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.NodeName = node;
                        }
                        result.addSet("MplsXCIndexNext", param);
                        continue;
                    }
                    if (mplsXCTableFlag && checkMPLSXCTable(mplsXCTable, oid, vb)) {
                        continue;
                    }
                    if (mplsMaxLabelStackDepthFlag && oid.startsWith(mplsMaxLabelStackDepthOID)) {
                        if (result == null) {
                            result = new eResult();
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.NodeName = node;
                        }
                        result.addSet("MplsMaxLabelStackDepth",
                                Long.valueOf(((UnsignedInteger32) vb.getVariable()).getValue()));
                        continue;
                    }
                    if (mplsLabelStackIndexNextFlag && oid.startsWith(mplsLabelStackIndexNextOID)) {
                        if (result == null) {
                            result = new eResult();
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.NodeName = node;
                        }
                        result.addSet("MplsLabelStackIndexNext", param);
                        continue;
                    }
                    if (mplsLabelStackFlag && checkMPLSStackLabel(mplsStackLabel, oid, vb)) {
                        continue;
                    }
                    if (mplsXCNotificationsEnableFlag && oid.startsWith(mplsXCNotificationsEnableOID)) {
                        String truth = (param.equals("1") ? "True" : "False");
                        if (result == null) {
                            result = new eResult();
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.NodeName = node;
                        }
                        result.addSet("MplsXCNotificationsEnable", truth);
                        continue;
                    }
                    if (mplsGenericLabelFlag && checkMPLSGenericLabel(mplsGenericLabel, oid, vb)) {
                        continue;
                    }
                } catch (Throwable t) {
                    logger.warning(t.getLocalizedMessage());
                }
            }
        }
        if ((result != null) && (result.param_name != null) && (result.param_name.length != 0)) {
            res.add(result);
        }
        if (mplsIfs.size() != 0) {
            for (Enumeration en = mplsIfs.keys(); en.hasMoreElements();) {
                String id = (String) en.nextElement();
                if (id == null) {
                    continue;
                }
                eResult result1 = (eResult) mplsIfs.get(id);
                if ((result1 != null) && (result1.param_name != null) && (result1.param_name.length != 0)) {
                    res.add(result1);
                }
            }
        }
        if (mplsIfsPerf.size() != 0) {
            for (Enumeration en = mplsIfsPerf.keys(); en.hasMoreElements();) {
                String id = (String) en.nextElement();
                if (id == null) {
                    continue;
                }
                eResult result1 = (eResult) mplsIfsPerf.get(id);
                if ((result1 != null) && (result1.param_name != null) && (result1.param_name.length != 0)) {
                    res.add(result1);
                }
            }
        }
        if (mplsInSegments.size() != 0) {
            for (Enumeration en = mplsInSegments.keys(); en.hasMoreElements();) {
                String id = (String) en.nextElement();
                if (id == null) {
                    continue;
                }
                eResult result1 = (eResult) mplsInSegments.get(id);
                if ((result1 != null) && (result1.param_name != null) && (result1.param_name.length != 0)) {
                    res.add(result1);
                }
            }
        }
        if (mplsInSegmentsPerf.size() != 0) {
            for (Enumeration en = mplsInSegmentsPerf.keys(); en.hasMoreElements();) {
                String id = (String) en.nextElement();
                if (id == null) {
                    continue;
                }
                eResult result1 = (eResult) mplsInSegmentsPerf.get(id);
                if ((result1 != null) && (result1.param_name != null) && (result1.param_name.length != 0)) {
                    res.add(result1);
                }
            }
        }
        if (mplsOutSegments.size() != 0) {
            for (Enumeration en = mplsOutSegments.keys(); en.hasMoreElements();) {
                String id = (String) en.nextElement();
                if (id == null) {
                    continue;
                }
                eResult result1 = (eResult) mplsOutSegments.get(id);
                if ((result1 != null) && (result1.param_name != null) && (result1.param_name.length != 0)) {
                    res.add(result1);
                }
            }
        }
        if (mplsOutSegmentsPerf.size() != 0) {
            for (Enumeration en = mplsOutSegmentsPerf.keys(); en.hasMoreElements();) {
                String id = (String) en.nextElement();
                if (id == null) {
                    continue;
                }
                eResult result1 = (eResult) mplsOutSegmentsPerf.get(id);
                if ((result1 != null) && (result1.param_name != null) && (result1.param_name.length != 0)) {
                    res.add(result1);
                }
            }
        }
        if (mplsXCTable.size() != 0) {
            for (Enumeration en = mplsXCTable.keys(); en.hasMoreElements();) {
                String id = (String) en.nextElement();
                if (id == null) {
                    continue;
                }
                eResult result1 = (eResult) mplsXCTable.get(id);
                if ((result1 != null) && (result1.param_name != null) && (result1.param_name.length != 0)) {
                    res.add(result1);
                }
            }
        }
        if (mplsStackLabel.size() != 0) {
            for (Enumeration en = mplsStackLabel.keys(); en.hasMoreElements();) {
                String id = (String) en.nextElement();
                if (id == null) {
                    continue;
                }
                eResult result1 = (eResult) mplsStackLabel.get(id);
                if ((result1 != null) && (result1.param_name != null) && (result1.param_name.length != 0)) {
                    res.add(result1);
                }
            }
        }
        if (mplsGenericLabel.size() != 0) {
            for (Enumeration en = mplsGenericLabel.keys(); en.hasMoreElements();) {
                String id = (String) en.nextElement();
                if (id == null) {
                    continue;
                }
                eResult result1 = (eResult) mplsGenericLabel.get(id);
                if ((result1 != null) && (result1.param_name != null) && (result1.param_name.length != 0)) {
                    res.add(result1);
                }
            }
        }
    }

    private boolean checkMPLSInterfaces(Hashtable mplsIfs, String oid, VariableBinding vb) {

        String param = vb.getVariable().toString();
        if (oid.startsWith(mplsInterfaceLabelMinInOID)) {
            String id = oid.substring(mplsInterfaceLabelMinInOID.length());
            if (!ifsInitialized) {
                initializeInterfaces();
            }
            String ifName = (String) ifSupport.get(id);
            if (ifName == null) {
                logger.warning("Got no correspondence as interface for " + id);
                return true;
            }
            if (!mplsIfs.containsKey(ifName)) {
                eResult result = new eResult();
                mplsIfs.put(ifName, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = ifName;
                result.addSet("MplsInterfaceLabelMinIn", param);
            } else {
                eResult result = (eResult) mplsIfs.get(ifName);
                result.addSet("MplsInterfaceLabelMinIn", param);
            }
            return true;
        }
        if (oid.startsWith(mplsInterfaceLabelMaxInOID)) {
            String id = oid.substring(mplsInterfaceLabelMaxInOID.length());
            if (!ifsInitialized) {
                initializeInterfaces();
            }
            String ifName = (String) ifSupport.get(id);
            if (ifName == null) {
                logger.warning("Got no correspondence as interface for " + id);
                return true;
            }
            if (!mplsIfs.containsKey(ifName)) {
                eResult result = new eResult();
                mplsIfs.put(ifName, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = ifName;
                result.addSet("MplsInterfaceLabelMaxIn", param);
            } else {
                eResult result = (eResult) mplsIfs.get(ifName);
                result.addSet("MplsInterfaceLabelMaxIn", param);
            }
            return true;
        }
        if (oid.startsWith(mplsInterfaceLabelMinOutOID)) {
            String id = oid.substring(mplsInterfaceLabelMinOutOID.length());
            if (!ifsInitialized) {
                initializeInterfaces();
            }
            String ifName = (String) ifSupport.get(id);
            if (ifName == null) {
                logger.warning("Got no correspondence as interface for " + id);
                return true;
            }
            if (!mplsIfs.containsKey(ifName)) {
                eResult result = new eResult();
                mplsIfs.put(ifName, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = ifName;
                result.addSet("MplsInterfaceLabelMinOut", param);
            } else {
                eResult result = (eResult) mplsIfs.get(ifName);
                result.addSet("MplsInterfaceLabelMinOut", param);
            }
            return true;
        }
        if (oid.startsWith(mplsInterfaceLabelMaxOutOID)) {
            String id = oid.substring(mplsInterfaceLabelMaxOutOID.length());
            if (!ifsInitialized) {
                initializeInterfaces();
            }
            String ifName = (String) ifSupport.get(id);
            if (ifName == null) {
                logger.warning("Got no correspondence as interface for " + id);
                return true;
            }
            if (!mplsIfs.containsKey(ifName)) {
                eResult result = new eResult();
                mplsIfs.put(ifName, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = ifName;
                result.addSet("MplsInterfaceLabelMaxOut", param);
            } else {
                eResult result = (eResult) mplsIfs.get(ifName);
                result.addSet("MplsInterfaceLabelMaxOut", param);
            }
            return true;
        }
        if (oid.startsWith(mplsInterfaceTotalBandwidthOID)) {
            String id = oid.substring(mplsInterfaceTotalBandwidthOID.length());
            if (!ifsInitialized) {
                initializeInterfaces();
            }
            String ifName = (String) ifSupport.get(id);
            if (ifName == null) {
                logger.warning("Got no correspondence as interface for " + id);
                return true;
            }
            if (!mplsIfs.containsKey(ifName)) {
                eResult result = new eResult();
                mplsIfs.put(ifName, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = ifName;
                result.addSet("MplsInterfaceTotalBandwidth", param);
            } else {
                eResult result = (eResult) mplsIfs.get(ifName);
                result.addSet("MplsInterfaceTotalBandwidth", param);
            }
            return true;
        }
        if (oid.startsWith(mplsInterfaceAvailableBandwidthOID)) {
            String id = oid.substring(mplsInterfaceAvailableBandwidthOID.length());
            if (!ifsInitialized) {
                initializeInterfaces();
            }
            String ifName = (String) ifSupport.get(id);
            if (ifName == null) {
                logger.warning("Got no correspondence as interface for " + id);
                return true;
            }
            if (!mplsIfs.containsKey(ifName)) {
                eResult result = new eResult();
                mplsIfs.put(ifName, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = ifName;
                result.addSet("MplsInterfaceAvailableBandwidth", param);
            } else {
                eResult result = (eResult) mplsIfs.get(ifName);
                result.addSet("MplsInterfaceAvailableBandwidth", param);
            }
            return true;
        }
        if (oid.startsWith(mplsInterfaceLabelParticipationTypeOID)) {
            String id = oid.substring(mplsInterfaceLabelParticipationTypeOID.length());
            String str = (param.equals("0") ? "PerPlatform" : "PerInterface");
            if (!ifsInitialized) {
                initializeInterfaces();
            }
            String ifName = (String) ifSupport.get(id);
            if (ifName == null) {
                logger.warning("Got no correspondence as interface for " + id);
                return true;
            }
            if (!mplsIfs.containsKey(ifName)) {
                eResult result = new eResult();
                mplsIfs.put(ifName, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = ifName;
                result.addSet("MplsInterfaceLabelParticipationType", str);
            } else {
                eResult result = (eResult) mplsIfs.get(ifName);
                result.addSet("MplsInterfaceLabelParticipationType", str);
            }
            return true;
        }
        return false;
    }

    private void initializeInterfaces() {

        ifSupport.clear();
        Hashtable index2if = new Hashtable();
        PDU[] pdu = snmp.run(defaultOptions + " -p GETBULK " + snmpAddress + " 1.3.6.1.2.1.2", null, null);
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
                    if ((param == null) || (param.length() == 0)) {
                        continue;
                    }
                    if (oid.startsWith(ifDescrOID)) {
                        String id = oid.substring(ifDescrOID.length());
                        index2if.put(id, param);
                    }
                } catch (Throwable t) {
                    logger.warning(t.getLocalizedMessage());
                }
            }
        }
        ifsInitialized = true;
    }

    private boolean checkMPLSInterfacesPerf(Hashtable mplsIfsPerf, String oid, VariableBinding vb) {

        if (oid.startsWith(mplsInterfacePerfInLabelsInUseOID)) {
            String id = oid.substring(mplsInterfacePerfInLabelsInUseOID.length());
            if (!ifsInitialized) {
                initializeInterfaces();
            }
            String ifName = (String) ifSupport.get(id);
            if (ifName == null) {
                logger.warning("Got no correspondence as interface for " + id);
                return true;
            }
            if (!mplsIfsPerf.containsKey(ifName)) {
                eResult result = new eResult();
                mplsIfsPerf.put(ifName, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = ifName;
                result.addSet("MplsInterfacePerfInLabelsInUse", Long.valueOf(((Gauge32) vb.getVariable()).getValue()));
            } else {
                eResult result = (eResult) mplsIfsPerf.get(ifName);
                result.addSet("MplsInterfacePerfInLabelsInUse", Long.valueOf(((Gauge32) vb.getVariable()).getValue()));
            }
            return true;
        }
        if (oid.startsWith(mplsInterfacePerfInLabelLookupFailuresOID)) {
            String id = oid.substring(mplsInterfacePerfInLabelLookupFailuresOID.length());
            if (!ifsInitialized) {
                initializeInterfaces();
            }
            String ifName = (String) ifSupport.get(id);
            if (ifName == null) {
                logger.warning("Got no correspondence as interface for " + id);
                return true;
            }
            if (!mplsIfsPerf.containsKey(ifName)) {
                eResult result = new eResult();
                mplsIfsPerf.put(ifName, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = ifName;
                result.addSet("MplsInterfacePerfInLabelLookupFailures",
                        Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            } else {
                eResult result = (eResult) mplsIfsPerf.get(ifName);
                result.addSet("MplsInterfacePerfInLabelLookupFailures",
                        Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            }
            return true;
        }
        if (oid.startsWith(mplsInterfacePerfOutLabelsInUseOID)) {
            String id = oid.substring(mplsInterfacePerfOutLabelsInUseOID.length());
            if (!ifsInitialized) {
                initializeInterfaces();
            }
            String ifName = (String) ifSupport.get(id);
            if (ifName == null) {
                logger.warning("Got no correspondence as interface for " + id);
                return true;
            }
            if (!mplsIfsPerf.containsKey(ifName)) {
                eResult result = new eResult();
                mplsIfsPerf.put(ifName, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = ifName;
                result.addSet("MplsInterfacePerfOutLabelsInUse", Long.valueOf(((Gauge32) vb.getVariable()).getValue()));
            } else {
                eResult result = (eResult) mplsIfsPerf.get(ifName);
                result.addSet("MplsInterfacePerfOutLabelsInUse", Long.valueOf(((Gauge32) vb.getVariable()).getValue()));
            }
            return true;
        }
        if (oid.startsWith(mplsInterfacePerfOutFragmentedPktsOID)) {
            String id = oid.substring(mplsInterfacePerfOutFragmentedPktsOID.length());
            if (!ifsInitialized) {
                initializeInterfaces();
            }
            String ifName = (String) ifSupport.get(id);
            if (ifName == null) {
                logger.warning("Got no correspondence as interface for " + id);
                return true;
            }
            if (!mplsIfsPerf.containsKey(ifName)) {
                eResult result = new eResult();
                mplsIfsPerf.put(ifName, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = ifName;
                result.addSet("MplsInterfacePerfOutFragmentedPkts",
                        Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            } else {
                eResult result = (eResult) mplsIfsPerf.get(ifName);
                result.addSet("MplsInterfacePerfOutFragmentedPkts",
                        Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            }
            return true;
        }
        return false;
    }

    private boolean checkMPLSInSegment(Hashtable mplsInSegments, String oid, VariableBinding vb) {

        String param = vb.getVariable().toString();
        if (oid.startsWith(mplsInSegmentInterfaceOID)) {
            String id = oid.substring(mplsInSegmentInterfaceOID.length());
            String ifName = null;
            if (param.equals("0")) {
                ifName = "PerPlatform";
            } else {
                if (!ifsInitialized) {
                    initializeInterfaces();
                }
                ifName = (String) ifSupport.get(param);
                if (ifName == null) {
                    logger.warning("Got no correspondence as interface for " + param);
                    return true;
                }
            }
            if (!mplsInSegments.containsKey(id)) {
                eResult result = new eResult();
                mplsInSegments.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_IN_SEG_" + id;
                result.addSet("MplsInSegmentInterface", ifName);
            } else {
                eResult result = (eResult) mplsInSegments.get(id);
                result.addSet("MplsInSegmentInterface", ifName);
            }
            return true;
        }
        if (oid.startsWith(mplsInSegmentLabelOID)) {
            if (param.equals("0")) {
                return true; // must be ignored as specified
            }
            String id = oid.substring(mplsInSegmentLabelOID.length());
            if (!mplsInSegments.containsKey(id)) {
                eResult result = new eResult();
                mplsInSegments.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_IN_SEG_" + id;
                result.addSet("MplsInSegmentLabel", param);
            } else {
                eResult result = (eResult) mplsInSegments.get(id);
                result.addSet("MplsInSegmentLabel", param);
            }
            return true;
        }
        if (oid.startsWith(mplsInSegmentNPopOID)) {
            String id = oid.substring(mplsInSegmentNPopOID.length());
            if (!mplsInSegments.containsKey(id)) {
                eResult result = new eResult();
                mplsInSegments.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_IN_SEG_" + id;
                result.addSet("MplsInSegmentNPop", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
            } else {
                eResult result = (eResult) mplsInSegments.get(id);
                result.addSet("MplsInSegmentNPop", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
            }
            return true;
        }
        if (oid.startsWith(mplsInSegmentAddrFamilyOID)) {
            String id = oid.substring(mplsInSegmentAddrFamilyOID.length());
            if (!mplsInSegments.containsKey(id)) {
                eResult result = new eResult();
                mplsInSegments.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_IN_SEG_" + id;
                result.addSet("MplsInSegmentAddrFamily", getMPLSAdrFamily(param));
            } else {
                eResult result = (eResult) mplsInSegments.get(id);
                result.addSet("MplsInSegmentAddrFamily", getMPLSAdrFamily(param));
            }
            return true;
        }
        if (oid.startsWith(mplsInSegmentXCIndexOID)) {
            String id = oid.substring(mplsInSegmentXCIndexOID.length());
            if (!mplsInSegments.containsKey(id)) {
                eResult result = new eResult();
                mplsInSegments.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_IN_SEG_" + id;
                result.addSet("MplsInSegmentXCIndex", param);
            } else {
                eResult result = (eResult) mplsInSegments.get(id);
                result.addSet("MplsInSegmentXCIndex", param);
            }
            return true;
        }
        if (oid.startsWith(mplsInSegmentOwnerOID)) {
            String id = oid.substring(mplsInSegmentOwnerOID.length());
            if (!mplsInSegments.containsKey(id)) {
                eResult result = new eResult();
                mplsInSegments.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_IN_SEG_" + id;
                result.addSet("MplsInSegmentOwner", getMPLSOwner(param));
            } else {
                eResult result = (eResult) mplsInSegments.get(id);
                result.addSet("MplsInSegmentOwner", getMPLSOwner(param));
            }
            return true;
        }
        if (oid.startsWith(mplsInSegmentRowStatusOID)) {
            String id = oid.substring(mplsInSegmentRowStatusOID.length());
            if (!mplsInSegments.containsKey(id)) {
                eResult result = new eResult();
                mplsInSegments.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_IN_SEG_" + id;
                result.addSet("MplsInSegmentRowStatus", getMPLSRowStatus(param));
            } else {
                eResult result = (eResult) mplsInSegments.get(id);
                result.addSet("MplsInSegmentRowStatus", getMPLSRowStatus(param));
            }
            return true;
        }
        if (oid.startsWith(mplsInSegmentStorageTypeOID)) {
            String id = oid.substring(mplsInSegmentStorageTypeOID.length());
            if (!mplsInSegments.containsKey(id)) {
                eResult result = new eResult();
                mplsInSegments.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_IN_SEG_" + id;
                result.addSet("MplsInSegmentStorageType", getMPLSStorageType(param));
            } else {
                eResult result = (eResult) mplsInSegments.get(id);
                result.addSet("MplsInSegmentStorageType", getMPLSStorageType(param));
            }
            return true;
        }
        return false;
    }

    private boolean checkMPLSInSegmentPerf(Hashtable mplsInSegmentsPerf, String oid, VariableBinding vb) {

        String param = vb.getVariable().toString();
        if (oid.startsWith(mplsInSegmentPerfOctetsOID)) {
            String id = oid.substring(mplsInSegmentPerfOctetsOID.length());
            if (!mplsInSegmentsPerf.containsKey(id)) {
                eResult result = new eResult();
                mplsInSegmentsPerf.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_IN_SEG_" + id;
                result.addSet("MplsInSegmentPerfOctets", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            } else {
                eResult result = (eResult) mplsInSegmentsPerf.get(id);
                result.addSet("MplsInSegmentPerfOctets", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            }
            return true;
        }
        if (oid.startsWith(mplsInSegmentPerfPacketsOID)) {
            String id = oid.substring(mplsInSegmentPerfPacketsOID.length());
            if (!mplsInSegmentsPerf.containsKey(id)) {
                eResult result = new eResult();
                mplsInSegmentsPerf.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_IN_SEG_" + id;
                result.addSet("MplsInSegmentPerfPackets", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            } else {
                eResult result = (eResult) mplsInSegmentsPerf.get(id);
                result.addSet("MplsInSegmentPerfPackets", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            }
            return true;
        }
        if (oid.startsWith(mplsInSegmentPerfErrorsOID)) {
            String id = oid.substring(mplsInSegmentPerfErrorsOID.length());
            if (!mplsInSegmentsPerf.containsKey(id)) {
                eResult result = new eResult();
                mplsInSegmentsPerf.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_IN_SEG_" + id;
                result.addSet("MplsInSegmentPerfErrors", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            } else {
                eResult result = (eResult) mplsInSegmentsPerf.get(id);
                result.addSet("MplsInSegmentPerfErrors", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            }
            return true;
        }
        if (oid.startsWith(mplsInSegmentPerfDiscardsOID)) {
            String id = oid.substring(mplsInSegmentPerfDiscardsOID.length());
            if (!mplsInSegmentsPerf.containsKey(id)) {
                eResult result = new eResult();
                mplsInSegmentsPerf.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_IN_SEG_" + id;
                result.addSet("MplsInSegmentPerfDiscards", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            } else {
                eResult result = (eResult) mplsInSegmentsPerf.get(id);
                result.addSet("MplsInSegmentPerfDiscards", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            }
            return true;
        }
        if (oid.startsWith(mplsInSegmentPerfHCOctetsOID)) {
            String id = oid.substring(mplsInSegmentPerfHCOctetsOID.length());
            if (!mplsInSegmentsPerf.containsKey(id)) {
                eResult result = new eResult();
                mplsInSegmentsPerf.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_IN_SEG_" + id;
                result.addSet("MplsInSegmentPerfHCOctets", Long.valueOf(((Counter64) vb.getVariable()).getValue()));
            } else {
                eResult result = (eResult) mplsInSegmentsPerf.get(id);
                result.addSet("MplsInSegmentPerfHCOctets", Long.valueOf(((Counter64) vb.getVariable()).getValue()));
            }
            return true;
        }
        if (oid.startsWith(mplsInSegmentPerfDiscontinuityTimeOID)) {
            String id = oid.substring(mplsInSegmentPerfDiscontinuityTimeOID.length());
            if (!mplsInSegmentsPerf.containsKey(id)) {
                eResult result = new eResult();
                mplsInSegmentsPerf.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_IN_SEG_" + id;
                result.addSet("MplsInSegmentPerfDiscontinuityTime", param);
            } else {
                eResult result = (eResult) mplsInSegmentsPerf.get(id);
                result.addSet("MplsInSegmentPerfDiscontinuityTime", param);
            }
            return true;
        }
        return false;
    }

    private boolean checkMPLSOutSegment(Hashtable mplsOutSegments, String oid, VariableBinding vb) {

        String param = vb.getVariable().toString();
        if (oid.startsWith(mplsOutSegmentInterfaceOID)) {
            String id = oid.substring(mplsOutSegmentInterfaceOID.length());
            String ifName = null;
            if (param.equals("0")) {
                ifName = "PerPlatform";
            } else {
                if (!ifsInitialized) {
                    initializeInterfaces();
                }
                ifName = (String) ifSupport.get(param);
                if (ifName == null) {
                    logger.warning("Got no correspondence as interface for " + param);
                    return true;
                }
            }
            if (!mplsOutSegments.containsKey(id)) {
                eResult result = new eResult();
                mplsOutSegments.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_OUT_SEG_" + id;
                result.addSet("MplsOutSegmentInterface", ifName);
            } else {
                eResult result = (eResult) mplsOutSegments.get(id);
                result.addSet("MplsOutSegmentInterface", ifName);
            }
            return true;
        }
        if (oid.startsWith(mplsOutSegmentPushTopLabelOID)) {
            String id = oid.substring(mplsOutSegmentPushTopLabelOID.length());
            String truth = (param.equals("1") ? "True" : "False");
            if (!mplsOutSegments.containsKey(id)) {
                eResult result = new eResult();
                mplsOutSegments.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_OUT_SEG_" + id;
                result.addSet("MplsOutSegmentPushTopLabel", truth);
            } else {
                eResult result = (eResult) mplsOutSegments.get(id);
                result.addSet("MplsOutSegmentPushTopLabel", truth);
            }
            return true;
        }
        if (oid.startsWith(mplsOutSegmentTopLabelOID)) {
            String id = oid.substring(mplsOutSegmentTopLabelOID.length());
            if (!mplsOutSegments.containsKey(id)) {
                eResult result = new eResult();
                mplsOutSegments.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_OUT_SEG_" + id;
                result.addSet("MplsOutSegmentTopLabel", param);
            } else {
                eResult result = (eResult) mplsOutSegments.get(id);
                result.addSet("MplsOutSegmentTopLabel", param);
            }
            return true;
        }
        if (oid.startsWith(mplsOutSegmentNextHopAddrTypeOID)) {
            String id = oid.substring(mplsOutSegmentNextHopAddrTypeOID.length());
            if (!mplsOutSegments.containsKey(id)) {
                eResult result = new eResult();
                mplsOutSegments.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_OUT_SEG_" + id;
                result.addSet("MplsOutSegmentNextHopAddrType", getMPLSAdrFamily(param));
            } else {
                eResult result = (eResult) mplsOutSegments.get(id);
                result.addSet("MplsOutSegmentNextHopAddrType", getMPLSAdrFamily(param));
            }
            return true;
        }
        if (oid.startsWith(mplsOutSegmentNextHopAddrOID)) {
            String id = oid.substring(mplsOutSegmentNextHopAddrOID.length());
            if (!mplsOutSegments.containsKey(id)) {
                eResult result = new eResult();
                mplsOutSegments.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_OUT_SEG_" + id;
                result.addSet("MplsOutSegmentNextHopAddr", param);
            } else {
                eResult result = (eResult) mplsOutSegments.get(id);
                result.addSet("MplsOutSegmentNextHopAddr", param);
            }
            return true;
        }
        if (oid.startsWith(mplsOutSegmentXCIndexOID)) {
            String id = oid.substring(mplsOutSegmentXCIndexOID.length());
            if (!mplsOutSegments.containsKey(id)) {
                eResult result = new eResult();
                mplsOutSegments.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_OUT_SEG_" + id;
                result.addSet("MplsOutSegmentXCIndex", param);
            } else {
                eResult result = (eResult) mplsOutSegments.get(id);
                result.addSet("MplsOutSegmentXCIndex", param);
            }
            return true;
        }
        if (oid.startsWith(mplsOutSegmentOwnerOID)) {
            String id = oid.substring(mplsOutSegmentOwnerOID.length());
            if (!mplsOutSegments.containsKey(id)) {
                eResult result = new eResult();
                mplsOutSegments.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_OUT_SEG_" + id;
                result.addSet("MplsOutSegmentOwner", getMPLSOwner(param));
            } else {
                eResult result = (eResult) mplsOutSegments.get(id);
                result.addSet("MplsOutSegmentOwner", getMPLSOwner(param));
            }
            return true;
        }
        if (oid.startsWith(mplsOutSegmentRowStatusOID)) {
            String id = oid.substring(mplsOutSegmentRowStatusOID.length());
            if (!mplsOutSegments.containsKey(id)) {
                eResult result = new eResult();
                mplsOutSegments.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_OUT_SEG_" + id;
                result.addSet("MplsOutSegmentRowStatus", getMPLSRowStatus(param));
            } else {
                eResult result = (eResult) mplsOutSegments.get(id);
                result.addSet("MplsOutSegmentRowStatus", getMPLSRowStatus(param));
            }
            return true;
        }
        if (oid.startsWith(mplsOutSegmentStorageTypeOID)) {
            String id = oid.substring(mplsOutSegmentStorageTypeOID.length());
            if (!mplsOutSegments.containsKey(id)) {
                eResult result = new eResult();
                mplsOutSegments.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_OUT_SEG_" + id;
                result.addSet("MplsOutSegmentStorageType", getMPLSStorageType(param));
            } else {
                eResult result = (eResult) mplsOutSegments.get(id);
                result.addSet("MplsOutSegmentStorageType", getMPLSStorageType(param));
            }
            return true;
        }
        return false;
    }

    private boolean checkMPLSOutSegmentPerf(Hashtable mplsOutSegmentsPerf, String oid, VariableBinding vb) {

        String param = vb.getVariable().toString();
        if (oid.startsWith(mplsOutSegmentPerfOctetsOID)) {
            String id = oid.substring(mplsOutSegmentPerfOctetsOID.length());
            if (!mplsOutSegmentsPerf.containsKey(id)) {
                eResult result = new eResult();
                mplsOutSegmentsPerf.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_OUT_SEG_" + id;
                result.addSet("MplsOutSegmentPerfOctets", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            } else {
                eResult result = (eResult) mplsOutSegmentsPerf.get(id);
                result.addSet("MplsOutSegmentPerfOctets", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            }
            return true;
        }
        if (oid.startsWith(mplsOutSegmentPerfPacketsOID)) {
            String id = oid.substring(mplsOutSegmentPerfPacketsOID.length());
            if (!mplsOutSegmentsPerf.containsKey(id)) {
                eResult result = new eResult();
                mplsOutSegmentsPerf.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_OUT_SEG_" + id;
                result.addSet("MplsOutSegmentPerfPackets", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            } else {
                eResult result = (eResult) mplsOutSegmentsPerf.get(id);
                result.addSet("MplsOutSegmentPerfPackets", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            }
            return true;
        }
        if (oid.startsWith(mplsOutSegmentPerfErrorsOID)) {
            String id = oid.substring(mplsOutSegmentPerfErrorsOID.length());
            if (!mplsOutSegmentsPerf.containsKey(id)) {
                eResult result = new eResult();
                mplsOutSegmentsPerf.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_OUT_SEG_" + id;
                result.addSet("MplsOutSegmentPerfErrors", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            } else {
                eResult result = (eResult) mplsOutSegmentsPerf.get(id);
                result.addSet("MplsOutSegmentPerfErrors", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            }
            return true;
        }
        if (oid.startsWith(mplsOutSegmentPerfDiscardsOID)) {
            String id = oid.substring(mplsOutSegmentPerfDiscardsOID.length());
            if (!mplsOutSegmentsPerf.containsKey(id)) {
                eResult result = new eResult();
                mplsOutSegmentsPerf.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_OUT_SEG_" + id;
                result.addSet("MplsOutSegmentPerfDiscards", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            } else {
                eResult result = (eResult) mplsOutSegmentsPerf.get(id);
                result.addSet("MplsOutSegmentPerfDiscards", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
            }
            return true;
        }
        if (oid.startsWith(mplsOutSegmentPerfHCOctetsOID)) {
            String id = oid.substring(mplsOutSegmentPerfHCOctetsOID.length());
            if (!mplsOutSegmentsPerf.containsKey(id)) {
                eResult result = new eResult();
                mplsOutSegmentsPerf.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_OUT_SEG_" + id;
                result.addSet("MplsOutSegmentPerfHCOctets", Long.valueOf(((Counter64) vb.getVariable()).getValue()));
            } else {
                eResult result = (eResult) mplsOutSegmentsPerf.get(id);
                result.addSet("MplsOutSegmentPerfHCOctets", Long.valueOf(((Counter64) vb.getVariable()).getValue()));
            }
            return true;
        }
        if (oid.startsWith(mplsOutSegmentPerfDiscontinuityTimeOID)) {
            String id = oid.substring(mplsOutSegmentPerfDiscontinuityTimeOID.length());
            if (!mplsOutSegmentsPerf.containsKey(id)) {
                eResult result = new eResult();
                mplsOutSegmentsPerf.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                result.NodeName = "MPLS_OUT_SEG_" + id;
                result.addSet("MplsOutSegmentPerfDiscontinuityTime", param);
            } else {
                eResult result = (eResult) mplsOutSegmentsPerf.get(id);
                result.addSet("MplsOutSegmentPerfDiscontinuityTime", param);
            }
            return true;
        }
        return false;
    }

    private boolean checkMPLSXCTable(Hashtable mplsXCTable, String oid, VariableBinding vb) {

        String param = vb.getVariable().toString();
        if (oid.startsWith(mplsXCLspIdOID)) {
            String id = oid.substring(mplsXCLspIdOID.length());
            if (!mplsXCTable.containsKey(id)) {
                eResult result = new eResult();
                mplsXCTable.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                String p[] = id.replace('.', ',').split(",");
                if ((p == null) || (p.length != 3)) {
                    logger.warning("Incorrect field for MPLS XC " + id);
                    return true;
                }
                StringBuilder buf = new StringBuilder();
                buf.append("MPLS_XC_").append(p[0]);
                if (!p[1].equals("0")) {
                    buf.append("_SEGIN_").append(p[1]);
                }
                if (!p[2].equals("0")) {
                    buf.append("_SEGOUT_").append(p[2]);
                }
                result.NodeName = buf.toString();
                result.addSet("MplsXCLspId", param);
            } else {
                eResult result = (eResult) mplsXCTable.get(id);
                result.addSet("MplsXCLspId", param);
            }
            return true;
        }
        if (oid.startsWith(mplsXCLabelStackIndexOID)) {
            String id = oid.substring(mplsXCLabelStackIndexOID.length());
            if (!mplsXCTable.containsKey(id)) {
                eResult result = new eResult();
                mplsXCTable.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                String p[] = id.replace('.', ',').split(",");
                if ((p == null) || (p.length != 3)) {
                    logger.warning("Incorrect field for MPLS XC " + id);
                    return true;
                }
                StringBuilder buf = new StringBuilder();
                buf.append("MPLS_XC_").append(p[0]);
                if (!p[1].equals("0")) {
                    buf.append("_SEGIN_").append(p[1]);
                }
                if (!p[2].equals("0")) {
                    buf.append("_SEGOUT_").append(p[2]);
                }
                result.NodeName = buf.toString();
                result.addSet("MplsXCLabelStackIndex", param);
            } else {
                eResult result = (eResult) mplsXCTable.get(id);
                result.addSet("MplsXCLabelStackIndex", param);
            }
            return true;
        }
        if (oid.startsWith(mplsXCOwnerOID)) {
            String id = oid.substring(mplsXCOwnerOID.length());
            if (!mplsXCTable.containsKey(id)) {
                eResult result = new eResult();
                mplsXCTable.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                String p[] = id.replace('.', ',').split(",");
                if ((p == null) || (p.length != 3)) {
                    logger.warning("Incorrect field for MPLS XC " + id);
                    return true;
                }
                StringBuilder buf = new StringBuilder();
                buf.append("MPLS_XC_").append(p[0]);
                if (!p[1].equals("0")) {
                    buf.append("_SEGIN_").append(p[1]);
                }
                if (!p[2].equals("0")) {
                    buf.append("_SEGOUT_").append(p[2]);
                }
                result.NodeName = buf.toString();
                result.addSet("MplsXCOwner", getMPLSOwner(param));
            } else {
                eResult result = (eResult) mplsXCTable.get(id);
                result.addSet("MplsXCOwner", getMPLSOwner(param));
            }
            return true;
        }
        if (oid.startsWith(mplsXCRowStatusOID)) {
            String id = oid.substring(mplsXCRowStatusOID.length());
            if (!mplsXCTable.containsKey(id)) {
                eResult result = new eResult();
                mplsXCTable.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                String p[] = id.replace('.', ',').split(",");
                if ((p == null) || (p.length != 3)) {
                    logger.warning("Incorrect field for MPLS XC " + id);
                    return true;
                }
                StringBuilder buf = new StringBuilder();
                buf.append("MPLS_XC_").append(p[0]);
                if (!p[1].equals("0")) {
                    buf.append("_SEGIN_").append(p[1]);
                }
                if (!p[2].equals("0")) {
                    buf.append("_SEGOUT_").append(p[2]);
                }
                result.NodeName = buf.toString();
                result.addSet("MplsXCRowStatus", getMPLSRowStatus(param));
            } else {
                eResult result = (eResult) mplsXCTable.get(id);
                result.addSet("MplsXCRowStatus", getMPLSRowStatus(param));
            }
            return true;
        }
        if (oid.startsWith(mplsXCStorageTypeOID)) {
            String id = oid.substring(mplsXCStorageTypeOID.length());
            if (!mplsXCTable.containsKey(id)) {
                eResult result = new eResult();
                mplsXCTable.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                String p[] = id.replace('.', ',').split(",");
                if ((p == null) || (p.length != 3)) {
                    logger.warning("Incorrect field for MPLS XC " + id);
                    return true;
                }
                StringBuilder buf = new StringBuilder();
                buf.append("MPLS_XC_").append(p[0]);
                if (!p[1].equals("0")) {
                    buf.append("_SEGIN_").append(p[1]);
                }
                if (!p[2].equals("0")) {
                    buf.append("_SEGOUT_").append(p[2]);
                }
                result.NodeName = buf.toString();
                result.addSet("MplsXCStorageType", getMPLSStorageType(param));
            } else {
                eResult result = (eResult) mplsXCTable.get(id);
                result.addSet("MplsXCStorageType", getMPLSStorageType(param));
            }
            return true;
        }
        if (oid.startsWith(mplsXCAdminStatusOID)) {
            String id = oid.substring(mplsXCAdminStatusOID.length());
            if (!mplsXCTable.containsKey(id)) {
                eResult result = new eResult();
                mplsXCTable.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                String p[] = id.replace('.', ',').split(",");
                if ((p == null) || (p.length != 3)) {
                    logger.warning("Incorrect field for MPLS XC " + id);
                    return true;
                }
                StringBuilder buf = new StringBuilder();
                buf.append("MPLS_XC_").append(p[0]);
                if (!p[1].equals("0")) {
                    buf.append("_SEGIN_").append(p[1]);
                }
                if (!p[2].equals("0")) {
                    buf.append("_SEGOUT_").append(p[2]);
                }
                result.NodeName = buf.toString();
                result.addSet("MplsXCAdminStatus", getMPLSAdminStatus(param));
            } else {
                eResult result = (eResult) mplsXCTable.get(id);
                result.addSet("MplsXCAdminStatus", getMPLSAdminStatus(param));
            }
            return true;
        }
        if (oid.startsWith(mplsXCOperStatusOID)) {
            String id = oid.substring(mplsXCOperStatusOID.length());
            if (!mplsXCTable.containsKey(id)) {
                eResult result = new eResult();
                mplsXCTable.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                String p[] = id.replace('.', ',').split(",");
                if ((p == null) || (p.length != 3)) {
                    logger.warning("Incorrect field for MPLS XC " + id);
                    return true;
                }
                StringBuilder buf = new StringBuilder();
                buf.append("MPLS_XC_").append(p[0]);
                if (!p[1].equals("0")) {
                    buf.append("_SEGIN_").append(p[1]);
                }
                if (!p[2].equals("0")) {
                    buf.append("_SEGOUT_").append(p[2]);
                }
                result.NodeName = buf.toString();
                result.addSet("MplsXCOperStatus", getMPLSOperStatus(param));
            } else {
                eResult result = (eResult) mplsXCTable.get(id);
                result.addSet("MplsXCOperStatus", getMPLSOperStatus(param));
            }
            return true;
        }
        return false;
    }

    private boolean checkMPLSStackLabel(Hashtable stackLabel, String oid, VariableBinding vb) {

        String param = vb.getVariable().toString();
        if (oid.startsWith(mplsLabelStackLabelOID)) {
            String id = oid.substring(mplsLabelStackLabelOID.length());
            if (!stackLabel.containsKey(id)) {
                eResult result = new eResult();
                stackLabel.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                String p[] = id.replace('.', ',').split(",");
                if ((p == null) || (p.length != 2)) {
                    logger.warning("Incorrect field for MPLS Stack Label " + id);
                    return true;
                }
                StringBuilder buf = new StringBuilder();
                buf.append("MPLS_STACK_").append(p[0]).append("_Labelndex_").append(p[1]);
                result.NodeName = buf.toString();
                result.addSet("MplsLabelStackLabel", param);
            } else {
                eResult result = (eResult) stackLabel.get(id);
                result.addSet("MplsLabelStackLabel", param);
            }
            return true;
        }
        if (oid.startsWith(mplsLabelStackRowStatusOID)) {
            String id = oid.substring(mplsLabelStackRowStatusOID.length());
            if (!stackLabel.containsKey(id)) {
                eResult result = new eResult();
                stackLabel.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                String p[] = id.replace('.', ',').split(",");
                if ((p == null) || (p.length != 2)) {
                    logger.warning("Incorrect field for MPLS Stack Label " + id);
                    return true;
                }
                StringBuilder buf = new StringBuilder();
                buf.append("MPLS_STACK_").append(p[0]).append("_Labelndex_").append(p[1]);
                result.NodeName = buf.toString();
                result.addSet("MplsLabelStackRowStatus", getMPLSRowStatus(param));
            } else {
                eResult result = (eResult) stackLabel.get(id);
                result.addSet("MplsLabelStackRowStatus", getMPLSRowStatus(param));
            }
            return true;
        }
        if (oid.startsWith(mplsLabelStackStorageTypeOID)) {
            String id = oid.substring(mplsLabelStackStorageTypeOID.length());
            if (!stackLabel.containsKey(id)) {
                eResult result = new eResult();
                stackLabel.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                String p[] = id.replace('.', ',').split(",");
                if ((p == null) || (p.length != 2)) {
                    logger.warning("Incorrect field for MPLS Stack Label " + id);
                    return true;
                }
                StringBuilder buf = new StringBuilder();
                buf.append("MPLS_STACK_").append(p[0]).append("_Labelndex_").append(p[1]);
                result.NodeName = buf.toString();
                result.addSet("MplsLabelStackStorageType", getMPLSStorageType(param));
            } else {
                eResult result = (eResult) stackLabel.get(id);
                result.addSet("MplsLabelStackStorageType", getMPLSStorageType(param));
            }
            return true;
        }
        return false;
    }

    private boolean checkMPLSGenericLabel(Hashtable genericLabel, String oid, VariableBinding vb) {

        String param = vb.getVariable().toString();
        if (oid.startsWith(mplsLdpEntityGenericLabelSpaceOID)) {
            String id = oid.substring(mplsLdpEntityGenericLabelSpaceOID.length());
            String s = (param.equals("1") ? "PerPlatform" : "PerInterface");
            if (!genericLabel.containsKey(id)) {
                eResult result = new eResult();
                genericLabel.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                String p[] = id.replace('.', ',').split(",");
                if ((p == null) || (p.length != 4)) {
                    logger.warning("Incorrect field for MPLS Stack Label " + id);
                    return true;
                }
                StringBuilder buf = new StringBuilder();
                buf.append("MPLS_GLabel_").append(p[0]).append("_Labelndex_").append(p[1]);
                result.NodeName = buf.toString();
                result.addSet("MplsLdpEntityGenericLRMin", p[2]);
                result.addSet("MplsLdpEntityGenericLRMax", p[3]);
                result.addSet("MplsLdpEntityGenericLabelSpace", s);
            } else {
                eResult result = (eResult) genericLabel.get(id);
                result.addSet("MplsLdpEntityGenericLabelSpace", s);
            }
            return true;
        }
        if (oid.startsWith(mplsLdpEntityGenericIfIndexOrZeroOID)) {
            String id = oid.substring(mplsLdpEntityGenericIfIndexOrZeroOID.length());
            if (!genericLabel.containsKey(id)) {
                String ifName = null;
                if (param.equals("0")) {
                    return true; // don't added
                }
                if (!ifsInitialized) {
                    initializeInterfaces();
                }
                ifName = (String) ifSupport.get(param);
                if (ifName == null) {
                    logger.warning("Got no correspondence as interface for " + param);
                    return true;
                }
                eResult result = new eResult();
                genericLabel.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                String p[] = id.replace('.', ',').split(",");
                if ((p == null) || (p.length != 4)) {
                    logger.warning("Incorrect field for MPLS Stack Label " + id);
                    return true;
                }
                StringBuilder buf = new StringBuilder();
                buf.append("MPLS_GLabel_").append(p[0]).append("_Labelndex_").append(p[1]);
                result.NodeName = buf.toString();
                result.addSet("MplsLdpEntityGenericLRMin", p[2]);
                result.addSet("MplsLdpEntityGenericLRMax", p[3]);
                result.addSet("MplsLdpEntityGenericIfIndex", ifName);
            } else {
                eResult result = (eResult) genericLabel.get(id);
                String ifName = null;
                if (param.equals("0")) {
                    return true; // don't added
                }
                if (!ifsInitialized) {
                    initializeInterfaces();
                }
                ifName = (String) ifSupport.get(param);
                if (ifName == null) {
                    logger.warning("Got no correspondence as interface for " + param);
                    return true;
                }
                result.addSet("MplsLdpEntityGenericIfIndex", ifName);
            }
            return true;
        }
        if (oid.startsWith(mplsLdpEntityGenericLRStorageTypeOID)) {
            String id = oid.substring(mplsLdpEntityGenericLRStorageTypeOID.length());
            if (!genericLabel.containsKey(id)) {
                eResult result = new eResult();
                genericLabel.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                String p[] = id.replace('.', ',').split(",");
                if ((p == null) || (p.length != 4)) {
                    logger.warning("Incorrect field for MPLS Stack Label " + id);
                    return true;
                }
                StringBuilder buf = new StringBuilder();
                buf.append("MPLS_GLabel_").append(p[0]).append("_Labelndex_").append(p[1]);
                result.NodeName = buf.toString();
                result.addSet("MplsLdpEntityGenericLRMin", p[2]);
                result.addSet("MplsLdpEntityGenericLRMax", p[3]);
                result.addSet("MplsLdpEntityGenericLRStorageType", getMPLSStorageType(param));
            } else {
                eResult result = (eResult) genericLabel.get(id);
                result.addSet("MplsLdpEntityGenericLRStorageType", getMPLSStorageType(param));
            }
            return true;
        }
        if (oid.startsWith(mplsLdpEntityGenericLRRowStatusOID)) {
            String id = oid.substring(mplsLdpEntityGenericLRRowStatusOID.length());
            if (!genericLabel.containsKey(id)) {
                eResult result = new eResult();
                genericLabel.put(id, result);
                result.FarmName = farmName;
                result.ClusterName = clusterName;
                result.Module = moduleName;
                String p[] = id.replace('.', ',').split(",");
                if ((p == null) || (p.length != 4)) {
                    logger.warning("Incorrect field for MPLS Stack Label " + id);
                    return true;
                }
                StringBuilder buf = new StringBuilder();
                buf.append("MPLS_GLabel_").append(p[0]).append("_Labelndex_").append(p[1]);
                result.NodeName = buf.toString();
                result.addSet("MplsLdpEntityGenericLRMin", p[2]);
                result.addSet("MplsLdpEntityGenericLRMax", p[3]);
                result.addSet("MplsLdpEntityGenericLRRowStatus", getMPLSRowStatus(param));
            } else {
                eResult result = (eResult) genericLabel.get(id);
                result.addSet("MplsLdpEntityGenericLRRowStatus", getMPLSRowStatus(param));
            }
            return true;
        }
        return false;
    }

    static final String[][] adrFamilyNumbers = new String[][] { { "0", "Other" }, { "1", "IpV4" }, { "2", "IpV6" },
            { "3", "Nsap" }, { "4", "Hdlc" }, { "5", "Bbn1822" }, { "6", "All802" }, { "7", "E163" }, { "8", "E164" },
            { "9", "F69" }, { "10", "X121" }, { "11", "Ipx" }, { "12", "Appletalk" }, { "13", "DecnetIV" },
            { "14", "BanyanVines" }, { "15", "E164withNsap" }, { "16", "Dns" }, { "17", "Distinguishedname" },
            { "18", "Asnumber" }, { "19", "Xtpoveripv4" }, { "20", "Xtpoveripv6" }, { "21", "Xtpnativemodextp" },
            { "65535", "Reserved" } };

    private String getMPLSAdrFamily(String fam) {

        if (fam == null) {
            return "Null";
        }
        for (String[] adrFamilyNumber : adrFamilyNumbers) {
            if (adrFamilyNumber[0].equals(fam)) {
                return adrFamilyNumber[1];
            }
        }
        return "Unknown";
    }

    static final String[][] mplsOwner = new String[][] { { "1", "Unknown" }, { "2", "Other" }, { "3", "Snmp" },
            { "4", "Ldp" }, { "5", "Crldp" }, { "6", "RsvpTe" }, { "7", "PolicyAgent" } };

    private String getMPLSOwner(String owner) {

        if (owner == null) {
            return "Null";
        }
        for (String[] element : mplsOwner) {
            if (element[0].equals(owner)) {
                return element[1];
            }
        }
        return "Unknown";
    }

    static final String[][] rowStatus = new String[][] { { "1", "Active" }, { "2", "NotInService" },
            { "3", "NotReady" }, { "4", "CreateAndGo" }, { "5", "CreateAndWait" }, { "6", "Destroy" } };

    private String getMPLSRowStatus(String status) {

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

    static final String[][] storageTypes = new String[][] { { "1", "Other" }, { "2", "Volatile" },
            { "3", "NonVolatile" }, { "4", "Permanent" }, { "5", "ReadOnly" } };

    private String getMPLSStorageType(String type) {

        if (type == null) {
            return "Null";
        }
        for (String[] storageType : storageTypes) {
            if (storageType[0].equals(type)) {
                return storageType[1];
            }
        }
        return "Unknown";
    }

    static final String[][] adminStatus = new String[][] { { "1", "Up" }, { "2", "Down" }, { "3", "Testing" } };

    private String getMPLSAdminStatus(String status) {

        if (status == null) {
            return "Null";
        }
        for (String[] adminStatu : adminStatus) {
            if (adminStatu[0].equals(status)) {
                return adminStatu[1];
            }
        }
        return "Unknown";
    }

    static final String[][] operStatus = new String[][] { { "1", "Up" }, { "2", "Down" }, { "3", "Testing" },
            { "4", "Unknown" }, { "5", "Dormant" }, { "6", "NotPresent" }, { "7", "LowerLayerDown" } };

    private String getMPLSOperStatus(String status) {

        if (status == null) {
            return "Null";
        }
        for (String[] operStatu : operStatus) {
            if (operStatu[0].equals(status)) {
                return operStatu[1];
            }
        }
        return "Unknown";
    }

    public boolean setMPLSXCNotificationsEnable(boolean set) {

        int tr = (set ? 1 : 2);
        PDU pdu[] = snmp.run(defaultOptions + " -p SET " + snmpAddress + " " + mplsXCNotificationsEnableOID + ".0"
                + "={s}" + tr, null, null);
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

} // end of class SNMPMPLS

