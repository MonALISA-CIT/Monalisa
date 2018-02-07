package lia.Monitor.Agents.SchedAgents;

import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Agents.AbstractAgent;
import lia.Monitor.ClientsFarmProxy.AgentsPlatform.AgentMessage;
import lia.Monitor.DataCache.Cache;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MonitorClient;
import lia.Monitor.monitor.dbStore;
import lia.util.DateFileWatchdog;

public class UserAgent extends AbstractAgent {

    private static final long serialVersionUID = 1699028120361910781L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(UserAgent.class.getName());

    private DateFileWatchdog dfw;
    String resourceAgentGroup = "ResourceAgents";

    //our clients
    private Vector clients;
    private Cache cache;

    public static long requestCheckInterval = 10000;
    public static long offerCheckInterval = 5000;

    //AgentInfo agentInfo;
    //AgentsCommunication agentComm;

    int msgID = 0;
    RSLRequest userRequest;

    Hashtable pendingRequests;
    Hashtable submittedRequests;
    Hashtable receivedOffers;

    Vector checkTimes;
    Vector checkIDs;

    boolean hasToRun = true;
    boolean firstTime = true;

    public UserAgent(String agentName, String agentGroup, String farmID) {
        super(agentName, agentGroup, farmID);
        //agentInfo = new AgentInfo(agentName, agentGroup, farmID);

        pendingRequests = new Hashtable();
        submittedRequests = new Hashtable();
        receivedOffers = new Hashtable();

        checkTimes = new Vector();
        checkIDs = new Vector();
        try {
            FileReader fr = new FileReader("/home/florinpop/req1.rsl");
            userRequest = new RSLRequest(fr);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Could not open RSL file");
            System.out.println("Could not open RSL file");
        }
        //dfw = new DateFileWatchdog(this.confFile, 5*1000);
        //      dfw.addObserver(this);
    }

    @Override
    public void doWork() {
        try {
            Thread.sleep(5);
        } catch (Exception e) {
        }

        try {
            System.out.println("### User agent");
            Thread.sleep(2000);
        } catch (Exception e) {
        }

        //		TODO throw exception here
        if (agentComm == null) {
            System.out.println("AgentsComm e null :((");

        }

        // TODO something with the input file ---> de verificat periodic un fis de 
        // intrare care sa contina descrieri de joburi/linkuri

        while (hasToRun) {
            System.out.println("###########################");
            //userRequest = new UserRequest("ls -l", 1, 2);

            AgentMessage amB = createMsg(msgID++, 1, 1, 5, null, null, new String("ResourceAgents"));
            agentComm.sendCtrlMsg(amB, "list");

            try {
                Thread.sleep(5000);
            } catch (Exception e) {
            }

        } // if - else

    } // doWork

    @Override
    public void processMsg(Object msg) {
        int i;
        AgentMessage agMessage = (AgentMessage) msg;

        String agentS = agMessage.agentAddrS;

        if (agentS.equals("proxy")) {

            pendingRequests.put(Integer.valueOf(userRequest.getID()), userRequest);
            long crtTime = System.currentTimeMillis();
            checkTimes.add(Long.valueOf(crtTime + offerCheckInterval));
            checkIDs.add(Integer.valueOf(userRequest.getID()));
            if (firstTime) {
                (new OfferChecker()).start();
                firstTime = false;
            }
            System.out.println("Mesaj venit din proxy : " + msg);
            StringTokenizer st = new StringTokenizer((String) (agMessage.message), ":");
            i = 0;
            while (st.hasMoreTokens()) {
                String dest = st.nextToken();

                try {
                    byte[] buff = null;
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(baos);
                    oos.writeObject(userRequest);
                    oos.flush();
                    baos.flush();
                    buff = baos.toByteArray();
                    if (buff == null) {
                        System.out.println("### sender buff e null!");
                    }
                    //AgentMessage am = createMsg (msgID++,SchedAgentMessage.REQUEST,
                    //					1, 5, dest, resourceAgentGroup, buff);
                    i++;
                    //System.out.println (am.messageID);
                    AgentMessage am = createMsg(msgID++, SchedAgentMessage.REQUEST, 1, 5, dest, resourceAgentGroup,
                            userRequest);
                    agentComm.sendMsg(am);
                } catch (IOException exc) {
                    logger.log(Level.WARNING, "UserAgent got exception:", exc);
                }
            }

            System.out.println("### found " + i + " agents");

        } else {

            switch ((agMessage.messageType).intValue()) {
            case SchedAgentMessage.RESPONSE:
                if (agMessage.message == null) {
                    System.out.println("###UserAgent primit raspuns null!");
                } else {
                    System.out.println("###UserAgent primit raspuns nenul!");
                }
                ClusterOffer msgData = (ClusterOffer) (agMessage.message);
                System.out.println("### Received response from " + agentS + ": " + msgData.toString());

                Integer reqID = (Integer) msgData.getParamValue("request_id");
                Vector offers = (Vector) receivedOffers.get(reqID);
                if (offers == null) {
                    offers = new Vector();
                }
                offers.add(msgData);
                receivedOffers.put(reqID, offers);
                System.out.println("###receivedResponse size: " + receivedOffers.size());
            }
        }

    } // processMsg

    public void chooseBestOffer(Integer requestID) {
        System.out.println("###chooseBestOffer size: " + receivedOffers.size());
        Vector offers = (Vector) receivedOffers.get(requestID);
        if (offers == null) {
            System.out.println("No offer for the request " + requestID);
            return;
        }

        double minTime = Double.MAX_VALUE;
        ClusterOffer bestOffer = null;
        for (int i = 0; i < offers.size(); i++) {
            ClusterOffer co = (ClusterOffer) offers.get(i);
            Integer t = (Integer) co.getParamValue("time");
            if (t.intValue() < minTime) {
                minTime = t.intValue();
                bestOffer = co;
            }
        }

        String resourceAddress = (String) bestOffer.getParamValue("resource_address");
        System.out.println("###User agent choosing " + resourceAddress + " for " + requestID);
        UserRequest req = (UserRequest) pendingRequests.get(requestID);
        AgentMessage am = createMsg(msgID++, SchedAgentMessage.SUBMIT, 1, 5, resourceAddress, null, req);
        agentComm.sendMsg(am);

        pendingRequests.remove(requestID);
        receivedOffers.remove(requestID);
    }

    public class OfferChecker extends Thread {
        @Override
        public void run() {
            while (true) {
                long crtTime = System.currentTimeMillis();

                long nextCheck = ((Long) checkTimes.firstElement()).longValue();

                System.out.println("### OfferChecker crtTime " + crtTime + " nextCheck " + nextCheck);
                Integer requestID = (Integer) checkIDs.firstElement();
                checkTimes.remove(0);
                checkIDs.remove(0);
                if (nextCheck < crtTime) {
                    System.out.println("Error: negative sleep time for the OfferChecker thread");
                    continue;
                }
                try {
                    Thread.sleep(nextCheck - crtTime);
                } catch (Exception e) {
                }
                System.out.println("###OfferChecker m-am trezit din sleep");
                chooseBestOffer(requestID);
            }
        }
    }

    // ----------------------- MonitorFilter Functions -----------------------------------------
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
        // TODO
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
