package lia.util;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AccountingResult;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.ExtResult;
import lia.Monitor.monitor.Gresult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;

/**
 * @author costing
 */
public final class StringFactory {

    private static final Logger logger = Logger.getLogger(StringFactory.class.getName());

    /**
     * be cool; do not try to "cache" anything
     */
    private static boolean DO_NOT_CACHE;

    /**
     * The weak reference mapping of Strings, if we don't use the String.internal() mechanism
     */
    private static Map<String, WeakReference<String>> hmStrings = null;

    /**
     * How many hits were to the cache
     */
    private static final transient AtomicLong lCacheHit = new AtomicLong(0);

    /**
     * How many misses (inserts)
     */
    private static final transient AtomicLong lCacheMiss = new AtomicLong(0);

    /**
     * How many were ignored (too big, null etc)
     */
    private static final transient AtomicLong lCacheIgnore = new AtomicLong(0);

    /**
     * Access to the shared objects is synchronized through this lock
     */
    private static final ReadWriteLock rwl = new ReentrantReadWriteLock();

    private static final Lock readLock = rwl.readLock();

    private static final Lock writeLock = rwl.writeLock();

    static {
        reloadConf();

        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                reloadConf();
            }
        });
    }

    private StringFactory() {
        // disabled default constructor
    }

    /**
     * @param vb
     * @return the String value
     */
    public static String get(final byte[] vb) {
        if (vb == null) {
            return null;
        }

        return get(new String(vb));
    }

    /**
     * @param vc
     * @return the String value
     */
    public static String get(final char[] vc) {
        if (vc == null) {
            return null;
        }

        return get(new String(vc));
    }

    /**
     * @return the current number of entries in the cache
     */
    public static int getCacheSize() {
        try {
            readLock.lock();
            return (hmStrings == null) ? -1 : hmStrings.size();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * @param s
     * @return the String value
     */
    public static String get(final String s) {
        if (s == null) {
            return null;
        }

        String sRet;

        try {
            readLock.lock();
            if (DO_NOT_CACHE) {
                lCacheIgnore.incrementAndGet();
                return s;
            }
            final WeakReference<String> t = hmStrings.get(s);
            if ((t != null) && ((sRet = t.get()) != null)) {
                lCacheHit.incrementAndGet();
                return sRet;
            }
        } finally {
            readLock.unlock();
        }

        try {
            writeLock.lock();
            //try again some other thread might have added in between
            final WeakReference<String> t = hmStrings.get(s);

            if ((t == null) || ((sRet = t.get()) == null)) {
                hmStrings.put(s, new WeakReference<String>(s));
                lCacheMiss.incrementAndGet();
                return s;
            }

            lCacheHit.incrementAndGet();
        } finally {
            writeLock.unlock();
        }

        return sRet;
    }

    /**
     * @return hit ratio
     */
    public static double getHitRatio() {

        try {
            readLock.lock();
            final double d = getAccessCount();

            if (d >= 1) {
                return (lCacheHit.get() * 100d) / d;
            }

        } finally {
            readLock.unlock();
        }

        return 0;
    }

    /**
     * @return ignored ratio
     */
    public static double getIgnoreRatio() {
        try {
            readLock.lock();
            final double d = getAccessCount();

            if (d >= 1) {
                return (lCacheIgnore.get() * 100d) / d;
            }
        } finally {
            readLock.unlock();
        }
        return 0;
    }

    /**
     * @return number of calls
     */
    public static double getAccessCount() {
        try {
            readLock.lock();
            return lCacheHit.get() + lCacheMiss.get() + lCacheIgnore.get();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * reset the counters
     */
    public static void resetHitCounters() {
        try {
            writeLock.lock();
            lCacheHit.set(0);
            lCacheMiss.set(0);
            lCacheIgnore.set(0);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * @param r
     */
    public static void convert(final Result r) {
        r.FarmName = get(r.FarmName);
        r.ClusterName = get(r.ClusterName);
        r.NodeName = get(r.NodeName);
        r.Module = get(r.Module);

        if (r.param_name != null) {
            for (int i = r.param_name.length - 1; i >= 0; i--) {
                if (r.param_name[i] != null) {
                    r.param_name[i] = get(r.param_name[i]);
                }
            }
        }
    }

    /**
     * @param r
     */
    public static final void convert(final eResult r) {
        r.FarmName = get(r.FarmName);
        r.ClusterName = get(r.ClusterName);
        r.NodeName = get(r.NodeName);
        r.Module = get(r.Module);

        if (r.param_name != null) {
            for (int i = r.param_name.length - 1; i >= 0; i--) {
                if (r.param_name[i] != null) {
                    r.param_name[i] = get(r.param_name[i]);
                }

                if ((r.param[i] != null) && (r.param[i] instanceof String)) {
                    r.param[i] = get((String) r.param[i]);
                }
            }
        }
    }

    /**
     * @param r
     */
    public static final void convert(final ExtResult r) {
        r.FarmName = get(r.FarmName);
        r.ClusterName = get(r.ClusterName);
        r.NodeName = get(r.NodeName);
        r.Module = get(r.Module);

        if (r.param_name != null) {
            for (int i = r.param_name.length - 1; i >= 0; i--) {
                if (r.param_name[i] != null) {
                    r.param_name[i] = get(r.param_name[i]);
                }
            }
        }

        if ((r.extra != null) && (r.extra instanceof String)) {
            r.extra = get((String) r.extra);
        }
    }

    /**
     * @param r
     */
    public static final void convert(final Gresult r) {
        r.FarmName = get(r.FarmName);
        r.ClusterName = get(r.ClusterName);
        r.Module = get(r.Module);
    }

    /**
     * @param r
     */
    public static final void convert(final AccountingResult r) {
        r.sGroup = get(r.sGroup);
        r.sUser = get(r.sUser);
        r.sJobID = get(r.sJobID);

        if (r.vsParams != null) {
            for (int i = 0; i < r.vsParams.size(); i++) {
                r.vsParams.set(i, get(r.vsParams.get(i)));
            }
        }
    }

    /**
     * @param o
     */
    public static final void convert(final Object o) {
        if (o == null) {
            return;
        }

        if (o instanceof Result) {
            convert((Result) o);
        } else if (o instanceof eResult) {
            convert((eResult) o);
        } else if (o instanceof ExtResult) {
            convert((ExtResult) o);
        } else if (o instanceof Gresult) {
            convert((Gresult) o);
        } else if (o instanceof AccountingResult) {
            convert((AccountingResult) o);
        } else if (o instanceof Collection<?>) {
            final Iterator<?> it = ((Collection<?>) o).iterator();

            while (it.hasNext()) {
                convert(it.next());
            }
        }
    }

    /**
     * 
     */
    static final void reloadConf() {
        try {
            writeLock.lock();

            DO_NOT_CACHE = AppConfig.getb("lia.util.StringFactory.do_not_cache", false);

            if (DO_NOT_CACHE) {
                hmStrings = null;
            } else {
                if (hmStrings == null) {
                    hmStrings = new WeakHashMap<String, WeakReference<String>>(1024);
                }
            }

            logger.log(Level.INFO, " [ StringFactory ] (re)loaded config. will cache: " + !DO_NOT_CACHE);
        } finally {
            writeLock.unlock();
        }
    }

}
