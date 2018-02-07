/*
 * Created on Aug 19, 2010
 *
 */
package lia.util.rrd;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AccountingResult;
import lia.Monitor.monitor.DataReceiver;
import lia.Monitor.monitor.ExtResult;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;

import org.uslhcnet.rrd.config.RRDConfigManager;

/**
 * @author ramiro
 */
public class RRDDataReceiver implements DataReceiver {
    private static final Logger logger = Logger.getLogger(RRDDataReceiver.class.getName());

    static {
        //force loading for RRDConfigManager
        try {
            RRDConfigManager.getInstance();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ RRDDataReceiver ] Unable to load RRDConfigManager");
        }

        try {
            RRDDataReceiverConfigMgr.getInstance();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ RRDDataReceiver ] Unable to load RRDDataReceiverConfigMgr");
        }
    }
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);

    private static final class DataReceiverTask implements Runnable {
        private final Result r;

        DataReceiverTask(Result result) {
            this.r = result;
        }

        @Override
        public void run() {
            try {
                MLRRDWrapper.updateResult(r);
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ DataReceiverTask ] Exception processing result: " + r + "; Cause:", t);
            }
        }
    }

    /* (non-Javadoc)
     * @see lia.Monitor.monitor.DataReceiver#addResult(lia.Monitor.monitor.Result)
     */
    @Override
    public void addResult(Result r) throws Exception {
        executor.execute(new DataReceiverTask(r));
    }

    /* (non-Javadoc)
     * @see lia.Monitor.monitor.DataReceiver#addResult(lia.Monitor.monitor.eResult)
     */
    @Override
    public void addResult(eResult r) throws Exception {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see lia.Monitor.monitor.DataReceiver#addResult(lia.Monitor.monitor.ExtResult)
     */
    @Override
    public void addResult(ExtResult r) throws Exception {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see lia.Monitor.monitor.DataReceiver#addResult(lia.Monitor.monitor.AccountingResult)
     */
    @Override
    public void addResult(AccountingResult r) throws Exception {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see lia.Monitor.monitor.DataReceiver#updateConfig(lia.Monitor.monitor.MFarm)
     */
    @Override
    public void updateConfig(MFarm farm) throws Exception {
        // TODO Auto-generated method stub

    }

}
