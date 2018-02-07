package lia.Monitor.Agents.OpticalPath;

import java.io.StringReader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Agents.OpticalPath.Lease.ExpiredLeaseWatcher;
import lia.Monitor.Agents.OpticalPath.Lease.Lease;
import lia.Monitor.Agents.OpticalPath.Lease.LeaseEvent;
import lia.Monitor.Agents.OpticalPath.Lease.LeaseEventListener;
import lia.Monitor.Agents.OpticalPath.Lease.LeaseRenewalManager;
import lia.Monitor.Agents.OpticalPath.comm.XDRGenericComm;
import lia.Monitor.Agents.OpticalPath.comm.XDRMessage;
import lia.Monitor.ClientsFarmProxy.AgentsPlatform.AgentMessage;
import lia.util.telnet.OSTelnet;
import lia.util.telnet.OSTelnetFactory;

/**
 * Wrapper class for any ml_path request It should be alive as long as the path is established ( or change the conf ...
 * I'll see )
 */
public final class MLPathSession implements LeaseEventListener {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(MLPathSession.class.getName());

    private class ReceivedCmd {

        String idl;

        String agentAddr;

        String sPort;

        String dPort;

        //        String pDconn;

        boolean isFDX;

        private ReceivedCmd(String idl, String agentAddr, String sPort, String dPort, boolean isFDX) {
            this.idl = idl;
            this.agentAddr = agentAddr;
            this.sPort = sPort;
            this.dPort = dPort;
            this.isFDX = isFDX;
        }
    }

    private class XDRMsgToken {

        XDRMessage xdrMsg;

        long sTime;

        XDRMsgToken(XDRMessage xdrMsg, long sTime) {
            this.xdrMsg = xdrMsg;
            this.sTime = sTime;
        }
    }

    /**
     * It should be only one instance of this class per every session This task is suppose to process all the messages
     * which come from OSDaemon! ( or Not ? )
     */
    private class XDRMsgSessionTask implements Runnable {

        private final LinkedList ll;

        private final AtomicBoolean running;

        XDRMsgSessionTask() {
            ll = new LinkedList();
            running = new AtomicBoolean(false);
        }

        private void processMsg(XDRMessage xdrMsg, long sTime) throws Exception {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " notifyXDRMessage :- " + xdrMsg.toString());
            }

            // JUST CHECK THAT WE ARE STILL ALIVE ....
            if (alreadyStopped.get()) {
                sendBackXDRMsg(XDRMessage.getErrorMessage("The session already expired ...."));
            }

            switch (xdrMsg.opCode) {
            case 0: {
                xdrMsg.data = theAgent.getPeers();
                break;
            }
            case 1: {
                String cmd = xdrMsg.data;
                if ((cmd == null) || (cmd.length() == 0)) {
                    xdrMsg.data = "Could not understand request ... [ MCONN | DCONN ]:-source - destination:-isFDX";
                    break;
                }

                // MCONN:-srcIP - dstIP:-fdx:-verbose
                String[] cmdTokens = cmd.split(":-");

                if (cmdTokens.length != 4) {
                    xdrMsg.data = "Could not understand request ... [ MCONN | DCONN ]:-source - destination:-isFDX";
                    break;
                }

                boolean makeConn = cmdTokens[0].equals("MCONN");

                if (!makeConn && !cmdTokens[0].equals("DCONN")) {
                    xdrMsg.data = "Could not understand request ... [ MCONN | DCONN ]:-source - destination:-isFDX";
                    break;
                }

                String[] sd = cmdTokens[1].split(" - ");

                if (sd.length != 2) {
                    xdrMsg.data = "Could not understand request ... [ MCONN | DCONN ]:-source - destination:-isFDX";
                    break;
                }

                boolean retv = false;

                if (makeConn) {// Make conn
                    opr = new OpticalPathRequest(sessionID, cmdTokens[2].equals("1"), cmdTokens[3].equals("1"));
                    opr.source = sd[0];
                    opr.destination = sd[1];
                    retv = theAgent.makeConnection(opr);
                } else {// Delete conn
                    retv = theAgent.deleteOLConns(opr);
                    stopIt();
                }

                if (retv) {
                    xdrMsg.olID = opr.id.toString();
                    if (makeConn) {
                        xdrMsg.data = opr.readableOpticalPath;
                    } else {
                        xdrMsg.data = "Optical Path Released ... \n";
                    }
                } else {
                    xdrMsg.data = opr.status.toString();
                    xdrMsg.olID = "NoSuchLink";
                    stopIt();
                }
                long dT = System.currentTimeMillis() - sTime;
                xdrMsg.data += "\n Operation took " + dT + " ms";

                sendBackXDRMsg(xdrMsg);

                // Just do the lease
                if (retv && makeConn) {
                    registerMasterLeases();
                }

                logger.log(Level.INFO, "\n\n notifyXDRMessage :- SENDING BACK :- " + xdrMsg.toString());
                break;
            }
            case 2: {
                xdrMsg.data = "N/A";// sendPeersCMD(xdrMsg.data);
                break;
            }
            }

            logger.log(Level.INFO, "\n\n Session Age " + (System.currentTimeMillis() - sessionStartTime));
        }

        @Override
        public void run() {
            String cName = Thread.currentThread().getName();
            try {
                assert running.get();

                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " XDRMsgSessionTask enters run ... ");
                }
                Thread.currentThread().setName(cName + " XDRMsgSessionTask for " + sessionID + " - runnig");

                while (running.get()) {
                    try {
                        XDRMsgToken mt;
                        synchronized (this.ll) {
                            assert ll.size() > 0;
                            mt = (XDRMsgToken) ll.removeFirst();
                        }
                        processMsg(mt.xdrMsg, mt.sTime);
                    } catch (Throwable t) {
                        // TODO - Should I notify back ?Should the session be discarded ?
                        logger.log(Level.WARNING, " //TODO - FixMe! XDRMsgSessionTask [ " + sessionID + " ] got Exc", t);
                    } finally {
                        synchronized (ll) {
                            running.set(ll.size() > 0);
                        }
                    }
                }// while
            } catch (Throwable t) {
                logger.log(Level.WARNING, " //TODO - FixMe TAKE SOME ACTION! XDRMsgSessionTask [ " + sessionID
                        + " ] got Exc", t);
            } finally {
                Thread.currentThread().setName(cName);
            }

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ XDRMsgSessionTask ] exits main loop ");
            }
        }// run
    }

    /**
     * This task is resposible for processing (almost) all remote messages coming from the other agents
     */
    private class AgentMsgSessionTask implements Runnable {

        private final AgentMessage am;

        RemoteAgentMsgWrapper[] raMsgs;

        // TODO - extra checks !!
        OSTelnet ost = null;

        long sTime;

        AgentMsgSessionTask(AgentMessage am, RemoteAgentMsgWrapper raMsg, long sTime) throws Exception {
            this(am, new RemoteAgentMsgWrapper[] { raMsg }, sTime);
            ost = OSTelnetFactory.getControlInstance(theAgent.cfg.osi.type.shortValue());
        }

        AgentMsgSessionTask(AgentMessage am, RemoteAgentMsgWrapper[] raMsgs, long sTime) throws Exception {
            this.sTime = sTime;
            this.am = am;
            this.raMsgs = raMsgs;
            ost = OSTelnetFactory.getControlInstance(theAgent.cfg.osi.type.shortValue());
        }

        /**
         * MCONN - REQ
         */
        private boolean handleMCONN(RemoteAgentMsgWrapper raMsg, long sTime) {

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Got MCONN from " + theAgent.getSwName(am.agentAddrS));
            }
            boolean bstatus = false;

            OSPort sOSPort = new OSPort(raMsg.sPort, OSPort.INPUT_PORT);
            OSPort dOSPort = new OSPort(raMsg.dPort, OSPort.OUTPUT_PORT);

            Long transactionID = null;
            theAgent.setDelayMonitoringResult();
            if (raMsg.isFDX) {

                transactionID = theAgent.cfg.osi.beginTransaction(new OSPort[] { sOSPort, dOSPort, sOSPort.getPear(),
                        dOSPort.getPear() }, new Integer[][] { new Integer[] { OpticalLink.CONNECTED_FREE },
                        new Integer[] { OpticalLink.CONNECTED_FREE }, new Integer[] { OpticalLink.CONNECTED_FREE },
                        new Integer[] { OpticalLink.CONNECTED_FREE } }, new Integer[] { OpticalLink.CONNECTED_ML_CONN,
                        OpticalLink.CONNECTED_ML_CONN, OpticalLink.CONNECTED_ML_CONN, OpticalLink.CONNECTED_ML_CONN, });
            } else {
                transactionID = theAgent.cfg.osi.beginTransaction(new OSPort[] { sOSPort, dOSPort, }, new Integer[][] {
                        new Integer[] { OpticalLink.CONNECTED_FREE }, new Integer[] { OpticalLink.CONNECTED_FREE }, },
                        new Integer[] { OpticalLink.CONNECTED_ML_CONN, OpticalLink.CONNECTED_ML_CONN, });
            }//else

            bstatus = ((transactionID != null) && (ost != null));
            try {
                if (bstatus) {
                    try {
                        if (raMsg.isFDX) {
                            ost.makeFDXConn(raMsg.unSplittedPorts);
                        } else {
                            ost.makeConn(raMsg.unSplittedPorts);
                        }
                    } catch (Throwable t) {
                        bstatus = false;
                        logger.log(Level.WARNING, " [ MLCopyAgent ] Got exception deleting conn: [ "
                                + raMsg.unSplittedPorts + "]", t);
                    }
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "Making conn FDX = " + raMsg.isFDX + " [ " + raMsg.unSplittedPorts
                                + " ] status = " + bstatus + ". It took [ " + (System.currentTimeMillis() - sTime)
                                + " ]");
                    }
                }
                if (bstatus) {

                    theAgent.cfg.osi.commit(transactionID);
                    theAgent.cfg.osi.map.get(sOSPort).opticalLinkID = raMsg.session;
                    theAgent.cfg.osi.map.get(dOSPort).opticalLinkID = raMsg.session;

                    theAgent.cfg.osi.crossConnects.put(sOSPort,
                            new OpticalCrossConnectLink(sOSPort, dOSPort, Integer.valueOf(OpticalCrossConnectLink.OK)));

                    remoteLease = new Lease(sessionID, am.agentAddrS, 30 * 1000, MLPathSession.this, null);
                    ExpiredLeaseWatcher.getInstance().add(remoteLease);
                    Lease renewalLease = new Lease(sessionID, am.agentAddrS, 10 * 1000, null, theAgent);
                    LeaseRenewalManager.getInstance().add(renewalLease);

                    if (raMsg.isFDX) {
                        theAgent.cfg.osi.crossConnects.put(
                                dOSPort.getPear(),
                                new OpticalCrossConnectLink(dOSPort.getPear(), sOSPort.getPear(), Integer
                                        .valueOf(OpticalCrossConnectLink.OK)));
                        theAgent.cfg.osi.map.get(sOSPort.getPear()).opticalLinkID = raMsg.session;
                        theAgent.cfg.osi.map.get(dOSPort.getPear()).opticalLinkID = raMsg.session;
                    }
                    theAgent.cfg.setNewConfiguration(theAgent.cfg.osi, 700);
                    // theAgent.sendBConfToAll();
                    theAgent.receivedCMDs.put(raMsg.session, new ReceivedCmd(raMsg.session, am.agentAddrS, raMsg.sPort,
                            raMsg.dPort, raMsg.isFDX));
                } else {
                    theAgent.cfg.osi.rollback(transactionID);
                }

            } catch (Throwable t) {
                logger.log(Level.WARNING, " Got exc", t);
                theAgent.cfg.osi.rollback(transactionID);
            }

            //            theAgent.clearDelayMonitoringResult();

            return bstatus;
        }// handleMCONN()

        /**
         * DCONN - REQ
         */
        private boolean handleDCONN(RemoteAgentMsgWrapper raMsg, long sTime, boolean pdconn) throws Exception {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " Got DCONN from " + theAgent.getSwName(am.agentAddrS) + " isPDCON = " + pdconn);
            }

            boolean status = true;

            theAgent.setDelayMonitoringResult();

            try {
                if (raMsg.isFDX) {
                    ost.deleteFDXConn(raMsg.unSplittedPorts);
                } else {
                    ost.deleteConn(raMsg.unSplittedPorts);
                }
            } catch (Throwable t) {
                status = false;
                logger.log(Level.WARNING, " [ MLCopyAgent ] Got exception deleting conn: [ " + raMsg.unSplittedPorts
                        + "]", t);
            }

            OSPort sOSPort = new OSPort(raMsg.sPort, OSPort.INPUT_PORT);
            OSPort dOSPort = new OSPort(raMsg.dPort, OSPort.OUTPUT_PORT);

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(
                        Level.FINEST,
                        "Deleting conn [ " + raMsg.unSplittedPorts + " ] status = " + status + ". It took [ "
                                + (System.currentTimeMillis() - sTime) + " ] DCONN from "
                                + theAgent.getSwName(am.agentAddrS));
            }

            if (status) {
                theAgent.changePortState(sOSPort, OpticalLink.CONNECTED_FREE);
                theAgent.changePortState(dOSPort, OpticalLink.CONNECTED_FREE);
                theAgent.cfg.osi.map.get(sOSPort).opticalLinkID = null;
                theAgent.cfg.osi.map.get(dOSPort).opticalLinkID = null;
                theAgent.cfg.osi.crossConnects.remove(sOSPort);

                if (raMsg.isFDX) {
                    theAgent.changePortState(sOSPort.getPear(), OpticalLink.CONNECTED_FREE);
                    theAgent.changePortState(dOSPort.getPear(), OpticalLink.CONNECTED_FREE);

                    theAgent.cfg.osi.map.get(sOSPort.getPear()).opticalLinkID = null;
                    theAgent.cfg.osi.map.get(dOSPort.getPear()).opticalLinkID = null;

                    theAgent.cfg.osi.crossConnects.remove(dOSPort.getPear());
                }//if isFDX

                theAgent.receivedCMDs.remove(raMsg.session);
            }

            if (!pdconn) {
                stopIt();
                //                theAgent.clearDelayMonitoringResult();
                theAgent.cfg.setNewConfiguration(theAgent.cfg.osi, 700);
            } else {
                //                ReceivedCmd rc = (ReceivedCmd) theAgent.receivedCMDs.get(raMsg.session);
                //                if (rc != null) {
                //                    if (rc.pDconn == null) {
                //                        rc.pDconn = raMsg.unSplittedPorts;
                //                    } else {
                //                        rc.pDconn += " - " + raMsg.unSplittedPorts;
                //                    }
                //                }
            }
            //            theAgent.clearDelayMonitoringResult();
            return status;
        }// handleDCONN()

        private boolean handleLRENEW(RemoteAgentMsgWrapper raMsg, long sTime) {
            try {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "\n\n {LEASE_SYSTEM} Got a remote ( " + theAgent.getSwName(am.agentAddrS)
                            + " ) LRENEW MSG:" + raMsg.rawMSG);
                }

                if (opr == null) {
                    if (remoteLease == null) {
                        logger.log(Level.WARNING,
                                " [ ProtocolException ] Null opr & null remoteLease ... Or expired remote Lease from "
                                        + theAgent.getSwName(am.agentAddrS) + " MSG: " + raMsg.rawMSG);
                    } else {// LRENEW from the initiaor of this Distributed MLPathSession
                        if (raMsg.session.equals(remoteLease.getSessionID())
                                && am.agentAddrS.equals(remoteLease.getRemoteAgentAddress())) {
                            logger.log(
                                    Level.WARNING,
                                    " Renew PEER lease [ " + remoteLease + " ] from: "
                                            + theAgent.getSwName(am.agentAddrS) + " MSG: " + raMsg.rawMSG
                                            + "   OK!!! <----");
                            ExpiredLeaseWatcher.getInstance().renew(remoteLease, remoteLease.getLeaseRewalInterval());
                        } else {
                            logger.log(
                                    Level.WARNING,
                                    " Renew PEER lease [ " + remoteLease + " ] from: "
                                            + theAgent.getSwName(am.agentAddrS) + " MSG: " + raMsg.rawMSG
                                            + "   NOT OK!!! ---->");
                        }
                    }
                } else {// LRENEW to initiator from one of the remote peers
                    Lease lease = (Lease) opr.leases.get(am.agentAddrS);
                    if (lease == null) {
                        logger.log(
                                Level.WARNING,
                                " [ ProtocolException ] ? Or expired local Lease from "
                                        + theAgent.getSwName(am.agentAddrS) + " MSG: " + raMsg.rawMSG);
                    } else {
                        logger.log(Level.WARNING,
                                " Renew PEER lease [ " + lease + " ] from: " + theAgent.getSwName(am.agentAddrS)
                                        + " MSG: " + raMsg.rawMSG);
                        ExpiredLeaseWatcher.getInstance().renew(lease, lease.getLeaseRewalInterval());
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [HANDLED] Got exc handleLRENEW() ", t);
            }

            return true;
        }

        /**
         * PDOWN - REQ
         */
        private boolean handlePDOWN(RemoteAgentMsgWrapper raMsg, long sTime) throws Exception {

            logger.log(Level.INFO, " Got remote PDOWN for: " + raMsg.dPort + " - " + raMsg.sPort);
            if ((opr == null) || (opr.links == null) || (opr.leases == null) || (opr.sentCMDs == null)) {
                logger.log(Level.WARNING, "\n\n Got remote PDOWN for: " + raMsg.dPort + " - " + raMsg.sPort
                        + "But MLPathSession no longer available!");
            }

            if ((raMsg.sPort == null) || (raMsg.dPort == null) || raMsg.sPort.equals("null")
                    || raMsg.dPort.equals("null")) {
                logger.log(Level.WARNING, "Cannot reroute for ID = [ " + opr.id
                        + " ] Will stop this session! NULL sPort or dPort END POINT AT THE OTHER END !!!!! ");
                theAgent.deleteMLPathConn(opr.id);
                return false;
            }

            OSPort osp = new OSPort(raMsg.dPort, OSPort.INPUT_PORT);
            OpticalLink ol = null;

            if (am.agentAddrS.equals(theAgent.agentInfo.agentAddr)) {//it's me
                ol = theAgent.cfg.osi.map.get(osp);
            } else {
                ol = ((SyncOpticalSwitchInfo) theAgent.otherConfs.get(am.agentAddrS)).map.get(osp);
            }

            ol.state = Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.CONN_FAIL);
            String remoteAgentAddr = (String) theAgent.swNameAddrHash.get(ol.destination);

            SyncOpticalSwitchInfo remoteOSI = null;
            if (remoteAgentAddr == null) {
                if (ol.destination.equals(theAgent.cfg.osi.name)) {
                    remoteOSI = theAgent.cfg.osi;
                } else {
                    logger.log(Level.SEVERE, " [ ProtocolException ] remoteAgentAddr cannot be null");
                    return false;
                }
            }

            if (remoteOSI == null) {
                remoteOSI = (SyncOpticalSwitchInfo) theAgent.otherConfs.get(remoteAgentAddr);
                if (remoteOSI == null) {
                    logger.log(Level.SEVERE, " [ ProtocolException ] NoSuchConfiguration for remoteAgentAddr [ "
                            + remoteAgentAddr + " ]");
                    return false;
                }
            }

            OSPort remoteOSP = new OSPort(raMsg.sPort, OSPort.OUTPUT_PORT);
            OpticalLink remoteOL = remoteOSI.map.get(remoteOSP);

            if (remoteOL == null) {
                logger.log(Level.SEVERE, " [ ProtocolException ] NoSuchOpticalLink for remoteAgentAddr [ "
                        + remoteAgentAddr + " ] remotePort [ " + raMsg.sPort + " ]");
                return false;
            }

            remoteOL.state = Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.CONN_FAIL);

            Hashtable oldLinks = opr.links;
            Hashtable oldLeases = opr.leases;
            Hashtable oldSentCMDs = opr.sentCMDs;

            opr.links = new Hashtable();
            opr.leases = new Hashtable();
            opr.sentCMDs = new Hashtable();

            boolean status = theAgent.makeConnection(opr, true);

            if (status) {
                logger.log(Level.INFO, " Older links: " + oldLinks);
                logger.log(Level.INFO, " New links: " + opr.links);

                HashMap linksToBeRemoved = new HashMap();
                HashMap alreadyCreatedLinks = new HashMap();

                //Determine which are the links that need to be deleted
                for (Iterator itOld = oldLinks.entrySet().iterator(); itOld.hasNext();) {
                    Map.Entry oldEntry = (Map.Entry) itOld.next();

                    String dAgent = (String) oldEntry.getKey();
                    String ports = (String) oldEntry.getValue();

                    String newPorts = (String) opr.links.get(dAgent);

                    if ((newPorts == null) || !ports.equals(newPorts)) {
                        linksToBeRemoved.put(dAgent, ports);
                    }
                }//for - links to be deleted

                //Det already created links
                for (Iterator it = opr.links.entrySet().iterator(); it.hasNext();) {
                    Map.Entry entry = (Map.Entry) it.next();

                    String dAgent = (String) entry.getKey();
                    String ports = (String) entry.getValue();

                    String oldPorts = (String) oldLinks.get(dAgent);

                    if ((oldPorts != null) && oldPorts.equals(ports)) {
                        alreadyCreatedLinks.put(dAgent, ports);
                    }
                }

                return theAgent.makeOLConns(opr, linksToBeRemoved, alreadyCreatedLinks);
            }

            logger.log(Level.WARNING, "Cannot reroute for ID = [ " + opr.id + " ] Will stop this session!");
            opr.links = oldLinks;
            opr.leases = oldLeases;
            opr.sentCMDs = oldSentCMDs;
            theAgent.deleteMLPathConn(opr.id);
            return false;
        }

        private void processMsg() {

            try {
                boolean status = true;

                for (int i = 0; (i < raMsgs.length) && status; i++) {
                    switch (raMsgs[i].remoteCMD) {
                    case RemoteAgentMsgWrapper.MCONN: {// MCONN
                        status = handleMCONN(raMsgs[i], sTime);
                        break;
                    }
                    case RemoteAgentMsgWrapper.DCONN: {// DCONN
                        removeAllSessionLeases();
                        status = handleDCONN(raMsgs[i], sTime, false);
                        break;
                    }
                    case RemoteAgentMsgWrapper.PDCONN: {// DCONN
                        removeAllSessionLeases();
                        status = handleDCONN(raMsgs[i], sTime, true);
                        break;
                    }
                    case RemoteAgentMsgWrapper.NCONF: {
                        theAgent.cfg.setNewConfiguration(SyncOpticalSwitchInfo.fromOpticalSwitchInfo(Util
                                .getOpticalSwitchInfo(new StringReader(raMsgs[i].conf))), -1);
                        status = true;
                    }
                    case RemoteAgentMsgWrapper.LRENEW: {
                        handleLRENEW(raMsgs[i], sTime);
                        return;
                    }
                    case RemoteAgentMsgWrapper.PDOWN: {
                        removeAllSessionLeases();
                        status = handlePDOWN(raMsgs[i], sTime);
                        if (status) {//succesfull reroute
                            registerMasterLeases();
                        }
                        return;
                    }
                    }// end - switch
                }//end for

                String sStatus = "NACK";
                String message = "NoSuchMSG";

                if (status) {
                    sStatus = "ACK";
                }

                AgentMessage amB = null;
                try {
                    StringBuilder sbMsg = new StringBuilder(50);

                    sbMsg.append(RemoteAgentMsgWrapper.getDecodedRemoteCMD(raMsgs[0].remoteCMD))
                            .append(MLCopyAgent.CMD_TOKEN).append(sStatus).append(MLCopyAgent.CMD_TOKEN);
                    sbMsg.append("NOP").append(MLCopyAgent.CMD_TOKEN).append(raMsgs[0].remoteCMD_ID)
                            .append(MLCopyAgent.CMD_TOKEN).append(raMsgs[0].session);

                    message = sbMsg.toString();

                    amB = theAgent.createMsg(MLCopyAgent.getAndIncrementMSGID(), 1, 1, 8, am.agentAddrS,
                            theAgent.agentInfo.agentGroup, message);
                    theAgent.sendAgentMessage(amB);

                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "With sending message " + theAgent.getSwName(am.agentAddrS) + " \n\n");
                    }

                    theAgent.cfg.setNewConfiguration(theAgent.cfg.osi, 2000);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exception trying to notify the peer ... sending the message" + amB,
                            t);
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Cannot commit conn from message " + am, t);
            }
            long dt = System.currentTimeMillis() - sTime;
            logger.log(Level.INFO, "=============> Processing CMD [ " + raMsgs + " ] took " + dt);

        }

        @Override
        public void run() {
            String cName = Thread.currentThread().getName();
            Thread.currentThread().setName(cName + " AgentMsgSessionTask for " + sessionID + " - runnig");
            try {
                processMsg();
            } catch (Throwable t) {
                logger.log(Level.WARNING, " AgentMsgSessionTask - Cannot commit conn from message " + am, t);
            } finally {
                Thread.currentThread().setName(cName);
            }
        }
    }

    /**
     * I was born to have this name :)
     */
    private final String sessionID;

    private Lease remoteLease;

    private final MLCopyAgent theAgent;

    private XDRGenericComm xdrComm;

    private XDRMsgSessionTask xdrMsgSessionTask;

    private final long sessionStartTime;

    private final AtomicBoolean alreadyStopped;

    OpticalPathRequest opr;

    public MLPathSession(String sessionID, MLCopyAgent theAgent, XDRGenericComm xdrComm, long startTime) {
        this.sessionID = sessionID;
        this.theAgent = theAgent;
        this.xdrComm = xdrComm;
        this.xdrMsgSessionTask = new XDRMsgSessionTask();
        this.sessionStartTime = startTime;
        alreadyStopped = new AtomicBoolean(false);
    }

    public MLPathSession(OpticalPathRequest opr, MLCopyAgent theAgent, long startTime) {
        this.sessionID = opr.id;
        this.opr = opr;
        this.theAgent = theAgent;
        alreadyStopped = new AtomicBoolean(false);
        this.sessionStartTime = startTime;
        registerMasterLeases();
    }

    /**
     * Got a mesage from a friend ( )
     */
    void notifyXDRMessage(XDRMessage xdrMsg, long startTime) {
        synchronized (xdrMsgSessionTask.ll) {
            xdrMsgSessionTask.ll.add(new XDRMsgToken(xdrMsg, startTime));
            if (!xdrMsgSessionTask.running.get()) {
                xdrMsgSessionTask.running.set(true);
                MLCopyAgent.executor.execute(xdrMsgSessionTask);
            }
        }
    }

    /**
     * 
     */
    void notifyAgentMessage(AgentMessage am, RemoteAgentMsgWrapper raMsg, long notifTime) throws Exception {
        MLCopyAgent.executor.execute(new AgentMsgSessionTask(am, raMsg, notifTime));
    }

    void notifyAgentMessage(AgentMessage am, RemoteAgentMsgWrapper[] raMsgs, long notifTime) throws Exception {
        MLCopyAgent.executor.execute(new AgentMsgSessionTask(am, raMsgs, notifTime));
    }

    private void sendBackXDRMsg(XDRMessage msg) {
        try {
            xdrComm.write(msg);
            logger.log(Level.INFO, "Worker [" + opr.id + "] Sent BACK:\n" + msg.toString() + "\n");
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception sending XDRMessage back to daemon", t);
        }

    }

    void removeAllSessionLeases() {
        ExpiredLeaseWatcher elw = ExpiredLeaseWatcher.getInstance();
        if ((opr != null) && (opr.leases != null)) {
            for (Iterator it = opr.leases.values().iterator(); it.hasNext();) {
                elw.remove((Lease) it.next());
            }
        } else if (remoteLease != null) {
            elw.remove(remoteLease);
        }

        LeaseRenewalManager.getInstance().remove(sessionID);
    }

    void stopIt() {
        // allow multiple stopIt() invocations
        if (alreadyStopped.getAndSet(true)) {
            return;
        }

        logger.log(Level.INFO,
                "\n\nMLPathSession finishes [ " + sessionID + " ] ... Age "
                        + (System.currentTimeMillis() - sessionStartTime) + "\n\n");
        removeAllSessionLeases();
        theAgent.cleanupSession(sessionID);

        // TODO ... just do an extra check

    }

    private void handleLeaseExpired(Lease expiredLease) throws Exception {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " \n\n [ MLPathSession - handleLeaseExpired ] Lease [ " + expiredLease
                    + " ]  expired  ...");
        }
        if ((opr != null) && (opr.links != null)) {
            boolean found = false;
            for (Iterator it = opr.leases.entrySet().iterator(); it.hasNext();) {
                Map.Entry entry = (Map.Entry) it.next();
                Lease l = (Lease) entry.getValue();
                if (l.equals(expiredLease)) {
                    found = true;
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, " \n\n [ MLPathSession - handleLeaseExpired ] Lease [ " + expiredLease
                                + " ] @ " + theAgent.getSwName(expiredLease.getRemoteAgentAddress()) + " expired  ...");
                    }
                    break;
                }
            }

            if (!found) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " \n\n [ MLPathSession - handleLeaseExpired ] [ProtocolException] Lease "
                            + expiredLease + " NOT found in my opr.links  ...");
                }
                return;
            }// if - !found

            // do it asynch
            MLCopyAgent.getExecutor().execute(new Runnable() {

                @Override
                public void run() {
                    try {
                        theAgent.deleteOLConns(opr);
                    } catch (Throwable t) {
                        logger.log(Level.WARNING,
                                " [ MLPathSession - handleLeaseExpired ] Got exception deleting conns", t);
                    } finally {
                        stopIt();
                    }
                }
            });
            return;
        }// if - opr.links != null

        if (remoteLease != null) {
            long sTime = System.currentTimeMillis();
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER,
                        " \n\n [ MLPathSession - handleLeaseExpired ] Lease Expired !!!  MASTER IS DOWNNNNNN!!!!!!!!");
            }
            boolean status = true;
            OSTelnet ost = OSTelnetFactory.getControlInstance(theAgent.cfg.osi.type.shortValue());
            theAgent.setDelayMonitoringResult();

            ReceivedCmd rc = (ReceivedCmd) theAgent.receivedCMDs.get(sessionID);
            if (rc == null) {
                logger.log(Level.INFO,
                        " \n\n [ MLPathSession - handleLeaseExpired ] [ProtocolException !] Lease Expired ... but no such ReceivedCmd !!!\n");
            }

            String connKey = rc.sPort + " - " + rc.dPort;
            try {
                if (rc.isFDX) {
                    ost.deleteFDXConn(connKey);
                } else {
                    ost.deleteConn(connKey);
                }
            } catch (Throwable t) {
                status = false;
                logger.log(Level.WARNING, " [ MLCopyAgent ] Got exception deleting conn: [ " + connKey + "]", t);
            }

            OSPort sOSPort = new OSPort(rc.sPort, OSPort.INPUT_PORT);
            OSPort dOSPort = new OSPort(rc.dPort, OSPort.OUTPUT_PORT);

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Deleting conn [ " + connKey + " ] status = " + status + ". It took [ "
                        + (System.currentTimeMillis() - sTime) + " ]");
            }

            if (status) {

                theAgent.changePortState(sOSPort, OpticalLink.CONNECTED_FREE);
                theAgent.changePortState(dOSPort, OpticalLink.CONNECTED_FREE);

                theAgent.cfg.osi.map.get(sOSPort).opticalLinkID = null;
                theAgent.cfg.osi.map.get(dOSPort).opticalLinkID = null;

                theAgent.cfg.osi.crossConnects.remove(sOSPort);
                if (rc.isFDX) {
                    theAgent.changePortState(sOSPort.getPear(), OpticalLink.CONNECTED_FREE);
                    theAgent.changePortState(dOSPort.getPear(), OpticalLink.CONNECTED_FREE);

                    theAgent.cfg.osi.map.get(sOSPort.getPear()).opticalLinkID = null;
                    theAgent.cfg.osi.map.get(dOSPort.getPear()).opticalLinkID = null;

                    theAgent.cfg.osi.crossConnects.remove(dOSPort.getPear());
                }
                theAgent.cfg.setNewConfiguration(theAgent.cfg.osi, -1);
                theAgent.receivedCMDs.remove(sessionID);
            }

            //            theAgent.clearDelayMonitoringResult();

        } else {
            logger.log(Level.INFO,
                    " \n\n [ MLPathSession - handleLeaseExpired ] [HANDLED] Lease Expired ... but I have no opr and no remoteLease !!!");
        }
        stopIt();
    }

    void registerMasterLeases() {
        try {
            LeaseRenewalManager lrm = LeaseRenewalManager.getInstance();
            ExpiredLeaseWatcher elw = ExpiredLeaseWatcher.getInstance();

            for (Enumeration en = opr.links.keys(); en.hasMoreElements();) {
                String agentAddr = (String) en.nextElement();
                if (!agentAddr.equals(theAgent.agentInfo.agentAddr)) {
                    Lease lrmLease = new Lease(sessionID, agentAddr, 10 * 1000, null, theAgent);
                    lrm.add(lrmLease);
                    Lease lease = new Lease(sessionID, agentAddr, 30 * 1000, MLPathSession.this, null);
                    logger.log(Level.WARNING, "\n\n [ MCONN ] Added to \nLRM: " + lrmLease + "\nELW: " + lease + " \n");
                    opr.leases.put(agentAddr, lease);
                    elw.add(lease);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " registerLease got exception ", t);
        }

    }

    @Override
    public void notify(LeaseEvent event) {
        // TODO Auto-generated method stub
        if ((event.lease != null) && (event.lease.getSessionID() != null)
                && event.lease.getSessionID().equals(sessionID)) {
            try {
                handleLeaseExpired(event.lease);
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Got EXception ", t);
            }
            stopIt();
        } else {
            logger.log(
                    Level.INFO,
                    " \n\n [ MLPathSession - notify :- Expired Lease ] [ProtocolException] Lease Expired ... but I it is not my sessionID !!!");
        }
    }
}
