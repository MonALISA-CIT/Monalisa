package lia.Monitor.Agents.SchedAgents;

import java.util.Hashtable;
import java.util.Random;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Agents.AbstractAgent;
import lia.Monitor.ClientsFarmProxy.AgentsPlatform.AgentMessage;
import lia.Monitor.DataCache.AgentsCommunication;
import lia.Monitor.DataCache.Cache;
import lia.Monitor.monitor.AgentInfo;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MonitorClient;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.dbStore;

public class ResourceAgent extends AbstractAgent {

    private static final long serialVersionUID = 1699028120361910780L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(ResourceAgent.class.getName());

    //AgentInfo agentInfo;
    //AgentsCommunication agentComm;
    ResourceManager manager;
    GridPredictor predictor;

    //  our clients
    private Vector clients;
    private Cache cache;

    int msgID = 0;

    boolean hasToRun = true;
    Random rgen;
    double currentLoad = 0.5;

    public ResourceAgent(String agentName, String agentGroup, String farmID) {
        super(agentName, agentGroup, farmID);
        //agentInfo = new AgentInfo(agentName, agentGroup, farmID);

        rgen = new Random();
        manager = new DummyManager();
        predictor = new DummyPredictor(this);
    }

    @Override
    public void doWork() {

        System.out.println("### Resource agent");
        try {
            Thread.sleep(2000);
        } catch (Exception e) {
        }

        // TODO throw exception here
        if (agentComm == null) {
            System.out.println("AgentsComm e null :((");
        }

        /* send an initial message so that the AgentsPlatform registers me */
        AgentMessage respMessage = createMsg(msgID++, SchedAgentMessage.REGISTER, 1, 5, agentInfo.agentAddr,
                agentInfo.agentGroup, null);
        agentComm.sendMsg(respMessage);

        System.out.println("### Resource agent sent register");

        while (hasToRun) {
            try {
                Thread.sleep(2000);
            } catch (Exception e) {
            }
        }

    } // doWork

    @Override
    public void processMsg(Object msg) {
        int i;
        AgentMessage agMessage = (AgentMessage) msg;
        System.out.println("### Resource agent received message " + agMessage);

        String agentS = agMessage.agentAddrS;
        Object msgData = null;

        System.out.println("### Resource agent entering constructor/...");
        //UserRequest req = new UserRequest(msgData);	

        switch ((agMessage.messageType).intValue()) {
        case SchedAgentMessage.REQUEST:
            UserRequest req = (UserRequest) agMessage.message;
            System.out.println("### Received request with ID: " + ((AgentMessage) msg).messageID + " in time:  "
                    + (System.currentTimeMillis() - ((AgentMessage) msg).timeStamp.longValue()) + " from " + agentS
                    + " data: " + req);

            //double load = rgen.nextDouble();
            System.out.println("### ResourceAgent load: " + currentLoad);
            Hashtable param = new Hashtable();
            param.put("request_id", Integer.valueOf(0));
            param.put("time", Integer.valueOf(0));
            param.put("cost", Integer.valueOf(0));
            param.put("cluster_load", Double.valueOf(currentLoad));
            param.put("resource_address", getAddress());
            ClusterOffer myOffer = new ClusterOffer(param);
            //ClusterOffer myOffer = predictor.evaluateRequest(req);
            manager.reserveResources(req);

            AgentMessage respMessage = createMsg(msgID++, SchedAgentMessage.RESPONSE, 1, 5, agentS, null, myOffer);
            agentComm.sendMsg(respMessage);
            break;

        case SchedAgentMessage.SUBMIT:
            UserRequest req2 = (UserRequest) msgData;
            System.out
                    .println("### Resource agent: " + agentInfo.agentName + " received submit request from " + agentS);
            manager.scheduleJob(req2);
            break;

        case SchedAgentMessage.REGISTER:
            System.out.println("### Resource agent registered");
        }

    } // processMsg

    @Override
    public synchronized void processErrorMsg(Object msg) {
        System.out.println("ERROR MESSAGE  ID: " + ((AgentMessage) msg).messageID);
    } // processErrorMsg

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
    } // init

    //  ----------------------- MonitorFilter Functions -----------------------------------------
    /**
         * @see lia.Monitor.monitor.MonitorFilter#initdb(lia.Monitor.monitor.dbStore,
         *         lia.Monitor.monitor.MFarm)
         * we don't make any query for the moment
         */
    @Override
    public void initdb(dbStore datastore, MFarm farm) {
        //TODO
    }

    /**
     * @see lia.Monitor.monitor.MonitorFilter#initCache(lia.Monitor.DataCache.Cache)
     */
    @Override
    public void initCache(Cache cache) {
        this.cache = cache;

    }

    /**
     * @see lia.Monitor.monitor.MonitorFilter#addClient(lia.Monitor.monitor.MonitorClient)
     */
    @Override
    public void addClient(MonitorClient client) {
        this.clients.add(client);
    }

    /**
     * @see lia.Monitor.monitor.MonitorFilter#removeClient(lia.Monitor.monitor.MonitorClient)
     */
    @Override
    public void removeClient(MonitorClient client) {
        this.clients.remove(client);
    }

    /**
     * received a new result from monIDS module
     */
    @Override
    public void addNewResult(Object o) {
        if (o != null) {
            if (o instanceof Vector) {
                Vector v = (Vector) o;
                for (int i = 0; i < v.size(); i++) {
                    addNewResult(v.get(i));
                }
            } else if (o instanceof Result) {
                Result r = (Result) o;
                if ((r.Module != null) && r.Module.equals("monProcLoad")) {
                    System.out.println("### ResourceAgent: received a result from monProcLoad module!" + r);
                    logger.log(Level.INFO, "ResourceAgent: received a result from monProcLoad module!" + r);
                    for (int j = 0; j < r.param.length; j++) {
                        if (r.param_name[j].equals("Load5")) {
                            currentLoad = r.param[j];
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
      * @see lia.Monitor.monitor.MonitorFilter#isAlive()
      */
    @Override
    public boolean isAlive() {
        return hasToRun;
    }

    /**
     * @see lia.Monitor.monitor.MonitorFilter#finishIt()
     */
    @Override
    public void finishIt() {
        hasToRun = false;
    }
}
