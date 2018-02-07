/*
 * $Id: ExternalProcess.java 6931 2010-11-01 00:19:18Z ramiro $
 * Created on Oct 10, 2010
 */
package lia.util.process;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author ramiro
 */
public class ExternalProcess {

    private static final AtomicLong PROCESS_ID_SEQ = new AtomicLong(0L);

    final Lock lock = new ReentrantLock();

    final Condition finishedCondition = lock.newCondition();

    final AtomicBoolean finished = new AtomicBoolean(false);

    public enum ExecutorFinishStatus {
        TIMED_OUT, NORMAL, ERROR
    }

    public static final class ExitStatus {

        // process id
        final long id;

        final int extProcExitStatus;

        final ExecutorFinishStatus executorFinishStatus ;

        final String stdOut;

        final String stdErr;

        /**
         * @param id
         * @param extProcExitStatus
         * @param mlstatus
         * @param stdOut
         * @param stdErr
         */
        public ExitStatus(long id, int extProcExitStatus, ExecutorFinishStatus executorFinishStatus, String stdOut, String stdErr) {
            this.id = id;
            this.extProcExitStatus = extProcExitStatus;
            this.executorFinishStatus = executorFinishStatus;
            this.stdOut = stdOut;
            this.stdErr = stdErr;
        }

        
        
        public long getId() {
            return id;
        }
        
        public int getExtProcExitStatus() {
            return extProcExitStatus;
        }
        
        public ExecutorFinishStatus getExecutorFinishStatus() {
            return executorFinishStatus;
        }
        


        
        public String getStdOut() {
            return stdOut;
        }


        
        public String getStdErr() {
            return stdErr;
        }


        /*
         * (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("ExitStatus [id=").append(id).append(", extProcExitStatus=").append(extProcExitStatus).append(", executorFinishStatus=").append(executorFinishStatus).append(", stdOut=").append(stdOut).append(", stdErr=").append(stdErr).append("]");
            return builder.toString();
        }
    }

    final Long id;

    final String shortCmd;

    final Process p;

    final boolean saveOutput;

    final ProcessNotifier notifier;

    volatile int exitStatus;

    final AtomicBoolean timedOut = new AtomicBoolean(false);

    ExternalProcess(Process p, final String shortCmd, ProcessNotifier notifier, boolean saveOutput) {
        this.p = p;
        this.id = Long.valueOf(PROCESS_ID_SEQ.incrementAndGet());
        this.notifier = notifier;
        this.saveOutput = saveOutput;
        this.shortCmd = shortCmd;
    }

    public void destroy() {
//        Utils.closeIgnoringException(p.getInputStream());
//        Utils.closeIgnoringException(p.getOutputStream());
//        Utils.closeIgnoringException(p.getErrorStream());
        p.destroy();
    }

    /**
     * @return the internal process identifier; it is <b>not</b> related with the real PID from the OS
     */
    public Long getInternalPid() {
        return id;
    }

    /**
     * 
     * @return false if already notified; false otherwise
     */
    boolean notifyTimedOut() {
        return timedOut.compareAndSet(false, true);
    }

    boolean notifyProcessExit(int exitStatus) {
        lock.lock();
        final boolean bRet = finished.compareAndSet(false, true);

        try {
            if (bRet) {
                this.exitStatus = exitStatus;
            }
            finishedCondition.signal();
        } finally {
            lock.unlock();
        }

        return bRet;
    }

    public boolean processFinished() {
        return finished.get();
    }

    /**
     * 
     * <b>Note:</b> If {@code newTimeout} is 0; the process will never timeout.
     * 
     * @param newTimeout
     *            - the new value of the timeout starting from this moment on
     * @param unit
     */
    public void renewTimeout(long newTimeout, TimeUnit unit) {
        ExternalProcessExecutor.renewTimeout(id, newTimeout, unit);
    }

    public ExitStatus waitFor() throws InterruptedException {
        try {
            lock.lock();
            try {
                while (!finished.get()) {
                    finishedCondition.await(10, TimeUnit.SECONDS);
                }
            } finally {
                lock.unlock();
            }


            final String stdout = ExternalProcessExecutor.getStdout(id);
            final String stderr = ExternalProcessExecutor.getStderr(id);
            final ExecutorFinishStatus execFinishStatus = (timedOut.get()) ? ExecutorFinishStatus.TIMED_OUT : (exitStatus == 0) ? ExecutorFinishStatus.NORMAL : ExecutorFinishStatus.ERROR;
            try {
                destroy();
            } catch (Throwable ignore) {
            }

            return new ExitStatus(id, exitStatus, execFinishStatus, stdout, stderr);
        } finally {
            ExternalProcessExecutor.cleanup(this.id);
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ExternalProcess [id=").append(id).append(", shortCmd=").append(shortCmd).append(", saveOutput=").append(saveOutput).append(", exitStatus=").append(exitStatus).append(", timedOut=").append(timedOut).append("]");
        return builder.toString();
    }

}
