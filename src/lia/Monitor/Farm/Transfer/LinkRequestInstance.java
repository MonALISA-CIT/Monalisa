package lia.Monitor.Farm.Transfer;

import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.util.ntp.NTPDate;

/**
 * Identifies a request (transfer/reservation/etc) at the link level.
 * @author catac
 */
class LinkRequestInstance {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(LinkRequestInstance.class.getName());

    /** Timeout after which a request expires and is removed, if not keptAlive, in seconds. Default: 2 hours */
    public static long REQUEST_TIMEOUT = AppConfig.getl("lia.app.transfer.LinkRequestInstance.timeout", 2 * 3600) * 1000;

    private final String requestID; // request ID
    private long startTime; // moment when this request has started
    private long lastUpdateTime;// moment when this request was confirmed as being alive
    private boolean active; // from start() 'till stop(). If not active, will be removed after the next checkStatus()

    /** 
     * Initialize a request instance.
     * @param requestID The request ID.
     */
    public LinkRequestInstance(String requestID) {
        this.requestID = requestID;
        active = false;
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Adding new link request instance: " + requestID);
        }
    }

    /** Start the request. It will become active. */
    public synchronized boolean start() {
        active = true;
        lastUpdateTime = startTime = NTPDate.currentTimeMillis();
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Starting link request instance: " + requestID);
        }
        return true;
    }

    /** Stop the request. It will become inactive. */
    public synchronized boolean stop() {
        active = false;
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Stopping link request instance: " + requestID);
        }
        return true;
    }

    /** Keep the request alive. This should be called from time to time so that it doesn't expire. */
    public synchronized void keepAlive() {
        lastUpdateTime = NTPDate.currentTimeMillis();
    }

    /** 
     * Check request's status; If active, append the requestID to the list.
     * @param activeList list of active transfers
     * @return whether the request is still active; if not, it will be removed from the link's htInstances.
      */
    public synchronized boolean checkStatus(StringBuilder activeList) {
        long now = NTPDate.currentTimeMillis();
        if ((lastUpdateTime + REQUEST_TIMEOUT) < now) {
            logger.warning("Request " + requestID + " not updated for more than " + (REQUEST_TIMEOUT / 1000)
                    + " sec. Stopping it.");
            stop(); // if it's active for more than the allowed period
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("Status for request " + requestID + " is active=" + active + " running for: "
                    + ((now - startTime) / 1000) + " sec; not updated for: " + ((now - lastUpdateTime) / 1000)
                    + " sec;" + " time to live: " + (((lastUpdateTime + REQUEST_TIMEOUT) - now) / 1000) + " sec.");
        }
        if (activeList.length() > 0) {
            activeList.append(",");
        }
        activeList.append(requestID);
        return active;
    }
}
