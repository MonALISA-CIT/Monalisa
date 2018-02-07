package lia.util.ntp;

/*
 * Connects to 4 NTP servers and calculates the time offset
 */

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;

class NTPClient {

    private static final Logger logger = Logger.getLogger(NTPClient.class.getName());

    private static final long SEVENTY_OFFSET; // offset (in ms) between 1900 and 1970

    static {
        long seventyOffset = 70 * 365; // days in 70 years
        seventyOffset += 17; // add days for leap years between 1900 and 1970
        seventyOffset *= 24; // hours in a day
        seventyOffset *= 60; // minutes in an hour
        seventyOffset *= 60; // seconds in a minute
        seventyOffset *= 1000; // milliseconds in a second
        SEVENTY_OFFSET = seventyOffset;
    }

    // private static final String DEFAULT_vsServers[] = {
    // "130.149.17.21",
    // "192.53.103.104",
    // "131.188.3.220",
    // "193.204.114.231"
    // };

    private static final String DEFAULT_vsServers[] = { "0.pool.ntp.org", "1.pool.ntp.org", "2.pool.ntp.org",
            "3.pool.ntp.org" };

    private static String vsServers[] = DEFAULT_vsServers;

    static {
        try {
            vsServers = AppConfig.getVectorProperty("lia.util.ntp.TIME_SERVERS");
        } catch (Throwable t) {
            vsServers = null;
        }
        if (vsServers == null) {
            vsServers = DEFAULT_vsServers;
        }
        if (logger.isLoggable(Level.FINE)) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            for (int i = 0; i < vsServers.length; i++) {
                sb.append(vsServers[i]).append(i < (vsServers.length - 1) ? "," : "");
            }
            sb.append("}");
            logger.log(Level.FINE, "NTPClient :- Using: " + sb.toString());
        }
    }

    byte[] NTPData;

    private final int NTPPort = 123; // NTP always uses port 123

    // Offsets in NTPData for each timestamp
    private final byte referenceOffset = 16;

    private final byte originateOffset = 24;

    private final byte receiveOffset = 32;

    private final byte transmitOffset = 40;

    private final byte refIDOffset = 12;

    private long transmitMillis;

    private long destinationTimestamp;

    private byte NTPleap;

    private byte NTPversion;

    private byte NTPmode;

    private byte NTPstratum;

    private byte NTPpoll;

    private byte NTPprecision;

    private long referenceTimestamp;

    private long originateTimestamp;

    private long receiveTimestamp;

    private long transmitTimestamp;

    private int counter;

    private final long[] localOffset;

    private final long[] rtDelay;

    private final boolean[] validResponse;

    private String refID;

    private String ipAddr;

    public NTPClient() {
        NTPData = new byte[48];
        localOffset = new long[vsServers.length];
        rtDelay = new long[vsServers.length];
        validResponse = new boolean[vsServers.length];

    }

    private void setTransmitTime() {
        GregorianCalendar startCal = new GregorianCalendar();
        long startMillis = startCal.getTimeInMillis();
        transmitMillis = startMillis + SEVENTY_OFFSET;
        toBytes(transmitMillis, transmitOffset);
    }

    public void toBytes(long n, int offset) {
        long intPart = 0;
        long fracPart = 0;
        intPart = n / 1000;
        fracPart = ((n % 1000) / 1000) * 0X100000000L;

        NTPData[offset + 0] = (byte) (intPart >>> 24);
        NTPData[offset + 1] = (byte) (intPart >>> 16);
        NTPData[offset + 2] = (byte) (intPart >>> 8);
        NTPData[offset + 3] = (byte) (intPart);

        NTPData[offset + 4] = (byte) (fracPart >>> 24);
        NTPData[offset + 5] = (byte) (fracPart >>> 16);
        NTPData[offset + 6] = (byte) (fracPart >>> 8);
        NTPData[offset + 7] = (byte) (fracPart);

    }

    public long toLong(int offset) {

        long intPart = ((((long) NTPData[offset + 3]) & 0xFF)) + ((((long) NTPData[offset + 2]) & 0xFF) << 8)
                + ((((long) NTPData[offset + 1]) & 0xFF) << 16) + ((((long) NTPData[offset + 0]) & 0xFF) << 24);

        long fracPart = ((((long) NTPData[offset + 7]) & 0xFF)) + ((((long) NTPData[offset + 6]) & 0xFF) << 8)
                + ((((long) NTPData[offset + 5]) & 0xFF) << 16) + ((((long) NTPData[offset + 4]) & 0xFF) << 24);
        long millisLong = (intPart * 1000) + ((fracPart * 1000) / 0X100000000L);

        return millisLong;
    }

    private void getLeap() { // The Leap Indicator is the first 2 bits of the first byte

        NTPleap = (byte) (NTPData[0] >> 6);
    }

    private void getVersion() {
        NTPversion = (byte) ((NTPData[0] & 0X38) >> 3);

    }

    private void getMode() {
        NTPmode = (byte) (NTPData[0] & 0X7);
    }

    private void getStratum() {
        NTPstratum = NTPData[1];
    }

    private void getPoll() {
        NTPpoll = NTPData[2];
    }

    private void getPrecision() {
        NTPprecision = NTPData[3];
    }

    private void getRefID() {
        refID = "";

        for (int i = 0; i <= 3; i++) {
            refID = refID.concat(String.valueOf((char) NTPData[refIDOffset + i]));
        }
    }

    private void getRefTimestamp() {
        referenceTimestamp = toLong(referenceOffset);
    }

    private void getOrigTimestamp() {
        originateTimestamp = toLong(originateOffset);
    }

    private void getRcvTimestamp() {
        receiveTimestamp = toLong(receiveOffset);
    }

    private void getTransTimestamp() {
        transmitTimestamp = toLong(transmitOffset);
    }

    private void getDelay() {
        long T1 = originateTimestamp;
        long T2 = receiveTimestamp;
        long T3 = transmitTimestamp;
        long T4 = destinationTimestamp;
        if (T1 != transmitMillis) {
            T1 = transmitMillis;
        }

        rtDelay[counter] = ((T4 - T1) - (T2 - T3));

    }

    private void getLocalOffset() {
        long T1 = originateTimestamp;
        long T2 = receiveTimestamp;
        long T3 = transmitTimestamp;
        long T4 = destinationTimestamp;
        if (T1 != transmitMillis) {
            T1 = transmitMillis;
        }
        localOffset[counter] = ((((T2 - T1) + (T3 - T4))) / 2);

    }

    private int checkNTPerrors() {
        if (NTPleap == 3) {
            logger.log(Level.FINE, "Aborting: NTP server is unsynchronized");
            return -1;
        }

        if (NTPmode != 4) {
            logger.log(Level.FINE, "Aborting: NTP server not in server mode");
            return -1;
        }

        if (NTPstratum != 1) {
            logger.log(Level.FINE, "NTP is not a primary reference");
            // return -1;
        }
        return 0;
    }

    private int iValidServersCount = 0;

    private long sumResults() {
        long offset = 0;
        long delay = 0;

        int nr = 0;

        for (int i = 0; i < vsServers.length; i++) {
            if (validResponse[i]) {
                offset += localOffset[i];
                delay += rtDelay[i];
                nr++;
            }
        }

        if (nr > 0) {
            offset = offset / nr;
        } else {
            logger.log(Level.WARNING, "Warning: No valid NTP server response!");
        }

        // startMillis = transmitMillis - seventyOffset;
        // newMillis = startMillis + offset;

        iValidServersCount = nr;

        return offset;
    }

    public int getValidServersCount() {
        return iValidServersCount;
    }

    private void retrieveData() {

        getLeap();
        getVersion();
        getMode();
        getStratum();
        if (checkNTPerrors() == 0) {
            validResponse[counter] = true;
            getPoll();
            getPrecision();
            getRefID();
            getRefTimestamp();
            getOrigTimestamp();
            getRcvTimestamp();
            getTransTimestamp();
            getDelay();
            getLocalOffset();
        } else {
            logger.log(Level.WARNING, "Error for server " + counter + " (" + ipAddr + ")");
            validResponse[counter] = false;
        }
    }

    private void setServer(int servNum) {
        ipAddr = vsServers[servNum];
    }

    public long NTPConnect() {
        try {

            for (counter = 0; counter < vsServers.length; counter++) {
                DatagramSocket NTPSocket = null;
                try {
                    setServer(counter); // get the IP address of the NTP server to use
                    initPacket(); // initialize the byte array being sent to NTP server
                    InetAddress IPAddress = InetAddress.getByName(ipAddr);
                    NTPSocket = new DatagramSocket();
                    DatagramPacket NTPPacket = new DatagramPacket(NTPData, NTPData.length, IPAddress, NTPPort);

                    try {
                        NTPSocket.setSoTimeout(5000);
                    } catch (Exception e) {
                    }

                    NTPSocket.send(NTPPacket); // send packet to NTP server
                    NTPSocket.receive(NTPPacket); // receive packet from NTP server

                    // record when the packet was received from the NTP server
                    destinationTimestamp = System.currentTimeMillis();
                    destinationTimestamp += SEVENTY_OFFSET;

                    NTPData = NTPPacket.getData(); // get NTP data from the buffer
                    retrieveData(); // get the data sent by the server

                } catch (Throwable e) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Time server [ " + counter + " ] cannot be reached:  ("
                                + vsServers[counter] + ")", e);
                    }
                } finally {
                    if (NTPSocket != null) {
                        try {
                            NTPSocket.close(); // close connection with NTP server
                        } catch (Throwable ignore) {
                        }
                    }
                }
            }

            return sumResults();
        } catch (Throwable e) {
            logger.log(Level.WARNING, "Exception in NTPConnect: ", e);
            return 0;
        }
    }

    /**
     * Check if the responses were valid. Look for at least two correct responses, and check if all the responses are at
     * most 10 seconds apart from the average value.
     */
    public boolean NTPok() {
        int countOk = 0;

        long average = 0;
        boolean firstIter = true;
        final Set<Integer> avgNotOkServI = new HashSet<Integer>();

        while (firstIter || (countOk <= 1)) {
            firstIter = false;

            for (int i = 0; i < vsServers.length; i++) {
                if (validResponse[i] && !avgNotOkServI.contains(Integer.valueOf(i))) {
                    countOk++;
                    average += localOffset[i];
                }
            }

            if (countOk <= 1) {
                return false;
            }

            average /= countOk;

            for (int i = 0; i < vsServers.length; i++) {
                if (validResponse[i] && (Math.abs(localOffset[i] - average) > 10000)) {
                    logger.log(Level.WARNING, "NTP responses sanity check failed : server " + vsServers[i]
                            + " thinks the offset is " + localOffset[i] + ", but the average value is " + average
                            + ". Recomputing avg without this server.");
                    avgNotOkServI.add(i);
                    continue;
                }
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER,
                        "Computed a reliable average for " + Arrays.toString(vsServers) + "\n avg=" + average
                                + ", countOk=" + countOk + "\n offsets=" + Arrays.toString(localOffset)
                                + ", validResponse=" + Arrays.toString(validResponse) + ", avgNotOkServI="
                                + avgNotOkServI.toString());
            }
            return true;
        }

        logger.log(Level.WARNING, "Unable to compute a reliable average for " + Arrays.toString(vsServers) + "\n avg="
                + average + ", countOk=" + countOk + "\n offsets=" + Arrays.toString(localOffset) + ", validResponse="
                + Arrays.toString(validResponse) + ", avgNotOkServI=" + avgNotOkServI.toString());
        return false;
    }

    private void initPacket() {
        NTPData[0] = 0x1B;
        for (int i = 1; i < 48; i++) {
            NTPData[i] = 0;
        }

        setTransmitTime();
    }

}
