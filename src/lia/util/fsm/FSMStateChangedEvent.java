package lia.util.fsm;

import java.util.EventObject;

import lia.util.timestamp.Timestamp;
import lia.util.timestamp.Timestampable;

/**
 * This event is used by a {@link GenericFSM} to notify its listeners.
 * <p><b>Important note:</b> These events are ordered in time.
 *    
 * @author ramiro
 */
public class FSMStateChangedEvent<T extends Enum<T>, E extends GenericFSM<T>> extends EventObject implements Timestampable<FSMStateChangedEvent<T, E>>{

    private static final long serialVersionUID = 2408193010646867787L;

    /**
     * previous FSM state of the FSM
     */
    public final T oldState;
    
    /**
     * new FSM state when this event was generated
     */
    public final T newState;

    /**
     * The timestamp of the event in {@link System#nanoTime() nanoseconds}. 
     */
    private final Timestamp timestamp;
    
    /**
     * Constructs an event with the specified parameters
     * 
     * @param source the event
     * @param oldState previous state
     * @param newState current state
     */
    public FSMStateChangedEvent(E source, T oldState, T newState) {
        this(source, oldState, newState, new Timestamp());
    }
    
    public FSMStateChangedEvent(E source, T oldState, T newState, Timestamp timestamp) {
        super(source);
        if(timestamp == null) {
            throw new NullPointerException("Null timestamp");
        }
        this.oldState = oldState;
        this.newState = newState;
        this.timestamp = timestamp; 
    }

    @Override
    public String toString() {
        return getClass().getName() + "[source=" + source + ", oldState=" +oldState + ", newState="+ newState + "]";
    }

    public E getSource() {
        return (E)this.source;
    }
    
    /**
     * @return the timestamp of the event
     */
    public Timestamp timestamp() {
        return timestamp;
    }
    
    public int compareTo(Timestampable<FSMStateChangedEvent<T, E>> o) {
        //optimization; the sequence is the same for sure; otherwise smth is wrong with AtomicLong
        if(this == o) return 0;
        return this.timestamp.compareTo(o.timestamp());
    }

    public FSMStateChangedEvent<T, E> value() {
        return null;
    }
}
