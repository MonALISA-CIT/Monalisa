/*
 * Created on Sep 18, 2010
 */
package lia.util.update.downloader;

import java.util.Arrays;

import lia.util.update.UpdaterUtils;

/**
 * @author ramiro
 */
public class DownloadResult {

    private final RemoteResource resource;
    private final long size;
    private final byte[] checkSum;
    
    DownloadResult(RemoteResource resource, final long size, final byte[] checkSum) {
        this.resource = resource;
        this.checkSum = checkSum;
        this.size = size;
    }

    
    /**
     * @return the checkSum
     */
    public byte[] getCheckSum() {
        return Arrays.copyOf(checkSum, checkSum.length);
    }

    
    /**
     * @return the size
     */
    public long getSize() {
        return size;
    }


    public RemoteResource getRemoteResource() {
        return resource;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "DownloadResult [url=" + resource.url + ", file=" + resource.destinationFile + ", size=" + size + ", checkSum=" + UpdaterUtils.toHexString(checkSum) + "]";
    }
    
    
}
