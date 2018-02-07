package lia.Monitor.ClientsFarmProxy.tunneling;

import lia.Monitor.ClientsFarmProxy.ClientWorker;
import lia.Monitor.ClientsFarmProxy.FarmWorker;
import lia.util.UUID;

/**
 * 
 * This class is used to identify an AppCtrl session between a client and a service
 *  
 * @author ramiro
 */
public class AppCtrlSession {
    
    final UUID sessionID;
    
    final ClientWorker clientWorker;
    final FarmWorker farmWorker;
    
    public AppCtrlSession(UUID sessionID,  ClientWorker clientWorker, FarmWorker farmWorker) {
        this.sessionID = sessionID;
        this.clientWorker = clientWorker;
        this.farmWorker = farmWorker;
    }
}
