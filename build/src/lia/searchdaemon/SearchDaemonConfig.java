package lia.searchdaemon;

import java.io.File;
import java.io.FileInputStream;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.util.DateFileWatchdog;

public class SearchDaemonConfig implements Observer {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(SearchDaemonConfig.class.getName());

    private static SearchDaemonConfig _thisInstance;
    File confFile;
    DateFileWatchdog dfw;
    private Properties cProps;

    private SearchDaemonConfig() throws Exception {
        confFile = new File(AppConfig.getProperty("lia.searchdaemon.SearchDaemonConfigFile"));
        cProps = null;
        if (confFile != null) {
            dfw = DateFileWatchdog.getInstance(confFile, 5 * 1000);
            dfw.addObserver(this);
        } else {
            throw new Exception("SearchDaemonConfFile is null");
        } // if - else

        reloadConf();
    } // OSDaemonConfig 

    public static final SearchDaemonConfig getInstance() {
        if (_thisInstance == null) {
            try {
                _thisInstance = new SearchDaemonConfig();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Could not init SearchDaemonConfFile", t);
                _thisInstance = null;
            }
        }
        return _thisInstance;
    } // getInstance

    public final String getProperty(String key) {
        return getProperty(key, null);
    } // getProperty

    public final synchronized String getProperty(String key, String defaultValue) {
        if (key == null) {
            return null;
        }
        String retS = System.getProperty(key);
        if (retS != null) {
            return retS.trim();
        }

        if (cProps == null) {
            return defaultValue;
        }

        retS = cProps.getProperty(key, defaultValue);
        if (retS != null) {
            return retS.trim();
        }

        return retS;
    } // getProperty

    public final String[] getVectorProperty(String key) {
        return getVectorProperty(key, null);
    } // getVectorProperty

    public synchronized final String[] getVectorProperty(String key, String defaultValue) {
        if (key == null) {
            return null;
        }
        String props = System.getProperty(key);

        if (props == null) {
            props = (cProps == null) ? defaultValue : cProps.getProperty(key, defaultValue);
        }

        if (props == null) {
            return null;
        }

        return props.trim().split("(\\s)*,(\\s)*");
    } // getVectorProperty

    private void reloadConf() {
        Properties p = new Properties();
        try {
            FileInputStream fis = new FileInputStream(confFile);
            p.load(fis);
            fis.close();
        } catch (Throwable t) {
            t.printStackTrace();
            p = null;
        }
        synchronized (this) {
            if (p != null) {
                cProps = p;
            }
        } // synchronized
    } // reloadConf

    @Override
    public void update(Observable o, Object arg) {
        reloadConf();
    } // update

} // OSDaemonConfig
