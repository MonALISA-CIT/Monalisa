/**
 * 
 */
package lia.Monitor.Farm.Pathload;

import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.logging.MLLogEvent;

/**
 * This is a rewrite of the old pathload timer class.
 * This thread accepts a runnable interface as an argument
 * which it will run after waiting <i>delay</i> miliseconds,
 * and it will run it at a fixed rate of <i>period</i> millis.
 * 
 * The Timer may be awakend by calling the wakeMeUp method that
 * will awaken the current sleeping thread.
 * 
 *  There is a drawback to this approach: If the thread is to be 
 *  awakend when the runnable task is already running, the wakeUp
 *  call will be ignored.
 *  If period is 0, then the task will never be scheduled and it will 
 *  wait to be awakend.
 *  
 * @author heri
 *
 */
public class Timer extends Thread {

    private final Runnable task;
    private final long delay;
    private final long period;
    private long runningTime;
    private boolean isShuttingDown;
    private boolean taskMayRun;
    private final Object lock;

    /**
     * Our logging component (this timer is used in abping/pathload modules, so log events
     * in this logger)
     */
    private static final Logger logger = Logger.getLogger(Timer.class.getName());

    /**
     * Default constructor
     * 
     * @param task	Task of witch to run the run method. Attention, it
     * 				is not realy a runnable thing. Run me as Thread.start();
     * @param delay	Time to wait until the fist time to run
     * @param period	Period to wait between reschedules.
     */
    public Timer(Runnable task, long delay, long period) {
        this.task = task;
        this.delay = delay;
        this.period = period;
        this.runningTime = Long.MAX_VALUE;
        if (period > 0) {
            this.runningTime = System.currentTimeMillis() + period;
        }
        this.isShuttingDown = false;
        this.taskMayRun = false;
        this.lock = new Object();
    }

    /**
     * This will shut the thread down as soon as it has the chance.
     *
     */
    public void shutdown() {
        synchronized (lock) {
            isShuttingDown = true;
        }
    };

    /**
     * Wake the thread up and run it.
     * A thread that is already running the given task,
     * will ignore this call. 
     *
     */
    public void wakeMeUp() {
        synchronized (lock) {
            taskMayRun = true;
            lock.notify();
        }
    }

    /**
     * The run cycle. This will check that the given parameters are
     * good, and run the thread scheduling continously, until stopped.
     */
    @Override
    public void run() {
        if ((task == null) || (delay < 0) || (period < 0)) {
            return;
        }

        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
        }

        while (true) {
            try {
                synchronized (lock) {
                    while ((!taskMayRun) && (runningTime > System.currentTimeMillis())) {
                        lock.wait(getWaitingPeriod());
                    }
                    if (isShuttingDown == true) {
                        break;
                    }
                }
            } catch (InterruptedException e) {
            }

            try {
                task.run();
            } catch (Throwable e) {
                //this should not happen, but just in case
                try {
                    MLLogEvent logEv = new MLLogEvent();
                    logEv.put("Error Name", "[PATHLOAD] Failed to run Timertask:" + task.toString());
                    logEv.put("Error", e.toString());
                    logger.log(Level.SEVERE, "Pathload time-task failed.", new Object[] { logEv });
                } catch (Throwable t) {
                    logger.log(Level.FINE, "Error while logging. " + t.getMessage());
                }
            }

            synchronized (lock) {
                if (period > 0) {
                    runningTime = System.currentTimeMillis() + period;
                }
                taskMayRun = false;
            }
        }
    }

    /**
     * Calculate the time to wait until the Thread is scheduled to
     * run.
     * 
     * @return	Time to wait until the thread must wake up.
     */
    private long getWaitingPeriod() {
        long waitingTime = (runningTime - System.currentTimeMillis()) + 10;

        if (waitingTime < 0) {
            return 0L;
        }
        return waitingTime;
    }
}
