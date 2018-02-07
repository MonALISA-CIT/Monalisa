package lia.Monitor.modules;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lia.Monitor.Farm.Pathload.PeerCache;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.ShutdownReceiver;
import lia.Monitor.monitor.cmdExec;
import lia.util.MLSignalHandler;
import lia.util.ShutdownManager;

/**
 * monPathload monitoring module. (Developer release)
 * 
 * @author heri
 * @since ML.1.4.10
 *
 */
public class monPathload extends cmdExec implements MonitoringModule, ShutdownReceiver {
    /**
     * module Version Number
     */
    public final static String buildNo = "001-060726";

    /**
     * Generated Serial Version
     */
    private static final long serialVersionUID = -7895076920176116050L;

    /**
     * Logging component
     */
    private static final Logger logger = Logger.getLogger(monPathload.class.getName());

    /**
     * Official Module Name
     */
    public static String ModuleName = "lia.Monitor.modules.monPathload.mainModule";
    public static String ClusterName = "Pathload";

    /**
     * Resource Types published by this module. 
     * <b>AwBandwidth_Low</b> - lower margin of the aw. bw from client to server
     * <b>AwBandwidth_High</b> - upper margin of available bandwidth 
     */
    public static String[] ResTypes = { "AwBandwidth_Low", "AwBandwidth_High", "MeasurementDuration", "FleetsSent",
            "MegaBytesReceived", "MeasurementStatus" };

    /**
     *  The operating on which monPathload runs. Default: linux
     */
    public static final String OsName = "linux";

    /**
     * Place to pick the results from
     */
    private final PeerCache peerCache;

    /**
     * Default constructor
     */
    public monPathload() {
        super(ModuleName);
        logger.log(Level.FINE, "monPathload module started");
        info.name = ModuleName;
        info.ResTypes = ResTypes;
        isRepetitive = true;
        peerCache = PeerCache.getInstance();
        MLSignalHandler mlsh = MLSignalHandler.getInstance();
        if (mlsh != null) {
            mlsh.addModule(this);
        } else {
            ShutdownManager sm = ShutdownManager.getInstance();
            sm.addModule(this);
        }
    }

    /** 
     * @see lia.Monitor.monitor.cmdExec#init(lia.Monitor.monitor.MNode, java.lang.String)
     */
    @Override
    public MonModuleInfo init(MNode Node, String param) {
        this.Node = Node;
        info = new MonModuleInfo();
        info.name = monPathload.ModuleName;
        info.ResTypes = ResTypes;

        try {
            if (param == null) {
                throw new IllegalArgumentException("Invalid module argumens in [myFarm].conf. Argument is null.");
            }

            Properties prop = parseParameters(peerCache, param);
            peerCache.setProperties(prop);
            peerCache.setFarmName(Node.getFarmName());
            peerCache.start();
        } catch (IllegalArgumentException e) {
            logger.log(Level.SEVERE, e.getMessage());
            info.addErrorCount();
            info.setState(1);
            info.setErrorDesc("Cannot load parameters for Pathload module");
        }

        return info;
    }

    /** 
     * Called by the Monalisa Core. It collects a vector of results.
     * 
     * @see 	lia.usrcode.util.d.b#doProcess()
     * @return	A Vector of lia.monitor.Results
     * @throws	Any kind of exception will disable this module.
     */
    @Override
    public Object doProcess() throws Exception {
        Vector vResults = peerCache.getResults();
        if (vResults == null) {
            vResults = new Vector();
        }

        if (logger.isLoggable(Level.FINEST)) {
            StringBuilder sb = new StringBuilder();
            sb.append("Pathload putting ");
            sb.append(vResults.size());
            sb.append(" elements. ");
            for (Iterator it = vResults.iterator(); it.hasNext();) {
                Result r = (Result) it.next();
                sb.append(r.toString());
                sb.append("\n");
            }
            logger.log(Level.FINEST, sb.toString());
        }

        return vResults;
    }

    /** 
     * Get Resource Types monitored by monPathload
     * 
     * @see 	lia.Monitor.monitor.MonitoringModule#ResTypes()
     * @return	An array of Resource Types. Used in Result creation.
     */
    @Override
    public String[] ResTypes() {
        return monPathload.ResTypes;
    }

    /** 
     * Gets the Os Name on which monPathload runs. 
     * @see 	lia.Monitor.monitor.MonitoringModule#getOsName()
     * @return	My OsType. Default: linux
     */
    @Override
    public String getOsName() {
        return monPathload.OsName;
    }

    /** 
     * Get Module Information
     * 
     * @see 	lia.Monitor.monitor.MonitoringModule#getInfo()
     * @return	My Module information.
     */
    @Override
    public MonModuleInfo getInfo() {
        return this.info;
    }

    /**
     * Shutdown gracefully.
     * All threads from the PeerCache will stop, no
     * Results will be exported.
     * 
     * @see lia.Monitor.monitor.cmdExec#stop()
     */
    @Override
    public boolean stop() {
        boolean bResult;

        bResult = super.stop();
        if (peerCache != null) {
            bResult &= peerCache.stop();
        }
        return bResult;
    }

    /**
     * Shutdown gracefully (This method is needed by the
     * ShutdownReceiver interface) 
     */
    @Override
    public void Shutdown() {
        logger.log(Level.INFO, "monPathload is shutting down.");
        stop();
    }

    /**
     * Basic testing method
     * TODO: Test this in standalone mode and url mode
     * 
     * @param args
     */
    public static void main(String[] args) {
        String myFQDNHostname = "";

        try {
            String itf = AppConfig.getProperty("lia.util.Pathload.client.interface");
            InetAddress addr = null;
            if (itf == null) {
                throw new IllegalArgumentException("The lia.util.Pathload.client.interface is critical. Please set "
                        + "this propery in ml.properties. Ex: lia.util.Pathload.client.interface=eth0 ");
            }

            boolean isSet = false;
            NetworkInterface ni = NetworkInterface.getByName(itf);
            for (Enumeration e = ni.getInetAddresses(); e.hasMoreElements() && (isSet == false);) {
                addr = (InetAddress) e.nextElement();
                if ((!addr.isSiteLocalAddress()) && (!addr.isLoopbackAddress())) {
                    if (addr instanceof java.net.Inet4Address) {
                        if (e.hasMoreElements()) {
                            logger.log(Level.INFO, "Interface " + ni.getDisplayName() + " has subinterfaces"
                                    + "defined. I have chosen the first one.");
                        }
                        isSet = true;
                    }
                }
            }

            if (!isSet) {
                throw new IllegalArgumentException("Could not find a valid interface to run on.");
            }

            myFQDNHostname = addr.getCanonicalHostName();
        } catch (IllegalArgumentException e) {
            logger.log(Level.SEVERE, e.getMessage());
        } catch (SocketException e) {
            logger.log(Level.SEVERE, "[monPathload] PeerCache Trying to get its ip but received a SocketException");
        } catch (NullPointerException e) {
            logger.log(Level.SEVERE, "[monPathload] PeerCache Trying to get its ip but received a NullPointerException");
        } catch (SecurityException e) {
            logger.log(Level.SEVERE, "[monPathload] PeerCache SecurityException while trying to determine hostname.");
        }

        monPathload aa = new monPathload();
        aa.init(new MNode(myFQDNHostname, myFQDNHostname, null, new MFarm("PTest" + (int) (Math.random() * 100))), null);
        while (true) {
            try {
                Vector vResults = (Vector) aa.doProcess();
                if (vResults != null) {
                    for (Iterator it = vResults.iterator(); it.hasNext();) {
                        Result result = (Result) it.next();
                        System.out.println("Result: \n" + result.toString());
                        logger.log(Level.FINEST, "Result: \n" + result.toString());
                    }
                }
                System.out.println("------------------------------------\n");
                logger.log(Level.FINEST, "------------------------------------\n");
                Thread.sleep(30 * 1000);
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }
    }

    /**
     * Parses parameters from myFarm.conf
     * 
     * @param peerCache		The PeerCache to hold the data
     * @param params		Parameters from myFarm.conf
     * @return				The Parameters in a Property object
     * @throws IllegalArgumentException	Throws IllegalArgumentException if one of the
     * 						arguments is null or if parsing failed.
     */
    private Properties parseParameters(PeerCache peerCache, String params) throws IllegalArgumentException {
        if ((peerCache == null) || (params == null)) {
            throw new IllegalArgumentException("IllegalArgument, internal peerCache is null "
                    + "or module parameters are null.");
        }

        Properties prop = new Properties();
        String sPattern = "\\s*(if|speed)\\s*=\\s*((\\w|\\.|\\:)+)\\s*";
        Pattern pattern = Pattern.compile(sPattern, Pattern.UNIX_LINES | Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        String[] arguments = params.split("\\s*;\\s*");
        for (String argument : arguments) {
            Matcher matcher = pattern.matcher(argument);
            if (matcher.find()) {
                if (matcher.groupCount() == 3) {
                    prop.put(matcher.group(1), matcher.group(2));
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "monPathload Putting config. name=" + matcher.group(1) + " value="
                                + matcher.group(2) + ".");
                    }
                } else {
                    logger.log(Level.WARNING, "monPathload did not find a match while reading property " + argument);
                }
            } else {
                logger.log(Level.WARNING, "monPathload could not read property " + argument);
            }
        }

        return prop;
    }
}
