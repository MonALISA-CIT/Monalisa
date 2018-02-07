/*
 * $Id: GMLEPublisher.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.JiniSerFarmMon;

import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.AttributePublisher;
import lia.Monitor.monitor.GenericMLEntry;
import lia.Monitor.monitor.MLAttributePublishers;
import lia.Monitor.monitor.MLJiniManagersProvider;
import lia.util.threads.MonALISAExecutors;
import net.jini.core.entry.Entry;
import net.jini.lookup.JoinManager;

/**
 * 
 * Helper class which publishes in the LUSs different attributes 
 * modifying the {@link GenericMLEntry} attribute
 * 
 * It is run as a periodic task. The default delay is 3 minutes. This may
 * be dynamically changed in the config properties file (ml.properties) using
 * the property <code>lia.Monitor.JiniSerFarmMon.GMLEPublisher.publishDelay</code>
 * 
 * The property is specified in seconds. The minimum accepted value is 10 seconds.
 * 
 * @author ramiro
 * 
 */
public class GMLEPublisher implements AttributePublisher {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(GMLEPublisher.class.getName());
    /** the one and only instance */
    private static final GMLEPublisher _thisInstance = new GMLEPublisher();
    /** temporary holder of attributes between updates */
    private final Hashtable<Object, Object> attrToPublish = new Hashtable<Object, Object>();
    /** Delay between */
    private static final AtomicLong publishDelay = new AtomicLong(3 * 60);

    //internal sync stuff which keep the state of the publisher;
    /** used to force the publishing. it should be true only when the publish was forced using <code>updateNow()</code> methods */
    private static boolean shouldForcePublish = true;
    /** is there a publishing task already running ?? */
    private static boolean isAlreadyRunning = false;
    /** sync guard for the state boolean variables <code>shouldPublish</code> and <code>publishing</code> */
    private static final Object stateLock = new Object();
    private static final Callable<Object> _theRealTask = Executors.privilegedCallable(new Callable<Object>() {

        @Override
        public Object call() throws Exception {
            _thisInstance.publishAndResched(true);
            return null;
        }
    });
    private static final Callable<Object> _theNaughtyTask = Executors.privilegedCallable(new Callable<Object>() {

        @Override
        public Object call() throws Exception {
            _thisInstance.publishAndResched(false);
            return null;
        }
    });

    static {
        reloadConf();

        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                reloadConf();
            }
        });

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ GMLEPublisher ] [ SCHED ] dumping thread: ", new Throwable(
                    "[ GMLEPublisher DEBUG Dump ]"));
        }

        MLAttributePublishers.addPublisher(_thisInstance);

        MonALISAExecutors.getMLHelperExecutor().schedule(_theRealTask, publishDelay.get(), TimeUnit.SECONDS);
    }

    private static void reloadConf() {
        long pDelay = 3 * 60;
        //let it eventualy change the publish delay without restarting ML
        try {
            //should be specified in seconds in ml.properties 
            pDelay = AppConfig.getl("lia.Monitor.JiniSerFarmMon.GMLEPublisher.publishDelay", 3 * 60);
        } catch (Throwable t) {
            pDelay = 3 * 60; //every 3 minutes
        }

        if (pDelay < 10) {
            pDelay = 10;
        }

        publishDelay.set(pDelay);

        final String sLevel = AppConfig.getProperty("lia.Monitor.JiniSerFarmMon.GMLEPublisher.level");
        Level loggingLevel = null;
        if (sLevel != null) {
            try {
                loggingLevel = Level.parse(sLevel);
            } catch (Throwable t) {
                loggingLevel = null;
            }

            logger.setLevel(loggingLevel);
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [ GMLEPublisher ] reloadedConf. Logging level: " + loggingLevel);
        }

    }

    private GMLEPublisher() {
        //singleton
    }

    public static synchronized final GMLEPublisher getInstance() {
        return _thisInstance;
    }

    private void publishAttributes() {

        GenericMLEntry gmle = null;
        final long sTime = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();

        try {

            final JoinManager jmngr = MLJiniManagersProvider.getJoinManager();
            if (jmngr == null) {
                if (logger.isLoggable(Level.FINE)) {
                    sb.append("\n[ GMLEPublisherTask ] [ publishAttributes ] The JoinManager is null for the moment ....");
                }
                return;
            }

            final Entry[] attributes = jmngr.getAttributes();
            for (Entry attribute : attributes) {
                if (attribute instanceof GenericMLEntry) {
                    gmle = (GenericMLEntry) attribute;

                    if (logger.isLoggable(Level.FINER)) {
                        sb.append("\n[ GMLEPublisherTask ] [ publishAttributes ] existing attributes: ").append(
                                gmle.hash);
                    }

                    synchronized (attrToPublish) {
                        gmle.hash.putAll(attrToPublish);
                        attrToPublish.clear();
                    }

                    if (logger.isLoggable(Level.FINER)) {
                        sb.append("\n[ GMLEPublisherTask ] [ publishAttributes ] publishing new attributes: ").append(
                                gmle.hash);
                    }

                    jmngr.modifyAttributes(new GenericMLEntry[] { new GenericMLEntry() }, new GenericMLEntry[] { gmle });
                    return;
                }
            }
            logger.log(
                    Level.WARNING,
                    "[ GMLEPublisherTask ] [ publishAttributes ] [ HANDLED ] GenericMLEntry attribute not found in the published attributes list!");
        } catch (Throwable t) {
            logger.log(Level.WARNING,
                    "[ GMLEPublisherTask ] [ publishAttributes ] [ HANDLED ] Exception publishing attrs to LUSs", t);
            //do not loose any "unpublished" attrs
            if ((gmle != null) && (gmle.hash != null)) {
                attrToPublish.putAll(gmle.hash);
            }
        } finally {
            if (logger.isLoggable(Level.FINE)) {
                sb.append("\n[ GMLEPublisherTask ] [ publishAttributes ] publishing the attrs took: ")
                        .append(System.currentTimeMillis() - sTime).append(" ms");
                logger.log(Level.FINE, sb.toString());
            }
        }
    }

    @Override
    public void publish(final Object key, final Object value) {
        attrToPublish.put(key, value);
    }

    @Override
    public void publish(final Map<?, ?> map) {
        attrToPublish.putAll(map);
    }

    @Override
    public void publishNow(final Map<?, ?> map) {
        publish(map);
        checkAndStartPublishNow();
    }

    @Override
    public void publishNow(final Object key, final Object value) {
        publish(key, value);
        checkAndStartPublishNow();
    }

    private void checkAndStartPublishNow() {
        synchronized (stateLock) {
            if (isAlreadyRunning) {
                shouldForcePublish = true;
            } else {
                //spawn a new publish task right now!
                MonALISAExecutors.getMLHelperExecutor().submit(_theNaughtyTask);
            }
        }
    }

    void publishAndResched(final boolean shouldResched) {

        final String cTaskName = "[ GMLEPublisher ] [ publishAndResched ] shouldResched=" + shouldResched + " TName: "
                + Thread.currentThread().getName();

        synchronized (stateLock) {
            if (!isAlreadyRunning) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, cTaskName + " started ...  isAlreadyRunning=" + isAlreadyRunning);
                }
                shouldForcePublish = false;
                isAlreadyRunning = true;
            } else {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, cTaskName + " started ... isAlreadyRunning=" + isAlreadyRunning
                            + " ... I will reschedule a normal publish");
                }
                //reschedule a normal publish
                if (shouldResched) {
                    MonALISAExecutors.getMLHelperExecutor()
                            .schedule(_theRealTask, publishDelay.get(), TimeUnit.SECONDS);
                    return;
                }
            }
        }

        try {
            for (;;) {
                if (!attrToPublish.isEmpty()) {
                    publishAttributes();
                }

                synchronized (stateLock) {
                    if (shouldForcePublish) {
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, cTaskName + " [ mainLoop ] ... isAlreadyRunning="
                                    + isAlreadyRunning + " shouldForcePublish=" + shouldForcePublish
                                    + "... I rerun the main loop");
                        }
                        shouldForcePublish = false;
                        isAlreadyRunning = true;
                        continue;
                    }

                    isAlreadyRunning = false;
                }

                break;
            }
        } finally {

            synchronized (stateLock) {
                isAlreadyRunning = false;
            }

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, cTaskName + "... exits main loop. isAlreadyRunning=" + isAlreadyRunning
                        + ", shouldForcePublish=" + shouldForcePublish + ", shouldResched=" + shouldResched);
            }

            if (shouldResched) {
                MonALISAExecutors.getMLHelperExecutor().schedule(_theRealTask, publishDelay.get(), TimeUnit.SECONDS);
            }
        }
    }
}
