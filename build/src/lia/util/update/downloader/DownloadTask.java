/*
 * Created on Sep 18, 2010
 */
package lia.util.update.downloader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.util.update.UpdaterUtils;
import lia.util.update.digest.DigestManager;

/**
 * @author ramiro
 */
class DownloadTask implements Callable<DownloadResult> {

    private static final Logger logger = Logger.getLogger(DownloadTask.class.getName());

    private static final int BUFFER_SIZE = AppConfig.geti("lia.util.update.BUFFER_SIZE", 8 * 8192);

    private final RemoteResource resource;

    private final int connectTimeout;

    private final int readTimeout;

    private final String digestAlgo;

    private final DownloadNotifier notifier;

    private final ScheduledExecutorService notifierExecutor;
    private final AtomicLong downloadedSize = new AtomicLong(0);

    DownloadTask(DownloadNotifier notifier, ScheduledExecutorService notifierExecutor, RemoteResource resource,
            int connectTimeout, int readTimeout) {
        this.notifier = notifier;
        this.notifierExecutor = notifierExecutor;
        this.resource = resource;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        final String dAlgo = resource.digestAlgorithm;
        this.digestAlgo = dAlgo;
    }

    /**
     * @return
     * @throws IOException
     * @throws DownloadException
     * @throws ExecutionException 
     * @throws InterruptedException 
     * @throws NoSuchAlgorithmException 
     */
    private final DownloadResult downloadResource() throws IOException, DownloadException, NoSuchAlgorithmException,
            InterruptedException, ExecutionException {

        InputStream is = null;
        BufferedInputStream bis = null;
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;

        final byte buffer[] = new byte[BUFFER_SIZE];
        ScheduledFuture<?> sf = null;

        final AtomicBoolean isDownloaded = new AtomicBoolean(false);
        final File file = resource.destinationFile;
        try {

            final URL url = resource.url;
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ DownloadTask ] [ downloadResource ] start processing URL: " + url
                        + " destination file: " + file);
            }
            final File parentDir = resource.destinationFile.getParentFile();
            if (parentDir.exists()) {
                if (!parentDir.isDirectory()) {
                    throw new IOException(" [ DownloadTask ] The destination cache: " + parentDir
                            + " is not a directory");
                }
            } else {
                if (!parentDir.mkdirs()) {
                    //the tasks may see different things if executed in parallel
                    if (!parentDir.exists()) {
                        throw new IOException(" [ DownloadTask ] Unable to create directory: " + parentDir);
                    }
                }
            }

            if ((notifier != null) && (notifierExecutor != null)) {
                notifier.notifyDownloadEvent(new DownloadEvent(DownloadEvent.Type.DOWNLOAD_STARTED, resource,
                        downloadedSize.get()));
                sf = notifierExecutor.scheduleWithFixedDelay(new Runnable() {

                    @Override
                    public void run() {
                        if (!isDownloaded.get()) {
                            notifier.notifyDownloadEvent(new DownloadEvent(DownloadEvent.Type.DOWNLOAD_IN_PROGRESS,
                                    resource, downloadedSize.get()));
                        }
                    }
                }, notifier.getProgressNotifierDelay(TimeUnit.NANOSECONDS), notifier
                        .getProgressNotifierDelay(TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS);
            }

            final URLConnection connection = url.openConnection();

            fos = new FileOutputStream(file);
            bos = new BufferedOutputStream(fos);

            connection.setUseCaches(false);
            connection.setDefaultUseCaches(false);

            if (connectTimeout >= 0) {
                connection.setConnectTimeout(connectTimeout);
            }
            if (readTimeout >= 0) {
                connection.setReadTimeout(readTimeout);
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ DownloadTask ] [ downloadResource ] connecting to: " + url);
            }
            connection.connect();

            is = connection.getInputStream();
            bis = new BufferedInputStream(is, BUFFER_SIZE);

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ DownloadTask ] [ downloadResource ] start downloading: " + url);
            }

            for (;;) {
                final int len = bis.read(buffer);
                if (len < 0) {
                    isDownloaded.set(true);
                    break;
                }

                bos.write(buffer, 0, len);
                // increment the conter
                downloadedSize.addAndGet(len);

                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINER, " [ DownloadTask ] [ downloadResource ] downloaded " + downloadedSize.get()
                            + " / for URL: " + url);
                }
            }
            bos.flush();

        } finally {
            if (sf != null) {
                sf.cancel(false);
            }
            UpdaterUtils.closeIgnoringException(is);
            UpdaterUtils.closeIgnoringException(bis);
            UpdaterUtils.closeIgnoringException(fos);
            UpdaterUtils.closeIgnoringException(bos);
        }

        isDownloaded.set(true);
        if ((notifier != null) && (notifierExecutor != null)) {
            notifier.notifyDownloadEvent(new DownloadEvent(DownloadEvent.Type.DOWNLOAD_FINISHED, resource,
                    downloadedSize.get()));
        }

        final byte[] digest = (digestAlgo != null) ? DigestManager.getInstance().asyncDigest(file, digestAlgo).get().digest
                : null;
        return new DownloadResult(resource, downloadedSize.get(), digest);
    }

    @Override
    public DownloadResult call() throws Exception {
        return downloadResource();
    }

}
