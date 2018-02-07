package lia.Monitor.Filters;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniSerFarmMon.GMLEPublisher;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.util.ntp.NTPDate;

public class TriggerAgent extends GenericMLFilter {

    /**
     * 
     */
    private static final long serialVersionUID = -8490717734701738794L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(TriggerAgent.class.getName());

    private static String Name = "TriggerAgent";

    public static int QUALITY2_OFFSET = 0;
    public static int QUALITY12_OFFSET = 1;
    public static int QUALITY24_OFFSET = 2;
    public static final long dt1 = Long.valueOf(AppConfig.getProperty("TriggerAgent.NotifyTime", "60").trim())
            .longValue() * 1000;
    public static long[] QUALITY_TIME = { 2 * 60 * 60 * 1000, 12 * 60 * 60 * 1000, 24 * 60 * 60 * 1000 };

    public static int QUALITY_NUMBER = 3;
    Hashtable qualityHash;

    private final int qualExpiringDelta = 1 * 60 * 1000; // after this time, the quality with a peer node expires
    private static long qualSendDelta = 30 * 1000; // send Qul results every ... 30s

    static {
        try {
            //in seconds
            qualSendDelta = Long.valueOf(AppConfig.getProperty("lia.Monitor.Filters.TriggerAgent.qualSendDelta", "30"))
                    .longValue() * 1000;
        } catch (Throwable t) {
            qualSendDelta = 30 * 1000;
        }

    }

    private static long atime1;

    private static String ALARM1 = "ALARM";
    private static long lastSent = 0;

    private static double FIRED = 1.0D;

    private boolean a1Triggered;
    private boolean a1Cancelled;
    Object sAlarm = new Object();

    private boolean firstTime;
    private Double status;

    public TriggerAgent(String farmName) {
        super(farmName);
        a1Triggered = false;
        a1Cancelled = false;
        qualityHash = new Hashtable();
        firstTime = true;
        status = Double.valueOf(-1D);
    }

    /* from MonitorFilter */
    @Override
    public String getName() {
        return Name;
    }

    private void processQuality(Result r) {
        int i;
        /* see if the result is in our Interest */
        if (r.ClusterName.indexOf("Peers") == -1) {
            return;
        }
        for (i = 0; i < r.param_name.length; i++) {
            if (r.param_name[i].indexOf("Quality") != -1) {
                break;
            }
        }

        if (i == r.param_name.length) {
            return;
        }

        int j = i; //keep the pos

        /* Let's do it */
        long now = NTPDate.currentTimeMillis();

        QualityResult qr = null;

        if (qualityHash.containsKey(r.NodeName)) {
            qr = (QualityResult) qualityHash.get(r.NodeName);
            for (i = 0; i < QUALITY_NUMBER; i++) {
                double oldQ = qr.qualityValues[i];
                double newQ = r.param[j];
                long t = qr.qualityTimes[i];
                long dt = now - t;
                long diffdt = QUALITY_TIME[i] - dt;

                qr.qualityValues[i] = (oldQ * ((double) diffdt / (double) QUALITY_TIME[i]))
                        + (newQ * ((double) dt / (double) QUALITY_TIME[i]));
            }
        } else {
            qr = new QualityResult();
            qr.qualityValues[QUALITY2_OFFSET] = r.param[j];
            qr.qualityValues[QUALITY12_OFFSET] = r.param[j];
            qr.qualityValues[QUALITY24_OFFSET] = r.param[j];
        } //else

        for (i = 0; i < QUALITY_NUMBER; i++) {
            qr.qualityTimes[i] = now;
        }
        qualityHash.put(r.NodeName, qr);
    } //processQuality

    private void removeExpiredQualities() {
        long now = NTPDate.currentTimeMillis();
        for (Enumeration e = qualityHash.keys(); e.hasMoreElements();) {
            String near = (String) e.nextElement();
            QualityResult qr = (QualityResult) qualityHash.get(near);
            if ((now - qr.qualityTimes[0]) > qualExpiringDelta) {
                logger.log(Level.INFO, "removing link qual with " + near);
                qualityHash.remove(near);
            }
        }
    }

    /* Quality */
    private Result[] informClients() {
        if ((qualityHash == null) || (qualityHash.size() == 0)) {
            return null;
        }
        // when the result will support objects, these results could be sent 
        // all at the same time, minimizing network delay and load

        Result[] rez = null;
        rez = new Result[qualityHash.size()];

        String[] rez_names = { "Qual2h", "Qual12h", "Qual24h" };
        long now = NTPDate.currentTimeMillis();

        int i = (rez.length == qualityHash.size()) ? 0 : 1;
        for (Enumeration e = qualityHash.keys(); e.hasMoreElements();) {
            String near = (String) e.nextElement();
            rez[i] = new Result(farmName, "Peers", near, "TriggerAgent", rez_names);
            rez[i].time = now;
            QualityResult qr = (QualityResult) qualityHash.get(near);
            for (int k = 0; k < QUALITY_NUMBER; k++) {
                rez[i].param[k] = qr.qualityValues[k];
            }
            i++;
        }
        if (logger.isLoggable(Level.FINEST)) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n\nReturning [ " + rez.length + " ] results\n");
            for (int j = 0; j < rez.length; j++) {
                sb.append(" rez [ " + j + " ] = " + rez[j] + " \n");
            }
            logger.log(Level.FINEST, sb.toString());
        }
        return rez;
    }

    private Object alarmResult(boolean fired) {
        Result r = new Result(farmName, null, null, ALARM1, null);
        r.addSet("alarm", fired ? FIRED : 0);
        r.time = NTPDate.currentTimeMillis();
        return r;
    }

    class QualityResult {
        public long qualityTimes[];
        public double qualityValues[];

        QualityResult() {
            qualityTimes = new long[QUALITY_NUMBER];
            qualityValues = new double[QUALITY_NUMBER];
        }
    }

    /**
     * @see lia.Monitor.Filters.GenericMLFilter#getSleepTime()
     */
    @Override
    public long getSleepTime() {
        return 2 * 1000;
    }

    /**
     * @see lia.Monitor.Filters.GenericMLFilter#getFilterPred()
     */
    @Override
    public monPredicate[] getFilterPred() {
        return null;
    }

    /**
     * @see lia.Monitor.Filters.GenericMLFilter#notifyResult(java.lang.Object)
     */
    @Override
    public void notifyResult(Object o) {
        // TODO Auto-generated method stub
        if (o == null) {
            return;
        }

        Result r = null;
        if (o instanceof Result) {
            r = (Result) o;
        } else {
            return;
        }

        try {//publish status
            if (r.ClusterName.equals("Reflector")) {
                if ((r.param != null) && (r.param_name != null) && (r.param.length == r.param_name.length)) {
                    boolean found = false;
                    Double newStatus = status;
                    for (int i = 0; i < r.param_name.length; i++) {
                        if (r.param_name[i].equals("Status")) {
                            newStatus = Double.valueOf(r.param[i]);
                            found = true;
                            break;
                        }
                    }

                    if (found) {
                        if (!newStatus.equals(status) || firstTime) {
                            status = newStatus;
                            firstTime = false;
                            logger.log(Level.INFO, " Publish PandaStatus = [ " + status + " ] ");
                            GMLEPublisher.getInstance().publishNow("PandaStatus", status);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception looking for STATUS", t);
        }

        if (r.ClusterName.equals("Reflector") || r.ClusterName.equals("Peers")) {

            atime1 = NTPDate.currentTimeMillis() + dt1;
            synchronized (sAlarm) {
                if (a1Triggered) {
                    a1Triggered = false;
                    if (!a1Cancelled) {
                        a1Cancelled = true;
                    }
                }
            }
        }

        /* Quality */
        processQuality(r);

    }

    /**
     * @see lia.Monitor.Filters.GenericMLFilter#expressResults()
     */
    @Override
    public Object expressResults() {
        if ((NTPDate.currentTimeMillis() > atime1) && !a1Triggered) {
            logger.log(Level.INFO, " ===============> ALLAAAARMMMM !!!!!!!!!!!!!");
            synchronized (sAlarm) {
                a1Triggered = true;
            }
        }
        if (a1Triggered) {
            // send this continuously while the alarm is on
            return alarmResult(true);
        }
        if (a1Cancelled) {
            logger.log(Level.INFO, " ===============> ALARM CANCELLED !!!! ");
            a1Cancelled = false; // send this only once
            return alarmResult(false);
        }
        removeExpiredQualities();
        if ((lastSent + qualSendDelta) < NTPDate.currentTimeMillis()) {
            lastSent = NTPDate.currentTimeMillis();
            return informClients();
        }
        return null;
    }

}
