package lia.Monitor.monitor;

public interface tcpConnNotifier {
    public void notifyMessage(Object o);
    public void notifyConnectionClosed();
}
