package lia.util.timestamp;


public class TimestampableStateValue<T extends Enum<T>, V> extends TimeStampedValue<V>{

    private final T state;
    
    public TimestampableStateValue(T state, V value) {
        this(state, value, new Timestamp());
    }
    
    public TimestampableStateValue(T state, V value, Timestamp timestamp) {
        super(value);
        this.state = state;
    }

    public T state() {
        return state;
    }

}
