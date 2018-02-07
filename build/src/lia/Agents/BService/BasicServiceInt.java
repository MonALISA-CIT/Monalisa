package lia.Agents.BService;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface BasicServiceInt extends Remote {
    public String getName() throws RemoteException;
}
