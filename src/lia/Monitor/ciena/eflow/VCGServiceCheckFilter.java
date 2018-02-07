/*
 * Created on Jun 25, 2012
 */
package lia.Monitor.ciena.eflow;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.DataCache.Cache;
import lia.Monitor.Filters.GenericMLFilter;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.monPredicate;
import lia.util.Utils;

/**
 * Stupid Ciena BUG workaround. Monitors the EFLOWS and sends stupid results
 * This bug has at least 1000 years
 * 
 * @author ramiro
 */
public class VCGServiceCheckFilter extends GenericMLFilter implements EFlowStatsConsumer {

    /**
     * AHA !!
     */
    private static final long serialVersionUID = 1059223248514415016L;

    private static final Logger logger = Logger.getLogger(VCGServiceCheckFilter.class.getName());

    private static final String VCGServiceCheckFilter_NAME = "VCGServiceCheckFilter";

    private final BlockingQueue<EFlowStats> rdvQueue;

    // auto adjust the max flows which were notified
    private int maxNotifFlows = 5;

    private final AtomicReference<VCGServiceCheckFilterConfig> configPointer = new AtomicReference<VCGServiceCheckFilterConfig>();

    private static final class ConfigListener implements AppConfigChangeListener {

        private final VCGServiceCheckFilter master;
        
        private ConfigListener(VCGServiceCheckFilter master) {
            this.master = master;
        }

        @Override
        public void notifyAppConfigChanged() {
            master.reloadConfig();
        }
    }

    private static final class VCGServiceCheckFilterConfig {

        private final long sleepTime;
        private final int maxRDVQueueSize;


        /**
         * @param sleepTime
         *            in millis
         */
        private VCGServiceCheckFilterConfig(long sleepTime, int maxRDVQueueSize) {
            this.sleepTime = sleepTime;
            this.maxRDVQueueSize = maxRDVQueueSize;
        }


        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("VCGServiceCheckFilterConfig [sleepTime=")
                   .append(sleepTime)
                   .append(", maxRDVQueueSize=")
                   .append(maxRDVQueueSize)
                   .append("]");
            return builder.toString();
        }
    }

    /**
     * @param farmName
     */
    public VCGServiceCheckFilter(String farmName) {
        super(farmName);
        final VCGServiceCheckFilterConfig cfg = loadConfig();
        this.configPointer.set(cfg);
        this.rdvQueue = new LinkedBlockingQueue<EFlowStats>(cfg.maxRDVQueueSize);
    }

    @Override
    public String getName() {
        return VCGServiceCheckFilter_NAME;
    }

    @Override
    public long getSleepTime() {
        final VCGServiceCheckFilterConfig vcgServiceCheckFilterConfig = configPointer.get();
        return vcgServiceCheckFilterConfig.sleepTime;
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
        // I cannot care less...
    }

    @Override
    public Object expressResults() {
        final long sTime = System.nanoTime();
        final int maxFlows = this.maxNotifFlows;
        final ArrayList<EFlowStats> dList = new ArrayList<EFlowStats>(maxFlows);
        final int cFlows = rdvQueue.drainTo(dList);
        
        final boolean isFine = logger.isLoggable(Level.FINE);
        
        try {
            final Object ret = processEFlows(dList);

            if (isFine) {
                logger.log(Level.FINE,
                           "[ " + VCGServiceCheckFilter_NAME + " ] sent " + dList.size() + " EFlowStats"
                                   + ((logger.isLoggable(Level.FINEST) ? dList.toString() : "")));
            }

            return ret;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ " + VCGServiceCheckFilter_NAME + " ] unable to express results. Cause: ", t);
        } finally {
            if (maxFlows < cFlows) {
                this.maxNotifFlows = cFlows;
            }
            
            if(isFine) {
                final long dt = System.nanoTime() - sTime;
                logger.log(Level.FINE, "[ " + VCGServiceCheckFilter_NAME + " ] took " + TimeUnit.NANOSECONDS.toMillis(dt) + " ms");
            }
        }

        return null;
    }

    private static Object processEFlows(ArrayList<EFlowStats> currentFlows) throws Exception {
        final int size = currentFlows.size();
        if (size == 0)
            return null;
        final EFlowStatsSer[] arr = new EFlowStatsSer[size];
        int idx = 0;
        for (final EFlowStats efs : currentFlows) {
            arr[idx++] = EFlowStatsSer.fromEFlowStat(efs);
        }
        final byte[] ret = Utils.writeCompressedObject(arr);
        if(ret != null && logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "[ " + VCGServiceCheckFilter_NAME + " ] compressed eflow array size: " + ret.length  + " bytes" );
        }
        return ret;
    }

    @Override
    public void updateStats(EFlowStats eflowStats) {
        if (!rdvQueue.offer(eflowStats)) {
            logger.log(Level.WARNING, "[ " + VCGServiceCheckFilter_NAME + " ] unable to add: " + eflowStats + " ... Queue full.");
        }
    }

    /*
     * (non-Javadoc)
     * @see lia.Monitor.Filters.GenericMLFilter#initCache(lia.Monitor.DataCache.Cache)
     */
    @Override
    public void initCache(Cache cache) {
        super.initCache(cache);
        final ConfigListener cl = new ConfigListener(this);
        AppConfig.addNotifier(cl);
        logger.log(Level.INFO, "[ " + VCGServiceCheckFilter_NAME + " ] registered for AppConfigChanges");
    }

    private static VCGServiceCheckFilterConfig loadConfig() {
        final long sleepTime = AppConfig.getl("VCGServiceCheckFilter.sleepTimeMills", TimeUnit.SECONDS.toMillis(20));
        final int maxRDVQueueSize = AppConfig.geti("VCGServiceCheckFilter.maxRDVQueueSize", 2048);
        final VCGServiceCheckFilterConfig cfg = new VCGServiceCheckFilterConfig(sleepTime, maxRDVQueueSize);
        if(logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "[ " + VCGServiceCheckFilter_NAME + " ] loaded config: " + cfg);
        }
        return cfg;
    }

    private void reloadConfig() {
        configPointer.set(loadConfig());
    }
}
