package lia.Monitor.Filters;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.DataCache.DataSelect;
import lia.Monitor.Store.Fast.IDGenerator;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.util.Utils;
import lia.util.mail.MailFactory;
import lia.util.mail.MailSender;
import lia.util.threads.MonALISAExecutors;
import lia.web.utils.Formatare;

/**
 * @author ramiro
 */
public class MLPingWatcher extends GenericMLFilter {

    /**
     * 
     */
    private static final long serialVersionUID = -2623572000833579571L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(MLPingWatcher.class.getName());

    private static final ScheduledExecutorService timeoutExecutor = MonALISAExecutors.getMLHelperExecutor();

    private static final class RCPTAddress {
        final String mailAddress;
        final boolean isSMS;

        /**
         * @param mailAddress
         * @param isSMS
         */
        private RCPTAddress(String mailAddress, boolean isSMS) {
            this.mailAddress = mailAddress;
            this.isSMS = isSMS;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("RCPTAddress [mailAddress=").append(mailAddress).append(", isSMS=").append(isSMS)
                    .append("]");
            return builder.toString();
        }

    }

    private static final class MLPingWatcherConfEntry {

        private final String predicateKey;

        private final monPredicate predicate;

        private final RCPTAddress[] RCPTS;

        // sorted set of thresholds; of integers; <= samplingLen
        private final int[] errValsThresholds;

        private final int samplingLen;

        private final long rearmDelta;

        MLPingWatcherConfEntry(final String predicateKey) {

            if (predicateKey == null) {
                throw new NullPointerException("predicateKey cannot be null");
            }

            this.predicateKey = predicateKey;
            this.predicate = Formatare.toPred(predicateKey);

            if (this.predicate == null) {
                final String errMsg = "Unable to parse predicate for key: " + predicateKey;
                final IllegalArgumentException iae = new IllegalArgumentException(errMsg);
                logger.log(Level.SEVERE, errMsg, iae);
                throw iae;
            }

            final String[] RCPTS = AppConfig.getVectorProperty("lia.Monitor.Filters.MLPingWatcher." + predicateKey
                    + ".RCPTS");

            if (RCPTS == null) {
                final String errMsg = "RCPTS[] cannot be null for key: " + predicateKey;
                final NullPointerException npe = new NullPointerException("RCPTS[] cannot be null for key: "
                        + predicateKey);
                logger.log(Level.SEVERE, errMsg, npe);
                throw npe;
            }

            if (RCPTS.length <= 0) {
                final String errMsg = "RCPTS.length <= 0 for key: " + predicateKey;
                final IllegalArgumentException iae = new IllegalArgumentException(errMsg);
                logger.log(Level.SEVERE, errMsg, iae);
                throw iae;
            }

            final ArrayList<RCPTAddress> addrList = new ArrayList<RCPTAddress>(RCPTS.length);
            for (final String rcpt : RCPTS) {
                final String key = rcpt + ".isSMS";
                final boolean isSms = AppConfig.getb(key, false);
                final RCPTAddress addr = new RCPTAddress(rcpt, isSms);
                addrList.add(addr);
                logger.log(Level.INFO, "Added new mail address: " + addr);
            }
            this.RCPTS = addrList.toArray(new RCPTAddress[] {});

            TreeSet<Integer> s = new TreeSet<Integer>();

            this.rearmDelta = TimeUnit.SECONDS.toNanos(AppConfig.getl("lia.Monitor.Filters.MLPingWatcher."
                    + predicateKey + ".rearmDelta", 30 * 60));
            final String[] errValsStrs = AppConfig.getVectorProperty("lia.Monitor.Filters.MLPingWatcher."
                    + predicateKey + ".errVals", "2,10,25,50");
            this.samplingLen = AppConfig.geti("lia.Monitor.Filters.MLPingWatcher." + predicateKey + ".samplingLen", 50);

            final int len = errValsStrs.length;
            for (int i = 0; i < len; i++) {
                final Integer val = Integer.valueOf(errValsStrs[i]);
                if (val.intValue() <= samplingLen) {
                    s.add(val);
                }
            }

            final int realErrLen = s.size();
            final Integer vals[] = s.toArray(new Integer[realErrLen]);
            this.errValsThresholds = new int[realErrLen];

            for (int i = 0; i < realErrLen; i++) {
                this.errValsThresholds[i] = vals[i].intValue();
            }

        }

        @Override
        public String toString() {
            return new StringBuilder().append("MLPingWatcherConfEntry").append(" key:").append(predicateKey)
                    .append(", predicate: ").append(predicate).append(", RCPTS[] ").append(Arrays.toString(RCPTS))
                    .append(", errValsThresholds: ").append(Arrays.toString(errValsThresholds))
                    .append(", samplingLen: ").append(samplingLen).append(", rearmDelta(nanos): ").append(rearmDelta)
                    .append(" / ").append(Utils.formatDuration(rearmDelta, TimeUnit.NANOSECONDS, false)).toString();
        }
    }

    private static final MLPingWatcherConfEntry getConfEntry(final Result r) {

        for (MLPingWatcherConfEntry confEntry : configMap.values()) {
            if (DataSelect.matchResult(r, confEntry.predicate) != null) {
                return confEntry;
            }
        }

        return null;
    }

    private static final class MLPingWatcherAlarmEntry {

        private final String key;

        private final String mailKey;

        // 0 is ok; 1 - not ok
        private final short[] lastMeasures;

        private final long[] lastMeasuresTimes;

        private int idx;

        private long lastStableTimestamp;
        private final HashSet<String> notifiedSMS;
        private long lastPingReceivedNanos;
        private long firstRecoveryProbe;
        private long downTimeSinceLastPing;
        private short lastState;

        private final MLPingWatcherConfEntry configEntry;

        private final boolean[] triggeredAlarms;
        private final AtomicBoolean[] notifiedAlarms;

        //prtected by synchronized(this)
        private ScheduledFuture<?> rearmFuture;

        MLPingWatcherAlarmEntry(final Result r, final int param) throws Exception {

            this.notifiedSMS = new HashSet<String>();
            configEntry = getConfEntry(r);
            final int realErrLen = configEntry.errValsThresholds.length;

            this.triggeredAlarms = new boolean[realErrLen];
            this.notifiedAlarms = new AtomicBoolean[realErrLen];

            for (int i = 0; i < realErrLen; i++) {
                this.notifiedAlarms[i] = new AtomicBoolean(false);
                this.triggeredAlarms[i] = false;
            }

            mailKey = r.ClusterName + "/" + r.NodeName;

            if (configEntry == null) {
                throw new Exception("No config entry for result: " + r);
            }

            this.key = IDGenerator.generateKey(r, param);

            if (this.key == null) {
                throw new NullPointerException(" [ MLPingWatcherAlarmEntry ] Null key for result: " + r + " param: "
                        + param);
            }

            this.lastMeasures = new short[configEntry.samplingLen];
            this.lastMeasuresTimes = new long[configEntry.samplingLen];

            this.idx = 0;
            this.lastStableTimestamp = System.currentTimeMillis();
            this.lastPingReceivedNanos = Utils.nanoNow();
            this.firstRecoveryProbe = System.currentTimeMillis();
            this.lastState = 1;
        }

        private synchronized final boolean isTriggered() {

            for (boolean triggeredAlarm : this.triggeredAlarms) {
                if (triggeredAlarm) {
                    return true;
                }
            }

            return false;
        }

        final synchronized void update(final short value, final long resTime) {

            if ((value == 0) && isTriggered() && (lastState == 1)) {
                this.firstRecoveryProbe = System.currentTimeMillis();
                this.downTimeSinceLastPing = Utils.nanoNow() - this.lastPingReceivedNanos;
            }

            this.lastState = value;
            this.lastMeasuresTimes[idx] = resTime;
            this.lastMeasures[idx++] = value;
            this.idx %= configEntry.samplingLen;

            int sum = 0;
            for (int i = 0; i < configEntry.samplingLen; i++) {
                sum += lastMeasures[i];
            }

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, this + " update ( " + value + " ) sum = " + sum);
            }

            final int almLen = configEntry.errValsThresholds.length;
            boolean schTask = false;
            for (int j = almLen - 1; j >= 0; j--) {
                final int iThreashold = configEntry.errValsThresholds[j];
                final boolean triggered = this.triggeredAlarms[j];
                if (!triggered && (iThreashold <= sum)) {
                    schTask = true;
                    this.triggeredAlarms[j] = true;
                    for (; j >= 0; j--) {
                        this.triggeredAlarms[j] = true;
                    }
                }
            }

            if (sum == 0) {
                this.lastPingReceivedNanos = Utils.nanoNow();
                if (rearmFuture != null) {
                    logger.log(Level.INFO, " [ MLPingWatcher ]  CLEARED alarm: \n " + this);
                    rearm();
                    final MailSender mailSender = MailFactory.getMailSender();
                    try {
                        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
                        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

                        final int idxSlash = mailKey.indexOf('/');
                        String mk = mailKey;
                        if (idxSlash >= 0) {
                            mk = mailKey.substring(idxSlash + 1);
                        }

                        final StringBuilder sb = new StringBuilder(8192);
                        final String downTime = Utils.formatDuration(this.downTimeSinceLastPing, TimeUnit.NANOSECONDS,
                                false);
                        sb.append("\n\n=================================================================================");
                        sb.append("\n -- MLPing ALARM CLEAR status report for *").append(mk).append("* as of: ")
                                .append(sdf.format(new Date(System.currentTimeMillis()))).append(" -- ");
                        sb.append("\n=================================================================================");
                        final String armed = "\nArmed:" + sdf.format(new Date(lastStableTimestamp));
                        sb.append(armed);
                        final String disArmed = "\nDisarmed:" + sdf.format(new Date(firstRecoveryProbe));
                        sb.append(disArmed);
                        final String stable = "\nStable:" + sdf.format(new Date(System.currentTimeMillis()));
                        sb.append(stable);
                        sb.append("\nDowntime: ").append(downTime);
                        sb.append("\n\n=================================================================================");
                        sb.append("\n -- END MLPing ALARM CLEAR status report for *").append(mk).append("* as of: ")
                                .append(sdf.format(new Date(System.currentTimeMillis()))).append(" -- ");
                        sb.append("\n=================================================================================");
                        final String msg = sb.toString();
                        final String smsMsg = armed + disArmed;
                        for (RCPTAddress ra : configEntry.RCPTS) {
                            final String subj = "[MLPing] RECOVERY: " + mk + "; Downtime: " + downTime;
                            if (ra.isSMS) {
                                mailSender.sendMessage("mlstatus@monalisa.cern.ch", new String[] { ra.mailAddress },
                                        subj, smsMsg);
                            } else {
                                mailSender.sendMessage("mlstatus@monalisa.cern.ch", new String[] { ra.mailAddress },
                                        subj, msg);
                            }
                        }

                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " [ MLPingWatcher ] Unable to notify packet loss. Cause: ", t);
                    }
                    this.firstRecoveryProbe = System.currentTimeMillis();
                }
                lastStableTimestamp = System.currentTimeMillis();
            }

            if (schTask) {
                if (rearmFuture != null) {
                    rearmFuture.cancel(false);
                    rearmFuture = null;
                }
                rearmFuture = timeoutExecutor.schedule(new Runnable() {
                    public void run() {
                        try {
                            rearm();
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, " [ MLPingWatcherAlarmEntry ] rearm() got exception ", t);
                        }
                    }
                }, configEntry.rearmDelta, TimeUnit.NANOSECONDS);
            }
        }

        final synchronized void rearm() {
            logger.log(Level.INFO, "Rearming key: " + key);
            final ScheduledFuture<?> localRearm = this.rearmFuture;
            if (localRearm == null) {
                logger.log(Level.WARNING, " [ MLPingWatcherAlarmEntry ] key: " + key
                        + ", rearm() but rearmFuture == null !!!!!?!?!");
            } else {
                localRearm.cancel(false);
                rearmFuture = null;
            }

            final int almLen = configEntry.errValsThresholds.length;
            for (int j = almLen - 1; j >= 0; j--) {
                this.triggeredAlarms[j] = false;
                this.notifiedAlarms[j].set(false);
            }

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ MLPingWatcherAlarmEntry ] key: " + key + " rearm-ed() ");
            }
        }

        @Override
        public synchronized String toString() {
            final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            return new StringBuilder(4096).append("MLPingWatcherConfEntry").append(" key:").append(key)
                    .append(" lastStableTimestamp: ").append(sdf.format(new Date(lastStableTimestamp))).append(", lastMeasures: ")
                    .append(Arrays.toString(lastMeasures)).append(", idx ").append(idx).append("triggered: ")
                    .append(Arrays.toString(triggeredAlarms)).append("notified: ")
                    .append(Arrays.toString(notifiedAlarms)).append(", almEntry: ").append(configEntry).toString();
        }
    }

    // K - String representing the F/C/N from monPredicate, V - MLPingWatcherConfEntry
    private static final ConcurrentMap<String, MLPingWatcherConfEntry> configMap = new ConcurrentHashMap<String, MLPingWatcherConfEntry>();

    private static final ConcurrentMap<String, MLPingWatcherAlarmEntry> alarmMap = new ConcurrentHashMap<String, MLPingWatcherAlarmEntry>();

    private final void reloadConfig() {

        final Map<String, MLPingWatcherConfEntry> initialConfig = new HashMap<String, MLPingWatcherConfEntry>(configMap);
        Map<String, MLPingWatcherConfEntry> newConfig = new HashMap<String, MLPingWatcherConfEntry>();

        try {
            final String[] predsVals = AppConfig.getVectorProperty("lia.Monitor.Filters.MLPingWatcher.monPreds");
            if ((predsVals == null) || (predsVals.length == 0)) {
                logger.log(Level.INFO,
                        " [ MLPingWatcher ] no config found ( lia.Monitor.Filters.MLPingWatcher.monPreds ) ");
                return;
            }

            for (final String predVal : predsVals) {
                MLPingWatcherConfEntry mlpwce = new MLPingWatcherConfEntry(predVal);
                newConfig.put(mlpwce.predicateKey, mlpwce);
            }

        } catch (Throwable t) {
            logger.log(Level.WARNING,
                    " [ MLPingWatcher ] Got exception reloading config. Will keep previous config. Cause: ", t);
            newConfig = initialConfig;
        } finally {
            configMap.putAll(newConfig);
            for (Iterator<String> it = configMap.keySet().iterator(); it.hasNext();) {
                final String key = it.next();
                if (!newConfig.containsKey(key)) {
                    logger.log(Level.WARNING, " [ MLPingWatcher ] removing key: " + key + " from config");
                    it.remove();
                }
            }
        }

        if (logger.isLoggable(Level.FINER)) {
            StringBuilder sb = new StringBuilder(512);
            sb.append("\n[ MLPingWatcher ] reloadConfig()");
            for (Entry<String, MLPingWatcherConfEntry> entry : configMap.entrySet()) {
                sb.append("\n --> Key: ").append(entry.getKey()).append(" --> pred: ").append(entry.getValue());
            }
            logger.log(Level.FINER, sb.toString());
        }
    }

    public MLPingWatcher(String farmName) {
        super(farmName);
        reloadConfig();
        AppConfig.addNotifier(new AppConfigChangeListener() {

            public void notifyAppConfigChanged() {
                try {
                    reloadConfig();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " [ MLPingWatcher ] exception reloading config. Cause: ", t);
                }
            }
        });

        logger.log(Level.INFO, " [ MLPingWatcher ] monitoring " + configMap.size() + " predicates.");
    }

    @Override
    public Object expressResults() {
        final boolean isFinest = logger.isLoggable(Level.FINEST);
        for (MLPingWatcherAlarmEntry alarm : alarmMap.values()) {
            synchronized (alarm) {
                if (isFinest) {
                    logger.log(Level.FINEST, " [ MLPingWatcher ] checking alarm : " + alarm);
                }

                final MLPingWatcherConfEntry configEntry = alarm.configEntry;
                for (int j = alarm.triggeredAlarms.length - 1; j >= 0; j--) {
                    if (alarm.triggeredAlarms[j]) {
                        if (alarm.notifiedAlarms[j].compareAndSet(false, true)) {
                            logger.log(Level.INFO, " [ MLPingWatcher ]  notifying alarm: \n " + alarm);
                            final MailSender mailSender = MailFactory.getMailSender();
                            try {
                                final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
                                sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
                                final int idxSlash = alarm.mailKey.indexOf('/');
                                String mk = alarm.mailKey;
                                if (idxSlash >= 0) {
                                    mk = alarm.mailKey.substring(idxSlash + 1);
                                }
                                final StringBuilder sb = new StringBuilder(128);
                                final String downtimeNanoFmt = Utils.formatDuration(Utils.nanoNow()
                                        - alarm.lastPingReceivedNanos, TimeUnit.NANOSECONDS, false);
                                sb.append("\n\n=================================================================================");
                                sb.append("\n -- MLPing ALARM status report for *").append(mk).append("* as of: ")
                                        .append(sdf.format(new Date(System.currentTimeMillis()))).append(" -- ");
                                sb.append("\n=================================================================================");
                                final String armedOn = "\n\nArmed on: " + sdf.format(new Date(alarm.lastStableTimestamp));
                                sb.append(armedOn);
                                final String downtimeStr = "\nDowntime since armed: " + downtimeNanoFmt;
                                sb.append(downtimeStr);
                                final StringBuilder sbExt = new StringBuilder(128);
                                sbExt.append("\n\n\nThere were at least: ");
                                sbExt.append(configEntry.errValsThresholds[j]).append(" / ")
                                        .append(configEntry.samplingLen);
                                sbExt.append(" lost probes.");
                                sbExt.append("\n\n\nLast lost probes timestamps:\n\n");

                                int count = 1;
                                for (int i = 0; i < alarm.lastMeasures.length; i++) {
                                    if (alarm.lastMeasures[i] > 0) {
                                        sbExt.append(" [ ").append(count++).append(" ] ")
                                                .append(sdf.format(new Date(alarm.lastMeasuresTimes[i]))).append("\n");
                                    }
                                }
                                sbExt.append("\n===============================================================================");
                                sbExt.append("\n -- END Status report for *").append(mk).append("* as of: ")
                                        .append(sdf.format(new Date(System.currentTimeMillis()))).append(" -- ");
                                sbExt.append("\n===============================================================================");

                                final StringBuilder subjSB = new StringBuilder(32);
                                subjSB.append("[MLPing] ALARM ").append(mk).append("; Downtime: ")
                                        .append(downtimeNanoFmt);

                                final String subj = subjSB.toString();
                                for (final RCPTAddress address : alarm.configEntry.RCPTS) {
                                    if (address.isSMS) {
                                        if (!alarm.notifiedSMS.contains(address.mailAddress)) {
                                            mailSender.sendMessage("mlstatus@monalisa.cern.ch",
                                                    new String[] { address.mailAddress }, subj, armedOn);
                                            alarm.notifiedSMS.add(address.mailAddress);
                                        } else {
                                            logger.log(Level.INFO, " --- Alarm already notified for SMS address: "
                                                    + address.mailAddress);
                                        }
                                    } else {
                                        mailSender.sendMessage("mlstatus@monalisa.cern.ch",
                                                new String[] { address.mailAddress }, subj,
                                                sb.toString() + sbExt.toString());
                                    }
                                }
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, " [ MLPingWatcher ] Unable to notify packet loss. Cause: ", t);
                                alarm.notifiedAlarms[j].set(false);
                            } finally {
                                if (alarm.notifiedAlarms[j].get()) {
                                    for (; j >= 0; j--) {
                                        alarm.notifiedAlarms[j].set(true);
                                        alarm.triggeredAlarms[j] = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public monPredicate[] getFilterPred() {
        return null;
    }

    @Override
    public String getName() {
        return "MLPingWatcher";
    }

    @Override
    public long getSleepTime() {
        return 1000;
    }

    @Override
    public void notifyResult(Object o) {
        try {

            if (o instanceof Result) {
                final Result r = (Result) o;
                if ((r.Module != null) && r.Module.equals("monPing")) {
                    final int idxLostPackParam = r.getIndex("LostPackages");
                    if (idxLostPackParam < 0) {
                        return;
                    }

                    final long rTime = r.time;

                    for (MLPingWatcherConfEntry mlPingWatcherConfEntry : configMap.values()) {
                        final monPredicate pred = mlPingWatcherConfEntry.predicate;
                        if (DataSelect.matchResult(r, pred) != null) {
                            final String resKey = IDGenerator.generateKey(r, idxLostPackParam);

                            MLPingWatcherAlarmEntry mlpwae = alarmMap.get(resKey);
                            if (mlpwae == null) {
                                try {
                                    mlpwae = new MLPingWatcherAlarmEntry(r, idxLostPackParam);
                                } catch (Throwable t) {
                                    logger.log(Level.WARNING,
                                            " [ MLPingWatcher ] notifyResults got exception for result: " + r
                                                    + " param: " + idxLostPackParam + ". Cause:", t);
                                    mlpwae = null;
                                }
                            }

                            if (mlpwae == null) {
                                return;
                            }

                            alarmMap.putIfAbsent(resKey, mlpwae);

                            mlpwae = alarmMap.get(resKey);

                            if (mlpwae == null) {
                                logger.log(Level.WARNING, " [ MLPingWatcher ] Ongoing cleanup ?? for r: " + r);
                                return;
                            }

                            if (r.param[idxLostPackParam] > 1) {
                                mlpwae.update((short) 1, rTime);
                            } else {
                                mlpwae.update((short) 0, rTime);
                            }
                        }
                    }
                }
            }

        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ MLPingWatcher ] notifyResults got exception notif result: " + o
                    + "\n. Cause: ", t);
        }
    }

}
