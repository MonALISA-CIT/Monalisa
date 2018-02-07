package lia.util.logging;

import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;


public class MLFilterLog implements Filter {

    private boolean canBeBuffered;
    private static final InternalLogger localLogger = InternalLogger.getInstance();

    public MLFilterLog(boolean canBeBuffered) {
        this.canBeBuffered = canBeBuffered;
    }
    
    public boolean isLoggable(LogRecord record) {
        Object[] loggingParams = record.getParameters();
        MLLogEvent mlle = null;
        
        if(loggingParams != null && loggingParams.length > 0) {
            for(int i=0; i<loggingParams.length; i++) {
                if(loggingParams[i] instanceof MLLogEvent) {
                    return true;
                }
            }//end for
        }//if
        
        Level l = MLLoggerConfig.getInstance().getRemoteLevel(record.getSourceClassName());
        if(localLogger.isDebugEnabled()) {
            localLogger.debug(" MLFilterLog:- ( " + record.getSourceClassName() + " ) "+ record.getSourceMethodName() + " = " + l);
        }

        
        if(l != null) {
            //normal logging event
            mlle = new MLLogEvent();
            mlle.canBeBuffered = canBeBuffered;

            if(loggingParams == null || loggingParams.length == 0) {
                if(localLogger.isDebugEnabled()) {
                    localLogger.debug(" MLFilterLog:-  ( " + record.getSourceClassName() + " ) "+ record.getSourceMethodName() + " will be MLLogged!");
                }
                record.setParameters(new Object[]{mlle});
                return true;
            }
            
            Object[] newLoggingParams = new Object[loggingParams.length + 1];
            System.arraycopy(loggingParams, 0, newLoggingParams, 0, loggingParams.length);
            newLoggingParams[loggingParams.length] = mlle;
            record.setParameters(newLoggingParams);
            if(localLogger.isDebugEnabled()) {
                localLogger.debug(" MLFilterLog:-  ( " + record.getSourceClassName() + " ) "+ record.getSourceMethodName() + " will be MLLogged!");
            }
            return true;
        }
        return true;
    }

}
