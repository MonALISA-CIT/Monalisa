/*
 * $Id: ServiceLoggerConn.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.ClientsFarmProxy.MLLogger;

import java.net.InetAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ClientsFarmProxy.Service;
import lia.Monitor.JiniClient.Store.ServiceGroups;
import lia.Monitor.monitor.GenericMLEntry;
import lia.Monitor.monitor.MLLoggerSI;
import lia.Monitor.monitor.monMessage;
import lia.Monitor.monitor.tcpConn;
import lia.Monitor.monitor.tcpConnNotifier;
import lia.util.Utils;
import lia.util.logging.comm.MLLogMsg;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;

/**
 * Wrapper class for communication between proxy and a MLLogerService 
 * @author ramiro
 */
public class ServiceLoggerConn extends Thread implements tcpConnNotifier {

    private static final Logger logger = Logger.getLogger(ServiceGroups.class.getName());

    ServiceItem si;
    private final AtomicBoolean hasToRun;
    tcpConn conn;
    ArrayBlockingQueue<MLLogMsg> queue;

    public ServiceLoggerConn(ServiceItem si) {
        super("( ML ) ServiceLoggerConn for " + si.serviceID);
        this.si = si;
        queue = new ArrayBlockingQueue<MLLogMsg>(100);
        hasToRun = new AtomicBoolean(true);
    }

    public void publish(MLLogMsg msg) {
        queue.offer(msg);
    }

    @Override
    public void notifyConnectionClosed() {
        stopIt();
    }

    /**
     * @param o  
     */
    @Override
    public void notifyMessage(Object o) {
        //TODO
    }

    public ServiceID getServiceID() {
        return this.si.serviceID;
    }

    public void stopIt() {
        if (hasToRun.compareAndSet(true, false)) {
            this.interrupt();
        }
    }

    private void cleanup() {
        try {
            if (this.conn != null) {
                this.conn.close_connection();
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception in cleanup() closing connection", t);
        }

        try {
            ServiceLoggerManager.getInstance().removeLoggerConn(this);
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception in cleanup() removing LoggerConn from Logger Manager", t);
        }
    }

    private boolean connect() {
        try {
            if ((si == null) || (si.service == null) || (si.attributeSets == null)) {
                logger.log(Level.WARNING,
                        " [ ServiceLoggerConn ] Eiteher ServiceItem, or si.service or si.attributesSets is null");
                return false;
            }

            if (si.service instanceof MLLoggerSI) {
                GenericMLEntry gmle = Utils.getEntry(si, GenericMLEntry.class);
                if (gmle == null) {
                    logger.log(Level.WARNING, " [ ServiceLoggerConn ] GMLE is null!!");
                    return false;
                }

                if (gmle.hash == null) {
                    logger.log(Level.WARNING, " [ ServiceLoggerConn ] GMLE.hash is null!!");
                    return false;
                }

                String hostname = (String) gmle.hash.get("hostname");
                InetAddress ia;
                if ((hostname == null) || (hostname.length() == 0)) {
                    logger.log(Level.WARNING, " [ ServiceLoggerConn ] hostname is null or empty in gmle is null!!");
                    return false;
                }

                try {
                    ia = InetAddress.getByName(hostname);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " [ ServiceLoggerConn ] Cannot determine hostname address", t);
                    ia = null;
                }

                if (ia == null) {
                    return false;
                }

                int port = -1;
                try {
                    port = Integer.parseInt((String) gmle.hash.get("port"));
                } catch (Throwable t) {
                    logger.log(Level.WARNING,
                            " [ ServiceLoggerConn ] Got exception trying to determine port from GMLE.", t);
                    port = -1;
                }

                if (port == -1) {
                    logger.log(Level.WARNING, " [ ServiceLoggerConn ] port is -1!!");
                    return false;
                }

                try {
                    this.conn = tcpConn.newConnection(this, ia, port);
                    this.conn.startCommunication();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " Cannot init tcpConn", t);
                    return false;
                }

                //every thing OK!
                return true;
            }
            logger.log(Level.WARNING, " [ ServiceLoggerConn ] got a ServiceItem which is not instanceof MLLoggerSI"
                    + si.service.getClass());
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ ServiceLoggerConn ] got general exception in connect()", t);
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(" [ ServiceLoggerConn ] [ ").append((this.si == null) ? "null" : this.si.serviceID).append(" ] for ");
        sb.append(conn);
        return sb.toString();
    }

    @Override
    public void run() {
        hasToRun.set(connect());

        boolean notified = false;
        try {
            ServiceID sid = null;
            while ((sid == null) && !notified) {
                sid = Service.getInstance().proxyID;
                if (sid != null) {
                    monMessage mm = new monMessage(null, null, sid);
                    this.conn.sendMsg(mm);
                    notified = true;
                }
                try {
                    Thread.sleep(5 * 1000);
                } catch (Throwable t) {//ignore
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ ServiceLoggerConn ] Got exception notifying SID ... will exit");
            hasToRun.set(false);
        }

        setName("( ML ) ServiceLoggerConn for " + si.serviceID + " :- " + conn);

        while (hasToRun.get()) {
            MLLogMsg mlm = null;
            try {
                mlm = queue.take();
            } catch (InterruptedException ie) {
                logger.log(Level.WARNING, " [ ServiceLoggerConn ] Got Interrupted Exception ...", ie);
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ ServiceLoggerConn ] Got General Exception ...", t);
            }

            try {
                if (mlm != null) {
                    monMessage mm = new monMessage(null, null, mlm);
                    conn.sendMsg(mm);
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ ServiceLoggerConn ] Got exception sending message", t);
            }
        }

        cleanup();
    }
}
