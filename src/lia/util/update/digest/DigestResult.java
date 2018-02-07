/*
 * Created on Sep 27, 2010
 */
package lia.util.update.digest;

import java.io.File;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import lia.util.update.UpdaterUtils;

/**
 * 
 *
 * @author ramiro
 */
public class DigestResult {

    public final File file;
    public final byte[] digest;
    public final long size;
    public final long timestamp;
    
    //kept in nanos
    private final long computeTime;
    
    /**
     * @param file
     * @param digest
     * @param size
     * @param timestamp
     * @param computeTime
     */
    DigestResult(File file, byte[] digest, long size, long timestamp, long computeTime) {
        this.file = file;
        this.digest = digest;
        this.size = size;
        this.timestamp = timestamp;
        this.computeTime = computeTime;
    }
    
    public long getComputeTime(TimeUnit unit) {
        return unit.convert(computeTime, TimeUnit.NANOSECONDS);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("DigestResult [file=").append(file).append(", digest=").append(UpdaterUtils.toHexString(digest)).append(", size=").append(size).append(", timestamp=").append(new Date(timestamp)).append(", computeTime=").append(TimeUnit.NANOSECONDS.toMillis(computeTime)).append("ms]");
        return builder.toString();
    }
    
    
}
