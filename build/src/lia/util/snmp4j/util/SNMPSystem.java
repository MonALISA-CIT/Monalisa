package lia.util.snmp4j.util;

import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.eResult;
import lia.util.snmp4j.SNMPFactory;

import org.snmp4j.PDU;
import org.snmp4j.smi.VariableBinding;

/**
 * Class used to gather informations for System through the use of SNMP.
 */
public class SNMPSystem {

    private static final Logger logger = Logger.getLogger(SNMPSystem.class.getName());

    private final SNMPFactory snmp;
    private final String defaultOptions;
    private final String snmpAddress;
    private final String farmName;
    private final String clusterName;
    private final String moduleName;

    static final String node = "SNMPSystem";

    /** A textual description of the entity. This value should include the full name and version identification of the system's hardware type, software 
     * operating-system, and networking software. */
    static final String sysDescrOID = "1.3.6.1.2.1.1.1";
    /** The time (in hundredths of a second) since the network management portion of the system was last re-initialized. Type=TimeTicks*/
    static final String sysUpTimeOID = "1.3.6.1.2.1.1.3";
    /** The textual identification of the contact person for this managed node, together with information on how to contact this person. If no contact 
     * information is known, the value is the zero-length string. */
    static final String sysContactOID = "1.3.6.1.2.1.1.4";
    /** An administratively-assigned name for this managed node. By convention, this is the node's fully-qualified domain name. If the name is unknown, 
     * the value is the zero-length string. */
    static final String sysNameOID = "1.3.6.1.2.1.1.5";
    /** The physical location of this node (e.g., `telephone closet, 3rd floor'). If the location is unknown, the value is the zero-length string. */
    static final String sysLocationOID = "1.3.6.1.2.1.1.6";

    /** Flags */
    boolean sysDescrFlag = true;
    boolean sysUpTimeFlag = true;
    boolean sysContactFlag = true;
    boolean sysNameFlag = true;
    boolean sysLocationFlag = true;

    /** See SNMPFactory for details about defaultOptions....like -A, -a, -v... */
    public SNMPSystem(String defaultOptions, SNMPFactory snmp, String snmpAddress, String farmName, String clusterName,
            String moduleName) {
        this.snmp = snmp;
        this.defaultOptions = defaultOptions.trim();
        this.snmpAddress = snmpAddress.trim();
        this.farmName = farmName;
        this.clusterName = clusterName;
        this.moduleName = moduleName;
    }

    public void setFlags(boolean sysDescrFlag, boolean sysUpTimeFlag, boolean sysContactFlag, boolean sysNameFlag,
            boolean sysLocationFlag) {

        this.sysDescrFlag = sysDescrFlag;
        this.sysUpTimeFlag = sysUpTimeFlag;
        this.sysContactFlag = sysContactFlag;
        this.sysNameFlag = sysNameFlag;
        this.sysLocationFlag = sysLocationFlag;
    }

    public void getResults(Vector res) {

        PDU[] pdu = snmp.run(defaultOptions + " -p GETBULK " + snmpAddress + " 1.3.6.1.2.1.1", null, null);
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
                    if ((param == null) || (param.length() == 0)) {
                        continue;
                    }
                    if (result == null) {
                        result = new eResult();
                        result.FarmName = farmName;
                        result.ClusterName = clusterName;
                        result.Module = moduleName;
                        result.NodeName = node;
                    }
                    if (oid.startsWith(sysDescrOID) && sysDescrFlag) {
                        result.addSet("SysDesc", param);
                    } else if (oid.startsWith(sysUpTimeOID) && sysUpTimeFlag) {
                        result.addSet("SysUpTime", param);
                    } else if (oid.startsWith(sysContactOID) && sysContactFlag) {
                        result.addSet("SysContact", param);
                    } else if (oid.startsWith(sysNameOID) && sysNameFlag) {
                        result.addSet("SysName", param);
                    } else if (oid.startsWith(sysLocationOID) && sysLocationFlag) {
                        result.addSet("SysLocation", param);
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

    public boolean setSysLocation(String location) {

        PDU pdu[] = snmp.run(defaultOptions + " -p SET " + snmpAddress + " " + sysLocationOID + "={s}" + location,
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

    public boolean setSysContact(String contact) {

        PDU pdu[] = snmp.run(defaultOptions + " -p SET " + snmpAddress + " " + sysContactOID + "={s}" + contact, null,
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

    public boolean setSysName(String name) {

        PDU pdu[] = snmp.run(defaultOptions + " -p SET " + snmpAddress + " " + sysNameOID + "={s}" + name, null, null);
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

} // end of class SNMPSystem

