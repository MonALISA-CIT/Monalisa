/*
 * $Id: UDPSplitter.java 6865 2010-10-10 10:03:16Z ramiro $
 */

package lia.util.udp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 
 * Simple UDP Splitter
 * 
 * Can be used to listen on one UDP port and send the same datagram to other addresses:ports. 
 * <p>
 * 
 * <code>
 *  java lia.util.udp.UDPSplitter -l <listenPort> -f [inetAddr1:]port1 [ -f [inetAddr2:]port2 ... ]
 * </code>
 * 
 * @author ramiro
 */
public class UDPSplitter {

    private static final List<InetSocketAddress> fwdAddrs = new LinkedList<InetSocketAddress>();
    private static final BlockingQueue<DatagramPacket> queue = new ArrayBlockingQueue<DatagramPacket>(10000);
    private static final BlockingQueue<DatagramPacket> freeQueue = new ArrayBlockingQueue<DatagramPacket>(10000);
    
    //if you ever need monitoring ....
    private static final AtomicLong receivedDatagrams = new AtomicLong(0);

    private static void printHelp() {
        System.err.println("\n java lia.util.udp.UDPSplitter -l <listenPort> -f [inetAddr1:]port1 [ -f [inetAddr2:]port2 ... ] \n");
    }

    private static final class Sender extends Thread {

        private final DatagramSocket sendingSocket;

        Sender() throws Exception {
            setName(" [ UDPSplitter ] Sender Thread ");
            sendingSocket = new DatagramSocket();
        }

        public void run() {

            for (;;) {

                try {
                    final DatagramPacket packet = queue.take();
                    for (final InetSocketAddress ias : fwdAddrs) {
                        packet.setSocketAddress(ias);
                        sendingSocket.send(packet);
                    }

                    freeQueue.add(packet);

                } catch (Throwable t) {
                    System.err.println("Sender Thread got exception: ");
                    t.printStackTrace();
                }
            }
        }
    }

    public static final void main(String[] args) {

        int listenPort = -1;

        if (args == null || args.length < 4) {
            printHelp();
        }

        for (int i = 0; i < args.length; i++) {
            final String param = args[i];

            if (param.equals("-l")) {
                if (listenPort > 0) {
                    System.err.println("Multiple -l option");
                    printHelp();
                    System.exit(1);
                }

                try {
                    listenPort = Integer.parseInt(args[++i]);
                } catch (Throwable t) {
                    System.err.println(" Unable to parse the listening port ");
                    t.printStackTrace();
                    printHelp();
                    System.exit(-1);
                }
            }

            if (param.equals("-f")) {
                i++;

                if (i >= args.length) {
                    System.err.println("Illegal -f option");
                    printHelp();
                    System.exit(-1);
                }

                final String sInetAddr = args[i];
                int idx = sInetAddr.indexOf(":");

                String hostName = null;
                String sPort = null;

                int port = -1;

                if (idx >= 0) {
                    hostName = sInetAddr.substring(0, idx);
                    sPort = sInetAddr.substring(idx);
                } else {
                    hostName = "localhost";
                    sPort = sInetAddr;
                }

                try {
                    port = Integer.parseInt(sPort);
                } catch (Throwable t) {
                    System.err.print(" Unable to parse port number for param: " + sInetAddr);
                    t.printStackTrace();
                    printHelp();
                    System.exit(1);
                }

                try {
                    fwdAddrs.add(new InetSocketAddress(hostName, port));
                } catch (Throwable t) {
                    System.err.print(" Unable to parse InetSocketAddress for param: " + sInetAddr);
                    t.printStackTrace();
                    printHelp();
                    System.exit(1);
                }
            }
        }//end parse params

        if (fwdAddrs.size() <= 0) {
            System.err.println("At least one forwarding port/address (-f param) must be specified");
            printHelp();
            System.exit(1);
        }

        //Everything ok...
        System.out.print(" Listening on port: " + listenPort + " and forwading to the following addresses: ");
        for (InetSocketAddress isa : fwdAddrs) {
            System.out.print(isa + " ");
        }

        System.out.println();
        DatagramSocket listenSocket = null;

        try {
            listenSocket = new DatagramSocket(listenPort);
        } catch (Throwable t) {
            System.err.println(" Unable to bind the listening socket on port: " + listenPort);
            t.printStackTrace();
            System.exit(1);
        }

        try {
            new Sender().start();
        } catch (Throwable t) {
            System.err.println(" Unable to start the sender thread");
            t.printStackTrace();
            System.exit(1);
        }

        for (;;) {
            try {
                DatagramPacket dp = freeQueue.poll();
                if (dp == null) {
                    byte[] buff = new byte[8192];
                    dp = new DatagramPacket(buff, buff.length);
                }

                listenSocket.receive(dp);
                receivedDatagrams.incrementAndGet();

                queue.add(dp);

            } catch (Throwable t) {
                System.err.println(" Receiver got exception in main loop");
                t.printStackTrace();
            }
        }
    }
}
