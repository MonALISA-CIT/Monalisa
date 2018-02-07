package lia.util.fsm.alarms;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

import lia.Monitor.ciena.triggers.repository.StateProvider;
import lia.util.fsm.FSMStateChangedEvent;
import lia.util.fsm.GenericFSM;
import lia.util.timestamp.Timestamp;
import lia.util.timestamp.TimestampableStateValue;

/**
 * @see GenericFSM
 * @author ramiro
 */
public abstract class GenericAlarm<T extends Enum<T>, V extends ArmingTimestampable<?>> extends GenericFSM<T> {

    private final Map<T, Timestamp> lastTimeStamps;

    final StateProvider<T, V> stateProvider;

    private final Deque<TimestampableStateValue<T, V>> lastValues;

    private final int samplingSize;

    private final int flipFlopTransitions;

    private final Collection<TimestampableStateValue<T, V>> notifLastValues;

    private Timestamp lastFlipFlop;

    /**
     * @param state
     */
    public GenericAlarm(T state, Class<T> stateClass, StateProvider<T, V> stateProvider) {
        super(state);
        this.stateProvider = stateProvider;
        this.samplingSize = stateProvider.samplingSize();
        this.flipFlopTransitions = stateProvider.flipFlopTransitions();
        this.lastValues = new ArrayDeque<TimestampableStateValue<T, V>>(samplingSize);
        this.notifLastValues = Collections.unmodifiableCollection(Collections.synchronizedCollection(this.lastValues));

        this.lastTimeStamps = new EnumMap<T, Timestamp>(stateClass);
        lastTimeStamps.put(state, new Timestamp());
    }

    public Timestamp getTimeStamp(T state) {
        synchronized (this) {
            return lastTimeStamps.get(state);
        }
    }

    public void notifyValue(V value) {
        T newState = stateProvider.newState(state(), value, notifLastValues);
        if (samplingSize > 0) {
            synchronized (lastValues) {
                final int cSize = lastValues.size();
                if (cSize + 1 >= samplingSize) {
                    lastValues.removeFirst();
                    lastValues.addLast(new TimestampableStateValue<T, V>(newState, value, value.timestamp()));
                }

                if (flipFlopTransitions > 0) {

                }
            }
        }
        getAndSetState(newState, value.timestamp());
    }

    @Override
    protected FSMStateChangedEvent<T, GenericAlarm<T, V>> decorateFSMChangeEvent(FSMStateChangedEvent<T, ? extends GenericFSM<T>> event) {
        try {
            return new GenericAlarmEvent<T, V>(this, event.oldState, event.newState, event.timestamp());
        } finally {
            if (event.oldState != event.newState) {
                this.lastTimeStamps.put(event.newState, event.timestamp());
            }
        }
    }

}
