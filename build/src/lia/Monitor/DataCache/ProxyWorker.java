/*
 * $Id: ProxyWorker.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.DataCache;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ClientsFarmProxy.ProxyServiceEntry;
import lia.Monitor.Farm.FarmMonitor;
import lia.Monitor.JiniSerFarmMon.MLLUSHelper;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.EMsg;
import lia.Monitor.monitor.GenericMLEntry;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.monMessage;
import lia.util.MFarmConfigUtils;
import lia.util.Utils;
import lia.util.logging.comm.MLLogMsg;
import lia.util.mail.MailFactory;
import lia.util.mail.PMSender;
import lia.util.ntp.NTPDate;
import lia.util.threads.MonALISAExecutors;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;

/**
 * @author ramiro
 * @since ML v1.2
 */
public class ProxyWorker extends Thread {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(ProxyWorker.class.getName());

    /** maximum time to connect with the other endPoint */
    private static final int CONNECT_TIMEOUT = 30 * 1000; // 30s

    private static ProxyWorker _thisInstance;

    private static final AtomicBoolean inited = new AtomicBoolean(false);

    private static final Map<ServiceID, ProxyInfo> proxies = new ConcurrentHashMap<ServiceID, ProxyInfo>();

    private static Cache cache;

    private static tcpServer server;

    private static volatile ServiceItem myServiceItem;

    private static final Object serviceItemReferenceLock = new Object();

    // used for agent comm
    // only for testing ... and DEMOS :)
    private static InetAddress preferredProxy;

    private static final AtomicBoolean sidChanged = new AtomicBoolean(false);

    public static final String PMS_KEY = "MLCapOK";

    public static final String MLLOG_KEY = "MLLogOK";

    public static final String PROXY_PORTS_KEY = "proxyPorts";

    private static final MLLUSHelper mlLusHelper = MLLUSHelper.getInstance();

    private static ServiceItem[] proxyServices;

    private static final AtomicLong lastConfSize = new AtomicLong(0L);

    private static final AtomicLong lastConfDT = new AtomicLong(0L);

    private static final AtomicLong newSIDChanged = new AtomicLong(0L);

    private static final AtomicLong newPItemsChanged = new AtomicLong(0L);

    private static final class ProxyAddress {
        private final String proxyHostName;
        private final InetAddress[] addresses;
        private final int[] ports;

        /**
         * @param addresses
         * @param proxyHostName
         * @param ports
         */
        private ProxyAddress(String proxyHostName, InetAddress[] addresses, int[] ports) {
            this.addresses = addresses;
            this.proxyHostName = proxyHostName;
            this.ports = ports;
        }

        static final ProxyAddress fromServiceItem(ServiceItem serviceItem) {

            return null;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("ProxyAddress [proxyHostName=").append(proxyHostName).append(", addresses=")
                    .append(Arrays.toString(addresses)).append(", ports=").append(Arrays.toString(ports)).append("]");
            return builder.toString();
        }

    }

    private static final class ProxyInfo {

        final tcpClientWorker tcw;

        final AtomicBoolean canSendMail;

        final AtomicBoolean canReceiveLogs;

        ProxyInfo(tcpClientWorker tcw, boolean bCanSendMail, boolean bCanReceiveLogs) {
            this.tcw = tcw;
            this.canSendMail = new AtomicBoolean(bCanSendMail);
            this.canReceiveLogs = new AtomicBoolean(bCanReceiveLogs);
        }

    }

    static {
        try {
            String prefAddrS = AppConfig.getProperty("lia.Monitor.DataCache.ProxyWorker.preferredProxyAddress", null);
            if (prefAddrS != null) {
                preferredProxy = InetAddress.getByName(prefAddrS);
                logger.log(Level.INFO, "\nUsing preferred address ... " + preferredProxy.toString());
            } else {
                preferredProxy = null;
            }
        } catch (Throwable t) {
            preferredProxy = null;
        }
    }

    public static final synchronized ProxyWorker getInstance(Cache cache, tcpServer tcpServer) {

        if (inited.compareAndSet(false, true)) {
            _thisInstance = new ProxyWorker();
            server = tcpServer;
            ProxyWorker.cache = cache;
            MonALISAExecutors.getMLNetworkExecutor().submit(_thisInstance);
        }

        return _thisInstance;
    }

    private ProxyWorker() {
    }

    public static final Map<String, Double> getMonitoringParams() {
        final Map<String, Double> m = new HashMap<String, Double>();
        m.put("PW_ConnPCount", Double.valueOf(proxies.size()));
        m.put("PW_NewSIDChanged", Double.valueOf(newSIDChanged.get()));
        m.put("PW_NewPItemsChanged", Double.valueOf(newPItemsChanged.get()));
        return m;
    }

    public static void setServiceID(final ServiceItem si) {
        if (si == null) {
            logger.log(Level.WARNING, "ProxyWorker notified with a null ServiceItem!!!!!");
            return;
        }

        if (si.serviceID == null) {
            logger.log(Level.WARNING, "ProxyWorker has null SID inside the service item");
            return;
        }

        synchronized (serviceItemReferenceLock) {
            if (myServiceItem == null) {
                myServiceItem = si;
                serviceItemReferenceLock.notifyAll();
                return;
            }
        }

        System.setProperty("MonALISA_ServiceID", si.serviceID.toString());

        if (myServiceItem.serviceID.equals(si.serviceID)) {
            return;
        }

        // I've got a different SID
        logger.log(Level.INFO, "ProxyWorker NEW SID: " + si.serviceID + "; Old one: " + myServiceItem.serviceID);
        newSIDChanged.incrementAndGet();

        try {
            String subjToAdd = cache.getUnitName() + " / ";
            try {
                subjToAdd += InetAddress.getLocalHost().getHostAddress();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[ ProxyWorker ] Unable to determine localhost address! Cause: ", t);
            }

            long ntpLongDate = NTPDate.currentTimeMillis();
            long cLongDate = System.currentTimeMillis();

            MailFactory.getMailSender().sendMessage(
                    FarmMonitor.realFromAddress,
                    "mlstatus@monalisa.cern.ch",
                    new String[] { "mlstatus@monalisa.cern.ch" },
                    " [ LUS -- SID CHANGED ] @ " + subjToAdd,
                    "NTPDate: " + new Date(ntpLongDate) + " / " + ntpLongDate + "\n" + "SysDate: "
                            + new Date(cLongDate) + " / " + cLongDate + "\n" + "NEW SID = " + si.serviceID
                            + "\nOLD SID = " + myServiceItem.serviceID);
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, " [ ProxyWorker ] Unable to send status mail. Cause:", t);
            }
        }

        synchronized (serviceItemReferenceLock) {
            myServiceItem = si;
        }

        sidChanged.set(true);
    }

    private final static class ProxyWorkerTask implements Runnable {

        @Override
        public void run() {
            try {
                final ServiceItem[] sitems = mlLusHelper.getProxies();
                if ((sitems != null) && (sitems.length > 0)) {
                    if (proxyServices != sitems) {

                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER,
                                    " [ ProxyWorker ] Received new array of ServiceItem[]-s from MLLUSHelper");
                        }

                        newPItemsChanged.incrementAndGet();
                        proxyServices = sitems;

                        final int len = proxyServices.length;
                        final Set<ServiceID> processedSIDs = new HashSet<ServiceID>(len);
                        for (int i = 0; i < len; i++) {

                            final ServiceItem si = proxyServices[i];
                            final ServiceID sid = si.serviceID;

                            if (processedSIDs.contains(sid)) {
                                continue;
                            }
                            processedSIDs.add(sid);

                            if (!proxies.containsKey(sid)) {
                                AddProxy(si);
                            }

                            // Dynamically set proxies which are capable to send emails
                            final ProxyInfo pi = proxies.get(sid);
                            // update mail capabilities
                            if (pi != null) {
                                final GenericMLEntry gmle = Utils.getEntry(si, GenericMLEntry.class);
                                if ((gmle != null) && (gmle.hash != null)) {
                                    boolean bCanSendMail = false;
                                    try {
                                        Boolean BcanSendMail = (Boolean) gmle.hash.get(PMS_KEY);
                                        bCanSendMail = (BcanSendMail == null) ? false : BcanSendMail.booleanValue();
                                    } catch (Throwable t) {
                                        bCanSendMail = false;
                                    }

                                    if (logger.isLoggable(Level.FINEST)) {
                                        logger.log(Level.FINEST, " [ PTW ] " + pi.tcw.getKey() + " " + PMS_KEY + " = "
                                                + bCanSendMail);
                                    }

                                    pi.canSendMail.set(bCanSendMail);

                                    boolean bCanReceiveLogs = false;
                                    try {
                                        Boolean BCanReceiveLogs = (Boolean) gmle.hash.get(MLLOG_KEY);
                                        bCanReceiveLogs = (BCanReceiveLogs == null) ? false : BCanReceiveLogs
                                                .booleanValue();
                                    } catch (Throwable t) {
                                        bCanReceiveLogs = false;
                                    }

                                    if (logger.isLoggable(Level.FINEST)) {
                                        logger.log(Level.FINEST, " [ PTW ] " + pi.tcw.getKey() + " " + MLLOG_KEY
                                                + " = " + bCanReceiveLogs);
                                    }

                                    pi.canReceiveLogs.set(bCanReceiveLogs);
                                }// if gmle != null
                            }// if pi != null
                        }// for - sitems
                    } else { // proxyServices != sitems
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER,
                                    " [ ProxyWorker ] Same received the same array of ServiceItem[]-s as in the last iteration from MLLUSHelper");
                        }
                    }
                }// if(sitems != null && sitems.length > 0)

                verifyProxiesConns();
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ ProxyWorkerTask ] [ HANDLED ] Exception in main loop: ", t);
            }
        }

    }

    @Override
    public void run() {
        logger.log(Level.INFO, "ProxyWorker Started ... waiting to get a serviceID");

        ServiceID sid = null;
        synchronized (serviceItemReferenceLock) {
            if (myServiceItem == null) {
                final long sTNanos = System.nanoTime();
                boolean bFirstTime = true;
                while (myServiceItem == null) {
                    try {
                        if (!bFirstTime) {
                            bFirstTime = false;
                            logger.log(
                                    Level.INFO,
                                    "ProxyWorker waiting for SID... DT="
                                            + TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - sTNanos) + " seconds");
                        }
                        serviceItemReferenceLock.wait(TimeUnit.SECONDS.toMillis(30));
                    } catch (InterruptedException ie) {
                        logger.log(Level.INFO, " [ ProxyWorker ] The thread was interrupted waiting for serviceID");
                        Thread.interrupted();
                    } catch (Throwable t) {
                        logger.log(Level.INFO,
                                " [ ProxyWorker ] The thread got general exception waiting for serviceID", t);
                    }
                }

                sid = myServiceItem.serviceID;
            }

        }

        logger.log(Level.INFO, "ProxyWorker got the serviceID: " + sid);

        // just speed-up ...

        try {
            ServiceItem[] sitems = mlLusHelper.getProxies();
            while ((sitems == null) || (sitems.length == 0)) {
                mlLusHelper.forceUpdate();
                try {
                    Thread.sleep(1000);
                } catch (Throwable t) {
                    // ignore
                }

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " [ ProxyWorker ] waiting for proxy ServiceItem-s");
                }

                sitems = mlLusHelper.getProxies();
            }

            MonALISAExecutors.getMLNetworkExecutor().scheduleWithFixedDelay(new ProxyWorkerTask(), 0, 40,
                    TimeUnit.SECONDS);
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got Exception main loop for ProxyWorker", t);
        }
    }

    public final int proxyConnectionsCount() {
        return proxies.size();
    }

    private static void verifyProxiesConns() {
        if (proxies.size() == 0) {
            return;
        }

        StringBuilder sbLog = null;

        final boolean isFiner = logger.isLoggable(Level.FINER);
        if (isFiner) {
            sbLog = new StringBuilder(1024);
        }

        boolean bSidChanged = sidChanged.get();
        if (bSidChanged) {
            sidChanged.set(false);
        }

        for (Iterator<Map.Entry<ServiceID, ProxyInfo>> it = proxies.entrySet().iterator(); it.hasNext();) {
            Map.Entry<ServiceID, ProxyInfo> entry = it.next();
            ServiceID sid = entry.getKey();
            tcpClientWorker tcw = entry.getValue().tcw;

            if (isFiner) {
                sbLog.append("\n SID [ ").append(sid).append(" ] ---> TCW [ ")
                        .append((tcw == null) ? "null" : tcw.getKey()).append(" ]");
            }

            try {
                if (bSidChanged || !tcw.isConnected()) {
                    it.remove();
                    logger.log(Level.INFO, " [ PTW ] Removing proxy conn [ " + tcw.getKey() + " ] ID= [ " + sid
                            + " ] from local PTW cache.");
                    tcw.close_connection();
                    tcw = null;
                }
            } catch (Throwable t) {
                logger.log(Level.INFO, " [ PTW ] [verifyProxiesConns] [ HANDLED ]  Got ex removing [ " + sid + " ] "
                        + " TCW = " + ((tcw == null) ? "null" : tcw.getKey()), t);
            }// catch
        }// for

        if (isFiner) {
            logger.log(Level.FINER, "\n [ PTW ] Current Connections: \n" + sbLog.toString());
        }
    }

    void newResult(Object o) {
        for (ProxyInfo pi : proxies.values()) {
            pi.tcw.addNewResult(o);
        }
    }

    void notifyPMS(Object o) {
        if (o instanceof Integer) {
            PMSender.getInstance().notifyDelivered((Integer) o);
            return;
        }
        // Be quiet ;) ... anyway it is a protocol ex or something really bad happened
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, " [ Cache ] [ notifyPMS ] unk obj: " + o);
        }
    }

    public boolean sendMLLog(MLLogMsg message) {
        monMessage msg = new monMessage(MLLOG_KEY, null, message);
        boolean succes = false;

        for (final ProxyInfo pi : proxies.values()) {
            if (pi.canReceiveLogs.get()) {
                try {
                    pi.tcw.WriteObject(msg, tcpClientWorker.ML_EMSG);
                    succes = true;
                    break;
                } catch (Throwable t) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, " [ PTW ] Got ex sending EMsg", t);
                    }
                }
            }
        }

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ PW ] Send Success = " + succes + " ... \n" + message);
        }

        return succes;
    }

    public boolean sendMail(EMsg emailMessage) {

        monMessage msg = new monMessage(PMS_KEY, null, emailMessage);
        boolean succes = false;

        for (final ProxyInfo pi : proxies.values()) {
            if (pi.canSendMail.get()) {
                try {
                    pi.tcw.WriteObject(msg, tcpClientWorker.ML_EMSG);
                    succes = true;
                    break;
                } catch (Throwable t) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, " [ PTW ] Got ex sending EMsg", t);
                    }
                }
            }
        }

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ PW ] Send Success = " + succes + " ... \n" + emailMessage);
        }

        return succes;
    }

    public void rezToOneProxy(Object o) {
        if (proxies.size() > 0) {
            boolean sent = false;

            if (preferredProxy != null) {
                for (final ProxyInfo pi : proxies.values()) {
                    final tcpClientWorker lw = pi.tcw;
                    if (preferredProxy.equals(lw.endPointAddress)) {
                        try {
                            lw.WriteObject(o, tcpClientWorker.ML_AGENT_MESSAGE);
                            sent = true;
                        } catch (Throwable t) {
                            sent = false;
                        } // try - catch
                        break;
                    }
                }// for
            }
            if (sent) {
                return;
            }
            for (final ProxyInfo pi : proxies.values()) {
                try {
                    pi.tcw.WriteObject(o, tcpClientWorker.ML_AGENT_MESSAGE);
                    sent = true;
                    break;
                } catch (Throwable t) {
                    logger.log(Level.WARNING,
                            " [ ProxyWorker ] Couldn't send message .. try another proxy connection ... ", t);
                    sent = false;
                }
            } // for

        } else {
            logger.log(Level.WARNING, "No proxy connections to send the agent message int rezToOneProxy()");
        }
    } // rezToOneProxy

    public void rezToAllProxies(Object o) {
        for (final ProxyInfo pi : proxies.values()) {
            try {
                pi.tcw.WriteObject(o, tcpClientWorker.ML_AGENT_MESSAGE);
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ ProxyWorker ] exception sending to all proxies. Cause: ", t);
            }
        } // for
    } // rezToAllProxies

    void updateConfig(MFarm farm) {

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER,
                    " [ ProxyWorker ] [ updateConfig ] Trying to send MFarm:\n " + MFarmConfigUtils.getMFarmDump(farm)
                            + " \n");
        }
        final monMessage msg = new monMessage(monMessage.ML_CONFIG_TAG, null, farm);
        byte[] sMsg = null;

        final long cTime = Utils.nanoNow();

        try {
            sMsg = Utils.writeDirectObject(msg);
        } catch (Throwable t) {
            sMsg = null;
            // OOM can be the one and only >>> ????
            logger.log(Level.WARNING, "[ ProxyWorker ] [ updateConfig ] [ HANDLED ] Got exception serialiazing Objs", t);
        }

        final long confDT = Utils.nanoNow() - cTime;
        lastConfSize.set((sMsg != null) ? sMsg.length : -1);
        lastConfDT.set(confDT);

        final Object oToWrite = ((sMsg == null) ? msg : (Object) sMsg);

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [ ProxyWorker ] [ updateConfig ] Config serialization DT = "
                    + TimeUnit.NANOSECONDS.toMillis(confDT) + " ms. Succed = " + (sMsg != null));
        }

        for (final ProxyInfo pi : proxies.values()) {
            pi.tcw.WriteObject(oToWrite, tcpClientWorker.ML_CONFIG_MESSAGE);
        }
    }

    public static final long getLastConfSize() {
        return lastConfSize.get();
    }

    public static final long getLastConfDelta() {
        return lastConfDT.get();
    }

    private static void AddProxy(ServiceItem si) {
        if (si == null) {
            return;
        }

        Entry[] proxyEntry = si.attributeSets;
        if (proxyEntry != null) {
            ProxyServiceEntry pse = Utils.getEntry(si, ProxyServiceEntry.class);
            if (pse != null) {
                GenericMLEntry gmle = Utils.getEntry(si, GenericMLEntry.class);
                int[] pPorts = null;

                if ((gmle != null) && (gmle.hash != null)) {
                    pPorts = (int[]) gmle.hash.get(PROXY_PORTS_KEY);
                }

                if ((pPorts == null) || (pPorts.length == 0)) {
                    pPorts = new int[] { pse.proxyPort.intValue() };
                }

                String ipAddress = pse.ipAddress;
                StringBuilder sb = new StringBuilder();
                sb.append(" [ ProxyWorker ] Trying to connect with ProxyService [ ").append(si.serviceID).append(" ]");

                sb.append(" IPAddress [ ").append(ipAddress).append(" ]");
                sb.append(" port(s): ").append(Arrays.toString(pPorts));

                logger.log(Level.INFO, sb.toString());

                tcpClientWorker tcw = null;

                for (int portNumber : pPorts) {
                    Socket s = null;
                    try {
                        s = new Socket();
                        s.connect(new InetSocketAddress(InetAddress.getByName(ipAddress), portNumber), CONNECT_TIMEOUT);
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Got exc connecting to " + ipAddress + ":" + portNumber, t);
                        if (s != null) {
                            try {
                                s.close();
                            } catch (Throwable ignSocket) {
                                // not now - I'm busy
                            }
                        }
                        continue;
                    }

                    try {
                        tcw = tcpClientWorker.newInstance(cache, server, s, server.getConnKey(s));
                        // send initial conf
                        tcw.WriteObject(new monMessage(monMessage.ML_SID_TAG, null, myServiceItem),
                                tcpClientWorker.ML_SID_MESSAGE);
                        tcw.WriteObject(new monMessage(monMessage.ML_CONFIG_TAG, null, Cache.getMFarm()),
                                tcpClientWorker.ML_CONFIG_MESSAGE);

                        proxies.put(si.serviceID, new ProxyInfo(tcw, false, false));
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Cannot create tcpClientWorker for " + server.getConnKey(s), t);
                        try {
                            if (tcw != null) {
                                tcw.close_connection();
                            }
                        } catch (Throwable ignore) {
                            // ignore exception
                        }
                        return;
                    }

                    // mail cap is always updated from LUS
                    if (cache.agentsEngine != null) {
                        cache.agentsEngine.newProxyConns();
                    }
                    break;
                }

                if (tcw != null) {
                    logger.log(Level.INFO, " [ PTW ] Connected with proxy: " + tcw.getKey());
                }

            } // if ( pse != null )
        }// if ( proxyEntry != null )
    }
}
