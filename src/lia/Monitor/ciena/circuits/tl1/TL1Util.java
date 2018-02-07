/*
 * $Id: TL1Util.java 7419 2013-10-16 12:56:15Z ramiro $
 * 
 * Created on dec 10, 2007
 */
package lia.Monitor.ciena.circuits.tl1;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ciena.tl1.TL1Response;
import lia.util.Utils;
import lia.util.telnet.CienaTelnet;

/**
 * 
 * Various helper funtions for SNCs
 * 
 * @author ramiro
 * 
 */
public class TL1Util {

    private static final Logger logger = Logger.getLogger(TL1Util.class.getName());

    public static final String CIRCUITS_CTAG = "sncs";

    public static final String RTRV_ALL_STSPC_CMD = "rtrv-stspc:::sncs;\n";
    public static final String RTRV_ALL_SNC_STSPC_CMD = "rtrv-snc-stspc::ALL:sncs;\n";
    public static final String RTRV_ALL_SNC_ROUTES_CMD = "rtrv-snc-route::ALL:sncs;\n";
    public static final String RTRV_ALL_VCG_CMD = "rtrv-vcg::ALL:sncs;\n";
    public static final String RTRV_SINGLE_VCG_CMD = "rtrv-vcg::";
    public static final String RTRV_SINGLE_SNC_STSPC_CMD = "rtrv-snc-stspc::";
    public static final String RTRV_ALL_CRS_CMD = "rtrv-crs:::sncs;\n";
    public static final String RTRV_ALL_GTP_CMD = "rtrv-gtp::ALL:sncs;\n";

    public static final String TL1_START_CODE = "M ";
    public static final String TL1_END_CODE = ";";

    public static final String UNLOCK_SNC_CMD_PREFIX = "ed-snc-stspc::";
    public static final String UNLOCK_SNC_CMD_SUFFIX = ":" + CIRCUITS_CTAG + "::PST=IS;\n";

    public static final String LOCK_SNC_CMD_PREFIX = "ed-snc-stspc::";
    public static final String LOCK_SNC_CMD_SUFFIX = ":" + CIRCUITS_CTAG + "::PST=OOS;\n";

    public static final TL1Response[] getAllSTSPCs() throws Exception {
        return execAndGet(RTRV_ALL_STSPC_CMD);
    }

    public static final TL1Response[] getAllSNCSTSPCs() throws Exception {
        return execAndGet(RTRV_ALL_SNC_STSPC_CMD);
    }

    public static final TL1Response[] getAllSNCRoutes() throws Exception {
        return execAndGet(RTRV_ALL_SNC_ROUTES_CMD);
    }

    public static final TL1Response[] getAllVCGs() throws Exception {
        return execAndGet(RTRV_ALL_VCG_CMD);
    }

    public static final TL1Response[] getAllCRSs() throws Exception {
        return execAndGet(RTRV_ALL_CRS_CMD);
    }

    public static final TL1Response[] getAllGTPs() throws Exception {
        return execAndGet(RTRV_ALL_GTP_CMD);
    }

    public static final TL1Response getSNC(final String sncName) throws Exception {
        return execAndGet(RTRV_SINGLE_SNC_STSPC_CMD + sncName + ":sncs;\n")[0];
    }

    public static final void lockSNC(final String sncName) throws Exception {
        execAndGet(LOCK_SNC_CMD_PREFIX + sncName + LOCK_SNC_CMD_SUFFIX);
    }

    public static final void unlockSNC(final String sncName) throws Exception {
        execAndGet(UNLOCK_SNC_CMD_PREFIX + sncName + UNLOCK_SNC_CMD_SUFFIX);
    }

    public static final TL1Response getVCG(final String vcgName) throws Exception {
        return execAndGet(RTRV_SINGLE_VCG_CMD + vcgName + ":sncs;\n")[0];
    }

    public static final TL1Response[] execAndGet(final String tl1CMD, String cTag) throws Exception {
        final long sTime = Utils.nanoNow();
        long dtTl1 = 0;

        final StringBuilder logMsg = new StringBuilder();

        try {
            final CienaTelnet tl1Telnet = CienaTelnet.getMonitorInstance();

            final StringBuilder sb = tl1Telnet.doCmd(tl1CMD, cTag);

            dtTl1 = Utils.nanoNow() - sTime;

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

            final ArrayList<TL1Response> osrpTL1Response = new ArrayList<TL1Response>();
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

                if (!line.startsWith(TL1Response.START_END_DELIMITER)) {
                    if (logger.isLoggable(Level.FINER)) {
                        logMsg.append("\n Ignoring line: [").append(line)
                                .append("] because it did not start with START_END_DELIMITER");
                    }
                    continue;
                }

                final TL1Response tl1Response = TL1Response.parseLine(line);

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

            return osrpTL1Response.toArray(new TL1Response[osrpTL1Response.size()]);
        } finally {
            if (logger.isLoggable(Level.FINE)) {
                logMsg.append("\n[ TL1Util ] [ execAndGet ] doCmd [ ").append(tl1CMD).append(" ]  DT wait CD/CI: ")
                        .append(TimeUnit.NANOSECONDS.toMillis(dtTl1)).append(" ms  Total DT: ")
                        .append(TimeUnit.NANOSECONDS.toMillis(Utils.nanoNow() - sTime)).append(" ms\n");
                logger.log(Level.FINE, logMsg.toString());
            }
        }
    }

    private static final TL1Response[] execAndGet(final String tl1CMD) throws Exception {
        return execAndGet(tl1CMD, CIRCUITS_CTAG);
    }

    public static final String getStringVal(final String key, final TL1Response osrpTL1Response) {
        if ((osrpTL1Response == null) || (key == null)) {
            return null;
        }
        return osrpTL1Response.paramsMap.get(key);
    }

    public static final int getIntVal(final String key, final TL1Response osrpTL1Response) {

        if ((key == null) || (osrpTL1Response == null) || (osrpTL1Response.paramsMap == null)) {
            return -1;
        }

        final Map<String, String> map = osrpTL1Response.paramsMap;
        int retV = -1;

        try {
            retV = Integer.parseInt(map.get(key));
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "[ Tl1Util ] [ getIntVal ] Unable to determine the integer value for Key: "
                        + key + " from OsrpTL1Response: " + osrpTL1Response, t);
            }
            retV = -1;
        }

        return retV;
    }

    public static final long getLongVal(final String key, final TL1Response osrpTL1Response) {

        if ((key == null) || (osrpTL1Response == null) || (osrpTL1Response.paramsMap == null)) {
            return -1;
        }

        final Map<String, String> map = osrpTL1Response.paramsMap;
        long retV = -1;

        try {
            retV = Long.parseLong(map.get(key));
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
