/*
 * Created on Feb 2, 2011
 */
package lia.util.threads;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stupid hack for BUG 6560953, it should no longer be used with Java7+
 * It does not support maximum number of threads! But hopefullty the "core" threads will be enough
 * 
 * @author ramiro
 */
public class MLScheduledThreadPoolExecutor implements ScheduledExecutorService {

    private final ScheduledThreadPoolExecutor executor;

    private final Logger logger;

    private final String name;

    public MLScheduledThreadPoolExecutor(final String name, final int corePoolSize, final long keepAliveTime, TimeUnit unit) {
        logger = Logger.getLogger(name + ".tpool");
        this.name = name;
        this.executor = new ScheduledThreadPoolExecutor(corePoolSize, new MLThreadFactory(name), new MLCallerRunsPolicy(name, logger));
        this.executor.setKeepAliveTime(keepAliveTime, unit);
        this.executor.allowCoreThreadTimeOut(true);
        final String msg = " [ MLScheduledThreadPoolExecutor ] thread pool " + name + " inited." + " core threads count: " + corePoolSize + " keepAlive: " + keepAliveTime + " " + unit;
        if (logger != null) {
            logger.log(Level.FINE, msg);
        }
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.ExecutorService#shutdown()
     */
    @Override
    public void shutdown() {
        final String msg = " [ MLScheduledThreadPoolExecutor ] thread pool " + name + " SHUTDOWN!";
        if (logger != null) {
            logger.log(Level.FINE, msg);
        }
        
        System.err.println(new Date() + msg);
        
        this.executor.shutdown();
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.ExecutorService#shutdownNow()
     */
    @Override
    public List<Runnable> shutdownNow() {
        final String msg = " [ MLScheduledThreadPoolExecutor ] thread pool " + name + " SHUTDOWN NOW!";
        if (logger != null) {
            logger.log(Level.FINE, msg);
        }
        
        System.err.println(new Date() + msg);
        return this.executor.shutdownNow();
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.ExecutorService#isShutdown()
     */
    @Override
    public boolean isShutdown() {
        return this.executor.isShutdown();
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.ExecutorService#isTerminated()
     */
    @Override
    public boolean isTerminated() {
        return this.executor.isTerminated();
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.ExecutorService#awaitTermination(long, java.util.concurrent.TimeUnit)
     */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return this.executor.awaitTermination(timeout, unit);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.ExecutorService#submit(java.util.concurrent.Callable)
     */
    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return this.executor.submit(task);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.ExecutorService#submit(java.lang.Runnable, java.lang.Object)
     */
    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return this.executor.submit(task, result);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.ExecutorService#submit(java.lang.Runnable)
     */
    @Override
    public Future<?> submit(Runnable task) {
        return this.executor.submit(task);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.ExecutorService#invokeAll(java.util.Collection)
     */
    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return this.executor.invokeAll(tasks);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.ExecutorService#invokeAll(java.util.Collection, long, java.util.concurrent.TimeUnit)
     */
    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return this.executor.invokeAll(tasks, timeout, unit);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.ExecutorService#invokeAny(java.util.Collection)
     */
    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return this.executor.invokeAny(tasks);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.ExecutorService#invokeAny(java.util.Collection, long, java.util.concurrent.TimeUnit)
     */
    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return this.executor.invokeAny(tasks, timeout, unit);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.Executor#execute(java.lang.Runnable)
     */
    @Override
    public void execute(Runnable command) {
        this.executor.execute(command);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.ScheduledExecutorService#schedule(java.lang.Runnable, long,
     * java.util.concurrent.TimeUnit)
     */
    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return this.executor.schedule(command, delay, unit);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.ScheduledExecutorService#schedule(java.util.concurrent.Callable, long,
     * java.util.concurrent.TimeUnit)
     */
    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return this.executor.schedule(callable, delay, unit);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.ScheduledExecutorService#scheduleAtFixedRate(java.lang.Runnable, long, long,
     * java.util.concurrent.TimeUnit)
     */
    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command, long initialDelay, long period, TimeUnit unit) {
        return this.executor.scheduleAtFixedRate(MLExecutorsFactory.safeRunnable(command, name, logger), initialDelay, period, unit);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.ScheduledExecutorService#scheduleWithFixedDelay(java.lang.Runnable, long, long,
     * java.util.concurrent.TimeUnit)
     */
    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return this.executor.scheduleWithFixedDelay(MLExecutorsFactory.safeRunnable(command, name, logger), initialDelay, delay, unit);
    }

    public void setCorePoolSize(int coreThreads) {
        this.executor.setCorePoolSize(coreThreads);
    }

    public int getCorePoolSize() {
        return this.executor.getCorePoolSize();
    }

    public int getMaximumPoolSize() {
        return this.executor.getMaximumPoolSize();
    }

    public void setMaximumPoolSize(int maxThreads) {
        this.executor.setMaximumPoolSize(maxThreads);
    }

    public int getPoolSize() {
        return this.executor.getPoolSize();
    }

    public int getActiveCount() {
        return this.executor.getActiveCount();
    }

    public int getLargestPoolSize() {
        return this.executor.getLargestPoolSize();
    }

    public long getTaskCount() {
        return this.executor.getTaskCount();
    }

    public long getCompletedTaskCount() {
        return this.executor.getCompletedTaskCount();
    }

    public void setKeepAliveTime(long timeout, TimeUnit unit) {
        this.executor.setKeepAliveTime(timeout, unit);
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        final MLScheduledThreadPoolExecutor executor = new MLScheduledThreadPoolExecutor("test.pool", 1, 2, TimeUnit.MINUTES);
        executor.scheduleWithFixedDelay(new Runnable() {

            private int count = 0;

            @Override
            public void run() {
                final Thread cThread = Thread.currentThread();

                System.out.println(" count: " + count + " name: " + cThread.getName() + " isInterrupted: " + cThread.isInterrupted());
                // TODO Auto-generated method stub
                try {
                    final ReentrantLock lock = new ReentrantLock();
                    final Condition c = lock.newCondition();

                    lock.tryLock(10, TimeUnit.SECONDS);

                    try {
                        c.await(2, TimeUnit.SECONDS);
                    } finally {
                        lock.unlock();
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                count++;
                System.out.println(" count: " + count + " interrupting thread !!! ");
                // check with interrupt
                cThread.interrupt();
                System.out.println(" count: " + count + " interupted thread ? " + cThread.isInterrupted());

            }
        }, 1, 2, TimeUnit.SECONDS);

        final Object lock = new Object();
        synchronized (lock) {
            for (;;) {
                try {
                    lock.wait();
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                    break;
                }
            }
        }

        System.out.println("Finished!");
    }

}
