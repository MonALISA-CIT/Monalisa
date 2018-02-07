package lia.Monitor.control;

import java.rmi.RemoteException;

import lia.Monitor.monitor.MFarm;

/**
 
 */
public interface ControlI /* extends Remote */{
	public void updateConfig(MFarm farm , String farmName) throws RemoteException;
	public void removeFarm(MFarm farm) throws RemoteException;
	public void addFarm(MFarm farm) throws RemoteException;

}
