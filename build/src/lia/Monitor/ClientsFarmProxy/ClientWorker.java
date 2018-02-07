/*
 * $Id: ClientWorker.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.ClientsFarmProxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import lia.Monitor.ClientsFarmProxy.tunneling.AppCtrlSessionManager;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MonMessageClientsProxy;
import lia.Monitor.monitor.monMessage;
import lia.util.MFarmConfigUtils;
import lia.util.Utils;
import lia.util.mail.MailFactory;
import lia.util.ntp.NTPDate;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;

/**
 * More or less like the FarmWorker; do not cycle
 * 
 * @author ramiro
 */
public class ClientWorker extends GenericProxyWorker {

    private static final Logger logger = Logger.getLogger(ClientWorker.class.getName());

    private static final AtomicLong lastErrNotify = new AtomicLong(0);

    private static final long DT_NOTIFY_COMPRESS_THRESHOLD_NANOS = TimeUnit.MINUTES.toNanos(AppConfig.getl(
            "lia.Monitor.ClientsFarmProxy.ClientWorker.DT_NOTIFY_COMPRESS_THRESHOLD_MINUTES", 45));
    private static final long DT_NOTIFY_COMPRESS_DURATION_THRESHOLD_NANOS = TimeUnit.SECONDS.toNanos(AppConfig.getl(
            "lia.Monitor.ClientsFarmProxy.ClientWorker.DT_NOTIFY_COMPRESS_DURATION_THRESHOLD_SECONDS", 20));
    private static final String[] NOTIFY_COMPRESS_RCPTS = AppConfig.getVectorProperty(
            "lia.Monitor.ClientsFarmProxy.ClientWorker.RCPTS", "Ramiro.Voicu@cern.ch");
    private static final AppCtrlSessionManager appCtrlSessMgr = AppCtrlSessionManager.getInstance();

    private final Integer myID;

    private final String myName;

    private final String strToFile;

    private long lastSizeMsg = 0;

    private long lastConfSizeMsg = 0;

    private long lastClientMsgRate = 0;

    private long lastRateMsg = 0;

    private long lastConfRate = 0;

    private final long startTime;

    private long lastMailConfSentBytes = 0;

    private final AtomicBoolean notified = new AtomicBoolean(false);

    private long lastMailSentBytes = 0; /*
                                         * bytes couter at last mail send with statistics
                                         */

    // TODO - sa leg grupurile de configuratii
    private final ConcurrentSkipListSet<String> groupsInterestedIn = new ConcurrentSkipListSet<String>();

    private final AtomicBoolean compressed = new AtomicBoolean(false);

    // private SendConfsThread sendConfs = new SendConfsThread();

    private boolean firstConfsSent = false;

    public ClientWorker(Integer id, ProxyTCPWorker ptw) {
        super(ptw);
        this.myID = id;
        String clientHostName = "N/A";
        try {
            clientHostName = ptw.conn.getEndPointAddress().getCanonicalHostName();
        } catch (Throwable t) {
            clientHostName = ptw.conn.getEndPointAddress().toString();
            logger.log(Level.WARNING, " ClientWorker init - unable to determine hostname for: " + clientHostName);
        }

        myName = ptw.conn.getEndPointAddress().toString() + "/" + clientHostName + ":" + ptw.conn.getEndPointPort();

        strToFile = "[Client] " + myName + "\n\t\t[NrOfMsgInLast hour] ";
        startTime = Utils.nanoNow();
        lastClientMsgRate = NTPDate.currentTimeMillis();

    } // ClientWorker

    public boolean isNew() {
        return compressed.get();
    } // isNew

    public double getMailConfBytes() {
        double d = 0;

        final long currentSentConfBytes = getConfSentBytes();
        if (currentSentConfBytes > lastMailConfSentBytes) {
            d = currentSentConfBytes - lastMailConfSentBytes;
        } // if
        lastMailConfSentBytes = currentSentConfBytes;

        return d;
    } // getMailConfBytes

    public double getMailSentMega() {
        double d = 0;

        final long currentSentBytes = getSentBytes();

        if (currentSentBytes > lastMailSentBytes) {
            d = currentSentBytes - lastMailSentBytes;
        }
        lastMailSentBytes = currentSentBytes;
        return d;
    } // getMailSentKilo

    private Set<String> getDiferences(Set<String> v1, Vector<String> v2) {

        if (v1 == null) {
            return new TreeSet<String>(v2);
        }

        if (v2 == null) {
            return null;
        }

        TreeSet<String> v = new TreeSet<String>();

        for (final String s : v2) {
            if (!v1.contains(s)) {
                v.add(s);
            } // if
        } // for

        return v;
    } // getdiferences

    public boolean isClosed() {
        return notified.get();
    }

    @Override
    public void notifyMessage(Object message) {

        final boolean isFinest = logger.isLoggable(Level.FINEST);
        final boolean isFiner = isFinest || logger.isLoggable(Level.FINEST);

        if (message != null) {

            if (isFinest) {
                logger.log(Level.FINEST, "ClientWorker rcv msg: " + message.toString());
            }

            // if message from client for preferred groups.
            if (message instanceof MonMessageClientsProxy) {
                MonMessageClientsProxy mmcp = (MonMessageClientsProxy) message;
                if (mmcp.tag != null) {
                    if (mmcp.tag.startsWith("proxy")) {

                        if (isFiner) {
                            logger.log(Level.FINER, "\nreceived a proxy message with groups: " + message + "\n");
                        }

                        try {
                            final String c = (String) mmcp.ident;
                            if ((c != null) && c.startsWith("c")) {
                                compressed.set(true);
                            }
                        } catch (Throwable t) {
                        }

                        if (compressed.get()) {
                            logger.log(Level.INFO, " The client: " + myName
                                    + " is a new Client; will receive new config messages");
                        } else {
                            logger.log(Level.INFO, " The client: " + myName
                                    + " is an old client; will NOT receive new config messages");
                        }

                        try {

                            if (!firstConfsSent) {
                                firstConfsSent = true;
                                groupsInterestedIn.addAll((Vector<String>) mmcp.result);
                                return;
                            } // if

                            Set<String> v = getDiferences(groupsInterestedIn, (Vector<String>) mmcp.result);

                            synchronized (groupsInterestedIn) {
                                groupsInterestedIn.clear();
                                final Vector<String> vr = (Vector<String>) mmcp.result;
                                if (vr != null) {
                                    groupsInterestedIn.addAll(vr);
                                }
                            }

                            startSendConfsThread(v);
                            return;
                        } catch (Throwable ex) {
                            logger.log(Level.WARNING,
                                    " [ ClientWorker ] got exception in notify message processing groups", ex);
                        }

                        // end if mmcp.tag.startsWith("proxy")
                    } else if (mmcp.tag.startsWith(monMessage.ML_APP_CTRL_TAG)) {
                        appCtrlSessMgr.notifyClientMessage(mmcp, this);
                        return;
                    }// mmcp.tag.startsWith(monMessage.ML_APP_CTRL_TAG)
                }// if tag != null
            }// end if (message instanceof MonMessageClientsProxy)

            ClientsCommunication.addSendMessage(myID, message);
        } else {
            logger.log(Level.WARNING, "ClientWorker received null message!");
        }

    } // notifyMessage

    @Override
    public void notifyConnectionClosed() {
        if (notified.compareAndSet(false, true)) {
            logger.log(Level.WARNING, "Close connection with client " + toString());
            ClientsCommunication.removeClient(myID);
            appCtrlSessMgr.notifyClientDown(this);
            stopIt();
            if (infoToFile.enabled()) {
                infoToFile.writeToFile((new Date()) + "[Client] [" + myName + "] stoped! ");
            }
        }
    } // notifyConnectionClosed

    // moved here from run()

    @Override
    public boolean commInit() {
        if (infoToFile.enabled()) {
            infoToFile.writeToFile((new Date()) + " [ Client ] [" + myName + "] started ! \n\n");
        }

        final long sendConfStart = Utils.nanoNow();
        try {
            Vector<ServiceItem> f = FarmCommunication.getFarms();
            MonMessageClientsProxy mm = new MonMessageClientsProxy(monMessage.PROXY_MLSERVICES_TAG, null, f, null);
            ptw.sendMsg(mm);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got Exception while trying to send farms to the client ... ", t);
        }// try - catch

        try {
            startSendConfsThread(groupsInterestedIn);
        } catch (Exception exp) {
            logger.log(Level.WARNING, "Exception sending configurations ... :(");
            return false;
        } // try - catch

        if (infoToFile.enabled()) {
            infoToFile.writeToFile((new Date()) + "[Client] [" + myName + "] "
                    + " send available farms and configurations took: "
                    + TimeUnit.NANOSECONDS.toMillis(Utils.nanoNow() - sendConfStart)
                    + " milliseconds and nr. of bytes: " + (getSentBytes() / 1000000d) + "\n\n");
        }
        return true;
    } // commInit

    public boolean isInterstedForConfigMsg(final ServiceID sID) {
        final Set<String> v = FarmCommunication.getFarmGroups(sID);

        for (final String s : v) {
            if (groupsInterestedIn.contains(s)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void processMsg(monMessage o) {
        ptw.sendMsg(o);
    }

    @Override
    public void processMsg(MonMessageClientsProxy o) {
        ptw.sendMsg(o);
    }

    public void processMsg1(Object o) {
        try {
            // First send the message and after that do the logging ...

            /**
             * ---------------------------------------------------------- log in a file how many messages came on this
             * connection
             */
            if (infoToFile.enabled() && ((System.currentTimeMillis() - lastUpdateFileInfo) >= 3600000)) {
                long nrMsg = msgsLastHourContor.getAndSet(0L);

                lastUpdateFileInfo = System.currentTimeMillis();

                long sizeMsgs = getSentBytes();

                long lastHourMsgsSize = 0;

                if ((lastSizeMsg == 0) || (lastSizeMsg > sizeMsgs)) {
                    lastHourMsgsSize = sizeMsgs;
                } else {
                    lastHourMsgsSize = sizeMsgs - lastSizeMsg;
                } // if = else
                lastSizeMsg = sizeMsgs;

                sizeMsgs = getConfSentBytes();
                long lastHourConfSize = 0;
                if ((lastConfSizeMsg == 0) || (lastConfSizeMsg > sizeMsgs)) {
                    lastHourConfSize = sizeMsgs;
                } else {
                    lastHourConfSize = sizeMsgs - lastConfSizeMsg;
                } // if = else
                lastConfSizeMsg = sizeMsgs;

                infoToFile.writeToFile((new Date()) + " : " + strToFile + nrMsg + "\n\t\t [size] "
                        + (lastHourMsgsSize / 1000000d) + "\n\t\t [conf size] " + (lastHourConfSize / 1000000d)
                        + "\n\n");
            } // if it passed an hour, write the log information to file

            /**
             * finished logging into the file --------------------------------------------------------------
             */

            /*
             * ------------------------------------------------------------ send with ApMon client msgs rate
             */
            if ((System.currentTimeMillis() - lastClientMsgRate) >= 60000) {
                long sizeMsgs = getSentBytes();

                long lastMinMsgRate = 0;

                if ((lastRateMsg == 0) || (lastRateMsg > sizeMsgs)) {
                    lastMinMsgRate = sizeMsgs;
                } else {
                    lastMinMsgRate = sizeMsgs - lastRateMsg;
                } // if = else
                lastRateMsg = sizeMsgs;

                final double totalR = lastMinMsgRate / 1000000;

                sizeMsgs = getConfSentBytes();
                long lastMinConfRate = 0;
                if ((lastConfRate == 0) || (lastConfRate > sizeMsgs)) {
                    lastMinConfRate = sizeMsgs;
                } else {
                    lastMinConfRate = sizeMsgs - lastConfRate;
                } // if - else
                lastConfRate = sizeMsgs;

                final double confR = lastMinConfRate / 1000000d;
                ClientsCommunication.addClientRate(myID, totalR, confR);

                lastClientMsgRate = System.currentTimeMillis();
            } // if

        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ ClientWorker ] got exc processing msg ", t);
        }
    }

    public String getName() {
        return myName;
    } // getName

    public long getStartTime() {
        return startTime;
    } // getStartTime

    @Override
    public String toString() {
        return "ClientWorker [ " + myName + " ]";
    } // toString

    public synchronized void startSendConfsThread(Set<String> groups) throws Exception {

        StringBuilder sb = new StringBuilder();
        final boolean isFinest = logger.isLoggable(Level.FINEST);
        final boolean isFiner = isFinest || logger.isLoggable(Level.FINER);
        final boolean isFine = isFiner || logger.isLoggable(Level.FINER);

        final boolean supportsCommpression = this.compressed.get();

        if (isFine) {
            sb.append("\n\n [ ClientWorker ] [ Compress ] Started for: ").append(myName)
                    .append(" startSendConfsThread for:  ").append(groups);
        }

        final Map<ServiceID, MFarmClientConfigInfo> chm = ClientsCommunication.getFarmsConfByGrps(groups);

        final Vector<MonMessageClientsProxy> confs = new Vector<MonMessageClientsProxy>();

        // send configurations
        for (final MFarmClientConfigInfo mm : chm.values()) {
            if (mm == null) {
                continue;
            }
            final MonMessageClientsProxy mmcp = mm.getConfigMessage();
            if (mmcp == null) {
                continue;
            }
            if (supportsCommpression) {
                if (mmcp.result != null) {
                    confs.add(mmcp);
                } // if
            } else {
                ptw.sendMsg(mmcp);
            } // if - else
              // if (mm != null) ptw.sendMsg(mm);
        } // for

        if (supportsCommpression) {
            // serialize confs vector ...
            long startCompress = Utils.nanoNow();
            if (isFiner) {
                sb.append("\n Before Compress Free Mem: ").append(Runtime.getRuntime().freeMemory())
                        .append(" TotalMem: ").append(Runtime.getRuntime().totalMemory());
            }
            ByteArrayOutputStream cBaos = new ByteArrayOutputStream();
            try {

            } finally {
                try {
                    cBaos.close();
                } catch (IOException e) {
                } // try - catch
            }

            Deflater compressor = new Deflater();
            compressor.setLevel(Deflater.BEST_SPEED);
            DeflaterOutputStream dos = new DeflaterOutputStream(cBaos, compressor);
            ObjectOutputStream cOos = new ObjectOutputStream(dos);
            cOos.writeObject(confs);

            cOos.flush();
            compressor.finish();
            dos.finish();

            final byte[] compressedData = cBaos.toByteArray(); // bytes to be compressed

            final long dtCompressNanos = Utils.nanoNow() - startCompress;

            if (isFiner) {
                sb.append("\n After compress: Free Mem: ").append(Runtime.getRuntime().freeMemory())
                        .append(" TotalMem: ").append(Runtime.getRuntime().totalMemory());
                sb.append("\n Compressed size: ").append(compressedData.length).append("\n Compress Time = ")
                        .append(TimeUnit.NANOSECONDS.toMillis(dtCompressNanos)).append(" ms");
                sb.append("\n [ ClientWorker ] [ Compress ] Finish compress for: ").append(myName).append(" @ ")
                        .append(new Date().toString()).append("\n\n");
            }

            if (dtCompressNanos >= DT_NOTIFY_COMPRESS_DURATION_THRESHOLD_NANOS) {
                sb = new StringBuilder();
                sb.append("\n [ ClientWorker ] [ Compress ] Finish compress for: ").append(myName).append(" @ ")
                        .append(new Date().toString()).append("\n\n");
                sb.append("\n After compress: Free Mem: ").append(Runtime.getRuntime().freeMemory())
                        .append(" TotalMem: ").append(Runtime.getRuntime().totalMemory());
                sb.append("\n Compressed size: ").append(compressedData.length).append("\n Compress Time = ")
                        .append(TimeUnit.NANOSECONDS.toMillis(dtCompressNanos)).append(" ms");
                notifyCompressThreshold(sb.toString(), dtCompressNanos);
            }

            // create MonMessageClientsProxy
            MonMessageClientsProxy mm = new MonMessageClientsProxy(monMessage.ML_CONFIG_TAG, null, compressedData, null);
            ptw.sendMsg(mm);

            if (isFine) {
                sb.append("\n *********** All confs ( ").append(confs.size())
                        .append(") sent to client: ********* \n\n");
                if (isFiner) {
                    for (final MonMessageClientsProxy mmcp : confs) {
                        sb.append(((MFarm) mmcp.result).name).append(" ");
                        if (isFinest) {
                            sb.append("\n").append(MFarmConfigUtils.getMFarmDump((MFarm) mmcp.result)).append("\n");
                        }
                    }
                    sb.append("\n\n ************ END All confs sent to client: ************ \n\n");
                }
                logger.log(Level.FINE, sb.toString());
            }
        }
    } // startSendConfsthread

    private static final void notifyCompressThreshold(String msg, long durationNanos) {
        final long now = Utils.nanoNow();
        if ((lastErrNotify.get() + DT_NOTIFY_COMPRESS_THRESHOLD_NANOS) < now) {
            lastErrNotify.set(now);
            try {
                MailFactory.getMailSender().sendMessage(
                        "support@monalisa.cern.ch",
                        NOTIFY_COMPRESS_RCPTS,
                        "[proxy(" + ServiceCommunication.getServiceName() + ")] CONFIG COMPRESS THRESHOLD: "
                                + TimeUnit.NANOSECONDS.toMillis(durationNanos) + " ms", msg);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[notifyCompressThreshold] Got exception sending mail", t);
            }
        }
    }

} // ClientWorker
