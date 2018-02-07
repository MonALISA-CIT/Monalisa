/*
 * $Id: ThreadPool.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.util.DynamicThreadPoll;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeSet;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.threads.MonALISAExecutors;

/**
 * @author Iosif Legrand
 * @author ramiro
 */
public class ThreadPool extends Thread {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(ThreadPool.class.getName());

    public final LinkedList<TWorker> workers; // dynamic array of workers

    public DelayQueue<DelayedSchJobInt> jobs;

    private int created;

    public int killedjobs;

    public int killedwks;

    public int donejobs;

    public final TreeSet<TWorker> activeWorkers;

    ResultNotification pass_res;

    /** maximal number of workers */
    private int MAX_WORKERS = 20;

    private static AtomicLong seq = new AtomicLong(0);

    private class DelayedSchJobInt implements Delayed {

        SchJobInt job;

        final long mySeq;

        private DelayedSchJobInt(SchJobInt job) {
            this.job = job;
            this.mySeq = seq.getAndIncrement();
        }

        @Override
        public int compareTo(Delayed o) {
            if (o == this) {
                return 0;
            }

            DelayedSchJobInt dsj = (DelayedSchJobInt) o;

            final long otherExecTime = dsj.job.get_exec_time();
            final long myExecTime = job.get_exec_time();
            if (otherExecTime > myExecTime) {
                return -1;
            }
            if (otherExecTime < myExecTime) {
                return 1;
            }

            if (dsj.mySeq > mySeq) {
                return -1;
            }

            return 1;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(job.get_exec_time() - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }
    }

    public ThreadPool(ResultNotification pass_res) {
        super("(ML) ThreadPool Job Manager");
        // making it daeomn thread
        try {
            setDaemon(true);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Cannot setDaemon", t);
        }

        this.pass_res = pass_res;
        workers = new LinkedList<TWorker>();
        jobs = new DelayQueue<DelayedSchJobInt>();
        activeWorkers = new TreeSet<TWorker>();

        created = 1;
        killedjobs = 0;
        killedwks = 0;
        donejobs = 0;

    }

    public ThreadPool(ResultNotification pass_res, int max_workers) {
        this(pass_res);
        MAX_WORKERS = max_workers;
    }

    public void setResultNotification(ResultNotification pass_res) {
        this.pass_res = pass_res;
    }

    public void addJob(SchJobInt job) {
        if (job == null) {
            return;
        }
        jobs.offer(new DelayedSchJobInt(job));
    }

    @Override
    public void run() {
        int len = 0;

        logger.log(Level.INFO, " [ MonitoringThP ] Main thread started .... ");
        workers.add(new TWorker(this));
        MonALISAExecutors.getMLHelperExecutor().scheduleWithFixedDelay(new VerifyWorkers(), 10, 50, TimeUnit.SECONDS);
        MonALISAExecutors.getMLHelperExecutor().scheduleWithFixedDelay(new ThreadPoolStatus(), 10, 2, TimeUnit.MINUTES);

        while (true) {
            try {
                len = activeWorkers.size();

                /* max 20 wks th */
                while ((len > (MAX_WORKERS - 1)) && (workers.size() == 0)) {
                    try {
                        Thread.sleep(200);
                    } catch (Exception e) {
                    }
                }

                final DelayedSchJobInt nnj = jobs.take();

                if (nnj != null) {
                    distributeJob(nnj.job);
                    donejobs++;
                }

            } catch (InterruptedException ie) {
                logger.log(Level.WARNING, "[ ThPool ] [ HANDLED ] Interrupted Exception taking job", ie);
                Thread.interrupted();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[ ThPool ] [ HANDLED ] Exception Main Loop", t);
                try {
                    Thread.sleep(500);
                } catch (Exception e) {
                }
            }
        }// while( true )

    }

    public void distributeJob(SchJobInt j) {

        TWorker theWorker = null;
        synchronized (workers) {
            boolean assignedJob = false;

            while ((workers.size() > 0) && !assignedJob) {
                final TWorker oworker = workers.removeFirst();
                if (oworker.workerIsAlive()) {
                    activeWorkers.add(oworker);
                    assignedJob = true;
                    theWorker = oworker;
                } else {
                    logger.log(Level.INFO, "\n --> [ ThPool ] [ HANDLED ] cleanUp worker " + oworker.getWorkerID());
                }
            }

            if (!assignedJob) {
                theWorker = new TWorker(this);
                created++;
                activeWorkers.add(theWorker);
            }
        }// end synchronized ( workers )

        if (theWorker != null) {
            theWorker.doProcess(j);
        } else {
            // This should not be the case ...!
            // Only becase of OOM ... make sure that you do not take 100% CPU
            logger.log(Level.WARNING, "\n\n --> [ThPool] [HANDLED] \n\n Could not assign job ... Rescheduling ");
            // DO NOT MAKE A LOOP!!
            try {
                Thread.sleep(200);
            } catch (Throwable t) {
            }

            addJob(j);
        }

    }

    void addAvailable(final TWorker bw) {

        boolean added = false;
        synchronized (workers) {
            if (activeWorkers.remove(bw)) {
                workers.add(bw);
                added = true;
            } else {
                added = false;
                logger.log(Level.INFO, "[ ThPool ] [ HANDLED ] TWorker " + bw.getWorkerID() + " not active any more");
            }
        }// end synchronized ( syncWorkers )

        if (!added) {

        }
    }

    public final String state() {
        StringBuilder sb = new StringBuilder(1024);
        sb.append(" Running Jobs = ").append(activeWorkers.size());
        sb.append(" Available Workers=").append(workers.size());
        sb.append(" Jobs Done = ").append(donejobs);
        sb.append(" killed=").append(killedjobs);
        sb.append(" Total Threads created =").append(created);
        sb.append(" killed workers=").append(killedwks);
        return sb.toString();
    }

    class VerifyWorkers implements Runnable {

        @Override
        public void run() {

            long now = System.currentTimeMillis();

            final ArrayList<TWorker> killList = new ArrayList<TWorker>();

            synchronized (workers) {
                for (final TWorker worker : activeWorkers) {
                    long tmax = worker.getTimeLimit();
                    if ((tmax > 0) && (tmax < now)) {
                        killList.add(worker);
                    }
                }

                // too much workers...save some memory
                for (final Iterator<TWorker> it = workers.iterator(); it.hasNext();) {
                    final TWorker worker = it.next();
                    if (((worker.idleTime.get() + TWorker.WORKER_IDLE_TIMEOUT.get()) <= System.currentTimeMillis())
                            && (workers.size() > (2 + activeWorkers.size()))) {
                        it.remove();
                        worker.finish();
                        killedwks++;
                    }
                }
            }// end synchronized ( workers )

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "\n\n[ ThPool ] ======> KILL LIST SIZE = " + killList.size());
            }

            for (int i = 0; i < killList.size(); i++) {
                final TWorker worker = killList.get(i);

                synchronized (worker.syncNotifyResult) {
                    SchJobInt job = worker.myJob;
                    if (!workers.contains(worker)) {
                        if (job != null) {
                            if (logger.isLoggable(Level.FINEST)) {
                                logger.log(Level.FINEST, "[ ThPool ] ---->TimeOUT [ worker " + worker.getWorkerID()
                                        + " ]. Trying to stop it's job " + job);
                            }
                            try {
                                job.stop();
                            } catch (Throwable t) {
                                if (logger.isLoggable(Level.FINE)) {
                                    logger.log(Level.FINE, "[ ThPool ] [ HANDLED ]----> Got exception in job.stop()", t);
                                }
                            }

                            try {
                                worker.interrupt();
                            } catch (Throwable t) {
                                if (logger.isLoggable(Level.FINE)) {
                                    logger.log(Level.FINE,
                                            "[ ThPool ] [ HANDLED ]----> Got exception in worker.interrupt()", t);
                                }
                            }

                            killedjobs++;
                        } else {
                            if (logger.isLoggable(Level.FINE)) {
                                logger.log(Level.FINE, " [ HANDLED ] ---->TimeOUT worker = " + worker.getWorkerID()
                                        + "  BUT JOB==NULL. ");
                            }
                        }
                    } else {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, " [ HANDLED ] ---->TimeOUT worker = " + worker.getWorkerID()
                                    + "  BUT worker recovered ok :) !");
                        }
                    }
                }// synchronized ( worker.syncNotifyResult )

            }// for()

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "END KILL LIST \n\n");
            }

        }

    }

    class ThreadPoolStatus implements Runnable {

        @Override
        public void run() {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "\n\n THP STATUS: " + state());
            }
        }
    }

}
