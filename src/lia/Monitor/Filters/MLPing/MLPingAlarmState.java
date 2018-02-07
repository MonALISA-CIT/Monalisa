package lia.Monitor.Filters.MLPing;

/**
 * 
 * @author ramiro
 *
 */
public enum MLPingAlarmState {
    /**
     * Intermediate state between {@link #DISARMED} and {@link #ARMED} Signals that the alarm is about to be armed. An
     * alarm can go directly in the {@link #ARMED} state
     */
    PRE_ARMED,

    /**
     * Signals that the alarm is triggered.
     */
    ARMED,

    /**
     * Intermediate state between {@link #ARMED} and {@link #DISARMED}. Signals that the alarm is about to be disarmed.
     * An alarm can go directly in the {@link #DISARMED} state
     */
    PRE_DISARMED,

    /**
     * Signals that the alarm is not triggered.
     */
    DISARMED
}
