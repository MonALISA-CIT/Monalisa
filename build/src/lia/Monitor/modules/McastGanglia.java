package lia.Monitor.modules;

import hep.io.xdr.XDRInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.Result;
import lia.util.ntp.NTPDate;

public class McastGanglia extends Thread {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(McastGanglia.class.getName());

    protected MulticastSocket socket = null;

    protected volatile boolean hasToRun;

    final byte[] buf = new byte[8192];

    private final MNode Node;

    private final String Module;

    private final Vector<Result> rezBuff = new Vector<Result>();

    static MGangliaMetrics gm = new MGangliaMetrics();

    public McastGanglia(InetAddress group, int port, MNode Node, String Module) throws IOException {
        this.Node = Node;
        this.Module = Module;
        socket = new MulticastSocket(port);
        socket.joinGroup(group);
        if (socket == null) {
            System.out.println(" s = null ");
            System.exit(-1);
        }
        hasToRun = true;
        start();

    }

    @Override
    public void run() {
        DatagramPacket packet = new DatagramPacket(buf, buf.length);

        while (hasToRun) {
            try {
                socket.receive(packet);
                final InetAddress address = packet.getAddress();
                final int len = packet.getLength();
                if (logger.isLoggable(Level.FINEST)) {
                    logger.finest(" [ McastGanglia ] recived pkt from: " + address + " len=" + len);
                }
                final byte[] data = packet.getData();
                XDRInputStream xdrIS = new XDRInputStream(new ByteArrayInputStream(data));

                final int key = xdrIS.readInt();
                xdrIS.pad();

                final Result r = new Result();
                r.NodeName = address.getHostName();
                r.FarmName = Node.getFarmName();
                r.ClusterName = Node.getClusterName();
                r.Module = Module;
                r.time = NTPDate.currentTimeMillis();

                if (key == 0) {
                    // custom gmetric
                    final String type = xdrIS.readString();
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.finest(" [ McastGanglia ] custom packet type: " + type);
                    }
                    xdrIS.pad();
                    final String name = xdrIS.readString();
                    xdrIS.pad();
                    final String value = xdrIS.readString();
                    r.addSet(name, Double.parseDouble(value));
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, " [ McastGanglia ] User defined result: " + r);
                    }
                    rezBuff.add(r);
                    continue;
                } else if ((key >= MGangliaMetrics.gMetrics.length) || (key < 0)) {
                    logger.log(Level.INFO, "No such metric. key = " + key);
                } else if ((key < MGangliaMetrics.MIN_RESERVED_KEY) || (key > MGangliaMetrics.MAX_RESERVED_KEY)) {
                    String gModule = MGangliaMetrics.gMetrics[key];
                    Ganglia2MLMetric g2ml = (Ganglia2MLMetric) gm.g2mlh.get(gModule);
                    if (g2ml != null) {
                        // System.out.println(" [ " + address.getHostName() + " ] " + key + " gm " + gModule + " mlm " +
                        // g2ml.mlMetric );
                        double value = 0;
                        switch (g2ml.xdrType) {
                        case XDRMLMappings.XDR_INT16:
                            value = xdrIS.readInt();
                            xdrIS.pad();
                            break;
                        case XDRMLMappings.XDR_INT32:
                            value = xdrIS.readInt();
                            xdrIS.pad();
                            break;
                        case XDRMLMappings.XDR_INT64:
                            value = xdrIS.readLong();
                            xdrIS.pad();
                            break;
                        case XDRMLMappings.XDR_REAL32:
                            value = xdrIS.readFloat();
                            xdrIS.pad();
                            break;
                        case XDRMLMappings.XDR_REAL64:
                            value = xdrIS.readDouble();
                            xdrIS.pad();
                            break;
                        }

                        // Quick Fix for memory and Network
                        if (g2ml.gMetric.equals("bytes_in") || g2ml.gMetric.equals("bytes_out")) {
                            value = (value * 8.0D) / (1000.0D * 1000.0D);// Mb
                        } else if (g2ml.gMetric.indexOf("mem_") != -1) {
                            value /= 1024.0D;// MB
                        }
                        r.addSet(g2ml.mlMetric, value);
                        rezBuff.add(r);
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ McastGanglia ] got exception ", t);
            }
        }
        socket.close();
    }

    public Collection<Result> getResults() {
        synchronized (rezBuff) {
            try {
                if (rezBuff.size() == 0) {
                    return Collections.EMPTY_LIST;
                }
                return new ArrayList<Result>(rezBuff);
            } finally {
                rezBuff.clear();
            }
        }
    }

    public static void main(String[] args) {
        try {
            new McastGanglia(InetAddress.getByName("239.2.11.71"), 8649, new MNode("localhost", null, null),
                    "monMcastGanlia");
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
    }
}
