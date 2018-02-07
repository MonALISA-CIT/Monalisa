package lia.Monitor.JiniClient.CommonGUI.Topology;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniSerFarmMon.MLLUSHelper;
import lia.Monitor.monitor.GenericMLEntry;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.tcpClient.MLSerClient;
import lia.util.Utils;
import net.jini.core.lookup.ServiceItem;

/**
 * Contains functions from the monOldTracepath module that previously was sending all
 * data to the client. Now this data is gathered in the client, using the same
 * mechanisms.
 * For now, this class is used to convert the new Tracepath results to old eResults
 * that are understood by NetTopologyAnalyzer. This will hold a reference to it, so that
 * it can post received results as soon as they are converted (i.e. information about
 * AS, location, descriptio etc. is retrieved).
 */
public class TraceFilter extends Thread {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(TraceFilter.class.getName());

    static String whoisQuery = null;

    static String geoQuery = null;

    private long lastURLsRefresh;

    static long URL_REFRESH_INTERVAL = 30 * 1000;

    static int MAX_QUERY_QUEUE_LEN = 2000;

    //HashMap should be ok; it's modified from single thread I hope
    Map<String, Map<String, String>> ipCache = new HashMap<String, Map<String, String>>(); // keep a cache
                                                                                           // with info abut
                                                                                           // each IP

    //HashMap should be ok; it's modified from single thread I hope
    Map<String, Map<String, String>> geoCache = new HashMap<String, Map<String, String>>(); // keep a cache with info about each AS

    final NetTopologyAnalyzer topoAnalyzer;

    final Queue<Object[]> resultsQueue = new LinkedBlockingQueue<Object[]>();

    volatile boolean hasToRun = true;

    public TraceFilter(NetTopologyAnalyzer ntpa) {
        super("(ML) - TraceFilter");
        topoAnalyzer = ntpa;
    }

    /** get whois and geo services addresses */
    void refreshURLs() {
        if ((whoisQuery == null) || (geoQuery == null)) {
            logger.log(Level.INFO, "TraceFilter: Trying to get topology services...");
        } else {
            long now = System.currentTimeMillis();
            if ((lastURLsRefresh + URL_REFRESH_INTERVAL) < now) {
                return;
            }
            lastURLsRefresh = now;
        }
        ServiceItem[] si = MLLUSHelper.getInstance().getTopologyServices();
        if ((si == null) || (si.length == 0) || (si[0].attributeSets.length == 0)) {
            logger.log(Level.SEVERE, "TraceFilter: No Geo & IPID service was found (yet)");
            whoisQuery = geoQuery = null;
        } else {
            GenericMLEntry gmle = (GenericMLEntry) si[0].attributeSets[0];
            if (gmle.hash != null) {
                String baseUrl = (String) gmle.hash.get("URL");
                boolean alreadyFound = whoisQuery != null;
                whoisQuery = baseUrl + "/FindIP";
                geoQuery = baseUrl + "/FindAS";
                logger.log((alreadyFound ? Level.FINE : Level.INFO), "TraceFilter: Whois and Geo services base URL: "
                        + baseUrl);
            }
        }
    }

    /**
     * for each hostInfo in trace, fill AS, NETNAME, DESCR data;
     * returns true if successfull
     */
    private boolean fillWhoisData(Vector<Map<String, String>> trace) {
        if (trace.size() == 0) {
            return true;
        }
        if ((whoisQuery == null) || !whoisQuery.startsWith("http")) {
            return false;
        }
        // build whois query
        StringBuilder query = new StringBuilder(whoisQuery);
        int crt = 0;
        for (final Map<String, String> hostInfo : trace) {
            // get next ip in trace
            String ip = hostInfo.get("ip");
            if ((ipCache.get(ip) != null) || ip.equals("?")) {
                continue; // if it's already in ipCache or if it's a no reply node, skip it
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
        if (crt > 0) {
            BufferedReader br = null;
            InputStream is = null;
            InputStreamReader isr = null;

            String line = null;

            try {
                // try solving it
                logger.log(Level.FINE, "WHOIS: " + allQuery);
                URLConnection uconn = new URL(allQuery).openConnection();
                uconn.setDefaultUseCaches(false);
                uconn.setUseCaches(false);
                is = uconn.getInputStream();
                isr = new InputStreamReader(is);
                br = new BufferedReader(isr);

                // info about hosts is separated by an empty line
                // int crtHost = 0;
                for (Map<String, String> traceIP : trace) {
                    // get next ip in trace
                    String ip = traceIP.get("ip");
                    if ((ipCache.get(ip) != null) || ip.equals("?")) {
                        continue; // if it's already in ipCache or "no reply", skip this
                    }
                    HashMap<String, String> hostInfo = new HashMap<String, String>();
                    // flag to ignore all other data about this IP, if it is on a private network.
                    // the FindIP will add a "publicIP" line and will return data about the machine
                    // from where the query is performed. This data must therefore be ignored.
                    boolean privateIP = false;
                    // the next data received from the query is for this record
                    while (((line = br.readLine()) != null) && !line.equals("")) {
                        // read data 'till end of file or empty line received
                        line = line.replaceAll("[ ]+", " ").trim();
                        int idp = line.indexOf(":");
                        if (idp < 0) {
                            continue;
                        }
                        String key = line.substring(0, idp).toLowerCase();
                        if (key.equals("publicip")) {
                            // System.out.println("IP "+ip+" is private!");
                            privateIP = true;
                        }
                        // ignore all data refering to private IPs
                        if (privateIP) {
                            continue;
                        }
                        if (key.equals("origin")) {
                            int idx = line.indexOf("AS");
                            if (idx == -1) {
                                idx = line.indexOf("as");
                            }
                            if (idx != -1) {
                                hostInfo.put("as", line.substring(idx + 2));
                            }
                        } else if (key.equals("netname")) {
                            String oldNet = hostInfo.get("net");
                            String newNet = line.substring(line.indexOf(":") + 2);
                            if ((oldNet == null) || (oldNet.length() > newNet.length())) {
                                hostInfo.put("net", newNet);
                            }
                        } else if (key.equals("descr")) {
                            String oldDescr = hostInfo.get("descr");
                            final int idxC = line.indexOf(":") + 2;
                            if (idxC > (line.length() - 1)) {
                                continue;
                            }
                            String newDescr = line.substring(idxC);
                            if (oldDescr == null) {
                                hostInfo.put("descr", newDescr);
                            } else {
                                hostInfo.put("descr", oldDescr + "\n" + newDescr);
                            }
                        } else if (key.equals("hostname")) {
                            hostInfo.put("host", line.substring(idp + 1).trim());
                        }
                    }
                    ipCache.put(ip, hostInfo);
                }
                br.close();
            } catch (Throwable t) {
                //if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.WARNING, "Error filling whois data (" + allQuery + ") line: '" + line + "'", t);
                //}
                return false;
            } finally {
                Utils.closeIgnoringException(is);
                Utils.closeIgnoringException(isr);
                Utils.closeIgnoringException(br);
            }
        }
        // fill the trace with information
        for (Map<String, String> hostInfo : trace) {
            String ip = hostInfo.get("ip");
            if (ip.equals("?")) {
                continue; // if it's a no reply node, skip it
            }
            Map<String, String> ipInfo = ipCache.get(ip);
            if (ipInfo == null) {
                logger.log(Level.WARNING, "IP " + ip + " should have been already in cache!");
                continue;
            }
            // put all known information about this ip
            hostInfo.putAll(ipInfo);
        }
        return true;
    }

    /**
     * for each hostInfo in trace, fill LAT, LONG, CITY, STATE, COUNTRY
     * returns true if sucessfull
     */
    private boolean fillGeoData(Vector<Map<String, String>> trace) {
        if (trace.size() == 0) {
            return true;
        }
        if ((geoQuery == null) || !geoQuery.startsWith("http")) {
            return false;
        }
        int[] vas = new int[trace.size()];
        int nvas = 0;
        StringBuilder query = new StringBuilder(geoQuery);
        for (Map<String, String> hostInfo : trace) {

            String as = hostInfo.get("as");
            if ((as == null) || as.equals("") || (geoCache.get(as) != null)) {
                continue; // if this as is invalid or already in geoCache then skip it
            }

            int a;
            try {
                a = Integer.parseInt(as);
            } catch (NumberFormatException ex) {
                logger.log(Level.WARNING, "Invalid AS number: " + as, ex);
                hostInfo.remove("as");
                continue;
            }
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
            BufferedReader br = null;
            try {
                logger.log(Level.FINE, "GEO: " + allQuery);
                URLConnection uconn = new URL(allQuery).openConnection();
                uconn.setDefaultUseCaches(false);
                uconn.setUseCaches(false);
                br = new BufferedReader(new InputStreamReader(uconn.getInputStream()));
                String line = null;
                while ((line = br.readLine()) != null) {
                    if (line.equals("")) {
                        continue;
                    }
                    int p = line.indexOf("\t");
                    String key = line.substring(0, p);
                    geoCache.put(key, parseGeoLine(line));
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error getting Geo data (" + allQuery + ")", ex);
                return false;
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException ex) {
                        logger.warning(ex.toString());
                    }
                }
            }
        }
        for (Map<String, String> hostInfo : trace) {

            String as = hostInfo.get("as");
            if ((as == null) || as.equals("")) {
                continue; // if this as is invalid or not in cache then skip it
            }
            Map<String, String> geoData = geoCache.get(as);
            if (geoData != null) {
                hostInfo.putAll(geoData);
            }
        }
        return true;
    }

    /**
     * return a hash with the fields from the given line
     */
    private Map<String, String> parseGeoLine(String line) {
        Map<String, String> asInfo = new HashMap<String, String>();
        try {
            StringTokenizer stk = new StringTokenizer(line, "\t");
            asInfo.put("as", stk.nextToken());
            asInfo.put("lat", stk.nextToken());
            asInfo.put("long", stk.nextToken());
            asInfo.put("net", stk.nextToken());
            String city = stk.nextToken();
            if (!city.equals("?") && !city.equals("")) {
                asInfo.put("city", city);
            }
            String state = stk.nextToken();
            if (!state.equals("?") && !state.equals("")) {
                asInfo.put("state", state);
            }
            String country = stk.nextToken();
            if (!country.equals("?") && !country.equals("")) {
                asInfo.put("country", country);
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error parsing Geo line: " + line, ex);
        }
        return asInfo;
    }

    /** convert a new Trace result to an old eResult */
    private eResult result2eResult(Result r) {
        eResult er = new eResult(r.FarmName, r.ClusterName, r.NodeName, r.Module, new String[] { "route" });
        Vector trace = new Vector();
        er.param[0] = trace;
        er.time = r.time;
        double lastDelay = 0, deltaDelay = 0;
        if ((r.param != null) && (r.param_name != null)) {
            for (int i = 0; i < r.param_name.length; i++) {
                String hop = r.param_name[i];
                if ((hop == null) || hop.equals("status") || (hop.indexOf(":") <= 0)) {
                    continue;
                }
                String ip = hop.substring(1 + hop.indexOf(":"));
                if (ip.equals("no_reply")) {
                    ip = "?";
                }
                deltaDelay = r.param[i] - lastDelay;
                if (deltaDelay < 0) {
                    deltaDelay = 0;
                }
                lastDelay = r.param[i];
                Map<String, String> hostInfo = new HashMap<String, String>();
                hostInfo.put("ip", ip);
                // Hashtable hostDetails = (Hashtable) ipCache.get(ip);
                // if(hostDetails != null){
                // for(Enumeration den = hostDetails.keys(); den.hasMoreElements(); ){
                // String key = (String) den.nextElement();
                // hostInfo.put(key, hostDetails.get(key));
                // }
                // }
                // "host" will be filled by the whois service
                hostInfo.put("delay", "" + deltaDelay);
                trace.add(hostInfo);
            }
        }
        return er;
    }

    /**
     * fill the trace with whois and geo data;
     * returns true if sucessfull
     */
    private boolean resolveTrace(eResult r) {
        if (r.param.length == 0) {
            return true;
        }
        Vector trace = (Vector) r.param[0];
        if (fillWhoisData(trace)) {
            if (fillGeoData(trace)) {
                return true;
            }
        }
        return false;
    }

    /**
     * adds a new Tracepath result. It will be resolved and then, the
     * corresponding eResult will be sent to NetTopologyAnalyzer.
     */
    public void addNewTraceResult(MLSerClient client, Result rez) {
        addOldTraceResult(client, result2eResult(rez));
    }

    /**
     * add an old Tracepath result. It will be resolved and then it will be
     * sent to NetTopologyAnalyzer
     */
    public void addOldTraceResult(MLSerClient client, eResult erez) {
        if ((erez.param == null) || (erez.param.length == 0) || (erez.param[0] == null)
                || (((Vector) erez.param[0]).size() == 0)) {
            topoAnalyzer.processResult(client, erez);
            return;
        } else if (((Vector) erez.param[0]).size() == 1) {
            return;
        }
        if (resultsQueue.size() > MAX_QUERY_QUEUE_LEN) {
            logger.log(Level.WARNING, "TraceFilter's queue too long. Dropping trace!");
            resultsQueue.poll();
        }
        Object map[] = new Object[] { client, erez };
        resultsQueue.add(map);
        synchronized (this) {
            this.notify();
        }
    }

    /**
     * resolve received results
     */
    @Override
    public void run() {
        while (hasToRun) {
            try {
                refreshURLs();
                synchronized (this) {
                    while (resultsQueue.size() == 0) {
                        this.wait();
                    }
                }
                Object map[] = null;
                synchronized (this) {
                    map = resultsQueue.poll();
                }
                if (map != null) {
                    eResult erez = (eResult) map[1];
                    if (resolveTrace(erez)) {
                        topoAnalyzer.processResult((MLSerClient) map[0], erez);
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error in TraceFilter", t);
            }
        }
    }
}
