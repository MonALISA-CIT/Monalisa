package lia.Monitor.ClientsFarmProxy;

import java.util.Vector;

import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;

public interface ServiceI {

    // register the service - will not be available throw proxy
    public boolean register() ;
    
    public void unregisterMessages (int clientID) ;
    
    // find farm services - will not be available throw proxy
    public ServiceItem[] getFarmServices() ;
    
    //send a message
    //public void sendMessageFarm (Object message) ;
    
    //receive message from farm
    //public Vector getMessageFarm (ServiceID farmID);

    //send a message to a client
    //public void sendMessageClient (Object message) ;
	
    //get message from a client
   // public Vector getMessageClient (int clientID);

    public Vector getFarms() ;
    
    public Vector getFarmsByGroup (String[] groups);
    
    public Vector getFarmsIDs();
    
    public ServiceID getProxyID();
    
    public Integer getNumberOfClients();
    
}
