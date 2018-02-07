package lia.Monitor.ciena.triggers.repository;

import java.util.Collection;

import lia.util.fsm.alarms.ArmingTimestampable;
import lia.util.timestamp.TimestampableStateValue;

/**
 * 
 * @author ramiro
 */
public interface StateProvider<T extends Enum<T>, E extends ArmingTimestampable<?>> {
    public T newState(T currentState, E currentValue, Collection<TimestampableStateValue<T, E>> lastValues);
    public int samplingSize();
    public int flipFlopTransitions();
    public boolean isFlipFlopTransition(T startState, T endState);
}
