/*
 * Created on Dec 4, 2011
 */
package lia.Monitor.ciena.eflow;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ramiro
 *
 */
public final class EFlowStatsMgr {
    private static final Logger logger = Logger.getLogger(EFlowStatsMgr.class.getName());

    private static final class Holder {
        private static final EFlowStatsMgr INSTANCE = new EFlowStatsMgr();
    }

    private final CopyOnWriteArrayList<EFlowStatsConsumer> consumers = new CopyOnWriteArrayList<EFlowStatsConsumer>();

    private final ExecutorService notifierExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        private final AtomicLong SEQ = new AtomicLong(0L);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "(ML) EFlowMgr worker: " + SEQ.incrementAndGet());
        }
    });

    private static final class ExecTask implements Runnable {
        private final EFlowStats stats;
        private static final EFlowStatsMgr mgr = EFlowStatsMgr.getInstance();

        ExecTask(EFlowStats stats) {
            this.stats = stats;
        }

        @Override
        public void run() {
            final CopyOnWriteArrayList<EFlowStatsConsumer> consumers = mgr.consumers;
            for (final EFlowStatsConsumer c : consumers) {
                try {
                    c.updateStats(stats);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "[EFlowStatsMgr] Exception notifying: " + stats + " to: " + c
                            + " Cause: ", t);
                }
            }
        }
    }

    public static final EFlowStatsMgr getInstance() {
        return Holder.INSTANCE;
    }

    public void registerConsumer(EFlowStatsConsumer consumer) {
        consumers.add(consumer);
    }

    public void unregisterConsumer(EFlowStatsConsumer consumer) {
        consumers.remove(consumer);
    }

    void newEFlowStats(EFlowStats stats) {
        notifierExecutor.execute(new ExecTask(stats));
    }
}
