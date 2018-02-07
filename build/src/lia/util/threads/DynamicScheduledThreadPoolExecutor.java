/*
 * Created on Aug 23, 2007 $Id: DynamicScheduledThreadPoolExecutor.java 7280 2012-07-03 23:01:28Z ramiro $
 */
package lia.util.threads;

import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This class provides a more flexible {@link ScheduledExecutorService}, extending {@link ScheduledThreadPoolExecutor}
 * and adding the possibility fo have a min/max number of threads. A thread ( the scheduler thread ) is always used
 * internally. All the opperations like {@link ThreadPoolExecutor.setCorePoolSize},
 * {@link ThreadPoolExecutor.setMaximumPoolSize}, {@link ThreadPoolExecutor.allowCoreThreadTimeOut},
 * {@link ThreadPoolExecutor.setKeepAliveTime} will modify the internal executor, which is a {@link ThreadPoolExecutor}
 * If the maximum number of threads is reached the all the tasks will be executed by the scheduler thread, so all the
 * suqsequent tasks will be delayed ... The logger used by this class is obtained from the <code>name</code> passed as
 * parameter to the constructor. ( Logger.getLogger(name) ); This class uses {@link decorateTask} which has/had a BUG (
 * 6560953 ). It was fixed in JDK 7(b15). backport-util-concurrent has the same bug ...
 * 
 * @author ramiro
 */
public class DynamicScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {

    @Override
    public void setCorePoolSize(int corePoolSize) {
        realExecutor.setCorePoolSize(corePoolSize);
    }

    @Override
    public void setKeepAliveTime(long time, TimeUnit unit) {
        realExecutor.setKeepAliveTime(time, unit);
    }

    @Override
    public void setMaximumPoolSize(int maximumPoolSize) {
        realExecutor.setMaximumPoolSize(maximumPoolSize);
    }

    @Override
    public int getCorePoolSize() {
        return realExecutor.getCorePoolSize();
    }

    @Override
    public int getMaximumPoolSize() {
        return realExecutor.getMaximumPoolSize();
    }

    @Override
    public int getPoolSize() {
        return realExecutor.getPoolSize();
    }

    @Override
    public int getActiveCount() {
        return realExecutor.getActiveCount();
    }

    @Override
    public int getLargestPoolSize() {
        return realExecutor.getLargestPoolSize();
    }

    @Override
    public long getTaskCount() {
        return super.getTaskCount();
    }

    @Override
    public long getCompletedTaskCount() {
        return realExecutor.getCompletedTaskCount();
    }

    @Override
    public void allowCoreThreadTimeOut(boolean value) {
        realExecutor.allowCoreThreadTimeOut(value);
    }

    @Override
    public boolean allowsCoreThreadTimeOut() {
        return realExecutor.allowsCoreThreadTimeOut();
    }

    /** The real executor for the tasks */
    private final ThreadPoolExecutor realExecutor;

    private final Logger logger;

    /**
     * @param name
     *            - the name used for setName() for all the Threads spawned by this thread pool; the logger used by this
     *            class will also use this name to get the logger.
     * @param corePoolSize
     * @param maxPoolSize
     * @param keepAliveTime
     * @param unit
     */
    public DynamicScheduledThreadPoolExecutor(final String name, final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, TimeUnit unit) {
        super(1, new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                final Thread t = new Thread(r, "( ML ) Main Task Scheduler [ " + name + " ] started: " + new Date());
                t.setDaemon(true);
                return t;
            }
        });

        super.allowCoreThreadTimeOut(false);
        logger = Logger.getLogger(name + ".tpool");

        realExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, new SynchronousQueue<Runnable>(), new MLThreadFactory(name), new MLCallerRunsPolicy(name, logger)) {

            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                if (t != null) {// it's already thrown ... this thread is hales-bules!!!
                    logger.log(Level.WARNING, Thread.currentThread().getName() + " [ HANDLED ] Exception  executing task " + r, t);
                }
                super.afterExecute(r, t);
            }
        };

        realExecutor.allowCoreThreadTimeOut(true);
    }

    private static class MyRunner<V> implements Runnable {

        private final RunnableScheduledFuture<V> task;

        private final Logger logger;

        // only for debugging ... we got some strange things going on
        private final Runnable r;

        private final Callable<V> c;

        MyRunner(Runnable runnable, RunnableScheduledFuture<V> task, Logger logger) {
            this.task = task;
            this.r = runnable;
            this.c = null;
            this.logger = logger;
        }

        MyRunner(Callable<V> callable, RunnableScheduledFuture<V> task, Logger logger) {
            this.task = task;
            this.r = null;
            this.c = callable;
            this.logger = logger;
        }

        @Override
        public void run() {
            if (logger != null && logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, Thread.currentThread().getName() + " start executing :" + this);
            }
            task.run();
        }

        @Override
        public String toString() {
            if (r != null) {
                return "MyRunner for: ( " + r.getClass().getName() + " ).toString(): " + r;
            }

            if (c != null) {
                return "MyRunner for: ( " + c.getClass().getName() + " ).toString(): " + c;
            }

            return "MyRunner for: ( NULLL !?!??!? ) CHECK THE CODE! ";
        }
    }

    private class MyRunnableScheduledFuture<V> implements RunnableScheduledFuture<V> {

        private final RunnableScheduledFuture<V> task;

        private final Runnable realRunner;

        MyRunnableScheduledFuture(Runnable runnable, RunnableScheduledFuture<V> task, Logger logger) {
            this.task = task;
            this.realRunner = new MyRunner<V>(runnable, task, logger);
        }

        MyRunnableScheduledFuture(Callable<V> callable, RunnableScheduledFuture<V> task, Logger logger) {
            this.task = task;
            this.realRunner = new MyRunner<V>(callable, task, logger);
        }

        @Override
        public boolean isPeriodic() {
            return task.isPeriodic();
        }

        @Override
        public void run() {
            realExecutor.execute(this.realRunner);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return task.cancel(mayInterruptIfRunning);
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            return task.get();
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return task.get(timeout, unit);
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }

        @Override
        public boolean isDone() {
            return task.isDone();
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return task.getDelay(unit);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof MyRunnableScheduledFuture) {
                @SuppressWarnings("unchecked")
                final MyRunnableScheduledFuture<V> other = (MyRunnableScheduledFuture<V>)o;
                return this.task.equals(other.task);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return this.task.hashCode() + System.identityHashCode(realRunner);
        }

        @Override
        public int compareTo(Delayed o) {
            return task.compareTo(o);
        }
    }

    /**
     * @see
     * java.util.concurrent.ScheduledThreadPoolExecutor#decorateTask(java.util.concurrent.Callable, java.util.concurrent.RunnableScheduledFuture)
     */
    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(Callable<V> callable, RunnableScheduledFuture<V> task) {
        return new MyRunnableScheduledFuture<V>(callable, task, logger);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.ScheduledThreadPoolExecutor#decorateTask(java.lang.Runnable,
     * java.util.concurrent.RunnableScheduledFuture)
     */
    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(Runnable runnable, RunnableScheduledFuture<V> task) {
        return new MyRunnableScheduledFuture<V>(runnable, task, logger);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.ThreadPoolExecutor#beforeExecute(java.lang.Thread,
     * java.lang.Runnable)
     */
    @Override
    protected void beforeExecute(final Thread t, final Runnable r) {
    	//not used
    }

    public static final void main(String[] args) {
        final AtomicLong SEQ = new AtomicLong(0);
        final ScheduledThreadPoolExecutor stpe = new DynamicScheduledThreadPoolExecutor("gigel", 10, 100, 10, TimeUnit.SECONDS);

        for (int j = 0; j < 10; j++) {
            final Runnable r = new Runnable() {

                final long id = SEQ.getAndIncrement();

                @Override
                public void run() {
                    final String origName = Thread.currentThread().getName();
                    Thread.currentThread().setName("FakeWorker " + id + " started @ " + new java.util.Date());
                    System.out.println(new java.util.Date() + "FakeWorker " + id + " started ");
                    try {
                        Thread.sleep(10 * 1000);
                    } catch (Throwable _) {
                    	//ignore it
                    }
                    System.out.println(new java.util.Date() + " FakeWorker " + id + " finished ");
                    Thread.currentThread().setName(origName);
                }
            };

            stpe.scheduleWithFixedDelay(r, 2, 4, TimeUnit.SECONDS);
        }

        final Object waitLock = new Object();
        for (;;) {
            try {
            	synchronized(waitLock) {
            		waitLock.wait();
            	}
            } catch (InterruptedException ie) {
            	//not interesting
            }
        }
    }

}
