package lia.Monitor.Agents.OpticalPath.comm;

import java.io.PrintWriter;
import java.io.StringWriter;


public class XDRMessage {
    
    public int xdrMessageSize;
    public String id;
    public int opCode;
    public String olID;
    public String data;
    
    public static final XDRMessage getErrorMessage(String cause) {
        XDRMessage retMsg = new XDRMessage();
        retMsg.id = "NoSuchLink";
        retMsg.opCode = -1;
        retMsg.olID = "NoSuchLink";
        retMsg.data = cause;
        return retMsg;
    }

    public static final XDRMessage getErrorMessage(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return getErrorMessage(sw.getBuffer().toString());
    }
    
    public String toString() {
        return "XDRMessage:\tid = " + id + "\topCode = " + opCode + "\tolID" +olID+"\tdata = " + data;
    }
}
