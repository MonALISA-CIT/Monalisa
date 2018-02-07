package lia.Monitor.ciena.eflow.client;

import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.ConnMessageMux;
import lia.Monitor.tcpClient.MLSerClient;
import net.jini.core.lookup.ServiceID;

/**
 * @author ramiro
 */
public class VCGSerClient extends MLSerClient {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(VCGSerClient.class.getName());

    /**
     * Create a new thread for each service that is discovered
     * 
     * @param sid
     * @param name
     * @param _address
     * @param _msgMux
     * @throws Exception
     */
    public VCGSerClient(ServiceID sid, String name, InetAddress _address, ConnMessageMux _msgMux) throws Exception {
        super(name, _address, _address.getCanonicalHostName(), _msgMux, sid);
        logger.log(Level.INFO, "[SerClient] Created client -> " + this.toString());
    }

    @Override
    public void addLocalClient(final LocalDataFarmClient client, final monPredicate pred) {
        super.addLocalClient(client, pred);
    }

    @Override
    public void addLocalClient(final LocalDataFarmClient client, final String filter) {
        super.addLocalClient(client, filter);
    }
    
    /**
     * @param nfarm
     */
    @Override
    public void newConfig(final MFarm nfarm) {
        // not interested in configs
    }

    /**
     * @param version
     */
    @Override
    public void postSetMLVersion(final String version) {
        // not intersted also
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("VCGSerClient [ServiceName=")
               .append(FarmName)
               .append(", mlVersion=")
               .append(mlVersion)
               .append(", buildNr=")
               .append(buildNr)
               .append(", hostName=")
               .append(hostName)
               .append(", address=")
               .append(address)
               .append("]");
        return builder.toString();
    }
    
    
}
