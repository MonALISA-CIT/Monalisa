/**
 * 
 */
package lia.Monitor.Farm.Pathload;

import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.Pathload.client.PathloadStub;
import lia.util.Pathload.client.ServletResponse;
import lia.util.Pathload.util.PathloadException;
import lia.util.logging.MLLogEvent;

/**
 * Get configuration data.
 * 
 * @author heri
 *
 */
public class ConfigGet implements Runnable {

    public final static int STATUS_DO_NOTHING = 0;
    public final static int STATUS_GET_TOKEN = 1;
    public final static int STATUS_REFRESH_STATE = 2;
    public final static int STATUS_RETURN_TOKEN = 3;

    private static final Logger logger = Logger.getLogger(ConfigGet.class.getName());

    private int status;
    private ServletResponse servletResponse;
    private final PathloadStub stub;
    private final PeerCache peerCache;
    private String url;

    private boolean reset;
    private final Object lock;

    /**
     * Default Constructor
     * 
     * @param peerCache
     * @param hostname
     * @param ipAddress
     * @param farmName
     * @param farmGroups
     */
    public ConfigGet(PeerCache peerCache, String hostname, String ipAddress, String farmName, String[] farmGroups) {
        logger.log(Level.FINEST, "[monPathload] ConfigGet is running Constructor");
        this.status = ConfigGet.STATUS_GET_TOKEN;
        this.servletResponse = null;
        this.stub = new PathloadStub(hostname, ipAddress, farmName, farmGroups);
        this.peerCache = peerCache;
        this.reset = false;
        this.url = null;
        this.lock = new Object();
    }

    /**
     * This configuration manager works just like a state machine
     * The transition from refresh state to return state is done
     * by external measures or by timeout.
     *  
     * @see java.util.TimerTask#run()
     */
    @Override
    public void run() {
        logger.log(Level.FINEST, "[monPathload] ConfigGet Running config thread.");

        synchronized (lock) {
            if (url == null) {
                logger.log(Level.INFO, "PathloadConnector URL not ready. Waiting for it to become available.");
                return;
            }

            if (reset == true) {
                status = ConfigGet.STATUS_GET_TOKEN;
                servletResponse = null;
                boolean bResult = stub.setUrl(url);
                if (!bResult) {
                    url = null;
                }
                reset = false;
            }
        }

        switch (status) {
        case ConfigGet.STATUS_DO_NOTHING:
            break;
        case ConfigGet.STATUS_GET_TOKEN:
            requestToken();
            break;
        case ConfigGet.STATUS_REFRESH_STATE:
            refreshState();
            break;
        case ConfigGet.STATUS_RETURN_TOKEN:
            releaseToken();
            break;
        }
    }

    /**
     * Try to get the token, if succesfull, transition to the
     * refresh state 
     *
     */
    private void requestToken() {
        try {
            logger.log(Level.FINEST, "[monPathload] ConfigGet Requesting Token.");
            servletResponse = stub.getToken();

            if (servletResponse != null) {
                peerCache.setServletResponse(servletResponse);
                if (servletResponse.hasToken()) {
                    status = ConfigGet.STATUS_REFRESH_STATE;
                    logger.log(Level.FINE, "[monPathload] ConfigGet Got token: " + servletResponse.toString());
                    peerCache.gotToken();
                }
                if (servletResponse.hasMyGroup() && (logger.isLoggable(Level.FINEST))) {
                    Vector myGroup = servletResponse.getMyGroup();
                    StringBuilder sb = new StringBuilder();
                    sb.append("[monPathload] ConfigGet My group has ");
                    sb.append(myGroup.size());
                    sb.append(" elements. ");
                    for (Iterator it = myGroup.iterator(); it.hasNext();) {
                        sb.append((String) it.next());
                        sb.append(" ");
                    }
                    logger.log(Level.FINEST, sb.toString());
                }
            } else {
                logger.log(Level.FINEST, "[monPathload] ConfigGet Token request denied.");
            }
        } catch (PathloadException e) {
            MLLogEvent logEv = new MLLogEvent();
            logEv.put("ModuleName", "monPathload");
            logEv.put("ErrorName", "Pathload Servlet Connection");
            logEv.put("ConfigGetState", "Request Token");
            logEv.put("Exception", e.toString());
            logEv.put("ConnectionURL", url);

            logger.log(Level.SEVERE, "[monPathload] ConfigGet - runState " + e.getMessage(), new Object[] { logEv });
        }
    }

    /**
     * Send keep-alives until a timeout period has passed or
     * until measurement is declared complete
     * The servlet is allowed to reset my action. If the current token isn't
     * mine, I will reset back to an initial state.
     *
     */
    private void refreshState() {
        logger.log(Level.FINEST, "************ CHECK RELEASE TOKEN **************");
        if (!peerCache.mustReleaseToken()) {
            logger.log(Level.FINEST, "************ CHECK RELEASE TOKEN END *************");
            logger.log(Level.FINEST, "[monPathload] ConfigGet Refreshing state of peer.");

            if ((servletResponse == null) || !(servletResponse.hasToken())) {
                status = ConfigGet.STATUS_GET_TOKEN;
                logger.log(Level.SEVERE, "[monPathload] ConfigGet Acquired token, yet token is now null.");
            } else {
                try {
                    ServletResponse sResponse = stub.refresh();

                    logger.log(Level.FINEST, "[monPathload] ConfigGet Pathload status refreshed.");

                    if ((sResponse != null) && (sResponse.isOutOfSync())) {
                        peerCache.outOfSync();
                        logger.log(Level.FINEST, "***************** OUT-OF-SYNC ******************");
                        logger.log(Level.WARNING, "[monPathload] This Peer is not allowed to have the token.");
                    }
                } catch (PathloadException e) {
                    MLLogEvent logEv = new MLLogEvent();
                    logEv.put("ModuleName", "monPathload");
                    logEv.put("ErrorName", "Pathload Servlet Connection");
                    logEv.put("ConfigGetState", "Refresh");
                    logEv.put("Exception", e.toString());
                    logEv.put("ConnectionURL", url);

                    logger.log(Level.SEVERE, e.getMessage(), new Object[] { logEv });
                }
            }
        } else {
            logger.log(Level.FINEST, "************ CHECK RELEASE TOKEN END *************");
            releaseToken();
        }
    }

    /**
     * Return the token to the servlet, and enter getToken
     * state 
     *
     */
    private boolean releaseToken() {
        try {
            status = ConfigGet.STATUS_GET_TOKEN;
            stub.releaseToken(servletResponse.getID());
            logger.log(Level.FINE, "[monPathload] ConfigGet Token: " + servletResponse.getID() + " released.");
            servletResponse = null;
        } catch (PathloadException e) {
            MLLogEvent logEv = new MLLogEvent();
            logEv.put("ModuleName", "monPathload");
            logEv.put("ErrorName", "Pathload Servlet Connection");
            logEv.put("ConfigGetState", "Release");
            logEv.put("Exception", e.toString());
            logEv.put("ConnectionURL", url);

            logger.log(Level.SEVERE, e.getMessage(), new Object[] { logEv });
        }
        return true;
    }

    /**
     * Try to announce the servlet we are shutting down
     * 
     * @return	True if operation succeded, false otherwise.
     */
    public boolean shutdown(String motive) {
        boolean bResult = false;

        synchronized (lock) {
            try {
                bResult = stub.shutdown();

                MLLogEvent logEv = new MLLogEvent();
                logEv.put("ModuleName", "monPathload");
                logEv.put("ErrorName", "Pathload Shutdown");
                logEv.put("ConfigGetState", "Shutdown");
                if (motive == null) {
                    motive = "No motive.";
                }
                logEv.put("Motive", motive);
                logEv.put("ConnectionURL", url);
                logger.log(Level.FINE, "[monPathload] MonPathload is announcing shutdown.");
            } catch (PathloadException e) {
                MLLogEvent logEv = new MLLogEvent();
                logEv.put("ModuleName", "monPathload");
                logEv.put("ErrorName", "Pathload Servlet Connection");
                logEv.put("ConfigGet State", "Shutdown");
                logEv.put("Exception", e.toString());
                logEv.put("ConnectionURL", url);

                logger.log(Level.SEVERE, e.getMessage(), new Object[] { logEv });
            }
            url = null;
        }
        return bResult;
    }

    /**
     * Change the URL to witch monPathload now connects.
     * 
     * @param newUrl	The new URL to use.
     */
    public void setUrl(String newUrl) {
        if (newUrl == null) {
            return;
        }

        synchronized (lock) {
            url = newUrl;
            reset = true;
        }
    }
}
