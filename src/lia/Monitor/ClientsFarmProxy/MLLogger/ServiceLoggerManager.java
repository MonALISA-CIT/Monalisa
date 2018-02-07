/*
 * $Id: ServiceLoggerManager.java 7419 2013-10-16 12:56:15Z ramiro $
 */

package lia.Monitor.ClientsFarmProxy.MLLogger;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniSerFarmMon.MLLUSHelper;
import lia.util.ServiceIDComparator;
import lia.util.logging.comm.MLLogMsg;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;

/**
 * You know ... the manager ... he/it manages stuff
 * @author ramiro
 */
public class ServiceLoggerManager extends Thread {

    private static final Logger logger = Logger.getLogger(ServiceLoggerManager.class.getName());

    private static final ServiceLoggerManager _thisInstance;

    private static final ConcurrentSkipListMap<ServiceID, ServiceLoggerConn> loggerConns = new ConcurrentSkipListMap<ServiceID, ServiceLoggerConn>(
            ServiceIDComparator.getInstance());

    private final AtomicBoolean hasToRun;

    static {
        _thisInstance = new ServiceLoggerManager();
        _thisInstance.start();
    }

    private ServiceLoggerManager() {
        super("( ML ) ServiceLoggerManager");
        hasToRun = new AtomicBoolean(true);
    }

    public void publish(MLLogMsg msg) {
        for (final ServiceLoggerConn slc : loggerConns.values()) {
            try {
                slc.publish(msg);
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ ServiceLoggerManager ] Got exc notifying logger service [ " + slc + " ]");
            }
        }
    }

    public void stopIt() {
        hasToRun.set(false);
    }

    public void removeLoggerConn(ServiceLoggerConn slc) {
        loggerConns.remove(slc.getServiceID());
        slc.stopIt();
    }

    public static ServiceLoggerManager getInstance() {
        return _thisInstance;
    }

    @Override
    public void run() {
        final MLLUSHelper lusHelper = MLLUSHelper.getInstance();

        while (hasToRun.get()) {
            try {
                try {
                    ServiceItem[] loggers = lusHelper.getLoggerServices();
                    if ((loggers != null) && (loggers.length != 0)) {
                        for (final ServiceItem si : loggers) {
                            if (loggerConns.containsKey(si.serviceID)) {
                                continue;
                            }
                            ServiceLoggerConn slc = new ServiceLoggerConn(si);
                            loggerConns.put(si.serviceID, slc);
                            slc.start();
                        }
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " [ ServiceLoggerManager ] Got exception in main loop", t);
                }

                try {
                    if (loggerConns.size() == 0) {
                        MLLUSHelper.getInstance().forceUpdate();
                        Thread.sleep(5 * 1000);
                    } else {
                        Thread.sleep(25 * 1000);
                    }
                } catch (Throwable t) {//ignore exception
                }

                if (logger.isLoggable(Level.FINER)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(" ServiceLoggerManager Connections [ ").append(loggerConns.size()).append(" ] \n");
                    int i = 0;
                    for (ServiceLoggerConn slc : loggerConns.values()) {
                        sb.append(" [ ").append(i++).append(" ] = ").append(slc);
                    }

                    logger.log(Level.FINER, sb.toString());
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ ServiceLoggerManager ] Got exception main loop", t);
            }
        }
    }
}
