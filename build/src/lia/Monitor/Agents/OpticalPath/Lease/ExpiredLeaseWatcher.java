package lia.Monitor.Agents.OpticalPath.Lease;

import java.util.ArrayList;
import java.util.Date;
import java.util.PriorityQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.ntp.NTPDate;

/**
 * 
 * Helper class used to notify the <code>LeaseEventListener</code>
 * if its <code>Lease</code> expired
 * 
 */
public class ExpiredLeaseWatcher extends Thread {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(ExpiredLeaseWatcher.class.getName());

    /**
     * @see http://www.cs.umd.edu/~pugh/java/memoryModel/jsr-133-faq.html
     */
    private static class ExpiredLeaseWatcherHolder {
        private static final ExpiredLeaseWatcher _thisInstance = new ExpiredLeaseWatcher();
    }

    private boolean hasToRun;

    private final PriorityQueue<Lease> leases;
    private final Object syncLeases;

    private ExpiredLeaseWatcher() {
        super(" ( ML ) ExpiredLeaseWatcher ");
        try {
            setDaemon(true);
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Cannot set daemon ExpiredLeaseWatcher !");
        }
        hasToRun = true;
        leases = new PriorityQueue<Lease>();
        syncLeases = new Object();
        start();
    }

    public static final ExpiredLeaseWatcher getInstance() {
        return ExpiredLeaseWatcherHolder._thisInstance;
    }

    /**
     * hmm ? Do you really want this ?
     */
    public void stopIt() {
        hasToRun = false;
    }

    public Lease add(Lease lease) {

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "\n\n [ ExpiredLeaseWatcher ] adding Lease " + lease);
        }

        synchronized (syncLeases) {
            leases.offer(lease);
            syncLeases.notify();
            return lease;
        }
    }

    public void remove(Lease lease) {

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "\n\n [ ExpiredLeaseWatcher ] removing Lease " + lease);
        }

        synchronized (syncLeases) {
            leases.remove(lease);
            syncLeases.notify();
        }

    }

    public void renew(Lease lease, long period) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ ExpiredLeaseWatcher ] Renewing lease: " + lease + " for dT = " + period);
        }
        synchronized (syncLeases) {//it does not get sort ... if not removed and added again!!!
            if (leases.remove(lease)) {
                lease.renew(period);
                leases.offer(lease);
            } else {
                logger.log(Level.INFO, " [ ExpiredLeaseWatcher ] [ HANDLED ] NoSuch lease " + lease
                        + " in my list ... Maybe it already expired");
            }
            syncLeases.notify();
        }
    }

    //it is already in in synch block 
    private ArrayList<Lease> getExpiredLeases(long now) {
        ArrayList<Lease> expiredLeases = new ArrayList<Lease>();
        while (leases.size() > 0) {
            Lease head = leases.peek();
            long waitTime = head.getExpireTime() - now;
            if (waitTime <= 0) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "Adding EXPIRED lease " + head + " leases size " + leases.size()
                            + " now: " + new Date(now) + " now " + now + " lease expiretime " + head.getExpireTime());
                }
                expiredLeases.add(leases.poll());
            } else {
                break;
            }
        }//while

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ ExpiredLeaseWatcher ] Expired Leases Size " + expiredLeases.size()
                    + " lease size = " + leases.size());
        }
        return expiredLeases;
    }

    @Override
    public void run() {
        logger.log(Level.INFO, "\n\n [ ExpiredLeaseWatcher ]  ExpiredLeaseWatcher started ... ");
        while (hasToRun) {
            ArrayList<Lease> expiredLeases = null;
            try {
                synchronized (syncLeases) {
                    while (leases.size() == 0) {
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST,
                                    "\n\n [ ExpiredLeaseWatcher ] ExpiredLeaseWatcher no leases to watch ... will wait()");
                        }
                        try {
                            syncLeases.wait();
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, " [ ExpiredLeaseWatcher ] Got Exception while waiting ...", t);
                        }
                    }//wile()

                    long now = NTPDate.currentTimeMillis();
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST,
                                "\n\n [ ExpiredLeaseWatcher ] ExpiredLeaseWatcher getting expired leases now: [ " + now
                                        + " ] ms ... I have " + leases.size() + " leases");
                    }

                    //Just to keep the sync block smaller as possible
                    expiredLeases = getExpiredLeases(now);
                    if (expiredLeases.size() == 0) {//nothing expired ...
                        Lease head = leases.peek();
                        long waitTime = head.getExpireTime() - now;
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST,
                                    "\n\n [ ExpiredLeaseWatcher ] ExpiredLeaseWatcher will sleep for [ " + waitTime
                                            + " ] ms ... I have " + leases.size() + " leases");
                        }

                        long startSleepTime = System.currentTimeMillis();
                        try {
                            syncLeases.wait(waitTime);
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, "Got exception ex", t);
                        }

                        if (logger.isLoggable(Level.FINEST)) {
                            long dtSleep = System.currentTimeMillis() - startSleepTime;
                            logger.log(Level.FINEST,
                                    "\n\n [ ExpiredLeaseWatcher ] ExpiredLeaseWatcher desired sleep [ " + waitTime
                                            + " ] ms ... I have slept for [ " + dtSleep + " ] ms. Leses Size = "
                                            + leases.size() + " leases");
                        }
                    }
                }//sync

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "\n\n [ ExpiredLeaseWatcher ] Start Removing expired leases ... [ "
                            + expiredLeases.size());
                }
                if (expiredLeases.size() > 0) {
                    //notify expired leases
                    for (int i = 0; i < expiredLeases.size(); i++) {
                        Lease l = expiredLeases.get(i);
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, "\n\n [ ExpiredLeaseWatcher ] Notifying expired Lease ... ");
                        }
                        l.getListener().notify(new LeaseEvent(l, LeaseEvent.LEASE_EXPIRED));
                    }//for()
                }//if()
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ ExpiredLeaseWatcher ] ExpiredLeaseWatcher got exception main loop", t);
            }
        }//while - main loop
        logger.log(Level.INFO, "[ ExpiredLeaseWatcher ] ExpiredLeaseWatcher stopped !!! \n\n");
    }
}
