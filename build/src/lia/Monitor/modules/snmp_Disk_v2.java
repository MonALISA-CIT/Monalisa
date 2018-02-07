package lia.Monitor.modules;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.snmpMon2;
import lia.util.ntp.NTPDate;
import snmp.SNMPBadValueException;
import snmp.SNMPGetException;
import snmp.SNMPInteger;
import snmp.SNMPObject;
import snmp.SNMPObjectIdentifier;
import snmp.SNMPSequence;
import snmp.SNMPVarBindList;

/**
 * Ver2 of Disk-Space monitoring module. (the information is reported for each partition separately)
 * Note that this module needs SNMP ver.2, so, be kind
 * and set lia.Monitor.SNMP_version="2c" in your configuration
 * 
 * @author adim
 * @since MonALISA 1.5.6
 */

public class snmp_Disk_v2 extends snmpMon2 implements MonitoringModule {

    private static final long serialVersionUID = 7251387720407051147L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(snmp_Disk_v2.class.getName());

    static public String ModuleName = "snmp_Disk_v2";

    static public String[] ResTypes = new String[0]; // dynamic

    static String sOidPart = "1.3.6.1.4.1.2021.9.1.2";

    static String sOidFree = "1.3.6.1.4.1.2021.9.1.7";

    static String sOidUsed = "1.3.6.1.4.1.2021.9.1.8";

    static String[] aResTypesOids = new String[] { sOidFree, sOidUsed };

    static String[] aResTypesSuffix = new String[] { "_FreeDsk", "_UsedDsk" };

    static double[] aResTypesSum = new double[2];

    static public String OsName = "*";

    //how often to update the partition information
    private static final int UPDATE_CONFIGURATION_RUNs = 1;

    public snmp_Disk_v2() {
        super();
    }

    @Override
    public MonModuleInfo init(MNode node, String args) {
        try {
            // ver 2C is required, so set it as default
            iSNMPVersion = SNMPV2;
            // set module specific configuration parameters
            if ((args != null) && (args.length() > 0)) {
                init_args(args);
            }
            // module init
            init(node);
        } catch (SocketException e) {
            // severe init error, cannot continue..
            logger.log(
                    Level.SEVERE,
                    "[SNMP] CommInterface could not be initialized (Network problems or SNMP v2 not supported by SNMP Agent",
                    e);
            info.addErrorCount();
            info.setState(1); // error
            info.setErrorDesc("CommInterface could not be initialized");
        } catch (Exception e) { // in case of other exception ... set error
            // state in module
            logger.log(Level.SEVERE, "[SNMP]Cannot parse module parameters. Please check module configuration", e);
            info.addErrorCount();
            info.setState(1); // error
            info.setErrorDesc("Cannot load parameters for SNMP module");
        }

        info.ResTypes = ResTypes;
        info.name = ModuleName;
        return info;
    }

    @Override
    public String[] ResTypes() {
        return ResTypes;
    }

    @Override
    public String getOsName() {
        return OsName;
    }

    private int runNo = 0;

    private final Map mDskPath_DiskIndex = new HashMap();

    @Override
    public Object doProcess() throws Exception {

        Vector vResults = new Vector();

        if (runNo++ == 0) {
            try {
                //update configuration map and get the removal results
                vResults.addAll(updateConfiguration());
            } catch (Throwable t) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING,
                            "[snmp_Disk_v2] Error while trying to read disks partition information. Using the old one:["
                                    + mDskPath_DiskIndex + "]", t);
                }
            }
        }
        if (runNo >= UPDATE_CONFIGURATION_RUNs) {
            runNo = 0;
        }

        if (mDskPath_DiskIndex.size() <= 0) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "[snmp_Disk_v2] No partition found. Returning....");
            }
            return ((vResults != null) && (vResults.size() > 0)) ? vResults : null;
        }

        // Prepare the REQUEST
        List oids = new ArrayList();
        String sPartition;
        int iIndex;
        for (Iterator iter = mDskPath_DiskIndex.entrySet().iterator(); iter.hasNext();) {
            Map.Entry element = (Map.Entry) iter.next();
            sPartition = (String) element.getKey();
            iIndex = ((Integer) element.getValue()).intValue();
            for (String aResTypesOid : aResTypesOids) {
                oids.add(aResTypesOid + "." + iIndex);
            }
        }

        // System.out.println("++OIDS requested:"+oids);
        // make the SNMP Request
        String[] itemIDs = (String[]) oids.toArray(new String[oids.size()]);
        oids.clear();

        Map mSNMPResults = super.snmpBulkGet(itemIDs);
        // System.out.println("++SNMP response:"+mSNMPResults);
        // iterate over the currentConfiguration and make a result with SNMP
        // data

        aResTypesSum[0] = aResTypesSum[1] = 0d;
        long lNow = NTPDate.currentTimeMillis();
        for (Iterator iter = mDskPath_DiskIndex.entrySet().iterator(); iter.hasNext();) {
            Map.Entry element = (Map.Entry) iter.next();
            sPartition = (String) element.getKey();
            iIndex = ((Integer) element.getValue()).intValue();

            Result r = new Result();
            r.FarmName = Node.getFarmName();
            r.ClusterName = Node.getClusterName();
            r.NodeName = Node.getName();
            r.Module = ModuleName;
            r.time = lNow;
            Object oValue;
            double dValue;
            for (int i = 0; i < aResTypesOids.length; i++) {
                String sPartKey = aResTypesOids[i] + "." + iIndex;
                // System.out.println( "search for: "+sPartKey);
                if (mSNMPResults.containsKey(sPartKey)) {
                    oValue = mSNMPResults.get(sPartKey);
                    // System.out.println( "found
                    // "+sPartKey+":"+oValue.getClass().toString());
                    if ((oValue == null) || !(oValue instanceof SNMPInteger)
                            || !(((SNMPInteger) oValue).getValue() instanceof BigInteger)) {
                        continue;
                    }

                    dValue = ((BigInteger) ((SNMPInteger) oValue).getValue()).doubleValue() / 1000000d;
                    if (dValue >= 0) {
                        r.addSet(sPartition + aResTypesSuffix[i], dValue);
                        aResTypesSum[i] += dValue;
                    }
                }
            }
            // System.out.println("result intermiediar:"+r);
            vResults.add(r);
        }

        // SUM-UP
        Result r = new Result();
        r.FarmName = Node.getFarmName();
        r.ClusterName = Node.getClusterName();
        r.NodeName = Node.getName();
        r.Module = ModuleName;
        r.time = lNow;
        r.addSet("Total" + aResTypesSuffix[0], aResTypesSum[0]);
        r.addSet("Total" + aResTypesSuffix[1], aResTypesSum[1]);
        vResults.add(r);

        if ((vResults != null) && (vResults.size() > 0)) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "[snmp_Disk_v2] Results:" + vResults);
            }
        }
        return vResults;
    }

    private Vector updateConfiguration() throws Exception {

        final Vector vRemovalResults = new Vector();

        Map mNewDskPath_DiskIndex = null;
        try {
            mNewDskPath_DiskIndex = getPartsIndexMap();
        } catch (Throwable t) {
            throw new Exception(
                    "[snmp_Disk_v2] Error while trying to read disks partition information. Using the old one:["
                            + mDskPath_DiskIndex + "]", t);
        }

        String sPartition;
        int iIndex;

        for (Iterator iter = mDskPath_DiskIndex.entrySet().iterator(); iter.hasNext();) {
            Map.Entry element = (Map.Entry) iter.next();
            sPartition = (String) element.getKey();
            // TODO test if getValue() is instaceof SNMPGauge32
            iIndex = ((Integer) element.getValue()).intValue();

            // Update the index from the new config
            if (mNewDskPath_DiskIndex.containsKey(sPartition)) {
                mDskPath_DiskIndex.put(sPartition, mNewDskPath_DiskIndex.get(sPartition));
                // remove already found partition
                mNewDskPath_DiskIndex.remove(sPartition);
            } else // partition removed
            {
                // remove it from configuration
                iter.remove();
                // send an removal eResult
                String[] sRemovedParamsName = new String[] { sPartition + "_FreeDsk", sPartition + "_UsedDsk" };
                Object[] oNullValues = new Object[] { null, null };
                eResult result = new eResult(Node.getFarmName(), Node.getClusterName(), Node.getName(), ModuleName,
                        sRemovedParamsName);
                result.param = oNullValues;
                vRemovalResults.addElement(result);
            }
        }
        // after all, add the *new* entries
        mDskPath_DiskIndex.putAll(mNewDskPath_DiskIndex);

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "[snmp_Disk_v2] Disks partition information updated:[" + mDskPath_DiskIndex + "]");
        }

        return vRemovalResults;
    }

    /*
         * utils methods
         */

    /*
         * configuration template: snmp_Disk_v2{params}%30 params:
         * [SNMP_community=mycommunity,SNMP_Version=2c,SNMP_RemoteAddress=x.x.x.x,SNMP_RemotePort=2161,SNMP_LocalAddress=x.x.x.x,SNMP_Timeout=xx,CanSuspend=false];if1=IF1_NAME;if2=IF2_NAME
         * 
         */
    private void init_args(String list) throws Exception {
        String[] splittedArgs = list.split("(\\s)*;+(\\s)*");

        if ((splittedArgs == null) || (splittedArgs.length == 0)) {
            throw new Exception(" Invalid ARGS " + list + " for [ " + Node.cluster.name + "/" + Node.name + " ]");
        }

        int iCount = splittedArgs.length;
        int iIndex = 0;

        // general configuration for this module Conf
        String sConfiguration = splittedArgs[0].trim();
        if (sConfiguration.startsWith("[") && sConfiguration.endsWith("]")) {
            // we found "configuration" parameter
            iIndex++;
            iCount--;
            try {
                sConfiguration = sConfiguration.substring(1, sConfiguration.length() - 1);
                String[] vConfiguration = sConfiguration.split("(\\s)*,(\\s)*");
                for (String element : vConfiguration) {
                    try {
                        String[] aParamValue = element.trim().split("(\\s)*=(\\s)*");
                        String sParam = aParamValue[0].trim();
                        String sValue = aParamValue[1].trim();
                        if ("SNMP_community".equalsIgnoreCase(sParam)) {
                            super.sCommunity = sValue;
                        } else if ("SNMP_Version".equalsIgnoreCase(sParam)) {
                            if (sValue.indexOf("2") != -1) {
                                iSNMPVersion = SNMPV2;
                            } else if (Integer.valueOf(sValue).intValue() == 1) {
                                iSNMPVersion = SNMPV1;
                            } else {
                                logger.log(Level.WARNING, "Could not understand SNMP_Version configuration: " + element);
                            }
                        } else if ("SNMP_RemoteAddress".equalsIgnoreCase(sParam)) {
                            try {
                                super.iaRemoteAddress = InetAddress.getByName(sValue);
                            } catch (Throwable t) {
                                logger.log(Level.WARNING,
                                        "Could not understand SNMP_RemoteAddress configuration:" + t.getMessage());
                            }
                        }

                        else if ("SNMP_RemotePort".equalsIgnoreCase(sParam)) {
                            try {
                                super.iRemotePort = Integer.valueOf(sValue).intValue();
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, "Could not understand SNMP_RemotePort configuration: "
                                        + element);
                            }
                        }

                        else if ("SNMP_LocalAddress".equalsIgnoreCase(sParam)) {
                            try {
                                super.iaLocalAddress = InetAddress.getByName(sValue);
                            } catch (Throwable t) {
                                logger.log(Level.WARNING,
                                        "Could not understand SNMP_LocalAddress configuration:" + t.getMessage());
                            }
                        } else if ("SNMP_receiveBufferSize".equalsIgnoreCase(sParam)) {
                            try {
                                snmpReceiveBufferSize = Integer.valueOf(sValue).intValue();
                            } catch (Throwable t) {
                                logger.log(Level.WARNING,
                                        "Could not understand  SNMP_receiveBufferSize configuration: " + element);
                            }
                        } else if ("SNMP_Timeout".equalsIgnoreCase(sParam)) {
                            try {
                                iReadTimeOut = Integer.valueOf(sValue).intValue();
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, "Could not understand  SNMP_Timeout configuration: "
                                        + element);
                            }
                        } else if ("CanSuspend".equalsIgnoreCase(sParam)) {
                            try {
                                canSuspend = Boolean.valueOf(sValue).booleanValue();
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, "Could not understand CanSuspend configuration: " + element);
                            }
                        }

                    } catch (Throwable t) {
                        if (logger.isLoggable(Level.WARNING)) {
                            logger.log(Level.WARNING, "[SNMP_v2] Could not understand parameter" + element);
                        }
                    }
                }// for
            } catch (Throwable t) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "[SNMP_v2] Could not parse configuration parameter" + sConfiguration,
                            t.getMessage());
                }
            }

        }

    }

    /**
         * Get Partitions configuration
         * 
         * @return map(partition_name->partition_index
         * @throws SNMPGetException
         * @throws IOException
         * @throws SNMPBadValueException
         */
    private Map getPartsIndexMap() throws SNMPGetException, IOException, SNMPBadValueException {

        final Map mDskPart_DskIndex = new HashMap();

        final String baseID = sOidPart;
        // System.out.println("Retrieving table corresponding to base OID " +
        // baseID);

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

            // extract the corresponding value from the pair; it's the
            // second
            // element in the sequence
            snmpValue = pair.getSNMPObjectAt(1);

            // print out the String representation of the retrieved value
            // System.out.println("Retrieved OID: " + snmpOID + ", type " +
            // snmpValue.getClass().getName() + ", value "
            // + snmpValue.toString());
            int iIndexOf = snmpOID.toString().indexOf(baseID) + baseID.length();

            Integer IIndex;
            try {
                IIndex = Integer.valueOf(snmpOID.toString().substring(iIndexOf + 1));
            } catch (Throwable t) {
                logger.log(Level.WARNING,
                        "[snmp_Disk_v2]: Cannot parse Disk Index" + snmpOID.toString().substring(iIndexOf + 1));
                continue;
            }
            mDskPart_DskIndex.put(snmpValue.toString(), IIndex);
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "[snmp_Disk_v2] Partitions: " + mDskPart_DskIndex);
        }
        return mDskPart_DskIndex;
    }

    static public void main(String[] args) throws Exception {

        logger.setLevel(Level.ALL);

        if (args.length < 2) {
            System.err.println("Usage: \n\t snmp_Disk_v2 agent_ip [config]");
            System.exit(1);
        }
        String host = args[0];
        snmp_Disk_v2 aa = new snmp_Disk_v2();
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.err.println(" Can not get ip for node " + e);
            System.exit(-1);
        }
        String config = "";
        if (args.length >= 2) {
            config = args[1];
        }

        MonModuleInfo info = aa.init(new MNode(args[0], ad, null, null), config);
        logger.info("Module Inited:" + aa.toString());

        while (true) {
            try {
                Object cc = aa.doProcess();
                System.err.println("Results: " + cc);
            } catch (Exception e) {
                System.err.println("parse error");
            }
            Thread.sleep(20000);
        }
    }

}
