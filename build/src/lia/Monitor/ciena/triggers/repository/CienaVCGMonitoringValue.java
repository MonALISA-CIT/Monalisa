package lia.Monitor.ciena.triggers.repository;

import lia.Monitor.monitor.Result;
import lia.util.fsm.alarms.ArmingTimestampable;
import lia.util.timestamp.TimeStampedValue;


public class CienaVCGMonitoringValue extends TimeStampedValue<Result> implements ArmingTimestampable<Result> {

    final boolean isArmed;
    
    CienaVCGMonitoringValue(final Result r, boolean isArmed) {
        super(r);
        this.isArmed = isArmed;
    }

    public boolean isArmed() {
        return isArmed;
    }

}
