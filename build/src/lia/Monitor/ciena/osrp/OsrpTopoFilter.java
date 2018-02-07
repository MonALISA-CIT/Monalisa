/*
 * $Id: OsrpTopoFilter.java 7419 2013-10-16 12:56:15Z ramiro $
 * 
 * Created on Nov 9, 2007
 *
 */
package lia.Monitor.ciena.osrp;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Filters.GenericMLFilter;
import lia.Monitor.ciena.osrp.topo.OsrpTopoHolder;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.monPredicate;
import lia.util.Utils;

public class OsrpTopoFilter extends GenericMLFilter {
    /**
     * 
     */
    private static final long serialVersionUID = 2368187665869424173L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(OsrpTopoFilter.class.getName());

    /**
     * execution rate in ms
     */
    private static final AtomicLong SLEEP_TIME = new AtomicLong(20 * 1000);

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
            slTime = AppConfig.getl("lia.Monitor.ciena.osrp.OsrpTopoFilter.execDelay", slTime) * 1000;
        } catch (Throwable t) {
            slTime = SLEEP_TIME.get() / 1000L;
        }

        SLEEP_TIME.set(slTime);
    }

    public OsrpTopoFilter(String farmName) {
        super(farmName);

    }

    @Override
    public Object expressResults() {
        OsrpTL1FetcherTask.getInstance();
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER,
                    " [ OsrpTopoFilter ] [ expressResults ] All osrpNodes ... " + OsrpTopoHolder.getAllOsrpNodeIDs());

        }

        byte[] ret = null;

        try {
            ret = Utils.writeObject(OsrpTopoHolder.getAllOsrpTL1Topo());
        } catch (Throwable t) {
            logger.log(Level.WARNING,
                    " [ OsrpTopoFilter ] [ expressResults ] exception trying to fetch the OsrpTL1Topo", t);
        }

        return ret;
    }

    @Override
    public monPredicate[] getFilterPred() {
        return null;
    }

    @Override
    public String getName() {
        // TODO Auto-generated method stub
        return "OsrpTopoFilter";
    }

    @Override
    public long getSleepTime() {
        return SLEEP_TIME.get();
    }

    @Override
    public void notifyResult(Object o) {
    }

}
