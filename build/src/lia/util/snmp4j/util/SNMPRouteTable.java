package lia.util.snmp4j.util;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.eResult;
import lia.util.snmp4j.SNMPFactory;

import org.snmp4j.PDU;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.VariableBinding;

/**
 * Class used to gather informations for Routing Table through the use of SNMP.
 */
public class SNMPRouteTable {

    private static final Logger logger = Logger.getLogger(SNMPRouteTable.class.getName());

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
    /** The destination IP address of this route. An entry with a value of 0.0.0.0 is considered a default route. Multiple routes to a single destination can 
     * appear in the table, but access to such multiple entries is dependent on the table-access mechanisms defined by the network management protocol 
     * in use. */
    static final String ipRouteDestOID = "1.3.6.1.2.1.4.21.1.1.";
    /** The index value which uniquely identifies the local interface through which the next hop of this route should be reached. The interface identified 
     * by a particular value of this index is the same interface as identified by the same value of ifIndex. */
    static final String ipRouteIfIndexOID = "1.3.6.1.2.1.4.21.1.2.";
    /** The primary routing metric for this route. The semantics of this metric are determined by the routing-protocol specified in the route's ipRouteProto 
     * value. If this metric is not used, its value should be set to -1.*/
    static final String ipRouteMetric1OID = "1.3.6.1.2.1.4.21.1.3.";
    /** An alternate routing metric for this route. The semantics of this metric are determined by the routing-protocol specified in the route's ipRouteProto 
     * value. If this metric is not used, its value should be set to -1. */
    static final String ipRouteMetric2OID = "1.3.6.1.2.1.4.21.1.4.";
    /** An alternate routing metric for this route. The semantics of this metric are determined by the routing-protocol specified in the route's ipRouteProto 
     * value. If this metric is not used, its value should be set to -1. */
    static final String ipRouteMetric3OID = "1.3.6.1.2.1.4.21.1.5.";
    /** An alternate routing metric for this route. The semantics of this metric are determined by the routing-protocol specified in the route's ipRouteProto 
     * value. If this metric is not used, its value should be set to -1. */
    static final String ipRouteMetric4OID = "1.3.6.1.2.1.4.21.1.6.";
    /** The IP address of the next hop of this route. (In the case of a route bound to an interface which is realized via a broadcast media, the value of 
     * this field is the agent's IP address on that interface.) */
    static final String ipRouteNextHopOID = "1.3.6.1.2.1.4.21.1.7.";
    /** The type of route. */
    static final String ipRouteTypeOID = "1.3.6.1.2.1.4.21.1.8.";
    /** The routing mechanism via which this route was learned. Inclusion of values for gateway routing protocols is not intended to imply that hosts 
     * should support those protocols. */
    static final String ipRouteProtoOID = "1.3.6.1.2.1.4.21.1.9.";
    /** The number of seconds since this route was last updated or otherwise determined to be correct. Note that no semantics of `too old' can be implied 
     * except through knowledge of the routing protocol by which the route was learned. */
    static final String ipRouteAgeOID = "1.3.6.1.2.1.4.21.1.10.";
    /** Indicate the mask to be logical-ANDed with the destination address before being compared to the value in the ipRouteDest field. If the value of 
     * the ipRouteDest is 0.0.0.0 (a default route), then the mask value is also 0.0.0.0. It should be noted that all IP routing subsystems implicitly use 
     * this mechanism. */
    static final String ipRouteMaskOID = "1.3.6.1.2.1.4.21.1.11.";
    /** An alternate routing metric for this route. The semantics of this metric are determined by the routing-protocol specified in the route's ipRouteProto 
     * value. If this metric is not used, its value should be set to -1. */
    static final String ipRouteMetric5OID = "1.3.6.1.2.1.4.21.1.12.";

    /** Flags */
    boolean ifDescrFlag = true;
    boolean ipRouteIfIndexFlag = true;
    boolean ipRouteMetric1Flag = true;
    boolean ipRouteMetric2Flag = true;
    boolean ipRouteMetric3Flag = true;
    boolean ipRouteMetric4Flag = true;
    boolean ipRouteNextHopFlag = true;
    boolean ipRouteTypeFlag = true;
    boolean ipRouteProtoFlag = true;
    boolean ipRouteAgeFlag = true;
    boolean ipRouteMaskFlag = true;
    boolean ipRouteMetric5Flag = true;

    /** See SNMPFactory for details about defaultOptions....like -A, -a, -v... */
    public SNMPRouteTable(String defaultOptions, SNMPFactory snmp, String snmpAddress, String farmName,
            String clusterName, String moduleName) {
        this.snmp = snmp;
        this.defaultOptions = defaultOptions.trim();
        this.snmpAddress = snmpAddress.trim();
        this.farmName = farmName;
        this.clusterName = clusterName;
        this.moduleName = moduleName;
    }

    public void setFlags(boolean ifDescrFlag, boolean ipRouteIfIndexFlag, boolean ipRouteMetric1Flag,
            boolean ipRouteMetric2Flag, boolean ipRouteMetric3Flag, boolean ipRouteMetric4Flag,
            boolean ipRouteNextHopFlag, boolean ipRouteTypeFlag, boolean ipRouteProtoFlag, boolean ipRouteAgeFlag,
            boolean ipRouteMaskFlag, boolean ipRouteMetric5Flag) {

        this.ifDescrFlag = ifDescrFlag;
        this.ipRouteIfIndexFlag = ipRouteIfIndexFlag;
        this.ipRouteMetric1Flag = ipRouteMetric1Flag;
        this.ipRouteMetric2Flag = ipRouteMetric2Flag;
        this.ipRouteMetric3Flag = ipRouteMetric3Flag;
        this.ipRouteMetric4Flag = ipRouteMetric4Flag;
        this.ipRouteMetric5Flag = ipRouteMetric5Flag;
        this.ipRouteNextHopFlag = ipRouteNextHopFlag;
        this.ipRouteTypeFlag = ipRouteTypeFlag;
        this.ipRouteProtoFlag = ipRouteProtoFlag;
        this.ipRouteAgeFlag = ipRouteAgeFlag;
        this.ipRouteMaskFlag = ipRouteMaskFlag;
    }

    public void getResults(Vector res) {

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
        pdu = snmp.run(defaultOptions + " -p GETBULK -Ow " + snmpAddress + " 1.3.6.1.2.1.4.21", null, null);
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
                    if (oid.startsWith(ipRouteDestOID)) {
                        String id = oid.substring(ipRouteDestOID.length());
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
                    } else if (oid.startsWith(ipRouteIfIndexOID) && ipRouteIfIndexFlag) {
                        String id = oid.substring(ipRouteIfIndexOID.length());
                        String ifName = (String) index2if.get(param);
                        if (ifName == null) {
                            continue;
                        }
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IpRouteIfName", ifName);
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IpRouteIfName", ifName);
                        }
                    } else if (oid.startsWith(ipRouteMetric1OID) && ipRouteMetric1Flag) {
                        String id = oid.substring(ipRouteMetric1OID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IpRouteMetric1", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IpRouteMetric1", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ipRouteMetric2OID) && ipRouteMetric2Flag) {
                        String id = oid.substring(ipRouteMetric2OID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IpRouteMetric2", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IpRouteMetric2", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ipRouteMetric3OID) && ipRouteMetric3Flag) {
                        String id = oid.substring(ipRouteMetric3OID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IpRouteMetric3", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IpRouteMetric3", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ipRouteMetric4OID) && ipRouteMetric4Flag) {
                        String id = oid.substring(ipRouteMetric4OID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IpRouteMetric4", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IpRouteMetric4", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ipRouteNextHopOID) && ipRouteNextHopFlag) {
                        String id = oid.substring(ipRouteNextHopOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IpRouteNextHop", param);
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IpRouteNextHop", param);
                        }
                    } else if (oid.startsWith(ipRouteTypeOID) && ipRouteTypeFlag) {
                        String id = oid.substring(ipRouteTypeOID.length());
                        String type = "Unknown";
                        if (param.equals("1")) {
                            type = "Other";
                        } else if (param.equals("2")) {
                            type = "Invalid";
                        } else if (param.equals("3")) {
                            type = "Direct";
                        } else if (param.equals("4")) {
                            type = "Indirect";
                        }
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IpRouteType", type);
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IpRouteType", type);
                        }
                    } else if (oid.startsWith(ipRouteProtoOID) && ipRouteProtoFlag) {
                        String id = oid.substring(ipRouteProtoOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IpRouteProto", getIpRouteProto(param));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IpRouteProto", getIpRouteProto(param));
                        }
                    } else if (oid.startsWith(ipRouteAgeOID) && ipRouteAgeFlag) {
                        String id = oid.substring(ipRouteAgeOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IpRouteAge", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IpRouteAge", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        }
                    } else if (oid.startsWith(ipRouteMaskOID) && ipRouteMaskFlag) {
                        String id = oid.substring(ipRouteMaskOID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IpRouteMask", param);
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IpRouteMask", param);
                        }
                    } else if (oid.startsWith(ipRouteMetric5OID) && ipRouteMetric5Flag) {
                        String id = oid.substring(ipRouteMetric5OID.length());
                        if (!ifSupport.containsKey(id)) {
                            eResult result = new eResult();
                            ifSupport.put(id, result);
                            result.FarmName = farmName;
                            result.ClusterName = clusterName;
                            result.Module = moduleName;
                            result.addSet("IpRouteMetric5", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        } else {
                            eResult result = (eResult) ifSupport.get(id);
                            result.addSet("IpRouteMetric5", Integer.valueOf(((Integer32) vb.getVariable()).getValue()));
                        }
                    }
                } catch (Throwable t) {
                    logger.warning(t.getLocalizedMessage());
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
            if ((result.NodeName != null) && (result.param_name != null) && (result.param_name.length != 0)) {
                res.add(result);
            }
        }
    }

    public boolean addRoute(String routeDest, String ifName, int routeMetric, String nextHop, String routeMask) {

        Hashtable index2if = new Hashtable();
        PDU[] pdu = snmp.run(defaultOptions + " -p GETBULK " + snmpAddress + " 1.3.6.1.2.1.2", null, null);
        if ((pdu == null) || (pdu.length == 0)) {
            return false; // no result
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
                        index2if.put(param, id);
                    }
                } catch (Throwable t) {
                    logger.warning(t.getLocalizedMessage());
                }
            }
        }
        if (!index2if.containsKey(ifName)) {
            logger.warning("Incorrect ifName " + ifName);
            return false;
        }
        StringBuilder command = new StringBuilder();
        command.append(defaultOptions).append(" -p SET ").append(snmpAddress).append(" ");
        //		command.append(".").append(ipRouteDestOID).append(routeDest).append("={s}").append(routeDest).append(" ");
        command.append(ipRouteIfIndexOID).append(routeDest).append("={i}").append(index2if.get(ifName)).append(" ");
        command.append(ipRouteMaskOID).append(routeDest).append("={a}").append(routeMask).append(" ");
        command.append(ipRouteNextHopOID).append(routeDest).append("={a}").append(nextHop).append(" ");
        command.append(ipRouteMetric1OID).append(routeDest).append("={i}").append(routeMetric).append(" ");
        pdu = snmp.run(command.toString(), null, null);
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

    final static String[][] routeProtos = new String[][] { { "1", "Other" }, { "2", "Local" }, { "3", "Netmgmt" },
            { "4", "Icmp" }, { "5", "Egp" }, { "6", "Ggp" }, { "7", "Hello" }, { "8", "Rip" }, { "9", "Is-is" },
            { "10", "Es-is" }, { "11", "CiscoIgrp" }, { "12", "BbnSpfIgp" }, { "13", "Ospf" }, { "14", "Bgp" } };

    private String getIpRouteProto(String type) {

        if (type == null) {
            return "Null";
        }
        for (String[] routeProto : routeProtos) {
            if (routeProto[0].equals(type)) {
                return routeProto[1];
            }
        }
        return type;
    }

    /** For testing */
    public static void main(String args[]) {

        logger.setLevel(Level.FINEST);
        SNMPFactory factory = new SNMPFactory();
        SNMPRouteTable route = new SNMPRouteTable("-c private -v 2c", factory, "udp:141.85.99.136/161", "RB",
                "SNMPRouteTable", "SNMPModule");
        Vector v = new Vector();
        route.getResults(v);
        for (int i = 0; i < v.size(); i++) {
            System.out.println(v.get(i));
        }
        route.addRoute("100.100.100.100", "lo", 0, "141.85.99.169", "255.255.255.192");
    }

} // end of class SNMPRouteTable

