package lia.Monitor.JiniClient.ReflRouter;

import java.util.Iterator;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.util.ntp.NTPDate;
import edu.caltech.hep.kangaroo.PandaProxyCmdSender;

/**
 * This is invoked by main class of JReflRouter client from time to time to
 * compute the MST and issue the routing change commands to the reflectors. 
 */
public class ReflRouter extends TimerTask {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(ReflRouter.class.getName());

    /** Don't send CONNECT commands until all pending DISCONNECT commands have been properly executed */
    private static boolean waitDisconnectToFinish = false;

    /** Don't send any commands before the first active reroute, 1.5 minutes by default */
    public final static long firstReRouteDelay = AppConfig.getl("lia.Monitor.JiniClient.ReflRouter.FirstReRouteDelay",
            90) * 1000; // seconds
    /** Time when this reflRouter was started */
    public static long appStartTime = NTPDate.currentTimeMillis();
    // this is used to speed up the hasJustStarted() check once the ReflRouter is running for a while
    private static boolean isLongRunning = false;

    public MST mst; // minimum spannig tree class
    private PandaProxyCmdSender panda; // use this to send commands to the reflectors

    private int count; // how many times the MST was computed	
    private boolean firstTime; // first time when MST alg. is invoked
    private final Vector dlinks; // vector with DLinks built from currently selected tunnels
    private final Vector deadTunnels; // list of tunnels that have to be removed at the next commandSend

    /**
     * Constructor for the reflector router
     * @param mst minimum spanning tree computing class
     */
    public ReflRouter(MST mst) {
        this.mst = mst;
        dlinks = new Vector();
        deadTunnels = new Vector();
        firstTime = true;
        //logger.setLevel(Level.FINEST);
        if (!mst.simulation) {
            panda = new PandaProxyCmdSender();
        }
    }

    /**
     * In case of losing the proxy, we will rediscover all the nodes. 
     * Mark the ReflRouter as when it has just restarted.
     */
    public static void resetStartTime() {
        appStartTime = NTPDate.currentTimeMillis();
        isLongRunning = false;
    }

    /** Returns true if ReflRouter was just started */
    public static boolean hasJustStarted() {
        if (isLongRunning) {
            return false;
        }
        isLongRunning = (NTPDate.currentTimeMillis() - appStartTime) > firstReRouteDelay;
        return !isLongRunning;
    }

    /**
     * Check if this ReflRouter should be available to the world:
     * - if it's enabled
     * - if it contains nodes and has a non null tree
     * - if it's not just started 
     */
    public boolean isAvailable() {
        boolean enabled = AppConfig.getb("lia.Monitor.JiniClient.ReflRouter.Enabled", false);
        boolean available = enabled;
        available &= mst.nodes.size() > 0;
        available &= mst.nextTree.size() > 0;
        available &= !hasJustStarted();
        Level logLevel = available ? Level.FINE : Level.INFO;
        if (logger.isLoggable(logLevel)) {
            StringBuilder sb = new StringBuilder("ReflRouter status:");
            sb.append(" available=").append(available);
            sb.append(" enabled=").append(enabled);
            sb.append(" nodes=").append(mst.nodes.size());
            sb.append(" mstSize=").append(mst.nextTree.size());
            sb.append(" justStarted=").append(hasJustStarted());
            logger.log(logLevel, sb.toString());
        }
        ReflRouterJiniService.getInstance().setAvailability(available);
        return available;
    }

    /**
     * triggered when rerouteTimer from lia.Monitor.JiniClient.ReflRouter.Main 
     * class expires
     */
    @Override
    synchronized public void run() {
        try {
            logger.fine("ReflRouter: --> run()");
            if (firstTime) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "ReflRouter: starting with current Reflectors' peers configuration");
                }
                mst.initOldTree();
                firstTime = false;
            }
            count++;
            mst.computeST();
            boolean available = isAvailable();
            if (shouldSendCommands()) {
                mst.commitST();
                rebuildDLinks(mst.nextTree);
                // send commands to the reflectors
                if (!mst.simulation) {
                    if (available) {
                        if (ReflRouterJiniService.getInstance().isMasterMode()) {
                            waitDisconnectToFinish = AppConfig.getb(
                                    "lia.Monitor.JiniClient.ReflRouter.waitDisconnectToFinish", false);
                            sendDisconnectCmds();
                            if ((!waitDisconnectToFinish) || (waitDisconnectToFinish && (mst.toDeactivate.size() == 0))) {
                                sendConnectCmds();
                            } else {
                                logger.info("Not yet sending CONNECTs since there are still unfinished DISCONNECTs!");
                            }
                        } else {
                            logger.info("Not sending commands since I'm not master");
                        }
                    } else {
                        // not available, message should have been provided
                    }
                } else {
                    logger.info("Not sending commands since I am in simulation mode.");
                }
            }
            logger.fine("ReflRouter: --> ReRouting process finished ...");
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error executing", t);
        }
    }

    /**
     * Mark for removal tunnels that are dead, i.e. tunnels 
     * connecting a node that was removed. 
     * @param tunnels
     */
    public void removeDeadTunnels(Vector tunnels) {
        deadTunnels.addAll(tunnels);
    }

    /**
     * Check if the commands computed by the MST algorithm should be sent to the
     * corresponding reflectors
     * @return true/false
     */
    public boolean shouldSendCommands() {
        boolean critical = false;
        synchronized (deadTunnels) {
            if (deadTunnels.size() > 0) {
                mst.toDeactivate.addAll(deadTunnels);
                StringBuilder sb = new StringBuilder("We have to remove dead IPtunnels:");
                for (Iterator tit = deadTunnels.iterator(); tit.hasNext();) {
                    sb.append("\n\t").append(tit.next().toString());
                }
                logger.log(Level.INFO, sb.toString());
                deadTunnels.clear();
                critical = true;
            }
        }

        for (int i = 0; (!critical) && (i < mst.toDeactivate.size()); i++) {
            IPTunnel tun = (IPTunnel) mst.toDeactivate.get(i);
            int nextStatus = tun.getNextStatus();
            if ((nextStatus == IPTunnel.MUST_ACTIVATE) || (nextStatus == IPTunnel.MUST_DEACTIVATE)) {
                critical = true;
            }
        }
        for (int i = 0; (!critical) && (i < mst.toActivate.size()); i++) {
            IPTunnel tun = (IPTunnel) mst.toActivate.get(i);
            int nextStatus = tun.getNextStatus();
            if ((nextStatus == IPTunnel.MUST_ACTIVATE) || (nextStatus == IPTunnel.MUST_DEACTIVATE)) {
                critical = true;
            }
        }
        // decide if we should send this commands to the reflectors
        if ((mst.toDeactivate.size() != 0) || (mst.toActivate.size() != 0)) {
            if (critical) {
                logger.info("Commands SHOULD BE SENT! (critical commands)");
                return true;
            }
            if (mst.toDeactivate.size() != mst.toActivate.size()) {
                logger.info("Commands SHOULD BE SENT to the Reflectors! (new/old tunnels added/deleted)");
                return true;
            }
            try {
                StringBuilder sb = new StringBuilder();
                if (mst.STisBetter(sb)) {
                    sb.append("\nCommands MAY BE SENT! (ST optimization commands)");
                    logger.info(sb.toString());
                    return true;
                }
                sb.append("\nCommands may be IGNORED! (non-semnificative ST optimization commands)");
                logger.info(sb.toString());
                return false;
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error deciding if the new ST is better", t);
                return false;
            }
        }
        logger.info("ST stable, NO COMMANDS available.");
        return false;
    }

    /**
     * Send all needed "disconnect" commands to reflectors
     * @return true/false as the operation succeded or not
     */
    boolean sendDisconnectCmds() {
        boolean success = true;
        for (int i = 0; i < mst.toDeactivate.size(); i++) {
            success &= sendDisconnect((IPTunnel) mst.toDeactivate.get(i));
        }
        return success;
    }

    /**
     * Send all needed "connect" commands to reflectors
     * @return true/false as the operation succeded or not
     */
    boolean sendConnectCmds() {
        boolean success = true;
        for (int i = 0; i < mst.toActivate.size(); i++) {
            success &= sendConnect((IPTunnel) mst.toActivate.get(i));
        }
        return success;
    }

    /**
     * Send "disconnect" commands to reflectors from both ends of a tunnel
     * @return true/false as the operation succeded or not
     */
    boolean sendDisconnect(IPTunnel tun) {
        logger.info("DISCONN: " + tun);
        tun.setLastCommand("Disconnect");
        panda.sendCommand(tun.from.ipad, PandaProxyCmdSender.CMD_DISCONNECT, tun.to.ipad);
        return true;
    }

    /**
     * Send "connect" commands to reflectors from both ends of a tunnel
     * @return true/false as the operation succeded or not
     */
    boolean sendConnect(IPTunnel tun) {
        logger.info("CONNECT: " + tun);
        tun.setLastCommand("Connect");
        panda.sendCommand(tun.from.ipad, PandaProxyCmdSender.CMD_CONNECT, tun.to.ipad);
        return true;
    }

    /**
     * rebuild the vector containing DLinks for the GUI client from the tunnels
     * currently selected in the MST
     * @param tunnels vector with tunnels
     */
    private void rebuildDLinks(Vector tunnels) {
        synchronized (dlinks) {
            dlinks.clear();
            for (int i = 0; i < tunnels.size(); i++) {
                IPTunnel tun = (IPTunnel) tunnels.get(i);
                dlinks.add(tun.toDLink());
            }
        }
    }

    /**
     * Retunrs a vector with all tunnels currently in the MST. This is invoked
     * by the GUI client
     * @return a vector with DLinks made from the currently selected Tunnels
     */
    public Vector getMST() {
        synchronized (dlinks) {
            return dlinks;
        }
    }

}
