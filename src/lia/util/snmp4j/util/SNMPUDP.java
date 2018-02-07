package lia.util.snmp4j.util;

import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.eResult;
import lia.util.snmp4j.SNMPFactory;

import org.snmp4j.PDU;
import org.snmp4j.smi.Counter32;
import org.snmp4j.smi.VariableBinding;

/**
 * Class used to gather informations for UDP through the use of SNMP.
 */
public class SNMPUDP {

    private static final Logger logger = Logger.getLogger(SNMPUDP.class.getName());

    private final SNMPFactory snmp;
    private final String defaultOptions;
    private final String snmpAddress;
    private final String farmName;
    private final String clusterName;
    private final String moduleName;

    static final String node = "SNMPTCP";

    /** The total number of UDP datagrams delivered to UDP users. */
    static final String udpInDatagramsOID = "1.3.6.1.2.1.7.1.";
    /** The total number of received UDP datagrams for which there was no application at the destination port. */
    static final String udpNoPortsOID = "1.3.6.1.2.1.7.2.";
    /** The number of received UDP datagrams that could not be delivered for reasons other than the lack of an application at the destination port. */
    static final String udpInErrorsOID = "1.3.6.1.2.1.7.3.";
    /** The total number of UDP datagrams sent from this entity. */
    static final String udpOutDatagramsOID = "1.3.6.1.2.1.7.4.";

    /** Flags */
    boolean udpInDatagramsFlag = true;
    boolean udpNoPortsFlag = true;
    boolean udpInErrorsFlag = true;
    boolean udpOutDatagramsFlag = true;

    /** See SNMPFactory for details about defaultOptions....like -A, -a, -v... */
    public SNMPUDP(String defaultOptions, SNMPFactory snmp, String snmpAddress, String farmName, String clusterName,
            String moduleName) {
        this.snmp = snmp;
        this.defaultOptions = defaultOptions.trim();
        this.snmpAddress = snmpAddress.trim();
        this.farmName = farmName;
        this.clusterName = clusterName;
        this.moduleName = moduleName;
    }

    public void setFlags(boolean udpInDatagramsFlag, boolean udpNoPortsFlag, boolean udpInErrorsFlag,
            boolean udpOutDatagramsFlag) {

        this.udpInDatagramsFlag = udpInDatagramsFlag;
        this.udpNoPortsFlag = udpNoPortsFlag;
        this.udpInErrorsFlag = udpInErrorsFlag;
        this.udpOutDatagramsFlag = udpOutDatagramsFlag;
    }

    public void getResults(Vector res) {

        PDU[] pdu = snmp.run(defaultOptions + " -p GETBULK -Ow " + snmpAddress + " 1.3.6.1.2.1.7", null, null);
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
                    if (oid.startsWith(udpInDatagramsOID) && udpInDatagramsFlag) {
                        result.addSet("UdpInDatagrams", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(udpNoPortsOID) && udpNoPortsFlag) {
                        result.addSet("UdpNoPorts", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(udpInErrorsOID) && udpInErrorsFlag) {
                        result.addSet("UdpInErrors", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    } else if (oid.startsWith(udpOutDatagramsOID) && udpOutDatagramsFlag) {
                        result.addSet("UdpOutDatagrams", Long.valueOf(((Counter32) vb.getVariable()).getValue()));
                    }
                } catch (Throwable t) {
                    logger.warning(t.getLocalizedMessage());
                }
            }
        }
        if ((result != null) && (result.param_name != null) && (result.param_name.length != 0)) {
            res.add(result);
        }
    }

    /** For testing */
    public static void main(String args[]) {

        logger.setLevel(Level.FINEST);
        SNMPFactory factory = new SNMPFactory();
        SNMPUDP route = new SNMPUDP("-c private -v 2c", factory, "udp:141.85.99.136/161", "RB", "SNMPUDP", "SNMPModule");
        Vector v = new Vector();
        route.getResults(v);
        for (int i = 0; i < v.size(); i++) {
            System.out.println(v.get(i));
        }
    }

} // end of class SNMPUDP

