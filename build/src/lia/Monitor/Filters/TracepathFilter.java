package lia.Monitor.Filters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.rmi.RemoteException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.DataCache.Cache;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MonitorClient;
import lia.Monitor.monitor.MonitorFilter;
import lia.Monitor.monitor.dbStore;
import lia.Monitor.monitor.eResult;
import lia.util.ntp.NTPDate;

public class TracepathFilter implements MonitorFilter, Runnable {

    /**
     * 
     */
    private static final long serialVersionUID = 4013603582693425061L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(TracepathFilter.class.getName());

    static String Name = "TracepathFilter";
    static String whoisQuery = AppConfig.getProperty("lia.Monitor.Tracepath.WhoisScript",
            "http://monalisa2.cern.ch/cgi-bin/findip");
    static String geoQuery = AppConfig.getProperty("lia.Monitor.Tracepath.GeoScript",
            "http://monalisa2.cern.ch/cgi-bin/findas");
    static String ipidQuery = AppConfig.getProperty("lia.Monitor.Tracepath.IPidScript",
            "http://monalisa2.cern.ch/cgi-bin/topo/addtrace.py");

    Hashtable ipCache = new Hashtable();
    Hashtable geoCache = new Hashtable();
    Hashtable traceCache = new Hashtable();
    static long TRACE_EXPIRE_DELAY = 12 * 60 * 1000; // 12 minutes
    static long SEND_ALL_DATA_TO_ALL_CLIENTS_DELAY = 90 * 1000; // 1.5 minutes 
    long lastSentAllDataAt = 0; // when that happen

    String[] interest = { "trace" };
    private volatile boolean active = true;
    dbStore store;
    MFarm farm;
    String farmName;
    Vector clients;
    Vector buff; // value = eResult containing the trace
    Vector buff1; // the same.
    Vector ans; // new list of results sent to clients
    Vector old_ans; // old list of results sent to clients 

    public TracepathFilter(String farmName) {
        this.farmName = farmName;
        clients = new Vector();
        buff = new Vector();
        buff1 = new Vector();
        ans = new Vector();
        old_ans = new Vector();
        logger.log(Level.INFO, "TracepathFilter: filter started");
    }

    @Override
    public void confChanged() {
    }

    @Override
    public String getName() {
        return Name;
    }

    @Override
    public void initdb(dbStore datastore, MFarm farm) {
        this.store = datastore;
        this.farm = farm;
    }

    @Override
    public void initCache(Cache cache) {
    }

    @Override
    public void addClient(MonitorClient client) {
        if (traceCache.size() > 0) {
            try {
                Vector vRez = new Vector();
                for (Enumeration en = traceCache.elements(); en.hasMoreElements();) {
                    vRez.add(en.nextElement());
                }
                client.notifyResult(vRez, Name);
                vRez = null;
            } catch (Exception e) {
            }
        }
        clients.add(client);
    }

    // TODO: fix this hack. The proxy should notify filters that a new client
    // appeared so the filter can inform it about all network topology
    public void sendAllDataToAllClients() {
        if (traceCache.size() > 0) {
            Vector vRez = new Vector();
            for (Enumeration en = traceCache.elements(); en.hasMoreElements();) {
                vRez.add(en.nextElement());
            }
            informClients(vRez);
            //			for(int i=0; i<clients.size(); i++){
            //				MonitorClient client = (MonitorClient) clients.get(i);
            //				try {
            //					client.notifyResult(vRez, Name);
            //				} catch (Exception e) {
            //				}
            //			}
            vRez = null;
        }
    }

    @Override
    public void removeClient(MonitorClient client) {
        clients.remove(client);
    }

    @Override
    public void addNewResult(Object r) {
        if (r != null) {
            if (r instanceof Vector) {
                Vector v = (Vector) r;
                for (int i = 0; i < v.size(); i++) {
                    addNewResult(v.get(i));
                }
            } else if (r instanceof eResult) {
                eResult er = (eResult) r;
                if (er.Module.equals("monOldTracepath")) {
                    //					logger.log(Level.INFO, "TracepathFilter: received a result from monOldTracepath module!");
                    buff.add(er);
                }
            }
        }
    }

    @Override
    public boolean isAlive() {
        return active;
    }

    @Override
    public void finishIt() {
        active = false;
        Thread.currentThread().interrupt();

        clients.clear();
        clients = null;
        buff.clear();
        buff = null;
        buff1.clear();
        buff1 = null;
        ans.clear();
        ans = null;
        old_ans.clear();
        old_ans = null;

        ipCache.clear();
        ipCache = null;
        geoCache.clear();
        geoCache = null;
        traceCache.clear();
        traceCache = null;

        this.store = null;
    }

    void updateResults() {
        synchronized (buff) {
            if (buff.size() == 0) {
                return;
            }
            buff1.clear();
            buff1.addAll(buff);
            buff.clear();
        }
        for (int i = 0; i < buff1.size(); i++) {
            update((eResult) buff1.elementAt(i));
        }
    }

    void update(eResult r) {
        if (r.param_name.length == 0) {
            eResult rez = new eResult(r.FarmName, r.ClusterName, r.NodeName, Name, r.param_name);
            traceCache.remove(r.NodeName);
            logger.log(Level.INFO, "TracepathFilter: removing trace to " + r.NodeName);
            ans.add(rez);
        } else {
            Vector trace = (Vector) r.param[0];
            //			logger.log(Level.INFO,"TracepathFilter: parsing tracepath output to "+r.NodeName);
            Vector newTrace = parseTracepathOutput(trace);

            eResult oldRez = (eResult) traceCache.get(r.NodeName);
            if ((oldRez == null) || tracesDiffer(newTrace, (Vector) oldRez.param[0])) {
                //				logger.log(Level.INFO, "TracepathFilter: NEW or CHANGED -> filling data");
                sendIPidData(newTrace);
                fillWhoisData(newTrace);
                fillGeoData(newTrace);
                addNewIPsToCache(newTrace);
                // build the eResult
                eResult newRez = new eResult(r.FarmName, r.ClusterName, r.NodeName, Name, interest);
                newRez.param[0] = newTrace;
                newRez.time = NTPDate.currentTimeMillis();
                traceCache.put(r.NodeName, newRez);
                // send to clients only if new or has changed
                ans.add(newRez);
            } else {
                sendIPidData(new Vector());
                oldRez.time = NTPDate.currentTimeMillis();
            }
        }
    }

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

    // parse a vector trace from monOldTracepath and produce a
    // vector containing hostInfo hashtables for each router
    // in the path. These hostInfos are cached in ipCache to
    // avoid duplication for common paths to peer farms
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

    // for each hostInfo in trace, fill AS, NETNAME, DESCR data
    void fillWhoisData(Vector trace) {
        if (trace.size() == 0) {
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
                    line = line.replaceAll("[ ]+", " ");
                    String key = line.substring(0, line.indexOf(":")).toLowerCase();
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

    // Send trace IPs to IPID service
    void sendIPidData(Vector trace) {
        // build whois query
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

    // for each hostInfo in trace, fill LAT, LONG, CITY, STATE, COUNTRY
    void fillGeoData(Vector trace) {
        if (trace.size() == 0) {
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

    void addNewIPsToCache(Vector trace) {
        for (int i = 0; i < trace.size(); i++) {
            Hashtable hostInfo = (Hashtable) trace.get(i);
            if (!(ipCache.containsValue(hostInfo) || ((String) hostInfo.get("ip")).equals("?"))) {
                ipCache.put(hostInfo.get("ip"), hostInfo);
                //				logger.log(Level.INFO, "TracepathFilter: added "+hostInfo.get("host")+" = "+hostInfo.get("ip")+" to ipCache");
            }
        }
    }

    public Vector expressResults() {
        synchronized (ans) {
            old_ans.clear();
            old_ans.addAll(ans);
            ans.clear();
        }
        return old_ans;
    }

    void informClients(Vector v) {
        //		logger.log(Level.INFO, "TracepathFilter: informClients "+v.size()+" results");
        if ((v == null) || (v.size() == 0)) {
            return;
        }
        for (Enumeration e = clients.elements(); e.hasMoreElements();) {
            MonitorClient client = (MonitorClient) e.nextElement();
            try {
                client.notifyResult(v, Name);
            } catch (RemoteException e1) {
                logger.log(Level.WARNING, "Error informing client", e1);
            }
        }
    }

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
                logger.log(Level.INFO, "TracepathFilter: trace info to " + res.NodeName + " has expired. Removing it. "
                        + res.param_name.length);
                // queue this result to announce the clients that 
                // this link was deleted
                ans.add(res);
            }
        }
    }

    @Override
    public void run() {
        while (active) {
            try {
                try {
                    Thread.sleep(6000);
                } catch (Exception e) {
                }
                updateResults();
                try {
                    Thread.sleep(500);
                } catch (Exception e) {
                }

                check4deadTraces();

                Vector v = expressResults();
                informClients(v);

                long now = NTPDate.currentTimeMillis();
                if ((now - lastSentAllDataAt) > SEND_ALL_DATA_TO_ALL_CLIENTS_DELAY) {
                    sendAllDataToAllClients();
                    lastSentAllDataAt = now;
                }

                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "TracepathFilter: Connected client no:" + clients.size());
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "TracepathFilter: General Exception in main loop", t);
            }
        }
        logger.log(Level.INFO, "TracepathFilter: STOPS");
    }
}
