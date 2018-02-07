/*
 * $Id: monCienaVCG.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.modules;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import lia.Monitor.ciena.circuits.tl1.TL1Util;
import lia.Monitor.ciena.tl1.TL1Response;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
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
import lia.util.telnet.OSTelnetException;

/**
 * Monitoring module for eflow trafic on Ciena CD/CI interfaces
 * 
 * @author ramiro
 */
public class monCienaVCG extends cmdExec implements MonitoringModule, Observer {

    /**
     * 
     */
    private static final long serialVersionUID = -8463006260527278580L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monCienaVCG.class.getName());

    File vcgConfFile = null;

    CienaTelnet cienaTL1Conn;

    public final double DEFAULT_OC1_BW_MULTIPLIER = 9621.5 / 192;

    private volatile double bwMultiplier = DEFAULT_OC1_BW_MULTIPLIER;

    // private final SimpleDateFormat dateParserTL1 = new
    // SimpleDateFormat("yy-MM-dd HH:mm:ss");

    private static final AtomicBoolean shouldMonitorPerformance = new AtomicBoolean(AppConfig.getb(
            "lia.Monitor.modules.monCienaVCG.shouldMonitorPerformance", false));

    private static final AtomicReference<Properties> mlMappingsReference = new AtomicReference<Properties>();

    static {
        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                shouldMonitorPerformance.set(AppConfig.getb("lia.Monitor.modules.monCienaVCG.shouldMonitorPerformance",
                        false));
            }
        });
    }

    public monCienaVCG() {
        TaskName = "monCienaVCG";
        isRepetitive = true;
    }

    @Override
    public MonModuleInfo init(MNode Node, String args) {
        info = new MonModuleInfo();
        info.name = TaskName;
        this.Node = Node;

        if (args == null) {
            logger.log(Level.SEVERE, "[ monCienaVCG ] Null params in init. The module is unable to monitor the CD/CI");
            throw new IllegalArgumentException(
                    "[ monCienaVCG ] Null params in init. The module is unable to monitor the CD/CI");
        }

        if (args.length() == 0) {
            logger.log(Level.SEVERE,
                    "[ monCienaVCG ] The args list is empty. The module is unable to monitor the CD/CI");
            throw new IllegalArgumentException(
                    "[ monCienaVCG ] The args list is empty. The module is unable to monitor the CD/CI");
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
                    if (argT.startsWith("vcgConfFile")) {
                        String[] fileTks = argT.split("(\\s)*=(\\s)*");
                        if ((fileTks == null) || (fileTks.length != 2)) {
                            logger.log(Level.SEVERE, "[ monCienaVCG ] cannot parse vcgConfFile param [ " + argT
                                    + " ]  ... ");
                            throw new IllegalArgumentException("[ monCienaVCG ] cannot parse vcgConfFile param [ "
                                    + argT + " ]  ... ");
                        }

                        vcgConfFile = new File(fileTks[1]);

                        if (!vcgConfFile.exists()) {
                            logger.log(Level.SEVERE, "[ monCienaVCG ] The vcgConfFile [ " + vcgConfFile
                                    + " ] does not exist");
                            throw new IllegalArgumentException("[ monCienaVCG ] The vcgConfFile [ " + vcgConfFile
                                    + " ] does not exist");
                        }

                        if (!vcgConfFile.canRead()) {
                            logger.log(Level.SEVERE, "[ monCienaVCG ] The vcgConfFile [ " + vcgConfFile
                                    + " ] does not have read access");
                            throw new IllegalArgumentException("[ monCienaVCG ] The vcgConfFile [ " + vcgConfFile
                                    + " ] does not have read access");
                        }

                    } else if (argT.startsWith("OC1_BWMULTIPLIER")) {
                        final String[] mTks = argT.split("(\\s)*=(\\s)*");
                        if ((mTks == null) || (mTks.length != 2)) {
                            logger.log(Level.SEVERE, "[ monCienaVCG ] cannot parse OC1_BWMULTIPLIER param [ " + argT
                                    + " ]  ... ");
                            throw new IllegalArgumentException("[ monCienaVCG ] cannot parse OC1_BWMULTIPLIER param [ "
                                    + argT + " ]  ... ");
                        }

                        double bwMul = DEFAULT_OC1_BW_MULTIPLIER;
                        try {
                            bwMul = Double.parseDouble(mTks[1]);
                        } catch (Throwable t) {
                            logger.log(Level.SEVERE, "[ monCienaVCG ] cannot parse OC1_BWMULTIPLIER param [ " + argT
                                    + " ]  ... Cause:", t);
                        }

                        bwMultiplier = bwMul;

                    }

                    logger.log(Level.INFO, " [ monCienaVCG ] OC192 time slot (STS1) Bw Multiplier = " + bwMultiplier);

                } catch (Throwable t) {
                    logger.log(Level.WARNING, "monCienaAlm - exception parsing module params params", t);
                }
            }
        } else {
            logger.log(Level.SEVERE,
                    "[ monCienaVCG ] Unable to determine the arguments tokens. The module is unable to monitor the CD/CI");
            throw new IllegalArgumentException(
                    "[ monCienaVCG ] Unable to determine the arguments tokens. The module is unable to monitor the CD/CI");
        }

        if (vcgConfFile == null) {
            logger.log(Level.SEVERE,
                    "[ monCienaVCG ] Unable to determine vcgConfFile. The module is unable to monitor the CD/CI");
            throw new IllegalArgumentException(
                    "[ monCienaVCG ] Unable to determine vcgConfFile. The module is unable to monitor the CD/CI");
        }

        // Just parse the conf file
        try {
            reloadConf();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "[ monCienaVCG ] Got exception parsing the conf files", t);
            throw new IllegalArgumentException("[ monCienaVCG ] Got exception parsing the conf files: "
                    + t.getMessage());
        }

        try {
            final DateFileWatchdog portsDfw = DateFileWatchdog.getInstance(vcgConfFile, 5 * 1000);
            portsDfw.addObserver(this);

        } catch (Throwable t) {
            logger.log(Level.SEVERE, "[ monCienaVCG ] Unable to monitor the config files for changes", t);
        }

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

    @Override
    public Object doProcess() throws Exception {

        final long sPerfTime = Utils.nanoNow();

        final ArrayList<Result> al = new ArrayList<Result>();
        final ArrayList<String> alErr = new ArrayList<String>();

        long now = NTPDate.currentTimeMillis();

        try {

            final Properties p = mlMappingsReference.get();

            for (final Map.Entry<Object, Object> entry : p.entrySet()) {

                final String mlVcgName = (String) entry.getValue();
                final String vcgName = (String) entry.getKey();

                try {
                    final TL1Response tl1Response = TL1Util.getVCG(vcgName);
                    final int pBW = TL1Util.getIntVal("PROVBW", tl1Response);
                    final int oBW = TL1Util.getIntVal("OPERBW", tl1Response);

                    final Result r = new Result();
                    r.time = now;
                    r.ClusterName = Node.getClusterName();
                    r.FarmName = Node.getFarmName();
                    r.NodeName = vcgName;
                    r.addSet("ProvisionedBWSlots", pBW);
                    r.addSet("OperationalBWSlots", oBW);
                    al.add(r);

                    final Result r1 = new Result();
                    r1.time = now;
                    r1.ClusterName = "MLVCGS";
                    r1.FarmName = Node.getFarmName();
                    r1.NodeName = mlVcgName;
                    r1.addSet("ProvisionedBW", pBW * bwMultiplier);
                    r1.addSet("OperationalBW", oBW * bwMultiplier);
                    r1.addSet("ProvisionedTimeSlots", pBW);
                    r1.addSet("OperationalTimeSlots", oBW);
                    al.add(r1);

                } catch (OSTelnetException ost) {
                    alErr.add(vcgName);
                    logger.log(Level.WARNING, "[ monCienaVCG ] got exception in doProcess() for VCG: " + vcgName
                            + "\n**** remote response **** \n: " + ost.getRemoteResponse(), ost);
                }
            }// end for

        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ monCienaVCG ] got exception in doProcess(). Cause: ", t);
            throw new Exception(t.getCause());
        } finally {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(
                        Level.FINER,
                        "[ monCienaVCG ] doProcess took: "
                                + TimeUnit.NANOSECONDS.toMillis((Utils.nanoNow() - sPerfTime)) + " ms.");
            }

        }

        if (alErr.size() > 0) {
            logger.log(Level.INFO, " [ monCienaVCG ] Exceptions for the following VCGs: " + alErr);
        }

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ monCienaVCG ] returning " + al.size() + " values: " + al);
        }

        return al;
    }

    @Override
    public void update(Observable o, Object arg) {
        try {
            reloadConf();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ monCienaVCG ] got exception reloading config", t);
        }

    }

    private final void reloadConf() throws Exception {
        final Properties p = new Properties();
        FileInputStream fis = null;
        BufferedInputStream bis = null;

        try {
            fis = new FileInputStream(vcgConfFile);
            bis = new BufferedInputStream(fis);

            p.load(bis);

        } finally {
            Utils.closeIgnoringException(bis);
            Utils.closeIgnoringException(fis);
        }

        mlMappingsReference.set(p);
    }

    @SuppressWarnings("unchecked")
    public static final void main(String[] args) throws Exception {
        LogManager.getLogManager().readConfiguration(
                new ByteArrayInputStream(("handlers= java.util.logging.ConsoleHandler\n"
                        + "java.util.logging.ConsoleHandler.level = FINEST\n"
                        + "java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter\n"
                        + "lia.Monitor.ciena.circuits.tl1.TL1Util.level = ALL\n" + "").getBytes()));
        logger.setLevel(Level.ALL);

        monCienaVCG aa = new monCienaVCG();
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
        aa.init(new MNode(host, ad, new MCluster("CMap", null), null), "vcgConfFile=/home/ramiro/vcgConfFile");

        try {
            for (int k = 0; k < 10000; k++) {
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
