package lia.Monitor.Agents.OpticalPath.Lease;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Agents.OpticalPath.MLCopyAgent;

/**
 *  This class is responsible for renewing all the remote leases
 */
public class LeaseRenewalManager implements LeaseEventListener, LeaseRenewal {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(LeaseRenewalManager.class.getName());

    /**
     * @see http://www.cs.umd.edu/~pugh/java/memoryModel/jsr-133-faq.html
     */
    private static final class LeaseRenewalManagerHolder {
        private static final LeaseRenewalManager _theInstance = new LeaseRenewalManager();
    }

    private final HashMap<Long, Lease> leasesMap;
    private final ExpiredLeaseWatcher elw;

    public static LeaseRenewalManager getInstance() {
        return LeaseRenewalManagerHolder._theInstance;
    }

    private final class LeaseRenewalTask implements Runnable {
        Lease lease;

        LeaseRenewalTask(Lease lease) {
            this.lease = lease;
        }

        @Override
        public void run() {
            try {
                logger.log(Level.WARNING, " [ LRM ] Renew " + lease);
                lease.getLeaseRenewal().renew(lease);
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ LRM ] Got exc while renew a lease", t);
            }
        }
    }

    private LeaseRenewalManager() {
        leasesMap = new HashMap<Long, Lease>();
        elw = ExpiredLeaseWatcher.getInstance();
    }

    public synchronized void add(Lease lease) {

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ LRM ] Adding lease for " + lease);
        }

        if (lease.getListener() == null) {
            lease.setListener(this);
        }
        leasesMap.put(lease.getLeaseID(), lease);
        elw.add(lease);
    }

    public synchronized void remove(Lease lease) {
        leasesMap.remove(lease.getLeaseID());
        elw.remove(lease);
    }

    public void remove(String sessionID) {

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ LRM ] Removing leases for: " + sessionID);
        }

        if (sessionID == null) {
            return;
        }

        int leasesNo = 0;
        synchronized (this) {
            for (Iterator<Map.Entry<Long, Lease>> it = leasesMap.entrySet().iterator(); it.hasNext();) {
                Entry<Long, Lease> mapEntry = it.next();
                Lease lease = mapEntry.getValue();

                if (lease.getSessionID().equals(sessionID)) {
                    leasesNo++;
                    elw.remove(lease);
                    it.remove();
                    logger.log(Level.INFO, "\n\n [ LRM ] Rmoving leases " + lease);
                }
            }
        }

        logger.log(Level.INFO, "\n\n [ LRM ] ALL Leases [ " + leasesNo + " ] for " + sessionID + " ... removed.");
    }

    @Override
    public synchronized void notify(LeaseEvent event) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.INFO, "\n\n [ LRM ] LeaseEvent for lease " + event.lease);
        }

        Lease l = leasesMap.get(event.lease.getLeaseID());
        if (l != null) {
            l.renew();
            elw.add(l);
        }

        if (l != null) {//already removed
            MLCopyAgent.getExecutor().execute(new LeaseRenewalTask(l));
        }
    }

    /**
     * @param lease  
     */
    @Override
    public boolean renew(Lease lease) {
        // TODO Auto-generated method stub
        return false;
    }

    /**
     * @param sessionID  
     */
    @Override
    public boolean renewAll(String sessionID) {
        // TODO Auto-generated method stub
        return false;
    }

}
