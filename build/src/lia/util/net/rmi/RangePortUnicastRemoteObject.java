/*
 * $Id: RangePortUnicastRemoteObject.java 7419 2013-10-16 12:56:15Z ramiro $
 */

package lia.util.net.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.exporters.RMIRangePortExporter;
import lia.util.net.TimeoutClientSocketFactory;
import lia.util.net.TimeoutServerSocketFactory;

public class RangePortUnicastRemoteObject extends UnicastRemoteObject {
    private static final long serialVersionUID = -3426582996451771949L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(RangePortUnicastRemoteObject.class.getName());

    private int bindPort = -1;
    private Remote thisRef = null;

    public RangePortUnicastRemoteObject() throws RemoteException {
        this(new TimeoutClientSocketFactory(), new TimeoutServerSocketFactory());
    }

    public RangePortUnicastRemoteObject(RMIClientSocketFactory rcsf, RMIServerSocketFactory rssf)
            throws RemoteException {
        this(rcsf, rssf, true);
    }

    public RangePortUnicastRemoteObject(RMIClientSocketFactory rcsf, RMIServerSocketFactory rssf, boolean shouldExport)
            throws RemoteException {
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (Throwable t) {
            ;
        }

        if (shouldExport) {
            thisRef = RMIRangePortExporter.export(this, rcsf, rssf);

            if (thisRef == null) {
                logger.log(Level.WARNING, " [ RangePortUnicastRemoteObject ] Cannot export!!");
                return;
            }

            bindPort = RMIRangePortExporter.getPort(thisRef);
            logger.log(Level.INFO, " [ RangePortUnicastRemoteObject ] RMI Interface [ " + getClass().getName() + " ]:"
                    + this + " exported on port: " + bindPort);
        } else {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ RangePortUnicastRemoteObject ] will not export " + getClass().getName());
            }
        }
    }

    public Remote getRemote() {
        return thisRef;
    }

    public int getBindPort() {
        return bindPort;
    }
}
