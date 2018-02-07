/*
 * Created on Aug 27, 2007
 */
package lia.util.threads;

import java.util.concurrent.ScheduledExecutorService;


/**
 * Helper class which provides the thread pools / executors inside the ML Framework
 *  
 * @author ramiro
 */
public class MonALISAExecutors {

    private static final ScheduledExecutorService _mlHelperScheduledExecutorService;
    private static final ScheduledExecutorService _mlStoreScheduledExecutorService;
    private static final ScheduledExecutorService _mlNetworkScheduledExecutorService;
    
    static {
        ScheduledExecutorService ses = null;
        try  {
            ses = MLExecutorsFactory.getScheduledExecutorService("lia.Monitor.Executor"); 
        } catch(Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(t);
        }
        
        _mlHelperScheduledExecutorService = ses;

        try  {
            ses = MLExecutorsFactory.getScheduledExecutorService("lia.Monitor.Store.Executor"); 
        } catch(Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(t);
        }
        
        _mlStoreScheduledExecutorService = ses;
        
        try  {
            ses = MLExecutorsFactory.getScheduledExecutorService("lia.Monitor.Network.Executor"); 
        } catch(Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(t);
        }
        
        _mlNetworkScheduledExecutorService = ses;
    }
    
    public static final ScheduledExecutorService getMLHelperExecutor() {
        return _mlHelperScheduledExecutorService;
    }

    public static final ScheduledExecutorService getMLStoreExecutor() {
        return _mlStoreScheduledExecutorService;
    }

    public static final ScheduledExecutorService getMLNetworkExecutor() {
        return _mlNetworkScheduledExecutorService;
    }
}
