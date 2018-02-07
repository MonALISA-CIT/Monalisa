package lia.util.geo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Hashtable;
import java.util.NoSuchElementException;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ILink;
import lia.util.QuotedStringTokenizer;

/**
 * This class is used to get info about a link. For a link name, the following info
 * is filled: source&dest ip, city, country, longitude, and latitude.  
 */
public class iNetGeoClient {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(iNetGeoClient.class.getName());

    Hashtable iLinkCache; // a cache with known ILinks
    String configFileAddr; // config file address
    Vector configData; // data from the config file
    //	boolean debug; 

    public iNetGeoClient() {
        //		debug =	Boolean.valueOf(AppConfig.getProperty("lia.Monitor.debug", "false")).booleanValue();
        configFileAddr = AppConfig.getProperty("lia.util.geo.iNetGeoConfig", "");
        iLinkCache = new Hashtable();
        configData = new Vector();
        loadConfig();
    }

    /**
     * This function reads the config file and resets the links cache.
     * Can be called to reread the config.
     */
    public void loadConfig() {
        if ((configFileAddr == null) || configFileAddr.equals("")) {
            logger.log(Level.WARNING, "configFileAddr invalid");
            return;
        }
        try {
            InputStream is = new URL(configFileAddr).openStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(is));
            synchronized (configData) {
                configData.clear();
                iLinkCache.clear();
                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    //skip comments and empty lines
                    if (line.startsWith("#") || line.equals("")) {
                        continue;
                    }
                    configData.add(line);
                }
            }
            in.close();
            is.close();
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
        }
    }

    /**
     * This function is normally called to get info about a link. 
     * @param linkName is the name of the link for which the info should be retreived.
     * @return ILink with all fields filled or null if nothing is found about this link.
     */
    public ILink getLink(String linkName) {
        ILink link;
        link = (ILink) iLinkCache.get(linkName);
        if (link != null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, linkName + " found in cache");
            }
            return link;
        }
        link = resolveLink(linkName);
        if (link != null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, linkName + " added to cache");
            }
            iLinkCache.put(linkName, link);
            return link;
        }
        return null;
    }

    /**
     * Internal function to retreive info about a link from the config File 
     * @param linkName name of the link
     * @return ILink with all fields filled or null if the link was not found.
     */
    private ILink resolveLink(String linkName) {
        String line;
        ILink link = null;
        String name;
        QuotedStringTokenizer st;
        synchronized (configData) {
            for (int k = 0; k < configData.size(); k++) {
                line = (String) configData.get(k);
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
                    // found link; don't read anymore
                    break;
                }
            }
        }
        if (link == null) {
            return null;
        }
        try {
            link.fromLAT = Double.parseDouble(link.from.get("LAT"));
            link.fromLONG = Double.parseDouble(link.from.get("LONG"));
            link.toLAT = Double.parseDouble(link.to.get("LAT"));
            link.toLONG = Double.parseDouble(link.to.get("LONG"));
        } catch (Throwable t) {//can get NumberFormatException!
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Exception parsing parameters ", t);
            }
        }
        return link;
    }

    /**
     * Internal function that fills a hashtable with the City, Country, Long & Lat
     * values from the config. file
     * @param line is the line from the config file that contains required info
     * @return hashtable with CITY, COUNTRY, LONG, LAT attributes
     */
    private Hashtable setLocationData(String line) {
        Hashtable rez = new Hashtable();
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

    /**
     * Get from the config file geo info about the node that has a given ip address
     * @param ipAddress the ip address of the node
     * @return a hashtable with the COUNTRY, CITY, LONG, LAT attributes or null if not found
     */
    public Hashtable getNodeGeo(String ipAddress) {
        String name, line;
        QuotedStringTokenizer st;
        boolean searchingCity = true;
        synchronized (configData) {
            for (int k = 0; k < configData.size(); k++) {
                line = (String) configData.get(k);
                st = new QuotedStringTokenizer(line);
                if (st.hasMoreTokens()) {
                    name = st.nextToken();
                } else {
                    continue; // empty line
                }
                if (searchingCity) {
                    if (name.equals("[Cities]")) {
                        searchingCity = false;
                    }
                } else {
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
                                return setLocationData(line);
                            }
                        }
                    } catch (NoSuchElementException ex) {
                        ; // maybe next time...					
                    }
                }
            }
        }
        return null;
    }
}
