package lia.Monitor.Agents;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.Enumeration;
import java.util.UUID;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ClientsFarmProxy.AgentsPlatform.AgentMessage;
import lia.Monitor.DataCache.AgentsCommunication;
import lia.Monitor.DataCache.Cache;
import lia.Monitor.monitor.AgentI;
import lia.Monitor.monitor.AgentInfo;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MonitorClient;
import lia.Monitor.monitor.MonitorFilter;
import lia.Monitor.monitor.dbStore;
import lia.util.ntp.NTPDate;

/**
 * 
 * Helper class that is much easiear to extend...
 * 
 */
public abstract class AbstractAgent implements AgentI, MonitorFilter {

    /**
     * 
     */
    private static final long serialVersionUID = -8720425893944579812L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(AbstractAgent.class.getName());

    protected AgentInfo agentInfo;
    protected AgentsCommunication agentComm;
    protected Cache cache;
    protected volatile MFarm farm;
    protected Vector clients;
    protected boolean hasToRun;
    protected AgentMessage regMsg = null;

    protected final UUID agentID;
    protected final UUID serviceID;

    @Override
    public void confChanged() {
    }

    public AbstractAgent(String agentName, String agentGroup, String farmID) {
        agentInfo = new AgentInfo(agentName, agentGroup, farmID);
        this.agentID = UUID.randomUUID();
        this.serviceID = UUID.fromString(farmID);

        if ((agentGroup != null) && (agentName != null)) {
            regMsg = createMsg(0, 1, 1, 5, agentName + " :- " + agentID + "@" + farmID, agentGroup, "reg");
        } // if
    }

    protected void informClients(Vector v) {
        if ((v == null) || (v.size() == 0)) {
            return;
        }
        String fName = getName();
        if ((fName == null) || (fName.length() == 0)) {
            return;
        }
        for (Enumeration e = clients.elements(); e.hasMoreElements();) {
            MonitorClient mc = (MonitorClient) e.nextElement();
            try {
                mc.notifyResult(v, fName);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

    }

    private AgentMessage createIntMsg(int messageID, int messageType, int messageTypeAck, int priority,
            String agentAddrD, String agentGroupD, Object message, boolean serialize) {

        AgentMessage am = new AgentMessage();

        // set source address
        am.agentAddrS = agentInfo.agentAddr;
        am.agentGroupS = agentInfo.agentGroup;

        // set destination addr.
        am.agentAddrD = agentAddrD;
        am.agentGroupD = agentGroupD;

        //set types priority
        am.messageID = new Integer(messageID);
        am.messageType = Integer.valueOf(messageType);
        am.messageTypeAck = Integer.valueOf(messageTypeAck);
        if ((0 < priority) && (priority <= 10)) {
            am.priority = Integer.valueOf(priority);
        } else { // wrong priority
            am.priority = Integer.valueOf(5);
        } // if - else

        if (serialize) {
            //set message
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(message);
                oos.flush();
                am.message = baos.toByteArray();
            } catch (Throwable t) {
                am.message = null;
            }
        } else {
            am.message = message;
        }

        //set timeStamp
        am.timeStamp = Long.valueOf(NTPDate.currentTimeMillis());

        return am;
    }

    protected void sendAgentMessage(AgentMessage am) {
        try {
            if (agentComm == null) {
                logger.log(Level.WARNING, "Cannot sent message! AgentsComm == null");
            } else {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "\n\nSending MSG: " + am.toString() + "\n\n");
                }
                agentComm.sendMsg(am);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got exception trying to send message", t);
        }
    }

    //mda ... trebuia sa ma gasndesc dinainte sa pun optiunea asta 
    //sa serializeze sau nu ... se pare ca apar exc in proxy de la msg de Ctrl!
    public AgentMessage createMsg(int messageID, int messageType, int messageTypeAck, int priority, String agentAddrD,
            String agentGroupD, Object message) {
        return createIntMsg(messageID, messageType, messageTypeAck, priority, agentAddrD, agentGroupD, message, true);
    } // basicMsg

    public AgentMessage createCtrlMsg(int messageID, int messageType, int messageTypeAck, int priority,
            String agentAddrD, String agentGroupD, Object message) {
        return createIntMsg(messageID, messageType, messageTypeAck, priority, agentAddrD, agentGroupD, message, false);
    }

    @Override
    public String getAddress() {
        return agentInfo.agentAddr;
    } // getAddress

    @Override
    public AgentInfo getAgentInfo() {
        return agentInfo;
    } // getAgentInfo

    @Override
    public String getGroup() {
        return agentInfo.agentGroup;
    } // getGroup

    @Override
    public String getName() {
        return agentInfo.agentName;
    } // getName

    @Override
    public void init(AgentsCommunication comm) {
        this.agentComm = comm;
        try {
            newProxyConnection();
        } catch (Throwable t) {
        } // try - catch
    } // init

    @Override
    public void processErrorMsg(Object msg) {
        System.out.println("ERROR MESSAGE  ID: " + ((AgentMessage) msg).messageID);
    } // processErrorMsg

    /* (non-Javadoc)
     * @see lia.Monitor.monitor.MonitorFilter#isAlive()
     */
    @Override
    public boolean isAlive() {
        return hasToRun;
    }

    /* (non-Javadoc)
     * @see lia.Monitor.monitor.MonitorFilter#finishIt()
     */
    @Override
    public void finishIt() {
        hasToRun = false;
    }

    /* (non-Javadoc)
     * @see lia.Monitor.monitor.MonitorFilter#initdb(lia.Monitor.monitor.dbStore, lia.Monitor.monitor.MFarm)
     */
    @Override
    public void initdb(dbStore datastore, MFarm farm) {
        // TODO Auto-generated method stub
        this.farm = farm;
    }

    /* (non-Javadoc)
     * @see lia.Monitor.monitor.MonitorFilter#initCache(lia.Monitor.DataCache.Cache)
     */
    @Override
    public void initCache(Cache cache) {
        // TODO Auto-generated method stub
        this.cache = cache;
    }

    /**
     * Older ... kept for compatibility
     * @see lia.Monitor.monitor.MonitorFilter#addClient(lia.Monitor.monitor.MonitorClient)
     */
    @Override
    public void addClient(MonitorClient client) {
        // TODO Auto-generated method stub
        clients.add(client);
    }

    /** 
     * Older ... kept for compatibility
     * @see lia.Monitor.monitor.MonitorFilter#removeClient(lia.Monitor.monitor.MonitorClient)
     */
    @Override
    public void removeClient(MonitorClient client) {
        // TODO Auto-generated method stub
        clients.remove(client);
    }

    @Override
    public void newProxyConnection() {
        if (regMsg != null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "\n\n ====> SENDING REGISTRATION MESSAGE !!!!! ");
            }
            agentComm.sendToAllMsg(regMsg);
        }
    } // newProxyConnection

    @Override
    public abstract void doWork();

    @Override
    public abstract void processMsg(Object msg);

}
