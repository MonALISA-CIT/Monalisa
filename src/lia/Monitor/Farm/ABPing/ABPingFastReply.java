package lia.Monitor.Farm.ABPing;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniSerFarmMon.MLLUSHelper;
import lia.Monitor.monitor.ABPingEntry;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;
import lia.util.ntp.NTPDate;

import com.BajieSoft.HttpSrv.an;

import lazyj.Utils;

/**
 * ABPingFastReply = Available Bandwidth Ping with fast packet reply
 * 
 * ABPing tries to estimate the available bandwidth of a link measuring RTT,
 * packet loss, jitter of UDP packages sent over the link.
 * 
 * It is both a client and a server and both are required in order to work.
 * 
 * This uses PeerInfo class to store info about peers
 *  
 */

public class ABPingFastReply {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(ABPingFastReply.class.getName());

    public static final int PORT;

    static {
        int port = 9000;
        try {
            port = Integer.valueOf(AppConfig.getProperty("lia.Monitor.ABPingUDPPort", "9000")).intValue();
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " Define lia.Monitor.ABPingUDPPort in ml.prop as an integer!!");
            }
            port = 9000;
        }
        PORT = port;
        logger.log(Level.INFO, " ABPing will use port " + PORT);
    }

    int PACKET_SIZE = 450; // bytes

    int PING_INTERVAL = 4000; // milliseconds

    // RTime, the link performance indicator is computed like this:
    // RTime = OVERALL_COEF + RTT_COEF * rtt + PKT_LOSS_COEF * loss% +
    // JITTER_COEF * jitter%
    // lower is better

    double OVERALL_COEF = 0;

    double RTT_COEF = 1;

    double PKT_LOSS_COEF = 100;

    double JITTER_COEF = 10;

    int RTT_SAMPLES = 5; // number of samples i keep in order to compute the
                         // rtt average

    int PKT_LOSS_MEM = 10; // remeber # pktLossMem pkts to compute % of lost
                           // pcks

    private static byte ECHO_PACKET = 123; // echo packet; sent to peer

    private static byte REPLY_PACKET = 45; // response from peer

    private Client cli;

    private Server serv;

    public boolean active;

    private DatagramSocket sock; // the datagram socket used to send and receive the UDP Packets

    private final Object syncActive = new Object();

    private static int MAX_UID = 1000;

    public String myName;

    public Hashtable myPeers;

    public Hashtable results;

    private static Hashtable addrCache = new Hashtable();

    private static Hashtable addrPortCache = new Hashtable();

    boolean standAlone = false;

    public static InetAddress getInetAddr(String name) throws UnknownHostException {
        InetAddress a = (InetAddress) addrCache.get(name);
        if (a == null) {
            a = InetAddress.getByName(name);
            addrCache.put(name, a);
        }
        return a;
    }

    public static int getPeerPort(String name) throws UnknownHostException {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "\n\nGetting Peer PORT for Name = " + name + " / addr = " + getInetAddr(name));
        }
        return getPeerPort(getInetAddr(name));
    }

    public static int getPeerPort(InetAddress addr) throws UnknownHostException {
        Integer val = (Integer) addrPortCache.get(addr);
        int retV = 9000;
        if (val != null) {
            retV = val.intValue();
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "\n\nGetting Peer PORT for ADDR addr = " + addr + " [ " + retV + " ]");
        }
        return retV;
    }

    public static void setPortForInetAddr(int port, InetAddress addr) {
        addrPortCache.put(addr, Integer.valueOf(port));
    }

    public static void setPortForName(int port, String name) throws UnknownHostException {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " Seetting Peer PORT [ " + port + " ] for Name = " + name + " / addr = "
                    + getInetAddr(name));
        }
        setPortForInetAddr(port, getInetAddr(name));
    }

    public ABPingFastReply(String myName) {
        this.myPeers = new Hashtable();
        this.results = new Hashtable();

        this.myName = myName;

        checkPingers();
    }

    public void setConfig(double overallCoef, double rttCoef, double pktLossCoef, double jitterCoef, int rttSamples,
            int pktLossMem, int packetSize, int pingInterval) {
        if ((OVERALL_COEF != overallCoef) || (RTT_COEF != rttCoef) || (PKT_LOSS_COEF != pktLossCoef)
                || (JITTER_COEF != jitterCoef) || (RTT_SAMPLES != rttSamples) || (PKT_LOSS_MEM != pktLossMem)
                || (PACKET_SIZE != packetSize) || (PING_INTERVAL != pingInterval)) {
            synchronized (myPeers) {
                OVERALL_COEF = overallCoef;
                RTT_COEF = rttCoef;
                PKT_LOSS_COEF = pktLossCoef;
                JITTER_COEF = jitterCoef;
                RTT_SAMPLES = rttSamples;
                PKT_LOSS_MEM = pktLossMem;
                PACKET_SIZE = packetSize;
                PING_INTERVAL = pingInterval;
                logger.log(Level.CONFIG, "ABPingFastReply: setting new config params...");
                for (Enumeration en = myPeers.keys(); en.hasMoreElements();) {
                    PeerInfo myPi = (PeerInfo) myPeers.get(en.nextElement());
                    myPi.setConfig();
                }
            }
        }
    }

    /** 
     * Generate a UID that is not used among the existing PeerInfo-s;
     * For this to work, MAX_UID should be greated than the foreseen number
     * of peers for a node. 
     */
    private int getFreePeerUID() {
        again: while (true) {
            int uid = (int) (Math.random() * MAX_UID);
            for (Enumeration enp = myPeers.elements(); enp.hasMoreElements();) {
                PeerInfo pi = (PeerInfo) enp.nextElement();
                if (pi.uid.intValue() == uid) {
                    continue again;
                }
            }
            return uid;
        }
    }

    /** get the current PacketLoss computed for the given peer */
    public double getPeerPacketLoss(String name) {
        PeerInfo pi = (PeerInfo) myPeers.get(name);
        if (pi == null) {
            return -1;
        }
        return pi.crtPacketLoss;
    }

    /** get the current RTT computed for the given peer */
    public double getPeerRTT(String name) {
        PeerInfo pi = (PeerInfo) myPeers.get(name);
        if (pi == null) {
            return -1;
        }
        return pi.crtRTT;
    }

    public void setPeers(String[] peers) {
        synchronized (myPeers) {
            // cannot simply remove & add all peers because I would lose
            // the info gathered before
            ABPingEntry[] abse = standAlone ? null : MLLUSHelper.getInstance().getABPingEntries();

            for (String peer : peers) {
                if (myPeers.get(peer) == null) {
                    PeerInfo pi = new PeerInfo(this, peer, getFreePeerUID());
                    pi.port = 9000;
                    try {
                        setPortForName(pi.port, peer);
                    } catch (Throwable t) {
                    }
                    myPeers.put(peer, pi);
                    logger.log(Level.INFO, "ABPingFastReply: adding peer: " + peer);
                }//if null

                if ((abse != null) && (abse.length > 0)) {
                    PeerInfo pi = (PeerInfo) myPeers.get(peer);
                    if (pi != null) {
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, " Peers SIZE " + abse.length);
                        }
                        for (ABPingEntry abe : abse) {
                            if (logger.isLoggable(Level.FINEST)) {
                                logger.log(Level.FINEST, " ABE \n" + "\nHostName = " + abe.HostName
                                        + "\nFullHostName = " + abe.FullHostName + "\nIPAddress = " + abe.IPAddress);
                            }
                            if ((abe != null)
                                    && (peer.equalsIgnoreCase(abe.FullHostName) || peer.equalsIgnoreCase(abe.IPAddress) || peer
                                            .equalsIgnoreCase(abe.HostName))) {
                                pi.port = abe.PORT.intValue();
                                try {
                                    setPortForName(pi.port, peer);
                                } catch (Throwable t) {
                                }
                            }
                        }
                    }
                }//if abse != null

            }//for
             // check if the new config contains less peers
            if (myPeers.size() != peers.length) {
                for (Enumeration en = myPeers.keys(); en.hasMoreElements();) {
                    String p = (String) en.nextElement();
                    boolean found = false;
                    for (String peer : peers) {
                        if (p.equals(peer)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        myPeers.remove(p);
                        logger.log(Level.INFO, "ABPingFastReply: removing peer: " + p);
                    }
                }
            }
        }
    }

    /**
     * Put the measurement result in the resultCache myPeers
     * for it to be read by the monABPing module
     * 
     * @param rez	Result to be added to the cache
     * @throws an exception if the module cannot be initialized
     */
    public void fillResults(Result rez) throws Exception {
        boolean moduleActive;
        synchronized (syncActive) {
            // be sure to get the latest value 
            moduleActive = active;
        }
        if (!moduleActive) {
            checkPingers();
            try {
                // allow some time for initialization
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
            }
            synchronized (syncActive) {
                if (!active) {
                    throw new Exception("Module cannot be initialized");
                }
            }
        }
        synchronized (myPeers) {
            PeerInfo pi = (PeerInfo) myPeers.get(rez.NodeName);
            if (pi != null) {
                rez.param[0] = pi.crtRTime; // RTime
                rez.param[1] = pi.crtRTT;
                rez.param[2] = pi.crtJitter;
                rez.param[3] = pi.crtPacketLoss;
            }
        }
    }

    /**
     * Check, and if not start, the client and the server threads. 
     * This method provides support for stopping and
     * auto-restarting the module.
     */
    private void checkPingers() {
        synchronized (syncActive) {
            if (cli == null) {
                active = true;
                try {
                    sock = new DatagramSocket(PORT);
                } catch (SocketException ex) {
                    logger.log(Level.WARNING, "Cannot create listening ABPing socket. Refusing to start.", ex);
                    active = false;
                    return;
                }
                cli = new Client();
                Thread cliThread = new Thread(cli, "(ML) ABPingFastReplyClient for " + myName);
                try {
                    cliThread.setDaemon(true);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Cannot setDaemon", t);
                }
                cliThread.start();
            }
            if (serv == null) {
                active = true;
                serv = new Server();
                Thread servThread = new Thread(serv, "(ML) ABPingFastReplyServer for " + myName);
                try {
                    servThread.setDaemon(true);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Cannot setDaemon", t);
                }
                servThread.start();
            }
        }
    }

    /**
     * ABPing measuring utility.
     * (Client)
     * 
     * @author Catalin
     *
     */
    class Client implements Runnable {

        /**
         * Default constructor
         *
         */
        Client() {
        }

        /**
         * While ABPing Client is active, run ABPing algorithm 
         * between all the hosts in myPeers 
         */
        @Override
        public void run() {
            try {
                while (active) {
                    try {
                        // prepare
                        byte[] buf = new byte[PACKET_SIZE];
                        buf[0] = ECHO_PACKET;
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        // pinging my peers
                        synchronized (myPeers) {
                            for (Enumeration en = myPeers.keys(); en.hasMoreElements();) {
                                PeerInfo pi = (PeerInfo) myPeers.get(en.nextElement());
                                try {
                                    packet.setAddress(getInetAddr(pi.peerHost));
                                    packet.setPort(pi.port);
                                    pi.sendPacket(); // update counter for packet loss
                                    buf[1] = (byte) (pi.uid.intValue() & 0xff);
                                    buf[2] = (byte) ((pi.uid.intValue() >> 8) & 0xff);
                                    pi.sendTime = NTPDate.currentTimeMillis();
                                    sock.send(packet);
                                    if (logger.isLoggable(Level.FINEST)) {
                                        logger.log(Level.FINEST, "Client: sent packet uid=" + pi.uid);
                                    }
                                } catch (Exception e) {
                                    if (logger.isLoggable(Level.FINER)) {
                                        logger.log(Level.FINER, " Got ex", e);
                                    }
                                }
                            }
                        }
                        try {
                            Thread.sleep(PING_INTERVAL);
                        } catch (Exception ex) {
                        }
                        // get results
                        Hashtable tempHash = new Hashtable();
                        synchronized (results) {
                            tempHash.putAll(results); // keep the synchronized
                                                      // part as small
                            results.clear(); // as possible
                        }

                        synchronized (myPeers) {
                            for (Enumeration en = myPeers.keys(); en.hasMoreElements();) {
                                PeerInfo myPi = (PeerInfo) myPeers.get(en.nextElement());
                                Long recvTime = (Long) tempHash.get(myPi.uid);
                                if (recvTime != null) {
                                    // reply received from peer
                                    long rtt = recvTime.longValue() - myPi.sendTime;
                                    if (logger.isLoggable(Level.FINEST)) {
                                        logger.log(Level.FINEST, "Client: addRTT " + rtt);
                                    }
                                    myPi.addRTT(rtt);
                                } else {
                                    // packet lost from peer
                                    myPi.computePacketLoss();
                                }
                                if (logger.isLoggable(Level.FINEST)) {
                                    logger.log(Level.FINEST, "Status: " + myPi);
                                }
                            }
                        }
                        tempHash.clear();
                    } catch (Exception e) {
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, "Got Exception ", e);
                        }
                        try {
                            Thread.sleep(PING_INTERVAL);
                        } catch (Exception ex) {
                        }
                    }

                }
                logger.log(Level.INFO, "ABPingFastReply: Client: not active anymore, stopping...");
            } catch (Exception ex) {
                logger.log(Level.INFO, "Got Exception", ex);
            } finally {
                synchronized (syncActive) {
                    cli = null;
                    active = false;
                }
            }
        }
    }

    class Server implements Runnable {

        Server() {
        }

        @Override
        public void run() {
            byte[] buf = new byte[PACKET_SIZE];
            //byte [] rbuf;
            DatagramPacket packet;
            long recvTime;
            try {
                logger.log(Level.INFO, "ABPingServer: started listening...[ " + PORT + " ]");

                while (active) {
                    try {
                        if (buf.length != PACKET_SIZE) {
                            buf = new byte[PACKET_SIZE];
                        }
                        packet = new DatagramPacket(buf, buf.length);
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, "Server: waiting packet...");
                        }
                        sock.receive(packet);
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, "Server: received packet from "
                                    + Utils.getHostName(packet.getAddress().getHostAddress()));
                        }
                        //rbuf = packet.getData(); // is this necessary?
                        if (buf[0] == ECHO_PACKET) {
                            // reply packet ASAP
                            buf[0] = REPLY_PACKET;
                            // packet.setAddress(packet.getAddress()); // is this really necessary?
                            // packet.setPort(getPeerPort(packet.getAddress()));
                            sock.send(packet);
                        } else if (buf[0] == REPLY_PACKET) {
                            // queue packet for analisys
                            recvTime = NTPDate.currentTimeMillis();
                            synchronized (results) {
                                int uid = ((0xff & buf[2]) << 8) | (0xff & buf[1]);
                                results.put(Integer.valueOf(uid), Long.valueOf(recvTime));
                                if (logger.isLoggable(Level.FINEST)) {
                                    logger.log(Level.FINEST, "Server: added result uid=" + uid);
                                }
                            }
                        } else {
                            logger.log(Level.WARNING, "ABPingFastReply: Server: received unknown packet. ignoring..");
                        }
                    } catch (Exception ex) {
                        logger.log(Level.WARNING, "Got exception", ex);
                    }
                }
                logger.log(Level.INFO, "ABPingFastReply: Server: not active anymore. Stopping..");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Got exception", e);
            } finally {
                synchronized (syncActive) {
                    sock.close();
                    sock = null;
                    serv = null;
                    active = false;
                }
            }
        }
    }

    public static void main(String[] args) {
        String[] ResTypes = { "RTime", "RTT", "Jitter", "PacketLoss" };
        ABPingFastReply abp = new ABPingFastReply(args[0]);
        abp.standAlone = true;
        String[] peers = new String[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            peers[i - 1] = args[i];
        }
        abp.setPeers(peers);
        while (true) {
            try {
                System.out.println("Filling results...");
                for (String peerName : peers) {
                    Result rez = new Result("farm", "cluster", peerName, "ABPingFastReply", ResTypes);
                    abp.fillResults(rez);
                    rez.time = NTPDate.currentTimeMillis();
                    System.out.println(rez);
                }
            } catch (Exception ex) {
                System.out.println("Error filling results... module would be suspended.\n" + ex);
            }
            try {
                Thread.sleep(4000);
            } catch (InterruptedException iex) {
            }
        }
    }

}
