package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.rmi.RMISecurityManager;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lia.Monitor.JiniSerFarmMon.MLLUSHelper;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.GenericMLEntry;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.Monitor.monitor.eResult;
import lia.util.Utils;
import lia.util.ntp.NTPDate;
import net.jini.core.lookup.ServiceItem;

/**
 * This module is a reimplementation of the monOldTracepath module. It will produce
 * Results instead of eResults and will provide just the necessary information in
 * a more concise way. 
 */
public class monTracepath extends cmdExec implements MonitoringModule {
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 1L;

    /** Logger used by this class */
    static final Logger logger = Logger.getLogger(monTracepath.class.getName());

    /**
     * Configuration URL for tracepath
     */
    public static final String TRACEPATH_CONFIG_URL = AppConfig
            .getProperty("lia.monitor.Modules.monTracepath.configURL");

    private static final String ModuleName = "monTracepath";

    /**
     * Result names
     */
    static final String[] ResTypes = { "status" };

    /** not traced yet; the peer is just added and there is no data about it yet */
    static public int STATUS_NOT_TRACED_YET = -1;
    /** current reported trace is ok */
    static public int STATUS_OK = 0;
    /** the tracepath has failed during trace to the given node */
    static public int STATUS_TRACEPATH_FAILED = 1;
    /** the traceroute has failed during trace to the given node */
    static public int STATUS_TRACEROUTE_FAILED = 2;
    /** the destination (given node) was unreachable */
    static public int STATUS_DESTINATION_UNREACHED = 3;
    /** this peer has been removed from config and must be deleted from the clients */
    static public int STATUS_REMOVED = 4;
    /**  neither tracepath nor traceroute cannot be run - either don't exist, either are both disabled */
    static public int STATUS_DISABLED = 5;
    /** there is an internal config problem with this peer; this should never appear */
    static public int STATUS_INTERNAL_ERR = 6;

    /**
     * Reload configuration interval
     */
    static long CONFIG_RELOAD_INTERVAL = AppConfig.getl("lia.monitor.Modules.monTracepath.ConfigReloadInterval", 240) * 1000; // 4 minutes

    /**
     * How often to perform traces ?
     */
    static long TRACE_INTERVAL = AppConfig.getl("lia.monitor.Modules.monTracepath.TraceInterval", 120) * 1000; // each 2 minutes between 2 traceroutes

    /**
     * How often to send the results?
     */
    static long IPID_SEND_INTERVAL = AppConfig.getl("lia.monitor.Modules.monTracepath.IPIDSendInterval", 120) * 1000; // each 2 minutes send cached IPs to IPID Service  

    /**
     * Configuration URL
     */
    String configURL = null;

    /**
     * IP URLs
     */
    String ipidURL = null;

    private String tracepathCmd = " -n "; // tracepath location will be searched and prepended
    private boolean useTracepath = true;
    private String tracerouteCmd = " -n -q 1 -m 30 -w 3 "; // traceroute location will be searched and prepended
    private boolean useTraceroute = true;

    /**
     * options for how others should do traces on me (see runTrace() for details)
     */
    String myTraceOpts = "";

    /**
     * my IP address - used by traceroute to set first hop ip; and by config loader to find myself in the config file
     */
    String myIP;

    /**
     * for TraceAutoConfig - the hostname of this machine, in case reverse dns doesn't work for this host
     */
    String hostName;

    /**
     * stores the list with expire results that are going to be sent at next doProcess
     */
    Vector<Object> nextExpireResults;

    /**
     * stores IPs discovered by traces that are sent to the IPID service by TraceFilter
     */
    Set<String> ipCache;

    /**
     * stores the current peers with their corresponding tracing options
     */
    Hashtable<String, String> crtPeersOpts;

    /**
     * stores the last result for each peer - see getConfExpireParamsResults
     */
    Hashtable<String, Result> crtPeersResults;

    /**
     * The tracepaths generated since the last execution of the module.
     */
    Vector<Object> newTracepath;

    /**
     * 
     */
    public monTracepath() {
        super(ModuleName);
        info.ResTypes = ResTypes;
        info.name = ModuleName;
        isRepetitive = true;
        crtPeersOpts = new Hashtable<String, String>();
        nextExpireResults = new Vector<Object>();
        ipCache = new HashSet<String>();
        crtPeersResults = new Hashtable<String, Result>();

        newTracepath = new Vector<Object>();

        Timer ttask = new Timer(true);
        TimerTask configLoader = new ConfigLoader();
        ttask.schedule(configLoader, TRACE_INTERVAL, CONFIG_RELOAD_INTERVAL);
        TracePanther tracePanther = new TracePanther();
        ttask.schedule(tracePanther, 2 * TRACE_INTERVAL, TRACE_INTERVAL);
        IPIDSender traceFilter = new IPIDSender();
        ttask.schedule(traceFilter, IPID_SEND_INTERVAL, IPID_SEND_INTERVAL);
    }

    /**
     * Initialize module: search the traceroute and tracepath locations on disk; 
     * send mail if both fail.
     */
    @Override
    public MonModuleInfo init(MNode node, String args) {
        super.init(node, args);
        String path = findExecutable("tracepath");
        if (path != null) {
            tracepathCmd = path + tracepathCmd;
        } else {
            useTracepath = false;
        }
        path = findExecutable("traceroute");
        if (path != null) {
            tracerouteCmd = path + tracerouteCmd;
        } else {
            useTraceroute = false;
        }
        if (!(useTracepath || useTraceroute)) {
            logger.log(Level.WARNING,
                    "Tracepath: Couldn't determine the location for neither tracepath or traceroute commands.");
        } else {
            logger.log(Level.INFO, "Tracepath: Using traceroute: '" + tracerouteCmd + "' and tracepath: '"
                    + tracepathCmd + "'");
        }
        try {
            String forceIP = AppConfig.getProperty("lia.Monitor.useIPaddress");
            InetAddress addr;
            if (forceIP != null) {
                addr = InetAddress.getByName(forceIP);
            } else {
                addr = InetAddress.getLocalHost();
            }
            myIP = addr.getHostAddress();
            hostName = addr.getCanonicalHostName();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Tracepath: Cannot determine my own IP!", ex);
            myIP = "127.0.0.1";
            hostName = "localhost";
        }

        myTraceOpts = args.trim();
        if (myTraceOpts.startsWith("\"")) {
            myTraceOpts = myTraceOpts.substring(1);
        }
        if (myTraceOpts.endsWith("\"")) {
            myTraceOpts = myTraceOpts.substring(0, myTraceOpts.length() - 1);
        }
        myTraceOpts = myTraceOpts.trim();
        if (myTraceOpts.length() > 0) {
            myTraceOpts = myTraceOpts.replaceAll("\\s+", ",");
            myTraceOpts = myTraceOpts.replaceAll("\\s*,\\s*", ",");
            logger.log(Level.INFO, "Tracepath: Starting with user options: " + myTraceOpts);
        }
        return null;
    }

    @Override
    public String[] ResTypes() {
        return ResTypes.clone();
    }

    @Override
    public String getOsName() {
        return "linux";
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    @Override
    public Object doProcess() throws Exception {
        final Vector<Object> rez = new Vector<Object>();

        synchronized (newTracepath) {
            rez.addAll(newTracepath);
            newTracepath.clear();
        }

        synchronized (crtPeersOpts) {
            // add last status for all current peers
            /*
            for(Enumeration ren = crtPeersResults.elements(); ren.hasMoreElements(); ){
            	Result o = (Result) ren.nextElement();
            	Result r = new Result(o.FarmName, o.ClusterName, o.NodeName, o.Module, o.param_name);
            	r.param = o.param;
            	r.time = NTPDate.currentTimeMillis();
            	rez.add(r);
            }
            */

            // add the peer expire results from ConfigLoader and
            // the param (hop) expire results from TracePanther
            rez.addAll(nextExpireResults);
            //			rez.addAll(getConfExpireParamsResults(nextResults));
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Returning traces for " + crtPeersResults.size() + " peers and "
                        + nextExpireResults.size() + " expire results.");
            }
            nextExpireResults.clear();
        }

        return rez;
    }

    /**
     * this will return the full path to the given executable  
     */
    private String findExecutable(String exec) {
        BufferedReader buff = procOutput("whereis " + exec);
        if (buff == null) {
            logger.log(Level.SEVERE, "Tracepath: Error running whereis");
            return null;
        }
        try {
            String line = null;
            while ((line = buff.readLine()) != null) {
                StringTokenizer stk = new StringTokenizer(line, " ");
                while (stk.hasMoreTokens()) {
                    String path = stk.nextToken();
                    if (path.endsWith("/" + exec)) {
                        return path;
                    }
                }
            }
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "Tracepath: Couldn't find the location for " + exec, ioe);
        } finally {
       		try{
       			buff.close();
       		}
       		catch (final Exception e){
       			// ignore
       		}
        	
            cleanup();
        }

        return null;
    }

    /**
     * Run tracepath, and if it fails, traceroute and return a result. The opts string can
     * contain a set of parameters for this trace. The parameters are of the following form:
     * param1=value1,param2=value2 ... Param names can be: basePort, useICMP, useTracepath,
     * useTraceroute, and the values: int, bool, bool, bool, respectively.     
     * @param peer 
     * @param opts 
     * @return the trace outcome
     */
    Result runTrace(String peer, String opts) {
        int basePort = 0;
        boolean icmp = true;
        boolean doTracepath = useTracepath;
        boolean doTraceroute = useTraceroute;
        // parse the options and change the defaults, if needed
        StringTokenizer stkOpt = new StringTokenizer(opts, ",");
        while (stkOpt.hasMoreTokens()) {
            StringTokenizer stkVal = new StringTokenizer(stkOpt.nextToken(), "=");
            String param = null, value = null;
            if (stkVal.hasMoreTokens()) {
                param = stkVal.nextToken();
            }
            if (stkVal.hasMoreTokens()) {
                value = stkVal.nextToken();
            }
            if ((param != null) && (value != null) && (param.length() > 0) && (value.length() > 0)) {
                if (param.equals("basePort")) {
                    try {
                        basePort = Integer.parseInt(value);
                    } catch (NumberFormatException ex) {
                        logger.log(Level.WARNING, "Tracepath: Incorrect value for basePort " + value
                                + ". Using default.");
                        basePort = 0;
                    }
                }
                if (param.equals("useICMP")) {
                    icmp = value.equalsIgnoreCase("true");
                }
                if (param.equals("useTracepath")) {
                    doTracepath = value.equalsIgnoreCase("true") && useTracepath; // if exists
                }
                if (param.equals("useTraceroute")) {
                    doTraceroute = value.equalsIgnoreCase("true") && useTraceroute; // if exists
                }
            }
        }
        Result res, resP = null, resR = null;
        if (doTracepath) {
            resP = runTracepath(peer, basePort);
        }
        if (((resP == null) || (resP.param[resP.getIndex("status")] != 0.0)) && doTracepath && (basePort != 33434)) {
            resP = runTracepath(peer, 33434);
        }
        if (((resP == null) || (resP.param[resP.getIndex("status")] != 0.0)) && doTraceroute) {
            resR = runTraceroute(peer, basePort, icmp);
        }
        if (resR != null) {
            res = resR;
        } else if (resP != null) {
            res = resP;
        } else { // this means that neither tracepath nor traceroute were run, so build a error result
            res = new Result(Node.getFarmName(), Node.getClusterName(), peer, ModuleName, ResTypes);
            res.time = NTPDate.currentTimeMillis();
            res.param[0] = STATUS_DISABLED;
        }
        return res;
    }

    /**
     * Run a tracepath to this peer, starting with the base port. If basePort is 0 or negative,
     * it will use the default port. 
     */
    private Result runTracepath(final String peerName, final int basePort) {
        Result res = new Result(Node.getFarmName(), Node.getClusterName(), peerName, ModuleName, new String[0]);
        res.time = NTPDate.currentTimeMillis();
        // peer -> IP
        String peer = peerName;
        try {
            peer = InetAddress.getByName(peer).getHostAddress();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Tracepath: Cannot resolve peer hostname " + peer);
        }
        String cmd = tracepathCmd + peer + (basePort > 0 ? "/" + basePort : "") + " 2>&1";
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "PRunning: " + cmd);
        }
        BufferedReader buff = procOutput(cmd);
        if (buff != null) {
            try {
                Pattern normalHop = Pattern.compile("\\s?\\d+:\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+(\\d+\\.\\d+)ms.*");
                Pattern asymmHop = Pattern
                        .compile("\\s?\\d+:\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+asymm\\s+\\d+\\s+(\\d+\\.\\d+)ms.*");
                Pattern noReply = Pattern.compile("\\s?\\d+:\\s+no reply\\.*");
                Matcher m;
                String line;
                int hop = 0;
                // force the first line to my IP
                res.addSet("" + (hop++) + ":" + myIP, 0);
                buff.readLine();
                while ((line = buff.readLine()) != null) {
                    //					logger.log(Level.INFO, "PLine: "+line);
                    m = normalHop.matcher(line);
                    if (m.matches()) {
                        res.addSet("" + (hop++) + ":" + m.group(1), Double.parseDouble(m.group(2)));
                    } else {
                        m = asymmHop.matcher(line);
                        if (m.matches()) {
                            res.addSet("" + (hop++) + ":" + m.group(1), Double.parseDouble(m.group(2)));
                        } else {
                            m = noReply.matcher(line);
                            if (m.matches()) {
                                res.addSet("" + (hop++) + ":no_reply", 0);
                            }
                        }
                    }
                }
                // remove trailing no_reply hops
                boolean modify = false;
                while ((--hop) > 0) {
                    if (!res.param_name[hop].endsWith(":no_reply")) {
                        break;
                    }
                    modify = true;
                }
                hop++;
                if (modify) {
                    String[] param_name = res.param_name;
                    double[] param = res.param;
                    res.param_name = new String[hop];
                    System.arraycopy(param_name, 0, res.param_name, 0, hop);
                    res.param = new double[hop];
                    System.arraycopy(param, 0, res.param, 0, hop);
                }
                // test if we reached the peer
                if ((hop > 0) && res.param_name[hop - 1].endsWith(peer)) {
                    res.addSet("status", STATUS_OK);
                } else {
                    res.addSet("status", STATUS_DESTINATION_UNREACHED);
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Tracepath: Reading from '" + cmd + "' process:", t);
                res.addSet("status", STATUS_TRACEPATH_FAILED);
            } finally {
            	try{
            		buff.close();
            	}
            	catch (final Exception e){
            		//ignore
            	}
            	
                cleanup();
            }
        } else {
            logger.log(Level.WARNING, "Tracepath: Running '" + cmd + "' returned null output.");
            res.addSet("status", STATUS_TRACEPATH_FAILED);
        }
        return res;
    }

    /**
     * Run a traceroute to this peer, starting with the base port and seding
     * echo icmp packets instead of udp packets if the ifcm flag is on. If basePort
     * is 0 or negative, use the default port.
     */
    private Result runTraceroute(final String peerName, final int basePort, final boolean icmp) {
        Result res = new Result(Node.getFarmName(), Node.getClusterName(), peerName, ModuleName, new String[0]);
        res.time = NTPDate.currentTimeMillis();
        // peer -> IP
        String peer = peerName;
        try {
            peer = InetAddress.getByName(peer).getHostAddress();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Tracepath: Cannot resolve peer hostname " + peer);
        }
        String cmd = tracerouteCmd + (basePort > 0 ? " -p " + basePort : "") + (icmp ? " -I " : " ") + peer + " 2>&1";
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "RRunning: " + cmd);
        }
        BufferedReader buff = procOutput(cmd);
        if (buff != null) {
            try {
                Pattern normalHop = Pattern.compile("\\s?\\d+\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+(\\d+\\.\\d+)\\sms.*");
                Pattern noReply = Pattern.compile("\\s?\\d+\\s+\\*\\.*");
                Matcher m;
                String line;
                res.addSet("0:" + myIP, 0.0); // traceroute does not put the current hop, so put it manually
                int hop = 1; // and that's why hop count starts at one
                while ((line = buff.readLine()) != null) {
                    //					logger.log(Level.INFO, "RLine: "+line);
                    m = normalHop.matcher(line);
                    if (m.matches()) {
                        res.addSet("" + (hop++) + ":" + m.group(1), Double.parseDouble(m.group(2)));
                    } else {
                        m = noReply.matcher(line);
                        if (m.matches()) {
                            res.addSet("" + (hop++) + ":no_reply", 0);
                        }
                    }
                }
                // remove trailing no_reply hops
                boolean modify = false;
                while ((--hop) > 0) {
                    if (!res.param_name[hop].endsWith(":no_reply")) {
                        break;
                    }
                    modify = true;
                }
                hop++;
                if (modify) {
                    String[] param_name = res.param_name;
                    double[] param = res.param;
                    res.param_name = new String[hop];
                    System.arraycopy(param_name, 0, res.param_name, 0, hop);
                    res.param = new double[hop];
                    System.arraycopy(param, 0, res.param, 0, hop);
                }
                // test if we reached the peer
                if ((hop > 0) && res.param_name[hop - 1].endsWith(peer)) {
                    res.addSet("status", STATUS_OK);
                } else {
                    res.addSet("status", STATUS_DESTINATION_UNREACHED);
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Tracepath: Reading from '" + cmd + "' process:", t);
                res.addSet("status", STATUS_TRACEROUTE_FAILED);
            } finally {
            	try{
            		buff.close();
            	}
            	catch (final Exception e){
            		// ignore
            	}
            	
                cleanup();
            }
        } else {
            logger.log(Level.WARNING, "Tracepath: Running '" + cmd + "' returned null output.");
            res.addSet("status", STATUS_TRACEROUTE_FAILED);
        }
        return res;
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        // String configURLbase = args[0]; // get the base of the config url
        monTracepath aa = new monTracepath();

        if (System.getSecurityManager() == null) {
            System.setSecurityManager(new RMISecurityManager());
        }

        String host = null;
        try {
            host = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        MonModuleInfo info = aa.init(new MNode(host, host, null, null), "");
        info.getName();

        try {
            for (int k = 0; k < 100; k++) {
                System.out.println("doProcess-ing...");
                Vector<?> bb = (Vector<?>) aa.doProcess();
                for (int i = 0; i < bb.size(); i++) {
                    Result r = (Result) bb.get(i);
                    System.out.println(r);
                }
                System.out.println("sleeping...");
                Thread.sleep(5 * 1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** this task sends the gathered IPs to the IPID service */
    class IPIDSender extends TimerTask {
        private final int CHUNK_SIZE = 35; // send at most CHUNK_SIZE ip's at a time
        private final int CLEAR_CACHE_AFTER = 100; // the ipCache is cleared after CLEAR_CACHE_AFTER run()'s
        private final Vector<String> myIPs = new Vector<String>();
        private int sentIdx = 0;
        private int clearCache = 0;
        private String prevIpidUrl = "%dummy_value@";

        /** Send to the IPID service last IPs gathered by traces */
        @Override
        public void run() {
            Thread.currentThread().setName("(ML) - Tracepath - IPIDSender");
            logger.fine("Started running IPID Sender");

            if (!Utils.equals(prevIpidUrl, ipidURL)) {
                prevIpidUrl = ipidURL;
                if (ipidURL != null) {
                    logger.info("Sending tracepath info to IPID service: " + ipidURL);
                } else {
                    logger.warning("Not sending IPID data since no topology service is available.");
                }
            }
            if (ipidURL == null) {
                return;
            }

            try {
                if (++clearCache > CLEAR_CACHE_AFTER) {
                    synchronized (crtPeersOpts) {
                        ipCache.clear();
                    }
                    clearCache = 0;
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Cleared ipCache & finished running IPID Sender");
                    }
                    return;
                }
                if (sentIdx >= myIPs.size()) {
                    myIPs.clear();
                    myIPs.addAll(ipCache);
                    sentIdx = 0;
                }
                int max = Math.min(myIPs.size(), sentIdx + CHUNK_SIZE);
                if (sentIdx < max) {
                    StringBuilder query = new StringBuilder(ipidURL);
                    query.append("?");
                    query.append(myIPs.get(sentIdx++));
                    while (sentIdx < max) {
                        query.append("+");
                        query.append(myIPs.get(sentIdx++));
                    }
                    try {
                        URLConnection uconn = new URL(query.toString()).openConnection();
                        uconn.setDefaultUseCaches(false);
                        uconn.setUseCaches(false);
                        BufferedReader br = new BufferedReader(new InputStreamReader(uconn.getInputStream()));
                        String line = br.readLine(); // only read the first line
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, "Got " + line + " for IPID query " + query.toString());
                        }
                        br.close();
                    } catch (Exception ex) {
                        logger.log(Level.WARNING, "Tracepath: Error sending IPID data", ex);
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error sending IPID data", t);
            }
            logger.fine("Finished running IPID Sender");
        }
    }

    /** this task performs the traceroutes */
    class TracePanther extends TimerTask {
        private final Hashtable<String, String> peersToTrace = new Hashtable<String, String>(); // key = hostname (String); value = options (String)

        private final Vector<String> paramsToRemove = new Vector<String>(); // parameters to removed due to the route change

        private final Vector<String> paramsToAdd = new Vector<String>(); // parameters to add due to the route change (just for debug)

        /** each ip that is discovered will be put into cache and then sent to the IPID service */
        private void addIPsToCache(Result r) {
            for (String name : r.param_name) {
                int ipIdx = name.indexOf(":");
                if (ipIdx > 0) {
                    String ip = name.substring(ipIdx + 1);
                    if ((!ip.equals("no_reply")) && (!ipCache.contains(ip))) {
                        ipCache.add(ip);
                    }
                }
            }
        }

        /** 
         * IF NEEDED, generate a result that will expire the old hops from this trace.
         * This means generating an eResult that is put in nextExpireResults vector. 
         */
        private void expireParams(String peer, Result r) {
            Result o = crtPeersResults.get(peer);

            if (o == null) {
                return;
            }

            paramsToRemove.clear();
            paramsToAdd.clear();
            // compare its parameters with the previous stored params for this node
            for (int i = 0; i < o.param.length; i++) {
                if (r.getIndex(o.param_name[i]) == -1) {
                    paramsToRemove.add(o.param_name[i]);
                }
            }
            for (int i = 0; i < r.param.length; i++) {
                if (o.getIndex(r.param_name[i]) == -1) {
                    paramsToAdd.add(r.param_name[i]);
                }
            }
            if (paramsToRemove.size() > 0) {
                // we have expired params
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, peer + " -> Expiring params " + paramsToRemove);
                }
                eResult expRes = new eResult(r.FarmName, r.ClusterName, r.NodeName, r.Module, new String[] {});
                expRes.time = NTPDate.currentTimeMillis();
                for (String string : paramsToRemove) {
                    expRes.addSet(string, null);
                }
                nextExpireResults.add(expRes);
            }
            if ((paramsToAdd.size() > 0) && logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, peer + " +> Adding params " + paramsToAdd);
            }
        }

        /** on each run, do a trace to another peer */
        @Override
        public void run() {
            Thread.currentThread().setName("(ML) - Tracepath - TracePanther");
            logger.log(Level.FINE, "Started running TracePanther");
            try {
                // if it finished all peers, start over
                if (peersToTrace.size() == 0) {
                    synchronized (crtPeersOpts) {
                        peersToTrace.putAll(crtPeersOpts);
                    }
                }
                // just take one peer
                for (Enumeration<String> enp = peersToTrace.keys(); enp.hasMoreElements();) {
                    String peer = enp.nextElement();
                    String options = peersToTrace.remove(peer);
                    try {
                        synchronized (crtPeersOpts) {
                            // before doing the trace, verify that this peer is still wanted
                            if (crtPeersOpts.get(peer) == null) {
                                continue;
                            }
                        }
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, "Tracing " + peer + " with opts: " + options);
                        }
                        // if there are no specific options on how to do the trace to this peer
                        // just use my options, if they exist.
                        if (options.equals("") && (!myTraceOpts.equals(""))) {
                            options = myTraceOpts;
                        }
                        Result r = runTrace(peer, options);
                        synchronized (crtPeersOpts) {
                            // before adding the trace result, verify that this peer is still wanted
                            if (crtPeersOpts.get(peer) == null) {
                                continue;
                            }
                            addIPsToCache(r);
                            expireParams(peer, r);
                            if (logger.isLoggable(Level.FINEST)) {
                                logger.log(Level.FINEST, "Adding result for " + peer + "\n" + r);
                            }
                            crtPeersResults.put(peer, r);

                            newTracepath.add(r);
                        }
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Tracepath: Error tracing " + peer + " " + options, t);
                    }
                    // enough tracing for now; do more next time
                    break;
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error running TracePanther");
            }
            logger.log(Level.FINE, "Finished running TracePanther");
        }
    }

    /** this task checks configuration file changes */
    class ConfigLoader extends TimerTask {
        private String prevConfigUrl = "#dummy_value$";

        /** get Config and IPID services addresses */
		private void refreshURLs() {
			configURL = ipidURL = null;
			String baseUrl = null;

			ServiceItem[] si = MLLUSHelper.getInstance().getTopologyServices();
			if ((si != null) && (si.length != 0) && (si[0].attributeSets.length != 0)) {
				GenericMLEntry gmle = (GenericMLEntry) si[0].attributeSets[0];
				if ((gmle.hash != null) && (gmle.hash.get("URL") != null)) {
					baseUrl = (String) gmle.hash.get("URL");
					configURL = baseUrl + "/GetTraceConf";
					ipidURL = baseUrl + "/AddTrace";
				}
			}

			if (TRACEPATH_CONFIG_URL != null && TRACEPATH_CONFIG_URL.length() > 0) {
				configURL = TRACEPATH_CONFIG_URL;
			}

			if (!Utils.equals(prevConfigUrl, configURL)) {
				prevConfigUrl = configURL;
				if (configURL == null) {
					logger.warning("Tracepath: No Geo & IPID service was found yet and none was specified.");
				} else if ((TRACEPATH_CONFIG_URL != null) && !TRACEPATH_CONFIG_URL.isEmpty()) {
					logger.info("Tracepath: Using specified confgURL: " + configURL);
				} else {
					logger.info("Tracepath: Using configURL & IPID services base URL: " + baseUrl);
				}
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
         * - Tracepath's options myTraceOpts
         * - Peers with their status (DEST_UNREACH, DISABLED, EXPIRED etc.) 
         */
        private String getTracepathParams() {
            StringBuilder result = new StringBuilder();
            String farmName = "UNKNOWN_FARM";
            if ((Node != null) && (Node.farm != null) && (Node.farm.name != null)) {
                farmName = Node.farm.name;
            }
            String farmGroups = AppConfig.getProperty("lia.Monitor.group", "UNKNOWN_GROUP");
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
                result.append(URLEncoder.encode(myIP, "UTF-8"));
                result.append("&HostName=");
                result.append(URLEncoder.encode(hostName, "UTF-8"));
                result.append("&MLversion=");
                result.append(URLEncoder.encode(MLver, "UTF-8"));
                result.append("&MLdate=");
                result.append(URLEncoder.encode(MLdate, "UTF-8"));
                result.append("&LONG=");
                result.append(URLEncoder.encode(LONG, "UTF-8"));
                result.append("&LAT=");
                result.append(URLEncoder.encode(LAT, "UTF-8"));
                result.append("&TraceOpts=");
                result.append(URLEncoder.encode(myTraceOpts, "UTF-8"));
                // compute peers' status
                StringBuilder peerStat = new StringBuilder();
                for (Enumeration<String> en = crtPeersResults.keys(); en.hasMoreElements();) {
                    String peerName = en.nextElement();
                    Result peerResult = crtPeersResults.get(peerName);
                    int statusIdx = peerResult.getIndex("status");
                    peerStat.append("&PeerStat=");
                    peerStat.append(URLEncoder.encode(peerName + "|"
                            + (statusIdx != -1 ? peerResult.param[statusIdx] : STATUS_INTERNAL_ERR), "UTF-8"));
                }
                result.append(peerStat);
            } catch (UnsupportedEncodingException uex) {
                logger.log(Level.WARNING, "Cannot encode Tracepath params", uex);
            }
            return result.toString();
        }

        /**
         * Mark this peer as being expired -> will send a result having the 
         * status expired for this peer. Also, the peer will be removed from
         * farm's configuration. 
         */
        private void expirePeer(String peer) {
            Result res = new Result(Node.getFarmName(), Node.getClusterName(), peer, ModuleName, ResTypes);
            res.time = NTPDate.currentTimeMillis();
            res.param[0] = STATUS_REMOVED;
            nextExpireResults.add(res);
            // add another result to make it expire from the configuration
            eResult resExp = new eResult(Node.getFarmName(), Node.getClusterName(), peer, ModuleName, null);
            resExp.time = NTPDate.currentTimeMillis();
            resExp.param = null;
            resExp.param_name = null;
            nextExpireResults.add(resExp);
        }

        /** 
         * Returns the reloaded config. This can be null if loading config 
         * failes and means that the previous config should be kept. 
         */
        private Hashtable<String, String> reloadConfig() {
            if (configURL == null) {
                return null; // keep previous config
            }

            // try loading the new config
            Hashtable<String, String> newPeers = null;
            URLConnection urlc = null;
            InputStream is = null;
            DataOutputStream os = null;
            BufferedReader in = null;
            try {
                urlc = new URL(configURL).openConnection();

                if (!configURL.startsWith("file:")) {
                    // We are going to do a POST
                    urlc.setDoInput(true);
                    urlc.setDoOutput(true);
                    // Quick fix to bypass http proxies 
                    urlc.setDefaultUseCaches(false);
                    urlc.setUseCaches(false);
                    // We are going to send URLEncoed data
                    urlc.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                    os = new DataOutputStream(urlc.getOutputStream());
                    String tracepathParams = getTracepathParams();
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "POSTing the following data:\n" + tracepathParams);
                    }
                    os.writeBytes(tracepathParams);
                    os.flush();
                    os.close();
                    os = null;
                }
                // Ok, now let's see what we got
                is = urlc.getInputStream();
                in = new BufferedReader(new InputStreamReader(is));
                Vector<String> peerOpts = new Vector<String>();
                Vector<String> peers = new Vector<String>();
                String line;
                boolean foundMyself = false;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.equals("") || line.startsWith("#")) {
                        continue;
                    }
                    StringTokenizer stk = new StringTokenizer(line);
                    if (stk.hasMoreTokens()) {
                        String firstWord = stk.nextToken();
                        if ("TraceOpts".equals(firstWord) && stk.hasMoreTokens()) {
                            peerOpts.add(stk.nextToken());
                        } else if (myIP.equals(firstWord)) {
                            foundMyself = true;
                            while (stk.hasMoreTokens()) {
                                peers.add(stk.nextToken());
                            }
                        }
                    }
                }
                if (foundMyself) {
                    newPeers = new Hashtable<String, String>();
                    // peers can be empty. In this case, tracepath will be 'disabled' on this node
                    for (int i = 0; i < peers.size(); i++) {
                        newPeers.put(peers.get(i), peerOpts.size() > i ? peerOpts.get(i) : "");
                    }
                }
                if (newPeers == null) {
                    logger.log(Level.FINE, "I didn't find myself [" + myIP + "] in the config from " + configURL
                            + "\nKeeping previous config.");
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Failed loading config from " + configURL + "\nKeeping previous config.", t);
                newPeers = null;
                configURL = null;
            } finally {
                try {
                    if (in != null) {
                        in.close();
                        in = null;
                    }
                } catch (Throwable tf) {
                    // ignore
                }
                try {
                    if (is != null) {
                        is.close();
                        is = null;
                    }
                } catch (Throwable tf) {
                    // ignore
                }
                try {
                    if (os != null) {
                        os.close();
                        os = null;
                    }
                } catch (Throwable tf) {
                    // ignore
                }
                urlc = null;
            }
            return newPeers;
        }

        /** call reloadConfig and then synch crtPeers with new peers */
        private void refreshConfig() {
            Hashtable<String, String> newPeers = reloadConfig();
            if (newPeers == null) {
                return; // keep the same config if I couldn't reload it
            }

            // add new peers and expire old ones
            synchronized (crtPeersOpts) {
                // add new peers and refresh existing ones, if options have changed
                for (Enumeration<String> enp = newPeers.keys(); enp.hasMoreElements();) {
                    String peer = enp.nextElement();
                    String opts = newPeers.get(peer);
                    if (crtPeersOpts.put(peer, opts) == null) {
                        // also add the not yet traced current status for this new peer
                        Result res = new Result(Node.getFarmName(), Node.getClusterName(), peer, ModuleName, ResTypes);
                        res.time = NTPDate.currentTimeMillis();
                        res.param[0] = STATUS_NOT_TRACED_YET;
                        crtPeersResults.put(peer, res);
                        logger.log(Level.INFO, "Tracepath: adding peer " + peer + " with TraceOpts=" + opts);
                    }
                }
                // remove old peers, by sending expired results for each
                for (Enumeration<String> enp = crtPeersOpts.keys(); enp.hasMoreElements();) {
                    String peer = enp.nextElement();
                    if (newPeers.get(peer) == null) {
                        expirePeer(peer);
                        crtPeersOpts.remove(peer);
                        crtPeersResults.remove(peer);
                        logger.log(Level.INFO, "Tracepath: removing peer " + peer);
                    }
                }
            }
        }

        @Override
        public void run() {
            Thread.currentThread().setName("(ML) - Tracepath - ConfigLoader");
            logger.log(Level.FINE, "Started running ConfigLoader");
            try {
                refreshURLs();
                refreshConfig();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Tracepath: Failed loading config", t);
                configURL = null;
            }
            logger.log(Level.FINE, "Finished running ConfigLoader");
        }
    }
}
