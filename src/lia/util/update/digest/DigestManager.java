/*
 * Created on Sep 24, 2010
 */
package lia.util.update.digest;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author ramiro 
 */
public class DigestManager {

    protected static AtomicInteger DMGR_SEQ = new AtomicInteger(1);

    private final int id;

    protected ExecutorService executor;

    private static final DigestManager _thisInstance = new DigestManager(2);
    
    private DigestManager(int maxWorkers) {
        this.id = DMGR_SEQ.getAndIncrement();
        executor = Executors.newFixedThreadPool(maxWorkers, new ThreadFactory() {

            final AtomicInteger SEQ = new AtomicInteger(1);

            public Thread newThread(Runnable r) {
                final Thread thread = new Thread(r);
                thread.setName("DigestManager " + id + " pool worker :- " + SEQ.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        });

    }

    public static DigestManager getInstance() {
        return _thisInstance;
    }
    
    public static DigestManager newInstance(int maxWorkers) {
        return new DigestManager(maxWorkers);
    }

    public Future<DigestResult> asyncDigest(File file, final String digestAlgo) throws NoSuchAlgorithmException {
        return executor.submit(new DigestTask(file, digestAlgo));
    }

    public List<Future<DigestResult>> asyncDigest(List<File> allFiles, final String digestAlgo) throws NoSuchAlgorithmException {
        final List<Future<DigestResult>> retFutures = new ArrayList<Future<DigestResult>>();
        for (final File file : allFiles) {
            retFutures.add(asyncDigest(file, digestAlgo));
        }
        return retFutures;
    }


}
