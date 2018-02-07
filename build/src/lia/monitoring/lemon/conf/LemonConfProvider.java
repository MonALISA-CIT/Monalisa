package lia.monitoring.lemon.conf;
import java.io.File;
import java.io.FileInputStream;
import java.util.Hashtable;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;

import lia.util.DateFileWatchdog;

public abstract class LemonConfProvider extends Observable implements Runnable, Observer {

    private Object syncConf = new Object();
    private LemonConf _theConf;
    private static LemonConfProvider _theInstance;
    
    private File confFile;
    private DateFileWatchdog dfw;
    
    public LemonConf getCurrentLemonConf() {
        synchronized(syncConf) {
            return _theConf;
        }
    }
    
    public void setConfFile(File confFile) {
        try {
            this.confFile = confFile;
            this.dfw  = DateFileWatchdog.getInstance(confFile, 5*1000);
            dfw.addObserver(this);
        } catch(Throwable t){
            t.printStackTrace();
            this.confFile = null;
            this.dfw = null;
        }
    }
    
    public void reloadConf() {
        try {
            if (_theInstance != null) {
                String jdbcUrl = "jdbc:oracle:oci8:@oramon1";
                String username = "lemonread";
                String passwd = "lemon2read";
                String LemonConfProvider = "ORACLE";
                String sVerifyDelay = "86400";
                String sErrorVerifyDelay = "360";
                
                long verifyDelay = 86400*1000;
                long errorVerifyDelay = 360*1000;
                
                try {
                    Properties p = new Properties();
		    FileInputStream fis = new FileInputStream(confFile);
                    p.load(fis);
		    fis.close();
                    
                    LemonConfProvider = p.getProperty("LemonConfProvider", "ORACLE");
                    jdbcUrl = p.getProperty("LemonConfOracleProvider.jdbcURL", "jdbc:oracle:oci8:@oramon1");
                    username = p.getProperty("LemonConfOracleProvider.username", "lemonread");
                    passwd = p.getProperty("LemonConfOracleProvider.passwd", "lemon2read");
                    
                    sVerifyDelay = p.getProperty("LemonConfOracleProvider.verifyDelay", "86400");
                    sErrorVerifyDelay = p.getProperty("LemonConfOracleProvider.verifyDelay", "360");
                    
                    try {
                        verifyDelay = Long.valueOf(sVerifyDelay.trim()).longValue()*1000;
                    }catch(Throwable t) {
                        verifyDelay = 86400*1000;
                    }

                    try {
                        errorVerifyDelay = Long.valueOf(sErrorVerifyDelay.trim()).longValue()*1000;
                    }catch(Throwable t) {
                        errorVerifyDelay = 360*1000;
                    }
                    
                }catch(Throwable t){
                    t.printStackTrace();
                }
                if (LemonConfProvider != null && LemonConfProvider.trim().equalsIgnoreCase("ORACLE")) {
                    ((LemonConfOracleProvider)_theInstance).setNewParams(jdbcUrl.trim(), username.trim(), passwd.trim(), verifyDelay, errorVerifyDelay);
                }
            }
        }catch(Throwable t){
            t.printStackTrace();
        }
    }
    
    public static synchronized final LemonConfProvider getInstance() {
        return _theInstance;
    }
    
    public static synchronized final LemonConfProvider getInstance(File fileConf) {
        try {
            if (_theInstance == null) {
                String jdbcUrl = "jdbc:oracle:oci8:@oramon1";
                String username = "lemonread";
                String passwd = "lemon2read";
                String LemonConfProvider = "ORACLE";
                String sVerifyDelay = "86400";
                String sErrorVerifyDelay = "360";
                
                long verifyDelay = 86400*1000;
                long errorVerifyDelay = 360*1000;
                
                try {
                    Properties p = new Properties();
		    FileInputStream fis = new FileInputStream(fileConf);
                    p.load(fis);
		    fis.close();
                    
                    LemonConfProvider = p.getProperty("LemonConfProvider", "ORACLE");
                    jdbcUrl = p.getProperty("LemonConfOracleProvider.jdbcURL", "jdbc:oracle:oci8:@oramon1");
                    username = p.getProperty("LemonConfOracleProvider.username", "lemonread");
                    passwd = p.getProperty("LemonConfOracleProvider.passwd", "lemon2read");
                    
                    sVerifyDelay = p.getProperty("LemonConfOracleProvider.verifyDelay", "86400");
                    sErrorVerifyDelay = p.getProperty("LemonConfOracleProvider.verifyDelay", "360");
                    
                    try {
                        verifyDelay = Long.valueOf(sVerifyDelay.trim()).longValue()*1000;
                    }catch(Throwable t) {
                        verifyDelay = 86400*1000;
                    }

                    try {
                        errorVerifyDelay = Long.valueOf(sErrorVerifyDelay.trim()).longValue()*1000;
                    }catch(Throwable t) {
                        errorVerifyDelay = 360*1000;
                    }
                    
                }catch(Throwable t){
                    t.printStackTrace();
                }
                if (LemonConfProvider != null && LemonConfProvider.trim().equalsIgnoreCase("ORACLE")) {

                    //TODO
                    //Maybe read these params from the same configuration file
                    _theInstance = new LemonConfOracleProvider(jdbcUrl.trim(), username.trim(), passwd.trim(), verifyDelay, errorVerifyDelay);
                    if (_theInstance != null) {
                        new Thread(_theInstance, "( ML ) LemonConfOracleProvider").start();
                    }
                }
                _theInstance.setConfFile(fileConf);
            }
        }catch(Throwable t){
            t.printStackTrace();
        }
        
        return _theInstance;
    }
    
    protected void setNewConf(Hashtable newHostsToClusterMap, Hashtable newMetricsIDToNameMap, Hashtable newMetricsNameToFieldsMap) {
        LemonConf nlc = new LemonConf(newHostsToClusterMap, newMetricsIDToNameMap, newMetricsNameToFieldsMap);
        
        synchronized(syncConf) {
            _theConf  = nlc;
        }
		setChanged();
        notifyObservers();
    }
    
    public void update(Observable o, Object arg) {
        if(o != null && o.equals(dfw)) {
            reloadConf();
        }
    }
}
