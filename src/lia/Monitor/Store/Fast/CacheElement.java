package lia.Monitor.Store.Fast;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.ExtendedResult;
import lia.Monitor.monitor.Result;
import lia.util.ntp.NTPDate;

/**
 * @author costing
 * @since forever
 */
public final class CacheElement implements Serializable {
    /**
     * This way we won't see any more errors in the logs :) 
     */
    private static final long serialVersionUID = -4347639138018062968L;

    private static final Logger logger = Logger.getLogger(CacheElement.class.getName());

    /**
     * Average value
     */
    double dValue;

    /**
     * Previous value
     */
    double dPrevValue;

    /**
     * Min value on the current interval
     */
    double dMin;

    /**
     * Max value on the current interval
     */
    double dMax;

    /**
     * Timestamp of the last received value
     */
    long lLastUpdate;

    /**
     * Timestamp of the last write to the database
     */
    long lLastWritten;

    /**
     * One of the Result objects, kept for ID only
     */
    final Result r;

    /**
     * Parameter index from the Result above
     */
    final int iParam;

    /**
     * Compacting interval
     */
    long lInterval;

    /**
     * Flag to signal if we have some unwritten data
     */
    boolean bSomeData;

    /**
     * Flag to signal if the value was the only one received in this interval
     */
    boolean bWasFirst;

    /**
     * The object that does the actual write
     */
    transient WriterInterface writer;

    /**
     * Accept values that are at most this long in the future (in milliseconds). It is set by the configuration option:
     * <code>
     * lia.Monitor.Store.ALLOW_FUTURE=(interval, in seconds, defaults to 10 minutes)
     * </code>
     * You can set it to 0 to skip checking.
     */
    private static long ALLOW_FUTURE = 600000;

    /**
     * When the configuration file is touched, reload parameters
     */
    static final void reloadConfig() {
        ALLOW_FUTURE = AppConfig.getl("lia.Monitor.Store.ALLOW_FUTURE", 600) * 1000;
    }

    static {
        reloadConfig();

        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                reloadConfig();
            }

        });
    }

    /**
     * The only constructor
     * 
     * @param _lInterval compact interval
     * @param result template
     * @param _iParam parameter index in the Result above
     * @param lTime current timestamp
     * @param bWrite whether or not to write this first value
     * @param w actual writer
     */
    public CacheElement(final long _lInterval, final Result result, final int _iParam, final long lTime,
            final boolean bWrite, final WriterInterface w) {
        r = result;
        iParam = _iParam;
        lInterval = _lInterval;

        lLastWritten = lTime - lInterval;
        lLastUpdate = lTime - lInterval;
        dValue = r.param[iParam];

        bSomeData = false;

        writer = w;

        update(result, bWrite);
    }

    /**
     * Used after a de-serialization to set the writer to a real one
     * 
     * @param w
     */
    public synchronized void setWriter(final WriterInterface w) {
        writer = w;
    }

    /**
     * Add a new value
     * 
     * @param _r
     * @param bWrite
     * @return true if the value was taken into account, false if it was ignored
     */
    public boolean update(final Result _r, final boolean bWrite) {
        if (_r instanceof ExtendedResult) {
            final ExtendedResult er = (ExtendedResult) _r;

            if (er.param == null) {
                return false;
            }

            if (er.param.length == 1) {
                return update(er.param[0], er.min, er.max, er.time, bWrite);
            }

            if ((iParam < er.param_name.length) && er.param_name[iParam].equals(r.param_name[iParam])) {
                return update(er.param[iParam], er.min, er.max, er.time, bWrite);
            }

            for (int i = 0; i < er.param_name.length; i++) {
                if ((i != iParam) && er.param_name[i].equals(r.param_name[iParam])) {
                    return update(er.param[i], er.min, er.max, _r.time, bWrite);
                }
            }

            return false;
        }

        if ((iParam < _r.param_name.length) && _r.param_name[iParam].equals(r.param_name[iParam])) {
            return update(_r.param[iParam], _r.param[iParam], _r.param[iParam], _r.time, bWrite);
        }

        for (int i = 0; i < _r.param_name.length; i++) {
            if ((i != iParam) && _r.param_name[i].equals(r.param_name[iParam])) {
                return update(_r.param[i], _r.param[i], _r.param[i], _r.time, bWrite);
            }
        }

        return false;
    }

    /**
     * Write the last unwritten value to the database.
     * 
     * @return true if there was something left to write and the write was a success
     */
    public synchronized boolean flush() {
        if (bSomeData) {
            bSomeData = false;

            if (!bWasFirst) {
                lLastWritten += lInterval;
                lLastUpdate = lLastWritten;

                return writer.insert(lLastWritten - (lInterval / 2), r, iParam, dValue, dMin, dMax);
            }
        }

        return false;
    }

    /**
     * Check whether or not the unwritten value should be flushed
     * 
     * @param lUpdateTime
     * @return true if the value was flushed, false if not
     */
    public synchronized boolean checkFlush(final long lUpdateTime) {
        if (bSomeData && ((lUpdateTime - lLastWritten) > (2 * lInterval))) {
            return flush();
        }

        return false;
    }

    /**
     * Add a new value
     * 
     * @param dNewValue the new average value
     * @param dNewMin the new min value
     * @param dNewMax the new max value
     * @param lUpdateTime timestamp of this value
     * @param bWrite true = write, false = do not write this value (was previously read from the db)
     * @return true if the value was taken into account, false if it was ignored
     */
    public synchronized boolean update(final double dNewValue, final double dNewMin, final double dNewMax,
            final long lUpdateTime, final boolean bWrite) {
        if (lUpdateTime <= lLastUpdate) {
            //debug("Returning false because update time < last update: "+lUpdateTime+", "+lLastUpdate);
            return false;
        }

        if (lLastUpdate < lLastWritten) {
            //debug("marking some data = false because last update < last written : "+lLastUpdate+", "+lLastWritten);
            bSomeData = false;
        }

        if ((ALLOW_FUTURE > 0) && (lUpdateTime > (NTPDate.currentTimeMillis() + ALLOW_FUTURE))) { // allow 1 minute positive data offset
            logger.log(
                    Level.FINE,
                    "update: return FALSE because lUpdateTime > now()" + r + "\n" + "lUpdateTime = " + lUpdateTime
                            + " : " + (new java.util.Date(lUpdateTime)) + "\n" + "now         = "
                            + NTPDate.currentTimeMillis() + " : " + (new NTPDate()));
            //debug("Returning false because update time is in the future: "+lUpdateTime);
            return false;
        }

        checkFlush(lUpdateTime);

        if (!bSomeData) {
            dMin = dNewMin;
            dMax = dNewMax;
            dPrevValue = dNewValue;

            bWasFirst = bWrite;

            if ((lUpdateTime - lLastWritten) >= lInterval) {
                while ((lUpdateTime - lLastWritten) >= lInterval) {
                    lLastWritten += lInterval;
                }

                if (bWrite) {
                    //debug("direct write because some data = false and write = true: "+lLastWritten);
                    writer.insert(lLastWritten - (lInterval / 2), r, iParam, dNewValue, dNewMin, dNewMax);
                }
            }

            lLastUpdate = lUpdateTime;
            dValue = dNewValue;

            bSomeData = true;

            return true;
        }

        bWasFirst = false;

        if ((lUpdateTime - lLastWritten) >= lInterval) { // we should write a new value to the database
            final double dIntersect = dPrevValue
                    + (((dNewValue - dPrevValue) * ((lLastWritten + lInterval) - lLastUpdate)) / (lUpdateTime - lLastUpdate));

            final double avg = ((dValue * (lLastUpdate - lLastWritten)) + (((dPrevValue + dIntersect) / 2) * ((lLastWritten + lInterval) - lLastUpdate)))
                    / lInterval;

            dMin = Math.min(dMin, dIntersect);
            dMax = Math.max(dMax, dIntersect);

            lLastWritten += lInterval;

            //debug("writing normal averaged data : lLastWritten = "+lLastWritten);
            writer.insert(lLastWritten - (lInterval / 2), r, iParam, avg, dMin, dMax);

            dValue = (dIntersect + dNewValue) / 2;
            dPrevValue = dNewValue;
            dMin = Math.min(dIntersect, dNewMin);
            dMax = Math.max(dIntersect, dNewMax);
            lLastUpdate = lUpdateTime;
        } else {
            //debug("just averaging the data");

            double avg;

            if (lUpdateTime > lLastWritten) {
                avg = ((dValue * (lLastUpdate - lLastWritten)) + (((dNewValue + dPrevValue) / 2) * (lUpdateTime - lLastUpdate)))
                        / (lUpdateTime - lLastWritten);
            } else {
                avg = (dValue + dNewValue) / 2;
            }

            dValue = avg;
            dPrevValue = dNewValue;

            dMax = Math.max(dMax, dNewMax);
            dMin = Math.min(dMin, dNewMin);

            lLastUpdate = lUpdateTime;
        }

        return true;
    }

}
