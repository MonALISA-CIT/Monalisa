package lia.util.fsm.alarms;

/**
 * Generic interface implemented by all possible "arming" values 
 * @author ramiro
 */
public interface ArmingValue<V> {
    public boolean isArmed();
}
