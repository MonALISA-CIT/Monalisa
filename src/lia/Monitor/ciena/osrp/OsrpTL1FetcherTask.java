/*
 * $Id: OsrpTL1FetcherTask.java 7419 2013-10-16 12:56:15Z ramiro $
 * 
 * Created on Oct 29, 2007
 * 
 */
package lia.Monitor.ciena.osrp;

import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ciena.osrp.tl1.OsrpTL1Response;
import lia.Monitor.ciena.osrp.tl1.OsrpTL1Topo;
import lia.Monitor.ciena.osrp.tl1.TL1Util;
import lia.Monitor.ciena.osrp.topo.OsrpTopoHolder;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.util.threads.MonALISAExecutors;

/**
 * 
 * @author ramiro
 * 
 */
public class OsrpTL1FetcherTask implements Runnable {

    private static final Logger logger = Logger.getLogger(OsrpTL1FetcherTask.class.getName());

    private static final AtomicLong OSRP_TL1FETCHER_DELAY = new AtomicLong(75);

    private static final ScheduledExecutorService execService = MonALISAExecutors.getMLHelperExecutor();

    private static OsrpTL1FetcherTask _thisInstance;

    static {
        reloadConf();
        AppConfig.addNotifier(new AppConfigChangeListener() {
            @Override
            public void notifyAppConfigChanged() {
                reloadConf();
            }
        });
    }

    private OsrpTL1FetcherTask() {
    }

    public static synchronized final OsrpTL1FetcherTask getInstance() {

        if (_thisInstance == null) {
            _thisInstance = new OsrpTL1FetcherTask();
            execService.schedule(_thisInstance, (OSRP_TL1FETCHER_DELAY.get() * 1000) + Math.round((2 * Math.random())),
                    TimeUnit.MILLISECONDS);
        }

        return _thisInstance;
    }

    private static final void reloadConf() {
        final long cDelay = AppConfig.getl("lia.Monitor.ciena.osrp.OsrpTL1FetcherTask.delayExec",
                OSRP_TL1FETCHER_DELAY.get());
        if (cDelay < 15) {
            OSRP_TL1FETCHER_DELAY.set(15);
        } else {
            OSRP_TL1FETCHER_DELAY.set(cDelay);
        }
    }

    @Override
    public void run() {

        final long sTime = System.currentTimeMillis();
        long dtCiena = -1;

        try {
            final OsrpTL1Response[] nodes = TL1Util.getAllOsrpNodes();
            final OsrpTL1Response[] ltps = TL1Util.getAllOsrpLtps();
            final OsrpTL1Response[] ctps = TL1Util.getAllOsrpCtps();
            final OsrpTL1Response[] routes = TL1Util.getAllRouteMetric();

            dtCiena = System.currentTimeMillis() - sTime;

            if (logger.isLoggable(Level.FINEST)) {
                StringBuilder sb = new StringBuilder();
                sb.append("\n [ CIENA ] [ OsrpTopoBuild ] Received from CD/CI following responses:");
                sb.append("\n nodes: ").append(Arrays.toString(nodes));
                sb.append("\n ltps: ").append(Arrays.toString(ltps));
                sb.append("\n ctps: ").append(Arrays.toString(ctps));
                sb.append("\n routes: ").append(Arrays.toString(routes));
                sb.append("\n");
                logger.log(Level.FINEST, sb.toString());
            }

            final OsrpTL1Topo osrpTL1Topo = new OsrpTL1Topo(nodes, ltps, ctps, routes);
            OsrpTopoHolder.notifyTL1Responses(new OsrpTL1Topo[] { osrpTL1Topo });

        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ CIENA ] [ OsrpTopoBuilder ] [ HANDLED ] Exception in main loop. Cause: ", t);
        } finally {
            final long reSched = (OSRP_TL1FETCHER_DELAY.get() * 1000) + Math.round((500 * Math.random()));
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        "[ CIENA ] [ OsrpTL1FetcherTask ] Processing took: " + (System.currentTimeMillis() - sTime)
                                + " ms. DTCiena = " + dtCiena + " Rescheduling ... DT = " + reSched + " ms");
            }
            execService.schedule(_thisInstance, reSched, TimeUnit.MILLISECONDS);
        }
    }

    public static final void main(String[] args) throws Exception {
        final Object lock = new Object();
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
