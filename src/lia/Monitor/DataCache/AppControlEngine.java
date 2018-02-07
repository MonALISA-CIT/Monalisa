/*
 * $Id: AppControlEngine.java 7419 2013-10-16 12:56:15Z ramiro $
 */

package lia.Monitor.DataCache;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppControlMessage;
import lia.Monitor.monitor.monMessage;
import lia.app.AppControl;
import lia.util.UUID;
import lia.util.Utils;
import monalisa.security.gridforum.gss.ExtendedGSSContext;
import monalisa.security.gss.globusutils.Certs.GCredentialException;
import monalisa.security.util.AcceptorGSSContext;
import monalisa.security.util.GSSInitData;

import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;

/**
 * App Control Dispatcher - intermediates the communication between the application plugins and remote clients<br>
 * Previously this function was implemented in {@linkplain lia.app.AppControl}.
 * <p>
 * <ul>
 * monMessage structure :
 * <li>tag : "app_control_init": during initial SSL handshake or "app_control_init": command/response communication </li>
 * <li>id : INTEGER, set by client</li>
 * <li>result : byte array </li>
 * </ul>
 * 
 *  @author adim
 *  
 */
public class AppControlEngine {

    // logger used by this class
    private static final Logger logger = Logger.getLogger(AppControlEngine.class.getName());
    // internal buffer used to communicate with AppControl dispatcher
    final static int MAX_QUEUE_SIZE = AppConfig.geti("lia.Monitor.DataCache.AppControlEngine.CLIENT_QUEUE_MAX_SIZE",
            1 << 10);
    // how often the remote client context is checked for validity (in minutes)
    final static int CONTEXT_CHECK_INTERVAL = AppConfig.geti(
            "lia.Monitor.DataCache.AppControlEngine.CONTEXT_CHECK_INTERVAL", 30);

    private GSSInitData gssData = null;

    /**
     * connected clients map: uidAppCtrlSessionID => Client
     */
    private final Map<UUID, AppControlRemoteClient> clients;

    /** connections pool handler */
    final protected ExecutorService pool;

    /**
     * Init the current engine, setting the proxy communication handler
     * 
     * @param proxies -
     *            communication handler that wraps multiple proxy connections
     * @throws GSSException
     * @throws GCredentialException
     */
    public AppControlEngine() throws GCredentialException, GSSException {

        /*
         * load public key autheticator data: trustore containing trusted certificates for administrators
         */
        String store = AppConfig.getProperty("lia.Monitor.SKeyStore");
        String passwd = AppConfig.getProperty("lia.Monitor.SKeyStorePass", "monalisa");

        String alias = "do_not_delete_this_key";
        this.gssData = new GSSInitData(store, passwd, alias, store, passwd, lia.util.net.Util.getCCB(),
                GSSCredential.INITIATE_AND_ACCEPT);
        logger.log(Level.INFO, "AppControl SSL service credentials loaded");

        /*
         * Init the clients map
         */
        clients = new ConcurrentHashMap<UUID, AppControlRemoteClient>();

        /*
         * Init the internal pool used to execute clients communications tasks
         */
        pool = Executors.newCachedThreadPool(new DaemonThreadFactory());
    }

    /**
     * custom thread factory used in connection pool
     */
    private final class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("AppControlRemoteClient pool thread");
            thread.setDaemon(true);
            return thread;
        }
    }

    protected ExecutorService getDispatcherPool() {
        return this.pool;
    }

    /**
     * Called when new monMessage is received from one remote client, through a given proxy [{@link lia.Monitor.DataCache.tcpClientWorker}]
     * 
     * @param mm
     *            the monMessage received
     */
    public void messageReceived(monMessage mm, tcpClientWorker srcProxy) {

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "New AppControl message received:" + "[" + mm.tag + "," + mm.ident + "," + mm.result
                    + "]");
        }
        Object command = mm.result;
        if (!(command instanceof byte[])) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Invalid command received " + command);
            }
            return;
        }
        if (!(mm.ident instanceof UUID)) {
            logger.log(Level.SEVERE, "Invalid message indetifier, Expect UUID, Got " + mm.ident.getClass().getName());
            return;
        }

        UUID sessionID = (UUID) mm.ident;
        // if it's the first time we see this session, we create a new ClientHandler for it
        AppControlRemoteClient client = clients.get(sessionID);
        if (client == null) {
            synchronized (clients) {
                if (!clients.containsKey(sessionID)) {
                    // create a new client handler
                    client = new AppControlRemoteClient(sessionID, srcProxy);
                    // lease a thread in executor pool
                    pool.execute(client);
                    // save the client in the global map
                    clients.put(sessionID, client);
                } else {
                    client = clients.get(sessionID);
                }
            }
        }

        // non-blocking call
        client.deliverMessage(new monMessageProxy(mm, srcProxy));

    }

    Object notifyClientFinished(UUID sessiondID) {
        return clients.remove(sessiondID);
    }

    /**
     * @param sessionID
     * @return: the Client handler associated with the supplied <i>sessionID</i>
     */
    AppControlRemoteClient getClient(UUID sessionID) {
        return this.clients.get(sessionID);
    }

    private final class monMessageProxy {
        monMessage message;
        tcpClientWorker msgSrcProxy;

        public monMessageProxy(monMessage message, tcpClientWorker msgSrcProxy) {
            this.message = message;
            this.msgSrcProxy = msgSrcProxy;
        }

    }

    /**
     * The handler for each client connected to the AppControl interface.<br>
     * Each instance is executed in the local pool. <br>
     * The messages/commands received from remote clients are notified in an internal queue dispatched in a serial manner by the main loop.
     * 
     * @author adim
     */
    private class AppControlRemoteClient implements Runnable {

        // session ID
        UUID sessioID;

        // control flag used to signal main loop termination
        private volatile boolean hasToRun = true;
        private AcceptorGSSContext acceptSecCtx;
        private final tcpClientWorker srcProxy;
        private final AppControl appCtrl;

        /*
         * INTERNALS
         */
        // streams used to interface with AppControl command interpreter
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        private final PrintWriter pw = new PrintWriter(new OutputStreamWriter(baos));
        // blocking queue storing the {@linkplain monMessageProxy} messages addresed to this client
        private final BlockingQueue<monMessageProxy> lbqMessages;

        private int iContextCheckCounter = 0;

        public AppControlRemoteClient(UUID sessionID, tcpClientWorker srcProxy) {
            this.sessioID = sessionID;
            this.srcProxy = srcProxy;
            lbqMessages = new LinkedBlockingQueue<monMessageProxy>(MAX_QUEUE_SIZE);
            this.appCtrl = AppControl.getInstance(false);
        }

        public final void deliverMessage(monMessageProxy message) {
            try {
                if (!lbqMessages.offer(message)) {
                    logger.severe("Cannot deliver message, service queue is full");
                }
            } catch (Throwable t) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "[AppControlRemoteClient " + getName() + "]Cannot deliver message", t);
                }
            }
        }

        public void createAcceptorSecContext(final GSSInitData gData) {
            this.acceptSecCtx = new AcceptorGSSContext(gData);
        }

        public ExtendedGSSContext getGSSContext() {
            if (acceptSecCtx != null) {
                return acceptSecCtx.context;
            }
            return null;
        }

        @Override
        public void run() {
            Object oMessage;
            monMessage monMessage;
            monMessageProxy proxyMessage;
            byte[] payload;
            String msgType = null;
            String errMessage = null;
            main_loop: while (hasToRun) {
                // consume lbqMessages
                try {
                    // oMessage = lbqMessages.take();
                    oMessage = lbqMessages.poll(60, TimeUnit.SECONDS);

                    // is the source proxy still alive?
                    if (oMessage == null) {
                        if (!this.srcProxy.isConnected()) {
                            if (logger.isLoggable(Level.INFO)) {
                                logger.log(Level.INFO, "[AppControlRemoteClient " + getName()
                                        + "] Proxy connection closed.");
                            }
                            break main_loop;
                        }
                    }
                    if ((oMessage != null) && (oMessage instanceof monMessageProxy)) {
                        proxyMessage = (monMessageProxy) oMessage;
                        // discard bad formatted messages
                        if (!(proxyMessage.message.result instanceof byte[])) {
                            if (logger.isLoggable(Level.SEVERE)) {
                                logger.log(Level.SEVERE, "[AppControlRemoteClient " + getName()
                                        + "] Invalid monMessageProxy .Got "
                                        + proxyMessage.message.result.getClass().getName());
                            }
                            continue;
                        }
                        // else, valid message
                        payload = (byte[]) proxyMessage.message.result;
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "[AppControlRemoteClient " + getName()
                                    + "] subscribe request received:" + proxyMessage.message);
                        }

                        /**
                         * <monMessage dispatch>
                         */
                        msgType = proxyMessage.message.tag;

                        // client disconnected
                        if (msgType.equalsIgnoreCase(AppControlMessage.APP_CONTROL_MSG_END_SESSION)) {
                            break main_loop;
                        }

                        // else, start a new session?
                        if (msgType.equalsIgnoreCase(AppControlMessage.APP_CONTROL_MSG_AUTH_START)) {
                            this.createAcceptorSecContext(AppControlEngine.this.gssData);
                            this.acceptSecCtx.setUpSecurityContext();
                        }

                        // handle ssl handshake
                        if (msgType.equalsIgnoreCase(AppControlMessage.APP_CONTROL_MSG_AUTH_START)
                                || msgType.equalsIgnoreCase(AppControlMessage.APP_CONTROL_MSG_AUTH)) {
                            byte[] outToken = this.acceptSecCtx.consumeInitSecContextMsg(payload);
                            if (outToken != null) {
                                if (this.acceptSecCtx.context.isEstablished()) {
                                    /* finally send the FINISHED signal to client */
                                    monMessage = new monMessage(AppControlMessage.APP_CONTROL_MSG_AUTH_FINISHED,
                                            this.sessioID, outToken);
                                    srcProxy.WriteObject(monMessage, tcpClientWorker.ML_AGENT_MESSAGE);
                                    if (logger.isLoggable(Level.FINE)) {
                                        logger.log(Level.FINE, "[AppControlRemoteClient " + getName()
                                                + "] [AUTH] Context established. Sending AUTH_FINISHED"
                                                + getSecurityCtxInfo(this.acceptSecCtx.context));
                                    }
                                } else {
                                    monMessage = new monMessage(AppControlMessage.APP_CONTROL_MSG_AUTH, this.sessioID,
                                            outToken);
                                    // AppControlEngine.this.proxiesComm.rezToAllProxies(monMessage);
                                    // send to the source, withe ML_AGENT_MESSAGE priority
                                    srcProxy.WriteObject(monMessage, tcpClientWorker.ML_AGENT_MESSAGE);
                                    logger.fine("[AppControlRemoteClient " + getName()
                                            + "] [AUTH] Send gss message to peer " + getName() + ":" + outToken.length);
                                }
                            } else {
                                logger.log(Level.WARNING, "[AppControlRemoteClient " + getName()
                                        + "]  [AUTH] Received null GSS token. Discarding");
                            }
                        } else if (msgType.equalsIgnoreCase(AppControlMessage.APP_CONTROL_MSG_END_SESSION)) {
                            if (logger.isLoggable(Level.FINE)) {
                                logger.log(Level.FINE, "[AppControlRemoteClient " + getName()
                                        + "] Got APP_CONTROL_MSG_END_SESSION. Quitting session");
                            }
                            break main_loop;
                        } else if (msgType.equalsIgnoreCase(AppControlMessage.APP_CONTROL_MSG_PROXY_ERR)) {
                            logger.log(Level.FINE, "[AppControlRemoteClient " + getName()
                                    + "] Got APP_CONTROL_MSG_PROXY_ERR from proxy. Quitting session: "
                                    + new String(payload));
                            break main_loop;
                        } else if (msgType.equalsIgnoreCase(AppControlMessage.APP_CONTROL_MSG_CMD)) {
                            // sanity check of context state
                            if (!isContextValid()) {
                                // cleanup
                                this.acceptSecCtx.dispose();
                                break main_loop;
                            }

                            // ...and finally, the real command interpreter
                            byte[] decrypted = this.acceptSecCtx.unwrap(payload, 0, payload.length);
                            AppControlMessage cmd = (AppControlMessage) Utils.readObject(decrypted);
                            if (logger.isLoggable(Level.FINEST)) {
                                logger.log(Level.FINEST, "Command received:" + cmd);
                            }
                            // use the same dispatcher from the old AppControl
                            this.baos.reset();
                            appCtrl.dispatch(cmd.msg, this.pw);
                            AppControlMessage response = new AppControlMessage(cmd.cmdID, new String(
                                    this.baos.toByteArray()));
                            byte[] clearResponse = Utils.writeObject(response);
                            // send the response
                            monMessage = new monMessage(AppControlMessage.APP_CONTROL_MSG_CMD, this.sessioID,
                                    this.acceptSecCtx.wrap(clearResponse, 0, clearResponse.length));
                            srcProxy.WriteObject(monMessage, tcpClientWorker.ML_AGENT_MESSAGE);
                            if (logger.isLoggable(Level.FINEST)) {
                                logger.log(Level.FINEST, "[AppControlRemoteClient " + getName()
                                        + "] Command response sent:" + response);
                            }
                        } else {
                            if (logger.isLoggable(Level.WARNING)) {
                                logger.log(Level.WARNING, "[AppControlRemoteClient " + getName()
                                        + "] Discarding unknown message type:" + msgType + " payload:"
                                        + new String(payload));
                            }
                        }
                        /**
                         * </monMessage dispatch>
                         */
                    } else {// no valid meesage received
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, "[AppControlRemoteClient " + getName()
                                    + "] Queue polling period elapsed, no valid message received");
                        }
                        // check the context state periodically (30min by default)
                        if (++iContextCheckCounter >= CONTEXT_CHECK_INTERVAL) {
                            iContextCheckCounter = 0;
                            if (!isContextValid()) {
                                // cleanup
                                this.acceptSecCtx.dispose();
                                break main_loop;
                            }
                        }
                    }
                } catch (Throwable t) {
                    errMessage = "[AppControlRemoteClient " + getName()
                            + "] Protocol exception occurred. Terminating sesion.,, " + t;
                    sendErrMsg(errMessage);
                    logger.log(Level.SEVERE, errMessage, t);
                    // cleanup
                    this.acceptSecCtx.dispose();
                    break main_loop;
                }
            }// main-loop

            // when main loop is finished (err/end_session), notify the manager that we cease to run
            final Object obj = AppControlEngine.this.notifyClientFinished(this.sessioID);
            if (obj != null) {
                logger.log(Level.INFO, "[AppControlRemoteClient " + getName() + " cleaned up. Connection closed");
            } else {
                logger.log(Level.INFO, "[AppControlRemoteClient " + getName() + " already cleaned up");
            }
        }

        /**
         * Check the current state of the security context
         * 
         * @return false if this context is not established or it expired, true otherwise
         */
        private boolean isContextValid() {
            if (!this.acceptSecCtx.context.isEstablished() || (this.acceptSecCtx.context.getLifetime() <= 0)) {
                // context expired, send RETRY message to client
                String errMessage = "[AppControlRemoteClient "
                        + getName()
                        + "] Command received but the SSL context is not valid (not yet established or already expired). Quitting...";
                sendRetryMsg(errMessage);
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, errMessage);
                }
                return false;
            }
            return true;
        }

        private String getSecurityCtxInfo(ExtendedGSSContext context) {
            String info = "";
            try {
                info = "\nInitiator:" + context.getSrcName();
            } catch (GSSException e) {
                // context not established, no initiator set
            }
            return info + "\nLifeTime:" + context.getLifetime() + "\nA/I/C :" + context.getAnonymityState() + "/"
                    + context.getIntegState() + "/" + context.getConfState();
        }

        private String getName() {
            return sessioID.toString();
        }

        public void stopIt() {
            hasToRun = false;
        }

        /**
         * Send an error to remote client
         * 
         * @param sErrMsg
         */
        private void sendErrMsg(String sErrMsg) {
            try {
                monMessage monMessage = new monMessage(AppControlMessage.APP_CONTROL_MSG_ERR, this.sessioID, sErrMsg);
                srcProxy.WriteObject(monMessage, tcpClientWorker.ML_AGENT_MESSAGE);
            } catch (Exception e) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE,
                            "Got exception while trying to send err message to remote client, Message not sent", e);
                }
            }
        }

        /**
         * Send a retry message to remote client <br>
         * This type of message is sent when the context is expired and needs a new handshake phase from the client.
         * 
         * @param sErrMsg
         */
        private void sendRetryMsg(String sErrMsg) {
            try {
                monMessage monMessage = new monMessage(AppControlMessage.APP_CONTROL_MSG_AUTH_RETRY, this.sessioID,
                        sErrMsg);
                srcProxy.WriteObject(monMessage, tcpClientWorker.ML_AGENT_MESSAGE);
            } catch (Exception e) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE,
                            "Got exception while trying to send err message to remote client, Message not sent", e);
                }
            }
        }

    }
}
