/*
 * Created on Mar 24, 2010
 */
package lia.net.topology;

/**
 * 
 * @author ramiro
 */
public class TopologyException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 7858090270904891103L;

    public TopologyException() {
        super();
    }

    public TopologyException(String message) {
        super(message);
    }

    public TopologyException(Throwable cause) {
        super(cause);
    }

    public TopologyException(String message, Throwable cause) {
        super(message, cause);
    }

}
