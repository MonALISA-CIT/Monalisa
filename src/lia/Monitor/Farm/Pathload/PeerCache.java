/**
 * 
 */
package lia.Monitor.Farm.Pathload;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;
import lia.app.pathload.AppPathload;
import lia.util.Pathload.client.ServletResponse;
import lia.util.ntp.NTPDate;

/**
 * The pathload module must do each of the following things: it must
 * get a configuration from the pathload servlet, it must report
 * results back to the ML core and it must ensure that results are
 * consistent (eliminate expired peers)
 * This is why at any time, the pathload module must have knowledge of the 
 * group with which it does the measurements.
 * 
 * This is the functionality of the PeerCache: it must be a glue between
 * the Thread that gets the configuration, the thread that executes the
 * actual pathload program and the main module that reads the results.
 * This also is the place where all data gets synchronized.
 * 
 * @author heri
 *
 */
public class PeerCache {
    /**
     * My Logger Component
     */
    private static final Logger logger = Logger.getLogger(PeerCache.class.getName());

    /**
     * Pathload Control Backend (Pathload Cache) is running 
     */
    public final static int PATHLOAD_CACHE_STATUS_RUNNING = 1;

    /**
     * Pathload Control Backend (Pathload Cache) is stopped
     */
    public final static int PATHLOAD_CACHE_STATUS_STOPPED = 0;

    /**
     * Default value for a not set integer property
     */
    public final static int NOT_SET = -1;

    /**
     * lia.monitor.Modules.monPathload.configTimer - Delay for the TimerThread for monPathload
     * 		to get his Configuration. Default is 30 sec.
     */
    public static final long PATHLOAD_CACHE_TIMER_CONFIG = Long.parseLong(AppConfig.getProperty(
            "lia.monitor.Modules.monPathload.configTimer", "30")) * 1000;

    /**
     * lia.monitor.Modules.monPathload.runTimer - Delay for the TimerThread for monPathload
     * 		to run the pathload client. Default is 30 sec.
     */
    public static final long PATHLOAD_CACHE_TIMER_RUN = Long.parseLong(AppConfig.getProperty(
            "lia.monitor.Modules.monPathload.runTimer", "30")) * 1000;

    /**
     * lia.monitor.Modules.monPathload.runServer - true or false. If monPathload should start
     * 		pathload_snd or not. Default: true
     */
    public static final boolean PATHLOAD_CACHE_RUN_SERVER = Boolean.valueOf(
            AppConfig.getProperty("lia.monitor.Modules.monPathload.runServer", "true")).booleanValue();

    /**
     * lia.monitor.Modules.monPathload.correctErrors - true or false. On some Gigabit Links Pathload
     * 		reports a maximum value greater than the link capacity. If this property is set to true,
     * 		and if it's the case, the result will be modified so neither value will exceed the link
     * 		capacity. Default: true
     */
    public static final boolean PATHLOAD_CORRECT_EVIDENT_ERRORS = Boolean.valueOf(
            AppConfig.getProperty("lia.monitor.Modules.monPathload.correctErrors", "true")).booleanValue();

    /**
     * If the result has min and max values and if the bandwidth is > 600 (Gigabit only)
     * then mark the result as errournous.
     */
    public static final double PATHLOAD_CACHE_ERROR_COEF = Double.parseDouble(AppConfig.getProperty(
            "lia.monitor.Modules.monPathload.errorCoef", "30"));

    /**
     * 
     */
    public static final double PATHLOAD_MIN_FLEETS = Integer.parseInt(AppConfig.getProperty(
            "lia.monitor.Modules.monPathload.minFleets", "2"));

    /**
     * In case the user decides for JINI PathloadUrl Connector discovery,
     * this is the Group he's searching for.
     * 
     */
    public static final String PATHLOAD_PEER_GROUP = AppConfig.getProperty("lia.monitor.Modules.monPathload.peerGroup");

    /**
     * 
     */
    public static final String PATHLOAD_PEER_URL = AppConfig.getProperty("lia.util.Pathload.client.PathloadConnector");

    /**
     * 
     */
    public static final String PATHLOAD_REPORT_ONLY = AppConfig
            .getProperty("lia.monitor.Modules.monPathload.reportOnly");

    private static final String ModuleName = "monPathload";
    private static final String ClusterName = "Pathload";
    private static final String[] ResTypes = { "AwBandwidth_Low", "AwBandwidth_High", "MeasurementDuration",
            "MeasurementStatus" };

    /**
     * Accepted Modules Parameters <br />
     * <b>if</b> 	- Interface to monitor <br />
     * <b>speed</b> - Speed of the interface <br />
     */
    public static final String[] ParamNames = { "if", "speed" };

    /**
     * This is my singleton instance
     */
    private static PeerCache miniMe = new PeerCache();
    private final Object lock;
    private int status;
    private Timer configGetTimer;
    private Timer runClientTimer;
    private ConfigGet configGet;
    private RunClient runClient;

    private String myFarmName;
    private String myIpAddress;
    private String myFQDNHostname;
    private String myIf;
    private long myMaxSpeed;

    private AppPathload appPathload;
    private ServletResponse servletResponse;
    private boolean releaseToken;
    private final Vector results;

    private final ConfigurationTracker configurationTracker;

    private boolean isOutOfSync;

    /**
     *	Default constructor. Initialize myself 
     *
     */
    private PeerCache() {
        lock = new Object();
        status = PeerCache.PATHLOAD_CACHE_STATUS_STOPPED;
        configurationTracker = new ConfigurationTracker(this);
        results = new Vector();
        myFarmName = "??";
        appPathload = null;
        releaseToken = false;
        isOutOfSync = false;
    }

    /**
     * Get myInstance. This is the only way tou can get me 
     *
     * @return	Return miniMe. I'm the only one here.
     */
    public static PeerCache getInstance() {
        return miniMe;
    }

    /**
     * The farmName is not available at the first initialization
     * of the PeerCache. This is why the farmName must be set
     * manually by the monPathload module.
     * Attention: the PeerCache module will not start without 
     * setting the FarmName
     * 
     * @param myFarmName 	My FarmName
     */
    public void setFarmName(String myFarmName) {
        if (myFarmName == null) {
            return;
        }

        synchronized (lock) {
            this.myFarmName = myFarmName;
            logger.log(Level.FINE, "[monPathload] PeerCache Farmname set to: " + myFarmName);
        }
    }

    /**
     * Initialize and start the ConfigGet and RunPathload thread
     *  
     * @return	True if successfull, false if my status is already marked
     * 			as running
     */
    public boolean start() {
        boolean bResult = false;
        if (status == PeerCache.PATHLOAD_CACHE_STATUS_RUNNING) {
            logger.log(Level.WARNING, "[monPathload] PeerCache Trying to start, but i'm already running.");
            return false;
        }

        synchronized (lock) {
            try {
                if ((myIf == null) || (myMaxSpeed == NOT_SET)) {
                    throw new IllegalArgumentException("Module is not configured properly. It needs the following "
                            + "required parameters: if and speed. Speed is the maximum speed in Megabytes per second.");
                }

                boolean isSet = false;
                InetAddress addr = null;
                NetworkInterface ni = NetworkInterface.getByName(myIf);
                for (Enumeration e = ni.getInetAddresses(); e.hasMoreElements() && (isSet == false);) {
                    addr = (InetAddress) e.nextElement();
                    if ((!addr.isSiteLocalAddress()) && (!addr.isLoopbackAddress())) {
                        if (addr instanceof java.net.Inet4Address) {
                            if (e.hasMoreElements()) {
                                logger.log(Level.INFO, "Interface " + ni.getDisplayName() + " has subinterfaces"
                                        + "defined. I have chosen the first one.");
                            }
                            isSet = true;
                        }
                    }
                }

                if (!isSet) {
                    throw new IllegalArgumentException("Could not find a valid interface to run on.");
                }

                myIpAddress = addr.getHostAddress();
                if (!isPublicAddress(myIpAddress)) {
                    throw new IllegalArgumentException("Ip Address is in private Address Space.");
                }

                myFQDNHostname = addr.getCanonicalHostName();
                logger.log(Level.INFO, "Using interface " + myIf + " [" + myFQDNHostname + "/" + myIpAddress + "].");

                String groupStr = AppConfig.getProperty("lia.Monitor.group", "test");
                String[] myGroup = null;
                if (groupStr != null) {
                    myGroup = groupStr.split(",");
                }

                if (myFarmName.equals("??")) {
                    throw new IllegalArgumentException("[monPathload] I must know my farmName beoynd this point.");
                }

                if (appPathload == null) {
                    appPathload = new AppPathload();

                    if (!appPathload.check()) {
                        throw new IllegalArgumentException("[monPathload] AppPathload checks failed. Please verify "
                                + "logLevel lia.app.abping.pathload.FINEST.");
                    }
                    if (PeerCache.PATHLOAD_CACHE_RUN_SERVER == true) {
                        if (!appPathload.restart()) {
                            throw new IllegalArgumentException(
                                    "[monPathload] AppPathload Pathload_snd startup failed. "
                                            + "Please verify logLevel lia.app.abping.pathload.FINEST.");
                        }
                    }

                }

                configGet = new ConfigGet(this, myFQDNHostname, myIpAddress, myFarmName, myGroup);
                configGetTimer = new Timer(configGet, 30 * 1000, PeerCache.PATHLOAD_CACHE_TIMER_CONFIG);

                runClient = new RunClient(this);
                runClientTimer = new Timer(runClient, 60 * 1000, PeerCache.PATHLOAD_CACHE_TIMER_RUN);

                if (PATHLOAD_PEER_URL != null) {
                    logger.log(Level.INFO,
                            "monPathload will be using Jini services for PathloadConnector Url discovery.");
                    changeUrl(null, PATHLOAD_PEER_URL);
                } else if (PATHLOAD_PEER_GROUP != null) {
                    logger.log(Level.INFO,
                            "monPathload will be using the static definition of the PathloadConnector Url.");
                    configurationTracker.start();
                } else {
                    throw new IllegalArgumentException("[monPathload] Neither Pathload Url or Pathload PeerGroup is "
                            + "specified. I'm not running");
                }

                configGetTimer.start();
                runClientTimer.start();

                status = PeerCache.PATHLOAD_CACHE_STATUS_RUNNING;
                bResult = true;
                logger.log(Level.FINE, "[monPathload] PeerCache started.");
            } catch (SecurityException e) {
                logger.log(Level.SEVERE,
                        "[monPathload] PeerCache SecurityException while trying to determine hostname.");
            } catch (IllegalArgumentException e) {
                logger.log(Level.SEVERE, e.getMessage());
            } catch (NullPointerException e) {
                logger.log(Level.SEVERE, e.getMessage());
            } catch (SocketException e) {
                logger.log(Level.SEVERE, e.getMessage());
            } catch (Throwable t) {
                logger.log(Level.SEVERE, t.getMessage());
            }
        }
        return bResult;
    }

    /**
     * Stop my threads cleanly.
     * 
     * @return	True if successfull, false if i'm already not running
     */
    public boolean stop() {
        if (status == PeerCache.PATHLOAD_CACHE_STATUS_STOPPED) {
            logger.log(Level.WARNING, "[monPathload] PeerCache Trying to stop but i'm not running.");
            return false;
        }

        synchronized (lock) {
            if (configGetTimer != null) {
                configGetTimer.shutdown();
            }
            configGetTimer = null;

            if (runClientTimer != null) {
                runClientTimer.shutdown();
            }
            runClientTimer = null;
            results.clear();

            if (appPathload != null) {
                appPathload.stop();
                appPathload.stopPathloadClient();
                logger.log(Level.INFO, "[monPathload] AppPathload stopped.");
            }

            if (configGet != null) {
                configGet.shutdown("monPathload module is shutting down.");
            }

            configurationTracker.shutdown();
            status = PeerCache.PATHLOAD_CACHE_STATUS_STOPPED;
            logger.log(Level.INFO, "[monPathload] PeerCache stopped.");
        }

        return true;
    }

    /**
     *	Get vector of last results. 
     *	Because measurements are rare, the method of showing that my
     * 	destination peers are still alive is to relay the last measurement
     *  results till my peer no longer responds.
     *  This is why my my results have a status: NEW if i just succeded
     *  a measurement (this is also used to determine the actual time of
     *  the measurement), RELAYED, if my measurement is actually old but
     *  i'm still measuring with that peer and FAILED, meaning I got the
     *  token to do a measurement with that peer but, pathload_rcv failed
     *  to supply the necessary data. 
     *
     * 
     * @return	Vector of last results.
     */
    public Vector getResults() {
        Vector response = null;

        synchronized (lock) {
            if (!results.isEmpty()) {
                response = new Vector(results);
                results.clear();
            }
        }

        return response;
    }

    /**
     * This is used by the run client to get the 
     * Token.
     * 
     * @return Get the passed servlet response from the servlet
     */
    public ServletResponse getToken() {
        ServletResponse result = null;

        synchronized (lock) {
            if (status == PeerCache.PATHLOAD_CACHE_STATUS_RUNNING) {
                if ((servletResponse != null) && (servletResponse.hasToken())) {
                    result = servletResponse;
                }
            }
        }
        return result;
    }

    /**
     * 
     * @param result
     * @return
     */
    public boolean putResult(PathloadResult result) {
        if (result == null) {
            return false;
        }

        Result r = new Result(this.myFarmName, PeerCache.ClusterName, result.getNodeName(), PeerCache.ModuleName,
                PeerCache.ResTypes);

        result = correctData(result);
        if ((PATHLOAD_REPORT_ONLY != null) && (result.getExitStat_value() >= 0)
                && (PATHLOAD_REPORT_ONLY.indexOf("" + result.getExitStat_value()) >= 0)) {
            logger.log(Level.INFO, "[PeerCache] Dropping PathloadResult " + result);
            return false;
        }

        synchronized (lock) {
            if ((result.getExitStat_value() == PathloadResult.MEASUREMENT_STATUS_FAILED) && (isOutOfSync == true)) {
                result.setExitStat_value(PathloadResult.MEASUREMENT_STATUS_OUT_OF_SYNC);
            }
            isOutOfSync = false;
            r = result.fillResult(r);
            r.time = NTPDate.currentTimeMillis();
            results.add(r);
        }

        return true;
    }

    /**
     *	Mark flag, that config get should release 
     *  the token.
     *   
     * @param tokenID	Token String ID. 
     * 					Future versions will check if the id's of the 
     * 					currentToken and the one released are the same.
     * @return			True if succeded, false otherwise
     */
    public boolean releaseToken(String tokenID) {
        synchronized (lock) {
            if (status == PeerCache.PATHLOAD_CACHE_STATUS_RUNNING) {
                logger.log(Level.FINEST, "I may now release the token!");
                releaseToken = true;
                servletResponse = null;
                logger.log(Level.FINEST, "Token is now null");
                configGetTimer.wakeMeUp();
            }
        }
        return true;
    }

    /**
     * The Configuration client has received the token. 
     * He may now push the thread that controls the receiver to run 
     * imediatly.
     *
     */
    protected void gotToken() {
        synchronized (lock) {
            runClientTimer.wakeMeUp();
        }
    }

    /**
     * Keep last token message from runClient
     * TODO: Check this
     * 
     * @param servletResponse	The Servlet's response with the token
     * @return					True if succeded, false otherwise
     */
    public boolean setServletResponse(ServletResponse servletResponse) {
        if (servletResponse == null) {
            return false;
        }

        synchronized (lock) {
            if (status == PeerCache.PATHLOAD_CACHE_STATUS_RUNNING) {
                this.servletResponse = servletResponse;
            }
        }

        return true;
    }

    /**
     * Check to see if the configGet thread should release the
     * token or not
     * 
     * @return		True if token can be released, false otherwise.
     */
    public boolean mustReleaseToken() {
        boolean bResult = false;

        synchronized (lock) {
            if (status == PeerCache.PATHLOAD_CACHE_STATUS_RUNNING) {
                logger.log(Level.FINEST, "Should I release the token?: " + releaseToken);
                bResult = releaseToken;
                releaseToken = false;
            }
        }

        return bResult;
    }

    /**
     * Get the Execution Command of the Pathload Client program.
     * This is used by the RunClient to query the appPathload class
     * for the desired information
     * 
     * @return Pathload client execution command.
     */
    public String getPathloadClientExecCmd() {
        return appPathload.getPathloadClientExecCmd(myIpAddress);
    }

    /**
     * The Pathload Servlet announced me i'm out of sync.
     * I will imediatly terminate all pathload_rcv connections.
     *
     */
    public void outOfSync() {
        synchronized (lock) {
            appPathload.stopPathloadClient();
            isOutOfSync = true;
        }
    }

    /**
     * Set module properties. Currently the only properties available
     * are if and speed. The properties are required!
     * 
     * @param prop
     */
    public void setProperties(Properties prop) {
        if (prop == null) {
            return;
        }

        for (Enumeration e = prop.keys(); e.hasMoreElements();) {
            String key = (String) e.nextElement();
            synchronized (lock) {
                if (key.equals("if")) {
                    myIf = prop.getProperty(key);
                } else if (key.equals("speed")) {
                    String maxSpeed = prop.getProperty(key);
                    try {
                        myMaxSpeed = Integer.parseInt(maxSpeed);
                    } catch (NumberFormatException ex) {
                        logger.log(Level.WARNING, "NumberFormatException while parsing speed parameter."
                                + "Speed is in Megabytes. Ex:. speed=100 ", ex.getMessage());
                    } catch (NullPointerException ex) {
                        logger.log(Level.WARNING, "NullPointerException while trying to parse speed ", ex.getMessage());
                    }
                    if (myMaxSpeed < 0) {
                        myMaxSpeed = NOT_SET;
                    }
                }
            }
        }
    }

    /**
     * Test and see if the given ipAddress is in the private Address
     * Space.
     * 
     * @param ipAddress	IP Address in String form.
     * @return	True if it's in public range, false otherwise.
     */
    private boolean isPublicAddress(String ipAddress) {
        int octet1, octet2, octet3;
        boolean bResult = true;

        if (ipAddress == null) {
            return false;
        }
        try {
            String[] octets = ipAddress.split("\\.");
            if ((octets == null) || (octets.length != 4)) {
                return false;
            }

            octet1 = Integer.parseInt(octets[0]);
            octet2 = Integer.parseInt(octets[1]);
            octet3 = Integer.parseInt(octets[2]);

            if ((octet1 == 10) || (octet1 == 127)) {
                bResult = false;
            }
            if ((octet1 == 172) && ((octet2 > 15) && (octet3 < 32))) {
                bResult = false;
            }
            if ((octet1 == 192) && (octet2 > 167)) {
                bResult = false;
            }
        } catch (NoSuchElementException e) {
            bResult = false;
        } catch (NullPointerException e) {
            bResult = false;
        } catch (NumberFormatException e) {
            bResult = false;
        }

        return bResult;
    }

    /**
     * If pathload_snd is unable to determine the maximum speed, it will print </br> 
     * out 0. This will then be modified by the maximum speed available (myMaxSpeed). </br>
     * If myMaxSpeed is not defined, the data will be left untouched. </br>
     * On some Gigabit Links, Pathload will sometimes announe a result greater than </br>
     * 1000Mbs per seconds. If </br>
     * <i>lia.monitor.Modules.monPathload.correctErrors</i> </br>
     * is true, then the maximum available bandwidth is modified into myMaxSpeed (1000);</br>
     * </br>
     * 
     * @param data	The measured data to be checked
     * @return		The modified data.
     */
    private PathloadResult correctData(PathloadResult result) {
        if ((myMaxSpeed == NOT_SET) || (result == null)) {
            return result;
        }

        if (PATHLOAD_CORRECT_EVIDENT_ERRORS) {
            if ((result.isAwbwHigh()) && (result.isAwbwLow()) && (result.getAwbwHigh_value() == 0.0)
                    && (result.getAwbwLow_value() > 0) && (result.getExitStat_value() == 1)) {
                result.setExitStat_value(5);
                result.setAwbwHigh(false);
            }
        }

        if ((result.isAwbwLow() && result.isAwbwHigh()) && (result.getAwbwLow_value() > result.getAwbwHigh_value())) {
            double temp = result.getAwbwLow_value();
            result.setAwbwLow_value(result.getAwbwHigh_value());
            result.setAwbwHigh_value(temp);
        }

        if (PATHLOAD_CORRECT_EVIDENT_ERRORS) {
            if (result.isAwbwLow()) {
                if (result.getAwbwLow_value() < 0) {
                    result.setAwbwLow_value(0);
                } else if (result.getAwbwLow_value() > myMaxSpeed) {
                    result.setAwbwLow_value(myMaxSpeed);
                }
            }
            if (result.isAwbwHigh()) {
                if (result.getAwbwHigh_value() < 0) {
                    result.setAwbwHigh_value(0);
                } else if (result.getAwbwHigh_value() > myMaxSpeed) {
                    result.setAwbwHigh_value(myMaxSpeed);
                }
            }
            if (result.isFleets() && (result.getFleets_value() < PeerCache.PATHLOAD_MIN_FLEETS)) {
                result.setExitStat_value(PathloadResult.MEASUREMENT_STATUS_FEW_FLEETS);
            }

            if ((result.isAwbwHigh() && result.isAwbwLow())
                    && ((result.getAwbwLow_value() == 0.0) && (result.getAwbwHigh_value() == 0.0))) {
                if ((result.isBytesRecv()) && (result.getBytesRecv_value() < 1)) {
                    result.setExitStat_value(PathloadResult.MEASUREMENT_STATUS_FIREWALLED_RECEIVER);
                } else if ((result.isExitStat()) && (result.getExitStat_value() > 0)) {
                    result.setExitStat_value(PathloadResult.MEASUREMENT_STATUS_LOW_BANDWITH_WARNING);
                    result.setAwbwHigh_value(0.2);
                }
            }

            if (result.isAwbwHigh() && result.isAwbwLow()) {
                if ((myMaxSpeed > 600)
                        && ((result.getAwbwHigh_value() - result.getAwbwLow_value()) > ((PATHLOAD_CACHE_ERROR_COEF / 100) * myMaxSpeed))) {
                    result.setExitStat_value(PathloadResult.MEASUREMENT_STATUS_ERRORNOUS);
                } else if ((myMaxSpeed == 100) && ((result.getAwbwHigh_value() - result.getAwbwLow_value()) > 70)) {
                    result.setExitStat_value(PathloadResult.MEASUREMENT_STATUS_ERRORNOUS);
                }
            }

            if ((result.isAwbwLow()) && (result.getAwbwLow_value() == 0.0) && (!result.isAwbwHigh())) {
                result.setAwbwLow(false);
                result.setExitStat_value(PathloadResult.MEASUREMENT_STATUS_FAILED);
            }
        }

        return result;
    }

    /**
     * The Url of the PathloadConnector has changed. Close all incomming and outgoing
     * connections and use the new URL.
     * 
     * @param oldUrl	Old url
     * @param newUrl	New url. If it's null, ignore it.
     */
    public void changeUrl(String oldUrl, String newUrl) {
        if (newUrl == null) {
            return;
        }
        if (configGet == null) {
            logger.log(Level.SEVERE, "[PeerCache] Configuration Thread died unexpectedly."
                    + "Please contact ML Developers.");
        }

        synchronized (lock) {
            if (oldUrl != null) {
                configGet.shutdown("Changing URL from " + oldUrl + " to " + newUrl);
            }

            configGet.setUrl(newUrl);

            if (oldUrl != null) {
                servletResponse = null;
                appPathload.stopPathloadClient();
                appPathload.restart();
                releaseToken = false;
            }
            logger.log(Level.INFO, "[PeerCache] PathloadConfigURL changed from " + oldUrl + " to " + newUrl + ".");
        }
    }
}
