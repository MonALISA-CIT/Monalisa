package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.rmi.RMISecurityManager;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniSerFarmMon.MLLUSHelper;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.GenericMLEntry;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.cmdExec;
import lia.Monitor.monitor.eResult;
import lia.util.ntp.NTPDate;
import net.jini.core.lookup.ServiceItem;

public class monOldTracepath extends cmdExec implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = 1192762856692414674L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monOldTracepath.class.getName());

    static public String ModuleName = "monOldTracepath";
    static public String[] ResTypes = { "route" };
    static public final String tracepathCmd = "/usr/sbin/tracepath ";
    static public final String tracerouteCmd = "traceroute -q 1 -m 30 ";
    static public final String OsName = "linux";

    static String useTraceroute = AppConfig.getProperty("lia.Monitor.Tracepath.useTraceroute", "false").trim();
    static String configURL = null;
    static String whoisQuery = null;
    static String geoQuery = null;
    static String ipidQuery = null;

    static long CONFIG_RELOAD_INTERVAL = 2 * 60 * 1000; // 2 minutes
    static long TRACE_INTERVAL = 30 * 1000; // 20 secs between 2 traceroutes
    static long TRACE_EXPIRE_DELAY = 3 * 60 * 60 * 1000; // 3 hours

    Vector myHostnames = new Vector(); // list with all my host names and IP - used to search my config
    Vector peers = new Vector(); // list with peer hostnames
    Vector nextResults = new Vector(); // list with the next results set
    Vector nextFilteredResults = new Vector(); // list with filtered results

    String myIPaddress;
    String myHostname;
    String myFullHostname;

    public monOldTracepath() {
        super(ModuleName);
        info.ResTypes = ResTypes;
        info.name = ModuleName;
        isRepetitive = true;
        initDefaults();
        TimerTask task = new ConfigLoader();
        Timer ttask = new Timer(true);
        ttask.schedule(task, 0, CONFIG_RELOAD_INTERVAL);
        Thread tracer = new TracePanther();
        tracer.start();
        Thread filter = new TraceFilter();
        filter.start();
    }

    private void initDefaults() {
        String forceIP = AppConfig.getProperty("lia.Monitor.useIPaddress");
        try {
            InetAddress addr = null;
            if (forceIP != null) {
                addr = InetAddress.getByName(forceIP);
            } else {
                addr = InetAddress.getLocalHost();
            }
            myIPaddress = addr.getHostAddress();
            myHostname = addr.getHostName();
            myFullHostname = addr.getCanonicalHostName();
            logger.log(Level.INFO,
                    "monOldTracepath: Starting... I will search conf file (when available from Geo&IPID service) for "
                            + myIPaddress + ", " + myHostname + ", " + myFullHostname);
            myHostnames.add(myHostname);
            myHostnames.add(myFullHostname);
            myHostnames.add(myIPaddress);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "monOldTracepath: could not get my IP address!");
        }
    }

    /** this thread checks configuration file changes */
    class ConfigLoader extends TimerTask {
        Vector crtPeers = new Vector();

        public void getIPidServiceAddress() {
            // get IPID service address
            ServiceItem[] si = MLLUSHelper.getInstance().getTopologyServices();
            if ((si == null) || (si.length == 0) || (si[0].attributeSets.length == 0)) {
                logger.log(Level.SEVERE, "No Geo & IPID service was found (yet)");
                ipidQuery = whoisQuery = geoQuery = null;
            } else {
                GenericMLEntry gmle = (GenericMLEntry) si[0].attributeSets[0];
                if (gmle.hash != null) {
                    String baseUrl = (String) gmle.hash.get("URL");
                    boolean alreadyFound = ipidQuery != null;
                    configURL = baseUrl + "/tracepath.conf";
                    ipidQuery = baseUrl + "/AddTrace";
                    whoisQuery = baseUrl + "/FindIP";
                    geoQuery = baseUrl + "/FindAS";
                    logger.log((alreadyFound ? Level.FINE : Level.INFO), "Geo & IPID service base URL: " + baseUrl);
                }
            }
        }

        @Override
        public void run() {
            try {
                getIPidServiceAddress();
                logger.log(Level.FINE, "monOldTracepath: trying to (re)load configuration");
                URLConnection uconn = new URL(configURL).openConnection();
                uconn.setDefaultUseCaches(false);
                uconn.setUseCaches(false);
                BufferedReader in = new BufferedReader(new InputStreamReader(uconn.getInputStream()));
                String line;
                crtPeers.clear();
                boolean active = false;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.equals("") || line.startsWith("#")) {
                        continue;
                    }
                    StringTokenizer stk = new StringTokenizer(line);
                    String name = stk.nextToken();
                    boolean itsMe = false;
                    for (int i = 0; i < myHostnames.size(); i++) {
                        String myName = (String) myHostnames.get(i);
                        if (myName.equals(name)) {
                            itsMe = true;
                            break;
                        }
                    }
                    if (itsMe) {
                        active = true;
                        while (stk.hasMoreTokens()) {
                            String peer = stk.nextToken();
                            crtPeers.add(peer);
                        }
                        break;
                    }
                }
                if (active) {
                    synchronized (peers) {
                        for (int i = 0; i < peers.size(); i++) {
                            String oldPeer = (String) peers.get(i);
                            if (!crtPeers.contains(oldPeer)) {
                                peers.remove(i);
                                i--;
                                eResult eresult = new eResult(Node.getFarmName(), Node.getClusterName(), oldPeer,
                                        ModuleName, new String[0]);
                                eresult.time = NTPDate.currentTimeMillis();
                                nextResults.add(eresult);
                                logger.log(Level.INFO, "monOldTracepath: removedPeer " + oldPeer);
                            }
                        }
                        // just for debugging purposes
                        for (int i = 0; i < crtPeers.size(); i++) {
                            String name = (String) crtPeers.get(i);
                            if (!peers.contains(name)) {
                                peers.add(name);
                                logger.log(Level.INFO, "monOldTracepath: adding trace peer node " + name);
                            }
                        }
                    }
                } else {
                    synchronized (peers) {
                        peers.clear();
                    }
                    logger.log(Level.INFO, "monOldTracepath: Module NOT active. (haven't found myself in confFile)");
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "monOldTracepath: Failed loading config from " + configURL, ex);
            }
        }
    }

    /** this thread performs the traceroutes */
    class TracePanther extends Thread {
        Vector crtPeers = new Vector();

        @Override
        public void run() {
            while (true) {
                synchronized (peers) {
                    crtPeers.clear();
                    crtPeers.addAll(peers);
                }
                for (int i = 0; i < crtPeers.size(); i++) {
                    String peer = (String) crtPeers.get(i);
                    try {
                        Vector route = null;
                        if (!useTraceroute.equals("true")) {
                            route = getTracepathCmdOut(peer);
                        }
                        if (route == null) {
                            route = getTracerouteCmdOut(peer);
                        }
                        if (route == null) {
                            route = new Vector();
                        }
                        // building eResult...
                        eResult eresult = new eResult(Node.getFarmName(), Node.getClusterName(), peer, ModuleName,
                                ResTypes);
                        eresult.time = NTPDate.currentTimeMillis();
                        eresult.param[0] = route;
                        synchronized (peers) {
                            nextResults.add(eresult);
                        }
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Error tracing", t);
                    }
                    try {
                        Thread.sleep(TRACE_INTERVAL);
                    } catch (InterruptedException ex) {
                        // ignore
                    }
                }
                try {
                    Thread.sleep(TRACE_INTERVAL);
                } catch (InterruptedException ex) {
                    // ignore
                }
            }
        }
    }

    /** this contains old TracepathFilter - finds information about IPs*/
    class TraceFilter extends Thread {
        Vector tempRawTraces = new Vector(); // temp for traces received from module
        Hashtable traceCache = new Hashtable(); // keep a cache with traces to all peers
        Hashtable ipCache = new Hashtable(); // keep a cache with info abut each IP
        Hashtable geoCache = new Hashtable(); // keep a cache with info about each AS

        /** check new traces from monOldTracepath */
        private void checkNewTraces() {
            synchronized (nextResults) {
                tempRawTraces.clear();
                tempRawTraces.addAll(nextResults);
                nextResults.clear();
            }
            for (int i = 0; i < tempRawTraces.size(); i++) {
                resolveTrace((eResult) tempRawTraces.elementAt(i));
            }
        }

        /** get IP and Geo information about each IP in this trace */
        private void resolveTrace(eResult r) {
            if (r.param_name.length == 0) {
                traceCache.remove(r.NodeName);
                logger.log(Level.INFO, "TraceFilter: removing trace to " + r.NodeName);
                nextFilteredResults.add(r);
            } else {
                Vector trace = (Vector) r.param[0];
                //				logger.log(Level.INFO,"TracepathFilter: parsing tracepath output to "+r.NodeName);
                Vector newTrace = parseTracepathOutput(trace);
                eResult oldRez = (eResult) traceCache.get(r.NodeName);
                Vector oldTrace = (oldRez == null ? null : (Vector) oldRez.param[0]);
                if ((oldRez == null) || tracesDiffer(newTrace, oldTrace)) {
                    //					logger.log(Level.INFO, "TraceFilter: NEW or CHANGED -> filling data");
                    sendIPidData(newTrace);
                    fillWhoisData(newTrace);
                    fillGeoData(newTrace);
                    addNewIPsToCache(newTrace);
                    // build the eResult, reusing eResult from module
                    r.param[0] = newTrace;
                    r.time = NTPDate.currentTimeMillis();
                    traceCache.put(r.NodeName, r);
                    // send to clients only if new or has changed
                    nextFilteredResults.add(r);
                } else {
                    sendIPidData(oldTrace);
                    oldRez.time = NTPDate.currentTimeMillis();
                    nextFilteredResults.add(oldRez);
                }
            }
        }

        /** add the new IPs in this trace into the ipCache */
        void addNewIPsToCache(Vector trace) {
            for (int i = 0; i < trace.size(); i++) {
                Hashtable hostInfo = (Hashtable) trace.get(i);
                if (!(ipCache.containsValue(hostInfo) || ((String) hostInfo.get("ip")).equals("?"))) {
                    ipCache.put(hostInfo.get("ip"), hostInfo);
                    //					logger.log(Level.INFO, "TraceFilter: added "+hostInfo.get("host")+" = "+hostInfo.get("ip")+" to ipCache");
                }
            }
        }

        /** compare 2 traces and say if they differ */
        boolean tracesDiffer(Vector t1, Vector t2) {
            if (t1.size() != t2.size()) {
                return true;
            }
            for (int i = 0; i < t1.size(); i++) {
                Hashtable h1 = (Hashtable) t1.get(i);
                Hashtable h2 = (Hashtable) t2.get(i);
                if (!h1.get("ip").equals(h2.get("ip"))) {
                    return true;
                }
            }
            return false;
        }

        /** 
         * parse a vector trace from monOldTracepath and produce a
         * vector containing hostInfo hashtables for each router
         * in the path. These hostInfos are cached in ipCache to
         * avoid duplication for common paths to peer farms
         */
        Vector parseTracepathOutput(Vector trace) {
            Vector rez = new Vector();
            try {
                for (Iterator tit = trace.iterator(); tit.hasNext();) {
                    String line = (String) tit.next();
                    StringTokenizer lt = new StringTokenizer(line, " ");
                    String hostName = lt.nextToken(); // hostname
                    String ip = lt.nextToken(); // ip
                    String delay = lt.nextToken(); // delay
                    boolean asymm = lt.hasMoreTokens(); // asymm follows
                    // check if this info is already cached
                    Hashtable hostInfo = (Hashtable) ipCache.get(ip);
                    if (hostInfo == null) {
                        // nope, create a new hostInfo record
                        // this hostInfo will be saved into ipCache only after fillGeoData
                        // to avoid messing things around
                        hostInfo = new Hashtable();
                        // fill parsed data
                        hostInfo.put("host", hostName);
                        hostInfo.put("ip", ip);
                    }
                    hostInfo.put("delay", delay); // this is always written
                    if (asymm) {
                        hostInfo.put("asymm", "asymm");
                    } else {
                        hostInfo.remove("asymm");
                    }
                    rez.add(hostInfo); // add record to the result trace
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Failed parsing trace:\n" + trace, ex);
                rez.clear();
            }
            return rez;
        }

        /** for each hostInfo in trace, fill AS, NETNAME, DESCR data */
        void fillWhoisData(Vector trace) {
            if ((trace.size() == 0) || (whoisQuery == null) || !whoisQuery.startsWith("http")) {
                return;
            }
            // build whois query
            StringBuilder query = new StringBuilder(whoisQuery);
            int crt = 0;
            for (int i = 0; i < trace.size(); i++) {
                // get next ip in trace
                Hashtable hostInfo = (Hashtable) trace.get(i);
                if (ipCache.containsValue(hostInfo)) {
                    continue; // if it's already in ipCache, skip this
                }
                String ip = (String) hostInfo.get("ip");
                if (ip.equals("?")) {
                    continue; // if it's a no reply node, skip it
                }
                crt++;
                if (crt == 1) {
                    query.append("?");
                } else {
                    query.append("+");
                }
                // append it to the query
                query.append(ip);
            }
            String allQuery = query.toString();
            try {
                // try solving it
                URLConnection uconn = new URL(allQuery).openConnection();
                uconn.setDefaultUseCaches(false);
                uconn.setUseCaches(false);
                BufferedReader br = new BufferedReader(new InputStreamReader(uconn.getInputStream()));
                // info about hosts is separated by an empty line
                String line = null;
                int crtHost = 0;
                for (int i = 0; i < trace.size(); i++) {
                    // get next ip in trace
                    Hashtable hostInfo = (Hashtable) trace.get(i);
                    if (ipCache.containsValue(hostInfo) || ((String) hostInfo.get("ip")).equals("?")) {
                        continue; // if it's already in ipCache or "no reply", skip this
                    }
                    // the next data received from the query is for this record
                    while (((line = br.readLine()) != null) && !line.equals("")) {
                        // read data 'till end of file or empty line received
                        line = line.replaceAll("[ ]+", " ").trim();
                        int idp = line.indexOf(":");
                        if (idp < 0) {
                            continue;
                        }
                        String key = line.substring(0, idp).toLowerCase();
                        if (key.equalsIgnoreCase("origin")) {
                            hostInfo.put("as", line.substring(line.indexOf("AS") + 2));
                        } else if (key.equalsIgnoreCase("netname")) {
                            String oldNet = (String) hostInfo.get("net");
                            String newNet = line.substring(line.indexOf(":") + 2);
                            if ((oldNet == null) || (oldNet.length() > newNet.length())) {
                                hostInfo.put("net", newNet);
                            }
                        } else if (key.equalsIgnoreCase("descr")) {
                            String oldDescr = (String) hostInfo.get("descr");
                            String newDescr = line.substring(line.indexOf(":") + 2);
                            if (oldDescr == null) {
                                hostInfo.put("descr", newDescr);
                            } else {
                                hostInfo.put("descr", oldDescr + "\n" + newDescr);
                            }
                        }
                    }
                }
                br.close();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error filling whois data", ex);
            }
        }

        /** Send trace IPs to IPID service */
        void sendIPidData(Vector trace) {
            // build whois query
            if ((ipidQuery == null) || !ipidQuery.startsWith("http")) {
                return; // skip sending IPID data
            }
            StringBuilder query = new StringBuilder(ipidQuery);
            int crt = 0;
            for (int i = 0; i < trace.size(); i++) {
                // get next ip in trace
                Hashtable hostInfo = (Hashtable) trace.get(i);
                String ip = (String) hostInfo.get("ip");
                if (ip.equals("?")) {
                    continue; // if it's a no reply node, skip it
                }
                crt++;
                if (crt == 1) {
                    query.append("?");
                } else {
                    query.append("+");
                }
                // append it to the query
                query.append(ip);
            }
            String allQuery = query.toString();
            try {
                //logger.log(Level.INFO, "IPID query: "+allQuery);
                BufferedReader br = new BufferedReader(new InputStreamReader(new URL(allQuery).openStream()));
                String line = br.readLine(); // only read the first line
                //logger.log(Level.INFO, "IPID service response: "+line);
                br.close();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error sending IPID data", ex);
            }
        }

        /** for each hostInfo in trace, fill LAT, LONG, CITY, STATE, COUNTRY */
        void fillGeoData(Vector trace) {
            if ((trace.size() == 0) || (geoQuery == null) || !geoQuery.startsWith("http")) {
                return;
            }
            int[] vas = new int[trace.size()];
            int nvas = 0;
            StringBuilder query = new StringBuilder(geoQuery);
            for (int i = 0; i < trace.size(); i++) {
                Hashtable hostInfo = (Hashtable) trace.get(i);
                if (ipCache.containsValue(hostInfo) || ((String) hostInfo.get("ip")).equals("?")) {
                    continue; // in cache or "no reply" type
                }

                String as = (String) hostInfo.get("as");
                // if this as is invalid or already in geoCache
                if ((as == null) || as.equals("") || (geoCache.get(as) != null)) {
                    continue; // then skip it
                }
                int a = Integer.parseInt(as);
                // store in `vas' all unknown and unique ASes 
                boolean found = false;
                for (int j = 0; j < nvas; j++) {
                    if (vas[j] == a) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    continue;
                }
                vas[nvas] = a;
                // and build the query
                if (nvas == 0) {
                    query.append("?");
                } else {
                    query.append("+");
                }
                query.append(as);
                nvas++;
            }
            String allQuery = query.toString();
            if (nvas > 0) {
                try {
                    URLConnection uconn = new URL(allQuery).openConnection();
                    uconn.setDefaultUseCaches(false);
                    uconn.setUseCaches(false);
                    BufferedReader br = new BufferedReader(new InputStreamReader(uconn.getInputStream()));
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        if (line.equals("")) {
                            continue;
                        }
                        int p = line.indexOf("\t");
                        String key = line.substring(0, p);
                        geoCache.put(key, line.substring(p + 1));
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            for (int i = 0; i < trace.size(); i++) {
                Hashtable hostInfo = (Hashtable) trace.get(i);
                // check if already cached
                if (ipCache.containsValue(hostInfo)) {
                    continue;
                }
                String key = (String) hostInfo.get("as");
                // check for a valid as
                if ((key == null) || key.equals("")) {
                    continue;
                }
                String geoData = (String) geoCache.get(key);
                // check if the retrieved data is valid
                if ((geoData == null) || geoData.equals("")) {
                    continue;
                }
                StringTokenizer stk = new StringTokenizer(geoData, "\t");
                hostInfo.put("lat", stk.nextToken());
                hostInfo.put("long", stk.nextToken());
                String newNet = stk.nextToken();
                String oldNet = (String) hostInfo.get("net");
                if (oldNet == null) {
                    hostInfo.put("net", newNet);
                }
                String city = stk.nextToken();
                if (!city.equals("?") && !city.equals("")) {
                    hostInfo.put("city", city);
                }
                String state = stk.nextToken();
                if (!state.equals("?") && !state.equals("")) {
                    hostInfo.put("state", state);
                }
                String country = stk.nextToken();
                if (!country.equals("?") && !country.equals("")) {
                    hostInfo.put("country", country);
                }
            }
        }

        /** verify if there are traces older than MAX_EXPIRE_DELAY and delete them */
        void check4deadTraces() {
            long now = NTPDate.currentTimeMillis();
            for (Enumeration en = traceCache.keys(); en.hasMoreElements();) {
                String peerNodeName = (String) en.nextElement();
                eResult res = (eResult) traceCache.get(peerNodeName);
                // check if the trace has expired
                if ((now - res.time) > TRACE_EXPIRE_DELAY) {
                    traceCache.remove(peerNodeName);
                    res.param_name = new String[0];
                    res.param = new Object[0];
                    res.time = now;
                    logger.log(Level.INFO, "TraceFilter: trace info to " + res.NodeName + " has expired. Removing it. "
                            + res.param_name.length);
                    // queue this result to announce the clients that 
                    // this link was deleted
                    nextFilteredResults.add(res);
                }
            }
        }

        @Override
        public void run() {
            while (true) {
                try {
                    try {
                        Thread.sleep(6000);
                    } catch (Exception e) {
                        // empty
                    }
                    checkNewTraces();
                    try {
                        Thread.sleep(500);
                    } catch (Exception e) {
                        // empty
                    }
                    check4deadTraces();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Error in TraceFilter", t);
                }
            }
        }
    }

    @Override
    public String[] ResTypes() {
        return ResTypes;
    }

    @Override
    public String getOsName() {
        return OsName;
    }

    @Override
    public Object doProcess() throws Exception {
        Vector rez = new Vector();
        synchronized (peers) {
            rez.addAll(nextFilteredResults);
            nextFilteredResults.clear();
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "monOldTracepath: sending vector with " + rez.size() + " results");
        }
        return rez;
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    // each element in the result vector contains a string with:
    // hostname ip delay
    // delay is delay between this and previous hop
    // if there is no reply from a router the ip and host are "?"
    Vector getTracerouteCmdOut(String peer) throws Exception {
        Vector rez = new Vector();
        try {
            String cmd = tracerouteCmd + peer;
            BufferedReader buff = procOutput(cmd);

            String hh = myHostname;
            if (hh.length() < myFullHostname.length()) {
                hh = myFullHostname;
            }
            int crtLine = 0;
            double lastDelay = 0;
            for (;;) {
                String lin = buff.readLine();
                crtLine++;
                if (lin == null) {
                    break;
                }
                if (crtLine == 1) {
                    rez.add(hh + " " + myIPaddress + " 0 ");
                } else if (lin.charAt(2) != ' ') {
                    continue;
                } else if (lin.indexOf("*") > 0) {
                    rez.add("? ? 0 ");
                } else {
                    if (lin.length() < 4) {
                        continue;
                    }
                    lin = lin.substring(4).replaceAll("[ ]+", " ");
                    StringTokenizer lt = new StringTokenizer(lin, " ");
                    String hostName = lt.nextToken(); // hostname
                    String ip = lt.nextToken(); // ip
                    ip = ip.substring(1, ip.length() - 1); // clear `(' and `)'
                    String delay = lt.nextToken(); // delay
                    double crtDelay = Double.parseDouble(delay);
                    double deltaDelay = crtDelay - lastDelay;
                    rez.add(hostName + " " + ip + " " + (deltaDelay > 0 ? deltaDelay : 0));
                    lastDelay = crtDelay;
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "failed running traceroute", t);
            return null;
        }
        cleanup();
        return rez;
    }

    // each element in the result vector contains a string with:
    // hostname ip delay asymm
    // asymm may not be present if the link to that router is asymmetric
    // delay is delay between this and previous hop
    // if there is no reply from a router the ip and host are "?"
    Vector getTracepathCmdOut(String peer) throws Exception {
        Vector rez = new Vector();
        try {
            String cmd = tracepathCmd + " " + peer;
            BufferedReader buff = procOutput(cmd);

            String hh = myHostname;
            if (hh.length() < myFullHostname.length()) {
                hh = myFullHostname;
            }
            int crtLine = 0;
            double lastDelay = 0;
            for (;;) {
                String lin = buff.readLine();
                crtLine++;
                if (lin == null) {
                    break;
                }
                if (crtLine == 1) {
                    rez.add(hh + " " + myIPaddress + " 0 ");
                } else if ((lin.charAt(2) != ':') && (lin.charAt(3) != ':')) {
                    continue;
                } else if (lin.indexOf("no reply") > 0) {
                    rez.add("? ? 0 ");
                } else {
                    lin = lin.substring(5).replaceAll("[ ]+", " ");
                    String hostName = null;
                    String ip = null;
                    String delay = null;
                    // don't care anymore about asymm
                    StringTokenizer lt = new StringTokenizer(lin, " ");
                    hostName = lt.nextToken();
                    if (lt.hasMoreTokens()) {
                        ip = lt.nextToken();
                        if (ip.startsWith("(")) {
                            ip = ip.substring(1, ip.length() - 1); // clear `(' and `)'
                        } else {
                            delay = ip;
                            ip = hostName;
                        }
                    } else {
                        ip = hostName;
                    }
                    if ((delay == null) && lt.hasMoreTokens()) {
                        delay = lt.nextToken();
                    }
                    if ((delay != null) && delay.equals("asymm")) {
                        lt.nextToken();
                        delay = null;
                        if (lt.hasMoreTokens()) {
                            delay = lt.nextToken();
                        }
                    }
                    double crtDelay = lastDelay;
                    if ((delay != null) && delay.endsWith("ms")) {
                        delay = delay.substring(0, delay.length() - 2); // remove milliseconds
                        crtDelay = Double.parseDouble(delay);
                    }
                    double deltaDelay = crtDelay - lastDelay;
                    rez.add(hostName + " " + ip + " " + (deltaDelay > 0 ? deltaDelay : 0) + " ");
                    lastDelay = crtDelay;
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "failed running tracepath", t);
            return null;
        }
        cleanup();
        return rez;
    }

    static public void main(String[] args) {
        String host = args[0];
        monOldTracepath aa = new monOldTracepath();
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        if (System.getSecurityManager() == null) {
            System.setSecurityManager(new RMISecurityManager());
        }

        MonModuleInfo info = aa.init(new MNode(args[0], ad, null, null), null, null);
        //		try{
        //			BufferedReader br = new BufferedReader(new FileReader(args[0]));
        //			String trace = aa.getTracepathCmdOut(br);
        //			System.out.println("parsing Trace output");
        //			Vector vv = aa.parseTracepathOutput(trace);
        //			System.out.println("filling whois data");
        //			aa.fillWhoisData(vv);
        //			System.out.println("filling geo data");
        //			aa.fillGeoData(vv);
        //			for(int i=0; i<vv.size(); i++){
        //				Hashtable router = (Hashtable) vv.get(i);
        //				System.out.println("host="+router.get("host")
        //						+" ip="+router.get("ip")
        //						+" as="+router.get("as")
        //						+" net="+router.get("net")
        //						+" delay="+router.get("delay")
        //						+" long="+router.get("long")
        //						+" lat="+router.get("lat")
        //						+" city="+router.get("city")
        //						+" state="+router.get("state")
        //						+" country="+router.get("country")
        //						+" descr:\n"+router.get("descr"));
        //			}
        //		}catch(Exception e){
        //		}
        try {
            for (int k = 0; k < 100; k++) {
                System.out.println("doProcess-ing...");
                Vector bb = (Vector) aa.doProcess();
                for (int i = 0; i < bb.size(); i++) {
                    eResult r = (eResult) bb.get(i);
                    System.out.println(r);
                }
                System.out.println("sleeping...");
                Thread.sleep(5 * 1000);
            }
        } catch (Exception e) {
            ;
        }
    }
}
