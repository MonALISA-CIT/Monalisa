package lia.Monitor.Store.Fast;

import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.TimestampedResult;

/**
 * @author costing
 * @since Jun 13, 2010
 */
final class TempMemWriter3Index implements Comparable<TempMemWriter3Index> {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(TempMemWriter3Index.class.getName());

    private static final AtomicLong SEQ = new AtomicLong(0);

    /**
     * All values
     */
    final PriorityQueue<TimestampedResult> llData;

    /**
     * Timestamp
     */
    final long time;

    /**
     * Sequence
     */
    final long seq;

    /**
     * Hash code
     */
    final int hash;

    /**
     * @param l
     * @param ll
     */
    public TempMemWriter3Index(final long l, final PriorityQueue<TimestampedResult> ll) {
        time = l;
        llData = ll;
        seq = SEQ.getAndIncrement();
        hash = (int) (((time ^ (time >>> 32)) + seq) ^ (seq >>> (32 + 23)));
    }

    @Override
    public final int compareTo(final TempMemWriter3Index other) {
        if (this == other) {
            return 0;
        }

        final long d = time - other.time;
        return (d < 0) ? -1 : (d > 0) ? 1 : (seq < other.seq) ? -1 : 1;
    }

    @Override
    public final boolean equals(final Object o) {
        if (o != null) {
            if (o == this) {
                return true;
            }
            //this should be out ... just testing the robustness ( should be an assert )
            if (seq == ((TempMemWriter3Index) o).seq) {
                // this is totally wrong
                logger.log(Level.SEVERE, " [ TempMemWriter3Index ] Same seq ... different Objects. seq: " + seq
                        + " otherSeq: " + ((TempMemWriter3Index) o).seq);
            }
        }
        return false;
    }

    /**
     * Copied from java.lang.Long
     */
    @Override
    public int hashCode() {
        return hash;
    }
}
