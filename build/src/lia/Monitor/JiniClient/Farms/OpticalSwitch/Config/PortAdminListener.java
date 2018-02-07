package lia.Monitor.JiniClient.Farms.OpticalSwitch.Config;

/**
 * An interface that needs to be implemented by any listener for port administration events...
 */
public interface PortAdminListener {

	public void changePortState(String portName, String newSignalType);
	
	public void disconnectPorts(String inputPort, String outputPort, boolean fullDuplex);
	
	public void connectPorts(String inputPort, String outputPort, boolean fullDuplex);
	
} // end of interface PortAdminListener
