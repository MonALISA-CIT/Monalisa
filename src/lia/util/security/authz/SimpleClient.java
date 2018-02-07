package lia.util.security.authz;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.TreeMap;

/**
 * @author adim Mar 14, 2005
 * @deprecated
 */
public class SimpleClient {

    /**
     * @param args
     */
    public static void main(String[] args) {

        Socket sock = null;
        ObjectInputStream ois = null;
        ObjectOutputStream oos = null;
        // Create a socket with a timeout
        try {
            InetAddress addr = InetAddress.getLocalHost();// getByName("localhost");
            int port = 1111;
            SocketAddress sockaddr = new InetSocketAddress(addr, port);

            // Create an unbound socket
            sock = new Socket();

            // This method will block no more than timeoutMs.
            // If the timeout occurs, SocketTimeoutException is thrown.
            int timeoutMs = 2000; // 2 seconds
            sock.connect(sockaddr, timeoutMs);

            oos = new ObjectOutputStream(sock.getOutputStream());
            ois = new ObjectInputStream(sock.getInputStream());
            System.out.println("Request sent");
            AuthZRequest req = new AuthZRequest("/C=RO/ST=Romania/L=Bucharest/O=ML/OU=ML/CN=Griddie", AuthZRequest.ALL_GROUPS);
            oos.writeObject(req);
            oos.flush();

            System.out.println("Request sent...Waiting response for: " + req.subject);
            // TreeMap<String,LinkedList<MLPermission>> response;
            AuthZResponse response = null;
            response = (AuthZResponse) ois.readObject();
            TreeMap<String, Boolean> perm = response.groupAuthorization;
            System.out.println(perm);

        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (SocketTimeoutException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (ois != null)
                try {
                    ois.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            if (oos != null)
                try {
                    oos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            if (sock != null)
                try {
                    sock.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }

    }
}
