/*
 * Created on Oct 4, 2007 $Id: monCienaEthIO.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.modules;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import lia.util.DateFileWatchdog;
import lia.util.StringFactory;
import lia.util.Utils;
import lia.util.ntp.NTPDate;
import lia.util.telnet.CienaTelnet;

/**
 * Monitoring module for trafic on Ciena CD/CI Ethernet interfaces
 * 
 * @author ramiro
 */
public class monCienaEthIO extends cmdExec implements MonitoringModule, Observer {

    private static final long serialVersionUID = 1526888675110369851L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monCienaEthIO.class.getName());

    private static final String TL1_CTAG = "mettp";

    private static final String TL1_PREFIX_CMD = "rtrv-mib-gige::"; // Key - TL1 Command; Value - TL1CmdStat

    private final ConcurrentMap<String, TL1CmdStat> ettpTL1CmdParams = new ConcurrentHashMap<String, TL1CmdStat>();

    private final Set<String> ettpParamsList = new ConcurrentSkipListSet<String>(); // Key - portName; value another
                                                                                    // HMap ( Key - paramName; Value:
                                                                                    // last counter Long )

    private final Set<String> ettpParamsListIgnoreRate = new ConcurrentSkipListSet<String>(); // Key - portName; value
                                                                                              // another HMap ( Key -
                                                                                              // paramName; Value: last
                                                                                              // counter Long )

    private final Map<String, Map<String, Long>> previousValues = new HashMap<String, Map<String, Long>>();

    File ettpConfFile = null;

    CienaTelnet cienaTL1Conn;

    private long lastRun;

    private final SimpleDateFormat dateParserTL1 = new SimpleDateFormat("yy-MM-dd HH:mm:ss");

    private static final AtomicBoolean shouldMonitorPerformance = new AtomicBoolean(AppConfig.getb(
            "lia.Monitor.modules.monCienaEthIO.shouldMonitorPerformance", false));

    private static volatile String portNameSuffix = "";

    static {
        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                shouldMonitorPerformance.set(AppConfig.getb(
                        "lia.Monitor.modules.monCienaEthIO.shouldMonitorPerformance", false));
            }
        });
    }

    /**
     * Keeps state and statistics for every TL1 cmd;
     */
    private static final class TL1CmdStat {

        private final String tl1Cmd;

        private long lastTimestampNTP;

        private long lastTimestampNE;

        private long lastExecDuration;

        private String lastNEDate;

        TL1CmdStat(final String tl1Cmd) {
            this.tl1Cmd = tl1Cmd;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("TL1CmdStat [tl1Cmd=").append(tl1Cmd).append(", lastTimestampNTP=").append(lastTimestampNTP)
                    .append(", lastTimestampNE=").append(lastTimestampNE).append(", lastExecDuration=")
                    .append(lastExecDuration).append(", lastNEDate=").append(lastNEDate).append("]");
            return builder.toString();
        }

    }

    public monCienaEthIO() {
        TaskName = "monCienaEthIO";
        isRepetitive = true;
    }

    @Override
    public MonModuleInfo init(MNode Node, String args) {
        info = new MonModuleInfo();
        info.name = TaskName;
        this.Node = Node;

        if (args == null) {
            logger.log(Level.SEVERE, "[ monCienaEthIO ] Null params in init. The module is unable to monitor the CD/CI");
            throw new IllegalArgumentException(
                    "[ monCienaEthIO ] Null params in init. The module is unable to monitor the CD/CI");
        }

        if (args.length() == 0) {
            logger.log(Level.SEVERE,
                    "[ monCienaEthIO ] The args list is empty. The module is unable to monitor the CD/CI");
            throw new IllegalArgumentException(
                    "[ monCienaEthIO ] The args list is empty. The module is unable to monitor the CD/CI");
        }

        if (args.startsWith("\"")) {
            args = args.substring(1);
        }

        if (args.endsWith("\"")) {
            args = args.substring(0, args.length() - 1);
        }

        final String[] argsTokens = args.split("(\\s)*;(\\s)*");
        if ((argsTokens != null) && (argsTokens.length > 0)) {
            for (String argsToken : argsTokens) {
                try {
                    final String argT = argsToken.trim();
                    if (argT.startsWith("ettpConfFile")) {
                        String[] fileTks = argT.split("(\\s)*=(\\s)*");
                        if ((fileTks == null) || (fileTks.length != 2)) {
                            logger.log(Level.SEVERE, "[ monCienaEthIO ] cannot parse ettpConfFile param [ " + argT
                                    + " ]  ... ");
                            throw new IllegalArgumentException(
                                    "[ monCienaEthIO ] cannot parse ettpListConfFile param [ " + argT + " ]  ... ");
                        }

                        ettpConfFile = new File(fileTks[1]);

                        if (!ettpConfFile.exists()) {
                            logger.log(Level.SEVERE, "[ monCienaEthIO ] The ettpConfFile [ " + ettpConfFile
                                    + " ] does not exist");
                            throw new IllegalArgumentException("[ monCienaEthIO ] The ettpConfFile [ " + ettpConfFile
                                    + " ] does not exist");
                        }

                        if (!ettpConfFile.canRead()) {
                            logger.log(Level.SEVERE, "[ monCienaEthIO ] The ettpConfFile [ " + ettpConfFile
                                    + " ] does not have read access");
                            throw new IllegalArgumentException("[ monCienaEthIO ] The ettpConfFile [ " + ettpConfFile
                                    + " ] does not have read access");
                        }

                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "monCienaAlm - exception parsing module params params", t);
                }
            }
        } else {
            logger.log(Level.SEVERE,
                    "[ monCienaEthIO ] Unable to determine the arguments tokens. The module is unable to monitor the CD/CI");
            throw new IllegalArgumentException(
                    "[ monCienaEthIO ] Unable to determine the arguments tokens. The module is unable to monitor the CD/CI");
        }

        if (ettpConfFile == null) {
            logger.log(Level.SEVERE,
                    "[ monCienaEthIO ] Unable to determine ettpConfFile. The module is unable to monitor the CD/CI");
            throw new IllegalArgumentException(
                    "[ monCienaEthIO ] Unable to determine ettpListConfFile. The module is unable to monitor the CD/CI");
        }

        // Just parse the conf file
        try {
            reloadConf();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "[ monCienaEthIO ] Got exception parsing the conf files", t);
            throw new IllegalArgumentException("[ monCienaEthIO ] Got exception parsing the conf files: "
                    + t.getMessage());
        }

        try {
            final DateFileWatchdog portsDfw = DateFileWatchdog.getInstance(ettpConfFile, 5 * 1000);
            portsDfw.addObserver(this);

        } catch (Throwable t) {
            logger.log(Level.SEVERE, "[ monCienaEthIO ] Unable to monitor the config files for changes", t);
        }

        return info;
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    @Override
    public String getOsName() {
        return "linux";
    }

    private final void buildMap(final String line, final Set<String> newParamMap) throws Exception {

        final String[] splitPorts = line.trim().split("(\\s)*,(\\s)*");
        for (int i = 0; i < splitPorts.length; i++) {
            splitPorts[i] = splitPorts[i].trim();
            if (splitPorts[i].length() > 0) {
                newParamMap.add(splitPorts[i]);
            }
        }
    }

    private final void reloadConf() throws Exception {

        // parse the conf files
        final Set<String> newPortList = new TreeSet<String>();
        final Set<String> newParamsList = new TreeSet<String>();
        final Set<String> newIgnoreParamsRate = new TreeSet<String>();

        final Properties p = new Properties();
        FileInputStream fis = null;
        BufferedInputStream bis = null;

        try {
            fis = new FileInputStream(ettpConfFile);
            bis = new BufferedInputStream(fis);

            p.load(bis);

            final String ettpParams = p.getProperty("ettpParams");
            buildMap(ettpParams, newParamsList);
            final String ettpPorts = p.getProperty("ettpPorts");
            buildMap(ettpPorts, newPortList);
            final String ettpParamsIgnoreRate = p.getProperty("ettpParamsIgnoreRate");
            buildMap(ettpParamsIgnoreRate, newIgnoreParamsRate);
            final String tPortNameSuffix = p.getProperty("ettpPortNameSuffix", "");
            if (tPortNameSuffix == null) {
                monCienaEthIO.portNameSuffix = "";
            } else {
                final String tPSFX = tPortNameSuffix.trim();
                if (tPSFX.isEmpty()) {
                    monCienaEthIO.portNameSuffix = "";
                } else {
                    monCienaEthIO.portNameSuffix = tPSFX;
                }
            }
        } finally {
            try {
                if (bis != null) {
                    bis.close();
                }

            } catch (Throwable ign) {
            }
            try {
                if (fis != null) {
                    fis.close();
                }

            } catch (Throwable ign) {
            }
        }

        // check removed ports
        for (final Iterator<String> it = ettpTL1CmdParams.keySet().iterator(); it.hasNext();) {
            final String cmd = it.next();
            int idx = cmd.lastIndexOf(":");
            final String cmdPort = cmd.substring(TL1_PREFIX_CMD.length(), idx);
            if (!newPortList.contains(cmdPort)) {
                it.remove();
            }
        }

        // add the new ports
        for (String string : newPortList) {
            final String portName = string.trim();
            if (portName.length() > 0) {
                final String tl1Cmd = TL1_PREFIX_CMD + portName + ":" + TL1_CTAG + ";\n";
                ettpTL1CmdParams.put(tl1Cmd, new TL1CmdStat(tl1Cmd));
            }

        }

        // check removed params for ignore rate
        for (final Iterator<String> it = ettpParamsListIgnoreRate.iterator(); it.hasNext();) {
            final String paramIgnoreRate = it.next();
            if (!newIgnoreParamsRate.contains(paramIgnoreRate)) {
                it.remove();
            }

        }

        ettpParamsListIgnoreRate.addAll(newIgnoreParamsRate);

        // check removed ignore params
        for (final Iterator<String> it = ettpParamsList.iterator(); it.hasNext();) {
            final String param = it.next();
            if (!newParamsList.contains(param)) {
                it.remove();
            }

        }

        ettpParamsList.addAll(newParamsList);

        if (logger.isLoggable(Level.FINER)) {
            final StringBuilder sb = new StringBuilder(256);
            sb.append("[ monCienaEthIO ] [ reloadConf ]\n (NEW) TL1 cmds: ").append(ettpTL1CmdParams).toString();
            sb.append("\n (NEW) params: ").append(ettpParamsList).toString();
            logger.log(Level.FINER, sb.toString());
        }

        logger.log(Level.INFO, " [ monCienaEthIO ] [ reloadConf ] portNameSuffix: " + monCienaEthIO.portNameSuffix);
    }

    private long parseNETime(final String TL1TimeLine) throws ParseException {
        return dateParserTL1.parse(TL1TimeLine.substring(TL1TimeLine.indexOf(" ") + 1)).getTime();
    }

    @Override
    public Object doProcess() throws Exception {

        final String portNameSuffix = monCienaEthIO.portNameSuffix;

        final long sPerfTime = Utils.nanoNow();

        // dt for all TL1 in ms
        long allTlPerf = 0;

        final long sTime = System.currentTimeMillis();
        ArrayList<Result> al = new ArrayList<Result>();

        long now = -1;
        long cNETime = -1;

        int cmdNo = 0;

        try {
            if (cienaTL1Conn == null) {
                cienaTL1Conn = CienaTelnet.getMonitorInstance();
            }

            now = NTPDate.currentTimeMillis();
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " NOW: " + now + " / " + new Date(now) + " lastRun: " + lastRun + " / "
                        + new Date(lastRun));
            }

            long dt = now - lastRun;

            lastRun = now;

            for (final Map.Entry<String, TL1CmdStat> entry : ettpTL1CmdParams.entrySet()) {
                final String cmd = entry.getKey();
                final TL1CmdStat cmdStat = entry.getValue();
                try {

                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "[ monCienaEthIO ] [ doProcess ] executing cmd: " + cmd);
                    }

                    final long sCmdTime = Utils.nanoNow();
                    final StringBuilder sb = cienaTL1Conn.doCmd(cmd, TL1_CTAG);
                    final long lastTimeStampNTP = NTPDate.currentTimeMillis();
                    dt = lastTimeStampNTP - cmdStat.lastTimestampNTP;

                    final long cmdDelay = TimeUnit.NANOSECONDS.toMillis(Utils.nanoNow() - sCmdTime);
                    allTlPerf += cmdDelay;
                    cmdNo++;

                    // ;rtrv-mib-gige::1-A-3-1-1:meio;
                    // IP meio
                    // <
                    //
                    // GNVA 07-10-04 14:27:32
                    // M meio COMPLD
                    // "1-A-3-1-1:RMON_PACKETS_TX,84028299"
                    // "1-A-3-1-1:RMON_OCTETS_RX,335688487788"
                    // "1-A-3-1-1:RMON_NON_UC_PACKETS_RX,13882"
                    // "1-A-3-1-1:RMON_FRAGMENTS,0"
                    // "1-A-3-1-1:RMON_JABBERS,0"
                    // "1-A-3-1-1:RMON_128_TO_255_TX,0"
                    // "1-A-3-1-1:RMON_1519_TO_MAX_TX,81349998"
                    // "1-A-3-1-1:RMON_NON_UC_PACKETS_TX,3835"
                    // "1-A-3-1-1:RMON_UNDERSIZE,0"
                    // "1-A-3-1-1:RMON_64_TX,0"
                    // "1-A-3-1-1:RMON_128_TO_255_RX,5"
                    // "1-A-3-1-1:RMON_256_TO_511_RX,5"
                    // "1-A-3-1-1:RMON_512_TO_1023_RX,4"
                    // "1-A-3-1-1:RMON_512_TO_1023_TX,2"
                    // "1-A-3-1-1:RMON_1024_TO_1518_TX,0"
                    // "1-A-3-1-1:RMON_PACKETS_RX,131406811"
                    // "1-A-3-1-1:RMON_OCTETS_TX,645524498490"
                    // "1-A-3-1-1:RMON_64_RX,0"
                    // "1-A-3-1-1:RMON_65_TO_127_RX,22993065"
                    // "1-A-3-1-1:RMON_65_TO_127_TX,2678298"
                    // "1-A-3-1-1:RMON_256_TO_511_TX,1"
                    // "1-A-3-1-1:RMON_1024_TO_1518_RX,0"
                    // "1-A-3-1-1:RMON_1519_TO_MAX_RX,108413732"
                    // "1-A-3-1-1:RMON_IN_ERRORS,0"
                    // "1-A-3-1-1:COS_QUEUE_0_TOTAL_OCTETS,0"
                    // "1-A-3-1-1:COS_QUEUE_0_IN_PACKET_RATE,0"
                    // "1-A-3-1-1:COS_QUEUE_0_IN_FRAMES,0"
                    // "1-A-3-1-1:COS_QUEUE_0_OUT_FRAMES,0"
                    // "1-A-3-1-1:COS_QUEUE_0_IN_OCTET_RATE,0"
                    // "1-A-3-1-1:COS_QUEUE_1_TOTAL_OCTETS,0"
                    // "1-A-3-1-1:COS_QUEUE_1_IN_PACKET_RATE,0"
                    // "1-A-3-1-1:COS_QUEUE_1_IN_FRAMES,0"
                    // "1-A-3-1-1:COS_QUEUE_1_OUT_FRAMES,0"
                    // "1-A-3-1-1:COS_QUEUE_1_IN_OCTET_RATE,0"
                    // "1-A-3-1-1:COS_QUEUE_2_TOTAL_OCTETS,657653144125"
                    // "1-A-3-1-1:COS_QUEUE_2_IN_PACKET_RATE,8402800"
                    // "1-A-3-1-1:COS_QUEUE_2_IN_FRAMES,84028300"
                    // "1-A-3-1-1:COS_QUEUE_2_OUT_FRAMES,84028299"
                    //
                    // <SNIP/>
                    //
                    // "1-A-3-1-1:COS_QUEUE_2_IN_OCTET_RATE,65765314383"
                    // "1-A-3-1-1:IEEE_IN_PAUSE_FRAMES,0"
                    // "1-A-3-1-1:IEEE_FRAME_TOO_LONGS,0"
                    // "1-A-3-1-1:IEEE_OUT_PAUSE_FRAMES,0"
                    // "1-A-3-1-1:IEEE_FCS_ERRORS,0"
                    // "1-A-3-1-1:IEEE_SYMBOL_ERRORS,0"
                    // ;

                    BufferedReader reader = new BufferedReader(new StringReader(sb.toString()));
                    String line = null;
                    String lastNEDate = null;
                    boolean started = false;

                    Result r = null;
                    String lastLine = null;

                    boolean bUseCDTime = false;
                    long devDT = 0;
                    long absDevDT = 0;

                    try {
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            try {
                                if (!started) {
                                    if (line.startsWith("M ")) {
                                        if (lastLine == null) {
                                            logger.log(Level.WARNING,
                                                    " [ monCienaEthIO ] Cannot determine NE Date ?!? Line is null before line: "
                                                            + line);
                                        } else {
                                            lastLine = lastLine.trim();
                                            cNETime = parseNETime(lastLine);
                                            if (logger.isLoggable(Level.FINER)) {
                                                logger.log(Level.FINER, " [ monCienaEthIO ] Time for CMD: " + cmd
                                                        + "; Date line: " + lastLine + "; millis: " + cNETime
                                                        + "; Parsed Date: " + new Date(cNETime));
                                            }

                                            final long dtCD = cNETime - cmdStat.lastTimestampNE;
                                            devDT = dtCD - dt;
                                            absDevDT = Math.abs(devDT);

                                            if ((absDevDT < 0) || (absDevDT > (4 * 1000))) {
                                                logger.log(Level.WARNING,
                                                        " [ monCienaEthIO ] The time goes back in time or NTP diff to large. dtNE"
                                                                + dtCD + " dtNTP: " + dt + " : currentNETime "
                                                                + cNETime + "; Date: " + new Date(cNETime)
                                                                + "; Line from NE: " + lastLine + " lastNETime: "
                                                                + cmdStat.lastTimestampNE + "; Date: "
                                                                + new Date(cmdStat.lastTimestampNE)
                                                                + "; Last line from NE: " + cmdStat.lastNEDate);
                                            } else {
                                                bUseCDTime = true;
                                                dt = dtCD;
                                            }

                                            lastNEDate = lastLine;
                                        }

                                        started = true;
                                    }

                                    continue;
                                }

                                if (logger.isLoggable(Level.FINEST)) {
                                    logger.log(Level.FINEST, " [ monCienaEthIO ] Processing line: [" + line + "]");
                                }

                                if (line.startsWith("\"") && line.endsWith("\"") && (line.length() >= 2)) {
                                    try {

                                        int idx = line.indexOf(":");
                                        int idx1 = line.indexOf(",");

                                        if ((idx <= 0) || (idx1 > (line.length() - 2))) {
                                            continue;
                                        }

                                        final String param = StringFactory.get(line.substring(idx + 1, idx1));
                                        if (!ettpParamsList.contains(param)) {
                                            continue;
                                        }

                                        final String valS = line.substring(idx1 + 1, line.length() - 1);
                                        final long cVal = Long.parseLong(valS);

                                        if (r == null) {
                                            final String portName = StringFactory.get(line.substring(1, idx));
                                            r = new Result();
                                            r.FarmName = Node.getFarmName();
                                            r.ClusterName = Node.getClusterName();
                                            r.NodeName = portName + portNameSuffix;
                                            r.time = now;
                                            r.Module = TaskName;
                                        }

                                        Map<String, Long> hmParams = previousValues.get(r.NodeName);

                                        if (hmParams == null) {
                                            hmParams = new HashMap<String, Long>();
                                            previousValues.put(r.NodeName, hmParams);
                                        }

                                        Long lastVal = hmParams.get(param);
                                        if (lastVal == null) {
                                            hmParams.put(param, Long.valueOf(cVal));
                                            continue;

                                        }

                                        final boolean ignoreRate = ettpParamsListIgnoreRate.contains(param);
                                        if ((lastVal.longValue() > cVal) && !ignoreRate) {
                                            logger.log(Level.WARNING, "[ monCienaEthIO ] The counter [ " + cVal
                                                    + " ] for line: " + line + " is smaller than lastVal: " + lastVal
                                                    + " .... ignoring it");
                                            hmParams.put(param, Long.valueOf(cVal));
                                            continue;

                                        }

                                        if (logger.isLoggable(Level.FINE)) {
                                            logger.log(Level.FINE, " Node: " + r.NodeName + " Param: " + param
                                                    + " currentVal: " + cVal + " lastVal: " + lastVal + " DT: " + dt);
                                        }

                                        if (!ignoreRate) {
                                            r.addSet(param, cVal);
                                            r.addSet(param + "_MLRate", ((cVal - lastVal.longValue()) * 8D) / dt
                                                    / 1000D);
                                        } else {
                                            r.addSet(param, (cVal * 8D) / (1000000D));
                                        }
                                        hmParams.put(param, Long.valueOf(cVal));

                                    } catch (Throwable t1) {
                                        logger.log(Level.WARNING,
                                                "[ monCienaEthIO ] [ HANDLED ] Got exception processing line: " + line,
                                                t1);
                                    }

                                }// end if

                            } finally {
                                lastLine = line;
                            }

                        }// TL1 buffer parsing

                    } finally {
                        // save current cmd state
                        cmdStat.lastExecDuration = cmdDelay;
                        cmdStat.lastTimestampNE = cNETime;
                        cmdStat.lastNEDate = lastNEDate;
                        cmdStat.lastTimestampNTP = lastTimeStampNTP;
                    }

                    if ((r != null) && (r.param != null) && (r.param.length > 0)) {
                        r.addSet("CDTimeStatus", (bUseCDTime) ? 0 : 1);
                        r.addSet("CDTimeNTPTime", devDT);
                        r.addSet("AbsCDTimeNTPTime", absDevDT);
                        al.add(r);
                    }

                } catch (Throwable t1) {
                    logger.log(Level.WARNING, "[ monCienaEthIO ] got exception in processing cmd: " + cmd, t1);
                }
            }// end for cmds

        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ monCienaEthIO ] got exception in doProcess()", t);
            throw new Exception(t);
        } finally {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "[ monCienaEthIO ] doProcess took: " + (System.currentTimeMillis() - sTime));
            }

        }

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "[ monCienaEthIO ] returning: " + al);
        }

        Collection<Result> retV = null;

        if (shouldMonitorPerformance.get()) {
            retV = new ArrayList<Result>(al.size() + 1);
            retV.addAll(al);
            // final stats
            try {
                Result r = new Result();
                r.time = now;
                r.FarmName = getFarmName();
                r.ClusterName = Node.getClusterName() + "_PerfStat";
                r.NodeName = "TL1_EthIO_Stat";
                r.Module = TaskName;
                r.addSet("TL1Delay", allTlPerf);
                r.addSet("TotalMLTime", TimeUnit.NANOSECONDS.toMillis(Utils.nanoNow() - sPerfTime));
                r.addSet("TotalTL1CmdS", cmdNo);
                retV.add(r);
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ monCienaAlm ] Unable to publish performance measurements", t);
            }
        } else {
            retV = new ArrayList<Result>(al);
        }

        return retV;
    }

    @Override
    public void update(Observable o, Object arg) {
        try {
            reloadConf();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ monCienaEthIO ] got exception reloading config", t);
        }

    }

    @SuppressWarnings("unchecked")
    public static final void main(String[] args) throws Exception {
        // SimpleDateFormat sdf = new SimpleDateFormat("yy-MM-dd HH:mm:ss");
        // System.out.println(sdf.parse("08-07-02 02:27:27"));
        // System.out.println(sdf.parse("08-07-02 02:27:16"));

        monCienaEthIO aa = new monCienaEthIO();
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
        aa.init(new MNode(host, ad, new MCluster("CMap", null), null),
                "ettpListConfFile=/home/ramiro/ettpPortList ; ettpParamsConfFile = /home/ramiro/ettpParamList");

        try {
            for (int k = 0; k < 10000; k++) {
                System.out.println(" Long.MAX_VAL = " + Long.MAX_VALUE);
                Collection<Result> bb = (Collection<Result>) aa.doProcess();
                for (final Result r : bb) {
                    System.out.println(r);
                }

                System.out.println("-------- sleeeping ----------");
                Thread.sleep(5000);
                System.out.println("-------- doProcess-ing --------- k=" + k);
            }

        } catch (Exception e) {
            System.out.println(" failed to process !!!");
        }
    }
}
