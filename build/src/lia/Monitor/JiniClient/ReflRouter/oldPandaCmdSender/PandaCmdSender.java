package lia.Monitor.JiniClient.ReflRouter.oldPandaCmdSender;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.rmi.RMISecurityManager;
import java.rmi.registry.LocateRegistry;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.net.TimeoutClientSocketFactory;
import edu.caltech.hep.panda.rmi.RemoteMonitorInterface;
import edu.caltech.hep.panda.router.PeerInfo;

public class PandaCmdSender {
    private static final Logger logger = Logger.getLogger(PandaCmdSender.class.getName());
    public static String CMD_CONNECT = "addUDPPeer";
    public static String CMD_DISCONNECT = "deleteUDPPeer";
    public static String CMD_DISCONNECT_ALL = "removeAllPeers";
    public static String CMD_GET_PEERS = "getPeerList";

    private final Hashtable pandaRefs;

    public PandaCmdSender() {
        pandaRefs = new Hashtable();
    }

    public boolean sendCommand(String host, String command, String peer) {
        int tries = 0;
        while (tries < 2) {
            tries++;
            RemoteMonitorInterface panda = (RemoteMonitorInterface) pandaRefs.get(host);
            if (panda == null) {
                try {
                    logger.log(Level.INFO, "Doing RMI lookup for panda at " + host + " ...");
                    panda = (RemoteMonitorInterface) LocateRegistry.getRegistry(host, 2091,
                            new TimeoutClientSocketFactory()).lookup("pandaPresence");
                    //panda = (RemoteMonitorInterface) Naming.lookup("//" + host + ":2091/pandaPresence");
                    pandaRefs.put(host, panda);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return false;
                }
            }
            logger.log(Level.INFO, "Sending command: [" + host + "]->" + command + "(" + peer + ")");
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
                    result = panda.addUDPPeer(peer);
                } else if (command.equals(CMD_DISCONNECT)) {
                    result = panda.deleteUDPPeer(peer);
                } else if (command.equals(CMD_DISCONNECT_ALL)) {
                    result = panda.removeAllPeers();
                } else if (command.equals(CMD_GET_PEERS)) {
                    ArrayList list = panda.getPeerList();
                    System.out.println("peerList.size = " + list.size());
                    for (int i = 0; i < list.size(); i++) {
                        PeerInfo pi = (PeerInfo) list.get(i);
                        System.out.println(pi.host_IP);
                    }
                    result = "ok";
                } else {
                    logger.log(Level.WARNING, "Unknown command " + command + " for host " + host);
                    return false;
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Failed sending command; Invalidating remote panda reference...", ex);
                pandaRefs.remove(host);
                continue;
            }
            logger.log(Level.INFO, "Panda response: " + result);
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
        PandaCmdSender cmdSender = new PandaCmdSender();
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
