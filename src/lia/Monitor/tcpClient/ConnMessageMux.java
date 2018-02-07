package lia.Monitor.tcpClient;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import lia.Monitor.JiniClient.CommonGUI.IpAddrCache;
import lia.Monitor.JiniClient.CommonJini.JiniClient;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MonMessageClientsProxy;
import lia.Monitor.monitor.monMessage;
import lia.Monitor.monitor.tcpConn;
import lia.Monitor.monitor.tcpConnNotifier;
import lia.util.ntp.NTPDate;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;

/**
 * @author catac
 */
public class ConnMessageMux implements Runnable, tcpConnNotifier {

    /** The Logger */
    protected static final Logger logger = Logger.getLogger(ConnMessageMux.class.getName());

    /**
     * 
     */
    public final tcpConn conn;

    /**
     * 
     */
    protected final MessageBuffer buffer;

    /**
     * 
     */
    public final Map<ServiceID, MonMessageClientsProxy> knownConfigurations;

    /**
     * 
     */
    public final JiniClient jiniClient;

    /**
     * 
     */
    protected final Map<ServiceID, MLSerClient> farmClients = new ConcurrentHashMap<ServiceID, MLSerClient>();

    /**
     * 
     */
    protected final int Q_MAX_LEN = AppConfig.geti("lia.Monitor.tcpClient.ConnMessageMux.Q_MAX_LEN", 15000);

    // private double Q_PURGE_RATE = .75;
    // protected Vector buff1;
    // protected Vector buff2;
    // protected Object buffSync;

    private final BlockingQueue<MonMessageClientsProxy> msgQueue = new ArrayBlockingQueue<MonMessageClientsProxy>(
            Q_MAX_LEN);

    /**
     * 
     */
    protected final AtomicBoolean active = new AtomicBoolean(false);

    /**
     * 
     */
    protected final String myName;

    private final AtomicInteger msgCnt = new AtomicInteger(0);

    /**
     * 
     */
    public long proxyLongIP = 0;

    /** show debug info about received configurations */
    protected boolean bConfigReport;

    /** debug variable for proxy buffer status check */
    static long lFirstMsgTime = -1;

    /** contains the address of proxy as IP */
    protected final String sIpAddress;

    /**
     * port the proxy is on, together with address identifies uniquely the proxy
     */
    protected final int nPort;

    /**
     * 
     */
    protected long connectionStartTime = -1;

    /** exposes a counter of received messages */
    private final AtomicLong inMsgCounter = new AtomicLong(0);

    /** exposes a counter of sent messages */
    private final AtomicLong outMsgCounter = new AtomicLong(0);

    private static final ExecutorService CONN_MUX_NOTIFIER_EXECUTOR = Executors
            .newCachedThreadPool(new ThreadFactory() {
                private final AtomicInteger SEQ = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "(ML) ConnMuxWorker [ " + SEQ.incrementAndGet() + " ] ");
                }
            });

    private final ProcessConfigTask cfgTask;
    private final Object closeLock = new Object();

    // guarded by closeLock
    private final ArrayList<Future<?>> tasksToCancel = new ArrayList<Future<?>>();

    /**
     * @param address
     * @param port
     * @param knownConfiguration
     * @param jiniClient
     * @throws Exception
     */
    public ConnMessageMux(InetAddress address, int port, Map<ServiceID, MonMessageClientsProxy> knownConfiguration,
            JiniClient jiniClient) throws Exception {
        logger.log(Level.INFO, "[ ConnMsgMux ] Q_MAX_LEN: " + Q_MAX_LEN);
        this.knownConfigurations = knownConfiguration;
        this.jiniClient = jiniClient;
        this.myName = "ConnMessageMux for " + address + ":" + port;
        cfgTask = new ProcessConfigTask(this);
        sIpAddress = address.getHostAddress();
        nPort = port;
        byte[] bip = address.getAddress();
        for (byte element : bip) {
            long b = ((long) element) >= 0 ? element : 256L + element;
            proxyLongIP = (proxyLongIP << 8) + b;
            // System.out.println("i="+i+" br= "+bip[i]+" b="+b+"
            // l="+proxyLongIP);
        }

        // read config modifications reporting
        bConfigReport = AppConfig.getb("lia.Monitor.monitor.tmClient.ConfigReport", false);

        conn = tcpConn.newConnection(this, address, port);
        // set the start time for connection
        connectionStartTime = NTPDate.currentTimeMillis();
        if (conn != null) {
            active.set(true);
            buffer = new MessageBuffer(this, conn);
            logger.log(Level.INFO, "Request farms configurations for active groups.");
            /**
             * first message, for proxy to detect the type of client
             */
            // buffer.sendMsg(new MonMessageClientsProxy());
            /**
             * second message contains the groups selected so that the
             * configurations sent by the proxy should be less than before
             */
            buffer.sendMsg(jiniClient.createActiveGroupsMessage());
        } else {
            buffer = null;
            active.set(false);
        }
        // set the status to proxy as fine (message buffer status)
        jiniClient.setProxyMsgBufStatus(0);
    }

    /**
     * 
     */
    public final void startCommunication() {
        synchronized (closeLock) {
            if (!active.get()) {
                logger.log(Level.WARNING, "\n\n Start communication but already inactive ... " + myName);
                return;
            }
            tasksToCancel.add(CONN_MUX_NOTIFIER_EXECUTOR.submit(cfgTask));
            tasksToCancel.add(CONN_MUX_NOTIFIER_EXECUTOR.submit(buffer));
            tasksToCancel.add(CONN_MUX_NOTIFIER_EXECUTOR.submit(this));
            conn.startCommunication();
        }
    }

    /**
     * @return full address
     */
    public String getFullAddress() {
        String hostname = IpAddrCache.getHostName(sIpAddress, true);
        return (hostname == null ? sIpAddress : hostname) + ":" + nPort;
    }

    /**
     * @param farmID
     * @param farmCl
     */
    public void addFarmClient(ServiceID farmID, MLSerClient farmCl) {
        if ((farmID != null) && (farmCl != null)) {
            farmClients.put(farmID, farmCl);
            MonMessageClientsProxy mm = knownConfigurations.get(farmID);
            if (mm != null) {
                // System.out.println("<mluc> resending configuration for farm "
                // + farmCl.FarmName);
                notifyMessage(mm);
            }
        }
    }

    /**
     * @param farmID
     */
    public void removeFarmClient(ServiceID farmID) {
        if (farmID != null) {
            // tClient tclient = (tClient)farmClients.get (farmID);
            farmClients.remove(farmID);
            // catac: removing config when farm disappears
            knownConfigurations.remove(farmID);
        } // if
    } // removeFarmClient

    /**
     * @return true if there is a proxy connection
     */
    public boolean verifyProxyConnection() {
        return conn.isConnected();
    }

    /**
     * 
     */
    public void closeProxyConnection() {
        if (conn != null) {
            conn.close_connection();
        }
    }

    /**
     * returns the active state of the proxy connection: false means that the
     * connection is being closed, so no further transmissions should be made
     * with it
     * 
     * @return boolean value
     */
    public boolean isActive() {
        return active.get();
    }

    /**
     * sync notify message so that 2 messages could not be sent in the same time
     * from different threads
     */
    @Override
    public synchronized void notifyMessage(Object o) {
        if (active.get()) {
            process(o);
        }
    } // notifyMessage

    @Override
    public void notifyConnectionClosed() {
        logger.log(Level.INFO, "Connection with proxy closed for " + myName + ". Removing all farmClients ["
                + farmClients.size() + "]");
        final ArrayList<Future<?>> tasksToCancel = new ArrayList<Future<?>>();
        synchronized (closeLock) {
            active.set(false);
            tasksToCancel.addAll(this.tasksToCancel);
        }

        jiniClient.incFailedConns();
        farmClients.clear();
        knownConfigurations.clear();

        for (final Future<?> f : tasksToCancel) {
            f.cancel(true);
        }

    }

    /**
     * sends a message to proxy, and also recomputes statistics params and
     * informs the statistics of moddiffications
     * 
     * @param mess
     *            message to be sent
     */
    public void sendMsg(MonMessageClientsProxy mess) {
        if (mess == null) {
            return;
        }
        // if ( bDebug )
        // System.out.println("<mluc> <proxy connection> sending message to
        // proxy with tag "+mess.tag+" and content "+mess.result);
        // count the message as out message
        outMsgCounter.incrementAndGet();
        buffer.sendMsg(mess);
        // after send, update the statistics frame, only if it is visible!
        if ((jiniClient != null) && (jiniClient.mainClientClass != null)) {
            if (jiniClient instanceof lia.Monitor.JiniClient.CommonGUI.SerMonitorBase) {
                lia.Monitor.JiniClient.CommonGUI.SerMonitorBase smb = (lia.Monitor.JiniClient.CommonGUI.SerMonitorBase) jiniClient;
                if ((smb.main != null) && (smb.main.sMon != null)) {
                    smb.main.sMon.newOutValue(false);
                }
            }
        }

    } // sendMsg

    /**
     * @return when the connection was established
     */
    public long getStartTime() {
        return connectionStartTime;
    }

    /**
     * @return messages in
     */
    public long getInMsgCounterValue() {
        return inMsgCounter.get();
    }

    /**
     * @return bytes in
     */
    public long getInByteCounterValue() {
        if (conn != null) {
            return conn.getRecvBytes();
        }
        return 0;
    }

    /**
     * @return conf
     */
    public long getInByteConfCounterValue() {
        if (conn != null) {
            return conn.getConfRecvBytes();
        }
        return 0;
    }

    /**
     * @return messages out
     */
    public long getOutMsgCounterValue() {
        return outMsgCounter.get();
    }

    /**
     * @return bytes out
     */
    public long getOutByteCounterValue() {
        if (conn != null) {
            return conn.getSentBytes();
        }
        return 0;
    }

    /**
     * from received compressed config vector generates several notifyMessage
     * calls for each new configuration.
     * 
     * @param msg
     */
    @SuppressWarnings("unchecked")
    protected void processZippedConfigs(MonMessageClientsProxy msg) {
        Vector<MonMessageClientsProxy> v = null;
        try {
            // decompressed the serialized Vector of configurations
            v = (Vector<MonMessageClientsProxy>) decompressSerObj((byte[]) msg.result);
        } catch (Exception exp) {
            return;
        } // try - catch

        if (!active.get()) {
            return;
        }

        for (final MonMessageClientsProxy mm : v) {
            try {
                notifyMessage(mm);
            } catch (Throwable t) {
                t.printStackTrace();
            } // try - catch
        } // for
    }

    /**
     * @param obj
     */
    @SuppressWarnings("unchecked")
    public void process(final Object obj) {

        if (obj == null) {
            return;
        }
        if (!(obj instanceof MonMessageClientsProxy)) {
            logger.log(Level.WARNING, "ConnMessageMux: Received an unknown object  ignore it !!\n Obj = " + obj);
            jiniClient.incInvalidMsgCount();
            return;
        } // if
        MonMessageClientsProxy msg = (MonMessageClientsProxy) obj;
        ServiceID farmID = msg.farmID;
        // if ( bDebug )
        // if ( !msg.tag.equals(monMessage.ML_TIME_TAG) &&
        // !msg.tag.equals(monMessage.ML_VERSION_TAG) )
        // System.out.println("<mluc> <proxy connection> new message received
        // with tag="+msg.tag+", ident="+msg.ident+" and result="+msg.result);

        if (msg.tag.startsWith("proxy")) {
            if (msg.tag.equals("proxyBuffer")) {
                double x = 0;
                if (msg.result != null) {
                    try {
                        x = ((Double) msg.result).doubleValue();
                    } catch (Throwable t) {
                        x = 0;
                    } // try - catch
                    if (x >= 100) {
                        jiniClient.setBadConnection();
                    } // if
                } // if
                jiniClient.setProxyMsgBufStatus(x);
            } // if
            return; // treat the proxy messages only here
        } // if

        if (msg.tag.equals(monMessage.PROXY_MLSERVICES_TAG)) {
            final Vector<ServiceItem> farmServices = (Vector<ServiceItem>) msg.result;
            // logger.log(Level.INFO, "Farms notification");
            jiniClient.setAllFarms(farmServices);
            jiniClient.updateGroups();
            // count the message
            inMsgCounter.incrementAndGet();
            notifyStatistics();
            return;
        } // if

        if ((farmID == null) && msg.tag.startsWith(monMessage.ML_CONFIG_TAG)) {
            /*
             * case when the message with all configurations comes compressed
             */
            if (msg.result instanceof byte[]) {
                if (!discardConfig()) {
                    cfgTask.addMsg(msg);
                }

                return;
            }
        }

        if (farmID != null) {
            /**
             * if this message is new, count it, if it is sent by addFarmClient,
             * don't count it
             */
            boolean bShouldBeCount = true;
            if (msg.tag.startsWith(monMessage.ML_CONFIG_TAG)) {
                if (discardConfig()) {
                    return;
                }

                MonMessageClientsProxy oldMsg = knownConfigurations.get(farmID);
                if ((oldMsg != null) && (oldMsg.result == msg.result)) {
                    bShouldBeCount = false;
                }

                // see details about
                // configuration changes
                if (bConfigReport) {
                    MFarmHelper.configReport(oldMsg, msg);
                }
                // case of a normal configuration, not compressed
                // save new configuration for farm
                // check first if diff config
                if ((msg.result instanceof MFarm[]) && (((MFarm[]) msg.result).length == 2)) {
                    MFarm addFarm = null;
                    MFarm remFarm = null;
                    MFarm oldFarm = null;
                    try {
                        if (oldMsg == null) {
                            // logger.log(Level.INFO,
                            // "ConnMessageMux: Received diff config message before full config from farm "+msg.farmID);//+"; will insert in queue.");
                            // add this premature message to cfgThread to be
                            // processed later
                            cfgTask.addDiffMsg(msg);

                            // TODO this is NOT an unfailable procerdure, but
                            // for most use cases (diff msg vector with
                            // small size) will work just fine
                            return;
                        }

                        // just add and remove differences, this also should be
                        // counted as a message
                        addFarm = ((MFarm[]) msg.result)[0];
                        remFarm = ((MFarm[]) msg.result)[1];
                        oldFarm = (MFarm) oldMsg.result;

                        // UNCOMMENT THIS for diff config debugging
                        // System.out.println(
                        // " addFarm Name: " + ((addFarm ==
                        // null)?" addFarm is null!":addFarm.name) +
                        // " remFarm Name: " + ((remFarm ==
                        // null)?" remFarm is null!":remFarm.name) +
                        // " oldFarm Name " + ((oldFarm ==
                        // null)?" oldFarm is null!":oldFarm.name)
                        // );
                        // if ( oldFarm.name.equals("devel_lnx_64") ) {
                        // System.out.println( "\n\n addFarm:\n" +
                        // MFarmConfigUtils.getMFarmDump(addFarm));
                        // MFarmHelper.farmSumReport(addFarm);
                        // System.out.println( "\n remFarm: "+
                        // MFarmConfigUtils.getMFarmDump(remFarm));
                        // MFarmHelper.farmSumReport(remFarm);
                        // System.out.println( " before oldFarm Name " +
                        // ((oldFarm ==
                        // null)?" oldFarm is null!":oldFarm.name));
                        // MFarmHelper.farmSumReport(oldFarm);
                        // }

                        MFarmHelper.removeClusters(oldFarm, remFarm);
                        MFarmHelper.addClusters(oldFarm, addFarm);

                        // if ( oldFarm.name.equals("devel_lnx_64") ) {
                        // System.out.println( "\n\n AFTER MFarmHelper:\n" +
                        // MFarmConfigUtils.getMFarmDump(oldFarm));
                        // }

                        // if ( oldFarm.name.equals("CIT_CMS_T2") ) {
                        // System.out.println( " addFarm Name: " + ((addFarm ==
                        // null)?" addFarm is null!":addFarm.name)
                        // );
                        // MFarmHelper.farmSumReport(addFarm);
                        // System.out.println( " remFarm Name: " + ((remFarm ==
                        // null)?" remFarm is null!":remFarm.name));
                        // MFarmHelper.farmSumReport(remFarm);
                        // System.out.println( " after oldFarm Name " +
                        // ((oldFarm ==
                        // null)?" oldFarm is null!":oldFarm.name));
                        // MFarmHelper.farmSumReport(oldFarm);
                        // }
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " Got exception for: " + " addFarm Name: "
                                + ((addFarm == null) ? " addFarm is null!" : addFarm.name) + " remFarm Name: "
                                + ((remFarm == null) ? " remFarm is null!" : remFarm.name) + " oldFarm Name "
                                + ((oldFarm == null) ? " oldFarm is null!" : oldFarm.name), t);
                        // this will close the connection with the proxy!
                        throw new RuntimeException(t);
                    }
                    // msg becomes oldMsg, and the oldMsg will be readded
                    // it is equivalent to sending a new config that also
                    // contains the old values
                    msg = oldMsg;
                }
                // msg.result instanceof MFarm
                // check...
                if (!(msg.result instanceof MFarm)) {
                    // logger.log(Level.INFO,
                    // "ConnMessageMux: Received config message is not of MFarm type "+msg.farmID);
                    return;
                }

                knownConfigurations.put(farmID, msg);

            }
            // System.out.println("M: "+msg.tag+" "+msg.ident+" "+msg.result);
            // int sz = sizeof.getSize(msg);
            // totSize += sz;
            /** if this is not sent by addFarmClient, count it */
            if (bShouldBeCount) {
                // count the message
                inMsgCounter.incrementAndGet();
                notifyStatistics();
                msgCnt.incrementAndGet();
            }
            // long t2 = NTPDate.currentTimeMillis();
            // if(t1 + 1000 < t2){
            // avgSize = totSize / msgCnt;
            // System.out.println("received "+msgCnt+" msg for clients in
            // "+(t2-t1)+" millis"+" avgSize="+avgSize+" totSize="+totSize);
            // t1 = t2;
            // msgCnt = 0;
            // totSize = 0;
            // }
            // if(msg != null)
            // return;
            if (jiniClient.testProxyBuff()) {
                // if ( lFirstMsgTime==-1 )
                // lFirstMsgTime = NTPDate.currentTimeMillis();
                // if ( NTPDate.currentTimeMillis()-lFirstMsgTime > 30000 ) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ex) {
                    // TODO Auto-generated catch block
                    ex.printStackTrace();
                }
                // }
            }
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "Received msg for clients " + msg);
            }

            enqueueMessageForClients(msg);

        } else {
            // count the message
            inMsgCounter.incrementAndGet();
            notifyStatistics();
            logger.log(Level.INFO, "ConnMessageMux: Received message from unknow farm.");
        }
    } // process

    /**
     * 
     */
    protected void notifyStatistics() {
        // nothing yet
    }

    /**
     * @return <code>true</code> if the configuration should be discarded
     */
    protected boolean discardConfig() {
        return false;
    }

    /**
     * @param compressedData
     * @return the decompressed object
     * @throws Exception
     */
    public Object decompressSerObj(final byte[] compressedData) throws Exception {
        Object o = null;
        try {

            // if(logger.isLoggable(Level.FINE) ) {
            logger.info("Received compressed configurations vector.");
            // }
            // measure decompression time
            final long startNanos = System.nanoTime();
            // a compressed message ? decompress it
            // Create the decompressor and give it the data to
            // decompress
            Inflater decompressor = new Inflater();
            ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
            InflaterInputStream iis = new InflaterInputStream(bais, decompressor);
            ObjectInputStream ois = new ObjectInputStream(iis);
            o = ois.readObject();
            final long endNanos = System.nanoTime();

            logger.info("Decompression of configurations vector, initial size: " + decompressor.getTotalIn()
                    + " bytes, expanded size: " + decompressor.getTotalOut() + " bytes; it took "
                    + TimeUnit.NANOSECONDS.toMillis(endNanos - startNanos) + " ms.");

            ois.close();
            decompressor.end();
            decompressor = null;
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Got exception while decompressing config vector! ", ex);
        }

        /*
         * int nCommpressLength = compressedData.length; // Create an expandable
         * byte array to hold the // decompressed data ByteArrayOutputStream bos
         * = new ByteArrayOutputStream(nCommpressLength); // Decompress the data
         * byte[] buf = new byte[1024]; while (!decompressor.finished()) { try {
         * int count = decompressor.inflate(buf); bos.write(buf, 0, count); }
         * catch (DataFormatException e) { } } // Get the decompressed data
         * byte[] decompressedData = bos.toByteArray(); try { bos.close(); }
         * catch (IOException e) { } // decopressedData is a serialized vector.
         * deserialize // it! int nDecompressLength = decompressedData.length;
         * logger.log(Level.INFO, "Config Vector, compressed size: "
         * +nCommpressLength
         * +" bytes, decompressed size: "+nDecompressLength+" bytes.");
         * ByteArrayInputStream bais = new
         * ByteArrayInputStream(decompressedData); ObjectInputStream ois = new
         * ObjectInputStream(bais); Object o = ois.readObject(); try {
         * bais.close(); ois.close(); } catch (Throwable t) { }
         */
        return o;
    } // decompressSerObj

    /**
     * return and reset the number of messages received from last query
     * 
     * @return number of messages since the last query
     */
    public int getMessageCount() {
        return msgCnt.getAndSet(0);
    }

    /**
     * return the proxy's IP as a long value
     * 
     * @return proxy host name
     */
    public long getProxyLongIP() {
        return proxyLongIP;
    }

    /**
     * put this message in msgQueue - it will be sent to the corresponding
     * client when the ConnMessageMux thread runs
     */
    private void enqueueMessageForClients(MonMessageClientsProxy msg) {
        if (!active.get()) {
            logger.log(Level.WARNING, " [ ConnMessageMux ] Connection no longer active. Ignoring message: " + msg);
            return;
        }

        try {
            for (;;) {
                final boolean luckyMe = msgQueue.offer(msg, 20, TimeUnit.SECONDS);
                if (luckyMe) {
                    break;
                }
                logger.log(Level.WARNING, " [ ConnMessageMux ] Am I too slow? Too many messages in the Queue (  MAX="
                        + Q_MAX_LEN + ", SIZE=" + msgQueue.size() + " ). Timeout at msg " + msg);
            }
        } catch (InterruptedException ie) {
            // this can be used as flag to interrupt processing of messages.
            // This log can become an .INFO in the future.

            if (active.get()) {
                logger.log(Level.WARNING,
                        "\n\n [ ConnMessageMux ] [ enqueueMessageForClients ] [ HANDLED ] Caught InterruptedException processing message: "
                                + msg + " Active:" + active + ". Cause ", ie);
            } else {
                logger.log(Level.WARNING,
                        " [ ConnMessageMux ] [ HANDLED ] [ enqueueMessageForClients ] Ignoring message (connection no longer active): "
                                + msg + " Active:" + active);
            }

        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ ConnMessageMux ] [ enqueueMessageForClients ] Unable to add message " + msg
                    + " to processing queue. Active:" + active + ". Cause:", t);
        }

    }

    /**
     * use a different policy - each second, switch the addBuffer with the
     * working buffer
     */
    @Override
    public void run() {
        final Thread cThread = Thread.currentThread();
        final String initName = cThread.getName();

        try {
            cThread.setName(myName);

            // //////////////////////
            //
            // REDONE - June 2008
            // and again in July 2012
            //
            // //////////////////////
            try {
                while (active.get()) {
                    final MonMessageClientsProxy msg = msgQueue.poll(20, TimeUnit.SECONDS);
                    if (msg == null) {
                        continue;
                    }
                    final MLSerClient client = farmClients.get(msg.farmID);
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, " [ ConnMessageMux ] [ run ] notifying " + msg.tag + " for "
                                + msg.farmID + " client=" + client);
                    }
                    if (client != null) {
                        try {
                            client.notifyMessage(msg);
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, "Error notifiying message " + msg, t);
                        }
                    }
                }
            } catch (InterruptedException ie) {
                if (active.get()) {
                    logger.log(Level.SEVERE,
                            "\n\n [ SEVERE ] [ ConnMessageMux ] [ run ] [ HANDLED ] Caught InterruptedException processing message. Active:"
                                    + active + ". Cause ", ie);
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING,
                        "\n\n [ EXCEPTION ] [ ConnMessageMux ] [ run ] Unable to get message from processing queue. Active:"
                                + active + ". Cause:", t);
            }

            closeProxyConnection();
        } finally {
            cThread.setName(initName);
            logger.log(Level.INFO, " [ ConnMessageMux ] ( " + myName + " ) exits main loop. Active: " + active);
        }

    }

    /**
     * waits until a config vector is provided, it decompresses it
     * 
     * @author mluc
     */
    static final class ProcessConfigTask extends Thread {

        private final ConnMessageMux master;

        // private ArrayBlockingQueue
        private final LinkedBlockingQueue<MonMessageClientsProxy> vConfigs = new LinkedBlockingQueue<MonMessageClientsProxy>();

        /**
         * vector that contains all diff configs received from proxy before the
         * archived config vector is received
         */
        private final ConcurrentLinkedQueue<DiffMsgTimeStamp> vUnclaimedDiffConfigs = new ConcurrentLinkedQueue<DiffMsgTimeStamp>();

        /**
         * sleep for maximum 20 seconds before checking if there are diff
         * messages that should not be
         */
        private static final long maxWaitForConfig = TimeUnit.MINUTES.toNanos(2);

        ProcessConfigTask(ConnMessageMux master) {
            this.master = master;
        }

        /**
         * object that contains a diff message and the timestamp when it was
         * generated
         * 
         * @author mluc
         */
        private static final class DiffMsgTimeStamp {

            final long timestamp;

            final MonMessageClientsProxy msg;

            public DiffMsgTimeStamp(MonMessageClientsProxy m) {
                timestamp = System.nanoTime();
                msg = m;
            }
        }

        @Override
        public void run() {
            final Thread thisThread = Thread.currentThread();
            final String initName = thisThread.getName();
            final String iName = " Config Messages Vector Decompressing " + master.myName;
            // //////////////////////
            //
            // REDONE - June 2008
            // and again in July 2012
            //
            // //////////////////////

            final AtomicBoolean active = master.active;

            try {
                this.setName(initName + " - " + iName);
                try {
                    while (active.get()) {
                        final MonMessageClientsProxy m = vConfigs.poll(20, TimeUnit.SECONDS);
                        if (m == null) {
                            continue;
                        }
                        master.processZippedConfigs(m);
                        // check also the unclaimed diff configs
                        long lCurrentTime = System.nanoTime();
                        ArrayList<MonMessageClientsProxy> diffs = new ArrayList<MonMessageClientsProxy>();
                        for (Iterator<DiffMsgTimeStamp> it = vUnclaimedDiffConfigs.iterator(); it.hasNext();) {
                            final DiffMsgTimeStamp dmts = it.next();
                            final MonMessageClientsProxy msg = dmts.msg;
                            long timestamp = dmts.timestamp;
                            ServiceID farmID = msg.farmID;
                            final MonMessageClientsProxy oldMsg = master.knownConfigurations.get(farmID);
                            if (oldMsg != null) {
                                // farm is available
                                diffs.add(msg);
                                it.remove();
                            } else {// check to see if it was kept too long
                                    // in
                                    // queue
                                final long dtNanos = lCurrentTime - timestamp;
                                if (dtNanos > maxWaitForConfig) {
                                    logger.log(Level.WARNING, master.myName + " - No config for " + dmts.msg.farmID
                                            + " after " + TimeUnit.NANOSECONDS.toSeconds(maxWaitForConfig)
                                            + " seconds ... removing it without processing");
                                    // remove it without processing
                                    it.remove();
                                } else {
                                    logger.log(
                                            Level.INFO,
                                            master.myName + " - No config for " + dmts.msg.farmID + " after "
                                                    + TimeUnit.NANOSECONDS.toSeconds(dtNanos)
                                                    + " seconds ... will wait for "
                                                    + TimeUnit.NANOSECONDS.toSeconds(maxWaitForConfig) + " seconds");
                                }
                            }
                        }
                        for (MonMessageClientsProxy monMessageClientsProxy : diffs) {
                            master.notifyMessage(monMessageClientsProxy);
                        }
                    }
                } catch (InterruptedException ie) {
                    if (master.active.get()) {
                        logger.log(
                                Level.SEVERE,
                                "\n\n [ SEVERE ] [ ConnMessageMux ] [ ConfigThread ] [ HANDLED ] Caught InterruptedException processing message. Active:"
                                        + active.get() + ". Cause ", ie);
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING,
                            "\n\n [ EXCEPTION ] [ ConnMessageMux ] [ ConfigThread ] Unable to get message from processing queue. Active:"
                                    + active.get() + ". Cause:", t);
                }
            } finally {
                thisThread.setName(initName);
                master.closeProxyConnection();
                logger.log(Level.INFO, " [ ProcessConfigTask ] " + iName + " exits main loop. Active: " + active.get());
            }

        }

        /**
         * @param msg
         */
        public void addMsg(MonMessageClientsProxy msg) {
            vConfigs.add(msg);
        }

        /**
         * @param msg
         */
        public void addDiffMsg(MonMessageClientsProxy msg) {
            vUnclaimedDiffConfigs.add(new DiffMsgTimeStamp(msg));
        }

    }

}
