/*
 * $Id: MLPingTrigger.java 6865 2010-10-10 10:03:16Z ramiro $
 */
package lia.Monitor.Filters.MLPing;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
import lia.Monitor.Filters.GenericMLFilter;
import lia.Monitor.Store.Fast.IDGenerator;
import lia.Monitor.ciena.triggers.repository.StateProvider;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.util.Utils;
import lia.util.mail.MailFactory;
import lia.util.mail.MailSender;
import lia.util.ntp.NTPDate;
import lia.util.threads.MonALISAExecutors;
import lia.util.timestamp.TimestampableStateValue;
import lia.web.utils.Formatare;

/**
 * @author ramiro
 */
public class MLPingTrigger extends GenericMLFilter implements StateProvider<MLPingAlarmState, MLPingMonitoringValue> {

    /**
     * 
     */
    private static final long serialVersionUID = -2623572000833579571L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(MLPingTrigger.class.getName());

    private static final ScheduledExecutorService timeoutExecutor = MonALISAExecutors.getMLHelperExecutor();

    private static final class MLPingWatcherConfEntry {

        private final String predicateKey;

        private final monPredicate predicate;

        private final String[] RCPTS;

        // sorted set of thresholds; of integers; <= samplingLen
        private final int[] errValsThresholds;

        private final int samplingLen;

        private final long rearmDelta;

        MLPingWatcherConfEntry(final String predicateKey) {

            final String prefixConfigKey = "lia.Monitor.Filters.MLPing.MLPingTrigger.";
            if (predicateKey == null) {
                throw new NullPointerException("predicateKey cannot be null");
            }

            this.predicateKey = predicateKey;
            this.predicate = Formatare.toPred(predicateKey);

            if (this.predicate == null) {
                throw new IllegalArgumentException("Cannot determine predicate from key: " + predicateKey);
            }

            final String[] RCPTS = AppConfig.getVectorProperty(prefixConfigKey + predicateKey + ".RCPTS");

            if (RCPTS == null) {
                throw new NullPointerException("RCPTS[] cannot be null");
            }

            if (RCPTS.length <= 0) {
                throw new IllegalArgumentException("RCPTS.length <= 0");
            }

            this.RCPTS = RCPTS;
            TreeSet s = new TreeSet();

            this.rearmDelta = TimeUnit.SECONDS.toNanos(AppConfig.getl(prefixConfigKey + predicateKey + ".rearmDelta", 30 * 60));
            final String[] errValsStrs = AppConfig.getVectorProperty(prefixConfigKey + predicateKey + ".errVals", "2,10,25,50");
            this.samplingLen = AppConfig.geti(prefixConfigKey + predicateKey + ".samplingLen", 50);

            final int len = errValsStrs.length;
            for (int i = 0; i < len; i++) {
                final Integer val = Integer.valueOf(errValsStrs[i]);
                if (val.intValue() <= samplingLen) {
                    s.add(val);
                }
            }

            final int realErrLen = s.size();
            final Integer vals[] = (Integer[]) s.toArray(new Integer[realErrLen]);
            this.errValsThresholds = new int[realErrLen];
            
            for (int i = 0; i < realErrLen; i++) {
                this.errValsThresholds[i] = vals[i].intValue();
            }

        }

        public String toString() {
            return new StringBuilder().append("MLPingWatcherConfEntry")
                                     .append(" key:")
                                     .append(predicateKey)
                                     .append(", predicate: ")
                                     .append(predicate)
                                     .append(", RCPTS[] ")
                                     .append(Arrays.toString(RCPTS))
                                     .append(", errValsThresholds: ")
                                     .append(Arrays.toString(errValsThresholds))
                                     .append(", samplingLen: ")
                                     .append(samplingLen)
                                     .append(", rearmDelta(nanos): ")
                                     .append(rearmDelta)
                                     .append(" / ")
                                     .append(Formatare.showInterval(TimeUnit.NANOSECONDS.toMillis(rearmDelta)))
                                     .toString();
        }
    }

    private static final MLPingWatcherConfEntry getConfEntry(final Result r) {

        for (final Iterator it = configMap.values().iterator(); it.hasNext();) {
            final MLPingWatcherConfEntry confEntry = (MLPingWatcherConfEntry) it.next();

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

        private long lastDisarmed;
        private long lastPingReceivedNanos;
        private long firstRecoveryProbe;
        private long downTimeSinceLastPing;
        private short lastState;
        
        private final MLPingWatcherConfEntry configEntry;

        private final boolean[] triggeredAlarms;
        private final AtomicBoolean[] notifiedAlarms;

        //prtected by synchronized(this)
        private ScheduledFuture rearmFuture;

        MLPingWatcherAlarmEntry(final Result r, final int param) throws Exception {

            configEntry = getConfEntry(r);
            final int realErrLen = configEntry.errValsThresholds.length;

            this.triggeredAlarms = new boolean[realErrLen];
            this.notifiedAlarms = new AtomicBoolean[realErrLen];

            for (int i = 0; i < realErrLen; i++) {
                this.notifiedAlarms[i] = new AtomicBoolean(false);
                this.triggeredAlarms[i] = false;
            }

            mailKey = r.ClusterName + " / " + r.NodeName;

            if (configEntry == null) {
                throw new Exception("No config entry for result: " + r);
            }

            this.key = IDGenerator.generateKey(r, param);

            if (this.key == null) {
                throw new NullPointerException(" [ MLPingWatcherAlarmEntry ] Null key for result: " + r + " param: " + param);
            }

            this.lastMeasures = new short[configEntry.samplingLen];
            this.lastMeasuresTimes = new long[configEntry.samplingLen];

            this.idx = 0;
            this.lastDisarmed = 0L;
            this.lastPingReceivedNanos = Utils.nanoNow();
            this.firstRecoveryProbe = NTPDate.currentTimeMillis();
            this.lastState = 1;
        }

        private synchronized final boolean isTriggered() {
            
            for(int i=0; i<this.triggeredAlarms.length; i++) {
                if(this.triggeredAlarms[i]) return true;
            }
            
            return false;
        }
        final synchronized void update(final short value, final long resTime) {
            
            if(value == 0 && isTriggered() && lastState == 1) {
                this.firstRecoveryProbe = NTPDate.currentTimeMillis();
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

            if(logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, this + " update ( " + value + " ) sum = " + sum);
            }
            
            final int almLen = configEntry.errValsThresholds.length;
            boolean schTask = false;
            for (int j = almLen - 1; j >= 0; j--) {
                final int iThreashold = configEntry.errValsThresholds[j];
                final boolean triggered = this.triggeredAlarms[j];
                if (!triggered && iThreashold <= sum) {
                    schTask = true;
                    this.triggeredAlarms[j] = true;
                    for (; j >= 0; j--) {
                        this.triggeredAlarms[j] = true;
                    }
                }
            }

            if(sum == 0) {
                this.lastPingReceivedNanos = Utils.nanoNow();
                if(rearmFuture != null) {
                    logger.log(Level.INFO, " [ MLPingTrigger ]  CLEARED alarm: \n " + this);
                    rearmFuture.cancel(false);
                    rearmFuture = null;
                    rearm();
                    final MailSender mailSender = MailFactory.getMailSender();
                    try {
                        final StringBuilder sb = new StringBuilder(8192);
                        sb.append("\n\nAlarm recovered on: ").append(new Date(NTPDate.currentTimeMillis()));
                        sb.append("\n\nLast received probe before alarm on: ").append(new Date(lastDisarmed));
                        sb.append("\nFirst received probe after alarm on: ").append(new Date(firstRecoveryProbe));
                        sb.append("\n\nDowntime: ").append(Formatare.showInterval(TimeUnit.NANOSECONDS.toMillis(this.downTimeSinceLastPing)));
                        mailSender.sendMessage("mlstatus@monalisa.cern.ch", configEntry.RCPTS, "[ MLPing ] RECOVERY: " + mailKey + "; Downtime: " + Formatare.showInterval(TimeUnit.NANOSECONDS.toMillis(this.downTimeSinceLastPing)), sb.toString());
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " [ MLPingTrigger ] Unable to notify packet loss. Cause: ", t);
                    } finally {
                    }
                    this.firstRecoveryProbe = NTPDate.currentTimeMillis();
                }
                lastDisarmed = NTPDate.currentTimeMillis();
            }
            
            if (schTask) {
                if(rearmFuture != null) {
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

        final void rearm() {

            synchronized (this) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " [ MLPingWatcherAlarmEntry ] key: " + key + " rearm-ed() ");
                }
                final int almLen = configEntry.errValsThresholds.length;
                for (int j = almLen - 1; j >= 0; j--) {
                    this.triggeredAlarms[j] = false;
                    this.notifiedAlarms[j].set(false);
                }
                if (rearmFuture == null) {
                    logger.log(Level.WARNING, " [ MLPingWatcherAlarmEntry ] key: " + key + ", rearm() but rearmFuture == null !!!!!?!?!");
                } else {
                    rearmFuture.cancel(false);
                    rearmFuture = null;
                }
            }

        }

        public synchronized String toString() {
            return new StringBuilder(4096).append("MLPingWatcherConfEntry")
                                         .append(" key:")
                                         .append(key)
                                         .append(" lastDisarmed: ")
                                         .append(new Date(lastDisarmed))
                                         .append(", lastMeasures: ")
                                         .append(Arrays.toString(lastMeasures))
                                         .append(", idx ")
                                         .append(idx)
                                         .append("triggered: ")
                                         .append(Arrays.toString(triggeredAlarms))
                                         .append("notified: ")
                                         .append(Arrays.toString(notifiedAlarms))
                                         .append(", almEntry: ")
                                         .append(configEntry)
                                         .toString();
        }
    }

    // K - String representing the F/C/N from monPredicate, V - MLPingWatcherConfEntry
    private static final ConcurrentMap configMap = new ConcurrentHashMap();

    private static final ConcurrentMap alarmMap = new ConcurrentHashMap();

    private final void reloadConfig() {

        final Map initialConfig = new HashMap(configMap);
        Map newConfig = new HashMap();
        final String keyPrefix = "lia.Monitor.Filters.MLPing.MLPingTrigger.";
        
        try {
            final String configKey = keyPrefix + "monPreds";
            final String[] predsVals = AppConfig.getVectorProperty(configKey);
            if (predsVals == null || predsVals.length == 0) {
                logger.log(Level.INFO, " [ MLPingTrigger ] no config found for: " + configKey);
                return;
            }

            for (int i = 0; i < predsVals.length; i++) {
                final String predVal = predsVals[i];
                MLPingWatcherConfEntry mlpwce = new MLPingWatcherConfEntry(predVal);
                newConfig.put(mlpwce.predicateKey, mlpwce);
            }

        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ MLPingTrigger ] Got exception reloading config. Will keep previous config. Cause: ", t);
            newConfig = initialConfig;
        } finally {
            configMap.putAll(newConfig);
            for (Iterator it = configMap.keySet().iterator(); it.hasNext();) {
                final Object key = it.next();
                if (!newConfig.containsKey(key)) {
                    logger.log(Level.WARNING, " [ MLPingTrigger ] removing key: " + key + " from config");
                    it.remove();
                }
            }
        }

        if (logger.isLoggable(Level.FINER)) {
            StringBuilder sb = new StringBuilder(512);
            sb.append("\n[ MLPingTrigger ] reloadConfig()");
            for (Iterator it = configMap.entrySet().iterator(); it.hasNext();) {
                Map.Entry entry = (Map.Entry) it.next();
                sb.append("\n --> Key: ").append(entry.getKey()).append(" --> pred: ").append(entry.getValue());
            }
            logger.log(Level.FINER, sb.toString());
        }
    }

    public MLPingTrigger(String farmName) {
        super(farmName);
        reloadConfig();
        AppConfig.addNotifier(new AppConfigChangeListener() {

            public void notifyAppConfigChanged() {
                try {
                    reloadConfig();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " [ MLPingTrigger ] exception reloading config. Cause: ", t);
                }
            }
        });

        logger.log(Level.INFO, " [ MLPingTrigger ] monitoring " + configMap.size() + " predicates.");
    }

    public Object expressResults() {
        final boolean isFinest = logger.isLoggable(Level.FINEST);
        for (final Iterator it = alarmMap.values().iterator(); it.hasNext();) {
            MLPingWatcherAlarmEntry alarm = (MLPingWatcherAlarmEntry) it.next();

            if (isFinest) {
                logger.log(Level.FINEST, " [ MLPingTrigger ] checking alarm : " + alarm);
            }

            final MLPingWatcherConfEntry configEntry = alarm.configEntry;
            for(int j = alarm.triggeredAlarms.length - 1; j >= 0; j--) {
                if(alarm.triggeredAlarms[j]) {
                    if(alarm.notifiedAlarms[j].compareAndSet(false, true)) {
                        logger.log(Level.INFO, " [ MLPingTrigger ]  notifying alarm: \n " + alarm);
                        final MailSender mailSender = MailFactory.getMailSender();
                        try {
                            final StringBuilder sb = new StringBuilder(8192);
                            sb.append("\n\nAlarm status on: ").append(new Date(NTPDate.currentTimeMillis()));
                            sb.append("\n\nLast received probe before alarm was triggered: ").append(new Date(alarm.lastDisarmed));
                            sb.append("\nDown time since last received ping probe: ").append(Formatare.showInterval(TimeUnit.NANOSECONDS.toMillis(Utils.nanoNow() - alarm.lastPingReceivedNanos)));
                            sb.append("\n\n\nThere were at least: ");
                            sb.append(alarm.configEntry.errValsThresholds[j]).append(" / ").append(alarm.configEntry.samplingLen);
                            sb.append(" lost probes.");
                            sb.append("\n\n\nLast lost probes timestamps:\n\n");
                            
                            int count=1;
                            for (int i = 0; i < alarm.lastMeasures.length; i++) {
                                if (alarm.lastMeasures[i] > 0) {
                                    sb.append(" [ ").append(count++).append(" ] ").append(new Date(alarm.lastMeasuresTimes[i])).append("\n");
                                }
                            }
                            mailSender.sendMessage("mlstatus@monalisa.cern.ch", alarm.configEntry.RCPTS, "[ MLPing ] ALARM " + alarm.mailKey + " ! " + configEntry.errValsThresholds[j] + "+/" + configEntry.samplingLen + " lost probes", sb.toString());
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, " [ MLPingTrigger ] Unable to notify packet loss. Cause: ", t);
                            alarm.notifiedAlarms[j].set(false);
                        } finally {
                            if(alarm.notifiedAlarms[j].get()) {
                                for(; j>=0; j--) {
                                    alarm.notifiedAlarms[j].set(true);
                                    alarm.triggeredAlarms[j] = true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public monPredicate[] getFilterPred() {
        return null;
    }

    public String getName() {
        return "MLPingTrigger";
    }

    public long getSleepTime() {
        return 1000;
    }

    public void notifyResult(Object o) {
        try {

            if (o instanceof Result) {
                final Result r = (Result) o;
                if (r.Module != null && r.Module.equals("monPing")) {
                    final int idxLostPackParam = r.getIndex("LostPackages");
                    if (idxLostPackParam < 0)
                        return;

                    final long rTime = r.time;

                    for (Iterator it = configMap.values().iterator(); it.hasNext();) {
                        final monPredicate pred = ((MLPingWatcherConfEntry) it.next()).predicate;
                        if (DataSelect.matchResult(r, pred) != null) {
                            final String resKey = IDGenerator.generateKey(r, idxLostPackParam);

                            MLPingWatcherAlarmEntry mlpwae = (MLPingWatcherAlarmEntry) alarmMap.get(resKey);
                            if (mlpwae == null) {
                                try {
                                    mlpwae = new MLPingWatcherAlarmEntry(r, idxLostPackParam);
                                } catch (Throwable t) {
                                    logger.log(Level.WARNING, " [ MLPingTrigger ] notifyResults got exception for result: " + r + " param: " + idxLostPackParam + ". Cause:", t);
                                    mlpwae = null;
                                }
                            }

                            if (mlpwae == null)
                                return;

                            alarmMap.putIfAbsent(resKey, mlpwae);

                            mlpwae = (MLPingWatcherAlarmEntry) alarmMap.get(resKey);

                            if (mlpwae == null) {
                                logger.log(Level.WARNING, " [ MLPingTrigger ] Ongoing cleanup ?? for r: " + r);
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
            logger.log(Level.WARNING, " [ MLPingTrigger ] notifyResults got exception notif result: " + o + "\n. Cause: ", t);
        }
    }

    public MLPingAlarmState newState(MLPingAlarmState currentState, MLPingMonitoringValue currentValue, Collection<TimestampableStateValue<MLPingAlarmState, MLPingMonitoringValue>> lastValues) {
        // TODO Auto-generated method stub
        return null;
    }

    public int samplingSize() {
        // TODO Auto-generated method stub
        return 0;
    }

    public int flipFlopTransitions() {
        // TODO Auto-generated method stub
        return 0;
    }

    public boolean isFlipFlopTransition(MLPingAlarmState startState, MLPingAlarmState endState) {
        // TODO Auto-generated method stub
        return false;
    }

}
