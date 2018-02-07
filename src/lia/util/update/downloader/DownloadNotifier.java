/*
 * Created on Sep 25, 2010
 */
package lia.util.update.downloader;

import java.util.concurrent.TimeUnit;

/**
 * 
 * @author ramiro
 */
public interface DownloadNotifier {
    public void notifyDownloadEvent(DownloadEvent event);
    public long getProgressNotifierDelay(TimeUnit unit);
}
