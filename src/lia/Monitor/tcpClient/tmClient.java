package lia.Monitor.tcpClient;

import java.net.InetAddress;
import java.util.Map;

import lia.Monitor.JiniClient.CommonJini.JiniClient;
import lia.Monitor.monitor.MonMessageClientsProxy;
import net.jini.core.lookup.ServiceID;

public class tmClient extends ConnMessageMux {

	public tmClient(InetAddress address, int port, Map<ServiceID, MonMessageClientsProxy> knownConfiguration, JiniClient jiniClient)
			throws Exception {
		super(address, port, knownConfiguration, jiniClient);
	}
	
	/**
	 * when a message is received and counter is incremented,
	 * notify statistics that will check that a timeout has passed
	 * before validating the change and generate a new value in the graph
	 * @author mluc
	 * @date May 6, 2006
	 */
	@Override
    protected void notifyStatistics() {
		if ( jiniClient!=null && jiniClient.mainClientClass != null){
			if(jiniClient instanceof lia.Monitor.JiniClient.CommonGUI.SerMonitorBase ) {
				lia.Monitor.JiniClient.CommonGUI.SerMonitorBase smb 
					= (lia.Monitor.JiniClient.CommonGUI.SerMonitorBase)jiniClient;
				if ( smb.main!=null && smb.main.sMon!=null )
					smb.main.sMon.newInValue(false);
			}
		}
	}
}
