package lia.util.fdt.xdr;


public interface XDRMessageNotifier {
    public void notifyXDRMessage(XDRMessage message, XDRGenericComm comm);
    public void notifyXDRCommClosed(XDRGenericComm comm);
}
