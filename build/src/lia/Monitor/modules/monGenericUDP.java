/*
 * $Id: monGenericUDP.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.modules;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.GenericUDPResult;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.util.DynamicThreadPoll.SchJob;

/**
 * 
 * @author ramiro
 * @author Costin Grigoras
 * @author Catalin Cirstoiu
 * 
 */
public abstract class monGenericUDP extends SchJob implements MonitoringModule, GenericUDPNotifier {

    /**
     * 
     */
    private static final long serialVersionUID = 3158836250458493949L;
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monGenericUDP.class.getName());
    public MNode Node;
    public String TaskName;
    public MonModuleInfo info;
    static public String ModuleName = "monGenericUDP";
    static public String OsName = "*";
    String[] resTypes = null;
    public boolean isRepetitive = false;
    InetAddress gAddress = null;
    int gPort = 8889;
    int maxMsgRate = 50; // maximum rate of messages accepted from a sender
    GenericUDPListener udpLS = null;
    volatile UDPAccessConf accessConf = null;
    long last_measured = -1;
    boolean debug = false;
    protected final Vector<GenericUDPResult> genResults;
    protected boolean bAppendIPToNodeName = false;
    protected boolean bReportSenderID = false;

    public monGenericUDP(String TaskName) {
        isRepetitive = true;
        genResults = new Vector<GenericUDPResult>();
        this.TaskName = TaskName;
        info = new MonModuleInfo();
        info.name = TaskName;
    }

    @Override
    public MonModuleInfo init(MNode Node, String arg) {
        this.Node = Node;
        init_args(arg);
        info = new MonModuleInfo();

        try {
            udpLS = new GenericUDPListener(gPort, this, null);
        } catch (Throwable tt) {
            logger.log(Level.WARNING, " Cannot create UDPListener !", tt);
        }
        udpLS.setMaxMsgRate(maxMsgRate);

        isRepetitive = true;

        info.ResTypes = resTypes;
        info.name = ModuleName;

        return info;
    }

    void init_args(String list) {
        if ((list == null) || (list.length() == 0)) {
            return;
        }
        String params[] = list.split("(\\s)*,(\\s)*");
        if ((params == null) || (params.length == 0)) {
            return;
        }
        for (String param : params) {
            int itmp = param.indexOf("ListenPort");
            if (itmp != -1) {
                String tmp = param.substring(itmp + "ListenPort".length()).trim();
                int iq = tmp.indexOf("=");
                String port = tmp.substring(iq + 1).trim();
                try {
                    gPort = Integer.valueOf(port).intValue();
                } catch (Throwable tt) {
                    //gPort = 8889; // catac: the default port is set already (in constructor).
                }
                continue;
            }

            itmp = param.indexOf("AppendIPToNodeName");
            if (itmp != -1) {
                String tmp = param.substring(itmp + "AppendIPToNodeName".length()).trim();
                int iq = tmp.indexOf("=");

                String val = "";

                if (iq > 0) {
                    val = tmp.substring(iq + 1).trim().toLowerCase();
                    // if the parameter exists the default value is true unless an explicit value of false is specified
                }
                if (val.startsWith("f") || val.startsWith("0")) {
                    bAppendIPToNodeName = false;
                } else {
                    bAppendIPToNodeName = true;
                }
                continue;
            }

            itmp = param.indexOf("ReportSenderID");
            if (itmp != -1) {
                String tmp = param.substring(itmp + "ReportSenderID".length()).trim();
                int iq = tmp.indexOf("=");

                String val = "";

                if (iq > 0) {
                    val = tmp.substring(iq + 1).trim().toLowerCase();
                    // if the parameter exists the default value is true unless an explicit value of false is specified
                }
                if (val.startsWith("f") || val.startsWith("0")) {
                    bReportSenderID = false;
                } else {
                    bReportSenderID = true;
                }
                continue;
            }

            itmp = param.indexOf("MaxMsgRate");
            if (itmp != -1) {
                String tmp = param.substring(itmp + "MaxMsgRate".length()).trim();
                int iq = tmp.indexOf("=");
                String rate = tmp.substring(iq + 1).trim();
                try {
                    maxMsgRate = Integer.valueOf(rate).intValue();
                } catch (Throwable tt) {
                    // already defined
                }

            }

            itmp = param.indexOf("AccessConfFile");
            if (itmp != -1) {

                File accessConfFile = null;

                String tmp = param.substring(itmp + "AccessConfFile".length()).trim();
                int iq = tmp.indexOf("=");
                String sCFile = tmp.substring(iq + 1).trim();
                if ((sCFile != null) && (sCFile.length() > 0)) {
                    try {
                        accessConfFile = new File(sCFile);
                    } catch (Throwable tt) {
                        logger.log(Level.WARNING, "[ monGenericUDP ] Got exception while initializing AccessConFile",
                                tt);
                        accessConfFile = null;
                    }
                } else {
                    logger.log(Level.WARNING,
                            "[ monGenericUDP ] Please make sure that you have defined a valid file name after AccessConfFile = [ "
                                    + sCFile + " ] ");
                    return;
                }

                try {
                    accessConf = new UDPAccessConf(accessConfFile);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " [ monGenericUDP ] Unable to instantiate UDPAccessConf", t);
                    accessConf = null;
                }

            }
        }
    }

    @Override
    public String[] ResTypes() {
        return resTypes;
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
        return ModuleName;
    }

    @Override
    public boolean isRepetitive() {
        return isRepetitive;
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    @Override
    abstract public void notifyData(int len, byte[] data, InetAddress source);

    public List<GenericUDPResult> getResults() {
        if ((genResults == null) || (genResults.size() == 0)) {
            return null;
        }

        List<GenericUDPResult> rList = null;

        synchronized (genResults) {
            rList = new ArrayList<GenericUDPResult>(genResults);
            genResults.clear();
        }

        return rList;
    }
}