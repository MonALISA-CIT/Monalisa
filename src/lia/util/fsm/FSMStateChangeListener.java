/*
 * Created on Feb 1, 2010
 */
package lia.util.fsm;

import java.util.EventListener;

/**
 * 
 * @author ramiro
 */
public interface FSMStateChangeListener<T extends Enum<T>, E extends GenericFSM<T>> extends EventListener {

    /**
     * This method is called by a FSM to notify a change.
     * @param event
     */
    public void stateChaged(FSMStateChangedEvent<T, ? extends GenericFSM<T>> event);
}
