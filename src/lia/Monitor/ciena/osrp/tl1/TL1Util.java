/*
 * $Id: TL1Util.java 7419 2013-10-16 12:56:15Z ramiro $
 * 
 * Created on Oct 26, 2007
 */
package lia.Monitor.ciena.osrp.tl1;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.telnet.CienaTelnet;

/**
 * Various helper funtions/
 * @author ramiro
 * 
 */
public class TL1Util {

    private static final Logger logger = Logger.getLogger(TL1Util.class.getName());

    public static final String OSRP_CTAG = "osrp";
    public static final String RTRV_OSRP_NODES_CMD = "rtrv-osrp-node::ALL:osrp;\n";
    public static final String RTRV_OSRP_CTPS_CMD = "rtrv-osrp-ctp::ALL:osrp;\n";
    public static final String RTRV_OSRP_LTPS_CMD = "rtrv-osrp-ltp::ALL:osrp;\n";
    public static final String RTRV_OSRP_ROUTEMETRIC_CMD = "rtrv-osrp-routemetric::ALL:osrp;\n";

    public static final String RTRV_ALL_STSPC_CMD = "rtrv-stspc:::osrp;\n";

    public static final String TL1_START_CODE = "M ";
    public static final String TL1_END_CODE = ";";

    public static final OsrpTL1Response[] getAllOsrpNodes() throws Exception {
        return execAndGet(RTRV_OSRP_NODES_CMD);
    }

    public static final OsrpTL1Response[] getAllOsrpLtps() throws Exception {
        return execAndGet(RTRV_OSRP_LTPS_CMD);
    }

    public static final OsrpTL1Response[] getAllOsrpCtps() throws Exception {
        return execAndGet(RTRV_OSRP_CTPS_CMD);
    }

    public static final OsrpTL1Response[] getAllSTSPCs() throws Exception {
        return execAndGet(RTRV_ALL_STSPC_CMD);
    }

    public static final OsrpTL1Response[] getAllRouteMetric() throws Exception {
        return execAndGet(RTRV_OSRP_ROUTEMETRIC_CMD);
    }

    private static final OsrpTL1Response[] execAndGet(final String tl1CMD) throws Exception {
        final long sTime = System.currentTimeMillis();
        long dtTl1 = 0;

        final StringBuilder logMsg = new StringBuilder();

        try {
            final CienaTelnet tl1Telnet = CienaTelnet.getMonitorInstance();

            final StringBuilder sb = tl1Telnet.doCmd(tl1CMD, OSRP_CTAG);

            dtTl1 = System.currentTimeMillis() - sTime;

            if (sb == null) {
                throw new NullPointerException("Null response from TL1 Telnet interface");
            }

            if (logger.isLoggable(Level.FINEST)) {
                logMsg.append("\n[ TL1Util ] [ execAndGet ] doCmd [ ").append(tl1CMD).append(" ] received:\n");
                logMsg.append(sb.toString());
            }

            BufferedReader reader = new BufferedReader(new StringReader(sb.toString()));
            String line = null;

            boolean started = false;

            final ArrayList osrpTL1Response = new ArrayList();
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!started) {
                    if (line.startsWith(TL1_START_CODE)) {
                        started = true;
                    }
                    continue;
                }

                if (line.startsWith(TL1_END_CODE)) {
                    break;
                }

                if (!line.startsWith(OsrpTL1Response.START_END_DELIMITER)) {
                    if (logger.isLoggable(Level.FINER)) {
                        logMsg.append("\n Ignoring line: [").append(line)
                                .append("] because it did not start with START_END_DELIMITER");
                    }
                    continue;
                }

                final OsrpTL1Response tl1Response = OsrpTL1Response.parseLine(line);

                if (logger.isLoggable(Level.FINER)) {
                    logMsg.append("\n").append("OsrpTL1Response for line: ").append(line).append(" -> ")
                            .append(tl1Response);
                }
                osrpTL1Response.add(tl1Response);
            }

            if (logger.isLoggable(Level.FINE)) {
                logMsg.append("\n[ TL1Util ] [ execAndGet ] doCmd [ ").append(tl1CMD).append(" ] returning: ")
                        .append(osrpTL1Response.size()).append(" values");
            }

            return (OsrpTL1Response[]) osrpTL1Response.toArray(new OsrpTL1Response[osrpTL1Response.size()]);
        } finally {
            if (logger.isLoggable(Level.FINE)) {
                logMsg.append("\n[ TL1Util ] [ execAndGet ] doCmd [ ").append(tl1CMD).append(" ]  DT wait CD/CI: ")
                        .append(dtTl1).append(" ms  Total DT: ").append(System.currentTimeMillis() - sTime)
                        .append(" ms\n");
                logger.log(Level.FINE, logMsg.toString());
            }
        }
    }

    public static final String getStringVal(final String key, final OsrpTL1Response osrpTL1Response) {
        if ((osrpTL1Response == null) || (key == null)) {
            return null;
        }

        return (String) osrpTL1Response.paramsMap.get(key);
    }

    public static final int getIntVal(final String key, final OsrpTL1Response osrpTL1Response) {

        if ((key == null) || (osrpTL1Response == null) || (osrpTL1Response.paramsMap == null)) {
            return -1;
        }

        final Map map = osrpTL1Response.paramsMap;
        int retV = -1;

        try {
            retV = Integer.parseInt((String) map.get(key));
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "[ Tl1Util ] [ getIntVal ] Unable to determine the integer value for Key: "
                        + key + " from OsrpTL1Response: " + osrpTL1Response, t);
            }
            retV = -1;
        }

        return retV;
    }

    public static final long getLongVal(final String key, final OsrpTL1Response osrpTL1Response) {

        if ((key == null) || (osrpTL1Response == null) || (osrpTL1Response.paramsMap == null)) {
            return -1;
        }

        final Map map = osrpTL1Response.paramsMap;
        long retV = -1;

        try {
            retV = Long.parseLong((String) map.get(key));
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "[ Tl1Util ] [ getLongVal ] Unable to determine the long value for Key: " + key
                        + " from OsrpTL1Response: " + osrpTL1Response, t);
            }
            retV = -1;
        }

        return retV;
    }
}
