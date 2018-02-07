package lia.Monitor.DataCache;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ClientsFarmProxy.AgentsPlatform.AgentMessage;
import lia.Monitor.monitor.AgentI;
import lia.Monitor.monitor.monMessage;

// context for running agents
public class AgentsEngine implements AgentsCommunication {

    private static final Logger logger = Logger.getLogger(AgentsEngine.class.getName());

    private final Map<String, AgentI> agents;

    private final Map<String, AgentThread> agentsThread;

    private final ProxyWorker proxyWorker;

    public AgentsEngine(ProxyWorker proxyWorker) {
        agents = new ConcurrentHashMap<String, AgentI>();
        agentsThread = new ConcurrentHashMap<String, AgentThread>();
        this.proxyWorker = proxyWorker;
    } // AgentsEngine

    // received a message; send it to the coresponding agent for processing
    public void messageReceived(monMessage mm) {

        AgentMessage am = (AgentMessage) mm.result;

        if (am == null) {
            logger.log(Level.WARNING, "AgentsEngine ====> message received null ( Ignoring IT)");
            return;
        }

        String agentN = am.getAgentDName();
        AgentI a = agents.get(agentN);
        if (a != null) {

            if (mm.tag.equals(monMessage.ML_AGENT_ERR_TAG)) {
                a.processErrorMsg(am);
                return;
            } // if error
            if ((am.message != null) && (am.message instanceof byte[])) {
                try {
                    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream((byte[]) am.message));
                    am.message = ois.readObject();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " Got exc while trying to deserialize the message", t);
                }
            }
            a.processMsg(am);
        } else {
            logger.log(Level.WARNING, "AgentsEngine ====> no agent with name " + agentN);
        } // if - else

    } // messageReceived

    public synchronized void addAgent(AgentI agent) {

        if ((agent != null) && (agent.getName() != null)) {
            logger.log(Level.INFO, "\n\n added agent with name : " + agent.getName() + " \n\n");
            agents.put(agent.getName(), agent);
            runAgent(agent);
        } // if

    } // addAgent

    public synchronized void removeAgent(String agentName) {
        agents.remove(agentName);
        AgentThread at = agentsThread.remove(agentName);
        try {
            at.interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        } // try - catch
    } // removeAgent

    @Override
    public void sendMsg(Object msg) {
        monMessage mm = new monMessage(monMessage.ML_AGENT_TAG, null, msg);
        if (logger.isLoggable(Level.FINEST)) {
            if (msg != null) {
                logger.log(Level.FINEST, " [ AgentsEngine ] sending MSG = " + msg.toString());
            } else {
                logger.log(Level.FINEST, " [ AgentsEngine ] Should HAVE sent but MSG == null");
            }
        }
        proxyWorker.rezToOneProxy(mm);
    } // sendMsg

    @Override
    public void sendToAllMsg(Object msg) {
        monMessage mm = new monMessage(monMessage.ML_AGENT_TAG, null, msg);
        if (msg != null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, " sending MSG to all proxies = " + msg.toString());
            }
        } else {
            logger.log(Level.WARNING, " Should HAVE sent but MSG == null");
        } // if - else

        proxyWorker.rezToAllProxies(mm);

    } // sendBcastMsg

    @Override
    public void sendCtrlMsg(Object msg, String ctrl) {
        if (ctrl == null) {
            return;
        }

        if (logger.isLoggable(Level.FINEST)) {
            if (msg != null) {
                logger.log(Level.FINEST, " sending ctrl MSG [" + ctrl + "]  MSG = " + msg.toString());
            } else {
                logger.log(Level.FINEST, " Should HAVE sent ctrl MSG [" + ctrl + "]  but MSG == null");
            }
        }

        monMessage mm = new monMessage("agentCtrl:" + ctrl, null, msg);
        proxyWorker.rezToOneProxy(mm);

    } // sendMsg

    private void runAgent(AgentI agent) {
        if (agent == null) {
            return;
        }

        AgentThread aThread = new AgentThread(agent, this);
        aThread.start();

    } // runAgent

    public void newProxyConns() {
        for (final AgentI a : agents.values()) {
            try {
                a.newProxyConnection();
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ AgentsEngine ] got exception notifying a new proxy connection [ agent "
                        + a + " ]", t);
            } // try - catch
        } // for
    } // newProxyConns

    class AgentThread extends Thread {

        final AgentI agent;

        final AgentsCommunication comm;

        public AgentThread(AgentI agent, AgentsCommunication comm) {
            this.agent = agent;
            this.comm = comm;

        } // AgentThread

        @Override
        public void run() {
            final String key = agent.getName();
            agentsThread.put(key, this);

            try {
                agent.init(comm);
                logger.log(Level.INFO, " [ AgentThread ] =====> communication initiated ");
                agent.doWork();
            } catch (Throwable t) {
                logger.log(Level.WARNING, " AgentThread for agent " + agent + " got Exc ", t);
            } // try - catch

            agentsThread.remove(key);
        } // run
    } // AgentThread

} // class AgentsEngine