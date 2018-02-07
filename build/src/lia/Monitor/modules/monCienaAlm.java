/*
 * $Id: monCienaAlm.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.modules;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.Monitor.monitor.eResult;
import lia.util.DateFileWatchdog;
import lia.util.Utils;
import lia.util.ciena.CienaUtils;
import lia.util.ciena.ParsedCienaTl1Alarm;
import lia.util.mail.MailFactory;
import lia.util.ntp.NTPDate;
import lia.util.telnet.CienaTelnet;

/**
 * Basic alarm monitoring for Ciena CD/CI. Uses TL1 rtrv-alm-all command.
 * 
 * @author ramiro
 */
public class monCienaAlm extends cmdExec implements MonitoringModule, Observer {

    private static final long serialVersionUID = -7004222448425042098L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monCienaAlm.class.getName());

    private static final String TL1_CTAG = "ralm";

    private static final String TL1_CMD = "rtrv-alm-all:::" + TL1_CTAG + ";\n";

    public MNode Node;

    static String[] ResTypes = { "Port-Conn" };

    public MonModuleInfo info;

    public boolean isRepetitive = false;

    private final String moduleName;

    private volatile CienaTelnet cienaTL1Conn;

    private File lastAlarmsStateFile = null;

    private File lastAlarmsSerFile = null;

    private File oc192ConfFile = null;

    private static final AtomicReference<String> clusterNameOC192Alm = new AtomicReference<String>();

    public static final String OC192_TAG = "OC192";
    public static final String OCN_TAG = "OCN";

    /**
     * The key is a String which may appear inside the TL1 Alarm response; The value is the integer (1, 2 ... )
     * associated with this value
     */
    private final ConcurrentMap<String, Integer> internalNotifAlarms = new ConcurrentHashMap<String, Integer>();

    private final ConcurrentMap<String, String> oc192InterfacesMap = new ConcurrentHashMap<String, String>();

    // K - OC192 Interface ( ciena AID );
    // Value a TreeMap K alarmLine; V OC192AlmEntry
    private final ConcurrentMap<String, Map<String, OC192AlmEntry>> previousReportedAlarms = new ConcurrentHashMap<String, Map<String, OC192AlmEntry>>();

    private volatile File alarmsLogDir = null;

    private final Properties stateProps;

    private boolean shouldSaveState = false;

    private long lastReportedAlarm;

    private TreeSet<String> lastAlarms;

    private volatile boolean canLogAlarms;

    private boolean ignoreAlmTime = false;

    private static final AtomicBoolean shouldMonitorPerformance = new AtomicBoolean(AppConfig.getb(
            "lia.Monitor.modules.monCienaAlm.shouldMonitorPerformance", false));

    private static volatile String[] alarmRCPTs = new String[] { "ramiro@roedu.net", "ramiro.voicu@cern.ch" };

    private static volatile String[] smallAlmRCPTs = new String[] { "ramiro@roedu.net", "ramiro.voicu@cern.ch",
            "Artur.Barczyk@cern.ch" };

    private static final class OC192AlmEntry {

        private final String alarmLine;

        private final long startTime;

        private long lastMailSent;

        private final Integer alarmCode;

        private OC192AlmEntry(final String alarmLine, final long startTime, final Integer alarmCode) {
            if (alarmLine == null) {
                throw new NullPointerException("alarmLine cannot be null");
            }
            this.alarmLine = alarmLine;
            this.startTime = startTime;
            this.alarmCode = alarmCode;
        }

        @Override
        public String toString() {
            return "{OC192AlmEntry} TL1AlmLine: " + alarmLine + "; startTime: " + new Date(startTime)
                    + "; lastMailNotif: " + new Date(lastMailSent);
        }
    }

    static {
        reloadConfig();
        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                reloadConfig();
            }
        });
    }

    private static final void reloadConfig() {
        try {
            shouldMonitorPerformance.set(AppConfig.getb("lia.Monitor.modules.monCienaAlm.shouldMonitorPerformance",
                    false));
        } catch (Throwable t) {
            logger.log(
                    Level.WARNING,
                    " [ monCienaAlm ] got exception looking for lia.Monitor.modules.monCienaAlm.shouldMonitorPerformance ",
                    t);
            shouldMonitorPerformance.set(false);
        }

        try {
            final String[] s = AppConfig.getVectorProperty("lia.Monitor.modules.monCienaAlm.alarmRCPTs", alarmRCPTs);
            alarmRCPTs = s;
        } catch (Throwable t) {
            logger.log(Level.WARNING,
                    " [ monCienaAlm ] got exception looking for lia.Monitor.modules.monCienaAlm.alarmRCPTs ", t);
            alarmRCPTs = new String[] { "ramiro@roedu.net", "ramiro.voicu@cern.ch" };
        }

        try {
            final String[] s = AppConfig.getVectorProperty("lia.Monitor.modules.monCienaAlm.smallAlmRCPTs",
                    smallAlmRCPTs);
            smallAlmRCPTs = s;
        } catch (Throwable t) {
            logger.log(Level.WARNING,
                    " [ monCienaAlm ] got exception looking for lia.Monitor.modules.monCienaAlm.smallAlmRCPTs ", t);
            smallAlmRCPTs = new String[] { "ramiro@roedu.net", "ramiro.voicu@cern.ch" };
        }
    }

    public monCienaAlm() {
        moduleName = "monCienaAlm";
        isRepetitive = true;
        stateProps = new Properties();
        lastAlarms = new TreeSet<String>();
        canLogAlarms = true;
    }

    @Override
    public MonModuleInfo init(MNode Node, String arg) {
        this.Node = Node;
        logger.log(Level.INFO, "monCienaAlm: farmName=" + Node.getFarmName() + " clusterName= " + Node.getClusterName()
                + " nodeName=" + Node.getName() + " arg = " + arg);

        if (arg.startsWith("\"")) {
            arg = arg.substring(1);
        }

        if (arg.endsWith("\"")) {
            arg = arg.substring(0, arg.length() - 1);
        }

        String[] args = arg.split("(\\s)*;(\\s)*");
        if (args != null) {
            for (String arg2 : args) {
                try {
                    final String argT = arg2.trim();
                    if (argT.startsWith("lastAlarmsStateFile")) {
                        String[] fileTks = argT.split("(\\s)*=(\\s)*");
                        if ((fileTks == null) || (fileTks.length != 2)) {
                            logger.log(Level.WARNING, "monCienaAlm cannot parse lastAlarmsStateFile param [ " + argT
                                    + " ]  ... ");
                            continue;
                        }
                        lastAlarmsStateFile = new File(fileTks[1]);
                    } else if (argT.startsWith("lastAlarmsSerFile")) {
                        String[] fileTks = argT.split("(\\s)*=(\\s)*");
                        if ((fileTks == null) || (fileTks.length != 2)) {
                            logger.log(Level.WARNING, "monCienaAlm cannot parse lastAlarmsSerFile param [ " + argT
                                    + " ]  ... ");
                            continue;
                        }
                        lastAlarmsSerFile = new File(fileTks[1]);
                    } else if (argT.startsWith("alarmsLogDir")) {
                        String[] fileTks = argT.split("(\\s)*=(\\s)*");
                        if ((fileTks == null) || (fileTks.length != 2)) {
                            logger.log(Level.WARNING, "monCienaAlm cannot parse lastAlarmsSerFile param [ " + argT
                                    + " ]  ... ");
                            continue;
                        }
                        alarmsLogDir = new File(fileTks[1]);
                    } else if (argT.startsWith("ignoreAlarmTime")) {
                        ignoreAlmTime = true;
                    } else if (argT.startsWith("OC192ConfFile")) {
                        String[] fileTks = argT.split("(\\s)*=(\\s)*");
                        if ((fileTks == null) || (fileTks.length != 2)) {
                            logger.log(Level.WARNING, "monCienaAlm cannot parse OC192ConfFile param [ " + argT
                                    + " ]  ... ");
                            continue;
                        }
                        oc192ConfFile = new File(fileTks[1]);

                        if (oc192ConfFile.isFile() && oc192ConfFile.canRead()) {
                            try {
                                DateFileWatchdog.getInstance(oc192ConfFile, 5 * 1000).addObserver(this);
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, " monCienaAlm - Unable to instantiate watchdog for "
                                        + oc192ConfFile + ". Cause: ", t);
                            }
                        } else {
                            logger.log(
                                    Level.WARNING,
                                    "\n\n monCienaAlm - No OC192ConfFile defined or the file "
                                            + oc192ConfFile
                                            + " cannot be accessed. The module WILL NOT REPORT status on Sonet Intetrfaces!\n\n");
                            oc192ConfFile = null;
                        }
                    }

                } catch (Throwable t) {
                    logger.log(Level.WARNING, "monCienaAlm - exception parsing module params params", t);
                }
            }
        }

        boolean statePropsLoaded = false;

        // Init lastAlarmsStateFile
        try {
            if (lastAlarmsStateFile == null) {// nothing defined as module param
                String farmHome = AppConfig.getProperty("lia.Monitor.Farm.HOME");
                if (farmHome == null) {
                    logger.log(Level.WARNING,
                            "[monCienaAlm] Cannot determine lia.Monitor.Farm.HOME .... I will use the local direcotry");
                    farmHome = ".";
                }
                lastAlarmsStateFile = new File(farmHome + File.separator + "monCienaAlm_lastAlarmsStateFile.properties");
            }

            if (lastAlarmsStateFile.exists()) {
                if (lastAlarmsStateFile.canRead()) {
                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(lastAlarmsStateFile);
                        stateProps.load(fis);
                        statePropsLoaded = true;
                        logger.log(Level.INFO, "monCienaAlm - lastAlarmsStateFile loaded successfully");
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "monCienaAlm - exception loading lastAlarmsStateFile: "
                                + lastAlarmsStateFile, t);
                    } finally {
                        if (fis != null) {
                            try {
                                fis.close();
                            } catch (Throwable ignore) {
                            }
                        }
                    }

                    if (!lastAlarmsStateFile.canWrite()) {
                        logger.log(Level.WARNING, "monCienaAlm - lastAlarmsStateFile: " + lastAlarmsStateFile
                                + " does not have write access");
                    }
                } else {
                    logger.log(Level.WARNING, "monCienaAlm lastAlarmsStateFile: " + lastAlarmsStateFile
                            + " exists but cannot be read");
                }
            } else {
                if (!lastAlarmsStateFile.createNewFile()) {
                    logger.log(Level.WARNING, "[monCienaAlm] Cannot create lastAlarmsStateFile: " + lastAlarmsStateFile);
                }
            }
        } catch (Throwable t1) {
            logger.log(Level.WARNING, " monCienaAlm - exception initializing the lastAlarmsStateFile: "
                    + lastAlarmsStateFile, t1);
        }

        // Init lastAlarmsStateFile
        try {
            if (lastAlarmsSerFile == null) {// nothing defined as module param
                String farmHome = AppConfig.getProperty("lia.Monitor.Farm.HOME");
                if (farmHome == null) {
                    logger.log(Level.WARNING,
                            " Cannot determine lia.Monitor.Farm.HOME .... I will use the local direcotry");
                    farmHome = ".";
                }
                lastAlarmsSerFile = new File(farmHome + "/monCienaAlm_lastAlarmsSerFile.ser");
            }

            if (lastAlarmsSerFile.exists()) {
                if (lastAlarmsSerFile.canRead()) {

                    if (statePropsLoaded) {
                        FileInputStream fis = null;
                        ObjectInputStream ois = null;

                        try {
                            fis = new FileInputStream(lastAlarmsSerFile);
                            ois = new ObjectInputStream(fis);

                            lastAlarms = (TreeSet<String>) ois.readObject();
                            logger.log(Level.INFO, "[monCienaAlm] - lastAlarms loaded successfully");
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, "[monCienaAlm] - exception loading lastAlarmsSerFile: "
                                    + lastAlarmsSerFile, t);
                        } finally {
                            if (ois != null) {
                                try {
                                    ois.close();
                                } catch (Throwable ignore) {
                                }
                            }

                            if (fis != null) {
                                try {
                                    fis.close();
                                } catch (Throwable ignore) {
                                }
                            }

                        }
                    } else {
                        logger.log(Level.WARNING,
                                "[monCienaAlm] - did not load lastAlarmsSerFile because lastAlarmsStateFile failed to load!");
                    }

                    if (!lastAlarmsSerFile.canWrite()) {
                        logger.log(Level.WARNING, "[monCienaAlm] - lastAlarmsSerFile: " + lastAlarmsSerFile
                                + " does not have write access");
                    }
                } else {
                    logger.log(Level.WARNING, "[monCienaAlm] lastAlarmsSerFile: " + lastAlarmsSerFile
                            + " exists but cannot be read");
                }
            } else {
                if (!lastAlarmsSerFile.createNewFile()) {
                    logger.log(Level.WARNING, "[monCienaAlm] Cannot create lastAlarmsSerFile: " + lastAlarmsSerFile);
                }
            }
        } catch (Throwable t1) {
            logger.log(Level.WARNING, " monCienaAlm - exception initializing the lastAlarmsSerFile: "
                    + lastAlarmsSerFile, t1);
        }

        // Init alarmsLogDir
        try {
            if (alarmsLogDir == null) {// nothing defined as module param
                String farmHome = AppConfig.getProperty("lia.Monitor.Farm.HOME");
                if (farmHome == null) {
                    logger.log(Level.WARNING,
                            "[monCienaAlm] Cannot determine lia.Monitor.Farm.HOME .... I will use the local direcotry");
                    farmHome = ".";
                }
                alarmsLogDir = new File(farmHome + "/monCienaAlm_alarmsLogDir");
            }

            if (alarmsLogDir.exists()) {

                if (!alarmsLogDir.isDirectory()) {
                    logger.log(Level.WARNING, "monCienaAlm - alarmsLogDir: " + alarmsLogDir
                            + " exists but is not a directory. Alarms will not be logged!!");
                    canLogAlarms = false;
                } else if (!alarmsLogDir.canWrite()) {
                    logger.log(Level.WARNING, "monCienaAlm - alarmsLogDir: " + alarmsLogDir
                            + " exists but dows not have write access. Alarms will not be logged!!");
                    canLogAlarms = false;
                }
            } else {
                if (!alarmsLogDir.mkdirs()) {
                    logger.log(Level.WARNING, "monCienaAlm Cannot create alarmsLogDir: " + alarmsLogDir
                            + ". Alarms will not be logged!!");
                    canLogAlarms = false;
                }
            }
        } catch (Throwable t1) {
            logger.log(Level.WARNING, " monCienaAlm - exception initializing the alarmsLogDir: " + alarmsLogDir
                    + ". Alarms will not be logged!!", t1);
            canLogAlarms = false;
        }

        info = new MonModuleInfo();
        info.name = moduleName;
        info.ResTypes = ResTypes;

        try {
            if (oc192ConfFile != null) {
                reloadConf();
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " monCienaAlm - got exception in reloadConf()", t);
        }

        return info;
    }

    /**
     *
     *
     *
     */
    @Override
    public Object doProcess() throws Exception {

        final long perfStartTime = Utils.nanoNow();

        long sTime = System.currentTimeMillis();
        ArrayList<Object> v = new ArrayList<Object>();

        StringBuilder sb = null;

        String line = null;

        // OC192 status
        final String clName = clusterNameOC192Alm.get();

        try {
            lastReportedAlarm = Long.parseLong(stateProps.getProperty("lastReportedAlarm", "0"));
        } catch (Throwable t1) {
            lastReportedAlarm = 0;
            logger.log(Level.WARNING, "monCienaAlm - Cannot parse lastReportedError ", t1);
        }

        if (lastReportedAlarm == 0L) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "monCienaAlm - lastAlarms was cleared because lastReportedAlarm == 0L");
            }
            lastAlarms.clear();
        }

        long maxIterTime = lastReportedAlarm;
        boolean shouldSaveAlarms = false;

        StringBuilder logsToSave = null;
        final long now = NTPDate.currentTimeMillis();

        long perfStartCMDTime = Utils.nanoNow();
        long perfFinishCMDTime = perfStartTime;

        try {

            if (cienaTL1Conn == null) {
                cienaTL1Conn = CienaTelnet.getMonitorInstance();
            }

            perfStartCMDTime = Utils.nanoNow();
            sb = cienaTL1Conn.doCmd(TL1_CMD, TL1_CTAG);
            perfFinishCMDTime = Utils.nanoNow();

            // Output Format
            // SID DATE TIME
            // M CTAG COMPLD
            // "aid,aidType:aisnc,condType,[serviceEffect],date,time,[location],[direction]:[desc],[aidDetection]"
            // ;
            //
            // Example:
            // CHGO 07-05-09 09:58:05
            // M 1 COMPLD
            // "1-A-CM1,EQPT:MN,PWR,NSA,2007-04-12,02:44:13,,:\"PowerInArmB=Failed \","
            // "1-A-6,EQPT:MN,PWR,NSA,2007-04-12,02:44:13,,:\"PowerInArmB=Failed \","
            // "1-A-1-1,OC192:NA,TIM-S,NSA,2007-05-07,14:50:20,,:\"Received trace:test-from-nyc Expected:\","
            // "1-A-CM2,EQPT:MN,PWR,NSA,2007-04-12,02:44:13,,:\"PowerInArmB=Failed \","
            // "1-A-1,EQPT:MN,PWR,NSA,2007-04-12,02:44:13,,:\"PowerInArmB=Failed \","
            // "TimingInput_BITS_1,REF:MN,SYNCCLK,NSA,2007-03-28,22:35:21,,:\"LOS on synchronization reference as seen by TM1\","
            // "TimingInput_BITS_2,REF:MN,SYNCCLK,NSA,2007-03-28,22:35:21,,:\"LOS on synchronization reference as seen by TM1\","
            // "1-A-SM2,EQPT:MN,PWR,NSA,2007-04-12,02:44:13,,:\"PowerInArmB=Failed \","
            // "TimingInput,REF:MJ,SYNCCLK,NSA,2007-03-28,22:36:52,,:\"LOS on synchronization reference TimingInput_BITS_1 as seen by TM2. LOS on synchronization reference TimingInput_BITS_2 as seen by TM2.\","
            // "1-A-5,EQPT:MN,PWR,NSA,2007-04-12,02:44:13,,:\"PowerInArmB=Failed \","
            // "TimingInput,REF:MN,SYNCCLK,NSA,2007-03-28,22:36:50,,:\"Synchronization clock mode is Freerun for TM2\","
            // "TimingInput,REF:MJ,SYNCCLK,NSA,2007-03-28,22:35:21,,:\"LOS on synchronization reference TimingInput_BITS_1 as seen by TM1. LOS on synchronization reference TimingInput_BITS_2 as seen by TM1.\","
            // "1-A-4,EQPT:MN,PWR,NSA,2007-04-12,02:44:13,,:\"PowerInArmB=Failed \","
            // "1-A-SM1,EQPT:MN,PWR,NSA,2007-04-12,02:44:13,,:\"PowerInArmB=Failed \","
            // "1-A-2,EQPT:MN,PWR,NSA,2007-04-12,02:44:13,,:\"PowerInArmB=Failed \","
            // "TimingInput,REF:MN,SYNCCLK,NSA,2007-03-28,22:35:21,,:\"Synchronization clock mode is Freerun for TM1\","
            // "1-A-3,EQPT:MN,PWR,NSA,2007-04-12,02:44:13,,:\"PowerInArmB=Failed \","
            // "1-PDU-1,EQPT:MJ,RECT,NSA,2007-04-12,02:44:13,,:\"B-PWR-Status=Failed, may be caused by blown fuse or tripped Breaker B  \","
            // "TimingInput_BITS_1,REF:MN,SYNCCLK,NSA,2007-03-28,22:36:52,,:\"LOS on synchronization reference as seen by TM2\","
            // "TimingInput_BITS_2,REF:MN,SYNCCLK,NSA,2007-03-28,22:36:52,,:\"LOS on synchronization reference as seen by TM2\","
            // "1-A-SM3,EQPT:MN,PWR,NSA,2007-04-12,02:44:13,,:\"PowerInArmB=Failed \","
            // "1-A-SM4,EQPT:MN,PWR,NSA,2007-04-12,02:44:13,,:\"PowerInArmB=Failed \","
            // ;

            BufferedReader reader = new BufferedReader(new StringReader(sb.toString()));

            boolean started = false;
            boolean hasAlms = false;

            line = reader.readLine();
            shouldSaveState = false;

            Map<String, Integer> currentOC192ErrorStatus = null;
            Set<String> currentOC192Alarms = null;

            String prevLine = null;

            String cdciName = null;

            while (line != null) {
                line = line.trim();
                if (!started) {
                    if (line.startsWith("M ")) {
                        try {
                            cdciName = prevLine.trim().split("(\\s)+")[0];
                        } catch (Throwable t) {
                            cdciName = null;
                        }
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, "[ monCienaAlm ] cdciName: " + cdciName + "; start parsing");
                        }
                        started = true;
                    }
                    prevLine = line;
                    line = reader.readLine();
                    continue;
                }

                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, " [ monCienaAlm ] Processing line: [" + line + "]");
                }

                if (line.startsWith("\"") && line.endsWith("\"") && (line.length() >= 2)) {
                    try {
                        final ParsedCienaTl1Alarm tl1Response = CienaUtils.parseTL1ResponseLine(line);

                        final String condType = tl1Response.condType;
                        if (condType != null) {
                            final Integer alarmCode = internalNotifAlarms.get(condType);
                            if (alarmCode != null) {
                                // nice - Borat
                                logger.log(Level.INFO, "\n\n [ monCienaAlm ]  notif alarm condType: " + condType
                                        + " code: " + alarmCode + " line: " + line + " \n\n");

                                if (currentOC192Alarms == null) {
                                    currentOC192Alarms = new TreeSet<String>();
                                }

                                currentOC192Alarms.add(line);

                                final String aid = tl1Response.aid;
                                final String aidType = tl1Response.aidType;
                                if (aid != null) {
                                    if ((aidType != null)
                                            && (aidType.equalsIgnoreCase(OC192_TAG) || aidType
                                                    .equalsIgnoreCase(OCN_TAG))
                                            && oc192InterfacesMap.keySet().contains(aid)) {
                                        logger.log(Level.WARNING,
                                                "\n\n [ monCienaAlm ] OC192 ERROR ?!? Got from CD/CI. Defined state [ "
                                                        + alarmCode + " ] ! " + tl1Response.TL1Line);
                                        if (currentOC192ErrorStatus == null) {
                                            currentOC192ErrorStatus = new HashMap<String, Integer>();
                                        }
                                        currentOC192ErrorStatus.put(aid, alarmCode);
                                    }

                                    Map<String, OC192AlmEntry> prevAlmMap = previousReportedAlarms.get(aid);
                                    if (prevAlmMap == null) {
                                        prevAlmMap = new TreeMap<String, OC192AlmEntry>();
                                        previousReportedAlarms.put(tl1Response.aid, prevAlmMap);
                                    }

                                    OC192AlmEntry almEntry = prevAlmMap.get(line);
                                    if (almEntry == null) {
                                        // first time ... notify by email
                                        almEntry = new OC192AlmEntry(tl1Response.TL1Line, now, alarmCode);
                                        almEntry.lastMailSent = now;
                                        prevAlmMap.put(tl1Response.TL1Line, almEntry);

                                        StringBuilder sbSubj = new StringBuilder(512);
                                        StringBuilder sbMsg = new StringBuilder(8192);
                                        final String linkName = oc192InterfacesMap.get(tl1Response.aid);

                                        sbSubj.append("Ciena Alm: ")
                                                .append((cdciName == null) ? Node.getFarmName() : cdciName)
                                                .append(": ").append(aidType).append(" - ").append(aid);
                                        if (linkName != null) {
                                            sbSubj.append(" - link: ").append(linkName);
                                        }
                                        sbSubj.append(" ").append(tl1Response.dateTL1).append(",")
                                                .append(tl1Response.timeTL1);
                                        sbMsg.append(" TL1 Alarm on port: ").append(tl1Response.aid)
                                                .append(" - link: ")
                                                .append((linkName == null) ? "UNDEFINED" : linkName)
                                                .append(" alarm:\n ").append(tl1Response.TL1Line);
                                        Date date = new Date(now);
                                        DateFormat df = new SimpleDateFormat("yyyy-MM-dd,HH:mm:ss z");
                                        sbMsg.append("\n\n Start Time: ").append(df.format(date));

                                        try {
                                            MailFactory.getMailSender().sendMessage("mlstatus@monalisa.cern.ch",
                                                    alarmRCPTs, sbSubj.toString(), sbMsg.toString());
                                            if (alarmCode.intValue() == 2) {
                                                MailFactory.getMailSender().sendMessage("mlstatus@monalisa.cern.ch",
                                                        smallAlmRCPTs, sbSubj.toString(),
                                                        tl1Response.dateTL1 + "," + tl1Response.timeTL1);
                                            }
                                            if (logger.isLoggable(Level.FINE)) {
                                                logger.log(
                                                        Level.FINE,
                                                        "\n\n\n ************ \n\n  [ monCienaAlm ] OC192 Alarm SET *** PROBLEM ALERT *** : "
                                                                + almEntry
                                                                + "\n ****** MAIL SENT! ******* \n Mail subj: "
                                                                + sbSubj.toString() + "\n Mail data: "
                                                                + sbMsg.toString() + " \n\n ******************* \n");
                                            }
                                        } catch (Throwable t) {
                                            logger.log(Level.WARNING,
                                                    "\n\n [ monCienaAlm ] cannot notify OC192 errors by mail. Cause", t);
                                        }

                                    } else {
                                        logger.log(Level.INFO, " [ monCienaAlm ] OC192 Alarm active: " + almEntry
                                                + " but mail was sent");
                                    }
                                } else {
                                    logger.log(Level.WARNING, "\n\n [ monCienaAlm ] Null tl1Response.aid for line "
                                            + line + " ??????");
                                }

                            } else {
                                if (logger.isLoggable(Level.FINER)) {
                                    logger.log(Level.FINER, " [ monCienaAlm ] not looking for condType: " + condType);
                                }
                            }
                        }

                        // /////////////////////////////////
                        // log new alarms
                        // /////////////////////////////////
                        final long almTime = tl1Response.date;

                        hasAlms = true;

                        if ((almTime >= lastReportedAlarm) && !lastAlarms.contains(line)) {

                            if (maxIterTime < almTime) {
                                maxIterTime = almTime;
                            }

                            eResult er = new eResult();

                            er.FarmName = Node.getFarmName();
                            er.ClusterName = Node.getClusterName();
                            er.NodeName = Node.getName();

                            er.addSet("Alarm", line);
                            er.time = (ignoreAlmTime) ? now : almTime;

                            lastAlarms.add(line);
                            shouldSaveAlarms = true;

                            if (logsToSave == null) {
                                logsToSave = new StringBuilder(8192);
                            }

                            logsToSave.append(new Date());
                            logsToSave.append(" - Alarm date: ").append(new Date(er.time)).append(" - Alarm Entry: ")
                                    .append(line);
                            logsToSave.append("\n");
                            er.Module = moduleName;

                            v.add(er);
                        } else {
                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, " The alarm " + line + " was already reported");
                            }
                        }

                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " [ monCienaAlm ] Got exception parsing line: " + line, t);
                    }
                }// end if
                prevLine = line;

                line = reader.readLine();
            }// for every line

            // /////////////////////////////////
            // OC192 alarms - if any
            // /////////////////////////////////
            for (final Map.Entry<String, String> entry : oc192InterfacesMap.entrySet()) {

                final String intfPortName = entry.getKey();
                final String intfMLParamName = entry.getValue();

                Result r = new Result();
                r.time = now;
                r.FarmName = getFarmName();
                r.ClusterName = clName;
                r.NodeName = intfPortName;
                r.Module = TaskName;

                double status = 1;
                if (currentOC192ErrorStatus != null) {
                    final Integer iStatus = currentOC192ErrorStatus.get(intfPortName);
                    if (iStatus != null) {
                        status = iStatus.doubleValue();
                    }
                }

                boolean bStateFound = false;
                if (status == 1) {
                    //try the rtrv-ocn
                    final StringBuilder sbCheck = cienaTL1Conn.doCmd("rtrv-ocn::" + intfPortName + ":" + TL1_CTAG
                            + ";\n", TL1_CTAG);
                    final BufferedReader readerCheck = new BufferedReader(new StringReader(sbCheck.toString()));
                    boolean bStarted = false;
                    try {
                        for (; !bStateFound;) {
                            String li = readerCheck.readLine();
                            if (li == null) {
                                break;
                            }
                            if (!bStarted) {
                                if (li.startsWith("M ")) {
                                    bStarted = true;
                                }
                                //li = readerCheck.readLine();
                                continue;
                            }
                            final String pLine = li.trim();
                            if (pLine.startsWith("\"") && pLine.endsWith("\"") && (pLine.length() >= 2)) {
                                final String[] pSplit = pLine.split("(\\s)*,(\\s)*");
                                for (final String s : pSplit) {
                                    if (s.indexOf("PST=") >= 0) {
                                        final String ssState = s.substring(4);
                                        if (!ssState.equalsIgnoreCase("IS-NR")) {
                                            logger.log(Level.WARNING, "\n\n Rewriting state for: " + intfPortName
                                                    + " / " + intfMLParamName + " from 1 to 5");
                                            status = 5;
                                        } else {
                                            logger.log(Level.WARNING, "\n\n SAME state for: " + intfPortName + " / "
                                                    + intfMLParamName + " 1");
                                        }
                                        bStateFound = true;
                                        break;
                                    }
                                }
                            }
                        }
                    } finally {
                        readerCheck.close();
                    }
                }

                r.addSet(intfMLParamName + "_OperStatus", status);

                v.add(r);
            }

            // check for cleared OC192 alarms

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "\n\n previousReportedAlarms: \n" + previousReportedAlarms
                        + "\n\n currentOC192Alarms: \n" + currentOC192Alarms);
            }

            if ((currentOC192Alarms == null) || (currentOC192Alarms.size() == 0)) {
                logger.log(Level.INFO,
                        " [ monCienaAlm ] No OC192 alarms in current iteration; clearing previousOC192AlmMap ");
                previousReportedAlarms.clear();
            } else {
                logger.log(Level.INFO, " [ monCienaAlm ] Current OC192 alarms not null; checking for cleared alarms ");

                for (Iterator<Map.Entry<String, Map<String, OC192AlmEntry>>> itPrevAlm = previousReportedAlarms
                        .entrySet().iterator(); itPrevAlm.hasNext();) {
                    final Map.Entry<String, Map<String, OC192AlmEntry>> entry = itPrevAlm.next();

                    final String oc192Intf = entry.getKey();
                    final Map<String, OC192AlmEntry> almMap = entry.getValue();

                    for (Iterator<Map.Entry<String, OC192AlmEntry>> itMap = almMap.entrySet().iterator(); itMap
                            .hasNext();) {
                        final Map.Entry<String, OC192AlmEntry> ientry = itMap.next();

                        final String tl1Alm = ientry.getKey();
                        if (!currentOC192Alarms.contains(tl1Alm)) {
                            // alarm was cleared
                            final OC192AlmEntry clearedAlmEntry = ientry.getValue();
                            itMap.remove();

                            long dtSeconds = (now - clearedAlmEntry.startTime) / 1000L;
                            final long dtHours = (dtSeconds) / 3600;
                            String dtStr = "";
                            if (dtHours > 0) {
                                dtStr += (dtHours + " hours ");
                            }
                            dtSeconds -= (dtHours * 3600);
                            final long dtMin = (dtSeconds) / 60;
                            dtStr += (dtMin + " minutes ");
                            dtSeconds -= dtMin * 60;
                            dtStr += dtSeconds + " seconds ";
                            StringBuilder sbSubj = new StringBuilder(512);
                            StringBuilder sbMsg = new StringBuilder(8192);

                            final String linkName = oc192InterfacesMap.get(oc192Intf);

                            sbSubj.append("Ciena Recovery: ")
                                    .append((cdciName == null) ? Node.getFarmName() : cdciName).append(" ")
                                    .append(oc192Intf);
                            if (linkName != null) {
                                sbSubj.append(" - link: ").append(linkName);
                            }
                            sbMsg.append(" Alarm cleared for: ").append(oc192Intf).append(" :\n ");

                            Date date = new Date(clearedAlmEntry.startTime);
                            DateFormat df = new SimpleDateFormat("yyyy-MM-dd,HH:mm:ss z");
                            sbMsg.append("\n\n Start Time: ").append(df.format(date));
                            date = new Date(now);
                            sbMsg.append("\n End Time: ").append(df.format(date));
                            sbMsg.append("\n Downtime: ").append(dtStr);
                            try {
                                MailFactory.getMailSender().sendMessage("mlstatus@monalisa.cern.ch", alarmRCPTs,
                                        sbSubj.toString(), sbMsg.toString());
                                if (clearedAlmEntry.alarmCode.intValue() == 2) {
                                    MailFactory.getMailSender().sendMessage("mlstatus@monalisa.cern.ch", smallAlmRCPTs,
                                            sbSubj.toString(), "Downtime: " + dtStr);
                                }
                                if (logger.isLoggable(Level.FINE)) {
                                    logger.log(Level.FINE,
                                            "\n\n\n ************ \n\n  [ monCienaAlm ] OC192 Alarm CLEARED *** RECOVERY NOTIFICATION ****: "
                                                    + ientry.getValue() + "\n ****** MAIL SENT! ******* \n Mail subj: "
                                                    + sbSubj.toString() + "\n Mail data: " + sbMsg.toString()
                                                    + " \n\n ******************* \n");
                                }
                            } catch (Throwable t) {
                                logger.log(Level.WARNING,
                                        "\n\n [ monCienaAlm ] cannot notify OC192 errors by mail. Cause", t);
                            }

                        } else {
                            // check for renotif
                            logger.log(Level.WARNING, " [ monCienaAlm ] OC192 alarm: " + ientry.getValue()
                                    + " still active");
                        }
                    }

                    if (almMap.size() == 0) {
                        itPrevAlm.remove();
                    }
                }// for every previous alarm
            }// else - check every alarm

            if (maxIterTime > lastReportedAlarm) {
                shouldSaveState = true;
                lastReportedAlarm = maxIterTime;
                stateProps.put("lastReportedAlarm", "" + lastReportedAlarm);
            }

            if (!hasAlms && (lastAlarms.size() > 0)) {
                logger.log(Level.INFO, "monCienaAlm clearing last alarms");
                lastAlarms.clear();
                shouldSaveAlarms = true;
            }

            // any uncommited states?
            if (shouldSaveState) {

                FileOutputStream fos = null;

                try {
                    final String msg = "Last reported alarm Time: [ " + new Date(lastReportedAlarm).toString() + " / "
                            + lastReportedAlarm + "]. Saved at: " + new Date().toString();
                    fos = new FileOutputStream(lastAlarmsStateFile);
                    stateProps.store(fos, msg);
                    logger.log(Level.INFO, "monCienaAlm - Saved state file with the following msg: " + msg);
                    shouldSaveState = false;
                } catch (Throwable tie) {
                    logger.log(Level.WARNING, " Cannot save lastAlarmsStateFile: " + lastAlarmsStateFile, tie);
                } finally {
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (Throwable ignore) {
                        }
                    }
                }
            }

            if (shouldSaveAlarms) {
                if (lastAlarmsSerFile != null) {
                    FileOutputStream fos = null;
                    ObjectOutputStream oos = null;
                    try {
                        fos = new FileOutputStream(lastAlarmsSerFile);
                        oos = new ObjectOutputStream(fos);
                        oos.writeObject(lastAlarms);
                    } catch (Throwable t) {
                        logger.log(Level.WARNING,
                                " monCienaAlm - Exception serialize lastAlarms to lastAlarmsSerFile [ "
                                        + lastAlarmsSerFile + " ]", t);
                    } finally {

                        // close the streams
                        if (oos != null) {
                            try {
                                oos.close();
                            } catch (Throwable ignore) {
                            }
                        }

                        if (fos != null) {
                            try {
                                fos.close();
                            } catch (Throwable ignore) {
                            }
                        }
                    }
                }
            }

            if (canLogAlarms && (logsToSave != null)) {
                try {
                    saveLogs(logsToSave);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "\n\nmonCienaAlm - Cannot save last logs:\n" + logsToSave.toString()
                            + "\n >>>> Exception:", t);
                }
            }

        } catch (Throwable t) {

            StringBuilder sbLog = new StringBuilder(8192);
            sbLog.append(" monCienaAlm got exception for line: [").append(line).append("]");

            if (sb != null) {
                sbLog.append("\n Received from Ciena system:\n").append(sb.toString()).append("\n\n Exception: \n");
            }

            logger.log(Level.WARNING, sbLog.toString(), t);

        } finally {
            if (logger.isLoggable(Level.FINE)) {
                StringBuilder sbLog = new StringBuilder(8192);
                sbLog.append(" [ monCienaAlm ] dt= [ ").append(System.currentTimeMillis() - sTime).append(" ] ms \n");
                if (logger.isLoggable(Level.FINEST)) {
                    sbLog.append(" returning ").append(v).append("\n");
                }
                logger.log(Level.FINE, sbLog.toString());
            }
        }

        if (shouldMonitorPerformance.get()) {
            // final stats
            try {
                Result r = new Result();
                r.time = now;
                r.FarmName = getFarmName();
                r.ClusterName = clName + "_PerfStat";
                r.NodeName = "TL1_Alm_Stat";
                r.Module = TaskName;
                r.addSet("TL1Delay", TimeUnit.NANOSECONDS.toMillis(perfFinishCMDTime - perfStartCMDTime));
                r.addSet("TotalMLTime", TimeUnit.NANOSECONDS.toMillis(Utils.nanoNow() - perfStartTime));
                v.add(r);
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ monCienaAlm ] Unable to publish performance measurements", t);
            }
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " monCienaAlm returning\n" + v.toString() + "\n");
        }

        return v;
    }

    private String getAlarmsLoggerName() {

        Calendar cDate = Calendar.getInstance();
        int year = cDate.get(Calendar.YEAR);
        int month = cDate.get(Calendar.MONTH);
        int day = cDate.get(Calendar.DAY_OF_MONTH);

        return alarmsLogDir + "/" + year + "_" + ((month <= 9) ? "0" + month : "" + month) + "_"
                + ((day <= 9) ? "0" + day : "" + day) + ".log";
    }

    private void saveLogs(final StringBuilder sb) throws Exception {

        FileWriter fw = null;
        BufferedWriter bw = null;

        try {

            fw = new FileWriter(getAlarmsLoggerName(), true);
            bw = new BufferedWriter(fw);
            bw.write(sb.toString());
            bw.flush();
            logger.log(Level.INFO, " monCienaAlm - saveLogs OK. Logs:\n" + sb.toString());
        } finally {
            // close the streams whatever happens
            if (bw != null) {
                try {
                    bw.close();
                } catch (Throwable ignore) {
                }
            }

            if (fw != null) {
                try {
                    fw.close();
                } catch (Throwable ignore) {
                }
            }
        }// end - finally
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    @Override
    public String[] ResTypes() {
        return ResTypes;
    }

    @Override
    public String getOsName() {
        return "linux";
    }

    @Override
    public MNode getNode() {
        return Node;
    }

    @Override
    public String getClusterName() {
        return Node.getClusterName();
    }

    @Override
    public String getFarmName() {
        return Node.getFarmName();
    }

    @Override
    public String getTaskName() {
        return moduleName;
    }

    @Override
    public boolean isRepetitive() {
        return isRepetitive;
    }

    @Override
    public boolean stop() {
        logger.log(Level.INFO, " monCienaAlm stop() Request . SHOULD NOT!!!");
        return true;
    }

    /**
     * @throws java.lang.Exception
     *             - if no interfaces defined or the splitter field is defined wrong
     */
    private final void reloadConf() throws Exception {
        InputStream fis = null;
        BufferedInputStream bis = null;

        Map<String, Integer> newAlmCodesMap = new HashMap<String, Integer>();

        // read the conf file & parse new config
        try {

            fis = new FileInputStream(oc192ConfFile);
            bis = new BufferedInputStream(fis);
            final Properties p = new Properties();
            p.load(bis);

            final String splitter = p.getProperty("splitter", "(\\s)*;(\\s)*");

            if (splitter == null) {
                throw new Exception(" [ OC192ConfFile ] \"splitter\" property cannot be null!");
            }

            final String intLine = p.getProperty("SonetInterfaces");
            if ((intLine == null) || (intLine.trim().length() < 1)) {
                throw new Exception(" [ OC192ConfFile ]  No SonetInterfaces defined");
            }

            final String clusterName = p.getProperty("MLClusterName_SonetStatus", "SonetIntf_Status");
            if ((clusterName == null) || (clusterName.length() < 1)) {
                throw new Exception(" [ OC192ConfFile ]  No MLClusterName_SonetStatus defined");
            }

            clusterNameOC192Alm.set(clusterName);

            final String intf[] = intLine.trim().split(splitter);

            if ((intf == null) || (intf.length < 1)) {
                throw new Exception(" [ OC192ConfFile ]  No interfaces defined");
            }

            for (final Map.Entry<Object, Object> entry : p.entrySet()) {

                final String key = (String) entry.getKey();

                if (key.equals("SonetInterfaces") || key.equals("splitter") || key.equals("MLClusterName_SonetStatus")) {
                    continue;
                }

                final String value = (String) entry.getValue();

                final Integer ki = Integer.decode(key);// can throw exception
                final String[] tks = value.split(splitter);

                final int len = tks.length;

                for (int i = 0; i < len; i++) {
                    newAlmCodesMap.put(tks[i], ki);
                }

            }

            internalNotifAlarms.putAll(newAlmCodesMap);
            internalNotifAlarms.keySet().retainAll(newAlmCodesMap.keySet());

            Arrays.sort(intf);
            Set<String> newPorts = new TreeSet<String>();
            for (final String oc192Entry : intf) {
                if (oc192Entry.indexOf("=") > 1) {
                    String[] tks = oc192Entry.split("(\\s)*=(\\s)*");
                    oc192InterfacesMap.put(tks[0], tks[1]);
                    newPorts.add(tks[0]);
                } else {
                    oc192InterfacesMap.put(oc192Entry, oc192Entry);
                    newPorts.add(oc192Entry);
                }
            }

            oc192InterfacesMap.keySet().retainAll(newPorts);

            StringBuilder sb = new StringBuilder();
            sb.append("\n\n [ monCienaAlm ] (re)Loaded config from oc192ConfFile: ").append(oc192ConfFile)
                    .append(".\n ");
            sb.append("\n\nOC192Interfaces: ").append(oc192InterfacesMap.toString());
            sb.append("\n\nOC192AlarmMap: ").append(internalNotifAlarms.toString()).append("\n\n");
            if (logger.isLoggable(Level.FINER)) {
                sb.append("\n MLClusterName_SonetStatus: ").append(clusterName).append("\n used splitter: ")
                        .append(splitter);
            }
            logger.log(Level.INFO, sb.toString());

        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ monCienaAlm ] Exception (re)loading config from oc192ConfFile:"
                    + oc192ConfFile + ". Cause: ", t);
        } finally {
            // do not leak FDs
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (Throwable ignore) {
            }
            try {
                if (bis != null) {
                    bis.close();
                }
            } catch (Throwable ignore) {
            }
        }
    }

    /**
     * Called by DateFileWatchdog
     * 
     * @param o
     * @param arg
     */
    @Override
    public void update(Observable o, Object arg) {
        try {
            reloadConf();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ monCienaAlm ] got exception reloading config", t);
        }
    }

    static public void main(String[] args) {

        monCienaAlm aa = new monCienaAlm();
        String ad = null;
        String host = null;
        try {
            host = (InetAddress.getLocalHost()).getHostName();
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }
        System.out.println("Using hostname= " + host + " IPaddress=" + ad);
        aa.init(new MNode(host, ad, new MCluster("CMap", null), null), "OC192ConfFile=/home/ramiro/OC192ConfFile");

        try {
            for (int k = 0; k < 10000; k++) {
                Collection<Object> bb = (Collection<Object>) aa.doProcess();
                for (final Object o : bb) {
                    System.out.println(o);
                }
                System.out.println("-------- sleeeping ----------");
                Thread.sleep(15000);
                System.out.println("-------- doProcess-ing --------- k=" + k);
            }
        } catch (Exception e) {
            System.out.println(" failed to process !!!");
        }
    }
}
