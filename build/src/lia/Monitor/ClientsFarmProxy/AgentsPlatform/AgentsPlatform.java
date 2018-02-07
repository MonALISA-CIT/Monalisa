/*
 * $Id: AgentsPlatform.java 7419 2013-10-16 12:56:15Z ramiro $
 */

package lia.Monitor.ClientsFarmProxy.AgentsPlatform;

import java.util.Enumeration;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ClientsFarmProxy.FarmWorker;
import lia.Monitor.monitor.monMessage;
import lia.util.ntp.NTPDate;
import net.jini.core.lookup.ServiceID;

/**
 * @author mickyt
 */
public class AgentsPlatform {

    private static final Logger logger = Logger.getLogger(AgentsPlatform.class.getName());

    public static final int DEFAULT_PRIO = 5; // default

    // message
    // priority.

    public static final int MAX_PRIO = 10;

    private final Map<ServiceID, FarmWorker> farmsSIDs; // farms connections ; key -

    // farmSID as String
    private final ConcurrentHashMap farmsAgents; // active agents and

    // their group

    // groups
    private final ConcurrentHashMap bGroups; // broadcast groups with their

    // agents

    private final PQTreeSet priorHash; // priority queue for

    // messages

    public AgentsPlatform(Map<ServiceID, FarmWorker> farmsSIDs) {

        this.farmsSIDs = farmsSIDs;
        priorHash = new PQTreeSet();
        farmsAgents = new ConcurrentHashMap();
        bGroups = new ConcurrentHashMap();

        (new MsgVerify()).start();

    } // AgentsPlatform

    // returns a list with all agents from the broadcast group "grup"
    // if grup==null, then returns a list with all active agents
    public String getAgentsList(String grup) {
        logger.log(Level.INFO, "\n getAgentsList ===> " + grup + " \n");
        String s = "";

        if (grup == null) { // get all existing agents ...

            for (Enumeration e = farmsAgents.keys(); e.hasMoreElements();) {
                s = s + (String) (e.nextElement()) + ":";
            } // for

        } else { // get agents list from a grup
            Vector v = (Vector) bGroups.get(grup);
            if (v != null) { // if the group exists
                for (int i = 0; i < v.size(); i++) {
                    s = s + (String) (v.elementAt(i)) + ":";
                } // for
            }
        } // if - else

        return s;

    } // getAgentsList

    public int getAgentsNr(String grup) {

        if (grup == null) {
            return farmsAgents.size();
        } else {
            Vector v = (Vector) bGroups.get(grup);
            if (v != null) {
                return v.size();
            }
            return 0;
        } // if - else
    } // getAgentsNr

    // called when a farm is deleted ; frees the hashtables
    public void deleteFarm(ServiceID sid) {
        final String farmSid = sid.toString();
        farmsSIDs.remove(sid);
        synchronized (farmsAgents) {
            for (Enumeration e = farmsAgents.keys(); e.hasMoreElements();) {
                String agentName = (String) e.nextElement();

                if (agentName.indexOf(farmSid) != -1) {

                    String agentGroup = (String) farmsAgents.get(agentName);

                    // remove from bGroups
                    if (agentGroup != null) {
                        Vector v = (Vector) bGroups.get(agentGroup);
                        if (v != null) {
                            v.remove(agentName);
                        } // if
                        if ((v != null) && (v.size() == 0)) {
                            bGroups.remove(agentGroup);
                        } // if
                    } // if
                    farmsAgents.remove(agentName);
                } // if
            } // for
        } // synchronized
        logger.log(Level.INFO, "DeleteFarm ===> " + farmsAgents + " ===> " + bGroups);
    } // deleteFarm

    // verify existence of broadcast group
    public boolean verifyGroupBcast(String groupN) {
        if (bGroups.containsKey(groupN)) {
            return true;
        } // if

        return false;
    } // verifyGroupBcast

    // verify registration of the source agent - the sender;
    // if not registered, register it
    private void verifySource(AgentMessage agentMsg) {

        if (agentMsg == null) {
            return;
        }

        String agentAddrS = agentMsg.agentAddrS;
        String agentGroupS = agentMsg.agentGroupS;

        // if the agentGroupS does not exist, create it
        synchronized (farmsAgents) {
            if (!farmsAgents.containsKey(agentAddrS)) {
                // a new message from an unknown agent, a new one

                if ((agentGroupS != null) && !bGroups.containsKey(agentGroupS)) { // create
                    // a
                    // new
                    // group
                    Vector v = new Vector();
                    v.add(agentAddrS);
                    bGroups.put(agentGroupS, v);

                    logger.log(Level.INFO, "\nAdd bcast group =====> " + agentGroupS + "\n");

                } else {
                    if (bGroups.containsKey(agentGroupS)) {
                        Vector v = (Vector) bGroups.get(agentGroupS);
                        if (!v.contains(agentAddrS)) {
                            v.add(agentAddrS);
                            bGroups.put(agentGroupS, v);
                        } // if
                    } // if

                } // if - else

                // add to farmsAgents
                farmsAgents.put(agentAddrS, agentGroupS);
                logger.log(Level.INFO, "\nAdded " + agentAddrS + " group " + agentGroupS
                        + " to farmsAgents hashtable \n");

            } // if

        } // synchronized

    } // verifySource

    private boolean verifyMsg(AgentMessage agentMsg) {

        if (agentMsg == null) {
            return false;
        }

        verifySource(agentMsg);

        String agentAddrD = agentMsg.agentAddrD;
        String agentGroupD = agentMsg.agentGroupD;

        if (agentAddrD == null) {
            return false;
        } // if

        if (agentGroupD != null) {

            StringTokenizer st = new StringTokenizer(agentAddrD, "@");
            st.nextToken();
            if (st.hasMoreTokens()) { // unicast address

                String sourceFarm = st.nextToken();
                return true; // verifyGroupFarm(agentGroupD, sourceFarm);

            } else { // bcast si mcast

                if (agentAddrD.startsWith("bcast")) { // a
                    // bcast
                    // address

                    StringTokenizer str = new StringTokenizer(agentAddrD, ":");
                    str.nextToken();
                    if (str.hasMoreTokens()) {
                        boolean er = verifyGroupBcast(str.nextToken());

                        return er;
                    } else {
                        // sendErrorMessage(agentMsg);
                        return false;
                    }
                } // if

            } // if - else

        } // if

        return true;

    } // verifyDestinationAddress;

    public void receivedCtrlMessage(AgentMessage am, String cmd) throws Exception {

        if (cmd == null) {
            sendErrorMessage(am);
            return;
        } // if

        if (am == null) {
            return;
        }

        verifySource(am);

        if (cmd.equals("nr")) {
            String group = (String) am.message;
            int nr = getAgentsNr(group);
            AgentMessage amN = new AgentMessage();
            amN.agentAddrS = "proxy";
            amN.timeStamp = Long.valueOf(NTPDate.currentTimeMillis());
            amN.agentAddrD = am.agentAddrS;
            amN.agentGroupD = am.agentGroupS;
            amN.message = "" + nr;
            amN.messageID = am.messageID;
            priorHash.insert(amN, AgentsPlatform.MAX_PRIO);
        } // if

        if (cmd.equals("list")) {
            String group = (String) am.message;
            String list = getAgentsList(group);
            AgentMessage amN = new AgentMessage();
            amN.agentAddrS = "proxy";
            amN.timeStamp = Long.valueOf(NTPDate.currentTimeMillis());
            amN.agentAddrD = am.agentAddrS;
            amN.agentGroupD = am.agentGroupS;
            amN.message = list;
            amN.messageID = am.messageID;

            priorHash.insert(amN, AgentsPlatform.MAX_PRIO);
        } // if

    } // receivedCtrlMessage

    public void receivedMessage(AgentMessage am) throws Exception { // will be called by an external thread

        if (am == null) {
            return;
        }

        // aici tre' sa verific daca mesajul este complet
        if (verifyMsg(am) == false) {
            sendErrorMessage(am);
            return;
        } // if

        if (am.priority != null) {
            // if the message has been set with a specified priority ...
            int prio = am.priority.intValue();
            if ((0 < prio) && (prio <= 10)) {
                priorHash.insert(am, prio);
                // insert the message in the priority queue;
            } else { // if a wrong priority has been set
                priorHash.insert(am, AgentsPlatform.DEFAULT_PRIO);
                // insert the message with the default priority
            } // if - else
        } else { // if the priority has not been set, insert it with the
            // default priority
            priorHash.insert(am, AgentsPlatform.DEFAULT_PRIO);
        } // if - else
    } // receiveMessage

    private AgentMessage extractMessage() throws Exception { // it is private because will be used by
        AgentMessage sm = priorHash.delMax();
        return sm;
    } // extractMessage

    private void sendErrorMessage(AgentMessage am) {
        // if it is write, then verify if the farm exists ( it's alive) ;
        try {
            am.agentAddrD = am.agentAddrS;
            am.agentGroupD = am.agentGroupS;
            FarmWorker fw = farmsSIDs.get(AgentsPlatform.serviceIDFromString(am.agentAddrS.substring(am.agentAddrS
                    .indexOf("@") + 1)));
            monMessage mm = new monMessage(monMessage.ML_AGENT_ERR_TAG, null, am);
            fw.sendMsg(mm);
        } catch (Exception e) {
            e.printStackTrace();
        } // try - catch
    } // sendErrorMessage

    class MsgVerify extends Thread {

        public MsgVerify() {
        } // MsgVerify

        void processMsg(AgentMessage msg) {

            String agentD = null;
            String agentDestA = msg.agentAddrD;
            try {
                if (agentDestA.startsWith("bcast")) {

                    StringTokenizer st = new StringTokenizer(agentDestA, ":");
                    st.nextToken();
                    String group = st.nextToken();
                    if (group != null) {
                        Vector v = (Vector) bGroups.get(group);
                        if (v != null) {
                            for (int i = 0; i < v.size(); i++) {
                                String agent = (String) v.elementAt(i);
                                final String destAddr = agent.substring(agent.indexOf("@") + 1);
                                final ServiceID dServiceID = AgentsPlatform.serviceIDFromString(destAddr);
                                final FarmWorker fw = farmsSIDs.get(dServiceID);

                                if (fw != null) {
                                    msg.agentAddrD = agent;
                                    monMessage mm = new monMessage("agents", null, msg);
                                    try {
                                        fw.sendMsg(mm);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    } // try - catch
                                } // if

                            } // for
                        } // if v!=null
                    } // if
                    return;
                } // bcast msg

                StringTokenizer st = new StringTokenizer(agentDestA, "@");
                String bla = st.nextToken();
                if (st.hasMoreTokens()) {
                    agentD = st.nextToken();
                }

                final FarmWorker fw = farmsSIDs.get(AgentsPlatform.serviceIDFromString(agentD));

                if (fw != null) {

                    monMessage mm = new monMessage("agents", null, msg);
                    try {
                        fw.sendMsg(mm);
                    } catch (Exception e) {
                        // message not sent; put it in cache.
                        e.printStackTrace();
                    } // try - catch

                } else {
                    logger.log(Level.INFO, "Not an active farm. " + msg + " Put message in cache");
                } // if- else
            } catch (Exception e) {
                sendErrorMessage(msg);
                e.printStackTrace();
            }

        } // processMsg

        @Override
        public void run() {

            while (true) {
                try {
                    AgentMessage msg = extractMessage();
                    processMsg(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                } // try - catch
            } // while

        } // run

    } // Thread

    public static final ServiceID serviceIDFromString(final String sID) {
        final UUID uuidHelper = UUID.fromString(sID);
        return new ServiceID(uuidHelper.getMostSignificantBits(), uuidHelper.getLeastSignificantBits());
    }
} // class AgentsPlatform

