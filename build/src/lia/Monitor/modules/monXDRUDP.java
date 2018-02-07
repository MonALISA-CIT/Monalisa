/*
 * $Id: monXDRUDP.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.modules;

import hep.io.xdr.XDRInputStream;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AccountingResult;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.GenericUDPResult;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.StringFactory;
import lia.util.Utils;
import lia.util.ntp.NTPDate;

/**
 * @author ramiro
 * @author Costin Grigoras
 * @author Catalin Cirstoiu
 */
public class monXDRUDP extends monGenericUDP {

    /**
     * 
     */
    private static final long serialVersionUID = 9208925666090673350L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monXDRUDP.class.getName());

    private static String XDR_MODULE_NAME = "monXDRUDP";

    /** The total number of datagrams received so far */
    int nTotalDatagrams = 0;

    /** Number of datagrams received since last report */
    int nDatagrams = 0;

    /** ApMon senders - used to identify packet loss */
    private final Map<Long, ApMonSender> apMonSenders;

    /**
     * If this is enabled, ApMon senders will be monitored.
     * This is set by defining the following line in ml.properties:
     * "lia.Monitor.modules.monXDRUDP.MONITOR_SENDERS = true"
     */
    private volatile boolean monitorApMonSenders = false;

    /** If monitorApMonSenders, when it published last statistics */
    private long lastStatsReportTime = 0;

    /** The total number of datagrams lost so far */
    private int nTotalLostDatagrams = 0;

    /** How many datagrams were lost since lastReportTime */
    private int nLostDatagrams = 0;

    /**
     * If no data is received from a sender for a longer period of time, that
     * sender is removed from the apMonSenders hash. This expiry interval is
     * by default 900 sec (15 min) and is set in ml.properties with:
     * "lia.Monitor.modules.monXDRUDP.SENDER_EXPIRE_TIME = 900"
     */
    long senderExpireTime = 900 * 1000;

    /**
     * This class identifies an ApMon sender and allows monitoring
     * the packet loss based on the sequence number of the received packets.
     * For each sender there is a circular buffer holding the last sequence
     * numbers for each sender.
     */
    class ApMonSender {

        private final int[] seqNr; // last heard sequence number

        private int lostPkts; // how many packets were lost since last summarize

        private long lastHeard; // when I heard last from this

        private final String ipAddress;

        private final int instanceID; // sender instance ID on that machine

        /**
         * Create and add to the internal hash a new ApMon Sender
         * 
         * @param ipAddress
         *            - its IP address
         * @param instanceID
         *            - its instance ID
         * @param crtSeqNr
         *            - the starting Sequence Number
         */
        ApMonSender(String ipAddress, int instanceID, int crtSeqNr) {
            // allocate a buffer used to store last received ApMon packets seq nr's
            seqNr = new int[AppConfig.geti("lia.Monitor.modules.monXDRUDP.SENDER_SEQNR_HISTORY", 10)];

            // initialize this so that the values in the buffer are
            // with one lenght of the buffer smaller thant what we expect
            // to receive from now on
            int idx = crtSeqNr % seqNr.length;
            for (int i = idx; i < (seqNr.length + idx); i++) {
                seqNr[i % seqNr.length] = (crtSeqNr - seqNr.length) + (i - idx);
            }

            this.ipAddress = ipAddress;
            this.instanceID = instanceID;
            // and finally,
            updateSeqNr(crtSeqNr);
            logger.log(Level.INFO, "Registering " + toString() + " seqNr: " + crtSeqNr);
        }

        /**
         * @param crtSeqNr
         */
        void updateSeqNr(final int crtSeqNr) {
            lastHeard = NTPDate.currentTimeMillis();
            int idx = crtSeqNr % seqNr.length;
            int oldSeqNr = seqNr[idx];
            if (oldSeqNr < crtSeqNr) {
                // number of lost packets
                lostPkts += ((crtSeqNr - oldSeqNr) / seqNr.length) - 1;
                seqNr[idx] = crtSeqNr;
            } else {
                // else we got even older packets. Too bad...
                logger.log(Level.WARNING, "Received packet with SeqNr older than length of the SEQ HISTORY"
                        + " buffer from " + ipAddress + " instance: " + instanceID + " got: " + crtSeqNr
                        + ", expecting SeqNr after: " + oldSeqNr);
            }
        }

        /**
         * @param now
         * @return <code>true</code> if expired
         */
        boolean isExpired(final long now) {
            if ((now - lastHeard) > senderExpireTime) {
                return true;
            }

            return false;
        }

        /**
         * @return log the number of lost packets and return the count
         */
        int summarizeLostPkts() {
            int lost = lostPkts;
            if (lostPkts > 0) {
                logger.log(Level.WARNING, "Lost " + lostPkts + " packets from IP: " + ipAddress + ", instance: "
                        + instanceID);
            }
            lostPkts = 0;
            return lost;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("ApMonSender ");
            sb.append(ipAddress).append(", instance: ").append(instanceID);
            sb.append(" lastHeard: ").append(new Date(lastHeard));
            return sb.toString();
        }
    }

    /**
     * 
     */
    public monXDRUDP() {
        super(XDR_MODULE_NAME);

        info.name = XDR_MODULE_NAME;
        OsName = "linux";
        isRepetitive = true;
        gPort = 8884; // default ApMon port; can be changed through given parameters (see monGenericUDP)

        apMonSenders = new Hashtable<Long, ApMonSender>();
        canSuspend = false;
    }

    @Override
    public void notifyData(int len, byte[] data, InetAddress source) {

        XDRInputStream xdrIS = null;

        GenericUDPResult gur = null;

        try {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "Datagram dump for len=" + len + ":\n" + Utils.hexDump(data, len));
            }
            xdrIS = new XDRInputStream(new ByteArrayInputStream(data));
            gur = new GenericUDPResult();

            gur.rtime = NTPDate.currentTimeMillis();

            // since ML 1.2.18
            // verify the password ... if sent
            boolean canProcess = (accessConf == null ? true : !accessConf.isPasswordDefined());
            String header = xdrIS.readString().trim();
            String version = null;
            byte majorVersion = -1;
            byte minorVersion = -1;
            byte maintenanceVersion = -1;

            xdrIS.pad();
            String c = header;

            int vi = header.indexOf("v:");
            int pi = header.indexOf("p:");

            if ((vi != -1) && (pi != -1)) {
                version = header.substring(2, pi);// first is v:

                // the version should be something like major[[.minor][.release]]|[-][_]lang
                String[] splittedVersion = version.split("(_|-)");
                if ((splittedVersion != null) && (splittedVersion.length > 0) && (splittedVersion[0] != null)
                        && (splittedVersion[0].length() > 0)) {
                    String realVersions[] = splittedVersion[0].split("\\.");
                    try {
                        majorVersion = Integer.valueOf(realVersions[0]).byteValue();
                        minorVersion = Integer.valueOf(realVersions[1]).byteValue();
                        maintenanceVersion = Integer.valueOf(realVersions[2]).byteValue();
                    } catch (Throwable t) {
                        // ignore
                    }
                }
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "Full Version = " + version + " => [" + majorVersion + "." + minorVersion
                            + "." + maintenanceVersion + "]");
                }

                // for now we ignore the version ... but in future versions maybe we'll need it
                String password = header.substring(pi + "p:".length()).trim();
                if ((accessConf != null) && (password != null)) {
                    canProcess = accessConf.checkPassword(password);
                }
                if (!canProcess) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "No Password matching...ignoring it");
                    }
                }
            }

            if (canProcess) {
                if ((majorVersion > 2) || ((majorVersion == 2) && (minorVersion >= 2))) {
                    // if ApMon version is at least 2.2.0, we will read the two new fields that identify the ApMon
                    // Sender
                    int instanceID = xdrIS.readInt();
                    int seqNr = xdrIS.readInt();

                    if (monitorApMonSenders) {
                        Long srcID = buildApMonSenderUID(source, instanceID);

                        synchronized (apMonSenders) {
                            ApMonSender apmSender = apMonSenders.get(srcID);
                            if (apmSender == null) {
                                apmSender = new ApMonSender(source.getHostAddress(), instanceID, seqNr);
                                apMonSenders.put(srcID, apmSender);
                            } else {
                                apmSender.updateSeqNr(seqNr);
                            }
                        }

                        gur.senderID = srcID.longValue();
                    }
                }

                if (majorVersion != -1) {
                    // if the packet defines a version, then we have to read the cluster name
                    // this is kept to be compatible with the first versions of ApMon which were
                    // not sending the version/password information and the cluster name was the
                    // first string in the packet.
                    c = xdrIS.readString();
                    xdrIS.pad();
                }

                String n = StringFactory.get(xdrIS.readString());

                xdrIS.pad();

                gur.clusterName = StringFactory.get(c);

                if ((accessConf != null) && !accessConf.checkClusterName(gur.clusterName)) {
                    logger.log(Level.INFO, " [ monXDRUDP ] ignoring UDP from  " + source + " . clusterName = "
                            + gur.clusterName);
                    return;
                }

                gur.nodeName = n;
                if ((accessConf != null) && !accessConf.checkNodeName(n)) {
                    logger.log(Level.INFO, " [ monXDRUDP ] ignoring UDP from  " + source + " . nodeName = "
                            + gur.nodeName);
                    return;
                }

                if (bAppendIPToNodeName) {
                    gur.nodeName += "_" + source.getHostAddress();
                    gur.addParam("NodeName", source.getHostAddress());
                }

                int nParams = xdrIS.readInt();
                xdrIS.pad();

                boolean error = false;
                for (int i = 0; !error && (i < nParams); i++) {
                    String paramName = StringFactory.get(xdrIS.readString());
                    xdrIS.pad();

                    if ((paramName == null) || (paramName.length() == 0)) {
                        break;
                    }

                    int paramType = xdrIS.readInt();
                    xdrIS.pad();

                    switch (paramType) {
                    case XDRMLMappings.XDR_STRING: {
                        String value = StringFactory.get(xdrIS.readString());
                        xdrIS.pad();
                        gur.addParam(paramName, value);
                        break;
                    }
                    case XDRMLMappings.XDR_INT32: {
                        int value = xdrIS.readInt();
                        xdrIS.pad();
                        gur.addParam(paramName, Double.valueOf(value));
                        break;
                    }
                    case XDRMLMappings.XDR_INT64: {
                        long value = xdrIS.readLong();
                        xdrIS.pad();
                        gur.addParam(paramName, Double.valueOf(value));
                        break;
                    }
                    case XDRMLMappings.XDR_REAL32: {
                        double value = xdrIS.readFloat();
                        xdrIS.pad();
                        gur.addParam(paramName, Double.valueOf(value));
                        break;
                    }
                    case XDRMLMappings.XDR_REAL64: {
                        double value = xdrIS.readDouble();
                        xdrIS.pad();
                        gur.addParam(paramName, Double.valueOf(value));
                        break;
                    }
                    default: {
                        error = true;
                    }
                    }// switch

                }// for

                if (!error && (len > xdrIS.getBytesRead())
                        && ((majorVersion >= 2) || ((majorVersion == 1) && (minorVersion >= 6)))) {
                    // Setting result time is supported since version 1.2.27
                    try {
                        long time = xdrIS.readInt();
                        time *= 1000;
                        xdrIS.pad();
                        if (time > 0) {
                            if (logger.isLoggable(Level.FINE)) {
                                logger.log(Level.FINE, " [ Generic UDP ] received timed result: " + time + " / "
                                        + new Date(time));
                            }
                            gur.rtime = time;
                        } else {
                            logger.log(Level.WARNING, " [ Generic UDP ] invalid time: " + time);
                        }
                    } catch (Exception ex) {
                        logger.log(Level.WARNING, " [ Generic UDP ] error while reading time for the result.");
                    }
                }

                if (!error) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, " [ Generic UDP ] adding GUR: " + gur);
                    }
                    genResults.add(gur);
                }
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(
                        Level.FINE,
                        " [ Generic UDP ] Exception while decoding UDP datagram at byte "
                                + (xdrIS != null ? xdrIS.getBytesRead() : -1) + " of " + len + ":\n"
                                + Utils.hexDump(data, len), t);

            }
            gur = null;
        } finally {
            if (xdrIS != null) {
                try {
                    xdrIS.close();
                } catch (Throwable t) {
                    // ignore
                }
                xdrIS = null;
            }
        }

        nDatagrams++;
    }

    @Override
    public Object doProcess() throws Exception {
        final Vector<Result> apMonStats = checkApMonSenders();

        List<GenericUDPResult> l = getResults();
        if (l == null) {
            return apMonStats;
        }
        Vector<Object> retV = new Vector<Object>();
        if (apMonStats != null) {
            retV.addAll(apMonStats);
        }
        for (final GenericUDPResult gur : l) {

            // //////////////////
            // TODO - ramiro
            // ------- Ar trebui refacuta bucata asta ......... e mult prea ca "la clasa a 5-a" !!! ( Nu prea e
            // folosita, sper )
            if (gur.nodeName.startsWith("#acc_")) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "XDR : Accounting info");
                }

                try {

                    final String sGroup = gur.clusterName;
                    final String sUser = gur.nodeName.substring(5);
                    final String sJobID = gur.paramValues.get(gur.paramNames.indexOf("jobid")).toString();

                    final long lStartTime = ((Number) gur.paramValues.get(gur.paramNames.indexOf("start"))).longValue() * 1000L;
                    final long lEndTime = ((Number) gur.paramValues.get(gur.paramNames.indexOf("stop"))).longValue() * 1000L;

                    AccountingResult ar = new AccountingResult(sGroup, sUser, sJobID, lStartTime, lEndTime);

                    if ((ar.sGroup != null) && (ar.sUser != null) && (ar.sJobID != null) && (ar.lStartTime > 0)
                            && (ar.lEndTime > 0)) {
                        ar.addParam("cpu_MHz", (Number) gur.paramValues.get(gur.paramNames.indexOf("cpu_MHz")));
                        ar.addParam("utime", (Number) gur.paramValues.get(gur.paramNames.indexOf("utime")));
                        ar.addParam("stime", (Number) gur.paramValues.get(gur.paramNames.indexOf("stime")));
                        ar.addParam("virtualmem", (Number) gur.paramValues.get(gur.paramNames.indexOf("virtualmem")));
                        ar.addParam("rss", (Number) gur.paramValues.get(gur.paramNames.indexOf("rss")));
                    }

                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "XDR : Returning : " + ar);
                    }

                    retV.add(ar);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "XDR : Throwable : ", t);
                }
            } else {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "XDR : normal Result");
                }

                Result r = new Result();
                r.FarmName = Node.getFarmName();
                r.ClusterName = gur.clusterName;
                r.time = gur.rtime;
                r.NodeName = gur.nodeName;
                r.Module = TaskName;

                eResult er = new eResult();
                er.FarmName = Node.getFarmName();
                er.ClusterName = gur.clusterName;
                er.time = gur.rtime;
                er.NodeName = gur.nodeName;
                er.Module = TaskName;

                if (bReportSenderID) {
                    r.addSet("SenderID", gur.senderID);
                }

                if (gur.paramValues != null) {
                    final int len = gur.paramValues.size();

                    for (int ip = 0; ip < len; ip++) {
                        final String key = (String) gur.paramNames.get(ip);
                        Object value = gur.paramValues.get(ip);
                        if (value != null) {
                            if (value instanceof Double) {
                                r.addSet(key, ((Double) value).doubleValue());
                            } else {
                                er.addSet(key, value);
                            }
                        }
                    }
                }

                if ((r.param != null) && (r.param.length > 0)) {
                    retV.add(r);
                }

                if ((er.param != null) && (er.param.length > 0)) {
                    retV.add(er);
                }
            }
        }

        return retV;
    }

    /**
     * Creates a ApMon-source ID that (hopefully) identifies uniquely an ApMon sender.
     * Its result is used as a key in the apMonSources hash.
     */
    private static Long buildApMonSenderUID(final InetAddress ipAddress, final int instanceID) {
        final byte[] ipBytes = ipAddress.getAddress();
        final long uid = ((ipBytes[0] & 0xffL) << 0) | ((ipBytes[1] & 0xffL) << 8) | ((ipBytes[2] & 0xffL) << 16)
                | ((ipBytes[3] & 0xffL) << 24) | ((instanceID & 0xffffffffL) << 32);
        return Long.valueOf(uid);
    }

    /**
     * Update the variables related to the monitoring of ApMon senders;
     * go through the list of ApMon senders and remove the expired ones.
     */
    private Vector<Result> checkApMonSenders() {
        monitorApMonSenders = AppConfig.getb("lia.Monitor.modules.monXDRUDP.MONITOR_SENDERS", false);
        senderExpireTime = AppConfig.getl("lia.Monitor.modules.monXDRUDP.SENDER_EXPIRE_TIME", 900) * 1000;
        Vector<Result> stats = null;

        if (monitorApMonSenders) {
            // check expired senders;
            long now = NTPDate.currentTimeMillis();
            int activeSenders = 0;
            synchronized (apMonSenders) {
                for (Iterator<ApMonSender> asit = apMonSenders.values().iterator(); asit.hasNext();) {
                    ApMonSender as = asit.next();
                    if (as.isExpired(now)) {
                        logger.log(Level.INFO, "Removing expired " + as.toString());
                        asit.remove();
                    } else {
                        activeSenders++;
                        nLostDatagrams += as.summarizeLostPkts();
                    }
                }
            }
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Monitoring " + activeSenders + " active ApMon senders on port " + gPort);
            }
            if ((now - lastStatsReportTime) >= (2 * 60 * 1000)) {
                double timeInterval = (now - lastStatsReportTime) / 1000.0;
                stats = new Vector<Result>();

                Result r = new Result();
                r.FarmName = Node.getFarmName();
                r.ClusterName = "MonaLisa";
                r.NodeName = "localhost";
                r.time = now;
                r.Module = TaskName;

                nTotalDatagrams += nDatagrams;
                nTotalLostDatagrams += nLostDatagrams;
                if ((nTotalDatagrams < 0) || (nTotalLostDatagrams < 0)) {
                    // reset counters on overflow
                    nTotalDatagrams = 0;
                    nTotalLostDatagrams = 0;
                }
                r.addSet("ApMon_" + gPort + "_lostDatagrams", nTotalLostDatagrams);
                r.addSet("ApMon_" + gPort + "_lostDatagrams_R", nLostDatagrams / timeInterval);
                r.addSet("ApMon_" + gPort + "_activeSenders", activeSenders);
                r.addSet("ApMon_" + gPort + "_receivedDatagrams", nTotalDatagrams);
                r.addSet("ApMon_" + gPort + "_receivedDatagrams_R", nDatagrams / timeInterval);

                stats.add(r);

                nDatagrams = 0;
                nLostDatagrams = 0;
                lastStatsReportTime = now;
            }
        } else {
            apMonSenders.clear();
        }
        return stats;
    }

    /**
     * Debug method
     * 
     * @param args
     */
    static public void main(String[] args) {
        String host = "localhost"; // args[0] ;

        monXDRUDP aa = new monXDRUDP();
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        aa.init(new MNode(host, ad, null, null), null);

        aa.bAppendIPToNodeName = true;

        for (;;) {
            try {
                Object bb = aa.doProcess();
                try {
                    Thread.sleep(1 * 1000);
                } catch (Exception e1) {
                    // ignore
                }

                if ((bb != null) && (bb instanceof Vector)) {
                    Vector<?> res = (Vector<?>) bb;
                    if (res.size() > 0) {
                        System.out.println("Got a Vector with " + res.size() + " results");
                        for (int i = 0; i < res.size(); i++) {
                            System.out.println(" { " + i + " } >>> " + res.elementAt(i));
                        }
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
