/*
 * $Id: MLLogSender.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.util.logging.relay;

import java.util.Date;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.DataCache.ProxyWorker;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.monMessage;
import lia.util.Utils;
import lia.util.logging.comm.MLLogMsg;
import lia.util.logging.comm.ProxyLogMessage;
import lia.util.threads.MonALISAExecutors;

/**
 * 
 * @author ramiro
 */
public class MLLogSender implements Runnable {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(MLLogSender.class.getName());

    private static volatile int MAX_LOG_MSGS = 150; // we are not GMAIL or Yahoo! or mail.cs.pub.ro :) ?

    private static volatile long RETRY_AFTER = TimeUnit.MINUTES.toNanos(1);

    private static volatile long RETRY_AFTER_FACTOR = 2;// Real Retry = RETRY_AFTER * RETRY_AFTER_FACTOR ( but max 1h )

    private static volatile int MAX_RETRIES = 10;// for sure it is a real problem if this value is reached ...

    private static volatile boolean DELIVER_INTERNAL_NOTIF = true;

    private static final long ONE_HOUR = 60 * 60 * 1000L;

    private static MLLogSender me = null;

    // The Mail Queue
    private static final DelayQueue<MLLogMsgEntry> logRecQueue = new DelayQueue<MLLogMsgEntry>();

    private static final ConcurrentHashMap<Long, MLLogMsgEntry> waitingNotif = new ConcurrentHashMap<Long, MLLogMsgEntry>();

    private static final AtomicReference<ProxyWorker> proxyWorkerPointer = new AtomicReference<ProxyWorker>(null);

    private static StatusThread statusThread;

    private final AtomicLong totalSent = new AtomicLong(0);

    static {
        reloadCfgParams();

        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                reloadCfgParams();
            }

        });
    }

    private class MLLogMsgEntry implements Delayed {

        MLLogMsg msg;

        long entryTime;

        long nextRetry;// in case of failure

        long id;// It seems redundant from emsg, but we need it to "safe" remove a notified EMsg from proxy ;)

        int retries;

        private MLLogMsgEntry(MLLogMsg msg, long entryTime, long nextRetry, int retries) {
            this.msg = msg;
            this.entryTime = entryTime;
            this.nextRetry = nextRetry;
            this.id = msg.id;
            this.retries = retries;
        }

        @Override
        public int compareTo(Delayed o) {
            if (o.equals(this)) {
                return 0;
            }
            MLLogMsgEntry msg = (MLLogMsgEntry) o;
            if (msg.nextRetry > nextRetry) {
                return -1;
            }
            if (msg.nextRetry < nextRetry) {
                return 1;
            }

            if (id < msg.id) {
                return -1;
            }
            if (id > msg.id) {
                return 1;
            }
            return 0;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(nextRetry - Utils.nanoNow(), TimeUnit.NANOSECONDS);
        }

        @Override
        public boolean equals(Object o) {
            return ((MLLogMsgEntry) o).id == id;
        }

        @Override
        public int hashCode() {
            // from Long.hascode()
            return (int) (id ^ (id >>> 32));
        }
    }

    private static final class StatusThread extends Thread {

        AtomicBoolean alive;

        long sleepTime;

        StatusThread(long sleepTime) {
            super(" (ML) MLLogS Status Thread ");
            alive = new AtomicBoolean(true);
            this.sleepTime = sleepTime;
        }

        void stopIt() {
            alive.set(false);
            StatusThread.this.interrupt();
        }

        @Override
        public void run() {
            logger.log(Level.INFO, " [ MLLogS ] [ StatusThread ] started ...");
            while (alive.get()) {
                try {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (Throwable ignoreT) {
                    }
                    logger.log(Level.INFO, getStatus());
                } catch (Throwable t) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, " Got ex ", t);
                    }
                }
            }// while

            logger.log(Level.INFO, " [ MLLogS ] [ StatusThread ] finishes ...");
        }// run()
    }

    // only from this package
    public static final void setPW(ProxyWorker pw) {
        proxyWorkerPointer.set(pw);
    }

    private MLLogSender() {
    }

    public void sendMessage(MLLogMsg msg) throws Exception {
        if (!DELIVER_INTERNAL_NOTIF) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, " Should notif MLLogMsg but DELIVER_INTERNAL_NOTIF == false");
            }
            return;
        }

        if (msg == null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "Got null MLLogMsg ... Ignoring it");
            }
            return;
        }

        // TODO - remove older emsgs ...
        if (logRecQueue.size() > MAX_LOG_MSGS) {
            // That's life ... sometimes you loose
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ MLLogS ] Too many notif info in the queue; Dropping notif info ... \n"
                        + msg);
            }
            return;
        }

        long nanoNow = Utils.nanoNow();
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ MLLogS ] MLLogMsg " + msg + " enqueued ... ");
        }
        logRecQueue.offer(new MLLogMsgEntry(msg, nanoNow, nanoNow, 0));
    }

    public static synchronized MLLogSender getInstance() {
        if (me == null) {
            me = new MLLogSender();
            MonALISAExecutors.getMLHelperExecutor().scheduleWithFixedDelay(me, 100 + Math.round(Math.random() * 10),
                    100 + Math.round(Math.random() * 10), TimeUnit.SECONDS);
        }
        return me;
    }

    public void remoteNotify(monMessage msg) {
        try {
            if (msg.result instanceof byte[]) {
                ProxyLogMessage plm = (ProxyLogMessage) Utils.readObject((byte[]) msg.result);
                if (plm.props != null) {
                    final Properties newRemoteProps = plm.props;
                    new Thread(" ( ML ) Remote prop msg notifier ") {

                        @Override
                        public void run() {
                            logger.log(Level.INFO, " RemoteProps notified");
                            AppConfig.setRemoteProps(newRemoteProps);
                        }
                    }.start();
                } else {
                    notifyDelivered(Long.valueOf(plm.ackID));
                }
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Got ex ", t);
            }
        }
    }

    public static final String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n ************** MLLogS Status *************** \n");
        sb.append("\n ---------- mq.size = ").append(logRecQueue.size()).append(" ------------- ");
        if ((logRecQueue.size() > 0) && logger.isLoggable(Level.FINEST)) {
            for (MLLogMsgEntry mm : logRecQueue) {
                sb.append("\n ---> [ ").append(new Date(mm.entryTime)).append(" <> ").append(new Date(mm.nextRetry))
                        .append("] <---- \n");
                sb.append(mm.msg).append("\n");
            }
        }
        sb.append("\n ---------- notif.size = ").append(waitingNotif.size()).append(" ------------- ");
        if ((waitingNotif.size() > 0) && logger.isLoggable(Level.FINEST)) {
            for (MLLogMsgEntry mms : waitingNotif.values()) {
                sb.append("\n ---> [ ").append(new Date(mms.entryTime)).append(" <> ").append(new Date(mms.nextRetry))
                        .append("] <---- \n");
                sb.append("\n").append(mms.msg);
            }
        }
        sb.append("\n************** END MLLogS Status *************** \n");
        return sb.toString();
    }

    private void deliverMsgs(ProxyWorker pw) {
        // will block until a new EMsg received
        MLLogMsgEntry eme = null;
        try {
            eme = logRecQueue.poll(1, TimeUnit.MINUTES);
        } catch (Exception ie) { // this will NEVER ?! happen
            logger.log(Level.WARNING, " [ MLLogS ] Got ex", ie);
        }

        if (eme != null) {
            // resch in case of failure
            // Not using the same EMsgEntry to not mess with compareTo()
            // TODO
            if (eme.msg.reqNotif) {
                if (eme.retries < MAX_RETRIES) {
                    MLLogMsgEntry resch = null;
                    long nanoNow = Utils.nanoNow();
                    long queueWTime = nanoNow - eme.entryTime;
                    long nextWait = RETRY_AFTER;
                    if (queueWTime >= RETRY_AFTER) {
                        nextWait = queueWTime * RETRY_AFTER_FACTOR;
                        if (nextWait > ONE_HOUR) {
                            nextWait = ONE_HOUR;
                        }
                    }

                    resch = new MLLogMsgEntry(eme.msg, eme.entryTime, nanoNow + nextWait, eme.retries + 1);

                    logRecQueue.offer(resch);
                    waitingNotif.put(Long.valueOf(resch.id), resch);
                } else {
                    // Just to make sure that we do not have leaks
                    waitingNotif.remove(Long.valueOf(eme.id));
                }
            }

            // now, after we have rescheduled, if any, the retry in case of a failure ( hope not :) )
            // try to send it
            pw.sendMLLog(eme.msg);
            totalSent.incrementAndGet();
        }
    }

    public final long getTotalSentMsgs() {
        return totalSent.get();
    }

    public void notifyDelivered(Long id) {
        try {
            MLLogMsgEntry removed = waitingNotif.remove(id);
            if ((removed != null) && logRecQueue.remove(removed)) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " [ MLLogS ] REMOVED from my cache " + id + " \n " + removed.msg);
                }
            } else {// maybe it was the 51st retry :)
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " [ MLLogS ] [ HANDLED ] No such id in my cache " + id);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ MLLogS ] [ HANDLED ] Got ex removing ID " + id, t);
        }
    }

    private static void reloadCfgParams() {

        DELIVER_INTERNAL_NOTIF = true;

        try {
            String st = AppConfig.getProperty("lia.util.logging.relay.MLLogS.DELIVER_INTERNAL_NOTIF", "true");
            if (st != null) {
                DELIVER_INTERNAL_NOTIF = Boolean.valueOf(st.trim()).booleanValue();
            } else {
                DELIVER_INTERNAL_NOTIF = true;
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        " [ MLLogS ] Got ex parsing lia.util.logging.relay.MLLogS.DELIVER_INTERNAL_NOTIF", t);
            }
            DELIVER_INTERNAL_NOTIF = true;
        }

        try {
            MAX_LOG_MSGS = Integer.valueOf(AppConfig.getProperty("lia.util.logging.relay.MLLogS.MAX_LOG_MSGS", "150"))
                    .intValue();
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ MLLogS ] Got ex parsing lia.util.logging.relay.MLLogS.MAX_LOG_MSGS", t);
            }
            MAX_LOG_MSGS = 150;
        }

        try {
            RETRY_AFTER = Long.valueOf(AppConfig.getProperty("lia.util.logging.relay.MLLogS.RETRY_AFTER", "300"))
                    .longValue() * 1000;
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ MLLogS ] Got ex parsing lia.util.logging.relay.MLLogS.RETRY_AFTER", t);
            }
            RETRY_AFTER = 5 * 60 * 1000;
        }

        try {
            RETRY_AFTER_FACTOR = Long.valueOf(AppConfig.getProperty("lia.util.logging.relay.MLLogS.RETRY_AFTER", "2"))
                    .longValue();
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ MLLogS ] Got ex parsing lia.util.logging.relay.MLLogS.RETRY_AFTER_FACTOR", t);
            }
            RETRY_AFTER_FACTOR = 2;
        }

        try {
            MAX_RETRIES = Integer.valueOf(AppConfig.getProperty("lia.util.logging.relay.MLLogS.MAX_RETRIES", "50"))
                    .intValue();
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ MLLogS ] Got ex parsing lia.util.logging.relay.MLLogS.MAX_RETRIES", t);
            }
            MAX_RETRIES = 50;
        }

        if (logger.isLoggable(Level.FINER)) {
            if (statusThread == null) {
                statusThread = new StatusThread(10 * 1000);
                statusThread.start();
            }
        } else {
            if (statusThread != null) {
                statusThread.stopIt();
                statusThread = null;
            }
        }

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ MLLogS ] [ reloadCfgParams ]  " + " DELIVER_INTERNAL_NOTIF = "
                    + DELIVER_INTERNAL_NOTIF + " MAX_LOG_MSGS = " + MAX_LOG_MSGS + " RETRY_AFTER = " + RETRY_AFTER
                    + " RETRY_AFTER_FACTOR = " + RETRY_AFTER_FACTOR + " MAX_RETRIES = " + MAX_RETRIES);
        }
    }

    /**
     * let's dance ... or let's loop :)
     */
    @Override
    public void run() {
        try {
            final ProxyWorker pw = proxyWorkerPointer.get();
            if (pw == null) {
                return;
            }
            deliverMsgs(pw);
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " Got Exc sending msg", t);
            }

        } finally {
            // Whatever happens - DO NOT LOOP!
            try {
                Thread.sleep(20 * 1000);
            } catch (Throwable t) {
                //ignore
            }
        }
    }

    public int getMLLogSSize() {
        return logRecQueue.size();
    }

    public int getMLLogSWSize() {
        return waitingNotif.size();
    }

}
