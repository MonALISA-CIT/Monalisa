package lia.Monitor.modules;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.SocketException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.snmpMon2;
import lia.util.StringFactory;
import lia.util.ntp.NTPDate;
import snmp.SNMPBadValueException;
import snmp.SNMPCounter32;
import snmp.SNMPCounter64;
import snmp.SNMPGauge32;
import snmp.SNMPGetException;
import snmp.SNMPInteger;
import snmp.SNMPObject;
import snmp.SNMPObjectIdentifier;
import snmp.SNMPSequence;
import snmp.SNMPTimeTicks;
import snmp.SNMPVarBindList;

public class snmp_CatSwitch extends snmpMon2 implements MonitoringModule {

    /**
     * <code>serialVersionUID</code>
     */
    private static final long serialVersionUID = 6070535010983051983L;

    private static final Logger logger = Logger.getLogger(snmp_CatSwitch.class.getName());

    static final public String ModuleName = "snmp_CatSwitch";
    static final public String OsName = "*";
    static final public String[] resTypes = new String[0];

    // SNMP stuff

    // 64bit Counters ifHC{In,Out}Bytes
    static final String oid_ifHCInOctets = "1.3.6.1.2.1.31.1.1.1.6";

    static final String oid_ifHCOutOctets = "1.3.6.1.2.1.31.1.1.1.10";

    // 32bit Counters if{In,Out}Bytes
    static final String oid_ifInOctets = "1.3.6.1.2.1.2.2.1.10";

    static final String oid_ifOutOctets = "1.3.6.1.2.1.2.2.1.16";

    // ifDescr - interface decription ID - > eth_name
    static final String oid_IfDescr = "1.3.6.1.2.1.2.2.1.2";

    /* speed */
    /** ifSpeed - in bps, but may be 4.294.967.295 */
    static final String oid_IfSpeed = "1.3.6.1.2.1.2.2.1.5";

    /** ifHighSpeed - in Mbps 1.3.6.1.2.1.31.1.1.1.15 */
    static final String oid_IfHighSpeed = "1.3.6.1.2.1.31.1.1.1.15";

    /** ifMtu in bytes(Integer32) 1.3.6.1.2.1.2.2.1.4 */
    static final String oid_IfMtu = "1.3.6.1.2.1.2.2.1.4";

    /** ifType (Integer32) 1.3.6.1.2.1.2.2.1.4 */
    static final String oid_IfType = "1.3.6.1.2.1.2.2.1.3";

    /** ifAlias 1.3.6.1.2.1.31.1.1.1.18 */
    static final String oid_IfAlias = "1.3.6.1.2.1.31.1.1.1.18";

    /**
     * BRIDGE-MIB 1.3.6.1.2.1.* oids default in V2
     */
    static final String oid_dot1dBasePort = "1.3.6.1.2.1.17.1.4.1.2";
    static final String oid_dot1dBasePortIfIndex = "1.3.6.1.2.1.17.1.4.1.2";

    static final String oid_TpFdbAddress = "1.3.6.1.2.1.17.4.3.1.1";
    static final String oid_TpFdbPort = "1.3.6.1.2.1.17.4.3.1.2";
    static final String oid_TpFdbStatus = "1.3.6.1.2.1.17.4.3.1.3";

    /** SNMPv2-MIB */
    static final String oid_sysDescr = "1.3.6.1.2.1.1.1.0";
    static final String oid_sysName = "1.3.6.1.2.1.1.5.0";
    static final String oid_sysLocation = "1.3.6.1.2.1.1.6.0";
    static final String oid_entPhysicalDescr = "1.3.6.1.2.1.47.1.1.1.1.2.1";

    /** Foundry MIB : BASE: 1.3.6.1.4.1.1991 * */
    static final String oid_chasisActualTemperature = "1.3.6.1.4.1.1991.1.1.1.1.18.0";
    static final String oid_chasisWarningTemperature = "1.3.6.1.4.1.1991.1.1.1.1.19.0";
    static final String oid_chasisShutdownTemperature = "1.3.6.1.4.1.1991.1.1.1.1.20.0";
    // TODO : add more vendors OIDs

    /**
     * used to protect posible long-lived snmp query sessions (e.g low module running frequencies)
     */

    private final ReentrantLock snmpQuerySessionLock = new ReentrantLock();

    /*
     * map(IfDescr->if_data): map for monitored ports if port map change occurs in switch, this map should be also updated to reflect the current configuration String->data
     */
    private Map<String, Map<String, Number>> mIfDescr_Data;

    private String[] saInputPorts;

    private int mode;

    // show stats flag
    private boolean bShowStats = true;

    // internal port-map keys
    private static final String IF_INDEX = "IF_INDEX";
    private static final String IF_SPEED = "IF_SPEED";
    private static final String COUNTER_IN = "_IN";
    private static final String COUNTER_OUT = "_OUT";
    private static final String IF_MTU = "IF_MTU";
    private static final String IF_TYPE = "IF_TYPE";
    private static final String IF_ALIAS = "IF_ALIAS";

    private static final int SW_UNKNOWN = 0;
    private static final int SW_FOUNDRY = 1;

    /**
     * Some information (such stats,temp) should be reported at large intervals these constants defines the the multipliers for defined the module running period
     */
    private static final int STATS_PERIOD_MULT = 20;
    private static final int REGULAR_STATS_MULT = 5;

    private int iTempStatsType = SW_UNKNOWN;

    /**
     * @see lia.Monitor.monitor.MonitoringModule#init(lia.Monitor.monitor.MNode, java.lang.String)
     */
    @Override
    public MonModuleInfo init(MNode node, String args) {
        try {
            this.Node = node;
            // parse args
            init_args(args);
            // init SNMP interface
            super.init(node);
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "SNMP module configuration for [ " + Node.cluster + "/" + Node.name + " ]: "
                        + this.toString());
            }
            initPortMap(saInputPorts);
        } catch (SocketException e) {
            // severe init error, cannot continue..
            logger.log(Level.SEVERE, "[SNMP] CommInterface could not be initialized", e);
            info.addErrorCount();
            info.setState(1); // error
            info.setErrorDesc("CommInterface could not be initialized");
        } catch (Exception e) { // in case of other exception ... set error
            // state in module
            logger.log(Level.SEVERE, "[SNMP]Module failed to init", e);
            info.addErrorCount();
            info.setState(1); // error
            info.setErrorDesc("Module failed to init");
        }

        info.setName(ModuleName);

        // load port map from args : map(ifDescr->IfIndex)

        this.mode = MODE64;
        info.ResTypes = resTypes;
        return info;
    }

    /**
     * SNMP map(interface-name->IfIndex) contacts the SNMP agent
     * 
     * @param saInputPortsList
     * @throws SNMPBadValueException
     * @throws IOException
     * @throws SNMPGetException
     */
    private void initPortMap(final String[] saInputPortsList) throws SNMPGetException, IOException,
            SNMPBadValueException {
        if ((saInputPortsList == null) || (saInputPortsList.length < 2)) {
            throw new InvalidParameterException("Invalid Input:" + saInputPortsList);
        }

        mIfDescr_Data = new HashMap<String, Map<String, Number>>();
    }

    /*
     * configuration template: snmp_IOpp_v2{params}%30 params:
     * [SNMP_community=mycommunity,SNMP_Version=2c,SNMP_RemoteAddress=x.x.x.x,SNMP_RemotePort=2161,SNMP_LocalAddress=x.x.x.x,SNMP_Timeout=xx,CanSuspend=false];
     * ifIndexList=(0,1,2..10,11);ifDescrList=(Fa1/*,Fa2/3)
     */
    private void init_args(String list) throws Exception {
        String[] splittedArgs = list.split("(\\s)*;+(\\s)*");

        if ((splittedArgs == null) || (splittedArgs.length == 0)) {
            throw new Exception(" Invalid ARGS " + list + " for [ " + Node.cluster.name + "/" + Node.name + " ]");
        }
        // general configuration for this module Conf
        String sConfiguration = splittedArgs[0].trim();
        if (sConfiguration.startsWith("[") && sConfiguration.endsWith("]")) {
            // we found "configuration" parameter
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
                        } else if ("ShowStats".equalsIgnoreCase(sParam)) {
                            try {
                                bShowStats = Boolean.valueOf(sValue).booleanValue();
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, "Could not understand ShowStats configuration  parameter: "
                                        + element);
                                bShowStats = true;
                            }
                        } else if ("ShowTempStats".equalsIgnoreCase(sParam)) {
                            try {
                                iTempStatsType = "Foundry".equalsIgnoreCase(sValue) ? SW_FOUNDRY : SW_UNKNOWN;
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, "Could not understand ShowStats configuration  parameter: "
                                        + element);
                                iTempStatsType = SW_UNKNOWN;
                            }
                        }

                    } catch (Throwable t) {
                        if (logger.isLoggable(Level.WARNING)) {
                            logger.log(Level.WARNING, "[snmp_CatSwitch] Could not understand parameter" + element);
                        }
                    }
                }// for
            } catch (Throwable t) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "[snmp_CatSwitch] Could not parse configuration parameter"
                            + sConfiguration, t.getMessage());
                }
            }

        }

        /*
         * ports to be monitored are inputed as comma separated list of ifIndex'es and/or comma separated list of ifDescr'es index 0: list of ifIndex'es index 1: list of IfDescr
         * (regex is permitted : e.g Fa2/*
         */
        saInputPorts = new String[2];
        try {
            for (int i = 1; i < splittedArgs.length; i++) {
                String[] aParameterValue = splittedArgs[i].split("(\\s)*=(\\s)*");

                if (aParameterValue.length != 2) {
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.log(Level.WARNING, "[snmp_CatSwitch] Invalid parameter" + splittedArgs[i]);
                    }
                    continue;
                }
                if ("ifIndexList".equalsIgnoreCase(aParameterValue[0].trim())) {
                    saInputPorts[0] = aParameterValue[1].trim();
                } else if ("ifDescrList".equalsIgnoreCase(aParameterValue[0].trim())) {
                    saInputPorts[1] = aParameterValue[1].trim();
                }
            }
        } catch (Exception e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "[snmp_CatSwitch] Invalid parameters:" + e.getMessage());
            }
        }
    }

    // walks ifDescr( "1.3.6.1.2.1.2.2.1.2" ) OID
    @Override
    public Object doProcess() throws Exception {
        if (snmpQuerySessionLock.tryLock()) {
            try {
                return doProcess0();
            } finally {
                snmpQuerySessionLock.unlock();
            }
        }// if tryLock()

        // else if previous running still in progress
        if (logger.isLoggable(Level.WARNING)) {
            logger.log(Level.WARNING, "[" + ModuleName + "] Previous running still in progress, skipping this round");
        }
        return null;

    }

    private int updateStatsPeriods = 0;

    // protected doProcess
    private Object doProcess0() throws Exception {
        // can't run this module, init failed
        if (info.getState() != 0) {
            throw new Exception("There was some exception during init ..." + info.getErrorDesc() + " ["
                    + info.getErrorCount() + "]");
        }

        final Vector vResult = new Vector();

        // periodically update mappings, speed and report chasis stats
        if (updateStatsPeriods == 0) {
            try {
                // check ifDescr->IfIndex mappings for monitored ports and
                // update if necessary
                updateMappings();
                // update interfaces' speed
                updateHighSpeeds();
                if (bShowStats) {
                    updatePortsInfo();
                }

            } catch (Throwable t) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "Cannot resolve mappings ...", t);
                }
                return null;
            }
            // resolveCnt++;

            if (bShowStats) {
                eResult eResult = new eResult(Node.getFarmName(), Node.getClusterName() + "_Stats", Node.getName(),
                        ModuleName, null);
                eResult.time = NTPDate.currentTimeMillis();
                Result rResult = new Result(Node.getFarmName(), Node.getClusterName() + "_Stats", Node.getName(),
                        ModuleName, null);
                rResult.time = NTPDate.currentTimeMillis();

                // walk the internal map and extract SPEED,MTU,TYPE,ALIAS
                for (Object element : mIfDescr_Data.entrySet()) {
                    try {
                        Map.Entry entry = (Map.Entry) element;
                        String ifDescr = (String) entry.getKey();
                        Map attrs = (Map) entry.getValue();
                        rResult.addSet(ifDescr.replace('/', '_') + "_SPEED",
                                ((Double) attrs.get(IF_SPEED)).doubleValue());

                        if (attrs.get(IF_MTU) != null) {
                            Long mtu = (Long) attrs.get(IF_MTU);
                            rResult.addSet(ifDescr.replace('/', '_') + "_MTU", mtu.doubleValue());
                        }
                        if (attrs.get(IF_TYPE) != null) {
                            Long type = (Long) attrs.get(IF_TYPE);
                            rResult.addSet(ifDescr.replace('/', '_') + "_TYPE", type.doubleValue());
                        }
                        if (attrs.get(IF_ALIAS) != null) {
                            eResult.addSet(ifDescr.replace('/', '_') + "_ALIAS", attrs.get(IF_ALIAS).toString());
                        }
                    } catch (Throwable t) {
                        if (logger.isLoggable(Level.WARNING)) {
                            logger.log(Level.WARNING, "Failed to report Stats", t);
                        }
                    }
                }

                // rResult.addSet(sModuleConfiguration.toString(), 0D);
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, " SPEEDS,MAP UPDATED " + eResult);
                }

                // do not report null values
                if ((eResult.param != null) && (eResult.param.length != 0)) {
                    StringFactory.convert(eResult);
                    vResult.addElement(eResult);
                }
                if ((rResult.param != null) && (rResult.param.length != 0)) {
                    StringFactory.convert(rResult);
                    vResult.addElement(rResult);
                }
                try {
                    Map chasis = super.snmpBulkGet(new String[] { oid_sysName, oid_sysDescr, oid_sysLocation,
                            oid_entPhysicalDescr });
                    // report module configuration
                    eResult strResult = new eResult(Node.getFarmName(), Node.getClusterName() + "_Stats",
                            Node.getName(), ModuleName, null);
                    strResult.time = NTPDate.currentTimeMillis();
                    if (chasis.get(oid_sysName) != null) {
                        strResult.addSet("sysName", chasis.get(oid_sysName).toString());
                    }
                    if (chasis.get(oid_sysLocation) != null) {
                        strResult.addSet("sysLocation", chasis.get(oid_sysLocation).toString());
                    }
                    if (chasis.get(oid_sysDescr) != null) {
                        strResult.addSet("sysDescr", chasis.get(oid_sysDescr).toString());
                    }
                    if (chasis.get(oid_entPhysicalDescr) != null) {
                        strResult.addSet("entPhysicalDescr", chasis.get(oid_entPhysicalDescr).toString());
                    }

                    if ((strResult.param != null) && (strResult.param.length > 0)) {
                        StringFactory.convert(strResult);
                        vResult.addElement(strResult);
                    }
                } catch (Throwable t) {
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.log(Level.WARNING, "Exception while getting chasis info", t);
                    }
                }
            }
        }// updateStatsPeriods==0

        if ((updateStatsPeriods % REGULAR_STATS_MULT) == 0) {
            /**
             * BEGIN query additional INFO (<code>REGULAR_STATS_MULT</code>*T)
             */
            // Query for additional
            List lStatsOIDsQuery = new ArrayList();

            if (bShowStats) {
                lStatsOIDsQuery.add(timeTicksOID);
            }
            if (iTempStatsType == SW_FOUNDRY) {
                lStatsOIDsQuery.add(oid_chasisActualTemperature);
                lStatsOIDsQuery.add(oid_chasisWarningTemperature);
                lStatsOIDsQuery.add(oid_chasisShutdownTemperature);
            }

            if (lStatsOIDsQuery.size() > 0) {
                try {
                    String[] aOIDs = (String[]) lStatsOIDsQuery.toArray(new String[lStatsOIDsQuery.size()]);
                    Map mResults = super.snmpBulkGet(aOIDs);

                    // report temp stats
                    Result rResult = new Result(Node.getFarmName(), Node.getClusterName() + "_Stats", Node.getName(),
                            ModuleName, null);
                    rResult.time = NTPDate.currentTimeMillis();

                    Object t;
                    BigInteger value;

                    /** SysUptime * */
                    t = mResults.get(timeTicksOID);
                    if ((t != null) && (t instanceof SNMPTimeTicks)) {
                        value = (BigInteger) ((SNMPTimeTicks) t).getValue();
                        if (value != null) {
                            rResult.addSet("uptime", value.doubleValue() / 100.0);
                        }
                    }

                    /** Foundry Temps * */
                    if (iTempStatsType == SW_FOUNDRY) {
                        t = mResults.get(oid_chasisActualTemperature);
                        if ((t != null) && (t instanceof SNMPInteger)) {
                            value = (BigInteger) ((SNMPInteger) t).getValue();
                            if (value != null) {
                                rResult.addSet("chasisActualTemperature", value.doubleValue() / 2);
                            }
                        }

                        t = mResults.get(oid_chasisWarningTemperature);
                        if ((t != null) && (t instanceof SNMPInteger)) {
                            value = (BigInteger) ((SNMPInteger) t).getValue();
                            if (value != null) {
                                // Foundry reports in units of 0.5 Celsius
                                // grades
                                rResult.addSet("chasisWarningTemperature", value.doubleValue() / 2);
                            }
                        }

                        t = mResults.get(oid_chasisShutdownTemperature);
                        if ((t != null) && (t instanceof SNMPInteger)) {
                            value = (BigInteger) ((SNMPInteger) t).getValue();
                            if (value != null) {
                                rResult.addSet("chasisShutdownTemperature", value.doubleValue() / 2);
                            }
                        }
                    }

                    if ((rResult.param != null) && (rResult.param.length > 0)) {
                        StringFactory.convert(rResult);
                        vResult.addElement(rResult);
                    }
                } catch (Throwable t) {
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.log(Level.WARNING, "Exception while getting chasis temperature info", t);
                    }
                }
            }

            /**
             * END query additional INFO at the same rate with Ports I/O information (%T)
             */
        }

        if (++updateStatsPeriods >= STATS_PERIOD_MULT) {
            updateStatsPeriods = 0;
        }

        // get ports I/O rates on _IN _OUT
        List oids = new ArrayList();
        // current IF_INDEX->IF_DESCR map
        List lIfDescrs = new ArrayList();
        int chunk = 0;

        // walk the ports and update internal map
        for (Object element : mIfDescr_Data.entrySet()) {
            try {
                Map.Entry entry = (Map.Entry) element;
                String ifDescr = (String) entry.getKey();
                Map attrs = (Map) entry.getValue();

                oids.add(((mode == MODE32) ? oid_ifInOctets : oid_ifHCInOctets) + "." + attrs.get(IF_INDEX).toString());
                oids.add(((mode == MODE32) ? oid_ifOutOctets : oid_ifHCOutOctets) + "."
                        + attrs.get(IF_INDEX).toString());
                lIfDescrs.add(ifDescr);
                if ((++chunk % 4) == 0) { // get chunk (this performs like the
                    // SNMP_WALK
                    oids.add(timeTicksOID);
                    String[] itemIDs = (String[]) oids.toArray(new String[oids.size()]);

                    try {
                        // update internal map with chunk results
                        // (map(OID->Value))
                        updatePortsStatus(super.snmpBulkGet(itemIDs), lIfDescrs);
                    } catch (SNMPGetException sge) {
                        // the OIDs cannot be retrieved fallback to MODE32
                        if (mode == MODE64 /* && sge.errorStatus */) {
                            if (logger.isLoggable(Level.INFO)) {
                                logger.log(Level.INFO,
                                        "HighCounters are not supported, fallback to 32bit counters. Cause:", sge);
                            }
                            mode = MODE32;
                        }
                        if (logger.isLoggable(Level.WARNING)) {
                            logger.log(Level.WARNING, "[SNMP] An error was occured ..Invalid OIDs." + Node.getName()
                                    + " ] :" + sge.getMessage());
                        } else if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "[SNMP] An error was occured..Invalid OIDs." + Node.getName()
                                    + " ] :" + sge);
                        }
                        resetCounters(lIfDescrs);
                        // return null0Conf(vResult);
                    } catch (Throwable t) { // other errors
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "Got exc for Node [ " + Node.getName() + " ] :", t);
                        } else if (logger.isLoggable(Level.WARNING)) {
                            logger.log(Level.WARNING, "Got exc for Node [ " + Node.getName() + " ] :" + t.getMessage());
                        }
                        // reset old counters
                        resetCounters(lIfDescrs);
                        // set error status
                        info.setErrorDesc("Got exc for Node [ " + Node.getName() + " ] :" + t.getMessage());
                        info.addErrorCount();
                        // return null0Conf(vResult);
                    }
                    oids.clear();
                    lIfDescrs.clear();
                }
            } catch (Throwable e) {
                // continue;
            }
        }
        if ((oids.size() > 0) && (lIfDescrs.size() > 0)) {
            oids.add(timeTicksOID);
            String[] itemIDs = (String[]) oids.toArray(new String[oids.size()]);

            try {
                // update internal map with chunk results
                // (map(OID->Value))
                updatePortsStatus(super.snmpBulkGet(itemIDs), lIfDescrs);
            } catch (SNMPGetException sge) {
                // the OIDs cannot be retrieved fallback to MODE32
                if (mode == MODE64 /* && sge.errorStatus */) {
                    if (logger.isLoggable(Level.INFO)) {
                        logger.log(Level.INFO, "HighCounters are not supported, fallback to 32bit counters. Cause:",
                                sge);
                    }
                    mode = MODE32;
                }
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "[SNMP] An error was occured ..Invalid OIDs." + Node.getName() + " ] :"
                            + sge.getMessage());
                } else if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "[SNMP] An error was occured..Invalid OIDs." + Node.getName() + " ] :" + sge);
                }
                resetCounters(lIfDescrs);
                // return null0Conf(vResult);
            } catch (Throwable t) { // other errors
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Got exc for Node [ " + Node.getName() + " ] :", t);
                } else if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "Got exc for Node [ " + Node.getName() + " ] :" + t.getMessage());
                }
                // reset old counters
                resetCounters(lIfDescrs);
                // set error status
                info.setErrorDesc("Got exc for Node [ " + Node.getName() + " ] :" + t.getMessage());
                info.addErrorCount();
                // return null0Conf(vResult);
            }
            oids.clear();
            lIfDescrs.clear();
        }
        // ports iterator, END WALK

        /* --- report data --- */
        // 1. walk internal map and make the result
        Result rResult = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(), ModuleName, null);
        rResult.time = NTPDate.currentTimeMillis();
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Current map: " + mIfDescr_Data);
        }

        for (Object element : mIfDescr_Data.entrySet()) {
            try {
                Map.Entry entry = (Map.Entry) element;
                String ifDescr = (String) entry.getKey();
                updateResult(rResult, ifDescr, COUNTER_IN);
                updateResult(rResult, ifDescr, COUNTER_OUT);
            } catch (Exception e) {
                // continue
            }
        }// for

        if ((rResult.param != null) && (rResult.param.length > 0)) {
            StringFactory.convert(rResult);
            vResult.addElement(rResult);
        }

        if (vResult.size() > 0) {
            return vResult;
        }
        return null;

    }

    private void updateResult(Result result, String ifDescr, String sCounter) {

        Map attrs = mIfDescr_Data.get(ifDescr);

        if ((result == null) || (attrs == null) || (sCounter == null)) {
            throw new IllegalArgumentException("Invalid arguments");
        }

        CounterData counter = (CounterData) attrs.get(sCounter);

        if (counter.getPreviousCounter() == null) {// step counters
            counter.stepCounter();
            return;
        }

        long dt = 0;
        if (counter.getCurrentTimestamp().longValue() > 0) {
            if (counter.getCurrentTimestamp().longValue() < counter.getPreviousTimestamp().longValue()) { // restarted
                logger.log(Level.WARNING, "last_measured [" + counter.getPreviousTimestamp().longValue()
                        + " ] >= snmpTime [ " + counter.getCurrentTimestamp().longValue() + " ] Was the device [ "
                        + Node.getName() + " ] restarted ?");
                // reset counters and skip this port
                counter.reset();
                // retry MODE64
                this.mode = MODE64;
                return;
            }
        } else {
            logger.log(Level.INFO, "Negative SNMP Time ?! " + counter.getCurrentTimestamp().longValue());
            // reset counters and skip this port
            counter.reset();
            return;
        }

        dt = counter.getCurrentTimestamp().longValue() - counter.getPreviousTimestamp().longValue();
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " SNMP dt = " + dt);
        }

        if (dt == 0) {
            logger.log(
                    Level.INFO,
                    " diff == 0 for "
                            + Node.getName()
                            + "/"
                            + ifDescr
                            + " ... probably the counters still not updated (... SNMP has high Load or SNMP queries are too often )");
            return;
        }

        double diff = counter.getCurrentCounter().subtract(counter.getPreviousCounter()).doubleValue();
        if (diff < 0) {
            logger.log(Level.WARNING,
                    " Diff neg [ " + Node.getName() + " ---> " + ifDescr + " ] New: " + counter.getCurrentCounter()
                            + " Old: " + counter.getPreviousCounter() + " diff: " + diff);
            // reset counters and skip this port
            counter.reset();
            return;
        }
        // else, diff >0
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, " [  " + Node.getName() + " ---> " + ifDescr + " ] Counter[: " + sCounter + counter
                    + " diff: " + diff);
        }
        // return rate in Mbps
        // time from SNMP is in hundredths of a second
        double rate = (diff / (10000.0D * dt)) * 8.0D;
        Double dRateTransport = counter.getTransport();
        if ((dRateTransport != null) && (dRateTransport.doubleValue() > 0)) {
            rate += dRateTransport.doubleValue();
        }
        counter.setTransport(Double.valueOf(0));

        Double speed = (Double) attrs.get(IF_SPEED);

        if ((speed != null) && (speed.doubleValue() >= 0) && (rate > speed.doubleValue())) {
            double rateTransport = rate - speed.doubleValue();
            counter.setTransport(Double.valueOf(rateTransport));

            rate = speed.doubleValue();

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " Exceed Rate " + " [ " + ifDescr + " ] " + rateTransport);
            }

            if (rateTransport > (speed.doubleValue() * 0.5)) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "**Rate transport is too high, [ " + ifDescr + "] speed=" + speed + "\n");
                }
                // TODO - broken routers often update too late the counters, in
                // this case the transport should not be discarded...
                // rateTransport[K] = 0;
            }
        }

        result.addSet(ifDescr.replace('/', '_') + sCounter, rate);
        counter.stepCounter();

    }

    private void updateMappings() throws SNMPGetException, IOException, SNMPBadValueException {
        Map m = getIfDescrIfIndexMap();
        for (Iterator iter = m.entrySet().iterator(); iter.hasNext();) {
            try {
                Map.Entry entry = (Map.Entry) iter.next();
                String key = (String) entry.getKey();
                Integer value = Integer.valueOf((String) entry.getValue());
                Map attrs = mIfDescr_Data.get(key);
                if (attrs == null) {
                    attrs = new HashMap();
                    mIfDescr_Data.put(key, attrs);
                }
                attrs.put(IF_INDEX, value);
            } catch (Throwable t) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "Parsing error, skip" + t);
                }
                continue;
            }
        }
    }

    private void updatePortsStatus(Map mOID_Value, List lIfDescrRequested) {

        // snmpTicks for this chunk
        BigInteger snmpTime;
        try {
            Object oTime = mOID_Value.get(timeTicksOID);
            oTime = ((SNMPTimeTicks) oTime).getValue();
            snmpTime = (BigInteger) oTime;
            if (snmpTime.longValue() < 0) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "Negative SNMP timestamp...Reset counters");
                }
                resetCounters(lIfDescrRequested);
                return;
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Failed to update" + lIfDescrRequested
                    + ": Got exception while trying to read SNMP timestamp ", t);
            return;
        }

        String _baseIN = ((mode == MODE64) ? oid_ifHCInOctets : oid_ifInOctets) + ".";
        String _baseOUT = ((mode == MODE64) ? oid_ifHCOutOctets : oid_ifOutOctets) + ".";

        String ifDescr = null, sIfIndex = null;
        String[] _oidsINOUT;

        int iActiveCounters = lIfDescrRequested.size() * 2;
        int iUnknownCounters = 0;

        for (Iterator iter = lIfDescrRequested.iterator(); iter.hasNext();) {
            try {
                ifDescr = (String) iter.next();
                sIfIndex = ((Map) mIfDescr_Data.get(ifDescr)).get(IF_INDEX).toString();
                _oidsINOUT = new String[] { _baseIN + sIfIndex, _baseOUT + sIfIndex };
                for (int j = 0; j < _oidsINOUT.length; j++) {
                    String _oid = _oidsINOUT[j];

                    if (mOID_Value.containsKey(_oid)) {
                        Object value = mOID_Value.get(_oid);
                        Map attrs = mIfDescr_Data.get(ifDescr);
                        CounterData counter_data;
                        String counter = (j == 0 ? COUNTER_IN : COUNTER_OUT);
                        if ((counter_data = (CounterData) attrs.get(counter)) == null) {
                            counter_data = new CounterData();
                            attrs.put(counter, counter_data);
                        }

                        if (mode == MODE64) {
                            if (!(value instanceof SNMPCounter64)) {
                                // SNMPUnknown?
                                logger.log(Level.WARNING, "[SNMP: " + this.Node.getName()
                                        + "]  Invalid SNMPCounter64 value " + value.getClass() + " for OID: " + _oid
                                        + " ifIndex: " + ifDescr + " ...Skipping it");
                                counter_data.reset();
                                iUnknownCounters++;
                                continue;
                            }
                            // data ok (64bit counter)
                            SNMPCounter64 cnt64 = (SNMPCounter64) value;
                            counter_data.setCurrentCounter((BigInteger) cnt64.getValue());
                        } else {
                            if (!(value instanceof SNMPCounter32)) {
                                logger.log(Level.WARNING, "[SNMP: " + this.Node.getName()
                                        + "]  Invalid SNMPCounter32 value " + value.getClass() + " for OID: " + _oid
                                        + " ifIndex: " + ifDescr + " ...Skipping it");
                                counter_data.reset();
                                iUnknownCounters++;
                                continue;
                            }
                            SNMPCounter32 cnt32 = (SNMPCounter32) value;
                            counter_data.setCurrentCounter((BigInteger) cnt32.getValue());

                        }
                        // update timestamps
                        counter_data.setPreviousTimestamp(counter_data.getCurrentTimestamp());
                        counter_data.setCurrentTimestamp(snmpTime);
                    }
                }
            } catch (Throwable t) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "Failed to update [" + ifDescr + "] data", t);
                }
            }
        }
        // if we received 2*no of interfaces "SNMPUnknownObject" and we have at least one monitored iface then switch to MODE32
        if ((iActiveCounters > 0) && (iUnknownCounters >= iActiveCounters)) {
            if (mode == MODE64) {
                logger.log(Level.INFO, "[SNMP: " + this.Node.getName()
                        + "] 64bit IF-Counters seems to not be supported. Fallback to 32bit counters");
                mode = MODE32;
            } else {
                logger.log(Level.INFO, "[SNMP: " + this.Node.getName()
                        + "] IF-Counters seems to not be supported. Check if SNMP agent exports IF-MIB");
                // set error status
                info.setErrorDesc("[SNMP: " + this.Node.getName()
                        + "] IF-Counters seems to not be supported. Check if SNMP agent exports IF-MIB");
                info.addErrorCount();
            }
        }

    }

    private void resetCounters(List lIfDescr) {
        String ifDescr;
        Map data;
        for (Iterator iter = lIfDescr.iterator(); iter.hasNext();) {
            try {
                ifDescr = (String) iter.next();
                data = mIfDescr_Data.get(ifDescr);
                if (data == null) {
                    continue;
                }

                CounterData in = (CounterData) data.get(COUNTER_IN);
                if (in != null) {
                    in.reset();
                }
                CounterData out = (CounterData) data.get(COUNTER_OUT);
                if (out != null) {
                    out.reset();
                }
            } catch (Throwable e) {
                // this should not happen
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "Got exception while resetting counters for " + lIfDescr);
                }
                continue;
            }
        }
    }

    @Override
    public String[] ResTypes() {
        return info.ResTypes;
    }

    @Override
    public String getOsName() {
        return OsName;
    }

    // SNMP helper methods

    private void updateHighSpeeds() throws SNMPGetException, IOException, SNMPBadValueException {
        List<String> lUnresolvedIfs = new ArrayList<String>();
        SNMPGauge32 snmpValue;
        Map mIfSpeeds = super.getMIBTable(oid_IfSpeed);

        for (Entry<String, Map<String, Number>> entry : mIfDescr_Data.entrySet()) {
            try {
                String ifDescr = entry.getKey();
                Map<String, Number> attrs = entry.getValue();
                if (attrs.get(IF_INDEX) == null) {
                    continue;
                }

                String ifSpeedOID = new StringBuilder(oid_IfSpeed).append(".").append(attrs.get(IF_INDEX).toString())
                        .toString();

                if (mIfSpeeds.containsKey(ifSpeedOID)) {
                    snmpValue = ((SNMPGauge32) mIfSpeeds.get(ifSpeedOID));
                    BigInteger biSNMPValue = (BigInteger) snmpValue.getValue();
                    if (biSNMPValue.longValue() == 4294967295L) {
                        // try ifHighSpeed for this interface
                        lUnresolvedIfs.add(ifDescr);// oid_IfHighSpeed + "." +
                        // attrs.get(IF_INDEX).toString());
                    } else {
                        long lBw = ((BigInteger) snmpValue.getValue()).longValue();
                        attrs.put(IF_SPEED, Double.valueOf(lBw / 1000000.0D));
                    }
                }
            } catch (Exception e) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "Exception while trying to update high-speeds" + e);
                }
            }
        }

        if (lUnresolvedIfs.size() <= 0) {
            return;
        }

        // else
        // query ifHighSpeed
        List lOIDs = new ArrayList();
        for (Object element : lUnresolvedIfs) {
            try {
                String ifDescr = (String) element;
                Map attrs = mIfDescr_Data.get(ifDescr);
                if ((attrs == null) || !attrs.containsKey(IF_INDEX)) {
                    continue;
                }
                lOIDs.add(oid_IfHighSpeed + "." + attrs.get(IF_INDEX).toString());
            } catch (Throwable t) {
            }
        }// for
        if (lOIDs.size() <= 0) {
            return;
        }

        String[] itemIDs = (String[]) lOIDs.toArray(new String[lOIDs.size()]);
        Map mIfHighSpeeds = super.snmpBulkGet(itemIDs);

        for (Object element : lUnresolvedIfs) {
            try {
                String ifDescr = (String) element;
                Map attrs = mIfDescr_Data.get(ifDescr);
                if (attrs.get(IF_INDEX) == null) {
                    continue;
                }

                String ifSpeedOID = new StringBuilder(oid_IfHighSpeed).append('.')
                        .append(attrs.get(IF_INDEX).toString()).toString();

                if (mIfHighSpeeds.containsKey(ifSpeedOID)) {
                    snmpValue = ((SNMPGauge32) mIfHighSpeeds.get(ifSpeedOID));

                    long lBw = ((BigInteger) snmpValue.getValue()).longValue();
                    if (lBw > 0) {
                        attrs.put(IF_SPEED, Double.valueOf(lBw));
                    }
                }
            } catch (Exception e) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "Exception while trying to update high-speeds" + e);
                }
            }
        }// for unresolved ifs
    }

    private void updatePortsInfo() {

        SNMPObject snmpValue;
        try {
            Map mIfMtus = super.getMIBTable(oid_IfMtu);
            Map mIfTypes = super.getMIBTable(oid_IfType);
            Map mIfAliases = super.getMIBTable(oid_IfAlias);

            for (Object element : mIfDescr_Data.entrySet()) {
                try {
                    Map.Entry entry = (Map.Entry) element;
                    // String ifDescr = (String) entry.getKey();
                    Map attrs = (Map) entry.getValue();
                    if (attrs.get(IF_INDEX) == null) {
                        continue;
                    }

                    String ifMtuOID = new StringBuilder(oid_IfMtu).append(".").append(attrs.get(IF_INDEX).toString())
                            .toString();
                    String ifTypeOID = new StringBuilder(oid_IfType).append(".").append(attrs.get(IF_INDEX).toString())
                            .toString();
                    String ifAliasOID = new StringBuilder(oid_IfAlias).append(".")
                            .append(attrs.get(IF_INDEX).toString()).toString();

                    // mtu
                    if (mIfMtus.containsKey(ifMtuOID)) {
                        snmpValue = ((SNMPInteger) mIfMtus.get(ifMtuOID));
                        BigInteger biSNMPValue = (BigInteger) snmpValue.getValue();
                        attrs.put(IF_MTU, Long.valueOf(biSNMPValue.longValue()));
                    }
                    // type
                    if (mIfTypes.containsKey(ifTypeOID)) {
                        snmpValue = ((SNMPInteger) mIfTypes.get(ifTypeOID));
                        BigInteger biSNMPValue = (BigInteger) snmpValue.getValue();
                        attrs.put(IF_TYPE, Long.valueOf(biSNMPValue.longValue()));
                    }
                    // Alias
                    if (mIfAliases.containsKey(ifAliasOID)) {
                        snmpValue = (SNMPObject) mIfAliases.get(ifAliasOID);
                        attrs.put(IF_ALIAS, snmpValue.toString());
                    }

                } catch (Exception e) {
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.log(Level.WARNING, "Exception while trying to update MTUs" + e);
                    }
                }
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Failed to update ports info", t);
            }
        }

    }

    /**
     * @return map(ifDescr->IfIndex)
     * @throws SNMPGetException
     * @throws IOException
     * @throws SNMPBadValueException
     */
    private Map getIfDescrIfIndexMap() throws SNMPGetException, IOException, SNMPBadValueException {

        Map mAliasPort = new HashMap();

        // base OID for ifDescr
        String baseID = "1.3.6.1.2.1.2.2.1.2";
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

            // extract the corresponding value from the pair; it's the second
            // element in the sequence
            snmpValue = pair.getSNMPObjectAt(1);

            int iPort = snmpOID.toString().indexOf(baseID) + baseID.length();
            mAliasPort.put(snmpValue.toString(), snmpOID.toString().substring(iPort + 1));
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Aliases: " + mAliasPort);
        }
        return mAliasPort;
    }

    /**
     * map(BasePort->IfIndex): String->SNMPObject <br>
     * use oid_dot1dBasePortIfIndex <br>
     * OID from BRIDGE-MIB
     */

    private Map geBasePort_IfIndex_Map() throws SNMPGetException, IOException, SNMPBadValueException {

        /*
         * map(BasePort->IfIndex): String->SNMPObject uses oid_dot1dBasePortIfIndex OID from BRIDGE-MIB
         */
        Map mBasePort_IfIndex = new HashMap();
        // make sure the socket is up
        snmpComInterface.reOpenSocketIfClosed();
        SNMPVarBindList tableVars = snmpComInterface.retrieveMIBTable(oid_dot1dBasePortIfIndex);

        SNMPSequence pair;
        SNMPObjectIdentifier snmpOID;
        SNMPObject snmpValue;

        // extract the (OID,value) pairs from the SNMPVarBindList;
        // each pair is just a two-element
        // SNMPSequence
        for (int i = 0; i < tableVars.size(); i++) {
            pair = (SNMPSequence) (tableVars.getSNMPObjectAt(i));

            // extract the object identifier from the pair; it's the first
            // element in the sequence
            snmpOID = (SNMPObjectIdentifier) pair.getSNMPObjectAt(0);

            // extract the corresponding value from the pair; it's the second
            // element in the sequence
            snmpValue = pair.getSNMPObjectAt(1);

            // print out the String representation of the retrieved value
            // System.out.println("Retrieved OID: " + snmpOID + ", type " +
            // snmpValue.getClass().getName() + ", value "
            // + snmpValue.toString());
            int iPortIdx = snmpOID.toString().indexOf(oid_dot1dBasePortIfIndex) + oid_dot1dBasePortIfIndex.length();
            mBasePort_IfIndex.put(snmpOID.toString().substring(iPortIdx + 1), snmpValue);
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "ma(BasePort->IfIndex): " + mBasePort_IfIndex);
        }
        return mBasePort_IfIndex;
    }

    private static final class CounterData {
        BigInteger currentCounter, previousCounter, currentTimestamp, previousTimestamp;
        Double transport;

        BigInteger getCurrentCounter() {
            return this.currentCounter;
        }

        void setCurrentCounter(BigInteger currentCounter) {
            this.currentCounter = currentCounter;
        }

        BigInteger getCurrentTimestamp() {
            return this.currentTimestamp;
        }

        void setCurrentTimestamp(BigInteger currentTimestamp) {
            this.currentTimestamp = currentTimestamp;
        }

        BigInteger getPreviousCounter() {
            return this.previousCounter;
        }

        void setPreviousCounter(BigInteger previousCounter) {
            this.previousCounter = previousCounter;
        }

        protected BigInteger getPreviousTimestamp() {
            return this.previousTimestamp;
        }

        protected void setPreviousTimestamp(BigInteger previousTimestamp) {
            this.previousTimestamp = previousTimestamp;
        }

        protected Double getTransport() {
            return this.transport;
        }

        protected void setTransport(Double transport) {
            this.transport = transport;
        }

        protected void stepCounter() {
            this.previousCounter = currentCounter;
            this.previousTimestamp = currentTimestamp;
        }

        protected void reset() {
            this.previousCounter = this.currentCounter = null;
            this.previousTimestamp = this.currentTimestamp = null;
            this.transport = null;
        }

        @Override
        public String toString() {
            return "{CurrentCounter:" + currentCounter + " CurrentTimestamp:" + currentTimestamp
                    + "}, {PreviousCounter:" + previousCounter + " PreviousTimestamp:" + previousTimestamp
                    + "}, RateTransport:" + transport;
        }

    }

    public static void main(String[] args) throws Exception {
        String host = args[0];
        snmp_CatSwitch aa = new snmp_CatSwitch();
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }
        String arg = args[1]; // "eth0=LAN;lo=LOCAL";
        MonModuleInfo info = aa.init(new MNode(host, ad, null, null), arg);
        int freq = 20000;
        try {
            freq = Integer.parseInt(args[2]);
        } catch (Throwable t) {
            freq = 20000;
        }
        while (true) {
            Vector cc = (Vector) aa.doProcess();
            if (cc == null) {
                System.out.println("[SIM] No result: continue....");
                continue;
            }
            for (Iterator iter = cc.iterator(); iter.hasNext();) {
                System.out.println("[SIM%" + freq + "]  Result" + iter.next());
            }
            System.out.println("====================================");
            try {
                Thread.sleep(freq);
            } catch (Exception e) {
            }
        }

    }

}
