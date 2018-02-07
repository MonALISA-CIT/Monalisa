/*
 * $Id: FarmCommunication.java 7419 2013-10-16 12:56:15Z ramiro $
 */

package lia.Monitor.ClientsFarmProxy;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ClientsFarmProxy.AgentsPlatform.AgentMessage;
import lia.Monitor.ClientsFarmProxy.AgentsPlatform.AgentsPlatform;
import lia.Monitor.ClientsFarmProxy.MLLogger.RemoteMLPropConfigurator;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.GenericMLEntry;
import lia.Monitor.monitor.MonMessageClientsProxy;
import lia.Monitor.monitor.MonaLisaEntry;
import lia.Monitor.monitor.SiteInfoEntry;
import lia.Monitor.monitor.monMessage;
import lia.util.ServiceIDComparator;
import lia.util.Utils;
import lia.util.ntp.NTPDate;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;

/**
 * TODO mda ... cred ca o sa sara in aer clasa asta cu totu' in curand ( ramiro )
 * 
 * @author mickyt
 */
public final class FarmCommunication extends Thread implements AppConfigChangeListener {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(FarmCommunication.class.getName());

    // farm services found - key - FarmWorker
    private static final ConcurrentSkipListMap<ServiceID, FarmWorker> mlServicesMap = new ConcurrentSkipListMap<ServiceID, FarmWorker>(
            ServiceIDComparator.getInstance());

    // agents related
    private static final AgentsPlatform agentsPlatform = new AgentsPlatform(mlServicesMap);

    private static final Lock farmCommunicationHashesLock = new ReentrantLock();

    // farm ID

    private final ServiceI service; // the ClientsFarmProxy service

    private static final AtomicBoolean active = new AtomicBoolean(true);

    /** Quick fix if a service gets more then one SID during it's lifetime */
    private final static long ML_SERVICE_LUS_SID_TIMEOUT = 5 * 60 * 1000;

    private static final AtomicLong nrAgentsMsg = new AtomicLong(0);

    private static final TreeMap<ServiceID, Long> jiniHackSIDinLUSs = new TreeMap<ServiceID, Long>(
            ServiceIDComparator.getInstance());

    private static final AtomicBoolean shouldKeepInSyncWithTheLUS = new AtomicBoolean(false);

    static {

        FarmCommunication tmpInstance = null;
        try {
            tmpInstance = new FarmCommunication();
            tmpInstance.start();
            AppConfig.addNotifier(tmpInstance);
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ FarmCommunication ] got exception in intialization ", t);
            System.exit(-1);
        }

        try {
            shouldKeepInSyncWithTheLUS.set(AppConfig.getb(
                    "lia.Monitor.ClientsFarmProxy.FarmCommunication.shouldKeepInSyncWithTheLUS", false));
        } catch (Throwable t1) {
            logger.log(Level.WARNING,
                    " Unable to get lia.Monitor.ClientsFarmProxy.FarmCommunication.shouldKeepInSyncWithTheLUS ", t1);
            shouldKeepInSyncWithTheLUS.set(false);
        }

    }

    private static final InetAddress getInetAddr(final String hostname) {

        InetAddress retv = null;
        if (retv == null) {
            try {
                retv = InetAddress.getByName(hostname);
            } catch (Throwable t) {
            }
        }

        return retv;
    }

    public static final HashMap<String, TreeSet<String>> getFarmsNamesGroups() {

        HashMap<String, TreeSet<String>> h = new HashMap<String, TreeSet<String>>();
        TreeSet<String> v = new TreeSet<String>();
        TreeSet<String> n = new TreeSet<String>();

        for (final FarmWorker fw : mlServicesMap.values()) {
            final ServiceItem si = fw.getServiceItem();
            final Entry[] entries = si.attributeSets;
            for (final Entry entry : entries) {
                if ((entry != null) && (entry instanceof MonaLisaEntry)) {
                    final MonaLisaEntry mle = (MonaLisaEntry) entry;
                    // getGroups
                    if (mle.Group != null) {
                        final String[] grps = Utils.getSplittedListFields(mle.Group);
                        for (final String group : grps) {
                            if (!v.contains(group)) {
                                v.add(group);
                            } // if
                        } // for
                    } // if

                    if (mle.Name != null) {
                        n.add(mle.Name);
                    } // if

                    break;
                } // if
            } // for
        } // for

        h.put("groups", v);
        h.put("names", n);

        return h;

    } // getFarmsGroups

    private FarmCommunication() throws Exception {
        this.service = Service.getServiceI();
        // Just start it !
        RemoteMLPropConfigurator.getInstance();
    } // constructor

    /**
     * @return
     */
    public static final Map<ServiceID, FarmWorker> getFarmsHash() {
        return mlServicesMap;
    } // getFarmsHash

    /**
     * @return
     */
    public static final int getFarmsNr() {
        return mlServicesMap.size();
    }

    public static final Lock getLock() {
        return farmCommunicationHashesLock;
    }

    @Override
    public void run() {

        try {
            setName("(ML) FarmCommunication Thread");
            while (active.get()) {
                try {
                    Thread.sleep(20 * 1000);
                } catch (InterruptedException ie) {
                    logger.log(Level.WARNING, " [ FarmCommunication ] got interrupted exception while sleeping", ie);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " [ FarmCommunication ] got general exception while sleeping", t);
                }

                try {
                    final ServiceItem[] serviceItems = service.getFarmServices();
                    // mlServicesMap found at a specified moment of time
                    if (serviceItems != null) {
                        // add new found mlServicesMap

                        boolean mustSendUpdates = false;
                        final Set<ServiceID> proccessedSIDs = new TreeSet<ServiceID>(ServiceIDComparator.getInstance());
                        for (final ServiceItem cServiceItem : serviceItems) {
                            final ServiceID cServiceID = cServiceItem.serviceID;

                            if (proccessedSIDs.contains(cServiceID)) {
                                continue;
                            }

                            proccessedSIDs.add(cServiceID);
                            jiniHackSIDinLUSs.put(cServiceID, NTPDate.currentTimeMillis() + ML_SERVICE_LUS_SID_TIMEOUT);

                            final FarmWorker cFW = mlServicesMap.get(cServiceID);

                            // for entry refresh
                            if (cFW != null) {
                                final ServiceItem origSI = cFW.getServiceItem();

                                // do not update SiteInfoEntry ... it contains overwritten IP-s
                                SiteInfoEntry orig_sie = (SiteInfoEntry) getEntry(origSI, SiteInfoEntry.class);
                                SiteInfoEntry add_sie = (SiteInfoEntry) getEntry(cServiceItem, SiteInfoEntry.class);
                                if ((orig_sie != null) && (add_sie != null) && !orig_sie.equals(add_sie)) {
                                    if ((orig_sie.IPAddress != null) && (orig_sie.IPAddress.length() > 0)) {
                                        add_sie.IPAddress = orig_sie.IPAddress;
                                    }
                                } // if

                                if (origSI != null) {
                                    mustSendUpdates = !Arrays.equals(origSI.attributeSets, cServiceItem.attributeSets);
                                } else {
                                    mustSendUpdates = true;
                                }

                                if (mustSendUpdates) {
                                    cFW.getAndSetServiceItem(cServiceItem);
                                }
                            }// if
                        } // for

                        if (mustSendUpdates) { // NEW ENTRIES. SEND UPDATE TO CLIENTS
                            // SEND BCAST MESSAGE WITH ALL FARMS TO ALL CLIENTS
                            MonMessageClientsProxy mm = new MonMessageClientsProxy(monMessage.PROXY_MLSERVICES_TAG,
                                    null, getFarms(), null);
                            // ClientPriorMsg cpm = new ClientPriorMsg (5,monMessage.PROXY_MLSERVICES_TAG, mm);
                            ClientsCommunication.sendBcastMsg(mm);
                        } // IF

                    }

                } catch (Throwable t) {
                    logger.log(Level.WARNING, " [ FarmCommunication ] inner loop got exception ", t);
                }
            } // while
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ FarmCommunication ] main loop got exception ", t);
        }
    } // run

    public static final FarmWorker addFarmWorker(ServiceID sid, ServiceItem si, ProxyTCPWorker ptw) {

        FarmWorker retv = null;
        retv = new FarmWorker(sid, si, ptw);
        return retv;
    }

    private static final Object getEntry(final ServiceItem si, Class<?> entryClass) {

        if (si == null) {
            return null;
        }
        final Entry[] entries = si.attributeSets;
        if ((entries == null) || (entries.length == 0)) {
            return null;
        }

        for (final Entry entry : entries) {
            if (entry.getClass() == entryClass) {
                return entry;
            }
        } // for

        return null;
    } // getEntry

    public static final void deleteFarm(final ServiceID id) {

        FarmWorker fw = null;

        farmCommunicationHashesLock.lock();
        try {
            fw = mlServicesMap.remove(id);
            agentsPlatform.deleteFarm(id);
        } catch (Throwable t) {
            logger.log(Level.WARNING,
                    "\n\n\n [ EXCEPTION !!! ] [ FarmCommunication ] Got exception removing service [ " + id
                            + " ] from the proxy HASHES !!", t);
        } finally {
            farmCommunicationHashesLock.unlock();
        }

        // send notification to clients
        try {
            MonMessageClientsProxy mm = new MonMessageClientsProxy(monMessage.PROXY_MLSERVICES_TAG, null, getFarms(),
                    null);
            ClientsCommunication.sendBcastMsg(mm);
        } catch (Throwable th) {
            logger.log(Level.INFO, "Send mlServicesMap to clients .... got exception", th);
        }

        if (fw != null) {
            fw.notifyConnectionClosed();
        }
    } // deleteFarm

    //    public static final Vector<String> getGroup(ServiceID farmID) {
    //
    //        final ServiceItem si = mlServicesMap.get(farmID).siRef.get();
    //        final Vector<String> v = new Vector<String>();
    //
    //        if (si == null)
    //            return null;
    //
    //        Entry[] attr = si.attributeSets;
    //        if (attr == null)
    //            return null;
    //        for (int i = 0; i < attr.length; i++) {
    //            if (attr[i] instanceof MonaLisaEntry) {
    //                String groups = ((MonaLisaEntry) attr[i]).Group;
    //                if (groups != null) {
    //                    StringTokenizer st = new StringTokenizer(groups, ",");
    //                    while (st.hasMoreTokens()) {
    //                        v.add(st.nextToken());
    //                    }
    //                } // if
    //            }// if
    //        } // for
    //
    //        return v;
    //
    //    } // getGroup

    public static final long getNrAgentsMsg() {
        return nrAgentsMsg.getAndSet(0L);
    } // getNrAgentsMsg

    public static final void fwdAgentsMsg(AgentMessage agentMsg, boolean control, String ctrl, ServiceID fromFarmID) {
        if (agentMsg == null) {
            return;
        } // if

        nrAgentsMsg.getAndIncrement();

        try {
            if (control == true) {
                agentsPlatform.receivedCtrlMessage(agentMsg, ctrl);
            } else {
                agentsPlatform.receivedMessage(agentMsg);
            } // if - else
        } catch (Throwable t) { // an exception occured while trying to process the message .... send an error message
                                // back to the sending source :(
            monMessage mm = new monMessage(monMessage.ML_AGENT_ERR_TAG, null, agentMsg);
            addMessageToSend(fromFarmID, mm);
            logger.log(Level.WARNING, "Got exception while forwarding an AggentMessage", t);
        } // try - catch
    } // fwdAgentsMsg

    public static final void notifyConnectionClosed(ServiceID farmID) {

        FarmWorker fw = null;
        farmCommunicationHashesLock.lock();
        try {
            fw = mlServicesMap.remove(farmID);
            deleteFarm(farmID);
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception in notifyConnectionClosed(ServiceID)", t);
        } finally {
            farmCommunicationHashesLock.unlock();
        }

        if (fw != null) {
            try {
                fw.notifyConnectionClosed();
            } catch (Throwable t1) {
                logger.log(Level.WARNING, " [ FarmComunication ] [ notifyConnectionClosed ] " + farmID
                        + " got exception: ", t1);
            }
        }

    } // notifyConnectionClosed

    public static final void addMessageToSend(ServiceID farmID, MonMessageClientsProxy mmcp) {
        final FarmWorker fw = mlServicesMap.get(farmID);
        if ((fw != null) && (mmcp != null)) {
            fw.sendMsg(mmcp);
        } // if
    }

    public static final void addMessageToSend(ServiceID farmID, monMessage message) {
        final FarmWorker fw = mlServicesMap.get(farmID);
        if ((fw != null) && (message != null)) {
            fw.sendMsg(message);
        } // if
    } // addMessageToSend

    public static final Set<ServiceID> getFarmsIDs() {
        return mlServicesMap.keySet();
    }

    public static final Vector<ServiceItem> getFarms() {

        Vector<ServiceItem> v = new Vector<ServiceItem>();

        try {
            for (final FarmWorker fw : mlServicesMap.values()) {
                final ServiceItem farmItem = fw.getServiceItem();
                final SiteInfoEntry sie = (SiteInfoEntry) getEntry(farmItem, SiteInfoEntry.class);
                GenericMLEntry gmle = (GenericMLEntry) getEntry(farmItem, GenericMLEntry.class);

                if ((gmle == null) || (gmle.hash == null) || !gmle.hash.containsKey("hostName")
                        || !gmle.hash.containsKey("ipAddress")) {
                    boolean addEntry = false;

                    if (gmle == null) {
                        gmle = new GenericMLEntry();
                        addEntry = true;
                    } // if

                    InetAddress IPAddress = ((sie != null) && (sie.IPAddress != null)) ? getInetAddr(sie.IPAddress)
                            : null;
                    if (IPAddress != null) {
                        gmle.hash.put("hostName", IPAddress.getHostName());
                        gmle.hash.put("ipAddress", IPAddress.getHostAddress());
                    } // if

                    Entry[] farmAttributes = farmItem.attributeSets;

                    if (addEntry) {
                        Entry[] newFarmAttributes = new Entry[farmAttributes.length + 1];
                        System.arraycopy(farmAttributes, 0, newFarmAttributes, 0, farmAttributes.length);

                        newFarmAttributes[farmAttributes.length] = gmle;

                        farmItem.attributeSets = newFarmAttributes;
                    }// if
                } // if
                v.add(farmItem);
            } // for
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exc getFarms()", t);
        } finally {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ FarmCommunication ] [ getFarms ] returning " + v.size() + " services!");
            }
        }

        return v;
    } // getFarms

    private static final String[] getGroups(String group) {
        return Utils.getSplittedListFields(group);
    }

    public static final Set<String> getFarmGroups(ServiceID sID) {
        return mlServicesMap.get(sID).getGroups();
    } // getFarmGroups

    public static final Vector<ServiceItem> getFarmsByGroup(String[] groups) {

        Vector<ServiceItem> v = new Vector<ServiceItem>();
        Vector<String> groupNames = new Vector<String>();

        // quick hack for adding mlServicesMap in more than one group
        for (String group2 : groups) {
            String[] gs = getGroups(group2);
            if ((gs != null) && (gs.length > 0)) {
                for (String element : gs) {
                    if ((element != null) && (element.length() > 0)) {
                        groupNames.add(element);
                    }
                }
            }
        } // for

        for (final FarmWorker fw : mlServicesMap.values()) {

            final ServiceItem farmService = fw.getServiceItem();
            final Entry[] farmAttributes = farmService.attributeSets;
            if (farmAttributes != null) {
                MonaLisaEntry mle = (MonaLisaEntry) getEntry(farmService, MonaLisaEntry.class);
                SiteInfoEntry sie = (SiteInfoEntry) getEntry(farmService, SiteInfoEntry.class);
                GenericMLEntry gmle = (GenericMLEntry) getEntry(farmService, GenericMLEntry.class);

                if (mle != null) {
                    String[] mleGroups = getGroups(mle.Group);
                    if ((mleGroups != null) && (mleGroups.length > 0)) {
                        for (String mleGroup : mleGroups) {
                            if (groupNames.contains(mleGroup)) {

                                boolean addEntry = false;

                                if (gmle == null) {
                                    gmle = new GenericMLEntry();
                                    addEntry = true;
                                } // if

                                InetAddress IPAddress = ((sie != null) && (sie.IPAddress != null)) ? getInetAddr(sie.IPAddress)
                                        : null;
                                if (IPAddress != null) {
                                    gmle.hash.put("hostName", IPAddress.getHostName());
                                    gmle.hash.put("ipAddress", IPAddress.getHostAddress());
                                } // if

                                if (addEntry) {
                                    Entry[] newFarmAttributes = new Entry[farmAttributes.length + 1];
                                    System.arraycopy(farmAttributes, 0, newFarmAttributes, 0, farmAttributes.length);

                                    newFarmAttributes[farmAttributes.length] = gmle;

                                    farmService.attributeSets = newFarmAttributes;
                                }// if
                                v.add(farmService);
                            }// if(groupNames.contains(mleGroups[ii]))
                        }// for()
                    }// if (mleGroups != null && mleGroups.length > 0 )
                }// if(mle != null)
            } // if
        } // for

        return v;
    } // getFarmsByGroup

    @Override
    public void notifyAppConfigChanged() {
        try {
            shouldKeepInSyncWithTheLUS.set(AppConfig.getb(
                    "lia.Monitor.ClientsFarmProxy.FarmCommunication.shouldKeepInSyncWithTheLUS", false));
        } catch (Throwable t1) {
            logger.log(Level.WARNING,
                    " Unable to get lia.Monitor.ClientsFarmProxy.FarmCommunication.shouldKeepInSyncWithTheLUS ", t1);
            shouldKeepInSyncWithTheLUS.set(false);
        }
    }
} // class
