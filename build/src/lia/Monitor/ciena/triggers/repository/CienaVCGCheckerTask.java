/*
 * Created on Jan 16, 2010
 */
package lia.Monitor.ciena.triggers.repository;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 * @author ramiro
 * 
 */
class CienaVCGCheckerTask implements Runnable {

    private static final Logger logger = Logger.getLogger(CienaVCGCheckerTask.class.getName());

    final CienaVCGAlarm vcgAlarm;

    CienaVCGCheckerTask(CienaVCGAlarm vcgAlarm) {
        if (vcgAlarm == null) {
            throw new NullPointerException("Null CienaVCGConfigEntry");
        }

        this.vcgAlarm = vcgAlarm;
    }

    @Override
    public void run() {
        final boolean isFine = logger.isLoggable(Level.FINE);
        final boolean isFiner = (isFine || logger.isLoggable(Level.FINER));
        final boolean isFinest = (isFiner || logger.isLoggable(Level.FINEST));

        if (isFinest) {
            logger.log(Level.FINEST, "Starting CienaVCGCheckerTask for: " + vcgAlarm);
        }
    }

}
