package lia.Monitor.JiniClient.ReflRouter.oldPandaCmdSender;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.rmi.RMISecurityManager;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import edu.caltech.hep.kangaroo.PandaProxyInterface;
import edu.caltech.hep.kangaroo.socket.DBSSLClientSocketFactory;
import edu.caltech.hep.panda.router.PeerInfo;

public class PandaProxyCmdSender {
    private static final Logger logger = Logger.getLogger(PandaProxyCmdSender.class.getName());
    public static String CMD_CONNECT = "addUDPPeer";
    public static String CMD_DISCONNECT = "deleteUDPPeer";
    public static String CMD_DISCONNECT_ALL = "removeAllPeers";
    public static String CMD_GET_PEERS = "getPeerList";

    private PandaProxyInterface proxyRef;

    public PandaProxyCmdSender() {
    }

    public boolean sendCommand(String host, String command, String peer) {
        String kangarooHost = AppConfig.getProperty("PandaProxyCmdSender.KANGAROO_HOST", "131.215.112.205");
        int kangarooPort = Integer.valueOf(AppConfig.getProperty("PandaProxyCmdSender.KANGAROO_PORT", "3236"))
                .intValue();
        int tries = 0;
        while (tries < 2) {
            tries++;
            if (proxyRef == null) {
                try {
                    logger.log(Level.INFO, "Doing RMI get registry for kangaroo at " + kangarooHost + ":"
                            + kangarooPort + " ...");
                    Registry registry = LocateRegistry.getRegistry(kangarooHost, kangarooPort,
                            new DBSSLClientSocketFactory());
                    logger.log(Level.INFO, "Doing RMI lookup for kangaroo at " + kangarooHost + ":" + kangarooPort
                            + " ...");
                    proxyRef = (PandaProxyInterface) (registry.lookup("pandaProxy"));
                    logger.log(Level.INFO, "Kangaroo Proxy Found.");
                    //proxy = (PandaProxyInterface) Naming.lookup("//" + kangaroo + ":3236/pandaProxy");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return false;
                }
            }
            logger.log(Level.INFO, "Sending command: [" + host + "]->" + command + "(" + peer + ") via " + kangarooHost
                    + ":" + kangarooPort);
            if (host == null) {
                logger.log(Level.WARNING, "Invalid host " + host);
                return false;
            }
            if ((command == null) || command.equals("")) {
                logger.log(Level.WARNING, "Invalid command " + command + " for host " + host);
                return false;
            }
            if (peer == null) {
                logger.log(Level.WARNING, "Invalid peer " + peer + " for host " + host);
                return false;
            }
            String result = null;
            try {
                if (command.equals(CMD_CONNECT)) {
                    result = proxyRef.addUDPPeer(host, peer);
                } else if (command.equals(CMD_DISCONNECT)) {
                    result = proxyRef.deleteUDPPeer(host, peer);
                } else if (command.equals(CMD_DISCONNECT_ALL)) {
                    result = proxyRef.removeAllPeers(host);
                } else if (command.equals(CMD_GET_PEERS)) {
                    ArrayList list = proxyRef.getPeerList(host);
                    if (list != null) {
                        System.out.println("peerList.size = " + list.size());
                        for (int i = 0; i < list.size(); i++) {
                            PeerInfo pi = (PeerInfo) list.get(i);
                            System.out.println(pi.host_IP);
                        }
                        result = "ok";
                    } else {
                        result = null;
                    }
                } else {
                    logger.log(Level.WARNING, "Unknown command " + command + " for host " + host);
                    return false;
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Failed sending command; Invalidating remote Kangaroo...", ex);
                proxyRef = null;
                continue;
            }
            logger.log(Level.INFO, "Kangaroo response: " + result);
            return true;
        }
        logger.log(Level.INFO, "Giving up, as reached maximum retries number.");
        return false;
    }

    public static void main(String[] args) {
        if (System.getSecurityManager() == null) {
            System.setSecurityManager(new RMISecurityManager());
        }
        BufferedReader in = null;
        String host = null;
        in = new BufferedReader(new InputStreamReader(System.in));
        PandaProxyCmdSender cmdSender = new PandaProxyCmdSender();
        while (true) {
            try {
                System.out.print("Panda Host <ENTER=last; exit exists> = ");
                String newHost = in.readLine();
                if (newHost.equals("exit")) {
                    System.exit(0);
                }
                if (!newHost.equals("")) {
                    host = newHost;
                } else {
                    if (host == null) {
                        continue;
                    } else {
                        System.out.println("Using host " + host);
                    }
                }
                System.out.print("Command = ");
                String cmd = in.readLine();
                String peer = null;
                System.out.print("Peer Host IP = ");
                peer = in.readLine();
                cmdSender.sendCommand(host, cmd, peer);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
