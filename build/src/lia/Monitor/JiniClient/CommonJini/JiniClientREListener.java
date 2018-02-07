package lia.Monitor.JiniClient.CommonJini;

import java.net.InetAddress;
import java.rmi.RMISecurityManager;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.lookup.ServiceDiscoveryEvent;
import net.jini.lookup.ServiceDiscoveryListener;
import net.jini.lookup.entry.Name;

import com.sun.jini.tool.ClassServer;

/**
 *	JiniClientREListener = Jini Client with Jini Remote Event Listener capabilities
 */
public abstract class JiniClientREListener extends JiniClient implements ServiceDiscoveryListener {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(JiniClientREListener.class.getName());

    public JiniClientREListener() {
        super();
    }

    @Override
    public void init() {
        String webhost = null;
        String webadd1 = null;

        //HACK For WebStart
        Policy.setPolicy(new Policy() {
            @Override
            public PermissionCollection getPermissions(CodeSource codesource) {
                Permissions perms = new Permissions();
                perms.add(new AllPermission());
                return (perms);
            }

            @Override
            public void refresh() {
            }
        });
        //END HACK For WebStart

        try {
            webhost = InetAddress.getLocalHost().getHostAddress();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Failed to get HOST address ", t);
        }

        if (webhost != null) {
            webadd1 = "http://" + webhost + ":8588/client_mon_dl.jar";
        }

        String forceWeb = AppConfig.getProperty("lia.Monitor.useCodeBase");

        if (forceWeb != null) {
            webadd1 = forceWeb + "/client_mon_dl.jar";
        }

        if ((webadd1 != null) && (forceWeb == null)) {

            try {
                new ClassServer(8588, ".", true, true).start();
            } catch (Throwable t) {
                logger.log(Level.WARNING, " failed to start the WEB ", t);
            }
        }

        // set security manager
        if (System.getSecurityManager() == null) {
            System.setSecurityManager(new RMISecurityManager());
        }

        System.setProperty("java.rmi.server.codebase", webadd1);

        // now call the jini registration
        super.init();
    }

    @Override
    public void serviceAdded(ServiceDiscoveryEvent event) {
        System.out.println(" added service ");
        ServiceItem si = event.getPostEventServiceItem();
        serviceInformation(si);
        AddMonitorUnit(si);
    }

    @Override
    public void serviceRemoved(ServiceDiscoveryEvent event) {
        System.out.println(" removed service ");
        ServiceItem si = event.getPreEventServiceItem();
        removeNode(si.serviceID);
    }

    @Override
    public void serviceChanged(ServiceDiscoveryEvent event) {
        System.out.println(" changed service ");
        ServiceItem si = event.getPostEventServiceItem();
    }

    public void serviceInformation(ServiceItem si) {
        Entry[] attrs = si.attributeSets;
        Name sname = getName(attrs);

        System.out.println(" Service Name = " + sname);
    }

    Name getName(Entry attrs[]) {
        for (Entry attr : attrs) {
            if (attr instanceof Name) {
                return (Name) attr;
            }
        }

        return null;
    }

    //	abstract public void newResult(Object res);

    abstract public void verifyNodes();

    @Override
    abstract public boolean AddMonitorUnit(ServiceItem si);

    abstract public void ErrorNode(ServiceID sid);

    @Override
    abstract public void removeNode(ServiceID id);

}