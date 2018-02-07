package lia.searchdaemon.comm;

public interface XDRMessageNotifier {
    public void notifyXDRMessage(XDRMessage message, XDRAbstractComm comm);
    public void notifyXDRCommClosed(XDRAbstractComm comm);
}
