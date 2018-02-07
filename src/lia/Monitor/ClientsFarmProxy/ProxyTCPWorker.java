/*
 * $Id: ProxyTCPWorker.java 7419 2013-10-16 12:56:15Z ramiro $
 */

package lia.Monitor.ClientsFarmProxy;

import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Vector;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ClientsFarmProxy.acl.AccessControlManager;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.MonMessageClientsProxy;
import lia.Monitor.monitor.MonaLisaEntry;
import lia.Monitor.monitor.SiteInfoEntry;
import lia.Monitor.monitor.monMessage;
import lia.Monitor.monitor.tcpConn;
import lia.Monitor.monitor.tcpConnNotifier;
import lia.util.Utils;
import lia.util.ntp.NTPDate;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.lookup.entry.Name;

/**
 * Helper Class for tcpConn for both farms and clients It "wraps" the TCP communication between ML Services and MLProxy
 * and also between ML Clients and MLProxy
 * 
 * @author ramiro
 */
public class ProxyTCPWorker implements tcpConnNotifier {

    private static final Logger logger = Logger.getLogger(ProxyTCPWorker.class.getName());

    // THE tcpConnWrapper for the Socket
    protected final tcpConn conn;

    // A ClientWorker or a FarmWorker
    public volatile GenericProxyWorker myComm = null;

    public final AtomicReference<GenericProxyWorker> myCommRef = new AtomicReference<GenericProxyWorker>(null);

    // Can we stop running ? I'm so tired
    protected volatile boolean active = true;

    // allow multiple stopIt() invocations
    private final AtomicBoolean notified = new AtomicBoolean(false);

    private final String myName;

    private final AtomicBoolean bPrevSend = new AtomicBoolean(false);

    ArrayList<Object> prevMsgs = new ArrayList<Object>();

    private final PriorityQueue<ProxyPriorityMsg> queue = new PriorityQueue<ProxyPriorityMsg>();

    private final ProxyTCPWorkerTask theRealWorker;

    private final long startTimeNano;

    private static final Executor executor;

    private static final ScheduledExecutorService queueCheckerExecutor = Executors.newScheduledThreadPool(Runtime
            .getRuntime().availableProcessors() * 2);

    private static final AtomicInteger MAX_QUEUE_SIZE = new AtomicInteger(15000);

    private static final AtomicInteger FIRST_QUEUE_THRESHOLD_LIMIT = new AtomicInteger(MAX_QUEUE_SIZE.get() / 2);

    private static final AtomicInteger SECOND_QUEUE_THRESHOLD_LIMIT = new AtomicInteger((MAX_QUEUE_SIZE.get() * 3) / 4);

    private static final AtomicInteger LAST_QUEUE_THRESHOLD_LIMIT = new AtomicInteger((MAX_QUEUE_SIZE.get() * 9) / 10);

    private static final AtomicLong MAX_QUEUE_THRESHOLD_DELAY = new AtomicLong(5 * 1000);

    private final ScheduledFuture<?> futureVerifier;

    private static final Object bigRemoveMeLock = new Object();

    static {
        ThreadPoolExecutor texecutor = new ThreadPoolExecutor(3, 512, 2 * 60, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(), new ThreadFactory() {

                    AtomicLong l = new AtomicLong(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "( ML ) ProxyTCPWorkerTask " + l.getAndIncrement());
                    }
                });
        texecutor.setRejectedExecutionHandler(new RejectedExecutionHandler() {

            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                try {
                    // slow down a little bit
                    final long SLEEP_TIME = Math.round(Math.random() * 1000D);
                    try {
                        Thread.sleep(SLEEP_TIME);
                    } catch (Throwable ignore) {
                        //ignore sleep
                    }
                    logger.log(Level.WARNING, "\n\n [ RejectedExecutionHandler ] slept for " + SLEEP_TIME);
                    // resubmit the task!
                    executor.execute(r);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " Got exc :- ", t);
                }
            }
        });

        // it will be added in 1.6
        // texecutor.allowCoreThreadTimeOut(true);
        texecutor.prestartAllCoreThreads();
        executor = texecutor;
        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                reloadProps();
            }
        });
        reloadProps();
    }

    private static void reloadProps() {

        try {
            MAX_QUEUE_SIZE.set(AppConfig.geti("lia.Monitor.ClientsFarmProxy.ProxyTCPWorker.MAX_QUEUE_SIZE", 15000));
        } catch (Throwable t) {
            MAX_QUEUE_SIZE.set(5000);
        }

        try {
            MAX_QUEUE_THRESHOLD_DELAY.set(AppConfig.getl(
                    "lia.Monitor.ClientsFarmProxy.ProxyTCPWorker.MAX_QUEUE_THRESHOLD_DELAY", 5) * 1000);
        } catch (Throwable t) {
            MAX_QUEUE_THRESHOLD_DELAY.set(5 * 1000);
        }

        FIRST_QUEUE_THRESHOLD_LIMIT.set(MAX_QUEUE_SIZE.get() / 2);
        SECOND_QUEUE_THRESHOLD_LIMIT.set((MAX_QUEUE_SIZE.get() * 3) / 4);
        LAST_QUEUE_THRESHOLD_LIMIT.set((MAX_QUEUE_SIZE.get() * 9) / 10);

        StringBuilder sb = new StringBuilder(1024);
        sb.append("\n\n[ ProxyTCPWorker ] [ (re)loadProps ]\n");
        sb.append("\n MAX_QUEUE_SIZE = ").append(MAX_QUEUE_SIZE);
        sb.append("\n FIRST_THRESHOLD = ").append(FIRST_QUEUE_THRESHOLD_LIMIT);
        sb.append("\n SECOND_THRESHOLD = ").append(SECOND_QUEUE_THRESHOLD_LIMIT);
        sb.append("\n LAST_THRESHOLD = ").append(LAST_QUEUE_THRESHOLD_LIMIT);
        sb.append("\n MAX_QUEUE_THRESHOLD_DELAY = ").append(MAX_QUEUE_THRESHOLD_DELAY.get() / 1000).append(" seconds")
                .append("\n");
        logger.log(Level.CONFIG, sb.toString());
    }

    private static final long INIT_COMM_DEADLINE_NANOS = TimeUnit.MINUTES.toNanos(2);

    private static final long OVERFLOW_DEADLINE_NANOS = TimeUnit.SECONDS.toNanos(5);

    private final class ProxyTCPWorkerQueueVerifierTask implements Runnable {

        long lastTimeOverflow = 0;

        private final long sTime = Utils.nanoNow();

        @Override
        public void run() {
            try {
                final boolean active = ProxyTCPWorker.this.active;
                if (active) {
                    final GenericProxyWorker myLocalComm = myCommRef.get();
                    if (myLocalComm != null) {
                        final int qSize = queue.size();
                        final int firstLimit = FIRST_QUEUE_THRESHOLD_LIMIT.get();

                        // hopefully most of the cases
                        if (qSize < firstLimit) {
                            lastTimeOverflow = 0;
                            return;
                        }

                        final long nanoNow = Utils.nanoNow();
                        final boolean isClient = (myLocalComm instanceof ClientWorker);
                        final int secondLimit = SECOND_QUEUE_THRESHOLD_LIMIT.get();
                        final int lastLimit = LAST_QUEUE_THRESHOLD_LIMIT.get();
                        final int maxQueueSize = MAX_QUEUE_SIZE.get();

                        logger.log(Level.WARNING, " ! [ WARNING ] Message queue warning for: " + myName + " isClient: "
                                + isClient + " qSize: " + qSize + " maxQueueSize " + maxQueueSize + " firstLimit: "
                                + firstLimit + " secondLimit: " + secondLimit + " lastLimit: " + lastLimit);

                        // 1. if the buffer is 50% full send the first message, if it wasn't sent !
                        if ((qSize >= firstLimit) && (qSize < secondLimit)) {
                            logger.log(Level.WARNING, " ! [ WARNING ] Message queue for: " + myName
                                    + " is 50% full. qSize: " + qSize + " MAX_QUEUE_SIZE = " + maxQueueSize);
                            if (active && isClient) {
                                logger.log(Level.WARNING, " ! [ WARNING ] Client : " + myName
                                        + " first notif 50% full queue");
                                final MonMessageClientsProxy msg = new MonMessageClientsProxy("proxyBuffer", null,
                                        Double.valueOf(50), null);
                                queue.add(new ProxyPriorityMsg(msg, ProxyPriorityMsg.ML_PROXY_MESSAGE));
                            } // if
                        } // if

                        // 2. if the buffer is 75% full, send the second buffer full warning message.
                        if ((qSize >= secondLimit) && (qSize < lastLimit)) {
                            logger.log(Level.WARNING, " ! [ WARNING ] Message queue for: " + myName
                                    + " is 75% full. qSize: " + qSize + " MAX_QUEUE_SIZE = " + maxQueueSize);
                            if (active && isClient) {
                                logger.log(Level.WARNING, " ! [ WARNING ] Client : " + myName
                                        + " second notif 75% full queue");
                                final MonMessageClientsProxy msg = new MonMessageClientsProxy("proxyBuffer", null,
                                        Double.valueOf(75), null);
                                queue.add(new ProxyPriorityMsg(msg, ProxyPriorityMsg.ML_PROXY_MESSAGE));
                            } // if
                        } // if

                        // 3. if the buffer is 90% full, send the third message.
                        if ((qSize >= lastLimit) && (qSize < maxQueueSize)) {
                            logger.log(Level.WARNING, " ! [ WARNING ] Message queue for: " + myName
                                    + " is 90% full. qSize: " + qSize + " MAX_QUEUE_SIZE = " + maxQueueSize);
                            if (active && isClient) {
                                logger.log(Level.WARNING, " ! [ WARNING ] Client : " + myName
                                        + " third notif 90% full queue");
                                final MonMessageClientsProxy msg = new MonMessageClientsProxy("proxyBuffer", null,
                                        Double.valueOf(90), null);
                                queue.add(new ProxyPriorityMsg(msg, ProxyPriorityMsg.ML_PROXY_MESSAGE));
                            } // if
                        } // if

                        // 4. if the buffer is 100% full, delete messages and send warning.
                        if (qSize >= maxQueueSize) {
                            logger.log(Level.SEVERE,
                                    " ! [ SEVERE ] Message queue for: " + myName + " is 100% full. qSize: " + qSize
                                            + " MAX_QUEUE_SIZE = " + maxQueueSize + " will stop this connectio in "
                                            + TimeUnit.NANOSECONDS.toMillis(OVERFLOW_DEADLINE_NANOS) + " ms!");
                            if (active && isClient) {
                                logger.log(Level.SEVERE,
                                        " ! [ SEVERE ] Client : " + myName
                                                + " last notif full queue !! ... will stop it in "
                                                + TimeUnit.NANOSECONDS.toMillis(OVERFLOW_DEADLINE_NANOS) + " ms!");
                                final MonMessageClientsProxy msg = new MonMessageClientsProxy("proxyBuffer", null,
                                        Double.valueOf(100), null);
                                queue.add(new ProxyPriorityMsg(msg, ProxyPriorityMsg.ML_PROXY_MESSAGE));
                            } // if

                            if (lastTimeOverflow <= 0) {
                                lastTimeOverflow = nanoNow;
                            }

                            final long dtOverflow = nanoNow - lastTimeOverflow;
                            // if in five seconds the client does not close the connection, force close ...
                            if ((lastTimeOverflow > 0) && (dtOverflow > OVERFLOW_DEADLINE_NANOS)) {
                                logger.log(Level.SEVERE,
                                        " ! [ SEVERE ] FORCED closing connection for: " + myName
                                                + " because is a bad connection for at least last "
                                                + TimeUnit.NANOSECONDS.toMillis(dtOverflow) + " ms.");
                                stopIT();
                                return;
                            }// if
                        } // if
                    } else {
                        final long dt = Utils.nanoNow() - this.sTime;
                        final long dLine = INIT_COMM_DEADLINE_NANOS - dt;
                        if (dLine < 0) {
                            logger.log(Level.INFO, " [ ProxyTCPWorkerQueueVerifierTask ] for " + myName
                                    + " myLocalComm is still null after " + TimeUnit.NANOSECONDS.toSeconds(dt)
                                    + " seconds. deadLine ( " + dLine + " ) < 0. WILL KILL PTW!!!");
                            stopIT();
                        } else {
                            logger.log(Level.INFO, " [ ProxyTCPWorkerQueueVerifierTask ] for " + myName
                                    + " myLocalComm is still null after " + TimeUnit.NANOSECONDS.toSeconds(dt)
                                    + " seconds. deadLine: " + TimeUnit.NANOSECONDS.toMillis(dLine));
                        }
                    }
                } else {
                    logger.log(Level.INFO, " [ ProxyTCPWorkerQueueVerifierTask ] for " + myName + " still alive");
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ ProxyTCPWorkerQueueVerifierTask ] got exception for " + myName, t);
            }
        }
    }

    private class ProxyTCPWorkerTask implements Runnable {

        private volatile boolean isRunning = false;

        @Override
        public void run() {

            final boolean isFiner = logger.isLoggable(Level.FINER);
            if (isFiner) {
                logger.log(Level.FINER, " [ ProxyTCPWorkerTask ] ( " + myName + " ) enters main loop; isRunning = "
                        + isRunning);
            }

            for (;;) {
                boolean isRunning = this.isRunning;
                try {
                    ProxyPriorityMsg ppm = null;
                    synchronized (queue) {
                        ppm = queue.poll();
                        if (isRunning) {
                            isRunning = (ppm != null);
                            if (!isRunning) {
                                break;
                            }
                        } else {
                            break;
                        }
                    }

                    final Object o = ppm.getMessage();
                    if (o instanceof byte[]) {
                        conn.directSend((byte[]) o);
                    } else {
                        conn.sendMsg(o);
                    }

                } catch (Throwable t) {
                    logger.log(Level.WARNING, "\n\n\n That is real BIG ... ProxyTCPWorkerTask for: " + myName
                            + " got exception \n\n", t);
                    isRunning = false;
                    break;
                } finally {
                    this.isRunning = isRunning;
                }
            }// while - true

            if (isFiner) {
                logger.log(Level.FINER, " [ ProxyTCPWorkerTask ] ( " + myName + " ) exits main loop; isRunning = "
                        + isRunning);
            }
        }
    }

    /**
     * Constructor
     * 
     * @param s
     *            - The communication socket
     */
    private ProxyTCPWorker(final Socket s) throws Exception {
        startTimeNano = NTPDate.currentTimeMillis();
        this.conn = tcpConn.newConnection(this, s);
        theRealWorker = new ProxyTCPWorkerTask();
        StringBuilder sb = new StringBuilder("ProxyTcpWorker [ ");
        sb.append(s.getInetAddress()).append(" / ").append(s.getInetAddress().getCanonicalHostName());
        sb.append(":").append(s.getPort());
        sb.append(" :- ").append(s.getLocalPort());
        sb.append(" ]");
        myName = sb.toString();
        futureVerifier = queueCheckerExecutor.scheduleWithFixedDelay(new ProxyTCPWorkerQueueVerifierTask(), 20, 2,
                TimeUnit.SECONDS);
    }

    public static final ProxyTCPWorker newInstance(final Socket s) throws Exception {
        final ProxyTCPWorker ptw = new ProxyTCPWorker(s);
        ptw.conn.startCommunication();
        return ptw;
    }

    public long getStartTimeNano() {
        return startTimeNano;
    }

    public String getName() {
        return myName;
    }

    private void sendMsg(ProxyPriorityMsg ppm) throws Exception {
        if (!active) {
            return;
        }
        synchronized (queue) {
            queue.offer(ppm);
            if (!theRealWorker.isRunning) {
                theRealWorker.isRunning = true;
                executor.execute(theRealWorker);
            }
        }
    }

    public void sendMsg(Object msg) {
        try {
            sendMsg(new ProxyPriorityMsg(msg, ProxyPriorityMsg.DEFAULT_PRIORITY));
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exc sending mesage", t);
        }
    }

    public void sendMsg(Object msg, int priority) throws Exception {
        sendMsg(new ProxyPriorityMsg(msg, priority));
    } // sendMsg

    public void stopIT() {
        notifyConnectionClosed();
    }

    @Override
    public void notifyMessage(final Object message) {

        if (active) {
            if (myComm == null) {
                //try once more
                //cache might be flashed
                myComm = this.myCommRef.get();
            } else {
                myComm.notifyMessage(message);
                return;
            }

            if (myComm != null) {
                myComm.notifyMessage(message);
                return;
            }

            synchronized (bigRemoveMeLock) {
                if (message instanceof MonMessageClientsProxy) {
                    logger.log(Level.INFO, "Got connection from a ML Client [ " + conn.getEndPointAddress() + ":"
                            + conn.getEndPointPort() + " ]");

                    if (!AccessControlManager.checkClientAccess(conn.getEndPointAddress())) {
                        logger.log(Level.SEVERE,
                                " [ ProxyTCPWorker ] The Client InetAddr: " + conn.getEndPointAddress()
                                        + " has failed to pass the access check ... will stop now!");
                        stopIT();
                        return;
                    }

                    this.myComm = ClientsCommunication.addClientWorker(this);
                    this.myCommRef.set(myComm);

                    logger.log(Level.INFO, " Client Worker myComm inited for " + myName);
                    if (myComm == null) {
                        stopIT();
                    } else {
                        if ((message instanceof MonMessageClientsProxy)
                                && (((MonMessageClientsProxy) message).tag != null)
                                && ((MonMessageClientsProxy) message).tag.startsWith("proxy")
                                && (myComm instanceof ClientWorker)) {
                            myComm.notifyMessage(message);
                        } // if
                    }
                } else if (message instanceof monMessage) {
                    final monMessage m = (monMessage) message;
                    if (m.tag.equals(monMessage.ML_SID_TAG)) {
                        logger.log(Level.INFO, myName + " GOT FARM_SID myComm is: " + myComm);

                        if ((m.result != null) && (m.result instanceof ServiceItem)) {

                            final ServiceItem si = (ServiceItem) m.result;
                            final ServiceID sid = si.serviceID;

                            try {

                                if (sid == null) {
                                    logger.log(Level.WARNING, "Got a null SERVICE ID!!! for " + myName
                                            + " ... will stop ptw");
                                    stopIT();
                                    return;
                                }

                                if (myComm == null) {

                                    final Name jiniServiceNameEntry = Utils.getEntry(si, Name.class);
                                    String MLServiceName = null;

                                    // first look for ML Service name in the Jini entries
                                    if (jiniServiceNameEntry == null) {
                                        logger.log(Level.WARNING,
                                                " Jini service name is null for " + conn.getEndPointAddress()
                                                        + " SID: " + sid);
                                    } else {
                                        MLServiceName = jiniServiceNameEntry.name;
                                    }

                                    if (MLServiceName == null) {
                                        final MonaLisaEntry mle = Utils.getEntry(si, MonaLisaEntry.class);
                                        MLServiceName = mle.Name;
                                    }

                                    if (MLServiceName == null) {
                                        logger.log(Level.SEVERE,
                                                "\n\n [ ProxyTCPWoker ] [ SEVERE ] Unable to determine the remote ML Service name ... will stop \n\n");
                                        stopIT();
                                        return;
                                    }
                                    // accessList
                                    if (!AccessControlManager.checkServiceAccess(conn.getEndPointAddress(),
                                            sid.toString(), MLServiceName)) {
                                        logger.log(Level.SEVERE, "\n\n [ ProxyTCPWorker ] The service " + sid
                                                + " InetAddr: " + conn.getEndPointAddress()
                                                + " has failed to pass the access check ... will stop now!\n\n");
                                        stopIT();
                                        return;
                                    }

                                    this.myComm = FarmCommunication.addFarmWorker(sid, si, this);
                                    this.myCommRef.set(myComm);

                                    logger.log(Level.INFO, " Farm Worker myComm inited for " + myName);
                                    final Map<ServiceID, FarmWorker> farmConnections = FarmCommunication.getFarmsHash();

                                    final Lock wLock = FarmCommunication.getLock();

                                    wLock.lock();

                                    try {
                                        if (farmConnections.containsKey(sid)) {// already a conn from Proxy to the farm
                                            FarmWorker oldfw = farmConnections.get(sid);
                                            FarmWorker newfw = (FarmWorker) myComm;

                                            newfw.registeredFilters.putAll(oldfw.registeredFilters);

                                            for (final monMessage msg : newfw.registeredFilters.values()) {
                                                sendMsg(msg);
                                            }

                                            final ConcurrentMap<Integer, PredicatesInfoCache> IDSave = ServiceCommunication
                                                    .getIDSave();

                                            if (IDSave != null) {
                                                for (final PredicatesInfoCache mic : IDSave.values()) {
                                                    if (newfw.id.equals(mic.farmID)) {
                                                        MonMessageClientsProxy mm = (MonMessageClientsProxy) mic.result;
                                                        final monMessage sentMessage = new monMessage(mm.tag, mm.ident,
                                                                mm.result);
                                                        sendMsg(sentMessage);
                                                    }
                                                }
                                            }

                                            FarmCommunication.deleteFarm(sid);
                                        }

                                        farmConnections.put(sid, (FarmWorker) myComm);
                                        fixIP(si, conn.getEndPointAddress());
                                        ServiceCommunication.notifyNewFarm(si);
                                        if (bPrevSend.compareAndSet(false, true)) {
                                            logger.log(Level.INFO, myName + " ... Notif prevMsgs [ " + prevMsgs.size()
                                                    + " ] ");
                                            for (final Object msg : prevMsgs) {
                                                myComm.notifyMessage(msg);
                                            } // if

                                            // GC...
                                            prevMsgs.clear();
                                        } // if

                                    } finally {
                                        wLock.unlock();
                                    } // try - catch - finally

                                    // send notification to clients
                                    try {
                                        final Vector<ServiceItem> services = FarmCommunication.getFarms();
                                        logger.log(Level.INFO, " [ ProxyTCPWorker ] Broadcasting [ " + services.size()
                                                + " ] services for new service " + si + " to the clients");
                                        MonMessageClientsProxy mm = new MonMessageClientsProxy(
                                                monMessage.PROXY_MLSERVICES_TAG, null, services, null);
                                        ClientsCommunication.sendBcastMsg(mm);
                                    } catch (Throwable th) {
                                        logger.log(Level.INFO, "ERROR Send farms to clients .... ", th);
                                    }
                                } else {
                                    logger.log(Level.WARNING, "\n\n ====---->" + myName
                                            + " got a FARM_SID but myComm != null!!!\n\n");
                                    stopIT();
                                }
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, " Got exc ", t);
                            }
                        } else {// should not happen
                            logger.log(Level.WARNING, myName + " got a FARM_SID message without a ServiceID!!");
                            stopIT();
                        }

                        logger.log(Level.INFO, myName + " END FARM_SID myComm is: " + myComm);
                    } else {
                        try {
                            if (bPrevSend.getAndSet(false)) {
                                logger.log(Level.WARNING,
                                        " [ ProtocolException ] Adding to prevMsgs ... but bPrevSend was true ... NOT_OK");
                            }
                            logger.log(Level.WARNING, " [ ProxyTCPWorker ] " + myName + " adding to prevMsgs msg: "
                                    + message);
                            prevMsgs.add(message);
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, " [ ProxyTCPWorker ] " + myName + " Got exc adding to prevMsgs",
                                    t);
                        }
                    }
                } else {
                    logger.log(Level.WARNING,
                            " [ ProxyTCPWorker ] [ notifyMessage ] " + myName + " ...... Got UNK obj", message);
                    return;
                }

                if (myComm != null) {
                    logger.log(Level.INFO, myName + " ... commInit() ");
                    myComm.commInit();
                }
            }
        }// if I'm still active
    }

    private final void fixIP(ServiceItem si, InetAddress ia) {
        SiteInfoEntry sie = Utils.getEntry(si, SiteInfoEntry.class);
        if (sie == null) {
            return;
        }
        String ipad = sie.IPAddress;
        if ((ipad == null) || (ipad.length() == 0)) {
            return;
        }

        InetAddress add = null;
        try {
            add = InetAddress.getByName(ipad);
        } catch (Exception e) {
            add = null;
        } // try - catch

        if (add == null) {
            sie.IPAddress = ia.getHostAddress();
            return;
        }

        String realIP = add.getHostAddress();
        if (realIP.startsWith("10.") || realIP.startsWith("192.168.") || realIP.startsWith("127.")) {
            sie.IPAddress = ia.getHostAddress();
            logger.log(Level.INFO, "\n\n " + myName + " Changing IP in SiteInfo from [ " + realIP + " ] => [ "
                    + sie.IPAddress + " ] \n\n");
        }
    }

    @Override
    public void notifyConnectionClosed() {
        // allow multiple notifyConnectionClosed()
        if (notified.compareAndSet(false, true)) {
            active = false;
            synchronized (queue) {
                queue.clear();
                if (theRealWorker != null) {
                    theRealWorker.isRunning = false;
                }
                queue.clear();
            }
            if (futureVerifier != null) {
                futureVerifier.cancel(false);
            }

            final GenericProxyWorker thisComm = myCommRef.get();
            try {
                if (thisComm != null) {
                    myComm = null;
                    myCommRef.set(null);
                    thisComm.notifyConnectionClosed();
                }
            } catch (Throwable ignore) {
                //ignore
            }

            if (conn != null) {
                conn.close_connection();
            }
        }
    } // notifyConnectionClosed

}
