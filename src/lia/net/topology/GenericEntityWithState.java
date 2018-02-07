/*
 * Created on Mar 21, 2010
 */
package lia.net.topology;

import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 
 * @author ramiro
 */
public class GenericEntityWithState<S extends Enum<S>> extends GenericEntity {

    /**
     * 
     */
    private static final long serialVersionUID = 5602203439671524077L;

    protected final AtomicReference<EnumSet<S>> state;
    
    protected GenericEntityWithState(String name, S initialState) throws TopologyException {
        this(name, UUID.nameUUIDFromBytes(name.getBytes()), initialState);
    }
    
    protected GenericEntityWithState(String name, UUID id, S initialState) throws TopologyException {
        super(name, id);
        this.state = new AtomicReference<EnumSet<S>>(EnumSet.of(initialState));
    }

    public EnumSet<S> getAndSetStates(EnumSet<S> newState) {
        return state.getAndSet(newState);
    }
    
    public EnumSet<S> getStates() {
        return state.get();
    }
    
    public boolean compareAndSetStates(EnumSet<S> expectedState, EnumSet<S> newState) {
        return state.compareAndSet(expectedState, newState);
    }
    
    @Override
    public String toString() {
        return getClass().getName() + "," + id + "," + name + ", state:" + state.get();
    }
}
