package lia.searchdaemon.comm;


public class XDRMessage {
    public static XDRMessage ERROR_MESSAGE = null;
    public static XDRMessage PING_MESSAGE = null;
    public static XDRMessage ECHO_REPLAY_MESSAGE = null;
    
    static {
        ERROR_MESSAGE = new XDRMessage();
        ERROR_MESSAGE.id = "NO_SUCH_ID";
        ERROR_MESSAGE.opCode = -1;
        ERROR_MESSAGE.olID = "NoSuchLink";
        ERROR_MESSAGE.data = "Generic Error ... ";

        PING_MESSAGE = new XDRMessage();
        PING_MESSAGE.id = "NO_SUCH_ID";
        PING_MESSAGE.opCode = -2;
        PING_MESSAGE.olID = "PING";
        PING_MESSAGE.data = "PING";

        ECHO_REPLAY_MESSAGE = new XDRMessage();
        ECHO_REPLAY_MESSAGE.id = "NO_SUCH_ID";
        ECHO_REPLAY_MESSAGE.opCode = -3;
        ECHO_REPLAY_MESSAGE.olID = "ECHO_REPLAY";
        ECHO_REPLAY_MESSAGE.data = "ECHO_REPLAY";
    }
    
    public int xdrMessageSize;
    public String id;
    public int opCode;
    public String olID;
    public String data;
    
    public String toString() {
        return "XDRMessage:\tid = " + id + "\topCode = " + opCode + "\tolID" +olID+"\tdata = " + data;
    }
}
