package lia.Monitor.Agents.OpticalPath.v2;

import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Agents.OpticalPath.comm.XDRAuthZSSLTcpServer;
import lia.Monitor.Agents.OpticalPath.comm.XDRGenericComm;
import lia.Monitor.Agents.OpticalPath.comm.XDRMessage;
import lia.Monitor.Agents.OpticalPath.comm.XDRMessageNotifier;
import lia.Monitor.Agents.OpticalPath.comm.XDRSSLTcpServer;
import lia.Monitor.Agents.OpticalPath.comm.XDRTcpServer;
import lia.Monitor.monitor.AppConfig;

public class XDRMessagesHandler implements XDRMessageNotifier {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(XDRMessagesHandler.class.getName());

    private static XDRTcpServer server;
    private static int xdrTCPPort;
    OpticalPathAgent_v2 agent;

    static {
        try {
            xdrTCPPort = Integer.valueOf(
                    AppConfig.getProperty("lia.Monitor.Agents.OpticalPath.MLCopyAgent.xdrTCPPort", "25001")).intValue();
        } catch (Throwable t) {
            xdrTCPPort = 25001;
        }
    }

    public XDRMessagesHandler(OpticalPathAgent_v2 agent) throws Exception {

        this.agent = agent;

        boolean shouldUseSSL = false;
        try {
            String cf = AppConfig.getProperty("lia.Monitor.Agents.OpticalPath.MLCopyAgent.useSSL", null);
            if (cf != null) {
                shouldUseSSL = Boolean.valueOf(cf).booleanValue();
            }
        } catch (Throwable t) {
            shouldUseSSL = false;
        }
        if (shouldUseSSL) {
            boolean shouldUseAuthz = false;
            try {
                String cf = AppConfig.getProperty("lia.Monitor.Agents.OpticalPath.MLCopyAgent.useAuthZ", null);
                if (cf != null) {
                    shouldUseAuthz = Boolean.valueOf(cf).booleanValue();
                }
            } catch (Throwable t) {
                shouldUseAuthz = false;
            }
            if (shouldUseAuthz) {
                server = new XDRAuthZSSLTcpServer(xdrTCPPort, this);
            } else {
                server = new XDRSSLTcpServer(xdrTCPPort, this);
            }
        } else {
            server = new XDRTcpServer(xdrTCPPort, this);
        }

        if (server != null) {
            server.start();
        } else {
            throw new Exception("XDRTCPServer cannot be instantiated");
        }
    }

    @Override
    public void notifyXDRCommClosed(XDRGenericComm comm) {

    }

    /**
     * messages coming from OSDaemon-s
     */
    @Override
    public void notifyXDRMessage(XDRMessage xdrMsg, XDRGenericComm comm) {
        try {
            //The very START time
            long notifyStartTime = System.currentTimeMillis();

            if (!OpticalPathAgent_v2.ping_pong_mode) {
                MLOpticalPathSession mlPathSession = null;
                if (xdrMsg.olID != null) {
                    if (xdrMsg.olID.equals("NOSENSE")) {// a new session must be created
                        final String sessionID = agent.getAddress() + "//::"
                                + OpticalPathAgent_v2.opticalContor.incrementAndGet();
                        mlPathSession = new MLOpticalPathSession(sessionID, agent, comm, notifyStartTime);
                        agent.currentSessions.put(sessionID, mlPathSession);
                    } else {
                        mlPathSession = (MLOpticalPathSession) agent.currentSessions.get(xdrMsg.olID);

                        if (mlPathSession == null) { // it should be inactive ....
                            try {
                                logger.log(Level.WARNING, " Got a XDR MSG bu session alreay expired ... notify ");
                                comm.write(XDRMessage.getErrorMessage("The session already expired ...\n\n"));
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, " Error sending message ", t);
                            }
                            return;
                        }
                    }
                    mlPathSession.notifyXDRMessage(xdrMsg, notifyStartTime);
                } else {
                    logger.log(Level.WARNING, " Got an xdrMsg with NULL olID!");
                    try {
                        comm.write(XDRMessage.getErrorMessage("Got an xdrMsg with NULL olID!\n\n"));
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " Error sending message ", t);
                    }
                    return;
                }
            } else {// only for debug
                xdrMsg.olID = "ping_pong_mode";
                long dT = System.currentTimeMillis() - notifyStartTime;
                xdrMsg.data = " Operation took " + dT + " ms";
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got Exception ex ... sending back to the ml_path client", t);
            XDRMessage msg = XDRMessage.getErrorMessage(t);
            try {
                comm.write(msg);
            } catch (Throwable tsend) {
                logger.log(Level.WARNING, "Got exc senfing message", tsend);
            }
        }
    }

    public int getXDRTCPPort() {
        return xdrTCPPort;
    }
}
