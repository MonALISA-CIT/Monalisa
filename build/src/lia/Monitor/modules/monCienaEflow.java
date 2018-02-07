package lia.Monitor.modules;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ciena.eflow.EFlowStats;
import lia.Monitor.ciena.eflow.FDXEFlow;
import lia.Monitor.ciena.tl1.TL1Response;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.UnsignedLong;
import lia.Monitor.monitor.cmdExec;
import lia.Monitor.monitor.eResult;
import lia.util.DateFileWatchdog;
import lia.util.Utils;
import lia.util.ntp.NTPDate;
import lia.util.telnet.CienaTelnet;
import lia.util.telnet.OSTelnetException;

/**
 * Monitoring module for eflow trafic on Ciena CD/CI interfaces
 *
 * @author ramiro
 */
public class monCienaEflow extends cmdExec implements MonitoringModule, Observer {

    /**
     *
     */
    private static final long serialVersionUID = -8463006260527278580L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monCienaEflow.class.getName());
    private static final String TL1_CTAG = "eflw";
    private static final String ALL_EFLOWS_CMD = "rtrv-eflow:::eflw;\n";

    private static final char IN_OUT_CHAR_DELIMITER = '-';
    private static final String IN_TOKEN = "IN";
    private static final String OUT_TOKEN = "OUT";

    File eflowConfFile = null;

    String mlEflowClusterName = "MLEFLOWS";
    String mlEflowCounterClusterName = "MLEFLOWS_COUNTERS";

    CienaTelnet cienaTL1Conn;

    private static final AtomicBoolean shouldMonitorPerformance = new AtomicBoolean(AppConfig.getb(
            "lia.Monitor.modules.monCienaEflow.shouldMonitorPerformance", false));

    private static final AtomicReference<Properties> mlMappingsReference = new AtomicReference<Properties>();

    //K: eflow name; V: EFlowStats
    private final Map<String, EFlowStats> eFlowsMap = new HashMap<String, EFlowStats>();

    static {
        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                shouldMonitorPerformance.set(AppConfig.getb(
                        "lia.Monitor.modules.monCienaEflow.shouldMonitorPerformance", false));
            }
        });
    }

    private static final class EFlowsMLMapping {

        private static final String getMLMapping(final String eFlowName) {
            final Properties p = mlMappingsReference.get();
            if (p != null) {
                return p.getProperty(eFlowName);
            }

            return null;
        }

    }

    public monCienaEflow() {
        TaskName = "monCienaEflow";
        isRepetitive = true;
    }

    @Override
    public MonModuleInfo init(MNode Node, String args) {
        info = new MonModuleInfo();
        info.name = TaskName;
        this.Node = Node;

        if (args == null) {
            logger.log(Level.SEVERE, "[ monCienaEflow ] Null params in init. The module is unable to monitor the CD/CI");
            throw new IllegalArgumentException(
                    "[ monCienaEflow ] Null params in init. The module is unable to monitor the CD/CI");
        }

        if (args.length() == 0) {
            logger.log(Level.SEVERE,
                    "[ monCienaEflow ] The args list is empty. The module is unable to monitor the CD/CI");
            throw new IllegalArgumentException(
                    "[ monCienaEflow ] The args list is empty. The module is unable to monitor the CD/CI");
        }

        if (args.startsWith("\"")) {
            args = args.substring(1);
        }

        if (args.endsWith("\"")) {
            args = args.substring(0, args.length() - 1);
        }

        final String[] argsTokens = args.split("(\\s)*;(\\s)*");
        if ((argsTokens != null) && (argsTokens.length > 0)) {
            for (String argsToken : argsTokens) {
                try {
                    final String argT = argsToken.trim();
                    if (argT.startsWith("eflowConfFile")) {
                        String[] fileTks = argT.split("(\\s)*=(\\s)*");
                        if ((fileTks == null) || (fileTks.length != 2)) {
                            logger.log(Level.SEVERE, "[ monCienaEflow ] cannot parse eflowConfFile param [ " + argT
                                    + " ]  ... ");
                            throw new IllegalArgumentException("[ monCienaEflow ] cannot parse eflowConfFile param [ "
                                    + argT + " ]  ... ");
                        }

                        eflowConfFile = new File(fileTks[1]);

                        if (!eflowConfFile.exists()) {
                            logger.log(Level.SEVERE, "[ monCienaEflow ] The eflowConfFile [ " + eflowConfFile
                                    + " ] does not exist");
                            throw new IllegalArgumentException("[ monCienaEflow ] The eflowConfFile [ " + eflowConfFile
                                    + " ] does not exist");
                        }

                        if (!eflowConfFile.canRead()) {
                            logger.log(Level.SEVERE, "[ monCienaEflow ] The eflowConfFile [ " + eflowConfFile
                                    + " ] does not have read access");
                            throw new IllegalArgumentException("[ monCienaEflow ] The eflowConfFile [ " + eflowConfFile
                                    + " ] does not have read access");
                        }

                    } else if (argT.startsWith("MLEFLOWS_CLUSTER_NAME")) {
                        String[] tks = argT.split("(\\s)*=(\\s)*");
                        if ((tks == null) || (tks.length != 2)) {
                            logger.log(Level.SEVERE, "[ monCienaEflow ] cannot parse MLEFLOWS_CLUSTER_NAME param [ "
                                    + argT + " ]  ... ");
                            throw new IllegalArgumentException(
                                    "[ monCienaEflow ] cannot parse MLEFLOWS_CLUSTER_NAME param [ " + argT + " ]  ... ");
                        }

                        mlEflowClusterName = tks[1];
                        mlEflowCounterClusterName = mlEflowClusterName + "_COUNTERS";
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "monCienaAlm - exception parsing module params params", t);
                }
            }
        } else {
            logger.log(Level.SEVERE,
                    "[ monCienaEflow ] Unable to determine the arguments tokens. The module is unable to monitor the CD/CI");
            throw new IllegalArgumentException(
                    "[ monCienaEflow ] Unable to determine the arguments tokens. The module is unable to monitor the CD/CI");
        }

        if (eflowConfFile == null) {
            logger.log(Level.SEVERE,
                    "[ monCienaEflow ] Unable to determine eflowConfFile. The module is unable to monitor the CD/CI");
            throw new IllegalArgumentException(
                    "[ monCienaEflow ] Unable to determine eflowConfFile. The module is unable to monitor the CD/CI");
        }

        // Just parse the conf file
        try {
            reloadConf();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "[ monCienaEflow ] Got exception parsing the conf files", t);
            throw new IllegalArgumentException("[ monCienaEflow ] Got exception parsing the conf files: "
                    + t.getMessage());
        }

        try {
            final DateFileWatchdog portsDfw = DateFileWatchdog.getInstance(eflowConfFile, 5 * 1000);
            portsDfw.addObserver(this);

        } catch (Throwable t) {
            logger.log(Level.SEVERE, "[ monCienaEflow ] Unable to monitor the config files for changes", t);
        }

        logger.log(Level.INFO, " [ monCienaEflow ] mlEflowClusterName=" + mlEflowClusterName
                + "; mlEflowCounterClusterName=" + mlEflowCounterClusterName);
        return info;
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    @Override
    public String getOsName() {
        return "linux";
    }

    private static final String INTF_OCTETS_MARKER = "INTF_IN_OCTETS,";
    private final static int INTF_OCTETS_MARKER_SIZE = INTF_OCTETS_MARKER.length();

    @Override
    public Object doProcess() throws Exception {

        final long sTime = Utils.nanoNow();
        ArrayList<Object> al = new ArrayList<Object>();

        long now = -1;

        String line = null;
        String iLine = null;

        final Set<EFlowStats> currentFlows = new TreeSet<EFlowStats>();

        final Map<String, EFlowStats> currentStrippedFlowsMap = new HashMap<String, EFlowStats>();
        //key String - flow name stripped(without -in or -out)
        //value FDXEFlow
        final Map<String, FDXEFlow> currentDuplexFlows = new HashMap<String, FDXEFlow>();

        try {
            if (cienaTL1Conn == null) {
                cienaTL1Conn = CienaTelnet.getMonitorInstance();
            }

            now = NTPDate.currentTimeMillis();

            final StringBuilder sb = cienaTL1Conn.doCmd(ALL_EFLOWS_CMD, TL1_CTAG);

            final BufferedReader reader = new BufferedReader(new StringReader(sb.toString()));
            line = null;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("\"") && line.endsWith("\"")) {
                    final int idx = line.indexOf(":");
                    if (idx > 0) {
                        final TL1Response tl1Response = TL1Response.parseLine(line);

                        final String eFlowName = tl1Response.singleParams.get(0);
                        final String sCollectPM = tl1Response.paramsMap.get("COLLECTPM");
                        final boolean canMonitor = ((sCollectPM != null) && sCollectPM.equals("YES"));

                        EFlowStats efws = eFlowsMap.get(eFlowName);
                        if (efws == null) {
                            efws = new EFlowStats(eFlowName, EFlowsMLMapping.getMLMapping(eFlowName));
                            eFlowsMap.put(eFlowName, efws);
                        }
                        efws.collectPM = canMonitor;

                        if (!currentFlows.add(efws)) {
                            logger.log(Level.WARNING, " [ monCienaEflow ] [ WARNING ] The flow: " + efws
                                    + " appeared at least one time before. TL1 response was: \n\n" + sb.toString()
                                    + "[ monCienaEflow ] [ END WARNING ] The flow: " + efws
                                    + " appeared at least one time before");
                        }

                        if (canMonitor) {
                            final int idxEnd = eFlowName.lastIndexOf(IN_OUT_CHAR_DELIMITER);
                            if ((idxEnd > 0) && (idxEnd < (eFlowName.length() - 2))) {
                                final String endToken = eFlowName.substring(idxEnd + 1);
                                final boolean isInFlow = endToken.equalsIgnoreCase(IN_TOKEN);
                                final String strippedFlowName = eFlowName.substring(0, idxEnd);
                                if (isInFlow) {
                                    final EFlowStats outFlow = currentStrippedFlowsMap.remove(strippedFlowName);
                                    if (outFlow == null) {
                                        currentStrippedFlowsMap.put(strippedFlowName, efws);
                                    } else {
                                        currentDuplexFlows.put(strippedFlowName, new FDXEFlow(strippedFlowName, efws,
                                                outFlow));
                                    }
                                } else if (endToken.equalsIgnoreCase(OUT_TOKEN)) {
                                    final EFlowStats inFlow = currentStrippedFlowsMap.remove(strippedFlowName);
                                    if (inFlow == null) {
                                        currentStrippedFlowsMap.put(strippedFlowName, efws);
                                    } else {
                                        currentDuplexFlows.put(strippedFlowName, new FDXEFlow(strippedFlowName, inFlow,
                                                efws));
                                    }
                                } else {
                                    logger.log(Level.WARNING,
                                            " [ monCienaEflow ]  Wrong flow name (the end token does not end in -IN nor -OUT): "
                                                    + eFlowName + " / strippedFlowName: " + strippedFlowName
                                                    + " Current response: \n" + sb.toString());
                                }
                            } else {
                                logger.log(Level.WARNING,
                                        " [ monCienaEflow ]  Wrong flow name (not enough chars in the end): "
                                                + eFlowName + " Current response: \n" + sb.toString());
                            }

                            final String cmdToSend = "rtrv-mib-eflow::" + eFlowName + ":" + TL1_CTAG
                                    + "::INTF_IN_OCTETS;\n";
                            if (logger.isLoggable(Level.FINEST)) {
                                logger.log(Level.FINEST, " [ monCienaEflow ] sending TL1 cmd for eflow: " + eFlowName
                                        + cmdToSend);
                            }
                            final StringBuilder iSB = cienaTL1Conn.doCmd(cmdToSend, TL1_CTAG);
                            final BufferedReader iReader = new BufferedReader(new StringReader(iSB.toString()));
                            iLine = null;
                            while ((iLine = iReader.readLine()) != null) {
                                final int idxIntf = iLine.indexOf(INTF_OCTETS_MARKER);
                                if (idxIntf > 0) {
                                    final String sSpeed = iLine.substring(idxIntf + INTF_OCTETS_MARKER_SIZE,
                                            iLine.length() - 1);
                                    efws.getSpeedAndSetLastValue(sSpeed);
                                }
                            }
                        } else {
                            logger.log(Level.WARNING, " [ monCienaEflow ] cannot monitor eFlow: " + eFlowName
                                    + " because COLLECTPM != YES ( " + sCollectPM + " ) ");
                        }
                    }
                }
            }

            //return the results in the EFLOWS cluster
            for (final Iterator<Map.Entry<String, EFlowStats>> it = eFlowsMap.entrySet().iterator(); it.hasNext();) {
                final Map.Entry<String, EFlowStats> entry = it.next();
                final String eflowName = entry.getKey();
                final EFlowStats efs = entry.getValue();

                //check for stalled/dead eflows
                if (!currentFlows.contains(efs)) {
                    logger.log(Level.INFO, "\n\n [ monCienaEflow ] The eflow: " + eflowName
                            + " is no longer available. Removing it.");
                    it.remove();
                    final eResult er = new eResult();
                    er.FarmName = Node.getFarmName();
                    er.ClusterName = Node.getClusterName();
                    er.NodeName = eflowName;
                    er.time = now;
                    er.Module = TaskName;
                    er.param = null;
                    er.param_name = null;
                    al.add(er);
                    continue;
                }

                final double lastSpeed = efs.getLastSpeed();

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " [ monCienaEflow ] eflow: " + efs);
                }

                if (lastSpeed < 0D) {
                    continue;
                }

                final Result r = new Result();
                r.FarmName = Node.getFarmName();
                r.ClusterName = Node.getClusterName();
                r.NodeName = eflowName;
                r.time = now;
                r.Module = TaskName;
                r.addSet("Rate", efs.getLastSpeed());
                r.addSet("CollectPMEnabled", (efs.collectPM) ? 1 : 0);
                al.add(r);

            }

            for (final FDXEFlow fdxFlow : currentDuplexFlows.values()) {
                final double inSpeed = fdxFlow.inFlow.getLastSpeed();
                if (inSpeed < 0D) {
                    continue;
                }
                final double outSpeed = fdxFlow.outFlow.getLastSpeed();
                if (outSpeed < 0D) {
                    continue;
                }
                final String mlEflowName = EFlowsMLMapping.getMLMapping(fdxFlow.name);
                if (mlEflowName != null) {
                    final Result mlr = new Result();
                    mlr.FarmName = Node.getFarmName();
                    mlr.ClusterName = mlEflowClusterName;
                    mlr.NodeName = Node.getName();
                    mlr.time = now;
                    mlr.Module = TaskName;
                    mlr.addSet(mlEflowName + "_IN", inSpeed);
                    mlr.addSet(mlEflowName + "_OUT", outSpeed);
                    al.add(mlr);

                    final Result counterResult = new Result();
                    counterResult.ClusterName = mlEflowCounterClusterName;
                    counterResult.NodeName = Node.getName();
                    counterResult.time = now;
                    counterResult.Module = TaskName;
                    counterResult.addSet(mlEflowName + "_IN", UnsignedLong.valueOf(fdxFlow.inFlow.lastCounterValue())
                            .doubleValue());
                    counterResult.addSet(mlEflowName + "_OUT", UnsignedLong.valueOf(fdxFlow.outFlow.lastCounterValue())
                            .doubleValue());
                    al.add(counterResult);
                }
            }
        } catch (OSTelnetException ost) {
            logger.log(Level.WARNING, "[ monCienaEflow ] got exception in doProcess()\n line: " + line + "\n iLine: "
                    + iLine + "\n remote response: " + ost.getRemoteResponse(), ost);
            throw new Exception(ost);
        } finally {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER,
                        "[ monCienaEflow ] doProcess took: " + TimeUnit.NANOSECONDS.toMillis(Utils.nanoNow() - sTime)
                        + " millis");
            }

        }

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "[ monCienaEflow ] returning: " + al);
        }

        //        if (shouldMonitorPerformance.get()) {
        //            retV = new Vector(al.size() + 1);
        //            retV.addAll(al);
        //            //final stats
        //            try {
        //                Result r = new Result();
        //                r.time = now;
        //                r.FarmName = getFarmName();
        //                r.ClusterName = Node.getClusterName() + "_PerfStat";
        //                r.NodeName = "TL1_EthIO_Stat";
        //                r.Module = TaskName;
        //                r.addSet("TL1Delay", allTlPerf);
        //                r.addSet("TotalMLTime", TimeUnit.NANOSECONDS.toMillis(Utils.nanoTime() - sPerfTime));
        //                r.addSet("TotalTL1CmdS", cmdNo);
        //                retV.add(r);
        //            } catch (Throwable t) {
        //                logger.log(Level.WARNING, " [ monCienaAlm ] Unable to publish performance measurements", t);
        //            }
        //        } else {
        //        }

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ monCienaElfow ] returning: " + al);
        }
        return al;
    }

    @Override
    public void update(Observable o, Object arg) {
        try {
            reloadConf();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ monCienaElfow ] got exception reloading config", t);
        }

    }

    private final void reloadConf() throws IOException {
        final Properties p = new Properties();
        FileInputStream fis = null;
        BufferedInputStream bis = null;

        try {
            fis = new FileInputStream(eflowConfFile);
            bis = new BufferedInputStream(fis);

            p.load(bis);
        } finally {
            Utils.closeIgnoringException(bis);
            Utils.closeIgnoringException(fis);
        }

        mlMappingsReference.set(p);
    }

    public static final void main(String[] args) throws Exception {

        monCienaEflow aa = new monCienaEflow();
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
        aa.init(new MNode(host, ad, new MCluster("CMap", null), null), "eflowConfFile=/home/ramiro/eflowConfFile");

        try {
            for (int k = 0; k < 10000; k++) {
                System.out.println(" Long.MAX_VAL = " + Long.MAX_VALUE);
                Collection<Object> bb = (Collection<Object>) aa.doProcess();
                for (final Object o : bb) {
                    System.out.println(o);
                }

                System.out.println("-------- sleeeping ----------");
                Thread.sleep(15000);
                System.out.println("-------- doProcess-ing --------- k=" + k);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(" failed to process !!!");
        }
    }
}
