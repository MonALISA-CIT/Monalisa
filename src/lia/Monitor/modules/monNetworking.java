package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.util.ntp.NTPDate;

/**
 * Networking module used to capture networking information regarding the localhost
 * @author cipsm
 *
 */
public class monNetworking extends cmdExec implements MonitoringModule {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(monNetworking.class.getName());

    /** serial version number */
    static final long serialVersionUID = 1105200606091979L;

    /** is this a 64 bits arch ? */
    private boolean is64BitArch = false;

    private long currentTime = 0L;

    // flags to specify if we should read classes of params or not
    private boolean useIP = true, useTCP = true, useUDP = true, useTCPExt = true, parseIF = false;

    protected InterfaceHandler ifHandler = null;

    //	// the possible params
    //	private final String[] tmetric = new String[] { 
    //	// IP params
    //	"InReceives", // "Rate of received packets"
    //	"InHdrErrors", // "Rate of packets received with incorrect headers"
    //	"InAddrErrors", // " Rate of packets received with invalid address field"
    //	"ForwDatagrams", // "Rate of forwarded datagrams"
    //	"InUnknownProtos", // "Rate of packets with unknown protocol number"
    //	"InDiscards", // "Rate of discarded packets"
    //	"InDelivers", // "Rate of packets delivered"
    //	"OutRequests", // "Rate of requests sent out"
    //	"OutDiscards", // "Rate of outgoing packets dropped"
    //	"OutNoRoutes", // "Rate of outgoing dropped packets because of missing route"
    //	"ReasmTimeout", // "Rate of fragments dropped after time out"
    //	"ReasmReqds", // "Rate of reassemblies requires"
    //	"ReasmOKs", // "Rate of packets reassembled ok"
    //	"ReasmFails", // "Rate of packets reassembled with failure"
    //	"FragOKs", // "Rate of fragments received ok"
    //	"FragFails", // "Rate of fragments failed"
    //	"FragCreates", // "Rate of created fragments"
    //	// TCP params
    //	"AttemptFails", // "Rate of failed connection attempts"
    //	"EstabResets", // "Rate of connection resets received"
    //	"InSegs", // "Rate of segments received"
    //	"OutSegs", // "Rate of segments sent"
    //	"RetransSegs", // "Rate of segments retransmitted"
    //	"InErrs", // "Rate of bad segments received"
    //	"OutRsts", // "Rate of resets sent"
    //	// UDP params
    //	"InDatagrams", // "Rate of datagrams received"
    //	"NoPorts", // "Rate of datagrams to unknown ports received"
    //	"InErrors", // "Rate of datagrams with errors received"
    //	"OutDatagrams", // "Rate of sent datagrams"
    //	// TCP Extended params
    //	"SyncookiesSent", // "Rate of SYN cookies sent"
    //	"SyncookiesRecv", // "Rate of SYN cookies received"
    //	"SyncookiesFailed", // "Rate of SYN cookies failed"
    //	"EmbryonicRsts", // "Rate of resets received because of embryonic sockets"
    //	"PruneCalled", // "Rate of packets pruned from receive queue because of buffer overrun"
    //	"RcvPruned", // "Rate of packets prunned from receive queue"
    //	"OfoPruned", // "Rate of packets prunned from out-of-order queue because of overflow"
    //	"TW", // "Rate of tcp sockets finished time wait in fast timer"
    //	"TWRecycled", // "Rate of sockets recicled by time stamp"
    //	"TWKilled", // "Rate of TCP sockets finished time wait in slow timer"
    //	"PAWSPassive", // "Rate of passive connections rejected because of timestamp"
    //	"PAWSActive", // "Rate of active connection rejected because of timestamp"
    //	"PAWSEstab", // "Rate of packets rejected in established connection because of timestamp"
    //	"DelayedACKs", // "Rate of delayed acks sent"
    //	"DelayedACKLocked", // "Rate of delayed acks further delayed because of socket lock"
    //	"DelayedACKLost", // "Rate of quick ack mode activated times"
    //	"ListenOverflows", // "Rate of how many times the listening queue of a socket overflowed"
    //	"ListenDrops", // "Rate of SYNs to LISTEN sockets ignored"
    //	"TCPPrequeued", // "Rate of packets directly queued to recvmsg - urgwent packets"
    //	"TCPDirectCopyFromBacklog", // "Rate of packets directly received from backlog"
    //	"TCPDirectCopyFromPrequeue", // "Rate of packets directly received from prequeue"
    //	"TCPPrequeueDropped", // "Rate of packets dropped from prequeue"
    //	"TCPHPHits", // "Rate of packet headers predicted"
    //	"TCPHPHitsToUser", // "Rate of packet headers predicted and directly queued to user"
    //	"SockMallocOOM", // "Rate of how many times oom was encoutered when sending"
    //	"TCPPureAcks", // "Rate of pure ack received (i.e. no data was sent)"
    //	"TCPHPAcks", // "Rate of ack received in TCP fast path (i.e. header prediction path)"
    //	"TCPRenoRecovery", // "Rate of how many times fast retransmission went into full retransmit (short cut retransmit mechanism that works faster when only a few packets got lost)"
    //	"TCPSackRecovery", // "Rate of how many times TCP recovered from packet loss due to SACK data"
    //	"TCPSACKReneging", // "Rate of bad SACKs received"
    //	"TCPFACKReorder", // "Rate of how many times reordering using FACK was detected"
    //	"TCPSACKReorder", // "Rate of how many times reordering using SACK was detected"
    //	"TCPRenoReorder", // "Rate of how many times reordering using reno fast retransmit was detected"
    //	"TCPTSReorder", // "Rate of how many times reordering using time stamp was detected"
    //	"TCPFullUndo", // "Rate of congestion windows fully recovered"
    //	"TCPPartialUndo", // "Rate of congestion windows partially recovered using Hoe heuristic"
    //	"TCPDSACKUndo", // "Rate of congestion windows recovered using DSACK"
    //	"TCPLossUndo", // "Rate of congestion windows recovered after partial ack"
    //	"TCPLoss", // "Rate of TCP data loss events"
    //	"TCPLostRetransmit", // "Rate of retransmits lost"
    //	"TCPRenoFailures", // "Rate of timeouts after reno fast retransmit"
    //	"TCPSackFailures", // "Rate of timeouts after SACK recovery"
    //	"TCPLossFailures", // "Rate of timeouts in loss state"
    //	"TCPFastRetrans", // "Rate of fast retransmits"
    //	"TCPForwardRetrans", // "Rate of forward retransmits"
    //	"TCPSlowStartRetrans", // "Rate of retransmits in slow start"
    //	"TCPTimeouts", // "Rate of other TCP timeouts"
    //	"TCPRenoRecoveryFail", // "Rate of how many times reno fast retransmits failed"
    //	"TCPSackRecoveryFail", // "Rate of how many times sack retransmits failed"
    //	"TCPSchedulerFailed", // "Rate of how many times receiver scheduled too late for direct processing"
    //	"TCPRcvCollapsed", // "Rate of packets collapsed in receive queue due to low socket buffer"
    //	"TCPDSACKOldSent", // "Rate of DSACKs sent for old packets"
    //	"TCPDSACKOfoSent", // "Rate of DSACKs sent for out of order packets"
    //	"TCPDSACKRecv", // "Rate of DSACKs received"
    //	"TCPDSACKOfoRecv", // "Rate of DSACKs for out of order packets received"
    //	"TCPAbortOnSyn", // "Rate of connections reset due to unexpected SYN"
    //	"TCPAbortOnData", // "Rate of connections reset due to unexpected data"
    //	"TCPAbortOnClose", // "Rate of connections reset due to early user close"
    //	"TCPAbortOnMemory", // "Rate of connections aborted due to memory pressure"
    //	"TCPAbortOnTimeout", // "Rate of connections aborted due to timeout"
    //	"TCPAbortOnLinger", // "Rate of connections aborted after user close in linger timeout"
    //	"TCPAbortFailed", // "Rate of how many times failed to send RST due to no memory"
    //	"TCPMemoryPressures", // "Rate of how many times TCP ran low on memory"
    //	};

    public monNetworking() {
        super("monNetworking");
        info.ResTypes = new String[] {};
        isRepetitive = true;
    }

    @Override
    public MonModuleInfo init(MNode Node, String arg) {
        logger.log(Level.INFO, " INIT monNetworking Module args: " + arg);
        this.Node = Node;
        info.ResTypes = new String[] {};
        if (arg != null) {
            String[] args = arg.split("(\\s)*;(\\s)*");
            if (args != null) {
                for (String arg2 : args) {
                    String argTemp = arg2.trim();
                    if (argTemp.startsWith("useIP=")) {
                        try {
                            useIP = Boolean.valueOf(argTemp.substring(6)).booleanValue();
                        } catch (Exception e) {
                        }
                    } else if (argTemp.startsWith("useTCP=")) {
                        try {
                            useTCP = Boolean.valueOf(argTemp.substring(7)).booleanValue();
                        } catch (Exception e) {
                        }
                    } else if (argTemp.startsWith("useUDP=")) {
                        try {
                            useUDP = Boolean.valueOf(argTemp.substring(7)).booleanValue();
                        } catch (Exception e) {
                        }
                    } else if (argTemp.startsWith("useTCPExt=")) {
                        try {
                            useTCPExt = Boolean.valueOf(argTemp.substring(10)).booleanValue();
                        } catch (Exception e) {
                        }
                    } else if (argTemp.startsWith("parseIfs=")) {
                        try {
                            parseIF = Boolean.valueOf(argTemp.substring(9)).booleanValue();
                        } catch (Exception e) {
                        }
                    }
                }
            }
        }
        return info;
    }

    @Override
    public Object doProcess() throws Exception {

        if (!useIP && !useTCP && !useUDP && !useTCPExt) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "[monNetworking: All flags are false ... no results returned]");
            }
            return null;
        }

        if (currentTime == 0L) {
            currentTime = NTPDate.currentTimeMillis();
            return null;
        }
        currentTime = NTPDate.currentTimeMillis();

        Vector res = new Vector();

        FileReader fr = null;
        BufferedReader br = null;

        // parse buffers...
        parseBuffers(res);

        if (parseIF) {
            try {
                parseInterfaces(res);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[monNetwork: Got general exception parsing status of the interfaces", t);
            }
        }

        //	first parse /proc/net/snmp
        if (useIP || useTCP || useUDP) {
            try {
                fr = new FileReader("/proc/net/snmp");
                br = new BufferedReader(fr);
                while (true) {
                    // we need to read two lines at a time, the header and the actual values
                    final String header = br.readLine();
                    if (header == null) {
                        break;
                    }
                    final String values = br.readLine();
                    if (values == null) {
                        break;
                    }
                    if (useIP && header.startsWith("Ip: ") && values.startsWith("Ip: ")) {
                        parseIPTable(header.substring(4), values.substring(4), res);
                        continue;
                    }
                    if (useTCP && header.startsWith("Tcp: ") && values.startsWith("Tcp: ")) {
                        parseTCPTable(header.substring(5), values.substring(5), res);
                        continue;
                    }
                    if (useUDP && header.startsWith("Udp: ") && values.startsWith("Udp: ")) {
                        parseUDPTable(header.substring(5), values.substring(5), res);
                        continue;
                    }
                }

            } catch (FileNotFoundException fne) {
                logger.log(Level.WARNING, "[monNetworking: File /proc/net/snmp not found.]");
            } catch (IOException ioe) {
                logger.log(Level.WARNING, "[monNetwork: Got I/O exception reading /proc/net/snmp", ioe);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[monNetwork: Got general exception reading /proc/net/snmp", t);
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (Throwable ignore) {
                    }
                }

                if (fr != null) {
                    try {
                        fr.close();
                    } catch (Throwable ignore) {
                    }
                }
            }
        }

        // second parse /proc/net/netstat
        if (useTCPExt) {
            try {
                fr = new FileReader("/proc/net/netstat");
                br = new BufferedReader(fr);
                while (true) {
                    // we need to read two lines at a time, the header and the actual values
                    final String header = br.readLine();
                    if (header == null) {
                        break;
                    }
                    final String values = br.readLine();
                    if (values == null) {
                        break;
                    }
                    if (header.startsWith("TcpExt: ") && values.startsWith("TcpExt: ")) {
                        parseExtendedTCPTable(header.substring(8), values.substring(8), res);
                        continue;
                    }
                }
            } catch (FileNotFoundException fne) {
                logger.log(Level.WARNING, "[monNetworking: File /proc/net/netstat not found.]");
            } catch (IOException ioe) {
                logger.log(Level.WARNING, "[monNetwork: Got I/O exception reading /proc/net/netstat", ioe);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[monNetwork: Got general exception reading /proc/net/netstat", t);
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (Throwable ignore) {
                    }
                }

                if (fr != null) {
                    try {
                        fr.close();
                    } catch (Throwable ignore) {
                    }
                }
            }
        }

        return res;
    }

    @Override
    public String getOsName() {
        return "linux";
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    private final HashMap lastValues = new HashMap();

    public final double getInstantValue(String key, long time, String newVal) {

        TreeMap h = null;
        if (!lastValues.containsKey(key)) {
            h = new TreeMap();
            lastValues.put(key, h);
        } else {
            h = (TreeMap) lastValues.get(key);
        }
        if (h.size() == 0) {
            h.put(Long.valueOf(time), newVal);
            return 0D;
        }
        if (h.size() == 2) {
            h.remove(h.firstKey());
        }
        h.put(Long.valueOf(time), newVal);
        Long first = (Long) h.firstKey();
        Long last = (Long) h.lastKey();
        String s1 = (String) h.get(first);
        String s2 = (String) h.get(last);
        double d = 0D;
        try {
            d = Double.parseDouble(diffWithOverflowCheck(s2, s1));
        } catch (Throwable t) {
            d = 0D;
        }
        d = d / ((last.longValue() - first.longValue()) / 1000D);
        return d;
    }

    private final void parseInterfaces(final Vector res) {

        if (ifHandler == null) {
            ifHandler = new InterfaceHandler();
        }
        ifHandler.check();
        final HashMap ifs = ifHandler.getIfStatistics();
        if (ifs != null) {
            for (Iterator it = ifs.keySet().iterator(); it.hasNext();) {
                final InterfaceStatistics stat = (InterfaceStatistics) ifs.get(it.next());
                String name = stat.getName();
                Result rr = new Result(Node.getFarmName(), Node.getClusterName(), name, TaskName, null);
                rr.addSet("MTU", stat.getMTU());
                rr.addSet("RX", stat.getRX());
                rr.addSet("RXPackets", stat.getRXPackets());
                rr.addSet("RXErrors", stat.getRXErrors());
                rr.addSet("RXDropped", stat.getRXDropped());
                rr.addSet("RXOverruns", stat.getRXOverruns());
                rr.addSet("RXFrame", stat.getRXFrame());
                rr.addSet("TX", stat.getTX());
                rr.addSet("TXPackets", stat.getTXPackets());
                rr.addSet("TXErrors", stat.getTXErrors());
                rr.addSet("TXDropped", stat.getTXDropped());
                rr.addSet("TXOverruns", stat.getTXOverruns());
                rr.addSet("TXCarrier", stat.getTXCarrier());
                rr.addSet("Collisions", stat.getCollisions());
                rr.time = currentTime;
                res.add(rr);
            }
        }
        final HashMap ifsStatic = ifHandler.getIfStatisticsStatic();
        if (ifsStatic != null) {
            for (Iterator it = ifsStatic.keySet().iterator(); it.hasNext();) {
                final InterfaceStatisticsStatic stat = (InterfaceStatisticsStatic) ifsStatic.get(it.next());
                String name = stat.getName();
                Result rr = new Result(Node.getFarmName(), Node.getClusterName(), name, TaskName, null);
                rr.addSet("MaxSpeed", stat.getMaxSpeed());
                rr.time = currentTime;
                res.add(rr);
            }

        }

    }

    private final void parseBuffers(final Vector res) {
        FileReader fr = null;
        BufferedReader br = null;
        try {
            fr = new FileReader("/proc/sys/net/ipv4/tcp_mem");
            br = new BufferedReader(fr);
            final String values = br.readLine();
            if (values != null) {
                StringTokenizer st = new StringTokenizer(values);
                if (st.countTokens() == 3) {
                    final String bmin = st.nextToken();
                    final String bdef = st.nextToken();
                    final String bmax = st.nextToken();
                    boolean found = false;
                    Result rr = new Result(Node.getFarmName(), Node.getClusterName(), "tcp_mem", TaskName, null);
                    try {
                        rr.addSet("MinTCPAllocatableBufferSpace", Double.parseDouble(bmin));
                        found = true;
                    } catch (Exception e) {
                    }
                    try {
                        rr.addSet("DefaultTCPAllocatableBufferSpace", Double.parseDouble(bdef));
                        found = true;
                    } catch (Exception e) {
                    }
                    try {
                        rr.addSet("MaxTCPAllocatableBufferSpace", Double.parseDouble(bmax));
                        found = true;
                    } catch (Exception e) {
                    }
                    if (found) {
                        rr.time = currentTime;
                        res.add(rr);
                    }
                }
            }
        } catch (FileNotFoundException fne) {
            logger.log(Level.WARNING, "[monNetworking: File /proc/sys/net/ipv4/tcp_mem not found.]");
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "[monNetwork: Got I/O exception reading /proc/sys/net/ipv4/tcp_mem", ioe);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[monNetwork: Got general exception reading /proc/sys/net/ipv4/tcp_mem", t);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignore) {
                }
            }
            if (fr != null) {
                try {
                    fr.close();
                } catch (Throwable ignore) {
                }
            }
        }

        try {
            fr = new FileReader("/proc/sys/net/ipv4/tcp_rmem");
            br = new BufferedReader(fr);
            final String values = br.readLine();
            if (values != null) {
                StringTokenizer st = new StringTokenizer(values);
                if (st.countTokens() == 3) {
                    final String rwmin = st.nextToken();
                    final String rwdef = st.nextToken();
                    final String rwmax = st.nextToken();
                    boolean found = false;
                    Result rr = new Result(Node.getFarmName(), Node.getClusterName(), "tcp_rmem", TaskName, null);
                    try {
                        rr.addSet("MinTransmitWindow", Double.parseDouble(rwmin));
                        found = true;
                    } catch (Exception e) {
                    }
                    try {
                        rr.addSet("DefaultTransmitWindow", Double.parseDouble(rwdef));
                        found = true;
                    } catch (Exception e) {
                    }
                    try {
                        rr.addSet("MaxTransmitWindow", Double.parseDouble(rwmax));
                        found = true;
                    } catch (Exception e) {
                    }
                    if (found) {
                        rr.time = currentTime;
                        res.add(rr);
                    }
                }
            }
        } catch (FileNotFoundException fne) {
            logger.log(Level.WARNING, "[monNetworking: File /proc/sys/net/ipv4/tcp_rmem not found.]");
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "[monNetwork: Got I/O exception reading /proc/sys/net/ipv4/tcp_rmem", ioe);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[monNetwork: Got general exception reading /proc/sys/net/ipv4/tcp_rmem", t);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignore) {
                }
            }
            if (fr != null) {
                try {
                    fr.close();
                } catch (Throwable ignore) {
                }
            }
        }

        try {
            fr = new FileReader("/proc/sys/net/ipv4/tcp_wmem");
            br = new BufferedReader(fr);
            final String values = br.readLine();
            if (values != null) {
                StringTokenizer st = new StringTokenizer(values);
                if (st.countTokens() == 3) {
                    final String twmin = st.nextToken();
                    final String twdef = st.nextToken();
                    final String twmax = st.nextToken();
                    boolean found = false;
                    Result rr = new Result(Node.getFarmName(), Node.getClusterName(), "tcp_wmem", TaskName, null);
                    try {
                        rr.addSet("MinReceiveWindow", Double.parseDouble(twmin));
                        found = true;
                    } catch (Exception e) {
                    }
                    try {
                        rr.addSet("DefaultReceiveWindow", Double.parseDouble(twdef));
                        found = true;
                    } catch (Exception e) {
                    }
                    try {
                        rr.addSet("MaxReceiveWindow", Double.parseDouble(twmax));
                        found = true;
                    } catch (Exception e) {
                    }
                    if (found) {
                        rr.time = currentTime;
                        res.add(rr);
                    }
                }
            }
        } catch (FileNotFoundException fne) {
            logger.log(Level.WARNING, "[monNetworking: File /proc/sys/net/ipv4/tcp_wmem not found.]");
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "[monNetwork: Got I/O exception reading /proc/sys/net/ipv4/tcp_wmem", ioe);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[monNetwork: Got general exception reading /proc/sys/net/ipv4/tcp_wmem", t);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignore) {
                }
            }
            if (fr != null) {
                try {
                    fr.close();
                } catch (Throwable ignore) {
                }
            }
        }

        try {
            fr = new FileReader("/proc/sys/net/core/wmem_default");
            br = new BufferedReader(fr);
            final String values = br.readLine();
            if (values != null) {
                boolean found = false;
                Result rr = new Result(Node.getFarmName(), Node.getClusterName(), "wmem_default", TaskName, null);
                try {
                    rr.addSet("DefaultTCPTransmitWindow", Double.parseDouble(values.trim()));
                    found = true;
                } catch (Exception e) {
                }
                if (found) {
                    rr.time = currentTime;
                    res.add(rr);
                }
            }
        } catch (FileNotFoundException fne) {
            logger.log(Level.WARNING, "[monNetworking: File /proc/sys/net/core/wmem_default not found.]");
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "[monNetwork: Got I/O exception reading /proc/sys/net/core/wmem_default", ioe);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[monNetwork: Got general exception reading /proc/sys/net/core/wmem_default", t);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignore) {
                }
            }
            if (fr != null) {
                try {
                    fr.close();
                } catch (Throwable ignore) {
                }
            }
        }

        try {
            fr = new FileReader("/proc/sys/net/core/rmem_default");
            br = new BufferedReader(fr);
            final String values = br.readLine();
            if (values != null) {
                boolean found = false;
                Result rr = new Result(Node.getFarmName(), Node.getClusterName(), "rmem_default", TaskName, null);
                try {
                    rr.addSet("DefaultTCPReceiveWindow", Double.parseDouble(values.trim()));
                    found = true;
                } catch (Exception e) {
                }
                if (found) {
                    rr.time = currentTime;
                    res.add(rr);
                }
            }
        } catch (FileNotFoundException fne) {
            logger.log(Level.WARNING, "[monNetworking: File /proc/sys/net/core/rmem_default not found.]");
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "[monNetwork: Got I/O exception reading /proc/sys/net/core/rmem_default", ioe);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[monNetwork: Got general exception reading /proc/sys/net/core/rmem_default", t);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignore) {
                }
            }
            if (fr != null) {
                try {
                    fr.close();
                } catch (Throwable ignore) {
                }
            }
        }

        try {
            fr = new FileReader("/proc/sys/net/core/wmem_max");
            br = new BufferedReader(fr);
            final String values = br.readLine();
            if (values != null) {
                boolean found = false;
                Result rr = new Result(Node.getFarmName(), Node.getClusterName(), "wmem_max", TaskName, null);
                try {
                    rr.addSet("MaxSizeTransmitWindow", Double.parseDouble(values.trim()));
                    found = true;
                } catch (Exception e) {
                }
                if (found) {
                    rr.time = currentTime;
                    res.add(rr);
                }
            }
        } catch (FileNotFoundException fne) {
            logger.log(Level.WARNING, "[monNetworking: File /proc/sys/net/core/wmem_max not found.]");
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "[monNetwork: Got I/O exception reading /proc/sys/net/core/wmem_max", ioe);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[monNetwork: Got general exception reading /proc/sys/net/core/wmem_max", t);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignore) {
                }
            }
            if (fr != null) {
                try {
                    fr.close();
                } catch (Throwable ignore) {
                }
            }
        }

        try {
            fr = new FileReader("/proc/sys/net/core/rmem_max");
            br = new BufferedReader(fr);
            final String values = br.readLine();
            if (values != null) {
                boolean found = false;
                Result rr = new Result(Node.getFarmName(), Node.getClusterName(), "rmem_max", TaskName, null);
                try {
                    rr.addSet("MaxSizeReceiveWindow", Double.parseDouble(values.trim()));
                    found = true;
                } catch (Exception e) {
                }
                if (found) {
                    rr.time = currentTime;
                    res.add(rr);
                }
            }
        } catch (FileNotFoundException fne) {
            logger.log(Level.WARNING, "[monNetworking: File /proc/sys/net/core/rmem_max not found.]");
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "[monNetwork: Got I/O exception reading /proc/sys/net/core/rmem_max", ioe);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[monNetwork: Got general exception reading /proc/sys/net/core/rmem_max", t);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignore) {
                }
            }
            if (fr != null) {
                try {
                    fr.close();
                } catch (Throwable ignore) {
                }
            }
        }

        try {
            fr = new FileReader("/proc/sys/net/ipv4/tcp_timestamps");
            br = new BufferedReader(fr);
            final String values = br.readLine();
            if (values != null) {
                boolean found = false;
                Result rr = new Result(Node.getFarmName(), Node.getClusterName(), "tcp_timestamps", TaskName, null);
                try {
                    rr.addSet("ActivateTimestamps", Double.parseDouble(values.trim()));
                    found = true;
                } catch (Exception e) {
                }
                if (found) {
                    rr.time = currentTime;
                    res.add(rr);
                }
            }
        } catch (FileNotFoundException fne) {
            logger.log(Level.WARNING, "[monNetworking: File /proc/sys/net/ipv4/tcp_timestamps not found.]");
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "[monNetwork: Got I/O exception reading /proc/sys/net/ipv4/tcp_timestamps", ioe);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[monNetwork: Got general exception reading /proc/sys/net/ipv4/tcp_timestamps", t);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignore) {
                }
            }
            if (fr != null) {
                try {
                    fr.close();
                } catch (Throwable ignore) {
                }
            }
        }

        try {
            fr = new FileReader("/proc/sys/net/ipv4/tcp_window_scaling");
            br = new BufferedReader(fr);
            final String values = br.readLine();
            if (values != null) {
                boolean found = false;
                Result rr = new Result(Node.getFarmName(), Node.getClusterName(), "tcp_window_scaling", TaskName, null);
                try {
                    rr.addSet("ActivateWindowScaling", Double.parseDouble(values.trim()));
                    found = true;
                } catch (Exception e) {
                }
                if (found) {
                    rr.time = currentTime;
                    res.add(rr);
                }
            }
        } catch (FileNotFoundException fne) {
            logger.log(Level.WARNING, "[monNetworking: File /proc/sys/net/ipv4/tcp_window_scaling not found.]");
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "[monNetwork: Got I/O exception reading /proc/sys/net/ipv4/tcp_window_scaling",
                    ioe);
        } catch (Throwable t) {
            logger.log(Level.WARNING,
                    "[monNetwork: Got general exception reading /proc/sys/net/ipv4/tcp_window_scaling", t);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignore) {
                }
            }
            if (fr != null) {
                try {
                    fr.close();
                } catch (Throwable ignore) {
                }
            }
        }

    }

    final Hashtable results = new Hashtable();

    /** Method used for parsing the values for IP */
    private final void parseIPTable(final String headerLine, final String valuesLine, final Vector res) {

        final StringTokenizer tok = new StringTokenizer(headerLine);
        final StringTokenizer valTok = new StringTokenizer(valuesLine);
        results.clear();
        String el;
        try {
            while ((el = tok.nextToken(" \t\n")) != null) {
                String valEl = valTok.nextToken(" \t\n");
                if (valEl == null) {
                    break;
                }
                if (el.equals("InReceives")) { // in received packets
                    results.put("InReceives", Double.valueOf(getInstantValue("InReceives", currentTime, valEl)));
                    continue;
                }
                if (el.equals("InHdrErrors")) { // in packets with header errors
                    results.put("InHdrErrors", Double.valueOf(getInstantValue("InHdrErrors", currentTime, valEl)));
                    continue;
                }
                if (el.equals("InAddrErrors")) { // in packets with incorrect address field
                    results.put("InAddrErrors", Double.valueOf(getInstantValue("InAddrErrors", currentTime, valEl)));
                    continue;
                }
                if (el.equals("ForwDatagrams")) { // number of forwarded datagrams
                    results.put("ForwDatagrams", Double.valueOf(getInstantValue("ForwDatagrams", currentTime, valEl)));
                    continue;
                }
                if (el.equals("InUnknownProtos")) { // number of packets with uknown protocol
                    results.put("InUnknownProtos",
                            Double.valueOf(getInstantValue("InUnknownProtos", currentTime, valEl)));
                    continue;
                }
                if (el.equals("InDiscards")) { // number of discarded packets
                    results.put("InDiscards", Double.valueOf(getInstantValue("InDiscards", currentTime, valEl)));
                    continue;
                }
                if (el.equals("InDelivers")) { // number of delivered packets
                    results.put("InDelivers", Double.valueOf(getInstantValue("InDelivers", currentTime, valEl)));
                    continue;
                }
                if (el.equals("OutRequests")) { // number of requests sent out
                    results.put("OutRequests", Double.valueOf(getInstantValue("OutRequests", currentTime, valEl)));
                    continue;
                }
                if (el.equals("OutDiscards")) { // number of outgoing dropped packets
                    results.put("OutDiscards", Double.valueOf(getInstantValue("OutDiscards", currentTime, valEl)));
                    continue;
                }
                if (el.equals("OutNoRoutes")) { // number of dropped because of no route  found
                    results.put("OutNoRoutes", Double.valueOf(getInstantValue("OutNoRoutes", currentTime, valEl)));
                    continue;
                }
                if (el.equals("ReasmTimeout")) { // number of fragments dropped after timeout
                    results.put("ReasmTimeout", Double.valueOf(getInstantValue("ReasmTimeout", currentTime, valEl)));
                    continue;
                }
                if (el.equals("ReasmReqds")) { // reassemblies required
                    results.put("ReasmReqds", Double.valueOf(getInstantValue("ReasmReqds", currentTime, valEl)));
                    continue;
                }
                if (el.equals("ReasmOKs")) { // reassembled ok
                    results.put("ReasmOKs", Double.valueOf(getInstantValue("ReasmOKs", currentTime, valEl)));
                    continue;
                }
                if (el.equals("ReasmFails")) { // failed reassembled
                    results.put("ReasmFails", Double.valueOf(getInstantValue("ReasmFails", currentTime, valEl)));
                    continue;
                }
                if (el.equals("FragOKs")) { // fragments received ok
                    results.put("FragOKs", Double.valueOf(getInstantValue("FragOKs", currentTime, valEl)));
                    continue;
                }
                if (el.equals("FragFails")) { // fragments failed
                    results.put("FragFails", Double.valueOf(getInstantValue("FragFails", currentTime, valEl)));
                    continue;
                }
                if (el.equals("FragCreates")) { // created fragments
                    results.put("FragCreates", Double.valueOf(getInstantValue("FragCreates", currentTime, valEl)));
                }
            }
        } catch (NoSuchElementException nse) { // done parsing
        }

        if (results.size() == 0) {
            return;
        }
        Result rr = new Result(Node.getFarmName(), Node.getClusterName(), "IP", TaskName, null);
        for (Enumeration en = results.keys(); en.hasMoreElements();) {
            final String r = (String) en.nextElement();
            rr.addSet(r, ((Double) results.get(r)).doubleValue());
        }
        rr.time = currentTime;
        res.add(rr);
    }

    /** Method used for parsing the statictical values for TCP stack */
    private final void parseTCPTable(final String headerLine, final String valuesLine, final Vector res) {
        final StringTokenizer tok = new StringTokenizer(headerLine);
        final StringTokenizer valTok = new StringTokenizer(valuesLine);
        String el;
        results.clear();
        try {
            while ((el = tok.nextToken(" \t\n")) != null) {
                String valEl = valTok.nextToken(" \t\n");
                if (valEl == null) {
                    break;
                }
                if (el.equals("AttemptFails")) { // failed connection attempts
                    results.put("AttemptFails", Double.valueOf(getInstantValue("AttemptFails", currentTime, valEl)));
                    continue;
                }
                if (el.equals("EstabResets")) { // connection resets received
                    results.put("EstabResets", Double.valueOf(getInstantValue("EstabResets", currentTime, valEl)));
                    continue;
                }
                if (el.equals("InSegs")) { // received segments
                    results.put("InSegs", Double.valueOf(getInstantValue("InSegs", currentTime, valEl)));
                    continue;
                }
                if (el.equals("OutSegs")) { // segments sent out
                    results.put("OutSegs", Double.valueOf(getInstantValue("OutSegs", currentTime, valEl)));
                    continue;
                }
                if (el.equals("RetransSegs")) { // retransmitted segments
                    results.put("RetransSegs", Double.valueOf(getInstantValue("RetransSegs", currentTime, valEl)));
                    continue;
                }
                if (el.equals("InErrs")) { // bad segments receied
                    results.put("InErrs", Double.valueOf(getInstantValue("InErrs", currentTime, valEl)));
                    continue;
                }
                if (el.equals("OutRsts")) { // resets sent
                    results.put("OutRsts", Double.valueOf(getInstantValue("OutRsts", currentTime, valEl)));
                }
            }
        } catch (NoSuchElementException nse) { // done parsing
        }
        if (results.size() == 0) {
            return;
        }
        Result rr = new Result(Node.getFarmName(), Node.getClusterName(), "TCP", TaskName, null);
        for (Enumeration en = results.keys(); en.hasMoreElements();) {
            final String r = (String) en.nextElement();
            rr.addSet(r, ((Double) results.get(r)).doubleValue());
        }
        rr.time = currentTime;
        res.add(rr);
    }

    /** Method used for parsing the statictical values for UDP stack */
    private final void parseUDPTable(final String headerLine, final String valuesLine, final Vector res) {
        final StringTokenizer tok = new StringTokenizer(headerLine);
        final StringTokenizer valTok = new StringTokenizer(valuesLine);
        String el;
        results.clear();
        try {
            while ((el = tok.nextToken(" \t\n")) != null) {
                String valEl = valTok.nextToken(" \t\n");
                if (valEl == null) {
                    break;
                }
                if (el.equals("InDatagrams")) { // datagrams received
                    results.put("InDatagrams", Double.valueOf(getInstantValue("InDatagrams", currentTime, valEl)));
                    continue; // advance to the next token
                }
                if (el.equals("NoPorts")) { // datagrams with no correct ports
                    results.put("NoPorts", Double.valueOf(getInstantValue("NoPorts", currentTime, valEl)));
                    continue;
                }
                if (el.equals("InErrors")) { // error datagrams
                    results.put("InErrors", Double.valueOf(getInstantValue("InErrors", currentTime, valEl)));
                    continue;
                }
                if (el.equals("OutDatagrams")) { // sent datagrams
                    results.put("OutDatagrams", Double.valueOf(getInstantValue("OutDatagrams", currentTime, valEl)));
                }
            }
        } catch (NoSuchElementException nse) { // done parsing
        }
        if (results.size() == 0) {
            return;
        }
        Result rr = new Result(Node.getFarmName(), Node.getClusterName(), "UDP", TaskName, null);
        for (Enumeration en = results.keys(); en.hasMoreElements();) {
            final String r = (String) en.nextElement();
            rr.addSet(r, ((Double) results.get(r)).doubleValue());
        }
        rr.time = currentTime;
        res.add(rr);
    }

    /** Method used for parsing the extended statistical values for TCP stack */
    private final void parseExtendedTCPTable(final String headerLine, final String valuesLine, final Vector res) {
        final StringTokenizer tok = new StringTokenizer(headerLine);
        final StringTokenizer valTok = new StringTokenizer(valuesLine);
        String el;
        results.clear();
        try {
            while ((el = tok.nextToken(" \t\n")) != null) {
                String valEl = valTok.nextToken(" \t\n");
                if (valEl == null) {
                    break;
                }
                if (el.equals("SyncookiesSent")) { // syncookies sent
                    results.put("SyncookiesSent", Double.valueOf(getInstantValue("SyncookiesSent", currentTime, valEl)));
                    continue; // advance to the next token
                }
                if (el.equals("SyncookiesRecv")) { // syncookies received
                    results.put("SyncookiesRecv", Double.valueOf(getInstantValue("SyncookiesRecv", currentTime, valEl)));
                    continue;
                }
                if (el.equals("SyncookiesFailed")) { // syncookies failed
                    results.put("SyncookiesFailed",
                            Double.valueOf(getInstantValue("SyncookiesFailed", currentTime, valEl)));
                    continue;
                }
                if (el.equals("EmbryonicRsts")) { // embryonic resets
                    results.put("EmbryonicRsts", Double.valueOf(getInstantValue("EmbryonicRsts", currentTime, valEl)));
                    continue;
                }
                if (el.equals("PruneCalled")) { // packets prunned because of buffer overflow
                    results.put("PruneCalled", Double.valueOf(getInstantValue("PruneCalled", currentTime, valEl)));
                    continue;
                }
                if (el.equals("RcvPruned")) { // packets prunned from received queue
                    results.put("RcvPruned", Double.valueOf(getInstantValue("RcvPruned", currentTime, valEl)));
                    continue;
                }
                if (el.equals("OfoPruned")) { // packets prunned from out-of-order queue - overfow
                    results.put("OfoPruned", Double.valueOf(getInstantValue("OfoPruned", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TW")) {
                    results.put("TW", Double.valueOf(getInstantValue("TW", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TWRecycled")) {
                    results.put("TWRecycled", Double.valueOf(getInstantValue("TWRecycled", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TWKilled")) {
                    results.put("TWKilled", Double.valueOf(getInstantValue("TWKilled", currentTime, valEl)));
                    continue;
                }
                if (el.equals("PAWSPassive")) {
                    results.put("PAWSPassive", Double.valueOf(getInstantValue("PAWSPassive", currentTime, valEl)));
                    continue;
                }
                if (el.equals("PAWSActive")) {
                    results.put("PAWSActive", Double.valueOf(getInstantValue("PAWSActive", currentTime, valEl)));
                    continue;
                }
                if (el.equals("PAWSEstab")) {
                    results.put("PAWSEstab", Double.valueOf(getInstantValue("PAWSEstab", currentTime, valEl)));
                    continue;
                }
                if (el.equals("DelayedACKs")) {
                    results.put("DelayedACKs", Double.valueOf(getInstantValue("DelayedACKs", currentTime, valEl)));
                    continue;
                }
                if (el.equals("DelayedACKLocked")) {
                    results.put("DelayedACKLocked",
                            Double.valueOf(getInstantValue("DelayedACKLocked", currentTime, valEl)));
                    continue;
                }
                if (el.equals("DelayedACKLost")) {
                    results.put("DelayedACKLost", Double.valueOf(getInstantValue("DelayedACKLost", currentTime, valEl)));
                    continue;
                }
                if (el.equals("ListenOverflows")) {
                    results.put("ListenOverflows",
                            Double.valueOf(getInstantValue("ListenOverflows", currentTime, valEl)));
                    continue;
                }
                if (el.equals("ListenDrops")) {
                    results.put("ListenDrops", Double.valueOf(getInstantValue("ListenDrops", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPPrequeued")) {
                    results.put("TCPPrequeued", Double.valueOf(getInstantValue("TCPPrequeued", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPDirectCopyFromBacklog")) {
                    results.put("TCPDirectCopyFromBacklog",
                            Double.valueOf(getInstantValue("TCPDirectCopyFromBacklog", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPDirectCopyFromPrequeue")) {
                    results.put("TCPDirectCopyFromPrequeue",
                            Double.valueOf(getInstantValue("TCPDirectCopyFromPrequeue", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPPrequeueDropped")) {
                    results.put("TCPPrequeueDropped",
                            Double.valueOf(getInstantValue("TCPPrequeueDropped", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPHPHits")) {
                    results.put("TCPHPHits", Double.valueOf(getInstantValue("TCPHPHits", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPHPHitsToUser")) {
                    results.put("TCPHPHitsToUser",
                            Double.valueOf(getInstantValue("TCPHPHitsToUser", currentTime, valEl)));
                    continue;
                }
                if (el.equals("SockMallocOOM")) {
                    results.put("SockMallocOOM", Double.valueOf(getInstantValue("SockMallocOOM", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPPureAcks")) {
                    results.put("TCPPureAcks", Double.valueOf(getInstantValue("TCPPureAcks", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPHPAcks")) {
                    results.put("TCPHPAcks", Double.valueOf(getInstantValue("TCPHPAcks", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPRenoRecovery")) {
                    results.put("TCPRenoRecovery",
                            Double.valueOf(getInstantValue("TCPRenoRecovery", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPSackRecovery")) {
                    results.put("TCPSackRecovery",
                            Double.valueOf(getInstantValue("TCPSackRecovery", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPSACKReneging")) {
                    results.put("TCPSACKReneging",
                            Double.valueOf(getInstantValue("TCPSACKReneging", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPFACKReorder")) {
                    results.put("TCPFACKReorder", Double.valueOf(getInstantValue("TCPFACKReorder", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPSACKReorder")) {
                    results.put("TCPSACKReorder", Double.valueOf(getInstantValue("TCPSACKReorder", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPRenoReorder")) {
                    results.put("TCPRenoReorder", Double.valueOf(getInstantValue("TCPRenoReorder", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPTSReorder")) {
                    results.put("TCPTSReorder", Double.valueOf(getInstantValue("TCPTSReorder", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPFullUndo")) {
                    results.put("TCPFullUndo", Double.valueOf(getInstantValue("TCPFullUndo", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPPartialUndo")) {
                    results.put("TCPPartialUndo", Double.valueOf(getInstantValue("TCPPartialUndo", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPDSACKUndo")) {
                    results.put("TCPDSACKUndo", Double.valueOf(getInstantValue("TCPDSACKUndo", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPLossUndo")) {
                    results.put("TCPLossUndo", Double.valueOf(getInstantValue("TCPLossUndo", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPLoss")) {
                    results.put("TCPLoss", Double.valueOf(getInstantValue("TCPLoss", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPLostRetransmit")) {
                    results.put("TCPLostRetransmit",
                            Double.valueOf(getInstantValue("TCPLostRetransmit", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPRenoFailures")) {
                    results.put("TCPRenoFailures",
                            Double.valueOf(getInstantValue("TCPRenoFailures", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPSackFailures")) {
                    results.put("TCPSackFailures",
                            Double.valueOf(getInstantValue("TCPSackFailures", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPLossFailures")) {
                    results.put("TCPLossFailures",
                            Double.valueOf(getInstantValue("TCPLossFailures", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPFastRetrans")) {
                    results.put("TCPFastRetrans", Double.valueOf(getInstantValue("TCPFastRetrans", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPForwardRetrans")) {
                    results.put("TCPForwardRetrans",
                            Double.valueOf(getInstantValue("TCPForwardRetrans", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPSlowStartRetrans")) {
                    results.put("TCPSlowStartRetrans",
                            Double.valueOf(getInstantValue("TCPSlowStartRetrans", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPTimeouts")) {
                    results.put("TCPTimeouts", Double.valueOf(getInstantValue("TCPTimeouts", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPRenoRecoveryFail")) {
                    results.put("TCPRenoRecoveryFail",
                            Double.valueOf(getInstantValue("TCPRenoRecoveryFail", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPSackRecoveryFail")) {
                    results.put("TCPSackRecoveryFail",
                            Double.valueOf(getInstantValue("TCPSackRecoveryFail", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPSchedulerFailed")) {
                    results.put("TCPSchedulerFailed",
                            Double.valueOf(getInstantValue("TCPSchedulerFailed", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPRcvCollapsed")) {
                    results.put("TCPRcvCollapsed",
                            Double.valueOf(getInstantValue("TCPRcvCollapsed", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPDSACKOldSent")) {
                    results.put("TCPDSACKOldSent",
                            Double.valueOf(getInstantValue("TCPDSACKOldSent", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPDSACKOfoSent")) {
                    results.put("TCPDSACKOfoSent",
                            Double.valueOf(getInstantValue("TCPDSACKOfoSent", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPDSACKRecv")) {
                    results.put("TCPDSACKRecv", Double.valueOf(getInstantValue("TCPDSACKRecv", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPDSACKOfoRecv")) {
                    results.put("TCPDSACKOfoRecv",
                            Double.valueOf(getInstantValue("TCPDSACKOfoRecv", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPAbortOnSyn")) {
                    results.put("TCPAbortOnSyn", Double.valueOf(getInstantValue("TCPAbortOnSyn", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPAbortOnData")) {
                    results.put("TCPAbortOnData", Double.valueOf(getInstantValue("TCPAbortOnData", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPAbortOnClose")) {
                    results.put("TCPAbortOnClose",
                            Double.valueOf(getInstantValue("TCPAbortOnClose", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPAbortOnMemory")) {
                    results.put("TCPAbortOnMemory",
                            Double.valueOf(getInstantValue("TCPAbortOnMemory", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPAbortOnTimeout")) {
                    results.put("TCPAbortOnTimeout",
                            Double.valueOf(getInstantValue("TCPAbortOnTimeout", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPAbortOnLinger")) {
                    results.put("TCPAbortOnLinger",
                            Double.valueOf(getInstantValue("TCPAbortOnLinger", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPAbortFailed")) {
                    results.put("TCPAbortFailed", Double.valueOf(getInstantValue("TCPAbortFailed", currentTime, valEl)));
                    continue;
                }
                if (el.equals("TCPMemoryPressures")) {
                    results.put("TCPMemoryPressures",
                            Double.valueOf(getInstantValue("TCPMemoryPressures", currentTime, valEl)));
                    continue;
                }
            }
        } catch (NoSuchElementException nse) { // done parsing
        }
        if (results.size() == 0) {
            return;
        }
        Result rr = new Result(Node.getFarmName(), Node.getClusterName(), "TCPExt", TaskName, null);
        for (Enumeration en = results.keys(); en.hasMoreElements();) {
            final String r = (String) en.nextElement();
            rr.addSet(r, ((Double) results.get(r)).doubleValue());
        }
        rr.time = currentTime;
        res.add(rr);
    }

    final static NumberFormat nf = NumberFormat.getInstance();

    static {
        nf.setMaximumFractionDigits(4);
        nf.setMinimumFractionDigits(4);
    }

    private final String prepareString(String str) {

        // first try to make it double
        try {
            double d = Double.parseDouble(str);
            if (!Double.isInfinite(d) && !Double.isNaN(d)) {
                String n = nf.format(d);
                n = n.replaceAll(",", "");
                return n;
            }
        } catch (Throwable t) {
        }

        if (str.indexOf(".") < 0) {
            return str + ".0000";
        }
        int nr = str.lastIndexOf('.') + 1;
        nr = str.length() - nr;
        for (int i = nr; i < 4; i++) {
            str += "0";
        }
        return str;
    }

    public String addWithOverflowCheck(String newVal, String oldVal) throws NumberFormatException {

        if (newVal == null) {
            return oldVal;
        }
        if (oldVal == null) {
            return newVal;
        }

        if (is64BitArch) {
            String str = prepareString(newVal);
            BigDecimal newv = null;
            try {
                newv = new BigDecimal(str);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[monNetworking: Got exception for " + str + "]", t);
            }
            str = prepareString(oldVal);
            BigDecimal oldv = null;
            try {
                oldv = new BigDecimal(str);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[monNetworking: Got exception for " + str + "]", t);
            }
            return newv.add(oldv).toString();
        }
        //		otherwise we still assume 32 bits arch
        double toCompare = 1L << 32;
        double newv = Double.parseDouble(newVal);
        double oldv = Double.parseDouble(oldVal);
        if ((newv >= toCompare) || (oldv >= toCompare)) {
            is64BitArch = true;
            return addWithOverflowCheck(newVal, oldVal);
        }
        //		so it's still 32 bits arch
        return "" + (newv + oldv);
    }

    public String divideWithOverflowCheck(String newVal, String oldVal) throws NumberFormatException {

        if (is64BitArch) {
            String str = prepareString(newVal);
            BigDecimal newv = null;
            try {
                newv = new BigDecimal(str);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[monNetworking: Got exception for " + str + "]", t);
            }
            str = prepareString(oldVal);
            BigDecimal oldv = null;
            try {
                oldv = new BigDecimal(str);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[monNetworking: Got exception for " + str + "]", t);
            }
            return newv.divide(oldv, BigDecimal.ROUND_FLOOR).toString();
        }
        //		otherwise we still assume 32 bits arch
        double toCompare = 1L << 32;
        double newv = Double.parseDouble(newVal);
        double oldv = Double.parseDouble(oldVal);
        if ((newv >= toCompare) || (oldv >= toCompare)) {
            is64BitArch = true;
            return divideWithOverflowCheck(newVal, oldVal);
        }
        //		so it's still 32 bits arch
        return "" + (newv / oldv);
    }

    public String mulWithOverflowCheck(String newVal, String oldVal) throws NumberFormatException {
        if (is64BitArch) {
            String str = prepareString(newVal);
            BigDecimal newv = null;
            try {
                newv = new BigDecimal(str);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[monNetworking: Got exception for " + str + "]", t);
            }
            str = prepareString(oldVal);
            BigDecimal oldv = null;
            try {
                oldv = new BigDecimal(str);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[monNetworking: Got exception for " + str + "]", t);
            }
            return newv.multiply(oldv).toString();
        }
        //		otherwise we still assume 32 bits arch
        double toCompare = 1L << 32;
        double newv = Double.parseDouble(newVal);
        double oldv = Double.parseDouble(oldVal);
        if ((newv >= toCompare) || (oldv >= toCompare)) {
            is64BitArch = true;
            return mulWithOverflowCheck(newVal, oldVal);
        }
        //		so it's still 32 bits arch
        return "" + (newv * oldv);
    }

    public String diffWithOverflowCheck(String newVal, String oldVal) throws NumberFormatException {
        if (is64BitArch) {
            String str = prepareString(newVal);
            BigDecimal newv = null;
            try {
                newv = new BigDecimal(str);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[monNetworking: Got exception for " + str + "]", t);
            }
            str = prepareString(oldVal);
            BigDecimal oldv = null;
            try {
                oldv = new BigDecimal(str);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[monNetworking: Got exception for " + str + "]", t);
            }
            if (newv.compareTo(oldv) >= 0) {
                return newv.subtract(oldv).toString();
            }
            BigInteger overflow = new BigInteger("1").shiftLeft(64);
            BigDecimal d = new BigDecimal(overflow.toString());
            return newv.add(d).subtract(oldv).toString();
        }
        //		otherwise we still assume 32 bits arch
        double toCompare = 1L << 32;
        double newv = Double.parseDouble(newVal);
        double oldv = Double.parseDouble(oldVal);
        if ((newv >= toCompare) || (oldv >= toCompare)) {
            is64BitArch = true;
            return diffWithOverflowCheck(newVal, oldVal);
        }
        //		so it's still 32 bits arch
        if (newv >= oldv) {
            return "" + (newv - oldv);
        }
        long vmax = 1L << 32; // 32 bits
        return "" + ((newv - oldv) + vmax);
    }

    // main for debugging purposes only, use it on your own will
    static public void main(String[] args) {

        String host = "localhost";
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }
        monNetworking net = new monNetworking();
        MonModuleInfo info = net.init(new MNode(host, ad, null, null), "useIP=false;useUDP=false;parseIfs=true");

        try {
            for (;;) {
                Object bb = net.doProcess();

                if (bb instanceof Vector) {
                    Vector v = (Vector) bb;
                    if (v != null) {
                        System.out.println(" Received a Vector having " + v.size() + " results");
                        for (int i = 0; i < v.size(); i++) {
                            System.out.println(v.elementAt(i));
                        }

                    }
                }
                try {
                    Thread.sleep(5 * 1000);
                } catch (Exception e1) {
                }
            }
        } catch (Exception e) {
            System.out.println(" failed to process ");
        }
    }

    public static class InterfaceStatistics {

        /** The name of the interface */
        protected final String name;

        /** For IPv4 */

        /** The IPv4 network address in use */
        protected String ipv4;

        /** The IPv4 broadcast domain */
        protected String bcastipv4;

        /** The IPV4 mask */
        protected String maskipv4;

        /** MTU */
        protected int mtu = 1500;

        /** For RX */

        protected double rxPackets = 0D;
        protected double rxErrors = 0D;
        protected double rxDropped = 0D;
        protected double rxOverruns = 0D;
        protected double rxFrame = 0D;
        protected double rx = 0D;

        /** For TX */

        protected double txPackets = 0D;
        protected double txErrors = 0D;
        protected double txDropped = 0D;
        protected double txOverruns = 0D;
        protected double txCarrier = 0D;
        protected double tx = 0D;

        /** General statistics */
        protected double collisions = 0D;
        protected boolean canCompress = false;
        protected double compressed = 0D;
        protected int txqueuelen = 0;

        /** The constructor - only the name is final */
        public InterfaceStatistics(final String name) {
            super();
            this.name = name;
        }

        /** Method used to retrieve the name of the interface */
        public final String getName() {
            return name;
        }

        /** Setter for the network address IPv4 */
        public final void setIPv4(final String address) {
            this.ipv4 = address;
        }

        /** Getter for the network address IPv4 */
        public final String getIPv4() {
            return ipv4;
        }

        /** Setter for the bcast address IPv4 */
        public final void setBcastv4(final String bcast) {
            this.bcastipv4 = bcast;
        }

        /** Getter for the bcast address IPv4 */
        public final String getBcastv4() {
            return bcastipv4;
        }

        /** Setter for the network mask IPv4 */
        public final void setMaskv4(final String mask) {
            this.maskipv4 = mask;
        }

        /** Getter for the network mask IPv4 */
        public final String getMaskv4() {
            return maskipv4;
        }

        /** Setter for mtu */
        public final void setMTU(final int mtu) {
            this.mtu = mtu;
        }

        /** Getter for mtu */
        public final int getMTU() {
            return mtu;
        }

        public final void setRX(final double rx) {
            this.rx = rx;
        }

        public final double getRX() {
            return rx;
        }

        public final void setRXPackets(final double packets) {
            this.rxPackets = packets;
        }

        public final double getRXPackets() {
            return rxPackets;
        }

        public final void setRXErrors(final double errors) {
            this.rxErrors = errors;
        }

        public final double getRXErrors() {
            return rxErrors;
        }

        public final void setRXDropped(final double dropped) {
            this.rxDropped = dropped;
        }

        public final double getRXDropped() {
            return rxDropped;
        }

        public final void setRXOverruns(final double overruns) {
            this.rxOverruns = overruns;
        }

        public final double getRXOverruns() {
            return rxOverruns;
        }

        public final void setRXFrame(final double frame) {
            this.rxFrame = frame;
        }

        public final double getRXFrame() {
            return rxFrame;
        }

        public final void setTX(final double tx) {
            this.tx = tx;
        }

        public final double getTX() {
            return tx;
        }

        public final void setTXPackets(final double packets) {
            this.txPackets = packets;
        }

        public final double getTXPackets() {
            return txPackets;
        }

        public final void setTXErrors(final double errors) {
            this.txErrors = errors;
        }

        public final double getTXErrors() {
            return txErrors;
        }

        public final void setTXDropped(final double dropped) {
            this.txDropped = dropped;
        }

        public final double getTXDropped() {
            return txDropped;
        }

        public final void setTXOverruns(final double overruns) {
            this.txOverruns = overruns;
        }

        public final double getTXOverruns() {
            return txOverruns;
        }

        public final void setTXCarrier(final double carrier) {
            this.txCarrier = carrier;
        }

        public final double getTXCarrier() {
            return txCarrier;
        }

        public final void setCompressed(final double compressed) {
            this.compressed = compressed;
        }

        public final double getCompressed() {
            return compressed;
        }

        public final void setCanCompress(boolean canCompress) {
            this.canCompress = canCompress;
        }

        public final boolean getCanCompress() {
            return canCompress;
        }

        public final void setCollisions(final double collisions) {
            this.collisions = collisions;
        }

        public final double getCollisions() {
            return collisions;
        }

        public final void setTXQueueLen(final int txqueuelen) {
            this.txqueuelen = txqueuelen;
        }

        public final int getTXQueueLen() {
            return txqueuelen;
        }

        /** Utility method for printing out the properties */
        @Override
        public String toString() {

            StringBuilder buf = new StringBuilder();
            buf.append("IFNAME [name=").append(name);
            buf.append(",ipv4=").append(ipv4);
            buf.append(",bcast4=").append(bcastipv4);
            buf.append(",mask4=").append(maskipv4);
            buf.append(",mtu=").append(mtu);
            buf.append(",rx_packets=").append(rxPackets);
            buf.append(",tx_packets=").append(txPackets);
            buf.append("]");
            return buf.toString();
        }

    } // end of class InterfaceStatistics

    public static class InterfaceStatisticsStatic {

        /********* Types of interface encapsulations *****************/

        public static final byte TYPE_ETHERNET = 0;
        public static final byte TYPE_FIBER = 1;

        public static final byte PORT_TP = 1;
        public static final byte PORT_AUI = 2;
        public static final byte PORT_BNC = 4;
        public static final byte PORT_MII = 8;
        public static final byte PORT_FIBRE = 16;

        public static final byte SUPPORTED_10BaseT_Half = 1;
        public static final byte SUPPORTED_10BaseT_Full = 2;
        public static final byte SUPPORTED_100BaseT_Half = 4;
        public static final byte SUPPORTED_100BaseT_Full = 8;
        public static final byte SUPPORTED_1000BaseT_Half = 16;
        public static final byte SUPPORTED_1000BaseT_Full = 32;

        public static final byte LINK_HALF = 0;
        public static final byte LINK_DUPLEX = 1;

        /** The name of the interface */
        protected final String name;

        /** The encapsulation type */
        protected byte encap = 0;

        /** The hardware address of the interface */
        protected String hwAddr;

        protected byte supportedPorts = -1; // -1 means error
        protected byte supportedLinkModes = -1; // -1 means error
        protected boolean supportsAutoNegotiation = false;

        protected int maxSpeed = -1; // supported speed in Mb/s
        protected byte duplex = -1; // -1 means unknown
        protected byte port = -1; // -1 means unknown

        /** The constructor - only the name is final */
        public InterfaceStatisticsStatic(final String name) {
            super();
            this.name = name;
        }

        /** Method used to retrieve the name of the interface */
        public final String getName() {
            return name;
        }

        /** Setter for the type of encapsulation for the network interface */
        public final void setEncap(final byte type) {
            this.encap = type;
        }

        /** Getter for the type of encapsulation */
        public final byte getEncap() {
            return encap;
        }

        /** Getter for the type of encapsulation */
        public final String getEncapAsString() {
            switch (encap) {
            case TYPE_ETHERNET:
                return "Ethernet";
            case TYPE_FIBER:
                return "Fiber";
            }
            return "Unknown";
        }

        /** Setter for the hardware address */
        public final void setHwAddr(final String hwAddr) {
            this.hwAddr = hwAddr;
        }

        /** Getter for the hardware address */
        public final String getHwAddr() {
            return hwAddr;
        }

        /** Utility method for printing out the properties */
        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("IFNAME [name=").append(name);
            buf.append(",encap=").append(getEncapAsString());
            buf.append(",hwaddr=").append(hwAddr);
            buf.append(",supportedPort=").append(supportedPorts);
            buf.append(",supportedLinkModes=").append(supportedLinkModes);
            buf.append(",supportsAutoNeg=").append(supportsAutoNegotiation);
            buf.append(",maxSpeed=").append(maxSpeed);
            buf.append(",duplex=").append(duplex);
            buf.append(",port=").append(port);
            buf.append("]");
            return buf.toString();
        }

        public byte getSupportedPorts() {
            return supportedPorts;
        }

        private boolean flagSet(byte b, byte flag) {
            return ((b & flag) == flag);
        }

        public String[] getSupportedPortsAsString() {

            if (supportedPorts < 0) {
                return new String[] { "UNKNOWN" };
            }

            final LinkedList list = new LinkedList();
            if (flagSet(supportedPorts, PORT_TP)) {
                list.addLast("TP");
            }
            if (flagSet(supportedPorts, PORT_AUI)) {
                list.addLast("AUI");
            }
            if (flagSet(supportedPorts, PORT_BNC)) {
                list.addLast("BNC");
            }
            if (flagSet(supportedPorts, PORT_MII)) {
                list.addLast("MII");
            }
            if (flagSet(supportedPorts, PORT_FIBRE)) {
                list.addLast("FIBRE");
            }

            if (list.size() == 0) {
                return new String[] { "NONE" };
            }
            final String ret[] = new String[list.size()];
            int i = 0;
            for (Iterator it = list.iterator(); it.hasNext();) {
                ret[i] = (String) it.next();
                i++;
            }
            return ret;
        }

        public void setSupportedPorts(byte supportedPorts) {
            if (this.supportedPorts < 0) {
                this.supportedPorts = 0;
            }
            this.supportedPorts = (byte) (this.supportedPorts | supportedPorts);
        }

        public byte getSupportedLinkModes() {
            return supportedLinkModes;
        }

        public String[] getSupportedLinkModesAsString() {
            if (supportedLinkModes < 0) {
                return new String[] { "UNKNOWN" };
            }
            final LinkedList list = new LinkedList();
            if (flagSet(supportedLinkModes, SUPPORTED_10BaseT_Half)) {
                list.add("10BaseT_Half");
            }
            if (flagSet(supportedLinkModes, SUPPORTED_10BaseT_Full)) {
                list.add("10BaseT_Full");
            }
            if (flagSet(supportedLinkModes, SUPPORTED_100BaseT_Half)) {
                list.add("100BaseT_Half");
            }
            if (flagSet(supportedLinkModes, SUPPORTED_100BaseT_Full)) {
                list.add("100BaseT_Full");
            }
            if (flagSet(supportedLinkModes, SUPPORTED_1000BaseT_Half)) {
                list.add("1000BaseT_Half");
            }
            if (flagSet(supportedLinkModes, SUPPORTED_1000BaseT_Full)) {
                list.add("1000BaseT_Full");
            }
            if (list.size() == 0) {
                return new String[] { "NONE" };
            }
            final String ret[] = new String[list.size()];
            int i = 0;
            for (Iterator it = list.iterator(); it.hasNext();) {
                ret[i] = (String) it.next();
                i++;
            }
            return ret;
        }

        public void setSupportedLinkModes(byte supportedLinkModes) {
            if (this.supportedLinkModes < 0) {
                this.supportedLinkModes = 0;
            }
            this.supportedLinkModes = (byte) (this.supportedLinkModes | supportedLinkModes);
        }

        public boolean supportsAutoNegotiation() {
            return supportsAutoNegotiation;
        }

        public void setSupportsAutoNegotiation(boolean supportsAutoNegotiation) {
            this.supportsAutoNegotiation = supportsAutoNegotiation;
        }

        public int getMaxSpeed() {
            return maxSpeed;
        }

        public String getMaxSpeedAsString() {
            if (maxSpeed < 0) {
                return "UNKNOWN";
            }
            return maxSpeed + " Mb/s";
        }

        public void setMaxSpeed(int maxSpeed) {
            this.maxSpeed = maxSpeed;
        }

        public byte getDuplex() {
            return duplex;
        }

        public void setDuplex(byte duplex) {
            this.duplex = duplex;
        }

        public byte getPort() {
            return port;
        }

        public void setPort(byte port) {
            this.port = port;
        }

    } // end of class InterfaceStatisticsStatic

    /** This module relies heavily on Pattern matching (it's safer that way), but also Pattern is a memory
     * consumer, so please use this class in order to retrieve a given Pattern. The main purpose of this
     * is to instantiate a pattern once in memory.
     */
    public static class PatternUtil {

        /** The mapping keys vs Patterns */
        protected final HashMap patterns = new HashMap();

        /** We only want to instantiante this class once, if necessary */
        protected static PatternUtil _p;

        /**
         * Call this method in order to retrieve the Pattern associated with a given key.
         * @param key The key to identify the Pattern
         * @param pattern The pattern to use if instantiation required
         * @return The pattern
         */
        public static synchronized Pattern getPattern(final String key, final String pattern) {
            return getPattern(key, pattern, false);
        }

        /**
         * Call this method in order to retrieve the Pattern associated with a given key.
         * @param key The key to identify the Pattern
         * @param pattern The pattern to use if instantiation required
         * @param takeEOL Should EndOfLine character be taken into consideration ?
         * @return The pattern
         */
        public static synchronized Pattern getPattern(final String key, final String pattern, final boolean takeEOL) {

            if (key == null) {
                return null; // for null key return a null pattern
            }
            if (_p == null) {
                _p = new PatternUtil();
                _p.patterns.put("Unknown command", _p.getNoSuchCommand());
            }
            if (!_p.patterns.containsKey(key)) {
                final Pattern p = takeEOL ? Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                        : Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                _p.patterns.put(key, p);
                return p;
            }
            return (Pattern) _p.patterns.get(key);
        }

        /** A high used pattern is the one used for recognizing of no such file pattern */
        protected Pattern getNoSuchCommand() {
            return Pattern.compile("([No such file or directory|Operation not permitted|bad command line argument])+",
                    Pattern.CASE_INSENSITIVE);
        }

    } // end of class PatternUtil

    static class InterfaceHandler {
        /** The interfaces of the system toghether with the monitored properties */
        protected final HashMap ifs = new HashMap();

        protected final HashMap lastGoodStatic = new HashMap();

        protected final HashMap staticifs = new HashMap();

        /** Different patterns used for output parsing **/

        protected static final String ifNamePattern = "^(\\S+)\\s+";
        protected static final String hwAddrPattern = "HWaddr\\s+(\\S+)";
        protected static final String ipv4Pattern = "inet addr:(\\S+)";
        protected static final String mask4Pattern = "Mask:(\\S+)";
        protected static final String bcast4Pattern = "Bcast:(\\S+)";
        protected static final String mtuPattern = "MTU:(\\S+)";

        protected static final String rxPackets = "RX[\\s\\S]+packets:(\\S+)";
        protected static final String rxErrors = "RX[\\s\\S]+errors:(\\S+)";
        protected static final String rxDropped = "RX[\\s\\S]+dropped:(\\S+)";
        protected static final String rxOverruns = "RX[\\s\\S]+overruns:(\\S+)";
        protected static final String rxFrame = "RX[\\s\\S]+frame:(\\S+)";
        protected static final String rxBytes = "RX\\s+bytes:(\\S+)";

        protected static final String txPackets = "TX[\\s\\S]+packets:(\\S+)";
        protected static final String txErrors = "TX[\\s\\S]+errors:(\\S+)";
        protected static final String txDropped = "TX[\\s\\S]+dropped:(\\S+)";
        protected static final String txOverruns = "TX[\\s\\S]+overruns:(\\S+)";
        protected static final String txCarrier = "TX[\\s\\S]+carrier:(\\S+)";
        protected static final String txBytes = "TX\\s+bytes:(\\S+)";

        protected static final String collisions = "\\s+collisions:(\\S+)";
        protected static final String compressed = "\\s+compressed:(\\S+)";
        protected static final String txqueuelen = "\\s+txqueuelen:(\\S+)";

        protected static final String netDevPattern = "(\\S+):\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+";

        protected static final String supportedPortsPattern = "Supported ports:\\s*\\[(.*)\\]";
        protected static final String supportedLinkModes = "Supported link modes:\\s*(.*?)Supports";
        protected static final String supportsAutoNegotiation = "Supports auto-negotiation:\\s*(\\S+)";
        protected static final String speed = "Speed:\\s*(.*)";
        protected static final String duplex = "Duplex:\\s*(.*)";
        protected static final String port = "Port:\\s*(.*)";

        /** Utility object used for running different commands */
        protected final iCmdExec exec = iCmdExec.getInstance();;

        protected final Hashtable lastRXPackets = new Hashtable();
        protected final Hashtable lastRXErrors = new Hashtable();
        protected final Hashtable lastRXDropped = new Hashtable();
        protected final Hashtable lastRXOverruns = new Hashtable();
        protected final Hashtable lastRXFrame = new Hashtable();
        protected final Hashtable lastRXBytes = new Hashtable();

        protected final Hashtable lastTXPackets = new Hashtable();
        protected final Hashtable lastTXErrors = new Hashtable();
        protected final Hashtable lastTXDropped = new Hashtable();
        protected final Hashtable lastTXOverruns = new Hashtable();
        protected final Hashtable lastTXCarrier = new Hashtable();
        protected final Hashtable lastTXBytes = new Hashtable();

        protected final Hashtable lastCollisions = new Hashtable();
        protected final Hashtable lastCompressed = new Hashtable();

        protected long lastCall = 0;

        /** is this a 64 bits arch ? */
        private boolean is64BitArch = false;

        /** some temporary variables ... we only want them to be instantiated once because of the latency involved */
        final List ifArray = new ArrayList();
        final List toRemove = new ArrayList();

        /** 
         * Action method for this task... retrieves the network interfaces together with their properties ...
         */
        public final void check() {
            checkIfConfig();
            if (ifs.size() == 0) { // no interface yet? let's read /proc/net/dev also
                getFromProcDev();
            }
            if (ifs.size() == 0) {
                return; // so there really is no net device installed into the system
            }
            if (shouldRunEthTool()) {
                checkEthtool();
            }

            final LinkedList names = new LinkedList();
            for (Iterator it = lastGoodStatic.keySet().iterator(); it.hasNext();) {
                names.addLast(it.next());
            }
            for (Iterator it = names.iterator(); it.hasNext();) {
                String name = (String) it.next();
                if (!staticifs.containsKey(name)) {
                    lastGoodStatic.remove(name);
                }
            }
            names.clear();
            for (Iterator it = lastGoodStatic.keySet().iterator(); it.hasNext();) {
                names.addLast(it.next());
            }
            for (Iterator it = names.iterator(); it.hasNext();) {
                String name = (String) it.next();
                if (!lastGoodStatic.containsKey(name)) {
                    lastGoodStatic.put(name, staticifs.get(name));
                    continue;
                }
                final InterfaceStatisticsStatic olds = (InterfaceStatisticsStatic) lastGoodStatic.get(name);
                final InterfaceStatisticsStatic news = (InterfaceStatisticsStatic) staticifs.get(name);

                if (!compare(olds, news)) {
                    lastGoodStatic.put(name, news);
                } else {
                    staticifs.remove(name);
                }
            }
        }

        public final HashMap getIfStatistics() {
            return ifs;
        }

        public final HashMap getIfStatisticsStatic() {
            return staticifs;
        }

        /** The part used for mii-tool */
        private final void checkEthtool() {
            final String eth = getEthtoolPath();
            final String command = "ethtool ";
            for (Iterator it = staticifs.keySet().iterator(); it.hasNext();) {
                final String ifName = (String) it.next();
                final InterfaceStatisticsStatic stat = (InterfaceStatisticsStatic) staticifs.get(ifName);
                if ((ifName == null) || (ifName.length() == 0) || (stat == null)) {
                    continue;
                }
                final lia.Monitor.modules.monNetworking.iCmdExec.CommandResult cmdRes = exec.executeCommandReality(
                        command + ifName, null, eth);
                final String ret = cmdRes.getOutput();
                if (cmdRes.failed() || (ret == null) || (ret.length() == 0)
                        || PatternUtil.getPattern("Unknown command", null).matcher(ret).matches()) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINE, ret);
                    }
                    continue;
                }
                stat.setSupportedPorts((byte) 0);

                Pattern pattern = PatternUtil.getPattern("supportedPorts", supportedPortsPattern);
                Matcher matcher = pattern.matcher(ret);
                if (matcher.find()) {
                    final String mm = matcher.group(1);
                    if (mm.trim().length() != 0) {
                        final String sp[] = mm.trim().split(" ");
                        for (String element : sp) {
                            if (element.equals("TP")) {
                                stat.setSupportedPorts(InterfaceStatisticsStatic.PORT_TP);
                            }
                            if (element.equals("AUI")) {
                                stat.setSupportedPorts(InterfaceStatisticsStatic.PORT_AUI);
                            }
                            if (element.equals("BNC")) {
                                stat.setSupportedPorts(InterfaceStatisticsStatic.PORT_BNC);
                            }
                            if (element.equals("MII")) {
                                stat.setSupportedPorts(InterfaceStatisticsStatic.PORT_MII);
                            }
                            if (element.equals("FIBRE")) {
                                stat.setSupportedPorts(InterfaceStatisticsStatic.PORT_FIBRE);
                            }
                        }
                    }
                }
                pattern = PatternUtil.getPattern("supportedLinkModes", supportedLinkModes, true);
                matcher = pattern.matcher(ret);
                if (matcher.find()) {
                    final String mm = matcher.group(1);
                    if (mm.trim().length() != 0) {
                        final String sp[] = mm.trim().split("[ \n]+");
                        for (String element : sp) {
                            if (element.trim().length() == 0) {
                                continue;
                            }
                            final String ss = element.trim();
                            if (ss.equals("10baseT/Half")) {
                                stat.setSupportedLinkModes(InterfaceStatisticsStatic.SUPPORTED_10BaseT_Half);
                            }
                            if (ss.equals("10baseT/Full")) {
                                stat.setSupportedLinkModes(InterfaceStatisticsStatic.SUPPORTED_10BaseT_Full);
                            }
                            if (ss.equals("100baseT/Half")) {
                                stat.setSupportedLinkModes(InterfaceStatisticsStatic.SUPPORTED_100BaseT_Half);
                            }
                            if (ss.equals("100baseT/Full")) {
                                stat.setSupportedLinkModes(InterfaceStatisticsStatic.SUPPORTED_100BaseT_Full);
                            }
                            if (ss.equals("1000baseT/Half")) {
                                stat.setSupportedLinkModes(InterfaceStatisticsStatic.SUPPORTED_1000BaseT_Half);
                            }
                            if (ss.equals("1000baseT/Full")) {
                                stat.setSupportedLinkModes(InterfaceStatisticsStatic.SUPPORTED_1000BaseT_Full);
                            }
                        }
                    }
                }
                pattern = PatternUtil.getPattern("supportsAutoNegotiation", supportsAutoNegotiation);
                matcher = pattern.matcher(ret);
                if (matcher.find()) {
                    final String mm = matcher.group(1);
                    if (mm.trim().length() != 0) {
                        if (mm.trim().equals("Yes")) {
                            stat.setSupportsAutoNegotiation(true);
                        }
                    }
                }
                pattern = PatternUtil.getPattern("linkSpeed", speed);
                matcher = pattern.matcher(ret);
                if (matcher.find()) {
                    final String mm = matcher.group(1);
                    if ((mm != null) && (mm.trim().length() != 0)) {
                        final String ss = mm.trim();
                        if (ss.equals("10Mb/s")) {
                            stat.setMaxSpeed(10);
                        } else if (ss.equals("100Mb/s")) {
                            stat.setMaxSpeed(100);
                        } else if (ss.equals("1000Mb/s")) {
                            stat.setMaxSpeed(1000);
                        } else if (ss.equals("10000Mb/s")) {
                            stat.setMaxSpeed(10000);
                        } else if (ss.startsWith("Unknown")) {
                            final String sp = ss.substring(ss.indexOf('(') + 1, ss.lastIndexOf(')'));
                            try {
                                stat.setMaxSpeed(Integer.parseInt(sp));
                            } catch (Exception ex) {
                                logger.warning("Can not determine speed " + ss + " for " + ifName);
                            }
                        }
                    }
                }
                pattern = PatternUtil.getPattern("linkDuplex", duplex);
                matcher = pattern.matcher(ret);
                if (matcher.find()) {
                    final String mm = matcher.group(1);
                    if ((mm != null) && (mm.trim().length() != 0)) {
                        final String ss = mm.trim();
                        if (ss.compareToIgnoreCase("Half") == 0) {
                            stat.setDuplex(InterfaceStatisticsStatic.LINK_HALF);
                        } else if (ss.compareToIgnoreCase("Full") == 0) {
                            stat.setDuplex(InterfaceStatisticsStatic.LINK_DUPLEX);
                        }
                    }
                }
                pattern = PatternUtil.getPattern("linkPort", port);
                matcher = pattern.matcher(ret);
                if (matcher.find()) {
                    final String mm = matcher.group(1);
                    if ((mm != null) && (mm.trim().length() != 0)) {
                        final String ss = mm.trim();
                        if (ss.compareTo("Twisted Pair") == 0) {
                            stat.setPort(InterfaceStatisticsStatic.PORT_TP);
                        } else if (ss.compareToIgnoreCase("FIBRE") == 0) {
                            stat.setPort(InterfaceStatisticsStatic.PORT_FIBRE);
                        } else if (ss.compareToIgnoreCase("AUI") == 0) {
                            stat.setPort(InterfaceStatisticsStatic.PORT_AUI);
                        } else if (ss.compareToIgnoreCase("BNC") == 0) {
                            stat.setPort(InterfaceStatisticsStatic.PORT_BNC);
                        } else if (ss.compareToIgnoreCase("MII") == 0) {
                            stat.setPort(InterfaceStatisticsStatic.PORT_MII);
                        }
                    }
                }
            }
        }

        /**
         * Called in order to check if we should use ethtool utility
         * @return False if net.ethtool.use is set to false
         */
        private final boolean shouldRunEthTool() {
            try {
                return Boolean.valueOf(System.getProperty("net.ethtool.use", "true")).booleanValue();
            } catch (Exception e) {
                return true;
            }
        }

        private synchronized final String getEthtoolPath() {
            String path = System.getProperty("net.ethtool.path", "/bin,/sbin,/usr/bin,/usr/sbin");
            if ((path == null) || (path.length() == 0)) {
                logger.warning("[Net - ethtool can not be found in " + path + "]");
                return null;
            }
            return path.replace(',', ':').trim();
        }

        private final boolean compare(InterfaceStatisticsStatic olds, InterfaceStatisticsStatic news) {
            if (olds.getEncap() != news.getEncap()) {
                return false;
            }
            if ((olds.getHwAddr() == null) && (news.getHwAddr() != null)) {
                return false;
            }
            if ((olds.getHwAddr() != null) && (news.getHwAddr() == null)) {
                return false;
            }
            if ((olds.getHwAddr() != null) && (news.getHwAddr() != null)) {
                if (!olds.getHwAddr().equals(news.getHwAddr())) {
                    return false;
                }
            }
            if (olds.getSupportedPorts() != news.getSupportedPorts()) {
                return false;
            }
            if (olds.getSupportedLinkModes() != news.getSupportedLinkModes()) {
                return false;
            }
            if (olds.supportsAutoNegotiation() != news.supportsAutoNegotiation()) {
                return false;
            }
            if (olds.getMaxSpeed() != news.getMaxSpeed()) {
                return false;
            }
            if (olds.getDuplex() != news.getDuplex()) {
                return false;
            }
            if (olds.getPort() != news.getPort()) {
                return false;
            }
            return true;
        }

        /** The part used for ifconfig */
        private final void checkIfConfig() {
            final String ifc = getIfconfigPath();
            final String command = "ifconfig";
            lia.Monitor.modules.monNetworking.iCmdExec.CommandResult cmdRes = exec.executeCommandReality(command,
                    (String) null, ifc);
            final String ret = cmdRes.getOutput();
            if (cmdRes.failed() || (ret == null) || (ret.length() == 0)
                    || PatternUtil.getPattern("Unknown command", null).matcher(ret).matches()) {
                logger.info(ret);
                return;
            }

            double diff = 0D;
            long now = NTPDate.currentTimeMillis();
            if (lastCall != 0) {
                diff = (now - lastCall) / 1000D;
            }
            lastCall = now;

            // otherwise we have ifconfig and can also be used
            final String interfaces[] = ret.split("\n\n");
            ifArray.clear();
            for (int i = 0; i < interfaces.length; i++) {
                Pattern pattern = PatternUtil.getPattern("ifname", ifNamePattern);
                Matcher matcher = pattern.matcher(interfaces[i]);
                InterfaceStatistics ifProp = null;
                InterfaceStatisticsStatic ifPropStatic = null;
                String name = null;
                if (matcher.find()) {
                    name = matcher.group(1);
                    ifArray.add(name);
                    if (!ifs.containsKey(name)) {
                        ifProp = new InterfaceStatistics(name);
                        ifs.put(name, ifProp);
                    } else {
                        ifProp = (InterfaceStatistics) ifs.get(name);
                    }
                    if (!staticifs.containsKey(name)) {
                        ifPropStatic = new InterfaceStatisticsStatic(name);
                        staticifs.put(name, ifPropStatic);
                    } else {
                        ifPropStatic = (InterfaceStatisticsStatic) staticifs.get(name);
                    }
                    if (!checkLinkEncap(ifPropStatic, interfaces[i])) { // if not an interface to be interested in...
                        ifArray.remove(name); // to be removed
                        continue; // continue with the next interface
                    }
                } else {
                    continue; // continue with the next interface
                }
                pattern = PatternUtil.getPattern("hwaddr", hwAddrPattern);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    ifPropStatic.setHwAddr(matcher.group(1));
                } else {
                    ifPropStatic.setHwAddr("unknown");
                }
                pattern = PatternUtil.getPattern("ipv4", ipv4Pattern);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    ifProp.setIPv4(matcher.group(1));
                } else {
                    ifProp.setIPv4("unknown");
                }
                pattern = PatternUtil.getPattern("mask4", mask4Pattern);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    ifProp.setMaskv4(matcher.group(1));
                } else {
                    ifProp.setMaskv4("unknown");
                }
                pattern = PatternUtil.getPattern("bcast4", bcast4Pattern);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    ifProp.setBcastv4(matcher.group(1));
                } else {
                    ifProp.setBcastv4("unknown");
                }
                // TODO also add information regarding ipv6
                pattern = PatternUtil.getPattern("mtu", mtuPattern);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    try {
                        ifProp.setMTU(Integer.parseInt(matcher.group(1)));
                    } catch (Exception ex) {
                        ifProp.setMTU(-1);
                    }
                } else {
                    ifProp.setMTU(-1);
                }
                pattern = PatternUtil.getPattern("rxpackets", rxPackets);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    String newVal = matcher.group(1);
                    if (diff > 0) {
                        String res = diffWithOverflowCheck(newVal, (String) lastRXPackets.get(name));
                        try {
                            double difRes = Double.parseDouble(res);
                            ifProp.setRXPackets(difRes / diff);
                        } catch (Throwable t) {
                            ifProp.setRXPackets(-1D);
                        }
                    }
                    lastRXPackets.put(name, newVal);
                }
                pattern = PatternUtil.getPattern("rxerrors", rxErrors);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    String newVal = matcher.group(1);
                    if (diff > 0) {
                        String res = diffWithOverflowCheck(newVal, (String) lastRXErrors.get(name));
                        try {
                            double difRes = Double.parseDouble(res);
                            ifProp.setRXErrors(difRes / diff);
                        } catch (Throwable t) {
                            ifProp.setRXErrors(-1D);
                        }
                    }
                    lastRXErrors.put(name, newVal);
                }
                pattern = PatternUtil.getPattern("rxdropped", rxDropped);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    String newVal = matcher.group(1);
                    if (diff > 0) {
                        String res = diffWithOverflowCheck(newVal, (String) lastRXDropped.get(name));
                        try {
                            double difRes = Double.parseDouble(res);
                            ifProp.setRXDropped(difRes / diff);
                        } catch (Throwable t) {
                            ifProp.setRXDropped(-1D);
                        }
                    }
                    lastRXDropped.put(name, newVal);
                }
                pattern = PatternUtil.getPattern("rxoverruns", rxOverruns);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    String newVal = matcher.group(1);
                    if (diff > 0) {
                        String res = diffWithOverflowCheck(newVal, (String) lastRXOverruns.get(name));
                        try {
                            double difRes = Double.parseDouble(res);
                            ifProp.setRXOverruns(difRes / diff);
                        } catch (Throwable t) {
                            ifProp.setRXOverruns(-1D);
                        }
                    }
                    lastRXOverruns.put(name, newVal);
                }
                pattern = PatternUtil.getPattern("rxframe", rxFrame);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    String newVal = matcher.group(1);
                    if (diff > 0) {
                        String res = diffWithOverflowCheck(newVal, (String) lastRXFrame.get(name));
                        try {
                            double difRes = Double.parseDouble(res);
                            ifProp.setRXFrame(difRes / diff);
                        } catch (Throwable t) {
                            ifProp.setRXFrame(-1D);
                        }
                    }
                    lastRXFrame.put(name, newVal);
                }
                pattern = PatternUtil.getPattern("rxBytes", rxBytes);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    String newVal = matcher.group(1);
                    if (diff > 0) {
                        String res = diffWithOverflowCheck(newVal, (String) lastRXBytes.get(name));
                        try {
                            double difRes = Double.parseDouble(res);
                            ifProp.setRX((difRes * 8D) / diff);
                        } catch (Throwable t) {
                            ifProp.setRX(-1D);
                        }
                    }
                    lastRXBytes.put(name, newVal);
                }

                pattern = PatternUtil.getPattern("txpackets", txPackets);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    String newVal = matcher.group(1);
                    if (diff > 0) {
                        String res = diffWithOverflowCheck(newVal, (String) lastTXPackets.get(name));
                        try {
                            double difRes = Double.parseDouble(res);
                            ifProp.setTXPackets(difRes / diff);
                        } catch (Throwable t) {
                            ifProp.setTXPackets(-1D);
                        }
                    }
                    lastTXPackets.put(name, newVal);
                }
                pattern = PatternUtil.getPattern("txerrors", txErrors);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    String newVal = matcher.group(1);
                    if (diff > 0) {
                        String res = diffWithOverflowCheck(newVal, (String) lastTXErrors.get(name));
                        try {
                            double difRes = Double.parseDouble(res);
                            ifProp.setTXErrors(difRes / diff);
                        } catch (Throwable t) {
                            ifProp.setTXErrors(-1D);
                        }
                    }
                    lastTXErrors.put(name, newVal);
                }
                pattern = PatternUtil.getPattern("txdropped", txDropped);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    String newVal = matcher.group(1);
                    if (diff > 0) {
                        String res = diffWithOverflowCheck(newVal, (String) lastTXDropped.get(name));
                        try {
                            double difRes = Double.parseDouble(res);
                            ifProp.setTXDropped(difRes / diff);
                        } catch (Throwable t) {
                            ifProp.setTXDropped(-1D);
                        }
                    }
                    lastTXDropped.put(name, newVal);
                }
                pattern = PatternUtil.getPattern("txoverruns", txOverruns);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    String newVal = matcher.group(1);
                    if (diff > 0) {
                        String res = diffWithOverflowCheck(newVal, (String) lastTXOverruns.get(name));
                        try {
                            double difRes = Double.parseDouble(res);
                            ifProp.setTXOverruns(difRes / diff);
                        } catch (Throwable t) {
                            ifProp.setTXOverruns(-1D);
                        }
                    }
                    lastTXOverruns.put(name, newVal);
                }
                pattern = PatternUtil.getPattern("txcarrier", txCarrier);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    String newVal = matcher.group(1);
                    if (diff > 0) {
                        String res = diffWithOverflowCheck(newVal, (String) lastTXCarrier.get(name));
                        try {
                            double difRes = Double.parseDouble(res);
                            ifProp.setTXCarrier(difRes / diff);
                        } catch (Throwable t) {
                            ifProp.setTXCarrier(-1D);
                        }
                    }
                    lastTXCarrier.put(name, newVal);
                }
                pattern = PatternUtil.getPattern("txBytes", txBytes);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    String newVal = matcher.group(1);

                    if (diff > 0) {
                        String res = diffWithOverflowCheck(newVal, (String) lastTXBytes.get(name));
                        try {
                            double difRes = Double.parseDouble(res);
                            ifProp.setTX((difRes * 8D) / diff);
                        } catch (Throwable t) {
                            ifProp.setTX(-1D);
                        }
                    }
                    lastTXBytes.put(name, newVal);
                }

                pattern = PatternUtil.getPattern("collisions", collisions);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    String newVal = matcher.group(1);
                    if (diff > 0) {
                        String res = diffWithOverflowCheck(newVal, (String) lastCollisions.get(name));
                        try {
                            double difRes = Double.parseDouble(res);
                            ifProp.setCollisions(difRes / diff);
                        } catch (Throwable t) {
                            ifProp.setCollisions(-1D);
                        }
                    }
                    lastCollisions.put(name, newVal);
                }

                pattern = PatternUtil.getPattern("compressed", compressed);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    String newVal = matcher.group(1);
                    if (diff > 0) {
                        String res = diffWithOverflowCheck(newVal, (String) lastCompressed.get(name));
                        try {
                            double difRes = Double.parseDouble(res);
                            ifProp.setCompressed(difRes / diff);
                            ifProp.setCanCompress(true);
                        } catch (Throwable t) {
                            ifProp.setCompressed(-1D);
                        }
                    }
                    lastCompressed.put(name, newVal);
                } else {
                    ifProp.setCanCompress(false);
                }

                pattern = PatternUtil.getPattern("txqueuelen", txqueuelen);
                matcher = pattern.matcher(interfaces[i]);
                if (matcher.find()) {
                    String newVal = matcher.group(1);
                    try {
                        ifProp.setTXQueueLen(Integer.parseInt(newVal));
                    } catch (Exception e) {
                        ifProp.setTXQueueLen(-1);
                    }
                }

            }
            // finnaly if some interfaces were removed, than better remove their properties also
            toRemove.clear();
            for (Iterator it = ifs.keySet().iterator(); it.hasNext();) {
                String ifn = (String) it.next();
                if (!ifArray.contains(ifn)) {
                    toRemove.add(ifn);
                }
            }
            for (Iterator it = toRemove.iterator(); it.hasNext();) {
                String ifn = (String) it.next();
                ifs.remove(ifn);
                staticifs.remove(ifn);
            }

            //			for (Map.Entry<String, InterfaceStatistics> entry : ifs.entrySet()) {
            //				logger.info(entry.getValue().toString());
            //			}
        }

        /** A method that can be used to check for the encapsulation type of a link 
         * @param props (Option) The properties to put the type into
         * @param ifOutput The output of the ifconfig utility
         * @return False if the encapsulation type is of no interest (for example loopback interface)
         * */
        private final boolean checkLinkEncap(final InterfaceStatisticsStatic props, final String ifOutput) {

            if ((ifOutput == null) || (ifOutput.length() == 0)) {
                return false;
            }
            if (ifOutput.indexOf("Link encap:Ethernet") >= 0) {
                if (props != null) {
                    props.setEncap(InterfaceStatisticsStatic.TYPE_ETHERNET);
                }
                return true;
            }
            if (ifOutput.indexOf("Link encap:Fiber Distributed Data Interface") >= 0) {
                if (props != null) {
                    props.setEncap(InterfaceStatisticsStatic.TYPE_FIBER);
                }
                return true;
            }
            // TODO maybe we should consider:
            // HIPPI: http://www.rfc-editor.org/rfc/rfc1374.txt
            // AX.25, X.25, Token Ring, Frame Relay, ISDN, HDLC, PPP, Econet
            return false;
        }

        /**
         * Return the current declared path of the ifconfig utility (default to /sbin)
         * @return The path to ifconfig utility.
         */
        private synchronized final String getIfconfigPath() {
            String path = "/bin,/sbin,/usr/bin,/usr/sbin";
            path = System.getProperty("net.ifconfig.path", path);
            if ((path == null) || (path.length() == 0)) {
                logger.warning("[Net - iconfig can not be found in " + path + "]");
                return null;
            }
            return path.replace(',', ':').trim();
        }

        /** This is used to check for network interface devices locally by looking into
         * /proc/net/dev - is used only if ifconfig fails to speed up things 
         */
        private final void getFromProcDev() {
            ifArray.clear();
            final Pattern pattern = PatternUtil.getPattern("/proc/net/dev", netDevPattern);
            try {
                BufferedReader reader = new BufferedReader(new FileReader("/proc/net/dev"));
                String line;
                while ((line = reader.readLine()) != null) {
                    final Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        final String ifName = matcher.group(1);
                        ifArray.add(ifName);
                        if (!ifs.containsKey(ifName)) {
                            ifs.put(ifName, new InterfaceStatistics(ifName));
                        }
                    }
                }
                reader.close();
            } catch (Exception e) {
            }

            // finnaly if some interfaces were removed, than better remove their properties also
            toRemove.clear();
            for (Iterator it = ifs.keySet().iterator(); it.hasNext();) {
                String ifn = (String) it.next();
                if (!ifArray.contains(ifn)) {
                    toRemove.add(ifn);
                }
            }
            for (Iterator it = toRemove.iterator(); it.hasNext();) {
                String ifn = (String) it.next();
                ifs.remove(ifn);
            }
        }

        public String diffWithOverflowCheck(String newVal, String oldVal) throws NumberFormatException {
            if (is64BitArch) {
                String str = prepareString(newVal);
                BigDecimal newv = null;
                try {
                    newv = new BigDecimal(str);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exception " + t + " for " + str);
                }
                str = prepareString(oldVal);
                BigDecimal oldv = null;
                try {
                    oldv = new BigDecimal(str);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exception " + t + " for " + str);
                }
                if (newv.compareTo(oldv) >= 0) {
                    return newv.subtract(oldv).toString();
                }
                BigInteger overflow = new BigInteger("1").shiftLeft(64);
                BigDecimal d = new BigDecimal(overflow.toString());
                return newv.add(d).subtract(oldv).toString();
            }
            // otherwise we still assume 32 bits arch
            double toCompare = 1L << 32;
            double newv = Double.parseDouble(newVal);
            double oldv = Double.parseDouble(oldVal);
            if ((newv >= toCompare) || (oldv >= toCompare)) {
                is64BitArch = true;
                return diffWithOverflowCheck(newVal, oldVal);
            }
            // so it's still 32 bits arch
            if (newv >= oldv) {
                return "" + (newv - oldv);
            }
            long vmax = 1L << 32; // 32 bits
            return "" + ((newv - oldv) + vmax);
        }

        public String divideWithOverflowCheck(String newVal, String oldVal) throws NumberFormatException {

            if (is64BitArch) {
                String str = prepareString(newVal);
                BigDecimal newv = null;
                try {
                    newv = new BigDecimal(str);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exception " + t + " for " + str);
                }
                str = prepareString(oldVal);
                BigDecimal oldv = null;
                try {
                    oldv = new BigDecimal(str);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exception " + t + " for " + str);
                }
                return newv.divide(oldv, BigDecimal.ROUND_FLOOR).toString();
            }
            // otherwise we still assume 32 bits arch
            double toCompare = 1L << 32;
            double newv = Double.parseDouble(newVal);
            double oldv = Double.parseDouble(oldVal);
            if ((newv >= toCompare) || (oldv >= toCompare)) {
                is64BitArch = true;
                return divideWithOverflowCheck(newVal, oldVal);
            }
            // so it's still 32 bits arch
            return "" + (newv / oldv);
        }

        private final String prepareString(String str) {

            // first try to make it double
            try {
                double d = Double.parseDouble(str);
                if (!Double.isInfinite(d) && !Double.isNaN(d)) {
                    String n = nf.format(d);
                    n = n.replaceAll(",", "");
                    return n;
                }
            } catch (Throwable t) {
            }

            if (str.indexOf(".") < 0) {
                return str + ".0000";
            }
            int nr = str.lastIndexOf('.') + 1;
            nr = str.length() - nr;
            for (int i = nr; i < 4; i++) {
                str += "0";
            }
            return str;
        }
    }

    private static class iCmdExec {

        private static final Logger logger = Logger.getLogger(iCmdExec.class.getName());

        public String full_cmd;
        public Process pro;
        String osname;
        String exehome = "";

        protected LinkedList streams = null;
        protected LinkedList streamsReal = null;

        //protected boolean isError = false;

        /* These varibles are set to true when we want to destroy the streams pool */
        protected boolean stopStreams = false;
        protected boolean stopStreamsReal = false;

        private static iCmdExec _instance = null;

        /**
         * structure for command output
         */
        public static CommandResult NullCommandResult = new CommandResult(null, true);

        public static class CommandResult {
            private final String output;
            private final boolean failed;

            public CommandResult(String output, boolean wasError) {
                this.output = output;
                this.failed = wasError;
            }

            /**
             * @return Returns the output.
             */
            public String getOutput() {
                return output == null ? "" : output;
            }

            /**
             * @return Returns the failed.
             */
            public boolean failed() {
                return failed;
            }

        }

        private iCmdExec() {
            osname = System.getProperty("os.name");
            exehome = System.getProperty("user.home");
            streams = new LinkedList();
            streamsReal = new LinkedList();
        }

        public static synchronized iCmdExec getInstance() {
            if (_instance == null) {
                _instance = new iCmdExec();
            }
            return _instance;
        }

        public void setCmd(String cmd) {
            osname = System.getProperty("os.name");
            full_cmd = cmd; // local
        }

        public BufferedReader procOutput(String cmd) {
            try {

                if (osname.startsWith("Linux") || osname.startsWith("Mac")) {
                    pro = Runtime.getRuntime().exec(new String[] { "/bin/sh", "-c", cmd });
                } else if (osname.startsWith("Windows")) {
                    pro = Runtime.getRuntime().exec(exehome + cmd);
                }

                InputStream out = pro.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(out));
                BufferedReader err = new BufferedReader(new InputStreamReader(pro.getErrorStream()));

                String buffer = "";
                String ret = "";
                while ((buffer = err.readLine()) != null) {
                    ret += buffer + "\n'";
                }

                if (ret.length() != 0) {
                    return null;
                }

                return br;

            } catch (Exception e) {
                logger.warning("FAILED to execute cmd = " + exehome + cmd);
                Thread.currentThread().interrupt();
            }

            return null;
        }

        public BufferedReader exeHomeOutput(String cmd) {

            try {
                pro = Runtime.getRuntime().exec(new String[] { "/bin/sh", "-c", exehome + cmd });
                InputStream out = pro.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(out));

                BufferedReader err = new BufferedReader(new InputStreamReader(pro.getErrorStream()));

                String buffer = "";
                String ret = "";
                while ((buffer = err.readLine()) != null) {
                    ret += buffer + "\n'";
                }

                if (ret.length() != 0) {
                    return null;
                }
                return br;
            } catch (Exception e) {
                logger.warning("FAILED to execute cmd = " + exehome + cmd);
                Thread.currentThread().interrupt();
            }
            return null;
        }

        public void stopModule() {
            if (this.pro != null) {
                this.pro.destroy();
            }
        }

        public BufferedReader readProc(String filePath) {
            try {
                return new BufferedReader(new FileReader(filePath));
            } catch (Exception e) {

                return null;
            }
        }

        public CommandResult executeCommand(String command, String expect) {
            return executeCommand(command, expect, 60 * 1000);
        }

        public CommandResult executeCommand(String command, String expect, long timeout) {

            StreamGobbler output = null;
            StreamGobbler error = null;
            boolean isError = false;
            try {
                String osName = System.getProperty("os.name");
                Process proc = null;

                if (osName.indexOf("Win") != -1) {
                    proc = Runtime.getRuntime().exec(command);
                } else if ((osName.indexOf("Linux") != -1) || (osName.indexOf("Mac") != -1)) {
                    String[] cmd = new String[3];
                    cmd[0] = "/bin/sh";
                    cmd[1] = "-c";
                    cmd[2] = command;
                    proc = Runtime.getRuntime().exec(cmd);
                } else {
                    isError = true;
                    return null;
                }

                error = getStreamGobbler();
                output = getStreamGobbler();

                // any error message?
                error.setInputStream(proc.getErrorStream());

                // any output?
                output.setInputStream(proc.getInputStream());

                String out = "";

                // any error???
                long startTime = NTPDate.currentTimeMillis();
                while (true) {
                    try {
                        out = error.getOutput();
                        if ((out != null) && (out.length() != 0) && (proc.exitValue() != 0)) {
                            isError = true;
                            break;
                        }
                    } catch (IllegalThreadStateException ex) {
                    }
                    if (expect != null) {
                        out = output.getOutput();
                        if ((out != null) && (out.length() != 0) && (out.indexOf(expect) != -1)) {
                            isError = false;
                            break;
                        }
                    }
                    long endTime = NTPDate.currentTimeMillis();
                    if ((endTime - startTime) > timeout) {
                        isError = true;
                        break;
                    }
                    Thread.sleep(100);
                }

                proc.destroy();
                proc.waitFor();

                if ((out.length() == 0) || (proc.exitValue() == 0)) {
                    out = output.getOutput();
                }

                error.stopIt();
                output.stopIt();

                addStreamGobbler(error);
                addStreamGobbler(output);

                error = null;
                output = null;

                return new CommandResult(out, isError);

            } catch (Exception e) {
                e.printStackTrace();

                if (error != null) {
                    addStreamGobbler(error);
                    error.stopIt();
                    error = null;
                }

                if (output != null) {
                    addStreamGobbler(output);
                    output.stopIt();
                    output = null;
                }
                isError = true;
                return new CommandResult("", true);
            }
        }

        public CommandResult executeCommand(String command, Pattern expect) {
            return executeCommand(command, expect, 60 * 1000);
        }

        public CommandResult executeCommand(String command, Pattern expect, long timeout) {

            StreamGobbler output = null;
            StreamGobbler error = null;
            boolean isError = false;
            try {
                String osName = System.getProperty("os.name");
                Process proc = null;

                if (osName.indexOf("Win") != -1) {
                    proc = Runtime.getRuntime().exec(command);
                } else if ((osName.indexOf("Linux") != -1) || (osName.indexOf("Mac") != -1)) {
                    String[] cmd = new String[3];
                    cmd[0] = "/bin/sh";
                    cmd[1] = "-c";
                    cmd[2] = command;
                    proc = Runtime.getRuntime().exec(cmd);
                } else {
                    isError = true;
                    return null;
                }

                error = getStreamGobbler();
                output = getStreamGobbler();

                // any error message?
                error.setInputStream(proc.getErrorStream());

                // any output?
                output.setInputStream(proc.getInputStream());

                String out = "";

                // any error???
                long startTime = NTPDate.currentTimeMillis();
                while (true) {
                    try {
                        out = error.getOutput();
                        if ((out != null) && (out.length() != 0) && (proc.exitValue() != 0)) {
                            isError = true;
                            break;
                        }
                    } catch (IllegalThreadStateException ex) {
                    }
                    if (expect != null) {
                        out = output.getOutput();
                        if ((out != null) && (out.length() != 0)) {
                            if (expect.matcher(out).matches()) {
                                isError = false;
                                break;
                            }
                        }
                    }
                    long endTime = NTPDate.currentTimeMillis();
                    if ((endTime - startTime) > timeout) {
                        isError = true;
                        break;
                    }
                    Thread.sleep(100);
                }

                proc.destroy();
                proc.waitFor();

                if ((out.length() == 0) || (proc.exitValue() == 0)) {
                    out = output.getOutput();
                }

                error.stopIt();
                output.stopIt();

                addStreamGobbler(error);
                addStreamGobbler(output);

                error = null;
                output = null;

                return new CommandResult(out, isError);

            } catch (Exception e) {
                e.printStackTrace();

                if (error != null) {
                    addStreamGobbler(error);
                    error.stopIt();
                    error = null;
                }

                if (output != null) {
                    addStreamGobbler(output);
                    output.stopIt();
                    output = null;
                }
                return new CommandResult("", true);
            }
        }

        public CommandResult executeCommand(String command, String expect, int howManyTimes) {
            return executeCommand(command, expect, howManyTimes, 60 * 1000);
        }

        public CommandResult executeCommand(String command, String expect, int howManyTimes, long timeout) {

            StreamGobbler output = null;
            StreamGobbler error = null;
            int nr = 0; // how many times the expect string occured
            boolean isError = false;
            try {
                String osName = System.getProperty("os.name");
                Process proc = null;

                if (osName.indexOf("Win") != -1) {
                    proc = Runtime.getRuntime().exec(command);
                } else if ((osName.indexOf("Linux") != -1) || (osName.indexOf("Mac") != -1)) {
                    String[] cmd = new String[3];
                    cmd[0] = "/bin/sh";
                    cmd[1] = "-c";
                    cmd[2] = command;
                    proc = Runtime.getRuntime().exec(cmd);
                } else {
                    return NullCommandResult;
                }

                error = getStreamGobbler();
                output = getStreamGobbler();

                error.setInputStream(proc.getErrorStream());

                output.setInputStream(proc.getInputStream());

                String out = "";

                long startTime = NTPDate.currentTimeMillis();
                while (true) {
                    try {
                        out = error.getOutput();
                        if ((out != null) && (out.length() != 0) && (proc.exitValue() != 0)) {
                            isError = true;
                            break;
                        }
                    } catch (IllegalThreadStateException ex) {
                    }
                    if (expect != null) {
                        out = output.getOutput();
                        if ((out != null) && (out.length() != 0) && (out.indexOf(expect) != -1)) {
                            nr = getStringOccurences(out, expect);
                            if (nr >= howManyTimes) {
                                isError = false;
                                break;
                            }
                        }
                    }
                    long endTime = NTPDate.currentTimeMillis();
                    if ((endTime - startTime) > timeout) {
                        isError = true;
                        break;
                    }
                    Thread.sleep(100);
                }

                proc.destroy();
                proc.waitFor();

                if ((out.length() == 0) || (proc.exitValue() == 0)) {
                    out = output.getOutput();
                }

                error.stopIt();
                output.stopIt();

                addStreamGobbler(error);
                addStreamGobbler(output);

                error = null;
                output = null;

                return new CommandResult(out, isError);

            } catch (Exception e) {
                e.printStackTrace();

                if (error != null) {
                    addStreamGobbler(error);
                    error.stopIt();
                    error = null;
                }

                if (output != null) {
                    addStreamGobbler(output);
                    output.stopIt();
                    output = null;
                }
                return NullCommandResult;
            }
        }

        protected int getStringOccurences(String text, String token) {

            if (text.indexOf(token) < 0) {
                return 0;
            }
            int nr = 0;
            String str = text;
            while (str.indexOf(token) >= 0) {
                str = str.substring(str.indexOf(token) + token.length());
                nr++;
            }
            return nr;
        }

        public CommandResult executeCommandReality(String command, String expect, String path) {
            return executeCommandReality(command, expect, 60 * 1000, path);
        }

        public CommandResult executeCommandReality(String command, String expect, long timeout, String path) {

            StreamRealGobbler error = null;
            StreamRealGobbler output = null;
            boolean isError = false;
            try {
                String osName = System.getProperty("os.name");
                Process proc = null;

                if (osName.indexOf("Win") != -1) {
                    proc = Runtime.getRuntime().exec(command);
                } else if (osName.indexOf("Linux") != -1) {
                    String[] cmd = new String[3];
                    cmd[0] = "/bin/sh";
                    cmd[1] = "-c";
                    cmd[2] = command;
                    if ((path != null) && (path.length() != 0)) {
                        proc = Runtime.getRuntime().exec(cmd, new String[] { "PATH=" + path });
                    } else {
                        proc = Runtime.getRuntime().exec(cmd);
                    }
                } else {
                    return NullCommandResult;
                }

                error = getStreamRealGobbler(timeout);
                output = getStreamRealGobbler(timeout);

                // any error message?
                error.setInputStream(proc.getErrorStream());

                // any output?
                output.setInputStream(proc.getInputStream());

                String out = "";

                // any error???
                long startTime = NTPDate.currentTimeMillis();
                boolean timeoutOccured = false;
                while (true) {
                    try {
                        out = error.forceAllOutput();
                        if (proc.exitValue() != 0) {
                            isError = true;
                        }
                        break; // also if exitValue did not throw exception than we're done running
                    } catch (IllegalThreadStateException ex) {
                    }
                    if (expect != null) {
                        out = output.forceAllOutput();
                        if ((out != null) && (out.length() != 0) && (out.indexOf(expect) != -1)) {
                            isError = false;
                            proc.destroy();
                            break;
                        }
                    }
                    long endTime = NTPDate.currentTimeMillis();
                    if ((endTime - startTime) > timeout) {
                        isError = true;
                        timeoutOccured = true;
                        proc.destroy();
                        break;
                    }
                    Thread.sleep(100);
                }

                if (!timeoutOccured) {
                    proc.waitFor();
                } else {
                    try {
                        Thread.sleep(2000);
                        proc.getOutputStream().close();
                        proc.getInputStream().close();
                        proc.getErrorStream().close();
                    } catch (Exception ex) {
                    }
                }

                if (((out != null) && (out.length() == 0)) || (proc.exitValue() == 0)) {
                    out = output.forceAllOutput();
                }

                if (timeoutOccured) {
                    out += "...Timeout";
                }

                error.stopIt();
                output.stopIt();

                addStreamRealGobbler(error);
                addStreamRealGobbler(output);

                error = null;
                output = null;

                return new CommandResult(out, isError);

            } catch (Exception e) {
                e.printStackTrace();

                if (error != null) {
                    addStreamRealGobbler(error);
                    error.stopIt();
                    error = null;
                }

                if (output != null) {
                    addStreamRealGobbler(output);
                    output.stopIt();
                    output = null;
                }
                return NullCommandResult;
            }
        }

        public void executeCommandRealityForFinish(String command, String path) {
            executeCommandRealityForFinish(command, false, path);
        }

        public void executeCommandRealityForFinish(String command, final boolean showOutput, String path) {
            executeCommandRealityForFinish(command, showOutput, 60 * 60 * 1000, path);
        }

        public boolean executeCommandRealityForFinish(String command, final boolean showOutput, long timeout,
                String path) {

            StreamRealGobbler error = null;
            StreamRealGobbler output = null;
            boolean isError = false;
            try {
                String osName = System.getProperty("os.name");
                Process proc = null;
                if (osName.indexOf("Win") != -1) {
                    proc = Runtime.getRuntime().exec(command);
                } else if (osName.indexOf("Linux") != -1) {
                    String[] cmd = new String[3];
                    cmd[0] = "/bin/sh";
                    cmd[1] = "-c";
                    cmd[2] = command;
                    if ((path != null) && (path.length() != 0)) {
                        proc = Runtime.getRuntime().exec(cmd, new String[] { "PATH=" + path });
                    } else {
                        proc = Runtime.getRuntime().exec(cmd);
                    }
                } else {
                    return true;
                }

                if (showOutput) {
                    error = getStreamRealGobbler(timeout);
                    output = getStreamRealGobbler(timeout);
                    error.setProgress(true);
                    output.setProgress(true);
                    // any error message?
                    error.setInputStream(proc.getErrorStream());
                    // any output?
                    output.setInputStream(proc.getInputStream());
                    error.setProgress(false);
                    output.setProgress(false);
                }

                long startTime = NTPDate.currentTimeMillis();
                boolean timeoutOccured = false;
                while (true) {
                    try {
                        if (proc.exitValue() != 0) {
                            isError = true;
                        }
                        break; // also if exitValue did not throw exception than we're done running
                    } catch (IllegalThreadStateException ex) {
                    }
                    long endTime = NTPDate.currentTimeMillis();
                    if ((endTime - startTime) > timeout) {
                        isError = true;
                        timeoutOccured = true;
                        proc.destroy();
                        break;
                    }
                    Thread.sleep(100);
                }

                if (!timeoutOccured) {
                    proc.waitFor();
                } else {
                    try {
                        Thread.sleep(2000);
                        proc.getOutputStream().close();
                        proc.getInputStream().close();
                        proc.getErrorStream().close();
                    } catch (Exception ex) {
                    }
                }

                if (showOutput) {
                    error.setProgress(false);
                    output.setProgress(false);
                    error.stopIt();
                    output.stopIt();
                    addStreamRealGobbler(error);
                    addStreamRealGobbler(output);
                    error = null;
                    output = null;
                }

            } catch (Exception e) {
                e.printStackTrace();
                isError = true;
                if (error != null) {
                    addStreamRealGobbler(error);
                    error.stopIt();
                    error = null;
                }

                if (output != null) {
                    addStreamRealGobbler(output);
                    output.stopIt();
                    output = null;
                }

            }
            return isError;
        }

        public CommandResult executeCommandReality(String command, String expect, int howManyTimes, String path) {
            return executeCommandReality(command, expect, howManyTimes, 60 * 1000, path);
        }

        public CommandResult executeCommandReality(String command, String expect, int howManyTimes, long timeout,
                String path) {

            StreamRealGobbler error = null;
            StreamRealGobbler output = null;
            boolean isError = false;
            try {
                String osName = System.getProperty("os.name");
                Process proc = null;

                if (osName.indexOf("Win") != -1) {
                    proc = Runtime.getRuntime().exec(command);
                } else if (osName.indexOf("Linux") != -1) {
                    String[] cmd = new String[3];
                    cmd[0] = "/bin/sh";
                    cmd[1] = "-c";
                    cmd[2] = command;
                    if ((path != null) && (path.length() != 0)) {
                        proc = Runtime.getRuntime().exec(cmd, new String[] { "PATH=" + path });
                    } else {
                        proc = Runtime.getRuntime().exec(cmd);
                    }
                } else {
                    return NullCommandResult;
                }

                error = getStreamRealGobbler(timeout);
                output = getStreamRealGobbler(timeout);

                error.setInputStream(proc.getErrorStream());

                output.setInputStream(proc.getInputStream());

                String out = "";

                long startTime = NTPDate.currentTimeMillis();
                boolean timeoutOccured = false;
                while (true) {
                    try {
                        out = error.forceAllOutput();
                        if ((out != null) && (out.length() != 0) && (proc.exitValue() != 0)) {
                            isError = true;
                        }
                        break;
                    } catch (IllegalThreadStateException ex) {
                    }
                    if (expect != null) {
                        out = output.forceAllOutput();
                        if ((out != null) && (out.length() != 0) && (out.indexOf(expect) != -1)) {
                            int nr = getStringOccurences(out, expect);
                            if (nr >= howManyTimes) {
                                isError = false;
                                proc.destroy();
                                break;
                            }
                        }
                    }
                    long endTime = NTPDate.currentTimeMillis();
                    if ((endTime - startTime) > timeout) {
                        isError = true;
                        timeoutOccured = true;
                        proc.destroy();
                        break;
                    }
                    Thread.sleep(100);
                }

                if (!timeoutOccured) {
                    proc.waitFor();
                } else {
                    try {
                        Thread.sleep(2000);
                        proc.getOutputStream().close();
                        proc.getInputStream().close();
                        proc.getErrorStream().close();
                    } catch (Exception ex) {
                    }
                }

                if (((out != null) && (out.length() == 0)) || (proc.exitValue() == 0)) {
                    out = output.forceAllOutput();
                }

                error.stopIt();
                output.stopIt();

                addStreamRealGobbler(error);
                addStreamRealGobbler(output);

                error = null;
                output = null;
                return new CommandResult(out, isError);

            } catch (Exception e) {
                e.printStackTrace();

                if (error != null) {
                    addStreamRealGobbler(error);
                    error.stopIt();
                    error = null;
                }

                if (output != null) {
                    addStreamRealGobbler(output);
                    output.stopIt();
                    output = null;
                }
                return NullCommandResult;
            }
        }

        public StreamGobbler getStreamGobbler() {

            synchronized (streams) {
                if (streams.size() == 0) {
                    StreamGobbler stream = new StreamGobbler(null);
                    stream.start();
                    return stream;
                }
                return (StreamGobbler) streams.removeFirst();
            }
        }

        public void addStreamGobbler(StreamGobbler stream) {

            synchronized (streams) {
                if (!stopStreams) {
                    streams.addLast(stream);
                } else {
                    stream.stopItForever();
                }
            }
        }

        public StreamRealGobbler getStreamRealGobbler(long timeout) {

            synchronized (streamsReal) {
                if (streamsReal.size() == 0) {
                    StreamRealGobbler stream = new StreamRealGobbler(null, timeout);
                    stream.start();
                    return stream;
                }
                StreamRealGobbler st = (StreamRealGobbler) streamsReal.removeFirst();
                st.setTimeout(timeout);
                return st;
            }
        }

        public void addStreamRealGobbler(StreamRealGobbler stream) {

            synchronized (streamsReal) {
                if (!stopStreamsReal) {
                    streamsReal.addLast(stream);
                } else {
                    stream.stopItForever();
                }
            }
        }

        public void stopIt() {
            synchronized (streams) {
                stopStreams = true;

                while (streams.size() > 0) {
                    StreamGobbler sg = (StreamGobbler) (streams.removeFirst());
                    sg.stopItForever();
                }
            }
            synchronized (streamsReal) {
                stopStreamsReal = true;

                while (streamsReal.size() > 0) {
                    StreamRealGobbler sg = (StreamRealGobbler) (streamsReal.removeFirst());
                    sg.stopItForever();
                }
            }
        }

        class StreamGobbler extends Thread {

            InputStream is;
            StringBuilder output;
            boolean stop = false;
            boolean stopForever = false;
            boolean doneReading = false;

            public StreamGobbler(InputStream is) {

                super("Stream Gobler");
                this.is = is;
                this.output = new StringBuilder();
                this.setDaemon(true);
            }

            public void setInputStream(InputStream is) {

                this.is = is;
                output = new StringBuilder();
                stop = false;
                synchronized (this) {
                    doneReading = false;
                    notify();
                }
            }

            public String getOutput() {
                return output == null ? null : output.toString();
            }

            public synchronized String forceAllOutput() {

                if (!doneReading) {
                    return null;
                }
                doneReading = false;
                return output == null ? null : output.toString();
            }

            public void stopIt() {

                stop = true;
            }

            public void stopItForever() {
                synchronized (this) {
                    stopForever = true;
                    notify();
                }
            }

            @Override
            public void run() {

                while (true) {

                    synchronized (this) {
                        while ((is == null) && !stopForever) {
                            try {
                                wait();
                            } catch (Exception e) {
                            }
                        }
                    }

                    if (stopForever) {
                        break;
                    }

                    try {
                        InputStreamReader isr = new InputStreamReader(is);
                        BufferedReader br = new BufferedReader(isr);
                        String line = null;
                        while (!stop && ((line = br.readLine()) != null)) {
                            output.append(line);
                        }
                        synchronized (this) {
                            doneReading = true;
                        }
                        is.close();
                    } catch (Exception ioe) {
                        output = new StringBuilder();
                    }
                    is = null;
                }
            }
        }

        class StreamRealGobbler extends Thread {

            InputStream is;
            InputStreamReader isr;
            final char[] buf;
            StringBuilder output = new StringBuilder();
            boolean stop = false;
            boolean doneReading = false;
            boolean stopForever = false;

            private Thread thread = null;
            private boolean showProgress = false;

            private long timeout;

            public StreamRealGobbler(InputStream is, long timeout) {

                super("Stream Real Gobler");
                this.timeout = timeout;
                this.is = is;
                this.setDaemon(true);
                buf = new char[32];
            }

            public void setTimeout(long timeout) {
                this.timeout = timeout;
            }

            public void setInputStream(InputStream is) {

                this.is = is;
                output = new StringBuilder();
                stop = false;
                synchronized (buf) {
                    doneReading = false;
                    buf.notifyAll();
                }
            }

            public void setProgress(boolean showProgress) {
                this.showProgress = showProgress;
            }

            public String getOutput() {

                return output == null ? null : output.toString();
            }

            public String forceAllOutput() {

                if (!doneReading) {
                    try {
                        if (!isr.ready()) {
                            return null;
                        }
                    } catch (Exception ex) {
                    }

                    try {
                        thread.interrupt(); // force the thread out of sleep
                    } catch (Exception ex) {
                    }
                    synchronized (buf) {
                        buf.notifyAll();
                    }
                    // otherwise let's give the output a chance to complete
                    long start = NTPDate.currentTimeMillis();
                    while (!doneReading) {
                        try {
                            Thread.sleep(200);
                        } catch (Exception ex) {
                        }
                        if (doneReading) {
                            return output == null ? null : output.toString();
                        }
                        long now = NTPDate.currentTimeMillis();
                        if ((now - start) >= timeout) {
                            return null; // last chance
                        }
                    }
                }
                return output == null ? null : output.toString();
            }

            public void stopIt() {
                try {
                    is.close();
                } catch (Exception ex) {
                }
                try {
                    isr.close();
                } catch (Exception ex) {
                }
                stop = true;
            }

            public void stopItForever() {
                synchronized (buf) {
                    stopIt();
                    stopForever = true;
                    buf.notifyAll();
                }
            }

            @Override
            public void run() {

                thread = Thread.currentThread();

                while (true) {

                    synchronized (buf) {
                        while ((is == null) && !stopForever) {
                            try {
                                buf.wait();
                            } catch (Exception e) {
                            }
                        }
                    }

                    if (stopForever) {
                        synchronized (buf) {
                            doneReading = true;
                        }
                        break;
                    }

                    try {
                        isr = new InputStreamReader(is);
                        while (!stop) {
                            try {
                                final int ret = isr.read(buf);
                                if (ret > 0) {
                                    final String nstr = new String(buf, 0, ret);
                                    if (showProgress) {
                                        logger.info(nstr);
                                    }
                                    output.append(nstr);
                                } else {
                                    break; // and of stream
                                }
                            } catch (Exception ex) {
                                break;
                            }
                        }

                        synchronized (buf) {
                            doneReading = true;
                        }
                    } catch (Exception ioe) {
                        output = new StringBuilder();
                    }
                    try {
                        is.close();
                    } catch (Exception ex) {
                    }
                    is = null;
                }
            }
        }

    }

} // end of class monNetworking

