/*
 * Created on Jan 16, 2010
 */
package lia.Monitor.ciena.triggers.repository;

import java.util.Vector;

import lia.Monitor.Store.DataSplitter;
import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.TransparentStoreFast;
import lia.Monitor.monitor.TimestampedResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.fsm.alarms.GenericAlarm;

/**
 * @author ramiro
 */
class CienaVCGAlarm extends GenericAlarm<State, CienaVCGMonitoringValue> {

    private static final TransparentStoreFast store = (TransparentStoreFast) TransparentStoreFactory.getStore();
 
    final CienaVCGConfigEntry config;

    public CienaVCGAlarm(CienaVCGConfigEntry config) {
        super(State.DISARMED, State.class, config);
        this.config = config;
    }

    private synchronized void recheckAndSetState() {
        boolean armed = false;
        if (config.checkMLPing()) {
            if (!checkPing()) {
            }
        }
        
        if(armed) {
            armed();
        }
        
    }

    private final void armed() {
        long nanoNotif = 0;
//        synchronized(this) {
//            this.lastTriggered = NTPDate.currentTimeMillis();
//            this.lastTriggeredNano = Utils.nanoNow();
//            nanoNotif = this.lastTriggeredNano - this.lastNotified;
//            this.lastNotified = this.lastTriggeredNano;
//        }
        
    }
    
    private boolean checkPing() {
        final monPredicate pred = config.mlPingPredicate();
        final double mlPingThreshold = config.mlPingThreshold();

        final DataSplitter ds = store.getDataSplitter(new monPredicate[] {pred}, -1);
        final Vector<TimestampedResult> r = ds.get(pred);

        return true;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        return sb.toString();
    }
}
