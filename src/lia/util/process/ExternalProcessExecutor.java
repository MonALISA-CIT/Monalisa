/*
 * Created on Oct 10, 2010
 */
package lia.util.process;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.util.MLProcess;
import lia.util.Utils;
import lia.util.threads.MLExecutorsFactory;

/**
 * Helper class for executing external processes with an optional timeout. All processes executed here are build
 * previously by a {@link ProcessBuilder}.
 * Optionally, the stderr/stdout are notified using a {@link ProcessNotifier} It will slowly replace {@link MLProcess}
 * 
 * @see ProcessBuilder
 * @see ProcessNotifier
 * @author ramiro
 */
class ExternalProcessExecutor {

    private static final Logger logger = Logger.getLogger(ExternalProcessExecutor.class.getName());

    static final ScheduledExecutorService executor;

    private static final Map<Long, InternalProcessDetails> procMap = new ConcurrentHashMap<Long, InternalProcessDetails>();

    private static final class InternalProcessDetails {

        final ExternalProcess externalProcess;

        volatile ScheduledFuture<?> timeoutTaskFuture;

        // these thre are alive as long as the process is alive
        final Future<String> stdErrFuture;

        final Future<String> stdOutFuture;

        final Future<?> procWaitFuture;

        InternalProcessDetails(ExternalProcess externalProcess, ScheduledFuture<?> timeoutTaskFuture,
                Future<String> stdErrFuture, Future<String> stdOutFuture, Future<?> procWaitFuture) {
            this.externalProcess = externalProcess;
            this.timeoutTaskFuture = timeoutTaskFuture;
            this.stdErrFuture = stdErrFuture;
            this.stdOutFuture = stdOutFuture;
            this.procWaitFuture = procWaitFuture;
        }

        /**
         * 
         */
        void cleanup() {
            Utils.cancelFutureIgnoreException(timeoutTaskFuture, false);

            // these taks may be blocked in I/O or waiting for other stuff
            Utils.cancelFutureIgnoreException(procWaitFuture, false);
            Utils.cancelFutureIgnoreException(stdOutFuture, false);
            Utils.cancelFutureIgnoreException(stdErrFuture, false);

            // log this after canceling; it might blow
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        "Clean up ALL Future tasks for external proccess. Emotions in scheduling. Process: "
                                + externalProcess);
            }
        }
    }

    static {

        try {
            // set a "reasonable" max thread count for lia.util.process.MAX_POOL_THREADS_COUNT
            if (AppConfig.setPropertyIfAbsent("lia.util.process.MAX_POOL_THREADS_COUNT", "100") == null) {
                logger.log(Level.FINE, "Setting default value for lia.util.process.MAX_POOL_THREADS_COUNT to "
                        + AppConfig.getProperty("lia.util.process.MAX_POOL_THREADS_COUNT"));
            } else {
                logger.log(Level.FINE, "Using predefined value for lia.util.process.MAX_POOL_THREADS_COUNT to "
                        + AppConfig.getProperty("lia.util.process.MAX_POOL_THREADS_COUNT"));
            }
            executor = MLExecutorsFactory.getScheduledExecutorService("lia.util.process");
        } catch (Throwable t) {
            logger.log(Level.SEVERE,
                    "Unable to instantiate lia.util.process executor. External processes will not work", t);
            throw new RuntimeException(
                    "Unable to instantiate lia.util.process executor. External processes will not work", t);
        }

    }

    private static String getExecWrapper() {
        final boolean doNotUseWrapper = AppConfig.getb("lia.util.MLProcess.doNotUseWrapper", false);

        if (doNotUseWrapper) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "lia.util.MLProcess.doNotUseWrapper is: " + doNotUseWrapper);
            }
            return null;
        }

        String cWrapper = null;

        try {
            String mlHOME = null;
            try {
                mlHOME = AppConfig.getProperty("MonaLisa_HOME", "");
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, " [ getWrapper ] Unable to determine mlHOME. Cause: " + t);
                }
                mlHOME = "";
            }
            try {
                cWrapper = AppConfig.getProperty("lia.util.MLProcess.CMD_WRAPPER", mlHOME + "/Service/CMD/cmd_run.sh");
            } catch (Throwable t) {
                cWrapper = mlHOME + "/Service/CMD/cmd_run.sh";
            }
            File cwf = new File(cWrapper);
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, " [ getWrapper ] possible wrapper: " + cwf);
            }
            if (!cwf.exists() || !cwf.canRead()) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, " [ getWrapper ] possible wrapper: " + cwf
                            + " cannot be read or do not exist");
                }
                cWrapper = null;
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ ExternalProcessExecutor ] Cannot use command wrapper", t);
            }
            cWrapper = null;
        }

        return cWrapper;
    }

    /**
     * @param externalProcessBuilder
     * @return
     * @throws IOException
     *             if the process cannot start
     */
    static ExternalProcess start(ExternalProcessBuilder externalProcessBuilder) throws IOException {
        final ProcessBuilder origBuilder = externalProcessBuilder.processBuilder;
        Process p = null;
        final String cWrapper = getExecWrapper();

        if (logger.isLoggable(Level.FINER)) {
            if (cWrapper != null) {
                logger.log(Level.FINER, "[ ExternalProcessExecutor ] using CMD_WRAPPER [" + cWrapper + "]");
            } else {
                logger.log(Level.FINER, "[ ExternalProcessExecutor ] not using CMD_WRAPPER");
            }
        }

        final List<String> cmd = origBuilder.command();
        final String shortCmd = cmd.get(0);

        final List<String> newCmd = new ArrayList<String>(cmd.size() + 1);
        if (cWrapper != null) {
            newCmd.add(cWrapper);
        }
        newCmd.addAll(cmd);

        ProcessBuilder iBuilder = new ProcessBuilder(newCmd);
        iBuilder.directory(origBuilder.directory()).redirectErrorStream(origBuilder.redirectErrorStream());
        Map<String, String> origEnv = origBuilder.environment();
        final Map<String, String> newEnv = iBuilder.environment();
        newEnv.putAll(origEnv);

        for (final Iterator<String> it = newEnv.keySet().iterator(); it.hasNext();) {
            final String key = it.next();
            if (!origEnv.containsKey(key)) {
                it.remove();
            }
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [ ExternalProcessExecutor ] executing cmd: '" + iBuilder.command()
                    + "' OrigCmd: ' " + origBuilder.command() + "'");
        }
        // if we throw exception here (e.g. the script is not found) nothing has to be cleaned up
        p = iBuilder.start();

        return decorateProcess(p, shortCmd, externalProcessBuilder);
    }

    private static ExternalProcess decorateProcess(Process p, String shortCmd,
            ExternalProcessBuilder externalProcessBuilder) {
        final long timeoutNanos = externalProcessBuilder.getTimeout(TimeUnit.NANOSECONDS);
        ScheduledFuture<?> timeoutTask = null;
        Future<?> procWaitFuture = null;
        Future<String> stdErrFuture = null;
        Future<String> stdOutFuture = null;
        ExternalProcess retProcVal = null;

        boolean bSchedOk = false;
        try {
            retProcVal = new ExternalProcess(p, shortCmd, externalProcessBuilder.notifier(),
                    externalProcessBuilder.returnOutputOnExit());

            ////////////////////////////////////////////////////////////////////////////////////////
            // KEEP THIS ORDER!
            //
            // Submit the I/O tasks first!
            //
            // Otherwise you'll get a nice BadFileDescriptorException for short lived tasks
            //
            /////////////////////////////////////////////////////////////////////////////////////////

            stdOutFuture = executor.submit(new StdoutReaderTask(retProcVal));
            stdErrFuture = executor.submit(new StderrReaderTask(retProcVal));
            procWaitFuture = executor.submit(new ProcessExitWaitTask(retProcVal));

            //////////////////////////////////////////////////////////
            //END KEEP THIS ORDER. Start the stream readers first
            //////////////////////////////////////////////////////////

            // we got the internal PID
            if (timeoutNanos > 0) {
                // schedule the timeout
                timeoutTask = executor
                        .schedule(new ProcessWatchdogTask(retProcVal), timeoutNanos, TimeUnit.NANOSECONDS);
            }

            procMap.put(retProcVal.getInternalPid(), new InternalProcessDetails(retProcVal, timeoutTask, stdErrFuture,
                    stdOutFuture, procWaitFuture));

            // start the feeders
            bSchedOk = true;
            return retProcVal;
        } finally {
            if (!bSchedOk) {// smth went wrong; we love to throw exceptions
                Utils.cancelFutureIgnoreException(timeoutTask, false);

                // these taks may be blocked in I/O or waiting for other stuff
                Utils.cancelFutureIgnoreException(procWaitFuture, true);
                Utils.cancelFutureIgnoreException(stdOutFuture, true);
                Utils.cancelFutureIgnoreException(stdErrFuture, true);

                // log this after canceling; it might blow
                logger.log(Level.FINE,
                        "Clean up ALL Future tasks for external proccess. Emotions in scheduling. Process: "
                                + externalProcessBuilder.command());
            }
        }

    }

    static void timedOut(Long id) {
        final InternalProcessDetails pd = procMap.get(id);
        if (pd != null) {
            try {
                Utils.cancelFutureIgnoreException(pd.procWaitFuture, true);
                Utils.cancelFutureIgnoreException(pd.stdOutFuture, true);
                Utils.cancelFutureIgnoreException(pd.stdErrFuture, true);
                pd.cleanup();
                try {
                    pd.externalProcess.destroy();
                } catch (Throwable ignore) {
                }

            } catch (Throwable t) {
                logger.log(Level.WARNING, " Got exception notifying the wait task. Cause: ", t);
            }
        }
    }

    /**
     * @param id
     */
    static void cleanup(Long id) {
        if (id == null) {
            return;
        }
        final InternalProcessDetails pd = procMap.remove(id);
        if (pd != null) {
            pd.cleanup();
        }
    }

    static String getStdout(Long id) {
        if (id == null) {
            return "";
        }
        final InternalProcessDetails pd = procMap.get(id);
        if (pd != null) {
            try {
                return pd.stdOutFuture.get(30, TimeUnit.SECONDS);
            } catch (CancellationException ce) {
                // time out
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " Got CancellationException waiting stdout for pid: " + id + " Cause: ", ce);
                }
            } catch (InterruptedException ie) {
                // time out
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " Got InterruptedException waiting stdout for pid: " + id + " Cause: ", ie);
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Got exception waiting stdout for pid: " + id + " Cause: ", t);
            }
        }

        return "";
    }

    static String getStderr(Long id) {
        if (id == null) {
            return "";
        }
        final InternalProcessDetails pd = procMap.get(id);
        if (pd != null) {
            try {
                return pd.stdErrFuture.get(30, TimeUnit.SECONDS);
            } catch (CancellationException ce) {
                // time out
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.WARNING, " Got CancellationException waiting stderr for pid: " + id + " Cause: ",
                            ce);
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Got exception waiting stderr for pid: " + id + " Cause: ", t);
            }
        }

        return "";
    }

    /**
     * @param id
     * @param newTimeout
     * @param unit
     */
    public static void renewTimeout(Long id, long newTimeout, TimeUnit unit) {

        //
        // TODO the synchronization here is a litlle loose, but it's not unsafe
        //
        // this operation should be synchronized with process ending
        // we got the advantage that process ID is always increasing; even if this will be executed in the future
        // and the process already finishes; it will no longer be in the map.
        //

        final InternalProcessDetails pd = procMap.get(id);
        if (pd != null) {
            pd.timeoutTaskFuture.cancel(false);

            if (newTimeout > 0) {
                // schedule the timeout
                pd.timeoutTaskFuture = executor.schedule(new ProcessWatchdogTask(pd.externalProcess), newTimeout, unit);
            }
        }
    }
}
