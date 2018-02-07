package lia.util.TestClient;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Inflater;

import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmonMessage;
import lia.Monitor.monitor.monMessage;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.monitor.tcpConn;
import lia.Monitor.monitor.tcpConnNotifier;
import lia.util.ntp.NTPDate;

/**
 * Simple Test Client
 * It register itself with some predicates and wait for ML Response 
 */
public class TestClient extends Thread implements tcpConnNotifier {

    private static final Logger logger = Logger.getLogger(TestClient.class.getName());

    public static boolean finished = false;
    public static long endTime;
    public boolean hasToRun;
    public tcpConn tc;
    private final InetAddress ia;
    private final int port;
    private static final String usage = "java TestClient hostname(IP) port\n";
    private static int PREDIDs;
    private final Inflater decompresser = new Inflater();
    Vector results;

    private static final long TIMEOUT = 5 * 60 * 60 * 1000;//wait for results for 5 minutes

    //switches for received Data
    private static boolean gotLocalTime;
    private static boolean gotConf;
    private static boolean gotMLVersion;
    private static boolean gotResults;

    public TestClient(InetAddress ia, int port) {
        super(" (ML) TestClient ");
        this.ia = ia;
        this.port = port;
        this.tc = null;
        PREDIDs = 1;
        results = new Vector();

        gotLocalTime = false;
        gotConf = false;
        gotMLVersion = false;
        gotResults = false;

        start();
    }

    public void sendPredicates() {

        //monPredicate format
        //FarmName,ClusterName,NodeName, tmin, tmax, [param_list], [constraints]
        //Get the values for 'Load5' param for Cluster 'MonaLisa' for the LAST HOUR ( tmin in millis )
        monPredicate p = new monPredicate("*", "MonaLisa", "*", -5 * 60 * 60 * 1000, 0, new String[] { "Load5" }, null);
        sendPredicate(p);
    }

    private void sendPredicate(monPredicate p) {
        p.id = PREDIDs++;
        monMessage msg = new monMessage(monMessage.PREDICATE_REGISTER_TAG, Integer.valueOf(p.id), p);
        tc.sendMsg(msg);
    }

    @Override
    public void run() {

        try {
            tc = tcpConn.newConnection(this, ia, port);
            sendPredicates();
        } catch (Throwable t) {
            System.out.println("Could not instantiate connection");
            System.exit(1);
        }
        hasToRun = true;
        while (hasToRun) {
            try {
                Thread.sleep(100);
            } catch (Throwable t1) {
            }
            if (results.size() > 0) {
                Vector tmpV = new Vector();
                //keep small the sync section
                synchronized (results) {
                    tmpV.addAll(results);
                    results.clear();
                }//end sync
                for (int i = 0; i < tmpV.size(); i++) {
                    Object o = tmpV.elementAt(i);
                    if (o instanceof Vector) {
                        Vector v = (Vector) o;
                        if ((v != null) && (v.size() > 0) && (v.elementAt(0) instanceof Result)) {
                            logger.log(Level.INFO, "Got Results [ " + v.size() + " ] !");
                            gotResults = true;
                        }
                    } else if (o instanceof Result) {
                        logger.log(Level.INFO, "Got ONE Result from farm");
                        gotResults = true;
                    }
                }
            }

            if (gotConf && gotLocalTime && gotMLVersion && gotResults) {//everything seems OK!
                hasToRun = false;
                finished = true;
            }
        }//while
    }

    @Override
    public void notifyMessage(Object o) {

        if ((o == null) || !(o instanceof monMessage)) {
            logger.log(Level.SEVERE, "Got an unknown object", o);
        }
        monMessage msg = (monMessage) o;

        if ((msg.tag == null) && (msg.tag.length() == 0)) {
            logger.log(Level.WARNING, "Got a msg with unkown tag", msg.tag);
            return;
        }

        //Got the Time
        if (msg.tag.startsWith(monMessage.ML_TIME_TAG)) {
            if (!gotLocalTime) {
                logger.log(Level.INFO, "Got Local Time: " + msg.result);
                gotLocalTime = true;
            }
            return;
        }

        //Got the MLVersion
        if (msg.tag.startsWith(monMessage.ML_CONFIG_TAG)) {
            if (!(msg.result instanceof MFarm)) {
                logger.log(Level.INFO, "Got MLConfig: " + msg.result);
                return;
            }
            if (!gotConf) {
                newConfig((MFarm) msg.result);
                gotConf = true;
            }
            return;
        }

        //Got the MLVersion
        if (msg.tag.startsWith(monMessage.ML_VERSION_TAG)) {
            if (!gotMLVersion) {
                logger.log(Level.INFO, "Got MLVersion: " + msg.result);
                gotMLVersion = true;
            }
            return;
        }

        if (msg.tag.equals(monMessage.ML_RESULT_TAG)) {
            Object res = null;
            if (msg.result instanceof cmonMessage) {
                cmonMessage cm = (cmonMessage) msg.result;

                int initSize = cm.cbuff.length;
                // Decompress the bytes
                decompresser.reset();
                decompresser.setInput(cm.cbuff);
                byte[] result = new byte[cm.dSize];
                int resultLength = 0;
                try {
                    resultLength = decompresser.inflate(result);
                } catch (Throwable t) {
                    //  t.printStackTrace();
                    logger.log(Level.WARNING, " Got Exception while decompressing", t);
                }

                try {
                    ByteArrayInputStream bais = new ByteArrayInputStream(result);
                    ObjectInputStream ois = new ObjectInputStream(bais);
                    res = ois.readObject();
                    ois = null;
                    bais = null;
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " Got Exception while decompressing", t);
                }
            }
            results.add(res);
        }

    }

    private void newConfig(MFarm farm) {
        try {
            StringBuilder sb = new StringBuilder(1024);
            Vector clusterList = farm.getClusters();
            if (clusterList == null) {
                logger.log(Level.WARNING, "Got FarmConf with null clusterList");
                return;
            }
            for (int i = 0; i < clusterList.size(); i++) {
                MCluster mc = (MCluster) clusterList.elementAt(i);
                sb.append("\n\t" + mc.getName());

                Vector nodeList = mc.getNodes();
                for (int j = 0; j < nodeList.size(); j++) {
                    MNode mn = (MNode) nodeList.elementAt(j);
                    sb.append("\n\t\t" + mn.name);
                    Vector parameterList = mn.getParameterList();
                    if ((parameterList != null) && (parameterList.size() > 0)) {
                        sb.append(" [ ");
                        for (int k = 0; k < parameterList.size(); k++) {
                            sb.append(parameterList.elementAt(k) + ((k == (parameterList.size() - 1)) ? "" : ","));
                        }
                        sb.append(" ] ");
                    }
                }
            }
            logger.log(Level.INFO, "Conf for " + farm.name + ":" + sb.toString());
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got Exc processing farmConf", t);
        }
    }

    @Override
    public void notifyConnectionClosed() {

    }

    private static void printUsage() {
        System.out.println("\n" + usage + "\n");
    }

    public static void main(String[] args) {

        //verify the args
        if ((args == null) || (args.length != 2)) {
            printUsage();
            System.exit(1);
        }

        //ML addr and port
        InetAddress ia = null;
        int port = -1;

        try {
            ia = InetAddress.getByName(args[0]);
            port = Integer.valueOf(args[1]).intValue();
        } catch (Throwable t) {
            System.out.println("Could not determine the hostname or the port");
            System.exit(-1);
        }
        TestClient tc = null;
        try {
            tc = new TestClient(ia, port);
        } catch (Throwable t) {
            System.out.println("ERROR! Cannot connect to " + args[0] + ":" + port);
        }

        endTime = NTPDate.currentTimeMillis() + TIMEOUT;

        while (!finished) {
            try {
                Thread.sleep(100);
            } catch (Throwable t) {

            }
            if (NTPDate.currentTimeMillis() > endTime) {
                tc.hasToRun = false;
                finished = true;
            }
        }

        if (gotConf && gotLocalTime && gotMLVersion && gotResults) {//everything seems OK!
            logger.log(Level.INFO, "\n\n Everything SEEMS OK!");
            System.exit(0);
        }

        if (!gotConf) {
            logger.log(Level.INFO, "\n\n Did NOT get FarmConf!");
            System.exit(1);
        }

        if (!gotMLVersion) {
            logger.log(Level.INFO, "\n\n Did NOT get MLVersion!");
            System.exit(1);
        }

        if (!gotLocalTime) {
            logger.log(Level.INFO, "\n\n Did NOT get LocalTime!");
            System.exit(1);
        }

        if (!gotResults) {
            logger.log(Level.INFO, "\n\n Did NOT get Results!");
            System.exit(1);
        }
    }
}
