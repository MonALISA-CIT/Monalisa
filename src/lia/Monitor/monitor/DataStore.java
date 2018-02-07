package lia.Monitor.monitor; 


public interface DataStore extends java.rmi.Remote  {

public void  Register( MonitorClient c, monPredicate p) throws java.rmi.RemoteException;

public void unRegister( MonitorClient c) throws java.rmi.RemoteException;

public void unRegister( MonitorClient c, Integer key) throws java.rmi.RemoteException;

public MFarm confRegister( MonitorClient c ) throws java.rmi.RemoteException;

public String getIPAddress () throws java.rmi.RemoteException;

public String getUnitName() throws java.rmi.RemoteException;

public String getLocalTime() throws java.rmi.RemoteException;


public void addFilter( MonitorFilter mfliter) throws java.rmi.RemoteException;

public String[]  getFilterList() throws java.rmi.RemoteException;

public void Register( MonitorClient c, String filter) throws java.rmi.RemoteException;
 
}
