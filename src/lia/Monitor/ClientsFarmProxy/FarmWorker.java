/*
 * $Id: FarmWorker.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.ClientsFarmProxy;

import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ClientsFarmProxy.AgentsPlatform.AgentMessage;
import lia.Monitor.ClientsFarmProxy.MLLogger.ServiceLoggerManager;
import lia.Monitor.ClientsFarmProxy.MLLogger.SimpleFileWriter;
import lia.Monitor.ClientsFarmProxy.tunneling.AppCtrlSessionManager;
import lia.Monitor.monitor.EMsg;
import lia.Monitor.monitor.MonMessageClientsProxy;
import lia.Monitor.monitor.MonaLisaEntry;
import lia.Monitor.monitor.monMessage;
import lia.util.Utils;
import lia.util.logging.comm.MLLogMsg;
import lia.util.logging.comm.ProxyLogMessage;
import lia.util.mail.MailFactory;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;

/**
 * More or less like the ClientWorker; do not cycle
 * 
 * @author ramiro
 */
public class FarmWorker extends GenericProxyWorker {

    private static final Logger logger = Logger.getLogger(FarmWorker.class.getName());

    private static final AppCtrlSessionManager appCtrlSessMgr = AppCtrlSessionManager.getInstance();

    public final ServiceID id; // worker id

    private final AtomicReference<ServiceItem> siRef; // worker id

    private final Set<String> serviceGroups = new ConcurrentSkipListSet<String>();

    public final ConcurrentHashMap<String, monMessage> registeredFilters = new ConcurrentHashMap<String, monMessage>();

    private final AtomicBoolean notified = new AtomicBoolean(false);

    private String strToFile;

    private String strName;

    private long lastSizeMsg = 0;

    private static final java.util.concurrent.Executor mailThPool;

    private static final java.util.concurrent.Executor loggerThPool;

    public static final String EMAIL_MSG_ID = "MLCapOK";

    public static final String MLLOG_MSG_ID = "MLLogOK";

    private final AtomicReference<MFarmClientConfigInfo> clientConfigInfoRef = new AtomicReference<MFarmClientConfigInfo>();

    static {
        ThreadPoolExecutor texecutor = new ThreadPoolExecutor(3, 3, 2 * 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {

                    AtomicLong l = new AtomicLong(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "( ML ) ProxyTCPWorkerTask " + l.getAndIncrement());
                    }
                });
        // it will be added in 1.6
        texecutor.allowCoreThreadTimeOut(true);
        texecutor.prestartAllCoreThreads();
        mailThPool = texecutor;

        ThreadPoolExecutor texecutor2 = new ThreadPoolExecutor(3, 3, 2 * 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {

                    AtomicLong l = new AtomicLong(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "( ML ) ProxyTCPWorkerTask " + l.getAndIncrement());
                    }
                });
        // it will be added in 1.6
        texecutor.allowCoreThreadTimeOut(true);
        texecutor2.prestartAllCoreThreads();
        loggerThPool = texecutor2;

    }

    private static final class MLLoggerTask implements Runnable {

        Object message;

        FarmWorker fw;

        MLLoggerTask(Object o, FarmWorker fw) {
            this.message = o;
            this.fw = fw;
        }

        @Override
        public void run() {
            try {
                if (message instanceof MLLogMsg) {
                    MLLogMsg mllm = (MLLogMsg) message;
                    try {
                        try {
                            SimpleFileWriter.getInstance().publishRecord(mllm);
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, "Got exc publishRecord() to SimpleFileWriter", t);
                        }

                        try {
                            ServiceLoggerManager.getInstance().publish(mllm);
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, "Got exc publishRecord() ServiceLoggerManager", t);
                        }

                        if (mllm.reqNotif) {
                            ProxyLogMessage plm = new ProxyLogMessage();
                            plm.ackID = mllm.id;

                            monMessage m = new monMessage(MLLOG_MSG_ID, null, Utils.writeObject(plm));
                            fw.sendMsg(m);
                        }
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " Cannot send ProxyLogMessage ", t);
                    }
                } else {
                    logger.log(Level.WARNING, " Got unk MLLogMsg message: " + message);
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ MLLoggerTask ] Got exception processing message " + message, t);
            }
        }
    }

    private final static class EmailTask implements Runnable {

        Object message;

        FarmWorker fw;

        EmailTask(Object o, FarmWorker fw) {
            this.message = o;
            this.fw = fw;
        }

        @Override
        public void run() {
            try {
                if (message instanceof EMsg) {
                    EMsg em = (EMsg) message;
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, " Sending EmailMsg .... \n" + em.getHeader());
                    }

                    try {
                        MailFactory.getMailSender().sendMessage(em, false);
                        Integer eID = em.getID();

                        monMessage m = new monMessage(EMAIL_MSG_ID, eID, eID);
                        fw.sendMsg(m);
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " Cannot send mail ", t);
                    }
                } else {
                    logger.log(Level.WARNING, " Got unk EMAIL_MSG_ID message: " + message);
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ EmailTask ] Got exception processing message " + message, t);
            }
        }
    }

    public FarmWorker(ServiceID id, ServiceItem si, ProxyTCPWorker ptw) {
        super(ptw);
        this.id = id;
        this.siRef = new AtomicReference<ServiceItem>(si);
        updateGroups();
        strName = " [ FarmWorker ] [ " + ptw.conn.getEndPointAddress().getCanonicalHostName() + " ] ";
        strToFile = strName + "\n\t\t[NrOfMsgInLastHour]: ";
        strName = "[FarmWorker] " + id;
        strToFile = strName + "\n\t\t[NrOfMsgInLastHour]: ";
    }

    // moved here from run()
    @Override
    public boolean commInit() {

        if (infoToFile.enabled()) {
            infoToFile.writeToFile((new Date()) + " : " + strName + " started ! \n\n");
        }

        final ConcurrentMap<Integer, PredicatesInfoCache> IDSave = ServiceCommunication.getIDSave();

        if (IDSave != null) {
            for (final PredicatesInfoCache mic : IDSave.values()) {
                if (this.id.equals(mic.farmID)) {
                    MonMessageClientsProxy mm = (MonMessageClientsProxy) mic.result;
                    monMessage sentMessage = new monMessage(mm.tag, mm.ident, mm.result);
                    sendMsg(sentMessage);
                    ServiceCommunication.incSendMsg();
                }
            }
        }
        return true;
    }

    @Override
    public void processMsg(final MonMessageClientsProxy mm) {
        monMessage sentMessage = new monMessage(mm.tag, mm.ident, mm.result);
        ServiceCommunication.incSendMsg();

        if (sentMessage.ident instanceof String) { // este vorba despre
            // un filtru
            /* it's a filtre */

            final String filterName = (String) sentMessage.ident;
            StringBuilder sb = new StringBuilder();
            sb.append("\nFarmWorker ====> filtrul : " + filterName + " ... ");
            if (!registeredFilters.containsKey(filterName)) {
                /* a new filter for this connection */
                sb.append("Not Added. Adding it!");
                registeredFilters.put((String) sentMessage.ident, sentMessage);
                ptw.sendMsg(sentMessage);
            } else {
                sb.append("Already added. Not adding it!\n");
            }
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, sb.toString());
            }
        } else {
            /* it's a predicate */
            ptw.sendMsg(sentMessage);
        }
    }

    @Override
    public void processMsg(monMessage msg) {
        try {
            /* a messae from farm */
            ptw.sendMsg(msg);
            ServiceCommunication.incSendMsg();

            /**
             * ------------------------------------------------------------------ log how many messages came on this
             * connection
             */
            if (infoToFile.enabled() && ((System.currentTimeMillis() - lastUpdateFileInfo) >= 3600000)) {

                lastUpdateFileInfo = System.currentTimeMillis();

                long nrMsg = msgsLastHourContor.getAndSet(0L);

                long sizeMsgs = getSentBytes();

                long lastHourMsgsSize = 0;

                if ((lastSizeMsg == 0) || (lastSizeMsg > sizeMsgs)) {
                    lastHourMsgsSize = sizeMsgs;
                } else {
                    lastHourMsgsSize = sizeMsgs - lastSizeMsg;
                } // if = else
                lastSizeMsg = sizeMsgs;

                infoToFile.writeToFile((new Date()) + " : " + strToFile + nrMsg + " \n\t\t[size] "
                        + (lastHourMsgsSize / 1000000) + "\n\n");

            } // if
            /**
             * finished logging ---------------------------------------------------------------------
             */

        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ FarmWorker ] got exc processing msg ", t);
        }
    }

    @Override
    public void notifyMessage(Object o) {

        monMessage mFarm = (monMessage) o;

        if (mFarm.tag != null) {
            // process a message between agents
            if (mFarm.tag.startsWith(monMessage.ML_AGENT_TAG)) {
                boolean sent = false;
                try {
                    AgentMessage msg = (AgentMessage) mFarm.result;
                    String agentAddr = msg.agentAddrS;

                    if (agentAddr != null) {
                        String sAddr = agentAddr.substring(agentAddr.indexOf("@") + 1);
                        if ((sAddr != null) && sAddr.equals(id.toString())) {
                            sent = true;
                            if (mFarm.tag.startsWith(monMessage.ML_AGENT_CTRL_TAG)) {
                                String ctrl = mFarm.tag.substring(mFarm.tag.indexOf(":") + 1);
                                FarmCommunication.fwdAgentsMsg(msg, true, ctrl, id);
                            } else {
                                FarmCommunication.fwdAgentsMsg(msg, false, null, id);
                            } // if - else
                        }
                    } // if

                    // if not sent, send an error message back to the sending agent ....
                    if (sent == false) {
                        monMessage monM = new monMessage(monMessage.ML_AGENT_ERR_TAG, null, msg);
                        sendMsg(monM);
                    } // if set false

                } catch (Throwable t) {
                    // if an exception occured while trying to process agent message, send an error message back to the
                    // sending agent ....
                    monMessage monM = new monMessage(monMessage.ML_AGENT_ERR_TAG, null, mFarm.result);
                    sendMsg(monM);
                    logger.log(Level.WARNING, " [ FarmWorker ] Got exception processing aggent msg: " + mFarm, t);
                    t.printStackTrace();
                    return;
                } // try - catch
                return;
            } // if

            // APP_CTRL Msg
            if (mFarm.tag.startsWith(monMessage.ML_APP_CTRL_TAG)) {
                appCtrlSessMgr.notifyServiceMessage(mFarm);
                return;
            }

            // EMAIL MESSAGE
            if (mFarm.tag.equals(EMAIL_MSG_ID)) {
                mailThPool.execute(new EmailTask(mFarm.result, this));
                return;
            }

            // MLLogMsg MESSAGE
            if (mFarm.tag.equals(MLLOG_MSG_ID)) {
                loggerThPool.execute(new MLLoggerTask(mFarm.result, this));
                return;
            }
        }

        final MonMessageClientsProxy mm = new MonMessageClientsProxy(mFarm.tag, mFarm.ident, mFarm.result, id);

        try {
            ServiceCommunication.receiveMessage(mm);
            ServiceCommunication.incRecvMsg();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ FarmWorker ] Got exception processing msg: " + mFarm, t);
        }
    } // notifyMessage

    @Override
    public String toString() {
        return "FarmWorker [ " + strName + " SID: " + id + " ]";
    }

    @Override
    public void notifyConnectionClosed() {
        try {
            if (notified.compareAndSet(false, true)) {
                appCtrlSessMgr.notifyServiceDown(this);
                FarmCommunication.notifyConnectionClosed(this.id);
                stopIt();
                /*
                 * log to file
                 */
                if (infoToFile.enabled()) {
                    long nrMsg = msgsLastHourContor.getAndSet(0L);
                    long sizeMsgs = getSentBytes();
                    long lastHourMsgsSize = 0;
                    if ((lastSizeMsg == 0) || (lastSizeMsg > sizeMsgs)) {
                        lastHourMsgsSize = sizeMsgs;
                    } else {
                        lastHourMsgsSize = sizeMsgs - lastSizeMsg;
                    } // if = else
                    lastSizeMsg = sizeMsgs;
                    infoToFile.writeToFile((new Date()) + " [LastInfoBecauseIDie] " + strToFile + nrMsg
                            + " \n\t\t[size] " + lastHourMsgsSize + "\n\n");
                } // if
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ FarmWorker ] Got exc notifyConnectionClosed()", t);
        }
    }

    public ServiceItem getServiceItem() {
        return this.siRef.get();
    }

    public ServiceItem getAndSetServiceItem(final ServiceItem cServiceItem) {
        try {
            return this.siRef.getAndSet(cServiceItem);
        } finally {
            updateGroups();
        }
    }

    private final void updateGroups() {
        final Set<String> newGroups = new TreeSet<String>();
        final ServiceItem si = this.siRef.get();
        if (si != null) {
            final MonaLisaEntry mle = Utils.getEntry(si, MonaLisaEntry.class);
            final String[] groups = Utils.getSplittedListFields(mle.Group);
            if ((groups != null) && (groups.length > 0)) {
                for (final String group : groups) {
                    newGroups.add(group);
                }
            }
        } // if

        serviceGroups.addAll(newGroups);

        for (final Iterator<String> it = serviceGroups.iterator(); it.hasNext();) {
            final String oldGroup = it.next();
            if (!newGroups.contains(oldGroup)) {
                logger.log(Level.INFO, " [ FarmWorker ] " + this.ptw + " removed oldGroup: " + oldGroup);
                it.remove();
            }
        }

        logger.log(Level.INFO, " [ FarmWorker ] [ updateGroups ] " + this.ptw + " groups: " + getGroups());
    }

    public final Set<String> getGroups() {
        return Collections.unmodifiableSet(serviceGroups);
    }

    public MFarmClientConfigInfo getClientConfigInfo() {
        return clientConfigInfoRef.get();
    }

    public MFarmClientConfigInfo getAndSetClientConfigInfo(MFarmClientConfigInfo lastConfMon) {
        return clientConfigInfoRef.getAndSet(lastConfMon);
    }
}
