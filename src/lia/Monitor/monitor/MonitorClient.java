package lia.Monitor.monitor; 


public interface MonitorClient extends java.rmi.Remote
{
 public void notifyResult ( Object res , int pid)  throws java.rmi.RemoteException;
 public void newConfig( MFarm f )  throws java.rmi.RemoteException;

public void notifyResult ( Object res , String filter)  throws java.rmi.RemoteException;

}
