package lia.Monitor.monitor;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.DynamicThreadPoll.SchJob;
import snmp.SNMPBadValueException;
import snmp.SNMPGetException;
import snmp.SNMPObject;
import snmp.SNMPObjectIdentifier;
import snmp.SNMPSequence;
import snmp.SNMPVarBindList;
import snmp.SNMPv1CommunicationInterface;

public abstract class snmpMon2 extends SchJob {

    /**
     *
     */
    private static final long serialVersionUID = -4290767534785327019L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(snmpMon2.class.getName());
    protected final static int NULL_INT = -1;

    protected InetAddress iaRemoteAddress = null; // not initilized
    protected int iRemotePort = NULL_INT; // not initilized
    protected int iReadTimeOut = NULL_INT; // not initilized
    protected InetAddress iaLocalAddress = null; // not initilized
    protected int snmpReceiveBufferSize = NULL_INT;

    protected static final int SNMPV1 = 0;
    protected static final int SNMPV2 = 1;
    protected int iSNMPVersion = NULL_INT; // not initilized

    protected String sCommunity;

    protected SNMPv1CommunicationInterface snmpComInterface;

    protected MNode Node;

    protected MonModuleInfo info;

    private String[] sOid;

    // counter type - 32bit or 64bit
    protected static final int MODE64 = 0;
    protected static final int MODE32 = 1;

    // useful OID used to retrieve SNMP agent sysUpTime
    protected final static String timeTicksOID = "1.3.6.1.2.1.1.3.0";

    public snmpMon2() {
        super();
        this.info = new MonModuleInfo();
        this.info.setState(0);
    }

    public snmpMon2(String sOid) {
        super();
        this.info = new MonModuleInfo();
        this.info.setState(0);
        this.sOid = new String[1];
        this.sOid[0] = sOid;
    }

    public snmpMon2(String[] sOid) {
        super();
        this.info = new MonModuleInfo();
        this.info.setState(0);
        this.sOid = sOid;
    }

    /**
     * @param node
     * @throws SocketException
     *             if the socket could not be opened, or the socket could not
     *             bind to the SNMP local port.
     */
    protected void init(MNode node) throws SocketException {
        this.Node = node;
        // if iaRemoteAddress it's not set till now get the default value
        // specified in ml.properties
        if (iaRemoteAddress == null) {
            try {
                iaRemoteAddress = InetAddress.getByName(Node.getIPaddress());
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Could not determine IP Address for remote SNMP Agent", t);
                info.addErrorCount();
                info.setState(1); // error
                info.setErrorDesc(" Could not determine IP Address for remote SNMP Agent");
            }
        }
        // if iRemotePort it's not set till now get the default value specified
        // in ml.properties
        if (iRemotePort == NULL_INT) {
            int iport = -1;
            try {
                iport = Integer.valueOf(AppConfig.getProperty("lia.Monitor.SNMP_port", "-1").trim()).intValue();
            } catch (Throwable t) {
                iport = -1;
            }
            if (iport != -1) {
                iRemotePort = iport;
            } else {
                iRemotePort = SNMPv1CommunicationInterface.DEFAULT_SNMPPORT;
            }
        }

        // if sSNMPVersion it's not set till now get the default value specified
        // in ml.properties
        if (iSNMPVersion == NULL_INT) {
            String snmpVersionS = AppConfig.getProperty("lia.Monitor.SNMP_version", "1");
            iSNMPVersion = SNMPV1;
            if (snmpVersionS != null) {
                if (snmpVersionS.indexOf("2") != -1) {
                    logger.log(Level.INFO, "Using SNMP V2");
                    iSNMPVersion = SNMPV2;
                }
            }
        }

        // if sCommunity it's not set till now get the default value specified
        // in ml.properties
        if (sCommunity == null) {
            String community = AppConfig.getProperty("lia.Monitor.SNMP_community", "public");
            sCommunity = community.trim();
        }

        // if isLocalAddress it's not set till now get the default value
        // specified in ml.properties

        if (iaLocalAddress == null) {
            try {
                String sLADDRD = AppConfig.getProperty("lia.Monitor.SNMP_localAddress", null);
                if (sLADDRD != null) {
                    try {
                        iaLocalAddress = InetAddress.getByName(sLADDRD);
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Cannot determine InetAddr for SNMP_localAddress " + sLADDRD, t);
                        iaLocalAddress = null;
                    }
                }
            } catch (Throwable t) {
                iaLocalAddress = null;
            }
        }

        snmpComInterface = new SNMPv1CommunicationInterface(iSNMPVersion, iaRemoteAddress, iRemotePort, sCommunity,
                iaLocalAddress);

        if (iReadTimeOut == NULL_INT) {
            try {
                iReadTimeOut = Integer.valueOf(AppConfig.getProperty("lia.Monitor.SNMP_timeout", "-1").trim())
                        .intValue();
            } catch (Throwable t) {
                iReadTimeOut = -1;
            }
        }

        if (snmpReceiveBufferSize == NULL_INT) {
            try {
                snmpReceiveBufferSize = AppConfig.geti("lia.Monitor.SNMP_receiveBufferSize", NULL_INT);
            } catch (Throwable t) {
                snmpReceiveBufferSize = -1;
            }
        }

        if (snmpReceiveBufferSize > 0) {
            snmpComInterface.setReceiveBufferSize(snmpReceiveBufferSize);
            logger.log(Level.INFO,
                    "Set snmpReceiveBufferSize (" + snmpReceiveBufferSize + ") for " + this.getClusterName() + "/"
                            + this.getNode());
        }

        if (iReadTimeOut > 0) {
            snmpComInterface.setSocketTimeout(iReadTimeOut);
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [SNMP] snmpComInterface for " + iaRemoteAddress.getHostAddress()
                    + " receiveBufferSize: " + snmpComInterface.getReceiveBufferSize() + " bytes");
        }
    }

    /**
     * @see lia.Monitor.monitor.MonitoringModule#isRepetitive()
     */
    public boolean isRepetitive() {
        return true;
    }

    public MNode getNode() {
        return Node;
    }

    public String getClusterName() {
        return Node.getClusterName();
    }

    public String getFarmName() {
        return Node.getFarmName();
    }

    /**
     * @see lia.Monitor.monitor.MonitoringModule#getInfo()
     */
    public MonModuleInfo getInfo() {
        return this.info;
    }

    /**
     * @see lia.util.DynamicThreadPoll.SchJobInt#stop()
     */
    @Override
    public boolean stop() {
        try {
            snmpComInterface.closeConnection();
        } catch (SocketException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "[SNMP] Could not close comm.interface", e);
            }
            return false;
        }
        return true;
    }

    public String getTaskName() {
        return info.getName();
    }

    // helper methods used for SNMP data gathering
    /**
     * "bulk-get"
     *
     * @param itemIDs -
     *            OIDs array
     *
     * @throws SNMPGetException
     * @throws SNMPBadValueException
     * @throws IOException
     *
     * @returns a Map of OID,Value (SNMPObject)
     */
    protected Map<String, SNMPObject> snmpBulkGet(final String[] itemIDs) throws SNMPGetException,
            SNMPBadValueException, IOException {
        Map<String, SNMPObject> mResults = new HashMap<String, SNMPObject>();
        // the getMIBEntry method of the communications interface returns an
        // SNMPVarBindList
        // object; this is essentially a Vector of SNMP (OID,value) pairs. In
        // this case, the
        // returned Vector has several pairs inside it.
        if (logger.isLoggable(Level.FINEST)) {
            StringBuilder sb = new StringBuilder("[SNMP] Request OIDS: ");
            for (String itemID : itemIDs) {
                sb.append(itemID + " ");
            }
            logger.finest(sb.toString());
        }
        // make sure the socket is up
        snmpComInterface.reOpenSocketIfClosed();
        // make request
        SNMPVarBindList results = snmpComInterface.getMIBEntry(itemIDs);

        // extract the (OID,value) pairs from the SNMPVarBindList; each pair is
        // just a two-element
        // SNMPSequence
        SNMPSequence pair;

        SNMPObject snmpValue;
        SNMPObjectIdentifier snmpOID;

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < results.size(); i++) {
            pair = (SNMPSequence) (results.getSNMPObjectAt(i));
            // extract the object identifier from the pair; it's the first
            // element in the sequence
            snmpOID = (SNMPObjectIdentifier) pair.getSNMPObjectAt(0);
            // extract the corresponding value from the pair; it's the second
            // element in the sequence
            snmpValue = pair.getSNMPObjectAt(1);
            if (logger.isLoggable(Level.FINEST)) {
                Object snmpV = snmpValue.getValue();
                if ((snmpV != null) && (snmpV instanceof BigInteger)) {
                    sb.append("\n").append(snmpOID.toString()).append(" = ").append(((BigInteger) snmpV).toString());
                }
            }
            mResults.put(snmpOID.toString(), snmpValue);

        }

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "\n\n[snmpMon2] Got:\n" + sb.toString());
        }
        return mResults;
    }

    protected Map<String, SNMPObject> snmpBulkGet() throws SNMPGetException, SNMPBadValueException, IOException {
        return snmpBulkGet(sOid);
    }

    /**
     * Retrieves the entire MIB table corresponding to a base OID
     *
     * @param baseID
     * @throws SNMPGetException
     * @throws IOException
     * @throws SNMPBadValueException
     *
     * @return a OID->SNMPValue map
     */
    protected Map<String, SNMPObject> getMIBTable(final String baseID) throws SNMPGetException, IOException,
            SNMPBadValueException {

        Map<String, SNMPObject> mMIBTable = new HashMap<String, SNMPObject>();
        // make sure the socket is up
        snmpComInterface.reOpenSocketIfClosed();
        SNMPVarBindList tableVars = snmpComInterface.retrieveMIBTable(baseID);
        SNMPSequence pair;
        SNMPObjectIdentifier snmpOID;
        SNMPObject snmpValue;

        // extract the (OID,value) pairs from the SNMPVarBindList; each pair is
        // just a two-element
        // SNMPSequence
        for (int i = 0; i < tableVars.size(); i++) {
            pair = (SNMPSequence) (tableVars.getSNMPObjectAt(i));

            // extract the object identifier from the pair; it's the first
            // element in the sequence
            snmpOID = (SNMPObjectIdentifier) pair.getSNMPObjectAt(0);
            // extract the corresponding value from the pair; it's the second
            // element in the sequence
            snmpValue = pair.getSNMPObjectAt(1);

            mMIBTable.put(snmpOID.toString(), snmpValue);
        }

        return mMIBTable;

    }

    protected Map<String, SNMPObject> getMIBTable(final String[] baseID) throws SNMPGetException, IOException,
            SNMPBadValueException {

        Map<String, SNMPObject> mMIBTable = new HashMap<String, SNMPObject>();
        // make sure the socket is up
        snmpComInterface.reOpenSocketIfClosed();
        SNMPVarBindList tableVars = snmpComInterface.retrieveMIBTable(baseID);
        SNMPSequence pair;
        SNMPObjectIdentifier snmpOID;
        SNMPObject snmpValue;

        // extract the (OID,value) pairs from the SNMPVarBindList; each pair is
        // just a two-element
        // SNMPSequence
        for (int i = 0; i < tableVars.size(); i++) {
            pair = (SNMPSequence) (tableVars.getSNMPObjectAt(i));

            // extract the object identifier from the pair; it's the first
            // element in the sequence
            snmpOID = (SNMPObjectIdentifier) pair.getSNMPObjectAt(0);
            // extract the corresponding value from the pair; it's the second
            // element in the sequence
            snmpValue = pair.getSNMPObjectAt(1);

            mMIBTable.put(snmpOID.toString(), snmpValue);
        }

        return mMIBTable;

    }

    @Override
    public String toString() {
        StringBuilder sMe = new StringBuilder();
        sMe.append("Version:").append(iSNMPVersion == SNMPV2 ? "v2c" : "v1");
        sMe.append("/Community:").append(sCommunity);
        sMe.append("/AgentAddress:").append(iaRemoteAddress.getHostAddress());
        sMe.append("/AgentPort:").append(iRemotePort);
        sMe.append("/LocalAddress:").append(iaLocalAddress);
        sMe.append("/Timeout:").append(iReadTimeOut);
        sMe.append("/rcvBufferSize:").append(snmpReceiveBufferSize);
        return sMe.toString();
    }
}
