package lia.Monitor.Farm.ABPing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.Result;
import lia.util.ntp.NTPDate;

/**
 * ABPing = Available Bandwidth Ping
 * 
 * ABPing tries to estimate the available bandwidth of a link
 * measuring RTT, delay, loss, jitter of UDP packages sent over the
 * link.
 * 
 * It is both a client and a server and both are required in order
 * to work.
 * 
 */

public class ABPing {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(ABPing.class.getName());

    private static int PORT = 9001;
    private static int MAX_PACKET_SIZE = 1000;
    private static int PING_INTERVAL = 1000;
    private static double JITTER_COEF = 0.2; // how important is the last result
    private static double RTT_COEF = 0.1; // (from 0 to 1)
    private static double LOSS_COEF = 0.2;

    private final Client cli;
    private final Server serv;
    public boolean active;

    public String myName;
    public Hashtable myPeers;
    public Hashtable replyTo;
    public Hashtable results;

    public ABPing(String[] peers) {
        this.myPeers = new Hashtable();
        this.replyTo = new Hashtable();
        this.results = new Hashtable();

        active = true;
        myName = peers[0];
        for (int i = 1; i < peers.length; i++) {
            this.myPeers.put(peers[i], new ABPeerInfo(myName, peers[i]));
        }

        cli = new Client();
        Thread cliThread = new Thread(cli, "(ML) ABPingClient for " + myName);
        try {
            cliThread.setDaemon(true);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Cannot setDaemon", t);
        }

        cliThread.start();

        serv = new Server();
        Thread servThread = new Thread(serv, "(ML) ABPingServer for " + myName);
        try {
            servThread.setDaemon(true);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Cannot setDaemon", t);
        }

        servThread.start();

    }

    public void fillResults(Result rez) {
        ABPeerInfo pi = (ABPeerInfo) myPeers.get(rez.NodeName);
        rez.param[0] = pi.quality;
        rez.param[1] = pi.jitter;
        rez.param[2] = pi.rtt;
        rez.param[3] = pi.loss;
    }

    class Client implements Runnable {
        Hashtable addrCache;
        DatagramSocket sock;
        long timeDiffSend = 0;
        long sendTime;

        Client() {
            try {
                addrCache = new Hashtable();
                sock = new DatagramSocket();
                timeDiffSend = 0;
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }

        InetAddress getInetAddr(String name) throws UnknownHostException {
            InetAddress a = (InetAddress) addrCache.get(name);
            if (a == null) {
                a = InetAddress.getByName(name);
                addrCache.put(name, a);
            }
            return a;
        }

        /**
         * Send a packet to peer
         * @param pi 
         * 		packet to send
         * @param to 
         * 		peer (hostname/address)
         * @return 
         * 		size of the packet sent
         * @throws IOException
         */
        int sendPacket(ABPeerInfo pi, String to) throws IOException {
            ByteArrayOutputStream bas = new ByteArrayOutputStream();
            ObjectOutputStream os = new ObjectOutputStream(bas);

            os.writeObject(pi);
            byte[] buf = bas.toByteArray();
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            packet.setAddress(getInetAddr(to));
            packet.setPort(PORT);
            sock.send(packet);
            os.close();
            bas.close();
            return buf.length;
        }

        /**
         * Send a packet of specified size to the peer
         * @param pi 
         * 		packet to send
         * @param to
         * 		peer (hostname/address)
         * @param size
         * 		desired size for the packet
         * @return
         * 		actual size of the packet sent
         * @throws IOException
         */
        int sendPacket(ABPeerInfo pi, String to, int size) throws IOException {
            ByteArrayOutputStream bas = new ByteArrayOutputStream();
            ObjectOutputStream os = new ObjectOutputStream(bas);

            os.writeObject(pi);
            byte[] buf = bas.toByteArray();
            byte[] buf2 = new byte[Math.max(buf.length, size)];
            int i;
            for (i = 0; i < buf.length; i++) {
                buf2[i] = buf[i];
            }
            // the rest of the packet must be filled with random data to avoid
            // compression over the network
            for (; i < buf2.length; i++) {
                buf2[i] = (byte) Math.round(Math.random() * 255);
            }
            DatagramPacket packet = new DatagramPacket(buf2, buf2.length);
            packet.setAddress(getInetAddr(to));
            packet.setPort(PORT);
            sock.send(packet);
            os.close();
            bas.close();
            return buf2.length;
        }

        @Override
        public void run() {
            try {
                while (active) {
                    // pinging my peers
                    for (Enumeration en = myPeers.keys(); en.hasMoreElements();) {
                        ABPeerInfo pi = (ABPeerInfo) myPeers.get(en.nextElement());
                        pi.seqNr++;
                        // set other information
                        pi.sendTime = (sendTime = NTPDate.currentTimeMillis()) + timeDiffSend;
                        int size = sendPacket(pi, pi.retFrom, 900);
                        timeDiffSend = NTPDate.currentTimeMillis() - sendTime;
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, "ABPing: sending : " + pi + " size= " + size);
                        }
                    }

                    Thread.sleep(PING_INTERVAL);

                    // replying peers
                    Hashtable tempHash = new Hashtable();
                    synchronized (replyTo) {
                        tempHash.putAll(replyTo); // keep the synchronized part as small
                        replyTo.clear(); // as possible
                    }

                    for (Enumeration en = tempHash.keys(); en.hasMoreElements();) {
                        ABPeerInfo pi = (ABPeerInfo) tempHash.get(en.nextElement());
                        // set other information
                        // set time spent at peer (i am the peer)
                        pi.rtt = ((sendTime = NTPDate.currentTimeMillis()) - pi.timeDiff) + timeDiffSend;
                        int size = sendPacket(pi, pi.sender, 900);
                        timeDiffSend = NTPDate.currentTimeMillis() - sendTime;
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, "ABPing: replying: " + pi + " size= " + size);
                        }
                    }
                    tempHash.clear();

                    // checking returned packets = results
                    synchronized (results) {
                        tempHash.putAll(results);
                        results.clear();
                    }

                    for (Enumeration en = myPeers.keys(); en.hasMoreElements();) {
                        ABPeerInfo myPi = (ABPeerInfo) myPeers.get(en.nextElement());
                        ABPeerInfo pi = (ABPeerInfo) tempHash.get(myPi.retFrom);
                        if (pi != null) {
                            double rtt = pi.timeDiff - pi.sendTime - pi.rtt; // round trip time
                            if (rtt < 0) {
                                rtt = 0;
                            }
                            myPi.jitter = (JITTER_COEF * Math.abs(rtt - myPi.rtt)) + ((1 - JITTER_COEF) * myPi.jitter);
                            myPi.rtt = rtt;
                            myPi.delay = myPi.rtt / 2;
                            double loss = pi.seqNr - myPi.awaitedSeqNr - (myPi.seqNr - myPi.awaitedSeqNr);
                            myPi.loss = (LOSS_COEF * loss) + ((1 - LOSS_COEF) * myPi.loss);
                            if (myPi.loss < 0) {
                                myPi.loss = 0;
                            }
                            myPi.awaitedSeqNr = pi.seqNr;
                            if (logger.isLoggable(Level.FINEST)) {
                                logger.log(Level.FINEST, "ABPing: result: " + myPi);
                            }
                        } else {
                            myPi.loss += LOSS_COEF;
                            if (logger.isLoggable(Level.FINEST)) {
                                logger.log(Level.FINEST, "ABPing: lost ?: " + myPi);
                            }
                        }
                        myPi.computeQuality();
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, "ABPing: " + myPi);
                        }
                    }
                    tempHash.clear();
                }
            } catch (Exception ex) {
                //                ex.printStackTrace();
                logger.log(Level.WARNING, "Got exception", ex);
            }
        }
    }

    class Server implements Runnable {

        Server() {
        }

        @Override
        public void run() {
            byte[] buf = new byte[MAX_PACKET_SIZE];
            DatagramSocket sock;
            DatagramPacket packet;
            ObjectInputStream os;
            long timeDiffRecv = 0;
            try {
                sock = new DatagramSocket(PORT);
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "ABPing: Server: started listening...");
                }
                while (active) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "Server: waiting packet...");
                    }
                    packet = new DatagramPacket(buf, buf.length);
                    sock.receive(packet);
                    timeDiffRecv = NTPDate.currentTimeMillis();
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "Server: got packet! size= " + packet.getLength());
                    }
                    if (MAX_PACKET_SIZE == packet.getLength()) {
                        logger.log(Level.WARNING, "Server: received packet truncated at " + MAX_PACKET_SIZE);
                    }
                    os = new ObjectInputStream(new ByteArrayInputStream(packet.getData()));
                    ABPeerInfo pi = (ABPeerInfo) os.readObject();
                    pi.timeDiff = timeDiffRecv;
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "Server: received " + pi);
                    }
                    if (pi.sender.equals(myName)) {
                        synchronized (results) {
                            results.put(pi.retFrom, pi);
                        }
                    } else {
                        synchronized (replyTo) {
                            replyTo.put(pi.sender, pi);
                        }
                    }
                    os.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {

        ABPing abp = new ABPing(args);
    }

}
