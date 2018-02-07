/*
 * $Id: TempMemWriter4.java 7533 2014-09-09 14:29:58Z costing $
 */
package lia.Monitor.Store.Fast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Store.Cache;
import lia.Monitor.Store.DataSplitter;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ExtendedResult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.TimestampedResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.ntp.NTPDate;
import lia.util.threads.MonALISAExecutors;

/**
 * 
 * @author costing
 */
public final class TempMemWriter4 extends Writer implements TempMemWriterInterface {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(TempMemWriter4.class.getName());

    private static long SEQ = 0;

    /**
     * "Farm/Cluster/Node/Parameter" -> list of time-ordered values mapping
     */
    HashMap<String, Set<ExtendedResultPointer>> hmData;

    /** the lowest data time in buffer */
    public long lMinTime;

    /** the highest data time in buffer */
    public long lMaxTime;

    /**
     * current size limit of the buffer, depeding on the actual memory conditions and the number of values already in the buffer 
     */
    int iLimit;

    /** the upper size limit of the buffer, depending on the jvm memory settings */
    int iHardLimit;

    /** statistics */
    public long lServedRequests = 0;

    /** how many values are currently stored in the buffer */
    int dataCount = 0;

    /**
     * Lock
     */
    final Object sync = new Object();

    /**
     * Set a new upper limit for the data count.
     * 
     * @param i
     *            the new maximum number of values from the memory buffer, in
     *            <i>K</i>
     */
    public void setHardLimit(int i) {
        if ((i < 64) || (i > 10240)) {
            return;
        }

        iHardLimit = i * 1024;
    }

    /**
     * Initialize the data structures and determine the upper limit of the
     * buffer.
     */
    public TempMemWriter4() {
        hmData = new HashMap<String, Set<ExtendedResultPointer>>();

        lMinTime = lMaxTime = NTPDate.currentTimeMillis();

        MonALISAExecutors.getMLStoreExecutor().scheduleWithFixedDelay(new TempMemWriterTask(), 500, 1000,
                TimeUnit.MILLISECONDS);

        iHardLimit = 256;

        long lMax = Runtime.getRuntime().maxMemory() / (1024L * 1024L);

        if (lMax >= 120) {
            iHardLimit = 512;
        }
        if (lMax >= 300) {
            iHardLimit = 1024;
        }
        if (lMax >= 500) {
            iHardLimit = 2048;
        }
        if (lMax >= 1000) {
            iHardLimit = 8192;
        }

        try {
            iHardLimit = Integer.parseInt(AppConfig.getProperty("lia.Monitor.Store.MemoryBufferSize", "" + iHardLimit));
        } catch (Exception e) {
            // ignore
        }

        if (iHardLimit < 64) {
            iHardLimit = 64;
        }

        if (iHardLimit > 10240) {
            iHardLimit = 10240;
        }

        iHardLimit *= 1024;

        iLimit = 64 * 1024;
    }

    /**
     * Abstract method in {@link Writer}, ignored in memory buffers.
     */
    @Override
    public final int save() {
        return 0;
    }

    /**
     * Add some data to the memory buffer. This function only finds out the data
     * type and acts accordingly.
     * 
     * @param o
     *            the data to be added, can be Result, ExtendedResult or a
     *            Collection
     */
    @Override
    public final void storeData(final Object o) {
        if (o instanceof ExtendedResult) {
            addSample((ExtendedResult) o);
        } else if (o instanceof Result) {
            addSample((Result) o);
        } else if (o instanceof Collection<?>) {
            Iterator<?> it = ((Collection<?>) o).iterator();

            while (it.hasNext()) {
                addSample(it.next());
            }
        }
    }

    /**
     * A Result is converted into one or more ExtendedResult objects by adding
     * min=max=value for each parameter.
     * 
     * @param r
     *            the object to be stored in memory
     */
    private void addSample(Result r) {
        if ((r == null) || (r.param_name == null) || (r.param_name.length <= 0)) {
            return;
        }

        for (int i = 0; i < r.param_name.length; i++) {
            addData(r.time, r, r.param_name[i], r.param[i], r.param[i], r.param[i]);
        }
    }

    /**
     * An ExtendedResult is split into one or more ExtendedResult objects, one
     * object for each parameter.
     * 
     * @param r
     *            the ExtendedResult object.
     */
    private void addSample(ExtendedResult r) {
        if ((r == null) || (r.param_name == null)) {
            return;
        }

        if (r.param_name.length == 1) {
            addDataToList(r);
        } else {
            for (int i = 0; i < r.param_name.length; i++) {
                addData(r.time, r, r.param_name[i], r.param[i], r.min, r.max);
            }
        }
    }

    /**
     * For nice printing of debug info.
     */
    @Override
    public final String getTableName() {
        return "TempMemWriter4";
    }

    /**
     * What is the actual time interval that is kept in memory.
     * 
     * @return the interval length, in millis, relative to the absolute current
     *         time;
     */
    @Override
    public final long getTotalTime() {
        return NTPDate.currentTimeMillis() - lMinTime;
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
        return dataCount;
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
        er.addSet(sParamName, mval);
        er.min = mmin;
        er.max = mmax;
        er.time = rectime;

        addDataToList(er);
    }

    /**
     * A new sample was received.
     * 
     * First the (Farm/Cluster/Node/Parameter) pair is looked up in the map: -
     * if there is an entry for it then add the new value to the existing list -
     * otherwise create an entry in the hash and add one Pointer to the
     * llStartPointers list
     */
    private final void addDataToList(final ExtendedResult er) {

        String sKey = IDGenerator.generateKey(er, 0);
        synchronized (sync) {

            Set<ExtendedResultPointer> ll = hmData.get(sKey);

            if (ll == null) {
                ll = new TreeSet<ExtendedResultPointer>();
                hmData.put(sKey, ll);

            }

            ExtendedResultPointer ep = new ExtendedResultPointer(er);
            llStartPointers.add(ep);
            ll.add(ep);

            dataCount++;
            if (er.time > lMaxTime) {
                lMaxTime = er.time;
            }
        }
    }

    /**
     * @return sequence
     */
    static final synchronized long getSeq() {
        return SEQ++;
    }

    private static final class ExtendedResultPointer implements Comparable<ExtendedResultPointer> {
        ExtendedResult er;

        long seq;

        ExtendedResultPointer(ExtendedResult result) {
            this.er = result;
            seq = getSeq();
        }

        @Override
        public int compareTo(final ExtendedResultPointer e1) {
            if (e1 == null) {
                return -1;
            }

            long diff = er.time - e1.er.time;

            if (diff < 0) {
                return -1;//consistent with equals
            }
            if (diff > 0) {
                return 1;
            }
            if (diff == 0) {

                if (seq > e1.seq) {
                    return 1;
                }
                if (seq < e1.seq) {
                    return -1;
                }
                return 0;
            }
            return 1;
        }
    }

    /**
     * Periodic task to determine the data count limit and, if necessary, to
     * clean the values that exceed the limit
     */
    final class TempMemWriterTask implements Runnable {
        @Override
        public void run() {
            synchronized (sync) {
                final int iOldLimit = iLimit;

                // adjust the limit size
                final long total = Runtime.getRuntime().maxMemory(); // instead of
                // totalMemory()
                final long free = Runtime.getRuntime().freeMemory() + (total - Runtime.getRuntime().totalMemory());

                long t = total / 10;
                if (t > (20 * 1024 * 1024)) {
                    t = 20 * 1024 * 1024;
                }

                if (((free < t) && (iLimit > 16)) || (iLimit > iHardLimit)) {
                    iLimit = (iLimit / 3) * 2;
                } else {
                    while ((free > (t * 2)) && ((dataCount > ((2 * iLimit) / 3)) || ((iLimit - dataCount) < 1000))
                            && (iLimit < iHardLimit)) {
                        iLimit = (iLimit * 3) / 2;
                    }
                }

                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "Readjusted limit to " + iLimit + " (" + iOldLimit + ")/" + iHardLimit
                            + " because : " + free + "/" + total + " : " + dataCount);
                }

                if ((iOldLimit != iLimit) && logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Readjusted limit to " + iLimit + " (" + iOldLimit + ")/" + iHardLimit
                            + " because : " + free + "/" + total + " : " + dataCount);
                }

                // try to delete many values at the same time
                if ((dataCount > ((11 * iLimit) / 10)) || (free < (5 * 1024 * 1024))) {
                    int iDownLimit = (9 * iLimit) / 10;

                    long lStart = NTPDate.currentTimeMillis();

                    int lNumberOfValuesToRemove = dataCount - iDownLimit;

                    if ((lNumberOfValuesToRemove > 0) && !removeHead(lNumberOfValuesToRemove)) {
                        logger.log(Level.INFO, "dataCount consistency check forced");

                        dataCount = 0;

                        Iterator<Set<ExtendedResultPointer>> it = hmData.values().iterator();

                        while (it.hasNext()) {
                            dataCount += it.next().size();
                        }
                    }

                    //if (lNumberOfValuesToRemove>0){
                    //System.err.println("TMW4: System.gc() : "+lNumberOfValuesToRemove+", "+dataCount+", "+iLimit+", "+iOldLimit);
                    //System.gc();
                    //}

                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "TempMemWriter4 cleanup took : "
                                + (NTPDate.currentTimeMillis() - lStart) + " ms for " + lNumberOfValuesToRemove
                                + " values in " + hmData.size() + " series");
                    }

                    if (dataCount > 0) {
                        lMinTime = getHead().time;
                    } else {
                        lMinTime = NTPDate.currentTimeMillis();
                    }
                }
            }
        }
    }

    /**
     * In order to know where from to start deleting we need the pointers to the
     * list heads.
     */
    private final TreeSet<ExtendedResultPointer> llStartPointers = new TreeSet<ExtendedResultPointer>();

    /**
     * Remove the oldest <i>iNumberOfValuesToRemove</i> values from the buffer.
     * @param numberOfValuesToRemove 
     * @return true if yes
     */
    final boolean removeHead(final int numberOfValuesToRemove) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " TempMemWriter4 entering removeHead [ " + numberOfValuesToRemove
                    + " ] ... llStartPointers.size() == " + llStartPointers.size() + " dataCount == " + dataCount);
        }

        int iNumberOfValuesToRemove = numberOfValuesToRemove;

        // find out the lowest two series
        synchronized (sync) {
            for (; iNumberOfValuesToRemove > 0; iNumberOfValuesToRemove--) {
                try {
                    ExtendedResultPointer ep = llStartPointers.first();
                    if (ep != null) {
                        llStartPointers.remove(ep);
                        String key = IDGenerator.generateKey(ep.er, 0);
                        Set<ExtendedResultPointer> ts = hmData.get(key);
                        if (ts != null) {
                            ts.remove(ep);
                        } else {
                            logger.log(Level.WARNING, " TreeSet null for key " + key);
                        }
                        dataCount--;
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exc", t);
                }
            }
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " TempMemWriter4 exiting removeHead [ " + iNumberOfValuesToRemove
                    + " ] ... llStartPointers.size() == " + llStartPointers.size() + " dataCount == " + dataCount);
        }
        return iNumberOfValuesToRemove == 0;
    }

    /**
     * Get the start of the list. We don't need sorting because this function is
     * called after <code>removeHead</code> which does the sorting for us.
     * @return the head
     */
    final ExtendedResult getHead() {

        synchronized (sync) {
            try {
                if (llStartPointers.size() > 0) {
                    return (llStartPointers.first()).er;
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Got exc", t);
                return null;
            }

        }
        return null;
    }

    /**
     * The DataSplitter is used to filter the values from the buffer and
     * retrieve only the data matching a given <code>monPredicate</code>
     */
    private final class MemDataSplitter extends DataSplitter {

        public MemDataSplitter() {
            // empty
        }

        @Override
        public Vector<TimestampedResult> getAndFilter(monPredicate pred) {
            final Vector<TimestampedResult> v;

            synchronized (sync) {
                v = Cache.getObjectsFromHash(hmData, pred, hmData, true);
            }

            return v;
        }
    }

    /**
     * The data splitter with all the values currently in memory
     * 
     * @return data splitter
     */
    public final DataSplitter getDataSplitter() {
        lServedRequests++;
        return new MemDataSplitter();
    }

    @Override
    public ArrayList<Object> getLatestValues() {
        synchronized (sync) {
            final ArrayList<Object> al = new ArrayList<Object>(hmData.size());

            final Iterator<Set<ExtendedResultPointer>> it = hmData.values().iterator();

            TreeSet<ExtendedResultPointer> ts;

            while (it.hasNext()) {
                ts = (TreeSet<ExtendedResultPointer>) it.next();

                if (ts.size() > 0) {
                    al.add(ts.last().er);
                }
            }

            return al;
        }
    }

}
