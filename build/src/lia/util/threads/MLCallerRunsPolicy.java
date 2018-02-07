/*
 * Created on Feb 2, 2011
 */
package lia.util.threads;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.Utils;

/**
 * Executes the task in the same thread as the scheduler
 *
 * @author ramiro
 */
final class MLCallerRunsPolicy extends CallerRunsPolicy {

    // in nanos
    private volatile long lastTriggered;

    final String name;

    final boolean hasLogger;

    final Logger logger;

    MLCallerRunsPolicy(final String name, final Logger logger) {
        this.name = name;
        this.logger = logger;
        hasLogger = (this.logger != null);
        lastTriggered = 0;
    }

    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {

        // try to get the stack trace as quick as possible
        final boolean isFiner = logger.isLoggable(Level.FINE);
        if (hasLogger && isFiner) {
            try {
                final Map<Thread, StackTraceElement[]> m = Thread.getAllStackTraces();
                StringBuilder sbf = new StringBuilder(16384);
                sbf.append("\n\n JVM Thread DUMP: \n\n");

                for (final Map.Entry<Thread, StackTraceElement[]> entry : m.entrySet()) {
                    final Thread t = entry.getKey();
                    final StackTraceElement[] ste = entry.getValue();
                    final int stackLen = ste.length;
                    sbf.append(t.getName()).append("\n");
                    for (int i = 0; i < stackLen; i++) {
                        sbf.append("\t").append(ste[i]).append("\n");
                    }
                }
                sbf.append("\n\n ********** END JVM Thread DUMP ******** \n");
                logger.log(Level.FINER, sbf.toString());
            } catch (Throwable ignoreInCaseOfProblems) {
            }
        }

        final long lt = lastTriggered;
        final long nowNanos = Utils.nanoNow();

        final long dtSeconds = TimeUnit.NANOSECONDS.toSeconds(nowNanos - lt);

        StringBuilder sb = new StringBuilder(1024);
        sb.append("\n\n [ MLCallerRunsPolicy ] [ HANDLED ] The ThP ").append(name).append(" has reached the limit ... ");
        sb.append("\n\n Current details: runnable.toString(): ").append(r).append(" in worker: ").append(Thread.currentThread().getName());
        sb.append("\n ActiveCount: ").append(e.getActiveCount()).append(" largestPoolSize: ").append(e.getLargestPoolSize());
        sb.append("\n MaximumPoolSize: ").append(e.getMaximumPoolSize()).append(" PoolSize: ").append(e.getPoolSize());
        sb.append("\n Will use MLCallerRunsPolicy");

        super.rejectedExecution(r, e);

        // try some magic! "sleep" delay
        if (lt > 0 && dtSeconds <= 120) {
            try {
                final long dtSleep = 20L + (long) (170d * Math.random());
                Thread.sleep(dtSleep);
                sb.append("\bSlept for: ").append(dtSleep).append(" ms after task execution\n");
            } catch (Throwable t) {
            }
        }

        lastTriggered = nowNanos;

        if (hasLogger) {
            logger.log(Level.WARNING, sb.toString());
        } else {
            System.err.println(sb.toString());
        }

    }
}