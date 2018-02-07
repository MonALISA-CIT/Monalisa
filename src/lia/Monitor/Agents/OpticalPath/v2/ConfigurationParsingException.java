package lia.Monitor.Agents.OpticalPath.v2;

/**
 * This Exception should be thrown whenever a parsing error 
 * is detected in the local configuration file ( or remote :) )... 
 */
public class ConfigurationParsingException extends Exception {

    private static final long serialVersionUID = 1660712680823646346L;
    
    /**
     * Constructs an <code>ConfigurationParsingException</code> with no 
     * detail message. 
     */
    public ConfigurationParsingException() {
        super();
    }

    /**
     * Constructs an <code>ConfigurationParsingException</code> with the 
     * specified detail message. 
     *
     * @param   s   the detail message.
     */
    public ConfigurationParsingException(String s) {
        super(s);
    }
    
    public ConfigurationParsingException(Throwable t) {
        super(t);
    }
}
