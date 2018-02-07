/*
 * $Id: AppCtrlSessionManager.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.ClientsFarmProxy.tunneling;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ClientsFarmProxy.ClientWorker;
import lia.Monitor.ClientsFarmProxy.FarmCommunication;
import lia.Monitor.ClientsFarmProxy.FarmWorker;
import lia.Monitor.monitor.AppControlMessage;
import lia.Monitor.monitor.MonMessageClientsProxy;
import lia.Monitor.monitor.monMessage;
import lia.util.UUID;
import lia.util.Utils;

/**
 * This class provides the "tunneling" of AppCtrl sessions through the ML Proxy
 * It keeps a reference
 * @author ramiro
 */
public class AppCtrlSessionManager {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(AppCtrlSessionManager.class.getName());

    //the one and only instance
    private static final AppCtrlSessionManager _thisInstance = new AppCtrlSessionManager();

    //the AppCtrl sessions hash
    private final ConcurrentSkipListMap<UUID, AppCtrlSession> appCtrlSessionHash;

    private AppCtrlSessionManager() {
        appCtrlSessionHash = new ConcurrentSkipListMap<UUID, AppCtrlSession>();
    }

    public static final AppCtrlSessionManager getInstance() {
        return _thisInstance;
    }

    public void notifyServiceMessage(final monMessage serviceMsg) {
        AppCtrlSession appCtrlSession = null;

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " AppCtrlSessionMgr: Service MSG: " + serviceMsg);
        }

        try {
            final UUID sessionID = (UUID) serviceMsg.ident;
            appCtrlSession = appCtrlSessionHash.get(sessionID);

            if (appCtrlSession == null) {
                final String errMsg = "Received a AppCtrl msg with no such sessionID(" + sessionID + ") in the hash";
                logger.log(Level.WARNING, errMsg);

                //notify back the service that the session is no longer available
                MonMessageClientsProxy mm = new MonMessageClientsProxy(AppControlMessage.APP_CONTROL_MSG_END_SESSION,
                        serviceMsg.ident, Utils.writeObject(errMsg), appCtrlSession.farmWorker.id);

                appCtrlSession.clientWorker.sendMsg(mm);
                return;
            }

            MonMessageClientsProxy mm = new MonMessageClientsProxy(serviceMsg.tag, serviceMsg.ident, serviceMsg.result,
                    appCtrlSession.farmWorker.id);

            appCtrlSession.clientWorker.sendMsg(mm);
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Exception sending AppCtrl session msg to the client: "
                    + appCtrlSession.clientWorker);
        }
    }

    /**
     * 
     * Processes messages from the client. The AppCtrl session is <b>ALWAYS</b> initiated by the client side.
     * The AppCtrl sessions are added if they do not exist ...
     * 
     * @param clientMsg
     * @param clientWorker
     */
    public void notifyClientMessage(final MonMessageClientsProxy clientMsg, final ClientWorker clientWorker) {
        try {

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, " AppCtrlSessionMgr: Client MSG: " + clientMsg);
            }

            final UUID sessionID = (UUID) clientMsg.ident;
            AppCtrlSession appCtrlSession = appCtrlSessionHash.get(sessionID);

            if (appCtrlSession == null) {
                if (!clientMsg.tag.equals(AppControlMessage.APP_CONTROL_MSG_AUTH_START)) {//session hijacking
                    final String errMsg = "\nNull AppCtrlSession in the session hash and the tag != APP_CONTROL_MSG_AUTH_START"
                            + " ( Session hijacking ?) client: "
                            + clientWorker
                            + " msg: "
                            + clientMsg
                            + "\n The session " + sessionID + " will end\n";
                    logger.log(Level.WARNING, errMsg);

                    //notify back the client
                    MonMessageClientsProxy mmcp = new MonMessageClientsProxy(
                            AppControlMessage.APP_CONTROL_MSG_PROXY_ERR, sessionID, errMsg, clientMsg.farmID);
                    clientWorker.sendMsg(mmcp);

                    return;
                }

                final FarmWorker farmWorker = FarmCommunication.getFarmsHash().get(clientMsg.farmID);
                if (farmWorker == null) {
                    final String errMsg = "\nNull FarmWorker for: " + clientMsg.farmID
                            + " ... Probably the service went down" + " client: " + clientWorker + " msg: " + clientMsg
                            + "\n The session " + sessionID + " will end\n";
                    logger.log(Level.WARNING, errMsg);
                    //notify back the client
                    MonMessageClientsProxy mmcp = new MonMessageClientsProxy(
                            AppControlMessage.APP_CONTROL_MSG_PROXY_ERR, sessionID, errMsg, clientMsg.farmID);
                    clientWorker.sendMsg(mmcp);

                    return;
                }

                appCtrlSession = new AppCtrlSession(sessionID, clientWorker, farmWorker);

                if (appCtrlSessionHash.putIfAbsent(sessionID, appCtrlSession) != null) {
                    //double check ... should not happen
                    final String errMsg = "\nPROTOCOL ERROR !!!!!! This should never happen. Synchronization problem??"
                            + "The session " + sessionID
                            + " already exists in the seesionHash. Should be ended by the remote Client.";
                    logger.log(Level.WARNING, errMsg);

                    //notify back the client
                    MonMessageClientsProxy mmcp = new MonMessageClientsProxy(
                            AppControlMessage.APP_CONTROL_MSG_PROXY_ERR, sessionID, errMsg, clientMsg.farmID);
                    clientWorker.sendMsg(mmcp);
                    return;
                }

                clientWorker.addAppCtrlSession(sessionID);
                farmWorker.addAppCtrlSession(sessionID);

                if (clientWorker.isClosed()) {//closed in between ?
                    logger.log(Level.WARNING, " AppCtrlSession from " + clientWorker
                            + " was closed while processing first message");
                    appCtrlSessionHash.remove(sessionID);
                    return;
                }

            }//if(appCtrlSession == null)

            appCtrlSession.farmWorker.sendMsg(clientMsg);
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Exception processing client message " + clientMsg + " from client: "
                    + clientWorker, t);
        }
    }

    public void notifyClientDown(final ClientWorker clientWorker) {
        final ConcurrentSkipListSet<UUID> appCtrlSessionSet = clientWorker.getAppCtrlSessionSet();

        logger.log(Level.INFO, " ClientWorker [ " + clientWorker + " ] down. appCtrlSessionSet = " + appCtrlSessionSet);

        for (UUID appCtrlSessionID : appCtrlSessionSet) {

            AppCtrlSession appCtrlSession = null;

            try {
                appCtrlSessionID = appCtrlSessionID;
                appCtrlSession = appCtrlSessionHash.remove(appCtrlSessionID);

                if (appCtrlSession == null) {
                    logger.log(Level.WARNING, " ClientWorker [ " + clientWorker + " ] down. No such AppCtrlSession [ "
                            + appCtrlSessionID + " ] in appCtrlSessionHash");
                    continue;
                }

                if (appCtrlSession.farmWorker != null) {
                    MonMessageClientsProxy mmcp = new MonMessageClientsProxy(
                            AppControlMessage.APP_CONTROL_MSG_END_SESSION, appCtrlSessionID,
                            AppControlMessage.EMPTY_PAYLOAD, appCtrlSession.farmWorker.id);
                    appCtrlSession.farmWorker.sendMsg(mmcp);
                    logger.log(Level.INFO, "Notified clientWorker down for appCtrlSessionID: " + appCtrlSessionID
                            + " appCtrlSession.farmWorker: " + appCtrlSession.farmWorker);
                } else {
                    logger.log(Level.WARNING, "\n\n ClientWorker [ " + clientWorker + " ] down. AppCtrlSession [ "
                            + appCtrlSessionID + " ] found in appCtrlSessionHash, but farmWorker is null!!!! \n\n");
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "\n\nGot exception for appCtrlSessionID: " + appCtrlSessionID
                        + " ClientWorker: " + clientWorker + " FarmWorker: "
                        + ((appCtrlSession != null) ? appCtrlSession.clientWorker : " appCtrlSession is NULL!!!\n\n"),
                        t);
            } finally {
                if ((appCtrlSession != null) && (appCtrlSession.farmWorker != null) && (appCtrlSessionID != null)) {
                    if (!appCtrlSession.farmWorker.removeAppCtrlSession(appCtrlSessionID)) {
                        logger.log(Level.WARNING, "The appCtrlSessionID: " + appCtrlSessionID
                                + " was not found in the FarmWorker appCtrlSessionSet");
                    }
                } else {
                    logger.log(Level.WARNING, "Unable to remove appCtrlSessionID: "
                            + appCtrlSessionID
                            + " from FarmWorker appCtrlSessionSet [ appCtrlSession: "
                            + appCtrlSession
                            + ((appCtrlSession == null) ? "" : " appCtrlSession.farmWorker "
                                    + appCtrlSession.farmWorker));
                }
            }
        }
    }

    public void notifyServiceDown(final FarmWorker farmWorker) {

        final ConcurrentSkipListSet<UUID> appCtrlSessionSet = farmWorker.getAppCtrlSessionSet();

        logger.log(Level.INFO, " FarmWorker [ " + farmWorker + " ] down. appCtrlSessionSet = " + appCtrlSessionSet);

        for (UUID appCtrlSessionID : appCtrlSessionSet) {

            AppCtrlSession appCtrlSession = null;

            try {
                appCtrlSessionID = appCtrlSessionID;
                appCtrlSession = appCtrlSessionHash.remove(appCtrlSessionID);

                if (appCtrlSession == null) {
                    logger.log(Level.WARNING, " FarmWorker [ " + farmWorker + " ] down. No such AppCtrlSession [ "
                            + appCtrlSessionID + " ] in appCtrlSessionHash");
                    continue;
                }

                if (appCtrlSession.clientWorker == null) {
                    logger.log(Level.WARNING, " FarmWorker [ " + farmWorker + " ] down. AppCtrlSession [ "
                            + appCtrlSessionID + " ] found in appCtrlSessionHash, but clientWorker is null!!!!");
                    continue;
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "\n\nGot exception for appCtrlSessionID: " + appCtrlSessionID
                        + " FarmWorker: " + farmWorker + " ClientWorker: "
                        + ((appCtrlSession != null) ? appCtrlSession.clientWorker : " appCtrlSession is NULL!!!\n\n"),
                        t);
            } finally {
                if ((appCtrlSession != null) && (appCtrlSession.clientWorker != null) && (appCtrlSessionID != null)) {
                    if (!appCtrlSession.clientWorker.removeAppCtrlSession(appCtrlSessionID)) {
                        logger.log(Level.WARNING, "The appCtrlSessionID: " + appCtrlSessionID
                                + " was not found in the ClientWorker appCtrlSessionSet");
                    }
                } else {
                    logger.log(Level.WARNING, "Unable to remove appCtrlSessionID: "
                            + appCtrlSessionID
                            + " from ClientWorker appCtrlSessionSet. [ appCtrlSession: "
                            + appCtrlSession
                            + ((appCtrlSession == null) ? "" : " appCtrlSession.clientWorker "
                                    + appCtrlSession.clientWorker));
                }
            }
        }//end for

    }

}
