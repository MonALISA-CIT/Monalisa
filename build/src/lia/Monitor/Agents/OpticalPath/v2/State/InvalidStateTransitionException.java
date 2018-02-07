package lia.Monitor.Agents.OpticalPath.v2.State;


public class InvalidStateTransitionException extends Exception {
    
    private static final long serialVersionUID = -791344392332042179L;

    /**
     * Constructs an <code>InvalidStateTransitionException</code> with no 
     * detail message. 
     */
    public InvalidStateTransitionException() {
        super();
    }

    /**
     * Constructs an <code>InvalidStateTransitionException</code> with the 
     * specified detail message. 
     *
     * @param   s   the detail message.
     */
    public InvalidStateTransitionException(String s) {
        super(s);
    }
    
    public InvalidStateTransitionException(Throwable t) {
        super(t);
    }

}
