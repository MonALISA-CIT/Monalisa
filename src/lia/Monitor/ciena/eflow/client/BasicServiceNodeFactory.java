/*
 * Created on Jul 2, 2012
 */
package lia.Monitor.ciena.eflow.client;

import lia.Monitor.monitor.DataStore;
import lia.Monitor.tcpClient.MLSerClient;
import net.jini.core.lookup.ServiceItem;


/**
 *
 * @author ramiro
 */
public class BasicServiceNodeFactory extends ServiceNodeFactory<BasicServiceNode> {

    /**
     * 
     */
    protected BasicServiceNodeFactory() {
    }

    /* (non-Javadoc)
     * @see lia.Monitor.ciena.eflow.client.ServiceNodeFactory#newServiceNodeInstace(net.jini.core.lookup.ServiceItem, lia.Monitor.monitor.DataStore, lia.Monitor.tcpClient.MLSerClient, java.lang.String, java.lang.String)
     */
    @Override
    public BasicServiceNode newServiceNodeInstace(ServiceItem si, DataStore dataStore, MLSerClient client, String unitName, String ipad) {
        return new BasicServiceNode(si.serviceID, unitName, client);
    }

    public static BasicServiceNodeFactory newInstance() {
        return new BasicServiceNodeFactory();
    }
}
