package lia.util.fsm;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lia.util.timestamp.Timestamp;

/**
 * A simple Finite State Machine with the capability to notify listeners about state changes.
 * The possible states of this FSM are thouse of the {@link Enum} T.</br>
 * The provides additional functionality to test the current state before changing it.</br>
 * </br><b>This class is thread safe.</b></br></br>
 * 
 * All state changes are operated with this object's monitor taken.</br></br>
 * Here is the sample code for all state changes.
 * <pre><code>
 * synchronized(this){
 *      if(currentState != newState) {
 *          //the state is still not changed yet
 *          preChangeState(newState);
 *          state = newState;
 *          changeEvent = decorateFSMChangeEvent(changeEvent);
 *      }
 * }
 * 
 * if(changeEvent != null) {
 *      notifyListeners(changeEvent);
 * }
 * 
 * </code></pre>
 * 
 * The notfication is sent out of the {@code synchronized} block. 
 * 
 * The subclasses can override {@link #preChangeState(Enum) #preChangeState(newState)}
 * and {@link #decorateFSMChangeEvent(FSMStateChangedEvent) decorateFSMChangeEvent(event)}
 * 
 * @author ramiro
 */
public abstract class GenericFSM<T extends Enum<T>> {

    /**
     * current state of the FSM; it's always gurded by this object's monitor.
     */
    private T state;
    
    /**
     * the registered set of states which are notified 
     */
    private final List<FSMStateChangeListener<T, ? extends GenericFSM<T>>> listeners;
    
    /**
     * Creates a new <code>GenericFSM</code> with the given initial state
     * @param initialState
     */
    public GenericFSM(T initialState) {
        this.state = initialState;
        this.listeners = new CopyOnWriteArrayList<FSMStateChangeListener<T, ? extends GenericFSM<T>>>();
    }

    /**
     * Atomically sets the state to the <code>newState</code>. 
     * The registered set of listeners is notified if state {@code !=} newState
     * 
     * @param newState the new state
     * @return the previous state of the FSM
     */
    public T getAndSetState(T newState, Timestamp timestamp) {
        T oldState;
        FSMStateChangedEvent<T, ? extends GenericFSM<T>> event = null;
        synchronized(this) {
            oldState = state;
            if(newState != oldState) {
                preChangeState(newState);
                state = newState;
                event = decorateFSMChangeEvent(new FSMStateChangedEvent<T, GenericFSM<T>>(this, oldState, newState, timestamp));
            }
        }
        
        if(event != null) {
            notifyListeners(event);
        }
        return oldState;
    }

    public T getAndSetState(T newState) {
        return getAndSetState(newState, new Timestamp());
    }
    
    /**
     * Atomically sets the state to the <code>newState</code>
     * if the current state == newState. The call is the same with 
     * {@link #compareAndSet(EnumSet, Enum) compareAndSet(expectedPossibleStates, newState)}
     * where expectedPossibleStates is constructed as {@link EnumSet#of(Enum) EnumSet.of(expectedState)}
     * @param possibleExpectedStates the possible expected set of states 
     * @param newState the new state
     * @return true if successful. In this case the registered set of listeners 
     *      is notified about the change 
     */
    public boolean compareAndSet(T expectedState, T newState) {
        FSMStateChangedEvent<T, ? extends GenericFSM<T>> event = null;
        synchronized(this) {
            if(state == expectedState) {
                preChangeState(newState);
                state = newState;
                event = decorateFSMChangeEvent(new FSMStateChangedEvent<T, GenericFSM<T>>(this, expectedState, newState));
            }
        }
     
        if(event != null) {
            notifyListeners(event);
        }
        
        return false;
    }
    
    /**
     * Atomically sets the state to the <code>newState</code>
     * if the current state is in one of the <code>possibleExpectedStates</code> set.
     * 
     * @param expectedPossibleStates the possible expected set of states 
     * @param newState the new state
     * @return true if successful. In this case all the registered listeners are notified
     *      about this change
     * @see 
     */
    public boolean compareAndSet(EnumSet<T> expectedPossibleStates, T newState) {
        T oldState;
        FSMStateChangedEvent<T, ? extends GenericFSM<T>> event = null;
        synchronized(this) {
            oldState = state;
            if(expectedPossibleStates.contains(state)) {
                preChangeState(newState);
                state = newState;
                event = decorateFSMChangeEvent(new FSMStateChangedEvent<T, GenericFSM<T>>(this, oldState, newState));
            }
        }
        
        if(event != null) {
            notifyListeners(event);
        }
        
        return false;
    }
    
    /**
     * Returns the current state
     * 
     * @return current state
     */
    public synchronized T state() {
        return state;
    }

    /**
     * Tests the current state with the expectedState
     * 
     * @param expectedState
     * @return - true if state == expectedState, false otherwise
     */
    public synchronized boolean testState(T expectedState) {
        return state == expectedState;
    }
    
    /**
     * Notifies all registered listeners. The classes which extends can filter
     * some events.
     *  
     * @param oldState previous state 
     * @param newState current state
     */
    protected void notifyListeners(FSMStateChangedEvent<T, ? extends GenericFSM<T>> event) {
        for(final FSMStateChangeListener<T, ? extends GenericFSM<T>> l: listeners) {
            l.stateChaged(event);
        }
    }
    
    /**
     * Adds a listener to the set of listeners
     * @param l the listener
     */
    public void addListener(FSMStateChangeListener<T, ? extends GenericFSM<T>> l) {
        this.listeners.add(l);
    }

    /**
     * Removes a listener from the set of registered listeners
     * 
     * @param l the listener
     * @return true if the list of listener contained the specified listener 
     */
    public boolean removeListener(FSMStateChangeListener<T, ? extends GenericFSM<T>> l) {
        return this.listeners.remove(l);
    }

    /**
     * This method is called right before {@code state} = {@code newState}
     * 
     * </br></br><b>The method is called with this object's monitor lock taken ( {@code synchronized(this)} )</b>
     * 
     * @param newState new state after this method is called
     */
    protected void preChangeState(T newState) {}
    
    /**
     * This method is called right after the new state has changed. 
     * The default implementation will return the same event.
     * 
     * The subclasses can override this method and return a subclass of {@link FSMStateChangedEvent},
     * or can return {@code null}. In case {@code null} is returned the 
     * listeners are not notified.
     * 
     * </br></br><b>The method is called with this object's monitor lock taken ( {@code synchronized(this)} )</b>
     * 
     * @param event to be "decorated"
     * @return the "new" event to be notified
     */
    protected FSMStateChangedEvent<T, ? extends GenericFSM<T>> decorateFSMChangeEvent(FSMStateChangedEvent<T, ? extends GenericFSM<T>> event) {
        return event;
    }
}
