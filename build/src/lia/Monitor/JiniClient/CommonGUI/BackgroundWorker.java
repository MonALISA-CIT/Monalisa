package lia.Monitor.JiniClient.CommonGUI;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * We use a single thread to do all background
 * work for the panels instead of creating a thread
 * for each one.
 * This should reduce the concurency and therefore the
 * load of the system.
 * The tasks sould reschedule themselves after each run
 * with a time in concordance with their status (non)/visible,
 * that is fairly generous.
 */
public final class BackgroundWorker {

    private final static ScheduledExecutorService timer = Executors
            .newSingleThreadScheduledExecutor(new ThreadFactory() {

                @Override
                public Thread newThread(Runnable r) {
                    final Thread t = new Thread(r);
                    t.setName("(ML) BackgroundWorker");
                    return t;
                }
            });

    // the one and only instance of this class
    private static BackgroundWorker pt = new BackgroundWorker();

    /**
     * private constructor;
     * the object will be created on it's first reference
     */
    private BackgroundWorker() {
        //singleton
    }

    /**
     * @see Timer.schedule
     * @param task
     * @param delay
     */
    public static void schedule(TimerTask task, long delay) {
        timer.schedule(task, delay, TimeUnit.MILLISECONDS);
    }

    public static void schedule(TimerTask task, long delay, long period) {
        timer.scheduleWithFixedDelay(task, delay, period, TimeUnit.MILLISECONDS);
    }

    public Thread newBackgroundWorkerThread(TimerTask task, long delay, long period) {

        return new BackgroundWorkerThread(task, delay, period);
    }

    public static Thread controlledSchedule(TimerTask task, long delay, long period) {

        Thread thread = pt.newBackgroundWorkerThread(task, delay, period);
        thread.start();
        return thread;
    }

    public static void cancel(Thread thread) {

        if (thread instanceof BackgroundWorkerThread) {
            ((BackgroundWorkerThread) thread).cancel();
        }
    }

    static class BackgroundWorkerThread extends Thread {

        protected TimerTask task;

        protected long delay = 0;

        protected long period = 0;

        protected boolean hasToRun = true;

        public BackgroundWorkerThread(TimerTask task, long delay, long period) {

            super("BackgroundWorkerThread");
            this.task = task;
            this.period = period;
            this.delay = delay;
        }

        public void cancel() {
            hasToRun = false;
        }

        @Override
        public void run() {

            try {
                Thread.sleep(delay);
            } catch (Throwable t) {
            }
            while (hasToRun) {
                try {
                    task.run();
                } catch (Throwable t) {
                    System.err.println("Error processing task. Terminating...");
                    t.printStackTrace();
                    return;
                }
                try {
                    Thread.sleep(period);
                } catch (Throwable t) {
                }
            }
        }
    }
}
