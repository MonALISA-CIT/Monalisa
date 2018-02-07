/*
 * Created on Aug 9, 2007
 * 
 * $Id: AccessControlManager.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.ClientsFarmProxy.acl;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;

/**
 * This class implemets basic Access Control checks for both services and clients. There is 
 * a single instance of this manager per application.
 * <br>
 * Both the clients and the ML services may be filtered by the IP Addresses/Hostname(s). 
 * For the services it is possible to filter the access also by the ServiceID, or the service name
 * <br>
 * The check is done using two sets of lists <code>allowedSet</code> and <conde>bannedSet</code>. The former
 * set prevails, being the first which is checked.
 * <br>
 * The classes interested to receive notifications when the <code>AccessControlManager</code>
 * reloads the configuration may subscribe for such notifications using {@link}addListener method
 * 
 * @author ramiro
 */
public final class AccessControlManager {

    private static final Logger logger = Logger.getLogger(AccessControlManager.class.getName());

    private static final Set<InetAddress> allowedClientsAddresses = new CopyOnWriteArraySet<InetAddress>();
    private static final Set<InetAddress> bannedClientsAddresses = new CopyOnWriteArraySet<InetAddress>();
    private static final Set<InetAddress> allowedServicesAddresses = new CopyOnWriteArraySet<InetAddress>();
    private static final Set<InetAddress> bannedServicesAddresses = new CopyOnWriteArraySet<InetAddress>();
    private static final Set<String> allowedServicesIDs = new CopyOnWriteArraySet<String>();
    private static final Set<String> bannedServicesIDs = new CopyOnWriteArraySet<String>();
    private static final Set<String> allowedServicesNames = new CopyOnWriteArraySet<String>();
    private static final Set<String> bannedServicesNames = new CopyOnWriteArraySet<String>();

    //these fields are used only for initialization ... use for() instead of C&P...or minimize the C&P
    private static final HashMap<String, Set<InetAddress>> inetHMaps = new HashMap<String, Set<InetAddress>>();
    private static final HashMap<String, Set<String>> stringHMaps = new HashMap<String, Set<String>>();

    private static final Set<AccessControlListener> listenersList = new CopyOnWriteArraySet<AccessControlListener>();

    static {

        inetHMaps.put("lia.Monitor.ClientsFarmProxy.acl.AccessControlManager.allowedClients", allowedClientsAddresses);
        inetHMaps.put("lia.Monitor.ClientsFarmProxy.acl.AccessControlManager.bannedClients", bannedClientsAddresses);
        inetHMaps
                .put("lia.Monitor.ClientsFarmProxy.acl.AccessControlManager.allowedServices", allowedServicesAddresses);
        inetHMaps.put("lia.Monitor.ClientsFarmProxy.acl.AccessControlManager.bannedServices", bannedServicesAddresses);

        stringHMaps.put("lia.Monitor.ClientsFarmProxy.acl.AccessControlManager.allowedServicesIDs", allowedServicesIDs);
        stringHMaps.put("lia.Monitor.ClientsFarmProxy.acl.AccessControlManager.bannedServicesIDs", bannedServicesIDs);
        stringHMaps.put("lia.Monitor.ClientsFarmProxy.acl.AccessControlManager.allowedServicesNames",
                allowedServicesNames);
        stringHMaps.put("lia.Monitor.ClientsFarmProxy.acl.AccessControlManager.bannedServicesNames",
                bannedServicesNames);

        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                reloadConfig();
            }
        });

        reloadConfig();
    }

    //configuration helper function
    private static final ArrayList<InetAddress> getInetAddresses(final String[] list) {
        ArrayList<InetAddress> retList = new ArrayList<InetAddress>();
        for (final String sAddress : list) {
            try {
                final InetAddress[] addresses = InetAddress.getAllByName(sAddress);
                for (final InetAddress ia : addresses) {
                    retList.add(ia);
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING,
                        " [ AccessControlManager ] Got exception processing filter addresses list for address: "
                                + sAddress, t);
            }
        }

        return retList;
    }

    //configuration helper function
    private static final void reloadInetAccessList(final String configProperty, final Set<InetAddress> set)
            throws Exception {
        final String[] confList = AppConfig.getVectorProperty(configProperty);
        if ((confList != null) && (confList.length > 0)) {
            final ArrayList<InetAddress> newAddresses = getInetAddresses(confList);
            set.retainAll(newAddresses);
            set.addAll(newAddresses);
        } else {
            set.clear();
        }
    }

    //configuration helper function
    private static final void reloadStringAccessList(final String configProperty, final Set<String> set)
            throws Exception {
        final String[] confList = AppConfig.getVectorProperty(configProperty);
        if ((confList != null) && (confList.length > 0)) {
            List<String> newList = Arrays.asList(confList);
            set.retainAll(newList);
            set.addAll(newList);
        } else {
            set.clear();
        }
    }

    //when the class is loaded or whenever "ml.properties" changes
    private static final void reloadConfig() {

        for (final Map.Entry<String, Set<InetAddress>> entry : inetHMaps.entrySet()) {
            final String property = entry.getKey();
            final Set<InetAddress> set = entry.getValue();

            try {
                reloadInetAccessList(property, set);
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ AccessControlManager ] reloadProps got exception processing Key: "
                        + property + "\n The new set is: " + set, t);
            }
        }//end for

        for (final Map.Entry<String, Set<String>> entry : stringHMaps.entrySet()) {
            final String property = entry.getKey();
            final Set<String> set = entry.getValue();

            try {
                reloadStringAccessList(property, set);
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ AccessControlManager ] reloadProps got exception processing Key: "
                        + property + "\n The new set is: " + set, t);
            }

        }//end for

        StringBuilder sb = new StringBuilder(1024);
        sb.append("\n\n[ AccessControlManager ] [ (re)loadConfig ]\n");
        sb.append("\n allowed clients addresses: ").append(allowedClientsAddresses);
        sb.append("\n banned clients addresses: ").append(bannedClientsAddresses);
        sb.append("\n allowed services addresses: ").append(allowedServicesAddresses);
        sb.append("\n banned services addresses: ").append(bannedServicesAddresses);
        sb.append("\n allowed services IDs: ").append(allowedServicesIDs);
        sb.append("\n banned services IDs: ").append(bannedServicesIDs);
        sb.append("\n allowed services names: ").append(allowedServicesNames);
        sb.append("\n banned services names: ").append(bannedServicesNames).append("\n\n");
        logger.log(Level.CONFIG, sb.toString());

        if (listenersList.size() > 0) {
            new Thread() {
                @Override
                public void run() {
                    setName("(ML) AccessControlManager Internal Notifier Thread");
                    for (final AccessControlListener listener : listenersList) {
                        try {
                            listener.notifyAccessContrlChanged();
                        } catch (Throwable t) {
                            logger.log(Level.WARNING,
                                    " [ AccessControlManager ] [ HANDLED ] Internal Notifier Thread got exception notifying "
                                            + listener, t);
                        }
                    }
                }
            }.start();
        }
    }

    /**
     * 
     * Simple ACL verifier. The function will first check the <code>allowedSet</code> first,
     * and based on its size, or wheter or not the <code>itemToCheck</code> is among its elements
     * will continue to check the <code>bannedSet</code>. 
     * 
     * <br><br>
     * This function will return <code>true</code> in the following cases:
     * <br>
     * <li>Both <code>allowedSet</code> and <code>bannedSet</code> have no elements;
     * <li><code>allowedSet</code> has elements and <code>itemToCheck</code>
     * is among them;
     * <li><code>allowedSet</code> do not have elements and <code>itemToCheck</code>
     * is not among the elements in the <code>bannedSet</code>
     * 
     * <br><br>
     * This function will return <code>false</code> in the following cases:
     * <br>
     * <li><code>allowedSet</code> has elements and <code>itemToCheck</code> 
     * is not among them
     * <li><code>allowedSet</code> has no elements and <code>itemToCheck</code> 
     * is among the elements in the <code>bannedSet</code>
     * 
     * @param <T>
     * @param itemToCheck
     * @param allowedSet
     * @param bannedSet
     * @return true only if itemToCheck obey the conditions above, false otherwise.
     */
    private static final <T> boolean checkAccess(final T itemToCheck, final Set<T> allowedSet, final Set<T> bannedSet) {
        if (allowedSet.size() == 0) {
            if (bannedSet.size() != 0) {
                if (bannedSet.contains(itemToCheck)) {
                    logger.log(Level.WARNING, "[ AccessControlManager ] [ checkAccess ] The item: " + itemToCheck
                            + " is in the banned access list.");
                    return false;
                }
            }
        } else {
            if (!allowedSet.contains(itemToCheck)) {
                logger.log(Level.WARNING, "[ AccessControlManager ] [ checkAccess ] The item: " + itemToCheck
                        + " is not in allowed access list.");
                return false;
            }
        }
        return true;
    }

    /**
     * 
     * ACL-based function to check the client access
     * 
     * @see checkAccess
     * @param clientAddress - The IP Address of the client
     * @return true - if the IP Address obeys the restrictions defined by the
     * <code>lia.Monitor.ClientsFarmProxy.acl.AccessControlManager.allowedClients</code> and
     * <code>lia.Monitor.ClientsFarmProxy.acl.AccessControlManager.bannedClients</code>
     */
    public static final boolean checkClientAccess(final InetAddress clientAddress) {
        return checkAccess(clientAddress, allowedClientsAddresses, bannedClientsAddresses);
    }

    /**
     * 
     * ACL-based function to check the services access. The access control checks
     * are done in the following order <code>serviceAddress</code>, <code>serviceID</code>
     * <code>mlServiceName</code> 
     * 
     * @see checkAccess
     * @param serviceAddress - The IP Address of the client
     * @param serviceID - The <code>String</code> representation of Jini's <code>ServiceID<code> to be checked 
     * @param mlServiceName - The service name
     * @return true - if all the checks succed, false otherwise
     */
    public static final boolean checkServiceAccess(final InetAddress serviceAddress, final String serviceID,
            final String mlServiceName) {
        if (!checkAccess(serviceAddress, allowedServicesAddresses, bannedServicesAddresses)) {
            return false;
        }

        if (!checkAccess(serviceID, allowedServicesIDs, bannedServicesIDs)) {
            return false;
        }

        if (!checkAccess(mlServiceName, allowedServicesNames, bannedServicesNames)) {
            return false;
        }

        return true;
    }

    /**
     * @return - a copy of the lia.Monitor.ClientsFarmProxy.acl.AccessControlManager.allowedClients
     * as seen by the AccessControlManager after the DNS lookup
     */
    public static final Set<InetAddress> getAllowedClientsAddresses() {
        return new CopyOnWriteArraySet<InetAddress>(allowedClientsAddresses);
    }

    /**
     * @return - a copy of the lia.Monitor.ClientsFarmProxy.acl.AccessControlManager.bannedClients
     * as seen by the AccessControlManager after the DNS lookup
     */
    public static final Set<InetAddress> getBannedClientsAddresses() {
        return new CopyOnWriteArraySet<InetAddress>(bannedClientsAddresses);
    }

    /**
     * @return - a copy of the lia.Monitor.ClientsFarmProxy.acl.AccessControlManager.allowedServices
     * as seen by the AccessControlManager after the DNS lookup
     */
    public static final Set<InetAddress> getAllowedServicesAddresses() {
        return new CopyOnWriteArraySet<InetAddress>(allowedServicesAddresses);
    }

    /**
     * @return - a copy of the lia.Monitor.ClientsFarmProxy.acl.AccessControlManager.bannedServices
     * as seen by the AccessControlManager after the DNS lookup
     */
    public static final Set<InetAddress> getBannedServicesAddresses() {
        return new CopyOnWriteArraySet<InetAddress>(bannedServicesAddresses);
    }

    /**
     * @return - a copy of the lia.Monitor.ClientsFarmProxy.acl.AccessControlManager.allowedServicesIDs
     * as seen by the AccessControlManager
     */
    public static final Set<String> getAllowedServicesIDs() {
        return new CopyOnWriteArraySet<String>(allowedServicesIDs);
    }

    /**
     * @return - a copy of the lia.Monitor.ClientsFarmProxy.acl.AccessControlManager.bannedServicesIDs
     * as seen by the AccessControlManager
     */
    public static final Set<String> getBannedServicesIDs() {
        return new CopyOnWriteArraySet<String>(bannedServicesIDs);
    }

    /**
     * @return - a copy of the lia.Monitor.ClientsFarmProxy.acl.AccessControlManager.bannedServicesNames
     * as seen by the AccessControlManager
     */
    public static final Set<String> getAllowedServicesNames() {
        return new CopyOnWriteArraySet<String>(allowedServicesNames);
    }

    /**
     * @return - a copy of the lia.Monitor.ClientsFarmProxy.acl.AccessControlManager.allowedServicesNames
     * as seen by the AccessControlManager
     */
    public static final Set<String> getBannedServicesNames() {
        return new CopyOnWriteArraySet<String>(bannedServicesNames);
    }

    /**
     * @param listener - subscriber interested to receive notifications whenever
     * the <code>AccessControlManager</code> reloads its configuration
     */
    public static final void addListener(final AccessControlListener listener) {
        if (listener == null) {
            throw new NullPointerException("AccessControlManager cannot add null AccessControlListener");
        }
        listenersList.add(listener);
    }

    /**
     * @param listener - the subscriber which is no longer interested to receive
     * notifications when <code>AccessControlManager</code> reloads its configuration
     */
    public static final void removeListener(final AccessControlListener listener) {
        if (listener == null) {
            throw new NullPointerException("AccessControlManager cannot remove null AccessControlListener");
        }
        listenersList.remove(listener);
    }

}
