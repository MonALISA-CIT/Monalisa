/*
 * $Id: CienaSNCFilter.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.ciena.circuits;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Filters.GenericMLFilter;
import lia.Monitor.ciena.circuits.topo.CircuitsHolder;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.monPredicate;
import lia.util.Utils;

/**
 *
 * @author ramiro
 */
public class CienaSNCFilter extends GenericMLFilter {

    /**
     * 
     */
    private static final long serialVersionUID = 1428818528174717140L;
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(CienaSNCFilter.class.getName());
    /**
     * execution rate in ms
     */
    private static final AtomicLong SLEEP_TIME = new AtomicLong(40 * 1000);

    static {
        AppConfig.addNotifier(new AppConfigChangeListener() {
            @Override
            public void notifyAppConfigChanged() {
                reloadConf();
            }
        });
    }

    private static final void reloadConf() {
        long slTime = SLEEP_TIME.get() / 1000L;

        try {
            slTime = AppConfig.getl("lia.Monitor.ciena.circuits.CienaSNCFilter.execDelay", slTime) * 1000;
        } catch (Throwable t) {
            slTime = SLEEP_TIME.get() / 1000L;
        }

        SLEEP_TIME.set(slTime);
    }

    public CienaSNCFilter(String farmName) {
        super(farmName);
    }

    @Override
    public String getName() {
        // TODO Auto-generated method stub
        return "CienaSNCFilter";
    }

    @Override
    public long getSleepTime() {
        return SLEEP_TIME.get();
    }

    @Override
    public monPredicate[] getFilterPred() {
        return null;
    }

    /**
     * @param o  
     */
    @Override
    public void notifyResult(Object o) {
        //not used
    }

    @Override
    public Object expressResults() {
        CircuitsFetcherTask.getInstance();

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [ CienaSNCFilter ] [ expressResults ] All nodes ... "
                    + CircuitsHolder.getInstance().getAllNodeNames());
        }

        byte[] ret = null;

        try {
            long t1 = System.nanoTime();
            ret = Utils.writeCompressedObject(CircuitsHolder.getInstance().getAllTL1Circuits());
            if (logger.isLoggable(Level.FINE)) {
                logger.log(
                        Level.FINE,
                        "[ OsrpTopoFilter ] writeCompressedObject sncs. DT = "
                                + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t1));
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING,
                    " [ OsrpTopoFilter ] [ expressResults ] exception trying to fetch the OsrpTL1Topo", t);
        }

        return ret;

    }

}
