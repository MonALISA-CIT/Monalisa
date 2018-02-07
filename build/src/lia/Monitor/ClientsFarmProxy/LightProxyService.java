package lia.Monitor.ClientsFarmProxy;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.Vector;

/**
 * 
 * A "lighter" version for lia.Monitor.ClientsFarmProxy.ProxyService
 * It is a "dumb" proxy; will return null on all methods
 * 
 * @author ramiro
 * 
 */
public class LightProxyService implements ProxyServiceI, Serializable {

    private static final long serialVersionUID = -986984224472050635L;

    /**
     * If this is not changed Jini will give the same SID
     */
    public String _key = "N/A";

    /**
     * @throws RemoteException  
     */
    public Vector getFarms() throws RemoteException {
        return null;
    }

    /**
     * @throws RemoteException  
     */
    public Vector getFarmsByGroup(String[] groups) throws RemoteException {
        return null;
    }

    /**
     * @throws RemoteException  
     */
    public Vector getFarmsIDs() throws RemoteException {
        return null;
    }

    /**
     * @throws RemoteException  
     */
    public Integer getNumberOfClients() throws RemoteException {
        return null;
    }

}
