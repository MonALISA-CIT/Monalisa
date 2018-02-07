/*
 * Created on Sep 24, 2010
 */
package lia.util.update.downloader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author ramiro 
 */
public class DownloadManager {

    protected static AtomicLong DMGR_SEQ = new AtomicLong(0L);

    private final Long id;

    protected ExecutorService executor;

    protected ScheduledExecutorService notifierExecutor;

    /**
     * in millis
     */
    private final AtomicInteger connectTimeout = new AtomicInteger(0);

    /**
     * in millis
     */
    private final AtomicInteger readTimeout = new AtomicInteger(0);

    private static final ConcurrentMap<String, DownloadManager> nameDwldMap = new ConcurrentHashMap<String, DownloadManager>();
    
    private final String instanceName;
    
    private DownloadManager(final String instanceName, int maxWorkers, int connectTimeout, int readTimeout) {
        this.id = Long.valueOf(DMGR_SEQ.getAndIncrement());
        this.instanceName = instanceName;
        
        executor = Executors.newFixedThreadPool(maxWorkers, new ThreadFactory() {

            final AtomicInteger SEQ = new AtomicInteger(1);

            public Thread newThread(Runnable r) {
                final Thread thread = new Thread(r);
                thread.setName("ThPool for " + instanceName + " " + id + " pool worker :- " + SEQ.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        });

        notifierExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {

            public Thread newThread(Runnable r) {
                final Thread thread = new Thread(r);
                thread.setName("DownloadManagerNotifier " + id + " worker");
                thread.setDaemon(true);
                return thread;
            }
        });
        this.connectTimeout.set(connectTimeout);
        this.readTimeout.set(readTimeout);
    }

    @Override
    public int hashCode() {
        return (int)(this.id.longValue() ^ (this.id.longValue() >>> 32));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DownloadManager other = (DownloadManager) obj;
        return this.id.longValue() == other.id.longValue();
    }

    public static DownloadManager newInstance(String instanceName, int maxWorkers, int connectTimeout, int readTimeout) {
        DownloadManager existDwld = nameDwldMap.get(instanceName);
        if(existDwld == null) {
            final DownloadManager newMgr = new DownloadManager(instanceName, maxWorkers, connectTimeout, readTimeout);
            existDwld = nameDwldMap.putIfAbsent(instanceName, newMgr);
            if(existDwld != null) {
                newMgr.executor.shutdownNow();
                newMgr.executor.shutdown();
            } else {
                return newMgr;
            }
        }
        return existDwld; 
    }

    public static DownloadManager newInstance(String instanceName, int maxWorkers) {
        return newInstance(instanceName, maxWorkers, 0, 0);
    }

    public Future<DownloadResult> asyncDownload(RemoteResource resource) {
        return executor.submit(new DownloadTask(null, notifierExecutor, resource, connectTimeout.get(), readTimeout.get()));
    }

    public Future<DownloadResult> asyncDownload(DownloadNotifier notifier, RemoteResource resource) {
        return executor.submit(new DownloadTask(notifier, notifierExecutor, resource, connectTimeout.get(), readTimeout.get()));
    }

    public List<Future<DownloadResult>> asyncDownloadAll(List<RemoteResource> allEntries) {
        return asyncDownloadAll(null, allEntries);
    }

    public List<Future<DownloadResult>> asyncDownloadAll(DownloadNotifier notifier, List<RemoteResource> allEntries) {
        final List<Future<DownloadResult>> retFutures = new ArrayList<Future<DownloadResult>>();
        for (final RemoteResource resource : allEntries) {
            retFutures.add(asyncDownload(notifier, resource));
        }
        return retFutures;
    }

    /**
     * @return the connectTimeout in milliseconds
     */
    public int getConnectTimeout() {
        return connectTimeout.get();
    }

    /**
     * @param connectTimeout
     *            the connectTimeout to set
     */
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout.set(connectTimeout);
    }

    /**
     * @return the readTimeout
     */
    public int getReadTimeout() {
        return readTimeout.get();
    }

    /**
     * @param readTimeout
     *            the readTimeout to set
     */
    public void setReadTimeout(int readTimeout) {
        this.readTimeout.set(readTimeout);
    }

    public boolean cancelAndShutdown() {
        final boolean bRemove = nameDwldMap.remove(this.instanceName, this);
        this.executor.shutdownNow();
        return bRemove;
        
    }
}
