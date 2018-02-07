package lia.util.geo;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import lia.Monitor.JiniClient.CommonGUI.Gmap.iNetGeoClient;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ILink;
import lia.util.QuotedStringTokenizer;
import lia.util.Utils;

public class iNetGeoManager {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(iNetGeoManager.class.getName());

    private static final int URL_CONNECT_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(10);

    private static final int URL_READ_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(20);

    static ScheduledExecutorService executor;

    Hashtable<String, Object> iLinkCache; // a cache with known ILinks

    Vector<String[]> nodeData; // node data from the config file

    Vector<String[]> linkData; // link data from the config file

    Vector<String> executors; // executors which are still waiting for data

    Vector<String> bannedURLs;

    // 30 minutes initial
    public static final long UPDATE_TIME_MILLIS;

    public iNetGeoClient clientFrame = null;

    static {
        executor = Executors.newScheduledThreadPool(5, new ThreadFactory() {

            final AtomicLong l = new AtomicLong(0);

            @Override
            public Thread newThread(Runnable r) {
                final Thread t = new Thread(r, "(ML) iNetGeoClientWorker " + l.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });

        final long defaultDelayMillis = TimeUnit.MINUTES.toMillis(30);
        long cfgDelayMillis = defaultDelayMillis;
        try {
            cfgDelayMillis = TimeUnit.SECONDS.toMillis(AppConfig.getl(
                    "lia.util.geo.iNetGeoManager.update_delay_seconds",
                    TimeUnit.MILLISECONDS.toSeconds(defaultDelayMillis)));

            if (cfgDelayMillis <= 0) {
                cfgDelayMillis = defaultDelayMillis;
            }

        } catch (Throwable t) {
            cfgDelayMillis = defaultDelayMillis;
        }

        UPDATE_TIME_MILLIS = cfgDelayMillis;
    }

    public iNetGeoManager() {

        iLinkCache = new Hashtable<String, Object>();
        executors = new Vector<String>();
        nodeData = new Vector<String[]>();
        linkData = new Vector<String[]>();
        bannedURLs = new Vector<String>();
        executor.scheduleWithFixedDelay(new iNetGeoPrincipal(), 2000, UPDATE_TIME_MILLIS, TimeUnit.MILLISECONDS);
        logger.log(Level.INFO, "iNetGeoManager reloadTime: " + UPDATE_TIME_MILLIS + " ms");
        clientFrame = new iNetGeoClient(this);
    }

    public void refresh() {
        executor.execute(new iNetGeoRefresher());
    }

    public boolean addURL(String url) {

        if ((url == null) || (url.length() == 0)) {
            return false;
        }
        final String configFileAddr = AppConfig.getProperty("lia.util.geo.iNetGeoConfig", "");
        final String addresses[] = configFileAddr.split("(\\s)*,(\\s)*");
        if (addresses != null) {
            for (String addresse : addresses) {
                if (addresse.equals(url)) {
                    return false;
                }
            }
        }
        System.setProperty("lia.util.geo.iNetGeoConfig", configFileAddr + "," + url);
        executors.add(url);
        executor.execute(new iNetGeoWorker(url));
        bannedURLs.remove(url);
        return true;
    }

    public void removeURL(String url) {

        if ((url == null) || (url.length() == 0)) {
            return;
        }
        String configFileAddr = AppConfig.getProperty("lia.util.geo.iNetGeoConfig", "");
        String addresses[] = configFileAddr.split(",");
        ArrayList<String> adr = new ArrayList<String>();
        if (addresses != null) {
            for (String addresse : addresses) {
                if (addresse.equals(url)) {
                    continue;
                }
                adr.add(addresse);
            }
        }
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < adr.size(); i++) {
            if (i != 0) {
                buf.append(",");
            }
            buf.append(adr.get(i));
        }
        System.setProperty("lia.util.geo.iNetGeoConfig", buf.toString());
        bannedURLs.add(url);
    }

    public synchronized void updateConfig(String address, LinkedList<String> config) {

        if ((config == null) || (config.size() == 0) || bannedURLs.contains(address)) {
            return;
        }
        boolean linkMode = true;
        for (String line : config) {
            String name = null;
            QuotedStringTokenizer st = new QuotedStringTokenizer(line);
            if (st.hasMoreTokens()) {
                name = st.nextToken();
            } else {
                continue; // empty line
            }
            if (name.equals("[Cities]")) {
                linkMode = false;
                continue;
            }
            if (linkMode) { // we still got a line regarding a specific link
                if (!linkExists(name)) {
                    linkData.add(new String[] { address, line });
                    // System.out.println("added link "+name);
                }
            } else { // we got a line concerning a city
                try {
                    String city = name;
                    // skip 3 fields (Country, Longitude, Latitude)
                    // first (City) is already skipped
                    do {
                        name = st.nextToken();
                    } while (name.equals(""));
                    String country = name;
                    do {
                        name = st.nextToken();
                    } while (name.equals(""));
                    String longitude = name;
                    do {
                        name = st.nextToken();
                    } while (name.equals(""));
                    String latitude = name;
                    for (int k = 0;; k++) {
                        do {
                            name = st.nextToken();
                        } while (name.equals(""));
                        if (!nodeExists(name)) {
                            nodeData.add(new String[] { address,
                                    city + " " + country + " " + longitude + " " + latitude + " " + name });
                        }
                    }
                } catch (NoSuchElementException ex) {
                    // maybe next time...
                }
            }
        }
        String[][] ld = new String[linkData.size()][2];
        for (int i = 0; i < linkData.size(); i++) {
            String[] str = linkData.get(i);
            ld[i][0] = str[0];
            ld[i][1] = str[1];
        }
        clientFrame.linkTable.setData(ld);
        String[][] nd = new String[nodeData.size()][2];
        for (int i = 0; i < nodeData.size(); i++) {
            String[] str = nodeData.get(i);
            nd[i][0] = str[0];
            nd[i][1] = str[1];
        }
        clientFrame.nodeTable.setData(nd);
    }

    private boolean linkExists(String linkName) {

        boolean found = false;
        for (int i = 0; i < linkData.size(); i++) {
            String line = linkData.get(i)[1];
            QuotedStringTokenizer st = new QuotedStringTokenizer(line);
            String name = null;
            if (st.hasMoreTokens()) {
                name = st.nextToken();
            } else {
                continue; // empty line
            }
            if (name.equals(linkName)) {
                found = true;
                break;
            }
        }
        return found;
    }

    private boolean nodeExists(String ipAddress) {

        boolean found = false;
        for (int k = 0; k < nodeData.size(); k++) {
            String line = nodeData.get(k)[1];
            QuotedStringTokenizer st = new QuotedStringTokenizer(line);
            String name = null;
            if (st.hasMoreTokens()) {
                name = st.nextToken();
            } else {
                continue; // empty line
            }

            try {
                // skip 3 fields (Country, Longitude, Latitude)
                // first (City) is already skipped
                for (int i = 0; i < 3; i++) {
                    do {
                        name = st.nextToken();
                    } while (name.equals(""));
                }
                // search the source & destination IP among IP list
                for (int i = 0;; i++) {
                    do {
                        name = st.nextToken();
                    } while (name.equals(""));
                    if (ipAddress.startsWith(name)) {
                        found = true;
                        break;
                    }
                }
            } catch (NoSuchElementException ex) {
                // maybe next time...
            }
            if (found) {
                break;
            }
        }
        return found;
    }

    /**
     * This function is normally called to get info about a link.
     * 
     * @param linkName
     *            is the name of the link for which the info should be retreived.
     * @return ILink with all fields filled or null if nothing is found about this link.
     */
    public ILink getLink(String linkName) {
        Object objLink = iLinkCache.get(linkName);
        if ((objLink != null) && !(objLink instanceof ILink)) {
            return null;
        }
        ILink link;
        link = (ILink) objLink;
        if (link != null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, linkName + " found in cache");
            }
            return link;
        }
        // link not found in cache, link==null
        objLink = resolveLink(linkName);

        if (objLink != null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, linkName + " added to cache");
            }
            iLinkCache.put(linkName, objLink); // add the found object to cache, but if not instance of link, don't
                                               // return it
            if ((objLink != null) && !(objLink instanceof ILink)) {
                return null;
            }
            return (ILink) objLink;
        }
        return null;
    }

    /**
     * This function is normally called to get info about a link.
     * 
     * @param linkName
     *            is the name of the link for which the info should be retreived.
     * @return list of ILink-s with all fields filled or null if nothing is found about this link.
     */
    public ArrayList getLinks(String linkName) {
        Object objLink = iLinkCache.get(linkName);
        ArrayList alLinks = null;
        if (objLink != null) {
            if (!(objLink instanceof ArrayList)) {
                alLinks = new ArrayList();
                alLinks.add(objLink);
            } else {
                alLinks = (ArrayList) objLink;
            }
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, linkName + " found in cache");
            }
            return alLinks;
        }
        // link not found in cache, link==null
        objLink = resolveLink(linkName);

        if (objLink != null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, linkName + " added to cache");
            }
            iLinkCache.put(linkName, objLink); // add the found object to cache, but if not instance of link, don't
                                               // return it
            if (!(objLink instanceof ArrayList)) {
                alLinks = new ArrayList();
                alLinks.add(objLink);
            } else {
                alLinks = (ArrayList) objLink;
            }
            return alLinks;
        }
        // System.out.println("link e null");
        return null;
    }

    /**
     * Internal function to retreive info about a link from the config File <br>
     * 15/11/2006 added functionality to return list of links including intermediary ips; all links have the same name
     * 
     * @param linkName
     *            name of the link
     * @return an object that can be an ILink or an array of ILinks with all fields filled, or null if the link was not
     *         found.
     */
    private Object resolveLink(String linkName) {
        String line;
        ILink link = null;
        String name;
        QuotedStringTokenizer st;
        ArrayList alLinks = null;
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "resolveLink " + linkName);
        }
        synchronized (this) {
            for (int k = 0; k < linkData.size(); k++) {
                line = linkData.get(k)[1];
                st = new QuotedStringTokenizer(line);
                if (st.hasMoreTokens()) {
                    name = st.nextToken();
                } else {
                    continue; // empty line
                }
                if (name.equals(linkName)) {
                    link = new ILink(linkName);
                    // read source IP
                    do {
                        name = st.nextToken();
                    } while (name.equals(""));
                    link.fromIP = name;
                    // read destination IP
                    do {
                        name = st.nextToken();
                    } while (name.equals(""));
                    link.toIP = name;
                    // read speed
                    do {
                        name = st.nextToken();
                    } while (name.equals(""));
                    link.speed = Double.parseDouble(name);
                    // get info about the ends
                    link.from = getNodeGeo(link.fromIP);
                    link.to = getNodeGeo(link.toIP);
                    try {
                        link.fromLAT = Double.parseDouble(link.from.get("LAT"));
                        link.fromLONG = Double.parseDouble(link.from.get("LONG"));
                        link.toLAT = Double.parseDouble(link.to.get("LAT"));
                        link.toLONG = Double.parseDouble(link.to.get("LONG"));
                    } catch (Throwable t) {// can get NumberFormatException!
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "Exception parsing parameters ", t);
                        }
                    }
                    // read additional ip-s, if available
                    String prevIP = link.fromIP;
                    Map<String, String> prevFrom = link.from;
                    double prevLAT = link.fromLAT, prevLONG = link.fromLONG;
                    ILink interLink;
                    try {// it will exit with an exception
                        do {
                            // TODO: create a new ILink for each intermediary ip
                            // read destination IP
                            name = null;
                            do {
                                name = st.nextToken();
                            } while (name.equals("") && st.hasMoreTokens());
                            if (name != null) {
                                // all links have the same name
                                interLink = new ILink(linkName);
                                interLink.fromIP = prevIP;
                                interLink.toIP = name;
                                interLink.speed = link.speed;
                                // get info about the ends
                                // FIXME? use with caution the reference to the other link's hashtable; should do only
                                // one modification, not two
                                interLink.from = prevFrom;
                                interLink.to = getNodeGeo(interLink.toIP);
                                interLink.fromLAT = prevLAT;
                                interLink.fromLONG = prevLONG;
                                try {
                                    interLink.toLAT = Double.parseDouble(interLink.to.get("LAT"));
                                    interLink.toLONG = Double.parseDouble(interLink.to.get("LONG"));
                                } catch (Throwable t) {// can get NumberFormatException!
                                    interLink.toLAT = 0;
                                    interLink.toLONG = 0;
                                }
                                if (alLinks == null) {
                                    alLinks = new ArrayList();
                                }
                                alLinks.add(interLink);
                                prevIP = interLink.toIP;
                                prevFrom = interLink.to;
                                prevLAT = interLink.toLAT;
                                prevLONG = interLink.toLONG;
                            }
                        } while (st.hasMoreTokens());
                    } catch (NoSuchElementException ex) {
                        //WTF
                    } finally {
                        if (alLinks != null) {// at least one intermediary ip
                            // loose the first link as it isn't usefull any more
                            // alLinks.add(0, link);
                            // create the final link between the last intermediary ip and destination ip
                            interLink = new ILink(linkName);
                            interLink.fromIP = prevIP;
                            interLink.toIP = link.toIP;
                            interLink.speed = link.speed;
                            // get info about the ends
                            // FIXME? use with caution the reference to the other link's hashtable; should do only one
                            // modification, not two
                            interLink.from = prevFrom;
                            interLink.to = link.to;
                            interLink.fromLAT = prevLAT;
                            interLink.fromLONG = prevLONG;
                            interLink.toLAT = link.toLAT;
                            interLink.toLONG = link.toLONG;
                            alLinks.add(interLink);
                        }
                    }
                    // found link; don't read anymore
                    break;
                }
            }
        }
        if (link == null) {
            return null;
        }
        if (alLinks != null) {
            return alLinks;
        }
        return link;
    }

    /**
     * Get from the config file geo info about the node that has a given ip address
     * 
     * @param ipAddress
     *            the ip address of the node
     * @return a hashtable with the COUNTRY, CITY, LONG, LAT attributes or null if not found
     */
    public Map<String, String> getNodeGeo(String ipAddress) {
        String name, line;
        QuotedStringTokenizer st;
        synchronized (this) {
            for (int k = 0; k < nodeData.size(); k++) {
                line = nodeData.get(k)[1];
                st = new QuotedStringTokenizer(line);
                if (st.hasMoreTokens()) {
                    name = st.nextToken();
                } else {
                    continue; // empty line
                }
                try {
                    // skip 3 fields (Country, Longitude, Latitude)
                    // first (City) is already skipped
                    for (int i = 0; i < 3; i++) {
                        do {
                            name = st.nextToken();
                        } while (name.equals(""));
                    }
                    // search the source & destination IP among IP list
                    for (;;) {
                        do {
                            name = st.nextToken();
                        } while (name.equals(""));
                        if (ipAddress.startsWith(name)) {
                            return setLocationData(line);
                        }
                    }
                } catch (NoSuchElementException ex) {
                    //WTF
                }
            }
        }
        return null;
    }

    /**
     * Internal function that fills a hashtable with the City, Country, Long & Lat
     * values from the config. file
     * 
     * @param line
     *            is the line from the config file that contains required info
     * @return hashtable with CITY, COUNTRY, LONG, LAT attributes
     */
    private Map<String, String> setLocationData(String line) {
        Hashtable<String, String> rez = new Hashtable<String, String>();
        QuotedStringTokenizer st = new QuotedStringTokenizer(line);
        String name = st.nextToken();
        rez.put("CITY", name);
        do {
            name = st.nextToken();
        } while (name.equals(""));
        rez.put("COUNTRY", name);
        do {
            name = st.nextToken();
        } while (name.equals(""));
        rez.put("LONG", name);
        do {
            name = st.nextToken();
        } while (name.equals(""));
        rez.put("LAT", name);
        return rez;
    }

    public synchronized void updateData() {

        iLinkCache.clear();
        nodeData.clear();
        linkData.clear();
        String configFileAddr = AppConfig.getProperty("lia.util.geo.iNetGeoConfig", "");
        String addresses[] = configFileAddr.split("(\\s)*,(\\s)*");
        if (addresses != null) {
            for (int i = 0; i < addresses.length; i++) {
                if (!executors.contains(addresses[i])) {
                    executors.add(addresses[i]);
                    executor.execute(new iNetGeoWorker(addresses[i]));
                }
            }
        }
    }

    class iNetGeoPrincipal implements Runnable {

        @Override
        public void run() {
            updateData();
        }
    }

    class iNetGeoRefresher implements Runnable {

        @Override
        public void run() {
            updateData();
        }
    }

    class iNetGeoWorker implements Runnable {

        String configFileAddr;

        public iNetGeoWorker(String configFileAddr) {

            this.configFileAddr = configFileAddr;
        }

        @Override
        public void run() {
            if ((configFileAddr == null) || configFileAddr.equals("")) {
                logger.log(Level.WARNING, "configFileAddr invalid");
                return;
            }
            long nStartAction = System.nanoTime();
            InputStream is = null;
            BufferedReader br = null;

            try {
                final URL url = new URL(configFileAddr + "?cTime=" + System.currentTimeMillis() + "&nTime="
                        + System.nanoTime());
                final URLConnection urlConnection = url.openConnection();
                urlConnection.setConnectTimeout(URL_CONNECT_TIMEOUT);
                urlConnection.setDefaultUseCaches(false);
                urlConnection.setUseCaches(false);
                urlConnection.setReadTimeout(URL_READ_TIMEOUT);
                urlConnection.connect();
                is = urlConnection.getInputStream();
                br = new BufferedReader(new InputStreamReader(is));
                final LinkedList<String> configData = new LinkedList<String>();
                configData.clear();
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    // skip comments and empty lines
                    if (line.startsWith("#") || line.equals("")) {
                        continue;
                    }
                    configData.add(line);
                }
                Utils.closeIgnoringException(is);
                Utils.closeIgnoringException(br);
                updateConfig(configFileAddr, configData);
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "config loaded successfully");
                }
            } catch (IOException e) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "config loading FAILED!!", e);
                }
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "General Exception! config loading FAILED!!", t);
                }
            } finally {
                Utils.closeIgnoringException(is);
                Utils.closeIgnoringException(br);
                if (logger.isLoggable(Level.FINE)) {
                    long nEndAction = System.nanoTime();
                    logger.log(Level.FINE, "Loaded iNetGeoManager config file " + configFileAddr + " in "
                            + TimeUnit.NANOSECONDS.toMillis(nEndAction - nStartAction) + " miliseconds");
                }
            }
            executors.remove(configFileAddr);
        }
    }

    public static void main(String args[]) throws SecurityException, IOException {
        LogManager.getLogManager().readConfiguration(
                new ByteArrayInputStream(("handlers= java.util.logging.ConsoleHandler\n"
                        + "java.util.logging.ConsoleHandler.level = FINEST\n"
                        + "java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter\n" + "")
                        .getBytes()));

        //System.setProperty("lia.util.geo.iNetGeoConfig", "http://monalisa.cacr.caltech.edu/iNetGeoConfig");
        System.setProperty("lia.util.geo.iNetGeoConfig", "http://localhost/~ramiro/iNetGeoConfig");
        iNetGeoManager manager = new iNetGeoManager();
        logger.setLevel(Level.ALL);
        manager.clientFrame.setVisible(true);
    }

} // end of class iNetGeoManager

