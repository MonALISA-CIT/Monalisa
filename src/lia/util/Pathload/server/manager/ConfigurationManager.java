/**
 * Managers represent the exposed side of the Pathload
 * Config Application.
 */
package lia.util.Pathload.server.manager;

import java.util.Iterator;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.Pathload.server.PathloadLogger;
import lia.util.Pathload.server.PeerCache;
import lia.util.Pathload.server.PeerInfo;
import lia.util.Pathload.server.Token;
import lia.util.Pathload.server.XMLWritable;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * The Configuration Manager is the one who releases tokens
 * to the pathload clients. Only one token may exist at one time.
 * A token is required for performing a pathload measurement.
 * A token contains an owner, a src host, a dest host and a token
 * time.
 * If MAX_TOKEN_AGING_TIME has passed without someone returning
 * the token, a new token is released and the old one is invalidated.
 * 
 * @author heri
 *
 */
public class ConfigurationManager implements XMLWritable {

    private final PeerCache peerCache;
    private Token token;
    private final Object lock;

    /**
     * Logging component
     */
    private static final Logger logger = Logger.getLogger(ConfigurationManager.class.getName());

    /**
     * WebLogging component
     */
    private final PathloadLogger log;

    private static ConfigurationManager miniMe = new ConfigurationManager();

    /**
     * Only one instance of the ConfigurationManager is 
     * allowed, so it's constructor is marked private.
     * Use the ConfigurationManager.getInstance() method
     * to aquire the singleton instance.
     *
     */
    private ConfigurationManager() {
        peerCache = new PeerCache();
        token = null;
        lock = new Object();
        log = PathloadLogger.getInstance();
    }

    /**
     * Get the sole instance of the ConfigurationManager
     * 
     * @return		The ConfigurationManager
     */
    public static ConfigurationManager getInstance() {
        return miniMe;
    }

    /**
     * Request the token. This method is called by pathload
     * clients to register themselfs and check for permission
     * to perform a new measurement.
     * 
     * @param p		PeerInfo of the requesting host.
     * @return		The Token if the token is granted, null
     * 				otherwise.
     */
    public Token getToken(PeerInfo p) {
        Token tokenResult = null;
        if (p == null) {
            return null;
        }

        synchronized (lock) {
            if (!peerCache.contains(p)) {
                log(Level.FINE, p.toString() + " added to the cache.");
                peerCache.add(p);
                tokenResult = null;
            } else {
                peerCache.refresh(p);
                if ((token == null) || (!token.isAlive(System.currentTimeMillis()))) {
                    token = createNewToken(token);
                    if (token != null) {
                        log(Level.INFO, "A new token " + token.toString() + "was created.");
                    }
                }
                if (token != null) {
                    tokenResult = token.getToken(p);
                    if (tokenResult != null) {
                        log(Level.FINEST, "**************** TOKEN ACQUIRED *****************");
                        log(Level.INFO, "Token " + tokenResult.toString() + " aquired by " + tokenResult.getOwner());
                    }
                }
            }
        }

        return tokenResult;
    }

    /**
     * Announce the release of the token. This usually happens
     * at the successful end of a measurement.
     * 
     * @param t		The Token to be returned.
     * @return		True if successfull, false otherwise.
     */
    public boolean releaseToken(Token t) {
        boolean bResult = false;
        if (t == null) {
            return false;
        }

        synchronized (lock) {
            if ((token != null) && (token.equals(t))) {
                log(Level.FINEST, "**************** TOKEN RELEASED *****************");
                log(Level.FINE, "Token " + token.toString() + " released.");
                token = null;
                bResult = true;
            }
        }
        return bResult;
    }

    /**
     * Announce yourself as being alive and check-in with
     * the ConfigurationManager. This will return false if p is
     * not the owner of the token, or p is not in the peer cache.
     * 
     * @param p		PeerInfo of the requesting host.
     * @return		True if successfull, false otherwise.
     */
    public boolean refresh(PeerInfo p) {
        boolean bResult = false;
        if (p == null) {
            return false;
        }

        synchronized (lock) {
            if (peerCache.contains(p)) {
                if ((token != null) && (token.getOwner().equals(p))) {
                    peerCache.refresh(p);
                    log(Level.FINEST, "**************** PEER REFRESHED *****************");
                    log(Level.FINE, p.toString() + " refreshed its status.");
                    bResult = true;
                } else {
                    log(Level.FINEST, "***************** OUT-OF-SYNC ******************");
                    log(Level.FINER, p.toString() + " tried to refresh its status but " + "the token isn't his.");
                }
            } else {
                log(Level.FINER, p.toString() + " tried to refresh its status but "
                        + "it is not part of the PeerCache.");
            }
        }

        return bResult;
    }

    /**
     * This is a cleanup method called by the PeerInfo p.
     * It is used to exit the system cleanly. 
     * 
     * @param p		PeerInfo that announces its shutdown
     * @return		True if operation succeded, false otherwise
     */
    public boolean shutdown(PeerInfo p) {
        boolean bResult = false;
        if (p == null) {
            return false;
        }

        synchronized (lock) {
            log(Level.INFO, "**************** PEER SHUTDOWN *****************");
            log(Level.INFO, "Peer " + p.toString() + " is shutting down.");
            if (token != null) {
                if (token.getSrcHost().equals(p)) {
                    token = null;
                    log(Level.INFO, "Peer had the current token. I will release a new one.");
                    token = createNewToken(null);
                }
            }
            bResult = peerCache.remove(p);
        }

        return bResult;
    }

    /**
     * Run a clean sweep on the backend cache and purge
     * dead peer hosts.
     *
     */
    public void cleanUpDeadPeers() {
        synchronized (lock) {
            if (peerCache != null) {
                Vector deadPeers = peerCache.cleanUpDeadPeers();
                if ((token != null) || (deadPeers != null) || (!deadPeers.isEmpty())) {
                    for (Iterator it = deadPeers.iterator(); it.hasNext();) {
                        PeerInfo p = (PeerInfo) it.next();
                        if (token.getSrcHost().equals(p)) {
                            token = null;
                            log(Level.INFO, "Peer had the current token. I will release a new one.");
                            token = createNewToken(null);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns  a string array with the peers with whom
     * pi is currently is enganged in monitoring activity.
     * If pi is not yet active, it returns null
     * 
     * @param pi	Current Peer Info
     * @return		Array with peers in the current group
     * 				or null if pi is null or pi is not
     * 				yet in the current peers group
     */
    public String[] getGroupOfPeer(PeerInfo pi) {
        String[] result = null;
        if (pi == null) {
            return null;
        }

        synchronized (lock) {
            Set s = peerCache.getCurrentPeersKeySet();
            if (s != null) {
                Vector currentSet = new Vector(s);
                if ((currentSet.size() > 1) && (currentSet.contains(pi))) {
                    result = new String[currentSet.size() - 1];
                    int contor = 0;
                    for (Iterator it = currentSet.iterator(); it.hasNext();) {
                        PeerInfo p = (PeerInfo) it.next();
                        if (!p.getIpAddress().equals(pi.getIpAddress())) {
                            result[contor++] = p.getIpAddress();
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Release a new token into the network.
     * This can either happen when the token is null or when 
     * the token isn't Alive.
     * The Token is null when it thas been released or when
     * no token has been created at all.
     * <b>This method will return null if the release of a new
     * token is not allowed by the PeerCache.</b> 
     * return null;
     * @param oldToken		The old Token. Null if it has been
     * 						released or !Null if it'd dead and
     * 						the owner must be removed from the
     * 						cache.
     * @return				The new token.
     */
    private Token createNewToken(Token oldToken) {
        Token nextToken = null;
        PeerInfo srcHost;
        PeerInfo destHost;

        if (oldToken != null) {
            if (peerCache.contains(oldToken.getOwner())) {
                log(Level.FINER, "Peer " + oldToken.getOwner().toString() + " lost the token.");
            }
        }
        srcHost = peerCache.getNextSrcHost();
        if (srcHost != null) {
            destHost = peerCache.getNextDestHost(srcHost);
            if (destHost != null) {
                nextToken = new Token(srcHost, srcHost, destHost);
            }
        }

        return nextToken;
    }

    /**
     * Dual logging, to file and to web interface.
     * Should dissapear in future versions.
     * 
     * @param level			Logging utility level of the message
     * @param message		The message being logged.
     */
    private void log(Level level, String message) {
        if ((level == null) || (message == null)) {
            return;
        }

        synchronized (lock) {
            if (logger.isLoggable(level)) {
                logger.log(level, message);
            }
            log.log(level, message);
        }
    }

    /**
     * Create XML of the internal status
     */
    @Override
    public Element getXML(Document document) {
        Element configManagerElement = document.createElement("ConfigurationManager");
        Element temp;

        synchronized (lock) {
            if (token != null) {
                temp = token.getXML(document);
                configManagerElement.appendChild(temp);
            }
            temp = peerCache.getXML(document);
            configManagerElement.appendChild(temp);

            temp = log.getXMLLastRound(document);
            if (temp != null) {
                configManagerElement.appendChild(temp);
            }
        }

        return configManagerElement;
    }
}
