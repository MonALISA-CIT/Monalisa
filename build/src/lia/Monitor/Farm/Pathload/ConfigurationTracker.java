/**
 * 
 */
package lia.Monitor.Farm.Pathload;

import java.util.Collections;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;

/**
 * The configuration tracker is a recuring task that
 * checks for changes of the URL of the PathloadConnector 
 * 
 * @author heri
 *
 */
public class ConfigurationTracker {

    /**
     * lia.monitor.Modules.monPathload.configTimer - Delay for the TimerThread for monPathload
     * 		to run the pathload URL Checker. Default is 30 sec.
     */
    public static final long PATHLOAD_CONFIG_TIMER_RUN = Long.parseLong(AppConfig.getProperty(
            "lia.monitor.Modules.monPathload.configTimer", "60")) * 1000;

    public static final String PATHLOAD_PEER_GROUP = AppConfig.getProperty("lia.monitor.Modules.monPathload.peerGroup");

    /**
     * My Logger Component
     */
    private static final Logger logger = Logger.getLogger(ConfigurationTracker.class.getName());

    private final PeerCache peerCache;
    private String pathloadConfigUrl;
    private Timer timer;

    /**
     * The Default Constructor 
     *
     */
    public ConfigurationTracker(PeerCache peerCache) {
        this.peerCache = peerCache;
    }

    /**
     * Startup the configuration Tracker. This will trigger a reccuring
     * TimerTask that checks the URL of the PathloadConnector. If a
     * change is found then, trigger an Event Change for monPathload.
     * 
     * @return	True if startup succeded, false otherwise
     */
    public boolean start() {
        if (peerCache == null) {
            logger.log(Level.SEVERE, "[ConfigTracker] My PeerCache connection is null.");
            return false;
        }
        if (PATHLOAD_PEER_GROUP == null) {
            logger.log(Level.SEVERE, "[ConfigTracker] No peer group specified. I can't continue. "
                    + "Either specify a pathloadUrl, or set "
                    + "lia.monitor.Modules.monPathload.peerGroup to you peerGroup community name.");
            return false;
        }

        timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Vector vec = PathloadUrlDiscoveryJini.getUrls(ConfigurationTracker.PATHLOAD_PEER_GROUP);

                if ((vec != null) && (!vec.isEmpty())) {
                    if (vec.size() > 1) {
                        try {
                            Collections.sort(vec, new Comparator() {
                                @Override
                                public int compare(Object arg0, Object arg1) {
                                    int result;
                                    PathloadUrlDiscoveryJini p1 = (PathloadUrlDiscoveryJini) arg0;
                                    PathloadUrlDiscoveryJini p2 = (PathloadUrlDiscoveryJini) arg1;

                                    String s1 = p1.getMyServiceID() + p1.getMySerial();
                                    String s2 = p2.getMyServiceID() + p2.getMySerial();
                                    result = s1.compareTo(s2);
                                    return result;
                                }
                            });
                            PathloadUrlDiscoveryJini pud = (PathloadUrlDiscoveryJini) vec.get(0);
                            setUrl(pud.getMyUrl());
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "[ConfigurationTracker] Found more than one PathloadUrl for "
                                    + ConfigurationTracker.PATHLOAD_PEER_GROUP + ". Could not get a comparator.");
                        }
                    } else {
                        PathloadUrlDiscoveryJini pud = (PathloadUrlDiscoveryJini) vec.get(0);
                        setUrl(pud.getMyUrl());
                    }
                } else {
                    logger.log(Level.FINEST, "[ConfigurationTracker] PathloadUrl is not yet available");
                }
            }
        };

        timer.schedule(task, 0, ConfigurationTracker.PATHLOAD_CONFIG_TIMER_RUN);
        logger.log(Level.INFO, "[ConfigTracker] Started up.");

        return true;
    }

    /**
     * Shut the ConfigurationTracker down, cleanup anything that is left behind.
     *  
     * @return	True if succeded, false otherwise.
     */
    public boolean shutdown() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        logger.log(Level.INFO, "[ConfigTracker] Shutting down.");
        return true;
    }

    /**
     * Set the new Url and announce the PeerCache
     * 
     * @param value	The new URL string, no checks are being done.
     */
    public void setUrl(String value) {
        if (value == null) {
            return;
        }
        if ((pathloadConfigUrl != null) && (pathloadConfigUrl.equals(value))) {
            logger.log(Level.FINEST, "The URLs are the same. " + value);
            return;
        }

        String oldValue = pathloadConfigUrl;
        pathloadConfigUrl = value;

        peerCache.changeUrl(oldValue, pathloadConfigUrl);
    }
}
