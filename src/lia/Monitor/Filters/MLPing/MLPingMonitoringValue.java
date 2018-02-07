package lia.Monitor.Filters.MLPing;

import lia.Monitor.monitor.Result;
import lia.util.fsm.alarms.ArmingTimestampable;
import lia.util.timestamp.TimeStampedValue;
import lia.util.timestamp.Timestamp;
import lia.util.timestamp.Timestampable;

public class MLPingMonitoringValue extends TimeStampedValue<Result> implements ArmingTimestampable<Result> {
    final boolean isArmed;
    
    MLPingMonitoringValue(final Result r, boolean isArmed) {
        super(r);
        this.isArmed = isArmed;
    }
    
    public boolean isArmed() {
        return isArmed();
    }

    public Timestamp timestamp() {
        return timestamp();
    }

    public int compareTo(Timestampable<Result> o) {
        return this.timestamp().compareTo(o.timestamp());
    }

}
