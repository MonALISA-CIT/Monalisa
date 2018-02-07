/*
 * $Id: monCienaOCn.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.modules;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ciena.tl1.TL1Response;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.util.DateFileWatchdog;
import lia.util.Utils;
import lia.util.ntp.NTPDate;
import lia.util.telnet.CienaTelnet;

/**
 * Started as OC192 interface monitoring.
 * 
 * @author ramiro
 */
public class monCienaOCn extends cmdExec implements MonitoringModule, Observer {

    private static final long serialVersionUID = 7792514221235348247L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monCienaOCn.class.getName());

    private static final String TL1_CTAG = "rocn";

    private static final String TL1_CMD = "rtrv-ocn::" + TL1_CTAG + ";\n";

    public MNode Node;

    public MonModuleInfo info;

    private final String moduleName;

    private volatile CienaTelnet cienaTL1Conn;

    private File oc192ConfFile = null;

    private static final AtomicReference clusterNameOC192Alm = new AtomicReference();

    static String[] ResTypes = { "Port-Conn" };

    public static final String OC192_TAG = "OC192";

    private final ConcurrentSkipListMap oc192InterfacesMap = new ConcurrentSkipListMap();

    public monCienaOCn() {
        moduleName = "monCienaAlm";
        isRepetitive = true;
    }

    @Override
    public MonModuleInfo init(MNode Node, String arg) {
        this.Node = Node;
        logger.log(Level.INFO, "monCienaAlm: farmName=" + Node.getFarmName() + " clusterName= " + Node.getClusterName()
                + " nodeName=" + Node.getName() + " arg = " + arg);

        if (arg.startsWith("\"")) {
            arg = arg.substring(1);
        }

        if (arg.endsWith("\"")) {
            arg = arg.substring(0, arg.length() - 1);
        }

        String[] args = arg.split("(\\s)*;(\\s)*");
        if (args != null) {
            for (String arg2 : args) {
                try {
                    final String argT = arg2.trim();
                    if (argT.startsWith("OC192ConfFile")) {
                        String[] fileTks = argT.split("(\\s)*=(\\s)*");
                        if ((fileTks == null) || (fileTks.length != 2)) {
                            logger.log(Level.WARNING, "monCienaAlm cannot parse OC192ConfFile param [ " + argT
                                    + " ]  ... ");
                            continue;
                        }
                        oc192ConfFile = new File(fileTks[1]);

                        if (oc192ConfFile.isFile() && oc192ConfFile.canRead()) {
                            try {
                                DateFileWatchdog.getInstance(oc192ConfFile, 5 * 1000).addObserver(this);
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, " monCienaAlm - Unable to instantiate watchdog for "
                                        + oc192ConfFile + ". Cause: ", t);
                            }
                        } else {
                            logger.log(
                                    Level.WARNING,
                                    "\n\n monCienaAlm - No OC192ConfFile defined or the file "
                                            + oc192ConfFile
                                            + " cannot be accessed. The module WILL NOT REPORT status on Sonet Intetrfaces!\n\n");
                            oc192ConfFile = null;
                        }
                    }

                } catch (Throwable t) {
                    logger.log(Level.WARNING, "monCienaAlm - exception parsing module params params", t);
                }
            }
        }

        info = new MonModuleInfo();
        info.name = moduleName;
        info.ResTypes = ResTypes;

        try {
            if (oc192ConfFile != null) {
                reloadConf();
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " monCienaAlm - got exception in reloadConf()", t);
        }

        return info;
    }

    /**
     *
     *
     *
     */
    @Override
    public Object doProcess() throws Exception {

        final long perfStartTime = Utils.nanoNow();

        long sTime = System.currentTimeMillis();
        Vector v = new Vector();

        StringBuilder sb = null;

        String line = null;

        // OC192 status
        final String clName = (String) clusterNameOC192Alm.get();

        final long now = NTPDate.currentTimeMillis();

        long perfStartCMDTime = Utils.nanoNow();
        long perfFinishCMDTime = perfStartTime;

        try {

            if (cienaTL1Conn == null) {
                cienaTL1Conn = CienaTelnet.getMonitorInstance();
            }

            perfStartCMDTime = Utils.nanoNow();
            sb = cienaTL1Conn.doCmd(TL1_CMD, TL1_CTAG);
            perfFinishCMDTime = Utils.nanoNow();
            BufferedReader reader = new BufferedReader(new StringReader(sb.toString()));

            boolean started = false;
            line = reader.readLine();

            String prevLine = null;

            String cdciName = null;

            while (line != null) {
                line = line.trim();
                if (!started) {
                    if (line.startsWith("M ")) {
                        try {
                            cdciName = prevLine.trim().split("(\\s)+")[0];
                        } catch (Throwable t) {
                            cdciName = null;
                        }
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, "[ monCienaAlm ] cdciName: " + cdciName + "; start parsing");
                        }
                        started = true;
                    }
                    prevLine = line;
                    line = reader.readLine();
                    continue;
                }

                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, " [ monCienaAlm ] Processing line: [" + line + "]");
                }

                if (line.startsWith("\"") && line.endsWith("\"") && (line.length() >= 2)) {
                    try {
                        final TL1Response tl1Response = TL1Response.parseLine(line);

                        final String aid = tl1Response.singleParams.get(0);
                        if (aid != null) {
                            final String intfPortName = (String) oc192InterfacesMap.get(aid);

                            if (intfPortName != null) {
                                final String pst = tl1Response.paramsMap.get("PST");
                                final String sState = tl1Response.paramsMap.get("SIGNALST");

                                final int status = ((pst != null) && pst.equals("IS-NR")) ? 1 : 2;
                                Result r = new Result();
                                r.time = now;
                                r.FarmName = getFarmName();
                                r.ClusterName = clName;
                                r.NodeName = aid;
                                r.Module = TaskName;

                                r.addSet(intfPortName + "_OperStatus", status);

                                v.add(r);
                            }

                        }
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " [ monCienaAlm ] Got exception parsing line: " + line, t);
                    }
                }// end if
                prevLine = line;

                line = reader.readLine();
            }// for every line

        } catch (Throwable t) {

            StringBuilder sbLog = new StringBuilder(8192);
            sbLog.append(" monCienaAlm got exception for line: [").append(line).append("]");

            if (sb != null) {
                sbLog.append("\n Received from Ciena system:\n").append(sb.toString()).append("\n\n Exception: \n");
            }

            logger.log(Level.WARNING, sbLog.toString(), t);

        } finally {
            if (logger.isLoggable(Level.FINE)) {
                StringBuilder sbLog = new StringBuilder(8192);
                sbLog.append(" [ monCienaAlm ] dt= [ ").append(System.currentTimeMillis() - sTime).append(" ] ms \n");
                if (logger.isLoggable(Level.FINEST)) {
                    sbLog.append(" returning ").append(v).append("\n");
                }
                logger.log(Level.FINE, sbLog.toString());
            }
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " monCienaAlm returning\n" + v.toString() + "\n");
        }

        return v;
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
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
        logger.log(Level.INFO, " monCienaAlm stop() Request . SHOULD NOT!!!");
        return true;
    }

    /**
     * @throws java.lang.Exception
     *             - if no interfaces defined or the splitter field is defined wrong
     */
    private final void reloadConf() throws Exception {
        InputStream fis = null;
        BufferedInputStream bis = null;

        Map newAlmCodesMap = new HashMap();

        // read the conf file & parse new config
        try {

            fis = new FileInputStream(oc192ConfFile);
            bis = new BufferedInputStream(fis);
            final Properties p = new Properties();
            p.load(bis);

            final String splitter = p.getProperty("splitter", "(\\s)*;(\\s)*");

            if (splitter == null) {
                throw new Exception(" [ OC192ConfFile ] \"splitter\" property cannot be null!");
            }

            final String intLine = p.getProperty("SonetInterfaces");
            if ((intLine == null) || (intLine.trim().length() < 1)) {
                throw new Exception(" [ OC192ConfFile ]  No SonetInterfaces defined");
            }

            final String clusterName = p.getProperty("MLClusterName_SonetStatus", "SonetIntf_Status");
            if ((clusterName == null) || (clusterName.length() < 1)) {
                throw new Exception(" [ OC192ConfFile ]  No MLClusterName_SonetStatus defined");
            }

            clusterNameOC192Alm.set(clusterName);

            final String intf[] = intLine.trim().split(splitter);

            if ((intf == null) || (intf.length < 1)) {
                throw new Exception(" [ OC192ConfFile ]  No interfaces defined");
            }

            for (Object element : p.entrySet()) {
                final Map.Entry entry = (Map.Entry) element;

                final String key = (String) entry.getKey();

                if (key.equals("SonetInterfaces") || key.equals("splitter") || key.equals("MLClusterName_SonetStatus")) {
                    continue;
                }

                final String value = (String) entry.getValue();

                final Integer ki = Integer.decode(key);// can throw exception
                final String[] tks = value.split(splitter);

                final int len = tks.length;

                for (int i = 0; i < len; i++) {
                    newAlmCodesMap.put(tks[i], ki);
                }

            }

            Arrays.sort(intf);
            Set newPorts = new TreeSet();
            for (final String oc192Entry : intf) {
                if (oc192Entry.indexOf("=") > 1) {
                    String[] tks = oc192Entry.split("(\\s)*=(\\s)*");
                    oc192InterfacesMap.put(tks[0], tks[1]);
                    newPorts.add(tks[0]);
                } else {
                    oc192InterfacesMap.put(oc192Entry, oc192Entry);
                    newPorts.add(oc192Entry);
                }
            }

            oc192InterfacesMap.keySet().retainAll(newPorts);

            StringBuilder sb = new StringBuilder();
            sb.append("\n\n [ monCienaAlm ] (re)Loaded config from oc192ConfFile: ").append(oc192ConfFile)
                    .append(".\n ");
            sb.append("\n\nOC192Interfaces: ").append(oc192InterfacesMap.toString()).append("\n\n");
            if (logger.isLoggable(Level.FINER)) {
                sb.append("\n MLClusterName_SonetStatus: ").append(clusterName).append("\n used splitter: ")
                        .append(splitter);
            }
            logger.log(Level.INFO, sb.toString());

        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ monCienaAlm ] Exception (re)loading config from oc192ConfFile:"
                    + oc192ConfFile + ". Cause: ", t);
        } finally {
            // do not leak FDs
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (Throwable ignore) {
            }
            try {
                if (bis != null) {
                    bis.close();
                }
            } catch (Throwable ignore) {
            }
        }
    }

    /**
     * Called by DateFileWatchdog
     * 
     * @param o
     * @param arg
     */
    @Override
    public void update(Observable o, Object arg) {
        try {
            reloadConf();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ monCienaAlm ] got exception reloading config", t);
        }
    }

    static public void main(String[] args) {

        monCienaOCn aa = new monCienaOCn();
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
                "OC192ConfFile=/export/home/ramiro/OC192ConfFile");

        try {
            for (int k = 0; k < 10000; k++) {
                Vector bb = (Vector) aa.doProcess();
                for (int q = 0; (bb != null) && (q < bb.size()); q++) {
                    System.out.println(bb.get(q));
                }
                System.out.println("-------- sleeeping ----------");
                Thread.sleep(15000);
                System.out.println("-------- doProcess-ing --------- k=" + k);
            }
        } catch (Exception e) {
            System.out.println(" failed to process !!!");
        }
    }
}
