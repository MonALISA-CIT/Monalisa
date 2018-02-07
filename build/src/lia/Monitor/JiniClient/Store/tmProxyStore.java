package lia.Monitor.JiniClient.Store;

import java.net.InetAddress;
import java.util.Hashtable;

import lia.Monitor.JiniClient.CommonJini.JiniClient;
import lia.Monitor.monitor.MonMessageClientsProxy;
import lia.Monitor.tcpClient.ConnMessageMux;
import net.jini.core.lookup.ServiceID;

/**
 *
 */
public class tmProxyStore extends ConnMessageMux {

	/**
	 * @param address
	 * @param port
	 * @param knownConfiguration
	 * @param jiniClient
	 * @throws Exception
	 */
	public tmProxyStore(final InetAddress address, final int port, final Hashtable<ServiceID, MonMessageClientsProxy> knownConfiguration, final JiniClient jiniClient) throws Exception {
		super(address, port, knownConfiguration, jiniClient);
	}
	
	@Override
	protected boolean discardConfig() {
		return true;
	}

}
