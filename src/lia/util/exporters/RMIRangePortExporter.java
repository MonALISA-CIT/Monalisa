/*
 * Created on Jul 11, 2003
 */
package lia.util.exporters;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * All RemoteObjects MUST be exported using this class!
 * 
 */
public final class RMIRangePortExporter extends RangePortExporter {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(RMIRangePortExporter.class.getName());

    public final static Remote export(Remote remoteObject) throws RemoteException {
        return export(remoteObject, null, null);
    }

    public final static Remote export(Remote remoteObject, RMIClientSocketFactory rcsf, RMIServerSocketFactory rssf)
            throws RemoteException {
        Remote retv = null;

        try {
            for (int bindPort = MIN_BIND_PORT; bindPort <= MAX_BIND_PORT; bindPort++) {
                try {
                    if (allocatedPorts.containsValue(Integer.valueOf(bindPort))) {
                        continue;
                    }

                    if ((rcsf != null) && (rssf != null)) {
                        retv = UnicastRemoteObject.exportObject(remoteObject, bindPort, rcsf, rssf);
                    } else {
                        retv = UnicastRemoteObject.exportObject(remoteObject, bindPort);
                    }
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "[ RMIRangePortExporter ] exported [ " + bindPort + ", " + remoteObject
                                + " ] ");
                    }
                    allocatedPorts.put(retv, Integer.valueOf(bindPort));
                    return retv;
                } catch (Throwable t) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE,
                                " [ RMIRangePortExporter ] [ HANDLED ] Exception...Trying next port available", t);
                    }
                    if (bindPort == MAX_BIND_PORT) {
                        RemoteException ret = new RemoteException("MAX_BIND_PORT (" + MAX_BIND_PORT + ") reached");
                        throw ret;
                    }
                }//catch
            }
        } catch (Throwable t) {
            logger.log(Level.SEVERE, " [ RMIRangePortExporter ] General Exception", t);
            throw new RemoteException("Unable to export RemoteObject [ " + remoteObject + " ] Error: " + t.getMessage());
        }

        //we should not get here!!!
        return retv;
    }

    public final static boolean isExported(Remote obj) {
        return allocatedPorts.containsKey(obj);
    }

    public final static int getPort(Remote obj) {
        Integer bindPort = (Integer) allocatedPorts.get(obj);

        if (bindPort != null) {
            return bindPort.intValue();
        }

        return -1;
    }
}