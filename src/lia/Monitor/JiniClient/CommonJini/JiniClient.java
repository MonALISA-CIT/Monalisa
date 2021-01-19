package lia.Monitor.JiniClient.CommonJini;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.rmi.RMISecurityManager;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import lia.Monitor.ClientsFarmProxy.ProxyServiceEntry;
import lia.Monitor.JiniClient.CommonGUI.IpAddrCache;
import lia.Monitor.JiniSerFarmMon.MLLUSHelper;
import lia.Monitor.Store.FarmBan;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.GenericMLEntry;
import lia.Monitor.monitor.MLControlEntry;
import lia.Monitor.monitor.MLJiniManagersProvider;
import lia.Monitor.monitor.MonMessageClientsProxy;
import lia.Monitor.monitor.MonaLisaEntry;
import lia.Monitor.monitor.SiteInfoEntry;
import lia.util.JiniConfigProvider;
import lia.util.SecureContextExecutor;
import lia.util.Utils;
import net.jini.config.Configuration;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lookup.ServiceDiscoveryManager;

public abstract class JiniClient extends Thread implements AppConfigChangeListener {

    private static final String default_LUDs = "monalisa.cern.ch";

    private static final Logger logger = Logger.getLogger(JiniClient.class.getName());

    public volatile LookupDiscoveryManager lookupDiscoveryManager = null;

    private volatile ServiceDiscoveryManager sdm;

    public static volatile ServiceItem proxyService;

    public final ConcurrentMap<ServiceID, ServiceItem> farms;

    protected final Vector<ServiceItem> allFarms;

    public final ConcurrentMap<String, Integer> SGroups;

    public Class<?> mainClientClass;

    protected MLLUSHelper mlLusHelper;

    private boolean startMLLusHelper;

    private boolean useFarmBan;

    public String topoSerURL = null;

    // parameters used for proxy balancing
    private volatile String asC = null;

    private volatile String netC = null;

    private volatile String countryC = null;

    private volatile String continentC = null;

    private volatile ServiceItem foundProxy = null;

    private volatile boolean badConnection = false;

    private final Object sync = new Object();

    private final ConcurrentHashMap<ServiceID, Long> proxyError;

    // can be specified as lia.Monitor.JiniClient.CommonJini.PROXY_ERROR_INTERVAL ( in seconds )
    // it will be reloaded by reloadConfig();
    private static final AtomicLong PROXY_ERROR_INTERVAL = new AtomicLong(TimeUnit.SECONDS.toNanos(300));

    protected boolean hasToRun = true;

    /**
     * number of connections to proxies made since the client started<br>
     * is incremented by each dying connection ( @see lia.Monitor.tcpClient.tmClient )
     */
    private int nFailedConns = 0;

    /** number of invalid messages received */
    private int nInvalidMsgCount = 0;

    /** procentual status of messages buffer in proxy; greater value means decaying status */
    private double dProxyMsgBufStatus = 0;

    /**
     * start the test on how well the proxy messages buffer mechanism works:
     * each message received introduces an additional delay of 200 ms
     */
    private volatile boolean bTestProxyMsgBuff = false;

    public JiniClient() {
        super("(ML) JiniClient ");
        reloadConfig();
        SGroups = new ConcurrentHashMap<String, Integer>();
        useFarmBan = false;
        startMLLusHelper = false;
        mainClientClass = null;
        farms = new ConcurrentHashMap<ServiceID, ServiceItem>();
        allFarms = new Vector<ServiceItem>();
        proxyError = new ConcurrentHashMap<ServiceID, Long>();
    } // JiniClient constructor

    /**
     * This is used to select the preference file
     * 
     * @param mainClientClassName
     *            - the main class of the current client
     */
    public JiniClient(Class<?> mainClientClass, boolean startMLLusHelper, boolean useFarmBan) {
        this();
        this.mainClientClass = mainClientClass;
        this.useFarmBan = useFarmBan;
        this.startMLLusHelper = startMLLusHelper;
        if (useFarmBan) {
            FarmBan.setJiniClient(this);
        }
    }

    public static final String getProxyIP(ServiceItem proxyItem) {
        final ServiceItem ps = proxyItem;

        if ((ps != null) && (ps.attributeSets != null)) {
            for (Entry e : ps.attributeSets) {
                if ((e != null) && (e instanceof ProxyServiceEntry)) {
                    return ((ProxyServiceEntry) e).ipAddress;
                }
            }
        }

        return null;
    }

    /**
     * Get the IP of the proxy currently in use
     * 
     * @return IP as string or null if not connected
     */
    public static String getProxyIP() {
        return getProxyIP(proxyService);
    }

    public void stopIt() {
        hasToRun = false;
    }

    /** Auxiliary method used to check if for a farm the groups changed meanwhile or not ... */
    private final boolean checkSameGroups(final String gr1, final String gr2) {

        final String groups1[] = gr1.split(",");
        final String groups2[] = gr2.split(",");
        if ((groups1 == null) && (groups2 == null)) {
            return true;
        }
        if ((groups1 == null) && (groups2 != null)) {
            return false;
        }
        if ((groups1 != null) && (groups2 == null)) {
            return false;
        }
        if (groups1.length != groups2.length) {
            return false;
        }
        for (String element : groups1) {
            boolean found = false;
            for (String element2 : groups2) {
                if (element.equals(element2)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /**
     * It seems that we need to compare not only if we have added the sid, but also if information haven't changed
     * for ServiceItem (for farms)
     */
    private boolean compareServiceItems(ServiceItem old_si, ServiceItem new_si) {

        MLControlEntry old_mlce = getEntry(old_si, MLControlEntry.class);
        MLControlEntry new_mlce = getEntry(new_si, MLControlEntry.class);
        if ((old_mlce == null) && (new_mlce != null)) {
            return false;
        }
        if ((old_mlce != null) && (new_mlce == null)) {
            return false;
        }
        if ((old_mlce != null) && (new_mlce != null)) {

            // System.out.println("old_controlPort="+old_mlce.ControlPort);
            // System.out.println("new_controlPort="+new_mlce.ControlPort);

            if ((old_mlce.ControlPort == null) && (new_mlce.ControlPort != null)) {
                return false;
            }
            if ((old_mlce.ControlPort != null) && (new_mlce.ControlPort == null)) {
                return false;
            }
            if ((old_mlce.ControlPort != null) && (new_mlce.ControlPort != null)
                    && !old_mlce.ControlPort.equals(new_mlce.ControlPort)) {
                return false;
            }
        }
        SiteInfoEntry old_sie = getEntry(old_si, SiteInfoEntry.class);
        SiteInfoEntry new_sie = getEntry(new_si, SiteInfoEntry.class);
        if ((old_sie == null) && (new_sie != null)) {
            return false;
        }
        if ((old_sie != null) && (new_sie == null)) {
            return false;
        }
        if ((old_sie != null) && (new_sie != null)) {
            if ((old_sie.IPAddress == null) && (new_sie.IPAddress != null)) {
                return false;
            }
            if ((old_sie.IPAddress != null) && (new_sie.IPAddress == null)) {
                return false;
            }
            if ((old_sie.IPAddress != null) && (new_sie.IPAddress != null)
                    && !old_sie.IPAddress.equals(new_sie.IPAddress)) {
                return false;
            }
            if ((old_sie.UnitName == null) && (new_sie.UnitName != null)) {
                return false;
            }
            if ((old_sie.UnitName != null) && (new_sie.UnitName == null)) {
                return false;
            }
            if ((old_sie.UnitName != null) && (new_sie.UnitName != null) && !old_sie.UnitName.equals(new_sie.UnitName)) {
                return false;
            }
            if ((old_sie.ML_PORT == null) && (new_sie.ML_PORT != null)) {
                return false;
            }
            if ((old_sie.ML_PORT != null) && (new_sie.ML_PORT == null)) {
                return false;
            }
            if ((old_sie.ML_PORT != null) && (new_sie.ML_PORT != null) && !old_sie.ML_PORT.equals(new_sie.ML_PORT)) {
                return false;
            }

            // System.out.println("old_registryPort="+old_sie.REGISTRY_PORT);
            // System.out.println("new_registryPort="+new_sie.REGISTRY_PORT);

            if ((old_sie.REGISTRY_PORT == null) && (new_sie.REGISTRY_PORT != null)) {
                return false;
            }
            if ((old_sie.REGISTRY_PORT != null) && (new_sie.REGISTRY_PORT == null)) {
                return false;
            }
            if ((old_sie.REGISTRY_PORT != null) && (new_sie.REGISTRY_PORT != null)
                    && !old_sie.REGISTRY_PORT.equals(new_sie.REGISTRY_PORT)) {
                return false;
            }
        }
        GenericMLEntry old_gmle = getEntry(old_si, GenericMLEntry.class);
        GenericMLEntry new_gmle = getEntry(new_si, GenericMLEntry.class);
        if ((old_gmle == null) && (new_gmle != null)) {
            return false;
        }
        if ((old_gmle != null) && (new_gmle == null)) {
            return false;
        }
        if ((old_gmle != null) && (new_gmle != null)) {
            if ((old_gmle.hash == null) && (new_gmle.hash != null)) {
                return false;
            }
            if ((old_gmle.hash != null) && (new_gmle.hash == null)) {
                return false;
            }
            if ((old_gmle.hash != null) && (new_gmle.hash != null)) {
                if (old_gmle.hash.containsKey("hostName") && !new_gmle.hash.containsKey("hostName")) {
                    return false;
                }
                if (!old_gmle.hash.containsKey("hostName") && new_gmle.hash.containsKey("hostName")) {
                    return false;
                }
                if (old_gmle.hash.containsKey("hostName") && new_gmle.hash.containsKey("hostName")
                        && !old_gmle.hash.get("hostName").equals(new_gmle.hash.get("hostName"))) {
                    return false;
                }
                if (old_gmle.hash.containsKey("ipAddress") && !new_gmle.hash.containsKey("ipAddress")) {
                    return false;
                }
                if (!old_gmle.hash.containsKey("ipAddress") && new_gmle.hash.containsKey("ipAddress")) {
                    return false;
                }
                if (old_gmle.hash.containsKey("ipAddress") && new_gmle.hash.containsKey("ipAddress")
                        && !old_gmle.hash.get("ipAddress").equals(new_gmle.hash.get("ipAddress"))) {
                    return false;
                }
            }
        }
        MonaLisaEntry old_mle = getEntry(old_si, MonaLisaEntry.class);
        MonaLisaEntry new_mle = getEntry(new_si, MonaLisaEntry.class);
        if ((old_mle == null) && (new_mle != null)) {
            return false;
        }
        if ((old_mle != null) && (new_mle == null)) {
            return false;
        }
        if ((old_mle != null) && (new_mle != null)) {
            // check latitude
            if ((old_mle.LAT == null) && (new_mle.LAT != null)) {
                return false;
            }
            if ((old_mle.LAT != null) && (new_mle.LAT == null)) {
                return false;
            }
            if ((old_mle.LAT != null) && (new_mle.LAT != null) && !old_mle.LAT.equals(new_mle.LAT)) {
                return false;
            }
            // check longitude
            if ((old_mle.LONG == null) && (new_mle.LONG != null)) {
                return false;
            }
            if ((old_mle.LONG != null) && (new_mle.LONG == null)) {
                return false;
            }
            if ((old_mle.LONG != null) && (new_mle.LONG != null) && !old_mle.LONG.equals(new_mle.LONG)) {
                return false;
            }
            // check group
            if ((old_mle.Group == null) && (new_mle.Group != null)) {
                return false;
            }
            if ((old_mle.Group != null) && (new_mle.Group == null)) {
                return false;
            }
            if ((old_mle.Group != null) && (new_mle.Group != null) && !checkSameGroups(old_mle.Group, new_mle.Group)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkGMLEForPortMap(ServiceItem old_si, ServiceItem new_si) {

        GenericMLEntry old_gmle = getEntry(old_si, GenericMLEntry.class);
        GenericMLEntry new_gmle = getEntry(new_si, GenericMLEntry.class);

        // System.out.println("old_gmle="+old_gmle.hash);
        // System.out.println("new_gmle="+new_gmle.hash);

        if ((old_gmle != null) && (old_gmle.hash != null) && (new_gmle != null) && (new_gmle.hash != null)) {
            if (new_gmle.hash.containsKey("OS_PortMap")) {
                return false;
            }
        }
        return true;
    }

    public void actualizeFarms(ArrayList<ServiceItem> activeFarms) {
        if (activeFarms == null) {
            return;
        }

        HashMap<ServiceID, ServiceItem> h = new HashMap<ServiceID, ServiceItem>();
        boolean addedFarm = false;
        // get new farms
        for (final ServiceItem farm : activeFarms) {

            /* extra checking - make sure that even if we have the config the farm data didn't changed meanwhile */
            final ServiceItem old_si = farms.get(farm.serviceID);
            if (old_si != null) {
                if (!compareServiceItems(old_si, farm)) { // so the configuration changed, remove the old one
                    removeNode(farm.serviceID);
                    farms.remove(farm.serviceID);
                } else {
                    try {
                        if (!checkGMLEForPortMap(old_si, farm)) {
                            GenericMLEntry gmle = getEntry(farm, GenericMLEntry.class);
                            if ((gmle != null) && (gmle.hash != null) && gmle.hash.containsKey("OS_PortMap")) {
                                ArrayList<?> list = (ArrayList<?>) gmle.hash.get("OS_PortMap");
                                portMapChanged(farm.serviceID, list);
                            }
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }

            if (!farms.containsKey(farm.serviceID) && knownConfiguration(farm.serviceID)) { // a new farm...
                if (!isBanned(farm)) {
                    if (AddMonitorUnit(farm)) {
                        farms.put(farm.serviceID, farm);
                        addedFarm = true;
                    } else {
                        System.out.println("Not re-adding farm " + farm.serviceID);
                    }
                }
            }

            h.put(farm.serviceID, farm);
        } // for
          // if added new farms, before removing or adding othres, wait for this addition to finish,
          // in order to allow initialization of all refrences to the newly added farms. If this is
          // skipped, it might be possible to remove a farm before completely adding it, so that
          // the snodes (from SerMonitorBase) and farms (from JiniClient) has become unsynchronized.
        if (addedFarm) {
            waitServiceThreads("...");
        }

        // remove old farm
        for (Iterator<ServiceID> it = farms.keySet().iterator(); it.hasNext();) {
            final ServiceID farmID = it.next();
            try {
                final ServiceItem sit = h.get(farmID);
                if (sit == null) { // an old farm
                    // first remove it from the list
                    it.remove();
                    // then call other cleanup functions, that might (and do) fail (sometimes)
                    removeNode(farmID);
                } else {
                    farms.put(farmID, sit);
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error removing old farm: " + farmID, t);
            }
        } // for

        // catac: THIS SHOULD NOT BE HERE!
        // MainBase.getInstance().refreshNumberOfNodes();

    } // actializeFarms

    private final Map<ServiceID, Long> htFarmBanNotifications = new ConcurrentHashMap<ServiceID, Long>();

    public boolean isBanned(ServiceItem si) {
        if (!useFarmBan) {
            return false;
        }
        SiteInfoEntry sie = getEntry(si, SiteInfoEntry.class);
        String ipad = null;
        String un = null;

        if (sie != null) {
            ipad = sie.IPAddress;
            un = sie.UnitName;

            if (FarmBan.isFarmBanned(un) || FarmBan.isIPBanned(ipad)) {
                Long l = htFarmBanNotifications.get(si.serviceID);

                if ((l == null) || (TimeUnit.NANOSECONDS.toMinutes(Utils.nanoNow() - l.longValue()) > 30)) {
                    System.out.println("Farm is banned: " + si.serviceID + " (" + un + " @ " + ipad + ")");
                    htFarmBanNotifications.put(si.serviceID, Long.valueOf(Utils.nanoNow()));
                }

                return true;
            }
        }
        System.out.println("A new farm: " + si.serviceID + " (" + un + " @ " + ipad + ")");
        return false;
    }

    public void reloadNode(String sFarmName) {
        for (ServiceItem si : farms.values()) {
            SiteInfoEntry sie = getEntry(si, SiteInfoEntry.class);
            String un = sie != null ? sie.UnitName : null;

            if ((un != null) && un.equals(sFarmName)) {
                removeNode(si.serviceID); // force rediscovery
            }
        }
    }

    public void reloadIP(String sIP) {

        for (final ServiceItem si : farms.values()) {

            SiteInfoEntry sie = getEntry(si, SiteInfoEntry.class);
            String ipad = sie != null ? sie.IPAddress : null;

            if ((ipad != null) && ipad.equals(sIP)) {
                removeNode(si.serviceID); // force rediscovery
            }
        }
    }

    public static final <T extends Entry> T getEntry(ServiceItem si, Class<T> entryClass) {
        if (si == null) {
            return null;
        }

        final Entry[] attrs = si.attributeSets;
        for (final Entry entry : attrs) {
            if (entry.getClass() == entryClass) {
                return entryClass.cast(entry);
            }
        }

        return null;
    }

    public void setBadConnection() {
        badConnection = true;
    } // setBadConnection

    public void addProxyToGet(ServiceItem p) {
        synchronized (sync) {
            foundProxy = p;
        } // sync
        logger.log(Level.INFO, "Closing connection with proxy");

        if ((proxyService != null) && (proxyService.serviceID != null)) {
            proxyErr(proxyService);
        }

        if ((p != null) && (p.serviceID != null)) {
            if (proxyError.remove(p.serviceID) != null) {
                logger.log(Level.INFO, "Proxy with SID: " + p.serviceID + " IP: " + getProxyIP(p)
                        + " removed from black list");
            }
        }

        closeProxyConnection();
    }

    private void proxyErr(ServiceItem proxyServiceItem) {
        if (proxyServiceItem == null) {
            logger.log(Level.WARNING, "\n\n proxyErr called with null proxyServiceItem \n\n");
        }

        logger.log(Level.INFO,
                " Adding proxy with SID: " + proxyServiceItem.serviceID + " IP: " + getProxyIP(proxyServiceItem)
                        + " to the black list for: " + TimeUnit.NANOSECONDS.toSeconds(PROXY_ERROR_INTERVAL.get())
                        + " seconds");
        proxyError.put(proxyServiceItem.serviceID, Long.valueOf(Utils.nanoNow()));
    }

    @Override
    public void run() {
        logger.log(Level.INFO, ">> Start trying to make a connection to proxy");

        // select a proxy and try to connect to it until succeeds
        while (hasToRun) {
            try {

                cleanupProxyErrHash();

                // check if we should connect to a new proxy or change the current proxy or
                // if there is a bad connection where the proxy deletes messages ....

                final boolean bProxyConnVerified = verifyProxyConnection();

                if ((bProxyConnVerified == false) || (foundProxy != null) || badConnection) {
                    // note the time we started connecting to proxy
                    final long nStartCreatingProxyConnection = System.currentTimeMillis();

                    // set the proxy with the bad connection in the error prone proxies cache
                    if (badConnection && (proxyService != null)) {
                        // set the current proxy to be errorProne
                        if ((proxyService != null) && (proxyService.serviceID != null)) {
                            proxyErr(proxyService);
                        }
                        badConnection = false;
                    } // if

                    if (bProxyConnVerified == false) {
                        // if current connection with proxy is broken, make sure that no farm is alive
                        // this will also be called later, but it might block in the findBestProxy for a longer time...
                        if ((proxyService != null) && (proxyService.serviceID != null)) {
                            proxyErr(proxyService);
                        }
                        actualizeFarms(new ArrayList<ServiceItem>());
                    }

                    // find the best or the given (foundProxy) proxy
                    ServiceItem nextProxy = null;
                    synchronized (sync) {
                        if (foundProxy != null) {
                            nextProxy = foundProxy;
                            foundProxy = null;
                        }
                    }
                    if (nextProxy == null) {
                        nextProxy = findBestProxy(null);
                    }

                    // ramiro: ServiceID has equals() ... should be faster ...
                    // if(proxyService == null || !
                    // nextProxy.serviceID.toString().equals(proxyService.serviceID.toString()) ||
                    // verifyProxyConnection() == false){

                    // if found another proxy OR connection with current proxy is broken,
                    if ((proxyService == null) || !nextProxy.serviceID.equals(proxyService.serviceID)
                            || (bProxyConnVerified == false)) {
                        if (bProxyConnVerified == true) {// should be the case only if changed from the GUI

                            logger.log(Level.INFO, "Closing connection with proxy");
                            // connection not OK, or not wanted
                            // close current connection or make sure that it's closed

                            // ramiro: add this to bad proxy black list
                            if ((proxyService != null) && (proxyService.serviceID != null)) {
                                proxyErr(proxyService);
                            }
                            closeProxyConnection();

                        }
                        // remove all nodes from the SerMonitorBase & all
                        actualizeFarms(new ArrayList<ServiceItem>());
                        // try connecting to the new proxy
                        proxyService = nextProxy;
                        connectToProxy(proxyService);

                        logger.log(Level.INFO, ">> Connection to proxy realised in "
                                + (System.currentTimeMillis() - nStartCreatingProxyConnection) + " miliseconds");
                        // skip sleeping at the end to find the farms faster
                        continue;
                    }
                } else {
                    // long t1 = NTPDate.currentTimeMillis();
                    actualizeFarms(getActiveFarms());
                    // long t2 = NTPDate.currentTimeMillis();
                    // System.out.println("actualizeFarms in "+(t2-t1)+" millis");
                }
                // else{
                // nrToTry = 0;
                // activeFarms = null;
                // while ((activeFarms == null && nrToTry < 3)) {
                // logger.log(Level.INFO, "Getting active farms from proxy... ");
                // long t1 = NTPDate.currentTimeMillis();
                // activeFarms = getActiveFarms((ProxyServiceI) proxyService.service);
                // long t2 = NTPDate.currentTimeMillis();
                // Sizeof sz = new Sizeof();
                // if(activeFarms != null)
                // System.out.println("Got "+activeFarms.size()+" size="+sz.getSize(activeFarms)+" in "+(t2-t1)+" millis");
                // else
                // System.out.println("activeFarms = null");
                // nrToTry++;
                // }
                // if(activeFarms == null || verifyProxyConnection() == false){
                // // we should change the current proxy
                // if (proxyService != null) {
                // ServiceID pID = proxyService.serviceID;
                // if (pID != null) {
                // proxyError.put(pID, Long.valueOf(NTPDate.currentTimeMillis()));
                // logger.log(Level.WARNING, "Error calling remote proxy function for "+proxyService.serviceID);
                // } // if
                // }else{
                // logger.log(Level.WARNING, "ProxyService is NULL");
                // }
                // }else{
                // System.out.println("###### actualizing farms #####");
                // actualizeFarms(activeFarms);
                // }
                // }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Got exception main loop for JiniClient:", t);
            }

            try {
                Thread.sleep(3000);
            } catch (Throwable ex) {
                logger.log(Level.WARNING, "Error sleeping", ex);
            }

        } // while
    }// run

    /**
     * perform the update on sGroups ","-sepparated list of groups by adding or
     * removing "group" as "in" suggests". It returns the updated sGroups ","-sep. string.
     */
    private String updatePrefString(String sGroups, String group, boolean in) {

        String groupsToChange[] = group.split(",");
        if ((groupsToChange == null) || (groupsToChange.length == 0)) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINE, "Group " + group + " error on decomposition...");
            }
            return sGroups;
        }
        if (in) {
            for (String element : groupsToChange) {
                // add this group [i] to the list if not already there
                StringTokenizer stk = new StringTokenizer(sGroups, ",");
                boolean found = false;
                while (stk.hasMoreTokens()) {
                    String oldGroup = stk.nextToken();
                    if (oldGroup.equals(element)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    sGroups += ((sGroups.length() == 0) ? element : "," + element);
                }
            }
        } else {
            // remove this groups from the list
            StringTokenizer stk = new StringTokenizer(sGroups, ",");
            sGroups = "";
            while (stk.hasMoreTokens()) {
                String oldGroup = stk.nextToken();
                boolean found = false;
                for (String element : groupsToChange) {
                    if (oldGroup.equals(element)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    sGroups += ((sGroups.length() == 0) ? oldGroup : "," + oldGroup);
                }
            }
        }
        return sGroups;
    }

    /**
     * update selected groups in the user preference file
     * 
     * @param group
     *            - the group in question
     * @param selected
     *            - whether it should be added or deleted from the
     *            current list of active groups
     */
    public void updateUserGroupPreferences(String group, boolean selected) {
        if (mainClientClass == null) {
            return;
        }

        // update preference file
        try {
            Preferences prefs = Preferences.userNodeForPackage(mainClientClass);
            String selGroups = prefs.get("lia.Monitor.group", "ml");
            // String unselGroups = prefs.get("lia.Monitor.groupUnselected", "test");
            selGroups = updatePrefString(selGroups, group, selected);
            // unselGroups = updatePrefString(unselGroups, group, !selected);
            prefs.put("lia.Monitor.group", selGroups);
            // prefs.put("lia.Monitor.groupUnselected", unselGroups);
            prefs.sync();
            // System.out.println("new lia.Monitor.group="+selGroups);
            // Thread.currentThread().dumpStack();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "While updating user preferences:", ex);
        }
    }

    /**
     * return from the deployment file the list of selected and unselected groups for this
     * user. If there isn't such a thing, use the default value.
     * 
     * @param groupsType
     *            - one of "lia.Monitor.group" or "lia.Monitor.groupUnselected"
     * @param def
     *            - the default value
     * @return - the preffered groups, separated by ","
     */
    public String getUserGroupPreferences(String groupsType, String def) {
        String selGroups = def;
        if (mainClientClass == null) {
            // System.out.println("mainClientClass is null");
            return def;
        }
        try {
            Preferences prefs = Preferences.userNodeForPackage(mainClientClass);
            selGroups = prefs.get(groupsType, def);
            // System.out.println("SelGroups: "+selGroups);
            // perform an update, just in case it isn't already there.
            if (selGroups != null) {
                prefs.put(groupsType, selGroups);
                prefs.sync();
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "While fetching user preferences:", ex);
        }
        return selGroups;
    }

    /**
     * helper for populateSGroups - puts in SGroups only the groups from a ","-sepparaed
     * list, according to selected
     */
    private void putInSGroups(String sGroups, boolean selected) {
        if (sGroups == null) {
            return;
        }
        StringTokenizer tz = new StringTokenizer(sGroups, ",");
        while (tz.hasMoreTokens()) {
            String ss = tz.nextToken();
            // commented to allow these options to be overwritten
            // if(! SGroups.containsKey(ss)) {
            Integer oldSel = SGroups.get(ss);
            if ((oldSel == null) || (oldSel.intValue() != (selected ? 1 : 0))) {
                SGroups.put(ss, Integer.valueOf(selected ? 1 : 0));
                updateUserGroupPreferences(ss, selected);
            }
        }
    }

    /**
     * populate SGroups with selected and unselected groups.
     */
    public synchronized void populateSGroups() {
        // default properties from environment (jnlp)
        String defSelGroup = AppConfig.getProperty("lia.Monitor.group", null);
        // custom local user properties (.java/.userPrefs/.../prefs.xml)
        String selGroup = getUserGroupPreferences("lia.Monitor.group", defSelGroup);
        if (!AppConfig.getProperty("lia.Monitor.useOnlyDefinedGroups", "false").equals("true")) {
            // if user is allowed to have his own properties, then these overwrite the defaults
            if (selGroup != null) {
                putInSGroups(selGroup, true);
                // System.out.println("puttin' in selGroup: "+selGroup);
            }
        } else if (defSelGroup != null) {
            putInSGroups(defSelGroup, true);
            // System.out.println("puttin' in defGroup: "+defSelGroup);
        }
    }

    public void init() {
        // System.out.println("init in JiniClient");

        // get SecurityManager
        if (System.getSecurityManager() == null) {
            System.setSecurityManager(new RMISecurityManager());
        }

        // HACK For WebStart
        Policy.setPolicy(new Policy() {

            /**
             * @param codesource  
             */
            @Override
            public PermissionCollection getPermissions(CodeSource codesource) {
                Permissions perms = new Permissions();
                perms.add(new AllPermission());
                return (perms);
            }

            @Override
            public void refresh() {
                //not now
            }
        });
        try {
            // TODO: is this really neccessary here? or is it sufficient the call from init in MainBase?
            populateSGroups();

            // set user codebase, if it is available
            setUserCodeBase();

            SecureContextExecutor.getInstance().execute(new PrivilegedExceptionAction<Object>() {

                @Override
                public Object run() throws Exception {

                    // get specified LookupLocators[]
                    final LookupLocator[] lookupLocators = getLUDSs();

                    // prepare the JiniExporter configuration
                    final Configuration cfgLUSs = JiniConfigProvider.getUserDefinedConfig();

                    // prepare the Jini managers...
                    while (lookupDiscoveryManager == null) {
                        try {
                            lookupDiscoveryManager = new LookupDiscoveryManager(null, lookupLocators, null, cfgLUSs);
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Failed to create the LookupDiscoveryManager. Will retry.", e);
                        }
                    }

                    LeaseRenewalManager lrm = null;
                    while (lrm == null) {
                        try {
                            lrm = new LeaseRenewalManager(cfgLUSs);
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Failed to create the LeaseRenewalManager. Will retry. Cause:", e);
                        }
                    }

                    while (sdm == null) {
                        try {
                            sdm = new ServiceDiscoveryManager(lookupDiscoveryManager, lrm);
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Failed to create ServiceDiscoveryManager. Will retry. Cause: ",
                                    e);
                        }
                    }

                    MLJiniManagersProvider.setManagers(lookupDiscoveryManager, sdm, null);
                    if (startMLLusHelper) {
                        mlLusHelper = MLLUSHelper.getInstance();
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed initializing Jini Managers", e);
        }
        // start the thread .....
        start();
    }

    /** Add to the sytem codebase the user given codebase. (useful for clients that provide a dl) */
    private void setUserCodeBase() {
        String codebase = System.getProperty("java.rmi.server.codebase");
        if (codebase == null) {
            codebase = "";
        }
        String userCodeBase = AppConfig.getProperty("lia.Monitor.userCodeBase");
        if (userCodeBase != null) {
            codebase = userCodeBase.replace(',', ' ').trim() + " " + codebase;
        }
        System.setProperty("java.rmi.server.codebase", codebase.trim());
    }

    LookupLocator[] getLUDSs() {
        String luslist = AppConfig.getProperty("lia.Monitor.LUSs", default_LUDs);
        StringTokenizer tz = new StringTokenizer(luslist, ",");
        int count = tz.countTokens();
        LookupLocator[] locators = new LookupLocator[count];
        StringBuilder sb = new StringBuilder("\n\nUsing LUSs:");
        int i = 0;
        while (tz.hasMoreTokens()) {
            String host = tz.nextToken();
            try {
                locators[i] = new LookupLocator("jini://" + host);
                sb.append("\n    LUS " + i + ": " + locators[i].getHost() + ":" + locators[i].getPort());
                i++;
            } catch (java.net.MalformedURLException e) {
                logger.log(Level.WARNING, "URL format error! host=" + host, e);
            }
        }
        sb.append("\n");
        logger.log(Level.INFO, sb.toString());

        if (i == count) {
            return locators;
        }

        LookupLocator[] nlocators = new LookupLocator[i];
        for (int j = 0; j < i; j++) {
            nlocators[j] = locators[j];
        }

        return nlocators;
    }

    /**
     * finds the best proxy; get topology information if not available
     */
    public ServiceItem findBestProxy(ServiceItem someProxy) {
        if (someProxy != null) {
            return someProxy;
        }
        if ((asC == null) && (netC == null)) {
            getTopologyData();
        }
        logger.log(Level.INFO, " [ JiniClient ] Searching for proxy services...");
        ServiceItem[] proxies = null;
        while ((proxies == null) || (proxies.length == 0)) {
            mlLusHelper.forceUpdate();
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                //faster
            }
            proxies = mlLusHelper.getProxies();
        } // while
        return findProxyWithMinClients(proxies);
    }

    // // finds proxy
    // public synchronized ServiceItem findProxy(ServiceItem p) {
    // ProxyServiceEntry pse = new ProxyServiceEntry();
    // pse.proxyGroup = AppConfig.getProperty ("lia.Monitor.ClientsFarmProxy.ProxyGroup","farm_proxy");
    // ServiceTemplate proxyTemplate = new ServiceTemplate(
    // null,
    // new Class[] { lia.Monitor.ClientsFarmProxy.ProxyServiceI.class },
    // new Entry[] {pse}
    // );
    // ServiceItem[] si = null;
    // ServiceItem proxyS = p;
    //
    // while (true) {
    // logger.log(Level.INFO, "Trying to find a proxy ....");
    // if (proxyS == null) {
    // try {
    // Thread.sleep(1000);
    // si = sdm.lookup(proxyTemplate,100, null);
    // proxyS = findProxyWithMinClients(si);
    // } catch (Exception e) {
    // e.printStackTrace ();
    // }
    // } // if
    // if (proxyS != null) {
    // return proxyS;
    // // try {
    // // AddProxyService (proxyS) ;
    // // return proxyS;
    // // } catch (Exception e) {
    // // logger.log(Level.WARNING, "Error connectiong to the proxy", e);
    // // if(proxyS.serviceID != null)
    // // proxyError.put(proxyS.serviceID, Long.valueOf(NTPDate.currentTimeMillis()));
    // // proxyS = null;
    // // }
    // }
    // }
    // } //findProxy

    /** try connecting to the given proxy; if this fails, add it to the proxyError hash */
    public boolean connectToProxy(ServiceItem proxyS) {
        try {
            AddProxyService(proxyS);
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error connectiong to the proxy", e);
            if (proxyS.serviceID != null) {
                proxyErr(proxyS);
            }
        }
        return false;
    }

    /**
     * 
     */
    private void cleanupProxyErrHash() {

        try {

            if (logger.isLoggable(Level.FINE)) {
                int proxyErrSize = proxyError.size();
                String logMsg = "proxyError.size() = " + proxyErrSize;
                if (proxyErrSize > 0) {
                    logMsg += " Elements: " + proxyError.toString();
                }
                logger.log(Level.FINE, logMsg);
            }

            // remove from error hash old proxies
            for (Iterator<Map.Entry<ServiceID, Long>> it = proxyError.entrySet().iterator(); it.hasNext();) {
                ServiceID key = null;
                try {
                    final Map.Entry<ServiceID, Long> entry = it.next();
                    key = entry.getKey();
                    final Long time = entry.getValue();
                    if ((Utils.nanoNow() - time.longValue()) > PROXY_ERROR_INTERVAL.get()) {
                        logger.log(Level.INFO, " [ JiniClient ] Removing proxy with SID: " + key + " from black list");
                        it.remove();
                    }// if
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " [ JiniClient ] Got exception removing proxy with SID: " + key, t);
                }
            } // for

        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ JiniClient ] Got exception in cleanupProxyErrHash. proxyError.size() = "
                    + proxyError.size(), t);
        }
    }

    private ServiceItem findProxyWithMinClients(ServiceItem[] services) {

        final String[] prefProxies = AppConfig.getVectorProperty("lia.Monitor.forceProxies");

        // try to reverse lookup
        final Set<String> pProxiesIPList = new HashSet<String>();
        if (prefProxies != null) {
            for (final String proxyHostName : prefProxies) {
                try {
                    final InetAddress[] allIPs = InetAddress.getAllByName(proxyHostName);
                    for (final InetAddress ip : allIPs) {
                        pProxiesIPList.add(ip.getHostAddress());
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " [ JiniClient ] Unable to got the IPs for proxy entry: " + proxyHostName);
                }
            }
        }

        cleanupProxyErrHash();

        if ((services == null) || (services.length == 0)) {
            return null;
        } // if

        ServiceItem minProxy = null;
        double value = Double.MAX_VALUE;

        for (ServiceItem min : services) {
            ServiceID pID = min.serviceID;
            if ((pID == null) || proxyError.containsKey(pID)) { // ignore proxies prone to errors.
                logger.log(Level.INFO, " Ignoring proxy with SID: " + pID + " IP: " + getProxyIP(min)
                        + " ... still in the black list");
                continue;
            } // if

            double points = 0;
            Entry[] attrs = min.attributeSets;
            GenericMLEntry generic = null;
            for (Entry attr : attrs) {
                if (attr instanceof GenericMLEntry) {
                    generic = (GenericMLEntry) attr;
                    break;
                } // if
            } // for

            if (generic != null) {
                String netP = (String) generic.hash.get("netName");
                String asP = (String) generic.hash.get("AS");
                String countryP = (String) generic.hash.get("countryCode");
                String continentP = (String) generic.hash.get("continentCode");

                boolean bClose = false;

                if ((netP != null) && (netC != null) && netP.equals(netC)) {
                    points = points + 0; // 20 points for the same network
                    bClose = true;
                } else {
                    if ((asP != null) && (asC != null) && asP.equals(asC)) {
                        points = points + 10; // 40 points for the same as
                        bClose = true;
                    } else {
                        if ((countryP != null) && (countryC != null) && countryP.equals(countryC)) {
                            points = points + 15; // 50 points for the same country
                            bClose = true;
                        } else {
                            if ((continentP != null) && (continentC != null) && continentP.equals(continentC)) {
                                points = points + 40;
                            } else {
                                points = points + 50;
                            }
                        } // if - else
                    } // if - else
                } // if - else

                Integer nrClients = (Integer) generic.hash.get("nrClients");
                if (nrClients != null) {
                    points = points + nrClients.intValue();
                } // if

                Double load = (Double) generic.hash.get("Load5");

                if ((load != null) && !load.isNaN()) {

                    if (bClose) {
                        points = points + (5 * load.doubleValue());
                    } else {
                        points = points + (10 * load.doubleValue());
                    } // if - else
                } // if

                if (attrs != null) {
                    if (attrs.length > 0) {
                        final int portNumber = ((ProxyServiceEntry) attrs[0]).proxyPort.intValue();
                        final String ipAddress = ((ProxyServiceEntry) attrs[0]).ipAddress;

                        // because it is a prefered proxy, count on it :)
                        if (pProxiesIPList.contains(ipAddress)) {
                            logger.log(Level.INFO, "this is a preferred proxy ! :). Point before: " + points);
                            points = 0;
                        } // if

                        // try {
                        /**
                         * To get the hostname for an proxy ip, don't use inetaddress interogation system
                         * dirrectly, instead, look first for the name entry in proxyserviceentry, and
                         * then, if not found, put the IpAddrCache to look for it in another thread
                         */
                        String hostAddress = ((ProxyServiceEntry) attrs[0]).proxyName;
                        if (hostAddress == null) {
                            hostAddress = IpAddrCache.getHostName(ipAddress, true);
                        } else {
                            IpAddrCache.putIPandHostInCache(ipAddress, hostAddress);
                        }
                        if (hostAddress == null) {
                            hostAddress = ipAddress;
                        }
                        logger.log(Level.INFO, "PROXY " + hostAddress + ":" + portNumber + "\n" + "net=" + netP
                                + " as=" + asP + " country=" + countryP + " continent=" + continentP + " clients="
                                + nrClients + " Load5=" + load + " points=" + points);
                        // } catch (UnknownHostException e) {
                        // e.printStackTrace();
                        // }
                    }
                }
                if (points < value) {
                    minProxy = min;
                    value = points;
                } // if
            } // if
        } // for
        if (minProxy == null) {
            minProxy = services[0];
        }
        return minProxy;
    } // findProxyWithMinClients

    public void setAllFarms(Vector<ServiceItem> farmServices) {
        synchronized (allFarms) {
            allFarms.clear();
            allFarms.addAll(farmServices);
        }
    }

    public ArrayList<ServiceItem> getActiveFarms() {
        String[] groups = getGroups();
        ArrayList<ServiceItem> rez = new ArrayList<ServiceItem>();
        synchronized (allFarms) {
            for (final ServiceItem si : allFarms) {
                MonaLisaEntry mle = getEntry(si, MonaLisaEntry.class);
                if (mle != null) {
                    StringTokenizer stk = new StringTokenizer(mle.Group, ",");
                    nextFarm: while (stk.hasMoreTokens()) {
                        String group = stk.nextToken();
                        for (int i = 0; i < groups.length; i++) {
                            if (groups[i].equals(group)) {
                                rez.add(si);
                                // System.out.println("received farm:"+mle.Name);
                                break nextFarm;
                            }
                        }
                    }
                }
            }
            // System.out.println("---------------"+new Date());
        }
        return rez;
    }

    /**
     * returns all groups that received service ids are in
     * uses allFarms vector
     * 
     * @return a String array containing all the groups found in current allFarms vector
     */
    protected String[] getFarmsGroups() {
        HashSet<String> groupsSet = new HashSet<String>();
        synchronized (allFarms) {
            for (final ServiceItem si : allFarms) {
                MonaLisaEntry mle = getEntry(si, MonaLisaEntry.class);
                if (mle != null) {
                    StringTokenizer stk = new StringTokenizer(mle.Group, ",");
                    while (stk.hasMoreTokens()) {
                        String group = stk.nextToken();
                        groupsSet.add(group);
                    }
                }
            }
        }
        int n = groupsSet.size();
        if (n == 0) {
            return null;
        }
        return groupsSet.toArray(new String[n]);
    }

    private Vector<String> getVGroups() {

        Vector<String> selectedGroups = new Vector<String>();

        for (Map.Entry<String, Integer> entry : SGroups.entrySet()) {
            final String group = entry.getKey();
            final Integer select = entry.getValue();
            if (select.intValue() == 1) {
                selectedGroups.add(group);
            }
        } // for

        return selectedGroups;
    }

    private String[] getGroups() {

        Vector<String> selectedGroups = getVGroups();
        if (selectedGroups == null) {
            return null;
        }
        String[] selectedG = selectedGroups.toArray(new String[selectedGroups.size()]);
        return selectedG;
    }

    public MonMessageClientsProxy createActiveGroupsMessage() {
        Vector<String> v = getVGroups();
        // if ( v==null )
        // System.out.println("<mluc> active groups vector is null");
        // else {
        // System.out.println("<mluc> active groups count: "+v.size());
        // String sGList="";
        // for( int i=0; i<v.size(); i++)
        // sGList+=(i>0?",":"")+v.get(i);
        // System.out.println("<mluc> active groups : "+sGList);
        // }
        final boolean bCompress = AppConfig.getb("lia.Monitor.Client.compress", true);

        //TODO stil bug in the client
        //hack to notify all configs as they are without diffs - 
        logger.log(Level.INFO, "--> Compressed configs - " + bCompress);
        return new MonMessageClientsProxy("proxy", (bCompress) ? "c" : "u", v, null);
    }

    public void addLUS(String host, int port) throws Exception {
        LookupLocator[] ll = new LookupLocator[1];
        logger.log(Level.INFO, " Adding LUS  Host = " + host);
        if (port > 0) {
            ll[0] = new LookupLocator(host, port);
        } else {
            ll[0] = new LookupLocator("jini://" + host);
        }
        lookupDiscoveryManager.addLocators(ll);
    }

    /**
     * populates the following attributes:
     * latC, lonC, asC, netC, continentC, countryC
     * by reading data from the given topology service
     */
    private boolean queryTopoService(String topoSerURL) {
        logger.log(Level.INFO, "Topology service found at [" + topoSerURL + "]. Querying it...");
        try {
            String ipAddress = InetAddress.getLocalHost().getHostAddress();
            String as = null;
            String network = null;
            String countryCode = null;
            String lon = null;
            String lat = null;
            String continentCode = null;

            // get as and network
            URL url = new URL(topoSerURL + "/FindIP?" + ipAddress);
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            huc.setRequestMethod("GET");
            huc.setUseCaches(false);
            huc.connect();
            InputStream is = huc.getInputStream();
            int code = huc.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                while (reader.ready()) {
                    String line = reader.readLine();
                    if (line.lastIndexOf("origin: ") >= 0) {
                        line = line.substring(line.indexOf("origin: "));
                        StringTokenizer st = new StringTokenizer(line, " \t\n");
                        st.nextToken();
                        as = st.nextToken();
                        as = as.substring(2); // skip AS
                        // logger.log (Level.INFO, "Topology thread found as: "+as);
                    }
                    if (line.lastIndexOf("netname: ") >= 0) {
                        line = line.substring(line.indexOf("netname: "));
                        StringTokenizer st = new StringTokenizer(line, " \t\n");
                        // line = line.substring(line.indexOf("netname: "));
                        st.nextToken();
                        network = st.nextToken();
                        // logger.log (Level.INFO, "Topology thread found network ====> "+network);
                    } // if

                } // while
            } else {
                logger.log(Level.WARNING, "Error " + code + " reading AS and NET from " + url.getHost());
                return false;
            } // if - else
            huc.disconnect();

            url = new URL(topoSerURL + "/FindAS?" + as);
            huc = (HttpURLConnection) url.openConnection();
            huc.setRequestMethod("GET");
            huc.setUseCaches(false);
            huc.connect();
            is = huc.getInputStream();
            code = huc.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                String line = reader.readLine();
                if (line == null) {
                    logger.log(Level.WARNING,
                            "Received empty response while reading topology data from " + url.getHost());
                    return false;
                }
                line = line.trim();
                StringTokenizer st = new StringTokenizer(line, "\t\n");

                int i = 0;
                while (st.hasMoreTokens()) {
                    i++;
                    if (i == 3) {
                        lon = st.nextToken();
                    } // if i 3

                    if (i == 2) {
                        lat = st.nextToken();
                    } // if i 2

                    if (i == 9) {
                        continentCode = st.nextToken();
                    } // if

                    if (i == 8) {
                        countryCode = st.nextToken();
                    }

                    if ((i != 2) && (i != 3) && (i != 8) && (i != 9)) {
                        st.nextToken();
                    }

                } // while
            } else {
                logger.log(Level.WARNING, "Error " + code + " reading topology data from " + url.getHost());
                return false;
            }
            huc.disconnect();

            logger.log(Level.INFO, "Client topology data : AS=" + as + " NET=" + network + " country=" + countryCode
                    + " continent=" + continentCode + " LONG=" + lon + " LAT=" + lat);

            // export attributes
            asC = as;
            netC = network;
            countryC = countryCode;
            continentC = continentCode;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error while accessing topology service", e);
            return false;
        } // try - catch
        return true;
    }

    /**
     * get a list of topology services and then query them
     */
    public void getTopologyData() {
        ServiceItem[] topSer = null;
        // String baseURL = null;
        long timeout = 20; // timeout this in 20 seconds...
        final long startTime = System.nanoTime();
        long currentTime;

        logger.log(Level.INFO, "Searching a topology service...");
        while ((topSer == null) || (topSer.length == 0)) {
            currentTime = System.nanoTime();
            if (TimeUnit.NANOSECONDS.toSeconds(currentTime - startTime) > timeout) {
                logger.log(Level.WARNING, "Timeout while searching topology service");
                return;
            }
            try {
                mlLusHelper.forceUpdate();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ex) {
                    //faster
                }
                topSer = mlLusHelper.getTopologyServices();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "While updating topology services list, got:", ex);
            }
        } // while

        for (ServiceItem element : topSer) {
            GenericMLEntry gmle = getEntry(element, GenericMLEntry.class);
            if (gmle == null) {
                logger.log(Level.WARNING, "The GMLEntry for the topology service is null");
                return;
            }
            topoSerURL = (String) gmle.hash.get("URL");
            if (topoSerURL == null) {
                logger.log(Level.WARNING, "The URL of the topology service is null");
                return;
            }
            if (queryTopoService(topoSerURL)) {
                break;
            }
        }
    }

    abstract public boolean AddMonitorUnit(ServiceItem s);

    abstract public void removeNode(ServiceID id);

    abstract public void AddProxyService(ServiceItem s) throws Exception;

    abstract public boolean verifyProxyConnection();

    abstract public boolean knownConfiguration(ServiceID farmID);

    abstract public void closeProxyConnection();

    abstract public void waitServiceThreads(String message);

    abstract public void portMapChanged(ServiceID id, ArrayList<?> portMap);

    public ServiceItem getNextProxy() {
        return foundProxy;
    }

    /**
     * method which can hardly wait to be overloaded by SerMonitorBase
     * implementation.
     */
    public void updateGroups() {
        //not now
    }

    /**
     * @return the nConnect
     */
    public int getFailedConns() {
        return nFailedConns;
    }

    public void incFailedConns() {
        nFailedConns++;
    }

    public int getInvalidMsgCount() {
        return nInvalidMsgCount;
    }

    public void incInvalidMsgCount() {
        nInvalidMsgCount++;
    }

    /**
     * sets a new status to the connectivity with proxy
     * 
     * @author mluc
     * @since Jun 19, 2006
     * @param newStatus
     *            double value between 0 and 100
     */
    public void setProxyMsgBufStatus(double newStatus) {
        dProxyMsgBufStatus = newStatus;
    }

    /**
     * @author mluc
     * @since Jun 19, 2006
     * @return
     */
    public String getProxyMsgBufStatus() {
        String sRet = "";
        if (dProxyMsgBufStatus < 50) {
            sRet = "<font color=green><b>fine</b></font> (lower than <b>50%</b>)";
        } else if (dProxyMsgBufStatus < 75) {
            sRet = "<font color='#E4E100'><b>caution</b></font> (over <b>50%</b>)";
        } else if (dProxyMsgBufStatus < 90) {
            sRet = "<font color='#E68C0B'><b>warning</b></font> (over <b>75%</b>)";
        } else if (dProxyMsgBufStatus < 100) {
            sRet = "<font color='#E45D33'><b>WARNING</b></font> (over <b>90%</b>)";
        } else {
            sRet = "<font color='#D94F4F'><b>ERROR</b></font> (at <b>100%</b>, closing connection)";
        }
        return sRet;
    }

    public void setTestProxyBuf(boolean bNewMode) {
        bTestProxyMsgBuff = bNewMode;
    }

    /**
     * start the test on how well the proxy messages buffer mechanism works:
     * each message received introduces an additional delay of 200 ms
     * 
     * @author mluc
     * @since Jun 19, 2006
     * @return
     */
    public boolean testProxyBuff() {
        return bTestProxyMsgBuff;
    }

    /**
     * Reloads the config when "ml.properties" is (re)loaded
     * 
     * @author ramiro
     * @since Jun 21, 2007
     */
    private void reloadConfig() {
        try {
            PROXY_ERROR_INTERVAL.set(TimeUnit.SECONDS.toNanos(AppConfig.getl(
                    "lia.Monitor.JiniClient.CommonJini.PROXY_ERROR_INTERVAL", 300L)));
        } catch (Throwable t) {
            logger.log(Level.WARNING,
                    " [ JiniClient ] Unable to reload PROXY_ERROR_INTERVAL. Will set default value. Cause: ", t);
            PROXY_ERROR_INTERVAL.set(TimeUnit.SECONDS.toNanos(300));
        }

        logger.log(
                Level.WARNING,
                " [ JiniClient ] PROXY_ERROR_INTERVAL se to: "
                        + TimeUnit.NANOSECONDS.toSeconds(PROXY_ERROR_INTERVAL.get()) + " seconds.");
    }

    /**
     * Reloads the config if "ml.propertie" is reloaded
     * 
     * @author ramiro
     * @since Jun 21, 2007
     */
    @Override
    public void notifyAppConfigChanged() {
        try {
            reloadConfig();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ JiniClient ] Got exception reloading config", t);
        }
    }
} // class JiniClient
