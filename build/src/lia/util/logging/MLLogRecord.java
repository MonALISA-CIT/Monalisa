package lia.util.logging;

import java.io.Serializable;
import java.util.Date;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import lia.util.Utils;
import lia.util.ntp.NTPDate;

public class MLLogRecord implements Serializable {
    
    /**
     * generated serialVersionUID
     */
    private static final long serialVersionUID = -3482740262412868984L;

    /**
     * The level inherited from LogRecord
     */
    public Level level;

    /**
     * The sourceClassName inherited from LogRecord
     */
    public String sourceClassName;
    
    /**
     * The sourceMethodName inherited from LogRecord
     */
    public String sourceMethodName;
    
    /**
     * The message inherited from LogRecord
     */
    public String message;

    /**
     * The threadID inherited from LogRecord
     */
    public int threadID;

    /**
     * The millis inherited from LogRecord
     */
    public long millis;
    
    /**
     * The stackTrace inherited from Throwable's LogRecord
     */
    public String stackTrace;

    public long ntpMillis;
    
    transient boolean canBeBuffered; 

    private Hashtable logEventParameters;
    
    public static final MLLogRecord getInstance(LogRecord lr, MLLogEvent mlle) {
        if(lr == null) return null;
        
        MLLogRecord mllr = new MLLogRecord();
        mllr.canBeBuffered = mlle.canBeBuffered;
        mllr.level = lr.getLevel();
        mllr.sourceClassName = lr.getSourceClassName();
        mllr.sourceMethodName = lr.getSourceMethodName();
        mllr.threadID = lr.getThreadID();
        mllr.millis = lr.getMillis();
        mllr.message = lr.getMessage();
        
        if (mlle != null && mlle.logParameters != null && mlle.logParameters.size() > 0) {
            mllr.logEventParameters = new Hashtable(mlle.logParameters);
        }
        
        Throwable t = lr.getThrown();
        if(t != null) {
            mllr.stackTrace = Utils.getStackTrace(t);
        }
        
        mllr.ntpMillis = NTPDate.currentTimeMillis();
        
        return mllr;
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder(1024);
        
        sb.append("\n\n === ").append(level).append("\n");
        sb.append(" [ ").append(new Date(ntpMillis)).append(" / ").append(new Date(millis)).append(" ] ");
        sb.append(sourceClassName).append(" ").append(sourceMethodName).append(" TID :- ").append(threadID).append("\n");
        sb.append(message).append("\n");
        
        if(stackTrace != null) {
            sb.append(stackTrace).append("\n");
        }
        
        if(logEventParameters != null) {
            sb.append("\n Ev Parameters:\n").append(logEventParameters.toString());
        }
        sb.append("\n === \n");
        return sb.toString();
    }
}
