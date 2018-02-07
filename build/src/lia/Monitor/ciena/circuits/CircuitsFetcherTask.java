/*
 * $Id: CircuitsFetcherTask.java 7419 2013-10-16 12:56:15Z ramiro $
 * 
 * Created on Dec 10, 2007
 */
package lia.Monitor.ciena.circuits;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ciena.circuits.tl1.TL1Util;
import lia.Monitor.ciena.circuits.topo.CircuitsHolder;
import lia.Monitor.ciena.circuits.topo.tl1.TL1CDCICircuitsHolder;
import lia.Monitor.ciena.tl1.TL1Response;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.util.threads.MonALISAExecutors;

/**
 *
 * @author ramiro
 */
public class CircuitsFetcherTask implements Runnable {

    private static final Logger logger = Logger.getLogger(CircuitsFetcherTask.class.getName());
    private static CircuitsFetcherTask _thisInstance;
    private static final AtomicLong delay = new AtomicLong(45);
    private static final ScheduledExecutorService execService = MonALISAExecutors.getMLHelperExecutor();

    private static final AtomicReference<String> myNameReference = new AtomicReference<String>();

    static {
        reloadConf();
        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                reloadConf();
            }
        });
    }

    private static final void appendTL1ResponsesToBuffer(final StringBuilder sb, final TL1Response[] responses) {
        for (int i = 0; i < responses.length; i++) {
            sb.append("\n[").append(i + 1).append("]: ").append(responses[i]);
        }
    }

    @Override
    public void run() {

        final long sTime = System.currentTimeMillis();
        long dtCiena = -1;

        try {

            final String myName = myNameReference.get();

            if ((myName == null) || (myName.length() == 0)) {
                logger.log(
                        Level.INFO,
                        "The CircuitsFetcherTask cannot determine local name. You have to set lia.Monitor.ciena.circuits.CircuitsFecherTask.myName in ml.properties");
                return;
            }

            final TL1Response[] stspcs = TL1Util.getAllSTSPCs();
            final TL1Response[] sncs = TL1Util.getAllSNCSTSPCs();
            final TL1Response[] sncsRoutes = TL1Util.getAllSNCRoutes();
            //            final TL1Response[] vcgs = TL1Util.getAllVCGs();
            final TL1Response[] vcgs = new TL1Response[] {};
            final TL1Response[] crss = TL1Util.getAllCRSs();
            final TL1Response[] gtps = TL1Util.getAllGTPs();

            dtCiena = System.currentTimeMillis() - sTime;

            if (logger.isLoggable(Level.FINEST)) {
                StringBuilder sb = new StringBuilder(4096);
                sb.append("\n\n [ CIENA ] [ CircuitsFetcherTask ] Received from CD/CI following responses:");
                sb.append("\n\n SNCs: ").append(sncs.length).append("\n");
                appendTL1ResponsesToBuffer(sb, sncs);
                sb.append("\n\n STSPCs: ").append(stspcs.length).append("\n");
                appendTL1ResponsesToBuffer(sb, stspcs);
                sb.append("\n\n GTPs: ").append(gtps.length).append("\n");
                appendTL1ResponsesToBuffer(sb, gtps);
                sb.append("\n\n SNCs Routes: ").append(sncsRoutes.length).append("\n");
                appendTL1ResponsesToBuffer(sb, sncsRoutes);
                sb.append("\n\n VCGs: ").append(vcgs.length).append("\n");
                appendTL1ResponsesToBuffer(sb, vcgs);
                sb.append("\n\n CRSs: ").append(crss.length).append("\n");
                appendTL1ResponsesToBuffer(sb, crss);
                sb.append("\n");
                logger.log(Level.FINEST, sb.toString());
            }

            final TL1CDCICircuitsHolder tl1CircuitsTopo = new TL1CDCICircuitsHolder(myName, stspcs, sncs, sncsRoutes,
                    gtps, vcgs, crss);
            CircuitsHolder.getInstance().notifyTL1Responses(new TL1CDCICircuitsHolder[] { tl1CircuitsTopo });

        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ CIENA ] [ CircuitsFetcherTask ] got exception in main loop", t);
        } finally {
            final long reSched = (delay.get() * 1000) + Math.round((500 * Math.random()));
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "[ CIENA ] [ CircuitsFetcherTask ] DT: " + (System.currentTimeMillis() - sTime)
                        + " ms. DTCiena = " + dtCiena + " ms ... Rescheduling ... DT = " + reSched + " ms");
            }
            execService.schedule(_thisInstance, reSched, TimeUnit.MILLISECONDS);
        }

    }

    public static synchronized final CircuitsFetcherTask getInstance() {
        if (_thisInstance == null) {
            _thisInstance = new CircuitsFetcherTask();
            execService.schedule(_thisInstance, (delay.get() * 1000) + Math.round((2 * Math.random())),
                    TimeUnit.MILLISECONDS);
        }

        return _thisInstance;
    }

    private static final void reloadConf() {
        final long cDelay = AppConfig.getl("lia.Monitor.ciena.circuits.CircuitsFecherTask.delayExec", delay.get());
        if (cDelay < 15) {
            delay.set(15);
        } else {
            delay.set(cDelay);
        }

        myNameReference.set(AppConfig.getProperty("lia.Monitor.ciena.circuits.CircuitsFecherTask.myName"));
    }

    public static void main(String[] args) {
        final Object lock = new Object();
        getInstance();

        for (;;) {
            try {
                synchronized (lock) {
                    lock.wait();
                }
            } catch (InterruptedException ie) {
                ie.printStackTrace();
                Thread.interrupted();
            }
        }
    }
}
