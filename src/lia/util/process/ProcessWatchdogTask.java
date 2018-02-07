/*
 * $Id: ProcessWatchdogTask.java 7419 2013-10-16 12:56:15Z ramiro $
 * 
 * Created on Oct 10, 2010
 */
package lia.util.process;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 * @author ramiro
 */
class ProcessWatchdogTask implements Runnable {
    private static final Logger logger = Logger.getLogger(ProcessWatchdogTask.class.getName());

    final ExternalProcess p;

    ProcessWatchdogTask(ExternalProcess p) {
        //TODO assert procWrapper != null && procWrapper.p != null
        this.p = p;
    }

    @Override
    public void run() {
        final Long id = p.getInternalPid();
        final String logPrefix = "ProcessWatchdogTask '" + p + "'";
        logger.log(Level.WARNING, logPrefix + " . Process time out. sending SIGTERM to process.");

        try {
            p.notifyTimedOut();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "ProcessWatchdogTask '" + p
                    + "' exception notifyTimedOut for the process. Cause: ", t);
        }

        try {
            ExternalProcessExecutor.timedOut(id);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "ProcessWatchdogTask '" + p + "' exception notifyTimedOut for executor. Cause: ",
                    t);
        }
    }
}