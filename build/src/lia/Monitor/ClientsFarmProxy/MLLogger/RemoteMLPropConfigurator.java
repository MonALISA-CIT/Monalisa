/*
 * $Id: RemoteMLPropConfigurator.java 7419 2013-10-16 12:56:15Z ramiro $
 */

package lia.Monitor.ClientsFarmProxy.MLLogger;

import java.io.FileInputStream;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ClientsFarmProxy.FarmCommunication;
import lia.Monitor.ClientsFarmProxy.FarmWorker;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.monMessage;
import lia.util.Utils;
import lia.util.logging.comm.ProxyLogMessage;
import net.jini.core.lookup.ServiceID;

/**
 * it should be used carrefully :) 
 * it overrides the remote ml.prop file ;)
 * 
 * @author ramiro
 */
public class RemoteMLPropConfigurator extends Thread implements AppConfigChangeListener {

    private static final Logger logger = Logger.getLogger(RemoteMLPropConfigurator.class.getName());
    private static RemoteMLPropConfigurator _thisInstance;
    private final Object lock;

    private RemoteMLPropConfigurator() {
        super("(ML) RemoteMLPropConfigurator");
        lock = new Object();
        AppConfig.addNotifier(this);
        setDaemon(true);
    }

    public static synchronized final RemoteMLPropConfigurator getInstance() {
        if (_thisInstance == null) {
            _thisInstance = new RemoteMLPropConfigurator();
            _thisInstance.start();
        }

        return _thisInstance;
    }

    private void notifyService(String sid) {
        try {
            String sFile = AppConfig.getProperty(sid + ".remotePropFile");
            if (sFile == null) {
                logger.log(Level.INFO, " No remotePropFile defined for sid [ " + sid + " ]");
                return;
            }

            try {
                Properties p = new Properties();
                p.load(new FileInputStream(sFile));

                Map<ServiceID, FarmWorker> hm = FarmCommunication.getFarmsHash();
                FarmWorker fw = null;

                try {
                    for (final Map.Entry<ServiceID, FarmWorker> entry : hm.entrySet()) {

                        final ServiceID rsid = entry.getKey();
                        final FarmWorker efw = entry.getValue();

                        if (rsid.toString().equals(sid)) {
                            fw = efw;
                            break;
                        }
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exc", t);
                }

                if (fw != null) {
                    ProxyLogMessage plm = new ProxyLogMessage();
                    plm.props = p;
                    monMessage m = new monMessage(FarmWorker.MLLOG_MSG_ID, null, Utils.writeObject(plm));
                    fw.sendMsg(m);
                    logger.log(Level.INFO, " RemoteMLPropConfigurator notified \n" + p + "\n for SID: " + sid);
                } else {
                    logger.log(Level.INFO, " No FarmWorker for sid: " + sid);
                }

            } catch (Throwable t) {
                logger.log(Level.WARNING, " Got exc ", t);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exc ", t);
        }

    }

    private void notifyRemoteServices() {
        try {
            String[] sids = AppConfig.getVectorProperty("SIDs");
            if ((sids == null) || (sids.length == 0)) {
                return;
            }

            for (String sid : sids) {
                if (sid == null) {
                    continue;
                }
                notifyService(sid);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public void run() {
        for (;;) {
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (Throwable t) {
                    try {
                        Thread.sleep(100);
                    } catch (Throwable t1) {
                    }
                }
            }
            notifyRemoteServices();
        }
    }

    @Override
    public void notifyAppConfigChanged() {
        synchronized (lock) {
            lock.notify();
        }
    }

}
