package lia.util.logging;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;

public class InternalLogger implements AppConfigChangeListener {

    private static final InternalLogger _thisInstance = new InternalLogger();
    private final AtomicBoolean debugEnabled = new AtomicBoolean(false);
    
    private InternalLogger() {
        reloadConf();
        AppConfig.addNotifier(this);
    }
    
    public static final InternalLogger getInstance() {
        return _thisInstance;
    }
    
    private final void reloadConf() {
        try {
            debugEnabled.set(AppConfig.getb("lia.util.logging.debugEnabled", false));
        }catch(Throwable t) {
            System.out.println(new Date() + " :- Cannot get debugEnabled flag " + t);
            debugEnabled.set(false);
        }
    }
    
    public boolean isDebugEnabled() {
        return debugEnabled.get();
    }
    
    public void debug(String s) {
        if(debugEnabled.get()) {
            log(s);
        }
    }
    public void log(String s) {
        System.out.println(new Date() + " :- " + s);
    }
    
    public void notifyAppConfigChanged() {
        reloadConf();
    }
}
