/**
 * 
 */
package lia.Monitor.Farm.Pathload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lia.Monitor.monitor.AppConfig;
import lia.util.MLProcess;
import lia.util.Pathload.client.ServletResponse;
import lia.util.ntp.NTPDate;

/**
 * This TimerTask will run the pathload_client, also, a watchdog will
 * keep the maximum measuring time at RunClient.maxExecTime
 * Data is sent back to the peerCache for processing.
 * 
 * @author heri
 *
 */
public class RunClient implements Runnable {

    private static final Logger logger = Logger.getLogger(RunClient.class.getName());

    /**
     * Maximum time the pathload_rcv client is allowed to run
     */
    public static final long maxExecTime = Long.parseLong(AppConfig.getProperty(
            "lia.Monitor.modules.monPathload.autoConfigTimeout", "10")) * 60 * 1000;

    /**
     *  Execution c`ommand. Pathload has been modified such -N stdout should write
     *  to stdout instead of a filem and -q should suppress all writing to stdout
     *  even errors.
     *  
     */
    private String execCmd;

    private final PeerCache peerCache;
    private final Pattern pattern;

    /**
     * Default constructor
     * DATE=20060327125110.975908 HOST=heri PROG=pathload LVL=Usage PATHLOAD.SNDR=141.85.99.136 PATHLOAD.FLEETS=9 
     * PATHLOAD.BYTES_RECV=11416800 PATHLOAD.ABWL=98.2Mbps PATHLOAD.ABWH=99.5Mbps PATHLOAD.EXTSTAT=350
     * 
     * @param peerCache
     */
    public RunClient(PeerCache peerCache) {
        String sPattern = "DATE=((.)*) HOST=((.)*) PROG=((.)*) LVL=((.)*) "
                + "PATHLOAD.SNDR=((.)*) PATHLOAD.FLEETS=((.)*) PATHLOAD.BYTES_RECV=((.)*) "
                + "PATHLOAD.ABWL=((.)*) PATHLOAD.ABWH=((.)*) PATHLOAD.EXTSTAT=((.)*)";
        pattern = Pattern.compile(sPattern, Pattern.UNIX_LINES | Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        this.peerCache = peerCache;
        this.execCmd = peerCache.getPathloadClientExecCmd();
    }

    /**
     * Default run method
     * TODO: Kill a  running pathload_rcv and report it
     * 
     */
    @Override
    public void run() {
        try {
            ServletResponse token = peerCache.getToken();
            if (token != null) {
                String destHost = convertIpAddr(token.getDestIp());

                this.execCmd = peerCache.getPathloadClientExecCmd();
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "[monPathload] RunClient acquired token. " + "Exec command is: " + execCmd
                            + " " + destHost + " 2>&1");
                }
                long startTime = NTPDate.currentTimeMillis();
                long stopTime = startTime;

                if (destHost == null) {
                    throw new IllegalArgumentException(
                            "[monPathload] RunClient tried to get ipAddress, but the ip is invalid.");
                }

                procOutput("killall -9 pathload_rcv");
                logger.log(Level.FINEST, "************************ START *********************");
                String output = procOutput(new String[] { "/bin/sh", "-c", execCmd + " " + destHost + " 2>&1 " });
                logger.log(Level.FINEST, "************************ STOP **********************");

                PathloadResult result = new PathloadResult(token.getDestIp(), token.getDestFarmName());
                result = parsePathloadOutput(output, result);
                stopTime = NTPDate.currentTimeMillis();
                result.setMeasurementDuration_value((stopTime - startTime) / 1000);

                peerCache.putResult(result);
                logger.log(Level.FINEST, "************************ Release Token START **********************");
                peerCache.releaseToken(token.getID());
                logger.log(Level.FINEST, "************************ Release Token STOP **********************");
            }
        } catch (IllegalArgumentException e) {
            logger.log(Level.FINE, e.getMessage());
        }
    }

    /**
     * Create a MLProcess and run my command. MLProcess uses a watchdog to control
     * my app.
     * 
     * @param command	Command to execute
     * @return			Command output
     */
    private String procOutput(String command) {
        if (command == null) {
            return null;
        }
        return procOutput(new String[] { "/bin/sh", "-c", command });
    }

    /**
     * Create a MLProcess and run my command. MLProcess uses a watchdog to control
     * my app.
     * 
     * @param command	Command to execute
     * @return			Command output
     */
    private String procOutput(String[] command) {
        String sResult = null;

        if (command == null) {
            return null;
        }

        try {
            Process p = MLProcess.exec(command, maxExecTime);
            InputStream is = p.getInputStream();
            OutputStream os = p.getOutputStream();
            os.close();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buff = new byte[1024];
            int count = 0;

            while ((count = is.read(buff)) > 0) {
                baos.write(buff, 0, count);
            }
            p.waitFor();
            baos.close();
            sResult = baos.toString();

        } catch (IOException e) {
            logger.log(Level.SEVERE, "[monPathload] RunClient IOException while running " + command);
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "[monPathload] RunClient caught interruptedException while waiting for " + command
                    + "to finish.");
        }

        return sResult;
    }

    /**
     * Converts a host name from canonical address to an IP
     * Address. If this is not possible it returns null.
     * 
     * @param destHost	Destination host
     * @return			Destination Host IP or null if the 
     * 					is not possible
     */
    private String convertIpAddr(String destHost) {
        String sResult = null;

        if (destHost == null) {
            return null;
        }
        try {
            sResult = InetAddress.getByName(destHost).getHostAddress();
        } catch (UnknownHostException e) {
            logger.log(Level.SEVERE, "Could not determine IP Address from " + destHost + ". UnknownHostException.");
        }
        return sResult;
    }

    /**
     * Parse pathload output with regex and return a lia.monitor.Result.
     * 
     * @param output		Output of pathload_rcv			
     * @return 				Min and Max Available Bandwidth
     */
    private PathloadResult parsePathloadOutput(String inputStr, PathloadResult result) {
        if (result == null) {
            logger.log(Level.FINEST, "Debug: Result is null");
            return null;
        }
        if (inputStr == null) {
            result.setExitStat_value(PathloadResult.MEASUREMENT_STATUS_FAILED);
            logger.log(Level.FINEST, "Debug: input String is null");
            return result;
        }

        try {
            Matcher matcher = pattern.matcher(inputStr);

            if (matcher.find()) {
                logger.log(Level.FINEST, "Input string is: " + inputStr);
                result.setFleets_value(matcher.group(11));
                result.setBytesRecv_value(matcher.group(13));
                result.setAwbwLow_value(matcher.group(15));
                result.setAwbwHigh_value(matcher.group(17));
                result.setExitStat_value(matcher.group(19));
            } else {
                logger.log(Level.FINEST, "No Match! Input string is: " + inputStr);
            }
        } catch (NumberFormatException e) {
            logger.log(Level.SEVERE, "NumberFormatException while parsing Pathload output.");
        }
        return result;
    }
}
