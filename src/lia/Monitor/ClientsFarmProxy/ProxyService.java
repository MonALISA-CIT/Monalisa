/*
 * $Id: ProxyService.java 7372 2013-04-04 15:38:50Z ramiro $
 */

package lia.Monitor.ClientsFarmProxy;

import java.rmi.RemoteException;
import java.util.Vector;

/**
 * 
 * @author mickyt
 * 
 */
public class ProxyService implements ProxyServiceI, java.io.Serializable {

    private static final long serialVersionUID = -6357564605550117036L;
    private transient ServiceI service;

    /**
     * @throws RemoteException  
     */
    public ProxyService() throws RemoteException {
    }

    /**
     * @throws RemoteException  
     */
    public ProxyService(ServiceI service) throws RemoteException {
        this.service = service;
        if (this.service == null) {
            System.out
                    .println("ProxyService ====> service null ... IN CONSTRUCTORUL DE LA PROXY SERVICE :((:((:(((((((((((");
        } else {
            System.out.println("ProxyService ====> service nu e null ... IN CONSTRUCTORUL DE LA PROXY SERVICE ");
        }
    } //constructor

    /**
     * 
     * @throws RemoteException  
     */
    public Vector getFarms() throws RemoteException {
        return service.getFarms();
    } //getFarms

    /**
     * 
     * @throws RemoteException  
     */
    public Vector getFarmsByGroup(String[] groups) throws RemoteException {
        if (service == null) {
            System.out.println("Service e null");
        }
        return service.getFarmsByGroup(groups);
    } //getFarmsByGroup

    /**
     * 
     * @throws RemoteException  
     */
    public Vector getFarmsIDs() throws RemoteException {
        if (service == null) {
            System.out.println("ProxyService =====> service este null");
        }
        return service.getFarmsIDs();
    }

    /**
     * 
     * @throws RemoteException  
     */
    public Integer getNumberOfClients() throws RemoteException {
        return service.getNumberOfClients();
    }

}
