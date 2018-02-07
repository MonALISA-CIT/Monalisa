/*
 * $Id: snmp_IOpp_v2.java 7462 2014-01-19 23:08:39Z ramiro $
 */

package lia.Monitor.modules;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
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

/**
 * Network Traffic monitoring module <br>
 * Reports network traffic information of SNMP enabled devices
 *
 * @author Adrian Muraru
 */
public class snmp_IOpp_v2 extends snmpMon2 implements MonitoringModule {

    private static final long serialVersionUID = -272829661823288331L;

    private static final Logger logger = Logger.getLogger(snmp_IOpp_v2.class.getName());

    private final String[] sResTypes = new String[0]; // dynamic

    static final public String ModuleName = "snmp_IOpp_v2";

    static final public String OsName = "*";

    // list with the last measurement timestamps for each GET chunk
    private final List<Long> lLastMeasurements = new LinkedList<Long>();

    private static final double epsilon = 0.00001d;

    private volatile long last_measured = -1;

    private volatile long last_doProcess_time = -1;

    /**
     * counters type: MODE64/MODE32
     */
    private volatile int mode = MODE64;

    /**
     * aliases refresh (in number of polling periods)
     */
    private volatile int iStatsUpdateFreq = 20;

    /**
     * maximum number of interface requested in a single GET request. - required
     * to avoid hitting the maximum size of af response datagram
     */
    private volatile int maxIFsPerRequest = 10;

    // SNMP stuff

    // 64bit Counters ifHC{In,Out}Bytes
    static final String oidInHC = "1.3.6.1.2.1.31.1.1.1.6";

    static final String oidOutHC = "1.3.6.1.2.1.31.1.1.1.10";

    // 32bit Counters if{In,Out}Bytes
    static final String oidIn = "1.3.6.1.2.1.2.2.1.10";

    static final String oidOut = "1.3.6.1.2.1.2.2.1.16";

    // 32bit Counters if{In,Out}Err packets
    static final String oidIfInErr = "1.3.6.1.2.1.2.2.1.14";

    static final String oidIfOutErr = "1.3.6.1.2.1.2.2.1.20";

    // ifDescr - interface decription ID - > eth_name
    static final String oidIfDescr = "1.3.6.1.2.1.2.2.1.2";

    /* bandwidth */
    /* ifSpeed - in bps, but may be 4.294.967.295 */
    static final String oidIfSpeed = "1.3.6.1.2.1.2.2.1.5";

    // ifHighSpeed - in Mbps 1.3.6.1.2.1.31.1.1.1.15
    static final String oidIfHighSpeed = "1.3.6.1.2.1.31.1.1.1.15";

    /*
     * The desired state of the interface: (INTEGER) 1 : up 2 : down 3 : testing
     */
    static final String oidIfAdminStatus = "1.3.6.1.2.1.2.2.1.7";

    /*
     * The current operational state of the interface: (INTEGER) 1 : up 2 : down
     * 3 : testing 4 : unknown 5 : dormant 6 : notPresent 7 : lowerLayerDown
     */
    static final String oidIfOperStatus = "1.3.6.1.2.1.2.2.1.8";

    private final List<MonRouterInterface> lInterfaces = new ArrayList<MonRouterInterface>();

    private StringBuilder sModuleConfiguration;

    // show stats flag
    private boolean bShowStats = true;

    // activate workaround for broken routers when th444e counters are very slowly
    // updated
    private boolean bSlowCountersUpdateWorkaround = false;

    private double dTransportThresholdPercent = 1.0d;

    private double dFluctuationThresholdPercent = 1.0d;

    private boolean bShouldReportFluctuations = true;

    public snmp_IOpp_v2() {
        // nothing to see here
    }

    /**
     * @see lia.Monitor.monitor.MonitoringModule#init(lia.Monitor.monitor.MNode, java.lang.String)
     */
    @Override
    public MonModuleInfo init(MNode node, String args) {
        try {

            this.Node = node;
            init_args(args);
            init(node);
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO,
                        "SNMP module configuration for [ " + Node.getClusterName() + "/" + Node.getName() + " ]: "
                                + this.toString());
            }

        } catch (SocketException e) {
            // severe init error, cannot continue..
            logger.log(Level.SEVERE, "[SNMP] CommInterface could not be initialized", e);
            info.addErrorCount();
            info.setState(1); // error
            info.setErrorDesc("CommInterface could not be initialized");
        } catch (Exception e) { // in case of other exception ... set error
            // state in module
            logger.log(Level.SEVERE, "[SNMP] Error while  parsing the module configuration.", e);
            info.addErrorCount();
            info.setState(1); // error
            info.setErrorDesc("[SNMP] Error while  parsing the module configuration." + e.getMessage());
        }

        // this.mode = MODE64;
        info.ResTypes = sResTypes;
        info.setName(ModuleName);
        return info;
    }

    /*
     * configuration template: snmp_IOpp_v2{params}%30 params:
     * [SNMP_community=mycommunity,SNMP_Version=2c,SNMP_RemoteAddress=x.x.x.x,SNMP_RemotePort=2161,SNMP_LocalAddress=x.x.
     * x.x,SNMP_Timeout=xx,CanSuspend=false];if1=IF1_NAME;if2=IF2_NAME
     */
    private void init_args(String list) throws Exception {
        String[] splittedArgs = list.split("(\\s)*;+(\\s)*");

        if ((splittedArgs == null) || (splittedArgs.length == 0)) {
            throw new Exception(" Invalid ARGS " + list + " for [ " + Node.getClusterName() + "/" + Node.getName()
                    + " ]");
        }

        int iCount = splittedArgs.length;
        int iIndex = 0;

        if (bShowStats) {
            sModuleConfiguration = new StringBuilder();
        }

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
                        } else if ("SNMP_Timeout".equalsIgnoreCase(sParam)) {
                            try {
                                iReadTimeOut = Integer.valueOf(sValue).intValue();
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, "Could not understand  SNMP_Timeout configuration: "
                                        + element);
                            }
                        } else if ("SNMP_receiveBufferSize".equalsIgnoreCase(sParam)) {
                            try {
                                snmpReceiveBufferSize = Integer.valueOf(sValue).intValue();
                            } catch (Throwable t) {
                                logger.log(Level.WARNING,
                                        "Could not understand  SNMP_receiveBufferSize configuration: " + element);
                            }
                        } else if ("SNMP_MaxIFsPerRequest".equalsIgnoreCase(sParam)) {
                            try {
                                maxIFsPerRequest = Integer.valueOf(sValue).intValue();
                                if (maxIFsPerRequest <= 0) {
                                    maxIFsPerRequest = 10;
                                }
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, "Could not understand SNMP_MaxIFsPerRequest configuration: "
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
                                logger.log(Level.WARNING, "Could not understand ShowStats configuration parameter: "
                                        + element);
                                bShowStats = true;
                            }
                        } else if ("StatsUpdateFreq".equalsIgnoreCase(sParam)) {
                            try {
                                iStatsUpdateFreq = Integer.valueOf(sValue).intValue();
                            } catch (Throwable t) {
                                logger.log(Level.WARNING,
                                        "Could not understand SlowCountersUpdateWorkaround configuration parameter: "
                                                + element);
                                bSlowCountersUpdateWorkaround = false;
                            }
                        } else if ("SlowCountersUpdateWorkaround".equalsIgnoreCase(sParam)) {
                            try {
                                bSlowCountersUpdateWorkaround = Boolean.valueOf(sValue).booleanValue();
                            } catch (Throwable t) {
                                logger.log(Level.WARNING,
                                        "Could not understand SlowCountersUpdateWorkaround configuration parameter: "
                                                + element);
                                bSlowCountersUpdateWorkaround = false;
                            }
                        } else if ("TransportThresholdPercent".equalsIgnoreCase(sParam)) {
                            try {
                                dTransportThresholdPercent = Double.valueOf(sValue).doubleValue();
                            } catch (Throwable t) {
                                logger.log(Level.WARNING,
                                        "Could not understand TransportThresholdPercent configuration parameter: "
                                                + element);
                                dTransportThresholdPercent = 0.5d;
                            }
                        } else if ("FluctuationThresholdPercent".equalsIgnoreCase(sParam)) {
                            try {
                                dFluctuationThresholdPercent = Double.valueOf(sValue).doubleValue();
                            } catch (Throwable t) {
                                logger.log(Level.WARNING,
                                        "Could not understand FluctuationThresholdPercent configuration parameter: "
                                                + element);
                                dFluctuationThresholdPercent = 0.3d;
                            }
                        } else if ("ShouldReportFluctuations".equalsIgnoreCase(sParam)) {
                            try {
                                bShouldReportFluctuations = Boolean.valueOf(sValue).booleanValue();
                            } catch (Throwable t) {
                                logger.log(Level.WARNING,
                                        "Could not understand ShouldReportFluctuations configuration parameter: "
                                                + element);
                                bShouldReportFluctuations = true;
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

        for (int i = 0; i < iCount; i++) {
            String[] ss = splittedArgs[iIndex++].split("(\\s)*=(\\s)*");
            if (ss.length < 2) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "Skip Invalid interface specification: " + splittedArgs[iIndex - 1]);
                }
                continue;
            }

            String pname = ss[1].trim();
            String ifName = ss[0].trim();

            // create a new interface to be monitored
            MonRouterInterface iface = new MonRouterInterface();
            try {
                int nport = Integer.parseInt(ifName);
                // interface specified as "ifIndex" (int)
                iface.bStatic = true;
                iface.sID = ifName;
                iface.ifIndex = nport;
            } catch (NumberFormatException e) {
                // interface specified by ifDescr
                iface.bStatic = false;
                iface.sID = ifName;
                iface.ifIndex = -1;
            }
            iface.sMLAlias = pname;
            lInterfaces.add(iface);

            // report in _Stats cluster the config
            if (bShowStats) {
                sModuleConfiguration.append(ifName).append("=").append(pname).append(";");
            }

        }

        if (lInterfaces.size() <= 0) {
            throw new Exception("No interface defined");
        }

        if (logger.isLoggable(Level.FINEST)) {
            StringBuilder sb = new StringBuilder();
            for (MonRouterInterface monRouterInterface : lInterfaces) {
                sb.append(monRouterInterface + " ");
            }
            logger.log(Level.FINEST, "SNMP IDs:" + sb.toString());
        }
    }

    /**
     * @see lia.Monitor.monitor.MonitoringModule#ResTypes()
     */
    @Override
    public String[] ResTypes() {
        return info.ResTypes;
    }

    /**
     * @see lia.Monitor.monitor.MonitoringModule#getOsName()
     */
    @Override
    public String getOsName() {
        return OsName;

    }

    /**
     * @see lia.Monitor.monitor.MonitoringModule#doProcess()
     */
    private int resolveCnt = 0;

    @Override
    public Object doProcess() throws Exception {
        if (info.getState() != 0) {
            throw new IOException("[SNMP: " + this.Node.getName() + "]  Module could not be initialized");
        }
        final Vector<Object> vResult = new Vector<Object>();

        // we refresh the aliases and report general information about the
        // router once at every *iStatsUpdateFreq* polling gperiods
        if (resolveCnt++ == 0) {
            resolveAliases();
            if (updateSpeeds() == 0) {
                info.addErrorCount();
                info.setErrorDesc("[SNMP: " + this.Node.getName()
                        + "]  No ifIndex match the configured list of interfaces...");
                logger.warning("[SNMP: " + this.Node.getName()
                        + "]  No ifIndex match the configured list of interfaces...");
                return null;
            }
            // else
            if (bShowStats) {
                Result rResult = new Result(Node.getFarmName(), Node.getClusterName() + "_Stats", Node.getName(),
                        ModuleName, null);
                eResult strResult = new eResult(Node.getFarmName(), Node.getClusterName() + "_Stats", Node.getName(),
                        ModuleName, null);

                strResult.time = rResult.time = NTPDate.currentTimeMillis();
                for (MonRouterInterface iface : lInterfaces) {
                    if (iface.dSpeed > 0) {
                        rResult.addSet(iface.sMLAlias + "_SPEED", iface.dSpeed);
                    } else {
                        final double mlSpeedCfg = AppConfig.getd(iface.sID + "_SPEED", -1);
                        if (mlSpeedCfg > 0) {
                            iface.dSpeed = mlSpeedCfg;
                            logger.log(Level.INFO, "Overriding iface speed for: " + iface.sID + " with " + mlSpeedCfg);
                            rResult.addSet(iface.sMLAlias + "_SPEED", iface.dSpeed);
                        }
                    }
                    strResult.addSet(iface.sMLAlias + "_IF", iface.sID);
                }

                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "[SNMP: " + this.Node.getName() + "]: speeds updated " + rResult);
                }

                // speeds
                if ((rResult.param != null) && (rResult.param.length != 0)) {
                    StringFactory.convert(rResult);
                    vResult.addElement(rResult);
                }

                // module configuration
                strResult.addSet("SNMP_Version", iSNMPVersion == SNMPV1 ? "v1" : "v2c");
                strResult.addSet("HC_Enabled", mode == MODE64 ? "true" : "false");
                StringFactory.convert(strResult);
                vResult.addElement(strResult);
            }// if ShowStats
        }

        if (resolveCnt >= iStatsUpdateFreq) {
            resolveCnt = 0;
        }

        // report status for each interface
        updateStatus();
        if (bShowStats) {
            Result rResult = new Result(Node.getFarmName(), Node.getClusterName() + "_Stats", Node.getName(),
                    ModuleName, null);
            rResult.time = NTPDate.currentTimeMillis();
            for (MonRouterInterface iface : lInterfaces) {
                if (iface.iAdminStatus > 0) {
                    rResult.addSet(iface.sMLAlias + "_AdminStatus", iface.iAdminStatus);
                }
                if (iface.iOperStatus > 0) {
                    rResult.addSet(iface.sMLAlias + "_OperStatus", iface.iOperStatus);
                }
                if (iface.iInErrors >= 0) {
                    rResult.addSet(iface.sMLAlias + "_InErrors", iface.iInErrors);
                }
                if (iface.iOutErrors >= 0) {
                    rResult.addSet(iface.sMLAlias + "_OutErrors", iface.iOutErrors);
                }
            }

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "[SNMP: " + this.Node.getName() + "]: status updated " + rResult);
            }

            // status
            if ((rResult.param != null) && (rResult.param.length != 0)) {
                StringFactory.convert(rResult);
                vResult.addElement(rResult);
            }
        }

        // report traffic on interfaces
        // a list of maps : each element in the list is a map containing a
        // timeTicksOID timestamp and the
        // traffic information for interfaces at this time. It is a list since
        // the request might have been splitted
        List<Map<String, SNMPObject>> results = null;
        try {
            results = lmResults();
        } catch (SNMPGetException sge) {
            // the OIDs cannot be retrieved fallback to MODE32
            logger.log(
                    Level.INFO,
                    "[SNMP: "
                            + this.Node.getName()
                            + "] SNMP-Get-Exception: ("
                            + sge.getMessage()
                            + " ErrorStatus:"
                            + sge.errorStatus
                            + ") Reason could be  that specified variable not supported by device, or that supplied community name has insufficient privileges");
            if (mode == MODE64) {
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "[SNMP: " + this.Node.getName()
                            + "] 64bit Counters seems to not be supported. Fallback to 32bit counters");
                }
                // mode = MODE64;
                return null0Conf(vResult);
            }
            resetOldCounters();
            return null0Conf(vResult);
        } catch (Throwable t) {
            // other errors
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "[SNMP: " + this.Node.getName()
                        + "] Error while trying to poll traffic counters: ", t);
            } else if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "[SNMP: " + this.Node.getName()
                        + "] Error while trying to poll traffic counters: " + t.getMessage());
            }
            // reset old counters
            resetOldCounters();
            // set error status
            info.setErrorDesc("[SNMP: " + this.Node.getName() + "] Error while trying to poll traffic counters: "
                    + t.getMessage());
            info.addErrorCount();
            return null0Conf(vResult);
        }

        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("[SNMP: " + this.Node.getName() + "]  Got traffic counters:" + results);
        }

        int iTicksIndex = 0;
        for (Iterator<Map<String, SNMPObject>> iterator = results.iterator(); iterator.hasNext(); iTicksIndex++) {

            Map<String, SNMPObject> res = iterator.next();
            long snmpTime = -1;
            try {
                Object oTime = res.get(timeTicksOID);
                oTime = ((SNMPTimeTicks) oTime).getValue();
                snmpTime = ((BigInteger) oTime).longValue();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[SNMP: " + this.Node.getName()
                        + "] Got exception trying to read the time from SNMP", t);
                return null0Conf(vResult);
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "[SNMP: " + this.Node.getName() + "] chunk size: " + res.size());
            }

            long dt = 0;
            long rightNow = NTPDate.currentTimeMillis();

            if (lLastMeasurements.size() <= iTicksIndex) { // first time, we initialise the ticks
                last_measured = -1;
                lLastMeasurements.add(Long.valueOf(last_measured));
            } else {
                last_measured = (lLastMeasurements.get(iTicksIndex)).longValue();
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "[SNMP: " + this.Node.getName() + "] Last measurement:" + iTicksIndex + " = "
                        + last_measured + ", all ticks:" + lLastMeasurements);
            }

            if (snmpTime > 0) {
                if (last_measured >= snmpTime) {// restarted ?
                    logger.log(Level.WARNING, "[SNMP: " + this.Node.getName() + "] last_measured [" + last_measured
                            + " ] >= snmpTime [ " + snmpTime + " ] Was the device [ " + Node.getName()
                            + " ] restarted ?");
                    // last_measured = snmpTime;
                    lLastMeasurements.set(iTicksIndex, Long.valueOf(snmpTime));
                    // mode = MODE64;
                    resetOldCounters();
                    return null0Conf(vResult);
                }

                dt = snmpTime - last_measured;
                lLastMeasurements.set(iTicksIndex, Long.valueOf(snmpTime));
                // last_measured = snmpTime;

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "[SNMP: " + this.Node.getName() + "] [ " + new Date(rightNow)
                            + " ] --> SNMP dt = " + dt + " Date DT " + (rightNow - last_doProcess_time)
                            + " rightNow(ms Since) = " + rightNow);
                }
                last_doProcess_time = rightNow;
            } else {
                logger.log(Level.INFO, "[SNMP: " + this.Node.getName() + "] Negative SNMP Time ?! " + snmpTime);
                return null0Conf(vResult);
            }

            if (dt == 0) {
                logger.log(
                        Level.INFO,
                        "[SNMP: "
                                + this.Node.getName()
                                + "]  diff == 0 for SNMPtime for "
                                + Node.getName()
                                + " ... probably the counters still not updated (... SNMP has high Load or SNMP queries done too often )");
                return null0Conf(vResult);
            }

            Result rResult = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(), ModuleName, null);
            rResult.time = rightNow;

            /**
             * Iterate over the types and return the result
             */

            String _baseIN = ((mode == MODE64) ? oidInHC : oidIn) + ".";
            String _baseOUT = ((mode == MODE64) ? oidOutHC : oidOut) + ".";

            int iActiveCounters = lInterfaces.size() * 2;
            int iUnknownCounters = 0;

            for (MonRouterInterface iface : lInterfaces) {
                if (iface.ifIndex < 0) {
                    iActiveCounters -= 2;
                    continue; // not resolved yet
                }
                String[] _oidsINOUT = new String[] { _baseIN, _baseOUT };
                for (int j = 0; j < _oidsINOUT.length; j++) {
                    String _oid = _oidsINOUT[j] + iface.ifIndex;
                    if (res.containsKey(_oid)) {
                        double diff = -1;
                        Object value = res.get(_oid);
                        if (mode == MODE64) {
                            if (!(value instanceof SNMPCounter64)) {
                                logger.log(Level.WARNING, "[SNMP: " + this.Node.getName()
                                        + "]  Invalid SNMPCounter64 value " + value.getClass() + " for OID: " + _oid
                                        + " ifIndex: " + iface + " ...Skipping it");
                                iface.resetCounter(j);
                                iUnknownCounters++;
                                continue;
                            }
                            SNMPCounter64 cnt64 = (SNMPCounter64) value;
                            iface.vCounters[j].biCurrentCounter = (BigInteger) cnt64.getValue();

                        } else {
                            if (!(value instanceof SNMPCounter32)) {
                                logger.log(Level.WARNING, "[SNMP: " + this.Node.getName()
                                        + "]  Invalid SNMPCounter32 value " + value.getClass() + " for OID: " + _oid
                                        + " ifIndex: " + iface + " ...Skipping it");
                                iface.resetCounter(j);
                                iUnknownCounters++;
                                continue;
                            }
                            SNMPCounter32 cnt32 = (SNMPCounter32) value;
                            iface.vCounters[j].biCurrentCounter = (BigInteger) cnt32.getValue();
                        }

                        // first step?
                        if (iface.vCounters[j].biPreviousCounter == null) {
                            iface.vCounters[j].stepCounter();
                            continue;
                        }

                        diff = iface.vCounters[j].biCurrentCounter.subtract(iface.vCounters[j].biPreviousCounter)
                                .doubleValue();
                        if (diff < 0) {
                            logger.log(Level.WARNING, "[SNMP: " + this.Node.getName() + "]  Diff neg ---> " + iface
                                    + iface.vCounters[j].getSName() + " ] New: " + iface.vCounters[j].biCurrentCounter
                                    + " Old: " + iface.vCounters[j].biPreviousCounter + " diff: " + diff);
                            iface.vCounters[j].reset();
                            continue;
                        }
                        // else, diff >0
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "[SNMP: " + this.Node.getName() + "]  ---> " + iface
                                    + iface.vCounters[j].getSName() + " ] New: " + iface.vCounters[j].biCurrentCounter
                                    + " Old: " + iface.vCounters[j].biPreviousCounter + " diff: " + diff);
                        }

                        // return rate in Mbps
                        // time from SNMP is in hundredths of a second
                        double rate = ((diff / (10000.0D * dt)) * 8.0D) + iface.vCounters[j].dTransport;
                        iface.vCounters[j].dTransport = 0d;

                        if ((iface.dSpeed > 0) && (rate > iface.dSpeed)) {

                            iface.vCounters[j].dTransport = rate - iface.dSpeed;
                            rate = iface.dSpeed;

                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, "[SNMP: " + this.Node.getName() + "]  Exceed Rate " + " [ "
                                        + iface + iface.vCounters[j].getSName() + " ] " + iface.vCounters[j].dTransport);
                            }

                            if (iface.vCounters[j].dTransport > (iface.dSpeed * dTransportThresholdPercent)) {
                                if (logger.isLoggable(Level.WARNING)) {
                                    logger.log(Level.WARNING, "[SNMP: " + this.Node.getName() + "] Rate transport ["
                                            + iface.vCounters[j].dTransport + "] is too high in (" + iface
                                            + iface.vCounters[j].getSName() + ") speed=" + iface.dSpeed);
                                }
                                // broken routers often update too late the
                                // counters
                                // counters, in this case the transport should
                                // not
                                // be discarded...
                                if (bSlowCountersUpdateWorkaround) {
                                    iface.vCounters[j].dTransport = 0;
                                }
                            }
                        }
                        // Check fluctuations : the difference beetwen the
                        // current
                        // rate and thre privious one should be under a
                        // controlled
                        // threshold
                        double dCheckedRate = checkTrafficRate(iface.vCounters[j].dPreviousRate, rate,
                                dFluctuationThresholdPercent * iface.dSpeed);
                        final boolean checkOK = ((rate >= (dCheckedRate - epsilon)) && (rate <= (dCheckedRate + epsilon)));
                        if (!checkOK) {
                            logger.log(Level.WARNING, "[SNMP: " + this.Node.getName()
                                    + "] Too big fluctuations detected in (" + iface + iface.vCounters[j].getSName()
                                    + "): PreviousRate:" + iface.vCounters[j].dPreviousRate + " CurrentRate:" + rate
                                    + "  FluctuationThreshold:" + dFluctuationThresholdPercent + "Value reported:"
                                    + dCheckedRate);
                            if (bShouldReportFluctuations) {
                                rResult.addSet(iface.sMLAlias + iface.vCounters[j].getSName(), dCheckedRate);
                            }
                        } else {
                            // no fluctuations
                            rResult.addSet(iface.sMLAlias + iface.vCounters[j].getSName(), rate);
                        }

                        iface.vCounters[j].dPreviousRate = rate;
                        iface.vCounters[j].stepCounter();
                    }// ifContains
                }// for counters
            }// interface interator

            // if we received 2*no of interfaces "SNMPUnknownObject" and we have
            // at
            // least one monitored iface then switch to MODE32
            // System.out.println("DEBUG:" + iUnknownCounters + " :" +
            // iActiveCounters);
            if ((iActiveCounters > 0) && (iUnknownCounters >= iActiveCounters)) {
                if (mode == MODE64) {
                    logger.log(Level.INFO, "[SNMP: " + this.Node.getName()
                            + "] 64bit IF-Counters seems to not be supported. Fallback to 32bit counters");
                    // mode = MODE64;
                } else {
                    logger.log(Level.INFO, "[SNMP: " + this.Node.getName()
                            + "] IF-Counters seems to not be supported. Check if SNMP agent exports IF-MIB");
                    // set error status
                    info.setErrorDesc("[SNMP: " + this.Node.getName()
                            + "] IF-Counters seems to not be supported. Check if SNMP agent exports IF-MIB");
                    info.addErrorCount();
                }
            }

            if ((rResult.param_name == null) || (rResult.param_name.length == 0)) {
                return null0Conf(vResult);
            }

            info.setLastMeasurement(rResult.time);
            vResult.addElement(rResult);
        } // result iterator

        return vResult;
    }

    private double checkTrafficRate(double dPreviousRate, double dCurrentRate, double dMaxFluctuation) {
        if ((dPreviousRate <= 0) || (Math.abs(dCurrentRate - dPreviousRate) <= dMaxFluctuation)) {
            return dCurrentRate;
        } else if (dCurrentRate < dPreviousRate) {
            return dPreviousRate - dMaxFluctuation;
        } else {
            return dPreviousRate + dMaxFluctuation;
        }

    }

    private Object null0Conf(Vector<Object> vResult) {
        return vResult.isEmpty() ? null : vResult;
    }

    /** * <SNMP helper methods> ** */

    /**
     * Query the traffic information (in/out counters)
     *
     * @return A list of maps, each map containing the OID->result mappings and
     *         a SINGLE one timestamp entry. Multiple map elements may be
     *         returned if splitting is required
     * @throws SNMPGetException
     * @throws SNMPBadValueException
     * @throws IOException
     */
    private List<Map<String, SNMPObject>> lmResults() throws SNMPGetException, SNMPBadValueException, IOException {

        List<String> oids = new ArrayList<String>();
        String[] itemIDs;

        // list of maps
        List<Map<String, SNMPObject>> lmResults = new ArrayList<Map<String, SNMPObject>>();

        for (MonRouterInterface iface : lInterfaces) {
            if (iface.ifIndex < 0) {
                continue;
            }
            String _oidin = ((mode == MODE32) ? oidIn : oidInHC) + "." + iface.ifIndex;
            oids.add(_oidin);
            String _oidout = ((mode == MODE32) ? oidOut : oidOutHC) + "." + iface.ifIndex;
            oids.add(_oidout);
        }

        // split the request
        List<String> chunk;
        int iChunkIndex;
        while (oids.size() > 0) {
            iChunkIndex = oids.size() - (maxIFsPerRequest * 2);
            if (iChunkIndex < 0) {
                iChunkIndex = 0;
            }
            chunk = oids.subList(iChunkIndex, oids.size());
            // for each chunk we request the timestamp
            itemIDs = chunk.toArray(new String[chunk.size() + 1]);
            itemIDs[itemIDs.length - 1] = timeTicksOID;
            lmResults.add(super.snmpBulkGet(itemIDs));
            chunk.clear();
        }

        return lmResults;

    }

    /**
     * walk ifDescr( "1.3.6.1.2.1.2.2.1.2" ) OID
     */
    private Map<String, String> getAliases() throws SNMPGetException, IOException, SNMPBadValueException {

        Map<String, String> mAliasPort = new HashMap<String, String>();

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
        // just a two-element SNMPSequence
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
            // snmpValue.getClass().getName() + ", value " +
            // snmpValue.toString());
            int iPort = snmpOID.toString().indexOf(baseID) + baseID.length();

            mAliasPort.put(snmpValue.toString(), snmpOID.toString().substring(iPort + 1));
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "[SNMP: " + this.Node.getName() + "] Aliases" + mAliasPort);
        }
        return mAliasPort;
    }

    private void resolveAliases() {
        try {
            Map<String, String> mAliasPort = getAliases();
            String sIndex;
            // refresh the indexes for the current interfaces (dynamically
            // managed)
            for (MonRouterInterface iface : lInterfaces) {
                if (iface.bStatic) {
                    continue;
                }
                if (mAliasPort.containsKey(iface.sID)) {
                    try {
                        sIndex = mAliasPort.get(iface.sID);
                        int newIndex = Integer.parseInt(sIndex);
                        if ((iface.ifIndex != -1) && (iface.ifIndex != newIndex) && logger.isLoggable(Level.INFO)) {
                            logger.log(Level.INFO, "[SNMP: " + this.Node.getName() + "] Map changed" + iface
                                    + "Old IfIndex:" + iface.ifIndex + " New IfIndex:" + newIndex);
                        }
                        // update current index
                        iface.ifIndex = Integer.parseInt(sIndex);
                    } catch (Throwable e) {
                        iface.ifIndex = -1;
                    }
                }
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "[SNMP: " + this.Node.getName() + "]  Exception while trying to get if aliases",
                        t);
            } else if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "[SNMP: " + this.Node.getName()
                        + "] Exception while trying to get if aliases: " + t.getMessage());
            }
            info.setErrorDesc("Exception while trying to get if aliases");
            info.addErrorCount();
        }
    }

    /**
     * Updates the status for the monitored interfaces using :<br>
     * <ul>
     * <li>ifOperStatus and ifAdminStatus OIDs</li>
     * <li>ifInErrors and IfOutErrors OIDs</li>
     * </ul>
     */
    private int updateStatus() {
        int iStatusUpdated = 0;
        try {
            // map port->bw
            Map<String, int[]> mStatus = getStatus();
            if (mStatus.size() <= 0) {
                return 0;
            }
            int[] status;
            for (MonRouterInterface iface : lInterfaces) {
                String key = Integer.toString(iface.ifIndex);
                if (mStatus.containsKey(key)) {
                    status = mStatus.get(key);
                    if (status.length != 4) {
                        continue; // this should never happen.
                    }
                    iface.iAdminStatus = status[0];
                    iface.iOperStatus = status[1];
                    iface.iInErrors = status[2];
                    iface.iOutErrors = status[3];
                    iStatusUpdated++;
                }
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "[SNMP: " + this.Node.getName()
                        + "] Severe error while trying to update if-status", t);
            } else if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "[SNMP: " + this.Node.getName()
                        + "] Severe error while trying to update if-status: " + t.getMessage());
            }

            info.setErrorDesc("Exception while trying to update if-status" + t.getMessage());
            info.addErrorCount();
        }
        return iStatusUpdated;
    }

    /**
     * Query ifAdminStatus,ifOperStatus,ifInErrors, ifOutErrors
     *
     * @return
     * @throws SNMPGetException
     * @throws IOException
     * @throws SNMPBadValueException
     */
    private Map<String, int[]> getStatus() throws SNMPGetException, IOException, SNMPBadValueException {

        // map port -> [adminStatus,operStatus] : -1 value as status meaning
        // *unavailable*
        Map<String, int[]> mResults = new HashMap<String, int[]>();

        SNMPObject snmpValue;
        String snmpOID;

        List<String> chunk;
        int iChunkIndex;

        List<String> oids = new ArrayList<String>();
        for (MonRouterInterface iface : lInterfaces) {
            if (iface.ifIndex < 0) {
                continue;
            }
            oids.add(oidIfOperStatus + "." + iface.ifIndex);
            oids.add(oidIfAdminStatus + "." + iface.ifIndex);
            oids.add(oidIfInErr + "." + iface.ifIndex);
            oids.add(oidIfOutErr + "." + iface.ifIndex);
        }

        while (oids.size() > 0) {
            iChunkIndex = oids.size() - (maxIFsPerRequest * 4);
            if (iChunkIndex < 0) {
                iChunkIndex = 0;
            }
            chunk = oids.subList(iChunkIndex, oids.size());
            String[] itemIDs = chunk.toArray(new String[chunk.size()]);
            chunk.clear();
            Set<Map.Entry<String, SNMPObject>> ifStatuses = super.snmpBulkGet(itemIDs).entrySet();
            for (Map.Entry<String, SNMPObject> element : ifStatuses) {
                snmpOID = element.getKey();
                snmpValue = element.getValue();

                if (!(snmpValue instanceof SNMPInteger)) {
                    logger.warning("[SNMP: " + this.Node.getName()
                            + "] Got an invalid SNMP type for if[Admin|Oper|InErr|OutErr]Status for  OID[" + snmpOID
                            + ": " + snmpValue.getClass().toString() + " ...Skipping it");
                    continue;
                }

                int iDot = snmpOID.lastIndexOf('.');
                String sPort = snmpOID.substring(iDot + 1);
                // index
                int iStatus;
                if (snmpOID.startsWith(oidIfAdminStatus)) {
                    iStatus = 0;
                } else if (snmpOID.startsWith(oidIfOperStatus)) {
                    iStatus = 1;
                } else if (snmpOID.startsWith(oidIfInErr)) {
                    iStatus = 2;
                } else if (snmpOID.startsWith(oidIfOutErr)) {
                    iStatus = 3;
                } else {
                    continue; // unknown OID received?
                }

                if (!mResults.containsKey(sPort)) {
                    mResults.put(sPort, new int[] { -1, -1, -1, -1 });
                }
                int[] aStatuses = mResults.get(sPort);
                aStatuses[iStatus] = ((BigInteger) snmpValue.getValue()).intValue();

            }
        }

        return mResults;

    }

    /**
     * Update the interface speed
     *
     * @return the number of updates
     */
    private int updateSpeeds() {
        int iSpeedsUpdated = 0;
        try {
            // map port->bw
            Map<String, Double> mSpeeds = getHighSpeeds();
            if (mSpeeds.size() <= 0) {
                return 0;
            }
            for (MonRouterInterface iface : lInterfaces) {
                String key = Integer.toString(iface.ifIndex);
                if (mSpeeds.containsKey(key)) {
                    iface.dSpeed = (mSpeeds.get(key)).doubleValue();
                    iSpeedsUpdated++;
                }
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "[SNMP: " + this.Node.getName()
                        + "] Severe error while trying to update if-speeds", t);
            } else if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.FINE, "[SNMP: " + this.Node.getName()
                        + "] Severe error while trying to update if-speeds: " + t.getMessage());
            }

            info.setErrorDesc("Exception while trying to update if-speeds" + t.getMessage());
            info.addErrorCount();
        }
        return iSpeedsUpdated;
    }

    private Map<String, Double> getHighSpeeds() throws SNMPGetException, IOException, SNMPBadValueException {
        // map port->bw
        Map<String, Double> mResults = new HashMap<String, Double>();

        String[] itemIDs;

        SNMPObject snmpValue;
        String snmpOID;
        Set<Map.Entry<String, SNMPObject>> ifSpeeds;

        List<String> oids = new ArrayList<String>();
        // high-speed interfaces
        List<String> hiOids = new ArrayList<String>();

        for (MonRouterInterface iface : lInterfaces) {
            if (iface.ifIndex < 0) {
                continue;
            }
            String _oids = oidIfSpeed + "." + iface.ifIndex;
            oids.add(_oids);
        }

        // split the request
        List<String> chunk;
        int iChunkIndex;
        while (oids.size() > 0) {
            iChunkIndex = oids.size() - maxIFsPerRequest;
            if (iChunkIndex < 0) {
                iChunkIndex = 0;
            }
            chunk = oids.subList(iChunkIndex, oids.size());

            itemIDs = chunk.toArray(new String[chunk.size()]);
            // clearing the partition also removes from hiOids list
            chunk.clear();
            // query
            ifSpeeds = super.snmpBulkGet(itemIDs).entrySet();

            for (Map.Entry<String, SNMPObject> element : ifSpeeds) {
                snmpOID = element.getKey();
                snmpValue = element.getValue();

                /*
                 * Test if snmpValue is instaceof SNMPGauge32 if the ifIndex
                 * does not exist ... an SNMPUnknownObject is returned, so we
                 * should skip it
                 */
                if (!(snmpValue instanceof SNMPGauge32)) {
                    logger.warning("[SNMP: " + this.Node.getName() + "] Got an invalid SNMP type for SPEED for  OID["
                            + snmpOID + ": " + snmpValue.getClass().toString() + " ...Skipping it");
                    continue;
                }

                int iDot = snmpOID.lastIndexOf('.');
                String sPort = snmpOID.substring(iDot + 1);
                if (((BigInteger) snmpValue.getValue()).longValue() == 4294967295L) {
                    // try ifHighSpeed for this interface
                    hiOids.add(oidIfHighSpeed + "." + sPort);
                } else {
                    long lBw = ((BigInteger) snmpValue.getValue()).longValue();
                    mResults.put(sPort, Double.valueOf(lBw / 1000000.0D));
                }
            }
        }

        if (hiOids.size() <= 0) {
            return mResults;
        }

        // else if there some high-speed interfaces

        // split the request
        while (hiOids.size() > 0) {
            iChunkIndex = hiOids.size() - maxIFsPerRequest;
            if (iChunkIndex < 0) {
                iChunkIndex = 0;
            }
            chunk = hiOids.subList(iChunkIndex, hiOids.size());

            itemIDs = chunk.toArray(new String[chunk.size()]);

            ifSpeeds = super.snmpBulkGet(itemIDs).entrySet();
            for (Map.Entry<String, SNMPObject> element : ifSpeeds) {
                snmpOID = element.getKey();
                snmpValue = element.getValue();

                /*
                 * Test if snmpValue is instaceof SNMPGauge32 if the ifIndex
                 * does not exist ... an SNMPUnknownObject is returned, so we
                 * should skip it
                 */
                if (!(snmpValue instanceof SNMPGauge32)) {
                    logger.warning("[SNMP: " + this.Node.getName() + "]  Got an invalid SNMP type for SPEED for  OID["
                            + snmpOID + ": " + snmpValue.getClass().toString() + " ...Skipping it");
                    continue;
                }

                int iDot = snmpOID.lastIndexOf('.');
                String sPort = snmpOID.substring(iDot + 1);
                long lBw = ((BigInteger) snmpValue.getValue()).longValue();
                // in Mbps
                if (lBw > 0) {
                    mResults.put(sPort, Double.valueOf(lBw));
                }
            }
            // clearing the partition also removes from hiOids list
            chunk.clear();
        }
        return mResults;

    }

    /** * <Internal counters methods> ** */
    private void resetOldCounters() {
        for (MonRouterInterface iface : lInterfaces) {
            iface.resetAllCounters();
        }
    }

    private static final class MonRouterInterface {

        /**
         * staticaly specified (if set, the interface will not be monitored for
         * ifIndex updates)
         */
        boolean bStatic;

        /**
         * unique internal name
         */
        String sID = "";

        /**
         * SNMP index (used to query interface)
         */
        int ifIndex = -1;

        /**
         * ML label (name of the reported parameter)
         */
        public String sMLAlias = "";

        /**
         * high speed
         */
        double dSpeed = 0.0d;

        /**
         * adminStatus: 1=>up 2=>down 3=>testing
         */
        int iAdminStatus = -1;

        /**
         * operStatus 1=>up 2=>down 3=>testing 4=>unknown 5=>dormant
         * 6=>notPresent 7=>lowerLayerDown
         */
        int iOperStatus = -1;

        /**
         * In/Out errors - packets discarded by the device
         */
        int iInErrors = -1;

        int iOutErrors = -1;

        CounterData[] vCounters;

        MonRouterInterface() {
            // IN/OUT counters
            vCounters = new CounterData[] { new CounterData("_IN"), new CounterData("_OUT") };
        }

        void resetCounter(int i) {
            if (vCounters[i] != null) {
                vCounters[i].reset();
            }
        }

        void resetAllCounters() {
            for (int i = 0; i < vCounters.length; i++) {
                resetCounter(i);
            }
        }

        @Override
        public String toString() {
            return "[" + sMLAlias + "/" + sID + "/" + ifIndex + "/S:" + bStatic + "]";
        }
    }

    private static final class CounterData {

        String sName = "";

        /**
         * SNMP counter used to calculate the current rate
         */
        BigInteger biCurrentCounter, biPreviousCounter;

        /**
         * rate carry (current_rate - MAX_SPEED)
         */
        double dTransport = 0.0d;

        /**
         * prevous rate - used to check for SNMP spikes on high load
         */
        double dPreviousRate = -1d;

        CounterData(String sName) {
            this.sName = sName;
            biCurrentCounter = null;
            biPreviousCounter = null;
        }

        protected void stepCounter() {
            this.biPreviousCounter = biCurrentCounter;

        }

        protected void reset() {
            this.biPreviousCounter = this.biCurrentCounter = null;
            this.dTransport = 0.0;
        }

        @Override
        public String toString() {
            return "{CurrentCounter:" + biCurrentCounter.toString() + "}, {PreviousCounter:" + biPreviousCounter
                    + "}, RateTransport:" + dTransport;
        }

        String getSName() {
            return sName;
        }
    }

    /** * </Internal counters methods> ** */

    /**
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out
                    .println("Usage: \n\t snmp_IOpp_v2 host <conf> "
                            + "\n\n\t conf:[SNMP_community=mycommunity,SNMP_Version=2c,SNMP_RemoteAddress=x.x.x.x,SNMP_RemotePort=2161,SNMP_LocalAddress=x.x.x.x,SNMP_Timeout=xx,CanSuspend=false];if1=IF1_NAME;if2=IF2_NAME ");
            System.exit(1);
        }

        String host = args[0];
        snmp_IOpp_v2 aa = new snmp_IOpp_v2();

        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println("[SNMP: " + aa.Node.getName() + "]  Can not get ip for node " + e);
            System.exit(-1);
        }

        String arg = args[1]; // "eth0=LAN;lo=LOCAL";
        aa.init(new MNode(host, ad, null, null), arg);
        while (true) {
            Object res = aa.doProcess();
            System.out.println("[SNMP: " + aa.Node.getName() + "] [SIM]  Result" + res);
            Thread.sleep(20000);
        }

    }
}
