package lia.Monitor.monitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.DateFileWatchdog;
import lia.util.Utils;

/**
 * Utility class for getting application specific properties in the following order: <br>
 * <br>
 * 1) System environment - first choice, or no URL specified, or invalid URL <br>
 * 2) A standard ".properties" file specified by an URL <br>
 * <br>
 * This class has only one instance for the entire application. <br>
 * The URL is specified by "lia.Monitor.ConfigURL" as a JVM System variable.
 *
 * @author ramiro
 */
public final class AppConfig implements Observer {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(AppConfig.class.getName());

    /** Properties used by this class. Loaded from lia.Monitor.ConfigURL */
    private static volatile Properties propertiesConfigApp = null;

    /** Properties used by this class. Loaded from lia.Monitor.ConfigURL */
    private static volatile Properties remotePropsConfigApp = null;

    /** Properties used by this class. Loaded from lia.Monitor.ConfigURL */
    private static volatile Properties localPropsConfigApp = null;

    /** Properties from inside app */
    private static volatile Properties internalConfigApp = null;

    /** The Global Environment Variables stored as Properties */
    private static final Properties globalEnv;

    private static final String OS;

    // holds the last timestamp for the reload
    private static AtomicLong lastReloaded = new AtomicLong();

    private static final List<AppConfigChangeListener> listeners = new CopyOnWriteArrayList<AppConfigChangeListener>();

    private static final String configURLString;

    private static final DateFileWatchdog dfw;

    // readLock used for getProperty*-like operations
    private static final Lock rLock;

    // readLock used for setting props
    private static final Lock wLock;

    private static final URL url;

    static final class MLEnvProperties {

        private static final class Holder {

            private static final MLEnvProperties theInstance = new MLEnvProperties();
        }

        final Properties serviceMLEnvProperties = new Properties();

        private MLEnvProperties() {
            reloadMLEnv();
        }

        void reloadMLEnv() {
            final Properties tmpProp = new Properties();
            String mlHomeApp = AppConfig.getProperty("MonaLisa_HOME", null);
            String mlHome = (mlHomeApp != null) ? mlHomeApp : AppConfig.getGlobalEnvProperty("MonaLisa_HOME");

            if (mlHome != null) {
                final File mlEnvFile = new File(mlHome + File.separator + "Service" + File.separator + "CMD"
                        + File.separator + "ml_env");
                if (mlEnvFile.exists() && mlEnvFile.canRead()) {
                    FileReader fr = null;
                    BufferedReader br = null;
                    try {
                        fr = new FileReader(mlEnvFile);
                        br = new BufferedReader(fr);

                        tmpProp.load(br);
                        final Map<Object, Object> sMap = new HashMap<Object, Object>(tmpProp.size());
                        for (Map.Entry<Object, Object> entry : tmpProp.entrySet()) {
                            final Object key = entry.getKey();
                            final String value = (String) entry.getValue();

                            String sValue = value.trim();
                            while (sValue.startsWith("\"") || sValue.startsWith("'")) {
                                sValue = sValue.substring(1);
                            }

                            while (sValue.endsWith("\"") || sValue.endsWith("'")) {
                                sValue = sValue.substring(0, sValue.length() - 1);
                            }
                            sMap.put(key, sValue.trim());
                        }
                        synchronized (serviceMLEnvProperties) {
                            serviceMLEnvProperties.clear();
                            serviceMLEnvProperties.putAll(sMap);
                        }
                    } catch (Throwable t) {
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.WARNING, "Unable to load ml_env file. Cause: ", t);
                        }
                    } finally {
                        Utils.closeIgnoringException(fr);
                        Utils.closeIgnoringException(br);
                    }
                }
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.WARNING, "Unable to load ml_env file because ml_env file: " + mlEnvFile
                            + " is not readable");
                }
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.WARNING, "Unable to load ml_env file because MonaLisa_HOME is undefined");
            }
        }

        static final MLEnvProperties getInstance() {
            return Holder.theInstance;
        }
    }

    static {

        final ReadWriteLock rwLock = new ReentrantReadWriteLock();
        internalConfigApp = new Properties();

        rLock = rwLock.readLock();
        wLock = rwLock.writeLock();

        String tmpCfgURLStr = null;

        wLock.lock();
        try {

            boolean fromWebStart = false;
            try {
                final String property = System.getProperty("lia.Monitor.AppConfig.WebStart");
                if (property != null) {
                    fromWebStart = true;
                }
            } catch (Throwable t) {
                System.err.println(" UNABLE TO READ lia.Monitor.AppConfig.WebStart env variable");
                t.printStackTrace();
                throw new RuntimeException("UNABLE TO READ lia.Monitor.AppConfig.WebStart env variable", t);
            }

            try {
                tmpCfgURLStr = System.getProperty("lia.Monitor.ConfigURL");
            } catch (Throwable t) {
                System.err.println("UNABLE TO READ lia.Monitor.ConfigURL env variable");
                t.printStackTrace();
                if (fromWebStart) {
                    throw new RuntimeException(
                            "UNABLE TO READ lia.Monitor.ConfigURL env variable and started as web start application");
                }
            }

            if (fromWebStart) {
                logger.log(Level.INFO, "WebStart detected! Will try loading remote properties now...");
                if ((tmpCfgURLStr == null) || tmpCfgURLStr.trim().isEmpty()) {
                    logger.log(Level.SEVERE,
                            "Unable to determine lia.Monitor.ConfigURL property and Application launched via WebStart!");
                    throw new RuntimeException(
                            "Unable to determine lia.Monitor.ConfigURL property and Application launched via WebStart!");
                }
            } else {
                logger.log(Level.INFO, "Normal app config (no webstart)");
            }

            propertiesConfigApp = new Properties();

            String os = null;
            try {
                os = System.getProperty("os.name").toLowerCase();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[ AppConfig ] Unable to determine os.name System Property. Cause:", t);
            }

            OS = os;

            Properties tmpGlobalEnv = null;

            try {
                tmpGlobalEnv = new Properties();
                tmpGlobalEnv.putAll(System.getenv());
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[ AppConfig ] Cannot load Global Env", t);
                tmpGlobalEnv = null;
            }

            globalEnv = tmpGlobalEnv;

            try {
                MLEnvProperties.getInstance().reloadMLEnv();
            } catch (Throwable ignore) {
                //not interested
            }
            propertiesConfigApp.putAll(MLEnvProperties.getInstance().serviceMLEnvProperties);

            configURLString = tmpCfgURLStr;

            URL tmpURL = null;
            if ((configURLString != null) && (configURLString.length() != 0)) {
                InputStream is = null;
                URLConnection urlConnection = null;

                try {
                    tmpURL = new URL(configURLString.trim());
                    urlConnection = tmpURL.openConnection();
                    if (fromWebStart) {
                        try {
                            urlConnection.setReadTimeout(10 * 1000);
                            urlConnection.setDefaultUseCaches(false);
                            urlConnection.setUseCaches(false);
                        } catch (Throwable t) {
                            logger.log(
                                    Level.WARNING,
                                    "[AppConfig] Unable to set connection paramters. May timeout after a long time ... Cause:",
                                    t);
                        }
                        urlConnection.connect();
                    }
                    is = urlConnection.getInputStream();
                    propertiesConfigApp.load(is);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "[ AppConfig ] lia.Monitor.ConfigURL: " + configURLString + " loaded.");
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "[ AppConfig ] [ HANDLED ] Unable to load lia.Monitor.ConfigURL: "
                            + configURLString + ". Cause: ", t);
                    logger.log(Level.WARNING, "[ AppConfig ] [ HANDLED ] Using System.getProperty(...)");
                    if (fromWebStart) {
                        throw new RuntimeException("WebStart application but unable to load the env. Cause", t);
                    }
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (Throwable ignore) {
                            //not interested
                        }
                    }

                }

            }// if()

            url = tmpURL;
            DateFileWatchdog tmpDFW = null;
            if (!fromWebStart) {
                try {
                    if (url != null) {
                        tmpDFW = DateFileWatchdog.getInstance(url.getFile(), 10 * 1000);
                        tmpDFW.addObserver(new Observer() {
                            /**
                             * @param o
                             * @param arg
                             */
                            @Override
                            public void update(Observable o, Object arg) {
                                reloadProps();
                            }

                        });
                    } else {
                        logger.log(Level.FINER,
                                "[ AppConfig ] No lia.Monitor.ConfigURL specified. Using JVM & local env.");
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING,
                            "[ AppConfig ] Unable to subscribe for future changes for lia.Monitor.ConfigURL: "
                                    + configURLString + ". Cause:", t);
                }

                // DO NOT CHANGE THIS IN NTPDate.currentTimeMillis() !!!! or you will get a very nice
                // deadlock
                lastReloaded.set(System.currentTimeMillis());
                logger.log(Level.INFO, "Properties loaded. Monitoring for changes...");
            } else {
                logger.log(Level.INFO, "Properties loaded via webstart...");
            }
            dfw = tmpDFW;

        } finally {
            wLock.unlock();
        }

    }

    /**
     * Private constructor. Only one instance!
     */
    private AppConfig() {

    }

    public static final long lastReloaded() {
        return lastReloaded.get();
    }

    public static void reloadProps() {

        Properties localPropertiesConfigAppTMP = new Properties();
        final String configURLString = System.getProperty("lia.Monitor.ConfigURL");
        if ((configURLString != null) && (configURLString.length() != 0)) {
            try {

                InputStream is = null;

                try {
                    is = new URL(configURLString.trim()).openStream();
                    localPropertiesConfigAppTMP.load(is);
                } catch (Throwable t) {
                    localPropertiesConfigAppTMP = null;
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (Throwable ignore) {
                            //not interested
                        }
                    }
                }

                if ((localPropertiesConfigAppTMP != null) && (localPropertiesConfigAppTMP.size() > 0)) {
                    localPropsConfigApp = localPropertiesConfigAppTMP;
                }

                try {
                    MLEnvProperties.getInstance().reloadMLEnv();
                } catch (Throwable ignore) {
                    //not interested
                }

                wLock.lock();
                try {
                    propertiesConfigApp.clear();
                    propertiesConfigApp.putAll(MLEnvProperties.getInstance().serviceMLEnvProperties);
                    propertiesConfigApp.putAll(localPropsConfigApp);
                    propertiesConfigApp.putAll(internalConfigApp);
                    if (remotePropsConfigApp != null) {
                        propertiesConfigApp.putAll(remotePropsConfigApp);
                    }
                } finally {
                    wLock.unlock();
                }

                logger.log(Level.INFO, "AppConfig Props (RE)LOADED");
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "Props: " + propertiesConfigApp);
                }

                lastReloaded.set(System.currentTimeMillis());
                notifyListeners();

            } catch (Throwable t) {
                logger.log(Level.WARNING, "\nCannot Load Properties URL form: " + configURLString + ". Cause:", t);
            } // catch()
        }// if()
    }

    public static void setRemoteProps(final Properties remoteProps) {
        remotePropsConfigApp = remoteProps;
        reloadProps();
    }

    /**
     * @see #java.lang.System.getProperty(String)
     */
    public static final String getProperty(String key) {
        return getProperty(key, null);
    }

    /**
     * @see #java.lang.System.getProperty(String, String)
     */
    public static final String getProperty(String key, String defaultValue) {

        String rv = System.getProperty(key);
        if (rv == null) {
            rLock.lock();
            try {
                rv = propertiesConfigApp.getProperty(key, defaultValue);
            } finally {
                rLock.unlock();
            }
        }

        return stripPropertyValue(rv);
    }

    private static final String stripPropertyValue(final String value) {
        String rv = value;

        if (rv == null) {
            return null;
        }

        while (true) {
            int i1 = rv.indexOf("${");
            int i2 = rv.indexOf("}");
            if ((i1 != -1) && (i2 != -1)) {
                String gps = getProperty(rv.substring(i1 + 2, i2).trim());

                if (gps == null) {
                    return rv;
                }
                final String s1 = rv.substring(0, i1);
                final String s2 = rv.substring(i2 + 1);
                rv = s1 + gps + s2;
            } else {
                break;
            }
        }

        return rv.trim();
    }

    /**
     * Sets a property and returns previous value
     *
     * @param key
     * @param newValue
     * @return null in case no property was found
     */
    public static final String setProperty(String key, String newValue) {
        wLock.lock();
        try {
            if (newValue == null) {
                internalConfigApp.remove(key);
                return (String) propertiesConfigApp.remove(key);
            }
            internalConfigApp.setProperty(key, newValue);
            return (String) propertiesConfigApp.setProperty(key, newValue);
        } finally {
            wLock.unlock();
        }
    }

    /**
     * Sets an app property if it's not set already
     *
     * @param key
     * @param newValue
     * @return the existing value or null if no mapping was found
     */
    public static final String setPropertyIfAbsent(String key, String newValue) {
        rLock.lock();
        String existing = null;
        try {
            existing = getProperty(key);
            if (existing != null) {
                return existing;
            }
        } finally {
            rLock.unlock();
        }

        wLock.lock();
        try {
            //DCL - double check
            existing = getProperty(key);
            if (existing != null) {
                return existing;
            }
            if (newValue == null) {
                internalConfigApp.remove(key);
                propertiesConfigApp.remove(key);
            } else {
                internalConfigApp.setProperty(key, newValue);
                propertiesConfigApp.setProperty(key, newValue);
            }
            return existing;
        } finally {
            wLock.unlock();
        }
    }

    public static final Properties getPropertiesConfigApp() {
        final Properties retv = new Properties();
        rLock.lock();
        try {
            retv.putAll(propertiesConfigApp);
        } finally {
            rLock.unlock();
        }

        return retv;
    }

    public static final String[] getVectorProperty(String key) {
        return getVectorProperty(key, (String) null);
    }

    /**
     * @param key
     * @param defaultValue
     *            - default values specified as a <code>String</code><br>
     *            Ex: getVectorProperty("myKey", "FirstString, SecondString, ThirdString"
     * @return String[] the property values specified by key
     */
    public static final String[] getVectorProperty(String key, String defaultValue) {

        String props = System.getProperty(key);

        if (props == null) {
            rLock.lock();
            try {
                props = propertiesConfigApp.getProperty(key, defaultValue);
            } finally {
                rLock.unlock();
            }
        }

        if (props == null) {
            return null;
        }

        final String[] unstrippedProps = props.trim().split("(\\s)*,(\\s)*");

        if ((unstrippedProps == null) || (unstrippedProps.length == 0)) {
            return null;
        }

        final int len = unstrippedProps.length;

        final String[] retV = new String[len];

        for (int i = 0; i < len; i++) {
            retV[i] = stripPropertyValue(unstrippedProps[i]);
        }

        return retV;
    }

    /**
     * @param key
     * @param defaultValues
     *            - default values specified as <code>String[]</code>
     * @return String[]
     */
    public static final String[] getVectorProperty(String key, String[] defaultValues) {
        String props = System.getProperty(key);

        if (props == null) {
            rLock.lock();
            try {
                props = propertiesConfigApp.getProperty(key);
            } finally {
                rLock.unlock();
            }
        }

        if (props == null) {
            return defaultValues;
        }

        return props.trim().split("(\\s)*,(\\s)*");
    }

    public static final String getGlobalEnvProperty(String key) {
        return getGlobalEnvProperty(key, null);
    }

    public static final String getGlobalEnvProperty(String key, String defaultValue) {

        if ((OS == null) || ((OS.indexOf("linux") == -1) && (OS.indexOf("unix") == -1))) {
            return defaultValue;
        }

        if (globalEnv == null) {
            return defaultValue;
        }

        return getEnvProperty(key, defaultValue, globalEnv);
    }

    private static final String getEnvProperty(String key, String defaultValue, Properties prop) {
        String retV = prop.getProperty(key, defaultValue);
        if (retV != null) {
            return retV.trim();
        }
        return retV;
    }

    public static final void addNotifier(AppConfigChangeListener l) {
        if (l == null) {
            return;
        }
        listeners.add(l);
    }

    static final void addLoggerNotifier(AppConfigChangeListener l) {
        if (l == null) {
            return;
        }
        listeners.add(0, l);
    }

    public static final void removeNotifier(AppConfigChangeListener l) {
        if (l == null) {
            return;
        }
        listeners.remove(l);
    }

    private static final void notifyListeners() {

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "[ AppConfig ] Starting notification thread");
        }

        new Thread() {

            @Override
            public void run() {
                setName(" ( ML ) AppConfigChangedNotifier ");

                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Iterating through registered listeners [ " + listeners.size() + " ] : "
                            + listeners);
                }

                try {
                    for (final AppConfigChangeListener accl : listeners) {
                        try {

                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, "[ AppConfigChangedNotifier ] Notifying : "
                                        + accl.getClass().getName());
                            }

                            accl.notifyAppConfigChanged();

                            if (logger.isLoggable(Level.FINE)) {
                                logger.log(Level.FINE, "[ AppConfigChangedNotifier ] notification complete");
                            }

                        } catch (Throwable ignore) {
                            if (logger.isLoggable(Level.FINE)) {
                                logger.log(Level.FINE,
                                        "[ AppConfigChangedNotifier ] [ HANDLED ] Got exception notifying AppConfigChangeListener: "
                                                + accl, ignore);
                            }
                        }
                    }// end for()
                } catch (Throwable t) {
                    logger.log(
                            Level.WARNING,
                            "[ AppConfigChangedNotifier ] [ HANDLED ] AppConfigChangedNotifier got exception in main loop",
                            t);
                }

                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "[ AppConfig ] Notification thread finished");
                }
            }
        }.start();
    }

    /**
     * @param arg
     */
    @Override
    public void update(Observable o, Object arg) {
        if ((dfw != null) && (o != null) && dfw.equals(o)) {
            reloadProps();
        }
    }

    /**
     * Get the value of the integer parameter, with the default value 0 (if it is not defined or incorrectly specified)
     *
     * @param sParam
     *            the parameter name
     * @return the integer value of the parameter or 0 if there was an error (undefined or incorrectly specified)
     */
    public static int geti(final String sParam) {
        return geti(sParam, 0);
    }

    /**
     * Get the value of the integer parameter, with the default value 0 (if it is not defined or incorrectly specified)
     *
     * @param sParam
     *            the parameter name
     * @param defaultVal
     *            default value to return in case of an error
     * @return the integer value of the parameter or 0 if there was an error parsing the value or it is unspecified in
     *         the configuration file
     */
    public static int geti(final String sParam, final int defaultVal) {
        try {

            final String s = getProperty(sParam);
            if ((s != null) && (s.length() > 0)) {
                final Integer iR = Integer.valueOf(s);

                if (iR == null) {
                    return defaultVal;
                }

                return iR.intValue();
            }

            return defaultVal;
        } catch (Throwable e) {
            return defaultVal;
        }
    }

    /**
     * Get the value of the long parameter, with the default value 0 (if it is not defined or incorrectly specified)
     *
     * @param sParam
     *            the parameter name
     * @return the long value of the parameter or 0 if there was an error (undefined or incorrectly specified)
     */
    public static long getl(final String sParam) {
        return getl(sParam, 0L);
    }

    /**
     * Get the value of the long parameter, with the default value 0 (if it is not defined or incorrectly specified)
     *
     * @param sParam
     *            the parameter name
     * @param defaultVal
     *            default value to return in case of an error
     * @return the long value of the parameter or 0 if there was an error parsing the value or it is unspecified in the
     *         configuration file
     */
    public static long getl(final String sParam, final long defaultVal) {
        try {
            final String s = getProperty(sParam);
            if ((s != null) && (s.length() > 0)) {
                final Long retV = Long.valueOf(s);
                if (retV == null) {
                    return defaultVal;
                }

                return retV.longValue();
            }

            return defaultVal;
        } catch (Throwable e) {
            return defaultVal;
        }
    }

    /**
     * Get the boolean value of this configuration parameter.
     *
     * @param sParam
     *            the configuration key
     * @param bDefault
     *            default value to return in case the option is not defined or cannot be recognized
     * @return true if the value is defined and starts with 't', 'y' or '1', false if the value is defined and starts
     *         with 'f', 'n' or '0' bDefault in any other case
     */
    public static boolean getb(final String sParam, final boolean bDefault) {
        String s = getProperty(sParam);

        if ((s != null) && (s.length() > 0)) {
            final char c = s.charAt(0);

            if ((c == 't') || (c == 'T') || (c == 'y') || (c == 'Y') || (c == '1')) {
                return true;
            }

            if ((c == 'f') || (c == 'F') || (c == 'n') || (c == 'N') || (c == '0')) {
                return false;
            }
        }

        return bDefault;
    }

    /**
     * Get the double (floating point) value of the configuration parameter
     *
     * @param sParam
     *            configuration parameter
     * @param dDefault
     *            default value to return in case of an parsing error / unexisting configuration parameter
     * @return the double value or dDefault in case of an parsing error / unexisting configuration parameter
     */
    public static double getd(final String sParam, final double dDefault) {
        try {
            return Double.parseDouble(getProperty(sParam));
        } catch (Exception e) {
            return dDefault;
        }
    }

}
