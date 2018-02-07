package lia.util.logging.service;

import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.loader.MLContainer;
import lia.util.logging.comm.MLLogMsg;

/**
 * Wrapper Thread for every MLLoggerPublisher.
 * It will stay alive as long as this publisher is defined in ml.prop file 
 * of the Logger Service
 * 
 * This class is visible only in this package
 * 
 * @author ramiro
 */
class PublisherThread extends Thread implements Observer {

    private static final Logger logger = Logger.getLogger(PublisherThread.class.getName());

    MLContainer container;
    private final AtomicBoolean hasToRun;
    MLLoggerPublisher publisher;
    final ArrayBlockingQueue<MLLogMsg> queue;

    private String[] classPath;
    private final ClassLoader initialClassLoader;
    private final String publisherName;
    private String publisherClassName;

    private final Object reloadSyncLock;

    /**
     * Only visible from the current package
     * @param publisherName
     * @param classPath
     * @throws NullPointerException if either publisherName is null, or classPath is null
     */
    PublisherThread(String publisherName, String[] classPath) throws Exception {
        super();
        hasToRun = new AtomicBoolean(true);

        if (publisherName == null) {
            hasToRun.set(false);
            start();
            throw new NullPointerException(" publisherName cannot be null ");
        }

        if (classPath == null) {
            hasToRun.set(false);
            start();
            throw new NullPointerException(" classPath cannot be null ");
        }
        reloadSyncLock = new Object();
        initialClassLoader = getContextClassLoader();

        this.classPath = classPath;
        this.publisherName = publisherName;

        setName("( ML ) PublisherThread for " + this.publisherName);

        //this will hold the MLLogEvents as long as this thread is alive
        queue = new ArrayBlockingQueue<MLLogMsg>(250);

        start();

    }

    public String getPublisherName() {
        return publisherName;
    }

    /**
     * WARNING:  this method should be called with reloadSyncLock locked !
     * It will not check for it
     */
    private void cleanupThreadContext() {

        this.publisherClassName = null;

        try {
            if (publisher != null) {
                publisher.finish();
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception calling finish() on publisher");
        } finally {
            publisher = null;
        }

        try {
            if (container != null) {
                container.deleteObserver(this);
                container.cleanup();
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception calling cleanup() on container");
        } finally {
            container = null;
        }

        if (initialClassLoader != null) {
            this.setContextClassLoader(initialClassLoader);
        }

    }

    private void reloadThreadContext() throws Exception {
        try {
            synchronized (reloadSyncLock) {
                //cleanup first
                cleanupThreadContext();

                container = new MLContainer(classPath, 5 * 1000);
                container.addObserver(this);

                this.setContextClassLoader(container.getClassLoader());

                Manifest[] manifests = container.getManifests();
                if ((manifests != null) && (manifests.length > 0)) {
                    for (Manifest m : manifests) {
                        Attributes attrs = m.getMainAttributes();
                        if (attrs == null) {
                            continue;
                        }
                        String className = attrs.getValue("MLLoggerPublisher-Class");
                        if (className == null) {
                            continue;
                        }
                        className = className.trim();

                        publisher = (MLLoggerPublisher) (getContextClassLoader().loadClass(className).newInstance());
                        this.publisherClassName = className;

                        setName("( ML ) PublisherThread [ " + publisherName + " ] for Class " + className
                                + " :- Instantiated");

                        //first come - first served ...
                        break;
                    }
                }

                if (publisher == null) {
                    throw new Exception("No MLLoggerPublisher-Class defined for " + publisherName);
                }
            }//end sync
        } finally {

        }
    }

    private String getExtendedStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n PublisherThread [ ").append(publisherName).append(" ] Extended Status\n");
        sb.append("\n Publisher Name: ").append(publisherName);
        sb.append("\n Publisher ClassPath: ").append(Arrays.toString(classPath));
        sb.append("\n MLLoggerPublisher-Class: ").append(this.publisherClassName);
        sb.append("\n queue.size(): ").append(((queue == null) ? "null" : "" + queue.size()));
        sb.append("\n Publisher: ").append(publisher).append("\n");
        return sb.toString();
    }

    /**
     * @return - the current classpath
     */
    public String[] getClassPath() {
        synchronized (reloadSyncLock) {
            return classPath;
        }
    }

    /**
     * Sets the new classpath. If different from the old one, this thread will reload the context
     * @param newClassPath
     */
    public void setClassPath(String[] newClassPath) {
        if (Arrays.equals(newClassPath, this.classPath)) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ PublisherThread ] " + publisherName
                        + " :- Old class path and new class path are the same: " + Arrays.toString(newClassPath));
            }
            return;
        }

        logger.log(Level.INFO, " [ PublisherThread ] New ClassPath " + Arrays.toString(newClassPath)
                + "\n Old ClassPath " + Arrays.toString(this.classPath));

        synchronized (reloadSyncLock) {
            this.classPath = newClassPath;
            try {
                reloadThreadContext();
            } catch (Throwable t) {
                logger.log(Level.WARNING,
                        " [ PublisherThread ] [ setClassPath ] Got exception trying to reload the thread context", t);
            }

            if (publisher != null) {
                reloadSyncLock.notify();
            }
        }
    }

    private void notifyPublisher(MLLogMsg mlm) {
        try {
            publisher.publish(mlm);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ PublisherThread ] :- " + getName() + " Got exception! trying to publish "
                    + mlm, t);
        }
    }

    /**
     * It will stop the current thread and clean its context
     */
    public void stopIt() {
        if (hasToRun.compareAndSet(true, false)) {//do not interrupt twice
            //if the current thread is waiting on queue interrupt it
            //or waiting on reloadSyncLock
            this.interrupt();
        }
    }

    boolean publish(MLLogMsg mlm) {
        if (hasToRun.get()) {
            return queue.offer(mlm);
        }

        logger.log(Level.INFO, "[ PublisherThread ] :- " + publisherName + " publishe() called but hasToRun = "
                + hasToRun);
        return false;
    }

    @Override
    public void run() {

        try {
            reloadThreadContext();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ PublisherThread ] Got exception trying to reload the thread context", t);
        }

        if (publisher != null) {
            logger.log(Level.INFO, "[ PublisherThread ] :- " + publisherName + " started main loop()");
        }

        while (hasToRun.get()) {

            try {

                synchronized (reloadSyncLock) {
                    while (hasToRun.get() && (publisher == null)) {
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, " [ PublisherThread ] :- " + publisherName
                                    + " has no publisher defined ... will wait");
                        }
                        reloadSyncLock.wait();
                    }
                    if (!hasToRun.get()) {
                        break;
                    }
                }

                MLLogMsg mlle = queue.take();
                synchronized (reloadSyncLock) {
                    if ((mlle != null) && (publisher != null)) {
                        notifyPublisher(mlle);
                    }
                }

            } catch (InterruptedException ie) {
                logger.log(Level.INFO, "[ PublisherThread ] :- " + getName() + " interrupted ...");
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[ PublisherThread ] :- " + getName() + " Got ex mainLoop.", t);
            }

        }//end loop

        logger.log(Level.INFO, "[ PublisherThread ] :- " + publisherName + " finished ... will cleanup()");

        try {
            synchronized (reloadSyncLock) {
                cleanupThreadContext();
            }
            MLLoggerService.getInstance().removePublisher(this.publisherName);
        } catch (Throwable t) {
            logger.log(Level.INFO, " Got exception trying to cleanup the thread after main loop finished.", t);
        } finally {
            queue.clear();
        }

    }

    @Override
    public String toString() {
        return " PublisherThread [ " + publisherName + " ] MLLoggerPublisher-Class: {" + publisherClassName
                + "} ClassPath: " + Arrays.toString(classPath);
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o != null) {
            if (o instanceof MLContainer) {
                logger.log(Level.INFO, " [ PublisherThread ] MLContainer notified classpath changes [ " + arg + " ]");
                try {
                    synchronized (reloadSyncLock) {
                        try {
                            reloadThreadContext();
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, " Got exception reloading the thread context", t);
                        }

                        if (publisher != null) {
                            reloadSyncLock.notify();
                        }
                    }

                } catch (Throwable t) {
                    logger.log(Level.WARNING,
                            " [ PublisherThread ] [ update ] Got exception trying to reload the publisher", t);
                }
            }
        }
    }
}
