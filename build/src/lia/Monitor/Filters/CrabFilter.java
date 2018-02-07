/* $Id: CrabFilter.java 7419 2013-10-16 12:56:15Z ramiro $*/
package lia.Monitor.Filters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.ntp.NTPDate;
import lia.util.threads.MonALISAExecutors;

/**
 *
 * @author ramiro
 */
public class CrabFilter extends GenericMLFilter {

    /**
     * 
     */
    private static final long serialVersionUID = 7873080040030981851L;
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(CrabFilter.class.getName());
    private static final String FILTER_NAME = "CrabFilter";

    //K: cluster name, v - ConcurrentHashMap with K: jobName, V: CrabJob
    private final ConcurrentHashMap jobsClusterMap = new ConcurrentHashMap();
    private final ConcurrentHashMap lastJobsClusterMap = new ConcurrentHashMap();
    private long lastRun;
    private final Object lock = new Object();
    private static final AtomicLong REPORT_DELAY = new AtomicLong(20 * 1000);
    private static final AtomicLong ETH_MAX_VAL = new AtomicLong(131072);
    private static final AtomicLong CONFIG_TIMEOUT = new AtomicLong(90 * 60 * 1000);//90 minutes
    private final ArrayList cleanupTaskResults = new ArrayList();

    private final class CleanupTask implements Runnable {

        @Override
        public void run() {
            try {

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " [ CrabFilter] CleanupTask started ... ");
                }

                boolean removed = false;
                StringBuilder sb = new StringBuilder();
                final long now = NTPDate.currentTimeMillis();
                final long nowTOUT = now - CONFIG_TIMEOUT.get();

                for (Iterator it = jobsClusterMap.entrySet().iterator(); it.hasNext();) {

                    final Map.Entry entry = (Map.Entry) it.next();
                    final String clusterName = (String) entry.getKey();
                    final ConcurrentHashMap jobsMap = (ConcurrentHashMap) entry.getValue();

                    for (Iterator jit = jobsMap.values().iterator(); jit.hasNext();) {
                        final CrabJob job = (CrabJob) jit.next();
                        final long lastUpdate = job.lastResultTimestamp.get();
                        if ((lastUpdate > 0) && (lastUpdate < nowTOUT)) {
                            removed = true;
                            sb.append("\nremoving job [ ").append(job).append(" ] from jobsMap for cluster: ")
                                    .append(clusterName);
                            sb.append("Last heard [ ").append(lastUpdate).append("/").append(new Date(lastUpdate))
                                    .append(" ] CurrentTime [ ").append(now).append("/").append(new Date(now))
                                    .append(" ]");
                            jit.remove();
                        }
                    }

                    if (jobsMap.size() == 0) {
                        removed = true;
                        sb.append("\nremoving CLUSTER [ ").append(clusterName).append(" ] from jobsClusterMap.");
                        lastJobsClusterMap.remove(clusterName);

                        it.remove();
                        final eResult er = new eResult(farmName, "_TOTALS_", clusterName, "CrabFilter", null);
                        er.time = now;
                        synchronized (cleanupTaskResults) {
                            cleanupTaskResults.add(er);
                        }
                    }
                }

                if (removed) {
                    logger.log(Level.INFO, "\n\n[ CrabFilter ] [ CleanupTask ] remove log\n" + sb.toString());
                }

            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ CrabFilter ] [ CleanupTask ] got exception in main loop", t);
            }
        }
    }

    static {
        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {

                long newSleepTime = REPORT_DELAY.get() / 1000L;

                try {
                    newSleepTime = AppConfig.getl("lia.Monitor.Filters.CrabFilter.REPORT_DELAY", newSleepTime);
                    REPORT_DELAY.set(newSleepTime * 1000);
                } catch (Throwable t) {
                    logger.log(Level.WARNING,
                            " [ CrabFilter ] unable to parse lia.Monitor.Filters.CrabFilter.REPORT_DELAY", t);
                }

                long newEthMaxVal = ETH_MAX_VAL.get();
                try {
                    newEthMaxVal = AppConfig.getl("lia.Monitor.Filters.CrabFilter.ETH_MAX_VAL", ETH_MAX_VAL.get());
                    ETH_MAX_VAL.set(newEthMaxVal);
                } catch (Throwable t) {
                    logger.log(Level.WARNING,
                            " [ CrabFilter ] unable to parse lia.Monitor.Filters.CrabFilter.ETH_MAX_VAL", t);
                }

                long newConfigTimeout = CONFIG_TIMEOUT.get() / 1000L;

                try {
                    newConfigTimeout = AppConfig
                            .getl("lia.Monitor.Filters.CrabFilter.CONFIG_TIMEOUT", newConfigTimeout);
                    CONFIG_TIMEOUT.set(newConfigTimeout * 1000);
                } catch (Throwable t) {
                    logger.log(Level.WARNING,
                            " [ CrabFilter ] unable to parse lia.Monitor.Filters.CrabFilter.CONFIG_TIMEOUT", t);
                }

                logger.log(Level.INFO, " [ CrabFilter ] (Re)Load config REPORT_DELAY = " + (REPORT_DELAY.get() / 1000L)
                        + " seconds; ETH_MAX_VAL = " + ETH_MAX_VAL.get() + " kBytes/s; CONFIG_TIMEOUT = "
                        + (CONFIG_TIMEOUT.get() / 1000L) + " seconds");
            }
        });
    }

    private final class EthInt {

        private final String ethName;
        private final CrabJob crabJob;
        private final AtomicLong totalIO = new AtomicLong(0);
        private final AtomicLong lastUpdate = new AtomicLong(0);

        EthInt(final String ethName, final long lastUpdate, final CrabJob crabJob) {
            this.ethName = ethName;
            this.lastUpdate.set(lastUpdate);
            this.crabJob = crabJob;
        }

        void updateTraffic(final double speed, final long now) {
            final double add = (speed * (now - lastUpdate.get())) / 1000;
            if (add < 0) {
                StringBuilder sb = new StringBuilder(256);
                sb.append("\n\n[ CrabFilter ] going back in time ?!? CrabJob ").append(this.crabJob);
                sb.append("\n Eth ").append(this.ethName);
                sb.append("\n lastUpdate: ").append(this.lastUpdate.get()).append("    now: ").append(now);
                sb.append("\n CurrentSpeed: ").append(speed).append(" totalIO: ").append(totalIO.get()).append("\n\n");
                logger.log(Level.WARNING, sb.toString());
            } else {
                totalIO.addAndGet((long) add);
            }

            lastUpdate.set(now);
        }

        @Override
        public String toString() {
            return " [ EthInt: " + ethName + " totalIO: " + totalIO + " lastUpdate: " + lastUpdate + " ] ";
        }
    }

    private final class CrabJob {

        private final String jobName;
        private final String clusterName;
        private volatile String macID;
        private volatile long senderID = -1;
        private final AtomicLong lastResultTimestamp = new AtomicLong(0L);
        private final ConcurrentHashMap ethInterfaces = new ConcurrentHashMap();
        private double cpuUsage = 0D;
        private final AtomicInteger memUsage = new AtomicInteger(0);

        CrabJob(final String jobName, final String clusterName) {
            if (jobName == null) {
                throw new NullPointerException("CrabJob: Null job name");
            }

            if (clusterName == null) {
                throw new NullPointerException("CrabJob: Null cluster name");
            }

            this.jobName = jobName;
            this.clusterName = clusterName;
        }

        public void setMacID(final String macID) {
            this.macID = macID;
        }

        public String getMacID() {
            return this.macID;
        }

        public void setSenderID(final long senderID) {
            this.senderID = senderID;
        }

        public long getSenderID() {
            return this.senderID;
        }

        @Override
        public int hashCode() {
            return jobName.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }

            if (o instanceof CrabJob) {
                CrabJob other = (CrabJob) o;
                return this.jobName.equals(other.jobName) && this.clusterName.equals(other.clusterName);
            }

            return false;
        }

        @Override
        public String toString() {
            return clusterName + "  ->  " + this.jobName + "; SenderID: " + getSenderID() + "; macID: " + getMacID();
        }
    }

    public CrabFilter(String farmName) {
        super(farmName);
        MonALISAExecutors.getMLHelperExecutor().scheduleWithFixedDelay(new CleanupTask(), 60, 60, TimeUnit.SECONDS);
    }

    @Override
    public String getName() {
        return FILTER_NAME;
    }

    @Override
    public long getSleepTime() {
        return REPORT_DELAY.get();
    }

    @Override
    public monPredicate[] getFilterPred() {
        return null;
    }

    //online (e)Results are notified here ...
    @Override
    public void notifyResult(Object o) {

        final long now = NTPDate.currentTimeMillis();

        if (o == null) {
            return;
        }

        if (o instanceof Collection) {
            for (Iterator it = ((Collection) o).iterator(); it.hasNext();) {
                notifyResult(it.next());
            }
        }

        try {
            //synch on the same lock with the one on which you express the results
            synchronized (lock) {
                if (o instanceof Result) {
                    try {
                        Result r = (Result) o;
                        if ((r.Module != null) && r.Module.equals("monXDRUDP")) {
                            if ((r.ClusterName != null) || (r.ClusterName.length() == 0)) {

                                if ((r.NodeName == null) || (r.NodeName.length() == 0)) {
                                    logger.log(Level.WARNING,
                                            " [ CrabFilter ] got a result with null/blank node name: " + r
                                                    + " ignoring it");
                                    return;
                                }

                                ConcurrentHashMap jobsMap = (ConcurrentHashMap) jobsClusterMap.get(r.ClusterName);
                                if (jobsMap == null) {
                                    jobsClusterMap.putIfAbsent(r.ClusterName, new ConcurrentHashMap());
                                    jobsMap = (ConcurrentHashMap) jobsClusterMap.get(r.ClusterName);
                                }

                                CrabJob cj = (CrabJob) jobsMap.get(r.NodeName);
                                if (cj == null) {
                                    jobsMap.putIfAbsent(r.NodeName, new CrabJob(r.NodeName, r.ClusterName));
                                    //just to be sure that we get the expected CrabJob if other thread added this in the meantime
                                    cj = (CrabJob) jobsMap.get(r.NodeName);
                                }

                                if ((r.param == null) || (r.param_name == null)) {
                                    logger.log(Level.WARNING,
                                            " [ CrabFilter ] got a result with null/blank param_name or para: " + r
                                                    + " ignoring it");
                                    return;
                                }

                                final int pLen = r.param.length;

                                for (int i = 0; i < pLen; i++) {

                                    final String paramName = r.param_name[i];
                                    final double val = r.param[i];

                                    if ((paramName.indexOf("eth") >= 0)
                                            && ((paramName.indexOf("_in") >= 0) || (paramName.indexOf("_out") >= 0))) {
                                        EthInt eth = (EthInt) cj.ethInterfaces.get(paramName);

                                        cj.lastResultTimestamp.set(now);

                                        if (eth == null) {
                                            cj.ethInterfaces.putIfAbsent(paramName, new EthInt(paramName, r.time, cj));
                                            continue;
                                        }

                                        if ((val >= 0) && (val < ETH_MAX_VAL.get())) {
                                            eth.updateTraffic(val, r.time);
                                        } else {
                                            logger.log(Level.WARNING, " [ CrabFilter ] Wrong eth traffic value for: "
                                                    + cj + " value: " + val + " kBytes/s .... ignoring it");
                                        }
                                        continue;
                                    }

                                    if (paramName.indexOf("SenderID") >= 0) {
                                        final long cSenderID = cj.getSenderID();
                                        final long senderID = (long) r.param[i];

                                        if ((cSenderID >= 0) && (cSenderID != senderID)) {
                                            logger.log(Level.WARNING, " \n\n SenderID has changed for CrabJob: " + cj
                                                    + " OLD SenderID: " + cSenderID + " NEW SenderID: " + senderID);
                                        }

                                        cj.lastResultTimestamp.set(now);
                                        cj.setSenderID(senderID);

                                        continue;
                                    }

                                    if (paramName.indexOf("cpu_usage") >= 0) {
                                        cj.lastResultTimestamp.set(now);
                                        cj.cpuUsage = val;
                                    }
                                }

                            } else {
                                logger.log(Level.WARNING, " [ CrabFilter ] got a result with null/blank cluster name: "
                                        + r + " ignoring it");
                            }
                        }
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " [ CrabFilter ] got exception in notifyResult processing Result "
                                + o, t);
                    }
                } else if (o instanceof eResult) {
                    try {
                        final eResult er = (eResult) o;
                        if ((er.Module != null) && er.Module.equals("monXDRUDP")) {
                            if ((er.ClusterName != null) || (er.ClusterName.length() == 0)) {

                                if ((er.NodeName == null) || (er.NodeName.length() == 0)) {
                                    logger.log(Level.WARNING,
                                            " [ CrabFilter ] got a result with null/blank node name: " + er
                                                    + " ignoring it");
                                    return;
                                }

                                ConcurrentHashMap jobsMap = (ConcurrentHashMap) jobsClusterMap.get(er.ClusterName);
                                if (jobsMap == null) {
                                    jobsClusterMap.putIfAbsent(er.ClusterName, new ConcurrentHashMap());
                                    jobsMap = (ConcurrentHashMap) jobsClusterMap.get(er.ClusterName);
                                }

                                CrabJob cj = (CrabJob) jobsMap.get(er.NodeName);
                                if (cj == null) {
                                    jobsMap.putIfAbsent(er.NodeName, new CrabJob(er.NodeName, er.ClusterName));
                                    //just to be sure that we get the expected CrabJob if other thread added this in the meantime
                                    cj = (CrabJob) jobsMap.get(er.NodeName);
                                }

                                if ((er.param == null) || (er.param_name == null)) {
                                    logger.log(Level.WARNING,
                                            " [ CrabFilter ] got a result with null/blank param_name or para: " + er
                                                    + " ignoring it");
                                    return;
                                }

                                final int pLen = er.param.length;

                                for (int i = 0; i < pLen; i++) {
                                    if (er.param_name[i].indexOf("MACID") >= 0) {
                                        final String macID = (String) er.param[i];
                                        final String cmacID = cj.getMacID();
                                        if (cmacID == null) {
                                            cj.lastResultTimestamp.set(now);
                                            cj.setMacID(macID);
                                            continue;
                                        }

                                        if (!cmacID.equals(macID)) {
                                            logger.log(Level.WARNING, " \n\n MACID has changed for CrabJob: " + cj
                                                    + " OLD MAC: " + cmacID + " NEW MAC: " + macID);
                                            cj.setMacID(macID);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " [ CrabFilter ] got exception in notifyResult processing eResult "
                                + o, t);
                    }
                } else {
                    logger.log(Level.WARNING, " [ CrabFilter ] unknown result received. Class : " + o.getClass()
                            + "     v: " + o);
                }

            }//sync

        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ CrabFilter ] got exception in notifyResult( " + o + ") ", t);
        }
    }

    @Override
    public Object expressResults() {
        final long now = NTPDate.currentTimeMillis();
        final double dt = (now - lastRun) / 1000D;

        try {

            if (logger.isLoggable(Level.FINEST)) {
                StringBuilder sb = new StringBuilder(8192);
                sb.append("\n\n [ CrabFilter ] Expres Result! Current clusterJobMap: \n\n");
                for (Iterator it = jobsClusterMap.values().iterator(); it.hasNext();) {
                    ConcurrentHashMap jobsMap = (ConcurrentHashMap) it.next();
                    for (Iterator jit = jobsMap.values().iterator(); jit.hasNext();) {
                        CrabJob job = (CrabJob) jit.next();

                        sb.append("\n===========\nCrabJob: ").append(job);
                        for (Iterator ethi = job.ethInterfaces.values().iterator(); ethi.hasNext();) {
                            EthInt eth = (EthInt) ethi.next();
                            sb.append("\n --> Eth=").append(eth.ethName).append("; totalIO=").append(eth.totalIO.get())
                                    .append("; lastUpdate=").append(eth.lastUpdate.get());
                        }
                    }
                }
                logger.log(Level.FINEST, sb.toString());
            }

            Vector retV = new Vector(jobsClusterMap.size());

            synchronized (lock) {

                for (Iterator it = jobsClusterMap.entrySet().iterator(); it.hasNext();) {

                    final Map.Entry entry = (Map.Entry) it.next();

                    final String clusterName = (String) entry.getKey();
                    final ConcurrentHashMap jobsMap = (ConcurrentHashMap) entry.getValue();

                    Result r = new Result();
                    r.time = now;
                    r.FarmName = farmName;
                    r.ClusterName = "_TOTALS_";
                    r.NodeName = clusterName;
                    r.Module = "CrabFilter";

                    ConcurrentHashMap oldMap = (ConcurrentHashMap) lastJobsClusterMap.get(clusterName);

                    if (oldMap == null) {
                        //new cluster
                        lastJobsClusterMap.putIfAbsent(clusterName, new ConcurrentHashMap());
                        oldMap = (ConcurrentHashMap) lastJobsClusterMap.get(clusterName);
                    }

                    final TreeSet macAddrSet = new TreeSet();

                    double total_IN = 0;
                    double total_OUT = 0;
                    double total_CPU_USAGE = 0;
                    long tJobs_cpu = 0;

                    for (Iterator jit = jobsMap.values().iterator(); jit.hasNext();) {
                        final CrabJob job = (CrabJob) jit.next();
                        CrabJob previousJob = (CrabJob) oldMap.get(job.jobName);

                        if (previousJob == null) {
                            oldMap.putIfAbsent(job.jobName, new CrabJob(job.jobName, clusterName));
                            previousJob = (CrabJob) oldMap.get(job.jobName);
                            previousJob.setMacID(job.macID);
                            previousJob.setSenderID(job.senderID);
                            previousJob.lastResultTimestamp.set(job.lastResultTimestamp.get());
                        }

                        final String macID = job.getMacID();

                        if (macID == null) {
                            continue;
                        }

                        final String oldMacID = previousJob.getMacID();
                        if (oldMacID == null) {
                            previousJob.setMacID(macID);
                        } else {
                            if (!oldMacID.equals(macID)) {
                                logger.log(Level.WARNING,
                                        "\n\n [ CrabFilter ] (expressResult) MAC Addrd changed for cJob: " + job
                                                + " previousJob: " + previousJob + " ??????? \n\n");
                            }
                        }

                        if (macAddrSet.contains(macID)) {
                            continue;
                        }

                        macAddrSet.add(macID);

                        double diff_in = 0;
                        double diff_out = 0;

                        total_CPU_USAGE += job.cpuUsage;
                        tJobs_cpu++;

                        for (final Iterator ethIt = job.ethInterfaces.values().iterator(); ethIt.hasNext();) {
                            final EthInt eth = (EthInt) ethIt.next();

                            EthInt oldEth = (EthInt) previousJob.ethInterfaces.get(eth.ethName);
                            if (oldEth == null) {
                                previousJob.ethInterfaces.putIfAbsent(eth.ethName, new EthInt(eth.ethName,
                                        eth.lastUpdate.get(), previousJob));
                                oldEth = (EthInt) previousJob.ethInterfaces.get(eth.ethName);
                                oldEth.totalIO.set(eth.totalIO.get());
                                continue;
                            }

                            final double d = eth.totalIO.get() - oldEth.totalIO.get();

                            if (d < 0) {
                                logger.log(Level.WARNING, " [ CrabFilter ] expressResult negative diff for job: " + job
                                        + " Eth: " + eth + " oldEth: " + oldEth);
                            } else {
                                if (eth.ethName.indexOf("_in") >= 0) {
                                    diff_in += d;
                                } else {
                                    diff_out += d;
                                }
                            }

                            oldEth.totalIO.set(eth.totalIO.get());
                        }

                        total_IN += diff_in;
                        total_OUT += diff_out;
                    }

                    if (macAddrSet.size() >= 1) {
                        r.addSet("Total_IN", total_IN / dt);
                        r.addSet("Total_OUT", total_OUT / dt);
                        r.addSet("Avg_cpu_usage", total_CPU_USAGE / tJobs_cpu);
                        retV.add(r);
                    }
                }
            }

            synchronized (cleanupTaskResults) {
                if (cleanupTaskResults.size() > 0) {
                    retV.addAll(cleanupTaskResults);
                    cleanupTaskResults.clear();
                }
            }

            if (retV.size() > 0) {
                logger.log(Level.FINE, " [ CrabFilter ] expressResults .... returning " + retV.size() + " results: "
                        + retV);
                return retV;
            }

            return null;

        } finally {
            lastRun = now;
        }
    }
}
