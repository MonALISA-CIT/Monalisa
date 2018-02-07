/*
 * Created on Sep 25, 2010
 */
package lia.util.update.downloader;

/**
 * 
 * @author ramiro
 */
public class DownloadEvent {

    public enum Type {
        DOWNLOAD_STARTED, DOWNLOAD_FINISHED, DOWNLOAD_IN_PROGRESS
    }
    
    public final Type eventType;
    public final RemoteResource resource;
    public final long downloadedSize;
    
    public DownloadEvent(Type eventType, RemoteResource resource, long downloadedSize) {
        this.eventType = eventType;
        this.resource = resource;
        this.downloadedSize = downloadedSize;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("DownloadEvent [eventType=").append(eventType).append(", resource=").append(resource).append(", downloadedSize=").append(downloadedSize).append("]");
        return builder.toString();
    }
    
}
