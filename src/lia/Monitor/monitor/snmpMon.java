package lia.Monitor.monitor;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.DynamicThreadPoll.SchJob;
import lia.util.ntp.NTPDate;

import org.opennms.protocols.snmp.SnmpEndOfMibView;
import org.opennms.protocols.snmp.SnmpHandler;
import org.opennms.protocols.snmp.SnmpObjectId;
import org.opennms.protocols.snmp.SnmpParameters;
import org.opennms.protocols.snmp.SnmpPduPacket;
import org.opennms.protocols.snmp.SnmpPduRequest;
import org.opennms.protocols.snmp.SnmpPeer;
import org.opennms.protocols.snmp.SnmpSMI;
import org.opennms.protocols.snmp.SnmpSession;
import org.opennms.protocols.snmp.SnmpSyntax;
import org.opennms.protocols.snmp.SnmpVarBind;

/*
 Abstract class to be used as a template for snmp modules
 */

public abstract class snmpMon extends SchJob implements SnmpHandler, Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -2299410475975189163L;

    private static final Logger logger = Logger.getLogger(snmpMon.class.getName());

    public MNode Node;

    public String TaskName;

    public String ClusterName;

    public String FarmName;

    public String[] sOid;

    public MonModuleInfo info;

    public int NR;

    //this flag is set to true any time there was an error ... either detected by the SNMP library
    //either by the TaskManager ... and stop() was called
    protected boolean wasError;

    protected String errorDescription;

    public volatile boolean hasToRun;

    SnmpSession session = null;

    SnmpPeer peer;

    Vector res;

    int m_port = -1;

    String m_community = null;

    protected int m_retries = 1;

    int m_timeout = 20000;

    SnmpObjectId m_stopAt = null;

    String m_startOid;

    protected int m_version = SnmpSMI.SNMPV1;

    InetAddress remote = null;

    Vector[] xres;

    int cpointer;

    public snmpMon(String sOid, String TaskName) {
        this.TaskName = TaskName;
        NR = 1;
        this.sOid = new String[1];
        this.sOid[0] = sOid;
        xres = new Vector[NR];
        info = new MonModuleInfo();
        clearErrorStatus();
    }

    public snmpMon(String[] sOid, String TaskName) {
        this.TaskName = TaskName;
        NR = sOid.length;
        this.sOid = sOid;
        info = new MonModuleInfo();
        clearErrorStatus();
    }

    public snmpMon() {
    }

    public boolean isRepetitive() {
        return true;
    }

    public MonModuleInfo init(MNode Node, String param) {
        this.Node = Node;
        try {
            remote = InetAddress.getByName(Node.getIPaddress());
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Could not determine remote IP Address", t);
        }

        peer = new SnmpPeer(remote);

        try {
            m_port = Integer.valueOf(AppConfig.getProperty("lia.Monitor.SNMP_port", "-1").trim()).intValue();
        } catch (Throwable t) {
            m_port = -1;
        }
        String snmpVersionS = AppConfig.getProperty("lia.Monitor.SNMP_version", "1");
        m_version = SnmpSMI.SNMPV1;
        if (snmpVersionS != null) {
            if (snmpVersionS.indexOf("2") != -1) {
                logger.log(Level.INFO, "Using SNMP V2");
                m_version = SnmpSMI.SNMPV2;
            }
        }
        InetAddress laddr = null;
        try {
            String sLADDRD = AppConfig.getProperty("lia.Monitor.SNMP_localAddress", null);
            if (sLADDRD != null) {
                try {
                    laddr = InetAddress.getByName(sLADDRD);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Cannot determine InetAddr for SNMP_localAddress " + sLADDRD, t);
                    laddr = null;
                }
            }
        } catch (Throwable t) {
            laddr = null;
        }
        if (m_port != -1) {
            peer.setPort(m_port);
        }
        peer.setTimeout(m_timeout);
        if (laddr != null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " Will use " + laddr + " as local IP Address");
            }
            peer.setServerAddress(laddr);
        }
        if (m_retries >= 1) {
            peer.setRetries(m_retries);
        }
        SnmpParameters parms = peer.getParameters();
        parms.setVersion(m_version);
        m_community = AppConfig.getProperty("lia.Monitor.SNMP_community");
        if (m_community != null) {
            parms.setReadCommunity(m_community.trim());
        }
        xres = new Vector[NR];
        session = null;
        return info;
    }

    public MNode getNode() {
        return Node;
    }

    public String getClusterName() {
        return Node.getClusterName();
    }

    public String getFarmName() {
        return Node.getFarmName();
    }

    public String getTaskName() {
        return TaskName;
    }

    void makeRequest() throws Exception {
        hasToRun = true;

        clearErrorStatus();

        try {
            session = new SnmpSession(peer);
        } catch (SocketException e) {
            logger.log(Level.WARNING, "SocketException creating the SNMP session", e);
        }

        session.setDefaultHandler(this);

        for (int i = 0; (i < NR) && hasToRun; i++) {
            xres[i] = new Vector();
        }

        for (int i = 0; (i < NR) && hasToRun; i++) {
            cpointer = i;
            SnmpObjectId id = new SnmpObjectId(sOid[i]);
            int[] ids = id.getIdentifiers();
            ++ids[ids.length - 1];
            id.setIdentifiers(ids);
            m_stopAt = id;

            SnmpVarBind[] vblist = { new SnmpVarBind(sOid[i]) };
            SnmpPduRequest pdu = new SnmpPduRequest(SnmpPduPacket.GETNEXT, vblist);
            pdu.setRequestId(SnmpPduPacket.nextSequence());
            try {
                synchronized (session) {
                    session.send(pdu);
                    session.wait();
                }
            } catch (InterruptedException e) {
                logger.log(Level.FINEST, " monSNMP interrupt Exception for node " + peer.getPeer(), e);
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void snmpReceivedPdu(SnmpSession session, int cmd, SnmpPduPacket pdu) {
        SnmpPduRequest req = null;
        if (pdu instanceof SnmpPduRequest) {
            req = (SnmpPduRequest) pdu;
        }

        if (pdu.getCommand() != SnmpPduPacket.RESPONSE) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Error: Received non-response command" + pdu.getCommand());
            }
            setError("Error: Received non-response command [ " + pdu.getCommand()
                    + " ] trying to communicate with the remote host " + session.getPeer().getPeer());
            synchronized (session) {
                session.notify();
            }
            return;
        }

        if (req.getErrorStatus() != 0) {
            setError("Error: Received an error status [ " + req.getErrorStatus()
                    + " ]  != 0 trying to communicate with the remote host " + session.getPeer().getPeer());
            synchronized (session) {
                session.notify();
            }
            return;
        }

        SnmpVarBind vb = pdu.getVarBindAt(0);
        if ((vb.getValue().typeId() == SnmpEndOfMibView.ASNTYPE)
                || ((m_stopAt != null) && (m_stopAt.compare(vb.getName()) < 0))) {
            synchronized (session) {
                session.notify();
            }
            return;
        }

        xres[cpointer].add(vb);

        SnmpVarBind[] vblist = { new SnmpVarBind(vb.getName()) };
        SnmpPduRequest newReq = new SnmpPduRequest(SnmpPduPacket.GETNEXT, vblist);
        newReq.setRequestId(SnmpPduPacket.nextSequence());
        session.send(newReq);

    }

    private void setError(String errorDesc) {
        wasError = true;
        errorDescription = errorDesc;
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " Setting error ... " + errorDesc);
        }
    }

    private void clearErrorStatus() {
        wasError = false;
        errorDescription = null;
    }

    @Override
    public void snmpTimeoutError(SnmpSession session, SnmpSyntax pdu) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINE, "The session timed out trying to communicate with the remote host "
                    + session.getPeer().getPeer());
        }
        setError("The session timed out trying to communicate with the remote host " + session.getPeer().getPeer());
        synchronized (session) {
            session.notify();
        }
    }

    @Override
    public void snmpInternalError(SnmpSession session, int err, SnmpSyntax pdu) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINE, "An unexpected error occured trying to communicate with the remote host "
                    + session.getPeer().getPeer());
        }
        setError("An unexpected error (snmpInternalError) occured trying to communicate with the remote host "
                + session.getPeer().getPeer());
        synchronized (session) {
            session.notify();
        }
    }

    public Vector results() throws Exception {
        long t0 = NTPDate.currentTimeMillis();
        makeRequest();
        eff_time = (NTPDate.currentTimeMillis() - t0);
        close();
        return xres[0];
    }

    public Vector[] mresults() throws Exception {
        long t0 = NTPDate.currentTimeMillis();
        makeRequest();
        eff_time = (NTPDate.currentTimeMillis() - t0);
        close();
        return xres;
    }

    void close() {
        if (session != null) {
            synchronized (session) {
                if (!session.isClosed()) {
                    session.close();
                }
            }
        }
    }

    //this is forced STOPED
    @Override
    public boolean stop() {
        if (session != null) {
            logger.log(Level.INFO, "Try to stop SNMP for = " + session.getPeer().getPeer() + " session " + session);
            // -- it's always a timeout and the library will call snmpTimeoutError()
            //if  close() is forced here could lead to a deadlock ... as seen
            //close();
        }
        hasToRun = false;
        setError("Stopped forced by the TaskManager");
        return true;
    }

    public MonModuleInfo getInfo() {
        return info;
    }

}