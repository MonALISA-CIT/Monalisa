/**
 * 
 */
package lia.Monitor.JiniClient.Store;

import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Store.Fast.DB;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;

/**
 * Class maintaining service group definitions.
 * 
 * @author costing
 * @since 26.02.2007
 */
public final class ServiceGroups extends Observable implements AppConfigChangeListener {

    private static final Logger logger = Logger.getLogger(ServiceGroups.class.getName());

    private static ServiceGroups instance = null;

    private long lLastReload = 0;

    /**
     * Get the single possible instance of this class
     * 
     * @return the single instance of this class
     */
    public static synchronized ServiceGroups getInstance() {
        if (instance == null) {
            logger.fine("Creating new instance of ServiceGroups");

            instance = new ServiceGroups();
        }

        return instance;
    }

    private final Map<String, Set<String>> groups = new TreeMap<String, Set<String>>();

    private ServiceGroups() {
        reload();

        AppConfig.addNotifier(this);
    }

    @Override
    public void notifyAppConfigChanged() {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("reacting to change in the configuration file");
        }

        reload();
    }

    /**
     * Reload the groups definition from the database. The groups are maintained in
     * AliEn repository by the <i>/pledged_new.jsp</i> page.
     */
    public void reload() {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("reloading the groups definition from the database");
        }

        synchronized (groups) {
            groups.clear();

            final DB db = new DB();
            
            db.setReadOnly(true);
            
            db.query(AppConfig.getProperty("lia.Monitor.JiniClient.Store.ServiceGroups.query",
                    "SELECT site,groupname FROM pledged_dynamic WHERE length(groupname)>0;"));

            while (db.moveNext()) {
                final String sSite = db.gets(1);
                final String sGroup = db.gets(2);

                Set<String> s = groups.get(sGroup);

                if (s == null) {
                    s = new TreeSet<String>();
                    groups.put(sGroup, s);
                }

                s.add(sSite);
            }
        }

        lLastReload = System.currentTimeMillis();

        setChanged();
        notifyObservers(this);
    }

    /**
     * Trigger an update of the structures from the database every hour. 
     * Just in case the change events don't propagate correctly from the web pages. 
     */
    private void checkForReload() {
        if ((System.currentTimeMillis() - lLastReload) > (1000 * 60 * 60)) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("periodic forced reload of the contents");
            }

            reload();
        }
    }

    /**
     * Get an independent copy of the groups. The structure is actually:<br>
     * <code>
     * Map&lt;String, Set&lt;String&gt;&gt;
     * </code><br>
     * with the convention:<br>
     * <ul>
     * <li>The key in the <code>Map</code> is the group name</li>
     * <li>The entries in the <code>Set</code> are the site names that belong to the group above</li>
     * </ul>
     * 
     * @return a copy of the service groups
     */
    public Map<String, Set<String>> getGroups() {
        final Map<String, Set<String>> ret = new TreeMap<String, Set<String>>();

        synchronized (groups) {
            checkForReload();

            final Iterator<Map.Entry<String, Set<String>>> it = groups.entrySet().iterator();

            while (it.hasNext()) {
                final Map.Entry<String, Set<String>> me = it.next();

                final String sGroup = me.getKey();
                final Set<String> sites = me.getValue();

                ret.put(sGroup, new TreeSet<String>(sites));
            }
        }

        return ret;
    }

    /**
     * Similar to {@link #getGroups()}, this function returns the reverse mapping, key=service name, 
     * value=group name.
     * 
     * @return services to groups mapping
     */
    public Map<String, String> getServicesInGroups() {
        final Map<String, Set<String>> m = getGroups();

        final Map<String, String> serviceToGroups = new TreeMap<String, String>();

        final Iterator<Map.Entry<String, Set<String>>> it = m.entrySet().iterator();

        while (it.hasNext()) {
            final Map.Entry<String, Set<String>> me = it.next();

            final String sGroupName = me.getKey();
            final Set<String> sGroupMembers = me.getValue();

            final Iterator<String> itm = sGroupMembers.iterator();

            while (itm.hasNext()) {
                serviceToGroups.put(itm.next(), sGroupName);
            }
        }

        return serviceToGroups;
    }
}
