package lia.Monitor.monitor;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lookup.JoinManager;
import net.jini.lookup.ServiceDiscoveryManager;

/**
 * 
 * Helper class which keeps references to Jini Managers: {@link LookupDiscoveryManager}, {@link ServiceDiscoveryManager},
 * {@link JoinManager}
 * 
 * @author ramiro
 * 
 */
public final class MLJiniManagersProvider {
    private final static Lock rLock;
    private final static Lock wLock;

    private static transient LookupDiscoveryManager ldm;
    private static transient ServiceDiscoveryManager sdm;
    private static transient JoinManager jmngr;

    static {
        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        rLock = rwLock.readLock();
        wLock = rwLock.writeLock();
    }
    
    public static final void setManagers(LookupDiscoveryManager ldm, ServiceDiscoveryManager sdm, JoinManager jmngr) {
        wLock.lock();
        try {
            MLJiniManagersProvider.sdm = sdm;
            MLJiniManagersProvider.ldm = ldm;
            MLJiniManagersProvider.jmngr = jmngr;
        } finally {
            wLock.unlock();
        }
    }
    
    public static final ServiceDiscoveryManager getServiceDiscoveryManager() {
        rLock.lock();
        try {
            return sdm;
        } finally {
            rLock.unlock();
        }
    }

    public static final JoinManager getJoinManager() {
        rLock.lock();
        try {
            return jmngr;
        } finally {
            rLock.unlock();
        }
    }

    public static final LookupDiscoveryManager getLookupDiscoveryManager() {
        rLock.lock();
        try {
            return ldm;
        } finally {
            rLock.unlock();
        }
    }
}
