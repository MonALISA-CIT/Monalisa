package lia.Monitor.Agents.IDS;

import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Agents.AbstractAgent;
import lia.Monitor.ClientsFarmProxy.AgentsPlatform.AgentMessage;
import lia.Monitor.DataCache.Cache;
import lia.Monitor.JiniSerFarmMon.GMLEPublisher;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MonitorClient;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.dbStore;
import lia.util.MLProcess;
import lia.util.mail.MailFactory;
import lia.util.ntp.NTPDate;

/**
 * 
 * IDS Filter/Agent
 *  
 */
public class IDSAgent extends AbstractAgent {

    private static final Logger logger = Logger.getLogger(IDSAgent.class.getName());

    private static final long serialVersionUID = 1699028120361910788L;

    private boolean hasToRun;
    private int msgCounter = 0;

    //our peers
    private final Vector peers;

    //our clients
    private final Vector clients;
    private Cache cache;

    //banned IPs
    private final Hashtable bannedIPs;
    private final Hashtable toUnban;

    //banning script
    private final String banIPCmd;// = "~/guard.sh";
    private static final char DENY = 'B';
    private static final char REMOVE = 'P';
    private static final long BAN_UNIT = 60 * 60 * 1000; //1h
    //the white list
    private final Vector whiteList;

    private final Object synch = new Object();
    private boolean attrPublished;
    private boolean isRegistered;

    private static final String[] RCPT = getAddresses(AppConfig.getProperty("lia.Monitor.Agents.IDSAgent.MAIL", null));
    //Time interval to send an email with blockedIPs(in minutes)
    private static final long dtReportMail = Long.valueOf(
            AppConfig.getProperty("lia.Monitor.Agents.IDSAgent.MAIL_INTERVAL", "360").trim()).longValue() * 1000 * 60;
    private long lastMailTime;
    private final Vector mailReport = new Vector();

    //interval to send results to ML (seconds)
    private static final long reportingInterval = Long.valueOf(
            AppConfig.getProperty("lia.Monitor.Agents.IDSAgent.RESULTS_INTERVAL", "20").trim()).longValue() * 1000;
    private long lastReportingTime;
    private int localAlertsCnt = 0;
    private int peersAlertsCnt = 0;

    //monIDS1 nodes
    private static final String LOW_ATTACKS = "LowLevelAttacks";
    private static final String MEDIUM_ATTACKS = "MediumLevelAttacks";
    private static final String HIGH_ATTACKS = "HighLevelAttacks";

    private int periodsTillRegistration = 0;

    /**
     * Init the filter/agent
     * @param agentName
     * @param agentGroup
     * @param farmID
     */
    public IDSAgent(String agentName, String agentGroup, String farmID) {
        super(agentName, agentGroup, farmID);

        this.hasToRun = true;
        this.peers = new Vector();
        this.clients = new Vector();
        this.bannedIPs = new Hashtable();

        this.toUnban = new Hashtable();
        this.whiteList = new Vector();
        this.banIPCmd = getGuardScript();

        //read from ml.properties
        String ips = AppConfig.getProperty("lia.Monitor.Agents.IDSAgent.WhiteList");
        if (ips != null) {
            StringTokenizer tz = new StringTokenizer(ips, ",");
            while (tz.hasMoreTokens()) {
                whiteList.add(tz.nextToken());
            }
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "White list:" + whiteList.toString());
            }
        }
        this.lastReportingTime = this.lastMailTime = NTPDate.currentTimeMillis();
    }

    /**
     *  
     */
    private String getGuardScript() {
        String farmHome;
        StringBuilder gs = new StringBuilder();
        if ((farmHome = System.getProperty("MonaLisa_HOME", null)) == null) {
            logger.log(Level.SEVERE, "Cannot find [MonalisaHOME] property");
            this.finishIt();
            return null;
        }

        gs.append(farmHome).append("/Service/CMD/")
                .append(AppConfig.getProperty("lia.Monitor.Agents.IDSAgent.GuardScript", "guard.sh"));
        boolean useSudo = Boolean.valueOf(AppConfig.getProperty("lia.Monitor.Agents.IDSAgent.SudoGuardScript", "true"))
                .booleanValue();
        if (useSudo) {
            gs.insert(0, "sudo ");
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "guard script used:" + gs.toString());
        }
        return gs.toString();
    }

    //---------------AGENT-----------//
    /**
     * @see lia.Monitor.monitor.AgentI#doWork()
     */
    @Override
    public void doWork() {
        while (hasToRun) {

            try {

                if (!attrPublished) {
                    try {
                        GMLEPublisher.getInstance().publishNow(agentInfo.agentName, agentInfo.agentGroup);
                        attrPublished = true;
                    } catch (Throwable t) {
                        attrPublished = false;
                    }
                }

                try {
                    Thread.sleep(10 * 1000);
                } catch (Exception e) {
                }

                if (agentComm == null) {
                    logger.warning("Cannot get AgentsComm ... Cannot join the IDS agents group");
                } else {
                    //It's time to refresh the registration in proxy?                   
                    if ((periodsTillRegistration--) <= 0) {
                        AgentMessage amB = createMsg((msgCounter++) % Integer.MAX_VALUE, 1, 1, 5, agentInfo.agentAddr,
                                agentInfo.agentGroup, "RegisterMePls");
                        agentComm.sendMsg(amB);
                        periodsTillRegistration = 60; //10minutes
                    }
                } // if - else

                // refresh the iptables rules
                synchronized (synch) {
                    Enumeration ips = bannedIPs.elements();
                    BannedIP ip;
                    while (ips.hasMoreElements()) {
                        ip = (BannedIP) ips.nextElement();
                        if (ip.isExpired()) {
                            //remove the ban                           
                            bannedIPs.remove(ip.getIp());
                            toUnban.put(ip.getIp(), ip);
                        }
                    }
                }

                //update iptables
                if (toUnban.size() > 0) {
                    Enumeration ips = toUnban.elements();
                    while (ips.hasMoreElements()) {
                        BannedIP ip = (BannedIP) ips.nextElement();
                        if (!writeIptablesRule(ip, REMOVE)) {
                            logger.log(Level.WARNING, "Cannot un-ban IP:" + ip.toString());
                        } else if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "IP un-banned:" + ip.toString());
                        }
                    }
                    toUnban.clear();
                }

                //deliver banned ips to ML
                deliverResults2ML();
                //send mail report
                sendMailReport();

            } catch (Throwable t) {
                logger.log(Level.WARNING, " IDSAgent - Got Exception in main loop", t);
            }

        }
    }/* doWork() */

    /**
     * @see lia.Monitor.monitor.AgentI#processMsg(java.lang.Object)     
     * handles the messages received from proxy or another agent
     */
    @Override
    public void processMsg(Object msg) {
        if (msg == null) {
            return;
        }

        if (msg instanceof AgentMessage) {
            AgentMessage am = (AgentMessage) msg;

            String agentS = am.agentAddrS;

            if (agentS.equals("proxy")) {//ctrl message
                StringTokenizer st = new StringTokenizer((String) (((AgentMessage) msg).message), ":");
                while (st.hasMoreTokens()) {
                    String dest = st.nextToken();
                    msgCounter++;
                    if (!dest.equals(getAddress())) {
                        if (!peers.contains(dest)) {
                            peers.add(dest);
                            //peers(dest);
                        }
                    }
                } // while

            } else {//from other agent
                // System.out.println("GOT A AGENT MESSAGE:"+am);
                //check for martian messages
                if ((am.message == null) || !(am.message instanceof IDSAlert)) {
                    logger.log(Level.WARNING, "Recceive an unknown message(not an IDS alert) :" + am.message.toString()
                            + "\nReturning...");
                    return;
                }

                //check for loopback messages
                if (am.agentAddrS.equalsIgnoreCase(super.agentInfo.agentAddr)) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Got a loopback message :" + am.message.toString() + "\nReturning...");
                    }
                    return;
                }

                IDSAlert alert = (IDSAlert) am.message;

                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "Got an IDS-message : " + alert.toString());
                }

                handleAlert(alert);
                peersAlertsCnt++;
            }
        }
    }

    /*not used*/
    public String getPeers() {
        AgentMessage amB = createMsg(msgCounter++, 1, 1, 5, null, null, "MLIDSGroup");
        agentComm.sendCtrlMsg(amB, "list");

        if ((peers == null) || (peers.size() == 0)) {
            return "N/A";
        }

        String retV = "";
        for (int i = 0; i < peers.size(); i++) {
            retV += (String) peers.elementAt(i) + ":";
        }
        return retV;
    }

    //-------------------FILTER--------------------------//
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
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Got a client. I should not. " + client);
        }
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
                //filter - an monIDS1 message for all clusters of type *LevelAttacks
                if ((r.Module != null) && r.Module.equals("monIDS1") && (r.ClusterName != null)
                        && r.ClusterName.endsWith("LevelAttacks")) {
                    logger.log(Level.INFO, "Filter: received an alert result from monIDS1 module!" + r);
                    IDSAlert alert = new IDSAlert(r.NodeName, toIntPriority(r.ClusterName));
                    //broadcast intrusion attempt (srcIP)
                    broadcastAlert(alert);
                    //local blocking of srcIP
                    localAlertsCnt++;
                    handleAlert(alert);
                }
            }
        }
    }

    /**
     * @param clusterName
     * @return
     */
    private int toIntPriority(String strPriority) {
        if (HIGH_ATTACKS.equals(strPriority)) {
            return 1;
        } else if (MEDIUM_ATTACKS.equals(strPriority)) {
            return 2;
        } else {
            /*(LOW_ATTACKS.equals(strPriority)) {*/
            return 3;
            /*        } else {
             return Integer.MAX_VALUE;
             }*/
        }
    }

    private void handleAlert(IDSAlert alert) {
        long crtTime = NTPDate.currentTimeMillis();

        try {
            String ip = alert.getIp();
            //Checks:
            //1- if ip is not in the whiteList
            //2- the alert priority
            if (whiteList.contains(ip) || (alert.getPriority() > alertablePriority())) {
                return;
            }
            //XXX
            BannedIP b = null;
            synchronized (synch) {
                //  check if the source ip is already banned....and refresh the
                // bannedTime
                if (bannedIPs.containsKey(ip)) {
                    b = (BannedIP) bannedIPs.get(ip);
                    b.update(crtTime, b.getInterval() * 2);
                } else {
                    long period = BAN_UNIT / alert.getPriority();
                    b = new BannedIP(ip, crtTime, period);
                    if (writeIptablesRule(b, DENY)) {
                        bannedIPs.put(ip, b);
                    }
                }
                if ((b != null) && getSendReportingMail()) {
                    //save it in order to report it through mail
                    mailReport.add(new BannedIP(ip, b.getTime(), b.getInterval()));
                }

            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Got an exception in <handleAlert>:\n" + t.toString());
            }
        }
    }

    /**
     * @return
     */
    private boolean getSendReportingMail() {

        return ((RCPT != null) && (RCPT.length > 0));
    }

    /**
     * @return
     */
    private int alertablePriority() {
        // TODO - read from ml.properties this value
        return Integer.MAX_VALUE;
    }

    private boolean writeIptablesRule(BannedIP bannedIP, char op) {
        Process proc = null;
        try {
            proc = MLProcess.exec(new String[] { "/bin/sh", "-c", banIPCmd + " " + op + " " + bannedIP.getIp() });
            if (proc.waitFor() != 0) {
                logger.log(Level.WARNING,
                        "cannot ban IP:[" + bannedIP.getIp() + "]." + banIPCmd + "ExitValue:" + proc.exitValue());
                return false;
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, " FAILED to execute cmd = " + banIPCmd, t);
            }
        }

        //log
        if (logger.isLoggable(Level.FINE)) {
            String msg = (op == DENY ? "Blocked" : "Un-blocked");
            logger.log(Level.FINE, msg + " IP:" + bannedIP.toString());
        }

        return true;
    }

    private void broadcastAlert(IDSAlert alert) {

        if (alert == null) {
            return;
        }
        String ip = alert.getIp();
        if (whiteList.contains(ip) || (alert.getPriority() > alertablePriority())) {
            return;
        }

        if (agentComm == null) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "AgentsComm e null :((");
            }
        } else {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Broadcast alert: " + alert.toString());
            }
            AgentMessage amB = createMsg(msgCounter++, 1, 1, 5, "bcast:" + agentInfo.agentGroup, agentInfo.agentGroup,
                    alert);
            agentComm.sendMsg(amB);
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

    private String getTargetIPs() {
        return this.cache.getIPAddress();
    }

    //----local methods----//
    private void deliverResults2ML() {

        long now = NTPDate.currentTimeMillis();
        if ((now - lastReportingTime) <= reportingInterval) {
            return;
        }
        Vector storeResults = new Vector();
        String clusterName = "BlockedIPs";
        double dt = (double) (now - lastReportingTime) / 1000;
        Result rp = new Result();
        rp.ClusterName = clusterName;
        rp.time = now;
        rp.NodeName = "Peers";
        rp.Module = "monIDSAgent";
        rp.addSet("Rate", peersAlertsCnt / dt);
        storeResults.add(rp);

        Result rl = new Result();
        rl.ClusterName = clusterName;
        rl.time = now;
        rl.NodeName = "Local";
        rl.Module = "monIDSAgent";
        rl.addSet("Rate", localAlertsCnt / dt);
        storeResults.add(rl);

        //inform cache
        this.cache.notifyInternalResults(storeResults);

        localAlertsCnt = peersAlertsCnt = 0;
        lastReportingTime = now;

    }

    private void sendMailReport() {
        long now = NTPDate.currentTimeMillis();

        if (!getSendReportingMail() || ((now - lastMailTime) < dtReportMail)) {
            return;
        }

        Vector tmp = new Vector();
        synchronized (mailReport) {
            tmp.addAll(mailReport);
            mailReport.clear();
        }

        try {

            StringBuilder msg = new StringBuilder();

            msg.append("[ ").append(new Date(lastMailTime)).append(" / ").append(new Date(now)).append(" ] ")
                    .append(this.cache.getUnitName()).append(" IDS Report: \n\n");

            Enumeration enum1 = tmp.elements();
            msg.append(tmp.size()).append(" IPs were blocked:\n\n");
            while (enum1.hasMoreElements()) {
                BannedIP blockedIP = (BannedIP) enum1.nextElement();
                msg.append(blockedIP.toString()).append("\n");
            }
            /*System.out.println("Sending mail"+"mlstatus@monalisa.cern.ch\n"+ RCPT+
             "\nMLIDS @ " + this.cache.getUnitName()+"\n" +msg.toString());*/
            MailFactory.getMailSender().sendMessage("mlstatus@monalisa.cern.ch", RCPT,
                    "MLIDS @ " + this.cache.getUnitName(), msg.toString());
        } catch (Throwable t1) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Got an exception while sending mail..\n" + t1.toString());
            }
        } finally {
            tmp = null;
        }
        lastMailTime = now;
    }

    private static String[] getAddresses(String csAddresses) {

        if ((csAddresses == null) || (csAddresses.length() == 0)) {
            return null;
        }
        StringTokenizer st = new StringTokenizer(csAddresses, ",");

        String[] ret = new String[st.countTokens()];
        int i = 0;
        while (st.hasMoreTokens()) {
            ret[i++] = st.nextToken();
        }

        return ret;

    }

}
