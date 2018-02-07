package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Farm.FarmMonitor;
import lia.Monitor.Farm.ABPing.ABPingFastReply;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AttributePublisher;
import lia.Monitor.monitor.MLAttributePublishers;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.util.DynamicThreadPoll.SchJob;
import lia.util.mail.MailFactory;
import lia.util.ntp.NTPDate;
import lia.util.threads.MonALISAExecutors;

/**
 * 
 * @author Catalin
 *
 */
public class monABPing extends SchJob implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = -7142561154044884445L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monABPing.class.getName());

    public MNode Node;

    /** list of configuration URL from where we can read config file */
    private final Vector configURLs = new Vector();

    /** 
     * list with hostnames searched in the config file
     * This are usually my hostnames. The list is searched from top
     * to bottom fo the first match in the ABPing Config Servlet
     * response
     */
    private final Vector hostNames = new Vector();

    /** "From" mail address for alert e-mails */
    private static final String mailFromAddress = "mlstatus@monalisa.cern.ch";

    /** "To" mail addresses for alert e-mails */
    private static String[] RCPT = null;

    static String[] ResTypes = { "RTime", "RTT", "Jitter", "PacketLoss" };
    public MonModuleInfo info;
    public boolean isRepetitive = false;
    String moduleName;
    ABPingFastReply pinger;
    long lastCfgLoadTime = 0; // when was the config updated last time
    private static final long cfgLoadDelta = 2 * 60 * 1000; // after 1 minute check for new config
    private long lastErrorTime = 0; // when I printed last time the error msg on log
    private static long errorTimeDelta = 24 * 3600 * 1000; // 12 hours
    Vector peers;
    private static String farmGroups = "UNKNOWN_GROUP";
    private static final AttributePublisher publisher = MLAttributePublishers.getInstance();

    static {
        try {
            farmGroups = AppConfig.getProperty("lia.Monitor.group", "UNKNOWN_GROUP");
        } catch (Throwable t) {
            farmGroups = "UNKNOWN_GROUP";
        }
        try {
            errorTimeDelta = Long.valueOf(AppConfig.getProperty("lia.Monitor.modules.monABPing.errorTimeDelta", null))
                    .longValue() * 1000;
        } catch (Throwable t) {
            errorTimeDelta = 24 * 3600 * 1000;
        }
    }

    //  Email Stuff
    private static final String contactEmail = AppConfig.getProperty("MonaLisa.ContactEmail", null);
    private static final boolean useContactEmail = Boolean.valueOf(
            AppConfig.getProperty("include.MonaLisa.ContactEmail", "false")).booleanValue();

    public static final String myIPaddress;
    public static final String myHostname;
    public static final String myFullHostname;
    public static boolean shouldPublishABPE = false;

    static {
        String mylIPaddress = "";
        String mylHostname = "";
        String mylFullHostname = "";

        String forceIP = AppConfig.getProperty("lia.Monitor.useIPaddress");
        try {
            InetAddress addr = null;
            if (forceIP != null) {
                addr = InetAddress.getByName(forceIP);
            } else {
                addr = InetAddress.getLocalHost();
            }
            mylIPaddress = addr.getHostAddress();
            mylHostname = addr.getHostName();
            mylFullHostname = addr.getCanonicalHostName();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "[ Init ] could not get my IP address!");
        }

        myIPaddress = mylIPaddress;
        myHostname = mylHostname;
        myFullHostname = mylFullHostname;

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " Using myIPaddress [ " + myIPaddress + " ] " + " myHostname [ " + myHostname
                    + " ] " + " myFullHostname [ " + myFullHostname + " ] ");
        }
    }

    /**
     * Default monABPing Constructor
     *
     */
    public monABPing() {
        peers = new Vector();
        moduleName = "monABPing";
        isRepetitive = true;
        if (useContactEmail && (contactEmail != null) && (contactEmail.indexOf("@") != -1)) {
            RCPT = new String[] { "mlabping@monalisa.cern.ch", contactEmail };
        } else {
            RCPT = new String[] { "mlabping@monalisa.cern.ch" };
        }
    }

    /**
     * Periodically load the config from the ABPing Servlet
     * 
     * @author Catalin
     *
     */
    class ConfigLoader implements Runnable {

        public final static int CFG_NOT_OK = -1;
        public final static int CFG_OK = 0;
        public final static int CFG_FILE_NOT_FOUND = 1;
        public final static int CFG_HOSTNAME_NOT_FOUND = 2;
        public final static int CFG_EMPTY = 3;
        public final static int CFG_NETWORK_ERROR = 4;

        public final static int ERROR_UNKNOWN_VALUE = -2;
        public final static int ERROR_COULD_NOTFIND_VALUE = -1;

        public final static int MAX_RETRIES = 3; // max retries if net error.

        private final ABPingFastReply pinger;
        private final StringBuilder sbLog;
        private final int sbLogIndex;

        private int previousConfigURL = ConfigLoader.ERROR_UNKNOWN_VALUE;
        private int previousHostName = ConfigLoader.ERROR_UNKNOWN_VALUE;

        private int currentConfigURL = ConfigLoader.ERROR_COULD_NOTFIND_VALUE;
        private int currentHostName = ConfigLoader.ERROR_COULD_NOTFIND_VALUE;

        private final String[] noPeers = {};

        /**
         * Default constructor
         * 
         * @param pinger ABPing client to use. The ABPing Clinet
         * 		is the actual class that measures the RTT and 
         * 		packet loss
         */
        protected ConfigLoader(ABPingFastReply pinger) {
            this.sbLog = new StringBuilder();
            this.pinger = pinger;
            sbLog.append("monABPing at: farmName=" + Node.getFarmName() + " clusterName= " + Node.getClusterName()
                    + " nodeName=" + Node.getName() + "\n");
            for (int i = 0; i < configURLs.size(); i++) {
                sbLog.append("configURL[" + i + "]=" + configURLs.get(i) + "\n");
            }
            for (int i = 0; i < hostNames.size(); i++) {
                sbLog.append("hostName[" + i + "]=" + hostNames.get(i) + "\n");
            }
            sbLog.append("\n");
            sbLogIndex = sbLog.length();
        }

        /**
         * Get destination hosts and their ABPing configuration
         * properties
         */
        @Override
        public void run() {
            int result = ConfigLoader.CFG_NOT_OK;
            sbLog.delete(sbLogIndex, sbLog.length());
            boolean wasNetworkError = false;
            HashMap hmPublish = new HashMap();

            for (int iURL = 0; (iURL < configURLs.size()) && (result != CFG_OK); iURL++) {
                String url = (String) configURLs.get(iURL);
                hmPublish.put("ABPingCfgURL_" + iURL, url);
                hmPublish.put("ABPingCfgURL_" + iURL + "_Time", new Date(NTPDate.currentTimeMillis()).toString());
                int retries = 0;
                currentConfigURL = ConfigLoader.ERROR_COULD_NOTFIND_VALUE;
                currentHostName = ConfigLoader.ERROR_COULD_NOTFIND_VALUE;

                do {
                    result = loadConfig(hostNames, url);

                    switch (result) {
                    case CFG_NETWORK_ERROR:
                        hmPublish.put("ABPingCfgURL_" + iURL + "_Status", "CFG_NETWORK_ERROR");
                        retries++;
                        wasNetworkError = true;
                        if (retries < MAX_RETRIES) {
                            String msg = "monABPing: network error accessing " + url + ". Retrying...";
                            logger.log(Level.WARNING, msg);
                            sbLog.append(msg);
                            sbLog.append("\n");
                        }
                        break;
                    case CFG_FILE_NOT_FOUND:
                        hmPublish.put("ABPingCfgURL_" + iURL + "_Status", "CFG_FILE_NOT_FOUND");
                        break;
                    case CFG_OK:
                        hmPublish.put("ABPingCfgURL_" + iURL + "_Status", "CFG_OK");
                        currentConfigURL = iURL;
                        break;
                    case CFG_HOSTNAME_NOT_FOUND:
                        hmPublish.put("ABPingCfgURL_" + iURL + "_Status", "CFG_HOSTNAME_NOT_FOUND");
                        break;
                    case CFG_EMPTY:
                        hmPublish.put("ABPingCfgURL_" + iURL + "_Status", "CFG_EMPTY");
                        break;
                    }
                } while ((result == CFG_NETWORK_ERROR) && (retries < MAX_RETRIES));
            }

            publisher.publish(hmPublish);

            /**
             * If hostname was not found in any of the URLs
             * and if i'm not logging this error message to fast
             * log my error.
             */
            if ((result == CFG_HOSTNAME_NOT_FOUND) && ((NTPDate.currentTimeMillis() - lastErrorTime) > errorTimeDelta)) {
                logger.log(Level.WARNING, "Cannot find my hostname/IP in the ABPing conf file. ABPing module inactive.");
                lastErrorTime = NTPDate.currentTimeMillis();
            }

            /**
             * If there was an NetworkError, revert to the last
             * known configuration
             */
            if (wasNetworkError) {
                currentConfigURL = previousConfigURL;
                currentHostName = previousHostName;
            }

            /**
             * If I do not receive a good answer clear out
             * the peer cache until the problem is solved.
             */
            if ((result != CFG_OK) && (!wasNetworkError)) {
                peers.clear();
                pinger.setPeers(noPeers);
            }

            /**
             * Announce any major configuration changes
             * and update config
             */
            if ((currentConfigURL != previousConfigURL) || (currentHostName != previousHostName)) {
                if ((currentConfigURL == ConfigLoader.ERROR_COULD_NOTFIND_VALUE)
                        && (currentHostName == ConfigLoader.ERROR_COULD_NOTFIND_VALUE)) {
                    alert("FAILED loading ANY config");
                } else if ((currentConfigURL != previousConfigURL) && (currentHostName != previousHostName)) {
                    alert("CHANGED location URL & hostname ");
                } else if (currentConfigURL != previousConfigURL) {
                    alert("CHANGED location URL");
                } else if (currentHostName != previousHostName) {
                    alert("CHANGED hostname");
                }

                previousConfigURL = currentConfigURL;
                previousHostName = currentHostName;
            }
        }

        /**
         * Alert ABPing maintainer that a major configuration has happened
         * 
         * @param subj	Mail Subject
         */
        private void alert(String subj) {
            String mesg = sbLog.toString();
            String s = "ABPing[" + hostNames.get(0) + "]: ";
            if ((previousConfigURL == ConfigLoader.ERROR_UNKNOWN_VALUE)
                    && (previousHostName == ConfigLoader.ERROR_UNKNOWN_VALUE)) {
                s += "STARTED ";
            }
            s += subj;
            try {
                MailFactory.getMailSender().sendMessage(FarmMonitor.realFromAddress, mailFromAddress, RCPT, s, mesg);
            } catch (Exception e) {
                logger.log(Level.WARNING, "monABPing: error sending mail ", e);
            }
        }

        /**
         * Build a URLEncoded string thet is going to be sent to the Config Generator servlet.
         * This will contain the following information:
         * - Farm Name
         * - Farm Group(s)
         * - Farm's IP (that will be used afterwards to identify my configuration)
         * - Farm's location (LONGitude and LATitude)
         * - Farm's version
         * - Farm's release date
         * - Peers with their Lost Packages and RTT values
         */
        private String getABPingParams() {
            StringBuilder result = new StringBuilder();
            String farmName = "UNKNOWN_FARM";
            if ((Node != null) && (Node.farm != null) && (Node.farm.name != null)) {
                farmName = Node.farm.name;
            }
            String LONG = AppConfig.getProperty("MonaLisa.LONG", "N/A");
            String LAT = AppConfig.getProperty("MonaLisa.LAT", "N/A");
            String MLver = lia.Monitor.Farm.FarmMonitor.MonaLisa_version;
            String MLdate = lia.Monitor.Farm.FarmMonitor.MonaLisa_vdate;
            try {
                result.append("FarmName=");
                result.append(URLEncoder.encode(farmName, "UTF-8"));
                result.append("&Groups=");
                result.append(URLEncoder.encode(farmGroups, "UTF-8"));
                result.append("&FarmIP=");
                result.append(URLEncoder.encode(myIPaddress, "UTF-8"));
                result.append("&HostName=");
                result.append(URLEncoder.encode(myFullHostname, "UTF-8"));
                result.append("&MLversion=");
                result.append(URLEncoder.encode(MLver, "UTF-8"));
                result.append("&MLdate=");
                result.append(URLEncoder.encode(MLdate, "UTF-8"));
                result.append("&LONG=");
                result.append(URLEncoder.encode(LONG, "UTF-8"));
                result.append("&LAT=");
                result.append(URLEncoder.encode(LAT, "UTF-8"));
                // compute peers' status
                StringBuilder peerStat = new StringBuilder();
                for (Enumeration en = peers.elements(); en.hasMoreElements();) {
                    String peerName = (String) en.nextElement();
                    peerStat.append("&PeerStat=");
                    peerStat.append(URLEncoder.encode(peerName + "|" + pinger.getPeerPacketLoss(peerName) + "|"
                            + pinger.getPeerRTT(peerName), "UTF-8"));
                }
                result.append(peerStat);
            } catch (UnsupportedEncodingException uex) {
                logger.log(Level.WARNING, "Cannot encode ABPing params", uex);
            }
            return result.toString();
        }

        /**
         * Load new configuration for host host from URL url
         * 
         * @param hostnames	A list of hostnames that my host will respond to
         * @param url		URL from witch to read the configuration
         * @return			CFG_* status codes (CFG_OK, CFG_FILE_NOT_FOUND, 
         *					CFG_HOSTNAME_NOT_FOUND, CFG_NETWORK_ERROR)
         */
        private int loadConfig(Vector hostnames, String url) {
            String msg = "";
            if ((hostnames == null) || (hostnames.isEmpty())) {
                sbLog.append("monABPing: hostname " + hostnames + " not found in config URL " + url + ".\n");
                return ConfigLoader.CFG_HOSTNAME_NOT_FOUND;
            }
            if ((url == null) || (url.equals(""))) {
                msg = "monABPing: invalid config URL:" + url + ".";
                logger.log(Level.WARNING, msg);
                sbLog.append(msg);
                sbLog.append("\n");
                return ConfigLoader.CFG_FILE_NOT_FOUND;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("monABPing: trying to load config for hosts: " + hostnames);
            sb.append("from " + url + ".");
            logger.log(Level.FINE, sb.toString());
            sbLog.append(sb.toString());
            sbLog.append("\n");

            double OVERALL_COEF = 0;
            double RTT_COEF = 1;
            double PKT_LOSS_COEF = 100;
            double JITTER_COEF = 10;
            int RTT_SAMPLES = 6;
            int PKT_LOSS_MEM = 10;
            int PACKET_SIZE = 450;
            int PING_INTERVAL = 4000;

            URLConnection urlc = null;
            InputStream is = null;
            DataOutputStream os = null;
            BufferedReader in = null;
            try {
                urlc = new URL(url).openConnection();
                // We are going to do a POST
                urlc.setDoInput(true);
                urlc.setDoOutput(true);
                // Quick fix to bypass http proxies 
                urlc.setDefaultUseCaches(false);
                urlc.setUseCaches(false);
                // We are going to send URLEncoed data
                urlc.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                os = new DataOutputStream(urlc.getOutputStream());
                String abPingParams = getABPingParams();
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "POSTing the following data:\n" + abPingParams);
                }
                os.writeBytes(abPingParams);
                os.flush();
                os.close();
                os = null;
                // Ok, now let's see what we got
                is = urlc.getInputStream();

                in = new BufferedReader(new InputStreamReader(is));
                String line;
                boolean confNotFound = true;
                boolean confEmpty = true;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if ((line.length() == 0) || line.startsWith("#")) {
                        continue;
                    }
                    confEmpty = false;
                    StringTokenizer st = new StringTokenizer(line, " ");
                    String id = "";
                    if (st.hasMoreTokens()) {
                        id = st.nextToken();
                    }
                    if (st.hasMoreTokens()) {
                        for (int i = 0; (i < hostnames.size()) && confNotFound; i++) {
                            if (id.compareToIgnoreCase((String) hostnames.get(i)) == 0) {
                                peers.clear();
                                String[] myPeers = new String[st.countTokens()];
                                int p = 0;
                                while (st.hasMoreTokens()) {
                                    String h = st.nextToken();
                                    peers.add(h);
                                    myPeers[p++] = h;
                                }
                                pinger.setPeers(myPeers);
                                confNotFound = false;
                                currentHostName = i;
                            }
                        }
                        if (!confNotFound) {
                            if (id.equals("OVERALL_COEF")) {
                                OVERALL_COEF = Double.parseDouble(st.nextToken());
                            } else if (id.equals("RTT_COEF")) {
                                RTT_COEF = Double.parseDouble(st.nextToken());
                            } else if (id.equals("PKT_LOSS_COEF")) {
                                PKT_LOSS_COEF = Double.parseDouble(st.nextToken());
                            } else if (id.equals("JITTER_COEF")) {
                                JITTER_COEF = Double.parseDouble(st.nextToken());
                            } else if (id.equals("RTT_SAMPLES")) {
                                RTT_SAMPLES = Integer.parseInt(st.nextToken());
                            } else if (id.equals("PKT_LOSS_MEM")) {
                                PKT_LOSS_MEM = Integer.parseInt(st.nextToken());
                            } else if (id.equals("PACKET_SIZE")) {
                                PACKET_SIZE = Integer.parseInt(st.nextToken());
                            } else if (id.equals("PING_INTERVAL")) {
                                PING_INTERVAL = Integer.parseInt(st.nextToken());
                            }
                        }
                    }
                }
                if (confEmpty) {
                    msg = "monABPing: received an empty config from " + url + ".";
                    logger.log(Level.WARNING, msg);
                    sbLog.append(msg);
                    sbLog.append("\n");
                    return CFG_EMPTY;
                }
                pinger.setConfig(OVERALL_COEF, RTT_COEF, PKT_LOSS_COEF, JITTER_COEF, RTT_SAMPLES, PKT_LOSS_MEM,
                        PACKET_SIZE, PING_INTERVAL);
                if ((!confNotFound) && (logger.isLoggable(Level.FINEST))) {
                    StringBuilder log = new StringBuilder();
                    log.append(" My hostname: " + hostnames.get(currentHostName));
                    log.append(" My peers: ");
                    for (Iterator it = peers.iterator(); it.hasNext();) {
                        log.append(" " + (String) it.next());
                    }
                    log.append(" OVERALL_COEF: " + OVERALL_COEF);
                    log.append(" RTT_COEF: " + RTT_COEF);
                    log.append(" PKT_LOSS_COEF: " + PKT_LOSS_COEF);
                    log.append(" JITTER_COEF: " + JITTER_COEF);
                    log.append(" RTT_SAMPLES: " + RTT_SAMPLES);
                    log.append(" PKT_LOSS_MEM: " + PKT_LOSS_MEM);
                    log.append(" PACKET_SIZE: " + PACKET_SIZE);
                    log.append(" PING_INTERVAL: " + PING_INTERVAL);
                    logger.log(Level.FINEST, log.toString());
                }

                if (confNotFound) {
                    msg = "monABPing: hostname " + hostnames + " not found in config file " + url + ".";
                    //logger.log(Level.WARNING, msg);
                    sbLog.append(msg);
                    sbLog.append("\n");
                    return CFG_HOSTNAME_NOT_FOUND;
                } else {
                    msg = "monABPing: config (re)loaded sucessfully from " + url + ".";
                    sbLog.append(msg);
                    sbLog.append("\n");
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, msg);
                    }
                    return CFG_OK;
                }
            } catch (FileNotFoundException enf) {
                msg = "monABPing: config file not found at " + url + ".";
                sbLog.append(msg);
                sbLog.append("\n");
                logger.log(Level.WARNING, "Got exception " + enf.getMessage());
                return CFG_FILE_NOT_FOUND;
            } catch (Throwable t) {
                msg = "monABPing: Got exception:\n" + t + ".";
                sbLog.append(msg);
                sbLog.append("\n");
                logger.log(Level.WARNING, "Got exception " + t.getMessage());
                return CFG_NETWORK_ERROR;
            } finally {
                try {
                    if (in != null) {
                        in.close();
                        in = null;
                    }
                } catch (Throwable tf) {
                }
                try {
                    if (is != null) {
                        is.close();
                        is = null;
                    }
                } catch (Throwable tf) {
                }
                try {
                    if (os != null) {
                        os.close();
                        os = null;
                    }
                } catch (Throwable tf) {
                }
                urlc = null;
            }
        }
    }

    /**
     * Put all my hostnames into a hostnames Vector.
     * Only fully qualified domain names are allowed.
     * 
     * @param host	My hostname
     */
    private void addDefaultHostname(String host) {
        if (host.indexOf(".") < 0) {
            return;
        }
        if (hostNames.contains(host)) {
            return;
        }
        hostNames.add(host);
    }

    /**
     * Add a new URL site for the ABPing configuration
     * cache
     * 
     * @param url
     */
    private void addDefaultURL(String url) {
        if (configURLs.contains(url)) {
            return;
        }
        configURLs.add(url);
    }

    /**
     * Initialize default values. Get myIp, myHostname
     * and add default configuration URLs to the iURL Vector
     *
     */
    private void initDefaults() {
        shouldPublishABPE = true;
        logger.log(Level.INFO, " Will publish ABPing presence ... ");
        hostNames.clear();
        addDefaultHostname(myHostname);
        addDefaultHostname(myFullHostname);
        addDefaultHostname(myIPaddress);
        // default hostName
        addDefaultHostname("dummyHost.dummyDomain");

        String urls = AppConfig.getProperty("lia.Monitor.ABPing.ConfigURL", "");
        if (urls == null) {
            urls = "";
        }
        StringTokenizer st = new StringTokenizer(urls, ",");
        configURLs.clear();
        while (st.hasMoreTokens()) {
            addDefaultURL(st.nextToken());
        }
        // default config URL
        addDefaultURL("http://monalisa.cern.ch/MONALISA/ABPingFarmConfig");
    }

    /**
     * Initialize monABPing module.
     * Create a default ABPing client (pinger) and schedule
     * a config Timer Task that updates ABPing configuration
     * 
     * @param	Node	New node information
     * @param	arg		Not used
     * @return	Initialized module Information
     */
    @Override
    public MonModuleInfo init(MNode Node, String arg) {
        this.Node = Node;
        logger.log(Level.INFO, "monABPing: farmName=" + Node.getFarmName() + " clusterName= " + Node.getClusterName()
                + " nodeName=" + Node.getName());
        pinger = new ABPingFastReply(Node.getName());

        initDefaults();

        MonALISAExecutors.getMLHelperExecutor().scheduleWithFixedDelay(new ConfigLoader(pinger), 0, cfgLoadDelta,
                TimeUnit.MILLISECONDS);

        info = new MonModuleInfo();
        info.name = moduleName;
        info.ResTypes = ResTypes;
        return info;
    }

    @Override
    public Object doProcess() throws Exception {

        Vector vec = new Vector();
        for (Enumeration en = peers.elements(); en.hasMoreElements();) {
            String peerName = (String) en.nextElement();
            Result rez = new Result(Node.getFarmName(), Node.getClusterName(), peerName, moduleName, ResTypes);
            pinger.fillResults(rez); // this might throw ex. if module is not active and cannot be started properly
            rez.time = NTPDate.currentTimeMillis();
            vec.add(rez);
        }
        return vec;
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
        pinger.active = false;
        logger.log(Level.INFO, " monABPing stop() Request ");
        return true;
    }

    static public void main(String[] args) {

        monABPing aa = new monABPing();
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
        MonModuleInfo info = aa.init(new MNode(host, ad, null, null), null);

        try {
            for (int k = 0; k < 10000; k++) {
                Vector bb = (Vector) aa.doProcess();
                for (int q = 0; q < bb.size(); q++) {
                    System.out.println(bb.get(q));
                }
                System.out.println("-------- sleeeping ----------");
                Thread.sleep(5000);
                System.out.println("-------- doProcess-ing --------- k=" + k);
            }
        } catch (Exception e) {
            System.out.println(" failed to process !!!");
        }
        aa.pinger.active = false;
    }

}
