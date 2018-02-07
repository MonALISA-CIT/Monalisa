package lia.util.net;

import java.net.InetAddress;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Farm.FarmMonitor;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.util.Utils;
import lia.util.mail.MailFactory;
import lia.util.ntp.NTPDate;

/**
 * The FloodControl class can be used to determine if a packet coming from a source 
 * should be dropped. The decision is based on the frequency, the goal being to
 * keep it at around maxMsgRate messages per second in average, from the same source.  
 */
public class FloodControl implements AppConfigChangeListener {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(FloodControl.class.getName());

    /** 
     * In case of continuous flood, don't send alert more often than 1800 seconds (= 30 min)
     * Can be set in ml.properties with lia.util.net.FloodControl.ALERT_INTERVAL 
     */
    private static final AtomicLong ALERT_INTERVAL = new AtomicLong(1800 * 1000);
    private final static String[] RCPT = new String[] { "mlstatus@monalisa.cern.ch", "Ramiro.Voicu@cern.ch",
            "Costin.Grigoras@cern.ch" };

    /** when the last alert was sent */
    private static final AtomicLong lastAlertTime = new AtomicLong(0);

    volatile int maxMsgRate = 50; // maximum rate of messages accepted from a sender
    int maxAddrInfos = 5000; // maximum number of flooding hosts
    long cleanupTimeout = 60; // after how many seconds we should do a cleanup, if maxAddrInfos not reached
    double hWeight = Math.exp(-5.0 / 60.0); // the probability to drop a message (0.92)

    long lastCleanupTime; // when was last cleanup done

    final Map<InetAddress, AddrInfo> hmAddrInfo;

    /** holds flood-related data about an address */
    class AddrInfo {
        Object addrID; // addr ID; for examle the InetAddress; 
        long prvTime = 0; // previous time when we got messages (i.e. prev. second)
        double prvRecv = 0; // how many msgs were received (i.e. NOT dropped)
        long prvDrop = 0; // how many were dropped
        long crtTime = 0; // current second
        long crtRecv = 0; // how many msgs were received (i.e. NOT dropped)
        long crtDrop = 0; // how many msgs were dropped

        public AddrInfo(Object addrID) {
            this.addrID = addrID;
        }

        @Override
        public String toString() {
            return addrID.toString() + " prvRecv:" + prvRecv + " prvDrop:" + prvDrop + " crtRecv:" + crtRecv
                    + " crtDrop:" + crtDrop + " maxMsgRate:" + maxMsgRate;
        }
    }

    public FloodControl() {
        hmAddrInfo = new HashMap<InetAddress, AddrInfo>();
        AppConfig.addNotifier(this);
        notifyAppConfigChanged();
    }

    /** 
     * set the number of messages per second, that in average should not be dropped,
     * on a per address basis.
     */
    public void setMaxMsgRate(int rate) {
        maxMsgRate = rate;
    }

    /** 
     * based on the frequency of the datagrams received from this source, 
     * decide whether we should drop or not the received packet.
     */
    public boolean shouldDrop(InetAddress addrID) {
        final long nowSeconds = TimeUnit.NANOSECONDS.toSeconds(Utils.nanoNow()); // current time in seconds from Epoch
        AddrInfo ai = hmAddrInfo.get(addrID);
        if (ai == null) {
            ai = new AddrInfo(addrID);
            hmAddrInfo.put(addrID, ai);
        }
        final boolean dropIt = shouldDrop(ai, nowSeconds);

        if ((hmAddrInfo.size() > maxAddrInfos) || ((nowSeconds - lastCleanupTime) > cleanupTimeout)) {
            doCleanup(nowSeconds);
            lastCleanupTime = nowSeconds;
        }
        return dropIt;
    }

    /**
     * do the actual dropping test for the corresponding addrInfo and
     * the current time, in seconds from Epoch.
     */
    private boolean shouldDrop(AddrInfo ai, long secondsNow) {
        if (secondsNow != ai.crtTime) {
            // new time, update previous counters
            ai.prvRecv = ((hWeight * ai.prvRecv) + ((1.0 - hWeight) * ai.crtRecv)) / (secondsNow - ai.crtTime);

            Level beVerbose = ((ai.prvDrop != 0) || (ai.crtDrop != 0) ? Level.WARNING : Level.FINE);
            if (logger.isLoggable(beVerbose)) {
                if ((ai.prvDrop != 0) || (ai.crtDrop != 0)) {
                    floodAlert(ai);
                }
                logger.log(beVerbose, "ACCEPT/DROP last second: " + ai.toString());
            }

            ai.prvDrop = ai.crtDrop;
            ai.prvTime = ai.crtTime;
            // reset current counters
            ai.crtTime = secondsNow;
            ai.crtRecv = 0;
            ai.crtDrop = 0;
        }

        // compute the history
        int valRecv = (int) (((ai.prvRecv * hWeight) + (ai.crtRecv * (1.0 - hWeight))) / (secondsNow - ai.prvTime));
        int level = maxMsgRate / 10; // when start dropping messages

        boolean dropIt = (valRecv > level ? (Math.random() * level) > (maxMsgRate - valRecv) : false);

        // count the sent and dropped messages
        if (dropIt) {
            ai.crtDrop++;
        } else {
            ai.crtRecv++;
        }

        if (logger.isLoggable(Level.FINEST)) {
            System.out.print(dropIt ? "." : "#");
        }

        return dropIt;
    }

    /** 
     * walk through the existing AddrInfos and do some cleanup
     */
    private void doCleanup(long secondsTime) {
        final boolean bFiner = logger.isLoggable(Level.FINER);
        // first, delete AddrInfos inactive for more than 10 seconds
        for (Iterator<AddrInfo> ait = hmAddrInfo.values().iterator(); ait.hasNext();) {
            final AddrInfo ai = ait.next();
            if ((secondsTime - ai.crtTime) > 10) {
                ait.remove();
                if (bFiner) {
                    logger.finer("Removed 1: " + ai.toString());
                }
            }
        }
        int toDelete = hmAddrInfo.size() - (int) (0.75 * maxAddrInfos);
        // remove others, if still needed
        for (Iterator<AddrInfo> ait = hmAddrInfo.values().iterator(); (toDelete > 0) && ait.hasNext();) {
            final AddrInfo ai = ait.next();
            ait.remove();
            if (bFiner) {
                logger.finer("Removed 2: " + ai.toString());
            }
        }
    }

    /** The configuration in ml.properties has changed. Reread also the ALERT_INTERVAL property */
    @Override
    public void notifyAppConfigChanged() {
        final long lDef = TimeUnit.MINUTES.toMillis(30);
        try {
            ALERT_INTERVAL.set(TimeUnit.SECONDS.toMillis(AppConfig.getl("lia.util.net.FloodControl.ALERT_INTERVAL",
                    lDef)));
        } catch (Throwable t) {
            logger.log(Level.WARNING,
                    "Cannot understand the value for the lia.util.net.FloodControl.ALERT_INTERVAL property. Using default 1800.");
            ALERT_INTERVAL.set(lDef);
        }
        logger.log(
                Level.INFO,
                " [ FloodControl ] (re)loaded new ALERT_INTERVAL = "
                        + TimeUnit.MILLISECONDS.toMinutes(ALERT_INTERVAL.get()) + " minutes");
    }

    /** Something is wrong. If I haven't already sent the alert sooner than ALERT_INTERVAL, do it */
    private void floodAlert(AddrInfo ai) {
        final long now = Utils.nanoNow();
        if (TimeUnit.NANOSECONDS.toMillis(now - lastAlertTime.get()) > ALERT_INTERVAL.get()) {
            lastAlertTime.set(now);
            try {
                String msg = "Sys Date: " + new Date(System.currentTimeMillis()) + "\nNTP Date: "
                        + new Date(NTPDate.currentTimeMillis()) + "\n\nACCEPT/DROP last second from: " + ai.toString()
                        + "\n\nIf flood persists, next alert will be in "
                        + TimeUnit.MILLISECONDS.toMinutes(ALERT_INTERVAL.get()) + " min.";
                MailFactory.getMailSender().sendMessage("mlstatus@monalisa.cern.ch", RCPT,
                        "[ FloodControl ] ALERT @ " + FarmMonitor.getStandardEmailSubject(), msg, true);
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "Error sending e-mail alert!", t);
                }
            }
        }
    }
}
