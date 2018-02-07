package lia.Monitor.modules;

import java.net.InetAddress;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.cmdExec;
import lia.Monitor.monitor.eResult;
import lia.util.ntp.NTPDate;
import lia.util.telnet.OSTelnet;
import lia.util.telnet.OSTelnetFactory;

public class monOSGMPLS extends cmdExec implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = 6148803205866794156L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monOSGMPLS.class.getName());

    public MNode Node;

    static final String[] ResTypes = { "OSPF-RouterID", "OSPF-AreaID", "RSVP-MsgRetryInvl", "RSVP-NtfRetryInvl",
            "RSVP-GrInvl", "RSVP-GrcvInvl", "CTRLCH-LocalIP", "CTRLCH-RemoteIP", "CTRLCH-LocalRID", "CTRLCH-RemoteRID",
            "CTRLCH-Port", "CTRLCH-Adjacency", "CTRLCH-HelloInterval", "CTRLCH-HelloIntervalMin",
            "CTRLCH-HelloIntervalMax", "CTRLCH-DeadInterval", "CTRLCH-DeadIntervalMin", "CTRLCH-DeadIntervalMax",
            "ADJ-LocalRID", "ADJ-RemoteRID", "ADJ-CtrlChName", "ADJ-CurrentCtrlChName", "ADJ-AdjIndex", "ADJ-OSPFArea",
            "ADJ-Metric", "ADJ-OSPFAdj", "ADJ-AdjType", "ADJ-RSVPRRFlag", "ADJ-RSVPGRFlag", "ADJ-NTFProc",
            "LINK-LinkType", "LINK-LocalRID", "LINK-RemoteRID", "LINK-LocalIP", "LINK-RemoteIP", "LINK-LocalIFIndex",
            "LINK-RemoteIF", "LINK-WDMRemoteTEIF", "LINK-FLTDetect", "LINK-Metric", "LINK-LMPVerify", "LINK-AdjName",
            "LINK-PortName", "FreePorts" };

    public MonModuleInfo info;
    short switchType;

    String moduleName;
    final String cluster1 = "OS_GMPLS";
    final String cluster2 = "OS_GMPLSNPPorts";
    final String cluster3 = "OS_GMPLSCtrlCh";
    final String cluster4 = "OS_GMPLSAdj";
    final String cluster5 = "OS_GMPLSLink";
    final String cluster6 = "OS_GMPLSPorts";

    OSTelnet osConn;
    boolean firstTime;

    Vector ospf = new Vector();

    /* we use patterns and compile them only one time */
    Hashtable compiledPatterns;

    /* we also need to identify each command by a specific CTAG - The value of CTAG consists of up to six ASCII */
    private int ctagCounter = 0;

    public monOSGMPLS() {
        moduleName = "monOSGMPLS";
        compiledPatterns = new Hashtable();
        isRepetitive = true;
        firstTime = true;
    }

    @Override
    public MonModuleInfo init(MNode Node, String arg) {
        this.Node = Node;
        logger.log(Level.INFO, "monOSGMPLS: farmName=" + Node.getFarmName() + " clusterName= " + Node.getClusterName()
                + " nodeName=" + Node.getName());

        String[] args = arg.split("(\\s)*;(\\s)*");
        if (args != null) {
            for (String arg2 : args) {
                String argTemp = arg2.trim();
                if (argTemp.startsWith("SwitchType")) {
                    String switchName = argTemp.split("(\\s)*=(\\s)*")[1].trim();
                    logger.log(Level.INFO, "monOSGMPLS: swType = " + switchName);
                    switchType = OSTelnet.getType(switchName);
                }
            }
        }

        info = new MonModuleInfo();
        info.name = moduleName;
        info.ResTypes = ResTypes;
        return info;
    }

    private final void getOSPFCalient(final long time, final Vector vec) {

        final String rPatternStr = "routerid\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String aPatternStr = "areaid\\s*?=\\s*?([\\S&&[^,\"]]+)";

        // RTRV-CFG-OSPF:[TID]:[<src>]:[CTAG];
        // SID DATE TIME
        // M CTAG COMPLD
        // "[ROUTERID=<routerId>],[AREAID=<areaId>]"

        final eResult rez = new eResult();
        rez.FarmName = Node.getFarmName();
        rez.ClusterName = cluster1;
        rez.Module = moduleName;
        rez.NodeName = Node.getName();
        rez.time = time;

        try {
            final String lines[] = osConn.execCmdAndGet("rtrv-cfg-ospf::;", getCTAG());
            if ((lines == null) || (lines.length == 0)) {
                return;
            }
            for (String line : lines) {
                try {
                    Pattern p = getPattern("OSPF-RouterID", rPatternStr);
                    Matcher m = p.matcher(line);
                    if (m.find()) {
                        rez.addSet("OSPF-RouterID", m.group(1));
                    }
                    p = getPattern("OSPF-AreaID", aPatternStr);
                    m = p.matcher(line);
                    if (m.find()) {
                        rez.addSet("OSPF-AreaID", m.group(1));
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exception parsing line [" + line + "]", t);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception reading from command's stream", t);
        }
        if ((rez.param != null) && (rez.param_name != null) && (rez.param.length != 0) && (rez.param_name.length != 0)) {
            vec.add(rez);
        }
    }

    private final void getRSVPCalient(final long time, final Vector vec) {

        final String mPatternStr = "msgretryinvl\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String nPatternStr = "ntfretryinvl\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String gPatternStr = "grinvl\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String gvPatternStr = "grcvinvl\\s*?=\\s*?([\\S&&[^,\"]]+)";

        final eResult rez = new eResult();
        rez.FarmName = Node.getFarmName();
        rez.ClusterName = cluster1;
        rez.Module = moduleName;
        rez.NodeName = Node.getName();
        rez.time = time;

        // RTRV-CFG-RSVP:[TID]:[<src>]:[CTAG];
        // SID DATE TIME
        // M CTAG COMPLD
        // "[MSGRETRYINVL=<msgretryinvl>],[NTFRETRYINVL=<ntfretryinvl>],[GRINVL=<grinvl>],[GRCVINVL=<grcvinvl>]"

        try {
            final String lines[] = osConn.execCmdAndGet("rtrv-cfg-rsvp::;", getCTAG());
            if ((lines == null) || (lines.length == 0)) {
                return;
            }
            for (String line : lines) {
                try {
                    Pattern p = getPattern("RSVP-MsgRetryInvl", mPatternStr);
                    Matcher m = p.matcher(line);
                    if (m.find()) {
                        rez.addSet("RSVP-MsgRetryInvl", m.group(1));
                    }
                    p = getPattern("RSVP-NtfRetryInvl", nPatternStr);
                    m = p.matcher(line);
                    if (m.find()) {
                        rez.addSet("RSVP-NtfRetryInvl", m.group(1));
                    }
                    p = getPattern("RSVP-GrInvl", gPatternStr);
                    m = p.matcher(line);
                    if (m.find()) {
                        rez.addSet("RSVP-GrInvl", m.group(1));
                    }
                    p = getPattern("RSVP-GrcvInvl", gvPatternStr);
                    m = p.matcher(line);
                    if (m.find()) {
                        rez.addSet("RSVP-GrcvInvl", m.group(1));
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exception parsing line [" + line + "]", t);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception reading from command's buffered stream", t);
        }
        if ((rez.param != null) && (rez.param_name != null) && (rez.param.length != 0) && (rez.param_name.length != 0)) {
            vec.add(rez);
        }
    }

    private final void getNPPorts(final long time, final Vector vec) {

        final String shelfPatternStr = "(\\d+)\\s*?:.*?shelfkind\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String cardPatternStr = "(\\d+\\.\\w+)\\s*:.*cardtype\\s*?=\\s*([\\S&&[^,\"]]+)";

        final String portPatternStr = "(\\d+\\.\\w+\\.\\w+)\\s*:";
        final String ipPatternStr = "ip\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String maskPatternStr = "mask\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String gwPatternStr = "gateway\\s*?=\\s*?([\\S&&[^,\"]]+)";

        // RTRV-EQPT:[TID]:[<src>]:[CTAG];
        // SID DATE TIME
        // M CTAG COMPLD
        // <AID>:[CARDTYPE=<CardType>],...,[SHELFKIND=<shelfKind>]

        try {
            final String[] lines = osConn.execCmdAndGet("rtrv-eqpt;", getCTAG());
            if ((lines == null) || (lines.length == 0)) {
                return;
            }
            for (String line : lines) {
                try {
                    Pattern p = getPattern("ShelfKind", shelfPatternStr);
                    Matcher m = p.matcher(line);
                    if (m.find()) {
                        final String shelf = m.group(1).trim();
                        if (shelf.length() == 0) {
                            continue;
                        }
                        final String shelfKind = m.group(2).trim();
                        if (shelfKind.length() == 0) {
                            continue;
                        }
                        if (shelfKind.compareToIgnoreCase("ControlShelf") != 0) {
                            continue;
                        }
                        final String lines2[] = osConn.execCmdAndGet("rtrv-eqpt::" + shelf + ";", getCTAG());
                        if ((lines2 == null) || (lines2.length == 0)) {
                            continue; // advance to the next shelf
                        }
                        for (String element : lines2) {
                            try {
                                p = getPattern("CardType", cardPatternStr);
                                m = p.matcher(element);
                                if (m.find()) {
                                    final String card = m.group(1).trim();
                                    if (card.length() == 0) {
                                        continue;
                                    }
                                    final String cardType = m.group(2).trim();
                                    if ((cardType.compareToIgnoreCase("NP") != 0)
                                            && (cardType.compareToIgnoreCase("CP") != 0)) {
                                        continue;
                                    }
                                    String lines3[] = osConn.execCmdAndGet("rtrv-eqpt::" + card + ";", getCTAG());
                                    if ((lines3 == null) || (lines3.length == 0)) {
                                        continue;
                                    }
                                    final eResult rez = new eResult();
                                    rez.FarmName = Node.getFarmName();
                                    rez.ClusterName = cluster2;
                                    rez.Module = moduleName;
                                    rez.NodeName = "";
                                    rez.time = time;
                                    for (String element2 : lines3) {
                                        try {
                                            p = getPattern("NPPort", portPatternStr);
                                            m = p.matcher(element2);
                                            if (m.find()) {
                                                rez.NodeName = m.group(1);
                                            }
                                            p = getPattern("NP-IP", ipPatternStr);
                                            m = p.matcher(element2);
                                            if (m.find()) {
                                                rez.addSet("NP-IP", m.group(1));
                                            }
                                            p = getPattern("NP-Mask", maskPatternStr);
                                            m = p.matcher(element2);
                                            if (m.find()) {
                                                rez.addSet("NP-Mask", m.group(1));
                                            }
                                            p = getPattern("NP-Gateway", gwPatternStr);
                                            m = p.matcher(element2);
                                            if (m.find()) {
                                                rez.addSet("NP-Gateway", m.group(1));
                                            }
                                        } catch (Throwable t) {
                                            logger.log(Level.WARNING, "Got exception parsing line [" + element2 + "]",
                                                    t);
                                        }
                                    }
                                    if ((rez.param != null) && (rez.param_name != null) && (rez.param.length != 0)
                                            && (rez.param_name.length != 0)) {
                                        vec.add(rez);
                                    }
                                }
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, "Got exception parsing line [" + element + "]", t);
                            }
                        }
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exception parsing line [" + line + "]", t);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception reading from command's buffered stream", t);
        }
    }

    private final void getCtrlChCalient(final long time, final Vector vec) {

        final String ctrlPatternStr = "([\\.-_\\w]+)\\s*?:";
        final String lipPatternStr = "localip\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String ripPatternStr = "remoteip\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String lridPatternStr = "localrid\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String rridPatternStr = "remoterid\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String portPatternStr = "port\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String adjPatternStr = "[adjname|adjacency]\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String hPatternStr = "hellointrvl\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String hMinPatternStr = "hellointrvlmin\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String hMaxPatternStr = "hellointrvlmax\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String dPatternStr = "deadintrvl\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String dMinPatternStr = "deadintrvlmin\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String dMaxPatternStr = "deadintrvlmax\\s*?=\\s*?([\\S&&[^,\"]]+)";

        // RTRV-CTRLCH:[TID]:[<src>]:[CTAG];
        // SID DATE TIME
        // M CTAG COMPLD
        // "<AID>:[LOCALIP=<localip>],[REMOTEIP=<remoteip>],[LOCALRID=<localrid>],[REMOTERID=<remoterid>],
        // [PORT=<port>],...,[ADJNAME=<adjname>],[HELLOINTRVL=<helloIntrvl>]..... (tired to keep up with all params here)

        try {
            final String lines[] = osConn.execCmdAndGet("rtrv-ctrlch;", getCTAG());
            if ((lines == null) || (lines.length == 0)) {
                return;
            }
            for (String line : lines) {
                try {
                    Pattern p = getPattern("CtrlChName", ctrlPatternStr);
                    Matcher m = p.matcher(line);
                    if (m.find()) {
                        final String ctrlChName = m.group(1).trim();
                        if (ctrlChName.length() == 0) {
                            continue;
                        }
                        final eResult rez = new eResult();
                        rez.FarmName = Node.getFarmName();
                        rez.ClusterName = cluster3;
                        rez.Module = moduleName;
                        rez.NodeName = ctrlChName;
                        rez.time = time;
                        final String lines2[] = osConn.execCmdAndGet("rtrv-ctrlch::" + ctrlChName + ";", getCTAG());
                        if ((lines2 == null) || (lines2.length == 0)) {
                            continue;
                        }
                        for (String element : lines2) {
                            try {
                                p = getPattern("CTRLCH-LocalIP", lipPatternStr);
                                m = p.matcher(element);
                                if (m.find()) {
                                    rez.addSet("CTRLCH-LocalIP", m.group(1));
                                }
                                p = getPattern("CTRLCH-RemoteIP", ripPatternStr);
                                m = p.matcher(element);
                                if (m.find()) {
                                    rez.addSet("CTRLCH-RemoteIP", m.group(1));
                                }
                                p = getPattern("CTRLCH-LocalRID", lridPatternStr);
                                m = p.matcher(element);
                                if (m.find()) {
                                    rez.addSet("CTRLCH-LocalRID", m.group(1));
                                }
                                p = getPattern("CTRLCH-RemoteRID", rridPatternStr);
                                m = p.matcher(element);
                                if (m.find()) {
                                    rez.addSet("CTRLCH-RemoteRID", m.group(1));
                                }
                                p = getPattern("CTRLCH-Port", portPatternStr);
                                m = p.matcher(element);
                                if (m.find()) {
                                    rez.addSet("CTRLCH-Port", m.group(1));
                                }
                                p = getPattern("CTRLCH-Adjacency", adjPatternStr);
                                m = p.matcher(element);
                                if (m.find()) {
                                    rez.addSet("CTRLCH-Adjacency", m.group(1));
                                }
                                p = getPattern("CTRLCH-HelloInterval", hPatternStr);
                                m = p.matcher(element);
                                if (m.find()) {
                                    rez.addSet("CTRLCH-HelloInterval", m.group(1));
                                }
                                p = getPattern("CTRLCH-HelloIntervalMin", hMinPatternStr);
                                m = p.matcher(element);
                                if (m.find()) {
                                    rez.addSet("CTRLCH-HelloIntervalMin", m.group(1));
                                }
                                p = getPattern("CTRLCH-HelloIntervalMax", hMaxPatternStr);
                                m = p.matcher(element);
                                if (m.find()) {
                                    rez.addSet("CTRLCH-HelloIntervalMax", m.group(1));
                                }
                                p = getPattern("CTRLCH-DeadInterval", dPatternStr);
                                m = p.matcher(element);
                                if (m.find()) {
                                    rez.addSet("CTRLCH-DeadInterval", m.group(1));
                                }
                                p = getPattern("CTRLCH-DeadIntervalMin", dMinPatternStr);
                                m = p.matcher(element);
                                if (m.find()) {
                                    rez.addSet("CTRLCH-DeadIntervalMin", m.group(1));
                                }
                                p = getPattern("CTRLCH-DeadIntervalMax", dMaxPatternStr);
                                m = p.matcher(element);
                                if (m.find()) {
                                    rez.addSet("CTRLCH-DeadIntervalMax", m.group(1));
                                }
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, "Got exception parsing line [" + element + "]", t);
                            }
                        }
                        if ((rez.param != null) && (rez.param_name != null) && (rez.param.length != 0)
                                && (rez.param_name.length != 0)) {
                            vec.add(rez);
                        }
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exception parsing line [" + line + "]", t);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception reading from command's buffered stream", t);
        }
    }

    private final void getAdjCalient(final long time, final Vector vec) {

        final String adjPatternStr = "([\\.-_\\w]+)\\s*?:";
        final String lridPatternStr = "localrid\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String rridPatternStr = "remoterid\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String ctrlPatternStr0 = "ctrlchname0\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String ctrlPatternStr1 = "ctrlchname1\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String ctrlPatternStr2 = "ctrlchname2\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String ctrlPatternStr3 = "ctrlchname3\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String cctrlPatternStr = "currentctrlchname\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String adjIndexPatternStr = "adjindex\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String ospfPatternStr = "ospfarea\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String metricPatternStr = "metric\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String ospfAdjPatternStr = "ospfadj\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String adjTypePatternStr = "adjtype\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String rsvprrPatternStr = "rsvprrflag\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String rsvpgrPatternStr = "rsvpgrflag\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String ntfProcPatternStr = "ntfproc\\s*?=\\s*?([\\S&&[^,\"]]+)";

        // RTRV-ADJ:[TID]:[<adj>]:[CTAG];
        // SID DATE TIME
        // M CTAG COMPLD
        // "<AID>:[LOCALIP=<localip>],[REMOTERID=<remoterid>],[CTRLCHNAME0=<ctrlch0>],...,[CTRLCHNAME3=<ctrlch3>],
        // [CURRENTCTRLCHNAME=<currentctrlchname>],[ADJINDEX=<adjindex>],[OSPFAREA=<ospfarea>],..... (tired to keep up with all params here)

        final Vector adj = new Vector();
        try {
            final String lines1[] = osConn.execCmdAndGet("rtrv-adj;", getCTAG());
            if ((lines1 == null) || (lines1.length == 0)) {
                return;
            }
            for (String element : lines1) {
                try {
                    Pattern p = getPattern("AdjName", adjPatternStr);
                    Matcher m = p.matcher(element);
                    if (m.find()) {
                        adj.add(m.group(1));
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exception parsing line [" + element + "]", t);
                }
            }
            for (int i = 0; i < adj.size(); i++) {
                String name = (String) adj.get(i);
                final String lines[] = osConn.execCmdAndGet("rtrv-adj::" + name + ";", getCTAG());
                if ((lines == null) || (lines.length == 0)) {
                    continue;
                }
                eResult rez = new eResult();
                rez.FarmName = Node.getFarmName();
                rez.ClusterName = cluster4;
                rez.Module = moduleName;
                rez.NodeName = name;
                rez.time = time;
                for (String line : lines) {
                    try {
                        Pattern p = getPattern("ADJ-LocalRID", lridPatternStr);
                        Matcher m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("ADJ-LocalRID", m.group(1));
                        }
                        p = getPattern("ADJ-RemoteRID", rridPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("ADJ-RemoteRID", m.group(1));
                        }
                        p = getPattern("ADJ-CtrlChName0", ctrlPatternStr0);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("ADJ-CtrlChName0", m.group(1));
                        }
                        p = getPattern("ADJ-CtrlChName1", ctrlPatternStr1);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("ADJ-CtrlChName1", m.group(1));
                        }
                        p = getPattern("ADJ-CtrlChName2", ctrlPatternStr2);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("ADJ-CtrlChName2", m.group(1));
                        }
                        p = getPattern("ADJ-CtrlChName3", ctrlPatternStr3);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("ADJ-CtrlChName3", m.group(1));
                        }
                        p = getPattern("ADJ-CurrentCtrlChName", cctrlPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("ADJ-CurrentCtrlChName", m.group(1));
                        }
                        p = getPattern("ADJ-AdjIndex", adjIndexPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("ADJ-AdjIndex", m.group(1));
                        }
                        p = getPattern("ADJ-OSPFArea", ospfPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("ADJ-OSPFArea", m.group(1));
                        }
                        p = getPattern("ADJ-Metric", metricPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("ADJ-Metric", m.group(1));
                        }
                        p = getPattern("ADJ-OSPFAdj", ospfAdjPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("ADJ-OSPFAdj", m.group(1));
                        }
                        p = getPattern("ADJ-AdjType", adjTypePatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("ADJ-AdjType", m.group(1));
                        }
                        p = getPattern("ADJ-RSVPRRFlag", rsvprrPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("ADJ-RSVPRRFlag", m.group(1));
                        }
                        p = getPattern("ADJ-RSVPGRFlag", rsvpgrPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("ADJ-RSVPGRFlag", m.group(1));
                        }
                        p = getPattern("ADJ-NTFProc", ntfProcPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("ADJ-NTFProc", m.group(1));
                        }
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Got exception parsing line [" + line + "]", t);
                    }
                }
                if ((rez.param != null) && (rez.param_name != null) && (rez.param.length != 0)
                        && (rez.param_name.length != 0)) {
                    vec.add(rez);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception reading from command's buffered stream", t);
        }
    }

    private final void getLinks(final long time, final Vector vec) {

        final String linkPatternStr = "([\\.-_\\w+]+)\\s*?:";
        final String linkTypePatternStr = "linktype\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String lridPatternStr = "localrid\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String rridPatternStr = "remoterid\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String lIPPatternStr = "localip\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String rIPPatternStr = "remoteip\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String adjNamePatternStr = "adjname\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String lIfIndexPatternStr = "localifindex\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String rIfIndexPatternStr = "remoteif\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String wdmRemoteIFPatternStr = "wdmremoteteif\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String fltDetectPatternStr = "fltdetect\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String metricPatternStr = "metric\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String lmpVerPatternStr = "lmpverify\\s*?=\\s*?([\\S&&[^,\"]]+)";
        final String portPatternStr = "port\\s*?=\\s*?([\\S&&[^,\"]]+)";

        // RTRV-LINK:[TID]:[<link>]:[CTAG];
        // SID DATE TIME 
        // M CTAG COMPLD  
        // <AID>:[LINKTYPE=<linktype>],[LOCARID=<localrid>], [REMOTERID=<remoterid>],[LOCALIP=<localip>], [REMOTEIP=<remoteip>],
        // [ADJNAME=<adjname>], [LOCALIFINDEX=<localifindex>], [REMOTEIF=<remoteifindex>], [WDMREMOTETEIF=<wdmremoteteif>], 
        // [FLTDETECT=<fltdetect>],[METRIC=<metric>], [LMPVERIFY=<lmpverify>],[ADJTYPE=<adjtype>], [ADMIN=<admin>],
        // [TOTALBANDWIDTH0-7=<totalbandwidth0-7>], [AVAILBANDWIDTH0-7=<availbandwidth0-7>], [PORT=<port>],[LSPENCODING=<lspencoding>], 
        // [BANDWIDTH=<bandwidth>], [PORTMINPRIORITY=<portminpriority>], [PORTREMPORTLABEL=<portremportlabel>], [PORTLOLSTATE=<portlolstate],
        // [SWCAP=<swcap>], [AS=<as>],[OS=<os>],[OC=<oc>],[AL=<al>], [COLOR=<color>]  ;

        // The returned result is:
        //		"TEST-TE:LINKTYPE=Numbered,
        //  LOCALRID=53.53.53.53,REMOTERID=4.5.6.7,LOCALIP=5.5.5.5,REMOTEIP=5.5.5.6,ADJNAME=TEST-USING-ADMIN-CC,
        //  LOCALIFINDEX=858980353,REMOTETEIF=0,WDMREMOTETEIF=0,FLTDETECT=Y,METRIC=1,LMPVERIFY=30,
        //  ADJTYPE=CALIENTGMPLSPEER,ADMIN=,TOTALBANDWIDTH0=OC48STM16,AVAILBANDWIDTH0=0.000 Mbps,
        //  TOTALBANDWIDTH1=OC48STM16,AVAILBANDWIDTH1=0.000 Mbps,TOTALBANDWIDTH2=OC48STM16,AVAILBANDWIDTH2=0.000 Mbps,
        //  TOTALBANDWIDTH3=OC48STM16,AVAILBANDWIDTH3=0.000 Mbps,TOTALBANDWIDTH4=OC48STM16,AVAILBANDWIDTH4=0.000 Mbps,
        //  TOTALBANDWIDTH5=OC48STM16,AVAILBANDWIDTH5=0.000 Mbps,TOTALBANDWIDTH6=OC48STM16,AVAILBANDWIDTH6=0.000 Mbps,
        //  TOTALBANDWIDTH7=OC48STM16,AVAILBANDWIDTH7=0.000 Mbps,PORT=0.11a.4,LSPENCODING=SDHSONET,BANDWIDTH=OC48STM16,
        //  PORTMINPRIORITY=7,PORTREMPORTLABEL=10.11a.6,PORTLOLSTATE=N,SWCAP=LSC,COLOR=0,AS=IS,OS=OOS,OC=FAIL,AL=MJ"

        final Vector links = new Vector();
        try {
            final String lines1[] = osConn.execCmdAndGet("rtrv-link;", getCTAG());
            if ((lines1 == null) || (lines1.length == 0)) {
                return;
            }
            for (String element : lines1) {
                try {
                    Pattern p = getPattern("linkName", linkPatternStr);
                    Matcher m = p.matcher(element);
                    if (m.find()) {
                        links.add(m.group(1));
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exception parsing line [" + element + "]", t);
                }
            }
            for (int i = 0; i < links.size(); i++) {
                String name = (String) links.get(i);
                final String lines[] = osConn.execCmdAndGet("rtrv-link::" + name + ";", getCTAG());
                if ((lines == null) || (lines.length == 0)) {
                    continue;
                }
                eResult rez = new eResult();
                rez.FarmName = Node.getFarmName();
                rez.ClusterName = cluster5;
                rez.Module = moduleName;
                rez.NodeName = name;
                rez.time = time;
                for (String line : lines) {
                    try {
                        Pattern p = getPattern("LINK-LinkType", linkTypePatternStr);
                        Matcher m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("LINK-LinkType", m.group(1));
                        }
                        p = getPattern("LINK-LocalRID", lridPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("LINK-LocalRID", m.group(1));
                        }
                        p = getPattern("LINK-RemoteRID", rridPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("LINK-RemoteRID", m.group(1));
                        }
                        p = getPattern("LINK-LocalIP", lIPPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("LINK-LocalIP", m.group(1));
                        }
                        p = getPattern("LINK-RemoteIP", rIPPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("LINK-RemoteIP", m.group(1));
                        }
                        p = getPattern("LINK-LocalIFIndex", lIfIndexPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("LINK-LocalIFIndex", m.group(1));
                        }
                        p = getPattern("LINK-RemoteIF", rIfIndexPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("LINK-RemoteIF", m.group(1));
                        }
                        p = getPattern("LINK-WDMRemoteTEIF", wdmRemoteIFPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("LINK-WDMRemoteTEIF", m.group(1));
                        }
                        p = getPattern("LINK-FLTDetect", fltDetectPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            final String val = m.group(1);
                            if ((val.compareToIgnoreCase("Y") == 0) || (val.compareToIgnoreCase("Yes") == 0)) {
                                rez.addSet("LINK-FLTDetect", Boolean.TRUE);
                            } else {
                                rez.addSet("LINK-FLTDetect", Boolean.FALSE);
                            }
                        }
                        p = getPattern("LINK-Metric", metricPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("LINK-Metric", m.group(1));
                        }
                        p = getPattern("LINK-LMPVerify", lmpVerPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("LINK-LMPVerify", m.group(1));
                        }
                        p = getPattern("LINK-AdjName", adjNamePatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("LINK-AdjName", m.group(1));
                        }
                        p = getPattern("LINK-PortName", portPatternStr);
                        m = p.matcher(line);
                        if (m.find()) {
                            rez.addSet("LINK-PortName", m.group(1));
                        }
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Got exception parsing line [" + line + "]", t);
                    }
                }
                if ((rez.param != null) && (rez.param_name != null) && (rez.param.length != 0)
                        && (rez.param_name.length != 0)) {
                    vec.add(rez);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception reading from command's buffered stream", t);
        }
    }

    private final void getPorts(final long time, final Vector vec) {

        final String eqptPatternStr = "([\\.-_\\w+]+)\\s*?:";

        // RTRV-PORT:[TID]:<eqptId>:[CTAG]::[<owner>],[<portcategory>];
        // SID DATE TIME 
        // M CTAG COMPLD 
        // "<AID>:<portType>,<inOwner>,<outOwner>: [INOPTDEGR=<inoptdegr>], [INOPTCRIT=<inoptcrit>], [OUTOPTDEGR=<outoptdegr>], 
        // [OUTOPTCRIT=<outoptcrit>], [INOPTHI=<inopthi>], [ATTNMODE=<attnmode>], [OUTPOWER=<outpower>],[VARIANT=<variant>],
        // [ALIAS=<alias>], [IN_AS=<inAS>],[IN_OS=<inOS>],[IN_OC=<inOC>], [OUT_AS=<outAS>], [OUT_OS=<outOS>], [OUT_OC=<outOC>]" ;

        final Vector eqpts = new Vector();
        final Vector tmp = new Vector();
        try {
            final String lines1[] = osConn.execCmdAndGet("rtrv-eqpt;", getCTAG());
            if ((lines1 == null) || (lines1.length == 0)) {
                return;
            }
            for (String element : lines1) {
                try {
                    Pattern p = getPattern("eqptName", eqptPatternStr);
                    Matcher m = p.matcher(element);
                    if (m.find()) {
                        eqpts.add(m.group(1));
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exception parsing line [" + element + "]", t);
                }
            }
            for (int i = 0; i < eqpts.size(); i++) {
                String name = (String) eqpts.get(i);
                final String lines[] = osConn.execCmdAndGet("rtrv-port::" + name + ":::,free;", getCTAG());
                if ((lines == null) || (lines.length == 0)) {
                    continue;
                }
                for (String line : lines) {
                    try {
                        Pattern p = getPattern("eqptName", eqptPatternStr);
                        Matcher m = p.matcher(line);
                        if (m.find()) {
                            final String port = m.group(1);
                            if (!tmp.contains(port)) {
                                tmp.add(port);
                            }
                        }
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Got exception parsing line [" + line + "]", t);
                    }
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception reading from command's buffered stream", t);
        }
        eResult rez = new eResult();
        rez.FarmName = Node.getFarmName();
        rez.ClusterName = cluster6;
        rez.Module = moduleName;
        rez.NodeName = "FreePorts";
        rez.time = time;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < tmp.size(); i++) {
            String port = (String) tmp.get(i);
            if ((port == null) || (port.length() == 0)) {
                continue;
            }
            if (i != 0) {
                buf.append(",");
            }
            buf.append(port);
        }
        rez.addSet("FreePorts", buf.toString());
        vec.add(rez);
    }

    @Override
    public Object doProcess() throws Exception {

        final Vector v = new Vector();
        if (osConn == null) {
            osConn = OSTelnetFactory.getMonitorInstance(switchType);
        }

        if ((osConn != null) && (switchType == OSTelnet.CALIENT)) {
            long time = NTPDate.currentTimeMillis();
            getNPPorts(time, v);
            getOSPFCalient(time, v);
            getRSVPCalient(time, v);
            getCtrlChCalient(time, v);
            getAdjCalient(time, v);
            getLinks(time, v);
            getPorts(time, v);
        }

        if (logger.isLoggable(Level.FINEST)) {
            if ((v == null) || (v.size() == 0)) {
                logger.log(Level.FINEST, " monOSGMPLS returning null Vector");
            } else {
                logger.log(Level.FINEST, " monOSGMPLS returning\n" + v.toString() + "\n");
            }
        }
        if ((v == null) || (v.size() == 0)) {
            return null;
        }
        return v;
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
        logger.log(Level.INFO, " monOSGMPLS stop() Request . SHOULD NOT!!!");
        return true;
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    static public void main(String[] args) {

        ////    	String patternStr = "routerid.*?=\\s*?(\\S+)\\s*?,*?$*?";
        //    	final String cardPatternStr = "(\\d+\\.\\w+)\\s*:.*cardtype.*?=\\s*([\\S&&[^,\"]]+)";
        //
        //    	Pattern pattern = Pattern.compile(cardPatternStr, Pattern.CASE_INSENSITIVE);
        //        Matcher matcher = pattern.matcher("\"0.7f:CardType=ADC");
        //    	if (matcher.find())  {
        //    		System.out.println("'"+matcher.group(1)+"'");
        //    		System.out.println("'"+matcher.group(2)+"'");
        //    	}
        //        
        //    	if (true) return;

        monOSGMPLS aa = new monOSGMPLS();
        System.setProperty("lia.util.telnet.CalientMonitorUsername", "admin");
        System.setProperty("lia.util.telnet.CalientMonitorPasswd", "cit***");
        System.setProperty("lia.util.telnet.CalientMonitorHostname", "137.164.19.54");
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
        aa.init(new MNode(host, ad, new MCluster("CMap", null), null), "SwitchType=Calient");

        try {
            for (int k = 0; k < 10000; k++) {
                Vector bb = (Vector) aa.doProcess();
                for (int q = 0; q < bb.size(); q++) {
                    System.out.println(bb.get(q));
                }
                System.out.println("-------- sleeeping ----------");
                Thread.sleep(5000);
                System.out.println("-------- doProcess-ing --------- k=" + k);
            }
        } catch (Exception e) {
            System.out.println(" failed to process !!!");
        }
    }

    /** Utility method that is used to extract a given pattern if already compiled, 
     * otherwise is compiled on the spot the new pattern which can be used on the spot
     * @param expr A string that identify the pattern
     * @param patternStr A pattern expression that is used to compile the new pattern if it doesn't exist
     * @return The corresponding patern
     */
    protected Pattern getPattern(String expr, String patternStr) {

        if (compiledPatterns.containsKey(expr)) {
            return (Pattern) compiledPatterns.get(patternStr);
        }
        Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
        compiledPatterns.put(expr, pattern);
        return pattern;
    }

    /** Utility method that is used to obtain the next value for the CTAG parameters of the TL1 commands */
    protected String getCTAG() {
        ctagCounter = (ctagCounter + 1) % 100000;
        return "" + ctagCounter;
    }

} // end of class monOSGMPLS
