package lia.util.fsm.alarms;

import lia.util.fsm.FSMStateChangedEvent;
import lia.util.timestamp.Timestamp;

public class GenericAlarmEvent<T extends Enum<T>, V extends ArmingTimestampable<?>> extends FSMStateChangedEvent<T, GenericAlarm<T, V>> {

    private static final long serialVersionUID = 4069814396235425672L;

    public final Timestamp oldStateTimeStamp;

    public final Timestamp newStateTimeStamp;

    GenericAlarmEvent(GenericAlarm<T, V> source, T oldState, T newState) {
        this(source, oldState, newState, new Timestamp());
    }

    GenericAlarmEvent(GenericAlarm<T, V> source, T oldState, T newState, Timestamp timestamp) {
        super(source, oldState, newState, timestamp);
        this.oldStateTimeStamp = source.getTimeStamp(oldState);
        this.newStateTimeStamp = source.getTimeStamp(newState);
    }

}
