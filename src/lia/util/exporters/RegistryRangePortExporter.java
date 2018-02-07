package lia.util.exporters;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RegistryRangePortExporter extends RangePortExporter {
    /** The Logger */
    private static final Logger logger = Logger.getLogger(RegistryRangePortExporter.class.getName());

    /**
     * 
     * @return exportedPort
     */
    public static int createRegistry() throws Exception {
        try {
            for (int bindPort = MIN_BIND_PORT; bindPort <= MAX_BIND_PORT; bindPort++) {
                try {
                    if (allocatedPorts.containsValue(Integer.valueOf(bindPort))) {
                        continue;
                    }
                    Registry registry = LocateRegistry.createRegistry(bindPort);
                    allocatedPorts.put(registry, Integer.valueOf(bindPort));
                    return bindPort;
                } catch (Throwable t) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.WARNING, "Exception...Trying next port available", t);
                    }
                    if (bindPort == MAX_BIND_PORT) {
                        Exception ret = new Exception("MAX_BIND_PORT (" + MAX_BIND_PORT + ") reached");
                        throw ret;
                    }
                }//catch
            }
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "General Exception", t);
            throw new RemoteException("Unable to export Error: " + t.getMessage());
        }
        //we should not get here!!!
        return Registry.REGISTRY_PORT;

    }
}
