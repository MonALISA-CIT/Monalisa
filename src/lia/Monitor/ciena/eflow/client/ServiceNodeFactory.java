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
public abstract class ServiceNodeFactory<T extends BasicServiceNode> {

    public abstract T newServiceNodeInstace(ServiceItem si, DataStore dataStore, MLSerClient client, String unitName, String ipad);

}
