/*
 * $Id: GlimmerFiberCutSim.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.util.oswitch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.util.Utils;
import lia.util.telnet.OSTelnet;
import lia.util.telnet.OSTelnetFactory;

/**
 * Helper class to make and delete fdx cross connects inside glimmerglass
 * 
 * Works with AppConfig from MonALISA. You need FarmMonitor.jar in your
 * classpath.
 * 
 * <br>
 * The config (ml.properties) accepted looks like this:
 * <pre>
 * 
 * #whether the ports should be cross connected or not when the simulation finishes
 * DEFAULT_UP=true
 * 
 * #every cross is defined by a pair of ports separated by '-'
 * crossNames= 02-05, 13-15, 22-23
 * 
 * #
 * #for every of the cross defined above specify how long should the cross 
 * #be kept up or down:
 * #sport-dport.simTimes = crossUpTime, crossDownTime, crossUpTime, crossDownTime, etc
 * #
 * 02-05.simTimes = 1300, 6000, 2000, 7000, 1200
 * 13-15.simTimes = 1300, 6000, 2000, 7000, 1200
 * 22-23.simTimes = 1300, 6000, 2000, 7000, 1200
 * 
 * #
 * #whether the real TL1 commands are sent or not to the switch
 * #it SYM_ONLY=true the real commands are not sent
 * #
 * SYM_ONLY=false
 * </pre> 
 * 
 * 
 * A simple example:
 * java -cp lib/FarmMonitor.jar:lib/backport-util-concurrent.jar:src/. -Dlia.Monitor.ConfigURL=file:`pwd`/app.conf lia.util.oswitch.GlimmerFiberCutSim
 *  
 * 
 * TODO: 
 *  
 *  <ul>
 *   <li> add shutdown hook ( if Ctrl + C is hit ... try to leave the cross in consistent state, depending on DEFAULT_UP flag )
 *   <li> add eventually a loop mode
 *   <li> watcher for ml.properties ( reload the simTimes only after simulation step is finished )
 *   <li> further improvements (th pool ... etc ). It took longer to right this "usage" almost javadoc, then the code itself 
 *   <li> go get a coffee ;) .... I'll go right now (2008-12-10 15:05:57) :)
 *  </ul> 
 *  
 *  
 * @author ramiro
 */
public class GlimmerFiberCutSim {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(GlimmerFiberCutSim.class.getName());

    private static final OSTelnet switchConnection;

    static {
        OSTelnet tmpTelnet = null;

        try {

            // sw type can be put out as param
            tmpTelnet = OSTelnetFactory.getControlInstance(OSTelnet.GLIMMERGLASS);
        } catch (Throwable t) {
            logger.log(Level.SEVERE,
                    "Unable to init the connection to the optical switch. The program will stop. Cause: ", t);
            System.exit(2);
        }

        if (tmpTelnet == null) {
            logger.log(Level.SEVERE,
                    "Unable to init the connection to the optical switch. Connection is null. Errors should heve been provided. Will exit");
            System.exit(3);
        }

        if (!tmpTelnet.isConnected() || !tmpTelnet.isActive()) {
            logger.log(Level.SEVERE,
                    "Unable to init the connection to the optical switch. Connection is null. Errors should heve been provided. Will exit");
            System.exit(4);
        }

        switchConnection = tmpTelnet;
    }

    private static final boolean SYM_ONLY = AppConfig.getb("SYM_ONLY", true);
    private static final boolean DEFAULT_UP = AppConfig.getb("DEFAULT_UP", true);

    private static final AtomicLong DT_CNX_TOTAL = new AtomicLong(0);

    private static final class WorkerSimThread extends Thread {

        private final String myName;

        private final CountDownLatch startSignal;

        private final CountDownLatch doneSignal;

        // in nano seconds
        private long[] upTimes;

        private long[] downTimes;

        long cnxDt = 0;
        long sleepDt = 0;

        private boolean makeLast = false;

        private WorkerSimThread(final String name, final CountDownLatch startSignal, final CountDownLatch doneSignal) {

            if (name == null) {
                throw new NullPointerException("name cannot be null for a worker");
            }

            if (name.trim().length() == 0) {
                throw new IllegalArgumentException(" name cannot be empty for a worker ");
            }

            myName = name;

            setName(" Worker [ " + myName + " ] ");

            this.startSignal = startSignal;
            this.doneSignal = doneSignal;

            loadMyConfig();
        }

        private final void loadMyConfig() {

            final String propName = myName + ".simTimes";

            final String[] times = AppConfig.getVectorProperty(propName);
            if ((times == null) || (times.length == 0)) {
                throw new IllegalArgumentException(" the property " + propName + " is not correctly set");
            }

            final ArrayList<Long> upTimesList = new ArrayList<Long>();
            final ArrayList<Long> downTimesList = new ArrayList<Long>();

            final int len = times.length;

            for (int idx = 0; idx < len; idx++) {
                final Long upTime = Long.valueOf(times[idx]);
                if (upTime.longValue() > 0) {
                    upTimesList.add(upTime);
                } else {
                    System.out.println("Ignoring upTime. It should be > 0 for: " + myName);
                }

                idx++;

                if (idx < len) {
                    final Long downTime = Long.valueOf(times[idx]);
                    if (downTime.longValue() > 0) {
                        downTimesList.add(downTime);
                    } else {
                        System.out.println("Ignoring downTime. It should be > 0 for: " + myName);
                    }
                }
            }

            this.upTimes = new long[upTimesList.size()];
            int i = 0;
            for (Long l : upTimesList) {
                this.upTimes[i++] = l.longValue();
            }

            this.downTimes = new long[downTimesList.size()];
            i = 0;
            for (Long l : downTimesList) {
                this.downTimes[i++] = l.longValue();
            }

        }

        private final void makeCnx() throws Exception {
            makeLast = true;
            final long nanoStart = Utils.nanoNow();
            if (!SYM_ONLY) {
                switchConnection.makeFDXConn(myName);
            }

            final long dtNano = Utils.nanoNow() - nanoStart;
            cnxDt += dtNano;
            logger.log(Level.INFO,
                    " [ " + myName + " ] make fdx cnx finished. took: " + TimeUnit.NANOSECONDS.toMillis(dtNano) + " ms");
        }

        private final void delCnx() throws Exception {
            makeLast = false;
            final long nanoStart = Utils.nanoNow();
            if (!SYM_ONLY) {
                switchConnection.deleteFDXConn(myName);
            }

            final long dtNano = Utils.nanoNow() - nanoStart;
            cnxDt += dtNano;
            logger.log(Level.INFO,
                    " [ " + myName + " ] del fdx cnx finished. took: " + TimeUnit.NANOSECONDS.toMillis(dtNano) + " ms");
        }

        @Override
        public void run() {

            long nanoStart = 0;
            try {

                logger.log(
                        Level.INFO,
                        myName + " upTimes: " + Arrays.toString(upTimes) + "\n downTimes: "
                                + Arrays.toString(downTimes));
                // wait for all the threads to start
                int i = 0;

                final long minLen = (upTimes.length > downTimes.length) ? downTimes.length : upTimes.length;
                startSignal.await();

                nanoStart = Utils.nanoNow();

                for (;;) {
                    if (i >= minLen) {
                        break;
                    }

                    final long sleepUp = upTimes[i];
                    final long sleepDown = downTimes[i];
                    i++;

                    makeCnx();
                    try {
                        sleepDt += sleepUp;
                        Thread.sleep(sleepUp);
                    } catch (InterruptedException ie) {
                        logger.log(Level.WARNING, myName + " got interrupted exception ", ie);
                        Thread.interrupted();
                    }

                    delCnx();
                    try {
                        sleepDt += sleepDown;
                        Thread.sleep(sleepDown);
                    } catch (InterruptedException ie) {
                        logger.log(Level.WARNING, myName + " got interrupted exception ", ie);
                        Thread.interrupted();
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.SEVERE, " The worker for: " + myName + " got exception. ", t);
            } finally {
                // wait for all the others to finish
                try {
                    if (DEFAULT_UP) {
                        makeCnx();
                    } else {
                        if (makeLast) {
                            delCnx();
                        }
                    }
                } catch (Throwable t1) {
                    logger.log(Level.WARNING, myName + " Got exception making last cnx ", t1);
                }

                final long dt = Utils.nanoNow() - nanoStart;
                logger.log(Level.INFO, " [ " + myName + " ] finished in: " + TimeUnit.NANOSECONDS.toMillis(dt)
                        + " millis. dtCnx: " + TimeUnit.NANOSECONDS.toMillis(cnxDt) + " ms. dtSleep: " + (sleepDt)
                        + " ms");
                DT_CNX_TOTAL.addAndGet(cnxDt);
                doneSignal.countDown();
            }
        }
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws InterruptedException {
        // TODO Auto-generated method stub
        final String[] crossNames = AppConfig.getVectorProperty("crossNames");

        if ((crossNames == null) || (crossNames.length == 0)) {
            System.err.println("Define crossNames in ml.properties file");
        }

        final int crossCount = crossNames.length;
        logger.log(Level.INFO, "\n\n\n =========== SYM_ONLY: " + SYM_ONLY + " ============= DEFAULT_UP: " + DEFAULT_UP
                + " \n\n");

        final CountDownLatch cdlStart = new CountDownLatch(1);
        final CountDownLatch cdlStop = new CountDownLatch(crossCount);

        for (int i = 0; i < crossCount; i++) {
            new WorkerSimThread(crossNames[i], cdlStart, cdlStop).start();
        }

        cdlStart.countDown();

        final long nanoStartSym = Utils.nanoNow();
        logger.log(Level.INFO, "\n\n [ MAIN THREAD ] Simulation started \n\n");

        cdlStop.await();

        logger.log(Level.INFO,
                " Simulation finished in: " + TimeUnit.NANOSECONDS.toMillis(Utils.nanoNow() - nanoStartSym)
                        + " ms. DT_CNX_TOTAL: " + TimeUnit.NANOSECONDS.toMillis(DT_CNX_TOTAL.get()) + " ms");
    }

}
