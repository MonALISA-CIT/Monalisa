package lia.util.logging;

import lia.util.logging.comm.SerMLLogMsg;
import lia.util.logging.relay.MLLogSender;
import lia.util.ntp.NTPDate;


public class MLRemoteLogger extends Thread {
    private static final InternalLogger localLogger = InternalLogger.getInstance();

    private static MLRemoteLogger _thisInstance;
    private static int MAX_QUEUE_SIZE = 30;
    private static long FLUSH_DELAY = 60 * 1000;
    
    MLLogRecord[] buff;
    int cIndex;
    private long lastFlush;
    private boolean hasToRun = true;
    
    private MLRemoteLogger() {
        super(" ( ML ) MLRemoteLogger");
        buff = new MLLogRecord[MAX_QUEUE_SIZE];
        cIndex = 0;
        lastFlush = NTPDate.currentTimeMillis();
        
        try {
            setDaemon(true);
        }catch(Throwable t){
            localLogger.log(" Cannot set daemon MLRemoteLogger");
        }
    }
    
    public synchronized static final MLRemoteLogger getInstance() {
        if(_thisInstance == null) {
            _thisInstance = new MLRemoteLogger();
            _thisInstance.start();
        }
        return _thisInstance;
    }
    
    public void run() {
        while(hasToRun) {
            try {
                try {
                    Thread.sleep(20*1000);
                }catch(Throwable t){
                    
                }
                boolean bShouldFlush = false;
                synchronized(buff) {
                    if(cIndex != 0 && (lastFlush + FLUSH_DELAY < NTPDate.currentTimeMillis())) {
                        bShouldFlush = true;
                    }
                }//end sync
                if(bShouldFlush) {
                    flush();
                }
            } catch(Throwable t) {
                t.printStackTrace();
            }
        }
    }
    
    public void flush() {
        try {
            SerMLLogMsg sml = null;
            synchronized(buff) {
                lastFlush = NTPDate.currentTimeMillis();
                if(cIndex == 0) return;
                sml = new SerMLLogMsg();
                sml.lrs = new MLLogRecord[cIndex];
                System.arraycopy(buff, 0, sml.lrs, 0, cIndex);
                cIndex = 0;
            }
            try {
                MLLogSender.getInstance().sendMessage(sml);
            }catch(Throwable t) {
                t.printStackTrace();
            }
        }catch(Throwable t){
            t.printStackTrace();
        }
    }
    
    public void publish(MLLogRecord mllr) {
        if(mllr.canBeBuffered) {
            synchronized(buff) {
                if(cIndex < MAX_QUEUE_SIZE) {
                    buff[cIndex++] = mllr;
                } else {
                    flush();
                    cIndex = 0;
                }
            }//end synch
        } else {
            SerMLLogMsg sml = new SerMLLogMsg();
            sml.lrs = new MLLogRecord[] {mllr};
            sml.reqNotif = true;
            try {
                MLLogSender.getInstance().sendMessage(sml);
            }catch(Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
