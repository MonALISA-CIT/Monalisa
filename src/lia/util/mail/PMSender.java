/*
 * $Id: PMSender.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.util.mail;

import java.util.Date;
import java.util.Map.Entry;
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
import lia.Monitor.monitor.EMsg;
import lia.util.ntp.NTPDate;
import lia.util.threads.MonALISAExecutors;

/**
 * 
 * @author ramiro
 */
public class PMSender extends MailSender implements Runnable {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(PMSender.class.getName());

    private static volatile int MAX_PMS_MSGS = 1500; // we are not GMAIL or Yahoo! or mail.cs.pub.ro :) ?
    private static volatile long RETRY_AFTER = 1 * 60 * 1000;//1 min
    private static volatile long RETRY_AFTER_FACTOR = 2;//Real Retry = RETRY_AFTER * RETRY_AFTER_FACTOR ( but max 1h )
    private static volatile int MAX_RETRIES = 500;//for sure it is a real problem if this value is reached ... 
    private static volatile boolean DELIVER_INTERNAL_NOTIF = true;
    private static final long ONE_HOUR = 60 * 60 * 1000L;

    private static PMSender me = null;

    //The Mail Queue
    private static final DelayQueue<EMsgEntry> mailq = new DelayQueue<EMsgEntry>();
    private static final ConcurrentHashMap<Integer, EMsgEntry> waitingNotif = new ConcurrentHashMap<Integer, EMsgEntry>();
    private static final AtomicLong droppedMsgsCount = new AtomicLong(0);
    private static final AtomicLong totalMsgsCount = new AtomicLong(0);
    private static final AtomicLong totalMsgsSentCount = new AtomicLong(0);
    private static final AtomicReference<ProxyWorker> proxyWorkerPointer = new AtomicReference<ProxyWorker>(null);
    private static StatusThread statusThread;

    static {
        reloadCfgParams();

        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                reloadCfgParams();
            }

        });
    }

    private static class EMsgEntry implements Delayed {
        private final EMsg emsg;
        final long entryTime;
        long nextRetry;//in case of failure
        private final Integer id;//It seems redundant from emsg, but we need it to "safe" remove a notified EMsg from proxy ;) 
        private final int retries;

        private EMsgEntry(EMsg emsg, long entryTime, long nextRetry, int retries) {
            this.emsg = emsg;
            this.entryTime = entryTime;
            this.nextRetry = nextRetry;
            this.id = emsg.getID();
            this.retries = retries;
        }

        @Override
        public int compareTo(Delayed o) {
            if (o == this) {
                return 0;
            }
            EMsgEntry eme = (EMsgEntry) o;
            if (eme.nextRetry > nextRetry) {
                return -1;
            }
            if (eme.nextRetry < nextRetry) {
                return 1;
            }

            return id.compareTo(eme.id);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(nextRetry - NTPDate.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof EMsgEntry) ? ((EMsgEntry) o).id.equals(id) : false;
        }

        @Override
        public int hashCode() {
            return id.intValue();
        }
    }

    private static class StatusThread extends Thread {
        AtomicBoolean alive;
        long sleepTime;

        StatusThread(long sleepTime) {
            super(" (ML) PMS Status Thread ");
            alive = new AtomicBoolean(true);
            this.sleepTime = sleepTime;
        }

        void stopIt() {
            alive.set(false);
            StatusThread.this.interrupt();
        }

        @Override
        public void run() {
            logger.log(Level.INFO, " [ PMS ] [ StatusThread ] started ...");
            while (alive.get()) {
                try {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (Throwable ignoreT) {
                        //not interesting
                    }
                    logger.log(Level.INFO, getStatus());
                } catch (Throwable t) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, " Got ex ", t);
                    }
                }
            }//while

            logger.log(Level.INFO, " [ PMS ] [ StatusThread ] finishes ...");
        }//run()
    }

    //only from this package
    public static final void setPW(ProxyWorker pw) {
        proxyWorkerPointer.set(pw);
    }

    private PMSender() {
    }

    /**
     * @param EMsg
     * @param enqueue  
     */
    @Override
    public void sendMessage(EMsg emsg, boolean enqueue) throws Exception {
        if (!DELIVER_INTERNAL_NOTIF) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, " Should notif EMsg but DELIVER_INTERNAL_NOTIF == false");
            }
            return;
        }

        if (emsg == null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "Got null EMsg ... Ignoring it");
            }
            return;
        }

        totalMsgsCount.incrementAndGet();

        //TODO - remove older emsgs ... 
        if (mailq.size() > MAX_PMS_MSGS) {
            droppedMsgsCount.incrementAndGet();
            //That's life ... sometimes you loose 
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ PMS ] Too many notif info in the queue; Dropping notif info ... \n"
                        + emsg.message);
            }
            return;
        }

        long now = NTPDate.currentTimeMillis();
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ PMS ] EMsg " + emsg + " enqueued ... ");
        }
        mailq.offer(new EMsgEntry(emsg, now, now, 0));
    }

    public static synchronized PMSender getInstance() {
        if (me == null) {
            me = new PMSender();
            MonALISAExecutors.getMLHelperExecutor().scheduleWithFixedDelay(me, 10 + Math.round(Math.random() * 10),
                    10 + Math.round(Math.random() * 10), TimeUnit.SECONDS);
        }
        return me;
    }

    public static String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n ************** PMS Status *************** \n");
        sb.append("\n ---------- mq.size = ").append(mailq.size()).append(" ------------- ");
        if ((mailq.size() > 0) && logger.isLoggable(Level.FINEST)) {
            for (EMsgEntry em : mailq) {
                sb.append("\n ---> [ ").append(new Date(em.entryTime)).append(" <> ").append(new Date(em.nextRetry))
                        .append("] <---- \n");
                sb.append(em.emsg.getHeader()).append("\n");
            }
        }
        sb.append("\n ---------- notif.size = ").append(waitingNotif.size()).append(" ------------- ");
        if ((waitingNotif.size() > 0) && logger.isLoggable(Level.FINEST)) {
            for (Entry<Integer, EMsgEntry> entry : waitingNotif.entrySet()) {
                EMsgEntry emsge = entry.getValue();

                sb.append("\n ---> [ ").append(new Date(emsge.entryTime)).append(" <> ")
                        .append(new Date(emsge.nextRetry)).append("] <---- \n");

                sb.append("\n").append(emsge.emsg.getHeader());
            }
        }
        sb.append("\n************** END PMS Status *************** \n");
        return sb.toString();
    }

    private void deliverMsgs(ProxyWorker pw) {
        //will block until a new EMsg received
        EMsgEntry eme = null;
        try {
            eme = mailq.poll();
        } catch (Exception ie) { //this will NEVER ?! happen
            logger.log(Level.WARNING, " [ PMS ] Got ex", ie);
        }

        while (eme != null) {
            //resch in case of failure
            // Not using the same EMsgEntry to not mess with compareTo()
            if (eme.retries < MAX_RETRIES) {
                EMsgEntry resch = null;
                long now = NTPDate.currentTimeMillis();
                long queueWTime = now - eme.entryTime;
                long nextWait = RETRY_AFTER;
                if (queueWTime >= RETRY_AFTER) {
                    nextWait = queueWTime * RETRY_AFTER_FACTOR;
                    if (nextWait > ONE_HOUR) {
                        nextWait = ONE_HOUR;
                    }
                }
                resch = new EMsgEntry(eme.emsg, eme.entryTime, now + nextWait, eme.retries + 1);

                mailq.offer(resch);
                waitingNotif.put(resch.id, resch);
            } else {
                //Just to make sure that we do not have leaks
                waitingNotif.remove(eme.id);
            }

            //now, after we have rescheduled the retry in case of a failure ( hope not :) )
            //try to send it
            pw.sendMail(eme.emsg);
            try {
                eme = mailq.poll();
            } catch (Exception ie) { //this will NEVER ?! happen
                logger.log(Level.WARNING, " [ PMS ] Got ex", ie);
            }
        }
    }

    public void notifyDelivered(Integer id) {
        try {
            EMsgEntry removed = waitingNotif.remove(id);
            if ((removed != null) && mailq.remove(removed)) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " [ PMS ] REMOVED from my cache " + id + " \n " + removed.emsg.message);
                }
                totalMsgsSentCount.incrementAndGet();
            } else {//maybe it was the 51st retry :)
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " [ PMS ] [ HANDLED ] No such id in my cache " + id);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ PMS ] [ HANDLED ] Got ex removing ID " + id, t);
        }
    }

    private static void reloadCfgParams() {

        DELIVER_INTERNAL_NOTIF = true;

        try {
            String st = AppConfig.getProperty("lia.util.mail.PMS.DELIVER_INTERNAL_NOTIF", "true");
            if (st != null) {
                DELIVER_INTERNAL_NOTIF = Boolean.valueOf(st.trim()).booleanValue();
            } else {
                DELIVER_INTERNAL_NOTIF = true;
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ PMS ] Got ex parsing lia.util.mail.PMS.DELIVER_INTERNAL_NOTIF", t);
            }
            DELIVER_INTERNAL_NOTIF = true;
        }

        try {
            MAX_PMS_MSGS = AppConfig.geti("lia.util.mail.PMS.MAX_PMS_MSGS", 1500);
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ PMS ] Got ex parsing lia.util.mail.PMS.MAX_PMS_MSGS", t);
            }
            MAX_PMS_MSGS = 1500;
        }

        try {
            RETRY_AFTER = AppConfig.getl("lia.util.mail.PMS.RETRY_AFTER", 60) * 1000;
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ PMS ] Got ex parsing lia.util.mail.PMS.RETRY_AFTER", t);
            }
            RETRY_AFTER = 60 * 1000;
        }

        try {
            RETRY_AFTER_FACTOR = Long.valueOf(AppConfig.getProperty("lia.util.mail.PMS.RETRY_AFTER", "2")).longValue();
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ PMS ] Got ex parsing lia.util.mail.PMS.RETRY_AFTER_FACTOR", t);
            }
            RETRY_AFTER_FACTOR = 2;
        }

        try {
            MAX_RETRIES = AppConfig.geti("lia.util.mail.PMS.MAX_RETRIES", 500);
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ PMS ] Got ex parsing lia.util.mail.PMS.MAX_RETRIES", t);
            }
            MAX_RETRIES = 500;
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
            logger.log(Level.FINEST, " [ PMS ] [ reloadCfgParams ]  " + " DELIVER_INTERNAL_NOTIF = "
                    + DELIVER_INTERNAL_NOTIF + " MAX_PMS_MSGS = " + MAX_PMS_MSGS + " RETRY_AFTER = " + RETRY_AFTER
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
                logger.log(Level.FINER, " Got Exc sending EMsg", t);
            }
        }
    }

    public int getPMSSize() {
        return mailq.size();
    }

    public int getPMSWSize() {
        return waitingNotif.size();
    }

    public long sentCount() {
        return totalMsgsCount.get();
    }

    public long droppedCount() {
        return droppedMsgsCount.get();
    }
}
