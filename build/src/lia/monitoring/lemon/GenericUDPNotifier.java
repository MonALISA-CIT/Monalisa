package lia.monitoring.lemon;

public interface GenericUDPNotifier {
    public void notifyData(int len, byte[] data);
}
