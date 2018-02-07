/*
 * $Id: TWorker.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.util.DynamicThreadPoll;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;

/**
 * 
 * @author Iosif Legrand
 * @author ramiro
 * 
 */
class TWorker extends Thread implements Comparable<TWorker> {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(TWorker.class.getName());

    private static final AtomicLong ID_SEQ = new AtomicLong(0);

    public static final AtomicLong WORKER_IDLE_TIMEOUT = new AtomicLong(2 * 60 * 1000);//2 minutes
    public static final AtomicLong DEFAULT_JOB_WAIT_TIME = new AtomicLong(1 * 60 * 1000);//1 minute

    static {
        AppConfig.addNotifier(new AppConfigChangeListener() {
            @Override
            public void notifyAppConfigChanged() {
                reloadConf();
            }
        });
    }

    private static final void reloadConf() {
        long tmpVal = 2 * 60 * 1000;
        try {
            tmpVal = AppConfig.getl("lia.util.DynamicThreadPoll.TWorker.IDLE_TIMEOUT", 120) * 1000;
        } catch (Throwable t) {
            tmpVal = 120 * 1000;
        }
        WORKER_IDLE_TIMEOUT.set(tmpVal);

        tmpVal = 1 * 60 * 1000;
        try {
            tmpVal = AppConfig.getl("lia.util.DynamicThreadPoll.TWorker.DEFAULT_JOB_WAIT_TIME", 60) * 1000;
        } catch (Throwable t) {
            tmpVal = 120 * 1000;
        }
    }

    private final long myID;
    public int jcount;
    private final ThreadPool main;

    public final Object syncNotifyResult = new Object();

    protected volatile SchJobInt myJob;
    final AtomicLong time_limit = new AtomicLong(0);
    private final AtomicBoolean alive;
    private final Object waitingJobLock = new Object();
    final AtomicLong idleTime = new AtomicLong(System.currentTimeMillis());

    public TWorker(ThreadPool main) {
        super();
        this.myID = ID_SEQ.getAndIncrement();
        super.setName("(ML) TWorker " + myID + " created " + new Date().toString());
        this.main = main;
        myJob = null;
        jcount = 0;
        alive = new AtomicBoolean(true);

        //make it daemon thread
        try {
            setDaemon(true);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Cannot setDaemon", t);
        }
        start();
    }

    public void finish() {
        alive.set(false);
        synchronized (waitingJobLock) {
            waitingJobLock.notify();
        }
    }

    public boolean workerIsAlive() {
        return alive.get();
    }

    public long getWorkerID() {
        return myID;
    }

    public void doProcess(SchJobInt j) {
        synchronized (waitingJobLock) {
            myJob = j;
            waitingJobLock.notify();
        }
    }

    long getTimeLimit() {
        if (myJob == null) {
            return 0;
        }
        return time_limit.get();
    }

    @Override
    public void run() {
        Object res = null;
        Throwable ey = null;

        while (alive.get()) {
            synchronized (waitingJobLock) {
                while ((myJob == null) && alive.get()) {
                    try {
                        idleTime.set(System.currentTimeMillis());
                        waitingJobLock.wait();
                    } catch (InterruptedException ie) {
                        logger.log(Level.WARNING, " [ TWorker ] " + myID
                                + " [ HANDLED ]  got interrupted exception waiting for job", ie);
                        Thread.interrupted();
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " [ TWorker " + myID
                                + " ] [ HANDLED ] got general exception waiting for job", t);
                    }
                }
            }//end sync

            if (myJob != null) {
                long jobMaxTime = myJob.get_max_time();

                if (jobMaxTime <= 0) {
                    jobMaxTime = DEFAULT_JOB_WAIT_TIME.get();
                }

                time_limit.set(System.currentTimeMillis() + jobMaxTime);

                res = null;
                ey = null;

                try {
                    res = process(myJob);
                } catch (InterruptedException ie) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, " [ TWorker " + myID + " ] Interrupted Exception in procesing job: "
                                + myJob, ie);
                    }
                    ey = ie;
                } catch (Throwable t) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, " [ TWorker " + myID + " ] Interrupted Exception in procesing job: "
                                + myJob, t);
                    }
                    ey = t;
                }

                synchronized (syncNotifyResult) {
                    try {
                        main.pass_res.notifyResult(myJob, res, ey);
                        myJob = null;

                        if (alive.get()) {
                            time_limit.set(0);
                            jcount++;
                            main.addAvailable(this);
                        }
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "[ TWorker ] [ HANDLED ] TWorker " + myID
                                + " got exc in notification hook", t);
                    }
                }//end sync
            }
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " TWorker " + myID + " is going to stop ");
        }

        if (myJob != null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ TWorker ]  " + myJob + " != null  ");
            }
            myJob.stop();
        }
        myJob = null;

        res = null;
        ey = null;
        alive.set(false);
    }

    public Object process(SchJobInt j) throws Exception {
        return j.doProcess();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(" Worker id=").append(myID).append(" Total # jobs done ").append(jcount);
        if (myJob == null) {
            sb.append(" status = waiting ");
        } else {
            sb.append(" status = running ");
        }
        return sb.toString();
    }

    @Override
    public int compareTo(TWorker other) {
        if (this == other) {
            return 0;
        }
        final long diff = myID - other.myID;
        if (diff < 0) {
            return -1;
        }
        return 1;
    }

}
