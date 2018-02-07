package lia.Monitor.JiniClient.ReflRouter;

import java.net.InetAddress;
import java.security.PrivilegedExceptionAction;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniSerFarmMon.GMLEPublisher;
import lia.Monitor.JiniSerFarmMon.MLLUSHelper;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.GenericMLEntry;
import lia.Monitor.monitor.MLJiniManagersProvider;
import lia.util.JiniConfigProvider;
import lia.util.SecureContextExecutor;
import lia.util.ntp.NTPDate;
import net.jini.config.Configuration;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.discovery.DiscoveryEvent;
import net.jini.discovery.DiscoveryListener;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lookup.JoinManager;
import net.jini.lookup.ServiceDiscoveryEvent;
import net.jini.lookup.ServiceDiscoveryListener;
import net.jini.lookup.ServiceDiscoveryManager;
import net.jini.lookup.ServiceIDListener;
import net.jini.lookup.entry.Name;

public class ReflRouterJiniService extends Thread implements ServiceIDListener, ServiceDiscoveryListener,
        DiscoveryListener {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(ReflRouterJiniService.class.getName());

    private static final long WAIT_FOR_SID_TIMEOUT = AppConfig.getl(
            "lia.Monitor.JiniClient.ReflRouter.WAIT_FOR_SID_TIMEOUT", 300) * 1000;

    private static ReflRouterJiniService _theInstance;
    private static ReflRouterJiniProxy myJiniProxy;
    private static GenericMLEntry gmle;
    private ServiceID mysid;

    protected transient LookupDiscoveryManager ldm;
    protected transient LeaseRenewalManager lrm;
    protected transient JoinManager jmngr;
    protected transient ServiceDiscoveryManager sdm;

    private final AtomicBoolean iAmMaster;
    private final AtomicBoolean iAmAvailable;
    private boolean active = false;
    private final Hashtable htKnownRR; // key: ReflRouterJiniProxy, value: GenericMLEntry for that service

    private ReflRouterJiniService() {
        super("( ML ) - ReflRouterJiniService");
        active = true;
        setDaemon(true);

        // Preparing the proxy...
        myJiniProxy = new ReflRouterJiniProxy();
        myJiniProxy.ip = getMyIP();
        myJiniProxy.groups = groupsToString(getGroups());

        // preparing my attributes ...
        gmle = new GenericMLEntry();
        gmle.hash.put("reflectors", Integer.valueOf(0));

        htKnownRR = new Hashtable();
        iAmMaster = new AtomicBoolean(false);
        iAmAvailable = new AtomicBoolean(false);
    }

    /** Get the one and only one instance of this class */
    public static synchronized ReflRouterJiniService getInstance() {
        if (_theInstance == null) {
            try {
                // shall this use secure LUSs ?
                _theInstance = new ReflRouterJiniService();
                _theInstance.setDaemon(false);
                _theInstance.start();
                //    	    	boolean useSecLUSs = AppConfig.getb("lia.Monitor.JiniClient.ReflRouter.useSecureLUSs", false);
                //    	    	if(useSecLUSs)
                //    	    		createInstanceWithSecureLUSs();
                //    	    	else
                //    	    		_theInstance = createInstance();
                logger.log(Level.INFO,
                        "Created ReflRouterJiniService instance; secureLUS="
                                + SecureContextExecutor.getInstance().bUseSecureLUSs + " instance=" + _theInstance);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Could NOT create the ReflRouterJiniService", t);
            }
        }
        return _theInstance;
    }

    /** Return the sorted list of groups followed by this ReflRouter */
    private String[] getGroups() {
        String mgroup = AppConfig.getProperty("lia.Monitor.group", "ml").trim();
        Set groups = new TreeSet();
        StringTokenizer stk = new StringTokenizer(mgroup, ",");
        while (stk.hasMoreTokens()) {
            String group = stk.nextToken().trim();
            if (group.length() > 0) {
                groups.add(group);
            }
        }
        if (groups.size() == 0) {
            logger.log(Level.WARNING,
                    "You should have at least a group defined in app.properties for key lia.Monitor.group");
        }
        String[] result = new String[groups.size()];
        groups.toArray(result);
        return result;
    }

    /** Convert a [] of groups to a single comma sepparated groups string */
    private String groupsToString(String[] groups) {
        if (groups.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(groups[0]);
        for (int i = 1; i < groups.length; i++) {
            sb.append(",");
            sb.append(groups[i]);
        }
        return sb.toString();
    }

    /** Return the IP where this ReflRouter runs */
    private String getMyIP() {
        String ip = AppConfig.getProperty("lia.Monitor.useIPaddress");
        if (ip == null) {
            try {
                ip = InetAddress.getLocalHost().getHostAddress();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Could not determine my IP address", t);
            }
        }
        return ip;
    }

    /** Return the attributes for the service */
    private Entry[] getAttributes() {
        Name name = new Name(myJiniProxy.toString());
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [ RRouterJService ] [ getAttributes ] gmle hash: " + gmle.hash);
        }
        return new Entry[] { gmle, name };
    }

    /** Connect to LUSs, and register this service */
    private void register() {
        logger.log(Level.INFO, "\nUsing group(s): " + groupsToString(getGroups()) + "\n");

        try {
            SecureContextExecutor.getInstance().execute(new PrivilegedExceptionAction() {
                @Override
                public Object run() throws Exception {
                    ldm = MLJiniManagersProvider.getLookupDiscoveryManager();
                    ldm.addDiscoveryListener(_theInstance);

                    sdm = MLJiniManagersProvider.getServiceDiscoveryManager();

                    Configuration cfgLUSs = JiniConfigProvider.getUserDefinedConfig();
                    Entry[] attrSet = getAttributes();
                    lrm = new LeaseRenewalManager(cfgLUSs);

                    jmngr = new JoinManager(myJiniProxy, attrSet, _theInstance, ldm, lrm, cfgLUSs);

                    final long sTime = System.currentTimeMillis();
                    synchronized (ReflRouterJiniService.this) {
                        try {
                            while ((mysid == null) && ((System.currentTimeMillis() - sTime) < WAIT_FOR_SID_TIMEOUT)) {
                                if (logger.isLoggable(Level.FINEST)) {
                                    logger.log(Level.FINEST, ".....watting for sid ...");
                                }
                                ReflRouterJiniService.this.wait(1000);
                            }
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, "Got exception waiting for SID", t);
                        }
                    }

                    // update the JiniManagers for GMLEPublisher
                    MLJiniManagersProvider.setManagers(ldm, sdm, jmngr);
                    // create HERE the GMLE instance (withing the context)
                    GMLEPublisher.getInstance();
                    return null;
                }
            });
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Failed to register service", t);
        }

        if (mysid == null) {
            logger.log(Level.SEVERE, "\n SID == NULL \n");
            System.exit(1);
        }
    }

    /** Main thread */
    @Override
    public void run() {
        logger.log(Level.INFO, "ReflRouter Jini Service is starting...");
        try {
            register();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got exception while registering", t);
            active = false;
        }
        while (active) {
            try {
                analyzeMasterMode();
                updateMyStatus();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Got exception while analyzing Master Mode", t);
            }
            try {
                Thread.sleep(5000);
            } catch (Throwable t) {
            }
        }
        finishIT();
        logger.log(Level.INFO, "ReflRouter Jini Service finished.");
    }

    /** Terminate the Jini Join Manager */
    private void finishIT() {
        if (jmngr != null) {
            try {
                jmngr.terminate();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Exc while unregistering from LUSs", t);
            }
        }
    }

    /** Stop ReflRouter Jini Service */
    public void stopIt() {
        this.active = false;
    }

    /** Returns true if this ReflRouter is master and it should actually send the commands */
    public boolean isMasterMode() {
        return iAmMaster.get();
    }

    /** Set the additional, variable parameters to publis in Jini */
    public void setSeenReflectors(int seenReflectors) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Setting parameters: seenReflectors=" + seenReflectors);
        }
        gmle.hash.put("reflectors", Integer.valueOf(seenReflectors));
    }

    /**
     * Set the availability of this ReflRouter. 
     * @param available whether the current ReflRouter is ready to be master
     */
    public void setAvailability(boolean available) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Setting availability: " + available);
        }
        iAmAvailable.set(available);
    }

    private void updateMyStatus() {
        long now = NTPDate.currentTimeMillis();
        gmle.hash.put("aliveAt", iAmAvailable.get() ? Long.valueOf(now) : Long.valueOf(0));
        gmle.hash.put("aliveAtText", iAmAvailable.get() ? new Date(now).toString() : "Not ready");
        GMLEPublisher.getInstance().publishNow(gmle.hash);
    }

    /** Check if I am supposed to be the master, by looking at the other ReflRouters */
    private void analyzeMasterMode() {
        MLLUSHelper.getInstance().forceUpdate();
        ServiceItem[] si = MLLUSHelper.getInstance().getReflRouterServices();

        while ((si == null) || (si.length == 0)) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "No service discovered in LUSs - not even me. Still waiting...");
            }
            iAmMaster.set(false);
            MLLUSHelper.getInstance().forceUpdate();
            si = MLLUSHelper.getInstance().getReflRouterServices();
            try {
                Thread.sleep(500);
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Exception waiting for ServiceItem-s from MLLUSHelper");
            }
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.finer("There are " + si.length + " ReflRouter service items.");
        }
        // prepare the list of available ReflRouters, registered in LUSs
        htKnownRR.clear();
        Vector addedRRs = new Vector(); // will hold the ServiceIDs of the knownRRs 
        for (int i = 0; i < si.length; i++) {
            ReflRouterJiniProxy rr = (ReflRouterJiniProxy) si[i].service;
            if ((rr == null) || (rr.groups == null) || (rr.ip == null)) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "Skipping " + rr + " with sid=" + si[i].serviceID);
                }
                continue;
            }
            if (si[i].attributeSets.length != 0) {
                if (myJiniProxy.groups.equals(rr.groups)) {
                    if (!addedRRs.contains(si[i].serviceID)) {
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, "Adding " + rr + " sid=" + si[i].serviceID);
                        }
                        htKnownRR.put(si[i].service, si[i].attributeSets[0]);
                        addedRRs.add(si[i].serviceID);
                    } else {
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, rr + " already added with sid=" + si[i].serviceID);
                        }
                    }
                } else {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Ignoring service in other group: " + rr + " sid=" + si[i].serviceID);
                    }
                }
            } else {
                logger.log(Level.WARNING, "Ignoring service with no attributes: " + rr + " sid=" + si[i].serviceID);
            }
        }

        // among these, select the best
        ReflRouterJiniProxy bestRR = null;
        int bestSeesRR = 0;
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Selecting best ReflRouter from " + htKnownRR.size() + " available...");
        }
        long maxInactiveTime = AppConfig.getl("lia.Monitor.JiniClient.ReflRouter.maxInactiveTime", 20) * 1000;
        long now = NTPDate.currentTimeMillis();
        for (Iterator rrit = htKnownRR.entrySet().iterator(); rrit.hasNext();) {
            Map.Entry rrme = (Map.Entry) rrit.next();
            ReflRouterJiniProxy rr = (ReflRouterJiniProxy) rrme.getKey();
            Hashtable htParams = ((GenericMLEntry) rrme.getValue()).hash;

            int crtSeesRR = ((Integer) htParams.get("reflectors")).intValue();
            Long aliveAt = (Long) htParams.get("aliveAt");
            boolean crtExpired = (aliveAt == null) || ((now - aliveAt.longValue()) > maxInactiveTime);
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Analyzing " + rr + " with attrs: " + htParams + " expired=" + crtExpired);
            }

            if ((!crtExpired)
                    && ((bestRR == null) || (crtSeesRR > bestSeesRR) || ((crtSeesRR == bestSeesRR) && (rr.ip
                            .compareTo(bestRR.ip) < 0)))) {
                bestRR = rr;
                bestSeesRR = crtSeesRR;
            }
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "BEST ReflRouter is: " + bestRR);
        }

        // if it's us, then we are the master!
        if (bestRR == null) {
            logger.log(Level.WARNING, "Could not select a 'best' ReflRouter. " + "I am " + myJiniProxy + " and I see "
                    + ((Integer) gmle.hash.get("reflectors")).intValue() + " reflectors...");
            iAmMaster.set(false);
            return;
        }
        if (myJiniProxy.ip.equals(bestRR.ip)) {
            if (!iAmMaster.get()) {
                logger.log(Level.INFO, "I am becoming master: " + myJiniProxy);
                iAmMaster.set(true);
            }
        } else {
            if (iAmMaster.get()) {
                logger.log(Level.INFO, "I am no longer master in favor of: " + bestRR);
                iAmMaster.set(false);
            }
        }
    }

    /**
     * @see net.jini.lookup.ServiceIDListener#serviceIDNotify(net.jini.core.lookup.ServiceID)
     */
    @Override
    public void serviceIDNotify(ServiceID serviceID) {
        mysid = serviceID;
        logger.log(Level.INFO, "Service registered [ " + mysid + " ] ");

        synchronized (this) {
            this.notifyAll();
        }

    }

    /**
     * @see net.jini.lookup.ServiceDiscoveryListener#serviceAdded(net.jini.lookup.ServiceDiscoveryEvent)
     */
    @Override
    public void serviceAdded(ServiceDiscoveryEvent event) {
        logger.log(Level.INFO, " Service added " + event.getSource());
        //		ServiceItem si = event.getPostEventServiceItem();
        //		if (si.serviceID.equals(mysid))
        //			me = si;
    }

    /**
     * @see net.jini.lookup.ServiceDiscoveryListener#serviceRemoved(net.jini.lookup.ServiceDiscoveryEvent)
     */
    @Override
    public void serviceRemoved(ServiceDiscoveryEvent event) {
        logger.log(Level.INFO, " Service removed " + event.getSource());
    }

    /**
     * @see net.jini.lookup.ServiceDiscoveryListener#serviceChanged(net.jini.lookup.ServiceDiscoveryEvent)
     */
    @Override
    public void serviceChanged(ServiceDiscoveryEvent event) {
        logger.log(Level.INFO, " Service changed " + event.getSource());
    }

    /**
     * @see net.jini.discovery.DiscoveryListener#discovered(net.jini.discovery.DiscoveryEvent)
     */
    @Override
    public void discovered(DiscoveryEvent e) {
        logger.log(Level.INFO, " LUS Service discovered " + e.getSource());
    }

    /**
     * @see net.jini.discovery.DiscoveryListener#discarded(net.jini.discovery.DiscoveryEvent)
     */
    @Override
    public void discarded(DiscoveryEvent e) {
        logger.log(Level.WARNING, " LUS Service discared " + e.getSource());
    }
}
