/*
 * $Id: TempMemWriter2.java 7533 2014-09-09 14:29:58Z costing $
 */
package lia.Monitor.Store.Fast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
public final class TempMemWriter2 extends Writer implements TempMemWriterInterface {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(TempMemWriter2.class.getName());

    /**
     *        "Farm/Cluster/Node/Parameter" -> list of time-ordered values mapping
     */
    HashMap<String, List<ExtendedResult>> hmData;

    /** the lowest data time in buffer */
    public long lMinTime;

    /** the highest data time in buffer */
    public long lMaxTime;

    /**
     * max number of values to keep
     */
    int iLimit;

    /**  the upper size limit of the buffer, depending on the jvm memory settings */
    int iHardLimit;

    /** statistics */
    public long lServedRequests = 0;

    /** how many values are currently stored in the buffer */
    int dataCount = 0;

    /**
     * Set a new upper limit for the data count.
     *
     * @param i the new maximum number of values from the memory buffer, in <i>K</i>
     */
    public void setHardLimit(int i) {
        if ((i < 64) || (i > 10240)) {
            return;
        }

        iHardLimit = i * 1024;
    }

    /**
     * Initialize the data structures and determine the upper limit of the buffer.
     */
    public TempMemWriter2() {
        hmData = new HashMap<String, List<ExtendedResult>>();

        lMinTime = lMaxTime = NTPDate.currentTimeMillis();

        MonALISAExecutors.getMLStoreExecutor().scheduleWithFixedDelay(new TempMemWriterTask(), 500, 500,
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
     * Add some data to the memory buffer. This function only finds out the data type and acts accordingly.
     *
     * @param o the data to be added, can be Result, ExtendedResult or a Collection
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
     * A Result is converted into one or more ExtendedResult objects by adding min=max=value for each parameter.
     *
     * @param r the object to be stored in memory
     */
    private void addSample(final Result r) {
        if ((r == null) || (r.param_name == null) || (r.param_name.length <= 0)) {
            return;
        }

        for (int i = 0; i < r.param_name.length; i++) {
            addData(r.time, r, r.param_name[i], r.param[i], r.param[i], r.param[i]);
        }
    }

    /**
     * An ExtendedResult is split into one or more ExtendedResult objects, one object for each parameter.
     *
     * @param r the ExtendedResult object.
     */
    private void addSample(final ExtendedResult r) {
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
        return "TempMemWriter2";
    }

    /**
     * What is the actual time interval that is kept in memory.
     * 
     * @return the interval length, in millis, relative to the absolute current time;
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
    private final void addData(final long rectime, final Result r, final String sParamName, final double mval,
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
     * Keep a single series time-ordered.
     */
    private static final class SortedList extends LinkedList<ExtendedResult> {

        /**
         * Please Eclipse, don't complain anymore!
         */
        private static final long serialVersionUID = -1154788868255441175L;

        public SortedList(ExtendedResult er) {
            super();
            super.add(er);
        }

        /**
         * Override the original <code>add</code> function to keep the data sorted.
         *
         * @param er the object to be added, an ExtendedResult instance
         */
        @Override
        public boolean add(final ExtendedResult er) {
            ExtendedResult erTemp = getLast();

            if (er.time >= erTemp.time) {
                //for normal data production this will be true
                addLast(er);
            } else {
                //if the latest value was in fact older the some previously received value
                Iterator<ExtendedResult> it = iterator();

                int index = 0;

                while (it.hasNext()) {
                    erTemp = it.next();

                    if (erTemp.time > er.time) {
                        // here is where i have to insert it
                        add(index, er);
                        break;
                    }

                    index++;
                }
            }

            return true;
        }

    }

    /**
     * A new sample was received.
     * 
     * First the (Farm/Cluster/Node/Parameter) pair is looked up in the map:
     *  - if there is an entry for it then add the new value to the existing list
     *  - otherwise create an entry in the hash and add one Pointer to the llStartPointers list
     */
    private final void addDataToList(ExtendedResult er) {
        synchronized (hmData) {
            String sKey = IDGenerator.generateKey(er, 0);

            List<ExtendedResult> ll = hmData.get(sKey);

            if (ll == null) {
                ll = new SortedList(er);
                hmData.put(sKey, ll);

                synchronized (llStartPointers) {
                    llStartPointers.add(new Pointer(ll));
                }
            } else {
                ll.add(er);
            }

            dataCount++;

            if (er.time > lMaxTime) {
                lMaxTime = er.time;
            }
        }
    }

    /**
     * Periodic task to determine the data count limit and, if necessary, to clean the values that exceed the limit
     */
    final class TempMemWriterTask implements Runnable {
        @Override
        public void run() {
            synchronized (hmData) {
                final int iOldLimit = iLimit;

                // adjust the limit size
                final long total = Runtime.getRuntime().maxMemory(); // instead of totalMemory()
                final long free = Runtime.getRuntime().freeMemory() + (total - Runtime.getRuntime().totalMemory());

                long t = total / 10;
                if (t > (20 * 1024 * 1024)) {
                    t = 20 * 1024 * 1024;
                }

                if (((free < t) && (iLimit > 16)) || (iLimit > iHardLimit)) {
                    iLimit = (iLimit / 3) * 2;
                }

                while ((free > (t * 2)) && ((dataCount > ((2 * iLimit) / 3)) || ((iLimit - dataCount) < 1000))
                        && (iLimit < iHardLimit)) {
                    iLimit = (iLimit * 3) / 2;
                }

                if (iLimit != iOldLimit) {
                    logger.log(Level.INFO, "Readjusted limit to " + iLimit + " (" + iOldLimit + ")/" + iHardLimit
                            + " because : " + free + "/" + total + " : " + dataCount);
                }

                // try to delete many values at the same time
                if (dataCount > ((11 * iLimit) / 10)) {
                    int iDownLimit = (9 * iLimit) / 10;

                    long lStart = NTPDate.currentTimeMillis();

                    int lNumberOfValuesToRemove = dataCount - iDownLimit;

                    if (removeHead(lNumberOfValuesToRemove)) {
                        dataCount = iDownLimit;
                    } else {
                        logger.log(Level.INFO, "dataCount consistency check forced");

                        dataCount = 0;

                        Iterator<List<ExtendedResult>> it = hmData.values().iterator();

                        while (it.hasNext()) {
                            dataCount += it.next().size();
                        }
                    }

                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "TempMemWriter2 cleanup took : "
                                + (NTPDate.currentTimeMillis() - lStart) + " ms for " + lNumberOfValuesToRemove
                                + " values in " + hmData.size() + " series");
                    }

                    if (dataCount > 0) {
                        lMinTime = getHead().time;
                    } else {
                        lMinTime = NTPDate.currentTimeMillis();
                    }

                    //System.err.println("TMW2: System.gc() : "+lNumberOfValuesToRemove+", "+dataCount+", "+iLimit+", "+iOldLimit);
                    //System.gc();
                }
            }
        }
    }

    /**
     * In order to know where from to start deleting we need the pointers to the list heads.
     */
    private final LinkedList<Pointer> llStartPointers = new LinkedList<Pointer>();

    /** 
     * Keep a pointer to each list to easily remove the oldest values.
     */
    private static final class Pointer implements Comparable<Pointer> {
        final List<ExtendedResult> llData;

        public Pointer(List<ExtendedResult> ll) {
            llData = ll;
        }

        public final long getLowestTime() {
            if (llData.size() > 0) {
                return llData.get(0).time;
            }

            return NTPDate.currentTimeMillis(); // an empty series should not affect the alg
        }

        @Override
        public final int compareTo(final Pointer o) {
            long t1 = getLowestTime();
            long t2 = o.getLowestTime();

            return (int) (t1 - t2);
        }
    }

    /**
     * Remove the oldest <i>iNumberOfValuesToRemove</i> values from the buffer.
     * @param numberOfValuesToRemove 
     * @return true if the had is to be removed
     */
    final boolean removeHead(final int numberOfValuesToRemove) {
        Pointer p = null; // the oldest data series from the buffer
        Pointer p2 = null; // the second oldest data series

        int iNumberOfValuesToRemove = numberOfValuesToRemove;

        long minTime = 0;
        long minTime2 = 0;

        // find out the lowest two series
        synchronized (llStartPointers) {
            Iterator<Pointer> it = llStartPointers.iterator();

            while (it.hasNext()) {
                Pointer pt = it.next();

                if (p == null) {
                    p = pt;
                    minTime = p.getLowestTime();
                } else if (p2 == null) {
                    p2 = pt;
                    minTime2 = p2.getLowestTime();
                } else if (pt.getLowestTime() < minTime) {
                    p2 = p;
                    minTime2 = minTime;

                    p = pt;
                    minTime = p.getLowestTime();
                } else if (pt.getLowestTime() < minTime2) {
                    p2 = pt;
                    minTime2 = p2.getLowestTime();
                }
            }
        }

        boolean bShouldCleanHash = false;

        while (iNumberOfValuesToRemove > 0) {
            if (p == null) { // we shouldn't have got to this point and not to have something to extract ...
                bShouldCleanHash = true;
                break;
            }

            if (p2 == null) { // improbable case with only one big series
                synchronized (hmData) {
                    Iterator<ExtendedResult> it = p.llData.iterator();

                    while (it.hasNext() && (iNumberOfValuesToRemove > 0)) {
                        it.next();
                        it.remove();
                        iNumberOfValuesToRemove--;
                    }

                    if (p.llData.size() == 0) {
                        bShouldCleanHash = true;
                        llStartPointers.remove(p);
                    }
                }

                break;
            }

            synchronized (hmData) {
                Iterator<ExtendedResult> it = p.llData.iterator();

                while (it.hasNext() && (iNumberOfValuesToRemove > 0)) {
                    ExtendedResult er = it.next();

                    if (er.time <= minTime2) {
                        it.remove();
                        iNumberOfValuesToRemove--;
                    } else {
                        break;
                    }
                }

                if (p.llData.size() == 0) {
                    bShouldCleanHash = true;
                    llStartPointers.remove(p);
                }
            }

            // the second lowest series is now the lowest series of all
            p = p2;
            minTime = minTime2;

            p2 = null;
            minTime2 = 0;
            // find out the next lowest series
            synchronized (llStartPointers) {
                Iterator<Pointer> it = llStartPointers.iterator();

                while (it.hasNext()) {
                    Pointer pt = it.next();

                    if ((p2 == null) || (pt.getLowestTime() < minTime2)) {
                        p2 = pt;
                        minTime2 = p2.getLowestTime();
                    }
                }
            }
        }

        if (bShouldCleanHash) {
            synchronized (hmData) {
                Iterator<Map.Entry<String, List<ExtendedResult>>> it = hmData.entrySet().iterator();

                while (it.hasNext()) {
                    Map.Entry<String, List<ExtendedResult>> me = it.next();

                    if (me.getValue().size() == 0) {
                        it.remove();
                    }
                }
            }
        }

        return iNumberOfValuesToRemove == 0;
    }

    /**
     * Get the start of the list. We don't need sorting because this function is called after <code>removeHead</code>
     * which does the sorting for us.
     * @return the head
     */
    final ExtendedResult getHead() {
        Pointer p = null;

        synchronized (llStartPointers) {
            if (llStartPointers.size() > 0) {
                p = llStartPointers.getFirst();
            }
        }

        if (p != null) {
            synchronized (hmData) {
                List<ExtendedResult> ll = p.llData;

                if (ll.size() > 0) {
                    return ll.get(0);
                }
            }
        }

        return null;
    }

    /**
     * The DataSplitter is used to filter the values from the buffer and retrieve only the data
     * matching a given <code>monPredicate</code>
     */
    private final class MemDataSplitter extends DataSplitter {
        public MemDataSplitter() {
            // ignore
        }

        @Override
        public Vector<TimestampedResult> getAndFilter(monPredicate pred) {
            final Vector<TimestampedResult> v;

            synchronized (hmData) {
                v = Cache.getObjectsFromHash(hmData, pred, hmData, true);
            }

            return v;
        }
    }

    /**
     * @return the data splitter for all the values in memory
     */
    public final DataSplitter getDataSplitter() {
        lServedRequests++;
        return new MemDataSplitter();
    }

    @Override
    public final ArrayList<Object> getLatestValues() {
        synchronized (hmData) {
            final ArrayList<Object> al = new ArrayList<Object>(hmData.size());

            final Iterator<List<ExtendedResult>> it = hmData.values().iterator();

            while (it.hasNext()) {
                List<ExtendedResult> ll = it.next();

                if (ll.size() > 0) {
                    al.add(((LinkedList<ExtendedResult>) ll).getLast());
                }
            }

            return al;
        }
    }

}
