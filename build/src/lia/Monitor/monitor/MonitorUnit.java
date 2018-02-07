package lia.Monitor.monitor;

public interface MonitorUnit extends java.rmi.Remote {
    public java.util.Vector getConfig() throws java.rmi.RemoteException;
    public String getUnitName() throws java.rmi.RemoteException;

    public String ConfigAdd(String cluster, String node, String module, long time) throws java.rmi.RemoteException;

    public String ConfigRemove(String cluster, String node, String module) throws java.rmi.RemoteException;
    public MFarm init() throws java.rmi.RemoteException;
    public void remove(String name) throws java.rmi.RemoteException;
    public String updateReflector() throws java.rmi.RemoteException;
    public String restartReflector() throws java.rmi.RemoteException;
    public boolean isVRVSFarm() throws java.rmi.RemoteException;
    public void restartML() throws java.rmi.RemoteException;
    public void stopML() throws java.rmi.RemoteException;
}
