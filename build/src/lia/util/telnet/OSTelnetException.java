package lia.util.telnet;

/**
 * Wrapper class for exceptions thrown by this package 
 */
public class OSTelnetException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = -963662108082616551L;
    
    private int code;
    private String remoteResponse;
    
    public static final int     GENERIC_EXCEPTION               =   0;
    public static final int     NULL_AUTH_PARAMS                =   1;
    public static final int     AUTH_FAILED                     =   2;
    public static final int     NULL_REMOTE_RESPONSE            =   3;
    public static final int     INVALID_TL1_RESPONSE_CODE       =   4;
    public static final int     NULL_CMD_PARAMS                 =   5;
    public static final int     NOT_CONNECTED                   =   6;
    public static final int     CANNOT_CONNECT_SOONER           =   7;
    
    public OSTelnetException(String message, int code) {
        this(message, code, " [ OSTelnetException ] No remote response");
    }

    public OSTelnetException(Throwable t) {
        super(t);
        this.code = GENERIC_EXCEPTION;
        this.remoteResponse = " [ OSTelnetException ] No remote response";
    }
    
    public OSTelnetException(String message, int code, String remoteResponse) {
        super(message);
        this.code = code;
        this.remoteResponse = remoteResponse;
    }

    public int getCode() {
        return code;
    }

    public String getRemoteResponse() {
        return remoteResponse;
    }
}
