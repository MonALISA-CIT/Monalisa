package lia.Monitor.Agents.OpticalPath.v2;

public class ProtocolException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = -4947164695883285873L;

    /**
     * Constructs an <code>ProtocolException</code> with no 
     * detail message. 
     */
    public ProtocolException() {
        super();
    }

    /**
     * Constructs an <code>ProtocolException</code> with the 
     * specified detail message. 
     *
     * @param   s   the detail message.
     */
    public ProtocolException(String s) {
        super(s);
    }
    
    public ProtocolException(Throwable t) {
        super(t);
    }

}
