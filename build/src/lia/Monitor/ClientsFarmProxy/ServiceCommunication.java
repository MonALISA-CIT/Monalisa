/*
 * $Id: ServiceCommunication.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.ClientsFarmProxy;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MonMessageClientsProxy;
import lia.Monitor.monitor.MonaLisaEntry;
import lia.Monitor.monitor.monMessage;
import lia.Monitor.monitor.monPredicate;
import lia.util.StringFactory;
import lia.util.Utils;
import lia.util.mail.MailFactory;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;

/**
 * TODO mda ... cred ca o sa sara in aer clasa asta cu totu' in curand ( ramiro )
 * 
 * @author mickyt
 */
public class ServiceCommunication {

    private static final Logger logger = Logger.getLogger(ServiceCommunication.class.getName());

    private static final ProxyTCPServer proxyTCPServer;

    private static final AtomicInteger ID_SEQ = new AtomicInteger(0);

    private static final ConcurrentSkipListMap<Integer, PredicatesInfoCache> IDSave = new ConcurrentSkipListMap<Integer, PredicatesInfoCache>();

    private static final ConcurrentHashMap<String, ConcurrentHashMap<ServiceID, ConcurrentSkipListSet<Integer>>> FilterSave = new ConcurrentHashMap<String, ConcurrentHashMap<ServiceID, ConcurrentSkipListSet<Integer>>>();

    private static final ConcurrentLinkedQueue<Integer> reusedIDs = new ConcurrentLinkedQueue<Integer>();

    // hope it is unique
    public static final String KEY_SEPARATOR = "//\\|\\/";

    // this should be used to allow only one thread to modify the state of IDSave
    // the normal delivery will still be very light synch-ed
    private static final Lock criticalSectionIDSaveLock = new ReentrantLock();

    // this should be used to allow only one thread to modify the state of FilterSave
    // the normal delivery will still be very light synch-ed
    private static final Lock criticalSectionFilterSaveSaveLock = new ReentrantLock();

    private static final AtomicLong receivedM = new AtomicLong(0);

    private static final AtomicLong sentM = new AtomicLong(0);

    /*
     * here are the groups and farm names that were sent in a status mail. it was returned by FarmCommunication ->
     * getFarmsNamesGroups. it is good for getting new farms and groups that appeared in LUSs
     */
    private static final HashMap<String, TreeSet<String>> lastMailSent = new HashMap<String, TreeSet<String>>();

    /*
     * between mail report interval there can be farms that appear and dissapears. these farms have to be raported also.
     * so this vector maintains new farms' names between mails and is cleared after a new report. do not play with this
     * hash :D. It is modified from FarmCommunication and ProxyTXPWorker every time a new farm appears, using the
     * function notifyNewFarm from ServiceCommunication, which is a singleton class.
     */
    private static final ConcurrentSkipListMap<String, Integer> newFarmsMailInterval = new ConcurrentSkipListMap<String, Integer>();

    private static final ConcurrentSkipListSet<String> newGroupsMailInterval = new ConcurrentSkipListSet<String>(); /*
                                                                                                                     * almost
                                                                                                                     * the
                                                                                                                     * same
                                                                                                                     * comment
                                                                                                                     */

    private static final String serviceName;

    private static Date mailLastSent = null;

    private static AtomicBoolean ignoreDiff = new AtomicBoolean(false);

    static {

        // add a timer. This timer sends status mail every one day.
        Timer timer = new Timer();
        timer.schedule(new ProxyMailSender(), 600000, 43200000 * 2);
        // timer.schedule(new ProxyMailSender(), 5 * 60 * 1000, 5 * 60 * 1000);

        ProxyTCPServer tmpServer = null;
        try {
            tmpServer = ProxyTCPServer.getInstance();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ ServiceCommunication ] got exception in initialization", t);
            System.exit(1);
        }

        if (tmpServer == null) {
            logger.log(Level.WARNING, " [ ServiceCommunication ] ProxyTCPServer is null ... will exit now");
            System.exit(1);
        }
        proxyTCPServer = tmpServer;

        /**
         * set serviceName
         */
        String tmpServiceName = AppConfig.getProperty("lia.Monitor.ClientsFarmProxy.Name");

        if (tmpServiceName == null) {
            try {
                tmpServiceName = InetAddress.getLocalHost().getHostName();
            } catch (Throwable t) {
                tmpServiceName = "N/A";
            }
        }// if

        serviceName = tmpServiceName;
        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                // TODO Auto-generated method stub
                reloadConf();
            }

        });

        reloadConf();
    }

    private static final void reloadConf() {
        ignoreDiff.set(AppConfig.getb("lia.Monitor.ClientsFarmProxy.ServiceCommunication.ignoreDiffConf", false));
        logger.log(Level.INFO, " [ ServiceCommunication ] ignoreDiff: " + ignoreDiff.get());
    }

    public static final String getServiceName() {
        return serviceName;
    }

    public static final int[] getPorts() {
        return proxyTCPServer.getPorts();
    } // getPorts

    public static final String[] getExternalAddresses() {
        return proxyTCPServer.getExternalAddresses();
    }

    public static final String getHostname() {
        return proxyTCPServer.getHostname();
    }

    public static final void notifyNewFarm(final ServiceItem si) {
        if (si == null) {
            return;
        }

        Entry[] attr = si.attributeSets;

        for (Entry element : attr) {
            if (element instanceof MonaLisaEntry) {
                MonaLisaEntry mle = (MonaLisaEntry) element;

                if (mle.Name != null) {
                    Integer aparitii = newFarmsMailInterval.get(mle.Name);
                    if (aparitii == null) {
                        aparitii = Integer.valueOf(1);
                    } else {
                        aparitii++;
                    }
                    newFarmsMailInterval.put(mle.Name, Integer.valueOf(aparitii));
                }

                if (mle.Group != null) {
                    String[] grps = mle.Group.split(",");
                    for (String grp : grps) {
                        newGroupsMailInterval.add(grp);
                    } // for
                } // if
                break;
            } // if
        } // for

    } // notifyNewFarm

    public static final Vector<ServiceID> getFarmsIDs() {
        final Vector<ServiceID> v = new Vector<ServiceID>();
        Set<ServiceID> e = FarmCommunication.getFarmsIDs();
        for (final ServiceID sid : e) {
            v.add(sid);
        }
        return v;
    }

    public static final int getFarmsNr() {
        return FarmCommunication.getFarmsNr();
    } // getFarmsNr

    public static final ConcurrentSkipListMap<Integer, PredicatesInfoCache> getIDSave() {
        return IDSave;
    }

    public static final HashMap<String, TreeMap<String, Integer>> parseConfigurations() {
        HashMap<String, TreeMap<String, Integer>> rez = new HashMap<String, TreeMap<String, Integer>>();
        TreeMap<String, Integer> hashNodes = new TreeMap<String, Integer>();
        TreeMap<String, Integer> hashParams = new TreeMap<String, Integer>();

        final Map<ServiceID, FarmWorker> confStat = FarmCommunication.getFarmsHash();

        int total_params = 0;
        int total_nodes = 0;

        for (final Map.Entry<ServiceID, FarmWorker> entry : confStat.entrySet()) {

            final ServiceID farmID = entry.getKey();
            final FarmWorker fw = entry.getValue();
            final MFarmClientConfigInfo mcfi = fw.getClientConfigInfo();

            Set<String> groups = fw.getGroups();

            final int nc = (int) mcfi.getNodesCount();
            final int pc = (int) mcfi.getParamsCount();

            total_nodes += nc;
            total_params += pc;

            for (final String group : groups) {

                Integer nrNodes = hashNodes.get(group);
                if (nrNodes == null) {
                    nrNodes = 0;
                }

                nrNodes += nc;
                hashNodes.put(group, nrNodes);

                Integer paramsCount = hashParams.get(group);
                if (paramsCount == null) {
                    paramsCount = 0;
                }

                paramsCount += pc;
                hashParams.put(group, paramsCount);
            }
        }

        hashNodes.put("total", total_nodes);
        hashParams.put("total", total_params);

        rez.put("nodes", hashNodes);
        rez.put("params", hashParams);
        return rez;
    } // parseConfigurations

    public static final long getNrAgentsMsg() {
        return FarmCommunication.getNrAgentsMsg();
    }

    public static final void receiveMessage(MonMessageClientsProxy mmessage) {
        final Object ident = mmessage.ident;

        final String filtru = mmessage.tag;

        if (filtru.startsWith(monMessage.ML_TIME_TAG) || filtru.startsWith(monMessage.ML_VERSION_TAG)) {
            final Collection<ClientWorker> clients = ClientsCommunication.getClients();
            for (final ClientWorker w : clients) {
                if (w != null) {
                    if (!w.isInterstedForConfigMsg(mmessage.farmID)) {
                        continue;
                    }
                    w.sendMsg(mmessage);
                }// w != null
            } // while
            return;
        }

        // Check if it's a configuration message or if it's a local time message
        // or MonALISA version
        if ((filtru != null) && filtru.startsWith(monMessage.ML_CONFIG_TAG)) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Got CONFIG MSG for SID == " + mmessage.farmID + " "
                        + ((MFarm) mmessage.result).name);
            }

            // Save configuratiOon for future clients that will come use
            final MFarm[] confDiff = ClientsCommunication.addFarmConfiguration(mmessage.farmID, mmessage);

            final MonMessageClientsProxy diffMesg = (confDiff == ClientsCommunication.NO_DIFF_CONF) ? mmessage
                    : new MonMessageClientsProxy(mmessage.tag, mmessage.ident, confDiff, mmessage.farmID);

            // Send the configuration or time/version to all clients
            final Collection<ClientWorker> clients = ClientsCommunication.getClients();
            for (final ClientWorker w : clients) {
                if (w != null) {
                    if (!w.isInterstedForConfigMsg(mmessage.farmID)) {
                        continue;
                    }

                    // if the client is new, send only the differences ....
                    if (w.isNew() && !ignoreDiff.get()) {
                        if (diffMesg != null) {
                            w.sendMsg(diffMesg);
                            continue;
                        }
                    }

                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, " [ ServiceCommunication ] sending entire conf for for SID == "
                                + mmessage.farmID + " " + ((MFarm) mmessage.result).name + " to client: " + w);
                    }

                    w.sendMsg(mmessage);
                }// w != null
            } // while

            return;
        }

        // Results for a monPredicate that we've sent earlier
        if (ident instanceof Integer) {

            final Integer predID = (Integer) ident;

            final PredicatesInfoCache mic = IDSave.get(ident);
            if (mic == null) {
                // maybe it died without unregistering
                sendUnregisterMessage(predID, mmessage.farmID);
                return;
            }

            if (!mic.isHistory && (mmessage.result == null)) {
                return;
            } // if

            final ConcurrentSkipListMap<Integer, ConcurrentSkipListSet<Integer>> regC = mic.msgs;

            if (regC != null) {
                // send message to all registered clients for this message
                try {
                    for (final Map.Entry<Integer, ConcurrentSkipListSet<Integer>> entry : regC.entrySet()) {
                        try {

                            final ClientWorker cw = ClientsCommunication.getClient(entry.getKey());
                            if (cw == null) {
                                continue;
                            }
                            final ConcurrentSkipListSet<Integer> regMsgs = entry.getValue();

                            for (final Integer messageID : regMsgs) {
                                try {
                                    MonMessageClientsProxy mm = new MonMessageClientsProxy(mmessage.tag, messageID,
                                            mmessage.result, mmessage.farmID);
                                    cw.sendMsg(mm);
                                } catch (Throwable ex) {
                                    logger.log(Level.WARNING, " [ ServiceCommunication ] ex ", ex);
                                }
                            } // for regMsgs
                        } catch (Throwable ex1) {
                            logger.log(Level.WARNING, " [ ServiceCommunication ] ex1 ", ex1);
                        }
                    } // for enum
                } catch (Throwable ex2) {
                    logger.log(Level.WARNING, " [ ServiceCommunication ] ex2 ", ex2);
                }
            } else {
                sendUnregisterMessage(predID, mmessage.farmID);
                return;
            }
            return;
        } // if

        // Results from Filters
        if (ident instanceof String) {
            if ((ident != null) && ident.equals(monMessage.ML_SID_TAG)) {
                return;
            }
            ConcurrentHashMap<ServiceID, ConcurrentSkipListSet<Integer>> hash = FilterSave.get(ident);
            if ((hash != null) && (mmessage.farmID != null)) {
                ConcurrentSkipListSet<Integer> vect = hash.get(mmessage.farmID);
                if (vect != null) {
                    for (final Integer clientID : vect) {
                        try {
                            final ClientWorker cw = ClientsCommunication.getClient(clientID);
                            if (cw != null) {
                                cw.sendMsg(mmessage);
                            }
                        } catch (Throwable ex3) {
                            logger.log(Level.WARNING, " Got exception notifying filter", ex3);
                        }
                    } // for
                }
            }
            return;
        } // if

        // We should not normaly reach so far ... there is nothing specified in
        // the protcol ...

        logger.log(Level.SEVERE, "\n\n [ ServiceCommunication ] Hmm ... strange ident [ " + ident + " ] for message [ "
                + mmessage + " ] \n\n");
    }

    public static final long getNrSentM() {
        return sentM.getAndSet(0L);
    } // getNrSentM

    public static final void incSendMsg() {
        sentM.getAndIncrement();
    }

    public static final void incRecvMsg() {
        receivedM.getAndIncrement();
    }

    public static final long getNrReceivedM() {
        return receivedM.getAndSet(0L);
    } // getNrSentM

    public static final void sendMessage(MonMessageClientsProxy mmessage, Integer clientID) {
        Object ident = mmessage.ident;
        // boolean sendIt = false;
        ArrayList<MonMessageClientsProxy> msgToSend = new ArrayList<MonMessageClientsProxy>();

        if (ident instanceof Integer) {
            // here I have to verify if the message is one for
            // unregistering if so, find the id from the ones memorized , send
            // the message with it and remove the id from the proxy .
            if (mmessage.tag.startsWith(monMessage.PREDICATE_UNREGISTER_TAG)) {
                // an unregister message .... for predicate ident
                Integer umessageId = (Integer) ident;

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "Unregister message for predicate ====> " + mmessage);
                } // if

                criticalSectionIDSaveLock.lock();
                try {

                    for (Iterator<Map.Entry<Integer, PredicatesInfoCache>> itIDSave = IDSave.entrySet().iterator(); itIDSave
                            .hasNext();) {
                        final Map.Entry<Integer, PredicatesInfoCache> entryIDSave = itIDSave.next();

                        final Integer messNewID = entryIDSave.getKey();
                        final PredicatesInfoCache storedMessCache = entryIDSave.getValue();

                        ConcurrentSkipListMap<Integer, ConcurrentSkipListSet<Integer>> regC = storedMessCache.msgs;
                        if (regC != null) {
                            final ConcurrentSkipListSet<Integer> regmsgs = regC.get(clientID);

                            if (regmsgs != null) {
                                regmsgs.remove(umessageId);

                                if (regmsgs.size() == 0) {
                                    // make sure that we do not add something in
                                    // between
                                    regC.remove(clientID);
                                }

                                if (regC.size() == 0) {
                                    itIDSave.remove();
                                    reusedIDs.offer(messNewID);
                                    MonMessageClientsProxy unreg = new MonMessageClientsProxy(mmessage.tag, messNewID,
                                            mmessage.result, mmessage.farmID);
                                    msgToSend.add(unreg);
                                    if (logger.isLoggable(Level.FINE)) {
                                        logger.log(Level.FINE, " Removed a predicate for FARM ! " + mmessage.farmID
                                                + "/      " + messNewID);
                                    }
                                }
                            }
                        }
                    }// for - IDSave.entrySet()
                } catch (Throwable t) {
                    logger.log(Level.WARNING,
                            " [ ServiceCommunication ] Unregister predicate got exception in CRITICAL_SECTION", t);
                } finally {
                    criticalSectionIDSaveLock.unlock();
                }

            } else {

                // a client request registering with a new predicate;
                // see if this key predicate for this farm is already registered
                final monPredicate monPred = (monPredicate) mmessage.result;
                if (monPred == null) { // a wrong predicate
                    return;
                }

                StringBuilder keyBuilder = new StringBuilder();
                keyBuilder.append((monPred.Farm == null) ? "*" : monPred.Farm);
                keyBuilder.append(KEY_SEPARATOR);
                keyBuilder.append((monPred.Cluster == null) ? "*" : monPred.Cluster);
                keyBuilder.append(KEY_SEPARATOR);
                keyBuilder.append((monPred.Node == null) ? "*" : monPred.Node);
                keyBuilder.append(KEY_SEPARATOR);
                if (monPred.parameters == null) {
                    keyBuilder.append("*");
                } else {
                    int len = monPred.parameters.length;
                    for (int p = 0; p < len; p++) {
                        keyBuilder.append(monPred.parameters[p]).append((p < (len - 1)) ? KEY_SEPARATOR : "");
                    } // for
                }

                long tmin = monPred.tmin;
                long tmax = monPred.tmax;

                final String key = keyBuilder.toString();

                criticalSectionIDSaveLock.lock();

                try {
                    boolean isAlready = false;
                    if ((tmin == 0) && (tmax == -1)) {
                        for (final PredicatesInfoCache pred : IDSave.values()) {

                            if (!pred.isHistory && key.equals(pred.key) && pred.farmID.equals(mmessage.farmID)) {
                                ConcurrentSkipListSet<Integer> regmsgs = pred.msgs.putIfAbsent(clientID,
                                        new ConcurrentSkipListSet<Integer>());
                                if (regmsgs == null) {
                                    regmsgs = pred.msgs.get(clientID);
                                }

                                regmsgs.add((Integer) mmessage.ident);
                                if (logger.isLoggable(Level.FINER)) {
                                    logger.log(Level.FINER,
                                            "Predicate already registered ... adding it " + pred.msgs.size() + "    "
                                                    + clientID + "     " + mmessage.ident);
                                }
                                isAlready = true;
                                break;
                            } // if
                        } // for enum
                    } // if

                    if (!isAlready) { // a new predicate; register it
                        boolean isHistory = true;
                        if ((tmin == 0) && (tmax == -1)) {
                            isHistory = false;
                        } // if

                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINER, "Registering predicate with key: " + key + " for farm "
                                    + mmessage.farmID + " and client " + clientID + "   and history " + isHistory);
                        }

                        PredicatesInfoCache mic = new PredicatesInfoCache(key, mmessage.farmID, isHistory);
                        ConcurrentSkipListSet<Integer> regmsgs = new ConcurrentSkipListSet<Integer>();
                        regmsgs.add((Integer) mmessage.ident);
                        mic.msgs.put(clientID, regmsgs);
                        ServiceID farmID = mmessage.farmID;
                        if (farmID != null) {
                            Integer newID = reusedIDs.poll();

                            if (newID == null) {
                                newID = Integer.valueOf(ID_SEQ.getAndIncrement());
                            }
                            monPred.id = newID.intValue();

                            final MonMessageClientsProxy newMsg = new MonMessageClientsProxy(mmessage.tag, newID,
                                    monPred, mmessage.farmID);
                            mic.result = newMsg;
                            if (IDSave.put(newID, mic) != null) {
                                logger.log(Level.WARNING,
                                        " [ ProtocolException ] [ SyncExcVerif ] IDSave already contains ID = " + newID);
                            }
                            msgToSend.add(newMsg);
                        } // if
                    } // if
                } catch (Throwable t) {
                    logger.log(Level.WARNING,
                            " [ ServiceCommunication ] Register predicate got exception in CRITICAL_SECTION");
                } finally {
                    criticalSectionIDSaveLock.unlock();
                }

            } // else - register
        } else if (ident instanceof String) {
            // sendIt = true;
            if (mmessage.farmID == null) {
                return;
            }

            ConcurrentHashMap<ServiceID, ConcurrentSkipListSet<Integer>> h = FilterSave.get(ident);
            final ServiceID farm = mmessage.farmID;

            criticalSectionFilterSaveSaveLock.lock();
            try {
                if (mmessage.tag.startsWith(monMessage.FILTER_UNREGISTER_TAG)) {
                    logger.log(Level.INFO, "Unregistering client from filter " + ident + " for farm " + farm);
                    if (h != null) {
                        ConcurrentSkipListSet<Integer> clients = h.get(farm);
                        if (clients != null) {
                            clients.remove(clientID);
                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, " Removing client [ " + clientID + " ] for FILTER = " + ident
                                        + " farm " + farm);
                            }
                            if (clients.size() == 0) {
                                // make sure that there wasn't another add in between
                                h.remove(farm);
                                if (h.size() == 0) {
                                    FilterSave.remove(ident);
                                    logger.log(Level.INFO, " Proxy will NOT LISTEN for FILTER = " + ident);
                                    msgToSend.add(mmessage);
                                }
                            }
                        } else {
                            logger.log(Level.INFO, " Proxy will NOT LISTEN for FILTER = " + ident);
                            msgToSend.add(mmessage);
                        }
                    } else {
                        logger.log(Level.INFO, " Proxy will NOT LISTEN for FILTER = " + ident);
                        msgToSend.add(mmessage);
                    }
                    return;
                }

                // a register message
                h = FilterSave.get(ident);
                if (h == null) {
                    h = FilterSave.putIfAbsent((String) ident,
                            new ConcurrentHashMap<ServiceID, ConcurrentSkipListSet<Integer>>());
                    if (h != null) {
                        // it should neveeeer go this faarrrr, only if there is a problem in the synch mechanism
                        logger.log(Level.WARNING,
                                " [ ServiceCommunication ] Register FILTER CRITICAL_SECTION the hashmap has gone way too long... h: "
                                        + h);
                    } else {
                        h = FilterSave.get(ident);
                    }
                }

                ConcurrentSkipListSet<Integer> clients = h.get(farm);
                if (clients == null) {// no previous values
                    clients = h.putIfAbsent(farm, new ConcurrentSkipListSet<Integer>());
                    if (clients != null) {
                        // it should neveeeer go this faarrrr, only if there is a problem in the synch mechanism
                        logger.log(Level.WARNING,
                                " [ ServiceCommunication ] Register FILTER CRITICAL_SECTION the clientsmap has gone way too long... clients: "
                                        + clients);
                    } else {
                        clients = h.get(farm);
                    }
                }

                if (clients.add(clientID)) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, " Adding client [ " + clientID + " ] for Filter = " + ident
                                + " Farm = " + farm);
                    }
                } else {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, " Already in cache client [ " + clientID + " ] for Filter = " + ident
                                + " Farm = " + farm);
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING,
                        " [ ServiceCommunication ] [ HANDLED ] Register FILTER CRITICAL_SECTION got exception: ", t);
            } finally {
                criticalSectionFilterSaveSaveLock.unlock();
            }

            msgToSend.add(mmessage);
        } else {
            logger.log(Level.WARNING, " UNKNOWN class in message indent ");
        }

        for (final MonMessageClientsProxy mMessage : msgToSend) {
            if ((mMessage != null) && (mMessage.farmID != null)) {
                FarmCommunication.addMessageToSend(mMessage.farmID, mMessage);
            }
        } // for

    }

    public static final long getFilterSaveDeepSize() {
        long deepSize = 0;
        for (final ConcurrentHashMap<ServiceID, ConcurrentSkipListSet<Integer>> chash : FilterSave.values()) {
            for (final ConcurrentSkipListSet<Integer> thash : chash.values()) {
                deepSize += thash.size();
            }
        }
        return deepSize;
    }

    public static final long getIDSaveDeepSize() {
        long deepSize = 0;
        for (final PredicatesInfoCache pic : IDSave.values()) {
            for (final ConcurrentSkipListSet<Integer> clist : pic.msgs.values()) {
                deepSize += clist.size();
            }
        }
        return deepSize;
    }

    public static final void unregisterMessages(final Integer unregisterClientID) {
        // unregister predicates when a client dies
        criticalSectionIDSaveLock.lock();

        try {

            for (Iterator<Map.Entry<Integer, PredicatesInfoCache>> it = IDSave.entrySet().iterator(); it.hasNext();) {

                final Map.Entry<Integer, PredicatesInfoCache> entry = it.next();

                final Integer newID = entry.getKey();
                final PredicatesInfoCache mic = entry.getValue();
                final ConcurrentSkipListMap<Integer, ConcurrentSkipListSet<Integer>> cli = mic.msgs;
                cli.remove(unregisterClientID);

                if (cli.size() == 0) {
                    MonMessageClientsProxy mmcp = new MonMessageClientsProxy(monMessage.PREDICATE_UNREGISTER_TAG,
                            newID, null, mic.farmID);
                    FarmCommunication.addMessageToSend(mic.farmID, mmcp);
                    it.remove();
                    reusedIDs.offer(newID);
                }
            } // for

        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ ServiceCommunication ] [ HANDLED ] Unregister client [ " + unregisterClientID
                    + " ] CRITICAL_SECTION predicates got exception: ", t);
        } finally {
            criticalSectionIDSaveLock.unlock();
        }

        criticalSectionFilterSaveSaveLock.lock();

        try {
            // unregister filters from farm
            for (final Map.Entry<String, ConcurrentHashMap<ServiceID, ConcurrentSkipListSet<Integer>>> entry : FilterSave
                    .entrySet()) {
                final String filter = entry.getKey();
                ConcurrentHashMap<ServiceID, ConcurrentSkipListSet<Integer>> v = entry.getValue();

                if (v != null) {
                    for (final Map.Entry<ServiceID, ConcurrentSkipListSet<Integer>> iEntry : v.entrySet()) {
                        final ServiceID farmID = iEntry.getKey();
                        final ConcurrentSkipListSet<Integer> hmID = iEntry.getValue();
                        hmID.remove(unregisterClientID);
                        if (hmID.size() == 0) {
                            MonMessageClientsProxy mess = new MonMessageClientsProxy(monMessage.FILTER_UNREGISTER_TAG,
                                    filter, null, farmID);
                            logger.log(Level.INFO, " Proxy will NOT LISTEN for FILTER = " + filter);
                            FarmCommunication.addMessageToSend(farmID, mess);
                        }
                    }// for
                } // if
            } // for
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ ServiceCommunication ] [ HANDLED ] Unregister client [ " + unregisterClientID
                    + " ] CRITICAL_SECTION filters got exception: ", t);
        } finally {
            criticalSectionFilterSaveSaveLock.unlock();
        }

    } // unregisterMessages

    private static final void sendUnregisterMessage(Integer messageID, ServiceID farmID) {
        MonMessageClientsProxy mmcp = new MonMessageClientsProxy(monMessage.PREDICATE_UNREGISTER_TAG, messageID, null,
                farmID);
        FarmCommunication.addMessageToSend(farmID, mmcp);
    } // sendUnregisterMessage

    /**
     * periodically sends a mail with farms' statistics and groups
     */
    static final class ProxyMailSender extends TimerTask {

        private String composeMail() {

            HashMap<String, Integer> newFarms = null;
            ArrayList<String> newGroups = null;
            HashMap<String, Integer> deadFarms = null;
            ArrayList<String> passedByGroups = null;

            final HashMap<String, TreeSet<String>> h = FarmCommunication.getFarmsNamesGroups();
            final TreeSet<String> lastMailSentNames = lastMailSent.get("names");
            final TreeSet<String> lastMailSentGroups = lastMailSent.get("groups");

            StringBuilder sb = new StringBuilder(16384);
            sb.append("\n\nMLProxy Version: ").append(Service.MonaLisa_version).append(" [ ")
                    .append(Service.MonaLisa_vdate).append(" ]");

            // add the uptime to the report
            sb.append("\nMLProxy Uptime: ");
            long seconds = TimeUnit.NANOSECONDS.toSeconds(Utils.nanoNow() - Service.getStartTime());
            final long days = seconds / (3600 * 24);
            seconds -= days * 3600 * 24;
            final long hours = seconds / 3600;
            seconds -= hours * 3600;
            final long mins = seconds / 60;
            seconds -= mins * 60;

            if (days != 0) {
                sb.append(((days < 10) ? "0" : "")).append(days).append(" day(s) ");
            }

            sb.append(((hours < 10) ? "0" : "")).append(hours).append(":");
            sb.append(((mins < 10) ? "0" : "")).append(mins).append(":");
            sb.append(((seconds < 10) ? "0" : "")).append(seconds);

            sb.append("\nML Proxy Report interval [ ");
            if (mailLastSent != null) {
                sb.append(mailLastSent).append(" - ");
            }

            final Date newDate = new Date(System.currentTimeMillis());
            sb.append(newDate);
            sb.append(" ]");

            sb.append("\n\n");

            sb.append("Number of ML Services in the current report: ");
            TreeSet<String> v = null;
            if (h != null) {
                v = h.get("names");
            }

            if (v != null) {

                sb.append(v.size());
                sb.append(") ");

                if (lastMailSentNames != null) {
                    newFarms = new HashMap<String, Integer>();
                    deadFarms = new HashMap<String, Integer>();
                    passedByGroups = new ArrayList<String>();
                    sb.append(" / No of ML Services in the previous report: ").append(lastMailSentNames.size())
                            .append(" \n");
                } // if

                sb.append("\n------------------------------------------------------------------\n");

                for (final String name : v) {
                    sb.append(name).append("  ");
                    if ((newFarms != null) && !(lastMailSentNames.contains(name))) {
                        Integer x = newFarmsMailInterval.get(name);
                        if (x == null) {
                            x = 0;
                        } // if
                        newFarms.put(name, x);
                    } // if
                } // for

                if (deadFarms != null) {
                    for (final String name : lastMailSentNames) {
                        if (!v.contains(name)) {
                            Integer x = newFarmsMailInterval.get(name);
                            if (x == null) {
                                x = 0;
                            } // if
                            deadFarms.put(name, Integer.valueOf(x));
                        } // if
                    } // for
                } // if

                if (newFarms != null) {
                    for (final Map.Entry<String, Integer> entry : newFarmsMailInterval.entrySet()) {
                        final String farmN = entry.getKey();
                        final int value = entry.getValue();

                        if (!lastMailSentNames.contains(farmN) && !v.contains(farmN)) {
                            newFarms.put(farmN, value);
                            deadFarms.put(farmN, value);
                        }
                    } // for
                    newFarmsMailInterval.clear();
                } // if
            } else {
                sb.append("0) \n");
            }

            sb.append("\n\nClients (");
            sb.append(ClientsCommunication.getNumberOfClients());
            sb.append(") : \n");
            sb.append("------------------------------------------------------------------\n");

            sb.append(ClientsCommunication.getInfoMailRaport(mailLastSent, newDate));

            sb.append("\n\n").append("Groups (");

            TreeSet<String> g = null;

            if (h != null) {
                g = h.get("groups");
            }

            if (g != null) {

                sb.append(g.size());
                sb.append(") :\n");
                sb.append("------------------------------------------------------------------\n");

                if (lastMailSentGroups != null) {
                    newGroups = new ArrayList<String>();
                } // if

                for (final String group : g) {
                    sb.append(group).append(" ");
                    if ((newGroups != null) && !lastMailSentGroups.contains(group)) {
                        newGroups.add(group);
                    } // if
                } // for

                if (passedByGroups != null) {
                    for (final String group : newGroupsMailInterval) {
                        if (!(lastMailSentGroups.contains(group)) && !g.contains(group)) {
                            passedByGroups.add(group);
                        } // if
                    }// for
                    newGroupsMailInterval.clear();
                } // passedByGroups

            } else {
                sb.append("0) \n");
            } // if - else

            sb.append("\n\n===================================================================\n\n");

            if (newFarms != null) {
                sb.append("New services ( ").append(newFarms.size()).append(") : \n");
                sb.append("------------------------------------------------------------------\n");
                for (final Map.Entry<String, Integer> entry : newFarms.entrySet()) {
                    final String farmN = entry.getKey();
                    sb.append(farmN);
                    final int val = entry.getValue();
                    if (val > 0) {
                        sb.append("(").append(val).append(")  ");
                    } else {
                        sb.append(" ");
                    } // if - else
                } // for
                sb.append("\n\n");
            } // if

            if (deadFarms != null) {
                sb.append("Disapeared services (").append(deadFarms.size()).append(") :\n");
                sb.append("------------------------------------------------------------------\n");
                for (java.util.Map.Entry<String, Integer> entry : deadFarms.entrySet()) {
                    final String farmN = entry.getKey();
                    sb.append(farmN);
                    final int val = entry.getValue();
                    if (val > 0) {
                        sb.append("(").append(val).append(")  ");
                    } else {
                        sb.append(" ");
                    }
                } // for
                sb.append("\n\n");
            } // if

            if (newGroups != null) {
                sb.append("New groups (").append(newGroups.size()).append(") :\n");
                sb.append("------------------------------------------------------------------\n");
                for (final String group : newGroups) {
                    sb.append(group).append("  ");
                } // for
                sb.append("\n\n");
            } // if

            if ((passedByGroups != null) && (passedByGroups.size() > 0)) {
                sb.append("Passed by groups (").append(passedByGroups.size()).append(") :\n");
                sb.append("------------------------------------------------------------------\n");
                for (final String group : passedByGroups) {
                    sb.append(group).append("  ");
                } // for
                sb.append("\n\n");
            } // if

            // remember what was sent by mail
            lastMailSent.clear();
            lastMailSent.putAll(h);

            mailLastSent = newDate;
            sb.append("\n\n------------------------------------------------------------------\n");

            return sb.toString();
        } // composeMail.

        @Override
        public void run() {
            String[] RCPTS = null;
            try {
                RCPTS = AppConfig.getVectorProperty("mlproxy_mail.RCPTs", "developers@monalisa.cern.ch");
                final long startRaportTime = System.currentTimeMillis();
                final String composedMail = composeMail();
                String mailBody = "\n\nThis report was generated in: " + (System.currentTimeMillis() - startRaportTime)
                        + " ms\n\n" + composedMail;
                MailFactory.getMailSender().sendMessage("proxystatus@monalisa.cern.ch", RCPTS,
                        "[proxy(" + serviceName + ")] status", mailBody);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Got exception sending mail", t);
                final String finalMail = new Date() + "\n\n Got exception processing mail. Cause:\n"
                        + Utils.getStackTrace(t);

                try {
                    if (RCPTS == null) {
                        RCPTS = new String[] { "ramiro.voicu@gmail.com" };
                    }
                    MailFactory.getMailSender().sendMessage("proxystatus@monalisa.cern.ch", RCPTS,
                            "[proxy(" + serviceName + ")] ERROR", finalMail);
                } catch (Throwable t1) {
                    logger.log(Level.WARNING, "Got exception sending FINAL mail, giving up ...", t1);
                }
            } // try - catch

            RCPTS = null;
            try {
                RCPTS = AppConfig.getVectorProperty("mlproxy_devel_mail.RCPTs");

                if ((RCPTS == null) || (RCPTS.length == 0)) {
                    return;
                }

                final long startRaportTime = System.currentTimeMillis();
                final long IDSaveDeepSize = getIDSaveDeepSize();
                final long FilterSaveDeepSize = getFilterSaveDeepSize();

                StringBuilder sb = new StringBuilder(8192);
                sb.append("\n\n IDSave.size() = ").append(IDSave.size()).append(" IDSave.deepSize() = ")
                        .append(IDSaveDeepSize);
                sb.append("\n FilterSave.size() = ").append(FilterSave.size()).append(" FilterSave.deepSize() = ")
                        .append(FilterSaveDeepSize);
                sb.append("\n\n StringFactory HitRatio ").append(StringFactory.getHitRatio()).append(" size: ")
                        .append(StringFactory.getCacheSize());
                sb.append("\n\n This mail was generated in: ").append(System.currentTimeMillis() - startRaportTime)
                        .append(" ms");

                MailFactory.getMailSender().sendMessage("proxystatus@monalisa.cern.ch", RCPTS,
                        "[proxy(" + serviceName + ")] devel status", sb.toString());
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Got exception sending mail", t);
                final String finalMail = new Date() + "\n\n Got exception processing mail. Cause:\n"
                        + Utils.getStackTrace(t);

                try {
                    if (RCPTS == null) {
                        RCPTS = new String[] { "ramiro.voicu@gmail.com" };
                    }
                    MailFactory.getMailSender().sendMessage("proxystatus@monalisa.cern.ch", RCPTS,
                            "[proxy(" + serviceName + ")] ERROR devel status", finalMail);
                } catch (Throwable t1) {
                    logger.log(Level.WARNING, "Got exception sending FINAL mail, giving up ...", t1);
                }
            } // try - catch

        } // run

    } // ProxyMailSender

} // ServiceCommunication
