package lia.util.logging;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;


public class MLLoggerConfig {
    private static MLLoggerConfig mlLoggerConfig;
    private static final InternalLogger localLogger = InternalLogger.getInstance();

    private HashMap remoteLevels;

    public static final synchronized MLLoggerConfig getInstance() {
        if(mlLoggerConfig == null) {
            mlLoggerConfig = new MLLoggerConfig();
        }
        return mlLoggerConfig;
    }
    
    private MLLoggerConfig() {
        remoteLevels = new HashMap();
    }
    
    public Level getRemoteLevel(String sLogName) {
        if(localLogger.isDebugEnabled()) {
            localLogger.debug(" [ MLLC ] getRemoteLevel (" + sLogName +")");
        }
        synchronized(remoteLevels) {
            if(remoteLevels.size() == 0) return null;
            if (sLogName == null) return null;
            String parent = sLogName;
            while (parent != null && parent.length() != 0) {
                Level l = (Level)remoteLevels.get(parent);
                if(l != null) {
                    if(localLogger.isDebugEnabled()) {
                        localLogger.debug(" [ MLLC ] getRemoteLevel (" + sLogName +") = " + l);
                    }
                    return l;
                }
                int ix = parent.lastIndexOf(".");
                if(ix == -1) break;
                parent = parent.substring(0, ix);
            }
            if(localLogger.isDebugEnabled()) {
                localLogger.debug(" [ MLLC ] getRemoteLevel (" + sLogName +") = " + null);
            }
            return null;
        }
    }

    public void notifyLocalProps(final Properties localProperties) {
        synchronized(remoteLevels) {
            //check if there are ".remotelevels" which have dissapear
            for(Iterator it = remoteLevels.keySet().iterator(); it.hasNext(); ) {
                String key = (String)it.next();
                if(!localProperties.containsKey(key + ".remotelevel")) {
                    it.remove();
                }
            }
            
            for(Iterator it = localProperties.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry entry = (Map.Entry)it.next();
                String key = (String) entry.getKey();
                String value = (String) entry.getValue();
                
                int idx = key.indexOf(".remotelevel");
                if(idx < 0) {
                    continue;
                }
                
                String sLoggerName = key.substring(0, idx);
                
                Level l = null;
                try {
                    l = Level.parse(value);
                }catch(Throwable t){}
                if(l == null) {
                    remoteLevels.remove(sLoggerName);
                }
                Logger logger = Logger.getLogger(sLoggerName);
                logger.setLevel(l);
                remoteLevels.put(sLoggerName, l);
            }//for
            if(localLogger.isDebugEnabled()) {
                localLogger.debug(" [ MLLC ] notifyLocalProps: " + remoteLevels.toString());
            }

        }//synchronized
    }
}
