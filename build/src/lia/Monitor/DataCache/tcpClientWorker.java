/*
 * $Id: tcpClientWorker.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.DataCache;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;

import lia.Monitor.Farm.FarmMonitor;
import lia.Monitor.Filters.LocalMonitorFilter;
import lia.Monitor.JiniSerFarmMon.RegFarmMonitor;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.AppControlMessage;
import lia.Monitor.monitor.DynamicModule;
import lia.Monitor.monitor.ExtResult;
import lia.Monitor.monitor.ExtendedResult;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MonitorClient;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmonMessage;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monMessage;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.monitor.tcpConn;
import lia.Monitor.monitor.tcpConnNotifier;
import lia.util.Utils;
import lia.util.logging.relay.MLLogSender;
import lia.util.ntp.NTPDate;
import lia.util.threads.MonALISAExecutors;

/**
 * 
 * @author Iosif Legrand
 * @author ramiro
 *
 */
public class tcpClientWorker implements MonitorClient, tcpConnNotifier {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(tcpClientWorker.class.getName());

    // define some priorities for messages
    public static final int ML_EMSG = 40; // least

    // important

    public static final int ML_TIME_MESSAGE = 30;

    public static final int ML_VERSION_MESSAGE = 30;

    public static final int ML_RESULT_MESSAGE = 20;

    public static final int ML_CONFIG_MESSAGE = 10;

    public static final int ML_AGENT_MESSAGE = 0;

    // THIS MUST REMAIN THE MOST IMPORTANT!!!
    public static final int ML_SID_MESSAGE = -1000;

    private static int TCPW_REZ_LIMIT = 10000;

    /** debugging */
    private final String myName;

    protected final String myKey;

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("tcpClientWorker [myName=").append(myName).append(", myKey=").append(myKey).append("]");
        return builder.toString();
    }

    protected final tcpConn conn;

    final Cache cache;

    final tcpServer server;

    /**
     * Key: Integer ( predicate ID ); Value: monPredicate
     */
    private final Map<Integer, monPredicate> predicatesMap = new ConcurrentHashMap<Integer, monPredicate>();

    int no_of_messages;

    private final AtomicBoolean alive = new AtomicBoolean(true);

    public InetAddress endPointAddress;

    private final PrioritySender ps;

    private static final ScheduledExecutorService ses;

    private static final String SERVICE_NAME = System.getProperty("MonALISA_ServiceName");

    static {
        reloadConfig();
        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                reloadConfig();
            }
        });

        ses = MonALISAExecutors.getMLNetworkExecutor();
    }

    private final ScheduledFuture<?> mlTimeFutureTask;

    private final ScheduledFuture<?> mlVersionFutureTask;

    private final ScheduledFuture<?> checkOldPredicatesFutureTask;

    private tcpClientWorker(Cache cache, tcpServer server, Socket client_sock, String key) throws Exception {

        try {
            myKey = key;
            myName = "[ tcpClientWorker ] [ " + key + " ] ";

            this.cache = cache;
            this.server = server;

            if (client_sock == null) {
                throw new NullPointerException(myName + ": The socket cannot be null");
            }

            this.endPointAddress = client_sock.getInetAddress();

            no_of_messages = 0;

            try {
                conn = tcpConn.newConnection(this, client_sock);
            } catch (Throwable t) {
                logger.log(Level.WARNING, myName + ": error instantiating tcpConn", t);
                close_connection();
                throw new Exception(t);
            }

            mlTimeFutureTask = ses.scheduleWithFixedDelay(new Runnable() {

                @Override
                public void run() {
                    try {
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, myName + " sendTime()");
                        }
                        if ((conn == null) || !conn.isConnected()) {
                            close_connection();
                            return;
                        }
                        sendTime();
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, myName + " [ HANDLED ] Exc sending Time", t);
                    }
                }
            }, 10 + Math.round(Math.random() * 5), 30 + Math.round(Math.random() * 10), TimeUnit.SECONDS);

            mlVersionFutureTask = ses.scheduleWithFixedDelay(new Runnable() {

                @Override
                public void run() {
                    try {
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, myName + " sendMLVersion()");
                        }
                        if ((conn == null) || !conn.isConnected()) {
                            close_connection();
                            return;
                        }
                        sendMLVersion();
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, myName + " [ HANDLED ] Exc sending MLVersion", t);
                    }
                }
            }, 10 + Math.round(Math.random() * 5), 60 + Math.round(Math.random() * 20), TimeUnit.SECONDS);

            checkOldPredicatesFutureTask = ses.scheduleWithFixedDelay(new Runnable() {

                @Override
                public void run() {
                    try {
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, myName + " RemoveOldPredicates(). CurrentStatus: "
                                    + getCurrentStats());
                        }
                        if ((conn == null) || !conn.isConnected()) {
                            close_connection();
                            return;
                        }
                        RemoveOldPredicates();
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, myName + " [ HANDLED ] Exc checkOldPredicates", t);
                    }
                }
            }, 10 + Math.round(Math.random() * 5), 60 + Math.round(Math.random() * 20), TimeUnit.SECONDS);

            ps = new PrioritySender();

        } catch (Throwable t) {
            close_connection();
            throw new Exception(t);
        }
    }

    public static final tcpClientWorker newInstance(Cache cache, tcpServer server, Socket client_sock, String key)
            throws Exception {
        final tcpClientWorker tcw = new tcpClientWorker(cache, server, client_sock, key);
        tcw.ps.start();
        tcw.conn.startCommunication();
        return tcw;
    }

    @Override
    public void notifyMessage(Object o) {
        if (alive.get()) {
            process_input(o);
        } else {
            logger.log(Level.WARNING, myName + " [ HANDLED ] connection already closed ...");
            close_connection();
        }
    }

    @Override
    public void notifyConnectionClosed() {
        close_connection();
        try {
            ps.interrupt();
        } catch (Throwable t) {
            logger.log(Level.WARNING, myName + " Got exc interrupting PSender", t);
        }
    }

    public String getKey() {
        return myKey;
    }

    public void close_connection() {
        if (alive.compareAndSet(true, false)) {
            try {
                if (conn != null) {
                    conn.close_connection();
                }
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, myName + " got exception closing the connection. Cause: ", t);
                }
            }

            try {
                if (this.cache != null) {
                    cache.funRegister(this);
                }
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, myName + " got exception trying to unregister from cache. Cause: ", t);
                }
            }

            try {
                if ((myKey != null) && (server != null)) {
                    server.disconnectClient(myKey);
                }
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, myName + " got exception trying to unregister from server. Cause: ", t);
                }
            }

            try {
                finishIt();
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, myName + " got exception running the cleanup. Cause: ", t);
                }
            }
        }
    }

    public void WriteObject(Object o, int priority) {
        final boolean logSent = logger.isLoggable(Level.FINEST);

        if (logSent) {
            logger.log(Level.FINEST, " [ tcpClientWorker ] [ WriteObject ] " + myKey + " ADDED TO QUEUE... " + o);
        }

        ps.add(new PQJob(o, priority));
    }

    private String getCurrentStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n########## Current registered predicates for [ ").append(myKey).append(" ] ##########\n");
        for (final monPredicate p : predicatesMap.values()) {
            sb.append(" ----> ").append(p).append("\n");
        }
        sb.append("\n There are " + ps.theQueue.size() + " msgs to be send ... ");
        sb.append("\n########################################################################\n");
        return sb.toString();
    }

    // The Time & Uptime
    private void sendTime() {
        WriteObject(
                new monMessage(monMessage.ML_TIME_TAG, null, cache.getLocalTime() + "&Uptime:"
                        + RegFarmMonitor.getServiceUpTime()), ML_TIME_MESSAGE);
    }

    // ML Version
    private void sendMLVersion() {
        WriteObject(new monMessage(monMessage.ML_VERSION_TAG, null, FarmMonitor.MonaLisa_version), ML_VERSION_MESSAGE);
    }

    private void process_input(Object obj) {
        if (!(obj instanceof monMessage)) {
            logger.log(Level.WARNING, myName + ": Received an unknown object  ignore it !!", new Object[] { obj });
            return;
        }
        monMessage msg = (monMessage) obj;

        if (msg.tag == null) {
            logger.log(Level.WARNING, " [ tcpClientWorker ] [ process_input ] got null msg.tag: " + msg
                    + " \n Ignoring message ....");
            return;
        }

        if (msg.tag.startsWith(monMessage.ML_AGENT_TAG)) {
            try {
                cache.agentsMsg(msg);
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ tcpClientWorker ] [ process_input ] exception processing agent message: "
                        + msg, t);
            }
            return;
        }

        // AppControl message?
        if (msg.tag.startsWith(monMessage.ML_APP_CTRL_TAG)) {
            if (cache.appControlDispatcher != null) {
                try {
                    cache.appControlDispatcher.messageReceived(msg, this);
                } catch (Throwable t) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(myName).append(" [ process_input ] [ ML_APP_CTRL_TAG ] got exception for msg: ")
                            .append(msg).append(" Cause: ").append(Utils.getStackTrace(t));
                    final monMessage monMessage = new monMessage(AppControlMessage.APP_CONTROL_MSG_ERR, msg.ident,
                            sb.toString());
                    WriteObject(monMessage, ML_AGENT_MESSAGE);
                }
            } else {
                String errMsg = null;
                if (cache.appControlDispatcherNullCause == null) {
                    errMsg = myName
                            + " [ process_input ] both cache.appControlDispatcher and cache.appControlDispatcherNullCause are NULL!";
                } else {
                    errMsg = myName + " AppCtrlDispatcher is null. Cause when starting the dispatcher: "
                            + cache.appControlDispatcherNullCause;
                }

                final monMessage monMessage = new monMessage(AppControlMessage.APP_CONTROL_MSG_ERR, msg.ident, errMsg);
                WriteObject(monMessage, ML_AGENT_MESSAGE);
            }

            return;
        }

        if (msg.tag.equals(ProxyWorker.PMS_KEY)) {
            try {
                cache.notifyPMS(msg.result);
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST,
                            myName + " [ process_input ] exception processing PMS_KEY message: " + msg, t);
                }
            }
            return;
        }

        if (msg.tag.equals(ProxyWorker.MLLOG_KEY)) {
            try {
                MLLogSender.getInstance().remoteNotify(msg);
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, myName + " [ process_input ] exception processing MLLOG_KEY message: "
                            + msg, t);
                }
            }
            return;
        }

        if (msg.tag.equals(monMessage.PREDICATE_REGISTER_TAG)) {
            try {
                addPredicate((monPredicate) msg.result);
            } catch (Throwable t) {
                logger.log(Level.WARNING, myName + " [ process_input ] exception adding predicate msg: " + msg
                        + " Cause:", t);
            }
            return;
        }

        if (msg.tag.equals(monMessage.PREDICATE_UNREGISTER_TAG)) {
            try {
                deletePredicate((Integer) msg.ident);
            } catch (Throwable t) {
                logger.log(Level.WARNING, myName + " [ process_input ] exception deleting predicate msg: " + msg
                        + " Cause:", t);
            }
            return;
        }

        if (msg.tag.equals(monMessage.FILTER_REGISTER_TAG)) {
            try {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, myName + " Try to register with filter " + msg.ident);
                }
                cache.Register((MonitorClient) this, (String) msg.ident);
            } catch (Throwable t) {
                logger.log(Level.WARNING, myName + ": Failed to register the filter ", t);
            }
            return;
        }

    }

    private void finishIt() {
        try {
            if (mlTimeFutureTask != null) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, myName + " canceling mlTimeFutureTask ...");
                }
                mlTimeFutureTask.cancel(true);
            } else {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, myName + " canceling mlTimeFutureTask ... but the task is null");
                }
            }
        } catch (Throwable ign) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, myName + " canceling mlTimeFutureTask ... got exc: ", ign);
            }
        }

        try {
            if (mlVersionFutureTask != null) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, myName + " canceling mlVersionFutureTask ...");
                }
                mlVersionFutureTask.cancel(true);
            } else {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, myName + " canceling mlVersionFutureTask ... but the task is null");
                }
            }
        } catch (Throwable ign) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, myName + " canceling mlVersionFutureTask ... got exc: ", ign);
            }
        }

        try {
            if (checkOldPredicatesFutureTask != null) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, myName + " canceling checkOldPredicatesFutureTask ...");
                }
                checkOldPredicatesFutureTask.cancel(true);
            } else {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, myName + " canceling checkOldPredicatesFutureTask ... but the task is null");
                }
            }
        } catch (Throwable ign) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, myName + " canceling checkOldPredicatesFutureTask ... got exc: ", ign);
            }
        }

        for (final Iterator<monPredicate> itPred = predicatesMap.values().iterator(); itPred.hasNext();) {
            final monPredicate pred = itPred.next();
            try {
                DynamicModule.unregisterPredicate(pred);
            } catch (Throwable t) {
                logger.log(Level.WARNING, myName + " [ HANDLED ] got exception removing predicate " + pred
                        + " from dynamic module. Cause: ", t);
            }
            itPred.remove();
        }

        if (ps != null) {
            try {
                ps.interrupt();
            } catch (Throwable t) {
                logger.log(Level.WARNING, myName + " got exception interrupting the priority sender thread", t);
            }
        }
    }

    public void addNewResult(Object o) {
        for (final monPredicate p : predicatesMap.values()) {
            // it cannot be a vector anymore ... but I never know ....
            final Vector<Object> rez = new Vector<Object>();

            if (o instanceof Result) {
                Result rr = (Result) o;
                Result re = DataSelect.matchResult(rr, p);

                if (re != null) {
                    rez.add(re);
                }
            } else if (o instanceof eResult) {
                eResult rr = (eResult) o;
                eResult re = DataSelect.matchResult(rr, p);

                if (re != null) {
                    rez.add(re);
                }
            } else if (o instanceof ExtResult) {
                Result rr = ((ExtResult) o).getResult();
                Result re = DataSelect.matchResult(rr, p);
                if (re != null) {
                    rez.add(re);
                }
            }

            if (rez.size() > 0) {
                WriteObject(new monMessage(monMessage.ML_RESULT_TAG, Integer.valueOf(p.id), rez), ML_RESULT_MESSAGE);
            }
        }
    }

    private void addPredicate(final monPredicate p) {

        if ((p.Cluster != null) && p.Cluster.equals("PMS_ML_STATUS") && (p.Node != null)
                && p.Node.equals("PMS_ML_STATUS")) {

            if ((p.tmax > 0) && (p.tmin == p.tmax)) {
                LocalMonitorFilter.setLoadThreshold(p.tmin);
            }

            boolean notif = LocalMonitorFilter.forceSendMail.compareAndSet(false, true);
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST,
                        "\n PMS_ML_STATUS recv = " + notif + " NewThreshold " + LocalMonitorFilter.getLoadThreshold());
            }
            return;
        }

        if (!DataSelect.matchString(p.Farm, SERVICE_NAME)) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER,
                        myName + "[ HANDLED ] [ addPredicate ] IGNORING REMOTE predicate: " + p.toString());
            }
            return;
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, myName + " REGISTER predicate: " + p.toString());
        }

        if ((p.tmax < 0) || ((p.tmax > 0) && (p.tmin <= 0))) {
            //RT predicate
            predicatesMap.put(Integer.valueOf(p.id), p);
            try {
                DynamicModule.registerPredicate(p);
            } catch (Throwable t) {
                logger.log(Level.WARNING, myName
                        + " [ preProcess ] got exception registering for dynamicModule. Cause:", t);
            }
        }

        ses.submit(new QueryWorkerTask(p, this));
    }

    private static class QueryWorkerTask implements Runnable {

        final monPredicate pred;

        final tcpClientWorker tcw;

        public QueryWorkerTask(monPredicate pred, tcpClientWorker tcw) {
            this.pred = pred;
            this.tcw = tcw;
        }

        @Override
        public void run() {

            final String iName = "QueryWorkerTask for: " + tcw.myKey + " pred: " + pred;

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, iName + " STARTED! ");
            }

            try {

                Vector<Object> rez = tcw.cache.select(pred);

                if ((rez == null) || (rez.size() == 0)) {
                    monMessage msg = new monMessage(monMessage.ML_RESULT_TAG, Integer.valueOf(pred.id), (Result) null);
                    tcw.WriteObject(msg, ML_RESULT_MESSAGE);
                    return;
                }

                Vector<Object> vTemp = new Vector<Object>();
                if ((TCPW_REZ_LIMIT > 0) && (rez.size() > TCPW_REZ_LIMIT)) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, iName + " Limiting result to " + TCPW_REZ_LIMIT + " from " + rez.size());
                    }
                    for (int ic = rez.size() - TCPW_REZ_LIMIT; ic < rez.size(); ic++) {
                        vTemp.add(rez.get(ic));
                    }
                } else {
                    vTemp.addAll(rez);
                }
                rez.clear();

                for (int ti = 0; ti < vTemp.size(); ti++) {
                    final Object o = vTemp.elementAt(ti);
                    if (o instanceof ExtendedResult) {
                        ExtendedResult er = (ExtendedResult) o;
                        Result r = new Result();
                        r.ClusterName = er.ClusterName;
                        r.FarmName = er.FarmName;
                        r.time = er.time;
                        r.NodeName = er.NodeName;
                        if ((er.param != null) && (er.param.length > 0)) {
                            r.param = new double[er.param.length];
                            System.arraycopy(er.param, 0, r.param, 0, er.param.length);
                        }
                        if ((er.param_name != null) && (er.param_name.length > 0)) {
                            r.param_name = new String[er.param_name.length];
                            System.arraycopy(er.param_name, 0, r.param_name, 0, er.param_name.length);
                        }
                        rez.add(r);
                    } else if ((o instanceof Result) || (o instanceof eResult)) {
                        rez.add(o);
                    }
                }

                monMessage msg = null;
                if (rez.size() > 20) {
                    try {
                        final Deflater compresser = new Deflater();

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(baos);

                        oos.writeObject(rez);
                        oos.flush();
                        baos.flush();
                        final byte[] buff = baos.toByteArray();

                        compresser.reset();
                        compresser.setInput(buff);
                        compresser.finish();

                        byte[] output = new byte[buff.length];// in the worst case...no compression
                        byte[] outc = new byte[compresser.deflate(output)];

                        System.arraycopy(output, 0, outc, 0, outc.length);

                        cmonMessage cm = new cmonMessage(buff.length, outc);
                        msg = new monMessage(monMessage.ML_RESULT_TAG, Integer.valueOf(pred.id), cm);

                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, iName + ": Comp " + rez.size() + " uc: " + buff.length + " c: "
                                    + outc.length);
                        }
                    } catch (Throwable ex) {
                        logger.log(Level.WARNING, iName + " . Got Exception in cache.select()/compress/notify", ex);
                    }
                }// if()

                if (msg == null) {// compression error or the size of result not big enough
                    msg = new monMessage(monMessage.ML_RESULT_TAG, Integer.valueOf(pred.id), rez);
                }

                tcw.WriteObject(msg, ML_RESULT_MESSAGE);
            } catch (Throwable t) {
                logger.log(Level.WARNING, iName + " got Exc main loop ", t);
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, iName + " ] finishes! ");
            }
        }
    }

    final private static AtomicLong pqJobSequenceGenerator = new AtomicLong();

    private static class PQJob implements Comparable<PQJob> {

        private final long seqNr;

        private final int priority;

        private final Object msg;

        PQJob(Object msg, int priority) {
            this.seqNr = pqJobSequenceGenerator.getAndIncrement();
            this.msg = msg;
            this.priority = priority;
        }

        @Override
        public int hashCode() {
            return (int) (seqNr ^ (seqNr >>> 32));
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }

            if (o instanceof PQJob) {
                final PQJob otherJob = (PQJob) o;
                return (this.seqNr == otherJob.seqNr);
            }

            return false;
        }

        @Override
        public int compareTo(PQJob other) {

            if (this == other) {
                return 0;
            }

            int op = other.priority;
            if (op < this.priority) {
                return -1;
            }
            if (op > this.priority) {
                return 1;
            }

            // in case of tie, order by seqNr
            if (this.seqNr < other.seqNr) {
                return -1;
            }

            return 1;
        }
    }

    private class PrioritySender extends Thread {

        final PriorityBlockingQueue<PQJob> theQueue;

        PrioritySender() {
            super(" ( ML ) PQSender [ " + myKey + " ] ");
            theQueue = new PriorityBlockingQueue<PQJob>();
        }

        void add(PQJob job) {
            theQueue.offer(job);
        }

        @Override
        public void run() {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " PQSender [ " + myKey + " ] STARTED! ");
            }

            while (alive.get()) {
                try {
                    final PQJob j = theQueue.take();
                    final Object m = j.msg;

                    final boolean logSent = logger.isLoggable(Level.FINEST);

                    if (logSent) {
                        logger.log(Level.FINEST, " [ tcpClientWorker ] [ PrioritySender ] " + myKey + " SENDING... "
                                + j.msg);
                    }
                    if (m instanceof byte[]) {
                        conn.directSend((byte[]) m);
                    } else {
                        conn.sendMsg(m);
                    }

                    if (logSent) {
                        logger.log(Level.FINEST, " [ tcpClientWorker ] [ PrioritySender ] " + myKey + " SENT! " + j.msg);
                    }
                } catch (InterruptedException ie) {// maybe we should stop
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, " PQSender for " + myKey + " was interrupted");
                    }
                    Thread.interrupted();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "PQSender got exc main loop", t);
                }
            }// while
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " PQSender [ " + myKey + " ] finishes! ");
            }

        }
    }

    private void deletePredicate(final Integer key) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, myKey + " GOT REMOTE remove predicate for key: " + key + "\n");
        }
        final monPredicate p = predicatesMap.remove(key);

        if (p == null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, myName
                        + " [ CheckRemovePredicates ] [ HANDLED ] Already removed predicate with ID: " + key);
            }
        } else {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, myName + " [ CheckRemovePredicates ] UNREGISTER: predicate: ( " + p
                        + " ) with id: " + key);
            }

            try {
                DynamicModule.unregisterPredicate(p);
            } catch (Throwable t) {
                logger.log(Level.WARNING, myName + " [ HANDLED ] [ CheckRemovePredicates ] UNREGISTER: predicate: ( "
                        + p + " ) with id: " + key + " got exception removing from DynamicModule. Cause:", t);
            }
        }
    }

    private void RemoveOldPredicates() { // the ones with tmax < now()

        for (final Iterator<Map.Entry<Integer, monPredicate>> itPred = predicatesMap.entrySet().iterator(); itPred
                .hasNext();) {
            final Map.Entry<Integer, monPredicate> entry = itPred.next();
            final monPredicate p = entry.getValue();
            if ((p.tmax > 0) && (NTPDate.currentTimeMillis() > p.tmax)) {
                itPred.remove();

                try {
                    DynamicModule.unregisterPredicate(p);
                } catch (Throwable t) {
                    logger.log(Level.WARNING,
                            " [ tcpClientWorker ] [ RemoveOldPredicates ] got exception removing DynamicModule for predicate: "
                                    + p);
                }

                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "\n" + myName + ": ( RemoveOldPredicates ) UNREGISTER: " + p + "\n");
                }
            }
        }
    }

    /**
     * @throws java.rmi.RemoteException  
     */
    @Override
    public void notifyResult(Object res, int pid) throws java.rmi.RemoteException {
        logger.log(Level.WARNING, myName + ": notifyResult() SHOULD NEVER by used !!! ");
    }

    /**
     * @throws java.rmi.RemoteException  
     */
    @Override
    public void newConfig(MFarm f) throws java.rmi.RemoteException {
        logger.log(Level.WARNING, myName + ": newConfig() SHOULD NEVER by used !!! ");
    }

    /**
     * @throws java.rmi.RemoteException  
     */
    @Override
    public void notifyResult(Object res, String filter) throws java.rmi.RemoteException {
        WriteObject(new monMessage(monMessage.ML_RESULT_TAG, filter, res), ML_RESULT_MESSAGE);
    }

    private static final void reloadConfig() {
        try {
            TCPW_REZ_LIMIT = AppConfig.geti("lia.Monitor.DataCache.tcpClientWorker.TCPW_REZ_LIMIT", 10000);
        } catch (Throwable t) {
            TCPW_REZ_LIMIT = 10000;
        }

        final String sLevel = AppConfig.getProperty("lia.Monitor.DataCache.tcpClientWorker.level");
        Level loggingLevel = null;
        if (sLevel != null) {
            try {
                loggingLevel = Level.parse(sLevel);
            } catch (Throwable t) {
                loggingLevel = null;
            }

            logger.setLevel(loggingLevel);
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [ tcpClientWorker ] reloadedConf. Logging level: " + loggingLevel);
        }
    }

    public final boolean isConnected() {
        return alive.get();
    }

}

/*
 * $Id: tcpClientWorker.java 7419 2013-10-16 12:56:15Z ramiro $
 */
