import java.net.InetAddress;


public interface GenericUDPNotifier {
    public void notifyData(int len, byte[] data, InetAddress source);
}
