package lia.Monitor.ClientsFarmProxy;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.Vector;

public interface ProxyServiceI extends java.rmi.Remote, Serializable {

    public Vector getFarms() throws RemoteException;

    public Vector getFarmsByGroup(String[] groups) throws RemoteException;

    public Vector getFarmsIDs() throws RemoteException;

    public Integer getNumberOfClients() throws RemoteException;

}
