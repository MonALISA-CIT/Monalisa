package lia.util.security.authz;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.RMISecurityManager;
import java.security.PrivilegedAction;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.Subject;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.GenericMLEntry;
import lia.util.security.MLLogin;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.config.EmptyConfiguration;
import net.jini.core.discovery.LookupLocator;
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

public class AuthZJiniService extends Thread implements ServiceIDListener, ServiceDiscoveryListener, DiscoveryListener {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(AuthZJiniService.class.getName());

    private static AuthZJiniService _thisInstance;
    private boolean active;
    private ServiceID mysid;
    private ServiceItem me;
    protected transient LookupDiscoveryManager ldm;
    protected transient LeaseRenewalManager lrm;
    protected transient JoinManager jmngr;
    protected transient ServiceDiscoveryManager sdm;

    private transient Subject subject;
    private transient boolean useSecureLUSs = false;

    private AuthZJiniService() {
        super("( ML ) - AuthzJiniService");
        active = true;
    }

    public static AuthZJiniService getInstance() {
        if (_thisInstance == null) {
            try {
                _thisInstance = new AuthZJiniService();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Could NOT instantiate AuthZJiniService ");
                _thisInstance = null;
            }
        }
        return _thisInstance;
    }

    private static final String[] getGroups(String group) {
        String[] retV = null;
        if ((group == null) || (group.length() == 0)) {
            return null;
        }
        StringTokenizer st = new StringTokenizer(group.trim(), ",");
        int count = st.countTokens();
        if (count > 0) {
            retV = new String[count];
            int vi = 0;
            while (st.hasMoreTokens()) {
                String token = st.nextToken().trim();
                if ((token != null) && (token.length() > 0)) {
                    retV[vi++] = token;
                }
            }
        }
        return retV;
    }

    /** return the codebase for this service, based on user properties */
    private String getCodebase() {
        String codebase = "";
        String dlURLs = AppConfig.getProperty("lia.util.security.authz.dlURLs",
                "http://monalisa.cacr.caltech.edu/AUTHZ_ML,http://monalisa.cern.ch/MONALISA/AUTHZ_ML");

        StringTokenizer st = new StringTokenizer(dlURLs, ",");
        if ((st != null) && (st.countTokens() > 0)) {
            while ((st != null) && st.hasMoreTokens()) {
                codebase += (st.nextToken() + "/dl/Authz-dl.jar" + (st.hasMoreTokens() ? " " : ""));
            }
        }
        return codebase;
    }

    private void register() throws Exception {
        LookupLocator[] luss = getLUDSs();
        StringBuilder sb = new StringBuilder();
        sb.append("\n\nUsing LUSs: ");
        if ((luss == null) || (luss.length == 0)) {//normaly we should have DEFAULT_LUDs
            logger.log(Level.SEVERE, "luss == null!?!??!?!");
        } else {
            for (int i = 0; i < luss.length; i++) {
                LookupLocator locator = luss[i];
                sb.append("\nLUS " + i + ": " + locator.getHost());
            }
        }
        String mgroup = AppConfig.getProperty("lia.Monitor.group", "ml");

        sb.append("\nUsing group(s): " + mgroup + " [ ");
        String[] groups = getGroups(mgroup);

        for (int gsi = 0; gsi < groups.length; gsi++) {
            sb.append(groups[gsi]);
            sb.append((gsi < (groups.length - 1)) ? "," : " ] \n");
        }

        logger.log(Level.INFO, sb.toString());
        try {

            String codebase = getCodebase(); //startWeb (topoPath + "/dl");
            System.setProperty("java.rmi.server.codebase", codebase);
            //System.setProperty ("export.codebase",codebase);		
            logger.log(Level.INFO, "using codebase: " + codebase);

            //setup configuration used by Jini helpers
            String cfg_file = AppConfig.getProperty("lia.util.security.authz.jiniconfig");
            Configuration config;
            if (cfg_file == null) {
                config = EmptyConfiguration.INSTANCE;
            } else {
                String[] configArgs = new String[] { cfg_file };
                config = ConfigurationProvider.getInstance(configArgs);
            }

            AuthZJiniProxy ps = new AuthZJiniProxy();
            long now = System.currentTimeMillis();
            ps.rTime = Long.valueOf(now);
            ps.rDate = new Date(now);
            ldm = new LookupDiscoveryManager(null, luss, this, config);

            try {
                Thread.sleep(5000);
            } catch (Exception e) {
            }

            Entry[] attrSet = getAttributes();
            this.lrm = new LeaseRenewalManager(config);
            this.jmngr = new JoinManager(ps, attrSet, this, ldm, lrm, config);
        } catch (Exception e) {
            System.out.println("A crapat .... ");
            e.printStackTrace();
        }
        for (int i = 0; i < 100; i++) {
            if (mysid != null) {
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, ".....watting for sid ...");
            }

        }

        if (mysid == null) {
            logger.log(Level.SEVERE, "\n SID == NULL \n");
        }
    }

    private Entry[] getAttributes() {
        GenericMLEntry gmle = new GenericMLEntry();
        int port = Integer.valueOf(AppConfig.getProperty("lia.util.security.authz.port", "36006")).intValue();
        String localhost;
        try {
            localhost = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            localhost = null;
        }
        String host = AppConfig.getProperty("lia.util.security.authz.host", localhost);

        if (host != null) {
            gmle.hash.put("hostname", host + ":" + port);
        }
        Name name = new Name("Authz-Service [ " + host + ":" + port + " ] ");
        return new Entry[] { gmle, name };
    }

    @Override
    public void run() {
        try {
            // set security manager
            if (System.getSecurityManager() == null) {
                System.setSecurityManager(new RMISecurityManager());
            }
            register();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got exception while registering: ", t);
            active = false;
        }
        while (active) {
            try {
                Thread.sleep(5000);
            } catch (Throwable t) {
            }
        }
        finishIT();
    }

    private void finishIT() {
        if (jmngr != null) {
            try {
                jmngr.terminate();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Exc while unregistering from LUSs", t);
            }
        }
    }

    private LookupLocator[] getLUDSs() {
        String luslist = AppConfig.getProperty("lia.Monitor.LUSs", null);
        if (luslist == null) {
            return null;
        }

        StringTokenizer tz = new StringTokenizer(luslist, ",");
        Vector locators = new Vector();

        while (tz.hasMoreTokens()) {
            String host = tz.nextToken().trim();
            try {
                locators.add(new LookupLocator("jini://" + host));
            } catch (java.net.MalformedURLException e) {
                logger.log(Level.WARNING, "URL format error ! host=" + host + "   \n", e);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "General Exception adding LUS for address " + host);
            }
        }

        if (locators.size() > 0) {
            return (LookupLocator[]) locators.toArray(new LookupLocator[locators.size()]);
        }

        return null;
    }

    public void stopIt() {
        this.active = false;
    }

    /**
     * @see net.jini.lookup.ServiceIDListener#serviceIDNotify(net.jini.core.lookup.ServiceID)
     */
    @Override
    public void serviceIDNotify(ServiceID serviceID) {
        // TODO Auto-generated method stub
        mysid = serviceID;
        logger.log(Level.INFO, "Service regstered [ " + mysid + " ] ");
    }

    /**
     * @see net.jini.lookup.ServiceDiscoveryListener#serviceAdded(net.jini.lookup.ServiceDiscoveryEvent)
     */
    @Override
    public void serviceAdded(ServiceDiscoveryEvent event) {
        ServiceItem si = event.getPostEventServiceItem();
        if (si.serviceID.equals(mysid)) {
            me = si;
        }
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

    /**
     * @return Returns the subject.
     */
    public Subject getSubject() {
        return this.subject;
    }

    /**
     * @param subject The subject to set.
     */
    public void setSubject(Subject subject) {
        this.subject = subject;
    }

    public static void main(String args[]) {
        System.out.println("Starging authz jini service...");
        boolean useSecLUSs = Boolean.valueOf(AppConfig.getProperty("lia.util.topology.useSecureLUSs", "false"))
                .booleanValue();
        if (useSecLUSs) {
            logger.log(Level.INFO, "Use Secure LUSs");
            try {
                /*
                 * set trustStore to empty string to accept any certificate in a
                 * SSL session (we don't want to authenticate the server (LUS)
                 */
                System.setProperty("javax.net.ssl.trustStore", "");

                /*
                 * gather private key and certificate chain from files
                 */
                String privateKeyPath = AppConfig.getProperty("lia.util.topology.privateKeyFile",
                        "/etc/grid-security/hostkey.pem");
                String certChainPath = AppConfig.getProperty("lia.util.topology.certChainFile",
                        "/etc/grid-security/hostcert.pem");

                logger.log(Level.FINEST, "Loading credentials from files\n" + privateKeyPath + "\n" + certChainPath);
                /*
                 * create local subject used in auth/authz
                 */
                MLLogin serviceCredentials = new MLLogin();
                serviceCredentials.login(privateKeyPath, null, certChainPath);

                Subject ctxSubject = serviceCredentials.getSubject();
                if (ctxSubject == null) {
                    logger.log(Level.WARNING, "Subject is null");
                }
                logger.log(Level.FINE, "SUBJECT: " + ctxSubject);

                Subject.doAsPrivileged(ctxSubject, new PrivilegedAction() {
                    @Override
                    public Object run() {
                        AuthZJiniService tjs = AuthZJiniService.getInstance();
                        tjs.setDaemon(false);
                        tjs.start();
                        return null; // nothing to return
                    }
                }, null);

            } catch (Throwable t) {
                logger.log(Level.WARNING, "Cannot init service credentials....Returning...\n" + t.getMessage());
            }
        }//if (useSecLUSs) ...
        else {//start service 
            AuthZJiniService tjs = AuthZJiniService.getInstance();
            tjs.setDaemon(false);
            tjs.start();
        }
    }

}
