package lia.util.logging;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.FileHandler;
import java.util.logging.LogRecord;


public class MLFileHandler extends FileHandler {

    private static final InternalLogger localLogger = InternalLogger.getInstance();
    
    public MLFileHandler() throws IOException, SecurityException {
        super();
        setFilter(new MLFilterLog(true));
    }
    
    public void publish(LogRecord record) {
        super.publish(record);
        Object[] params = record.getParameters();
        if(localLogger.isDebugEnabled()) {
            localLogger.debug(" MLFileHandler :- ( " + record.getSourceClassName() + " ) " + record.getSourceMethodName());
        }
        
        if (params != null) {

            if(localLogger.isDebugEnabled()) {
                localLogger.debug(" MLFileHandler :- ( " + record.getSourceClassName() + " ) NON null params!" + Arrays.toString(params));
            }
            //find the position of MLLogEvent object or Throwable, if any
            MLLogEvent mlle =null;
            boolean foundThrowable = false;
            ArrayList newParamsList = new ArrayList();
            
            for(int i=0; i<params.length; i++) {
                if (params[i] != null) {
                    
                    if(mlle == null && params[i] instanceof MLLogEvent) {
                        if(localLogger.isDebugEnabled()) {
                            localLogger.debug(" MLFileHandler :- ( " + record.getSourceClassName() + " ) shall be MLLogged!");
                        }

                        mlle = (MLLogEvent)params[i];
                        continue;
                    }
                    
                    if(!foundThrowable && params[i] instanceof Throwable) {
                        foundThrowable = true;
                        record.setThrown((Throwable)params[i]);
                        continue;
                    }
                    
                    newParamsList.add(params[i]);
                }
            }//for
            
            Object[] newParams = null;
            if(newParamsList.size() > 0) {
                newParams = newParamsList.toArray(new Object[newParamsList.size()]);
            }
            record.setParameters(newParams);
            
            //if MLLogEvent was found ... trigger a notification
            if(mlle != null) {
                MLLogRecord mllr = MLLogRecord.getInstance(record, mlle);
                if(mllr.sourceClassName != null && mllr.sourceClassName.indexOf("lia.util.logging") == -1) {
                    MLRemoteLogger.getInstance().publish(mllr);
                    if(localLogger.isDebugEnabled()) {
                        localLogger.debug(" MLFileHandler :- ( " + record.getSourceClassName() + " ) will be MLLogged!");
                    }
                }
            }
            
        }//if (params != null)
        
    }

}
