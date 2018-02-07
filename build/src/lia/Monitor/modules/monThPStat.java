/*
 * Created on Feb 1, 2011
 */
package lia.Monitor.modules;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.Result;
import lia.util.Utils;
import lia.util.ntp.NTPDate;
import lia.util.threads.MLExecutorsFactory;
import lia.util.threads.MLScheduledThreadPoolExecutor;

/**
 * 
 * @author ramiro
 */
public class monThPStat extends AbstractSchJobMonitoring {

    /**
     * 
     */
    private static final long serialVersionUID = -6975014946785727439L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monThPStat.class.getName());

    private static final class ThreadPoolStats {

        private final String name;

        private long lastUpdateNano;
        private long completedTaskCount;

        private double completedTaskRate;

        /**
         * @param name
         */
        public ThreadPoolStats(String name, long completedTaskCount) {
            this.name = name;
            this.completedTaskCount = completedTaskCount;
            this.lastUpdateNano = Utils.nanoNow();
        }

        private void updateStats(long newTaskCount) {
            final long nanoNow = Utils.nanoNow();
            final long dtNanos = nanoNow - lastUpdateNano;

            if (dtNanos <= 0) {
                throw new IllegalStateException(
                        " [ ThreadPoolStats ] [ updateStats ] Time in nano seconds go back ? old: " + lastUpdateNano
                                + " new: " + nanoNow + " diff: " + dtNanos);
            }

            final long diffCount = newTaskCount - this.completedTaskCount;
            if (diffCount < 0) {
                throw new IllegalStateException(" [ ThreadPoolStats ] [ updateStats ] Task count goes back ? old: "
                        + this.completedTaskCount + " new: " + newTaskCount + " diff: " + dtNanos);
            }

            //everything ok
            this.completedTaskRate = (double) diffCount / (double) TimeUnit.NANOSECONDS.toSeconds(dtNanos);
            this.lastUpdateNano = nanoNow;
            this.completedTaskCount = newTaskCount;

        }
    }

    private final Map<String, ThreadPoolStats> internalStatMap = new HashMap<String, ThreadPoolStats>();

    @Override
    public MonModuleInfo initArgs(final String argStr) {
        return new MonModuleInfo();
    }

    @Override
    public boolean isRepetitive() {
        return true;
    }

    @Override
    public String getTaskName() {
        return "monThPStat";
    }

    @Override
    public Object doProcess() throws Exception {
        final Map<String, MLScheduledThreadPoolExecutor> execMap = MLExecutorsFactory.getExecutors();

        final int count = execMap.size();
        if (count > 0) {
            final long time = NTPDate.currentTimeMillis();
            final ArrayList<Result> retRes = new ArrayList<Result>(count);
            for (final Map.Entry<String, MLScheduledThreadPoolExecutor> entry : execMap.entrySet()) {
                final String execName = entry.getKey();
                String name = execName;
                if (execName.startsWith("lia.")) {
                    name = name.substring("lia.".length());
                }
                final MLScheduledThreadPoolExecutor tpe = entry.getValue();
                final Result r = new Result(node.getFarmName(), node.getClusterName(), name, getTaskName());
                r.time = time;
                final long newTaskCount = tpe.getCompletedTaskCount();
                r.addSet("ActiveCount", tpe.getActiveCount());
                r.addSet("CompletedTasks", newTaskCount);
                r.addSet("LargestPoolSize", tpe.getLargestPoolSize());
                r.addSet("PoolSize", tpe.getPoolSize());
                try {
                    final ThreadPoolStats stats = internalStatMap.get(name);
                    if (stats == null) {
                        internalStatMap.put(name, new ThreadPoolStats(name, newTaskCount));
                    } else {
                        stats.updateStats(newTaskCount);
                        r.addSet("TaskRate", stats.completedTaskRate);

                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "[ HANDLED ] [ monThPStat ] [ doProcess ] unable to compute task rate", t);
                }
                retRes.add(r);
            }

            return retRes;
        }
        return null;
    }

    /**
     * @param args
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception {
        LogManager.getLogManager().readConfiguration(
                new ByteArrayInputStream(("handlers= java.util.logging.ConsoleHandler\n"
                        + "java.util.logging.ConsoleHandler.level = FINEST\n"
                        + "java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter\n" + "")
                        .getBytes()));
        logger.setLevel(Level.INFO);

        monThPStat mdIOStat = new monThPStat();
        mdIOStat.init(new MNode(), "");

        for (;;) {

            Thread.sleep(5 * 1000);
            final long sTime = Utils.nanoNow();
            Collection<?> cr = (Collection<?>) mdIOStat.doProcess();
            final long endTime = Utils.nanoNow();
            StringBuilder sb = new StringBuilder();
            for (final Object r : cr) {
                sb.append(r).append("\n");
            }
            logger.log(Level.INFO, " DT " + TimeUnit.NANOSECONDS.toMillis(endTime - sTime) + " ms. \n\n Returning \n"
                    + sb.toString());
        }
    }

}
