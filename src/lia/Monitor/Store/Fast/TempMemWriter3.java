/*
 * $Id: TempMemWriter3.java 7533 2014-09-09 14:29:58Z costing $
 * 
 */
package lia.Monitor.Store.Fast;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Store.Cache;
import lia.Monitor.Store.DataSplitter;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.ExtResult;
import lia.Monitor.monitor.ExtendedResult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.TimestampedResult;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.ntp.NTPDate;
import lia.util.threads.MonALISAExecutors;

/**
 * 
 * @author costing
 * @author ramiro
 * 
 */
public final class TempMemWriter3 extends Writer implements TempMemWriterInterface, AppConfigChangeListener {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(TempMemWriter3.class.getName());

    /**
     * "Farm/Cluster/Node/Parameter" -> list of time-ordered values mapping
     */
    private final Map<String, PriorityQueue<TimestampedResult>> hmData;

    private final ReadWriteLock hmLock;

    /**
     * Buffer read lock
     */
    Lock hmReadLock;

    /**
     * Buffer write lock
     */
    Lock hmWriteLock;

    // the lowest data time in buffer
    private long lMinTime;

    // the highest data time in buffer
    private long lMaxTime;

    /**
     * current size limit of the buffer, depending on the actual memory conditions and the number of values already in the buffer
     * @see #getLimit()
     */
    int iLimit;

    /** 
     * the upper size limit of the buffer, depending on the JVM memory settings
     */
    static int iHardLimit;

    /**
     * lower limit, in case the explicit GC is disabled and the code would clean up too much
     */
    static int iLowLimit;

    // statistics
    private long lServedRequests = 0;

    /**
     * all values ordered by time and with a reference to the actual structure that holds them
     */
    final PriorityQueue<TempMemWriter3Index> silData;

    /**
     * only for debug 
     */
    long lastPrintedStats = 0;

    /**
     * Do not execute gc() too often, even if we really remove some large portions of the data from the buffer
     */
    long lastGC;

    /**
     * What is the minimum time interval to leave between gc() calls?
     * @see #reloadConf()
     */
    static long GC_DELAY = 10 * 1000;

    /**
     * How often to print the debugging ?
     */
    static long STATS_DELAY = 20 * 1000;

    private static int IHARDLIMIT = 0;

    /**
     * Initialize the data structures and determine the upper limit of the buffer.
     */
    public TempMemWriter3() {

        final boolean useHMap = AppConfig.getb("lia.Monitor.Store.Fast.TempMemWriter3.useHashMap", false);
        if (useHMap) {
            hmData = new HashMap<String, PriorityQueue<TimestampedResult>>();
        } else {
            hmData = new TreeMap<String, PriorityQueue<TimestampedResult>>();
        }

        reloadConf();
        lastGC = NTPDate.currentTimeMillis();

        // the locks
        hmLock = new ReentrantReadWriteLock();
        hmReadLock = hmLock.readLock();
        hmWriteLock = hmLock.writeLock();

        lMinTime = lMaxTime = NTPDate.currentTimeMillis();
//        lMinTime = 0;

        calculateHardLimit();

        //do not force realloc -se it's size as big as possible
        //... no other way to not make a System.arraycopy()
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ TMW3 ] Setting silDataSize to " + iHardLimit);
        }
        silData = new PriorityQueue<TempMemWriter3Index>(iHardLimit);
        AppConfig.addNotifier(this);
        iLimit = 32 * 1024;

        MonALISAExecutors.getMLStoreExecutor().scheduleWithFixedDelay(new TempMemWriterTask(), 1500, 1000,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Estimate how many values could we keep in memory at maximum?
     */
    static void calculateHardLimit() {
        if (IHARDLIMIT > 0) {
            iHardLimit = IHARDLIMIT;
            return;
        }

        final long lMax = Runtime.getRuntime().maxMemory() / 1024;

        // the maximum number of values we'll try to keep in memory
        iHardLimit = (int) ((lMax - (15 * 1024)) / 200); // 220 is the approximate size of an entry

        if (iHardLimit < 16) {
            iHardLimit = 16;
        }

        if (iHardLimit > 10240) {
            iHardLimit = 10240;
        }

        iHardLimit *= 1024;
    }

    /**
     * Abstract method in {@link Writer}, ignored in memory buffers.
     */
    @Override
    public final int save() {
        return 0;
    }

    /**
     * Add some data to the memory buffer. This function only finds out the data type and acts accordingly.
     * 
     * @param o
     *            the data to be added, can be Result, ExtendedResult or a Collection
     */
    @Override
    public final void storeData(final Object o) {   	
    	hmWriteLock.lock();
    	
    	try{
	        if (o instanceof ExtendedResult) {
	            addSample((ExtendedResult) o);
	        } else if (o instanceof Result) {
	            addSample((Result) o);
	        } else if (o instanceof ExtResult) {
	            addSample(((ExtResult) o).getResult());
	        } else if (o instanceof eResult) {
	            addSample((eResult) o);
	        } else if (o instanceof Collection<?>) {
	        	for (final Object toStore: (Collection<?>) o){
	        		storeData(toStore);
	        	}
	        }
    	}
    	finally{
    		hmWriteLock.unlock();
    	}
    }

    /**
     * A Result is converted into one or more ExtendedResult objects by adding min=max=value for each parameter.
     * 
     * @param r
     *            the object to be stored in memory
     */
    private void addSample(final Result r) {
        if ((r == null) || (r.param_name == null) || (r.param_name.length <= 0)) {
            return;
        }

        for (int i = Math.min(r.param_name.length, r.param.length) - 1; i >= 0; i--) {
            addData(r.time, r, r.param_name[i], r.param[i], r.param[i], r.param[i]);
        }
    }

    /**
     * An ExtendedResult is split into one or more ExtendedResult objects, one object for each parameter.
     * 
     * @param r
     *            the ExtendedResult object.
     */
    private void addSample(final ExtendedResult r) {
        if ((r == null) || (r.param_name == null) || (r.param_name.length <= 0)) {
            return;
        }

        if (r.param_name.length == 1) {
            addDataToList(r, IDGenerator.generateKey(r, 0), r.time);
        } else {
            for (int i = r.param_name.length - 1; i >= 0; i--) {
                addData(r.time, r, r.param_name[i], r.param[i], r.min, r.max);
            }
        }
    }

    /**
     * A Result is split into one or more ExtendedResult objects, one object for each parameter.
     * 
     * @param r
     *            the ExtendedResult object.
     */
    public final void addSample(final eResult r) {
        if ((r == null) || (r.param_name == null) || (r.param_name.length <= 0)) {
            return;
        }

        if (r.param_name.length == 1) {
            addDataToList(r, IDGenerator.generateKey(r, 0), r.time);
        } else {
            for (int i = r.param_name.length - 1; i >= 0; i--) {
                addData(r.time, r, r.param_name[i], r.param[i]);
            }
        }
    }

    /**
     * For nice printing of debug info.
     */
    @Override
    public final String getTableName() {
        return "TempMemWriter3";
    }

    /**
     * What is the actual time interval that is kept in memory.
     * 
     * @return the interval length, in millis, relative to the absolute current time;
     */
    @Override
    public final long getTotalTime() {
        final long lDiff = NTPDate.currentTimeMillis() - lMinTime;

        return lDiff > 0 ? lDiff : 1000;
    }

    /**
     * Get the current data limit.
     */
    @Override
    public final int getLimit() {
        return iLimit;
    }

    /**
     * Get the maximum data size.
     */
    @Override
    public final int getHardLimit() {
        return iHardLimit;
    }

    /**
     * Get the current number of values stored in the buffer.
     */
    @Override
    public final int getSize() {
        return silData.size();
    }

    /**
     * How many queries were served from this buffer
     */
    @Override
    public final long getServedRequests() {
        return lServedRequests;
    }

    /**
     * Defined in {@link Writer}, ignored in memory implementations of it.
     */
    @Override
    public final boolean cleanup(boolean bCleanHash) {
        return true;
    }

    /**
     * Build an ExtendedResult for each parameter.
     */
    private void addData(final long rectime, final Result r, final String sParamName, final double mval,
            final double mmin, final double mmax) {
        final ExtendedResult er = new ExtendedResult();

        er.FarmName = r.FarmName;
        er.ClusterName = r.ClusterName;
        er.NodeName = r.NodeName;
        er.param_name = new String[]{sParamName};
        er.param = new double[]{mval};
        er.min = mmin;
        er.max = mmax;
        er.time = rectime;

        addDataToList(er, IDGenerator.generateKey(er, 0), rectime);
    }

    private void addData(final long rectime, final eResult r, final String sParamName, final Object oParamValue) {
        final eResult er = new eResult(r.FarmName, r.ClusterName, r.NodeName, null, new String[]{sParamName});
        er.param[0] = oParamValue;
        er.time = rectime;

        addDataToList(er, IDGenerator.generateKey(er, 0), rectime);
    }

    /**
     * A comparator for the history elements that sorts the entries by time
     */
    static final class CacheComparator implements Comparator<TimestampedResult>, Serializable {
        private static final long serialVersionUID = 7042232616325995158L;

        @Override
        public final int compare(final TimestampedResult o1, final TimestampedResult o2) {
            final long lDiff = o1.getTime() - o2.getTime();

            return lDiff < 0 ? -1 : (lDiff > 0 ? 1 : 0);
        }
    }

    /**
     * Compare two entries from the buffer
     */
    private static final Comparator<TimestampedResult> erComparator = new CacheComparator();

    /**
     * It will remove the latest value from this queue
     * 
     */
    final void removeEntryFromList() {
        /*
         * NO need to acquire a lock ... because this function is called 
         * only if a Write Lock is already acquired !
         */
        try {
            final TempMemWriter3Index idx = silData.poll();
            if (idx == null) {
                logger.log(Level.WARNING, "\n\n [ TMW3 ] [ StoreException ] silData.poll() returned null \n\n");
            } else {
                final Object o = idx.llData.poll();

                if (idx.llData.size() == 0) {
                    final String sKey = IDGenerator.generateKey(o, 0);

                    if (sKey == null) {
                        logger.warning(" [ TMW3 ] : cannot remove ll from hmData because sKey==null for " + o);
                    } else {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.finest(" [ TMW3 ] : removing key " + sKey + " from hmData");
                        }
                        hmData.remove(sKey);
                    }
                }
            }
        } catch (final Throwable t) {
            logger.log(Level.WARNING, " [ TMW 3 ] [ removeEntryFromList ] [ HANDLED ] Got exc ", t);
        }
    }

    /**
     * MUST be called with Write lock acquired !
     */
    final void recalcTimeFrame() {
        if (silData.size() > 0) {
            lMinTime = silData.peek().time;
        } else {
            lMinTime = NTPDate.currentTimeMillis();
        }
    }

    private final void addDataToList(final TimestampedResult o, final String sKey, final long lTime) {
        // do not accept cache polluting values
        if (lTime < lMinTime) {
            return;
        }

        try {
            if (silData.size() > (iHardLimit - 1)) {
                //make way for ... I'm coming
                //recalc also the time frame
                removeEntryFromList();
                recalcTimeFrame();
            }

            PriorityQueue<TimestampedResult> ll = hmData.get(sKey);

            if (ll == null) {
                final int seriesCount = hmData.size();

                final int llLen = Math.max(16, seriesCount > 0 ? (silData.size() / seriesCount) + 1 : 0);

                ll = new PriorityQueue<TimestampedResult>(llLen, erComparator);

                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, " [ TMW3 ] Adding new Key " + sKey + " PQSize = " + llLen);
                }

                hmData.put(sKey, ll);
            }

            ll.offer(o);

            silData.offer(new TempMemWriter3Index(lTime, ll));

            if (lTime > lMaxTime) {
                lMaxTime = lTime;
            }

        } catch (final Throwable t) {
            logger.log(Level.WARNING, " Got exc adding data to TMW3", t);
        }
    }

    /**
     * Debugging method
     * 
     * @return a string with all the internal parameters
     */
    String getStatus() {

        final long total = Runtime.getRuntime().maxMemory();
        final long jvmFree = Runtime.getRuntime().freeMemory();
        final long jvmTotal = Runtime.getRuntime().totalMemory();
        final long free = jvmFree + (total - jvmTotal);

        final StringBuilder sb = new StringBuilder();
        sb.append("\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");
        sb.append(" | JVM_MAX_Mem: ").append(total);
        sb.append(" | JVM_Total_Mem: ").append(jvmTotal);
        sb.append(" | JVM_Free: ").append(jvmFree);
        sb.append(" | Free: ").append(free);
        sb.append(" | iLimit: ").append(iLimit);
        sb.append(" | iHardLimit: ").append(iHardLimit);
        sb.append(" | size(): ").append(getSize());
        sb.append(" | hmData.size(): ").append(hmData.size());
        sb.append(" | silData.size(): ").append(silData.size());
        sb.append("\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");

        return sb.toString();
    }

    /**
     * Periodic task to determine the data count limit and, if necessary, to clean the values that exceed the limit
     */
    final class TempMemWriterTask implements Runnable {

        private int iRuns = 0;

        private final void recalculate() {
            if (iLowLimit == iHardLimit) {
                iLimit = iHardLimit;
                return;
            }

            final long total = Runtime.getRuntime().maxMemory();
            final long jvmFree = Runtime.getRuntime().freeMemory();
            final long jvmTotal = Runtime.getRuntime().totalMemory();
            final long free = jvmFree + (total - jvmTotal);

            long t = total / 6; // try to keep ~15% free memory at all times

            if (((free < t) && (iLimit > 16)) || (iLimit > iHardLimit)
                    || ((jvmFree < (12 * 1024 * 1024)) && (jvmTotal > (32 * 1024 * 1024)))) {// TO SHRINK
                iLimit = (iLimit / 10) * 9;
                if (iLimit < 32) {//Just make sure that we do not get in any trouble ... We should be able to keep at least 32 values in mem
                    iLimit = 32;
                }
            } else {// OR NOT TO SHRINK ... This is the question :)
                int dCount = silData.size();
                if (free > (t * 2)) {
                    while (((dCount > ((4 * iLimit) / 5)) || ((iLimit - dCount) < 1000)) && (iLimit < iHardLimit)) {
                        iLimit = ((iLimit * 11) / 10) + 16;
                    }
                }
            }

            if (iLimit > iHardLimit) {// TO SHRINK SHOULD BE THE FINAL ANSWER ;) !
                iLimit = iHardLimit;
            }

            if (iLimit < iLowLimit) {
                iLimit = iLowLimit;
            }

            if (logger.isLoggable(Level.FINEST) && ((lastPrintedStats + STATS_DELAY) < NTPDate.currentTimeMillis())) {
                lastPrintedStats = NTPDate.currentTimeMillis();
                logger.log(Level.FINEST, getStatus());
            }

        }

        @Override
        public void run() {
            final int iOldLimit = iLimit;

            if (iRuns > 60) {
                // every 30 seconds or so recalculate the hard limit
                calculateHardLimit();
                iRuns = 0;
            }

            recalculate();

            hmWriteLock.lock();
            try {
                // adjust the limit size
                // if (iLimit>iOldLimit)
                // silData.ensureCapacity((iLimit*23)/20);

                // try to delete many values
                int dCount = silData.size();
                if (dCount >= ((21 * iLimit) / 20)) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, " [ TMW3 ] cleanup: iOldLimit=" + iOldLimit + getStatus());
                    }

                    final int iDownLimit = (19 * iLimit) / 20;

                    final int lNumberOfValuesToRemove = dCount - iDownLimit;

                    for (int i = lNumberOfValuesToRemove - 1; i >= 0; i--) {
                        removeEntryFromList();
                    }

                    recalcTimeFrame();

                    if (logger.isLoggable(Level.FINEST)) {
                        logger.finest(" [ TMW3 ]  cleanup for " + lNumberOfValuesToRemove + " values" + getStatus());
                    }

                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "TMW3: before System.gc(): silData.size() = " + silData.size()
                                + ", iLimit " + iLimit + " : " + Runtime.getRuntime().freeMemory() + " free");
                    }

                    if ((lastGC + GC_DELAY) < NTPDate.currentTimeMillis()) {
                        System.gc();
                        lastGC = NTPDate.currentTimeMillis();
                    }
                }
            } catch (Throwable ignore) {
                // nothing
            } finally {
                hmWriteLock.unlock();
            }

        }
    }

    /**
     * Get the data for this list of predicates in the form of a DataSplitter
     * 
     * @param pred
     * @return the DataSplitter with the values that match this predicate
     */
    public final DataSplitter getDataSplitter(final monPredicate pred[]) {
        hmReadLock.lock();

        try {
            lServedRequests++;
            return Cache.getDataSplitter(hmData, pred, null, true);
        } finally {
            hmReadLock.unlock();
        }
    }

    /**
     * Avem nevoie de asta ?
     * Poate ar fi bine sa fie un alt hash doar cu ultimele valori ( )
     * Am vazut ca se mai tin si in Cache!
     */
    @Override
    public final ArrayList<Object> getLatestValues() {
        return new ArrayList<Object>();

        //        hmReadLock.lock();
        //        final ArrayList al = new ArrayList(hmData.size());
        //        try {
        //            final Iterator it = hmData.values().iterator();
        //
        //            PriorityQueue ll;
        //            
        //            while (it.hasNext()){
        //            ll = (PriorityQueue) it.next();
        //        
        //            if (ll.size()>0)
        //                al.add(ll.());
        //            }
        //            
        //        } catch (Throwable t) {
        //            
        //        } finally {
        //            hmReadLock.unlock();
        //        }
        //        return al;
    }

    /**
     * Reload configuration parameters from AppConfig
     */
    public static void reloadConf() {
        STATS_DELAY = AppConfig.getl("lia.Monitor.Store.Fast.TempMemWriter3.STATS_DELAY", 20) * 1000;

        IHARDLIMIT = AppConfig.geti("lia.Monitor.Store.MemoryBufferSize", 0) * 1024;

        GC_DELAY = AppConfig.getl("lia.Monitor.Store.GC_DELAY", 60 * 1000);

        iLowLimit = AppConfig.geti("lia.Monitor.Store.Fast.TempMemWriter3.lowLimit", 0) * 1024;

        if ((IHARDLIMIT > 0) && (iLowLimit > IHARDLIMIT)) {
            iLowLimit = IHARDLIMIT;
        }

        calculateHardLimit();

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ TMW3 ] reloadConf() ");
        }
    }

    @Override
    public void notifyAppConfigChanged() {
        reloadConf();
    }

}
