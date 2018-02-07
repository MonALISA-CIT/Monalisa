/*
 * Created on Oct 2, 2010
 */
package lia.Monitor.Farm;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.Utils;
import lia.util.DynamicThreadPoll.ResultNotification;
import lia.util.DynamicThreadPoll.SchJobInt;
import lia.util.threads.MLExecutorsFactory;
import lia.util.threads.MLScheduledThreadPoolExecutor;


/**
 * Replacement class for ThreadPool ...
 * 
 * @author ramiro
 */
public class SchJobExecutor {
    
    private static final Logger logger = Logger.getLogger(SchJobExecutor.class.getName());

    private final static class InnerSchJobTask implements Runnable {
        
        private final static AtomicLong TASK_SEQ = new AtomicLong(1L);
        
        private final SchJobInt myJob;
        final ResultNotification resultNotifier;
        private final long id;
        
        public InnerSchJobTask(SchJobInt myJob, ResultNotification resultNotifier) {
            this.myJob = myJob;
            this.resultNotifier = resultNotifier;
            this.id = TASK_SEQ.getAndIncrement();
        }

        @Override
        public void run() {
            final boolean isFiner = logger.isLoggable(Level.FINER);
            if(isFiner) {
                logger.log(Level.FINER, "[ SchJobExecutor ] Executing job: " + id);
            }
            Object retResult = null;
            Throwable t = null;
            try {
                retResult = myJob.doProcess();
            } catch(InterruptedException ie) {
                t = ie;
                Thread.currentThread().interrupt();
            } catch(Throwable t1) {
                t = t1;
            } finally {
                if(isFiner) {
                    logger.log(Level.INFO, "[ SchJobExecutor ] Executing job: " + id + " returning " + ((retResult == null)?"null":retResult.toString()) + " exception: " + t);
                }
                resultNotifier.notifyResult(myJob, retResult, t);
            }
        }
        
    }
    
    final MLScheduledThreadPoolExecutor executor;
    final ResultNotification resultNotifier;
    final Map<SchJobInt, InnerSchJobTask> jobsMap = new HashMap<SchJobInt, InnerSchJobTask>();
    
    //@GuardedBy jobsMap
    boolean started = false;
    
    SchJobExecutor(int corePool, int maxWorkers, ResultNotification resultNotifier) throws Exception {
        this.executor = MLExecutorsFactory.getScheduledExecutorService("lia.Monitor.modules", 2, 50, 5);
        this.resultNotifier = resultNotifier;
    }
    
    /**
     * 
     * @param job
     * @throws IllegalStateException if the job was already scheduled
     */
    public void addJob(SchJobInt job) {
        if(job == null) {
            throw new NullPointerException("Job cannot be null");
        }
        final InnerSchJobTask iJob = new InnerSchJobTask(job, this.resultNotifier); 
        synchronized(jobsMap) {
            if(jobsMap.containsKey(job)) {
                throw new IllegalStateException("[ SchJobExecutor ] job " + job.getClass().getName() + " already scheduled ");
            }
            jobsMap.put(job, iJob);
            if(logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "[ SchJobExecutor ] adding job: " + job.getClass().getName() + " id: " + iJob.id);
            }
            if(started) {
                scheduleJob(job);
            }
        }
    }

    private final void scheduleJob(SchJobInt job) {
        final InnerSchJobTask iJob = jobsMap.get(job); 
        if(logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " Scheduling job " + job.getClass().getName() + " id: " + iJob.id + " repeatTime: " + job.get_repet_time() + " ms");
        }
        
        final long possibleRepeatTime = TimeUnit.MILLISECONDS.toNanos(job.get_repet_time());
        final long repeatTimeNanos = (possibleRepeatTime > 0)?possibleRepeatTime:TimeUnit.SECONDS.toNanos(30);
        if(possibleRepeatTime <= 0) {
            logger.log(Level.INFO, " Setting default repeat time: " + TimeUnit.NANOSECONDS.toSeconds(repeatTimeNanos) + " for job: " + job.getClass().getName());
        }
        
        //sounds complicated but you may get negative (int)nanos from 1 minute above ...
        final long initialDelayNanos = TimeUnit.SECONDS.toNanos(new Random(Utils.nanoNow()).nextInt((int)TimeUnit.NANOSECONDS.toSeconds(repeatTimeNanos)));

        if(repeatTimeNanos <= 0) {
            executor.schedule(iJob, 30, TimeUnit.SECONDS);
            return;
        }
        

        if(logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "[ SchJobExecutor ] scheduleJob job: " + job.getClass().getName() + " id: " + iJob.id + " initialDelay: " + TimeUnit.NANOSECONDS.toMillis(initialDelayNanos) + " ms repeatDelay: " + TimeUnit.NANOSECONDS.toMillis(repeatTimeNanos) + " ms");
        }

        executor.scheduleWithFixedDelay(iJob, initialDelayNanos, repeatTimeNanos, TimeUnit.NANOSECONDS);
    }
    
    public void startMonitoring() {
        synchronized(jobsMap) {
            if(!started) {
                started = true;
                for(final SchJobInt job: jobsMap.keySet()) {
                    scheduleJob(job);
                }
                logger.log(Level.INFO, " [ SchJobExecutor ] all monitoring modules submited for execution. ");
            }
        }
    }

    public boolean isStarted() {
        synchronized(jobsMap) {
            return started;
        }
    }
    
    public int getActiveCount() {
        return executor.getActiveCount();
    }
    
    public int getPoolSize() {
        return executor.getPoolSize();
    }

    public long getCompletedTaskCount() {
        return executor.getCompletedTaskCount();
    }

    public long getTaskCount() {
        return executor.getTaskCount();
    }
}
