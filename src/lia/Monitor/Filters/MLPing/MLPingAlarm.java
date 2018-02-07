package lia.Monitor.Filters.MLPing;

import lia.Monitor.ciena.triggers.repository.StateProvider;
import lia.util.fsm.alarms.GenericAlarm;


public class MLPingAlarm extends GenericAlarm<MLPingAlarmState, MLPingMonitoringValue> {

    final String key;
    
    public MLPingAlarm(String key, StateProvider<MLPingAlarmState, MLPingMonitoringValue> theTrigger) {
        super(MLPingAlarmState.DISARMED, MLPingAlarmState.class, theTrigger);
        if(key == null) {
            throw new NullPointerException("GenericAlarm: Null alarm key");
        }
        this.key = key;
    }
}
