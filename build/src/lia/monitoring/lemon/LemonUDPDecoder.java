/*
 * $Id: LemonUDPDecoder.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.monitoring.lemon;

import java.io.File;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.monitoring.lemon.conf.LemonMetricFields;
import lia.monitoring.lemon.conf.MLLemonConf;
import lia.monitoring.lemon.conf.MLLemonConfProvider;

/**
 * 
 * The Lemon UDP decoder
 * 
 * @author ramiro
 * 
 */
public abstract class LemonUDPDecoder extends cmdExec implements MonitoringModule, GenericUDPNotifier, Observer {

    /**
     * 
     */
    private static final long serialVersionUID = -7049184371000984639L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(LemonUDPDecoder.class.getName());

    public MonModuleInfo info;

    static public String OsName = "Linux";

    String[] types;

    private boolean packetLogged = false;

    public boolean isRepetitive = false;

    InetAddress gAddress = null;

    int gPort = 12509;

    GenericUDPListener udpLS = null;

    boolean debug = false;
    Vector genResults;
    MLLemonConfProvider mlcp = null;
    MLLemonConf conf;

    String MLLemonConfFile;

    private static final Pattern SPACE_PATTERN = Pattern.compile("(\\s)+");
    private static final Pattern POUND_PATTERN = Pattern.compile("#");

    public LemonUDPDecoder(String TaskName) {
        isRepetitive = true;
        this.TaskName = TaskName;
        info = new MonModuleInfo();
        info.name = TaskName;
    }

    @Override
    public MonModuleInfo init(MNode Node, String arg) {
        this.Node = Node;
        init_args(arg);
        info = new MonModuleInfo();

        genResults = new Vector();

        isRepetitive = true;

        try {
            udpLS = new GenericUDPListener(gPort, this, null);
        } catch (Throwable tt) {
            logger.log(Level.WARNING, " Cannot create UDPListener !", tt);
        }

        info.ResTypes = types;

        try {
            mlcp = new MLLemonConfProvider(new File(MLLemonConfFile));
        } catch (Throwable t) {
            logger.log(Level.WARNING, " LemonUDPDecoder:- Got Exc", t);
            mlcp = null;
        }

        if (mlcp != null) {
            mlcp.addObserver(this);
            conf = mlcp.getConf();
        }

        return info;
    }

    private String getValueForKey(String row, String key) {
        if ((row == null) || (key == null) || (row.indexOf(key) == -1)) {
            return null;
        }
        int itmp = row.indexOf(key);
        String tmp = row.substring(itmp + key.length()).trim();
        int iq = tmp.indexOf("=");
        return tmp.substring(iq + 1).trim();
    }

    void init_args(String list) {

        if ((list != null) || (list.length() > 0)) {

            String params[] = list.split("(\\s)*,(\\s)*");
            if ((params != null) || (params.length > 0)) {
                for (String param : params) {
                    String sPort = getValueForKey(param, "ListenPort");
                    if (sPort != null) {
                        try {
                            gPort = Integer.valueOf(sPort).intValue();
                        } catch (Throwable tt) {
                            gPort = 12509;
                        }
                        continue;
                    }

                    MLLemonConfFile = getValueForKey(param, "MLLemonConfFile");
                    if (MLLemonConfFile != null) {
                        break;
                    }
                }
            }

        }

        if (MLLemonConfFile == null) {
            logger.log(Level.WARNING, "No MLLemonConfFile in parameters ... using default");
            MLLemonConfFile = "/var/monalemon/MonaLisa/Service/usr_code/Lemon/conf/MLLemonConfFile";
        }

        logger.log(Level.INFO, "\n\nUsing \n[" + MLLemonConfFile + "]\n as MLLemonConfFile\n\n");
    }

    @Override
    public String[] ResTypes() {
        return types;
    }

    @Override
    public String getOsName() {
        return OsName;
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
        return TaskName;
    }

    @Override
    public boolean isRepetitive() {
        return isRepetitive;
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    private void logPacket(int len, String sData) {
        if (packetLogged) {
            logger.log(Level.WARNING, "LemonUDPDecoder: logpacket(): Packet already logged");
            packetLogged = true;
            return;
        }
        logger.log(Level.WARNING, "LemonUDPDecoder: logpacket():  len [" + len + "] data = \n" + sData + "\n");
    }

    @Override
    public void notifyData(int len, byte[] data) {
        packetLogged = false;
        if ((conf == null) || (conf.getIDPrefML() == null) || (conf.getLemonConf() == null)) {
            return;
        }

        //
        //   The old protocol looks like this:
        //      A0 0 hostname#metric_id1 values#metric_id2 values#...#
        //  The new protocol looks like this:
        //      A1 0 hostname1#metric_id values#hostname2#metric_id2 values#...#
        //

        CharBuffer cb = ByteBuffer.wrap(data).asCharBuffer();
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ LemonUDPDecoder ] rcv datagram: " + cb);
        }

        String sData = new String(data, 0, len - 1);

        try {
            if (sData != null) {
                //                String[] tk = sData.split("#");

                final String[] tk = POUND_PATTERN.split(sData);

                if ((tk == null) || (tk.length < 2)) {
                    logger.log(Level.WARNING, "data should have at least 2 records ... Ignoring packet!!");
                    if (packetLogged) {
                        logger.log(Level.WARNING, "LemonUDPDecoder: logpacket(): Packet already logged");
                    } else {
                        logPacket(len, sData);
                    }
                    return;
                }

                //first record should be: A0 0 <hostname>
                //get the hostname from this first line
                //                String[] splitData = tk[0].split("(\\s)+");
                final String[] splitData = SPACE_PATTERN.split(tk[0]);

                if ((splitData == null) || (splitData.length != 3)) {
                    logger.log(Level.WARNING, "Invalid HEADER...should have at least 3 tokens in first record ... ");
                    if (packetLogged) {
                        logger.log(Level.WARNING, "LemonUDPDecoder: logpacket(): Packet already logged");
                    } else {
                        logPacket(len, sData);
                    }
                    return;
                }

                String nodeName = splitData[2];
                if (nodeName != null) {
                    nodeName = nodeName.trim();
                } else {
                    return;
                }

                String clusterName = conf.getCluster(nodeName);
                if ((clusterName == null) || (clusterName.trim().length() == 0)) {
                    clusterName = "UnMapped";
                }
                for (int i = 1; i < tk.length; i++) {
                    String row = tk[i].trim();
                    if ((row == null) || (row.length() == 0)) {
                        logger.log(Level.WARNING, " Row [ " + i + " ] = " + row + " is 0 Length");
                        if (packetLogged) {
                            logger.log(Level.WARNING, "LemonUDPDecoder: logpacket(): Packet already logged");
                        } else {
                            logPacket(len, sData);
                        }
                        continue;
                    }

                    //                    String[] rowTK = row.split("(\\s)+");
                    final String[] rowTK = SPACE_PATTERN.split(row);
                    if ((rowTK == null) || (rowTK.length <= 2)) {
                        logger.log(Level.WARNING, " Row [ " + i + " ] = " + row
                                + " is too small!!! Should have at least 3 tokens");
                        if (packetLogged) {
                            logger.log(Level.WARNING, "LemonUDPDecoder: logpacket(): Packet already logged");
                        } else {
                            logPacket(len, sData);
                        }
                        continue;
                    }

                    String sID = rowTK[0];
                    long pTime = -1;
                    try {
                        pTime = Long.valueOf(rowTK[1]).longValue() * 1000;
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " Row [ " + i + " ] = " + row
                                + " Error parsing time! Ignoring it....");
                        if (packetLogged) {
                            logger.log(Level.WARNING, "LemonUDPDecoder: logpacket(): Packet already logged");
                        } else {
                            logPacket(len, sData);
                        }
                        continue;
                    }
                    Integer LemonID = null;
                    try {
                        LemonID = Integer.valueOf(sID);
                    } catch (Throwable t) {
                        LemonID = null;
                    }

                    if (LemonID == null) {
                        logger.log(Level.WARNING, " Row [ " + i + " ] = " + row + " has no valid ID [" + sID + "] !!!");
                        if (packetLogged) {
                            logger.log(Level.WARNING, "LemonUDPDecoder: logpacket(): Packet already logged");
                        } else {
                            logPacket(len, sData);
                        }
                        continue;
                    }

                    /*
                     * there are 3 posibilities:
                     * 
                     *  1) null - we are not interested in this parameter
                     *  2) "" - o length string ... leave the param name as it is
                     *  3) "prefix" - add "prefix" to parameter name
                     */
                    String mlParamPrefix = conf.getIDPrefix(sID);

                    if (mlParamPrefix == null) {//not interested continue
                        continue;
                    }

                    LemonMetricFields lmf = conf.getMetricFields(LemonID);
                    if ((lmf == null) || (lmf.fieldNames == null) || (lmf.fieldTypes == null)
                            || (lmf.fieldNames.length != (rowTK.length - 2))) {
                        logger.log(Level.WARNING, " Row [ " + i + " ] = " + row
                                + " has no valid LemonMetricField for ID [" + sID + "] !!!");
                        if (packetLogged) {
                            logger.log(Level.WARNING, "LemonUDPDecoder: logpacket(): Packet already logged");
                        } else {
                            logPacket(len, sData);
                        }
                        continue;
                    }

                    Result r = new Result();

                    r.FarmName = getFarmName();
                    r.ClusterName = clusterName;
                    r.NodeName = nodeName;
                    r.Module = TaskName;
                    r.time = pTime;
                    for (int iparam = 0; iparam < lmf.fieldNames.length; iparam++) {
                        if ((lmf.fieldTypes[iparam] == LemonMetricFields.INTEGER)
                                || (lmf.fieldTypes[iparam] == LemonMetricFields.FLOAT)) {
                            String lemonParam = lmf.fieldNames[iparam];
                            String tName = conf.getTranslatedParam(lemonParam);
                            if (tName == null) {
                                tName = lemonParam;
                            }
                            try {
                                r.addSet(mlParamPrefix + tName, Double.valueOf(rowTK[iparam + 2]).doubleValue());
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, " LemonUDPDecoder:- Got Exc", t);
                            }
                        }
                    }
                    genResults.add(r);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " LemonUDPDecoder:- Got Exc", t);
        }

    }

    @Override
    public void update(Observable o, Object arg) {
        if (mlcp != null) {
            conf = mlcp.getConf();
        }
    }

    public Object getResults() {
        if ((genResults == null) || (genResults.size() == 0)) {
            return null;
        }
        Vector rV = null;
        synchronized (genResults) {
            rV = new Vector(genResults.size());
            rV.addAll(genResults);
            genResults.clear();
        }
        return rV;
    }

}
